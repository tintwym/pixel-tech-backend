package com.shopping.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginAdminRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
