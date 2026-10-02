package com.shopping.cart.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SmartBundleRequest {
    @NotEmpty(message = "Add at least one product to your cart to get bundle ideas.")
    @Size(max = 20, message = "Send at most 20 cart items.")
    private List<String> items;
}
