package com.shop.productservice.stock;

import java.util.List;

public class UnknownSkuException extends RuntimeException {

    public UnknownSkuException(List<String> skus) {
        super("Unknown SKUs: " + skus);
    }
}
