package org.telegram.ui.MediaRecorder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stories.recorder.MediaRecorderView;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.EDIT_MODE_NONE;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.PAGE_CAMERA;
import static org.telegram.ui.Stories.recorder.MediaRecorderView.PAGE_EMBEDDED_CAMERA;

public class ChatMediaRecorder {

    public static final long STATE_SWITCH_DURATION_MS = 320;

    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    protected final Theme.ResourcesProvider resourcesProvider;
    protected final Activity activity;

    @IntDef({SHRINK_STATE, EXPANDED_STATE, SHRINKING_STATE, EXPANDING_STATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    public static final int SHRINK_STATE = 0;
    public static final int SHRINKING_STATE = 1;
    public static final int EXPANDED_STATE = 2;
    public static final int EXPANDING_STATE = 3;

    private int state;
    private float animationProgress = 0F;

    private final int currentAccount;

    @State
    public int getViewState() {
        return state;
    }

    public boolean isAnimating() {
        return state == SHRINKING_STATE || state == EXPANDING_STATE;
    }

    public boolean isExpanded() {
        return state == EXPANDED_STATE;
    }

    public boolean isExpanding() {
        return state == EXPANDING_STATE;
    }

    public boolean isShrink() {
        return state == SHRINK_STATE;
    }

    public boolean isShrinking() {
        return state == SHRINKING_STATE;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    private ValueAnimator openCloseAnimator;

    // View params before extended
    private final RectF shrinkedRect = new RectF();

    // Parent translations
    private int cameraViewOffsetX;
    private int cameraViewOffsetY;
    private int currentPanTranslationY;

    public void setCameraViewOffsetX(int offset) {
        cameraViewOffsetX = offset;
    }

    public void setCameraViewOffsetY(int offset) {
        cameraViewOffsetY = offset;
    }

    public void setCurrentPanTranslationY(int translation) {
        currentPanTranslationY = translation;
    }

    private MediaRecorderView mediaRecorderView;

    public MediaRecorderView getView() {
        return mediaRecorderView;
    }

    public ChatMediaRecorder(
            Activity activity,
            ChatAttachAlertPhotoLayout parentLayout,
            ChatAttachAlert parentAlert,
            boolean isShrinkMode,
            Theme.ResourcesProvider resourcesProvider
    ) {
        this.activity = activity;
        this.currentAccount = parentAlert.currentAccount;

        this.resourcesProvider = resourcesProvider;

        mediaRecorderView = new MediaRecorderView(activity, currentAccount, resourcesProvider, MediaRecorderView.CHAT_ATTACH) {

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (AndroidUtilities.makingGlobalBlurBitmap) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    super.dispatchDraw(canvas);
                } else {
                    int maxY = (int) Math.min(
                            parentAlert.getCommentTextViewTop() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom()
                                    + dp(8) : 0) + currentPanTranslationY + parentAlert.getContainerView().getTranslationY()
                                    - parentLayout.getTranslationY(), parentLayout.getMeasuredHeight());

                    canvas.save();
                    if (isShrink() || isShrinking()) {
                        canvas.clipRect(
                                cameraViewOffsetX,
                                AndroidUtilities.lerp(0, cameraViewOffsetY, isAnimating() ? getAnimationProgress() : 1F),
                                getMeasuredWidth(),
                                maxY
                        );
                    } else if (isExpanded() || isExpanding()) {
                        canvas.clipRect(
                                AndroidUtilities.lerp(cameraViewOffsetX, 0, isAnimating() ? getAnimationProgress() : 1F),
                                AndroidUtilities.lerp(cameraViewOffsetY, 0, isAnimating() ? getAnimationProgress() : 1F),
                                getMeasuredWidth(),
                                getMeasuredHeight()
                        );
                    }

                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            }

        };
        if (Build.VERSION.SDK_INT >= 21) {
            mediaRecorderView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int maxY = (int) Math.min(
                            parentAlert.getCommentTextViewTop() - (parentAlert.mentionContainer != null ? parentAlert.mentionContainer.clipBottom()
                                    + dp(8) : 0) + currentPanTranslationY + parentAlert.getContainerView().getTranslationY()
                                    - mediaRecorderView.getTranslationY(), view.getMeasuredHeight());

                    if (isShrink() || isShrinking()) {
                        int rad = AndroidUtilities.lerp(0, dp(8 * parentAlert.getCornerRadius()),
                                isAnimating() ? getAnimationProgress() : 1F);
                        outline.setRoundRect(cameraViewOffsetX,
                                AndroidUtilities.lerp(0, cameraViewOffsetY, isAnimating() ? getAnimationProgress() : 1F),
                                view.getMeasuredWidth() + rad, maxY + rad, rad);
                    } else if (isExpanded() || isExpanding()) {
                        int rad = AndroidUtilities.lerp(0, dp(8 * parentAlert.getCornerRadius()),
                                isAnimating() ? 1F - getAnimationProgress() : 1F);
                        outline.setRoundRect(
                                AndroidUtilities.lerp(cameraViewOffsetX, 0, isAnimating() ? getAnimationProgress() : 1F),
                                AndroidUtilities.lerp(cameraViewOffsetY, 0, isAnimating() ? getAnimationProgress() : 1F),
                                view.getMeasuredWidth() + rad,
                                view.getMeasuredHeight() + rad,
                                rad
                        );
                    }
                }
            });
            mediaRecorderView.setClipToOutline(true);
        }
        mediaRecorderView.setMediaRecorderDelegate(new MediaRecorderDelegateImpl());
        mediaRecorderView.setOpenProgress(1F);

        this.state = isShrinkMode ? SHRINK_STATE : EXPANDED_STATE;
    }

    public boolean processTouchEvent(MotionEvent event) {
        if (openCloseAnimator != null && !openCloseAnimator.isRunning() && mediaRecorderView != null) {
            mediaRecorderView.onTouchEvent(event);
            return true;
        }
        return true;
    }

    public void showCamera() {
        mediaRecorderView.setupState(null, false, new MediaRecorderView.Params(false, 0));

        AndroidUtilities.lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mediaRecorderView.navigateTo(PAGE_EMBEDDED_CAMERA, false);
        mediaRecorderView.switchToEditMode(EDIT_MODE_NONE, false);
    }

    public void expandView(boolean animated, RectF targetRect, ViewGroup parent, Runnable onOpened) {
        if (state != SHRINK_STATE) {
            return;
        }

        Rect tmp = new Rect();
        mediaRecorderView.getDrawingRect(tmp);
        parent.offsetDescendantRectToMyCoords(mediaRecorderView, tmp);
        shrinkedRect.set(tmp);
        shrinkedRect.offset(mediaRecorderView.getTranslationX(), mediaRecorderView.getTranslationY());
        mediaRecorderView.setSourceViewRect(0, shrinkedRect);

        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        openCloseAnimator = ObjectAnimator.ofFloat(0F, 1F);
        openCloseAnimator.setDuration(STATE_SWITCH_DURATION_MS);
        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                state = EXPANDING_STATE;

                mediaRecorderView.setExpandProgress(0);
                ViewGroup.LayoutParams lp = mediaRecorderView.getLayoutParams();
                lp.width = (int) targetRect.width();
                lp.height = (int) targetRect.height();
                mediaRecorderView.setY(targetRect.top);
                mediaRecorderView.setX(targetRect.left);

                mediaRecorderView.requestLayout();

                mediaRecorderView.navigateTo(PAGE_CAMERA, true);
                AndroidUtilities.lockOrientation(activity);
                notificationsLocker.lock();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                state = EXPANDED_STATE;
                onOpened.run();

                if (mediaRecorderView != null) {
                    mediaRecorderView.onOpened();
                    mediaRecorderView.checkBackgroundVisibility(true);
                }

                AndroidUtilities.unlockOrientation(activity);
                notificationsLocker.unlock();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                NotificationCenter.getGlobalInstance().runDelayedNotifications();
            }
        });

        animationProgress = 0F;
        openCloseAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();

            mediaRecorderView.setExpandProgress(animationProgress);
            mediaRecorderView.checkBackgroundVisibility();

            mediaRecorderView.invalidate();
        });
        openCloseAnimator.start();

        mediaRecorderView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void shrinkView() {
        if (state != EXPANDED_STATE) {
            return;
        }

        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        openCloseAnimator = ObjectAnimator.ofFloat(0F, 1F);
        openCloseAnimator.setDuration(STATE_SWITCH_DURATION_MS);
        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);

        animationProgress = 0F;
        openCloseAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            animationProgress = animatedValue;

            mediaRecorderView.setExpandProgress(1f - animatedValue);

            mediaRecorderView.invalidate();
        });
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                state = SHRINKING_STATE;

                mediaRecorderView.navigateTo(PAGE_EMBEDDED_CAMERA, true);
                AndroidUtilities.lockOrientation(activity);
                notificationsLocker.lock();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                state = SHRINK_STATE;

                if (mediaRecorderView != null) {
                    Bulletin.removeDelegate(mediaRecorderView);

                    mediaRecorderView.setExpandProgress(1f);
                    ViewGroup.LayoutParams lp = mediaRecorderView.getLayoutParams();
                    lp.width = (int) shrinkedRect.width();
                    lp.height = (int) shrinkedRect.height();
                    mediaRecorderView.setTranslationY(shrinkedRect.top);
                    mediaRecorderView.setTranslationX(shrinkedRect.left);

                    mediaRecorderView.requestLayout();
                }

                shrinkedRect.setEmpty();

                AndroidUtilities.unlockOrientation(activity);
                notificationsLocker.unlock();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                NotificationCenter.getGlobalInstance().runDelayedNotifications();
            }
        });
        openCloseAnimator.start();

        mediaRecorderView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    public void close() {
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator.removeAllUpdateListeners();
            openCloseAnimator = null;
        }

        if (mediaRecorderView != null) {
            mediaRecorderView.close(false);
            mediaRecorderView = null;
        }
    }

    private class MediaRecorderDelegateImpl implements MediaRecorderView.MediaRecorderDelegate {


        @Override
        public void close(boolean animated) {
            ChatMediaRecorder.this.shrinkView();
        }

        @Override
        public void openPremium() {
            // Do nothing here
        }

        @Override
        public void processDone(StoryEntry outputEntry) {
            // TODO handle message send
        }

        @Override
        public void onCoverEdited(StoryEntry outputEntry) {
            // Do nothing here
        }

        @Override
        public void setTitle(StoryEntry outputEntry, int page, SimpleTextView view) {

        }

        @Override
        public void onEntryCreated(StoryEntry entry) {
            // Do nothing here
        }
    }

    private static void log(String message) {
        Log.i("kirillNay", message);
    }

}
