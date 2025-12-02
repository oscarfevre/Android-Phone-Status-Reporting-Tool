package android_status.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android_status.app.MetricsCollector.BatteryInfo;

public class MetricService extends Service {
    private static final String TAG = "MetricService";
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "android_status_channel";

    private ScheduledExecutorService scheduler;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback fusedCallback;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private MetricsCollector.LocationInfo lastLocation = new MetricsCollector.LocationInfo();
    private long prevIdle = -1;
    private long prevTotal = -1;

    private String webhookUrl = null;
    private String apiEndpoint = null;
    private String apiKey = null;
    private boolean enableSlack = true;
    private boolean enableApi = true;
    private boolean enablePeriodic = true;
    private int intervalSeconds = 30;
    private boolean triggerImmediate = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Monitoring device status"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("webhook")) {
            webhookUrl = intent.getStringExtra("webhook");
        }
        if (intent != null && intent.hasExtra("api")) {
            apiEndpoint = intent.getStringExtra("api");
        }
        if (intent != null && intent.hasExtra("apiKey")) {
            apiKey = intent.getStringExtra("apiKey");
        }
        if (intent != null && intent.hasExtra("enableSlack")) {
            enableSlack = intent.getBooleanExtra("enableSlack", true);
        }
        if (intent != null && intent.hasExtra("enableApi")) {
            enableApi = intent.getBooleanExtra("enableApi", true);
        }
        if (intent != null && intent.hasExtra("enablePeriodic")) {
            enablePeriodic = intent.getBooleanExtra("enablePeriodic", true);
        }
        if (intent != null && intent.getBooleanExtra("triggerImmediate", false)) {
            triggerImmediate = true;
        }
        if (scheduler == null) {
            // prime CPU baseline so first tick has a delta
            long[] priming = MetricsCollector.readCpuStat();
            if (priming != null) {
                prevIdle = priming[3] + priming[4];
                long total = 0;
                for (long v : priming) total += v;
                prevTotal = total;
            }
            scheduler = Executors.newSingleThreadScheduledExecutor();
            if (enablePeriodic) {
                scheduler.scheduleAtFixedRate(this::collectAndSend, 0, intervalSeconds, TimeUnit.SECONDS);
            }
        }
        if (triggerImmediate) {
            triggerImmediate = false;
            scheduler.execute(this::collectAndSend);
        }
        startLocationUpdates();
        return START_STICKY;
    }

    private void collectAndSend() {
        try {
            Context ctx = getApplicationContext();
            double cpu = 0.0;
            long[] snap = MetricsCollector.readCpuStat();
            if (snap != null) {
                long idle = snap[3] + snap[4];
                long total = 0;
                for (long v : snap) total += v;
                if (prevTotal > 0 && total > prevTotal) {
                    double totalDelta = (double) (total - prevTotal);
                    double idleDelta = (double) (idle - prevIdle);
                    cpu = ((totalDelta - idleDelta) / totalDelta) * 100.0;
                }
                prevIdle = idle;
                prevTotal = total;
            }

            double memPct = Math.round(MetricsCollector.readMemUsagePercent() * 10.0) / 10.0; // one decimal
            Double temp = MetricsCollector.readTempCelsius(ctx);
            BatteryInfo bi = MetricsCollector.readBattery(ctx);
            MetricsCollector.LocationInfo li = (lastLocation != null && lastLocation.lat != null) ? lastLocation : MetricsCollector.readLocation(ctx);

            String deviceId = MetricsCollector.deviceId(ctx);
            String deviceName = resolveDeviceName(deviceId);
            String nowShort = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
            String nowFull = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            String tempStr = temp != null ? String.format(Locale.US, "temp=%.1f'C", temp) : "temp=N/A";
            String voltage = String.format(Locale.US, "%.2fV", bi.voltageMv / 1000.0);
            String msg = String.format(Locale.US,
                    "[%s] [%s] NOTIFICATION [Camera %s] MEM: %.1f%% | %s | Battery: %d%% | Voltage: %s",
                    nowShort, nowFull, (deviceName != null ? deviceName : deviceId), memPct, tempStr, bi.level, voltage);

            if (enableSlack && webhookUrl != null && !webhookUrl.isEmpty()) {
                boolean ok = SlackPoster.postToWebhook(webhookUrl, msg);
                Log.i(TAG, "Posted to Slack: " + ok);
            } else {
                Log.i(TAG, "Slack disabled or webhook not set, message: " + msg);
            }

            if (enableApi && apiEndpoint != null && !apiEndpoint.isEmpty()) {
                try {
                    org.json.JSONObject payload = new org.json.JSONObject();
                    payload.put("deviceId", deviceId);
                    if (deviceName != null) payload.put("deviceName", deviceName);
                    payload.put("timestampMs", System.currentTimeMillis());
                    payload.put("memoryPct", memPct);
                    if (temp != null) payload.put("tempC", temp);
                    payload.put("batteryPct", bi.level);
                    payload.put("voltageV", bi.voltageMv / 1000.0);
                    if (li.lat != null && li.lon != null) {
                        double lat5 = Math.round(li.lat * 100000.0) / 100000.0;
                        double lon5 = Math.round(li.lon * 100000.0) / 100000.0;
                        payload.put("lat", lat5);
                        payload.put("lon", lon5);
                        if (li.accuracy != null) payload.put("accuracy", li.accuracy);
                        if (li.provider != null) payload.put("provider", li.provider);
                    }
                    boolean apiOk = ApiPoster.postJson(apiEndpoint, payload, apiKey);
                    Log.i(TAG, "Posted to API: " + apiOk);
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to build/send API payload", ex);
                }
            } else {
                Log.i(TAG, "API disabled or endpoint not set; skipping API post");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error collecting/sending metrics", ex);
        }
    }

    private Notification buildNotification(String text) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android Status")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return b.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Android Status", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdownNow();
        stopLocationUpdates();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }

    private void startLocationUpdates() {
        // stop any previous callbacks before starting a new provider
        stopLocationUpdates();
        boolean fusedStarted = false;
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                    .setMinUpdateIntervalMillis(5_000)
                    .setWaitForAccurateLocation(true)
                    .build();
            fusedCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult result) {
                    if (result == null || result.getLocations() == null) return;
                    for (Location l : result.getLocations()) {
                        updateLastLocation(l);
                    }
                }
            };
            fusedLocationClient.requestLocationUpdates(req, fusedCallback, Looper.getMainLooper());
            fusedStarted = true;
        } catch (SecurityException se) {
            Log.w(TAG, "Location permission not granted; cannot start fused updates", se);
        } catch (Exception ex) {
            Log.w(TAG, "startLocationUpdates (fused) failed, falling back", ex);
        }
        if (fusedStarted) return;

        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    updateLastLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    // no-op
                }

                @Override
                public void onProviderEnabled(String provider) {
                    // no-op
                }

                @Override
                public void onProviderDisabled(String provider) {
                    // no-op
                }
            };
            // request from both GPS and network to increase chances on emulator
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 0, locationListener);
        } catch (SecurityException se) {
            Log.w(TAG, "Location permission not granted; cannot start fallback updates", se);
        } catch (Exception ex) {
            Log.w(TAG, "startLocationUpdates failed", ex);
        }
    }

    private void stopLocationUpdates() {
        try {
            if (fusedLocationClient != null && fusedCallback != null) {
                fusedLocationClient.removeLocationUpdates(fusedCallback);
            }
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateLastLocation(Location location) {
        if (location == null) return;
        MetricsCollector.LocationInfo li = new MetricsCollector.LocationInfo();
        li.lat = location.getLatitude();
        li.lon = location.getLongitude();
        li.accuracy = location.getAccuracy();
        li.provider = location.getProvider();
        lastLocation = li;
    }

    private String resolveDeviceName(String deviceId) {
        if (deviceId == null) return null;
        switch (deviceId) {
            case "9ea5006ef6b50d20":
                return "atom1";
            case "0e908fe6ca72fba2":
                return "atom2";
            case "08968328ace45d30":
                return "atom3";
            default:
                return null;
        }
    }
}
