package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

  @Query(
      """
      select payrollRun
      from PayrollRun payrollRun
        join fetch payrollRun.payrollPeriod
        left join fetch payrollRun.branch
      where payrollRun.organization.id = :organizationId
      order by payrollRun.createdAt desc
      """)
  List<PayrollRun> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select payrollRun
      from PayrollRun payrollRun
        join fetch payrollRun.payrollPeriod
        left join fetch payrollRun.branch
      where payrollRun.organization.id = :organizationId
        and payrollRun.id = :runId
      """)
  Optional<PayrollRun> findWithinOrganization(
      @Param("runId") UUID runId, @Param("organizationId") UUID organizationId);
}
