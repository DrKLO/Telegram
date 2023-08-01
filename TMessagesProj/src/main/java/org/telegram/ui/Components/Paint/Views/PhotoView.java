package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.Size;

public class PhotoView extends EntityView {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            PhotoView.this.stickerDraw(canvas);
        }
    }

    private String path;
    private int anchor = -1;
    private boolean mirrored = false;
    private final AnimatedFloat mirrorT;
    private Size baseSize;

    private FrameLayoutDrawer containerView;
    public final ImageReceiver centerImage = new ImageReceiver();

    public PhotoView(Context context, Point position, float angle, float scale, Size baseSize, String path, int orientation, int invert) {
        super(context, position);
        setRotation(angle);
        setScale(scale);

        this.path = path;
        this.baseSize = baseSize;

        containerView = new FrameLayoutDrawer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        mirrorT = new AnimatedFloat(containerView, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);
        centerImage.setRoundRadius(AndroidUtilities.dp(12));
        centerImage.setOrientation(orientation, invert, true);
        final int side = Math.round(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .8f / AndroidUtilities.density);
        centerImage.setImage(ImageLocation.getForPath(path), side + "_" + side, null, null, null, 1);
        updatePosition();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        centerImage.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        centerImage.onAttachedToWindow();
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
        containerView.invalidate();
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

    protected void stickerDraw(Canvas canvas) {
        if (containerView == null) {
            return;
        }

        canvas.save();
        float mirrorT = this.mirrorT.set(mirrored);
        canvas.scale(1 - mirrorT * 2, 1f, baseSize.width / 2f, 0);
        canvas.skew(0, 4 * mirrorT * (1f - mirrorT) * .25f);
        centerImage.setImageCoords(0, 0, (int) baseSize.width, (int) baseSize.height);
        centerImage.draw(canvas);
        canvas.restore();
    }

    public long getDuration() {
        RLottieDrawable rLottieDrawable = centerImage.getLottieAnimation();
        if (rLottieDrawable != null) {
            return rLottieDrawable.getDuration();
        }
        AnimatedFileDrawable animatedFileDrawable = centerImage.getAnimation();
        if (animatedFileDrawable != null) {
            return animatedFileDrawable.getDurationMs();
        }
        return 0;

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec((int) baseSize.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) baseSize.height, MeasureSpec.EXACTLY));
    }

    @Override
    protected Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + AndroidUtilities.dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + AndroidUtilities.dp(64) / scale;
        float left = (getPositionX() - width / 2.0f) * scale;
        float right = left + width * scale;
        return new Rect(left, (getPositionY() - height / 2.0f) * scale, right - left, height * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new PhotoViewSelectionView(getContext());
    }

    public String getPath() {
        return path;
    }

    public Size getBaseSize() {
        return baseSize;
    }

    public class PhotoViewSelectionView extends SelectionView {

        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PhotoViewSelectionView(Context context) {
            super(context);
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(19.5f);

            float inset = radius + thickness;
            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            float middle = inset + height / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + width - radius && y > middle - radius && x < inset + width + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            if (x > inset && x < width && y > inset && y < height) {
                return SELECTION_WHOLE_HANDLE;
            }

            return 0;
        }

        private Path path = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float thickness = AndroidUtilities.dp(2.0f);
            float radius = AndroidUtilities.dpf2(5.66f);

            float inset = radius + thickness + AndroidUtilities.dp(15);

            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + height);

            float R = AndroidUtilities.dp(12);
            float rx = Math.min(R, width / 2f), ry = Math.min(R, height / 2f);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset, inset + rx * 2, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 180, 90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset, inset + width, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 270, 90);
            canvas.drawPath(path, paint);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset + height - ry * 2, inset + rx * 2, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 180, -90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset + height - ry * 2, inset + width, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 90, -90);
            canvas.drawPath(path, paint);

            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius - AndroidUtilities.dp(1) + 1, dotPaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius - AndroidUtilities.dp(1) + 1, dotPaint);

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            canvas.drawLine(inset, inset + ry, inset, inset + height - ry, paint);
            canvas.drawLine(inset + width, inset + ry, inset + width, inset + height - ry, paint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius + AndroidUtilities.dp(1) - 1, clearPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius + AndroidUtilities.dp(1) - 1, clearPaint);

            canvas.restore();
        }
    }
}
