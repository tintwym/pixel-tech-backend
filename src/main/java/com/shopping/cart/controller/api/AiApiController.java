package com.shopping.cart.controller.api;

import com.shopping.cart.dto.request.AiSearchRequest;
import com.shopping.cart.dto.request.SmartBundleRequest;
import com.shopping.cart.dto.response.AiSearchResponse;
import com.shopping.cart.dto.response.SmartBundleResponse;
import com.shopping.cart.service.ai.AiRateLimiter;
import com.shopping.cart.service.ai.GeminiClient;
import com.shopping.cart.service.ai.ProductSearchService;
import com.shopping.cart.service.ai.SmartBundleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Customer-facing AI features. Public (guests have carts too) but rate-limited per client. */
@RestController
@RequestMapping("/api/ai")
public class AiApiController {
    private final GeminiClient gemini;
    private final AiRateLimiter rateLimiter;
    private final SmartBundleService smartBundleService;
    private final ProductSearchService productSearchService;

    public AiApiController(GeminiClient gemini, AiRateLimiter rateLimiter, SmartBundleService smartBundleService,
            ProductSearchService productSearchService) {
        this.gemini = gemini;
        this.rateLimiter = rateLimiter;
        this.smartBundleService = smartBundleService;
        this.productSearchService = productSearchService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("enabled", gemini.isEnabled()));
    }

    @PostMapping("/bundles")
    public ResponseEntity<SmartBundleResponse> bundles(
            @Valid @RequestBody SmartBundleRequest body, HttpServletRequest request) {
        rateLimiter.check(request);
        return ResponseEntity.ok(smartBundleService.suggest(body.getItems()));
    }

    @PostMapping("/search")
    public ResponseEntity<AiSearchResponse> search(
            @Valid @RequestBody AiSearchRequest body, HttpServletRequest request) {
        rateLimiter.check(request);
        return ResponseEntity.ok(productSearchService.search(body.getQuery()));
    }
}
