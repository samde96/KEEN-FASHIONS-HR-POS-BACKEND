package com.company.fashionpos.delivery;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DocumentDeliveryServiceTest {
  @Test
  void configurationReportsMissingSettingsWithoutRevealingCredentials() {
    var missing = new DocumentDeliveryService(new ObjectMapper(), "", "").configuration();
    assertThat(missing.configured()).isFalse();
    assertThat(missing.missingSettings()).containsExactly("RESEND_API_KEY", "RESEND_FROM_EMAIL");
    var configured =
        new DocumentDeliveryService(
                new ObjectMapper(), "re_private_test_key", " KEEN Fashions <alerts@example.test> ")
            .configuration();
    assertThat(configured.configured()).isTrue();
    assertThat(configured.fromEmail()).isEqualTo("KEEN Fashions <alerts@example.test>");
    assertThat(configured.toString()).doesNotContain("re_private_test_key");
  }

  @Test
  void doesNotAttemptDeliveryWithoutTheApiKey() {
    var delivery = new DocumentDeliveryService(new ObjectMapper(), "", "alerts@example.test");
    assertThatThrownBy(
            () ->
                delivery.sendSummary(
                    "owner@example.test", "Alert", "Same inbox message", "test/notification"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RESEND_API_KEY");
  }
}
