package com.dsv.node;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import com.dsv.model.NodeStatus;
import com.dsv.model.ApiResponse;

import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
public class NodeController {
    private final Javalin app;
    private final String nodeId;
    private final NodeMessageService messageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NodeController(String nodeId, NodeMessageService messageService, int port) {
        this.nodeId = nodeId;
        this.messageService = messageService;
        this.app = setupServer(port);
    }

    private Javalin setupServer(int port) {
        return Javalin.create(config -> {
                config.http.defaultContentType = "application/json";
                config.jsonMapper(new JavalinJackson());
            })
            .get("/status", this::getStatus)
            .post("/message/{targetNodeId}", this::sendTestMessage)
            .post("/slowness/{milliseconds}", this::setSlowness)
            .post("/resource/{resourceId}/request", this::requestResource)
            .post("/resource/{resourceId}/enter", this::enterCriticalSection)
            .post("/resource/{resourceId}/exit", this::exitCriticalSection)
            .post("/resources/request", this::requestMultipleResources)
            .get("/health", this::healthCheck)
            .start(port);
    }

    private void getStatus(Context ctx) {
        log.info("Status request received");
        NodeStatus status = new NodeStatus(
            nodeId,
            messageService.getNodeStatus().toString(),
            messageService.getResourceQueues()
        );
        ctx.json(status);
    }

    private void requestResource(Context ctx) {
        String resourceId = ctx.pathParam("resourceId");
        log.info("Resource request received for resource: {}", resourceId);
        
        try {
            messageService.requestAccess(resourceId);
            ctx.status(202).json(new ApiResponse(
                true,
                "Resource request initiated for " + resourceId
            ));
        } catch (Exception e) {
            log.error("Error requesting resource: {}", e.getMessage());
            ctx.status(500).json(new ApiResponse(
                false,
                "Failed to request resource: " + e.getMessage()
            ));
        }
    }

    private void sendTestMessage(Context ctx) {
        String targetNodeId = ctx.pathParam("targetNodeId");
        String message = ctx.body();
        try {
            messageService.sendTestMessage(targetNodeId, message);
            ctx.json(new ApiResponse(true, "Message sent to " + targetNodeId));
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
            ctx.status(500).json(new ApiResponse(false, "Failed to send message: " + e.getMessage()));
        }
    }

    private void setSlowness(Context ctx) { 
        try {
            long milliseconds = Long.parseLong(ctx.pathParam("milliseconds"));
            messageService.setSlowness(milliseconds);
            ctx.json(new ApiResponse(true, "Node slowness set to " + milliseconds + " ms"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(new ApiResponse(false, "Invalid milliseconds value"));
        }
    }

    private void enterCriticalSection(Context ctx) {
        String resourceId = ctx.pathParam("resourceId");
        log.info("Request to enter critical section for resource: {}", resourceId);
        
        try {
            boolean entered = messageService.enterCriticalSection(resourceId);
            if (entered) {
                ctx.status(200).json(new ApiResponse(
                    true,
                    "Entered critical section for " + resourceId
                ));
            } else {
                ctx.status(403).json(new ApiResponse(
                    false,
                    "Cannot enter critical section at this time"
                ));
            }
        } catch (Exception e) {
            log.error("Error entering critical section: {}", e.getMessage());
            ctx.status(500).json(new ApiResponse(
                false,
                "Failed to enter critical section: " + e.getMessage()
            ));
        }
    }

    private void exitCriticalSection(Context ctx) {
        String resourceId = ctx.pathParam("resourceId");
        log.info("Request to exit critical section for resource: {}", resourceId);
        
        try {
            messageService.exitCriticalSection(resourceId);
            ctx.status(200).json(new ApiResponse(
                true,
                "Exited critical section for " + resourceId
            ));
        } catch (Exception e) {
            log.error("Error exiting critical section: {}", e.getMessage());
            ctx.status(500).json(new ApiResponse(
                false,
                "Failed to exit critical section: " + e.getMessage()
            ));
        }
    }

    private void requestMultipleResources(Context ctx) {
        try {
            List<String> resourceIds = objectMapper.readValue(
                ctx.body(), 
                new TypeReference<List<String>>() {}
            );
            
            log.info("Received request for multiple resources: {}", resourceIds);
            
            messageService.requestMultipleResources(resourceIds);
            
            ctx.status(202).json(new ApiResponse(
                true,
                "Resource requests initiated for: " + String.join(", ", resourceIds)
            ));
        } catch (Exception e) {
            log.error("Error requesting multiple resources: {}", e.getMessage());
            ctx.status(500).json(new ApiResponse(
                false,
                "Failed to request resources: " + e.getMessage()
            ));
        }
    }

    private void healthCheck(Context ctx) {
        ctx.status(200).json(new ApiResponse(true, "Node " + nodeId + " is healthy"));
    }
}
