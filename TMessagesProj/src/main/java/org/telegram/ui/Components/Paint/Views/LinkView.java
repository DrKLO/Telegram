package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.Gravity;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;

public class LinkView extends EntityView {

    public final LinkPreview marker;
    private int currentColor;
    private int currentType;
    private boolean hasColor;

    public LinkPreview.WebPagePreview link;
    public TL_stories.MediaArea mediaArea;

    @Override
    protected float getStickyPaddingLeft() {
        return marker.padx;
    }

    @Override
    protected float getStickyPaddingTop() {
        return marker.pady;
    }

    @Override
    protected float getStickyPaddingRight() {
        return marker.padx;
    }

    @Override
    protected float getStickyPaddingBottom() {
        return marker.pady;
    }

    public LinkView(Context context, Point position, int currentAccount, LinkPreview.WebPagePreview link, TL_stories.MediaArea mediaArea, float density, int maxWidth, int type) {
        super(context, position);

        marker = new LinkPreview(context, density);
        marker.setMaxWidth(maxWidth);
        setLink(currentAccount, link, mediaArea);
        marker.setType(currentType = type, currentColor);
        addView(marker, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        setClipChildren(false);
        setClipToPadding(false);

        updatePosition();
    }

    public void setLink(int currentAccount, LinkPreview.WebPagePreview link, TL_stories.MediaArea area) {
        this.link = link;
        this.mediaArea = area;
        marker.set(currentAccount, link);
        updateSelectionView();
    }

    public void setMaxWidth(int maxWidth) {
        marker.setMaxWidth(maxWidth);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updatePosition();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updatePosition();
    }

    public void setType(int type) {
        marker.setType(currentType = type, currentColor);
    }


    public void setColor(int color) {
        hasColor = true;
        currentColor = color;
    }

    public boolean hasColor() {
        return hasColor;
    }

    public int getColor() {
        return currentColor;
    }

    public int getType() {
        return currentType;
    }

    public int getNextType() {
        int nextType = currentType + 1;
        if (nextType == 4) {
            return hasColor ? 0 : 1;
        }
        return nextType;
    }

    @Override
    protected float getMaxScale() {
        return 1.5f;
    }

    @Override
    public Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + AndroidUtilities.dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + AndroidUtilities.dp(64) / scale;
        float left = (getPositionX() - width / 2.0f) * scale;
        float right = left + width * scale;
        return new Rect(left, (getPositionY() - height / 2f) * scale, right - left, height * scale);
    }

    protected TextViewSelectionView createSelectionView() {
        return new TextViewSelectionView(getContext());
    }

    public class TextViewSelectionView extends SelectionView {

        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TextViewSelectionView(Context context) {
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
                return 0;
            }

            return 0;
        }

        private Path path = new Path();

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

            canvas.restoreToCount(count);
        }
    }
}
