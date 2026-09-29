package com.company.fashionpos.catalog;

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
@Table(name = "product_categories")
public class ProductCategory {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CatalogStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected ProductCategory() {}

  public ProductCategory(UUID id, Organization organization, String name, String code) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.code = code;
    this.status = CatalogStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = CatalogStatus.ACTIVE;
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void updateDetails(String name, String code, CatalogStatus status) {
    this.name = name;
    this.code = code;
    this.status = status == null ? CatalogStatus.ACTIVE : status;
  }

  public void deactivate() {
    this.status = CatalogStatus.INACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public CatalogStatus getStatus() {
    return status;
  }
}
