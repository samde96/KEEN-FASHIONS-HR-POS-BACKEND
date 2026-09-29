package com.company.fashionpos.shared.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit.login")
public class LoginRateLimitProperties {

  private boolean enabled = true;

  @Min(1)
  private int maxFailedAttempts = 5;

  @NotNull private Duration window = Duration.ofMinutes(15);

  @NotNull private Duration lockout = Duration.ofMinutes(15);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxFailedAttempts() {
    return maxFailedAttempts;
  }

  public void setMaxFailedAttempts(int maxFailedAttempts) {
    this.maxFailedAttempts = maxFailedAttempts;
  }

  public Duration getWindow() {
    return window;
  }

  public void setWindow(Duration window) {
    this.window = window;
  }

  public Duration getLockout() {
    return lockout;
  }

  public void setLockout(Duration lockout) {
    this.lockout = lockout;
  }
}
