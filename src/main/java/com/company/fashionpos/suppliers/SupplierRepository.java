package com.company.fashionpos.suppliers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

  @Query(
      """
            select supplier
            from Supplier supplier
            where supplier.organization.id = :organizationId
            order by supplier.name
            """)
  List<Supplier> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select supplier
            from Supplier supplier
            where supplier.id = :supplierId
              and supplier.organization.id = :organizationId
            """)
  Optional<Supplier> findWithinOrganization(
      @Param("supplierId") UUID supplierId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(supplier) > 0
            from Supplier supplier
            where supplier.organization.id = :organizationId
              and lower(supplier.name) = lower(:name)
            """)
  boolean existsByNameWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("name") String name);

  @Query(
      """
            select count(supplier) > 0
            from Supplier supplier
            where supplier.organization.id = :organizationId
              and lower(supplier.name) = lower(:name)
              and supplier.id <> :supplierId
            """)
  boolean existsByNameWithinOrganizationExcludingSupplier(
      @Param("organizationId") UUID organizationId,
      @Param("name") String name,
      @Param("supplierId") UUID supplierId);
}
