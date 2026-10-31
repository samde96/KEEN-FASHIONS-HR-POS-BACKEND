package com.company.fashionpos.shifts;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashierShiftService {
  private final JdbcTemplate jdbc;
  private final BranchService branches;
  private static final String SELECT =
      """
      select s.*, b.name branch_name, u.display_name cashier_name
      from cashier_shifts s join branches b on b.id=s.branch_id
      join users u on u.id=s.cashier_id
      where s.organization_id=? and s.cashier_id=?
      """;

  public CashierShiftService(JdbcTemplate jdbc, BranchService branches) {
    this.jdbc = jdbc;
    this.branches = branches;
  }

  /** Checkout, expense registration, opening and closing share this per-user transaction lock. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockOperator(AuthenticatedUser user) {
    var ids =
        jdbc.queryForList(
            "select id from users where id=? and organization_id=? for update",
            UUID.class,
            user.userId(),
            user.organizationId());
    if (ids.isEmpty()) throw new AccessDeniedException("Operator is not in this business");
  }

  @Transactional(readOnly = true)
  public List<CashierShiftResponse> list(AuthenticatedUser user) {
    return jdbc
        .query(
            SELECT + " order by s.opened_at desc limit 100",
            this::read,
            user.organizationId(),
            user.userId())
        .stream()
        .filter(
            s ->
                !user.isCashier() && user.branchIds().isEmpty()
                    || user.branchIds().contains(s.branchId()))
        .toList();
  }

  @Transactional
  public CashierShiftResponse open(
      AuthenticatedUser user, CashierShiftController.OpenRequest request) {
    branches.requireBranchAccess(user, request.branchId());
    lockOperator(user);
    Integer active =
        jdbc.queryForObject(
            "select count(*) from branches where id=? and organization_id=? and status='ACTIVE'",
            Integer.class,
            request.branchId(),
            user.organizationId());
    if (active == null || active == 0) throw new IllegalArgumentException("Select an active shop");
    Integer count =
        jdbc.queryForObject(
            "select count(*) from cashier_shifts where organization_id=? and cashier_id=? and status='OPEN'",
            Integer.class,
            user.organizationId(),
            user.userId());
    if (count != null && count > 0)
      throw new IllegalArgumentException("Close your open shift first");
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into cashier_shifts(id, organization_id, branch_id, cashier_id, status,
          opening_float, opening_note, opened_at) values (?, ?, ?, ?, 'OPEN', ?, ?, ?)
        """,
        id,
        user.organizationId(),
        request.branchId(),
        user.userId(),
        request.openingFloat(),
        request.openingNote(),
        Timestamp.from(Instant.now()));
    return require(user, id);
  }

  @Transactional
  public CashierShiftResponse close(
      AuthenticatedUser user, UUID id, CashierShiftController.CloseRequest request) {
    lockOperator(user);
    var shift = require(user, id);
    branches.requireBranchAccess(user, shift.branchId());
    if (shift.status().equals("CLOSED")) {
      if (shift.closingCash().compareTo(request.closingCash()) == 0
          && java.util.Objects.equals(shift.closingNote(), request.closingNote())) return shift;
      throw new IllegalArgumentException("This shift is already closed");
    }
    Instant closedAt = Instant.now();
    var totals =
        totals(
            user.organizationId(),
            user.userId(),
            shift.branchId(),
            shift.openedAt(),
            closedAt,
            shift.openingFloat());
    jdbc.update(
        """
        update cashier_shifts set status='CLOSED', closed_at=?, closing_cash=?, closing_note=?,
          cash_sales=?, cash_expenses=?, total_sales=?, transaction_count=?, expected_cash=?
        where id=? and organization_id=? and cashier_id=? and status='OPEN'
        """,
        Timestamp.from(closedAt),
        request.closingCash(),
        request.closingNote(),
        totals.cashSales(),
        totals.cashExpenses(),
        totals.totalSales(),
        totals.count(),
        totals.expected(),
        id,
        user.organizationId(),
        user.userId());
    return require(user, id);
  }

  private CashierShiftResponse require(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(SELECT + " and s.id=?", this::read, user.organizationId(), user.userId(), id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Shift not found");
    return rows.get(0);
  }

  private record Totals(
      BigDecimal cashSales,
      BigDecimal cashExpenses,
      BigDecimal totalSales,
      int count,
      BigDecimal expected) {}

  private Totals totals(
      UUID organization,
      UUID cashier,
      UUID branch,
      Instant start,
      Instant end,
      BigDecimal opening) {
    var sales =
        jdbc.queryForMap(
            """
        select coalesce(sum(s.total_amount),0) revenue, count(*) transactions,
          coalesce(sum((select sum(p.amount) from payments p
            where p.sale_id=s.id and p.method='CASH' and p.status='SETTLED')),0) cash
        from sales s where s.organization_id=? and s.cashier_id=? and s.branch_id=?
          and s.status='COMPLETED' and s.sold_at>=? and s.sold_at<=?
        """,
            organization,
            cashier,
            branch,
            Timestamp.from(start),
            Timestamp.from(end));
    BigDecimal cashExpenses =
        jdbc.queryForObject(
            """
        select coalesce(sum(amount),0) from expenses where organization_id=? and created_by=?
          and branch_id=? and status='PAID' and payment_method='CASH' and incurred_at>=? and incurred_at<=?
        """,
            BigDecimal.class,
            organization,
            cashier,
            branch,
            Timestamp.from(start),
            Timestamp.from(end));
    BigDecimal cash = (BigDecimal) sales.get("cash");
    return new Totals(
        cash,
        cashExpenses,
        (BigDecimal) sales.get("revenue"),
        ((Number) sales.get("transactions")).intValue(),
        opening.add(cash).subtract(cashExpenses));
  }

  private CashierShiftResponse read(ResultSet row, int index) throws SQLException {
    var id = row.getObject("id", UUID.class);
    var branch = row.getObject("branch_id", UUID.class);
    var cashier = row.getObject("cashier_id", UUID.class);
    Instant opened = row.getTimestamp("opened_at").toInstant();
    var closedValue = row.getTimestamp("closed_at");
    Instant closed = closedValue == null ? null : closedValue.toInstant();
    BigDecimal opening = row.getBigDecimal("opening_float");
    var total =
        closed == null
            ? totals(
                row.getObject("organization_id", UUID.class),
                cashier,
                branch,
                opened,
                Instant.now(),
                opening)
            : new Totals(
                row.getBigDecimal("cash_sales"),
                row.getBigDecimal("cash_expenses"),
                row.getBigDecimal("total_sales"),
                row.getInt("transaction_count"),
                row.getBigDecimal("expected_cash"));
    BigDecimal closingCash = row.getBigDecimal("closing_cash");
    return new CashierShiftResponse(
        id,
        branch,
        row.getString("branch_name"),
        cashier,
        row.getString("cashier_name"),
        row.getString("status"),
        opening,
        row.getString("opening_note"),
        opened,
        closed,
        closingCash,
        row.getString("closing_note"),
        total.cashSales(),
        total.cashExpenses(),
        total.totalSales(),
        total.count(),
        total.expected(),
        closingCash == null ? null : closingCash.subtract(total.expected()));
  }
}
