package com.company.fashionpos.shared.api;

import com.company.fashionpos.auth.LoginRateLimitExceededException;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(EntityNotFoundException.class)
  ProblemDetail notFound(EntityNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/not-found"));
    problem.setTitle("Resource not found");
    return problem;
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden() {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/access-denied"));
    problem.setTitle("Access denied");
    return problem;
  }

  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail unauthorized() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/authentication"));
    problem.setTitle("Authentication failed");
    return problem;
  }

  @ExceptionHandler(LoginRateLimitExceededException.class)
  ResponseEntity<ProblemDetail> tooManyLoginAttempts(LoginRateLimitExceededException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/rate-limited"));
    problem.setTitle("Too many requests");

    long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
        .body(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/bad-request"));
    problem.setTitle("Bad request");
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/validation"));
    problem.setTitle("Validation failed");

    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    problem.setProperty("errors", errors);
    return problem;
  }

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    // Log the full exception for server-side diagnostics while returning a safe problem detail to
    // clients
    log.error("Unhandled exception caught by ApiExceptionHandler", exception);

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setType(URI.create("https://keen-fashion-pos.local/problems/internal-error"));
    problem.setTitle("Internal server error");
    return problem;
  }
}
