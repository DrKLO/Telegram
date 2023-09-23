package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Brush;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

public class PaintToolsView extends LinearLayout {
    private RLottieImageView[] buttons = new RLottieImageView[Brush.BRUSHES_LIST.size() + 2];
    private Delegate delegate;
    private Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int brushesCount;

    private boolean isShapeSelected;

    private int selectedIndex = 1;
    private int nextSelectedIndex = -1;
    private float nextSelectedIndexProgress = 0f;
    private ValueAnimator nextSelectedAnimator;

    public PaintToolsView(Context context, boolean includeBlurer) {
        super(context);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        setClipToPadding(false);

        selectorPaint.setColor(0x30ffffff);

        brushesCount = Brush.BRUSHES_LIST.size() - (!includeBlurer ? 1 : 0);

        for (int i = 0, a = 0; i < Brush.BRUSHES_LIST.size() + 2; i++) {
            buttons[a] = createView(i == 0, i == Brush.BRUSHES_LIST.size() + 1);
            int finalI = a;
            if (i == 0) {
                buttons[a].setOnClickListener(v -> delegate.onColorPickerSelected());
            } else if (i > 0 && i <= Brush.BRUSHES_LIST.size()) {
                Brush brush = Brush.BRUSHES_LIST.get(i - 1);
                if (!includeBlurer && brush instanceof Brush.Blurer) {
                    continue;
                }
                buttons[a].setAnimation(brush.getIconRes(), 28, 28);
                buttons[a].setOnClickListener(v -> {
                    animateNextIndex(finalI);
                    delegate.onGetPalette().setCurrentBrush(finalI - 1);
                    delegate.onBrushSelected(brush);
                });
            } else if (i == Brush.BRUSHES_LIST.size() + 1) {
                buttons[a].setImageResource(R.drawable.msg_add);
                buttons[a].setOnClickListener(v -> delegate.onAddButtonPressed(v));
            }
            addView(buttons[a]);
            a++;
        }
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        if (isShapeSelected) {
            isShapeSelected = false;
            AndroidUtilities.updateImageViewImageAnimated(buttons[brushesCount + 1], R.drawable.msg_add);
        }
        invalidate();
    }

    public void select(int index) {
        animateNextIndex(index);
        delegate.onGetPalette().setCurrentBrush(index - 1);
    }

    public void animatePlusToIcon(int icon) {
        animateNextIndex(brushesCount + 1);
        AndroidUtilities.updateImageViewImageAnimated(buttons[brushesCount + 1], icon);
        isShapeSelected = true;
    }

    private void animateNextIndex(int index) {
        if (index < 0 || index >= buttons.length) {
            return;
        }
        if (nextSelectedAnimator != null && nextSelectedIndex == index) {
            return;
        }

        RLottieImageView button = buttons[index];
        if (button != null) {
            Drawable dr = button.getDrawable();
            if (dr instanceof RLottieDrawable) {
                ((RLottieDrawable) dr).setCurrentFrame(0);
                ((RLottieDrawable) dr).start();
            }
        }

        if (nextSelectedAnimator != null) {
            nextSelectedAnimator.cancel();
        }

        if (selectedIndex == index) {
            return;
        }

        if (isShapeSelected) {
            isShapeSelected = false;
            AndroidUtilities.updateImageViewImageAnimated(buttons[brushesCount + 1], R.drawable.msg_add);
        }

        nextSelectedIndex = index;
        nextSelectedIndexProgress = 0f;

        nextSelectedAnimator = ValueAnimator.ofFloat(0, 1).setDuration(250);
        nextSelectedAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        nextSelectedAnimator.addUpdateListener(animation -> {
            nextSelectedIndexProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        nextSelectedAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == nextSelectedAnimator) {
                    selectedIndex = nextSelectedIndex;
                    nextSelectedIndex = -1;
                    nextSelectedAnimator = null;
                }
            }
        });
        nextSelectedAnimator.start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            for (int i = 1; i < getChildCount() - 1; ++i) {
                View child = getChildAt(i);
                if (x >= child.getLeft() && x <= child.getRight()) {
                    if (nextSelectedAnimator != null ? nextSelectedIndex != i : selectedIndex != i) {
                        animateNextIndex(i);
                        post(() -> child.performClick());
                        return true;
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        View v = buttons[selectedIndex];
        View next = nextSelectedIndex != -1 ? buttons[nextSelectedIndex] : null;
        float pr = next != null ? nextSelectedIndexProgress : 0f;
        float scale;
        if (pr <= 0.25f) {
            scale = 1f;
        } else if (pr >= 0.75f) {
            scale = 1f;
        } else if (pr > 0.25f && pr < 0.5f) {
            scale = (0.5f - pr) / 0.25f;
        } else {
            scale = 1f - (0.75f - pr) / 0.25f;
        }
        float rad = Math.min(v.getWidth() - v.getPaddingLeft() - v.getPaddingRight(), v.getHeight() - v.getPaddingTop() - v.getPaddingBottom()) / 2f + AndroidUtilities.dp(3) + AndroidUtilities.dp(3) * scale;
        float cx = v.getX() + v.getWidth() / 2f + getOffsetForIndex(selectedIndex);
        float nextCx = (next != null ? next.getX() + next.getWidth() / 2f : 0f) + (nextSelectedIndex != -1 ? getOffsetForIndex(nextSelectedIndex) : 0f);
        canvas.drawCircle(
            AndroidUtilities.lerp(cx, nextCx, pr),
            v.getY() + v.getHeight() / 2f,
            rad,
            selectorPaint
        );
    }

    private float getOffsetForIndex(int index) {
        return index == brushesCount + 1 ? AndroidUtilities.dp(4) : 0;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private RLottieImageView createView(boolean first, boolean last) {
        RLottieImageView imageView = new RLottieImageView(getContext());
        imageView.setPadding(AndroidUtilities.dp(first ? 0 : 8), AndroidUtilities.dp(8), AndroidUtilities.dp(last ? 0 : 8), AndroidUtilities.dp(8));
        imageView.setLayoutParams(LayoutHelper.createLinear(0, 24 + 16, 1f));
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        return imageView;
    }

    public interface Delegate {
        void onBrushSelected(Brush brush);
        void onColorPickerSelected();
        void onAddButtonPressed(View btn);
        PersistColorPalette onGetPalette();
    }
}
