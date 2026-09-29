package com.company.fashionpos.sales;

import com.company.fashionpos.catalog.ProductItem;
import com.company.fashionpos.catalog.VatCategory;
import com.company.fashionpos.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale_lines")
public class SaleLine {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id", nullable = false)
  private Sale sale;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductItem product;

  @Column(name = "product_name", nullable = false, length = 180)
  private String productName;

  @Column(nullable = false, length = 80)
  private String sku;

  @Column(name = "category_name", nullable = false, length = 120)
  private String categoryName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "cost_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal costPrice;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal lineTotal;

  @Enumerated(EnumType.STRING)
  @Column(name = "vat_category", nullable = false, length = 1)
  private VatCategory vatCategory;

  @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
  private BigDecimal taxRate;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal taxAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SaleLine() {}

  public SaleLine(
      UUID id,
      Organization organization,
      Sale sale,
      ProductItem product,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal costPrice,
      BigDecimal lineTotal,
      VatCategory vatCategory,
      BigDecimal taxRate,
      BigDecimal taxAmount) {
    this.id = id;
    this.organization = organization;
    this.sale = sale;
    this.product = product;
    this.productName = product.getName();
    this.sku = product.getSku();
    this.categoryName = product.getCategory().getName();
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.costPrice = costPrice;
    this.lineTotal = lineTotal;
    this.vatCategory = vatCategory == null ? VatCategory.A : vatCategory;
    this.taxRate = taxRate;
    this.taxAmount = taxAmount;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (vatCategory == null) {
      vatCategory = VatCategory.A;
    }
    if (taxRate == null) {
      taxRate = vatCategory.rate();
    }
    if (taxAmount == null) {
      taxAmount = BigDecimal.ZERO;
    }
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Sale getSale() {
    return sale;
  }

  public ProductItem getProduct() {
    return product;
  }

  public String getProductName() {
    return productName;
  }

  public String getSku() {
    return sku;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getCostPrice() {
    return costPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public VatCategory getVatCategory() {
    return vatCategory;
  }

  public BigDecimal getTaxRate() {
    return taxRate;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }
}
