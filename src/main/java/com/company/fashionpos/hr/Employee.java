package com.company.fashionpos.hr;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.staff.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class Employee {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "primary_branch_id", nullable = false)
  private Branch primaryBranch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_title_id")
  private JobTitle jobTitle;

  @Column(name = "employee_number", nullable = false, length = 40)
  private String employeeNumber;

  @Column(name = "first_name", nullable = false, length = 80)
  private String firstName;

  @Column(name = "middle_name", length = 80)
  private String middleName;

  @Column(name = "last_name", nullable = false, length = 80)
  private String lastName;

  @Column(name = "preferred_name", length = 80)
  private String preferredName;

  @Column(length = 254)
  private String email;

  @Column(length = 40)
  private String phone;

  @Column(length = 32)
  private String gender;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "national_id_number", length = 80)
  private String nationalIdNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_type", nullable = false, length = 32)
  private EmployeeEmploymentType employmentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_status", nullable = false, length = 32)
  private EmployeeEmploymentStatus employmentStatus;

  @Column(name = "joining_date", nullable = false)
  private LocalDate joiningDate;

  @Column(name = "probation_end_date")
  private LocalDate probationEndDate;

  @Column(name = "contract_start_date")
  private LocalDate contractStartDate;

  @Column(name = "contract_end_date")
  private LocalDate contractEndDate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_employee_id")
  private Employee manager;

  @Column(name = "work_location", length = 160)
  private String workLocation;

  @Column(name = "emergency_contact_name", length = 120)
  private String emergencyContactName;

  @Column(name = "emergency_contact_phone", length = 40)
  private String emergencyContactPhone;

  @Column(length = 500)
  private String address;

  @Column(length = 2000)
  private String notes;

  @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
  private BigDecimal basicSalary;

  @Enumerated(EnumType.STRING)
  @Column(name = "salary_payment_method", nullable = false, length = 16)
  private SalaryPaymentMethod salaryPaymentMethod;

  @Column(name = "bank_name", length = 120)
  private String bankName;

  @Column(name = "bank_account_number", length = 80)
  private String bankAccountNumber;

  @Column(name = "bank_account_name", length = 160)
  private String bankAccountName;

  @Column(name = "mpesa_number", length = 40)
  private String mpesaNumber;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected Employee() {}

  public Employee(
      UUID id,
      Organization organization,
      Branch primaryBranch,
      String employeeNumber,
      String firstName,
      String lastName,
      EmployeeEmploymentType employmentType,
      EmployeeEmploymentStatus employmentStatus,
      LocalDate joiningDate) {
    this.id = id;
    this.organization = organization;
    this.primaryBranch = primaryBranch;
    this.employeeNumber = employeeNumber;
    this.firstName = firstName;
    this.lastName = lastName;
    this.employmentType = employmentType;
    this.employmentStatus = employmentStatus;
    this.joiningDate = joiningDate;
    this.basicSalary = BigDecimal.ZERO.setScale(2);
    this.salaryPaymentMethod = SalaryPaymentMethod.UNSPECIFIED;
    this.active = true;
  }

  public void updateDetails(
      UserAccount user,
      Branch primaryBranch,
      Department department,
      JobTitle jobTitle,
      String employeeNumber,
      String firstName,
      String middleName,
      String lastName,
      String preferredName,
      String email,
      String phone,
      String gender,
      LocalDate dateOfBirth,
      String nationalIdNumber,
      EmployeeEmploymentType employmentType,
      EmployeeEmploymentStatus employmentStatus,
      LocalDate joiningDate,
      LocalDate probationEndDate,
      LocalDate contractStartDate,
      LocalDate contractEndDate,
      Employee manager,
      String workLocation,
      String emergencyContactName,
      String emergencyContactPhone,
      String address,
      String notes,
      BigDecimal basicSalary,
      SalaryPaymentMethod salaryPaymentMethod,
      String bankName,
      String bankAccountNumber,
      String bankAccountName,
      String mpesaNumber,
      boolean active) {
    this.user = user;
    this.primaryBranch = primaryBranch;
    this.department = department;
    this.jobTitle = jobTitle;
    this.employeeNumber = employeeNumber;
    this.firstName = firstName;
    this.middleName = middleName;
    this.lastName = lastName;
    this.preferredName = preferredName;
    this.email = email;
    this.phone = phone;
    this.gender = gender;
    this.dateOfBirth = dateOfBirth;
    this.nationalIdNumber = nationalIdNumber;
    this.employmentType = employmentType;
    this.employmentStatus = employmentStatus;
    this.joiningDate = joiningDate;
    this.probationEndDate = probationEndDate;
    this.contractStartDate = contractStartDate;
    this.contractEndDate = contractEndDate;
    this.manager = manager;
    this.workLocation = workLocation;
    this.emergencyContactName = emergencyContactName;
    this.emergencyContactPhone = emergencyContactPhone;
    this.address = address;
    this.notes = notes;
    this.basicSalary = basicSalary;
    this.salaryPaymentMethod =
        salaryPaymentMethod == null ? SalaryPaymentMethod.UNSPECIFIED : salaryPaymentMethod;
    this.bankName = bankName;
    this.bankAccountNumber = bankAccountNumber;
    this.bankAccountName = bankAccountName;
    this.mpesaNumber = mpesaNumber;
    this.active = active;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
    basicSalary = basicSalary == null ? BigDecimal.ZERO.setScale(2) : basicSalary;
    salaryPaymentMethod =
        salaryPaymentMethod == null ? SalaryPaymentMethod.UNSPECIFIED : salaryPaymentMethod;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Organization getOrganization() {
    return organization;
  }

  public UserAccount getUser() {
    return user;
  }

  public Branch getPrimaryBranch() {
    return primaryBranch;
  }

  public Department getDepartment() {
    return department;
  }

  public JobTitle getJobTitle() {
    return jobTitle;
  }

  public String getEmployeeNumber() {
    return employeeNumber;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getMiddleName() {
    return middleName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getPreferredName() {
    return preferredName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getGender() {
    return gender;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getNationalIdNumber() {
    return nationalIdNumber;
  }

  public EmployeeEmploymentType getEmploymentType() {
    return employmentType;
  }

  public EmployeeEmploymentStatus getEmploymentStatus() {
    return employmentStatus;
  }

  public LocalDate getJoiningDate() {
    return joiningDate;
  }

  public LocalDate getProbationEndDate() {
    return probationEndDate;
  }

  public LocalDate getContractStartDate() {
    return contractStartDate;
  }

  public LocalDate getContractEndDate() {
    return contractEndDate;
  }

  public Employee getManager() {
    return manager;
  }

  public String getWorkLocation() {
    return workLocation;
  }

  public String getEmergencyContactName() {
    return emergencyContactName;
  }

  public String getEmergencyContactPhone() {
    return emergencyContactPhone;
  }

  public String getAddress() {
    return address;
  }

  public String getNotes() {
    return notes;
  }

  public BigDecimal getBasicSalary() {
    return basicSalary;
  }

  public SalaryPaymentMethod getSalaryPaymentMethod() {
    return salaryPaymentMethod;
  }

  public String getBankName() {
    return bankName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public String getMpesaNumber() {
    return mpesaNumber;
  }

  public boolean isActive() {
    return active;
  }
}
