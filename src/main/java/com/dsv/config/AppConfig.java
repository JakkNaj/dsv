package com.dsv.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
public class AppConfig {
    private RabbitConfig rabbitmq;
    private Map<String, NodeConfig> nodes = new HashMap<>();
}