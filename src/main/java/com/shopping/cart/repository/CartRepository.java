package com.shopping.cart.repository;

import com.shopping.cart.entity.Cart;
import com.shopping.cart.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Cart findByUser(User user);

    // Fetch items + product only (not product.images) — Hibernate forbids multiple bag fetches.
    @Query("SELECT DISTINCT c FROM Cart c "
            + "LEFT JOIN FETCH c.cartItems ci "
            + "LEFT JOIN FETCH ci.product p "
            + "WHERE c.user = :user")
    Cart findByUserWithItems(@Param("user") User user);
}
