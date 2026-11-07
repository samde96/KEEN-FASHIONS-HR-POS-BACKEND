package com.company.fashionpos.hr;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class WorkforceController {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Nairobi");

  private final JdbcTemplate jdbc;
  private final AuthenticatedUserService authenticatedUsers;
  private final BranchService branches;

  public WorkforceController(
      JdbcTemplate jdbc, AuthenticatedUserService authenticatedUsers, BranchService branches) {
    this.jdbc = jdbc;
    this.authenticatedUsers = authenticatedUsers;
    this.branches = branches;
  }

  @GetMapping("/workforce")
  public List<Map<String, Object>> workforce(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID branchId,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    if (to.isBefore(from) || to.isAfter(from.plusDays(30))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a schedule range of up to 31 days");
    }
    OffsetDateTime start = from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    OffsetDateTime end = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    StringBuilder sql =
        new StringBuilder(
            """
            select w.id, w.user_id as "userId", e.id as "employeeId",
                   e.employee_number as "employeeNumber",
                   concat(e.first_name, ' ', e.last_name) as "employeeName",
                   w.branch_id as "branchId", b.name as "branchName",
                   w.kind, w.starts_at as "startsAt", w.ends_at as "endsAt",
                   w.break_minutes as "breakMinutes", w.status, w.source, w.note
            from workforce_records w
            join employees e on e.user_id = w.user_id and e.organization_id = w.organization_id
            left join branches b on b.id = w.branch_id
            where w.organization_id = ? and w.kind = 'WORK' and w.starts_at < ?
              and coalesce(w.ends_at, 'infinity'::timestamptz) > ?
            """);
    List<Object> args = new ArrayList<>(List.of(user.organizationId(), end, start));
    if (branchId != null) {
      requireStoreBranch(user, branchId);
      sql.append(" and w.branch_id = ?");
      args.add(branchId);
    } else if (!user.branchIds().isEmpty()) {
      sql.append(" and w.branch_id in (");
      sql.append(String.join(",", java.util.Collections.nCopies(user.branchIds().size(), "?")));
      sql.append(")");
      args.addAll(user.branchIds());
    }
    sql.append(" order by w.starts_at, e.last_name, e.first_name");
    return jdbc.queryForList(sql.toString(), args.toArray());
  }

  @PostMapping("/workforce")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> createShift(
      @Valid @RequestBody WorkforceRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    validateShift(user, request);
    ensureNoOverlap(user, request, null);
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into workforce_records (
          id, organization_id, request_key, user_id, branch_id, kind, starts_at, ends_at,
          break_minutes, status, source, note, created_at, updated_at, version
        ) values (?, ?, ?, ?, ?, 'WORK', ?, ?, ?, 'APPROVED', 'ADMIN', ?, now(), now(), 0)
        """,
        id,
        user.organizationId(),
        UUID.randomUUID(),
        request.userId(),
        request.branchId(),
        request.startsAt(),
        request.endsAt(),
        request.breakMinutes(),
        normalizedNote(request.note()));
    return shift(id, user);
  }

  @PutMapping("/workforce/{recordId}")
  @Transactional
  public Map<String, Object> updateShift(
      @PathVariable UUID recordId,
      @Valid @RequestBody WorkforceRequest request,
      java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> existing = existingShift(recordId, user);
    requireShiftBranchScope(user, (UUID) existing.get("branchId"));
    if (!"ADMIN".equals(existing.get("source")) || "CANCELLED".equals(existing.get("status"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This shift can no longer be changed");
    }
    validateShift(user, request);
    ensureNoOverlap(user, request, recordId);
    jdbc.update(
        """
        update workforce_records
        set user_id = ?, branch_id = ?, starts_at = ?, ends_at = ?, break_minutes = ?,
            note = ?, updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        request.userId(),
        request.branchId(),
        request.startsAt(),
        request.endsAt(),
        request.breakMinutes(),
        normalizedNote(request.note()),
        recordId,
        user.organizationId());
    return shift(recordId, user);
  }

  @PatchMapping("/workforce/{recordId}/cancel")
  @Transactional
  public Map<String, Object> cancelShift(
      @PathVariable UUID recordId, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    Map<String, Object> existing = existingShift(recordId, user);
    requireShiftBranchScope(user, (UUID) existing.get("branchId"));
    if (!"ADMIN".equals(existing.get("source")) || "CANCELLED".equals(existing.get("status"))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This shift can no longer be cancelled");
    }
    jdbc.update(
        """
        update workforce_records
        set status = 'CANCELLED', updated_at = now(), version = version + 1
        where id = ? and organization_id = ?
        """,
        recordId,
        user.organizationId());
    return shift(recordId, user);
  }

  @GetMapping("/operating-hours")
  public List<Map<String, Object>> operatingHours(
      @RequestParam UUID branchId, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    requireStoreBranch(user, branchId);
    List<Map<String, Object>> saved =
        jdbc.queryForList(
            """
            select day_of_week as "dayOfWeek", opens_at as "opensAt",
                   closes_at as "closesAt", closed
            from location_operating_hours
            where organization_id = ? and branch_id = ?
            order by day_of_week
            """,
            user.organizationId(),
            branchId);
    Map<Integer, Map<String, Object>> days = new LinkedHashMap<>();
    for (int day = 1; day <= 7; day++) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("dayOfWeek", day);
      row.put("opensAt", null);
      row.put("closesAt", null);
      row.put("closed", true);
      days.put(day, row);
    }
    for (Map<String, Object> day : saved) {
      days.put(((Number) day.get("dayOfWeek")).intValue(), day);
    }
    return new ArrayList<>(days.values());
  }

  @PutMapping("/operating-hours")
  @Transactional
  public List<Map<String, Object>> saveOperatingHours(
      @Valid @RequestBody OperatingHoursRequest request, java.security.Principal principal) {
    AuthenticatedUser user = authenticatedUsers.current(principal);
    requireStoreBranch(user, request.branchId());
    if (request.days().size() != 7) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Provide one entry for every day of the week");
    }
    Set<Integer> seenDays = new HashSet<>();
    for (OperatingHoursDay day : request.days()) {
      if (!seenDays.add(day.dayOfWeek())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Each weekday must appear only once");
      }
      if (!day.closed()
          && (day.opensAt() == null
              || day.closesAt() == null
              || !day.closesAt().isAfter(day.opensAt()))) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Open days require a closing time later than opening time");
      }
    }
    if (seenDays.size() != 7) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide all seven weekdays");
    }
    for (OperatingHoursDay day : request.days()) {
      jdbc.update(
          """
          insert into location_operating_hours (
            id, organization_id, branch_id, day_of_week, opens_at, closes_at,
            closed, created_at, updated_at, version
          ) values (?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
          on conflict (branch_id, day_of_week) do update
          set opens_at = excluded.opens_at, closes_at = excluded.closes_at,
              closed = excluded.closed, updated_at = now(),
              version = location_operating_hours.version + 1
          """,
          UUID.randomUUID(),
          user.organizationId(),
          request.branchId(),
          day.dayOfWeek(),
          day.closed() ? null : day.opensAt(),
          day.closed() ? null : day.closesAt(),
          day.closed());
    }
    return operatingHours(request.branchId(), principal);
  }

  private void validateShift(AuthenticatedUser user, WorkforceRequest request) {
    requireStoreBranch(user, request.branchId());
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Shift end must be after its start");
    }
    long durationMinutes =
        java.time.Duration.between(request.startsAt(), request.endsAt()).toMinutes();
    if (request.breakMinutes() > durationMinutes) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Break duration cannot exceed the shift duration");
    }
    boolean eligible =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (
                  select 1 from employees e
                  join users u on u.id = e.user_id and u.organization_id = e.organization_id
                  join user_branch_assignments assignment on assignment.user_id = u.id
                  where e.organization_id = ? and e.user_id = ? and e.active = true
                    and u.status = 'ACTIVE' and assignment.branch_id = ?
                )
                """,
                Boolean.class,
                user.organizationId(),
                request.userId(),
                request.branchId()));
    if (!eligible) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Choose an active staff profile whose system account is assigned to this location");
    }
  }

  private void ensureNoOverlap(
      AuthenticatedUser user, WorkforceRequest request, UUID excludedRecordId) {
    String sql =
        """
        select exists (
          select 1 from workforce_records
          where organization_id = ? and user_id = ? and kind = 'WORK'
            and status in ('PENDING', 'APPROVED')
            and starts_at < ? and coalesce(ends_at, 'infinity'::timestamptz) > ?
        """;
    boolean overlaps;
    if (excludedRecordId == null) {
      overlaps =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  sql + ")",
                  Boolean.class,
                  user.organizationId(),
                  request.userId(),
                  request.endsAt(),
                  request.startsAt()));
    } else {
      overlaps =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  sql + " and id <> ?)",
                  Boolean.class,
                  user.organizationId(),
                  request.userId(),
                  request.endsAt(),
                  request.startsAt(),
                  excludedRecordId));
    }
    if (overlaps) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This employee already has an overlapping shift");
    }
  }

  private void requireStoreBranch(AuthenticatedUser user, UUID branchId) {
    branches.requireBranchAccess(user, branchId);
    boolean store =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (
                  select 1 from branches
                  where id = ? and organization_id = ? and status = 'ACTIVE'
                    and branch_type in ('STORE', 'SHOP')
                )
                """,
                Boolean.class,
                branchId,
                user.organizationId()));
    if (!store) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an active store or shop");
    }
  }

  private void requireShiftBranchScope(AuthenticatedUser user, UUID branchId) {
    if (branchId != null) {
      branches.requireBranchAccess(user, branchId);
    } else if (!user.branchIds().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found");
    }
  }

  private Map<String, Object> existingShift(UUID id, AuthenticatedUser user) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select id, branch_id as "branchId", source, status
            from workforce_records where id = ? and organization_id = ? and kind = 'WORK'
            """,
            id,
            user.organizationId());
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found");
    }
    return rows.get(0);
  }

  private Map<String, Object> shift(UUID id, AuthenticatedUser user) {
    return jdbc.queryForMap(
        """
        select w.id, w.user_id as "userId", e.id as "employeeId",
               e.employee_number as "employeeNumber",
               concat(e.first_name, ' ', e.last_name) as "employeeName",
               w.branch_id as "branchId", b.name as "branchName",
               w.kind, w.starts_at as "startsAt", w.ends_at as "endsAt",
               w.break_minutes as "breakMinutes", w.status, w.source, w.note
        from workforce_records w
        join employees e on e.user_id = w.user_id and e.organization_id = w.organization_id
        left join branches b on b.id = w.branch_id
        where w.id = ? and w.organization_id = ?
        """,
        id,
        user.organizationId());
  }

  private static String normalizedNote(String note) {
    return note == null ? "" : note.trim();
  }

  public record WorkforceRequest(
      @NotNull UUID userId,
      @NotNull UUID branchId,
      @NotNull OffsetDateTime startsAt,
      @NotNull OffsetDateTime endsAt,
      @Min(0) @Max(1440) int breakMinutes,
      @Size(max = 500) String note) {}

  public record OperatingHoursRequest(
      @NotNull UUID branchId,
      @NotNull @Size(min = 7, max = 7) List<@Valid OperatingHoursDay> days) {}

  public record OperatingHoursDay(
      @Min(1) @Max(7) int dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {}
}
