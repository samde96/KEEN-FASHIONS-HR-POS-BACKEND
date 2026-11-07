package com.company.fashionpos.branch;

import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

  private final BranchRepository branchRepository;
  private final OrganizationRepository organizationRepository;

  public BranchService(
      BranchRepository branchRepository, OrganizationRepository organizationRepository) {
    this.branchRepository = branchRepository;
    this.organizationRepository = organizationRepository;
  }

  @Transactional(readOnly = true)
  public List<BranchResponse> listAuthorizedBranches(AuthenticatedUser user) {
    return branchRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(
            branch ->
                (!user.isCashier() && !user.isHotelSalesManager())
                    || user.branchIds().contains(branch.getId()))
        .map(BranchResponse::from)
        .toList();
  }

  @Transactional
  public BranchResponse create(AuthenticatedUser user, BranchRequest request) {
    String code =
        request.code() == null || request.code().isBlank()
            ? generateCode(user, request.name())
            : normalizeCode(request.code());
    if (branchRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Branch code is already used");
    }

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    Branch branch =
        new Branch(
            UUID.randomUUID(),
            organization,
            normalizeName(request.name()),
            code,
            normalizeTimeZone(request.timeZone()),
            request.branchType() == null ? BranchType.SHOP : request.branchType());
    branch.updateDetails(
        branch.getName(),
        branch.getCode(),
        branch.getTimeZone(),
        statusOrActive(request.status()),
        request.branchType() == null ? BranchType.SHOP : request.branchType());

    return BranchResponse.from(branchRepository.save(branch));
  }

  @Transactional
  public BranchResponse update(AuthenticatedUser user, UUID branchId, BranchRequest request) {
    Branch branch =
        branchRepository
            .findWithinOrganization(branchId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

    String code =
        request.code() == null || request.code().isBlank()
            ? branch.getCode()
            : normalizeCode(request.code());
    if (branchRepository.existsByCodeWithinOrganizationExcludingBranch(
        user.organizationId(), code, branchId)) {
      throw new IllegalArgumentException("Branch code is already used");
    }

    branch.updateDetails(
        normalizeName(request.name()),
        code,
        normalizeTimeZone(request.timeZone()),
        statusOrActive(request.status()),
        request.branchType() == null ? branch.getBranchType() : request.branchType());
    return BranchResponse.from(branch);
  }

  @Transactional
  public void deactivate(AuthenticatedUser user, UUID branchId) {
    Branch branch =
        branchRepository
            .findWithinOrganization(branchId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
    branch.updateDetails(
        branch.getName(),
        branch.getCode(),
        branch.getTimeZone(),
        BranchStatus.INACTIVE,
        branch.getBranchType());
  }

  @Transactional(readOnly = true)
  public void requireBranchAccess(AuthenticatedUser user, UUID branchId) {
    boolean branchIsInOrganization =
        branchRepository.existsWithinOrganization(branchId, user.organizationId());
    boolean userHasExplicitBranchScope = !user.branchIds().isEmpty();
    if (!branchIsInOrganization
        || ((user.isCashier() || user.isHotelSalesManager()) && user.branchIds().isEmpty())
        || (userHasExplicitBranchScope && !user.branchIds().contains(branchId))) {
      throw new AccessDeniedException("Branch is outside the authenticated user's scope");
    }
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String generateCode(AuthenticatedUser user, String name) {
    String base =
        normalizeName(name)
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (base.isEmpty()) {
      base = "LOCATION";
    }
    base = base.substring(0, Math.min(base.length(), 24)).replaceAll("-+$", "");
    if (base.isEmpty()) {
      base = "LOCATION";
    }

    String candidate = base;
    int suffix = 2;
    while (branchRepository.existsByCodeWithinOrganization(user.organizationId(), candidate)) {
      String suffixText = "-" + suffix++;
      int prefixLength = 24 - suffixText.length();
      candidate = base.substring(0, Math.min(base.length(), prefixLength)).replaceAll("-+$", "");
      candidate += suffixText;
    }
    return candidate;
  }

  private static String normalizeTimeZone(String value) {
    String normalized = value.trim();
    try {
      ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException("Time zone is not valid");
    }
    return normalized;
  }

  private static BranchStatus statusOrActive(BranchStatus status) {
    return status == null ? BranchStatus.ACTIVE : status;
  }
}
