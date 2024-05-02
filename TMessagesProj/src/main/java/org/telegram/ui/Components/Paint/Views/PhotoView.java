package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

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

    private TLObject object;
    private String path;
    private int anchor = -1;
    private boolean mirrored = false;
    private final AnimatedFloat mirrorT;
    public Size baseSize;
    private boolean overridenSegmented = false;

    private int orientation, invert;

    private boolean segmented = false;
    private AnimatedFloat segmentedT;

    private final FrameLayoutDrawer containerView;
    public final ImageReceiver centerImage = new ImageReceiver() {
        @Override
        protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
            if (type == TYPE_IMAGE && drawable instanceof BitmapDrawable) {
                segmentImage(((BitmapDrawable) drawable).getBitmap());
            }
            return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
        }
    };

    private File segmentedFile;
    public void preloadSegmented(String path) {
        if (TextUtils.isEmpty(path)) return;
        segmentingLoading = true;
        final int side = Math.round(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .8f / AndroidUtilities.density);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        opts.inSampleSize = StoryEntry.calculateInSampleSize(opts, side, side);
        opts.inJustDecodeBounds = false;
        opts.inDither = true;
        segmentedImage = BitmapFactory.decodeFile(path, opts);
        if (segmentedImage != null) {
            segmentedFile = new File(path);
            segmentingLoaded = true;
        }
        segmentingLoading = false;
    }

    public PhotoView(Context context, Point position, float angle, float scale, Size baseSize, String path, int orientation, int invert) {
        super(context, position);
        setRotation(angle);
        setScale(scale);

        this.path = path;
        this.baseSize = baseSize;

        containerView = new FrameLayoutDrawer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        mirrorT = new AnimatedFloat(containerView, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);
        segmentedT = new AnimatedFloat(containerView, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        this.orientation = orientation;
        this.invert = invert;
        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);
        centerImage.setRoundRadius(dp(12));
        centerImage.setOrientation(orientation, invert, true);
        centerImage.setImage(ImageLocation.getForPath(path), getImageFilter(), null, null, null, 1);
        updatePosition();
    }

    public PhotoView(Context context, Point position, float angle, float scale, Size baseSize, TLObject obj) {
        super(context, position);
        setRotation(angle);
        setScale(scale);

        this.object = obj;
        this.baseSize = baseSize;

        containerView = new FrameLayoutDrawer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        mirrorT = new AnimatedFloat(containerView, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);
        segmentedT = new AnimatedFloat(containerView, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);
        centerImage.setRoundRadius(dp(12));

        if (object instanceof TLRPC.Photo) {
            TLRPC.Photo photo = (TLRPC.Photo) object;
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 1000);
            TLRPC.PhotoSize thumbPhotoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90);
            centerImage.setImage(ImageLocation.getForPhoto(photoSize, photo), getImageFilter(), ImageLocation.getForPhoto(thumbPhotoSize, photo), getImageFilter(), (String) null, null, 1);
        }
        updatePosition();
    }

    private String getImageFilter() {
        final int side = Math.round(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .8f / AndroidUtilities.density);
        return side + "_" + side;
    }

    private boolean segmentingLoading, segmentingLoaded;
    public Bitmap segmentedImage;
    public void segmentImage(Bitmap source) {
        if (segmentingLoaded || segmentingLoading || source == null) return;
        if (Build.VERSION.SDK_INT < 24) return;
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        segmentingLoading = true;
        InputImage inputImage = InputImage.fromBitmap(source, orientation);
        segmenter.process(inputImage)
            .addOnSuccessListener(result -> {
                segmentingLoaded = true;
                segmentingLoading = false;
                segmentedImage = result.getForegroundBitmap();
                highlightSegmented();
            })
            .addOnFailureListener(error -> {
                segmentingLoading = false;
                FileLog.e(error);
                if (isWaitingMlKitError(error) && isAttachedToWindow()) {
                    AndroidUtilities.runOnUIThread(() -> segmentImage(source), 2000);
                } else {
                    segmentingLoaded = true;
                }
            });
    }

    public boolean hasSegmentedImage() {
        return segmentedImage != null;
    }

    public static boolean isWaitingMlKitError(Exception e) {
        if (Build.VERSION.SDK_INT < 24) return false;
        return e instanceof MlKitException && e.getMessage() != null && e.getMessage().contains("segmentation optional module to be downloaded");
    }

    public File saveSegmentedImage(int currentAccount) {
        if (segmentedImage == null) {
            return null;
        }
        if (segmentedFile == null) {
            segmentedFile = StoryEntry.makeCacheFile(currentAccount, "webp");
            try {
                segmentedImage.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(segmentedFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return segmentedFile;
    }

    public void deleteSegmentedFile() {
        if (segmentedFile != null) {
            try {
                segmentedFile.delete();
            } catch (Exception e) {}
            segmentedFile = null;
        }
    }

    public void onSwitchSegmentedAnimationStarted(boolean thanos) {
        overridenSegmented = true;
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    public Bitmap getSegmentedOutBitmap() {
        if (!(centerImage.getImageDrawable() instanceof BitmapDrawable))
            return null;

        Bitmap source = ((BitmapDrawable) centerImage.getImageDrawable()).getBitmap();
        Bitmap mask = segmentedImage;

        if (source == null || mask == null)
            return null;

        int w = source.getWidth(), h = source.getHeight();
        if (orientation == 90 || orientation == 270 || orientation == -90 || orientation == -270) {
            w = source.getHeight();
            h = source.getWidth();
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        roundRectPath.rewind();
        AndroidUtilities.rectTmp.set(0, 0, w, h);
        float mirrorT = this.mirrorT.get();
        canvas.scale(1 - mirrorT * 2, 1f, w / 2f, 0);
        canvas.skew(0, 4 * mirrorT * (1f - mirrorT) * .25f);
        roundRectPath.addRoundRect(AndroidUtilities.rectTmp, dp(12) * getScaleX(), dp(12) * getScaleY(), Path.Direction.CW);
        canvas.clipPath(roundRectPath);
        canvas.translate(w / 2f, h / 2f);
        canvas.rotate(orientation);
        canvas.translate(-source.getWidth() / 2f, -source.getHeight() / 2f);

        AndroidUtilities.rectTmp.set(0, 0, source.getWidth(), source.getHeight());
        canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 0xFF, Canvas.ALL_SAVE_FLAG);
        canvas.drawBitmap(source, 0, 0, null);
        Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        canvas.save();
        canvas.drawBitmap(mask, 0, 0, clearPaint);
        canvas.restore();
        canvas.restore();

        return bitmap;
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
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    public boolean isMirrored() {
        return mirrored;
    }

    public boolean isSegmented() {
        return segmented;
    }

    public void toggleSegmented(boolean animated) {
        segmented = !segmented;
        if (animated && segmented) {
            overridenSegmented = false;
        }
        if (!animated) {
            segmentedT.set(segmented, true);
        }
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    protected void updatePosition() {
        float halfWidth = baseSize.width / 2.0f;
        float halfHeight = baseSize.height / 2.0f;
        setX(getPositionX() - halfWidth);
        setY(getPositionY() - halfHeight);
        updateSelectionView();
    }

    private final android.graphics.Rect src = new android.graphics.Rect();
    private final android.graphics.RectF dest = new android.graphics.RectF();

    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private long highlightStart = -1;
    private LinearGradient highlightGradient;
    private Matrix highlightGradientMatrix;
    private Paint highlightPaint;
    private boolean needHighlight;

    protected void stickerDraw(Canvas canvas) {
        if (containerView == null) {
            return;
        }

        canvas.save();
        float mirrorT = this.mirrorT.set(mirrored);
        canvas.scale(1 - mirrorT * 2, 1f, baseSize.width / 2f, 0);
        canvas.skew(0, 4 * mirrorT * (1f - mirrorT) * .25f);

        final float segmentedT = this.segmentedT.set(segmented);
        if (!segmented) {
            centerImage.setAlpha(1f - segmentedT);
            centerImage.setImageCoords(0, 0, (int) baseSize.width, (int) baseSize.height);
            centerImage.draw(canvas);
            if (segmentedT > 0) {
                drawSegmented(canvas);
            }

            if (segmentedImage != null) {
                canvas.saveLayerAlpha(0, 0, baseSize.width, baseSize.height, 0xFF, Canvas.ALL_SAVE_FLAG);
                drawSegmented(canvas);
                canvas.save();
                final long now = System.currentTimeMillis();
                if (highlightStart <= 0) {
                    highlightStart = now;
                }
                final float gradientWidth = .80f * baseSize.width;
                final float highlightT = (now - highlightStart) / 1000f;
                final float translate = highlightT * (2 * gradientWidth + baseSize.width) - gradientWidth;
                if (highlightPaint == null) {
                    highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    highlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                    highlightGradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{0x00feee8c, 0x66feee8c, 0x66feee8c, 0x00feee8c}, new float[]{0, .4f, .6f, 1f}, Shader.TileMode.CLAMP);
                    highlightGradientMatrix = new Matrix();
                    highlightGradient.setLocalMatrix(highlightGradientMatrix);
                    highlightPaint.setShader(highlightGradient);
                }
                highlightGradientMatrix.reset();
                highlightGradientMatrix.postTranslate(translate, 0);
                highlightGradient.setLocalMatrix(highlightGradientMatrix);
                canvas.drawRect(0, 0, (int) baseSize.width, (int) baseSize.height, highlightPaint);
                canvas.restore();
                canvas.restore();

                if ((highlightT > 0 || needHighlight) && highlightT < 1f) {
                    needHighlight = false;
                    containerView.invalidate();
                }
            }
        } else {
            highlightStart = -1;
            needHighlight = false;
            if (!overridenSegmented) {
                centerImage.setImageCoords(0, 0, (int) baseSize.width, (int) baseSize.height);
                centerImage.setAlpha(1f);
                centerImage.draw(canvas);
            }
            drawSegmented(canvas);
        }

        canvas.restore();
    }

    private Path roundRectPath;
    private void drawSegmented(Canvas canvas) {
        if (segmentedImage == null) return;
        src.set(0, 0, segmentedImage.getWidth(), segmentedImage.getHeight());
        int bitmapWidth = segmentedImage.getWidth(), bitmapHeight = segmentedImage.getHeight();
        if (orientation == 90 || orientation == 270 || orientation == -90 || orientation == -270) {
            bitmapWidth = segmentedImage.getHeight();
            bitmapHeight = segmentedImage.getWidth();
        }
        final float scale = Math.max(bitmapWidth / baseSize.width, bitmapHeight / baseSize.height);
        final float bitmapW = segmentedImage.getWidth() / scale;
        final float bitmapH = segmentedImage.getHeight() / scale;
        dest.set((baseSize.width - bitmapW) / 2, (baseSize.height - bitmapH) / 2, (baseSize.width + bitmapW) / 2, (baseSize.height + bitmapH) / 2);
        canvas.save();
        if (orientation != 0) {
            canvas.rotate(orientation, dest.centerX(), dest.centerY());
        }
        if (roundRectPath == null) {
            roundRectPath = new Path();
        }
        roundRectPath.rewind();
        roundRectPath.addRoundRect(dest, dp(12), dp(12), Path.Direction.CW);
        canvas.clipPath(roundRectPath);
        canvas.drawBitmap(segmentedImage, src, dest, segmentPaint);
        canvas.restore();
    }

    public void highlightSegmented() {
        needHighlight = true;
        if (highlightStart <= 0 || System.currentTimeMillis() - highlightStart >= 1000) {
            highlightStart = System.currentTimeMillis();
        }
        if (containerView != null) {
            containerView.invalidate();
        }
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
    public Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();
        float width = getMeasuredWidth() * getScale() + dp(64) / scale;
        float height = getMeasuredHeight() * getScale() + dp(64) / scale;
        float left = (getPositionX() - width / 2.0f) * scale;
        float right = left + width * scale;
        return new Rect(left, (getPositionY() - height / 2.0f) * scale, right - left, height * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new PhotoViewSelectionView(getContext());
    }

    public String getPath(int currentAccount) {
        if (object instanceof TLRPC.Photo) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) object).sizes, 1000);
            try {
                return FileLoader.getInstance(currentAccount).getPathToAttach(photoSize, true).getAbsolutePath();
            } catch (Exception ignore) {}
        }
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
            float thickness = dp(1.0f);
            float radius = dp(19.5f);

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

            int count = canvas.getSaveCount();

            float alpha = getShowAlpha();
            if (alpha <= 0) {
                return;
            } else if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            float thickness = dp(2.0f);
            float radius = AndroidUtilities.dpf2(5.66f);

            float inset = radius + thickness + dp(15);

            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + height);

            float R = dp(12);
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
            canvas.drawCircle(inset, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            canvas.drawLine(inset, inset + ry, inset, inset + height - ry, paint);
            canvas.drawLine(inset + width, inset + ry, inset + width, inset + height - ry, paint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);

            canvas.restoreToCount(count);
        }
    }
}
