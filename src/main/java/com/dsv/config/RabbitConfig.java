package com.dsv.config;

import lombok.Data;

@Data
public class RabbitConfig {
    private String host;
    private int port;
    private String username;
    private String password;
}