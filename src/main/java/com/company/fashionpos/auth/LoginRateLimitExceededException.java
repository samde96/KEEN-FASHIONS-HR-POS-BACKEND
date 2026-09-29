package com.company.fashionpos.auth;

import java.time.Duration;

public class LoginRateLimitExceededException extends RuntimeException {

  private final Duration retryAfter;

  public LoginRateLimitExceededException(Duration retryAfter) {
    super("Too many login attempts");
    this.retryAfter = retryAfter;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }
}
