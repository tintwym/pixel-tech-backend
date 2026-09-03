package com.shopping.cart.interfaces;

import com.shopping.cart.dto.request.AddReviewRequest;
import com.shopping.cart.dto.response.ReviewResponse;
import com.shopping.cart.entity.Review;

import java.util.List;
import java.util.UUID;

public interface IReviewService {
    Review getReview(UUID id);
    List<ReviewResponse> getAllReviews();
    List<ReviewResponse> getAllReviewsForProduct(UUID productId);

    void addReview(String token, AddReviewRequest addReviewRequest);
    void deleteReview();
    void updateReview();
}
