package com.scheduler.service;

import com.scheduler.model.JobDetails;
import com.scheduler.model.JobExecutionJob;
import com.scheduler.model.JobStatus;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(LocalSchedulerService.class);
    private final Scheduler scheduler;
    private final Map<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();

    public LocalSchedulerService() throws SchedulerException {
        SchedulerFactory factory = new StdSchedulerFactory();
        this.scheduler = factory.getScheduler();
        this.scheduler.start();
        logger.info("Local scheduler service started");
    }

    public boolean scheduleJob(JobDetails jobDetails) {
        try {
            JobKey jobKey = new JobKey(jobDetails.getJobId(), jobDetails.getJobGroup());
            
            if (scheduler.checkExists(jobKey)) {
                logger.warn("Job {} already exists in group {}", jobDetails.getJobId(), jobDetails.getJobGroup());
                return false;
            }

            JobDetail job = JobBuilder.newJob(JobExecutionJob.class)
                    .withIdentity(jobKey)
                    .withDescription(jobDetails.getDescription())
                    .build();

            if (jobDetails.getJobData() != null) {
                job.getJobDataMap().putAll(jobDetails.getJobData());
            }

            Trigger trigger = createTrigger(jobDetails);
            scheduler.scheduleJob(job, trigger);
            
            jobStatuses.put(jobDetails.getJobId(), JobStatus.SCHEDULED);
            logger.info("Job {} scheduled successfully", jobDetails.getJobId());
            return true;
        } catch (SchedulerException e) {
            logger.error("Failed to schedule job {}: {}", jobDetails.getJobId(), e.getMessage(), e);
            return false;
        }
    }

    public boolean rescheduleJob(String jobId, String jobGroup, LocalDateTime newScheduleTime, String newCronExpression) {
        try {
            JobKey jobKey = new JobKey(jobId, jobGroup);
            
            if (!scheduler.checkExists(jobKey)) {
                logger.warn("Job {} not found for rescheduling", jobId);
                return false;
            }

            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            if (triggers.isEmpty()) {
                logger.warn("No triggers found for job {}", jobId);
                return false;
            }

            TriggerKey triggerKey = triggers.get(0).getKey();
            
            Trigger newTrigger;
            if (newCronExpression != null && !newCronExpression.trim().isEmpty()) {
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(newCronExpression))
                        .build();
            } else if (newScheduleTime != null) {
                Date startTime = Date.from(newScheduleTime.atZone(ZoneId.systemDefault()).toInstant());
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .startAt(startTime)
                        .build();
            } else {
                logger.error("Neither schedule time nor cron expression provided for rescheduling");
                return false;
            }

            scheduler.rescheduleJob(triggerKey, newTrigger);
            jobStatuses.put(jobId, JobStatus.RESCHEDULED);
            logger.info("Job {} rescheduled successfully", jobId);
            return true;
        } catch (SchedulerException e) {
            logger.error("Failed to reschedule job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    public boolean cancelJob(String jobId, String jobGroup) {
        try {
            JobKey jobKey = new JobKey(jobId, jobGroup);
            
            if (!scheduler.checkExists(jobKey)) {
                logger.warn("Job {} not found for cancellation", jobId);
                return false;
            }

            boolean deleted = scheduler.deleteJob(jobKey);
            if (deleted) {
                jobStatuses.put(jobId, JobStatus.CANCELLED);
                logger.info("Job {} cancelled successfully", jobId);
            } else {
                logger.warn("Failed to cancel job {}", jobId);
            }
            return deleted;
        } catch (SchedulerException e) {
            logger.error("Failed to cancel job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    public boolean jobExists(String jobId, String jobGroup) {
        try {
            JobKey jobKey = new JobKey(jobId, jobGroup);
            return scheduler.checkExists(jobKey);
        } catch (SchedulerException e) {
            logger.error("Error checking job existence {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    public JobStatus getJobStatus(String jobId) {
        return jobStatuses.getOrDefault(jobId, JobStatus.FAILED);
    }

    public List<String> getAllJobs() {
        try {
            List<String> jobs = new ArrayList<>();
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    jobs.add(jobKey.getName() + " (" + jobKey.getGroup() + ")");
                }
            }
            return jobs;
        } catch (SchedulerException e) {
            logger.error("Failed to get all jobs: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private Trigger createTrigger(JobDetails jobDetails) {
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(jobDetails.getJobId() + "_trigger", jobDetails.getJobGroup());

        if (jobDetails.getCronExpression() != null && !jobDetails.getCronExpression().trim().isEmpty()) {
            return triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(jobDetails.getCronExpression())).build();
        } else if (jobDetails.getScheduleTime() != null) {
            Date startTime = Date.from(jobDetails.getScheduleTime().atZone(ZoneId.systemDefault()).toInstant());
            return triggerBuilder.startAt(startTime).build();
        } else {
            throw new IllegalArgumentException("Either schedule time or cron expression must be provided");
        }
    }

    public void shutdown() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown(true);
                logger.info("Local scheduler service shutdown");
            }
        } catch (SchedulerException e) {
            logger.error("Error shutting down scheduler: {}", e.getMessage(), e);
        }
    }
}