package com.example.messaging.step6_idempotent_consumer.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "step6_product_view_counts")
public class ProductViewCount {

    @Id
    private Long productId;

    private long count;

    private LocalDateTime updatedAt;

    protected ProductViewCount() {
    }

    public ProductViewCount(Long productId, long count) {
        this.productId = productId;
        this.count = count;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getProductId() {
        return productId;
    }

    public long getCount() {
        return count;
    }

    public void updateCount(long newCount) {
        this.count = newCount;
        this.updatedAt = LocalDateTime.now();
    }
}
