package com.company.fashionpos.operations;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PosPreferencesService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public PosPreferencesService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public PosPreferences get(UUID organizationId) {
    return jdbc.query(
            """
        select o.time_zone, p.* from organizations o left join pos_preferences p on p.organization_id=o.id where o.id=?
        """,
            (rs, n) ->
                new PosPreferences(
                    rs.getObject("daily_summary_enabled") == null
                        || rs.getBoolean("daily_summary_enabled"),
                    rs.getObject("in_app_enabled") == null || rs.getBoolean("in_app_enabled"),
                    rs.getBoolean("email_enabled"),
                    recipients(rs.getString("recipient_ids")),
                    rs.getObject("inactivity_minutes") == null
                        ? 2880
                        : rs.getInt("inactivity_minutes"),
                    rs.getObject("long_entry_minutes") == null
                        ? 10
                        : rs.getInt("long_entry_minutes"),
                    rs.getString("time_zone"),
                    rs.getString("notification_email")),
            organizationId)
        .get(0);
  }

  @Transactional
  public PosPreferences save(AuthenticatedUser user, PosPreferences p) {
    if (!user.isAdmin()) throw new AccessDeniedException("Administrator access required");
    try {
      ZoneId.of(p.timeZone());
    } catch (java.time.DateTimeException e) {
      throw new IllegalArgumentException("Business timezone is not valid");
    }
    if (p.dailySummaryEnabled() && !p.inAppEnabled() && !p.emailEnabled())
      throw new IllegalArgumentException("Select at least one notification channel");
    for (UUID id : p.recipientIds()) {
      Integer eligible =
          jdbc.queryForObject(
              """
          select count(*) from users u where u.id=? and u.organization_id=? and u.status='ACTIVE'
          and exists (select 1 from user_roles ur join role_permissions rp on rp.role_id=ur.role_id
              join permissions p on p.id=rp.permission_id where ur.user_id=u.id and p.code in ('admin:manage','reports:view'))
          """,
              Integer.class,
              id,
              user.organizationId());
      if (eligible == null || eligible == 0)
        throw new IllegalArgumentException(
            "Recipients must be active report users in this business");
    }
    jdbc.update(
        """
        insert into pos_preferences (organization_id,daily_summary_enabled,in_app_enabled,email_enabled,recipient_ids,inactivity_minutes,long_entry_minutes,notification_email)
        values (?,?,?,?,?,?,?,?) on conflict (organization_id) do update set daily_summary_enabled=excluded.daily_summary_enabled,
        in_app_enabled=excluded.in_app_enabled,email_enabled=excluded.email_enabled,recipient_ids=excluded.recipient_ids,
        inactivity_minutes=excluded.inactivity_minutes,long_entry_minutes=excluded.long_entry_minutes,
        schedule_started_at=case when not pos_preferences.daily_summary_enabled and excluded.daily_summary_enabled then now() else pos_preferences.schedule_started_at end,
        notification_email_started_at=case when (not pos_preferences.email_enabled and excluded.email_enabled)
          or pos_preferences.notification_email<>excluded.notification_email then now() else pos_preferences.notification_email_started_at end,
        notification_email=excluded.notification_email
        """,
        user.organizationId(),
        p.dailySummaryEnabled(),
        p.inAppEnabled(),
        p.emailEnabled(),
        encode(p.recipientIds()),
        p.inactivityMinutes(),
        p.longEntryMinutes(),
        p.notificationEmail());
    jdbc.update(
        "update organizations set time_zone=?, updated_at=now(), version=version+1 where id=?",
        p.timeZone(),
        user.organizationId());
    return p;
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to encode data", e);
    }
  }

  private List<UUID> recipients(String value) {
    try {
      return value == null ? List.of() : json.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid recipient configuration", e);
    }
  }
}
