package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.touchSlop;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.RenderView;
import org.telegram.ui.Components.Paint.Views.EntityView;
import org.telegram.ui.Components.Paint.Views.MessageEntityView;
import org.telegram.ui.Components.Paint.Views.PhotoView;
import org.telegram.ui.Components.Paint.Views.RoundView;
import org.telegram.ui.Components.PhotoFilterBlurControl;
import org.telegram.ui.Components.PhotoFilterCurvesControl;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.VideoEditTextureView;
import org.telegram.ui.Components.WaveDrawable;
import org.telegram.ui.Components.ZoomControlView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaRecorderView extends SizeNotifierFrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface MediaRecorderDelegate {

        void close(boolean animated);

        void openPremium();

        void processDone(StoryEntry outputEntry);

        void onCoverEdited(StoryEntry outputEntry);

    }

    public static class Params {

        private boolean isCaptionAllowed = true;

        public Params(boolean isCaptionAllowed) {
            this.isCaptionAllowed = isCaptionAllowed;
        }

        private static Params defaultParams = new Params(true);

    }

    // System components
    private final Activity activity;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;

    // Parent relative components
    private MediaRecorderDelegate delegate;
    private Params params = Params.defaultParams;

    public void setMediaRecorderDelegate(MediaRecorderDelegate delegate) {
        this.delegate = delegate;
    }

    public void setParams(Params params) {
        this.params = params;
    }

    // Accounts details
    private final int currentAccount;
    private long selectedDialogId;

    private final Theme.ResourcesProvider resourcesProvider;

    // Views
    private ContainerView containerView;
    private FlashViews flashViews;
    private ThanosEffect thanosEffect;

    private boolean wasSend;
    private long wasSendPeer = 0;

    // User input handlers
    private final GestureDetectorFixDoubleTap gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;

    public MediaRecorderView(
            Activity activity,
            WindowManager windowManager,
            WindowManager.LayoutParams windowLayoutParams,
            Theme.ResourcesProvider resourcesProvider,
            int currentAccount
    ) {
        super(activity);
        this.activity = activity;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.windowManager = windowManager;
        this.windowLayoutParams = windowLayoutParams;

        gestureDetector = new GestureDetectorFixDoubleTap(activity, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(activity, new ScaleListener());

        initViews();
    }

    // Source details
    private RectF sourceRect = new RectF();
    private float sourceRoundRadius;

    // Opening params
    private float openProgress;
    private int openType;

    public void setOpenProgress(float openProgress) {
        this.openProgress = openProgress;
    }

    private int previewW, previewH;
    private int underControls;
    private boolean underStatusBar;
    private boolean scrollingY, scrollingX;

    private Float frozenDismissProgress = null;
    private float dismissProgress;

    public void saveDismissProgress() {
        frozenDismissProgress = dismissProgress;
    }

    public void resetDismissProgress() {
        frozenDismissProgress = null;
    }

    private int insetLeft, insetTop, insetRight, insetBottom;

    public final RectF rectF = new RectF(), fullRectF = new RectF();
    private final Path clipPath = new Path();

    public void applyOpenProgress() {
        if (openType != 1) {
            return;
        }
        fullRectF.set(previewContainer.getLeft(), previewContainer.getTop(), previewContainer.getMeasuredWidth(),
                previewContainer.getMeasuredHeight());
        fullRectF.offset(containerView.getX(), containerView.getY());
        AndroidUtilities.lerp(sourceRect, fullRectF, openProgress, rectF);
        previewContainer.setAlpha(openProgress);
        previewContainer.setTranslationX(rectF.left - previewContainer.getLeft() - containerView.getX());
        previewContainer.setTranslationY(rectF.top - previewContainer.getTop() - containerView.getY());

        previewContainer.setScaleX(rectF.width() / previewContainer.getMeasuredWidth());
        previewContainer.setScaleY(rectF.height() / previewContainer.getMeasuredHeight());
        actionBarContainer.setAlpha(openProgress);
        controlContainer.setAlpha(openProgress);
        captionContainer.setAlpha(openProgress);
        if (currentPage == PAGE_COVER) {
            coverButton.setAlpha(openProgress);
        }
    }

    @Override
    public int getBottomPadding() {
        return getHeight() - containerView.getBottom() + underControls;
    }

    public int getBottomPadding2() {
        return getHeight() - containerView.getBottom();
    }

    public int getPaddingUnderContainer() {
        return getHeight() - insetBottom - containerView.getBottom();
    }

    public void setSourceViewRect(float radius, RectF rect) {
        this.sourceRoundRadius = radius;
        this.sourceRect = rect;
    }

    public void setupState(StoryEntry entry, boolean videoTextureActive, int openType) {
        this.outputEntry = entry;
        videoTextureHolder.active = videoTextureActive;
        this.openType = openType;

        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        containerView.setTranslationY2(0);
        containerView.setScaleX(1f);
        containerView.setScaleY(1f);
        dismissProgress = 0;

        updateBackground();
    }

    public void updateBackground() {
        containerView.updateBackground();
        previewContainer.setBackgroundColor(openType == 1 || openType == 0 ? 0 : 0xff1f1f1f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float dismiss = frozenDismissProgress != null ? frozenDismissProgress : dismissProgress;
        final float r = AndroidUtilities.lerp(sourceRoundRadius, 0, openProgress);
        boolean restore = false;
        if (openType == 0) {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * openProgress * (1f - dismiss))));
        }
        if (openProgress != 1) {
            if (openType == 0) {
                fullRectF.set(0, 0, getWidth(), getHeight());
                fullRectF.offset(containerView.getTranslationX(), containerView.getTranslationY());
                AndroidUtilities.lerp(sourceRect, fullRectF, openProgress, rectF);

                canvas.save();
                clipPath.rewind();
                clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
                canvas.clipPath(clipPath);

                final float alpha = Utilities.clamp(openProgress * 3, 1, 0);
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
                canvas.translate(rectF.left, rectF.top - containerView.getTranslationY() * openProgress);
                final float s = Math.max(rectF.width() / getWidth(), rectF.height() / getHeight());
                canvas.scale(s, s);
                restore = true;
            } else if (openType == 1) {
                applyOpenProgress();
            }
        }

        if (paintView != null) {
            paintView.onParentPreDraw();
        }
        super.dispatchDraw(canvas);
        if (restore) {
            canvas.restore();
            canvas.restore();
        }
    }

    // region User input handling
    private boolean flingDetected;
    private boolean touchInCollageList;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        flingDetected = false;
        if (collageListView != null && collageListView.isVisible()) {
            final float y = containerView.getY() + actionBarContainer.getY() + collageListView.getY();
            if (ev.getY() >= y && ev.getY() <= y + collageListView.getHeight() || touchInCollageList) {
                touchInCollageList = ev.getAction() != MotionEvent.ACTION_UP && ev.getAction() != MotionEvent.ACTION_CANCEL;
                return super.dispatchTouchEvent(ev);
            } else {
                collageListView.setVisible(false, true);
                updateActionBarButtons(true);
            }
        }
        if (touchInCollageList && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
            touchInCollageList = false;
        }
        scaleGestureDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);
        if (ev.getAction() == MotionEvent.ACTION_UP && !flingDetected) {
            if (containerView.getTranslationY() > 0) {
                if (dismissProgress > .4f) {
                    delegate.close(true);
                } else {
                    animateContainerBack();
                }
            } else if (galleryListView != null && galleryListView.getTranslationY() > 0 && !galleryClosing) {
                animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
            }
            galleryClosing = false;
            modeSwitcherView.stopScroll(0);
            scrollingY = false;
            scrollingX = false;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void cancelGestures() {
        scaleGestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
        gestureDetector.onTouchEvent(AndroidUtilities.emptyMotionEvent());
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event != null && event.getKeyCode()
                == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            onBackPressed();
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    private boolean scaling = false;

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!scaling || cameraView == null || currentPage != PAGE_CAMERA || cameraView.isDualTouch()
                    || collageLayoutView.getFilledProgress() >= 1) {
                return false;
            }
            final float deltaScaleFactor = (detector.getScaleFactor() - 1.0f) * .75f;
            cameraZoom += deltaScaleFactor;
            cameraZoom = Utilities.clamp(cameraZoom, 1, 0);
            cameraView.setZoom(cameraZoom);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom, false);
            }
            showZoomControls(true, true);
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            if (cameraView == null || currentPage != PAGE_CAMERA || wasGalleryOpen) {
                return false;
            }
            scaling = true;
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            scaling = false;
            animateGalleryListView(false);
            animateContainerBack();
            super.onScaleEnd(detector);
        }
    }

    private float ty, sty, stx;

    private final class GestureListener extends GestureDetectorFixDoubleTap.OnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            sty = 0;
            stx = 0;
            return false;
        }

        @Override
        public void onShowPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            scrollingY = false;
            scrollingX = false;
            if (!hasDoubleTap(e)) {
                if (onSingleTapConfirmed(e)) {
                    return true;
                }
            }
            if (isGalleryOpen() && e.getY() < galleryListView.top()) {
                animateGalleryListView(false);
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (galleryOpenCloseSpringAnimator != null || galleryOpenCloseAnimator != null || recordControl.isTouch()
                    || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch()
                    || inCheck()) {
                return false;
            }
            if (takingVideo || takingPhoto || currentPage != PAGE_CAMERA) {
                return false;
            }
            if (!scrollingX) {
                sty += distanceY;
                if (!scrollingY && Math.abs(sty) >= touchSlop) {
                    if (collageLayoutView != null) {
                        collageLayoutView.cancelTouch();
                    }
                    scrollingY = true;
                }
            }
            if (scrollingY) {
                int galleryMax = getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight
                        + ActionBar.getCurrentActionBarHeight());
                if (galleryListView == null || galleryListView.getTranslationY() >= galleryMax) {
                    ty = containerView.getTranslationY1();
                } else {
                    ty = galleryListView.getTranslationY() - galleryMax;
                }
                if (galleryListView != null && galleryListView.listView.canScrollVertically(-1)) {
                    distanceY = Math.max(0, distanceY);
                }
                ty -= distanceY;
                ty = Math.max(-galleryMax, ty);
                if (currentPage == PAGE_PREVIEW) {
                    ty = Math.max(0, ty);
                }
                if (ty >= 0) {
                    containerView.setTranslationY(ty);
                    if (galleryListView != null) {
                        galleryListView.setTranslationY(galleryMax);
                    }
                } else {
                    containerView.setTranslationY(0);
                    if (galleryListView == null) {
                        createGalleryListView();
                    }
                    galleryListView.setTranslationY(galleryMax + ty);
                }
            }
            if (!scrollingY) {
                stx += distanceX;
                if (!scrollingX && Math.abs(stx) >= touchSlop) {
                    if (collageLayoutView != null) {
                        collageLayoutView.cancelTouch();
                    }
                    scrollingX = true;
                }
            }
            if (scrollingX) {
                modeSwitcherView.scrollX(distanceX);
            }
            return true;
        }

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling
                    || zoomControlView != null && zoomControlView.isTouch() || inCheck()) {
                return false;
            }
            flingDetected = true;
            boolean r = false;
            if (scrollingY) {
                if (Math.abs(containerView.getTranslationY1()) >= dp(1)) {
                    if (velocityY > 0 && Math.abs(velocityY) > 2000 && Math.abs(velocityY) > Math.abs(velocityX) || dismissProgress > .4f) {
                        delegate.close(true);
                    } else {
                        animateContainerBack();
                    }
                    r = true;
                } else if (galleryListView != null && !galleryClosing) {
                    if (Math.abs(velocityY) > 200 && (!galleryListView.listView.canScrollVertically(-1) || !wasGalleryOpen)) {
                        animateGalleryListView(!takingVideo && velocityY < 0);
                        r = true;
                    } else {
                        animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
                        r = true;
                    }
                }
            }
            if (scrollingX) {
                r = modeSwitcherView.stopScroll(velocityX) || r;
            }
            galleryClosing = false;
            scrollingY = false;
            scrollingX = false;
            if (r && collageLayoutView != null) {
                collageLayoutView.cancelTouch();
            }
            return r;
        }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (cameraView != null) {
                cameraView.allowToTapFocus();
                return true;
            }
            return false;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited() || currentPage != PAGE_CAMERA) {
                return false;
            }
            cameraView.switchCamera();
            recordControl.rotateFlip(180);
            saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (cameraView != null) {
                cameraView.clearTapFocus();
            }
            return false;
        }

        @Override
        public boolean hasDoubleTap(MotionEvent e) {
            return currentPage == PAGE_CAMERA && cameraView != null && !awaitingPlayer && cameraView.isInited() && !takingPhoto
                    && !recordControl.isTouch() && !isGalleryOpen() && galleryListViewOpening == null;
        }
    }
    // endregion

    private boolean ignoreLayout;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Build.VERSION.SDK_INT < 21) {
            insetTop = AndroidUtilities.statusBarHeight;
            insetBottom = AndroidUtilities.navigationBarHeight;
        }

        final int W = MeasureSpec.getSize(widthMeasureSpec);
        final int H = MeasureSpec.getSize(heightMeasureSpec);
        final int w = W - insetLeft - insetRight;

        final int statusbar = insetTop;
        final int navbar = insetBottom;

        final int hFromW = (int) Math.ceil(w / 9f * 16f);
        underControls = dp(48);
        if (hFromW + underControls <= H - navbar) {
            previewW = w;
            previewH = hFromW;
            underStatusBar = previewH + underControls > H - navbar - statusbar;
        } else {
            underStatusBar = false;
            previewH = H - underControls - navbar - statusbar;
            previewW = (int) Math.ceil(previewH * 9f / 16f);
        }
        underControls = Utilities.clamp(H - previewH - (underStatusBar ? 0 : statusbar), dp(68), dp(48));

        int flags = getSystemUiVisibility();
        if (underStatusBar) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        setSystemUiVisibility(flags);

        containerView.measure(
                MeasureSpec.makeMeasureSpec(previewW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewH + underControls, MeasureSpec.EXACTLY)
        );
        flashViews.backgroundView.measure(
                MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
        );
        if (thanosEffect != null) {
            thanosEffect.measure(
                    MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
            );
        }
        if (changeDayNightView != null) {
            changeDayNightView.measure(
                    MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
            );
        }
        if (themeSheet != null) {
            themeSheet.measure(widthMeasureSpec, heightMeasureSpec);
        }

        if (galleryListView != null) {
            galleryListView.measure(MeasureSpec.makeMeasureSpec(previewW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY));
        }

        if (captionEdit != null) {
            EmojiView emojiView = captionEdit.editText.getEmojiView();
            if (measureKeyboardHeight() > AndroidUtilities.dp(20)) {
                ignoreLayout = true;
//                    captionEdit.editText.hideEmojiView();
                ignoreLayout = false;
            }
            if (emojiView != null) {
                emojiView.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(emojiView.getLayoutParams().height, MeasureSpec.EXACTLY)
                );
            }
        }

        if (paintView != null) {
            if (paintView.emojiView != null) {
                paintView.emojiView.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(paintView.emojiView.getLayoutParams().height, MeasureSpec.EXACTLY)
                );
            }
            if (paintView.reactionLayout != null) {
                measureChild(paintView.reactionLayout, widthMeasureSpec, heightMeasureSpec);
                if (paintView.reactionLayout.getReactionsWindow() != null) {
                    measureChild(paintView.reactionLayout.getReactionsWindow().windowView, widthMeasureSpec, heightMeasureSpec);
                }
            }
        }

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof DownloadButton.PreparingVideoToast) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(H, MeasureSpec.EXACTLY)
                );
            } else if (child instanceof Bulletin.ParentLayout) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.min(dp(340), H - (underStatusBar ? 0 : statusbar)), MeasureSpec.EXACTLY)
                );
            }
        }

        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (ignoreLayout) {
            return;
        }
        final int W = right - left;
        final int H = bottom - top;

        final int statusbar = insetTop;
        final int underControls = navbarContainer.getMeasuredHeight();

        final int T = underStatusBar ? 0 : statusbar;
        int l = insetLeft + (W - insetRight - previewW) / 2,
                r = insetLeft + (W - insetRight + previewW) / 2, t, b;
        if (underStatusBar) {
            t = T;
            b = T + previewH + underControls;
        } else {
            t = T + ((H - T - insetBottom) - previewH - underControls) / 2;
            if (openType == 1 && sourceRect.top + previewH + underControls < H - insetBottom) {
                t = (int) sourceRect.top;
            } else if (t - T < dp(40)) {
                t = T;
            }
            b = t + previewH + underControls;
        }

        containerView.layout(l, t, r, b);
        flashViews.backgroundView.layout(0, 0, W, H);
        if (thanosEffect != null) {
            thanosEffect.layout(0, 0, W, H);
        }
        if (changeDayNightView != null) {
            changeDayNightView.layout(0, 0, W, H);
        }

        if (galleryListView != null) {
            galleryListView.layout((W - galleryListView.getMeasuredWidth()) / 2, 0, (W + galleryListView.getMeasuredWidth()) / 2, H);
        }
        if (themeSheet != null) {
            themeSheet.layout((W - themeSheet.getMeasuredWidth()) / 2, H - themeSheet.getMeasuredHeight(), (W + themeSheet.getMeasuredWidth()) / 2,
                    H);
        }

        if (captionEdit != null) {
            EmojiView emojiView = captionEdit.editText.getEmojiView();
            if (emojiView != null) {
                emojiView.layout(insetLeft, H - insetBottom - emojiView.getMeasuredHeight(), W - insetRight, H - insetBottom);
            }
        }

        if (paintView != null) {
            if (paintView.emojiView != null) {
                paintView.emojiView.layout(insetLeft, H - insetBottom - paintView.emojiView.getMeasuredHeight(), W - insetRight, H - insetBottom);
            }
            if (paintView.reactionLayout != null) {
                paintView.reactionLayout.layout(insetLeft, insetTop, insetLeft + paintView.reactionLayout.getMeasuredWidth(),
                        insetTop + paintView.reactionLayout.getMeasuredHeight());
                View reactionsWindowView = paintView.reactionLayout.getReactionsWindow() != null
                        ? paintView.reactionLayout.getReactionsWindow().windowView : null;
                if (reactionsWindowView != null) {
                    reactionsWindowView.layout(insetLeft, insetTop, insetLeft + reactionsWindowView.getMeasuredWidth(),
                            insetTop + reactionsWindowView.getMeasuredHeight());
                }
            }
        }

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof DownloadButton.PreparingVideoToast) {
                child.layout(0, 0, W, H);
            } else if (child instanceof Bulletin.ParentLayout) {
                child.layout(0, t, child.getMeasuredWidth(), t + child.getMeasuredHeight());
            }
        }
    }

    public void drawBlurBitmap(Bitmap bitmap, float amount) {
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff000000);
        final float scale = (float) bitmap.getWidth() / getWidth();
        canvas.scale(scale, scale);

        TextureView textureView = previewView.getTextureView();
        if (textureView == null) {
            textureView = previewView.filterTextureView;
        }
        if (textureView != null) {
            canvas.save();
            canvas.translate(containerView.getX() + previewContainer.getX(), containerView.getY() + previewContainer.getY());
            int w = (int) (textureView.getWidth() / amount), h = (int) (textureView.getHeight() / amount);
            try {
                Bitmap textureBitmap = textureView.getBitmap(w, h);
                canvas.scale(1f / scale, 1f / scale);
                canvas.drawBitmap(textureBitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
                textureBitmap.recycle();
            } catch (Exception ignore) {
            }
            canvas.restore();
        }
        canvas.save();
        canvas.translate(containerView.getX(), containerView.getY());
        for (int i = 0; i < containerView.getChildCount(); ++i) {
            View child = containerView.getChildAt(i);
            canvas.save();
            canvas.translate(child.getX(), child.getY());
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            } else if (child == previewContainer) {
                for (int j = 0; j < previewContainer.getChildCount(); ++j) {
                    child = previewContainer.getChildAt(j);
                    if (child == previewView || child == cameraView || child == cameraViewThumb || child.getVisibility() != View.VISIBLE) {
                        continue;
                    }
                    canvas.save();
                    canvas.translate(child.getX(), child.getY());
                    child.draw(canvas);
                    canvas.restore();
                }
            } else {
                child.draw(canvas);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private class ContainerView extends FrameLayout {
        public ContainerView(Context context) {
            super(context);
        }

        public void updateBackground() {
            if (openType == 0) {
                setBackground(Theme.createRoundRectDrawable(dp(12), 0xff000000));
            } else {
                setBackground(null);
            }
        }

        private float translationY1;
        private float translationY2;

        public void setTranslationY2(float translationY2) {
            super.setTranslationY(this.translationY1 + (this.translationY2 = translationY2));
        }

        public float getTranslationY1() {
            return translationY1;
        }

        public float getTranslationY2() {
            return translationY2;
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY((this.translationY1 = translationY) + translationY2);

            dismissProgress = Utilities.clamp(translationY / getMeasuredHeight() * 4, 1, 0);
            checkBackgroundVisibility();
            MediaRecorderView.this.invalidate();

            final float scale = 1f - .1f * Utilities.clamp(getTranslationY() / AndroidUtilities.dp(320), 1, 0);
            setScaleX(scale);
            setScaleY(scale);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int t = underStatusBar ? insetTop : 0;

            final int w = right - left;
            final int h = bottom - top;

            previewContainer.layout(0, 0, previewW, previewH);
            previewContainer.setPivotX(previewW * .5f);
            actionBarContainer.layout(0, t, previewW, t + actionBarContainer.getMeasuredHeight());
            controlContainer.layout(0, previewH - controlContainer.getMeasuredHeight(), previewW, previewH);
            navbarContainer.layout(0, previewH, previewW, previewH + navbarContainer.getMeasuredHeight());
            captionContainer.layout(0, 0, previewW, previewH);
            if (captionEditOverlay != null) {
                captionEditOverlay.layout(0, 0, w, h);
            }
            flashViews.foregroundView.layout(0, 0, w, h);

            if (captionEdit.mentionContainer != null) {
                captionEdit.mentionContainer.layout(0, 0, previewW, previewH);
                captionEdit.updateMentionsLayoutPosition();
            }

            if (photoFilterView != null) {
                photoFilterView.layout(0, 0, photoFilterView.getMeasuredWidth(), photoFilterView.getMeasuredHeight());
            }
            if (paintView != null) {
                paintView.layout(0, 0, paintView.getMeasuredWidth(), paintView.getMeasuredHeight());
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof ItemOptions.DimView) {
                    child.layout(0, 0, w, h);
                }
            }

            setPivotX((right - left) / 2f);
            setPivotY(-h * .2f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int W = MeasureSpec.getSize(widthMeasureSpec);
            final int H = MeasureSpec.getSize(heightMeasureSpec);

            measureChildExactly(previewContainer, previewW, previewH);
            applyFilterMatrix();
            measureChildExactly(actionBarContainer, previewW, dp(56 + 56 + 38));
            measureChildExactly(controlContainer, previewW, dp(220));
            measureChildExactly(navbarContainer, previewW, underControls);
            measureChildExactly(captionContainer, previewW, previewH);
            measureChildExactly(flashViews.foregroundView, W, H);
            if (captionEditOverlay != null) {
                measureChildExactly(captionEditOverlay, W, H);
            }

            if (captionEdit.mentionContainer != null) {
                measureChildExactly(captionEdit.mentionContainer, previewW, previewH);
            }

            if (photoFilterView != null) {
                measureChildExactly(photoFilterView, W, H);
            }
            if (paintView != null) {
                measureChildExactly(paintView, W, H);
            }

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof ItemOptions.DimView) {
                    measureChildExactly(child, W, H);
                }
            }

            setMeasuredDimension(W, H);
        }

        private void measureChildExactly(View child, int width, int height) {
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        private final Paint topGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient topGradient;

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean r = super.drawChild(canvas, child, drawingTime);
            if (child == previewContainer) {
                final float top = underStatusBar ? AndroidUtilities.statusBarHeight : 0;
                if (topGradient == null) {
                    topGradient = new LinearGradient(0, top, 0, top + dp(72), new int[]{0x40000000, 0x00000000}, new float[]{top / (top + dp(72)), 1},
                            Shader.TileMode.CLAMP);
                    topGradientPaint.setShader(topGradient);
                }
                topGradientPaint.setAlpha(0xFF);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), dp(72 + 12) + top);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), topGradientPaint);
            }
            return r;
        }
    }

    public static final int PAGE_CAMERA = 0;
    public static final int PAGE_PREVIEW = 1;
    public static final int PAGE_COVER = 2;
    private int currentPage = PAGE_CAMERA;

    public static final int EDIT_MODE_NONE = -1;
    public static final int EDIT_MODE_PAINT = 0;
    public static final int EDIT_MODE_FILTER = 1;
    public static final int EDIT_MODE_TIMELINE = 2;
    private int currentEditMode = EDIT_MODE_NONE;

    private FrameLayout previewContainer;
    private FrameLayout actionBarContainer;
    private LinearLayout actionBarButtons;
    private FrameLayout controlContainer;
    private FrameLayout captionContainer;
    private FrameLayout navbarContainer;

    private FlashViews.ImageViewInvertable backButton;
    private SimpleTextView titleTextView;
    private BlurringShader.BlurManager blurManager;
    private PreviewView.TextureViewHolder videoTextureHolder;
    private View captionEditOverlay;

    private CollageLayout lastCollageLayout;

    /* PAGE_CAMERA */
    private CollageLayoutView2 collageLayoutView;
    private ImageView cameraViewThumb;
    private DualCameraView cameraView;

    private int flashButtonResId;
    private ToggleButton2 flashButton;
    private ToggleButton dualButton;
    private CollageLayoutButton collageButton;
    private ToggleButton2 collageRemoveButton;
    private CollageLayoutButton.CollageLayoutListView collageListView;
    private VideoTimerView videoTimerView;
    private boolean wasGalleryOpen;
    private boolean galleryClosing;
    private GalleryListView galleryListView;
    private DraftSavedHint draftSavedHint;
    private RecordControl recordControl;
    private PhotoVideoSwitcherView modeSwitcherView;
    private HintTextView hintTextView;
    private HintTextView collageHintTextView;
    private ZoomControlView zoomControlView;
    private HintView2 cameraHint;
    private StoryThemeSheet themeSheet;

    /* PAGE_PREVIEW */
    private PreviewView previewView;
    private FrameLayout videoTimelineContainerView;
    private TimelineView timelineView;
    private VideoTimeView videoTimeView;
    private PreviewButtons previewButtons;
    private CaptionStory captionEdit;
    private DownloadButton downloadButton;
    private RLottieDrawable muteButtonDrawable;
    private RLottieImageView muteButton;
    private RLottieDrawable themeButtonDrawable;
    private ImageView themeButton;
    private PlayPauseButton playButton;
    private HintView2 muteHint;
    private HintView2 dualHint;
    private HintView2 savedDualHint;
    private HintView2 removeCollageHint;
    private PreviewHighlightView previewHighlight;
    private TrashView trash;
    private RoundVideoRecorder currentRoundRecorder;

    /* PAGE_COVER */
    private TimelineView coverTimelineView;
    private ButtonWithCounterView coverButton;

    /* EDIT_MODE_PAINT */
    private PaintView paintView;
    private RenderView paintViewRenderView;
    private View paintViewRenderInputView;
    private View paintViewTextDim;
    private View paintViewEntitiesView;
    private View paintViewSelectionContainerView;

    /* EDIT_MODE_FILTER */
    private PhotoFilterView photoFilterView;
    private PhotoFilterView.EnhanceView photoFilterEnhanceView;
    private TextureView photoFilterViewTextureView;
    private PhotoFilterBlurControl photoFilterViewBlurControl;
    private PhotoFilterCurvesControl photoFilterViewCurvesControl;

    private File outputFile;
    private StoryEntry outputEntry;
    private boolean fromGallery;
    private long coverValue;

    private boolean videoError;

    private boolean isVideo = false;
    private boolean takingPhoto = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private boolean awaitingPlayer = false;

    private float cameraZoom;

    private int shiftDp = -3;
    private boolean showSavedDraftHint;

    public int getCurrentPage() {
        return currentPage;
    }

    public StoryEntry getOutputEntry() {
        return outputEntry;
    }

    public void clearOutputEntry() {
        outputEntry = null;
    }

    public long close(boolean animated) {
        if (outputEntry != null && !outputEntry.isEditSaved) {
            if (wasSend && outputEntry.isEdit || outputEntry.draftId != 0) {
                outputEntry.editedMedia = false;
            }
            outputEntry.destroy(false);
        }
        outputEntry = null;

        long pos = previewView.release();

        if (previewView != null && !animated) {
            previewView.set(null);
        }

        return pos;
    }

    public void onOpened() {
        wasSend = false;
        if (openType == 1) {
            previewContainer.setAlpha(1f);
            previewContainer.setTranslationX(0);
            previewContainer.setTranslationY(0);
            actionBarContainer.setAlpha(1f);
            controlContainer.setAlpha(1f);
            setBackgroundColor(0xff000000);
            if (currentPage == PAGE_COVER) {
                coverButton.setAlpha(1f);
            }
        }

        if (whenOpenDone != null) {
            whenOpenDone.run();
            whenOpenDone = null;
        } else {
            onResume(false);
        }

        if (outputEntry != null && outputEntry.isRepost) {
            createPhotoPaintView();
            hidePhotoPaintView();
            createFilterPhotoView();
        } else if (outputEntry != null && outputEntry.isRepostMessage) {
            if (outputEntry.isVideo) {
                previewView.setupVideoPlayer(outputEntry, null, 0);
            }
            createFilterPhotoView();
        }

        addNotificationObservers();
    }

    public void onClosed() {
        if (cameraView != null) {
            if (takingVideo) {
                CameraController.getInstance().stopVideoRecording(cameraView.getCameraSession(), false);
            }
            destroyCameraView(false);
        }
        if (previewView != null) {
            previewView.set(null);
        }
        destroyPhotoPaintView();
        destroyPhotoFilterView();
        if (outputFile != null && !wasSend) {
            try {
                outputFile.delete();
            } catch (Exception ignore) {
            }
        }
        outputFile = null;
        if (whenOpenDone != null) {
            whenOpenDone = null;
        }
        lastGalleryScrollPosition = null;
        if (captionContainer != null) {
            Bulletin.removeDelegate(captionContainer);
        }
        if (collageLayoutView != null) {
            collageLayoutView.clear(true);
        }

        removeNotificationObservers();
    }

    public void inflateEntryWithCover(Runnable onDone) {
        if (previewView != null && !outputEntry.coverSet && currentPage != PAGE_COVER) {
            outputEntry.cover = previewView.getCurrentPosition();
            previewView.getCoverBitmap(bitmap -> {
                if (outputEntry == null) {
                    return;
                }
                if (outputEntry.coverBitmap != null) {
                    outputEntry.coverBitmap.recycle();
                }
                outputEntry.coverBitmap = bitmap;
                onDone.run();
            }, previewView, paintViewRenderView, paintViewEntitiesView);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Context context = getContext();

        if (Build.VERSION.SDK_INT >= 21) {
            setFitsSystemWindows(true);
            setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    final WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
                    final androidx.core.graphics.Insets i = insetsCompat.getInsets(
                            WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                    insetTop = Math.max(i.top, insets.getStableInsetTop());
                    insetBottom = Math.max(i.bottom, insets.getStableInsetBottom());
                    insetLeft = Math.max(i.left, insets.getStableInsetLeft());
                    insetRight = Math.max(i.right, insets.getStableInsetRight());
                    insetTop = Math.max(insetTop, AndroidUtilities.statusBarHeight);
                    requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }
        setFocusable(true);

        flashViews = new FlashViews(context, windowManager, this, windowLayoutParams);
        flashViews.add(new FlashViews.Invertable() {
            @Override
            public void setInvert(float invert) {
                AndroidUtilities.setLightNavigationBar(MediaRecorderView.this, invert > 0.5f);
                AndroidUtilities.setLightStatusBar(MediaRecorderView.this, invert > 0.5f);
            }

            @Override
            public void invalidate() {
            }
        });
        addView(flashViews.backgroundView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(containerView = new ContainerView(context));
        containerView.addView(previewContainer = new FrameLayout(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (previewTouchable != null) {
                    previewTouchable.onTouch(event);
                    return true;
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                if (photoFilterViewCurvesControl != null) {
                    photoFilterViewCurvesControl.setActualArea(0, 0, photoFilterViewCurvesControl.getMeasuredWidth(),
                            photoFilterViewCurvesControl.getMeasuredHeight());
                }
                if (photoFilterViewBlurControl != null) {
                    photoFilterViewBlurControl.setActualAreaSize(photoFilterViewBlurControl.getMeasuredWidth(),
                            photoFilterViewBlurControl.getMeasuredHeight());
                }
            }

            private final Rect leftExclRect = new Rect();
            private final Rect rightExclRect = new Rect();

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final int w = right - left;
                    final int h = bottom - top;
                    leftExclRect.set(0, h - dp(120), dp(40), h);
                    rightExclRect.set(w - dp(40), h - dp(120), w, h);
                    setSystemGestureExclusionRects(Arrays.asList(leftExclRect, rightExclRect));
                }
            }

            private RenderNode renderNode;

            @Override
            protected void dispatchDraw(@NonNull Canvas c) {
                boolean endRecording = false;
                Canvas canvas = c;
                if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated() && !AndroidUtilities.makingGlobalBlurBitmap) {
                    if (renderNode == null) {
                        renderNode = new RenderNode("StoryRecorder.PreviewView");
                    }
                    renderNode.setPosition(0, 0, getWidth(), getHeight());
                    canvas = renderNode.beginRecording();
                    endRecording = true;
                }
                super.dispatchDraw(canvas);
                if (endRecording && Build.VERSION.SDK_INT >= 31) {
                    renderNode.endRecording();
                    if (blurManager != null) {
                        blurManager.setRenderNode(this, renderNode, 0xFF1F1F1F);
                    }
                    c.drawRenderNode(renderNode);
                }
            }
        });
        containerView.addView(flashViews.foregroundView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        blurManager = new BlurringShader.BlurManager(previewContainer);
        videoTextureHolder = new PreviewView.TextureViewHolder();
        containerView.addView(actionBarContainer = new FrameLayout(context)); // 150dp
        containerView.addView(controlContainer = new FrameLayout(context)); // 220dp
        containerView.addView(captionContainer = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY && captionEdit != null) {
                    super.setTranslationY(translationY);
                    captionEdit.updateMentionsLayoutPosition();
                }
            }
        }); // full height
        captionContainer.setVisibility(View.GONE);
        captionContainer.setAlpha(0f);
        containerView.addView(navbarContainer = new FrameLayout(context)); // 48dp

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return dp(56);
            }

            @Override
            public int getBottomOffset(int tag) {
                return Bulletin.Delegate.super.getBottomOffset(tag);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });

        collageLayoutView = new CollageLayoutView2(context, blurManager, containerView, resourcesProvider) {
            @Override
            protected void onLayoutUpdate(CollageLayout layout) {
                collageListView.setVisible(false, true);
                if (layout != null && layout.parts.size() > 1) {
                    collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout = layout), true);
                    collageButton.setSelected(true, true);
                } else {
                    collageButton.setSelected(false, true);
                }
                updateActionBarButtons(true);
            }
        };
        collageLayoutView.setCancelGestures(this::cancelGestures);
        collageLayoutView.setResetState(() -> {
            updateActionBarButtons(true);
        });
        previewContainer.addView(collageLayoutView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        cameraViewThumb = new ImageView(context);
        cameraViewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cameraViewThumb.setOnClickListener(v -> {
            if (noCameraPermission) {
                requestCameraPermission(true);
            }
        });
        cameraViewThumb.setClickable(true);
        previewContainer.addView(cameraViewThumb, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        previewContainer.setBackgroundColor(openType == 1 || openType == 0 ? 0 : 0xff1f1f1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            previewContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), dp(12));
                }
            });
            previewContainer.setClipToOutline(true);
        }
        photoFilterEnhanceView = new PhotoFilterView.EnhanceView(context, this::createFilterPhotoView);
        previewView = new PreviewView(context, blurManager, videoTextureHolder) {
            @Override
            public boolean additionalTouchEvent(MotionEvent ev) {
                if (captionEdit != null && captionEdit.isRecording()) {
                    return false;
                }
                return photoFilterEnhanceView.onTouch(ev);
            }

            @Override
            public void applyMatrix() {
                super.applyMatrix();
                applyFilterMatrix();
            }

            @Override
            public void onEntityDraggedTop(boolean value) {
                previewHighlight.show(true, value, actionBarContainer);
            }

            @Override
            public void onEntityDraggedBottom(boolean value) {
                previewHighlight.updateCaption(captionEdit.getText());
//                previewHighlight.show(false, value, null);
            }

            @Override
            public void onEntityDragEnd(boolean delete) {
                controlContainer.clearAnimation();
                controlContainer.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                trash.onDragInfo(false, delete);
                trash.clearAnimation();
                trash.animate().alpha(0f).withEndAction(() -> {
                    trash.setVisibility(View.GONE);
                }).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).setStartDelay(delete ? 500 : 0).start();
                super.onEntityDragEnd(delete);
            }

            @Override
            public void onEntityDragStart() {
                controlContainer.clearAnimation();
                controlContainer.animate().alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();

                trash.setVisibility(View.VISIBLE);
                trash.setAlpha(0f);
                trash.clearAnimation();
                trash.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            }

            @Override
            public void onEntityDragTrash(boolean enter) {
                trash.onDragInfo(enter, false);
            }

            @Override
            protected void onTimeDrag(boolean dragStart, long time, boolean dragEnd) {
                videoTimeView.setTime(time, !dragStart);
                videoTimeView.show(!dragEnd, true);
            }

            @Override
            public void onRoundSelectChange(boolean selected) {
                if (paintView == null) {
                    return;
                }
                if (!selected && paintView.getSelectedEntity() instanceof RoundView) {
                    paintView.selectEntity(null);
                } else if (selected && !(paintView.getSelectedEntity() instanceof RoundView) && paintView.findRoundView() != null) {
                    paintView.selectEntity(paintView.findRoundView());
                }
            }

            @Override
            public void onRoundRemove() {
                if (previewView != null) {
                    previewView.setupRound(null, null, true);
                }
                if (paintView != null) {
                    paintView.deleteRound();
                }
                if (captionEdit != null) {
                    captionEdit.setHasRoundVideo(false);
                }
                if (outputEntry != null) {
                    if (outputEntry.round != null) {
                        try {
                            outputEntry.round.delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.round = null;
                    }
                    if (outputEntry.roundThumb != null) {
                        try {
                            new File(outputEntry.roundThumb).delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.roundThumb = null;
                    }
                }
            }

            @Override
            protected void invalidateTextureViewHolder() {
                if (outputEntry != null && outputEntry.isRepostMessage && outputEntry.isVideo && paintView != null
                        && paintView.entitiesView != null) {
                    for (int i = 0; i < paintView.entitiesView.getChildCount(); ++i) {
                        View child = paintView.entitiesView.getChildAt(i);
                        if (child instanceof MessageEntityView) {
                            ((MessageEntityView) child).invalidateAll();
                        }
                    }
                }
            }

            @Override
            public void onAudioChanged() {
                if (paintView != null) {
                    paintView.setHasAudio(outputEntry != null && outputEntry.audioPath != null);
                }
            }
        };
        previewView.setCollageView(collageLayoutView);
        previewView.invalidateBlur = this::invalidateBlur;
        previewView.setOnTapListener(() -> {
            if (currentEditMode != EDIT_MODE_NONE || currentPage != PAGE_PREVIEW || captionEdit.keyboardShown
                    || captionEdit != null && captionEdit.isRecording()) {
                return;
            }
            if (timelineView.onBackPressed()) {
                return;
            }
            switchToEditMode(EDIT_MODE_PAINT, true);
            if (paintView != null) {
                paintView.openText();
                paintView.enteredThroughText = true;
            }
        });
        previewView.setVisibility(View.GONE);
        previewView.whenError(() -> {
            videoError = true;
            previewButtons.setShareEnabled(false);
            downloadButton.showFailedVideo();
        });
        previewContainer.addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        previewContainer.addView(photoFilterEnhanceView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        captionEdit = new CaptionStory(context, this, this, containerView, resourcesProvider, blurManager) {
            @Override
            protected boolean ignoreTouches(float x, float y) {
                if (paintView == null || paintView.entitiesView == null || captionEdit.keyboardShown) {
                    return false;
                }
                x += captionEdit.getX();
                y += captionEdit.getY();
                x += captionContainer.getX();
                y += captionContainer.getY();
                x -= previewContainer.getX();
                y -= previewContainer.getY();

                for (int i = 0; i < paintView.entitiesView.getChildCount(); ++i) {
                    View view = paintView.entitiesView.getChildAt(i);
                    if (view instanceof EntityView) {
                        org.telegram.ui.Components.Rect rect = ((EntityView) view).getSelectionBounds();
                        AndroidUtilities.rectTmp.set(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height);
                        if (AndroidUtilities.rectTmp.contains(x, y)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
            }

            @Override
            protected void drawBlurBitmap(Bitmap bitmap, float amount) {
                MediaRecorderView.this.drawBlurBitmap(bitmap, amount);
                super.drawBlurBitmap(bitmap, amount);
            }

            @Override
            protected boolean captionLimitToast() {
                if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
                    return false;
                }
                Bulletin visibleBulletin = Bulletin.getVisibleBulletin();
                if (visibleBulletin != null && visibleBulletin.tag == 2) {
                    return false;
                }
                final int symbols = MessagesController.getInstance(currentAccount).storyCaptionLengthLimitPremium;
                final int times = Math.round((float) symbols / MessagesController.getInstance(currentAccount).storyCaptionLengthLimitDefault);
                SpannableStringBuilder text = AndroidUtilities.replaceTags(
                        LocaleController.formatPluralString("CaptionPremiumSubtitle", times, "" + symbols));
                int startIndex = text.toString().indexOf("__");
                if (startIndex >= 0) {
                    text.replace(startIndex, startIndex + 2, "");
                    int endIndex = text.toString().indexOf("__");
                    if (endIndex >= 0) {
                        text.replace(endIndex, endIndex + 2, "");
                        text.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider)), startIndex, endIndex,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        text.setSpan(new ClickableSpan() {
                            @Override
                            public void updateDrawState(@NonNull TextPaint ds) {
                                ds.setUnderlineText(false);
                            }

                            @Override
                            public void onClick(@NonNull View widget) {
                                openPremium();
                            }
                        }, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                Bulletin bulletin = BulletinFactory.of(captionContainer, resourcesProvider)
                        .createSimpleBulletin(R.raw.caption_limit, getString(R.string.CaptionPremiumTitle), text);
                bulletin.tag = 2;
                bulletin.setDuration(5000);
                bulletin.show(false);
                return true;
            }

            @Override
            protected void onCaptionLimitUpdate(boolean overLimit) {
                previewButtons.setShareEnabled(
                        !videoError && !overLimit && (!MessagesController.getInstance(currentAccount).getStoriesController().hasStoryLimit() || (
                                outputEntry != null && outputEntry.isEdit)));
            }

            @Override
            public boolean canRecord() {
                return requestAudioPermission();
            }

            @Override
            public void putRecorder(RoundVideoRecorder recorder) {
                if (currentRoundRecorder != null) {
                    currentRoundRecorder.destroy(true);
                }
                if (previewView != null) {
                    previewView.mute(true);
                    previewView.seek(0);
                }
                recorder.onDone((file, thumb, duration) -> {
                    if (previewView != null) {
                        previewView.mute(false);
                        previewView.seek(0);
                    }
                    if (outputEntry != null) {
                        outputEntry.round = file;
                        outputEntry.roundThumb = thumb;
                        outputEntry.roundDuration = duration;
                        outputEntry.roundLeft = 0;
                        outputEntry.roundRight = 1;
                        outputEntry.roundOffset = 0;
                        outputEntry.roundVolume = 1f;

                        createPhotoPaintView();
                        if (previewView != null && paintView != null) {
                            RoundView roundView = paintView.createRound(outputEntry.roundThumb, true);
                            setHasRoundVideo(true);
                            previewView.setupRound(outputEntry, roundView, true);

                            recorder.hideTo(roundView);
                        } else {
                            recorder.destroy(false);
                        }
                    }
                });
                recorder.onDestroy(() -> {
                    if (previewView != null) {
                        previewView.mute(false);
                        previewView.seek(0);
                    }
                });
                previewContainer.addView(currentRoundRecorder = recorder,
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }

            @Override
            public void removeRound() {
                if (previewView != null) {
                    previewView.setupRound(null, null, true);
                }
                if (paintView != null) {
                    paintView.deleteRound();
                }
                if (captionEdit != null) {
                    captionEdit.setHasRoundVideo(false);
                }
                if (outputEntry != null) {
                    if (outputEntry.round != null) {
                        try {
                            outputEntry.round.delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.round = null;
                    }
                    if (outputEntry.roundThumb != null) {
                        try {
                            new File(outputEntry.roundThumb).delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.roundThumb = null;
                    }
                }
            }

            @Override
            public void invalidateDrawOver2() {
                if (captionEditOverlay != null) {
                    captionEditOverlay.invalidate();
                }
            }

            @Override
            public boolean drawOver2FromParent() {
                return true;
            }

            @Override
            public int getTimelineHeight() {
                if (videoTimelineContainerView != null && timelineView != null && timelineView.getVisibility() == View.VISIBLE) {
                    return timelineView.getTimelineHeight();
                }
                return 0;
            }

            @Override
            protected boolean customBlur() {
                return blurManager.hasRenderNode();
            }

            private final Path path = new Path();

            @Override
            protected void drawBlur(BlurringShader.StoryBlurDrawer blur, Canvas canvas, RectF rect, float r, boolean text, float ox, float oy,
                                    boolean thisView, float alpha) {
                if (!canvas.isHardwareAccelerated()) {
                    return;
                }
                canvas.save();
                path.rewind();
                path.addRoundRect(rect, r, r, Path.Direction.CW);
                canvas.clipPath(path);
                canvas.translate(ox, oy);
                blur.drawRect(canvas, 0, 0, alpha);
                canvas.restore();
            }
        };
        captionEdit.setAccount(currentAccount);
        captionEdit.setUiBlurBitmap(this::getUiBlurBitmap);
        Bulletin.addDelegate(captionContainer, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return captionEdit.getEditTextHeight() + AndroidUtilities.dp(12);
            }
        });
        captionEdit.setOnHeightUpdate(height -> {
            if (videoTimelineContainerView != null) {
                videoTimelineContainerView.setTranslationY(
                        currentEditMode == EDIT_MODE_TIMELINE ? dp(68) : -(captionEdit.getEditTextHeight() + dp(12)) + dp(64));
            }
            Bulletin visibleBulletin = Bulletin.getVisibleBulletin();
            if (visibleBulletin != null && visibleBulletin.tag == 2) {
                visibleBulletin.updatePosition();
            }
        });
        captionEdit.setOnPeriodUpdate(period -> {
            if (outputEntry != null) {
                outputEntry.period = period;
                MessagesController.getGlobalMainSettings().edit().putInt("story_period", period).apply();
//                privacySelector.setStoryPeriod(period);
            }
        });
        if (selectedDialogId != 0) {
            captionEdit.setDialogId(selectedDialogId);
        }
        captionEdit.setOnPremiumHint(this::showPremiumPeriodBulletin);
        captionEdit.setOnKeyboardOpen(open -> {
            if (open && timelineView != null) {
                timelineView.onBackPressed();
            }
            previewView.updatePauseReason(2, open);
            videoTimelineContainerView.clearAnimation();
            videoTimelineContainerView.animate().alpha(open ? 0f : 1f).setDuration(120).start();
            Bulletin visibleBulletin = Bulletin.getVisibleBulletin();
            if (visibleBulletin != null && visibleBulletin.tag == 2) {
                visibleBulletin.updatePosition();
            }
        });
        captionEditOverlay = new View(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(captionContainer.getX() + captionEdit.getX(), captionContainer.getY() + captionEdit.getY());
                captionEdit.drawOver2(canvas, captionEdit.getBounds(), captionEdit.getOver2Alpha());
                canvas.restore();
            }
        };
        containerView.addView(captionEditOverlay);

        timelineView = new TimelineView(context, containerView, previewContainer, resourcesProvider, blurManager);
        timelineView.setOnTimelineClick(() -> {
            if (currentPage != PAGE_PREVIEW) {
                return;
            }
            switchToEditMode(EDIT_MODE_TIMELINE, true);
        });
        previewView.setVideoTimelineView(timelineView);
        timelineView.setVisibility(View.GONE);
        timelineView.setAlpha(0f);
        videoTimelineContainerView = new FrameLayout(context);
        videoTimelineContainerView.addView(timelineView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TimelineView.heightDp(), Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 0));
        videoTimeView = new VideoTimeView(context);
        videoTimeView.setVisibility(View.GONE);
        videoTimeView.show(false, false);
        videoTimelineContainerView.addView(videoTimeView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 25, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        captionContainer.addView(videoTimelineContainerView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TimelineView.heightDp() + 25, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0,
                        68));
        captionContainer.addView(captionEdit,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 200, 0,
                        0));
        collageLayoutView.setTimelineView(timelineView);
        collageLayoutView.setPreviewView(previewView);

        coverTimelineView = new TimelineView(context, containerView, previewContainer, resourcesProvider, blurManager);
        coverTimelineView.setCover();
        coverTimelineView.setVisibility(View.GONE);
        coverTimelineView.setAlpha(0f);
        captionContainer.addView(coverTimelineView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TimelineView.heightDp(), Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 6));

        backButton = new FlashViews.ImageViewInvertable(context);
        backButton.setContentDescription(getString(R.string.AccDescrGoBack));
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageResource(R.drawable.msg_photo_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        backButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        backButton.setOnClickListener(e -> {
            if (awaitingPlayer) {
                return;
            }
            onBackPressed();
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));
        flashViews.add(backButton);

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(20);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        titleTextView.setTextColor(0xffffffff);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setText(getString(R.string.RecorderNewStory));
        titleTextView.getPaint().setShadowLayer(dpf2(1), 0, 1, 0x40000000);
        titleTextView.setAlpha(0f);
        titleTextView.setVisibility(View.GONE);
        titleTextView.setEllipsizeByGradient(true);
        titleTextView.setRightPadding(AndroidUtilities.dp(144));
        actionBarContainer.addView(titleTextView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL, 71, 0, 0, 0));

        actionBarButtons = new LinearLayout(context);
        actionBarButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionBarButtons.setGravity(Gravity.RIGHT);
        actionBarContainer.addView(actionBarButtons,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.RIGHT | Gravity.FILL_HORIZONTAL, 0, 0, 8, 0));

        downloadButton = new DownloadButton(context, done -> {
            applyPaint();
            applyPaintMessage();
            applyFilter(done);
        }, currentAccount, this, resourcesProvider);

        muteHint = new HintView2(getContext(), HintView2.DIRECTION_TOP)
                .setJoint(1, -77 + 8 - 2)
                .setDuration(2000)
                .setBounce(false)
                .setAnimatedTextHacks(true, true, false);
        muteHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(muteHint,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        muteButton = new RLottieImageView(context);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        muteButton.setImageResource(outputEntry != null && outputEntry.muted ? R.drawable.media_unmute : R.drawable.media_mute);
        muteButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        muteButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        muteButton.setOnClickListener(e -> {
            if (outputEntry == null || awaitingPlayer) {
                return;
            }
            outputEntry.muted = !outputEntry.muted;
            if (outputEntry.collageContent != null) {
                for (StoryEntry entry : outputEntry.collageContent) {
                    entry.muted = outputEntry.muted;
                }
            }
            final boolean hasMusic = !TextUtils.isEmpty(outputEntry.audioPath);
            final boolean hasRound = outputEntry.round != null;
            if (currentEditMode == EDIT_MODE_NONE) {
                muteHint.setText(
                        outputEntry.muted ?
                                getString(hasMusic || hasRound ? R.string.StoryOriginalSoundMuted : R.string.StorySoundMuted) :
                                getString(hasMusic || hasRound ? R.string.StoryOriginalSoundNotMuted : R.string.StorySoundNotMuted),
                        muteHint.shown()
                );
                muteHint.show();
            }
            setIconMuted(outputEntry.muted, true);
            previewView.checkVolumes();
        });
        muteButton.setVisibility(View.GONE);
        muteButton.setAlpha(0f);

        playButton = new PlayPauseButton(context);
        playButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        playButton.setVisibility(View.GONE);
        playButton.setAlpha(0f);
        playButton.setOnClickListener(e -> {
            boolean playing = previewView.isPlaying();
            previewView.play(!playing);
            playButton.drawable.setPause(!playing, true);
        });

        actionBarButtons.addView(playButton, LayoutHelper.createLinear(46, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarButtons.addView(muteButton, LayoutHelper.createLinear(46, 56, Gravity.TOP | Gravity.RIGHT));
        actionBarButtons.addView(downloadButton, LayoutHelper.createFrame(46, 56, Gravity.TOP | Gravity.RIGHT));

        flashButton = new ToggleButton2(context);
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setOnClickListener(e -> {
            if (cameraView == null || awaitingPlayer) {
                return;
            }
            String current = getCurrentFlashMode();
            String next = getNextFlashMode();
            if (current == null || current.equals(next)) {
                return;
            }
            setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });
        flashButton.setOnLongClickListener(e -> {
            if (cameraView == null || !cameraView.isFrontface()) {
                return false;
            }

            checkFrontfaceFlashModes();
            flashButton.setSelected(true);
            flashViews.previewStart();
            ItemOptions.makeOptions(containerView, resourcesProvider, flashButton)
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_WARMTH)
                                    .setValue(flashViews.warmth)
                                    .setOnValueChange(v -> {
                                        flashViews.setWarmth(v);
                                    })
                    )
                    .addSpaceGap()
                    .addView(
                            new SliderView(getContext(), SliderView.TYPE_INTENSITY)
                                    .setMinMax(.65f, 1f)
                                    .setValue(flashViews.intensity)
                                    .setOnValueChange(v -> {
                                        flashViews.setIntensity(v);
                                    })
                    )
                    .setOnDismiss(() -> {
                        saveFrontFaceFlashMode();
                        flashViews.previewEnd();
                        flashButton.setSelected(false);
                    })
                    .setDimAlpha(0)
                    .setGravity(Gravity.RIGHT)
                    .translate(dp(46), -dp(4))
                    .setBackgroundColor(0xbb1b1b1b)
                    .show();
            return true;
        });
        flashButton.setVisibility(View.GONE);
        flashButton.setAlpha(0f);
        flashViews.add(flashButton);
        actionBarContainer.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
        dualButton.setOnClickListener(v -> {
            if (cameraView == null || currentPage != PAGE_CAMERA) {
                return;
            }
            cameraView.toggleDual();
            dualButton.setValue(cameraView.isDual());

            dualHint.hide();
            MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", 2).apply();
            if (savedDualHint.shown()) {
                MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", 2).apply();
            }
            savedDualHint.hide();
        });
        final boolean dualCameraAvailable = DualCameraView.dualAvailableStatic(context);
        dualButton.setVisibility(dualCameraAvailable ? View.VISIBLE : View.GONE);
        dualButton.setAlpha(dualCameraAvailable ? 1.0f : 0.0f);
        flashViews.add(dualButton);
        actionBarContainer.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageButton = new CollageLayoutButton(context);
        collageButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        if (lastCollageLayout == null) {
            lastCollageLayout = CollageLayout.getLayouts().get(6);
        }
        collageButton.setOnClickListener(v -> {
            if (currentPage != PAGE_CAMERA || animatedRecording) {
                return;
            }
            if (cameraView != null && cameraView.isDual()) {
                cameraView.toggleDual();
            }
            if (!collageListView.isVisible() && !collageLayoutView.hasLayout()) {
                collageLayoutView.setLayout(lastCollageLayout, true);
                collageListView.setSelected(lastCollageLayout);
                collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), true);
                collageButton.setSelected(true);
                if (cameraView != null) {
                    cameraView.recordHevc = !collageLayoutView.hasLayout();
                }
            }
            collageListView.setVisible(!collageListView.isVisible(), true);
            updateActionBarButtons(true);
        });
        collageButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(lastCollageLayout), false);
        collageButton.setSelected(false);
        collageButton.setVisibility(View.VISIBLE);
        collageButton.setAlpha(1.0f);
        flashViews.add(collageButton);
        actionBarContainer.addView(collageButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageRemoveButton = new ToggleButton2(context);
        collageRemoveButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        collageRemoveButton.setIcon(new CollageLayoutButton.CollageLayoutDrawable(new CollageLayout("../../.."), true), false);
        collageRemoveButton.setVisibility(View.GONE);
        collageRemoveButton.setAlpha(0.0f);
        collageRemoveButton.setOnClickListener(v -> {
            collageLayoutView.setLayout(null, true);
            collageLayoutView.clear(true);
            collageListView.setSelected(null);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageListView.setVisible(false, true);
            updateActionBarButtons(true);
        });
        flashViews.add(collageRemoveButton);
        actionBarContainer.addView(collageRemoveButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        collageListView = new CollageLayoutButton.CollageLayoutListView(context, flashViews);
        collageListView.listView.scrollToPosition(6);
        collageListView.setSelected(null);
        collageListView.setOnLayoutClick(layout -> {
            collageLayoutView.setLayout(lastCollageLayout = layout, true);
            collageListView.setSelected(layout);
            if (cameraView != null) {
                cameraView.recordHevc = !collageLayoutView.hasLayout();
            }
            collageButton.setDrawable(new CollageLayoutButton.CollageLayoutDrawable(layout));
            setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), true);
            recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);
        });
        actionBarContainer.addView(collageListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.RIGHT));

        dualHint = new HintView2(getContext(), HintView2.DIRECTION_TOP)
                .setJoint(1, -20)
                .setDuration(5000)
                .setCloseButton(true)
                .setText(getString(R.string.StoryCameraDualHint))
                .setOnHiddenListener(() -> MessagesController.getGlobalMainSettings().edit()
                        .putInt("storydualhint", MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) + 1).apply());
        dualHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(dualHint,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        savedDualHint = new HintView2(getContext(), HintView2.DIRECTION_RIGHT)
                .setJoint(0, 56 / 2F)
                .setDuration(5000)
                .setMultilineText(true);
        actionBarContainer.addView(savedDualHint,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 52, 0));

        removeCollageHint = new HintView2(getContext(), HintView2.DIRECTION_TOP)
                .setJoint(1, -20)
                .setDuration(5000)
                .setText(LocaleController.getString(R.string.StoryCollageRemoveGrid));
        removeCollageHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(removeCollageHint,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        actionBarContainer.addView(videoTimerView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 56, 0, 56, 0));
        flashViews.add(videoTimerView);

        if (Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }

        recordControl = new RecordControl(context);
        recordControl.setDelegate(recordControlDelegate);
        recordControl.startAsVideo(isVideo);
        controlContainer.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(recordControl);
        recordControl.setCollageProgress(collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f, true);

        cameraHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM)
                .setMultilineText(true)
                .setText(getString(R.string.StoryCameraHint2))
                .setMaxWidth(320)
                .setDuration(5000L)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER);
        controlContainer.addView(cameraHint,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM, 0, 0, 0, 100));

        zoomControlView = new ZoomControlView(context);
        zoomControlView.enabledTouch = false;
        zoomControlView.setAlpha(0.0f);
        controlContainer.addView(zoomControlView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 100 + 8));
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });
        zoomControlView.setZoom(cameraZoom = 0, false);

        modeSwitcherView = new PhotoVideoSwitcherView(context) {
            @Override
            protected boolean allowTouch() {
                return !inCheck();
            }
        };
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
            if (takingPhoto || takingVideo) {
                return;
            }

            isVideo = newIsVideo;
            showVideoTimer(isVideo && !collageListView.isVisible(), true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> {
            recordControl.startAsVideoT(t);
        });
        navbarContainer.addView(modeSwitcherView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        flashViews.add(modeSwitcherView);

        hintTextView = new HintTextView(context);
        navbarContainer.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(hintTextView);

        collageHintTextView = new HintTextView(context);
        collageHintTextView.setText(LocaleController.getString(R.string.StoryCollageReorderHint), false);
        collageHintTextView.setAlpha(0.0f);
        navbarContainer.addView(collageHintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));
        flashViews.add(collageHintTextView);

        coverButton = new ButtonWithCounterView(context, resourcesProvider);
        coverButton.setVisibility(View.GONE);
        coverButton.setAlpha(0f);
        coverButton.setText(LocaleController.getString(R.string.StoryCoverSave), false);
        coverButton.setOnClickListener(v -> {
            if (outputEntry == null) {
                return;
            }
            outputEntry.coverSet = true;
            outputEntry.cover = coverValue;
            processDone();
            if (outputEntry != null && !outputEntry.isEditingCover) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!outputEntry.isEditingCover && previewView != null) {
                        previewView.getCoverBitmap(bitmap -> {
                            if (outputEntry == null) {
                                return;
                            }
                            AndroidUtilities.recycleBitmap(outputEntry.coverBitmap);
                            outputEntry.coverBitmap = bitmap;
                            delegate.onCoverEdited(outputEntry);
                        }, previewView, paintViewRenderView, paintViewEntitiesView);
                    }
                    navigateTo(PAGE_PREVIEW, true);
                }, 400);
            }
        });
        navbarContainer.addView(coverButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));

        previewButtons = new PreviewButtons(context);
        previewButtons.setVisibility(View.GONE);
        previewButtons.setOnClickListener((Integer btn) -> {
            if (outputEntry == null || captionEdit.isRecording()) {
                return;
            }
            captionEdit.clearFocus();
            if (btn == PreviewButtons.BUTTON_SHARE) {
                processDone();
            } else if (btn == PreviewButtons.BUTTON_PAINT) {
                switchToEditMode(EDIT_MODE_PAINT, true);
                if (paintView != null) {
                    paintView.enteredThroughText = false;
                    paintView.openPaint();
                }
            } else if (btn == PreviewButtons.BUTTON_TEXT) {
                switchToEditMode(EDIT_MODE_PAINT, true);
                if (paintView != null) {
                    paintView.openText();
                    paintView.enteredThroughText = true;
                }
            } else if (btn == PreviewButtons.BUTTON_STICKER) {
                createPhotoPaintView();
                hidePhotoPaintView();
                if (paintView != null) {
                    paintView.openStickers();
                }
            } else if (btn == PreviewButtons.BUTTON_ADJUST) {
                switchToEditMode(EDIT_MODE_FILTER, true);
            }
        });
        navbarContainer.addView(previewButtons,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL));

        trash = new TrashView(context);
        trash.setAlpha(0f);
        trash.setVisibility(View.GONE);
        previewContainer.addView(trash,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        previewHighlight = new PreviewHighlightView(context, currentAccount, resourcesProvider);
        previewContainer.addView(previewHighlight, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateActionBarButtonsOffsets();
    }

    private void processDone() {
        if (videoError) {
            downloadButton.showFailedVideo();
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(previewButtons.shareButton, shiftDp = -shiftDp);
            return;
        }
        if (captionEdit != null && captionEdit.isCaptionOverLimit()) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(captionEdit.limitTextView, shiftDp = -shiftDp);
            captionEdit.captionLimitToast();
            return;
        }

        outputEntry.captionEntitiesAllowed = MessagesController.getInstance(currentAccount).storyEntitiesAllowed();
        if (captionEdit != null && !outputEntry.captionEntitiesAllowed) {
            CharSequence text = captionEdit.getText();
            if (text instanceof Spannable && (
                    ((Spannable) text).getSpans(0, text.length(), TextStyleSpan.class).length > 0 ||
                            ((Spannable) text).getSpans(0, text.length(), URLSpan.class).length > 0
            )) {
                BulletinFactory.of(this, resourcesProvider)
                        .createSimpleBulletin(R.raw.voip_invite, premiumText(getString(R.string.StoryPremiumFormatting))).show(true);
                AndroidUtilities.shakeViewSpring(captionEdit, shiftDp = -shiftDp);
                return;
            }
        }

        if (captionEdit != null) {
            outputEntry.updatedCaption = captionEdit.getText();
        }

        delegate.processDone(outputEntry);
    }

    private Bitmap getUiBlurBitmap() {
        Bitmap blur = null;
        if (photoFilterView != null) {
            blur = photoFilterView.getUiBlurBitmap();
        }
        if (blur == null && previewView != null && previewView.getTextureView() != null) {
            blur = previewView.getTextureView().getUiBlurBitmap();
        }
        return blur;
    }

    private DraftSavedHint getDraftSavedHint() {
        if (draftSavedHint == null) {
            draftSavedHint = new DraftSavedHint(getContext());
            controlContainer.addView(draftSavedHint,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0,
                            66 + 12));
        }
        return draftSavedHint;
    }

    public File prepareThumb(StoryEntry storyEntry, boolean forDraft) {
        if (storyEntry == null || previewView.getWidth() <= 0 || previewView.getHeight() <= 0) {
            return null;
        }
//        if (!forDraft && !storyEntry.wouldBeVideo() && !storyEntry.isEdit) {
//            return null;
//        }
        File file = forDraft ? storyEntry.draftThumbFile : storyEntry.uploadThumbFile;
        if (file != null) {
            file.delete();
            file = null;
        }

        View previewView = collageLayoutView.hasLayout() ? collageLayoutView : this.previewView;

        final float scale = forDraft ? 1 / 3f : 1f;
        final int w = (int) (previewView.getWidth() * scale);
        final int h = (int) (previewView.getHeight() * scale);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);

        canvas.save();
        canvas.scale(scale, scale);
        AndroidUtilities.makingGlobalBlurBitmap = true;
        previewView.draw(canvas);
        AndroidUtilities.makingGlobalBlurBitmap = false;
        canvas.restore();

        final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        TextureView textureView = this.previewView.getTextureView();
        if (storyEntry.isVideo && !storyEntry.isRepostMessage && textureView != null) {
            Bitmap previewTextureView = textureView.getBitmap();
            Matrix matrix = textureView.getTransform(null);
            if (matrix != null) {
                matrix = new Matrix(matrix);
                matrix.postScale(scale, scale);
            }
            canvas.drawBitmap(previewTextureView, matrix, bitmapPaint);
            previewTextureView.recycle();
        }

        if (storyEntry.paintBlurFile != null) {
            try {
                Bitmap paintBitmap = BitmapFactory.decodeFile(storyEntry.paintBlurFile.getPath());
                canvas.save();
                float scale2 = w / (float) paintBitmap.getWidth();
                canvas.scale(scale2, scale2);
                canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                canvas.restore();
                paintBitmap.recycle();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (storyEntry.paintFile != null) {
            try {
                Bitmap paintBitmap = BitmapFactory.decodeFile(storyEntry.paintFile.getPath());
                canvas.save();
                float scale2 = w / (float) paintBitmap.getWidth();
                canvas.scale(scale2, scale2);
                canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                canvas.restore();
                paintBitmap.recycle();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (paintView != null && paintView.entitiesView != null) {
            canvas.save();
            canvas.scale(scale, scale);
            paintView.drawForThemeToggle = true;
            paintView.entitiesView.drawForThumb = true;
            paintView.entitiesView.draw(canvas);
            paintView.entitiesView.drawForThumb = false;
            paintView.drawForThemeToggle = false;
            canvas.restore();
        }

        Bitmap thumbBitmap = Bitmap.createScaledBitmap(bitmap, 40, 22, true);

        file = StoryEntry.makeCacheFile(currentAccount, false);
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, forDraft ? 95 : 99, new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        bitmap.recycle();

        if (forDraft) {
            storyEntry.draftThumbFile = file;
        } else {
            storyEntry.uploadThumbFile = file;
        }
        storyEntry.thumbBitmap = thumbBitmap;
        return file;
    }

    private String flashButtonMode;

    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (cameraView != null && cameraView.isDual() || animatedRecording) {
            mode = null;
        }
        flashButtonMode = mode;
        if (mode == null) {
            setActionBarButtonVisible(flashButton, false, animated);
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                break;
        }
        flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
        setActionBarButtonVisible(flashButton, currentPage == PAGE_CAMERA && !collageListView.isVisible() && flashButtonMode != null && !inCheck(),
                animated);
    }

    private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {
        @Override
        public boolean canRecordAudio() {
            return requestAudioPermission();
        }

        @Override
        public void onPhotoShoot() {
            if (takingPhoto || awaitingPlayer || currentPage != PAGE_CAMERA || cameraView == null || !cameraView.isInited()) {
                return;
            }
            cameraHint.hide();
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {
                }
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, false);
            takingPhoto = true;
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flash(this::takePicture);
            } else {
                takePicture(null);
            }
        }

        @Override
        public void onCheckClick() {
            ArrayList<StoryEntry> entries = collageLayoutView.getContent();
            if (entries.size() == 1) {
                outputEntry = entries.get(0);
            } else {
                outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
            }
            isVideo = outputEntry != null && outputEntry.isVideo;
            if (modeSwitcherView != null) {
                modeSwitcherView.switchMode(isVideo);
            }
            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
            navigateTo(PAGE_PREVIEW, true);
        }

        private void takePicture(Utilities.Callback<Runnable> done) {
            boolean savedFromTextureView = false;
            if (!useDisplayFlashlight()) {
                cameraView.startTakePictureAnimation(true);
            }
            if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF)
                    || collageLayoutView.hasLayout()) {
                if (!collageLayoutView.hasLayout()) {
                    cameraView.pauseAsTakingPicture();
                }
                final Bitmap bitmap = cameraView.getTextureView().getBitmap();
                try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    savedFromTextureView = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                bitmap.recycle();
            }
            if (!savedFromTextureView) {
                takingPhoto = CameraController.getInstance().takePicture(outputFile, true, cameraView.getCameraSessionObject(), (orientation) -> {
                    if (useDisplayFlashlight()) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {
                        }
                    }
                    takingPhoto = false;
                    if (outputFile == null) {
                        return;
                    }
                    int w = -1, h = -1;
                    try {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                        w = opts.outWidth;
                        h = opts.outHeight;
                    } catch (Exception ignore) {
                    }

                    int rotate = orientation == -1 ? 0 : 90;
                    if (orientation == -1) {
                        if (w > h) {
                            rotate = 270;
                        }
                    } else if (h > w && rotate != 0) {
                        rotate = 0;
                    }
                    StoryEntry entry = StoryEntry.fromPhotoShoot(outputFile, rotate);

                    if (collageLayoutView.hasLayout()) {
                        outputFile = null;
                        if (collageLayoutView.push(entry)) {
                            outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                            fromGallery = false;

                            if (done != null) {
                                done.run(null);
                            }
//                            if (done != null) {
//                                done.run(() -> navigateTo(PAGE_PREVIEW, true));
//                            } else {
//                                navigateTo(PAGE_PREVIEW, true);
//                            }
                        } else if (done != null) {
                            done.run(null);
                        }
                        updateActionBarButtons(true);
                    } else {
                        outputEntry = entry;
                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                        fromGallery = false;

                        if (done != null) {
                            done.run(() -> navigateTo(PAGE_PREVIEW, true));
                        } else {
                            navigateTo(PAGE_PREVIEW, true);
                        }
                    }
                });
            } else {
                takingPhoto = false;
                final StoryEntry entry = StoryEntry.fromPhotoShoot(outputFile, 0);

                if (collageLayoutView.hasLayout()) {
                    outputFile = null;
                    if (collageLayoutView.push(entry)) {
                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                        fromGallery = false;
                        if (done != null) {
                            done.run(null);
                        }
//                        if (done != null) {
//                            done.run(() -> navigateTo(PAGE_PREVIEW, true));
//                        } else {
//                            navigateTo(PAGE_PREVIEW, true);
//                        }
                    } else if (done != null) {
                        done.run(null);
                    }
                    updateActionBarButtons(true);
                } else {
                    outputEntry = entry;
                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    fromGallery = false;

                    if (done != null) {
                        done.run(() -> navigateTo(PAGE_PREVIEW, true));
                    } else {
                        navigateTo(PAGE_PREVIEW, true);
                    }
                }
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || stoppingTakingVideo || awaitingPlayer || currentPage != PAGE_CAMERA || cameraView == null
                    || cameraView.getCameraSession() == null) {
                return;
            }
            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            cameraHint.hide();
            takingVideo = true;
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {
                }
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, true);
            checkFrontfaceFlashModes();
            isDark = false;
            if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
                checkIsDark();
            }
            if (useDisplayFlashlight()) {
                flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
            } else {
                startRecording(byLongPress, whenStarted);
            }
        }

        private void startRecording(boolean byLongPress, Runnable whenStarted) {
            if (cameraView == null) {
                return;
            }
            CameraController.getInstance().recordVideo(cameraView.getCameraSessionObject(), outputFile, false, (thumbPath, duration) -> {
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                if (useDisplayFlashlight()) {
                    flashViews.flashOut();
                }
                if (outputFile == null || cameraView == null) {
                    return;
                }

                takingVideo = false;
                stoppingTakingVideo = false;

                if (duration <= 800) {
                    animateRecording(false, true);
                    setAwakeLock(false);
                    videoTimerView.setRecording(false, true);
                    if (recordControl != null) {
                        recordControl.stopRecordingLoading(true);
                    }
                    try {
                        outputFile.delete();
                        outputFile = null;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (thumbPath != null) {
                        try {
                            new File(thumbPath).delete();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return;
                }

                showVideoTimer(false, true);

                StoryEntry entry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
                animateRecording(false, true);
                setAwakeLock(false);
                videoTimerView.setRecording(false, true);
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                if (collageLayoutView.hasLayout()) {
                    outputFile = null;
                    entry.videoVolume = 1.0f;
                    if (collageLayoutView.push(entry)) {
                        outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                        fromGallery = false;
                        int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
                        if (width > 0 && height > 0) {
                            outputEntry.width = width;
                            outputEntry.height = height;
                            outputEntry.setupMatrix();
                        }
                    }
                    updateActionBarButtons(true);
                } else {
                    outputEntry = entry;
                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    fromGallery = false;
                    int width = cameraView.getVideoWidth(), height = cameraView.getVideoHeight();
                    if (width > 0 && height > 0) {
                        outputEntry.width = width;
                        outputEntry.height = height;
                        outputEntry.setupMatrix();
                    }
                    navigateToPreviewWithPlayerAwait(() -> {
                        navigateTo(PAGE_PREVIEW, true);
                    }, 0);
                }
            }, () /* onVideoStart */ -> {
                whenStarted.run();

                hintTextView.setText(getString(byLongPress ? R.string.StoryHintSwipeToZoom : R.string.StoryHintPinchToZoom), false);
                animateRecording(true, true);
                setAwakeLock(true);

                collageListView.setVisible(false, true);
                videoTimerView.setRecording(true, true);
                showVideoTimer(true, true);
            }, cameraView, true);

            if (!isVideo) {
                isVideo = true;
                collageListView.setVisible(false, true);
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);
            }
        }

        @Override
        public void onVideoRecordLocked() {
            hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public void onVideoRecordPause() {

        }

        @Override
        public void onVideoRecordResume() {

        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            if (stoppingTakingVideo || !takingVideo) {
                return;
            }
            stoppingTakingVideo = true;
            AndroidUtilities.runOnUIThread(() -> {
                if (takingVideo && stoppingTakingVideo && cameraView != null) {
                    showZoomControls(false, true);
//                    animateRecording(false, true);
//                    setAwakeLock(false);
                    CameraController.getInstance().stopVideoRecording(cameraView.getCameraSessionRecording(), false, false);
                }
            }, byDuration ? 0 : 400);
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onGalleryClick() {
            if (currentPage == PAGE_CAMERA && !takingPhoto && !takingVideo && requestGalleryPermission()) {
                animateGalleryListView(true);
            }
        }

        @Override
        public void onFlipClick() {
            if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited() || currentPage != PAGE_CAMERA) {
                return;
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
                final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
                sharedPreferences.edit().putString("flashMode", mode).commit();
            }
            cameraView.switchCamera();
            saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (cameraView != null) {
                cameraView.toggleDual();
            }
        }

        @Override
        public void onZoom(float zoom) {
            zoomControlView.setZoom(zoom, true);
            showZoomControls(false, true);
        }
    };

    private void setAwakeLock(boolean lock) {
        if (lock) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        } else {
            windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        try {
            windowManager.updateViewLayout(this, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private AnimatorSet recordingAnimator;
    private boolean animatedRecording;
    private boolean animatedRecordingWasInCheck;

    private void animateRecording(boolean recording, boolean animated) {
        if (recording) {
            if (dualHint != null) {
                dualHint.hide();
            }
            if (savedDualHint != null) {
                savedDualHint.hide();
            }
            if (muteHint != null) {
                muteHint.hide();
            }
            if (cameraHint != null) {
                cameraHint.hide();
            }
        }
        if (animatedRecording == recording && animatedRecordingWasInCheck == inCheck()) {
            return;
        }
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }
        animatedRecording = recording;
        animatedRecordingWasInCheck = inCheck();
        if (recording && collageListView != null && collageListView.isVisible()) {
            collageListView.setVisible(false, animated);
        }
        updateActionBarButtons(animated);
        if (animated) {
            recordingAnimator = new AnimatorSet();
            recordingAnimator.playTogether(
                    ObjectAnimator.ofFloat(hintTextView, View.ALPHA, recording && currentPage == PAGE_CAMERA && !inCheck() ? 1 : 0),
                    ObjectAnimator.ofFloat(hintTextView, View.TRANSLATION_Y, recording && currentPage == PAGE_CAMERA && !inCheck() ? 0 : dp(16)),
                    ObjectAnimator.ofFloat(collageHintTextView, View.ALPHA, !recording && currentPage == PAGE_CAMERA && inCheck() ? 0.6f : 0),
                    ObjectAnimator.ofFloat(collageHintTextView, View.TRANSLATION_Y,
                            !recording && currentPage == PAGE_CAMERA && inCheck() ? 0 : dp(16)),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, recording || currentPage != PAGE_CAMERA || inCheck() ? 0 : 1),
                    ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, recording || currentPage != PAGE_CAMERA || inCheck() ? dp(16) : 0)
            );
            recordingAnimator.setDuration(260);
            recordingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            recordingAnimator.start();
        } else {
            hintTextView.setAlpha(recording && currentPage == PAGE_CAMERA && !inCheck() ? 1f : 0);
            hintTextView.setTranslationY(recording && currentPage == PAGE_CAMERA && !inCheck() ? 0 : dp(16));
            collageHintTextView.setAlpha(!recording && currentPage == PAGE_CAMERA && inCheck() ? 0.6f : 0);
            collageHintTextView.setTranslationY(!recording && currentPage == PAGE_CAMERA && inCheck() ? 0 : dp(16));
            modeSwitcherView.setAlpha(recording || currentPage != PAGE_CAMERA || inCheck() ? 0 : 1f);
            modeSwitcherView.setTranslationY(recording || currentPage != PAGE_CAMERA || inCheck() ? dp(16) : 0);
        }
    }

    private boolean isDark;

    private void checkIsDark() {
        if (cameraView == null || cameraView.getTextureView() == null) {
            isDark = false;
            return;
        }
        final Bitmap bitmap = cameraView.getTextureView().getBitmap();
        if (bitmap == null) {
            isDark = false;
            return;
        }
        float l = 0;
        final int sx = bitmap.getWidth() / 12;
        final int sy = bitmap.getHeight() / 12;
        for (int x = 0; x < 10; ++x) {
            for (int y = 0; y < 10; ++y) {
                l += AndroidUtilities.computePerceivedBrightness(bitmap.getPixel((1 + x) * sx, (1 + y) * sy));
            }
        }
        l /= 100;
        bitmap.recycle();
        isDark = l < .22f;
    }

    private boolean useDisplayFlashlight() {
        return (takingPhoto || takingVideo) && (cameraView != null && cameraView.isFrontface()) && (frontfaceFlashMode == 2
                || frontfaceFlashMode == 1 && isDark);
    }

    private boolean videoTimerShown = true;

    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(() -> {
                        if (!show) {
                            videoTimerView.setRecording(false, false);
                        }
                    }).start();
        } else {
            videoTimerView.clearAnimation();
            videoTimerView.setAlpha(show ? 1 : 0);
            if (!show) {
                videoTimerView.setRecording(false, false);
            }
        }
    }

    private Runnable zoomControlHideRunnable;
    private AnimatorSet zoomControlAnimation;

    private void showZoomControls(boolean show, boolean animated) {
        if (zoomControlView.getTag() != null && show || zoomControlView.getTag() == null && !show) {
            if (show) {
                if (zoomControlHideRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(zoomControlHideRunnable);
                }
                AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                    showZoomControls(false, true);
                    zoomControlHideRunnable = null;
                }, 2000);
            }
            return;
        }
        if (zoomControlAnimation != null) {
            zoomControlAnimation.cancel();
        }
        zoomControlView.setTag(show ? 1 : null);
        zoomControlAnimation = new AnimatorSet();
        zoomControlAnimation.setDuration(180);
        if (show) {
            zoomControlView.setVisibility(View.VISIBLE);
        }
        zoomControlAnimation.playTogether(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, show ? 1.0f : 0.0f));
        zoomControlAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    zoomControlView.setVisibility(View.GONE);
                }
                zoomControlAnimation = null;
            }
        });
        zoomControlAnimation.start();
        if (show) {
            AndroidUtilities.runOnUIThread(zoomControlHideRunnable = () -> {
                showZoomControls(false, true);
                zoomControlHideRunnable = null;
            }, 2000);
        }
    }

    public boolean onBackPressed() {
        if (captionEdit != null && captionEdit.stopRecording()) {
            return false;
        }
        if (takingVideo) {
            recordControl.stopRecording();
            return false;
        }
        if (takingPhoto) {
            return false;
        }
        if (captionEdit != null && captionEdit.onBackPressed()) {
            return false;
        } else if (themeSheet != null) {
            themeSheet.dismiss();
            return false;
        } else if (galleryListView != null) {
            if (galleryListView.onBackPressed()) {
                return false;
            }
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
            return false;
        } else if (currentEditMode == EDIT_MODE_PAINT && paintView != null && paintView.onBackPressed()) {
            return false;
        } else if (currentEditMode > EDIT_MODE_NONE) {
            switchToEditMode(EDIT_MODE_NONE, true);
            return false;
        } else if (currentPage == PAGE_CAMERA && collageLayoutView.hasContent()) {
            collageLayoutView.clear(true);
            updateActionBarButtons(true);
            return false;
        } else if (currentPage == PAGE_PREVIEW && (outputEntry == null || !outputEntry.isRepost && !outputEntry.isRepostMessage) && (
                outputEntry == null || !outputEntry.isEdit || (paintView != null && paintView.hasChanges()) || outputEntry.editedMedia
                        || outputEntry.editedCaption)) {
            if (paintView != null && paintView.onBackPressed()) {
                return false;
            } else if ((fromGallery && !collageLayoutView.hasLayout() && (paintView == null || !paintView.hasChanges()) && (outputEntry == null
                    || outputEntry.filterFile == null) || !previewButtons.isShareEnabled()) && (outputEntry == null || !outputEntry.isEdit
                    || !outputEntry.isRepost && !outputEntry.isRepostMessage)) {
                navigateTo(PAGE_CAMERA, true);
            } else {
                if (outputEntry.botId != 0) {
                    delegate.close(true);
                } else {
                    showDismissEntry();
                }
            }
            return false;
        } else if (currentPage == PAGE_COVER && !(outputEntry == null || outputEntry.isEditingCover)) {
            processDone();
            navigateTo(PAGE_PREVIEW, true);
            return false;
        } else {
            delegate.close(true);
            return true;
        }
    }

    private void setReply() {
        if (captionEdit == null) {
            return;
        }
        if (outputEntry == null || !outputEntry.isRepost) {
            captionEdit.setReply(null, null);
        } else {
            TLRPC.Peer peer = outputEntry.repostPeer;
            CharSequence peerName;
            if (peer instanceof TLRPC.TL_peerUser) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
                String name = UserObject.getUserName(user);
                peerName = outputEntry.repostPeerName = new SpannableStringBuilder(MessageObject.userSpan()).append(" ").append(name);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-DialogObject.getPeerDialogId(peer));
                String name = chat == null ? "" : chat.title;
                peerName = outputEntry.repostPeerName = new SpannableStringBuilder(MessageObject.userSpan()).append(" ").append(name);
            }
            CharSequence repostCaption = outputEntry.repostCaption;
            if (TextUtils.isEmpty(repostCaption)) {
                SpannableString s = new SpannableString(getString(R.string.Story));
                s.setSpan(new CharacterStyle() {
                    @Override
                    public void updateDrawState(TextPaint tp) {
                        tp.setAlpha(0x80);
                    }
                }, 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                repostCaption = s;
            }
            captionEdit.setReply(peerName, repostCaption);
        }
    }

    private Runnable afterPlayerAwait;
    private boolean previewAlreadySet;

    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo) {
        navigateToPreviewWithPlayerAwait(open, seekTo, 800);
    }

    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo, long ms) {
        if (awaitingPlayer || outputEntry == null) {
            return;
        }
        if (afterPlayerAwait != null) {
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
        }
        previewAlreadySet = true;
        awaitingPlayer = true;
        afterPlayerAwait = () -> {
            animateGalleryListView(false);
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
            afterPlayerAwait = null;
            awaitingPlayer = false;
            open.run();
        };
        previewView.setAlpha(0f);
        previewView.setVisibility(View.VISIBLE);
        previewView.set(outputEntry, afterPlayerAwait, seekTo);
        previewView.setupAudio(outputEntry, false);
        AndroidUtilities.runOnUIThread(afterPlayerAwait, ms);
    }

    private AnimatorSet pageAnimator;

    public void navigateTo(int page, boolean animated) {
        if (page == currentPage) {
            return;
        }

        final int oldPage = currentPage;
        currentPage = page;

        if (pageAnimator != null) {
            pageAnimator.cancel();
        }

        onNavigateStart(oldPage, page);
        if (previewButtons != null) {
            previewButtons.appear(page == PAGE_PREVIEW, animated);
        }
        showVideoTimer(page == PAGE_CAMERA && isVideo && !collageListView.isVisible() && !inCheck(), animated);
        if (page != PAGE_PREVIEW) {
            videoTimeView.show(false, animated);
        }
        setActionBarButtonVisible(backButton, !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton, page == PAGE_CAMERA && !collageListView.isVisible() && flashButtonMode != null && !inCheck(),
                animated);
        setActionBarButtonVisible(dualButton, page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible()
                && !collageLayoutView.hasLayout(), true);
        setActionBarButtonVisible(collageButton, page == PAGE_CAMERA && !collageListView.isVisible(), animated);
        updateActionBarButtons(animated);
        if (animated) {
            pageAnimator = new AnimatorSet();

            ArrayList<Animator> animators = new ArrayList<>();

            if (cameraView != null) {
                animators.add(ObjectAnimator.ofFloat(cameraView, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            }
            cameraViewThumb.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(cameraViewThumb, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(previewView, View.ALPHA,
                    page == PAGE_PREVIEW && !collageLayoutView.hasLayout() || page == PAGE_COVER ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(collageLayoutView, View.ALPHA,
                    page == PAGE_CAMERA || page == PAGE_PREVIEW && collageLayoutView.hasLayout() ? 1 : 0));

            animators.add(ObjectAnimator.ofFloat(recordControl, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
//            animators.add(ObjectAnimator.ofFloat(flashButton, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
//            animators.add(ObjectAnimator.ofFloat(dualButton, View.ALPHA, page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable()
            //            ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(recordControl, View.TRANSLATION_Y, page == PAGE_CAMERA ? 0 : dp(24)));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, page == PAGE_CAMERA && !inCheck() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, page == PAGE_CAMERA && !inCheck() ? 0 : dp(24)));
//            backButton.setVisibility(View.VISIBLE);
//            animators.add(ObjectAnimator.ofFloat(backButton, View.ALPHA, 1));
            animators.add(ObjectAnimator.ofFloat(hintTextView, View.ALPHA, page == PAGE_CAMERA && animatedRecording && !inCheck() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(collageHintTextView, View.ALPHA, page == PAGE_CAMERA && !animatedRecording && inCheck() ? 0.6f : 0));
            animators.add(ObjectAnimator.ofFloat(captionContainer, View.ALPHA,
                    page == PAGE_PREVIEW && (outputEntry == null || outputEntry.botId == 0) || page == PAGE_COVER ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(captionContainer, View.TRANSLATION_Y,
                    page == PAGE_PREVIEW && (outputEntry == null || outputEntry.botId == 0) || page == PAGE_COVER ? 0 : dp(12)));
            animators.add(ObjectAnimator.ofFloat(captionEdit, View.ALPHA, page == PAGE_COVER ? 0f : 1f));
            animators.add(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, page == PAGE_PREVIEW || page == PAGE_COVER ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(coverButton, View.ALPHA, page == PAGE_COVER ? 1f : 0f));

            animators.add(ObjectAnimator.ofFloat(timelineView, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(coverTimelineView, View.ALPHA, page == PAGE_COVER ? 1f : 0));

            animators.add(ObjectAnimator.ofFloat(muteButton, View.ALPHA, page == PAGE_PREVIEW && isVideo ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(playButton, View.ALPHA,
                    page == PAGE_PREVIEW && (isVideo || outputEntry != null && !TextUtils.isEmpty(outputEntry.audioPath)) ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(downloadButton, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));
            if (themeButton != null) {
                animators.add(ObjectAnimator.ofFloat(themeButton, View.ALPHA,
                        page == PAGE_PREVIEW && (outputEntry != null && outputEntry.isRepostMessage) ? 1f : 0));
            }
//            animators.add(ObjectAnimator.ofFloat(privacySelector, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));

            animators.add(ObjectAnimator.ofFloat(zoomControlView, View.ALPHA, 0));

            pageAnimator.playTogether(animators);
            pageAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onNavigateEnd(oldPage, page);
                }
            });
            pageAnimator.setDuration(460);
            pageAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            pageAnimator.start();
        } else {
            if (cameraView != null) {
                cameraView.setAlpha(page == PAGE_CAMERA ? 1 : 0);
            }
            cameraViewThumb.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            cameraViewThumb.setVisibility(page == PAGE_CAMERA ? View.VISIBLE : View.GONE);
            previewView.setAlpha(page == PAGE_PREVIEW && !collageLayoutView.hasLayout() || page == PAGE_COVER ? 1f : 0);
            collageLayoutView.setAlpha(page == PAGE_CAMERA || page == PAGE_PREVIEW && collageLayoutView.hasLayout() ? 1 : 0);
            recordControl.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            recordControl.setTranslationY(page == PAGE_CAMERA ? 0 : dp(16));
            modeSwitcherView.setAlpha(page == PAGE_CAMERA && !inCheck() ? 1f : 0);
            modeSwitcherView.setTranslationY(page == PAGE_CAMERA && !inCheck() ? 0 : dp(16));
            hintTextView.setAlpha(page == PAGE_CAMERA && animatedRecording && !inCheck() ? 1f : 0);
            collageHintTextView.setAlpha(page == PAGE_CAMERA && !animatedRecording && inCheck() ? 0.6f : 0);
            captionContainer.setAlpha(page == PAGE_PREVIEW || page == PAGE_COVER ? 1f : 0);
            captionContainer.setTranslationY(page == PAGE_PREVIEW || page == PAGE_COVER ? 0 : dp(12));
            captionEdit.setAlpha(page == PAGE_COVER ? 0f : 1f);
            muteButton.setAlpha(page == PAGE_PREVIEW && isVideo ? 1f : 0);
            playButton.setAlpha(page == PAGE_PREVIEW && (isVideo || outputEntry != null && !TextUtils.isEmpty(outputEntry.audioPath)) ? 1f : 0);
            downloadButton.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            if (themeButton != null) {
                themeButton.setAlpha(page == PAGE_PREVIEW && (outputEntry != null && outputEntry.isRepostMessage) ? 1f : 0);
            }
//            privacySelector.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            timelineView.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            coverTimelineView.setAlpha(page == PAGE_COVER ? 1f : 0f);
            titleTextView.setAlpha(page == PAGE_PREVIEW || page == PAGE_COVER ? 1f : 0f);
            coverButton.setAlpha(page == PAGE_COVER ? 1f : 0f);
            onNavigateEnd(oldPage, page);
        }
    }

    private ValueAnimator containerViewBackAnimator;
    private boolean applyContainerViewTranslation2 = true;

    private void animateContainerBack() {
        if (containerViewBackAnimator != null) {
            containerViewBackAnimator.cancel();
            containerViewBackAnimator = null;
        }
        applyContainerViewTranslation2 = false;
        float y1 = containerView.getTranslationY1(), y2 = containerView.getTranslationY2(), a = containerView.getAlpha();
        containerViewBackAnimator = ValueAnimator.ofFloat(1, 0);
        containerViewBackAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            containerView.setTranslationY(y1 * t);
            containerView.setTranslationY2(y2 * t);
//            containerView.setAlpha(AndroidUtilities.lerp(a, 1f, t));
        });
        containerViewBackAnimator.setDuration(340);
        containerViewBackAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        containerViewBackAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                containerViewBackAnimator = null;
                containerView.setTranslationY(0);
                containerView.setTranslationY2(0);
            }
        });
        containerViewBackAnimator.start();
    }

    private void openThemeSheet() {
        if (themeSheet == null) {
            themeSheet = new StoryThemeSheet(getContext(), currentAccount, resourcesProvider, () -> {
                removeView(themeSheet);
                themeSheet = null;
            }) {
                @Override
                protected void updateWallpaper() {
                    previewView.setupWallpaper(outputEntry, true);
                }
            };
            addView(themeSheet);
        }
        themeSheet.open(outputEntry);
    }

    private Parcelable lastGalleryScrollPosition;
    private MediaController.AlbumEntry lastGallerySelectedAlbum;

    private void createGalleryListView() {
        createGalleryListView(false);
    }

    private void destroyGalleryListView() {
        if (galleryListView == null) {
            return;
        }
        removeView(galleryListView);
        galleryListView = null;
        if (galleryOpenCloseAnimator != null) {
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator = null;
        }
        if (galleryOpenCloseSpringAnimator != null) {
            galleryOpenCloseSpringAnimator.cancel();
            galleryOpenCloseSpringAnimator = null;
        }
        galleryListViewOpening = null;
    }

    private void createGalleryListView(boolean forAddingPart) {
        if (galleryListView != null || getContext() == null) {
            return;
        }

        galleryListView = new GalleryListView(currentAccount, getContext(), resourcesProvider, lastGallerySelectedAlbum, forAddingPart) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (applyContainerViewTranslation2) {
                    final float amplitude = getMeasuredHeight() - galleryListView.top();
                    float t = Utilities.clamp(1f - translationY / amplitude, 1, 0);
                    containerView.setTranslationY2(t * dp(-32));
                    containerView.setAlpha(1 - .6f * t);
                    actionBarContainer.setAlpha(1f - t);
                }
            }

            @Override
            public void firstLayout() {
                galleryListView.setTranslationY(getMeasuredHeight() - galleryListView.top());
                if (galleryLayouted != null) {
                    galleryLayouted.run();
                    galleryLayouted = null;
                }
            }

            @Override
            protected void onFullScreen(boolean isFullscreen) {
                if (currentPage == PAGE_CAMERA && isFullscreen) {
                    AndroidUtilities.runOnUIThread(() -> {
                        destroyCameraView(true);
                        cameraViewThumb.setImageDrawable(getCameraThumb());
                    });
                }
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
                    galleryClosing = true;
                    animateGalleryListView(false);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        galleryListView.allowSearch(false);
        galleryListView.setOnBackClickListener(() -> {
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
        });
        galleryListView.setOnSelectListener((entry, blurredBitmap) -> {
            if (entry == null || galleryListViewOpening != null || scrollingY || !isGalleryOpen()) {
                return;
            }

            if (forAddingPart) {
                if (outputEntry == null) {
                    return;
                }
                createPhotoPaintView();
                outputEntry.editedMedia = true;
                if (entry instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                    paintView.appearAnimation(paintView.createPhoto(photoEntry.path, false));
                } else if (entry instanceof TLObject) {
                    paintView.appearAnimation(paintView.createPhoto((TLObject) entry, false));
                }
                animateGalleryListView(false);
            } else {
                StoryEntry storyEntry;
                showVideoTimer(false, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);

                animateGalleryListView(false);
                if (entry instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                    isVideo = photoEntry.isVideo;
                    storyEntry = StoryEntry.fromPhotoEntry(photoEntry);
                    storyEntry.blurredVideoThumb = blurredBitmap;
                    storyEntry.setupMatrix();
                    fromGallery = true;

                    if (collageLayoutView.hasLayout()) {
                        outputFile = null;
                        storyEntry.videoVolume = 1.0f;
                        if (collageLayoutView.push(storyEntry)) {
                            outputEntry = StoryEntry.asCollage(collageLayoutView.getLayout(), collageLayoutView.getContent());
//                            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
//                            navigateTo(PAGE_PREVIEW, true);
                        }
                        updateActionBarButtons(true);
                    } else {
                        outputEntry = storyEntry;
                        if (entry instanceof MediaController.PhotoEntry) {
                            StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                        }
                        navigateTo(PAGE_PREVIEW, true);
                    }
                } else if (entry instanceof StoryEntry) {
                    storyEntry = (StoryEntry) entry;
                    if (storyEntry.file == null && !storyEntry.isCollage()) {
                        downloadButton.showToast(R.raw.error, "Failed to load draft");
                        MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(storyEntry);
                        return;
                    }
                    storyEntry.setupMatrix();
                    isVideo = storyEntry.isVideo;
                    storyEntry.blurredVideoThumb = blurredBitmap;
                    fromGallery = false;

                    collageLayoutView.set(storyEntry, true);
                    outputEntry = storyEntry;
                    if (entry instanceof MediaController.PhotoEntry) {
                        StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    }
                    navigateTo(PAGE_PREVIEW, true);
                } else {
                    return;
                }
            }

            if (galleryListView != null) {
                lastGalleryScrollPosition = galleryListView.layoutManager.onSaveInstanceState();
                lastGallerySelectedAlbum = galleryListView.getSelectedAlbum();
            }
        });
        if (lastGalleryScrollPosition != null) {
            galleryListView.layoutManager.onRestoreInstanceState(lastGalleryScrollPosition);
        }
        addView(galleryListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private boolean isGalleryOpen() {
        return !scrollingY && galleryListView != null && galleryListView.getTranslationY() < (getMeasuredHeight() - (int) (
                AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()));
    }

    private ValueAnimator galleryOpenCloseAnimator;
    private SpringAnimation galleryOpenCloseSpringAnimator;
    private Boolean galleryListViewOpening;
    private Runnable galleryLayouted;

    private void animateGalleryListView(boolean open) {
        wasGalleryOpen = open;
        if (galleryListViewOpening != null && galleryListViewOpening == open) {
            return;
        }

        if (galleryListView == null) {
            if (open) {
                createGalleryListView();
            }
            if (galleryListView == null) {
                return;
            }
        }

        if (galleryListView.firstLayout) {
            galleryLayouted = () -> animateGalleryListView(open);
            return;
        }

        if (galleryOpenCloseAnimator != null) {
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator = null;
        }
        if (galleryOpenCloseSpringAnimator != null) {
            galleryOpenCloseSpringAnimator.cancel();
            galleryOpenCloseSpringAnimator = null;
        }

        if (galleryListView == null) {
            if (open) {
                createGalleryListView();
            }
            if (galleryListView == null) {
                return;
            }
        }
        if (galleryListView != null) {
            galleryListView.ignoreScroll = false;
        }

        if (open && draftSavedHint != null) {
            draftSavedHint.hide(true);
        }

        galleryListViewOpening = open;

        float from = galleryListView.getTranslationY();
        float to = open ? 0 : getHeight() - galleryListView.top() + AndroidUtilities.navigationBarHeight * 2.5f;
        float fulldist = Math.max(1, getHeight());

        galleryListView.ignoreScroll = !open;

        applyContainerViewTranslation2 = containerViewBackAnimator == null;
        if (open) {
            galleryOpenCloseSpringAnimator = new SpringAnimation(galleryListView, DynamicAnimation.TRANSLATION_Y, to);
            galleryOpenCloseSpringAnimator.getSpring().setDampingRatio(0.75f);
            galleryOpenCloseSpringAnimator.getSpring().setStiffness(350.0f);
            galleryOpenCloseSpringAnimator.addEndListener((a, canceled, c, d) -> {
                if (canceled) {
                    return;
                }
                galleryListView.setTranslationY(to);
                galleryListView.ignoreScroll = false;
                galleryOpenCloseSpringAnimator = null;
                galleryListViewOpening = null;
            });
            galleryOpenCloseSpringAnimator.start();
        } else {
            galleryOpenCloseAnimator = ValueAnimator.ofFloat(from, to);
            galleryOpenCloseAnimator.addUpdateListener(anm -> {
                galleryListView.setTranslationY((float) anm.getAnimatedValue());
            });
            galleryOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(galleryListView);
                    galleryListView = null;
                    galleryOpenCloseAnimator = null;
                    galleryListViewOpening = null;
                    captionEdit.keyboardNotifier.ignore(currentPage != PAGE_PREVIEW);
                }
            });
            galleryOpenCloseAnimator.setDuration(450L);
            galleryOpenCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            galleryOpenCloseAnimator.start();
        }

        if (!open && !awaitingPlayer) {
            lastGalleryScrollPosition = null;
        }

        if (!open && currentPage == PAGE_CAMERA && !noCameraPermission) {
            cameraViewThumb.setImageDrawable(null);
            createCameraView();
        }
    }

    private void onNavigateStart(int fromPage, int toPage) {
        if (toPage == PAGE_CAMERA) {
            requestCameraPermission(false);
            recordControl.setVisibility(View.VISIBLE);
            if (recordControl != null) {
                recordControl.stopRecordingLoading(false);
            }
            modeSwitcherView.setVisibility(View.VISIBLE);
            zoomControlView.setVisibility(View.VISIBLE);
            zoomControlView.setAlpha(0);
            videoTimerView.setDuration(0, true);

            if (outputEntry != null) {
                outputEntry.destroy(false);
                outputEntry = null;
            }
            if (collageLayoutView != null) {
                collageLayoutView.clear(true);
                recordControl.setCollageProgress(0.0f, false);
            }
        }
        if (fromPage == PAGE_CAMERA) {
            setCameraFlashModeIcon(null, true);
            saveLastCameraBitmap(() -> cameraViewThumb.setImageDrawable(getCameraThumb()));
            if (draftSavedHint != null) {
                draftSavedHint.setVisibility(View.GONE);
            }
            cameraHint.hide();
            if (dualHint != null) {
                dualHint.hide();
            }
        }
        if (toPage == PAGE_PREVIEW || fromPage == PAGE_PREVIEW) {
            downloadButton.setEntry(toPage == PAGE_PREVIEW ? outputEntry : null);
            if (isVideo) {
                muteButton.setVisibility(View.VISIBLE);
                setIconMuted(outputEntry != null && outputEntry.muted, false);
                playButton.setVisibility(View.VISIBLE);
                previewView.play(true);
                playButton.drawable.setPause(previewView.isPlaying(), false);
                titleTextView.setRightPadding(AndroidUtilities.dp(144));
            } else if (outputEntry != null && !TextUtils.isEmpty(outputEntry.audioPath)) {
                muteButton.setVisibility(View.GONE);
                playButton.setVisibility(View.VISIBLE);
                playButton.drawable.setPause(true, false);
                titleTextView.setRightPadding(AndroidUtilities.dp(48));
            } else {
                titleTextView.setRightPadding(AndroidUtilities.dp(48));
            }
            downloadButton.setVisibility(View.VISIBLE);
            if (outputEntry != null && outputEntry.isRepostMessage) {
                getThemeButton().setVisibility(View.VISIBLE);
                updateThemeButtonDrawable(false);
            } else if (themeButton != null) {
                themeButton.setVisibility(View.GONE);
            }
//            privacySelector.setVisibility(View.VISIBLE);
            previewButtons.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.VISIBLE);
            captionEdit.setVisibility(params.isCaptionAllowed ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoTimelineContainerView.getLayoutParams();
            lp.bottomMargin = params.isCaptionAllowed ? dp(68) : dp(12);
            videoTimelineContainerView.setLayoutParams(lp);
            captionContainer.setVisibility(View.VISIBLE);
            captionContainer.clearFocus();

//            privacySelector.setStoryPeriod(outputEntry == null || !UserConfig.getInstance(currentAccount).isPremium() ? 86400 : outputEntry.period);
            captionEdit.setPeriod(outputEntry == null ? 86400 : outputEntry.period, false);
            captionEdit.setPeriodVisible(
                    !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (outputEntry == null || !outputEntry.isEdit));
            captionEdit.setHasRoundVideo(outputEntry != null && outputEntry.round != null);
            setReply();
            timelineView.setOpen(outputEntry == null || !outputEntry.isCollage() || !outputEntry.hasVideo(), false);
        }
        if (toPage == PAGE_COVER || fromPage == PAGE_COVER) {
            titleTextView.setVisibility(View.VISIBLE);
            coverTimelineView.setVisibility(View.VISIBLE);
            if (outputEntry != null && outputEntry.isEditingCover) {
                titleTextView.setText(getString(R.string.RecorderEditCover));
            }
            captionContainer.setVisibility(View.VISIBLE);
            coverButton.setVisibility(View.VISIBLE);
        }
        if (toPage == PAGE_COVER) {
            titleTextView.setText(getString(R.string.RecorderEditCover));
        }
        if (toPage == PAGE_PREVIEW) {
            videoError = false;
            final boolean isBot = outputEntry != null && outputEntry.botId != 0;
            final boolean isEdit = outputEntry != null && outputEntry.isEdit;
            previewButtons.setShareText(getString(isEdit ? R.string.Done : isBot ? R.string.UploadBotPreview : R.string.Next), !isBot);
            coverTimelineView.setVisibility(View.GONE);
            coverButton.setVisibility(View.GONE);
//            privacySelector.set(outputEntry, false);
            if (!previewAlreadySet) {
                if (outputEntry != null && outputEntry.isRepostMessage) {
                    previewView.preset(outputEntry);
                } else {
                    previewView.set(outputEntry);
                }
            }
            previewAlreadySet = false;
            captionEdit.editText.getEditText()
                    .setOnPremiumMenuLockClickListener(MessagesController.getInstance(currentAccount).storyEntitiesAllowed() ? null : () -> {
                        BulletinFactory.of(this, resourcesProvider)
                                .createSimpleBulletin(R.raw.voip_invite, premiumText(getString(R.string.StoryPremiumFormatting))).show(true);
                    });
            if (outputEntry != null && (outputEntry.isDraft || outputEntry.isEdit || outputEntry.isRepostMessage)) {
                if (outputEntry.paintFile != null) {
                    destroyPhotoPaintView();
                    createPhotoPaintView();
                    hidePhotoPaintView();
                }
//                if (outputEntry.filterState != null) {
//                    destroyPhotoFilterView();
//                    createFilterPhotoView();
//                }
                if (outputEntry.isVideo && outputEntry.filterState != null) {
                    VideoEditTextureView textureView = previewView.getTextureView();
                    if (textureView != null) {
                        textureView.setDelegate(eglThread -> {
                            if (eglThread != null && outputEntry != null && outputEntry.filterState != null) {
                                eglThread.setFilterGLThreadDelegate(FilterShaders.getFilterShadersDelegate(outputEntry.filterState));
                            }
                        });
                    }
                }
                captionEdit.setText(outputEntry.caption);
            } else {
                captionEdit.clear();
            }
            previewButtons.setFiltersVisible(
                    outputEntry == null || (!outputEntry.isRepostMessage || outputEntry.isVideo) && !outputEntry.isCollage());
            previewButtons.setShareEnabled(!videoError && !captionEdit.isCaptionOverLimit() && (
                    !MessagesController.getInstance(currentAccount).getStoriesController().hasStoryLimit() || (outputEntry != null && (
                            outputEntry.isEdit || outputEntry.botId != 0))));
            muteButton.setImageResource(outputEntry != null && outputEntry.muted ? R.drawable.media_unmute : R.drawable.media_mute);
            previewView.setVisibility(View.VISIBLE);
            timelineView.setVisibility(View.VISIBLE);
            titleTextView.setVisibility(View.VISIBLE);
            titleTextView.setTranslationX(0);
            if (outputEntry != null && outputEntry.botId != 0) {
                titleTextView.setText("");
            } else if (outputEntry != null && outputEntry.isEdit) {
                titleTextView.setText(getString(R.string.RecorderEditStory));
            } else if (outputEntry != null && outputEntry.isRepostMessage) {
                titleTextView.setText(getString(R.string.RecorderRepost));
            } else if (outputEntry != null && outputEntry.isRepost) {
                SpannableStringBuilder title = new SpannableStringBuilder();
                AvatarSpan span = new AvatarSpan(titleTextView, currentAccount, 32);
                titleTextView.setTranslationX(-dp(6));
                SpannableString avatar = new SpannableString("a");
                avatar.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (outputEntry.repostPeer instanceof TLRPC.TL_peerUser) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(outputEntry.repostPeer.user_id);
                    span.setUser(user);
                    title.append(avatar).append("  ");
                    title.append(UserObject.getUserName(user));
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-DialogObject.getPeerDialogId(outputEntry.repostPeer));
                    span.setChat(chat);
                    title.append(avatar).append("  ");
                    title.append(chat != null ? chat.title : "");
                }
                titleTextView.setText(title);
            } else {
                titleTextView.setText(getString(R.string.RecorderNewStory));
            }

//            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
//            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
        }
        if (fromPage == PAGE_PREVIEW) {
//            privacySelectorHint.hide();
            captionEdit.hidePeriodPopup();
            muteHint.hide();
        }
        if (toPage == PAGE_COVER) {
            if (outputEntry != null) {
                if (outputEntry.cover < 0) {
                    outputEntry.cover = 0;
                }
                coverValue = outputEntry.cover;
                long duration = previewView.getDuration() < 100 ? outputEntry.duration : previewView.getDuration();
                if (outputEntry.duration <= 0) {
                    outputEntry.duration = duration;
                }
                coverTimelineView.setVideo(false, outputEntry.getOriginalFile().getAbsolutePath(), outputEntry.duration, outputEntry.videoVolume);
                coverTimelineView.setCoverVideo((long) (outputEntry.left * duration), (long) (outputEntry.right * duration));
                final Utilities.Callback2<Boolean, Float> videoLeftSet = (start, left) -> {
                    final long _duration = previewView.getDuration() < 100 ? outputEntry.duration : previewView.getDuration();
                    coverValue = (long) ((left + 0.04f * (left / (1f - 0.04f))) * (outputEntry.right - outputEntry.left) * _duration);
                    previewView.seekTo(coverValue = (long) (outputEntry.left * _duration + coverValue), false);
                    if (paintView != null) {
                        paintView.setCoverTime(coverValue);
                    }
                    if (outputEntry != null && outputEntry.isEdit) {
                        outputEntry.editedMedia = true;
                    }
                };
                coverTimelineView.setDelegate(new TimelineView.TimelineDelegate() {
                    @Override
                    public void onVideoLeftChange(float left) {
                        videoLeftSet.run(false, left);
                    }
                });
                float left = (float) coverValue / Math.max(1, duration) * (1f - 0.04f);
                coverTimelineView.setVideoLeft(left);
                coverTimelineView.setVideoRight(left + 0.04f);
                videoLeftSet.run(true, left);
            }
        }
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setAllowTouch(false);
        }
        cameraViewThumb.setClickable(false);
        if (savedDualHint != null) {
            savedDualHint.hide();
        }
        Bulletin.hideVisible();

        if (captionEdit != null) {
            captionEdit.closeKeyboard();
            captionEdit.ignoreTouches = true;
        }
        if (previewView != null) {
            previewView.updatePauseReason(8, toPage != PAGE_PREVIEW);
        }
        if (paintView != null) {
            paintView.setCoverPreview(toPage != PAGE_PREVIEW);
        }
        if (removeCollageHint != null) {
            removeCollageHint.hide();
        }
        collageLayoutView.setPreview(toPage == PAGE_PREVIEW && collageLayoutView.hasLayout());
    }

    private void onNavigateEnd(int fromPage, int toPage) {
        if (fromPage == PAGE_CAMERA) {
            destroyCameraView(false);
            recordControl.setVisibility(View.GONE);
            zoomControlView.setVisibility(View.GONE);
            modeSwitcherView.setVisibility(View.GONE);
//            dualButton.setVisibility(View.GONE);
            animateRecording(false, false);
            setAwakeLock(false);
        }
        cameraViewThumb.setClickable(toPage == PAGE_CAMERA);
        if (fromPage == PAGE_COVER) {
            coverTimelineView.setVisibility(View.GONE);
            captionContainer.setVisibility(toPage == PAGE_PREVIEW ? View.VISIBLE : View.GONE);
            captionEdit.setVisibility(View.GONE);
            coverButton.setVisibility(View.GONE);
        }
        if (fromPage == PAGE_PREVIEW) {
            previewButtons.setVisibility(View.GONE);
            captionContainer.setVisibility(toPage == PAGE_COVER ? View.VISIBLE : View.GONE);
            muteButton.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            downloadButton.setVisibility(View.GONE);
            if (themeButton != null) {
                themeButton.setVisibility(View.GONE);
            }
//            privacySelector.setVisibility(View.GONE);
            previewView.setVisibility(toPage == PAGE_COVER ? View.VISIBLE : View.GONE);
            timelineView.setVisibility(View.GONE);
            if (toPage != PAGE_COVER) {
                destroyPhotoPaintView();
                destroyPhotoFilterView();
            }
            titleTextView.setVisibility(toPage == PAGE_COVER ? View.VISIBLE : View.GONE);
            destroyGalleryListView();
            trash.setAlpha(0f);
            trash.setVisibility(View.GONE);
            videoTimeView.setVisibility(View.GONE);
        }
        if (toPage == PAGE_PREVIEW) {
            if (outputEntry == null || !outputEntry.isRepost) {
                createPhotoPaintView();
                hidePhotoPaintView();
            }
            if (outputEntry == null || !outputEntry.isRepost && !outputEntry.isRepostMessage) {
                createFilterPhotoView();
            }
            if (photoFilterEnhanceView != null) {
                photoFilterEnhanceView.setAllowTouch(false);
            }
            previewView.updatePauseReason(2, false);
            previewView.updatePauseReason(3, false);
            previewView.updatePauseReason(4, false);
            previewView.updatePauseReason(5, false);
            previewView.updatePauseReason(7, false);
            videoTimeView.setVisibility(outputEntry != null && outputEntry.duration >= 30_000 ? View.VISIBLE : View.GONE);
            captionContainer.setAlpha(1f);
            captionContainer.setTranslationY(0);
            captionEdit.setVisibility(params.isCaptionAllowed ? View.VISIBLE : View.GONE);
        }
        if (toPage == PAGE_CAMERA && showSavedDraftHint) {
            getDraftSavedHint().setVisibility(View.VISIBLE);
            getDraftSavedHint().show();
            recordControl.updateGalleryImage();
        }
        showSavedDraftHint = false;

        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setAllowTouch(
                    toPage == PAGE_PREVIEW && (currentEditMode == EDIT_MODE_NONE || currentEditMode == EDIT_MODE_FILTER));
        }
//        if (toPage == PAGE_PREVIEW && !privacySelectorHintOpened) {
//            privacySelectorHint.show(false);
//            privacySelectorHintOpened = true;
//        }
        if (captionEdit != null) {
            captionEdit.ignoreTouches = toPage != PAGE_PREVIEW;
        }

        if (toPage == PAGE_PREVIEW) {
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
            MessagesController.getInstance(currentAccount).getStoriesController().loadBlocklistAtFirst();
            MessagesController.getInstance(currentAccount).getStoriesController().loadSendAs();
        }
    }

    private AnimatorSet editModeAnimator;

    public void switchToEditMode(int editMode, boolean animated) {
        switchToEditMode(editMode, false, animated);
    }

    public void switchToEditMode(int editMode, boolean force, boolean animated) {
        if (currentEditMode == editMode && !force) {
            return;
        }
        if (editMode != EDIT_MODE_NONE && (captionEdit != null && captionEdit.isRecording())) {
            return;
        }

        final int oldEditMode = currentEditMode;
        currentEditMode = editMode;

        if (editModeAnimator != null) {
            editModeAnimator.cancel();
            editModeAnimator = null;
        }

        previewButtons.appear((editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE) && openProgress > 0, animated);

        ArrayList<Animator> animators = new ArrayList<>();

        boolean delay = photoFilterView == null && editMode == EDIT_MODE_FILTER;
        if (editMode == EDIT_MODE_FILTER) {
            createFilterPhotoView();
//            animatePhotoFilterTexture(true, animated);
            previewTouchable = photoFilterView;
            View toolsView = photoFilterView != null ? photoFilterView.getToolsView() : null;
            if (toolsView != null) {
                toolsView.setAlpha(0f);
                toolsView.setVisibility(View.VISIBLE);
                animators.add(ObjectAnimator.ofFloat(toolsView, View.TRANSLATION_Y, 0));
                animators.add(ObjectAnimator.ofFloat(toolsView, View.ALPHA, 1));
            }
        } else if (oldEditMode == EDIT_MODE_FILTER && photoFilterView != null) {
            previewTouchable = null;
//            animatePhotoFilterTexture(false, animated);
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.TRANSLATION_Y, dp(186 + 40)));
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.ALPHA, 0));
        }

        if (editMode == EDIT_MODE_PAINT) {
            createPhotoPaintView();
            previewTouchable = paintView;
            animators.add(ObjectAnimator.ofFloat(backButton, View.ALPHA, 0));
            animators.add(ObjectAnimator.ofFloat(paintView.getTopLayout(), View.ALPHA, 0, 1));
            animators.add(ObjectAnimator.ofFloat(paintView.getTopLayout(), View.TRANSLATION_Y, -AndroidUtilities.dp(16), 0));
            animators.add(ObjectAnimator.ofFloat(paintView.getBottomLayout(), View.ALPHA, 0, 1));
            animators.add(ObjectAnimator.ofFloat(paintView.getBottomLayout(), View.TRANSLATION_Y, AndroidUtilities.dp(48), 0));
            animators.add(ObjectAnimator.ofFloat(paintView.getWeightChooserView(), View.TRANSLATION_X, -AndroidUtilities.dp(32), 0));
        } else if (oldEditMode == EDIT_MODE_PAINT && paintView != null) {
            previewTouchable = null;
            animators.add(ObjectAnimator.ofFloat(backButton, View.ALPHA, 1));
            animators.add(ObjectAnimator.ofFloat(paintView.getTopLayout(), View.ALPHA, 0));
            animators.add(ObjectAnimator.ofFloat(paintView.getTopLayout(), View.TRANSLATION_Y, -AndroidUtilities.dp(16)));
            animators.add(ObjectAnimator.ofFloat(paintView.getBottomLayout(), View.ALPHA, 0));
            animators.add(ObjectAnimator.ofFloat(paintView.getBottomLayout(), View.TRANSLATION_Y, AndroidUtilities.dp(48)));
            animators.add(ObjectAnimator.ofFloat(paintView.getWeightChooserView(), View.TRANSLATION_X, -AndroidUtilities.dp(32)));
        }

        animators.add(
                ObjectAnimator.ofFloat(muteButton, View.ALPHA, (editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE) && isVideo ? 1 : 0));
        animators.add(ObjectAnimator.ofFloat(playButton, View.ALPHA,
                (editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE) && (isVideo || outputEntry != null && !TextUtils.isEmpty(
                        outputEntry.audioPath)) ? 1 : 0));
        animators.add(ObjectAnimator.ofFloat(downloadButton, View.ALPHA, (editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE) ? 1 : 0));
        if (themeButton != null) {
            animators.add(ObjectAnimator.ofFloat(themeButton, View.ALPHA,
                    (editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE) && (outputEntry != null && outputEntry.isRepostMessage) ? 1f : 0));
        }
        animators.add(ObjectAnimator.ofFloat(titleTextView, View.ALPHA,
                (currentPage == PAGE_PREVIEW || currentPage == PAGE_COVER) && editMode == EDIT_MODE_NONE ? 1f : 0f));

        int rightMargin = 0;
        int bottomMargin = 0;
        if (editMode == EDIT_MODE_FILTER) {
            previewContainer.setPivotY(previewContainer.getMeasuredHeight() * .2f);
            bottomMargin = dp(164);
        } else if (editMode == EDIT_MODE_PAINT) {
            previewContainer.setPivotY(previewContainer.getMeasuredHeight() * .6f);
            bottomMargin = dp(40);
        } else if (editMode == EDIT_MODE_TIMELINE) {
            previewContainer.setPivotY(0);
            bottomMargin = timelineView.getContentHeight() + dp(8);
//            rightMargin = dp(46);
        }

        float scale = 1f;
        if (bottomMargin > 0) {
            final int bottomPivot = previewContainer.getHeight() - (int) previewContainer.getPivotY();
            scale = (float) (bottomPivot - bottomMargin) / bottomPivot;
        }
        if (rightMargin > 0) {
            final int rightPivot = previewContainer.getWidth() - (int) previewContainer.getPivotX();
            scale = Math.min(scale, (float) (rightPivot - rightMargin) / rightPivot);
        }

        animators.add(ObjectAnimator.ofFloat(previewContainer, View.SCALE_X, scale));
        animators.add(ObjectAnimator.ofFloat(previewContainer, View.SCALE_Y, scale));
        if (editMode == EDIT_MODE_NONE) {
            animators.add(ObjectAnimator.ofFloat(previewContainer, View.TRANSLATION_Y, 0));
        }

        if (photoFilterViewCurvesControl != null) {
            animators.add(ObjectAnimator.ofFloat(photoFilterViewCurvesControl, View.ALPHA, editMode == EDIT_MODE_FILTER ? 1f : 0));
        }
        if (photoFilterViewBlurControl != null) {
            animators.add(ObjectAnimator.ofFloat(photoFilterViewBlurControl, View.ALPHA, editMode == EDIT_MODE_FILTER ? 1f : 0));
        }

        animators.add(ObjectAnimator.ofFloat(captionEdit, View.ALPHA, editMode == EDIT_MODE_NONE ? 1f : 0));
        animators.add(ObjectAnimator.ofFloat(videoTimelineContainerView, View.ALPHA,
                editMode == EDIT_MODE_NONE || editMode == EDIT_MODE_TIMELINE ? 1f : 0));
        animators.add(ObjectAnimator.ofFloat(videoTimelineContainerView, View.TRANSLATION_Y,
                editMode == EDIT_MODE_TIMELINE ? dp(68) : -(captionEdit.getEditTextHeight() + AndroidUtilities.dp(12)) + AndroidUtilities.dp(64)));
        actionBarButtons.setPivotX(actionBarButtons.getMeasuredWidth() - dp(46 / 2.0f));
        animators.add(ObjectAnimator.ofFloat(actionBarButtons, View.ROTATION, editMode == EDIT_MODE_TIMELINE ? -90 : 0));
        animators.add(ObjectAnimator.ofFloat(playButton, View.ROTATION, editMode == EDIT_MODE_TIMELINE ? 90 : 0));
        animators.add(ObjectAnimator.ofFloat(muteButton, View.ROTATION, editMode == EDIT_MODE_TIMELINE ? 90 : 0));
        animators.add(ObjectAnimator.ofFloat(downloadButton, View.ROTATION, editMode == EDIT_MODE_TIMELINE ? 90 : 0));
        if (themeButton != null) {
            animators.add(ObjectAnimator.ofFloat(themeButton, View.ROTATION, editMode == EDIT_MODE_TIMELINE ? 90 : 0));
        }
        if (blurManager.hasRenderNode()) {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                    captionEdit.invalidateBlur();
                }
            });
            animators.add(valueAnimator);
        }

        if (oldEditMode != editMode) {
            onSwitchEditModeStart(oldEditMode, editMode);
        }
        if (timelineView != null) {
            timelineView.setOpen(outputEntry == null || !outputEntry.isCollage() || !outputEntry.hasVideo() || editMode == EDIT_MODE_TIMELINE,
                    animated);
        }
        if (animated) {
            editModeAnimator = new AnimatorSet();
            editModeAnimator.playTogether(animators);
            editModeAnimator.setDuration(320);
            editModeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            editModeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (oldEditMode != editMode) {
                        onSwitchEditModeEnd(oldEditMode, editMode);
                    }
                }
            });
            if (delay) {
                editModeAnimator.setStartDelay(120L);
            }
            editModeAnimator.start();
        } else {
            for (int i = 0; i < animators.size(); ++i) {
                Animator a = animators.get(i);
                a.setDuration(1);
                a.start();
            }
            if (oldEditMode != editMode) {
                onSwitchEditModeEnd(oldEditMode, editMode);
            }
        }
    }

    private void hidePhotoPaintView() {
        if (paintView == null) {
            return;
        }
        previewTouchable = null;
        paintView.getTopLayout().setAlpha(0f);
        paintView.getTopLayout().setTranslationY(-AndroidUtilities.dp(16));
        paintView.getBottomLayout().setAlpha(0f);
        paintView.getBottomLayout().setTranslationY(AndroidUtilities.dp(48));
        paintView.getWeightChooserView().setTranslationX(-AndroidUtilities.dp(32));
        paintView.setVisibility(View.GONE);
//        paintView.keyboardNotifier.ignore(true);
    }

    private void createPhotoPaintView() {
        if (paintView != null) {
            return;
        }
        Pair<Integer, Integer> size = previewView.getPaintSize();

        Bitmap paintViewBitmap = null;
        if (outputEntry != null && (outputEntry.isDraft || outputEntry.isEdit) && outputEntry.paintFile != null) {
            paintViewBitmap = BitmapFactory.decodeFile(outputEntry.paintFile.getPath());
        }
        if (paintViewBitmap == null) {
            paintViewBitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888);
        }

        boolean hasBlur = false;
        Bitmap paintViewBlurBitmap = null;
        if (outputEntry != null && (outputEntry.isDraft || outputEntry.isEdit) && outputEntry.paintBlurFile != null) {
            paintViewBlurBitmap = BitmapFactory.decodeFile(outputEntry.paintBlurFile.getPath());
            if (paintViewBlurBitmap != null) {
                hasBlur = true;
            }
        }
        if (paintViewBlurBitmap == null) {
            paintViewBlurBitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888);
        }

        int w = previewContainer.getMeasuredWidth(), h = previewContainer.getMeasuredHeight();
        paintView = new PaintView(
                getContext(),
                outputEntry != null && !outputEntry.fileDeletable,
                outputEntry == null ? null : outputEntry.file,
                outputEntry != null && outputEntry.isVideo,
                outputEntry != null && outputEntry.botId != 0,
                this,
                (Activity) getContext(),
                currentAccount,
                paintViewBitmap,
                paintViewBlurBitmap,
                null,
                previewView.getOrientation(),
                outputEntry == null ? null : outputEntry.mediaEntities,
                outputEntry,
                w, h,
                new MediaController.CropState(),
                null,
                blurManager,
                resourcesProvider,
                videoTextureHolder,
                previewView
        ) {
            @Override
            public void onEntityDraggedTop(boolean value) {
                previewHighlight.show(true, value, actionBarContainer);
            }

            @Override
            protected void onGalleryClick() {
                captionEdit.keyboardNotifier.ignore(true);
                destroyGalleryListView();
                createGalleryListView(true);
                animateGalleryListView(true);
            }

            @Override
            public void onEntityDraggedBottom(boolean value) {
                previewHighlight.updateCaption(captionEdit.getText());
                previewHighlight.show(false, value && multitouch, null);
            }

            @Override
            public void onEntityDragEnd(boolean delete) {
                if (!isEntityDeletable()) {
                    delete = false;
                }
                captionEdit.clearAnimation();
                captionEdit.animate().alpha(currentEditMode == EDIT_MODE_NONE ? 1f : 0).setDuration(180)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                videoTimelineContainerView.clearAnimation();
                videoTimelineContainerView.animate().alpha(currentEditMode == EDIT_MODE_NONE || currentEditMode == EDIT_MODE_TIMELINE ? 1f : 0)
                        .setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                showTrash(false, delete);
                if (delete) {
                    removeCurrentEntity();
                }
                super.onEntityDragEnd(delete);
                multitouch = false;
            }

            @Override
            public void onEntityDragStart() {
                paintView.showReactionsLayout(false);
                captionEdit.clearAnimation();
                captionEdit.animate().alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                if (currentEditMode != EDIT_MODE_TIMELINE) {
                    videoTimelineContainerView.clearAnimation();
                    videoTimelineContainerView.animate().alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                }
                showTrash(isEntityDeletable(), false);
            }

            public void showTrash(boolean show, boolean delete) {
                if (show) {
                    trash.setVisibility(View.VISIBLE);
                    trash.setAlpha(0f);
                    trash.clearAnimation();
                    trash.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                } else {
                    trash.onDragInfo(false, delete);
                    trash.clearAnimation();
                    trash.animate().alpha(0f).withEndAction(() -> {
                        trash.setVisibility(View.GONE);
                    }).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).setStartDelay(delete ? 500 : 0).start();
                }
            }

            private boolean multitouch;

            @Override
            public void onEntityDragMultitouchStart() {
                multitouch = true;
                paintView.showReactionsLayout(false);
                showTrash(false, false);
            }

            @Override
            public void onEntityDragMultitouchEnd() {
                multitouch = false;
                showTrash(isEntityDeletable(), false);
                previewHighlight.show(false, false, null);
            }

            @Override
            public void onEntityDragTrash(boolean enter) {
                trash.onDragInfo(enter, false);
            }

            @Override
            protected void editSelectedTextEntity() {
                captionEdit.editText.closeKeyboard();
                switchToEditMode(EDIT_MODE_PAINT, true);
                super.editSelectedTextEntity();
            }

            @Override
            public void dismiss() {
                captionEdit.editText.closeKeyboard();
                switchToEditMode(EDIT_MODE_NONE, true);
            }

            @Override
            protected void onOpenCloseStickersAlert(boolean open) {
                if (previewView != null) {
                    previewView.updatePauseReason(6, open);
                    if (playButton != null) {
                        playButton.drawable.setPause(previewView.isPlaying(), true);
                    }
                }
                if (captionEdit != null) {
                    captionEdit.ignoreTouches = open;
                    captionEdit.keyboardNotifier.ignore(open);
                }
            }

            @Override
            protected void onAudioSelect(MessageObject messageObject) {
                previewView.setupAudio(messageObject, true);
                if (outputEntry != null && !isVideo) {
                    boolean appear = !TextUtils.isEmpty(outputEntry.audioPath);
                    playButton.drawable.setPause(!previewView.isPlaying(), false);
                    playButton.setVisibility(View.VISIBLE);
                    playButton.animate().alpha(appear ? 1 : 0).withEndAction(() -> {
                        if (!appear) {
                            playButton.setVisibility(View.GONE);
                        }
                    }).start();
                }
                switchToEditMode(collageLayoutView.hasLayout() && collageLayoutView.hasVideo() && !TextUtils.isEmpty(outputEntry.audioPath)
                        ? EDIT_MODE_TIMELINE : EDIT_MODE_NONE, true, true);
            }

            @Override
            public void onEntityHandleTouched() {
                paintView.showReactionsLayout(false);
            }

            @Override
            protected boolean checkAudioPermission(Runnable granted) {
                if (activity == null) {
                    return true;
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 115);
                        audioGrantedCallback = granted;
                        return false;
                    }
                } else if (Build.VERSION.SDK_INT >= 23
                        && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 115);
                    audioGrantedCallback = granted;
                    return false;
                }
                return true;
            }

            @Override
            public void onCreateRound(RoundView roundView) {
                if (previewView != null) {
                    previewView.attachRoundView(roundView);
                }
                if (captionEdit != null) {
                    captionEdit.setHasRoundVideo(true);
                }
            }

            @Override
            public void onTryDeleteRound() {
                if (captionEdit != null) {
                    captionEdit.showRemoveRoundAlert();
                }
            }

            @Override
            public void onDeleteRound() {
                if (previewView != null) {
                    previewView.setupRound(null, null, true);
                }
                if (paintView != null) {
                    paintView.deleteRound();
                }
                if (captionEdit != null) {
                    captionEdit.setHasRoundVideo(false);
                }
                if (outputEntry != null) {
                    if (outputEntry.round != null) {
                        try {
                            outputEntry.round.delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.round = null;
                    }
                    if (outputEntry.roundThumb != null) {
                        try {
                            new File(outputEntry.roundThumb).delete();
                        } catch (Exception ignore) {
                        }
                        outputEntry.roundThumb = null;
                    }
                }
            }

            @Override
            public void onSwitchSegmentedAnimation(PhotoView photoView) {
                if (photoView == null) {
                    return;
                }
                ThanosEffect thanosEffect = getThanosEffect();
                if (thanosEffect == null) {
                    photoView.onSwitchSegmentedAnimationStarted(false);
                    return;
                }
                Bitmap bitmap = photoView.getSegmentedOutBitmap();
                if (bitmap == null) {
                    photoView.onSwitchSegmentedAnimationStarted(false);
                    return;
                }
                Matrix matrix = new Matrix();
                float w = photoView.getWidth(), h = photoView.getHeight();
                float tx = 0, ty = 0;
                if (photoView.getRotation() != 0) {
                    final float bw = bitmap.getWidth();
                    final float bh = bitmap.getHeight();
                    final float r = (float) Math.sqrt((bw / 2f) * (bw / 2f) + (bh / 2f) * (bh / 2f));
                    final float d = 2 * r;
                    Bitmap newBitmap = Bitmap.createBitmap((int) d, (int) d, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(newBitmap);
                    canvas.save();
                    canvas.rotate(photoView.getRotation(), r, r);
                    canvas.drawBitmap(bitmap, (d - bw) / 2, (d - bh) / 2, null);
                    bitmap.recycle();
                    bitmap = newBitmap;

                    final float pd = 2 * (float) Math.sqrt((w / 2f) * (w / 2f) + (h / 2f) * (h / 2f));
                    tx = -(pd - w) / 2;
                    ty = -(pd - h) / 2;
                    w = pd;
                    h = pd;
                }
                matrix.postScale(w, h);
                matrix.postScale(photoView.getScaleX(), photoView.getScaleY(), w / 2f, h / 2f);
                matrix.postTranslate(containerView.getX() + previewContainer.getX() + photoView.getX() + tx,
                        containerView.getY() + previewContainer.getY() + photoView.getY() + ty);
                thanosEffect.animate(matrix, bitmap, () -> {
                    photoView.onSwitchSegmentedAnimationStarted(true);
                }, () -> {
                });
            }

            @Override
            public void onSelectRound(RoundView roundView) {
                if (timelineView != null) {
                    timelineView.selectRound(true);
                }
            }

            @Override
            public void onDeselectRound(RoundView roundView) {
                if (timelineView != null) {
                    timelineView.selectRound(false);
                }
            }
        };
        paintView.setHasAudio(outputEntry != null && outputEntry.audioPath != null);
        paintView.setBlurManager(blurManager);
        containerView.addView(paintView);
        paintViewRenderView = paintView.getRenderView();
        if (paintViewRenderView != null) {
            paintViewRenderView.getPainting().hasBlur = hasBlur;
            previewContainer.addView(paintViewRenderView);
        }
        paintViewRenderInputView = paintView.getRenderInputView();
        if (paintViewRenderInputView != null) {
            previewContainer.addView(paintViewRenderInputView);
        }
        paintViewTextDim = paintView.getTextDimView();
        if (paintViewTextDim != null) {
            previewContainer.addView(paintViewTextDim);
        }
        paintViewEntitiesView = paintView.getEntitiesView();
        if (paintViewEntitiesView != null) {
            previewContainer.addView(paintViewEntitiesView);
        }
        paintViewSelectionContainerView = paintView.getSelectionEntitiesView();
        if (paintViewSelectionContainerView != null) {
            previewContainer.addView(paintViewSelectionContainerView);
        }
        orderPreviewViews();
        paintView.setOnDoneButtonClickedListener(() -> {
            switchToEditMode(EDIT_MODE_NONE, true);
        });
        paintView.setOnCancelButtonClickedListener(() -> {
            switchToEditMode(EDIT_MODE_NONE, true);
        });
        paintView.init();
    }

    private void orderPreviewViews() {
        if (paintViewRenderView != null) {
            paintViewRenderView.bringToFront();
        }
        if (paintViewRenderInputView != null) {
            paintViewRenderInputView.bringToFront();
        }
        if (paintViewTextDim != null) {
            paintViewTextDim.bringToFront();
        }
        if (paintViewEntitiesView != null) {
            paintViewEntitiesView.bringToFront();
        }
        if (paintViewSelectionContainerView != null) {
            paintViewSelectionContainerView.bringToFront();
        }
        if (trash != null) {
            trash.bringToFront();
        }
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.bringToFront();
        }
        if (photoFilterViewBlurControl != null) {
            photoFilterViewBlurControl.bringToFront();
        }
        if (photoFilterViewCurvesControl != null) {
            photoFilterViewCurvesControl.bringToFront();
        }
        if (previewHighlight != null) {
            previewHighlight.bringToFront();
        }
        if (currentRoundRecorder != null) {
            currentRoundRecorder.bringToFront();
        }
    }

    private void destroyPhotoPaintView() {
        if (paintView == null) {
            return;
        }
        paintView.onCleanupEntities();

        paintView.shutdown();
        containerView.removeView(paintView);
        paintView = null;
        if (paintViewRenderView != null) {
            previewContainer.removeView(paintViewRenderView);
            paintViewRenderView = null;
        }
        if (paintViewTextDim != null) {
            previewContainer.removeView(paintViewTextDim);
            paintViewTextDim = null;
        }
        if (paintViewRenderInputView != null) {
            previewContainer.removeView(paintViewRenderInputView);
            paintViewRenderInputView = null;
        }
        if (paintViewEntitiesView != null) {
            previewContainer.removeView(paintViewEntitiesView);
            paintViewEntitiesView = null;
        }
        if (paintViewSelectionContainerView != null) {
            previewContainer.removeView(paintViewSelectionContainerView);
            paintViewSelectionContainerView = null;
        }
    }

    private void onSwitchEditModeStart(int fromMode, int toMode) {
        if (toMode == EDIT_MODE_NONE) {
            backButton.setVisibility(View.VISIBLE);
            captionEdit.setVisibility(View.VISIBLE);
            if (paintView != null) {
                paintView.clearSelection();
            }
            downloadButton.setVisibility(View.VISIBLE);
            if (outputEntry != null && outputEntry.isRepostMessage) {
                getThemeButton().setVisibility(View.VISIBLE);
                updateThemeButtonDrawable(false);
            } else if (themeButton != null) {
                themeButton.setVisibility(View.GONE);
            }
            titleTextView.setVisibility(View.VISIBLE);
//            privacySelector.setVisibility(View.VISIBLE);
            if (isVideo) {
                muteButton.setVisibility(View.VISIBLE);
                playButton.setVisibility(View.VISIBLE);
            } else if (outputEntry != null && !TextUtils.isEmpty(outputEntry.audioPath)) {
                muteButton.setVisibility(View.GONE);
                playButton.setVisibility(View.VISIBLE);
            }
            timelineView.setVisibility(View.VISIBLE);
        }
        if (toMode == EDIT_MODE_PAINT && paintView != null) {
            paintView.setVisibility(View.VISIBLE);
        }
        if ((toMode == EDIT_MODE_PAINT || fromMode == EDIT_MODE_PAINT) && paintView != null) {
            paintView.onAnimationStateChanged(true);
        }

        if (paintView != null) {
            paintView.keyboardNotifier.ignore(toMode != EDIT_MODE_PAINT);
        }
        captionEdit.keyboardNotifier.ignore(toMode != EDIT_MODE_NONE);
//        privacySelectorHint.hide();
        Bulletin.hideVisible();
        if (photoFilterView != null && fromMode == EDIT_MODE_FILTER) {
            applyFilter(null);
        }
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setAllowTouch(false);
        }
        muteHint.hide();
    }

    private void onSwitchEditModeEnd(int fromMode, int toMode) {
        if (toMode == EDIT_MODE_PAINT) {
            backButton.setVisibility(View.GONE);
        }
        if (fromMode == EDIT_MODE_PAINT && paintView != null) {
            paintView.setVisibility(View.GONE);
        }
        if (fromMode == EDIT_MODE_NONE) {
            captionEdit.setVisibility(View.GONE);
            muteButton.setVisibility(toMode == EDIT_MODE_TIMELINE ? View.VISIBLE : View.GONE);
            playButton.setVisibility(toMode == EDIT_MODE_TIMELINE ? View.VISIBLE : View.GONE);
            downloadButton.setVisibility(toMode == EDIT_MODE_TIMELINE ? View.VISIBLE : View.GONE);
            if (themeButton != null) {
                themeButton.setVisibility(toMode == EDIT_MODE_TIMELINE ? View.VISIBLE : View.GONE);
            }
            timelineView.setVisibility(toMode == EDIT_MODE_TIMELINE ? View.VISIBLE : View.GONE);
            titleTextView.setVisibility(View.GONE);
        }
        previewView.setAllowCropping(toMode == EDIT_MODE_NONE);
        if ((toMode == EDIT_MODE_PAINT || fromMode == EDIT_MODE_PAINT) && paintView != null) {
            paintView.onAnimationStateChanged(false);
        }
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setAllowTouch(toMode == EDIT_MODE_FILTER || toMode == EDIT_MODE_NONE);
        }
    }

    public void applyPaintInBackground(Runnable whenDone) {
        final PaintView paintView = this.paintView;
        final StoryEntry outputEntry = this.outputEntry;
        if (paintView == null || outputEntry == null) {
            applyPaintMessage();
            whenDone.run();
            return;
        }

        outputEntry.clearPaint();
        final boolean hasChanges = paintView.hasChanges();
        final boolean hasBlur = paintView.hasBlur();
        final int resultWidth = outputEntry.resultWidth;
        final int resultHeight = outputEntry.resultHeight;
        Utilities.searchQueue.postRunnable(() -> {

            ArrayList<VideoEditedInfo.MediaEntity> mediaEntities = new ArrayList<>();

            paintView.getBitmap(mediaEntities, resultWidth, resultHeight, false, false, false, false, outputEntry);
            if (!outputEntry.isVideo) {
                outputEntry.averageDuration = Utilities.clamp(paintView.getLcm(), 7500L, 5000L);
            }
            List<TLRPC.InputDocument> masks = paintView.getMasks();
            final List<TLRPC.InputDocument> stickers = masks != null ? new ArrayList<>(masks) : null;
            final boolean isVideo = outputEntry.isVideo;
            final boolean wouldBeVideo = outputEntry.wouldBeVideo();

            mediaEntities.clear();
            Bitmap bitmap = paintView.getBitmap(mediaEntities, resultWidth, resultHeight, true, false, false, !isVideo, outputEntry);
            if (mediaEntities.isEmpty()) {
                mediaEntities = null;
            }

            final File paintFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                    ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, outputEntry.resultWidth, outputEntry.resultHeight, 87, false,
                            101, 101), true);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;

            final File backgroundFile;
            if (outputEntry.isRepostMessage && outputEntry.backgroundWallpaperPeerId != Long.MIN_VALUE) {
                Drawable drawable = outputEntry.backgroundDrawable;
                if (drawable == null) {
                    drawable = PreviewView.getBackgroundDrawable(null, currentAccount, outputEntry.backgroundWallpaperPeerId, isDark);
                }
                if (drawable != null) {
                    backgroundFile = StoryEntry.makeCacheFile(currentAccount, "webp");
                    bitmap = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
                    StoryEntry.drawBackgroundDrawable(new Canvas(bitmap), drawable, bitmap.getWidth(), bitmap.getHeight());
                    try {
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(backgroundFile));
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        bitmap = null;
                    }
                } else {
                    backgroundFile = null;
                }
            } else {
                backgroundFile = null;
            }
            File messageVideoMaskFile = null;
            if (outputEntry.isRepostMessage && outputEntry.isVideo) {
                int videoWidth = outputEntry.width;
                int videoHeight = outputEntry.height;
                MessageEntityView messageEntityView = paintView.findMessageView();
                ImageReceiver photoImage = null;
                if (messageEntityView != null && messageEntityView.listView.getChildCount() == 1 && videoWidth > 0 && videoHeight > 0) {
                    View child = messageEntityView.listView.getChildAt(0);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) messageEntityView.listView.getChildAt(0);
                        photoImage = cell.getPhotoImage();
                    }
                }
                if (photoImage != null && (int) photoImage.getImageWidth() > 0 && (int) photoImage.getImageHeight() > 0) {
                    float scale = Math.max(photoImage.getImageWidth() / videoWidth, photoImage.getImageHeight() / videoHeight);
                    final float S = 2f;
                    int w = (int) (videoWidth * scale / S), h = (int) (videoHeight * scale / S);
                    Bitmap maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    float[] radii = new float[8];
                    for (int a = 0; a < photoImage.getRoundRadius().length; a++) {
                        radii[a * 2] = photoImage.getRoundRadius()[a];
                        radii[a * 2 + 1] = photoImage.getRoundRadius()[a];
                    }
                    Canvas canvas = new Canvas(maskBitmap);
                    Path path = new Path();
                    canvas.scale(1f / S, 1f / S);
                    AndroidUtilities.rectTmp.set(
                            w * S / 2f - photoImage.getImageWidth() / 2f,
                            h * S / 2f - photoImage.getImageHeight() / 2f,
                            w * S / 2f + photoImage.getImageWidth() / 2f,
                            h * S / 2f + photoImage.getImageHeight() / 2f
                    );
                    path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.WHITE);
                    canvas.drawPath(path, paint);
                    try {
                        messageVideoMaskFile = StoryEntry.makeCacheFile(currentAccount, "webp");
                        maskBitmap.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(messageVideoMaskFile));
                    } catch (Exception e) {
                        FileLog.e(e);
                        messageVideoMaskFile = null;
                    }
                    maskBitmap.recycle();
                }
            }
            final File finalMessageVideoMaskFile = messageVideoMaskFile;

            final File paintEntitiesFile;
            if (!wouldBeVideo) {
                bitmap = paintView.getBitmap(new ArrayList<>(), resultWidth, resultHeight, false, true, false, false, outputEntry);
                paintEntitiesFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                        ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, resultWidth, resultHeight, 87, false, 101, 101), true);
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                bitmap = null;
            } else {
                paintEntitiesFile = null;
            }

            final File paintBlurFile;
            if (hasBlur) {
                bitmap = paintView.getBlurBitmap();
                paintBlurFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                        ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, resultWidth, resultHeight, 87, false, 101, 101), true);
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                bitmap = null;
            } else {
                paintBlurFile = null;
            }

            final ArrayList<VideoEditedInfo.MediaEntity> finalMediaEntities = mediaEntities;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (outputEntry.paintFile != null) {
                        outputEntry.paintFile.delete();
                    }
                } catch (Exception ignore) {
                }
                try {
                    if (outputEntry.paintEntitiesFile != null) {
                        outputEntry.paintEntitiesFile.delete();
                    }
                } catch (Exception ignore) {
                }
                try {
                    if (outputEntry.paintBlurFile != null) {
                        outputEntry.paintBlurFile.delete();
                    }
                } catch (Exception ignore) {
                }
                outputEntry.paintFile = null;
                outputEntry.paintEntitiesFile = null;
                outputEntry.paintBlurFile = null;
                if (outputEntry.backgroundFile != null) {
                    try {
                        outputEntry.backgroundFile.delete();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    outputEntry.backgroundFile = null;
                }
                if (outputEntry.messageVideoMaskFile != null) {
                    try {
                        outputEntry.messageVideoMaskFile.delete();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    outputEntry.messageVideoMaskFile = null;
                }

                outputEntry.editedMedia |= hasChanges;
                outputEntry.mediaEntities = finalMediaEntities;
                outputEntry.paintFile = paintFile;
                outputEntry.backgroundFile = backgroundFile;
                outputEntry.paintEntitiesFile = paintEntitiesFile;
                outputEntry.messageVideoMaskFile = finalMessageVideoMaskFile;
                outputEntry.paintBlurFile = paintBlurFile;
                outputEntry.stickers = stickers;

                applyPaintMessage();
                if (whenDone != null) {
                    whenDone.run();
                }
            });
        });
    }

    private void applyPaint() {
        if (paintView == null || outputEntry == null) {
            return;
        }

        outputEntry.clearPaint();
        outputEntry.editedMedia |= paintView.hasChanges();

        if (outputEntry.mediaEntities == null) {
            outputEntry.mediaEntities = new ArrayList<>();
        } else {
            outputEntry.mediaEntities.clear();
        }
        paintView.getBitmap(outputEntry.mediaEntities, outputEntry.resultWidth, outputEntry.resultHeight, false, false, false, false, outputEntry);
        if (!outputEntry.isVideo) {
            outputEntry.averageDuration = Utilities.clamp(paintView.getLcm(), 7500L, 5000L);
        }
        List<TLRPC.InputDocument> masks = paintView.getMasks();
        outputEntry.stickers = masks != null ? new ArrayList<>(masks) : null;
        final boolean isVideo = outputEntry.isVideo;
        final boolean wouldBeVideo = outputEntry.wouldBeVideo();

        outputEntry.mediaEntities = new ArrayList<>();
        Bitmap bitmap = paintView.getBitmap(outputEntry.mediaEntities, outputEntry.resultWidth, outputEntry.resultHeight, true, false, false,
                !isVideo, outputEntry);
        if (outputEntry.mediaEntities.isEmpty()) {
            outputEntry.mediaEntities = null;
        }

        try {
            if (outputEntry.paintFile != null) {
                outputEntry.paintFile.delete();
            }
        } catch (Exception ignore) {
        }
        try {
            if (outputEntry.paintEntitiesFile != null) {
                outputEntry.paintEntitiesFile.delete();
            }
        } catch (Exception ignore) {
        }
        try {
            if (outputEntry.paintBlurFile != null) {
                outputEntry.paintBlurFile.delete();
            }
        } catch (Exception ignore) {
        }
        outputEntry.paintFile = null;
        outputEntry.paintEntitiesFile = null;
        outputEntry.paintBlurFile = null;

        outputEntry.paintFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, outputEntry.resultWidth, outputEntry.resultHeight, 87, false, 101,
                        101), true);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;

        if (outputEntry.isRepostMessage) {
            if (outputEntry.backgroundFile != null) {
                try {
                    outputEntry.backgroundFile.delete();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                outputEntry.backgroundFile = null;
            }
            if (outputEntry.backgroundWallpaperPeerId != Long.MIN_VALUE) {
                Drawable drawable = outputEntry.backgroundDrawable;
                if (drawable == null) {
                    drawable = PreviewView.getBackgroundDrawable(null, currentAccount, outputEntry.backgroundWallpaperPeerId, isDark);
                }
                if (drawable != null) {
                    outputEntry.backgroundFile = StoryEntry.makeCacheFile(currentAccount, "webp");
                    bitmap = Bitmap.createBitmap(outputEntry.resultWidth, outputEntry.resultHeight, Bitmap.Config.ARGB_8888);
                    StoryEntry.drawBackgroundDrawable(new Canvas(bitmap), drawable, bitmap.getWidth(), bitmap.getHeight());
                    try {
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(outputEntry.backgroundFile));
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        bitmap = null;
                    }
                }
            }
        }
        if (outputEntry.isRepostMessage) {
            if (outputEntry.messageVideoMaskFile != null) {
                try {
                    outputEntry.messageVideoMaskFile.delete();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                outputEntry.messageVideoMaskFile = null;
            }
            if (outputEntry.isRepostMessage && outputEntry.isVideo) {
                int videoWidth = outputEntry.width;
                int videoHeight = outputEntry.height;
                MessageEntityView messageEntityView = paintView.findMessageView();
                if (messageEntityView != null && messageEntityView.listView.getChildCount() == 1 && videoWidth > 0 && videoHeight > 0) {
                    View child = messageEntityView.listView.getChildAt(0);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) messageEntityView.listView.getChildAt(0);
                        ImageReceiver photoImage = cell.getPhotoImage();
                        if (photoImage != null && (int) photoImage.getImageWidth() > 0 && (int) photoImage.getImageHeight() > 0) {
                            float scale = Math.max(photoImage.getImageWidth() / videoWidth, photoImage.getImageHeight() / videoHeight);
                            final float S = 2f;
                            int w = (int) (videoWidth * scale / S), h = (int) (videoHeight * scale / S);
                            Bitmap maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            float[] radii = new float[8];
                            for (int a = 0; a < photoImage.getRoundRadius().length; a++) {
                                radii[a * 2] = photoImage.getRoundRadius()[a];
                                radii[a * 2 + 1] = photoImage.getRoundRadius()[a];
                            }
                            Canvas canvas = new Canvas(maskBitmap);
                            Path path = new Path();
                            canvas.scale(1f / S, 1f / S);
                            AndroidUtilities.rectTmp.set(
                                    w * S / 2f - photoImage.getImageWidth() / 2f,
                                    h * S / 2f - photoImage.getImageHeight() / 2f,
                                    w * S / 2f + photoImage.getImageWidth() / 2f,
                                    h * S / 2f + photoImage.getImageHeight() / 2f
                            );
                            path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            paint.setColor(Color.WHITE);
                            canvas.drawPath(path, paint);
                            try {
                                outputEntry.messageVideoMaskFile = StoryEntry.makeCacheFile(currentAccount, "webp");
                                maskBitmap.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(outputEntry.messageVideoMaskFile));
                            } catch (Exception e) {
                                FileLog.e(e);
                                outputEntry.messageVideoMaskFile = null;
                            }
                            maskBitmap.recycle();
                        }
                    }
                }
            }
        }

        if (!wouldBeVideo) {
            bitmap = paintView.getBitmap(new ArrayList<>(), outputEntry.resultWidth, outputEntry.resultHeight, false, true, false, false,
                    outputEntry);
            outputEntry.paintEntitiesFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                    ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, outputEntry.resultWidth, outputEntry.resultHeight, 87, false,
                            101, 101), true);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }

        if (paintView.hasBlur()) {
            bitmap = paintView.getBlurBitmap();
            outputEntry.paintBlurFile = FileLoader.getInstance(currentAccount).getPathToAttach(
                    ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, outputEntry.resultWidth, outputEntry.resultHeight, 87, false,
                            101, 101), true);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }
    }

    // separated to run on main thread (chatmessagecell uses global paints and resources)
    private void applyPaintMessage() {
        if (paintView == null || outputEntry == null) {
            return;
        }

        if (outputEntry.isRepostMessage) {
            if (outputEntry.messageFile != null) {
                try {
                    outputEntry.messageFile.delete();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                outputEntry.messageFile = null;
            }
            outputEntry.messageFile = StoryEntry.makeCacheFile(currentAccount, "webp");
            Bitmap bitmap = paintView.getBitmap(outputEntry.mediaEntities, outputEntry.resultWidth, outputEntry.resultHeight, false, false, true,
                    !isVideo, outputEntry);
            try {
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(outputEntry.messageFile));
            } catch (Exception e) {
                FileLog.e(e);
                try {
                    outputEntry.messageFile.delete();
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
                outputEntry.messageFile = null;
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                bitmap = null;
            }
        }
    }

    public void applyFilter(Runnable whenDone) {
        if (photoFilterView == null || outputEntry == null) {
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        outputEntry.editedMedia |= photoFilterView.hasChanges();
        outputEntry.updateFilter(photoFilterView, whenDone);
        if (whenDone == null && !outputEntry.isVideo && previewView != null) {
            previewView.set(outputEntry);
        }
    }

//    private Matrix photoFilterStartMatrix, photoFilterEndMatrix;

    private void createFilterPhotoView() {
        if (photoFilterView != null || outputEntry == null) {
            return;
        }

        Bitmap photoBitmap = null;
        if (!outputEntry.isVideo) {
            if (outputEntry.filterFile == null) {
                photoBitmap = previewView.getPhotoBitmap();
            } else {
                photoBitmap = StoryEntry.getScaledBitmap(opts -> BitmapFactory.decodeFile(outputEntry.file.getAbsolutePath(), opts),
                        AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y, true, true);
            }
        }
        if (photoBitmap == null && !outputEntry.isVideo) {
            return;
        }

        photoFilterView = new PhotoFilterView(activity, previewView.getTextureView(), photoBitmap, previewView.getOrientation(),
                outputEntry == null ? null : outputEntry.filterState, null, 0, false, false, blurManager, resourcesProvider);
        containerView.addView(photoFilterView);
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setFilterView(photoFilterView);
        }
        photoFilterViewTextureView = photoFilterView.getMyTextureView();
        if (photoFilterViewTextureView != null) {
            photoFilterViewTextureView.setOpaque(false);
        }
        previewView.setFilterTextureView(photoFilterViewTextureView, photoFilterView);
        if (photoFilterViewTextureView != null) {
            photoFilterViewTextureView.setAlpha(0f);
            photoFilterViewTextureView.animate().alpha(1f).setDuration(220).start();
        }
        applyFilterMatrix();
        photoFilterViewBlurControl = photoFilterView.getBlurControl();
        if (photoFilterViewBlurControl != null) {
            previewContainer.addView(photoFilterViewBlurControl);
        }
        photoFilterViewCurvesControl = photoFilterView.getCurveControl();
        if (photoFilterViewCurvesControl != null) {
            previewContainer.addView(photoFilterViewCurvesControl);
        }
        orderPreviewViews();

        photoFilterView.getDoneTextView().setOnClickListener(v -> {
            switchToEditMode(EDIT_MODE_NONE, true);
        });
        photoFilterView.getCancelTextView().setOnClickListener(v -> {
            switchToEditMode(EDIT_MODE_NONE, true);
        });
        photoFilterView.getToolsView().setVisibility(View.GONE);
        photoFilterView.getToolsView().setAlpha(0f);
        photoFilterView.getToolsView().setTranslationY(AndroidUtilities.dp(186));
        photoFilterView.init();
    }

    public void invalidateBlur() {
        if (captionEdit != null) {
            captionEdit.invalidateBlur();
        }
    }

    private void applyFilterMatrix() {
        if (outputEntry != null && photoFilterViewTextureView != null && previewContainer.getMeasuredWidth() > 0
                && previewContainer.getMeasuredHeight() > 0) {
            Matrix photoFilterStartMatrix = new Matrix();
            photoFilterStartMatrix.reset();
            if (outputEntry.orientation != 0) {
                photoFilterStartMatrix.postRotate(-outputEntry.orientation, previewContainer.getMeasuredWidth() / 2f,
                        previewContainer.getMeasuredHeight() / 2f);
                if (outputEntry.orientation / 90 % 2 == 1) {
                    photoFilterStartMatrix.postScale(
                            (float) previewContainer.getMeasuredWidth() / previewContainer.getMeasuredHeight(),
                            (float) previewContainer.getMeasuredHeight() / previewContainer.getMeasuredWidth(),
                            previewContainer.getMeasuredWidth() / 2f,
                            previewContainer.getMeasuredHeight() / 2f
                    );
                }
            }
            photoFilterStartMatrix.postScale(
                    1f / previewContainer.getMeasuredWidth() * outputEntry.width,
                    1f / previewContainer.getMeasuredHeight() * outputEntry.height
            );
            photoFilterStartMatrix.postConcat(outputEntry.matrix);
            photoFilterStartMatrix.postScale(
                    (float) previewContainer.getMeasuredWidth() / outputEntry.resultWidth,
                    (float) previewContainer.getMeasuredHeight() / outputEntry.resultHeight
            );
            photoFilterViewTextureView.setTransform(photoFilterStartMatrix);
            photoFilterViewTextureView.invalidate();
        }
    }

    public void destroyPhotoFilterView() {
        if (photoFilterView == null) {
            return;
        }
        photoFilterView.shutdown();
        photoFilterEnhanceView.setFilterView(null);
        containerView.removeView(photoFilterView);
        if (photoFilterViewTextureView != null) {
            previewContainer.removeView(photoFilterViewTextureView);
            photoFilterViewTextureView = null;
        }
        previewView.setFilterTextureView(null, null);
        if (photoFilterViewBlurControl != null) {
            previewContainer.removeView(photoFilterViewBlurControl);
            photoFilterViewBlurControl = null;
        }
        if (photoFilterViewCurvesControl != null) {
            previewContainer.removeView(photoFilterViewCurvesControl);
            photoFilterViewCurvesControl = null;
        }
        photoFilterView = null;
//        photoFilterStartMatrix = null;
//        photoFilterEndMatrix = null;
//        if (photoFilterAnimator != null) {
//            photoFilterAnimator.cancel();
//            photoFilterAnimator = null;
//        }
    }

    private boolean noCameraPermission;

    @SuppressLint("ClickableViewAccessibility")
    private void createCameraView() {
        if (cameraView != null || getContext() == null) {
            return;
        }
        cameraView = new DualCameraView(getContext(), getCameraFace(), false) {
            @Override
            public void onEntityDraggedTop(boolean value) {
                previewHighlight.show(true, value, actionBarContainer);
            }

            @Override
            public void onEntityDraggedBottom(boolean value) {
                previewHighlight.updateCaption(captionEdit.getText());
                previewHighlight.show(false, value, controlContainer);
            }

            @Override
            public void toggleDual() {
                super.toggleDual();
                dualButton.setValue(isDual());
//                recordControl.setDual(isDual());
                setCameraFlashModeIcon(getCurrentFlashMode(), true);
            }

            @Override
            protected void onSavedDualCameraSuccess() {
                if (MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) < 2) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (takingVideo || takingPhoto || cameraView == null || currentPage != PAGE_CAMERA) {
                            return;
                        }
                        if (savedDualHint != null) {
                            CharSequence text = isFrontface() ? getString(R.string.StoryCameraSavedDualBackHint)
                                    : getString(R.string.StoryCameraSavedDualFrontHint);
                            savedDualHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, savedDualHint.getTextPaint()));
                            savedDualHint.setText(text);
                            savedDualHint.show();
                            MessagesController.getGlobalMainSettings().edit()
                                    .putInt("storysvddualhint", MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) + 1).apply();
                        }
                    }, 340);
                }
                dualButton.setValue(isDual());
            }

            @Override
            protected void receivedAmplitude(double amplitude) {
                if (recordControl != null) {
                    recordControl.setAmplitude(Utilities.clamp((float) (amplitude / WaveDrawable.MAX_AMPLITUDE), 1, 0), true);
                }
            }
        };
        if (recordControl != null) {
            recordControl.setAmplitude(0, false);
        }
        cameraView.recordHevc = !collageLayoutView.hasLayout();
        cameraView.setThumbDrawable(getCameraThumb());
        cameraView.initTexture();
        cameraView.setDelegate(() -> {
            String currentFlashMode = getCurrentFlashMode();
            if (TextUtils.equals(currentFlashMode, getNextFlashMode())) {
                currentFlashMode = null;
            }
            setCameraFlashModeIcon(currentPage == PAGE_CAMERA ? currentFlashMode : null, true);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom = 0, false);
            }
            updateActionBarButtons(true);
        });
        setActionBarButtonVisible(dualButton, cameraView.dualAvailable() && currentPage == PAGE_CAMERA, true);
        collageButton.setTranslationX(cameraView.dualAvailable() ? 0 : dp(46));
//        collageLayoutView.getLast().addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity
//        .FILL));
        collageLayoutView.setCameraView(cameraView);
        if (MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) < 1) {
            cameraHint.show();
            MessagesController.getGlobalMainSettings().edit()
                    .putInt("storyhint2", MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) + 1).apply();
        } else if (!cameraView.isSavedDual() && cameraView.dualAvailable()
                && MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) < 2) {
            dualHint.show();
        }
    }

    private int frontfaceFlashMode = -1;
    private ArrayList<String> frontfaceFlashModes;

    private void checkFrontfaceFlashModes() {
        if (frontfaceFlashMode < 0) {
            frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
            frontfaceFlashModes = new ArrayList<>();
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

            flashViews.setWarmth(MessagesController.getGlobalMainSettings().getFloat("frontflash_warmth", .9f));
            flashViews.setIntensity(MessagesController.getGlobalMainSettings().getFloat("frontflash_intensity", 1));
        }
    }

    private void saveFrontFaceFlashMode() {
        if (frontfaceFlashMode >= 0) {
            MessagesController.getGlobalMainSettings().edit()
                    .putFloat("frontflash_warmth", flashViews.warmth)
                    .putFloat("frontflash_intensity", flashViews.intensity)
                    .apply();
        }
    }

    private String getCurrentFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode);
        }
        return cameraView.getCameraSession().getCurrentFlashMode();
    }

    private String getNextFlashMode() {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return null;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
        }
        return cameraView.getCameraSession().getNextFlashMode();
    }

    private void setCurrentFlashMode(String mode) {
        if (cameraView == null || cameraView.getCameraSession() == null) {
            return;
        }
        if (cameraView.isFrontface() && !cameraView.getCameraSession().hasFlashModes()) {
            int index = frontfaceFlashModes.indexOf(mode);
            if (index >= 0) {
                frontfaceFlashMode = index;
                MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
            }
            return;
        }
        cameraView.getCameraSession().setCurrentFlashMode(mode);
    }

    private Drawable getCameraThumb() {
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {
        }
        if (bitmap != null) {
            return new BitmapDrawable(bitmap);
        } else {
            return getContext().getResources().getDrawable(R.drawable.icplaceholder);
        }
    }

    private void saveLastCameraBitmap(Runnable whenDone) {
        if (cameraView == null || cameraView.getTextureView() == null) {
            return;
        }
        try {
            TextureView textureView = cameraView.getTextureView();
            final Bitmap bitmap = textureView.getBitmap();
            Utilities.themeQueue.postRunnable(() -> {
                try {
                    if (bitmap != null) {
                        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), cameraView.getMatrix(), true);
                        bitmap.recycle();
                        Bitmap bitmap2 = newBitmap;
                        Bitmap lastBitmap = Bitmap.createScaledBitmap(bitmap2, 80, (int) (bitmap2.getHeight() / (bitmap2.getWidth() / 80.0f)), true);
                        if (lastBitmap != null) {
                            if (lastBitmap != bitmap2) {
                                bitmap2.recycle();
                            }
                            Utilities.blurBitmap(lastBitmap, 7, 1, lastBitmap.getWidth(), lastBitmap.getHeight(), lastBitmap.getRowBytes());
                            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                            FileOutputStream stream = new FileOutputStream(file);
                            lastBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                            lastBitmap.recycle();
                            stream.close();
                        }
                    }
                } catch (Throwable ignore) {

                } finally {
                    AndroidUtilities.runOnUIThread(whenDone);
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private void showDismissEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(getString(R.string.DiscardChanges));
        builder.setMessage(getString(R.string.PhotoEditorDiscardAlert));
        if (outputEntry != null && !outputEntry.isEdit) {
            builder.setNeutralButton(getString(outputEntry.isDraft ? R.string.StoryKeepDraft : R.string.StorySaveDraft), (di, i) -> {
                if (outputEntry == null) {
                    return;
                }
                outputEntry.captionEntitiesAllowed = MessagesController.getInstance(currentAccount).storyEntitiesAllowed();
                showSavedDraftHint = !outputEntry.isDraft;
                applyFilter(null);
                applyPaint();
                applyPaintMessage();
                destroyPhotoFilterView();
                StoryEntry storyEntry = outputEntry;
                storyEntry.destroy(true);
                storyEntry.caption = captionEdit.getText();
                outputEntry = null;
                prepareThumb(storyEntry, true);
                DraftsController drafts = MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController();
                if (storyEntry.isDraft) {
                    drafts.edit(storyEntry);
                } else {
                    drafts.append(storyEntry);
                }
                navigateTo(PAGE_CAMERA, true);
            });
        }
        builder.setPositiveButton(outputEntry != null && outputEntry.isDraft && !outputEntry.isEdit ? getString(R.string.StoryDeleteDraft)
                : getString(R.string.Discard), (dialogInterface, i) -> {
            if (outputEntry != null && !(outputEntry.isEdit || outputEntry.isRepost && !outputEntry.isRepostMessage) && outputEntry.isDraft) {
                MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(outputEntry);
                outputEntry = null;
            }
            if (outputEntry != null && (outputEntry.isEdit || outputEntry.isRepost && !outputEntry.isRepostMessage)) {
                delegate.close(true);
            } else {
                navigateTo(PAGE_CAMERA, true);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        View positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton instanceof TextView) {
            ((TextView) positiveButton).setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            positiveButton.setBackground(Theme.createRadSelectorDrawable(
                    ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), (int) (0.2f * 255)), 6, 6));
        }
    }

    private void destroyCameraView(boolean waitForThumb) {
        if (cameraView != null) {
            if (waitForThumb) {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                    if (cameraView != null) {
                        cameraView.destroy(true, null);
                        AndroidUtilities.removeFromParent(cameraView);
                        if (collageLayoutView != null) {
                            collageLayoutView.setCameraView(null);
                        }
                        cameraView = null;
                    }
                });
            } else {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                });
                cameraView.destroy(true, null);
                AndroidUtilities.removeFromParent(cameraView);
                if (collageLayoutView != null) {
                    collageLayoutView.setCameraView(null);
                }
                cameraView = null;
            }
        }
    }

    public interface Touchable {
        boolean onTouch(MotionEvent event);
    }

    private Touchable previewTouchable;
    private boolean requestedCameraPermission;

    private void requestCameraPermission(boolean force) {
        if (requestedCameraPermission && !force) {
            return;
        }
        noCameraPermission = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity != null) {
            noCameraPermission = activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            if (noCameraPermission) {
                Drawable iconDrawable = getContext().getResources().getDrawable(R.drawable.story_camera).mutate();
                iconDrawable.setColorFilter(new PorterDuffColorFilter(0x3dffffff, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable drawable = new CombinedDrawable(new ColorDrawable(0xff222222), iconDrawable);
                drawable.setIconSize(dp(64), dp(64));
                cameraViewThumb.setImageDrawable(drawable);
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                            .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false,
                                    Theme.getColor(Theme.key_dialogTopBackground))
                            .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoCameraWithHint)))
                            .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                    activity.startActivity(intent);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            })
                            .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                            .create()
                            .show();
                    return;
                }
                activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 111);
                requestedCameraPermission = true;
            }
        }

        if (!noCameraPermission) {
            if (CameraController.getInstance().isCameraInitied()) {
                createCameraView();
            } else {
                CameraController.getInstance().initCamera(this::createCameraView);
            }
        }
    }

    private boolean requestGalleryPermission() {
        if (activity != null) {
            boolean noGalleryPermission = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                noGalleryPermission = (
                        activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                                activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                );
                if (noGalleryPermission) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 114);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                noGalleryPermission = activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
                if (noGalleryPermission) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 114);
                }
            }
            return !noGalleryPermission;
        }
        return true;
    }

    private boolean requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity != null) {
            boolean granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 112);
                return false;
            }
        }
        return true;
    }

    private Runnable whenOpenDone;

    public void onResume(boolean isAnimating) {
        if (currentPage == PAGE_CAMERA) {
//            requestedCameraPermission = false;
            if (isAnimating) {
                whenOpenDone = () -> requestCameraPermission(false);
            } else {
                requestCameraPermission(false);
            }
        }
        if (captionEdit != null) {
            captionEdit.onResume();
        }
        if (recordControl != null) {
            recordControl.updateGalleryImage();
        }
        if (previewHighlight != null) {
            previewHighlight.updateCount();
        }
        if (paintView != null) {
            paintView.onResume();
        }
        if (previewView != null) {
            previewView.updatePauseReason(0, false);
        }

        MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().load();
    }

    public void onPause() {
        destroyCameraView(false);
        if (captionEdit != null) {
            captionEdit.onPause();
        }
        if (previewView != null) {
            previewView.updatePauseReason(0, true);
        }
    }

    private Runnable audioGrantedCallback;

    public void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        final boolean granted = grantResults != null && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 111) {
            noCameraPermission = !granted;
            if (granted && currentPage == PAGE_CAMERA) {
                cameraViewThumb.setImageDrawable(null);
                if (CameraController.getInstance().isCameraInitied()) {
                    createCameraView();
                } else {
                    CameraController.getInstance().initCamera(this::createCameraView);
                }
            }
        } else if (requestCode == 114) {
            if (granted) {
                MediaController.loadGalleryPhotosAlbums(0);
                animateGalleryListView(true);
            } else {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTopAnimation(R.raw.permission_request_folder, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false,
                                Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionStorageWithHint)))
                        .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
        } else if (requestCode == 112) {
            if (!granted) {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false,
                                Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoCameraMicVideo)))
                        .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
        } else if (requestCode == 115) {
            if (!granted) {
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTopAnimation(R.raw.permission_request_folder, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false,
                                Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoAudioStorageStory)))
                        .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                        .create()
                        .show();
            }
            if (granted && audioGrantedCallback != null) {
                audioGrantedCallback.run();
            }
            audioGrantedCallback = null;
        }
    }

    private void saveCameraFace(boolean frontface) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("stories_camera", frontface).apply();
    }

    private boolean getCameraFace() {
        return MessagesController.getGlobalMainSettings().getBoolean("stories_camera", false);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            if (recordControl != null) {
                recordControl.updateGalleryImage();
            }
            if (lastGallerySelectedAlbum != null && MediaController.allMediaAlbums != null) {
                for (int a = 0; a < MediaController.allMediaAlbums.size(); a++) {
                    MediaController.AlbumEntry entry = MediaController.allMediaAlbums.get(a);
                    if (entry.bucketId == lastGallerySelectedAlbum.bucketId && entry.videoOnly == lastGallerySelectedAlbum.videoOnly) {
                        lastGallerySelectedAlbum = entry;
                        break;
                    }
                }
            }
        } else if (id == NotificationCenter.storiesDraftsUpdated) {
            if (recordControl != null && !showSavedDraftHint) {
                recordControl.updateGalleryImage();
            }
        } else if (id == NotificationCenter.storiesLimitUpdate && currentPage == PAGE_PREVIEW) {
            previewButtons.setShareEnabled(!videoError && !captionEdit.isCaptionOverLimit() && (
                    !MessagesController.getInstance(currentAccount).getStoriesController().hasStoryLimit() || (outputEntry != null && (
                            outputEntry.isEdit || outputEntry.botId != 0))));
        }
    }

    private void addNotificationObservers() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesDraftsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesLimitUpdate);
    }

    private void removeNotificationObservers() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesDraftsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesLimitUpdate);
    }

    private boolean isBackgroundVisible;
    private boolean forceBackgroundVisible;

    public void checkBackgroundVisibility() {
        checkBackgroundVisibility(forceBackgroundVisible);
    }

    public void checkBackgroundVisibility(boolean forceBackgroundVisible) {
        this.forceBackgroundVisible = forceBackgroundVisible;
        boolean shouldBeVisible = dismissProgress != 0 || openProgress < 1 || forceBackgroundVisible;
        if (shouldBeVisible == isBackgroundVisible) {
            return;
        }
        if (activity instanceof LaunchActivity) {
            LaunchActivity launchActivity = (LaunchActivity) activity;
            launchActivity.drawerLayoutContainer.setAllowDrawContent(shouldBeVisible);
        }
        isBackgroundVisible = shouldBeVisible;
    }

    private CharSequence premiumText(String text) {
        return AndroidUtilities.replaceSingleTag(text, Theme.key_chat_messageLinkIn, 0, this::openPremium, resourcesProvider);
    }

    private void openPremium() {
        if (previewView != null) {
            previewView.updatePauseReason(4, true);
        }
        if (captionEdit != null) {
            captionEdit.hidePeriodPopup();
        }
        delegate.openPremium();
    }

    private void showPremiumPeriodBulletin(int period) {
        final int hours = period / 3600;

        Bulletin.BulletinWindow.BulletinWindowLayout window = Bulletin.BulletinWindow.make(activity, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return 0;
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });
        WindowManager.LayoutParams params = window.getLayout();
        if (params != null) {
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.width = containerView.getWidth();
            params.y = (int) (containerView.getY() + AndroidUtilities.dp(56));
            window.updateLayout();
        }
        window.setTouchable(true);
        BulletinFactory.of(window, resourcesProvider)
                .createSimpleBulletin(R.raw.fire_on, premiumText(LocaleController.formatPluralString("StoryPeriodPremium", hours)), 3)
                .show(true);
    }

    public void setIconMuted(boolean muted, boolean animated) {
        if (muteButtonDrawable == null) {
            muteButtonDrawable = new RLottieDrawable(R.raw.media_mute_unmute, "media_mute_unmute", AndroidUtilities.dp(28), AndroidUtilities.dp(28),
                    true, null);
            muteButtonDrawable.multiplySpeed(1.5f);
        }
        muteButton.setAnimation(muteButtonDrawable);
        if (!animated) {
            muteButtonDrawable.setCurrentFrame(muted ? 20 : 0, false);
            return;
        }
        if (muted) {
            if (muteButtonDrawable.getCurrentFrame() > 20) {
                muteButtonDrawable.setCurrentFrame(0, false);
            }
            muteButtonDrawable.setCustomEndFrame(20);
            muteButtonDrawable.start();
        } else {
            if (muteButtonDrawable.getCurrentFrame() == 0 || muteButtonDrawable.getCurrentFrame() >= 43) {
                return;
            }
            muteButtonDrawable.setCustomEndFrame(43);
            muteButtonDrawable.start();
        }
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
            }));
        }
        return thanosEffect;
    }

    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;

    public ImageView getThemeButton() {
        if (themeButton == null) {
            themeButtonDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, dp(28), dp(28), true, null);
            themeButtonDrawable.setPlayInDirectionOfCustomEndFrame(true);
            if (!(outputEntry != null && outputEntry.isDark)) {
                themeButtonDrawable.setCustomEndFrame(0);
                themeButtonDrawable.setCurrentFrame(0);
            } else {
                themeButtonDrawable.setCurrentFrame(35);
                themeButtonDrawable.setCustomEndFrame(36);
            }
            themeButtonDrawable.beginApplyLayerColors();
            int color = Theme.getColor(Theme.key_chats_menuName, resourcesProvider);
            themeButtonDrawable.setLayerColor("Sunny.**", color);
            themeButtonDrawable.setLayerColor("Path 6.**", color);
            themeButtonDrawable.setLayerColor("Path.**", color);
            themeButtonDrawable.setLayerColor("Path 5.**", color);
            themeButtonDrawable.commitApplyLayerColors();
            themeButton = new ImageView(getContext());
            themeButton.setScaleType(ImageView.ScaleType.CENTER);
            themeButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            themeButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
            themeButton.setOnClickListener(e -> {
                toggleTheme();
            });
//            themeButton.setOnLongClickListener(e -> {
//                openThemeSheet();
//                return true;
//            });
            themeButton.setVisibility(View.GONE);
            themeButton.setImageDrawable(themeButtonDrawable);
            themeButton.setAlpha(0f);
            actionBarButtons.addView(themeButton, 0, LayoutHelper.createLinear(46, 56, Gravity.TOP | Gravity.RIGHT));
        }
        return themeButton;
    }

    public void updateThemeButtonDrawable(boolean animated) {
        if (themeButtonDrawable != null) {
            if (animated) {
                themeButtonDrawable.setCustomEndFrame(outputEntry != null && outputEntry.isDark ? themeButtonDrawable.getFramesCount() : 0);
                if (themeButtonDrawable != null) {
                    themeButtonDrawable.start();
                }
            } else {
                int frame = outputEntry != null && outputEntry.isDark ? themeButtonDrawable.getFramesCount() - 1 : 0;
                themeButtonDrawable.setCurrentFrame(frame, false, true);
                themeButtonDrawable.setCustomEndFrame(frame);
                if (themeButton != null) {
                    themeButton.invalidate();
                }
            }
        }
    }

    public void toggleTheme() {
        if (outputEntry == null || changeDayNightView != null || themeButton == null
                || changeDayNightViewAnimator != null && changeDayNightViewAnimator.isRunning()) {
            return;
        }
        final boolean isDark = outputEntry.isDark;

        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        themeButton.setAlpha(0f);
        if (previewView != null) {
            previewView.drawForThemeToggle = true;
        }
        if (paintView != null) {
            paintView.drawForThemeToggle = true;
        }
        draw(bitmapCanvas);
        if (previewView != null) {
            previewView.drawForThemeToggle = false;
        }
        if (paintView != null) {
            paintView.drawForThemeToggle = false;
        }
        themeButton.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        themeButton.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + themeButton.getMeasuredWidth() / 2f;
        float cy = y + themeButton.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) + AndroidUtilities.navigationBarHeight;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                themeButton.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                if (changeDayNightView != null) {
                    changeDayNightView.invalidate();
                }
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                }
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setStartDelay(80);
        changeDayNightViewAnimator.setDuration(isDark ? 320 : 450);
        changeDayNightViewAnimator.setInterpolator(isDark ? CubicBezierInterpolator.EASE_IN : CubicBezierInterpolator.EASE_OUT_QUINT);
        changeDayNightViewAnimator.start();

        addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            if (outputEntry == null) {
                return;
            }
            outputEntry.isDark = !outputEntry.isDark;
            if (previewView != null) {
                previewView.setupWallpaper(outputEntry, false);
            }
            if (paintView != null && paintView.entitiesView != null) {
                for (int i = 0; i < paintView.entitiesView.getChildCount(); ++i) {
                    View child = paintView.entitiesView.getChildAt(i);
                    if (child instanceof MessageEntityView) {
                        ((MessageEntityView) child).setupTheme(outputEntry);
                    }
                }
            }
            updateThemeButtonDrawable(true);
        });
    }

    public void updatePauseReason(int reasonId, boolean pause) {
        if (previewView != null) {
            previewView.updatePauseReason(reasonId, pause);
        }
    }

    public void setDialogId(long dialogId) {
        this.selectedDialogId = dialogId;
        if (captionEdit != null) {
            captionEdit.setDialogId(dialogId);
        }
    }

    public boolean hasCollages() {
        return collageLayoutView.hasLayout();
    }

    public void setActionBarButtonVisible(View view, boolean visible, boolean animated) {
        if (view == null) {
            return;
        }
        if (animated) {
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(visible ? 1.0f : 0.0f)
                    .setUpdateListener(animation -> updateActionBarButtonsOffsets())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            updateActionBarButtonsOffsets();
                            if (!visible) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    })
                    .setDuration(320)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            view.animate().cancel();
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            view.setAlpha(visible ? 1.0f : 0.0f);
            updateActionBarButtonsOffsets();
        }
    }

    private boolean inCheck() {
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        return !animatedRecording && collageProgress >= 1.0f;
    }

    private void updateActionBarButtons(boolean animated) {
        showVideoTimer(currentPage == PAGE_CAMERA && isVideo && !collageListView.isVisible() && !inCheck(), animated);
        collageButton.setSelected(collageLayoutView.hasLayout());
        setActionBarButtonVisible(backButton, collageListView == null || !collageListView.isVisible(), animated);
        setActionBarButtonVisible(flashButton,
                !animatedRecording && currentPage == PAGE_CAMERA && flashButtonMode != null && !collageListView.isVisible() && !inCheck(), animated);
        setActionBarButtonVisible(dualButton,
                !animatedRecording && currentPage == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() && !collageListView.isVisible()
                        && !collageLayoutView.hasLayout(), animated);
        setActionBarButtonVisible(collageButton, currentPage == PAGE_CAMERA && !collageListView.isVisible(), animated);
        setActionBarButtonVisible(collageRemoveButton, collageListView.isVisible(), animated);
        final float collageProgress = collageLayoutView.hasLayout() ? collageLayoutView.getFilledProgress() : 0.0f;
        recordControl.setCollageProgress(collageProgress, animated);
        removeCollageHint.show(collageListView.isVisible());
        animateRecording(animatedRecording, animated);
    }

    private void updateActionBarButtonsOffsets() {
        float right = 0;
        collageRemoveButton.setTranslationX(-right);
        right += dp(46) * collageRemoveButton.getAlpha();
        dualButton.setTranslationX(-right);
        right += dp(46) * dualButton.getAlpha();
        collageButton.setTranslationX(-right);
        right += dp(46) * collageButton.getAlpha();
        flashButton.setTranslationX(-right);
        right += dp(46) * flashButton.getAlpha();

        float left = 0;
        backButton.setTranslationX(left);
        left += dp(46) * backButton.getAlpha();

        collageListView.setBounds(left + dp(8), right + dp(8));
    }

    private static void log(String message) {
        Log.i("kirillNay", message);
    }
}
