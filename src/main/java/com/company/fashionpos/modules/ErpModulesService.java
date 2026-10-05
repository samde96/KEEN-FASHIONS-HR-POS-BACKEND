package com.company.fashionpos.modules;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErpModulesService {

  private final JdbcTemplate jdbcTemplate;

  public ErpModulesService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public ModuleSummary procurementSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "procurement",
        "Procurement",
        "Supplier purchasing, RFQs, purchase orders, receiving, and payables.",
        List.of(
            metric(
                "Purchase requests",
                count("purchase_requests", "record", user, BranchScope.REQUIRED),
                "Approval pipeline"),
            metric(
                "Open POs",
                countWhere(
                    "purchase_orders",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "and record.status not in ('CANCELLED', 'CLOSED', 'FULLY_RECEIVED')"),
                "Awaiting delivery"),
            metric(
                "Goods receipts",
                count("goods_receipts", "record", user, BranchScope.REQUIRED),
                "Inventory handoff"),
            moneyMetric(
                "Supplier payables",
                sumWhere(
                    "supplier_invoices",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "record.total_amount - record.paid_amount",
                    "and record.status not in ('PAID', 'VOID')"),
                "Outstanding invoices")),
        recentWorkItems(
            """
            select
              po.id,
              po.po_number as reference,
              supplier.name as title,
              po.status,
              po.total_amount as amount,
              branch.name as branch_name,
              po.expected_delivery as due_date,
              po.created_at
            from purchase_orders po
            join suppliers supplier on supplier.id = po.supplier_id
            join branches branch on branch.id = po.branch_id
            """,
            "po",
            user,
            BranchScope.REQUIRED));
  }

  @Transactional(readOnly = true)
  public ModuleSummary customersSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "customers",
        "Customers",
        "Customer records, KRA information, credit limits, and receivables.",
        List.of(
            metric(
                "Active customers",
                countWhere(
                    "customers",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status = 'ACTIVE'"),
                "Available for sales"),
            metric(
                "Credit hold",
                countWhere(
                    "customers",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status = 'CREDIT_HOLD'"),
                "Blocked credit sales"),
            moneyMetric(
                "Receivables",
                sum("customers", "record", user, BranchScope.OPTIONAL, "record.outstanding_balance"),
                "Customer balances"),
            metric(
                "Business customers",
                countWhere(
                    "customers",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.customer_type = 'BUSINESS'"),
                "KRA PIN capable")),
        recentWorkItems(
            """
            select
              customer.id,
              customer.customer_code as reference,
              customer.name as title,
              customer.status,
              customer.outstanding_balance as amount,
              branch.name as branch_name,
              null as due_date,
              customer.created_at
            from customers customer
            left join branches branch on branch.id = customer.branch_id
            """,
            "customer",
            user,
            BranchScope.OPTIONAL));
  }

  @Transactional(readOnly = true)
  public ModuleSummary crmSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "crm",
        "CRM",
        "Leads, opportunities, activities, and customer follow-up pipeline.",
        List.of(
            metric(
                "Open leads",
                countWhere(
                    "crm_leads",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status not in ('WON', 'LOST')"),
                "Lead pipeline"),
            metric(
                "Open opportunities",
                countWhere(
                    "crm_opportunities",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.stage not in ('WON', 'LOST')"),
                "Sales pipeline"),
            moneyMetric(
                "Pipeline value",
                sumWhere(
                    "crm_opportunities",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "record.expected_value",
                    "and record.stage not in ('WON', 'LOST')"),
                "Expected value"),
            metric(
                "Open activities",
                countWhere(
                    "crm_activities",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status = 'OPEN'"),
                "Tasks and follow-ups")),
        recentWorkItems(
            """
            select
              opportunity.id,
              opportunity.opportunity_number as reference,
              opportunity.title,
              opportunity.stage as status,
              opportunity.expected_value as amount,
              branch.name as branch_name,
              opportunity.expected_close_date as due_date,
              opportunity.created_at
            from crm_opportunities opportunity
            left join branches branch on branch.id = opportunity.branch_id
            """,
            "opportunity",
            user,
            BranchScope.OPTIONAL));
  }

  @Transactional(readOnly = true)
  public ModuleSummary commercialSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "commercial",
        "Quotations & Invoicing",
        "Quotations, sales orders, invoices, payments, and customer receivables.",
        List.of(
            metric(
                "Open quotations",
                countWhere(
                    "quotations",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "and record.status in ('DRAFT', 'SENT', 'VIEWED')"),
                "Awaiting customer decision"),
            metric(
                "Open sales orders",
                countWhere(
                    "sales_orders",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "and record.status not in ('FULFILLED', 'INVOICED', 'CANCELLED')"),
                "Fulfillment pipeline"),
            metric(
                "Outstanding invoices",
                countWhere(
                    "invoices",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "and record.status in ('SENT', 'PARTIALLY_PAID', 'OVERDUE')"),
                "Receivables"),
            moneyMetric(
                "Invoice balance",
                sumWhere(
                    "invoices",
                    "record",
                    user,
                    BranchScope.REQUIRED,
                    "record.total_amount - record.paid_amount",
                    "and record.status in ('SENT', 'PARTIALLY_PAID', 'OVERDUE')"),
                "Amount due")),
        recentWorkItems(
            """
            select
              invoice.id,
              invoice.invoice_number as reference,
              coalesce(customer.name, invoice.customer_kra_pin, 'Walk-in customer') as title,
              invoice.status,
              invoice.total_amount as amount,
              branch.name as branch_name,
              invoice.due_date,
              invoice.created_at
            from invoices invoice
            left join customers customer on customer.id = invoice.customer_id
            join branches branch on branch.id = invoice.branch_id
            """,
            "invoice",
            user,
            BranchScope.REQUIRED));
  }

  @Transactional(readOnly = true)
  public ModuleSummary accountingSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "accounting",
        "Accounting",
        "Chart of accounts, journals, financial periods, tax rules, AR and AP.",
        List.of(
            metric("Accounts", count("accounts", "record", user, BranchScope.NONE), "Chart of accounts"),
            metric(
                "Posted journals",
                countWhere(
                    "journal_entries",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status = 'POSTED'"),
                "Approved entries"),
            moneyMetric(
                "Debits",
                sumJoin(
                    "journal_entry_lines line join journal_entries entry on entry.id = line.journal_entry_id",
                    "line",
                    "entry",
                    user,
                    BranchScope.OPTIONAL,
                    "line.debit_amount",
                    "and entry.status = 'POSTED'"),
                "Posted ledger"),
            moneyMetric(
                "Credits",
                sumJoin(
                    "journal_entry_lines line join journal_entries entry on entry.id = line.journal_entry_id",
                    "line",
                    "entry",
                    user,
                    BranchScope.OPTIONAL,
                    "line.credit_amount",
                    "and entry.status = 'POSTED'"),
                "Posted ledger")),
        recentWorkItems(
            """
            select
              entry.id,
              entry.journal_number as reference,
              entry.description as title,
              entry.status,
              null as amount,
              branch.name as branch_name,
              entry.entry_date as due_date,
              entry.created_at
            from journal_entries entry
            left join branches branch on branch.id = entry.branch_id
            """,
            "entry",
            user,
            BranchScope.OPTIONAL));
  }

  @Transactional(readOnly = true)
  public ModuleSummary etimsSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "etims",
        "eTIMS",
        "KRA eTIMS configuration, fiscal submission queue, retries, and tax reports.",
        List.of(
            metric(
                "Configured branches",
                count("etims_configurations", "record", user, BranchScope.OPTIONAL),
                "eTIMS setup"),
            metric(
                "Pending submissions",
                countWhere(
                    "etims_submission_queue",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status in ('PENDING', 'RETRY_REQUIRED')"),
                "Needs processing"),
            metric(
                "Accepted",
                countWhere(
                    "etims_submission_queue",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status = 'ACCEPTED'"),
                "Fiscalized"),
            metric(
                "Failed",
                countWhere(
                    "etims_submission_queue",
                    "record",
                    user,
                    BranchScope.OPTIONAL,
                    "and record.status in ('FAILED', 'REJECTED')"),
                "Needs review")),
        recentWorkItems(
            """
            select
              submission.id,
              submission.idempotency_key as reference,
              submission.source_type as title,
              submission.status,
              null as amount,
              branch.name as branch_name,
              cast(submission.next_attempt_at as date) as due_date,
              submission.created_at
            from etims_submission_queue submission
            left join branches branch on branch.id = submission.branch_id
            """,
            "submission",
            user,
            BranchScope.OPTIONAL));
  }

  @Transactional(readOnly = true)
  public ModuleSummary notificationsSummary(AuthenticatedUser user) {
    return new ModuleSummary(
        "notifications",
        "Notifications",
        "Operational alerts for approvals, stock, fiscalization, payroll, and payments.",
        List.of(
            metric(
                "Unread",
                countNotifications(user, "and notification.status = 'UNREAD'"),
                "Requires attention"),
            metric(
                "All alerts",
                countNotifications(user, ""),
                "Current organization"),
            metric(
                "Dismissed",
                countNotifications(user, "and notification.status = 'DISMISSED'"),
                "Cleared alerts"),
            metric(
                "Read",
                countNotifications(user, "and notification.status = 'READ'"),
                "Seen alerts")),
        recentNotificationItems(user));
  }

  @Transactional(readOnly = true)
  public List<NotificationResponse> notifications(AuthenticatedUser user) {
    Scope scope = scope(user, "notification", BranchScope.OPTIONAL);
    List<Object> args = new ArrayList<>(scope.args());
    args.add(user.userId());
    return jdbcTemplate.query(
        """
        select
          notification.id,
          notification.notification_type,
          notification.title,
          notification.message,
          notification.source_module,
          notification.source_record_id,
          notification.status,
          notification.created_at,
          notification.read_at
        from notifications notification
        """
            + scope.where()
            + """
              and (notification.user_id is null or notification.user_id = ?)
            order by notification.created_at desc
            limit 50
            """,
        this::mapNotification,
        args.toArray());
  }

  @Transactional(readOnly = true)
  public List<AuditEventResponse> auditEvents(AuthenticatedUser user) {
    return jdbcTemplate.query(
        """
        select
          audit.id,
          audit.branch_id,
          branch.name as branch_name,
          audit.actor_user_id,
          actor.display_name as actor_name,
          audit.action,
          audit.target_type,
          audit.target_id,
          audit.reason,
          audit.correlation_id,
          cast(audit.metadata_json as text) as metadata_json,
          audit.created_at
        from audit_events audit
        left join branches branch on branch.id = audit.branch_id
        left join users actor on actor.id = audit.actor_user_id
        where audit.organization_id = ?
        order by audit.created_at desc
        limit 100
        """,
        this::mapAuditEvent,
        user.organizationId());
  }

  private List<ModuleWorkItem> recentNotificationItems(AuthenticatedUser user) {
    Scope scope = scope(user, "notification", BranchScope.OPTIONAL);
    List<Object> args = new ArrayList<>(scope.args());
    args.add(user.userId());
    return jdbcTemplate.query(
        """
        select
          notification.id,
          coalesce(notification.source_module, notification.notification_type) as reference,
          notification.title,
          notification.status,
          null as amount,
          branch.name as branch_name,
          null as due_date,
          notification.created_at
        from notifications notification
        left join branches branch on branch.id = notification.branch_id
        """
            + scope.where()
            + """
              and (notification.user_id is null or notification.user_id = ?)
            order by notification.created_at desc
            limit 8
            """,
        this::mapWorkItem,
        args.toArray());
  }

  private List<ModuleWorkItem> recentWorkItems(
      String selectSql, String alias, AuthenticatedUser user, BranchScope branchScope) {
    Scope scope = scope(user, alias, branchScope);
    return jdbcTemplate.query(
        selectSql + scope.where() + " order by " + alias + ".created_at desc limit 8",
        this::mapWorkItem,
        scope.args().toArray());
  }

  private ModuleMetric metric(String label, long value, String detail) {
    return new ModuleMetric(label, Long.toString(value), detail);
  }

  private ModuleMetric moneyMetric(String label, BigDecimal value, String detail) {
    return new ModuleMetric(label, value == null ? "0.00" : value.toPlainString(), detail);
  }

  private long count(String table, String alias, AuthenticatedUser user, BranchScope branchScope) {
    return countWhere(table, alias, user, branchScope, "");
  }

  private long countWhere(
      String table,
      String alias,
      AuthenticatedUser user,
      BranchScope branchScope,
      String extraPredicate) {
    Scope scope = scope(user, alias, branchScope);
    Number result =
        jdbcTemplate.queryForObject(
            "select count(*) from " + table + " " + alias + scope.where() + " " + extraPredicate,
            Number.class,
            scope.args().toArray());
    return result == null ? 0 : result.longValue();
  }

  private long countNotifications(AuthenticatedUser user, String extraPredicate) {
    Scope scope = scope(user, "notification", BranchScope.OPTIONAL);
    List<Object> args = new ArrayList<>(scope.args());
    args.add(user.userId());
    Number result =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from notifications notification
            """
                + scope.where()
                + " and (notification.user_id is null or notification.user_id = ?) "
                + extraPredicate,
            Number.class,
            args.toArray());
    return result == null ? 0 : result.longValue();
  }

  private BigDecimal sum(
      String table,
      String alias,
      AuthenticatedUser user,
      BranchScope branchScope,
      String expression) {
    return sumWhere(table, alias, user, branchScope, expression, "");
  }

  private BigDecimal sumWhere(
      String table,
      String alias,
      AuthenticatedUser user,
      BranchScope branchScope,
      String expression,
      String extraPredicate) {
    Scope scope = scope(user, alias, branchScope);
    BigDecimal result =
        jdbcTemplate.queryForObject(
            "select coalesce(sum("
                + expression
                + "), 0) from "
                + table
                + " "
                + alias
                + scope.where()
                + " "
                + extraPredicate,
            BigDecimal.class,
            scope.args().toArray());
    return result == null ? BigDecimal.ZERO : result;
  }

  private BigDecimal sumJoin(
      String fromSql,
      String organizationAlias,
      String branchAlias,
      AuthenticatedUser user,
      BranchScope branchScope,
      String expression,
      String extraPredicate) {
    Scope scope = scope(user, organizationAlias, branchAlias, branchScope);
    BigDecimal result =
        jdbcTemplate.queryForObject(
            "select coalesce(sum("
                + expression
                + "), 0) from "
                + fromSql
                + scope.where()
                + " "
                + extraPredicate,
            BigDecimal.class,
            scope.args().toArray());
    return result == null ? BigDecimal.ZERO : result;
  }

  private Scope scope(AuthenticatedUser user, String alias, BranchScope branchScope) {
    return scope(user, alias, alias, branchScope);
  }

  private Scope scope(
      AuthenticatedUser user, String organizationAlias, String branchAlias, BranchScope branchScope) {
    List<Object> args = new ArrayList<>();
    args.add(user.organizationId());
    StringBuilder where = new StringBuilder(" where ");
    where.append(organizationAlias).append(".organization_id = ?");

    if (branchScope != BranchScope.NONE && !user.branchIds().isEmpty()) {
      String placeholders = placeholders(user.branchIds().size());
      if (branchScope == BranchScope.OPTIONAL) {
        where.append(" and (")
            .append(branchAlias)
            .append(".branch_id is null or ")
            .append(branchAlias)
            .append(".branch_id in (")
            .append(placeholders)
            .append("))");
      } else {
        where.append(" and ")
            .append(branchAlias)
            .append(".branch_id in (")
            .append(placeholders)
            .append(")");
      }
      args.addAll(user.branchIds());
    }

    return new Scope(where.toString(), args);
  }

  private String placeholders(int count) {
    return String.join(", ", java.util.Collections.nCopies(count, "?"));
  }

  private ModuleWorkItem mapWorkItem(ResultSet rs, int rowNum) throws SQLException {
    return new ModuleWorkItem(
        uuid(rs, "id"),
        rs.getString("reference"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getBigDecimal("amount"),
        rs.getString("branch_name"),
        localDate(rs, "due_date"),
        instant(rs, "created_at"));
  }

  private NotificationResponse mapNotification(ResultSet rs, int rowNum) throws SQLException {
    return new NotificationResponse(
        uuid(rs, "id"),
        rs.getString("notification_type"),
        rs.getString("title"),
        rs.getString("message"),
        rs.getString("source_module"),
        uuid(rs, "source_record_id"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "read_at"));
  }

  private AuditEventResponse mapAuditEvent(ResultSet rs, int rowNum) throws SQLException {
    return new AuditEventResponse(
        uuid(rs, "id"),
        uuid(rs, "branch_id"),
        rs.getString("branch_name"),
        uuid(rs, "actor_user_id"),
        rs.getString("actor_name"),
        rs.getString("action"),
        rs.getString("target_type"),
        uuid(rs, "target_id"),
        rs.getString("reason"),
        rs.getString("correlation_id"),
        rs.getString("metadata_json"),
        instant(rs, "created_at"));
  }

  private static UUID uuid(ResultSet rs, String column) throws SQLException {
    String value = rs.getString(column);
    return value == null ? null : UUID.fromString(value);
  }

  private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
    Date value = rs.getDate(column);
    return value == null ? null : value.toLocalDate();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private enum BranchScope {
    NONE,
    REQUIRED,
    OPTIONAL
  }

  private record Scope(String where, List<Object> args) {}
}
