package com.company.fashionpos.mpesa;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mpesa")
public record MpesaProperties(
    boolean enabled,
    String environment,
    UUID organizationId,
    String consumerKey,
    String consumerSecret,
    String passkey,
    String shortcode,
    String partyB,
    String transactionType,
    String callbackBaseUrl,
    String callbackSecret,
    String encryptionKey,
    List<String> callbackAllowedIps,
    List<String> trustedProxies,
    Duration connectTimeout,
    Duration requestTimeout) {
  public MpesaProperties {
    environment = environment == null ? "sandbox" : environment;
    transactionType = transactionType == null ? "CustomerPayBillOnline" : transactionType;
    callbackAllowedIps = callbackAllowedIps == null ? List.of() : List.copyOf(callbackAllowedIps);
    trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
    requestTimeout = requestTimeout == null ? Duration.ofSeconds(8) : requestTimeout;
    if (enabled) {
      List<String> invalid = new ArrayList<>();
      if (!List.of("sandbox", "live").contains(environment))
        invalid.add("MPESA_ENVIRONMENT (sandbox or live)");
      if (organizationId == null) invalid.add("MPESA_ORGANIZATION_ID (business UUID)");
      if (blank(consumerKey)) invalid.add("MPESA_CONSUMER_KEY");
      if (blank(consumerSecret)) invalid.add("MPESA_CONSUMER_SECRET");
      if (blank(passkey)) invalid.add("MPESA_PASSKEY");
      if (shortcode == null || !shortcode.matches("\\d{5,7}"))
        invalid.add("MPESA_SHORTCODE (5 to 7 digits)");
      if (partyB == null || !partyB.matches("\\d{5,7}"))
        invalid.add("MPESA_PARTY_B (Paybill or till number, 5 to 7 digits)");
      if (!List.of("CustomerPayBillOnline", "CustomerBuyGoodsOnline").contains(transactionType))
        invalid.add("MPESA_TRANSACTION_TYPE (CustomerPayBillOnline or CustomerBuyGoodsOnline)");
      if (blank(callbackSecret) || callbackSecret.length() < 32)
        invalid.add("MPESA_CALLBACK_SECRET_KEY (at least 32 characters)");
      if (callbackAllowedIps.isEmpty() || !validNetworks(callbackAllowedIps))
        invalid.add("MPESA_CALLBACK_ALLOWED_IPS (Safaricom IP addresses or CIDRs)");
      if (!validNetworks(trustedProxies))
        invalid.add("MPESA_TRUSTED_PROXIES (ingress proxy IP addresses or CIDRs)");
      if (connectTimeout.isNegative() || connectTimeout.isZero())
        invalid.add("app.mpesa.connect-timeout (must be positive)");
      if (requestTimeout.isNegative()
          || requestTimeout.isZero()
          || requestTimeout.compareTo(Duration.ofSeconds(15)) > 0)
        invalid.add("app.mpesa.request-timeout (greater than zero and at most 15 seconds)");
      if (!validCallbackOrigin(callbackBaseUrl))
        invalid.add(
            "MPESA_CALLBACK_BASE_URL (HTTPS origin only, without a path, query or fragment)");
      try {
        if (Base64.getDecoder().decode(encryptionKey).length != 32)
          throw new IllegalArgumentException();
      } catch (RuntimeException invalidKey) {
        invalid.add("MPESA_ENCRYPTION_KEY (base64-encoded 32-byte key)");
      }
      if (!invalid.isEmpty())
        throw new IllegalArgumentException(
            "Invalid M-Pesa settings: " + String.join(", ", invalid));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean validCallbackOrigin(String value) {
    try {
      URI callback = URI.create(value == null ? "" : value);
      return "https".equals(callback.getScheme())
          && callback.getHost() != null
          && callback.getUserInfo() == null
          && callback.getQuery() == null
          && callback.getFragment() == null
          && "".equals(callback.getPath());
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  private static boolean validNetworks(List<String> networks) {
    try {
      networks.forEach(MpesaCallbackFirewall::validateNetwork);
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  public String baseUrl() {
    return "live".equals(environment)
        ? "https://api.safaricom.co.ke"
        : "https://sandbox.safaricom.co.ke";
  }

  public String accountFingerprint() {
    return MpesaCrypto.sha256(environment + ":" + organizationId + ":" + shortcode + ":" + partyB);
  }
}
