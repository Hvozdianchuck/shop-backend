package com.shop.messaging;

import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class EventHeaders {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";

    private EventHeaders() {
    }

    public static UUID eventId(Headers headers) {
        var header = headers.lastHeader(EVENT_ID);
        if (header == null) {
            throw new IllegalArgumentException("Record has no " + EVENT_ID + " header");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
