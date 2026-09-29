package com.company.fashionpos.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

  @Query(
      """
            select item
            from InventoryItem item
              join fetch item.branch
              join fetch item.product product
              join fetch product.category
            where item.organization.id = :organizationId
            order by item.branch.name, product.name
            """)
  List<InventoryItem> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select item
            from InventoryItem item
              join fetch item.branch
              join fetch item.product product
              join fetch product.category
            where item.id = :itemId
              and item.organization.id = :organizationId
            """)
  Optional<InventoryItem> findWithinOrganization(
      @Param("itemId") UUID itemId, @Param("organizationId") UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select item
            from InventoryItem item
              join fetch item.branch
              join fetch item.product product
              join fetch product.category
            where item.organization.id = :organizationId
              and item.branch.id = :branchId
              and item.product.id = :productId
            """)
  Optional<InventoryItem> findForMutation(
      @Param("organizationId") UUID organizationId,
      @Param("branchId") UUID branchId,
      @Param("productId") UUID productId);
}
