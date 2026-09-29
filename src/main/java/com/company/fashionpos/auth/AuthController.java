package com.company.fashionpos.auth;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import com.company.fashionpos.shared.security.CookieSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String SESSION_COOKIE_NAME = "JSESSIONID";
  private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final int REMEMBER_DEVICE_SECONDS = 14 * 24 * 60 * 60;

  private final AuthenticationManager authenticationManager;
  private final AuthenticatedUserService authenticatedUserService;
  private final LoginRateLimiter loginRateLimiter;
  private final boolean secureCookies;
  private final String sameSite;
  private final CsrfTokenRepository csrfTokenRepository;
  private final HttpSessionSecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(
      AuthenticationManager authenticationManager,
      AuthenticatedUserService authenticatedUserService,
      LoginRateLimiter loginRateLimiter,
      CsrfTokenRepository csrfTokenRepository,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
    this.authenticationManager = authenticationManager;
    this.authenticatedUserService = authenticatedUserService;
    this.loginRateLimiter = loginRateLimiter;
    this.csrfTokenRepository = csrfTokenRepository;
    this.secureCookies = secureCookies;
    this.sameSite = CookieSettings.normalizeSameSite(sameSite);
  }

  @GetMapping("/csrf")
  public ResponseEntity<CsrfTokenResponse> csrf(
      HttpServletRequest request, HttpServletResponse response) {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.loadToken(request);
      if (csrfToken == null) {
        csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
      }
    }

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(CsrfTokenResponse.from(csrfToken));
  }

  @PostMapping("/login")
  public MeResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String username = normalizeIdentifier(request.username());
    String sourceIp = sourceIp(httpRequest);
    loginRateLimiter.assertAllowed(username, sourceIp);

    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
    } catch (AuthenticationException exception) {
      loginRateLimiter.recordFailure(username, sourceIp);
      throw exception;
    }

    loginRateLimiter.recordSuccess(username, sourceIp);

    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    SecurityContextHolder.setContext(securityContext);

    if (httpRequest.getSession(false) != null) {
      httpRequest.changeSessionId();
    } else {
      httpRequest.getSession(true);
    }

    HttpSession session = httpRequest.getSession(false);
    if (session != null && request.rememberDevice()) {
      session.setMaxInactiveInterval(REMEMBER_DEVICE_SECONDS);
    }

    securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

    // Ensure a fresh CSRF token is issued for the authenticated session so clients can
    // continue to make unsafe requests without requiring an extra GET /csrf call.
    try {
      jakarta.servlet.http.HttpServletRequest req = httpRequest;
      jakarta.servlet.http.HttpServletResponse res = httpResponse;
      var csrfToken = csrfTokenRepository.generateToken(req);
      csrfTokenRepository.saveToken(csrfToken, req, res);
    } catch (Exception ignored) {
      // If CSRF token generation fails for any reason, fall back to clearing the cookie
      // to avoid exposing inconsistent state. The client can still call /api/v1/auth/csrf.
      clearCookie(httpResponse, CSRF_COOKIE_NAME);
    }

    return MeResponse.from(authenticatedUserService.current(authentication));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    SecurityContextHolder.clearContext();

    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    clearCookie(response, SESSION_COOKIE_NAME);
    clearCookie(response, CSRF_COOKIE_NAME);
  }

  static String normalizeIdentifier(String identifier) {
    return identifier.trim().toLowerCase(Locale.ROOT);
  }

  private static String sourceIp(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    if (remoteAddress == null || remoteAddress.isBlank()) {
      return "unknown";
    }
    return remoteAddress;
  }

  private void clearCookie(HttpServletResponse response, String name) {
    ResponseCookie cookie =
        ResponseCookie.from(name, "")
            .path("/")
            .httpOnly(SESSION_COOKIE_NAME.equals(name))
            .secure(secureCookies)
            .sameSite(sameSite)
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
