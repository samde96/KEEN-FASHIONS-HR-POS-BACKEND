package com.company.fashionpos.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

  @Query(
      """
            select transfer
            from StockTransfer transfer
              join fetch transfer.sourceBranch
              join fetch transfer.destinationBranch
              join fetch transfer.product product
              join fetch product.category
            where transfer.organization.id = :organizationId
            order by transfer.transferredAt desc
            """)
  List<StockTransfer> findWithinOrganization(@Param("organizationId") UUID organizationId);
}
