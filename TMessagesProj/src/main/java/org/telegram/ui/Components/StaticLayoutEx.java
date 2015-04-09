/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;

public class StaticLayoutEx {

    private static final String TEXT_DIR_CLASS = "android.text.TextDirectionHeuristic";
    private static final String TEXT_DIRS_CLASS = "android.text.TextDirectionHeuristics";
    private static final String TEXT_DIR_FIRSTSTRONG_LTR = "FIRSTSTRONG_LTR";
    private static boolean initialized;

    private static Constructor<StaticLayout> sConstructor;
    private static Object[] sConstructorArgs;
    private static Object sTextDirection;

    public static void init() {
        if (initialized) {
            return;
        }

        try {
            final Class<?> textDirClass;
            if (Build.VERSION.SDK_INT >= 18) {
                textDirClass = TextDirectionHeuristic.class;
                sTextDirection = TextDirectionHeuristics.FIRSTSTRONG_LTR;
            } else {
                ClassLoader loader = StaticLayoutEx.class.getClassLoader();
                textDirClass = loader.loadClass(TEXT_DIR_CLASS);
                Class<?> textDirsClass = loader.loadClass(TEXT_DIRS_CLASS);
                sTextDirection = textDirsClass.getField(TEXT_DIR_FIRSTSTRONG_LTR).get(textDirsClass);
            }

            final Class<?>[] signature = new Class[]{
                    CharSequence.class,
                    int.class,
                    int.class,
                    TextPaint.class,
                    int.class,
                    Layout.Alignment.class,
                    textDirClass,
                    float.class,
                    float.class,
                    boolean.class,
                    TextUtils.TruncateAt.class,
                    int.class,
                    int.class
            };

            sConstructor = StaticLayout.class.getDeclaredConstructor(signature);
            sConstructor.setAccessible(true);
            sConstructorArgs = new Object[signature.length];
            initialized = true;
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public static StaticLayout createStaticLayout(CharSequence source, TextPaint paint, int width, Layout.Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines) {
        return createStaticLayout(source, 0, source.length(), paint, width, align, spacingmult, spacingadd, includepad, ellipsize, ellipsisWidth, maxLines);
    }

    public static StaticLayout createStaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerWidth, Layout.Alignment align, float spacingMult, float spacingAdd, boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsisWidth, int maxLines) {
        if (Build.VERSION.SDK_INT >= 14) {
            init();
            try {
                sConstructorArgs[0] = source;
                sConstructorArgs[1] = bufstart;
                sConstructorArgs[2] = bufend;
                sConstructorArgs[3] = paint;
                sConstructorArgs[4] = outerWidth;
                sConstructorArgs[5] = align;
                sConstructorArgs[6] = sTextDirection;
                sConstructorArgs[7] = spacingMult;
                sConstructorArgs[8] = spacingAdd;
                sConstructorArgs[9] = includePad;
                sConstructorArgs[10] = ellipsize;
                sConstructorArgs[11] = ellipsisWidth;
                sConstructorArgs[12] = maxLines;
                return sConstructor.newInstance(sConstructorArgs);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        try {
            if (maxLines == 1) {
                return new StaticLayout(source, bufstart, bufend, paint, outerWidth, align, spacingMult, spacingAdd, includePad, ellipsize, ellipsisWidth);
            } else {
                StaticLayout layout = new StaticLayout(source, paint, outerWidth, align, spacingMult, spacingAdd, includePad);
                if (layout.getLineCount() <= maxLines) {
                    return layout;
                } else {
                    int off = layout.getOffsetForHorizontal(maxLines - 1, layout.getLineWidth(maxLines - 1));
                    return new StaticLayout(source.subSequence(0, off), paint, outerWidth, align, spacingMult, spacingAdd, includePad);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }
}
