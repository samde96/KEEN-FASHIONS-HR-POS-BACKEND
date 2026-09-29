package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  @Query(
      """
      select employee
      from Employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        left join fetch employee.user
        left join fetch employee.manager
      where employee.organization.id = :organizationId
      order by employee.lastName, employee.firstName
      """)
  List<Employee> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select employee
      from Employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department department
        left join fetch employee.jobTitle jobTitle
        left join fetch employee.user
        left join fetch employee.manager manager
        left join fetch manager.primaryBranch
      where employee.id = :employeeId
        and employee.organization.id = :organizationId
      """)
  Optional<Employee> findWithinOrganization(
      @Param("employeeId") UUID employeeId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(employee) > 0
      from Employee employee
      where employee.organization.id = :organizationId
        and lower(employee.employeeNumber) = lower(:employeeNumber)
      """)
  boolean existsByEmployeeNumberWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("employeeNumber") String employeeNumber);

  @Query(
      """
      select count(employee) > 0
      from Employee employee
      where employee.organization.id = :organizationId
        and lower(employee.employeeNumber) = lower(:employeeNumber)
        and employee.id <> :employeeId
      """)
  boolean existsByEmployeeNumberWithinOrganizationExcludingEmployee(
      @Param("organizationId") UUID organizationId,
      @Param("employeeNumber") String employeeNumber,
      @Param("employeeId") UUID employeeId);

  @Query(
      """
      select count(employee) > 0
      from Employee employee
      where employee.user.id = :userId
      """)
  boolean existsByUserId(@Param("userId") UUID userId);

  @Query(
      """
      select employee
      from Employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        left join fetch employee.user
      where employee.organization.id = :organizationId
        and employee.user.id = :userId
      """)
  Optional<Employee> findByUserWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("userId") UUID userId);
}
