package com.shopping.cart.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.cart.dto.response.AiSearchResponse;
import com.shopping.cart.entity.Product;
import com.shopping.cart.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductSearchService service = new ProductSearchService(gemini, products);
    private final ObjectMapper mapper = new ObjectMapper();

    private Product product(String name, String price) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setDescription("Specs for " + name);
        p.setPrice(new BigDecimal(price));
        p.setStock(5);
        return p;
    }

    @Test
    void sanitizeQueryFlattensAndCaps() {
        assertEquals("phone under 3M 'pro'", ProductSearchService.sanitizeQuery("  phone\nunder\t3M \"pro\" "));
        assertNull(ProductSearchService.sanitizeQuery(" \n "));
        assertEquals(ProductSearchService.MAX_QUERY_LENGTH,
                ProductSearchService.sanitizeQuery("a".repeat(500)).length());
    }

    @Test
    void rejectsBlankQueryWithoutCallingGemini() {
        assertThrows(ResponseStatusException.class, () -> service.search("   "));
        verifyNoInteractions(gemini);
    }

    @Test
    void keepsOnlyKnownCatalogRefsInOrderWithoutDuplicates() throws Exception {
        Product iphone = product("iPhone 16 Pro", "4899000");
        Product pixel = product("Pixel 9", "3299000");
        // Catalog is sorted by name, so iPhone is P1 and Pixel is P2.
        when(products.findByIsDeletedFalse()).thenReturn(List.of(pixel, iphone));
        String json = """
                {"summary":"Two phones fit.","results":[
                  {"ref":"p2","reason":"Best camera"},
                  {"ref":"P9","reason":"Hallucinated"},
                  {"ref":"%s","reason":"Duplicate by id"},
                  {"ref":"%s","reason":"Unknown id"},
                  {"ref":"P1","reason":"Cheaper"}]}
                """.formatted(pixel.getId(), UUID.randomUUID());
        when(gemini.generateJson(anyString(), contains("P2 | Pixel 9 | 3299000 | 5"), any())).thenReturn(mapper.readTree(json));

        AiSearchResponse res = service.search("phone with good camera");

        assertEquals("Two phones fit.", res.summary());
        assertEquals(List.of(pixel.getId(), iphone.getId()), res.results().stream().map(AiSearchResponse.Match::productId).toList());
        assertEquals("Pixel 9", res.results().get(0).name());
        assertEquals("Best camera", res.results().get(0).reason());
    }

    @Test
    void emptyCatalogSkipsGemini() {
        when(products.findByIsDeletedFalse()).thenReturn(List.of());
        assertTrue(service.search("laptop").results().isEmpty());
        verifyNoInteractions(gemini);
    }
}
