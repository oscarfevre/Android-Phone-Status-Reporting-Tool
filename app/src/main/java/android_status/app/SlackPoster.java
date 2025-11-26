package android_status.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SlackPoster {
    private static final String TAG = "SlackPoster";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static boolean postToWebhook(String webhookUrl, String text) {
        try {
            JSONObject j = new JSONObject();
            j.put("text", text);
            RequestBody body = RequestBody.create(j.toString(), JSON);
            Request req = new Request.Builder().url(webhookUrl).post(body).build();
            try (Response resp = client.newCall(req).execute()) {
                boolean ok = resp.isSuccessful();
                if (!ok) Log.w(TAG, "Slack post failed: " + resp.code() + " " + resp.message());
                return ok;
            }
        } catch (IOException ex) {
            Log.e(TAG, "IO error posting to slack", ex);
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "Error posting to slack", ex);
            return false;
        }
    }
}
