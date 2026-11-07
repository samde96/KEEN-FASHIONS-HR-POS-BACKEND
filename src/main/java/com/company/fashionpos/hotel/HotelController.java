package com.company.fashionpos.hotel;

import static com.company.fashionpos.hotel.HotelModels.*;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hotel")
public class HotelController {
  private final HotelService hotel;
  private final AuthenticatedUserService users;

  public HotelController(HotelService hotel, AuthenticatedUserService users) {
    this.hotel = hotel;
    this.users = users;
  }

  @GetMapping("/workspace")
  public Workspace workspace(@RequestParam UUID branchId, Principal principal) {
    return hotel.workspace(users.current(principal), branchId);
  }

  @GetMapping("/checkout")
  public Checkout checkout(@RequestParam UUID branchId, Principal principal) {
    return hotel.checkout(users.current(principal), branchId);
  }

  @GetMapping("/waiters")
  public List<Waiter> waiters(@RequestParam UUID branchId, Principal principal) {
    return hotel.listWaiters(users.current(principal), branchId);
  }

  @PostMapping("/waiters")
  @ResponseStatus(HttpStatus.CREATED)
  public Waiter createWaiter(@Valid @RequestBody WaiterRequest request, Principal principal) {
    return hotel.saveWaiter(users.current(principal), null, request);
  }

  @PutMapping("/waiters/{id}")
  public Waiter updateWaiter(
      @PathVariable UUID id, @Valid @RequestBody WaiterRequest request, Principal principal) {
    return hotel.saveWaiter(users.current(principal), id, request);
  }

  @DeleteMapping("/waiters/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWaiter(@PathVariable UUID id, Principal principal) {
    hotel.deleteWaiter(users.current(principal), id);
  }

  @GetMapping("/receipt-settings")
  public ReceiptSettings receiptSettings(@RequestParam UUID branchId, Principal principal) {
    return hotel.receiptSettings(users.current(principal), branchId);
  }

  @PutMapping("/receipt-settings")
  public ReceiptSettings saveReceiptSettings(
      @Valid @RequestBody ReceiptSettingsRequest request, Principal principal) {
    return hotel.saveReceiptSettings(users.current(principal), request);
  }

  @PostMapping("/menu-items")
  @ResponseStatus(HttpStatus.CREATED)
  public MenuItem createMenuItem(@Valid @RequestBody MenuItemRequest request, Principal principal) {
    return hotel.saveMenuItem(users.current(principal), null, request);
  }

  @PutMapping("/menu-items/{id}")
  public MenuItem updateMenuItem(
      @PathVariable UUID id, @Valid @RequestBody MenuItemRequest request, Principal principal) {
    return hotel.saveMenuItem(users.current(principal), id, request);
  }

  @GetMapping("/recipes")
  public List<Recipe> recipes(@RequestParam UUID branchId, Principal principal) {
    return hotel.recipes(users.current(principal), branchId);
  }

  @PostMapping("/menu-items/{id}/recipes")
  @ResponseStatus(HttpStatus.CREATED)
  public Recipe createRecipeVersion(
      @PathVariable UUID id, @Valid @RequestBody RecipeRequest request, Principal principal) {
    return hotel.saveRecipe(users.current(principal), id, request);
  }

  @PostMapping("/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public Room createRoom(@Valid @RequestBody RoomRequest request, Principal principal) {
    return hotel.saveRoom(users.current(principal), null, request);
  }

  @PutMapping("/rooms/{id}")
  public Room updateRoom(
      @PathVariable UUID id, @Valid @RequestBody RoomRequest request, Principal principal) {
    return hotel.saveRoom(users.current(principal), id, request);
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  public Booking reserve(@Valid @RequestBody BookingRequest request, Principal principal) {
    return hotel.saveBooking(users.current(principal), null, request);
  }

  @PutMapping("/bookings/{id}")
  public Booking updateBooking(
      @PathVariable UUID id, @Valid @RequestBody BookingRequest request, Principal principal) {
    return hotel.saveBooking(users.current(principal), id, request);
  }

  @PostMapping("/bookings/{id}/check-in")
  public Booking checkIn(@PathVariable UUID id, Principal principal) {
    return hotel.transition(users.current(principal), id, "check-in");
  }

  @PostMapping("/bookings/{id}/check-out")
  public Booking checkOut(@PathVariable UUID id, Principal principal) {
    return hotel.transition(users.current(principal), id, "check-out");
  }

  @PostMapping("/bookings/{id}/cancel")
  public Booking cancel(@PathVariable UUID id, Principal principal) {
    return hotel.transition(users.current(principal), id, "cancel");
  }

  @PostMapping("/bookings/{id}/payments")
  public Booking pay(
      @PathVariable UUID id, @Valid @RequestBody PaymentRequest request, Principal principal) {
    return hotel.pay(users.current(principal), id, request);
  }

  @GetMapping("/bookings/{id}/orders")
  public List<FoodOrder> bookingOrders(@PathVariable UUID id, Principal principal) {
    return hotel.bookingOrders(users.current(principal), id);
  }

  @GetMapping("/sales-history")
  public List<SalesHistoryOrder> salesHistory(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID branchId,
      Principal principal) {
    return hotel.salesHistory(users.current(principal), branchId, from, to);
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public Item createItem(@Valid @RequestBody ItemRequest request, Principal principal) {
    return hotel.saveItem(users.current(principal), null, request);
  }

  @PutMapping("/items/{id}")
  public Item updateItem(
      @PathVariable UUID id, @Valid @RequestBody ItemRequest request, Principal principal) {
    return hotel.saveItem(users.current(principal), id, request);
  }

  @PostMapping("/items/{id}/stock")
  public Item stock(
      @PathVariable UUID id, @Valid @RequestBody StockRequest request, Principal principal) {
    return hotel.adjustStock(users.current(principal), id, request);
  }

  @GetMapping("/expenses")
  public List<HotelExpense> expenses(@RequestParam UUID branchId, Principal principal) {
    return hotel.expenses(users.current(principal), branchId);
  }

  @PostMapping("/expenses")
  @ResponseStatus(HttpStatus.CREATED)
  public HotelExpense createExpense(
      @Valid @RequestBody HotelExpenseRequest request, Principal principal) {
    return hotel.saveExpense(users.current(principal), null, request);
  }

  @PutMapping("/expenses/{id}")
  public HotelExpense updateExpense(
      @PathVariable UUID id, @Valid @RequestBody HotelExpenseRequest request, Principal principal) {
    return hotel.saveExpense(users.current(principal), id, request);
  }

  @GetMapping("/assets")
  public List<HotelAsset> assets(@RequestParam UUID branchId, Principal principal) {
    return hotel.assets(users.current(principal), branchId);
  }

  @PostMapping("/assets")
  @ResponseStatus(HttpStatus.CREATED)
  public HotelAsset createAsset(
      @Valid @RequestBody HotelAssetRequest request, Principal principal) {
    return hotel.saveAsset(users.current(principal), null, request);
  }

  @PutMapping("/assets/{id}")
  public HotelAsset updateAsset(
      @PathVariable UUID id, @Valid @RequestBody HotelAssetRequest request, Principal principal) {
    return hotel.saveAsset(users.current(principal), id, request);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public FoodOrder sell(@Valid @RequestBody OrderRequest request, Principal principal) {
    return hotel.sell(users.current(principal), request);
  }

  @GetMapping("/orders/{id}")
  public FoodOrder order(@PathVariable UUID id, Principal principal) {
    return hotel.order(users.current(principal), id);
  }

  @PostMapping("/orders/{id}/payment")
  public FoodOrder settleOrder(
      @PathVariable UUID id, @Valid @RequestBody OrderPaymentRequest request, Principal principal) {
    return hotel.settleOrder(users.current(principal), id, request);
  }

  @PostMapping("/orders/{id}/cancel")
  public FoodOrder cancelOrder(@PathVariable UUID id, Principal principal) {
    return hotel.cancelOrder(users.current(principal), id);
  }

  @GetMapping("/kitchen")
  public List<KitchenTicket> kitchen(@RequestParam UUID branchId, Principal principal) {
    return hotel.kitchenOrders(users.current(principal), branchId);
  }

  @PostMapping("/orders/{id}/preparations")
  public KitchenTicket prepareOrder(
      @PathVariable UUID id,
      @Valid @RequestBody KitchenPreparationRequest request,
      Principal principal) {
    return hotel.prepareOrder(users.current(principal), id, request);
  }

  @GetMapping("/reports")
  public Report report(
      @RequestParam UUID branchId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      Principal principal) {
    return hotel.report(users.current(principal), branchId, from, to);
  }
}
