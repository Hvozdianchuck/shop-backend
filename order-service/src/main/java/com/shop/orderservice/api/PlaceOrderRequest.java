package com.shop.orderservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotBlank String sku, @Positive int quantity) {
    }
}
