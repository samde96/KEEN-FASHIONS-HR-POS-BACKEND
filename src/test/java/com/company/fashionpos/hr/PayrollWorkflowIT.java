package com.company.fashionpos.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false",
      "app.notifications.scheduler-enabled=false",
      "app.security.admin-email=payroll-test@example.test",
      "app.security.admin-password=payroll-test-only"
    })
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
@Transactional
class PayrollWorkflowIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PayrollService payroll;
  @Autowired WorkforceController workforce;

  private UUID organizationId;
  private UUID homeBranchId;
  private UUID secondBranchId;
  private UUID cashierId;
  private UUID employeeId;
  private AuthenticatedUser admin;

  @BeforeEach
  void createPayrollFixture() {
    organizationId = UUID.randomUUID();
    homeBranchId = UUID.randomUUID();
    secondBranchId = UUID.randomUUID();
    cashierId = UUID.randomUUID();
    employeeId = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Payroll test','KES','Africa/Nairobi','ACTIVE')",
        organizationId);
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Payroll admin','ACTIVE')",
        cashierId,
        organizationId,
        cashierId + "@test.local");
    for (UUID branchId : List.of(homeBranchId, secondBranchId)) {
      jdbc.update(
          "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,?,?,'Africa/Nairobi','ACTIVE')",
          branchId,
          organizationId,
          "Payroll shop",
          branchId.toString().substring(0, 12));
    }
    jdbc.update(
        "insert into user_branch_assignments(id,user_id,branch_id) values (?,?,?)",
        UUID.randomUUID(),
        cashierId,
        homeBranchId);
    jdbc.update(
        """
        insert into employees(id,organization_id,user_id,primary_branch_id,employee_number,
          first_name,last_name,employment_type,employment_status,joining_date,basic_salary)
        values (?,?,?,?,?,'Payroll','Employee','PERMANENT','ACTIVE','2025-01-01',50000)
        """,
        employeeId,
        organizationId,
        cashierId,
        homeBranchId,
        "PAY-" + employeeId.toString().substring(0, 8));
    admin =
        new AuthenticatedUser(
            cashierId,
            cashierId + "@test.local",
            "Payroll admin",
            organizationId,
            Set.of(homeBranchId, secondBranchId),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    jdbc.update(
        """
        insert into sales(id,organization_id,branch_id,sale_number,idempotency_key,
          subtotal_amount,total_amount,status,cashier_id,sold_at)
        values (?,?,?,?,?,150,150,'COMPLETED',?,?)
        """,
        UUID.randomUUID(),
        organizationId,
        homeBranchId,
        "PAY-SALE-" + UUID.randomUUID().toString().substring(0, 8),
        UUID.randomUUID().toString(),
        cashierId,
        Timestamp.from(
            LocalDate.of(2026, 3, 10)
                .atTime(12, 0)
                .atZone(ZoneId.of("Africa/Nairobi"))
                .toInstant()));
    jdbc.update(
        """
        insert into attendance_records(id,organization_id,employee_id,branch_id,attendance_date,
          clock_in,status,worked_minutes)
        values (?,?,?,?,?,'2026-03-10T08:00:00+03:00','PRESENT',480)
        """,
        UUID.randomUUID(),
        organizationId,
        employeeId,
        homeBranchId,
        LocalDate.of(2026, 3, 10));
  }

  @Test
  void calculatesAndApprovesPayrollWithOverridesFundingAndLosses() {
    assertThat(payroll.setup(admin).get("employees")).isNotNull();
    payroll.saveRule(
        admin,
        null,
        Map.of(
            "locationId",
            homeBranchId.toString(),
            "effectiveFrom",
            "2026-03-01",
            "dailyTarget",
            100,
            "bonusMode",
            "FIXED",
            "bonusValue",
            20,
            "enabled",
            true));
    Map<String, Object> overrideRequest =
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "employeeId",
            employeeId.toString(),
            "effectiveDate",
            "2026-03-10",
            "kind",
            "AUTO_BONUS_OVERRIDE",
            "amount",
            35,
            "reason",
            "Approved daily override",
            "active",
            true);
    Map<String, Object> override = payroll.saveAdjustment(admin, null, overrideRequest);
    assertThat(payroll.saveAdjustment(admin, null, overrideRequest).get("id"))
        .isEqualTo(override.get("id"));
    Map<String, Object> bonus =
        payroll.saveAdjustment(
            admin,
            null,
            Map.of(
                "requestKey",
                UUID.randomUUID().toString(),
                "employeeId",
                employeeId.toString(),
                "effectiveDate",
                "2026-03-10",
                "kind",
                "MANUAL_BONUS",
                "amount",
                40,
                "reason",
                "Recognition",
                "active",
                true));
    payroll.funding(
        admin,
        "2026-03",
        Map.of(
            "employeeId",
            employeeId.toString(),
            "monthlySalary",
            60000,
            "reason",
            "March salary",
            "shares",
            List.of(
                Map.of("locationId", homeBranchId.toString(), "percentage", 70),
                Map.of("locationId", secondBranchId.toString(), "percentage", 30))));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> plans =
        (List<Map<String, Object>>) payroll.funding(admin, "2026-03").get("plans");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> savedShares = (List<Map<String, Object>>) plans.get(0).get("shares");
    assertThat(savedShares).hasSize(2);
    assertThat(payroll.lossEligibility(admin, homeBranchId, LocalDate.of(2026, 3, 10)))
        .extracting(person -> person.get("employeeId"))
        .containsExactly(employeeId);
    payroll.saveLoss(
        admin,
        null,
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "locationId",
            homeBranchId.toString(),
            "occurredOn",
            "2026-03-10",
            "deductionDate",
            "2026-03-10",
            "amount",
            10,
            "recoverableAmount",
            10,
            "allocationMode",
            "CUSTOM",
            "reason",
            "Verified till shortage",
            "allocations",
            List.of(Map.of("employeeId", employeeId.toString(), "amount", 10))));

    Map<String, Object> workspace = payroll.workspace(admin, "2026-03");
    @SuppressWarnings("unchecked")
    Map<String, Object> preview = (Map<String, Object>) workspace.get("preview");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) preview.get("lines");
    Map<String, Object> line = lines.get(0);
    assertThat(new BigDecimal(line.get("automaticBonus").toString())).isEqualByComparingTo("35.00");
    assertThat(new BigDecimal(line.get("manualBonus").toString())).isEqualByComparingTo("40.00");
    assertThat(new BigDecimal(line.get("lossDeductions").toString())).isEqualByComparingTo("10.00");
    assertThat(new BigDecimal(line.get("baseSalary").toString())).isEqualByComparingTo("60000.00");
    assertThat(new BigDecimal(line.get("netPay").toString())).isEqualByComparingTo("60065.00");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> shares = (List<Map<String, Object>>) line.get("funding");
    assertThat(shares).hasSize(2);
    assertThat(shares)
        .extracting(
            share ->
                new BigDecimal(share.get("amount").toString()).stripTrailingZeros().toPlainString())
        .containsExactlyInAnyOrder("42000", "18000");

    Map<String, Object> approved =
        payroll.payrollAction(
            admin, "2026-03", "approve", Map.of("previewToken", preview.get("token")));
    assertThat(approved.get("status")).isEqualTo("APPROVED");
    assertThatThrownBy(
            () ->
                payroll.saveAdjustment(
                    admin,
                    null,
                    Map.of(
                        "requestKey",
                        UUID.randomUUID().toString(),
                        "employeeId",
                        employeeId.toString(),
                        "effectiveDate",
                        "2026-03-20",
                        "kind",
                        "DEDUCTION",
                        "amount",
                        1,
                        "reason",
                        "Locked period",
                        "active",
                        true)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
        .isEqualTo(409);

    Map<String, Object> paid =
        payroll.payrollAction(
            admin, "2026-03", "paid", Map.of("paymentReference", "PAY-2026-03-001"));
    assertThat(paid.get("status")).isEqualTo("PAID");
    Map<String, Object> reports =
        payroll.reports(admin, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    assertThat(reports.get("paidTotal").toString()).isEqualTo("60065.00");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> statements = (List<Map<String, Object>>) reports.get("statements");
    assertThat(statements).hasSize(1);
    assertThat(new BigDecimal(statements.get(0).get("netPay").toString()))
        .isEqualByComparingTo("60065");
    Map<String, Object> reopened =
        payroll.payrollAction(
            admin, "2026-03", "reopen", Map.of("reason", "Correcting a payroll record"));
    assertThat(reopened.get("status")).isEqualTo("REOPENED");
    payroll.saveAdjustment(
        admin,
        null,
        Map.of(
            "requestKey",
            UUID.randomUUID().toString(),
            "employeeId",
            employeeId.toString(),
            "effectiveDate",
            "2026-03-20",
            "kind",
            "DEDUCTION",
            "amount",
            1,
            "reason",
            "Correction after reopening",
            "active",
            true));
    assertThat(override.get("kind")).isEqualTo("AUTO_BONUS_OVERRIDE");
    assertThat(bonus.get("kind")).isEqualTo("MANUAL_BONUS");
  }

  @Test
  void managesStaffShiftsAndWeeklyLocationHours() {
    Principal principal = () -> cashierId + "@test.local";
    OffsetDateTime startsAt = OffsetDateTime.parse("2026-03-11T09:00:00+03:00");
    OffsetDateTime endsAt = OffsetDateTime.parse("2026-03-11T17:00:00+03:00");
    Map<String, Object> created =
        workforce.createShift(
            new WorkforceController.WorkforceRequest(
                cashierId, homeBranchId, startsAt, endsAt, 30, "Opening shift"),
            principal);
    UUID shiftId = (UUID) created.get("id");
    assertThat(created.get("status")).isEqualTo("APPROVED");
    assertThat(created.get("employeeId")).isEqualTo(employeeId);
    assertThat(
            workforce
                .workforce(
                    LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 11), homeBranchId, principal)
                .stream()
                .map(shift -> shift.get("id")))
        .contains(shiftId);

    assertThatThrownBy(
            () ->
                workforce.createShift(
                    new WorkforceController.WorkforceRequest(
                        cashierId,
                        homeBranchId,
                        startsAt.plusHours(1),
                        endsAt.plusHours(1),
                        30,
                        "Overlapping shift"),
                    principal))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
        .isEqualTo(409);

    workforce.updateShift(
        shiftId,
        new WorkforceController.WorkforceRequest(
            cashierId,
            homeBranchId,
            startsAt.plusHours(1),
            endsAt.plusHours(1),
            45,
            "Updated shift"),
        principal);
    assertThat(workforce.cancelShift(shiftId, principal).get("status")).isEqualTo("CANCELLED");

    List<WorkforceController.OperatingHoursDay> schedule =
        java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(
                day ->
                    new WorkforceController.OperatingHoursDay(
                        day,
                        day > 5,
                        day > 5 ? null : LocalTime.of(9, 0),
                        day > 5 ? null : LocalTime.of(18, 0)))
            .toList();
    assertThat(
            workforce.saveOperatingHours(
                new WorkforceController.OperatingHoursRequest(homeBranchId, schedule), principal))
        .hasSize(7)
        .satisfies(
            days -> {
              assertThat(days.get(0).get("closed")).isEqualTo(false);
              assertThat(days.get(0).get("opensAt").toString()).startsWith("09:00");
              assertThat(days.get(6).get("closed")).isEqualTo(true);
            });
  }
}
