package com.company.fashionpos.auth;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final AuthenticatedUserService authenticatedUserService;

  public MeController(AuthenticatedUserService authenticatedUserService) {
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public MeResponse me(Principal principal) {
    return MeResponse.from(authenticatedUserService.current(principal));
  }
}
