package com.company.fashionpos.staff;

import com.company.fashionpos.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_branch_assignments")
public class UserBranchAssignment {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserBranchAssignment() {}

  public UserBranchAssignment(UUID id, UserAccount user, Branch branch) {
    this.id = id;
    this.user = user;
    this.branch = branch;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getUser() {
    return user;
  }

  public Branch getBranch() {
    return branch;
  }
}
