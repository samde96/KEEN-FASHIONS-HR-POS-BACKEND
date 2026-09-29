package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

  @Query(
      """
      select request
      from LeaveRequest request
        join fetch request.employee employee
        join fetch employee.organization
        join fetch request.branch
        join fetch request.leaveType
        left join fetch request.approverUser
        left join fetch employee.department
        left join fetch employee.jobTitle
      where request.organization.id = :organizationId
      order by request.createdAt desc
      """)
  List<LeaveRequest> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select request
      from LeaveRequest request
        join fetch request.employee employee
        join fetch employee.organization
        join fetch request.branch
        join fetch request.leaveType
        left join fetch request.approverUser
        left join fetch employee.department
        left join fetch employee.jobTitle
      where request.organization.id = :organizationId
        and request.id = :requestId
      """)
  Optional<LeaveRequest> findWithinOrganization(
      @Param("requestId") UUID requestId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(request) > 0
      from LeaveRequest request
      where request.employee.id = :employeeId
        and request.id <> coalesce(:requestId, request.id)
        and request.status in ('SUBMITTED', 'PENDING_APPROVAL', 'APPROVED')
        and request.startDate <= :endDate
        and request.endDate >= :startDate
      """)
  boolean hasOverlappingRequest(
      @Param("employeeId") UUID employeeId,
      @Param("requestId") UUID requestId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
