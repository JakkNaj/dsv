package com.dsv.resource;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Queue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import com.dsv.health.HealthChecker;
import com.dsv.config.AppConfig;

@Slf4j
public class ResourceMessageService {
    private final Channel channel;
    private final String resourceId;
    private final ObjectMapper objectMapper;
    private final Queue<String> resourceQueue;
    private Thread healthCheckerThread;
    private HealthChecker healthChecker;
    private final AppConfig appConfig;
    
    private static final String NODE_EXCHANGE = "nodes.topic";
    
    public ResourceMessageService(Channel channel, String resourceId, AppConfig appConfig) {
        this.channel = channel;
        this.resourceId = resourceId;
        this.objectMapper = new ObjectMapper();
        this.resourceQueue = new LinkedList<>();
        this.appConfig = appConfig;
    }
    
    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Resource received message: type={}, from={}", 
                message.getType(), message.getSenderId());
                        
            switch (message.getType()) {
                case REQUEST_ACCESS:
                    log.info("Processing REQUEST_ACCESS from node {}", message.getSenderId());
                    handleRequestAccess(message);
                    break;
                case RELEASE_ACCESS:
                    log.info("Processing RELEASE_ACCESS from node {}", message.getSenderId());
                    handleReleaseAccess(message);
                    break;
                case CONNECTION_TEST:
                    log.info("Processing CONNECTION_TEST from node {}", message.getSenderId());
                    handleTestAccess(message);
                    break;
                case REMOVE_FROM_QUEUE:
                    log.info("Processing REMOVE_FROM_QUEUE from node {}", message.getSenderId());
                    handleRemoveFromQueue(message);
                    break;
                default:
                    log.warn("Resource received unhandled message type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message in resource: {}", e.getMessage(), e);
        }
    }

    private void sendNodeMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + ".node." + 
                message.getType().toString().toLowerCase();
            
            log.info("Resource sending message to node: type={}, to={}", 
                message.getType(), message.getTargetId());
            
            channel.basicPublish(NODE_EXCHANGE, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message to node: {}", e.getMessage(), e);
        }
    }

    private void handleRequestAccess(Message message) {
        try {
            String newNodeId = message.getSenderId();
            boolean wasEmpty = resourceQueue.isEmpty();
            resourceQueue.add(newNodeId);
            
            if (wasEmpty) {
                startHealthChecker(newNodeId);
            }
            
            Message response = new Message();
            response.setSenderId(resourceId);
            response.setTargetId(message.getSenderId());
            response.setType(EMessageType.QUEUE_UPDATE);
            response.setResourceId(resourceId);
            
            response.setContent(objectMapper.writeValueAsString(
                new ArrayList<>(resourceQueue)
            ));
            
            log.info("Current queue state for resource {}: {}", 
                resourceId,
                String.join(", ", resourceQueue));
            
            sendNodeMessage(response);
        } catch (Exception e) {
            log.error("Error handling request access: {}", e.getMessage(), e);
        }
    }

    private void handleReleaseAccess(Message message) {
        try {
            if (!resourceQueue.peek().equals(message.getSenderId())) {
                log.warn("Received RELEASE_ACCESS from node {} but it's not first in queue", 
                    message.getSenderId());
                return;
            }
            
            resourceQueue.poll();
            stopHealthChecker();
            
            String nextNodeId = resourceQueue.peek();
            if (nextNodeId != null) {
                startHealthChecker(nextNodeId);
            }
            
            Message queueUpdate = new Message();
            queueUpdate.setSenderId(resourceId);
            queueUpdate.setType(EMessageType.QUEUE_UPDATE);
            queueUpdate.setResourceId(resourceId);
            queueUpdate.setContent(objectMapper.writeValueAsString(
                new ArrayList<>(resourceQueue)
            ));
            
            for (String msg : resourceQueue) {
                queueUpdate.setTargetId(msg);
                sendNodeMessage(queueUpdate);
            }
            
            log.info("Resource {} queue updated after release from {}: {}", 
                resourceId, message.getSenderId(), resourceQueue);
            
        } catch (Exception e) {
            log.error("Error handling release access: {}", e.getMessage(), e);
        }
    }

    private void handleTestAccess(Message message) {
        log.info("Resource received CONNECTION_TEST from node {}", message.getSenderId());
    }

    private void handleNodeFailure(String failedNodeId) {
        log.warn("Node {} failed health check, releasing it from resource queue", failedNodeId);
        
        if (resourceQueue.peek().equals(failedNodeId)) {
            stopHealthChecker();
            resourceQueue.poll();
            
            String nextNodeId = resourceQueue.peek();
            if (nextNodeId != null) {
                startHealthChecker(nextNodeId);
            }
            
            Message queueUpdate = new Message();
            queueUpdate.setSenderId(resourceId);
            queueUpdate.setType(EMessageType.QUEUE_UPDATE);
            queueUpdate.setResourceId(resourceId);
            
            try {
                queueUpdate.setContent(objectMapper.writeValueAsString(
                    new ArrayList<>(resourceQueue)
                ));
                
                for (String nodeId : resourceQueue) {
                    queueUpdate.setTargetId(nodeId);
                    sendNodeMessage(queueUpdate);
                }
            } catch (Exception e) {
                log.error("Error sending queue update after node failure: {}", 
                    e.getMessage());
            }
        }
    }

    private void startHealthChecker(String nodeId) {
        stopHealthChecker();
        
        log.info("Starting health checker for node {}", nodeId);
        
        Runnable onNodeFailure = () -> {
            if (nodeId.equals(resourceQueue.peek())) {
                handleNodeFailure(nodeId);
            }
        };

        healthChecker = new HealthChecker(
            resourceId.split("-")[0],   // nodeId
            Set.of(nodeId),
            onNodeFailure,
            appConfig
        );

        healthCheckerThread = new Thread(healthChecker);
        healthCheckerThread.setDaemon(true);
        healthCheckerThread.start();
        
        log.info("Health checker started for node {}", nodeId);
    }

    private void stopHealthChecker() {
        if (healthChecker != null) {
            log.info("Stopping health checker");
            healthChecker.stop();
            healthChecker = null;
        }
        if (healthCheckerThread != null) {
            healthCheckerThread.interrupt();
            healthCheckerThread = null;
        }
    }

    public void stop() {
        stopHealthChecker();
    }

    private void handleRemoveFromQueue(Message message) {
        try {
            String nodeToRemove = message.getSenderId();
            if (resourceQueue.remove(nodeToRemove)) {
                log.info("Removed node {} from queue", nodeToRemove);
                
                // nahlášení ostatním node v queue, že se queue změnila
                Message queueUpdate = new Message();
                queueUpdate.setSenderId(resourceId);
                queueUpdate.setType(EMessageType.QUEUE_UPDATE);
                queueUpdate.setResourceId(resourceId);
                queueUpdate.setContent(objectMapper.writeValueAsString(
                    new ArrayList<>(resourceQueue)
                ));
                
                for (String nodeId : resourceQueue) {
                    queueUpdate.setTargetId(nodeId);
                    sendNodeMessage(queueUpdate);
                }
                
                log.info("Resource {} queue updated after removal of {}: {}", 
                    resourceId, nodeToRemove, resourceQueue);
            }
        } catch (Exception e) {
            log.error("Error handling remove from queue: {}", e.getMessage(), e);
        }
    }

} 
