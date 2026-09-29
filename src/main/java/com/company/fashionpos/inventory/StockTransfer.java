package com.company.fashionpos.inventory;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.catalog.ProductItem;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
public class StockTransfer {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_branch_id", nullable = false)
  private Branch sourceBranch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "destination_branch_id", nullable = false)
  private Branch destinationBranch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductItem product;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StockTransferStatus status;

  @Column(name = "reference_number", length = 80)
  private String referenceNumber;

  @Column(length = 500)
  private String notes;

  @Column(name = "transferred_at", nullable = false)
  private Instant transferredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StockTransfer() {}

  public StockTransfer(
      UUID id,
      Organization organization,
      Branch sourceBranch,
      Branch destinationBranch,
      ProductItem product,
      int quantity,
      String referenceNumber,
      String notes) {
    this.id = id;
    this.organization = organization;
    this.sourceBranch = sourceBranch;
    this.destinationBranch = destinationBranch;
    this.product = product;
    this.quantity = quantity;
    this.referenceNumber = referenceNumber;
    this.notes = notes;
    this.status = StockTransferStatus.COMPLETED;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = StockTransferStatus.COMPLETED;
    }
    Instant now = Instant.now();
    transferredAt = transferredAt == null ? now : transferredAt;
    createdAt = createdAt == null ? now : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public Branch getSourceBranch() {
    return sourceBranch;
  }

  public Branch getDestinationBranch() {
    return destinationBranch;
  }

  public ProductItem getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public StockTransferStatus getStatus() {
    return status;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getTransferredAt() {
    return transferredAt;
  }
}
