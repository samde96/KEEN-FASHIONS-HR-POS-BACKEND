package com.company.fashionpos.offline;

import static org.assertj.core.api.Assertions.*;

import com.company.fashionpos.catalog.*;
import com.company.fashionpos.inventory.*;
import com.company.fashionpos.sales.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false",
      "app.notifications.scheduler-enabled=false",
      "app.mpesa.enabled=false",
      "app.mpesa.scheduler-enabled=false",
      "app.security.admin-email=offline-bootstrap@example.test",
      "app.security.admin-password=test-password-only"
    })
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class OfflineOperationsIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    r.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    r.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired OfflineOperationService operations;
  @Autowired InventoryService inventory;
  @Autowired CatalogService catalog;
  UUID org, actor, branch, destination, product;
  AuthenticatedUser user;

  @BeforeEach
  void fixture() {
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    branch = UUID.randomUUID();
    destination = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Offline Test','KES','Africa/Nairobi','ACTIVE')",
        org);
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Operator','ACTIVE')",
        actor,
        org,
        actor + "@example.test");
    for (UUID id : List.of(branch, destination))
      jdbc.update(
          "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,?,?,'Africa/Nairobi','ACTIVE')",
          id,
          org,
          id.equals(branch) ? "Shop" : "Warehouse",
          id.toString().substring(0, 8));
    user =
        new AuthenticatedUser(
            actor,
            actor + "@example.test",
            "Operator",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    var category =
        catalog.createCategory(
            user, new ProductCategoryRequest("Shirts", "SHIRT", CatalogStatus.ACTIVE));
    product =
        catalog
            .createProduct(
                user,
                new ProductRequest(
                    "Shirt",
                    "OFFLINE-SHIRT",
                    "OFFLINE-BARCODE",
                    category.id(),
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
                    0))
            .id();
    jdbc.update(
        "update catalog_price_history set valid_from=now()-interval '1 day' where product_id=?",
        product);
    inventory.addStock(user, intake(product, 10));
  }

  StockIntakeRequest intake(UUID productId, int qty) {
    return new StockIntakeRequest(
        branch, productId, "Supplier", "TEST", qty, BigDecimal.valueOf(50), "");
  }

  OfflineOperationService.Request request(String kind, Object payload) {
    UUID id = UUID.randomUUID();
    return new OfflineOperationService.Request(
        id, actor, Instant.now().minusSeconds(3600), kind, json.valueToTree(payload), List.of(id));
  }

  OfflineOperationService.Request sale(PaymentMethod method) {
    var sale =
        new SaleRequest(
            branch,
            "Walk-in",
            null,
            null,
            null,
            BigDecimal.ZERO,
            List.of(
                new SalePaymentRequest(
                    method,
                    BigDecimal.valueOf(100),
                    method == PaymentMethod.MPESA ? "MANUAL-RECEIPT" : "",
                    null)),
            List.of(new SaleLineRequest(product, 1)));
    return request(
        "SALE",
        new SaleService.DeviceSale(
            sale,
            "pos-" + UUID.randomUUID(),
            List.of(new SaleService.DevicePrice(product, BigDecimal.valueOf(100), VatCategory.G)),
            method == PaymentMethod.MPESA));
  }

  int quantity(UUID location) {
    return inventory.listInventory(user).stream()
        .filter(i -> i.branchId().equals(location) && i.productId().equals(product))
        .mapToInt(InventoryResponse::quantityOnHand)
        .sum();
  }

  int receipts() {
    return jdbc.queryForObject(
        "select count(*) from offline_operations where organization_id=? and applied_at is not null",
        Integer.class,
        org);
  }

  @Test
  void lostAcknowledgementAndConcurrentRetriesApplySaleExactlyOnce() throws Exception {
    var request = sale(PaymentMethod.CASH);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> operations.apply(user, request));
      var second = pool.submit(() -> operations.apply(user, request));
      var a = first.get(20, TimeUnit.SECONDS);
      var b = second.get(20, TimeUnit.SECONDS);
      assertThat(a.result().path("id")).isEqualTo(b.result().path("id"));
      assertThat(quantity(branch)).isEqualTo(9);
      assertThat(receipts()).isEqualTo(1);
      assertThat(Instant.parse(a.result().path("soldAt").asText())).isEqualTo(request.occurredAt());
      assertThat(operations.apply(user, request).confirmed()).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void manualMpesaIsRecordedWithoutInventingAProviderTransaction() {
    var receipt = operations.apply(user, sale(PaymentMethod.MPESA));
    assertThat(receipt.result().path("payments").get(0).path("method").asText()).isEqualTo("MPESA");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from mpesa_transactions where organization_id=?",
                Integer.class,
                org))
        .isZero();
    assertThat(quantity(branch)).isEqualTo(9);
  }

  @Test
  void stockReceiptAndTransferRetriesNeverMoveStockTwice() {
    UUID draft = UUID.randomUUID();
    var request =
        request(
            "STOCK_RECEIPT",
            new OfflineOperationService.StockReceipt(
                draft,
                branch,
                json.createObjectNode().put("branchId", branch.toString()),
                List.of(intake(product, 5)),
                0));
    var acknowledged = operations.apply(user, request);
    operations.apply(user, request);
    assertThat(quantity(branch)).isEqualTo(15);
    assertThat(acknowledged.result().path("intakes")).hasSize(1);
    assertThat(
            Instant.parse(acknowledged.result().path("intakes").get(0).path("receivedAt").asText()))
        .isEqualTo(request.occurredAt());
    var transfer =
        request(
            "STOCK_TRANSFER",
            new InventoryService.TransferRequest(
                branch,
                destination,
                List.of(new InventoryService.TransferLine(product, 4)),
                "Warehouse supply"));
    operations.apply(user, transfer);
    operations.apply(user, transfer);
    assertThat(quantity(branch)).isEqualTo(11);
    assertThat(quantity(destination)).isEqualTo(4);
    assertThat(receipts()).isEqualTo(2);
  }

  @Test
  void aFailedBatchRollsBackEveryLineAndLeavesNoSuccessReceipt() {
    var receipt =
        request(
            "STOCK_RECEIPT",
            new OfflineOperationService.StockReceipt(
                UUID.randomUUID(),
                branch,
                json.createObjectNode(),
                List.of(intake(product, 3), intake(UUID.randomUUID(), 2)),
                0));
    assertThatThrownBy(() -> operations.apply(user, receipt))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    assertThat(quantity(branch)).isEqualTo(10);
    assertThat(receipts()).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from stock_operations where organization_id=?",
                Integer.class,
                org))
        .isZero();
  }

  @Test
  void changedPayloadAndAnotherOperatorCannotReuseAnId() {
    var original = sale(PaymentMethod.CASH);
    operations.apply(user, original);
    var changed =
        new OfflineOperationService.Request(
            original.id(),
            actor,
            original.occurredAt(),
            original.kind(),
            json.createObjectNode(),
            original.pendingIds());
    assertThatThrownBy(() -> operations.apply(user, changed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("different transaction");
    var other =
        new AuthenticatedUser(
            UUID.randomUUID(),
            "other@example.test",
            "Other",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of(),
            Set.of("admin:manage"));
    assertThatThrownBy(() -> operations.apply(other, original))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(quantity(branch)).isEqualTo(9);
  }

  @Test
  void stockCountsDetectConcurrentStockChangesWithoutOverwritingThem() {
    var adjustment = new StockAdjustmentRequest(branch, product, 8, "Physical count", "");
    var request =
        request("STOCK_ADJUSTMENT", Map.of("adjustment", adjustment, "expectedQuantity", 10));
    inventory.addStock(user, intake(product, 2));
    assertThatThrownBy(() -> operations.apply(user, request)).hasMessageContaining("Stock changed");
    assertThat(quantity(branch)).isEqualTo(12);
    assertThat(receipts()).isZero();
  }

  @Test
  void replayDiscoversOtherAcknowledgementsBeforeReturningANewStockBaseline() {
    var first = sale(PaymentMethod.CASH);
    var second = sale(PaymentMethod.CASH);
    operations.apply(user, first);
    operations.apply(user, second);
    var retry =
        new OfflineOperationService.Request(
            first.id(),
            actor,
            first.occurredAt(),
            first.kind(),
            first.payload(),
            List.of(first.id(), second.id()));
    var receipt = operations.apply(user, retry);
    assertThat(receipt.confirmed())
        .extracting(OfflineOperationService.Confirmation::id)
        .containsExactlyInAnyOrder(first.id(), second.id());
    assertThat(quantity(branch)).isEqualTo(8);
    assertThat(receipts()).isEqualTo(2);
  }

  @Test
  void currentPermissionsAndBothTransferLocationsAreEnforced() {
    var cashier =
        new AuthenticatedUser(
            actor,
            user.email(),
            "Cashier",
            org,
            Set.of(branch),
            Set.of("CASHIER"),
            Set.of("Cashier"),
            Set.of("pos:sell"));
    var transfer =
        request(
            "STOCK_TRANSFER",
            new InventoryService.TransferRequest(
                branch, destination, List.of(new InventoryService.TransferLine(product, 1)), ""));
    assertThatThrownBy(() -> operations.apply(cashier, transfer))
        .isInstanceOf(AccessDeniedException.class);
    var scoped =
        new AuthenticatedUser(
            actor,
            user.email(),
            "Store",
            org,
            Set.of(branch),
            Set.of("STORE_MANAGER"),
            Set.of(),
            Set.of("inventory:adjust"));
    assertThatThrownBy(() -> operations.apply(scoped, transfer))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(quantity(branch)).isEqualTo(10);
    assertThat(quantity(destination)).isZero();
  }

  @Test
  void capturedPublishedPricesSurviveCatalogueChangesButInventedPricesAreRejected() {
    var saved = sale(PaymentMethod.CASH);
    jdbc.update("update products set unit_price=200,vat_category='A' where id=?", product);
    var receipt = operations.apply(user, saved);
    assertThat(receipt.result().path("totalAmount").decimalValue()).isEqualByComparingTo("100.00");
    var forged = saved.payload().deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) forged.path("prices").get(0))
        .put("unitPrice", 1);
    var request =
        new OfflineOperationService.Request(
            UUID.randomUUID(), actor, saved.occurredAt(), "SALE", forged, List.of());
    assertThatThrownBy(() -> operations.apply(user, request))
        .hasMessageContaining("published catalogue price");
    assertThat(quantity(branch)).isEqualTo(9);
  }
}
