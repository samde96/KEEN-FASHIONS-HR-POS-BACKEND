package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

  @Query(
      """
      select leaveType
      from LeaveType leaveType
      where leaveType.organization.id = :organizationId
      order by leaveType.name
      """)
  List<LeaveType> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select leaveType
      from LeaveType leaveType
      where leaveType.organization.id = :organizationId
        and leaveType.id = :leaveTypeId
      """)
  Optional<LeaveType> findWithinOrganization(
      @Param("leaveTypeId") UUID leaveTypeId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(leaveType) > 0
      from LeaveType leaveType
      where leaveType.organization.id = :organizationId
        and lower(leaveType.code) = lower(:code)
      """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);

  @Query(
      """
      select count(leaveType) > 0
      from LeaveType leaveType
      where leaveType.organization.id = :organizationId
        and lower(leaveType.code) = lower(:code)
        and leaveType.id <> :leaveTypeId
      """)
  boolean existsByCodeWithinOrganizationExcludingLeaveType(
      @Param("organizationId") UUID organizationId,
      @Param("code") String code,
      @Param("leaveTypeId") UUID leaveTypeId);
}
