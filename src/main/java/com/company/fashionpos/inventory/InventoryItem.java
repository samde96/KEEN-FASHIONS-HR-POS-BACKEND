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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

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

  @Column(name = "quantity_on_hand", nullable = false)
  private int quantityOnHand;

  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved;

  @Column(name = "reorder_level", nullable = false)
  private int reorderLevel;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected InventoryItem() {}

  public InventoryItem(UUID id, Organization organization, Branch branch, ProductItem product) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.product = product;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void addStock(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be greater than zero");
    }
    quantityOnHand += quantity;
  }

  public void removeStock(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be greater than zero");
    }
    if (availableQuantity() < quantity) {
      throw new IllegalArgumentException("Source branch does not have enough available stock");
    }
    quantityOnHand -= quantity;
  }

  public void reconcileToPhysicalCount(int countedQuantity) {
    if (countedQuantity < 0) {
      throw new IllegalArgumentException("Counted quantity cannot be negative");
    }
    if (countedQuantity < quantityReserved) {
      throw new IllegalArgumentException("Counted quantity cannot be less than reserved stock");
    }
    quantityOnHand = countedQuantity;
  }

  public int availableQuantity() {
    return Math.max(0, quantityOnHand - quantityReserved);
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

  public int getQuantityOnHand() {
    return quantityOnHand;
  }

  public int getQuantityReserved() {
    return quantityReserved;
  }

  public int getReorderLevel() {
    return reorderLevel;
  }

  public void setReorderLevel(int reorderLevel) {
    if (reorderLevel < 0) {
      throw new IllegalArgumentException("Reorder level cannot be negative");
    }
    this.reorderLevel = reorderLevel;
  }
}
