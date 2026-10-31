package com.company.fashionpos.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MpesaPropertiesTest {
  private static final String SECRET = "private-consumer-secret-must-not-be-logged";
  private static final String PASSKEY = "private-passkey-must-not-be-logged";
  private static final String ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  private MpesaProperties settings(
      UUID organizationId,
      String partyB,
      String callback,
      String encryptionKey,
      List<String> allowedIps) {
    return new MpesaProperties(
        true,
        "sandbox",
        organizationId,
        "consumer",
        SECRET,
        PASSKEY,
        "174379",
        partyB,
        "CustomerPayBillOnline",
        callback,
        "callback-secret-at-least-32-characters",
        encryptionKey,
        allowedIps,
        List.of(),
        Duration.ofSeconds(3),
        Duration.ofSeconds(8));
  }

  @Test
  void reportsAllMissingSettingsWithoutIncludingCredentialValues() {
    assertThatThrownBy(
            () ->
                settings(
                    null,
                    "",
                    "https://payments.example.test/api/mpesa/callback",
                    ENCRYPTION_KEY,
                    List.of("196.201.214.200")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MPESA_ORGANIZATION_ID")
        .hasMessageContaining("MPESA_PARTY_B")
        .hasMessageContaining("MPESA_CALLBACK_BASE_URL")
        .hasMessageNotContaining(SECRET)
        .hasMessageNotContaining(PASSKEY)
        .hasMessageNotContaining(ENCRYPTION_KEY)
        .hasMessageNotContaining("/api/mpesa/callback");
  }

  @Test
  void acceptsACompleteSandboxConfiguration() {
    var settings =
        settings(
            UUID.randomUUID(),
            "174379",
            "https://payments.example.test",
            ENCRYPTION_KEY,
            List.of("196.201.214.200"));
    assertThat(settings.enabled()).isTrue();
  }

  @Test
  void invalidEncryptionKeyIsNamedWithoutDisclosingIt() {
    assertThatThrownBy(
            () ->
                settings(
                    UUID.randomUUID(),
                    "174379",
                    "https://payments.example.test",
                    "private-invalid-encryption-key",
                    List.of("196.201.214.200")))
        .hasMessageContaining("MPESA_ENCRYPTION_KEY")
        .hasMessageNotContaining("private-invalid-encryption-key")
        .hasMessageNotContaining(SECRET)
        .hasMessageNotContaining(PASSKEY);
  }

  @Test
  void malformedCallbackUrlsAndNetworksProduceSafeConfigurationErrors() {
    assertThatThrownBy(
            () ->
                settings(
                    UUID.randomUUID(),
                    "174379",
                    "https://private host/secret-path",
                    ENCRYPTION_KEY,
                    List.of("not-an-ip")))
        .hasMessageContaining("MPESA_CALLBACK_BASE_URL")
        .hasMessageContaining("MPESA_CALLBACK_ALLOWED_IPS")
        .hasMessageNotContaining("private host")
        .hasMessageNotContaining("secret-path")
        .hasMessageNotContaining("not-an-ip");
  }

  @Test
  void disabledPaymentsDoNotRequireDarajaConfiguration() {
    var settings =
        new MpesaProperties(
            false, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null);
    assertThat(settings.enabled()).isFalse();
  }
}
