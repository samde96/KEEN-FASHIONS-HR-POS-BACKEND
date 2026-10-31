package com.company.fashionpos.customers;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerService customerService;
  private final AuthenticatedUserService authenticatedUserService;

  public CustomerController(
      CustomerService customerService, AuthenticatedUserService authenticatedUserService) {
    this.customerService = customerService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public List<CustomerResponse> list(Principal principal) {
    return customerService.list(authenticatedUserService.current(principal));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerResponse create(@Valid @RequestBody CustomerRequest request, Principal principal) {
    return customerService.create(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/{customerId}")
  public CustomerResponse update(
      @PathVariable UUID customerId,
      @Valid @RequestBody CustomerRequest request,
      Principal principal) {
    return customerService.update(authenticatedUserService.current(principal), customerId, request);
  }

  @PostMapping("/{customerId}/clear")
  public CustomerResponse clear(@PathVariable UUID customerId, Principal principal) {
    return customerService.clearCustomerData(
        authenticatedUserService.current(principal), customerId);
  }

  @DeleteMapping("/{customerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID customerId, Principal principal) {
    customerService.delete(authenticatedUserService.current(principal), customerId);
  }

  @GetMapping("/{customerId}/period-report")
  public CustomerPeriodReportResponse periodReport(
      @PathVariable UUID customerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return customerService.periodReport(
        authenticatedUserService.current(principal), customerId, from, to);
  }
}
