package com.company.fashionpos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BackOfficeWorkflowIT {

  private static final String ADMIN_EMAIL = "admin@test.local";
  private static final String ADMIN_PASSWORD = "test-admin-password";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("fashion_pos_workflow_test")
          .withUsername("fashion_pos_app")
          .withPassword("test-password");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  private MockHttpSession session;
  private CsrfExchange csrf;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.security.admin-email", () -> ADMIN_EMAIL);
    registry.add("app.security.admin-password", () -> ADMIN_PASSWORD);
    registry.add("app.security.manager-email", () -> "");
    registry.add("app.security.manager-password", () -> "");
    registry.add("app.security.cors.allowed-origin", () -> "http://localhost:5173");
    registry.add("app.bootstrap.organization-name", () -> "KEEN Fashions");
    registry.add("app.bootstrap.currency-code", () -> "KES");
    registry.add("app.bootstrap.time-zone", () -> "Africa/Nairobi");
    registry.add("app.bootstrap.admin-display-name", () -> "System Admin");
  }

  @BeforeEach
  void login() throws Exception {
    CsrfExchange anonymousCsrf = csrfExchange(null);
    MvcResult loginResult =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()),
                    null,
                    anonymousCsrf))
            .andExpect(status().isOk())
            .andReturn();

    session = (MockHttpSession) loginResult.getRequest().getSession(false);
    csrf = csrfExchange(session);
  }

  @Test
  void registersBackOfficeRecordsAndMovesStock() throws Exception {
    String sourceBranchId = createBranch("Warehouse", "WH", "Africa/Nairobi").get("id").asText();
    String destinationBranchId =
        createBranch("Town Shop", "TOWN", "Africa/Nairobi").get("id").asText();

    JsonNode role = createRole("Workflow Manager");
    JsonNode user =
        postJson(
            "/api/v1/users",
            """
            {
              "email": "manager@test.local",
              "password": "manager-password",
              "displayName": "Shop Manager",
              "status": "ACTIVE",
              "roleIds": ["%s"],
              "branchIds": ["%s"]
            }
            """
                .formatted(role.get("id").asText(), destinationBranchId),
            201);

    assertThat(user.get("roleNames").get(0).asText()).isEqualTo("Workflow Manager");
    assertThat(user.get("branchNames").get(0).asText()).isEqualTo("Town Shop");
    assertThat(user.has("password")).isFalse();
    String managerPasswordHash =
        jdbcTemplate.queryForObject(
            "select password_hash from users where lower(email) = lower(?)",
            String.class,
            "manager@test.local");
    assertThat(managerPasswordHash).isNotEqualTo("manager-password");
    assertThat(passwordEncoder.matches("manager-password", managerPasswordHash)).isTrue();

    String categoryId = createCategory("Shirts", "SHIRTS").get("id").asText();
    JsonNode product = createProduct(categoryId);
    assertThat(product.get("imageUrl").asText()).isEqualTo("https://example.test/oxford-shirt.png");
    String productId = product.get("id").asText();
    JsonNode supplier = createSupplier();
    JsonNode suppliers = getJson("/api/v1/suppliers");
    assertThat(suppliers).hasSize(1);
    assertThat(suppliers.get(0).get("name").asText()).isEqualTo("Keen Supplier");

    postJson(
        "/api/v1/stock-intakes",
        """
        {
          "branchId": "%s",
          "productId": "%s",
          "supplierName": "%s",
          "referenceNumber": "GRN-001",
          "quantity": 20,
          "unitCost": 500
        }
        """
            .formatted(sourceBranchId, productId, supplier.get("name").asText()),
        201);

    postJson(
        "/api/v1/transfers",
        """
        {
          "sourceBranchId": "%s",
          "destinationBranchId": "%s",
          "productId": "%s",
          "quantity": 7,
          "referenceNumber": "TR-001"
        }
        """
            .formatted(sourceBranchId, destinationBranchId, productId),
        201);

    JsonNode sale = createSale(destinationBranchId, productId);
    assertThat(sale.get("saleNumber").asText()).startsWith("SALE-");
    assertThat(sale.get("totalAmount").decimalValue()).isEqualByComparingTo("3248.00");
    assertThat(sale.get("payments")).hasSize(2);
    assertThat(sale.get("payments").get(0).get("method").asText()).isEqualTo("CASH");
    assertThat(sale.get("payments").get(0).get("amount").decimalValue())
        .isEqualByComparingTo("2000.00");
    assertThat(sale.get("payments").get(0).get("cashReceived").decimalValue())
        .isEqualByComparingTo("2500.00");
    assertThat(sale.get("payments").get(0).get("changeDue").decimalValue())
        .isEqualByComparingTo("500.00");
    assertThat(sale.get("payments").get(1).get("method").asText()).isEqualTo("MPESA");
    assertThat(sale.get("payments").get(1).get("amount").decimalValue())
        .isEqualByComparingTo("1248.00");
    assertThat(sale.get("payments").get(1).get("reference").asText()).isEqualTo("MPE-001");

    JsonNode inventory = getJson("/api/v1/inventory");

    assertThat(quantityFor(inventory, sourceBranchId, productId)).isEqualTo(13);
    assertThat(quantityFor(inventory, destinationBranchId, productId)).isEqualTo(6);

    JsonNode lossAdjustment =
        postJson(
            "/api/v1/stock-adjustments",
            """
            {
              "branchId": "%s",
              "productId": "%s",
              "countedQuantity": 11,
              "reason": "Physical count shortage",
              "notes": "Cycle count variance"
            }
            """
                .formatted(sourceBranchId, productId),
            201);
    assertThat(lossAdjustment.get("systemQuantity").asInt()).isEqualTo(13);
    assertThat(lossAdjustment.get("countedQuantity").asInt()).isEqualTo(11);
    assertThat(lossAdjustment.get("varianceQuantity").asInt()).isEqualTo(-2);
    assertThat(lossAdjustment.get("lossValue").decimalValue()).isEqualByComparingTo("2400.00");
    assertThat(lossAdjustment.get("excessValue").decimalValue()).isEqualByComparingTo("0.00");

    JsonNode excessAdjustment =
        postJson(
            "/api/v1/stock-adjustments",
            """
            {
              "branchId": "%s",
              "productId": "%s",
              "countedQuantity": 9,
              "reason": "Physical count overage"
            }
            """
                .formatted(destinationBranchId, productId),
            201);
    assertThat(excessAdjustment.get("systemQuantity").asInt()).isEqualTo(6);
    assertThat(excessAdjustment.get("countedQuantity").asInt()).isEqualTo(9);
    assertThat(excessAdjustment.get("varianceQuantity").asInt()).isEqualTo(3);
    assertThat(excessAdjustment.get("lossValue").decimalValue()).isEqualByComparingTo("0.00");
    assertThat(excessAdjustment.get("excessValue").decimalValue()).isEqualByComparingTo("3600.00");

    JsonNode adjustments = getJson("/api/v1/stock-adjustments");
    assertThat(adjustments).hasSize(2);
    JsonNode adjustmentSummary = getJson("/api/v1/stock-adjustments/summary");
    assertThat(adjustmentSummary.get("lossItems").asInt()).isEqualTo(1);
    assertThat(adjustmentSummary.get("excessItems").asInt()).isEqualTo(1);
    assertThat(adjustmentSummary.get("totalLossQuantity").asInt()).isEqualTo(2);
    assertThat(adjustmentSummary.get("totalExcessQuantity").asInt()).isEqualTo(3);
    assertThat(adjustmentSummary.get("totalLossValue").decimalValue())
        .isEqualByComparingTo("2400.00");
    assertThat(adjustmentSummary.get("totalExcessValue").decimalValue())
        .isEqualByComparingTo("3600.00");
    assertThat(adjustmentSummary.get("netValue").decimalValue()).isEqualByComparingTo("1200.00");

    inventory = getJson("/api/v1/inventory");
    assertThat(quantityFor(inventory, sourceBranchId, productId)).isEqualTo(11);
    assertThat(quantityFor(inventory, destinationBranchId, productId)).isEqualTo(9);

    JsonNode sourceInventoryItem = inventoryItemFor(inventory, sourceBranchId, productId);
    JsonNode reorderedInventoryItem =
        putJson(
            "/api/v1/inventory/%s/reorder-level".formatted(sourceInventoryItem.get("id").asText()),
            """
            {
              "reorderLevel": 15
            }
            """,
            200);
    assertThat(reorderedInventoryItem.get("reorderLevel").asInt()).isEqualTo(15);
    assertThat(reorderedInventoryItem.get("stockHealth").asText()).isEqualTo("LOW_STOCK");

    JsonNode sales = getJson("/api/v1/sales");
    assertThat(sales).hasSize(1);
    assertThat(sales.get(0).get("saleNumber").asText()).isEqualTo(sale.get("saleNumber").asText());

    JsonNode expense = createExpense(destinationBranchId);
    assertThat(expense.get("expenseNumber").asText()).startsWith("EXP-");
    assertThat(expense.get("amount").decimalValue()).isEqualByComparingTo("1500.00");
    assertThat(expense.get("status").asText()).isEqualTo("PENDING");

    JsonNode paidExpense =
        postWithoutBody("/api/v1/expenses/%s/paid".formatted(expense.get("id").asText()));
    assertThat(paidExpense.get("status").asText()).isEqualTo("PAID");

    JsonNode expenses = getJson("/api/v1/expenses");
    assertThat(expenses).hasSize(1);
    assertThat(expenses.get(0).get("expenseNumber").asText())
        .isEqualTo(expense.get("expenseNumber").asText());

    JsonNode voidedExpense =
        deleteJson("/api/v1/expenses/%s".formatted(expense.get("id").asText()));
    assertThat(voidedExpense.get("status").asText()).isEqualTo("VOID");
  }

  @Test
  void generatesProductCodesWhenMissingAndPreservesManualCodes() throws Exception {
    String categoryId = createCategory("Generated Codes", "GENCODES").get("id").asText();

    JsonNode generatedProduct =
        postJson(
            "/api/v1/catalog/products",
            """
            {
              "name": "Auto Code Shirt",
              "categoryId": "%s",
              "unitPrice": 1200,
              "costPrice": 700,
              "status": "ACTIVE"
            }
            """
                .formatted(categoryId),
            201);

    assertThat(generatedProduct.get("sku").asText()).startsWith("SKU-");
    assertThat(generatedProduct.get("barcode").asText()).matches("\\d{13}");
    String generatedProductId = generatedProduct.get("id").asText();
    String generatedBarcode = generatedProduct.get("barcode").asText();

    JsonNode updatedProduct =
        putJson(
            "/api/v1/catalog/products/%s".formatted(generatedProductId),
            """
            {
              "name": "Auto Code Shirt",
              "sku": "%s",
              "barcode": "",
              "categoryId": "%s",
              "unitPrice": 1200,
              "costPrice": 700,
              "status": "ACTIVE"
            }
            """
                .formatted(generatedProduct.get("sku").asText(), categoryId),
            200);

    assertThat(updatedProduct.get("barcode").asText()).isEqualTo(generatedBarcode);

    JsonNode manualProduct =
        postJson(
            "/api/v1/catalog/products",
            """
            {
              "name": "Manual Code Shirt",
              "sku": "manual-001",
              "barcode": "BAR-001",
              "categoryId": "%s",
              "unitPrice": 1500,
              "costPrice": 900,
              "status": "ACTIVE"
            }
            """
                .formatted(categoryId),
            201);

    assertThat(manualProduct.get("sku").asText()).isEqualTo("MANUAL-001");
    assertThat(manualProduct.get("barcode").asText()).isEqualTo("BAR-001");
  }

  @Test
  void assignsRolePermissionsAndReturnsUserEffectivePermissions() throws Exception {
    JsonNode permissions = getJson("/api/v1/permissions");
    String posSellPermissionId = permissionIdFor(permissions, "pos:sell");
    String reportsViewPermissionId = permissionIdFor(permissions, "reports:view");

    JsonNode role =
        postJson(
            "/api/v1/roles",
            """
            {
              "name": "Checkout Lead",
              "key": "checkout_lead",
              "description": "Front of house supervision",
              "permissionIds": ["%s", "%s"]
            }
            """
                .formatted(posSellPermissionId, reportsViewPermissionId),
            201);

    assertThat(role.get("key").asText()).isEqualTo("CHECKOUT_LEAD");
    assertThat(textValues(role.get("permissionCodes"))).containsExactly("pos:sell", "reports:view");

    String branchId =
        createBranch("Permission Branch", "PERM", "Africa/Nairobi").get("id").asText();
    JsonNode staffUser =
        postJson(
            "/api/v1/users",
            """
            {
              "email": "checkout-lead@test.local",
              "password": "checkout-lead-password",
              "displayName": "Checkout Lead",
              "status": "ACTIVE",
              "roleIds": ["%s"],
              "branchIds": ["%s"]
            }
            """
                .formatted(role.get("id").asText(), branchId),
            201);

    assertThat(textValues(staffUser.get("permissionCodes")))
        .containsExactly("pos:sell", "reports:view");
  }

  private JsonNode createBranch(String name, String code, String timeZone) throws Exception {
    return postJson(
        "/api/v1/branches",
        """
        {
          "name": "%s",
          "code": "%s",
          "timeZone": "%s",
          "status": "ACTIVE"
        }
        """
            .formatted(name, code, timeZone),
        201);
  }

  private JsonNode createRole(String name) throws Exception {
    return postJson(
        "/api/v1/roles",
        """
        {
          "name": "%s",
          "description": "Assigned branch operations"
        }
        """
            .formatted(name),
        201);
  }

  private JsonNode createCategory(String name, String code) throws Exception {
    return postJson(
        "/api/v1/catalog/categories",
        """
        {
          "name": "%s",
          "code": "%s",
          "status": "ACTIVE"
        }
        """
            .formatted(name, code),
        201);
  }

  private JsonNode createProduct(String categoryId) throws Exception {
    return postJson(
        "/api/v1/catalog/products",
        """
        {
          "name": "Oxford Shirt",
          "sku": "OXF-001",
          "categoryId": "%s",
          "department": "Menswear",
          "imageUrl": "https://example.test/oxford-shirt.png",
          "unitPrice": 2800,
          "costPrice": 1200,
          "sizes": ["M", "L"],
          "colors": ["Blue"],
          "status": "ACTIVE"
        }
        """
            .formatted(categoryId),
        201);
  }

  private JsonNode createSupplier() throws Exception {
    return postJson(
        "/api/v1/suppliers",
        """
        {
          "name": "Keen Supplier",
          "contactPerson": "Jane Supplier",
          "phone": "+254700000001",
          "email": "supplier@test.local",
          "notes": "Main stock supplier",
          "status": "ACTIVE"
        }
        """,
        201);
  }

  private JsonNode createSale(String branchId, String productId) throws Exception {
    return postJson(
        "/api/v1/sales",
        """
        {
          "branchId": "%s",
          "discountAmount": 0,
          "payments": [
            {
              "method": "CASH",
              "amount": 2000,
              "cashReceived": 2500
            },
            {
              "method": "MPESA",
              "amount": 1248,
              "paymentReference": "MPE-001"
            }
          ],
          "lines": [
            {
              "productId": "%s",
              "quantity": 1
            }
          ]
        }
        """
            .formatted(branchId, productId),
        "workflow-sale-001",
        201);
  }

  private JsonNode createExpense(String branchId) throws Exception {
    return postJson(
        "/api/v1/expenses",
        """
        {
          "branchId": "%s",
          "category": "Utilities",
          "description": "Electricity bill",
          "vendorName": "Kenya Power",
          "amount": 1500,
          "paymentMethod": "MPESA",
          "paymentReference": "MPE-001",
          "status": "PENDING",
          "notes": "Monthly branch bill"
        }
        """
            .formatted(branchId),
        201);
  }

  private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
    return postJson(path, body, null, expectedStatus);
  }

  private JsonNode postJson(String path, String body, String idempotencyKey, int expectedStatus)
      throws Exception {
    var requestBuilder =
        withSessionAndCsrf(
            post(path).contentType(MediaType.APPLICATION_JSON).content(body), session, csrf);
    if (idempotencyKey != null) {
      requestBuilder.header("Idempotency-Key", idempotencyKey);
    }

    String content =
        mockMvc
            .perform(requestBuilder)
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(content);
  }

  private JsonNode getJson(String path) throws Exception {
    String content =
        mockMvc
            .perform(withSession(get(path), session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(content);
  }

  private JsonNode putJson(String path, String body, int expectedStatus) throws Exception {
    String content =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    put(path).contentType(MediaType.APPLICATION_JSON).content(body), session, csrf))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(content);
  }

  private JsonNode postWithoutBody(String path) throws Exception {
    String content =
        mockMvc
            .perform(withSessionAndCsrf(post(path), session, csrf))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(content);
  }

  private JsonNode deleteJson(String path) throws Exception {
    String content =
        mockMvc
            .perform(withSessionAndCsrf(delete(path), session, csrf))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(content);
  }

  private CsrfExchange csrfExchange(MockHttpSession session) throws Exception {
    MvcResult result =
        mockMvc
            .perform(withSession(get("/api/v1/auth/csrf"), session))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfExchange(body.get("headerName").asText(), body.get("token").asText(), cookie);
  }

  private static MockHttpServletRequestBuilder withSession(
      MockHttpServletRequestBuilder requestBuilder, MockHttpSession session) {
    if (session == null) {
      return requestBuilder;
    }
    return requestBuilder.session(session);
  }

  private static MockHttpServletRequestBuilder withSessionAndCsrf(
      MockHttpServletRequestBuilder requestBuilder, MockHttpSession session, CsrfExchange csrf) {
    return withSession(requestBuilder, session)
        .cookie(csrf.cookie())
        .header(csrf.headerName(), csrf.token());
  }

  private String loginBody() {
    return """
        {
          "username": "%s",
          "password": "%s",
          "rememberDevice": false
        }
        """
        .formatted(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  private static int quantityFor(JsonNode inventory, String branchId, String productId) {
    return inventoryItemFor(inventory, branchId, productId).get("quantityOnHand").asInt();
  }

  private static JsonNode inventoryItemFor(JsonNode inventory, String branchId, String productId) {
    for (JsonNode item : inventory) {
      if (item.get("branchId").asText().equals(branchId)
          && item.get("productId").asText().equals(productId)) {
        return item;
      }
    }
    throw new AssertionError("Inventory row not found");
  }

  private static String permissionIdFor(JsonNode permissions, String code) {
    for (JsonNode permission : permissions) {
      if (permission.get("code").asText().equals(code)) {
        return permission.get("id").asText();
      }
    }
    throw new AssertionError("Permission not found: " + code);
  }

  private static List<String> textValues(JsonNode values) {
    List<String> text = new ArrayList<>();
    values.forEach(value -> text.add(value.asText()));
    return text;
  }

  private record CsrfExchange(String headerName, String token, Cookie cookie) {}
}
