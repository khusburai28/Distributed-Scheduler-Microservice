package com.scheduler.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaResponseListener implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaResponseListener.class);
    private static final String RESPONSE_TOPIC = "scheduler-responses";
    
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, CompletableFuture<SchedulerMessage>> pendingResponses = new ConcurrentHashMap<>();
    private final String instanceId;

    public KafkaResponseListener(String bootstrapServers, String instanceId) {
        this.instanceId = instanceId;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "scheduler-response-group-" + instanceId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(RESPONSE_TOPIC));
        logger.info("Kafka response listener initialized for instance: {}", instanceId);
    }

    @Override
    public void run() {
        logger.info("Starting Kafka response listener for instance: {}", instanceId);
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processResponse(record.value());
                    } catch (Exception e) {
                        logger.error("Error processing response: {}", e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in response listener loop: {}", e.getMessage(), e);
                if (running.get()) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        consumer.close();
        logger.info("Kafka response listener stopped for instance: {}", instanceId);
    }

    private void processResponse(String responseJson) {
        try {
            SchedulerMessage response = objectMapper.readValue(responseJson, SchedulerMessage.class);
            logger.info("Received response: {}", response);
            
            CompletableFuture<SchedulerMessage> future = pendingResponses.remove(response.getMessageId());
            if (future != null) {
                future.complete(response);
                logger.debug("Completed future for message: {}", response.getMessageId());
            } else {
                logger.debug("No pending future found for message: {}", response.getMessageId());
            }
            
        } catch (Exception e) {
            logger.error("Error parsing response JSON: {}", e.getMessage(), e);
        }
    }

    public CompletableFuture<SchedulerMessage> waitForResponse(String messageId, long timeoutMs) {
        CompletableFuture<SchedulerMessage> future = new CompletableFuture<>();
        pendingResponses.put(messageId, future);
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(timeoutMs);
                CompletableFuture<SchedulerMessage> expiredFuture = pendingResponses.remove(messageId);
                if (expiredFuture != null && !expiredFuture.isDone()) {
                    expiredFuture.completeExceptionally(new RuntimeException("Response timeout"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        return future;
    }

    public void stop() {
        running.set(false);
        pendingResponses.values().forEach(future -> 
                future.completeExceptionally(new RuntimeException("Service stopped")));
        pendingResponses.clear();
        logger.info("Stopping Kafka response listener for instance: {}", instanceId);
    }
}