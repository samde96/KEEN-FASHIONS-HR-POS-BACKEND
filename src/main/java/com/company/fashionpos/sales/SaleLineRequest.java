package com.company.fashionpos.sales;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SaleLineRequest(@NotNull UUID productId, @Min(1) int quantity) {}
