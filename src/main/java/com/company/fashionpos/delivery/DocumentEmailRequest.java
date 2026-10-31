package com.company.fashionpos.delivery;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentEmailRequest(
    @NotBlank @Email @Size(max = 254) String to,
    @Email @Size(max = 254) String from,
    @NotBlank @Size(max = 160) String subject,
    @NotBlank @Size(max = 2000) String message,
    @NotBlank @Size(max = 120) String attachmentFileName,
    @NotBlank String attachmentContentBase64) {}
