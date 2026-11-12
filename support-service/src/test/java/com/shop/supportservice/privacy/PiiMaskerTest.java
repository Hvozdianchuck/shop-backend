package com.shop.supportservice.privacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksEmail() {
        assertThat(masker.mask("Write to jan.kowalski+shop@example.co.uk please"))
                .isEqualTo("Write to [EMAIL] please");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+48 600 123 456", "+380 (67) 123-45-67", "0671234567", "600-123-456"})
    void masksPhoneNumbers(String phone) {
        assertThat(masker.mask("Call me at " + phone + ", thanks")).isEqualTo("Call me at [PHONE], thanks");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111 1111 1111 1111", "4111-1111-1111-1111", "4111111111111111"})
    void masksCardNumbers(String card) {
        assertThat(masker.mask("I paid with " + card + ".")).isEqualTo("I paid with [CARD].");
    }

    @ParameterizedTest
    @ValueSource(strings = {"550e8400-e29b-41d4-a716-446655440000", "550E8400-E29B-41D4-A716-446655440000",
            "550e8400e29b41d4a716446655440000"})
    void keepsOrderIds(String orderId) {
        var text = "Where is order " + orderId + "?";

        assertThat(masker.mask(text)).isEqualTo(text);
    }

    @Test
    void keepsDatesPricesAndQuantities() {
        var text = "Ordered 2 mice on 12.11.2026 for 129.99 PLN, delivery in 14 days";

        assertThat(masker.mask(text)).isEqualTo(text);
    }
}
