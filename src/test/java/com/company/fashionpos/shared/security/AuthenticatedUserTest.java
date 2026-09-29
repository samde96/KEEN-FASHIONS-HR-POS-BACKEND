package com.company.fashionpos.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");
  private static final UUID ORGANIZATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");

  @Test
  void adminRoleCanViewProfit() {
    assertThat(userWith(Set.of("ADMIN"), Set.of()).canViewProfit()).isTrue();
  }

  @Test
  void nonAdminCannotViewProfitEvenWithProfitPermission() {
    assertThat(
            userWith(Set.of("SUPERVISOR"), Set.of("admin:manage", "profit:view")).canViewProfit())
        .isFalse();
  }

  private static AuthenticatedUser userWith(Set<String> roleKeys, Set<String> permissionCodes) {
    return new AuthenticatedUser(
        USER_ID,
        "user@test.local",
        "Test User",
        ORGANIZATION_ID,
        Set.of(),
        roleKeys,
        roleKeys,
        permissionCodes);
  }
}
