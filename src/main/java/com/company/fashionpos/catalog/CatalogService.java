package com.company.fashionpos.catalog;

import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private final ProductCategoryRepository productCategoryRepository;
  private final ProductRepository productRepository;
  private final OrganizationRepository organizationRepository;
  private final JdbcTemplate jdbcTemplate;

  public CatalogService(
      ProductCategoryRepository productCategoryRepository,
      ProductRepository productRepository,
      OrganizationRepository organizationRepository,
      JdbcTemplate jdbcTemplate) {
    this.productCategoryRepository = productCategoryRepository;
    this.productRepository = productRepository;
    this.organizationRepository = organizationRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public List<ProductCategoryResponse> listCategories(AuthenticatedUser user) {
    return productCategoryRepository.findWithinOrganization(user.organizationId()).stream()
        .map(ProductCategoryResponse::from)
        .toList();
  }

  @Transactional
  public ProductCategoryResponse createCategory(
      AuthenticatedUser user, ProductCategoryRequest request) {
    String code = normalizeCode(request.code());
    if (productCategoryRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Category code is already used");
    }

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    ProductCategory category =
        new ProductCategory(UUID.randomUUID(), organization, normalizeName(request.name()), code);
    category.updateDetails(
        category.getName(), category.getCode(), statusOrActive(request.status()));
    return ProductCategoryResponse.from(productCategoryRepository.save(category));
  }

  @Transactional
  public ProductCategoryResponse updateCategory(
      AuthenticatedUser user, UUID categoryId, ProductCategoryRequest request) {
    ProductCategory category =
        productCategoryRepository
            .findWithinOrganization(categoryId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Category not found"));

    String code = normalizeCode(request.code());
    if (productCategoryRepository.existsByCodeWithinOrganizationExcludingCategory(
        user.organizationId(), code, categoryId)) {
      throw new IllegalArgumentException("Category code is already used");
    }

    category.updateDetails(normalizeName(request.name()), code, statusOrActive(request.status()));
    return ProductCategoryResponse.from(category);
  }

  @Transactional
  public void deactivateCategory(AuthenticatedUser user, UUID categoryId) {
    ProductCategory category =
        productCategoryRepository
            .findWithinOrganization(categoryId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Category not found"));
    category.deactivate();
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> listProducts(AuthenticatedUser user) {
    return productRepository.findWithinOrganization(user.organizationId()).stream()
        .map(product -> toProductResponse(product, user.canViewProfit()))
        .toList();
  }

  @Transactional
  public ProductResponse createProduct(AuthenticatedUser user, ProductRequest request) {
    String sku = normalizeSku(request.sku());
    if (sku == null) {
      sku = generateUniqueSku(user.organizationId());
    }
    requireSkuAvailable(user.organizationId(), sku);

    String barcode = normalizeOptional(request.barcode());
    if (barcode == null) {
      barcode = generateUniqueBarcode(user.organizationId());
    }
    requireBarcodeAvailable(user.organizationId(), barcode);

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    ProductCategory category = requireCategory(user, request.categoryId());
    ProductItem product =
        new ProductItem(
            UUID.randomUUID(),
            organization,
            category,
            normalizeName(request.name()),
            sku,
            requireNonNegative(request.unitPrice(), "Unit price"));
    updateProductDetails(
        product, category, sku, barcode, request, organization.getDefaultProductVatCategory());
    configureVariants(user, product, request);
    return toProductResponse(productRepository.save(product), user.canViewProfit());
  }

  @Transactional
  public ProductResponse updateProduct(
      AuthenticatedUser user, UUID productId, ProductRequest request) {
    ProductItem product =
        productRepository
            .findForMutation(productId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    String sku = normalizeSku(request.sku());
    if (sku == null) {
      sku = product.getSku();
    }
    requireSkuAvailable(user.organizationId(), sku, productId);

    String barcode = normalizeOptional(request.barcode());
    if (barcode == null) {
      barcode = product.getBarcode();
    }
    if (barcode == null) {
      barcode = generateUniqueBarcode(user.organizationId());
    }
    requireBarcodeAvailable(user.organizationId(), barcode, productId);

    ProductCategory category = requireCategory(user, request.categoryId());
    updateProductDetails(product, category, sku, barcode, request, product.getVatCategory());
    configureVariants(user, product, request);
    return toProductResponse(product, user.canViewProfit());
  }

  @Transactional
  public void deactivateProduct(AuthenticatedUser user, UUID productId) {
    ProductItem product =
        productRepository
            .findForMutation(productId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    product.deactivate();
  }

  private void updateProductDetails(
      ProductItem product,
      ProductCategory category,
      String sku,
      String barcode,
      ProductRequest request,
      VatCategory fallbackVatCategory) {
    product.updateDetails(
        category,
        normalizeName(request.name()),
        sku,
        barcode,
        normalizeOptional(request.department()),
        normalizeOptional(request.description()),
        normalizeOptional(request.imageUrl()),
        requireNonNegative(request.unitPrice(), "Unit price"),
        requireNonNegative(request.costPrice(), "Buying price"),
        vatCategoryOrFallback(request.vatCategory(), fallbackVatCategory),
        joinList(request.sizes()),
        joinList(request.colors()),
        statusOrActive(request.status()));
  }

  private ProductCategory requireCategory(AuthenticatedUser user, UUID categoryId) {
    return productCategoryRepository
        .findWithinOrganization(categoryId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  private ProductResponse toProductResponse(ProductItem product, boolean includeCostPrice) {
    return ProductResponse.from(
        product,
        splitList(product.getSizes()),
        splitList(product.getColors()),
        totalStock(product),
        includeCostPrice);
  }

  private int totalStock(ProductItem product) {
    Number total =
        jdbcTemplate.queryForObject(
            "select coalesce(sum(i.quantity_on_hand), 0) from inventory_items i join products p on p.id=i.product_id where p.id = ? or p.parent_product_id = ?",
            Number.class,
            product.getId(),
            product.getId());
    return total == null ? 0 : total.intValue();
  }

  private void configureVariants(
      AuthenticatedUser user, ProductItem product, ProductRequest request) {
    ProductType type =
        request.productType() == null ? product.getProductType() : request.productType();
    UUID parentId = request.parentProductId();
    ProductItem parent =
        parentId == null
            ? product.getParentProduct()
            : productRepository
                .findForMutation(parentId, user.organizationId())
                .orElseThrow(() -> new EntityNotFoundException("Parent product not found"));
    if (parent != null
        && (parent.getId().equals(product.getId())
            || parent.getProductType() != ProductType.VARIABLE
            || type != ProductType.SIMPLE)) {
      throw new IllegalArgumentException("Variants must belong to a parent product with variants");
    }
    if (product.getParentProduct() != null
        && parent != null
        && !product.getParentProduct().getId().equals(parent.getId())) {
      throw new IllegalArgumentException("A variant cannot be moved to another parent");
    }
    if (type != product.getProductType()) {
      Number usage =
          jdbcTemplate.queryForObject(
              "select (select count(*) from inventory_items where product_id=?) + (select count(*) from sale_lines where product_id=?) + (select count(*) from products where parent_product_id=?)",
              Number.class,
              product.getId(),
              product.getId(),
              product.getId());
      if (usage != null && usage.longValue() > 0)
        throw new IllegalArgumentException(
            "Product type cannot change after inventory, sales, or variants exist");
    }
    Map<String, String> attributes =
        request.variantAttributes() == null
            ? product.getVariantAttributes()
            : request.variantAttributes();
    if (parent != null && attributes.isEmpty())
      throw new IllegalArgumentException("Add at least one variant attribute");
    int reorderLevel =
        request.reorderLevel() == null ? product.getDefaultReorderLevel() : request.reorderLevel();
    boolean reorderLevelChanged = reorderLevel != product.getDefaultReorderLevel();
    product.configureVariants(type, parent, attributes, reorderLevel);
    if (request.reorderLevel() != null && reorderLevelChanged) {
      jdbcTemplate.update(
          "update inventory_items set reorder_level=?, version=version+1 where organization_id=? and product_id=?",
          reorderLevel,
          user.organizationId(),
          product.getId());
    }
  }

  private void requireSkuAvailable(UUID organizationId, String sku) {
    if (productRepository.existsBySkuWithinOrganization(organizationId, sku)) {
      throw new IllegalArgumentException("Product SKU is already used");
    }
  }

  private void requireSkuAvailable(UUID organizationId, String sku, UUID productId) {
    if (productRepository.existsBySkuWithinOrganizationExcludingProduct(
        organizationId, sku, productId)) {
      throw new IllegalArgumentException("Product SKU is already used");
    }
  }

  private void requireBarcodeAvailable(UUID organizationId, String barcode) {
    if (barcode != null
        && productRepository.existsByBarcodeWithinOrganization(organizationId, barcode)) {
      throw new IllegalArgumentException("Product barcode is already used");
    }
  }

  private void requireBarcodeAvailable(UUID organizationId, String barcode, UUID productId) {
    if (barcode != null
        && productRepository.existsByBarcodeWithinOrganizationExcludingProduct(
            organizationId, barcode, productId)) {
      throw new IllegalArgumentException("Product barcode is already used");
    }
  }

  private String generateUniqueSku(UUID organizationId) {
    long sequence = productRepository.countWithinOrganization(organizationId) + 1;
    for (int attempt = 0; attempt < 1_000_000; attempt += 1) {
      String sku = buildGeneratedSku(sequence + attempt);
      if (!productRepository.existsBySkuWithinOrganization(organizationId, sku)) {
        return sku;
      }
    }
    throw new IllegalStateException("Unable to generate a unique product SKU");
  }

  private String generateUniqueBarcode(UUID organizationId) {
    long sequence = productRepository.countWithinOrganization(organizationId) + 1;
    for (int attempt = 0; attempt < 1_000_000; attempt += 1) {
      String barcode = buildGeneratedBarcode(sequence + attempt);
      if (!productRepository.existsByBarcodeWithinOrganization(organizationId, barcode)) {
        return barcode;
      }
    }
    throw new IllegalStateException("Unable to generate a unique product barcode");
  }

  private static String buildGeneratedSku(long sequence) {
    return "SKU-%04d".formatted(sequence);
  }

  private static String buildGeneratedBarcode(long sequence) {
    long boundedSequence = Math.floorMod(sequence, 1_000_000_000L);
    String firstTwelveDigits = "200%09d".formatted(boundedSequence);
    return withEan13CheckDigit(firstTwelveDigits);
  }

  private static String withEan13CheckDigit(String firstTwelveDigits) {
    int sum = 0;
    for (int index = 0; index < firstTwelveDigits.length(); index += 1) {
      int digit = Character.digit(firstTwelveDigits.charAt(index), 10);
      sum += digit * (index % 2 == 0 ? 1 : 3);
    }
    int checkDigit = (10 - (sum % 10)) % 10;
    return firstTwelveDigits + checkDigit;
  }

  private static BigDecimal requireNonNegative(BigDecimal value, String label) {
    if (value == null) {
      throw new IllegalArgumentException(label + " is required");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(label + " cannot be negative");
    }
    return value;
  }

  private static String joinList(List<String> values) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    if (values != null) {
      for (String value : values) {
        if (value != null && !value.isBlank()) {
          String trimmed = value.trim();
          if (trimmed.contains(",")) {
            throw new IllegalArgumentException("List values cannot contain commas");
          }
          normalized.add(trimmed);
        }
      }
    }
    return String.join(",", normalized);
  }

  private static List<String> splitList(String value) {
    List<String> values = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return values;
    }

    for (String item : value.split(",")) {
      if (!item.isBlank()) {
        values.add(item.trim());
      }
    }
    return values;
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeSku(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static CatalogStatus statusOrActive(CatalogStatus status) {
    return status == null ? CatalogStatus.ACTIVE : status;
  }

  private static VatCategory vatCategoryOrFallback(
      VatCategory vatCategory, VatCategory fallbackVatCategory) {
    if (vatCategory != null) {
      return vatCategory;
    }
    return fallbackVatCategory == null ? VatCategory.A : fallbackVatCategory;
  }
}
