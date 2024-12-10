package com.dsv.messaging;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.dsv.resource.ResourceManager;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.HashMap;
import lombok.Getter;
import lombok.Setter;

@Slf4j
public class MessageService {
    private final Channel channel;
    private final String nodeId;
    private final String exchangeName;
    private final ResourceManager resourceManager;
    private final ObjectMapper objectMapper;
    private long lamportClock;

    // ms to slow every message
    @Getter
    @Setter
    private long slowness = 0;
    
    public MessageService(Channel channel, String nodeId, String exchangeName, 
                         ResourceManager resourceManager) {
        this.channel = channel;
        this.nodeId = nodeId;
        this.exchangeName = exchangeName;
        this.resourceManager = resourceManager;
        this.objectMapper = new ObjectMapper();
        this.lamportClock = 0;
    }
    
    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Received message: type={}, from={}, timestamp={}", 
                message.getType(), message.getSenderId(), message.getTimestamp());
            
            updateLamportClock(message.getTimestamp());
            
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
                    break;
                case GRANT_ACCESS:
                    log.info("Processing GRANT_ACCESS from node {}", message.getSenderId());
                    handleGrantAccess(message);
                    break;
                case DENY_ACCESS:
                    log.info("Processing DENY_ACCESS from node {}", message.getSenderId());
                    break;
                case VALUE_RESPONSE:
                    log.info("Processing VALUE_RESPONSE from node {}", message.getSenderId());
                    handleValueResponse(message);
                    break;
                case READ_CRITIC_VALUE:
                    log.info("Processing READ_CRITIC_VALUE from node {}", message.getSenderId());
                    readCriticalValue(message);
                    break;
                case WRITE_CRITIC_VALUE:
                //TODO: změna kritické hodnoty
                    log.info("Processing WRITE_CRITIC_VALUE from node {}", message.getSenderId());
                    try {
                        resourceManager.writeValue(message.getSenderId(), Integer.parseInt(message.getContent()));
                    } catch (Exception e) {
                        log.error("Error writing critical value: {}", e.getMessage(), e);
                    }
                    break;    
                default:
                    log.warn("Unhandled message type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
        }
    }

    // only on node methods, do not slow RESOURCE part of the node
    private void simulateSlowness() {
        if (slowness > 0) {
            try {
                Thread.sleep(slowness);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void requestAccess(String resourceId) {
        simulateSlowness();
        Message request = new Message();
        request.setSenderId(nodeId);
        request.setType(EMessageType.REQUEST_ACCESS);
        request.setTimestamp(++lamportClock);
        request.setResourceId(resourceId);
        
        sendMessage(request);
    }
    
    private void handleRequestAccess(Message message) {
        simulateSlowness();
        if (resourceManager.canGrantAccess(message)) {
            sendGrantAccess(message.getSenderId());
        } else {
            sendDenyAccess(message.getSenderId());
        }
    }
    
    private void handleReleaseAccess(Message message) {
        simulateSlowness();
        resourceManager.releaseAccess(message.getSenderId());
    }
    
    private void sendGrantAccess(String targetNodeId) {
        Message response = new Message();
        response.setSenderId(nodeId);
        response.setTargetId(targetNodeId);
        response.setType(EMessageType.GRANT_ACCESS);
        response.setTimestamp(++lamportClock);
        
        sendMessage(response);
    }
    
    private void sendDenyAccess(String targetNodeId) {
        Message response = new Message();
        response.setSenderId(nodeId);
        response.setTargetId(targetNodeId);
        response.setType(EMessageType.DENY_ACCESS);
        response.setTimestamp(++lamportClock);
        
        sendMessage(response);
    }
    
    private void sendMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + "." + 
                (message.getType().toString().toLowerCase());
            
            log.info("Sending message: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            Map<String, Object> headers = new HashMap<>();
            headers.put("timestamp", lamportClock);
            headers.put("sender", nodeId);
            
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .build();
            
            channel.basicPublish(exchangeName, routingKey, properties,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }
    
    private void updateLamportClock(long messageTimestamp) {
        lamportClock = Math.max(lamportClock, messageTimestamp) + 1;
    }

    public void sendTestMessage(String targetNodeId, String message) {
        simulateSlowness();
        Message msg = new Message();
        msg.setSenderId(nodeId);
        msg.setTargetId(targetNodeId);
        msg.setType(EMessageType.CONNECTION_TEST);
        msg.setTimestamp(++lamportClock);
        msg.setContent(message);
        
        try {
            sendMessage(msg);
            log.info("Test message sent to {}: {}", targetNodeId, message);
        } catch (Exception e) {
            log.error("Failed to send test message: {}", e.getMessage());
            throw e;
        }
    }

    private void handleGrantAccess(Message message) {
        try {
            // Počkáme 2 sekund před čtením kritické hodnoty
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted", e);
            }
            // Pošleme požadavek na čtení hodnoty
            Message readRequest = new Message();
            readRequest.setSenderId(nodeId);
            readRequest.setTargetId(message.getSenderId());
            readRequest.setType(EMessageType.READ_CRITIC_VALUE);
            readRequest.setTimestamp(++lamportClock);
            readRequest.setResourceId(message.getResourceId());
            sendMessage(readRequest);
            
        } catch (Exception e) {
            log.error("Error handling GRANT_ACCESS: {}", e.getMessage());
        }
    }

    private void handleValueResponse(Message message) {
        try {
            int value = Integer.parseInt(message.getContent());
            log.info("Successfully accessed critical section for resource: {}. Value: {}", 
                     message.getResourceId(), value);
            // Počkáme 5 sekund před uvolněním zdroje
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted", e);
            }
            // Uvolníme zdroj
            Message release = new Message();
            release.setSenderId(nodeId);
            release.setTargetId(message.getSenderId());
            release.setType(EMessageType.RELEASE_ACCESS);
            release.setTimestamp(++lamportClock);
            release.setResourceId(message.getResourceId());
            sendMessage(release);
            
        } catch (Exception e) {
            log.error("Error handling VALUE_RESPONSE: {}", e.getMessage());
        }
    }

    private void readCriticalValue(Message message) {
        try {
            int value = resourceManager.readValue(message.getSenderId());
            // Pošleme odpověď s hodnotou
            Message response = new Message();
            response.setType(EMessageType.VALUE_RESPONSE);
            response.setSenderId(nodeId);
            response.setTargetId(message.getSenderId());
            response.setContent(String.valueOf(value));
            response.setResourceId(message.getResourceId());
            sendMessage(response);
        } catch (Exception e) {
            log.error("Error reading critical value: {}", e.getMessage());
        }
    }
} 
