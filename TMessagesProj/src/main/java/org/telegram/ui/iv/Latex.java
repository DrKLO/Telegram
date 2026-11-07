package org.telegram.ui.iv;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import ru.noties.jlatexmath.JLatexMathAndroid;
import ru.noties.jlatexmath.JLatexMathDrawable;

public final class Latex {

    public final Bitmap bitmap;
    public final int width;
    public final int height;
    public final int depth;

    private Latex(Bitmap bm, int w, int h, int d) {
        bitmap = bm;
        width = w;
        height = h;
        depth = d;
    }

    private static volatile boolean sInitialized = false;

    private static void ensureInitialized() {
        if (!sInitialized) {
            synchronized (Latex.class) {
                if (!sInitialized) {
                    JLatexMathAndroid.init(ApplicationLoader.applicationContext);
                    sInitialized = true;
                }
            }
        }
    }

    @Nullable
    public static Latex render(@NonNull String source, float textSizePx) {
        return render(source, textSizePx, false);
    }
    @Nullable
    public static Latex render(@NonNull String source, float textSizePx, boolean needDepth) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        try {
            ensureInitialized();
            final JLatexMathDrawable drawable =
                JLatexMathDrawable.builder(source)
                    .textSize(textSizePx)
                    .build();
            final int w = drawable.getIntrinsicWidth();
            final int h = drawable.getIntrinsicHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(new Canvas(bm));
            int depth = 0;
            if (needDepth) {
                try {
                    depth = drawable.icon().getIconDepth();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            return new Latex(bm, w, h, depth);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
