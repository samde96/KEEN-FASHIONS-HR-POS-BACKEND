package com.company.fashionpos.organization;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {

  private final OrganizationService organizationService;
  private final AuthenticatedUserService authenticatedUserService;

  public OrganizationController(
      OrganizationService organizationService, AuthenticatedUserService authenticatedUserService) {
    this.organizationService = organizationService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/current")
  public OrganizationResponse current(Principal principal) {
    return organizationService.currentOrganization(authenticatedUserService.current(principal));
  }

  @PutMapping("/current/vat-settings")
  public OrganizationResponse updateVatSettings(
      @Valid @RequestBody VatSettingsRequest request, Principal principal) {
    return organizationService.updateVatSettings(
        authenticatedUserService.current(principal), request);
  }
}
