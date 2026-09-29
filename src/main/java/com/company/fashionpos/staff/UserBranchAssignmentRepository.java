package com.company.fashionpos.staff;

import com.company.fashionpos.branch.Branch;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBranchAssignmentRepository extends JpaRepository<UserBranchAssignment, UUID> {

  @Query(
      """
            select assignment.branch.id
            from UserBranchAssignment assignment
            where assignment.user.id = :userId
              and assignment.branch.organization.id = :organizationId
            """)
  Set<UUID> findAuthorizedBranchIds(
      @Param("userId") UUID userId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select assignment.branch
            from UserBranchAssignment assignment
            where assignment.user.id = :userId
              and assignment.branch.organization.id = :organizationId
              and assignment.branch.status = com.company.fashionpos.branch.BranchStatus.ACTIVE
            order by assignment.branch.name
            """)
  List<Branch> findActiveBranchesForUser(
      @Param("userId") UUID userId, @Param("organizationId") UUID organizationId);
}
