package org.telegram.messenger.utils;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.RequiresApi;



import org.telegram.messenger.Utilities;

import java.util.function.Supplier;

public class ViewOutlineProviderImpl {
    public static final ViewOutlineProvider BOUNDS_OVAL = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    public static final ViewOutlineProvider BOUNDS_ROUND_RECT = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                Math.min(view.getWidth(), view.getHeight()) / 2f);
        }
    };

    public static ViewOutlineProvider fromDrawable(Drawable drawable) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                drawable.getOutline(outline);
            }
        };
    }

    public static ViewOutlineProvider boundsWithPaddingFromViewAndRoundRect(float radius) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), radius);
            }
        };
    }

    public static ViewOutlineProvider boundsWithPaddingRoundRect(int padding, float radius) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(padding, padding,
                    view.getWidth() - padding,
                    view.getHeight() - padding, radius);
            }
        };
    }

    public static ViewOutlineProvider boundsWithPaddingRoundRect(int padding) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int w = view.getWidth() - padding * 2;
                final int h = view.getHeight() - padding * 2;
                final float r = Math.min(w, h) / 2f;

                outline.setRoundRect(padding, padding,
                    view.getWidth() - padding,
                    view.getHeight() - padding, r);
            }
        };
    }


    private static Path drawClipPath;
    private static Outline drawClipOutline;
    private static Rect drawClipRect;

    public static void drawViewWithOutline(Canvas canvas, View view, Utilities.Callback<Canvas> draw) {
        if (canvas == null || view == null) {
            return;
        }

        final ViewOutlineProvider outlineProvider = view.getOutlineProvider();
        if (
                canvas.isHardwareAccelerated() ||
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                        !view.getClipToOutline() ||
                        outlineProvider == null
        ) {
            draw.run(canvas);
            return;
        }

        final Path path;
        final Outline outline;
        final Rect rect;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (drawClipPath == null) {
                drawClipPath = new Path();
                drawClipOutline = new Outline();
                drawClipRect = new Rect();
            }

            path = drawClipPath;
            outline = drawClipOutline;
            rect = drawClipRect;

            outline.setEmpty();
            rect.setEmpty();
        } else {
            path = new Path();
            outline = new Outline();
            rect = new Rect();
        }

        outlineProvider.getOutline(view, outline);

        if (!outlineToPath(outline, rect, path)) {
            draw.run(canvas);
            return;
        }

        final int saveCount = canvas.save();
        canvas.clipPath(path);
        draw.run(canvas);
        canvas.restoreToCount(saveCount);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private static boolean outlineToPath(
            Outline outline,
            Rect rect,
            Path outPath
    ) {
        outPath.rewind();

        if (outline.isEmpty() || !outline.getRect(rect)) {
            return false;
        }

        final float radius = outline.getRadius();

        if (radius > 0f) {
            outPath.addRoundRect(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    radius,
                    radius,
                    Path.Direction.CW
            );
        } else {
            outPath.addRect(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    Path.Direction.CW
            );
        }

        return true;
    }
}
