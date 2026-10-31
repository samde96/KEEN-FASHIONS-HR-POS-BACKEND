package com.company.fashionpos.ai;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.expenses.Expense;
import com.company.fashionpos.expenses.ExpenseRepository;
import com.company.fashionpos.inventory.InventoryItem;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.sales.Sale;
import com.company.fashionpos.sales.SaleRepository;
import com.company.fashionpos.shared.audit.AuditEventService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
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
  private final BranchRepository branchRepository;
  private final AuditEventService auditEventService;

  public AiAssistantService(
      SaleRepository saleRepository,
      InventoryRepository inventoryRepository,
      ExpenseRepository expenseRepository,
      BranchRepository branchRepository,
      AuditEventService auditEventService) {
    this.saleRepository = saleRepository;
    this.inventoryRepository = inventoryRepository;
    this.expenseRepository = expenseRepository;
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
    return overviewAnswer(user);
  }

  private AiAssistantResponse overviewAnswer(AuthenticatedUser user) {
    List<Branch> branches = scopedBranches(user);
    List<Sale> sales = scopedSales(user);
    List<Expense> expenses = scopedExpenses(user);
    BigDecimal revenue =
        sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cost =
        expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new AiAssistantResponse(
        "I can help with read-only summaries for sales, inventory, and expenses. "
            + "Right now you have access to "
            + branches.size()
            + " active branches, "
            + sales.size()
            + " sales totaling "
            + money(revenue)
            + ", and expenses totaling "
            + money(cost)
            + ".",
        List.of(
            "Live ERP data: branches in your access scope",
            "Live ERP data: sales in your access scope",
            "Live ERP data: expenses in your access scope"),
        List.of("Show today's sales summary", "Show low stock items", "Show expenses summary"));
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
        List.of("Show today's sales summary", "Show expenses summary"));
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
        List.of("Show today's sales summary", "Show low stock items"));
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
