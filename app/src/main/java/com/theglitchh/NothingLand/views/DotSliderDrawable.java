package com.theglitchh.NothingLand.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Nothing-style slider track: a row of dots. Dots up to the current value are
 * solid, the rest are faint, and the current value is one larger red dot.
 * Used as a SeekBar progress drawable; the SeekBar sets the value via setLevel().
 */
public class DotSliderDrawable extends Drawable {
    public static final int NOTHING_RED = 0xFFD71921;

    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final float gap;

    public DotSliderDrawable(int color, int accentColor, float density) {
        on.setColor(color);
        off.setColor(color);
        off.setAlpha(60);
        accent.setColor(accentColor);
        radius = 2.5f * density;
        gap = 4f * density;
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0) return;
        float step = radius * 2 + gap;
        int count = Math.max(2, (int) ((b.width() + gap) / step));
        float total = count * step - gap;
        float x0 = b.left + (b.width() - total) / 2f + radius;
        float cy = b.exactCenterY();
        int active = Math.round(getLevel() / 10000f * (count - 1));
        for (int i = 0; i < count; i++) {
            float cx = x0 + i * step;
            if (i == active) {
                canvas.drawCircle(cx, cy, radius * 1.7f, accent);
            } else {
                canvas.drawCircle(cx, cy, radius, i < active ? on : off);
            }
        }
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) Math.ceil(radius * 4);
    }

    @Override
    public void setAlpha(int alpha) {
        on.setAlpha(alpha);
        accent.setAlpha(alpha);
        off.setAlpha(Math.min(alpha, 60));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        on.setColorFilter(colorFilter);
        off.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
