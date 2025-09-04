package com.scheduler.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;

public class KafkaMessageProducer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);
    private static final String REQUEST_TOPIC = "scheduler-requests";
    private static final String RESPONSE_TOPIC = "scheduler-responses";
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaMessageProducer(String bootstrapServers) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        this.producer = new KafkaProducer<>(props);
        logger.info("Kafka producer initialized with bootstrap servers: {}", bootstrapServers);
    }

    public void sendRequest(SchedulerMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            ProducerRecord<String, String> record = new ProducerRecord<>(REQUEST_TOPIC, message.getJobId(), messageJson);
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send message to Kafka: {}", exception.getMessage(), exception);
                } else {
                    logger.debug("Message sent successfully to topic: {}, partition: {}, offset: {}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
            
            logger.info("Sent {} request for job: {}", message.getType(), message.getJobId());
        } catch (Exception e) {
            logger.error("Error sending message to Kafka: {}", e.getMessage(), e);
        }
    }

    public void sendResponse(SchedulerMessage responseMessage) {
        try {
            String messageJson = objectMapper.writeValueAsString(responseMessage);
            ProducerRecord<String, String> record = new ProducerRecord<>(RESPONSE_TOPIC, 
                    responseMessage.getMessageId(), messageJson);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send response to Kafka: {}", exception.getMessage(), exception);
                } else {
                    logger.debug("Response sent successfully to topic: {}, partition: {}, offset: {}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
            
            logger.info("Sent {} response: {}", responseMessage.getType(), responseMessage.isSuccess());
        } catch (Exception e) {
            logger.error("Error sending response to Kafka: {}", e.getMessage(), e);
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
            logger.info("Kafka producer closed");
        }
    }
}