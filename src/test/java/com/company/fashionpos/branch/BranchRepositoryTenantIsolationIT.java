package com.company.fashionpos.branch;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BranchRepositoryTenantIsolationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("fashion_pos_test")
          .withUsername("fashion_pos_app")
          .withPassword("test-password");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private BranchRepository branchRepository;

  @Autowired private UserAccountRepository userAccountRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedTenantScopedBranchData() {
    jdbcTemplate.update("delete from user_branch_assignments");
    jdbcTemplate.update("delete from user_roles");
    jdbcTemplate.update("delete from roles");
    jdbcTemplate.update("delete from users");
    jdbcTemplate.update("delete from registers");
    jdbcTemplate.update("delete from branches");
    jdbcTemplate.update("delete from organizations");

    jdbcTemplate.update(
        """
        insert into organizations (id, name, currency_code, time_zone, status)
        values
          ('00000000-0000-4000-8000-000000000001', 'KEEN Fashion', 'KES', 'Africa/Nairobi', 'ACTIVE'),
          ('00000000-0000-4000-8000-000000000002', 'External Tenant Sample', 'KES', 'Africa/Nairobi', 'ACTIVE')
        """);
    jdbcTemplate.update(
        """
        insert into branches (id, organization_id, name, code, time_zone, status)
        values
          ('00000000-0000-4000-8000-000000000101', '00000000-0000-4000-8000-000000000001', 'Nairobi CBD', 'CBD', 'Africa/Nairobi', 'ACTIVE'),
          ('00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000000001', 'Westlands', 'WST', 'Africa/Nairobi', 'ACTIVE'),
          ('00000000-0000-4000-8000-000000000201', '00000000-0000-4000-8000-000000000002', 'Other Tenant Branch', 'OTH', 'Africa/Nairobi', 'ACTIVE')
        """);
    jdbcTemplate.update(
        """
        insert into users (id, organization_id, email, display_name, status)
        values
          ('00000000-0000-4000-8000-000000000302', '00000000-0000-4000-8000-000000000001', 'manager@keen.local', 'Westlands Manager', 'ACTIVE')
        """);
    jdbcTemplate.update(
        """
        insert into user_branch_assignments (id, user_id, branch_id)
        values
          ('00000000-0000-4000-8000-000000000603', '00000000-0000-4000-8000-000000000302', '00000000-0000-4000-8000-000000000102')
        """);
  }

  @Test
  void listsOnlyBranchesAssignedToTheAuthenticatedUsersOrganization() {
    var manager =
        userAccountRepository
            .findByEmailIgnoreCaseAndStatus("manager@keen.local", UserStatus.ACTIVE)
            .orElseThrow();

    var branches =
        branchRepository.findAuthorizedBranches(manager.getOrganization().getId(), manager.getId());

    assertThat(branches).extracting(Branch::getCode).containsExactly("WST");
  }

  @Test
  void branchExistenceCheckDoesNotCrossTenantBoundaries() {
    var keenOrganizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    var otherTenantBranchId = UUID.fromString("00000000-0000-4000-8000-000000000201");

    boolean visible =
        branchRepository.existsWithinOrganization(otherTenantBranchId, keenOrganizationId);

    assertThat(visible).isFalse();
  }
}
