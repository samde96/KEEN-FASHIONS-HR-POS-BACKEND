package com.company.fashionpos.operations;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PosOperationsController {
  private final AuthenticatedUserService users;
  private final PosPreferencesService preferences;
  private final StockOperationService stock;
  private final DailySalesService reports;
  private final SaleReversalService reversals;

  public PosOperationsController(
      AuthenticatedUserService users,
      PosPreferencesService preferences,
      StockOperationService stock,
      DailySalesService reports,
      SaleReversalService reversals) {
    this.users = users;
    this.preferences = preferences;
    this.stock = stock;
    this.reports = reports;
    this.reversals = reversals;
  }

  @GetMapping("/pos-preferences")
  public PosPreferences preferences(Principal p) {
    return preferences.get(users.current(p).organizationId());
  }

  @PutMapping("/pos-preferences")
  public PosPreferences preferences(@Valid @RequestBody PosPreferences request, Principal p) {
    return preferences.save(users.current(p), request);
  }

  @GetMapping("/stock-operations")
  public List<StockOperationService.Operation> drafts(Principal p) {
    return stock.list(users.current(p));
  }

  @PutMapping("/stock-operations/{id}")
  public StockOperationService.Operation save(
      @PathVariable UUID id,
      @Valid @RequestBody StockOperationService.DraftRequest request,
      Principal p) {
    return stock.save(users.current(p), id, request);
  }

  @PostMapping("/stock-operations/{id}/complete")
  public StockOperationService.Operation complete(@PathVariable UUID id, Principal p) {
    return stock.complete(users.current(p), id);
  }

  @DeleteMapping("/stock-operations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void discard(@PathVariable UUID id, Principal p) {
    stock.discard(users.current(p), id);
  }

  @GetMapping("/daily-sales-reports")
  public List<DailySalesReport> history(@RequestParam UUID branchId, Principal p) {
    return reports.history(users.current(p), branchId);
  }

  @PostMapping("/daily-sales-reports/generate")
  public DailySalesReport generate(
      @RequestParam UUID branchId, @RequestParam LocalDate date, Principal p) {
    return reports.generateForUser(users.current(p), branchId, date);
  }

  @PostMapping("/daily-sales-reports/{id}/resend")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resend(
      @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, Principal p) {
    reports.resend(users.current(p), id, key);
  }

  @GetMapping("/sale-reversals")
  public List<Map<String, Object>> reversals(Principal p) {
    return reversals.list(users.current(p));
  }

  @PostMapping("/sales/{id}/reversal")
  public Map<String, Object> reversal(
      @PathVariable UUID id, @Valid @RequestBody SaleReversalService.Request request, Principal p) {
    return reversals.reverse(users.current(p), id, request);
  }
}
