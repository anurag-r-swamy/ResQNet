# Rescue Navigation - Developer Implementation Guide

## Architecture Overview

```
┌─────────────────────────────┐
│   RescueNavigationActivity  │
│     (Main Activity)         │
└────────────┬────────────────┘
             │
     ┌───────┴────────┬──────────────┬─────────────┐
     │                │              │             │
     ↓                ↓              ↓             ↓
  MapView        LocationManager  MainActivity   Status Panel
  (OSMDroid)     (GPS Tracking)    (Mesh Net)    (TextView UI)
     │                │              │             │
     └────────────────┴──────────────┴─────────────┘
              │
          Overlays:
     - User Marker
     - Mesh Nodes
     - Emergency Locations
     - Signal Paths
```

## File Structure

```
app/src/main/
├── java/com/example/myapplication/
│   ├── RescueNavigationActivity.java      [NEW] Main activity
│   ├── MainActivity.java                  [MODIFIED] Added nav item
│   ├── CryptoUtils.java                   [EXISTING] Encryption
│   └── ...
├── res/
│   ├── layout/
│   │   └── activity_rescue_navigation.xml [NEW] Main layout
│   ├── drawable/
│   │   ├── button_style_primary.xml       [NEW] Share button style
│   │   └── button_style_sos.xml           [NEW] SOS button style
│   ├── menu/
│   │   └── bottom_nav_menu.xml            [MODIFIED] Added rescue nav item
│   └── ...
└── AndroidManifest.xml                    [MODIFIED] Added activity & permissions

gradle/
└── libs.versions.toml                     [MODIFIED] Added dependencies
app/
└── build.gradle.kts                       [MODIFIED] Added dependencies
```

## Key Classes and Methods

### RescueNavigationActivity

#### Initialization Methods

**`onCreate(Bundle savedInstanceState)`**
- Initializes all UI components
- Calls setupMap(), setupLocationTracking(), setupActionButtons()
- Registers window insets listener

**`initializeViews()`**
- Binds all UI components from layout
- Sets up references to TextView and Button elements

**`setupMap()`**
- Configures OSMDroid MapView
- Sets tile source to MAPNIK
- Initializes three overlay layers:
  1. User location (blue marker)
  2. Mesh nodes (green nodes with signal paths)
  3. Emergency locations (hospital, shelter, etc.)

#### GPS and Location Methods

**`setupLocationTracking()`**
- Checks location permissions
- Requests permissions if needed
- Calls startLocationUpdates()
- Calls visualizeMeshNodes()

**`startLocationUpdates()`**
- Registers with LocationManager for GPS_PROVIDER
- Update frequency: 1 second
- Minimum distance change: 1 meter
- Uses locationListener anonymous inner class

**`locationListener` (Anonymous Inner Class)**
- Implements LocationListener interface
- `onLocationChanged()` - Updates map center, caches tile
- `onProviderEnabled()` - Updates GPS status
- `onProviderDisabled()` - Updates GPS status

#### Mesh Network Methods

**`visualizeMeshNodes()`**
- Gets connected node names from MainActivity
- Creates pseudo-random locations within ~500m radius
- Adds overlay items to meshOverlay
- Calls drawSignalPaths()

**`drawSignalPaths()`**
- Connects user location to all mesh nodes
- Creates Polyline overlays (green lines)
- Updates dynamically as nodes connect/disconnect

#### Action Methods

**`shareLocation()`**
- Verifies GPS fix available
- Formats location data with coordinates and accuracy
- Creates Intent.ACTION_SEND for sharing
- In production, would broadcast to mesh network

**`triggerSOS()`**
- Toggles emergencyMode boolean flag
- Changes SOS button color (RED when active)
- Broadcasts SOS message via sendMeshMessage()
- Displays toast with status

#### Status Update Methods

**`updateStatusPanel()`**
- Updates GPS status text (accuracy or "Searching...")
- Updates node count
- Updates signal quality
- Gets battery percentage via IntentFilter
- Updates emergency mode indicator

#### Utility Methods

**`hasLocationPermissions()`**
- Checks for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
- Returns boolean

**`cacheMapTile()`**
- Stub for future offline caching implementation
- Currently logs cache action

#### Lifecycle Methods

**`onResume()`**
- Calls mapView.onResume()
- Updates status panel

**`onPause()`**
- Calls mapView.onPause()

**`onDestroy()`**
- Removes location listener
- Detaches map view

**`onRequestPermissionsResult()`**
- Handles location permission requests
- Calls startLocationUpdates() if granted

## Configuration Parameters

### Location Update Settings
```java
private static final int LOCATION_REQUEST_CODE = 2001;
private static final float ZOOM_LEVEL = 15f;

// In startLocationUpdates():
locationManager.requestLocationUpdates(
    LocationManager.GPS_PROVIDER,
    1000,      // Time interval: 1 second
    1,         // Distance interval: 1 meter
    locationListener
);
```

### Mesh Node Visualization
```java
// Pseudo-random location generation (in visualizeMeshNodes):
double offsetLat = (Math.random() - 0.5) * 0.01;   // ±0.005° ≈ ±500m
double offsetLon = (Math.random() - 0.5) * 0.01;
```

### Map Configuration
```java
Configuration.getInstance().setUserAgentValue(getPackageName());
mapView.setTileSource(TileSourceFactory.MAPNIK);
mapView.setMultiTouchControls(true);
mapView.getController().setZoom(ZOOM_LEVEL);
```

## Extension Points

### 1. Adding New Emergency Location Types

**Step 1**: Identify a new location type (e.g., "Police Station")

**Step 2**: Add to visualizeMeshNodes():
```java
// After examining meshOverlay items:
if (nodeName.contains("Police")) {
    OverlayItem policeNode = new OverlayItem(
        nodeName, 
        "Police Station", 
        nodePoint
    );
    emergencyOverlay.addItem(policeNode);
}
```

**Step 3**: Update overlay icon handling (future enhancement)

### 2. Enhancing Map Rendering

**Current**: Basic overlay approach

**Improvement Options**:
1. **Custom Drawable Markers** - Create unique icon resources for each type
2. **Cluster Markers** - Group nearby nodes when zoomed out
3. **Heat Maps** - Show node density visualization
4. **Real-time Animation** - Animated signal rays between nodes

### 3. Offline Tile Caching

**Current**: Stub implementation

**Full Implementation**:
```java
private void cacheMapTile() {
    if (isOnline && currentLocation != null) {
        // 1. Calculate bounding box (25-50km radius)
        // 2. Download tiles for current zoom level
        // 3. Store in app's cache directory
        // 4. Implement circular buffer for old tiles
    }
}
```

### 4. Advanced SOS Features

**Multi-Stage SOS**:
```java
private int sosLevel = 0;  // 0=off, 1=alert, 2=critical, 3=mayday

public void triggerSOS() {
    sosLevel = (sosLevel + 1) % 4;  // Cycle through levels
    
    String[] messages = {
        "SOS deactivated",
        "SOS Level 1: Alert",
        "SOS Level 2: Critical",
        "SOS Level 3: MAYDAY"
    };
    
    broadcastMessage(messages[sosLevel]);
}
```

### 5. Custom Map Styling

**Create custom tile sources**:
```java
// Instead of TileSourceFactory.MAPNIK
TileSourceFactory.getTileSource("OpenAndroMaps");  // Hiking maps
TileSourceFactory.getTileSource("USGS_TOPO");      // Topographic
```

## Testing Checklist

### Unit Tests
- [ ] Location permission checking
- [ ] Mesh node filtering
- [ ] SOS message formatting
- [ ] Emergency mode toggling

### Integration Tests
- [ ] GPS acquisition and updates
- [ ] Map overlay rendering
- [ ] Mesh node discovery
- [ ] Location sharing via MainActivity

### System Tests (Real Device)
- [ ] Full GPS-to-map pipeline
- [ ] Mesh network discovery with 2+ devices
- [ ] SOS broadcast and relay
- [ ] Location sharing with other apps
- [ ] Battery status monitoring
- [ ] Map rendering performance

### Emulator Testing
Note: GPS and Bluetooth don't work in standard emulator
- [ ] UI layout and button clicks (limited)
- [ ] Permission requests
- [ ] Error handling

## Performance Optimization Tips

### Memory
```java
// Current overhead per mesh node:
// OverlayItem: ~200 bytes
// GeoPoint: ~100 bytes
// Total for 100 nodes: ~30KB (acceptable)

// Optimization: Implement node clustering
if (meshOverlay.size() > 50) {
    clusterNearbyNodes();  // Group nodes within 1km
}
```

### Battery
```java
// Increase update interval for low battery:
if (batteryLevel < 20) {
    MIN_UPDATE_TIME = 5000;  // 5 seconds instead of 1
}
```

### Network Bandwidth
```java
// Compress SOS messages:
String sosMessage = "SOS:" + lat + "," + lon;  // Minimal
// Instead of full JSON structure
```

## Debugging Tips

### Log Tags
```java
private static final String TAG = "RescueNav";  // Use in all logs

// Key log points:
Log.d(TAG, "GPS acquired: " + location);
Log.d(TAG, "Mesh nodes discovered: " + nodeCount);
Log.d(TAG, "SOS triggered at: " + location);
Log.e(TAG, "Permission denied: " + permission);
```

### Debugging Location Services
```java
// Check location availability:
boolean gpsEnabled = locationManager
    .isProviderEnabled(LocationManager.GPS_PROVIDER);

// Check location accuracy:
if (location.getAccuracy() > 100) {
    Log.w(TAG, "GPS accuracy poor: " + location.getAccuracy() + "m");
}
```

### Battery Debugging
```java
// Get battery info:
IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
Intent batteryStatus = registerReceiver(null, ifilter);
int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
int batteryPct = (int) (level / (float) scale * 100);
```

## Common Issues and Solutions

### Issue 1: GPS Lock Takes Too Long
**Cause**: Cold start, no location history
**Solution**: 
- First fix may take 30-60 seconds
- Use SUPL (Secure User Plane Location) - requires internet initially
- Cache location history for cold start

### Issue 2: Mesh Nodes Not Appearing
**Cause**: MainActivity.getInstance() returns null
**Solution**:
- Ensure MainActivity has initialized (onCreate called)
- Check getInstance() is being called from UI thread
- Verify connectivity to mesh network

### Issue 3: SOS Messages Not Broadcasting
**Cause**: No connected endpoints
**Solution**:
- Verify Bluetooth and WiFi enabled
- Ensure at least one other device running ResQNet nearby
- Check CryptoUtils.encrypt() not failing

### Issue 4: Memory Leaks
**Cause**: LocationListener or MapView not cleaned up
**Solution**:
- Call removeUpdates() with specific listener
- Call mapView.onDetach() in onDestroy()
- Unregister any receivers

## Future Roadmap

### Phase 2: Advanced Features (v2.0)
- Offline map tile caching with 100GB+ support
- Voice SOS with mesh audio compression
- Pre-disaster map annotations
- External API integration

### Phase 3: AI/ML (v3.0)
- Predictive node routing
- Anomaly detection for spoofed nodes
- Emergency hotspot detection
- Automatic optimal relay selection

### Phase 4: Professional Tools (v4.0)
- Command center dashboard
- Real-time asset tracking
- Team coordination interface
- Historical event playback

## References

- [OSMDroid Documentation](https://github.com/osmdroid/osmdroid/wiki)
- [Android Location Services](https://developer.android.com/training/location)
- [Google Nearby Connections](https://developers.google.com/nearby/connections/overview)
- [Android Battery Manager](https://developer.android.com/reference/android/os/BatteryManager)

---

**Last Updated**: June 2026
**Version**: 1.0 Beta

