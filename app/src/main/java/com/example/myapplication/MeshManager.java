package com.example.myapplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton to manage mesh network state across different activities.
 */
public class MeshManager {
    private static MeshManager instance;
    
    private final Map<String, List<Message>> chatHistory = new ConcurrentHashMap<>();
    private final Set<String> discoveredDirectNodes = new HashSet<>();
    private final Set<String> connectedDirectNodes = new HashSet<>();
    
    // Tracks all nodes in the mesh (direct and indirect)
    // Key: Node ID, Value: Display Name
    private final Map<String, String> allMeshNodes = new ConcurrentHashMap<>();
    
    // Tracks topology: Node ID -> Set of its neighbors' IDs
    private final Map<String, Set<String>> meshTopology = new ConcurrentHashMap<>();
    
    public interface MessageListener {
        void onMessageReceived(String nodeId, Message message);
    }
    
    public interface MeshUpdateListener {
        void onMeshUpdated();
    }
    
    private final List<MessageListener> listeners = new ArrayList<>();
    private final List<MeshUpdateListener> meshListeners = new ArrayList<>();
    
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
    
    public void addMeshUpdateListener(MeshUpdateListener listener) {
        synchronized (meshListeners) {
            meshListeners.add(listener);
        }
    }

    public static String extractId(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) return nameOrId;
        int lastParen = nameOrId.lastIndexOf('(');
        int endParen = nameOrId.lastIndexOf(')');
        if (lastParen != -1 && endParen > lastParen + 1) {
            return nameOrId.substring(lastParen + 1, endParen);
        }
        return nameOrId;
    }
    
    public void addMessage(String nodeIdOrName, Message message) {
        String nodeId = extractId(nodeIdOrName);
        if (nodeId == null) return;
        
        List<Message> history = chatHistory.computeIfAbsent(nodeId, k -> new ArrayList<>());
        synchronized (history) {
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
        List<Message> history = chatHistory.get(nodeId);
        return history != null ? new ArrayList<>(history) : new ArrayList<>();
    }

    public void updateNodePresence(String nodeId, String displayName, Set<String> neighbors) {
        if (nodeId == null) return;
        allMeshNodes.put(nodeId, displayName);
        if (neighbors != null) {
            meshTopology.put(nodeId, new HashSet<>(neighbors));
        }
        notifyMeshUpdated();
    }

    public Map<String, String> getAllMeshNodes() {
        return new HashMap<>(allMeshNodes);
    }

    public Map<String, Set<String>> getMeshTopology() {
        return new HashMap<>(meshTopology);
    }
    
    public void setDiscoveredNodes(List<String> nodes) {
        synchronized (discoveredDirectNodes) {
            this.discoveredDirectNodes.clear();
            for (String n : nodes) this.discoveredDirectNodes.add(n);
        }
        notifyMeshUpdated();
    }
    
    public List<String> getDiscoveredNodes() {
        synchronized (discoveredDirectNodes) {
            return new ArrayList<>(discoveredDirectNodes);
        }
    }
    
    public void setConnectedNodes(List<String> nodes) {
        synchronized (connectedDirectNodes) {
            this.connectedDirectNodes.clear();
            for (String n : nodes) this.connectedDirectNodes.add(n);
        }
        notifyMeshUpdated();
    }
    
    public List<String> getConnectedNodes() {
        synchronized (connectedDirectNodes) {
            return new ArrayList<>(connectedDirectNodes);
        }
    }

    private void notifyMeshUpdated() {
        synchronized (meshListeners) {
            for (MeshUpdateListener listener : meshListeners) {
                listener.onMeshUpdated();
            }
        }
    }
}