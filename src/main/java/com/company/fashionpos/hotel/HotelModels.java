package com.company.fashionpos.hotel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HotelModels {
  private HotelModels() {}

  public record RoomRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 30) String number,
      @NotBlank @Size(max = 80) String roomType,
      @Min(1) @Max(100) int capacity,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal nightlyRate,
      @NotNull @Pattern(regexp = "READY|CLEANING|MAINTENANCE") String status) {}

  public record BookingRequest(
      @NotNull UUID requestId,
      @NotNull UUID branchId,
      @NotNull UUID roomId,
      @NotBlank @Size(max = 160) String guestName,
      @Size(max = 40) String guestPhone,
      @Email @Size(max = 254) String guestEmail,
      @Min(1) @Max(100) int guests,
      @NotNull LocalDate arrival,
      @NotNull LocalDate departure,
      @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal nightlyRate,
      @Size(max = 1000) String notes) {}

  public record PaymentRequest(
      @NotNull UUID requestId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull @Pattern(regexp = "PAYMENT|REFUND") String kind,
      @NotNull @Pattern(regexp = "CASH|MPESA|CARD|BANK") String method,
      @Size(max = 100) String reference) {}

  public record ItemRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String name,
      @NotNull @Pattern(regexp = "PRODUCT|INGREDIENT|SUPPLY") String category,
      @NotBlank @Size(max = 30) String unit,
      boolean sellable,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
      @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal reorderLevel,
      boolean active) {}

  public record MenuItemRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 80) String category,
      @Size(max = 500) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal price,
      boolean available) {}

  public record StockRequest(
      @NotNull UUID requestId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
      @NotNull @Pattern(regexp = "RECEIPT|USAGE") String kind,
      @NotBlank @Size(max = 500) String note,
      @DecimalMin("0.0001") @Digits(integer = 12, fraction = 4) BigDecimal unitCost) {
    public StockRequest(UUID requestId, BigDecimal quantity, String kind, String note) {
      this(requestId, quantity, kind, note, null);
    }
  }

  public record RecipeIngredientRequest(
      @NotNull UUID itemId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
      @NotBlank @Size(max = 30) String unit,
      boolean optional) {}

  public record RecipeRequest(
      @NotNull UUID requestId,
      @NotBlank @Size(max = 80) String servingLabel,
      @NotEmpty @Size(max = 100) List<@Valid RecipeIngredientRequest> ingredients) {}

  public record HotelExpenseRequest(
      @NotNull UUID requestId,
      @NotNull UUID branchId,
      @NotNull LocalDate expenseDate,
      @NotNull
          @Pattern(
              regexp = "FOOD_SUPPLIES|UTILITIES|RENT|REPAIRS_MAINTENANCE|PAYROLL|TRANSPORT|OTHER")
          String category,
      @NotBlank @Size(max = 160) String description,
      @Size(max = 160) String vendor,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull @Pattern(regexp = "CASH|MPESA|CARD|BANK|OTHER") String paymentMethod,
      @Size(max = 100) String reference,
      @Size(max = 1000) String notes) {}

  public record HotelExpense(
      UUID id,
      UUID branchId,
      LocalDate expenseDate,
      String category,
      String description,
      String vendor,
      BigDecimal amount,
      String paymentMethod,
      String reference,
      String notes,
      Instant createdAt) {}

  public record HotelAssetRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String name,
      @NotNull @Pattern(regexp = "EQUIPMENT|FURNITURE|ELECTRONICS|OTHER") String category,
      @Min(1) @Max(100000) int quantity,
      @NotNull LocalDate purchaseDate,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal purchaseCost,
      @Size(max = 160) String location,
      @NotNull @Pattern(regexp = "ACTIVE|NEEDS_REPAIR|RETIRED") String status,
      @Size(max = 1000) String notes) {}

  public record HotelAsset(
      UUID id,
      UUID branchId,
      String name,
      String category,
      int quantity,
      LocalDate purchaseDate,
      BigDecimal purchaseCost,
      String location,
      String status,
      String notes) {}

  public record OrderLineRequest(
      @NotNull UUID itemId,
      @NotNull @Pattern(regexp = "FOOD|PRODUCT") String itemType,
      @NotNull @DecimalMin("0.001") @Digits(integer = 8, fraction = 3) BigDecimal quantity) {}

  public record OrderRequest(
      @NotNull UUID requestId,
      @NotNull UUID branchId,
      @NotNull UUID waiterId,
      UUID bookingId,
      @Size(max = 160) String customerName,
      @NotNull @Pattern(regexp = "UNPAID|CASH|MPESA|CARD|BANK|ROOM") String method,
      @Size(max = 100) String paymentReference,
      @NotNull @Pattern(regexp = "DINE_IN|TAKEAWAY|DELIVERY") String serviceType,
      @Size(max = 30) String tableNumber,
      @Size(max = 1000) String notes,
      @NotEmpty @Size(max = 100) List<@Valid OrderLineRequest> lines) {}

  public record OrderPaymentRequest(
      @NotNull UUID requestId,
      @NotNull @Pattern(regexp = "CASH|MPESA|CARD|BANK|ROOM") String method,
      UUID bookingId,
      @Size(max = 100) String reference) {}

  public record KitchenPreparationLineRequest(
      @NotNull UUID lineId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {}

  public record KitchenPreparationRequest(
      @NotNull UUID requestId,
      @NotEmpty @Size(max = 100) List<@Valid KitchenPreparationLineRequest> lines) {}

  public record WaiterRequest(
      @NotNull UUID branchId, @Size(max = 160) String name, UUID userId, boolean active) {
    public WaiterRequest(UUID branchId, String name, boolean active) {
      this(branchId, name, null, active);
    }
  }

  public record Waiter(UUID id, UUID branchId, String name, boolean active, UUID userId) {}

  public record ReceiptSettingsRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 160) String hotelName,
      @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9 ]+") String tillNumber,
      @NotBlank @Size(max = 40) String contactPhone) {}

  public record ReceiptSettings(String hotelName, String tillNumber, String contactPhone) {}

  public record Room(
      UUID id,
      UUID branchId,
      String number,
      String roomType,
      int capacity,
      BigDecimal nightlyRate,
      String status) {}

  public record Payment(
      UUID id,
      UUID bookingId,
      BigDecimal amount,
      String kind,
      String method,
      String reference,
      Instant createdAt) {}

  public record Booking(
      UUID id,
      UUID branchId,
      UUID roomId,
      String roomNumber,
      String reference,
      String guestName,
      String guestPhone,
      String guestEmail,
      int guests,
      LocalDate arrival,
      LocalDate departure,
      long nights,
      BigDecimal nightlyRate,
      BigDecimal roomTotal,
      BigDecimal foodTotal,
      BigDecimal total,
      BigDecimal paid,
      BigDecimal balance,
      String status,
      String notes,
      Instant checkedInAt,
      Instant checkedOutAt,
      List<Payment> payments) {}

  public record Item(
      UUID id,
      UUID branchId,
      String name,
      String category,
      String unit,
      boolean sellable,
      BigDecimal price,
      BigDecimal unitCost,
      boolean unitCostEstimated,
      BigDecimal stock,
      BigDecimal reorderLevel,
      boolean active) {}

  public record MenuItem(
      UUID id,
      UUID branchId,
      String name,
      String category,
      String description,
      BigDecimal price,
      boolean available) {}

  public record RecipeIngredient(
      UUID itemId,
      String itemName,
      BigDecimal quantity,
      String unit,
      String stockUnit,
      BigDecimal unitCost,
      BigDecimal estimatedCost,
      boolean estimated,
      boolean optional) {}

  public record Recipe(
      UUID id,
      UUID menuItemId,
      String menuItemName,
      BigDecimal sellingPrice,
      int version,
      boolean current,
      String servingLabel,
      BigDecimal estimatedCost,
      BigDecimal contribution,
      BigDecimal ingredientCostPercent,
      List<RecipeIngredient> ingredients,
      Instant createdAt) {}

  public record OrderLine(
      UUID lineId,
      UUID itemId,
      String itemType,
      String itemName,
      BigDecimal quantity,
      BigDecimal preparedQuantity,
      boolean recipeConfigured,
      BigDecimal unitPrice,
      BigDecimal total) {}

  public record KitchenOrderLine(
      UUID lineId,
      String itemName,
      BigDecimal quantity,
      BigDecimal preparedQuantity,
      boolean recipeConfigured) {}

  public record KitchenTicket(
      UUID id,
      UUID branchId,
      String reference,
      String tableNumber,
      String serviceType,
      String notes,
      String status,
      Instant createdAt,
      List<KitchenOrderLine> lines) {}

  public record FoodOrder(
      UUID id,
      UUID branchId,
      UUID bookingId,
      String roomNumber,
      String reference,
      String customerName,
      BigDecimal total,
      String method,
      String paymentReference,
      UUID operatorId,
      UUID waiterId,
      String servedByName,
      String tableNumber,
      String serviceType,
      String notes,
      String status,
      String kitchenStatus,
      String hotelName,
      String tillNumber,
      String contactPhone,
      Instant paidAt,
      Instant createdAt,
      List<OrderLine> lines) {}

  public record SalesHistoryOrder(
      UUID id,
      UUID branchId,
      String branchName,
      String reference,
      String customerName,
      BigDecimal total,
      String method,
      String status,
      Instant createdAt,
      List<OrderLine> lines) {}

  public record GuestOption(UUID id, String roomNumber, String guestName) {}

  public record Checkout(
      LocalDate businessDate,
      List<MenuItem> menuItems,
      List<Item> products,
      List<GuestOption> guests,
      List<FoodOrder> orders,
      List<Waiter> waiters,
      ReceiptSettings receiptSettings) {}

  public record StockMovement(
      UUID id,
      UUID itemId,
      String itemName,
      BigDecimal quantity,
      BigDecimal balance,
      String kind,
      String note,
      BigDecimal unitCost,
      BigDecimal costValue,
      Instant createdAt) {}

  public record Workspace(
      LocalDate businessDate,
      List<Room> rooms,
      List<Booking> bookings,
      List<Item> items,
      List<MenuItem> menuItems,
      List<FoodOrder> orders,
      List<StockMovement> movements) {}

  public record DailyRevenue(
      LocalDate date,
      BigDecimal accommodation,
      BigDecimal food,
      BigDecimal products,
      BigDecimal payments,
      BigDecimal expenses) {}

  public record ExpenseCategoryTotal(String category, BigDecimal amount) {}

  public record RoomPerformance(
      String roomNumber, long completedStays, long soldNights, BigDecimal revenue) {}

  public record Report(
      LocalDate from,
      LocalDate to,
      BigDecimal roomRevenue,
      BigDecimal foodRevenue,
      BigDecimal productRevenue,
      BigDecimal totalRevenue,
      BigDecimal totalExpenses,
      BigDecimal profitLoss,
      BigDecimal paymentsReceived,
      BigDecimal outstandingBalance,
      long completedStays,
      long soldNights,
      long bookedRoomNights,
      long availableRoomNights,
      BigDecimal bookedOccupancyRate,
      BigDecimal averageDailyRate,
      List<DailyRevenue> daily,
      List<RoomPerformance> rooms,
      List<ExpenseCategoryTotal> expensesByCategory) {}
}
