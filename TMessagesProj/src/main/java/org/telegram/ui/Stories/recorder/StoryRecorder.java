package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.touchSlop;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.exifinterface.media.ExifInterface;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.GestureDetector2;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoFilterBlurControl;
import org.telegram.ui.Components.PhotoFilterCurvesControl;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.VideoEditTextureView;
import org.telegram.ui.Components.VideoTimelinePlayView;
import org.telegram.ui.Components.ZoomControlView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.DialogStoriesCell;
import org.telegram.ui.Stories.PeerStoriesView;
import org.telegram.ui.Stories.StoryViewer;
import org.telegram.ui.Stories.StoryWaveEffectView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StoryRecorder implements NotificationCenter.NotificationCenterDelegate {

    private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();

    private Activity activity;
    private int currentAccount;

    private boolean isShown;
    private boolean prepareClosing;

    WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;
    private WindowView windowView;
    private ContainerView containerView;

    private static StoryRecorder instance;
    private boolean wasSend;
    private ClosingViewProvider closingSourceProvider;

    public static StoryRecorder getInstance(Activity activity, int currentAccount) {
        if (instance != null && (instance.activity != activity || instance.currentAccount != currentAccount)) {
            instance.close(false);
            instance = null;
        }
        if (instance == null) {
            instance = new StoryRecorder(activity, currentAccount);
        }
        return instance;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.close(false);
        }
        instance = null;
    }

    public static boolean isVisible() {
        return instance != null && instance.isShown;
    }
    public StoryRecorder(Activity activity, int currentAccount) {
        this.activity = activity;
        this.currentAccount = currentAccount;

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowLayoutParams.flags = (
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        initViews();
    }

    private ValueAnimator openCloseAnimator;
    private SourceView fromSourceView;
    private float fromRounding;
    private RectF fromRect = new RectF();
    private float openProgress;
    private int openType;
    private float dismissProgress;
    private Float frozenDismissProgress;

    public static class SourceView {

        int type = 0;
        float rounding;
        RectF screenRect = new RectF();
        ImageReceiver backgroundImageReceiver;
        Paint backgroundPaint;
        Drawable iconDrawable;
        int iconSize;
        View view;

        protected void show() {}
        protected void hide() {}
        protected void drawAbove(Canvas canvas, float alpha) {}

        public static SourceView fromStoryViewer(StoryViewer storyViewer) {
            if (storyViewer == null) {
                return null;
            }
            SourceView src = new SourceView() {
                @Override
                protected void show() {
                    final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
                    if (peerView != null) {
                        peerView.animateOut(false);
                    }
                    if (view != null) {
                        view.setTranslationX(0);
                        view.setTranslationY(0);
                    }
                }

                @Override
                protected void hide() {
                    final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
                    if (peerView != null) {
                        peerView.animateOut(true);
                    }
                }
            };
            if (!storyViewer.getStoryRect(src.screenRect)) {
                return null;
            }
            src.type = 1;
            src.rounding = dp(8);
            final PeerStoriesView peerView = storyViewer.getCurrentPeerView();
            if (peerView != null) {
                src.view = peerView.storyContainer;
            }
            return src;
        }

        public static SourceView fromFloatingButton(FrameLayout floatingButton) {
            if (floatingButton == null) {
                return null;
            }
            SourceView src = new SourceView() {
                @Override
                protected void show() {
                    floatingButton.setVisibility(View.VISIBLE);
                }
                @Override
                protected void hide() {
                    floatingButton.post(() -> {
                        floatingButton.setVisibility(View.GONE);
                    });
                }
            };
            int[] loc = new int[2];
            final View imageView = floatingButton.getChildAt(0);
            imageView.getLocationOnScreen(loc);
            src.screenRect.set(loc[0], loc[1], loc[0] + imageView.getWidth(), loc[1] + imageView.getHeight());
            src.backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            src.backgroundPaint.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            src.iconDrawable = floatingButton.getContext().getResources().getDrawable(R.drawable.story_camera).mutate();
            src.iconSize = AndroidUtilities.dp(56);
            src.rounding = Math.max(src.screenRect.width(), src.screenRect.height()) / 2f;
            return src;
        }

        public static SourceView fromStoryCell(DialogStoriesCell.StoryCell storyCell) {
            if (storyCell == null || storyCell.getRootView() == null) {
                return null;
            }
            final float size = storyCell.avatarImage.getImageWidth();
            final float radius = size / 2f;
            SourceView src = new SourceView() {
                @Override
                protected void show() {
                    storyCell.drawAvatar = true;
                    storyCell.invalidate();
                }

                @Override
                protected void hide() {
                    storyCell.post(() -> {
                        storyCell.drawAvatar = false;
                        storyCell.invalidate();
                    });
                }

                @Override
                protected void drawAbove(Canvas canvas, float alpha) {
                    storyCell.drawPlus(canvas, radius, radius, (float) Math.pow(alpha, 16));
                }
            };
            final int[] loc = new int[2];
            final float[] locPositon = new float[2];
            storyCell.getRootView().getLocationOnScreen(loc);
            AndroidUtilities.getViewPositionInParent(storyCell, (ViewGroup) storyCell.getRootView(), locPositon);
            final float x = loc[0] + locPositon[0] + storyCell.avatarImage.getImageX();
            final float y = loc[1] + locPositon[1] + storyCell.avatarImage.getImageY();

            src.screenRect.set(x, y, x + size, y + size);
            src.backgroundImageReceiver = storyCell.avatarImage;
            src.rounding = Math.max(src.screenRect.width(), src.screenRect.height()) / 2f;
            return src;
        }
    }

    public StoryRecorder closeToWhenSent(ClosingViewProvider closingSourceProvider) {
        this.closingSourceProvider = closingSourceProvider;
        return this;
    }

    public void open(SourceView sourceView) {
        open(sourceView, true);
    }

    public void open(SourceView sourceView, boolean animated) {
        if (isShown) {
            return;
        }

        prepareClosing = false;
//        privacySelectorHintOpened = false;
        forceBackgroundVisible = false;

        if (windowManager != null && windowView != null && windowView.getParent() == null) {
            windowManager.addView(windowView, windowLayoutParams);
        }

        cameraViewThumb.setImageDrawable(getCameraThumb());

        navigateTo(PAGE_CAMERA, false);
        switchToEditMode(EDIT_MODE_NONE, false);

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }
        containerView.updateBackground();
        previewContainer.setBackgroundColor(openType == 1 ? 0 : 0xff1f1f1f);

        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        containerView.setTranslationY2(0);
        containerView.setScaleX(1f);
        containerView.setScaleY(1f);
        dismissProgress = 0;

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        animateOpenTo(1, animated, this::onOpenDone);

        addNotificationObservers();
    }

    public void openEdit(SourceView sourceView, StoryEntry entry, long time, boolean animated) {
        if (isShown) {
            return;
        }

        prepareClosing = false;
//        privacySelectorHintOpened = false;
        forceBackgroundVisible = false;

        if (windowManager != null && windowView != null && windowView.getParent() == null) {
            windowManager.addView(windowView, windowLayoutParams);
        }

        outputEntry = entry;
        isVideo = outputEntry != null && outputEntry.isVideo;

        if (sourceView != null) {
            fromSourceView = sourceView;
            openType = sourceView.type;
            fromRect.set(sourceView.screenRect);
            fromRounding = sourceView.rounding;
            fromSourceView.hide();
        } else {
            openType = 0;
            fromRect.set(0, dp(100), AndroidUtilities.displaySize.x, dp(100) + AndroidUtilities.displaySize.y);
            fromRounding = dp(8);
        }
        containerView.updateBackground();
        previewContainer.setBackgroundColor(openType == 1 ? 0 : 0xff1f1f1f);

        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        containerView.setTranslationY2(0);
        containerView.setScaleX(1f);
        containerView.setScaleY(1f);
        dismissProgress = 0;

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        navigateToPreviewWithPlayerAwait(() -> {
            animateOpenTo(1, animated, this::onOpenDone);
            previewButtons.appear(true, true);
        }, time);
        navigateTo(PAGE_PREVIEW, false);
        switchToEditMode(EDIT_MODE_NONE, false);

        if (outputEntry != null) {
            captionEdit.setText(outputEntry.caption);
        }

        addNotificationObservers();
    }

    public void close(boolean animated) {
        if (!isShown) {
            return;
        }

        if (privacySheet != null) {
            privacySheet.dismiss();
            privacySheet = null;
        }

        if (outputEntry != null) {
            if (wasSend && outputEntry.isEdit) {
                outputEntry.editedMedia = false;
            }
            outputEntry.destroy(false);
            outputEntry = null;
        }

        if (onClosePrepareListener != null && previewView != null) {
            if (prepareClosing) {
                return;
            }
            prepareClosing = true;
            onClosePrepareListener.run(previewView.release(), () -> {
                onClosePrepareListener = null;
                prepareClosing = false;
                close(animated);
            }, wasSend);
            return;
        }

        if (previewView != null && !animated) {
            previewView.set(null);
        }

        animateOpenTo(0, animated, this::onCloseDone);
        if (openType == 1) {
            windowView.setBackgroundColor(0x00000000);
            previewButtons.appear(false, true);
        }

        removeNotificationObservers();
    }

    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private StoryWaveEffectView waveEffect;

    private void animateOpenTo(final float value, boolean animated, Runnable onDone) {
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        if (animated) {
            notificationsLocker.lock();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            frozenDismissProgress = dismissProgress;
            openCloseAnimator = ValueAnimator.ofFloat(openProgress, value);
            openCloseAnimator.addUpdateListener(anm -> {
                openProgress = (float) anm.getAnimatedValue();
                checkBackgroundVisibility();
                containerView.invalidate();
                windowView.invalidate();
                if (openProgress < .3f && waveEffect != null) {
                    waveEffect.start();
                    waveEffect = null;
                }
            });
            openCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    frozenDismissProgress = null;
                    openProgress = value;
                    containerView.invalidate();
                    windowView.invalidate();
                    if (onDone != null) {
                        onDone.run();
                    }
                    if (fromSourceView != null && waveEffect != null) {
                        waveEffect.start();
                        waveEffect = null;
                    }
                    notificationsLocker.unlock();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    NotificationCenter.getGlobalInstance().runDelayedNotifications();
                    checkBackgroundVisibility();
                }
            });
            if (value < 1 && wasSend) {
                openCloseAnimator.setDuration(250);
                openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                if (value > 0 || containerView.getTranslationY1() < AndroidUtilities.dp(20)) {
                    openCloseAnimator.setDuration(270L);
                    openCloseAnimator.setInterpolator(new FastOutSlowInInterpolator());
                } else {
                    openCloseAnimator.setDuration(400L);
                    openCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                }
            }
            openCloseAnimator.start();
        } else {
            frozenDismissProgress = null;
            openProgress = value;
            containerView.invalidate();
            windowView.invalidate();
            if (onDone != null) {
                onDone.run();
            }
            checkBackgroundVisibility();
        }
    }

    private void onOpenDone() {
        isShown = true;
        wasSend = false;
        if (openType == 1) {
            previewContainer.setAlpha(1f);
            previewContainer.setTranslationX(0);
            previewContainer.setTranslationY(0);
            actionBarContainer.setAlpha(1f);
            controlContainer.setAlpha(1f);
            windowView.setBackgroundColor(0xff000000);
        }

        if (whenOpenDone != null) {
            whenOpenDone.run();
            whenOpenDone = null;
        } else {
            onResumeInternal();
        }
    }

    private void onCloseDone() {
        isShown = false;
        AndroidUtilities.unlockOrientation(activity);
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
            } catch (Exception ignore) {}
        }
        outputFile = null;
        AndroidUtilities.runOnUIThread(() -> {
            if (windowManager != null && windowView != null && windowView.getParent() != null) {
                windowManager.removeView(windowView);
            }
        }, 16);
        if (fromSourceView != null) {
            fromSourceView.show();
        }
        if (whenOpenDone != null) {
            whenOpenDone = null;
        }
        lastGalleryScrollPosition = null;
        if (instance != null) {
            instance.close(false);
        }
        instance = null;

        if (onCloseListener != null) {
            onCloseListener.run();
            onCloseListener = null;
        }
        if (windowView != null) {
            Bulletin.removeDelegate(windowView);
        }
    }

    private Runnable onCloseListener;
    public void setOnCloseListener(Runnable listener) {
        onCloseListener = listener;
    }

    private Utilities.Callback3<Long, Runnable, Boolean> onClosePrepareListener;
    public void setOnPrepareCloseListener(Utilities.Callback3<Long, Runnable, Boolean> listener) {
        onClosePrepareListener = listener;
    }

    private int previewW, previewH;
    private int underControls;
    private boolean underStatusBar;
    private boolean scrollingY, scrollingX;

    private int insetLeft, insetTop, insetRight, insetBottom;

    public class WindowView extends SizeNotifierFrameLayout {

        private GestureDetectorFixDoubleTap gestureDetector;
        private ScaleGestureDetector scaleGestureDetector;

        public WindowView(Context context) {
            super(context);
            gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());
            scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        }

        private RectF rectF = new RectF(), fullRectF = new RectF();
        private Path clipPath = new Path();
        private Rect rect = new Rect();
        private int lastKeyboardHeight;

        public int getBottomPadding(boolean withUnderControls) {
            return getHeight() - containerView.getBottom() + (withUnderControls ? underControls : 0);
        }

        public int getPaddingUnderContainer() {
            return getHeight() - insetBottom - containerView.getBottom();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float dismiss = frozenDismissProgress != null ? frozenDismissProgress : dismissProgress;
            if (openType == 0) {
                canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * openProgress * (1f - dismiss))));
            }
            boolean restore = false;
            final float r = AndroidUtilities.lerp(fromRounding, 0, openProgress);
            if (openProgress != 1) {
                if (openType == 0) {
                    fullRectF.set(0, 0, getWidth(), getHeight());
                    fullRectF.offset(containerView.getTranslationX(), containerView.getTranslationY());
                    AndroidUtilities.lerp(fromRect, fullRectF, openProgress, rectF);

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
                    fullRectF.set(previewContainer.getLeft(), previewContainer.getTop(), previewContainer.getMeasuredWidth(), previewContainer.getMeasuredHeight());
                    fullRectF.offset(containerView.getX(), containerView.getY());
                    AndroidUtilities.lerp(fromRect, fullRectF, openProgress, rectF);
                    previewContainer.setAlpha(openProgress);
                    previewContainer.setTranslationX(rectF.left - previewContainer.getLeft() - containerView.getX());
                    previewContainer.setTranslationY(rectF.top - previewContainer.getTop() - containerView.getY());
                    if (fromSourceView != null && fromSourceView.view != null) {
                        fromSourceView.view.setTranslationX((fullRectF.left - fromRect.left) * openProgress);
                        fromSourceView.view.setTranslationY((fullRectF.top - fromRect.top) * openProgress);
                    }
                    previewContainer.setScaleX(rectF.width() / previewContainer.getMeasuredWidth());
                    previewContainer.setScaleY(rectF.height() / previewContainer.getMeasuredHeight());
                    actionBarContainer.setAlpha(openProgress);
                    controlContainer.setAlpha(openProgress);
                    captionContainer.setAlpha(openProgress);
                }
            }
            super.dispatchDraw(canvas);
            if (restore) {
                canvas.restore();
                canvas.restore();

                if (fromSourceView != null) {
                    final float alpha = Utilities.clamp(1f - openProgress * 1.5f, 1, 0);
                    final float bcx = rectF.centerX(),
                            bcy = rectF.centerY(),
                            br = Math.min(rectF.width(), rectF.height()) / 2f;
                    if (fromSourceView.backgroundImageReceiver != null) {
                        fromSourceView.backgroundImageReceiver.setImageCoords(rectF);
                        fromSourceView.backgroundImageReceiver.setAlpha(alpha);
                        fromSourceView.backgroundImageReceiver.draw(canvas);
                    } else if (fromSourceView.backgroundPaint != null) {
                        fromSourceView.backgroundPaint.setShadowLayer(dp(2), 0, dp(3), Theme.multAlpha(0x33000000, alpha));
                        fromSourceView.backgroundPaint.setAlpha((int) (0xFF * alpha));
                        canvas.drawRoundRect(rectF, r, r, fromSourceView.backgroundPaint);
                    }
                    if (fromSourceView.iconDrawable != null) {
                        rect.set(fromSourceView.iconDrawable.getBounds());
                        fromSourceView.iconDrawable.setBounds(
                            (int) (bcx - fromSourceView.iconSize / 2),
                            (int) (bcy - fromSourceView.iconSize / 2),
                            (int) (bcx + fromSourceView.iconSize / 2),
                            (int) (bcy + fromSourceView.iconSize / 2)
                        );
                        int wasAlpha = fromSourceView.iconDrawable.getAlpha();
                        fromSourceView.iconDrawable.setAlpha((int) (wasAlpha * alpha));
                        fromSourceView.iconDrawable.draw(canvas);
                        fromSourceView.iconDrawable.setBounds(rect);
                        fromSourceView.iconDrawable.setAlpha(wasAlpha);
                    }

                    canvas.save();
                    canvas.translate(fromRect.left, fromRect.top);
                    fromSourceView.drawAbove(canvas, alpha);
                    canvas.restore();
                }
            }
        }

        private boolean flingDetected;

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            flingDetected = false;
            scaleGestureDetector.onTouchEvent(ev);
            gestureDetector.onTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_UP && !flingDetected) {
                allowModeScroll = true;
                if (containerView.getTranslationY() > 0) {
                    if (dismissProgress > .4f) {
                        close(true);
                    } else {
                        animateContainerBack();
                    }
                } else if (galleryListView != null && galleryListView.getTranslationY() > 0) {
                    animateGalleryListView(!takingVideo && galleryListView.getTranslationY() < galleryListView.getPadding());
                }
                modeSwitcherView.stopScroll(0);
                scrollingY = false;
                scrollingX = false;
            }
            return super.dispatchTouchEvent(ev);
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
                if (!scaling || cameraView == null || currentPage != PAGE_CAMERA || cameraView.isDualTouch()) {
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
        private boolean allowModeScroll = true;

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
            public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (openCloseAnimator != null && openCloseAnimator.isRunning() || galleryOpenCloseSpringAnimator != null || galleryOpenCloseAnimator != null || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch()) {
                    return false;
                }
                if (takingVideo || takingPhoto || currentPage != PAGE_CAMERA) {
                    return false;
                }
                if (!scrollingX) {
                    sty += distanceY;
                    if (!scrollingY && Math.abs(sty) >= touchSlop) {
                        scrollingY = true;
                    }
                }
                if (scrollingY) {
                    int galleryMax = windowView.getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
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
                        scrollingX = true;
                    }
                }
                if (scrollingX) {
                    modeSwitcherView.scrollX(distanceX);
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {

            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (openCloseAnimator != null && openCloseAnimator.isRunning() || recordControl.isTouch() || cameraView != null && cameraView.isDualTouch() || scaling || zoomControlView != null && zoomControlView.isTouch()) {
                    return false;
                }
                flingDetected = true;
                allowModeScroll = true;
                boolean r = false;
                if (scrollingY) {
                    if (Math.abs(containerView.getTranslationY1()) >= dp(1)) {
                        if (velocityY > 0 && Math.abs(velocityY) > 2000 && Math.abs(velocityY) > Math.abs(velocityX) || dismissProgress > .4f) {
                            close(true);
                        } else {
                            animateContainerBack();
                        }
                        r = true;
                    } else if (galleryListView != null) {
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
                scrollingY = false;
                scrollingX = false;
                return r;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (cameraView != null) {
                    cameraView.allowToTapFocus();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (cameraView == null || awaitingPlayer || takingPhoto || !cameraView.isInited() || currentPage != PAGE_CAMERA) {
                    return false;
                }
                cameraView.switchCamera();
                recordControl.rotateFlip(180);
                saveCameraFace(cameraView.isFrontface());
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
                return currentPage == PAGE_CAMERA && cameraView != null && !awaitingPlayer && cameraView.isInited() && !takingPhoto && !recordControl.isTouch() && !isGalleryOpen() && galleryListViewOpening == null;
            }
        };

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

            if (paintView != null && paintView.emojiView != null) {
                paintView.emojiView.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(paintView.emojiView.getLayoutParams().height, MeasureSpec.EXACTLY)
                );
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
                if (openType == 1 && fromRect.top + previewH + underControls < H - insetBottom) {
                    t = (int) fromRect.top;
                } else if (t - T < dp(40)) {
                    t = T;
                }
                b = t + previewH + underControls;
            }

            containerView.layout(l, t, r, b);

            if (galleryListView != null) {
                galleryListView.layout((W - galleryListView.getMeasuredWidth()) / 2, 0, (W + galleryListView.getMeasuredWidth()) / 2, H);
            }

            if (captionEdit != null) {
                EmojiView emojiView = captionEdit.editText.getEmojiView();
                if (emojiView != null) {
                    emojiView.layout(insetLeft, H - insetBottom - emojiView.getMeasuredHeight(), W - insetRight, H - insetBottom);
                }
            }

            if (paintView != null && paintView.emojiView != null) {
                paintView.emojiView.layout(insetLeft, H - insetBottom - paintView.emojiView.getMeasuredHeight(), W - insetRight, H - insetBottom);
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
            final float scale = (float) bitmap.getWidth() / windowView.getWidth();
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
                } catch (Exception ignore) {}
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
            windowView.invalidate();

            final float scale = 1f - .1f * Utilities.clamp(getTranslationY() / AndroidUtilities.dp(320), 1, 0);
            setScaleX(scale);
            setScaleY(scale);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int t = underStatusBar ? insetTop : 0;

            previewContainer.layout(0, 0, previewW, previewH);
            previewContainer.setPivotX(previewW * .5f);
            actionBarContainer.layout(0, t, previewW, t + actionBarContainer.getMeasuredHeight());
            controlContainer.layout(0, previewH - controlContainer.getMeasuredHeight(), previewW, previewH);
            navbarContainer.layout(0, previewH, previewW, previewH + navbarContainer.getMeasuredHeight());
            captionContainer.layout(0, 0, previewW, previewH);

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

            final int w = right - left;
            final int h = bottom - top;

            setPivotX((right - left) / 2f);
            setPivotY(-h * .2f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int W = MeasureSpec.getSize(widthMeasureSpec);
            final int H = MeasureSpec.getSize(heightMeasureSpec);

            measureChildExactly(previewContainer, previewW, previewH);
            measureChildExactly(actionBarContainer, previewW, dp(56 + 56 + 38));
            measureChildExactly(controlContainer, previewW, dp(220));
            measureChildExactly(navbarContainer, previewW, underControls);
            measureChildExactly(captionContainer, previewW, previewH);
            if (captionEdit.mentionContainer != null) {
                measureChildExactly(captionEdit.mentionContainer, previewW, previewH);
            }

            if (photoFilterView != null) {
                measureChildExactly(photoFilterView, W, H);
            }
            if (paintView != null) {
                measureChildExactly(paintView, W, H);
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
                    topGradient = new LinearGradient(0, top, 0, top + dp(72), new int[] {0x40000000, 0x00000000}, new float[] { top / (top + dp(72)), 1 }, Shader.TileMode.CLAMP );
                    topGradientPaint.setShader(topGradient);
                }
                topGradientPaint.setAlpha((int) (0xFF * openProgress));
                canvas.drawRect(0, 0, getWidth(), dp(72) + top, topGradientPaint);
            }
            return r;
        }
    }

    public static final int PAGE_CAMERA = 0;
    public static final int PAGE_PREVIEW = 1;
    private int currentPage = PAGE_CAMERA;

    public static final int EDIT_MODE_NONE = -1;
    public static final int EDIT_MODE_PAINT = 0;
    public static final int EDIT_MODE_FILTER = 1;
    private int currentEditMode = EDIT_MODE_NONE;

    private FrameLayout previewContainer;
    private FrameLayout actionBarContainer;
    private FrameLayout controlContainer;
    private FrameLayout captionContainer;
    private FrameLayout navbarContainer;

    private ImageView backButton;
    private SimpleTextView titleTextView;
    private StoryPrivacyBottomSheet privacySheet;

    /* PAGE_CAMERA */
    private ImageView cameraViewThumb;
    private DualCameraView cameraView;

    private int flashButtonResId;
    private ImageView flashButton;
    private ToggleButton dualButton;
    private VideoTimerView videoTimerView;
    private boolean wasGalleryOpen;
    private GalleryListView galleryListView;
    private DraftSavedHint draftSavedHint;
    private RecordControl recordControl;
    private PhotoVideoSwitcherView modeSwitcherView;
    private HintTextView hintTextView;
    private ZoomControlView zoomControlView;
    private HintView2 cameraHint;

    /* PAGE_PREVIEW */
    private PreviewView previewView;
    private FrameLayout videoTimelineContainerView;
    private VideoTimelinePlayView videoTimelineView;
    private VideoTimeView videoTimeView;
    private PreviewButtons previewButtons;
    private CaptionContainerView captionEdit;
    private DownloadButton downloadButton;
    private RLottieDrawable muteButtonDrawable;
    private RLottieImageView muteButton;
    private HintView2 muteHint;
    private HintView2 dualHint;
    private HintView2 savedDualHint;
//    private StoryPrivacySelector privacySelector;
//    private boolean privacySelectorHintOpened;
//    private StoryPrivacySelector.StoryPrivacyHint privacySelectorHint;
    private PreviewHighlightView previewHighlight;
    private TrashView trash;

    /* EDIT_MODE_PAINT */
    private Bitmap paintViewBitmap;
    private PaintView paintView;
    private View paintViewRenderView;
    private View paintViewRenderInputView;
    private View paintViewTextDim;
    private View paintViewEntitiesView;
    private View paintViewSelectionContainerView;

    /* EDIT_MODE_FILTER */
    private Bitmap photoFilterBitmap;
    private PhotoFilterView photoFilterView;
    private PhotoFilterView.EnhanceView photoFilterEnhanceView;
    private TextureView photoFilterViewTextureView;
    private PhotoFilterBlurControl photoFilterViewBlurControl;
    private PhotoFilterCurvesControl photoFilterViewCurvesControl;

    private File outputFile;
    private StoryEntry outputEntry;
    private boolean fromGallery;

    private boolean isVideo = false;
    private boolean takingPhoto = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private boolean awaitingPlayer = false;

    private float cameraZoom;

    private int shiftDp = -3;
    private boolean showSavedDraftHint;

    public Context getContext() {
        return activity;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Context context = getContext();

        windowView = new WindowView(context);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        insetTop = r.top;
                        insetBottom = r.bottom;
                        insetLeft = r.left;
                        insetRight = r.right;
                    } else {
                        insetTop = insets.getStableInsetTop();
                        insetBottom = insets.getStableInsetBottom();
                        insetLeft = insets.getStableInsetLeft();
                        insetRight = insets.getStableInsetRight();
                    }
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }
        windowView.setFocusable(true);

        windowView.addView(containerView = new ContainerView(context));

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
                    photoFilterViewCurvesControl.setActualArea(0, 0, photoFilterViewCurvesControl.getMeasuredWidth(), photoFilterViewCurvesControl.getMeasuredHeight());
                }
                if (photoFilterViewBlurControl != null) {
                    photoFilterViewBlurControl.setActualAreaSize(photoFilterViewBlurControl.getMeasuredWidth(), photoFilterViewBlurControl.getMeasuredHeight());
                }
            }
        });
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

        Bulletin.addDelegate(windowView, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return (int) (dp(56) + dp(56 - 10) * muteButton.getAlpha());
            }
        });

        cameraViewThumb = new ImageView(context);
        cameraViewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cameraViewThumb.setOnClickListener(v -> {
            if (noCameraPermission) {
                requestCameraPermission(true);
            }
        });
        cameraViewThumb.setClickable(true);
        previewContainer.addView(cameraViewThumb, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        previewContainer.setBackgroundColor(openType == 1 ? 0 : 0xff1f1f1f);
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
        previewView = new PreviewView(context) {
            @Override
            public boolean additionalTouchEvent(MotionEvent ev) {
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
                if (delete) {
                    deleteCurrentPart();
                }
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
        };
        previewView.setOnTapListener(() -> {
            if (currentEditMode != EDIT_MODE_NONE || currentPage != PAGE_PREVIEW || captionEdit.keyboardShown) {
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
            previewButtons.setShareEnabled(false);
            downloadButton.showFailedVideo();
        });
        previewContainer.addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        previewContainer.addView(photoFilterEnhanceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        captionEdit = new CaptionContainerView(context, currentAccount, windowView, containerView, resourcesProvider) {
            @Override
            protected void drawBlurBitmap(Bitmap bitmap, float amount) {
                windowView.drawBlurBitmap(bitmap, amount);
                super.drawBlurBitmap(bitmap, amount);
            }
        };
        captionEdit.setOnHeightUpdate(height -> {
            if (videoTimelineContainerView != null) {
                videoTimelineContainerView.setTranslationY(-(captionEdit.getEditTextHeight() + AndroidUtilities.dp(12)) + AndroidUtilities.dp(64));
            }
        });
        captionEdit.setOnPeriodUpdate(period -> {
            if (outputEntry != null) {
                outputEntry.period = period;
                MessagesController.getGlobalMainSettings().edit().putInt("story_period", period).apply();
//                privacySelector.setStoryPeriod(period);
            }
        });
        captionEdit.setOnPremiumHint(this::showPremiumPeriodBulletin);
        captionEdit.setOnKeyboardOpen(open -> {
            previewView.updatePauseReason(2, open);
            videoTimelineContainerView.clearAnimation();
            videoTimelineContainerView.animate().alpha(open ? 0f : 1f).setDuration(120).start();
        });

        videoTimelineView = previewView.getTimelineView();
        videoTimelineView.setVisibility(View.GONE);
        videoTimelineView.setAlpha(0f);
        videoTimelineContainerView = new FrameLayout(context);
        videoTimelineContainerView.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 0));
        videoTimeView = new VideoTimeView(context);
        videoTimeView.setVisibility(View.GONE);
        videoTimeView.show(false, false);
        videoTimelineContainerView.addView(videoTimeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 25, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        captionContainer.addView(videoTimelineContainerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58 + 25, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 64));
        captionContainer.addView(captionEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 200, 0, 0));

        backButton = new ImageView(context);
        backButton.setContentDescription(LocaleController.getString("AccDescrGoBack", R.string.AccDescrGoBack));
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

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextSize(20);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        titleTextView.setTextColor(0xffffffff);
        titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleTextView.setText(LocaleController.getString("RecorderNewStory", R.string.RecorderNewStory));
        titleTextView.getPaint().setShadowLayer(dpf2(1), 0, 1, 0x40000000);
        titleTextView.setAlpha(0f);
        titleTextView.setVisibility(View.GONE);
        titleTextView.setEllipsizeByGradient(true);
        titleTextView.setRightPadding(AndroidUtilities.dp(96));
        actionBarContainer.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL, 71, 0, 0, 0));

        downloadButton = new DownloadButton(context, done -> {
            applyPaint(true);
            applyFilter(done);
        }, currentAccount, windowView, resourcesProvider);
        actionBarContainer.addView(downloadButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        muteHint = new HintView2(activity, HintView2.DIRECTION_TOP)
            .setJoint(1, -68)
            .setDuration(2000)
            .setBounce(false)
            .setAnimatedTextHacks(true, true, false);
        muteHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(muteHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

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
            muteHint.setText(outputEntry.muted ? LocaleController.getString("StorySoundMuted") : LocaleController.getString("StorySoundNotMuted"), muteHint.shown());
            muteHint.show();
            setIconMuted(outputEntry.muted, true);
            previewView.mute(outputEntry.muted);
        });
        muteButton.setVisibility(View.GONE);
        muteButton.setAlpha(0f);
        actionBarContainer.addView(muteButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT, 0, 0, 48, 0));

        flashButton = new ImageView(context);
        flashButton.setScaleType(ImageView.ScaleType.CENTER);
        flashButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setOnClickListener(e -> {
            if (cameraView == null || awaitingPlayer) {
                return;
            }
            CameraSession cameraSession = cameraView.getCameraSession();
            if (cameraSession == null) {
                return;
            }
            String current = cameraSession.getCurrentFlashMode();
            String next = cameraSession.getNextFlashMode();
            if (current.equals(next)) {
                return;
            }
            cameraView.getCameraSession().setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });
        flashButton.setVisibility(View.GONE);
        flashButton.setAlpha(0f);
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
        dualButton.setVisibility(DualCameraView.dualAvailableStatic(context) ? View.VISIBLE : View.GONE);
        actionBarContainer.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        dualHint = new HintView2(activity, HintView2.DIRECTION_TOP)
            .setJoint(1, -20)
            .setDuration(5000)
            .setCloseButton(true)
            .setText(LocaleController.getString(R.string.StoryCameraDualHint))
            .setOnHiddenListener(() -> MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) + 1).apply());
        dualHint.setPadding(dp(8), 0, dp(8), 0);
        actionBarContainer.addView(dualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 52, 0, 0));

        savedDualHint = new HintView2(activity, HintView2.DIRECTION_RIGHT)
                .setJoint(0, 56 / 2)
                .setDuration(5000)
                .setMultilineText(true);
        actionBarContainer.addView(savedDualHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 52, 0));

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        actionBarContainer.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 56, 0, 56, 0));

        if (Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }

        recordControl = new RecordControl(context);
        recordControl.setDelegate(recordControlDelegate);
        recordControl.startAsVideo(isVideo);
        controlContainer.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        cameraHint = new HintView2(activity, HintView2.DIRECTION_BOTTOM)
                .setMultilineText(true)
                .setText(LocaleController.getString(R.string.StoryCameraHint2))
                .setMaxWidth(320)
                .setDuration(5000L)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER);
        controlContainer.addView(cameraHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM, 0, 0, 0, 100));

        zoomControlView = new ZoomControlView(context);
        zoomControlView.enabledTouch = false;
        zoomControlView.setAlpha(0.0f);
        controlContainer.addView(zoomControlView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 100 + 8));
        zoomControlView.setDelegate(zoom -> {
            if (cameraView != null) {
                cameraView.setZoom(cameraZoom = zoom);
            }
            showZoomControls(true, true);
        });
        zoomControlView.setZoom(cameraZoom = 0, false);

        modeSwitcherView = new PhotoVideoSwitcherView(context);
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {
            if (takingPhoto || takingVideo) {
                return;
            }

            isVideo = newIsVideo;
            showVideoTimer(isVideo, true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> {
            recordControl.startAsVideoT(t);
        });
        navbarContainer.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        hintTextView = new HintTextView(context);
        navbarContainer.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER, 8, 0, 8, 8));

        previewButtons = new PreviewButtons(context);
        previewButtons.setVisibility(View.GONE);
        previewButtons.setOnClickListener((Integer btn) -> {
            if (outputEntry == null) {
                return;
            }
            captionEdit.clearFocus();
            if (btn == PreviewButtons.BUTTON_SHARE) {
                if (privacySheet != null) {
                    privacySheet.dismiss();
                    privacySheet = null;
                }
                if (!previewButtons.isShareEnabled()) {
                    downloadButton.showFailedVideo();
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    AndroidUtilities.shakeViewSpring(previewButtons.shareButton, shiftDp = -shiftDp);
                    return;
                }
                if (outputEntry.isEdit) {
                    outputEntry.editedPrivacy = false;
                    applyFilter(null);
                    upload(true);
                } else {
                    previewView.updatePauseReason(3, true);
                    privacySheet = new StoryPrivacyBottomSheet(activity, outputEntry.period, resourcesProvider)
                        .setValue(outputEntry.privacy)
                        .whenDismiss(privacy -> {
                            if (outputEntry != null) {
                                outputEntry.privacy = privacy;
                            }
                        })
                        .isEdit(false)
                        .setWarnUsers(getUsersFrom(captionEdit.getText()))
                        .whenSelectedRules((privacy, allowScreenshots, keepInProfile, whenDone) -> {
                            if (outputEntry == null) {
                                return;
                            }
                            previewView.updatePauseReason(5, true);
                            outputEntry.privacy = privacy;
                            StoryPrivacySelector.save(currentAccount, outputEntry.privacy);
                            outputEntry.pinned = keepInProfile;
                            outputEntry.allowScreenshots = allowScreenshots;
                            outputEntry.privacyRules.clear();
                            outputEntry.privacyRules.addAll(privacy.rules);
                            outputEntry.editedPrivacy = true;
                            applyFilter(() -> {
                                whenDone.run();
                                upload(true);
                            });
                        }, false);
                    privacySheet.setOnDismissListener(di -> {
                        previewView.updatePauseReason(3, false);
                        privacySheet = null;
                    });
                    privacySheet.show();
                }
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
        navbarContainer.addView(previewButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL));

        trash = new TrashView(context);
        trash.setAlpha(0f);
        trash.setVisibility(View.GONE);
        previewContainer.addView(trash, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        previewHighlight = new PreviewHighlightView(context, currentAccount, resourcesProvider);
        previewContainer.addView(previewHighlight, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private ArrayList<String> getUsersFrom(CharSequence caption) {
        ArrayList<String> users = new ArrayList<>();
        if (caption instanceof Spanned) {
            URLSpanUserMention[] spans = ((Spanned) caption).getSpans(0, caption.length(), URLSpanUserMention.class);
            for (int i = 0; i < spans.length; ++i) {
                URLSpanUserMention span = spans[i];
                if (span != null) {
                    try {
                        Long userId = Long.parseLong(span.getURL());
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                        if (user != null && UserObject.getPublicUsername(user) != null && !users.contains(user)) {
                            users.add(UserObject.getPublicUsername(user));
                        }
                    } catch (Exception ignore) {}
                }
            }
        }
        if (caption != null) {
            int u = -1;
            for (int i = 0; i < caption.length(); ++i) {
                char c = caption.charAt(i);
                if (c == '@') {
                    u = i + 1;
                } else if (c == ' ') {
                    if (u != -1) {
                        String username = caption.subSequence(u, i).toString();
                        TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(username);
                        if (obj instanceof TLRPC.User && !((TLRPC.User) obj).bot && ((TLRPC.User) obj).id != 777000 && !UserObject.isReplyUser((TLRPC.User) obj) && !users.contains(username)) {
                            users.add(username);
                        }
                    }
                    u = -1;
                }
            }
            if (u != -1) {
                String username = caption.subSequence(u, caption.length()).toString();
                TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(username);
                if (obj instanceof TLRPC.User && !((TLRPC.User) obj).bot && ((TLRPC.User) obj).id != 777000 && !UserObject.isReplyUser((TLRPC.User) obj) && !users.contains(username)) {
                    users.add(username);
                }
            }
        }
        return users;
    }

    private DraftSavedHint getDraftSavedHint() {
        if (draftSavedHint == null) {
            draftSavedHint = new DraftSavedHint(getContext());
            controlContainer.addView(draftSavedHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 66 + 12));
        }
        return draftSavedHint;
    }

    private void upload(boolean asStory) {
        applyPaint(true);
        if (outputEntry == null) {
            close(true);
            return;
        }
        destroyPhotoFilterView();
        prepareThumb(outputEntry, false);
        CharSequence caption = captionEdit.getText();
        outputEntry.editedCaption = !TextUtils.equals(outputEntry.caption, caption);
        outputEntry.caption = caption;
        MessagesController.getInstance(currentAccount).getStoriesController().uploadStory(outputEntry, asStory);
        if (outputEntry.isDraft) {
            MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(outputEntry);
        }
        outputEntry.cancelCheckStickers();
        outputEntry = null;

        wasSend = true;
        forceBackgroundVisible = true;
        checkBackgroundVisibility();

        Runnable runnable = () -> {
            if (asStory) {
                if (fromSourceView != null) {
                    fromSourceView.show();
                    fromSourceView = null;
                }
                fromSourceView = closingSourceProvider != null ? closingSourceProvider.getView() : null;
                if (fromSourceView != null) {
                    openType = fromSourceView.type;
                    containerView.updateBackground();
                    previewContainer.setBackgroundColor(openType == 1 ? 0 : 0xff1f1f1f);
                    fromRect.set(fromSourceView.screenRect);
                    fromRounding = fromSourceView.rounding;
                    fromSourceView.hide();

                    if (waveEffect == null && SharedConfig.getDevicePerformanceClass() > SharedConfig.PERFORMANCE_CLASS_AVERAGE && LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && false) {
                        waveEffect = new StoryWaveEffectView(getContext(), fromSourceView.screenRect.centerX(), fromSourceView.screenRect.centerY(), fromSourceView.screenRect.width() / 2f);
                    }
                }
                closingSourceProvider = null;

                if (activity instanceof LaunchActivity) {
                    ((LaunchActivity) activity).drawerLayoutContainer.post(() -> {
                        if (waveEffect != null) {
                            waveEffect.prepare();
                        }
                        close(true);
                    });
                } else {
                    close(true);
                }
            } else {
                close(true);
            }
        };
        if (closingSourceProvider != null) {
            closingSourceProvider.preLayout(runnable);
        } else {
            runnable.run();
        }
        MessagesController.getGlobalMainSettings().edit().putInt("storyhint2", 2).apply();
    }

    private File prepareThumb(StoryEntry storyEntry, boolean forDraft) {
        if (storyEntry == null || previewView.getWidth() <= 0 || previewView.getHeight() <= 0) {
            return null;
        }
        if (!forDraft && !storyEntry.wouldBeVideo() && !storyEntry.isEdit) {
            return null;
        }
        File file = forDraft ? storyEntry.draftThumbFile : storyEntry.uploadThumbFile;
        if (file != null) {
            file.delete();
            file = null;
        }

        final float scale = forDraft ? 1 / 3f : 1f;
        final int w = (int) (previewView.getWidth() * scale);
        final int h = (int) (previewView.getHeight() * scale);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);

        canvas.save();
        canvas.scale(scale, scale);
        previewView.draw(canvas);
        canvas.restore();

        final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        TextureView textureView = previewView.getTextureView();
        if (storyEntry.isVideo && textureView != null) {
            Bitmap previewTextureView = textureView.getBitmap();
            Matrix matrix = textureView.getTransform(null);
            if (matrix != null) {
                matrix = new Matrix(matrix);
                matrix.postScale(scale, scale);
            }
            canvas.drawBitmap(previewTextureView, matrix, bitmapPaint);
            previewTextureView.recycle();
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
            paintView.entitiesView.draw(canvas);
            canvas.restore();
        }

        Bitmap thumbBitmap = Bitmap.createScaledBitmap(bitmap, 40, 22, true);

        file = StoryEntry.makeCacheFile(currentAccount, false);
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, forDraft ? 95 : 75, new FileOutputStream(file));
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

    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (cameraView != null && cameraView.isDual() || animatedRecording) {
            mode = null;
        }
        if (mode == null) {
            if (animated) {
                flashButton.setVisibility(View.VISIBLE);
                flashButton.animate().alpha(0).withEndAction(() -> {
                    flashButton.setVisibility(View.GONE);
                }).start();
            } else {
                flashButton.setVisibility(View.GONE);
                flashButton.setAlpha(0f);
            }
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(LocaleController.getString("AccDescrCameraFlashOn", R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(LocaleController.getString("AccDescrCameraFlashAuto", R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(LocaleController.getString("AccDescrCameraFlashOff", R.string.AccDescrCameraFlashOff));
                break;
        }
        if (animated && flashButtonResId != resId) {
            AndroidUtilities.updateImageViewImageAnimated(flashButton, flashButtonResId = resId);
        } else {
            flashButton.setImageResource(flashButtonResId = resId);
        }
        flashButton.setVisibility(View.VISIBLE);
        if (animated) {
            flashButton.animate().alpha(1f).start();
        } else {
            flashButton.setAlpha(1f);
        }
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
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, false);
            cameraView.startTakePictureAnimation();
            boolean savedFromTextureView = false;
            if (cameraView.isDual() && TextUtils.equals(cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF)) {
                takingPhoto = true;
                cameraView.pauseAsTakingPicture();
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
                final CameraSession cameraSession = cameraView.getCameraSession();
                takingPhoto = CameraController.getInstance().takePicture(outputFile, true, cameraSession, (orientation) -> {
                    takingPhoto = false;
                    if (outputFile == null) {
                        return;
                    }
                    int rotate = orientation == -1 ? 0 : 90;
                    if (orientation == -1) {
                        int w = -1, h = -1;
                        try {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                            w = opts.outWidth;
                            h = opts.outHeight;
                        } catch (Exception ignore) {}
                        if (w > h) {
                            rotate = 270;
                        }
                    }
                    outputEntry = StoryEntry.fromPhotoShoot(outputFile, rotate);
                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    fromGallery = false;
                    navigateTo(PAGE_PREVIEW, true);
                });
            } else {
                takingPhoto = false;
                outputEntry = StoryEntry.fromPhotoShoot(outputFile, 0);
                StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                fromGallery = false;
                navigateTo(PAGE_PREVIEW, true);
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || stoppingTakingVideo || awaitingPlayer || currentPage != PAGE_CAMERA || cameraView == null || cameraView.getCameraSession() == null) {
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
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, true);
            CameraController.getInstance().recordVideo(cameraView.getCameraSession(), outputFile, false, (thumbPath, duration) -> {
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
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

                outputEntry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
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
            }, () /* onVideoStart */ -> {
                whenStarted.run();

                hintTextView.setText(byLongPress ? LocaleController.getString("StoryHintSwipeToZoom", R.string.StoryHintSwipeToZoom) : LocaleController.getString("StoryHintPinchToZoom", R.string.StoryHintPinchToZoom), false);
                animateRecording(true, true);
                setAwakeLock(true);

                videoTimerView.setRecording(true, true);
                showVideoTimer(true, true);
            }, cameraView, false);

            if (!isVideo) {
                isVideo = true;
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);
            }
        }

        @Override
        public void onVideoRecordLocked() {
            hintTextView.setText(LocaleController.getString("StoryHintPinchToZoom", R.string.StoryHintPinchToZoom), true);
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
            if (currentPage == PAGE_CAMERA && requestGalleryPermission()) {
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
            cameraView.switchCamera();
            saveCameraFace(cameraView.isFrontface());
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
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private AnimatorSet recordingAnimator;
    private boolean animatedRecording;
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
        if (animatedRecording == recording) {
            return;
        }
        if (recordingAnimator != null) {
            recordingAnimator.cancel();
            recordingAnimator = null;
        }
        animatedRecording = recording;
        if (animated) {
            backButton.setVisibility(View.VISIBLE);
            flashButton.setVisibility(View.VISIBLE);
            dualButton.setVisibility(cameraView != null && cameraView.dualAvailable() ? View.VISIBLE : View.GONE);
            recordingAnimator = new AnimatorSet();
            recordingAnimator.playTogether(
                ObjectAnimator.ofFloat(backButton, View.ALPHA, recording ? 0 : 1),
                ObjectAnimator.ofFloat(flashButton, View.ALPHA, recording || currentPage != PAGE_CAMERA ? 0 : 1),
                ObjectAnimator.ofFloat(dualButton, View.ALPHA, recording || currentPage != PAGE_CAMERA || cameraView == null || !cameraView.dualAvailable() ? 0 : 1),
                ObjectAnimator.ofFloat(hintTextView, View.ALPHA, recording && currentPage == PAGE_CAMERA ? 1 : 0),
                ObjectAnimator.ofFloat(hintTextView, View.TRANSLATION_Y, recording || currentPage != PAGE_CAMERA ? 0 : dp(16)),
                ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, recording || currentPage != PAGE_CAMERA ? 0 : 1),
                ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, recording || currentPage != PAGE_CAMERA ? dp(16) : 0)
            );
            recordingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (recording) {
                        backButton.setVisibility(View.GONE);
                    }
                    if (recording || currentPage != PAGE_CAMERA) {
                        flashButton.setVisibility(View.GONE);
                    }
                }
            });
            recordingAnimator.setDuration(260);
            recordingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            recordingAnimator.start();
        } else {
            backButton.setAlpha(recording ? 0 : 1f);
            backButton.setVisibility(recording ? View.GONE : View.VISIBLE);
            flashButton.setAlpha(recording || currentPage != PAGE_CAMERA ? 0 : 1f);
            flashButton.setVisibility(recording || currentPage != PAGE_CAMERA ? View.GONE : View.VISIBLE);
            dualButton.setAlpha(recording || currentPage != PAGE_CAMERA ? 0 : 1f);
            dualButton.setVisibility(recording || currentPage != PAGE_CAMERA || cameraView == null || !cameraView.dualAvailable() ? View.GONE : View.VISIBLE);
            hintTextView.setAlpha(recording && currentPage == PAGE_CAMERA ? 1f : 0);
            hintTextView.setTranslationY(recording || currentPage != PAGE_CAMERA ? 0 : dp(16));
            modeSwitcherView.setAlpha(recording || currentPage != PAGE_CAMERA ? 0 : 1f);
            modeSwitcherView.setTranslationY(recording || currentPage != PAGE_CAMERA ? dp(16) : 0);
        }
    }

    private boolean videoTimerShown = true;
    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
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
        if (takingVideo) {
            recordControl.stopRecording();
            return false;
        }
        if (takingPhoto) {
            return false;
        }
        if (captionEdit.onBackPressed()) {
            return false;
        } else if (galleryListView != null) {
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
            return false;
        } else if (currentEditMode == EDIT_MODE_PAINT && paintView != null && paintView.onBackPressed()) {
            return false;
        } else if (currentEditMode > EDIT_MODE_NONE) {
            switchToEditMode(EDIT_MODE_NONE, true);
            return false;
        } else if (currentPage == PAGE_PREVIEW && (outputEntry == null || !outputEntry.isEdit)) {
            if (fromGallery && (paintView == null || !paintView.hasChanges()) && (outputEntry == null || outputEntry.filterFile == null) || !previewButtons.isShareEnabled()) {
                navigateTo(PAGE_CAMERA, true);
            } else {
                showDismissEntry();
            }
            return false;
        } else {
            close(true);
            return true;
        }
    }

    private Runnable afterPlayerAwait;
    private boolean previewAlreadySet;
    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo) {
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
        AndroidUtilities.runOnUIThread(afterPlayerAwait, 400);
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
            previewButtons.appear(page == PAGE_PREVIEW && openProgress > 0, animated);
        }
        showVideoTimer(page == PAGE_CAMERA && isVideo, animated);
        if (page != PAGE_PREVIEW) {
            videoTimeView.show(false, animated);
        }
        if (animated) {
            pageAnimator = new AnimatorSet();

            ArrayList<Animator> animators = new ArrayList<>();

            if (cameraView != null) {
                animators.add(ObjectAnimator.ofFloat(cameraView, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            }
            cameraViewThumb.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(cameraViewThumb, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(previewView, View.ALPHA, page == PAGE_PREVIEW ? 1 : 0));

            animators.add(ObjectAnimator.ofFloat(recordControl, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(flashButton, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(dualButton, View.ALPHA, page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(recordControl, View.TRANSLATION_Y, page == PAGE_CAMERA ? 0 : dp(24)));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.ALPHA, page == PAGE_CAMERA ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(modeSwitcherView, View.TRANSLATION_Y, page == PAGE_CAMERA ? 0 : dp(24)));
            backButton.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(backButton, View.ALPHA, 1));
            animators.add(ObjectAnimator.ofFloat(hintTextView, View.ALPHA, page == PAGE_CAMERA && animatedRecording ? 1 : 0));
            animators.add(ObjectAnimator.ofFloat(captionContainer, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(captionContainer, View.TRANSLATION_Y, page == PAGE_PREVIEW ? 0 : dp(12)));
            animators.add(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));

            animators.add(ObjectAnimator.ofFloat(videoTimelineView, View.ALPHA, page == PAGE_PREVIEW && isVideo ? 1f : 0));

            animators.add(ObjectAnimator.ofFloat(muteButton, View.ALPHA, page == PAGE_PREVIEW && isVideo ? 1f : 0));
            animators.add(ObjectAnimator.ofFloat(downloadButton, View.ALPHA, page == PAGE_PREVIEW ? 1f : 0));
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
            previewView.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            flashButton.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            dualButton.setAlpha(page == PAGE_CAMERA && cameraView != null && cameraView.dualAvailable() ? 1f : 0);
            recordControl.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            recordControl.setTranslationY(page == PAGE_CAMERA ? 0 : dp(16));
            modeSwitcherView.setAlpha(page == PAGE_CAMERA ? 1f : 0);
            modeSwitcherView.setTranslationY(page == PAGE_CAMERA ? 0 : dp(16));
            backButton.setVisibility(View.VISIBLE);
            backButton.setAlpha(1f);
            hintTextView.setAlpha(page == PAGE_CAMERA && animatedRecording ? 1f : 0);
            captionContainer.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            captionContainer.setTranslationY(page == PAGE_PREVIEW ? 0 : dp(12));
            muteButton.setAlpha(page == PAGE_PREVIEW && isVideo ? 1f : 0);
            downloadButton.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
//            privacySelector.setAlpha(page == PAGE_PREVIEW ? 1f : 0);
            videoTimelineView.setAlpha(page == PAGE_PREVIEW && isVideo ? 1f : 0);
            titleTextView.setAlpha(page == PAGE_PREVIEW ? 1f : 0f);
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

    private Parcelable lastGalleryScrollPosition;
    private MediaController.AlbumEntry lastGallerySelectedAlbum;

    private void createGalleryListView() {
        createGalleryListView(false);
    }

    private void destroyGalleryListView() {
        if (galleryListView == null) {
            return;
        }
        windowView.removeView(galleryListView);
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
                    final float amplitude = windowView.getMeasuredHeight() - galleryListView.top();
                    float t = Utilities.clamp(1f - translationY / amplitude, 1, 0);
                    containerView.setTranslationY2(t * dp(-32));
                    containerView.setAlpha(1 - .6f * t);
                    actionBarContainer.setAlpha(1f - t);
                }
            }

            @Override
            public void firstLayout() {
                galleryListView.setTranslationY(windowView.getMeasuredHeight() - galleryListView.top());
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
        };
        galleryListView.setOnBackClickListener(() -> {
            animateGalleryListView(false);
            lastGallerySelectedAlbum = null;
        });
        galleryListView.setOnSelectListener((entry, blurredBitmap) -> {
            if (entry == null || galleryListViewOpening != null || scrollingY || !isGalleryOpen()) {
                return;
            }

            if (forAddingPart) {
                if (outputEntry == null || !(entry instanceof MediaController.PhotoEntry)) {
                    return;
                }
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                createPhotoPaintView();
                outputEntry.editedMedia = true;
                paintView.appearAnimation(paintView.createPhoto(photoEntry.path, false));
//                StoryEntry.Part part = new StoryEntry.Part();
//                part.id = outputEntry.partsMaxId++;
//                part.file = new File(photoEntry.path);
//                if (photoEntry.width <= 0 || photoEntry.height <= 0) {
//                    BitmapFactory.Options opts = new BitmapFactory.Options();
//                    opts.inJustDecodeBounds = true;
//                    BitmapFactory.decodeFile(photoEntry.path, opts);
//                    part.width = opts.outWidth;
//                    part.height = opts.outHeight;
//                } else {
//                    part.width = photoEntry.width;
//                    part.height = photoEntry.height;
//                }
//                part.fileDeletable = false;
//                part.matrix.reset();
//                int width = part.width, height = part.height;
//                part.matrix.postScale(photoEntry.invert == 1 ? -1.0f : 1.0f, photoEntry.invert == 2 ? -1.0f : 1.0f, width / 2f, height / 2f);
//                if (photoEntry.orientation != 0) {
//                    part.matrix.postTranslate(-width / 2f, -height / 2f);
//                    part.matrix.postRotate(photoEntry.orientation);
//                    if (photoEntry.orientation == 90 || photoEntry.orientation == 270) {
//                        final int swap = height;
//                        height = width;
//                        width = swap;
//                    }
//                    part.matrix.postTranslate(width / 2f, height / 2f);
//                }
//                float scale = (float) outputEntry.resultWidth / width;
//                if ((float) height / (float) width > 1.29f) {
//                    scale = Math.max(scale, (float) outputEntry.resultHeight / height);
//                }
//                part.matrix.postScale(scale, scale);
//                part.matrix.postTranslate((outputEntry.resultWidth - width * scale) / 2f, (outputEntry.resultHeight - height * scale) / 2f);
//
//                final float randScale = .5f;
////                final float hw = (1f - randScale) * outputEntry.resultWidth / 2f, hh = (1f - randScale) * outputEntry.resultHeight / 2f;
////                float randTranslateX = AndroidUtilities.lerp(-hw, hw, Utilities.fastRandom.nextFloat());
////                float randTranslateY = AndroidUtilities.lerp(-hh, hh, Utilities.fastRandom.nextFloat());
////                float randRotate = AndroidUtilities.lerp(-10, 10, Utilities.fastRandom.nextFloat());
//                part.matrix.postScale(randScale, randScale, outputEntry.resultWidth / 2f, outputEntry.resultHeight / 2f);
////                part.matrix.postRotate(randRotate, outputEntry.resultWidth / 2f, outputEntry.resultHeight / 2f);
////                part.matrix.postTranslate(randTranslateX, randTranslateY);
//                outputEntry.parts.add(part);
//                previewView.set(outputEntry);
//
                animateGalleryListView(false);
            } else {
                if (entry instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                    isVideo = photoEntry.isVideo;
                    outputEntry = StoryEntry.fromPhotoEntry(photoEntry);
                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    outputEntry.blurredVideoThumb = blurredBitmap;
                    fromGallery = true;
                } else if (entry instanceof StoryEntry) {
                    StoryEntry storyEntry = (StoryEntry) entry;
                    if (storyEntry.file == null) {
                        downloadButton.showToast(R.raw.error, "Failed to load draft");
                        MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(storyEntry);
                        return;
                    }

                    isVideo = storyEntry.isVideo;
                    outputEntry = storyEntry;
                    outputEntry.blurredVideoThumb = blurredBitmap;
                    fromGallery = false;
                }

                showVideoTimer(false, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);

                animateGalleryListView(false);
                navigateTo(PAGE_PREVIEW, true);
            }

            if (galleryListView != null) {
                lastGalleryScrollPosition = galleryListView.layoutManager.onSaveInstanceState();
                lastGallerySelectedAlbum = galleryListView.getSelectedAlbum();
            }
        });
        if (lastGalleryScrollPosition != null) {
            galleryListView.layoutManager.onRestoreInstanceState(lastGalleryScrollPosition);
        }
        windowView.addView(galleryListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private boolean isGalleryOpen() {
        return !scrollingY && galleryListView != null && galleryListView.getTranslationY() < (windowView.getMeasuredHeight() - (int) (AndroidUtilities.displaySize.y * 0.35f) - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()));
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
        float to = open ? 0 : windowView.getHeight() - galleryListView.top() + AndroidUtilities.navigationBarHeight * 2.5f;
        float fulldist = Math.max(1, windowView.getHeight());

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
                    windowView.removeView(galleryListView);
                    galleryListView = null;
                    galleryOpenCloseAnimator = null;
                    galleryListViewOpening = null;
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
                titleTextView.setRightPadding(AndroidUtilities.dp(96));
            } else {
                titleTextView.setRightPadding(AndroidUtilities.dp(48));
            }
            downloadButton.setVisibility(View.VISIBLE);
//            privacySelector.setVisibility(View.VISIBLE);
            previewButtons.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.VISIBLE);
            captionContainer.setVisibility(View.VISIBLE);
            captionContainer.clearFocus();

//            privacySelector.setStoryPeriod(outputEntry == null || !UserConfig.getInstance(currentAccount).isPremium() ? 86400 : outputEntry.period);
            captionEdit.setPeriod(outputEntry == null ? 86400 : outputEntry.period, false);
            captionEdit.setPeriodVisible(outputEntry == null || !outputEntry.isEdit);
        }
        if (toPage == PAGE_PREVIEW) {
            previewButtons.setShareText(outputEntry != null && outputEntry.isEdit ? LocaleController.getString("Done", R.string.Done) : LocaleController.getString("Next", R.string.Next));
            previewButtons.setShareEnabled(true);
//            privacySelector.set(outputEntry, false);
            if (!previewAlreadySet) {
                previewView.set(outputEntry);
            }
            previewAlreadySet = false;
            if (outputEntry != null && outputEntry.isDraft) {
                if (outputEntry.paintFile != null) {
                    destroyPhotoPaintView();
                    createPhotoPaintView();
                    hidePhotoPaintView();
                }
                if (outputEntry.filterState != null) {
                    destroyPhotoFilterView();
                    createFilterPhotoView();
                }
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
            muteButton.setImageResource(outputEntry != null && outputEntry.muted ? R.drawable.media_unmute : R.drawable.media_mute);
            previewView.setVisibility(View.VISIBLE);
            videoTimelineView.setVisibility(isVideo ? View.VISIBLE : View.GONE);
            titleTextView.setVisibility(View.VISIBLE);
            titleTextView.setText(outputEntry != null && outputEntry.isEdit ? LocaleController.getString(R.string.RecorderEditStory) :  LocaleController.getString(R.string.RecorderNewStory));
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
        }
        if (fromPage == PAGE_PREVIEW) {
//            privacySelectorHint.hide();
            captionEdit.hidePeriodPopup();
            muteHint.hide();
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
    }

    private void onNavigateEnd(int fromPage, int toPage) {
        if (fromPage == PAGE_CAMERA) {
            destroyCameraView(false);
            recordControl.setVisibility(View.GONE);
            zoomControlView.setVisibility(View.GONE);
            modeSwitcherView.setVisibility(View.GONE);
            dualButton.setVisibility(View.GONE);
            animateRecording(false, false);
            setAwakeLock(false);
        }
        cameraViewThumb.setClickable(toPage == PAGE_CAMERA);
        if (fromPage == PAGE_PREVIEW) {
            previewButtons.setVisibility(View.GONE);
            previewView.setVisibility(View.GONE);
            captionContainer.setVisibility(View.GONE);
            muteButton.setVisibility(View.GONE);
            downloadButton.setVisibility(View.GONE);
//            privacySelector.setVisibility(View.GONE);
            previewView.setVisibility(View.GONE);
            videoTimelineView.setVisibility(View.GONE);
            destroyPhotoPaintView();
            destroyPhotoFilterView();
            titleTextView.setVisibility(View.GONE);
            destroyGalleryListView();
            trash.setAlpha(0f);
            trash.setVisibility(View.GONE);
            videoTimeView.setVisibility(View.GONE);
        }
        if (toPage == PAGE_PREVIEW) {
            createPhotoPaintView();
            hidePhotoPaintView();
            createFilterPhotoView();
            previewView.updatePauseReason(2, false);
            previewView.updatePauseReason(3, false);
            previewView.updatePauseReason(4, false);
            previewView.updatePauseReason(5, false);
            videoTimeView.setVisibility(outputEntry != null && outputEntry.duration >= 30_000 ? View.VISIBLE : View.GONE);
        }
        if (toPage == PAGE_CAMERA && showSavedDraftHint) {
            getDraftSavedHint().setVisibility(View.VISIBLE);
            getDraftSavedHint().show();
            recordControl.updateGalleryImage();
        }
        showSavedDraftHint = false;

        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setAllowTouch(toPage == PAGE_PREVIEW && (currentEditMode == EDIT_MODE_NONE || currentEditMode == EDIT_MODE_FILTER));
        }
//        if (toPage == PAGE_PREVIEW && !privacySelectorHintOpened) {
//            privacySelectorHint.show(false);
//            privacySelectorHintOpened = true;
//        }
        if (captionEdit != null) {
            captionEdit.ignoreTouches = toPage != PAGE_PREVIEW;
        }
    }

    private AnimatorSet editModeAnimator;
    public void switchToEditMode(int editMode, boolean animated) {
        if (currentEditMode == editMode) {
            return;
        }

        final int oldEditMode = currentEditMode;
        currentEditMode = editMode;

        if (editModeAnimator != null) {
            editModeAnimator.cancel();
            editModeAnimator = null;
        }

        previewButtons.appear(editMode == EDIT_MODE_NONE && openProgress > 0, animated);

        ArrayList<Animator> animators = new ArrayList<>();

        boolean delay = photoFilterView == null && editMode == EDIT_MODE_FILTER;
        if (editMode == EDIT_MODE_FILTER) {
            createFilterPhotoView();
//            animatePhotoFilterTexture(true, animated);
            previewTouchable = photoFilterView;
            photoFilterView.getToolsView().setAlpha(0f);
            photoFilterView.getToolsView().setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.TRANSLATION_Y, 0));
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.ALPHA, 1));
            TextureView textureView = photoFilterView.getMyTextureView();
            if (textureView != null) {
                animators.add(ObjectAnimator.ofFloat(textureView, View.ALPHA, 1));
            }
        } else if (oldEditMode == EDIT_MODE_FILTER && photoFilterView != null) {
            previewTouchable = null;
//            animatePhotoFilterTexture(false, animated);
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.TRANSLATION_Y, dp(186 + 40)));
            animators.add(ObjectAnimator.ofFloat(photoFilterView.getToolsView(), View.ALPHA, 0));
            TextureView textureView = photoFilterView.getMyTextureView();
            if (textureView != null) {
                animators.add(ObjectAnimator.ofFloat(textureView, View.ALPHA, 0));
            }
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

        animators.add(ObjectAnimator.ofFloat(muteButton, View.ALPHA, editMode == EDIT_MODE_NONE && isVideo ? 1 : 0));
        animators.add(ObjectAnimator.ofFloat(downloadButton, View.ALPHA, editMode == EDIT_MODE_NONE ? 1 : 0));
//        animators.add(ObjectAnimator.ofFloat(privacySelector, View.ALPHA, editMode == EDIT_MODE_NONE ? 1 : 0));

//        animators.add(ObjectAnimator.ofFloat(videoTimelineView, View.ALPHA, currentPage == PAGE_PREVIEW && isVideo && editMode == EDIT_MODE_NONE ? 1f : 0f));
        animators.add(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, currentPage == PAGE_PREVIEW && editMode == EDIT_MODE_NONE ? 1f : 0f));

        int bottomMargin = 0;
        if (editMode == EDIT_MODE_FILTER) {
            previewContainer.setPivotY(previewContainer.getMeasuredHeight() * .2f);
            bottomMargin = dp(164);
        } else if (editMode == EDIT_MODE_PAINT) {
            previewContainer.setPivotY(previewContainer.getMeasuredHeight() * .6f);
            bottomMargin = dp(40);
        }

        float scale = 1f;
        if (bottomMargin > 0) {
            final int bottomPivot = previewContainer.getHeight() - (int) previewContainer.getPivotY();
            scale = (float) (bottomPivot - bottomMargin) / bottomPivot;
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

        animators.add(ObjectAnimator.ofFloat(captionContainer, View.ALPHA, editMode == EDIT_MODE_NONE ? 1f : 0));

        onSwitchEditModeStart(oldEditMode, editMode);
        if (animated) {
            editModeAnimator = new AnimatorSet();
            editModeAnimator.playTogether(animators);
            editModeAnimator.setDuration(320);
            editModeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            editModeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onSwitchEditModeEnd(oldEditMode, editMode);
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
            onSwitchEditModeEnd(oldEditMode, editMode);
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
        paintView.keyboardNotifier.ignore(true);
    }

    private void createPhotoPaintView() {
        if (paintView != null) {
            return;
        }
        Pair<Integer, Integer> size = previewView.getPaintSize();
        if (paintViewBitmap != null) {
            paintViewBitmap.recycle();
            paintViewBitmap = null;
        }
        if (outputEntry != null && outputEntry.isDraft && outputEntry.paintFile != null) {
            paintViewBitmap = BitmapFactory.decodeFile(outputEntry.paintFile.getPath());
        }
        if (paintViewBitmap == null) {
            paintViewBitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888);
        }
        paintView = new PaintView(activity, windowView, activity, currentAccount, paintViewBitmap, null, previewView.getOrientation(), outputEntry == null ? null : outputEntry.mediaEntities, previewContainer.getMeasuredWidth(), previewContainer.getMeasuredHeight(), new MediaController.CropState(), null, resourcesProvider) {
            @Override
            public void onEntityDraggedTop(boolean value) {
                previewHighlight.show(true, value, actionBarContainer);
            }

            @Override
            protected void onGalleryClick() {
                destroyGalleryListView();
                createGalleryListView(true);
                animateGalleryListView(true);
            }

            @Override
            public void onEntityDraggedBottom(boolean value) {
                previewHighlight.updateCaption(captionEdit.getText());
//                previewHighlight.show(false, value, null);
            }

            @Override
            public void onEntityDragEnd(boolean delete) {
                captionContainer.clearAnimation();
                captionContainer.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                trash.onDragInfo(false, delete);
                trash.clearAnimation();
                trash.animate().alpha(0f).withEndAction(() -> {
                    trash.setVisibility(View.GONE);
                }).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).setStartDelay(delete ? 500 : 0).start();
                if (delete) {
                    removeCurrentEntity();
                }
                super.onEntityDragEnd(delete);
            }

            @Override
            public void onEntityDragStart() {
                captionContainer.clearAnimation();
                captionContainer.animate().alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();

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
                }
                if (captionEdit != null) {
                    captionEdit.ignoreTouches = open;
                    captionEdit.keyboardNotifier.ignore(open);
                }
            }
        };
        containerView.addView(paintView);
        paintViewRenderView = paintView.getRenderView();
        if (paintViewRenderView != null) {
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
    }

    private void destroyPhotoPaintView() {
        if (paintView == null) {
            return;
        }
        paintView.onCleanupEntities();

        paintView.shutdown();
        containerView.removeView(paintView);
        if (paintViewBitmap != null) {
            paintViewBitmap.recycle();
            paintViewBitmap = null;
        }
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
            captionContainer.setVisibility(View.VISIBLE);
            if (paintView != null) {
                paintView.clearSelection();
            }
            downloadButton.setVisibility(View.VISIBLE);
            titleTextView.setVisibility(View.VISIBLE);
//            privacySelector.setVisibility(View.VISIBLE);
            if (isVideo) {
                muteButton.setVisibility(View.VISIBLE);
            }
            videoTimelineView.setVisibility(View.VISIBLE);
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
        if (fromMode == EDIT_MODE_FILTER && toMode == EDIT_MODE_NONE) {
            destroyPhotoFilterView();
        }
        if (toMode == EDIT_MODE_PAINT) {
            backButton.setVisibility(View.GONE);
        }
        if (fromMode == EDIT_MODE_PAINT && paintView != null) {
            paintView.setVisibility(View.GONE);
        }
        if (fromMode == EDIT_MODE_NONE) {
            captionContainer.setVisibility(View.GONE);
            muteButton.setVisibility(View.GONE);
            downloadButton.setVisibility(View.GONE);
//            privacySelector.setVisibility(View.GONE);
            videoTimelineView.setVisibility(View.GONE);
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

    private void applyPaint(boolean drawEntities) {
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
        paintView.getBitmap(outputEntry.mediaEntities, outputEntry.resultWidth, outputEntry.resultHeight, false, false);
        ArrayList<VideoEditedInfo.MediaEntity> entities = new ArrayList<>();
        Bitmap bitmap = paintView.getBitmap(entities, outputEntry.resultWidth, outputEntry.resultHeight, true, drawEntities && !outputEntry.wouldBeVideo());
        List<TLRPC.InputDocument> masks = paintView.getMasks();

        outputEntry.stickers = masks != null ? new ArrayList<>(masks) : null;
        TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, Bitmap.CompressFormat.PNG, outputEntry.resultWidth, outputEntry.resultHeight, 87, false, 101, 101);
        outputEntry.mediaEntities = entities == null || entities.isEmpty() ? null : entities;
        if (!outputEntry.isVideo) {
            outputEntry.averageDuration = Utilities.clamp(paintView.getLcm(), 7500L, 5000L);
        }
        outputEntry.paintFile = FileLoader.getInstance(currentAccount).getPathToAttach(size, true);
    }

    private void applyFilter(Runnable whenDone) {
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
                if (photoFilterBitmap != null) {
                    photoFilterBitmap.recycle();
                    photoFilterBitmap = null;
                }
                photoBitmap = photoFilterBitmap = StoryEntry.getScaledBitmap(opts -> BitmapFactory.decodeFile(outputEntry.file.getAbsolutePath(), opts), AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y, true);
            }
        }
        if (photoBitmap == null && !outputEntry.isVideo) {
            return;
        }

        photoFilterView = new PhotoFilterView(activity, previewView.getTextureView(), photoBitmap, previewView.getOrientation(), outputEntry == null ? null : outputEntry.filterState, null, 0, false, false, resourcesProvider);
        containerView.addView(photoFilterView);
        if (photoFilterEnhanceView != null) {
            photoFilterEnhanceView.setFilterView(photoFilterView);
        }
        photoFilterViewTextureView = photoFilterView.getMyTextureView();
        if (photoFilterViewTextureView != null) {
            photoFilterViewTextureView.setOpaque(false);
        }
        previewView.setFilterTextureView(photoFilterViewTextureView);
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
            applyFilter(null);
            switchToEditMode(EDIT_MODE_NONE, true);
        });
        photoFilterView.getCancelTextView().setOnClickListener(v -> {
//            if (photoFilterView.hasChanges()) {
//                if (parentActivity == null) {
//                    return;
//                }
//                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
//                builder.setMessage(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
//                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
//                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> switchToEditMode(0));
//                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
//                showAlertDialog(builder);
//            } else {
                switchToEditMode(EDIT_MODE_NONE, true);
//            }
        });
        photoFilterView.getToolsView().setVisibility(View.GONE);
        photoFilterView.getToolsView().setAlpha(0f);
        photoFilterView.getToolsView().setTranslationY(AndroidUtilities.dp(186));
        photoFilterView.init();
    }

    private void applyFilterMatrix() {
        if (outputEntry != null && photoFilterViewTextureView != null) {
            Matrix photoFilterStartMatrix = new Matrix();
            photoFilterStartMatrix.reset();
            if (outputEntry.orientation != 0) {
                photoFilterStartMatrix.postRotate(-outputEntry.orientation, previewContainer.getMeasuredWidth() / 2f, previewContainer.getMeasuredHeight() / 2f);
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

//    private float photoFilterShow;
//    private ValueAnimator photoFilterAnimator;
//    private void animatePhotoFilterTexture(boolean show, boolean animated) {
//        if (photoFilterView == null || photoFilterView.getMyTextureView() == null || photoFilterStartMatrix == null || photoFilterEndMatrix == null) {
//            return;
//        }
//        TextureView textureView = photoFilterView.getMyTextureView();
//        if (photoFilterAnimator != null) {
//            photoFilterAnimator.cancel();
//            photoFilterAnimator = null;
//        }
//        if (animated) {
//            if (show) {
//                previewView.setDraw(false);
//            }
//
//            float[] startValues = new float[9];
//            float[] endValues = new float[9];
//            photoFilterStartMatrix.getValues(startValues);
//            photoFilterEndMatrix.getValues(endValues);
//
//            Matrix interpolatedMatrix = new Matrix();
//            float[] interpolatedValues = new float[9];
//
//            photoFilterAnimator = ValueAnimator.ofFloat(photoFilterShow, show ? 1 : 0);
//            photoFilterAnimator.addUpdateListener(anm -> {
//                photoFilterShow = (float) anm.getAnimatedValue();
//                for (int i = 0; i < 9; i++) {
//                    interpolatedValues[i] = startValues[i] + photoFilterShow * (endValues[i] - startValues[i]);
//                }
//                interpolatedMatrix.setValues(interpolatedValues);
//                textureView.setTransform(interpolatedMatrix);
//                textureView.invalidate();
//            });
//            photoFilterAnimator.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(Animator animation) {
//                    photoFilterShow = show ? 1 : 0;
//                    final Matrix matrix = show ? photoFilterEndMatrix : photoFilterStartMatrix;
//                    if (matrix != null) {
//                        textureView.setTransform(matrix);
//                    }
//                    textureView.invalidate();
//                    if (!show) {
//                        previewView.setDraw(true);
//                    }
//                }
//            });
//            photoFilterAnimator.setDuration(320);
//            photoFilterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
//            photoFilterAnimator.start();
//        } else {
//            previewView.setDraw(!show);
//            photoFilterShow = show ? 1 : 0;
//            final Matrix matrix = show ? photoFilterEndMatrix : photoFilterStartMatrix;
//            textureView.setTransform(matrix);
//            textureView.invalidate();
//        }
//    }

    private void destroyPhotoFilterView() {
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
        previewView.setFilterTextureView(null);
        if (photoFilterViewBlurControl != null) {
            previewContainer.removeView(photoFilterViewBlurControl);
            photoFilterViewBlurControl = null;
        }
        if (photoFilterViewCurvesControl != null) {
            previewContainer.removeView(photoFilterViewCurvesControl);
            photoFilterViewCurvesControl = null;
        }
        photoFilterView = null;
        if (photoFilterBitmap != null) {
            photoFilterBitmap.recycle();
            photoFilterBitmap = null;
        }
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
                setCameraFlashModeIcon(isDual() || getCameraSession() == null || isFrontface() ? null : getCameraSession().getCurrentFlashMode(), true);
            }

            @Override
            protected void onSavedDualCameraSuccess() {
                if (MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) < 2) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (takingVideo || takingPhoto || cameraView == null || currentPage != PAGE_CAMERA) {
                            return;
                        }
                        if (savedDualHint != null) {
                            CharSequence text = isFrontface() ? LocaleController.getString(R.string.StoryCameraSavedDualBackHint) : LocaleController.getString(R.string.StoryCameraSavedDualFrontHint);
                            savedDualHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, savedDualHint.getTextPaint()));
                            savedDualHint.setText(text);
                            savedDualHint.show();
                            MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", MessagesController.getGlobalMainSettings().getInt("storysvddualhint", 0) + 1).apply();
                        }
                    }, 340);
                }
                dualButton.setValue(isDual());
            }
        };
        cameraView.isStory = true;
        cameraView.setThumbDrawable(getCameraThumb());
        cameraView.initTexture();
        cameraView.setDelegate(() -> {
            String currentFlashMode = cameraView.getCameraSession().getCurrentFlashMode();
            if (TextUtils.equals(currentFlashMode, cameraView.getCameraSession().getNextFlashMode())) {
                currentFlashMode = null;
            }
            setCameraFlashModeIcon(currentPage == PAGE_CAMERA ? currentFlashMode : null, true);
            if (zoomControlView != null) {
                zoomControlView.setZoom(cameraZoom = 0, false);
            }
        });
        dualButton.setVisibility(cameraView.dualAvailable() && currentPage == PAGE_CAMERA ? View.VISIBLE : View.GONE);
        flashButton.setTranslationX(cameraView.dualAvailable() ? -dp(46) : 0);
        previewContainer.addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        if (MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) < 1) {
            cameraHint.show();
            MessagesController.getGlobalMainSettings().edit().putInt("storyhint2", MessagesController.getGlobalMainSettings().getInt("storyhint2", 0) + 1).apply();
        } else if (!cameraView.isSavedDual() && cameraView.dualAvailable() && MessagesController.getGlobalMainSettings().getInt("storydualhint", 0) < 2) {
            dualHint.show();
        }
    }

    private Drawable getCameraThumb() {
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {}
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
        } catch (Throwable ignore) {}
    }

    private void showDismissEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
        builder.setMessage(LocaleController.getString("PhotoEditorDiscardAlert", R.string.PhotoEditorDiscardAlert));
        if (outputEntry != null) {
            builder.setNeutralButton(outputEntry.isDraft ? LocaleController.getString("StoryKeepDraft") : LocaleController.getString("StorySaveDraft"), (di, i) -> {
                if (outputEntry == null) {
                    return;
                }
                showSavedDraftHint = !outputEntry.isDraft;
                applyFilter(null);
                applyPaint(false);
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
        builder.setPositiveButton(outputEntry != null && outputEntry.isDraft ? LocaleController.getString("StoryDeleteDraft") : LocaleController.getString("Discard", R.string.Discard), (dialogInterface, i) -> {
            if (outputEntry != null && outputEntry.isDraft) {
                MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(outputEntry);
                outputEntry = null;
            }
            navigateTo(PAGE_CAMERA, true);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        View positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton instanceof TextView) {
            ((TextView) positiveButton).setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            positiveButton.setBackground(Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), (int) (0.2f * 255)), 6, 6));
        }
    }

    private void destroyCameraView(boolean waitForThumb) {
        if (cameraView != null) {
            if (waitForThumb) {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                    if (cameraView != null) {
                        cameraView.destroy(true, null);
                        previewContainer.removeView(cameraView);
                        cameraView = null;
                    }
                });
            } else {
                saveLastCameraBitmap(() -> {
                    cameraViewThumb.setImageDrawable(getCameraThumb());
                });
                cameraView.destroy(true, null);
                previewContainer.removeView(cameraView);
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
                    new AlertDialog.Builder(getContext())
                        .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString("PermissionNoCameraWithHint", R.string.PermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                activity.startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
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
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                noGalleryPermission = (
//                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
//                    activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
//                );
//                if (noGalleryPermission) {
//                    activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 114);
//                }
//            } else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
    }

    private Runnable whenOpenDone;
    private void onResumeInternal() {
        if (currentPage == PAGE_CAMERA) {
//            requestedCameraPermission = false;
            if (openCloseAnimator != null && openCloseAnimator.isRunning()) {
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

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
    }
    private void onPauseInternal() {
        destroyCameraView(false);
        if (captionEdit != null) {
            captionEdit.onPause();
        }
        if (previewView != null) {
            previewView.updatePauseReason(0, true);
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
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
            }
        } else if (requestCode == 112) {
            if (!granted) {
                new AlertDialog.Builder(getContext())
                    .setTopAnimation(R.raw.permission_request_camera, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                    .setMessage(AndroidUtilities.replaceTags(LocaleController.getString("PermissionNoCameraMicVideo", R.string.PermissionNoCameraMicVideo)))
                    .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    })
                    .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
                    .create()
                    .show();
            }
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
        }
    }

    public void addNotificationObservers() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesDraftsUpdated);
    }

    public void removeNotificationObservers() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesDraftsUpdated);
    }

    private boolean isBackgroundVisible;
    private boolean forceBackgroundVisible;
    private void checkBackgroundVisibility() {
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

    public interface ClosingViewProvider {
        void preLayout(Runnable runnable);
        SourceView getView();
    }

    private void showPremiumPeriodBulletin(int period) {
        final int hours = period / 3600;
        BulletinFactory.of(windowView, resourcesProvider)
            .createSimpleBulletin(R.raw.fire_on, AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("StoryPeriodPremium", hours), () -> {
                if (previewView != null) {
                    previewView.updatePauseReason(4, true);
                }
                PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(new BaseFragment() {
                    { currentAccount = StoryRecorder.this.currentAccount; }
                    @Override
                    public Dialog showDialog(Dialog dialog) {
                        dialog.show();
                        return dialog;
                    }
                    @Override
                    public Activity getParentActivity() {
                        return StoryRecorder.this.activity;
                    }
                }, 0, false);
                sheet.setOnDismissListener(d -> {
                    if (previewView != null) {
                        previewView.updatePauseReason(4, false);
                    }
                });
                sheet.show();
            })).show(true);
    }

    public void setIconMuted(boolean muted, boolean animated) {
        if (muteButtonDrawable == null) {
            muteButtonDrawable = new RLottieDrawable(R.raw.media_mute_unmute, "media_mute_unmute", AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
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

    public static CharSequence cameraBtnSpan(Context context) {
        SpannableString cameraStr = new SpannableString("c");
        Drawable cameraDrawable = context.getResources().getDrawable(R.drawable.story_camera).mutate();
        final int sz = AndroidUtilities.dp(35);
        cameraDrawable.setBounds(-sz / 4, -sz, sz / 4 * 3, 0);
        cameraStr.setSpan(new ImageSpan(cameraDrawable) {
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                return super.getSize(paint, text, start, end, fm) / 3 * 2;
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                canvas.save();
                canvas.translate(0, (bottom - top) / 2 + dp(1));
                cameraDrawable.setAlpha(paint.getAlpha());
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
                canvas.restore();
            }
        }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return cameraStr;
    }
}
