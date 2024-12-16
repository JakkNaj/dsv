package com.dsv.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class RabbitConfig {
    private List<RabbitHost> hosts;
    private String username;
    private String password;
    
    @Data
    @NoArgsConstructor
    public static class RabbitHost {
        private String host;
        private int port;
    }
}