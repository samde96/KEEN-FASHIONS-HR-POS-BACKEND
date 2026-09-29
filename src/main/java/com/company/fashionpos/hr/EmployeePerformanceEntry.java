package com.company.fashionpos.hr;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_performance_entries")
public class EmployeePerformanceEntry {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_user_id")
  private UserAccount createdByUser;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 32)
  private EmployeePerformanceEntryType entryType;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(precision = 14, scale = 2)
  private BigDecimal amount;

  @Column private Integer score;

  @Column(length = 1000)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected EmployeePerformanceEntry() {}

  public EmployeePerformanceEntry(
      UUID id,
      Organization organization,
      Employee employee,
      Branch branch,
      UserAccount createdByUser,
      EmployeePerformanceEntryType entryType,
      LocalDate entryDate,
      String title,
      BigDecimal amount,
      Integer score,
      String notes) {
    this.id = id;
    this.organization = organization;
    this.employee = employee;
    this.branch = branch;
    this.createdByUser = createdByUser;
    this.entryType = entryType;
    this.entryDate = entryDate;
    this.title = title;
    this.amount = amount;
    this.score = score;
    this.notes = notes;
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

  public Employee getEmployee() {
    return employee;
  }

  public Branch getBranch() {
    return branch;
  }

  public UserAccount getCreatedByUser() {
    return createdByUser;
  }

  public EmployeePerformanceEntryType getEntryType() {
    return entryType;
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public String getTitle() {
    return title;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Integer getScore() {
    return score;
  }

  public String getNotes() {
    return notes;
  }
}
