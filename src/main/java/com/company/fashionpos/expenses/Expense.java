package com.company.fashionpos.expenses;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @Column(name = "expense_number", nullable = false, length = 40)
  private String expenseNumber;

  @Column(nullable = false, length = 80)
  private String category;

  @Column(nullable = false, length = 180)
  private String description;

  @Column(name = "vendor_name", length = 160)
  private String vendorName;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 32)
  private ExpensePaymentMethod paymentMethod;

  @Column(name = "payment_reference", length = 80)
  private String paymentReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ExpenseStatus status;

  @Column(length = 500)
  private String notes;

  @Column(name = "incurred_at", nullable = false)
  private Instant incurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Expense() {}

  public Expense(
      UUID id,
      Organization organization,
      Branch branch,
      String expenseNumber,
      String category,
      String description,
      String vendorName,
      BigDecimal amount,
      ExpensePaymentMethod paymentMethod,
      String paymentReference,
      ExpenseStatus status,
      String notes) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.expenseNumber = expenseNumber;
    this.category = category;
    this.description = description;
    this.vendorName = vendorName;
    this.amount = amount;
    this.paymentMethod = paymentMethod;
    this.paymentReference = paymentReference;
    this.status = status;
    this.notes = notes;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = ExpenseStatus.PENDING;
    }
    Instant now = Instant.now();
    incurredAt = incurredAt == null ? now : incurredAt;
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void markPaid() {
    if (status == ExpenseStatus.VOID) {
      throw new IllegalArgumentException("Voided expenses cannot be marked paid");
    }
    status = ExpenseStatus.PAID;
  }

  public void voidExpense() {
    status = ExpenseStatus.VOID;
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

  public String getExpenseNumber() {
    return expenseNumber;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public String getVendorName() {
    return vendorName;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public ExpensePaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public ExpenseStatus getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getIncurredAt() {
    return incurredAt;
  }
}
