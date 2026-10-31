package com.company.fashionpos.operations;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySalesService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final BranchService branches;
  private final PosPreferencesService preferences;

  public DailySalesService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      BranchService branches,
      PosPreferencesService preferences) {
    this.jdbc = jdbc;
    this.json = json;
    this.branches = branches;
    this.preferences = preferences;
  }

  public record Window(Instant fromInclusive, Instant toExclusive) {}

  public static Window window(LocalDate date, String timeZone) {
    ZoneId zone = ZoneId.of(timeZone == null || timeZone.isBlank() ? "Africa/Nairobi" : timeZone);
    return new Window(
        date.atStartOfDay(zone).toInstant(), date.atTime(21, 0).atZone(zone).toInstant());
  }

  public List<DailySalesReport> history(AuthenticatedUser user, UUID branchId) {
    branches.requireBranchAccess(user, branchId);
    return jdbc.query(
        "select snapshot from daily_sales_reports where organization_id=? and branch_id=? order by business_date desc limit 366",
        (rs, n) -> decode(rs.getString(1)),
        user.organizationId(),
        branchId);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public DailySalesReport generateForUser(AuthenticatedUser user, UUID branchId, LocalDate date) {
    branches.requireBranchAccess(user, branchId);
    return generate(user.organizationId(), branchId, date);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public DailySalesReport generate(UUID org, UUID branch, LocalDate date) {
    var existing =
        jdbc.query(
            "select snapshot from daily_sales_reports where organization_id=? and branch_id=? and business_date=?",
            (rs, n) -> decode(rs.getString(1)),
            org,
            branch,
            date);
    if (!existing.isEmpty()) return existing.get(0);
    PosPreferences p = preferences.get(org);
    Window w = window(date, p.timeZone());
    if (w.toExclusive().isAfter(Instant.now()))
      throw new IllegalArgumentException(
          "Daily summaries are available after 9:00 PM for the selected day");
    var store =
        jdbc.queryForMap(
            "select b.name,o.currency_code from branches b join organizations o on o.id=b.organization_id where b.id=? and b.organization_id=?",
            branch,
            org);
    Object[] args = {
      org, branch, Timestamp.from(w.fromInclusive()), Timestamp.from(w.toExclusive())
    };
    String period =
        " s.organization_id=? and s.branch_id=? and s.sold_at>=? and s.sold_at<? and s.status='COMPLETED' ";
    var totals =
        jdbc.queryForMap(
            "select count(*) as transactions,coalesce(sum(subtotal_amount),0) as gross,coalesce(sum(discount_amount),0) as discounts,coalesce(sum(tax_amount),0) as tax from sales s where "
                + period,
            args);
    var reversed =
        jdbc.queryForMap(
            """
        select coalesce(sum(case when kind='REFUND' then amount else 0 end),0) as refunds,
        count(case when kind='REFUND' then 1 end) as refund_count,
        coalesce(sum(case when kind='CANCELLATION' then amount else 0 end),0) as cancellations,
        count(case when kind='CANCELLATION' then 1 end) as cancel_count,
        coalesce(sum(amount-tax_amount),0) as net_reversed,coalesce(sum(tax_amount),0) as reversed_tax
        from sale_reversals where organization_id=? and branch_id=? and created_at>=? and created_at<?
        """,
            args);
    Map<String, BigDecimal> payments = new LinkedHashMap<>();
    for (String method : List.of("CASH", "MPESA", "CARD", "BANK_TRANSFER"))
      payments.put(method, BigDecimal.ZERO);
    jdbc.query(
        "select p.method,sum(p.amount) as amount from payments p join sales s on s.id=p.sale_id where "
            + period
            + " and p.status='SETTLED' group by p.method",
        rs -> {
          payments.put(rs.getString(1), rs.getBigDecimal(2));
        },
        args);
    jdbc.query(
        """
        select p.method,sum(p.amount) from payments p join sale_reversals r on r.sale_id=p.sale_id
        where r.organization_id=? and r.branch_id=? and r.created_at>=? and r.created_at<? and p.status='SETTLED' group by p.method
        """,
        rs -> {
          payments.merge(rs.getString(1), rs.getBigDecimal(2).negate(), BigDecimal::add);
        },
        args);
    long units =
        jdbc.queryForObject(
            "select coalesce(sum(l.quantity),0) from sale_lines l join sales s on s.id=l.sale_id where "
                + period,
            Long.class,
            args);
    List<Map<String, Object>> variants =
        jdbc.queryForList(
            "select l.product_id as id,l.product_name as name,l.sku,sum(l.quantity) as quantity,sum(l.line_total) as revenue from sale_lines l join sales s on s.id=l.sale_id where "
                + period
                + " group by l.product_id,l.product_name,l.sku order by quantity desc,l.product_name",
            args);
    List<Map<String, Object>> parents =
        jdbc.queryForList(
            "select coalesce(l.parent_product_id,l.product_id) as id,coalesce(l.parent_product_name,l.product_name) as name,sum(l.quantity) as quantity,sum(l.line_total) as revenue from sale_lines l join sales s on s.id=l.sale_id where "
                + period
                + " group by coalesce(l.parent_product_id,l.product_id),coalesce(l.parent_product_name,l.product_name) order by quantity desc",
            args);
    List<Map<String, Object>> cashiers =
        jdbc.queryForList(
            "select s.cashier_id as id,coalesce(u.display_name,'Unattributed (legacy)') as name,count(*) as transactions,sum(s.total_amount) as revenue from sales s left join users u on u.id=s.cashier_id where "
                + period
                + " group by s.cashier_id,u.display_name order by revenue desc",
            args);
    BigDecimal net =
        amount(totals, "gross")
            .subtract(amount(totals, "discounts"))
            .subtract(amount(reversed, "net_reversed"));
    BigDecimal tax = amount(totals, "tax").subtract(amount(reversed, "reversed_tax"));
    DailySalesReport report =
        new DailySalesReport(
            UUID.randomUUID(),
            branch,
            (String) store.get("name"),
            date,
            p.timeZone(),
            w.fromInclusive(),
            w.toExclusive(),
            (String) store.get("currency_code"),
            ((Number) totals.get("transactions")).longValue(),
            amount(totals, "gross"),
            amount(totals, "discounts"),
            tax,
            amount(reversed, "refunds"),
            ((Number) reversed.get("refund_count")).longValue(),
            amount(reversed, "cancellations"),
            ((Number) reversed.get("cancel_count")).longValue(),
            net,
            net.add(tax),
            payments,
            units,
            variants,
            parents,
            cashiers);
    int inserted =
        jdbc.update(
            "insert into daily_sales_reports (id,organization_id,branch_id,business_date,time_zone,from_inclusive,to_exclusive,snapshot) values (?,?,?,?,?,?,?,?) on conflict (organization_id,branch_id,business_date) do nothing",
            report.id(),
            org,
            branch,
            date,
            p.timeZone(),
            Timestamp.from(w.fromInclusive()),
            Timestamp.from(w.toExclusive()),
            preferences.encode(report));
    if (inserted == 0)
      return jdbc.queryForObject(
          "select snapshot from daily_sales_reports where organization_id=? and branch_id=? and business_date=?",
          (rs, n) -> decode(rs.getString(1)),
          org,
          branch,
          date);
    queue(org, report, "scheduled");
    return report;
  }

  @Transactional
  public void resend(AuthenticatedUser user, UUID reportId, String key) {
    if (key == null || key.isBlank() || key.length() > 120 || key.equals("scheduled"))
      throw new IllegalArgumentException("A unique Idempotency-Key is required for a resend");
    var reports =
        jdbc.query(
            "select snapshot from daily_sales_reports where id=? and organization_id=?",
            (rs, n) -> decode(rs.getString(1)),
            reportId,
            user.organizationId());
    if (reports.isEmpty()) throw new EntityNotFoundException("Daily report not found");
    branches.requireBranchAccess(user, reports.get(0).branchId());
    queue(user.organizationId(), reports.get(0), key);
  }

  public List<Map<String, Object>> recipients(UUID org, UUID branch) {
    return jdbc.queryForList(
        """
        select u.id,u.email from users u where u.organization_id=? and u.status='ACTIVE'
        and exists (select 1 from user_roles ur join role_permissions rp on rp.role_id=ur.role_id join permissions p on p.id=rp.permission_id
          where ur.user_id=u.id and p.code in ('admin:manage','reports:view'))
        and (not exists (select 1 from user_branch_assignments a where a.user_id=u.id)
          or exists (select 1 from user_branch_assignments a where a.user_id=u.id and a.branch_id=?))
        """,
        org,
        branch);
  }

  private void queue(UUID org, DailySalesReport report, String key) {
    PosPreferences p = preferences.get(org);
    for (var recipient : recipients(org, report.branchId())) {
      UUID id = (UUID) recipient.get("id");
      // An empty selection defaults to administrators, never all staff.
      if (p.recipientIds().isEmpty()) {
        if (jdbc.queryForObject(
                "select count(*) from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=? and r.role_key='ADMIN'",
                Integer.class,
                id)
            == 0) continue;
      } else if (!p.recipientIds().contains(id)) continue;
      for (String channel : List.of("IN_APP", "EMAIL")) {
        if ((channel.equals("IN_APP") && !p.inAppEnabled())
            || (channel.equals("EMAIL") && !p.emailEnabled())) continue;
        jdbc.update(
            "insert into daily_summary_deliveries (id,report_id,user_id,channel,batch_key) values (?,?,?,?,?) on conflict (report_id,user_id,channel,batch_key) do nothing",
            UUID.randomUUID(),
            report.id(),
            id,
            channel,
            key);
      }
    }
  }

  private BigDecimal amount(Map<String, Object> row, String key) {
    return (BigDecimal) row.get(key);
  }

  public DailySalesReport decode(String value) {
    try {
      return json.readValue(value, DailySalesReport.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid daily sales report", e);
    }
  }
}
