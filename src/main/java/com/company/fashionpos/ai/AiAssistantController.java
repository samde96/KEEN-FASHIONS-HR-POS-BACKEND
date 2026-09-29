package com.company.fashionpos.ai;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-assistant")
public class AiAssistantController {

  private final AiAssistantService aiAssistantService;
  private final AuthenticatedUserService authenticatedUserService;

  public AiAssistantController(
      AiAssistantService aiAssistantService, AuthenticatedUserService authenticatedUserService) {
    this.aiAssistantService = aiAssistantService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @PostMapping("/chat")
  public AiAssistantResponse chat(
      @Valid @RequestBody AiAssistantMessageRequest request, Principal principal) {
    return aiAssistantService.answer(authenticatedUserService.current(principal), request);
  }
}
