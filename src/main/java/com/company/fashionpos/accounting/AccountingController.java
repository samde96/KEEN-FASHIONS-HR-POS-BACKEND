package com.company.fashionpos.accounting;

import com.company.fashionpos.accounting.AccountingDtos.AccountRequest;
import com.company.fashionpos.accounting.AccountingDtos.AccountResponse;
import com.company.fashionpos.accounting.AccountingDtos.BalanceSheetResponse;
import com.company.fashionpos.accounting.AccountingDtos.CashFlowResponse;
import com.company.fashionpos.accounting.AccountingDtos.FinancialPeriodRequest;
import com.company.fashionpos.accounting.AccountingDtos.FinancialPeriodResponse;
import com.company.fashionpos.accounting.AccountingDtos.JournalEntryRequest;
import com.company.fashionpos.accounting.AccountingDtos.JournalEntryResponse;
import com.company.fashionpos.accounting.AccountingDtos.LedgerEntryResponse;
import com.company.fashionpos.accounting.AccountingDtos.ProfitAndLossResponse;
import com.company.fashionpos.accounting.AccountingDtos.TaxRuleRequest;
import com.company.fashionpos.accounting.AccountingDtos.TaxRuleResponse;
import com.company.fashionpos.accounting.AccountingDtos.TrialBalanceResponse;
import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting")
public class AccountingController {

  private final AccountingService accountingService;
  private final AuthenticatedUserService authenticatedUserService;

  public AccountingController(
      AccountingService accountingService, AuthenticatedUserService authenticatedUserService) {
    this.accountingService = accountingService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/accounts")
  public List<AccountResponse> listAccounts(Principal principal) {
    return accountingService.listAccounts(authenticatedUserService.current(principal));
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse createAccount(
      @Valid @RequestBody AccountRequest request, Principal principal) {
    return accountingService.createAccount(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/accounts/{accountId}")
  public AccountResponse updateAccount(
      @PathVariable UUID accountId,
      @Valid @RequestBody AccountRequest request,
      Principal principal) {
    return accountingService.updateAccount(
        authenticatedUserService.current(principal), accountId, request);
  }

  @DeleteMapping("/accounts/{accountId}")
  public AccountResponse deactivateAccount(@PathVariable UUID accountId, Principal principal) {
    return accountingService.deactivateAccount(authenticatedUserService.current(principal), accountId);
  }

  @GetMapping("/periods")
  public List<FinancialPeriodResponse> listPeriods(Principal principal) {
    return accountingService.listPeriods(authenticatedUserService.current(principal));
  }

  @PostMapping("/periods")
  @ResponseStatus(HttpStatus.CREATED)
  public FinancialPeriodResponse createPeriod(
      @Valid @RequestBody FinancialPeriodRequest request, Principal principal) {
    return accountingService.createPeriod(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/periods/{periodId}")
  public FinancialPeriodResponse updatePeriod(
      @PathVariable UUID periodId,
      @Valid @RequestBody FinancialPeriodRequest request,
      Principal principal) {
    return accountingService.updatePeriod(
        authenticatedUserService.current(principal), periodId, request);
  }

  @GetMapping("/tax-rules")
  public List<TaxRuleResponse> listTaxRules(Principal principal) {
    return accountingService.listTaxRules(authenticatedUserService.current(principal));
  }

  @PostMapping("/tax-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public TaxRuleResponse createTaxRule(
      @Valid @RequestBody TaxRuleRequest request, Principal principal) {
    return accountingService.createTaxRule(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/tax-rules/{taxRuleId}")
  public TaxRuleResponse updateTaxRule(
      @PathVariable UUID taxRuleId,
      @Valid @RequestBody TaxRuleRequest request,
      Principal principal) {
    return accountingService.updateTaxRule(
        authenticatedUserService.current(principal), taxRuleId, request);
  }

  @GetMapping("/journals")
  public List<JournalEntryResponse> listJournals(Principal principal) {
    return accountingService.listJournals(authenticatedUserService.current(principal));
  }

  @PostMapping("/journals")
  @ResponseStatus(HttpStatus.CREATED)
  public JournalEntryResponse createJournal(
      @Valid @RequestBody JournalEntryRequest request, Principal principal) {
    return accountingService.createJournal(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/journals/{journalId}")
  public JournalEntryResponse updateJournal(
      @PathVariable UUID journalId,
      @Valid @RequestBody JournalEntryRequest request,
      Principal principal) {
    return accountingService.updateJournal(
        authenticatedUserService.current(principal), journalId, request);
  }

  @PostMapping("/journals/{journalId}/post")
  public JournalEntryResponse postJournal(@PathVariable UUID journalId, Principal principal) {
    return accountingService.postJournal(authenticatedUserService.current(principal), journalId);
  }

  @DeleteMapping("/journals/{journalId}")
  public JournalEntryResponse voidJournal(@PathVariable UUID journalId, Principal principal) {
    return accountingService.voidJournal(authenticatedUserService.current(principal), journalId);
  }

  @GetMapping("/ledger")
  public List<LedgerEntryResponse> ledger(
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return accountingService.ledger(
        authenticatedUserService.current(principal), accountId, branchId, from, to);
  }

  @GetMapping("/reports/trial-balance")
  public TrialBalanceResponse trialBalance(
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return accountingService.trialBalance(
        authenticatedUserService.current(principal), branchId, from, to);
  }

  @GetMapping("/reports/profit-and-loss")
  public ProfitAndLossResponse profitAndLoss(
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return accountingService.profitAndLoss(
        authenticatedUserService.current(principal), branchId, from, to);
  }

  @GetMapping("/reports/balance-sheet")
  public BalanceSheetResponse balanceSheet(
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf,
      Principal principal) {
    return accountingService.balanceSheet(
        authenticatedUserService.current(principal), branchId, asOf);
  }

  @GetMapping("/reports/cash-flow")
  public CashFlowResponse cashFlow(
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return accountingService.cashFlow(
        authenticatedUserService.current(principal), branchId, from, to);
  }
}
