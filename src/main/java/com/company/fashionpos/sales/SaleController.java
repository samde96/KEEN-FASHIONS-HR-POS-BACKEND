package com.company.fashionpos.sales;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

  private final SaleService saleService;
  private final HotelSalesSummaryService hotelSalesSummary;
  private final AuthenticatedUserService authenticatedUserService;

  public SaleController(
      SaleService saleService,
      HotelSalesSummaryService hotelSalesSummary,
      AuthenticatedUserService authenticatedUserService) {
    this.saleService = saleService;
    this.hotelSalesSummary = hotelSalesSummary;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public List<SaleResponse> listSales(
      Principal principal,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate) {
    return saleService.listSales(authenticatedUserService.current(principal), fromDate, toDate);
  }

  @GetMapping("/hotel-summary")
  public HotelSalesSummary hotelSummary(
      Principal principal, @RequestParam(required = false) UUID branchId) {
    return hotelSalesSummary.summary(authenticatedUserService.current(principal), branchId);
  }

  @GetMapping("/{saleId}")
  public SaleResponse getSale(@PathVariable UUID saleId, Principal principal) {
    return saleService.getSale(authenticatedUserService.current(principal), saleId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SaleResponse createSale(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SaleRequest request,
      Principal principal) {
    return saleService.createSale(
        authenticatedUserService.current(principal), idempotencyKey, request);
  }
}
