package com.company.fashionpos.organization;

import com.company.fashionpos.catalog.VatCategory;
import jakarta.validation.constraints.NotNull;

public record VatSettingsRequest(@NotNull VatCategory defaultProductVatCategory) {}
