package com.company.fashionpos.accounting;

import com.company.fashionpos.accounting.AccountingDtos.AccountRequest;
import com.company.fashionpos.accounting.AccountingDtos.AccountResponse;
import com.company.fashionpos.accounting.AccountingDtos.BalanceSheetResponse;
import com.company.fashionpos.accounting.AccountingDtos.CashFlowResponse;
import com.company.fashionpos.accounting.AccountingDtos.CashFlowRow;
import com.company.fashionpos.accounting.AccountingDtos.FinancialPeriodRequest;
import com.company.fashionpos.accounting.AccountingDtos.FinancialPeriodResponse;
import com.company.fashionpos.accounting.AccountingDtos.FinancialStatementRow;
import com.company.fashionpos.accounting.AccountingDtos.JournalEntryRequest;
import com.company.fashionpos.accounting.AccountingDtos.JournalEntryResponse;
import com.company.fashionpos.accounting.AccountingDtos.JournalLineRequest;
import com.company.fashionpos.accounting.AccountingDtos.JournalLineResponse;
import com.company.fashionpos.accounting.AccountingDtos.LedgerEntryResponse;
import com.company.fashionpos.accounting.AccountingDtos.ProfitAndLossResponse;
import com.company.fashionpos.accounting.AccountingDtos.TaxRuleRequest;
import com.company.fashionpos.accounting.AccountingDtos.TaxRuleResponse;
import com.company.fashionpos.accounting.AccountingDtos.TrialBalanceResponse;
import com.company.fashionpos.accounting.AccountingDtos.TrialBalanceRow;
import com.company.fashionpos.shared.audit.AuditEventService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingService {

  private static final DateTimeFormatter JOURNAL_NUMBER_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
  private static final Set<String> ACCOUNT_TYPES =
      Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "COGS", "EXPENSE");
  private static final Set<String> PERIOD_STATUSES = Set.of("OPEN", "CLOSED", "LOCKED");
  private static final Set<String> REQUEST_JOURNAL_STATUSES = Set.of("DRAFT", "POSTED");

  private final JdbcTemplate jdbcTemplate;
  private final AuditEventService auditEventService;

  public AccountingService(JdbcTemplate jdbcTemplate, AuditEventService auditEventService) {
    this.jdbcTemplate = jdbcTemplate;
    this.auditEventService = auditEventService;
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listAccounts(AuthenticatedUser user) {
    return jdbcTemplate.query(
        """
        select
          account.id,
          account.parent_account_id,
          parent.account_code as parent_account_code,
          account.account_code,
          account.account_name,
          account.account_type,
          account.active,
          account.created_at,
          account.updated_at
        from accounts account
        left join accounts parent on parent.id = account.parent_account_id
        where account.organization_id = ?
        order by account.account_code
        """,
        this::mapAccount,
        user.organizationId());
  }

  @Transactional
  public AccountResponse createAccount(AuthenticatedUser user, AccountRequest request) {
    requireManage(user);
    UUID accountId = UUID.randomUUID();
    String accountType = normalizeEnum(request.accountType(), ACCOUNT_TYPES, "Account type");
    UUID parentAccountId = request.parentAccountId();
    if (parentAccountId != null) {
      requireAccount(user, parentAccountId);
    }

    try {
      jdbcTemplate.update(
          """
          insert into accounts (
            id,
            organization_id,
            parent_account_id,
            account_code,
            account_name,
            account_type,
            active,
            created_at,
            updated_at,
            version
          )
          values (?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
          """,
          accountId,
          user.organizationId(),
          parentAccountId,
          normalizeCode(request.accountCode()),
          normalizeRequired(request.accountName(), "Account name"),
          accountType,
          request.active() == null || request.active());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Account code is already used");
    }

    auditEventService.record(
        user,
        null,
        "accounting.account.created",
        "Account",
        accountId,
        Map.of("accountCode", normalizeCode(request.accountCode()), "accountType", accountType));
    return requireAccount(user, accountId);
  }

  @Transactional
  public AccountResponse updateAccount(
      AuthenticatedUser user, UUID accountId, AccountRequest request) {
    requireManage(user);
    requireAccount(user, accountId);
    String accountType = normalizeEnum(request.accountType(), ACCOUNT_TYPES, "Account type");
    UUID parentAccountId = request.parentAccountId();
    if (parentAccountId != null) {
      requireAccount(user, parentAccountId);
      if (parentAccountId.equals(accountId) || createsAccountCycle(accountId, parentAccountId)) {
        throw new IllegalArgumentException("Parent account cannot create a chart cycle");
      }
    }

    try {
      jdbcTemplate.update(
          """
          update accounts
          set
            parent_account_id = ?,
            account_code = ?,
            account_name = ?,
            account_type = ?,
            active = ?,
            updated_at = now(),
            version = version + 1
          where id = ? and organization_id = ?
          """,
          parentAccountId,
          normalizeCode(request.accountCode()),
          normalizeRequired(request.accountName(), "Account name"),
          accountType,
          request.active() == null || request.active(),
          accountId,
          user.organizationId());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Account code is already used");
    }

    auditEventService.record(
        user,
        null,
        "accounting.account.updated",
        "Account",
        accountId,
        Map.of("accountCode", normalizeCode(request.accountCode()), "accountType", accountType));
    return requireAccount(user, accountId);
  }

  @Transactional
  public AccountResponse deactivateAccount(AuthenticatedUser user, UUID accountId) {
    requireManage(user);
    AccountResponse account = requireAccount(user, accountId);
    jdbcTemplate.update(
        """
        update accounts
        set active = false, updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        accountId,
        user.organizationId());
    auditEventService.record(
        user,
        null,
        "accounting.account.deactivated",
        "Account",
        accountId,
        Map.of("accountCode", account.accountCode()));
    return requireAccount(user, accountId);
  }

  @Transactional(readOnly = true)
  public List<FinancialPeriodResponse> listPeriods(AuthenticatedUser user) {
    return jdbcTemplate.query(
        """
        select id, name, period_start, period_end, status, created_at, updated_at
        from financial_periods
        where organization_id = ?
        order by period_start desc
        """,
        this::mapPeriod,
        user.organizationId());
  }

  @Transactional
  public FinancialPeriodResponse createPeriod(
      AuthenticatedUser user, FinancialPeriodRequest request) {
    requireManage(user);
    validatePeriodDates(request.periodStart(), request.periodEnd());
    UUID periodId = UUID.randomUUID();
    String status = normalizeEnum(request.status(), PERIOD_STATUSES, "Period status");
    try {
      jdbcTemplate.update(
          """
          insert into financial_periods (
            id,
            organization_id,
            name,
            period_start,
            period_end,
            status,
            created_at,
            updated_at,
            version
          )
          values (?, ?, ?, ?, ?, ?, now(), now(), 0)
          """,
          periodId,
          user.organizationId(),
          normalizeRequired(request.name(), "Period name"),
          request.periodStart(),
          request.periodEnd(),
          status);
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Financial period name is already used");
    }
    auditEventService.record(
        user,
        null,
        "accounting.period.created",
        "FinancialPeriod",
        periodId,
        Map.of("name", normalizeRequired(request.name(), "Period name"), "status", status));
    return requirePeriod(user, periodId);
  }

  @Transactional
  public FinancialPeriodResponse updatePeriod(
      AuthenticatedUser user, UUID periodId, FinancialPeriodRequest request) {
    requireManage(user);
    requirePeriod(user, periodId);
    validatePeriodDates(request.periodStart(), request.periodEnd());
    String status = normalizeEnum(request.status(), PERIOD_STATUSES, "Period status");
    try {
      jdbcTemplate.update(
          """
          update financial_periods
          set
            name = ?,
            period_start = ?,
            period_end = ?,
            status = ?,
            updated_at = now(),
            version = version + 1
          where id = ? and organization_id = ?
          """,
          normalizeRequired(request.name(), "Period name"),
          request.periodStart(),
          request.periodEnd(),
          status,
          periodId,
          user.organizationId());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Financial period name is already used");
    }
    auditEventService.record(
        user,
        null,
        "accounting.period.updated",
        "FinancialPeriod",
        periodId,
        Map.of("name", normalizeRequired(request.name(), "Period name"), "status", status));
    return requirePeriod(user, periodId);
  }

  @Transactional(readOnly = true)
  public List<TaxRuleResponse> listTaxRules(AuthenticatedUser user) {
    return jdbcTemplate.query(
        """
        select
          id,
          tax_code,
          name,
          tax_category,
          rate,
          inclusive,
          effective_from,
          effective_to,
          active,
          created_at,
          updated_at
        from tax_rules
        where organization_id = ?
        order by effective_from desc, tax_code
        """,
        this::mapTaxRule,
        user.organizationId());
  }

  @Transactional
  public TaxRuleResponse createTaxRule(AuthenticatedUser user, TaxRuleRequest request) {
    requireManage(user);
    validateTaxDates(request.effectiveFrom(), request.effectiveTo());
    UUID taxRuleId = UUID.randomUUID();
    try {
      jdbcTemplate.update(
          """
          insert into tax_rules (
            id,
            organization_id,
            tax_code,
            name,
            tax_category,
            rate,
            inclusive,
            effective_from,
            effective_to,
            active,
            created_at,
            updated_at,
            version
          )
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
          """,
          taxRuleId,
          user.organizationId(),
          normalizeCode(request.taxCode()),
          normalizeRequired(request.name(), "Tax rule name"),
          normalizeCode(request.taxCategory()),
          percentage(request.rate()),
          request.inclusive() != null && request.inclusive(),
          request.effectiveFrom(),
          request.effectiveTo(),
          request.active() == null || request.active());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Tax rule code and effective date are already used");
    }
    auditEventService.record(
        user,
        null,
        "accounting.tax-rule.created",
        "TaxRule",
        taxRuleId,
        Map.of("taxCode", normalizeCode(request.taxCode())));
    return requireTaxRule(user, taxRuleId);
  }

  @Transactional
  public TaxRuleResponse updateTaxRule(
      AuthenticatedUser user, UUID taxRuleId, TaxRuleRequest request) {
    requireManage(user);
    requireTaxRule(user, taxRuleId);
    validateTaxDates(request.effectiveFrom(), request.effectiveTo());
    try {
      jdbcTemplate.update(
          """
          update tax_rules
          set
            tax_code = ?,
            name = ?,
            tax_category = ?,
            rate = ?,
            inclusive = ?,
            effective_from = ?,
            effective_to = ?,
            active = ?,
            updated_at = now(),
            version = version + 1
          where id = ? and organization_id = ?
          """,
          normalizeCode(request.taxCode()),
          normalizeRequired(request.name(), "Tax rule name"),
          normalizeCode(request.taxCategory()),
          percentage(request.rate()),
          request.inclusive() != null && request.inclusive(),
          request.effectiveFrom(),
          request.effectiveTo(),
          request.active() == null || request.active(),
          taxRuleId,
          user.organizationId());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Tax rule code and effective date are already used");
    }
    auditEventService.record(
        user,
        null,
        "accounting.tax-rule.updated",
        "TaxRule",
        taxRuleId,
        Map.of("taxCode", normalizeCode(request.taxCode())));
    return requireTaxRule(user, taxRuleId);
  }

  @Transactional(readOnly = true)
  public List<JournalEntryResponse> listJournals(AuthenticatedUser user) {
    BranchPredicate scope = branchPredicate(user, "entry", true);
    List<Object> args = new ArrayList<>();
    args.add(user.organizationId());
    args.addAll(scope.args());
    List<JournalHeader> headers =
        jdbcTemplate.query(
            """
            select
              entry.id,
              entry.branch_id,
              branch.name as branch_name,
              entry.financial_period_id,
              period.name as financial_period_name,
              entry.created_by_user_id,
              creator.display_name as created_by_name,
              entry.journal_number,
              entry.entry_date,
              entry.description,
              entry.reference,
              entry.source_module,
              entry.source_transaction_id,
              entry.status,
              entry.created_at,
              entry.updated_at
            from journal_entries entry
            left join branches branch on branch.id = entry.branch_id
            left join financial_periods period on period.id = entry.financial_period_id
            left join users creator on creator.id = entry.created_by_user_id
            where entry.organization_id = ?
            """
                + scope.sql()
                + """
                order by entry.entry_date desc, entry.created_at desc
                limit 100
                """,
            this::mapJournalHeader,
            args.toArray());
    return headers.stream().map(this::journalResponse).toList();
  }

  @Transactional
  public JournalEntryResponse createJournal(AuthenticatedUser user, JournalEntryRequest request) {
    requireManage(user);
    UUID journalId = UUID.randomUUID();
    UUID branchId = normalizeBranch(user, request.branchId());
    UUID periodId = normalizePeriodId(user, request.financialPeriodId(), request.entryDate());
    String status =
        normalizeEnum(
            request.status() == null || request.status().isBlank() ? "DRAFT" : request.status(),
            REQUEST_JOURNAL_STATUSES,
            "Journal status");
    JournalTotals totals = validateJournalLines(user, request.lines());
    if ("POSTED".equals(status)) {
      requireOpenPeriod(user, periodId);
    }
    String journalNumber =
        request.journalNumber() == null || request.journalNumber().isBlank()
            ? nextJournalNumber()
            : normalizeCode(request.journalNumber());
    try {
      jdbcTemplate.update(
          """
          insert into journal_entries (
            id,
            organization_id,
            branch_id,
            financial_period_id,
            created_by_user_id,
            journal_number,
            entry_date,
            description,
            reference,
            source_module,
            source_transaction_id,
            status,
            created_at,
            updated_at,
            version
          )
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
          """,
          journalId,
          user.organizationId(),
          branchId,
          periodId,
          user.userId(),
          journalNumber,
          request.entryDate(),
          normalizeRequired(request.description(), "Journal description"),
          normalizeOptional(request.reference()),
          normalizeOptional(request.sourceModule()),
          request.sourceTransactionId(),
          status);
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Journal number is already used");
    }
    saveJournalLines(user, journalId, request.lines());
    auditEventService.record(
        user,
        branchId,
        "POSTED".equals(status) ? "accounting.journal.posted" : "accounting.journal.created",
        "JournalEntry",
        journalId,
        Map.of(
            "journalNumber",
            journalNumber,
            "status",
            status,
            "totalDebit",
            totals.totalDebit().toPlainString()));
    return requireJournal(user, journalId);
  }

  @Transactional
  public JournalEntryResponse updateJournal(
      AuthenticatedUser user, UUID journalId, JournalEntryRequest request) {
    requireManage(user);
    JournalEntryResponse existing = requireJournal(user, journalId);
    if (!"DRAFT".equals(existing.status())) {
      throw new IllegalArgumentException("Only draft journals can be edited");
    }
    UUID branchId = normalizeBranch(user, request.branchId());
    UUID periodId = normalizePeriodId(user, request.financialPeriodId(), request.entryDate());
    String status =
        normalizeEnum(
            request.status() == null || request.status().isBlank() ? "DRAFT" : request.status(),
            REQUEST_JOURNAL_STATUSES,
            "Journal status");
    JournalTotals totals = validateJournalLines(user, request.lines());
    if ("POSTED".equals(status)) {
      requireOpenPeriod(user, periodId);
    }
    String journalNumber =
        request.journalNumber() == null || request.journalNumber().isBlank()
            ? existing.journalNumber()
            : normalizeCode(request.journalNumber());
    try {
      jdbcTemplate.update(
          """
          update journal_entries
          set
            branch_id = ?,
            financial_period_id = ?,
            journal_number = ?,
            entry_date = ?,
            description = ?,
            reference = ?,
            source_module = ?,
            source_transaction_id = ?,
            status = ?,
            updated_at = now(),
            version = version + 1
          where id = ? and organization_id = ?
          """,
          branchId,
          periodId,
          journalNumber,
          request.entryDate(),
          normalizeRequired(request.description(), "Journal description"),
          normalizeOptional(request.reference()),
          normalizeOptional(request.sourceModule()),
          request.sourceTransactionId(),
          status,
          journalId,
          user.organizationId());
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("Journal number is already used");
    }
    jdbcTemplate.update("delete from journal_entry_lines where journal_entry_id = ?", journalId);
    saveJournalLines(user, journalId, request.lines());
    auditEventService.record(
        user,
        branchId,
        "POSTED".equals(status) ? "accounting.journal.posted" : "accounting.journal.updated",
        "JournalEntry",
        journalId,
        Map.of(
            "journalNumber",
            journalNumber,
            "status",
            status,
            "totalDebit",
            totals.totalDebit().toPlainString()));
    return requireJournal(user, journalId);
  }

  @Transactional
  public JournalEntryResponse postJournal(AuthenticatedUser user, UUID journalId) {
    requireManage(user);
    JournalEntryResponse journal = requireJournal(user, journalId);
    if (!"DRAFT".equals(journal.status())) {
      throw new IllegalArgumentException("Only draft journals can be posted");
    }
    if (journal.totalDebit().compareTo(journal.totalCredit()) != 0
        || journal.totalDebit().signum() <= 0) {
      throw new IllegalArgumentException("Journal debits and credits must balance before posting");
    }
    requireOpenPeriod(user, journal.financialPeriodId());
    jdbcTemplate.update(
        """
        update journal_entries
        set status = 'POSTED', updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        journalId,
        user.organizationId());
    auditEventService.record(
        user,
        journal.branchId(),
        "accounting.journal.posted",
        "JournalEntry",
        journalId,
        Map.of("journalNumber", journal.journalNumber(), "totalDebit", journal.totalDebit()));
    return requireJournal(user, journalId);
  }

  @Transactional
  public JournalEntryResponse voidJournal(AuthenticatedUser user, UUID journalId) {
    requireManage(user);
    JournalEntryResponse journal = requireJournal(user, journalId);
    if ("VOID".equals(journal.status())) {
      return journal;
    }
    jdbcTemplate.update(
        """
        update journal_entries
        set status = 'VOID', updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        journalId,
        user.organizationId());
    auditEventService.record(
        user,
        journal.branchId(),
        "accounting.journal.voided",
        "JournalEntry",
        journalId,
        Map.of("journalNumber", journal.journalNumber()));
    return requireJournal(user, journalId);
  }

  @Transactional(readOnly = true)
  public List<LedgerEntryResponse> ledger(
      AuthenticatedUser user, UUID accountId, UUID branchId, LocalDate from, LocalDate to) {
    if (accountId != null) {
      requireAccount(user, accountId);
    }
    normalizeReportBranch(user, branchId);
    ReportFilter filter = reportFilter(user, "entry", branchId, from, to);
    List<Object> args = new ArrayList<>(filter.args());
    if (accountId != null) {
      args.add(accountId);
    }
    List<LedgerEntryResponse> rows =
        jdbcTemplate.query(
            """
            select
              entry.id as journal_entry_id,
              line.id as line_id,
              entry.journal_number,
              entry.entry_date,
              entry.description as journal_description,
              entry.reference,
              account.id as account_id,
              account.account_code,
              account.account_name,
              account.account_type,
              line.debit_amount,
              line.credit_amount,
              branch.name as branch_name
            from journal_entry_lines line
            join journal_entries entry on entry.id = line.journal_entry_id
            join accounts account on account.id = line.account_id
            left join branches branch on branch.id = entry.branch_id
            """
                + filter.where()
                + (accountId == null ? "" : " and account.id = ?")
                + """
                order by entry.entry_date, entry.created_at, entry.journal_number, line.id
                limit 500
                """,
            (rs, rowNum) -> mapLedgerEntry(rs, BigDecimal.ZERO),
            args.toArray());

    BigDecimal running = BigDecimal.ZERO;
    List<LedgerEntryResponse> ledger = new ArrayList<>();
    for (LedgerEntryResponse row : rows) {
      BigDecimal movement =
          normalBalanceMovement(row.accountType(), row.debitAmount(), row.creditAmount());
      running = running.add(movement);
      ledger.add(
          new LedgerEntryResponse(
              row.journalEntryId(),
              row.lineId(),
              row.journalNumber(),
              row.entryDate(),
              row.journalDescription(),
              row.reference(),
              row.accountId(),
              row.accountCode(),
              row.accountName(),
              row.accountType(),
              row.debitAmount(),
              row.creditAmount(),
              money(running),
              row.branchName()));
    }
    return ledger;
  }

  @Transactional(readOnly = true)
  public TrialBalanceResponse trialBalance(
      AuthenticatedUser user, UUID branchId, LocalDate from, LocalDate to) {
    DateRange range = normalizedPeriodRange(from, to);
    List<AccountBalance> balances =
        accountBalances(user, branchId, range.from(), range.to(), Set.of());
    List<TrialBalanceRow> rows =
        balances.stream()
            .map(
                balance -> {
                  BigDecimal net = balance.debitAmount().subtract(balance.creditAmount());
                  return new TrialBalanceRow(
                      balance.accountId(),
                      balance.accountCode(),
                      balance.accountName(),
                      balance.accountType(),
                      money(balance.debitAmount()),
                      money(balance.creditAmount()),
                      money(net.signum() > 0 ? net : BigDecimal.ZERO),
                      money(net.signum() < 0 ? net.abs() : BigDecimal.ZERO));
                })
            .toList();
    BigDecimal totalDebit =
        rows.stream().map(TrialBalanceRow::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredit =
        rows.stream().map(TrialBalanceRow::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new TrialBalanceResponse(
        range.from(), range.to(), money(totalDebit), money(totalCredit), money(totalDebit.subtract(totalCredit)), rows);
  }

  @Transactional(readOnly = true)
  public ProfitAndLossResponse profitAndLoss(
      AuthenticatedUser user, UUID branchId, LocalDate from, LocalDate to) {
    DateRange range = normalizedPeriodRange(from, to);
    List<AccountBalance> balances =
        accountBalances(user, branchId, range.from(), range.to(), Set.of("REVENUE", "COGS", "EXPENSE"));
    List<FinancialStatementRow> revenueRows = statementRows(balances, "REVENUE");
    List<FinancialStatementRow> cogsRows = statementRows(balances, "COGS");
    List<FinancialStatementRow> expenseRows = statementRows(balances, "EXPENSE");
    BigDecimal revenue = sumStatementRows(revenueRows);
    BigDecimal cogs = sumStatementRows(cogsRows);
    BigDecimal grossProfit = revenue.subtract(cogs);
    BigDecimal expenses = sumStatementRows(expenseRows);
    return new ProfitAndLossResponse(
        range.from(),
        range.to(),
        money(revenue),
        money(cogs),
        money(grossProfit),
        money(expenses),
        money(grossProfit.subtract(expenses)),
        revenueRows,
        cogsRows,
        expenseRows);
  }

  @Transactional(readOnly = true)
  public BalanceSheetResponse balanceSheet(
      AuthenticatedUser user, UUID branchId, LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    List<AccountBalance> balances =
        accountBalances(user, branchId, null, asOf, Set.of("ASSET", "LIABILITY", "EQUITY"));
    List<FinancialStatementRow> assetRows = statementRows(balances, "ASSET");
    List<FinancialStatementRow> liabilityRows = statementRows(balances, "LIABILITY");
    List<FinancialStatementRow> equityRows = statementRows(balances, "EQUITY");
    BigDecimal assets = sumStatementRows(assetRows);
    BigDecimal liabilities = sumStatementRows(liabilityRows);
    BigDecimal equity = sumStatementRows(equityRows);
    ProfitAndLossResponse retained =
        profitAndLoss(user, branchId, LocalDate.of(1900, 1, 1), asOf);
    BigDecimal retainedEarnings = retained.netIncome();
    BigDecimal totalLiabilitiesAndEquity = liabilities.add(equity).add(retainedEarnings);
    return new BalanceSheetResponse(
        asOf,
        money(assets),
        money(liabilities),
        money(equity),
        money(retainedEarnings),
        money(totalLiabilitiesAndEquity),
        money(assets.subtract(totalLiabilitiesAndEquity)),
        assetRows,
        liabilityRows,
        equityRows);
  }

  @Transactional(readOnly = true)
  public CashFlowResponse cashFlow(
      AuthenticatedUser user, UUID branchId, LocalDate from, LocalDate to) {
    DateRange range = normalizedPeriodRange(from, to);
    normalizeReportBranch(user, branchId);
    ReportFilter filter = reportFilter(user, "entry", branchId, null, range.to());
    List<Object> args = new ArrayList<>();
    args.add(range.from());
    args.add(range.from());
    args.add(range.to());
    args.add(range.from());
    args.add(range.to());
    args.add(range.to());
    args.addAll(filter.args());
    List<CashFlowRow> rows =
        jdbcTemplate.query(
            """
            select
              account.id as account_id,
              account.account_code,
              account.account_name,
              coalesce(
                sum(
                  case
                    when entry.entry_date < ? then line.debit_amount - line.credit_amount
                    else 0
                  end
                ),
                0
              ) as opening_balance,
              coalesce(
                sum(
                  case
                    when entry.entry_date >= ? and entry.entry_date <= ? then line.debit_amount
                    else 0
                  end
                ),
                0
              ) as cash_in,
              coalesce(
                sum(
                  case
                    when entry.entry_date >= ? and entry.entry_date <= ? then line.credit_amount
                    else 0
                  end
                ),
                0
              ) as cash_out,
              coalesce(
                sum(
                  case
                    when entry.entry_date <= ? then line.debit_amount - line.credit_amount
                    else 0
                  end
                ),
                0
              ) as closing_balance
            from journal_entry_lines line
            join journal_entries entry on entry.id = line.journal_entry_id
            join accounts account on account.id = line.account_id
            """
                + filter.where()
                + """
                and account.account_type = 'ASSET'
                and (
                  lower(account.account_name) like '%cash%'
                  or lower(account.account_name) like '%bank%'
                  or lower(account.account_name) like '%m-pesa%'
                  or lower(account.account_name) like '%mpesa%'
                  or lower(account.account_name) like '%petty%'
                )
                group by account.id, account.account_code, account.account_name
                order by account.account_code
                """,
            this::mapCashFlowRow,
            args.toArray());
    BigDecimal openingCash =
        rows.stream().map(CashFlowRow::openingBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cashIn =
        rows.stream().map(CashFlowRow::cashIn).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cashOut =
        rows.stream().map(CashFlowRow::cashOut).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal closingCash =
        rows.stream().map(CashFlowRow::closingBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CashFlowResponse(
        range.from(),
        range.to(),
        money(openingCash),
        money(cashIn),
        money(cashOut),
        money(cashIn.subtract(cashOut)),
        money(closingCash),
        rows);
  }

  private AccountResponse requireAccount(AuthenticatedUser user, UUID accountId) {
    List<AccountResponse> accounts =
        jdbcTemplate.query(
            """
            select
              account.id,
              account.parent_account_id,
              parent.account_code as parent_account_code,
              account.account_code,
              account.account_name,
              account.account_type,
              account.active,
              account.created_at,
              account.updated_at
            from accounts account
            left join accounts parent on parent.id = account.parent_account_id
            where account.id = ? and account.organization_id = ?
            """,
            this::mapAccount,
            accountId,
            user.organizationId());
    if (accounts.isEmpty()) {
      throw new EntityNotFoundException("Account not found");
    }
    return accounts.get(0);
  }

  private FinancialPeriodResponse requirePeriod(AuthenticatedUser user, UUID periodId) {
    List<FinancialPeriodResponse> periods =
        jdbcTemplate.query(
            """
            select id, name, period_start, period_end, status, created_at, updated_at
            from financial_periods
            where id = ? and organization_id = ?
            """,
            this::mapPeriod,
            periodId,
            user.organizationId());
    if (periods.isEmpty()) {
      throw new EntityNotFoundException("Financial period not found");
    }
    return periods.get(0);
  }

  private TaxRuleResponse requireTaxRule(AuthenticatedUser user, UUID taxRuleId) {
    List<TaxRuleResponse> rules =
        jdbcTemplate.query(
            """
            select
              id,
              tax_code,
              name,
              tax_category,
              rate,
              inclusive,
              effective_from,
              effective_to,
              active,
              created_at,
              updated_at
            from tax_rules
            where id = ? and organization_id = ?
            """,
            this::mapTaxRule,
            taxRuleId,
            user.organizationId());
    if (rules.isEmpty()) {
      throw new EntityNotFoundException("Tax rule not found");
    }
    return rules.get(0);
  }

  private JournalEntryResponse requireJournal(AuthenticatedUser user, UUID journalId) {
    BranchPredicate scope = branchPredicate(user, "entry", true);
    List<Object> args = new ArrayList<>();
    args.add(journalId);
    args.add(user.organizationId());
    args.addAll(scope.args());
    List<JournalHeader> headers =
        jdbcTemplate.query(
            """
            select
              entry.id,
              entry.branch_id,
              branch.name as branch_name,
              entry.financial_period_id,
              period.name as financial_period_name,
              entry.created_by_user_id,
              creator.display_name as created_by_name,
              entry.journal_number,
              entry.entry_date,
              entry.description,
              entry.reference,
              entry.source_module,
              entry.source_transaction_id,
              entry.status,
              entry.created_at,
              entry.updated_at
            from journal_entries entry
            left join branches branch on branch.id = entry.branch_id
            left join financial_periods period on period.id = entry.financial_period_id
            left join users creator on creator.id = entry.created_by_user_id
            where entry.id = ? and entry.organization_id = ?
            """
                + scope.sql(),
            this::mapJournalHeader,
            args.toArray());
    if (headers.isEmpty()) {
      throw new EntityNotFoundException("Journal entry not found");
    }
    return journalResponse(headers.get(0));
  }

  private JournalEntryResponse journalResponse(JournalHeader header) {
    List<JournalLineResponse> lines =
        jdbcTemplate.query(
            """
            select
              line.id,
              account.id as account_id,
              account.account_code,
              account.account_name,
              account.account_type,
              line.debit_amount,
              line.credit_amount,
              line.description
            from journal_entry_lines line
            join accounts account on account.id = line.account_id
            where line.journal_entry_id = ?
            order by account.account_code, line.id
            """,
            this::mapJournalLine,
            header.id());
    BigDecimal totalDebit =
        lines.stream().map(JournalLineResponse::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredit =
        lines.stream().map(JournalLineResponse::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new JournalEntryResponse(
        header.id(),
        header.branchId(),
        header.branchName(),
        header.financialPeriodId(),
        header.financialPeriodName(),
        header.createdByUserId(),
        header.createdByName(),
        header.journalNumber(),
        header.entryDate(),
        header.description(),
        header.reference(),
        header.sourceModule(),
        header.sourceTransactionId(),
        header.status(),
        money(totalDebit),
        money(totalCredit),
        header.createdAt(),
        header.updatedAt(),
        lines);
  }

  private void saveJournalLines(
      AuthenticatedUser user, UUID journalId, List<JournalLineRequest> lines) {
    for (JournalLineRequest line : lines) {
      jdbcTemplate.update(
          """
          insert into journal_entry_lines (
            id,
            organization_id,
            journal_entry_id,
            account_id,
            debit_amount,
            credit_amount,
            description
          )
          values (?, ?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          user.organizationId(),
          journalId,
          line.accountId(),
          moneyOrZero(line.debitAmount()),
          moneyOrZero(line.creditAmount()),
          normalizeOptional(line.description()));
    }
  }

  private JournalTotals validateJournalLines(
      AuthenticatedUser user, List<JournalLineRequest> lines) {
    if (lines == null || lines.size() < 2) {
      throw new IllegalArgumentException("Journal entries need at least two lines");
    }
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    Set<UUID> accountIds = new HashSet<>();
    for (JournalLineRequest line : lines) {
      AccountResponse account = requireAccount(user, line.accountId());
      if (!account.active()) {
        throw new IllegalArgumentException("Inactive accounts cannot be used in journal lines");
      }
      accountIds.add(account.id());
      BigDecimal debit = moneyOrZero(line.debitAmount());
      BigDecimal credit = moneyOrZero(line.creditAmount());
      boolean hasDebit = debit.signum() > 0;
      boolean hasCredit = credit.signum() > 0;
      if (hasDebit == hasCredit) {
        throw new IllegalArgumentException("Each journal line must have either a debit or a credit");
      }
      totalDebit = totalDebit.add(debit);
      totalCredit = totalCredit.add(credit);
    }
    if (accountIds.size() < 2) {
      throw new IllegalArgumentException("Journal entries must affect at least two accounts");
    }
    if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.signum() <= 0) {
      throw new IllegalArgumentException("Journal debit and credit totals must balance");
    }
    return new JournalTotals(money(totalDebit), money(totalCredit));
  }

  private List<AccountBalance> accountBalances(
      AuthenticatedUser user, UUID branchId, LocalDate from, LocalDate to, Set<String> types) {
    normalizeReportBranch(user, branchId);
    ReportFilter filter = reportFilter(user, "entry", branchId, from, to);
    List<Object> args = new ArrayList<>(filter.args());
    String typePredicate = "";
    if (types != null && !types.isEmpty()) {
      typePredicate = " and account.account_type in (" + placeholders(types.size()) + ")";
      args.addAll(types.stream().sorted().toList());
    }
    return jdbcTemplate.query(
        """
        select
          account.id as account_id,
          account.account_code,
          account.account_name,
          account.account_type,
          coalesce(sum(line.debit_amount), 0) as debit_amount,
          coalesce(sum(line.credit_amount), 0) as credit_amount
        from journal_entry_lines line
        join journal_entries entry on entry.id = line.journal_entry_id
        join accounts account on account.id = line.account_id
        """
            + filter.where()
            + typePredicate
            + """
            group by account.id, account.account_code, account.account_name, account.account_type
            order by account.account_code
            """,
        this::mapAccountBalance,
        args.toArray());
  }

  private ReportFilter reportFilter(
      AuthenticatedUser user, String alias, UUID branchId, LocalDate from, LocalDate to) {
    List<Object> args = new ArrayList<>();
    args.add(user.organizationId());
    StringBuilder where =
        new StringBuilder(
            " where " + alias + ".organization_id = ? and " + alias + ".status = 'POSTED'");
    if (branchId != null) {
      where.append(" and ").append(alias).append(".branch_id = ?");
      args.add(branchId);
    } else {
      BranchPredicate predicate = branchPredicate(user, alias, true);
      where.append(predicate.sql());
      args.addAll(predicate.args());
    }
    if (from != null) {
      where.append(" and ").append(alias).append(".entry_date >= ?");
      args.add(from);
    }
    if (to != null) {
      where.append(" and ").append(alias).append(".entry_date <= ?");
      args.add(to);
    }
    return new ReportFilter(where.toString(), args);
  }

  private List<FinancialStatementRow> statementRows(
      List<AccountBalance> balances, String accountType) {
    return balances.stream()
        .filter(balance -> accountType.equals(balance.accountType()))
        .map(
            balance ->
                new FinancialStatementRow(
                    balance.accountId(),
                    balance.accountCode(),
                    balance.accountName(),
                    balance.accountType(),
                    money(statementAmount(balance))))
        .filter(row -> row.amount().compareTo(BigDecimal.ZERO) != 0)
        .sorted(Comparator.comparing(FinancialStatementRow::accountCode))
        .toList();
  }

  private BigDecimal statementAmount(AccountBalance balance) {
    return switch (balance.accountType()) {
      case "ASSET", "COGS", "EXPENSE" -> balance.debitAmount().subtract(balance.creditAmount());
      case "LIABILITY", "EQUITY", "REVENUE" -> balance.creditAmount().subtract(balance.debitAmount());
      default -> BigDecimal.ZERO;
    };
  }

  private BigDecimal sumStatementRows(List<FinancialStatementRow> rows) {
    return rows.stream().map(FinancialStatementRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal normalBalanceMovement(
      String accountType, BigDecimal debitAmount, BigDecimal creditAmount) {
    if ("LIABILITY".equals(accountType) || "EQUITY".equals(accountType) || "REVENUE".equals(accountType)) {
      return creditAmount.subtract(debitAmount);
    }
    return debitAmount.subtract(creditAmount);
  }

  private UUID normalizeBranch(AuthenticatedUser user, UUID branchId) {
    if (branchId == null) {
      return null;
    }
    normalizeReportBranch(user, branchId);
    return branchId;
  }

  private void normalizeReportBranch(AuthenticatedUser user, UUID branchId) {
    if (branchId == null) {
      return;
    }
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from branches where id = ? and organization_id = ?",
            Integer.class,
            branchId,
            user.organizationId());
    if (count == null || count == 0 || (!user.branchIds().isEmpty() && !user.branchIds().contains(branchId))) {
      throw new AccessDeniedException("Branch is outside the authenticated user's scope");
    }
  }

  private UUID normalizePeriodId(AuthenticatedUser user, UUID periodId, LocalDate entryDate) {
    if (periodId != null) {
      requirePeriod(user, periodId);
      return periodId;
    }
    List<UUID> openPeriods =
        jdbcTemplate.queryForList(
            """
            select id
            from financial_periods
            where organization_id = ?
              and status = 'OPEN'
              and period_start <= ?
              and period_end >= ?
            order by period_start desc
            limit 1
            """,
            UUID.class,
            user.organizationId(),
            entryDate,
            entryDate);
    return openPeriods.isEmpty() ? null : openPeriods.get(0);
  }

  private void requireOpenPeriod(AuthenticatedUser user, UUID periodId) {
    if (periodId == null) {
      return;
    }
    FinancialPeriodResponse period = requirePeriod(user, periodId);
    if (!"OPEN".equals(period.status())) {
      throw new IllegalArgumentException("Journal entries can only post into open periods");
    }
  }

  private BranchPredicate branchPredicate(AuthenticatedUser user, String alias, boolean includeNull) {
    if (user.branchIds().isEmpty()) {
      return new BranchPredicate("", List.of());
    }
    List<Object> args = new ArrayList<>(user.branchIds());
    String clause =
        " and "
            + (includeNull ? "(" + alias + ".branch_id is null or " : "")
            + alias
            + ".branch_id in ("
            + placeholders(user.branchIds().size())
            + ")"
            + (includeNull ? ")" : "");
    return new BranchPredicate(clause, args);
  }

  private boolean createsAccountCycle(UUID accountId, UUID parentAccountId) {
    UUID current = parentAccountId;
    while (current != null) {
      if (current.equals(accountId)) {
        return true;
      }
      List<UUID> parents =
          jdbcTemplate.queryForList(
              "select parent_account_id from accounts where id = ?",
              UUID.class,
              current);
      current = parents.isEmpty() ? null : parents.get(0);
    }
    return false;
  }

  private void requireManage(AuthenticatedUser user) {
    if (!user.isAdmin()
        && !user.hasPermission("admin:manage")
        && !user.hasPermission("accounting:manage")) {
      throw new AccessDeniedException("Accounting management permission is required");
    }
  }

  private AccountResponse mapAccount(ResultSet rs, int rowNum) throws SQLException {
    return new AccountResponse(
        uuid(rs, "id"),
        uuid(rs, "parent_account_id"),
        rs.getString("parent_account_code"),
        rs.getString("account_code"),
        rs.getString("account_name"),
        rs.getString("account_type"),
        rs.getBoolean("active"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private FinancialPeriodResponse mapPeriod(ResultSet rs, int rowNum) throws SQLException {
    return new FinancialPeriodResponse(
        uuid(rs, "id"),
        rs.getString("name"),
        localDate(rs, "period_start"),
        localDate(rs, "period_end"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private TaxRuleResponse mapTaxRule(ResultSet rs, int rowNum) throws SQLException {
    return new TaxRuleResponse(
        uuid(rs, "id"),
        rs.getString("tax_code"),
        rs.getString("name"),
        rs.getString("tax_category"),
        money(rs.getBigDecimal("rate")),
        rs.getBoolean("inclusive"),
        localDate(rs, "effective_from"),
        localDate(rs, "effective_to"),
        rs.getBoolean("active"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private JournalHeader mapJournalHeader(ResultSet rs, int rowNum) throws SQLException {
    return new JournalHeader(
        uuid(rs, "id"),
        uuid(rs, "branch_id"),
        rs.getString("branch_name"),
        uuid(rs, "financial_period_id"),
        rs.getString("financial_period_name"),
        uuid(rs, "created_by_user_id"),
        rs.getString("created_by_name"),
        rs.getString("journal_number"),
        localDate(rs, "entry_date"),
        rs.getString("description"),
        rs.getString("reference"),
        rs.getString("source_module"),
        uuid(rs, "source_transaction_id"),
        rs.getString("status"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private JournalLineResponse mapJournalLine(ResultSet rs, int rowNum) throws SQLException {
    return new JournalLineResponse(
        uuid(rs, "id"),
        uuid(rs, "account_id"),
        rs.getString("account_code"),
        rs.getString("account_name"),
        rs.getString("account_type"),
        money(rs.getBigDecimal("debit_amount")),
        money(rs.getBigDecimal("credit_amount")),
        rs.getString("description"));
  }

  private LedgerEntryResponse mapLedgerEntry(ResultSet rs, BigDecimal runningBalance)
      throws SQLException {
    return new LedgerEntryResponse(
        uuid(rs, "journal_entry_id"),
        uuid(rs, "line_id"),
        rs.getString("journal_number"),
        localDate(rs, "entry_date"),
        rs.getString("journal_description"),
        rs.getString("reference"),
        uuid(rs, "account_id"),
        rs.getString("account_code"),
        rs.getString("account_name"),
        rs.getString("account_type"),
        money(rs.getBigDecimal("debit_amount")),
        money(rs.getBigDecimal("credit_amount")),
        money(runningBalance),
        rs.getString("branch_name"));
  }

  private AccountBalance mapAccountBalance(ResultSet rs, int rowNum) throws SQLException {
    return new AccountBalance(
        uuid(rs, "account_id"),
        rs.getString("account_code"),
        rs.getString("account_name"),
        rs.getString("account_type"),
        money(rs.getBigDecimal("debit_amount")),
        money(rs.getBigDecimal("credit_amount")));
  }

  private CashFlowRow mapCashFlowRow(ResultSet rs, int rowNum) throws SQLException {
    return new CashFlowRow(
        uuid(rs, "account_id"),
        rs.getString("account_code"),
        rs.getString("account_name"),
        money(rs.getBigDecimal("opening_balance")),
        money(rs.getBigDecimal("cash_in")),
        money(rs.getBigDecimal("cash_out")),
        money(rs.getBigDecimal("closing_balance")));
  }

  private static UUID uuid(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    return value == null ? null : (UUID) value;
  }

  private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
    Date date = rs.getDate(column);
    return date == null ? null : date.toLocalDate();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static DateRange normalizedPeriodRange(LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.now();
    LocalDate effectiveTo = to == null ? today : to;
    LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
    if (effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException("Report end date cannot be before start date");
    }
    return new DateRange(effectiveFrom, effectiveTo);
  }

  private static void validatePeriodDates(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("Financial period end date cannot be before start date");
    }
  }

  private static void validateTaxDates(LocalDate from, LocalDate to) {
    if (to != null && to.isBefore(from)) {
      throw new IllegalArgumentException("Tax rule end date cannot be before effective date");
    }
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

  private static String normalizeCode(String value) {
    return normalizeRequired(value, "Code").toUpperCase(Locale.ROOT);
  }

  private static String normalizeEnum(String value, Set<String> allowedValues, String label) {
    String normalized = normalizeRequired(value, label).toUpperCase(Locale.ROOT);
    if (!allowedValues.contains(normalized)) {
      throw new IllegalArgumentException(label + " is not valid");
    }
    return normalized;
  }

  private static BigDecimal percentage(BigDecimal value) {
    BigDecimal normalized = moneyOrZero(value);
    if (normalized.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Rate cannot be negative");
    }
    return normalized;
  }

  private static BigDecimal moneyOrZero(BigDecimal value) {
    return money(value == null ? BigDecimal.ZERO : value);
  }

  private static BigDecimal money(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
  }

  private static String nextJournalNumber() {
    String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    return "JRN-" + JOURNAL_NUMBER_TIMESTAMP.format(Instant.now()) + "-" + suffix;
  }

  private static String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }

  private record BranchPredicate(String sql, List<Object> args) {}

  private record ReportFilter(String where, List<Object> args) {}

  private record DateRange(LocalDate from, LocalDate to) {}

  private record JournalTotals(BigDecimal totalDebit, BigDecimal totalCredit) {}

  private record AccountBalance(
      UUID accountId,
      String accountCode,
      String accountName,
      String accountType,
      BigDecimal debitAmount,
      BigDecimal creditAmount) {}

  private record JournalHeader(
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
      Instant createdAt,
      Instant updatedAt) {}
}
