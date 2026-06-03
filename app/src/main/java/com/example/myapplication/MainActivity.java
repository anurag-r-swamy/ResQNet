package com.example.myapplication;

import android.Manifest;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.common.api.ApiException;
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

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MeshApp";
    public static final String PREFS_NAME = "ResQNetPrefs";
    public static final String KEY_USERNAME = "username";

    private static MainActivity instance;
    private MeshService meshService;
    private boolean isBound = false;

    private String myShortId;
    
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

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshBinder binder = (MeshService.MeshBinder) service;
            meshService = binder.getService();
            isBound = true;
            Log.d(TAG, "Service bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            meshService = null;
            isBound = false;
        }
    };

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) androidId = UUID.randomUUID().toString();
        myShortId = androidId.substring(Math.max(0, androidId.length() - 4)).toUpperCase();

        BottomNavigationView navView = findViewById(R.id.bottom_navigation);
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_discovery) {
                selectedFragment = new DiscoveryFragment();
            } else if (id == R.id.nav_chats) {
                selectedFragment = new ChatListFragment();
            } else if (id == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DiscoveryFragment())
                    .commit();
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 1001);
        } else {
            startMeshService();
        }
    }

    private void startMeshService() {
        Intent intent = new Intent(this, MeshService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        if (instance == this) instance = null;
    }

    public String getMyShortId() { return myShortId; }

    public String getUserName() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USERNAME, "User");
    }

    public String getDisplayName() {
        return getUserName() + " (" + myShortId + ")";
    }

    public String getStatus() { 
        return isBound ? "Mesh Active" : "Starting..."; 
    }

    public List<String> getDiscoveredNodeNames() {
        return isBound ? meshService.getDiscoveredNodeNames() : new ArrayList<>();
    }

    public List<String> getConnectedNodeNames() {
        return isBound ? meshService.getConnectedNodeNames() : new ArrayList<>();
    }

    public void connectToNodeByName(String nodeName) {
        // In the new service-based model, we might want to automate connections
        // or let the service handle it. For now, let's just toast.
        Toast.makeText(this, "Automatic discovery & connection active", Toast.LENGTH_SHORT).show();
    }

    public List<Message> getMessagesForNode(String nodeIdOrName) {
        return MeshManager.getInstance().getMessages(nodeIdOrName);
    }

    public void openIndividualChat(String nodeIdOrName) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("node_id", nodeIdOrName);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    public String getDisplayNameForNode(String nodeId) {
        // This could be improved by keeping a mapping in MeshManager
        return nodeId;
    }

    private boolean hasPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasPermissions()) {
            startMeshService();
        }
    }

    public void refreshNearby() {
        if (isBound) meshService.startNearby();
    }

    public void sendMeshMessage(String targetIdOrName, String message) {
        if (!isBound) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String targetId = MeshManager.extractId(targetIdOrName);
        meshService.sendMeshMessage(targetId, message);
    }
}
