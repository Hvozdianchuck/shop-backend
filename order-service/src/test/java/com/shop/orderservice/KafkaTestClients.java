package com.shop.orderservice;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KafkaTestClients {

    private KafkaTestClients() {
    }

    public static Producer<String, Object> avroProducer() {
        var serializer = new KafkaAvroSerializer();
        serializer.configure(Map.of(
                "schema.registry.url", TestcontainersConfiguration.schemaRegistryUrl(),
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        return new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()),
                new StringSerializer(), serializer);
    }

    public static Producer<String, byte[]> bytesProducer() {
        return new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()),
                new StringSerializer(), new ByteArraySerializer());
    }

    public static void send(Producer<String, Object> producer, String topic, String key, SpecificRecord event,
                            UUID eventId) {
        var record = new ProducerRecord<String, Object>(topic, key, event);
        record.headers()
                .add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8))
                .add("eventType", event.getSchema().getName().getBytes(StandardCharsets.UTF_8));
        producer.send(record);
        producer.flush();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Consumer<String, Object> avroConsumerFromEnd(String topic) {
        var deserializer = new KafkaAvroDeserializer();
        deserializer.configure(Map.of(
                "schema.registry.url", TestcontainersConfiguration.schemaRegistryUrl(),
                "specific.avro.reader", true), false);
        return fromEnd(new KafkaConsumer<>(consumerConfig(), new StringDeserializer(), (Deserializer) deserializer), topic);
    }

    public static Consumer<String, byte[]> bytesConsumerFromEnd(String topic) {
        return fromEnd(new KafkaConsumer<>(consumerConfig(), new StringDeserializer(), new ByteArrayDeserializer()), topic);
    }

    public static <V> ConsumerRecord<String, V> recordFor(Consumer<String, V> consumer, String key) {
        var records = recordsFor(consumer, key, Duration.ofSeconds(15), 1);
        if (records.isEmpty()) {
            throw new AssertionError("No record with key " + key);
        }
        return records.getFirst();
    }

    /** Polls for the whole duration unless enough records arrive, so it also proves that nothing more was sent. */
    public static <V> List<ConsumerRecord<String, V>> recordsFor(Consumer<String, V> consumer, String key,
                                                                 Duration duration, int enough) {
        var found = new ArrayList<ConsumerRecord<String, V>>();
        var deadline = Instant.now().plus(duration);
        while (found.size() < enough && Instant.now().isBefore(deadline)) {
            for (var record : consumer.poll(Duration.ofMillis(200))) {
                if (key.equals(record.key())) {
                    found.add(record);
                }
            }
        }
        return found;
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String bootstrapServers() {
        return TestcontainersConfiguration.kafka.getBootstrapServers();
    }

    private static Map<String, Object> consumerConfig() {
        return Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
    }

    private static <V> Consumer<String, V> fromEnd(Consumer<String, V> consumer, String topic) {
        var partitions = consumer.partitionsFor(topic).stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }
}
