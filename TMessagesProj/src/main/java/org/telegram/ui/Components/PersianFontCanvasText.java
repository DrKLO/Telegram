package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;

import org.telegram.messenger.SharedConfig;

/** Draws short Canvas labels with the same script-specific spans as text views. */
public final class PersianFontCanvasText {
    private final LruCache<String, Entry> entries = new LruCache<>(32);
    private final TextPaint metrics = new TextPaint();
    private boolean initialized;
    private boolean enabled;

    private static final class Entry {
        float width;
        StaticLayout layout;
    }

    private Entry get(String text, Paint paint) {
        if (!initialized || enabled != SharedConfig.useVazirmatnFont || !sameMetrics(paint)) {
            entries.evictAll();
            metrics.set(paint);
            enabled = SharedConfig.useVazirmatnFont;
            initialized = true;
        }
        Entry entry = entries.get(text);
        if (entry == null) {
            entry = new Entry();
            TextPaint layoutPaint = new TextPaint(paint);
            layoutPaint.setTextAlign(Paint.Align.LEFT);
            CharSequence styled = PersianFontApplier.applyForPaint(text, layoutPaint);
            if (styled != text) {
                entry.width = Layout.getDesiredWidth(styled, layoutPaint);
                entry.layout = new StaticLayout(styled, layoutPaint,
                        Math.max(1, (int) Math.ceil(entry.width) + 1),
                        Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            } else {
                entry.width = paint.measureText(text);
            }
            entries.put(text, entry);
        }
        return entry;
    }

    private boolean sameMetrics(Paint paint) {
        return metrics.getTypeface() == paint.getTypeface()
                && metrics.getTextSize() == paint.getTextSize()
                && metrics.getTextScaleX() == paint.getTextScaleX()
                && metrics.getTextSkewX() == paint.getTextSkewX()
                && metrics.getFlags() == paint.getFlags()
                && metrics.getLetterSpacing() == paint.getLetterSpacing()
                && TextUtils.equals(metrics.getFontFeatureSettings(), paint.getFontFeatureSettings())
                && (Build.VERSION.SDK_INT < 26 || TextUtils.equals(metrics.getFontVariationSettings(), paint.getFontVariationSettings()))
                && (Build.VERSION.SDK_INT >= 24
                    ? metrics.getTextLocales().equals(paint.getTextLocales())
                    : metrics.getTextLocale().equals(paint.getTextLocale()));
    }

    public float measure(String text, Paint paint) {
        return text == null || text.isEmpty() ? 0 : get(text, paint).width;
    }

    public void draw(Canvas canvas, String text, float x, float baseline, Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Entry entry = get(text, paint);
        if (entry.layout == null) {
            canvas.drawText(text, x, baseline, paint);
            return;
        }
        // Preserve Canvas alignment and baseline without changing the caller's paint.
        float left = x;
        if (paint.getTextAlign() == Paint.Align.CENTER) {
            left -= entry.width / 2f;
        } else if (paint.getTextAlign() == Paint.Align.RIGHT) {
            left -= entry.width;
        }
        entry.layout.getPaint().set(paint);
        entry.layout.getPaint().setTextAlign(Paint.Align.LEFT);
        int save = canvas.save();
        canvas.translate(left - entry.layout.getLineLeft(0), baseline - entry.layout.getLineBaseline(0));
        entry.layout.draw(canvas);
        canvas.restoreToCount(save);
    }
}
