package com.shop.orderservice.api;

import java.util.List;

public record OrderPage(List<OrderResponse> items, int page, int size, boolean hasNext) {
}
