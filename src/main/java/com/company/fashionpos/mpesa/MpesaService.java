package com.company.fashionpos.mpesa;

import com.company.fashionpos.sales.SaleService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MpesaService {
  private final MpesaProperties properties;
  private final MpesaLedger ledger;
  private final MpesaCrypto crypto;
  private final SaleService sales;

  public MpesaService(
      MpesaProperties properties, MpesaLedger ledger, MpesaCrypto crypto, SaleService sales) {
    this.properties = properties;
    this.ledger = ledger;
    this.crypto = crypto;
    this.sales = sales;
  }

  public boolean enabled(AuthenticatedUser user) {
    return properties.enabled() && user.organizationId().equals(properties.organizationId());
  }

  public MpesaDtos.Payment start(
      AuthenticatedUser user, String key, MpesaDtos.StartRequest request) {
    if (!enabled(user))
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "M-Pesa STK Push is not configured for this business");
    if (key == null || !key.matches("[A-Za-z0-9_-]{16,120}"))
      throw new IllegalArgumentException("Use a unique Idempotency-Key of 16 to 120 characters");
    String phone = MpesaCrypto.normalizePhone(request.phoneNumber());
    String saleHash = ledger.saleHash(request.sale());
    String requestHash = MpesaCrypto.sha256(crypto.phoneHash(phone) + ":" + saleHash);
    var replay = ledger.replay(user, key, requestHash);
    if (replay.isPresent()) return replay.get();
    var amount = sales.quoteMpesaAmount(user, request.sale());
    if (amount.stripTrailingZeros().scale() > 0
        || amount.signum() <= 0
        || amount.compareTo(new java.math.BigDecimal("250000")) > 0)
      throw new IllegalArgumentException(
          "M-Pesa requires a whole shilling amount between KES 1 and 250,000");
    return ledger.create(user, request.sale(), key, saleHash, phone, amount);
  }
}
