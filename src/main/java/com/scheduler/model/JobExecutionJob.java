package com.scheduler.model;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobExecutionJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(JobExecutionJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobId = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        
        logger.info("Executing job: {} in group: {}", jobId, jobGroup);
        
        try {
            logger.info("Job {} executed successfully at {}", jobId, context.getFireTime());
        } catch (Exception e) {
            logger.error("Error executing job {}: {}", jobId, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}