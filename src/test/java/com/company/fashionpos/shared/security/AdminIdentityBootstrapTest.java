package com.company.fashionpos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminIdentityBootstrapTest {

  @Test
  void createsConfiguredOrganizationAdminAndRole() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    createBootstrapTables(jdbcTemplate);
    var bootstrap =
        new AdminIdentityBootstrap(
            jdbcTemplate,
            passwordEncoder,
            " Admin@Example.COM ",
            " admin-password ",
            " KEEN Retail ",
            " kes ",
            " Africa/Nairobi ",
            " Admin User ");

    bootstrap.run(null);

    assertThat(
            jdbcTemplate.queryForObject(
                "select name from organizations where id = ?",
                String.class,
                UUID.fromString("00000000-0000-4000-8000-000000000001")))
        .isEqualTo("KEEN Retail");
    assertThat(
            jdbcTemplate.queryForObject(
                "select currency_code from organizations where id = ?",
                String.class,
                UUID.fromString("00000000-0000-4000-8000-000000000001")))
        .isEqualTo("KES");
    assertThat(
            jdbcTemplate.queryForObject(
                "select email from users where id = ?",
                String.class,
                UUID.fromString("00000000-0000-4000-8000-000000000301")))
        .isEqualTo("admin@example.com");
    assertThat(
            jdbcTemplate.queryForObject(
                "select display_name from users where id = ?",
                String.class,
                UUID.fromString("00000000-0000-4000-8000-000000000301")))
        .isEqualTo("Admin User");
    String passwordHash =
        jdbcTemplate.queryForObject(
            "select password_hash from users where id = ?",
            String.class,
            UUID.fromString("00000000-0000-4000-8000-000000000301"));
    assertThat(passwordHash).isNotEqualTo("admin-password");
    assertThat(passwordEncoder.matches("admin-password", passwordHash)).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from user_roles where user_id = ?",
                Integer.class,
                UUID.fromString("00000000-0000-4000-8000-000000000301")))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select role_key from roles where id = ?",
                String.class,
                UUID.fromString("00000000-0000-4000-8000-000000000401")))
        .isEqualTo("ADMIN");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from role_permissions where role_id = ?",
                Integer.class,
                UUID.fromString("00000000-0000-4000-8000-000000000401")))
        .isEqualTo(1);
  }

  @Test
  void requiresConfiguredAdminEmail() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    createBootstrapTables(jdbcTemplate);
    var bootstrap =
        new AdminIdentityBootstrap(
            jdbcTemplate,
            new BCryptPasswordEncoder(4),
            "",
            "admin-password",
            "KEEN Retail",
            "KES",
            "Africa/Nairobi",
            "Admin User");

    assertThatThrownBy(() -> bootstrap.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("FASHIONPOS_ADMIN_EMAIL must be set in .env");
  }

  private static JdbcTemplate jdbcTemplate() {
    var dataSource =
        new DriverManagerDataSource(
            "jdbc:h2:mem:"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    return new JdbcTemplate(dataSource);
  }

  private static void createBootstrapTables(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.execute(
        """
        create table organizations (
          id uuid primary key,
          name varchar(160) not null,
          currency_code char(3) not null,
          time_zone varchar(64) not null,
          status varchar(32) not null,
          created_at timestamp not null default now(),
          updated_at timestamp not null default now(),
          version bigint not null default 0
        )
        """);
    jdbcTemplate.execute(
        """
        create table users (
          id uuid primary key,
          organization_id uuid not null,
          email varchar(254) not null,
          display_name varchar(160) not null,
          password_hash varchar(255),
          status varchar(32) not null,
          created_at timestamp not null default now(),
          updated_at timestamp not null default now(),
          version bigint not null default 0
        )
        """);
    jdbcTemplate.execute(
        """
        create table roles (
          id uuid primary key,
          organization_id uuid not null,
          name varchar(80) not null,
          role_key varchar(80) not null,
          description varchar(255),
          created_at timestamp not null default now(),
          updated_at timestamp not null default now(),
          version bigint not null default 0
        )
        """);
    jdbcTemplate.execute(
        """
        create table permissions (
          id uuid primary key,
          code varchar(120) not null,
          description varchar(255),
          created_at timestamp not null default now()
        )
        """);
    jdbcTemplate.update(
        """
        insert into permissions (id, code, description)
        values (?, 'admin:manage', 'Manage users, roles, branches, products, and settings')
        """,
        UUID.fromString("00000000-0000-4000-8000-000000000501"));
    jdbcTemplate.execute(
        """
        create table role_permissions (
          role_id uuid not null,
          permission_id uuid not null,
          primary key (role_id, permission_id)
        )
        """);
    jdbcTemplate.execute(
        """
        create table user_roles (
          user_id uuid not null,
          role_id uuid not null,
          primary key (user_id, role_id)
        )
        """);
  }
}
