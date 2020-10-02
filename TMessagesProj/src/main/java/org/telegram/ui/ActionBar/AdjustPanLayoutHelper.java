package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

public class AdjustPanLayoutHelper {

    private final static float[] interpolatorValues = new float[]{0f, 0.11162791f, 0.40232557f, 0.6534884f, 0.7930232f, 0.8802326f, 0.9302326f, 0.96046513f, 0.9767442f, 0.9860465f, 0.99186045f, 0.9953488f, 0.9976744f, 0.99883723f, 1};
    public final static Interpolator keyboardInterpolator = CubicBezierInterpolator.DEFAULT;//new LookupTableInterpolator(interpolatorValues);
    public final static long keyboardDuration = 250;

    private final View parent;
    private View resizableViewToSet;

    private ViewGroup decorView;
    private ViewGroup contentView;
    private View resizableView;
    private boolean animationInProgress;

    int previousHeight = -1;
    int previousContentHeight = -1;
    int previousStartOffset = -1;

    View parentForListener;
    ValueAnimator animator;

    int notificationsIndex;

    ArrayList<View> viewsToHeightSet = new ArrayList<>();
    protected float keyboardSize;

    ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (!SharedConfig.smoothKeyboard) {
                onDetach();
                return true;
            }
            int contentHeight = parent.getHeight();
            if (contentHeight - startOffset() == previousHeight - previousStartOffset || contentHeight == previousHeight || animator != null) {
                if (animator == null) {
                    previousHeight = contentHeight;
                    previousContentHeight = contentView.getHeight();
                    previousStartOffset = startOffset();
                }
                return true;
            }

            if (!heightAnimationEnabled() || Math.abs(previousHeight - contentHeight) < AndroidUtilities.dp(20)) {
                previousHeight = contentHeight;
                previousContentHeight = contentView.getHeight();
                previousStartOffset = startOffset();
                return true;
            }

            if (previousHeight != -1 && previousContentHeight == contentView.getHeight()) {
                boolean isKeyboardVisible = contentHeight < contentView.getBottom();
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
        if (animator != null) {
            animator.cancel();
        }

        int startOffset = startOffset();
        getViewsToSetHeight(parent);
        setViewHeight(Math.max(previousHeight, contentHeight));
        resizableView.requestLayout();

        onTransitionStart(isKeyboardVisible);

        float dy = contentHeight - previousHeight;
        float from;
        float to;
        keyboardSize = Math.abs(dy);

        if (contentHeight > previousHeight) {
            dy -= startOffset;
            parent.setTranslationY(-dy);
            onPanTranslationUpdate(dy, 1f, isKeyboardVisible);
            from = -dy;
            to = 0;
            animator = ValueAnimator.ofFloat(1f, 0);
        } else {
            parent.setTranslationY(previousStartOffset);
            onPanTranslationUpdate(-previousStartOffset, 0f, isKeyboardVisible);
            to = -previousStartOffset;
            from = dy;
            animator = ValueAnimator.ofFloat(0, 1f);
        }
        animator.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            float y = from * v + to * (1f - v);
            parent.setTranslationY(y);
            onPanTranslationUpdate(-y, v, isKeyboardVisible);
        });
        animationInProgress = true;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animationInProgress = false;
                NotificationCenter.getInstance(UserConfig.selectedAccount).onAnimationFinish(notificationsIndex);
                animator = null;
                setViewHeight(ViewGroup.LayoutParams.MATCH_PARENT);
                viewsToHeightSet.clear();
                resizableView.requestLayout();
                onPanTranslationUpdate(0, isKeyboardVisible ? 1f : 0f, isKeyboardVisible);
                parent.setTranslationY(0);
                onTransitionEnd();
            }
        });
        animator.setDuration(220);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);

        notificationsIndex = NotificationCenter.getInstance(UserConfig.selectedAccount).setAnimationInProgress(notificationsIndex, null);
        animator.start();
    }

    private void setViewHeight(int height) {
        for (int i = 0; i < viewsToHeightSet.size(); i++) {
            viewsToHeightSet.get(i).getLayoutParams().height = height;
            viewsToHeightSet.get(i).requestLayout();
        }
    }

    protected int startOffset() {
        return 0;
    }

    private void getViewsToSetHeight(View parent) {
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
        this.parent = parent;
        onAttach();
    }

    public void onAttach() {
        if (!SharedConfig.smoothKeyboard) {
            return;
        }
        onDetach();
        Context context = parent.getContext();
        Activity activity = getActivity(context);
        if (activity != null) {
            decorView = (android.view.ViewGroup) activity.getWindow().getDecorView();
            contentView = decorView.findViewById(Window.ID_ANDROID_CONTENT);
        }
        resizableView = findResizableView(parent);
        if (resizableView != null) {
            parentForListener = resizableView;
            resizableView.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
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
    }

    protected boolean heightAnimationEnabled() {
        return true;
    }

    protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {

    }

    protected void onTransitionStart(boolean keyboardVisible) {

    }

    protected void onTransitionEnd() {

    }


    public void setResizableView(FrameLayout windowView) {
        resizableViewToSet = windowView;
    }

    public boolean animationInProgress() {
        return animationInProgress;
    }

    /**
     * copy from androidx.interpolator.view.animation.LookupTableInterpolator
     */
    static class LookupTableInterpolator implements Interpolator {

        private final float[] mValues;
        private final float mStepSize;

        protected LookupTableInterpolator(float[] values) {
            mValues = values;
            mStepSize = 1f / (mValues.length - 1);
        }

        @Override
        public float getInterpolation(float input) {
            if (input >= 1.0f) {
                return 1.0f;
            }
            if (input <= 0f) {
                return 0f;
            }

            // Calculate index - We use min with length - 2 to avoid IndexOutOfBoundsException when
            // we lerp (linearly interpolate) in the return statement
            int position = Math.min((int) (input * (mValues.length - 1)), mValues.length - 2);

            // Calculate values to account for small offsets as the lookup table has discrete values
            float quantized = position * mStepSize;
            float diff = input - quantized;
            float weight = diff / mStepSize;

            // Linearly interpolate between the table values
            return mValues[position] + weight * (mValues[position + 1] - mValues[position]);
        }
    }
}
