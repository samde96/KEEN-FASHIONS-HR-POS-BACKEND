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
import java.util.UUID;

@Entity
@Table(name = "payroll_runs")
public class PayrollRun {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payroll_period_id", nullable = false)
  private PayrollPeriod payrollPeriod;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "branch_id")
  private Branch branch;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PayrollRunStatus status;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollRun() {}

  public PayrollRun(
      UUID id, Organization organization, PayrollPeriod payrollPeriod, Branch branch, String name) {
    this.id = id;
    this.organization = organization;
    this.payrollPeriod = payrollPeriod;
    this.branch = branch;
    this.name = name;
    this.status = PayrollRunStatus.DRAFT;
  }

  public void updateDetails(String name, PayrollRunStatus status, Instant processedAt) {
    this.name = name;
    this.status = status;
    this.processedAt = processedAt;
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

  public Organization getOrganization() {
    return organization;
  }

  public PayrollPeriod getPayrollPeriod() {
    return payrollPeriod;
  }

  public Branch getBranch() {
    return branch;
  }

  public String getName() {
    return name;
  }

  public PayrollRunStatus getStatus() {
    return status;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
