package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

  @Query(
      """
      select attendance
      from AttendanceRecord attendance
        join fetch attendance.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        join fetch attendance.branch
      where attendance.organization.id = :organizationId
      order by attendance.attendanceDate desc, employee.lastName asc, employee.firstName asc
      """)
  List<AttendanceRecord> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select attendance
      from AttendanceRecord attendance
        join fetch attendance.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        join fetch attendance.branch
      where attendance.organization.id = :organizationId
        and attendance.id = :attendanceId
      """)
  Optional<AttendanceRecord> findWithinOrganization(
      @Param("attendanceId") UUID attendanceId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select attendance
      from AttendanceRecord attendance
      where attendance.employee.id = :employeeId
        and attendance.attendanceDate = :attendanceDate
      """)
  Optional<AttendanceRecord> findByEmployeeAndDate(
      @Param("employeeId") UUID employeeId, @Param("attendanceDate") LocalDate attendanceDate);
}
