package com.company.fashionpos.shared.security;

import com.company.fashionpos.staff.UserAccount;
import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserStatus;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
  LoginRateLimitProperties.class,
  com.company.fashionpos.mpesa.MpesaProperties.class
})
public class SecurityConfig {

  private static final String ADMIN_MANAGE = "admin:manage";
  private static final String ACCOUNTING_MANAGE = "accounting:manage";
  private static final String ACCOUNTING_VIEW = "accounting:view";
  private static final String INVENTORY_ADJUST = "inventory:adjust";
  private static final String INVENTORY_RECEIVE = "inventory:receive";
  private static final String AUDIT_VIEW = "audit:view";
  private static final String COMMERCIAL_MANAGE = "commercial:manage";
  private static final String CRM_MANAGE = "crm:manage";
  private static final String NOTIFICATIONS_VIEW = "notifications:view";
  private static final String POS_SELL = "pos:sell";
  private static final String PROCUREMENT_MANAGE = "procurement:manage";
  private static final String REPORTS_VIEW = "reports:view";
  private static final String SALES_VIEW = "sales:view";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityProblemResponseWriter problemResponseWriter,
      CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler =
        new CsrfTokenRequestAttributeHandler();

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers(
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                            "/api/v1/mpesa/callback/**", "POST"))
                    .csrfTokenRequestHandler(csrfTokenRequestHandler))
        .cors(Customizer.withDefaults())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(problemResponseWriter::writeUnauthorized)
                    .accessDeniedHandler(problemResponseWriter::writeForbidden))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/mpesa/callback/**")
                    .permitAll()
                    .requestMatchers("/api/v1/mpesa/**")
                    .hasAnyAuthority(ADMIN_MANAGE, POS_SELL)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/me",
                        "/api/v1/organization/current",
                        "/api/v1/branches",
                        "/api/v1/catalog/categories",
                        "/api/v1/catalog/products",
                        "/api/v1/inventory")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/organization/current/vat-settings")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/pos-preferences")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.GET, "/api/v1/daily-sales-reports/**")
                    .hasAnyAuthority(ADMIN_MANAGE, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.GET, "/api/v1/sale-reversals")
                    .hasAnyAuthority(ADMIN_MANAGE, REPORTS_VIEW, SALES_VIEW)
                    .requestMatchers("/api/v1/daily-sales-reports/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.POST, "/api/v1/sales/*/reversal")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers("/api/v1/stock-operations/**")
                    .hasAnyAuthority(ADMIN_MANAGE, INVENTORY_RECEIVE)
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/roles", "/api/v1/permissions", "/api/v1/users")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.GET, "/api/v1/procurement/**")
                    .hasAnyAuthority(ADMIN_MANAGE, PROCUREMENT_MANAGE, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.GET, "/api/v1/customers/**")
                    .hasAnyAuthority(ADMIN_MANAGE, COMMERCIAL_MANAGE, CRM_MANAGE, SALES_VIEW)
                    .requestMatchers("/api/v1/customers/**")
                    .hasAnyAuthority(ADMIN_MANAGE, COMMERCIAL_MANAGE, CRM_MANAGE)
                    .requestMatchers(HttpMethod.POST, "/api/v1/document-delivery/email")
                    .hasAnyAuthority(ADMIN_MANAGE, COMMERCIAL_MANAGE, PROCUREMENT_MANAGE)
                    .requestMatchers(HttpMethod.GET, "/api/v1/crm/**")
                    .hasAnyAuthority(ADMIN_MANAGE, CRM_MANAGE, SALES_VIEW, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.GET, "/api/v1/commercial/**")
                    .hasAnyAuthority(ADMIN_MANAGE, COMMERCIAL_MANAGE, SALES_VIEW, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.GET, "/api/v1/accounting/**")
                    .hasAnyAuthority(ADMIN_MANAGE, ACCOUNTING_VIEW, ACCOUNTING_MANAGE, REPORTS_VIEW)
                    .requestMatchers("/api/v1/accounting/**")
                    .hasAnyAuthority(ADMIN_MANAGE, ACCOUNTING_MANAGE)
                    .requestMatchers(HttpMethod.GET, "/api/v1/notifications/**")
                    .hasAnyAuthority(ADMIN_MANAGE, NOTIFICATIONS_VIEW)
                    .requestMatchers(HttpMethod.GET, "/api/v1/audit-events")
                    .hasAnyAuthority(ADMIN_MANAGE, AUDIT_VIEW)
                    .requestMatchers(
                        "/api/v1/roles",
                        "/api/v1/roles/**",
                        "/api/v1/permissions",
                        "/api/v1/permissions/**",
                        "/api/v1/users",
                        "/api/v1/users/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.POST, "/api/v1/branches")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/branches/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/branches/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.POST, "/api/v1/catalog/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.PUT, "/api/v1/catalog/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/catalog/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers("/api/v1/suppliers/**")
                    .hasAuthority(ADMIN_MANAGE)
                    .requestMatchers(HttpMethod.GET, "/api/v1/stock-intakes")
                    .hasAnyAuthority(ADMIN_MANAGE, INVENTORY_RECEIVE, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.POST, "/api/v1/stock-intakes")
                    .hasAnyAuthority(ADMIN_MANAGE, INVENTORY_RECEIVE)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/stock-adjustments",
                        "/api/v1/stock-adjustments/summary")
                    .hasAnyAuthority(ADMIN_MANAGE, INVENTORY_ADJUST, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.POST, "/api/v1/stock-adjustments")
                    .hasAnyAuthority(ADMIN_MANAGE, INVENTORY_ADJUST)
                    .requestMatchers(HttpMethod.GET, "/api/v1/sales/**")
                    .hasAnyAuthority(ADMIN_MANAGE, SALES_VIEW, REPORTS_VIEW)
                    .requestMatchers(HttpMethod.POST, "/api/v1/sales")
                    .hasAnyAuthority(ADMIN_MANAGE, POS_SELL)
                    .requestMatchers("/api/v1/shifts", "/api/v1/shifts/**")
                    .hasAnyAuthority(ADMIN_MANAGE, "shifts:manage")
                    .requestMatchers(HttpMethod.GET, "/api/v1/expenses")
                    .hasAnyAuthority(ADMIN_MANAGE, REPORTS_VIEW, SALES_VIEW, "expenses:create")
                    .requestMatchers(HttpMethod.POST, "/api/v1/expenses")
                    .hasAnyAuthority(ADMIN_MANAGE, REPORTS_VIEW, SALES_VIEW, "expenses:create")
                    .requestMatchers("/api/v1/expenses/**")
                    .hasAnyAuthority(ADMIN_MANAGE, REPORTS_VIEW, SALES_VIEW)
                    .anyRequest()
                    .authenticated())
        .httpBasic(AbstractHttpConfigurer::disable)
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  UserDetailsService userDetailsService(
      UserAccountRepository userAccountRepository, JdbcTemplate jdbcTemplate) {
    return username -> {
      String normalizedUsername = normalizedEmail(username);
      UserAccount account =
          userAccountRepository
              .findByEmailIgnoreCaseAndStatus(normalizedUsername, UserStatus.ACTIVE)
              .filter(user -> hasText(user.getPasswordHash()))
              .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

      Set<String> authorities = new LinkedHashSet<>();
      authorities.add("ROLE_USER");
      authorities.addAll(roleAuthoritiesForUser(jdbcTemplate, account.getId()));
      authorities.addAll(permissionCodesForUser(jdbcTemplate, account.getId()));

      return User.withUsername(account.getEmail())
          .password(account.getPasswordHash())
          .authorities(authorities.stream().map(SimpleGrantedAuthority::new).toList())
          .build();
    };
  }

  private static List<String> permissionCodesForUser(
      JdbcTemplate jdbcTemplate, java.util.UUID userId) {
    return jdbcTemplate.queryForList(
        """
        select distinct permission.code
        from user_roles user_role
        join role_permissions role_permission on role_permission.role_id = user_role.role_id
        join permissions permission on permission.id = role_permission.permission_id
        where user_role.user_id = ?
        order by permission.code
        """,
        String.class,
        userId);
  }

  private static List<String> roleAuthoritiesForUser(
      JdbcTemplate jdbcTemplate, java.util.UUID userId) {
    return jdbcTemplate
        .queryForList(
            """
            select distinct role.role_key
            from user_roles assignment
            join roles role on role.id = assignment.role_id
            where assignment.user_id = ?
            order by role.role_key
            """,
            String.class,
            userId)
        .stream()
        .map(SecurityConfig::roleAuthority)
        .toList();
  }

  private static String roleAuthority(String roleKey) {
    return "ROLE_" + roleKey;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository(
      @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookiePath("/");
    csrfTokenRepository.setCookieCustomizer(
        cookie ->
            cookie
                .secure(secureCookies)
                .sameSite(CookieSettings.normalizeSameSite(sameSite))
                .path("/"));
    return csrfTokenRepository;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value(
              "${app.security.cors.allowed-origins:${app.security.cors.allowed-origin:http://localhost:5173}}")
          String allowedOrigins) {
    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(SecurityConfig::hasText)
            .toList();
    if (origins.isEmpty()) {
      throw new IllegalStateException("At least one allowed CORS origin must be configured");
    }
    if (origins.contains("*")) {
      throw new IllegalStateException("Wildcard CORS origins are not allowed with credentials");
    }

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Correlation-Id",
            "Idempotency-Key",
            "X-CSRF-TOKEN",
            "X-XSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private static String normalizedEmail(String email) {
    return email.toLowerCase(Locale.ROOT);
  }
}
