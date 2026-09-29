package com.company.fashionpos.inventory;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.catalog.ProductItem;
import com.company.fashionpos.catalog.ProductRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final StockAdjustmentRepository stockAdjustmentRepository;
  private final StockIntakeRepository stockIntakeRepository;
  private final StockTransferRepository stockTransferRepository;
  private final BranchRepository branchRepository;
  private final BranchService branchService;
  private final ProductRepository productRepository;

  public InventoryService(
      InventoryRepository inventoryRepository,
      StockAdjustmentRepository stockAdjustmentRepository,
      StockIntakeRepository stockIntakeRepository,
      StockTransferRepository stockTransferRepository,
      BranchRepository branchRepository,
      BranchService branchService,
      ProductRepository productRepository) {
    this.inventoryRepository = inventoryRepository;
    this.stockAdjustmentRepository = stockAdjustmentRepository;
    this.stockIntakeRepository = stockIntakeRepository;
    this.stockTransferRepository = stockTransferRepository;
    this.branchRepository = branchRepository;
    this.branchService = branchService;
    this.productRepository = productRepository;
  }

  @Transactional(readOnly = true)
  public List<InventoryResponse> listInventory(AuthenticatedUser user) {
    return inventoryRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(item -> canAccessBranch(user, item.getBranch().getId()))
        .map(InventoryResponse::from)
        .toList();
  }

  @Transactional
  public InventoryResponse updateReorderLevel(
      AuthenticatedUser user, UUID inventoryItemId, InventoryReorderLevelRequest request) {
    InventoryItem item =
        inventoryRepository
            .findWithinOrganization(inventoryItemId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Inventory item not found"));
    branchService.requireBranchAccess(user, item.getBranch().getId());
    item.setReorderLevel(request.reorderLevel());
    return InventoryResponse.from(inventoryRepository.save(item));
  }

  @Transactional(readOnly = true)
  public List<StockAdjustmentResponse> listStockAdjustments(AuthenticatedUser user) {
    return stockAdjustmentRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(adjustment -> canAccessBranch(user, adjustment.getBranch().getId()))
        .map(StockAdjustmentResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public StockAdjustmentSummaryResponse stockAdjustmentSummary(AuthenticatedUser user) {
    List<StockAdjustment> adjustments =
        stockAdjustmentRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(adjustment -> canAccessBranch(user, adjustment.getBranch().getId()))
            .toList();
    return StockAdjustmentSummaryResponse.from(adjustments);
  }

  @Transactional
  public StockAdjustmentResponse adjustStock(
      AuthenticatedUser user, StockAdjustmentRequest request) {
    branchService.requireBranchAccess(user, request.branchId());

    ProductItem product = requireProduct(user, request.productId());
    Branch branch = requireBranch(user, request.branchId());
    InventoryItem item = getOrCreateInventory(product.getOrganization(), branch, product);
    int systemQuantity = item.getQuantityOnHand();
    int countedQuantity = request.countedQuantity();
    int varianceQuantity = countedQuantity - systemQuantity;
    BigDecimal unitCost = money(product.getCostPrice());
    BigDecimal varianceValue =
        money(unitCost.multiply(BigDecimal.valueOf(Math.abs(varianceQuantity))));
    BigDecimal lossValue = varianceQuantity < 0 ? varianceValue : zeroMoney();
    BigDecimal excessValue = varianceQuantity > 0 ? varianceValue : zeroMoney();

    item.reconcileToPhysicalCount(countedQuantity);
    inventoryRepository.save(item);

    StockAdjustment adjustment =
        new StockAdjustment(
            UUID.randomUUID(),
            product.getOrganization(),
            branch,
            product,
            user.userId(),
            systemQuantity,
            countedQuantity,
            varianceQuantity,
            unitCost,
            lossValue,
            excessValue,
            normalizeRequired(request.reason(), "Reason"),
            normalizeOptional(request.notes()));
    return StockAdjustmentResponse.from(stockAdjustmentRepository.save(adjustment));
  }

  @Transactional(readOnly = true)
  public List<StockIntakeResponse> listStockIntakes(AuthenticatedUser user) {
    return stockIntakeRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(intake -> canAccessBranch(user, intake.getBranch().getId()))
        .map(StockIntakeResponse::from)
        .toList();
  }

  @Transactional
  public StockIntakeResponse addStock(AuthenticatedUser user, StockIntakeRequest request) {
    requirePositive(request.quantity());
    branchService.requireBranchAccess(user, request.branchId());

    ProductItem product = requireProduct(user, request.productId());
    Branch branch = requireBranch(user, request.branchId());
    BigDecimal unitCost =
        request.unitCost() == null
            ? product.getCostPrice()
            : optionalNonNegative(request.unitCost(), "Unit cost");
    InventoryItem item = getOrCreateInventory(product.getOrganization(), branch, product);
    item.addStock(request.quantity());
    inventoryRepository.save(item);

    StockIntake intake =
        new StockIntake(
            UUID.randomUUID(),
            product.getOrganization(),
            branch,
            product,
            normalizeRequired(request.supplierName(), "Supplier name"),
            normalizeOptional(request.referenceNumber()),
            request.quantity(),
            money(unitCost),
            normalizeOptional(request.notes()));
    return StockIntakeResponse.from(stockIntakeRepository.save(intake));
  }

  @Transactional(readOnly = true)
  public List<StockTransferResponse> listTransfers(AuthenticatedUser user) {
    return stockTransferRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(
            transfer ->
                canAccessBranch(user, transfer.getSourceBranch().getId())
                    || canAccessBranch(user, transfer.getDestinationBranch().getId()))
        .map(StockTransferResponse::from)
        .toList();
  }

  @Transactional
  public StockTransferResponse transferStock(AuthenticatedUser user, StockTransferRequest request) {
    requirePositive(request.quantity());
    if (request.sourceBranchId().equals(request.destinationBranchId())) {
      throw new IllegalArgumentException("Source and destination branches must be different");
    }
    branchService.requireBranchAccess(user, request.sourceBranchId());
    branchService.requireBranchAccess(user, request.destinationBranchId());

    ProductItem product = requireProduct(user, request.productId());
    Branch sourceBranch = requireBranch(user, request.sourceBranchId());
    Branch destinationBranch = requireBranch(user, request.destinationBranchId());
    InventoryItem sourceItem =
        inventoryRepository
            .findForMutation(user.organizationId(), sourceBranch.getId(), product.getId())
            .orElseThrow(
                () -> new IllegalArgumentException("Source branch does not have this product"));
    InventoryItem destinationItem =
        getOrCreateInventory(product.getOrganization(), destinationBranch, product);

    sourceItem.removeStock(request.quantity());
    destinationItem.addStock(request.quantity());
    inventoryRepository.save(sourceItem);
    inventoryRepository.save(destinationItem);

    StockTransfer transfer =
        new StockTransfer(
            UUID.randomUUID(),
            product.getOrganization(),
            sourceBranch,
            destinationBranch,
            product,
            request.quantity(),
            normalizeOptional(request.referenceNumber()),
            normalizeOptional(request.notes()));
    return StockTransferResponse.from(stockTransferRepository.save(transfer));
  }

  private InventoryItem getOrCreateInventory(
      Organization organization, Branch branch, ProductItem product) {
    return inventoryRepository
        .findForMutation(organization.getId(), branch.getId(), product.getId())
        .orElseGet(() -> new InventoryItem(UUID.randomUUID(), organization, branch, product));
  }

  private ProductItem requireProduct(AuthenticatedUser user, UUID productId) {
    return productRepository
        .findWithinOrganization(productId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));
  }

  private Branch requireBranch(AuthenticatedUser user, UUID branchId) {
    return branchRepository
        .findWithinOrganization(branchId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
  }

  private static boolean canAccessBranch(AuthenticatedUser user, UUID branchId) {
    return user.branchIds().isEmpty() || user.branchIds().contains(branchId);
  }

  private static void requirePositive(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be greater than zero");
    }
  }

  private static BigDecimal optionalNonNegative(BigDecimal value, String label) {
    if (value == null) {
      return null;
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(label + " cannot be negative");
    }
    return value;
  }

  private static BigDecimal money(BigDecimal value) {
    if (value == null) {
      return zeroMoney();
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal zeroMoney() {
    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  }

  private static String normalizeRequired(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
