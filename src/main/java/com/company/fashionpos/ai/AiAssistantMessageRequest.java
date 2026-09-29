package com.company.fashionpos.ai;

import jakarta.validation.constraints.NotBlank;

public record AiAssistantMessageRequest(@NotBlank String message) {}
