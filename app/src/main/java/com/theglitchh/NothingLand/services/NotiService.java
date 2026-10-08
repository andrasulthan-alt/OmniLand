package com.theglitchh.NothingLand.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.theglitchh.NothingLand.plugins.LiveActivity.LiveActivityPlugin;
import com.theglitchh.NothingLand.utils.Broadcasts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


public class NotiService extends NotificationListenerService {

    /** Clock apps whose ongoing notifications are timers/stopwatches. */
    private static final Set<String> CLOCK_APPS = new HashSet<>(Arrays.asList(
            "com.sec.android.app.clockpackage",   // Samsung
            "com.google.android.deskclock",       // Google
            "com.android.deskclock",              // AOSP and many others
            "com.coloros.alarmclock",             // OPPO / OnePlus
            "com.oneplus.deskclock",
            "com.huawei.deskclock",
            "com.android.BBKClock",               // vivo
            "com.miui.clock",                     // Xiaomi
            "com.nothing.deskclock"));            // Nothing

    /** Navigation apps (list after Smart Island, GPL-3.0). */
    private static final Set<String> NAV_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps", "com.waze", "com.sygic.aura", "com.here.app.maps",
            "com.tomtom.gplay.navapp", "net.osmand", "net.osmand.plus", "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps", "com.huawei.maps.app", "com.mapmyindia.maps", "com.autonavi.minimap",
            "com.baidu.BaiduMap", "com.locnall.KimGiSa", "com.nhn.android.nmap", "com.gojek.app", "com.grabtaxi.passenger"));

    /** System packages that post the "Hotspot on" notification. */
    private static final Set<String> SYSTEM_APPS = new HashSet<>(Arrays.asList(
            "com.android.systemui", "com.android.settings", "com.android.networkstack.tethering",
            "com.samsung.android.net.wifi.wifiguider", "android"));

    // Notification.CallStyle extras (Android 12+), read by key so older SDKs still compile.
    private static final String EXTRA_CALL_TYPE = "android.callType";
    private static final String EXTRA_ANSWER_INTENT = "android.answerIntent";
    private static final String EXTRA_DECLINE_INTENT = "android.declineIntent";
    private static final String EXTRA_HANG_UP_INTENT = "android.hangUpIntent";

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(getPackageName() + ".ACTION_OPEN_CLOSE");
        filter.addAction(getPackageName() + ".ACTION_CLOSE");
        filter.addAction(getPackageName() + Broadcasts.RESYNC);
        Broadcasts.register(this, receiver, filter);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // Media sessions can only be read once notification access is active, so let
        // the island (re)attach now instead of waiting for the next restart.
        Broadcasts.send(this, Broadcasts.internal(this, Broadcasts.LISTENER_CONNECTED));
    }

    private final ArrayList<StatusBarNotification> notifications = new ArrayList<>();

    @Override
    public void onDestroy() {
        super.onDestroy();
        Broadcasts.unregister(this, receiver);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        Notification notification = sbn.getNotification();
        String liveType = null;
        try {
            liveType = liveActivityType(sbn);
        } catch (Exception ignored) {
        }
        if (liveType != null) {
            sendLiveActivity(sbn, liveType);
            return;
        }
        Intent intent = new Intent(getPackageName() + ".NOTIFICATION_POSTED");
        intent.putExtra("package_name", sbn.getPackageName());
        intent.putExtra("id", sbn.getId());
        intent.putExtra("key", sbn.getKey());
        intent.putExtra("time", sbn.getPostTime());
        intent.putExtra("icon_large", sbn.getNotification().getLargeIcon());
        intent.putExtra("icon_small", sbn.getNotification().getSmallIcon());
        intent.putExtra("category", sbn.getNotification().category);
        try {
            // Read as CharSequence: many apps (WhatsApp, Telegram, Gmail) post styled text,
            // which getString() returns as null, so those notifications never showed.
            intent.putExtra("title", text(notification.extras, Notification.EXTRA_TITLE));
            intent.putExtra("body", text(notification.extras, Notification.EXTRA_TEXT));
        } catch (Exception e) {
            //ignore
        }
        notifications.removeIf(x -> x.getKey().equals(sbn.getKey()));
        notifications.add(sbn);
        Broadcasts.send(this, intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        Intent intent = new Intent(getPackageName() + ".NOTIFICATION_REMOVED");
        intent.putExtra("id", sbn.getId());
        intent.putExtra("key", sbn.getKey());
        intent.putExtra("package_name", sbn.getPackageName());
        notifications.removeIf(x -> x.getKey().equals(sbn.getKey()));
        Broadcasts.send(this, intent);
    }

    // ---------------------------------------------------------------- live activities

    /**
     * Decides whether a notification is a call, a timer/stopwatch or a download/upload
     * in progress. Returns null for normal notifications, or when the Live Activities
     * plugin (or that kind of activity) is switched off.
     */
    private String liveActivityType(StatusBarNotification sbn) {
        SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        if (!prefs.getBoolean("LiveActivityPlugin_enabled", true)) return null;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;

        if (Notification.CATEGORY_CALL.equals(n.category)) {
            return prefs.getBoolean(LiveActivityPlugin.PREF_CALLS, true) ? LiveActivityPlugin.TYPE_CALL : null;
        }
        String lower = (text(e, Notification.EXTRA_TITLE) + " " + text(e, Notification.EXTRA_TEXT)).toLowerCase();
        boolean navCategory = "navigation".equals(n.category);
        if (ongoing && (navCategory || NAV_APPS.contains(sbn.getPackageName()))
                && !lower.contains("hotspot") && !lower.contains("recording")) {
            return prefs.getBoolean(LiveActivityPlugin.PREF_NAV, true) ? LiveActivityPlugin.TYPE_NAV : null;
        }
        if (ongoing && SYSTEM_APPS.contains(sbn.getPackageName())
                && (lower.contains("hotspot") || lower.contains("tethering"))) {
            return prefs.getBoolean(LiveActivityPlugin.PREF_HOTSPOT, true) ? LiveActivityPlugin.TYPE_HOTSPOT : null;
        }
        boolean chrono = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        if (ongoing && (chrono || CLOCK_APPS.contains(sbn.getPackageName()))) {
            return prefs.getBoolean(LiveActivityPlugin.PREF_TIMERS, true) ? LiveActivityPlugin.TYPE_TIMER : null;
        }
        int max = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        boolean indeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        if (ongoing && (max > 0 || indeterminate)) {
            return prefs.getBoolean(LiveActivityPlugin.PREF_PROGRESS, true) ? LiveActivityPlugin.TYPE_PROGRESS : null;
        }
        return null;
    }

    private static String text(Bundle e, String key) {
        CharSequence cs = e.getCharSequence(key);
        return cs == null ? "" : cs.toString();
    }

    private void sendLiveActivity(StatusBarNotification sbn, String type) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        Intent intent = new Intent(getPackageName() + ".LIVE_POSTED");
        intent.putExtra("live_type", type);
        intent.putExtra("package_name", sbn.getPackageName());
        intent.putExtra("id", sbn.getId());
        intent.putExtra("title", text(e, Notification.EXTRA_TITLE));
        intent.putExtra("body", text(e, Notification.EXTRA_TEXT));
        intent.putExtra("subtext", text(e, Notification.EXTRA_SUB_TEXT));
        intent.putExtra("when", n.when);
        intent.putExtra("chrono", e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false));
        intent.putExtra("countdown", e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false));
        intent.putExtra("progress", e.getInt(Notification.EXTRA_PROGRESS, 0));
        intent.putExtra("progress_max", e.getInt(Notification.EXTRA_PROGRESS_MAX, 0));
        intent.putExtra("indeterminate", e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false));
        intent.putExtra("icon_small", n.getSmallIcon());
        intent.putExtra("icon_large", n.getLargeIcon());
        if (n.contentIntent != null) intent.putExtra("content_intent", n.contentIntent);

        ArrayList<String> titles = new ArrayList<>();
        ArrayList<PendingIntent> intents = new ArrayList<>();
        if (LiveActivityPlugin.TYPE_CALL.equals(type)) {
            boolean incoming = isIncomingCall(n);
            intent.putExtra("incoming", incoming);
            // Prefer the standard call buttons (Android 12+ call notifications)...
            if (incoming) {
                addIntent(e, EXTRA_DECLINE_INTENT, "Decline", titles, intents);
                addIntent(e, EXTRA_ANSWER_INTENT, "Answer", titles, intents);
            } else {
                addIntent(e, EXTRA_HANG_UP_INTENT, "Hang up", titles, intents);
            }
            // ...otherwise use the notification's own buttons (Samsung Phone, WhatsApp, ...).
            if (titles.isEmpty() && n.actions != null) {
                for (Notification.Action a : n.actions) {
                    if (a == null || a.actionIntent == null || a.title == null) continue;
                    titles.add(a.title.toString());
                    intents.add(a.actionIntent);
                    if (titles.size() == 2) break;
                }
            }
        }
        intent.putStringArrayListExtra("action_titles", titles);
        intent.putParcelableArrayListExtra("action_intents", intents);
        Broadcasts.send(this, intent);
    }

    private static boolean isIncomingCall(Notification n) {
        int callType = n.extras.getInt(EXTRA_CALL_TYPE, 0);
        if (callType == 1 || callType == 3) return true;   // incoming / screening
        if (callType == 2) return false;                   // ongoing
        boolean chrono = n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        return !chrono && n.fullScreenIntent != null;
    }

    private static void addIntent(Bundle e, String key, String title,
                                  ArrayList<String> titles, ArrayList<PendingIntent> intents) {
        Parcelable p = e.getParcelable(key);
        if (p instanceof PendingIntent) {
            titles.add(title);
            intents.add((PendingIntent) p);
        }
    }

    /** Finds a notification by its unique key (ids alone clash between apps). */
    private StatusBarNotification find(Intent intent) {
        String key = intent.getStringExtra("key");
        if (key != null) {
            for (StatusBarNotification x : notifications) {
                if (key.equals(x.getKey())) return x;
            }
            try {
                StatusBarNotification[] active = getActiveNotifications(new String[]{key});
                if (active != null && active.length > 0) return active[0];
            } catch (Exception ignored) {
            }
            return null;
        }
        int id = intent.getIntExtra("id", 0);
        String pkg = intent.getStringExtra("package_name");
        for (StatusBarNotification x : notifications) {
            if (x.getId() == id && (pkg == null || pkg.equals(x.getPackageName()))) return x;
        }
        return null;
    }

    /** Resends calls, timers, navigation, etc. after the island was rebuilt. */
    private void resendLiveActivities() {
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (Exception e) {
            return; // not connected yet
        }
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            try {
                String type = liveActivityType(sbn);
                if (type != null) sendLiveActivity(sbn, type);
            } catch (Exception ignored) {
            }
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(context.getPackageName() + Broadcasts.RESYNC)) {
                resendLiveActivities();
                return;
            }
            boolean open = action.equals(context.getPackageName() + ".ACTION_OPEN_CLOSE");
            boolean close = action.equals(context.getPackageName() + ".ACTION_CLOSE");
            if (!open && !close) return;
            StatusBarNotification x = find(intent);
            if (x == null) return;
            try {
                if (open && x.getNotification().contentIntent != null) {
                    x.getNotification().contentIntent.send();
                }
            } catch (Exception ignored) {
            }
            try {
                // Really dismiss it; the system sends the app's own deleteIntent for us.
                cancelNotification(x.getKey());
            } catch (Exception ignored) {
            }
        }
    };
}
