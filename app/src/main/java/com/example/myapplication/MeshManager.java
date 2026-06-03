package com.example.myapplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton to manage mesh network state across different activities.
 */
public class MeshManager {
    private static MeshManager instance;
    
    private final Map<String, List<Message>> chatHistory = new HashMap<>();
    private final List<String> discoveredNodes = new ArrayList<>();
    private final List<String> connectedNodes = new ArrayList<>();
    
    public interface MessageListener {
        void onMessageReceived(String nodeId, Message message);
    }
    
    private final List<MessageListener> listeners = new ArrayList<>();
    
    private MeshManager() {}
    
    public static synchronized MeshManager getInstance() {
        if (instance == null) {
            instance = new MeshManager();
        }
        return instance;
    }

    public void addListener(MessageListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(MessageListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public static String extractId(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) return nameOrId;
        int lastParen = nameOrId.lastIndexOf('(');
        int endParen = nameOrId.lastIndexOf(')');
        if (lastParen != -1 && endParen > lastParen) {
            return nameOrId.substring(lastParen + 1, endParen);
        }
        return nameOrId;
    }
    
    public void addMessage(String nodeIdOrName, Message message) {
        String nodeId = extractId(nodeIdOrName);
        if (nodeId == null) return;
        synchronized (chatHistory) {
            List<Message> history = chatHistory.get(nodeId);
            if (history == null) {
                history = new ArrayList<>();
                chatHistory.put(nodeId, history);
            }
            history.add(message);
        }
        
        synchronized (listeners) {
            for (MessageListener listener : listeners) {
                listener.onMessageReceived(nodeId, message);
            }
        }
    }
    
    public List<Message> getMessages(String nodeIdOrName) {
        String nodeId = extractId(nodeIdOrName);
        synchronized (chatHistory) {
            List<Message> history = chatHistory.get(nodeId);
            return history != null ? new ArrayList<>(history) : new ArrayList<>();
        }
    }
    
    public void setDiscoveredNodes(List<String> nodes) {
        synchronized (discoveredNodes) {
            this.discoveredNodes.clear();
            this.discoveredNodes.addAll(nodes);
        }
    }
    
    public List<String> getDiscoveredNodes() {
        synchronized (discoveredNodes) {
            return new ArrayList<>(discoveredNodes);
        }
    }
    
    public void setConnectedNodes(List<String> nodes) {
        synchronized (connectedNodes) {
            this.connectedNodes.clear();
            this.connectedNodes.addAll(nodes);
        }
    }
    
    public List<String> getConnectedNodes() {
        synchronized (connectedNodes) {
            return new ArrayList<>(connectedNodes);
        }
    }
}