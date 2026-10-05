package com.company.fashionpos.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccountingDtos {

  private AccountingDtos() {}

  public record AccountRequest(
      UUID parentAccountId,
      @NotBlank String accountCode,
      @NotBlank String accountName,
      @NotBlank String accountType,
      Boolean active) {}

  public record AccountResponse(
      UUID id,
      UUID parentAccountId,
      String parentAccountCode,
      String accountCode,
      String accountName,
      String accountType,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {}

  public record FinancialPeriodRequest(
      @NotBlank String name,
      @NotNull LocalDate periodStart,
      @NotNull LocalDate periodEnd,
      @NotBlank String status) {}

  public record FinancialPeriodResponse(
      UUID id,
      String name,
      LocalDate periodStart,
      LocalDate periodEnd,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  public record TaxRuleRequest(
      @NotBlank String taxCode,
      @NotBlank String name,
      @NotBlank String taxCategory,
      @NotNull @DecimalMin("0.00") BigDecimal rate,
      Boolean inclusive,
      @NotNull LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Boolean active) {}

  public record TaxRuleResponse(
      UUID id,
      String taxCode,
      String name,
      String taxCategory,
      BigDecimal rate,
      boolean inclusive,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {}

  public record JournalLineRequest(
      @NotNull UUID accountId,
      @DecimalMin("0.00") BigDecimal debitAmount,
      @DecimalMin("0.00") BigDecimal creditAmount,
      String description) {}

  public record JournalEntryRequest(
      UUID branchId,
      UUID financialPeriodId,
      String journalNumber,
      @NotNull LocalDate entryDate,
      @NotBlank String description,
      String reference,
      String sourceModule,
      UUID sourceTransactionId,
      String status,
      @NotEmpty @Valid List<JournalLineRequest> lines) {}

  public record JournalLineResponse(
      UUID id,
      UUID accountId,
      String accountCode,
      String accountName,
      String accountType,
      BigDecimal debitAmount,
      BigDecimal creditAmount,
      String description) {}

  public record JournalEntryResponse(
      UUID id,
      UUID branchId,
      String branchName,
      UUID financialPeriodId,
      String financialPeriodName,
      UUID createdByUserId,
      String createdByName,
      String journalNumber,
      LocalDate entryDate,
      String description,
      String reference,
      String sourceModule,
      UUID sourceTransactionId,
      String status,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      Instant createdAt,
      Instant updatedAt,
      List<JournalLineResponse> lines) {}

  public record LedgerEntryResponse(
      UUID journalEntryId,
      UUID lineId,
      String journalNumber,
      LocalDate entryDate,
      String journalDescription,
      String reference,
      UUID accountId,
      String accountCode,
      String accountName,
      String accountType,
      BigDecimal debitAmount,
      BigDecimal creditAmount,
      BigDecimal runningBalance,
      String branchName) {}

  public record TrialBalanceRow(
      UUID accountId,
      String accountCode,
      String accountName,
      String accountType,
      BigDecimal debitAmount,
      BigDecimal creditAmount,
      BigDecimal netDebit,
      BigDecimal netCredit) {}

  public record TrialBalanceResponse(
      LocalDate fromDate,
      LocalDate toDate,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      BigDecimal difference,
      List<TrialBalanceRow> rows) {}

  public record FinancialStatementRow(
      UUID accountId, String accountCode, String accountName, String accountType, BigDecimal amount) {}

  public record ProfitAndLossResponse(
      LocalDate fromDate,
      LocalDate toDate,
      BigDecimal revenue,
      BigDecimal costOfGoodsSold,
      BigDecimal grossProfit,
      BigDecimal expenses,
      BigDecimal netIncome,
      List<FinancialStatementRow> revenueRows,
      List<FinancialStatementRow> costOfGoodsSoldRows,
      List<FinancialStatementRow> expenseRows) {}

  public record BalanceSheetResponse(
      LocalDate asOfDate,
      BigDecimal assets,
      BigDecimal liabilities,
      BigDecimal equity,
      BigDecimal retainedEarnings,
      BigDecimal totalLiabilitiesAndEquity,
      BigDecimal difference,
      List<FinancialStatementRow> assetRows,
      List<FinancialStatementRow> liabilityRows,
      List<FinancialStatementRow> equityRows) {}

  public record CashFlowRow(
      UUID accountId,
      String accountCode,
      String accountName,
      BigDecimal openingBalance,
      BigDecimal cashIn,
      BigDecimal cashOut,
      BigDecimal closingBalance) {}

  public record CashFlowResponse(
      LocalDate fromDate,
      LocalDate toDate,
      BigDecimal openingCash,
      BigDecimal cashIn,
      BigDecimal cashOut,
      BigDecimal netCashFlow,
      BigDecimal closingCash,
      List<CashFlowRow> rows) {}
}
