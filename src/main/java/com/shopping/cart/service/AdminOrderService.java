package com.shopping.cart.service;

import com.shopping.cart.dto.response.AdminOrderResponse;
import com.shopping.cart.entity.Order;
import com.shopping.cart.entity.OrderItem;
import com.shopping.cart.entity.User;
import com.shopping.cart.repository.OrderRepository;
import com.shopping.cart.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AdminOrderService {
    /** Forward-only delivery flow; {@code cancelled} may be set from any non-final state. */
    static final List<String> FLOW = List.of("pending", "processing", "out_for_delivery", "delivered");
    static final String CANCELLED = "cancelled";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public AdminOrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listOrders() {
        return orderRepository.findAllWithItems().stream().map(AdminOrderService::toResponse).toList();
    }

    @Transactional
    public AdminOrderResponse updateStatus(UUID orderId, String requested) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        String current = fulfillmentOf(order);
        if (current.equals(requested)) {
            return toResponse(order);
        }
        if ("delivered".equals(current) || CANCELLED.equals(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This order is already " + current + " and can't be changed.");
        }
        if (!CANCELLED.equals(requested) && FLOW.indexOf(requested) < FLOW.indexOf(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Orders can only move forward (currently " + current.replace('_', ' ') + ").");
        }
        order.setFulfillmentStatus(requested);
        return toResponse(orderRepository.save(order));
    }

    /** Atomically adds stock; returns the new level. */
    @Transactional
    public int restock(UUID productId, int amount) {
        if (productRepository.addStock(productId, amount) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    static String fulfillmentOf(Order order) {
        String status = order.getFulfillmentStatus();
        return status == null || status.isBlank() ? FLOW.get(0) : status;
    }

    private static AdminOrderResponse toResponse(Order order) {
        User user = order.getUser();
        List<AdminOrderResponse.Item> items = order.getOrderItems() == null ? List.of()
                : order.getOrderItems().stream().map(AdminOrderService::toItem).toList();
        String createdAt = order.getCreatedAt() == null ? null
                : order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString();
        return new AdminOrderResponse(
                order.getId(),
                customerName(user),
                user != null ? user.getEmail() : null,
                order.getTotalPrice(),
                order.getStatus(),
                fulfillmentOf(order),
                createdAt,
                items);
    }

    private static AdminOrderResponse.Item toItem(OrderItem item) {
        var product = item.getProduct();
        return new AdminOrderResponse.Item(
                product != null ? product.getId() : null,
                product != null ? product.getName() : "Removed product",
                item.getQuantity(),
                item.getPrice());
    }

    private static String customerName(User user) {
        if (user == null) {
            return "Guest";
        }
        String full = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }
}
