package com.shopping.cart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Gemini API key; AI endpoints return 503 when blank. */
    private String geminiApiKey;
    private String model = "gemini-3.5-flash";
    private int timeoutSeconds = 20;
    /** Max AI requests per client (user or IP) per minute. */
    private int requestsPerMinute = 10;

    public boolean isEnabled() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
