package com.theglitchh.NothingLand.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.theglitchh.NothingLand.BuildConfig;
import com.theglitchh.NothingLand.R;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

/**
 * Checks GitHub for a newer OmniLand release and, if there is one, tells the user
 * and opens the release page when tapped. It deliberately does NOT download or
 * install anything itself: installs go through the browser, Obtainium or
 * Komi Store, so the app doesn't need the "install unknown apps" permission.
 */
public class UpdaterService extends Service {

    private static final String CHANNEL_ID = "updates";
    private static final int NOTIFICATION_ID = 1001;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        String url = "https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest";
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, (JSONObject) null, release -> {
            try {
                // Release tags are plain versionCode numbers ("4", "5", ...).
                int latest = Integer.parseInt(release.getString("tag_name").trim());
                if (latest > BuildConfig.VERSION_CODE) {
                    String name = release.optString("name", "a new version");
                    String page = release.optString("html_url",
                            "https://github.com/" + BuildConfig.UPDATE_REPO + "/releases/latest");
                    Intent intent = new Intent(getPackageName() + ".UPDATE_AVAIL");
                    intent.setPackage(getPackageName());
                    intent.putExtra("version", name);
                    intent.putExtra("url", page);
                    com.theglitchh.NothingLand.utils.Broadcasts.send(this, intent);
                    showUpdateNotification(name, page);
                }
            } catch (Exception e) {
                Log.w("UpdaterService", "Could not read the latest release", e);
            }
            stopSelf();
        }, error -> {
            Log.w("UpdaterService", "Update check failed", error);
            stopSelf();
        });
        queue.add(request);
    }

    private void showUpdateNotification(String versionName, String page) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "App updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(Intent.ACTION_VIEW, Uri.parse(page)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.launcher_foreground)
                .setContentTitle("OmniLand update available")
                .setContentText(versionName + " is out. Tap to open the download page.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
