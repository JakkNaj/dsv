package com.dsv.health;

import lombok.extern.slf4j.Slf4j;
import com.dsv.config.AppConfig;
import com.dsv.config.NodeConfig;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@Slf4j
public class HealthChecker implements Runnable {
    private final Set<String> nodesToCheck;
    private final Map<String, NodeInfo> nodeInfoMap = new HashMap<>();
    private final HttpClient httpClient;
    private final Runnable onNodeFailure;
    private volatile boolean running = true;

    private static final int CHECK_INTERVAL = 5000;
    private static final int TIMEOUT = 3000;

    private static class NodeInfo {
        String ip;
        int port;

        NodeInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    public HealthChecker(Set<String> nodesToCheck, Runnable onNodeFailure, AppConfig config) {
        this.nodesToCheck = nodesToCheck;
        this.onNodeFailure = onNodeFailure;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT))
            .build();

        for (Map.Entry<String, NodeConfig> entry : config.getNodes().entrySet()) {
            NodeConfig nodeConfig = entry.getValue();
            if (nodesToCheck.contains(nodeConfig.getId())) {
                nodeInfoMap.put(
                    nodeConfig.getId(), 
                    new NodeInfo(nodeConfig.getIp(), nodeConfig.getPort())
                );
            }
        }

        log.info("HealthChecker initialized for nodes: {}", 
            String.join(", ", nodesToCheck));
    }

    @Override
    public void run() {
        while (running) {
            try {
                for (String nodeId : nodesToCheck) {
                    checkNodeHealth(nodeId);
                }
                Thread.sleep(CHECK_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void checkNodeHealth(String nodeId) {
        NodeInfo nodeInfo = nodeInfoMap.get(nodeId);
        if (nodeInfo == null) {
            log.error("No configuration found for node {}", nodeId);
            onNodeFailure.run();
            return;
        }

        try {
            String url = String.format("http://%s:%d/health", nodeInfo.ip, nodeInfo.port);
            log.info("Sending health check to node {} at {}", nodeId, url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(
                request, 
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.error("Node {} health check failed with status {}", 
                    nodeId, response.statusCode());
                onNodeFailure.run();
            } else {
                log.info("Node {} health check successful", nodeId);
            }
        } catch (Exception e) {
            log.error("Failed to check health of node {}: {}", 
                nodeId, e.getMessage());
            onNodeFailure.run();
        }
    }

    public void stop() {
        running = false;
    }
} 