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

@Slf4j
public class MessageService {
    private final Channel channel;
    private final String nodeId;
    private final String exchangeName;
    private final ResourceManager resourceManager;
    private final ObjectMapper objectMapper;
    private long lamportClock;
    
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
                default:
                    log.warn("Unhandled message type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
        }
    }
    
    public void requestAccess(String resourceId) {
        Message request = new Message();
        request.setSenderId(nodeId);
        request.setType(EMessageType.REQUEST_ACCESS);
        request.setTimestamp(++lamportClock);
        request.setResourceId(resourceId);
        
        sendMessage(request);
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

    public void sendMessage(String targetNodeId, String message) {
        Message msg = new Message();
        msg.setSenderId(nodeId);
        msg.setTargetId(targetNodeId);
        msg.setType(EMessageType.CONNECTION_TEST);
        msg.setTimestamp(++lamportClock);
        msg.setResourceId(message);
        sendMessage(msg);
    }
} 