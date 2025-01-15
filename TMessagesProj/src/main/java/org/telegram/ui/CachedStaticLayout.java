package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;

public class CachedStaticLayout {

    public final StaticLayout layout;
    private boolean disabled;

    public CachedStaticLayout(StaticLayout source) {
        this.layout = source;
    }

    public CachedStaticLayout disableCache() {
        disabled = true;
        return this;
    }

    private RenderNode renderNode;

    public void draw(Canvas canvas) {
        if (!disabled && canvas.isHardwareAccelerated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canvas.quickReject(0, 0, layout.getWidth(), layout.getHeight())) {
                return;
            }
            if (hasChanges() || renderNode == null || !renderNode.hasDisplayList()) {
                if (renderNode == null) {
                    renderNode = new RenderNode("CachedStaticLayout");
                    renderNode.setClipToBounds(false);
                }
                renderNode.setPosition(getLayoutBounds());
                Canvas cacheCanvas = renderNode.beginRecording();
                int color = layout.getPaint().getColor();
                layout.getPaint().setColor(ColorUtils.setAlphaComponent(color, 0xFF));
                layout.draw(cacheCanvas);
                layout.getPaint().setColor(color);
                renderNode.endRecording();
            }

            renderNode.setAlpha(layout.getPaint().getAlpha() / 255.0f);
            canvas.drawRenderNode(renderNode);
            return;
        }

        layout.draw(canvas);
    }

    private int textColor;
    private int linkColor;
    private Typeface typeface;
    private float textSize;
    private final Rect lastLayoutBounds = new Rect();
    private boolean[] lastEmojiLoaded;
    private boolean[] tempEmojiLoaded;

    private boolean[] getEmojiLoaded() {
        if (!(getText() instanceof Spanned)) {
            return null;
        }
        Emoji.EmojiSpan[] spans = ((Spanned) getText()).getSpans(0, getText().length(), Emoji.EmojiSpan.class);
        if (spans == null || spans.length <= 0)
            return null;
        if (tempEmojiLoaded == null || tempEmojiLoaded.length != spans.length) {
            tempEmojiLoaded = new boolean[spans.length];
        }
        for (int i = 0; i < spans.length; ++i) {
            tempEmojiLoaded[i] = spans[i].getDrawable() instanceof Emoji.EmojiDrawable && ((Emoji.EmojiDrawable) spans[i].getDrawable()).isLoaded();
        }
        return tempEmojiLoaded;
    }

    private boolean emojiLoadedEquals(boolean[] a, boolean[] b) {
        if (a == null && b == null) {
            return true;
        }
        if ((a == null ? 0 : a.length) != (b == null ? 0 : b.length)) {
            return false;
        }
        int n = a == null ? 0 : a.length;
        for (int i = 0; i < n; ++i) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private boolean hasChanges() {
        boolean a = false, b = false, c = false, d = false, e = false, f = false;
        boolean[] emojiLoaded = null;
        if (
            (a = (ColorUtils.setAlphaComponent(textColor, 0xFF) != ColorUtils.setAlphaComponent(layout.getPaint().getColor(), 0xFF))) ||
            (b = (ColorUtils.setAlphaComponent(linkColor, 0xFF) != ColorUtils.setAlphaComponent(layout.getPaint().linkColor, 0xFF))) ||
            (c = (Math.abs(textSize - layout.getPaint().getTextSize()) > 0.1f)) ||
            (d = (typeface != layout.getPaint().getTypeface())) ||
            (e = (!lastLayoutBounds.equals(getLayoutBounds()))) ||
            (f = (!emojiLoadedEquals(emojiLoaded = getEmojiLoaded(), lastEmojiLoaded)))
        ) {
            textColor = layout.getPaint().getColor();
            linkColor = layout.getPaint().linkColor;
            textSize = layout.getPaint().getTextSize();
            typeface = layout.getPaint().getTypeface();
            lastLayoutBounds.set(getLayoutBounds());
            if (emojiLoaded != null) {
                lastEmojiLoaded = emojiLoaded.clone();
            }
            return true;
        }
        return false;
    }


    private final Rect bounds = new Rect();

    private Rect getLayoutBounds() {
        bounds.set(0, 0, layout.getWidth(), layout.getHeight());
        return bounds;
    }

    public CharSequence getText() {
        return layout.getText();
    }

    public TextPaint getPaint() {
        return layout.getPaint();
    }

    public int getWidth() {
        return layout.getWidth();
    }

    public int getHeight() {
        return layout.getHeight();
    }

    public int getLineCount() {
        return layout.getLineCount();
    }

    public int getLineTop(int line) {
        return layout.getLineTop(line);
    }

    public int getLineBottom(int line) {
        return layout.getLineBottom(line);
    }

    public float getLineLeft(int line) {
        return layout.getLineLeft(line);
    }

    public float getLineRight(int line) {
        return layout.getLineRight(line);
    }

    public float getLineWidth(int line) {
        return layout.getLineWidth(line);
    }

    public float getPrimaryHorizontal(int x) {
        return layout.getPrimaryHorizontal(x);
    }

    public int getLineEnd(int line) {
        return layout.getLineEnd(line);
    }

    public int getLineStart(int line) {
        return layout.getLineStart(line);
    }

}