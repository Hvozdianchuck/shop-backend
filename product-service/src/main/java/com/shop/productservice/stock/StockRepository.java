package com.shop.productservice.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, String> {

    @Modifying
    @Query("update Stock s set s.available = s.available - :quantity where s.sku = :sku and s.available >= :quantity")
    int take(String sku, int quantity);
}
