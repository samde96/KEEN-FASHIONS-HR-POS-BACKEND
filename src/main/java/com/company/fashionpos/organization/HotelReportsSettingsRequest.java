package com.company.fashionpos.organization;

import jakarta.validation.constraints.NotNull;

public record HotelReportsSettingsRequest(@NotNull Boolean showHotelReports) {}
