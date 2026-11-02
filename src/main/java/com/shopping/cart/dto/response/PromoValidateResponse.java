package com.shopping.cart.dto.response;

import java.math.BigDecimal;

public record PromoValidateResponse(
        boolean valid,
        String code,
        String discountType,
        BigDecimal amount,
        String label,
        String message) {}
