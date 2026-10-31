package com.company.fashionpos.shifts;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shifts")
public class CashierShiftController {
  private final CashierShiftService shifts;
  private final AuthenticatedUserService users;

  public CashierShiftController(CashierShiftService shifts, AuthenticatedUserService users) {
    this.shifts = shifts;
    this.users = users;
  }

  public record OpenRequest(
      @NotNull UUID branchId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal openingFloat,
      @Size(max = 1000) String openingNote) {}

  public record CloseRequest(
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal closingCash,
      @Size(max = 1000) String closingNote) {}

  @GetMapping
  public List<CashierShiftResponse> list(Principal principal) {
    return shifts.list(users.current(principal));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CashierShiftResponse open(@Valid @RequestBody OpenRequest request, Principal principal) {
    return shifts.open(users.current(principal), request);
  }

  @PostMapping("/{id}/close")
  public CashierShiftResponse close(
      @PathVariable UUID id, @Valid @RequestBody CloseRequest request, Principal principal) {
    return shifts.close(users.current(principal), id, request);
  }
}
