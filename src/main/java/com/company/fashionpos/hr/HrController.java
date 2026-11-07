package com.company.fashionpos.hr;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {
  private final JdbcTemplate jdbc;
  private final AuthenticatedUserService authenticatedUsers;
  private final BranchService branches;

  public HrController(
      JdbcTemplate jdbc, AuthenticatedUserService authenticatedUsers, BranchService branches) {
    this.jdbc = jdbc;
    this.authenticatedUsers = authenticatedUsers;
    this.branches = branches;
  }

  @GetMapping("/me/availability")
  public Map<String, Object> myAvailability(
      @RequestParam(required = false) YearMonth month, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> employee = employeeForSelf(user);
    UUID employeeId = (UUID) employee.get("id");
    UUID branchId = (UUID) employee.get("branchId");
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
    YearMonth selectedMonth = month == null ? YearMonth.from(today) : month;
    LocalDate monthStart = selectedMonth.atDay(1);
    LocalDate monthEnd = selectedMonth.plusMonths(1).atDay(1);
    OffsetDateTime monthStartTime =
        monthStart.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime();
    OffsetDateTime monthEndTime =
        monthEnd.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime();
    List<Map<String, Object>> attendance =
        jdbc.queryForList(
            """
            select a.id, a.employee_id as "employeeId",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   b.name as "branchName", a.attendance_date as "attendanceDate",
                   a.clock_in as "clockIn", a.clock_out as "clockOut", a.status,
                   a.worked_minutes as "workedMinutes", a.notes
            from attendance_records a
            join employees e on e.id = a.employee_id
            join branches b on b.id = a.branch_id
            where a.organization_id = ? and a.employee_id = ? and a.attendance_date = ?
            """,
            user.organizationId(),
            employeeId,
            today);
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("employee", employee);
    result.put("month", selectedMonth.toString());
    result.put("todayAttendance", attendance.isEmpty() ? null : attendance.get(0));
    result.put(
        "leaveTypes",
        jdbc.queryForList(
            """
            select id, name, code, requires_balance as "requiresBalance"
            from leave_types where organization_id = ? and active = true order by name
            """,
            user.organizationId()));
    result.put(
        "leaveRequests",
        jdbc.queryForList(
            """
            select r.id, r.employee_id as "employeeId",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   r.branch_id as "branchId", b.name as "branchName",
                   r.leave_type_id as "leaveTypeId", t.code as "leaveTypeCode",
                   t.name as "leaveTypeName", r.start_date as "startDate",
                   r.end_date as "endDate", r.requested_days as "requestedDays",
                   r.reason, r.status, r.approver_comments as "approverComments"
            from leave_requests r
            join employees e on e.id = r.employee_id
            join branches b on b.id = r.branch_id
            join leave_types t on t.id = r.leave_type_id
            where r.organization_id = ? and r.employee_id = ?
              and r.end_date >= ? and r.start_date < ?
            order by r.created_at desc
            """,
            user.organizationId(),
            employeeId,
            monthStart,
            monthEnd));
    List<Map<String, Object>> calendarRecords = new ArrayList<>();
    for (Map<String, Object> request :
        jdbc.queryForList(
            """
            select r.id, r.leave_type_id as "leaveTypeId", t.code as "leaveTypeCode",
                   t.name as "leaveTypeName", r.start_date as "startDate",
                   r.end_date as "endDate", r.reason as note,
                   r.approver_comments as "reviewNote", r.status,
                   r.branch_id as "locationId", b.name as "locationName",
                   case
                     when upper(t.code) like '%SICK%' or lower(t.name) like '%sick%' then 'SICK_LEAVE'
                     when upper(t.code) like '%ANNUAL%' or lower(t.name) like '%annual%' then 'ANNUAL_LEAVE'
                     else 'OFF_DAY'
                   end as kind
            from leave_requests r
            join leave_types t on t.id = r.leave_type_id
            join branches b on b.id = r.branch_id
            where r.organization_id = ? and r.employee_id = ?
              and r.end_date >= ? and r.start_date < ?
            order by r.start_date
            """,
            user.organizationId(), employeeId, monthStart, monthEnd)) {
      if ("PENDING_APPROVAL".equals(request.get("status"))
          || "SUBMITTED".equals(request.get("status"))) {
        request.put("status", "PENDING");
      } else if ("REJECTED".equals(request.get("status"))) {
        request.put("status", "REJECTED");
      }
      calendarRecords.add(request);
    }
    calendarRecords.addAll(
        jdbc.queryForList(
            """
            select w.id, w.kind, w.starts_at as "startsAt", w.ends_at as "endsAt",
                   w.status, w.note, w.branch_id as "locationId", b.name as "locationName"
            from workforce_records w
            join branches b on b.id = w.branch_id
            where w.organization_id = ? and w.user_id = ? and w.kind = 'WORK'
              and w.starts_at < ? and coalesce(w.ends_at, 'infinity'::timestamptz) >= ?
            order by w.starts_at
            """,
            user.organizationId(),
            user.userId(),
            monthEndTime,
            monthStartTime));
    List<Map<String, Object>> attendanceHistory =
        jdbc.queryForList(
            """
            select a.id, a.attendance_date as "attendanceDate", a.clock_in as "clockIn",
                   a.clock_out as "clockOut", a.worked_minutes as "workedMinutes",
                   a.status, a.notes, a.branch_id as "locationId", b.name as "locationName"
            from attendance_records a
            join branches b on b.id = a.branch_id
            where a.organization_id = ? and a.employee_id = ?
              and a.attendance_date >= ? and a.attendance_date < ?
            order by a.attendance_date
            """,
            user.organizationId(),
            employeeId,
            monthStart,
            monthEnd);
    result.put("calendarRecords", calendarRecords);
    result.put("attendanceHistory", attendanceHistory);
    result.put(
        "clockedInStaff",
        jdbc.queryForList(
            """
            select e.id as "employeeId", concat(e.first_name, ' ', e.last_name) as "employeeName",
                   a.clock_in as "clockIn"
            from attendance_records a
            join employees e on e.id = a.employee_id
            where a.organization_id = ? and a.branch_id = ? and a.attendance_date = ?
              and a.clock_in is not null and a.clock_out is null and e.active = true
            order by e.first_name, e.last_name
            """,
            user.organizationId(),
            branchId,
            today));
    result.put(
        "upcomingShifts",
        jdbc.queryForList(
            """
            select w.id, w.branch_id as "branchId", b.name as "branchName",
                   w.starts_at as "startsAt", w.ends_at as "endsAt",
                   w.break_minutes as "breakMinutes", w.note
            from workforce_records w
            join branches b on b.id = w.branch_id
            where w.organization_id = ? and w.user_id = ? and w.kind = 'WORK'
              and w.status = 'APPROVED' and w.ends_at >= now()
            order by w.starts_at
            limit 14
            """,
            user.organizationId(),
            user.userId()));
    return result;
  }

  @PostMapping("/me/clock-in")
  @Transactional
  public Map<String, Object> clockIn(java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> employee = employeeForSelf(user);
    UUID employeeId = (UUID) employee.get("id");
    UUID branchId = (UUID) employee.get("branchId");
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
    OffsetDateTime now = java.time.OffsetDateTime.now(ZoneId.of("Africa/Nairobi"));
    UUID id =
        jdbc.query(
            """
            insert into attendance_records (
              id, organization_id, employee_id, branch_id, attendance_date, clock_in,
              status, worked_minutes, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, 'PRESENT', 0, now(), now(), 0)
            on conflict (employee_id, attendance_date) do update
              set branch_id = excluded.branch_id, clock_in = excluded.clock_in,
                  clock_out = null, status = 'PRESENT', worked_minutes = 0,
                  updated_at = now(), version = attendance_records.version + 1
              where attendance_records.clock_in is null
            returning id
            """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            UUID.randomUUID(),
            user.organizationId(),
            employeeId,
            branchId,
            today,
            now);
    if (id == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "You are already clocked in or have completed today’s session");
    }
    return attendanceRecord(id, user);
  }

  @PostMapping("/me/clock-out")
  @Transactional
  public Map<String, Object> clockOut(java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> employee = employeeForSelf(user);
    UUID employeeId = (UUID) employee.get("id");
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
    OffsetDateTime clockIn =
        jdbc.query(
            """
            select clock_in from attendance_records
            where organization_id = ? and employee_id = ? and attendance_date = ?
              and clock_in is not null and clock_out is null
            for update
            """,
            rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null,
            user.organizationId(),
            employeeId,
            today);
    if (clockIn == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "You do not have an active session today");
    }
    OffsetDateTime now = java.time.OffsetDateTime.now(ZoneId.of("Africa/Nairobi"));
    int workedMinutes = Math.toIntExact(java.time.Duration.between(clockIn, now).toMinutes());
    UUID id =
        jdbc.query(
            """
            update attendance_records
            set clock_out = ?, worked_minutes = ?, updated_at = now(), version = version + 1
            where organization_id = ? and employee_id = ? and attendance_date = ?
              and clock_out is null
            returning id
            """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            now,
            Math.max(0, workedMinutes),
            user.organizationId(),
            employeeId,
            today);
    if (id == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Your working session has already ended");
    }
    return attendanceRecord(id, user);
  }

  @PostMapping("/me/leave-requests")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> requestMyLeave(
      @Valid @RequestBody MyLeaveRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> employee = employeeForSelf(user);
    UUID employeeId = (UUID) employee.get("id");
    UUID branchId = (UUID) employee.get("branchId");
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
    if (request.endDate().isBefore(request.startDate())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "End date must not precede start date");
    }
    if (request.startDate().isBefore(today)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Leave requests cannot start in the past");
    }
    boolean leaveTypeExists =
        !jdbc.queryForList(
                """
            select id from leave_types
            where id = ? and organization_id = ? and active = true
            """,
                UUID.class,
                request.leaveTypeId(),
                user.organizationId())
            .isEmpty();
    if (!leaveTypeExists) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave type not found");
    }
    long days =
        java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into leave_requests (
          id, organization_id, employee_id, branch_id, leave_type_id, start_date, end_date,
          requested_days, reason, status, submitted_at, created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', now(), now(), now(), 0)
        """,
        id,
        user.organizationId(),
        employeeId,
        branchId,
        request.leaveTypeId(),
        request.startDate(),
        request.endDate(),
        BigDecimal.valueOf(days),
        blankToNull(request.reason()));
    return leaveRequest(id, user);
  }

  @GetMapping("/employees")
  public List<Map<String, Object>> employees(
      @RequestParam(defaultValue = "false") boolean includeInactive,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    StringBuilder sql =
        new StringBuilder(
            """
            select e.id, e.user_id as "userId", e.employee_number as "employeeNumber", e.first_name as "firstName",
                   e.middle_name as "middleName", e.last_name as "lastName", e.email, e.phone,
                   e.primary_branch_id as "branchId", b.name as "branchName",
                   jt.title as "jobTitle", e.employment_type as "employmentType",
                   e.employment_status as "employmentStatus", e.joining_date as "joiningDate",
                   e.basic_salary as "basicSalary", e.active
            from employees e
            join branches b on b.id = e.primary_branch_id
            left join job_titles jt on jt.id = e.job_title_id
            where e.organization_id = ? and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """);
    List<Object> args = new ArrayList<>();
    args.add(user.organizationId());
    if (!includeInactive) {
      sql.append(" and e.active = true");
    }
    appendBranchScope(sql, args, user);
    sql.append(" order by e.last_name, e.first_name");
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  @PostMapping("/employees")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> createEmployee(
      @Valid @RequestBody EmployeeRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    branches.requireBranchAccess(user, request.branchId());
    requireHrLocation(user, request.branchId());
    if (!List.of("PERMANENT", "CONTRACT", "TEMPORARY", "INTERN", "CASUAL")
        .contains(request.employmentType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employment type is invalid");
    }
    if (request.joiningDate().isAfter(LocalDate.now(ZoneId.of("Africa/Nairobi")))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Joining date cannot be in the future");
    }

    String firstName = request.firstName().trim();
    String middleName = blankToNull(request.middleName());
    String lastName = request.lastName().trim();
    String email = blankToNull(request.email());
    if (request.userId() != null) {
      List<Map<String, Object>> accounts =
          jdbc.queryForList(
              """
              select display_name as "displayName", email, status
              from users where id = ? and organization_id = ?
              """,
              request.userId(),
              user.organizationId());
      if (accounts.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System account not found");
      }
      Map<String, Object> account = accounts.get(0);
      if (!"ACTIVE".equals(account.get("status"))) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Choose an active system account");
      }
      boolean alreadyLinked =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  "select exists (select 1 from employees where organization_id = ? and user_id = ?)",
                  Boolean.class,
                  user.organizationId(),
                  request.userId()));
      if (alreadyLinked) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "This system account is already linked to an employee");
      }
      String[] nameParts = account.get("displayName").toString().trim().split("\\s+");
      firstName = nameParts[0];
      lastName = nameParts.length > 1 ? nameParts[nameParts.length - 1] : nameParts[0];
      middleName =
          nameParts.length > 2
              ? String.join(" ", java.util.Arrays.copyOfRange(nameParts, 1, nameParts.length - 1))
              : null;
      if (firstName.length() > 80
          || lastName.length() > 80
          || (middleName != null && middleName.length() > 80)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "The account name is too long to use as an employee name");
      }
      email = account.get("email").toString();
    }

    UUID id = UUID.randomUUID();
    String employeeNumber;
    do {
      long sequence = jdbc.queryForObject("select nextval('employee_number_sequence')", Long.class);
      employeeNumber = "EMP-%06d".formatted(sequence);
    } while (!jdbc.queryForList(
            "select id from employees where organization_id = ? and employee_number = ?",
            UUID.class,
            user.organizationId(),
            employeeNumber)
        .isEmpty());
    jdbc.update(
        """
        insert into employees (
          id, organization_id, user_id, primary_branch_id, employee_number, first_name, middle_name,
          last_name, email, phone, employment_type, employment_status, joining_date,
          basic_salary, active, created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, true, now(), now(), 0)
        """,
        id,
        user.organizationId(),
        request.userId(),
        request.branchId(),
        employeeNumber,
        firstName,
        middleName,
        lastName,
        email,
        blankToNull(request.phone()),
        request.employmentType(),
        request.joiningDate(),
        request.basicSalary());
    List<UUID> leaveTypeIds =
        jdbc.queryForList(
            "select id from leave_types where organization_id = ? and active = true",
            UUID.class,
            user.organizationId());
    if (!leaveTypeIds.isEmpty()) {
      jdbc.batchUpdate(
          """
          insert into leave_balances (
            id, organization_id, employee_id, leave_type_id, balance_days, used_days,
            created_at, updated_at, version
          ) values (?, ?, ?, ?, ?, 0, now(), now(), 0)
          """,
          leaveTypeIds,
          leaveTypeIds.size(),
          (statement, leaveTypeId) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, user.organizationId());
            statement.setObject(3, id);
            statement.setObject(4, leaveTypeId);
            statement.setBigDecimal(
                5,
                jdbc.queryForObject(
                    "select default_days from leave_types where id = ? and organization_id = ?",
                    BigDecimal.class,
                    leaveTypeId,
                    user.organizationId()));
          });
    }
    return jdbc.queryForMap(
        """
        select e.id, e.user_id as "userId", e.employee_number as "employeeNumber", e.first_name as "firstName",
               e.middle_name as "middleName", e.last_name as "lastName", e.email, e.phone,
               e.primary_branch_id as "branchId", b.name as "branchName",
               null as "jobTitle", e.employment_type as "employmentType",
               e.employment_status as "employmentStatus", e.joining_date as "joiningDate",
               e.basic_salary as "basicSalary", e.active
        from employees e join branches b on b.id = e.primary_branch_id
        where e.id = ? and e.organization_id = ?
        """,
        id,
        user.organizationId());
  }

  @PutMapping("/employees/{employeeId}")
  @Transactional
  public Map<String, Object> updateEmployee(
      @PathVariable UUID employeeId,
      @Valid @RequestBody EmployeeUpdateRequest request,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    UUID currentBranchId =
        jdbc.query(
            "select primary_branch_id from employees where id = ? and organization_id = ?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            employeeId,
            user.organizationId());
    if (currentBranchId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    if (!user.branchIds().isEmpty() && !user.branchIds().contains(currentBranchId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    branches.requireBranchAccess(user, request.branchId());
    requireHrLocation(user, request.branchId());
    if (!List.of("PERMANENT", "CONTRACT", "TEMPORARY", "INTERN", "CASUAL")
        .contains(request.employmentType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employment type is invalid");
    }
    if (request.joiningDate().isAfter(LocalDate.now(ZoneId.of("Africa/Nairobi")))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Joining date cannot be in the future");
    }
    jdbc.update(
        """
        update employees
        set primary_branch_id = ?, first_name = case when user_id is null then ? else first_name end,
            middle_name = case when user_id is null then ? else middle_name end,
            last_name = case when user_id is null then ? else last_name end,
            email = case when user_id is null then ? else email end,
            phone = ?, employment_type = ?, joining_date = ?, basic_salary = ?,
            updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        request.branchId(),
        request.firstName().trim(),
        blankToNull(request.middleName()),
        request.lastName().trim(),
        blankToNull(request.email()),
        blankToNull(request.phone()),
        request.employmentType(),
        request.joiningDate(),
        request.basicSalary(),
        employeeId,
        user.organizationId());
    return jdbc.queryForMap(
        """
        select e.id, e.user_id as "userId", e.employee_number as "employeeNumber", e.first_name as "firstName",
               e.middle_name as "middleName", e.last_name as "lastName", e.email, e.phone,
               e.primary_branch_id as "branchId", b.name as "branchName",
               jt.title as "jobTitle", e.employment_type as "employmentType",
               e.employment_status as "employmentStatus", e.joining_date as "joiningDate",
               e.basic_salary as "basicSalary", e.active
        from employees e join branches b on b.id = e.primary_branch_id
        left join job_titles jt on jt.id = e.job_title_id
        where e.id = ? and e.organization_id = ?
        """,
        employeeId,
        user.organizationId());
  }

  @PatchMapping("/employees/{employeeId}/active")
  @Transactional
  public Map<String, Object> setEmployeeActive(
      @PathVariable UUID employeeId,
      @Valid @RequestBody EmployeeActivationRequest request,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    UUID branchId =
        jdbc.query(
            "select primary_branch_id from employees where id = ? and organization_id = ?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            employeeId,
            user.organizationId());
    if (branchId == null || (!user.branchIds().isEmpty() && !user.branchIds().contains(branchId))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    if (!request.active()) {
      boolean clockedIn =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
                  select exists (
                    select 1 from attendance_records
                    where organization_id = ? and employee_id = ?
                      and clock_in is not null and clock_out is null
                  )
                  """,
                  Boolean.class,
                  user.organizationId(),
                  employeeId));
      if (clockedIn) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Clock the employee out before deactivating the profile");
      }
      jdbc.update(
          """
          update workforce_records
          set status = 'CANCELLED', updated_at = now(), version = version + 1
          where organization_id = ? and user_id = (
            select user_id from employees where id = ? and organization_id = ?
          ) and kind = 'WORK' and status = 'APPROVED' and ends_at > now()
          """,
          user.organizationId(),
          employeeId,
          user.organizationId());
    }
    int updated =
        jdbc.update(
            """
            update employees set active = ?, updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """,
            request.active(),
            employeeId,
            user.organizationId());
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
    }
    return jdbc.queryForMap(
        """
        select e.id, e.user_id as "userId", e.employee_number as "employeeNumber",
               e.first_name as "firstName", e.middle_name as "middleName",
               e.last_name as "lastName", e.email, e.phone,
               e.primary_branch_id as "branchId", b.name as "branchName",
               jt.title as "jobTitle", e.employment_type as "employmentType",
               e.employment_status as "employmentStatus", e.joining_date as "joiningDate",
               e.basic_salary as "basicSalary", e.active
        from employees e join branches b on b.id = e.primary_branch_id
        left join job_titles jt on jt.id = e.job_title_id
        where e.id = ? and e.organization_id = ?
        """,
        employeeId,
        user.organizationId());
  }

  @GetMapping("/attendance")
  public List<Map<String, Object>> attendance(
      @RequestParam LocalDate date, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    StringBuilder sql =
        new StringBuilder(
            """
            select a.id, a.employee_id as "employeeId",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   b.name as "branchName", a.attendance_date as "attendanceDate",
                   a.clock_in as "clockIn", a.clock_out as "clockOut", a.status,
                   a.worked_minutes as "workedMinutes", a.notes
            from attendance_records a
            join employees e on e.id = a.employee_id
            join branches b on b.id = a.branch_id
            where a.organization_id = ? and a.attendance_date = ?
              and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """);
    List<Object> args = new ArrayList<>(List.of(user.organizationId(), date));
    appendBranchScope(sql, args, user);
    sql.append(" order by e.last_name, e.first_name");
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  @PostMapping("/attendance")
  @Transactional
  public Map<String, Object> saveAttendance(
      @Valid @RequestBody AttendanceRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    branches.requireBranchAccess(user, request.branchId());
    requireHrLocation(user, request.branchId());
    requireEmployeeAtBranch(user, request.employeeId(), request.branchId());
    if (!List.of("PRESENT", "ABSENT", "LATE", "HALF_DAY", "ON_LEAVE", "HOLIDAY", "OFF_DAY")
        .contains(request.status())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attendance status is invalid");
    }
    if (request.attendanceDate().isAfter(LocalDate.now(ZoneId.of("Africa/Nairobi")))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Attendance cannot be recorded for a future date");
    }
    UUID id =
        jdbc.query(
            """
            insert into attendance_records (
              id, organization_id, employee_id, branch_id, attendance_date, status,
              worked_minutes, notes, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, 0, ?, now(), now(), 0)
            on conflict (employee_id, attendance_date) do update
              set branch_id = excluded.branch_id, status = excluded.status,
                  notes = excluded.notes, updated_at = now(), version = attendance_records.version + 1
            returning id
            """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            UUID.randomUUID(),
            user.organizationId(),
            request.employeeId(),
            request.branchId(),
            request.attendanceDate(),
            request.status(),
            blankToNull(request.notes()));
    return jdbc.queryForMap(
        """
        select a.id, a.employee_id as "employeeId",
               concat(e.first_name, ' ', e.last_name) as "employeeName",
               b.name as "branchName", a.attendance_date as "attendanceDate",
               a.clock_in as "clockIn", a.clock_out as "clockOut", a.status,
               a.worked_minutes as "workedMinutes", a.notes
        from attendance_records a
        join employees e on e.id = a.employee_id
        join branches b on b.id = a.branch_id
        where a.id = ? and a.organization_id = ?
        """,
        id,
        user.organizationId());
  }

  @GetMapping("/leave-types")
  public List<Map<String, Object>> leaveTypes(java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    return jdbc.queryForList(
        """
        select id, name, code, requires_balance as "requiresBalance"
        from leave_types where organization_id = ? and active = true order by name
        """,
        user.organizationId());
  }

  @PostMapping("/leave-types")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> createLeaveType(
      @Valid @RequestBody LeaveTypeRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
    if (!code.matches("[A-Z0-9_-]{2,40}")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Leave type code must use 2–40 letters, numbers, dashes or underscores");
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into leave_types (
          id, organization_id, name, code, requires_balance, default_days, active,
          created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
        """,
        id,
        user.organizationId(),
        request.name().trim(),
        code,
        request.requiresBalance(),
        request.defaultDays());
    List<UUID> employeeIds =
        jdbc.queryForList(
            """
            select e.id from employees e join branches b on b.id = e.primary_branch_id
            where e.organization_id = ? and e.active = true
              and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """,
            UUID.class,
            user.organizationId());
    if (!employeeIds.isEmpty()) {
      jdbc.batchUpdate(
          """
          insert into leave_balances (
            id, organization_id, employee_id, leave_type_id, balance_days, used_days,
            created_at, updated_at, version
          ) values (?, ?, ?, ?, ?, 0, now(), now(), 0)
          """,
          employeeIds,
          employeeIds.size(),
          (statement, employeeId) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, user.organizationId());
            statement.setObject(3, employeeId);
            statement.setObject(4, id);
            statement.setBigDecimal(5, BigDecimal.valueOf(request.defaultDays()));
          });
    }
    return jdbc.queryForMap(
        """
        select id, name, requires_balance as "requiresBalance"
        from leave_types where id = ? and organization_id = ?
        """,
        id,
        user.organizationId());
  }

  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests(java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    StringBuilder sql =
        new StringBuilder(
            """
            select r.id, r.employee_id as "employeeId",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   r.branch_id as "branchId", b.name as "branchName",
                   r.leave_type_id as "leaveTypeId",
                   t.name as "leaveTypeName", r.start_date as "startDate",
                   r.end_date as "endDate", r.requested_days as "requestedDays",
                   r.reason, r.status, r.approver_comments as "approverComments"
            from leave_requests r
            join employees e on e.id = r.employee_id
            join branches b on b.id = r.branch_id
            join leave_types t on t.id = r.leave_type_id
            where r.organization_id = ? and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """);
    List<Object> args = new ArrayList<>();
    args.add(user.organizationId());
    appendBranchScope(sql, args, user);
    sql.append(" order by r.created_at desc");
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  @GetMapping("/admin/summary")
  public Map<String, Object> adminSummary(
      @RequestParam YearMonth month,
      @RequestParam(required = false) UUID branchId,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    if (branchId != null) {
      branches.requireBranchAccess(user, branchId);
      requireHrLocation(user, branchId);
    }
    LocalDate from = month.atDay(1);
    LocalDate to = month.plusMonths(1).atDay(1);
    StringBuilder sql =
        new StringBuilder(
            """
            with staff as (
              select e.id as "employeeId", e.user_id as "userId",
                     e.employee_number as "employeeNumber",
                     concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
                     j.title as "jobTitle", e.primary_branch_id as "branchId",
                     b.name as "branchName"
              from employees e
              join branches b on b.id = e.primary_branch_id
              left join job_titles j on j.id = e.job_title_id
              where e.organization_id = ? and e.active = true
                and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """);
    List<Object> args = new ArrayList<>(List.of(user.organizationId()));
    appendReportBranchScope(sql, args, user, branchId, "b");
    sql.append(
        """
            ), attendance as (
              select a.employee_id, coalesce(sum(a.worked_minutes), 0) as "workedMinutes",
                     count(*) filter (where a.clock_in is not null) as "workingDays",
                     count(*) filter (where a.status = 'ABSENT') as "absentDays"
              from attendance_records a
              join branches b on b.id = a.branch_id
              join staff s on s."employeeId" = a.employee_id
              where a.organization_id = ? and a.attendance_date >= ? and a.attendance_date < ?
            """);
    args.add(user.organizationId());
    args.add(from);
    args.add(to);
    appendReportBranchScope(sql, args, user, branchId, "b");
    sql.append(
        """
              group by a.employee_id
            ), sales as (
              select s.cashier_id as "userId", coalesce(sum(s.total_amount), 0) as "sales",
                     count(*) as transactions
              from sales s
              join branches b on b.id = s.branch_id
              where s.organization_id = ? and s.status = 'COMPLETED'
                and s.sold_at >= ? and s.sold_at < ?
            """);
    args.add(user.organizationId());
    args.add(from.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime());
    args.add(to.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime());
    appendReportBranchScope(sql, args, user, branchId, "b");
    sql.append(
        """
              group by s.cashier_id
              union all
              select o.operator_id as "userId", coalesce(sum(o.total), 0) as sales,
                     count(*) as transactions
              from hotel_orders o
              join branches b on b.id = o.branch_id
              where o.organization_id = ? and o.status = 'CLOSED'
                and coalesce(o.paid_at, o.created_at) >= ?
                and coalesce(o.paid_at, o.created_at) < ?
            """);
    args.add(user.organizationId());
    args.add(from.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime());
    args.add(to.atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime());
    appendReportBranchScope(sql, args, user, branchId, "b");
    sql.append(
        """
              group by o.operator_id
            ), sales_by_user as (
              select "userId", sum(sales) as sales, sum(transactions) as transactions
              from sales
              group by "userId"
            ), leave_summary as (
              select r.employee_id,
                coalesce(sum(case when r.status = 'APPROVED'
                  and (upper(t.code) like '%SICK%' or lower(t.name) like '%sick%')
                  then r.requested_days else 0 end), 0) as "sickDays",
                coalesce(sum(case when r.status = 'APPROVED'
                  and (upper(t.code) like '%ANNUAL%' or lower(t.name) like '%annual%')
                  then r.requested_days else 0 end), 0) as "annualLeaveDays",
                coalesce(sum(case when r.status = 'APPROVED'
                  and upper(t.code) not like '%SICK%' and lower(t.name) not like '%sick%'
                  and upper(t.code) not like '%ANNUAL%' and lower(t.name) not like '%annual%'
                  then r.requested_days else 0 end), 0) as "offDays",
                count(*) filter (where r.status = 'PENDING_APPROVAL') as "pendingRequests"
              from leave_requests r
              join leave_types t on t.id = r.leave_type_id
              join branches b on b.id = r.branch_id
              join staff s on s."employeeId" = r.employee_id
              where r.organization_id = ? and r.end_date >= ? and r.start_date < ?
            """);
    args.add(user.organizationId());
    args.add(from);
    args.add(to);
    appendReportBranchScope(sql, args, user, branchId, "b");
    sql.append(
        """
              group by r.employee_id
            )
            select s."employeeId", s."userId", s."employeeNumber", s."employeeName",
                   s."jobTitle", s."branchId", s."branchName",
                   coalesce(a."workedMinutes", 0) as "workedMinutes",
                   coalesce(a."workingDays", 0) as "workingDays",
                   coalesce(a."absentDays", 0) as "absentDays",
                   coalesce(l."offDays", 0) as "offDays",
                   coalesce(l."sickDays", 0) as "sickDays",
                   coalesce(l."annualLeaveDays", 0) as "annualLeaveDays",
                   coalesce(l."pendingRequests", 0) as "pendingRequests",
                   coalesce(sa.sales, 0) as sales, coalesce(sa.transactions, 0) as transactions
            from staff s
            left join attendance a on a.employee_id = s."employeeId"
            left join sales_by_user sa on sa."userId" = s."userId"
            left join leave_summary l on l.employee_id = s."employeeId"
            order by s."employeeName"
            """);
    List<Map<String, Object>> staff = jdbc.queryForList(sql.toString(), args.toArray());

    StringBuilder attendanceSql =
        new StringBuilder(
            """
            select a.id, a.employee_id as "employeeId",
                   concat_ws(' ', e.first_name, e.middle_name, e.last_name) as "employeeName",
                   b.name as "branchName", a.attendance_date as "attendanceDate",
                   a.clock_in as "clockIn", a.clock_out as "clockOut", a.status,
                   a.worked_minutes as "workedMinutes", a.notes
            from attendance_records a
            join employees e on e.id = a.employee_id
            join branches b on b.id = a.branch_id
            where a.organization_id = ? and a.attendance_date >= ? and a.attendance_date < ?
              and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
            """);
    List<Object> attendanceArgs = new ArrayList<>(List.of(user.organizationId(), from, to));
    appendReportBranchScope(attendanceSql, attendanceArgs, user, branchId, "b");
    attendanceSql.append(" order by a.attendance_date desc, e.last_name, e.first_name");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month.toString());
    result.put("staff", staff);
    result.put("attendance", jdbc.queryForList(attendanceSql.toString(), attendanceArgs.toArray()));
    return result;
  }

  @PostMapping("/leave-requests")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> createLeaveRequest(
      @Valid @RequestBody LeaveRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    branches.requireBranchAccess(user, request.branchId());
    requireHrLocation(user, request.branchId());
    requireEmployeeAtBranch(user, request.employeeId(), request.branchId());
    if (request.endDate().isBefore(request.startDate())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "End date must not precede start date");
    }
    long rangeDays =
        java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    if (request.requestedDays().compareTo(BigDecimal.valueOf(rangeDays)) > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Requested days cannot exceed the selected date range");
    }
    boolean leaveTypeExists =
        !jdbc.queryForList(
                """
            select id from leave_types
            where id = ? and organization_id = ? and active = true
            """,
                UUID.class,
                request.leaveTypeId(),
                user.organizationId())
            .isEmpty();
    if (!leaveTypeExists) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave type not found");
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into leave_requests (
          id, organization_id, employee_id, branch_id, leave_type_id, start_date, end_date,
          requested_days, reason, status, submitted_at, created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', now(), now(), now(), 0)
        """,
        id,
        user.organizationId(),
        request.employeeId(),
        request.branchId(),
        request.leaveTypeId(),
        request.startDate(),
        request.endDate(),
        request.requestedDays(),
        blankToNull(request.reason()));
    return leaveRequest(id, user);
  }

  @PostMapping("/leave-requests/{requestId}/decision")
  @Transactional
  public Map<String, Object> decideLeaveRequest(
      @PathVariable UUID requestId,
      @Valid @RequestBody LeaveDecision decision,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    if (!List.of("APPROVED", "REJECTED").contains(decision.status())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Decision must be approved or rejected");
    }
    UUID branchId =
        jdbc.query(
            """
            select branch_id from leave_requests
            where id = ? and organization_id = ? and status = 'PENDING_APPROVAL'
            """,
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            requestId,
            user.organizationId());
    if (branchId == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Pending leave request was not found");
    }
    branches.requireBranchAccess(user, branchId);
    if ("APPROVED".equals(decision.status())) {
      List<Map<String, Object>> pending =
          jdbc.queryForList(
              """
              select employee_id as "employeeId", leave_type_id as "leaveTypeId",
                     requested_days as "requestedDays"
              from leave_requests where id = ? and organization_id = ? for update
              """,
              requestId,
              user.organizationId());
      Map<String, Object> row = pending.get(0);
      UUID employeeId = (UUID) row.get("employeeId");
      UUID leaveTypeId = (UUID) row.get("leaveTypeId");
      BigDecimal days = (BigDecimal) row.get("requestedDays");
      boolean requiresBalance =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  "select requires_balance from leave_types where id = ? and organization_id = ?",
                  Boolean.class,
                  leaveTypeId,
                  user.organizationId()));
      if (requiresBalance) {
        List<BigDecimal> balances =
            jdbc.queryForList(
                """
                select balance_days - used_days from leave_balances
                where employee_id = ? and leave_type_id = ? and organization_id = ? for update
                """,
                BigDecimal.class,
                employeeId,
                leaveTypeId,
                user.organizationId());
        if (balances.isEmpty() || balances.get(0).compareTo(days) < 0) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT, "The employee does not have enough leave balance");
        }
        jdbc.update(
            """
            update leave_balances set used_days = used_days + ?, updated_at = now(),
              version = version + 1
            where employee_id = ? and leave_type_id = ? and organization_id = ?
            """,
            days,
            employeeId,
            leaveTypeId,
            user.organizationId());
      }
    }
    int updated =
        jdbc.update(
            """
            update leave_requests
            set status = ?, approver_user_id = ?, approver_comments = ?,
                decided_at = now(), updated_at = now(), version = version + 1
            where id = ? and organization_id = ? and status = 'PENDING_APPROVAL'
              and branch_id in (
                select id from branches where organization_id = ?
                  and branch_type in ('STORE', 'SHOP', 'HOTEL')
              )
            """,
            decision.status(),
            user.userId(),
            blankToNull(decision.approverComments()),
            requestId,
            user.organizationId(),
            user.organizationId());
    if (updated != 1) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Pending leave request was not found");
    }
    return leaveRequest(requestId, user);
  }

  @GetMapping("/payroll/preview")
  public List<Map<String, Object>> payrollPreview(
      @RequestParam String period, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    final YearMonth month;
    try {
      month = YearMonth.parse(period);
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period must use YYYY-MM format");
    }
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();
    StringBuilder sql =
        new StringBuilder(
            """
            select e.id as "employeeId", e.employee_number as "employeeNumber",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   b.name as "branchName", e.basic_salary as "basicSalary",
                   coalesce(sum(case when p.entry_type = 'BONUS' then p.amount else 0 end), 0)
                     as "bonusAmount",
                   coalesce(sum(case when p.entry_type = 'LOSS' then p.amount else 0 end), 0)
                     as deductions,
                   e.basic_salary
                     + coalesce(sum(case when p.entry_type = 'BONUS' then p.amount else 0 end), 0)
                     - coalesce(sum(case when p.entry_type = 'LOSS' then p.amount else 0 end), 0)
                     as "netPay"
            from employees e
            join branches b on b.id = e.primary_branch_id
            left join employee_performance_entries p
              on p.employee_id = e.id and p.organization_id = e.organization_id
              and p.entry_date between ? and ?
            where e.organization_id = ? and e.active = true
              and e.joining_date <= ? and b.branch_type in ('STORE', 'SHOP')
              and (e.contract_end_date is null or e.contract_end_date >= ?)
            """);
    List<Object> args = new ArrayList<>(List.of(start, end, user.organizationId(), end, start));
    appendBranchScope(sql, args, user);
    sql.append(
        """
        group by e.id, e.employee_number, e.first_name, e.last_name, b.name, e.basic_salary
        order by e.last_name, e.first_name
        """);
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  private Map<String, Object> leaveRequest(UUID requestId, AuthenticatedUser user) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select r.id, r.employee_id as "employeeId",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   r.branch_id as "branchId", b.name as "branchName",
                   r.leave_type_id as "leaveTypeId",
                   t.name as "leaveTypeName", r.start_date as "startDate",
                   r.end_date as "endDate", r.requested_days as "requestedDays",
                   r.reason, r.status, r.approver_comments as "approverComments"
            from leave_requests r join employees e on e.id = r.employee_id
            join branches b on b.id = r.branch_id join leave_types t on t.id = r.leave_type_id
            where r.id = ? and r.organization_id = ?
            """,
            requestId,
            user.organizationId());
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found");
    }
    return rows.get(0);
  }

  private Map<String, Object> employeeForSelf(AuthenticatedUser user) {
    List<Map<String, Object>> employees =
        jdbc.queryForList(
            """
            select e.id, e.employee_number as "employeeNumber",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   e.primary_branch_id as "branchId", b.name as "branchName"
            from employees e
            join branches b on b.id = e.primary_branch_id
            where e.organization_id = ? and e.active = true
              and b.branch_type in ('STORE', 'SHOP', 'HOTEL') and e.user_id = ?
            """,
            user.organizationId(),
            user.userId());
    if (employees.isEmpty()) {
      employees =
          jdbc.queryForList(
              """
              select e.id, e.employee_number as "employeeNumber",
                     concat(e.first_name, ' ', e.last_name) as "employeeName",
                     e.primary_branch_id as "branchId", b.name as "branchName"
              from employees e
              join branches b on b.id = e.primary_branch_id
              where e.organization_id = ? and e.active = true and e.user_id is null
                and lower(e.email) = lower(?) and b.branch_type in ('STORE', 'SHOP', 'HOTEL')
              """,
              user.organizationId(),
              user.email());
    }
    if (employees.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Your account is not linked to an active staff profile. Ask HR to add your system account to an employee profile at your work location.");
    }
    if (employees.size() > 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Your account matches more than one staff profile. Ask HR to resolve the duplicate.");
    }
    return employees.get(0);
  }

  private Map<String, Object> attendanceRecord(UUID id, AuthenticatedUser user) {
    return jdbc.queryForMap(
        """
        select a.id, a.employee_id as "employeeId",
               concat(e.first_name, ' ', e.last_name) as "employeeName",
               b.name as "branchName", a.attendance_date as "attendanceDate",
               a.clock_in as "clockIn", a.clock_out as "clockOut", a.status,
               a.worked_minutes as "workedMinutes", a.notes
        from attendance_records a
        join employees e on e.id = a.employee_id
        join branches b on b.id = a.branch_id
        where a.id = ? and a.organization_id = ?
        """,
        id,
        user.organizationId());
  }

  private void requireEmployeeAtBranch(AuthenticatedUser user, UUID employeeId, UUID branchId) {
    boolean exists =
        !jdbc.queryForList(
                """
            select id from employees
            where id = ? and organization_id = ? and primary_branch_id = ? and active = true
            """,
                UUID.class,
                employeeId,
                user.organizationId(),
                branchId)
            .isEmpty();
    if (!exists) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Active employee was not found at this location");
    }
  }

  private void requireHrLocation(AuthenticatedUser user, UUID branchId) {
    boolean valid =
        !jdbc.queryForList(
                """
            select id from branches
            where id = ? and organization_id = ? and status = 'ACTIVE'
              and branch_type in ('STORE', 'SHOP', 'HOTEL')
            """,
                UUID.class,
                branchId,
                user.organizationId())
            .isEmpty();
    if (!valid) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose an active store, shop or hotel");
    }
  }

  private static void appendBranchScope(
      StringBuilder sql, List<Object> args, AuthenticatedUser user) {
    if (user.branchIds().isEmpty()) return;
    sql.append(" and b.id in (");
    sql.append(String.join(",", java.util.Collections.nCopies(user.branchIds().size(), "?")));
    sql.append(")");
    args.addAll(user.branchIds());
  }

  private static void appendReportBranchScope(
      StringBuilder sql,
      List<Object> args,
      AuthenticatedUser user,
      UUID selectedBranchId,
      String branchAlias) {
    if (selectedBranchId != null) {
      sql.append(" and ").append(branchAlias).append(".id = ?");
      args.add(selectedBranchId);
    }
    if (!user.branchIds().isEmpty()) {
      sql.append(" and ")
          .append(branchAlias)
          .append(".id in (")
          .append(String.join(",", java.util.Collections.nCopies(user.branchIds().size(), "?")))
          .append(")");
      args.addAll(user.branchIds());
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record EmployeeRequest(
      UUID userId,
      @NotNull UUID branchId,
      @NotBlank @Size(max = 80) String firstName,
      @Size(max = 80) String middleName,
      @NotBlank @Size(max = 80) String lastName,
      @Email @Size(max = 254) String email,
      @Size(max = 40) String phone,
      @NotBlank @Pattern(regexp = "PERMANENT|CONTRACT|TEMPORARY|INTERN|CASUAL")
          String employmentType,
      @NotNull LocalDate joiningDate,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal basicSalary) {}

  public record EmployeeUpdateRequest(
      @NotNull UUID branchId,
      @NotBlank @Size(max = 80) String firstName,
      @Size(max = 80) String middleName,
      @NotBlank @Size(max = 80) String lastName,
      @Email @Size(max = 254) String email,
      @Size(max = 40) String phone,
      @NotBlank @Pattern(regexp = "PERMANENT|CONTRACT|TEMPORARY|INTERN|CASUAL")
          String employmentType,
      @NotNull LocalDate joiningDate,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal basicSalary) {}

  public record EmployeeActivationRequest(@NotNull Boolean active) {}

  public record AttendanceRequest(
      @NotNull UUID employeeId,
      @NotNull UUID branchId,
      @NotNull LocalDate attendanceDate,
      @NotBlank @Size(max = 32) String status,
      @Size(max = 1000) String notes) {}

  public record LeaveRequest(
      @NotNull UUID employeeId,
      @NotNull UUID branchId,
      @NotNull UUID leaveTypeId,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @NotNull @DecimalMin(value = "0.01") @Digits(integer = 6, fraction = 2)
          BigDecimal requestedDays,
      @Size(max = 2000) String reason) {}

  public record MyLeaveRequest(
      @NotNull UUID leaveTypeId,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @Size(max = 2000) String reason) {}

  public record LeaveTypeRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 40) String code,
      @NotNull @Min(0) Integer defaultDays,
      @NotNull Boolean requiresBalance) {}

  public record LeaveDecision(
      @NotBlank @Size(max = 32) String status, @Size(max = 1000) String approverComments) {}
}
