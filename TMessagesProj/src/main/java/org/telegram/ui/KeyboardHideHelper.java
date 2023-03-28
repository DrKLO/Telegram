package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Insets;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowInsetsAnimationControlListener;
import android.view.WindowInsetsAnimationController;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;

public class KeyboardHideHelper {

    public static boolean ENABLED = false;

    public KeyboardHideHelper() {

    }

    private View view, enterView;
    private AdjustPanLayoutHelper panLayoutHelper;
    private WindowInsetsAnimationController insetsController;
    private boolean isKeyboard = false;
    private boolean movingKeyboard = false, exactlyMovingKeyboard = false;
    private boolean endingMovingKeyboard = false;
    private boolean startedOutsideView = false;
    private boolean startedAtBottom = false;
    private VelocityTracker tracker;
    private float rawT, lastT, lastDifferentT, t;
    private float fromY;
    private int keyboardSize, bottomNavBarSize;

    public boolean onTouch(AdjustPanLayoutHelper panLayoutHelper, View view, RecyclerListView listView, ChatActivityEnterView enterView, ChatActivity ca, MotionEvent ev) {
        if (!ENABLED) {
            return false;
        }
        this.panLayoutHelper = panLayoutHelper;
        this.view = view;
        this.enterView = enterView;

        if (view == null || enterView == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean isKeyboardVisible = view.getRootWindowInsets().getInsets(WindowInsetsCompat.Type.ime()).bottom > 0;
            if (!movingKeyboard && !isKeyboardVisible && !endingMovingKeyboard /* && !enterView.isPopupShowing()*/) {
                return false;
            }
            boolean insideEnterView = ev.getY() >= enterView.getTop();
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                startedOutsideView = !insideEnterView;
                startedAtBottom = !listView.canScrollVertically(1);
            }
            if (!movingKeyboard && insideEnterView && startedOutsideView && ev.getAction() == MotionEvent.ACTION_MOVE) {
                movingKeyboard = true;
                isKeyboard = !enterView.isPopupShowing();
                keyboardSize = (
                    isKeyboard ?
                        view.getRootWindowInsets().getInsets(WindowInsetsCompat.Type.ime()).bottom :
                        enterView.getEmojiPadding()
                );
                bottomNavBarSize = view.getRootWindowInsets().getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                view.getWindowInsetsController().controlWindowInsetsAnimation(
                    WindowInsetsCompat.Type.ime(),
                    -1,
                    new LinearInterpolator(),
                    new CancellationSignal(),
                    new WindowInsetsAnimationControlListener() {
                        @Override
                        public void onReady(@NonNull WindowInsetsAnimationController windowInsetsAnimationController, int i) {
                            insetsController = windowInsetsAnimationController;
                        }

                        @Override
                        public void onFinished(@NonNull WindowInsetsAnimationController windowInsetsAnimationController) {
                            insetsController = null;
                        }

                        @Override
                        public void onCancelled(@Nullable WindowInsetsAnimationController windowInsetsAnimationController) {
                            insetsController = null;
                        }
                    }
                );
                fromY = ev.getRawY();
                exactlyMovingKeyboard = false;
                panLayoutHelper.setEnabled(false);
                update(0, false);
                listView.stopScroll();
                t = rawT = lastT = lastDifferentT = 0;
                panLayoutHelper.OnTransitionStart(true, view.getHeight());
                if (tracker == null) {
                    tracker = VelocityTracker.obtain();
                }
                tracker.clear();
            }

            if (movingKeyboard) {
                tracker.addMovement(ev);
                t = MathUtils.clamp(rawT = (ev.getRawY() - fromY) / keyboardSize, 0, 1);
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    movingKeyboard = false;
                    exactlyMovingKeyboard = false;
                    endingMovingKeyboard = true;
                    tracker.computeCurrentVelocity(1000);
                    final boolean end = t > .15f && t >= lastDifferentT || t > .8f;
                    final float endT = end ? 1 : 0;
                    ValueAnimator va = ValueAnimator.ofFloat(t, endT);
                    va.addUpdateListener(a -> {
                        update(t = (float) a.getAnimatedValue(), true);
                    });
                    va.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (insetsController != null && isKeyboard) {
                                insetsController.finish(!end);
                            }
                            update(1, false);
                            rawT = endT;
                            panLayoutHelper.OnTransitionEnd();
                            view.post(() -> {
                                panLayoutHelper.setEnabled(true);
                                endingMovingKeyboard = false;
                            });
                        }
                    });
                    va.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    va.setDuration(200);
                    va.start();
                    if (end && startedAtBottom && ca != null) {
                        ca.scrollToLastMessage(true, false);
                    }
                    startedOutsideView = false;
                    return true;
                }
//                if (t > .15f && !exactlyMovingKeyboard) {
//                    exactlyMovingKeyboard = true;
//                }
//                if (exactlyMovingKeyboard) {
                    update(t, true);
//                }
                if (lastT != t) {
                    lastDifferentT = lastT;
                }
                lastT = t;
                return true;
            }
        }

        return false;
    }

    public boolean disableScrolling() {
        return ENABLED && (movingKeyboard || endingMovingKeyboard) && rawT >= 0;
    }

    private void update(float t, boolean withKeyboard) {
        if (isKeyboard) {
            float y = Math.max((1f - t) * keyboardSize - bottomNavBarSize - 1, 0);
            panLayoutHelper.OnPanTranslationUpdate(y, t, true);
            ((View) ((View) view.getParent()).getParent()).setTranslationY(-y);
            if (withKeyboard) {
                if (insetsController != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insetsController.setInsetsAndAlpha(Insets.of(0, 0, 0, (int) (keyboardSize * (1f - t))), 1f, t);
                }
            }
        } else {
            float y = (1f - t) * keyboardSize;
            panLayoutHelper.OnPanTranslationUpdate(y, t, true);
//            ((View) ((View) view.getParent()).getParent()).setTranslationY(-y);
        }
    }
}
