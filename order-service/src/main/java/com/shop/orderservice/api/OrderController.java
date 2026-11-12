package com.shop.orderservice.api;

import com.shop.orderservice.application.CancelOrderService;
import com.shop.orderservice.application.PlaceOrderService;
import com.shop.orderservice.application.RequestedItem;
import com.shop.orderservice.domain.OrderRepository;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final PlaceOrderService placeOrder;
    private final CancelOrderService cancelOrder;
    private final OrderRepository orders;

    public OrderController(PlaceOrderService placeOrder, CancelOrderService cancelOrder, OrderRepository orders) {
        this.placeOrder = placeOrder;
        this.cancelOrder = cancelOrder;
        this.orders = orders;
    }

    @PostMapping
    @RateLimiter(name = "placeOrder")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        var items = request.items().stream()
                .map(i -> new RequestedItem(i.sku(), i.quantity()))
                .toList();
        var order = placeOrder.place(items);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(OrderResponse.from(order));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(cancelOrder.cancel(id));
    }

    @GetMapping
    public OrderPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var request = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        var orders = this.orders.findPageWithLines(request);
        return new OrderPage(orders.map(OrderResponse::from).getContent(), page, size, orders.hasNext());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.findWithLinesById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }
}
