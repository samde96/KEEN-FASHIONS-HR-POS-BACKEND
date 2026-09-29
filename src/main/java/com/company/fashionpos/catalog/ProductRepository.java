package com.company.fashionpos.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductItem, UUID> {

  @Query(
      """
            select product
            from ProductItem product
              join fetch product.category
            where product.organization.id = :organizationId
            order by product.name
            """)
  List<ProductItem> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select product
            from ProductItem product
              join fetch product.category
            where product.id = :productId
              and product.organization.id = :organizationId
            """)
  Optional<ProductItem> findWithinOrganization(
      @Param("productId") UUID productId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(product) > 0
            from ProductItem product
            where product.organization.id = :organizationId
              and lower(product.sku) = lower(:sku)
            """)
  boolean existsBySkuWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("sku") String sku);

  @Query(
      """
            select count(product)
            from ProductItem product
            where product.organization.id = :organizationId
            """)
  long countWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(product) > 0
            from ProductItem product
            where product.organization.id = :organizationId
              and lower(product.sku) = lower(:sku)
              and product.id <> :productId
            """)
  boolean existsBySkuWithinOrganizationExcludingProduct(
      @Param("organizationId") UUID organizationId,
      @Param("sku") String sku,
      @Param("productId") UUID productId);

  @Query(
      """
            select count(product) > 0
            from ProductItem product
            where product.organization.id = :organizationId
              and product.barcode is not null
              and lower(product.barcode) = lower(:barcode)
            """)
  boolean existsByBarcodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("barcode") String barcode);

  @Query(
      """
            select count(product) > 0
            from ProductItem product
            where product.organization.id = :organizationId
              and product.barcode is not null
              and lower(product.barcode) = lower(:barcode)
              and product.id <> :productId
            """)
  boolean existsByBarcodeWithinOrganizationExcludingProduct(
      @Param("organizationId") UUID organizationId,
      @Param("barcode") String barcode,
      @Param("productId") UUID productId);
}
