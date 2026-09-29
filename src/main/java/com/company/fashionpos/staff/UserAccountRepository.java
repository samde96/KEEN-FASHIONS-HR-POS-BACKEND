package com.company.fashionpos.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmailIgnoreCaseAndStatus(String email, UserStatus status);

  @Query(
      """
            select user
            from UserAccount user
            where user.organization.id = :organizationId
            order by user.displayName
            """)
  List<UserAccount> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select user
            from UserAccount user
            where user.id = :userId
              and user.organization.id = :organizationId
            """)
  Optional<UserAccount> findWithinOrganization(
      @Param("userId") UUID userId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(user) > 0
            from UserAccount user
            where user.organization.id = :organizationId
              and lower(user.email) = lower(:email)
            """)
  boolean existsByEmailWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("email") String email);

  @Query(
      """
            select count(user) > 0
            from UserAccount user
            where user.organization.id = :organizationId
              and lower(user.email) = lower(:email)
              and user.id <> :userId
            """)
  boolean existsByEmailWithinOrganizationExcludingUser(
      @Param("organizationId") UUID organizationId,
      @Param("email") String email,
      @Param("userId") UUID userId);
}
