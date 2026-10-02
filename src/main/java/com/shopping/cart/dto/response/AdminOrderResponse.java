package com.shopping.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Order as shown in the admin dashboard. {@code createdAt} is an ISO-8601 instant. */
public record AdminOrderResponse(
        UUID id,
        String customerName,
        String customerEmail,
        BigDecimal totalPrice,
        String paymentStatus,
        String fulfillmentStatus,
        String createdAt,
        List<Item> items) {

    public record Item(UUID productId, String productName, int quantity, BigDecimal price) {}
}
