package android_status.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiPoster {
    private static final String TAG = "ApiPoster";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static boolean postJson(String url, JSONObject payload, String apiKey) {
        if (url == null || url.isEmpty()) return false;
        try {
            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request.Builder builder = new Request.Builder().url(url).post(body);
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            Request req = builder.build();
            try (Response resp = client.newCall(req).execute()) {
                boolean ok = resp.isSuccessful();
                if (!ok) Log.w(TAG, "API post failed: " + resp.code() + " " + resp.message());
                return ok;
            }
        } catch (IOException ex) {
            Log.e(TAG, "IO error posting to API", ex);
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "Error posting to API", ex);
            return false;
        }
    }
}
