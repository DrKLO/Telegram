package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
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
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.zxing.common.detector.MathUtils;

import org.checkerframework.checker.units.qual.C;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.Crop.CropAreaView;
import org.telegram.ui.Components.Crop.CropRotationWheel;
import org.telegram.ui.Components.Crop.CropTransform;
import org.telegram.ui.Components.Crop.CropView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CropEditor extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final PreviewView previewView;

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

    private int getCurrentWidth() {
        if (entry == null) return 1;
        return entry.orientation == 90 || entry.orientation == 270 ? previewView.getContentHeight() : previewView.getContentWidth();
    }

    private int getCurrentHeight() {
        if (entry == null) return 1;
        return entry.orientation == 90 || entry.orientation == 270 ? previewView.getContentWidth() : previewView.getContentHeight();
    }

    public CropEditor(Context context, PreviewView previewView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.previewView = previewView;
        this.resourcesProvider = resourcesProvider;

        contentView = new ContentView(context);
        animatedMirror = new AnimatedFloat(contentView, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        animatedOrientation = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);

        cropView = new CropView(context) {
            @Override
            public int getCurrentWidth() {
                return CropEditor.this.getCurrentWidth();
            }
            @Override
            public int getCurrentHeight() {
                return CropEditor.this.getCurrentHeight();
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
            contentView.invalidate();
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
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        cropView.setBottomPadding(controlsLayout.getPaddingBottom() + dp(116));
        super.onLayout(changed, left, top, right, bottom);
    }

    private float appearProgress = 0.0f;
    private final int[] thisLocation = new int[2];
    private final int[] previewLocation = new int[2];

    private final CropTransform cropTransform = new CropTransform();

    private StoryEntry entry;

    public void setEntry(StoryEntry entry) {
        if (entry == null) return;
        this.entry = entry;

        applied = false;
        closing = false;
        cropView.onShow();

        getLocationOnScreen(thisLocation);
        previewView.getLocationOnScreen(previewLocation);
        final MediaController.CropState restoreState = entry.crop != null ? entry.crop : null;
        cropView.start(entry.orientation, true, false, cropTransform, restoreState);
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
        animatedOrientation.set((lastOrientation / 360) * 360 + cropTransform.getOrientation(), true);

        this.contentView.setVisibility(View.VISIBLE);
        this.contentView.invalidate();
        previewView.setCropEditorDrawing(this);
//        previewView.setVisibility(View.GONE);
    }

    public boolean closing;
    public void disappearStarts() {
        previewView.setCropEditorDrawing(this);
        closing = true;
    }

    public void stop() {
        this.entry = null;
        cropView.stop();
        cropView.onHide();
        contentView.setVisibility(View.GONE);
        previewView.setCropEditorDrawing(null);
//        previewView.setVisibility(View.VISIBLE);
    }

    public boolean applied;
    public void apply() {
        if (entry == null) return;

        applied = true;

        entry.crop = new MediaController.CropState();
        cropView.applyToCropState(entry.crop);
        entry.crop.orientation = entry.orientation;
    }

    public float getAppearProgress() {
        return appearProgress;
    }

    public void setAppearProgress(float appearProgress) {
        if (Math.abs(this.appearProgress - appearProgress) < 0.001f) return;
        this.appearProgress = appearProgress;
        contentView.setAlpha(appearProgress);
        contentView.invalidate();
        cropView.areaView.setDimAlpha(0.5f * appearProgress);
        cropView.areaView.setFrameAlpha(appearProgress);
        cropView.areaView.invalidate();
        previewView.invalidate();
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

        private final Matrix identityMatrix = new Matrix();
        private final Matrix matrix = new Matrix();

        private final Matrix previewMatrix = new Matrix();
        private final Matrix cropMatrix = new Matrix();
        private final Matrix clipMatrix = new Matrix();
        private final Matrix invertedClipMatrix = new Matrix();

        public void drawImage(Canvas canvas, boolean fromPreview) {
            if (fromPreview) {
                if (appearProgress >= 1) {
                    return;
                }
                canvas.saveLayerAlpha(0, 0, previewView.getWidth(), previewView.getHeight(), (int) (0xFF * Math.min(1, (1.0f - appearProgress) * 2)), Canvas.ALL_SAVE_FLAG);
                canvas.translate(thisLocation[0] - previewLocation[0], thisLocation[1] - previewLocation[1]);
            }

            canvas.save();
            dimPaint.setColor(0xFF000000);
            dimPaint.setAlpha((int) (0xFF * appearProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            if (appearProgress < 1.0f && !fromPreview) {

                previewClipPath.rewind();
                previewClipRect.set(0, 0, previewView.getWidth(), previewView.getHeight());
                previewClipRect.offset(previewLocation[0], previewLocation[1]);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                lerp(previewClipRect, AndroidUtilities.rectTmp, appearProgress, previewClipRect);
                final float r = lerp(dp(12), 0, appearProgress);
                previewClipPath.addRoundRect(previewClipRect, r, r, Path.Direction.CW);

                canvas.clipPath(previewClipPath);
            }

            final float pa = 1.0f - appearProgress; // preview alpha
            final float ca = appearProgress; // crop alpha

            previewMatrix.reset();
            cropMatrix.reset();

            previewMatrix.preTranslate(-thisLocation[0], -thisLocation[1]);
            previewMatrix.preTranslate(previewLocation[0], previewLocation[1]);
//            canvas.translate(-thisLocation[0] * pa, -thisLocation[1] * pa);
//            canvas.translate(previewLocation[0] * pa, previewLocation[1] * pa);

//            if (pa > 0) {
//                float sx = lerp(1.0f, (float) previewView.getWidth() / entry.resultWidth, pa);
//                float sy = lerp(1.0f, (float) previewView.getHeight() / entry.resultHeight, pa);
//                canvas.scale(sx, sy);
//                AndroidUtilities.lerp(identityMatrix, entry.matrix, pa, matrix);
//                canvas.concat(matrix);
//                canvas.translate(previewView.getContentWidth() / 2.0f * pa, previewView.getContentHeight() / 2.0f * pa);
//            }
            previewMatrix.preScale((float) previewView.getWidth() / entry.resultWidth, (float) previewView.getHeight() / entry.resultHeight);
            previewMatrix.preConcat(entry.matrix);
            previewMatrix.preTranslate(previewView.getContentWidth() / 2.0f, previewView.getContentHeight() / 2.0f);

            final boolean inBubbleMode = getContext() instanceof BubbleActivity;
            final float paddingTop = (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);

            cropMatrix.preTranslate((dp(16) + getContainerWidth() / 2.0f), (paddingTop + (getContainerHeight() + dp(32)) / 2.0f));
//            canvas.translate((dp(16) + getContainerWidth() / 2.0f) * ca, (paddingTop + (getContainerHeight() + dp(32)) / 2.0f) * ca);

            if (fromPreview) {
                AndroidUtilities.lerp(previewMatrix, identityMatrix, appearProgress, clipMatrix);
                clipMatrix.preRotate(-entry.orientation);
                if (clipMatrix.invert(invertedClipMatrix)) {
                    final int orientation = entry.orientation + (entry.crop != null ? entry.crop.transformRotation : 0);
                    final boolean rotated = (orientation / 90) % 2 == 1;
                    final float w = previewView.getContentWidth();
                    final float h = previewView.getContentHeight();
                    final float pw = entry.crop != null ? entry.crop.cropPw : 1.0f;
                    final float ph = entry.crop != null ? entry.crop.cropPh : 1.0f;
                    final float hw = (rotated ? h : w) * pw / 2.0f;
                    final float hh = (rotated ? w : h) * ph / 2.0f;
                    final float s = lerp(1.0f, 4.0f, ca);
                    canvas.concat(clipMatrix);
                    canvas.clipRect(-hw * s, -hh * s, hw * s, hh * s);
                    canvas.concat(invertedClipMatrix);
                }
            }

            applyCrop(previewMatrix, true);
            applyCrop(cropMatrix, false);
//            applyCrop(canvas, ca, 1.0f);
            float mirror = animatedMirror.set(closing ? (entry.crop != null && entry.crop.mirrored) : cropView.isMirrored());
            cropMatrix.preScale(1 - mirror * 2, 1f);
            previewMatrix.preScale(1 - mirror * 2, 1f);
//            canvas.scale(1 - mirror * 2, 1f);
            cropMatrix.preSkew(0, 4 * mirror * (1f - mirror) * .25f);
            previewMatrix.preSkew(0, 4 * mirror * (1f - mirror) * .25f);
//            canvas.skew(0, 4 * mirror * (1f - mirror) * .25f);
            cropMatrix.preTranslate(-previewView.getContentWidth() / 2.0f, -previewView.getContentHeight() / 2.0f);
            previewMatrix.preTranslate(-previewView.getContentWidth() / 2.0f, -previewView.getContentHeight() / 2.0f);
//            canvas.translate(
//                    -previewView.getContentWidth() / 2.0f,
//                    -previewView.getContentHeight() / 2.0f
//            );

            AndroidUtilities.lerp(previewMatrix, cropMatrix, appearProgress, matrix);
            canvas.concat(matrix);

            previewView.drawContent(canvas);

            canvas.restore();

//            final float originalWidth = 100, originalHeight = 100f / previewView.getContentWidth() * previewView.getContentHeight();
//            cropView.applyToCropState(cropState);
//            if (cropState.useMatrix != null) {
//                float pw = cropState.cropPw, ph = cropState.cropPh;
//                if ((cropState.orientation / 90) % 2 == 1) {
//                    pw = cropState.cropPh;
//                    ph = cropState.cropPw;
//                }
//                float _pw = (1.0f - pw) / 2.0f, _ph = (1.0f - ph) / 2.0f;
//                DEBUG_vertices[0] = originalWidth * _pw;
//                DEBUG_vertices[1] = originalHeight * _ph;
//                DEBUG_vertices[2] = originalWidth * (_pw + pw);
//                DEBUG_vertices[3] = originalHeight * _ph;
//                DEBUG_vertices[4] = originalWidth * _pw;
//                DEBUG_vertices[5] = originalHeight * (_ph + ph);
//                DEBUG_vertices[6] = originalWidth * (_pw + pw);
//                DEBUG_vertices[7] = originalHeight * (_ph + ph);
//                cropState.useMatrix.mapPoints(DEBUG_vertices);
//                for (int a = 0; a < 4; a++) {
//                    DEBUG_vertices[a * 2] = DEBUG_vertices[a * 2] / originalWidth * 2f - 1f;
//                    DEBUG_vertices[a * 2 + 1] = 1f - DEBUG_vertices[a * 2 + 1] / originalHeight * 2f;
//                }
//
//                float uvOw = originalWidth, uvOh = originalHeight;
//                DEBUG_uv[0] = uvOw*pw*-0.5f;
//                DEBUG_uv[1] = uvOh*ph*-0.5f;
//                DEBUG_uv[2] = uvOw*pw*+0.5f;
//                DEBUG_uv[3] = uvOh*ph*-0.5f;
//                DEBUG_uv[4] = uvOw*pw*-0.5f;
//                DEBUG_uv[5] = uvOh*ph*+0.5f;
//                DEBUG_uv[6] = uvOw*pw*+0.5f;
//                DEBUG_uv[7] = uvOh*ph*+0.5f;
//                float angle = (float) (-cropState.cropRotate * (Math.PI / 180.0f));
//                for (int a = 0; a < 4; ++a) {
//                    float x = DEBUG_uv[a * 2 + 0], y = DEBUG_uv[a * 2 + 1];
//                    x -= cropState.cropPx * uvOw;
//                    y -= cropState.cropPy * uvOh;
//                    float x2 = (float) (x * Math.cos(angle) - y * Math.sin(angle)) / uvOw;
//                    float y2 = (float) (x * Math.sin(angle) + y * Math.cos(angle)) / uvOh;
//                    x2 /= cropState.cropScale;
//                    y2 /= cropState.cropScale;
//                    x2 += 0.5f;
//                    y2 += 0.5f;
//                    DEBUG_uv[a * 2 + 0] = x2;
//                    DEBUG_uv[a * 2 + 1] = y2;
//                }
//
//                final float cx = getWidth() / 2.0f, cy = getHeight() / 2.0f;
//                canvas.save();
//                canvas.translate(cx-originalWidth/2.0f, cy-originalHeight/2.0f);
//                DEBUG_path.rewind();
//                DEBUG_path.moveTo(DEBUG_uv[0] * originalWidth, DEBUG_uv[1] * originalHeight);
//                DEBUG_path.lineTo(DEBUG_uv[2] * originalWidth, DEBUG_uv[3] * originalHeight);
//                DEBUG_path.lineTo(DEBUG_uv[4] * originalWidth, DEBUG_uv[5] * originalHeight);
//                DEBUG_path.lineTo(DEBUG_uv[6] * originalWidth, DEBUG_uv[7] * originalHeight);
//                DEBUG_path.close();
//                canvas.drawRect(0,0,originalWidth,originalHeight,Theme.DEBUG_BLUE);
//                canvas.drawPath(DEBUG_path, Theme.DEBUG_RED);
//                canvas.restore();
//            }

            if (fromPreview) {
                canvas.restore();
            }
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (entry == null) return;
            drawImage(canvas, false);
        }

//        private final MediaController.CropState cropState = new MediaController.CropState();
//        private float[] DEBUG_vertices = new float[8];
//        private float[] DEBUG_uv = new float[8];
//        private final Path DEBUG_path = new Path();

        private float getContainerWidth() {
            return getWidth() - dp(32);
        }

        private float getContainerHeight() {
            final boolean inBubbleMode = getContext() instanceof BubbleActivity;
            final float paddingTop = (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            final float paddingBottom = cropView.bottomPadding;
            return getHeight() - paddingTop - paddingBottom - dp(32);
        }

        private void applyCrop(Matrix matrix, boolean preview) {
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

            float trueScale = 1.0f + (cropTransform.getTrueCropScale() - 1.0f) * (preview ? 1.0f : 0.0f);
            float scaleToFit = (getContainerWidth()) / (float) rotatedWidth;
            if (scaleToFit * rotatedHeight > (getContainerHeight())) {
                scaleToFit = (getContainerHeight()) / (float) rotatedHeight;
            }

            boolean orientedRotate = (entry.orientation / 90) % 2 == 1;
            matrix.preTranslate(cropTransform.getCropAreaX(), cropTransform.getCropAreaY());
            float s = (cropTransform.getScale() / trueScale) * scaleToFit;
            if (entry != null && entry.crop != null) {
                if (preview) s = entry.crop.cropScale;
            } else {
                if (preview) s = 1.0f;
            }
            matrix.preScale(s, s);
            float px = cropTransform.getCropPx(), py = cropTransform.getCropPy();
            if (closing) {
                if (preview) {
                    px = entry.crop == null ? 0 : !orientedRotate ? entry.crop.cropPx : entry.crop.cropPy;
                    py = entry.crop == null ? 0 : !orientedRotate ? entry.crop.cropPy : entry.crop.cropPx;
                }
            }
            matrix.preTranslate(px * rotatedWidth, py * rotatedHeight);
            float rotate = entry.orientation + cropTransform.getRotation() + orientation;//animatedOrientation.set((lastOrientation / 360) * 360 + orientation);
            if (entry.crop == null) {
                if (preview) rotate = 0;
            } else {
                if (preview) rotate = entry.crop.cropRotate + entry.crop.transformRotation;
            }
            matrix.preRotate(rotate);
        }

        private void applyCrop(Canvas canvas, float scaleAlpha, float alpha) {
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

            boolean orientedRotate = (entry.orientation / 90) % 2 == 1;
            canvas.translate(cropTransform.getCropAreaX() * alpha, cropTransform.getCropAreaY() * alpha);
            float s = (cropTransform.getScale() / trueScale) * scaleToFit;
            if (entry != null && entry.crop != null) {
                s = lerp(entry.crop.cropScale, s, scaleAlpha);
            } else {
                s = lerp(1.0f, s, scaleAlpha);
            }
            canvas.scale(s, s);
            float px = cropTransform.getCropPx(), py = cropTransform.getCropPy();
            if (closing) {
                px = lerp(entry.crop == null ? 0 : !orientedRotate ? entry.crop.cropPx : entry.crop.cropPy, px, appearProgress);
                py = lerp(entry.crop == null ? 0 : !orientedRotate ? entry.crop.cropPy : entry.crop.cropPx, py, appearProgress);
            }
            canvas.translate(px * rotatedWidth, py * rotatedHeight);
            float rotate = entry.orientation + cropTransform.getRotation() + animatedOrientation.set((lastOrientation / 360) * 360 + orientation);
            if (entry.crop == null) {
                rotate = lerp(0, rotate, appearProgress);
            } else {
                rotate = lerp(entry.crop.cropRotate + entry.crop.transformRotation, rotate, appearProgress);
            }
            canvas.rotate(rotate);
        }
    }

    protected void close() {

    }
}
