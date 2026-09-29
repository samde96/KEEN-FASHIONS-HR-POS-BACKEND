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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_titles")
public class JobTitle {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = 40)
  private String code;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected JobTitle() {}

  public JobTitle(
      UUID id, Organization organization, Department department, String title, String code) {
    this.id = id;
    this.organization = organization;
    this.department = department;
    this.title = title;
    this.code = code;
    this.active = true;
  }

  public void updateDetails(
      Department department, String title, String code, String description, boolean active) {
    this.department = department;
    this.title = title;
    this.code = code;
    this.description = description;
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

  public Department getDepartment() {
    return department;
  }

  public Organization getOrganization() {
    return organization;
  }

  public String getTitle() {
    return title;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }
}
