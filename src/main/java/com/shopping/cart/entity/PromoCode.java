package com.shopping.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {

    public enum DiscountType {
        FIXED_MMK,
        PERCENT,
        FREE_SHIPPING
    }

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DiscountType discountType;

    /** Fixed MMK off, or percent points (e.g. 10 = 10%). Ignored for FREE_SHIPPING. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    private Integer maxRedemptions;

    @Column(nullable = false)
    private int redemptionCount = 0;

    private LocalDateTime expiresAt;

    @Column(length = 255)
    private String label;
}
