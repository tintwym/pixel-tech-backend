package com.shopping.cart.repository;

import com.shopping.cart.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    @Query("SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.product WHERE r.product.id = :productId")
    List<Review> findByProductIdWithDetails(@Param("productId") UUID productId);

    @Query("SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.user LEFT JOIN FETCH r.product")
    List<Review> findAllWithDetails();

    boolean existsByOrderItem_Id(UUID orderItemId);
}
