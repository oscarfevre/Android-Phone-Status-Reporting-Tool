package android_status.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String PREFS = "android_status_prefs";
    private static final String KEY_WEBHOOK = "webhook_url";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String webhook = prefs.getString(KEY_WEBHOOK, null);
            if (webhook != null && !webhook.isEmpty()) {
                Intent svc = new Intent(context, MetricService.class);
                svc.putExtra("webhook", webhook);
                ContextCompat.startForegroundService(context, svc);
            }
        }
    }
}
