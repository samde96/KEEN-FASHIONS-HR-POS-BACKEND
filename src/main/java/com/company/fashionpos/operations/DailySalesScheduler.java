package com.company.fashionpos.operations;

import java.time.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    name = "app.daily-summary.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DailySalesScheduler {
  private final JdbcTemplate jdbc;
  private final DailySalesService reports;
  private final DailySummaryDelivery deliveries;
  private final Clock clock;

  public DailySalesScheduler(
      JdbcTemplate jdbc, DailySalesService reports, DailySummaryDelivery deliveries, Clock clock) {
    this.jdbc = jdbc;
    this.reports = reports;
    this.deliveries = deliveries;
    this.clock = clock;
  }

  @Scheduled(cron = "0 * * * * *")
  public void tick() {
    jdbc.update(
        "insert into pos_preferences (organization_id) select id from organizations on conflict (organization_id) do nothing");
    var stores =
        jdbc.queryForList(
            """
        select b.id,b.organization_id,o.time_zone,greatest(p.schedule_started_at,b.created_at) as started
        from branches b join organizations o on o.id=b.organization_id join pos_preferences p on p.organization_id=o.id
        where p.daily_summary_enabled=true and b.status='ACTIVE' and o.status='ACTIVE'
        """);
    for (var store : stores) {
      ZoneId zone = ZoneId.of((String) store.get("time_zone"));
      ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
      LocalDate due =
          now.toLocalTime().isBefore(LocalTime.of(21, 0))
              ? now.toLocalDate().minusDays(1)
              : now.toLocalDate();
      LocalDate start =
          ((java.sql.Timestamp) store.get("started")).toInstant().atZone(zone).toLocalDate();
      var existing =
          new java.util.HashSet<>(
              jdbc.queryForList(
                  "select business_date from daily_sales_reports where organization_id=? and branch_id=?",
                  LocalDate.class,
                  store.get("organization_id"),
                  store.get("id")));
      for (LocalDate date = start; !date.isAfter(due); date = date.plusDays(1)) {
        if (existing.contains(date)) continue;
        try {
          reports.generate((UUID) store.get("organization_id"), (UUID) store.get("id"), date);
        } catch (RuntimeException e) {
          org.slf4j.LoggerFactory.getLogger(getClass())
              .warn(
                  "Daily report generation failed for shop {} on {}: {}",
                  store.get("id"),
                  date,
                  e.getMessage());
        }
      }
    }
  }

  @Scheduled(fixedDelay = 15000, initialDelay = 15000)
  public void deliver() {
    for (int i = 0; i < 50; i++) {
      try {
        if (!deliveries.deliverNext()) break;
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Daily report delivery worker failed", e);
        break;
      }
    }
  }
}
