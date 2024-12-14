package com.dsv.model;

import lombok.Data;

@Data
public class NodeStatus {
    private final String nodeId;
    private final String status;
    private final String resourceQueues;
}