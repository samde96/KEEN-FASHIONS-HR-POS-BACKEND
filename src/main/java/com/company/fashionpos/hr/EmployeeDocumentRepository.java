package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

  @Query(
      """
      select document
      from EmployeeDocument document
        join fetch document.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        left join fetch document.uploadedByUser
      where document.organization.id = :organizationId
      order by document.uploadedAt desc
      """)
  List<EmployeeDocument> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select document
      from EmployeeDocument document
        join fetch document.employee employee
        join fetch employee.primaryBranch
        left join fetch employee.department
        left join fetch employee.jobTitle
        left join fetch document.uploadedByUser
      where document.id = :documentId
        and document.organization.id = :organizationId
      """)
  Optional<EmployeeDocument> findWithinOrganization(
      @Param("documentId") UUID documentId, @Param("organizationId") UUID organizationId);
}
