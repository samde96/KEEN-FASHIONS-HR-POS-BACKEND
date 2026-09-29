package com.company.fashionpos.auth;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {

  static CsrfTokenResponse from(CsrfToken csrfToken) {
    return new CsrfTokenResponse(
        csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
  }
}
