package com.company.fashionpos.hr;

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
@Table(name = "payroll_components")
public class PayrollComponent {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "component_type", nullable = false, length = 32)
  private PayrollComponentType componentType;

  @Column(nullable = false)
  private boolean taxable;

  @Column(name = "default_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal defaultAmount;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollComponent() {}

  public PayrollComponent(
      UUID id,
      Organization organization,
      String name,
      String code,
      PayrollComponentType componentType,
      BigDecimal defaultAmount) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.code = code;
    this.componentType = componentType;
    this.defaultAmount = defaultAmount;
    this.active = true;
  }

  public void updateDetails(
      String name,
      String code,
      PayrollComponentType componentType,
      boolean taxable,
      BigDecimal defaultAmount,
      boolean active) {
    this.name = name;
    this.code = code;
    this.componentType = componentType;
    this.taxable = taxable;
    this.defaultAmount = defaultAmount;
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

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public PayrollComponentType getComponentType() {
    return componentType;
  }

  public boolean isTaxable() {
    return taxable;
  }

  public BigDecimal getDefaultAmount() {
    return defaultAmount;
  }

  public boolean isActive() {
    return active;
  }
}
