package com.company.fashionpos.hr;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollSalesBonusRuleRepository
    extends JpaRepository<PayrollSalesBonusRule, UUID> {

  @Query(
      """
      select rule
      from PayrollSalesBonusRule rule
      where rule.organization.id = :organizationId
      """)
  Optional<PayrollSalesBonusRule> findByOrganizationId(
      @Param("organizationId") UUID organizationId);
}
