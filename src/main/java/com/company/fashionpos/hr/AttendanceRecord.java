package com.company.fashionpos.hr;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

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

  @Column(name = "attendance_date", nullable = false)
  private LocalDate attendanceDate;

  @Column(name = "clock_in")
  private OffsetDateTime clockIn;

  @Column(name = "clock_out")
  private OffsetDateTime clockOut;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AttendanceStatus status;

  @Column(name = "worked_minutes", nullable = false)
  private int workedMinutes;

  @Column(name = "overtime_minutes", nullable = false)
  private int overtimeMinutes;

  @Column(name = "late_minutes", nullable = false)
  private int lateMinutes;

  @Column(length = 1000)
  private String notes;

  @Column(name = "correction_reason", length = 500)
  private String correctionReason;

  @Column(nullable = false)
  private boolean corrected;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected AttendanceRecord() {}

  public AttendanceRecord(
      UUID id,
      Organization organization,
      Employee employee,
      Branch branch,
      LocalDate attendanceDate) {
    this.id = id;
    this.organization = organization;
    this.employee = employee;
    this.branch = branch;
    this.attendanceDate = attendanceDate;
    this.status = AttendanceStatus.ABSENT;
  }

  public void updateDetails(
      Employee employee,
      Branch branch,
      LocalDate attendanceDate,
      OffsetDateTime clockIn,
      OffsetDateTime clockOut,
      AttendanceStatus status,
      int expectedDailyMinutes,
      String notes,
      String correctionReason,
      boolean corrected) {
    this.employee = employee;
    this.branch = branch;
    this.attendanceDate = attendanceDate;
    this.clockIn = clockIn;
    this.clockOut = clockOut;
    this.status = status;
    this.notes = notes;
    this.correctionReason = correctionReason;
    this.corrected = corrected;
    recalculateDurations(expectedDailyMinutes);
  }

  private void recalculateDurations(int expectedDailyMinutes) {
    int normalizedExpectedDailyMinutes = Math.max(expectedDailyMinutes, 0);
    int actualWorkedMinutes = 0;
    if (clockIn != null && clockOut != null) {
      long durationMinutes = Duration.between(clockIn, clockOut).toMinutes();
      actualWorkedMinutes = (int) Math.max(durationMinutes, 0);
    }
    this.workedMinutes = actualWorkedMinutes;
    this.overtimeMinutes = Math.max(actualWorkedMinutes - normalizedExpectedDailyMinutes, 0);
    this.lateMinutes =
        status == AttendanceStatus.LATE && actualWorkedMinutes > 0
            ? Math.max(Math.min(30, normalizedExpectedDailyMinutes - actualWorkedMinutes), 0)
            : 0;
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

  public Employee getEmployee() {
    return employee;
  }

  public Branch getBranch() {
    return branch;
  }

  public LocalDate getAttendanceDate() {
    return attendanceDate;
  }

  public OffsetDateTime getClockIn() {
    return clockIn;
  }

  public OffsetDateTime getClockOut() {
    return clockOut;
  }

  public AttendanceStatus getStatus() {
    return status;
  }

  public int getWorkedMinutes() {
    return workedMinutes;
  }

  public int getOvertimeMinutes() {
    return overtimeMinutes;
  }

  public int getLateMinutes() {
    return lateMinutes;
  }

  public String getNotes() {
    return notes;
  }

  public String getCorrectionReason() {
    return correctionReason;
  }

  public boolean isCorrected() {
    return corrected;
  }
}
