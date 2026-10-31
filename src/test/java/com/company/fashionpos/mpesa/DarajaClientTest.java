package com.company.fashionpos.mpesa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class DarajaClientTest {
  final ObjectMapper json = new ObjectMapper();
  final HttpClient http = mock(HttpClient.class);

  MpesaProperties properties(Duration timeout) {
    return new MpesaProperties(
        true,
        "sandbox",
        UUID.randomUUID(),
        "consumer",
        "consumer-secret",
        "passkey",
        "174379",
        "654321",
        "CustomerBuyGoodsOnline",
        "https://payments.example.test",
        "callback-secret-at-least-32-characters",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        List.of("196.201.214.200"),
        List.of(),
        Duration.ofMillis(50),
        timeout);
  }

  MpesaDtos.Transaction transaction(MpesaCrypto crypto) {
    return new MpesaDtos.Transaction(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        "hash",
        "hash",
        crypto.encryptPhone("254712345678"),
        "hash",
        "254***5678",
        BigDecimal.valueOf(123),
        "QUEUED",
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.now(),
        0);
  }

  @SuppressWarnings("unchecked")
  HttpResponse<byte[]> response(int status, String body) {
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
    return response;
  }

  static String requestBody(HttpRequest request) {
    var bytes = new java.io.ByteArrayOutputStream();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<ByteBuffer>() {
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              public void onNext(ByteBuffer item) {
                byte[] value = new byte[item.remaining()];
                item.get(value);
                bytes.writeBytes(value);
              }

              public void onError(Throwable error) {
                throw new AssertionError(error);
              }

              public void onComplete() {}
            });
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void usesExactDarajaPayloadCachesOAuthAndKeepsMerchantSettingsOnServer() throws Exception {
    var properties = properties(Duration.ofSeconds(1));
    var crypto = new MpesaCrypto(properties);
    List<HttpRequest> requests = new ArrayList<>();
    when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            call -> {
              HttpRequest request = call.getArgument(0);
              requests.add(request);
              return CompletableFuture.completedFuture(
                  request.uri().getPath().startsWith("/oauth")
                      ? response(200, "{\"access_token\":\"test-token\",\"expires_in\":\"3600\"}")
                      : response(
                          200,
                          "{\"ResponseCode\":\"0\",\"MerchantRequestID\":\"merchant-1\",\"CheckoutRequestID\":\"checkout-1\"}"));
            });
    var client = new DarajaClient(properties, crypto, json, http);
    var tx = transaction(crypto);
    client.push(tx);
    client.push(transaction(crypto));
    assertThat(requests).hasSize(3);
    assertThat(requests.get(0).uri().toString())
        .isEqualTo(
            "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials");
    assertThat(requests.get(0).headers().firstValue("Authorization").orElseThrow())
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("consumer:consumer-secret".getBytes(StandardCharsets.UTF_8)));
    HttpRequest push = requests.get(1);
    assertThat(push.uri().toString())
        .isEqualTo("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest");
    assertThat(push.headers().firstValue("Authorization").orElseThrow())
        .isEqualTo("Bearer test-token");
    var body = json.readTree(requestBody(push));
    assertThat(body.path("BusinessShortCode").asText()).isEqualTo("174379");
    assertThat(body.path("PartyB").asText()).isEqualTo("654321");
    assertThat(body.path("TransactionType").asText()).isEqualTo("CustomerBuyGoodsOnline");
    assertThat(body.path("Amount").asInt()).isEqualTo(123);
    assertThat(body.path("PartyA").asText()).isEqualTo("254712345678");
    assertThat(body.path("PhoneNumber").asText()).isEqualTo("254712345678");
    assertThat(body.path("AccountReference").asText()).hasSize(12);
    assertThat(body.path("TransactionDesc").asText().length()).isLessThanOrEqualTo(13);
    assertThat(body.path("CallBackURL").asText())
        .isEqualTo(
            "https://payments.example.test/api/v1/mpesa/callback/"
                + tx.id()
                + "/"
                + crypto.callbackToken(tx.id()));
    String timestamp = body.path("Timestamp").asText();
    assertThat(LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
        .isNotNull();
    assertThat(
            new String(
                Base64.getDecoder().decode(body.path("Password").asText()), StandardCharsets.UTF_8))
        .isEqualTo("174379passkey" + timestamp);
  }

  @Test
  void aTimeoutAfterSubmissionIsUncertainAndDoesNotRetryThePost() {
    var properties = properties(Duration.ofMillis(50));
    var crypto = new MpesaCrypto(properties);
    var pending = new CompletableFuture<HttpResponse<byte[]>>();
    when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenAnswer(
            call ->
                ((HttpRequest) call.getArgument(0)).uri().getPath().startsWith("/oauth")
                    ? CompletableFuture.completedFuture(
                        response(200, "{\"access_token\":\"test-token\",\"expires_in\":3600}"))
                    : pending);
    var client = new DarajaClient(properties, crypto, json, http);
    assertThatThrownBy(() -> client.push(transaction(crypto)))
        .isInstanceOf(DarajaClient.Uncertain.class);
    assertThat(pending.isCancelled()).isTrue();
    verify(http, times(2)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void failedOAuthNeverSubmitsACharge() {
    var properties = properties(Duration.ofSeconds(1));
    var crypto = new MpesaCrypto(properties);
    var rejected = response(401, "{}");
    when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(CompletableFuture.completedFuture(rejected));
    assertThatThrownBy(
            () -> new DarajaClient(properties, crypto, json, http).push(transaction(crypto)))
        .isInstanceOf(DarajaClient.Rejected.class);
    verify(http, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  void phoneEncryptionRejectsTamperingAndNormalizesBothKenyanMobilePrefixes() {
    var crypto = new MpesaCrypto(properties(Duration.ofSeconds(1)));
    assertThat(MpesaCrypto.normalizePhone("+254112345678")).isEqualTo("254112345678");
    assertThat(MpesaCrypto.normalizePhone("0712345678")).isEqualTo("254712345678");
    assertThatThrownBy(() -> MpesaCrypto.normalizePhone("+441234567890"))
        .isInstanceOf(IllegalArgumentException.class);
    String encrypted = crypto.encryptPhone("254712345678");
    byte[] tampered = Base64.getDecoder().decode(encrypted);
    tampered[tampered.length - 1] ^= 1;
    assertThatThrownBy(() -> crypto.decryptPhone(Base64.getEncoder().encodeToString(tampered)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(crypto.encryptPhone("254712345678")).isNotEqualTo(encrypted);
  }
}
