package com.company.fashionpos.customers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  @Query(
      """
            select customer
            from Customer customer
              left join fetch customer.branch
            where customer.organization.id = :organizationId
            order by customer.name
            """)
  List<Customer> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select customer
            from Customer customer
              left join fetch customer.branch
            where customer.id = :customerId
              and customer.organization.id = :organizationId
            """)
  Optional<Customer> findWithinOrganization(
      @Param("customerId") UUID customerId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(customer) > 0
            from Customer customer
            where customer.organization.id = :organizationId
              and lower(customer.customerCode) = lower(:customerCode)
            """)
  boolean existsByCustomerCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("customerCode") String customerCode);

  @Query(
      """
            select count(customer) > 0
            from Customer customer
            where customer.organization.id = :organizationId
              and lower(customer.customerCode) = lower(:customerCode)
              and customer.id <> :customerId
            """)
  boolean existsByCustomerCodeWithinOrganizationExcludingCustomer(
      @Param("organizationId") UUID organizationId,
      @Param("customerCode") String customerCode,
      @Param("customerId") UUID customerId);
}
