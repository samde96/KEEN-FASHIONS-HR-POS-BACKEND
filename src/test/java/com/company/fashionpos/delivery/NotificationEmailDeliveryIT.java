package com.company.fashionpos.delivery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.company.fashionpos.operations.*;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "app.daily-summary.scheduler-enabled=false", "app.notifications.scheduler-enabled=false",
      "app.mpesa.enabled=false", "app.mpesa.scheduler-enabled=false",
      "app.security.admin-email=notification-test@example.test",
          "app.security.admin-password=test-password-only"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FASHIONPOS_TEST_DB_URL", matches = ".+")
class NotificationEmailDeliveryIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("FASHIONPOS_TEST_DB_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("FASHIONPOS_TEST_DB_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("FASHIONPOS_TEST_DB_PASSWORD"));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PosPreferencesService preferences;
  @Autowired DailySalesService reports;
  @Autowired MockMvc http;
  DocumentDeliveryService email;
  NotificationEmailDelivery delivery;
  TransactionTemplate tx;
  UUID org, userId, branch;
  AuthenticatedUser user;

  @BeforeEach
  void fixture() {
    // Isolate the worker from other test organizations and their unsent messages.
    jdbc.update("update pos_preferences set email_enabled=false");
    jdbc.update("update notification_email_deliveries set status='FAILED' where status='PENDING'");
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now()+interval '1 day' where status='PENDING'");
    org = UUID.randomUUID();
    branch = UUID.randomUUID();
    jdbc.update(
        "insert into organizations(id,name,currency_code,time_zone,status) values (?,'Notification Business','KES','Africa/Nairobi','ACTIVE')",
        org);
    jdbc.update(
        "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,'Town','TOWN','Africa/Nairobi','ACTIVE')",
        branch,
        org);
    userId = recipient(org, "owner-" + org + "@example.test", "admin:manage", null);
    user =
        new AuthenticatedUser(
            userId,
            "owner-" + org + "@example.test",
            "Owner",
            org,
            Set.of(),
            Set.of("ADMIN"),
            Set.of("Admin"),
            Set.of("admin:manage"));
    preferences.save(
        user, new PosPreferences(true, true, true, List.of(userId), 2880, 10, "Africa/Nairobi"));
    email = mock(DocumentDeliveryService.class);
    when(email.configuration())
        .thenReturn(
            new DocumentDeliveryService.EmailConfiguration(
                "Resend", true, "alerts@example.test", List.of()));
    delivery = new NotificationEmailDelivery(jdbc, email);
    tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
  }

  private int queue() {
    return tx.execute(status -> delivery.queueNewNotifications());
  }

  private boolean deliver() {
    return tx.execute(status -> delivery.deliverNext());
  }

  private UUID recipient(
      UUID organization, String address, String permission, UUID assignedBranch) {
    UUID id = UUID.randomUUID(), role = UUID.randomUUID();
    jdbc.update(
        "insert into users(id,organization_id,email,display_name,status) values (?,?,?,'Recipient','ACTIVE')",
        id,
        organization,
        address);
    jdbc.update(
        "insert into roles(id,organization_id,name,role_key) values (?,?,?,?)",
        role,
        organization,
        "Recipient " + role,
        role.toString());
    jdbc.update("insert into user_roles(user_id,role_id) values (?,?)", id, role);
    jdbc.update(
        "insert into role_permissions(role_id,permission_id) select ?,id from permissions where code=?",
        role,
        permission);
    if (assignedBranch != null)
      jdbc.update(
          "insert into user_branch_assignments(id,user_id,branch_id) values (gen_random_uuid(),?,?)",
          id,
          assignedBranch);
    return id;
  }

  private UUID notification(UUID target, UUID shop, String type) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into notifications(id,organization_id,branch_id,user_id,notification_type,title,message,source_module,status) values (?,?,?,?,?,'Stock needs attention','Stock needs attention\nCheck the current stock count.','inventory','UNREAD')",
        id,
        org,
        shop,
        target,
        type);
    return id;
  }

  @Test
  void copiesTheExactInboxMessageAndRetriesWithTheSamePayloadAndKey() {
    UUID notification = notification(userId, branch, "LOW_STOCK");
    assertThat(queue()).isEqualTo(1);
    assertThat(queue()).isZero();
    UUID id =
        jdbc.queryForObject(
            "select id from notification_email_deliveries where notification_id=?",
            UUID.class,
            notification);
    String key = "notification/" + id;
    String address = "owner-" + org + "@example.test";
    String message = "Stock needs attention\nCheck the current stock count.";
    doThrow(new IllegalStateException("temporary provider failure"))
        .doNothing()
        .when(email)
        .sendSummary(address, "Stock needs attention", message, key);
    deliver();
    assertThat(
            jdbc.queryForObject(
                "select status from notifications where id=?", String.class, notification))
        .isEqualTo("UNREAD");
    assertThat(
            jdbc.queryForObject(
                "select status from notification_email_deliveries where id=?", String.class, id))
        .isEqualTo("PENDING");
    jdbc.update(
        "update notifications set message='Updated after enqueue' where id=?", notification);
    jdbc.update("update notification_email_deliveries set next_attempt_at=now() where id=?", id);
    deliver();
    verify(email, times(2)).sendSummary(address, "Stock needs attention", message, key);
    assertThat(
            jdbc.queryForObject(
                "select status from notification_email_deliveries where id=?", String.class, id))
        .isEqualTo("SENT");
    assertThat(deliver()).isFalse();
  }

  @Test
  void respectsRecipientBranchAndNotificationPermissions() {
    UUID otherBranch = UUID.randomUUID();
    jdbc.update(
        "insert into branches(id,organization_id,name,code,time_zone,status) values (?,?,'Other','OTHER','Africa/Nairobi','ACTIVE')",
        otherBranch,
        org);
    UUID restricted =
        recipient(org, "restricted-" + org + "@example.test", "notifications:view", otherBranch);
    recipient(org, "denied-" + org + "@example.test", "sales:view", null);
    UUID targeted = notification(userId, branch, "APPROVAL");
    UUID shop = notification(null, branch, "LOW_STOCK");
    UUID global = notification(null, null, "SYSTEM_ALERT");
    notification(userId, branch, "DAILY_SALES_SUMMARY");
    assertThat(queue()).isEqualTo(4);
    assertThat(
            jdbc.queryForList(
                "select user_id from notification_email_deliveries where notification_id=?",
                UUID.class,
                targeted))
        .containsExactly(userId);
    assertThat(
            jdbc.queryForList(
                "select user_id from notification_email_deliveries where notification_id=?",
                UUID.class,
                shop))
        .containsExactly(userId);
    assertThat(
            jdbc.queryForList(
                "select user_id from notification_email_deliveries where notification_id=?",
                UUID.class,
                global))
        .containsExactlyInAnyOrder(userId, restricted);
  }

  @Test
  void doesNotSendHistoricalAlertsWhenEmailCopiesAreEnabled() {
    jdbc.update("update pos_preferences set email_enabled=false where organization_id=?", org);
    UUID old = notification(userId, branch, "LOW_STOCK");
    jdbc.update("update notifications set created_at=now()-interval '1 day' where id=?", old);
    assertThat(queue()).isZero();
    preferences.save(
        user, new PosPreferences(true, true, true, List.of(userId), 2880, 10, "Africa/Nairobi"));
    UUID recent = notification(userId, branch, "LOW_STOCK");
    assertThat(queue()).isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "select notification_id from notification_email_deliveries where user_id=?",
                UUID.class,
                userId))
        .containsExactly(recent);
  }

  @Test
  void rechecksAccessAndStopsRetriesBeforeProviderIdempotencyExpires() {
    UUID notice = notification(userId, branch, "LOW_STOCK");
    queue();
    jdbc.update("update users set status='DISABLED' where id=?", userId);
    deliver();
    assertThat(
            jdbc.queryForObject(
                "select last_error from notification_email_deliveries where notification_id=?",
                String.class,
                notice))
        .isEqualTo("Recipient no longer has notification access");
    verify(email, never()).sendSummary(anyString(), anyString(), anyString(), anyString());
    jdbc.update("update users set status='ACTIVE' where id=?", userId);
    UUID expired = notification(userId, branch, "LOW_STOCK");
    queue();
    jdbc.update(
        "update notification_email_deliveries set created_at=? where notification_id=?",
        Timestamp.from(Instant.now().minusSeconds(24 * 3600)),
        expired);
    deliver();
    assertThat(
            jdbc.queryForObject(
                "select last_error from notification_email_deliveries where notification_id=?",
                String.class,
                expired))
        .isEqualTo("Retry window expired");
    verify(email, never()).sendSummary(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void missingConfigurationLeavesInboxAndEmailQueueIntact() {
    UUID notice = notification(userId, branch, "LOW_STOCK");
    queue();
    when(email.configuration())
        .thenReturn(
            new DocumentDeliveryService.EmailConfiguration(
                "Resend", false, "", List.of("RESEND_API_KEY")));
    assertThat(deliver()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select status from notifications where id=?", String.class, notice))
        .isEqualTo("UNREAD");
    assertThat(
            jdbc.queryForObject(
                "select attempts from notification_email_deliveries where notification_id=?",
                Integer.class,
                notice))
        .isZero();
    verify(email, never()).sendSummary(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void emailOnlyDailyPreferenceStillCreatesExactlyOneMatchingInboxMessage() {
    preferences.save(
        user, new PosPreferences(true, false, true, List.of(userId), 2880, 10, "Africa/Nairobi"));
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries where report_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(2);
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now()+interval '1 day' where report_id=? and channel='IN_APP'",
        report.id());
    var daily = new DailySummaryDelivery(jdbc, reports, email);
    tx.execute(status -> daily.deliverNext());
    var inbox =
        jdbc.queryForMap(
            "select title,message from notifications where source_record_id=?", report.id());
    verify(email)
        .sendSummary(
            eq(user.email()),
            eq((String) inbox.get("title")),
            eq((String) inbox.get("message")),
            anyString());
    jdbc.update(
        "update daily_summary_deliveries set next_attempt_at=now() where report_id=? and channel='IN_APP'",
        report.id());
    tx.execute(status -> daily.deliverNext());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notifications where source_record_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(1);
    assertThat(queue()).isZero();
  }

  @Test
  void onlyAdministratorsCanInspectEmailConfiguration() throws Exception {
    http.perform(
            MockMvcRequestBuilders.get("/api/v1/document-delivery/email/status")
                .with(
                    SecurityMockMvcRequestPostProcessors.user("owner")
                        .authorities(new SimpleGrantedAuthority("admin:manage"))))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.provider").value("Resend"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.apiKey").doesNotExist());
    http.perform(
            MockMvcRequestBuilders.get("/api/v1/document-delivery/email/status")
                .with(
                    SecurityMockMvcRequestPostProcessors.user("viewer")
                        .authorities(new SimpleGrantedAuthority("notifications:view"))))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
  }

  @Test
  void notificationRecipientIsTrimmedPersistedAndCanBeCleared() {
    var saved =
        preferences.save(
            user,
            new PosPreferences(
                true,
                true,
                true,
                List.of(userId),
                2880,
                10,
                "Africa/Nairobi",
                " reports@example.test "));
    assertThat(saved.notificationEmail()).isEqualTo("reports@example.test");
    assertThat(preferences.get(org).notificationEmail()).isEqualTo("reports@example.test");
    preferences.save(
        user,
        new PosPreferences(true, true, true, List.of(userId), 2880, 10, "Africa/Nairobi", ""));
    assertThat(preferences.get(org).notificationEmail()).isEmpty();
  }

  @Test
  void sharedNotificationInboxReceivesOneEmailForMultipleAuthorizedUsers() {
    preferences.save(
        user,
        new PosPreferences(
            true, true, true, List.of(userId), 2880, 10, "Africa/Nairobi", "reports@example.test"));
    recipient(org, "manager-" + org + "@example.test", "notifications:view", null);
    UUID notice = notification(null, branch, "LOW_STOCK");
    assertThat(queue()).isEqualTo(1);
    assertThat(queue()).isZero();
    UUID id =
        jdbc.queryForObject(
            "select id from notification_email_deliveries where notification_id=?",
            UUID.class,
            notice);
    deliver();
    verify(email)
        .sendSummary(
            "reports@example.test",
            "Stock needs attention",
            "Stock needs attention\nCheck the current stock count.",
            "notification/" + id);
    assertThat(deliver()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select status from notifications where id=?", String.class, notice))
        .isEqualTo("UNREAD");
  }

  @Test
  void sharedInboxReceivesOneDailySummaryWhileEachSelectedUserKeepsTheirInboxEntry() {
    UUID manager = recipient(org, "manager-" + org + "@example.test", "reports:view", null);
    preferences.save(
        user,
        new PosPreferences(
            true,
            true,
            true,
            List.of(userId, manager),
            2880,
            10,
            "Africa/Nairobi",
            "reports@example.test"));
    var report = reports.generateForUser(user, branch, LocalDate.of(2026, 10, 20));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_summary_deliveries where report_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(3);
    var daily = new DailySummaryDelivery(jdbc, reports, email);
    for (int i = 0; i < 3; i++) tx.execute(status -> daily.deliverNext());
    verify(email, times(1))
        .sendSummary(
            eq("reports@example.test"),
            anyString(),
            eq(DailySummaryDelivery.message(report)),
            anyString());
    assertThat(
            jdbc.queryForList(
                "select user_id from notifications where source_record_id=?",
                UUID.class,
                report.id()))
        .containsExactlyInAnyOrder(userId, manager);
  }

  @Test
  void preferencesApiRejectsInvalidNotificationEmail() throws Exception {
    String payload =
        """
        {"dailySummaryEnabled":true,"inAppEnabled":true,"emailEnabled":true,
         "recipientIds":[],"inactivityMinutes":2880,"longEntryMinutes":10,
         "timeZone":"Africa/Nairobi","notificationEmail":"invalid-address"}
        """;
    http.perform(
            MockMvcRequestBuilders.put("/api/v1/pos-preferences")
                .contentType("application/json")
                .content(payload)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(
                    SecurityMockMvcRequestPostProcessors.user("owner")
                        .authorities(new SimpleGrantedAuthority("admin:manage"))))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }
}
