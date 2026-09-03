package com.shopping.cart.repository;

import com.shopping.cart.entity.Order;
import com.shopping.cart.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUser(User user);

    // Fetch items + product only (not product.images) — Hibernate forbids multiple bag fetches.
    @Query("SELECT DISTINCT o FROM Order o "
            + "LEFT JOIN FETCH o.orderItems oi "
            + "LEFT JOIN FETCH oi.product p "
            + "WHERE o.user = :user ORDER BY o.createdAt DESC")
    List<Order> findByUserWithItems(@Param("user") User user);

    Optional<Order> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
}
