package com.company.fashionpos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 254) String username,
    @NotBlank @Size(max = 1024) String password,
    boolean rememberDevice) {}
