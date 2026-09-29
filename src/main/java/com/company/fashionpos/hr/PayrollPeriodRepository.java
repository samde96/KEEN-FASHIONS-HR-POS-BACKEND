package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

  @Query(
      """
      select period
      from PayrollPeriod period
        left join fetch period.branch
      where period.organization.id = :organizationId
      order by period.periodStart desc
      """)
  List<PayrollPeriod> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select period
      from PayrollPeriod period
        left join fetch period.branch
      where period.organization.id = :organizationId
        and period.id = :periodId
      """)
  Optional<PayrollPeriod> findWithinOrganization(
      @Param("periodId") UUID periodId, @Param("organizationId") UUID organizationId);
}
