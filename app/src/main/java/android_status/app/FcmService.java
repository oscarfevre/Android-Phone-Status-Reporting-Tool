package android_status.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

/**
 * Handles FCM data messages to trigger on-demand location uploads.
 */
public class FcmService extends FirebaseMessagingService {
    private static final String TAG = "FcmService";
    private static final String KEY_WEBHOOK = "webhook_url";
    private static final String KEY_API = "api_endpoint";
    private static final String KEY_API_KEY = "api_key";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage == null || remoteMessage.getData() == null) return;
        String type = remoteMessage.getData().get("type");
        if (!"REQUEST_LOCATION".equalsIgnoreCase(type)) {
            return;
        }
        Log.i(TAG, "Received REQUEST_LOCATION");
        Context ctx = getApplicationContext();
        // Load saved endpoints/keys
        String webhook = Prefs.get(ctx).getString(KEY_WEBHOOK, "");
        String api = Prefs.get(ctx).getString(KEY_API, "");
        String apiKey = Prefs.get(ctx).getString(KEY_API_KEY, "");

        // Start/ensure MetricService is running with config and request an immediate send
        Intent svc = new Intent(ctx, MetricService.class);
        svc.putExtra("webhook", webhook);
        svc.putExtra("api", api);
        svc.putExtra("apiKey", apiKey);
        svc.putExtra("triggerImmediate", true);
        // Force immediate sends to go to API only; disable Slack/periodic for this trigger
        svc.putExtra("enableSlack", false);
        svc.putExtra("enableApi", true);
        svc.putExtra("enablePeriodic", false);
        ContextCompat.startForegroundService(ctx, svc);
    }

    @Override
    public void onNewToken(String token) {
        Log.i(TAG, "FCM token refreshed: " + token);
        try {
            Context ctx = getApplicationContext();
            String api = Prefs.get(ctx).getString(KEY_API, "");
            String apiKey = Prefs.get(ctx).getString(KEY_API_KEY, "");
            if (api == null || api.isEmpty()) {
                Log.i(TAG, "No API endpoint configured; skipping token registration");
                return;
            }
            JSONObject payload = new JSONObject();
            payload.put("type", "registerToken");
            payload.put("deviceId", MetricsCollector.deviceId(ctx));
            payload.put("fcmToken", token);
            boolean ok = ApiPoster.postJson(api, payload, apiKey);
            Log.i(TAG, "Posted FCM token to API: " + ok);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to send refreshed token", ex);
        }
    }
}
