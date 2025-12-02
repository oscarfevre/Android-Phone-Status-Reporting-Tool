package android_status.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class App extends AppCompatActivity {
    private EditText webhookInput;
    private EditText apiInput;
    private EditText apiKeyInput;
    private Switch enableSlack;
    private Switch enableApi;
    private Switch enablePeriodic;
    private Button startButton;
    private Button stopButton;
    private TextView statusView;

    private static final String KEY_WEBHOOK = "webhook_url";
    private static final String KEY_API = "api_endpoint";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_ENABLE_SLACK = "enable_slack";
    private static final String KEY_ENABLE_API = "enable_api";
    private static final String KEY_ENABLE_PERIODIC = "enable_periodic";
    private static final int REQ_POST_NOTIF = 42;
    private static final int REQ_LOCATION = 43;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webhookInput = findViewById(R.id.editWebhook);
        startButton = findViewById(R.id.btnStart);
        stopButton = findViewById(R.id.btnStop);
        statusView = findViewById(R.id.txtStatus);
        apiInput = findViewById(R.id.editApiEndpoint);
        apiKeyInput = findViewById(R.id.editApiKey);
        enableSlack = findViewById(R.id.switchEnableSlack);
        enableApi = findViewById(R.id.switchEnableApi);
        enablePeriodic = findViewById(R.id.switchEnablePeriodic);

        SharedPreferences prefs = Prefs.get(this);
        String saved = prefs.getString(KEY_WEBHOOK, "");
        String savedApi = prefs.getString(KEY_API, "");
        String savedApiKey = prefs.getString(KEY_API_KEY, "");
        boolean savedSlack = prefs.getBoolean(KEY_ENABLE_SLACK, true);
        boolean savedApiSend = prefs.getBoolean(KEY_ENABLE_API, true);
        boolean savedPeriodic = prefs.getBoolean(KEY_ENABLE_PERIODIC, true);
        webhookInput.setText(saved);
        apiInput.setText(savedApi);
        apiKeyInput.setText(savedApiKey);
        enableSlack.setChecked(savedSlack);
        enableApi.setChecked(savedApiSend);
        enablePeriodic.setChecked(savedPeriodic);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String webhook = webhookInput.getText().toString().trim();
                String api = apiInput.getText().toString().trim();
                String apiKey = apiKeyInput.getText().toString().trim();
                boolean doSlack = enableSlack.isChecked();
                boolean doApi = enableApi.isChecked();
                boolean doPeriodic = enablePeriodic.isChecked();
                prefs.edit().putString(KEY_WEBHOOK, webhook).apply();
                prefs.edit().putString(KEY_API, api).apply();
                prefs.edit().putString(KEY_API_KEY, apiKey).apply();
                prefs.edit().putBoolean(KEY_ENABLE_SLACK, doSlack).apply();
                prefs.edit().putBoolean(KEY_ENABLE_API, doApi).apply();
                prefs.edit().putBoolean(KEY_ENABLE_PERIODIC, doPeriodic).apply();
                Intent svc = new Intent(App.this, MetricService.class);
                svc.putExtra("webhook", webhook);
                svc.putExtra("api", api);
                svc.putExtra("apiKey", apiKey);
                svc.putExtra("enableSlack", doSlack);
                svc.putExtra("enableApi", doApi);
                svc.putExtra("enablePeriodic", doPeriodic);
                ContextCompat.startForegroundService(App.this, svc);
                statusView.setText("Service started");
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent svc = new Intent(App.this, MetricService.class);
                stopService(svc);
                statusView.setText("Service stopped");
            }
        });

        requestNotificationPermissionIfNeeded();
        requestLocationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
            }
        }
    }

    private void requestLocationPermissionIfNeeded() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine || !coarse) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIF && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusView.setText("Notification permission granted");
            } else {
                statusView.setText("Notification permission denied; foreground notification may be hidden");
            }
        } else if (requestCode == REQ_LOCATION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusView.setText("Location permission granted");
            } else {
                statusView.setText("Location permission denied; GPS data will be missing");
            }
        }
    }
}
