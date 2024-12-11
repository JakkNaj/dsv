package com.dsv.node;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;

@Slf4j
public class NodeMessageService {
    private final Channel channel;
    private final String nodeId;
    private final String exchangeName;
    private final ObjectMapper objectMapper;
    private long lamportClock;

    @Getter
    @Setter
    private long slowness = 0;
    
    public NodeMessageService(Channel channel, String nodeId, String exchangeName) {
        this.channel = channel;
        this.nodeId = nodeId;
        this.exchangeName = exchangeName;
        this.objectMapper = new ObjectMapper();
        this.lamportClock = 0;
    }

    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Node received message: type={}, from={}, timestamp={}", 
                message.getType(), message.getSenderId(), message.getTimestamp());
            
            updateLamportClock(message.getTimestamp());
            
            switch (message.getType()) {
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
                case CONNECTION_TEST:
                    log.info("Processing CONNECTION_TEST from node {}", message.getSenderId());
                    break;
                default:
                    log.warn("Node received unhandled message type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing message in node: {}", e.getMessage(), e);
        }
    }

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
        
        // Send to resource exchange
        request.setTargetId(resourceId);
        sendMessage(request);
    }

    private void handleGrantAccess(Message message) {
        try {
            Thread.sleep(2000); // Wait before reading critical value
            
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
            
            Thread.sleep(5000); // Wait before releasing
            
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

    private void sendMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + ".node." + 
                message.getType().toString().toLowerCase();
            
            log.info("Node sending message: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            channel.basicPublish(exchangeName, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message from node: {}", e.getMessage(), e);
        }
    }
    
    private void updateLamportClock(long messageTimestamp) {
        lamportClock = Math.max(lamportClock, messageTimestamp) + 1;
    }

    public void sendTestMessage(String targetNodeId, String content) {
        simulateSlowness();
        Message msg = new Message();
        msg.setSenderId(nodeId);
        msg.setTargetId(targetNodeId);
        msg.setType(EMessageType.CONNECTION_TEST);
        msg.setTimestamp(++lamportClock);
        msg.setContent(content);
        
        sendMessage(msg);
    }
}