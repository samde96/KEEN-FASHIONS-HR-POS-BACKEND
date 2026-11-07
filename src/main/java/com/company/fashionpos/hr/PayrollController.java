package com.company.fashionpos.hr;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll")
@PreAuthorize("hasRole('ADMIN')")
public class PayrollController {
  private final PayrollService payroll;
  private final AuthenticatedUserService users;

  public PayrollController(PayrollService payroll, AuthenticatedUserService users) {
    this.payroll = payroll;
    this.users = users;
  }

  @GetMapping("/setup")
  public ResponseEntity<Map<String, Object>> setup(Principal principal) {
    return noStore(payroll.setup(users.current(principal)));
  }

  @GetMapping("/workspace")
  public ResponseEntity<Map<String, Object>> workspace(
      @RequestParam String month, Principal principal) {
    return noStore(payroll.workspace(users.current(principal), month));
  }

  @PostMapping("/bonus-rules")
  public ResponseEntity<Map<String, Object>> createBonusRule(
      @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveRule(users.current(principal), null, input));
  }

  @PutMapping("/bonus-rules/{id}")
  public ResponseEntity<Map<String, Object>> updateBonusRule(
      @PathVariable UUID id, @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveRule(users.current(principal), id, input));
  }

  @PostMapping("/adjustments")
  public ResponseEntity<Map<String, Object>> createAdjustment(
      @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveAdjustment(users.current(principal), null, input));
  }

  @PutMapping("/adjustments/{id}")
  public ResponseEntity<Map<String, Object>> updateAdjustment(
      @PathVariable UUID id, @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveAdjustment(users.current(principal), id, input));
  }

  @PostMapping("/losses")
  public ResponseEntity<Map<String, Object>> createLoss(
      @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveLoss(users.current(principal), null, input));
  }

  @PutMapping("/losses/{id}")
  public ResponseEntity<Map<String, Object>> updateLoss(
      @PathVariable UUID id, @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.saveLoss(users.current(principal), id, input));
  }

  @GetMapping("/loss-eligibility")
  public ResponseEntity<List<Map<String, Object>>> lossEligibility(
      @RequestParam UUID locationId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Principal principal) {
    return noStore(payroll.lossEligibility(users.current(principal), locationId, date));
  }

  @GetMapping("/funding")
  public ResponseEntity<Map<String, Object>> funding(
      @RequestParam String month, Principal principal) {
    return noStore(payroll.funding(users.current(principal), month));
  }

  @PostMapping("/funding")
  public ResponseEntity<Map<String, Object>> saveFunding(
      @RequestParam String month, @RequestBody Map<String, Object> input, Principal principal) {
    return noStore(payroll.funding(users.current(principal), month, input));
  }

  @PostMapping({"/runs/{month}/approve", "/payroll/{month}/approve"})
  public ResponseEntity<Map<String, Object>> approve(
      @PathVariable String month, @RequestBody Map<String, Object> command, Principal principal) {
    return noStore(payroll.payrollAction(users.current(principal), month, "approve", command));
  }

  @PostMapping({"/runs/{month}/{action:paid|reopen}", "/payroll/{month}/{action:paid|reopen}"})
  public ResponseEntity<Map<String, Object>> transition(
      @PathVariable String month,
      @PathVariable String action,
      @RequestBody Map<String, Object> command,
      Principal principal) {
    return noStore(payroll.payrollAction(users.current(principal), month, action, command));
  }

  @GetMapping("/reports")
  public ResponseEntity<Map<String, Object>> reports(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Principal principal) {
    return noStore(payroll.reports(users.current(principal), from, to));
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
