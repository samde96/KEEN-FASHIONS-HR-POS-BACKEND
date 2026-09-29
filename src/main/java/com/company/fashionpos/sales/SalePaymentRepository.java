package com.company.fashionpos.sales;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalePaymentRepository extends JpaRepository<SalePayment, UUID> {

  @Query(
      """
            select payment
            from SalePayment payment
            where payment.sale.id = :saleId
            order by payment.createdAt
            """)
  List<SalePayment> findForSale(@Param("saleId") UUID saleId);
}
