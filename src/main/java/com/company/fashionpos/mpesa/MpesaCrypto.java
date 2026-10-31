package com.company.fashionpos.mpesa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MpesaCrypto {
  private final MpesaProperties properties;
  private final SecureRandom random = new SecureRandom();

  public MpesaCrypto(MpesaProperties properties) {
    this.properties = properties;
  }

  public String encryptPhone(String phone) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(phone.getBytes(StandardCharsets.UTF_8));
      byte[] result = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, result, 0, iv.length);
      System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(result);
    } catch (Exception error) {
      throw new IllegalStateException("Unable to protect M-Pesa phone number");
    }
  }

  public String decryptPhone(String encrypted) {
    try {
      byte[] value = Base64.getDecoder().decode(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, value, 0, 12));
      return new String(cipher.doFinal(value, 12, value.length - 12), StandardCharsets.UTF_8);
    } catch (Exception error) {
      throw new IllegalStateException("Unable to read protected M-Pesa phone number");
    }
  }

  private SecretKeySpec key() {
    return new SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKey()), "AES");
  }

  public String phoneHash(String phone) {
    return hmac(Base64.getDecoder().decode(properties.encryptionKey()), "phone:" + phone);
  }

  public String callbackToken(UUID transactionId) {
    return hmac(
        properties.callbackSecret().getBytes(StandardCharsets.UTF_8), transactionId.toString());
  }

  public boolean validCallbackToken(UUID id, String supplied) {
    return supplied != null
        && MessageDigest.isEqual(
            callbackToken(id).getBytes(StandardCharsets.US_ASCII),
            supplied.getBytes(StandardCharsets.US_ASCII));
  }

  private static String hmac(byte[] secret, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("Unable to authenticate M-Pesa data");
    }
  }

  public static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("Unable to fingerprint payment");
    }
  }

  public static String normalizePhone(String value) {
    String phone = value == null ? "" : value.trim();
    if (!phone.matches("(?:0|254|\\+254)[17]\\d{8}"))
      throw new IllegalArgumentException(
          "Enter a Kenyan mobile number such as 0712345678 or +254712345678");
    return "254" + phone.substring(phone.length() - 9);
  }
}
