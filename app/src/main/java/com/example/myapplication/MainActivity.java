package com.example.myapplication;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Handler;
import android.os.Looper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MeshApp";
    private static final String SERVICE_ID = "com.example.myapplication";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final int MAX_SEEN_MESSAGE_IDS = 5000;
    public static final String PREFS_NAME = "ResQNetPrefs";
    public static final String KEY_USERNAME = "username";
    private static final String CHANNEL_ID = "mesh_messages";

    private static MainActivity instance;

    private String myShortId;
    private String currentStatus = "Idle";
    private boolean isEmergencyActive = false;
    
    private final Set<String> connectedEndpoints = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> endpointIdToNodeMap = new ConcurrentHashMap<>();
    private final Set<String> seenMessageIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> pendingConnectionRequests = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> discoveredEndpointNames = new ConcurrentHashMap<>();
    
    private final Map<String, String> nodeIdToDisplayName = new ConcurrentHashMap<>();
    private final Map<String, Long> meshNodeLastSeen = new ConcurrentHashMap<>();
    private final Handler meshHandler = new Handler(Looper.getMainLooper());

    private final String[] REQUIRED_PERMISSIONS;

    {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        REQUIRED_PERMISSIONS = permissions.toArray(new String[0]);
    }

    public static MainActivity getInstance() { return instance; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = UUID.randomUUID().toString();
        myShortId = androidId.substring(Math.max(0, androidId.length() - 4)).toUpperCase();

        setupUI();
        startMeshHeartbeat();

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 1001);
        } else {
            startNearby();
        }
    }

    private void setupUI() {
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_discovery) selectedFragment = new DiscoveryFragment();
            else if (id == R.id.nav_chats) selectedFragment = new ChatListFragment();
            else if (id == R.id.nav_rescue) {
                startActivity(new Intent(this, RescueNavigationActivity.class));
                return true;
            } else if (id == R.id.nav_settings) selectedFragment = new SettingsFragment();
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new DiscoveryFragment()).commit();
        }
    }

    private void startMeshHeartbeat() {
        meshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                broadcastPresence();
                cleanupOldNodes();
                meshHandler.postDelayed(this, 30000);
            }
        }, 5000);
    }

    private void broadcastPresence() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "HELLO");
            json.put("msgId", UUID.randomUUID().toString());
            json.put("sender", getDisplayName());
            json.put("target", "ALL");
            broadcastToNeighbors(json.toString(), null);
        } catch (Exception ignored) {}
    }

    private void cleanupOldNodes() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String nodeId : new HashSet<>(meshNodeLastSeen.keySet())) {
            if (now - meshNodeLastSeen.get(nodeId) > 120000) {
                meshNodeLastSeen.remove(nodeId);
                nodeIdToDisplayName.remove(nodeId);
                changed = true;
            }
        }
        if (changed) notifyFragmentsDataChanged();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mesh Messages", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void showNotification(String senderName, String messageText, String nodeId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("node_id", nodeId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, nodeId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.status_dot).setContentTitle(senderName).setContentText(messageText)
                .setContentIntent(pi).setAutoCancel(true);
        NotificationManagerCompat.from(this).notify(nodeId.hashCode(), builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        meshHandler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
    }

    public String getMyShortId() { return myShortId; }
    public String getUserName() { return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USERNAME, "User"); }
    public String getDisplayName() { 
        if (isEmergencyActive) return "🆘 SOS " + myShortId;
        return getUserName() + " (" + myShortId + ")"; 
    }
    public String getStatus() { return currentStatus; }

    public List<String> getAllMeshNodeNames() {
        List<String> names = new ArrayList<>();
        for (String id : meshNodeLastSeen.keySet()) {
            String name = nodeIdToDisplayName.get(id);
            if (name != null) names.add(name);
        }
        for (String name : discoveredEndpointNames.values()) {
            if (!names.contains(name)) names.add(name);
        }
        Collections.sort(names);
        return names;
    }

    public List<String> getConnectedNodeNames() {
        List<String> names = new ArrayList<>();
        synchronized (connectedEndpoints) {
            for (String id : connectedEndpoints) {
                String name = endpointIdToNodeMap.get(id);
                if (name != null && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    public boolean isNodeDirect(String nodeIdOrName) {
        String id = extractId(nodeIdOrName);
        synchronized (connectedEndpoints) {
            for (String eid : connectedEndpoints) {
                if (extractId(endpointIdToNodeMap.get(eid)).equals(id)) return true;
            }
        }
        return false;
    }

    public void connectToNodeByName(String nodeName) {
        String endpointId = null;
        for (Map.Entry<String, String> entry : discoveredEndpointNames.entrySet()) {
            if (nodeName.equals(entry.getValue())) { endpointId = entry.getKey(); break; }
        }
        if (endpointId == null || connectedEndpoints.contains(endpointId) || pendingConnectionRequests.contains(endpointId)) return;
        final String targetId = endpointId;
        pendingConnectionRequests.add(targetId);
        Nearby.getConnectionsClient(this).requestConnection(getDisplayName(), targetId, connectionLifecycleCallback)
                .addOnFailureListener(e -> pendingConnectionRequests.remove(targetId));
    }

    public void sendMeshMessage(String targetIdOrName, String message) {
        try {
            String msgId = UUID.randomUUID().toString();
            markMessageSeen(msgId);
            String targetId = extractId(targetIdOrName);
            JSONObject json = new JSONObject();
            json.put("type", "MSG");
            json.put("msgId", msgId);
            json.put("sender", getDisplayName());
            json.put("target", targetId);
            String encryptedBody = CryptoUtils.encrypt(message);
            if (encryptedBody == null) return;
            json.put("body", encryptedBody);
            broadcastToNeighbors(json.toString(), null);
            addMessageToHistory(targetId, new Message(myShortId, message, true));
            
            // If it's a broadcast SOS, update local emergency state
            if (targetId.equals("ALL") && message.contains("SOS")) {
                setEmergencyMode(true);
            }
        } catch (Exception e) { Log.e(TAG, "Send error", e); }
    }

    public void setEmergencyMode(boolean active) {
        if (this.isEmergencyActive != active) {
            this.isEmergencyActive = active;
            // Restart advertising with SOS in the name so OTHERS WITHOUT THE APP see it in Bluetooth list
            startNearby();
        }
    }

    private void processReceivedData(String data, String fromEndpointId) {
        try {
            JSONObject json = new JSONObject(data);
            String msgId = json.getString("msgId");
            if (isMessageSeen(msgId)) return;
            markMessageSeen(msgId);

            String type = json.optString("type", "MSG");
            String sender = json.getString("sender");
            String senderId = extractId(sender);

            meshNodeLastSeen.put(senderId, System.currentTimeMillis());
            nodeIdToDisplayName.put(senderId, sender);

            if (type.equals("MSG")) {
                String target = json.getString("target");
                String body = json.getString("body");
                if (target.equals("ALL") || target.equalsIgnoreCase(myShortId)) {
                    String clearText = CryptoUtils.decrypt(body);
                    if (clearText != null) {
                        addMessageToHistory(sender, new Message(senderId, clearText, false));
                        // If we receive an SOS broadcast, it's public knowledge
                        if (target.equals("ALL") && clearText.contains("SOS")) {
                            Log.d(TAG, "Relaying SOS alert from " + sender);
                        }
                    }
                }
            }
            
            // RELAY: This is the core "hopping" logic. Every node that receives a new packet 
            // re-broadcasts it to all its neighbors EXCEPT the one it got it from.
            broadcastToNeighbors(data, fromEndpointId);
            notifyFragmentsDataChanged();
        } catch (Exception e) { Log.e(TAG, "Process error", e); }
    }

    private void addMessageToHistory(String nodeIdOrName, Message message) {
        String nodeId = extractId(nodeIdOrName);
        List<Message> history = MeshManager.getInstance().getMessages(nodeId);
        MeshManager.getInstance().addMessage(nodeId, message);
        
        if (!message.isMe()) {
            boolean isChatActive = ChatActivity.isActive() && nodeId.equalsIgnoreCase(extractId(ChatActivity.getCurrentNodeId()));
            if (!isChatActive) showNotification(nodeIdToDisplayName.getOrDefault(nodeId, nodeId), message.getText(), nodeIdOrName);
        }

        runOnUiThread(() -> {
            if (ChatActivity.isActive() && nodeId.equalsIgnoreCase(extractId(ChatActivity.getCurrentNodeId()))) {
                ChatActivity activity = ChatActivity.getInstance();
                if (activity != null) activity.updateMessages(MeshManager.getInstance().getMessages(nodeId));
            }
            notifyFragmentsDataChanged();
        });
    }

    private void broadcastToNeighbors(String data, String excludeId) {
        List<String> targets;
        synchronized (connectedEndpoints) { targets = new ArrayList<>(connectedEndpoints); }
        if (excludeId != null) targets.remove(excludeId);
        if (!targets.isEmpty()) Nearby.getConnectionsClient(this).sendPayload(targets, Payload.fromBytes(data.getBytes(StandardCharsets.UTF_8)));
    }

    public static String extractId(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) return nameOrId;
        int lastParen = nameOrId.lastIndexOf('(');
        int endParen = nameOrId.lastIndexOf(')');
        if (lastParen != -1 && endParen > lastParen + 1) return nameOrId.substring(lastParen + 1, endParen);
        return nameOrId;
    }

    private boolean hasPermissions() {
        for (String p : REQUIRED_PERMISSIONS) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private void markMessageSeen(String id) {
        synchronized (seenMessageIds) {
            seenMessageIds.add(id);
            if (seenMessageIds.size() > MAX_SEEN_MESSAGE_IDS) seenMessageIds.remove(seenMessageIds.iterator().next());
        }
    }

    private boolean isMessageSeen(String id) { synchronized (seenMessageIds) { return seenMessageIds.contains(id); } }

    public void startNearby() {
        Nearby.getConnectionsClient(this).stopAdvertising();
        Nearby.getConnectionsClient(this).stopDiscovery();
        Nearby.getConnectionsClient(this).startAdvertising(getDisplayName(), SERVICE_ID, connectionLifecycleCallback, new AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnSuccessListener(unused -> updateStatus("Active")).addOnFailureListener(e -> updateStatus("Adv Failed"));
        Nearby.getConnectionsClient(this).startDiscovery(SERVICE_ID, endpointDiscoveryCallback, new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    public void refreshNearby() { stopNearby(); startNearby(); }

    private void stopNearby() {
        Nearby.getConnectionsClient(this).stopAllEndpoints();
        Nearby.getConnectionsClient(this).stopAdvertising();
        Nearby.getConnectionsClient(this).stopDiscovery();
        connectedEndpoints.clear();
        endpointIdToNodeMap.clear();
        discoveredEndpointNames.clear();
        updateStatus("Stopped");
        notifyFragmentsDataChanged();
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String eid, @NonNull ConnectionInfo info) {
            endpointIdToNodeMap.put(eid, info.getEndpointName());
            Nearby.getConnectionsClient(MainActivity.this).acceptConnection(eid, payloadCallback);
        }
        @Override
        public void onConnectionResult(@NonNull String eid, @NonNull ConnectionResolution res) {
            if (res.getStatus().isSuccess()) { connectedEndpoints.add(eid); notifyFragmentsDataChanged(); }
        }
        @Override
        public void onDisconnected(@NonNull String eid) {
            connectedEndpoints.remove(eid);
            endpointIdToNodeMap.remove(eid);
            notifyFragmentsDataChanged();
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String eid, @NonNull DiscoveredEndpointInfo info) {
            String name = info.getEndpointName();
            if (name == null || name.equalsIgnoreCase(getDisplayName())) return;
            discoveredEndpointNames.put(eid, name);
            if (!connectedEndpoints.contains(eid) && !pendingConnectionRequests.contains(eid)) {
                pendingConnectionRequests.add(eid);
                Nearby.getConnectionsClient(MainActivity.this).requestConnection(getDisplayName(), eid, connectionLifecycleCallback)
                        .addOnFailureListener(e -> pendingConnectionRequests.remove(eid));
            }
            notifyFragmentsDataChanged();
        }
        @Override
        public void onEndpointLost(@NonNull String eid) {
            discoveredEndpointNames.remove(eid);
            notifyFragmentsDataChanged();
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String eid, @NonNull Payload p) {
            if (p.getType() == Payload.Type.BYTES && p.asBytes() != null) processReceivedData(new String(p.asBytes(), StandardCharsets.UTF_8), eid);
        }
        @Override
        public void onPayloadTransferUpdate(@NonNull String eid, @NonNull PayloadTransferUpdate u) {}
    };

    private void updateStatus(String status) {
        currentStatus = status;
        runOnUiThread(() -> {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof DiscoveryFragment) ((DiscoveryFragment) f).updateStatus(status);
        });
    }

    private void notifyFragmentsDataChanged() {
        runOnUiThread(() -> {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            List<String> nodes = getAllMeshNodeNames();
            if (f instanceof DiscoveryFragment) ((DiscoveryFragment) f).updateDiscoveredNodes(nodes);
            else if (f instanceof ChatListFragment) ((ChatListFragment) f).updateConnectedNodes(nodes);
        });
    }
}
