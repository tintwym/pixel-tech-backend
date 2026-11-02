package com.shopping.cart.dto.request;

import com.shopping.cart.entity.PromoCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreatePromoRequest {
    @NotBlank
    @Size(max = 40)
    private String code;

    @NotNull
    private PromoCode.DiscountType discountType;

    private BigDecimal amount = BigDecimal.ZERO;

    private Integer maxRedemptions;

    /** ISO-8601 local datetime string, optional. */
    private String expiresAt;

    @Size(max = 255)
    private String label;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PromoCode.DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(PromoCode.DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public void setMaxRedemptions(Integer maxRedemptions) {
        this.maxRedemptions = maxRedemptions;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
