package com.company.fashionpos.sales;

import java.math.BigDecimal;

public record HotelSalesSummary(BigDecimal totalAmount, long transactions, long itemsSold) {}
