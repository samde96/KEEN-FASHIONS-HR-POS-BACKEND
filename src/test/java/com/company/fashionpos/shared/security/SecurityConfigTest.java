package com.company.fashionpos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

  private final SecurityConfig securityConfig = new SecurityConfig();
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

  @Test
  void userCredentialsAreLoadedFromStoredPasswordHashes() {
    UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    UserAccount account =
        userAccount("Admin@Example.COM", passwordEncoder.encode("admin-only-password"));
    when(userAccountRepository.findByEmailIgnoreCaseAndStatus(
            "admin@example.com", UserStatus.ACTIVE))
        .thenReturn(Optional.of(account));
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForList(
            argThat(sql -> sql != null && sql.contains("role.role_key")), eq(String.class), any()))
        .thenReturn(java.util.List.of("ADMIN"));
    when(jdbcTemplate.queryForList(
            argThat(sql -> sql != null && sql.contains("permission.code")),
            eq(String.class),
            any()))
        .thenReturn(java.util.List.of("admin:manage"));
    var users = securityConfig.userDetailsService(userAccountRepository, jdbcTemplate);

    var owner = users.loadUserByUsername("admin@example.com");

    assertThat(passwordEncoder.matches("admin-only-password", owner.getPassword())).isTrue();
    assertThat(owner.getAuthorities())
        .extracting("authority")
        .contains("ROLE_USER", "ROLE_ADMIN", "admin:manage");
    assertThatThrownBy(() -> users.loadUserByUsername("owner@keen.local"))
        .isInstanceOf(UsernameNotFoundException.class);
  }

  @Test
  void userWithoutPasswordHashCannotAuthenticate() {
    UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    UserAccount account = userAccount("admin@example.com", null);
    when(userAccountRepository.findByEmailIgnoreCaseAndStatus(
            "admin@example.com", UserStatus.ACTIVE))
        .thenReturn(Optional.of(account));
    var users = securityConfig.userDetailsService(userAccountRepository, mock(JdbcTemplate.class));

    assertThatThrownBy(() -> users.loadUserByUsername("admin@example.com"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessage("Invalid email or password");
  }

  @Test
  void wildcardCorsOriginsAreRejectedWhenCredentialsAreAllowed() {
    assertThatThrownBy(() -> securityConfig.corsConfigurationSource("*"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Wildcard CORS origins are not allowed with credentials");
  }

  private static UserAccount userAccount(String email, String passwordHash) {
    UUID organizationId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    UserAccount account =
        new UserAccount(
            UUID.fromString("00000000-0000-4000-8000-000000000301"),
            new Organization(organizationId, "KEEN Fashions", "KES", "Africa/Nairobi"),
            email,
            "Admin User");
    account.updatePasswordHash(passwordHash);
    return account;
  }
}
