package com.company.fashionpos.mpesa;

import static com.company.fashionpos.mpesa.MpesaDtos.*;

import com.company.fashionpos.sales.PaymentMethod;
import com.company.fashionpos.sales.SaleRequest;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MpesaLedger {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final MpesaCrypto crypto;
  private final MpesaProperties properties;

  public MpesaLedger(
      JdbcTemplate jdbc, ObjectMapper json, MpesaCrypto crypto, MpesaProperties properties) {
    this.jdbc = jdbc;
    this.json = json;
    this.crypto = crypto;
    this.properties = properties;
  }

  public String saleHash(SaleRequest sale) {
    try {
      Map<String, Object> value = new TreeMap<>();
      value.put("branch", sale.branchId());
      value.put("customer", trimmed(sale.customerName()));
      value.put("discount", money(sale.discountAmount()));
      Map<String, Integer> lines = new TreeMap<>();
      sale.lines()
          .forEach(
              line -> lines.merge(line.productId().toString(), line.quantity(), Math::addExact));
      value.put("lines", lines);
      value.put(
          "payments",
          sale.payments() == null
              ? List.of()
              : sale.payments().stream()
                  .map(
                      p ->
                          List.of(
                              p.method().name(),
                              money(p.amount()),
                              p.method() == PaymentMethod.MPESA
                                  ? ""
                                  : trimmed(p.paymentReference()),
                              p.method() == PaymentMethod.CASH
                                  ? money(p.cashReceived() == null ? p.amount() : p.cashReceived())
                                  : ""))
                  .toList());
      value.put("legacyMethod", sale.paymentMethod() == null ? "" : sale.paymentMethod().name());
      return MpesaCrypto.sha256(json.writeValueAsString(value));
    } catch (Exception invalid) {
      throw new IllegalArgumentException("Invalid checkout payment details");
    }
  }

  private static String money(BigDecimal amount) {
    return (amount == null ? BigDecimal.ZERO : amount)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString();
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }

  private static Transaction row(ResultSet r, int ignored) throws SQLException {
    return new Transaction(
        r.getObject("id", UUID.class),
        r.getObject("organization_id", UUID.class),
        r.getObject("branch_id", UUID.class),
        r.getObject("user_id", UUID.class),
        r.getString("idempotency_key"),
        r.getString("request_hash"),
        r.getString("sale_hash"),
        r.getString("phone_ciphertext"),
        r.getString("phone_hash"),
        r.getString("phone_mask"),
        r.getBigDecimal("amount"),
        r.getString("status"),
        r.getString("merchant_request_id"),
        r.getString("checkout_request_id"),
        r.getString("receipt_number"),
        r.getObject("result_code", Integer.class),
        r.getString("result_description"),
        r.getObject("sale_id", UUID.class),
        r.getTimestamp("created_at").toInstant(),
        r.getInt("query_attempts"));
  }

  private Transaction require(UUID id) {
    return jdbc
        .query("select * from mpesa_transactions where id=? for update", MpesaLedger::row, id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
  }

  private void checkAccess(AuthenticatedUser user, Transaction tx) {
    if (!tx.organizationId().equals(user.organizationId())
        || (!user.hasPermission("admin:manage") && !tx.userId().equals(user.userId()))
        || (!user.branchIds().isEmpty() && !user.branchIds().contains(tx.branchId())))
      throw new EntityNotFoundException("Payment not found");
  }

  @Transactional(readOnly = true)
  public Payment get(AuthenticatedUser user, UUID id) {
    var tx =
        jdbc
            .query(
                "select * from mpesa_transactions where id=? and organization_id=?",
                MpesaLedger::row,
                id,
                user.organizationId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
    checkAccess(user, tx);
    return tx.payment();
  }

  @Transactional(readOnly = true)
  public Payment getByKey(AuthenticatedUser user, String key) {
    var tx =
        jdbc
            .query(
                "select * from mpesa_transactions where organization_id=? and idempotency_key=?",
                MpesaLedger::row,
                user.organizationId(),
                key)
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
    checkAccess(user, tx);
    return tx.payment();
  }

  @Transactional(readOnly = true)
  public List<Payment> list(AuthenticatedUser user) {
    return jdbc
        .query(
            "select * from mpesa_transactions where organization_id=? order by created_at desc limit 100",
            MpesaLedger::row,
            user.organizationId())
        .stream()
        .filter(
            t ->
                (user.hasPermission("admin:manage") || t.userId().equals(user.userId()))
                    && (user.branchIds().isEmpty() || user.branchIds().contains(t.branchId())))
        .map(Transaction::payment)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<Payment> replay(AuthenticatedUser user, String key, String requestHash) {
    return jdbc
        .query(
            "select * from mpesa_transactions where organization_id=? and idempotency_key=?",
            MpesaLedger::row,
            user.organizationId(),
            key)
        .stream()
        .findFirst()
        .map(
            tx -> {
              checkAccess(user, tx);
              if (!tx.requestHash().equals(requestHash))
                throw conflict("This payment key already belongs to different checkout details");
              return tx.payment();
            });
  }

  @Transactional
  public Payment create(
      AuthenticatedUser user,
      SaleRequest sale,
      String key,
      String saleHash,
      String phone,
      BigDecimal amount) {
    if (!properties.organizationId().equals(user.organizationId()))
      throw new AccessDeniedException("M-Pesa is not configured for this business");
    jdbc.queryForObject(
        "select id from users where id=? and organization_id=? for update",
        UUID.class,
        user.userId(),
        user.organizationId());
    String phoneHash = crypto.phoneHash(phone);
    String requestHash = MpesaCrypto.sha256(phoneHash + ":" + saleHash);
    var existing = replay(user, key, requestHash);
    if (existing.isPresent()) return existing.get();
    Integer recent =
        jdbc.queryForObject(
            "select count(*) from mpesa_transactions where user_id=? and created_at>now()-interval '1 minute'",
            Integer.class,
            user.userId());
    if (recent != null && recent >= 5)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Too many payment requests. Wait a minute before trying again.");
    UUID id = UUID.randomUUID();
    int inserted =
        jdbc.update(
            """
        insert into mpesa_transactions(id,organization_id,branch_id,user_id,idempotency_key,request_hash,sale_hash,
          phone_ciphertext,phone_hash,phone_mask,amount,checkout_payload,provider_account,status,result_description)
        values (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,'QUEUED','Preparing payment request') on conflict do nothing
        """,
            id,
            user.organizationId(),
            sale.branchId(),
            user.userId(),
            key,
            requestHash,
            saleHash,
            crypto.encryptPhone(phone),
            phoneHash,
            "254***" + phone.substring(phone.length() - 4),
            amount,
            checkoutPayload(sale),
            properties.accountFingerprint());
    if (inserted == 0)
      return replay(user, key, requestHash)
          .orElseThrow(
              () ->
                  conflict(
                      "This phone has an unresolved payment. Check its existing status before requesting another payment."));
    return require(id).payment();
  }

  private String checkoutPayload(SaleRequest request) {
    try {
      return json.writeValueAsString(request);
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      throw new IllegalArgumentException("Invalid checkout details");
    }
  }

  @Transactional
  public Optional<Transaction> claimPush() {
    jdbc.update(
        """
        update mpesa_transactions set status='UNKNOWN',updated_at=now(),
          result_description='Payment submission is uncertain; do not send another request'
        where status='SENDING' and updated_at<now()-interval '45 seconds'
        """);
    var next =
        jdbc.query(
            "select * from mpesa_transactions where status='QUEUED' and provider_account=? order by created_at limit 1 for update skip locked",
            MpesaLedger::row,
            properties.accountFingerprint());
    if (next.isEmpty()) return Optional.empty();
    Transaction tx = next.get(0);
    jdbc.update(
        "update mpesa_transactions set status='SENDING',updated_at=now() where id=?", tx.id());
    return Optional.of(tx);
  }

  @Transactional
  public void submitted(UUID id, String merchant, String checkout) {
    Transaction tx = require(id);
    if ((tx.checkoutRequestId() != null && !tx.checkoutRequestId().equals(checkout))
        || (tx.merchantRequestId() != null && !tx.merchantRequestId().equals(merchant))) {
      review(id, "Conflicting provider identifiers require reconciliation");
      return;
    }
    jdbc.update(
        """
        update mpesa_transactions set merchant_request_id=?,checkout_request_id=?,updated_at=now(),
          status=case when status in ('SENDING','UNKNOWN') then 'PENDING' else status end,
          result_description=case when status in ('SENDING','UNKNOWN') then 'Enter your M-Pesa PIN on your phone' else result_description end
        where id=?
        """,
        merchant,
        checkout,
        id);
  }

  @Transactional
  public void submissionFailed(UUID id, boolean definitelyRejected) {
    jdbc.update(
        """
        update mpesa_transactions set status=?,updated_at=now(),result_description=?
        where id=? and status='SENDING'
        """,
        definitelyRejected ? "FAILED" : "UNKNOWN",
        definitelyRejected
            ? "The payment request was rejected before it was accepted"
            : "Payment submission is uncertain; do not send another request",
        id);
  }

  @Transactional
  public Optional<Transaction> claimQuery() {
    var rows =
        jdbc.query(
            """
        select * from mpesa_transactions where status in ('PENDING','UNKNOWN','PAID_UNCONFIRMED')
          and checkout_request_id is not null and next_query_at<=now() and provider_account=?
        order by next_query_at limit 1 for update skip locked
        """,
            MpesaLedger::row,
            properties.accountFingerprint());
    if (rows.isEmpty()) return Optional.empty();
    Transaction tx = rows.get(0);
    if (tx.queryAttempts() >= 15) {
      review(tx.id(), "Payment needs reconciliation. Do not charge again.");
      return Optional.empty();
    }
    jdbc.update(
        "update mpesa_transactions set query_attempts=query_attempts+1,next_query_at=now()+interval '30 seconds' where id=?",
        tx.id());
    return Optional.of(tx);
  }

  @Transactional
  public void queried(UUID id, JsonNode response) {
    Transaction tx = require(id);
    if (!List.of("PENDING", "UNKNOWN", "PAID_UNCONFIRMED").contains(tx.status())) return;
    if (!tx.checkoutRequestId().equals(text(response, "CheckoutRequestID"))) return;
    String merchant = text(response, "MerchantRequestID");
    if (merchant != null && !merchant.equals(tx.merchantRequestId())) {
      review(id, "Provider identifiers do not match");
      return;
    }
    Integer code = resultCode(response);
    if (code == null) return;
    // The query API confirms status but does not supply a receipt. Never invent one.
    if (code == 0) {
      jdbc.update(
          "update mpesa_transactions set status='PAID_UNCONFIRMED',result_code=0,result_description='Payment received; awaiting verified receipt',updated_at=now() where id=?",
          id);
    } else if (List.of(1, 17, 1019, 1032, 1037, 2001).contains(code)
        && !tx.status().equals("PAID_UNCONFIRMED")) {
      jdbc.update(
          "update mpesa_transactions set status='FAILED',result_code=?,result_description=?,updated_at=now() where id=?",
          code,
          failureMessage(code),
          id);
    }
  }

  @Transactional
  public void callback(UUID id, JsonNode payload, String payloadHash) {
    Transaction tx = require(id);
    JsonNode callback = payload.path("Body").path("stkCallback");
    String checkout = identifier(callback, "CheckoutRequestID");
    String merchant = identifier(callback, "MerchantRequestID");
    Integer code = resultCode(callback);
    if (code == null) throw new IllegalArgumentException("Missing callback result code");
    if (tx.status().equals("QUEUED")) throw conflict("Payment has not been dispatched");
    if ((tx.checkoutRequestId() != null && !tx.checkoutRequestId().equals(checkout))
        || (tx.merchantRequestId() != null && !tx.merchantRequestId().equals(merchant)))
      throw conflict("Callback identifiers do not match");
    int inserted =
        jdbc.update(
            "insert into mpesa_callback_events(id,transaction_id,payload_hash,result_code) values (?,?,?,?) on conflict do nothing",
            UUID.randomUUID(),
            id,
            payloadHash,
            code);
    if (inserted == 0) return;
    if (code == 0) {
      Map<String, JsonNode> metadata = new HashMap<>();
      JsonNode items = callback.path("CallbackMetadata").path("Item");
      if (!items.isArray() || items.size() > 20)
        throw new IllegalArgumentException("Invalid payment metadata");
      for (JsonNode item : items) {
        String name = item.path("Name").asText();
        if (metadata.putIfAbsent(name, item.path("Value")) != null)
          throw new IllegalArgumentException("Duplicate callback metadata");
      }
      BigDecimal paid;
      try {
        paid = new BigDecimal(metadata.getOrDefault("Amount", json.nullNode()).asText());
      } catch (RuntimeException invalid) {
        throw new IllegalArgumentException("Missing payment amount");
      }
      String receipt = metadata.getOrDefault("MpesaReceiptNumber", json.nullNode()).asText("");
      if (!receipt.matches("[A-Z0-9]{8,40}"))
        throw new IllegalArgumentException("Missing or invalid payment receipt");
      java.time.Instant paidAt;
      try {
        paidAt =
            LocalDateTime.parse(
                    metadata.getOrDefault("TransactionDate", json.nullNode()).asText(""),
                    DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
                        .withResolverStyle(ResolverStyle.STRICT))
                .atZone(ZoneId.of("Africa/Nairobi"))
                .toInstant();
      } catch (java.time.DateTimeException invalid) {
        throw new IllegalArgumentException("Invalid payment timestamp");
      }
      var receiptOwner =
          jdbc.queryForList(
              "select id from mpesa_transactions where receipt_number=? and id<>?",
              UUID.class,
              receipt,
              id);
      if (!receiptOwner.isEmpty()) {
        review(id, "This receipt already belongs to another payment");
        return;
      }
      String phone = metadata.getOrDefault("PhoneNumber", json.nullNode()).asText("");
      boolean matchesPhone =
          !phone.matches("(?:0|254|\\+254)[17]\\d{8}")
              || crypto.phoneHash(MpesaCrypto.normalizePhone(phone)).equals(tx.phoneHash());
      if (paid.compareTo(tx.amount()) != 0 || !matchesPhone) {
        review(id, "Payment amount or payer does not match the request");
        return;
      }
      if (tx.receiptNumber() != null) {
        if (!tx.receiptNumber().equals(receipt))
          review(id, "Conflicting payment receipts require reconciliation");
        return;
      }
      String status =
          tx.status().equals("FAILED") || tx.status().equals("REVIEW") ? "REVIEW" : "SUCCEEDED";
      jdbc.update(
          """
          update mpesa_transactions set status=?,merchant_request_id=?,checkout_request_id=?,receipt_number=?,
            result_code=0,result_description=?,paid_at=?,updated_at=now() where id=?
          """,
          status,
          merchant,
          checkout,
          receipt,
          status.equals("SUCCEEDED")
              ? "Payment confirmed"
              : "Payment needs reconciliation. Do not charge again.",
          java.sql.Timestamp.from(paidAt),
          id);
    } else if (!List.of("SUCCEEDED", "REVIEW").contains(tx.status())) {
      if (tx.status().equals("PAID_UNCONFIRMED")) {
        review(id, "Conflicting payment results require reconciliation");
        return;
      }
      if (!List.of(1, 17, 1019, 1032, 1037, 2001).contains(code)) {
        review(id, "Unrecognized payment result needs reconciliation. Do not charge again.");
        return;
      }
      jdbc.update(
          "update mpesa_transactions set status='FAILED',merchant_request_id=?,checkout_request_id=?,result_code=?,result_description=?,updated_at=now() where id=?",
          merchant,
          checkout,
          code,
          failureMessage(code),
          id);
    }
  }

  private void review(UUID id, String message) {
    jdbc.update(
        "update mpesa_transactions set status='REVIEW',result_description=?,updated_at=now() where id=?",
        message,
        id);
  }

  /**
   * Called inside the sale transaction: verification, inventory and receipt consumption commit
   * together.
   */
  @Transactional
  public void consume(AuthenticatedUser user, String key, SaleRequest request, UUID saleId) {
    var existing =
        jdbc.query(
            "select * from mpesa_transactions where organization_id=? and idempotency_key=? for update",
            MpesaLedger::row,
            user.organizationId(),
            key);
    if (!properties.enabled() && existing.isEmpty()) return;
    if (request.payments() == null
        || request.payments().stream().noneMatch(p -> p.method() == PaymentMethod.MPESA)) {
      if (!existing.isEmpty() || request.paymentMethod() == PaymentMethod.MPESA)
        throw conflict("A verified M-Pesa payment is required");
      return;
    }
    if (request.payments().stream().filter(p -> p.method() == PaymentMethod.MPESA).count() != 1)
      throw conflict("Use one M-Pesa tender per checkout");
    var payment =
        request.payments().stream()
            .filter(p -> p.method() == PaymentMethod.MPESA)
            .findFirst()
            .orElseThrow();
    Transaction tx =
        existing.stream()
            .findFirst()
            .orElseThrow(() -> conflict("A verified M-Pesa payment is required"));
    checkAccess(user, tx);
    if (!"SUCCEEDED".equals(tx.status())
        || tx.saleId() != null
        || !request.branchId().equals(tx.branchId())
        || !saleHash(request).equals(tx.saleHash())
        || !Objects.equals(payment.paymentReference(), tx.receiptNumber())
        || payment.amount().compareTo(tx.amount()) != 0)
      throw conflict(
          "This M-Pesa payment is unconfirmed, already used, or belongs to a different checkout");
    jdbc.update(
        "update mpesa_transactions set sale_id=?,updated_at=now() where id=?", saleId, tx.id());
  }

  static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  static String identifier(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || !value.matches("[A-Za-z0-9_-]{1,160}"))
      throw new IllegalArgumentException("Invalid callback identifiers");
    return value;
  }

  static Integer resultCode(JsonNode node) {
    String code = text(node, "ResultCode");
    return code != null && code.matches("\\d{1,6}") ? Integer.valueOf(code) : null;
  }

  private static String failureMessage(int code) {
    return switch (code) {
      case 1032 -> "Payment cancelled on the phone";
      case 1037, 1019 -> "The payment prompt expired or the phone could not be reached";
      case 1 -> "Insufficient M-Pesa balance";
      case 2001 -> "M-Pesa could not authorize the payment";
      default -> "M-Pesa did not complete this payment";
    };
  }

  static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
