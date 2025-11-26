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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class App extends AppCompatActivity {
    private EditText webhookInput;
    private Button startButton;
    private Button stopButton;
    private TextView statusView;

    private static final String PREFS = "android_status_prefs";
    private static final String KEY_WEBHOOK = "webhook_url";
    private static final int REQ_POST_NOTIF = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webhookInput = findViewById(R.id.editWebhook);
        startButton = findViewById(R.id.btnStart);
        stopButton = findViewById(R.id.btnStop);
        statusView = findViewById(R.id.txtStatus);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_WEBHOOK, "");
        webhookInput.setText(saved);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String webhook = webhookInput.getText().toString().trim();
                prefs.edit().putString(KEY_WEBHOOK, webhook).apply();
                Intent svc = new Intent(App.this, MetricService.class);
                svc.putExtra("webhook", webhook);
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
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
            }
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
        }
    }
}
