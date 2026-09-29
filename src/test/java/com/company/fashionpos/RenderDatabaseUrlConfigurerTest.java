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

  @Test
  void prefersRenderDatabaseUrlWhenFashionPosUrlPointsToLocalhost() {
    String selected =
        RenderDatabaseUrlConfigurer.selectDatabaseUrl(
            "jdbc:postgresql://localhost:5432/fashion_pos",
            "postgresql://render_user:secret@dpg-example-a:5432/fashion_pos");

    assertThat(selected)
        .isEqualTo("postgresql://render_user:secret@dpg-example-a:5432/fashion_pos");
  }

  @Test
  void replacesSpringDatasourceUrlWhenItPointsToLocalhost() {
    assertThat(
            RenderDatabaseUrlConfigurer.shouldReplaceLocalDatasourceUrl(
                "jdbc:postgresql://localhost:5432/fashion_pos",
                "postgresql://render_user:secret@dpg-example-a:5432/fashion_pos"))
        .isTrue();
  }

  @Test
  void preservesSpringDatasourceUrlWhenItPointsToExternalDatabase() {
    assertThat(
            RenderDatabaseUrlConfigurer.shouldReplaceLocalDatasourceUrl(
                "jdbc:postgresql://database.example.com:5432/fashion_pos",
                "postgresql://render_user:secret@dpg-example-a:5432/fashion_pos"))
        .isFalse();
  }
}
