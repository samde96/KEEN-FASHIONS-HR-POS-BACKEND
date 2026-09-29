package com.company.fashionpos.suppliers;

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
@Table(name = "suppliers")
public class Supplier {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "contact_person", length = 160)
  private String contactPerson;

  @Column(length = 40)
  private String phone;

  @Column(length = 254)
  private String email;

  @Column(length = 500)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SupplierStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Supplier() {}

  public Supplier(UUID id, Organization organization, String name) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.status = SupplierStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = SupplierStatus.ACTIVE;
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void updateDetails(
      String name,
      String contactPerson,
      String phone,
      String email,
      String notes,
      SupplierStatus status) {
    this.name = name;
    this.contactPerson = contactPerson;
    this.phone = phone;
    this.email = email;
    this.notes = notes;
    this.status = status == null ? SupplierStatus.ACTIVE : status;
  }

  public void deactivate() {
    this.status = SupplierStatus.INACTIVE;
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

  public String getContactPerson() {
    return contactPerson;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public String getNotes() {
    return notes;
  }

  public SupplierStatus getStatus() {
    return status;
  }
}
