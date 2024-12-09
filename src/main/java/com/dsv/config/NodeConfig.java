package com.dsv.config;

public class NodeConfig {

    private String id;
    private int port;

    public NodeConfig(String id, int port) {
        this.id = id;
        this.port = port;
    }

    // Default constructor for YAML
    public NodeConfig() {}

    public String getId() {
        return id;
    }

    public int getPort() {
        return port;
    }
}