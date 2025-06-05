package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Size;

public class RoundView extends EntityView {

    private int anchor = -1;
    private boolean mirrored = false;
    private final AnimatedFloat mirrorT;
    private Size baseSize;

    public TextureView textureView;
    private FrameLayout.LayoutParams textureViewParams;

    public Bitmap thumbBitmap;
    public final Rect src = new Rect(), dst = new Rect();

    public RoundView(Context context, Point position, float angle, float scale, Size baseSize, String thumbPath) {
        super(context, position);
        setRotation(angle);
        setScale(scale);

        this.baseSize = baseSize;

        thumbBitmap = BitmapFactory.decodeFile(thumbPath);
        if (thumbBitmap != null) {
            a = (float) thumbBitmap.getWidth() / thumbBitmap.getHeight();
            src.set(0, 0, thumbBitmap.getWidth(), thumbBitmap.getHeight());
        }

        textureView = new TextureView(context);
        addView(textureView, textureViewParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        mirrorT = new AnimatedFloat(this, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

        updatePosition();
        setWillNotDraw(false);
    }

    private float a = 1.0f;
    public void resizeTextureView(int w, int h) {
        final float na = (float) w / h;
        if (Math.abs(a - na) >= 0.0001f) {
            a = na;
            requestLayout();
        }
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = (int) baseSize.width;
        final int h = (int) baseSize.height;
        if (textureView != null) {
            textureView.measure(
                MeasureSpec.makeMeasureSpec(a >= 1.0f ? (int) (a * h) : w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(a >= 1.0f ? h : (int) (w / a), MeasureSpec.EXACTLY)
            );
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (textureView != null) {
            final int t = ((bottom - top) - textureView.getMeasuredHeight()) / 2;
            final int l = ((right - left) - textureView.getMeasuredWidth()) / 2;
            textureView.layout(l, t, l + textureView.getMeasuredWidth(), t + textureView.getMeasuredHeight());
        }
    }

    private final Path clipPath = new Path();

    private boolean draw = true;
    public void setDraw(boolean draw) {
        if (this.draw != draw) {
            this.draw = draw;
            invalidate();
        }
    }

    private boolean shown = true;
    private AnimatedFloat shownT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    public void setShown(boolean shown, boolean animated) {
        if (this.shown != shown) {
            this.shown = shown;
            if (!animated) {
                shownT.set(shown, true);
            }
            invalidate();
        }
    }

    private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    { clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)); }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (!draw) {
            return false;
        }
        if (child == textureView) {
            canvas.save();
            float mirrorT = this.mirrorT.set(mirrored);
            canvas.scale(1 - mirrorT * 2, 1f, getMeasuredWidth() / 2f, 0);
            canvas.skew(0, 4 * mirrorT * (1f - mirrorT) * .25f);

            final float show = shownT.set(shown);

            final float cx = child.getX() + child.getWidth() / 2f, cy = child.getY() + child.getHeight() / 2f, r = Math.min(child.getWidth() / 2f, child.getHeight() / 2f);

            if (show < 1) {
                canvas.saveLayerAlpha(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight(), 0x80, Canvas.ALL_SAVE_FLAG);
                clipPath.rewind();
                clipPath.addCircle(cx, cy, r, Path.Direction.CW);
                canvas.clipPath(clipPath);
                if (thumbBitmap != null) {
                    dst.set(0, 0, child.getWidth(), child.getHeight());
                    canvas.drawBitmap(thumbBitmap, src, dst, null);
                }
                super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            }

            canvas.save();
            clipPath.rewind();
            clipPath.addCircle(cx, cy, r * show, Path.Direction.CW);
            canvas.clipPath(clipPath);
            if (thumbBitmap != null) {
                dst.set(0, 0, child.getWidth(), child.getHeight());
                canvas.drawBitmap(thumbBitmap, src, dst, null);
            }
            final boolean res;
            if (getParent() instanceof EntitiesContainerView && ((EntitiesContainerView) getParent()).drawForThumb) {
                res = true;
            } else {
                res = super.drawChild(canvas, child, drawingTime);
            }

            canvas.restore();

            canvas.restore();
            return res;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public int getAnchor() {
        return anchor;
    }

    public void mirror() {
        mirror(false);
    }

    public void mirror(boolean animated) {
        mirrored = !mirrored;
        if (!animated) {
            mirrorT.set(mirrored, true);
        }
        invalidate();
    }

    public boolean isMirrored() {
        return mirrored;
    }

    protected void updatePosition() {
        float halfWidth = baseSize.width / 2.0f;
        float halfHeight = baseSize.height / 2.0f;
        setX(getPositionX() - halfWidth);
        setY(getPositionY() - halfHeight);
        updateSelectionView();
    }

    @Override
    public org.telegram.ui.Components.Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new org.telegram.ui.Components.Rect();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + AndroidUtilities.dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + AndroidUtilities.dp(64) / scale;
        float left = (getPositionX() - width / 2.0f) * scale;
        float right = left + width * scale;
        return new org.telegram.ui.Components.Rect(left, (getPositionY() - height / 2.0f) * scale, right - left, height * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new RoundViewSelectionView(getContext());
    }

    public Size getBaseSize() {
        return baseSize;
    }

    public class RoundViewSelectionView extends SelectionView {

        public RoundViewSelectionView(Context context) {
            super(context);
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(19.5f);

            float inset = radius + thickness;
            float middle = inset + (getMeasuredHeight() - inset * 2) / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + (getMeasuredWidth() - inset * 2) - radius && y > middle - radius && x < inset + (getMeasuredWidth() - inset * 2) + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            float selectionRadius = getMeasuredWidth() / 2.0f;

            if (Math.pow(x - selectionRadius, 2) + Math.pow(y - selectionRadius, 2) < Math.pow(selectionRadius, 2)) {
                return SELECTION_WHOLE_HANDLE;
            }

            return 0;
        }

        private final RectF arcRect = new RectF();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int count = canvas.getSaveCount();

            float alpha = getShowAlpha();
            if (alpha <= 0) {
                return;
            } else if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dpf2(5.66f);

            float inset = radius + thickness + AndroidUtilities.dp(15);
            float mainRadius = getMeasuredWidth() / 2f - inset;

            arcRect.set(inset, inset, inset + mainRadius * 2, inset + mainRadius * 2);
            canvas.drawArc(arcRect, 0, 180, false, paint);
            canvas.drawArc(arcRect, 180, 180, false, paint);

            canvas.drawCircle(inset, inset + mainRadius, radius, dotStrokePaint);
            canvas.drawCircle(inset, inset + mainRadius, radius - AndroidUtilities.dp(1), dotPaint);

            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotStrokePaint);
            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius - AndroidUtilities.dp(1), dotPaint);

            canvas.restoreToCount(count);
        }
    }

    @Override
    public boolean trashCenter() {
        return true;
    }
}
