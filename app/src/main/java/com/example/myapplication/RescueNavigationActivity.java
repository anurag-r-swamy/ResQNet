package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class RescueNavigationActivity extends AppCompatActivity {
    private static final String TAG = "RescueNav";
    private static final int LOCATION_REQUEST_CODE = 2001;
    private static final float ZOOM_LEVEL = 15f;

    private MapView mapView;
    private LocationManager locationManager;
    private Location currentLocation;
    private GeoPoint userLocation;

    private TextView gpsStatusText, nodeCountText, signalQualityText, batteryText, emergencyModeText;
    private Button shareLocationBtn, sosBtn;
    private ImageButton locateMeBtn;
    private LinearLayout statusPanel;

    private List<Marker> meshMarkers = new ArrayList<>();
    private List<Marker> emergencyMarkers = new ArrayList<>();
    private MyLocationNewOverlay myLocationOverlay;
    private Marker userMarker;

    private boolean emergencyMode = false;
    private boolean isOnline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescue_navigation);

        initializeViews();
        setupMap();
        setupLocationTracking();
        setupActionButtons();
        updateStatusPanel();

        View mainView = findViewById(R.id.rescue_nav_root);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void initializeViews() {
        statusPanel = findViewById(R.id.status_panel);
        gpsStatusText = findViewById(R.id.gps_status);
        nodeCountText = findViewById(R.id.node_count);
        signalQualityText = findViewById(R.id.signal_quality);
        batteryText = findViewById(R.id.battery_status);
        emergencyModeText = findViewById(R.id.emergency_mode);
        shareLocationBtn = findViewById(R.id.btn_share_location);
        sosBtn = findViewById(R.id.btn_sos);
        locateMeBtn = findViewById(R.id.btn_locate_me);
    }

    private void setupMap() {
        Configuration.getInstance().setUserAgentValue(getPackageName());
        mapView = findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(ZOOM_LEVEL);

        // MyLocation overlay (shows current GPS location)
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Optional user marker (visible always at center)
        userMarker = new Marker(mapView);
        userMarker.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation));
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        userMarker.setTitle("You");
        mapView.getOverlays().add(userMarker);

        // Mesh nodes will be represented by Marker objects; list initialized above

        // Emergency markers list initialized above
    }

    private void setupLocationTracking() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return;
        }

        startLocationUpdates();
        loadLastKnownLocation();
        visualizeMeshNodes();
    }

    private void loadLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown != null) {
                updateFromLocation(lastKnown, true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get last known GPS location: " + e.getMessage());
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,  // Min time 1 second
                1,     // Min distance 1 meter
                locationListener
        );
    }

    private void visualizeMeshNodes() {
        MainActivity main = MainActivity.getInstance();
        if (main == null) return;

        List<String> connectedNodes = main.getConnectedNodeNames();
        // Remove existing mesh markers from the map
        try {
            for (Marker m : meshMarkers) {
                mapView.getOverlays().remove(m);
            }
            meshMarkers.clear();
        } catch (Exception ignored) {}

        for (String nodeName : connectedNodes) {
            if (userLocation != null) {
                double offsetLat = (Math.random() - 0.5) * 0.01;  // ~500m variation
                double offsetLon = (Math.random() - 0.5) * 0.01;
                GeoPoint nodePoint = new GeoPoint(
                        userLocation.getLatitude() + offsetLat,
                        userLocation.getLongitude() + offsetLon
                );
                Marker nodeMarker = new Marker(mapView);
                nodeMarker.setPosition(nodePoint);
                nodeMarker.setIcon(ContextCompat.getDrawable(this, android.R.drawable.presence_online));
                nodeMarker.setTitle(nodeName);
                mapView.getOverlays().add(nodeMarker);
                meshMarkers.add(nodeMarker);
            }
        }

        // Draw signal paths between nodes
        drawSignalPaths();
    }

    private void drawSignalPaths() {
        // Remove any existing Polyline overlays
        try {
            List overlays = mapView.getOverlays();
            for (int i = overlays.size() - 1; i >= 0; i--) {
                Object o = overlays.get(i);
                if (o instanceof Polyline) overlays.remove(i);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not clean old paths: " + e.getMessage());
        }

        if (meshMarkers.size() > 0 && userLocation != null) {
            List<GeoPoint> pathPoints = new ArrayList<>();
            pathPoints.add(userLocation);
            for (Marker m : meshMarkers) {
                pathPoints.add((GeoPoint) m.getPosition());
            }
            if (pathPoints.size() > 1) {
                Polyline line = new Polyline();
                line.setPoints(pathPoints);
                line.setColor(Color.GREEN);
                line.setWidth(3);
                mapView.getOverlays().add(line);
            }
        }
    }

    private void setupActionButtons() {
        shareLocationBtn.setOnClickListener(v -> shareLocation());
        sosBtn.setOnClickListener(v -> triggerSOS());
        if (locateMeBtn != null) {
            locateMeBtn.setOnClickListener(v -> {
                if (currentLocation != null) {
                    centerMapOnCurrentLocation(true);
                    Toast.makeText(this, "Centered on your GPS location", Toast.LENGTH_SHORT).show();
                } else {
                    loadLastKnownLocation();
                    if (currentLocation == null) {
                        Toast.makeText(this, "GPS location not available yet", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void centerMapOnCurrentLocation(boolean zoomIn) {
        if (mapView == null || userLocation == null) return;
        mapView.getController().setCenter(userLocation);
        if (zoomIn) {
            mapView.getController().setZoom(17.0);
        }
        mapView.invalidate();
    }

    private void updateFromLocation(@NonNull Location location, boolean recenter) {
        currentLocation = location;
        userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (userMarker != null) {
            userMarker.setPosition(userLocation);
        }
        if (recenter) {
            centerMapOnCurrentLocation(false);
        }
        updateStatusPanel();
        cacheMapTile();
        visualizeMeshNodes();
    }

    private void shareLocation() {
        if (currentLocation == null) {
            Toast.makeText(this, "GPS not available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        String locationData = String.format(Locale.US,
                "My Location: %.6f, %.6f\nAccuracy: %.0fm\nApp: ResQNet (Offline Mesh)",
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                currentLocation.getAccuracy()
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, locationData);
        startActivity(Intent.createChooser(shareIntent, "Share My Location"));
    }

    private void triggerSOS() {
        emergencyMode = !emergencyMode;
        sosBtn.setBackgroundColor(emergencyMode ? Color.RED : Color.GRAY);
        emergencyModeText.setText(emergencyMode ? "EMERGENCY: ON" : "EMERGENCY: OFF");

        if (emergencyMode) {
            // Broadcast SOS via mesh
            MainActivity main = MainActivity.getInstance();
            if (main != null && currentLocation != null) {
                String sosMessage = String.format(Locale.US,
                        "SOS from %s at %.6f, %.6f",
                        main.getUserName(),
                        currentLocation.getLatitude(),
                        currentLocation.getLongitude()
                );
                main.sendMeshMessage("ALL", sosMessage);
            }
            Toast.makeText(this, "SOS ACTIVATED - Broadcasting via Mesh Network", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "SOS Deactivated", Toast.LENGTH_SHORT).show();
        }
    }

    private void cacheMapTile() {
        // Cache map data for 25-50km radius when online
        if (isOnline && currentLocation != null) {
            // Simple caching - in production would use proper offline tile storage
            Log.d(TAG, "Caching map tiles for region: " + currentLocation.getLatitude() + "," + currentLocation.getLongitude());
        }
    }

    private void updateStatusPanel() {
        MainActivity main = MainActivity.getInstance();
        
        // GPS Status
        if (currentLocation != null) {
            gpsStatusText.setText(String.format(Locale.US, "GPS: %.0fm accuracy", currentLocation.getAccuracy()));
        } else {
            gpsStatusText.setText("GPS: Searching...");
        }

        // Active Nodes
        if (main != null) {
            int nodeCount = main.getConnectedNodeNames().size();
            nodeCountText.setText("Nodes: " + nodeCount);
        }

        // Signal Quality
        signalQualityText.setText("Signal: " + (emergencyMode ? "SOS" : "Normal"));

        // Battery Status
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) (level / (float) scale * 100);
            batteryText.setText("Battery: " + batteryPct + "%");
        } else {
            batteryText.setText("Battery: --");
        }

        // Emergency Mode
        emergencyModeText.setText(emergencyMode ? "EMERGENCY: ON" : "Emergency: OFF");
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            visualizeMeshNodes();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        updateStatusPanel();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }
        mapView.onDetach();
        super.onDestroy();
    }
    
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            updateFromLocation(location, true);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            gpsStatusText.setText("GPS: Active");
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            gpsStatusText.setText("GPS: Disabled");
        }
    };
}


