package com.theglitchh.NothingLand.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theglitchh.NothingLand.BuildConfig;
import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.plugins.ExportedPlugins;
import com.theglitchh.NothingLand.services.OverlayService;
import com.theglitchh.NothingLand.services.UpdaterService;
import com.theglitchh.NothingLand.utils.adapters.RecylerViewSettingsAdapter;
import com.theglitchh.NothingLand.utils.SettingStruct;
import com.theglitchh.NothingLand.utils.DemoNotifications;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences sharedPreferences;
    private final ArrayList<SettingStruct> settings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (sharedPreferences.getBoolean("clip_copy_enabled", true)) {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("OmniLand error log", throwable.getMessage() + " : " + Arrays.toString(throwable.getStackTrace()));
                clipboard.setPrimaryClip(clip);
                sendCrashNotification();
            }
            Runtime.getRuntime().exit(0);
        });
        init();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if ((Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners") != null && !Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners").contains(getApplicationContext().getPackageName())
        ) || ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, PermissionActivity.class));
        }
        // Ask once for the runtime permissions the island uses: notifications (Android 13+)
        // and Bluetooth, for the "headphones connected" mini event (Android 12+).
        ArrayList<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!ask.isEmpty()) {
            ActivityCompat.requestPermissions(this, ask.toArray(new String[0]), 105);
        }
        MaterialCardView enable_btn = findViewById(R.id.enable_switch);
        enable_btn.setOnClickListener(l -> {
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "OmniLand is already enabled", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ blocks sideloaded apps from turning on an Accessibility
                // Service directly ("Restricted setting"). Guide the user around it.
                new MaterialAlertDialogBuilder(this)
                        .setTitle("One extra step on Android 13+")
                        .setMessage("Sideloaded apps can't enable this directly on Android 13 and up.\n\n" +
                                "If the toggle is greyed out or shows \"Restricted setting\":\n" +
                                "1. Open OmniLand's App Info page\n" +
                                "2. Tap the 3-dot menu (top right)\n" +
                                "3. Tap \"Allow restricted settings\"\n" +
                                "4. Come back here and open Accessibility settings again")
                        .setPositiveButton("Open App Info", (d, w) ->
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName()))))
                        .setNeutralButton("Open Accessibility Settings", (d, w) ->
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(this, "Installed Apps -> OmniLand", Toast.LENGTH_SHORT).show();
            }
        });
        updateEnableStatus();
        settings.add(new SettingStruct("Manage Overlay Layout", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                startActivity(new Intent(MainActivity.this, OverlayLayoutSettingActivity.class));
            }
        });

        settings.add(new SettingStruct("Overlay color", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context ctx) {
                startActivity(new Intent(MainActivity.this, AppearanceActivity.class));
            }
        });
        if (BuildConfig.AUTO_UPDATE)
            settings.add(new SettingStruct("Enable auto update checking", "App Settings", SettingStruct.TYPE_TOGGLE) {
                @Override
                public boolean onAttach(Context ctx) {
                    return sharedPreferences.getBoolean("update_enabled", true);
                }

                @Override
                public void onCheckChanged(boolean checked, Context ctx) {
                    sharedPreferences.edit().putBoolean("update_enabled", checked).apply();
                }
            });
        settings.add(new SettingStruct("Disable battery optimization", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                requestIgnoreBatteryOptimizations();
            }
        });
        settings.add(new SettingStruct("Invert long press and click functions", "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("invert_click", checked).apply();
            }

            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("invert_click", false);
            }
        });
        settings.add(new SettingStruct("Enable on lockscreen", "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("enable_on_lockscreen", false);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("enable_on_lockscreen", checked).apply();
            }
        });
        settings.add(new SettingStruct("Gestures and quick cards", "App Settings", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                startActivity(new Intent(MainActivity.this, GestureSettingsActivity.class));
            }
        });
        settings.add(new SettingStruct("Material You colours (island and app)", "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("wallpaper_color", false);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                if (sharedPreferences.getBoolean("wallpaper_color", false) == checked) return;
                sharedPreferences.edit().putBoolean("wallpaper_color", checked).apply();
                // Redraw the settings in the new colours right away (only on a real change).
                MainActivity.this.recreate();
            }
        });
        settings.add(appToggle("Smooth animations", "smooth_animation"));
        settings.add(appToggle("Show a second activity as a bubble", "split_bubble"));
        settings.add(appToggle("Color the island from album art", "album_color"));
        settings.add(appToggle("Haptic feedback", "haptics_enabled"));
        settings.add(new SettingStruct("Copy crash logs to clipboard", "App Settings") {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean("clip_copy_enabled", true);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean("clip_copy_enabled", checked).apply();
            }
        });
        settings.add(null);
        settings.add(demo("Demo: incoming call", () -> DemoNotifications.incomingCall(this)));
        settings.add(demo("Demo: 3-minute timer", () -> DemoNotifications.timer(this)));
        settings.add(demo("Demo: download progress", () -> DemoNotifications.download(this)));
        settings.add(demo("Demo: Maps navigation", () -> DemoNotifications.navigation(this)));
        settings.add(demo("Demo: clear all demo items", () -> DemoNotifications.clearAll(this)));
        settings.add(null);
        ExportedPlugins.getPlugins().forEach(x -> {
            settings.add(new SettingStruct("Enable " + x.getName() + " Plugin", x.getName() + " Plugin Settings") {
                             @Override
                             public boolean onAttach(Context ctx) {
                                 return sharedPreferences.getBoolean(x.getID() + "_enabled", true);
                             }

                             @Override
                             public void onCheckChanged(boolean checked, Context ctx) {
                                 sharedPreferences.edit().putBoolean(x.getID() + "_enabled", checked).apply();
                             }
                         }
            );
            if (x.getSettings() != null) {
                settings.addAll(x.getSettings());
            }
            settings.add(null);
        });
        RecylerViewSettingsAdapter adapter = new RecylerViewSettingsAdapter(this, settings);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new ItemDecoration());
        recyclerView.invalidateItemDecorations();
        if (sharedPreferences.getBoolean("update_enabled", true) && BuildConfig.AUTO_UPDATE)
            startService(new Intent(this, UpdaterService.class));
        if (BuildConfig.AUTO_UPDATE)
            com.theglitchh.NothingLand.utils.Broadcasts.register(this, broadcastReceiver, new IntentFilter(getPackageName() + ".UPDATE_AVAIL"));

    }

    private RecyclerView recyclerView;

    /** A tappable demo entry that fires a fake event for testing the island. */
    private SettingStruct demo(String label, Runnable action) {
        return new SettingStruct(label, "Demo Mode", SettingStruct.TYPE_CUSTOM) {
            @Override
            public void onClick(Context c) {
                action.run();
                Toast.makeText(MainActivity.this, "Sent. Check the island.", Toast.LENGTH_SHORT).show();
            }
        };
    }

    /** On/off setting stored under key, on by default. */
    private SettingStruct appToggle(String label, String key) {
        return new SettingStruct(label, "App Settings", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return sharedPreferences.getBoolean(key, true);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                sharedPreferences.edit().putBoolean(key, checked).apply();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        updateEnableStatus();
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + OverlayService.class.getName();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        for (String s : enabledServices.split(":")) {
            if (s.equalsIgnoreCase(service)) return true;
        }
        return false;
    }

    private void updateEnableStatus() {
        TextView status = findViewById(R.id.enable_status_text);
        if (status == null) return;
        status.setText(isAccessibilityServiceEnabled() ? "Enabled" : "Tap to enable");
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String pkg = getPackageName();
        if (pm != null && pm.isIgnoringBatteryOptimizations(pkg)) {
            Toast.makeText(this, "Battery optimization already disabled for OmniLand", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

    }

    private void init() {
        if (sharedPreferences == null) {

            sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Intent intent = new Intent(getPackageName() + ".SETTINGS_CHANGED");
        Bundle b = new Bundle();
        sharedPreferences.getAll().forEach((key, value) -> {
            if (value instanceof Boolean) {
                b.putBoolean(key, (boolean) value);
            } else if (value instanceof String) {
                b.putString(key, (String) value);
            }
        });
        intent.putExtra("settings", b);
        com.theglitchh.NothingLand.utils.Broadcasts.send(this, intent);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!(getPackageName() + ".UPDATE_AVAIL").equals(intent.getAction())) return;
            String version = intent.getStringExtra("version");
            String url = intent.getStringExtra("url");
            if (isFinishing() || url == null) return;
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("Update available")
                    .setMessage("OmniLand " + (version != null ? version : "update") + " is available (you have "
                            + BuildConfig.VERSION_NAME + ").\n\nDownload it from the release page, or update through "
                            + "Obtainium or Komi Store. Your settings are kept.")
                    .setNegativeButton("Later", (dialogInterface, i) -> dialogInterface.dismiss())
                    .setPositiveButton("Open download page", (dialogInterface, i) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(MainActivity.this, "No browser found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        }
    };

    private int dpToInt(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public class ItemDecoration extends RecyclerView.ItemDecoration {
        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int color = MaterialColors.getColor(MainActivity.this, com.google.android.material.R.attr.colorOnSecondary, getColor(R.color.md_theme_dark_secondary));
            super.onDraw(c, parent, state);
            int childCount = recyclerView.getChildCount();
            int width = recyclerView.getWidth();
            Rect cornerBounds = new Rect();
            c.getClipBounds(cornerBounds);
            for (int i = 0; i < childCount; i++) {
                View childAt = recyclerView.getChildAt(i);
                RecylerViewSettingsAdapter.ViewHolder viewHolder = (RecylerViewSettingsAdapter.ViewHolder) recyclerView.getChildViewHolder(childAt);
                int vo = recyclerView.getChildAdapterPosition(childAt);
                if (!viewHolder.isItem) {
                    if (settings.size() >= vo + 2 && settings.get(vo + 1) != null) {
                        c.drawText(settings.get(vo + 1).category, cornerBounds.left + dpToInt(10), childAt.getBottom() - dpToInt(30), new Paint() {
                            {
                                setColor(MainActivity.this.getColor(R.color.quite_white));
                                setTextSize(dpToInt(16));
                            }
                        });
                    }
                    Drawable cornerBottom = getDrawable(R.drawable.rounded_corner_setting_bottom);
                    Drawable cornerTop = getDrawable(R.drawable.rounded_corner_setting_top);
                    if (recyclerView.getChildAt(i + 1) != null) {
                        View v = recyclerView.getChildAt(i + 1);
                        cornerTop.setBounds(cornerBounds.left, (int) v.getY() - dpToInt(20), cornerBounds.right, (int) v.getY());
                        cornerTop.draw(c);
                    }
                    if (recyclerView.getChildAt(i - 1) != null) {
                        View v = recyclerView.getChildAt(i - 1);
                        cornerBottom.setBounds(cornerBounds.left, v.getBottom(), cornerBounds.right, v.getBottom() + dpToInt(20));
                        cornerBottom.draw(c);
                    }
                } else {
                    Rect bounds = new Rect(cornerBounds.left, (int) childAt.getY(), cornerBounds.right, childAt.getBottom());
                    c.drawRect(bounds, new Paint() {
                        {
                            setColor(color);
                        }
                    });
                }

            }
            if (recyclerView.getChildCount() < 1) return;
            if (((RecylerViewSettingsAdapter.ViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(0))).isItem) {
                Drawable roundedCornerTop = getDrawable(R.drawable.rounded_corner_setting_top);
                roundedCornerTop.setBounds(cornerBounds.left, cornerBounds.top, cornerBounds.right, (int) recyclerView.getChildAt(0).getY());
                roundedCornerTop.draw(c);
            }
            if (((RecylerViewSettingsAdapter.ViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(recyclerView.getChildCount() - 1))).isItem) {
                Drawable roundedCornerBottom = getDrawable(R.drawable.rounded_corner_setting_bottom);
                roundedCornerBottom.setBounds(cornerBounds.left,
                        recyclerView.getChildAt(recyclerView.getChildCount() - 1).getBottom(),
                        cornerBounds.right,
                        recyclerView.getChildAt(recyclerView.getChildCount() - 1).getBottom() + dpToInt(20));
                roundedCornerBottom.draw(c);
            }
        }
    }

    private void sendCrashNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final String NOTIFICATION_CHANNEL_ID = getPackageName() + ".updater_channel";
        String channelName = "Updater Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN);
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(false)
                .setContentTitle("OmniLand Crashed")
                .setContentText("Crash Log copied to clipboard")
                .setSmallIcon(R.drawable.launcher_foreground)
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_ERROR)
                .build();
        manager.notify(100, notification);
    }
}
