package com.company.fashionpos.delivery;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEmailDelivery {
  private final JdbcTemplate jdbc;
  private final DocumentDeliveryService email;

  // Match the system inbox's organization, recipient, branch and permission boundaries.
  private static final String ELIGIBLE =
      """
      u.organization_id=n.organization_id and u.status='ACTIVE'
      and (n.user_id is null or n.user_id=u.id)
      and (n.branch_id is null
        or not exists (select 1 from user_branch_assignments a where a.user_id=u.id)
        or exists (select 1 from user_branch_assignments a where a.user_id=u.id and a.branch_id=n.branch_id))
      and exists (select 1 from user_roles ur join role_permissions rp on rp.role_id=ur.role_id
        join permissions permission on permission.id=rp.permission_id
        where ur.user_id=u.id and permission.code in ('admin:manage','notifications:view'))
      """;

  public NotificationEmailDelivery(JdbcTemplate jdbc, DocumentDeliveryService email) {
    this.jdbc = jdbc;
    this.email = email;
  }

  @Transactional
  public int queueNewNotifications() {
    // Daily summaries already have their own durable per-channel outbox.
    return jdbc.update(
        """
        insert into notification_email_deliveries (id,notification_id,user_id,to_email,subject,message,uses_notification_email)
        select gen_random_uuid(),n.id,u.id,coalesce(nullif(p.notification_email,''),u.email),n.title,n.message,p.notification_email<>''
        from notifications n join pos_preferences p on p.organization_id=n.organization_id
        join organizations o on o.id=n.organization_id
        join users u on u.organization_id=n.organization_id
        where p.email_enabled=true and o.status='ACTIVE'
          and n.created_at>=p.notification_email_started_at
          and n.notification_type<>'DAILY_SALES_SUMMARY'
          and not exists (select 1 from notification_email_deliveries d where d.notification_id=n.id
            and (d.user_id=u.id or (p.notification_email<>'' and d.uses_notification_email)))
          and
        """
            + ELIGIBLE
            + """
        order by n.created_at,n.id,u.id limit 200
        on conflict do nothing
        """);
  }

  @Transactional
  public boolean deliverNext() {
    if (!email.configuration().configured()) return false;
    var rows =
        jdbc.queryForList(
            """
        select d.*,p.email_enabled,o.status as organization_status,
          n.created_at>=p.notification_email_started_at as current_period,
          exists (select 1 from users u where u.id=d.user_id
            and ((d.uses_notification_email and p.notification_email=d.to_email)
              or (not d.uses_notification_email and p.notification_email='' and u.email=d.to_email))
            and
        """
                + ELIGIBLE
                + """
          ) as eligible
        from notification_email_deliveries d join notifications n on n.id=d.notification_id
        join pos_preferences p on p.organization_id=n.organization_id
        join organizations o on o.id=n.organization_id
        where d.status='PENDING' and d.next_attempt_at<=now()
        order by d.next_attempt_at,d.id limit 1 for update of d skip locked
        """);
    if (rows.isEmpty()) return false;
    var row = rows.get(0);
    UUID id = (UUID) row.get("id");
    Instant created = ((Timestamp) row.get("created_at")).toInstant();
    String failure = null;
    if (!Boolean.TRUE.equals(row.get("eligible"))
        || !"ACTIVE".equals(row.get("organization_status"))) {
      failure = "Recipient no longer has notification access";
    } else if (!Boolean.TRUE.equals(row.get("email_enabled"))) {
      failure = "Email copies are disabled";
    } else if (!Boolean.TRUE.equals(row.get("current_period"))) {
      failure = "Alert predates current email settings";
    } else if (created.plus(Duration.ofHours(23)).isBefore(Instant.now())) {
      failure = "Retry window expired";
    }
    if (failure != null) {
      jdbc.update(
          "update notification_email_deliveries set status='FAILED',last_error=? where id=?",
          failure,
          id);
      return true;
    }
    int attempts = ((Number) row.get("attempts")).intValue() + 1;
    try {
      email.sendSummary(
          (String) row.get("to_email"),
          (String) row.get("subject"),
          (String) row.get("message"),
          "notification/" + id);
      jdbc.update(
          "update notification_email_deliveries set status='SENT',attempts=?,sent_at=now(),last_error=null where id=?",
          attempts,
          id);
    } catch (IllegalStateException exception) {
      jdbc.update(
          "update notification_email_deliveries set attempts=?,next_attempt_at=?,last_error=? where id=?",
          attempts,
          Timestamp.from(
              Instant.now().plusSeconds(Math.min(3600, 60L << Math.min(attempts - 1, 6)))),
          "Email delivery failed; check Resend configuration and provider status",
          id);
      LoggerFactory.getLogger(getClass()).warn("Notification email delivery {} failed", id);
    }
    return true;
  }
}
