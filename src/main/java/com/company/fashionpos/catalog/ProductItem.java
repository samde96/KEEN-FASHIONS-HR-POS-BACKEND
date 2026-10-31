package com.company.fashionpos.catalog;

import com.company.fashionpos.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductItem {

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 24)
  private ProductType productType = ProductType.SIMPLE;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_product_id")
  private ProductItem parentProduct;

  @Convert(converter = VariantAttributesConverter.class)
  @Column(name = "variant_attributes", columnDefinition = "TEXT", nullable = false)
  private Map<String, String> variantAttributes = Map.of();

  @Column(name = "default_reorder_level", nullable = false)
  private int defaultReorderLevel;

  public ProductType getProductType() {
    return productType;
  }

  public ProductItem getParentProduct() {
    return parentProduct;
  }

  public Map<String, String> getVariantAttributes() {
    return variantAttributes;
  }

  public int getDefaultReorderLevel() {
    return defaultReorderLevel;
  }

  public boolean isSellable() {
    return productType == ProductType.SIMPLE
        && status == CatalogStatus.ACTIVE
        && (parentProduct == null || parentProduct.getStatus() == CatalogStatus.ACTIVE);
  }

  public String getDisplayName() {
    if (parentProduct == null) return name;
    String details =
        variantAttributes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
    return parentProduct.getName() + " — " + name + (details.isBlank() ? "" : " (" + details + ")");
  }

  public void configureVariants(
      ProductType type, ProductItem parent, Map<String, String> attributes, int reorderLevel) {
    this.productType = type;
    this.parentProduct = parent;
    this.variantAttributes = Map.copyOf(attributes);
    this.defaultReorderLevel = reorderLevel;
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private ProductCategory category;

  @Column(nullable = false, length = 180)
  private String name;

  @Column(nullable = false, length = 80)
  private String sku;

  @Column(length = 80)
  private String barcode;

  @Column(length = 80)
  private String department;

  @Column(length = 500)
  private String description;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "cost_price", nullable = false, precision = 14, scale = 2)
  private BigDecimal costPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "vat_category", nullable = false, length = 1)
  private VatCategory vatCategory;

  @Column(nullable = false, length = 500)
  private String sizes;

  @Column(nullable = false, length = 500)
  private String colors;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CatalogStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected ProductItem() {}

  public ProductItem(
      UUID id,
      Organization organization,
      ProductCategory category,
      String name,
      String sku,
      BigDecimal unitPrice) {
    this.id = id;
    this.organization = organization;
    this.category = category;
    this.name = name;
    this.sku = sku;
    this.unitPrice = unitPrice;
    this.sizes = "";
    this.colors = "";
    this.status = CatalogStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (sizes == null) {
      sizes = "";
    }
    if (colors == null) {
      colors = "";
    }
    if (status == null) {
      status = CatalogStatus.ACTIVE;
    }
    if (vatCategory == null) {
      vatCategory = VatCategory.A;
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void updateDetails(
      ProductCategory category,
      String name,
      String sku,
      String barcode,
      String department,
      String description,
      String imageUrl,
      BigDecimal unitPrice,
      BigDecimal costPrice,
      VatCategory vatCategory,
      String sizes,
      String colors,
      CatalogStatus status) {
    this.category = category;
    this.name = name;
    this.sku = sku;
    this.barcode = barcode;
    this.department = department;
    this.description = description;
    this.imageUrl = imageUrl;
    this.unitPrice = unitPrice;
    this.costPrice = costPrice;
    this.vatCategory = vatCategory == null ? VatCategory.A : vatCategory;
    this.sizes = sizes;
    this.colors = colors;
    this.status = status == null ? CatalogStatus.ACTIVE : status;
  }

  public void deactivate() {
    this.status = CatalogStatus.INACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public ProductCategory getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public String getSku() {
    return sku;
  }

  public String getBarcode() {
    return barcode;
  }

  public String getDepartment() {
    return department;
  }

  public String getDescription() {
    return description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getCostPrice() {
    return costPrice;
  }

  public VatCategory getVatCategory() {
    return vatCategory;
  }

  public String getSizes() {
    return sizes;
  }

  public String getColors() {
    return colors;
  }

  public CatalogStatus getStatus() {
    return status;
  }
}
