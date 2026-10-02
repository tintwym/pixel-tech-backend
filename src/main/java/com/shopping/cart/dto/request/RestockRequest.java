package com.shopping.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestockRequest {
    @Min(value = 1, message = "Restock amount must be at least 1.")
    @Max(value = 10000, message = "Restock amount can be at most 10,000 units.")
    private int amount;
}
