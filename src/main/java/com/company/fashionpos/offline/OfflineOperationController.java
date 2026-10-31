package com.company.fashionpos.offline;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offline/operations")
public class OfflineOperationController {
  private final OfflineOperationService operations;
  private final AuthenticatedUserService users;
  private final JdbcTemplate jdbc;

  public OfflineOperationController(
      OfflineOperationService operations, AuthenticatedUserService users, JdbcTemplate jdbc) {
    this.operations = operations;
    this.users = users;
    this.jdbc = jdbc;
  }

  @PostMapping
  public OfflineOperationService.Receipt apply(
      @Valid @RequestBody OfflineOperationService.Request request, Principal principal) {
    return operations.apply(users.current(principal), request);
  }

  @GetMapping("/suppliers")
  public List<Map<String, Object>> supplierNames(Principal principal) {
    var user = users.current(principal);
    if (!user.hasPermission("admin:manage") && !user.hasPermission("inventory:receive"))
      throw new AccessDeniedException("Stock receiving permission is required");
    // Receiving stock needs names, not supplier banking/contact/credit details.
    return jdbc.queryForList(
        "select id,name,status from suppliers where organization_id=? order by name",
        user.organizationId());
  }
}
