package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.Subject;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
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
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

@SuppressLint("ViewConstructor")
public class StickerMakerView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public int currentAccount = -1;
    private final AnimatedFloat segmentBorderAlpha = new AnimatedFloat(0, (View) null, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
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
    public SegmentedObject[] objects;
    private volatile Bitmap sourceBitmap;
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

    private final Matrix imageReceiverMatrix = new Matrix();
    private float imageReceiverWidth, imageReceiverHeight;

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
        actionTextView.setText(LocaleController.getString(R.string.SegmentationTabToCrop));
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
        borderPaint.setPathEffect(new CornerPathEffect(dp(6)));
        borderPaint.setMaskFilter(new BlurMaskFilter(dp(4), BlurMaskFilter.Blur.NORMAL));

        segmentBorderPaint.setColor(Color.WHITE);
        segmentBorderPaint.setStrokeWidth(dp(3));
        segmentBorderPaint.setStyle(Paint.Style.STROKE);
        segmentBorderPaint.setStrokeCap(Paint.Cap.ROUND);
        segmentBorderPaint.setPathEffect(new CornerPathEffect(dp(6)));
        segmentBorderPaint.setMaskFilter(new BlurMaskFilter(dp(4), BlurMaskFilter.Blur.NORMAL));

        bgPaint.setColor(0x66000000);
        setLayerType(LAYER_TYPE_HARDWARE, null);
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
        public Bitmap darkMaskImage;

        public RectF bounds = new RectF();
        public RectF rotatedBounds = new RectF();

        private float borderImageWidth;
        private float borderImageHeight;

        private final Path segmentBorderPath = new Path();
        private final Path partSegmentBorderPath = new Path();

        public void drawBorders(Canvas canvas, float progress, float alpha, View parent) {
            select.setParent(parent);
            if (sourceBitmap == null) return;

            final float s = AndroidUtilities.lerp(1f, 1.065f, alpha) * AndroidUtilities.lerp(1f, 1.05f, select.set(hover));

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

            if (image != null) {
                canvas.save();
                canvas.rotate(orientation);
                canvas.scale(1f / w * borderImageWidth, 1f / h * borderImageHeight);
                canvas.drawBitmap(image, -sourceBitmap.getWidth() / 2f, -sourceBitmap.getHeight() / 2f, null);
                canvas.restore();
            }
            canvas.restore();
        }

        public void recycle() {
            segmentBorderPath.reset();
            if (image != null) {
                image.recycle();
                image = null;
            }
            if (darkMaskImage != null) {
                darkMaskImage.recycle();
                darkMaskImage = null;
            }
        }
    }


    public void drawSegmentBorderPath(Canvas canvas, ImageReceiver imageReceiver, Matrix matrix, ViewGroup parent) {
        segmentBorderAlpha.setParent(parent);
        if ((bordersAnimator == null && segmentBorderAlpha.get() <= 0) || parent == null) {
            return;
        }

        imageReceiverWidth = imageReceiver.getImageWidth();
        imageReceiverHeight = imageReceiver.getImageHeight();
        matrix.invert(imageReceiverMatrix);

        float progress = (bordersAnimatorValueStart + bordersAnimatorValue) % 1.0f;
        float alpha = segmentBorderAlpha.set(bordersAnimator == null ? 0f : 1f);

        canvas.drawColor(Theme.multAlpha(0x50000000, alpha));
        if (objects != null) {
            for (SegmentedObject object : objects) {
                object.drawBorders(canvas, progress, alpha, parent);
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
        float[] p = new float[] { tx, ty };
        imageReceiverMatrix.mapPoints(p);
        int w, h;
        if (objects[0].orientation / 90 % 2 != 0) {
            w = sourceBitmap.getHeight();
            h = sourceBitmap.getWidth();
        } else {
            w = sourceBitmap.getWidth();
            h = sourceBitmap.getHeight();
        }
        for (int i = 0; i < objects.length; ++i) {
            AndroidUtilities.rectTmp.set(
                objects[i].rotatedBounds.left / w * imageReceiverWidth,
                objects[i].rotatedBounds.top / h * imageReceiverHeight,
                objects[i].rotatedBounds.right / w * imageReceiverWidth,
                objects[i].rotatedBounds.bottom / h * imageReceiverHeight
            );
            AndroidUtilities.rectTmp.offset(-imageReceiverWidth / 2f, -imageReceiverHeight / 2f);
            if (AndroidUtilities.rectTmp.contains(p[0], p[1])) {
                return objects[i];
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        actionTextView.setTranslationY(getMeasuredWidth() / 2f + dp(10));
    }

    public boolean isSegmentedState() {
        return isSegmentedState;
    }

    public void setSegmentedState(boolean segmentedState, SegmentedObject selectedObject) {
        isSegmentedState = segmentedState;
        this.selectedObject = selectedObject;
    }

    public Bitmap getSegmentedDarkMaskImage() {
        return isSegmentedState && selectedObject != null ? selectedObject.darkMaskImage : null;
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
        return selectedObject.image;
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
            matrix.postRotate(object.orientation, object.darkMaskImage.getWidth() / 2f, object.darkMaskImage.getHeight() / 2f);
            if (object.orientation / 90 % 2 != 0) {
                float dxy = (object.darkMaskImage.getHeight() - object.darkMaskImage.getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(filteredBitmap.getWidth() / (float) object.darkMaskImage.getHeight(), filteredBitmap.getHeight() / (float) object.darkMaskImage.getWidth());
            canvas.drawBitmap(object.darkMaskImage, matrix, maskPaint);
        } else {
            canvas.drawBitmap(object.darkMaskImage, null, dstRect, maskPaint);
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

    private Bitmap createSmoothEdgesSegmentedImage(int x, int y, Bitmap inputBitmap) {
        Bitmap srcBitmap = getSourceBitmap();
        if (inputBitmap == null || srcBitmap == null) {
            return null;
        }
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap bluredBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bluredBitmap);
        canvas.drawBitmap(inputBitmap, x, y, bitmapPaint);
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

    public void segmentImage(Bitmap source, int orientation, int containerWidth, int containerHeight) {
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        if (segmentingLoaded) {
            return;
        }
        if (segmentingLoading || source == null) return;
        if (Build.VERSION.SDK_INT < 24) return;
        SubjectSegmenter segmenter = SubjectSegmentation.getClient(
            new SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    new SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableSubjectBitmap()
                        .build()
                )
                .build()
        );
        segmentingLoading = true;
        sourceBitmap = source;
        InputImage inputImage = InputImage.fromBitmap(source, orientation);
        segmenter.process(inputImage)
                .addOnSuccessListener(result -> {
                    if (sourceBitmap == null) return;
                    final ArrayList<SegmentedObject> finalObjects = new ArrayList<>();
                    Utilities.themeQueue.postRunnable(() -> {
                        if (sourceBitmap == null) return;
                        List<Subject> subjects = result.getSubjects();
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
                        for (int i = 0; i < subjects.size(); ++i) {
                            Subject subject = subjects.get(i);
                            SegmentedObject o = new SegmentedObject();
                            o.bounds.set(subject.getStartX(), subject.getStartY(), subject.getStartX() + subject.getWidth(), subject.getStartY() + subject.getHeight());
                            o.rotatedBounds.set(o.bounds);
                            matrix.mapRect(o.rotatedBounds);
                            o.orientation = orientation;
                            o.image = createSmoothEdgesSegmentedImage(subject.getStartX(), subject.getStartY(), subject.getBitmap());
                            if (o.image == null) continue;

                            o.darkMaskImage = Bitmap.createBitmap(o.image.getWidth(), o.image.getHeight(), Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(o.darkMaskImage);
                            canvas.drawColor(Color.BLACK);
                            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                            canvas.drawBitmap(o.image, 0, 0, maskPaint);

                            createSegmentImagePath(o, containerWidth, containerHeight);
                            segmentBorderImageWidth = o.borderImageWidth;
                            segmentBorderImageHeight = o.borderImageHeight;

                            finalObjects.add(o);
                        }
                        selectedObject = null;

                        segmentingLoaded = true;
                        segmentingLoading = false;
                        AndroidUtilities.runOnUIThread(() -> {
                            objects = finalObjects.toArray(new SegmentedObject[1]);
                            if (objects.length > 0) {
                                stickerCutOutBtn.setScaleX(0.3f);
                                stickerCutOutBtn.setScaleY(0.3f);
                                stickerCutOutBtn.setAlpha(0f);
                                stickerCutOutBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                            }
                        });

                    });
                })
                .addOnFailureListener(error -> {
                    segmentingLoading = false;
                    FileLog.e(error);
                    if (isWaitingMlKitError(error) && isAttachedToWindow()) {
                        AndroidUtilities.runOnUIThread(() -> segmentImage(source, orientation, containerWidth, containerHeight), 2000);
                    } else {
                        segmentingLoaded = true;
                    }
                });
    }

    private void createSegmentImagePath(SegmentedObject object, int containerWidth, int containerHeight) {
        int imageWidth = object.image.getWidth();
        int imageHeight = object.image.getHeight();
        int maxImageSize = Math.max(imageWidth, imageHeight);
        float scaleFactor = maxImageSize / 256f;

        if (object.orientation / 90 % 2 != 0) {
            imageWidth = object.image.getHeight();
            imageHeight = object.image.getWidth();
        }

        Bitmap bitmap = Bitmap.createBitmap((int) (imageWidth / scaleFactor), (int) (imageHeight / scaleFactor), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF rectF = new RectF();
        rectF.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (object.orientation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(object.orientation, object.image.getWidth() / 2f, object.image.getHeight() / 2f);
            if (object.orientation / 90 % 2 != 0) {
                float dxy = (object.image.getHeight() - object.image.getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(rectF.width() / imageWidth, rectF.height() / imageHeight);
            canvas.drawBitmap(object.image, matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        } else {
            canvas.drawBitmap(object.image, null, rectF, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }

        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        ArrayList<Point> leftPoints = new ArrayList<>();
        ArrayList<Point> rightPoints = new ArrayList<>();
        Point leftPoint = null;
        Point rightPoint = null;

        scaleFactor = containerWidth / (float) bitmap.getWidth();
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

        List<Point> topBottomPointsList = new ArrayList<>(topBottomPointsSet);
        List<Point> leftRightPointsList = new ArrayList<>(leftRightPointsSet);

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
    }

    public Bitmap cutSegmentInFilteredBitmap(Bitmap filteredBitmap, int orientation) {
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
            matrix.postRotate(selectedObject.orientation, selectedObject.darkMaskImage.getWidth() / 2f, selectedObject.darkMaskImage.getHeight() / 2f);
            if (selectedObject.orientation / 90 % 2 != 0) {
                float dxy = (selectedObject.image.getHeight() - selectedObject.image.getWidth()) / 2f;
                matrix.postTranslate(dxy, -dxy);
            }
            matrix.postScale(filteredBitmap.getWidth() / (float) selectedObject.darkMaskImage.getHeight(), filteredBitmap.getHeight() / (float) selectedObject.darkMaskImage.getWidth());
            canvas.drawBitmap(selectedObject.darkMaskImage, matrix, maskPaint);
        } else {
            canvas.drawBitmap(selectedObject.darkMaskImage, null, dstRect, maskPaint);
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
                objects[i].recycle();
            }
            objects = null;
        }
        segmentingLoaded = false;
        segmentingLoading = false;
        isSegmentedState = false;
        actionTextView.setAlpha(0f);
        actionTextView.setScaleX(0.3f);
        actionTextView.setScaleY(0.3f);
    }

    public static boolean isWaitingMlKitError(Exception e) {
        return e instanceof MlKitException && e.getMessage() != null && e.getMessage().contains("segmentation optional module to be downloaded");
    }

    public void setCurrentAccount(int account) {
        if (currentAccount != account) {
            if (currentAccount >= 0) {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingStarted);
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
            }

            currentAccount = account;

            if (currentAccount >= 0) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
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
//                progress = convertingProgress * .3f + uploadProgress * .7f;
//                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.uploadStoryProgress, path, progress);

//                if (firstSecondSize < 0 && convertingProgress * duration >= 1000) {
//                    firstSecondSize = availableSize;
//                }

                FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(finalPath, false, Math.max(1, availableSize), finalSize, convertingProgress);

//                if (finalSize > 0) {
//                    if (firstSecondSize < 0) {
//                        firstSecondSize = finalSize;
//                    }
//                    ready = true;
//                }
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            if (stickerUploader == null) return;
            if (args[0] == stickerUploader.messageObject) {
                hideLoadingDialog();
            }
        }
    }

    public void uploadStickerFile(String path, VideoEditedInfo videoEditedInfo, String emoji, CharSequence stickerPackName, boolean addToFavorite, TLRPC.StickerSet stickerSet, TLRPC.Document replacedSticker) {
        AndroidUtilities.runOnUIThread(() -> {
            stickerUploader = new StickerUploader();
            stickerUploader.emoji = emoji;
            stickerUploader.path = stickerUploader.finalPath = path;
            stickerUploader.stickerPackName = stickerPackName;
            stickerUploader.addToFavorite = addToFavorite;
            stickerUploader.stickerSet = stickerSet;
            stickerUploader.replacedSticker = replacedSticker;
            stickerUploader.videoEditedInfo = videoEditedInfo;
            if (videoEditedInfo != null) {
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.id = 1;
                stickerUploader.finalPath = message.attachPath = StoryEntry.makeCacheFile(UserConfig.selectedAccount, "webm").getAbsolutePath();
                stickerUploader.messageObject = new MessageObject(UserConfig.selectedAccount, message, (MessageObject) null, false, false);
                stickerUploader.messageObject.videoEditedInfo = videoEditedInfo;
                MediaController.getInstance().scheduleVideoConvert(stickerUploader.messageObject, false, false);
            } else {
                FileLoader.getInstance(UserConfig.selectedAccount).uploadFile(path, false, true, ConnectionsManager.FileTypeFile);
            }
            showLoadingDialog();
        }, 300);
    }

    private void showLoadingDialog() {
        loadingDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, new DarkThemeResourceProvider());
        loadingDialog.show();
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    private void uploadMedia() {
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
            BulletinFactory.of((FrameLayout) getParent(), resourcesProvider).createErrorBulletin(error.text).show();
        }
    }

    private void afterUploadingMedia() {
        final int currentAccount = UserConfig.selectedAccount;
        if (stickerUploader.replacedSticker != null) {
            TLRPC.TL_stickers_replaceSticker req = new TLRPC.TL_stickers_replaceSticker();
            req.sticker = MediaDataController.getInputStickerSetItem(stickerUploader.replacedSticker, "").document;
            req.new_sticker = stickerUploader.tlInputStickerSetItem;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document), 250);
                }
                showError(error);
                hideLoadingDialog();
            }));
        } else if (stickerUploader.stickerPackName != null) {
            TLRPC.TL_stickers_createStickerSet req = new TLRPC.TL_stickers_createStickerSet();
            req.user_id = new TLRPC.TL_inputUserSelf();
            req.title = stickerUploader.stickerPackName.toString();
            req.short_name = "";
            req.stickers.add(stickerUploader.tlInputStickerSetItem);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document), 250);
                }
                showError(error);
                hideLoadingDialog();
            }));
        } else if (stickerUploader.addToFavorite) {
            hideLoadingDialog();
            NotificationCenter.getInstance(currentAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false);
            AndroidUtilities.runOnUIThread(() -> MediaDataController.getInstance(UserConfig.selectedAccount).addRecentSticker(MediaDataController.TYPE_FAVE, null, stickerUploader.mediaDocument.document, (int) (System.currentTimeMillis() / 1000), false), 350);
        } else if (stickerUploader.stickerSet != null) {
            TLRPC.TL_stickers_addStickerToSet req = new TLRPC.TL_stickers_addStickerToSet();
            req.stickerset = MediaDataController.getInputStickerSet(stickerUploader.stickerSet);
            req.sticker = stickerUploader.tlInputStickerSetItem;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(null, response, 2, null, false, false);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, stickerUploader.mediaDocument.document), 250);
                }
                showError(error);
                hideLoadingDialog();
            }));
        }
    }

    private static class StickerUploader {
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

        public MessageObject messageObject;
        public VideoEditedInfo videoEditedInfo;
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
