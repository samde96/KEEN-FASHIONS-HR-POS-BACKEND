package com.company.fashionpos.inventory;

import jakarta.validation.constraints.Min;

public record InventoryReorderLevelRequest(@Min(0) int reorderLevel) {}
