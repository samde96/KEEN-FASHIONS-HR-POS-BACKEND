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
@Table(name = "leave_requests")
public class LeaveRequest {

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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approver_user_id")
  private UserAccount approverUser;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "requested_days", nullable = false, precision = 8, scale = 2)
  private BigDecimal requestedDays;

  @Column(length = 2000)
  private String reason;

  @Column(name = "approver_comments", length = 1000)
  private String approverComments;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LeaveRequestStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected LeaveRequest() {}

  public LeaveRequest(
      UUID id,
      Organization organization,
      Employee employee,
      Branch branch,
      LeaveType leaveType,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal requestedDays) {
    this.id = id;
    this.organization = organization;
    this.employee = employee;
    this.branch = branch;
    this.leaveType = leaveType;
    this.startDate = startDate;
    this.endDate = endDate;
    this.requestedDays = requestedDays;
    this.status = LeaveRequestStatus.DRAFT;
  }

  public void updateDetails(
      Employee employee,
      Branch branch,
      LeaveType leaveType,
      UserAccount approverUser,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal requestedDays,
      String reason,
      String approverComments,
      LeaveRequestStatus status,
      Instant submittedAt,
      Instant decidedAt) {
    this.employee = employee;
    this.branch = branch;
    this.leaveType = leaveType;
    this.approverUser = approverUser;
    this.startDate = startDate;
    this.endDate = endDate;
    this.requestedDays = requestedDays;
    this.reason = reason;
    this.approverComments = approverComments;
    this.status = status;
    this.submittedAt = submittedAt;
    this.decidedAt = decidedAt;
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

  public LeaveType getLeaveType() {
    return leaveType;
  }

  public UserAccount getApproverUser() {
    return approverUser;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public BigDecimal getRequestedDays() {
    return requestedDays;
  }

  public String getReason() {
    return reason;
  }

  public String getApproverComments() {
    return approverComments;
  }

  public LeaveRequestStatus getStatus() {
    return status;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
