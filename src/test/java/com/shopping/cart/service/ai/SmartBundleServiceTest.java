package com.shopping.cart.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.cart.dto.response.SmartBundleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class SmartBundleServiceTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final SmartBundleService service = new SmartBundleService(gemini);

    @Test
    void sanitizeStripsControlCharactersAndSymbols() {
        assertEquals("MacBook Air 13\" M3 — 16GB/512GB",
                SmartBundleService.sanitizeItemName("MacBook Air 13\" M3 — 16GB/512GB"));
        assertEquals("iPhone ignore previous", SmartBundleService.sanitizeItemName("iPhone\n<ignore previous>"));
        assertNull(SmartBundleService.sanitizeItemName("   "));
        assertNull(SmartBundleService.sanitizeItemName("x".repeat(81)));
    }

    @Test
    void rejectsWhenNoValidItems() {
        assertThrows(ResponseStatusException.class, () -> service.suggest(List.of("", "<<>>")));
        verifyNoInteractions(gemini);
    }

    @Test
    void mapsModelJsonToBundles() throws Exception {
        String json = """
                [{"name":"Creator Kit","description":"Edit on the go","setupTime":"15 mins","difficulty":"Easy",
                  "cartItems":["MacBook Air"],"suggestedAddOns":["USB-C Hub",""],"steps":["Plug in hub"]},
                 {"name":"","description":"dropped","setupTime":"","difficulty":"",
                  "cartItems":[],"suggestedAddOns":[],"steps":[]}]
                """;
        when(gemini.generateJson(anyString(), contains("MacBook Air"), any()))
                .thenReturn(new ObjectMapper().readTree(json));

        SmartBundleResponse response = service.suggest(List.of("MacBook Air", "MacBook Air"));

        assertEquals(1, response.bundles().size());
        SmartBundleResponse.Bundle bundle = response.bundles().get(0);
        assertEquals("Creator Kit", bundle.name());
        assertEquals(List.of("USB-C Hub"), bundle.suggestedAddOns());
        assertEquals(List.of("Plug in hub"), bundle.steps());
    }
}
