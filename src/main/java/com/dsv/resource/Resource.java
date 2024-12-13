package com.dsv.resource;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Resource {
    private final String resourceId;
    private final String ownerId;
    private final Channel channel;
    private final String exchangeName;
    private ResourceMessageService messageService;
    private ResourceManager resourceManager;
    
    public Resource(String resourceId, String ownerId, Channel channel, String exchangeName) {
        this.resourceId = resourceId;
        this.ownerId = ownerId;
        this.channel = channel;
        this.exchangeName = exchangeName;
    }
    
    public void start() {
        setupQueue();
        this.resourceManager = new ResourceManager(resourceId, ownerId);
        this.messageService = new ResourceMessageService(channel, resourceId, exchangeName, resourceManager);
        log.info("Resource {} initialized with resource manager", resourceId);
    }
    
    private void setupQueue() {
        try {
            // Deklarace exchange pro resource
            channel.exchangeDeclare(exchangeName, "topic", true);
            
            String queueName = resourceId + "-queue";
            
            // Deklarace queue
            channel.queueDeclare(queueName, true, false, false, null);
            
            // Binding queue k exchange s routing key pro tento resource
            channel.queueBind(queueName, exchangeName, resourceId + ".resource.#");
            
            // Nastavení consumera
            channel.basicConsume(queueName, true, (consumerTag, message) -> {
                try {
                    messageService.handleMessage(
                        message.getEnvelope().getRoutingKey(),
                        message.getBody(),
                        message.getProperties()
                    );
                } catch (Exception e) {
                    log.error("Error processing message in resource {}: {}", resourceId, e.getMessage(), e);
                }
            }, consumerTag -> {
                log.warn("Consumer was cancelled for resource {}: {}", resourceId, consumerTag);
            });
            
            log.info("Resource {} initialized queue {} and consumer", resourceId, queueName);
        } catch (Exception e) {
            log.error("Failed to setup resource queue for {}", resourceId, e);
            throw new RuntimeException(e);
        }
    }
} 