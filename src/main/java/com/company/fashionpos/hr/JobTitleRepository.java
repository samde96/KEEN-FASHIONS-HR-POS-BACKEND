package com.company.fashionpos.hr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {

  @Query(
      """
      select jobTitle
      from JobTitle jobTitle
        join fetch jobTitle.department department
        left join fetch department.branch
      where jobTitle.organization.id = :organizationId
      order by jobTitle.title
      """)
  List<JobTitle> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select jobTitle
      from JobTitle jobTitle
        join fetch jobTitle.department department
        left join fetch department.branch
      where jobTitle.id = :jobTitleId
        and jobTitle.organization.id = :organizationId
      """)
  Optional<JobTitle> findWithinOrganization(
      @Param("jobTitleId") UUID jobTitleId, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(jobTitle) > 0
      from JobTitle jobTitle
      where jobTitle.organization.id = :organizationId
        and lower(jobTitle.code) = lower(:code)
      """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);

  @Query(
      """
      select count(jobTitle) > 0
      from JobTitle jobTitle
      where jobTitle.organization.id = :organizationId
        and lower(jobTitle.code) = lower(:code)
        and jobTitle.id <> :jobTitleId
      """)
  boolean existsByCodeWithinOrganizationExcludingJobTitle(
      @Param("organizationId") UUID organizationId,
      @Param("code") String code,
      @Param("jobTitleId") UUID jobTitleId);
}
