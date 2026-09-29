package com.company.fashionpos.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class StaffServiceTest {

  @Test
  void createUserHashesPasswordBeforeSaving() {
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    StaffService service = service(userAccountRepository, passwordEncoder);
    UUID organizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    UserResponse response =
        service.createUser(
            authenticatedUser(organizationId),
            new UserRequest(
                "cashier@test.local",
                "cashier-password",
                "Cashier User",
                UserStatus.ACTIVE,
                Set.of(),
                Set.of()));

    ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
    verify(userAccountRepository).saveAndFlush(accountCaptor.capture());
    UserAccount savedAccount = accountCaptor.getValue();

    assertThat(response.email()).isEqualTo("cashier@test.local");
    assertThat(savedAccount.getPasswordHash()).isNotBlank();
    assertThat(savedAccount.getPasswordHash()).isNotEqualTo("cashier-password");
    assertThat(passwordEncoder.matches("cashier-password", savedAccount.getPasswordHash()))
        .isTrue();
  }

  @Test
  void createUserRequiresPassword() {
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    StaffService service = service(userAccountRepository, passwordEncoder);
    UUID organizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    assertThatThrownBy(
            () ->
                service.createUser(
                    authenticatedUser(organizationId),
                    new UserRequest(
                        "cashier@test.local",
                        null,
                        "Cashier User",
                        UserStatus.ACTIVE,
                        Set.of(),
                        Set.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Password is required");

    verify(userAccountRepository, never()).saveAndFlush(any());
  }

  @Test
  void createRoleRejectsOwnerKey() {
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    StaffService service = service(userAccountRepository, passwordEncoder);
    UUID organizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    assertThatThrownBy(
            () ->
                service.createRole(
                    authenticatedUser(organizationId),
                    new RoleRequest("Owner", "OWNER", "Legacy owner access", Set.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Owner role is no longer supported");
  }

  private static StaffService service(
      UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
    RoleRepository roleRepository = mock(RoleRepository.class);
    BranchRepository branchRepository = mock(BranchRepository.class);
    OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    UUID organizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    when(userAccountRepository.existsByEmailWithinOrganization(eq(organizationId), anyString()))
        .thenReturn(false);
    when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(organizationRepository.findById(organizationId))
        .thenReturn(
            Optional.of(
                new Organization(organizationId, "KEEN Fashions", "KES", "Africa/Nairobi")));
    when(jdbcTemplate.queryForList(anyString(), eq(UUID.class), any())).thenReturn(List.of());
    when(jdbcTemplate.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of());

    return new StaffService(
        userAccountRepository,
        roleRepository,
        branchRepository,
        organizationRepository,
        jdbcTemplate,
        passwordEncoder);
  }

  private static AuthenticatedUser authenticatedUser(UUID organizationId) {
    return new AuthenticatedUser(
        UUID.fromString("00000000-0000-4000-8000-000000000301"),
        "admin@test.local",
        "Admin User",
        organizationId,
        Set.of(),
        Set.of("ADMIN"),
        Set.of("Admin"),
        Set.of());
  }
}
