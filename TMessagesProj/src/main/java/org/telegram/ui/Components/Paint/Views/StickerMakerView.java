package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.segmentation.subject.Subject;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmuDetector;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ObjectDetectionEmojis;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Stories.recorder.DownloadButton;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

@SuppressLint("ViewConstructor")
public class StickerMakerView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public int currentAccount = -1;
    private final AnimatedFloat segmentBorderAlpha = new AnimatedFloat(0, (View) null, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat outlineAlpha = new AnimatedFloat(0, (View) null, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segmentBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PathMeasure bordersPathMeasure = new PathMeasure();
    private final Path bgPath = new Path();
    private final Path areaPath = new Path();
    private final Path screenPath = new Path();
    private final Path dashPath = new Path();
    private volatile boolean segmentingLoading;
    private volatile boolean segmentingLoaded;
    private SegmentedObject selectedObject;
    public float outlineWidth = 2f;
    public boolean empty;
    public SegmentedObject[] objects;
    private volatile Bitmap sourceBitmap;
    public int orientation;
    private Bitmap filteredBitmap;
    private boolean isSegmentedState;
    private final TextView actionTextView;
    private ValueAnimator bordersAnimator;
    private ValueAnimator bordersEnterAnimator;
    private float segmentBorderImageWidth, segmentBorderImageHeight;
    private float bordersAnimatorValueStart, bordersAnimatorValue;
    private float bordersEnterAnimatorValue;
    private ThanosEffect thanosEffect;
    private int containerWidth;
    private int containerHeight;
    public boolean isThanosInProgress;
    private StickerUploader stickerUploader;
    private AlertDialog loadingDialog;
    private final Theme.ResourcesProvider resourcesProvider;
    private StickerCutOutBtn stickerCutOutBtn;
    public String detectedEmoji;

    private DownloadButton.PreparingVideoToast loadingToast;

    private final Matrix imageReceiverMatrix = new Matrix();
    private float imageReceiverWidth, imageReceiverHeight;

    public PaintWeightChooserView weightChooserView;

    public StickerMakerView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        dashPaint.setColor(0xffffffff);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(dp(2));
        dashPaint.setStrokeCap(Paint.Cap.ROUND);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(10)}, .5f));
        dashPaint.setShadowLayer(AndroidUtilities.dpf2(0.75f), 0, 0, 0x50000000);
        dashPaint.setAlpha(140);

        actionTextView = new TextView(context);
        actionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        actionTextView.setTextColor(Color.WHITE);
        actionTextView.setAlpha(0f);
        actionTextView.setScaleX(0.3f);
        actionTextView.setScaleY(0.3f);
        addView(actionTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(dp(3));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        borderPaint.setPathEffect(new CornerPathEffect(dp(20)));
        borderPaint.setMaskFilter(new BlurMaskFilter(dp(4), BlurMaskFilter.Blur.NORMAL));

        segmentBorderPaint.setColor(Color.WHITE);
        segmentBorderPaint.setStrokeWidth(dp(3));
        segmentBorderPaint.setStyle(Paint.Style.STROKE);
        segmentBorderPaint.setStrokeCap(Paint.Cap.ROUND);
        segmentBorderPaint.setPathEffect(new CornerPathEffect(dp(20)));
        segmentBorderPaint.setMaskFilter(new BlurMaskFilter(dp(4), BlurMaskFilter.Blur.NORMAL));

        bgPaint.setColor(0x66000000);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        weightChooserView = new PaintWeightChooserView(context);
        weightChooserView.setAlpha(0f);
        weightChooserView.setTranslationX(-dp(18));
        weightChooserView.setMinMax(.33f, 10);
        weightChooserView.setBrushWeight(outlineWidth);
        weightChooserView.setValueOverride(new PaintWeightChooserView.ValueOverride() {
            @Override
            public float get() {
                return outlineWidth;
            }
            @Override
            public void set(float val) {
                setOutlineWidth(val);
            }
        });
        weightChooserView.setTranslationX(-dp(18));
        weightChooserView.setAlpha(0f);
        addView(weightChooserView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setStickerCutOutBtn(StickerCutOutBtn stickerCutOutBtn) {
        this.stickerCutOutBtn = stickerCutOutBtn;
    }

    public float getSegmentBorderImageHeight() {
        return segmentBorderImageHeight;
    }

    public float getSegmentBorderImageWidth() {
        return segmentBorderImageWidth;
    }

    public ThanosEffect getThanosEffect() {
        if (!ThanosEffect.supports()) {
            return null;
        }
        if (thanosEffect == null) {
            addView(thanosEffect = new ThanosEffect(getContext(), () -> {
                ThanosEffect thisThanosEffect = thanosEffect;
                if (thisThanosEffect != null) {
                    thanosEffect = null;
                    removeView(thisThanosEffect);
                }
            }), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        return thanosEffect;
    }

    public class SegmentedObject {

        public AnimatedFloat select = new AnimatedFloat(0, (View) null, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public boolean hover;

        public int orientation;
        public Bitmap image;
        public Bitmap overrideImage;
        public Bitmap darkMaskImage;
        public Bitmap overrideDarkMaskImage;

        public Bitmap getImage() {
            if (overrideImage != null) {
                return overrideImage;
            }
            return image;
        }

        public Bitmap getDarkMaskImage() {
            if (overrideDarkMaskImage != null) {
                return overrideDarkMaskImage;
            }
            return darkMaskImage;
        }

        public Bitmap makeDarkMaskImage() {
            Bitmap darkMaskImage = Bitmap.createBitmap(getImage().getWidth(), getImage().getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(darkMaskImage);
            canvas.drawColor(Color.BLACK);
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvas.drawBitmap(getImage(), 0, 0, maskPaint);
            return darkMaskImage;
        }

        public RectF bounds = new RectF();
        public RectF rotatedBounds = new RectF();

        private float borderImageWidth;
        private float borderImageHeight;

        private final Path segmentBorderPath = new Path();
        private final Path partSegmentBorderPath = new Path();

        private int pointsCount;
        private float[] points;
        private final Paint bordersFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bordersStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bordersDiffStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointsHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final boolean USE_POINTS = true;
        public void initPoints() {
            PathMeasure pathMeasure = new PathMeasure();
            pathMeasure.setPath(segmentBorderPath, true);
            final float length = pathMeasure.getLength();
            final float pointRadius = dp(2);
            pointsCount = (int) Math.ceil(length / pointRadius);
            this.points = new float[pointsCount * 2];
            final float[] pos = new float[2];
            for (int i = 0; i < pointsCount; ++i) {
                pathMeasure.getPosTan(((float) i / pointsCount * length) % length, pos, null);
                this.points[i * 2] = pos[0];
                this.points[i * 2 + 1] = pos[1];
            }

            bordersFillPaint.setStyle(Paint.Style.FILL);
            bordersFillPaint.setColor(Color.WHITE);
            bordersFillPaint.setStrokeJoin(Paint.Join.ROUND);
            bordersFillPaint.setStrokeCap(Paint.Cap.ROUND);
            bordersFillPaint.setPathEffect(new CornerPathEffect(dp(10)));
            bordersStrokePaint.setStyle(Paint.Style.STROKE);
            bordersStrokePaint.setColor(Color.WHITE);
            bordersStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            bordersStrokePaint.setStrokeCap(Paint.Cap.ROUND);
            bordersStrokePaint.setPathEffect(new CornerPathEffect(dp(10)));

            pointsPaint.setStyle(Paint.Style.STROKE);
            pointsPaint.setStrokeWidth(dp(4));
            pointsPaint.setColor(0xFFFFFFFF);
            pointsPaint.setStrokeCap(Paint.Cap.ROUND);
            pointsPaint.setMaskFilter(new BlurMaskFilter(dp(.33f), BlurMaskFilter.Blur.NORMAL));

            pointsHighlightPaint.setStyle(Paint.Style.STROKE);
            pointsHighlightPaint.setColor(Theme.multAlpha(0xFFFFFFFF, .04f));
            pointsHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
            pointsHighlightPaint.setStrokeWidth(dp(20));
            pointsHighlightPaint.setColor(Theme.multAlpha(Color.WHITE, .04f));
            pointsHighlightPaint.setMaskFilter(new BlurMaskFilter(dp(60), BlurMaskFilter.Blur.NORMAL));
        }

        public void drawOutline(Canvas canvas, boolean after, float width, float alpha) {
            if (outlineBoundsPath == null)
                return;
            canvas.save();
            canvas.clipPath(outlineBoundsPath);
            if (sourceBitmap != null) {
                Paint paint = after ? bordersStrokePaint : bordersFillPaint;
                paint.setAlpha((int) (0xFF * alpha));
                paint.setStrokeWidth(dp(width));
                canvas.drawPath(segmentBorderPath, paint);
                if (outlineBoundsPath != null && after) {
                    canvas.clipPath(segmentBorderPath);
                    paint.setStrokeWidth(dp(2 * width));
                    canvas.drawPath(outlineBoundsPath, paint);
                }
            }
            canvas.restore();
        }

        public void drawAnimationBorders(Canvas canvas, float progress, float alpha, View parent) {
            select.setParent(parent);
            if (sourceBitmap == null || alpha <= 0) return;

            final float s = lerp(1f, 1.065f, alpha) * lerp(1f, 1.05f, select.set(hover));

            int w, h;
            if (orientation / 90 % 2 != 0) {
                w = sourceBitmap.getHeight();
                h = sourceBitmap.getWidth();
            } else {
                w = sourceBitmap.getWidth();
                h = sourceBitmap.getHeight();
            }

            canvas.save();
            canvas.scale(s, s, rotatedBounds.centerX() / w * borderImageWidth - borderImageWidth / 2f, rotatedBounds.centerY() / h * borderImageHeight - borderImageHeight / 2f);

            if (USE_POINTS && points != null) {
                final int fromIndex = (int) (progress * pointsCount);
                final int toIndex = fromIndex + Math.min(500, (int) (.6f * pointsCount));
                if (pointsCount > 0) {
                    for (int i = fromIndex; i <= toIndex; ++i) {
                        final float ha = (1f - (toIndex - i) / (float) pointsCount);
                        if (ha > 0) {
                            pointsHighlightPaint.setAlpha((int) (0xFF * .04f * ha * alpha));
                            canvas.drawPoints(points, (i % pointsCount) * 2, 2, pointsHighlightPaint);
                        }
                    }
                }
            }

            if (getImage() != null) {
                canvas.save();
                canvas.rotate(orientation);
                canvas.scale(1f / w * borderImageWidth, 1f / h * borderImageHeight);
                canvas.drawBitmap(getImage(), -sourceBitmap.getWidth() / 2f, -sourceBitmap.getHeight() / 2f, null);
                canvas.restore();
            }

            if (USE_POINTS && points != null) {
                final int fromIndex = (int) (progress * pointsCount);
                final int toIndex = fromIndex + Math.min(500, (int) (.6f * pointsCount));
                if (pointsCount > 0) {
                    for (int i = fromIndex; i <= toIndex; ++i) {
                        final float p = (float) (i - fromIndex) / (toIndex - fromIndex);
                        final float a = Math.min(1, 4f * Math.min(p, 1 - p));
                        pointsPaint.setAlpha((int) (0xFF * a * alpha));
                        canvas.drawPoints(points, (i % pointsCount) * 2, 2, pointsPaint);
                    }
                }
            } else {
                bordersPathMeasure.setPath(segmentBorderPath, false);
                partSegmentBorderPath.reset();

                float length = bordersPathMeasure.getLength();
                if (length == 0) {
                    return;
                }

                segmentBorderPaint.setAlpha((int) (0xFF * alpha));
                borderPaint.setAlpha((int) (0x40 * alpha));
                canvas.drawPath(partSegmentBorderPath, borderPaint);

                float toPercent = progress + 0.2f;
                float from = length * progress;
                float to = length * toPercent;
                bordersPathMeasure.getSegment(from, to, partSegmentBorderPath, true);

                canvas.drawPath(partSegmentBorderPath, segmentBorderPaint);
                canvas.drawPath(partSegmentBorderPath, segmentBorderPaint);
                if (toPercent > 1) {
                    from = 0;
                    to = (toPercent - 1) * length;
                    partSegmentBorderPath.reset();
                    bordersPathMeasure.setPath(segmentBorderPath, false);
                    bordersPathMeasure.getSegment(from, to, partSegmentBorderPath, true);
                    canvas.drawPath(partSegmentBorderPath, segmentBorderPaint);
                    canvas.drawPath(partSegmentBorderPath, segmentBorderPaint);
                }
            }

            canvas.restore();
        }

        public void recycle() {
            segmentBorderPath.reset();
            if (overrideImage != null) {
                overrideImage.recycle();
                overrideImage = null;
            }
            if (image != null) {
                image.recycle();
                image = null;
            }
            if (overrideDarkMaskImage != null) {
                overrideDarkMaskImage.recycle();
                overrideDarkMaskImage = null;
            }
            if (darkMaskImage != null) {
                darkMaskImage.recycle();
                darkMaskImage = null;
            }
        }
    }

    public void setOutlineWidth(float width) {
        outlineWidth = width;
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    public void updateOutlinePath(Bitmap newMaskBitmap) {
        if (selectedObject == null) return;
        selectedObject.overrideImage = createSmoothEdgesSegmentedImage(0, 0, newMaskBitmap, true);
        selectedObject.overrideDarkMaskImage = selectedObject.makeDarkMaskImage();
        createSegmentImagePath(selectedObject, containerWidth, containerHeight);
    }

    public boolean overriddenPaths() {
        if (objects != null) {
            for (SegmentedObject obj : objects) {
                if (obj != null && obj.overrideImage != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public void resetPaths() {
        if (objects != null) {
            for (SegmentedObject obj : objects) {
                if (obj != null && obj.overrideImage != null) {
                    obj.overrideImage.recycle();
                    obj.overrideImage = null;
                    if (obj.overrideDarkMaskImage != null) {
                        obj.overrideDarkMaskImage.recycle();
                        obj.overrideDarkMaskImage = null;
                    }
                    createSegmentImagePath(obj, containerWidth, containerHeight);
                }
            }
        }
    }

    public boolean outlineVisible;
    public void setOutlineVisible(boolean visible) {
        if (outlineVisible == visible) return;
        outlineVisible = visible;
        weightChooserView.animate().alpha(visible ? 1f : 0f).translationX(visible ? 0 : dp(-18)).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exclusionRects.clear();
            if (outlineVisible) {
                exclusionRects.add(exclusionRect);
                int h = (int) (getMeasuredHeight() * .3f);
                exclusionRect.set(0, (getMeasuredHeight() - h) / 2, dp(20), (getMeasuredHeight() + h) / 2);
            }
            setSystemGestureExclusionRects(exclusionRects);
        }
    }

    public void drawOutline(Canvas canvas, boolean after, ViewGroup parent, boolean hide) {
        this.outlineAlpha.setParent(parent);
        if (!outlineVisible && this.outlineAlpha.get() <= 0) {
            return;
        }

        final float outlineAlpha = parent == null ? 1f : this.outlineAlpha.set(outlineVisible && !hide);
        if (objects != null) {
            for (SegmentedObject object : objects) {
                if (object != null && object == selectedObject && outlineWidth > 0) {
                    object.drawOutline(canvas, after, outlineWidth, outlineAlpha);
                    break;
                }
            }
        }
    }

    public boolean setOutlineBounds;
    public final Matrix outlineMatrix = new Matrix();
    private Path outlineBoundsPath, outlineBoundsInnerPath;
    private final RectF outlineBounds = new RectF();
    public void updateOutlineBounds(boolean set) {
        setOutlineBounds = set;
        if (set) {
            if (outlineBoundsPath == null) {
                outlineBoundsPath = new Path();
            } else {
                outlineBoundsPath.rewind();
            }
            if (outlineBoundsInnerPath == null) {
                outlineBoundsInnerPath = new Path();
                AndroidUtilities.rectTmp.set(0, 0, 1, 1);
                outlineBoundsInnerPath.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.width() * .12f, AndroidUtilities.rectTmp.height() * .12f, Path.Direction.CW);
            }
            outlineBoundsPath.addPath(outlineBoundsInnerPath, outlineMatrix);
            outlineBoundsPath.computeBounds(outlineBounds, true);
        }
    }

    public void drawSegmentBorderPath(Canvas canvas, ImageReceiver imageReceiver, Matrix matrix, ViewGroup parent) {
        segmentBorderAlpha.setParent(parent);
        if (bordersAnimator == null && segmentBorderAlpha.get() <= 0 || parent == null) {
            return;
        }

        imageReceiverWidth = imageReceiver.getImageWidth();
        imageReceiverHeight = imageReceiver.getImageHeight();
        imageReceiverMatrix.set(matrix);

        float progress = (bordersAnimatorValueStart + bordersAnimatorValue) % 1.0f;
        float alpha = segmentBorderAlpha.set(bordersAnimator != null);

        canvas.drawColor(Theme.multAlpha(0x50000000, alpha));
        if (objects != null) {
            for (SegmentedObject object : objects) {
                if (object == null) continue;
                object.drawAnimationBorders(canvas, progress, alpha, parent);
            }
        }

        parent.invalidate();
    }

    public void enableClippingMode(Utilities.Callback<SegmentedObject> onClickListener) {
        setOnClickListener(v -> {
            if (objects == null || objects.length == 0 || sourceBitmap == null) {
                return;
            }

            SegmentedObject object = objectBehind(tx, ty);
            if (object != null) {
                onClickListener.run(object);
            }
        });

        actionTextView.setText(LocaleController.getString(R.string.SegmentationTabToCrop));
        actionTextView.animate().cancel();
        actionTextView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        if (bordersAnimator != null) {
            bordersAnimator.cancel();
        }
        bordersAnimatorValueStart = bordersAnimatorValue;
        bordersAnimator = ValueAnimator.ofFloat(0, 1);
        bordersAnimator.addUpdateListener(animation -> {
            bordersAnimatorValue = (float) animation.getAnimatedValue();
        });
        bordersAnimator.setRepeatCount(ValueAnimator.INFINITE);
        bordersAnimator.setRepeatMode(ValueAnimator.RESTART);
        bordersAnimator.setDuration(2400);
        bordersAnimator.setInterpolator(new LinearInterpolator());
        bordersAnimator.start();
    }

    float tx, ty;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        tx = ev.getX();
        ty = ev.getY();
        if (objects != null && bordersAnimator != null) {
            SegmentedObject object = objectBehind(tx, ty);
            for (int i = 0; i < objects.length; ++i) {
                final boolean hover = objects[i] == object && (ev.getAction() != MotionEvent.ACTION_CANCEL && ev.getAction() != MotionEvent.ACTION_UP);
                if (hover && !objects[i].hover) {
                    AndroidUtilities.vibrateCursor(this);
                }
                objects[i].hover = hover;
            }
            if (getParent() instanceof View) {
                ((View) getParent()).invalidate();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    public SegmentedObject objectBehind(float tx, float ty) {
        if (sourceBitmap == null) return null;
        for (int i = 0; i < objects.length; ++i) {
            SegmentedObject obj = objects[i];
            if (obj == null) continue;
            int w, h;
            if (objects[i].orientation / 90 % 2 != 0) {
                w = sourceBitmap.getHeight();
                h = sourceBitmap.getWidth();
            } else {
                w = sourceBitmap.getWidth();
                h = sourceBitmap.getHeight();
            }
            AndroidUtilities.rectTmp.set(
                objects[i].rotatedBounds.left / w * imageReceiverWidth,
                objects[i].rotatedBounds.top / h * imageReceiverHeight,
                objects[i].rotatedBounds.right / w * imageReceiverWidth,
                objects[i].rotatedBounds.bottom / h * imageReceiverHeight
            );
            imageReceiverMatrix.mapRect(AndroidUtilities.rectTmp);
            if (AndroidUtilities.rectTmp.contains(tx, ty)) {
                return obj;
            }
        }
        return null;
    }

    public void disableClippingMode() {
        segmentBorderAlpha.set(0f);
        if (bordersAnimator != null) {
            bordersAnimator.cancel();
            bordersAnimator = null;
        }
        setOnClickListener(null);
        setClickable(false);
        actionTextView.animate().cancel();
        actionTextView.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
    }

    private ArrayList<Rect> exclusionRects = new ArrayList<>();
    private Rect exclusionRect = new Rect();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        actionTextView.setTranslationY(-(getMeasuredWidth() / 2f + dp(10)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exclusionRects.clear();
            if (outlineVisible) {
                exclusionRects.add(exclusionRect);
                int h = (int) (getMeasuredHeight() * .3f);
                exclusionRect.set(0, (getMeasuredHeight() - h) / 2, dp(20), (getMeasuredHeight() + h) / 2);
            }
            setSystemGestureExclusionRects(exclusionRects);
        }
    }

    public boolean isSegmentedState() {
        return isSegmentedState;
    }

    public void setSegmentedState(boolean segmentedState, SegmentedObject selectedObject) {
        isSegmentedState = segmentedState;
        this.selectedObject = selectedObject;
    }

    public Bitmap getSegmentedDarkMaskImage() {
        return isSegmentedState && selectedObject != null ? selectedObject.getDarkMaskImage() : null;
    }

    public boolean hasSegmentedBitmap() {
        return segmentingLoaded && objects != null && objects.length > 0;
    }

    public Bitmap getSourceBitmap() {
        return sourceBitmap;
    }

    public Bitmap getSourceBitmap(boolean hasFilters) {
        if (hasFilters && filteredBitmap != null) {
            return filteredBitmap;
        }
        return sourceBitmap;
    }

    public Bitmap getSegmentedImage(Bitmap filteredBitmap, boolean hasFilters, int orientation) {
        if (selectedObject == null) {
            return sourceBitmap;
        }
        if (hasFilters && filteredBitmap != null) {
            return cutSegmentInFilteredBitmap(filteredBitmap, orientation);
        }
        return selectedObject.getImage();
    }

    @Nullable
    public Bitmap getThanosImage(MediaController.PhotoEntry photoEntry, int orientation) {
        Bitmap filteredBitmap = photoEntry.filterPath != null ? BitmapFactory.decodeFile(photoEntry.filterPath) : getSourceBitmap();
        Bitmap paintedBitmap = BitmapFactory.decodeFile(photoEntry.paintPath);

        Bitmap result = Bitmap.createBitmap(filteredBitmap.getWidth(), filteredBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(filteredBitmap, 0, 0, bitmapPaint);

        Rect dstRect = new Rect();
        dstRect.set(0, 0, filteredBitmap.getWidth(), filteredBitmap.getHeight());

        SegmentedObject object = selectedObject;
        if (object == null && objects.length > 0) {
            object = objects[0];
        }
        if (object == null)
            return null;

        if (object.orientation != 0 && photoEntry.isFiltered) {
            Matrix matrix = new Matrix();
            matrix.postRotate(object.orientation, object.getDarkMaskImage().getWidth() / 2f, object.getDarkMaskImage().getHeight() / 2f);
            if (object.orientation / 90 % 2 != 0) {
                float dxy = (object.getDarkMaskImage().getHeight() - object.getDarkMaskImage().getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(filteredBitmap.getWidth() / (float) object.getDarkMaskImage().getHeight(), filteredBitmap.getHeight() / (float) object.getDarkMaskImage().getWidth());
            canvas.drawBitmap(object.getDarkMaskImage(), matrix, maskPaint);
        } else {
            canvas.drawBitmap(object.getDarkMaskImage(), null, dstRect, maskPaint);
        }

        if (paintedBitmap != null) {
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            if (object.orientation != 0 && !photoEntry.isFiltered) {
                Matrix matrix = new Matrix();
                matrix.postRotate(-object.orientation, paintedBitmap.getWidth() / 2f, paintedBitmap.getHeight() / 2f);
                if (object.orientation / 90 % 2 != 0) {
                    float dxy = (paintedBitmap.getHeight() - paintedBitmap.getWidth()) / 2f;
                    matrix.postTranslate(dxy, -dxy);
                }
                matrix.postScale(filteredBitmap.getWidth() / (float) paintedBitmap.getHeight(), filteredBitmap.getHeight() / (float) paintedBitmap.getWidth());
                canvas.drawBitmap(paintedBitmap, matrix, maskPaint);
            } else {
                canvas.drawBitmap(paintedBitmap, null, dstRect, maskPaint);
            }
        }
        return result;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        float inset = dp(10);
        float width = getMeasuredWidth() - inset * 2;
        float height = getMeasuredHeight() - inset * 2;

        float rx = width / 8f;
        AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + width);
        AndroidUtilities.rectTmp.offset(0, (height - AndroidUtilities.rectTmp.height()) / 2);
        areaPath.rewind();
        areaPath.addRoundRect(AndroidUtilities.rectTmp, rx, rx, Path.Direction.CW);

        bgPath.rewind();
        bgPath.addRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Path.Direction.CW);

        screenPath.reset();
        screenPath.op(bgPath, areaPath, Path.Op.DIFFERENCE);
        dashPath.rewind();
        AndroidUtilities.rectTmp.inset(dp(-1f), dp(-1f));
        dashPath.addRoundRect(AndroidUtilities.rectTmp, rx, rx, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawPath(screenPath, bgPaint);
        canvas.drawPath(dashPath, dashPaint);
    }

    private Bitmap createSmoothEdgesSegmentedImage(int x, int y, Bitmap inputBitmap, boolean full) {
        Bitmap srcBitmap = getSourceBitmap();
        if (inputBitmap == null || inputBitmap.isRecycled() || srcBitmap == null) {
            return null;
        }
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap bluredBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bluredBitmap);
        if (full) {
            canvas.scale((float) bluredBitmap.getWidth() / inputBitmap.getWidth(), (float) bluredBitmap.getHeight() / inputBitmap.getHeight());
            canvas.drawBitmap(inputBitmap, x, y, bitmapPaint);
        } else {
            canvas.drawBitmap(inputBitmap, x, y, bitmapPaint);
        }
        Utilities.stackBlurBitmap(bluredBitmap, 5);

        Bitmap resultBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(srcBitmap, 0, 0, bitmapPaint);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(bluredBitmap, 0, 0, maskPaint);
        Bitmap segmentedImage = resultBitmap;
        bluredBitmap.recycle();
        return segmentedImage;
    }

    public void segmentImage(Bitmap source, int orientation, int containerWidth, int containerHeight, Utilities.Callback<SegmentedObject> whenEmpty) {
        if (containerWidth <= 0) {
            containerWidth = AndroidUtilities.displaySize.x;
        }
        if (containerHeight <= 0) {
            containerHeight = AndroidUtilities.displaySize.y;
        }
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        if (segmentingLoaded) {
            return;
        }
        if (segmentingLoading || source == null) return;
        if (Build.VERSION.SDK_INT < 24) return;
        sourceBitmap = source;
        this.orientation = orientation;
        detectedEmoji = null;
        segment(source, orientation, subjects -> {
            final ArrayList<SegmentedObject> finalObjects = new ArrayList<>();

            Utilities.themeQueue.postRunnable(() -> {
                if (sourceBitmap == null || segmentingLoaded) return;
                Matrix matrix = new Matrix();
                matrix.postScale(1f / sourceBitmap.getWidth(), 1f / sourceBitmap.getHeight());
                matrix.postTranslate(-.5f, -.5f);
                matrix.postRotate(orientation);
                matrix.postTranslate(.5f, .5f);
                if (orientation / 90 % 2 != 0) {
                    matrix.postScale(sourceBitmap.getHeight(), sourceBitmap.getWidth());
                } else {
                    matrix.postScale(sourceBitmap.getWidth(), sourceBitmap.getHeight());
                }
                if (subjects.isEmpty()) {
                    SegmentedObject o = new SegmentedObject();
                    o.bounds.set(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight());
                    o.rotatedBounds.set(o.bounds);
                    matrix.mapRect(o.rotatedBounds);
                    o.orientation = orientation;
                    o.image = createSmoothEdgesSegmentedImage(0, 0, sourceBitmap, false);
                    if (o.image == null) {
                        FileLog.e(new RuntimeException("createSmoothEdgesSegmentedImage failed on empty image"));
                        return;
                    }
                    o.darkMaskImage = o.makeDarkMaskImage();
                    createSegmentImagePath(o, this.containerWidth, this.containerHeight);
                    segmentBorderImageWidth = o.borderImageWidth;
                    segmentBorderImageHeight = o.borderImageHeight;

                    finalObjects.add(o);
                    AndroidUtilities.runOnUIThread(() -> {
                        empty = true;
                        objects = finalObjects.toArray(new SegmentedObject[0]);
                        whenEmpty.run(o);
                    });
                    selectedObject = o;
                    segmentingLoaded = true;
                    segmentingLoading = false;
                    return;
                } else {
                    for (int i = 0; i < subjects.size(); ++i) {
                        SubjectMock subject = subjects.get(i);
                        SegmentedObject o = new SegmentedObject();
                        o.bounds.set(subject.startX, subject.startY, subject.startX + subject.width, subject.startY + subject.height);
                        o.rotatedBounds.set(o.bounds);
                        matrix.mapRect(o.rotatedBounds);
                        o.orientation = orientation;
                        o.image = createSmoothEdgesSegmentedImage(subject.startX, subject.startY, subject.bitmap, false);
                        if (o.image == null) continue;
                        o.darkMaskImage = o.makeDarkMaskImage();
                        createSegmentImagePath(o, this.containerWidth, this.containerHeight);
                        segmentBorderImageWidth = o.borderImageWidth;
                        segmentBorderImageHeight = o.borderImageHeight;

                        finalObjects.add(o);
                    }
                }
                selectedObject = null;

                segmentingLoaded = true;
                segmentingLoading = false;
                AndroidUtilities.runOnUIThread(() -> {
                    empty = false;
                    objects = finalObjects.toArray(new SegmentedObject[0]);
                    if (objects.length > 0) {
                        stickerCutOutBtn.setScaleX(0.3f);
                        stickerCutOutBtn.setScaleY(0.3f);
                        stickerCutOutBtn.setAlpha(0f);
                        stickerCutOutBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                });

            });
        }, whenEmpty);
    }

    private static class SubjectMock {
        public Bitmap bitmap;
        public int startX, startY, width, height;
        public static SubjectMock of(Subject subject) {
            SubjectMock m = new SubjectMock();
            m.bitmap = subject.getBitmap();
            m.startX = subject.getStartX();
            m.startY = subject.getStartY();
            m.width = subject.getWidth();
            m.height = subject.getHeight();
            return m;
        }
        public static SubjectMock mock(Bitmap source) {
            SubjectMock m = new SubjectMock();
            m.width = m.height = (int) (Math.min(source.getWidth(), source.getHeight()) * .4f);
            m.bitmap = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888);
            new Canvas(m.bitmap).drawRect(0, 0, m.width, m.height, Theme.DEBUG_RED);
            m.startX = (source.getWidth() - m.width) / 2;
            m.startY = (source.getHeight() - m.height) / 2;
            return m;
        }
    }

    private void segment(Bitmap bitmap, int orientation, Utilities.Callback<List<SubjectMock>> whenDone, Utilities.Callback<SegmentedObject> whenEmpty) {
        segmentingLoading = true;
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(
            new SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableSubjectBitmap()
                        .build()
                )
                .build()
        );
        if (EmuDetector.with(getContext()).detect()) {
            ArrayList<SubjectMock> list = new ArrayList<>();
            list.add(SubjectMock.mock(sourceBitmap));
            whenDone.run(list);
            return;
        }
        InputImage inputImage = InputImage.fromBitmap(bitmap, orientation);
        segmenter.process(inputImage)
            .addOnSuccessListener(result -> {
                ArrayList<SubjectMock> list = new ArrayList<>();
                for (int i = 0; i < result.getSubjects().size(); ++i) {
                    list.add(SubjectMock.of(result.getSubjects().get(i)));
                }
                whenDone.run(list);
            })
            .addOnFailureListener(error -> {
                segmentingLoading = false;
                FileLog.e(error);
                if (isWaitingMlKitError(error) && isAttachedToWindow()) {
                    AndroidUtilities.runOnUIThread(() -> segmentImage(bitmap, orientation, containerWidth, containerHeight, whenEmpty), 2000);
                } else {
                    whenDone.run(new ArrayList<>());
                }
            });


        if (detectedEmoji == null) {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnSuccessListener(labels -> {
                    if (labels.size() <= 0) {
                        FileLog.d("objimg: no objects");
                        return;
                    }
                    detectedEmoji = ObjectDetectionEmojis.labelToEmoji(labels.get(0).getIndex());
                    FileLog.d("objimg: detected #" + labels.get(0).getIndex() + " " + detectedEmoji + " " + labels.get(0).getText());
                    Emoji.getEmojiDrawable(detectedEmoji); // preload
                })
                .addOnFailureListener(e -> {
                });
        }

        // preload emojis
        List<TLRPC.TL_availableReaction> defaultReactions = MediaDataController.getInstance(currentAccount).getEnabledReactionsList();
        for (int i = 0; i < Math.min(defaultReactions.size(), 9); ++i) {
            Emoji.getEmojiDrawable(defaultReactions.get(i).reaction);
        }
    }

    private void createSegmentImagePath(SegmentedObject object, int containerWidth, int containerHeight) {
        int imageWidth = object.getImage().getWidth();
        int imageHeight = object.getImage().getHeight();
        int maxImageSize = Math.max(imageWidth, imageHeight);
        float scaleFactor = maxImageSize / (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 512f : 384f);

        if (object.orientation / 90 % 2 != 0) {
            imageWidth = object.getImage().getHeight();
            imageHeight = object.getImage().getWidth();
        }

        Bitmap bitmap = Bitmap.createBitmap((int) (imageWidth / scaleFactor), (int) (imageHeight / scaleFactor), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF rectF = new RectF();
        rectF.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (object.orientation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(object.orientation, object.getImage().getWidth() / 2f, object.image.getHeight() / 2f);
            if (object.orientation / 90 % 2 != 0) {
                float dxy = (object.getImage().getHeight() - object.getImage().getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(rectF.width() / imageWidth, rectF.height() / imageHeight);
            canvas.drawBitmap(object.getImage(), matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        } else {
            canvas.drawBitmap(object.getImage(), null, rectF, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }

        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        ArrayList<Point> leftPoints = new ArrayList<>();
        ArrayList<Point> rightPoints = new ArrayList<>();
        Point leftPoint = null;
        Point rightPoint = null;

        scaleFactor = Math.min(
            containerWidth / (float) bitmap.getWidth(),
            containerHeight / (float) bitmap.getHeight()
        );
        for (int i = 0; i < pixels.length; i++) {
            int y = i / bitmap.getWidth();
            int x = i - y * bitmap.getWidth();

            boolean hasColor = pixels[i] != 0;
            if (pixels[i] == 0) {
                boolean hasLeft = i - 1 >= 0;
                boolean hasRight = i + 1 < pixels.length;
                if (hasLeft && pixels[i - 1] != 0) {
                    rightPoint = new Point(x, y, scaleFactor);
                }
                if (leftPoint == null && hasRight && pixels[i + 1] != 0) {
                    leftPoint = new Point(x, y, scaleFactor);
                }
            }
            boolean isLastPixelInX = x == bitmap.getWidth() - 1;
            boolean isFirstPixelInX = x == 0;
            if (isLastPixelInX) {
                if (hasColor) {
                    rightPoint = new Point(x, y, scaleFactor);
                }
                if (leftPoint != null) leftPoints.add(leftPoint);
                if (rightPoint != null) rightPoints.add(rightPoint);
                leftPoint = null;
                rightPoint = null;
            }
            if (isFirstPixelInX) {
                if (hasColor) {
                    leftPoint = new Point(x, y, scaleFactor);
                }
            }
        }

        ArrayList<Point> topPoints = new ArrayList<>();
        ArrayList<Point> bottomPoints = new ArrayList<>();
        Point topPoint = null;
        Point bottomPoint = null;
        for (int i = 0; i < pixels.length; i++) {
            int x = i / bitmap.getHeight();
            int y = i - x * bitmap.getHeight();
            boolean hasColor = pixels[x + y * bitmap.getWidth()] != 0;
            if (!hasColor) {
                int topPos = x + (y - 1) * bitmap.getWidth();
                int bottomPos = x + (y + 1) * bitmap.getWidth();
                boolean hasTop = topPos >= 0;
                boolean hasBottom = bottomPos < pixels.length;
                if (hasTop && pixels[topPos] != 0) {
                    bottomPoint = new Point(x, y, scaleFactor);
                }
                if (topPoint == null && hasBottom && pixels[bottomPos] != 0) {
                    topPoint = new Point(x, y, scaleFactor);
                }
            }
            boolean isLastPixelInY = y == bitmap.getHeight() - 1;
            boolean isFirstPixelInY = y == 0;
            if (isLastPixelInY) {
                if (hasColor) {
                    bottomPoint = new Point(x, y, scaleFactor);
                }
                if (topPoint != null) topPoints.add(topPoint);
                if (bottomPoint != null) bottomPoints.add(bottomPoint);
                topPoint = null;
                bottomPoint = null;
            }
            if (isFirstPixelInY) {
                if (hasColor) {
                    topPoint = new Point(x, y, scaleFactor);
                }
            }
        }

        HashSet<Point> topBottomPointsSet = new LinkedHashSet<>();
        HashSet<Point> leftRightPointsSet = new LinkedHashSet<>();
        Collections.reverse(rightPoints);
        Collections.reverse(topPoints);

        leftRightPointsSet.addAll(leftPoints);
        leftRightPointsSet.addAll(rightPoints);

        topBottomPointsSet.addAll(bottomPoints);
        topBottomPointsSet.addAll(topPoints);

        List<Point> topBottomPointsList = removeUnnecessaryPoints(new ArrayList<>(topBottomPointsSet));
        List<Point> leftRightPointsList = removeUnnecessaryPoints(new ArrayList<>(leftRightPointsSet));

        Path path1 = new Path();
        for (int i = 0; i < leftRightPointsList.size(); i += 2) {
            Point point = leftRightPointsList.get(i);
            if (path1.isEmpty()) {
                path1.moveTo(point.x, point.y);
            } else {
                path1.lineTo(point.x, point.y);
            }
        }

        Path path2 = new Path();
        for (int i = 0; i < topBottomPointsList.size(); i += 2) {
            Point point = topBottomPointsList.get(i);
            if (path2.isEmpty()) {
                path2.moveTo(point.x, point.y);
            } else {
                path2.lineTo(point.x, point.y);
            }
        }

        object.segmentBorderPath.reset();
        object.segmentBorderPath.op(path1, path2, Path.Op.INTERSECT);
        scaleFactor = Math.min(
            containerWidth / (float) imageWidth,
            containerHeight / (float) imageHeight
        );
        object.borderImageWidth = imageWidth * scaleFactor;
        object.borderImageHeight = imageHeight * scaleFactor;
        object.segmentBorderPath.offset(-object.borderImageWidth / 2f, -object.borderImageHeight / 2f);
        object.initPoints();
    }

    public static List<Point> removeUnnecessaryPoints(List<Point> points) {
        if (points.size() < 3) return points;

        List<Point> optimizedPoints = new ArrayList<>();
        optimizedPoints.add(points.get(0));

        for (int i = 1; i < points.size() - 1; i++) {
            Point prev = points.get(i - 1);
            Point curr = points.get(i);
            Point next = points.get(i + 1);

            if (!isPointOnLine(prev, curr, next)) {
                optimizedPoints.add(curr);
            }
        }

        optimizedPoints.add(points.get(points.size() - 1));
        return optimizedPoints;
    }

    private static boolean isPointOnLine(Point a, Point b, Point c) {
        int crossProduct = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        return Math.abs(crossProduct - (-1.0f)) < 0.15f;
    }

    public Bitmap cutSegmentInFilteredBitmap(Bitmap filteredBitmap, int orientation) {
        if (filteredBitmap == null) {
            return null;
        }
        if (selectedObject == null) {
            return filteredBitmap;
        }
        this.filteredBitmap = filteredBitmap;
        if (selectedObject.darkMaskImage == null || !isSegmentedState) {
            return filteredBitmap;
        }
        Bitmap result = Bitmap.createBitmap(filteredBitmap.getWidth(), filteredBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        canvas.drawBitmap(filteredBitmap, 0, 0, bitmapPaint);
        Rect dstRect = new Rect();
        dstRect.set(0, 0, filteredBitmap.getWidth(), filteredBitmap.getHeight());
        if (selectedObject.orientation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(selectedObject.orientation, selectedObject.getDarkMaskImage().getWidth() / 2f, selectedObject.getDarkMaskImage().getHeight() / 2f);
            if (selectedObject.orientation / 90 % 2 != 0) {
                float dxy = (selectedObject.getImage().getHeight() - selectedObject.getImage().getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(filteredBitmap.getWidth() / (float) selectedObject.getDarkMaskImage().getHeight(), filteredBitmap.getHeight() / (float) selectedObject.getDarkMaskImage().getWidth());
            canvas.drawBitmap(selectedObject.getDarkMaskImage(), matrix, maskPaint);
        } else {
            canvas.drawBitmap(selectedObject.getDarkMaskImage(), null, dstRect, maskPaint);
        }
        return result;
    }

    public void clean() {
        if (bordersAnimator != null) {
            bordersAnimator.cancel();
            bordersAnimator = null;
        }
        sourceBitmap = null;
        if (objects != null) {
            for (int i = 0; i < objects.length; ++i) {
                if (objects[i] != null) {
                    objects[i].recycle();
                }
            }
            objects = null;
        }
        segmentingLoaded = false;
        segmentingLoading = false;
        isSegmentedState = false;
        actionTextView.setAlpha(0f);
        actionTextView.setScaleX(0.3f);
        actionTextView.setScaleY(0.3f);
        if (stickerUploader != null) {
            if (!stickerUploader.uploaded)
                stickerUploader.destroy(true);
            stickerUploader = null;
        }
        hideLoadingDialog();
        isThanosInProgress = false;
    }

    public static boolean isWaitingMlKitError(Exception e) {
        return e instanceof MlKitException && e.getMessage() != null && e.getMessage().contains("segmentation optional module to be downloaded");
    }

    public void setCurrentAccount(int account) {
        if (currentAccount != account) {
            if (currentAccount >= 0 && isAttachedToWindow()) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingStarted);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
            }

            currentAccount = account;

            if (currentAccount >= 0 && isAttachedToWindow()) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingFailed);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingStarted);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (currentAccount >= 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingStarted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded) {
            final String location = (String) args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile) args[1];
            if (stickerUploader != null && location.equalsIgnoreCase(stickerUploader.finalPath)) {
                stickerUploader.file = file;
                uploadMedia();
            }
        } else if (id == NotificationCenter.fileUploadProgressChanged) {
            String location = (String) args[0];
            if (stickerUploader != null && location.equalsIgnoreCase(stickerUploader.finalPath)) {
                final long uploadedSize = (long) args[1];
                final long totalSize = (long) args[2];
                if (totalSize > 0) {
                    stickerUploader.uploadProgress = Utilities.clamp(uploadedSize / (float) totalSize, 1, stickerUploader.uploadProgress);
                    if (loadingToast != null) {
                        loadingToast.setProgress(stickerUploader.getProgress());
                    }
                }
            }
        } else if (id == NotificationCenter.fileUploadFailed) {
            String location = (String) args[0];
            if (stickerUploader != null && location.equalsIgnoreCase(stickerUploader.finalPath)) {
                hideLoadingDialog();
            }
        } else if (id == NotificationCenter.filePreparingStarted) {
            if (stickerUploader == null) return;
            if (args[0] == stickerUploader.messageObject) {
                FileLoader.getInstance(UserConfig.selectedAccount).uploadFile(stickerUploader.finalPath, false, true, ConnectionsManager.FileTypeFile);
            }
        } else if (id == NotificationCenter.fileNewChunkAvailable) {
            if (stickerUploader == null) return;
            if (args[0] == stickerUploader.messageObject) {
                String finalPath = (String) args[1];
                long availableSize = (Long) args[2];
                long finalSize = (Long) args[3];
                float convertingProgress = (float) args[4];

                stickerUploader.messageObject.videoEditedInfo.needUpdateProgress = true;
                FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(finalPath, false, Math.max(1, availableSize), finalSize, convertingProgress);

                stickerUploader.convertingProgress = Math.max(stickerUploader.convertingProgress, convertingProgress);
                if (loadingToast != null) {
                    loadingToast.setProgress(stickerUploader.getProgress());
                }
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            if (stickerUploader == null) return;
            if (args[0] == stickerUploader.messageObject) {
                hideLoadingDialog();
            }
        }
    }

    public void uploadStickerFile(String path, VideoEditedInfo videoEditedInfo, String emoji, CharSequence stickerPackName, boolean addToFavorite, TLRPC.StickerSet stickerSet, TLRPC.Document replacedSticker, String thumbPath, Utilities.Callback<Boolean> whenDone, Utilities.Callback2<String, TLRPC.InputDocument> customStickerHandler) {
        AndroidUtilities.runOnUIThread(() -> {
            final boolean newStickerUploader = !(whenDone != null && stickerUploader != null && stickerUploader.uploaded);
            if (newStickerUploader) {
                if (stickerUploader != null) {
                    stickerUploader.destroy(true);
                }
                stickerUploader = new StickerUploader();
            }
            stickerUploader.emoji = emoji;
            stickerUploader.path = stickerUploader.finalPath = path;
            stickerUploader.stickerPackName = stickerPackName;
            stickerUploader.addToFavorite = addToFavorite;
            stickerUploader.stickerSet = stickerSet;
            stickerUploader.replacedSticker = replacedSticker;
            stickerUploader.videoEditedInfo = videoEditedInfo;
            stickerUploader.thumbPath = thumbPath;
            stickerUploader.whenDone = whenDone;
            stickerUploader.customHandler = customStickerHandler;
            stickerUploader.setupFiles();
            if (!newStickerUploader) {
                afterUploadingMedia();
            } else if (videoEditedInfo != null) {
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.id = 1;
                stickerUploader.finalPath = message.attachPath = StoryEntry.makeCacheFile(UserConfig.selectedAccount, "webm").getAbsolutePath();
                stickerUploader.messageObject = new MessageObject(UserConfig.selectedAccount, message, (MessageObject) null, false, false);
                stickerUploader.messageObject.videoEditedInfo = videoEditedInfo;
                MediaController.getInstance().scheduleVideoConvert(stickerUploader.messageObject, false, false, false);
            } else {
                FileLoader.getInstance(currentAccount).uploadFile(path, false, true, ConnectionsManager.FileTypeFile);
            }
            if (whenDone == null) {
                showLoadingDialog();
            }
        }, 300);
    }

    private void showLoadingDialog() {
        if (loadingToast == null) {
            loadingToast = new DownloadButton.PreparingVideoToast(getContext());
        }
        loadingToast.setOnCancelListener(() -> {
            if (stickerUploader != null) {
                if (stickerUploader.messageObject != null) {
                    MediaController.getInstance().cancelVideoConvert(stickerUploader.messageObject);
                    FileLoader.getInstance(currentAccount).cancelFileUpload(stickerUploader.finalPath, false);
                    if (stickerUploader.reqId != 0) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(stickerUploader.reqId, true);
                    }
                }
                stickerUploader.destroy(true);
                stickerUploader = null;
            }
            loadingToast.hide();
            loadingToast = null;
        });
        if (loadingToast.getParent() == null) {
            addView(loadingToast, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
        loadingToast.show();
    }

    private void hideLoadingDialog() {
        if (loadingToast != null) {
            loadingToast.hide();
            loadingToast = null;
        }
    }

    private void uploadMedia() {
        final StickerUploader stickerUploader = this.stickerUploader;
        if (stickerUploader == null)
            return;
        TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
        req.peer = new TLRPC.TL_inputPeerSelf();
        req.media = new TLRPC.TL_inputMediaUploadedDocument();
        req.media.file = stickerUploader.file;
        if (stickerUploader.videoEditedInfo != null) {
            req.media.mime_type = "video/webm";
        } else {
            req.media.mime_type = "image/webp";
        }
        TLRPC.TL_documentAttributeSticker attr = new TLRPC.TL_documentAttributeSticker();
        attr.alt = stickerUploader.emoji;
        attr.stickerset = new TLRPC.TL_inputStickerSetEmpty();
        req.media.attributes.add(attr);
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.TL_messageMediaDocument mediaDocument = (TLRPC.TL_messageMediaDocument) response;
                stickerUploader.tlInputStickerSetItem = MediaDataController.getInputStickerSetItem(mediaDocument.document, stickerUploader.emoji);
                stickerUploader.mediaDocument = mediaDocument;
                afterUploadingMedia();
            } else {
                hideLoadingDialog();
                showError(error);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void showError(TLRPC.TL_error error) {
        if (error != null) {
            if ("PACK_TITLE_INVALID".equals(error.text)) {
                return;
            }
            BulletinFactory.of((FrameLayout) getParent(), resourcesProvider).createErrorBulletin(error.text).show();
        }
    }

    private void afterUploadingMedia() {
        final StickerUploader stickerUploader = this.stickerUploader;
        if (stickerUploader == null) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        stickerUploader.uploaded = true;
        if (stickerUploader.customHandler != null) {
            hideLoadingDialog();
            stickerUploader.customHandler.run(stickerUploader.finalPath, stickerUploader.tlInputStickerSetItem.document);
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated), 250);
            return;
        }
        if (stickerUploader.replacedSticker != null) {
            TLRPC.TL_stickers_replaceSticker req = new TLRPC.TL_stickers_replaceSticker();
            req.sticker = MediaDataController.getInputStickerSetItem(stickerUploader.replacedSticker, stickerUploader.emoji).document;
            req.new_sticker = stickerUploader.tlInputStickerSetItem;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean success = false;
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                    MediaDataController.getInstance(currentAccount).putStickerSet(set);
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(set.set.id)) {
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    }
                    if (loadingToast != null) {
                        loadingToast.setProgress(1f);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document, stickerUploader.thumbPath, true);
                        hideLoadingDialog();
                    }, 450);
                    success = true;
                } else {
                    showError(error);
                    hideLoadingDialog();
                }
                if (stickerUploader.whenDone != null) {
                    stickerUploader.whenDone.run(success);
                    stickerUploader.whenDone = null;
                }
            }));
        } else if (stickerUploader.stickerPackName != null) {
            TLRPC.TL_stickers_createStickerSet req = new TLRPC.TL_stickers_createStickerSet();
            req.user_id = new TLRPC.TL_inputUserSelf();
            req.title = stickerUploader.stickerPackName.toString();
            req.short_name = "";
            req.stickers.add(stickerUploader.tlInputStickerSetItem);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean success = false;
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                    MediaDataController.getInstance(currentAccount).putStickerSet(set);
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    if (loadingToast != null) {
                        loadingToast.setProgress(1f);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document, stickerUploader.thumbPath, false);
                        hideLoadingDialog();
                    }, 250);
                    success = true;
                } else {
                    showError(error);
                    hideLoadingDialog();
                }
                if (stickerUploader.whenDone != null) {
                    stickerUploader.whenDone.run(success);
                    stickerUploader.whenDone = null;
                }
            }));
        } else if (stickerUploader.addToFavorite) {
            hideLoadingDialog();
            NotificationCenter.getInstance(currentAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false);
            AndroidUtilities.runOnUIThread(() -> MediaDataController.getInstance(UserConfig.selectedAccount).addRecentSticker(MediaDataController.TYPE_FAVE, null, stickerUploader.mediaDocument.document, (int) (System.currentTimeMillis() / 1000), false), 350);
            if (stickerUploader.whenDone != null) {
                stickerUploader.whenDone.run(true);
            }
        } else if (stickerUploader.stickerSet != null) {
            TLRPC.TL_stickers_addStickerToSet req = new TLRPC.TL_stickers_addStickerToSet();
            req.stickerset = MediaDataController.getInputStickerSet(stickerUploader.stickerSet);
            req.sticker = stickerUploader.tlInputStickerSetItem;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean success = false;
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                    MediaDataController.getInstance(currentAccount).putStickerSet(set);
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(set.set.id)) {
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    }
                    if (loadingToast != null) {
                        loadingToast.setProgress(1f);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document, stickerUploader.thumbPath, false);
                        hideLoadingDialog();
                    }, 450);
                    success = true;
                } else {
                    showError(error);
                    hideLoadingDialog();
                }
                if (stickerUploader.whenDone != null) {
                    stickerUploader.whenDone.run(success);
                    stickerUploader.whenDone = null;
                }
            }));
        }
    }

    public static class StickerUploader {
        public String path;
        public String finalPath;
        public String emoji;
        public CharSequence stickerPackName;
        public TLRPC.TL_inputStickerSetItem tlInputStickerSetItem;
        public TLRPC.TL_messageMediaDocument mediaDocument;
        public TLRPC.InputFile file;
        public boolean addToFavorite;
        public TLRPC.StickerSet stickerSet;
        public TLRPC.Document replacedSticker;
        public String thumbPath;
        public Utilities.Callback2<String, TLRPC.InputDocument> customHandler;
        public Utilities.Callback<Boolean> whenDone;
        public boolean uploaded;

        public ArrayList<File> finalFiles = new ArrayList<File>();
        public ArrayList<File> files = new ArrayList<File>();

        public MessageObject messageObject;
        public VideoEditedInfo videoEditedInfo;
        public int reqId;

        private float convertingProgress = 0, uploadProgress = 0;
        public float getProgress() {
            final float maxPercent = customHandler == null ? .9f : 1f;
            if (videoEditedInfo == null) {
                return maxPercent * uploadProgress;
            }
            return maxPercent * (.5f * convertingProgress + .5f * uploadProgress);
        }

        public void setupFiles() {
            if (!TextUtils.isEmpty(finalPath)) {
                finalFiles.add(new File(finalPath));
            }
            if (!TextUtils.isEmpty(path) && !TextUtils.equals(path, finalPath)) {
                files.add(new File(path));
            }
            if (!TextUtils.isEmpty(thumbPath)) {
                files.add(new File(thumbPath));
            }
        }

        public void destroy(boolean all) {
            if (all) {
                for (File file : finalFiles) {
                    try {
                        file.delete();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            finalFiles.clear();
            for (File file : files) {
                try {
                    file.delete();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            files.clear();
        }
    }

    private static class Point extends android.graphics.Point {

        public Point(int x, int y) {
            super(x, y);
        }

        public Point(int x, int y, float scale) {
            super((int) (x * scale), (int) (y * scale));
        }
    }
}
