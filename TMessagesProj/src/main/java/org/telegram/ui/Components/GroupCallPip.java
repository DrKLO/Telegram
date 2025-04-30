package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.voip.RTMPStreamPipOverlay;
import org.telegram.ui.GroupCallActivity;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

public class GroupCallPip implements NotificationCenter.NotificationCenterDelegate {

    private static GroupCallPip instance;
    private static boolean forceRemoved = true;

    FrameLayout windowView;
    FrameLayout windowRemoveTooltipView;
    View removeTooltipView;
    FrameLayout windowRemoveTooltipOverlayView;
    FrameLayout alertContainer;
    GroupCallPipAlertView pipAlertView;
    int currentAccount;
    WindowManager windowManager;
    WindowManager.LayoutParams windowLayoutParams;
    AvatarsImageView avatarsImageView;
    RLottieDrawable deleteIcon;
    boolean showAlert;


    boolean animateToShowRemoveTooltip;
    boolean animateToPrepareRemove;
    float prepareToRemoveProgress = 0;

    boolean removed;

    int[] location = new int[2];
    float[] point = new float[2];

    int lastScreenX;
    int lastScreenY;
    float xRelative = -1;
    float yRelative = -1;

    int windowTop;
    int windowLeft;

    float windowX;
    float windowY;
    float windowOffsetLeft;
    float windowOffsetTop;


    private ValueAnimator.AnimatorUpdateListener updateXlistener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float x = (float) valueAnimator.getAnimatedValue();
            windowLayoutParams.x = (int) x;
            updateAvatarsPosition();
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener updateYlistener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float y = (float) valueAnimator.getAnimatedValue();
            windowLayoutParams.y = (int) y;
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        }
    };

    private final GroupCallPipButton button;
    private final RLottieImageView iconView;
    boolean moving;

    public GroupCallPip(Context context, int account) {
        currentAccount = account;
        float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        windowView = new FrameLayout(context) {
            float startX;
            float startY;
            long startTime;
            boolean pressed;
            AnimatorSet moveToBoundsAnimator;

            Runnable pressedRunnable = new Runnable() {
                @Override
                public void run() {
                    VoIPService voIPService = VoIPService.getSharedInstance();
                    if (voIPService != null && voIPService.isMicMute()) {
                        ChatObject.Call call = voIPService.groupCall;
                        TLRPC.GroupCallParticipant participant = call.participants.get(voIPService.getSelfId());
                        if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(voIPService.getChat())) {
                            return;
                        }
                        AndroidUtilities.runOnUIThread(micRunnable, 90);
                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignore) {}
                        pressed = true;
                    }
                }
            };

            Runnable micRunnable = () -> {
                if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().isMicMute()) {
                    VoIPService.getSharedInstance().setMicMute(false, true, false);
                }
            };

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (AndroidUtilities.displaySize.x != lastScreenX || lastScreenY != AndroidUtilities.displaySize.y) {

                    lastScreenX = AndroidUtilities.displaySize.x;
                    lastScreenY = AndroidUtilities.displaySize.y;

                    if (xRelative < 0) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("groupcallpipconfig", Context.MODE_PRIVATE);
                        xRelative = preferences.getFloat("relativeX", 1f);
                        yRelative = preferences.getFloat("relativeY", 0.4f);
                    }

                    if (instance != null) {
                        instance.setPosition(xRelative, yRelative);
                    }
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (instance == null) {
                    return false;
                }
                float x = event.getRawX();
                float y = event.getRawY();
                ViewParent parent = getParent();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        getLocationOnScreen(location);
                        windowOffsetLeft = location[0] - windowLayoutParams.x;
                        windowOffsetTop = location[1] - windowLayoutParams.y;

                        startX = x;
                        startY = y;
                        startTime = System.currentTimeMillis();
                        AndroidUtilities.runOnUIThread(pressedRunnable, 300);
                        windowX = windowLayoutParams.x;
                        windowY = windowLayoutParams.y;
                        pressedState = true;
                        checkButtonAlpha();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - startX;
                        float dy = y - startY;
                        if (!moving && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                            if (parent != null) {
                                parent.requestDisallowInterceptTouchEvent(true);
                            }
                            AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                            moving = true;
                            showRemoveTooltip(true);
                            showAlert(false);
                            startX = x;
                            startY = y;
                            dx = 0;
                            dy = 0;
                        }
                        if (moving) {
                            windowX += dx;
                            windowY += dy;
                            startX = x;
                            startY = y;

                            updateButtonPosition();

                            float cx = windowX + getMeasuredWidth() / 2f;
                            float cy = windowY + getMeasuredHeight() / 2f;

                            float cxRemove = windowLeft - windowOffsetLeft + windowRemoveTooltipView.getMeasuredWidth() / 2f;
                            float cyRemove = windowTop - windowOffsetTop + windowRemoveTooltipView.getMeasuredHeight() / 2f;
                            float distanceToRemove = (cx - cxRemove) * (cx - cxRemove) + (cy - cyRemove) * (cy - cyRemove);
                            boolean prepareToRemove = false;
                            boolean pinnedToCenter = false;
                            if (distanceToRemove < AndroidUtilities.dp(80) * AndroidUtilities.dp(80)) {
                                prepareToRemove = true;
                                double angle = Math.toDegrees(Math.atan((cx - cxRemove) / (cy - cyRemove)));
                                if ((cx > cxRemove && cy < cyRemove) || (cx < cxRemove && cy < cyRemove)) {
                                    angle = 270 - angle;
                                } else {
                                    angle = 90 - angle;
                                }
                                button.setRemoveAngle(angle);
                                if (distanceToRemove < AndroidUtilities.dp(50) * AndroidUtilities.dp(50)) {
                                    pinnedToCenter = true;
                                }
                            }
                            pinnedToCenter(pinnedToCenter);
                            prepareToRemove(prepareToRemove);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        AndroidUtilities.cancelRunOnUIThread(micRunnable);
                        AndroidUtilities.cancelRunOnUIThread(pressedRunnable);
                        if (animateToPrepareRemove) {
                            if (pressed) {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().setMicMute(true, false, false);
                                }
                            }
                            pressed = false;
                            remove();
                            return false;
                        }
                        pressedState = false;
                        checkButtonAlpha();
                        if (pressed) {
                            if (VoIPService.getSharedInstance() != null) {
                                VoIPService.getSharedInstance().setMicMute(true, false, false);
                                try {
                                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                } catch (Exception ignored) {}
                            }
                            pressed = false;
                        } else if (event.getAction() == MotionEvent.ACTION_UP && !moving) {
                            onTap();
                            return false;
                        }
                        if (parent != null && moving) {
                            parent.requestDisallowInterceptTouchEvent(false);

                            int parentWidth = AndroidUtilities.displaySize.x;
                            int parentHeight = AndroidUtilities.displaySize.y;

                            float left = windowLayoutParams.x;
                            float right = left + getMeasuredWidth();
                            float top = windowLayoutParams.y;
                            float bottom = top + getMeasuredHeight();

                            moveToBoundsAnimator = new AnimatorSet();

                            float finallyX = left;
                            float finallyY = top;

                            float paddingHorizontal = -AndroidUtilities.dp(36);

                            if (left < paddingHorizontal) {
                                ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.x, finallyX = paddingHorizontal);
                                animator.addUpdateListener(updateXlistener);
                                moveToBoundsAnimator.playTogether(animator);
                            } else if (right > parentWidth - paddingHorizontal) {
                                ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.x, finallyX = (parentWidth - getMeasuredWidth() - paddingHorizontal));
                                animator.addUpdateListener(updateXlistener);
                                moveToBoundsAnimator.playTogether(animator);
                            }

                            int maxBottom = parentHeight + AndroidUtilities.dp(36);
                            if (top < AndroidUtilities.statusBarHeight - AndroidUtilities.dp(36)) {
                                ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.y, finallyY = AndroidUtilities.statusBarHeight - AndroidUtilities.dp(36));
                                animator.addUpdateListener(updateYlistener);
                                moveToBoundsAnimator.playTogether(animator);
                            } else if (bottom > maxBottom) {
                                ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.y, finallyY = maxBottom - getMeasuredHeight());
                                animator.addUpdateListener(updateYlistener);
                                moveToBoundsAnimator.playTogether(animator);
                            }
                            moveToBoundsAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
                            moveToBoundsAnimator.start();

                            if (xRelative >= 0) {
                                getRelativePosition(finallyX, finallyY, point);
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("groupcallpipconfig", Context.MODE_PRIVATE);
                                preferences.edit()
                                        .putFloat("relativeX", xRelative = point[0])
                                        .putFloat("relativeY", yRelative = point[1])
                                        .apply();
                            }
                        }
                        moving = false;
                        showRemoveTooltip(false);
                        break;
                }
                return true;
            }

            private void onTap() {
                if (VoIPService.getSharedInstance() != null) {
                    showAlert(!showAlert);
                    //performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);x
                }
            }
        };
        windowView.setAlpha(0.7f);

        button = new GroupCallPipButton(context, currentAccount, false);
        windowView.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        avatarsImageView = new AvatarsImageView(context, true);
        avatarsImageView.setStyle(5);
        avatarsImageView.setCentered(true);
        avatarsImageView.setVisibility(View.GONE);
        avatarsImageView.setDelegate(() -> updateAvatars(true));
        updateAvatars(false);
        windowView.addView(avatarsImageView, LayoutHelper.createFrame(108, 36, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        windowRemoveTooltipView = new FrameLayout(context) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                windowRemoveTooltipView.getLocationOnScreen(location);
                windowLeft = location[0];
                windowTop = location[1] - AndroidUtilities.dp(25);
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                windowRemoveTooltipOverlayView.setVisibility(visibility);
            }
        };
        removeTooltipView = new View(context) {

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                if (animateToPrepareRemove && prepareToRemoveProgress != 1f) {
                    prepareToRemoveProgress += 16 / 250f;
                    if (prepareToRemoveProgress > 1f) {
                        prepareToRemoveProgress = 1f;
                    }
                    invalidate();
                } else if (!animateToPrepareRemove && prepareToRemoveProgress != 0) {
                    prepareToRemoveProgress -= 16 / 250f;
                    if (prepareToRemoveProgress < 0) {
                        prepareToRemoveProgress = 0;
                    }
                    invalidate();
                }

                paint.setColor(ColorUtils.blendARGB(0x66050D15, 0x66350C12, prepareToRemoveProgress));
                float r = AndroidUtilities.dp(35) + AndroidUtilities.dp(5) * prepareToRemoveProgress;
                canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f - AndroidUtilities.dp(25), r, paint);
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                windowRemoveTooltipOverlayView.setAlpha(alpha);
            }

            @Override
            public void setScaleX(float scaleX) {
                super.setScaleX(scaleX);
                windowRemoveTooltipOverlayView.setScaleX(scaleX);
            }

            @Override
            public void setScaleY(float scaleY) {
                super.setScaleY(scaleY);
                windowRemoveTooltipOverlayView.setScaleY(scaleY);
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                windowRemoveTooltipOverlayView.setTranslationY(translationY);
            }
        };
        windowRemoveTooltipView.addView(removeTooltipView);

        windowRemoveTooltipOverlayView = new FrameLayout(context);
        iconView = new RLottieImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        deleteIcon = new RLottieDrawable(R.raw.group_pip_delete_icon, "" + R.raw.group_pip_delete_icon, AndroidUtilities.dp(40), AndroidUtilities.dp(40), true, null);
        deleteIcon.setPlayInDirectionOfCustomEndFrame(true);
        iconView.setAnimation(deleteIcon);
        iconView.setColorFilter(Color.WHITE);
        windowRemoveTooltipOverlayView.addView(iconView, LayoutHelper.createFrame(40, 40, Gravity.CENTER, 0, 0, 0, 25));


        alertContainer = new FrameLayout(context) {
            int lastSize = -1;
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int size = AndroidUtilities.displaySize.x + AndroidUtilities.displaySize.y;
                if (lastSize > 0 && lastSize != size) {
                    setVisibility(View.GONE);
                    showAlert = false;
                    checkButtonAlpha();
                }
                lastSize = size;
            }

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                if (visibility == View.GONE) {
                    lastSize = -1;
                }
            }
        };
        alertContainer.setOnClickListener(view -> showAlert(false));
        alertContainer.setClipChildren(false);
        alertContainer.addView(pipAlertView = new GroupCallPipAlertView(context, currentAccount), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public static boolean isShowing() {
        if (RTMPStreamPipOverlay.isVisible()) {
            return true;
        }
        if (instance != null) {
            return true;
        }
        if (!checkInlinePermissions()) {
            return false;
        }
        VoIPService service = VoIPService.getSharedInstance();
        boolean groupCall = false;
        if (service != null && service.groupCall != null && !service.isHangingUp()) {
            groupCall = true;
        }
        return groupCall && !forceRemoved && (ApplicationLoader.mainInterfaceStopped || !GroupCallActivity.groupCallUiVisible);
    }

    public static boolean onBackPressed() {
        if (instance != null && instance.showAlert) {
            instance.showAlert(false);
            return true;
        }
        return false;
    }

    private void showAlert(boolean b) {
        if (b != showAlert) {
            showAlert = b;
            alertContainer.animate().setListener(null).cancel();
            if (showAlert) {

                if (alertContainer.getVisibility() != View.VISIBLE) {
                    alertContainer.setVisibility(View.VISIBLE);
                    alertContainer.setAlpha(0);
                    pipAlertView.setScaleX(0.7f);
                    pipAlertView.setScaleY(0.7f);
                }
                alertContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        alertContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                        alertContainer.getLocationOnScreen(location);

                        float cx = windowLayoutParams.x + windowOffsetLeft + button.getMeasuredWidth() / 2f - location[0];
                        float cy = windowLayoutParams.y + windowOffsetTop + button.getMeasuredWidth() / 2f - location[1];


                        boolean canHorizontal = cy - AndroidUtilities.dp(45 + 16) > 0 && cy + AndroidUtilities.dp(45 + 16) < alertContainer.getMeasuredHeight();
                        if (cx + AndroidUtilities.dp(45 + 16) + pipAlertView.getMeasuredWidth() < alertContainer.getMeasuredWidth() - AndroidUtilities.dp(16) && canHorizontal) {
                            pipAlertView.setTranslationX(cx + AndroidUtilities.dp(45 + 16));
                            float yOffset = cy / (float) (alertContainer.getMeasuredHeight());
                            float maxOffset = AndroidUtilities.dp(40) / (float) pipAlertView.getMeasuredHeight();

                            yOffset = Math.max(maxOffset, Math.min(yOffset, 1f - maxOffset));
                            pipAlertView.setTranslationY((int) (cy - pipAlertView.getMeasuredHeight() * yOffset));
                            pipAlertView.setPosition(GroupCallPipAlertView.POSITION_LEFT, cx, cy);
                        } else if (cx - AndroidUtilities.dp(45 + 16) - pipAlertView.getMeasuredWidth() > AndroidUtilities.dp(16) && canHorizontal) {
                            float yOffset = cy / (float) alertContainer.getMeasuredHeight();
                            float maxOffset = AndroidUtilities.dp(40) / (float) pipAlertView.getMeasuredHeight();
                            yOffset = Math.max(maxOffset, Math.min(yOffset, 1f - maxOffset));

                            pipAlertView.setTranslationX((int) (cx - AndroidUtilities.dp(45 + 16) - pipAlertView.getMeasuredWidth()));
                            pipAlertView.setTranslationY((int) (cy - pipAlertView.getMeasuredHeight() * yOffset));
                            pipAlertView.setPosition(GroupCallPipAlertView.POSITION_RIGHT, cx, cy);
                        } else if (cy > alertContainer.getMeasuredHeight() * 0.3f) {
                            float xOffset = cx / (float) alertContainer.getMeasuredWidth();
                            float maxOffset = AndroidUtilities.dp(40) / (float) pipAlertView.getMeasuredWidth();
                            xOffset = Math.max(maxOffset, Math.min(xOffset, 1f - maxOffset));
                            pipAlertView.setTranslationX((int) (cx - pipAlertView.getMeasuredWidth() * xOffset));
                            pipAlertView.setTranslationY((int) (cy - pipAlertView.getMeasuredHeight() - AndroidUtilities.dp(45 + 16)));
                            pipAlertView.setPosition(GroupCallPipAlertView.POSITION_TOP, cx, cy);
                        } else {
                            float xOffset = cx / (float) alertContainer.getMeasuredWidth();
                            float maxOffset = AndroidUtilities.dp(40) / (float) pipAlertView.getMeasuredWidth();
                            xOffset = Math.max(maxOffset, Math.min(xOffset, 1f - maxOffset));
                            pipAlertView.setTranslationX((int) (cx - pipAlertView.getMeasuredWidth() * xOffset));
                            pipAlertView.setTranslationY((int) (cy + AndroidUtilities.dp(45 + 16)));
                            pipAlertView.setPosition(GroupCallPipAlertView.POSITION_BOTTOM, cx, cy);
                        }
                        return false;
                    }
                });
                alertContainer.animate().alpha(1f).setDuration(150).start();
                pipAlertView.animate().scaleX(1f).scaleY(1f).setDuration(150).start();

            } else {
                pipAlertView.animate().scaleX(0.7f).scaleY(0.7f).setDuration(150).start();
                alertContainer.animate().alpha(0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        alertContainer.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
        checkButtonAlpha();
    }

    boolean pressedState;
    boolean buttonInAlpha;

    private void checkButtonAlpha() {
        boolean alpha = pressedState || showAlert;
        if (buttonInAlpha != alpha) {
            buttonInAlpha = alpha;
            if (buttonInAlpha) {
                windowView.animate().alpha(1f).start();
            } else {
                windowView.animate().alpha(0.7f).start();
            }
            button.setPressedState(alpha);
        }
    }

    public static GroupCallPip getInstance() {
        return instance;
    }

    private void remove() {
        if (instance == null) {
            return;
        }
        removed = true;
        forceRemoved = true;
        button.removed = true;

        instance.showAlert(false);

        float cx = windowLayoutParams.x + windowView.getMeasuredWidth() / 2f;
        float cy = windowLayoutParams.y + windowView.getMeasuredHeight() / 2f;

        float cxRemove = windowLeft - windowOffsetLeft + windowRemoveTooltipView.getMeasuredWidth() / 2f;
        float cyRemove = windowTop - windowOffsetTop + windowRemoveTooltipView.getMeasuredHeight() / 2f;

        float dx = cxRemove - cx;
        float dy = cyRemove - cy;

        WindowManager windowManager = instance.windowManager;
        View windowView = instance.windowView;
        View windowRemoveTooltipView = instance.windowRemoveTooltipView;
        View windowRemoveTooltipOverlayView = instance.windowRemoveTooltipOverlayView;
        View alert = instance.alertContainer;

        onDestroy();

        instance = null;
        AnimatorSet animatorSet = new AnimatorSet();

        long moveDuration = 350;
        long additionalDuration = 0;
        if (deleteIcon.getCurrentFrame() < 33) {
            additionalDuration = (long) ((1f - deleteIcon.getCurrentFrame() / 33f) * deleteIcon.getDuration() / 2);
        }

        ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.x, windowLayoutParams.x + dx);
        animator.addUpdateListener(updateXlistener);
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.playTogether(animator);

        animator = ValueAnimator.ofFloat(windowLayoutParams.y, windowLayoutParams.y + dy - AndroidUtilities.dp(30), windowLayoutParams.y + dy);
        animator.addUpdateListener(updateYlistener);
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);

        animatorSet.playTogether(animator);
        animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.SCALE_X, windowView.getScaleX(), 0.1f).setDuration(180));
        animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.SCALE_Y, windowView.getScaleY(), 0.1f).setDuration(180));

        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(windowView, View.ALPHA, 1f, 0f);
        alphaAnimator.setStartDelay((long) (moveDuration * 0.7f));
        alphaAnimator.setDuration((long) (moveDuration * 0.3f));
        animatorSet.playTogether(alphaAnimator);

        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged), moveDuration + 20);

        moveDuration += 180 + additionalDuration;

        ObjectAnimator o = ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_X, 1f, 1.05f);
        o.setDuration(moveDuration);
        o.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        animatorSet.playTogether(o);

        o = ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_Y, 1f, 1.05f);
        o.setDuration(moveDuration);
        o.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        animatorSet.playTogether(o);

        o = ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_X, 1f, 0.3f);
        o.setStartDelay(moveDuration);
        o.setDuration(350);
        o.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.playTogether(o);

        o = ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_Y, 1f, 0.3f);
        o.setStartDelay(moveDuration);
        o.setDuration(350);
        o.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.playTogether(o);

        o = ObjectAnimator.ofFloat(removeTooltipView, View.TRANSLATION_Y, 0, AndroidUtilities.dp(60));
        o.setStartDelay(moveDuration);
        o.setDuration(350);
        o.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.playTogether(o);

        o = ObjectAnimator.ofFloat(removeTooltipView, View.ALPHA, 1f, 0);
        o.setStartDelay(moveDuration);
        o.setDuration(350);
        o.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.playTogether(o);


        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                    windowView.setVisibility(View.GONE);
                    windowRemoveTooltipView.setVisibility(View.GONE);
                    windowManager.removeView(windowView);
                    windowManager.removeView(windowRemoveTooltipView);
                    windowManager.removeView(windowRemoveTooltipOverlayView);
                    windowManager.removeView(alert);
                });
            }
        });
        animatorSet.start();
        deleteIcon.setCustomEndFrame(66);
        iconView.stopAnimation();
        iconView.playAnimation();
    }

    private void updateAvatars(boolean animated) {
        if (avatarsImageView.avatarsDrawable.transitionProgressAnimator == null) {
            ChatObject.Call call;

            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService != null) {
                call = voIPService.groupCall;
            } else {
                call = null;
            }
            if (call != null) {
                long selfId = voIPService.getSelfId();
                for (int a = 0, N = call.sortedParticipants.size(), k = 0; k < 2; a++) {
                    if (a < N) {
                        TLRPC.GroupCallParticipant participant = call.sortedParticipants.get(a);
                        if (MessageObject.getPeerId(participant.peer) == selfId || (SystemClock.uptimeMillis() - participant.lastSpeakTime > 500)) {
                            continue;
                        }
                        avatarsImageView.setObject(k, currentAccount, participant);
                        k++;
                    } else {
                        avatarsImageView.setObject(k, currentAccount, null);
                        k++;
                    }
                }
                avatarsImageView.setObject(2, currentAccount, null);
                avatarsImageView.commitTransition(animated);
            } else {
                for (int a = 0; a < 3; a++) {
                    avatarsImageView.setObject(a, currentAccount, null);
                }
                avatarsImageView.commitTransition(animated);
            }
        } else {
            avatarsImageView.updateAfterTransitionEnd();
        }
    }

    public static void show(Context context, int account) {
        if (instance != null) {
            return;
        }
        instance = new GroupCallPip(context, account);
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        instance.windowManager = wm;


        WindowManager.LayoutParams windowLayoutParams = createWindowLayoutParams(context);
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.dimAmount = 0.25f;
        windowLayoutParams.flags = FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_DIM_BEHIND;

        wm.addView(instance.alertContainer, windowLayoutParams);
        instance.alertContainer.setVisibility(View.GONE);

        windowLayoutParams = createWindowLayoutParams(context);
        windowLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        windowLayoutParams.width = AndroidUtilities.dp(100);
        windowLayoutParams.height = AndroidUtilities.dp(150);
        wm.addView(instance.windowRemoveTooltipView, windowLayoutParams);

        windowLayoutParams = createWindowLayoutParams(context);
        instance.windowLayoutParams = windowLayoutParams;
        wm.addView(instance.windowView, windowLayoutParams);

        windowLayoutParams = createWindowLayoutParams(context);
        windowLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        windowLayoutParams.width = AndroidUtilities.dp(100);
        windowLayoutParams.height = AndroidUtilities.dp(150);
        wm.addView(instance.windowRemoveTooltipOverlayView, windowLayoutParams);

        instance.windowRemoveTooltipView.setVisibility(View.GONE);

        instance.windowView.setScaleX(0.5f);
        instance.windowView.setScaleY(0.5f);
        instance.windowView.setAlpha(0f);
        instance.windowView.animate().alpha(0.7f).scaleY(1f).scaleX(1f).setDuration(350).setInterpolator(new OvershootInterpolator()).start();

        NotificationCenter.getInstance(instance.currentAccount).addObserver(instance, NotificationCenter.groupCallUpdated);
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.webRtcSpeakerAmplitudeEvent);
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.didEndCall);
    }

    private void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.groupCallVisibilityChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
    }

    private void setPosition(float xRelative, float yRelative) {
        float paddingHorizontal = -AndroidUtilities.dp(36);
        float w = AndroidUtilities.displaySize.x - paddingHorizontal * 2;

        windowLayoutParams.x = (int) (paddingHorizontal + (w - AndroidUtilities.dp(105)) * xRelative);
        windowLayoutParams.y = (int) ((AndroidUtilities.displaySize.y - AndroidUtilities.dp(105)) * yRelative);
        updateAvatarsPosition();
        if (windowView.getParent() != null) {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        }
    }


    public static void finish() {
        if (instance != null) {
            instance.showAlert(false);
            WindowManager windowManager = instance.windowManager;
            View windowView = instance.windowView;
            View windowRemoveTooltipView = instance.windowRemoveTooltipView;
            View windowRemoveTooltipOverlayView = instance.windowRemoveTooltipOverlayView;
            View alert = instance.alertContainer;
            instance.windowView.animate().scaleX(0.5f).scaleY(0.5f).alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (windowView.getParent() != null) {
                        windowView.setVisibility(View.GONE);
                        windowRemoveTooltipView.setVisibility(View.GONE);
                        windowRemoveTooltipOverlayView.setVisibility(View.GONE);
                        windowManager.removeView(windowView);
                        windowManager.removeView(windowRemoveTooltipView);
                        windowManager.removeView(windowRemoveTooltipOverlayView);
                        windowManager.removeView(alert);
                    }
                }
            }).start();

            instance.onDestroy();
            instance = null;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged);
        }
    }

    private static WindowManager.LayoutParams createWindowLayoutParams(Context context) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();

        windowLayoutParams.height = AndroidUtilities.dp(105);
        windowLayoutParams.width = AndroidUtilities.dp(105);

        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;

        if (AndroidUtilities.checkInlinePermissions(context)) {
            if (Build.VERSION.SDK_INT >= 26) {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        }

        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return windowLayoutParams;
    }


    AnimatorSet showRemoveAnimator;

    void showRemoveTooltip(boolean show) {
        if (animateToShowRemoveTooltip != show) {
            animateToShowRemoveTooltip = show;
            if (showRemoveAnimator != null) {
                showRemoveAnimator.removeAllListeners();
                showRemoveAnimator.cancel();
            }
            if (show) {
                if (windowRemoveTooltipView.getVisibility() != View.VISIBLE) {
                    windowRemoveTooltipView.setVisibility(View.VISIBLE);
                    removeTooltipView.setAlpha(0);
                    removeTooltipView.setScaleX(0.5f);
                    removeTooltipView.setScaleY(0.5f);
                    deleteIcon.setCurrentFrame(0);
                }
                showRemoveAnimator = new AnimatorSet();
                showRemoveAnimator.playTogether(
                        ObjectAnimator.ofFloat(removeTooltipView, View.ALPHA, removeTooltipView.getAlpha(), 1f),
                        ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_X, removeTooltipView.getScaleX(), 1f),
                        ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_Y, removeTooltipView.getScaleY(), 1f)
                );
                showRemoveAnimator.setDuration(150).start();
            } else {
                showRemoveAnimator = new AnimatorSet();
                showRemoveAnimator.playTogether(
                        ObjectAnimator.ofFloat(removeTooltipView, View.ALPHA, removeTooltipView.getAlpha(), 0f),
                        ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_X, removeTooltipView.getScaleX(), 0.5f),
                        ObjectAnimator.ofFloat(removeTooltipView, View.SCALE_Y, removeTooltipView.getScaleY(), 0.5f)
                );
                showRemoveAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        windowRemoveTooltipView.setVisibility(View.GONE);
                        animateToPrepareRemove = false;
                        prepareToRemoveProgress = 0f;
                    }
                });
                showRemoveAnimator.setDuration(150);
                showRemoveAnimator.start();
            }
        }
    }

    void prepareToRemove(boolean prepare) {
        if (animateToPrepareRemove != prepare) {
            animateToPrepareRemove = prepare;
            removeTooltipView.invalidate();

            if (!removed) {
                deleteIcon.setCustomEndFrame(prepare ? 33 : 0);
                iconView.playAnimation();
            }
            if (prepare) {
                try {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}
            }
        }
        button.prepareToRemove(prepare);
    }

    boolean animateToPinnedToCenter = false;
    float pinnedProgress = 0f;
    ValueAnimator pinAnimator;

    void pinnedToCenter(boolean pinned) {
        if (removed) {
            return;
        }
        if (animateToPinnedToCenter != pinned) {
            animateToPinnedToCenter = pinned;
            if (pinAnimator != null) {
                pinAnimator.removeAllListeners();
                pinAnimator.cancel();
            }
            pinAnimator = ValueAnimator.ofFloat(pinnedProgress, pinned ? 1f : 0);
            pinAnimator.addUpdateListener(valueAnimator -> {
                if (removed) {
                    return;
                }
                pinnedProgress = (float) valueAnimator.getAnimatedValue();
                button.setPinnedProgress(pinnedProgress);
                windowView.setScaleX(1f - 0.6f * pinnedProgress);
                windowView.setScaleY(1f - 0.6f * pinnedProgress);
                if (moving) {
                    updateButtonPosition();
                }
            });
            pinAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (removed) {
                        return;
                    }
                    pinnedProgress = pinned ? 1f : 0f;
                    button.setPinnedProgress(pinnedProgress);
                    windowView.setScaleX(1f - 0.6f * pinnedProgress);
                    windowView.setScaleY(1f - 0.6f * pinnedProgress);
                    if (moving) {
                        updateButtonPosition();
                    }
                }
            });
            pinAnimator.setDuration(250);
            pinAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            pinAnimator.start();
        }
    }

    private void updateButtonPosition() {
        float cxRemove = windowLeft - windowOffsetLeft + windowRemoveTooltipView.getMeasuredWidth() / 2f - windowView.getMeasuredWidth() / 2f;
        float cyRemove = windowTop - windowOffsetTop + windowRemoveTooltipView.getMeasuredHeight() / 2f - windowView.getMeasuredHeight() / 2f - AndroidUtilities.dp(25);

        windowLayoutParams.x = (int) (windowX * (1f - pinnedProgress) + cxRemove * pinnedProgress);
        windowLayoutParams.y = (int) (windowY * (1f - pinnedProgress) + cyRemove * pinnedProgress);

        updateAvatarsPosition();
        if (windowView.getParent() != null) {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        }
    }

    private void updateAvatarsPosition() {
        float x = Math.max(windowLayoutParams.x, -AndroidUtilities.dp(36));
        int parentWidth = AndroidUtilities.displaySize.x;
        x = Math.min(x, (parentWidth - windowView.getMeasuredWidth() + AndroidUtilities.dp(36)));
        if (x < 0) {
            avatarsImageView.setTranslationX(Math.abs(x) / 3f);
        } else if (x > parentWidth - windowView.getMeasuredWidth()) {
            avatarsImageView.setTranslationX(- Math.abs(x - (parentWidth - windowView.getMeasuredWidth())) / 3f);
        } else {
            avatarsImageView.setTranslationX(0);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupCallUpdated || id == NotificationCenter.webRtcSpeakerAmplitudeEvent) {
            updateAvatars(true);
        } else if (id == NotificationCenter.didEndCall) {
            updateVisibility(ApplicationLoader.applicationContext);
        }
    }

    private void getRelativePosition(float x, float y, float[] point) {
        float width = AndroidUtilities.displaySize.x;
        float height = AndroidUtilities.displaySize.y;

        float paddingHorizontal = -AndroidUtilities.dp(36);

        point[0] = (x - paddingHorizontal) / (width - 2 * paddingHorizontal - AndroidUtilities.dp(105));
        point[1] = y / (height - AndroidUtilities.dp(105));
        point[0] = Math.min(1f, Math.max(0, point[0]));
        point[1] = Math.min(1f, Math.max(0, point[1]));
    }

    public static void updateVisibility(Context context) {
        VoIPService service = VoIPService.getSharedInstance();

        boolean groupCall = false;
        if (service != null && service.groupCall != null && !service.isHangingUp()) {
            groupCall = true;
        }
        boolean visible;
        if (!AndroidUtilities.checkInlinePermissions(ApplicationLoader.applicationContext)) {
            visible = false;
        } else {
            visible = groupCall && !forceRemoved && (ApplicationLoader.mainInterfaceStopped || !GroupCallActivity.groupCallUiVisible);
        }
        if (visible) {
            show(context, service.getAccount());
            instance.showAvatars(true);
        } else {
            finish();
        }
    }

    private void showAvatars(boolean show) {
        boolean isShowing = avatarsImageView.getTag() != null;
        if (show != isShowing) {
            avatarsImageView.animate().setListener(null).cancel();
            if (show) {
                if (avatarsImageView.getVisibility() != View.VISIBLE) {
                    avatarsImageView.setVisibility(View.VISIBLE);
                    avatarsImageView.setAlpha(0f);
                    avatarsImageView.setScaleX(0.5f);
                    avatarsImageView.setScaleY(0.5f);
                }
                avatarsImageView.animate().alpha(1f).scaleX(1).scaleY(1f).setDuration(150).start();
            } else {
                avatarsImageView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        avatarsImageView.setVisibility(View.GONE);
                    }
                }).start();
            }
            avatarsImageView.setTag(show ? 1 : null);
        }
    }

    public static void clearForce() {
        forceRemoved = false;
    }

    public static boolean checkInlinePermissions() {
        if (Build.VERSION.SDK_INT < 23 || ApplicationLoader.canDrawOverlays) {
            return true;
        }
        return false;
    }
}
