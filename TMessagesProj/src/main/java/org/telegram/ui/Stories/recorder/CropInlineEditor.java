package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.Crop.CropRotationWheel;
import org.telegram.ui.Components.Crop.CropTransform;
import org.telegram.ui.Components.Crop.CropView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Views.PhotoView;

public class CropInlineEditor extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final PreviewView previewContainer;
    private PhotoView photoView;

    private final AnimatedFloat animatedMirror;
    private int lastOrientation = 0;
    private final AnimatedFloat animatedOrientation;
    public final ContentView contentView;
    public final FrameLayout controlsLayout;

    public final CropView cropView;
    public final CropRotationWheel wheel;

    public final FrameLayout buttonsLayout;
    public final TextView cancelButton;
    public final TextView resetButton;
    public final TextView cropButton;

    public final LinearLayout shapesLayout;

    private int getCurrentWidth() {
        if (photoView == null) return 1;
        return photoView.getOrientation() == 90 || photoView.getOrientation() == 270 ? photoView.getContentHeight() : photoView.getContentWidth();
    }

    private int getCurrentHeight() {
        if (photoView == null) return 1;
        return photoView.getOrientation() == 90 || photoView.getOrientation() == 270 ? photoView.getContentWidth() : photoView.getContentHeight();
    }

    public CropInlineEditor(Context context, PreviewView previewView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.previewContainer = previewView;
        this.resourcesProvider = resourcesProvider;

        contentView = new ContentView(context);
        animatedMirror = new AnimatedFloat(contentView, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedOrientation = new AnimatedFloat(contentView, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        cropView = new CropView(context) {
            @Override
            public int getCurrentWidth() {
                return CropInlineEditor.this.getCurrentWidth();
            }
            @Override
            public int getCurrentHeight() {
                return CropInlineEditor.this.getCurrentHeight();
            }
        };
        cropView.setListener(new CropView.CropViewListener() {
            @Override
            public void onChange(boolean reset) {

            }

            @Override
            public void onUpdate() {
                contentView.invalidate();
            }

            @Override
            public void onAspectLock(boolean enabled) {

            }

            @Override
            public void onTapUp() {

            }
        });
        addView(cropView);

        controlsLayout = new FrameLayout(context);
        addView(controlsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        wheel = new CropRotationWheel(context);
        wheel.setListener(new CropRotationWheel.RotationWheelListener() {
            @Override
            public void onStart() {
                cropView.onRotationBegan();
            }

            @Override
            public void onChange(float angle) {
                cropView.setRotation(angle);
            }

            @Override
            public void onEnd(float angle) {
                cropView.onRotationEnded();
            }

            @Override
            public void aspectRatioPressed() {
                cropView.showAspectRatioDialog();
            }

            @Override
            public boolean rotate90Pressed() {
                boolean r = cropView.rotate(-90);
                cropView.maximize(true);
                contentView.invalidate();
                return r;
            }

            @Override
            public boolean mirror() {
                contentView.invalidate();
                return cropView.mirror();
            }
        });
        controlsLayout.addView(wheel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.BOTTOM, 0, 0, 0, 52));

        buttonsLayout = new FrameLayout(context);
        controlsLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.BOTTOM, 0, 0, 0, 0));

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTypeface(AndroidUtilities.bold());
        cancelButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        cancelButton.setTextColor(0xFFFFFFFF);
        cancelButton.setText(LocaleController.getString(R.string.Cancel));
        cancelButton.setPadding(dp(12), 0, dp(12), 0);
        buttonsLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL | Gravity.LEFT));
        cancelButton.setOnClickListener(v -> {
            close();
        });

        resetButton = new TextView(context);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetButton.setTypeface(AndroidUtilities.bold());
        resetButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        resetButton.setTextColor(0xFFFFFFFF);
        resetButton.setText(LocaleController.getString(R.string.CropReset));
        resetButton.setPadding(dp(12), 0, dp(12), 0);
        buttonsLayout.addView(resetButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL | Gravity.CENTER_HORIZONTAL));
        resetButton.setOnClickListener(v -> {
            cropView.reset(true);
            wheel.setRotated(false);
            wheel.setMirrored(false);
            wheel.setRotation(0, true);
        });

        cropButton = new TextView(context);
        cropButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cropButton.setTypeface(AndroidUtilities.bold());
        cropButton.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        cropButton.setTextColor(0xff199cff);
        cropButton.setText(LocaleController.getString(R.string.StoryCrop));
        cropButton.setPadding(dp(12), 0, dp(12), 0);
        buttonsLayout.addView(cropButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL | Gravity.RIGHT));
        cropButton.setOnClickListener(v -> {
            apply();
            close();
        });

        shapesLayout = new LinearLayout(context);

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        cropView.setTopPadding(dp(52));
        cropView.setBottomPadding(controlsLayout.getPaddingBottom() + dp(116));
        super.onLayout(changed, left, top, right, bottom);
    }

    private float appearProgress = 0.0f;
    private final int[] thisLocation = new int[2];
    private final int[] previewLocation = new int[2];
    private final int[] photoViewLocation = new int[2];

    private final CropTransform cropTransform = new CropTransform();

    public void set(PhotoView photoView) {
        if (photoView == null) return;
        this.photoView = photoView;
        setVisibility(View.VISIBLE);

        applied = false;
        closing = false;
        cropView.onShow();

        getLocationOnScreen(thisLocation);
        previewContainer.getLocationOnScreen(previewLocation);
        photoView.getLocationOnScreen(photoViewLocation);

        final MediaController.CropState restoreState = photoView.crop != null ? photoView.crop : null;
        cropView.start(photoView.getOrientation(), true, false, cropTransform, restoreState);
        wheel.setRotation(cropView.getRotation());
        if (restoreState != null) {
            wheel.setRotation(restoreState.cropRotate, false);
            wheel.setRotated(restoreState.transformRotation != 0);
            wheel.setMirrored(restoreState.mirrored);
            animatedMirror.set(restoreState.mirrored, false);
        } else {
            wheel.setRotation(0, false);
            wheel.setRotated(false);
            wheel.setMirrored(false);
            animatedMirror.set(false, false);
        }
        cropView.updateMatrix();

        this.contentView.setVisibility(View.VISIBLE);
        this.contentView.invalidate();
    }

    public boolean closing;
    public void disappearStarts() {
        closing = true;
    }

    public void stop() {
        this.photoView = null;
        cropView.stop();
        cropView.onHide();
        contentView.setVisibility(View.GONE);
        setVisibility(View.GONE);
//        previewView.setVisibility(View.VISIBLE);
    }

    public boolean applied;
    public void apply() {
        if (photoView == null) return;

        applied = true;

        photoView.crop = new MediaController.CropState();
        cropView.applyToCropState(photoView.crop);
        photoView.crop.orientation = photoView.getOrientation();
        photoView.updatePosition();
        photoView.requestLayout();
        photoView.containerView.requestLayout();
        photoView.containerView.invalidate();
        photoView.containerView.post(() -> {
            if (photoView.selectionView != null) {
                photoView.selectionView.updatePosition();
            }
            photoView.updatePosition();
        });
    }

    public float getAppearProgress() {
        return appearProgress;
    }

    public void setAppearProgress(float appearProgress) {
        if (Math.abs(this.appearProgress - appearProgress) < 0.001f) return;
        this.appearProgress = appearProgress;
        this.contentView.invalidate();

        cropView.areaView.setDimAlpha(0.5f * appearProgress);
        cropView.areaView.setFrameAlpha(appearProgress);
        cropView.areaView.invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    public class ContentView extends View {
        public ContentView(Context context) {
            super(context);
        }

        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path previewClipPath = new Path();
        private final RectF previewClipRect = new RectF();

        private final Matrix previewMatrix = new Matrix();
        private final Matrix identityMatrix = new Matrix();
        private final Matrix matrix = new Matrix();

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (photoView == null) return;

//            canvas.save();
//            canvas.translate(-thisLocation[0], -thisLocation[1]);
//            canvas.translate(previewLocation[0], previewLocation[1]);
//
//            previewClipPath.rewind();
//            previewClipRect.set(0, 0, previewView.getWidth(), previewView.getHeight());
//            previewClipPath.addRoundRect(previewClipRect, dp(12), dp(12), Path.Direction.CW);
//            canvas.clipPath(previewClipPath);
//            previewView.drawBackground(canvas);
//
//            canvas.restore();

            canvas.save();
            dimPaint.setColor(0xFF000000);
            dimPaint.setAlpha((int) (0xFF * appearProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            if (appearProgress < 1.0f) {
                previewClipPath.rewind();
                previewClipRect.set(0, 0, previewContainer.getWidth(), previewContainer.getHeight());
                previewClipRect.offset(previewLocation[0], previewLocation[1]);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                lerp(previewClipRect, AndroidUtilities.rectTmp, appearProgress, previewClipRect);
                final float r = lerp(dp(12), 0, appearProgress);
                previewClipPath.addRoundRect(previewClipRect, r, r, Path.Direction.CW);

                canvas.clipPath(previewClipPath);
            }

            final float pa = 1.0f - appearProgress; // preview alpha
            final float ca = appearProgress; // crop alpha

            canvas.translate(-thisLocation[0] * pa, -thisLocation[1] * pa);

            if (pa > 0) {
                if (closing) {
                    photoView.getLocationOnScreen(photoViewLocation);
                }
                canvas.translate((photoViewLocation[0]) * pa, (photoViewLocation[1]) * pa);
                float pw = 1.0f, ph = 1.0f;
                if (photoView.crop != null) {
                    pw = photoView.crop.cropPw;
                    ph = photoView.crop.cropPh;
                }
                final float s = lerp(1.0f, (float) photoView.getWidth() / pw * photoView.getScaleX() / previewContainer.getWidth(), pa);
                canvas.scale(s, s);
                canvas.rotate(photoView.getRotation() * pa);
                canvas.translate(photoView.getContentWidth() * pw / 2.0f * pa, photoView.getContentHeight() * ph / 2.0f * pa);
            }

            final boolean inBubbleMode = getContext() instanceof BubbleActivity;
            final float paddingTop = cropView.topPadding + (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);

            canvas.translate((dp(16) + getContainerWidth() / 2.0f) * ca, (paddingTop + (getContainerHeight() + dp(32)) / 2.0f) * ca);

            if (pa > 0) {
                final float w = photoView.getContentWidth();
                final float h = photoView.getContentHeight();
                final float pw = photoView.crop != null ? photoView.crop.cropPw : 1.0f;
                final float ph = photoView.crop != null ? photoView.crop.cropPh : 1.0f;
                final float hw = w * lerp(1.0f, pw, pa) / 2.0f;
                final float hh = h * lerp(1.0f, ph, pa) / 2.0f;
                final float s = lerp(1.0f, 4.0f, ca);
                canvas.clipRect(-hw * s, -hh * s, hw * s, hh * s);
            }

            applyCrop(canvas, ca, pa, 1.0f);
            canvas.rotate(photoView.getOrientation());
            float mirror = animatedMirror.set(closing ? (photoView.crop != null && photoView.crop.mirrored) : cropView.isMirrored());
            canvas.scale(lerp(1.0f, -1.0f, mirror), 1.0f);
            canvas.translate(-photoView.getContentWidth() / 2.0f, -photoView.getContentHeight() / 2.0f);

            photoView.drawContent(canvas);

            canvas.restore();
        }

        private float getContainerWidth() {
            return getWidth() - dp(32);
        }

        private float getContainerHeight() {
            final boolean inBubbleMode = getContext() instanceof BubbleActivity;
            final float paddingTop = cropView.topPadding + (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            final float paddingBottom = cropView.bottomPadding;
            return getHeight() - paddingTop - paddingBottom - dp(32);
        }

        private void applyCrop(Canvas canvas, float scaleAlpha, float clipAlpha, float alpha) {
            final float scale = 1.0f;

            int originalWidth = getCurrentWidth();
            int originalHeight = getCurrentHeight();

            int rotatedWidth = originalWidth;
            int rotatedHeight = originalHeight;
            int orientation = cropTransform.getOrientation();
            if (orientation == 90 || orientation == 270) {
                int temp = rotatedWidth;
                rotatedWidth = rotatedHeight;
                rotatedHeight = temp;
            }

            float trueScale = 1.0f + (cropTransform.getTrueCropScale() - 1.0f) * (1.0f - scaleAlpha);
            float scaleToFit = (getContainerWidth()) / (float) rotatedWidth;
            if (scaleToFit * rotatedHeight > (getContainerHeight())) {
                scaleToFit = (getContainerHeight()) / (float) rotatedHeight;
            }

            canvas.translate(cropTransform.getCropAreaX() * alpha, cropTransform.getCropAreaY() * alpha);
            float s = (cropTransform.getScale() / trueScale) * scaleToFit;
            if (photoView != null && photoView.crop != null) {
                s = lerp(photoView.crop.cropScale, s, scaleAlpha);
            } else {
                s = lerp(1.0f, s, scaleAlpha);
            }
            canvas.scale(s, s);
            canvas.translate(cropTransform.getCropPx() * rotatedWidth * alpha, cropTransform.getCropPy() * rotatedHeight * alpha);
            float rotate = photoView.getOrientation() + cropTransform.getRotation() + animatedOrientation.set((lastOrientation / 360) * 360 + orientation);
//            if (lastOrientation != orientation) {
//                if ((lastOrientation / 360) * 360 + orientation < lastOrientation) {
//                    lastOrientation = ((lastOrientation / 360 + 1) * 360) + orientation;
//                } else {
//                    lastOrientation = (lastOrientation / 360) * 360 + orientation;
//                }
//            }
            if (photoView.crop == null) {
                rotate = lerp(0, rotate, appearProgress);
            } else {
                rotate = lerp(photoView.crop.cropRotate + photoView.crop.transformRotation, rotate, appearProgress);
            }
            canvas.rotate(rotate);
        }
    }

    protected void close() {

    }
}
