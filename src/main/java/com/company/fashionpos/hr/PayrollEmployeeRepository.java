package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollEmployeeRepository extends JpaRepository<PayrollEmployee, UUID> {

  @Query(
      """
      select payrollEmployee
      from PayrollEmployee payrollEmployee
        join fetch payrollEmployee.payrollRun payrollRun
        join fetch payrollEmployee.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
      where payrollRun.organization.id = :organizationId
      order by employee.lastName, employee.firstName
      """)
  List<PayrollEmployee> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select payrollEmployee
      from PayrollEmployee payrollEmployee
        join fetch payrollEmployee.payrollRun payrollRun
        join fetch payrollEmployee.employee employee
      where payrollRun.id = :runId
      """)
  List<PayrollEmployee> findByRunId(@Param("runId") UUID runId);

  @Query(
      """
      select payrollEmployee
      from PayrollEmployee payrollEmployee
        join fetch payrollEmployee.payrollRun payrollRun
        join fetch payrollRun.payrollPeriod
        left join fetch payrollRun.branch
        join fetch payrollEmployee.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
      where payrollEmployee.id = :payrollEmployeeId
        and payrollRun.id = :runId
        and payrollRun.organization.id = :organizationId
      """)
  Optional<PayrollEmployee> findWithinRun(
      @Param("payrollEmployeeId") UUID payrollEmployeeId,
      @Param("runId") UUID runId,
      @Param("organizationId") UUID organizationId);
}
