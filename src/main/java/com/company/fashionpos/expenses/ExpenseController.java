package com.company.fashionpos.expenses;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

  private final ExpenseService expenseService;
  private final AuthenticatedUserService authenticatedUserService;

  public ExpenseController(
      ExpenseService expenseService, AuthenticatedUserService authenticatedUserService) {
    this.expenseService = expenseService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public List<ExpenseResponse> listExpenses(Principal principal) {
    return expenseService.listExpenses(authenticatedUserService.current(principal));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseResponse createExpense(
      @Valid @RequestBody ExpenseRequest request, Principal principal) {
    return expenseService.createExpense(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/{expenseId}/paid")
  public ExpenseResponse markPaid(@PathVariable UUID expenseId, Principal principal) {
    return expenseService.markPaid(authenticatedUserService.current(principal), expenseId);
  }

  @DeleteMapping("/{expenseId}")
  public ExpenseResponse voidExpense(@PathVariable UUID expenseId, Principal principal) {
    return expenseService.voidExpense(authenticatedUserService.current(principal), expenseId);
  }
}
