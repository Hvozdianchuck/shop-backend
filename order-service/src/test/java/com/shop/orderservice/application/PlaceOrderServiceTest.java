package com.shop.orderservice.application;

import com.shop.orderservice.domain.CatalogProduct;
import com.shop.orderservice.domain.CatalogUnavailableException;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.OrderStatus;
import com.shop.orderservice.domain.ProductCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceOrderServiceTest {

    private final FakeCatalog catalog = new FakeCatalog();
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PlaceOrderService service = new PlaceOrderService(catalog, orders);

    @BeforeEach
    void setUp() {
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        catalog.add("MOUSE-1", "24.90", true);
        catalog.add("KBD-1", "89.00", true);
        catalog.add("CAM-720", "19.00", false);
    }

    @Test
    void placesPendingOrderWithCurrentPrices() {
        var order = service.place(List.of(new RequestedItem("MOUSE-1", 2), new RequestedItem("KBD-1", 1)));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getLines()).containsExactlyInAnyOrder(
                new OrderLine("MOUSE-1", 2, new BigDecimal("24.90")),
                new OrderLine("KBD-1", 1, new BigDecimal("89.00")));
        assertThat(order.getTotal()).isEqualByComparingTo("138.80");
        verify(orders).save(order);
    }

    @Test
    void mergesRepeatedSkuIntoOneLine() {
        var order = service.place(List.of(new RequestedItem("MOUSE-1", 1), new RequestedItem("MOUSE-1", 2)));

        assertThat(order.getLines()).containsExactly(new OrderLine("MOUSE-1", 3, new BigDecimal("24.90")));
    }

    @Test
    void rejectsUnknownAndInactiveProducts() {
        assertThatThrownBy(() -> service.place(List.of(
                new RequestedItem("MOUSE-1", 1), new RequestedItem("CAM-720", 1), new RequestedItem("NOPE", 1))))
                .isInstanceOfSatisfying(UnknownProductsException.class,
                        e -> assertThat(e.getSkus()).containsExactly("CAM-720", "NOPE"));

        verify(orders, never()).save(any());
    }

    @Test
    void savesNothingWhenCatalogIsUnavailable() {
        catalog.down = true;

        assertThatThrownBy(() -> service.place(List.of(new RequestedItem("MOUSE-1", 1))))
                .isInstanceOf(CatalogUnavailableException.class);

        verify(orders, never()).save(any());
    }

    static class FakeCatalog implements ProductCatalog {

        private final Map<String, CatalogProduct> products = new HashMap<>();
        boolean down;

        void add(String sku, String price, boolean active) {
            products.put(sku, new CatalogProduct(sku, new BigDecimal(price), active));
        }

        @Override
        public Map<String, CatalogProduct> findBySkus(Collection<String> skus) {
            if (down) {
                throw new CatalogUnavailableException(new RuntimeException("down"));
            }
            var found = new HashMap<String, CatalogProduct>();
            skus.stream().filter(products::containsKey).forEach(sku -> found.put(sku, products.get(sku)));
            return found;
        }
    }
}
