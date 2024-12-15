package com.dsv.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
public class NodeConfig {
    private String id;
    private int port;
    private boolean isResource = false;
    private String ip;
    private Map<String, Integer> resourcePorts = new HashMap<>();
}