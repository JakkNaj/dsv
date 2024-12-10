package com.dsv.model;

import lombok.Data;

@Data
public class Message {
    private String senderId;
    private String targetId;
    private EMessageType type;
    private long timestamp;
    private String resourceId;
    private String content;
} 