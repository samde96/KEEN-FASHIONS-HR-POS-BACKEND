package com.company.fashionpos.shared.security;

import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AdminIdentityBootstrap implements ApplicationRunner {

  private static final UUID DEFAULT_ORGANIZATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID BOOTSTRAP_ADMIN_USER_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000301");
  private static final UUID BOOTSTRAP_ADMIN_ROLE_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000401");
  private static final String ADMIN_ROLE_NAME = "Admin";
  private static final String ADMIN_ROLE_KEY = "ADMIN";

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;
  private final String organizationName;
  private final String currencyCode;
  private final String timeZone;
  private final String adminDisplayName;

  AdminIdentityBootstrap(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      @Value("${app.security.admin-email}") String adminEmail,
      @Value("${app.security.admin-password}") String adminPassword,
      @Value("${app.bootstrap.organization-name}") String organizationName,
      @Value("${app.bootstrap.currency-code}") String currencyCode,
      @Value("${app.bootstrap.time-zone}") String timeZone,
      @Value("${app.bootstrap.admin-display-name}") String adminDisplayName) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
    this.organizationName = organizationName;
    this.currencyCode = currencyCode;
    this.timeZone = timeZone;
    this.adminDisplayName = adminDisplayName;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    UUID organizationId = ensureOrganization();
    UUID adminUserId = ensureAdminUser(organizationId);
    UUID adminRoleId = ensureAdminRole(organizationId);
    ensureAdminRoleAssignment(adminUserId, adminRoleId);
    ensureAdminPermissions(adminRoleId);
  }

  private UUID ensureOrganization() {
    String configuredName = requiredCredential(organizationName, "FASHIONPOS_ORGANIZATION_NAME");
    String configuredCurrency =
        requiredCredential(currencyCode, "FASHIONPOS_CURRENCY_CODE").toUpperCase(Locale.ROOT);
    String configuredTimeZone = requiredCredential(timeZone, "FASHIONPOS_TIME_ZONE");

    if (exists("select count(*) from organizations where id = ?", DEFAULT_ORGANIZATION_ID)) {
      jdbcTemplate.update(
          """
          update organizations
          set name = ?, currency_code = ?, time_zone = ?, status = 'ACTIVE', updated_at = now()
          where id = ?
          """,
          configuredName,
          configuredCurrency,
          configuredTimeZone,
          DEFAULT_ORGANIZATION_ID);
      return DEFAULT_ORGANIZATION_ID;
    }

    jdbcTemplate.update(
        """
        insert into organizations (id, name, currency_code, time_zone, status, created_at, updated_at, version)
                values (?, ?, ?, ?, 'ACTIVE', now(), now(), ?)
        """,
        DEFAULT_ORGANIZATION_ID,
        configuredName,
        configuredCurrency,
        configuredTimeZone,
        0L);
    return DEFAULT_ORGANIZATION_ID;
  }

  private UUID ensureAdminUser(UUID organizationId) {
    String configuredEmail =
        requiredCredential(adminEmail, "FASHIONPOS_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
    String configuredPassword = requiredCredential(adminPassword, "FASHIONPOS_ADMIN_PASSWORD");
    String configuredDisplayName =
        requiredCredential(adminDisplayName, "FASHIONPOS_ADMIN_DISPLAY_NAME");
    String passwordHash = passwordEncoder.encode(configuredPassword);

    if (exists("select count(*) from users where id = ?", BOOTSTRAP_ADMIN_USER_ID)) {
      jdbcTemplate.update(
          """
          update users
          set organization_id = ?, email = ?, display_name = ?, password_hash = ?, status = 'ACTIVE', updated_at = now()
          where id = ?
          """,
          organizationId,
          configuredEmail,
          configuredDisplayName,
          passwordHash,
          BOOTSTRAP_ADMIN_USER_ID);
      return BOOTSTRAP_ADMIN_USER_ID;
    }

    jdbcTemplate.update(
        """
        insert into users (id, organization_id, email, display_name, password_hash, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), ?)
        """,
        BOOTSTRAP_ADMIN_USER_ID,
        organizationId,
        configuredEmail,
        configuredDisplayName,
        passwordHash,
        0L);
    return BOOTSTRAP_ADMIN_USER_ID;
  }

  private UUID ensureAdminRole(UUID organizationId) {
    UUID existingRoleId =
        findUuid(
            "select id from roles where organization_id = ? and lower(name) = lower(?)",
            organizationId,
            ADMIN_ROLE_NAME);
    if (existingRoleId != null) {
      jdbcTemplate.update(
          """
          update roles
          set name = ?, role_key = ?, description = ?, updated_at = now()
          where id = ?
          """,
          ADMIN_ROLE_NAME,
          ADMIN_ROLE_KEY,
          "Full system administration for this business",
          existingRoleId);
      return existingRoleId;
    }

    jdbcTemplate.update(
        """
        insert into roles (id, organization_id, name, role_key, description, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), now(), ?)
        """,
        BOOTSTRAP_ADMIN_ROLE_ID,
        organizationId,
        ADMIN_ROLE_NAME,
        ADMIN_ROLE_KEY,
        "Full system administration for this business",
        0L);
    return BOOTSTRAP_ADMIN_ROLE_ID;
  }

  private void ensureAdminRoleAssignment(UUID adminUserId, UUID adminRoleId) {
    if (!exists(
        "select count(*) from user_roles where user_id = ? and role_id = ?",
        adminUserId,
        adminRoleId)) {
      jdbcTemplate.update(
          "insert into user_roles (user_id, role_id) values (?, ?)", adminUserId, adminRoleId);
    }
  }

  private void ensureAdminPermissions(UUID adminRoleId) {
    jdbcTemplate.update(
        """
        insert into role_permissions (role_id, permission_id)
        select ?, permission.id
        from permissions permission
        on conflict do nothing
        """,
        adminRoleId);
  }

  private boolean exists(String sql, Object... args) {
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
    return count != null && count > 0;
  }

  private UUID findUuid(String sql, Object... args) {
    try {
      return jdbcTemplate.queryForObject(sql, UUID.class, args);
    } catch (EmptyResultDataAccessException exception) {
      return null;
    }
  }

  private static String requiredCredential(String configuredValue, String variableName) {
    if (configuredValue == null || configuredValue.isBlank()) {
      throw new IllegalStateException(variableName + " must be set in .env");
    }
    return configuredValue.trim();
  }
}
