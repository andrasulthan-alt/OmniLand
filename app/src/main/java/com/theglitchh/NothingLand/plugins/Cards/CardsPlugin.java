package com.theglitchh.NothingLand.plugins.Cards;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.plugins.BasePlugin;
import com.theglitchh.NothingLand.services.OverlayService;
import com.theglitchh.NothingLand.utils.CallBack;
import com.theglitchh.NothingLand.utils.QuickActions;
import com.theglitchh.NothingLand.utils.SettingStruct;
import com.theglitchh.NothingLand.views.DotSliderDrawable;

import java.util.ArrayList;

/**
 * "Quick Cards": panels the user opens with a gesture on the idle island.
 * - sliders: screen brightness, media volume, flashlight brightness
 * - apps: grid of favorite apps
 * The views are built in code so no extra layout files are needed.
 */
public class CardsPlugin extends BasePlugin {

    public static final String CARD_SLIDERS = QuickActions.SLIDERS;
    public static final String CARD_APPS = QuickActions.APPS;
    private static final long ANIMATION_MS = 850;
    private static final long AUTO_CLOSE_MS = 10_000;

    private OverlayService ctx;
    private Handler handler;
    private String card;
    private LinearLayout mView;
    private LinearLayout panel;
    private boolean expanded = false;
    private boolean queued = false;

    @Override
    public String getID() {
        return "CardsPlugin";
    }

    @Override
    public String getName() {
        return "Quick Cards";
    }

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        handler = new Handler(context.getMainLooper());
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        card = null;
        queued = false;
        expanded = false;
        mView = null;
    }

    /** Called by the overlay service when a gesture asks for a card. */
    public void show(String which) {
        if (ctx == null) return;
        if (queued && which.equals(card)) {
            // Same card again: toggle it closed.
            close();
            return;
        }
        card = which;
        if (queued) {
            rebuildPanel();
            resetAutoClose();
            return;
        }
        queued = true;
        ctx.enqueue(this);
    }

    private void resetAutoClose() {
        handler.removeCallbacks(closer);
        handler.postDelayed(closer, AUTO_CLOSE_MS);
    }

    private final Runnable closer = this::close;

    private void close() {
        handler.removeCallbacks(closer);
        if (!queued) return;
        if (expanded) {
            collapse();
            handler.postDelayed(this::dequeueNow, ANIMATION_MS);
        } else {
            dequeueNow();
        }
    }

    private void dequeueNow() {
        if (!queued) return;
        queued = false;
        card = null;
        ctx.dequeue(this);
    }

    // ---------------------------------------------------------------- views

    @Override
    public View onBind() {
        mView = new LinearLayout(ctx);
        mView.setId(R.id.binded);
        mView.setOrientation(LinearLayout.VERTICAL);
        // Empty compact row so the island keeps its normal size before it expands.
        View row = new View(ctx);
        row.setMinimumHeight(ctx.minHeight);
        row.setMinimumWidth(ctx.dpToInt(83));
        mView.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.minHeight));
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = ctx.dpToInt(18);
        panel.setPadding(pad, ctx.dpToInt(8), pad, ctx.dpToInt(14));
        panel.setVisibility(View.GONE);
        mView.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildPanel();
        return mView;
    }

    @Override
    public void onBindComplete() {
        onExpand();
        resetAutoClose();
    }

    @Override
    public void onUnbind() {
        expanded = false;
        mView = null;
        panel = null;
    }

    private void rebuildPanel() {
        if (panel == null) return;
        panel.removeAllViews();
        if (CARD_APPS.equals(card)) buildAppsCard();
        else buildSlidersCard();
    }

    /** Small uppercase caption above a slider, Nothing style. */
    private TextView label(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase(java.util.Locale.ROOT));
        tv.setTextColor(ctx.textColor);
        tv.setAlpha(0.7f);
        tv.setTextSize(11);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(0, ctx.dpToInt(8), 0, 0);
        return tv;
    }

    /** Small grey note under a caption (e.g. why the flashlight has no levels). */
    private TextView note(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ctx.textColor);
        tv.setAlpha(0.45f);
        tv.setTextSize(10);
        return tv;
    }

    private SeekBar slider(int max, int value, SeekBar.OnSeekBarChangeListener listener) {
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max);
        sb.setProgress(value);
        // Dotted Nothing-style track with one red dot at the current value; no thumb.
        sb.setProgressDrawable(new DotSliderDrawable(ctx.textColor, DotSliderDrawable.NOTHING_RED,
                ctx.getResources().getDisplayMetrics().density));
        sb.setThumb(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        sb.setSplitTrack(false);
        sb.setMinimumHeight(ctx.dpToInt(32));
        sb.setOnSeekBarChangeListener(listener);
        panel.addView(sb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sb;
    }

    private abstract static class OnChange implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private void buildSlidersCard() {
        // Brightness
        panel.addView(label("Brightness"));
        int brightness = 128;
        try {
            brightness = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) {
        }
        slider(255, brightness, new OnChange() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                resetAutoClose();
                if (!Settings.System.canWrite(ctx)) {
                    Toast.makeText(ctx, "Allow OmniLand to modify system settings for brightness", Toast.LENGTH_SHORT).show();
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        i.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                    } catch (Exception ignored) {
                    }
                    close();
                    return;
                }
                try {
                    Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, value));
                } catch (Exception e) {
                    Log.w("CardsPlugin", "Brightness failed", e);
                }
            }
        });

        // Media volume
        final AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        panel.addView(label("Media volume"));
        slider(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC), new OnChange() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                resetAutoClose();
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                } catch (Exception e) {
                    Log.w("CardsPlugin", "Volume failed", e);
                }
            }
        });

        // Flashlight: brightness levels when the phone supports them (Android 13+ and
        // a camera driver that reports them), otherwise a simple on/off slider.
        final int torchMax = QuickActions.torchMaxLevel(ctx);
        String torchNote;
        if (torchMax > 1) {
            torchNote = torchMax + " levels";
        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            torchNote = "On/off only. Brightness levels need Android 13+";
        } else {
            torchNote = "On/off only. This phone doesn't offer brightness levels to apps";
        }
        panel.addView(label("Flashlight"));
        panel.addView(note(torchNote));
        slider(torchMax, QuickActions.torchLevel(ctx), new OnChange() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (!fromUser) return;
                resetAutoClose();
                // Change brightness live while dragging, like the iPhone flashlight.
                if (torchMax > 1) QuickActions.setTorchLevel(ctx, value);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                QuickActions.setTorchLevel(ctx, sb.getProgress());
            }
        });
    }

    private void buildAppsCard() {
        String csv = ctx.sharedPreferences.getString("favorite_apps", "");
        ArrayList<String> pkgs = new ArrayList<>();
        if (csv != null) {
            for (String p : csv.split(",")) if (!p.trim().isEmpty()) pkgs.add(p.trim());
        }
        if (pkgs.isEmpty()) {
            TextView tv = label("No favorite apps yet. Choose them in OmniLand → Gestures and quick cards.");
            tv.setGravity(Gravity.CENTER);
            panel.addView(tv);
            return;
        }
        PackageManager pm = ctx.getPackageManager();
        LinearLayout rowView = null;
        int size = ctx.dpToInt(52);
        int perRow = 4;
        for (int i = 0; i < pkgs.size() && i < 8; i++) {
            if (i % perRow == 0) {
                rowView = new LinearLayout(ctx);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                rowView.setGravity(Gravity.CENTER);
                panel.addView(rowView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final String pkg = pkgs.get(i);
            Drawable icon;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                icon = pm.getApplicationIcon(info);
            } catch (Exception e) {
                continue;
            }
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            int m = ctx.dpToInt(8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(m, m, m, m);
            iv.setOnClickListener(v -> {
                QuickActions.launchApp(ctx, pkg);
                close();
            });
            rowView.addView(iv, lp);
        }
    }

    // ---------------------------------------------------------------- expand / collapse

    private int expandedHeight() {
        if (CARD_APPS.equals(card)) {
            String csv = ctx.sharedPreferences.getString("favorite_apps", "");
            int n = csv == null || csv.trim().isEmpty() ? 0 : csv.split(",").length;
            int rows = Math.max(1, (Math.min(n, 8) + 3) / 4);
            return ctx.statusBarHeight + ctx.dpToInt(30 + rows * 68);
        }
        return ctx.statusBarHeight + ctx.dpToInt(215);
    }

    private final CallBack startCallBack = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null) return;
            ViewGroup.LayoutParams lp = mView.getLayoutParams();
            if (expanded) {
                panel.setVisibility(View.VISIBLE);
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);
                if (lp != null) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    mView.setLayoutParams(lp);
                }
            } else {
                panel.setVisibility(View.GONE);
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
        if (expanded || mView == null) return;
        expanded = true;
        ctx.animateOverlay(expandedHeight(), ctx.metrics.widthPixels - ctx.dpToInt(15), true, startCallBack, endCallBack, false);
    }

    private void collapse() {
        if (!expanded || mView == null) return;
        expanded = false;
        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT, false, startCallBack, endCallBack, false);
    }

    @Override
    public void onCollapse() {
        close();
    }

    @Override
    public void onClick() {
    }

    @Override
    public void onSwipeUp() {
        close();
    }

    @Override
    public void onTextColorChange() {
        rebuildPanel();
    }

    @Override
    public String[] permissionsRequired() {
        return null;
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        return null;
    }
}
