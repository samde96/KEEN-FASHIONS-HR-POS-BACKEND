package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollComponentRepository extends JpaRepository<PayrollComponent, UUID> {

  @Query(
      """
      select component
      from PayrollComponent component
      where component.organization.id = :organizationId
      order by component.componentType, component.name
      """)
  List<PayrollComponent> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select component
      from PayrollComponent component
      where component.organization.id = :organizationId
        and component.id = :componentId
      """)
  Optional<PayrollComponent> findWithinOrganization(
      @Param("componentId") UUID componentId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(component) > 0
      from PayrollComponent component
      where component.organization.id = :organizationId
        and lower(component.code) = lower(:code)
      """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);
}
