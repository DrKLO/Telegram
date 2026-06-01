/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;

public class StaticLayoutEx {

    public static Layout.Alignment[] alignments = Layout.Alignment.values();
    public static Layout.Alignment ALIGN_RIGHT() {
        return alignments.length >= 5 ? alignments[4] : Layout.Alignment.ALIGN_OPPOSITE;
    }
    public static Layout.Alignment ALIGN_LEFT() {
        return alignments.length >= 5 ? alignments[3] : Layout.Alignment.ALIGN_NORMAL;
    }

    public static void init() {

    }

    public static StaticLayout createStaticLayout2(CharSequence source, TextPaint paint, int width, Layout.Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines) {
        if (Build.VERSION.SDK_INT >= 23) {
            StaticLayout.Builder builder = StaticLayout.Builder.obtain(source, 0, source.length(), paint, ellipsisWidth)
                    .setAlignment(align)
                    .setLineSpacing(spacingadd, spacingmult)
                    .setIncludePad(includepad)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setEllipsizedWidth(ellipsisWidth)
                    .setMaxLines(maxLines)
                    .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
            return builder.build();
        } else {
            return createStaticLayout(source, paint, width, align, spacingmult, spacingadd, includepad, ellipsize, ellipsisWidth, maxLines, true);
        }
    }

    public static StaticLayout createStaticLayout(CharSequence source, TextPaint paint, int width, Layout.Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines) {
        return createStaticLayout(source, paint, width, align, spacingmult, spacingadd, includepad, ellipsize, ellipsisWidth, maxLines, true);
    }

    public static StaticLayout createStaticLayout(CharSequence source, TextPaint paint, int outerWidth, Layout.Alignment align, float spacingMult, float spacingAdd, boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines, boolean canContainUrl) {
        try {
            if (maxLines == 1) {
                int index = TextUtils.indexOf(source, "\n") - 1;
                if (index > 0) {
                    source = SpannableStringBuilder.valueOf(source.subSequence(0, index)).append("…");
                }
                CharSequence text = TextUtils.ellipsize(source, paint, ellipsisWidth, TextUtils.TruncateAt.END);
                return new StaticLayout(text, 0, text.length(), paint, outerWidth, align, spacingMult, spacingAdd, includePad);
            } else {
                StaticLayout layout;
                if (Build.VERSION.SDK_INT >= 23) {
                    StaticLayout.Builder builder = StaticLayout.Builder.obtain(source, 0, source.length(), paint, outerWidth)
                            .setAlignment(align)
                            .setLineSpacing(spacingAdd, spacingMult)
                            .setIncludePad(includePad)
                            .setEllipsize(null)
                            .setEllipsizedWidth(ellipsisWidth)
                            .setMaxLines(maxLines)
                            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
                    layout = builder.build();

                    boolean realWidthLarger = false;
                    for (int l = 0; l < layout.getLineCount(); ++l) {
                        if (layout.getLineRight(l) > outerWidth) {
                            realWidthLarger = true;
                            break;
                        }
                    }
                    if (realWidthLarger) {
                        builder = StaticLayout.Builder.obtain(source, 0, source.length(), paint, outerWidth)
                            .setAlignment(align)
                            .setLineSpacing(spacingAdd, spacingMult)
                            .setIncludePad(includePad)
                            .setEllipsize(null)
                            .setEllipsizedWidth(ellipsisWidth)
                            .setMaxLines(maxLines)
                            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE)
                            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
                        layout = builder.build();
                    }
                } else {
                    layout = new StaticLayout(source, paint, outerWidth, align, spacingMult, spacingAdd, includePad);
                }
                if (layout.getLineCount() <= maxLines) {
                    return layout;
                } else {
                    int off;
                    int start;
                    float left = layout.getLineLeft(maxLines - 1);
                    float lineWidth = layout.getLineWidth(maxLines - 1);
                    if (left != 0) {
                        off = layout.getOffsetForHorizontal(maxLines - 1, left);
                    } else {
                        off = layout.getOffsetForHorizontal(maxLines - 1, lineWidth);
                    }
                    if (lineWidth < ellipsisWidth - AndroidUtilities.dp(10)) {
                        off += 3;
                    }
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(source.subSequence(0, Math.max(0, off - 3)));
                    stringBuilder.append("\u2026");
                    if (Build.VERSION.SDK_INT >= 23) {
                        StaticLayout.Builder builder = StaticLayout.Builder.obtain(stringBuilder, 0, stringBuilder.length(), paint, outerWidth)
                                .setAlignment(align)
                                .setLineSpacing(spacingAdd, spacingMult)
                                .setIncludePad(includePad)
                                .setEllipsize(stringBuilder.getSpans(0, stringBuilder.length(), AnimatedEmojiSpan.class).length > 0 ? null : ellipsize)
                                .setEllipsizedWidth(ellipsisWidth)
                                .setMaxLines(maxLines)
                                .setBreakStrategy(canContainUrl ? StaticLayout.BREAK_STRATEGY_HIGH_QUALITY : StaticLayout.BREAK_STRATEGY_SIMPLE)
                                .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE);
                        return builder.build();
                    } else {
                        return new StaticLayout(stringBuilder, paint, outerWidth, align, spacingMult, spacingAdd, includePad);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }
}
