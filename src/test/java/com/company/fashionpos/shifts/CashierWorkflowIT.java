package com.company.fashionpos.shifts;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.company.fashionpos.catalog.*;
import com.company.fashionpos.expenses.*;
import com.company.fashionpos.inventory.*;
import com.company.fashionpos.sales.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.staff.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false",
      "app.security.admin-email=cashier-test@example.test",
      "app.security.admin-password=test-password-only"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class CashierWorkflowIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    r.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    r.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired StaffService staff;
  @Autowired CashierShiftService shifts;
  @Autowired ExpenseService expenses;
  @Autowired SaleService sales;
  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired ObjectMapper json;
  @Autowired MockMvc http;
  UUID org, branch, otherBranch, cashierRole, product;
  AuthenticatedUser admin, cashier;
  static final Set<String> PERMISSIONS = Set.of("pos:sell", "expenses:create", "shifts:manage");

  @BeforeEach
  void fixture() {
    org = UUID.randomUUID();
    branch = UUID.randomUUID();
    otherBranch = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Shop Business','KES','Africa/Nairobi','ACTIVE')",
        org);
    UUID owner = UUID.randomUUID();
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Owner','ACTIVE')",
        owner,
        org,
        owner + "@test.local");
    admin =
        new AuthenticatedUser(
            owner,
            owner + "@test.local",
            "Owner",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    for (UUID id : List.of(branch, otherBranch))
      jdbc.update(
          "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,?,?,'Africa/Nairobi','ACTIVE')",
          id,
          org,
          "Shop " + id,
          id.toString().substring(0, 12));
    var role =
        staff.listRoles(admin).stream()
            .filter(r -> r.key().equals("CASHIER"))
            .findFirst()
            .orElseThrow();
    cashierRole = role.id();
    assertThat(role.permissionCodes()).containsExactlyInAnyOrderElementsOf(PERMISSIONS);
    cashier = operator();
    var category =
        catalog.createCategory(
            admin, new ProductCategoryRequest("Food", "FOOD", CatalogStatus.ACTIVE));
    product =
        catalog
            .createProduct(
                admin,
                new ProductRequest(
                    "Bread",
                    "BRD",
                    "123",
                    category.id(),
                    null,
                    null,
                    null,
                    new BigDecimal("200"),
                    BigDecimal.TEN,
                    VatCategory.G,
                    List.of(),
                    List.of(),
                    CatalogStatus.ACTIVE))
            .id();
    inventory.addStock(
        admin, new StockIntakeRequest(branch, product, "Supplier", "REF", 20, BigDecimal.TEN, ""));
  }

  AuthenticatedUser operator() {
    String email = UUID.randomUUID() + "@test.local";
    var response =
        staff.createUser(
            admin,
            new UserRequest(
                email,
                "cashier-password",
                "Shop Cashier",
                UserStatus.ACTIVE,
                Set.of(cashierRole),
                Set.of(branch)));
    return new AuthenticatedUser(
        response.id(),
        email,
        "Shop Cashier",
        org,
        Set.of(branch),
        Set.of("CASHIER"),
        Set.of("Cashier"),
        PERMISSIONS);
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor principal() {
    return user(cashier.email())
        .authorities(PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList());
  }

  ExpenseRequest expense(UUID shop, int amount) {
    return new ExpenseRequest(
        shop,
        "Transport",
        "Shop delivery",
        null,
        BigDecimal.valueOf(amount),
        ExpensePaymentMethod.CASH,
        null,
        ExpenseStatus.PAID,
        "");
  }

  SaleRequest sale() {
    return new SaleRequest(
        branch,
        "Walk-in",
        null,
        null,
        null,
        BigDecimal.ZERO,
        List.of(
            new SalePaymentRequest(
                PaymentMethod.CASH, new BigDecimal("80"), null, new BigDecimal("100")),
            new SalePaymentRequest(PaymentMethod.MPESA, new BigDecimal("120"), "MPESA", null)),
        List.of(new SaleLineRequest(product, 1)));
  }

  @Test
  void cashierCanSellAndRegisterExpensesButCannotAccessManagementOrChangeExpenses()
      throws Exception {
    http.perform(
            post("/api/v1/sales")
                .with(principal())
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(json.writeValueAsString(sale())))
        .andExpect(status().isCreated());
    var response =
        http.perform(
                post("/api/v1/expenses")
                    .with(principal())
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(expense(branch, 20))))
            .andExpect(status().isCreated())
            .andReturn();
    String id = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    expenses.createExpense(operator(), expense(branch, 900));
    http.perform(get("/api/v1/expenses").with(principal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    http.perform(post("/api/v1/expenses/" + id + "/paid").with(principal()).with(csrf()))
        .andExpect(status().isForbidden());
    http.perform(delete("/api/v1/expenses/" + id).with(principal()).with(csrf()))
        .andExpect(status().isForbidden());
    for (String endpoint :
        List.of(
            "roles",
            "permissions",
            "users",
            "sales",
            "accounting/summary",
            "stock-intakes",
            "stock-adjustments",
            "daily-sales-reports",
            "procurement/summary",
            "customers"))
      http.perform(get("/api/v1/" + endpoint).with(principal())).andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/expenses")
                .with(principal())
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(expense(otherBranch, 20))))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/shifts")
                .with(principal())
                .contentType("application/json")
                .content("{\"branchId\":\"" + branch + "\",\"openingFloat\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void shiftEndpointsValidateAmountsAndUseTheAuthenticatedCashier() throws Exception {
    http.perform(
            post("/api/v1/shifts")
                .with(principal())
                .with(csrf())
                .contentType("application/json")
                .content("{\"branchId\":\"" + branch + "\",\"openingFloat\":-1}"))
        .andExpect(status().isBadRequest());
    var response =
        http.perform(
                post("/api/v1/shifts")
                    .with(principal())
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"branchId\":\"" + branch + "\",\"openingFloat\":100}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cashierId").value(cashier.userId().toString()))
            .andReturn();
    String id = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    var colleague = operator();
    http.perform(
            post("/api/v1/shifts/" + id + "/close")
                .with(
                    user(colleague.email())
                        .authorities(
                            PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList()))
                .with(csrf())
                .contentType("application/json")
                .content("{\"closingCash\":100}"))
        .andExpect(status().isNotFound());
    http.perform(
            post("/api/v1/shifts/" + id + "/close")
                .with(principal())
                .with(csrf())
                .contentType("application/json")
                .content("{\"closingCash\":-1}"))
        .andExpect(status().isBadRequest());
    http.perform(
            post("/api/v1/shifts/" + id + "/close")
                .with(principal())
                .with(csrf())
                .contentType("application/json")
                .content("{\"closingCash\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.variance").value(0));
  }

  @Test
  void ownShiftPersistsAndClosesWithAccurateCashTotalsAndAnImmutableSnapshot() throws Exception {
    sales.createSale(cashier, UUID.randomUUID().toString(), sale()); // outside the shift
    var shift =
        shifts.open(
            cashier,
            new CashierShiftController.OpenRequest(branch, new BigDecimal("500"), "Opening"));
    sales.createSale(cashier, UUID.randomUUID().toString(), sale());
    expenses.createExpense(cashier, expense(branch, 20));
    var colleague = operator();
    sales.createSale(colleague, UUID.randomUUID().toString(), sale());
    expenses.createExpense(colleague, expense(branch, 300));
    var active = shifts.list(cashier).get(0);
    assertThat(active.cashSales()).isEqualByComparingTo("80");
    assertThat(active.totalSales()).isEqualByComparingTo("200");
    assertThat(active.cashExpenses()).isEqualByComparingTo("20");
    assertThat(active.expectedCash()).isEqualByComparingTo("560");
    assertThat(active.transactionCount()).isEqualTo(1);
    http.perform(get("/api/v1/shifts").with(principal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(shift.id().toString()));
    var closed =
        shifts.close(
            cashier,
            shift.id(),
            new CashierShiftController.CloseRequest(new BigDecimal("550"), "Counted"));
    assertThat(closed.variance()).isEqualByComparingTo("-10");
    assertThat(closed.status()).isEqualTo("CLOSED");
    assertThat(
            shifts.close(
                cashier,
                shift.id(),
                new CashierShiftController.CloseRequest(new BigDecimal("550"), "Counted")))
        .isEqualTo(closed);
    sales.createSale(cashier, UUID.randomUUID().toString(), sale());
    assertThat(shifts.list(cashier).get(0)).isEqualTo(closed);
  }

  @Test
  void cashiersCannotOperateAnotherUsersShiftOrAnUnassignedShop() {
    var shift =
        shifts.open(cashier, new CashierShiftController.OpenRequest(branch, BigDecimal.ZERO, ""));
    var colleague = operator();
    assertThat(shifts.list(colleague)).isEmpty();
    assertThatThrownBy(
            () ->
                shifts.close(
                    colleague,
                    shift.id(),
                    new CashierShiftController.CloseRequest(BigDecimal.ZERO, "")))
        .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    assertThatThrownBy(
            () ->
                shifts.open(
                    colleague,
                    new CashierShiftController.OpenRequest(otherBranch, BigDecimal.ZERO, "")))
        .isInstanceOf(AccessDeniedException.class);
    var noShop =
        new AuthenticatedUser(
            cashier.userId(),
            cashier.email(),
            cashier.displayName(),
            org,
            Set.of(),
            Set.of("CASHIER"),
            Set.of("Cashier"),
            PERMISSIONS);
    assertThat(shifts.list(noShop)).isEmpty();
    assertThatThrownBy(
            () ->
                shifts.open(
                    noShop, new CashierShiftController.OpenRequest(branch, BigDecimal.ZERO, "")))
        .isInstanceOf(AccessDeniedException.class);
    var foreign =
        new AuthenticatedUser(
            cashier.userId(),
            cashier.email(),
            cashier.displayName(),
            UUID.randomUUID(),
            Set.of(branch),
            Set.of("CASHIER"),
            Set.of("Cashier"),
            PERMISSIONS);
    assertThat(shifts.list(foreign)).isEmpty();
    assertThatThrownBy(
            () ->
                shifts.close(
                    foreign,
                    shift.id(),
                    new CashierShiftController.CloseRequest(BigDecimal.ZERO, "")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void cashierProfileCannotGainReportsOrBeAssignedWithoutAShopOrWithAnotherRole() {
    assertThatThrownBy(
            () ->
                staff.createUser(
                    admin,
                    new UserRequest(
                        UUID.randomUUID() + "@test.local",
                        "password-123",
                        "Unassigned",
                        UserStatus.ACTIVE,
                        Set.of(cashierRole),
                        Set.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one shop");
    UUID manager = UUID.randomUUID();
    jdbc.update(
        "insert into roles(id,organization_id,name,role_key) values (?,?,'Shop Manager','SHOP_MANAGER')",
        manager,
        org);
    assertThatThrownBy(
            () ->
                staff.createUser(
                    admin,
                    new UserRequest(
                        UUID.randomUUID() + "@test.local",
                        "password-123",
                        "Mixed",
                        UserStatus.ACTIVE,
                        Set.of(cashierRole, manager),
                        Set.of(branch))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("assigned alone");
    var ids =
        jdbc.queryForList(
            "select id from permissions where code in ('pos:sell','expenses:create','reports:view')",
            UUID.class);
    assertThatThrownBy(
            () ->
                staff.updateRole(
                    admin, cashierRole, new RoleRequest("Cashier", "CASHIER", "", Set.copyOf(ids))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limited to");
  }

  @Test
  void concurrentOpenRequestsCreateOnlyOneShift() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    Callable<Boolean> open =
        () -> {
          start.await();
          try {
            shifts.open(
                cashier, new CashierShiftController.OpenRequest(branch, BigDecimal.ZERO, ""));
            return true;
          } catch (IllegalArgumentException e) {
            return false;
          }
        };
    try {
      var first = pool.submit(open);
      var second = pool.submit(open);
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(shifts.list(cashier)).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
