package com.company.fashionpos.mpesa;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mpesa")
public class MpesaController {
  private final MpesaService service;
  private final MpesaLedger ledger;
  private final MpesaCrypto crypto;
  private final MpesaProperties properties;
  private final AuthenticatedUserService users;
  private final ObjectMapper json;

  public MpesaController(
      MpesaService service,
      MpesaLedger ledger,
      MpesaCrypto crypto,
      MpesaProperties properties,
      AuthenticatedUserService users,
      ObjectMapper json) {
    this.service = service;
    this.ledger = ledger;
    this.crypto = crypto;
    this.properties = properties;
    this.users = users;
    this.json = json;
  }

  @GetMapping("/config")
  public Map<String, Boolean> config(Principal principal) {
    return Map.of("enabled", service.enabled(users.current(principal)));
  }

  @PostMapping("/stkpush")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public MpesaDtos.Payment start(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody MpesaDtos.StartRequest request,
      Principal principal) {
    return service.start(users.current(principal), key, request);
  }

  @GetMapping("/transactions/{id}")
  public MpesaDtos.Payment get(@PathVariable UUID id, Principal principal) {
    return ledger.get(users.current(principal), id);
  }

  @GetMapping("/transactions")
  public List<MpesaDtos.Payment> list(Principal principal) {
    return ledger.list(users.current(principal));
  }

  @GetMapping("/transactions/by-key/{key}")
  public MpesaDtos.Payment getByKey(@PathVariable String key, Principal principal) {
    return ledger.getByKey(users.current(principal), key);
  }

  @PostMapping(value = "/callback/{id}/{token}", consumes = "application/json")
  public ResponseEntity<?> callback(
      @PathVariable UUID id, @PathVariable String token, HttpServletRequest request)
      throws java.io.IOException {
    if (!properties.enabled() || !crypto.validCallbackToken(id, token))
      return ResponseEntity.status(403).build();
    byte[] body = request.getInputStream().readNBytes(32769);
    if (body.length > 32768) return ResponseEntity.status(413).build();
    try {
      var payload = json.readTree(body);
      if (payload == null || !payload.isObject()) return ResponseEntity.badRequest().build();
      ledger.callback(id, payload, MpesaCrypto.sha256(new String(body, StandardCharsets.UTF_8)));
      return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException
        | IllegalArgumentException invalid) {
      return ResponseEntity.badRequest().build();
    }
  }
}
