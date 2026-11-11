package com.shop.orderservice.application;

import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.ProductCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final ProductCatalog catalog;
    private final OrderRepository orders;

    public PlaceOrderService(ProductCatalog catalog, OrderRepository orders) {
        this.catalog = catalog;
        this.orders = orders;
    }

    // Not @Transactional: the price lookup may take seconds with retries and must not hold a DB connection.
    public Order place(List<RequestedItem> items) {
        Map<String, Integer> quantities = items.stream()
                .collect(Collectors.toMap(RequestedItem::sku, RequestedItem::quantity, Integer::sum, TreeMap::new));

        var products = catalog.findBySkus(quantities.keySet());

        var unknown = quantities.keySet().stream()
                .filter(sku -> !products.containsKey(sku) || !products.get(sku).active())
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownProductsException(unknown);
        }

        var lines = quantities.entrySet().stream()
                .map(e -> new OrderLine(e.getKey(), e.getValue(), products.get(e.getKey()).price()))
                .toList();
        var order = orders.save(Order.place(lines));
        log.info("Order {} placed, total {}", order.getId(), order.getTotal());
        return order;
    }
}
