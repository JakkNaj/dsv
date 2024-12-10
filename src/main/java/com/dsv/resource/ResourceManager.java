package com.dsv.resource;

import com.dsv.model.Message;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceManager {
    private final String resourceId;
    private final String ownerId;
    private final ReentrantLock lock = new ReentrantLock();
    private String currentHolder = null;
    private final PriorityQueue<Message> requestQueue;
    
    public ResourceManager(String resourceId, String ownerId) {
        this.resourceId = resourceId;
        this.ownerId = ownerId;
        this.requestQueue = new PriorityQueue<>((a, b) -> 
            Long.compare(a.getTimestamp(), b.getTimestamp()));
    }
    
    public boolean isAvailable() {
        lock.lock();
        try {
            return currentHolder == null;
        } finally {
            lock.unlock();
        }
    }
    
    public boolean canGrantAccess(Message request) {
        lock.lock();
        try {
            if (currentHolder != null) {
                requestQueue.offer(request);
                return false;
            }
            
            if (requestQueue.isEmpty() || requestQueue.peek().getTimestamp() > request.getTimestamp()) {
                currentHolder = request.getSenderId();
                return true;
            }
            
            requestQueue.offer(request);
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    public void releaseAccess(String nodeId) {
        lock.lock();
        try {
            if (currentHolder != null && currentHolder.equals(nodeId)) {
                currentHolder = null;
                processNextRequest();
            }
        } finally {
            lock.unlock();
        }
    }
    
    private void processNextRequest() {
        if (!requestQueue.isEmpty()) {
            Message nextRequest = requestQueue.poll();
            currentHolder = nextRequest.getSenderId();
            // Notify MessageService to send GRANT_ACCESS
        }
    }

    public String getResourceStatus() {
        if (currentHolder == null) {
            return "Resource " + resourceId + " is available";
        }
        return "Resource " + resourceId + " is held by " + currentHolder;
    }
} 