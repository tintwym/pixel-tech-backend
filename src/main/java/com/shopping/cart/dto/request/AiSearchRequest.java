package com.shopping.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiSearchRequest {
    @NotBlank(message = "Type what you're looking for.")
    @Size(max = 200, message = "Keep your search under 200 characters.")
    private String query;
}
