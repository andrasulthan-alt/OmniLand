package com.theglitchh.NothingLand.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * OmniLand's own broadcasts (notifications, calls, settings) must stay inside the app.
 * Sent broadcasts are limited to our package, so other apps can't read notification
 * text or call buttons, and receivers are registered as not exported on Android 13+,
 * so other apps can't send us fake calls or settings. System broadcasts (screen off,
 * battery, wallpaper, ...) still arrive at these receivers.
 */
public final class Broadcasts {
    private Broadcasts() {
    }

    /** Sent by NotiService when it is connected, so the island can (re)attach to media sessions. */
    public static final String LISTENER_CONNECTED = ".LISTENER_CONNECTED";
    /** Sent by the island after it was rebuilt, so NotiService resends calls, timers, etc. */
    public static final String RESYNC = ".RESYNC";

    /** An intent for one of our own actions, delivered only to OmniLand. */
    public static Intent internal(Context ctx, String actionSuffix) {
        Intent intent = new Intent(ctx.getPackageName() + actionSuffix);
        intent.setPackage(ctx.getPackageName());
        return intent;
    }

    /** Sends an intent to OmniLand only. */
    public static void send(Context ctx, Intent intent) {
        intent.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(intent);
    }

    /** Registers a receiver that other apps can't send to (Android 13+). */
    public static void register(Context ctx, BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ctx.registerReceiver(receiver, filter);
        }
    }

    /** Unregisters without throwing when the receiver was never registered. */
    public static void unregister(Context ctx, BroadcastReceiver receiver) {
        if (ctx == null || receiver == null) return;
        try {
            ctx.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
    }
}
