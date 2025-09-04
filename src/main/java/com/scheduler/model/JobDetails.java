package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Map;

public class JobDetails {
    private final String jobId;
    private final String jobName;
    private final String jobGroup;
    private final LocalDateTime scheduleTime;
    private final String cronExpression;
    private final Map<String, Object> jobData;
    private final String description;
    private final boolean recurring;

    @JsonCreator
    public JobDetails(@JsonProperty("jobId") String jobId,
                     @JsonProperty("jobName") String jobName,
                     @JsonProperty("jobGroup") String jobGroup,
                     @JsonProperty("scheduleTime") LocalDateTime scheduleTime,
                     @JsonProperty("cronExpression") String cronExpression,
                     @JsonProperty("jobData") Map<String, Object> jobData,
                     @JsonProperty("description") String description,
                     @JsonProperty("recurring") boolean recurring) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.jobGroup = jobGroup;
        this.scheduleTime = scheduleTime;
        this.cronExpression = cronExpression;
        this.jobData = jobData;
        this.description = description;
        this.recurring = recurring;
    }

    public String getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public String getJobGroup() { return jobGroup; }
    public LocalDateTime getScheduleTime() { return scheduleTime; }
    public String getCronExpression() { return cronExpression; }
    public Map<String, Object> getJobData() { return jobData; }
    public String getDescription() { return description; }
    public boolean isRecurring() { return recurring; }

    @Override
    public String toString() {
        return "JobDetails{" +
                "jobId='" + jobId + '\'' +
                ", jobName='" + jobName + '\'' +
                ", jobGroup='" + jobGroup + '\'' +
                ", scheduleTime=" + scheduleTime +
                ", cronExpression='" + cronExpression + '\'' +
                ", description='" + description + '\'' +
                ", recurring=" + recurring +
                '}';
    }
}