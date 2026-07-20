package org.telegram.messenger.utils;

import static org.telegram.messenger.AndroidUtilities.cascade;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.graphics.Rect;

import org.telegram.messenger.ImageReceiver;

public class DrawableUtils {
    private static final Rect tmpRect = new Rect();
    private static final RectF tmpRectF = new RectF();

    private DrawableUtils() {

    }

    public static void drawWithScale(Canvas canvas, Drawable drawable, float scale) {
        if (drawable == null || scale == 0) {
            return;
        }
        if (scale == 1) {
            drawable.draw(canvas);
        } else {
            canvas.save();
            canvas.scale(scale, scale, drawable.getBounds().exactCenterX(), drawable.getBounds().exactCenterY());
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    public static void setBoundsIncreasePadding(Drawable drawable, Rect bounds) {
        if (drawable.getPadding(tmpRect)) {
            drawable.setBounds(
                bounds.left - tmpRect.left,
                bounds.top - tmpRect.top,
                bounds.right + tmpRect.right,
                bounds.bottom + tmpRect.bottom);
        } else {
            drawable.setBounds(bounds);
        }
    }

    public static int getCommunityCardDrawableRadius(int size) {
        return size * 20 / 72;
    }

    public static void drawCommunityCardDrawable(Canvas canvas, Drawable drawable, float cx, float cy, float size) {
        final float left = cx - dpf2(36);
        final float top = cy - dpf2(36);
        final float scale = size / dpf2(72);
        DrawableUtils.setBounds(drawable, left + dpf2(9.66f), top + dpf2(4.66f), Gravity.RIGHT | Gravity.TOP);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        drawable.draw(canvas);
        canvas.restore();
    }

    public static void setBounds(Drawable drawable, float x, float y, int gravity) {
        if (drawable == null) {
            return;
        }
        setBounds(drawable, x, y, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), gravity);
    }

    public static void setBounds(ImageReceiver drawable, float x, float y,
                                 int width, int height, int gravity) {
        if (drawable != null) {
            setBounds(tmpRect, x, y, width, height, gravity);
            drawable.setImageCoords(tmpRect);
        }
    }

    public static void setBounds(Drawable drawable, float x, float y,
                                 int width, int height, int gravity) {
        if (drawable != null) {
            setBounds(tmpRect, x, y, width, height, gravity);
            drawable.setBounds(tmpRect);
        }
    }

    public static void setBounds(Rect rect, float x, float y,
        int width, int height, int gravity) {
        int left;
        int top;

        final int horizontalGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        switch (horizontalGravity) {
            case Gravity.LEFT:
                left = Math.round(x);
                break;

            case Gravity.RIGHT:
                left = Math.round(x - width);
                break;

            case Gravity.CENTER_HORIZONTAL:
            default:
                left = Math.round(x - width / 2f);
                break;
        }

        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        switch (verticalGravity) {
            case Gravity.TOP:
                top = Math.round(y);
                break;
            case Gravity.BOTTOM:
                top = Math.round(y - height);
                break;
            case Gravity.CENTER_VERTICAL:
            default:
                top = Math.round(y - height / 2f);
                break;
        }

        rect.set(left, top, left + width, top + height);
    }
}
