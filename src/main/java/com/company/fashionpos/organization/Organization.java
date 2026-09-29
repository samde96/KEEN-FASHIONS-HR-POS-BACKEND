package com.company.fashionpos.organization;

import com.company.fashionpos.catalog.VatCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

  @Id private UUID id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "currency_code", nullable = false, length = 3)
  private String currencyCode;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone;

  @Column(name = "tax_registration_number", length = 64)
  private String taxRegistrationNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_product_vat_category", nullable = false, length = 1)
  private VatCategory defaultProductVatCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OrganizationStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Organization() {}

  public Organization(UUID id, String name, String currencyCode, String timeZone) {
    this.id = id;
    this.name = name;
    this.currencyCode = currencyCode;
    this.timeZone = timeZone;
    this.defaultProductVatCategory = VatCategory.A;
    this.status = OrganizationStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = OrganizationStatus.ACTIVE;
    }
    if (defaultProductVatCategory == null) {
      defaultProductVatCategory = VatCategory.A;
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

  public String getCurrencyCode() {
    return currencyCode;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public String getTaxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public VatCategory getDefaultProductVatCategory() {
    return defaultProductVatCategory;
  }

  public OrganizationStatus getStatus() {
    return status;
  }

  public void updateVatSettings(VatCategory defaultProductVatCategory) {
    this.defaultProductVatCategory =
        defaultProductVatCategory == null ? VatCategory.A : defaultProductVatCategory;
  }
}
