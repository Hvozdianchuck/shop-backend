package com.shop.orderservice.domain;

import java.util.Collection;
import java.util.Map;

public interface ProductCatalog {

    /** Current prices for the given SKUs in one call; SKUs the catalog does not know are left out. */
    Map<String, CatalogProduct> findBySkus(Collection<String> skus);
}
