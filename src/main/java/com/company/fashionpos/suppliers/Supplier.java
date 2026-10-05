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
import java.math.BigDecimal;
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

  @Column(name = "supplier_code", length = 40)
  private String supplierCode;

  @Column(name = "company_name", nullable = false, length = 160)
  private String companyName;

  @Column(name = "contact_person", length = 160)
  private String contactPerson;

  @Column(length = 40)
  private String phone;

  @Column(length = 254)
  private String email;

  @Column(length = 255)
  private String address;

  @Column(name = "kra_pin", length = 40)
  private String kraPin;

  @Column(name = "payment_terms", length = 120)
  private String paymentTerms;

  @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
  private BigDecimal creditLimit;

  @Column(name = "bank_details", length = 500)
  private String bankDetails;

  @Column(name = "products_supplied", length = 500)
  private String productsSupplied;

  @Column(name = "outstanding_balance", nullable = false, precision = 14, scale = 2)
  private BigDecimal outstandingBalance;

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
    this.companyName = name;
    this.creditLimit = BigDecimal.ZERO;
    this.outstandingBalance = BigDecimal.ZERO;
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
    if (companyName == null || companyName.isBlank()) {
      companyName = name;
    }
    if (creditLimit == null) {
      creditLimit = BigDecimal.ZERO;
    }
    if (outstandingBalance == null) {
      outstandingBalance = BigDecimal.ZERO;
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
      String supplierCode,
      String companyName,
      String contactPerson,
      String phone,
      String email,
      String address,
      String kraPin,
      String paymentTerms,
      BigDecimal creditLimit,
      String bankDetails,
      String productsSupplied,
      BigDecimal outstandingBalance,
      String notes,
      SupplierStatus status) {
    this.name = name;
    this.supplierCode = supplierCode;
    this.companyName = companyName == null || companyName.isBlank() ? name : companyName;
    this.contactPerson = contactPerson;
    this.phone = phone;
    this.email = email;
    this.address = address;
    this.kraPin = kraPin;
    this.paymentTerms = paymentTerms;
    this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
    this.bankDetails = bankDetails;
    this.productsSupplied = productsSupplied;
    this.outstandingBalance = outstandingBalance == null ? BigDecimal.ZERO : outstandingBalance;
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

  public String getSupplierCode() {
    return supplierCode;
  }

  public String getCompanyName() {
    return companyName;
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

  public String getAddress() {
    return address;
  }

  public String getKraPin() {
    return kraPin;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public String getBankDetails() {
    return bankDetails;
  }

  public String getProductsSupplied() {
    return productsSupplied;
  }

  public BigDecimal getOutstandingBalance() {
    return outstandingBalance;
  }

  public String getNotes() {
    return notes;
  }

  public SupplierStatus getStatus() {
    return status;
  }
}
