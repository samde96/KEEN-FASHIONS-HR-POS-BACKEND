package com.company.fashionpos.catalog;

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
@RequestMapping("/api/v1/catalog")
public class CatalogController {

  private final CatalogService catalogService;
  private final AuthenticatedUserService authenticatedUserService;

  public CatalogController(
      CatalogService catalogService, AuthenticatedUserService authenticatedUserService) {
    this.catalogService = catalogService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/categories")
  public List<ProductCategoryResponse> listCategories(Principal principal) {
    return catalogService.listCategories(authenticatedUserService.current(principal));
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductCategoryResponse createCategory(
      @Valid @RequestBody ProductCategoryRequest request, Principal principal) {
    return catalogService.createCategory(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/categories/{categoryId}")
  public ProductCategoryResponse updateCategory(
      @PathVariable UUID categoryId,
      @Valid @RequestBody ProductCategoryRequest request,
      Principal principal) {
    return catalogService.updateCategory(
        authenticatedUserService.current(principal), categoryId, request);
  }

  @DeleteMapping("/categories/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateCategory(@PathVariable UUID categoryId, Principal principal) {
    catalogService.deactivateCategory(authenticatedUserService.current(principal), categoryId);
  }

  @GetMapping("/products")
  public List<ProductResponse> listProducts(Principal principal) {
    return catalogService.listProducts(authenticatedUserService.current(principal));
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductResponse createProduct(
      @Valid @RequestBody ProductRequest request, Principal principal) {
    return catalogService.createProduct(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/products/{productId}")
  public ProductResponse updateProduct(
      @PathVariable UUID productId,
      @Valid @RequestBody ProductRequest request,
      Principal principal) {
    return catalogService.updateProduct(
        authenticatedUserService.current(principal), productId, request);
  }

  @DeleteMapping("/products/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateProduct(@PathVariable UUID productId, Principal principal) {
    catalogService.deactivateProduct(authenticatedUserService.current(principal), productId);
  }
}
