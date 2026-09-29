package com.company.fashionpos.expenses;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

  private static final DateTimeFormatter EXPENSE_NUMBER_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final ExpenseRepository expenseRepository;
  private final BranchRepository branchRepository;
  private final BranchService branchService;

  public ExpenseService(
      ExpenseRepository expenseRepository,
      BranchRepository branchRepository,
      BranchService branchService) {
    this.expenseRepository = expenseRepository;
    this.branchRepository = branchRepository;
    this.branchService = branchService;
  }

  @Transactional(readOnly = true)
  public List<ExpenseResponse> listExpenses(AuthenticatedUser user) {
    return expenseRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(expense -> canAccessBranch(user, expense.getBranch().getId()))
        .map(ExpenseResponse::from)
        .toList();
  }

  @Transactional
  public ExpenseResponse createExpense(AuthenticatedUser user, ExpenseRequest request) {
    branchService.requireBranchAccess(user, request.branchId());
    Branch branch = requireBranch(user, request.branchId());
    ExpenseStatus status = statusOrPending(request.status());
    if (status == ExpenseStatus.VOID) {
      throw new IllegalArgumentException("New expenses cannot be created as void");
    }

    Expense expense =
        new Expense(
            UUID.randomUUID(),
            branch.getOrganization(),
            branch,
            nextExpenseNumber(),
            normalizeRequired(request.category(), "Category"),
            normalizeRequired(request.description(), "Description"),
            normalizeOptional(request.vendorName()),
            money(request.amount()),
            request.paymentMethod(),
            normalizeOptional(request.paymentReference()),
            status,
            normalizeOptional(request.notes()));
    return ExpenseResponse.from(expenseRepository.save(expense));
  }

  @Transactional
  public ExpenseResponse markPaid(AuthenticatedUser user, UUID expenseId) {
    Expense expense = requireExpense(user, expenseId);
    expense.markPaid();
    return ExpenseResponse.from(expense);
  }

  @Transactional
  public ExpenseResponse voidExpense(AuthenticatedUser user, UUID expenseId) {
    Expense expense = requireExpense(user, expenseId);
    expense.voidExpense();
    return ExpenseResponse.from(expense);
  }

  private Expense requireExpense(AuthenticatedUser user, UUID expenseId) {
    Expense expense =
        expenseRepository
            .findWithinOrganization(expenseId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Expense not found"));
    if (!canAccessBranch(user, expense.getBranch().getId())) {
      throw new EntityNotFoundException("Expense not found");
    }
    return expense;
  }

  private Branch requireBranch(AuthenticatedUser user, UUID branchId) {
    return branchRepository
        .findWithinOrganization(branchId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
  }

  private static BigDecimal money(BigDecimal value) {
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("Amount must be greater than zero");
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static String normalizeRequired(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static ExpenseStatus statusOrPending(ExpenseStatus status) {
    return status == null ? ExpenseStatus.PENDING : status;
  }

  private static String nextExpenseNumber() {
    String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    return "EXP-" + EXPENSE_NUMBER_TIMESTAMP.format(Instant.now()) + "-" + suffix;
  }

  private static boolean canAccessBranch(AuthenticatedUser user, UUID branchId) {
    return user.branchIds().isEmpty() || user.branchIds().contains(branchId);
  }
}
