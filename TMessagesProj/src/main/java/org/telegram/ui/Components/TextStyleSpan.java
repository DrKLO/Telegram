/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static me.vkryl.core.BitwiseUtils.hasFlag;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class TextStyleSpan extends MetricAffectingSpan {

    private int textSize;
    private int color;
    private TextStyleRun style;

    public static class TextStyleRun {

        public int flags;
        public int start;
        public int end;
        public TLRPC.MessageEntity urlEntity;
        public String lng;
        public boolean header;

        public TextStyleRun() {}

        public TextStyleRun(TextStyleRun run) {
            flags = run.flags;
            start = run.start;
            end = run.end;
            urlEntity = run.urlEntity;
            header = run.header;
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
            if ((flags & FLAG_STYLE_STRIKE) != 0 || (flags & FLAG_STYLE_STRIKE_RED) != 0) {
                p.setFlags(p.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                p.setFlags(p.getFlags() &~ Paint.STRIKE_THRU_TEXT_FLAG);
            }

            if ((flags & FLAG_STYLE_SPOILER_REVEALED) != 0) {
                p.bgColor = Theme.getColor(Theme.key_chats_archivePullDownBackground);
            }
            if ((flags & FLAG_STYLE_STRIKE_RED) != 0) {
                p.setColor(Theme.getColor(Theme.key_text_RedBold));
            } else if ((flags & FLAG_STYLE_ACCENT) != 0) {
                p.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            }
        }

        public Typeface getTypeface() {
            if (header) {
                if ((flags & FLAG_STYLE_ITALIC) != 0) {
                    return AndroidUtilities.getTypeface("fonts/mw_bolditalic.ttf");
                }
                return AndroidUtilities.getTypeface("fonts/mw_bold.ttf");
            }
            if ((flags & FLAG_STYLE_MONO) != 0 || (flags & FLAG_STYLE_CODE) != 0) {
                return Typeface.MONOSPACE;
            } else if ((flags & FLAG_STYLE_BOLD) != 0 && (flags & FLAG_STYLE_ITALIC) != 0) {
                return AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC);
            } else if ((flags & FLAG_STYLE_BOLD) != 0) {
                return AndroidUtilities.bold();
            } else if ((flags & FLAG_STYLE_ITALIC) != 0) {
                return AndroidUtilities.getTypeface("fonts/ritalic.ttf");
            } else {
                return null;
            }
        }
    }

    public final static int FLAG_STYLE_BOLD        = 1 << 0;
    public final static int FLAG_STYLE_ITALIC      = 1 << 1;
    public final static int FLAG_STYLE_MONO        = 1 << 2;
    public final static int FLAG_STYLE_STRIKE      = 1 << 3;
    public final static int FLAG_STYLE_UNDERLINE   = 1 << 4;
    public final static int FLAG_STYLE_QUOTE       = 1 << 5;
    public final static int FLAG_STYLE_MENTION     = 1 << 6;
    public final static int FLAG_STYLE_URL         = 1 << 7;
    public final static int FLAG_STYLE_SPOILER     = 1 << 8;
    public final static int FLAG_STYLE_SPOILER_REVEALED = 1 << 9;
    public final static int FLAG_STYLE_TEXT_URL    = 1 << 10;
    public final static int FLAG_STYLE_CODE        = 1 << 11;
    public final static int FLAG_STYLE_ACCENT      = 1 << 12;
    public final static int FLAG_STYLE_STRIKE_RED  = 1 << 13;
    public final static int FLAG_STYLE_SUBSCRIPT   = 1 << 14;
    public final static int FLAG_STYLE_SUPERSCRIPT = 1 << 15;
    public final static int FLAG_STYLE_MARKED = 1 << 16;

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

    public boolean isSpoiler() {
        return (style.flags & FLAG_STYLE_SPOILER) > 0;
    }

    public boolean isSpoilerRevealed() {
        return (style.flags & FLAG_STYLE_SPOILER_REVEALED) > 0;
    }

    public void setSpoilerRevealed(boolean b) {
        if (b)
            style.flags |= FLAG_STYLE_SPOILER_REVEALED;
        else style.flags &= ~FLAG_STYLE_SPOILER_REVEALED;
    }

    public boolean isMono() {
        return style.getTypeface() == Typeface.MONOSPACE;
    }

    public boolean isBold() {
        return style.getTypeface() == AndroidUtilities.bold();
    }

    public boolean isItalic() {
        return style.getTypeface() == AndroidUtilities.getTypeface("fonts/ritalic.ttf");
    }

    public boolean isBoldItalic() {
        return style.getTypeface() == AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf");
    }

    private void applySubSuper(TextPaint p) {
        if (!hasFlag(style.flags, FLAG_STYLE_SUBSCRIPT | FLAG_STYLE_SUPERSCRIPT)) {
            return;
        }
        float base = p.getTextSize();
        p.setTextSize(base * 0.75f);
        if (hasFlag(style.flags, FLAG_STYLE_SUPERSCRIPT)) {
            p.baselineShift -= (int) (base * 0.35f);
        } else if (hasFlag(style.flags, FLAG_STYLE_SUBSCRIPT)) {
            p.baselineShift += (int) (base * 0.12f);
        }
    }

    @Override
    public void updateMeasureState(TextPaint p) {
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        applySubSuper(p);
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        style.applyStyle(p);
    }

    @Override
    public void updateDrawState(TextPaint p) {
        if (textSize != 0) {
            p.setTextSize(textSize);
        }
        applySubSuper(p);
        if (color != 0) {
            p.setColor(color);
        }
        p.setFlags(p.getFlags() | Paint.SUBPIXEL_TEXT_FLAG);
        style.applyStyle(p);
    }
}
