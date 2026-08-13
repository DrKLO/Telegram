/*
 * UZGRAM Liquid Glass rendering core.
 *
 * Central definition of the translucent surface language used across the app:
 * frosted fills, specular edge strokes, hardware blur, immersive windows and
 * the spring physics used by touch feedback and page transitions.
 *
 * Everything here is presentation only - it never touches storage, networking
 * or serialization. The whole layer can be switched off at runtime through
 * {@link #setEnabled(boolean)}, in which case every helper becomes a no-op and
 * the stock Telegram look is restored.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

public final class LiquidGlass {

    private LiquidGlass() {
    }

    /* ------------------------------------------------------------------ *
     *  Master switch
     * ------------------------------------------------------------------ */

    private static final String PREFS = "liquidglass";
    private static final String KEY_ENABLED = "enabled";

    private static Boolean enabled;

    /**
     * True when the liquid glass presentation layer is active. Reads the stored
     * preference lazily so it stays usable before ApplicationLoader is ready.
     */
    public static boolean isEnabled() {
        if (enabled == null) {
            final Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return true;
            }
            enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
        }
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        final Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            editor.putBoolean(KEY_ENABLED, value);
            editor.apply();
        }
    }

    /* ------------------------------------------------------------------ *
     *  Palette
     * ------------------------------------------------------------------ */

    /** Blur radius used for chrome surfaces (toolbars, tab bars, sheets). */
    public static final float BLUR_RADIUS = 28.0f;
    /** Deeper blur used behind modal dialogs. */
    public static final float MODAL_BLUR_RADIUS = 32.0f;

    /** 65% frosted white. */
    public static final int FILL_LIGHT = Color.argb(165, 255, 255, 255);
    /** 45% volcanic dark glass. */
    public static final int FILL_DARK = Color.argb(115, 18, 18, 18);

    /** Denser fill for floating plates that carry their own content. */
    public static final int PANEL_LIGHT = Color.argb(175, 255, 255, 255);
    public static final int PANEL_DARK = Color.argb(135, 22, 22, 22);

    /** Toolbar masks sit lighter so the content scrolling underneath reads through. */
    public static final int TOOLBAR_LIGHT = Color.argb(140, 255, 255, 255);
    public static final int TOOLBAR_DARK = Color.argb(90, 15, 15, 15);

    /** Micro structural glow along the edge of every glass surface. */
    public static final int STROKE_LIGHT = Color.argb(55, 255, 255, 255);
    public static final int STROKE_DARK = Color.argb(40, 255, 255, 255);

    /** Message bubbles: translucent white in, vivid Apple blue out. */
    public static final int BUBBLE_IN_LIGHT = Color.argb(153, 255, 255, 255);
    public static final int BUBBLE_IN_DARK = Color.argb(140, 44, 44, 46);
    public static final int BUBBLE_OUT = Color.argb(179, 0, 122, 255);

    /** Corner radii of the iOS 26 geometry, in dp. */
    public static final float RADIUS_BUBBLE = 18.0f;
    public static final float RADIUS_MODAL = 24.0f;
    public static final float RADIUS_CELL = 20.0f;

    /** Hairline width of the specular edge, in dp. */
    public static final float STROKE_WIDTH = 0.8f;

    public static boolean isDark() {
        try {
            return Theme.isCurrentThemeDark();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static int getLiquidGlassColor(boolean dark) {
        return dark ? FILL_DARK : FILL_LIGHT;
    }

    public static int getPanelColor(boolean dark) {
        return dark ? PANEL_DARK : PANEL_LIGHT;
    }

    public static int getToolbarColor(boolean dark) {
        return dark ? TOOLBAR_DARK : TOOLBAR_LIGHT;
    }

    public static int getStrokeColor(boolean dark) {
        return dark ? STROKE_DARK : STROKE_LIGHT;
    }

    public static int getBubbleColor(boolean out, boolean dark) {
        if (out) {
            return BUBBLE_OUT;
        }
        return dark ? BUBBLE_IN_DARK : BUBBLE_IN_LIGHT;
    }

    /* ------------------------------------------------------------------ *
     *  Drawing
     * ------------------------------------------------------------------ */

    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Fine translucent border stroke that locks a glass surface down against the
     * content refracting behind it. Drawn inset by half the stroke width so the
     * hairline stays inside the surface bounds.
     */
    public static void drawStroke(Canvas canvas, RectF bounds, float radius) {
        drawStroke(canvas, bounds, radius, isDark(), 1f);
    }

    public static void drawStroke(Canvas canvas, RectF bounds, float radius, boolean dark, float alpha) {
        if (canvas == null || bounds == null || alpha <= 0) {
            return;
        }
        final float width = dp(STROKE_WIDTH);
        strokePaint.setStrokeWidth(width);
        final int color = getStrokeColor(dark);
        strokePaint.setColor(color);
        if (alpha < 1f) {
            strokePaint.setAlpha((int) (Color.alpha(color) * alpha));
        }
        final float inset = width / 2f;
        canvas.drawRoundRect(
            bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset,
            radius, radius, strokePaint
        );
    }

    /** Fills {@code bounds} with the frosted plate colour and its specular edge. */
    public static void drawPanel(Canvas canvas, RectF bounds, float radius) {
        drawPanel(canvas, bounds, radius, getPanelColor(isDark()), 1f);
    }

    public static void drawPanel(Canvas canvas, RectF bounds, float radius, int color, float alpha) {
        if (canvas == null || bounds == null || alpha <= 0) {
            return;
        }
        fillPaint.setColor(color);
        if (alpha < 1f) {
            fillPaint.setAlpha((int) (Color.alpha(color) * alpha));
        }
        canvas.drawRoundRect(bounds, radius, radius, fillPaint);
        drawStroke(canvas, bounds, radius, isDark(), alpha);
    }

    /**
     * Applies the glass treatment along an arbitrary path: a light frosted wash
     * plus the specular hairline. Used by surfaces whose geometry is not a plain
     * rounded rectangle, such as the multi-line service message pill.
     */
    public static void drawPathGlass(Canvas canvas, android.graphics.Path path, float alpha) {
        if (canvas == null || path == null || !isEnabled() || alpha <= 0) {
            return;
        }
        final boolean dark = isDark();
        final int fill = getLiquidGlassColor(dark);
        fillPaint.setColor(fill);
        // A full-strength wash would bleach the service pill, which is already
        // translucent - a third of it is enough to read as frosted glass.
        fillPaint.setAlpha((int) (Color.alpha(fill) * 0.33f * Math.min(1f, alpha)));
        canvas.drawPath(path, fillPaint);

        strokePaint.setStrokeWidth(dp(STROKE_WIDTH));
        final int stroke = getStrokeColor(dark);
        strokePaint.setColor(stroke);
        strokePaint.setAlpha((int) (Color.alpha(stroke) * Math.min(1f, alpha)));
        canvas.drawPath(path, strokePaint);
    }

    /* ------------------------------------------------------------------ *
     *  Hardware blur
     * ------------------------------------------------------------------ */

    public static boolean supportsHardwareBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void applyHardwareBlur(View view) {
        applyHardwareBlur(view, BLUR_RADIUS);
    }

    public static void applyHardwareBlur(View view, float radius) {
        if (view == null || !isEnabled() || !supportsHardwareBlur() || radius <= 0) {
            return;
        }
        try {
            view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        } catch (Throwable ignore) {
            // RenderEffect is unavailable on software-rendered views; the flat fill still applies.
        }
    }

    public static void clearHardwareBlur(View view) {
        if (view == null || !supportsHardwareBlur()) {
            return;
        }
        try {
            view.setRenderEffect(null);
        } catch (Throwable ignore) {
        }
    }

    /* ------------------------------------------------------------------ *
     *  Windows
     * ------------------------------------------------------------------ */

    /**
     * Drops solid system bar colouring so content is drawn edge to edge behind
     * the status and navigation bars. Insets are still delivered to the view
     * tree, so layouts that already consume them keep working.
     */
    public static void applyImmersiveWindow(Window window) {
        if (window == null || !isEnabled()) {
            return;
        }
        try {
            // Reuses the app's own edge-to-edge path: transparent bars, no decor
            // fitting, cutout aware and contrast enforcement disabled.
            AndroidUtilities.enableEdgeToEdge(window);
        } catch (Throwable ignore) {
        }
    }

    /**
     * Makes a modal window transparent and, on Android 12+, blurs everything
     * rendered behind it so the modal reads as a floating crystal plate. When
     * the platform or the user's settings do not allow window blur the call is
     * a no-op and the translucent plate alone carries the effect.
     */
    public static void applyGlassDialogWindow(Window window) {
        applyGlassDialogWindow(window, true);
    }

    /**
     * @param clearWindowBackground pass false when the caller has installed its
     *                              own window background (a native blur plate,
     *                              for instance) that must not be wiped.
     */
    public static void applyGlassDialogWindow(Window window, boolean clearWindowBackground) {
        if (window == null || !isEnabled()) {
            return;
        }
        try {
            if (clearWindowBackground) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                final android.view.WindowManager.LayoutParams params = window.getAttributes();
                params.setBlurBehindRadius((int) MODAL_BLUR_RADIUS);
                window.setAttributes(params);
            }
        } catch (Throwable ignore) {
        }
    }

    /* ------------------------------------------------------------------ *
     *  Physics
     * ------------------------------------------------------------------ */

    /** iOS-style ease used for entrances. */
    public static final Interpolator SPRING_IN = CubicBezierInterpolator.EASE_OUT_QUINT;
    /** Slight overshoot used when a pressed element settles back. */
    public static final Interpolator SPRING_BACK = new OvershootInterpolator(1.5f);

    public static final long PRESS_DURATION = 130;
    public static final long RELEASE_DURATION = 200;
    public static final float PRESS_SCALE = 0.92f;

    /** Scale the outgoing screen shrinks to during a push transition. */
    public static final float PAGE_BACKGROUND_SCALE = 0.94f;

    /**
     * Binds elastic press feedback to a view. The listener never consumes the
     * event, so click handling, long press and scrolling are unaffected.
     */
    public static void bindElasticTouch(final View view) {
        if (view == null || !isEnabled()) {
            return;
        }
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate()
                        .scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                        .setDuration(PRESS_DURATION)
                        .setInterpolator(SPRING_IN)
                        .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(RELEASE_DURATION)
                        .setInterpolator(SPRING_BACK)
                        .start();
                    break;
            }
            return false;
        });
    }

    /**
     * Applies the layered push/pop depth effect to the screen that is being
     * covered: it scales down towards {@link #PAGE_BACKGROUND_SCALE} and fades
     * as {@code progress} runs from 0 (fully visible) to 1 (fully covered).
     */
    public static void applyPageDepth(View view, float progress) {
        if (view == null || !isEnabled()) {
            return;
        }
        progress = Math.max(0f, Math.min(1f, progress));
        final float scale = AndroidUtilities.lerp(1f, PAGE_BACKGROUND_SCALE, progress);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setAlpha(AndroidUtilities.lerp(1f, 0.75f, progress));
    }

    /** Restores a view that {@link #applyPageDepth} has previously transformed. */
    public static void resetPageDepth(View view) {
        if (view == null) {
            return;
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
    }
}
