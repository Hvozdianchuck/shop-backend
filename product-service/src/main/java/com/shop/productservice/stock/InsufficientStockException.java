package com.shop.productservice.stock;

import java.util.List;

public class InsufficientStockException extends RuntimeException {

    private final List<String> missingSkus;

    public InsufficientStockException(List<String> missingSkus) {
        super("Not enough stock for " + missingSkus, null, false, false);
        this.missingSkus = List.copyOf(missingSkus);
    }

    public List<String> getMissingSkus() {
        return missingSkus;
    }
}
