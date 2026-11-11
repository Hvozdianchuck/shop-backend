package com.shop.productservice.catalog;

import com.shop.productservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsRequestedProductsAndSkipsUnknownSkus() {
        var result = mvc.get().uri("/api/products?skus=MOUSE-1,CAM-720,NOPE");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].sku").asArray()
                .containsExactlyInAnyOrder("MOUSE-1", "CAM-720");
        assertThat(result).bodyJson().extractingPath("$[?(@.sku == 'CAM-720')].active").asArray()
                .containsExactly(false);
        assertThat(result).bodyJson().extractingPath("$[?(@.sku == 'MOUSE-1')].price").asArray()
                .containsExactly(24.90);
    }

    @Test
    void rejectsRequestWithoutSkus() {
        assertThat(mvc.get().uri("/api/products")).hasStatus4xxClientError();
    }
}
