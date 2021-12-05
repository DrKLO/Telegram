package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Tooltip extends TextView {

    private View anchor;
    private ViewPropertyAnimator animator;
    private boolean showing;

    Runnable dismissRunnable = () -> {
        animator = animate().alpha(0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.GONE);
            }
        }).setDuration(300);
        animator.start();
    };

    public Tooltip(Context context, ViewGroup parentView, int backgroundColor, int textColor) {
        super(context);

        setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), backgroundColor));
        setTextColor(textColor);
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
        setGravity(Gravity.CENTER_VERTICAL);

        parentView.addView(this, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 5, 0, 5, 3));
        setVisibility(GONE);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateTooltipPosition();
    }

    private void updateTooltipPosition() {
        if (anchor == null) {
            return;
        }
        int top = 0;
        int left = 0;

        View containerView = (View) getParent();
        View view = anchor;

        while (view != containerView) {
            top += view.getTop();
            left += view.getLeft();
            view = (View) view.getParent();
        }
        int x = left + anchor.getWidth() / 2 - getMeasuredWidth() / 2;
        if (x < 0) {
            x = 0;
        } else if (x + getMeasuredWidth() > containerView.getMeasuredWidth()) {
            x = containerView.getMeasuredWidth() - getMeasuredWidth() - AndroidUtilities.dp(16);
        }
        setTranslationX(x);

        int y = top - getMeasuredHeight();
        setTranslationY(y);
    }

    public void show(View anchor) {
        if (anchor == null) {
            return;
        }
        this.anchor = anchor;
        updateTooltipPosition();
        showing = true;

        AndroidUtilities.cancelRunOnUIThread(dismissRunnable);
        AndroidUtilities.runOnUIThread(dismissRunnable, 2000);
        if (animator != null) {
            animator.setListener(null);
            animator.cancel();
            animator = null;
        }
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
            animator = animate().setDuration(300).alpha(1f).setListener(null);
            animator.start();
        }
    }

    public void hide() {
        if (showing) {
            if (animator != null) {
                animator.setListener(null);
                animator.cancel();
                animator = null;
            }

            AndroidUtilities.cancelRunOnUIThread(dismissRunnable);
            dismissRunnable.run();
        }
        showing = false;
    }
}
