package com.company.fashionpos.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeePerformanceEntryRepository
    extends JpaRepository<EmployeePerformanceEntry, UUID> {

  @Query(
      """
      select entry
      from EmployeePerformanceEntry entry
        join fetch entry.employee employee
        join fetch entry.branch
        left join fetch entry.createdByUser
        left join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
      where entry.organization.id = :organizationId
        and entry.entryDate between :fromDate and :toDate
      order by entry.entryDate desc, entry.createdAt desc
      """)
  List<EmployeePerformanceEntry> findWithinPeriod(
      @Param("organizationId") UUID organizationId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
