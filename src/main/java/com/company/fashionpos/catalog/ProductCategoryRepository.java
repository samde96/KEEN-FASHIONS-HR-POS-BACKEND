package com.company.fashionpos.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

  @Query(
      """
            select category
            from ProductCategory category
            where category.organization.id = :organizationId
            order by category.name
            """)
  List<ProductCategory> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select category
            from ProductCategory category
            where category.id = :categoryId
              and category.organization.id = :organizationId
            """)
  Optional<ProductCategory> findWithinOrganization(
      @Param("categoryId") UUID categoryId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(category) > 0
            from ProductCategory category
            where category.organization.id = :organizationId
              and lower(category.code) = lower(:code)
            """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);

  @Query(
      """
            select count(category) > 0
            from ProductCategory category
            where category.organization.id = :organizationId
              and lower(category.code) = lower(:code)
              and category.id <> :categoryId
            """)
  boolean existsByCodeWithinOrganizationExcludingCategory(
      @Param("organizationId") UUID organizationId,
      @Param("code") String code,
      @Param("categoryId") UUID categoryId);
}
