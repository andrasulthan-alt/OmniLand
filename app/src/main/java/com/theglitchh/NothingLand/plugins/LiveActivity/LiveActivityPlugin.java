package com.theglitchh.NothingLand.plugins.LiveActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.plugins.BasePlugin;
import com.theglitchh.NothingLand.services.OverlayService;
import com.theglitchh.NothingLand.utils.CallBack;
import com.theglitchh.NothingLand.utils.SettingStruct;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Live Activities": shows calls, timers/stopwatches and download/upload progress
 * in the island. NotiService decides which notifications are live activities and
 * sends them here (".LIVE_POSTED") instead of to the normal notification plugin.
 * Priority when several are active: call > timer > progress.
 */
public class LiveActivityPlugin extends BasePlugin {

    public static final String TYPE_CALL = "call";
    public static final String TYPE_TIMER = "timer";
    public static final String TYPE_PROGRESS = "progress";
    public static final String TYPE_NAV = "nav";
    public static final String TYPE_HOTSPOT = "hotspot";

    // Setting keys, also read by NotiService.
    public static final String PREF_CALLS = "live_calls";
    public static final String PREF_TIMERS = "live_timers";
    public static final String PREF_PROGRESS = "live_progress";
    public static final String PREF_NAV = "live_nav";
    public static final String PREF_HOTSPOT = "live_hotspot";

    // Leading distance such as "In 200 m" / "1.2 km" (navigation parsing after Smart Island, GPL-3.0).
    private static final Pattern DISTANCE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(km|m|mi|ft|meters?|miles?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICES = Pattern.compile("(\\d+)\\s*(?:device|connected|client)", Pattern.CASE_INSENSITIVE);

    // Nothing style: white for "answer", the one red for "decline / hang up".
    private static final int WHITE = Color.WHITE;
    private static final int RED = Color.parseColor("#D71921");
    private static final long ANIMATION_MS = 850;

    private static class Item {
        String type, pkg, title, text, subText;
        int id;
        long when;
        boolean chrono, countdown, incoming, indeterminate;
        int progress, max;
        PendingIntent contentIntent;
        ArrayList<String> actionTitles = new ArrayList<>();
        ArrayList<PendingIntent> actionIntents = new ArrayList<>();
        Icon smallIcon, largeIcon;

        int priority() {
            if (TYPE_CALL.equals(type)) return 0;
            if (TYPE_NAV.equals(type)) return 1;
            if (TYPE_TIMER.equals(type)) return 2;
            if (TYPE_PROGRESS.equals(type)) return 3;
            return 4;
        }
    }

    private OverlayService ctx;
    private Handler handler;
    private final ArrayList<Item> items = new ArrayList<>();
    private Item current;
    private View mView;
    private boolean expanded = false;
    private boolean queued = false;

    @Override
    public String getID() {
        return "LiveActivityPlugin";
    }

    @Override
    public String getName() {
        return "Live Activities";
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle extras = intent.getExtras();
            if (action == null || extras == null) return;
            try {
                if (action.equals(ctx.getPackageName() + ".LIVE_POSTED")) {
                    onLivePosted(extras);
                } else if (action.equals(ctx.getPackageName() + ".NOTIFICATION_REMOVED")
                        || action.equals(ctx.getPackageName() + ".NOTIFICATION_POSTED")) {
                    // Removed, or turned into a normal notification (e.g. "Download complete").
                    removeItem(extras.getString("package_name"), extras.getInt("id"));
                }
            } catch (Exception e) {
                Log.w("LiveActivityPlugin", "Could not handle " + action, e);
            }
        }
    };

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        handler = new Handler(context.getMainLooper());
        IntentFilter filter = new IntentFilter();
        filter.addAction(context.getPackageName() + ".LIVE_POSTED");
        filter.addAction(context.getPackageName() + ".NOTIFICATION_REMOVED");
        filter.addAction(context.getPackageName() + ".NOTIFICATION_POSTED");
        com.theglitchh.NothingLand.utils.Broadcasts.register(context, receiver, filter);
    }

    @Override
    public void onDestroy() {
        // Also called for plugins that were never created (when disabled), so guard everything.
        try {
            if (ctx != null) ctx.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (handler != null) handler.removeCallbacksAndMessages(null);
        items.clear();
        current = null;
        queued = false;
    }

    // ---------------------------------------------------------------- data

    @SuppressWarnings("unchecked")
    private void onLivePosted(Bundle e) {
        Item item = new Item();
        item.type = e.getString("live_type", TYPE_PROGRESS);
        item.pkg = e.getString("package_name", "");
        item.id = e.getInt("id");
        item.title = e.getString("title", "");
        item.text = e.getString("body", "");
        item.subText = e.getString("subtext", "");
        item.when = e.getLong("when", System.currentTimeMillis());
        item.chrono = e.getBoolean("chrono", false);
        item.countdown = e.getBoolean("countdown", false);
        item.incoming = e.getBoolean("incoming", false);
        item.progress = e.getInt("progress", 0);
        item.max = e.getInt("progress_max", 0);
        item.indeterminate = e.getBoolean("indeterminate", false);
        item.contentIntent = e.getParcelable("content_intent");
        item.smallIcon = e.getParcelable("icon_small");
        item.largeIcon = e.getParcelable("icon_large");
        ArrayList<String> titles = e.getStringArrayList("action_titles");
        ArrayList<PendingIntent> intents = e.getParcelableArrayList("action_intents");
        if (titles != null && intents != null && titles.size() == intents.size()) {
            item.actionTitles = titles;
            item.actionIntents = intents;
        }

        for (int i = 0; i < items.size(); i++) {
            Item old = items.get(i);
            if (old.id == item.id && old.pkg.equals(item.pkg)) {
                items.remove(i);
                break;
            }
        }
        items.add(0, item);
        refresh();
    }

    private void removeItem(String pkg, int id) {
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.id == id && (pkg == null || it.pkg.equals(pkg))) {
                items.remove(i);
                changed = true;
                break;
            }
        }
        if (changed) refresh();
    }

    private Item pickCurrent() {
        Item best = null;
        for (Item it : items) {
            if (best == null || it.priority() < best.priority()) best = it;
        }
        return best;
    }

    private void refresh() {
        Item next = pickCurrent();
        if (next == null) {
            current = null;
            if (!queued) return;
            if (expanded) {
                collapse();
                handler.postDelayed(this::dequeueIfEmpty, ANIMATION_MS);
            } else {
                dequeueIfEmpty();
            }
            return;
        }
        boolean wasIncoming = current != null && TYPE_CALL.equals(current.type) && current.incoming;
        current = next;
        boolean isIncoming = TYPE_CALL.equals(current.type) && current.incoming;
        if (!queued) {
            queued = true;
            ctx.enqueue(this);
        } else if (isIncoming && !wasIncoming) {
            // An incoming call always takes over the island, even if music was put in front.
            ctx.promote(this);
        }
        updateViews();
        ctx.refreshBubble();
        if (isIncoming && !expanded && mView != null) {
            onExpand();
        } else if (wasIncoming && !isIncoming && expanded) {
            // Call was answered: go back to the small island with the call timer.
            collapse();
        } else if (expanded) {
            resizeExpanded();
        }
    }

    private void dequeueIfEmpty() {
        if (pickCurrent() != null || !queued) return;
        queued = false;
        ctx.dequeue(this);
    }

    // ---------------------------------------------------------------- views

    @Override
    public View onBind() {
        mView = LayoutInflater.from(ctx).inflate(R.layout.live_activity_layout, null);
        View row = mView.findViewById(R.id.live_row);
        row.setMinimumHeight(ctx.minHeight);
        mView.findViewById(R.id.live_action_1).setOnClickListener(v -> runAction(0));
        mView.findViewById(R.id.live_action_2).setOnClickListener(v -> runAction(1));
        updateViews();
        return mView;
    }

    @Override
    public void onBindComplete() {
        updateViews();
        if (current != null && TYPE_CALL.equals(current.type) && current.incoming) onExpand();
    }

    @Override
    public void onUnbind() {
        stopChronometers();
        expanded = false;
        mView = null;
    }

    private void stopChronometers() {
        if (mView == null) return;
        ((Chronometer) mView.findViewById(R.id.live_chrono)).stop();
        ((Chronometer) mView.findViewById(R.id.live_panel_chrono)).stop();
    }

    private Drawable appIcon(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateViews() {
        if (mView == null || current == null) return;
        Item it = current;
        int color = ctx.textColor;

        ImageView icon = mView.findViewById(R.id.live_icon);
        ImageView panelIcon = mView.findViewById(R.id.live_panel_icon);
        TextView shortText = mView.findViewById(R.id.live_short);
        Chronometer chrono = mView.findViewById(R.id.live_chrono);
        Chronometer panelChrono = mView.findViewById(R.id.live_panel_chrono);
        TextView title = mView.findViewById(R.id.live_title);
        TextView text = mView.findViewById(R.id.live_text);
        ProgressBar bar = mView.findViewById(R.id.live_progress);

        // Small island icon: the notification's own small icon, tinted like the island text.
        Drawable small = null;
        try {
            if (it.smallIcon != null) small = it.smallIcon.loadDrawable(ctx);
        } catch (Exception ignored) {
        }
        Drawable app = appIcon(it.pkg);
        Drawable large = null;
        try {
            if (it.largeIcon != null) large = it.largeIcon.loadDrawable(ctx);
        } catch (Exception ignored) {
        }
        if (TYPE_NAV.equals(it.type) && large != null) {
            // Navigation: the maneuver arrow is the important icon.
            icon.setImageDrawable(large);
            icon.setImageTintList(null);
            panelIcon.setImageDrawable(large);
        } else {
            if (small != null) {
                icon.setImageDrawable(small);
                icon.setImageTintList(ColorStateList.valueOf(color));
            } else {
                icon.setImageDrawable(app);
                icon.setImageTintList(null);
            }
            panelIcon.setImageDrawable(app != null ? app : small);
        }

        title.setText(it.title);
        title.setTextColor(color);
        String body = it.text == null ? "" : it.text;
        if (it.subText != null && !it.subText.isEmpty()) {
            body = body.isEmpty() ? it.subText : body + "\n" + it.subText;
        }
        text.setText(body);
        text.setTextColor(color);
        text.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        shortText.setTextColor(color);
        chrono.setTextColor(color);
        panelChrono.setTextColor(color);

        // Live time for ongoing calls, timers and stopwatches.
        boolean showTime = it.chrono && !(TYPE_CALL.equals(it.type) && it.incoming);
        if (showTime) {
            long base = SystemClock.elapsedRealtime() + (it.when - System.currentTimeMillis());
            for (Chronometer c : new Chronometer[]{chrono, panelChrono}) {
                c.setCountDown(it.countdown);
                c.setBase(base);
                c.setVisibility(View.VISIBLE);
                c.start();
            }
            shortText.setVisibility(View.GONE);
        } else {
            chrono.stop();
            panelChrono.stop();
            chrono.setVisibility(View.GONE);
            panelChrono.setVisibility(View.GONE);
            shortText.setVisibility(View.VISIBLE);
            shortText.setText(shortLabel(it));
        }

        // Progress bar for downloads/uploads.
        if (TYPE_PROGRESS.equals(it.type)) {
            bar.setVisibility(View.VISIBLE);
            bar.setIndeterminate(it.indeterminate || it.max <= 0);
            if (it.max > 0) {
                bar.setMax(it.max);
                bar.setProgress(it.progress);
            }
            bar.setProgressTintList(ColorStateList.valueOf(color));
            bar.setIndeterminateTintList(ColorStateList.valueOf(color));
        } else {
            bar.setVisibility(View.GONE);
        }

        updateActions();
    }

    private String shortLabel(Item it) {
        if (TYPE_PROGRESS.equals(it.type)) {
            if (it.indeterminate || it.max <= 0) return "•••";
            return String.format(Locale.US, "%d%%", Math.round(100f * it.progress / it.max));
        }
        if (TYPE_CALL.equals(it.type)) return it.title;
        if (TYPE_NAV.equals(it.type)) {
            Matcher m = DISTANCE.matcher(it.title == null ? "" : it.title);
            if (m.find()) return m.group(0);
            return it.title;
        }
        if (TYPE_HOTSPOT.equals(it.type)) {
            Matcher m = DEVICES.matcher((it.title == null ? "" : it.title) + " " + (it.text == null ? "" : it.text));
            if (m.find()) return m.group(1) + ("1".equals(m.group(1)) ? " device" : " devices");
            return "On";
        }
        // Timer from an app that doesn't expose a live clock: show its own text.
        return it.text != null && !it.text.isEmpty() ? it.text : it.title;
    }

    /** Buttons: answer/decline for incoming calls, hang up for ongoing calls. */
    private void updateActions() {
        if (mView == null || current == null) return;
        View holder = mView.findViewById(R.id.live_actions);
        Button b1 = mView.findViewById(R.id.live_action_1);
        Button b2 = mView.findViewById(R.id.live_action_2);
        int count = TYPE_CALL.equals(current.type) ? Math.min(2, current.actionTitles.size()) : 0;
        holder.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        styleButton(b1, count > 0 ? current.actionTitles.get(0) : null);
        styleButton(b2, count > 1 ? current.actionTitles.get(1) : null);
    }

    private void styleButton(Button b, String label) {
        if (label == null) {
            b.setVisibility(View.GONE);
            return;
        }
        b.setVisibility(View.VISIBLE);
        b.setText(label.toUpperCase(Locale.ROOT));
        boolean positive = isPositive(label);
        b.setBackgroundTintList(ColorStateList.valueOf(positive ? WHITE : RED));
        b.setTextColor(positive ? Color.BLACK : Color.WHITE);
        b.setLetterSpacing(0.08f);
    }

    private static boolean isPositive(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        return l.contains("answer") || l.contains("accept") || l.contains("jawab")
                || l.contains("terima") || l.contains("angkat");
    }

    private void runAction(int index) {
        if (current == null || index >= current.actionIntents.size()) return;
        try {
            current.actionIntents.get(index).send();
        } catch (Exception e) {
            Log.w("LiveActivityPlugin", "Action failed", e);
        }
    }

    // ---------------------------------------------------------------- expand / collapse

    private int expandedHeight() {
        int h = ctx.statusBarHeight + ctx.dpToInt(84);
        if (current != null && TYPE_PROGRESS.equals(current.type)) h += ctx.dpToInt(24);
        if (current != null && current.subText != null && !current.subText.isEmpty()) h += ctx.dpToInt(20);
        if (current != null && TYPE_CALL.equals(current.type) && !current.actionTitles.isEmpty()) h += ctx.dpToInt(60);
        return h;
    }

    private final CallBack startCallBack = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null) return;
            ViewGroup.LayoutParams lp = mView.getLayoutParams();
            if (expanded) {
                mView.findViewById(R.id.live_row).setVisibility(View.GONE);
                mView.findViewById(R.id.live_panel).setVisibility(View.VISIBLE);
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);
                if (lp != null) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    mView.setLayoutParams(lp);
                }
            } else {
                mView.findViewById(R.id.live_panel).setVisibility(View.GONE);
                mView.findViewById(R.id.live_row).setVisibility(View.VISIBLE);
                mView.setPadding(0, 0, 0, 0);
            }
        }
    };

    private final CallBack endCallBack = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null || expanded) return;
            ViewGroup.LayoutParams lp = mView.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mView.setLayoutParams(lp);
            }
        }
    };

    @Override
    public void onExpand() {
        if (expanded || mView == null || current == null) return;
        expanded = true;
        ctx.animateOverlay(expandedHeight(), ctx.metrics.widthPixels - ctx.dpToInt(15), true,
                startCallBack, endCallBack, false);
    }

    private void resizeExpanded() {
        if (!expanded || mView == null) return;
        ctx.animateOverlay(expandedHeight(), ctx.metrics.widthPixels - ctx.dpToInt(15), true,
                new CallBack(), new CallBack(), true);
    }

    private void collapse() {
        if (!expanded || mView == null) return;
        expanded = false;
        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT, false,
                startCallBack, endCallBack, false);
    }

    @Override
    public void onCollapse() {
        // Keep an incoming call open until it's answered or declined.
        if (current != null && TYPE_CALL.equals(current.type) && current.incoming) return;
        collapse();
    }

    @Override
    public void onClick() {
        if (current == null) return;
        if (!expanded && TYPE_CALL.equals(current.type) && current.incoming) {
            onExpand();
            return;
        }
        if (current.contentIntent != null) {
            try {
                current.contentIntent.send();
            } catch (Exception e) {
                Log.w("LiveActivityPlugin", "Could not open app", e);
            }
        }
    }

    @Override
    public void onSwipeUp() {
        onCollapse();
    }

    @Override
    public void onTextColorChange() {
        updateViews();
    }

    @Override
    public Drawable getMiniIcon() {
        if (current == null) return null;
        try {
            if (current.smallIcon != null) {
                Drawable d = current.smallIcon.loadDrawable(ctx);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(ctx.textColor);
                    return d;
                }
            }
        } catch (Exception ignored) {
        }
        return appIcon(current.pkg);
    }

    @Override
    public String[] permissionsRequired() {
        return null;
    }

    // ---------------------------------------------------------------- settings

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
        list.add(toggle("Show calls", PREF_CALLS));
        list.add(toggle("Show timers and stopwatches", PREF_TIMERS));
        list.add(toggle("Show download and upload progress", PREF_PROGRESS));
        list.add(toggle("Show Maps navigation", PREF_NAV));
        list.add(toggle("Show hotspot status", PREF_HOTSPOT));
        return list;
    }
}
