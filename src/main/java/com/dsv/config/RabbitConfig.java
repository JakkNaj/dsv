package com.dsv.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RabbitConfig {
    private String host;
    private int port;
    private String username;
    private String password;
}