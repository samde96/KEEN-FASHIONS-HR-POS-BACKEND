package com.company.fashionpos.suppliers;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

  private final SupplierService supplierService;
  private final AuthenticatedUserService authenticatedUserService;

  public SupplierController(
      SupplierService supplierService, AuthenticatedUserService authenticatedUserService) {
    this.supplierService = supplierService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public List<SupplierResponse> list(Principal principal) {
    return supplierService.list(authenticatedUserService.current(principal));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierResponse create(@Valid @RequestBody SupplierRequest request, Principal principal) {
    return supplierService.create(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/{supplierId}")
  public SupplierResponse update(
      @PathVariable UUID supplierId,
      @Valid @RequestBody SupplierRequest request,
      Principal principal) {
    return supplierService.update(authenticatedUserService.current(principal), supplierId, request);
  }

  @DeleteMapping("/{supplierId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID supplierId, Principal principal) {
    supplierService.deactivate(authenticatedUserService.current(principal), supplierId);
  }
}
