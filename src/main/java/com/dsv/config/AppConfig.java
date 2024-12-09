package com.dsv.config;

import java.util.Map;

public class AppConfig {

    private RabbitConfig rabbitmq;
    private Map<String, NodeConfig> nodes;

    public AppConfig(RabbitConfig rabbitmq, Map<String, NodeConfig> nodes) {
        this.rabbitmq = rabbitmq;
        this.nodes = nodes;
    }

    public RabbitConfig getRabbitmq() {
        return rabbitmq;
    }

    public Map<String, NodeConfig> getNodes() {
        return nodes;
    }
}