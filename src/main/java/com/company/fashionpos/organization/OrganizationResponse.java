package com.company.fashionpos.organization;

import com.company.fashionpos.catalog.VatCategory;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    String currencyCode,
    String timeZone,
    String taxRegistrationNumber,
    VatCategory defaultProductVatCategory,
    OrganizationStatus status) {

  public static OrganizationResponse from(Organization organization) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getCurrencyCode(),
        organization.getTimeZone(),
        organization.getTaxRegistrationNumber(),
        organization.getDefaultProductVatCategory(),
        organization.getStatus());
  }
}
