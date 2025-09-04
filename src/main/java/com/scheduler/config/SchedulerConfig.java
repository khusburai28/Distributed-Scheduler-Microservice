package com.scheduler.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class SchedulerConfig {
    private static final Properties properties = new Properties();
    private static SchedulerConfig instance;
    
    private final String instanceId;
    private final int serverPort;
    private final String kafkaBootstrapServers;
    private final String contextPath;

    private SchedulerConfig() {
        loadProperties();
        this.instanceId = properties.getProperty("scheduler.instance.id", UUID.randomUUID().toString());
        this.serverPort = Integer.parseInt(properties.getProperty("scheduler.server.port", "8080"));
        this.kafkaBootstrapServers = properties.getProperty("kafka.bootstrap.servers", "localhost:9092");
        this.contextPath = properties.getProperty("scheduler.context.path", "/sch");
    }

    public static synchronized SchedulerConfig getInstance() {
        if (instance == null) {
            instance = new SchedulerConfig();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load application.properties, using defaults: " + e.getMessage());
        }
        
        properties.putAll(System.getProperties());
    }

    public String getInstanceId() {
        return instanceId;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public String toString() {
        return "SchedulerConfig{" +
                "instanceId='" + instanceId + '\'' +
                ", serverPort=" + serverPort +
                ", kafkaBootstrapServers='" + kafkaBootstrapServers + '\'' +
                ", contextPath='" + contextPath + '\'' +
                '}';
    }
}