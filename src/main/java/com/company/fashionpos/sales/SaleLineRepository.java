package com.company.fashionpos.sales;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {

  @Query(
      """
            select line
            from SaleLine line
              join fetch line.product
            where line.sale.id = :saleId
            order by line.productName
            """)
  List<SaleLine> findForSale(@Param("saleId") UUID saleId);
}
