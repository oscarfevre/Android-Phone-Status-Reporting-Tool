package android_status.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

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
    private long prevIdle = -1;
    private long prevTotal = -1;

    private String webhookUrl = null;
    private int intervalSeconds = 30;

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
            scheduler.scheduleAtFixedRate(this::collectAndSend, 0, intervalSeconds, TimeUnit.SECONDS);
        }
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

            double memPct = MetricsCollector.readMemUsagePercent();
            Double temp = MetricsCollector.readTempCelsius(ctx);
            BatteryInfo bi = MetricsCollector.readBattery(ctx);

            String deviceId = MetricsCollector.deviceId(ctx);
            String nowShort = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
            String nowFull = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            String tempStr = temp != null ? String.format(Locale.US, "temp=%.1f'C", temp) : "temp=N/A";
            String voltage = String.format(Locale.US, "%.2fV", bi.voltageMv / 1000.0);
            String msg = String.format(Locale.US,
                    "[%s] [%s] NOTIFICATION [Camera %s] MEM: %.1f%% | %s | Battery: %d%% | Voltage: %s",
                    nowShort, nowFull, deviceId, memPct, tempStr, bi.level, voltage);

            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                boolean ok = SlackPoster.postToWebhook(webhookUrl, msg);
                Log.i(TAG, "Posted to Slack: " + ok);
            } else {
                Log.i(TAG, "Webhook not set, message: " + msg);
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
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
