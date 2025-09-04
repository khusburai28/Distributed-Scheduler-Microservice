package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SchedulerResponse {
    private final boolean success;
    private final String message;
    private final String jobId;
    private final Object data;

    @JsonCreator
    public SchedulerResponse(@JsonProperty("success") boolean success,
                            @JsonProperty("message") String message,
                            @JsonProperty("jobId") String jobId,
                            @JsonProperty("data") Object data) {
        this.success = success;
        this.message = message;
        this.jobId = jobId;
        this.data = data;
    }

    public static SchedulerResponse success(String message, String jobId, Object data) {
        return new SchedulerResponse(true, message, jobId, data);
    }

    public static SchedulerResponse success(String message, String jobId) {
        return new SchedulerResponse(true, message, jobId, null);
    }

    public static SchedulerResponse error(String message) {
        return new SchedulerResponse(false, message, null, null);
    }

    public static SchedulerResponse error(String message, String jobId) {
        return new SchedulerResponse(false, message, jobId, null);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getJobId() { return jobId; }
    public Object getData() { return data; }
}