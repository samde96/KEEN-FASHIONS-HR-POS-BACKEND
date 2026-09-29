package com.company.fashionpos.branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

  @Query(
      """
            select branch
            from Branch branch
            where branch.organization.id = :organizationId
            order by branch.name
            """)
  List<Branch> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select branch
            from Branch branch
            where branch.id = :branchId
              and branch.organization.id = :organizationId
            """)
  Optional<Branch> findWithinOrganization(
      @Param("branchId") UUID branchId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(branch) > 0
            from Branch branch
            where branch.organization.id = :organizationId
              and lower(branch.code) = lower(:code)
            """)
  boolean existsByCodeWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("code") String code);

  @Query(
      """
            select count(branch) > 0
            from Branch branch
            where branch.organization.id = :organizationId
              and lower(branch.code) = lower(:code)
              and branch.id <> :branchId
            """)
  boolean existsByCodeWithinOrganizationExcludingBranch(
      @Param("organizationId") UUID organizationId,
      @Param("code") String code,
      @Param("branchId") UUID branchId);

  @Query(
      """
            select assignment.branch
            from UserBranchAssignment assignment
            where assignment.user.id = :userId
              and assignment.branch.organization.id = :organizationId
              and assignment.branch.status = com.company.fashionpos.branch.BranchStatus.ACTIVE
            order by assignment.branch.name
            """)
  List<Branch> findAuthorizedBranches(
      @Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

  @Query(
      """
            select count(branch) > 0
            from Branch branch
            where branch.id = :branchId
              and branch.organization.id = :organizationId
            """)
  boolean existsWithinOrganization(
      @Param("branchId") UUID branchId, @Param("organizationId") UUID organizationId);
}
