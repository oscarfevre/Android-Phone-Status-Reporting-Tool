package android_status.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String KEY_WEBHOOK = "webhook_url";
    private static final String KEY_API = "api_endpoint";
    private static final String KEY_API_KEY = "api_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            android.content.SharedPreferences prefs = Prefs.get(context);
            String webhook = prefs.getString(KEY_WEBHOOK, null);
            String api = prefs.getString(KEY_API, null);
            String apiKey = prefs.getString(KEY_API_KEY, null);
            if (webhook != null && !webhook.isEmpty()) {
                Intent svc = new Intent(context, MetricService.class);
                svc.putExtra("webhook", webhook);
                svc.putExtra("api", api);
                svc.putExtra("apiKey", apiKey);
                ContextCompat.startForegroundService(context, svc);
            }
        }
    }
}
