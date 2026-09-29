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
@Table(name = "stock_adjustments")
public class StockAdjustment {

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

  @Column(name = "adjusted_by_user_id")
  private UUID adjustedByUserId;

  @Column(name = "system_quantity", nullable = false)
  private int systemQuantity;

  @Column(name = "counted_quantity", nullable = false)
  private int countedQuantity;

  @Column(name = "variance_quantity", nullable = false)
  private int varianceQuantity;

  @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
  private BigDecimal unitCost;

  @Column(name = "loss_value", nullable = false, precision = 14, scale = 2)
  private BigDecimal lossValue;

  @Column(name = "excess_value", nullable = false, precision = 14, scale = 2)
  private BigDecimal excessValue;

  @Column(nullable = false, length = 160)
  private String reason;

  @Column(length = 500)
  private String notes;

  @Column(name = "adjusted_at", nullable = false)
  private Instant adjustedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StockAdjustment() {}

  public StockAdjustment(
      UUID id,
      Organization organization,
      Branch branch,
      ProductItem product,
      UUID adjustedByUserId,
      int systemQuantity,
      int countedQuantity,
      int varianceQuantity,
      BigDecimal unitCost,
      BigDecimal lossValue,
      BigDecimal excessValue,
      String reason,
      String notes) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.product = product;
    this.adjustedByUserId = adjustedByUserId;
    this.systemQuantity = systemQuantity;
    this.countedQuantity = countedQuantity;
    this.varianceQuantity = varianceQuantity;
    this.unitCost = unitCost;
    this.lossValue = lossValue;
    this.excessValue = excessValue;
    this.reason = reason;
    this.notes = notes;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    adjustedAt = adjustedAt == null ? now : adjustedAt;
    createdAt = createdAt == null ? now : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Branch getBranch() {
    return branch;
  }

  public ProductItem getProduct() {
    return product;
  }

  public UUID getAdjustedByUserId() {
    return adjustedByUserId;
  }

  public int getSystemQuantity() {
    return systemQuantity;
  }

  public int getCountedQuantity() {
    return countedQuantity;
  }

  public int getVarianceQuantity() {
    return varianceQuantity;
  }

  public BigDecimal getUnitCost() {
    return unitCost;
  }

  public BigDecimal getLossValue() {
    return lossValue;
  }

  public BigDecimal getExcessValue() {
    return excessValue;
  }

  public String getReason() {
    return reason;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getAdjustedAt() {
    return adjustedAt;
  }
}
