package com.company.fashionpos;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class RenderDatabaseUrlConfigurer {

  private static final String SPRING_DATASOURCE_URL = "spring.datasource.url";
  private static final String SPRING_DATASOURCE_USERNAME = "spring.datasource.username";
  private static final String SPRING_DATASOURCE_PASSWORD = "spring.datasource.password";

  private RenderDatabaseUrlConfigurer() {}

  static void apply() {
    if (hasText(System.getProperty(SPRING_DATASOURCE_URL))
        || hasText(System.getenv("SPRING_DATASOURCE_URL"))) {
      return;
    }

    String databaseUrl =
        firstNonBlank(System.getenv("FASHIONPOS_DB_URL"), System.getenv("DATABASE_URL"));
    if (!hasText(databaseUrl)) {
      return;
    }

    if (databaseUrl.startsWith("jdbc:postgresql://")) {
      System.setProperty(SPRING_DATASOURCE_URL, databaseUrl);
      return;
    }

    if (!isPostgresUrl(databaseUrl)) {
      return;
    }

    DatabaseConnection connection = fromPostgresUrl(databaseUrl);
    System.setProperty(SPRING_DATASOURCE_URL, connection.jdbcUrl());
    setIfMissing(SPRING_DATASOURCE_USERNAME, "SPRING_DATASOURCE_USERNAME", connection.username());
    setIfMissing(SPRING_DATASOURCE_PASSWORD, "SPRING_DATASOURCE_PASSWORD", connection.password());
  }

  static DatabaseConnection fromPostgresUrl(String databaseUrl) {
    URI uri = URI.create(databaseUrl);
    String host = uri.getHost();
    if (!hasText(host)) {
      throw new IllegalArgumentException("DATABASE_URL must include a PostgreSQL host");
    }

    StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
    jdbcUrl.append(formatHost(host));
    if (uri.getPort() > 0) {
      jdbcUrl.append(':').append(uri.getPort());
    }
    jdbcUrl.append(hasText(uri.getRawPath()) ? uri.getRawPath() : "/");
    if (hasText(uri.getRawQuery())) {
      jdbcUrl.append('?').append(uri.getRawQuery());
    }

    Credentials credentials = credentialsFrom(uri.getRawUserInfo());
    return new DatabaseConnection(
        jdbcUrl.toString(), credentials.username(), credentials.password());
  }

  private static Credentials credentialsFrom(String rawUserInfo) {
    if (!hasText(rawUserInfo)) {
      return new Credentials(null, null);
    }

    String[] parts = rawUserInfo.split(":", 2);
    String username = urlDecode(parts[0]);
    String password = parts.length > 1 ? urlDecode(parts[1]) : null;
    return new Credentials(username, password);
  }

  private static void setIfMissing(String propertyName, String envName, String value) {
    if (!hasText(value)
        || hasText(System.getProperty(propertyName))
        || hasText(System.getenv(envName))) {
      return;
    }
    System.setProperty(propertyName, value);
  }

  private static String formatHost(String host) {
    if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
      return "[" + host + "]";
    }
    return host;
  }

  private static String urlDecode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static boolean isPostgresUrl(String value) {
    return value.startsWith("postgresql://") || value.startsWith("postgres://");
  }

  private static String firstNonBlank(String first, String second) {
    return hasText(first) ? first : second;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  record DatabaseConnection(String jdbcUrl, String username, String password) {}

  private record Credentials(String username, String password) {}
}
