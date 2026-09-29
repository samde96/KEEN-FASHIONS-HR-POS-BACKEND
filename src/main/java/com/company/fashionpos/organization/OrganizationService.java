package com.company.fashionpos.organization;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  public OrganizationService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Transactional(readOnly = true)
  public OrganizationResponse currentOrganization(AuthenticatedUser user) {
    return OrganizationResponse.from(requireOrganization(user));
  }

  @Transactional
  public OrganizationResponse updateVatSettings(
      AuthenticatedUser user, VatSettingsRequest request) {
    Organization organization = requireOrganization(user);
    organization.updateVatSettings(request.defaultProductVatCategory());
    return OrganizationResponse.from(organization);
  }

  private Organization requireOrganization(AuthenticatedUser user) {
    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    return organization;
  }
}
