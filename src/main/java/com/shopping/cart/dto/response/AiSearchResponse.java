package com.shopping.cart.dto.response;

import java.util.List;
import java.util.UUID;

/** Natural-language search result: a short answer plus catalog products ranked best-first. */
public record AiSearchResponse(String summary, List<Match> results) {

    public record Match(UUID productId, String name, String reason) {}
}
