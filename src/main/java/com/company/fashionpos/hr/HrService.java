package com.company.fashionpos.hr;

import com.company.fashionpos.branch.Branch;
import com.company.fashionpos.branch.BranchRepository;
import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.organization.Organization;
import com.company.fashionpos.organization.OrganizationRepository;
import com.company.fashionpos.sales.Sale;
import com.company.fashionpos.sales.SaleRepository;
import com.company.fashionpos.shared.audit.AuditEventService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.staff.UserAccount;
import com.company.fashionpos.staff.UserAccountRepository;
import com.company.fashionpos.staff.UserBranchAssignmentRepository;
import com.company.fashionpos.staff.UserStatus;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HrService {

  private static final long MAX_EMPLOYEE_DOCUMENT_BYTES = 10L * 1024L * 1024L;
  private static final String DEFAULT_DOCUMENT_CONTENT_TYPE = "application/octet-stream";
  private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES =
      Set.of(
          "application/pdf",
          "image/jpeg",
          "image/png",
          "image/webp",
          "text/plain",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final DepartmentRepository departmentRepository;
  private final JobTitleRepository jobTitleRepository;
  private final EmployeeRepository employeeRepository;
  private final EmployeeDocumentRepository employeeDocumentRepository;
  private final AttendanceRepository attendanceRepository;
  private final LeaveTypeRepository leaveTypeRepository;
  private final LeaveBalanceRepository leaveBalanceRepository;
  private final LeaveRequestRepository leaveRequestRepository;
  private final EmployeePerformanceEntryRepository employeePerformanceEntryRepository;
  private final PayrollComponentRepository payrollComponentRepository;
  private final PayrollSalesBonusRuleRepository payrollSalesBonusRuleRepository;
  private final PayrollPeriodRepository payrollPeriodRepository;
  private final PayrollRunRepository payrollRunRepository;
  private final PayrollEmployeeRepository payrollEmployeeRepository;
  private final OrganizationRepository organizationRepository;
  private final BranchRepository branchRepository;
  private final BranchService branchService;
  private final UserAccountRepository userAccountRepository;
  private final UserBranchAssignmentRepository userBranchAssignmentRepository;
  private final SaleRepository saleRepository;
  private final AuditEventService auditEventService;

  public HrService(
      DepartmentRepository departmentRepository,
      JobTitleRepository jobTitleRepository,
      EmployeeRepository employeeRepository,
      EmployeeDocumentRepository employeeDocumentRepository,
      AttendanceRepository attendanceRepository,
      LeaveTypeRepository leaveTypeRepository,
      LeaveBalanceRepository leaveBalanceRepository,
      LeaveRequestRepository leaveRequestRepository,
      EmployeePerformanceEntryRepository employeePerformanceEntryRepository,
      PayrollComponentRepository payrollComponentRepository,
      PayrollSalesBonusRuleRepository payrollSalesBonusRuleRepository,
      PayrollPeriodRepository payrollPeriodRepository,
      PayrollRunRepository payrollRunRepository,
      PayrollEmployeeRepository payrollEmployeeRepository,
      OrganizationRepository organizationRepository,
      BranchRepository branchRepository,
      BranchService branchService,
      UserAccountRepository userAccountRepository,
      UserBranchAssignmentRepository userBranchAssignmentRepository,
      SaleRepository saleRepository,
      AuditEventService auditEventService) {
    this.departmentRepository = departmentRepository;
    this.jobTitleRepository = jobTitleRepository;
    this.employeeRepository = employeeRepository;
    this.employeeDocumentRepository = employeeDocumentRepository;
    this.attendanceRepository = attendanceRepository;
    this.leaveTypeRepository = leaveTypeRepository;
    this.leaveBalanceRepository = leaveBalanceRepository;
    this.leaveRequestRepository = leaveRequestRepository;
    this.employeePerformanceEntryRepository = employeePerformanceEntryRepository;
    this.payrollComponentRepository = payrollComponentRepository;
    this.payrollSalesBonusRuleRepository = payrollSalesBonusRuleRepository;
    this.payrollPeriodRepository = payrollPeriodRepository;
    this.payrollRunRepository = payrollRunRepository;
    this.payrollEmployeeRepository = payrollEmployeeRepository;
    this.organizationRepository = organizationRepository;
    this.branchRepository = branchRepository;
    this.branchService = branchService;
    this.userAccountRepository = userAccountRepository;
    this.userBranchAssignmentRepository = userBranchAssignmentRepository;
    this.saleRepository = saleRepository;
    this.auditEventService = auditEventService;
  }

  @Transactional(readOnly = true)
  public List<DepartmentResponse> listDepartments(AuthenticatedUser user) {
    return departmentRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(department -> branchVisible(user, department.getBranch()))
        .map(DepartmentResponse::from)
        .toList();
  }

  @Transactional
  public DepartmentResponse createDepartment(AuthenticatedUser user, DepartmentRequest request) {
    String code = normalizeCode(request.code());
    if (departmentRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Department code is already used");
    }

    Organization organization = requireOrganization(user.organizationId());
    Department department =
        new Department(UUID.randomUUID(), organization, normalizeName(request.name()), code);
    updateDepartment(department, user, request);
    Department saved = departmentRepository.save(department);
    auditEventService.record(
        user,
        saved.getBranch() == null ? null : saved.getBranch().getId(),
        "hr.department.created",
        "Department",
        saved.getId(),
        Map.of("name", saved.getName(), "code", saved.getCode()));
    return DepartmentResponse.from(saved);
  }

  @Transactional
  public DepartmentResponse updateDepartment(
      AuthenticatedUser user, UUID departmentId, DepartmentRequest request) {
    Department department =
        departmentRepository
            .findWithinOrganization(departmentId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));

    String code = normalizeCode(request.code());
    if (departmentRepository.existsByCodeWithinOrganizationExcludingDepartment(
        user.organizationId(), code, departmentId)) {
      throw new IllegalArgumentException("Department code is already used");
    }

    updateDepartment(department, user, request);
    auditEventService.record(
        user,
        department.getBranch() == null ? null : department.getBranch().getId(),
        "hr.department.updated",
        "Department",
        department.getId(),
        Map.of("name", department.getName(), "code", department.getCode()));
    return DepartmentResponse.from(department);
  }

  @Transactional(readOnly = true)
  public List<JobTitleResponse> listJobTitles(AuthenticatedUser user) {
    return jobTitleRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(jobTitle -> branchVisible(user, jobTitle.getDepartment().getBranch()))
        .map(JobTitleResponse::from)
        .toList();
  }

  @Transactional
  public JobTitleResponse createJobTitle(AuthenticatedUser user, JobTitleRequest request) {
    String code = normalizeCode(request.code());
    if (jobTitleRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Job title code is already used");
    }

    Department department = requireDepartment(user, request.departmentId());
    Organization organization = requireOrganization(user.organizationId());
    JobTitle jobTitle =
        new JobTitle(
            UUID.randomUUID(), organization, department, normalizeName(request.title()), code);
    jobTitle.updateDetails(
        department,
        normalizeName(request.title()),
        code,
        normalizeOptional(request.description()),
        booleanOrTrue(request.active()));
    JobTitle saved = jobTitleRepository.save(jobTitle);
    auditEventService.record(
        user,
        department.getBranch() == null ? null : department.getBranch().getId(),
        "hr.job-title.created",
        "JobTitle",
        saved.getId(),
        Map.of("title", saved.getTitle(), "code", saved.getCode()));
    return JobTitleResponse.from(saved);
  }

  @Transactional
  public JobTitleResponse updateJobTitle(
      AuthenticatedUser user, UUID jobTitleId, JobTitleRequest request) {
    JobTitle jobTitle =
        jobTitleRepository
            .findWithinOrganization(jobTitleId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Job title not found"));

    String code = normalizeCode(request.code());
    if (jobTitleRepository.existsByCodeWithinOrganizationExcludingJobTitle(
        user.organizationId(), code, jobTitleId)) {
      throw new IllegalArgumentException("Job title code is already used");
    }

    Department department = requireDepartment(user, request.departmentId());
    jobTitle.updateDetails(
        department,
        normalizeName(request.title()),
        code,
        normalizeOptional(request.description()),
        booleanOrTrue(request.active()));
    auditEventService.record(
        user,
        department.getBranch() == null ? null : department.getBranch().getId(),
        "hr.job-title.updated",
        "JobTitle",
        jobTitle.getId(),
        Map.of("title", jobTitle.getTitle(), "code", jobTitle.getCode()));
    return JobTitleResponse.from(jobTitle);
  }

  @Transactional(readOnly = true)
  public EmployeePageResponse listEmployees(
      AuthenticatedUser user,
      String search,
      UUID branchId,
      UUID departmentId,
      UUID jobTitleId,
      EmployeeEmploymentStatus employmentStatus,
      String sort,
      int page,
      int size) {
    if (branchId != null) {
      branchService.requireBranchAccess(user, branchId);
    }
    String normalizedSearch = normalizeSearch(search);
    List<EmployeeListResponse> filtered =
        employeeRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(employee -> canAccessEmployee(user, employee))
            .filter(
                employee ->
                    branchId == null || employee.getPrimaryBranch().getId().equals(branchId))
            .filter(
                employee ->
                    departmentId == null
                        || (employee.getDepartment() != null
                            && employee.getDepartment().getId().equals(departmentId)))
            .filter(
                employee ->
                    jobTitleId == null
                        || (employee.getJobTitle() != null
                            && employee.getJobTitle().getId().equals(jobTitleId)))
            .filter(
                employee ->
                    employmentStatus == null || employee.getEmploymentStatus() == employmentStatus)
            .filter(employee -> matchesEmployeeSearch(employee, normalizedSearch))
            .sorted(EmployeeSort.order(sort))
            .map(EmployeeListResponse::from)
            .toList();

    int normalizedPage = Math.max(page, 0);
    int normalizedSize = size <= 0 ? 20 : Math.min(size, 200);
    int fromIndex = Math.min(normalizedPage * normalizedSize, filtered.size());
    int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
    return new EmployeePageResponse(filtered.subList(fromIndex, toIndex), filtered.size());
  }

  @Transactional(readOnly = true)
  public EmployeeProfileResponse getEmployee(AuthenticatedUser user, UUID employeeId) {
    Employee employee = requireEmployee(user, employeeId);
    return EmployeeProfileResponse.from(employee);
  }

  @Transactional(readOnly = true)
  public List<EmployeeDocumentResponse> listDocuments(
      AuthenticatedUser user, String search, UUID branchId, UUID employeeId, String documentType) {
    if (branchId != null) {
      branchService.requireBranchAccess(user, branchId);
    }
    if (employeeId != null) {
      Employee employee = requireEmployee(user, employeeId);
      if (branchId != null && !employee.getPrimaryBranch().getId().equals(branchId)) {
        throw new IllegalArgumentException("Employee does not belong to the selected branch");
      }
    }

    String normalizedSearch = normalizeSearch(search);
    String normalizedDocumentType = normalizeOptional(documentType);
    return employeeDocumentRepository.findWithinOrganization(user.organizationId()).stream()
        .filter(document -> canAccessEmployee(user, document.getEmployee()))
        .filter(
            document ->
                branchId == null
                    || document.getEmployee().getPrimaryBranch().getId().equals(branchId))
        .filter(document -> employeeId == null || document.getEmployee().getId().equals(employeeId))
        .filter(
            document ->
                normalizedDocumentType == null
                    || document.getDocumentType().equalsIgnoreCase(normalizedDocumentType))
        .filter(document -> matchesDocumentSearch(document, normalizedSearch))
        .map(EmployeeDocumentResponse::from)
        .toList();
  }

  @Transactional
  public EmployeeDocumentResponse uploadDocument(
      AuthenticatedUser user,
      UUID employeeId,
      String documentType,
      String title,
      String notes,
      MultipartFile file) {
    Employee employee = requireEmployee(user, employeeId);
    Organization organization = requireOrganization(user.organizationId());
    UserAccount uploadedByUser = requireUser(user.organizationId(), user.userId());
    DocumentUpload upload = documentUploadFrom(file);
    String normalizedDocumentType = requireDocumentText(documentType, "Document type", 80);
    String normalizedTitle =
        truncate(
            normalizeOptional(title) == null ? upload.fileName() : normalizeOptional(title), 160);
    String normalizedNotes = normalizeOptional(notes);
    if (normalizedNotes != null) {
      normalizedNotes = truncate(normalizedNotes, 1000);
    }

    EmployeeDocument document =
        new EmployeeDocument(
            UUID.randomUUID(),
            organization,
            employee,
            uploadedByUser,
            normalizedDocumentType,
            normalizedTitle,
            upload.fileName(),
            upload.contentType(),
            upload.content().length,
            normalizedNotes,
            upload.content());
    EmployeeDocument saved = employeeDocumentRepository.save(document);
    auditEventService.record(
        user,
        employee.getPrimaryBranch().getId(),
        "hr.document.uploaded",
        "EmployeeDocument",
        saved.getId(),
        employeeDocumentAuditMetadata(saved));
    return EmployeeDocumentResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public EmployeeDocumentContent getDocumentContent(AuthenticatedUser user, UUID documentId) {
    EmployeeDocument document =
        employeeDocumentRepository
            .findWithinOrganization(documentId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
    if (!canAccessEmployee(user, document.getEmployee())) {
      throw new EntityNotFoundException("Document not found");
    }
    return new EmployeeDocumentContent(
        document.getFileName(), document.getContentType(), document.getFileData());
  }

  @Transactional
  public EmployeeProfileResponse createEmployee(AuthenticatedUser user, EmployeeRequest request) {
    String employeeNumber = normalizeEmployeeNumber(request.employeeNumber());
    if (employeeRepository.existsByEmployeeNumberWithinOrganization(
        user.organizationId(), employeeNumber)) {
      throw new IllegalArgumentException("Employee number is already used");
    }

    Organization organization = requireOrganization(user.organizationId());
    Branch branch = requireBranch(user, request.primaryBranchId());
    Employee employee =
        new Employee(
            UUID.randomUUID(),
            organization,
            branch,
            employeeNumber,
            normalizeName(request.firstName()),
            normalizeName(request.lastName()),
            request.employmentType(),
            request.employmentStatus(),
            requireDate(request.joiningDate(), "Joining date"));
    applyEmployeeRequest(employee, user, request, employeeNumber);
    Employee saved = employeeRepository.save(employee);
    auditEventService.record(
        user,
        saved.getPrimaryBranch().getId(),
        "hr.employee.created",
        "Employee",
        saved.getId(),
        employeeAuditMetadata(saved));
    return EmployeeProfileResponse.from(saved);
  }

  @Transactional
  public EmployeeProfileResponse updateEmployee(
      AuthenticatedUser user, UUID employeeId, EmployeeRequest request) {
    Employee employee = requireEmployee(user, employeeId);
    String employeeNumber = normalizeEmployeeNumber(request.employeeNumber());
    if (employeeRepository.existsByEmployeeNumberWithinOrganizationExcludingEmployee(
        user.organizationId(), employeeNumber, employeeId)) {
      throw new IllegalArgumentException("Employee number is already used");
    }

    applyEmployeeRequest(employee, user, request, employeeNumber);
    auditEventService.record(
        user,
        employee.getPrimaryBranch().getId(),
        "hr.employee.updated",
        "Employee",
        employee.getId(),
        employeeAuditMetadata(employee));
    return EmployeeProfileResponse.from(employee);
  }

  @Transactional
  public EmployeeUserSyncResponse syncUsersAsEmployees(AuthenticatedUser user) {
    Organization organization = requireOrganization(user.organizationId());
    List<UserAccount> accounts =
        userAccountRepository.findWithinOrganization(user.organizationId());
    List<EmployeeListResponse> createdEmployees = new ArrayList<>();
    int skippedCount = 0;

    for (UserAccount account : accounts) {
      if (account.getStatus() != UserStatus.ACTIVE
          || employeeRepository.existsByUserId(account.getId())) {
        skippedCount++;
        continue;
      }

      Branch primaryBranch = primaryBranchForUser(user, account);
      if (primaryBranch == null) {
        skippedCount++;
        continue;
      }

      PersonName personName = personNameFromDisplayName(account.getDisplayName());
      String employeeNumber = employeeNumberForUser(user.organizationId(), account);
      Employee employee =
          new Employee(
              UUID.randomUUID(),
              organization,
              primaryBranch,
              employeeNumber,
              personName.firstName(),
              personName.lastName(),
              EmployeeEmploymentType.PERMANENT,
              EmployeeEmploymentStatus.ACTIVE,
              LocalDate.now());
      employee.updateDetails(
          account,
          primaryBranch,
          null,
          null,
          employeeNumber,
          personName.firstName(),
          null,
          personName.lastName(),
          null,
          account.getEmail(),
          null,
          null,
          null,
          null,
          EmployeeEmploymentType.PERMANENT,
          EmployeeEmploymentStatus.ACTIVE,
          LocalDate.now(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "Imported from main system user account",
          zeroMoney(),
          SalaryPaymentMethod.UNSPECIFIED,
          null,
          null,
          null,
          null,
          true);
      Employee saved = employeeRepository.save(employee);
      createdEmployees.add(EmployeeListResponse.from(saved));
      auditEventService.record(
          user,
          saved.getPrimaryBranch().getId(),
          "hr.employee.synced-from-user",
          "Employee",
          saved.getId(),
          employeeAuditMetadata(saved));
    }

    return new EmployeeUserSyncResponse(
        accounts.size(), createdEmployees.size(), skippedCount, createdEmployees);
  }

  @Transactional(readOnly = true)
  public AttendancePageResponse listAttendance(
      AuthenticatedUser user,
      String search,
      UUID branchId,
      UUID employeeId,
      AttendanceStatus status,
      LocalDate fromDate,
      LocalDate toDate,
      int page,
      int size) {
    if (branchId != null) {
      branchService.requireBranchAccess(user, branchId);
    }
    if (employeeId != null) {
      Employee employee = requireEmployee(user, employeeId);
      if (branchId != null && !employee.getPrimaryBranch().getId().equals(branchId)) {
        throw new IllegalArgumentException("Employee does not belong to the selected branch");
      }
    }
    LocalDate normalizedFrom =
        fromDate == null && toDate == null ? LocalDate.now().minusDays(30) : fromDate;
    LocalDate normalizedTo =
        toDate == null ? (normalizedFrom == null ? LocalDate.now() : normalizedFrom) : toDate;
    if (normalizedFrom != null && normalizedTo != null && normalizedTo.isBefore(normalizedFrom)) {
      throw new IllegalArgumentException("To date cannot be before from date");
    }
    String normalizedSearch = normalizeSearch(search);

    List<AttendanceRecord> filtered =
        attendanceRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(attendance -> canAccessEmployee(user, attendance.getEmployee()))
            .filter(
                attendance -> branchId == null || attendance.getBranch().getId().equals(branchId))
            .filter(
                attendance ->
                    employeeId == null || attendance.getEmployee().getId().equals(employeeId))
            .filter(attendance -> status == null || attendance.getStatus() == status)
            .filter(
                attendance ->
                    normalizedFrom == null
                        || !attendance.getAttendanceDate().isBefore(normalizedFrom))
            .filter(
                attendance ->
                    normalizedTo == null || !attendance.getAttendanceDate().isAfter(normalizedTo))
            .filter(attendance -> matchesAttendanceSearch(attendance, normalizedSearch))
            .toList();

    int normalizedPage = Math.max(page, 0);
    int normalizedSize = size <= 0 ? 20 : Math.min(size, 200);
    int fromIndex = Math.min(normalizedPage * normalizedSize, filtered.size());
    int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
    List<AttendanceResponse> items =
        filtered.subList(fromIndex, toIndex).stream().map(AttendanceResponse::from).toList();
    AttendanceSummaryResponse summary =
        new AttendanceSummaryResponse(
            filtered.size(),
            countStatus(filtered, AttendanceStatus.PRESENT),
            countStatus(filtered, AttendanceStatus.ABSENT),
            countStatus(filtered, AttendanceStatus.LATE),
            countStatus(filtered, AttendanceStatus.ON_LEAVE),
            countStatus(filtered, AttendanceStatus.HALF_DAY),
            countStatus(filtered, AttendanceStatus.HOLIDAY),
            countStatus(filtered, AttendanceStatus.OFF_DAY),
            (int) filtered.stream().filter(AttendanceRecord::isCorrected).count(),
            filtered.stream().mapToLong(AttendanceRecord::getWorkedMinutes).sum(),
            filtered.stream().mapToLong(AttendanceRecord::getOvertimeMinutes).sum());
    return new AttendancePageResponse(items, filtered.size(), summary);
  }

  @Transactional
  public AttendanceResponse createAttendance(AuthenticatedUser user, AttendanceRequest request) {
    Employee employee = requireEmployee(user, request.employeeId());
    Branch branch = requireBranch(user, request.branchId());
    validateAttendanceRequest(employee, branch, request, false);
    if (attendanceRepository
        .findByEmployeeAndDate(employee.getId(), request.attendanceDate())
        .isPresent()) {
      throw new IllegalArgumentException("Attendance already exists for this employee and date");
    }

    AttendanceRecord attendance =
        new AttendanceRecord(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            employee,
            branch,
            request.attendanceDate());
    applyAttendanceRequest(attendance, employee, branch, request, false);
    AttendanceRecord saved = attendanceRepository.save(attendance);
    auditEventService.record(
        user,
        saved.getBranch().getId(),
        "hr.attendance.created",
        "AttendanceRecord",
        saved.getId(),
        attendanceAuditMetadata(saved));
    return AttendanceResponse.from(saved);
  }

  @Transactional
  public AttendanceResponse updateAttendance(
      AuthenticatedUser user, UUID attendanceId, AttendanceRequest request) {
    AttendanceRecord attendance =
        attendanceRepository
            .findWithinOrganization(attendanceId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Attendance record not found"));
    if (!canAccessEmployee(user, attendance.getEmployee())) {
      throw new EntityNotFoundException("Attendance record not found");
    }
    Employee employee = requireEmployee(user, request.employeeId());
    Branch branch = requireBranch(user, request.branchId());
    validateAttendanceRequest(employee, branch, request, true);
    attendanceRepository
        .findByEmployeeAndDate(employee.getId(), request.attendanceDate())
        .filter(existing -> !existing.getId().equals(attendance.getId()))
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException(
                  "Attendance already exists for this employee and date");
            });

    applyAttendanceRequest(attendance, employee, branch, request, true);
    auditEventService.record(
        user,
        attendance.getBranch().getId(),
        "hr.attendance.corrected",
        "AttendanceRecord",
        attendance.getId(),
        attendanceAuditMetadata(attendance));
    return AttendanceResponse.from(attendance);
  }

  @Transactional(readOnly = true)
  public LeavePageResponse getLeavePage(AuthenticatedUser user) {
    List<LeaveTypeResponse> leaveTypes =
        leaveTypeRepository.findWithinOrganization(user.organizationId()).stream()
            .map(LeaveTypeResponse::from)
            .toList();
    List<LeaveBalanceResponse> balances =
        leaveBalanceRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(balance -> canAccessEmployee(user, balance.getEmployee()))
            .map(LeaveBalanceResponse::from)
            .toList();
    List<LeaveRequestResponse> requests =
        leaveRequestRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(request -> canAccessEmployee(user, request.getEmployee()))
            .map(LeaveRequestResponse::from)
            .toList();
    return new LeavePageResponse(leaveTypes, balances, requests);
  }

  @Transactional
  public LeaveTypeResponse createLeaveType(AuthenticatedUser user, LeaveTypeRequest request) {
    String code = normalizeCode(request.code());
    if (leaveTypeRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Leave type code is already used");
    }
    LeaveType leaveType =
        new LeaveType(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            normalizeName(request.name()),
            code);
    leaveType.updateDetails(
        normalizeName(request.name()),
        code,
        normalizeOptional(request.description()),
        booleanOrTrue(request.requiresBalance()),
        request.defaultDays() == null ? 0 : request.defaultDays(),
        booleanOrTrue(request.active()));
    LeaveType saved = leaveTypeRepository.save(leaveType);
    auditEventService.record(
        user,
        null,
        "hr.leave-type.created",
        "LeaveType",
        saved.getId(),
        Map.of("name", saved.getName(), "code", saved.getCode()));
    return LeaveTypeResponse.from(saved);
  }

  @Transactional
  public LeaveTypeResponse updateLeaveType(
      AuthenticatedUser user, UUID leaveTypeId, LeaveTypeRequest request) {
    LeaveType leaveType =
        leaveTypeRepository
            .findWithinOrganization(leaveTypeId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
    String code = normalizeCode(request.code());
    if (leaveTypeRepository.existsByCodeWithinOrganizationExcludingLeaveType(
        user.organizationId(), code, leaveTypeId)) {
      throw new IllegalArgumentException("Leave type code is already used");
    }
    leaveType.updateDetails(
        normalizeName(request.name()),
        code,
        normalizeOptional(request.description()),
        booleanOrTrue(request.requiresBalance()),
        request.defaultDays() == null ? 0 : request.defaultDays(),
        booleanOrTrue(request.active()));
    auditEventService.record(
        user,
        null,
        "hr.leave-type.updated",
        "LeaveType",
        leaveType.getId(),
        Map.of("name", leaveType.getName(), "code", leaveType.getCode()));
    return LeaveTypeResponse.from(leaveType);
  }

  @Transactional
  public LeaveRequestResponse createLeaveRequest(
      AuthenticatedUser user, LeaveRequestInput request) {
    Employee employee = requireEmployee(user, request.employeeId());
    LeaveType leaveType = requireLeaveType(user.organizationId(), request.leaveTypeId());
    validateLeaveRequest(user, employee, leaveType, request, null);
    BigDecimal requestedDays = calculateRequestedDays(request.startDate(), request.endDate());
    LeaveRequest leaveRequest =
        new LeaveRequest(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            employee,
            employee.getPrimaryBranch(),
            leaveType,
            request.startDate(),
            request.endDate(),
            requestedDays);
    applyLeaveRequest(leaveRequest, user, employee, leaveType, request, requestedDays);
    LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
    syncLeaveBalance(saved, leaveType);
    if (saved.getStatus() == LeaveRequestStatus.APPROVED) {
      mirrorApprovedLeaveToAttendance(saved);
    }
    auditEventService.record(
        user,
        saved.getBranch().getId(),
        "hr.leave-request.created",
        "LeaveRequest",
        saved.getId(),
        leaveRequestAuditMetadata(saved));
    return LeaveRequestResponse.from(saved);
  }

  @Transactional
  public LeaveRequestResponse updateLeaveRequest(
      AuthenticatedUser user, UUID requestId, LeaveRequestInput request) {
    LeaveRequest leaveRequest =
        leaveRequestRepository
            .findWithinOrganization(requestId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Leave request not found"));
    if (!canAccessEmployee(user, leaveRequest.getEmployee())) {
      throw new EntityNotFoundException("Leave request not found");
    }
    Employee employee = requireEmployee(user, request.employeeId());
    LeaveType leaveType = requireLeaveType(user.organizationId(), request.leaveTypeId());
    validateLeaveRequest(user, employee, leaveType, request, leaveRequest.getId());
    BigDecimal requestedDays = calculateRequestedDays(request.startDate(), request.endDate());
    applyLeaveRequest(leaveRequest, user, employee, leaveType, request, requestedDays);
    syncLeaveBalance(leaveRequest, leaveType);
    if (leaveRequest.getStatus() == LeaveRequestStatus.APPROVED) {
      mirrorApprovedLeaveToAttendance(leaveRequest);
    }
    auditEventService.record(
        user,
        leaveRequest.getBranch().getId(),
        "hr.leave-request.updated",
        "LeaveRequest",
        leaveRequest.getId(),
        leaveRequestAuditMetadata(leaveRequest));
    return LeaveRequestResponse.from(leaveRequest);
  }

  @Transactional(readOnly = true)
  public EmployeePerformancePageResponse getPerformancePage(
      AuthenticatedUser user,
      LocalDate fromDate,
      LocalDate toDate,
      UUID branchId,
      UUID employeeId) {
    PerformancePeriod period = normalizePerformancePeriod(fromDate, toDate);
    if (branchId != null) {
      branchService.requireBranchAccess(user, branchId);
    }
    if (employeeId != null) {
      Employee employee = requireEmployee(user, employeeId);
      if (branchId != null && !employee.getPrimaryBranch().getId().equals(branchId)) {
        throw new IllegalArgumentException("Employee does not belong to the selected branch");
      }
    }

    Organization organization = requireOrganization(user.organizationId());
    List<Employee> employees =
        employeeRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(employee -> canAccessEmployee(user, employee))
            .filter(
                employee ->
                    branchId == null || employee.getPrimaryBranch().getId().equals(branchId))
            .filter(employee -> employeeId == null || employee.getId().equals(employeeId))
            .toList();
    List<EmployeePerformanceEntry> entries =
        employeePerformanceEntryRepository
            .findWithinPeriod(user.organizationId(), period.fromDate(), period.toDate())
            .stream()
            .filter(entry -> canAccessEmployee(user, entry.getEmployee()))
            .filter(entry -> branchId == null || entry.getBranch().getId().equals(branchId))
            .filter(entry -> employeeId == null || entry.getEmployee().getId().equals(employeeId))
            .toList();
    List<Sale> sales =
        saleRepository
            .findAttributedSalesWithinPeriod(
                user.organizationId(),
                period.fromInstant(organization),
                period.toExclusiveInstant(organization))
            .stream()
            .filter(sale -> sale.getSoldByEmployee() != null)
            .filter(sale -> canAccessEmployee(user, sale.getSoldByEmployee()))
            .filter(sale -> branchId == null || sale.getBranch().getId().equals(branchId))
            .filter(
                sale -> employeeId == null || sale.getSoldByEmployee().getId().equals(employeeId))
            .toList();
    Map<UUID, PayrollAccumulator> accumulators = payrollAccumulators(entries, sales, organization);
    PayrollSalesBonusRuleResponse salesBonusRule = salesBonusRuleResponse(user.organizationId());
    List<EmployeePerformanceSummaryResponse> summaries =
        employees.stream()
            .map(
                employee ->
                    performanceSummary(
                        employee, accumulators.get(employee.getId()), salesBonusRule))
            .toList();
    return new EmployeePerformancePageResponse(
        period.fromDate(),
        period.toDate(),
        salesBonusRule,
        summaries,
        entries.stream().map(EmployeePerformanceEntryResponse::from).toList());
  }

  @Transactional
  public EmployeePerformanceEntryResponse createPerformanceEntry(
      AuthenticatedUser user, EmployeePerformanceEntryRequest request) {
    Employee employee = requireEmployee(user, request.employeeId());
    validatePerformanceEntry(request);
    BigDecimal amount =
        request.entryType() == EmployeePerformanceEntryType.PERFORMANCE_REVIEW
            ? null
            : money(request.amount());
    Integer score =
        request.entryType() == EmployeePerformanceEntryType.PERFORMANCE_REVIEW
            ? request.score()
            : null;
    EmployeePerformanceEntry entry =
        new EmployeePerformanceEntry(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            employee,
            employee.getPrimaryBranch(),
            requireUser(user.organizationId(), user.userId()),
            request.entryType(),
            request.entryDate(),
            performanceEntryTitle(request),
            amount,
            score,
            normalizeOptional(request.notes()));
    EmployeePerformanceEntry saved = employeePerformanceEntryRepository.save(entry);
    auditEventService.record(
        user,
        employee.getPrimaryBranch().getId(),
        "hr.performance-entry.created",
        "EmployeePerformanceEntry",
        saved.getId(),
        performanceEntryAuditMetadata(saved));
    return EmployeePerformanceEntryResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PayrollPageResponse getPayrollPage(AuthenticatedUser user) {
    List<PayrollComponentResponse> components =
        payrollComponentRepository.findWithinOrganization(user.organizationId()).stream()
            .map(PayrollComponentResponse::from)
            .toList();
    List<PayrollPeriodResponse> periods =
        payrollPeriodRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(period -> branchVisible(user, period.getBranch()))
            .map(PayrollPeriodResponse::from)
            .toList();
    List<PayrollEmployee> payrollEmployees =
        payrollEmployeeRepository.findWithinOrganization(user.organizationId());
    List<PayrollRunResponse> runs =
        payrollRunRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(run -> branchVisible(user, run.getBranch()))
            .map(
                run ->
                    PayrollRunResponse.from(
                        run,
                        payrollEmployees.stream()
                            .filter(
                                payrollEmployee ->
                                    payrollEmployee.getPayrollRun().getId().equals(run.getId()))
                            .filter(
                                payrollEmployee ->
                                    canAccessEmployee(user, payrollEmployee.getEmployee()))
                            .map(PayrollEmployeeResponse::from)
                            .toList()))
            .toList();
    return new PayrollPageResponse(
        components, periods, runs, salesBonusRuleResponse(user.organizationId()));
  }

  @Transactional
  public PayrollSalesBonusRuleResponse updateSalesBonusRule(
      AuthenticatedUser user, PayrollSalesBonusRuleRequest request) {
    PayrollSalesBonusRule rule =
        payrollSalesBonusRuleRepository
            .findByOrganizationId(user.organizationId())
            .orElseGet(
                () ->
                    new PayrollSalesBonusRule(
                        UUID.randomUUID(), requireOrganization(user.organizationId())));
    rule.updateDetails(
        money(request.dailySalesTarget()),
        money(request.bonusPerTargetDay()),
        booleanOrTrue(request.active()));
    PayrollSalesBonusRule saved = payrollSalesBonusRuleRepository.save(rule);
    auditEventService.record(
        user,
        null,
        "hr.payroll-sales-bonus-rule.updated",
        "PayrollSalesBonusRule",
        saved.getId(),
        Map.of(
            "dailySalesTarget",
            saved.getDailySalesTarget(),
            "bonusPerTargetDay",
            saved.getBonusPerTargetDay(),
            "active",
            saved.isActive()));
    return PayrollSalesBonusRuleResponse.from(saved);
  }

  @Transactional
  public PayrollComponentResponse createPayrollComponent(
      AuthenticatedUser user, PayrollComponentRequest request) {
    String code = normalizeCode(request.code());
    if (payrollComponentRepository.existsByCodeWithinOrganization(user.organizationId(), code)) {
      throw new IllegalArgumentException("Payroll component code is already used");
    }
    PayrollComponent component =
        new PayrollComponent(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            normalizeName(request.name()),
            code,
            request.componentType(),
            request.defaultAmount().setScale(2, RoundingMode.HALF_UP));
    component.updateDetails(
        normalizeName(request.name()),
        code,
        request.componentType(),
        booleanOrTrue(request.taxable()),
        request.defaultAmount().setScale(2, RoundingMode.HALF_UP),
        booleanOrTrue(request.active()));
    PayrollComponent saved = payrollComponentRepository.save(component);
    auditEventService.record(
        user,
        null,
        "hr.payroll-component.created",
        "PayrollComponent",
        saved.getId(),
        Map.of(
            "name",
            saved.getName(),
            "code",
            saved.getCode(),
            "type",
            saved.getComponentType().name()));
    return PayrollComponentResponse.from(saved);
  }

  @Transactional
  public PayrollPeriodResponse createPayrollPeriod(
      AuthenticatedUser user, PayrollPeriodRequest request) {
    Branch branch = request.branchId() == null ? null : requireBranch(user, request.branchId());
    if (request.periodEnd().isBefore(request.periodStart())) {
      throw new IllegalArgumentException("Payroll period end date cannot be before start date");
    }
    PayrollPeriod period =
        new PayrollPeriod(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            branch,
            normalizeName(request.name()),
            request.periodStart(),
            request.periodEnd());
    period.updateDetails(
        branch,
        normalizeName(request.name()),
        request.periodStart(),
        request.periodEnd(),
        request.paymentDate(),
        request.status());
    PayrollPeriod saved = payrollPeriodRepository.save(period);
    auditEventService.record(
        user,
        saved.getBranch() == null ? null : saved.getBranch().getId(),
        "hr.payroll-period.created",
        "PayrollPeriod",
        saved.getId(),
        Map.of("name", saved.getName(), "status", saved.getStatus().name()));
    return PayrollPeriodResponse.from(saved);
  }

  @Transactional
  public PayrollRunResponse createPayrollRun(AuthenticatedUser user, PayrollRunRequest request) {
    PayrollPeriod period =
        payrollPeriodRepository
            .findWithinOrganization(request.payrollPeriodId(), user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Payroll period not found"));
    if (!branchVisible(user, period.getBranch())) {
      throw new EntityNotFoundException("Payroll period not found");
    }
    PayrollRun run =
        new PayrollRun(
            UUID.randomUUID(),
            requireOrganization(user.organizationId()),
            period,
            period.getBranch(),
            normalizeName(request.name()));
    PayrollRunStatus status = request.status();
    run.updateDetails(
        normalizeName(request.name()),
        status,
        status == PayrollRunStatus.PROCESSED || status == PayrollRunStatus.LOCKED
            ? Instant.now()
            : null);
    PayrollRun saved = payrollRunRepository.save(run);
    recalculatePayrollRun(user, saved);
    auditEventService.record(
        user,
        saved.getBranch() == null ? null : saved.getBranch().getId(),
        "hr.payroll-run.created",
        "PayrollRun",
        saved.getId(),
        Map.of("name", saved.getName(), "status", saved.getStatus().name()));
    return PayrollRunResponse.from(
        saved,
        payrollEmployeeRepository.findByRunId(saved.getId()).stream()
            .map(PayrollEmployeeResponse::from)
            .toList());
  }

  @Transactional
  public PayrollRunResponse recalculatePayrollRun(AuthenticatedUser user, UUID runId) {
    PayrollRun payrollRun = requirePayrollRun(user, runId);
    validatePayrollRunEditable(payrollRun);
    recalculatePayrollRun(user, payrollRun);
    auditEventService.record(
        user,
        payrollRun.getBranch() == null ? null : payrollRun.getBranch().getId(),
        "hr.payroll-run.recalculated",
        "PayrollRun",
        payrollRun.getId(),
        Map.of("name", payrollRun.getName(), "status", payrollRun.getStatus().name()));
    return PayrollRunResponse.from(
        payrollRun,
        payrollEmployeeRepository.findByRunId(payrollRun.getId()).stream()
            .map(PayrollEmployeeResponse::from)
            .toList());
  }

  @Transactional
  public PayrollEmployeeResponse updatePayrollEmployeeAdjustment(
      AuthenticatedUser user,
      UUID runId,
      UUID payrollEmployeeId,
      PayrollEmployeeAdjustmentRequest request) {
    PayrollRun payrollRun = requirePayrollRun(user, runId);
    validatePayrollRunEditable(payrollRun);
    PayrollEmployee payrollEmployee =
        payrollEmployeeRepository
            .findWithinRun(payrollEmployeeId, runId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Payroll employee not found"));
    if (!canAccessEmployee(user, payrollEmployee.getEmployee())) {
      throw new EntityNotFoundException("Payroll employee not found");
    }

    BigDecimal bonusAmount = money(request.bonusAmount());
    BigDecimal lossAmount = money(request.lossAmount());
    BigDecimal automaticBonusAmount =
        payrollEmployee.getAutomaticBonusAmount().min(bonusAmount).max(zeroMoney());
    BigDecimal manualBonusAmount = bonusAmount.subtract(automaticBonusAmount).max(zeroMoney());
    BigDecimal grossPay = payrollEmployee.getBasicSalary().add(bonusAmount);
    BigDecimal netPay = grossPay.subtract(lossAmount).max(zeroMoney());
    SalaryPaymentMethod paymentMethod =
        SalaryPaymentMethod.valueOf(payrollEmployee.getPaymentMethod());
    payrollEmployee.updateTotals(
        money(grossPay),
        payrollEmployee.getBasicSalary(),
        bonusAmount,
        automaticBonusAmount,
        manualBonusAmount,
        lossAmount,
        payrollEmployee.getSalesAmount(),
        payrollEmployee.getQualifyingSalesDays(),
        payrollEmployee.getSalesBonusTarget(),
        payrollEmployee.getSalesBonusPerDay(),
        payrollEmployee.getPerformanceScore(),
        lossAmount,
        money(netPay),
        paymentMethod,
        payrollEmployee.getPaymentDestination(),
        normalizeOptional(request.notes()));
    PayrollEmployee saved = payrollEmployeeRepository.save(payrollEmployee);
    auditEventService.record(
        user,
        saved.getEmployee().getPrimaryBranch().getId(),
        "hr.payroll-employee.adjusted",
        "PayrollEmployee",
        saved.getId(),
        Map.of(
            "employeeId",
            saved.getEmployee().getId(),
            "payrollRunId",
            payrollRun.getId(),
            "bonusAmount",
            saved.getBonusAmount(),
            "lossAmount",
            saved.getLossAmount()));
    return PayrollEmployeeResponse.from(saved);
  }

  private void updateDepartment(
      Department department, AuthenticatedUser user, DepartmentRequest request) {
    Branch branch = request.branchId() == null ? null : requireBranch(user, request.branchId());
    UserAccount managerUser =
        request.managerUserId() == null
            ? null
            : requireUser(user.organizationId(), request.managerUserId());
    if (managerUser != null && branch != null) {
      branchService.requireBranchAccess(user, branch.getId());
    }
    department.updateDetails(
        branch,
        normalizeName(request.name()),
        normalizeCode(request.code()),
        normalizeOptional(request.description()),
        managerUser,
        booleanOrTrue(request.active()));
  }

  private void applyEmployeeRequest(
      Employee employee, AuthenticatedUser user, EmployeeRequest request, String employeeNumber) {
    Branch primaryBranch = requireBranch(user, request.primaryBranchId());
    Department department =
        request.departmentId() == null ? null : requireDepartment(user, request.departmentId());
    JobTitle jobTitle =
        request.jobTitleId() == null ? null : requireJobTitle(user, request.jobTitleId());
    if (department != null
        && department.getBranch() != null
        && !department.getBranch().getId().equals(primaryBranch.getId())) {
      throw new IllegalArgumentException("Department must belong to the selected primary branch");
    }
    if (department != null && department.getBranch() != null) {
      branchService.requireBranchAccess(user, department.getBranch().getId());
    }
    if (department != null
        && jobTitle != null
        && !jobTitle.getDepartment().getId().equals(department.getId())) {
      throw new IllegalArgumentException("Job title must belong to the selected department");
    }
    UserAccount linkedUser =
        request.userId() == null ? null : requireUser(user.organizationId(), request.userId());
    if (linkedUser != null
        && employeeRepository.existsByUserId(linkedUser.getId())
        && (employee.getUser() == null || !linkedUser.getId().equals(employee.getUser().getId()))) {
      throw new IllegalArgumentException("Selected user is already linked to another employee");
    }
    Employee manager =
        request.managerEmployeeId() == null
            ? null
            : requireEmployee(user, request.managerEmployeeId());
    if (manager != null && manager.getId().equals(employee.getId())) {
      throw new IllegalArgumentException("Employee cannot be their own manager");
    }
    validateEmploymentDates(request);
    SalaryPaymentMethod salaryPaymentMethod =
        request.salaryPaymentMethod() == null
            ? SalaryPaymentMethod.UNSPECIFIED
            : request.salaryPaymentMethod();
    validateSalaryPaymentDetails(salaryPaymentMethod, request);
    employee.updateDetails(
        linkedUser,
        primaryBranch,
        department,
        jobTitle,
        employeeNumber,
        normalizeName(request.firstName()),
        normalizeOptional(request.middleName()),
        normalizeName(request.lastName()),
        normalizeOptional(request.preferredName()),
        normalizeOptional(request.email()),
        normalizeOptional(request.phone()),
        normalizeOptional(request.gender()),
        request.dateOfBirth(),
        normalizeOptional(request.nationalIdNumber()),
        request.employmentType(),
        request.employmentStatus(),
        requireDate(request.joiningDate(), "Joining date"),
        request.probationEndDate(),
        request.contractStartDate(),
        request.contractEndDate(),
        manager,
        normalizeOptional(request.workLocation()),
        normalizeOptional(request.emergencyContactName()),
        normalizeOptional(request.emergencyContactPhone()),
        normalizeOptional(request.address()),
        normalizeOptional(request.notes()),
        money(request.basicSalary()),
        salaryPaymentMethod,
        normalizeOptional(request.bankName()),
        normalizeOptional(request.bankAccountNumber()),
        normalizeOptional(request.bankAccountName()),
        normalizeOptional(request.mpesaNumber()),
        booleanOrTrue(request.active()));
  }

  private void applyAttendanceRequest(
      AttendanceRecord attendance,
      Employee employee,
      Branch branch,
      AttendanceRequest request,
      boolean corrected) {
    attendance.updateDetails(
        employee,
        branch,
        request.attendanceDate(),
        request.clockIn(),
        request.clockOut(),
        request.status(),
        8 * 60,
        normalizeOptional(request.notes()),
        normalizeOptional(request.correctionReason()),
        corrected);
  }

  private void applyLeaveRequest(
      LeaveRequest leaveRequest,
      AuthenticatedUser user,
      Employee employee,
      LeaveType leaveType,
      LeaveRequestInput request,
      BigDecimal requestedDays) {
    UserAccount approver =
        request.approverUserId() == null
            ? null
            : requireUser(user.organizationId(), request.approverUserId());
    Instant submittedAt =
        request.status() == LeaveRequestStatus.SUBMITTED
                || request.status() == LeaveRequestStatus.PENDING_APPROVAL
            ? leaveRequest.getSubmittedAt() == null ? Instant.now() : leaveRequest.getSubmittedAt()
            : null;
    Instant decidedAt =
        request.status() == LeaveRequestStatus.APPROVED
                || request.status() == LeaveRequestStatus.REJECTED
            ? Instant.now()
            : null;
    LeaveRequestStatus normalizedStatus = normalizeLeaveStatus(request.status());
    leaveRequest.updateDetails(
        employee,
        employee.getPrimaryBranch(),
        leaveType,
        approver,
        request.startDate(),
        request.endDate(),
        requestedDays,
        normalizeOptional(request.reason()),
        normalizeOptional(request.approverComments()),
        normalizedStatus,
        submittedAt,
        decidedAt);
  }

  private void validateAttendanceRequest(
      Employee employee, Branch branch, AttendanceRequest request, boolean correction) {
    if (!employee.getPrimaryBranch().getId().equals(branch.getId())) {
      throw new IllegalArgumentException(
          "Attendance branch must match the employee primary branch");
    }
    if (!employee.isActive()) {
      throw new IllegalArgumentException("Attendance can only be recorded for active employees");
    }
    if (employee.getEmploymentStatus() == EmployeeEmploymentStatus.TERMINATED
        || employee.getEmploymentStatus() == EmployeeEmploymentStatus.RETIRED
        || employee.getEmploymentStatus() == EmployeeEmploymentStatus.RESIGNED) {
      throw new IllegalArgumentException(
          "Attendance cannot be recorded for inactive employment status");
    }
    if (request.clockIn() != null
        && request.clockOut() != null
        && request.clockOut().isBefore(request.clockIn())) {
      throw new IllegalArgumentException("Clock out cannot be earlier than clock in");
    }
    boolean requiresClockIn =
        request.status() == AttendanceStatus.PRESENT
            || request.status() == AttendanceStatus.LATE
            || request.status() == AttendanceStatus.HALF_DAY;
    if (requiresClockIn && request.clockIn() == null) {
      throw new IllegalArgumentException(
          "Clock in is required for present, late, and half-day attendance");
    }
    if (request.clockOut() != null && request.clockIn() == null) {
      throw new IllegalArgumentException("Clock in is required when clock out is provided");
    }
    if (correction && normalizeOptional(request.correctionReason()) == null) {
      throw new IllegalArgumentException("Correction reason is required when updating attendance");
    }
  }

  private void validateLeaveRequest(
      AuthenticatedUser user,
      Employee employee,
      LeaveType leaveType,
      LeaveRequestInput request,
      UUID requestId) {
    if (!employee.isActive()) {
      throw new IllegalArgumentException("Leave can only be requested for active employees");
    }
    if (request.endDate().isBefore(request.startDate())) {
      throw new IllegalArgumentException("Leave end date cannot be before start date");
    }
    if (employee.getEmploymentStatus() == EmployeeEmploymentStatus.TERMINATED
        || employee.getEmploymentStatus() == EmployeeEmploymentStatus.RESIGNED
        || employee.getEmploymentStatus() == EmployeeEmploymentStatus.RETIRED) {
      throw new IllegalArgumentException(
          "Leave cannot be requested for inactive employment status");
    }
    if (leaveRequestRepository.hasOverlappingRequest(
        employee.getId(), requestId, request.startDate(), request.endDate())) {
      throw new IllegalArgumentException("Employee already has an overlapping leave request");
    }
    BigDecimal requestedDays = calculateRequestedDays(request.startDate(), request.endDate());
    if (leaveType.isRequiresBalance()) {
      LeaveBalance balance = requireOrCreateLeaveBalance(user, employee, leaveType);
      BigDecimal available = balance.getBalanceDays().subtract(balance.getUsedDays());
      if (available.compareTo(requestedDays) < 0) {
        throw new IllegalArgumentException("Requested leave exceeds available balance");
      }
    }
    if ((request.status() == LeaveRequestStatus.APPROVED
            || request.status() == LeaveRequestStatus.REJECTED)
        && request.approverUserId() == null) {
      throw new IllegalArgumentException("Approver is required when deciding a leave request");
    }
  }

  private Employee requireEmployee(AuthenticatedUser user, UUID employeeId) {
    Employee employee =
        employeeRepository
            .findWithinOrganization(employeeId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    if (!canAccessEmployee(user, employee)) {
      throw new EntityNotFoundException("Employee not found");
    }
    return employee;
  }

  private PayrollRun requirePayrollRun(AuthenticatedUser user, UUID runId) {
    PayrollRun payrollRun =
        payrollRunRepository
            .findWithinOrganization(runId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Payroll run not found"));
    if (!branchVisible(user, payrollRun.getBranch())) {
      throw new EntityNotFoundException("Payroll run not found");
    }
    return payrollRun;
  }

  private static void validatePayrollRunEditable(PayrollRun payrollRun) {
    if (payrollRun.getStatus() == PayrollRunStatus.PROCESSED
        || payrollRun.getStatus() == PayrollRunStatus.LOCKED) {
      throw new IllegalArgumentException("Processed or locked payroll runs cannot be changed");
    }
  }

  private LeaveType requireLeaveType(UUID organizationId, UUID leaveTypeId) {
    return leaveTypeRepository
        .findWithinOrganization(leaveTypeId, organizationId)
        .orElseThrow(() -> new EntityNotFoundException("Leave type not found"));
  }

  private LeaveBalance requireOrCreateLeaveBalance(
      AuthenticatedUser user, Employee employee, LeaveType leaveType) {
    return leaveBalanceRepository
        .findByEmployeeAndLeaveType(employee.getId(), leaveType.getId())
        .orElseGet(
            () ->
                leaveBalanceRepository.save(
                    new LeaveBalance(
                        UUID.randomUUID(),
                        requireOrganization(user.organizationId()),
                        employee,
                        leaveType,
                        BigDecimal.valueOf(leaveType.getDefaultDays())
                            .setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))));
  }

  private Department requireDepartment(AuthenticatedUser user, UUID departmentId) {
    Department department =
        departmentRepository
            .findWithinOrganization(departmentId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found"));
    if (!branchVisible(user, department.getBranch())) {
      throw new EntityNotFoundException("Department not found");
    }
    return department;
  }

  private JobTitle requireJobTitle(AuthenticatedUser user, UUID jobTitleId) {
    JobTitle jobTitle =
        jobTitleRepository
            .findWithinOrganization(jobTitleId, user.organizationId())
            .orElseThrow(() -> new EntityNotFoundException("Job title not found"));
    if (!branchVisible(user, jobTitle.getDepartment().getBranch())) {
      throw new EntityNotFoundException("Job title not found");
    }
    return jobTitle;
  }

  private Organization requireOrganization(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
  }

  private Branch requireBranch(AuthenticatedUser user, UUID branchId) {
    branchService.requireBranchAccess(user, branchId);
    return branchRepository
        .findWithinOrganization(branchId, user.organizationId())
        .orElseThrow(() -> new EntityNotFoundException("Branch not found"));
  }

  private UserAccount requireUser(UUID organizationId, UUID userId) {
    return userAccountRepository
        .findWithinOrganization(userId, organizationId)
        .orElseThrow(() -> new EntityNotFoundException("User not found"));
  }

  private Branch primaryBranchForUser(AuthenticatedUser actor, UserAccount account) {
    return userBranchAssignmentRepository
        .findActiveBranchesForUser(account.getId(), actor.organizationId())
        .stream()
        .filter(branch -> branchVisible(actor, branch))
        .findFirst()
        .orElse(null);
  }

  private static boolean canAccessEmployee(AuthenticatedUser user, Employee employee) {
    return user.branchIds().isEmpty()
        || user.branchIds().contains(employee.getPrimaryBranch().getId());
  }

  private static boolean branchVisible(AuthenticatedUser user, Branch branch) {
    return branch == null
        || user.branchIds().isEmpty()
        || user.branchIds().contains(branch.getId());
  }

  private static boolean matchesEmployeeSearch(Employee employee, String search) {
    if (search == null) {
      return true;
    }
    return String.join(
            " ",
            employee.getEmployeeNumber(),
            employee.getFirstName(),
            optional(employee.getMiddleName()),
            employee.getLastName(),
            optional(employee.getPreferredName()),
            optional(employee.getEmail()),
            optional(employee.getPhone()),
            optional(employee.getDepartment() == null ? null : employee.getDepartment().getName()),
            optional(employee.getJobTitle() == null ? null : employee.getJobTitle().getTitle()))
        .toLowerCase(Locale.ROOT)
        .contains(search);
  }

  private static boolean matchesAttendanceSearch(AttendanceRecord attendance, String search) {
    if (search == null) {
      return true;
    }
    return String.join(
            " ",
            attendance.getEmployee().getEmployeeNumber(),
            EmployeeProfileResponse.fullName(attendance.getEmployee()),
            attendance.getBranch().getName(),
            optional(
                attendance.getEmployee().getDepartment() == null
                    ? null
                    : attendance.getEmployee().getDepartment().getName()),
            optional(
                attendance.getEmployee().getJobTitle() == null
                    ? null
                    : attendance.getEmployee().getJobTitle().getTitle()),
            attendance.getStatus().name())
        .toLowerCase(Locale.ROOT)
        .contains(search);
  }

  private static BigDecimal calculateRequestedDays(LocalDate startDate, LocalDate endDate) {
    return BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1L)
        .setScale(2, RoundingMode.HALF_UP);
  }

  private void syncLeaveBalance(LeaveRequest leaveRequest, LeaveType leaveType) {
    if (!leaveType.isRequiresBalance()) {
      return;
    }
    LeaveBalance balance =
        leaveBalanceRepository
            .findByEmployeeAndLeaveType(leaveRequest.getEmployee().getId(), leaveType.getId())
            .orElseThrow(() -> new EntityNotFoundException("Leave balance not found"));
    BigDecimal usedDays =
        leaveRequestRepository
            .findWithinOrganization(leaveRequest.getEmployee().getOrganization().getId())
            .stream()
            .filter(
                request -> request.getEmployee().getId().equals(leaveRequest.getEmployee().getId()))
            .filter(request -> request.getLeaveType().getId().equals(leaveType.getId()))
            .filter(request -> request.getStatus() == LeaveRequestStatus.APPROVED)
            .map(LeaveRequest::getRequestedDays)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    balance.adjustUsage(usedDays);
  }

  private void mirrorApprovedLeaveToAttendance(LeaveRequest leaveRequest) {
    LocalDate date = leaveRequest.getStartDate();
    while (!date.isAfter(leaveRequest.getEndDate())) {
      LocalDate attendanceDate = date;
      AttendanceRecord attendance =
          attendanceRepository
              .findByEmployeeAndDate(leaveRequest.getEmployee().getId(), attendanceDate)
              .orElseGet(
                  () ->
                      new AttendanceRecord(
                          UUID.randomUUID(),
                          leaveRequest.getEmployee().getOrganization(),
                          leaveRequest.getEmployee(),
                          leaveRequest.getBranch(),
                          attendanceDate));
      attendance.updateDetails(
          leaveRequest.getEmployee(),
          leaveRequest.getBranch(),
          attendanceDate,
          null,
          null,
          AttendanceStatus.ON_LEAVE,
          8 * 60,
          "Auto-synced from approved leave",
          "Approved leave request",
          attendance.getId() != null);
      attendanceRepository.save(attendance);
      date = date.plusDays(1);
    }
  }

  private EmployeePerformanceSummaryResponse performanceSummary(
      Employee employee,
      PayrollAccumulator accumulator,
      PayrollSalesBonusRuleResponse salesBonusRule) {
    PayrollAccumulator facts = accumulator == null ? new PayrollAccumulator() : accumulator;
    BigDecimal basicSalary = money(employee.getBasicSalary());
    BigDecimal automaticBonusAmount = facts.automaticBonusAmount(salesBonusRule);
    BigDecimal manualBonusAmount = facts.manualBonusAmount();
    BigDecimal bonusAmount = manualBonusAmount.add(automaticBonusAmount);
    BigDecimal projectedNetPay =
        basicSalary.add(bonusAmount).subtract(facts.lossAmount()).max(zeroMoney());
    return new EmployeePerformanceSummaryResponse(
        employee.getId(),
        EmployeeProfileResponse.fullName(employee),
        employee.getEmployeeNumber(),
        employee.getPrimaryBranch().getId(),
        employee.getPrimaryBranch().getName(),
        employee.getDepartment() == null ? null : employee.getDepartment().getId(),
        employee.getDepartment() == null ? null : employee.getDepartment().getName(),
        employee.getJobTitle() == null ? null : employee.getJobTitle().getTitle(),
        basicSalary,
        employee.getSalaryPaymentMethod(),
        paymentDestination(employee),
        facts.actualSalesAmount(),
        facts.manualSalesAmount(),
        facts.totalSalesAmount(),
        money(bonusAmount),
        automaticBonusAmount,
        manualBonusAmount,
        facts.lossAmount(),
        money(projectedNetPay),
        facts.qualifyingSalesDays(salesBonusRule),
        salesBonusRule.dailySalesTarget(),
        salesBonusRule.bonusPerTargetDay(),
        facts.averagePerformanceScore(),
        facts.entryCount(),
        facts.salesDays());
  }

  private Map<UUID, PayrollAccumulator> payrollAccumulators(
      List<EmployeePerformanceEntry> entries, List<Sale> sales, Organization organization) {
    Map<UUID, PayrollAccumulator> accumulators = new LinkedHashMap<>();
    for (EmployeePerformanceEntry entry : entries) {
      PayrollAccumulator accumulator =
          accumulators.computeIfAbsent(
              entry.getEmployee().getId(), ignored -> new PayrollAccumulator());
      accumulator.recordEntry(entry);
    }
    for (Sale sale : sales) {
      if (sale.getSoldByEmployee() == null) {
        continue;
      }
      PayrollAccumulator accumulator =
          accumulators.computeIfAbsent(
              sale.getSoldByEmployee().getId(), ignored -> new PayrollAccumulator());
      accumulator.addActualSales(sale, organization);
    }
    return accumulators;
  }

  private static PerformancePeriod normalizePerformancePeriod(
      LocalDate fromDate, LocalDate toDate) {
    LocalDate today = LocalDate.now();
    LocalDate normalizedFrom =
        fromDate == null && toDate == null ? today.withDayOfMonth(1) : fromDate;
    LocalDate normalizedTo =
        toDate == null ? (normalizedFrom == null ? today : normalizedFrom) : toDate;
    if (normalizedFrom == null) {
      normalizedFrom = normalizedTo.withDayOfMonth(1);
    }
    if (normalizedTo.isBefore(normalizedFrom)) {
      throw new IllegalArgumentException("To date cannot be before from date");
    }
    return new PerformancePeriod(normalizedFrom, normalizedTo);
  }

  private static void validatePerformanceEntry(EmployeePerformanceEntryRequest request) {
    if (request.entryType() == EmployeePerformanceEntryType.PERFORMANCE_REVIEW) {
      if (request.score() == null) {
        throw new IllegalArgumentException("Performance review score is required");
      }
      return;
    }
    if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be greater than zero");
    }
  }

  private static String performanceEntryTitle(EmployeePerformanceEntryRequest request) {
    String title = normalizeOptional(request.title());
    if (title != null) {
      return title;
    }
    return switch (request.entryType()) {
      case BONUS -> "Bonus";
      case LOSS -> "Loss";
      case MANUAL_SALE -> "Manual sale";
      case PERFORMANCE_REVIEW -> "Performance review";
    };
  }

  private static void validateSalaryPaymentDetails(
      SalaryPaymentMethod salaryPaymentMethod, EmployeeRequest request) {
    if (salaryPaymentMethod == SalaryPaymentMethod.BANK
        && (normalizeOptional(request.bankName()) == null
            || normalizeOptional(request.bankAccountNumber()) == null)) {
      throw new IllegalArgumentException(
          "Bank name and account number are required for bank salary payments");
    }
    if (salaryPaymentMethod == SalaryPaymentMethod.MPESA
        && normalizeOptional(request.mpesaNumber()) == null) {
      throw new IllegalArgumentException("M-Pesa number is required for M-Pesa salary payments");
    }
  }

  private static String paymentDestination(Employee employee) {
    if (employee.getSalaryPaymentMethod() == SalaryPaymentMethod.BANK) {
      return compactDestination(
          employee.getBankAccountName(), employee.getBankName(), employee.getBankAccountNumber());
    }
    if (employee.getSalaryPaymentMethod() == SalaryPaymentMethod.MPESA) {
      return optionalOrDefault(employee.getMpesaNumber(), "M-Pesa number not configured");
    }
    return "Not configured";
  }

  private static DocumentUpload documentUploadFrom(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Document file is required");
    }
    if (file.getSize() > MAX_EMPLOYEE_DOCUMENT_BYTES) {
      throw new IllegalArgumentException("Document file must be 10 MB or smaller");
    }

    String fileName = sanitizeDocumentFileName(file.getOriginalFilename());
    String contentType = documentContentType(file.getContentType(), fileName);
    if (!ALLOWED_DOCUMENT_CONTENT_TYPES.contains(contentType)) {
      throw new IllegalArgumentException(
          "Only PDF, image, text, Word, and Excel documents are supported");
    }

    try {
      return new DocumentUpload(fileName, contentType, file.getBytes());
    } catch (IOException exception) {
      throw new IllegalArgumentException("Document file could not be read");
    }
  }

  private static String sanitizeDocumentFileName(String originalFileName) {
    String normalized =
        originalFileName == null || originalFileName.isBlank()
            ? "employee-document"
            : originalFileName.replace('\\', '/');
    int lastSlash = normalized.lastIndexOf('/');
    String fileName =
        (lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized)
            .replaceAll("[\\r\\n]+", " ")
            .trim();
    if (fileName.isBlank()) {
      fileName = "employee-document";
    }
    return truncate(fileName, 220);
  }

  private static String documentContentType(String contentType, String fileName) {
    String normalized =
        contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("image/jpg")) {
      normalized = "image/jpeg";
    }
    if (!normalized.isBlank() && !normalized.equals(DEFAULT_DOCUMENT_CONTENT_TYPE)) {
      return normalized;
    }

    String extension = documentFileExtension(fileName);
    return switch (extension) {
      case "pdf" -> "application/pdf";
      case "jpg", "jpeg" -> "image/jpeg";
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      case "txt" -> "text/plain";
      case "doc" -> "application/msword";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "xls" -> "application/vnd.ms-excel";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      default -> DEFAULT_DOCUMENT_CONTENT_TYPE;
    };
  }

  private static String documentFileExtension(String fileName) {
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
  }

  private static String requireDocumentText(String value, String label, int maxLength) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      throw new IllegalArgumentException(label + " is required");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + " must be " + maxLength + " characters or less");
    }
    return normalized;
  }

  private static boolean matchesDocumentSearch(EmployeeDocument document, String search) {
    if (search == null) {
      return true;
    }
    Employee employee = document.getEmployee();
    return document.getTitle().toLowerCase(Locale.ROOT).contains(search)
        || document.getFileName().toLowerCase(Locale.ROOT).contains(search)
        || document.getDocumentType().toLowerCase(Locale.ROOT).contains(search)
        || employee.getEmployeeNumber().toLowerCase(Locale.ROOT).contains(search)
        || EmployeeProfileResponse.fullName(employee).toLowerCase(Locale.ROOT).contains(search);
  }

  private PayrollSalesBonusRuleResponse salesBonusRuleResponse(UUID organizationId) {
    return payrollSalesBonusRuleRepository
        .findByOrganizationId(organizationId)
        .map(PayrollSalesBonusRuleResponse::from)
        .orElseGet(PayrollSalesBonusRuleResponse::empty);
  }

  private static String compactDestination(String first, String second, String third) {
    return String.join(
        " - ",
        List.of(optional(first), optional(second), optional(third)).stream()
            .filter(value -> !value.isBlank())
            .toList());
  }

  private static BigDecimal money(BigDecimal value) {
    if (value == null) {
      return zeroMoney();
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal zeroMoney() {
    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  }

  private static LeaveRequestStatus normalizeLeaveStatus(LeaveRequestStatus status) {
    if (status == LeaveRequestStatus.SUBMITTED) {
      return LeaveRequestStatus.PENDING_APPROVAL;
    }
    return status;
  }

  private void recalculatePayrollRun(AuthenticatedUser user, PayrollRun payrollRun) {
    Organization organization = requireOrganization(user.organizationId());
    PayrollSalesBonusRuleResponse salesBonusRule = salesBonusRuleResponse(user.organizationId());
    Map<UUID, PayrollAccumulator> accumulators =
        payrollAccumulators(
            employeePerformanceEntryRepository.findWithinPeriod(
                user.organizationId(),
                payrollRun.getPayrollPeriod().getPeriodStart(),
                payrollRun.getPayrollPeriod().getPeriodEnd()),
            saleRepository.findAttributedSalesWithinPeriod(
                user.organizationId(),
                new PerformancePeriod(
                        payrollRun.getPayrollPeriod().getPeriodStart(),
                        payrollRun.getPayrollPeriod().getPeriodEnd())
                    .fromInstant(organization),
                new PerformancePeriod(
                        payrollRun.getPayrollPeriod().getPeriodStart(),
                        payrollRun.getPayrollPeriod().getPeriodEnd())
                    .toExclusiveInstant(organization)),
            organization);
    List<Employee> employees =
        employeeRepository.findWithinOrganization(user.organizationId()).stream()
            .filter(employee -> canAccessEmployee(user, employee))
            .filter(
                employee ->
                    payrollRun.getBranch() == null
                        || employee
                            .getPrimaryBranch()
                            .getId()
                            .equals(payrollRun.getBranch().getId()))
            .filter(Employee::isActive)
            .toList();
    Map<UUID, PayrollEmployee> existingEmployees = new LinkedHashMap<>();
    payrollEmployeeRepository
        .findByRunId(payrollRun.getId())
        .forEach(
            payrollEmployee ->
                existingEmployees.put(payrollEmployee.getEmployee().getId(), payrollEmployee));
    for (Employee employee : employees) {
      PayrollAccumulator accumulator =
          accumulators.getOrDefault(employee.getId(), new PayrollAccumulator());
      BigDecimal basicSalary = money(employee.getBasicSalary());
      BigDecimal automaticBonusAmount = accumulator.automaticBonusAmount(salesBonusRule);
      BigDecimal manualBonusAmount = accumulator.manualBonusAmount();
      BigDecimal bonusAmount = manualBonusAmount.add(automaticBonusAmount);
      BigDecimal lossAmount = accumulator.lossAmount();
      BigDecimal grossPay = basicSalary.add(bonusAmount);
      BigDecimal netPay = grossPay.subtract(lossAmount).max(zeroMoney());
      PayrollEmployee payrollEmployee =
          existingEmployees.getOrDefault(
              employee.getId(),
              new PayrollEmployee(UUID.randomUUID(), organization, payrollRun, employee));
      payrollEmployee.updateTotals(
          money(grossPay),
          basicSalary,
          money(bonusAmount),
          automaticBonusAmount,
          manualBonusAmount,
          lossAmount,
          accumulator.totalSalesAmount(),
          accumulator.qualifyingSalesDays(salesBonusRule),
          salesBonusRule.dailySalesTarget(),
          salesBonusRule.bonusPerTargetDay(),
          accumulator.averagePerformanceScore(),
          lossAmount,
          money(netPay),
          employee.getSalaryPaymentMethod(),
          paymentDestination(employee),
          "Calculated from basic salary plus automatic sales target bonus and manual bonuses less period losses");
      payrollEmployeeRepository.save(payrollEmployee);
    }
  }

  private static int countStatus(List<AttendanceRecord> attendance, AttendanceStatus status) {
    return (int) attendance.stream().filter(item -> item.getStatus() == status).count();
  }

  private static void validateEmploymentDates(EmployeeRequest request) {
    LocalDate contractStart = request.contractStartDate();
    LocalDate contractEnd = request.contractEndDate();
    if (contractStart != null && contractEnd != null && contractEnd.isBefore(contractStart)) {
      throw new IllegalArgumentException("Contract end date cannot be before contract start date");
    }
    if (request.probationEndDate() != null
        && request.probationEndDate().isBefore(request.joiningDate())) {
      throw new IllegalArgumentException("Probation end date cannot be before joining date");
    }
  }

  private static LocalDate requireDate(LocalDate value, String label) {
    if (value == null) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static String normalizeName(String value) {
    return value.trim();
  }

  private static String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeEmployeeNumber(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String employeeNumberForUser(UUID organizationId, UserAccount account) {
    String source = account.getId().toString().replace("-", "");
    String base =
        normalizeEmployeeNumber("USR-" + source.substring(Math.max(0, source.length() - 12)));
    String candidate = base;
    int suffix = 2;
    while (employeeRepository.existsByEmployeeNumberWithinOrganization(organizationId, candidate)) {
      String suffixText = "-" + suffix++;
      candidate = base.substring(0, Math.min(base.length(), 40 - suffixText.length())) + suffixText;
    }
    return candidate;
  }

  private static PersonName personNameFromDisplayName(String displayName) {
    String normalized = normalizeName(displayName);
    String[] parts = normalized.split("\\s+", 2);
    String firstName = truncate(parts[0], 80);
    String lastName = parts.length > 1 ? truncate(parts[1], 80) : "User";
    return new PersonName(firstName, lastName);
  }

  private static String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String normalizeSearch(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean booleanOrTrue(Boolean value) {
    return value == null || value;
  }

  private static String optional(String value) {
    return value == null ? "" : value;
  }

  private static String optionalOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static Map<String, Object> employeeAuditMetadata(Employee employee) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("employeeNumber", employee.getEmployeeNumber());
    metadata.put("employmentStatus", employee.getEmploymentStatus().name());
    metadata.put("employmentType", employee.getEmploymentType().name());
    metadata.put("branchId", employee.getPrimaryBranch().getId());
    metadata.put(
        "departmentId", employee.getDepartment() == null ? null : employee.getDepartment().getId());
    metadata.put(
        "jobTitleId", employee.getJobTitle() == null ? null : employee.getJobTitle().getId());
    metadata.put("basicSalary", employee.getBasicSalary());
    metadata.put("salaryPaymentMethod", employee.getSalaryPaymentMethod().name());
    metadata.put("active", employee.isActive());
    return metadata;
  }

  private static Map<String, Object> attendanceAuditMetadata(AttendanceRecord attendance) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("employeeId", attendance.getEmployee().getId());
    metadata.put("employeeNumber", attendance.getEmployee().getEmployeeNumber());
    metadata.put("attendanceDate", attendance.getAttendanceDate());
    metadata.put("status", attendance.getStatus().name());
    metadata.put("clockIn", attendance.getClockIn());
    metadata.put("clockOut", attendance.getClockOut());
    metadata.put("workedMinutes", attendance.getWorkedMinutes());
    metadata.put("overtimeMinutes", attendance.getOvertimeMinutes());
    metadata.put("lateMinutes", attendance.getLateMinutes());
    metadata.put("corrected", attendance.isCorrected());
    metadata.put("correctionReason", attendance.getCorrectionReason());
    return metadata;
  }

  private static Map<String, Object> leaveRequestAuditMetadata(LeaveRequest request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("employeeId", request.getEmployee().getId());
    metadata.put("leaveTypeId", request.getLeaveType().getId());
    metadata.put("startDate", request.getStartDate());
    metadata.put("endDate", request.getEndDate());
    metadata.put("requestedDays", request.getRequestedDays());
    metadata.put("status", request.getStatus().name());
    metadata.put(
        "approverUserId",
        request.getApproverUser() == null ? null : request.getApproverUser().getId());
    return metadata;
  }

  private static Map<String, Object> performanceEntryAuditMetadata(EmployeePerformanceEntry entry) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("employeeId", entry.getEmployee().getId());
    metadata.put("employeeNumber", entry.getEmployee().getEmployeeNumber());
    metadata.put("entryType", entry.getEntryType().name());
    metadata.put("entryDate", entry.getEntryDate());
    metadata.put("amount", entry.getAmount());
    metadata.put("score", entry.getScore());
    return metadata;
  }

  private static Map<String, Object> employeeDocumentAuditMetadata(EmployeeDocument document) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("employeeId", document.getEmployee().getId());
    metadata.put("employeeNumber", document.getEmployee().getEmployeeNumber());
    metadata.put("documentType", document.getDocumentType());
    metadata.put("title", document.getTitle());
    metadata.put("fileName", document.getFileName());
    metadata.put("contentType", document.getContentType());
    metadata.put("sizeBytes", document.getSizeBytes());
    return metadata;
  }

  private record PerformancePeriod(LocalDate fromDate, LocalDate toDate) {
    private Instant fromInstant(Organization organization) {
      return fromDate.atStartOfDay(ZoneId.of(organization.getTimeZone())).toInstant();
    }

    private Instant toExclusiveInstant(Organization organization) {
      return toDate.plusDays(1).atStartOfDay(ZoneId.of(organization.getTimeZone())).toInstant();
    }
  }

  private record PersonName(String firstName, String lastName) {}

  private record DocumentUpload(String fileName, String contentType, byte[] content) {}

  private static final class PayrollAccumulator {
    private BigDecimal actualSalesAmount = zeroMoney();
    private BigDecimal manualSalesAmount = zeroMoney();
    private BigDecimal manualBonusAmount = zeroMoney();
    private BigDecimal lossAmount = zeroMoney();
    private final Map<LocalDate, SalesDayAccumulator> salesDays = new LinkedHashMap<>();
    private int scoreTotal;
    private int scoreCount;
    private int entryCount;

    private void recordEntry(EmployeePerformanceEntry entry) {
      entryCount++;
      switch (entry.getEntryType()) {
        case BONUS -> manualBonusAmount = manualBonusAmount.add(money(entry.getAmount()));
        case LOSS -> lossAmount = lossAmount.add(money(entry.getAmount()));
        case MANUAL_SALE -> {
          BigDecimal amount = money(entry.getAmount());
          manualSalesAmount = manualSalesAmount.add(amount);
          salesDays
              .computeIfAbsent(entry.getEntryDate(), ignored -> new SalesDayAccumulator())
              .recordManual(amount);
        }
        case PERFORMANCE_REVIEW -> {
          if (entry.getScore() != null) {
            scoreTotal += entry.getScore();
            scoreCount++;
          }
        }
      }
    }

    private void addActualSales(Sale sale, Organization organization) {
      BigDecimal amount = money(sale.getTotalAmount());
      actualSalesAmount = actualSalesAmount.add(amount);
      LocalDate salesDate =
          sale.getSoldAt().atZone(ZoneId.of(organization.getTimeZone())).toLocalDate();
      salesDays.computeIfAbsent(salesDate, ignored -> new SalesDayAccumulator()).record(amount);
    }

    private BigDecimal actualSalesAmount() {
      return money(actualSalesAmount);
    }

    private BigDecimal manualSalesAmount() {
      return money(manualSalesAmount);
    }

    private BigDecimal totalSalesAmount() {
      return money(actualSalesAmount.add(manualSalesAmount));
    }

    private BigDecimal manualBonusAmount() {
      return money(manualBonusAmount);
    }

    private BigDecimal automaticBonusAmount(PayrollSalesBonusRuleResponse salesBonusRule) {
      if (!salesBonusRule.active()
          || salesBonusRule.dailySalesTarget().compareTo(BigDecimal.ZERO) <= 0
          || salesBonusRule.bonusPerTargetDay().compareTo(BigDecimal.ZERO) <= 0) {
        return zeroMoney();
      }
      return money(
          salesBonusRule
              .bonusPerTargetDay()
              .multiply(BigDecimal.valueOf(qualifyingSalesDays(salesBonusRule))));
    }

    private int qualifyingSalesDays(PayrollSalesBonusRuleResponse salesBonusRule) {
      if (!salesBonusRule.active()
          || salesBonusRule.dailySalesTarget().compareTo(BigDecimal.ZERO) <= 0) {
        return 0;
      }
      return (int)
          salesDays.values().stream()
              .filter(day -> day.amount().compareTo(salesBonusRule.dailySalesTarget()) >= 0)
              .count();
    }

    private BigDecimal lossAmount() {
      return money(lossAmount);
    }

    private Integer averagePerformanceScore() {
      return scoreCount == 0 ? null : Math.round((float) scoreTotal / scoreCount);
    }

    private int entryCount() {
      return entryCount;
    }

    private List<EmployeeSalesDayResponse> salesDays() {
      return salesDays.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(
              entry ->
                  new EmployeeSalesDayResponse(
                      entry.getKey(), entry.getValue().amount(), entry.getValue().receiptCount()))
          .toList();
    }
  }

  private static final class SalesDayAccumulator {
    private BigDecimal amount = zeroMoney();
    private int receiptCount;

    private void record(BigDecimal value) {
      amount = amount.add(money(value));
      receiptCount++;
    }

    private void recordManual(BigDecimal value) {
      amount = amount.add(money(value));
    }

    private BigDecimal amount() {
      return money(amount);
    }

    private int receiptCount() {
      return receiptCount;
    }
  }
}
