package com.company.fashionpos.suppliers;

import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

  private final SupplierRepository supplierRepository;
  private final OrganizationRepository organizationRepository;

  public SupplierService(
      SupplierRepository supplierRepository, OrganizationRepository organizationRepository) {
    this.supplierRepository = supplierRepository;
    this.organizationRepository = organizationRepository;
  }

  @Transactional(readOnly = true)
  public List<SupplierResponse> list(AuthenticatedUser user) {
    return supplierRepository.findWithinOrganization(user.organizationId()).stream()
        .map(SupplierResponse::from)
        .toList();
  }

  @Transactional
  public SupplierResponse create(AuthenticatedUser user, SupplierRequest request) {
    String name = normalizeName(request.name());
    if (supplierRepository.existsByNameWithinOrganization(user.organizationId(), name)) {
      throw new IllegalArgumentException("Supplier name is already used");
    }
    String supplierCode = normalizeOptional(request.supplierCode());
    if (supplierCode != null
        && supplierRepository.existsBySupplierCodeWithinOrganization(
            user.organizationId(), supplierCode)) {
      throw new IllegalArgumentException("Supplier code is already used");
    }

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    Supplier supplier = new Supplier(UUID.randomUUID(), organization, name);
    updateSupplierDetails(supplier, request, name);
    return SupplierResponse.from(supplierRepository.save(supplier));
  }

  @Transactional
  public SupplierResponse update(AuthenticatedUser user, UUID supplierId, SupplierRequest request) {
    Supplier supplier =
        supplierRepository
            .findWithinOrganization(supplierId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

    String name = normalizeName(request.name());
    if (supplierRepository.existsByNameWithinOrganizationExcludingSupplier(
        user.organizationId(), name, supplierId)) {
      throw new IllegalArgumentException("Supplier name is already used");
    }
    String supplierCode = normalizeOptional(request.supplierCode());
    if (supplierCode != null
        && supplierRepository.existsBySupplierCodeWithinOrganizationExcludingSupplier(
            user.organizationId(), supplierCode, supplierId)) {
      throw new IllegalArgumentException("Supplier code is already used");
    }

    updateSupplierDetails(supplier, request, name);
    return SupplierResponse.from(supplier);
  }

  @Transactional
  public void deactivate(AuthenticatedUser user, UUID supplierId) {
    Supplier supplier =
        supplierRepository
            .findWithinOrganization(supplierId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
    supplier.deactivate();
  }

  private static void updateSupplierDetails(
      Supplier supplier, SupplierRequest request, String name) {
    supplier.updateDetails(
        name,
        normalizeOptional(request.supplierCode()),
        normalizeOptional(request.companyName()),
        normalizeOptional(request.contactPerson()),
        normalizeOptional(request.phone()),
        normalizeOptional(request.email()),
        normalizeOptional(request.address()),
        normalizeOptional(request.kraPin()),
        normalizeOptional(request.paymentTerms()),
        nonNegativeMoney(request.creditLimit(), "Credit limit"),
        normalizeOptional(request.bankDetails()),
        normalizeOptional(request.productsSupplied()),
        nonNegativeMoney(request.outstandingBalance(), "Outstanding balance"),
        normalizeOptional(request.notes()),
        statusOrActive(request.status()));
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static SupplierStatus statusOrActive(SupplierStatus status) {
    return status == null ? SupplierStatus.ACTIVE : status;
  }

  private static BigDecimal nonNegativeMoney(BigDecimal value, String label) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(label + " cannot be negative");
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
