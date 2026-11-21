package org.telegram.ui.QrShare;

import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.RequiresApi;

/**
 * Everything of the QR screen that needs Android 10+ (render nodes, render effects, blend modes) or 12+ (screen corners).
 * The nodes are kept as {@code Object} by the callers, so their classes never reference the newer framework classes
 * and stay fully verified (and compiled ahead of time) on Android 6–9, where none of this is called.
 */
public final class QrRender {

    public static final int BLEND_NONE = 0, BLEND_PLUS = 1, BLEND_SCREEN = 2, BLEND_COLOR_DODGE = 3, BLEND_OVERLAY = 4,
        BLEND_SOFT_LIGHT = 5, BLEND_COLOR_BURN = 6;

    private QrRender() {
    }

    /** Render nodes with render effects: the blur of the glass. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean hasEffects() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Render nodes and blend modes. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean hasNodes() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** drawVertices on a hardware canvas (HWUI skips it before Android 10). */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean hasVertices() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /* Render nodes */

    @RequiresApi(Build.VERSION_CODES.Q)
    public static Object createNode(String name) {
        return new RenderNode(name);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static Canvas beginRecording(Object node, int width, int height) {
        final RenderNode n = (RenderNode) node;
        n.setPosition(0, 0, width, height);
        return n.beginRecording();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static void endRecording(Object node) {
        ((RenderNode) node).endRecording();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static boolean hasDisplayList(Object node) {
        return node != null && ((RenderNode) node).hasDisplayList();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static void draw(Canvas canvas, Object node) {
        canvas.drawRenderNode((RenderNode) node);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static void setAlpha(Object node, float alpha) {
        ((RenderNode) node).setAlpha(alpha);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public static void setBlur(Object node, float radius, Shader.TileMode mode) {
        ((RenderNode) node).setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, mode) : null);
    }

    /** The blur, then the colour matrix over it. */
    @RequiresApi(Build.VERSION_CODES.S)
    public static void setBlurAndColorMatrix(Object node, float radius, float[] matrix) {
        ((RenderNode) node).setRenderEffect(RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix),
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)));
    }

    /* Clips */

    /** Clips the path out; before Android 8 the same through the difference op (deprecated only later). */
    @SuppressWarnings("deprecation")
    public static void clipOut(Canvas canvas, android.graphics.Path path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(path);
        } else {
            canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
        }
    }

    /* Blend modes */

    @RequiresApi(Build.VERSION_CODES.Q)
    public static void setBlendMode(Paint paint, int mode) {
        paint.setBlendMode(blendMode(mode));
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static BlendMode blendMode(int mode) {
        switch (mode) {
            case BLEND_PLUS:
                return BlendMode.PLUS;
            case BLEND_SCREEN:
                return BlendMode.SCREEN;
            case BLEND_COLOR_DODGE:
                return BlendMode.COLOR_DODGE;
            case BLEND_OVERLAY:
                return BlendMode.OVERLAY;
            case BLEND_SOFT_LIGHT:
                return BlendMode.SOFT_LIGHT;
            case BLEND_COLOR_BURN:
                return BlendMode.COLOR_BURN;
            default:
                return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static ColorFilter srcIn(int color) {
        return new BlendModeColorFilter(color, BlendMode.SRC_IN);
    }

    /* Screen */

    /**
     * The radius of the screen's corners as the system tells it: the window's rounded corner (Android 12+, the view
     * attached), else the framework's rounded_corner_radius; 0 for a square screen or when neither is known.
     */
    public static float screenCornerRadius(View view) {
        if (Build.VERSION.SDK_INT >= 31) {
            final float r = insetsCornerRadius(view);
            if (r >= 0f) {
                return r;
            }
        }
        return frameworkCornerRadius();
    }

    /**
     * The window's top left rounded corner (as ActionBarLayout clips its pages): 0 when the insets have none (a square
     * screen), -1 when there are no insets yet (the view not attached).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private static float insetsCornerRadius(View view) {
        final WindowInsets insets = view != null ? view.getRootWindowInsets() : null;
        if (insets == null) {
            return -1f;
        }
        final RoundedCorner corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        return corner != null ? corner.getRadius() : 0f;
    }

    private static float frameworkRadius = -1f;

    /** The display's corner radius from the framework resources (what SystemUI rounds with), read once. */
    private static float frameworkCornerRadius() {
        if (frameworkRadius < 0f) {
            float r = 0f;
            try {
                final android.content.res.Resources res = android.content.res.Resources.getSystem();
                int id = res.getIdentifier("rounded_corner_radius_top", "dimen", "android");
                if (id != 0) r = res.getDimensionPixelSize(id);
                if (r <= 0f) {
                    id = res.getIdentifier("rounded_corner_radius", "dimen", "android");
                    if (id != 0) r = res.getDimensionPixelSize(id);
                }
            } catch (Exception ignore) {
                r = 0f;
            }
            frameworkRadius = Math.max(0f, r);
        }
        return frameworkRadius;
    }
}
