package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

  @Query(
      """
      select balance
      from LeaveBalance balance
        join fetch balance.employee employee
        join fetch balance.leaveType leaveType
      where employee.organization.id = :organizationId
      order by employee.lastName, employee.firstName, leaveType.name
      """)
  List<LeaveBalance> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select balance
      from LeaveBalance balance
        join fetch balance.employee employee
        join fetch balance.leaveType leaveType
      where employee.id = :employeeId
        and leaveType.id = :leaveTypeId
      """)
  Optional<LeaveBalance> findByEmployeeAndLeaveType(
      @Param("employeeId") UUID employeeId, @Param("leaveTypeId") UUID leaveTypeId);
}
