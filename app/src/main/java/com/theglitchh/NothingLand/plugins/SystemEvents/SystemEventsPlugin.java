package com.theglitchh.NothingLand.plugins.SystemEvents;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.plugins.BasePlugin;
import com.theglitchh.NothingLand.services.OverlayService;
import com.theglitchh.NothingLand.utils.SettingStruct;

import java.util.ArrayList;

/**
 * Short "mini events" in the island: Bluetooth connected/disconnected, ringer
 * mode, airplane mode, hotspot, headphones, low battery and battery saver.
 * Each shows for a few seconds and then goes away.
 * Event detection follows the approach used by Smart Island (GPL-3.0, Animesh Gupta).
 */
public class SystemEventsPlugin extends BasePlugin {

    private static final long SHOW_MS = 3500;
    private static final String ACTION_WIFI_AP = "android.net.wifi.WIFI_AP_STATE_CHANGED";

    public static final String PREF_BT = "ev_bluetooth";
    public static final String PREF_RINGER = "ev_ringer";
    public static final String PREF_AIRPLANE = "ev_airplane";
    public static final String PREF_HOTSPOT = "ev_hotspot";
    public static final String PREF_HEADSET = "ev_headset";
    public static final String PREF_BATTERY = "ev_battery";

    private OverlayService ctx;
    private Handler handler;
    private View mView;
    private String text = "";
    private boolean queued = false;
    private long createdAt;
    private int lastRinger = -1;
    private int lastApState = -1;

    @Override
    public String getID() {
        return "SystemEventsPlugin";
    }

    @Override
    public String getName() {
        return "System Events";
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            // Sticky broadcasts fire as soon as we register; ignore those.
            boolean startup = System.currentTimeMillis() - createdAt < 2000;
            try {
                switch (action) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        if (on(PREF_BT)) show("" + deviceName(intent) + " connected");
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        if (on(PREF_BT)) show("" + deviceName(intent) + " disconnected");
                        break;
                    case AudioManager.RINGER_MODE_CHANGED_ACTION: {
                        int mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
                        if (mode == lastRinger) break;
                        lastRinger = mode;
                        if (startup || !on(PREF_RINGER)) break;
                        if (mode == AudioManager.RINGER_MODE_SILENT) show("Silent");
                        else if (mode == AudioManager.RINGER_MODE_VIBRATE) show("Vibrate");
                        else if (mode == AudioManager.RINGER_MODE_NORMAL) show("Sound on");
                        break;
                    }
                    case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                        if (startup || !on(PREF_AIRPLANE)) break;
                        show(intent.getBooleanExtra("state", false) ? "Airplane mode on" : "Airplane mode off");
                        break;
                    case ACTION_WIFI_AP: {
                        int state = intent.getIntExtra("wifi_state", -1);
                        if (state == lastApState) break;
                        lastApState = state;
                        if (startup || !on(PREF_HOTSPOT)) break;
                        if (state == 13) show("Hotspot on");
                        else if (state == 11) show("Hotspot off");
                        break;
                    }
                    case Intent.ACTION_HEADSET_PLUG:
                        if (startup || !on(PREF_HEADSET)) break;
                        show(intent.getIntExtra("state", 0) == 1 ? "Headphones connected" : "Headphones removed");
                        break;
                    case Intent.ACTION_BATTERY_LOW:
                        if (on(PREF_BATTERY)) show("Low battery");
                        break;
                    case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED: {
                        if (startup || !on(PREF_BATTERY)) break;
                        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                        show(pm != null && pm.isPowerSaveMode() ? "Battery saver on" : "Battery saver off");
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception ignored) {
            }
        }
    };

    private String deviceName(Intent intent) {
        try {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // Reading the name needs BLUETOOTH_CONNECT on Android 12+; fall back to a generic label.
            String name = device != null ? device.getName() : null;
            return name == null || name.trim().isEmpty() ? "Bluetooth device" : name;
        } catch (Exception e) {
            return "Bluetooth device";
        }
    }

    private boolean on(String key) {
        return ctx.sharedPreferences.getBoolean(key, true);
    }

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        handler = new Handler(context.getMainLooper());
        createdAt = System.currentTimeMillis();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(ACTION_WIFI_AP);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        context.registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        try {
            if (ctx != null) ctx.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (handler != null) handler.removeCallbacksAndMessages(null);
        queued = false;
        mView = null;
    }

    private void show(String message) {
        text = message;
        handler.removeCallbacks(hider);
        handler.postDelayed(hider, SHOW_MS);
        if (mView != null) apply();
        if (!queued) {
            queued = true;
            ctx.enqueue(this);
        }
    }

    private final Runnable hider = () -> {
        if (!queued) return;
        queued = false;
        ctx.dequeue(this);
    };

    private void apply() {
        if (mView == null) return;
        TextView tv = mView.findViewById(R.id.live_short);
        tv.setMaxWidth(ctx.dpToInt(170));
        tv.setText(text);
        tv.setTextColor(ctx.textColor);
        mView.findViewById(R.id.live_icon).setVisibility(View.GONE);
    }

    @Override
    public View onBind() {
        mView = LayoutInflater.from(ctx).inflate(R.layout.live_activity_layout, null);
        mView.findViewById(R.id.live_row).setMinimumHeight(ctx.minHeight);
        apply();
        return mView;
    }

    @Override
    public void onUnbind() {
        mView = null;
    }

    @Override
    public void onExpand() {
    }

    @Override
    public void onCollapse() {
    }

    @Override
    public void onClick() {
    }

    @Override
    public void onTextColorChange() {
        apply();
    }

    @Override
    public String[] permissionsRequired() {
        return null;
    }

    private SettingStruct toggle(String label, String key) {
        return new SettingStruct(label, getName() + " Plugin Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context c) {
                return prefs(c).getBoolean(key, true);
            }

            @Override
            public void onCheckChanged(boolean checked, Context c) {
                prefs(c).edit().putBoolean(key, checked).apply();
            }
        };
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(c.getPackageName(), Context.MODE_PRIVATE);
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        ArrayList<SettingStruct> list = new ArrayList<>();
        list.add(toggle("Bluetooth connected / disconnected", PREF_BT));
        list.add(toggle("Ringer mode (sound, vibrate, silent)", PREF_RINGER));
        list.add(toggle("Airplane mode", PREF_AIRPLANE));
        list.add(toggle("Hotspot on / off", PREF_HOTSPOT));
        list.add(toggle("Headphones plugged in", PREF_HEADSET));
        list.add(toggle("Low battery and battery saver", PREF_BATTERY));
        return list;
    }
}
