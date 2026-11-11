package com.shop.messaging;

import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class OutboxWriter {

    private final JdbcClient jdbc;

    public OutboxWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the event in the caller's transaction; the relay sends it after commit.
     * Without an active transaction this throws, because the event would be committed apart from the data.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String topic, String aggregateId, SpecificRecord event) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO outbox (id, aggregate_id, event_type, topic, msg_key, payload, headers)
                        VALUES (:id, :aggregateId, :eventType, :topic, :key, :payload, CAST(:headers AS jsonb))
                        """)
                .param("id", id)
                .param("aggregateId", aggregateId)
                .param("eventType", event.getSchema().getFullName())
                .param("topic", topic)
                .param("key", aggregateId)
                .param("payload", toJson(event))
                .param("headers", "{}")
                .update();
        return id;
    }

    private static String toJson(SpecificRecord event) {
        var schema = event.getSchema();
        var out = new ByteArrayOutputStream();
        try {
            var encoder = EncoderFactory.get().jsonEncoder(schema, out);
            new SpecificDatumWriter<SpecificRecord>(schema, SpecificData.getForClass(event.getClass()))
                    .write(event, encoder);
            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
