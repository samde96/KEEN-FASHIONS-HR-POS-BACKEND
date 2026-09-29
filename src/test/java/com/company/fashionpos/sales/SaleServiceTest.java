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
import com.company.fashionpos.hr.Employee;
import com.company.fashionpos.hr.EmployeeEmploymentStatus;
import com.company.fashionpos.hr.EmployeeEmploymentType;
import com.company.fashionpos.hr.EmployeeRepository;
import com.company.fashionpos.hr.SalaryPaymentMethod;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.staff.UserAccount;
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
  private static final UUID CASHIER_USER_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000201");
  private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000202");
  private static final UUID CASHIER_EMPLOYEE_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000301");
  private static final UUID OTHER_EMPLOYEE_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000302");

  private SaleRepository saleRepository;
  private SaleLineRepository saleLineRepository;
  private SalePaymentRepository salePaymentRepository;
  private OrganizationRepository organizationRepository;
  private SaleService saleService;
  private Organization organization;
  private Branch branch;

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
            mock(EmployeeRepository.class),
            organizationRepository);

    organization = new Organization(ORGANIZATION_ID, "KEEN Fashions", "KES", "Africa/Nairobi");
    branch = new Branch(BRANCH_ID, organization, "Town Shop", "TOWN", "Africa/Nairobi");
    when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
    when(saleLineRepository.findForSale(any(UUID.class))).thenReturn(List.of());
    when(salePaymentRepository.findForSale(any(UUID.class))).thenReturn(List.of());
  }

  @Test
  void cashierListIsLimitedToOwnSalesWithinSelectedBusinessDate() {
    Sale ownSale = sale(CASHIER_EMPLOYEE_ID, employee(CASHIER_EMPLOYEE_ID, CASHIER_USER_ID));
    Sale otherCashierSale = sale(OTHER_EMPLOYEE_ID, employee(OTHER_EMPLOYEE_ID, OTHER_USER_ID));
    Sale unassignedSale = sale(UUID.fromString("00000000-0000-4000-8000-000000000303"), null);
    when(saleRepository.findWithinOrganizationAndPeriod(
            eq(ORGANIZATION_ID),
            eq(Instant.parse("2026-09-19T21:00:00Z")),
            eq(Instant.parse("2026-09-20T21:00:00Z"))))
        .thenReturn(List.of(ownSale, otherCashierSale, unassignedSale));

    List<SaleResponse> sales =
        saleService.listSales(
            cashier(), LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-20"));

    assertThat(sales).extracting(SaleResponse::id).containsExactly(ownSale.getId());
    verify(saleRepository, never()).findWithinOrganization(ORGANIZATION_ID);
  }

  @Test
  void administratorCanSeeAllCashierSales() {
    Sale ownSale = sale(CASHIER_EMPLOYEE_ID, employee(CASHIER_EMPLOYEE_ID, CASHIER_USER_ID));
    Sale otherCashierSale = sale(OTHER_EMPLOYEE_ID, employee(OTHER_EMPLOYEE_ID, OTHER_USER_ID));
    Sale unassignedSale = sale(UUID.fromString("00000000-0000-4000-8000-000000000303"), null);
    when(saleRepository.findWithinOrganization(ORGANIZATION_ID))
        .thenReturn(List.of(ownSale, otherCashierSale, unassignedSale));

    List<SaleResponse> sales = saleService.listSales(administrator(), null, null);

    assertThat(sales)
        .extracting(SaleResponse::id)
        .containsExactly(ownSale.getId(), otherCashierSale.getId(), unassignedSale.getId());
  }

  @Test
  void cashierCannotOpenAnotherCashiersSale() {
    Sale otherCashierSale = sale(OTHER_EMPLOYEE_ID, employee(OTHER_EMPLOYEE_ID, OTHER_USER_ID));
    when(saleRepository.findWithinOrganization(otherCashierSale.getId(), ORGANIZATION_ID))
        .thenReturn(Optional.of(otherCashierSale));

    assertThatThrownBy(() -> saleService.getSale(cashier(), otherCashierSale.getId()))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Sale not found");
  }

  private Sale sale(UUID id, Employee employee) {
    Sale sale =
        new Sale(
            id,
            organization,
            branch,
            employee,
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

  private Employee employee(UUID employeeId, UUID userId) {
    UserAccount user = new UserAccount(userId, organization, userId + "@test.local", "Cashier");
    Employee employee =
        new Employee(
            employeeId,
            organization,
            branch,
            "EMP-" + employeeId.toString().substring(0, 8),
            "Cashier",
            "User",
            EmployeeEmploymentType.PERMANENT,
            EmployeeEmploymentStatus.ACTIVE,
            LocalDate.parse("2026-01-01"));
    employee.updateDetails(
        user,
        branch,
        null,
        null,
        employee.getEmployeeNumber(),
        employee.getFirstName(),
        null,
        employee.getLastName(),
        null,
        user.getEmail(),
        null,
        null,
        null,
        null,
        EmployeeEmploymentType.PERMANENT,
        EmployeeEmploymentStatus.ACTIVE,
        LocalDate.parse("2026-01-01"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        BigDecimal.ZERO,
        SalaryPaymentMethod.UNSPECIFIED,
        null,
        null,
        null,
        null,
        true);
    return employee;
  }

  private static AuthenticatedUser cashier() {
    return new AuthenticatedUser(
        CASHIER_USER_ID,
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
