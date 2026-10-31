package com.company.fashionpos.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentDeliveryService {

  private static final URI RESEND_EMAILS_URI = URI.create("https://api.resend.com/emails");
  private static final int MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String resendApiKey;
  private final String defaultFromEmail;

  public DocumentDeliveryService(
      ObjectMapper objectMapper,
      @Value("${app.delivery.resend.api-key:}") String resendApiKey,
      @Value("${app.delivery.resend.from-email:}") String defaultFromEmail) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    this.objectMapper = objectMapper;
    this.resendApiKey = resendApiKey;
    this.defaultFromEmail = defaultFromEmail;
  }

  public DocumentEmailResponse sendEmail(DocumentEmailRequest request) {
    String apiKey = requireConfigured(resendApiKey, "RESEND_API_KEY");
    String from =
        hasText(request.from())
            ? request.from().trim()
            : requireConfigured(defaultFromEmail, "RESEND_FROM_EMAIL");
    byte[] attachment = decodeAttachment(request.attachmentContentBase64());

    Map<String, Object> payload =
        Map.of(
            "from",
            from,
            "to",
            List.of(request.to().trim()),
            "subject",
            request.subject().trim(),
            "html",
            htmlBody(request.message()),
            "attachments",
            List.of(
                Map.of(
                    "filename",
                    safeFileName(request.attachmentFileName()),
                    "content",
                    Base64.getEncoder().encodeToString(attachment))));

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(RESEND_EMAILS_URI)
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();
      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Resend email failed with status " + response.statusCode());
      }
      JsonNode body = objectMapper.readTree(response.body());
      String id = body.path("id").asText("");
      return new DocumentEmailResponse(id, "SENT");
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to send email through Resend", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Email delivery was interrupted", exception);
    }
  }

  public void sendSummary(String to, String subject, String message, String idempotencyKey) {
    String apiKey = requireConfigured(resendApiKey, "RESEND_API_KEY");
    String from = requireConfigured(defaultFromEmail, "RESEND_FROM_EMAIL");
    try {
      HttpRequest request =
          HttpRequest.newBuilder(RESEND_EMAILS_URI)
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", idempotencyKey)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "from",
                              from,
                              "to",
                              List.of(to),
                              "subject",
                              subject,
                              "text",
                              message))))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalStateException("Email provider returned status " + response.statusCode());
    } catch (IOException e) {
      throw new IllegalStateException("Email delivery failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Email delivery interrupted", e);
    }
  }

  private static byte[] decodeAttachment(String attachmentContentBase64) {
    try {
      byte[] decoded = Base64.getDecoder().decode(attachmentContentBase64);
      if (decoded.length == 0) {
        throw new IllegalArgumentException("Attachment cannot be empty");
      }
      if (decoded.length > MAX_ATTACHMENT_BYTES) {
        throw new IllegalArgumentException("Attachment must be 8 MB or smaller");
      }
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Attachment must be a valid PDF payload");
    }
  }

  private static String htmlBody(String message) {
    return "<p>"
        + escapeHtml(message.trim()).replace("\n", "<br>")
        + "</p><p>The PDF document is attached.</p>";
  }

  private static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String safeFileName(String fileName) {
    String normalized = fileName.trim().replaceAll("[\\\\/:*?\"<>|]+", "-");
    return normalized.toLowerCase().endsWith(".pdf") ? normalized : normalized + ".pdf";
  }

  private static String requireConfigured(String value, String variableName) {
    if (!hasText(value)) {
      throw new IllegalStateException(variableName + " must be configured before sending email");
    }
    return value.trim();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
