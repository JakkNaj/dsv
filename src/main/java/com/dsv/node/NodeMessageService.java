package com.dsv.node;

import com.dsv.model.Message;
import com.dsv.model.EMessageType;
import com.dsv.model.ENodeStatus;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.PriorityQueue;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

@Slf4j
public class NodeMessageService {
    private final Channel channel;
    private final String nodeId;
    private final String exchangeName;
    private final ObjectMapper objectMapper;
    private final Map<String, PriorityQueue<Message>> resourceQueues;
    private Set<String> requestedResources;
    private Set<String> receivedQueues;

    @Getter
    @Setter
    private long slowness = 0;

    @Getter
    @Setter
    private ENodeStatus nodeStatus = ENodeStatus.IDLE;
    
    private static final String RESOURCE_EXCHANGE = "resources.topic";
    
    public NodeMessageService(Channel channel, String nodeId, String exchangeName) {
        this.channel = channel;
        this.nodeId = nodeId;
        this.exchangeName = exchangeName;
        this.objectMapper = new ObjectMapper();
        this.resourceQueues = new ConcurrentHashMap<>();
        this.requestedResources = new HashSet<>();
        this.receivedQueues = new HashSet<>();
    }

    public void handleMessage(String routingKey, byte[] body, AMQP.BasicProperties properties) {
        try {
            Message message = objectMapper.readValue(new String(body), Message.class);
            log.info("Node received message: type={}, from={}, timestamp={}", 
                message.getType(), message.getSenderId(), message.getTimestamp());
                        
            switch (message.getType()) {
                case REQUEST_ACCESS:
                    log.info("Processing REQUEST_ACCESS from node {}", message.getSenderId());
                    requestAccess(message.getResourceId());
                    break;
                case CONNECTION_TEST:
                    log.info("Processing CONNECTION_TEST from node {}", message.getSenderId());
                    break;
                case QUEUE_UPDATE:
                    handleQueueUpdate(message);
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
    
    // požádání o přidělení jednoho ZDROJE
    public void requestAccess(String resourceId) {
        if (nodeStatus != ENodeStatus.IDLE) {
            log.warn("Cannot request access while in {} state", nodeStatus);
            return;
        }
        simulateSlowness();
        Message request = new Message();
        request.setSenderId(nodeId);
        request.setType(EMessageType.REQUEST_ACCESS);
        request.setResourceId(resourceId);
        request.setTargetId(resourceId);
        request.setTimestamp(System.currentTimeMillis());

        nodeStatus = ENodeStatus.WAITING_FOR_RESOURCES_QUEUES;
        sendResourceMessage(request);
    }

     // požádání o více ZDROJŮ NAJEDNOU
     public void requestMultipleResources(List<String> resourceIds) {
        if (nodeStatus != ENodeStatus.IDLE) {
            throw new IllegalStateException("Cannot request resources while in " + nodeStatus + " state");
        }

        simulateSlowness();
        
        // Vytvoření společného timestampu pro všechny zprávy
        long commonTimestamp = System.currentTimeMillis();
        
        requestedResources.clear();
        receivedQueues.clear();
        requestedResources.addAll(resourceIds);
        
        nodeStatus = ENodeStatus.WAITING_FOR_RESOURCES_QUEUES;
        
        List<Message> requests = resourceIds.stream()
            .map(resourceId -> {
                Message request = new Message();
                request.setSenderId(nodeId);
                request.setType(EMessageType.REQUEST_ACCESS);
                request.setResourceId(resourceId);
                request.setTargetId(resourceId);
                request.setTimestamp(commonTimestamp);
                return request;
            })
            .collect(Collectors.toList());
        
        requests.forEach(this::sendResourceMessage);
        
        log.info("Sent batch resource requests with timestamp {} for resources: {}", 
            commonTimestamp, resourceIds);
    }

    // testovací zpráva pro testování komunikace mezi nody
    public void sendTestMessage(String targetNodeId, String content) {
        simulateSlowness();
        Message msg = new Message();
        msg.setSenderId(nodeId);
        msg.setTargetId(targetNodeId);
        msg.setType(EMessageType.CONNECTION_TEST);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
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

    // Pomocná metoda pro kontrolu, zda může node vstoupit do kritické sekce
    private boolean canEnterCriticalSection(String resourceId) {
        PriorityQueue<Message> queue = resourceQueues.get(resourceId);
        if (queue == null || queue.isEmpty()) {
            log.warn("No queue found for resource {} or queue is empty", resourceId);
            return false;
        }
        
        return queue.peek().getSenderId().equals(nodeId);
    }

    // vstup do kritické sekce (používání zdroje)
    public boolean enterCriticalSection(String resourceId) {
        if (nodeStatus != ENodeStatus.READY_TO_ENTER) {
            log.warn("Cannot enter critical section while in {} state", nodeStatus);
            return false;
        }

        if (!canEnterCriticalSection(resourceId)) {
            if (requestedResources.contains(resourceId)) {
                log.info("Node {} cannot enter critical section for resource {}, not first in queue", nodeId, resourceId);
                nodeStatus = ENodeStatus.WAITING_IN_QUEUE_FOR_RESOURCE;
            }
            log.info("Node {} cannot enter critical section for resource {}, request entry first", nodeId, resourceId);
            return false;
        }
        
        log.info("WORKING WITH RESOURCE {}", resourceId);
        nodeStatus = ENodeStatus.WORKING;
        return true;
    }

    // opuštění kritické sekce (uvolnění zdroje)
    public void exitCriticalSection(String resourceId) {
        if (nodeStatus != ENodeStatus.WORKING) {
            log.warn("Attempting to exit critical section while not in WORKING state");
            return;
        }
        
        Message releaseMessage = new Message();
        releaseMessage.setSenderId(nodeId);
        releaseMessage.setTargetId(resourceId);
        releaseMessage.setType(EMessageType.RELEASE_ACCESS);
        releaseMessage.setResourceId(resourceId);
        
        sendResourceMessage(releaseMessage);
        PriorityQueue<Message> queue = resourceQueues.get(resourceId);
        queue.poll();
        
        requestedResources.remove(resourceId);
        if (requestedResources.isEmpty()) {
            nodeStatus = ENodeStatus.IDLE;
            log.info("All resources released, changing state to IDLE");
        }
        
        log.info("Node {} released resource {}", nodeId, resourceId);
    }

    // zpracování zprávy o aktualizaci fronty (grafu závislosti)
    private void handleQueueUpdate(Message message) {
        try {
            List<Message> queueData = objectMapper.readValue(
                message.getContent(), 
                new TypeReference<List<Message>>() {}
            );
            
            PriorityQueue<Message> queue = new PriorityQueue<>((m1, m2) -> {
                int timestampCompare = Long.compare(m1.getTimestamp(), m2.getTimestamp());
                if (timestampCompare == 0) {
                    return m1.getSenderId().compareTo(m2.getSenderId());
                }
                return timestampCompare;
            });
            queue.addAll(queueData);
            
            resourceQueues.put(message.getResourceId(), queue);
            
            receivedQueues.add(message.getResourceId());
            
            log.info("Updated queue for resource {}: {}", 
                message.getResourceId(), 
                queue.stream()
                    .map(msg -> String.format("%s(ts:%d)", msg.getSenderId(), msg.getTimestamp()))
                    .collect(Collectors.joining(", "))
            );
            
            if (nodeStatus == ENodeStatus.WAITING_FOR_RESOURCES_QUEUES && 
                receivedQueues.containsAll(requestedResources)) {
                nodeStatus = ENodeStatus.READY_TO_ENTER;
                log.info("Received all requested queues, changing state to READY_TO_ENTER");
            }
        } catch (Exception e) {
            log.error("Error handling queue update: {}", e.getMessage(), e);
        }
    }

// ----------------------- Gettery a settery ---------------------------------------------

    public String getResourceQueues() {
        return resourceQueues.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + 
                entry.getValue().stream()
                    .map(msg -> String.format("%s(ts:%d)", msg.getSenderId(), msg.getTimestamp()))
                    .collect(Collectors.joining(", ")))
            .collect(Collectors.joining("\n"));
    }
}
