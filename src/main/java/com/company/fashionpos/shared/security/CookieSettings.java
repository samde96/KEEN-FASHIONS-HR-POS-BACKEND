package com.company.fashionpos.shared.security;

import java.util.Locale;

public final class CookieSettings {

  private CookieSettings() {}

  public static String normalizeSameSite(String sameSite) {
    if (sameSite == null || sameSite.isBlank()) {
      return "Lax";
    }

    return switch (sameSite.trim().toLowerCase(Locale.ROOT)) {
      case "none" -> "None";
      case "strict" -> "Strict";
      case "lax" -> "Lax";
      default ->
          throw new IllegalArgumentException("Unsupported SameSite cookie value: " + sameSite);
    };
  }
}
