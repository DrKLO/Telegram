package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

public class BotCommandsMenuContainer extends FrameLayout implements NestedScrollingParent {

    private ObjectAnimator currentAnimation = null;
    private NestedScrollingParentHelper nestedScrollingParentHelper;

    public RecyclerListView listView;
    Paint topBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float containerY;

    boolean dismissed = true;

    float scrollYOffset;

    public BotCommandsMenuContainer(Context context) {
        super(context);

        nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (listView.getLayoutManager() == null || listView.getAdapter() == null || listView.getAdapter().getItemCount() == 0) {
                    super.dispatchDraw(canvas);
                    return;
                }

                float y = scrollYOffset;

                y -= dp(8);
                containerY = y - dp(16);
                if (backgroundDrawable != null) {
                    backgroundDrawable.draw(canvas);
                }
                AndroidUtilities.rectTmp.set(
                    getMeasuredWidth() / 2f - dp(12),
                    y - dp(4),
                    getMeasuredWidth() / 2f + dp(12),
                    y
                );
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), topBackground);
                super.dispatchDraw(canvas);
            }
        };
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setClipToPadding(false);
        listView.setClipToOutline(true);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                View firstView = listView.getLayoutManager().findViewByPosition(0);
                float y = 0;
                if (firstView != null) {
                    y = firstView.getY();
                }
                if (y < 0) {
                    y = 0;
                }
                scrollYOffset = y;

                checkBackgroundBounds();
            }
        });
        addView(listView);
        updateColors();
        setClipChildren(false);
    }

    public float clipBottom() {
        if (dismissed) return 0;
        return Math.max(0, getMeasuredHeight() - (containerY + listView.getTranslationY()));
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return !dismissed && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
        if (dismissed) {
            return;
        }
        cancelCurrentAnimation();
    }

    @Override
    public void onStopNestedScroll(View target) {
        nestedScrollingParentHelper.onStopNestedScroll(target);
        if (dismissed) {
            return;
        }
        checkDismiss();
    }

    private void checkDismiss() {
        if (dismissed) {
            return;
        }
        if (listView.getTranslationY() > dp(16)) {
            dismiss();
        } else {
            playEnterAnim(false);
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dismissed) {
            return;
        }
        cancelCurrentAnimation();
        if (dyUnconsumed != 0) {
            float currentTranslation = listView.getTranslationY();
            currentTranslation -= dyUnconsumed;
            if (currentTranslation < 0) {
                currentTranslation = 0;
            }
            listView.setTranslationY(currentTranslation);
            invalidate();
        }
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dismissed) {
            return;
        }
        cancelCurrentAnimation();
        float currentTranslation = listView.getTranslationY();
        if (currentTranslation > 0 && dy > 0) {
            currentTranslation -= dy;
            consumed[1] = dy;
            if (currentTranslation < 0) {
                currentTranslation = 0;
            }
            listView.setTranslationY(currentTranslation);
            invalidate();
        }
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public int getNestedScrollAxes() {
        return nestedScrollingParentHelper.getNestedScrollAxes();
    }

    private void cancelCurrentAnimation() {
        if (currentAnimation != null) {
            currentAnimation.removeAllListeners();
            currentAnimation.cancel();
            currentAnimation = null;
        }
    }

    private boolean entering;

    public void show() {
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            listView.scrollToPosition(0);
            entering = true;
            dismissed = false;
        } else if (dismissed) {
            dismissed = false;
            cancelCurrentAnimation();
            playEnterAnim(false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (entering && !dismissed) {
            listView.setTranslationY(listView.getMeasuredHeight() - listView.getPaddingTop() + dp(16));
            playEnterAnim(true);
            entering = false;
        }

        checkBackgroundBounds();
    }

    public RecyclerListView getListView() {
        return listView;
    }

    private void playEnterAnim(boolean firstTime) {
        if (dismissed) {
            return;
        }
        currentAnimation = ObjectAnimator.ofFloat(listView, TRANSLATION_Y, listView.getTranslationY(), 0);
        if (firstTime) {
            currentAnimation.setDuration(320);
            currentAnimation.setInterpolator(new OvershootInterpolator(0.8f));
        } else {
            currentAnimation.setDuration(150);
            currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
        }
        currentAnimation.start();
    }

    public void dismiss() {
        if (!dismissed) {
            dismissed = true;
            cancelCurrentAnimation();
            currentAnimation = ObjectAnimator.ofFloat(listView, TRANSLATION_Y, listView.getTranslationY(), getMeasuredHeight() - scrollYOffset + dp(40));
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                    currentAnimation = null;
                }
            });
            currentAnimation.setDuration(150);
            currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            currentAnimation.start();
            onDismiss();
        }
    }

    protected void onDismiss() {

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < scrollYOffset - dp(24)) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void updateColors() {
        topBackground.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        if (backgroundDrawable != null) {
            backgroundDrawable.updateColors();
        }

        invalidate();
    }


    private @Nullable BlurredBackgroundDrawable backgroundDrawable;

    public void setBackgroundDrawable(@NonNull BlurredBackgroundDrawable backgroundDrawable) {
        this.backgroundDrawable = backgroundDrawable;
        this.backgroundDrawable.setRadius(dp(22));
        this.backgroundDrawable.setPadding(dp(5));

        listView.setOutlineProvider(backgroundDrawable.getViewOutlineProvider());
    }

    private void checkBackgroundBounds() {
        if (backgroundDrawable != null) {
            backgroundDrawable.setBounds(
                0,
                (int) scrollYOffset - dp(20 + 5),
                getMeasuredWidth(),
                getMeasuredHeight() + dp(5)
            );

            listView.invalidateOutline();
            listView.invalidate();
        }
    }
}
