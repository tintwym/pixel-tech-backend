package com.shopping.cart.controller.api;

import com.shopping.cart.dto.response.PromoValidateResponse;
import com.shopping.cart.service.PromoCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promos")
public class PromoApiController {
    private final PromoCodeService promoCodeService;

    public PromoApiController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @GetMapping("/validate")
    public PromoValidateResponse validate(@RequestParam String code) {
        return promoCodeService.validate(code);
    }
}
