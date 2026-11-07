package com.company.fashionpos.hr;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PayrollService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Nairobi");
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public PayrollService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Map<String, Object> setup(AuthenticatedUser user) {
    Map<String, Object> setup = new LinkedHashMap<>();
    setup.put(
        "employees",
        jdbc.queryForList(
            """
        select e.id, e.version, e.employee_number as "employeeNumber",
          concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "fullName",
          e.user_id as "userId", u.display_name as "userName", u.email as "userEmail",
          coalesce((select string_agg(distinct r.name, ', ' order by r.name)
            from user_roles ur join roles r on r.id=ur.role_id
            where ur.user_id=e.user_id and r.organization_id=e.organization_id), '') as "roleName",
          e.primary_branch_id as "locationId", b.name as "locationName", b.branch_type as "locationType",
          e.phone, e.basic_salary as "baseSalary", e.joining_date as "startDate",
          e.contract_end_date as "endDate", e.active
        from employees e join branches b on b.id=e.primary_branch_id
        left join users u on u.id=e.user_id and u.organization_id=e.organization_id
        where e.organization_id=? and b.branch_type in ('STORE','SHOP')
        order by e.active desc, e.last_name, e.first_name
        """,
            user.organizationId()));
    List<Map<String, Object>> users =
        jdbc.queryForList(
            """
        select u.id, u.display_name as name, u.email,
          coalesce(json_agg(distinct jsonb_build_object('id', b.id, 'name', b.name, 'type', b.branch_type))
            filter (where b.id is not null), '[]'::json) as locations,
          coalesce(string_agg(distinct r.name, ', ' order by r.name), '') as roles
        from users u
        left join user_branch_assignments a on a.user_id=u.id
        left join branches b on b.id=a.branch_id and b.organization_id=u.organization_id
          and b.branch_type in ('STORE','SHOP') and b.status='ACTIVE'
        left join user_roles ur on ur.user_id=u.id
        left join roles r on r.id=ur.role_id and r.organization_id=u.organization_id
        where u.organization_id=? and u.status='ACTIVE'
        group by u.id order by u.display_name
        """,
            user.organizationId());
    users.forEach(row -> row.put("locations", jsonList(row.get("locations"))));
    setup.put("users", users);
    setup.put(
        "locations",
        jdbc.queryForList(
            """
        select id, name, branch_type as type, status='ACTIVE' as active
        from branches where organization_id=? and branch_type in ('STORE','SHOP')
        order by name
        """,
            user.organizationId()));
    setup.put(
        "rules",
        jdbc.queryForList(
            """
        select r.id, r.version, r.branch_id as "locationId", b.name as "locationName",
          r.effective_from as "effectiveFrom", r.daily_target as "dailyTarget",
          r.bonus_mode as "bonusMode", r.bonus_value as "bonusValue", r.enabled
        from hr_bonus_rules r join branches b on b.id=r.branch_id
        where r.organization_id=? order by r.effective_from desc, b.name
        """,
            user.organizationId()));
    return setup;
  }

  public Map<String, Object> workspace(AuthenticatedUser user, String month) {
    YearMonth period = parseMonth(month);
    LocalDate first = period.atDay(1);
    LocalDate last = period.atEndOfMonth();
    List<Map<String, Object>> lines =
        jdbc.queryForList(
            """
        with staff as (
          select e.id, e.employee_number as "employeeNumber",
            concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
            e.user_id as "userId", e.primary_branch_id as "locationId", b.name as "locationName",
            b.branch_type as "locationType",
            coalesce((select p.monthly_salary from payroll_salary_plans p
              where p.organization_id=e.organization_id and p.employee_id=e.id and p.effective_month<=?
              order by p.effective_month desc limit 1), e.basic_salary) as "baseSalary",
            coalesce((select json_agg(jsonb_build_object('locationId',b2.id,'locationName',b2.name,
              'percentage',fs.percentage,
              'amount',round(sp.monthly_salary*fs.percentage/100,2)) order by b2.name)
              from payroll_salary_plans sp join payroll_funding_shares fs on fs.plan_id=sp.id
              join branches b2 on b2.id=fs.branch_id
              where sp.organization_id=e.organization_id and sp.employee_id=e.id and sp.effective_month<=?
                and sp.effective_month=(select max(sp2.effective_month) from payroll_salary_plans sp2
                  where sp2.organization_id=e.organization_id and sp2.employee_id=e.id and sp2.effective_month<=?)),
              '[]'::json) as funding
          from employees e join branches b on b.id=e.primary_branch_id
          where e.organization_id=? and e.active=true and e.joining_date<=?
            and (e.contract_end_date is null or e.contract_end_date>=?)
            and b.branch_type in ('STORE','SHOP')
        ), sales as (
          select e.id as "employeeId", s.branch_id as "locationId",
            (s.sold_at at time zone 'Africa/Nairobi')::date as "saleDate",
            sum(s.total_amount) as amount, count(*) as transactions
          from sales s join employees e on e.user_id=s.cashier_id and e.organization_id=s.organization_id
          join branches b on b.id=s.branch_id and b.branch_type='SHOP'
          where s.organization_id=? and s.status='COMPLETED'
            and s.sold_at >= ? and s.sold_at < ?
          group by e.id, s.branch_id, (s.sold_at at time zone 'Africa/Nairobi')::date
        ), sales_totals as (
          select "employeeId", sum(amount) as sales, sum(transactions) as transactions
          from sales group by "employeeId"
        ), bonus_daily as (
          select s."employeeId", s."saleDate",
            sum(case when r.id is null or not r.enabled or s.amount<=r.daily_target then 0
              when r.bonus_mode='FIXED' then r.bonus_value
              else round((s.amount-r.daily_target)*r.bonus_value/100, 2) end) as amount
          from sales s join staff e on e.id=s."employeeId"
          left join lateral (select rule.* from hr_bonus_rules rule
            where rule.organization_id=? and rule.branch_id=s."locationId"
              and rule.effective_from<=s."saleDate"
            order by rule.effective_from desc limit 1) r on true
          group by s."employeeId", s."saleDate"
        ), overrides as (
          select distinct on (employee_id, effective_date) employee_id, effective_date, amount
          from hr_payroll_adjustments
          where organization_id=? and kind='AUTO_BONUS_OVERRIDE' and active=true
            and effective_date between ? and ?
          order by employee_id, effective_date, updated_at desc, created_at desc
        ), automatic as (
          select d."employeeId", sum(coalesce(o.amount,d.amount)) as amount,
            t.sales, t.transactions
          from bonus_daily d join sales_totals t on t."employeeId"=d."employeeId"
          left join overrides o on o.employee_id=d."employeeId" and o.effective_date=d."saleDate"
          group by d."employeeId", t.sales, t.transactions
        )
        select e.*, coalesce(a.amount,0) as "automaticBonus",
          coalesce(a.sales,0) as netSales, coalesce(a.transactions,0) as transactions,
          coalesce(adj.manual_bonus,0) as "manualBonus",
          coalesce(adj.other_deductions,0) as "otherDeductions",
          coalesce(loss.loss_deductions,0) as "lossDeductions",
          e."baseSalary"+coalesce(a.amount,0)+coalesce(adj.manual_bonus,0) as "grossPay",
          e."baseSalary"+coalesce(a.amount,0)+coalesce(adj.manual_bonus,0)
            -coalesce(adj.other_deductions,0)-coalesce(loss.loss_deductions,0) as "netPay"
        from staff e
        left join automatic a on a."employeeId"=e.id
        left join lateral (
          select sum(case when kind='MANUAL_BONUS' then amount else 0 end) as manual_bonus,
            sum(case when kind='DEDUCTION' then amount else 0 end) as other_deductions
          from hr_payroll_adjustments where organization_id=? and employee_id=e.id and active=true
            and effective_date between ? and ?
        ) adj on true
        left join lateral (
          select sum(a.amount) as loss_deductions
          from hr_payroll_loss_allocations a join hr_payroll_losses l on l.id=a.loss_id
          where l.organization_id=? and a.employee_id=e.id and l.deduction_date between ? and ?
        ) loss on true
        order by e."employeeName"
        """,
            first,
            first,
            first,
            user.organizationId(),
            last,
            first,
            user.organizationId(),
            Timestamp.from(first.atStartOfDay(BUSINESS_ZONE).toInstant()),
            Timestamp.from(last.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()),
            user.organizationId(),
            user.organizationId(),
            first,
            last,
            user.organizationId(),
            first,
            last,
            user.organizationId(),
            first,
            last);
    lines.forEach(line -> line.put("funding", jsonList(line.get("funding"))));

    BigDecimal totalBase = sum(lines, "baseSalary");
    BigDecimal totalBonuses = sum(lines, "automaticBonus").add(sum(lines, "manualBonus"));
    BigDecimal totalDeductions = sum(lines, "lossDeductions").add(sum(lines, "otherDeductions"));
    BigDecimal totalNet = sum(lines, "netPay");
    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("month", month);
    preview.put("lines", lines);
    preview.put("totalBaseSalary", totalBase);
    preview.put("totalBonuses", totalBonuses);
    preview.put("totalDeductions", totalDeductions);
    preview.put("totalNetPay", totalNet);
    preview.put("warnings", List.of());
    preview.put("token", token(preview));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month);
    result.put("preview", preview);
    result.put(
        "adjustments",
        jdbc.queryForList(
            """
        select a.id, a.version, a.request_key as "requestKey", a.employee_id as "employeeId",
          concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          e.primary_branch_id as "locationId", a.effective_date as "effectiveDate",
          a.kind, a.amount, a.reason, a.active
        from hr_payroll_adjustments a join employees e on e.id=a.employee_id
        where a.organization_id=? and a.effective_date between ? and ?
        order by a.effective_date desc
        """,
            user.organizationId(),
            first,
            last));
    List<Map<String, Object>> losses =
        jdbc.queryForList(
            """
        select l.id, l.version, l.request_key as "requestKey", l.source_key as "sourceKey",
          l.branch_id as "locationId", b.name as "locationName", l.occurred_on as "occurredOn",
          l.deduction_date as "deductionDate", l.amount, l.recoverable_amount as "recoverableAmount",
          l.amount-l.recoverable_amount as "waivedAmount", l.allocation_mode as "allocationMode",
          l.reason
        from hr_payroll_losses l join branches b on b.id=l.branch_id
        where l.organization_id=? and l.deduction_date between ? and ?
        order by l.occurred_on desc
        """,
            user.organizationId(),
            first,
            last);
    for (Map<String, Object> loss : losses) {
      loss.put(
          "allocations",
          jdbc.queryForList(
              """
          select a.employee_id as "employeeId",
            concat_ws(' ',e.first_name,e.middle_name,e.last_name) as "employeeName", a.amount
          from hr_payroll_loss_allocations a join employees e on e.id=a.employee_id
          where a.loss_id=? order by e.last_name,e.first_name
          """,
              loss.get("id")));
    }
    result.put("losses", losses);
    result.put(
        "detectedLosses",
        jdbc.queryForList(
            """
        select concat('SHIFT:', s.id) as "sourceKey", s.branch_id as "locationId", b.name as "locationName",
          (s.closed_at at time zone 'Africa/Nairobi')::date as date,
          'CASHIER_SHIFT' as source, s.expected_cash-s.closing_cash as amount,
          l.id as "recordedLossId"
        from cashier_shifts s join branches b on b.id=s.branch_id
        left join hr_payroll_losses l on l.organization_id=s.organization_id and l.source_key=concat('SHIFT:',s.id)
        where s.organization_id=? and s.status='CLOSED' and s.closed_at>=? and s.closed_at<?
          and s.closing_cash<s.expected_cash
        order by s.closed_at desc
        """,
            user.organizationId(),
            Timestamp.from(first.atStartOfDay(BUSINESS_ZONE).toInstant()),
            Timestamp.from(last.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant())));
    List<Map<String, Object>> runs =
        jdbc.queryForList(
            """
        select id, version, status, snapshot, approved_at as "approvedAt", paid_at as "paidAt",
          payment_reference as "paymentReference", reopen_reason as "reopenReason"
        from hr_payroll_runs where organization_id=? and period=?
        """,
            user.organizationId(),
            first);
    result.put("run", runs.isEmpty() ? null : readRun(runs.get(0)));
    return result;
  }

  @Transactional
  public Map<String, Object> saveRule(AuthenticatedUser user, UUID id, Map<String, Object> input) {
    UUID branchId = uuid(input.get("locationId"));
    requireBranch(user, branchId, true);
    LocalDate effectiveFrom = date(input.get("effectiveFrom"), "effectiveFrom");
    ensurePeriodEditable(user, YearMonth.from(effectiveFrom));
    BigDecimal target = amount(input.get("dailyTarget"), "dailyTarget");
    BigDecimal value = amount(input.get("bonusValue"), "bonusValue");
    String mode = text(input.get("bonusMode"), "bonusMode");
    if (!List.of("FIXED", "PERCENT_EXCESS").contains(mode))
      bad("Choose a supported bonus calculation.");
    if (mode.equals("PERCENT_EXCESS") && value.compareTo(new BigDecimal("100")) > 0)
      bad("Bonus percentage cannot exceed 100.");
    boolean enabled = Boolean.TRUE.equals(input.get("enabled"));
    if (id == null) {
      UUID newId = UUID.randomUUID();
      jdbc.update(
          """
          insert into hr_bonus_rules(id, organization_id, branch_id, effective_from, daily_target,
            bonus_mode, bonus_value, enabled) values (?,?,?,?,?,?,?,?)
          on conflict (branch_id, effective_from) do update set daily_target=excluded.daily_target,
            bonus_mode=excluded.bonus_mode, bonus_value=excluded.bonus_value,
            enabled=excluded.enabled, updated_at=now(), version=hr_bonus_rules.version+1
          """,
          newId,
          user.organizationId(),
          branchId,
          effectiveFrom,
          target,
          mode,
          value,
          enabled);
      id =
          jdbc.queryForObject(
              """
          select id from hr_bonus_rules where organization_id=? and branch_id=? and effective_from=?
          """,
              UUID.class,
              user.organizationId(),
              branchId,
              effectiveFrom);
    } else {
      int updated =
          jdbc.update(
              """
          update hr_bonus_rules set branch_id=?, effective_from=?, daily_target=?, bonus_mode=?,
            bonus_value=?, enabled=?, updated_at=now(), version=version+1
          where id=? and organization_id=?
          """,
              branchId,
              effectiveFrom,
              target,
              mode,
              value,
              enabled,
              id,
              user.organizationId());
      if (updated != 1) notFound("Bonus rule not found.");
    }
    return jdbc.queryForMap(
        """
        select r.id, r.version, r.branch_id as "locationId", b.name as "locationName",
          r.effective_from as "effectiveFrom", r.daily_target as "dailyTarget",
          r.bonus_mode as "bonusMode", r.bonus_value as "bonusValue", r.enabled
        from hr_bonus_rules r join branches b on b.id=r.branch_id where r.id=? and r.organization_id=?
        """,
        id,
        user.organizationId());
  }

  @Transactional
  public Map<String, Object> saveAdjustment(
      AuthenticatedUser user, UUID id, Map<String, Object> input) {
    UUID employeeId = uuid(input.get("employeeId"));
    requireEmployee(user, employeeId);
    UUID requestKey =
        input.get("requestKey") == null ? UUID.randomUUID() : uuid(input.get("requestKey"));
    if (id == null) {
      List<UUID> duplicate =
          jdbc.queryForList(
              """
          select id from hr_payroll_adjustments where organization_id=? and request_key=?
          """,
              UUID.class,
              user.organizationId(),
              requestKey);
      if (!duplicate.isEmpty()) return adjustment(user, duplicate.get(0));
    }
    LocalDate effectiveDate = date(input.get("effectiveDate"), "effectiveDate");
    ensurePeriodEditable(user, YearMonth.from(effectiveDate));
    if (id != null) {
      List<LocalDate> existingDates =
          jdbc.queryForList(
              """
          select effective_date from hr_payroll_adjustments where id=? and organization_id=?
          """,
              LocalDate.class,
              id,
              user.organizationId());
      if (existingDates.isEmpty()) notFound("Payroll adjustment not found.");
      ensurePeriodEditable(user, YearMonth.from(existingDates.get(0)));
    }
    String kind = text(input.get("kind"), "kind");
    if (!List.of("MANUAL_BONUS", "AUTO_BONUS_OVERRIDE", "DEDUCTION").contains(kind))
      bad("Choose a valid adjustment type.");
    BigDecimal amount = amount(input.get("amount"), "amount");
    String reason = text(input.get("reason"), "reason");
    if (reason.length() > 500) bad("Reason must be 500 characters or fewer.");
    boolean active = !Boolean.FALSE.equals(input.get("active"));
    if (kind.equals("AUTO_BONUS_OVERRIDE") && active) {
      Boolean duplicate =
          id == null
              ? jdbc.queryForObject(
                  """
                  select exists(select 1 from hr_payroll_adjustments where organization_id=? and employee_id=?
                    and effective_date=? and kind='AUTO_BONUS_OVERRIDE' and active=true)
                  """,
                  Boolean.class,
                  user.organizationId(),
                  employeeId,
                  effectiveDate)
              : jdbc.queryForObject(
                  """
                  select exists(select 1 from hr_payroll_adjustments where organization_id=? and employee_id=?
                    and effective_date=? and kind='AUTO_BONUS_OVERRIDE' and active=true and id<>?)
                  """,
                  Boolean.class,
                  user.organizationId(),
                  employeeId,
                  effectiveDate,
                  id);
      if (Boolean.TRUE.equals(duplicate))
        conflict("An automatic bonus override already exists for this employee and date.");
    }
    if (id == null) {
      id = UUID.randomUUID();
      jdbc.update(
          """
          insert into hr_payroll_adjustments(id, organization_id, request_key, employee_id,
            effective_date, kind, amount, reason, changed_by_user_id, active)
          values (?,?,?,?,?,?,?,?,?,?)
          """,
          id,
          user.organizationId(),
          requestKey,
          employeeId,
          effectiveDate,
          kind,
          amount,
          reason,
          user.userId(),
          active);
    } else {
      int updated =
          jdbc.update(
              """
          update hr_payroll_adjustments set employee_id=?, effective_date=?, kind=?, amount=?,
            reason=?, changed_by_user_id=?, active=?, updated_at=now(), version=version+1
          where id=? and organization_id=?
          """,
              employeeId,
              effectiveDate,
              kind,
              amount,
              reason,
              user.userId(),
              active,
              id,
              user.organizationId());
      if (updated != 1) notFound("Payroll adjustment not found.");
    }
    return jdbc.queryForMap(
        """
        select a.id, a.version, a.request_key as "requestKey", a.employee_id as "employeeId",
          concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          e.primary_branch_id as "locationId", a.effective_date as "effectiveDate",
          a.kind, a.amount, a.reason, a.active
        from hr_payroll_adjustments a join employees e on e.id=a.employee_id
        where a.id=? and a.organization_id=?
        """,
        id,
        user.organizationId());
  }

  @Transactional
  public Map<String, Object> saveLoss(AuthenticatedUser user, UUID id, Map<String, Object> input) {
    UUID branchId = uuid(input.get("locationId"));
    requireBranch(user, branchId, false);
    LocalDate occurredOn = date(input.get("occurredOn"), "occurredOn");
    LocalDate deductionDate = date(input.get("deductionDate"), "deductionDate");
    ensurePeriodEditable(user, YearMonth.from(deductionDate));
    if (deductionDate.isBefore(occurredOn))
      bad("Payroll deduction date cannot precede the loss date.");
    BigDecimal total = amount(input.get("amount"), "amount");
    if (total.signum() <= 0) bad("Loss amount must be greater than zero.");
    BigDecimal recoverable = amount(input.get("recoverableAmount"), "recoverableAmount");
    if (recoverable.compareTo(total) > 0) bad("Recoverable amount cannot exceed the total loss.");
    String mode = text(input.get("allocationMode"), "allocationMode");
    if (!List.of("ALL_BRANCH", "SELECTED", "CUSTOM", "WAIVED").contains(mode))
      bad("Choose a valid loss allocation.");
    String reason = text(input.get("reason"), "reason");
    if (reason.length() > 500) bad("Reason must be 500 characters or fewer.");
    List<Map<String, Object>> allocations = allocationInput(input.get("allocations"));
    BigDecimal allocated =
        allocations.stream()
            .map(a -> amount(a.get("amount"), "allocation"))
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    if (mode.equals("WAIVED")) {
      recoverable = ZERO;
      allocations = List.of();
    } else if (recoverable.compareTo(allocated) != 0
        || (recoverable.signum() > 0 && allocations.isEmpty())) {
      bad("Staff allocations must add up exactly to the recoverable amount.");
    }
    for (Map<String, Object> allocation : allocations) {
      UUID employeeId = uuid(allocation.get("employeeId"));
      requireEmployee(user, employeeId);
      if (!"WAIVED".equals(mode)) {
        Boolean eligible =
            jdbc.queryForObject(
                """
            select exists(select 1 from attendance_records a
              join employees e on e.id=a.employee_id
              where a.organization_id=? and a.employee_id=? and a.branch_id=? and a.attendance_date=?
                and e.active=true and a.status in ('PRESENT','LATE','HALF_DAY') and a.clock_in is not null)
            """,
                Boolean.class,
                user.organizationId(),
                employeeId,
                branchId,
                occurredOn);
        if (!Boolean.TRUE.equals(eligible))
          bad(
              "Only active staff with confirmed attendance at the affected location may be charged.");
      }
    }
    UUID requestKey =
        input.get("requestKey") == null ? UUID.randomUUID() : uuid(input.get("requestKey"));
    List<UUID> duplicate =
        jdbc.queryForList(
            """
        select id from hr_payroll_losses where organization_id=? and request_key=?
        """,
            UUID.class,
            user.organizationId(),
            requestKey);
    if (id == null && !duplicate.isEmpty()) return loss(user, duplicate.get(0));
    if (id == null && input.get("sourceKey") != null) {
      List<UUID> fromSource =
          jdbc.queryForList(
              """
          select id from hr_payroll_losses where organization_id=? and source_key=?
          """,
              UUID.class,
              user.organizationId(),
              input.get("sourceKey").toString());
      if (!fromSource.isEmpty()) return loss(user, fromSource.get(0));
    }
    if (id == null) {
      id = UUID.randomUUID();
      jdbc.update(
          """
          insert into hr_payroll_losses(id, organization_id, request_key, source_key, branch_id,
            occurred_on, deduction_date, amount, recoverable_amount, allocation_mode, reason, changed_by_user_id)
          values (?,?,?,?,?,?,?,?,?,?,?,?)
          """,
          id,
          user.organizationId(),
          requestKey,
          input.get("sourceKey"),
          branchId,
          occurredOn,
          deductionDate,
          total,
          recoverable,
          mode,
          reason,
          user.userId());
    } else {
      int updated =
          jdbc.update(
              """
          update hr_payroll_losses set branch_id=?, occurred_on=?, deduction_date=?, amount=?,
            recoverable_amount=?, allocation_mode=?, reason=?, changed_by_user_id=?,
            updated_at=now(), version=version+1 where id=? and organization_id=?
          """,
              branchId,
              occurredOn,
              deductionDate,
              total,
              recoverable,
              mode,
              reason,
              user.userId(),
              id,
              user.organizationId());
      if (updated != 1) notFound("Payroll loss not found.");
      jdbc.update("delete from hr_payroll_loss_allocations where loss_id=?", id);
    }
    for (Map<String, Object> allocation : allocations) {
      jdbc.update(
          """
          insert into hr_payroll_loss_allocations(id, loss_id, employee_id, amount)
          values (?,?,?,?)
          """,
          UUID.randomUUID(),
          id,
          uuid(allocation.get("employeeId")),
          amount(allocation.get("amount"), "allocation"));
    }
    return loss(user, id);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> lossEligibility(
      AuthenticatedUser user, UUID branchId, LocalDate date) {
    requireBranch(user, branchId, false);
    return jdbc.queryForList(
        """
        select e.id as "employeeId", concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          a.clock_in as "clockIn", a.clock_out as "clockOut"
        from attendance_records a join employees e on e.id=a.employee_id
        where a.organization_id=? and a.branch_id=? and a.attendance_date=? and e.active=true
          and a.status in ('PRESENT','LATE','HALF_DAY') and a.clock_in is not null
        order by e.last_name, e.first_name
        """,
        user.organizationId(),
        branchId,
        date);
  }

  @Transactional
  public Map<String, Object> funding(
      AuthenticatedUser user, String month, Map<String, Object> input) {
    LocalDate effectiveMonth = parseMonth(month).atDay(1);
    ensurePeriodEditable(user, YearMonth.from(effectiveMonth));
    UUID employeeId = uuid(input.get("employeeId"));
    requireEmployee(user, employeeId);
    BigDecimal salary = amount(input.get("monthlySalary"), "monthlySalary");
    String reason = text(input.get("reason"), "reason");
    List<Map<String, Object>> shares = allocationInput(input.get("shares"));
    BigDecimal total = ZERO;
    for (Map<String, Object> share : shares) {
      UUID branchId = uuid(share.get("locationId"));
      requireBranch(user, branchId, true);
      total = total.add(amount(share.get("percentage"), "percentage"));
    }
    if (!shares.isEmpty() && total.compareTo(new BigDecimal("100")) != 0)
      bad("Salary funding shares must total exactly 100%.");
    UUID planId =
        jdbc.query(
            """
        insert into payroll_salary_plans(id, organization_id, employee_id, effective_month,
          monthly_salary, reason, changed_by_user_id)
        values (?, ?, ?, ?, ?, ?, ?)
        on conflict (employee_id, effective_month) do update set monthly_salary=excluded.monthly_salary,
          reason=excluded.reason, changed_by_user_id=excluded.changed_by_user_id,
          updated_at=now(), version=payroll_salary_plans.version+1
        returning id
        """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            UUID.randomUUID(),
            user.organizationId(),
            employeeId,
            effectiveMonth,
            salary,
            reason,
            user.userId());
    jdbc.update("delete from payroll_funding_shares where plan_id=?", planId);
    for (Map<String, Object> share : shares) {
      jdbc.update(
          "insert into payroll_funding_shares(plan_id, branch_id, percentage) values (?,?,?)",
          planId,
          uuid(share.get("locationId")),
          amount(share.get("percentage"), "percentage"));
    }
    Map<String, Object> result =
        jdbc.queryForMap(
            """
        select p.id, p.employee_id as "employeeId", p.effective_month as month,
          p.monthly_salary as "monthlySalary", p.reason,
          coalesce(json_agg(jsonb_build_object('locationId',b.id,'locationName',b.name,'percentage',s.percentage))
            filter (where b.id is not null), '[]'::json) as shares
        from payroll_salary_plans p left join payroll_funding_shares s on s.plan_id=p.id
        left join branches b on b.id=s.branch_id
        where p.id=? group by p.id
        """,
            planId);
    result.put("shares", jsonList(result.get("shares")));
    return result;
  }

  public Map<String, Object> funding(AuthenticatedUser user, String month) {
    LocalDate effectiveMonth = parseMonth(month).atDay(1);
    List<Map<String, Object>> plans =
        jdbc.queryForList(
            """
        select distinct on (p.employee_id) p.id, p.employee_id as "employeeId",
          concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          p.effective_month as month, p.monthly_salary as "monthlySalary", p.reason,
          coalesce((select json_agg(jsonb_build_object('locationId',b.id,'locationName',b.name,'percentage',s.percentage))
            from payroll_funding_shares s join branches b on b.id=s.branch_id where s.plan_id=p.id),'[]'::json) as shares
        from payroll_salary_plans p join employees e on e.id=p.employee_id
        where p.organization_id=? and p.effective_month<=?
        order by p.employee_id, p.effective_month desc
        """,
            user.organizationId(),
            effectiveMonth);
    plans.forEach(plan -> plan.put("shares", jsonList(plan.get("shares"))));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month);
    result.put("plans", plans);
    result.put(
        "employees",
        jdbc.queryForList(
            """
        select e.id as "employeeId", concat_ws(' ',e.first_name,e.middle_name,e.last_name) as "employeeName",
          e.primary_branch_id as "locationId", b.name as "locationName",
          coalesce((select p.monthly_salary from payroll_salary_plans p where p.employee_id=e.id
            and p.organization_id=e.organization_id and p.effective_month<=?
            order by p.effective_month desc limit 1), e.basic_salary) as "monthlySalary"
        from employees e join branches b on b.id=e.primary_branch_id
        where e.organization_id=? and e.active=true order by e.last_name,e.first_name
        """,
            effectiveMonth,
            user.organizationId()));
    return result;
  }

  @Transactional
  public Map<String, Object> payrollAction(
      AuthenticatedUser user, String month, String action, Map<String, Object> command) {
    YearMonth period = parseMonth(month);
    LocalDate date = period.atDay(1);
    List<Map<String, Object>> existing =
        jdbc.queryForList(
            """
        select id, version, status, snapshot, payment_reference as "paymentReference"
        from hr_payroll_runs where organization_id=? and period=? for update
        """,
            user.organizationId(),
            date);
    if (action.equals("approve")) {
      if (!existing.isEmpty() && !existing.get(0).get("status").equals("REOPENED"))
        conflict("This payroll period is already approved or paid.");
      Map<String, Object> preview = map(workspace(user, month).get("preview"));
      if (!java.util.Objects.equals(command.get("previewToken"), preview.get("token")))
        conflict("Payroll changed after this preview. Refresh and review the updated amounts.");
      UUID runId = existing.isEmpty() ? UUID.randomUUID() : (UUID) existing.get(0).get("id");
      if (existing.isEmpty()) {
        jdbc.update(
            """
            insert into hr_payroll_runs(id, organization_id, period, status, snapshot, approved_by_user_id, approved_at)
            values (?,?,?,'APPROVED',?,?,now())
            """,
            runId,
            user.organizationId(),
            date,
            mapper.valueToTree(preview).toString(),
            user.userId());
      } else {
        jdbc.update(
            """
            update hr_payroll_runs set status='APPROVED', snapshot=?, approved_by_user_id=?,
              approved_at=now(), paid_at=null, payment_reference=null, reopen_reason=null,
              updated_at=now(), version=version+1 where id=? and organization_id=?
            """,
            mapper.valueToTree(preview).toString(),
            user.userId(),
            runId,
            user.organizationId());
      }
      return run(user, runId);
    }
    if (existing.isEmpty()) notFound("No payroll approval exists for this period.");
    Map<String, Object> run = existing.get(0);
    UUID runId = (UUID) run.get("id");
    if (action.equals("paid")) {
      if (!"APPROVED".equals(run.get("status")))
        conflict("Only approved payroll can be marked paid.");
      String reference = text(command.get("paymentReference"), "paymentReference");
      if (reference.length() > 180) bad("Payment reference must be 180 characters or fewer.");
      jdbc.update(
          """
          update hr_payroll_runs set status='PAID', paid_at=now(), payment_reference=?,
            updated_at=now(), version=version+1 where id=? and organization_id=?
          """,
          reference,
          runId,
          user.organizationId());
      jdbc.update(
          """
          insert into hr_payroll_payments(id, organization_id, run_id, period, snapshot,
            payment_reference, recorded_by_user_id, paid_at)
          values (?,?,?,?,?,?,?,now())
          """,
          UUID.randomUUID(),
          user.organizationId(),
          runId,
          date,
          run.get("snapshot"),
          reference,
          user.userId());
    } else if (action.equals("reopen")) {
      if (!List.of("APPROVED", "PAID").contains(run.get("status")))
        conflict("This payroll period is already open.");
      String reason = text(command.get("reason"), "reason");
      if (reason.length() > 500) bad("Reopen reason must be 500 characters or fewer.");
      jdbc.update(
          """
          update hr_payroll_runs set status='REOPENED', reopen_reason=?, paid_at=null,
            updated_at=now(), version=version+1 where id=? and organization_id=?
          """,
          reason,
          runId,
          user.organizationId());
    } else {
      bad("Unsupported payroll action.");
    }
    return run(user, runId);
  }

  public Map<String, Object> reports(AuthenticatedUser user, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) bad("Choose a valid report date range.");
    List<Map<String, Object>> payments =
        jdbc.queryForList(
            """
        select p.id, p.period, p.payment_reference as "paymentReference", p.paid_at as "paidAt",
          p.snapshot
        from hr_payroll_payments p where p.organization_id=? and p.period between ? and ?
        order by p.period desc, p.paid_at desc
        """,
            user.organizationId(),
            from.withDayOfMonth(1),
            to.withDayOfMonth(1));
    List<Map<String, Object>> monthly = new ArrayList<>();
    List<Map<String, Object>> statements = new ArrayList<>();
    for (Map<String, Object> payment : payments) {
      Map<String, Object> snapshot = parseSnapshot(payment.get("snapshot").toString());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("period", payment.get("period"));
      row.put("paymentReference", payment.get("paymentReference"));
      row.put("paidAt", payment.get("paidAt"));
      row.put("totalNetPay", snapshot.get("totalNetPay"));
      row.put("totalBonuses", snapshot.get("totalBonuses"));
      row.put("totalDeductions", snapshot.get("totalDeductions"));
      monthly.add(row);
      Object rawLines = snapshot.get("lines");
      if (rawLines instanceof List<?> lines) {
        for (Object rawLine : lines) {
          Map<String, Object> line = mapper.convertValue(rawLine, new TypeReference<>() {});
          Map<String, Object> statement = new LinkedHashMap<>();
          statement.put("period", payment.get("period"));
          statement.put("paymentReference", payment.get("paymentReference"));
          statement.put("paidAt", payment.get("paidAt"));
          statement.put("employeeId", line.get("id"));
          statement.put("employeeNumber", line.get("employeeNumber"));
          statement.put("employeeName", line.get("employeeName"));
          statement.put("baseSalary", line.get("baseSalary"));
          statement.put("automaticBonus", line.get("automaticBonus"));
          statement.put("manualBonus", line.get("manualBonus"));
          statement.put("lossDeductions", line.get("lossDeductions"));
          statement.put("otherDeductions", line.get("otherDeductions"));
          statement.put("grossPay", line.get("grossPay"));
          statement.put("netPay", line.get("netPay"));
          statements.add(statement);
        }
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("from", from);
    result.put("to", to);
    result.put("monthly", monthly);
    result.put("statements", statements);
    result.put(
        "paidTotal",
        monthly.stream().map(m -> decimal(m.get("totalNetPay"))).reduce(ZERO, BigDecimal::add));
    return result;
  }

  private Map<String, Object> run(AuthenticatedUser user, UUID id) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
        select id, version, status, snapshot, approved_at as "approvedAt", paid_at as "paidAt",
          payment_reference as "paymentReference", reopen_reason as "reopenReason"
        from hr_payroll_runs where id=? and organization_id=?
        """,
            id,
            user.organizationId());
    if (rows.isEmpty()) notFound("Payroll run not found.");
    return readRun(rows.get(0));
  }

  private Map<String, Object> readRun(Map<String, Object> row) {
    Map<String, Object> result = new LinkedHashMap<>(row);
    result.put("payroll", parseSnapshot(row.get("snapshot").toString()));
    result.remove("snapshot");
    return result;
  }

  private Map<String, Object> loss(AuthenticatedUser user, UUID id) {
    Map<String, Object> result =
        jdbc.queryForMap(
            """
        select l.id, l.version, l.request_key as "requestKey", l.source_key as "sourceKey",
          l.branch_id as "locationId", b.name as "locationName", l.occurred_on as "occurredOn",
          l.deduction_date as "deductionDate", l.amount, l.recoverable_amount as "recoverableAmount",
          l.amount-l.recoverable_amount as "waivedAmount", l.allocation_mode as "allocationMode", l.reason
        from hr_payroll_losses l join branches b on b.id=l.branch_id
        where l.id=? and l.organization_id=?
        """,
            id,
            user.organizationId());
    result.put(
        "allocations",
        jdbc.queryForList(
            """
        select a.employee_id as "employeeId", concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          a.amount from hr_payroll_loss_allocations a join employees e on e.id=a.employee_id
        where a.loss_id=? order by e.last_name,e.first_name
        """,
            id));
    return result;
  }

  private Map<String, Object> adjustment(AuthenticatedUser user, UUID id) {
    return jdbc.queryForMap(
        """
        select a.id, a.version, a.request_key as "requestKey", a.employee_id as "employeeId",
          concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
          e.primary_branch_id as "locationId", a.effective_date as "effectiveDate",
          a.kind, a.amount, a.reason, a.active
        from hr_payroll_adjustments a join employees e on e.id=a.employee_id
        where a.id=? and a.organization_id=?
        """,
        id,
        user.organizationId());
  }

  private List<Map<String, Object>> jsonList(Object value) {
    if (value == null) return List.of();
    try {
      return mapper.readValue(value.toString(), new TypeReference<>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("A saved payroll list is invalid.", exception);
    }
  }

  private void ensurePeriodEditable(AuthenticatedUser user, YearMonth period) {
    Boolean finalized =
        jdbc.queryForObject(
            """
        select exists(select 1 from hr_payroll_runs where organization_id=? and period=?
          and status in ('APPROVED','PAID'))
        """,
            Boolean.class,
            user.organizationId(),
            period.atDay(1));
    if (Boolean.TRUE.equals(finalized))
      conflict("Reopen the approved payroll period before changing its source records.");
  }

  private void requireEmployee(AuthenticatedUser user, UUID id) {
    Boolean found =
        jdbc.queryForObject(
            """
        select exists(select 1 from employees e join branches b on b.id=e.primary_branch_id
          where e.id=? and e.organization_id=? and b.branch_type in ('STORE','SHOP'))
        """,
            Boolean.class,
            id,
            user.organizationId());
    if (!Boolean.TRUE.equals(found)) notFound("Employee not found.");
  }

  private void requireBranch(AuthenticatedUser user, UUID id, boolean shopOnly) {
    Boolean found =
        jdbc.queryForObject(
            """
        select exists(select 1 from branches where id=? and organization_id=?
          and status='ACTIVE' and branch_type in (%s))
        """
                .formatted(shopOnly ? "'SHOP'" : "'STORE','SHOP'"),
            Boolean.class,
            id,
            user.organizationId());
    if (!Boolean.TRUE.equals(found))
      bad(shopOnly ? "Choose an active shop." : "Choose an active store or shop.");
  }

  private static List<Map<String, Object>> allocationInput(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?>)) bad("Invalid allocation data.");
      Map<?, ?> source = (Map<?, ?>) item;
      Map<String, Object> allocation = new LinkedHashMap<>();
      allocation.put("employeeId", source.get("employeeId"));
      allocation.put("locationId", source.get("locationId"));
      allocation.put("amount", source.get("amount"));
      allocation.put("percentage", source.get("percentage"));
      result.add(allocation);
    }
    return result;
  }

  private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
    return rows.stream()
        .map(row -> decimal(row.get(key)))
        .reduce(ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal amount(Object value, String name) {
    BigDecimal amount = decimal(value);
    if (amount.signum() < 0 || amount.scale() > 2)
      bad(name + " must be a non-negative amount with at most two decimals.");
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return ZERO;
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException exception) {
      bad("Invalid numeric value.");
      return ZERO;
    }
  }

  private static UUID uuid(Object value) {
    if (value instanceof UUID id) return id;
    try {
      return UUID.fromString(String.valueOf(value));
    } catch (RuntimeException exception) {
      bad("A valid identifier is required.");
      return null;
    }
  }

  private static LocalDate date(Object value, String name) {
    try {
      return value instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(value));
    } catch (RuntimeException exception) {
      bad("A valid " + name + " date is required.");
      return null;
    }
  }

  private static String text(Object value, String name) {
    if (value == null || value.toString().isBlank()) bad(name + " is required.");
    return value.toString().trim();
  }

  private YearMonth parseMonth(String value) {
    try {
      YearMonth month = YearMonth.parse(value);
      if (month.getYear() < 2000 || month.getYear() > 2100)
        bad("Choose a month between 2000 and 2100.");
      return month;
    } catch (RuntimeException exception) {
      if (exception instanceof ResponseStatusException) throw exception;
      bad("Month must use YYYY-MM format.");
      return null;
    }
  }

  private Map<String, Object> parseSnapshot(String json) {
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Saved payroll snapshot is invalid.", exception);
    }
  }

  private String token(Map<String, Object> data) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(mapper.valueToTree(data).toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to validate payroll preview.", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  private static void bad(String detail) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
  }

  private static void notFound(String detail) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
  }

  private static void conflict(String detail) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, detail);
  }
}
