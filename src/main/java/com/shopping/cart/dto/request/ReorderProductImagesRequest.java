package com.shopping.cart.dto.request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ordered image IDs; index 0 becomes the catalog card (primary) image. */
public class ReorderProductImagesRequest {
    private List<UUID> imageIds = new ArrayList<>();

    public List<UUID> getImageIds() {
        return imageIds;
    }

    public void setImageIds(List<UUID> imageIds) {
        this.imageIds = imageIds != null ? imageIds : new ArrayList<>();
    }
}
