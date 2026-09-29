package com.company.fashionpos.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockIntakeRepository extends JpaRepository<StockIntake, UUID> {

  @Query(
      """
            select intake
            from StockIntake intake
              join fetch intake.branch
              join fetch intake.product product
              join fetch product.category
            where intake.organization.id = :organizationId
            order by intake.receivedAt desc
            """)
  List<StockIntake> findWithinOrganization(@Param("organizationId") UUID organizationId);
}
