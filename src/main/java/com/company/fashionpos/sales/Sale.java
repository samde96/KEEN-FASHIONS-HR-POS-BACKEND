package com.company.fashionpos.sales;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.hr.Employee;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
public class Sale {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sold_by_employee_id")
  private Employee soldByEmployee;

  @Column(name = "sale_number", nullable = false, length = 40)
  private String saleNumber;

  @Column(name = "idempotency_key", nullable = false, length = 120)
  private String idempotencyKey;

  @Column(name = "customer_name", length = 160)
  private String customerName;

  @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal subtotalAmount;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal discountAmount;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal taxAmount;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SaleStatus status;

  @Column(name = "sold_at", nullable = false)
  private Instant soldAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Sale() {}

  public Sale(
      UUID id,
      Organization organization,
      Branch branch,
      Employee soldByEmployee,
      String saleNumber,
      String idempotencyKey,
      String customerName,
      BigDecimal subtotalAmount,
      BigDecimal discountAmount,
      BigDecimal taxAmount,
      BigDecimal totalAmount) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.soldByEmployee = soldByEmployee;
    this.saleNumber = saleNumber;
    this.idempotencyKey = idempotencyKey;
    this.customerName = customerName;
    this.subtotalAmount = subtotalAmount;
    this.discountAmount = discountAmount;
    this.taxAmount = taxAmount;
    this.totalAmount = totalAmount;
    this.status = SaleStatus.COMPLETED;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = SaleStatus.COMPLETED;
    }
    Instant now = Instant.now();
    soldAt = soldAt == null ? now : soldAt;
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

  public Employee getSoldByEmployee() {
    return soldByEmployee;
  }

  public String getSaleNumber() {
    return saleNumber;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getCustomerName() {
    return customerName;
  }

  public BigDecimal getSubtotalAmount() {
    return subtotalAmount;
  }

  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public SaleStatus getStatus() {
    return status;
  }

  public Instant getSoldAt() {
    return soldAt;
  }
}
