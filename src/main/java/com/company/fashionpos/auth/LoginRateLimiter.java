package com.company.fashionpos.auth;

import com.company.fashionpos.shared.security.LoginRateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginRateLimiter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoginRateLimiter.class);

  private final LoginRateLimitProperties properties;
  private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

  public LoginRateLimiter(LoginRateLimitProperties properties) {
    this.properties = properties;
  }

  public void assertAllowed(String accountIdentifier, String sourceIp) {
    if (!properties.isEnabled()) {
      return;
    }

    Instant now = Instant.now();
    Duration accountRetryAfter = retryAfter(accountKey(accountIdentifier), now);
    Duration ipRetryAfter = retryAfter(ipKey(sourceIp), now);
    Duration retryAfter = longer(accountRetryAfter, ipRetryAfter);
    if (!retryAfter.isZero() && !retryAfter.isNegative()) {
      throw new LoginRateLimitExceededException(retryAfter);
    }
  }

  public void recordFailure(String accountIdentifier, String sourceIp) {
    if (!properties.isEnabled()) {
      return;
    }

    Instant now = Instant.now();
    recordFailure(accountKey(accountIdentifier), now);
    recordFailure(ipKey(sourceIp), now);
  }

  public void recordSuccess(String accountIdentifier, String sourceIp) {
    if (!properties.isEnabled()) {
      return;
    }

    attempts.remove(accountKey(accountIdentifier));
    attempts.remove(ipKey(sourceIp));
  }

  private void recordFailure(String key, Instant now) {
    attempts.compute(
        key,
        (ignored, existing) -> {
          AttemptWindow current = activeWindow(existing, now);
          int failedAttempts = current.failedAttempts() + 1;
          Instant lockedUntil = current.lockedUntil();
          if (failedAttempts >= properties.getMaxFailedAttempts()) {
            lockedUntil = now.plus(properties.getLockout());
            LOGGER.warn("Login rate limit locked {}", current.logSafeKey());
          }
          return new AttemptWindow(
              current.windowStartedAt(), failedAttempts, lockedUntil, current.logSafeKey());
        });
  }

  private Duration retryAfter(String key, Instant now) {
    AttemptWindow attemptWindow = attempts.get(key);
    if (attemptWindow == null || attemptWindow.lockedUntil() == null) {
      return Duration.ZERO;
    }
    if (attemptWindow.lockedUntil().isAfter(now)) {
      return Duration.between(now, attemptWindow.lockedUntil());
    }
    attempts.remove(key, attemptWindow);
    return Duration.ZERO;
  }

  private AttemptWindow activeWindow(AttemptWindow existing, Instant now) {
    if (existing == null) {
      return new AttemptWindow(now, 0, null, "credential bucket");
    }
    if (existing.lockedUntil() != null && existing.lockedUntil().isAfter(now)) {
      return existing;
    }
    if (existing.windowStartedAt().plus(properties.getWindow()).isBefore(now)
        || existing.windowStartedAt().plus(properties.getWindow()).equals(now)) {
      return new AttemptWindow(now, 0, null, "credential bucket");
    }
    return existing;
  }

  private static String accountKey(String accountIdentifier) {
    return "account:" + normalize(accountIdentifier);
  }

  private static String ipKey(String sourceIp) {
    return "ip:" + normalize(sourceIp);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static Duration longer(Duration first, Duration second) {
    return first.compareTo(second) >= 0 ? first : second;
  }

  private record AttemptWindow(
      Instant windowStartedAt, int failedAttempts, Instant lockedUntil, String logSafeKey) {}
}
