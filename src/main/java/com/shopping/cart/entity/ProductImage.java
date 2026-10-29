package com.shopping.cart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_images")
public class ProductImage extends BaseEntity {
    private String path;
    private String altText;

    /** Gallery position; 0 = primary card image. Null on legacy rows until re-seeded. */
    @Column(name = "sort_order")
    private Integer sortOrder;

    // Many-to-One relationship with Product
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Default constructor is required by JPA
    public ProductImage() {}

    // Parameterized constructor
    public ProductImage(String path, String altText) {
        this.path = path;
        this.altText = altText;
    }

    public ProductImage(String path, String altText, int sortOrder) {
        this.path = path;
        this.altText = altText;
        this.sortOrder = Integer.valueOf(sortOrder);
    }
}
