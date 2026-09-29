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
@Table(name = "payroll_sales_bonus_rules")
public class PayrollSalesBonusRule {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(name = "daily_sales_target", nullable = false, precision = 14, scale = 2)
  private BigDecimal dailySalesTarget;

  @Column(name = "bonus_per_target_day", nullable = false, precision = 14, scale = 2)
  private BigDecimal bonusPerTargetDay;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollSalesBonusRule() {}

  public PayrollSalesBonusRule(UUID id, Organization organization) {
    this.id = id;
    this.organization = organization;
    this.dailySalesTarget = BigDecimal.ZERO.setScale(2);
    this.bonusPerTargetDay = BigDecimal.ZERO.setScale(2);
    this.active = true;
  }

  public void updateDetails(
      BigDecimal dailySalesTarget, BigDecimal bonusPerTargetDay, boolean active) {
    this.dailySalesTarget = dailySalesTarget;
    this.bonusPerTargetDay = bonusPerTargetDay;
    this.active = active;
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

  public BigDecimal getDailySalesTarget() {
    return dailySalesTarget;
  }

  public BigDecimal getBonusPerTargetDay() {
    return bonusPerTargetDay;
  }

  public boolean isActive() {
    return active;
  }
}
