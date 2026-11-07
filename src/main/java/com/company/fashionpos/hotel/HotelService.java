package com.company.fashionpos.hotel;

import static com.company.fashionpos.hotel.HotelModels.*;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.audit.AuditEventService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class HotelService {
  private final JdbcTemplate jdbc;
  private final BranchService branches;
  private final AuditEventService audit;
  private final ObjectMapper json;
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final String BOOKING_SQL =
      """
      select b.*, r.number room_number,
        coalesce((select sum(o.total) from hotel_orders o where o.booking_id=b.id and o.status<>'CANCELLED'),0) food_total
      from hotel_bookings b join hotel_rooms r on r.id=b.room_id
      where b.organization_id=?
      """;

  public HotelService(
      JdbcTemplate jdbc, BranchService branches, AuditEventService audit, ObjectMapper json) {
    this.jdbc = jdbc;
    this.branches = branches;
    this.audit = audit;
    this.json = json;
  }

  private void permission(AuthenticatedUser user, String... codes) {
    if (user.branchIds().isEmpty()
        && !user.hasPermission("admin:manage")
        && !user.hasPermission("hotel:manage")
        && user.isHotelSalesManager())
      throw new AccessDeniedException("Hotel staff must be assigned to a location");
    if (user.isCashier()
        || !(user.hasPermission("admin:manage")
            || (user.hasPermission("hotel:manage")
                && Arrays.stream(codes).anyMatch(c -> !c.equals("hotel:reports")))
            || Arrays.stream(codes).anyMatch(user::hasPermission))) {
      throw new AccessDeniedException("Hotel permission required");
    }
  }

  private void scope(AuthenticatedUser user, UUID branch) {
    permission(user, "hotel:view");
    branches.requireBranchAccess(user, branch);
  }

  // All hotel mutations take the same branch lock before reading availability, stock or balances.
  // This also makes check-out, room charges, payments and duplicate retries atomic with one
  // another.
  private void lock(AuthenticatedUser user, UUID branch) {
    lock(user, branch, "hotel:manage");
  }

  private void lock(AuthenticatedUser user, UUID branch, String permission) {
    permission(user, permission);
    branches.requireBranchAccess(user, branch);
    var statuses =
        jdbc.queryForList(
            "select status from branches where id=? and organization_id=? for update",
            String.class,
            branch,
            user.organizationId());
    if (statuses.isEmpty() || !statuses.get(0).equals("ACTIVE"))
      throw new IllegalArgumentException("Select an active hotel location");
  }

  private LocalDate today(UUID branch) {
    String zone =
        jdbc.queryForObject("select time_zone from branches where id=?", String.class, branch);
    return LocalDate.now(ZoneId.of(zone));
  }

  public Workspace workspace(AuthenticatedUser user, UUID branch) {
    scope(user, branch);
    return new Workspace(
        today(branch),
        rooms(user, branch),
        bookings(user, branch),
        items(user, branch),
        menu(user, branch),
        orders(user, branch, true),
        movements(user, branch));
  }

  @Transactional
  public Checkout checkout(AuthenticatedUser user, UUID branch) {
    permission(user, "hotel:sell", "hotel:view");
    branches.requireBranchAccess(user, branch);
    syncAccountWaiters(user.organizationId(), branch);
    var guests =
        jdbc.query(
            """
        select b.id,r.number,b.guest_name from hotel_bookings b join hotel_rooms r on r.id=b.room_id
        where b.organization_id=? and b.branch_id=? and b.status='CHECKED_IN' order by r.number
        """,
            (r, n) ->
                new GuestOption(uuid(r, "id"), r.getString("number"), r.getString("guest_name")),
            user.organizationId(),
            branch);
    return new Checkout(
        today(branch),
        menu(user, branch),
        items(user, branch).stream().filter(Item::sellable).toList(),
        guests,
        orders(user, branch, true),
        waiters(user, branch).stream().filter(Waiter::active).toList(),
        receiptSettingsForBranch(user, branch));
  }

  private void administrator(AuthenticatedUser user) {
    if (!user.hasPermission("admin:manage"))
      throw new AccessDeniedException("Administrator access required");
  }

  public List<Waiter> listWaiters(AuthenticatedUser user, UUID branch) {
    administrator(user);
    branches.requireBranchAccess(user, branch);
    return jdbc.query(
        """
        select * from hotel_waiters
        where organization_id=? and branch_id=?
        order by lower(name),id
        """,
        this::readWaiter,
        user.organizationId(),
        branch);
  }

  private List<Waiter> waiters(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        "select * from hotel_waiters where organization_id=? and branch_id=? order by lower(name),id",
        this::readWaiter,
        user.organizationId(),
        branch);
  }

  private void syncAccountWaiters(UUID organizationId, UUID branch) {
    jdbc.update(
        """
        update hotel_waiters waiter set active=false
        where waiter.organization_id=? and waiter.branch_id=? and waiter.user_id is not null
          and not exists (
            select 1 from users account
            where account.id=waiter.user_id and account.organization_id=?
              and account.status='ACTIVE'
          )
        """,
        organizationId,
        branch,
        organizationId);
    jdbc.update(
        """
        update hotel_waiters waiter set name=account.display_name
        from users account
        where waiter.organization_id=? and waiter.branch_id=? and waiter.user_id=account.id
          and account.organization_id=? and account.status='ACTIVE'
        """,
        organizationId,
        branch,
        organizationId);
    jdbc.update(
        """
        update hotel_waiters waiter set user_id=account.id,active=true
        from users account
        where waiter.organization_id=? and waiter.branch_id=? and waiter.user_id is null
          and account.organization_id=? and account.status='ACTIVE'
          and lower(waiter.name)=lower(account.display_name)
          and not exists (
            select 1 from hotel_waiters linked
            where linked.organization_id=? and linked.branch_id=? and linked.user_id=account.id
          )
        """,
        organizationId,
        branch,
        organizationId,
        organizationId,
        branch);
    jdbc.update(
        """
        insert into hotel_waiters(id,organization_id,branch_id,name,active,user_id)
        select gen_random_uuid(),account.organization_id,location.branch_id,
          account.display_name,true,account.id
        from users account
        join user_roles assignment on assignment.user_id=account.id
        join roles role on role.id=assignment.role_id
          and role.organization_id=account.organization_id and role.role_key='WAITER'
        join user_branch_assignments location
          on location.user_id=account.id and location.branch_id=?
        where account.organization_id=? and account.status='ACTIVE'
          and not exists (
            select 1 from hotel_waiters linked
            where linked.organization_id=? and linked.branch_id=? and linked.user_id=account.id
          )
          and not exists (
            select 1 from hotel_waiter_exclusions exclusion
            where exclusion.organization_id=? and exclusion.branch_id=? and exclusion.user_id=account.id
          )
        on conflict do nothing
        """,
        branch,
        organizationId,
        organizationId,
        branch,
        organizationId,
        branch);
  }

  @Transactional
  public Waiter saveWaiter(AuthenticatedUser user, UUID id, WaiterRequest request) {
    administrator(user);
    lock(user, request.branchId());
    UUID target = id == null ? UUID.randomUUID() : id;
    String name;
    if (request.userId() != null) {
      List<String> accountNames =
          jdbc.queryForList(
              """
              select account.display_name from users account
              where account.id=? and account.organization_id=? and account.status='ACTIVE'
              """,
              String.class,
              request.userId(),
              user.organizationId());
      if (accountNames.isEmpty()) {
        throw new IllegalArgumentException("Choose an active user account");
      }
      name = accountNames.get(0);
    } else {
      if (request.name() == null || request.name().isBlank()) {
        throw new IllegalArgumentException("Choose a Waiter account");
      }
      name = request.name().trim();
    }

    if (id != null) {
      List<UUID> existingUserIds =
          jdbc.queryForList(
              "select user_id from hotel_waiters where organization_id=? and branch_id=? and id=?",
              UUID.class,
              user.organizationId(),
              request.branchId(),
              id);
      if (existingUserIds.isEmpty()) {
        throw new EntityNotFoundException("Waiter not found at this hotel");
      }
      UUID existingUserId = existingUserIds.get(0);
      if (existingUserId != null && !existingUserId.equals(request.userId())) {
        throw new IllegalArgumentException("Waiter account assignment cannot be changed");
      }
      if (existingUserId != null) {
        jdbc.update(
            "update hotel_waiters set active=? where id=? and organization_id=?",
            request.active(),
            id,
            user.organizationId());
      } else {
        linkOrUpdateManualWaiter(user, request, id, name);
      }
      audit.record(
          user,
          request.branchId(),
          "HOTEL_WAITER_SAVED",
          "HOTEL_WAITER",
          id,
          Map.of("name", name, "active", request.active()));
      return waiters(user, request.branchId()).stream()
          .filter(w -> w.id().equals(id))
          .findFirst()
          .orElseThrow();
    }

    if (request.userId() != null) {
      List<UUID> linkedWaiters =
          jdbc.queryForList(
              "select id from hotel_waiters where organization_id=? and branch_id=? and user_id=?",
              UUID.class,
              user.organizationId(),
              request.branchId(),
              request.userId());
      jdbc.update(
          """
          delete from hotel_waiter_exclusions
          where organization_id=? and branch_id=? and user_id=?
          """,
          user.organizationId(),
          request.branchId(),
          request.userId());
      if (!linkedWaiters.isEmpty()) {
        target = linkedWaiters.get(0);
        jdbc.update(
            "update hotel_waiters set name=?,active=? where id=?", name, request.active(), target);
        return waiterById(user, request.branchId(), target);
      }
      List<UUID> matchingManualWaiters =
          jdbc.queryForList(
              """
              select id from hotel_waiters
              where organization_id=? and branch_id=? and user_id is null and lower(name)=lower(?)
              order by id limit 1
              """,
              UUID.class,
              user.organizationId(),
              request.branchId(),
              name);
      if (!matchingManualWaiters.isEmpty()) {
        target = matchingManualWaiters.get(0);
        jdbc.update(
            "update hotel_waiters set user_id=?,name=?,active=? where id=?",
            request.userId(),
            name,
            request.active(),
            target);
      } else {
        if (count(
                "select count(*) from hotel_waiters where organization_id=? and branch_id=? and lower(name)=lower(?)",
                user.organizationId(),
                request.branchId(),
                name)
            > 0) {
          throw new IllegalArgumentException(
              "A waiter with this name already exists at this hotel");
        }
        jdbc.update(
            "insert into hotel_waiters(id,organization_id,branch_id,name,active,user_id) values (?,?,?,?,?,?)",
            target,
            user.organizationId(),
            request.branchId(),
            name,
            request.active(),
            request.userId());
      }
    } else {
      if (count(
              "select count(*) from hotel_waiters where organization_id=? and branch_id=? and lower(name)=lower(?)",
              user.organizationId(),
              request.branchId(),
              name)
          > 0) {
        throw new IllegalArgumentException("A waiter with this name already exists at this hotel");
      }
      jdbc.update(
          "insert into hotel_waiters(id,organization_id,branch_id,name,active) values (?,?,?,?,?)",
          target,
          user.organizationId(),
          request.branchId(),
          name,
          request.active());
    }
    audit.record(
        user,
        request.branchId(),
        "HOTEL_WAITER_SAVED",
        "HOTEL_WAITER",
        target,
        Map.of("name", name, "active", request.active()));
    return waiterById(user, request.branchId(), target);
  }

  @Transactional
  public void deleteWaiter(AuthenticatedUser user, UUID id) {
    administrator(user);
    Waiter waiter =
        jdbc
            .query(
                "select * from hotel_waiters where id=? and organization_id=?",
                this::readWaiter,
                id,
                user.organizationId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Waiter not found at this hotel"));
    lock(user, waiter.branchId());
    if (waiter.userId() != null) {
      jdbc.update(
          """
          insert into hotel_waiter_exclusions(organization_id,branch_id,user_id)
          values (?,?,?) on conflict do nothing
          """,
          user.organizationId(),
          waiter.branchId(),
          waiter.userId());
    }
    jdbc.update(
        """
        update hotel_orders set waiter_id=null
        where organization_id=? and branch_id=? and waiter_id=?
        """,
        user.organizationId(),
        waiter.branchId(),
        id);
    jdbc.update(
        "delete from hotel_waiters where id=? and organization_id=?", id, user.organizationId());
    audit.record(
        user,
        waiter.branchId(),
        "HOTEL_WAITER_DELETED",
        "HOTEL_WAITER",
        id,
        Map.of("name", waiter.name()));
  }

  private Waiter waiterById(AuthenticatedUser user, UUID branch, UUID waiterId) {
    return waiters(user, branch).stream()
        .filter(waiter -> waiter.id().equals(waiterId))
        .findFirst()
        .orElseThrow();
  }

  private void linkOrUpdateManualWaiter(
      AuthenticatedUser user, WaiterRequest request, UUID id, String name) {
    if (request.userId() == null) {
      jdbc.update(
          "update hotel_waiters set name=?,active=? where id=? and organization_id=? and user_id is null",
          name,
          request.active(),
          id,
          user.organizationId());
      return;
    }
    jdbc.update(
        "update hotel_waiters set name=?,active=?,user_id=? where id=? and organization_id=? and user_id is null",
        name,
        request.active(),
        request.userId(),
        id,
        user.organizationId());
  }

  public ReceiptSettings receiptSettings(AuthenticatedUser user, UUID branch) {
    administrator(user);
    branches.requireBranchAccess(user, branch);
    return receiptSettingsForBranch(user, branch);
  }

  private ReceiptSettings receiptSettingsForBranch(AuthenticatedUser user, UUID branch) {
    var settings =
        jdbc.query(
            "select * from hotel_receipt_settings where organization_id=? and branch_id=?",
            (r, n) ->
                new ReceiptSettings(
                    r.getString("hotel_name"),
                    r.getString("till_number"),
                    r.getString("contact_phone")),
            user.organizationId(),
            branch);
    if (!settings.isEmpty()) return settings.get(0);
    return new ReceiptSettings(
        jdbc.queryForObject(
            "select name from organizations where id=?", String.class, user.organizationId()),
        "",
        "");
  }

  @Transactional
  public ReceiptSettings saveReceiptSettings(
      AuthenticatedUser user, ReceiptSettingsRequest request) {
    administrator(user);
    lock(user, request.branchId());
    jdbc.update(
        """
        insert into hotel_receipt_settings(branch_id,organization_id,hotel_name,till_number,contact_phone)
        values (?,?,?,?,?) on conflict (branch_id) do update set hotel_name=excluded.hotel_name,
          till_number=excluded.till_number,contact_phone=excluded.contact_phone
        """,
        request.branchId(),
        user.organizationId(),
        request.hotelName().trim(),
        request.tillNumber().replace(" ", ""),
        request.contactPhone().trim());
    audit.record(
        user,
        request.branchId(),
        "HOTEL_RECEIPT_SETTINGS_SAVED",
        "HOTEL_LOCATION",
        request.branchId(),
        Map.of());
    return receiptSettingsForBranch(user, request.branchId());
  }

  private List<MenuItem> menu(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        "select * from hotel_menu_items where organization_id=? and branch_id=? order by category,name",
        this::readMenu,
        user.organizationId(),
        branch);
  }

  private MenuItem menuItem(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(
            "select * from hotel_menu_items where organization_id=? and id=?",
            this::readMenu,
            user.organizationId(),
            id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Food menu item not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  @Transactional
  public MenuItem saveMenuItem(AuthenticatedUser user, UUID id, MenuItemRequest request) {
    lock(user, request.branchId());
    if (id != null && !menuItem(user, id).branchId().equals(request.branchId()))
      throw new IllegalArgumentException("Menu items cannot move between locations");
    UUID target = id == null ? UUID.randomUUID() : id;
    if (count(
            "select count(*) from hotel_menu_items where organization_id=? and branch_id=? and lower(name)=lower(?) and id<>?",
            user.organizationId(),
            request.branchId(),
            request.name().trim(),
            target)
        > 0) throw new IllegalArgumentException("A dish with this name already exists");
    if (id == null)
      jdbc.update(
          """
        insert into hotel_menu_items(id,organization_id,branch_id,name,category,description,price,available)
        values (?,?,?,?,?,?,?,?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          request.name().trim(),
          request.category().trim(),
          clean(request.description()),
          request.price(),
          request.available());
    else
      jdbc.update(
          "update hotel_menu_items set name=?,category=?,description=?,price=?,available=? where id=? and organization_id=?",
          request.name().trim(),
          request.category().trim(),
          clean(request.description()),
          request.price(),
          request.available(),
          target,
          user.organizationId());
    audit.record(
        user,
        request.branchId(),
        "HOTEL_MENU_SAVED",
        "HOTEL_MENU_ITEM",
        target,
        Map.of("name", request.name()));
    return menuItem(user, target);
  }

  private List<Room> rooms(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        "select * from hotel_rooms where organization_id=? and branch_id=? order by number",
        this::readRoom,
        user.organizationId(),
        branch);
  }

  private Room room(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(
            "select * from hotel_rooms where organization_id=? and id=?",
            this::readRoom,
            user.organizationId(),
            id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Room not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  @Transactional
  public Room saveRoom(AuthenticatedUser user, UUID id, RoomRequest request) {
    lock(user, request.branchId());
    String number = request.number().trim().toUpperCase(Locale.ROOT);
    Room previous = id == null ? null : room(user, id);
    if (previous != null && !previous.branchId().equals(request.branchId()))
      throw new IllegalArgumentException("A room cannot move to another location");
    UUID target = id == null ? UUID.randomUUID() : id;
    if (count(
            "select count(*) from hotel_rooms where organization_id=? and branch_id=? and upper(number)=? and id<>?",
            user.organizationId(),
            request.branchId(),
            number,
            target)
        > 0) throw new IllegalArgumentException("This room number already exists at this location");
    if (count(
            "select count(*) from hotel_bookings where room_id=? and status in ('RESERVED','CHECKED_IN') and guests>? and departure>?",
            target,
            request.capacity(),
            today(request.branchId()))
        > 0) throw new IllegalArgumentException("Room capacity must cover its active reservations");
    if (!request.status().equals("READY")
        && count(
                "select count(*) from hotel_bookings where room_id=? and status='CHECKED_IN'",
                target)
            > 0)
      throw new IllegalArgumentException(
          "Check out the current guest before changing room readiness");
    if (request.status().equals("MAINTENANCE")
        && count(
                "select count(*) from hotel_bookings where room_id=? and status='RESERVED' and departure>?",
                target,
                today(request.branchId()))
            > 0)
      throw new IllegalArgumentException(
          "Move or cancel future reservations before closing this room for maintenance");
    if (previous == null)
      jdbc.update(
          """
        insert into hotel_rooms(id,organization_id,branch_id,number,room_type,capacity,nightly_rate,status)
        values (?,?,?,?,?,?,?,?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          number,
          request.roomType().trim(),
          request.capacity(),
          request.nightlyRate(),
          request.status());
    else
      jdbc.update(
          """
        update hotel_rooms set number=?,room_type=?,capacity=?,nightly_rate=?,status=?
        where id=? and organization_id=?
        """,
          number,
          request.roomType().trim(),
          request.capacity(),
          request.nightlyRate(),
          request.status(),
          target,
          user.organizationId());
    audit.record(
        user,
        request.branchId(),
        "HOTEL_ROOM_SAVED",
        "HOTEL_ROOM",
        target,
        Map.of("number", number));
    return room(user, target);
  }

  private List<Booking> bookings(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        BOOKING_SQL + " and b.branch_id=? order by b.arrival desc,b.created_at desc",
        this::readBooking,
        user.organizationId(),
        branch);
  }

  private Booking booking(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(BOOKING_SQL + " and b.id=?", this::readBooking, user.organizationId(), id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Reservation not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  @Transactional
  public Booking saveBooking(AuthenticatedUser user, UUID id, BookingRequest request) {
    lock(user, request.branchId());
    Command command =
        id == null
            ? command(user, request.branchId(), request.requestId(), "BOOKING", request)
            : null;
    if (command != null && command.replay()) return booking(user, command.target());
    UUID target = id == null ? command.target() : id;
    Booking previous = id == null ? null : booking(user, id);
    if (previous != null
        && (!previous.branchId().equals(request.branchId())
            || !previous.status().equals("RESERVED")))
      throw new IllegalArgumentException("Only a reserved stay at this location can be edited");
    Room selected = room(user, request.roomId());
    if (!selected.branchId().equals(request.branchId()))
      throw new IllegalArgumentException("Select a room at this hotel location");
    if (request.arrival().isBefore(today(request.branchId()))
        || !request.departure().isAfter(request.arrival())
        || ChronoUnit.DAYS.between(request.arrival(), request.departure()) > 366)
      throw new IllegalArgumentException(
          "Choose a stay of 1 to 366 nights starting today or later");
    if (request.guests() > selected.capacity())
      throw new IllegalArgumentException("The guest count exceeds this room's capacity");
    if (selected.status().equals("MAINTENANCE"))
      throw new IllegalArgumentException("This room is closed for maintenance");
    available(user, selected.id(), target, request.arrival(), request.departure());
    BigDecimal rate =
        request.nightlyRate() == null ? selected.nightlyRate() : request.nightlyRate();
    BigDecimal total =
        money(
            rate.multiply(
                BigDecimal.valueOf(
                    ChronoUnit.DAYS.between(request.arrival(), request.departure()))));
    if (previous != null && total.compareTo(previous.paid()) < 0)
      throw new IllegalArgumentException(
          "Refund the excess payment before reducing this reservation's total");
    if (previous == null)
      jdbc.update(
          """
        insert into hotel_bookings(id,organization_id,branch_id,room_id,reference,guest_name,guest_phone,
          guest_email,guests,arrival,departure,nightly_rate,status,notes)
        values (?,?,?,?,?,?,?,?,?,?,?,?,'RESERVED',?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          selected.id(),
          reference("STAY", target),
          request.guestName().trim(),
          clean(request.guestPhone()),
          clean(request.guestEmail()),
          request.guests(),
          request.arrival(),
          request.departure(),
          rate,
          clean(request.notes()));
    else
      jdbc.update(
          """
        update hotel_bookings set room_id=?,guest_name=?,guest_phone=?,guest_email=?,guests=?,arrival=?,departure=?,nightly_rate=?,notes=?
        where id=? and organization_id=?
        """,
          selected.id(),
          request.guestName().trim(),
          clean(request.guestPhone()),
          clean(request.guestEmail()),
          request.guests(),
          request.arrival(),
          request.departure(),
          rate,
          clean(request.notes()),
          target,
          user.organizationId());
    audit.record(
        user,
        request.branchId(),
        previous == null ? "HOTEL_RESERVED" : "HOTEL_RESERVATION_UPDATED",
        "HOTEL_BOOKING",
        target,
        Map.of("room", selected.number()));
    return booking(user, target);
  }

  private void available(
      AuthenticatedUser user, UUID room, UUID excluding, LocalDate arrival, LocalDate departure) {
    if (count(
            """
        select count(*) from hotel_bookings where organization_id=? and room_id=? and id<>?
          and status in ('RESERVED','CHECKED_IN') and arrival<? and departure>?
        """,
            user.organizationId(),
            room,
            excluding,
            departure,
            arrival)
        > 0)
      throw new IllegalArgumentException("This room is already booked for part of these dates");
  }

  @Transactional
  public Booking transition(AuthenticatedUser user, UUID id, String action) {
    Booking initial = booking(user, id);
    lock(user, initial.branchId());
    Booking stay = booking(user, id);
    switch (action) {
      case "check-in" -> {
        if (stay.status().equals("CHECKED_IN")) return stay;
        if (!stay.status().equals("RESERVED"))
          throw new IllegalArgumentException("Only a reservation can be checked in");
        LocalDate date = today(stay.branchId());
        if (stay.arrival().isAfter(date) || !stay.departure().isAfter(date))
          throw new IllegalArgumentException(
              "Check-in is available during the reserved stay dates");
        if (!room(user, stay.roomId()).status().equals("READY"))
          throw new IllegalArgumentException(
              "Mark the room ready after cleaning or maintenance before check-in");
        if (count(
                "select count(*) from hotel_bookings where room_id=? and status='CHECKED_IN'",
                stay.roomId())
            > 0)
          throw new IllegalArgumentException(
              "The previous guest must check out before this room can be occupied");
        available(user, stay.roomId(), id, date, stay.departure());
        jdbc.update(
            "update hotel_bookings set status='CHECKED_IN',checked_in_at=? where id=?",
            Timestamp.from(Instant.now()),
            id);
      }
      case "check-out" -> {
        if (stay.status().equals("CHECKED_OUT")) return stay;
        if (!stay.status().equals("CHECKED_IN"))
          throw new IllegalArgumentException("Only a checked-in guest can check out");
        if (stay.balance().compareTo(ZERO) != 0)
          throw new IllegalArgumentException(
              "Settle the guest's outstanding balance before check-out");
        jdbc.update(
            "update hotel_bookings set status='CHECKED_OUT',checked_out_at=? where id=?",
            Timestamp.from(Instant.now()),
            id);
        jdbc.update("update hotel_rooms set status='CLEANING' where id=?", stay.roomId());
      }
      case "cancel" -> {
        if (stay.status().equals("CANCELLED")) return stay;
        if (!stay.status().equals("RESERVED"))
          throw new IllegalArgumentException("Only an unoccupied reservation can be cancelled");
        if (stay.paid().compareTo(ZERO) != 0)
          throw new IllegalArgumentException(
              "Record a refund of the deposit before cancelling this reservation");
        jdbc.update("update hotel_bookings set status='CANCELLED' where id=?", id);
      }
      default -> throw new IllegalArgumentException("Unknown hotel action");
    }
    audit.record(
        user,
        stay.branchId(),
        "HOTEL_" + action.toUpperCase(Locale.ROOT).replace('-', '_'),
        "HOTEL_BOOKING",
        id,
        Map.of("reference", stay.reference()));
    return booking(user, id);
  }

  @Transactional
  public Booking pay(AuthenticatedUser user, UUID id, PaymentRequest request) {
    Booking initial = booking(user, id);
    lock(user, initial.branchId());
    Command command =
        command(user, initial.branchId(), request.requestId(), "PAYMENT", List.of(id, request));
    if (command.replay()) return booking(user, id);
    Booking stay = booking(user, id);
    if (!Set.of("RESERVED", "CHECKED_IN").contains(stay.status()))
      throw new IllegalArgumentException("Payments can only change for an active stay");
    boolean refund = request.kind().equals("REFUND");
    if (request.amount().compareTo(refund ? stay.paid() : stay.balance()) > 0)
      throw new IllegalArgumentException(
          refund ? "Refund exceeds the payments held" : "Payment exceeds the outstanding balance");
    requirePaymentReference(request.method(), request.reference());
    jdbc.update(
        """
        insert into hotel_payments(id,organization_id,branch_id,booking_id,amount,kind,method,reference)
        values (?,?,?,?,?,?,?,?)
        """,
        command.target(),
        user.organizationId(),
        stay.branchId(),
        id,
        request.amount(),
        request.kind(),
        request.method(),
        clean(request.reference()));
    audit.record(
        user,
        stay.branchId(),
        "HOTEL_" + request.kind(),
        "HOTEL_BOOKING",
        id,
        Map.of("amount", request.amount(), "method", request.method()));
    return booking(user, id);
  }

  private List<Item> items(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        "select * from hotel_items where organization_id=? and branch_id=? order by name",
        this::readItem,
        user.organizationId(),
        branch);
  }

  private Item item(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(
            "select * from hotel_items where organization_id=? and id=?",
            this::readItem,
            user.organizationId(),
            id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Hotel inventory item not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  private HotelExpense expense(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(
            "select * from hotel_expenses where organization_id=? and id=?",
            this::readExpense,
            user.organizationId(),
            id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Hotel expense not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  private HotelAsset asset(AuthenticatedUser user, UUID id) {
    var rows =
        jdbc.query(
            "select * from hotel_assets where organization_id=? and id=?",
            this::readAsset,
            user.organizationId(),
            id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Hotel asset not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  @Transactional
  public Item saveItem(AuthenticatedUser user, UUID id, ItemRequest request) {
    lock(user, request.branchId());
    if (id != null && !item(user, id).branchId().equals(request.branchId()))
      throw new IllegalArgumentException("An inventory item cannot move to another location");
    if (request.sellable()
        && (!request.category().equals("PRODUCT") || request.price().signum() <= 0))
      throw new IllegalArgumentException(
          "Only stocked products with a positive selling price can be sold from inventory");
    UUID target = id == null ? UUID.randomUUID() : id;
    if (count(
            "select count(*) from hotel_items where organization_id=? and branch_id=? and lower(name)=lower(?) and id<>?",
            user.organizationId(),
            request.branchId(),
            request.name().trim(),
            target)
        > 0)
      throw new IllegalArgumentException("An item with this name already exists at this hotel");
    if (id != null
        && !item(user, id).unit().equals(request.unit().trim())
        && count("select count(*) from hotel_stock_movements where item_id=?", id) > 0)
      throw new IllegalArgumentException(
          "Keep the existing unit for items with stock history; create a new item for a different unit");
    if (id == null)
      jdbc.update(
          """
        insert into hotel_items(id,organization_id,branch_id,name,category,unit,sellable,price,reorder_level,active)
        values (?,?,?,?,?,?,?,?,?,?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          request.name().trim(),
          request.category(),
          request.unit().trim(),
          request.sellable(),
          request.price(),
          request.reorderLevel(),
          request.active());
    else
      jdbc.update(
          """
        update hotel_items set name=?,category=?,unit=?,sellable=?,price=?,reorder_level=?,active=? where id=? and organization_id=?
        """,
          request.name().trim(),
          request.category(),
          request.unit().trim(),
          request.sellable(),
          request.price(),
          request.reorderLevel(),
          request.active(),
          target,
          user.organizationId());
    audit.record(
        user,
        request.branchId(),
        "HOTEL_ITEM_SAVED",
        "HOTEL_ITEM",
        target,
        Map.of("name", request.name()));
    return item(user, target);
  }

  @Transactional
  public Item adjustStock(AuthenticatedUser user, UUID id, StockRequest request) {
    Item initial = item(user, id);
    lock(user, initial.branchId());
    Command command =
        command(user, initial.branchId(), request.requestId(), "STOCK", List.of(id, request));
    if (command.replay()) return item(user, id);
    Item current = item(user, id);
    if (!current.active())
      throw new IllegalArgumentException("Reactivate this inventory item before changing stock");
    BigDecimal delta =
        request.kind().equals("RECEIPT") ? request.quantity() : request.quantity().negate();
    move(user, current, command.target(), delta, request.kind(), request.note().trim());
    audit.record(
        user,
        current.branchId(),
        "HOTEL_STOCK_" + request.kind(),
        "HOTEL_ITEM",
        id,
        Map.of("quantity", delta, "note", request.note()));
    return item(user, id);
  }

  private void move(
      AuthenticatedUser user, Item item, UUID id, BigDecimal delta, String kind, String note) {
    BigDecimal balance = item.stock().add(delta);
    if (balance.signum() < 0)
      throw new IllegalArgumentException("Insufficient stock for " + item.name());
    if (balance.compareTo(new BigDecimal("99999999999.999")) > 0)
      throw new IllegalArgumentException("Stock quantity is too large");
    jdbc.update(
        "update hotel_items set stock=? where id=? and organization_id=?",
        balance,
        item.id(),
        user.organizationId());
    jdbc.update(
        """
        insert into hotel_stock_movements(id,organization_id,branch_id,item_id,quantity,balance,kind,note)
        values (?,?,?,?,?,?,?,?)
        """,
        id,
        user.organizationId(),
        item.branchId(),
        item.id(),
        delta,
        balance,
        kind,
        note);
  }

  private List<StockMovement> movements(AuthenticatedUser user, UUID branch) {
    return jdbc.query(
        """
        select m.*, i.name item_name from hotel_stock_movements m join hotel_items i on i.id=m.item_id
        where m.organization_id=? and m.branch_id=? order by m.created_at desc,m.id limit 100
        """,
        (r, n) ->
            new StockMovement(
                uuid(r, "id"),
                uuid(r, "item_id"),
                r.getString("item_name"),
                r.getBigDecimal("quantity"),
                r.getBigDecimal("balance"),
                r.getString("kind"),
                r.getString("note"),
                instant(r, "created_at")),
        user.organizationId(),
        branch);
  }

  public List<HotelExpense> expenses(AuthenticatedUser user, UUID branch) {
    permission(user, "hotel:manage");
    branches.requireBranchAccess(user, branch);
    return jdbc.query(
        "select * from hotel_expenses where organization_id=? and branch_id=? order by expense_date desc,created_at desc",
        this::readExpense,
        user.organizationId(),
        branch);
  }

  @Transactional
  public HotelExpense saveExpense(AuthenticatedUser user, UUID id, HotelExpenseRequest request) {
    lock(user, request.branchId());
    HotelExpense previous = id == null ? null : expense(user, id);
    if (previous != null && !previous.branchId().equals(request.branchId()))
      throw new IllegalArgumentException("An expense cannot move to another location");

    UUID target;
    if (id == null) {
      Command command = command(user, request.branchId(), request.requestId(), "EXPENSE", request);
      target = command.target();
      if (command.replay()) return expense(user, target);
      jdbc.update(
          """
        insert into hotel_expenses(id,organization_id,branch_id,expense_date,category,description,vendor,amount,payment_method,reference,notes,created_by)
        values (?,?,?,?,?,?,?,?,?,?,?,?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          request.expenseDate(),
          request.category(),
          request.description().trim(),
          clean(request.vendor()),
          money(request.amount()),
          request.paymentMethod(),
          clean(request.reference()),
          clean(request.notes()),
          user.userId());
    } else {
      target = id;
      jdbc.update(
          """
        update hotel_expenses set expense_date=?,category=?,description=?,vendor=?,amount=?,payment_method=?,reference=?,notes=?
        where id=? and organization_id=? and branch_id=?
        """,
          request.expenseDate(),
          request.category(),
          request.description().trim(),
          clean(request.vendor()),
          money(request.amount()),
          request.paymentMethod(),
          clean(request.reference()),
          clean(request.notes()),
          target,
          user.organizationId(),
          request.branchId());
    }
    audit.record(
        user,
        request.branchId(),
        previous == null ? "HOTEL_EXPENSE_RECORDED" : "HOTEL_EXPENSE_UPDATED",
        "HOTEL_EXPENSE",
        target,
        Map.of("description", request.description(), "amount", request.amount().toPlainString()));
    return expense(user, target);
  }

  public List<HotelAsset> assets(AuthenticatedUser user, UUID branch) {
    permission(user, "hotel:manage");
    branches.requireBranchAccess(user, branch);
    return jdbc.query(
        "select * from hotel_assets where organization_id=? and branch_id=? order by name,id",
        this::readAsset,
        user.organizationId(),
        branch);
  }

  @Transactional
  public HotelAsset saveAsset(AuthenticatedUser user, UUID id, HotelAssetRequest request) {
    lock(user, request.branchId());
    if (id != null && !asset(user, id).branchId().equals(request.branchId()))
      throw new IllegalArgumentException("An asset cannot move to another location");
    UUID target = id == null ? UUID.randomUUID() : id;
    if (id == null) {
      jdbc.update(
          """
        insert into hotel_assets(id,organization_id,branch_id,name,category,quantity,purchase_date,purchase_cost,location,status,notes)
        values (?,?,?,?,?,?,?,?,?,?,?)
        """,
          target,
          user.organizationId(),
          request.branchId(),
          request.name().trim(),
          request.category(),
          request.quantity(),
          request.purchaseDate(),
          money(request.purchaseCost()),
          clean(request.location()),
          request.status(),
          clean(request.notes()));
    } else {
      jdbc.update(
          """
        update hotel_assets set name=?,category=?,quantity=?,purchase_date=?,purchase_cost=?,location=?,status=?,notes=?,updated_at=now()
        where id=? and organization_id=? and branch_id=?
        """,
          request.name().trim(),
          request.category(),
          request.quantity(),
          request.purchaseDate(),
          money(request.purchaseCost()),
          clean(request.location()),
          request.status(),
          clean(request.notes()),
          target,
          user.organizationId(),
          request.branchId());
    }
    audit.record(
        user,
        request.branchId(),
        id == null ? "HOTEL_ASSET_REGISTERED" : "HOTEL_ASSET_UPDATED",
        "HOTEL_ASSET",
        target,
        Map.of("name", request.name(), "quantity", request.quantity()));
    return asset(user, target);
  }

  @Transactional
  public FoodOrder sell(AuthenticatedUser user, OrderRequest request) {
    lock(user, request.branchId(), "hotel:sell");
    Command command = command(user, request.branchId(), request.requestId(), "ORDER", request);
    if (command.replay()) return order(user, command.target());
    Waiter waiter =
        waiters(user, request.branchId()).stream()
            .filter(w -> w.id().equals(request.waiterId()) && w.active())
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Select an active waiter at this hotel"));
    ReceiptSettings settings = receiptSettingsForBranch(user, request.branchId());
    Booking stay = null;
    if (request.method().equals("ROOM")) {
      if (request.bookingId() == null)
        throw new IllegalArgumentException("Select a checked-in guest to charge a room");
      stay = booking(user, request.bookingId());
      if (!stay.branchId().equals(request.branchId()) || !stay.status().equals("CHECKED_IN"))
        throw new IllegalArgumentException("Room charges require a checked-in guest at this hotel");
    } else {
      if (request.bookingId() != null)
        throw new IllegalArgumentException("Only room charges can be attached to a stay");
      if (!request.method().equals("UNPAID"))
        requirePaymentReference(request.method(), request.paymentReference());
    }
    record LineKey(UUID id, String type) {}
    Map<LineKey, BigDecimal> quantities = new LinkedHashMap<>();
    for (OrderLineRequest line : request.lines())
      quantities.merge(
          new LineKey(line.itemId(), line.itemType()), line.quantity(), BigDecimal::add);
    List<OrderLine> lines = new ArrayList<>();
    BigDecimal total = ZERO;
    for (var entry : quantities.entrySet()) {
      String name;
      BigDecimal price;
      if (entry.getKey().type().equals("FOOD")) {
        MenuItem dish = menuItem(user, entry.getKey().id());
        if (!dish.branchId().equals(request.branchId()) || !dish.available())
          throw new IllegalArgumentException("Choose an available food item at this hotel");
        name = dish.name();
        price = dish.price();
      } else {
        Item selected = item(user, entry.getKey().id());
        if (!selected.branchId().equals(request.branchId())
            || !selected.sellable()
            || !selected.active())
          throw new IllegalArgumentException("Choose an active stocked product at this hotel");
        if (selected.stock().compareTo(entry.getValue()) < 0)
          throw new IllegalArgumentException("Insufficient stock for " + selected.name());
        name = selected.name();
        price = selected.price();
      }
      BigDecimal lineTotal = money(price.multiply(entry.getValue()));
      if (lineTotal.signum() <= 0)
        throw new IllegalArgumentException("Order line total must be at least 0.01");
      lines.add(
          new OrderLine(
              entry.getKey().id(),
              entry.getKey().type(),
              name,
              entry.getValue(),
              price,
              lineTotal));
      total = money(total.add(lineTotal));
    }
    String ref = reference("FOOD", command.target());
    String customer = stay == null ? clean(request.customerName()) : stay.guestName();
    if (customer.isBlank()) customer = "Walk-in customer";
    jdbc.update(
        """
        insert into hotel_orders(id,organization_id,branch_id,booking_id,reference,customer_name,total,method,payment_reference,
          operator_id,waiter_id,served_by_name,table_number,service_type,notes,status,hotel_name,till_number,contact_phone,paid_at)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        command.target(),
        user.organizationId(),
        request.branchId(),
        stay == null ? null : stay.id(),
        ref,
        customer,
        total,
        request.method(),
        clean(request.paymentReference()),
        user.userId(),
        waiter.id(),
        waiter.name(),
        clean(request.tableNumber()),
        request.serviceType(),
        clean(request.notes()),
        request.method().equals("UNPAID") ? "OPEN" : "CLOSED",
        settings.hotelName(),
        settings.tillNumber(),
        settings.contactPhone(),
        Set.of("UNPAID", "ROOM").contains(request.method()) ? null : Timestamp.from(Instant.now()));
    for (int i = 0; i < lines.size(); i++) {
      OrderLine line = lines.get(i);
      jdbc.update(
          """
          insert into hotel_order_lines(id,order_id,item_type,menu_item_id,stock_item_id,item_name,quantity,unit_price,total) values (?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          command.target(),
          line.itemType(),
          line.itemType().equals("FOOD") ? line.itemId() : null,
          line.itemType().equals("PRODUCT") ? line.itemId() : null,
          line.itemName(),
          line.quantity(),
          line.unitPrice(),
          line.total());
      if (line.itemType().equals("PRODUCT"))
        move(
            user,
            item(user, line.itemId()),
            UUID.randomUUID(),
            line.quantity().negate(),
            "SALE",
            ref);
    }
    audit.record(
        user,
        request.branchId(),
        "HOTEL_FOOD_SOLD",
        "HOTEL_ORDER",
        command.target(),
        Map.of("total", total, "method", request.method()));
    return order(user, command.target());
  }

  private static final String ORDER_SQL =
      """
      select o.*,r.number room_number from hotel_orders o
      left join hotel_bookings b on b.id=o.booking_id left join hotel_rooms r on r.id=b.room_id
      where o.organization_id=?
      """;

  private List<FoodOrder> orders(AuthenticatedUser user, UUID branch, boolean limited) {
    return jdbc.query(
        ORDER_SQL
            + " and o.branch_id=? "
            + (limited
                ? """
        and (o.status='OPEN' or o.id in (
          select id from hotel_orders where organization_id=o.organization_id and branch_id=o.branch_id
          order by created_at desc,id limit 100))
        """
                : "")
            + " order by o.created_at desc,o.id",
        this::readOrder,
        user.organizationId(),
        branch);
  }

  public FoodOrder order(AuthenticatedUser user, UUID id) {
    permission(user, "hotel:view", "hotel:sell");
    var rows = jdbc.query(ORDER_SQL + " and o.id=?", this::readOrder, user.organizationId(), id);
    if (rows.isEmpty()) throw new EntityNotFoundException("Food order not found");
    branches.requireBranchAccess(user, rows.get(0).branchId());
    return rows.get(0);
  }

  public List<FoodOrder> bookingOrders(AuthenticatedUser user, UUID id) {
    permission(user, "hotel:view");
    booking(user, id);
    return jdbc.query(
        ORDER_SQL + " and o.booking_id=? order by o.created_at,o.id",
        this::readOrder,
        user.organizationId(),
        id);
  }

  @Transactional
  public FoodOrder settleOrder(AuthenticatedUser user, UUID id, OrderPaymentRequest request) {
    FoodOrder initial = order(user, id);
    lock(user, initial.branchId(), "hotel:sell");
    Command command =
        command(
            user, initial.branchId(), request.requestId(), "ORDER_PAYMENT", List.of(id, request));
    if (command.replay()) return order(user, id);
    FoodOrder current = order(user, id);
    if (!current.method().equals("UNPAID") || current.status().equals("CANCELLED"))
      throw new IllegalArgumentException("Only an unpaid active order can be settled");
    Booking guest = null;
    if (request.method().equals("ROOM")) {
      if (request.bookingId() == null)
        throw new IllegalArgumentException("Select a checked-in guest");
      guest = booking(user, request.bookingId());
      if (!guest.branchId().equals(current.branchId()) || !guest.status().equals("CHECKED_IN"))
        throw new IllegalArgumentException("Select a checked-in guest at this hotel");
    } else {
      if (request.bookingId() != null)
        throw new IllegalArgumentException("Only a room charge can include a guest stay");
      requirePaymentReference(request.method(), request.reference());
    }
    jdbc.update(
        "update hotel_orders set method=?,payment_reference=?,booking_id=?,paid_at=?,status='CLOSED' where id=?",
        request.method(),
        clean(request.reference()),
        guest == null ? null : guest.id(),
        guest == null ? Timestamp.from(Instant.now()) : null,
        id);
    audit.record(
        user,
        current.branchId(),
        "HOTEL_ORDER_PAYMENT",
        "HOTEL_ORDER",
        id,
        Map.of("method", request.method(), "amount", current.total()));
    return order(user, id);
  }

  @Transactional
  public FoodOrder cancelOrder(AuthenticatedUser user, UUID id) {
    FoodOrder initial = order(user, id);
    lock(user, initial.branchId(), "hotel:sell");
    FoodOrder current = order(user, id);
    if (current.status().equals("CANCELLED")) return current;
    if (!current.method().equals("UNPAID"))
      throw new IllegalArgumentException("Only an unpaid order can be cancelled");
    for (OrderLine line : current.lines())
      if (line.itemType().equals("PRODUCT"))
        move(
            user,
            item(user, line.itemId()),
            UUID.randomUUID(),
            line.quantity(),
            "RETURN",
            "Cancelled " + current.reference());
    jdbc.update(
        "update hotel_orders set status='CANCELLED' where id=? and organization_id=?",
        id,
        user.organizationId());
    audit.record(
        user,
        current.branchId(),
        "HOTEL_ORDER_CANCELLED",
        "HOTEL_ORDER",
        id,
        Map.of("reference", current.reference()));
    return order(user, id);
  }

  public Report report(AuthenticatedUser user, UUID branch, LocalDate from, LocalDate to) {
    permission(user, "hotel:reports");
    branches.requireBranchAccess(user, branch);
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days < 1 || days > 366)
      throw new IllegalArgumentException("Select a reporting period of 1 to 366 days");
    ZoneId zone =
        ZoneId.of(
            jdbc.queryForObject("select time_zone from branches where id=?", String.class, branch));
    List<Booking> stays = bookings(user, branch);
    List<FoodOrder> food =
        orders(user, branch, false).stream().filter(o -> !o.status().equals("CANCELLED")).toList();
    List<Booking> completed =
        stays.stream()
            .filter(
                b ->
                    b.status().equals("CHECKED_OUT")
                        && within(b.checkedOutAt().atZone(zone).toLocalDate(), from, to))
            .toList();
    BigDecimal accommodation =
        completed.stream().map(Booking::roomTotal).reduce(ZERO, BigDecimal::add);
    BigDecimal foodRevenue =
        food.stream()
            .filter(o -> within(o.createdAt().atZone(zone).toLocalDate(), from, to))
            .flatMap(o -> o.lines().stream())
            .filter(l -> l.itemType().equals("FOOD"))
            .map(OrderLine::total)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal productRevenue =
        food.stream()
            .filter(o -> within(o.createdAt().atZone(zone).toLocalDate(), from, to))
            .flatMap(o -> o.lines().stream())
            .filter(l -> l.itemType().equals("PRODUCT"))
            .map(OrderLine::total)
            .reduce(ZERO, BigDecimal::add);
    List<HotelExpense> periodExpenses =
        jdbc.query(
            "select * from hotel_expenses where organization_id=? and branch_id=? and expense_date between ? and ? order by expense_date,created_at",
            this::readExpense,
            user.organizationId(),
            branch,
            from,
            to);
    BigDecimal totalExpenses =
        periodExpenses.stream().map(HotelExpense::amount).reduce(ZERO, BigDecimal::add);
    List<Payment> payments =
        jdbc.query(
            "select * from hotel_payments where organization_id=? and branch_id=?",
            this::readPayment,
            user.organizationId(),
            branch);
    BigDecimal collected =
        payments.stream()
            .filter(p -> within(p.createdAt().atZone(zone).toLocalDate(), from, to))
            .map(this::signedPayment)
            .reduce(ZERO, BigDecimal::add)
            .add(
                food.stream()
                    .filter(
                        o ->
                            o.paidAt() != null
                                && within(o.paidAt().atZone(zone).toLocalDate(), from, to))
                    .map(FoodOrder::total)
                    .reduce(ZERO, BigDecimal::add));
    BigDecimal outstanding =
        stays.stream()
            .filter(b -> Set.of("RESERVED", "CHECKED_IN").contains(b.status()))
            .map(Booking::balance)
            .reduce(ZERO, BigDecimal::add)
            .add(
                food.stream()
                    .filter(o -> o.method().equals("UNPAID"))
                    .map(FoodOrder::total)
                    .reduce(ZERO, BigDecimal::add));
    Set<String> bookedDates = new HashSet<>();
    for (Booking stay : stays) {
      if (stay.status().equals("CANCELLED")) continue;
      LocalDate end = stay.departure();
      if (stay.checkedOutAt() != null
          && stay.checkedOutAt().atZone(zone).toLocalDate().isBefore(end))
        end = stay.checkedOutAt().atZone(zone).toLocalDate();
      if (end.isAfter(to.plusDays(1))) end = to.plusDays(1);
      for (LocalDate day = stay.arrival().isBefore(from) ? from : stay.arrival();
          day.isBefore(end);
          day = day.plusDays(1)) bookedDates.add(stay.roomId() + ":" + day);
    }
    long bookedNights = bookedDates.size();
    long capacityNights = rooms(user, branch).size() * days;
    long soldNights = completed.stream().mapToLong(Booking::nights).sum();
    List<DailyRevenue> daily = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      LocalDate day = date;
      BigDecimal roomDay =
          completed.stream()
              .filter(b -> b.checkedOutAt().atZone(zone).toLocalDate().equals(day))
              .map(Booking::roomTotal)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal foodDay =
          food.stream()
              .filter(o -> o.createdAt().atZone(zone).toLocalDate().equals(day))
              .flatMap(o -> o.lines().stream())
              .filter(l -> l.itemType().equals("FOOD"))
              .map(OrderLine::total)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal productsDay =
          food.stream()
              .filter(o -> o.createdAt().atZone(zone).toLocalDate().equals(day))
              .flatMap(o -> o.lines().stream())
              .filter(l -> l.itemType().equals("PRODUCT"))
              .map(OrderLine::total)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal paidDay =
          payments.stream()
              .filter(p -> p.createdAt().atZone(zone).toLocalDate().equals(day))
              .map(this::signedPayment)
              .reduce(ZERO, BigDecimal::add)
              .add(
                  food.stream()
                      .filter(
                          o ->
                              o.paidAt() != null
                                  && o.paidAt().atZone(zone).toLocalDate().equals(day))
                      .map(FoodOrder::total)
                      .reduce(ZERO, BigDecimal::add));
      BigDecimal expenseDay =
          periodExpenses.stream()
              .filter(expense -> expense.expenseDate().equals(day))
              .map(HotelExpense::amount)
              .reduce(ZERO, BigDecimal::add);
      daily.add(new DailyRevenue(day, roomDay, foodDay, productsDay, paidDay, expenseDay));
    }
    List<RoomPerformance> performance =
        rooms(user, branch).stream()
            .map(
                r -> {
                  var matching = completed.stream().filter(b -> b.roomId().equals(r.id())).toList();
                  return new RoomPerformance(
                      r.number(),
                      matching.size(),
                      matching.stream().mapToLong(Booking::nights).sum(),
                      matching.stream().map(Booking::roomTotal).reduce(ZERO, BigDecimal::add));
                })
            .toList();
    return new Report(
        from,
        to,
        accommodation,
        foodRevenue,
        productRevenue,
        accommodation.add(foodRevenue).add(productRevenue),
        totalExpenses,
        accommodation.add(foodRevenue).add(productRevenue).subtract(totalExpenses),
        collected,
        outstanding,
        completed.size(),
        soldNights,
        bookedNights,
        capacityNights,
        capacityNights == 0
            ? ZERO
            : BigDecimal.valueOf(bookedNights * 100)
                .divide(BigDecimal.valueOf(capacityNights), 2, RoundingMode.HALF_UP),
        soldNights == 0
            ? ZERO
            : accommodation.divide(BigDecimal.valueOf(soldNights), 2, RoundingMode.HALF_UP),
        daily,
        performance,
        periodExpenses.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    HotelExpense::category,
                    java.util.TreeMap::new,
                    java.util.stream.Collectors.reducing(
                        ZERO, HotelExpense::amount, BigDecimal::add)))
            .entrySet()
            .stream()
            .map(entry -> new ExpenseCategoryTotal(entry.getKey(), entry.getValue()))
            .toList());
  }

  private Room readRoom(ResultSet r, int n) throws SQLException {
    return new Room(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getString("number"),
        r.getString("room_type"),
        r.getInt("capacity"),
        r.getBigDecimal("nightly_rate"),
        r.getString("status"));
  }

  private Booking readBooking(ResultSet r, int n) throws SQLException {
    UUID id = uuid(r, "id");
    var payments =
        jdbc.query(
            "select * from hotel_payments where booking_id=? order by created_at,id",
            this::readPayment,
            id);
    LocalDate arrival = r.getDate("arrival").toLocalDate(),
        departure = r.getDate("departure").toLocalDate();
    long nights = ChronoUnit.DAYS.between(arrival, departure);
    BigDecimal roomTotal = r.getBigDecimal("nightly_rate").multiply(BigDecimal.valueOf(nights));
    BigDecimal total = roomTotal.add(r.getBigDecimal("food_total"));
    BigDecimal paid = payments.stream().map(this::signedPayment).reduce(ZERO, BigDecimal::add);
    return new Booking(
        id,
        uuid(r, "branch_id"),
        uuid(r, "room_id"),
        r.getString("room_number"),
        r.getString("reference"),
        r.getString("guest_name"),
        r.getString("guest_phone"),
        r.getString("guest_email"),
        r.getInt("guests"),
        arrival,
        departure,
        nights,
        r.getBigDecimal("nightly_rate"),
        roomTotal,
        r.getBigDecimal("food_total"),
        total,
        paid,
        r.getString("status").equals("CANCELLED") ? ZERO : total.subtract(paid),
        r.getString("status"),
        r.getString("notes"),
        instant(r, "checked_in_at"),
        instant(r, "checked_out_at"),
        payments);
  }

  private Payment readPayment(ResultSet r, int n) throws SQLException {
    return new Payment(
        uuid(r, "id"),
        uuid(r, "booking_id"),
        r.getBigDecimal("amount"),
        r.getString("kind"),
        r.getString("method"),
        r.getString("reference"),
        instant(r, "created_at"));
  }

  private Item readItem(ResultSet r, int n) throws SQLException {
    return new Item(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getString("name"),
        r.getString("category"),
        r.getString("unit"),
        r.getBoolean("sellable"),
        r.getBigDecimal("price"),
        r.getBigDecimal("stock"),
        r.getBigDecimal("reorder_level"),
        r.getBoolean("active"));
  }

  private HotelExpense readExpense(ResultSet r, int n) throws SQLException {
    return new HotelExpense(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getObject("expense_date", LocalDate.class),
        r.getString("category"),
        r.getString("description"),
        r.getString("vendor"),
        r.getBigDecimal("amount"),
        r.getString("payment_method"),
        r.getString("reference"),
        r.getString("notes"),
        instant(r, "created_at"));
  }

  private HotelAsset readAsset(ResultSet r, int n) throws SQLException {
    return new HotelAsset(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getString("name"),
        r.getString("category"),
        r.getInt("quantity"),
        r.getObject("purchase_date", LocalDate.class),
        r.getBigDecimal("purchase_cost"),
        r.getString("location"),
        r.getString("status"),
        r.getString("notes"));
  }

  private FoodOrder readOrder(ResultSet r, int n) throws SQLException {
    UUID id = uuid(r, "id");
    var lines =
        jdbc.query(
            "select *,coalesce(menu_item_id,stock_item_id) item_id from hotel_order_lines where order_id=? order by item_name,id",
            (row, index) ->
                new OrderLine(
                    uuid(row, "item_id"),
                    row.getString("item_type"),
                    row.getString("item_name"),
                    row.getBigDecimal("quantity"),
                    row.getBigDecimal("unit_price"),
                    row.getBigDecimal("total")),
            id);
    return new FoodOrder(
        id,
        uuid(r, "branch_id"),
        uuid(r, "booking_id"),
        r.getString("room_number"),
        r.getString("reference"),
        r.getString("customer_name"),
        r.getBigDecimal("total"),
        r.getString("method"),
        r.getString("payment_reference"),
        uuid(r, "operator_id"),
        uuid(r, "waiter_id"),
        r.getString("served_by_name"),
        r.getString("table_number"),
        r.getString("service_type"),
        r.getString("notes"),
        r.getString("status"),
        r.getString("hotel_name"),
        r.getString("till_number"),
        r.getString("contact_phone"),
        instant(r, "paid_at"),
        instant(r, "created_at"),
        lines);
  }

  private MenuItem readMenu(ResultSet r, int n) throws SQLException {
    return new MenuItem(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getString("name"),
        r.getString("category"),
        r.getString("description"),
        r.getBigDecimal("price"),
        r.getBoolean("available"));
  }

  private Waiter readWaiter(ResultSet r, int n) throws SQLException {
    return new Waiter(
        uuid(r, "id"),
        uuid(r, "branch_id"),
        r.getString("name"),
        r.getBoolean("active"),
        r.getObject("user_id", UUID.class));
  }

  private record Command(UUID target, boolean replay) {}

  private Command command(
      AuthenticatedUser user, UUID branch, UUID requestId, String kind, Object request) {
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot fingerprint hotel request", e);
    }
    var existing =
        jdbc.queryForList(
            "select * from hotel_commands where organization_id=? and request_id=?",
            user.organizationId(),
            requestId);
    if (!existing.isEmpty()) {
      var row = existing.get(0);
      if (!row.get("branch_id").equals(branch)
          || !row.get("kind").equals(kind)
          || !row.get("request_hash").equals(hash))
        throw new IllegalArgumentException(
            "This request was already used for a different change; refresh and try again");
      return new Command((UUID) row.get("target_id"), true);
    }
    UUID target = UUID.randomUUID();
    jdbc.update(
        "insert into hotel_commands(organization_id,request_id,branch_id,kind,request_hash,target_id) values (?,?,?,?,?,?)",
        user.organizationId(),
        requestId,
        branch,
        kind,
        hash,
        target);
    return new Command(target, false);
  }

  private int count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args));
  }

  private static UUID uuid(ResultSet r, String column) throws SQLException {
    return r.getObject(column, UUID.class);
  }

  private static Instant instant(ResultSet r, String column) throws SQLException {
    Timestamp value = r.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private BigDecimal signedPayment(Payment p) {
    return p.kind().equals("REFUND") ? p.amount().negate() : p.amount();
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static String reference(String prefix, UUID id) {
    return prefix + "-" + id.toString().replace("-", "").toUpperCase(Locale.ROOT);
  }

  private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
    return !date.isBefore(from) && !date.isAfter(to);
  }

  private static BigDecimal money(BigDecimal value) {
    BigDecimal result = value.setScale(2, RoundingMode.HALF_UP);
    if (result.signum() < 0 || result.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw new IllegalArgumentException("Total amount is outside the supported range");
    return result;
  }

  private static void requirePaymentReference(String method, String reference) {
    if (!method.equals("CASH") && clean(reference).isEmpty())
      throw new IllegalArgumentException("Enter the payment transaction reference for " + method);
  }
}
