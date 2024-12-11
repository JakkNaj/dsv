package com.dsv.resource;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceMessageService {
    private final Channel channel;
    private final String resourceId;
    private final String exchangeName;
    private final ResourceManager resourceManager;
    private final ObjectMapper objectMapper;
    private long lamportClock;
    
    public ResourceMessageService(Channel channel, String resourceId, String exchangeName, 
                                ResourceManager resourceManager) {
        this.channel = channel;
        this.resourceId = resourceId;
        this.exchangeName = exchangeName;
        this.resourceManager = resourceManager;
        this.objectMapper = new ObjectMapper();
        this.lamportClock = 0;
    }
    
    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Resource received message: type={}, from={}, timestamp={}", 
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
                case READ_CRITIC_VALUE:
                    log.info("Processing READ_CRITIC_VALUE from node {}", message.getSenderId());
                    handleReadCriticalValue(message);
                    break;
                case WRITE_CRITIC_VALUE:
                    log.info("Processing WRITE_CRITIC_VALUE from node {}", message.getSenderId());
                    handleWriteCriticalValue(message);
                    break;
                default:
                    log.warn("Resource received unhandled message type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message in resource: {}", e.getMessage(), e);
        }
    }

    private void handleRequestAccess(Message message) {
        if (resourceManager.canGrantAccess(message)) {
            sendGrantAccess(message.getSenderId());
        } else {
            sendDenyAccess(message.getSenderId());
        }
    }
    
    private void handleReleaseAccess(Message message) {
        resourceManager.releaseAccess(message.getSenderId());
    }
    
    private void handleReadCriticalValue(Message message) {
        try {
            int value = resourceManager.readValue(message.getSenderId());
            Message response = new Message();
            response.setType(EMessageType.VALUE_RESPONSE);
            response.setSenderId(resourceId);
            response.setTargetId(message.getSenderId());
            response.setContent(String.valueOf(value));
            response.setResourceId(resourceId);
            sendMessage(response);
        } catch (Exception e) {
            log.error("Error reading critical value: {}", e.getMessage());
        }
    }
    
    private void handleWriteCriticalValue(Message message) {
        try {
            resourceManager.writeValue(message.getSenderId(), 
                Integer.parseInt(message.getContent()));
        } catch (Exception e) {
            log.error("Error writing critical value: {}", e.getMessage());
        }
    }
    
    private void sendGrantAccess(String targetNodeId) {
        Message response = new Message();
        response.setSenderId(resourceId);
        response.setTargetId(targetNodeId);
        response.setType(EMessageType.GRANT_ACCESS);
        response.setTimestamp(++lamportClock);
        
        sendMessage(response);
    }
    
    private void sendDenyAccess(String targetNodeId) {
        Message response = new Message();
        response.setSenderId(resourceId);
        response.setTargetId(targetNodeId);
        response.setType(EMessageType.DENY_ACCESS);
        response.setTimestamp(++lamportClock);
        
        sendMessage(response);
    }
    
    private void sendMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + ".resource." + 
                message.getType().toString().toLowerCase();
            
            log.info("Resource sending message: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            channel.basicPublish(exchangeName, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message from resource: {}", e.getMessage(), e);
        }
    }
    
    private void updateLamportClock(long messageTimestamp) {
        lamportClock = Math.max(lamportClock, messageTimestamp) + 1;
    }
} 