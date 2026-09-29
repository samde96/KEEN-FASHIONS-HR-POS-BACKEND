package com.company.fashionpos.hr;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

  private final HrService hrService;
  private final AuthenticatedUserService authenticatedUserService;

  public HrController(HrService hrService, AuthenticatedUserService authenticatedUserService) {
    this.hrService = hrService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/departments")
  public List<DepartmentResponse> listDepartments(Principal principal) {
    return hrService.listDepartments(authenticatedUserService.current(principal));
  }

  @PostMapping("/departments")
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentResponse createDepartment(
      @Valid @RequestBody DepartmentRequest request, Principal principal) {
    return hrService.createDepartment(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/departments/{departmentId}")
  public DepartmentResponse updateDepartment(
      @PathVariable UUID departmentId,
      @Valid @RequestBody DepartmentRequest request,
      Principal principal) {
    return hrService.updateDepartment(
        authenticatedUserService.current(principal), departmentId, request);
  }

  @GetMapping("/job-titles")
  public List<JobTitleResponse> listJobTitles(Principal principal) {
    return hrService.listJobTitles(authenticatedUserService.current(principal));
  }

  @PostMapping("/job-titles")
  @ResponseStatus(HttpStatus.CREATED)
  public JobTitleResponse createJobTitle(
      @Valid @RequestBody JobTitleRequest request, Principal principal) {
    return hrService.createJobTitle(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/job-titles/{jobTitleId}")
  public JobTitleResponse updateJobTitle(
      @PathVariable UUID jobTitleId,
      @Valid @RequestBody JobTitleRequest request,
      Principal principal) {
    return hrService.updateJobTitle(
        authenticatedUserService.current(principal), jobTitleId, request);
  }

  @GetMapping("/employees")
  public EmployeePageResponse listEmployees(
      Principal principal,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) UUID jobTitleId,
      @RequestParam(required = false) EmployeeEmploymentStatus employmentStatus,
      @RequestParam(defaultValue = "lastName,asc") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return hrService.listEmployees(
        authenticatedUserService.current(principal),
        search,
        branchId,
        departmentId,
        jobTitleId,
        employmentStatus,
        sort,
        page,
        size);
  }

  @GetMapping("/employees/{employeeId}")
  public EmployeeProfileResponse getEmployee(@PathVariable UUID employeeId, Principal principal) {
    return hrService.getEmployee(authenticatedUserService.current(principal), employeeId);
  }

  @GetMapping("/documents")
  public List<EmployeeDocumentResponse> listDocuments(
      Principal principal,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) String documentType) {
    return hrService.listDocuments(
        authenticatedUserService.current(principal), search, branchId, employeeId, documentType);
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeeDocumentResponse uploadDocument(
      @RequestParam UUID employeeId,
      @RequestParam String documentType,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String notes,
      @RequestParam("file") MultipartFile file,
      Principal principal) {
    return hrService.uploadDocument(
        authenticatedUserService.current(principal), employeeId, documentType, title, notes, file);
  }

  @GetMapping("/documents/{documentId}/content")
  public ResponseEntity<byte[]> getDocumentContent(
      @PathVariable UUID documentId, Principal principal) {
    EmployeeDocumentContent content =
        hrService.getDocumentContent(authenticatedUserService.current(principal), documentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(content.fileName()).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .body(content.content());
  }

  @PostMapping("/employees")
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeeProfileResponse createEmployee(
      @Valid @RequestBody EmployeeRequest request, Principal principal) {
    return hrService.createEmployee(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/employees/sync-users")
  public EmployeeUserSyncResponse syncUsersAsEmployees(Principal principal) {
    return hrService.syncUsersAsEmployees(authenticatedUserService.current(principal));
  }

  @PutMapping("/employees/{employeeId}")
  public EmployeeProfileResponse updateEmployee(
      @PathVariable UUID employeeId,
      @Valid @RequestBody EmployeeRequest request,
      Principal principal) {
    return hrService.updateEmployee(
        authenticatedUserService.current(principal), employeeId, request);
  }

  @GetMapping("/attendance")
  public AttendancePageResponse listAttendance(
      Principal principal,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) AttendanceStatus status,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return hrService.listAttendance(
        authenticatedUserService.current(principal),
        search,
        branchId,
        employeeId,
        status,
        fromDate,
        toDate,
        page,
        size);
  }

  @PostMapping("/attendance")
  @ResponseStatus(HttpStatus.CREATED)
  public AttendanceResponse createAttendance(
      @Valid @RequestBody AttendanceRequest request, Principal principal) {
    return hrService.createAttendance(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/attendance/{attendanceId}")
  public AttendanceResponse updateAttendance(
      @PathVariable UUID attendanceId,
      @Valid @RequestBody AttendanceRequest request,
      Principal principal) {
    return hrService.updateAttendance(
        authenticatedUserService.current(principal), attendanceId, request);
  }

  @GetMapping("/leave")
  public LeavePageResponse getLeavePage(Principal principal) {
    return hrService.getLeavePage(authenticatedUserService.current(principal));
  }

  @PostMapping("/leave/types")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveTypeResponse createLeaveType(
      @Valid @RequestBody LeaveTypeRequest request, Principal principal) {
    return hrService.createLeaveType(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/leave/types/{leaveTypeId}")
  public LeaveTypeResponse updateLeaveType(
      @PathVariable UUID leaveTypeId,
      @Valid @RequestBody LeaveTypeRequest request,
      Principal principal) {
    return hrService.updateLeaveType(
        authenticatedUserService.current(principal), leaveTypeId, request);
  }

  @PostMapping("/leave/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public LeaveRequestResponse createLeaveRequest(
      @Valid @RequestBody LeaveRequestInput request, Principal principal) {
    return hrService.createLeaveRequest(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/leave/requests/{requestId}")
  public LeaveRequestResponse updateLeaveRequest(
      @PathVariable UUID requestId,
      @Valid @RequestBody LeaveRequestInput request,
      Principal principal) {
    return hrService.updateLeaveRequest(
        authenticatedUserService.current(principal), requestId, request);
  }

  @GetMapping("/performance")
  public EmployeePerformancePageResponse getPerformancePage(
      Principal principal,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID employeeId) {
    return hrService.getPerformancePage(
        authenticatedUserService.current(principal), fromDate, toDate, branchId, employeeId);
  }

  @PostMapping("/performance/entries")
  @ResponseStatus(HttpStatus.CREATED)
  public EmployeePerformanceEntryResponse createPerformanceEntry(
      @Valid @RequestBody EmployeePerformanceEntryRequest request, Principal principal) {
    return hrService.createPerformanceEntry(authenticatedUserService.current(principal), request);
  }

  @GetMapping("/payroll")
  public PayrollPageResponse getPayrollPage(Principal principal) {
    return hrService.getPayrollPage(authenticatedUserService.current(principal));
  }

  @PostMapping("/payroll/components")
  @ResponseStatus(HttpStatus.CREATED)
  public PayrollComponentResponse createPayrollComponent(
      @Valid @RequestBody PayrollComponentRequest request, Principal principal) {
    return hrService.createPayrollComponent(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/payroll/sales-bonus-rule")
  public PayrollSalesBonusRuleResponse updateSalesBonusRule(
      @Valid @RequestBody PayrollSalesBonusRuleRequest request, Principal principal) {
    return hrService.updateSalesBonusRule(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/payroll/periods")
  @ResponseStatus(HttpStatus.CREATED)
  public PayrollPeriodResponse createPayrollPeriod(
      @Valid @RequestBody PayrollPeriodRequest request, Principal principal) {
    return hrService.createPayrollPeriod(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/payroll/runs")
  @ResponseStatus(HttpStatus.CREATED)
  public PayrollRunResponse createPayrollRun(
      @Valid @RequestBody PayrollRunRequest request, Principal principal) {
    return hrService.createPayrollRun(authenticatedUserService.current(principal), request);
  }

  @PostMapping("/payroll/runs/{runId}/recalculate")
  public PayrollRunResponse recalculatePayrollRun(@PathVariable UUID runId, Principal principal) {
    return hrService.recalculatePayrollRun(authenticatedUserService.current(principal), runId);
  }

  @PutMapping("/payroll/runs/{runId}/employees/{payrollEmployeeId}/adjustments")
  public PayrollEmployeeResponse updatePayrollEmployeeAdjustment(
      @PathVariable UUID runId,
      @PathVariable UUID payrollEmployeeId,
      @Valid @RequestBody PayrollEmployeeAdjustmentRequest request,
      Principal principal) {
    return hrService.updatePayrollEmployeeAdjustment(
        authenticatedUserService.current(principal), runId, payrollEmployeeId, request);
  }
}
