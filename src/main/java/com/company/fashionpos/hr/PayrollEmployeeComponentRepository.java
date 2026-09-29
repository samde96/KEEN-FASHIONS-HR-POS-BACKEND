package com.company.fashionpos.hr;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollEmployeeComponentRepository
    extends JpaRepository<PayrollEmployeeComponent, UUID> {

  @Query(
      """
      select component
      from PayrollEmployeeComponent component
        join fetch component.payrollEmployee payrollEmployee
        join fetch component.payrollComponent payrollComponent
      where payrollEmployee.id = :payrollEmployeeId
      order by payrollComponent.componentType, payrollComponent.name
      """)
  List<PayrollEmployeeComponent> findByPayrollEmployeeId(
      @Param("payrollEmployeeId") UUID payrollEmployeeId);
}
