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

@Slf4j
public class ResourceMessageService {
    private final Channel channel;
    private final String resourceId;
    private final ObjectMapper objectMapper;
    private final PriorityQueue<Message> resourceQueue;
    
    private static final String NODE_EXCHANGE = "nodes.topic";
    
    public ResourceMessageService(Channel channel, String resourceId) {
        this.channel = channel;
        this.resourceId = resourceId;
        this.objectMapper = new ObjectMapper();
        this.resourceQueue = new PriorityQueue<>((m1, m2) -> {
            int timestampCompare = Long.compare(m1.getTimestamp(), m2.getTimestamp());
            if (timestampCompare == 0) {
                return m1.getSenderId().compareTo(m2.getSenderId());
            }
            return timestampCompare;
        });
    }
    
    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Resource received message: type={}, from={}, timestamp={}", 
                message.getType(), message.getSenderId(), message.getTimestamp());
                        
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
            
            log.info("Resource sending message to node: type={}, to={}, timestamp={}", 
                message.getType(), message.getTargetId(), message.getTimestamp());
            
            channel.basicPublish(NODE_EXCHANGE, routingKey, null,
                objectMapper.writeValueAsBytes(message));
        } catch (Exception e) {
            log.error("Error sending message to node: {}", e.getMessage(), e);
        }
    }

    private void handleRequestAccess(Message message) {
        try {
            resourceQueue.add(message);
            
            Message response = new Message();
            response.setSenderId(resourceId);
            response.setTargetId(message.getSenderId());
            response.setType(EMessageType.QUEUE_UPDATE);
            response.setResourceId(resourceId);
            
            response.setContent(objectMapper.writeValueAsString(
                new ArrayList<>(resourceQueue)
            ));
            
            log.info("Resource {} received request from node {} with timestamp {}", 
                resourceId, 
                message.getSenderId(),
                message.getTimestamp());
            
            log.info("Current queue state for resource {}: {}", 
                resourceId,
                resourceQueue.stream()
                    .map(msg -> String.format("%s(ts:%d)", msg.getSenderId(), msg.getTimestamp()))
                    .collect(Collectors.joining(", "))
            );
            
            sendNodeMessage(response);
        } catch (Exception e) {
            log.error("Error handling request access: {}", e.getMessage(), e);
        }
    }

    private void handleReleaseAccess(Message message) {
        try {
            if (!resourceQueue.peek().getSenderId().equals(message.getSenderId())) {
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
            
            for (Message msg : resourceQueue) {
                queueUpdate.setTargetId(msg.getSenderId());
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
