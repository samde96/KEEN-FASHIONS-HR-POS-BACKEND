package com.company.fashionpos.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

  @Query(
      """
            select role
            from Role role
            where role.organization.id = :organizationId
            order by role.name
            """)
  List<Role> findWithinOrganization(@Param("organizationId") UUID organizationId);

  @Query(
      """
            select role
            from Role role
            where role.id = :roleId
              and role.organization.id = :organizationId
            """)
  Optional<Role> findWithinOrganization(
      @Param("roleId") UUID roleId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(role) > 0
            from Role role
            where role.id = :roleId
              and role.organization.id = :organizationId
            """)
  boolean existsWithinOrganization(
      @Param("roleId") UUID roleId, @Param("organizationId") UUID organizationId);

  @Query(
      """
            select count(role) > 0
            from Role role
            where role.organization.id = :organizationId
              and lower(role.name) = lower(:name)
            """)
  boolean existsByNameWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("name") String name);

  @Query(
      """
            select count(role) > 0
            from Role role
            where role.organization.id = :organizationId
              and lower(role.key) = lower(:key)
            """)
  boolean existsByKeyWithinOrganization(
      @Param("organizationId") UUID organizationId, @Param("key") String key);

  @Query(
      """
            select count(role) > 0
            from Role role
            where role.organization.id = :organizationId
              and lower(role.name) = lower(:name)
              and role.id <> :roleId
            """)
  boolean existsByNameWithinOrganizationExcludingRole(
      @Param("organizationId") UUID organizationId,
      @Param("name") String name,
      @Param("roleId") UUID roleId);

  @Query(
      """
            select count(role) > 0
            from Role role
            where role.organization.id = :organizationId
              and lower(role.key) = lower(:key)
              and role.id <> :roleId
            """)
  boolean existsByKeyWithinOrganizationExcludingRole(
      @Param("organizationId") UUID organizationId,
      @Param("key") String key,
      @Param("roleId") UUID roleId);
}
