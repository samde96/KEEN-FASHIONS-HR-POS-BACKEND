package com.company.fashionpos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.text.IsEmptyString.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.fashionpos.auth.AuthController;
import com.company.fashionpos.auth.LoginRateLimiter;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityProblemResponseWriter.class, LoginRateLimiter.class})
@TestPropertySource(
    properties = {
      "app.security.admin-email=admin@test.local",
      "app.security.admin-password=test-admin-password",
      "app.security.manager-email=",
      "app.security.manager-password=",
      "app.security.cors.allowed-origins=http://localhost:5173",
      "app.security.rate-limit.login.max-failed-attempts=2",
      "app.security.rate-limit.login.window=15m",
      "app.security.rate-limit.login.lockout=15m",
      "server.servlet.session.cookie.secure=false"
    })
class AuthenticationSecurityTest {

  private static final String ADMIN_EMAIL = "admin@test.local";
  private static final String ADMIN_PASSWORD = "test-admin-password";
  private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");
  private static final String MANAGER_EMAIL = "manager@test.local";
  private static final String MANAGER_PASSWORD = "test-manager-password";
  private static final UUID MANAGER_USER_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000302");
  private static final UUID ORGANIZATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private AuthenticatedUserService authenticatedUserService;

  @MockitoBean private UserAccountRepository userAccountRepository;

  @MockitoBean private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    UserAccount adminAccount =
        new UserAccount(
            ADMIN_USER_ID,
            new Organization(ORGANIZATION_ID, "KEEN Fashions", "KES", "Africa/Nairobi"),
            ADMIN_EMAIL,
            "System Admin");
    adminAccount.updatePasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
    when(userAccountRepository.findByEmailIgnoreCaseAndStatus(ADMIN_EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(adminAccount));
    when(jdbcTemplate.queryForList(any(String.class), any(Class.class), any()))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0, String.class);
              Object userId = invocation.getArgument(2);
              if (sql.contains("role.role_key")) {
                return MANAGER_USER_ID.equals(userId)
                    ? java.util.List.of("SHOP_MANAGER")
                    : java.util.List.of("ADMIN");
              }
              return MANAGER_USER_ID.equals(userId)
                  ? java.util.List.of("hr:dashboard:view", "hr:employee:view", "reports:view")
                  : java.util.List.of("admin:manage", "profit:view");
            });

    when(authenticatedUserService.current(any(Authentication.class)))
        .thenReturn(
            new AuthenticatedUser(
                ADMIN_USER_ID,
                ADMIN_EMAIL,
                "System Admin",
                ORGANIZATION_ID,
                Set.of(),
                Set.of("ADMIN"),
                Set.of("Admin"),
                Set.of()));
  }

  @Test
  void csrfEndpointIssuesTokenAndReadableCookie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.token", not(emptyString())))
            .andReturn();

    assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
  }

  @Test
  void loginRequiresValidCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, "wrong-password")))
        .andExpect(status().isForbidden())
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(withCsrf(csrfExchange(), "invalid-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, "wrong-password")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(withCsrf(csrfExchange()))
                .with(remoteAddress("203.0.113.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ADMIN_EMAIL, "wrong-password")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password"));
  }

  @Test
  void repeatedFailedLoginsReturnTooManyRequestsWithRetryAfter() throws Exception {
    String body = loginBody("missing-user@test.local", "wrong-password");
    CsrfExchange csrf = csrfExchange();

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(withCsrf(csrf))
                .with(remoteAddress("203.0.113.20"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(withCsrf(csrf))
                .with(remoteAddress("203.0.113.20"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(withCsrf(csrf))
                .with(remoteAddress("203.0.113.20"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, not(emptyString())))
        .andExpect(jsonPath("$.detail").value("Too many login attempts. Try again later."));
  }

  @Test
  void httpBasicDoesNotBypassSessionLoginControls() throws Exception {
    String basicCredentials =
        java.util.Base64.getEncoder()
            .encodeToString((ADMIN_EMAIL + ":" + ADMIN_PASSWORD).getBytes());

    mockMvc
        .perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Authentication is required"));
  }

  @Test
  void hrEndpointsRequireAdminRoleEvenWhenUserHasHrPermissions() throws Exception {
    UserAccount managerAccount =
        new UserAccount(
            MANAGER_USER_ID,
            new Organization(ORGANIZATION_ID, "KEEN Fashions", "KES", "Africa/Nairobi"),
            MANAGER_EMAIL,
            "Shop Manager");
    managerAccount.updatePasswordHash(passwordEncoder.encode(MANAGER_PASSWORD));
    when(userAccountRepository.findByEmailIgnoreCaseAndStatus(MANAGER_EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(managerAccount));

    MockHttpSession anonymousSession = new MockHttpSession();
    CsrfExchange csrf = csrfExchange();
    HttpSession authenticatedSession =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .session(anonymousSession)
                    .with(withCsrf(csrf))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(MANAGER_EMAIL, MANAGER_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);

    mockMvc
        .perform(get("/api/v1/hr/departments").session((MockHttpSession) authenticatedSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void corsAllowsOnlyConfiguredOriginsWithCredentialsAndCsrfHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("x-xsrf-token")));

    mockMvc
        .perform(
            options("/api/v1/me")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void loginNormalizesEmailAndRotatesExistingSessionId() throws Exception {
    MockHttpSession anonymousSession = new MockHttpSession();
    String anonymousSessionId = anonymousSession.getId();
    CsrfExchange csrf = csrfExchange();

    HttpSession authenticatedSession =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .session(anonymousSession)
                    .with(withCsrf(csrf))
                    .with(remoteAddress("203.0.113.30"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody("ADMIN@TEST.LOCAL", ADMIN_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
            .andReturn()
            .getRequest()
            .getSession(false);

    assertThat(authenticatedSession).isNotNull();
    assertThat(authenticatedSession.getId()).isNotEqualTo(anonymousSessionId);
  }

  private static String loginBody(String username, String password) {
    return """
        {
          "username": "%s",
          "password": "%s",
          "rememberDevice": false
        }
        """
        .formatted(username, password);
  }

  private static RequestPostProcessor remoteAddress(String sourceIp) {
    return request -> {
      request.setRemoteAddr(sourceIp);
      return request;
    };
  }

  private CsrfExchange csrfExchange() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    var body = objectMapper.readTree(result.getResponse().getContentAsString());
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return new CsrfExchange(body.get("headerName").asText(), body.get("token").asText(), cookie);
  }

  private static RequestPostProcessor withCsrf(CsrfExchange csrf) {
    return withCsrf(csrf, csrf.token());
  }

  private static RequestPostProcessor withCsrf(CsrfExchange csrf, String token) {
    return request -> {
      request.setCookies(csrf.cookie());
      request.addHeader(csrf.headerName(), token);
      return request;
    };
  }

  private record CsrfExchange(String headerName, String token, Cookie cookie) {}
}
