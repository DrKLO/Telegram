package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

public class TrendingStickersAlert extends BottomSheet {

    private final int topOffset = AndroidUtilities.dp(12);

    private final GradientDrawable shapeDrawable = new GradientDrawable();
    private final AlertContainerView alertContainerView;
    private final TrendingStickersLayout layout;

    private int scrollOffsetY;

    public TrendingStickersAlert(@NonNull Context context, BaseFragment parentFragment, TrendingStickersLayout trendingStickersLayout, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);

        alertContainerView = new AlertContainerView(context);
        alertContainerView.addView(trendingStickersLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        containerView = alertContainerView;

        layout = trendingStickersLayout;
        layout.setParentFragment(parentFragment);
        layout.setOnScrollListener(new RecyclerListView.OnScrollListener() {

            private int scrolledY;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerListView.SCROLL_STATE_IDLE) {
                    scrolledY = 0;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                scrolledY += dy;
                if (recyclerView.getScrollState() == RecyclerListView.SCROLL_STATE_DRAGGING && Math.abs(scrolledY) > AndroidUtilities.dp(96)) {
                    View view = layout.findFocus();
                    if (view == null) {
                        view = layout;
                    }
                    AndroidUtilities.hideKeyboard(view);
                }
                if (dy != 0) {
                    updateLayout();
                }
            }
        });
    }

    @Override
    public void show() {
        super.show();
        setHeavyOperationsEnabled(false);
    }

    @Override
    public void dismiss() {
        super.dismiss();
        layout.recycle();
        setHeavyOperationsEnabled(true);
    }

    public void setHeavyOperationsEnabled(boolean enabled) {
        NotificationCenter.getGlobalInstance().postNotificationName(enabled ? NotificationCenter.startAllHeavyOperations : NotificationCenter.stopAllHeavyOperations, 2);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public TrendingStickersLayout getLayout() {
        return layout;
    }

    private void updateLayout() {
        if (layout.update()) {
            scrollOffsetY = layout.getContentTopOffset();
            containerView.invalidate();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        layout.getThemeDescriptions(descriptions, layout::updateColors);
        descriptions.add(new ThemeDescription(alertContainerView, 0, null, null, new Drawable[]{shadowDrawable}, null, Theme.key_dialogBackground));
        descriptions.add(new ThemeDescription(alertContainerView, 0, null, null, null, null, Theme.key_sheet_scrollUp));
        return descriptions;
    }

    @Override
    public void setAllowNestedScroll(boolean allowNestedScroll) {
        this.allowNestedScroll = allowNestedScroll;
    }

    private class AlertContainerView extends SizeNotifierFrameLayout {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean gluedToTop = false;
        private boolean ignoreLayout = false;
        private boolean statusBarVisible = false;
        private ValueAnimator statusBarAnimator;
        private float statusBarAlpha = 0f;
        private float[] radii = new float[8];

        public AlertContainerView(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            setDelegate(new SizeNotifierFrameLayoutDelegate() {

                private int lastKeyboardHeight;
                private boolean lastIsWidthGreater;

                @Override
                public void onSizeChanged(int keyboardHeight, boolean isWidthGreater) {
                    if (lastKeyboardHeight != keyboardHeight || lastIsWidthGreater != isWidthGreater) {
                        lastKeyboardHeight = keyboardHeight;
                        lastIsWidthGreater = isWidthGreater;
                        if (keyboardHeight > AndroidUtilities.dp(20) && !gluedToTop) {
                            TrendingStickersAlert.this.setAllowNestedScroll(false);
                            gluedToTop = true;
                        }
                    }
                }
            });
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            AndroidUtilities.runOnUIThread(this::requestLayout, 200);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                dismiss();
                return true;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return !isDismissed() && super.onTouchEvent(e);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int statusBarHeight = Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0;
            final int height = MeasureSpec.getSize(getMeasuredHeight()) - statusBarHeight;
            final int keyboardHeight = measureKeyboardHeight();
            final int padding = (int) ((height + keyboardHeight) * 0.2f);

            ignoreLayout = true;
            if (keyboardHeight > AndroidUtilities.dp(20)) {
                layout.glueToTop(true);
                TrendingStickersAlert.this.setAllowNestedScroll(false);
                gluedToTop = true;
            } else {
                layout.glueToTop(false);
                TrendingStickersAlert.this.setAllowNestedScroll(true);
                gluedToTop = false;
            }
            layout.setContentViewPaddingTop(padding);
            if (getPaddingTop() != statusBarHeight) {
                setPadding(backgroundPaddingLeft, statusBarHeight, backgroundPaddingLeft, 0);
            }
            ignoreLayout = false;
            super.onLayout(changed, l, t, r, b);
        }

        @Override
        public void requestLayout() {
            if (!ignoreLayout) {
                super.requestLayout();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            updateLayout();
            super.onDraw(canvas);

            final float fraction = getFraction();
            final int offset = (int) (topOffset * (1f - fraction));
            final int translationY = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) - topOffset;

            canvas.save();
            canvas.translate(0, layout.getTranslationY() + translationY);

            // background with top corners
            shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop + offset, getMeasuredWidth(), getMeasuredHeight() + (translationY < 0 ? -translationY : 0));
            shadowDrawable.draw(canvas);

            // mutable top corners
            if (fraction > 0f && fraction < 1f) {
                final float radius = AndroidUtilities.dp(12) * fraction;
                shapeDrawable.setColor(getThemedColor(Theme.key_dialogBackground));
                radii[0] = radii[1] = radii[2] = radii[3] = radius;
                shapeDrawable.setCornerRadii(radii);
                shapeDrawable.setBounds(backgroundPaddingLeft, scrollOffsetY + offset, getWidth() - backgroundPaddingLeft, scrollOffsetY + offset + AndroidUtilities.dp(24));
                shapeDrawable.draw(canvas);
            }

            canvas.restore();
        }

        private boolean statusBarOpen;
        private void updateLightStatusBar(boolean open) {
            if (statusBarOpen != open) {
                statusBarOpen = open;
                boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
                boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
                boolean isLight = open ? openBgLight : closedBgLight;
                AndroidUtilities.setLightStatusBar(getWindow(), isLight);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {

            final float fraction = getFraction();

            // status bar
            setStatusBarVisible(fraction == 0f && Build.VERSION.SDK_INT >= 21 && !isDismissed(), true);
            updateLightStatusBar(statusBarAlpha > .5f);
            if (statusBarAlpha > 0f) {
                paint.setColor(getThemedColor(Theme.key_dialogBackground));
                int bottom = (int) Math.max(0, scrollOffsetY + (topOffset * (1f - getFraction())) + AndroidUtilities.dp(24) + (layout.getTranslationY() + ((Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) - topOffset)));
                canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.lerp(bottom, -AndroidUtilities.statusBarHeight, statusBarAlpha), getMeasuredWidth() - backgroundPaddingLeft, bottom, paint);
            }

            super.dispatchDraw(canvas);

            canvas.save();
            canvas.translate(0, layout.getTranslationY() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0) - topOffset);

            // top icon
            final int w = AndroidUtilities.dp(36);
            final int h = AndroidUtilities.dp(4);
            final int offset = (int) (h * 2f * (1f - fraction));
            shapeDrawable.setCornerRadius(AndroidUtilities.dp(2));
            final int sheetScrollUpColor = getThemedColor(Theme.key_sheet_scrollUp);
            shapeDrawable.setColor(ColorUtils.setAlphaComponent(sheetScrollUpColor, (int) (Color.alpha(sheetScrollUpColor) * fraction)));
            shapeDrawable.setBounds((getWidth() - w) / 2, scrollOffsetY + AndroidUtilities.dp(10) + offset, (getWidth() + w) / 2, scrollOffsetY + AndroidUtilities.dp(10) + offset + h);
            shapeDrawable.draw(canvas);

            canvas.restore();
        }

        @Override
        public void setTranslationY(float translationY) {
            layout.setTranslationY(translationY);
            invalidate();
        }

        @Override
        public float getTranslationY() {
            return layout.getTranslationY();
        }

        private float getFraction() {
            return Math.min(1f, Math.max(0f, scrollOffsetY / (topOffset * 2f)));
        }

        private void setStatusBarVisible(boolean visible, boolean animated) {
            if (statusBarVisible != visible) {
                if (statusBarAnimator != null) {
                    statusBarAnimator.cancel();
                }

                this.statusBarVisible = visible;

                if (animated) {
                    if (statusBarAnimator == null) {
                        statusBarAnimator = ValueAnimator.ofFloat(statusBarAlpha, visible ? 1f : 0f);
                        statusBarAnimator.addUpdateListener(a -> {
                            statusBarAlpha = (float) a.getAnimatedValue();
                            invalidate();
                        });
                        statusBarAnimator.setDuration(200);
                    } else {
                        statusBarAnimator.setFloatValues(statusBarAlpha, visible ? 1f : 0f);
                    }
                    statusBarAnimator.start();
                } else {
                    statusBarAlpha = visible ? 1f : 0f;
                    invalidate();
                }
            }
        }
    }
}
