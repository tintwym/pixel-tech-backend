package com.shopping.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginAdminRequest {
    @NotBlank(message = "Please enter an admin username.")
    @Size(min = 3, max = 64, message = "Admin username must be 3–64 characters.")
    private String username;

    @NotBlank(message = "Please enter your password.")
    @Size(max = 128, message = "Password must be 128 characters or fewer.")
    private String password;
}
