package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
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
                if (attribute.mask_coords != null)
                    anchor = attribute.mask_coords.n;
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

        updatePosition();
    }

    public StickerView(Context context, StickerView stickerView, Point position) {
        this(context, position, stickerView.getRotation(), stickerView.getScale(), stickerView.baseSize, stickerView.sticker, stickerView.parentObject);
        if (stickerView.mirrored) {
            mirror();
        }
    }

    public int getAnchor() {
        return anchor;
    }

    public void mirror() {
        mirrored = !mirrored;
        containerView.invalidate();
    }

    protected void updatePosition() {
        float halfWidth = baseSize.width / 2.0f;
        float halfHeight = baseSize.height / 2.0f;
        setX(position.x - halfWidth);
        setY(position.y - halfHeight);
        updateSelectionView();
    }

    protected void stickerDraw(Canvas canvas) {
        if (containerView == null) {
            return;
        }

        canvas.save();
        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            if (mirrored) {
                canvas.scale(-1.0f, 1.0f);
                canvas.translate(-baseSize.width, 0);
            }
            centerImage.setImageCoords(0, 0, (int) baseSize.width, (int) baseSize.height);
            centerImage.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec((int) baseSize.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) baseSize.height, MeasureSpec.EXACTLY));
    }

    @Override
    protected Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        float scale = parentView.getScaleX();

        float side = getWidth() * (getScale() + 0.4f);
        return new Rect((position.x - side / 2.0f) * scale, (position.y - side / 2.0f) * scale, side * scale, side * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new StickerViewSelectionView(getContext());
    }

    public TLRPC.Document getSticker() {
        return sticker;
    }

    public class StickerViewSelectionView extends SelectionView {

        private Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF arcRect = new RectF();

        public StickerViewSelectionView(Context context) {
            super(context);

            arcPaint.setColor(0xffffffff);
            arcPaint.setStrokeWidth(AndroidUtilities.dp(1));
            arcPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(19.5f);

            float inset = radius + thickness;
            float middle = inset + (getHeight() - inset * 2) / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + (getWidth() - inset * 2) - radius && y > middle - radius && x < inset + (getWidth() - inset * 2) + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            float selectionRadius = getWidth() / 2.0f;

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
            float mainRadius = getWidth() / 2 - inset;

            float space = 4.0f;
            float length = 4.0f;

            arcRect.set(inset, inset, inset + mainRadius * 2, inset + mainRadius * 2);
            for (int i = 0; i < 48; i++) {
                canvas.drawArc(arcRect, i * (space + length), length, false, arcPaint);
            }

            canvas.drawCircle(inset, inset + mainRadius, radius, dotPaint);
            canvas.drawCircle(inset, inset + mainRadius, radius, dotStrokePaint);

            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotPaint);
            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotStrokePaint);
        }
    }
}
