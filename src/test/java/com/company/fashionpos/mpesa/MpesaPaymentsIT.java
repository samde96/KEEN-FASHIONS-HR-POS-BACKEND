package com.company.fashionpos.mpesa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.company.fashionpos.catalog.*;
import com.company.fashionpos.inventory.*;
import com.company.fashionpos.sales.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false",
      "app.notifications.scheduler-enabled=false",
      "app.mpesa.scheduler-enabled=false",
      "app.security.admin-email=mpesa-bootstrap@example.test",
      "app.security.admin-password=test-password-only",
      "app.mpesa.enabled=true",
      "app.mpesa.environment=sandbox",
      "app.mpesa.organization-id=8ec12957-932a-449f-9299-68194886f413",
      "app.mpesa.consumer-key=test-consumer",
      "app.mpesa.consumer-secret=test-consumer-secret",
      "app.mpesa.passkey=test-passkey",
      "app.mpesa.shortcode=174379",
      "app.mpesa.party-b=174379",
      "app.mpesa.callback-base-url=https://payments.example.test",
      "app.mpesa.callback-secret=test-callback-secret-at-least-32-characters",
      "app.mpesa.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "app.mpesa.callback-allowed-ips=196.201.214.200",
      "app.mpesa.trusted-proxies=10.0.0.0/8"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class MpesaPaymentsIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MpesaService payments;
  @Autowired MpesaLedger ledger;
  @Autowired MpesaCrypto crypto;
  @Autowired MpesaProperties properties;
  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired SaleService sales;
  @Autowired ObjectMapper json;
  @Autowired MockMvc http;
  @MockitoBean DarajaClient daraja;
  final UUID org = UUID.fromString("8ec12957-932a-449f-9299-68194886f413");
  UUID branch, userId, product;
  AuthenticatedUser operator;
  SaleRequest sale;
  String key;
  final String phone = "0712345678";

  @BeforeEach
  void fixture() {
    reset(daraja);
    key = UUID.randomUUID().toString();
    branch = UUID.randomUUID();
    userId = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Payments Test','KES','Africa/Nairobi','ACTIVE') on conflict do nothing",
        org);
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Operator','ACTIVE')",
        userId,
        org,
        userId + "@example.test");
    UUID role =
        jdbc
            .query(
                "select id from roles where organization_id=? and role_key='ADMIN'",
                (r, n) -> r.getObject(1, UUID.class),
                org)
            .stream()
            .findFirst()
            .orElseGet(
                () -> {
                  UUID id = UUID.randomUUID();
                  jdbc.update(
                      "insert into roles(id,organization_id,name,role_key) values (?,?,'Admin','ADMIN')",
                      id,
                      org);
                  jdbc.update(
                      "insert into role_permissions(role_id,permission_id) select ?,id from permissions where code='admin:manage'",
                      id);
                  return id;
                });
    jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", userId, role);
    jdbc.update(
        "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,'Payments Shop',?,'Africa/Nairobi','ACTIVE')",
        branch,
        org,
        branch.toString().substring(0, 8));
    operator =
        new AuthenticatedUser(
            userId,
            userId + "@example.test",
            "Operator",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    var category =
        catalog.createCategory(
            operator,
            new ProductCategoryRequest(
                "Category " + branch, branch.toString(), CatalogStatus.ACTIVE));
    product =
        catalog
            .createProduct(
                operator,
                new ProductRequest(
                    "Shirt",
                    branch.toString(),
                    branch.toString(),
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
    inventory.addStock(
        operator,
        new StockIntakeRequest(
            branch, product, "Supplier", "TEST", 10, BigDecimal.valueOf(50), ""));
    sale =
        new SaleRequest(
            branch,
            "Walk-in",
            null,
            null,
            null,
            BigDecimal.ZERO,
            List.of(new SalePaymentRequest(PaymentMethod.MPESA, BigDecimal.valueOf(100), "", null)),
            List.of(new SaleLineRequest(product, 1)));
    when(daraja.push(any()))
        .thenReturn(
            json.createObjectNode()
                .put("ResponseCode", "0")
                .put("MerchantRequestID", "merchant-" + key)
                .put("CheckoutRequestID", "checkout-" + key));
  }

  @AfterEach
  void clearQueue() {
    jdbc.update(
        "update mpesa_transactions set status='FAILED' where organization_id=? and sale_id is null",
        org);
  }

  MpesaDtos.Payment start() {
    return payments.start(operator, key, new MpesaDtos.StartRequest(phone, sale));
  }

  void dispatch() {
    new MpesaWorker(properties, ledger, daraja).dispatch();
  }

  String receipt() {
    return "T" + key.replace("-", "").substring(0, 9).toUpperCase();
  }

  JsonNode callback(int code, int amount) {
    var result =
        json.createObjectNode()
            .put("MerchantRequestID", "merchant-" + key)
            .put("CheckoutRequestID", "checkout-" + key)
            .put("ResultCode", code);
    if (code == 0) {
      var items = result.putObject("CallbackMetadata").putArray("Item");
      items.addObject().put("Name", "Amount").put("Value", amount);
      items.addObject().put("Name", "MpesaReceiptNumber").put("Value", receipt());
      items.addObject().put("Name", "PhoneNumber").put("Value", 254712345678L);
      items
          .addObject()
          .put("Name", "TransactionDate")
          .put(
              "Value",
              DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                  .withZone(ZoneId.of("Africa/Nairobi"))
                  .format(Instant.now()));
    }
    var payload = json.createObjectNode();
    payload.putObject("Body").set("stkCallback", result);
    return payload;
  }

  void confirm(MpesaDtos.Payment tx) {
    var payload = callback(0, 100);
    ledger.callback(tx.id(), payload, MpesaCrypto.sha256(payload.toString()));
  }

  SaleRequest withReceipt() {
    return new SaleRequest(
        branch,
        "Walk-in",
        null,
        null,
        null,
        BigDecimal.ZERO,
        List.of(
            new SalePaymentRequest(PaymentMethod.MPESA, BigDecimal.valueOf(100), receipt(), null)),
        sale.lines());
  }

  MockHttpServletRequestBuilder webhook(
      MpesaDtos.Payment tx, String ip, String forwarded, String token) throws Exception {
    var request =
        post("/api/v1/mpesa/callback/" + tx.id() + "/" + token)
            .contentType("application/json")
            .content(json.writeValueAsBytes(callback(0, 100)))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                });
    if (forwarded != null) request.header("X-Forwarded-For", forwarded);
    return request;
  }

  @Test
  void initiationIsFastDurableAndNeverWaitsForDaraja() throws Exception {
    var request =
        post("/api/v1/mpesa/stkpush")
            .with(user(operator.email()).authorities(new SimpleGrantedAuthority("admin:manage")))
            .with(csrf())
            .header("Idempotency-Key", key)
            .contentType("application/json")
            .content(json.writeValueAsBytes(new MpesaDtos.StartRequest(phone, sale)));
    assertThatCode(
            () ->
                http.perform(request)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED")))
        .doesNotThrowAnyException();
    verifyNoInteractions(daraja);
    String encrypted =
        jdbc.queryForObject(
            "select phone_ciphertext from mpesa_transactions where idempotency_key=?",
            String.class,
            key);
    assertThat(encrypted).doesNotContain("254712345678");
    assertThat(crypto.decryptPhone(encrypted)).isEqualTo("254712345678");
  }

  @Test
  void concurrentClicksCreateOneChargeAndReplayedCallbacksOneSale() throws Exception {
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<UUID>> calls = new ArrayList<>();
      for (int i = 0; i < 16; i++) calls.add(() -> start().id());
      var results = pool.invokeAll(calls);
      UUID id = results.get(0).get();
      for (var result : results) assertThat(result.get()).isEqualTo(id);
    } finally {
      pool.shutdownNow();
    }
    var tx = start();
    dispatch();
    verify(daraja, times(1)).push(any());
    confirm(tx);
    confirm(tx);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from mpesa_callback_events where transaction_id=?",
                Integer.class,
                tx.id()))
        .isEqualTo(1);
    var first = sales.createSale(operator, key, withReceipt());
    assertThat(sales.createSale(operator, key, withReceipt()).id()).isEqualTo(first.id());
    assertThat(
            jdbc.queryForObject(
                "select quantity_on_hand from inventory_items where branch_id=? and product_id=?",
                Integer.class,
                branch,
                product))
        .isEqualTo(9);
    assertThat(ledger.get(operator, tx.id()).saleId()).isEqualTo(first.id());
  }

  @Test
  void changedPayloadAndNewKeyCannotChargeAnUnresolvedPhone() {
    start();
    assertThatThrownBy(
            () -> payments.start(operator, key, new MpesaDtos.StartRequest("0712345679", sale)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                payments.start(
                    operator,
                    UUID.randomUUID().toString(),
                    new MpesaDtos.StartRequest(phone, sale)))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(daraja);
  }

  @Test
  void timeoutAndWorkerRestartNeverResendAnAmbiguousCharge() {
    var tx = start();
    when(daraja.push(any())).thenThrow(new DarajaClient.Uncertain());
    dispatch();
    dispatch();
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("UNKNOWN");
    assertThat(start().id()).isEqualTo(tx.id());
    verify(daraja, times(1)).push(any());
    assertThatThrownBy(
            () ->
                payments.start(
                    operator,
                    UUID.randomUUID().toString(),
                    new MpesaDtos.StartRequest(phone, sale)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void crashedSenderIsNotDispatchedAgain() {
    var tx = start();
    ledger.claimPush();
    jdbc.update(
        "update mpesa_transactions set updated_at=now()-interval '1 minute' where id=?", tx.id());
    dispatch();
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("UNKNOWN");
    verifyNoInteractions(daraja);
  }

  @Test
  void callbacksBeforeAcknowledgementAndLateFailureAreSafe() {
    var tx = start();
    ledger.claimPush();
    confirm(tx);
    ledger.submitted(tx.id(), "merchant-" + key, "checkout-" + key);
    var failure = callback(1032, 0);
    ledger.callback(tx.id(), failure, MpesaCrypto.sha256(failure.toString()));
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void querySuccessWithoutReceiptDoesNotSettleAndNeverRecharges() {
    var tx = start();
    dispatch();
    ledger.queried(
        tx.id(),
        json.createObjectNode()
            .put("CheckoutRequestID", "checkout-" + key)
            .put("MerchantRequestID", "merchant-" + key)
            .put("ResultCode", "0"));
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("PAID_UNCONFIRMED");
    assertThatThrownBy(() -> sales.createSale(operator, key, withReceipt()))
        .isInstanceOf(ResponseStatusException.class);
    confirm(tx);
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void mismatchedAmountCannotSettleOrReleaseThePhone() {
    var tx = start();
    dispatch();
    var invalid = callback(0, 101);
    ledger.callback(tx.id(), invalid, MpesaCrypto.sha256(invalid.toString()));
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("REVIEW");
    assertThatThrownBy(() -> sales.createSale(operator, key, withReceipt()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void callbackFirewallRejectsSpoofedHeadersAndWrongSecrets() throws Exception {
    var tx = start();
    dispatch();
    http.perform(webhook(tx, "8.8.8.8", "196.201.214.200", crypto.callbackToken(tx.id())))
        .andExpect(status().isForbidden());
    http.perform(webhook(tx, "10.0.0.4", "196.201.214.200,8.8.8.8", crypto.callbackToken(tx.id())))
        .andExpect(status().isForbidden());
    http.perform(webhook(tx, "196.201.214.200", null, "incorrect-secret"))
        .andExpect(status().isForbidden());
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("PENDING");
    http.perform(webhook(tx, "10.0.0.4", "196.201.214.200", crypto.callbackToken(tx.id())))
        .andExpect(status().isOk());
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void csrfIsStillRequiredOnInitiationAndAnonymousStatusIsRejected() throws Exception {
    http.perform(
            post("/api/v1/mpesa/stkpush")
                .with(
                    user(operator.email()).authorities(new SimpleGrantedAuthority("admin:manage")))
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(json.writeValueAsBytes(new MpesaDtos.StartRequest(phone, sale))))
        .andExpect(status().isForbidden());
    http.perform(get("/api/v1/mpesa/transactions")).andExpect(status().isUnauthorized());
    http.perform(
            get("/api/v1/mpesa/config")
                .with(user("someone").authorities(new SimpleGrantedAuthority("sales:view"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantAndBranchIsolationProtectPaymentStatus() {
    var tx = start();
    var foreign =
        new AuthenticatedUser(
            userId,
            operator.email(),
            "Foreign",
            UUID.randomUUID(),
            Set.of(),
            Set.of("ADMIN"),
            Set.of(),
            Set.of("admin:manage"));
    assertThatThrownBy(() -> ledger.get(foreign, tx.id()))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    var wrongBranch =
        new AuthenticatedUser(
            userId,
            operator.email(),
            "Operator",
            org,
            Set.of(UUID.randomUUID()),
            Set.of("ADMIN"),
            Set.of(),
            Set.of("admin:manage"));
    assertThatThrownBy(() -> ledger.get(wrongBranch, tx.id()))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
  }

  @Test
  void unverifiedAndChangedCheckoutsCannotCompleteASale() {
    var tx = start();
    assertThatThrownBy(() -> sales.createSale(operator, key, withReceipt()))
        .isInstanceOf(ResponseStatusException.class);
    dispatch();
    confirm(tx);
    var changed =
        new SaleRequest(
            branch,
            "Different customer",
            null,
            null,
            null,
            BigDecimal.ZERO,
            withReceipt().payments(),
            sale.lines());
    assertThatThrownBy(() -> sales.createSale(operator, key, changed))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(
            jdbc.queryForObject(
                "select quantity_on_hand from inventory_items where branch_id=? and product_id=?",
                Integer.class,
                branch,
                product))
        .isEqualTo(10);
    assertThat(sales.createSale(operator, key, withReceipt()).payments().get(0).reference())
        .isEqualTo(receipt());
  }

  @Test
  void unknownCallbackCodeCannotReleaseThePhoneForAnotherCharge() {
    var tx = start();
    dispatch();
    var pending = callback(4999, 0);
    ledger.callback(tx.id(), pending, MpesaCrypto.sha256(pending.toString()));
    assertThat(ledger.get(operator, tx.id()).status()).isEqualTo("REVIEW");
    assertThatThrownBy(
            () ->
                payments.start(
                    operator,
                    UUID.randomUUID().toString(),
                    new MpesaDtos.StartRequest(phone, sale)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void aVerifiedSplitPaymentSettlesOnlyItsOriginalTenderPlan() {
    sale =
        new SaleRequest(
            branch,
            "Walk-in",
            null,
            null,
            null,
            BigDecimal.ZERO,
            List.of(
                new SalePaymentRequest(PaymentMethod.MPESA, BigDecimal.valueOf(60), "", null),
                new SalePaymentRequest(
                    PaymentMethod.CASH, BigDecimal.valueOf(40), "", BigDecimal.valueOf(40))),
            sale.lines());
    var tx = start();
    assertThat(tx.amount()).isEqualByComparingTo("60");
    dispatch();
    var callback = callback(0, 60);
    ledger.callback(tx.id(), callback, MpesaCrypto.sha256(callback.toString()));
    var finalRequest =
        new SaleRequest(
            branch,
            "Walk-in",
            null,
            null,
            null,
            BigDecimal.ZERO,
            List.of(
                new SalePaymentRequest(
                    PaymentMethod.MPESA, BigDecimal.valueOf(60), receipt(), null),
                sale.payments().get(1)),
            sale.lines());
    assertThat(sales.createSale(operator, key, finalRequest).payments()).hasSize(2);
  }

  @Test
  void offlineHttpEnvelopeRecordsManualMpesaOnceEvenWhenStkIsEnabled() throws Exception {
    var operation =
        new com.company.fashionpos.offline.OfflineOperationService.Request(
            UUID.randomUUID(),
            userId,
            Instant.now(),
            "SALE",
            json.valueToTree(
                new SaleService.DeviceSale(
                    sale,
                    key,
                    List.of(
                        new SaleService.DevicePrice(
                            product, BigDecimal.valueOf(100), VatCategory.G)),
                    true)),
            List.of());
    for (int retry = 0; retry < 2; retry++)
      http.perform(
              post("/api/v1/offline/operations")
                  .with(
                      user(operator.email())
                          .authorities(new SimpleGrantedAuthority("admin:manage")))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsBytes(operation)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(operation.id().toString()))
          .andExpect(jsonPath("$.result.payments[0].method").value("MPESA"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales where organization_id=? and idempotency_key=?",
                Integer.class,
                org,
                key))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select quantity_on_hand from inventory_items where branch_id=? and product_id=?",
                Integer.class,
                branch,
                product))
        .isEqualTo(9);
    verify(daraja, never()).push(any());
  }

  @Test
  void aPendingStkCheckoutCannotBeRecordedAsManualMpesaThroughOfflineSync() throws Exception {
    start();
    var operation =
        new com.company.fashionpos.offline.OfflineOperationService.Request(
            UUID.randomUUID(),
            userId,
            Instant.now(),
            "SALE",
            json.valueToTree(
                new SaleService.DeviceSale(
                    sale,
                    key,
                    List.of(
                        new SaleService.DevicePrice(
                            product, BigDecimal.valueOf(100), VatCategory.G)),
                    true)),
            List.of());
    http.perform(
            post("/api/v1/offline/operations")
                .with(
                    user(operator.email()).authorities(new SimpleGrantedAuthority("admin:manage")))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(operation)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales where organization_id=? and idempotency_key=?",
                Integer.class,
                org,
                key))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select quantity_on_hand from inventory_items where branch_id=? and product_id=?",
                Integer.class,
                branch,
                product))
        .isEqualTo(10);
  }

  @Test
  void stockReceivingStaffCanPrepareSupplierNamesWithoutPrivateSupplierDetails() throws Exception {
    UUID receiverRole = UUID.randomUUID();
    jdbc.update(
        "insert into roles(id,organization_id,name,role_key) values (?,?,? ,?)",
        receiverRole,
        org,
        "Receiver " + receiverRole,
        "RECEIVER_" + receiverRole.toString().substring(0, 8));
    jdbc.update(
        "insert into role_permissions(role_id,permission_id) select ?,id from permissions where code='inventory:receive'",
        receiverRole);
    jdbc.update("delete from user_roles where user_id=?", userId);
    jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", userId, receiverRole);
    jdbc.update(
        "insert into suppliers(id,organization_id,name,company_name,status,bank_details) values (?,?,?,'Supplier Company','ACTIVE','private-test-bank-details')",
        UUID.randomUUID(),
        org,
        "Stock Supplier");
    http.perform(
            get("/api/v1/offline/operations/suppliers")
                .with(
                    user(operator.email())
                        .authorities(new SimpleGrantedAuthority("inventory:receive"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].bankDetails").doesNotExist())
        .andExpect(jsonPath("$[0].bank_details").doesNotExist());
    http.perform(
            get("/api/v1/suppliers")
                .with(
                    user(operator.email())
                        .authorities(new SimpleGrantedAuthority("inventory:receive"))))
        .andExpect(status().isForbidden());
  }
}
