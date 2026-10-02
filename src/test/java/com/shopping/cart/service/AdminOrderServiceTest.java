package com.shopping.cart.service;

import com.shopping.cart.dto.response.AdminOrderResponse;
import com.shopping.cart.entity.Order;
import com.shopping.cart.entity.User;
import com.shopping.cart.repository.OrderRepository;
import com.shopping.cart.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminOrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AdminOrderService service = new AdminOrderService(orders, products);

    private Order order(String fulfillment) {
        User user = new User();
        user.setFirstName("Su");
        user.setLastName("Myat");
        user.setUsername("sumyat");
        Order order = new Order(new BigDecimal("1500000"), user);
        order.setId(UUID.randomUUID());
        order.setStatus("COMPLETED");
        order.setFulfillmentStatus(fulfillment);
        when(orders.findByIdWithItems(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        return order;
    }

    @Test
    void legacyOrdersWithoutStatusAdvanceFromPending() {
        Order order = order(null);
        AdminOrderResponse res = service.updateStatus(order.getId(), "processing");
        assertEquals("processing", res.fulfillmentStatus());
        assertEquals("COMPLETED", res.paymentStatus());
        assertEquals("Su Myat", res.customerName());
    }

    @Test
    void rejectsMovingBackwards() {
        Order order = order("out_for_delivery");
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateStatus(order.getId(), "processing"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(orders, never()).save(any());
    }

    @Test
    void finalStatesAreLocked() {
        Order delivered = order("delivered");
        assertThrows(ResponseStatusException.class, () -> service.updateStatus(delivered.getId(), "cancelled"));
        Order cancelled = order("cancelled");
        assertThrows(ResponseStatusException.class, () -> service.updateStatus(cancelled.getId(), "processing"));
    }

    @Test
    void canCancelInFlightOrder() {
        Order order = order("processing");
        assertEquals("cancelled", service.updateStatus(order.getId(), "cancelled").fulfillmentStatus());
    }

    @Test
    void restockReportsMissingProduct() {
        UUID id = UUID.randomUUID();
        when(products.addStock(id, 5)).thenReturn(0);
        var ex = assertThrows(ResponseStatusException.class, () -> service.restock(id, 5));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
