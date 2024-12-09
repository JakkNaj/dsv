package com.dsv.config;

public class RabbitConfig {

    private String host;
    private int port;
    private String username;
    private String password;

    public RabbitConfig(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    // Default constructor for YAML
    public RabbitConfig() {}

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}