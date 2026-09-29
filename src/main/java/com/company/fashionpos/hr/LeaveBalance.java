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
@Table(name = "leave_balances")
public class LeaveBalance {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @Column(name = "balance_days", nullable = false, precision = 8, scale = 2)
  private BigDecimal balanceDays;

  @Column(name = "used_days", nullable = false, precision = 8, scale = 2)
  private BigDecimal usedDays;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected LeaveBalance() {}

  public LeaveBalance(
      UUID id,
      Organization organization,
      Employee employee,
      LeaveType leaveType,
      BigDecimal balanceDays,
      BigDecimal usedDays) {
    this.id = id;
    this.organization = organization;
    this.employee = employee;
    this.leaveType = leaveType;
    this.balanceDays = balanceDays;
    this.usedDays = usedDays;
  }

  public void adjustUsage(BigDecimal usedDays) {
    this.usedDays = usedDays;
  }

  public UUID getId() {
    return id;
  }

  public Employee getEmployee() {
    return employee;
  }

  public LeaveType getLeaveType() {
    return leaveType;
  }

  public BigDecimal getBalanceDays() {
    return balanceDays;
  }

  public BigDecimal getUsedDays() {
    return usedDays;
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
