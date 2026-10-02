package com.shopping.cart.dto.response;

import java.util.List;

public record SmartBundleResponse(List<Bundle> bundles) {

    public record Bundle(
            String name,
            String description,
            String setupTime,
            String difficulty,
            List<String> cartItems,
            List<String> suggestedAddOns,
            List<String> steps) {}
}
