package com.company.fashionpos.operations;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.inventory.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockOperationService {
  public record DraftRequest(
      @NotNull UUID branchId,
      @NotNull JsonNode form,
      @NotNull @Size(max = 500) List<@Valid StockIntakeRequest> lines,
      @Min(0) long version,
      JsonNode displayLines) {}

  public record Operation(
      UUID id,
      UUID branchId,
      String status,
      JsonNode payload,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      long version) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final BranchService branches;
  private final InventoryService inventory;

  public StockOperationService(
      JdbcTemplate jdbc, ObjectMapper json, BranchService branches, InventoryService inventory) {
    this.jdbc = jdbc;
    this.json = json;
    this.branches = branches;
    this.inventory = inventory;
  }

  public List<Operation> list(AuthenticatedUser user) {
    return jdbc
        .query(
            "select * from stock_operations where organization_id=? and user_id=? and status='DRAFT' order by updated_at desc",
            this::map,
            user.organizationId(),
            user.userId())
        .stream()
        .filter(o -> user.branchIds().isEmpty() || user.branchIds().contains(o.branchId()))
        .toList();
  }

  @Transactional
  public Operation save(AuthenticatedUser user, UUID id, DraftRequest request) {
    branches.requireBranchAccess(user, request.branchId());
    if (request.lines().stream().anyMatch(l -> !l.branchId().equals(request.branchId())))
      throw new IllegalArgumentException("A stock receipt must belong to one shop");
    String payload = encode(request);
    if (payload.length() > 2000000) throw new IllegalArgumentException("Draft is too large");
    jdbc.update(
        "insert into stock_operations (id,organization_id,user_id,branch_id,payload) values (?,?,?,?,?) on conflict (id) do nothing",
        id,
        user.organizationId(),
        user.userId(),
        request.branchId(),
        payload);
    Operation previous = require(user, id);
    if (!previous.status().equals("DRAFT"))
      throw new IllegalArgumentException("This operation is already finished");
    if (previous.version() != request.version())
      throw new IllegalArgumentException("Draft changed elsewhere. Reload before saving");
    jdbc.update(
        "update stock_operations set branch_id=?,payload=?,updated_at=now(),version=version+1 where id=?",
        request.branchId(),
        payload,
        id);
    return require(user, id);
  }

  @Transactional
  public Operation complete(AuthenticatedUser user, UUID id) {
    return complete(user, id, null);
  }

  private Operation complete(AuthenticatedUser user, UUID id, Instant occurredAt) {
    Operation operation = require(user, id);
    if (operation.status().equals("COMPLETED")) return operation;
    if (!operation.status().equals("DRAFT"))
      throw new IllegalArgumentException("Operation was discarded");
    DraftRequest request;
    try {
      request = json.treeToValue(operation.payload(), DraftRequest.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid stock draft", e);
    }
    if (request.lines().isEmpty())
      throw new IllegalArgumentException("Add at least one stock line");
    // Deterministic lock order avoids deadlocks when two receipts contain the same products.
    for (StockIntakeRequest line :
        request.lines().stream()
            .sorted(java.util.Comparator.comparing(StockIntakeRequest::productId))
            .toList()) inventory.addStock(user, line, id, occurredAt);
    jdbc.update(
        "update stock_operations set status='COMPLETED',completed_at=?,updated_at=now(),version=version+1 where id=?",
        java.sql.Timestamp.from(occurredAt == null ? Instant.now() : occurredAt),
        id);
    return require(user, id);
  }

  @Transactional
  public Operation completeDevice(
      AuthenticatedUser user, UUID id, DraftRequest request, Instant occurredAt) {
    jdbc.update(
        "insert into stock_operations(id,organization_id,user_id,branch_id,payload) values (?,?,?,?,?) on conflict(id) do nothing",
        id,
        user.organizationId(),
        user.userId(),
        request.branchId(),
        encode(request));
    Operation previous = require(user, id);
    boolean samePayload =
        jsonEqual(previous.payload().path("form"), request.form())
            && jsonEqual(previous.payload().path("lines"), json.valueToTree(request.lines()));
    if (previous.status().equals("COMPLETED")) {
      if (!samePayload)
        throw new IllegalArgumentException(
            "This receipt ID was already completed with different stock lines");
      return previous;
    }
    if (!previous.status().equals("DRAFT"))
      throw new IllegalArgumentException("This stock draft was discarded");
    if (previous.version() != request.version() && !samePayload)
      throw new IllegalArgumentException(
          "This stock draft changed on another device. Review it before synchronizing");
    save(
        user,
        id,
        new DraftRequest(
            request.branchId(),
            request.form(),
            request.lines(),
            previous.version(),
            request.displayLines()));
    return complete(user, id, occurredAt);
  }

  @Transactional
  public void discard(AuthenticatedUser user, UUID id) {
    Operation operation = require(user, id);
    if (!operation.status().equals("DRAFT"))
      throw new IllegalArgumentException("Only drafts can be discarded");
    jdbc.update(
        "update stock_operations set status='DISCARDED',completed_at=now(),updated_at=now(),version=version+1 where id=?",
        id);
  }

  private Operation require(AuthenticatedUser user, UUID id) {
    List<Operation> found =
        jdbc.query(
            "select * from stock_operations where id=? and organization_id=? and user_id=? for update",
            this::map,
            id,
            user.organizationId(),
            user.userId());
    if (found.isEmpty()) throw new EntityNotFoundException("Stock draft not found");
    branches.requireBranchAccess(user, found.get(0).branchId());
    return found.get(0);
  }

  private Operation map(ResultSet rs, int n) throws SQLException {
    try {
      return new Operation(
          rs.getObject("id", UUID.class),
          rs.getObject("branch_id", UUID.class),
          rs.getString("status"),
          json.readTree(rs.getString("payload")),
          rs.getTimestamp("created_at").toInstant(),
          rs.getTimestamp("updated_at").toInstant(),
          rs.getTimestamp("completed_at") == null
              ? null
              : rs.getTimestamp("completed_at").toInstant(),
          rs.getLong("version"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid stock draft", e);
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid stock draft", e);
    }
  }

  private static boolean jsonEqual(JsonNode a, JsonNode b) {
    return a.equals(
        (left, right) ->
            left.isNumber() && right.isNumber()
                ? left.decimalValue().compareTo(right.decimalValue())
                : left.equals(right) ? 0 : 1,
        b);
  }
}
