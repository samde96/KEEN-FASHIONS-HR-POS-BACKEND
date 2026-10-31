package com.company.fashionpos.mpesa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MpesaCallbackFirewall extends OncePerRequestFilter {
  private final MpesaProperties properties;

  public MpesaCallbackFirewall(MpesaProperties properties) {
    this.properties = properties;
  }

  static void validateNetwork(String value) {
    if (value == null || !value.matches("[0-9a-fA-F.:]+(?:/\\d{1,3})?"))
      throw new IllegalArgumentException(
          "M-Pesa allowlists must contain literal IP addresses or CIDRs");
    new IpAddressMatcher(value);
  }

  static boolean matches(List<String> networks, String ip) {
    if (ip == null || !ip.matches("[0-9a-fA-F.:]+")) return false;
    try {
      return networks.stream()
          .filter(s -> !s.isBlank())
          .anyMatch(n -> new IpAddressMatcher(n).matches(ip));
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  boolean allowed(HttpServletRequest request) {
    // Unwrap Spring's ForwardedHeaderFilter: only the actual network peer may supply forwarding
    // headers.
    HttpServletRequest raw = request;
    while (raw instanceof HttpServletRequestWrapper wrapper)
      raw = (HttpServletRequest) wrapper.getRequest();
    String peer = raw.getRemoteAddr();
    if (!matches(properties.trustedProxies(), peer))
      return matches(properties.callbackAllowedIps(), peer);
    String forwarded = raw.getHeader("X-Forwarded-For");
    if (forwarded == null || forwarded.length() > 1024) return false;
    String[] hops = forwarded.split(",", -1);
    if (hops.length > 16) return false;
    for (int i = hops.length - 1; i >= 0; i--) {
      String hop = hops[i].trim();
      if (!matches(properties.trustedProxies(), hop))
        return matches(properties.callbackAllowedIps(), hop);
    }
    return false;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request
        .getRequestURI()
        .startsWith(request.getContextPath() + "/api/v1/mpesa/callback/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!properties.enabled()) {
      response.setStatus(404);
      return;
    }
    if (!"POST".equals(request.getMethod()) || !allowed(request)) {
      response.setStatus(403);
      return;
    }
    if (request.getContentLengthLong() > 32768) {
      response.setStatus(413);
      return;
    }
    chain.doFilter(request, response);
  }
}
