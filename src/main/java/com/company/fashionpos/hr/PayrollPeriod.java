package com.company.fashionpos.hr;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payroll_periods")
public class PayrollPeriod {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "branch_id")
  private Branch branch;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PayrollPeriodStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollPeriod() {}

  public PayrollPeriod(
      UUID id,
      Organization organization,
      Branch branch,
      String name,
      LocalDate periodStart,
      LocalDate periodEnd) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.name = name;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.status = PayrollPeriodStatus.DRAFT;
  }

  public void updateDetails(
      Branch branch,
      String name,
      LocalDate periodStart,
      LocalDate periodEnd,
      LocalDate paymentDate,
      PayrollPeriodStatus status) {
    this.branch = branch;
    this.name = name;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.paymentDate = paymentDate;
    this.status = status;
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

  public UUID getId() {
    return id;
  }

  public Branch getBranch() {
    return branch;
  }

  public String getName() {
    return name;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public PayrollPeriodStatus getStatus() {
    return status;
  }

  public Organization getOrganization() {
    return organization;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
