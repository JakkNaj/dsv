package com.dsv.node;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Node {
    private final String nodeId;
    private final Channel channel;
    private final String exchangeName;
    private NodeMessageService messageService;
    private NodeController controller;
    
    public Node(String nodeId, Channel channel, String exchangeName) {
        this.nodeId = nodeId;
        this.channel = channel;
        this.exchangeName = exchangeName;
    }
    
    public void start(int port) {
        setupQueue();
        this.messageService = new NodeMessageService(channel, nodeId, exchangeName);
        controller = new NodeController(nodeId, messageService, port);
    }
    
    private void setupQueue() {
        try {
            String queueName = nodeId + "-node-queue";
            
            // Deklarace queue
            channel.queueDeclare(queueName, true, false, false, null);
            
            // Binding queue k exchange s routing key pro tento node
            channel.queueBind(queueName, exchangeName, nodeId + ".node.#");
            
            // Nastavení consumera
            channel.basicConsume(queueName, true, (consumerTag, message) -> {
                try {
                    messageService.handleMessage(
                        message.getEnvelope().getRoutingKey(),
                        message.getBody(),
                        message.getProperties()
                    );
                } catch (Exception e) {
                    log.error("Error processing message in node {}: {}", nodeId, e.getMessage(), e);
                }
            }, consumerTag -> {
                log.warn("Consumer was cancelled for node {}: {}", nodeId, consumerTag);
            });
            
            log.info("Node {} initialized queue {} and consumer", nodeId, queueName);
        } catch (Exception e) {
            log.error("Failed to setup node queue for {}", nodeId, e);
            throw new RuntimeException(e);
        }
    }
} 