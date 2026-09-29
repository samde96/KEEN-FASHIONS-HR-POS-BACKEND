package com.company.fashionpos.sales;

import com.company.fashionpos.branch.Branch;
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
@Table(name = "payments")
public class SalePayment {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sale_id", nullable = false)
  private Sale sale;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentMethod method;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentStatus status;

  @Column(length = 80)
  private String reference;

  @Column(name = "cash_received", precision = 19, scale = 4)
  private BigDecimal cashReceived;

  @Column(name = "change_due", precision = 19, scale = 4)
  private BigDecimal changeDue;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SalePayment() {}

  public SalePayment(
      UUID id,
      Organization organization,
      Branch branch,
      Sale sale,
      PaymentMethod method,
      BigDecimal amount,
      String reference,
      BigDecimal cashReceived,
      BigDecimal changeDue) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.sale = sale;
    this.method = method;
    this.amount = amount;
    this.reference = reference;
    this.cashReceived = cashReceived;
    this.changeDue = changeDue;
    this.status = PaymentStatus.SETTLED;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = PaymentStatus.SETTLED;
    }
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Sale getSale() {
    return sale;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getReference() {
    return reference;
  }

  public BigDecimal getCashReceived() {
    return cashReceived;
  }

  public BigDecimal getChangeDue() {
    return changeDue;
  }
}
