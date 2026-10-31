package com.company.fashionpos.offline;

import com.company.fashionpos.inventory.*;
import com.company.fashionpos.operations.StockOperationService;
import com.company.fashionpos.sales.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflineOperationService {
  public record Request(
      @NotNull UUID id,
      @NotNull UUID userId,
      @NotNull Instant occurredAt,
      @NotBlank @Size(max = 32) String kind,
      @NotNull JsonNode payload,
      @Size(max = 5000) List<@NotNull UUID> pendingIds) {}

  public record Confirmation(UUID id, JsonNode result, Instant appliedAt) {}

  public record Receipt(
      UUID id,
      JsonNode result,
      List<InventoryResponse> inventory,
      Instant appliedAt,
      List<Confirmation> confirmed,
      long revision) {}

  public record StockReceipt(
      @NotNull UUID draftId,
      @NotNull UUID branchId,
      @NotNull JsonNode form,
      @NotEmpty @Size(max = 500) List<@Valid StockIntakeRequest> lines,
      @Min(0) long version) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Validator validator;
  private final SaleService sales;
  private final InventoryService inventory;
  private final StockOperationService stock;

  public OfflineOperationService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      Validator validator,
      SaleService sales,
      InventoryService inventory,
      StockOperationService stock) {
    this.jdbc = jdbc;
    this.json = json;
    this.validator = validator;
    this.sales = sales;
    this.inventory = inventory;
    this.stock = stock;
  }

  @Transactional
  public Receipt apply(AuthenticatedUser user, Request request) {
    if (!user.userId().equals(request.userId()))
      throw new AccessDeniedException("Sign in as the operator who saved this transaction");
    if (request.occurredAt().isAfter(Instant.now().plusSeconds(300)))
      throw new IllegalArgumentException("The device clock is ahead. Correct it before retrying");
    requireKind(user, request.kind());
    // All devices for this operator share a lock. This makes receipt discovery and
    // the returned stock baseline consistent with other queued writes by that user.
    jdbc.queryForObject(
        "select id from users where id=? and organization_id=? for update",
        UUID.class,
        user.userId(),
        user.organizationId());
    com.fasterxml.jackson.databind.node.ObjectNode canonical = json.valueToTree(request);
    canonical.remove("pendingIds");
    String payload = encode(canonical);
    if (payload.length() > 2000000)
      throw new IllegalArgumentException("Offline operation is too large");
    // Serializes retries across tabs, servers and restored backups. The second insert waits
    // for the first transaction, then reads its receipt. Rollbacks leave no false receipt.
    jdbc.update(
        "insert into offline_operations(organization_id,id,user_id,kind,request_payload,occurred_at) values (?,?,?,?,?::jsonb,?) on conflict do nothing",
        user.organizationId(),
        request.id(),
        user.userId(),
        request.kind(),
        payload,
        java.sql.Timestamp.from(request.occurredAt()));
    var row =
        jdbc.queryForMap(
            "select user_id, request_payload=?::jsonb same_payload, result_payload::text result, applied_at from offline_operations where organization_id=? and id=? for update",
            payload,
            user.organizationId(),
            request.id());
    if (!user.userId().equals(row.get("user_id")) || !Boolean.TRUE.equals(row.get("same_payload")))
      throw new IllegalArgumentException(
          "This operation ID already belongs to different transaction details");
    if (row.get("result") != null)
      return receipt(
          user,
          request,
          decode((String) row.get("result")),
          ((java.sql.Timestamp) row.get("applied_at")).toInstant());
    Object result;
    switch (request.kind()) {
      case "SALE" -> {
        require(user, "pos:sell");
        var deviceSale = parse(request.payload(), SaleService.DeviceSale.class);
        for (var price : deviceSale.prices()) {
          Integer matches =
              jdbc.queryForObject(
                  "select count(*) from catalog_price_history where organization_id=? and product_id=? and round(unit_price,2)=? and vat_category=? and valid_from<=? and (valid_to is null or valid_to+interval '30 days'>=?)",
                  Integer.class,
                  user.organizationId(),
                  price.productId(),
                  price.unitPrice(),
                  price.vatCategory().name(),
                  java.sql.Timestamp.from(request.occurredAt().plusSeconds(300)),
                  java.sql.Timestamp.from(request.occurredAt()));
          if (matches == null || matches == 0)
            throw new IllegalArgumentException(
                "The captured price or VAT does not match a published catalogue price. The saved sale needs review");
        }
        result =
            sales.createDeviceSale(
                user, "device:" + request.id(), deviceSale, request.occurredAt());
      }
      case "STOCK_RECEIPT" -> {
        require(user, "inventory:receive");
        var receipt = parse(request.payload(), StockReceipt.class);
        // Reuse the existing draft ID so an earlier completion response lost in transit
        // cannot cause a second receipt when a draft is recovered offline.
        result =
            stock.completeDevice(
                user,
                receipt.draftId(),
                new StockOperationService.DraftRequest(
                    receipt.branchId(), receipt.form(), receipt.lines(), receipt.version(), null),
                request.occurredAt());
        com.fasterxml.jackson.databind.node.ObjectNode completed = json.valueToTree(result);
        completed.set(
            "intakes", json.valueToTree(inventory.listOperationIntakes(user, receipt.draftId())));
        result = completed;
      }
      case "STOCK_INTAKE" -> {
        require(user, "inventory:receive");
        result =
            inventory.addStock(
                user,
                parse(request.payload(), StockIntakeRequest.class),
                null,
                request.occurredAt());
      }
      case "STOCK_ADJUSTMENT" -> {
        require(user, "inventory:adjust");
        var node = request.payload();
        var adjustment = parse(node.path("adjustment"), StockAdjustmentRequest.class);
        inventory.requireExpectedQuantity(
            user,
            adjustment.branchId(),
            adjustment.productId(),
            node.path("expectedQuantity").asInt(-1));
        result = inventory.adjustStock(user, adjustment, request.occurredAt());
      }
      case "STOCK_TRANSFER" -> {
        require(user, "inventory:adjust");
        result =
            inventory.transferStock(
                user, parse(request.payload(), InventoryService.TransferRequest.class));
      }
      default -> throw new IllegalArgumentException("Unsupported offline operation");
    }
    Instant applied = Instant.now();
    JsonNode saved = json.valueToTree(result);
    jdbc.update(
        "update offline_operations set result_payload=?::jsonb,applied_at=? where organization_id=? and id=?",
        encode(saved),
        java.sql.Timestamp.from(applied),
        user.organizationId(),
        request.id());
    return receipt(user, request, saved, applied);
  }

  private Receipt receipt(
      AuthenticatedUser user, Request request, JsonNode result, Instant appliedAt) {
    var ids = new HashSet<>(request.pendingIds() == null ? List.<UUID>of() : request.pendingIds());
    ids.add(request.id());
    String array =
        "{"
            + ids.stream()
                .map(UUID::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","))
            + "}";
    List<Confirmation> confirmed =
        jdbc.query(
            "select id,result_payload::text result,applied_at from offline_operations where organization_id=? and user_id=? and id=any(?::uuid[]) and applied_at is not null",
            (r, n) ->
                new Confirmation(
                    r.getObject("id", UUID.class),
                    redact(user, decode(r.getString("result"))),
                    r.getTimestamp("applied_at").toInstant()),
            user.organizationId(),
            user.userId(),
            array);
    long revision = jdbc.queryForObject("select nextval('offline_snapshot_revision')", Long.class);
    return new Receipt(
        request.id(),
        redact(user, result),
        inventory.listInventory(user),
        appliedAt,
        confirmed,
        revision);
  }

  private JsonNode redact(AuthenticatedUser user, JsonNode result) {
    JsonNode copy = result.deepCopy();
    if (!user.canViewProfit() && copy.path("lines").isArray())
      for (var line : copy.path("lines"))
        if (line.isObject())
          ((com.fasterxml.jackson.databind.node.ObjectNode) line).remove("costPrice");
    return copy;
  }

  private void requireKind(AuthenticatedUser user, String kind) {
    switch (kind) {
      case "SALE" -> require(user, "pos:sell");
      case "STOCK_RECEIPT", "STOCK_INTAKE" -> require(user, "inventory:receive");
      case "STOCK_ADJUSTMENT", "STOCK_TRANSFER" -> require(user, "inventory:adjust");
      default -> throw new IllegalArgumentException("Unsupported offline operation");
    }
  }

  private void require(AuthenticatedUser user, String permission) {
    if (!user.hasPermission("admin:manage") && !user.hasPermission(permission))
      throw new AccessDeniedException("Your current account cannot synchronize this operation");
  }

  private <T> T parse(JsonNode node, Class<T> type) {
    try {
      T value = json.treeToValue(node, type);
      if (value == null) throw new IllegalArgumentException("Operation details are required");
      var errors = validator.validate(value);
      if (!errors.isEmpty())
        throw new IllegalArgumentException(
            errors.iterator().next().getPropertyPath()
                + " "
                + errors.iterator().next().getMessage());
      return value;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid operation details", e);
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid operation", e);
    }
  }

  private JsonNode decode(String value) {
    try {
      return json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid saved operation", e);
    }
  }
}
