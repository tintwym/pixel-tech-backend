package com.shopping.cart.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PromoCodeResponse(
        UUID id,
        String code,
        String discountType,
        BigDecimal amount,
        boolean active,
        Integer maxRedemptions,
        int redemptionCount,
        String expiresAt,
        String label) {}
