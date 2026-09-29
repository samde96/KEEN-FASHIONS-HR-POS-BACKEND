package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

  @Query(
      """
      select department
      from Department department
        left join fetch department.branch
        left join fetch department.managerUser
      where department.organization.id = :organizationId
      order by department.name
      """)
  List<Department> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select department
      from Department department
        left join fetch department.branch
        left join fetch department.managerUser
      where department.id = :departmentId
        and department.organization.id = :organizationId
      """)
  Optional<Department> findWithinOrganization(
      @Param("departmentId") UUID departmentId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(department) > 0
      from Department department
      where department.organization.id = :organizationId
        and lower(department.code) = lower(:code)
      """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);

  @Query(
      """
      select count(department) > 0
      from Department department
      where department.organization.id = :organizationId
        and lower(department.code) = lower(:code)
        and department.id <> :departmentId
      """)
  boolean existsByCodeWithinOrganizationExcludingDepartment(
      @Param("organizationId") UUID organizationId,
      @Param("code") String code,
      @Param("departmentId") UUID departmentId);
}
