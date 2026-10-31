package com.company.fashionpos.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DarajaClient {
  private final MpesaProperties properties;
  private final MpesaCrypto crypto;
  private final ObjectMapper json;
  private final HttpClient http;
  private volatile String token;
  private volatile Instant tokenExpires = Instant.EPOCH;

  @org.springframework.beans.factory.annotation.Autowired
  public DarajaClient(MpesaProperties properties, MpesaCrypto crypto, ObjectMapper json) {
    this(
        properties,
        crypto,
        json,
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build());
  }

  DarajaClient(MpesaProperties properties, MpesaCrypto crypto, ObjectMapper json, HttpClient http) {
    this.properties = properties;
    this.crypto = crypto;
    this.json = json;
    this.http = http;
  }

  public static class Rejected extends RuntimeException {
    public Rejected() {
      super("Daraja rejected the payment request");
    }
  }

  public static class Uncertain extends RuntimeException {
    public Uncertain() {
      super("Daraja payment response is uncertain");
    }
  }

  private synchronized String accessToken() {
    if (token != null && Instant.now().isBefore(tokenExpires)) return token;
    try {
      String basic =
          Base64.getEncoder()
              .encodeToString(
                  (properties.consumerKey() + ":" + properties.consumerSecret())
                      .getBytes(StandardCharsets.UTF_8));
      JsonNode response =
          send(
              HttpRequest.newBuilder(
                      URI.create(
                          properties.baseUrl()
                              + "/oauth/v1/generate?grant_type=client_credentials"))
                  .timeout(properties.requestTimeout())
                  .header("Authorization", "Basic " + basic)
                  .GET()
                  .build());
      String next = response.path("access_token").asText("");
      if (next.isBlank() || next.length() > 8192) throw new Rejected();
      long lifetime = Math.min(3600, response.path("expires_in").asLong(3600));
      if (lifetime <= 0) throw new Rejected();
      token = next;
      tokenExpires = Instant.now().plusSeconds(Math.max(1, lifetime - 60));
      return token;
    } catch (RuntimeException failure) {
      // No charge request has been sent when OAuth fails. It is safe to report rejection.
      throw new Rejected();
    }
  }

  private Map<String, Object> credentials() {
    String timestamp =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Africa/Nairobi"))
            .format(Instant.now());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("BusinessShortCode", properties.shortcode());
    body.put("Timestamp", timestamp);
    body.put(
        "Password",
        Base64.getEncoder()
            .encodeToString(
                (properties.shortcode() + properties.passkey() + timestamp)
                    .getBytes(StandardCharsets.UTF_8)));
    return body;
  }

  public JsonNode push(MpesaDtos.Transaction tx) {
    String bearer = accessToken();
    String phone = crypto.decryptPhone(tx.phoneCiphertext());
    Map<String, Object> body = credentials();
    body.put("TransactionType", properties.transactionType());
    body.put("Amount", tx.amount().toBigIntegerExact());
    body.put("PartyA", phone);
    body.put("PartyB", properties.partyB());
    body.put("PhoneNumber", phone);
    body.put(
        "CallBackURL",
        properties.callbackBaseUrl()
            + "/api/v1/mpesa/callback/"
            + tx.id()
            + "/"
            + crypto.callbackToken(tx.id()));
    body.put("AccountReference", tx.id().toString().replace("-", "").substring(0, 12));
    body.put("TransactionDesc", "POS payment");
    JsonNode response = post("/mpesa/stkpush/v1/processrequest", bearer, body);
    if (!"0".equals(response.path("ResponseCode").asText())) {
      if (response.hasNonNull("ResponseCode")) throw new Rejected();
      throw new Uncertain();
    }
    return response;
  }

  public JsonNode query(String checkoutRequestId) {
    String bearer = accessToken();
    Map<String, Object> body = credentials();
    body.put("CheckoutRequestID", checkoutRequestId);
    return post("/mpesa/stkpushquery/v1/query", bearer, body);
  }

  private JsonNode post(String path, String bearer, Object body) {
    try {
      return send(
          HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
              .timeout(properties.requestTimeout())
              .header("Authorization", "Bearer " + bearer)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build());
    } catch (Rejected | Uncertain error) {
      throw error;
    } catch (Exception error) {
      throw new Uncertain();
    }
  }

  private JsonNode send(HttpRequest request) {
    var task = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    try {
      HttpResponse<byte[]> response =
          task.get(
              properties.requestTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (response.statusCode() == 400
          || response.statusCode() == 401
          || response.statusCode() == 403) {
        if (response.statusCode() == 401) {
          token = null;
          tokenExpires = Instant.EPOCH;
        }
        throw new Rejected();
      }
      if (response.statusCode() != 200) throw new Uncertain();
      byte[] bytes = response.body();
      if (bytes.length > 32768) throw new Uncertain();
      JsonNode value = json.readTree(bytes);
      if (value == null || !value.isObject()) throw new Uncertain();
      return value;
    } catch (InterruptedException error) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw new Uncertain();
    } catch (Rejected | Uncertain error) {
      throw error;
    } catch (Exception error) {
      task.cancel(true);
      throw new Uncertain();
    }
  }
}
