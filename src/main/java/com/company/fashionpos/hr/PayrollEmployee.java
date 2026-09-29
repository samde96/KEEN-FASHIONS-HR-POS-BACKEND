package com.company.fashionpos.hr;

import com.company.fashionpos.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_employees")
public class PayrollEmployee {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payroll_run_id", nullable = false)
  private PayrollRun payrollRun;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Column(name = "gross_pay", nullable = false, precision = 14, scale = 2)
  private BigDecimal grossPay;

  @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
  private BigDecimal basicSalary;

  @Column(name = "bonus_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal bonusAmount;

  @Column(name = "automatic_bonus_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal automaticBonusAmount;

  @Column(name = "manual_bonus_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal manualBonusAmount;

  @Column(name = "loss_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal lossAmount;

  @Column(name = "sales_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal salesAmount;

  @Column(name = "qualifying_sales_days", nullable = false)
  private int qualifyingSalesDays;

  @Column(name = "sales_bonus_target", nullable = false, precision = 14, scale = 2)
  private BigDecimal salesBonusTarget;

  @Column(name = "sales_bonus_per_day", nullable = false, precision = 14, scale = 2)
  private BigDecimal salesBonusPerDay;

  @Column(name = "performance_score")
  private Integer performanceScore;

  @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalDeductions;

  @Column(name = "net_pay", nullable = false, precision = 14, scale = 2)
  private BigDecimal netPay;

  @Column(name = "payment_method", nullable = false, length = 16)
  private String paymentMethod;

  @Column(name = "payment_destination", length = 220)
  private String paymentDestination;

  @Column(length = 1000)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected PayrollEmployee() {}

  public PayrollEmployee(
      UUID id, Organization organization, PayrollRun payrollRun, Employee employee) {
    this.id = id;
    this.organization = organization;
    this.payrollRun = payrollRun;
    this.employee = employee;
    this.grossPay = BigDecimal.ZERO.setScale(2);
    this.basicSalary = BigDecimal.ZERO.setScale(2);
    this.bonusAmount = BigDecimal.ZERO.setScale(2);
    this.automaticBonusAmount = BigDecimal.ZERO.setScale(2);
    this.manualBonusAmount = BigDecimal.ZERO.setScale(2);
    this.lossAmount = BigDecimal.ZERO.setScale(2);
    this.salesAmount = BigDecimal.ZERO.setScale(2);
    this.salesBonusTarget = BigDecimal.ZERO.setScale(2);
    this.salesBonusPerDay = BigDecimal.ZERO.setScale(2);
    this.totalDeductions = BigDecimal.ZERO.setScale(2);
    this.netPay = BigDecimal.ZERO.setScale(2);
    this.paymentMethod = SalaryPaymentMethod.UNSPECIFIED.name();
  }

  public void updateTotals(
      BigDecimal grossPay,
      BigDecimal basicSalary,
      BigDecimal bonusAmount,
      BigDecimal automaticBonusAmount,
      BigDecimal manualBonusAmount,
      BigDecimal lossAmount,
      BigDecimal salesAmount,
      int qualifyingSalesDays,
      BigDecimal salesBonusTarget,
      BigDecimal salesBonusPerDay,
      Integer performanceScore,
      BigDecimal totalDeductions,
      BigDecimal netPay,
      SalaryPaymentMethod paymentMethod,
      String paymentDestination,
      String notes) {
    this.grossPay = grossPay;
    this.basicSalary = basicSalary;
    this.bonusAmount = bonusAmount;
    this.automaticBonusAmount = automaticBonusAmount;
    this.manualBonusAmount = manualBonusAmount;
    this.lossAmount = lossAmount;
    this.salesAmount = salesAmount;
    this.qualifyingSalesDays = qualifyingSalesDays;
    this.salesBonusTarget = salesBonusTarget;
    this.salesBonusPerDay = salesBonusPerDay;
    this.performanceScore = performanceScore;
    this.totalDeductions = totalDeductions;
    this.netPay = netPay;
    this.paymentMethod =
        paymentMethod == null ? SalaryPaymentMethod.UNSPECIFIED.name() : paymentMethod.name();
    this.paymentDestination = paymentDestination;
    this.notes = notes;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public PayrollRun getPayrollRun() {
    return payrollRun;
  }

  public Employee getEmployee() {
    return employee;
  }

  public BigDecimal getGrossPay() {
    return grossPay;
  }

  public BigDecimal getBasicSalary() {
    return basicSalary;
  }

  public BigDecimal getBonusAmount() {
    return bonusAmount;
  }

  public BigDecimal getAutomaticBonusAmount() {
    return automaticBonusAmount;
  }

  public BigDecimal getManualBonusAmount() {
    return manualBonusAmount;
  }

  public BigDecimal getLossAmount() {
    return lossAmount;
  }

  public BigDecimal getSalesAmount() {
    return salesAmount;
  }

  public int getQualifyingSalesDays() {
    return qualifyingSalesDays;
  }

  public BigDecimal getSalesBonusTarget() {
    return salesBonusTarget;
  }

  public BigDecimal getSalesBonusPerDay() {
    return salesBonusPerDay;
  }

  public Integer getPerformanceScore() {
    return performanceScore;
  }

  public BigDecimal getTotalDeductions() {
    return totalDeductions;
  }

  public BigDecimal getNetPay() {
    return netPay;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public String getPaymentDestination() {
    return paymentDestination;
  }

  public String getNotes() {
    return notes;
  }
}
