package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bottom sheet with horizontally paged content. Pages are arbitrary ViewGroups
 * placed into a single {@link ViewPagerFixed}, so a swipe (or a programmatic
 * {@link #openPage(int)}) slides the current page to the left while the next one
 * slides in from the right. The sheet background is drawn over the tail of the
 * pages (following the animated content top of whichever pages are visible) and the
 * whole thing is clipped to that rounded rectangle, mirroring
 * StoryPrivacyBottomSheet / EmojiBottomSheet.
 */
public class NavBottomSheet extends BottomSheet {

    private final ViewPagerFixed viewPager;
    private final ArrayList<View> pageViews = new ArrayList<>();
    private boolean adapterAttached;
    private FrameLayout footerContainer;
    private Integer backgroundColorOverride;

    public NavBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, View... pages) {
        this(context, resourcesProvider, pages == null ? null : Arrays.asList(pages));
    }

    public NavBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, List<View> pages) {
        super(context, true, resourcesProvider);
        if (pages != null) {
            pageViews.addAll(pages);
        }

        NavContainerView navContainer = new NavContainerView(context);
        containerView = navContainer;

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                NavBottomSheet.this.containerView.invalidate();
            }
        };
        viewPager.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.navigationBarHeight);
        navContainer.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    /**
     * Pins a view to the bottom of the sheet, outside the pager, so it stays in
     * place while the pages above it slide horizontally.
     */
    public NavBottomSheet setFixedFooter(View footer) {
        if (footerContainer == null) {
            footerContainer = new FrameLayout(getContext());
            footerContainer.setClipChildren(false);
            footerContainer.setClipToPadding(false);
            containerView.addView(footerContainer, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.BOTTOM
            ));
        } else {
            footerContainer.removeAllViews();
        }
        footerContainer.addView(footer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM
        ));
        viewPager.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        ((NavContainerView) containerView).requestLayout();
        return this;
    }

    public NavBottomSheet setBackgroundOverride(int color) {
        backgroundColorOverride = color;
        setBackgroundColor(color);
        fixNavigationBar(color);
        ((NavContainerView) containerView).invalidate();
        return this;
    }

    public NavBottomSheet addPage(View page) {
        pageViews.add(page);
        if (adapterAttached) {
            viewPager.rebuild(false);
            viewPager.fillTabs(false);
        }
        return this;
    }

    public ViewPagerFixed getViewPager() {
        return viewPager;
    }

    public int getCurrentPosition() {
        return viewPager.getCurrentPosition();
    }

    public int getPageCount() {
        return pageViews.size();
    }

    public boolean openPage(int position) {
        if (position < 0 || position >= pageViews.size() || position == viewPager.getCurrentPosition()) {
            return false;
        }
        closeKeyboard();
        return viewPager.scrollToPosition(position);
    }

    public boolean goBack() {
        if (viewPager.getCurrentPosition() <= 0) {
            return false;
        }
        closeKeyboard();
        viewPager.scrollToPosition(viewPager.getCurrentPosition() - 1);
        return true;
    }

    public void closeKeyboard() {
        View[] pages = viewPager.getViewPages();
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) {
                AndroidUtilities.hideKeyboard(pages[i]);
            }
        }
    }

    @Override
    public void show() {
        if (!adapterAttached) {
            adapterAttached = true;
            viewPager.setAdapter(new ViewPagerFixed.Adapter() {
                @Override
                public int getItemCount() {
                    return pageViews.size();
                }

                @Override
                public View createView(int viewType) {
                    return pageViews.get(viewType);
                }

                @Override
                public int getItemViewType(int position) {
                    return position;
                }

                @Override
                public void bindView(View view, int position, int viewType) {
                    if (view instanceof NavPage) {
                        ((NavPage) view).update();
                    }
                }
            });
        }
        super.show();
        if (backgroundColorOverride != null) {
            setOverlayNavBarColor(backgroundColorOverride);
        }
    }

    @Override
    public void onBackPressed() {
        if (goBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return viewPager.getCurrentPosition() == 0;
    }

    @Override
    protected boolean canSwipeToBack(MotionEvent event) {
        return viewPager.getCurrentPosition() == 0;
    }

    /**
     * Pages opt-into the animated sheet shape by reporting the y-offset (from the
     * page's own top edge) at which their visible content begins. The sheet
     * background follows the weighted average of the tops of the two pages that are
     * on screen during a transition.
     */
    public interface NavPage {
        float top();

        default void update() {
        }
    }

    /**
     * Convenience page: a full-height {@link FrameLayout} that bottom-aligns a
     * single content holder and reports its top automatically, so plain content-based
     * sheets get the animated background for free.
     */
    public static class Page extends FrameLayout implements NavPage {

        private final FrameLayout content;

        public Page(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
            content = new FrameLayout(context);
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        }

        public FrameLayout getContent() {
            return content;
        }

        @Override
        public float top() {
            return Math.max(0f, getHeight() - content.getMeasuredHeight());
        }
    }

    private class NavContainerView extends FrameLayout {

        private final AnimatedFloat isActionBar = new AnimatedFloat(this, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private float top;

        public NavContainerView(Context context) {
            super(context);
            setWillNotDraw(false);
            backgroundPaint.setColor(getThemedColor(Theme.key_dialogBackground));
        }        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            int footerHeight = 0;
            if (footerContainer != null) {
                footerContainer.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                );
                footerHeight = footerContainer.getMeasuredHeight();
            }
            viewPager.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, height - footerHeight), MeasureSpec.EXACTLY)
            );
            setMeasuredDimension(width, height);
        }

        private float navTopFor(View page) {
            return page instanceof NavPage ? ((NavPage) page).top() : 0f;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            backgroundPaint.setColor(
                    backgroundColorOverride != null
                            ? backgroundColorOverride
                            : getThemedColor(Theme.key_dialogBackground)
            );
            final View[] views = viewPager.getViewPages();
            top = 0;
            for (int i = 0; i < views.length; i++) {
                final View page = views[i];
                if (page == null) {
                    continue;
                }
                final float t = Utilities.clamp(
                        1f - Math.abs(page.getTranslationX() / (float) Math.max(1, page.getMeasuredWidth())),
                        1,
                        0
                );
                top += navTopFor(page) * t;
                if (page.getVisibility() == View.VISIBLE && page instanceof NavPage) {
                    ((NavPage) page).update();
                }
            }
            final float actionBarT = isActionBar.set(top <= AndroidUtilities.statusBarHeight ? 1f : 0f);
            top = Math.max(AndroidUtilities.statusBarHeight, top) - AndroidUtilities.statusBarHeight * actionBarT;
            AndroidUtilities.rectTmp.set(
                    backgroundPaddingLeft,
                    top,
                    getWidth() - backgroundPaddingLeft,
                    getHeight() + dp(8)
            );
            final float r = AndroidUtilities.lerp(dp(14), 0, actionBarT);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
            if (topBulletinContainer != null) {
                float sheetTop = containerView.getY() + top + backgroundPaddingTop;
                float minBottom = AndroidUtilities.statusBarHeight + dp(56) + topBulletinContainer.getHeight();
                topBulletinContainer.setTranslationY(Math.max(sheetTop, minBottom) - topBulletinContainer.getBottom());
            }
            canvas.save();
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
