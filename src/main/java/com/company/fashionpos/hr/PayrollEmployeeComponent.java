package com.company.fashionpos.hr;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_employee_components")
public class PayrollEmployeeComponent {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payroll_employee_id", nullable = false)
  private PayrollEmployee payrollEmployee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payroll_component_id", nullable = false)
  private PayrollComponent payrollComponent;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollEmployeeComponent() {}

  public PayrollEmployeeComponent(
      UUID id,
      Organization organization,
      PayrollEmployee payrollEmployee,
      PayrollComponent payrollComponent,
      BigDecimal amount) {
    this.id = id;
    this.organization = organization;
    this.payrollEmployee = payrollEmployee;
    this.payrollComponent = payrollComponent;
    this.amount = amount;
  }

  public UUID getId() {
    return id;
  }

  public PayrollEmployee getPayrollEmployee() {
    return payrollEmployee;
  }

  public PayrollComponent getPayrollComponent() {
    return payrollComponent;
  }

  public BigDecimal getAmount() {
    return amount;
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
}
