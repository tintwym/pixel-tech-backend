package com.shopping.cart.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
    @Column(unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    /** e.g. Apple, Samsung, ASUS — applies across phones, laptops, desktops, etc. */
    @Column(length = 120)
    private String brand;

    /** e.g. Smartphones, Laptops, Tablets, Audio / Headphones */
    @Column(length = 120)
    private String category;

    private BigDecimal price;
    private int stock;

    // Stripe product and price IDs (internal — not exposed on public product JSON)
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "stripe_product_id", unique = true)
    private String stripeProductId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "stripe_price_id", unique = true)
    private String stripePriceId;

    private boolean isDeleted = false;

    // One-to-Many relationship with ProductImage (@BatchSize avoids N+1 when carts/orders skip JOIN FETCH)
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC NULLS LAST")
    private List<ProductImage> images;

    // Default constructor is required by JPA
    public Product() {}

    // Parameterized constructor
    public Product(String name, String description, BigDecimal price, int stock, String stripeProductId, String stripePriceId, boolean isDeleted, List<ProductImage> images) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.stripeProductId = stripeProductId;
        this.stripePriceId = stripePriceId;
        this.isDeleted = isDeleted;
        this.images = images;
    }
}
