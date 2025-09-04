package com.scheduler;

import com.scheduler.config.SchedulerConfig;
import com.scheduler.controller.SchedulerController;
import com.scheduler.service.DistributedSchedulerService;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerApplication {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerApplication.class);
    
    private Server server;
    private DistributedSchedulerService schedulerService;

    public static void main(String[] args) {
        SchedulerApplication app = new SchedulerApplication();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            app.stop();
        }));
        
        try {
            app.start();
            app.join();
        } catch (Exception e) {
            logger.error("Failed to start scheduler application: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public void start() throws Exception {
        SchedulerConfig config = SchedulerConfig.getInstance();
        logger.info("Starting Scheduler Application with config: {}", config);
        
        schedulerService = new DistributedSchedulerService(
                config.getInstanceId(), 
                config.getKafkaBootstrapServers()
        );
        
        SchedulerController controller = new SchedulerController(schedulerService);
        
        server = new Server(config.getServerPort());
        
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath(config.getContextPath());
        
        context.addServlet(new ServletHolder(controller), "/*");
        
        server.setHandler(context);
        server.start();
        
        logger.info("Scheduler Application started successfully");
        logger.info("Instance ID: {}", config.getInstanceId());
        logger.info("Server running on port: {}", config.getServerPort());
        logger.info("Context path: {}", config.getContextPath());
        logger.info("Kafka servers: {}", config.getKafkaBootstrapServers());
        logger.info("API endpoints available at: http://localhost:{}{}", 
                config.getServerPort(), config.getContextPath());
        
        printApiUsage(config);
    }

    public void join() throws InterruptedException {
        if (server != null) {
            server.join();
        }
    }

    public void stop() {
        logger.info("Stopping Scheduler Application...");
        
        try {
            if (schedulerService != null) {
                schedulerService.shutdown();
            }
            
            if (server != null && server.isStarted()) {
                server.stop();
            }
            
            logger.info("Scheduler Application stopped successfully");
        } catch (Exception e) {
            logger.error("Error stopping application: {}", e.getMessage(), e);
        }
    }

    private void printApiUsage(SchedulerConfig config) {
        String baseUrl = "http://localhost:" + config.getServerPort() + config.getContextPath();
        
        logger.info("\n" +
                "=== Scheduler API Usage ===\n" +
                "Base URL: {}\n" +
                "\n" +
                "1. Create Job:\n" +
                "   POST {}/create\n" +
                "   Content-Type: application/json\n" +
                "   Body: {\n" +
                "     \"jobId\": \"job-001\",\n" +
                "     \"jobName\": \"My Job\",\n" +
                "     \"jobGroup\": \"default\",\n" +
                "     \"scheduleTime\": \"2024-12-31T10:30:00\",\n" +
                "     \"description\": \"Test job\",\n" +
                "     \"recurring\": false\n" +
                "   }\n" +
                "\n" +
                "2. Reschedule Job:\n" +
                "   POST {}/reschedule\n" +
                "   Content-Type: application/json\n" +
                "   Body: {\n" +
                "     \"jobId\": \"job-001\",\n" +
                "     \"jobGroup\": \"default\",\n" +
                "     \"newScheduleTime\": \"2024-12-31T11:00:00\"\n" +
                "   }\n" +
                "\n" +
                "3. Cancel Job:\n" +
                "   POST {}/cancel\n" +
                "   Content-Type: application/json\n" +
                "   Body: {\n" +
                "     \"jobId\": \"job-001\",\n" +
                "     \"jobGroup\": \"default\"\n" +
                "   }\n" +
                "\n" +
                "4. Get All Jobs:\n" +
                "   GET {}/jobs\n" +
                "\n" +
                "5. Get Job Status:\n" +
                "   GET {}/status/job-001\n" +
                "\n" +
                "===============================", 
                baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl);
    }
}