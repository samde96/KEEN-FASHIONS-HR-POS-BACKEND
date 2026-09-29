package com.company.fashionpos.shared.security;

import com.company.fashionpos.shared.api.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemResponseWriter {

  private final ObjectMapper objectMapper;

  public SecurityProblemResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void writeUnauthorized(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    writeProblem(
        response,
        HttpStatus.UNAUTHORIZED,
        "Authentication failed",
        "Authentication is required",
        "https://keen-fashion-pos.local/problems/authentication");
  }

  public void writeForbidden(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    writeProblem(
        response,
        HttpStatus.FORBIDDEN,
        "Access denied",
        "Access denied",
        "https://keen-fashion-pos.local/problems/access-denied");
  }

  private void writeProblem(
      HttpServletResponse response, HttpStatus status, String title, String detail, String type)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("title", title);
    body.put("status", status.value());
    body.put("detail", detail);

    String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
    if (correlationId != null && !correlationId.isBlank()) {
      body.put("correlationId", correlationId);
    }

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
