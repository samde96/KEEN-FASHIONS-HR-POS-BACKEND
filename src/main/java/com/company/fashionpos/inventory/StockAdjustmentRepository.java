package com.company.fashionpos.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

  @Query(
      """
            select adjustment
            from StockAdjustment adjustment
              join fetch adjustment.branch
              join fetch adjustment.product product
              join fetch product.category
            where adjustment.organization.id = :organizationId
            order by adjustment.adjustedAt desc
            """)
  List<StockAdjustment> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select adjustment
            from StockAdjustment adjustment
              join fetch adjustment.branch
              join fetch adjustment.product product
              join fetch product.category
            where adjustment.organization.id = :organizationId
              and adjustment.adjustedAt >= :fromInclusive
              and adjustment.adjustedAt < :toExclusive
            order by adjustment.adjustedAt desc
            """)
  List<StockAdjustment> findWithinOrganizationAndPeriod(
      @Param("organizationId") UUID organizationId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive);
}
