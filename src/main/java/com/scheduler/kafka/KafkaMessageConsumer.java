package com.scheduler.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.service.LocalSchedulerService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaMessageConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    private static final String REQUEST_TOPIC = "scheduler-requests";
    
    private final KafkaConsumer<String, String> consumer;
    private final LocalSchedulerService localScheduler;
    private final KafkaMessageProducer producer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String instanceId;

    public KafkaMessageConsumer(String bootstrapServers, String instanceId, 
                               LocalSchedulerService localScheduler, 
                               KafkaMessageProducer producer) {
        this.instanceId = instanceId;
        this.localScheduler = localScheduler;
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "scheduler-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(REQUEST_TOPIC));
        logger.info("Kafka consumer initialized for instance: {}", instanceId);
    }

    @Override
    public void run() {
        logger.info("Starting Kafka message consumer for instance: {}", instanceId);
        
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processMessage(record.value());
                    } catch (Exception e) {
                        logger.error("Error processing message: {}", e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in consumer loop: {}", e.getMessage(), e);
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
        logger.info("Kafka message consumer stopped for instance: {}", instanceId);
    }

    private void processMessage(String messageJson) {
        try {
            SchedulerMessage message = objectMapper.readValue(messageJson, SchedulerMessage.class);
            logger.info("Received message: {}", message);
            
            if (message.getInstanceId() != null && message.getInstanceId().equals(instanceId)) {
                logger.debug("Ignoring message from same instance: {}", instanceId);
                return;
            }
            
            boolean operationSuccess = false;
            String errorMessage = null;
            SchedulerMessage.MessageType responseType;
            
            try {
                switch (message.getType()) {
                    case CANCEL_JOB:
                        operationSuccess = localScheduler.cancelJob(message.getJobId(), message.getJobGroup());
                        responseType = SchedulerMessage.MessageType.CANCEL_RESPONSE;
                        if (!operationSuccess) {
                            errorMessage = "Job not found or failed to cancel on instance: " + instanceId;
                        }
                        break;
                        
                    case RESCHEDULE_JOB:
                        operationSuccess = localScheduler.rescheduleJob(
                                message.getJobId(), 
                                message.getJobGroup(), 
                                message.getNewScheduleTime(),
                                message.getNewCronExpression()
                        );
                        responseType = SchedulerMessage.MessageType.RESCHEDULE_RESPONSE;
                        if (!operationSuccess) {
                            errorMessage = "Job not found or failed to reschedule on instance: " + instanceId;
                        }
                        break;
                        
                    default:
                        logger.warn("Unsupported message type: {}", message.getType());
                        return;
                }
                
                SchedulerMessage response = SchedulerMessage.response(
                        message.getMessageId(),
                        responseType,
                        operationSuccess,
                        errorMessage
                );
                
                producer.sendResponse(response);
                logger.info("Processed {} for job: {} with result: {}", 
                        message.getType(), message.getJobId(), operationSuccess);
                        
            } catch (Exception e) {
                logger.error("Error processing scheduler operation: {}", e.getMessage(), e);
                SchedulerMessage errorResponse = SchedulerMessage.response(
                        message.getMessageId(),
                        message.getType() == SchedulerMessage.MessageType.CANCEL_JOB ? 
                                SchedulerMessage.MessageType.CANCEL_RESPONSE : 
                                SchedulerMessage.MessageType.RESCHEDULE_RESPONSE,
                        false,
                        "Error: " + e.getMessage()
                );
                producer.sendResponse(errorResponse);
            }
            
        } catch (Exception e) {
            logger.error("Error parsing message JSON: {}", e.getMessage(), e);
        }
    }

    public void stop() {
        running.set(false);
        logger.info("Stopping Kafka message consumer for instance: {}", instanceId);
    }
}