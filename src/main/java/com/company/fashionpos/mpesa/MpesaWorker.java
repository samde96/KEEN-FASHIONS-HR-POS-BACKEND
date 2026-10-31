package com.company.fashionpos.mpesa;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.mpesa.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MpesaWorker {
  private final MpesaProperties properties;
  private final MpesaLedger ledger;
  private final DarajaClient daraja;

  public MpesaWorker(MpesaProperties properties, MpesaLedger ledger, DarajaClient daraja) {
    this.properties = properties;
    this.ledger = ledger;
    this.daraja = daraja;
  }

  @Scheduled(fixedDelayString = "${app.mpesa.dispatch-delay-ms:250}", scheduler = "mpesaScheduler")
  public void dispatch() {
    if (!properties.enabled()) return;
    ledger
        .claimPush()
        .ifPresent(
            tx -> {
              try {
                var response = daraja.push(tx);
                ledger.submitted(
                    tx.id(),
                    MpesaLedger.identifier(response, "MerchantRequestID"),
                    MpesaLedger.identifier(response, "CheckoutRequestID"));
              } catch (DarajaClient.Rejected rejected) {
                ledger.submissionFailed(tx.id(), true);
              } catch (RuntimeException uncertain) {
                // Never log request bodies, provider errors, phone numbers, tokens or callback
                // URLs.
                LoggerFactory.getLogger(getClass())
                    .warn("M-Pesa submission {} requires reconciliation", tx.id());
                ledger.submissionFailed(tx.id(), false);
              }
            });
  }

  @Scheduled(fixedDelayString = "${app.mpesa.query-delay-ms:1000}", scheduler = "mpesaScheduler")
  public void reconcile() {
    if (!properties.enabled()) return;
    ledger
        .claimQuery()
        .ifPresent(
            tx -> {
              try {
                ledger.queried(tx.id(), daraja.query(tx.checkoutRequestId()));
              } catch (RuntimeException uncertain) {
                /* Keep the durable pending state; querying never resends a push. */
              }
            });
  }
}
