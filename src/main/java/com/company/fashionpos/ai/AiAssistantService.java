package com.company.fashionpos.ai;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.expenses.Expense;
import com.company.fashionpos.expenses.ExpenseRepository;
import com.company.fashionpos.hr.AttendanceRecord;
import com.company.fashionpos.hr.AttendanceRepository;
import com.company.fashionpos.hr.Employee;
import com.company.fashionpos.hr.EmployeeRepository;
import com.company.fashionpos.hr.LeaveRequest;
import com.company.fashionpos.hr.LeaveRequestRepository;
import com.company.fashionpos.hr.LeaveRequestStatus;
import com.company.fashionpos.inventory.InventoryItem;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.sales.Sale;
import com.company.fashionpos.sales.SaleRepository;
import com.company.fashionpos.shared.audit.AuditEventService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantService {

  private final SaleRepository saleRepository;
  private final InventoryRepository inventoryRepository;
  private final ExpenseRepository expenseRepository;
  private final EmployeeRepository employeeRepository;
  private final AttendanceRepository attendanceRepository;
  private final LeaveRequestRepository leaveRequestRepository;
  private final BranchRepository branchRepository;
  private final AuditEventService auditEventService;

  public AiAssistantService(
      SaleRepository saleRepository,
      InventoryRepository inventoryRepository,
      ExpenseRepository expenseRepository,
      EmployeeRepository employeeRepository,
      AttendanceRepository attendanceRepository,
      LeaveRequestRepository leaveRequestRepository,
      BranchRepository branchRepository,
      AuditEventService auditEventService) {
    this.saleRepository = saleRepository;
    this.inventoryRepository = inventoryRepository;
    this.expenseRepository = expenseRepository;
    this.employeeRepository = employeeRepository;
    this.attendanceRepository = attendanceRepository;
    this.leaveRequestRepository = leaveRequestRepository;
    this.branchRepository = branchRepository;
    this.auditEventService = auditEventService;
  }

  @Transactional
  public AiAssistantResponse answer(AuthenticatedUser user, AiAssistantMessageRequest request) {
    String prompt = request.message().trim();
    if (prompt.isEmpty()) {
      throw new IllegalArgumentException("Message is required");
    }

    AiAssistantResponse response = buildResponse(user, prompt);
    auditEventService.record(
        user,
        null,
        "ai.assistant.asked",
        "AiAssistant",
        user.userId(),
        java.util.Map.of("message", prompt, "answer", response.answer()));
    return response;
  }

  private AiAssistantResponse buildResponse(AuthenticatedUser user, String prompt) {
    String normalized = prompt.toLowerCase(Locale.ROOT);
    if (mentionsAny(normalized, "sale", "sales", "revenue")) {
      return salesAnswer(user);
    }
    if (mentionsAny(normalized, "stock", "inventory", "low stock")) {
      return inventoryAnswer(user);
    }
    if (mentionsAny(normalized, "expense", "expenses", "spending", "cost")) {
      return expensesAnswer(user);
    }
    if (mentionsAny(normalized, "attendance", "present", "absent", "late")) {
      return attendanceAnswer(user);
    }
    if (mentionsAny(normalized, "leave", "leave request", "time off")) {
      return leaveAnswer(user);
    }
    if (mentionsAny(normalized, "employee", "employees", "staff", "headcount")) {
      return employeeAnswer(user);
    }
    return overviewAnswer(user);
  }

  private AiAssistantResponse overviewAnswer(AuthenticatedUser user) {
    List<Branch> branches = scopedBranches(user);
    List<Sale> sales = scopedSales(user);
    List<Expense> expenses = scopedExpenses(user);
    List<Employee> employees = scopedEmployees(user);
    BigDecimal revenue =
        sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cost =
        expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new AiAssistantResponse(
        "I can help with read-only summaries for sales, inventory, expenses, employees, attendance, and leave. "
            + "Right now you have access to "
            + branches.size()
            + " active branches, "
            + employees.size()
            + " employees, "
            + sales.size()
            + " sales totaling "
            + money(revenue)
            + ", and expenses totaling "
            + money(cost)
            + ".",
        List.of(
            "Live ERP data: branches in your access scope",
            "Live ERP data: employee records in your access scope",
            "Live ERP data: sales in your access scope",
            "Live ERP data: expenses in your access scope"),
        List.of(
            "Show today's sales summary",
            "Show low stock items",
            "Show attendance summary",
            "Show pending leave requests"));
  }

  private AiAssistantResponse salesAnswer(AuthenticatedUser user) {
    List<Sale> sales = scopedSales(user);
    Instant since = Instant.now().minusSeconds(60L * 60L * 24L);
    List<Sale> todaySales =
        sales.stream().filter((sale) -> sale.getSoldAt().isAfter(since)).toList();
    BigDecimal total =
        todaySales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new AiAssistantResponse(
        "Sales in your scope for the last 24 hours: "
            + todaySales.size()
            + " transactions totaling "
            + money(total)
            + ".",
        List.of("Live ERP data: sales filtered to your authorized branches"),
        List.of("Show expenses summary", "Show low stock items", "Show branch performance"));
  }

  private AiAssistantResponse inventoryAnswer(AuthenticatedUser user) {
    List<InventoryItem> items = scopedInventory(user);
    List<InventoryItem> lowStock =
        items.stream()
            .filter((item) -> item.availableQuantity() <= item.getReorderLevel())
            .sorted(Comparator.comparingInt(InventoryItem::availableQuantity))
            .limit(5)
            .toList();

    String summary =
        lowStock.isEmpty()
            ? "No low stock items were found in your accessible branches."
            : "Low stock items: "
                + lowStock.stream()
                    .map(
                        item ->
                            item.getProduct().getName()
                                + " at "
                                + item.getBranch().getName()
                                + " ("
                                + item.availableQuantity()
                                + " available)")
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("No low stock items.");

    return new AiAssistantResponse(
        summary,
        List.of("Live ERP data: inventory filtered to your authorized branches"),
        List.of("Show today's sales summary", "Show expenses summary", "Show employee headcount"));
  }

  private AiAssistantResponse expensesAnswer(AuthenticatedUser user) {
    List<Expense> expenses = scopedExpenses(user);
    Instant since = Instant.now().minusSeconds(60L * 60L * 24L * 30L);
    List<Expense> recent =
        expenses.stream().filter((expense) -> expense.getIncurredAt().isAfter(since)).toList();
    BigDecimal total =
        recent.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new AiAssistantResponse(
        "Expenses in your scope for the last 30 days: "
            + recent.size()
            + " records totaling "
            + money(total)
            + ".",
        List.of("Live ERP data: expenses filtered to your authorized branches"),
        List.of(
            "Show today's sales summary", "Show low stock items", "Show pending leave requests"));
  }

  private AiAssistantResponse employeeAnswer(AuthenticatedUser user) {
    List<Employee> employees = scopedEmployees(user);
    long active = employees.stream().filter(Employee::isActive).count();
    return new AiAssistantResponse(
        "You currently have "
            + employees.size()
            + " employees in scope, with "
            + active
            + " marked active.",
        List.of("Live ERP data: employees filtered to your authorized branches"),
        List.of("Show attendance summary", "Show pending leave requests", "Show payroll overview"));
  }

  private AiAssistantResponse attendanceAnswer(AuthenticatedUser user) {
    List<AttendanceRecord> attendance = scopedAttendance(user);
    LocalDate today = LocalDate.now();
    List<AttendanceRecord> todaysRecords =
        attendance.stream().filter((record) -> today.equals(record.getAttendanceDate())).toList();
    long present =
        todaysRecords.stream()
            .filter((record) -> record.getStatus().name().equals("PRESENT"))
            .count();
    long late =
        todaysRecords.stream().filter((record) -> record.getStatus().name().equals("LATE")).count();
    long absent =
        todaysRecords.stream()
            .filter((record) -> record.getStatus().name().equals("ABSENT"))
            .count();

    return new AiAssistantResponse(
        "Attendance for today in your scope: "
            + present
            + " present, "
            + late
            + " late, and "
            + absent
            + " absent across "
            + todaysRecords.size()
            + " attendance records.",
        List.of("Live ERP data: attendance filtered to your authorized branches"),
        List.of(
            "Show employees summary", "Show pending leave requests", "Show today's sales summary"));
  }

  private AiAssistantResponse leaveAnswer(AuthenticatedUser user) {
    List<LeaveRequest> leaveRequests = scopedLeaveRequests(user);
    List<LeaveRequest> pending =
        leaveRequests.stream()
            .filter((request) -> request.getStatus() == LeaveRequestStatus.PENDING_APPROVAL)
            .limit(5)
            .toList();

    String detail =
        pending.isEmpty()
            ? "There are no pending leave requests in your accessible branches."
            : "Pending leave requests: "
                + pending.stream()
                    .map(
                        request ->
                            request.getEmployee().getFirstName()
                                + " "
                                + request.getEmployee().getLastName()
                                + " ("
                                + request.getLeaveType().getName()
                                + " "
                                + request.getStartDate()
                                + " to "
                                + request.getEndDate()
                                + ")")
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("");

    return new AiAssistantResponse(
        detail,
        List.of("Live ERP data: leave requests filtered to your authorized branches"),
        List.of("Show attendance summary", "Show employees summary", "Show today's sales summary"));
  }

  private List<Branch> scopedBranches(AuthenticatedUser user) {
    return branchRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((branch) -> branch.getStatus().name().equals("ACTIVE"))
        .filter((branch) -> canAccessBranch(user, branch.getId()))
        .toList();
  }

  private List<Sale> scopedSales(AuthenticatedUser user) {
    return saleRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((sale) -> canAccessBranch(user, sale.getBranch().getId()))
        .toList();
  }

  private List<Expense> scopedExpenses(AuthenticatedUser user) {
    return expenseRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((expense) -> canAccessBranch(user, expense.getBranch().getId()))
        .toList();
  }

  private List<Employee> scopedEmployees(AuthenticatedUser user) {
    return employeeRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((employee) -> canAccessBranch(user, employee.getPrimaryBranch().getId()))
        .toList();
  }

  private List<AttendanceRecord> scopedAttendance(AuthenticatedUser user) {
    return attendanceRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((record) -> canAccessBranch(user, record.getBranch().getId()))
        .toList();
  }

  private List<LeaveRequest> scopedLeaveRequests(AuthenticatedUser user) {
    return leaveRequestRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((request) -> canAccessBranch(user, request.getBranch().getId()))
        .toList();
  }

  private List<InventoryItem> scopedInventory(AuthenticatedUser user) {
    return inventoryRepository.findWithinOrganization(user.organizationId()).stream()
        .filter((item) -> canAccessBranch(user, item.getBranch().getId()))
        .toList();
  }

  private static boolean mentionsAny(String prompt, String... keywords) {
    for (String keyword : keywords) {
      if (prompt.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static boolean canAccessBranch(AuthenticatedUser user, UUID branchId) {
    return user.branchIds().isEmpty() || user.branchIds().contains(branchId);
  }

  private static String money(BigDecimal amount) {
    return amount == null ? "0.00" : amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
  }
}
