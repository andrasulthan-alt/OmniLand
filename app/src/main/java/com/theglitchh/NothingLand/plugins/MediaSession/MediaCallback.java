package com.theglitchh.NothingLand.plugins.MediaSession;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import androidx.annotation.Nullable;

import com.theglitchh.NothingLand.utils.CallBack;

public class MediaCallback extends MediaController.Callback {
    public MediaCallback(MediaController mCurrent, MediaSessionPlugin context) {
        this.mCurrent = mCurrent;
        this.ctx = context;
        try {
            isPlaying = mCurrent.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
            mediaMetadata = mCurrent.getMetadata();
            updateView();
        } catch (Exception e) {
            // do nothing lol
        }
    }

    private final MediaSessionPlugin ctx;
    private final MediaController mCurrent;
    private MediaMetadata mediaMetadata;
    private boolean isPlaying = true;

    /** Stops listening to this player (the media plugin is being destroyed). */
    public void release() {
        try {
            mCurrent.unregisterCallback(this);
        } catch (Exception ignored) {
        }
    }

    private void updateView() {
        if (!isPlaying) return;
        if (mCurrent.getMetadata() == null) return;
        if (mediaMetadata == null) mediaMetadata = mCurrent.getMetadata();
        // Many players have no album art, or publish it under another key.
        Bitmap b = mediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = mediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = mediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        CharSequence titleText = mediaMetadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        String title = titleText == null ? "" : titleText.toString();
        String artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) artist = "";
        ctx.mCurrent = mCurrent;
        ctx.queueUpdate(new UpdateQueueStruct(artist, title, b));
        ctx.openOverlay(mCurrent.getPackageName());
        ctx.onPlayerResume(false);
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        super.onMetadataChanged(metadata);
        // Next track without a play/pause change: refresh title and cover.
        try {
            if (metadata == null || !isPlaying) return;
            if (ctx.mCurrent != null && !ctx.mCurrent.getPackageName().equals(mCurrent.getPackageName())) return;
            mediaMetadata = metadata;
            updateView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        super.onPlaybackStateChanged(state);
        try {
            if (state == null || mCurrent.getMetadata() == null) return;
            MediaMetadata targetMetada = mCurrent.getMetadata();
            boolean isPlaying2 = state.getState() == PlaybackState.STATE_PLAYING;
            if (mediaMetadata != null && mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) != null
                    && mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE).equals(targetMetada.getString(MediaMetadata.METADATA_KEY_TITLE)) && ctx.overlayOpen()) {
                if (ctx.mCurrent != null && ctx.mCurrent.getPackageName().equals(mCurrent.getPackageName())) {
                    if (!isPlaying2) ctx.onPlayerPaused(true);
                    else ctx.onPlayerResume(true);
                }
                isPlaying = isPlaying2;
                return;
            }

            isPlaying = isPlaying2;
            if (ctx.mCurrent != null && ctx.mCurrent.getPackageName().equals(mCurrent.getPackageName())) {
                if (!isPlaying) ctx.onPlayerPaused(false);
                else ctx.onPlayerResume(false);
            }
            if (!isPlaying) return;
            mediaMetadata = targetMetada;
            ctx.mCurrent = mCurrent;
            if (ctx.expanded) {
                updateView();
                return;
            }
            ctx.closeOverlay(new CallBack() {
                @Override
                public void onFinish() {
                    super.onFinish();
                    updateView();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            ctx.closeOverlay();
        }
    }

    @Override
    public void onSessionDestroyed() {
        super.onSessionDestroyed();
        if (mCurrent != null) {
            mCurrent.unregisterCallback(this);
            ctx.callbackMap.remove(mCurrent.getPackageName());
            if (ctx.mCurrent != null && ctx.mCurrent.getPackageName().equals(mCurrent.getPackageName())) {
                ctx.mCurrent = null;
            }
        }
        ctx.closeOverlay();

    }
}
