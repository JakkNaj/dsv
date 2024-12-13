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
    
    private static final String RESOURCE_EXCHANGE = "resources.topic";
    
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
                case REQUEST_ACCESS:
                    log.info("Processing REQUEST_ACCESS from node {}", message.getSenderId());
                    requestAccess(message.getResourceId());
                    break;
                case GRANT_ACCESS:
                    log.info("Processing GRANT_ACCESS from resource {}", message.getSenderId());
                    handleGrantAccess(message);
                    break;
                case DENY_ACCESS:
                    log.info("Processing DENY_ACCESS from resource {}", message.getSenderId());
                    //TODO: reakce na odepření přístupu ke ZDROJI
                    break;
                case VALUE_RESPONSE:
                    log.info("Processing VALUE_RESPONSE from resource {}", message.getSenderId());
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
    
// ----------------------- Metoda pro posílání zpráv do node queue -----------------------
    private void sendNodeMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + ".node." + 
                message.getType().toString().toLowerCase();
            
            log.info("Node sending message to node: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            channel.basicPublish(exchangeName, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message to node: {}", e.getMessage(), e);
        }
    }

// ----------------------- Metoda pro posílání zpráv do resource queue -----------------------
    private void sendResourceMessage(Message message) {
        try {
            String routingKey = message.getTargetId() + ".resource." + 
                message.getType().toString().toLowerCase();
            
            log.info("Node sending message to resource: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            channel.basicPublish(RESOURCE_EXCHANGE, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message to resource: {}", e.getMessage(), e);
        }
    }

// ----------------------- Metody pro zpracování zpráv ---------------------------------------
    
    // požádání o přidělení ZDROJE
    public void requestAccess(String resourceId) {
        simulateSlowness();
        Message request = new Message();
        request.setSenderId(nodeId);
        request.setType(EMessageType.REQUEST_ACCESS);
        request.setTimestamp(++lamportClock);
        request.setResourceId(resourceId);
        request.setTargetId(resourceId);
        
        sendResourceMessage(request);
    }

    // reakce na získání přístupu ke ZDROJI
    private void handleGrantAccess(Message message) {
        log.info("Node received GRANT_ACCESS from resource {}", message.getSenderId());
        /* try {
            Thread.sleep(2000); // Wait before reading critical value
            
            Message readRequest = new Message();
            readRequest.setSenderId(nodeId);
            readRequest.setTargetId(message.getSenderId());
            readRequest.setType(EMessageType.READ_CRITIC_VALUE);
            readRequest.setTimestamp(++lamportClock);
            readRequest.setResourceId(message.getResourceId());
            
            sendResourceMessage(readRequest);
            
        } catch (Exception e) {
            log.error("Error handling GRANT_ACCESS: {}", e.getMessage());
        } */
    }

    // reakce na získání hodnoty kritické sekce ze ZDROJE
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
            
            sendResourceMessage(release);
            
        } catch (Exception e) {
            log.error("Error handling VALUE_RESPONSE: {}", e.getMessage());
        }
    }

    // testovací zpráva pro testování komunikace mezi nody
    public void sendTestMessage(String targetNodeId, String content) {
        simulateSlowness();
        Message msg = new Message();
        msg.setSenderId(nodeId);
        msg.setTargetId(targetNodeId);
        msg.setType(EMessageType.CONNECTION_TEST);
        msg.setTimestamp(++lamportClock);
        msg.setContent(content);
        
        sendNodeMessage(msg);
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

    private void updateLamportClock(long messageTimestamp) {
        lamportClock = Math.max(lamportClock, messageTimestamp) + 1;
    }
}