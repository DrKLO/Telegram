package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.Size;

public class StickerView extends EntityView {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            StickerView.this.stickerDraw(canvas);
        }
    }

    private TLRPC.Document sticker;
    private Object parentObject;
    private int anchor = -1;
    private boolean mirrored = false;
    private Size baseSize;

    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();

    public StickerView(Context context, Point position, Size baseSize, TLRPC.Document sticker, Object parentObject) {
        this(context, position, 0.0f, 1.0f, baseSize, sticker, parentObject);
    }

    public StickerView(Context context, Point position, float angle, float scale, Size baseSize, TLRPC.Document sticker, Object parentObject) {
        super(context, position);
        setRotation(angle);
        setScale(scale);

        this.sticker = sticker;
        this.baseSize = baseSize;
        this.parentObject = parentObject;

        for (int a = 0; a < sticker.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = sticker.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.mask_coords != null) {
                    anchor = attribute.mask_coords.n;
                }
                break;
            }
        }

        containerView = new FrameLayoutDrawer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
        centerImage.setImage(ImageLocation.getForDocument(sticker), null, ImageLocation.getForDocument(thumb, sticker), null, "webp", parentObject, 1);
        centerImage.setDelegate((imageReceiver, set, isThumb, memCache) -> {
            if (set && !isThumb) {
                RLottieDrawable drawable = imageReceiver.getLottieAnimation();
                if (drawable != null) {
                    didSetAnimatedSticker(drawable);
                }
            }
        });

        updatePosition();
    }

    public StickerView(Context context, StickerView stickerView, Point position) {
        this(context, position, stickerView.getRotation(), stickerView.getScale(), stickerView.baseSize, stickerView.sticker, stickerView.parentObject);
        if (stickerView.mirrored) {
            mirror();
        }
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
        mirrored = !mirrored;
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

    protected void didSetAnimatedSticker(RLottieDrawable drawable) {

    }

    protected void stickerDraw(Canvas canvas) {
        if (containerView == null) {
            return;
        }

        canvas.save();
        if (mirrored) {
            canvas.scale(-1.0f, 1.0f);
            canvas.translate(-baseSize.width, 0);
        }
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

        float side = getMeasuredWidth() * (getScale() + 0.5f);
        return new Rect((getPositionX() - side / 2.0f) * scale, (getPositionY() - side / 2.0f) * scale, side * scale, side * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new StickerViewSelectionView(getContext());
    }

    public TLRPC.Document getSticker() {
        return sticker;
    }

    public Object getParentObject() {
        return parentObject;
    }

    public Size getBaseSize() {
        return baseSize;
    }

    public class StickerViewSelectionView extends SelectionView {

        private RectF arcRect = new RectF();

        public StickerViewSelectionView(Context context) {
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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(4.5f);

            float inset = radius + thickness + AndroidUtilities.dp(15);
            float mainRadius = getMeasuredWidth() / 2 - inset;

            arcRect.set(inset, inset, inset + mainRadius * 2, inset + mainRadius * 2);
            canvas.drawArc(arcRect, 0, 180, false, paint);
            canvas.drawArc(arcRect, 180, 180, false, paint);

            canvas.drawCircle(inset, inset + mainRadius, radius, dotPaint);
            canvas.drawCircle(inset, inset + mainRadius, radius, dotStrokePaint);

            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotPaint);
            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotStrokePaint);
        }
    }
}
