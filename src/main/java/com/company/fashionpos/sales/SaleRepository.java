package com.company.fashionpos.sales;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

  @Query(
      """
            select sale
            from Sale sale
              join fetch sale.branch
              left join fetch sale.soldByEmployee soldByEmployee
              left join fetch soldByEmployee.user
            where sale.organization.id = :organizationId
            order by sale.soldAt desc
            """)
  List<Sale> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select sale
            from Sale sale
              join fetch sale.branch
              left join fetch sale.soldByEmployee soldByEmployee
              left join fetch soldByEmployee.user
            where sale.organization.id = :organizationId
              and sale.soldAt >= :fromInclusive
              and sale.soldAt < :toExclusive
            order by sale.soldAt desc
            """)
  List<Sale> findWithinOrganizationAndPeriod(
      @Param("organizationId") UUID organizationId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);

  @Query(
      """
            select sale
            from Sale sale
              join fetch sale.branch
              left join fetch sale.soldByEmployee soldByEmployee
              left join fetch soldByEmployee.user
            where sale.id = :saleId
              and sale.organization.id = :organizationId
            """)
  Optional<Sale> findWithinOrganization(
      @Param("saleId") UUID saleId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select sale
            from Sale sale
              join fetch sale.branch
              left join fetch sale.soldByEmployee soldByEmployee
              left join fetch soldByEmployee.user
            where sale.organization.id = :organizationId
              and sale.idempotencyKey = :idempotencyKey
            """)
  Optional<Sale> findByIdempotencyKeyWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("idempotencyKey") String idempotencyKey);

  @Query(
      """
            select sale
            from Sale sale
              join fetch sale.branch
              join fetch sale.soldByEmployee soldByEmployee
              join fetch soldByEmployee.primaryBranch
              left join fetch soldByEmployee.department
              left join fetch soldByEmployee.jobTitle
            where sale.organization.id = :organizationId
              and sale.soldAt >= :fromInclusive
              and sale.soldAt < :toExclusive
            order by sale.soldAt desc
            """)
  List<Sale> findAttributedSalesWithinPeriod(
      @Param("organizationId") UUID organizationId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);
}
