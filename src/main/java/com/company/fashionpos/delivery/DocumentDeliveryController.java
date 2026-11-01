package com.company.fashionpos.delivery;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-delivery")
public class DocumentDeliveryController {

  private final DocumentDeliveryService documentDeliveryService;

  public DocumentDeliveryController(DocumentDeliveryService documentDeliveryService) {
    this.documentDeliveryService = documentDeliveryService;
  }

  @PostMapping("/email")
  public DocumentEmailResponse sendEmail(@Valid @RequestBody DocumentEmailRequest request) {
    return documentDeliveryService.sendEmail(request);
  }

  @GetMapping("/email/status")
  public DocumentDeliveryService.EmailConfiguration emailStatus() {
    return documentDeliveryService.configuration();
  }
}
