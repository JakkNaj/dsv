package com.dsv.config;

import lombok.Data;
import java.util.Map;

@Data
public class AppConfig {
    private RabbitConfig rabbitmq;
    private Map<String, NodeConfig> nodes;
}