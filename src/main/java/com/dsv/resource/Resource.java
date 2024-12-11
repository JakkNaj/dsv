package com.dsv.resource;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Resource {
    private final String resourceId;
    private final String ownerId;
    private final Channel channel;
    private final String exchangeName;
    private final ResourceMessageService messageService;
    private final ResourceManager resourceManager;
    
    public Resource(String resourceId, String ownerId, Channel channel, String exchangeName) {
        this.resourceId = resourceId;
        this.ownerId = ownerId;
        this.channel = channel;
        this.exchangeName = exchangeName;
        this.resourceManager = new ResourceManager(resourceId, ownerId);
        this.messageService = new ResourceMessageService(channel, resourceId, exchangeName, resourceManager);
    }
    
    public void start() {
        setupQueue();
    }
    
    private void setupQueue() {
        try {
            String queueName = resourceId + "-resource-queue";
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, exchangeName, resourceId + ".resource.#");
            // ... setup consumer ...
        } catch (Exception e) {
            log.error("Failed to setup resource queue", e);
            throw new RuntimeException(e);
        }
    }
} 