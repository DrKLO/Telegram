/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

public class TextStyleSpan extends MetricAffectingSpan {

    private int textSize;
    private int color;
    private TextStyleRun style;

    public static class TextStyleRun {

        public int flags;
        public int start;
        public int end;
        public TLRPC.MessageEntity urlEntity;

        public TextStyleRun() {

        }

        public TextStyleRun(TextStyleRun run) {
            flags = run.flags;
            start = run.start;
            end = run.end;
            urlEntity = run.urlEntity;
        }

        public void merge(TextStyleRun run) {
            flags |= run.flags;
            if (urlEntity == null && run.urlEntity != null) {
                urlEntity = run.urlEntity;
            }
        }

        public void replace(TextStyleRun run) {
            flags = run.flags;
            urlEntity = run.urlEntity;
        }

        public void applyStyle(TextPaint p) {
            Typeface typeface = getTypeface();
            if (typeface != null) {
                p.setTypeface(typeface);
            }
            if ((flags & FLAG_STYLE_UNDERLINE) != 0) {
                p.setFlags(p.getFlags() | Paint.UNDERLINE_TEXT_FLAG);
            } else {
                p.setFlags(p.getFlags() &~ Paint.UNDERLINE_TEXT_FLAG);
            }
            if ((flags & FLAG_STYLE_STRIKE) != 0) {
                p.setFlags(p.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                p.setFlags(p.getFlags() &~ Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        public Typeface getTypeface() {
            if ((flags & FLAG_STYLE_MONO) != 0 || (flags & FLAG_STYLE_QUOTE) != 0) {
                return Typeface.MONOSPACE;
            } else if ((flags & FLAG_STYLE_BOLD) != 0 && (flags & FLAG_STYLE_ITALIC) != 0) {
                return AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf");
            } else if ((flags & FLAG_STYLE_BOLD) != 0) {
                return AndroidUtilities.getTypeface("fonts/rmedium.ttf");
            } else if ((flags & FLAG_STYLE_ITALIC) != 0) {
                return AndroidUtilities.getTypeface("fonts/ritalic.ttf");
            } else {
                return null;
            }
        }
    }

    public final static int FLAG_STYLE_BOLD = 1;
    public final static int FLAG_STYLE_ITALIC = 2;
    public final static int FLAG_STYLE_MONO = 4;
    public final static int FLAG_STYLE_STRIKE = 8;
    public final static int FLAG_STYLE_UNDERLINE = 16;
    public final static int FLAG_STYLE_QUOTE = 32;
    public final static int FLAG_STYLE_MENTION = 64;
    public final static int FLAG_STYLE_URL = 128;

    public TextStyleSpan(TextStyleRun run) {
        this(run, 0, 0);
    }

    public TextStyleSpan(TextStyleRun run, int size) {
        this(run, size, 0);
    }

    public TextStyleSpan(TextStyleRun run, int size, int textColor) {
        style = run;
        if (size > 0) {
            textSize = size;
        }
        color = textColor;
    }

    public int getStyleFlags() {
        return style.flags;
    }

    public TextStyleRun getTextStyleRun() {
        return style;
    }

    public Typeface getTypeface() {
        return style.getTypeface();
    }

    public void setColor(int value) {
        color = value;
    }

    public boolean isMono() {
        return style.getTypeface() == Typeface.MONOSPACE;
    }

    public boolean isBold() {
        return style.getTypeface() == AndroidUtilities.getTypeface("fonts/rmedium.ttf");
    }

    public boolean isItalic() {
        return style.getTypeface() == AndroidUtilities.getTypeface("fonts/ritalic.ttf");
    }

    public boolean isBoldItalic() {
        return style.getTypeface() == AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf");
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        style.applyStyle(p);
    }

    @Override
    public void updateDrawState(TextPaint p) {
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        if (color != 0) {
            p.setColor(color);
        }
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        style.applyStyle(p);
    }
}
