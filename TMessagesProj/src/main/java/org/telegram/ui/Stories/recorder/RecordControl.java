package org.telegram.ui.Stories.recorder;

import static android.graphics.Color.BLACK;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Point;

import java.util.ArrayList;

public class RecordControl extends View implements FlashViews.Invertable {

    public interface Delegate {
        void onPhotoShoot();
        void onVideoRecordStart(boolean byLongPress, Runnable whenStarted);
        void onVideoRecordPause();
        void onVideoRecordResume();
        void onVideoRecordEnd(boolean byDuration);
        void onVideoDuration(long duration);
        void onGalleryClick();
        void onFlipClick();
        void onFlipLongClick();
        void onZoom(float zoom);
        void onVideoRecordLocked();
        boolean canRecordAudio();
        void onCheckClick();

        default long getMaxVideoDuration() {
            return 60 * 1000L;
        }
        default boolean showStoriesDrafts() {
            return true;
        }
    }

    public void startAsVideo(boolean isVideo) {
        overrideStartModeIsVideoT = -1;
        this.startModeIsVideo = isVideo;
        invalidate();
    }

    public void startAsVideoT(float isVideoT) {
        overrideStartModeIsVideoT = isVideoT;
        invalidate();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private Delegate delegate;

    private final ImageReceiver galleryImage = new ImageReceiver();
    private final CombinedDrawable noGalleryDrawable;
    private final Drawable flipDrawableWhite, flipDrawableBlack;
    private final Drawable unlockDrawable, lockDrawable;
    private final Drawable pauseDrawable;

    private final static int WHITE = 0xFFFFFFFF;
    private final static int RED = 0xFFF73131;
    private final static int BG = 0x64000000;

    private final Paint mainPaint =          new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint =       new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlineFilledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint =        new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaintWhite =   new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redPaint =           new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintLinePaintWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintLinePaintBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint =         new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix redMatrix =         new Matrix();
    private RadialGradient redGradient;

    private final ButtonBounce recordButton =  new ButtonBounce(this);
    private final ButtonBounce flipButton =    new ButtonBounce(this);
    private final ButtonBounce lockButton =   new ButtonBounce(this);

    private float flipDrawableRotate;
    private final AnimatedFloat flipDrawableRotateT = new AnimatedFloat(this, 0, 310, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean dual;
    private final AnimatedFloat dualT = new AnimatedFloat(this, 0, 330, CubicBezierInterpolator.EASE_OUT_QUINT);

    private long recordingStart;
    private long lastDuration;

    private final Path checkPath = new Path();
    private final Point check1 = new Point(-dpf2(29/3.0f), dpf2(7/3.0f));
    private final Point check2 = new Point(-dpf2(8.5f/3.0f), dpf2(26/3.0f));
    private final Point check3 = new Point(dpf2(29/3.0f), dpf2(-11/3.0f));

    public RecordControl(Context context) {
        super(context);

        setWillNotDraw(false);

        redGradient = new RadialGradient(0, 0, dp(30 + 18), new int[] {RED, RED, WHITE}, new float[] {0, .64f, 1f}, Shader.TileMode.CLAMP);
        redGradient.setLocalMatrix(redMatrix);
        redPaint.setShader(redGradient);
        outlinePaint.setColor(WHITE);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlineFilledPaint.setColor(RED);
        outlineFilledPaint.setStrokeCap(Paint.Cap.ROUND);
        outlineFilledPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setColor(BG);
        buttonPaintWhite.setColor(WHITE);
        hintLinePaintWhite.setColor(0x58ffffff);
        hintLinePaintBlack.setColor(0x18000000);
        hintLinePaintWhite.setStyle(Paint.Style.STROKE);
        hintLinePaintWhite.setStrokeCap(Paint.Cap.ROUND);
        hintLinePaintBlack.setStyle(Paint.Style.STROKE);
        hintLinePaintBlack.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPaint.setBlendMode(android.graphics.BlendMode.CLEAR);
        } else {
            checkPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        galleryImage.setParentView(this);
        galleryImage.setCrossfadeWithOldImage(true);
        galleryImage.setRoundRadius(dp(6));

        final Drawable noPhotosIcon = context.getResources().getDrawable(R.drawable.msg_media_gallery).mutate();
        noPhotosIcon.setColorFilter(new PorterDuffColorFilter(0x4dFFFFFF, PorterDuff.Mode.MULTIPLY));
        noGalleryDrawable = new CombinedDrawable(Theme.createRoundRectDrawable(dp(6), 0xFF2E2E2F), noPhotosIcon);
        noGalleryDrawable.setFullsize(false);
        noGalleryDrawable.setIconSize(dp(24), dp(24));

        flipDrawableWhite = context.getResources().getDrawable(R.drawable.msg_photo_switch2).mutate();
        flipDrawableWhite.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        flipDrawableBlack = context.getResources().getDrawable(R.drawable.msg_photo_switch2).mutate();
        flipDrawableBlack.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));

        unlockDrawable = context.getResources().getDrawable(R.drawable.msg_filled_unlockedrecord).mutate();
        unlockDrawable.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        lockDrawable = context.getResources().getDrawable(R.drawable.msg_filled_lockedrecord).mutate();
        lockDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));

        pauseDrawable = context.getResources().getDrawable(R.drawable.msg_round_pause_m).mutate();
        pauseDrawable.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));

        updateGalleryImage();
    }

    public void updateGalleryImage() {
        final String filter = "80_80";
        if (delegate != null && delegate.showStoriesDrafts()) {
            ArrayList<StoryEntry> drafts = MessagesController.getInstance(galleryImage.getCurrentAccount()).getStoriesController().getDraftsController().drafts;
            galleryImage.setOrientation(0, 0, true);
            if (drafts != null && !drafts.isEmpty() && drafts.get(0).draftThumbFile != null) {
                galleryImage.setImage(ImageLocation.getForPath(drafts.get(0).draftThumbFile.getAbsolutePath()), filter, null, null, noGalleryDrawable, 0, null, null, 0);
                return;
            }
        }
        MediaController.AlbumEntry albumEntry = MediaController.allMediaAlbumEntry;
        MediaController.PhotoEntry photoEntry = null;
        if (albumEntry != null && albumEntry.photos != null && !albumEntry.photos.isEmpty()) {
            photoEntry = albumEntry.photos.get(0);
        }
        if (photoEntry != null && photoEntry.thumbPath != null) {
            galleryImage.setImage(ImageLocation.getForPath(photoEntry.thumbPath), filter, null, null, noGalleryDrawable, 0, null, null, 0);
        } else if (photoEntry != null && photoEntry.path != null) {
            if (photoEntry.isVideo) {
                galleryImage.setImage(ImageLocation.getForPath("vthumb://" + photoEntry.imageId + ":" + photoEntry.path), filter, null, null, noGalleryDrawable, 0, null, null, 0);
            } else {
                galleryImage.setOrientation(photoEntry.orientation, photoEntry.invert, true);
                galleryImage.setImage(ImageLocation.getForPath("thumb://" + photoEntry.imageId + ":" + photoEntry.path), filter, null, null, noGalleryDrawable, 0, null, null, 0);
            }
        } else {
            galleryImage.setImageBitmap(noGalleryDrawable);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        galleryImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        galleryImage.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    public void setInvert(float invert) {
        outlinePaint.setColor(ColorUtils.blendARGB(WHITE, BLACK, invert));
        buttonPaint.setColor(ColorUtils.blendARGB(BG, 0x16000000, invert));
        hintLinePaintWhite.setColor(ColorUtils.blendARGB(0x58ffffff, 0x10ffffff, invert));
        hintLinePaintBlack.setColor(ColorUtils.blendARGB(0x18000000, 0x30000000, invert));
        flipDrawableWhite.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert), PorterDuff.Mode.MULTIPLY));
        unlockDrawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert), PorterDuff.Mode.MULTIPLY));
    }

    public float amplitude;
    public final AnimatedFloat animatedAmplitude = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.DEFAULT);
    public void setAmplitude(float amplitude, boolean animated) {
        this.amplitude = amplitude;
        if (!animated) {
            this.animatedAmplitude.set(amplitude, true);
        }
    }

    private float cx, cy;
    private float leftCx, rightCx;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = dp(100);

        cx = width / 2f;
        cy = height / 2f;

        final float dist = Math.min(dp(135), width * .35f);
        leftCx = cx - dist;
        rightCx = cx + dist;

        setDrawableBounds(flipDrawableWhite, rightCx, cy, dp(14));
        setDrawableBounds(flipDrawableBlack, rightCx, cy, dp(14));
        setDrawableBounds(unlockDrawable, leftCx, cy);
        setDrawableBounds(lockDrawable, leftCx, cy);
        setDrawableBounds(pauseDrawable, leftCx, cy);
        galleryImage.setImageCoords(leftCx - dp(20), cy - dp(20), dp(40), dp(40));

        redMatrix.reset();
        redMatrix.postTranslate(cx, cy);
        redGradient.setLocalMatrix(redMatrix);

        setMeasuredDimension(width, height);
    }

    private static void setDrawableBounds(Drawable drawable, float cx, float cy) {
        setDrawableBounds(drawable, cx, cy, Math.max(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()) / 2f);
    }

    private static void setDrawableBounds(Drawable drawable, float cx, float cy, float r) {
        drawable.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
    }

    private final AnimatedFloat startModeIsVideoT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private float overrideStartModeIsVideoT = -1;
    private boolean startModeIsVideo = true;

    private final AnimatedFloat recordingT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat recordingLongT = new AnimatedFloat(this, 0, 850, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean recording;

    private float loadingSegments[] = new float[2];
    private final AnimatedFloat recordingLoadingT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean recordingLoading;
    private long recordingLoadingStart;

    private boolean touch;
    private boolean discardParentTouch;
    private long touchStart;
    private float touchX, touchY;
    private boolean longpressRecording;
    private boolean showLock;
    private final AnimatedFloat touchT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat touchIsCenterT = new AnimatedFloat(this, 0, 650, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat touchIsCenter2T = new AnimatedFloat(this, 0, 160, CubicBezierInterpolator.EASE_IN);
    private final AnimatedFloat recordCx = new AnimatedFloat(this, 0, 750, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat touchIsButtonT = new AnimatedFloat(this, 0, 650, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat lockedT = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    private float collageProgress;
    private final AnimatedFloat collage = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat collageProgressAnimated = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat checkAnimated = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setCollageProgress(float collageProgress, boolean animated) {
        if (Math.abs(collageProgress - this.collageProgress) < 0.01f) return;
        this.collageProgress = collageProgress;
        if (!animated) {
            this.collage.set(collageProgress > 0 && !recording, true);
            this.collageProgressAnimated.set(collageProgress, true);
        }
        invalidate();
    }

    private final Runnable onRecordLongPressRunnable = () -> {
        if (recording || hasCheck()) {
            return;
        }
        if (!delegate.canRecordAudio()) {
            touch = false;
            recordButton.setPressed(false);
            flipButton.setPressed(false);
            lockButton.setPressed(false);
            return;
        }
        longpressRecording = true;
        showLock = true;
        delegate.onVideoRecordStart(true, () -> {
            recordingStart = System.currentTimeMillis();
            recording = true;
            delegate.onVideoDuration(lastDuration = 0);
        });
    };

    private final Runnable onFlipLongPressRunnable = () -> {
        if (!recording && !hasCheck()) {
            delegate.onFlipLongClick();
            rotateFlip(360);

            touch = false;
            recordButton.setPressed(false);
            flipButton.setPressed(false);
            lockButton.setPressed(false);
        }
    };

    private final Path metaballsPath = new Path();
    private final Path circlePath = new Path();

    private final float HALF_PI = (float) Math.PI / 2;

    @Override
    protected void onDraw(Canvas canvas) {
        final float recordingT = this.recordingT.set(recording ? 1 : 0);
        final float recordingLongT = this.recordingLongT.set(recording ? 1 : 0);
        final float isVideo = Math.max(recordingT, overrideStartModeIsVideoT >= 0 ? overrideStartModeIsVideoT : this.startModeIsVideoT.set(startModeIsVideo ? 1 : 0));

        float scale;

        final float touchT = this.touchT.set(touch ? 1 : 0);
        final float touchIsCenterT = touchT * this.touchIsCenterT.set(Math.abs(touchX - cx) < dp(64) && (recording || recordButton.isPressed()) ? 1 : 0);
        final float touchIsCenter2T = touchT * this.touchIsCenter2T.set(Math.abs(touchX - cx) < dp(64) ? 1 : 0);
        final float touchCenterT16 = clamp((touchX - cx) / dp(16), 1, -1);
        final float touchCenterT96 = clamp((touchX - cx) / dp(64), 1, -1);
        final float touchIsButtonT = touchT * this.touchIsButtonT.set(Math.min(Math.abs(touchX - rightCx), Math.abs(touchX - leftCx)) < dp(16) ? 1 : 0);

        final float collage = this.collage.set(collageProgress > 0) * (1.0f - recordingT);
        final float collageProgress = this.collageProgressAnimated.set(this.collageProgress);
        final float check = checkAnimated.set(hasCheck());

        float hintLineT = longpressRecording ? recordingT * isVideo * touchT : 0;
        if (hintLineT > 0) {
            float lcx = cx - dp(42 + 8), rcx = cx + dp(42 + 8);
            hintLinePaintWhite.setStrokeWidth(dp(2));
            hintLinePaintBlack.setStrokeWidth(dp(2));

            canvas.drawLine(rcx, cy, lerp(rcx, rightCx - dp(22 + 8), hintLineT), cy, hintLinePaintBlack);
            canvas.drawLine(rcx, cy, lerp(rcx, rightCx - dp(22 + 8), hintLineT), cy, hintLinePaintWhite);

            canvas.drawLine(lcx, cy, lerp(lcx, leftCx + dp(22 + 8), hintLineT), cy, hintLinePaintBlack);
            canvas.drawLine(lcx, cy, lerp(lcx, leftCx + dp(22 + 8), hintLineT), cy, hintLinePaintWhite);
        }

        float acx = lerp(cx, recordCx.set(cx + dp(4) * touchCenterT16), touchIsCenterT);
        float r =   lerp(lerp(dp(29), dp(12), recordingT), dp(32) - dp(4) * Math.abs(touchCenterT96), touchIsCenterT);
        float rad = lerp(lerp(dp(32), dp(7), recordingT), dp(32), touchIsCenterT);
        scale = lerp(recordButton.getScale(startModeIsVideo ? 0 : .2f), 1 + .2f * animatedAmplitude.set(amplitude), recordingT);
        AndroidUtilities.rectTmp.set(acx - r, cy - r, acx + r, cy + r);
        mainPaint.setColor(ColorUtils.blendARGB(WHITE, RED, isVideo * (1.0f - check)));
        if (check > 0) {
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            mainPaint.setAlpha((int) (0xFF * (1.0f - check)));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, mainPaint);
            canvas.restore();
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }
        canvas.scale(scale, scale, cx, cy);
        mainPaint.setAlpha(0xFF);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, mainPaint);
        if (check > 0) {
            checkPaint.setStrokeWidth(dp(4));
            checkPath.rewind();
            checkPath.moveTo(check1.x, check1.y);
            checkPath.lineTo(lerp(check1.x, check2.x, clamp(check / .3f, 1.0f, 0.0f)), lerp(check1.y, check2.y, clamp(check / .3f, 1.0f, 0.0f)));
            if (check > .3f) checkPath.lineTo(lerp(check2.x, check3.x, clamp((check-.3f) / .7f, 1.0f, 0.0f)), lerp(check2.y, check3.y, clamp((check-.3f) / .7f, 1.0f, 0.0f)));
            canvas.translate(cx, cy);
            canvas.drawPath(checkPath, checkPaint);
        }
        canvas.restore();

        canvas.save();
        scale = Math.max(scale, 1);
        canvas.scale(scale, scale, cx, cy);
        float or = Math.max(dpf2(33.5f), r + lerp(dpf2(4.5f), dp(9), touchIsCenterT) + dp(5) * collage * (1.0f - touchIsCenterT));
        final float strokeWidth = lerp(dp(3), dp(4), collage);
        or = lerp(or, r - strokeWidth - dp(4), check);
        AndroidUtilities.rectTmp.set(cx - or, cy - or, cx + or, cy + or);
        outlinePaint.setStrokeWidth(strokeWidth);
        outlinePaint.setAlpha((int) (0xFF * lerp(1.0f, 0.3f, collage) * (1.0f - check)));
        canvas.drawCircle(cx, cy, or, outlinePaint);
        if (collage > 0 & collageProgress > 0) {
            outlinePaint.setAlpha(0xFF);
            canvas.drawArc(AndroidUtilities.rectTmp, -90, 360 * collageProgress, false, outlinePaint);
        }

        final long duration = System.currentTimeMillis() - recordingStart;
        final float recordEndT = recording ? 0 : 1f - recordingLongT;
        final long maxDuration = delegate != null ? delegate.getMaxVideoDuration() : 60_000;
        final float sweepAngle = Math.min(duration / (float) (maxDuration < 0 ? 60_000 : maxDuration) * 360, 360);

        final float recordingLoading = this.recordingLoadingT.set(this.recordingLoading);

        outlineFilledPaint.setStrokeWidth(strokeWidth);
        outlineFilledPaint.setAlpha((int) (0xFF * Math.max(.7f * recordingLoading, 1f - recordEndT)));

        if (recordingLoading <= 0) {
            canvas.drawArc(AndroidUtilities.rectTmp, -90, sweepAngle, false, outlineFilledPaint);
        } else {
            final long now = SystemClock.elapsedRealtime();
            CircularProgressDrawable.getSegments((now - recordingLoadingStart) % 5400, loadingSegments);
            invalidate();
            float fromAngle = loadingSegments[0], toAngle = loadingSegments[1];

            float center = (fromAngle + toAngle) / 2f;
            float amplitude = Math.abs(toAngle - fromAngle) / 2f;

            if (this.recordingLoading) {
                center = lerp(-90 + sweepAngle / 2f, center, recordingLoading);
                amplitude = lerp(sweepAngle / 2f, amplitude, recordingLoading);
            }

            canvas.drawArc(AndroidUtilities.rectTmp, center - amplitude, amplitude * 2, false, outlineFilledPaint);
        }

        if (recording) {
            invalidate();

            if (duration / 1000L != lastDuration / 1000L) {
                delegate.onVideoDuration(duration / 1000L);
            }
            if (maxDuration > 0 && duration >= maxDuration) {
                post(() -> {
                    recording = false;
                    longpressRecording = false;
                    this.recordingLoadingStart = SystemClock.elapsedRealtime();
                    this.recordingLoading = true;
                    touch = false;
                    recordButton.setPressed(false);
                    flipButton.setPressed(false);
                    lockButton.setPressed(false);
                    delegate.onVideoRecordEnd(true);
                });
            }
            lastDuration = duration;
        }

        canvas.restore();

        if (showLock) {
            scale = lockButton.getScale(.2f) * recordingT;
            if (scale > 0) {
                canvas.save();
                canvas.scale(scale, scale, leftCx, cy);
                canvas.drawCircle(leftCx, cy, dp(22), buttonPaint);
                canvas.rotate(-getRotation(), leftCx, cy);
                unlockDrawable.draw(canvas);
                canvas.restore();
            }
        }

        scale = lockButton.getScale(.2f) * (1f - recordingT) * (1.0f - check);
        if (scale > 0) {
            canvas.save();
            canvas.scale(scale, scale, leftCx, cy);
            canvas.rotate(-getRotation(), leftCx, cy);
            galleryImage.draw(canvas);
            canvas.restore();
        }

        float dualT = this.dualT.set(dual ? 1f : 0f);
        if (dualT > 0) {
            canvas.save();
            scale = flipButton.getScale(.2f) * dualT * (1.0f - check);
            canvas.scale(scale, scale, rightCx, cy);
            canvas.rotate(flipDrawableRotateT.set(flipDrawableRotate) - getRotation(), rightCx, cy);
            canvas.drawCircle(rightCx, cy, dp(22), buttonPaintWhite);
            flipDrawableBlack.draw(canvas);
            canvas.restore();
        }
        if (dualT < 1) {
            canvas.save();
            scale = flipButton.getScale(.2f) * (1f - dualT) * (1.0f - check);
            canvas.scale(scale, scale, rightCx, cy);
            canvas.rotate(flipDrawableRotateT.set(flipDrawableRotate) - getRotation(), rightCx, cy);
            canvas.drawCircle(rightCx, cy, dp(22), buttonPaint);
            flipDrawableWhite.draw(canvas);
            canvas.restore();
        }

        final float tr;
        if (longpressRecording && !hasCheck()) {
            tr = (
                touchT *
                isVideo *
                recordingT *
                lerp(
                    dp(16),
                    lerp(
                        dp(8) + dp(8) * Math.abs(touchCenterT96),
                        dp(22),
                        touchIsButtonT
                    ),
                    Math.max(touchIsButtonT, touchIsCenterT)
                )
            );
        } else {
            tr = 0;
        }
        float locked = lockedT.set(!longpressRecording && recording ? 1 : 0);
        if (tr > 0) {
            redPaint.setAlpha(0xFF);
            canvas.drawCircle(touchX, cy, tr, redPaint);

            float x1 = acx, x2 = touchX;
            final float handleSize = 2.4f;
            final float v = clamp(1f - touchT * Math.abs(touchCenterT96) / 1.3f, 1, 0);
            final float d = Math.abs(x1 - x2);
            final float maxdist = r + tr * 2f;
            if (d < maxdist && v < .6f) {

                double u1, u2;
                if (d < r + tr) {
                    u1 = Math.acos((r * r + d * d - tr * tr) / (2 * r * d));
                    u2 = Math.acos((tr * tr + d * d - r * r) / (2 * tr * d));
                } else {
                    u1 = u2 = 0;
                }

                final double angleBetweenCenters = x2 > x1 ? 0 : Math.PI;
                final double maxSpread = (float) Math.acos((r - tr) / d);

                double angle1 = angleBetweenCenters + u1 + (maxSpread - u1) * v;
                double angle2 = angleBetweenCenters - u1 - (maxSpread - u1) * v;
                double angle3 = angleBetweenCenters + Math.PI - u2 - (Math.PI - u2 - maxSpread) * v;
                double angle4 = angleBetweenCenters - Math.PI + u2 + (Math.PI - u2 - maxSpread) * v;

                getVector(x1, cy, angle1, r, p1);
                getVector(x1, cy, angle2, r, p2);
                getVector(x2, cy, angle3, tr, p3);
                getVector(x2, cy, angle4, tr, p4);

                final float totalRadius = r + tr;
                final float d2Base = Math.min(v * handleSize, dist(p1, p3) / totalRadius);
                final float d2 = d2Base * Math.min(1, (d * 2) / (r + tr));

                final float r1 = r * d2;
                final float r2 = tr * d2;

                getVector(p1.x, p1.y, angle1 - HALF_PI, r1, h1);
                getVector(p2.x, p2.y, angle2 + HALF_PI, r1, h2);
                getVector(p3.x, p3.y, angle3 + HALF_PI, r2, h3);
                getVector(p4.x, p4.y, angle4 - HALF_PI, r2, h4);

                float alpha = touchT * isVideo * recordingT * touchIsCenter2T;

                if (alpha > 0) {
                    metaballsPath.rewind();

                    metaballsPath.moveTo(p1.x, p1.y);
                    metaballsPath.cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y);
                    metaballsPath.lineTo(p4.x, p4.y);
                    metaballsPath.cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y);
                    metaballsPath.lineTo(p1.x, p1.y);

                    redPaint.setAlpha((int) (0xFF * alpha));
                    canvas.drawPath(metaballsPath, redPaint);

                    AndroidUtilities.rectTmp.set(acx - r, cy - r, acx + r, cy + r);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, redPaint);
                }
            }
        }
        if (tr > 0 || locked > 0) {
            scale = lockButton.getScale(.2f) * recordingT * (1.0f - check);;
            canvas.save();
            circlePath.rewind();
            if (tr > 0) {
                circlePath.addCircle(touchX, cy, tr, Path.Direction.CW);
            }
            if (locked > 0 && showLock) {
                circlePath.addCircle(leftCx, cy, locked * dp(22) * scale, Path.Direction.CW);
            }
            canvas.clipPath(circlePath);

            if (showLock) {
                canvas.save();
                canvas.scale(scale, scale, leftCx, cy);
                canvas.drawCircle(leftCx, cy, dp(22), buttonPaintWhite);
                canvas.rotate(-getRotation(), leftCx, cy);
                lockDrawable.draw(canvas);
                canvas.restore();
            }

            scale = flipButton.getScale(.2f) * (1.0f - check);
            canvas.save();
            canvas.scale(scale, scale, rightCx, cy);
            canvas.rotate(flipDrawableRotateT.set(flipDrawableRotate) - getRotation(), rightCx, cy);
            canvas.drawCircle(rightCx, cy, dp(22), buttonPaintWhite);
            flipDrawableBlack.draw(canvas);
            canvas.restore();

            canvas.restore();
        }
    }

    public boolean hasCheck() {
        return collageProgress >= 1.0f;
    }

    private final Point p1 = new Point(), p2 = new Point(), p3 = new Point(), p4 = new Point(), h1 = new Point(), h2 = new Point(), h3 = new Point(), h4 = new Point();
    private void getVector(float cx, float cy, double a, float r, Point point) {
        point.x = (float) (cx + Math.cos(a) * r);
        point.y = (float) (cy + Math.sin(a) * r);
    }

    private float dist(Point a, Point b) {
        return MathUtils.distance(a.x, a.y, b.x, b.y);
    }

    public void rotateFlip(float angles) {
        flipDrawableRotateT.setDuration(angles > 180 ? 620 : 310);
        flipDrawableRotate += angles;
        invalidate();
    }

    private boolean isPressed(float ex, float ey, float cx, float cy, float r, boolean ignoreWhenZoom) {
        if (recording) {
            if (ignoreWhenZoom && cy - ey > AndroidUtilities.dp(100)) {
                return false;
            }
            return Math.abs(cx - ex) <= r;
        }
        return MathUtils.distance(ex, ey, cx, cy) <= r;
    }

    private boolean flipButtonWasPressed;

    public boolean isTouch() {
        return discardParentTouch;
    }

    public void setDual(boolean active) {
        if (active != dual) {
            this.dual = active;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float ox = 0, oy = 0;
        final int action = event.getAction();

        final float x = clamp(event.getX() + ox, rightCx, leftCx), y = event.getY() + oy;

        final boolean innerFlipButton = isPressed(x, y, rightCx, cy, dp(7), true);
        if (recordingLoading) {
            recordButton.setPressed(false);
            flipButton.setPressed(false);
            lockButton.setPressed(false);
        } else if (action == MotionEvent.ACTION_DOWN || touch) {
            recordButton.setPressed(isPressed(x, y, cx, cy, dp(60), false));
            flipButton.setPressed(isPressed(x, y, rightCx, cy, dp(30), true) && !hasCheck());
            lockButton.setPressed(isPressed(x, y, leftCx, cy, dp(30), false) && !hasCheck());
        }

        boolean r = false;
        if (action == MotionEvent.ACTION_DOWN) {
            touch = true;
            discardParentTouch = recordButton.isPressed() || flipButton.isPressed();
            touchStart = System.currentTimeMillis();
            touchX = x;
            touchY = y;

            if (Math.abs(touchX - cx) < dp(50)) {
                AndroidUtilities.runOnUIThread(onRecordLongPressRunnable, ViewConfiguration.getLongPressTimeout());
            }

            if (flipButton.isPressed()) {
                AndroidUtilities.runOnUIThread(onFlipLongPressRunnable, ViewConfiguration.getLongPressTimeout());
            }

            r = true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!touch) {
                return false;
            }
            touchX = clamp(x, rightCx, leftCx);
            touchY = y;
            invalidate();

            if (recording && !flipButtonWasPressed && innerFlipButton) {
                rotateFlip(180);
                delegate.onFlipClick();
            }

            if (recording && longpressRecording) {
                final float dy = cy - dp(48) - y;
                final float zoom = clamp(dy / (AndroidUtilities.displaySize.y / 2f), 1, 0);
                delegate.onZoom(zoom);
            }

            r = true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!touch) {
                return false;
            }

            touch = false;
            discardParentTouch = false;

            AndroidUtilities.cancelRunOnUIThread(onRecordLongPressRunnable);
            AndroidUtilities.cancelRunOnUIThread(onFlipLongPressRunnable);

            if (!recording && lockButton.isPressed()) {
                delegate.onGalleryClick();
            } else if (recording && longpressRecording) {
                if (lockButton.isPressed()) {
                    longpressRecording = false;
                    lockedT.set(1, true);
                    delegate.onVideoRecordLocked();
                } else {
                    recording = false;
                    this.recordingLoadingStart = SystemClock.elapsedRealtime();
                    this.recordingLoading = true;
                    delegate.onVideoRecordEnd(false);
                }
            } else if (recordButton.isPressed()) {
                if (hasCheck()) {
                    delegate.onCheckClick();
                } else if (!startModeIsVideo && !recording && !longpressRecording) {
                    delegate.onPhotoShoot();
                } else if (!recording) {
                    if (delegate.canRecordAudio()) {
                        lastDuration = 0;
                        recordingStart = System.currentTimeMillis();
                        showLock = false;
                        delegate.onVideoRecordStart(false, () -> {
                            recordingStart = System.currentTimeMillis();
                            lastDuration = 0;
                            recording = true;
                            delegate.onVideoDuration(lastDuration);
                        });
                    }
                } else {
                    recording = false;
                    this.recordingLoadingStart = SystemClock.elapsedRealtime();
                    this.recordingLoading = true;
                    delegate.onVideoRecordEnd(false);
                }
            }

            longpressRecording = false;

            if (flipButton.isPressed()) {
                rotateFlip(180);
                delegate.onFlipClick();
            }

            recordButton.setPressed(false);
            flipButton.setPressed(false);
            lockButton.setPressed(false);

            invalidate();

            r = true;
        }
        flipButtonWasPressed = innerFlipButton;
        return r;
    }

    public boolean isRecording() {
        return recording;
    }

    public void stopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        this.recordingLoadingStart = SystemClock.elapsedRealtime();
        this.recordingLoading = true;
        delegate.onVideoRecordEnd(false);
        recordButton.setPressed(false);
        flipButton.setPressed(false);
        lockButton.setPressed(false);
        invalidate();
    }

    public void stopRecordingLoading(boolean animated) {
        this.recordingLoading = false;
        if (!animated) {
            this.recordingLoadingT.set(false, true);
        }
        invalidate();
    }
}
