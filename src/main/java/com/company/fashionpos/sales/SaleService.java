package com.company.fashionpos.sales;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.catalog.ProductItem;
import com.company.fashionpos.catalog.ProductRepository;
import com.company.fashionpos.catalog.VatCategory;
import com.company.fashionpos.inventory.InventoryItem;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.mpesa.MpesaLedger;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.shifts.CashierShiftService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

  private static final DateTimeFormatter SALE_NUMBER_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final SaleRepository saleRepository;
  private final SaleLineRepository saleLineRepository;
  private final SalePaymentRepository salePaymentRepository;
  private final BranchRepository branchRepository;
  private final BranchService branchService;
  private final ProductRepository productRepository;
  private final InventoryRepository inventoryRepository;
  private final OrganizationRepository organizationRepository;
  private final CashierShiftService cashierShifts;
  private final MpesaLedger mpesa;

  public SaleService(
      SaleRepository saleRepository,
      SaleLineRepository saleLineRepository,
      SalePaymentRepository salePaymentRepository,
      BranchRepository branchRepository,
      BranchService branchService,
      ProductRepository productRepository,
      InventoryRepository inventoryRepository,
      OrganizationRepository organizationRepository,
      CashierShiftService cashierShifts,
      MpesaLedger mpesa) {
    this.saleRepository = saleRepository;
    this.saleLineRepository = saleLineRepository;
    this.salePaymentRepository = salePaymentRepository;
    this.branchRepository = branchRepository;
    this.branchService = branchService;
    this.productRepository = productRepository;
    this.inventoryRepository = inventoryRepository;
    this.organizationRepository = organizationRepository;
    this.cashierShifts = cashierShifts;
    this.mpesa = mpesa;
  }

  @Transactional(readOnly = true)
  public List<SaleResponse> listSales(
      AuthenticatedUser user, LocalDate fromDate, LocalDate toDate) {
    SalePeriod period = normalizeSalePeriod(user, fromDate, toDate);
    List<Sale> sales =
        period == null
            ? saleRepository.findWithinOrganization(user.organizationId())
            : saleRepository.findWithinOrganizationAndPeriod(
                user.organizationId(), period.fromInclusive(), period.toExclusive());
    return sales.stream()
        .filter(sale -> canAccessBranch(user, sale.getBranch().getId()))
        .map(sale -> toSaleResponse(user, sale))
        .toList();
  }

  @Transactional(readOnly = true)
  public SaleResponse getSale(AuthenticatedUser user, UUID saleId) {
    Sale sale =
        saleRepository
            .findWithinOrganization(saleId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Sale not found"));
    if (!canAccessBranch(user, sale.getBranch().getId())) {
      throw new EntityNotFoundException("Sale not found");
    }
    return toSaleResponse(user, sale);
  }

  @Transactional
  public SaleResponse createSale(
      AuthenticatedUser user, String idempotencyKey, SaleRequest request) {
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    cashierShifts.lockOperator(user);
    return saleRepository
        .findByIdempotencyKeyWithinOrganization(user.organizationId(), normalizedIdempotencyKey)
        .map(
            sale -> {
              branchService.requireBranchAccess(user, sale.getBranch().getId());
              return toSaleResponse(user, sale);
            })
        .orElseGet(() -> createNewSale(user, normalizedIdempotencyKey, request));
  }

  private SaleResponse createNewSale(
      AuthenticatedUser user, String idempotencyKey, SaleRequest request) {
    branchService.requireBranchAccess(user, request.branchId());
    Branch branch = requireBranch(user, request.branchId());
    Map<UUID, Integer> requestedQuantities = requestedQuantities(request.lines());
    if (requestedQuantities.isEmpty()) {
      throw new IllegalArgumentException("Sale must include at least one product");
    }

    BigDecimal subtotal = BigDecimal.ZERO;
    Map<ProductItem, Integer> productsToSell = new LinkedHashMap<>();
    Map<ProductItem, BigDecimal> lineTotals = new LinkedHashMap<>();

    for (Map.Entry<UUID, Integer> requestedLine :
        requestedQuantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
      ProductItem product = requireActiveProduct(user, requestedLine.getKey());
      int quantity = requestedLine.getValue();
      InventoryItem inventoryItem = requireAvailableInventory(user, branch, product, quantity);

      inventoryItem.removeStock(quantity);
      inventoryRepository.save(inventoryItem);

      BigDecimal unitPrice = money(product.getUnitPrice());
      BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
      subtotal = subtotal.add(lineTotal);
      productsToSell.put(product, quantity);
      lineTotals.put(product, lineTotal);
    }

    subtotal = money(subtotal);
    BigDecimal discount = optionalMoney(request.discountAmount());
    if (discount.compareTo(subtotal) > 0) {
      throw new IllegalArgumentException("Discount cannot exceed subtotal");
    }
    BigDecimal netAmount = money(subtotal.subtract(discount));
    Map<ProductItem, BigDecimal> lineTaxes =
        lineTaxes(productsToSell, lineTotals, subtotal, discount);
    BigDecimal tax = money(lineTaxes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal total = money(netAmount.add(tax));
    List<NormalizedPayment> payments = payments(request, total);

    Sale sale =
        saleRepository.save(
            new Sale(
                UUID.randomUUID(),
                branch.getOrganization(),
                branch,
                nextSaleNumber(),
                idempotencyKey,
                normalizeOptional(request.customerName()),
                subtotal,
                discount,
                tax,
                total));

    sale.setCashierId(user.userId());
    saleRepository.flush();
    mpesa.consume(user, idempotencyKey, request, sale.getId());
    for (Map.Entry<ProductItem, Integer> line : productsToSell.entrySet()) {
      ProductItem product = line.getKey();
      saleLineRepository.save(
          new SaleLine(
              UUID.randomUUID(),
              branch.getOrganization(),
              sale,
              product,
              line.getValue(),
              money(product.getUnitPrice()),
              money(product.getCostPrice()),
              lineTotals.get(product),
              vatCategoryOrStandard(product.getVatCategory()),
              vatCategoryOrStandard(product.getVatCategory()).rate(),
              lineTaxes.get(product)));
    }

    for (NormalizedPayment payment : payments) {
      salePaymentRepository.save(
          new SalePayment(
              UUID.randomUUID(),
              branch.getOrganization(),
              branch,
              sale,
              payment.method(),
              payment.amount(),
              payment.reference(),
              payment.cashReceived(),
              payment.changeDue()));
    }

    return toSaleResponse(user, sale);
  }

  /** Prices and VAT come from the catalog; the browser cannot choose the STK charge. */
  @Transactional
  public BigDecimal quoteMpesaAmount(AuthenticatedUser user, SaleRequest request) {
    branchService.requireBranchAccess(user, request.branchId());
    Branch branch = requireBranch(user, request.branchId());
    if (!"KES".equals(branch.getOrganization().getCurrencyCode()))
      throw new IllegalArgumentException("M-Pesa payments require a KES business");
    Map<UUID, Integer> quantities = requestedQuantities(request.lines());
    if (quantities.isEmpty() || quantities.size() > 200)
      throw new IllegalArgumentException("Select between 1 and 200 sale items");
    Map<ProductItem, Integer> products = new LinkedHashMap<>();
    Map<ProductItem, BigDecimal> totals = new LinkedHashMap<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    for (var line : quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
      ProductItem product = requireActiveProduct(user, line.getKey());
      requireAvailableInventory(user, branch, product, line.getValue());
      BigDecimal lineTotal =
          money(money(product.getUnitPrice()).multiply(BigDecimal.valueOf(line.getValue())));
      products.put(product, line.getValue());
      totals.put(product, lineTotal);
      subtotal = subtotal.add(lineTotal);
    }
    subtotal = money(subtotal);
    BigDecimal discount = optionalMoney(request.discountAmount());
    if (discount.compareTo(subtotal) > 0)
      throw new IllegalArgumentException("Discount cannot exceed subtotal");
    BigDecimal tax =
        lineTaxes(products, totals, subtotal, discount).values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal total = money(subtotal.subtract(discount).add(tax));
    var tenders = payments(request, total);
    if (request.payments() == null
        || tenders.stream().filter(p -> p.method() == PaymentMethod.MPESA).count() != 1)
      throw new IllegalArgumentException("Use exactly one M-Pesa tender per checkout");
    return tenders.stream()
        .filter(p -> p.method() == PaymentMethod.MPESA)
        .findFirst()
        .orElseThrow()
        .amount();
  }

  private ProductItem requireActiveProduct(AuthenticatedUser user, UUID productId) {
    ProductItem product =
        productRepository
            .findForMutation(productId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    if (!product.isSellable()) {
      throw new IllegalArgumentException("Select an active simple product or exact variant");
    }
    return product;
  }

  private InventoryItem requireAvailableInventory(
      AuthenticatedUser user, Branch branch, ProductItem product, int quantity) {
    InventoryItem inventoryItem =
        inventoryRepository
            .findForMutation(user.organizationId(), branch.getId(), product.getId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        product.getName() + " has no stock at " + branch.getName()));

    if (inventoryItem.availableQuantity() < quantity) {
      throw new IllegalArgumentException(
          "%s has only %d units available at %s"
              .formatted(product.getName(), inventoryItem.availableQuantity(), branch.getName()));
    }
    return inventoryItem;
  }

  private Branch requireBranch(AuthenticatedUser user, UUID branchId) {
    return branchRepository
        .findWithinOrganization(branchId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
  }

  private SaleResponse toSaleResponse(AuthenticatedUser user, Sale sale) {
    return SaleResponse.from(
        sale,
        saleLineRepository.findForSale(sale.getId()),
        salePaymentRepository.findForSale(sale.getId()),
        user.canViewProfit());
  }

  private static Map<UUID, Integer> requestedQuantities(List<SaleLineRequest> lines) {
    Map<UUID, Integer> requestedQuantities = new LinkedHashMap<>();
    if (lines == null) {
      return requestedQuantities;
    }

    for (SaleLineRequest line : lines) {
      if (line.productId() == null || line.quantity() <= 0) {
        throw new IllegalArgumentException("Sale line quantities must be greater than zero");
      }
      requestedQuantities.merge(line.productId(), line.quantity(), Math::addExact);
    }
    return requestedQuantities;
  }

  private static List<NormalizedPayment> payments(SaleRequest request, BigDecimal total) {
    if (request.payments() != null && !request.payments().isEmpty()) {
      List<NormalizedPayment> payments =
          request.payments().stream().map(SaleService::normalizedPayment).toList();
      BigDecimal paid =
          payments.stream().map(NormalizedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
      if (money(paid).compareTo(total) != 0) {
        throw new IllegalArgumentException("Split payment amounts must equal the sale total");
      }
      return payments;
    }

    if (request.paymentMethod() == null) {
      throw new IllegalArgumentException("Select a payment method before completing the sale");
    }
    return List.of(
        normalizedPayment(
            new SalePaymentRequest(
                request.paymentMethod(),
                total,
                request.paymentReference(),
                request.cashReceived())));
  }

  private static NormalizedPayment normalizedPayment(SalePaymentRequest payment) {
    if (payment.method() == null) {
      throw new IllegalArgumentException("Select a payment method before completing the sale");
    }

    BigDecimal amount = optionalMoney(payment.amount());
    if (amount.signum() == 0) {
      throw new IllegalArgumentException("Payment amounts must be greater than zero");
    }

    BigDecimal cashReceived = null;
    BigDecimal changeDue = null;
    if (payment.method() == PaymentMethod.CASH) {
      cashReceived =
          payment.cashReceived() == null ? amount : optionalMoney(payment.cashReceived());
      if (cashReceived.compareTo(amount) < 0) {
        throw new IllegalArgumentException(
            "Cash received cannot be less than the cash payment amount");
      }
      changeDue = money(cashReceived.subtract(amount));
    }

    return new NormalizedPayment(
        payment.method(),
        amount,
        normalizeOptional(payment.paymentReference()),
        cashReceived,
        changeDue);
  }

  private static BigDecimal optionalMoney(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException("Amounts cannot be negative");
    }
    return money(value);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key header is required");
    }
    String normalized = value.trim();
    if (normalized.length() > 120) {
      throw new IllegalArgumentException("Idempotency-Key header must be 120 characters or less");
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String nextSaleNumber() {
    String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    return "SALE-" + SALE_NUMBER_TIMESTAMP.format(Instant.now()) + "-" + suffix;
  }

  private static boolean canAccessBranch(AuthenticatedUser user, UUID branchId) {
    return user.branchIds().isEmpty() || user.branchIds().contains(branchId);
  }

  private SalePeriod normalizeSalePeriod(
      AuthenticatedUser user, LocalDate fromDate, LocalDate toDate) {
    if (fromDate == null && toDate == null) {
      return null;
    }
    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    LocalDate normalizedFrom = fromDate == null ? toDate : fromDate;
    LocalDate normalizedTo = toDate == null ? normalizedFrom : toDate;
    if (normalizedTo.isBefore(normalizedFrom)) {
      throw new IllegalArgumentException("To date cannot be before from date");
    }
    ZoneId zoneId = ZoneId.of(organization.getTimeZone());
    return new SalePeriod(
        normalizedFrom.atStartOfDay(zoneId).toInstant(),
        normalizedTo.plusDays(1).atStartOfDay(zoneId).toInstant());
  }

  private record SalePeriod(Instant fromInclusive, Instant toExclusive) {}

  private static Map<ProductItem, BigDecimal> lineTaxes(
      Map<ProductItem, Integer> productsToSell,
      Map<ProductItem, BigDecimal> lineTotals,
      BigDecimal subtotal,
      BigDecimal discount) {
    Map<ProductItem, BigDecimal> lineTaxes = new LinkedHashMap<>();
    for (ProductItem product : productsToSell.keySet()) {
      BigDecimal lineTotal = lineTotals.get(product);
      BigDecimal lineDiscount = proportionalDiscount(lineTotal, subtotal, discount);
      BigDecimal taxableBase = money(lineTotal.subtract(lineDiscount));
      BigDecimal tax =
          money(taxableBase.multiply(vatCategoryOrStandard(product.getVatCategory()).rate()));
      lineTaxes.put(product, tax);
    }
    return lineTaxes;
  }

  private static BigDecimal proportionalDiscount(
      BigDecimal lineTotal, BigDecimal subtotal, BigDecimal discount) {
    if (discount.signum() == 0 || subtotal.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return discount.multiply(lineTotal).divide(subtotal, 6, RoundingMode.HALF_UP);
  }

  private static VatCategory vatCategoryOrStandard(VatCategory vatCategory) {
    return vatCategory == null ? VatCategory.A : vatCategory;
  }

  private record NormalizedPayment(
      PaymentMethod method,
      BigDecimal amount,
      String reference,
      BigDecimal cashReceived,
      BigDecimal changeDue) {}
}
