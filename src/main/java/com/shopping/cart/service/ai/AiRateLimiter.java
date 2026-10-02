package com.shopping.cart.service.ai;

import com.shopping.cart.config.AiProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed one-minute window per client. In-memory, so limits are per Cloud Run instance —
 * good enough to stop accidental loops and casual abuse of the paid model.
 */
@Component
public class AiRateLimiter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long resetAt, int count) {}

    private final AiProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AiRateLimiter(AiProperties properties) {
        this.properties = properties;
    }

    public void check(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now > w.resetAt());
        }
        Window window = windows.compute(clientKey(request), (key, current) ->
                current == null || now > current.resetAt()
                        ? new Window(now + WINDOW_MS, 1)
                        : new Window(current.resetAt(), current.count() + 1));
        if (window.count() > properties.getRequestsPerMinute()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many AI requests. Please wait a minute and try again.");
        }
    }

    private static String clientKey(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        if (username != null) return "user:" + username;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
