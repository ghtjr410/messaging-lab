package com.example.messaging.step6_idempotent_consumer.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "step6_product_stocks")
public class ProductStock {

    @Id
    private Long productId;

    private long stock;

    private int version;

    protected ProductStock() {
    }

    public ProductStock(Long productId, long stock, int version) {
        this.productId = productId;
        this.stock = stock;
        this.version = version;
    }

    public Long getProductId() {
        return productId;
    }

    public long getStock() {
        return stock;
    }

    public int getVersion() {
        return version;
    }

    public boolean applyIfNewer(long newStock, int newVersion) {
        if (newVersion > this.version) {
            this.stock = newStock;
            this.version = newVersion;
            return true;
        }
        return false;
    }
}
