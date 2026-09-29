package com.company.fashionpos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RenderDatabaseUrlConfigurerTest {

  @Test
  void convertsRenderPostgresUrlToJdbcSettings() {
    var connection =
        RenderDatabaseUrlConfigurer.fromPostgresUrl(
            "postgresql://fashion_user:p%40ssword@dpg-example-a:5432/fashion_pos?sslmode=require");

    assertThat(connection.jdbcUrl())
        .isEqualTo("jdbc:postgresql://dpg-example-a:5432/fashion_pos?sslmode=require");
    assertThat(connection.username()).isEqualTo("fashion_user");
    assertThat(connection.password()).isEqualTo("p@ssword");
  }
}
