package com.theglitchh.NothingLand.plugins.MediaSession;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.theglitchh.NothingLand.utils.Broadcasts;
import com.theglitchh.NothingLand.utils.CallBack;
import com.theglitchh.NothingLand.R;
import com.theglitchh.NothingLand.plugins.BasePlugin;
import com.theglitchh.NothingLand.services.NotiService;
import com.theglitchh.NothingLand.services.OverlayService;
import com.theglitchh.NothingLand.utils.SettingStruct;
import com.google.android.material.imageview.ShapeableImageView;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MediaSessionPlugin extends BasePlugin {

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals(ctx.getPackageName() + ".COLOR_CHANGED")) {
                    // Handle color change, update UI or settings accordingly
                    int newColor = intent.getIntExtra("Allaccent_color", Color.RED);
                    // Example: update the visualizer's color
                    visualizer.setColor(newColor);
                }
            }
        }
        };

    private SeekBar seekBar;
    private TextView elapsedView;
    private TextView remainingView;
    public String current_package_name = "";
    public boolean expanded = false;
    public Map<String, MediaController.Callback> callbackMap = new HashMap<>();
    private boolean seekbar_dragging = false;
    public Instant last_played;
    OverlayService ctx;
    Handler mHandler;
    public MediaController mCurrent;

    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            if (!expanded || mView == null) return;
            if (mCurrent == null) {
                closeOverlay();
                return;
            }
            PlaybackState state = mCurrent.getPlaybackState();
            MediaMetadata metadata = mCurrent.getMetadata();
            if (state == null || metadata == null || state.getPosition() < 0) {
                closeOverlay();
                return;
            }
            long elapsed = state.getPosition();
            long total = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            elapsedView.setText(DurationFormatUtils.formatDuration(elapsed, "mm:ss", true));
            if (total > 0) {
                remainingView.setText("-" + DurationFormatUtils.formatDuration(Math.abs(total - elapsed), "mm:ss", true));
                if (!seekbar_dragging) seekBar.setProgress((int) ((((float) elapsed / total) * 100)));
            } else {
                remainingView.setText("");
            }
            // Twice a second is plenty for a mm:ss clock; posting with no delay kept
            // the main thread busy the whole time the player was open.
            mHandler.postDelayed(r, 500);
        }
    };


    private boolean overlayOpen = false;

    public boolean overlayOpen() {
        return overlayOpen;
    }

    public void closeOverlay() {
        if (mView == null) {
            overlayOpen = false;
            return;
        }
        animateChild(0, new CallBack());
        overlayOpen = false;
        shouldRemoveOverlay();
    }

    public void closeOverlay(CallBack callBack) {
        if (mView == null) {
            overlayOpen = false;
            callBack.onFinish();
            return;
        }
        animateChild(0, callBack);
        overlayOpen = false;
    }

    private ImageView pause_play;

    public MediaController getActiveCurrent(List<MediaController> mediaControllers) {
        if (mediaControllers.size() == 0) return null;
        try {
            Optional<MediaController> controller = mediaControllers.stream().filter(x -> x.getPlaybackState().getState() == PlaybackState.STATE_PLAYING).findFirst();
            return controller.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isColorDark(int color) {
        // Source : https://stackoverflow.com/a/24261119
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        // It's a dark color
        return !(darkness < 0.5); // It's a light color
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void onPlayerResume(boolean b) {
        if (expanded && b && pause_play != null) {
            pause_play.setImageDrawable(ctx.getDrawable(R.drawable.avd_play_to_pause));
            pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
            ((AnimatedVectorDrawable) pause_play.getDrawable()).start();
        }
        if (mCurrent == null || mediaSessionManager == null || visualizer == null) return;
        int index = -1;
        List<MediaController> controllerList = mediaSessionManager.getActiveSessions(new ComponentName(ctx.getBaseContext(), NotiService.class));
        for (int v = 0; v < controllerList.size(); v++) {
            if (Objects.equals(controllerList.get(v).getPackageName(), mCurrent.getPackageName())) {
                index = v;
                break;
            }

        }
        if (index == -1) return;
        visualizer.setPlayerId(index);
    }

    private int lightenColor(int colorin) {
        Color color = Color.valueOf(colorin);
        double fraction = 0.3;
        float red = (float) (Math.min(255, color.red() * 255f + 255 * fraction) / 225f);
        float green = (float) (Math.min(255, color.green() * 255f + 255 * fraction) / 225f);
        float blue = (float) (Math.min(255, color.blue() * 255f + 255 * fraction) / 225f);
        float alpha = color.alpha();

        return Color.valueOf(red, green, blue, alpha).toArgb();

    }

    private SongVisualizer visualizer;
    private MediaSessionManager mediaSessionManager;

    @SuppressLint("UseCompatLoadingForDrawables")
    public void onPlayerPaused(boolean b) {
        if (expanded && b && pause_play != null) {
            pause_play.setImageDrawable(ctx.getDrawable(R.drawable.avd_pause_to_play));
            pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
            ((AnimatedVectorDrawable) pause_play.getDrawable()).start();
        }
        last_played = Instant.now();
        mHandler.postDelayed(() -> {
            if (mediaSessionManager == null || last_played == null) return;
            if (Math.abs(Instant.now().toEpochMilli() - last_played.toEpochMilli()) >= 60 * 1000) {
                try {
                    if (getActiveCurrent(mediaSessionManager.getActiveSessions(new ComponentName(ctx, NotiService.class))) == null)
                        closeOverlay();
                } catch (Exception ignored) {
                }
            }
        }, 60 * 1000);
    }

    public static int getDominantColor(Bitmap bitmap, Context ctx) {
        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        final int color = newBitmap.getPixel(0, 0);
        newBitmap.recycle();

        return 0;



    }

    @Override
    public String getID() {
        return "MediaSessionPlugin";
    }

    private MediaSessionManager.OnActiveSessionsChangedListener listnerForActiveSessions = list -> {
        list.forEach(x -> {
            if (callbackMap.get(x.getPackageName()) != null) return;
            MediaCallback c = new MediaCallback(x, this);
            callbackMap.put(x.getPackageName(), c);
            x.registerCallback(c);
        });
    };
    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        mHandler = new Handler(context.getMainLooper());

        mView = LayoutInflater.from(context).inflate(R.layout.media_session_layout, null);
        mView.findViewById(R.id.blank_space).setVisibility(View.VISIBLE);
        init();

        // The visualizer follows the accent colour; register once here (it used to be
        // registered again on every track, and never removed).
        Broadcasts.register(ctx, receiver, new IntentFilter(ctx.getPackageName() + ".COLOR_CHANGED"));

        mediaSessionManager = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            ComponentName listener = new ComponentName(ctx, NotiService.class);
            mediaSessionManager.addOnActiveSessionsChangedListener(listnerForActiveSessions, listener);
            mediaSessionManager.getActiveSessions(listener).forEach(x -> {
                if (callbackMap.get(x.getPackageName()) != null) return;
                MediaCallback c = new MediaCallback(x, this);
                callbackMap.put(x.getPackageName(), c);
                x.registerCallback(c);
            });
        } catch (SecurityException e) {
            // Notification access isn't granted (yet). The island rebuilds itself when
            // NotiService connects, and this runs again then.
            Log.w("MediaSessionPlugin", "No notification access yet", e);
        }
    }


    public void shouldRemoveOverlay() {
        if (mediaSessionManager == null) {
            ctx.dequeue(this);
            return;
        }
        try {
            if (getActiveCurrent(mediaSessionManager.getActiveSessions(new ComponentName(ctx, NotiService.class))) == null) {
                ctx.dequeue(this);
            }
        } catch (Exception e) {
            ctx.dequeue(this);
        }
    }

    private void layoutHandle(View v, int width) {
        int width1 = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        v.measure(width1, height);
        v.getLayoutParams().width = v.getMeasuredWidth();
        v.getLayoutParams().height = v.getMeasuredHeight();
        v.setLayoutParams(v.getLayoutParams());
    }

    public void openOverlay(String pkg_name) {
        if (overlayOpen) return;
        overlayOpen = true;
        current_package_name = pkg_name;
        animateChild(ctx.dpToInt(ctx.minHeight / 4), new CallBack());
    }

    private View mView;
    //int redTintColor= Color.parseColor("#80FF0000");
    private ShapeableImageView cover;
    ImageView back;
    ImageView next;

    private void init() {
        seekBar = mView.findViewById(R.id.progressBar);
        elapsedView = mView.findViewById(R.id.elapsed);
        remainingView = mView.findViewById(R.id.remaining);
        pause_play = mView.findViewById(R.id.pause_play);
        next = mView.findViewById(R.id.next_play);
        back = mView.findViewById(R.id.back_play);
        cover = mView.findViewById(R.id.cover);
        coverHolder = mView.findViewById(R.id.relativeLayout);
        text_info = mView.findViewById(R.id.text_info);
        controls_holder = mView.findViewById(R.id.controls_holder);

        pause_play.setOnClickListener(l -> {
            if (mCurrent == null) return;
            PlaybackState state = mCurrent.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                mCurrent.getTransportControls().play();

            } else {
                mCurrent.getTransportControls().pause();

            }
        });
        TextView titleView = mView.findViewById(R.id.title);
        TextView artistView = mView.findViewById(R.id.artist_subtitle);
        elapsedView.setTextColor(ctx.textColor);
        remainingView.setTextColor(ctx.textColor);
        titleView.setTextColor(ctx.textColor);
        artistView.setTextColor(ctx.textColor);
        back.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        next.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        seekBar.getProgressDrawable().setColorFilter(ctx.textColor, PorterDuff.Mode.SRC_ATOP);

        next.setOnClickListener(l -> {
            if (mCurrent == null) return;
            mCurrent.getTransportControls().skipToNext();
        });
        back.setOnClickListener(l -> {
            if (mCurrent == null) return;
            mCurrent.getTransportControls().skipToPrevious();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekbar_dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekbar_dragging = false;
                if (mCurrent == null || mCurrent.getMetadata() == null) return;
                long total = mCurrent.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (total <= 0) return;
                mCurrent.getTransportControls().seekTo((long) ((float) seekBar.getProgress() / 100 * total));
            }
        });
        visualizer = mView.findViewById(R.id.visualizer);
    }

    @Override
    public View onBind() {
        bound = true;
        applyAlbumColor();
        return mView;
    }

    @Override
    public void onUnbind() {
        mHandler.removeCallbacks(r);
        bound = false;
        ctx.clearIslandColorOverride();
        if (expanded && mView != null) {
            // Something else took the island while the player was open: put the
            // player back in its small state so it works again when it returns.
            expanded = false;
            try {
                OverLayCallBackStart.onFinish();
                overLayCallBackEnd.onFinish();
                int size = ctx.dpToInt(ctx.minHeight / 4);
                for (View v : new View[]{cover, visualizer}) {
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    lp.width = size;
                    lp.height = size;
                    v.setLayoutParams(lp);
                }
                visualizer.setVisibility(View.VISIBLE);
                visualizer.paused = false;
            } catch (Exception e) {
                Log.w("MediaSessionPlugin", "Could not reset the player view", e);
            }
        }
    }

    // ------------------------------------------------------------ album colour

    private boolean bound = false;
    private Bitmap lastCover;
    private Integer albumColor;

    /** Colours the island from the current album art (setting "Color island from album art"). */
    private void applyAlbumColor() {
        if (!bound) return;
        if (albumColor == null || !ctx.sharedPreferences.getBoolean("album_color", true)) {
            ctx.clearIslandColorOverride();
            return;
        }
        ctx.setIslandColorOverride(albumColor);
    }

    /**
     * Picks a rich, dark colour from the album art: the average of its most colourful
     * pixels, darkened so white text and controls stay readable on top.
     */
    static Integer islandColorFrom(Bitmap art) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(art, 24, 24, true);
            float[] hsv = new float[3];
            long r = 0, g = 0, b = 0, rAll = 0, gAll = 0, bAll = 0;
            int n = 0, nAll = 0;
            for (int yy = 0; yy < small.getHeight(); yy++) {
                for (int xx = 0; xx < small.getWidth(); xx++) {
                    int px = small.getPixel(xx, yy);
                    rAll += Color.red(px);
                    gAll += Color.green(px);
                    bAll += Color.blue(px);
                    nAll++;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] > 0.3f && hsv[2] > 0.25f) {
                        r += Color.red(px);
                        g += Color.green(px);
                        b += Color.blue(px);
                        n++;
                    }
                }
            }
            if (small != art) small.recycle();
            if (nAll == 0) return null;
            int avg = n >= 8
                    ? Color.rgb((int) (r / n), (int) (g / n), (int) (b / n))
                    : Color.rgb((int) (rAll / nAll), (int) (gAll / nAll), (int) (bAll / nAll));
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.min(hsv[1], 0.85f);
            hsv[2] = Math.max(0.18f, Math.min(hsv[2], 0.40f));
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Drawable getMiniIcon() {
        if (!overlayOpen || lastCover == null) return null;
        try {
            return new BitmapDrawable(ctx.getResources(), lastCover);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
        if (visualizer != null) {
            visualizer.release();
            visualizer.unregister();
        }
        Broadcasts.unregister(ctx, receiver);
        if (mediaSessionManager != null)
            mediaSessionManager.removeOnActiveSessionsChangedListener(listnerForActiveSessions);
        // Stop listening to the players, otherwise a destroyed plugin keeps reacting
        // to play/pause, and after a rebuild the music never shows up again.
        for (MediaController.Callback c : callbackMap.values()) {
            if (c instanceof MediaCallback) ((MediaCallback) c).release();
        }
        callbackMap.clear();
        mediaSessionManager = null;
        mCurrent = null;
        mView = null;
        expanded = false;
        overlayOpen = false;
        bound = false;
    }

    @Override
    public void onTextColorChange() {
        if (mView == null) return;
        TextView titleView = mView.findViewById(R.id.title);
        TextView artistView = mView.findViewById(R.id.artist_subtitle);
        elapsedView.setTextColor(ctx.textColor);
        remainingView.setTextColor(ctx.textColor);
        titleView.setTextColor(ctx.textColor);
        artistView.setTextColor(ctx.textColor);
        back.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        next.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
        seekBar.getProgressDrawable().setColorFilter(ctx.textColor, PorterDuff.Mode.SRC_ATOP);
    }

    RelativeLayout coverHolder;
    private final CallBack onChange = new CallBack() {
        @Override
        public void onChange(float p) {
            if (mView == null) return;
            float f;
            if (expanded) {
                f = p;
            } else {
                f = 1 - p;
            }
            mView.setPadding(0, (int) (f * ctx.statusBarHeight), 0, 0);
            ((RelativeLayout.LayoutParams) coverHolder.getLayoutParams()).leftMargin = (int) (f * ctx.dpToInt(20));
        }
    };

    @Override
    public void onExpand() {
        if (expanded) return;
        expanded = true;
        DisplayMetrics metrics = ctx.metrics;
        ctx.animateOverlay(ctx.dpToInt(210), metrics.widthPixels - ctx.dpToInt(15), expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);
        animateChild(true, ctx.dpToInt(76));

    }

    LinearLayout text_info;
    LinearLayout controls_holder;
    CallBack OverLayCallBackStart = new CallBack() {
        @Override
        public void onFinish() {
            super.onFinish();
            if (mView == null) return;
            if (expanded) {
                mView.findViewById(R.id.blank_space).setVisibility(View.GONE);
                ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
                layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                text_info.setVisibility(View.VISIBLE);
                controls_holder.setVisibility(View.VISIBLE);
                seekBar.setVisibility(View.VISIBLE);
                mView.findViewById(R.id.title).setSelected(true);
                mView.findViewById(R.id.artist_subtitle).setSelected(true);
                elapsedView.setVisibility(View.VISIBLE);
                remainingView.setVisibility(View.VISIBLE);
                ((RelativeLayout.LayoutParams) coverHolder.getLayoutParams()).removeRule(RelativeLayout.CENTER_VERTICAL);
                coverHolder.setLayoutParams(coverHolder.getLayoutParams());
            } else {
                mHandler.removeCallbacks(r);
                text_info.setVisibility(View.GONE);
                controls_holder.setVisibility(View.GONE);
                seekBar.setVisibility(View.GONE);
                mView.findViewById(R.id.blank_space).setVisibility(View.VISIBLE);
                elapsedView.setVisibility(View.GONE);
                remainingView.setVisibility(View.GONE);

                ((RelativeLayout.LayoutParams) coverHolder.getLayoutParams()).addRule(RelativeLayout.CENTER_VERTICAL);
                coverHolder.setLayoutParams(coverHolder.getLayoutParams());

            }
        }
    };
    CallBack overLayCallBackEnd = new CallBack() {
        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        public void onFinish() {
            super.onFinish();
            if (mView == null) return;
            if (expanded) {
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);
                ((RelativeLayout.LayoutParams) coverHolder.getLayoutParams()).leftMargin = ctx.dpToInt(20);
                if (mCurrent != null && mCurrent.getPlaybackState() != null) {
                    if (mCurrent.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                        pause_play.setImageDrawable(ctx.getDrawable(R.drawable.pause));
                        pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
                    } else {
                        pause_play.setImageDrawable(ctx.getDrawable(R.drawable.play));
                        pause_play.setImageTintList(ColorStateList.valueOf(ctx.textColor));
                    }
                }
                mHandler.post(r);
                text_info.setAlpha(1);
                controls_holder.setAlpha(1);
                seekBar.setAlpha(1);
                elapsedView.setAlpha(1);
                remainingView.setAlpha(1);
            } else {
                ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
                layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mView.setLayoutParams(layoutParams);
                mView.setPadding(0, 0, 0, 0);
                ((RelativeLayout.LayoutParams) coverHolder.getLayoutParams()).leftMargin = 0;
            }
        }
    };

    @Override
    public void onCollapse() {
        if (!expanded) return;
        expanded = false;
        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT, expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);
        animateChild(false, ctx.dpToInt(ctx.minHeight / 4));
    }

    @Override
    public void onClick() {
        if (expanded && !openOnTouchWhenExpanded()) return;
        openMusicApp();
    }

    /**
     * The island runs in its own process, so its settings bundle is the source of
     * truth (it is kept in sync by the SETTINGS_CHANGED broadcast). Stored
     * preferences are only a fallback for the very first run.
     */
    private boolean openOnTouchWhenExpanded() {
        if (ctx == null) return false;
        if (ctx.sharedPreferences.containsKey("ms_enable_touch_expanded")) {
            return ctx.sharedPreferences.getBoolean("ms_enable_touch_expanded", false);
        }
        return ctx.getSharedPreferences(ctx.getPackageName(), Context.MODE_PRIVATE)
                .getBoolean("ms_enable_touch_expanded", false);
    }

    /**
     * Opens the music app. Most players do not publish a session activity, so the
     * old code silently did nothing for them; fall back to their launcher entry.
     */
    private void openMusicApp() {
        if (ctx == null) return;
        String pkg = mCurrent != null ? mCurrent.getPackageName() : current_package_name;
        if (mCurrent != null) {
            try {
                PendingIntent session = mCurrent.getSessionActivity();
                if (session != null) {
                    session.send();
                    afterOpen();
                    return;
                }
            } catch (Exception e) {
                Log.w("MediaSessionPlugin", "Session activity could not be started", e);
            }
        }
        if (pkg != null && !pkg.isEmpty()) {
            try {
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    ctx.startActivity(launch);
                    afterOpen();
                    return;
                }
            } catch (Exception e) {
                Log.w("MediaSessionPlugin", "Could not launch " + pkg, e);
            }
        }
        Toast.makeText(ctx, "Couldn't open the music app", Toast.LENGTH_SHORT).show();
    }

    /** Tuck the island away so the app it just opened is not covered. */
    private void afterOpen() {
        if (expanded) onCollapse();
    }

    @Override
    public String[] permissionsRequired() {
        return null;
    }

    @Override
    public String getName() {
        return "Media Session";
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        ArrayList<SettingStruct> s = new ArrayList<>();
        s.add(new SettingStruct("Open music app on touch when expanded", "Media Session", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return ctx.getSharedPreferences(ctx.getPackageName(), Context.MODE_PRIVATE).getBoolean("ms_enable_touch_expanded", false);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                ctx.getSharedPreferences(ctx.getPackageName(), Context.MODE_PRIVATE).edit().putBoolean("ms_enable_touch_expanded", checked).apply();
                if (MediaSessionPlugin.this.ctx != null) {
                    MediaSessionPlugin.this.ctx.sharedPreferences.putBoolean("ms_enable_touch_expanded", checked);
                }
            }
        });
        return s;
    }


    public void queueUpdate(UpdateQueueStruct queueStruct) {
        if (mView == null) return; // plugin was switched off
        ctx.enqueue(this);
        TextView titleView = mView.findViewById(R.id.title);
        TextView artistView = mView.findViewById(R.id.artist_subtitle);
        ShapeableImageView imageView = cover;
        titleView.setText(queueStruct.getTitle());
        artistView.setText(queueStruct.getArtist());
        if (queueStruct.getCover() != null) {
            imageView.setImageBitmap(queueStruct.getCover());
        } else {
            // Players without album art: show the app's icon instead of nothing.
            Drawable appIcon = null;
            try {
                if (mCurrent != null) appIcon = ctx.getPackageManager().getApplicationIcon(mCurrent.getPackageName());
            } catch (Exception ignored) {
            }
            imageView.setImageDrawable(appIcon);
        }
        if (queueStruct.getCover() != lastCover) {
            lastCover = queueStruct.getCover();
            albumColor = lastCover != null ? islandColorFrom(lastCover) : null;
            applyAlbumColor();
        }
        ctx.refreshBubble();
        //imageView.setColorFilter(redTintColor, PorterDuff.Mode.SRC_ATOP); will use it in future for tinting

    }


    private void animateChild(boolean expanding, int h) {
        if (mView == null) return;
        View view1 = cover;
        View view2 = visualizer;

        ValueAnimator height_anim = ValueAnimator.ofInt(view1.getHeight(), h);
        height_anim.setDuration(500);
        height_anim.addUpdateListener(valueAnimator -> {
            ViewGroup.LayoutParams params1 = view1.getLayoutParams();
            ViewGroup.LayoutParams params2 = view2.getLayoutParams();
            params1.height = (int) valueAnimator.getAnimatedValue();
            params2.height = (int) valueAnimator.getAnimatedValue();
            params1.width = (int) valueAnimator.getAnimatedValue();
            params2.width = (int) valueAnimator.getAnimatedValue();
            view1.setLayoutParams(params1);
            view2.setLayoutParams(params2);
        });
        height_anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (!expanding) {
                    view2.setVisibility(View.VISIBLE);
                    visualizer.paused = false;
                }
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (expanding) {
                    view2.setVisibility(View.GONE);
                    visualizer.paused = true;
                }
            }
        });
        height_anim.setInterpolator(new OvershootInterpolator(0.5f));
        height_anim.start();


    }

    private void animateChild(int h, CallBack callback) {
        View view1 = cover;
        View view2 = visualizer;
        if (h != 0) {
            ViewGroup.LayoutParams params1 = view1.getLayoutParams();
            ViewGroup.LayoutParams params2 = view2.getLayoutParams();
            params1.height = h;
            params2.height = h;
            params1.width = h;
            params2.width = h;
            view1.setScaleY(0);
            view1.setScaleX(0);
            view2.setScaleX(0);
            view2.setScaleY(0);
            view1.setLayoutParams(params1);
            view2.setLayoutParams(params2);
        }
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(h != 0 ? 0 : 1, h != 0 ? 1 : 0);
        valueAnimator.addUpdateListener(l -> {
            float f = (float) l.getAnimatedValue();
            view1.setScaleX(f);
            view1.setScaleY(f);
            view2.setScaleX(f);
            view2.setScaleY(f);
        });
        valueAnimator.setDuration(500);
        valueAnimator.start();
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                callback.onFinish();
            }
        });


    }

}
