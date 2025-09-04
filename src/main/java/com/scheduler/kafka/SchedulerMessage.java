package com.scheduler.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public class SchedulerMessage {
    public enum MessageType {
        CANCEL_JOB, RESCHEDULE_JOB, CANCEL_RESPONSE, RESCHEDULE_RESPONSE
    }

    private final String messageId;
    private final MessageType type;
    private final String jobId;
    private final String jobGroup;
    private final String instanceId;
    private final LocalDateTime newScheduleTime;
    private final String newCronExpression;
    private final boolean success;
    private final String errorMessage;
    private final long timestamp;

    @JsonCreator
    public SchedulerMessage(@JsonProperty("messageId") String messageId,
                           @JsonProperty("type") MessageType type,
                           @JsonProperty("jobId") String jobId,
                           @JsonProperty("jobGroup") String jobGroup,
                           @JsonProperty("instanceId") String instanceId,
                           @JsonProperty("newScheduleTime") LocalDateTime newScheduleTime,
                           @JsonProperty("newCronExpression") String newCronExpression,
                           @JsonProperty("success") boolean success,
                           @JsonProperty("errorMessage") String errorMessage,
                           @JsonProperty("timestamp") long timestamp) {
        this.messageId = messageId;
        this.type = type;
        this.jobId = jobId;
        this.jobGroup = jobGroup;
        this.instanceId = instanceId;
        this.newScheduleTime = newScheduleTime;
        this.newCronExpression = newCronExpression;
        this.success = success;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
    }

    public static SchedulerMessage cancelJob(String jobId, String jobGroup, String instanceId) {
        return new SchedulerMessage(
                UUID.randomUUID().toString(),
                MessageType.CANCEL_JOB,
                jobId,
                jobGroup,
                instanceId,
                null,
                null,
                false,
                null,
                System.currentTimeMillis()
        );
    }

    public static SchedulerMessage rescheduleJob(String jobId, String jobGroup, String instanceId, 
                                               LocalDateTime newScheduleTime, String newCronExpression) {
        return new SchedulerMessage(
                UUID.randomUUID().toString(),
                MessageType.RESCHEDULE_JOB,
                jobId,
                jobGroup,
                instanceId,
                newScheduleTime,
                newCronExpression,
                false,
                null,
                System.currentTimeMillis()
        );
    }

    public static SchedulerMessage response(String messageId, MessageType responseType, boolean success, String errorMessage) {
        return new SchedulerMessage(
                messageId,
                responseType,
                null,
                null,
                null,
                null,
                null,
                success,
                errorMessage,
                System.currentTimeMillis()
        );
    }

    public String getMessageId() { return messageId; }
    public MessageType getType() { return type; }
    public String getJobId() { return jobId; }
    public String getJobGroup() { return jobGroup; }
    public String getInstanceId() { return instanceId; }
    public LocalDateTime getNewScheduleTime() { return newScheduleTime; }
    public String getNewCronExpression() { return newCronExpression; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "SchedulerMessage{" +
                "messageId='" + messageId + '\'' +
                ", type=" + type +
                ", jobId='" + jobId + '\'' +
                ", jobGroup='" + jobGroup + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
}