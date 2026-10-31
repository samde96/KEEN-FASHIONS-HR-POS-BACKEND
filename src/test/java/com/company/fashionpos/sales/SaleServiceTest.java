package com.company.fashionpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.catalog.ProductRepository;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaleServiceTest {

  private static final UUID ORGANIZATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");
  private static final UUID OTHER_BRANCH_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000102");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");

  private SaleRepository saleRepository;
  private SaleLineRepository saleLineRepository;
  private SalePaymentRepository salePaymentRepository;
  private OrganizationRepository organizationRepository;
  private SaleService saleService;
  private Organization organization;
  private Branch branch;
  private Branch otherBranch;

  @BeforeEach
  void setUp() {
    saleRepository = mock(SaleRepository.class);
    saleLineRepository = mock(SaleLineRepository.class);
    salePaymentRepository = mock(SalePaymentRepository.class);
    organizationRepository = mock(OrganizationRepository.class);
    saleService =
        new SaleService(
            saleRepository,
            saleLineRepository,
            salePaymentRepository,
            mock(BranchRepository.class),
            mock(BranchService.class),
            mock(ProductRepository.class),
            mock(InventoryRepository.class),
            organizationRepository,
            mock(com.company.fashionpos.shifts.CashierShiftService.class),
            mock(com.company.fashionpos.mpesa.MpesaLedger.class));

    organization = new Organization(ORGANIZATION_ID, "KEEN Fashions", "KES", "Africa/Nairobi");
    branch = new Branch(BRANCH_ID, organization, "Town Shop", "TOWN", "Africa/Nairobi");
    otherBranch =
        new Branch(OTHER_BRANCH_ID, organization, "Outlet Shop", "OUTLET", "Africa/Nairobi");
    when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
    when(saleLineRepository.findForSale(any(UUID.class))).thenReturn(List.of());
    when(salePaymentRepository.findForSale(any(UUID.class))).thenReturn(List.of());
  }

  @Test
  void listSalesFiltersByAuthorizedBranchWithinSelectedBusinessDate() {
    Sale accessibleSale = sale(UUID.fromString("00000000-0000-4000-8000-000000000301"), branch);
    Sale inaccessibleSale =
        sale(UUID.fromString("00000000-0000-4000-8000-000000000302"), otherBranch);
    when(saleRepository.findWithinOrganizationAndPeriod(
            eq(ORGANIZATION_ID),
            eq(Instant.parse("2026-09-19T21:00:00Z")),
            eq(Instant.parse("2026-09-20T21:00:00Z"))))
        .thenReturn(List.of(accessibleSale, inaccessibleSale));

    List<SaleResponse> sales =
        saleService.listSales(
            branchScopedUser(), LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-20"));

    assertThat(sales).extracting(SaleResponse::id).containsExactly(accessibleSale.getId());
    verify(saleRepository, never()).findWithinOrganization(ORGANIZATION_ID);
  }

  @Test
  void administratorCanSeeAllBranchSales() {
    Sale firstSale = sale(UUID.fromString("00000000-0000-4000-8000-000000000301"), branch);
    Sale secondSale = sale(UUID.fromString("00000000-0000-4000-8000-000000000302"), otherBranch);
    when(saleRepository.findWithinOrganization(ORGANIZATION_ID))
        .thenReturn(List.of(firstSale, secondSale));

    List<SaleResponse> sales = saleService.listSales(administrator(), null, null);

    assertThat(sales)
        .extracting(SaleResponse::id)
        .containsExactly(firstSale.getId(), secondSale.getId());
  }

  @Test
  void branchScopedUserCannotOpenAnotherBranchesSale() {
    Sale inaccessibleSale =
        sale(UUID.fromString("00000000-0000-4000-8000-000000000302"), otherBranch);
    when(saleRepository.findWithinOrganization(inaccessibleSale.getId(), ORGANIZATION_ID))
        .thenReturn(Optional.of(inaccessibleSale));

    assertThatThrownBy(() -> saleService.getSale(branchScopedUser(), inaccessibleSale.getId()))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Sale not found");
  }

  private Sale sale(UUID id, Branch saleBranch) {
    Sale sale =
        new Sale(
            id,
            organization,
            saleBranch,
            "SALE-" + id.toString().substring(0, 8),
            "idem-" + id,
            null,
            BigDecimal.valueOf(100),
            BigDecimal.ZERO,
            BigDecimal.valueOf(16),
            BigDecimal.valueOf(116));
    sale.onCreate();
    return sale;
  }

  private static AuthenticatedUser branchScopedUser() {
    return new AuthenticatedUser(
        USER_ID,
        "cashier@test.local",
        "Cashier User",
        ORGANIZATION_ID,
        Set.of(BRANCH_ID),
        Set.of("CASHIER"),
        Set.of("Cashier"),
        Set.of("sales:view"));
  }

  private static AuthenticatedUser administrator() {
    return new AuthenticatedUser(
        UUID.fromString("00000000-0000-4000-8000-000000000203"),
        "admin@test.local",
        "Admin User",
        ORGANIZATION_ID,
        Set.of(),
        Set.of("ADMIN"),
        Set.of("Administrator"),
        Set.of("admin:manage"));
  }
}
