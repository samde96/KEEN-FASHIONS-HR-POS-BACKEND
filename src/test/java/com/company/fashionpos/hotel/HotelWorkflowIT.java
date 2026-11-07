package com.company.fashionpos.hotel;

import static com.company.fashionpos.hotel.HotelModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.company.fashionpos.branch.BranchRequest;
import com.company.fashionpos.branch.BranchResponse;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.branch.BranchStatus;
import com.company.fashionpos.branch.BranchType;
import com.company.fashionpos.hr.HrController;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false", "app.notifications.scheduler-enabled=false",
      "app.security.admin-email=hotel-test@example.test",
          "app.security.admin-password=test-password-only"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class HotelWorkflowIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    r.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    r.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired HotelService hotel;
  @Autowired HrController hr;
  @Autowired BranchService branches;
  @Autowired ObjectMapper json;
  @Autowired MockMvc http;
  @Autowired com.company.fashionpos.staff.StaffService staff;
  UUID org, branch, otherBranch, waiterId;
  AuthenticatedUser admin;
  LocalDate date;

  @BeforeEach
  void fixture() {
    org = UUID.randomUUID();
    branch = UUID.randomUUID();
    otherBranch = UUID.randomUUID();
    date = LocalDate.now(ZoneId.of("Africa/Nairobi"));
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Hotel','KES','Africa/Nairobi','ACTIVE')",
        org);
    UUID owner = UUID.randomUUID();
    String email = owner + "@test.local";
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Hotel Owner','ACTIVE')",
        owner,
        org,
        email);
    UUID role = UUID.randomUUID();
    jdbc.update(
        "insert into roles(id,organization_id,name,role_key) values (?,?,'Admin','ADMIN')",
        role,
        org);
    jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", owner, role);
    jdbc.update(
        "insert into role_permissions(role_id,permission_id) select ?,id from permissions where code='admin:manage'",
        role);
    admin =
        new AuthenticatedUser(
            owner,
            email,
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
          "Hotel " + id,
          id.toString().substring(0, 12));
    waiterId = hotel.saveWaiter(admin, null, new WaiterRequest(branch, "Alice Waiter", true)).id();
    hotel.saveReceiptSettings(
        admin, new ReceiptSettingsRequest(branch, "Test Hotel", "1234567", "+254700000111"));
  }

  @Test
  void generatesUniqueLocationCodesWhenTheRequestOmitsOne() {
    BranchResponse first =
        branches.create(
            admin,
            new BranchRequest(
                "Main Shop", null, "Africa/Nairobi", BranchStatus.ACTIVE, BranchType.SHOP));
    BranchResponse second =
        branches.create(
            admin,
            new BranchRequest(
                "Main Shop", null, "Africa/Nairobi", BranchStatus.ACTIVE, BranchType.SHOP));

    assertThat(first.code()).isEqualTo("MAIN-SHOP");
    assertThat(second.code()).isEqualTo("MAIN-SHOP-2");
  }

  @Test
  void hotelWaiterCanUseAvailabilityAfterHrLinksAnEmployeeProfile() throws Exception {
    jdbc.update("update branches set branch_type='HOTEL' where id=?", branch);
    UUID waiterAccountId = UUID.randomUUID();
    String waiterEmail = waiterAccountId + "@test.local";
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Hotel Waiter','ACTIVE')",
        waiterAccountId,
        org,
        waiterEmail);
    jdbc.update(
        "insert into user_branch_assignments(id,user_id,branch_id) values (?,?,?)",
        UUID.randomUUID(),
        waiterAccountId,
        branch);
    hotel.saveWaiter(admin, null, new WaiterRequest(branch, "Hotel Waiter", waiterAccountId, true));

    Map<String, Object> employeeRequest =
        Map.of(
            "userId",
            waiterAccountId,
            "branchId",
            branch,
            "firstName",
            "Hotel",
            "lastName",
            "Waiter",
            "employmentType",
            "CASUAL",
            "joiningDate",
            date,
            "basicSalary",
            BigDecimal.ZERO);
    http.perform(
            post("/api/v1/hr/employees")
                .with(user(admin.email()).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(employeeRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(waiterAccountId.toString()));

    http.perform(get("/api/v1/hr/me/availability").with(user(waiterEmail)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employee.employeeName").value("Hotel Waiter"))
        .andExpect(jsonPath("$.employee.branchName").exists());
    http.perform(post("/api/v1/hr/me/clock-in").with(user(waiterEmail)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeName").value("Hotel Waiter"));
  }

  @Test
  void checkoutIncludesAnyExplicitlyAssignedActiveUserAndLinksTheirNames() {
    UUID accountId = UUID.randomUUID();
    String email = accountId + "@test.local";
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'System Waiter','ACTIVE')",
        accountId,
        org,
        email);
    assertThat(hotel.checkout(admin, branch).waiters())
        .noneMatch(registeredWaiter -> accountId.equals(registeredWaiter.userId()));
    hotel.saveWaiter(admin, null, new WaiterRequest(branch, null, accountId, true));
    Checkout checkout = hotel.checkout(admin, branch);

    assertThat(checkout.waiters())
        .anySatisfy(
            registeredWaiter -> {
              assertThat(registeredWaiter.name()).isEqualTo("System Waiter");
              assertThat(registeredWaiter.userId()).isEqualTo(accountId);
              assertThat(registeredWaiter.active()).isTrue();
            });
  }

  @Test
  void checkoutRegistersWaiterRoleAccountsUnlessTheyAreRemoved() {
    var waiterRole =
        staff.listRoles(admin).stream()
            .filter(role -> role.key().equals("WAITER"))
            .findFirst()
            .orElseThrow();
    var account =
        staff.createUser(
            admin,
            new com.company.fashionpos.staff.UserRequest(
                UUID.randomUUID() + "@test.local",
                "test-password-only",
                "Restaurant Waiter",
                com.company.fashionpos.staff.UserStatus.ACTIVE,
                Set.of(waiterRole.id()),
                Set.of(branch)));
    AuthenticatedUser waiter =
        new AuthenticatedUser(
            account.id(),
            account.email(),
            account.displayName(),
            org,
            Set.of(branch),
            Set.of("WAITER"),
            Set.of("Waiter"),
            Set.of("hotel:sell"));

    Waiter registered =
        hotel.checkout(waiter, branch).waiters().stream()
            .filter(candidate -> account.id().equals(candidate.userId()))
            .findFirst()
            .orElseThrow();
    hotel.deleteWaiter(admin, registered.id());

    assertThat(hotel.checkout(waiter, branch).waiters())
        .noneMatch(candidate -> account.id().equals(candidate.userId()));
  }

  @Test
  void removesWaiterAssignmentsWhilePreservingReceiptHistory() {
    Item water = item("Water", 1);
    FoodOrder order = hotel.sell(admin, order(water, null, "CASH", 1));

    hotel.deleteWaiter(admin, waiterId);

    assertThat(hotel.listWaiters(admin, branch)).noneMatch(waiter -> waiter.id().equals(waiterId));
    assertThat(hotel.checkout(admin, branch).waiters())
        .noneMatch(waiter -> waiter.id().equals(waiterId));
    assertThat(hotel.order(admin, order.id()).servedByName()).isEqualTo("Alice Waiter");
    assertThat(
            jdbc.queryForObject(
                "select waiter_id from hotel_orders where id=?", UUID.class, order.id()))
        .isNull();
  }

  @Test
  void includesClosedRestaurantOrdersInStaffProductivity() {
    UUID employeeId = UUID.randomUUID();
    jdbc.update("update branches set branch_type='HOTEL' where id=?", branch);
    jdbc.update(
        """
        insert into employees(id,organization_id,user_id,primary_branch_id,employee_number,
          first_name,last_name,employment_type,employment_status,joining_date,basic_salary)
        values (?,?,?,?,?,'Hotel','Operator','PERMANENT','ACTIVE',?,0)
        """,
        employeeId,
        org,
        admin.userId(),
        branch,
        "HOTEL-" + employeeId.toString().substring(0, 8),
        date);
    MenuItem meal =
        hotel.saveMenuItem(
            admin,
            null,
            new MenuItemRequest(branch, "Lunch", "Meals", "", new BigDecimal("400"), true));
    hotel.sell(
        admin,
        new OrderRequest(
            UUID.randomUUID(),
            branch,
            waiterId,
            null,
            "Walk-in",
            "CASH",
            "",
            "DINE_IN",
            "",
            "",
            List.of(new OrderLineRequest(meal.id(), "FOOD", BigDecimal.ONE))));

    Map<String, Object> summary =
        hr.adminSummary(YearMonth.from(date), branch, () -> admin.email());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> staff = (List<Map<String, Object>>) summary.get("staff");
    Map<String, Object> operator =
        staff.stream()
            .filter(row -> employeeId.equals(row.get("employeeId")))
            .findFirst()
            .orElseThrow();

    assertThat(new BigDecimal(operator.get("sales").toString())).isEqualByComparingTo("400");
    assertThat(((Number) operator.get("transactions")).longValue()).isEqualTo(1);
  }

  Room room(String number) {
    return hotel.saveRoom(
        admin, null, new RoomRequest(branch, number, "Deluxe", 2, new BigDecimal("3000"), "READY"));
  }

  Item item(String name, int stock) {
    Item item =
        hotel.saveItem(
            admin,
            null,
            new ItemRequest(
                branch,
                name,
                "PRODUCT",
                "piece",
                true,
                new BigDecimal("250"),
                BigDecimal.TEN,
                true));
    if (stock > 0)
      hotel.adjustStock(
          admin,
          item.id(),
          new StockRequest(UUID.randomUUID(), BigDecimal.valueOf(stock), "RECEIPT", "Delivery"));
    return item;
  }

  BookingRequest request(Room room, LocalDate arrival, LocalDate departure) {
    return new BookingRequest(
        UUID.randomUUID(),
        branch,
        room.id(),
        "Jane Guest",
        "0700000000",
        "guest@example.test",
        2,
        arrival,
        departure,
        null,
        "Quiet room");
  }

  Booking stay(Room room) {
    return hotel.saveBooking(admin, null, request(room, date, date.plusDays(2)));
  }

  PaymentRequest payment(String amount) {
    return new PaymentRequest(UUID.randomUUID(), new BigDecimal(amount), "PAYMENT", "CASH", "");
  }

  OrderRequest order(Item item, UUID booking, String method, int quantity) {
    return new OrderRequest(
        UUID.randomUUID(),
        branch,
        waiterId,
        booking,
        "Walk-in",
        method,
        "",
        "DINE_IN",
        "",
        "",
        List.of(new OrderLineRequest(item.id(), "PRODUCT", BigDecimal.valueOf(quantity))));
  }

  @Test
  void completesStayWithDepositRoomFoodChargeFinalPaymentReceiptAndReports() {
    Room room = room("101");
    Item food = item("Lunch", 10);
    Booking stay = stay(room);
    PaymentRequest deposit = payment("1000");
    hotel.pay(admin, stay.id(), deposit);
    hotel.pay(admin, stay.id(), deposit);
    hotel.transition(admin, stay.id(), "check-in");
    OrderRequest request = order(food, stay.id(), "ROOM", 2);
    FoodOrder receipt = hotel.sell(admin, request);
    assertThat(receipt.tableNumber()).isEmpty();
    assertThat(hotel.sell(admin, request).id()).isEqualTo(receipt.id());
    var bill = hotel.workspace(admin, branch).bookings().get(0);
    assertThat(bill.total()).isEqualByComparingTo("6500");
    assertThat(bill.balance()).isEqualByComparingTo("5500");
    assertThat(bill.payments()).hasSize(1);
    assertThat(hotel.workspace(admin, branch).items().get(0).stock()).isEqualByComparingTo("8");
    assertThatThrownBy(() -> hotel.transition(admin, stay.id(), "check-out"))
        .hasMessageContaining("Settle");
    hotel.pay(admin, stay.id(), payment("5500"));
    assertThat(hotel.transition(admin, stay.id(), "check-out").status()).isEqualTo("CHECKED_OUT");
    assertThat(hotel.workspace(admin, branch).rooms().get(0).status()).isEqualTo("CLEANING");
    assertThat(hotel.bookingOrders(admin, stay.id())).hasSize(1);
    Report report = hotel.report(admin, branch, date, date);
    assertThat(report.roomRevenue()).isEqualByComparingTo("6000");
    assertThat(report.productRevenue()).isEqualByComparingTo("500");
    assertThat(report.foodRevenue()).isZero();
    assertThat(report.totalRevenue()).isEqualByComparingTo("6500");
    assertThat(report.paymentsReceived()).isEqualByComparingTo("6500");
    assertThat(report.outstandingBalance()).isZero();
    // Repeating check-out must not reset a room that housekeeping has already prepared.
    hotel.saveRoom(
        admin, room.id(), new RoomRequest(branch, "101", "Deluxe", 2, room.nightlyRate(), "READY"));
    hotel.transition(admin, stay.id(), "check-out");
    assertThat(hotel.workspace(admin, branch).rooms().get(0).status()).isEqualTo("READY");
  }

  @Test
  void countsOperatingExpensesButExcludesAssetPurchasesFromProfitAndLoss() {
    HotelExpenseRequest expense =
        new HotelExpenseRequest(
            UUID.randomUUID(),
            branch,
            date,
            "UTILITIES",
            "Electricity",
            "Power company",
            new BigDecimal("1200"),
            "CASH",
            "",
            "");
    HotelExpense recorded = hotel.saveExpense(admin, null, expense);
    assertThat(hotel.saveExpense(admin, null, expense).id()).isEqualTo(recorded.id());
    hotel.saveAsset(
        admin,
        null,
        new HotelAssetRequest(
            branch,
            "Kitchen fridge",
            "EQUIPMENT",
            1,
            date,
            new BigDecimal("45000"),
            "Kitchen",
            "ACTIVE",
            ""));

    Report report = hotel.report(admin, branch, date, date);
    assertThat(report.totalRevenue()).isZero();
    assertThat(report.totalExpenses()).isEqualByComparingTo("1200");
    assertThat(report.profitLoss()).isEqualByComparingTo("-1200");
    assertThat(report.daily())
        .singleElement()
        .satisfies(day -> assertThat(day.expenses()).isEqualByComparingTo("1200"));
    assertThat(report.expensesByCategory())
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.category()).isEqualTo("UTILITIES");
              assertThat(total.amount()).isEqualByComparingTo("1200");
            });
    assertThat(hotel.expenses(admin, branch)).hasSize(1);
    assertThat(hotel.assets(admin, branch))
        .singleElement()
        .satisfies(asset -> assertThat(asset.purchaseCost()).isEqualByComparingTo("45000"));
  }

  @Test
  void rejectsOverlappingReservationsButAllowsSameDayTurnoverAndReplay() {
    Room room = room("102");
    BookingRequest request = request(room, date, date.plusDays(2));
    Booking first = hotel.saveBooking(admin, null, request);
    assertThat(hotel.saveBooking(admin, null, request).id()).isEqualTo(first.id());
    assertThatThrownBy(
            () -> hotel.saveBooking(admin, null, request(room, date.plusDays(1), date.plusDays(3))))
        .hasMessageContaining("already booked");
    assertThat(
            hotel
                .saveBooking(admin, null, request(room, date.plusDays(2), date.plusDays(3)))
                .status())
        .isEqualTo("RESERVED");
    assertThat(hotel.workspace(admin, branch).bookings()).hasSize(2);
  }

  @Test
  void serializesConcurrentReservations() throws Exception {
    Room room = room("103");
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> reserve =
          () -> {
            start.await();
            try {
              hotel.saveBooking(admin, null, request(room, date, date.plusDays(1)));
              return true;
            } catch (IllegalArgumentException conflict) {
              assertThat(conflict.getMessage()).contains("already booked");
              return false;
            }
          };
      var one = pool.submit(reserve);
      var two = pool.submit(reserve);
      start.countDown();
      assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(hotel.workspace(admin, branch).bookings()).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void keepsAgreedPricesWhenRoomAndMenuRatesChange() {
    Room room = room("104");
    Item food = item("Breakfast", 5);
    Booking stay = stay(room);
    hotel.transition(admin, stay.id(), "check-in");
    FoodOrder order = hotel.sell(admin, order(food, stay.id(), "ROOM", 1));
    hotel.saveRoom(
        admin,
        room.id(),
        new RoomRequest(branch, room.number(), "Deluxe", 2, new BigDecimal("9000"), "READY"));
    hotel.saveItem(
        admin,
        food.id(),
        new ItemRequest(
            branch,
            food.name(),
            "PRODUCT",
            "piece",
            true,
            new BigDecimal("800"),
            BigDecimal.TEN,
            true));
    assertThat(hotel.workspace(admin, branch).bookings().get(0).roomTotal())
        .isEqualByComparingTo("6000");
    assertThat(hotel.order(admin, order.id()).lines().get(0).unitPrice())
        .isEqualByComparingTo("250");
  }

  @Test
  void rejectsStockOversellingAndRollsBackWholeOrder() {
    Item one = item("Water", 10), two = item("Dinner", 1);
    OrderRequest request =
        new OrderRequest(
            UUID.randomUUID(),
            branch,
            waiterId,
            null,
            "Guest",
            "CASH",
            "",
            "DINE_IN",
            "1",
            "",
            List.of(
                new OrderLineRequest(one.id(), "PRODUCT", BigDecimal.ONE),
                new OrderLineRequest(two.id(), "PRODUCT", new BigDecimal("2"))));
    assertThatThrownBy(() -> hotel.sell(admin, request)).hasMessageContaining("Insufficient stock");
    var workspace = hotel.workspace(admin, branch);
    assertThat(workspace.orders()).isEmpty();
    assertThat(
            workspace.items().stream()
                .filter(i -> i.id().equals(one.id()))
                .findFirst()
                .orElseThrow()
                .stock())
        .isEqualByComparingTo("10");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from hotel_commands where request_id=?",
                Integer.class,
                request.requestId()))
        .isZero();
    StockRequest usage =
        new StockRequest(UUID.randomUUID(), new BigDecimal("2"), "USAGE", "Staff meal");
    hotel.adjustStock(admin, one.id(), usage);
    hotel.adjustStock(admin, one.id(), usage);
    assertThat(
            hotel.workspace(admin, branch).items().stream()
                .filter(i -> i.id().equals(one.id()))
                .findFirst()
                .orElseThrow()
                .stock())
        .isEqualByComparingTo("8");
  }

  @Test
  void requiresDepositRefundBeforeCancellationAndRejectsOverpayments() {
    Room room = room("105");
    Booking stay = stay(room);
    assertThatThrownBy(() -> hotel.pay(admin, stay.id(), payment("6000.01")))
        .hasMessageContaining("exceeds");
    hotel.pay(admin, stay.id(), payment("1000"));
    assertThatThrownBy(() -> hotel.transition(admin, stay.id(), "cancel"))
        .hasMessageContaining("refund");
    assertThatThrownBy(
            () ->
                hotel.pay(
                    admin,
                    stay.id(),
                    new PaymentRequest(
                        UUID.randomUUID(), new BigDecimal("1001"), "REFUND", "CASH", "")))
        .hasMessageContaining("exceeds");
    hotel.pay(
        admin,
        stay.id(),
        new PaymentRequest(UUID.randomUUID(), new BigDecimal("1000"), "REFUND", "CASH", ""));
    assertThat(hotel.transition(admin, stay.id(), "cancel").balance()).isZero();
    assertThat(hotel.saveBooking(admin, null, request(room, date, date.plusDays(1))).status())
        .isEqualTo("RESERVED");
    assertThat(hotel.report(admin, branch, date, date).paymentsReceived()).isZero();
  }

  @Test
  void enforcesDatesCapacityHousekeepingAndOccupiedRooms() {
    Room room = room("106");
    Booking stay = stay(room);
    hotel.saveRoom(
        admin,
        room.id(),
        new RoomRequest(branch, room.number(), room.roomType(), 2, room.nightlyRate(), "CLEANING"));
    assertThatThrownBy(() -> hotel.transition(admin, stay.id(), "check-in"))
        .hasMessageContaining("ready");
    hotel.saveRoom(
        admin,
        room.id(),
        new RoomRequest(branch, room.number(), room.roomType(), 2, room.nightlyRate(), "READY"));
    hotel.transition(admin, stay.id(), "check-in");
    assertThatThrownBy(
            () ->
                hotel.saveRoom(
                    admin,
                    room.id(),
                    new RoomRequest(
                        branch, room.number(), room.roomType(), 2, room.nightlyRate(), "CLEANING")))
        .hasMessageContaining("Check out");
    jdbc.update(
        "update hotel_bookings set arrival=?,departure=? where id=?",
        date.minusDays(2),
        date,
        stay.id());
    Booking next = hotel.saveBooking(admin, null, request(room, date, date.plusDays(1)));
    assertThatThrownBy(() -> hotel.transition(admin, next.id(), "check-in"))
        .hasMessageContaining("previous guest");
    Room future = room("107");
    Booking later =
        hotel.saveBooking(admin, null, request(future, date.plusDays(1), date.plusDays(2)));
    assertThatThrownBy(() -> hotel.transition(admin, later.id(), "check-in"))
        .hasMessageContaining("stay dates");
    BookingRequest tooMany =
        new BookingRequest(
            UUID.randomUUID(),
            branch,
            future.id(),
            "Group",
            "",
            "",
            3,
            date,
            date.plusDays(1),
            null,
            "");
    assertThatThrownBy(() -> hotel.saveBooking(admin, null, tooMany))
        .hasMessageContaining("capacity");
  }

  @Test
  void enforcesTenantBranchAndPermissionBoundaries() {
    Room room = room("108");
    Booking stay = stay(room);
    Item food = item("Tea", 4);
    AuthenticatedUser outsider =
        new AuthenticatedUser(
            admin.userId(),
            admin.email(),
            "Other",
            UUID.randomUUID(),
            Set.of(),
            Set.of("ADMIN"),
            Set.of(),
            Set.of("admin:manage"));
    assertThatThrownBy(() -> hotel.transition(outsider, stay.id(), "check-in"))
        .isInstanceOf(EntityNotFoundException.class);
    AuthenticatedUser restricted =
        new AuthenticatedUser(
            admin.userId(),
            admin.email(),
            "Manager",
            org,
            Set.of(otherBranch),
            Set.of("SHOP_MANAGER"),
            Set.of(),
            Set.of("hotel:view", "hotel:manage"));
    assertThatThrownBy(() -> hotel.workspace(restricted, branch))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> hotel.saveBooking(restricted, null, request(room, date, date.plusDays(1))))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> hotel.order(restricted, UUID.randomUUID()))
        .isInstanceOf(EntityNotFoundException.class);
    AuthenticatedUser reader =
        new AuthenticatedUser(
            admin.userId(),
            admin.email(),
            "Reader",
            org,
            Set.of(branch),
            Set.of("SHOP_MANAGER"),
            Set.of(),
            Set.of("hotel:view"));
    assertThat(hotel.workspace(reader, branch).rooms()).hasSize(1);
    assertThatThrownBy(() -> hotel.sell(reader, order(food, null, "CASH", 1)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> hotel.report(reader, branch, date, date))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> hotel.workspace(outsider, branch))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rejectsForeignRoomChargesAndChangedIdempotencyPayloads() {
    Item food = item("Coffee", 10);
    Room room = room("109");
    Booking stay = stay(room);
    assertThatThrownBy(() -> hotel.sell(admin, order(food, stay.id(), "ROOM", 1)))
        .hasMessageContaining("checked-in");
    hotel.transition(admin, stay.id(), "check-in");
    OrderRequest first = order(food, null, "CASH", 1);
    hotel.sell(admin, first);
    OrderRequest changed =
        new OrderRequest(
            first.requestId(),
            branch,
            waiterId,
            null,
            "Guest",
            "CASH",
            "",
            "DINE_IN",
            "1",
            "",
            List.of(new OrderLineRequest(food.id(), "PRODUCT", BigDecimal.TEN)));
    assertThatThrownBy(() -> hotel.sell(admin, changed)).hasMessageContaining("different change");
    assertThatThrownBy(
            () ->
                hotel.sell(
                    admin,
                    new OrderRequest(
                        UUID.randomUUID(),
                        otherBranch,
                        waiterId,
                        stay.id(),
                        "",
                        "ROOM",
                        "",
                        "DINE_IN",
                        "1",
                        "",
                        first.lines())))
        .hasMessageContaining("this hotel");
    assertThatThrownBy(
            () ->
                hotel.sell(
                    admin,
                    new OrderRequest(
                        UUID.randomUUID(),
                        otherBranch,
                        waiterId,
                        null,
                        "",
                        "CASH",
                        "",
                        "DINE_IN",
                        "1",
                        "",
                        first.lines())))
        .hasMessageContaining("this hotel");
    assertThatThrownBy(
            () ->
                hotel.pay(
                    admin,
                    stay.id(),
                    new PaymentRequest(UUID.randomUUID(), BigDecimal.ONE, "PAYMENT", "MPESA", "")))
        .hasMessageContaining("reference");
  }

  @Test
  void countsDirectFoodSalesOnceAndValidatesReportPeriod() {
    Item food = item("Supper", 10);
    hotel.sell(admin, order(food, null, "CASH", 3));
    Report report = hotel.report(admin, branch, date, date);
    assertThat(report.productRevenue()).isEqualByComparingTo("750");
    assertThat(report.paymentsReceived()).isEqualByComparingTo("750");
    assertThat(report.daily().get(0).products()).isEqualByComparingTo("750");
    assertThatThrownBy(() -> hotel.report(admin, branch, date, date.plusDays(366)))
        .hasMessageContaining("366");
    assertThatThrownBy(() -> hotel.report(admin, branch, date.plusDays(1), date))
        .hasMessageContaining("366");
  }

  @Test
  void securesHttpRoutesAndValidatesRequests() throws Exception {
    var owner = user(admin.email()).authorities(new SimpleGrantedAuthority("admin:manage"));
    http.perform(get("/api/v1/hotel/workspace").param("branchId", branch.toString()))
        .andExpect(status().isUnauthorized());
    http.perform(
            get("/api/v1/hotel/workspace")
                .param("branchId", branch.toString())
                .with(user(admin.email()).authorities(new SimpleGrantedAuthority("pos:sell"))))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/hotel/rooms")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    http.perform(
            post("/api/v1/hotel/rooms")
                .with(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    var room = new RoomRequest(branch, "HTTP", "Standard", 2, new BigDecimal("1500"), "READY");
    http.perform(
            post("/api/v1/hotel/rooms")
                .with(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(room)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("number").value("HTTP"));
    http.perform(get("/api/v1/hotel/workspace").param("branchId", branch.toString()).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("rooms[0].nightlyRate").value(1500));
    http.perform(
            get("/api/v1/hotel/reports")
                .param("branchId", branch.toString())
                .param("from", date.toString())
                .param("to", date.toString())
                .with(user(admin.email()).authorities(new SimpleGrantedAuthority("hotel:view"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void cancelsUnpaidOrderAndReturnsOnlyProductsOnce() {
    MenuItem meal =
        hotel.saveMenuItem(
            admin,
            null,
            new MenuItemRequest(branch, "Pilau", "Meals", "", new BigDecimal("400"), true));
    Item water = item("Water bottle", 5);
    FoodOrder order =
        hotel.sell(
            admin,
            new OrderRequest(
                UUID.randomUUID(),
                branch,
                waiterId,
                null,
                "Guest",
                "UNPAID",
                "",
                "TAKEAWAY",
                "",
                "",
                List.of(
                    new OrderLineRequest(meal.id(), "FOOD", BigDecimal.ONE),
                    new OrderLineRequest(water.id(), "PRODUCT", BigDecimal.ONE))));
    hotel.cancelOrder(admin, order.id());
    hotel.cancelOrder(admin, order.id());
    assertThat(hotel.workspace(admin, branch).items().get(0).stock()).isEqualByComparingTo("5");
    assertThat(hotel.workspace(admin, branch).movements()).hasSize(3);
    assertThat(hotel.report(admin, branch, date, date).foodRevenue()).isZero();
    assertThat(hotel.report(admin, branch, date, date).outstandingBalance()).isZero();
    assertThatThrownBy(
            () ->
                hotel.settleOrder(
                    admin,
                    order.id(),
                    new OrderPaymentRequest(UUID.randomUUID(), "CASH", null, "")))
        .hasMessageContaining("active");
  }

  AuthenticatedUser operator(String name, String role, Set<String> permissions) {
    UUID id = UUID.randomUUID();
    String email = id + "@test.local";
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,?,'ACTIVE')",
        id,
        org,
        email,
        name);
    return new AuthenticatedUser(
        id, email, name, org, Set.of(branch), Set.of(role), Set.of(role), permissions);
  }

  @Test
  void earlyCheckoutReleasesCalendarNightsWithoutDoubleCountingOccupancy() {
    Room room = room("EARLY");
    Booking first = stay(room);
    hotel.transition(admin, first.id(), "check-in");
    hotel.pay(admin, first.id(), payment("6000"));
    hotel.transition(admin, first.id(), "check-out");
    hotel.saveBooking(admin, null, request(room, date, date.plusDays(1)));
    Report report = hotel.report(admin, branch, date, date);
    assertThat(report.bookedRoomNights()).isEqualTo(1);
    assertThat(report.bookedOccupancyRate()).isEqualByComparingTo("100");
    assertThat(hotel.report(admin, branch, date.plusDays(1), date.plusDays(1)).bookedRoomNights())
        .isZero();
  }

  @Test
  void sharedSalesManagerSelectsWaiterAndReceiptsKeepOriginalWaiterAndPaymentDetails() {
    MenuItem rice =
        hotel.saveMenuItem(
            admin,
            null,
            new MenuItemRequest(branch, "Rice and stew", "Meals", "", new BigDecimal("350"), true));
    Item bottled = item("Bottled water", 10);
    AuthenticatedUser counter =
        operator("Hotel counter", "HOTEL_SALES_MANAGER", Set.of("hotel:sell"));
    OrderRequest request =
        new OrderRequest(
            UUID.randomUUID(),
            branch,
            waiterId,
            null,
            "Guest",
            "UNPAID",
            "",
            "DINE_IN",
            "7",
            "No chilli",
            List.of(
                new OrderLineRequest(rice.id(), "FOOD", new BigDecimal("4")),
                new OrderLineRequest(bottled.id(), "PRODUCT", BigDecimal.ONE)));
    FoodOrder order = hotel.sell(counter, request);
    assertThat(order.servedByName()).isEqualTo("Alice Waiter");
    assertThat(order.operatorId()).isEqualTo(counter.userId());
    assertThat(order.waiterId()).isEqualTo(waiterId);
    assertThat(order.status()).isEqualTo("OPEN");
    assertThat(order.tillNumber()).isEqualTo("1234567");
    assertThat(order.contactPhone()).isEqualTo("+254700000111");
    assertThat(hotel.workspace(admin, branch).items().get(0).stock()).isEqualByComparingTo("9");
    assertThat(hotel.workspace(admin, branch).movements()).hasSize(2);
    assertThat(hotel.report(admin, branch, date, date).foodRevenue()).isEqualByComparingTo("1400");
    assertThat(hotel.report(admin, branch, date, date).productRevenue())
        .isEqualByComparingTo("250");
    hotel.saveWaiter(admin, waiterId, new WaiterRequest(branch, "Alice renamed", false));
    hotel.saveReceiptSettings(
        admin, new ReceiptSettingsRequest(branch, "New hotel name", "7654321", "+254700000222"));
    FoodOrder reprint = hotel.order(counter, order.id());
    assertThat(reprint.servedByName()).isEqualTo("Alice Waiter");
    assertThat(reprint.hotelName()).isEqualTo("Test Hotel");
    assertThat(reprint.tillNumber()).isEqualTo("1234567");
    OrderPaymentRequest payment = new OrderPaymentRequest(UUID.randomUUID(), "CASH", null, "");
    assertThat(hotel.settleOrder(counter, order.id(), payment).status()).isEqualTo("CLOSED");
    hotel.settleOrder(counter, order.id(), payment);
    assertThat(hotel.report(admin, branch, date, date).paymentsReceived())
        .isEqualByComparingTo("1650");
    assertThatThrownBy(() -> hotel.workspace(counter, branch))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> hotel.saveWaiter(counter, null, new WaiterRequest(branch, "Other", true)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                hotel.saveReceiptSettings(
                    counter, new ReceiptSettingsRequest(branch, "Other", "1", "0700000000")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void validatesWaiterDirectoryAndAllowsSalesBeforeReceiptDetailsAreConfigured() {
    Item water = item("Water", 5);
    OrderRequest sale = order(water, null, "CASH", 1);
    hotel.saveWaiter(admin, waiterId, new WaiterRequest(branch, "Alice Waiter", false));
    assertThatThrownBy(() -> hotel.sell(admin, sale)).hasMessageContaining("active waiter");
    hotel.saveWaiter(admin, waiterId, new WaiterRequest(branch, "Alice Waiter", true));
    assertThatThrownBy(
            () -> hotel.saveWaiter(admin, null, new WaiterRequest(branch, "alice waiter", true)))
        .hasMessageContaining("already exists");
    jdbc.update("delete from hotel_receipt_settings where branch_id=?", branch);
    FoodOrder order = hotel.sell(admin, sale);
    assertThat(order.tillNumber()).isEmpty();
    assertThat(order.contactPhone()).isEmpty();
    assertThat(hotel.workspace(admin, branch).items().get(0).stock()).isEqualByComparingTo("4");
    UUID foreign =
        hotel
            .saveWaiter(admin, null, new WaiterRequest(otherBranch, "Different hotel waiter", true))
            .id();
    var wrong =
        new OrderRequest(
            UUID.randomUUID(),
            branch,
            foreign,
            null,
            "Guest",
            "CASH",
            "",
            "DINE_IN",
            "1",
            "",
            sale.lines());
    assertThatThrownBy(() -> hotel.sell(admin, wrong)).hasMessageContaining("active waiter");
  }

  @Test
  void hotelSalesManagerHasOnlyAssignedHotelSalesAccessOverHttp() throws Exception {
    var role =
        staff.listRoles(admin).stream()
            .filter(r -> r.key().equals("HOTEL_SALES_MANAGER"))
            .findFirst()
            .orElseThrow();
    assertThat(role.permissionCodes()).containsExactly("hotel:sell");
    var saved =
        staff.createUser(
            admin,
            new com.company.fashionpos.staff.UserRequest(
                UUID.randomUUID() + "@test.local",
                "test-password-only",
                "Hotel Sales Counter",
                com.company.fashionpos.staff.UserStatus.ACTIVE,
                Set.of(role.id()),
                Set.of(branch)));
    var principal =
        user(saved.email())
            .authorities(
                new SimpleGrantedAuthority("ROLE_HOTEL_SALES_MANAGER"),
                new SimpleGrantedAuthority("hotel:sell"));
    http.perform(get("/api/v1/hotel/checkout").param("branchId", branch.toString()).with(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("waiters[0].name").value("Alice Waiter"));
    http.perform(get("/api/v1/branches").with(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    for (String path :
        List.of(
            "/api/v1/catalog/products",
            "/api/v1/inventory",
            "/api/v1/expenses",
            "/api/v1/offline/bootstrap",
            "/api/v1/hotel/workspace",
            "/api/v1/hotel/waiters",
            "/api/v1/hotel/receipt-settings",
            "/api/v1/hotel/reports",
            "/api/v1/hotel/kitchen"))
      http.perform(get(path).param("branchId", branch.toString()).with(principal))
          .andExpect(status().isForbidden());
    var sale = order(item("Bottle", 4), null, "CASH", 1);
    http.perform(
            post("/api/v1/hotel/orders")
                .with(principal)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(sale)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("servedByName").value("Alice Waiter"));
    http.perform(
            get("/api/v1/hotel/checkout").param("branchId", otherBranch.toString()).with(principal))
        .andExpect(status().isForbidden());
    assertThatThrownBy(
            () ->
                staff.createUser(
                    admin,
                    new com.company.fashionpos.staff.UserRequest(
                        UUID.randomUUID() + "@test.local",
                        "test-password-only",
                        "Unassigned",
                        com.company.fashionpos.staff.UserStatus.ACTIVE,
                        Set.of(role.id()),
                        Set.of())))
        .hasMessageContaining("assigned alone");
  }

  @Test
  void retainsUnpaidOrdersBeyondTheRecentReceiptLimit() {
    MenuItem meal =
        hotel.saveMenuItem(
            admin, null, new MenuItemRequest(branch, "Tea", "Drinks", "", BigDecimal.ONE, true));
    FoodOrder unpaid =
        hotel.sell(
            admin,
            new OrderRequest(
                UUID.randomUUID(),
                branch,
                waiterId,
                null,
                "Guest",
                "UNPAID",
                "",
                "DINE_IN",
                "1",
                "",
                List.of(new OrderLineRequest(meal.id(), "FOOD", BigDecimal.ONE))));
    jdbc.update(
        "update hotel_orders set created_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(2))),
        unpaid.id());
    jdbc.update(
        """
        insert into hotel_orders(id,organization_id,branch_id,reference,customer_name,total,method,
          operator_id,served_by_name,table_number,service_type,notes,status,paid_at)
        select gen_random_uuid(),?,?,'H-'||n,'Historic guest',1,'CASH',?,'Owner','1','DINE_IN','','CLOSED',now()
        from generate_series(1,101) n
        """,
        org,
        branch,
        admin.userId());
    jdbc.update(
        """
        insert into hotel_order_lines(id,order_id,item_type,menu_item_id,item_name,quantity,unit_price,total)
        select gen_random_uuid(),id,'FOOD',?,'Tea',1,1,1 from hotel_orders where organization_id=? and reference like 'H-%'
        """,
        meal.id(), org);
    var orders = hotel.checkout(admin, branch).orders();
    assertThat(orders).hasSize(101);
    assertThat(orders)
        .anyMatch(order -> order.id().equals(unpaid.id()) && order.method().equals("UNPAID"));
  }

  @Test
  void upgradesExistingHotelOrdersWithoutLosingServingNamesOrMoney() {
    String schema = "hotel_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    String url = System.getenv("FASHIONPOS_TEST_DB_URL"),
        username = System.getenv("FASHIONPOS_TEST_DB_USERNAME"),
        password = System.getenv("FASHIONPOS_TEST_DB_PASSWORD");
    Flyway.configure()
        .dataSource(url, username, password)
        .schemas(schema)
        .defaultSchema(schema)
        .target("37")
        .load()
        .migrate();
    var isolated =
        new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                username,
                password));
    UUID organization = UUID.randomUUID(),
        location = UUID.randomUUID(),
        operator = UUID.randomUUID(),
        order = UUID.randomUUID();
    try {
      isolated.update(
          "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Legacy Hotel','KES','Africa/Nairobi','ACTIVE')",
          organization);
      isolated.update(
          "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,'Hotel','OLD','Africa/Nairobi','ACTIVE')",
          location,
          organization);
      isolated.update(
          "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Old waiter login','ACTIVE')",
          operator,
          organization,
          operator + "@test.local");
      isolated.update(
          "insert into hotel_orders(id,organization_id,branch_id,reference,customer_name,total,method,served_by_id,served_by_name,service_type,kitchen_status) values (?,?,?,'OLD-1','Guest',125,'CASH',?,'Alice original','TAKEAWAY','PREPARING')",
          order,
          organization,
          location,
          operator);
      Flyway.configure()
          .dataSource(url, username, password)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      var migrated = isolated.queryForMap("select * from hotel_orders where id=?", order);
      assertThat(migrated.get("operator_id")).isEqualTo(operator);
      assertThat(migrated.get("served_by_name")).isEqualTo("Alice original");
      assertThat((BigDecimal) migrated.get("total")).isEqualByComparingTo("125");
      assertThat(migrated.get("status")).isEqualTo("CLOSED");
      assertThat(migrated.get("hotel_name")).isEqualTo("Legacy Hotel");
      assertThat(
              isolated.queryForObject(
                  "select count(*) from role_permissions rp join roles r on r.id=rp.role_id join permissions p on p.id=rp.permission_id where r.organization_id=? and r.role_key='HOTEL_SALES_MANAGER' and p.code='hotel:sell'",
                  Integer.class,
                  organization))
          .isEqualTo(1);
    } finally {
      jdbc.execute("drop schema " + schema + " cascade");
    }
  }
}
