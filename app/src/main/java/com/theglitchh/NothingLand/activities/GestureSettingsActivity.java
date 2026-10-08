package com.theglitchh.NothingLand.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.utils.QuickActions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings for what happens when the idle island is tapped, double-tapped,
 * long-pressed or swiped, plus the favorite apps shown in the apps card.
 */
public class GestureSettingsActivity extends AppCompatActivity {

    public static final String[] KEYS = {"gesture_tap", "gesture_double_tap", "gesture_long_press",
            "gesture_swipe_left", "gesture_swipe_right", "gesture_swipe_up", "gesture_swipe_down"};
    private static final String[] NAMES = {"Tap", "Double tap", "Long press",
            "Swipe left", "Swipe right", "Swipe up", "Swipe down"};
    /** Defaults when nothing has been chosen yet. */
    public static final String[] DEFAULTS = {QuickActions.NONE, QuickActions.FLASHLIGHT, QuickActions.SLIDERS,
            QuickActions.NONE, QuickActions.NONE, QuickActions.NONE, QuickActions.NOTIFICATIONS};
    public static final String PREF_FAVORITES = "favorite_apps";

    private SharedPreferences prefs;
    /** Value text of each row (gestures first, then favorite apps), refreshed after a change. */
    private final List<TextView> values = new ArrayList<>();

    public static String actionFor(SharedPreferences prefs, String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return prefs.getString(key, DEFAULTS[i]);
        }
        return prefs.getString(key, QuickActions.NONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Gestures and quick cards");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        // Nothing-style page: big dot-matrix title, small red section captions and
        // rounded near-black rows with the chosen action on the right.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(32));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_baseline_arrow_back_ios_24);
        back.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        back.setPadding(dp(4), dp(16), dp(16), dp(8));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        content.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Gestures and\nquick cards");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        title.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(title);

        TextView hint = new TextView(this);
        hint.setText("What the island does when nothing is playing or active. Tap a gesture to choose its action.");
        hint.setTextSize(13);
        hint.setTextColor(getColor(R.color.omni_grey));
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(hint);

        content.addView(section("Gestures"));
        for (int i = 0; i < KEYS.length; i++) {
            final int gesture = i;
            content.addView(row(NAMES[i], v -> pickAction(gesture)));
        }
        content.addView(section("Quick cards"));
        content.addView(row("Favorite apps", v -> pickFavorites()));
        refreshRows();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(content);
        setContentView(scroll);
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase(Locale.ROOT));
        tv.setTextSize(11);
        tv.setLetterSpacing(0.12f);
        tv.setTextColor(getColor(R.color.omni_red));
        tv.setPadding(dp(8), dp(24), dp(8), dp(8));
        return tv;
    }

    private View row(String name, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.omni_card_bg));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), getColor(R.color.omni_card_stroke));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(16);
        label.setTextColor(Color.WHITE);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setTextSize(13);
        value.setTextColor(getColor(R.color.omni_grey));
        value.setGravity(Gravity.END);
        value.setMaxWidth(dp(170));
        value.setPadding(dp(12), 0, 0, 0);
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        values.add(value);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
    }

    /** Shows the current action for each gesture and the favorites summary. */
    private void refreshRows() {
        for (int i = 0; i < values.size(); i++) {
            String text = i < KEYS.length
                    ? QuickActions.label(this, actionFor(prefs, KEYS[i]))
                    : favoritesSummary();
            values.get(i).setText(text);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String favoritesSummary() {
        String csv = prefs.getString(PREF_FAVORITES, "");
        if (csv == null || csv.trim().isEmpty()) return "None";
        int n = csv.split(",").length;
        return n + (n == 1 ? " app" : " apps");
    }

    private void pickAction(int gesture) {
        String current = actionFor(prefs, KEYS[gesture]);
        int checked = 0;
        for (int i = 0; i < QuickActions.IDS.length; i++) {
            if (QuickActions.IDS[i].equals(current)) checked = i;
        }
        if (current.startsWith(QuickActions.APP_PREFIX)) checked = QuickActions.IDS.length - 1;
        new MaterialAlertDialogBuilder(this)
                .setTitle(NAMES[gesture])
                .setSingleChoiceItems(QuickActions.LABELS, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String id = QuickActions.IDS[which];
                    if (QuickActions.OPEN_APP.equals(id)) {
                        pickApp(gesture);
                    } else {
                        save(KEYS[gesture], id);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static class AppEntry {
        String pkg, name;
    }

    private List<AppEntry> launchableApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<>();
        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            AppEntry e = new AppEntry();
            e.pkg = pkg;
            e.name = String.valueOf(ri.loadLabel(pm));
            apps.add(e);
        }
        Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return apps;
    }

    private void pickApp(int gesture) {
        List<AppEntry> apps = launchableApps();
        String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) names[i] = apps.get(i).name;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Open which app?")
                .setItems(names, (dialog, which) -> save(KEYS[gesture], QuickActions.APP_PREFIX + apps.get(which).pkg))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickFavorites() {
        List<AppEntry> apps = launchableApps();
        String csv = prefs.getString(PREF_FAVORITES, "");
        Set<String> chosen = new HashSet<>();
        if (csv != null) chosen.addAll(Arrays.asList(csv.split(",")));
        String[] names = new String[apps.size()];
        boolean[] checks = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).name;
            checks[i] = chosen.contains(apps.get(i).pkg);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Favorite apps (up to 8)")
                .setMultiChoiceItems(names, checks, (dialog, which, isChecked) -> checks[which] = isChecked)
                .setPositiveButton("Save", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (int i = 0; i < apps.size() && n < 8; i++) {
                        if (!checks[i]) continue;
                        if (sb.length() > 0) sb.append(",");
                        sb.append(apps.get(i).pkg);
                        n++;
                    }
                    save(PREF_FAVORITES, sb.toString());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save(String key, String value) {
        prefs.edit().putString(key, value).apply();
        refreshRows();
        // Tell the island service so the change applies right away.
        Intent intent = new Intent(getPackageName() + ".SETTINGS_CHANGED");
        Bundle b = new Bundle();
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) b.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof String) b.putString(e.getKey(), (String) v);
        }
        intent.putExtra("settings", b);
        com.theglitchh.NothingLand.utils.Broadcasts.send(this, intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
