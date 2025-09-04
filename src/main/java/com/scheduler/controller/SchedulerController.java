package com.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.model.JobDetails;
import com.scheduler.model.SchedulerResponse;
import com.scheduler.service.DistributedSchedulerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public class SchedulerController extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerController.class);
    
    private final DistributedSchedulerService schedulerService;
    private final ObjectMapper objectMapper;

    public SchedulerController(DistributedSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                pathInfo = "/create";
            }
            
            switch (pathInfo) {
                case "/create":
                    handleCreateJob(req, resp);
                    break;
                case "/reschedule":
                    handleRescheduleJob(req, resp);
                    break;
                case "/cancel":
                    handleCancelJob(req, resp);
                    break;
                default:
                    sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        
        try {
            if ("/jobs".equals(pathInfo)) {
                handleGetJobs(req, resp);
            } else if (pathInfo != null && pathInfo.startsWith("/status/")) {
                String jobId = pathInfo.substring(8);
                handleGetJobStatus(jobId, req, resp);
            } else {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Error processing GET request: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private void handleCreateJob(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JobDetails jobDetails = readJobDetails(req);
        if (jobDetails == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid job details");
            return;
        }

        boolean success = schedulerService.scheduleJob(jobDetails);
        if (success) {
            SchedulerResponse response = SchedulerResponse.success("Job scheduled successfully", jobDetails.getJobId());
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } else {
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to schedule job");
        }
    }

    private void handleRescheduleJob(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RescheduleRequest rescheduleReq = readRescheduleRequest(req);
        if (rescheduleReq == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid reschedule request");
            return;
        }

        CompletableFuture<SchedulerResponse> future = schedulerService.rescheduleJobAsync(
                rescheduleReq.jobId,
                rescheduleReq.jobGroup,
                rescheduleReq.newScheduleTime,
                rescheduleReq.newCronExpression
        );

        future.thenAccept(response -> {
            try {
                sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
            } catch (IOException e) {
                logger.error("Error sending reschedule response: {}", e.getMessage(), e);
            }
        }).exceptionally(throwable -> {
            try {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to reschedule job: " + throwable.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    private void handleCancelJob(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CancelRequest cancelReq = readCancelRequest(req);
        if (cancelReq == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid cancel request");
            return;
        }

        CompletableFuture<SchedulerResponse> future = schedulerService.cancelJobAsync(cancelReq.jobId, cancelReq.jobGroup);

        future.thenAccept(response -> {
            try {
                sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
            } catch (IOException e) {
                logger.error("Error sending cancel response: {}", e.getMessage(), e);
            }
        }).exceptionally(throwable -> {
            try {
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to cancel job: " + throwable.getMessage());
            } catch (IOException e) {
                logger.error("Error sending error response: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    private void handleGetJobs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            var jobs = schedulerService.getAllJobs();
            SchedulerResponse response = SchedulerResponse.success("Jobs retrieved successfully", null, jobs);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            logger.error("Error getting jobs: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to get jobs");
        }
    }

    private void handleGetJobStatus(String jobId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            var status = schedulerService.getJobStatus(jobId);
            SchedulerResponse response = SchedulerResponse.success("Job status retrieved", jobId, status);
            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            logger.error("Error getting job status: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to get job status");
        }
    }

    private JobDetails readJobDetails(HttpServletRequest req) {
        try {
            StringBuilder buffer = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
            }
            return objectMapper.readValue(buffer.toString(), JobDetails.class);
        } catch (Exception e) {
            logger.error("Error reading job details: {}", e.getMessage(), e);
            return null;
        }
    }

    private RescheduleRequest readRescheduleRequest(HttpServletRequest req) {
        try {
            StringBuilder buffer = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
            }
            return objectMapper.readValue(buffer.toString(), RescheduleRequest.class);
        } catch (Exception e) {
            logger.error("Error reading reschedule request: {}", e.getMessage(), e);
            return null;
        }
    }

    private CancelRequest readCancelRequest(HttpServletRequest req) {
        try {
            StringBuilder buffer = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
            }
            return objectMapper.readValue(buffer.toString(), CancelRequest.class);
        } catch (Exception e) {
            logger.error("Error reading cancel request: {}", e.getMessage(), e);
            return null;
        }
    }

    private void sendJsonResponse(HttpServletResponse resp, int statusCode, Object data) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String json = objectMapper.writeValueAsString(data);
        resp.getWriter().write(json);
        resp.getWriter().flush();
    }

    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        SchedulerResponse errorResponse = SchedulerResponse.error(message);
        sendJsonResponse(resp, statusCode, errorResponse);
    }

    public static class RescheduleRequest {
        public String jobId;
        public String jobGroup;
        public LocalDateTime newScheduleTime;
        public String newCronExpression;
    }

    public static class CancelRequest {
        public String jobId;
        public String jobGroup;
    }
}