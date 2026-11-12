package com.shop.messaging;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class OutboxWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxWriter(JdbcClient jdbc, Tracer tracer, Propagator propagator) {
        this.jdbc = jdbc;
        this.tracer = tracer;
        this.propagator = propagator;
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
                .param("headers", traceHeaders())
                .update();
        return id;
    }

    // The relay sends the event later from its own thread; keeping the trace context lets it continue the same trace.
    private String traceHeaders() {
        var context = tracer.currentTraceContext().context();
        Map<String, String> headers = new LinkedHashMap<>();
        if (context != null) {
            propagator.inject(context, headers, Map::put);
        }
        return JSON.writeValueAsString(headers);
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
