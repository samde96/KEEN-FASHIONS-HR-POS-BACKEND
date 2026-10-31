package com.company.fashionpos.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.company.fashionpos.accounting.AccountingDtos.AccountRequest;
import com.company.fashionpos.accounting.AccountingDtos.FinancialPeriodRequest;
import com.company.fashionpos.accounting.AccountingDtos.JournalLineRequest;
import com.company.fashionpos.accounting.AccountingService;
import com.company.fashionpos.branch.*;
import com.company.fashionpos.catalog.*;
import com.company.fashionpos.delivery.DocumentDeliveryService;
import com.company.fashionpos.inventory.*;
import com.company.fashionpos.sales.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false",
      "app.mpesa.enabled=false",
      "app.mpesa.scheduler-enabled=false",
      "app.security.admin-email=pos-test@example.test",
      "app.security.admin-password=test-password-only"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class PosEnhancementsIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired SaleService sales;
  @Autowired StockOperationService stock;
  @Autowired DailySalesService reports;
  @Autowired PosPreferencesService preferences;
  @Autowired AccountingService accounting;
  @Autowired SaleReversalService reversals;
  @Autowired BranchService branches;
  @Autowired ObjectMapper json;
  @Autowired MockMvc http;
  UUID org, userId, branch, category;
  AuthenticatedUser user;

  @BeforeEach
  void fixture() {
    org = UUID.randomUUID();
    userId = UUID.randomUUID();
    branch = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Test Business','KES','Africa/Nairobi','ACTIVE')",
        org);
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,'owner@example.test','Test Owner','ACTIVE')",
        userId,
        org);
    UUID role = UUID.randomUUID();
    jdbc.update(
        "insert into roles(id,organization_id,name,role_key) values (?,?,'Admin','ADMIN')",
        role,
        org);
    jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", userId, role);
    jdbc.update(
        "insert into role_permissions(role_id,permission_id) select ?,id from permissions where code='admin:manage'",
        role);
    jdbc.update(
        "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,'Town','TOWN','Africa/Nairobi','ACTIVE')",
        branch,
        org);
    user =
        new AuthenticatedUser(
            userId,
            "owner@example.test",
            "Owner",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    category =
        catalog
            .createCategory(
                user, new ProductCategoryRequest("Drinks", "DRINKS", CatalogStatus.ACTIVE))
            .id();
  }

  ProductResponse product(String name, String sku, ProductType type, UUID parent, int price) {
    return catalog.createProduct(
        user,
        new ProductRequest(
            name,
            sku,
            sku,
            category,
            null,
            null,
            null,
            BigDecimal.valueOf(price),
            BigDecimal.valueOf(price / 2),
            VatCategory.G,
            List.of(),
            List.of(),
            CatalogStatus.ACTIVE,
            type,
            parent,
            parent == null ? Map.of() : Map.of("Volume", name, "Packaging", "Bottle"),
            3));
  }

  StockIntakeRequest intake(UUID product, int quantity) {
    return new StockIntakeRequest(branch, product, "Supplier", "INV", quantity, BigDecimal.TEN, "");
  }

  SaleResponse sell(UUID product, int quantity) {
    return sales.createSale(
        user,
        UUID.randomUUID().toString(),
        new SaleRequest(
            branch,
            "Walk-in",
            PaymentMethod.CASH,
            null,
            null,
            BigDecimal.ZERO,
            List.of(),
            List.of(new SaleLineRequest(product, quantity))));
  }

  int quantity(UUID product) {
    return inventory.listInventory(user).stream()
        .filter(i -> i.productId().equals(product))
        .findFirst()
        .orElseThrow()
        .quantityOnHand();
  }

  @Test
  void variantsKeepStockPricesAndReceiptSnapshotsIndependent() {
    var parent = product("Cola", "COLA", ProductType.VARIABLE, null, 0);
    var small = product("300 ml", "COLA-300", ProductType.SIMPLE, parent.id(), 50);
    var large = product("500 ml", "COLA-500", ProductType.SIMPLE, parent.id(), 80);
    inventory.addStock(user, intake(small.id(), 40));
    inventory.addStock(user, intake(large.id(), 25));
    var sale = sell(small.id(), 3);
    assertThat(quantity(small.id())).isEqualTo(37);
    assertThat(quantity(large.id())).isEqualTo(25);
    assertThat(sale.totalAmount()).isEqualByComparingTo("150");
    assertThat(sale.lines().get(0).parentProductId()).isEqualTo(parent.id());
    assertThat(sale.lines().get(0).productName()).contains("Cola", "300 ml", "Packaging: Bottle");
    catalog.updateProduct(
        user,
        small.id(),
        new ProductRequest(
            "Renamed",
            small.sku(),
            small.barcode(),
            category,
            null,
            null,
            null,
            BigDecimal.valueOf(70),
            BigDecimal.TEN,
            VatCategory.G,
            List.of(),
            List.of(),
            CatalogStatus.ACTIVE));
    assertThat(sales.getSale(user, sale.id()).lines().get(0).productName())
        .contains("300 ml")
        .doesNotContain("Renamed");
    assertThat(inventory.listInventory(user).get(0).reorderLevel()).isEqualTo(3);
    assertThat(
            catalog.listProducts(user).stream()
                .filter(p -> p.id().equals(parent.id()))
                .findFirst()
                .orElseThrow()
                .totalStock())
        .isEqualTo(62);
  }

  @Test
  void parentsAndInactiveVariantsCannotBeSoldOrRestocked() {
    var parent = product("Cola", "COLA", ProductType.VARIABLE, null, 0);
    var variant = product("500 ml", "COLA-500", ProductType.SIMPLE, parent.id(), 80);
    assertThatThrownBy(() -> inventory.addStock(user, intake(parent.id(), 5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sell(parent.id(), 1)).isInstanceOf(IllegalArgumentException.class);
    inventory.addStock(user, intake(variant.id(), 5));
    catalog.deactivateProduct(user, parent.id());
    assertThatThrownBy(() -> sell(variant.id(), 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> inventory.addStock(user, intake(variant.id(), 5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(quantity(variant.id())).isEqualTo(5);
  }

  @Test
  void simpleProductsStillSellAndBankTransfersReconcile() {
    var simple = product("Bread", "BREAD", ProductType.SIMPLE, null, 100);
    inventory.addStock(user, intake(simple.id(), 10));
    var sale =
        sales.createSale(
            user,
            "bank-transfer",
            new SaleRequest(
                branch,
                "Walk-in",
                PaymentMethod.BANK_TRANSFER,
                "BANK-123",
                null,
                BigDecimal.ZERO,
                List.of(),
                List.of(new SaleLineRequest(simple.id(), 2))));
    assertThat(sale.totalAmount()).isEqualByComparingTo("200");
    assertThat(quantity(simple.id())).isEqualTo(8);
  }

  @Test
  void concurrentSalesCannotOversell() throws Exception {
    var p = product("Water", "WATER", ProductType.SIMPLE, null, 50);
    inventory.addStock(user, intake(p.id(), 5));
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> task =
          () -> {
            start.await();
            try {
              sell(p.id(), 4);
              return true;
            } catch (IllegalArgumentException e) {
              return false;
            }
          };
      var first = executor.submit(task);
      var second = executor.submit(task);
      start.countDown();
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(quantity(p.id())).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentInitialStockReceiptsShareOneInventoryRow() throws Exception {
    var p = product("Water", "WATER", ProductType.SIMPLE, null, 50);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Void> task =
          () -> {
            start.await();
            inventory.addStock(user, intake(p.id(), 3));
            return null;
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
      assertThat(quantity(p.id())).isEqualTo(6);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void stockDraftCompletesAtomicallyAndRetriesOnce() {
    var p = product("Water", "WATER", ProductType.SIMPLE, null, 50);
    UUID id = UUID.randomUUID();
    var saved =
        stock.save(
            user,
            id,
            new StockOperationService.DraftRequest(
                branch,
                json.createObjectNode().put("quantity", "3"),
                List.of(intake(p.id(), 3)),
                0,
                null));
    assertThat(saved.createdAt()).isNotNull();
    assertThat(inventory.listInventory(user)).isEmpty();
    assertThat(stock.complete(user, id).status()).isEqualTo("COMPLETED");
    assertThat(stock.complete(user, id).completedAt()).isNotNull();
    assertThat(quantity(p.id())).isEqualTo(3);
    UUID failed = UUID.randomUUID();
    stock.save(
        user,
        failed,
        new StockOperationService.DraftRequest(
            branch,
            json.createObjectNode(),
            List.of(intake(p.id(), 2), intake(UUID.randomUUID(), 2)),
            0,
            null));
    assertThatThrownBy(() -> stock.complete(user, failed)).isInstanceOf(RuntimeException.class);
    assertThat(quantity(p.id())).isEqualTo(3);
    assertThat(stock.list(user)).extracting(StockOperationService.Operation::id).contains(failed);
    assertThatThrownBy(
            () ->
                stock.save(
                    user,
                    failed,
                    new StockOperationService.DraftRequest(
                        branch, json.createObjectNode(), List.of(), 0, null)))
        .isInstanceOf(IllegalArgumentException.class);
    stock.discard(user, failed);
    assertThat(stock.list(user)).isEmpty();
  }

  @Test
  void reportWindowUsesBusinessMidnightAndExcludesExactlyNinePm() {
    LocalDate date = LocalDate.of(2026, 10, 20);
    var p = product("Water", "WATER", ProductType.SIMPLE, null, 100);
    inventory.addStock(user, intake(p.id(), 10));
    var midnight = sell(p.id(), 1);
    var last = sell(p.id(), 2);
    var late = sell(p.id(), 3);
    var before = sell(p.id(), 1);
    soldAt(midnight.id(), "2026-10-19T21:00:00Z");
    soldAt(last.id(), "2026-10-20T17:59:59.999999Z");
    soldAt(late.id(), "2026-10-20T18:00:00Z");
    soldAt(before.id(), "2026-10-19T20:59:59Z");
    var report = reports.generateForUser(user, branch, date);
    assertThat(report.transactions()).isEqualTo(2);
    assertThat(report.grossSales()).isEqualByComparingTo("300");
    assertThat(report.itemsSold()).isEqualTo(3);
    assertThat(report.payments().get("CASH")).isEqualByComparingTo("300");
    assertThat(reports.generateForUser(user, branch, date).id()).isEqualTo(report.id());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries where report_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(1);
    reports.resend(user, report.id(), "manual-1");
    reports.resend(user, report.id(), "manual-1");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries where report_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(2);
  }

  @Test
  void splitPaymentsDiscountsRefundsAndCancellationAreCountedWithoutJoinMultiplication() {
    var parent = product("Cola", "COLA", ProductType.VARIABLE, null, 0);
    var p = product("500 ml", "COLA-500", ProductType.SIMPLE, parent.id(), 100);
    inventory.addStock(user, intake(p.id(), 20));
    var split =
        sales.createSale(
            user,
            "split",
            new SaleRequest(
                branch,
                "Buyer",
                null,
                null,
                null,
                BigDecimal.TEN,
                List.of(
                    new SalePaymentRequest(
                        PaymentMethod.CASH, BigDecimal.valueOf(80), null, BigDecimal.valueOf(100)),
                    new SalePaymentRequest(
                        PaymentMethod.MPESA, BigDecimal.valueOf(110), "MP1", null)),
                List.of(new SaleLineRequest(p.id(), 2))));
    var refunded = sell(p.id(), 1);
    var cancelled = sell(p.id(), 1);
    var refund =
        reversals.reverse(
            user, refunded.id(), new SaleReversalService.Request("REFUND", "Returned"));
    reversals.reverse(user, refunded.id(), new SaleReversalService.Request("REFUND", "Retry"));
    var cancel =
        reversals.reverse(
            user, cancelled.id(), new SaleReversalService.Request("CANCELLATION", "Wrong sale"));
    for (var sale : List.of(split, refunded, cancelled)) soldAt(sale.id(), "2026-10-20T10:00:00Z");
    jdbc.update(
        "update sale_reversals set created_at=? where id in (?,?)",
        Timestamp.from(Instant.parse("2026-10-20T11:00:00Z")),
        refund.get("id"),
        cancel.get("id"));
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    assertThat(report.grossSales()).isEqualByComparingTo("400");
    assertThat(report.discounts()).isEqualByComparingTo("10");
    assertThat(report.refunds()).isEqualByComparingTo("100");
    assertThat(report.cancellations()).isEqualByComparingTo("100");
    assertThat(report.netSales()).isEqualByComparingTo("190");
    assertThat(report.totalRevenue()).isEqualByComparingTo("190");
    assertThat(report.payments().get("CASH")).isEqualByComparingTo("80");
    assertThat(report.payments().get("MPESA")).isEqualByComparingTo("110");
    assertThat(report.topVariants()).hasSize(1);
    assertThat(report.parentProducts().get(0).get("name")).isEqualTo("Cola");
    assertThat(quantity(p.id())).isEqualTo(18);
  }

  @Test
  void reportsAndDraftsRespectOrganizationBranchAndUserBoundaries() {
    var other =
        new AuthenticatedUser(
            UUID.randomUUID(),
            "other@test",
            "Other",
            UUID.randomUUID(),
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    assertThatThrownBy(() -> reports.history(other, branch))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> product("500 ml", "X", ProductType.SIMPLE, UUID.randomUUID(), 20))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    UUID id = UUID.randomUUID();
    stock.save(
        user,
        id,
        new StockOperationService.DraftRequest(
            branch, json.createObjectNode(), List.of(), 0, null));
    assertThat(stock.list(other)).isEmpty();
    assertThatThrownBy(() -> stock.complete(other, id))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
  }

  @Test
  void deliveryRetriesFailuresAndDeduplicatesInAppMessages() {
    preferences.save(
        user, new PosPreferences(true, true, true, List.of(userId), 5, 10, "Africa/Nairobi"));
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    DocumentDeliveryService email = mock(DocumentDeliveryService.class);
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now()+interval '1 day' where report_id<>?",
        report.id());
    doThrow(new IllegalStateException("temporary"))
        .doNothing()
        .when(email)
        .sendSummary(anyString(), anyString(), anyString(), anyString());
    DailySummaryDelivery delivery = new DailySummaryDelivery(jdbc, reports, email);
    // Run this instance through a transaction just like the production proxied worker.
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()));
    for (int i = 0; i < 2; i++) tx.execute(status -> delivery.deliverNext());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notifications where source_record_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(1);
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now() where report_id=?", report.id());
    tx.execute(status -> delivery.deliverNext());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries where report_id=? and status='SENT'",
                Integer.class,
                report.id()))
        .isEqualTo(2);
    verify(email, times(2)).sendSummary(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void schedulerWaitsUntilNineAndDuplicateWorkersDoNotSendTwice() throws Exception {
    preferences.save(
        user, new PosPreferences(true, true, false, List.of(userId), 5, 10, "Africa/Nairobi"));
    jdbc.update(
        "update pos_preferences set schedule_started_at='2026-10-20T00:00:00Z' where organization_id=?",
        org);
    jdbc.update("update branches set created_at='2026-10-20T00:00:00Z' where id=?", branch);
    var delivery = mock(DailySummaryDelivery.class);
    var before =
        new DailySalesScheduler(
            jdbc,
            reports,
            delivery,
            Clock.fixed(Instant.parse("2026-10-20T17:59:59Z"), ZoneOffset.UTC));
    before.tick();
    assertThat(reports.history(user, branch)).isEmpty();
    var atNine =
        new DailySalesScheduler(
            jdbc,
            reports,
            delivery,
            Clock.fixed(Instant.parse("2026-10-20T18:00:00Z"), ZoneOffset.UTC));
    var executor = Executors.newFixedThreadPool(2);
    try {
      var a = executor.submit(atNine::tick);
      var b = executor.submit(atNine::tick);
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    atNine.tick();
    assertThat(reports.history(user, branch)).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries d join daily_sales_reports r on r.id=d.report_id where r.organization_id=?",
                Integer.class,
                org))
        .isEqualTo(1);
  }

  @Test
  void taxAndLateRefundsUseEventDatesAndNetPaymentsReconcile() {
    var p =
        catalog.createProduct(
            user,
            new ProductRequest(
                "Taxable",
                "TAX",
                "TAX",
                category,
                null,
                null,
                null,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(40),
                VatCategory.A,
                List.of(),
                List.of(),
                CatalogStatus.ACTIVE));
    inventory.addStock(user, intake(p.id(), 5));
    var sale = sell(p.id(), 2);
    soldAt(sale.id(), "2026-10-19T10:00:00Z");
    var refund =
        reversals.reverse(
            user, sale.id(), new SaleReversalService.Request("REFUND", "Later return"));
    jdbc.update(
        "update sale_reversals set created_at='2026-10-20T10:00:00Z' where id=?", refund.get("id"));
    var soldDay = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 19));
    assertThat(soldDay.netSales()).isEqualByComparingTo("200");
    assertThat(soldDay.tax()).isEqualByComparingTo("32");
    assertThat(soldDay.totalRevenue()).isEqualByComparingTo("232");
    var returnDay = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    assertThat(returnDay.transactions()).isZero();
    assertThat(returnDay.refunds()).isEqualByComparingTo("232");
    assertThat(returnDay.netSales()).isEqualByComparingTo("-200");
    assertThat(returnDay.tax()).isEqualByComparingTo("-32");
    assertThat(returnDay.totalRevenue()).isEqualByComparingTo(returnDay.payments().get("CASH"));
  }

  @Test
  void recipientsAreLimitedToTheirAssignedBranchAndRevokedAccessStopsDelivery() {
    UUID second =
        branches
            .create(user, new BranchRequest("Outlet", "OUT", "Africa/Nairobi", BranchStatus.ACTIVE))
            .id();
    jdbc.update(
        "insert into user_branch_assignments (id,user_id,branch_id) values (?,?,?)",
        UUID.randomUUID(),
        userId,
        branch);
    assertThat(reports.recipients(org, branch)).hasSize(1);
    assertThat(reports.recipients(org, second)).isEmpty();
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now()+interval '1 day' where report_id<>?",
        report.id());
    jdbc.update("delete from user_roles where user_id=?", userId);
    var email = mock(DocumentDeliveryService.class);
    var delivery = new DailySummaryDelivery(jdbc, reports, email);
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()));
    tx.execute(status -> delivery.deliverNext());
    assertThat(
            jdbc.queryForObject(
                "select status from daily_summary_deliveries where report_id=?",
                String.class,
                report.id()))
        .isEqualTo("FAILED");
    verifyNoInteractions(email);
  }

  @Test
  void timezoneWindowsFollowDaylightSavingChanges() {
    var spring = DailySalesService.window(LocalDate.of(2026, 3, 8), "America/New_York");
    var fall = DailySalesService.window(LocalDate.of(2026, 11, 1), "America/New_York");
    assertThat(Duration.between(spring.fromInclusive(), spring.toExclusive()).toHours())
        .isEqualTo(20);
    assertThat(Duration.between(fall.fromInclusive(), fall.toExclusive()).toHours()).isEqualTo(22);
  }

  @Test
  void cashierCannotChangeSettingsGenerateReportsOrReverseSales() throws Exception {
    var cashier =
        SecurityMockMvcRequestPostProcessors.user("cashier")
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("pos:sell"));
    var csrf = SecurityMockMvcRequestPostProcessors.csrf();
    http.perform(
            MockMvcRequestBuilders.put("/api/v1/pos-preferences")
                .with(cashier)
                .with(csrf)
                .contentType("application/json")
                .content("{}"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
    http.perform(
            MockMvcRequestBuilders.post("/api/v1/daily-sales-reports/generate")
                .with(cashier)
                .with(csrf))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
    http.perform(
            MockMvcRequestBuilders.post("/api/v1/sales/" + UUID.randomUUID() + "/reversal")
                .with(cashier)
                .with(csrf)
                .contentType("application/json")
                .content("{}"))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
  }

  @Test
  void unrelatedCatalogEditsPreserveShopReorderOverrides() {
    var p = product("Water", "WATER", ProductType.SIMPLE, null, 100);
    inventory.addStock(user, intake(p.id(), 10));
    var item = inventory.listInventory(user).get(0);
    inventory.updateReorderLevel(user, item.id(), new InventoryReorderLevelRequest(9));
    catalog.updateProduct(
        user,
        p.id(),
        new ProductRequest(
            "Water renamed",
            p.sku(),
            p.barcode(),
            category,
            null,
            null,
            null,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(50),
            VatCategory.G,
            List.of(),
            List.of(),
            CatalogStatus.ACTIVE,
            ProductType.SIMPLE,
            null,
            Map.of(),
            3));
    assertThat(inventory.listInventory(user).get(0).reorderLevel()).isEqualTo(9);
    catalog.updateProduct(
        user,
        p.id(),
        new ProductRequest(
            "Water renamed",
            p.sku(),
            p.barcode(),
            category,
            null,
            null,
            null,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(50),
            VatCategory.G,
            List.of(),
            List.of(),
            CatalogStatus.ACTIVE,
            ProductType.SIMPLE,
            null,
            Map.of(),
            4));
    assertThat(inventory.listInventory(user).get(0).reorderLevel()).isEqualTo(4);
  }

  @Test
  void skuAndBarcodeUniquenessAreCaseInsensitive() {
    product("Water", "WATER", ProductType.SIMPLE, null, 100);
    assertThatThrownBy(() -> product("Duplicate", "water", ProductType.SIMPLE, null, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SKU");
    assertThatThrownBy(
            () ->
                catalog.createProduct(
                    user,
                    new ProductRequest(
                        "Duplicate barcode",
                        "OTHER",
                        "water",
                        category,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        VatCategory.G,
                        List.of(),
                        List.of(),
                        CatalogStatus.ACTIVE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("barcode");
  }

  @Test
  void stockInactivityDefaultsToTwoDaysAndCustomDayIntervalsPersistWithValidation()
      throws Exception {
    assertThat(preferences.get(org).inactivityMinutes()).isEqualTo(2880);
    jdbc.update("insert into pos_preferences(organization_id) values (?)", org);
    assertThat(preferences.get(org).inactivityMinutes()).isEqualTo(2880);
    String email = userId + "@example.test";
    jdbc.update("update users set email=? where id=?", email, userId);
    var principal =
        SecurityMockMvcRequestPostProcessors.user(email)
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "admin:manage"));
    for (int days : List.of(2, 7, 21, 365)) {
      http.perform(
              MockMvcRequestBuilders.put("/api/v1/pos-preferences")
                  .with(principal)
                  .with(SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          new PosPreferences(
                              true, true, false, List.of(), days * 1440, 10, "Africa/Nairobi"))))
          .andExpect(MockMvcResultMatchers.status().isOk());
      assertThat(preferences.get(org).inactivityMinutes()).isEqualTo(days * 1440);
    }
    for (int invalid : List.of(0, 525601)) {
      http.perform(
              MockMvcRequestBuilders.put("/api/v1/pos-preferences")
                  .with(principal)
                  .with(SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          new PosPreferences(
                              true, true, false, List.of(), invalid, 10, "Africa/Nairobi"))))
          .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
    assertThat(preferences.get(org).inactivityMinutes()).isEqualTo(365 * 1440);
  }

  @Test
  void draftJournalsStayOutOfReportsUntilPostedAndAutoMatchThePeriodByDate() throws Exception {
    var cash =
        accounting.createAccount(
            user, new AccountRequest(null, "1000", "Shop Cash", "ASSET", true));
    var bank =
        accounting.createAccount(user, new AccountRequest(null, "1100", "Bank", "ASSET", true));
    accounting.createPeriod(
        user,
        new FinancialPeriodRequest(
            "FY 2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "OPEN"));
    var period =
        accounting.createPeriod(
            user,
            new FinancialPeriodRequest(
                "FY 2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "OPEN"));
    String email = userId + "@example.test";
    jdbc.update("update users set email=? where id=?", email, userId);
    var principal =
        SecurityMockMvcRequestPostProcessors.user(email)
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "admin:manage"));
    var body = json.createObjectNode();
    body.put("branchId", branch.toString());
    body.putNull("financialPeriodId");
    body.put("journalNumber", "");
    body.put("entryDate", "2026-10-29");
    body.put("description", "Move Bank money to Shop Cash");
    body.put("status", "DRAFT");
    body.set(
        "lines",
        json.valueToTree(
            List.of(
                new JournalLineRequest(cash.id(), BigDecimal.valueOf(5000), BigDecimal.ZERO, ""),
                new JournalLineRequest(bank.id(), BigDecimal.ZERO, BigDecimal.valueOf(5000), ""))));
    var result =
        http.perform(
                MockMvcRequestBuilders.post("/api/v1/accounting/journals")
                    .with(principal)
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType("application/json")
                    .content(body.toString()))
            .andExpect(MockMvcResultMatchers.status().isCreated())
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.financialPeriodId").value(period.id().toString()))
            .andReturn();
    UUID id =
        UUID.fromString(
            json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    LocalDate date = LocalDate.of(2026, 10, 29);
    assertThat(accounting.ledger(user, null, branch, date, date)).isEmpty();
    assertThat(accounting.trialBalance(user, branch, date, date).totalDebit()).isZero();
    http.perform(
            MockMvcRequestBuilders.post("/api/v1/accounting/journals/" + id + "/post")
                .with(principal)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(MockMvcResultMatchers.status().isOk());
    assertThat(accounting.ledger(user, null, branch, date, date)).hasSize(2);
    assertThat(accounting.ledger(user, cash.id(), branch, date, date)).hasSize(1);
    assertThat(accounting.profitAndLoss(user, branch, date, date).netIncome()).isZero();
    assertThat(accounting.balanceSheet(user, branch, date).assets()).isZero();
    assertThat(accounting.cashFlow(user, branch, date, date).cashIn()).isEqualByComparingTo("5000");
    assertThat(accounting.trialBalance(user, branch, date, date).totalDebit())
        .isEqualByComparingTo("5000");
    http.perform(
            MockMvcRequestBuilders.put("/api/v1/accounting/journals/" + id)
                .with(principal)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content(body.toString()))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
    http.perform(
            MockMvcRequestBuilders.delete("/api/v1/accounting/journals/" + id)
                .with(principal)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(MockMvcResultMatchers.status().isOk());
    assertThat(accounting.ledger(user, null, branch, date, date)).isEmpty();
    assertThat(accounting.listJournals(user).get(0).status()).isEqualTo("VOID");
  }

  @Test
  void invalidTimezoneAndForeignRecipientsCannotBeSaved() {
    assertThatThrownBy(
            () ->
                preferences.save(
                    user, new PosPreferences(true, true, false, List.of(), 5, 10, "Invalid/Zone")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timezone");
    assertThatThrownBy(
            () ->
                preferences.save(
                    user,
                    new PosPreferences(
                        true, true, false, List.of(UUID.randomUUID()), 5, 10, "Africa/Nairobi")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Recipients");
  }

  @Test
  void resendAndDiscardReturnTheEmptySuccessStatusExpectedByTheClient() throws Exception {
    String email = userId + "@example.test";
    jdbc.update("update users set email=? where id=?", email, userId);
    var principal =
        SecurityMockMvcRequestPostProcessors.user(email)
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "admin:manage"));
    var csrf = SecurityMockMvcRequestPostProcessors.csrf();
    UUID draft = UUID.randomUUID();
    stock.save(
        user,
        draft,
        new StockOperationService.DraftRequest(
            branch, json.createObjectNode(), List.of(), 0, null));
    http.perform(
            MockMvcRequestBuilders.delete("/api/v1/stock-operations/" + draft)
                .with(principal)
                .with(csrf))
        .andExpect(MockMvcResultMatchers.status().isNoContent());
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    http.perform(
            MockMvcRequestBuilders.post("/api/v1/daily-sales-reports/" + report.id() + "/resend")
                .header("Idempotency-Key", "http-resend")
                .with(principal)
                .with(csrf))
        .andExpect(MockMvcResultMatchers.status().isNoContent());
  }

  void soldAt(UUID id, String instant) {
    jdbc.update(
        "update sales set sold_at=? where id=?", Timestamp.from(Instant.parse(instant)), id);
  }
}
