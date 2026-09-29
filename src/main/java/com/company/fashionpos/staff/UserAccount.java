package com.company.fashionpos.staff;

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
@Table(name = "users")
public class UserAccount {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 160)
  private String displayName;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UserStatus status;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected UserAccount() {}

  public UserAccount(UUID id, Organization organization, String email, String displayName) {
    this.id = id;
    this.organization = organization;
    this.email = email;
    this.displayName = displayName;
    this.status = UserStatus.ACTIVE;
  }

  public void updateDetails(String email, String displayName, UserStatus status) {
    this.email = email;
    this.displayName = displayName;
    this.status = status == null ? UserStatus.ACTIVE : status;
  }

  public void updatePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void disable() {
    this.status = UserStatus.DISABLED;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = UserStatus.ACTIVE;
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

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }
}
