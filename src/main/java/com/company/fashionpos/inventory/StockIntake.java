package com.company.fashionpos.inventory;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.catalog.ProductItem;
import com.company.fashionpos.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "stock_intakes")
public class StockIntake {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductItem product;

  @Column(name = "supplier_name", nullable = false, length = 160)
  private String supplierName;

  @Column(name = "reference_number", length = 80)
  private String referenceNumber;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitCost;

  @Column(length = 500)
  private String notes;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StockIntake() {}

  public StockIntake(
      UUID id,
      Organization organization,
      Branch branch,
      ProductItem product,
      String supplierName,
      String referenceNumber,
      int quantity,
      BigDecimal unitCost,
      String notes) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.product = product;
    this.supplierName = supplierName;
    this.referenceNumber = referenceNumber;
    this.quantity = quantity;
    this.unitCost = unitCost;
    this.notes = notes;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    receivedAt = receivedAt == null ? now : receivedAt;
    createdAt = createdAt == null ? now : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public Branch getBranch() {
    return branch;
  }

  public ProductItem getProduct() {
    return product;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
