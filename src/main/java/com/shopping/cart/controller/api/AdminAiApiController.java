package com.shopping.cart.controller.api;

import com.shopping.cart.service.ai.AdminGuard;
import com.shopping.cart.service.ai.GeminiClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin-only AI features. Every endpoint must call {@link AdminGuard#requireAdmin}. */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiApiController {
    private final AdminGuard adminGuard;
    private final GeminiClient gemini;

    public AdminAiApiController(AdminGuard adminGuard, GeminiClient gemini) {
        this.adminGuard = adminGuard;
        this.gemini = gemini;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return ResponseEntity.ok(Map.of("enabled", gemini.isEnabled(), "model", gemini.model()));
    }
}
