package com.company.fashionpos.customers;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final BranchRepository branchRepository;
  private final JdbcTemplate jdbcTemplate;

  public CustomerService(
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      BranchRepository branchRepository,
      JdbcTemplate jdbcTemplate) {
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.branchRepository = branchRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public List<CustomerResponse> list(AuthenticatedUser user) {
    return customerRepository.findWithinOrganization(user.organizationId()).stream()
        .map(CustomerResponse::from)
        .toList();
  }

  @Transactional
  public CustomerResponse create(AuthenticatedUser user, CustomerRequest request) {
    String customerCode = normalizeCode(request.customerCode());
    if (customerRepository.existsByCustomerCodeWithinOrganization(
        user.organizationId(), customerCode)) {
      throw new IllegalArgumentException("Customer code is already used");
    }

    Organization organization =
        organizationRepository
            .findById(user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    Branch branch = branchForRequest(user, request.branchId());
    Customer customer =
        new Customer(
            UUID.randomUUID(), organization, branch, customerCode, normalizeName(request.name()));
    updateCustomerDetails(user, customer, request, customerCode);
    return CustomerResponse.from(customerRepository.save(customer));
  }

  @Transactional
  public CustomerResponse update(AuthenticatedUser user, UUID customerId, CustomerRequest request) {
    Customer customer = requireCustomer(user, customerId);
    String customerCode = normalizeCode(request.customerCode());
    if (customerRepository.existsByCustomerCodeWithinOrganizationExcludingCustomer(
        user.organizationId(), customerCode, customerId)) {
      throw new IllegalArgumentException("Customer code is already used");
    }

    updateCustomerDetails(user, customer, request, customerCode);
    return CustomerResponse.from(customer);
  }

  @Transactional
  public CustomerResponse clearCustomerData(AuthenticatedUser user, UUID customerId) {
    Customer customer = requireCustomer(user, customerId);
    customer.clearBalances();
    return CustomerResponse.from(customer);
  }

  @Transactional
  public void delete(AuthenticatedUser user, UUID customerId) {
    Customer customer = requireCustomer(user, customerId);
    customerRepository.delete(customer);
  }

  @Transactional(readOnly = true)
  public CustomerPeriodReportResponse periodReport(
      AuthenticatedUser user, UUID customerId, LocalDate fromDate, LocalDate toDate) {
    Customer customer = requireCustomer(user, customerId);
    Organization organization = customer.getOrganization();
    LocalDate normalizedTo =
        toDate == null ? LocalDate.now(ZoneId.of(organization.getTimeZone())) : toDate;
    LocalDate normalizedFrom =
        fromDate == null ? normalizedTo.minusMonths(1).plusDays(1) : fromDate;
    if (normalizedTo.isBefore(normalizedFrom)) {
      throw new IllegalArgumentException("To date cannot be before from date");
    }

    ZoneId zoneId = ZoneId.of(organization.getTimeZone());
    Instant fromInclusive = normalizedFrom.atStartOfDay(zoneId).toInstant();
    Instant toExclusive = normalizedTo.plusDays(1).atStartOfDay(zoneId).toInstant();
    List<CustomerSaleActivityResponse> activities =
        jdbcTemplate.query(
            """
            select
              sale.sale_number as reference,
              branch.name as branch_name,
              sale.sold_at as occurred_at,
              sale.total_amount as bought_amount,
              coalesce(sum(payment.amount), 0) as paid_amount
            from sales sale
            join branches branch on branch.id = sale.branch_id
            left join payments payment on payment.sale_id = sale.id
            where sale.organization_id = ?
              and sale.sold_at >= ?
              and sale.sold_at < ?
              and lower(trim(coalesce(sale.customer_name, ''))) = lower(trim(?))
            group by sale.id, sale.sale_number, branch.name, sale.sold_at, sale.total_amount
            order by sale.sold_at desc
            """,
            this::activityFromResultSet,
            user.organizationId(),
            fromInclusive,
            toExclusive,
            customer.getName());

    BigDecimal boughtAmount =
        activities.stream()
            .map(CustomerSaleActivityResponse::boughtAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal paidAmount =
        activities.stream()
            .map(CustomerSaleActivityResponse::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new CustomerPeriodReportResponse(
        customer.getId(),
        customer.getName(),
        normalizedFrom,
        normalizedTo,
        money(boughtAmount),
        money(paidAmount),
        money(boughtAmount.subtract(paidAmount)),
        activities.size(),
        activities);
  }

  private void updateCustomerDetails(
      AuthenticatedUser user, Customer customer, CustomerRequest request, String customerCode) {
    customer.updateDetails(
        branchForRequest(user, request.branchId()),
        customerCode,
        normalizeName(request.name()),
        normalizeOptional(request.contactPerson()),
        normalizeOptional(request.phone()),
        normalizeOptional(request.email()),
        normalizeOptional(request.address()),
        normalizeOptional(request.kraPin()),
        request.customerType() == null ? CustomerType.INDIVIDUAL : request.customerType(),
        nonNegativeMoney(request.creditLimit(), "Credit limit"),
        nonNegativeMoney(request.outstandingBalance(), "Outstanding balance"),
        normalizeOptional(request.paymentTerms()),
        normalizeOptional(request.notes()),
        request.status() == null ? CustomerStatus.ACTIVE : request.status());
  }

  private Customer requireCustomer(AuthenticatedUser user, UUID customerId) {
    return customerRepository
        .findWithinOrganization(customerId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
  }

  private Branch branchForRequest(AuthenticatedUser user, UUID branchId) {
    if (branchId == null) {
      return null;
    }
    if (!user.branchIds().isEmpty() && !user.branchIds().contains(branchId)) {
      throw new EntityNotFoundException("Branch not found");
    }
    return branchRepository
        .findWithinOrganization(branchId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
  }

  private CustomerSaleActivityResponse activityFromResultSet(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new CustomerSaleActivityResponse(
        resultSet.getString("reference"),
        resultSet.getString("branch_name"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getBigDecimal("bought_amount"),
        resultSet.getBigDecimal("paid_amount"));
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase();
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static BigDecimal nonNegativeMoney(BigDecimal value, String label) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(label + " cannot be negative");
    }
    return money(value);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
