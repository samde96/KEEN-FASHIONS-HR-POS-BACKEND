package com.company.fashionpos.modules;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ErpModulesController {

  private final ErpModulesService erpModulesService;
  private final AuthenticatedUserService authenticatedUserService;

  public ErpModulesController(
      ErpModulesService erpModulesService, AuthenticatedUserService authenticatedUserService) {
    this.erpModulesService = erpModulesService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/procurement/summary")
  public ModuleSummary procurementSummary(Principal principal) {
    return erpModulesService.procurementSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/customers/summary")
  public ModuleSummary customersSummary(Principal principal) {
    return erpModulesService.customersSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/crm/summary")
  public ModuleSummary crmSummary(Principal principal) {
    return erpModulesService.crmSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/commercial/summary")
  public ModuleSummary commercialSummary(Principal principal) {
    return erpModulesService.commercialSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/accounting/summary")
  public ModuleSummary accountingSummary(Principal principal) {
    return erpModulesService.accountingSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/notifications/summary")
  public ModuleSummary notificationsSummary(Principal principal) {
    return erpModulesService.notificationsSummary(authenticatedUserService.current(principal));
  }

  @GetMapping("/notifications")
  public List<NotificationResponse> notifications(Principal principal) {
    return erpModulesService.notifications(authenticatedUserService.current(principal));
  }

  @GetMapping("/audit-events")
  public List<AuditEventResponse> auditEvents(Principal principal) {
    return erpModulesService.auditEvents(authenticatedUserService.current(principal));
  }
}
