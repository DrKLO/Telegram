package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class ChatSearchTabs extends BlurredFrameLayout {

    public ViewPagerFixed.TabsView tabs;

    public ChatSearchTabs(@NonNull Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout) {
        super(context, sizeNotifierFrameLayout);
    }

    public void setTabs(ViewPagerFixed.TabsView tabs) {
        this.tabs = tabs;
        addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public float shownT;
    public boolean showWithCut = true;
    public void setShown(float shownT) {
        this.shownT = shownT;
        if (tabs != null) {
            tabs.setPivotX(tabs.getWidth() / 2f);
            tabs.setPivotY(0);
            tabs.setScaleX(lerp(0.8f, 1, shownT));
            tabs.setScaleY(lerp(0.8f, 1, shownT));
        }
        if (showWithCut) {
            if (tabs != null) {
                tabs.setAlpha(shownT);
            }
        } else {
            setAlpha(shownT);
        }
        invalidate();
    }

    protected void onShownUpdate(boolean finish) {

    }

    private boolean shown;
    private float actionBarTagsT;
    private ValueAnimator actionBarTagsAnimator;
    public void show(boolean show) {
        shown = show;
        if (actionBarTagsAnimator != null) {
            Animator a = actionBarTagsAnimator;
            actionBarTagsAnimator = null;
            a.cancel();
        }
        if (show) {
            setVisibility(View.VISIBLE);
        }
        actionBarTagsAnimator = ValueAnimator.ofFloat(actionBarTagsT, show ? 1f : 0f);
        actionBarTagsAnimator.addUpdateListener(valueAnimator1 -> {
            actionBarTagsT = (float) valueAnimator1.getAnimatedValue();
            setShown(actionBarTagsT);
            onShownUpdate(false);
        });
        actionBarTagsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        actionBarTagsAnimator.setDuration(320);
        actionBarTagsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation != actionBarTagsAnimator) return;
                actionBarTagsT = show ? 1f : 0f;
                setShown(actionBarTagsT);
                if (!show) {
                    setVisibility(View.GONE);
                }
                onShownUpdate(true);
            }
        });
        actionBarTagsAnimator.start();
    }

    public boolean isShown() {
        return shown;
    }

    public boolean shown() {
        return shownT > 0.5f;
    }
    public int getCurrentHeight() {
        return (int) (getMeasuredHeight() * shownT);
    }
    private Paint backgroundPaint2;
    @Override
    public void setBackgroundColor(int color) {
        if (SharedConfig.chatBlurEnabled() && super.sizeNotifierFrameLayout != null) {
            super.setBackgroundColor(color);
        } else {
            backgroundPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint2.setColor(color);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        if (showWithCut) {
            canvas.clipRect(0, 0, getWidth(), getCurrentHeight());
        }
        if (backgroundPaint2 != null) {
            canvas.drawRect(0, 0, getWidth(), getCurrentHeight(), backgroundPaint2);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

}
