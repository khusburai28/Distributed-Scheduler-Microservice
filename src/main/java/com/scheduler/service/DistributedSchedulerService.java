package com.scheduler.service;

import com.scheduler.kafka.KafkaMessageConsumer;
import com.scheduler.kafka.KafkaMessageProducer;
import com.scheduler.kafka.KafkaResponseListener;
import com.scheduler.kafka.SchedulerMessage;
import com.scheduler.model.JobDetails;
import com.scheduler.model.JobStatus;
import com.scheduler.model.SchedulerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DistributedSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(DistributedSchedulerService.class);
    private static final long RESPONSE_TIMEOUT_MS = 10000; // 10 seconds
    
    private final LocalSchedulerService localScheduler;
    private final KafkaMessageProducer producer;
    private final KafkaMessageConsumer consumer;
    private final KafkaResponseListener responseListener;
    private final ExecutorService executorService;
    private final String instanceId;

    public DistributedSchedulerService(String instanceId, String kafkaBootstrapServers) throws Exception {
        this.instanceId = instanceId;
        this.localScheduler = new LocalSchedulerService();
        this.producer = new KafkaMessageProducer(kafkaBootstrapServers);
        this.responseListener = new KafkaResponseListener(kafkaBootstrapServers, instanceId);
        this.consumer = new KafkaMessageConsumer(kafkaBootstrapServers, instanceId, localScheduler, producer);
        
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        
        executorService.submit(consumer);
        executorService.submit(responseListener);
        
        logger.info("Distributed scheduler service started for instance: {}", instanceId);
    }

    public boolean scheduleJob(JobDetails jobDetails) {
        logger.info("Scheduling job: {} on instance: {}", jobDetails.getJobId(), instanceId);
        return localScheduler.scheduleJob(jobDetails);
    }

    public CompletableFuture<SchedulerResponse> rescheduleJobAsync(String jobId, String jobGroup, 
                                                                   LocalDateTime newScheduleTime, String newCronExpression) {
        logger.info("Attempting to reschedule job: {} on instance: {}", jobId, instanceId);
        
        if (localScheduler.jobExists(jobId, jobGroup)) {
            logger.info("Job {} found locally, rescheduling on local instance", jobId);
            boolean success = localScheduler.rescheduleJob(jobId, jobGroup, newScheduleTime, newCronExpression);
            if (success) {
                return CompletableFuture.completedFuture(
                        SchedulerResponse.success("Job rescheduled successfully on local instance", jobId));
            } else {
                return CompletableFuture.completedFuture(
                        SchedulerResponse.error("Failed to reschedule job on local instance", jobId));
            }
        }
        
        logger.info("Job {} not found locally, broadcasting reschedule request via Kafka", jobId);
        SchedulerMessage message = SchedulerMessage.rescheduleJob(jobId, jobGroup, instanceId, 
                newScheduleTime, newCronExpression);
        producer.sendRequest(message);
        
        CompletableFuture<SchedulerMessage> responseFuture = responseListener.waitForResponse(
                message.getMessageId(), RESPONSE_TIMEOUT_MS);
        
        return responseFuture.thenApply(response -> {
            if (response.isSuccess()) {
                return SchedulerResponse.success("Job rescheduled successfully on remote instance", jobId);
            } else {
                return SchedulerResponse.error("Failed to reschedule job: " + response.getErrorMessage(), jobId);
            }
        }).exceptionally(throwable -> {
            logger.error("Timeout or error waiting for reschedule response: {}", throwable.getMessage());
            return SchedulerResponse.error("Timeout waiting for reschedule confirmation", jobId);
        });
    }

    public CompletableFuture<SchedulerResponse> cancelJobAsync(String jobId, String jobGroup) {
        logger.info("Attempting to cancel job: {} on instance: {}", jobId, instanceId);
        
        if (localScheduler.jobExists(jobId, jobGroup)) {
            logger.info("Job {} found locally, cancelling on local instance", jobId);
            boolean success = localScheduler.cancelJob(jobId, jobGroup);
            if (success) {
                return CompletableFuture.completedFuture(
                        SchedulerResponse.success("Job cancelled successfully on local instance", jobId));
            } else {
                return CompletableFuture.completedFuture(
                        SchedulerResponse.error("Failed to cancel job on local instance", jobId));
            }
        }
        
        logger.info("Job {} not found locally, broadcasting cancel request via Kafka", jobId);
        SchedulerMessage message = SchedulerMessage.cancelJob(jobId, jobGroup, instanceId);
        producer.sendRequest(message);
        
        CompletableFuture<SchedulerMessage> responseFuture = responseListener.waitForResponse(
                message.getMessageId(), RESPONSE_TIMEOUT_MS);
        
        return responseFuture.thenApply(response -> {
            if (response.isSuccess()) {
                return SchedulerResponse.success("Job cancelled successfully on remote instance", jobId);
            } else {
                return SchedulerResponse.error("Failed to cancel job: " + response.getErrorMessage(), jobId);
            }
        }).exceptionally(throwable -> {
            logger.error("Timeout or error waiting for cancel response: {}", throwable.getMessage());
            return SchedulerResponse.error("Timeout waiting for cancellation confirmation", jobId);
        });
    }

    public JobStatus getJobStatus(String jobId) {
        return localScheduler.getJobStatus(jobId);
    }

    public List<String> getAllJobs() {
        return localScheduler.getAllJobs();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void shutdown() {
        logger.info("Shutting down distributed scheduler service for instance: {}", instanceId);
        
        try {
            consumer.stop();
            responseListener.stop();
            producer.close();
            localScheduler.shutdown();
            
            executorService.shutdown();
            if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            logger.info("Distributed scheduler service shutdown completed for instance: {}", instanceId);
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage(), e);
            executorService.shutdownNow();
        }
    }
}