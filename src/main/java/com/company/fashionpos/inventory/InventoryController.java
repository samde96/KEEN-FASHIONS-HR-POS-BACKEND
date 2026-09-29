package com.company.fashionpos.inventory;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

  private final InventoryService inventoryService;
  private final AuthenticatedUserService authenticatedUserService;

  public InventoryController(
      InventoryService inventoryService, AuthenticatedUserService authenticatedUserService) {
    this.inventoryService = inventoryService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/inventory")
  public List<InventoryResponse> listInventory(Principal principal) {
    return inventoryService.listInventory(authenticatedUserService.current(principal));
  }

  @PutMapping("/inventory/{inventoryItemId}/reorder-level")
  public InventoryResponse updateReorderLevel(
      @PathVariable UUID inventoryItemId,
      @Valid @RequestBody InventoryReorderLevelRequest request,
      Principal principal) {
    return inventoryService.updateReorderLevel(
        authenticatedUserService.current(principal), inventoryItemId, request);
  }

  @GetMapping("/stock-intakes")
  public List<StockIntakeResponse> listStockIntakes(Principal principal) {
    return inventoryService.listStockIntakes(authenticatedUserService.current(principal));
  }

  @GetMapping("/stock-adjustments")
  public List<StockAdjustmentResponse> listStockAdjustments(Principal principal) {
    return inventoryService.listStockAdjustments(authenticatedUserService.current(principal));
  }

  @GetMapping("/stock-adjustments/summary")
  public StockAdjustmentSummaryResponse stockAdjustmentSummary(Principal principal) {
    return inventoryService.stockAdjustmentSummary(authenticatedUserService.current(principal));
  }

  @PostMapping("/stock-adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  public StockAdjustmentResponse adjustStock(
      @Valid @RequestBody StockAdjustmentRequest request, Principal principal) {
    return inventoryService.adjustStock(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/stock-intakes")
  @ResponseStatus(HttpStatus.CREATED)
  public StockIntakeResponse addStock(
      @Valid @RequestBody StockIntakeRequest request, Principal principal) {
    return inventoryService.addStock(authenticatedUserService.current(principal), request);
  }

  @GetMapping("/transfers")
  public List<StockTransferResponse> listTransfers(Principal principal) {
    return inventoryService.listTransfers(authenticatedUserService.current(principal));
  }

  @PostMapping("/transfers")
  @ResponseStatus(HttpStatus.CREATED)
  public StockTransferResponse transferStock(
      @Valid @RequestBody StockTransferRequest request, Principal principal) {
    return inventoryService.transferStock(authenticatedUserService.current(principal), request);
  }
}
