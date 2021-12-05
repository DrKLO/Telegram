package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class VoIPNotificationsLayout extends LinearLayout {

    HashMap<String, NotificationView> viewsByTag = new HashMap<>();
    ArrayList<NotificationView> viewToAdd = new ArrayList<>();
    ArrayList<NotificationView> viewToRemove = new ArrayList<>();
    TransitionSet transitionSet;
    boolean lockAnimation;
    boolean wasChanged;
    Runnable onViewsUpdated;

    public VoIPNotificationsLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            transitionSet = new TransitionSet();
            transitionSet.addTransition(new Fade(Fade.OUT).setDuration(150))
                    .addTransition(new ChangeBounds().setDuration(200))
                    .addTransition(new Visibility() {
                        @Override
                        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                            AnimatorSet set = new AnimatorSet();
                            view.setAlpha(0);
                            set.playTogether(
                                    ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getMeasuredHeight(), 0)
                            );

                            set.setInterpolator(CubicBezierInterpolator.DEFAULT);

                            return set;
                        }
                    }.setDuration(200));
            transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        }
    }

    public void addNotification(int iconRes, String text, String tag, boolean animated) {
        if (viewsByTag.get(tag) != null) {
            return;
        }

        NotificationView view = new NotificationView(getContext());
        view.tag = tag;
        view.iconView.setImageResource(iconRes);
        view.textView.setText(text);
        viewsByTag.put(tag, view);

        if (animated) {
            view.startAnimation();
        }
        if (lockAnimation) {
            viewToAdd.add(view);
        } else {
            wasChanged = true;
            addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4));
        }
    }

    public void removeNotification(String tag) {
        NotificationView view = viewsByTag.remove(tag);
        if (view != null) {
            if (lockAnimation) {
                if (viewToAdd.remove(view)) {
                    return;
                }
                viewToRemove.add(view);
            } else {
                wasChanged = true;
                removeView(view);
            }
        }
    }

    private void lock() {
        lockAnimation = true;
        AndroidUtilities.runOnUIThread(() -> {
            lockAnimation = false;
            runDelayed();
        }, 700);
    }

    private void runDelayed() {
        if (viewToAdd.isEmpty() && viewToRemove.isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ViewParent parent = getParent();
            if (parent != null) {
                TransitionManager.beginDelayedTransition(this, transitionSet);
            }
        }

        for (int i = 0; i < viewToAdd.size(); i++) {
            NotificationView view = viewToAdd.get(i);
            for (int j = 0; j < viewToRemove.size(); j++) {
                if (view.tag.equals(viewToRemove.get(j).tag)) {
                    viewToAdd.remove(i);
                    viewToRemove.remove(j);
                    i--;
                    break;
                }
            }
        }

        for (int i = 0; i < viewToAdd.size(); i++) {
            addView(viewToAdd.get(i), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 0, 4));
        }
        for (int i = 0; i < viewToRemove.size(); i++) {
            removeView(viewToRemove.get(i));
        }
        viewsByTag.clear();
        for (int i = 0; i < getChildCount(); i++) {
            NotificationView v = (NotificationView) getChildAt(i);
            viewsByTag.put(v.tag, v);
        }
        viewToAdd.clear();
        viewToRemove.clear();
        lock();
        if (onViewsUpdated != null) {
            onViewsUpdated.run();
        }
    }

    public void beforeLayoutChanges() {
        wasChanged = false;
        if (!lockAnimation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ViewParent parent = getParent();
                if (parent != null) {
                    TransitionManager.beginDelayedTransition(this, transitionSet);
                }
            }
        }
    }

    public void animateLayoutChanges() {
        if (wasChanged) {
            lock();
        }
        wasChanged = false;
    }

    public int getChildsHight() {
        int n = getChildCount();
        return (n > 0 ? AndroidUtilities.dp(16) : 0) + n * AndroidUtilities.dp(32);
    }

    private static class NotificationView extends FrameLayout {

        public String tag;
        ImageView iconView;
        TextView textView;

        public NotificationView(@NonNull Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);

            iconView = new ImageView(context);
            setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f))));
            addView(iconView, LayoutHelper.createFrame(24, 24, 0, 10, 4, 10, 4));

            textView = new TextView(context);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 44, 4, 16, 4));
        }

        public void startAnimation() {
            textView.setVisibility(View.GONE);
            postDelayed(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    TransitionSet transitionSet = new TransitionSet();
                    transitionSet.
                            addTransition(new Fade(Fade.IN).setDuration(150))
                            .addTransition(new ChangeBounds().setDuration(200));
                    transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
                    ViewParent parent = getParent();
                    if (parent != null) {
                        TransitionManager.beginDelayedTransition((ViewGroup) parent, transitionSet);
                    }
                }

                textView.setVisibility(View.VISIBLE);
            }, 400);
        }
    }

    public void setOnViewsUpdated(Runnable onViewsUpdated) {
        this.onViewsUpdated = onViewsUpdated;
    }
}
