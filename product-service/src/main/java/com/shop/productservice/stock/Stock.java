package com.shop.productservice.stock;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Stock {

    @Id
    private String sku;

    private int available;

    protected Stock() {
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }
}
