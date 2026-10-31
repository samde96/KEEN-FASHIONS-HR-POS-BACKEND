package com.company.fashionpos.customers;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "branch_id")
  private Branch branch;

  @Column(name = "customer_code", nullable = false, length = 40)
  private String customerCode;

  @Column(nullable = false, length = 160)
  private String name;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "customer_type", nullable = false, length = 32)
  private CustomerType customerType;

  @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
  private BigDecimal creditLimit;

  @Column(name = "outstanding_balance", nullable = false, precision = 14, scale = 2)
  private BigDecimal outstandingBalance;

  @Column(name = "payment_terms", length = 120)
  private String paymentTerms;

  @Column(length = 500)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CustomerStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Customer() {}

  public Customer(
      UUID id, Organization organization, Branch branch, String customerCode, String name) {
    this.id = id;
    this.organization = organization;
    this.branch = branch;
    this.customerCode = customerCode;
    this.name = name;
    this.customerType = CustomerType.INDIVIDUAL;
    this.creditLimit = BigDecimal.ZERO;
    this.outstandingBalance = BigDecimal.ZERO;
    this.status = CustomerStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (customerType == null) {
      customerType = CustomerType.INDIVIDUAL;
    }
    if (creditLimit == null) {
      creditLimit = BigDecimal.ZERO;
    }
    if (outstandingBalance == null) {
      outstandingBalance = BigDecimal.ZERO;
    }
    if (status == null) {
      status = CustomerStatus.ACTIVE;
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
      Branch branch,
      String customerCode,
      String name,
      String contactPerson,
      String phone,
      String email,
      String address,
      String kraPin,
      CustomerType customerType,
      BigDecimal creditLimit,
      BigDecimal outstandingBalance,
      String paymentTerms,
      String notes,
      CustomerStatus status) {
    this.branch = branch;
    this.customerCode = customerCode;
    this.name = name;
    this.contactPerson = contactPerson;
    this.phone = phone;
    this.email = email;
    this.address = address;
    this.kraPin = kraPin;
    this.customerType = customerType == null ? CustomerType.INDIVIDUAL : customerType;
    this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
    this.outstandingBalance = outstandingBalance == null ? BigDecimal.ZERO : outstandingBalance;
    this.paymentTerms = paymentTerms;
    this.notes = notes;
    this.status = status == null ? CustomerStatus.ACTIVE : status;
  }

  public void clearBalances() {
    this.outstandingBalance = BigDecimal.ZERO;
    this.creditLimit = BigDecimal.ZERO;
    this.notes = null;
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public Branch getBranch() {
    return branch;
  }

  public String getCustomerCode() {
    return customerCode;
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

  public String getAddress() {
    return address;
  }

  public String getKraPin() {
    return kraPin;
  }

  public CustomerType getCustomerType() {
    return customerType;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public BigDecimal getOutstandingBalance() {
    return outstandingBalance;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public String getNotes() {
    return notes;
  }

  public CustomerStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
