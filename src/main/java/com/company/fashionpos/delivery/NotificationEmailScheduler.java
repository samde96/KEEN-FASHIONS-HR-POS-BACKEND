package com.company.fashionpos.delivery;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    name = "app.notifications.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotificationEmailScheduler {
  private final NotificationEmailDelivery deliveries;

  public NotificationEmailScheduler(NotificationEmailDelivery deliveries) {
    this.deliveries = deliveries;
  }

  @Scheduled(fixedDelay = 15000, initialDelay = 15000)
  public void deliver() {
    try {
      deliveries.queueNewNotifications();
      for (int i = 0; i < 50; i++) {
        if (!deliveries.deliverNext()) break;
      }
    } catch (RuntimeException exception) {
      LoggerFactory.getLogger(getClass()).warn("Notification email worker failed", exception);
    }
  }
}
