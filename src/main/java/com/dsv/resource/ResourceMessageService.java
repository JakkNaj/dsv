package com.dsv.resource;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.LinkedList;

@Slf4j
public class ResourceMessageService {
    private final Channel channel;
    private final String resourceId;
    private final ObjectMapper objectMapper;
    private final Queue<String> resourceQueue;
    
    private static final String NODE_EXCHANGE = "nodes.topic";
    
    public ResourceMessageService(Channel channel, String resourceId) {
        this.channel = channel;
        this.resourceId = resourceId;
        this.objectMapper = new ObjectMapper();
        this.resourceQueue = new LinkedList<>();
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
            resourceQueue.add(message.getSenderId());
            
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

} 
