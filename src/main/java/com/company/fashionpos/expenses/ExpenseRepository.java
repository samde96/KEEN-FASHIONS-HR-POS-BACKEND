package com.company.fashionpos.expenses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

  @Query(
      """
            select expense
            from Expense expense
              join fetch expense.branch
            where expense.organization.id = :organizationId
            order by expense.incurredAt desc
            """)
  List<Expense> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select expense
            from Expense expense
              join fetch expense.branch
            where expense.id = :expenseId
              and expense.organization.id = :organizationId
            """)
  Optional<Expense> findWithinOrganization(
      @Param("expenseId") UUID expenseId, @Param("organizationId") UUID organizationId);
}
