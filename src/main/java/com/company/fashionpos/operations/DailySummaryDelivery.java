package com.company.fashionpos.operations;

import com.company.fashionpos.delivery.DocumentDeliveryService;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySummaryDelivery {
  private final JdbcTemplate jdbc;
  private final DailySalesService reports;
  private final DocumentDeliveryService email;

  public DailySummaryDelivery(
      JdbcTemplate jdbc, DailySalesService reports, DocumentDeliveryService email) {
    this.jdbc = jdbc;
    this.reports = reports;
    this.email = email;
  }

  @Transactional
  public boolean deliverNext() {
    var rows =
        jdbc.queryForList(
            """
        select d.*,r.organization_id,r.branch_id,r.snapshot,
          coalesce(p.notification_email,'') as notification_email,coalesce(p.email_enabled,false) as email_enabled
        from daily_summary_deliveries d join daily_sales_reports r on r.id=d.report_id
        left join pos_preferences p on p.organization_id=r.organization_id
        where d.status='PENDING' and d.next_attempt_at<=now() order by d.next_attempt_at limit 1 for update of d skip locked
        """);
    if (rows.isEmpty()) return false;
    var row = rows.get(0);
    UUID id = (UUID) row.get("id"),
        user = (UUID) row.get("user_id"),
        org = (UUID) row.get("organization_id"),
        branch = (UUID) row.get("branch_id");
    var recipient =
        reports.recipients(org, branch).stream().filter(r -> r.get("id").equals(user)).findFirst();
    Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
    // Provider idempotency expires after 24h. Stop earlier to avoid an ambiguous repeat send.
    if (recipient.isEmpty() || created.plus(Duration.ofHours(23)).isBefore(Instant.now())) {
      jdbc.update(
          "update daily_summary_deliveries set status='FAILED',last_error=? where id=?",
          recipient.isEmpty()
              ? "Recipient no longer has report access"
              : "Retry window expired; manual resend is available",
          id);
      return true;
    }
    DailySalesReport report = reports.decode((String) row.get("snapshot"));
    String title = "Daily Sales Summary — " + report.businessDate() + " — " + report.branchName();
    if (title.length() > 160) title = title.substring(0, 160);
    String message = message(report);
    String recipientEmail = (String) row.get("to_email");
    if (recipientEmail == null) recipientEmail = (String) recipient.get().get("email");
    if (row.get("channel").equals("EMAIL")) {
      boolean usesSharedEmail = Boolean.TRUE.equals(row.get("uses_notification_email"));
      String currentEmail = (String) row.get("notification_email");
      boolean recipientChanged =
          usesSharedEmail
              ? !recipientEmail.equals(currentEmail)
              : !currentEmail.isBlank() || !recipientEmail.equals(recipient.get().get("email"));
      if (!Boolean.TRUE.equals(row.get("email_enabled")) || recipientChanged) {
        jdbc.update(
            "update daily_summary_deliveries set status='FAILED',last_error=? where id=?",
            "Email settings or notification recipient changed",
            id);
        return true;
      }
      jdbc.update(
          "update daily_summary_deliveries set to_email=? where id=? and to_email is null",
          recipientEmail,
          id);
    }
    int attempts = ((Number) row.get("attempts")).intValue() + 1;
    try {
      UUID inboxId = id;
      if (row.get("channel").equals("EMAIL")) {
        var inApp =
            jdbc.queryForList(
                "select id from daily_summary_deliveries where report_id=? and user_id=? and batch_key=? and channel='IN_APP'",
                UUID.class,
                row.get("report_id"),
                user,
                row.get("batch_key"));
        inboxId =
            inApp.isEmpty()
                ? UUID.nameUUIDFromBytes(
                    ("daily-summary/in-app/" + id)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : inApp.get(0);
      }
      jdbc.update(
          """
            insert into notifications (id,organization_id,branch_id,user_id,notification_type,title,message,source_module,source_record_id,status)
            values (?,?,?,?,'DAILY_SALES_SUMMARY',?,?,'reports',?,'UNREAD') on conflict (id) do nothing
            """,
          inboxId,
          org,
          branch,
          user,
          title,
          message,
          report.id());
      if (row.get("channel").equals("EMAIL"))
        email.sendSummary(recipientEmail, title, message, "daily-summary/" + id);
      jdbc.update(
          "update daily_summary_deliveries set status='SENT',attempts=?,sent_at=now(),last_error=null where id=?",
          attempts,
          id);
    } catch (IllegalStateException e) {
      jdbc.update(
          "update daily_summary_deliveries set attempts=?,next_attempt_at=?,last_error=? where id=?",
          attempts,
          java.sql.Timestamp.from(
              Instant.now().plusSeconds(Math.min(3600, 60L << Math.min(attempts - 1, 6)))),
          "Notification delivery failed; check provider configuration and logs",
          id);
      LoggerFactory.getLogger(getClass())
          .warn("Daily summary delivery {} failed: {}", id, e.getMessage());
    }
    return true;
  }

  public static String message(DailySalesReport r) {
    StringBuilder text =
        new StringBuilder("Daily Sales Summary — ")
            .append(r.businessDate())
            .append("\n")
            .append(r.branchName())
            .append(" | ")
            .append(r.timeZone())
            .append(" | 00:00–20:59:59\n")
            .append("Transactions: ")
            .append(r.transactions())
            .append("\nGross sales: ")
            .append(r.grossSales())
            .append("\nDiscounts: ")
            .append(r.discounts())
            .append("\nRefunds: ")
            .append(r.refunds())
            .append(" (")
            .append(r.refundTransactions())
            .append(")")
            .append("\nCancellations: ")
            .append(r.cancellations())
            .append(" (")
            .append(r.cancelledTransactions())
            .append(")")
            .append("\nNet sales before tax: ")
            .append(r.netSales())
            .append("\nNet tax: ")
            .append(r.tax())
            .append("\nTotal revenue: ")
            .append(r.totalRevenue())
            .append(" ")
            .append(r.currencyCode())
            .append("\nItems sold: ")
            .append(r.itemsSold())
            .append("\nPayments (after reversals):\n");
    r.payments()
        .forEach((method, amount) -> text.append(method).append(": ").append(amount).append("\n"));
    text.append("Top products and variants:\n");
    r.topVariants().stream()
        .limit(5)
        .forEach(
            p ->
                text.append(p.get("name"))
                    .append(": ")
                    .append(p.get("quantity"))
                    .append(" units\n"));
    text.append("By cashier (original sales including tax):\n");
    r.cashiers()
        .forEach(
            c ->
                text.append(c.get("name"))
                    .append(": ")
                    .append(c.get("transactions"))
                    .append(" transactions, ")
                    .append(c.get("revenue"))
                    .append("\n"));
    return text.toString();
  }
}
