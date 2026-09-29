package com.company.fashionpos.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
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
class HrPhase1WorkflowIT {

  private static final String ADMIN_EMAIL = "admin@test.local";
  private static final String ADMIN_PASSWORD = "test-admin-password";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("fashion_pos_hr_test")
          .withUsername("fashion_pos_app")
          .withPassword("test-password");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

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
    loginAs(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  private void loginAs(String username, String password) throws Exception {
    CsrfExchange anonymousCsrf = csrfExchange(null);
    MvcResult loginResult =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)),
                    null,
                    anonymousCsrf))
            .andExpect(status().isOk())
            .andReturn();

    session = (MockHttpSession) loginResult.getRequest().getSession(false);
    csrf = csrfExchange(session);
  }

  @Test
  void createsDepartmentsJobTitlesAndEmployees() throws Exception {
    String branchId = createBranch("HR Branch", "HRB").get("id").asText();

    JsonNode department =
        postJson(
            "/api/v1/hr/departments",
            """
            {
              "name": "Human Resources",
              "code": "HR",
              "branchId": "%s",
              "description": "People operations",
              "active": true
            }
            """
                .formatted(branchId),
            201);

    JsonNode jobTitle =
        postJson(
            "/api/v1/hr/job-titles",
            """
            {
              "title": "HR Officer",
              "code": "HR_OFFICER",
              "departmentId": "%s",
              "description": "Handles employee records",
              "active": true
            }
            """
                .formatted(department.get("id").asText()),
            201);

    JsonNode employee =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-001",
              "firstName": "Jane",
              "lastName": "Doe",
              "email": "jane@keen.local",
              "phone": "0712345678",
              "primaryBranchId": "%s",
              "departmentId": "%s",
              "jobTitleId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-01-10",
              "workLocation": "Head office",
              "active": true
            }
            """
                .formatted(branchId, department.get("id").asText(), jobTitle.get("id").asText()),
            201);

    assertThat(employee.get("employeeNumber").asText()).isEqualTo("EMP-001");
    assertThat(employee.get("departmentName").asText()).isEqualTo("Human Resources");

    JsonNode employees = getJson("/api/v1/hr/employees");
    assertThat(employees.get("total").asInt()).isEqualTo(1);
    assertThat(employees.get("items").get(0).get("jobTitleTitle").asText()).isEqualTo("HR Officer");

    JsonNode profile = getJson("/api/v1/hr/employees/%s".formatted(employee.get("id").asText()));
    assertThat(profile.get("tabs")).hasSize(8);
  }

  @Test
  void uploadsListsAndStreamsEmployeeDocuments() throws Exception {
    String branchId = createBranch("Document Branch", "DOC").get("id").asText();
    JsonNode employee =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-DOC-1",
              "firstName": "Diana",
              "lastName": "Records",
              "primaryBranchId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-08-01",
              "active": true
            }
            """
                .formatted(branchId),
            201);

    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "signed-contract.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "contract bytes".getBytes(StandardCharsets.UTF_8));
    MvcResult uploadResult =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    multipart("/api/v1/hr/documents")
                        .file(file)
                        .param("employeeId", employee.get("id").asText())
                        .param("documentType", "Employment Contract")
                        .param("title", "Signed employment contract")
                        .param("notes", "Original scan"),
                    session,
                    csrf))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());

    assertThat(uploaded.get("employeeId").asText()).isEqualTo(employee.get("id").asText());
    assertThat(uploaded.get("fileName").asText()).isEqualTo("signed-contract.pdf");
    assertThat(uploaded.get("documentType").asText()).isEqualTo("Employment Contract");

    JsonNode documents =
        getJson("/api/v1/hr/documents?employeeId=%s".formatted(employee.get("id").asText()));
    assertThat(documents).hasSize(1);
    assertThat(documents.get(0).get("title").asText()).isEqualTo("Signed employment contract");

    MvcResult contentResult =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    get("/api/v1/hr/documents/%s/content".formatted(uploaded.get("id").asText())),
                    session,
                    csrf))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(contentResult.getResponse().getContentType())
        .isEqualTo(MediaType.APPLICATION_PDF_VALUE);
    assertThat(contentResult.getResponse().getContentAsByteArray())
        .isEqualTo("contract bytes".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void rejectsDuplicateEmployeeNumbers() throws Exception {
    String branchId = createBranch("Duplicate Branch", "DUP").get("id").asText();

    postJson(
        "/api/v1/hr/employees",
        """
        {
          "employeeNumber": "EMP-010",
          "firstName": "John",
          "lastName": "Smith",
          "primaryBranchId": "%s",
          "employmentType": "CONTRACT",
          "employmentStatus": "ACTIVE",
          "joiningDate": "2026-02-01",
          "active": true
        }
        """
            .formatted(branchId),
        201);

    MvcResult duplicateResult =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    post("/api/v1/hr/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "employeeNumber": "EMP-010",
                              "firstName": "Janet",
                              "lastName": "Stone",
                              "primaryBranchId": "%s",
                              "employmentType": "CONTRACT",
                              "employmentStatus": "ACTIVE",
                              "joiningDate": "2026-03-01",
                              "active": true
                            }
                            """
                                .formatted(branchId)),
                    session,
                    csrf))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(duplicateResult.getResponse().getContentAsString())
        .contains("Employee number is already used");
  }

  @Test
  void supportsFilteringAndUpdatingEmployees() throws Exception {
    String branchA = createBranch("Alpha Branch", "ALP").get("id").asText();
    String branchB = createBranch("Beta Branch", "BET").get("id").asText();

    JsonNode alpha =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-100",
              "firstName": "Alpha",
              "lastName": "One",
              "primaryBranchId": "%s",
              "employmentType": "INTERN",
              "employmentStatus": "PROBATION",
              "joiningDate": "2026-04-01",
              "active": true
            }
            """
                .formatted(branchA),
            201);

    postJson(
        "/api/v1/hr/employees",
        """
        {
          "employeeNumber": "EMP-200",
          "firstName": "Beta",
          "lastName": "Two",
          "primaryBranchId": "%s",
          "employmentType": "TEMPORARY",
          "employmentStatus": "ACTIVE",
          "joiningDate": "2026-04-02",
          "active": true
        }
        """
            .formatted(branchB),
        201);

    JsonNode filtered =
        getJson("/api/v1/hr/employees?branchId=%s&employmentStatus=PROBATION".formatted(branchA));
    assertThat(filtered.get("total").asInt()).isEqualTo(1);
    assertThat(filtered.get("items").get(0).get("employeeNumber").asText()).isEqualTo("EMP-100");

    JsonNode updated =
        putJson(
            "/api/v1/hr/employees/%s".formatted(alpha.get("id").asText()),
            """
            {
              "employeeNumber": "EMP-100",
              "firstName": "Alpha",
              "lastName": "One",
              "preferredName": "A1",
              "primaryBranchId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-04-01",
              "active": true
            }
            """
                .formatted(branchA),
            200);

    assertThat(updated.get("preferredName").asText()).isEqualTo("A1");
    assertThat(updated.get("employmentType").asText()).isEqualTo("PERMANENT");
  }

  @Test
  void createsAndCorrectsAttendanceRecords() throws Exception {
    String branchId = createBranch("Attendance Branch", "ATD").get("id").asText();
    JsonNode employee =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-ATT-1",
              "firstName": "Amina",
              "lastName": "Noor",
              "primaryBranchId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-05-01",
              "active": true
            }
            """
                .formatted(branchId),
            201);

    JsonNode attendance =
        postJson(
            "/api/v1/hr/attendance",
            """
            {
              "employeeId": "%s",
              "branchId": "%s",
              "attendanceDate": "2026-05-11",
              "clockIn": "2026-05-11T08:00:00+03:00",
              "clockOut": "2026-05-11T17:15:00+03:00",
              "status": "PRESENT",
              "notes": "Normal shift"
            }
            """
                .formatted(employee.get("id").asText(), branchId),
            201);

    assertThat(attendance.get("workedMinutes").asInt()).isEqualTo(555);
    assertThat(attendance.get("overtimeMinutes").asInt()).isEqualTo(75);

    JsonNode listing = getJson("/api/v1/hr/attendance?branchId=%s".formatted(branchId));
    assertThat(listing.get("total").asInt()).isEqualTo(1);
    assertThat(listing.get("summary").get("presentCount").asInt()).isEqualTo(1);

    JsonNode corrected =
        putJson(
            "/api/v1/hr/attendance/%s".formatted(attendance.get("id").asText()),
            """
            {
              "employeeId": "%s",
              "branchId": "%s",
              "attendanceDate": "2026-05-11",
              "clockIn": "2026-05-11T08:20:00+03:00",
              "clockOut": "2026-05-11T17:00:00+03:00",
              "status": "LATE",
              "notes": "Traffic delay",
              "correctionReason": "Manual correction approved"
            }
            """
                .formatted(employee.get("id").asText(), branchId),
            200);

    assertThat(corrected.get("status").asText()).isEqualTo("LATE");
    assertThat(corrected.get("corrected").asBoolean()).isTrue();
    assertThat(corrected.get("lateMinutes").asInt()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void createsLeaveTypesAndApprovesLeaveRequests() throws Exception {
    String branchId = createBranch("Leave Branch", "LEV").get("id").asText();
    JsonNode employee =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-LEV-1",
              "firstName": "Mary",
              "lastName": "Wanjiku",
              "primaryBranchId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-06-01",
              "active": true
            }
            """
                .formatted(branchId),
            201);

    JsonNode leaveType =
        postJson(
            "/api/v1/hr/leave/types",
            """
            {
              "name": "Annual Leave",
              "code": "ANNUAL",
              "description": "Annual entitlement",
              "requiresBalance": true,
              "defaultDays": 21,
              "active": true
            }
            """,
            201);

    JsonNode leaveRequest =
        postJson(
            "/api/v1/hr/leave/requests",
            """
            {
              "employeeId": "%s",
              "leaveTypeId": "%s",
              "startDate": "2026-06-10",
              "endDate": "2026-06-12",
              "reason": "Family travel",
              "status": "APPROVED",
              "approverUserId": "00000000-0000-4000-8000-000000000101",
              "approverComments": "Approved"
            }
            """
                .formatted(employee.get("id").asText(), leaveType.get("id").asText()),
            201);

    assertThat(leaveRequest.get("status").asText()).isEqualTo("APPROVED");
    assertThat(leaveRequest.get("requestedDays").decimalValue()).isEqualByComparingTo("3.00");

    JsonNode leavePage = getJson("/api/v1/hr/leave");
    assertThat(leavePage.get("leaveTypes")).hasSize(1);
    assertThat(leavePage.get("requests")).hasSize(1);
    assertThat(leavePage.get("balances").get(0).get("availableDays").decimalValue())
        .isEqualByComparingTo("18.00");
  }

  @Test
  void autoPopulatesPayrollFromAttributedSalesAndAllowsAdjustments() throws Exception {
    String branchId = createBranch("Payroll Sales Branch", "PSB").get("id").asText();
    JsonNode employee =
        postJson(
            "/api/v1/hr/employees",
            """
            {
              "employeeNumber": "EMP-PAY-1",
              "firstName": "Grace",
              "lastName": "Sales",
              "primaryBranchId": "%s",
              "employmentType": "PERMANENT",
              "employmentStatus": "ACTIVE",
              "joiningDate": "2026-07-01",
              "basicSalary": 5000,
              "salaryPaymentMethod": "MPESA",
              "mpesaNumber": "0711111111",
              "active": true
            }
            """
                .formatted(branchId),
            201);
    JsonNode category =
        postJson(
            "/api/v1/catalog/categories",
            """
            {
              "name": "Payroll Test Apparel",
              "code": "PAYROLL_TEST_APPAREL",
              "status": "ACTIVE"
            }
            """,
            201);
    JsonNode product =
        postJson(
            "/api/v1/catalog/products",
            """
            {
              "name": "Payroll Test Dress",
              "sku": "PAYROLL-DRESS-001",
              "barcode": "PAYROLL-DRESS-001",
              "categoryId": "%s",
              "department": "Women",
              "unitPrice": 500,
              "costPrice": 300,
              "vatCategory": "G",
              "sizes": ["M"],
              "colors": ["Black"],
              "status": "ACTIVE"
            }
            """
                .formatted(category.get("id").asText()),
            201);
    postJson(
        "/api/v1/stock-intakes",
        """
        {
          "branchId": "%s",
          "productId": "%s",
          "supplierName": "Payroll Test Supplier",
          "referenceNumber": "PAY-STOCK-1",
          "quantity": 5,
          "unitCost": 300,
          "notes": "Test stock"
        }
        """
            .formatted(branchId, product.get("id").asText()),
        201);

    JsonNode sale =
        postJsonWithIdempotency(
            "/api/v1/sales",
            "payroll-sales-test-1",
            """
            {
              "branchId": "%s",
              "soldByEmployeeId": "%s",
              "customerName": "Walk-in",
              "paymentMethod": "CASH",
              "cashReceived": 1000,
              "discountAmount": 0,
              "lines": [
                {
                  "productId": "%s",
                  "quantity": 2
                }
              ]
            }
            """
                .formatted(branchId, employee.get("id").asText(), product.get("id").asText()),
            201);

    assertThat(sale.get("soldByEmployeeId").asText()).isEqualTo(employee.get("id").asText());
    assertThat(sale.get("totalAmount").decimalValue()).isEqualByComparingTo("1000.00");

    postJson(
        "/api/v1/hr/performance/entries",
        """
        {
          "employeeId": "%s",
          "entryType": "BONUS",
          "entryDate": "2026-09-10",
          "title": "Sales target bonus",
          "amount": 300,
          "notes": "Exceeded target"
        }
        """
            .formatted(employee.get("id").asText()),
        201);
    postJson(
        "/api/v1/hr/performance/entries",
        """
        {
          "employeeId": "%s",
          "entryType": "LOSS",
          "entryDate": "2026-09-11",
          "title": "Damaged stock recovery",
          "amount": 100,
          "notes": "Approved deduction"
        }
        """
            .formatted(employee.get("id").asText()),
        201);

    JsonNode performance =
        getJson(
            "/api/v1/hr/performance?fromDate=2020-01-01&toDate=2030-12-31&branchId=%s"
                .formatted(branchId));
    JsonNode summary = performance.get("summaries").get(0);
    assertThat(summary.get("actualSalesAmount").decimalValue()).isEqualByComparingTo("1000.00");
    assertThat(summary.get("bonusAmount").decimalValue()).isEqualByComparingTo("300.00");
    assertThat(summary.get("lossAmount").decimalValue()).isEqualByComparingTo("100.00");
    assertThat(summary.get("salesDays")).hasSize(1);
    assertThat(summary.get("salesDays").get(0).get("amount").decimalValue())
        .isEqualByComparingTo("1000.00");

    JsonNode period =
        postJson(
            "/api/v1/hr/payroll/periods",
            """
            {
              "branchId": "%s",
              "name": "Payroll Sales Period",
              "periodStart": "2020-01-01",
              "periodEnd": "2030-12-31",
              "paymentDate": "2030-12-31",
              "status": "OPEN"
            }
            """
                .formatted(branchId),
            201);
    JsonNode run =
        postJson(
            "/api/v1/hr/payroll/runs",
            """
            {
              "payrollPeriodId": "%s",
              "name": "Payroll Sales Run",
              "status": "CALCULATED"
            }
            """
                .formatted(period.get("id").asText()),
            201);
    JsonNode payrollEmployee = run.get("employees").get(0);
    assertThat(payrollEmployee.get("employeeId").asText()).isEqualTo(employee.get("id").asText());
    assertThat(payrollEmployee.get("basicSalary").decimalValue()).isEqualByComparingTo("5000.00");
    assertThat(payrollEmployee.get("bonusAmount").decimalValue()).isEqualByComparingTo("300.00");
    assertThat(payrollEmployee.get("lossAmount").decimalValue()).isEqualByComparingTo("100.00");
    assertThat(payrollEmployee.get("salesAmount").decimalValue()).isEqualByComparingTo("1000.00");
    assertThat(payrollEmployee.get("netPay").decimalValue()).isEqualByComparingTo("5200.00");

    JsonNode adjusted =
        putJson(
            "/api/v1/hr/payroll/runs/%s/employees/%s/adjustments"
                .formatted(run.get("id").asText(), payrollEmployee.get("id").asText()),
            """
            {
              "bonusAmount": 400,
              "lossAmount": 150,
              "notes": "Manager adjustment"
            }
            """,
            200);
    assertThat(adjusted.get("bonusAmount").decimalValue()).isEqualByComparingTo("400.00");
    assertThat(adjusted.get("lossAmount").decimalValue()).isEqualByComparingTo("150.00");
    assertThat(adjusted.get("netPay").decimalValue()).isEqualByComparingTo("5250.00");

    JsonNode refreshed =
        postJsonWithoutBody(
            "/api/v1/hr/payroll/runs/%s/recalculate".formatted(run.get("id").asText()), 200);
    assertThat(refreshed.get("employees")).hasSize(1);
    JsonNode refreshedEmployee = refreshed.get("employees").get(0);
    assertThat(refreshedEmployee.get("bonusAmount").decimalValue()).isEqualByComparingTo("300.00");
    assertThat(refreshedEmployee.get("lossAmount").decimalValue()).isEqualByComparingTo("100.00");
    assertThat(refreshedEmployee.get("netPay").decimalValue()).isEqualByComparingTo("5200.00");
  }

  @Test
  void syncsSystemUsersAsEmployeesAndPerformanceUsesMainSystemSales() throws Exception {
    String branchId = createBranch("Synced Staff Branch", "SSB").get("id").asText();
    String cashierPassword = "synced-cashier-password";
    JsonNode cashier =
        postJson(
            "/api/v1/users",
            """
            {
              "email": "synced-cashier@keen.local",
              "password": "%s",
              "displayName": "Synced Cashier",
              "status": "ACTIVE",
              "roleIds": ["00000000-0000-4000-8000-000000000401"],
              "branchIds": ["%s"]
            }
            """
                .formatted(cashierPassword, branchId),
            201);

    JsonNode syncResult = postJsonWithoutBody("/api/v1/hr/employees/sync-users", 200);
    assertThat(syncResult.get("createdCount").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode syncedEmployee = null;
    for (JsonNode employee : syncResult.get("employees")) {
      if (employee.get("linkedUserId").asText().equals(cashier.get("id").asText())) {
        syncedEmployee = employee;
        break;
      }
    }
    assertThat(syncedEmployee).isNotNull();
    assertThat(syncedEmployee.get("fullName").asText()).isEqualTo("Synced Cashier");
    assertThat(syncedEmployee.get("branchId").asText()).isEqualTo(branchId);

    JsonNode category =
        postJson(
            "/api/v1/catalog/categories",
            """
            {
              "name": "Synced Staff Apparel",
              "code": "SYNCED_STAFF_APPAREL",
              "status": "ACTIVE"
            }
            """,
            201);
    JsonNode product =
        postJson(
            "/api/v1/catalog/products",
            """
            {
              "name": "Synced Staff Shirt",
              "sku": "SYNCED-SHIRT-001",
              "barcode": "SYNCED-SHIRT-001",
              "categoryId": "%s",
              "department": "Men",
              "unitPrice": 750,
              "costPrice": 400,
              "vatCategory": "G",
              "sizes": ["M"],
              "colors": ["White"],
              "status": "ACTIVE"
            }
            """
                .formatted(category.get("id").asText()),
            201);
    postJson(
        "/api/v1/stock-intakes",
        """
        {
          "branchId": "%s",
          "productId": "%s",
          "supplierName": "Synced Staff Supplier",
          "referenceNumber": "SYNC-STOCK-1",
          "quantity": 3,
          "unitCost": 400,
          "notes": "Test stock"
        }
        """
            .formatted(branchId, product.get("id").asText()),
        201);

    loginAs(cashier.get("email").asText(), cashierPassword);
    JsonNode sale =
        postJsonWithIdempotency(
            "/api/v1/sales",
            "synced-staff-sale-1",
            """
            {
              "branchId": "%s",
              "customerName": "Walk-in",
              "paymentMethod": "CASH",
              "cashReceived": 1000,
              "discountAmount": 0,
              "lines": [
                {
                  "productId": "%s",
                  "quantity": 1
                }
              ]
            }
            """
                .formatted(branchId, product.get("id").asText()),
            201);

    assertThat(sale.get("soldByEmployeeId").asText()).isEqualTo(syncedEmployee.get("id").asText());

    JsonNode performance =
        getJson(
            "/api/v1/hr/performance?fromDate=2020-01-01&toDate=2030-12-31&branchId=%s"
                .formatted(branchId));
    JsonNode summary = performance.get("summaries").get(0);
    assertThat(summary.get("employeeId").asText()).isEqualTo(syncedEmployee.get("id").asText());
    assertThat(summary.get("actualSalesAmount").decimalValue()).isEqualByComparingTo("750.00");
    assertThat(summary.get("salesDays")).hasSize(1);
  }

  private JsonNode createBranch(String name, String code) throws Exception {
    return postJson(
        "/api/v1/branches",
        """
        {
          "name": "%s",
          "code": "%s",
          "timeZone": "Africa/Nairobi",
          "status": "ACTIVE"
        }
        """
            .formatted(name, code),
        201);
  }

  private JsonNode getJson(String path) throws Exception {
    MvcResult result =
        mockMvc
            .perform(withSessionAndCsrf(get(path), session, csrf))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    post(path).contentType(MediaType.APPLICATION_JSON).content(body),
                    session,
                    csrf))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode postJsonWithIdempotency(
      String path, String idempotencyKey, String body, int expectedStatus) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withSessionAndCsrf(
                        post(path).contentType(MediaType.APPLICATION_JSON).content(body),
                        session,
                        csrf)
                    .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode postJsonWithoutBody(String path, int expectedStatus) throws Exception {
    MvcResult result =
        mockMvc
            .perform(withSessionAndCsrf(post(path), session, csrf))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode putJson(String path, String body, int expectedStatus) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withSessionAndCsrf(
                    put(path).contentType(MediaType.APPLICATION_JSON).content(body), session, csrf))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder withSessionAndCsrf(
      MockHttpServletRequestBuilder builder,
      MockHttpSession currentSession,
      CsrfExchange exchange) {
    MockHttpServletRequestBuilder next = builder;
    if (currentSession != null) {
      next = next.session(currentSession);
    }
    if (exchange != null) {
      next = next.cookie(exchange.cookie()).header(exchange.headerName(), exchange.token());
    }
    return next;
  }

  private CsrfExchange csrfExchange(MockHttpSession currentSession) throws Exception {
    MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
    if (currentSession != null) {
      request = request.session(currentSession);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfExchange(body.get("headerName").asText(), body.get("token").asText(), cookie);
  }

  private static String loginBody(String username, String password) {
    return """
        {
          "username": "%s",
          "password": "%s",
          "rememberDevice": false
        }
        """
        .formatted(username, password);
  }

  private record CsrfExchange(String headerName, String token, Cookie cookie) {}
}
