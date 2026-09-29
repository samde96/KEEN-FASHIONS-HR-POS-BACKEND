package com.company.fashionpos.branch;

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
@Table(name = "branches")
public class Branch {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 24)
  private String code;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BranchStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Branch() {}

  public Branch(UUID id, Organization organization, String name, String code, String timeZone) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.code = code;
    this.timeZone = timeZone;
    this.status = BranchStatus.ACTIVE;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = BranchStatus.ACTIVE;
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void updateDetails(String name, String code, String timeZone, BranchStatus status) {
    this.name = name;
    this.code = code;
    this.timeZone = timeZone;
    this.status = status;
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

  public String getTimeZone() {
    return timeZone;
  }

  public BranchStatus getStatus() {
    return status;
  }
}
