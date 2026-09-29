package com.company.fashionpos.hr;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departments")
public class Department {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "branch_id")
  private Branch branch;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 40)
  private String code;

  @Column(length = 500)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_user_id")
  private UserAccount managerUser;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Department() {}

  public Department(UUID id, Organization organization, String name, String code) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.code = code;
    this.active = true;
  }

  public void updateDetails(
      Branch branch,
      String name,
      String code,
      String description,
      UserAccount managerUser,
      boolean active) {
    this.branch = branch;
    this.name = name;
    this.code = code;
    this.description = description;
    this.managerUser = managerUser;
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

  public Organization getOrganization() {
    return organization;
  }

  public Branch getBranch() {
    return branch;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public UserAccount getManagerUser() {
    return managerUser;
  }

  public boolean isActive() {
    return active;
  }
}
