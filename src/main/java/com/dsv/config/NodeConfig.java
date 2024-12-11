package com.dsv.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NodeConfig {
    private String id;
    private int port;
    private boolean isResource = false;
}