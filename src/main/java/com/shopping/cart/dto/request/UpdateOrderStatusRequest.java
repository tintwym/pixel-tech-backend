package com.shopping.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {
    @NotBlank(message = "Please choose an order status.")
    @Pattern(regexp = "pending|processing|out_for_delivery|delivered|cancelled",
            message = "Status must be pending, processing, out_for_delivery, delivered or cancelled.")
    private String status;
}
