package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ChatListItemAnimator;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;

public class AdjustPanLayoutHelper {

    public static boolean USE_ANDROID11_INSET_ANIMATOR = false;
    private boolean useInsetsAnimator;

    public final static Interpolator keyboardInterpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR;
    public final static long keyboardDuration = 250;

    private final View parent;
    private View resizableViewToSet;

    private ViewGroup contentView;
    private View resizableView;
    private boolean usingInsetAnimator = false;
    private boolean animationInProgress;
    private boolean needDelay;
    private Runnable delayedAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            if (animator != null && !animator.isRunning()) {
                animator.start();
            }
        }
    };

    public View getAdjustingParent() {
        return parent;
    }

    public View getAdjustingContentView() {
        return contentView;
    }

    int previousHeight = -1;
    int previousContentHeight = -1;
    int previousStartOffset = -1;

    View parentForListener;
    ValueAnimator animator;

    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    ArrayList<View> viewsToHeightSet = new ArrayList<>();
    protected float keyboardSize;

    boolean checkHierarchyHeight;

    float from, to;
    boolean inverse;
    boolean isKeyboardVisible;
    long startAfter;

    ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            int contentHeight = parent.getHeight();
            if (contentHeight - startOffset() == previousHeight - previousStartOffset || contentHeight == previousHeight || animator != null) {
                if (animator == null) {
                    previousHeight = contentHeight;
                    previousContentHeight = contentView.getHeight();
                    previousStartOffset = startOffset();
                    usingInsetAnimator = false;
                }
                return true;
            }

            if (!heightAnimationEnabled() || Math.abs(previousHeight - contentHeight) < AndroidUtilities.dp(20)) {
                previousHeight = contentHeight;
                previousContentHeight = contentView.getHeight();
                previousStartOffset = startOffset();
                usingInsetAnimator = false;
                return true;
            }

            if (previousHeight != -1 && previousContentHeight == contentView.getHeight()) {
                isKeyboardVisible = contentHeight < contentView.getBottom();
                animateHeight(previousHeight, contentHeight, isKeyboardVisible);
                previousHeight = contentHeight;
                previousContentHeight = contentView.getHeight();
                previousStartOffset = startOffset();
                return false;
            }

            previousHeight = contentHeight;
            previousContentHeight = contentView.getHeight();
            previousStartOffset = startOffset();
            return false;
        }
    };

    private void animateHeight(int previousHeight, int contentHeight, boolean isKeyboardVisible) {
        if (ignoreOnce) {
            ignoreOnce = false;
            return;
        }
        if (!enabled) {
            return;
        }
        startTransition(previousHeight, contentHeight, isKeyboardVisible);
        animator.addUpdateListener(animation -> {
            if (!usingInsetAnimator) {
                updateTransition((float) animation.getAnimatedValue());
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!usingInsetAnimator) {
                    stopTransition();
                }
            }
        });
        animator.setDuration(keyboardDuration);
        animator.setInterpolator(keyboardInterpolator);

        notificationsLocker.lock();
        if (needDelay) {
            needDelay = false;
            startAfter = SystemClock.elapsedRealtime() + 100;
            AndroidUtilities.runOnUIThread(delayedAnimationRunnable, 100);
        } else {
            animator.start();
            startAfter = -1;
        }
    }

    public boolean showingKeyboard;
    public void startTransition(int previousHeight, int contentHeight, boolean isKeyboardVisible) {
        if (animator != null) {
            animator.cancel();
        }

        int startOffset = startOffset();
        getViewsToSetHeight(parent);
        int additionalContentHeight = 0;
        if (checkHierarchyHeight) {
            ViewParent viewParent = parent.getParent();
            if (viewParent instanceof View) {
                additionalContentHeight = ((View) viewParent).getHeight() - contentHeight;
            }
        }
        int bottomTabsHeight = 0;
        if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null) {
            bottomTabsHeight += LaunchActivity.instance.getBottomSheetTabs().getExpandedHeight();
        }
        if (applyTranslation()) setViewHeight(Math.max(previousHeight, contentHeight + additionalContentHeight + bottomTabsHeight));
        resizableView.requestLayout();

        onTransitionStart(isKeyboardVisible, previousHeight, contentHeight);

        float dy = contentHeight - previousHeight;
        keyboardSize = Math.abs(dy);

        animationInProgress = true;
        showingKeyboard = contentHeight <= previousHeight;
        if (contentHeight > previousHeight) {
            dy -= startOffset;
            if (applyTranslation()) parent.setTranslationY(-dy);
            onPanTranslationUpdate(dy, 1f, isKeyboardVisible);
            from = -dy;
            to = -bottomTabsHeight;
            inverse = true;
        } else {
            if (applyTranslation()) parent.setTranslationY(previousStartOffset);
            onPanTranslationUpdate(-previousStartOffset, 0f, isKeyboardVisible);
            to = -previousStartOffset;
            from = dy;
            inverse = false;
        }
        animator = ValueAnimator.ofFloat(0, 1);
        usingInsetAnimator = false;
    }

    public void updateTransition(float t) {
        if (inverse) {
            t = 1f - t;
        }
        float y = (int) (from * t + to * (1f - t));
        if (applyTranslation()) parent.setTranslationY(y);
        onPanTranslationUpdate(-y, t, isKeyboardVisible);
    }

    public void stopTransition() {
        if (animator != null) {
            animator.cancel();
        }
        animationInProgress = false;
        usingInsetAnimator = false;
        notificationsLocker.unlock();
        animator = null;
        setViewHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        viewsToHeightSet.clear();
        resizableView.requestLayout();
        onPanTranslationUpdate(0, isKeyboardVisible ? 1f : 0f, isKeyboardVisible);
        if (applyTranslation()) parent.setTranslationY(0);
        onTransitionEnd();
    }
    public void stopTransition(float t, boolean isKeyboardVisible) {
        if (animator != null) {
            animator.cancel();
        }
        animationInProgress = false;
        notificationsLocker.unlock();
        animator = null;
        setViewHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        viewsToHeightSet.clear();
        resizableView.requestLayout();
        onPanTranslationUpdate(0, t, this.isKeyboardVisible = isKeyboardVisible);
        if (applyTranslation()) parent.setTranslationY(0);
        onTransitionEnd();
    }

    public void setViewHeight(int height) {
        for (int i = 0; i < viewsToHeightSet.size(); i++) {
            viewsToHeightSet.get(i).getLayoutParams().height = height;
            viewsToHeightSet.get(i).requestLayout();
        }
    }

    protected int startOffset() {
        return 0;
    }

    public void getViewsToSetHeight(View parent) {
        viewsToHeightSet.clear();
        View v = parent;
        while (v != null) {
            viewsToHeightSet.add(v);
            if (v == resizableView) {
                return;
            }
            if (v.getParent() instanceof View) {
                v = (View) v.getParent();
            } else {
                v = null;
            }
        }
    }

    public AdjustPanLayoutHelper(View parent) {
        this(parent, USE_ANDROID11_INSET_ANIMATOR);
    }

    public AdjustPanLayoutHelper(View parent, boolean useInsetsAnimator) {
        this.useInsetsAnimator = useInsetsAnimator;
        this.parent = parent;
        AndroidUtilities.runOnUIThread(this::onAttach);
    }

    public void onAttach() {
        onDetach();
        Context context = parent.getContext();
        Activity activity = getActivity(context);
        if (activity != null) {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            contentView = decorView.findViewById(Window.ID_ANDROID_CONTENT);
        }
        resizableView = findResizableView(parent);
        if (resizableView != null) {
            parentForListener = resizableView;
            resizableView.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
        }
        if (useInsetsAnimator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setupNewCallback();
        }
    }

    private Activity getActivity(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextThemeWrapper) {
            return getActivity(((ContextThemeWrapper) context).getBaseContext());
        }
        return null;
    }

    private View findResizableView(View parent) {
        if (resizableViewToSet != null) {
            return resizableViewToSet;
        }
        View view = parent;
        while (view != null) {
            if (view.getParent() instanceof DrawerLayoutContainer) {
                return view;
            }
            if (view.getParent() instanceof View) {
                view = (View) view.getParent();
            } else {
                return null;
            }
        }
        return null;
    }

    public void onDetach() {
        if (animator != null) {
            animator.cancel();
        }
        if (parentForListener != null) {
            parentForListener.getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
            parentForListener = null;
        }
        if (parent != null && useInsetsAnimator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            parent.setWindowInsetsAnimationCallback(null);
        }
    }

    private boolean enabled = true;
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    private boolean ignoreOnce;
    public void ignoreOnce() {
        ignoreOnce = true;
    }

    protected boolean heightAnimationEnabled() {
        return true;
    }
    protected boolean applyTranslation() {
        return true;
    }

    public void OnPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
        onPanTranslationUpdate(y, progress, keyboardVisible);
    }
    public void OnTransitionStart(boolean keyboardVisible, int contentHeight) {
        onTransitionStart(keyboardVisible, contentHeight);
    }
    public void OnTransitionEnd() {
        onTransitionEnd();
    }

    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {

    }

    protected void onTransitionStart(boolean keyboardVisible, int previousHeight, int contentHeight) {
        onTransitionStart(keyboardVisible, contentHeight);
    }

    protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {

    }

    protected void onTransitionEnd() {

    }

    public void setResizableView(FrameLayout windowView) {
        resizableViewToSet = windowView;
    }

    public boolean animationInProgress() {
        return animationInProgress;
    }

    public void setCheckHierarchyHeight(boolean checkHierarchyHeight) {
        this.checkHierarchyHeight = checkHierarchyHeight;
    }

    public void delayAnimation() {
       needDelay = true;
    }

    public void runDelayedAnimation() {
        AndroidUtilities.cancelRunOnUIThread(delayedAnimationRunnable);
        delayedAnimationRunnable.run();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void setupNewCallback() {
        if (resizableView == null) {
            return;
        }
        resizableView.setWindowInsetsAnimationCallback(
            new WindowInsetsAnimation.Callback(WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                @NonNull
                @Override
                public WindowInsets onProgress(@NonNull WindowInsets insets, @NonNull List<WindowInsetsAnimation> runningAnimations) {
                    if (!animationInProgress || AndroidUtilities.screenRefreshRate < 90) {
                        return insets;
                    }

                    WindowInsetsAnimation imeAnimation = null;
                    for (WindowInsetsAnimation animation : runningAnimations) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            imeAnimation = animation;
                            break;
                        }
                    }

                    if (imeAnimation != null && SystemClock.elapsedRealtime() >= startAfter) {
                        usingInsetAnimator = true;
                        updateTransition((float) imeAnimation.getInterpolatedFraction());
                    }
                    return insets;
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimation animation) {
                    if (!animationInProgress || AndroidUtilities.screenRefreshRate < 90) {
                        return;
                    }
                    stopTransition();
                }
            }
        );
    }
}
