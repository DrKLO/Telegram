package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.Utilities.clamp01;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.GradientClip;

import java.util.ArrayList;

public class BottomSheetTabsOverlay extends View {

    public interface Sheet {
        public SheetView getWindowView();

        public void show();
        public void dismiss(boolean tabs);

        public BottomSheetTabs.WebTabData saveState();
        public boolean restoreState(BaseFragment fragment, BottomSheetTabs.WebTabData tab);

        public void release();
        public boolean isFullSize();

        public default boolean hadDialog() { return false; };
        public boolean setDialog(BottomSheetTabDialog dialog);

        default void setLastVisible(boolean lastVisible) {};

        public int getNavigationBarColor(int color);
    }

    public interface SheetView {
        public Context getContext();

        public void setDrawingFromOverlay(boolean value);
        public RectF getRect();
        public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening);

        public boolean post(Runnable r);
    }

    private BottomSheetTabs tabsView;
    private Sheet dismissingSheet;
    private Sheet openingSheet;
    private BottomSheetTabs.TabDrawable dismissingTab;
    private BottomSheetTabs.TabDrawable openingTab;
    private float openingTabScroll;
    private ValueAnimator animator;
    private float dismissProgress;
    private float openingProgress;

    private final AnimatedFloat animatedCount = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final OverScroller scroller;
    private final int maximumVelocity, minimumVelocity;
    private int navigationBarInset;

    public BottomSheetTabsOverlay(Context context) {
        super(context);

        setWillNotDraw(false);

        scroller = new OverScroller(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        invalidate();

        return WindowInsetsCompat.CONSUMED;
    }

    public boolean isOpened() {
        return openProgress > .1f;
    }

    public void setTabsView(BottomSheetTabs tabsView) {
        this.tabsView = tabsView;
    }

    private TabPreview pressTab;
    private boolean pressTabClose;
    private float startY, startX;
    private long startTime;

    private boolean verticallyScrolling;
    private boolean horizontallySwiping;
    private VelocityTracker velocityTracker;

    private float lastY;

    private boolean hitCloseAllButton;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (AndroidUtilities.isTablet() && event.getAction() == MotionEvent.ACTION_DOWN && !tabsViewBounds.contains(event.getX(), event.getY())) {
            return false;
        }
        boolean r = false;
        if (openProgress > 0) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startTime = System.currentTimeMillis();
                startX = event.getX();
                startY = event.getY();
                pressTab = getTabAt(event.getX(), event.getY());
                hitCloseAllButton = closeAllButtonBackground != null && closeAllButtonBackground.getBounds().contains((int) event.getX(), (int) event.getY());
                if (hitCloseAllButton) pressTab = null;
                if (closeAllButtonBackground != null) {
                    closeAllButtonBackground.setHotspot(event.getX(), event.getY());
                    closeAllButtonBackground.setState(hitCloseAllButton ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[] {});
                }
                verticallyScrolling = false;
                horizontallySwiping = false;
                pressTabClose = false;
                if (pressTab != null) {
                    pressTab.cancelDismissAnimator();
                    pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains((int) (event.getX() - pressTab.clickBounds.left), (int) (event.getY() - pressTab.clickBounds.top - dp(24)));
                    if (pressTabClose) {
                        pressTab.tabDrawable.closeRipple.setHotspot((int) (event.getX() - rect.left), (int) (event.getY() - rect.centerY()));
                    }
                    pressTab.setPressed(!pressTabClose);
                    pressTab.tabDrawable.closeRipple.setState(pressTabClose ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
                }
                lastY = event.getY();
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                if (scrollAnimator != null) {
                    scrollAnimator.cancel();
                    scrollAnimator = null;
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressTab != null) {
                    if (pressTab.isPressed()) {
                        if (!horizontallySwiping && !verticallyScrolling && MathUtils.distance(startX, event.getY(), event.getX(), event.getY()) > AndroidUtilities.touchSlop) {
                            horizontallySwiping = true;
                        }
                        if (!verticallyScrolling && !horizontallySwiping && MathUtils.distance(event.getX(), startY, event.getX(), event.getY()) > AndroidUtilities.touchSlop) {
                            if (!scroller.isFinished()) {
                                scroller.abortAnimation();
                            }
                            if (scrollAnimator != null) {
                                scrollAnimator.cancel();
                                scrollAnimator = null;
                            }
                            verticallyScrolling = true;
                        }
                        if (tabsView != null && (verticallyScrolling || horizontallySwiping)) {
                            pressTab.setPressed(false);
                            pressTab.cancelDismissAnimator();
                        }
                    } else {
                        if (!pressTabClose && !horizontallySwiping && !verticallyScrolling && MathUtils.distance(startX, event.getY(), event.getX(), event.getY()) > AndroidUtilities.touchSlop) {
                            horizontallySwiping = true;
                        }
                        if (!pressTabClose && !verticallyScrolling && !horizontallySwiping && MathUtils.distance(event.getX(), startY, event.getX(), event.getY()) > AndroidUtilities.touchSlop) {
                            if (!scroller.isFinished()) {
                                scroller.abortAnimation();
                            }
                            if (scrollAnimator != null) {
                                scrollAnimator.cancel();
                                scrollAnimator = null;
                            }
                            verticallyScrolling = true;
                        }
                        if (pressTabClose) {
                            pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains((int) (event.getX() - pressTab.clickBounds.left), (int) (event.getY() - pressTab.clickBounds.top - dp(24)));
                            if (!pressTabClose) {
                                pressTab.tabDrawable.closeRipple.setState(new int[]{});
                            }
                        }
                    }
                    if (!pressTab.isPressed()) {
                        if (horizontallySwiping) {
                            pressTab.dismissProgress = (event.getX() - startX) / dp(300);
                        } else if (verticallyScrolling) {
                            float deltaY = event.getY() - lastY;
                            if (offset < getScrollMin()) {
                                deltaY *= (1f - .5f * Utilities.clamp((getScrollMin() - offset) / getScrollStep(), 1f, 0f));
                            }
                            setScrollOffset(
                                Utilities.clamp((getScrollOffset() * getScrollStep() - deltaY) / getScrollStep(), getScrollMax(), getScrollMin() - 1.4f * getScrollStep())
                            );
                            invalidate();
                        }
                    }
                    invalidate();
                }
                if (closeAllButtonBackground != null && hitCloseAllButton) {
                    hitCloseAllButton = pressTab == null && closeAllButtonBackground != null && closeAllButtonBackground.getBounds().contains((int) event.getX(), (int) event.getY());
                    if (!hitCloseAllButton) {
                        closeAllButtonBackground.setState(new int[] {});
                    }
                }
                lastY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressTab != null) {
                    if (tabsView != null && Math.abs(pressTab.dismissProgress) > .4f) {
                        TabPreview tab = pressTab;
                        tabsView.removeTab(pressTab.tabData, success -> {
                            if (success) {
                                tab.animateDismiss(tab.dismissProgress < 0 ? -1f : 1f);
                                scrollTo(Utilities.clamp(offset, getScrollMax(false), getScrollMin(false)));
                                if (tabsView.getTabs().isEmpty()) {
                                    closeTabsView();
                                }
                            } else {
                                tab.animateDismiss(0);
                            }
                        });
                    } else {
                        pressTab.animateDismiss(0);
                        if (tabsView != null && pressTab.isPressed()) {
                            closeTabsView();
                            pressTab.webView = null;
                            tabsView.openTab(pressTab.tabData);
                        } else if (verticallyScrolling) {
                            if (offset < getScrollMin() - getScrollWindow() * .15f) {
                                closeTabsView();
                            } else if (offset < getScrollMin()) {
                                scrollTo(getScrollMin());
                            } else {
                                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                                float velocityY = velocityTracker.getYVelocity();
                                if (Math.abs(velocityY) > minimumVelocity) {
                                    scroller.fling(0, (int) (getScrollOffset() * getScrollStep()), 0, (int) -velocityY, 0, 0, (int) (getScrollMin() * getScrollStep()), (int) (getScrollMax() * getScrollStep()), 0, (int) (.1f * getScrollStep()));
                                } else {
                                    scroller.startScroll(0, (int) (getScrollOffset() * getScrollStep()), 0, 0, 0);
                                }
                            }
                            velocityTracker.recycle();
                            velocityTracker = null;
                            postInvalidateOnAnimation();
                        }
                    }
                    pressTab.setPressed(false);
                    if (pressTabClose) {
                        pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains((int) (event.getX() - pressTab.clickBounds.left), (int) (event.getY() - pressTab.clickBounds.top - dp(24)));
                    }
                    if (pressTabClose) {
                        TabPreview tab = pressTab;
                        tabsView.removeTab(pressTab.tabData, success -> {
                            if (success) {
                                tab.animateDismiss(1f);
                                scrollTo(Utilities.clamp(offset, getScrollMax(false), getScrollMin(false)));
                                if (tabsView.getTabs().isEmpty()) {
                                    closeTabsView();
                                }
                            } else {
                                tab.animateDismiss(0);
                            }
                        });
                    }
                    pressTab.tabDrawable.closeRipple.setState(new int[]{});
                } else if (hitCloseAllButton) {
                    tabsView.removeAll();
                    closeTabsView();
                } else if (MathUtils.distance(startX, startY, event.getX(), event.getY()) <= AndroidUtilities.touchSlop && !verticallyScrolling && !horizontallySwiping && System.currentTimeMillis() - startTime <= ViewConfiguration.getTapTimeout() * 1.2f) {
                    closeTabsView();
                }
                pressTab = null;
                pressTabClose = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                hitCloseAllButton = false;
                if (closeAllButtonBackground != null) {
                    closeAllButtonBackground.setState(new int[] {});
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (pressTab != null) {
                    pressTab.animateDismiss(0);
                    pressTab.setPressed(false);
                    pressTab.tabDrawable.closeRipple.setState(new int[]{});
                }
                pressTab = null;
                pressTabClose = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                hitCloseAllButton = false;
                if (closeAllButtonBackground != null) {
                    closeAllButtonBackground.setState(new int[] {});
                }
            }
            r = true;
        }
        return r;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            setScrollOffset(scroller.getCurrY() / getScrollStep());
            postInvalidateOnAnimation();
        }
    }

    public float offset;
    public float getScrollOffset() {
        return this.offset; // Utilities.clamp(this.offset, getScrollMax(), getScrollMin());
    }

    public void setScrollOffset(float offset) {
        this.offset = offset; // Utilities.clamp(offset, getScrollMax(), getScrollMin());
    }

    private float getScrollStep() {
        return dp(200);
    }

    public float getScrollRange() {
        return getScrollRange(true);
    }

    public float getScrollRange(boolean animated) {
        float tabCount = 0;
        for (int i = 0; i < tabs.size(); ++i) {
            final TabPreview tab = tabs.get(i);
            tabCount += tab.tabDrawable.index >= 0 ? 1 : 0;
        }
        return animated ? animatedCount.set(tabCount) : tabCount;
    }

    public float getScrollWindow() {
        return Math.min(3, getScrollRange());
    }

    public float getScrollWindow(boolean animated) {
        return Math.min(3, getScrollRange(animated));
    }

    public float getScrollMin() {
        return getScrollMin(true);
    }

    public float getScrollMin(boolean animated) {
        return -getScrollWindow() / 3f * Utilities.clamp(getScrollRange(animated), 1f, 0);
    }

    public float getScrollMax() {
        return getScrollMax(true);
    }

    public float getScrollMax(boolean animated) {
        return getScrollRange(animated) - getScrollWindow(animated) - getScrollWindow(animated) / 3f * Utilities.clamp(4f - getScrollRange(animated), .5f, 0);
    }

    public boolean canScroll() {
        return canScroll(false);
    }

    public boolean canScroll(boolean animated) {
        return getScrollMax(animated) - getScrollMin(animated) > .5f;
    }

    private TabPreview getTabAt(float x, float y) {
        if (openProgress < 1)
            return null;
        for (int i = tabs.size() - 1; i >= 0; --i) {
            TabPreview tab = tabs.get(i);
            if (Math.abs(tab.dismissProgress) < .4f && tab.clickBounds.contains(x, y))
                return tab;
        }
        return null;
    }

    private boolean slowerDismiss;
    public void setSlowerDismiss(boolean slowerDismiss) {
        this.slowerDismiss = slowerDismiss;
    }

    public boolean openSheet(Sheet sheet, BottomSheetTabs.WebTabData tab, Runnable whenOpened) {
        if (sheet == null) return false;
        if (tabsView == null) return false;

        if (dismissingSheet != null || openingSheet != null) {
            if (animator != null) {
                animator.end();
                animator = null;
            }
        }

        openingSheet = sheet;
        sheet.getWindowView().setDrawingFromOverlay(true);
        invalidate();

        if (animator != null) {
            animator.cancel();
        }

        openingTab = tabsView.findTabDrawable(tab);
        openingTabScroll = openingTab != null ? ((animatedCount.get() - 1 - openingTab.getPosition()) - Math.max(getScrollMin(), getScrollOffset())) / getScrollWindow() : 0;

        openingProgress = 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(anm -> {
            openingProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                sheet.getWindowView().setDrawingFromOverlay(false);
                sheet.getWindowView().post(() -> {
                    openingSheet = null;
                    openingTab = null;
                    if (!isOpen) {
                        clearTabs();
                    }
                    openingProgress = 0;
                    invalidate();
                });

                if (whenOpened != null) {
                    whenOpened.run();
                }
            }
        });
        AndroidUtilities.applySpring(animator, 260, 30, 1);
//        animator.setDuration(5000);
        animator.start();

        return true;
    }

    public void stopAnimations() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    public boolean dismissSheet(Sheet sheet) {
        if (sheet == null) return false;
        if (tabsView == null) return false;

        if (dismissingSheet != null || openingSheet != null) {
            if (animator != null) {
                animator.end();
                animator = null;
            }
        }

        dismissingSheet = sheet;
        sheet.setLastVisible(false);
//        sheet.getWindowView().setDrawingFromOverlay(true);

        if (animator != null) {
            animator.cancel();
        }

        BottomSheetTabs.WebTabData tab = sheet.saveState();
        dismissingTab = tabsView.pushTab(tab);
        post(() -> {
            if (sheet != null && sheet.getWindowView() != null) {
                sheet.getWindowView().setDrawingFromOverlay(true);
            }
        });
        invalidate();

        dismissProgress = 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(anm -> {
            dismissProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                View view = tab.webView != null ? tab.webView : tab.view2;
                if (view != null && tab.previewBitmap == null && tab.viewWidth > 0 && tab.viewHeight > 0) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        renderHardwareViewToBitmap(view, -tab.viewScroll, b -> {
                            tab.previewBitmap = b;
                            sheet.getWindowView().setDrawingFromOverlay(false);
                            sheet.release();
                        });
                        dismissingSheet = null;
                        invalidate();
                        return;
                    } else {
                        tab.previewBitmap = Bitmap.createBitmap(tab.viewWidth, tab.viewHeight, Bitmap.Config.RGB_565);
                        Canvas canvas = new Canvas(tab.previewBitmap);
                        canvas.translate(0, -tab.viewScroll);
                        view.draw(canvas);
                    }
                }
                sheet.getWindowView().setDrawingFromOverlay(false);
                sheet.release();
                dismissingSheet = null;
                invalidate();
            }
        });
        AndroidUtilities.applySpring(animator, 220, 30, 1);
        animator.setDuration((long) (animator.getDuration() * 1.1f));
        animator.start();

        slowerDismiss = false;

        return true;
    }

    public boolean onBackPressed() {
        if (isOpen) {
            closeTabsView();
            return true;
        }
        return false;
    }

    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;
    private void prepareBlur(View view) {
        AndroidUtilities.makingGlobalBlurBitmap = true;
        blurBitmap = AndroidUtilities.makeBlurBitmap(view, 14, 14);
        AndroidUtilities.makingGlobalBlurBitmap = false;

        blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : +.25f);
        blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        blurMatrix = new Matrix();
    }

    private final RectF tabsViewBounds = new RectF();
    private final ArrayList<TabPreview> tabs = new ArrayList<>();
    private View actionBarLayout;

    public void openTabsView() {
        if (tabsView == null || !(tabsView.getParent() instanceof View)) return;

        stopAnimations();

        actionBarLayout = (View) tabsView.getParent();
        if (actionBarLayout != null) {
            actionBarLayout.getLocationOnScreen(pos);
        } else {
            pos[0] = pos[1] = 0;
        }
        getLocationOnScreen(pos2);
        tabsViewBounds.set(pos[0] - pos2[0], pos[1] - pos2[1], pos[0] - pos2[0] + actionBarLayout.getWidth(), pos[1] - pos2[1] + actionBarLayout.getHeight());

        prepareBlur(actionBarLayout);
        clearTabs();
        prepareTabs();
        animateOpen(true);
    }

    private void clearTabs() {
        tabs.clear();
    }

    private void prepareTabs() {
        ArrayList<BottomSheetTabs.WebTabData> tabs = tabsView.getTabs();
        ArrayList<BottomSheetTabs.TabDrawable> tabDrawables = tabsView.getTabDrawables();

        for (int i = tabs.size() - 1; i >= 0; --i) {
            BottomSheetTabs.WebTabData tabData = tabs.get(i);
            BottomSheetTabs.TabDrawable tabDrawable = null;
            for (int j = 0; j < tabDrawables.size(); ++j) {
                BottomSheetTabs.TabDrawable d = tabDrawables.get(j);
                if (d.tab == tabData) {
                    tabDrawable = d;
                    break;
                }
            }
            if (tabDrawable == null) continue;
            this.tabs.add(new TabPreview(this, tabData, tabDrawable));
        }
        animatedCount.set(this.tabs.size(), true);
        setScrollOffset(getScrollMax());
    }

    public void closeTabsView() {
        animateOpen(false);
    }

    private ValueAnimator scrollAnimator;
    private void scrollTo(float offset) {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        scrollAnimator = ValueAnimator.ofFloat(this.offset, offset);
        scrollAnimator.addUpdateListener(anm -> {
            this.offset = (float) anm.getAnimatedValue();
        });
        scrollAnimator.setDuration(250);
        scrollAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        scrollAnimator.start();
    }

    private boolean isOpen;
    private float openProgress;
    private ValueAnimator openAnimator;
    private void animateOpen(boolean open) {
        if (isOpen == open) return;
        if (openAnimator != null) {
            openAnimator.cancel();
        }

        isOpen = open;
        if (tabsView != null) {
            tabsView.drawTabs = false;
            tabsView.invalidate();
        }
        invalidate();
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1f : 0f);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (tabsView != null) {
                    tabsView.drawTabs = true;
                    tabsView.invalidate();
                }
                openProgress = isOpen ? 1f : 0f;
                invalidate();
                if (!isOpen && openingSheet == null) {
                    clearTabs();
                }
            }
        });
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(320);
        openAnimator.start();
    }

    private final int[] pos = new int[2];
    private final int[] pos2 = new int[2];
    private final int[] pos3 = new int[2];
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final RectF clipRect = new RectF();
    private final Path clipPath = new Path();

    private void drawDismissingTab(Canvas canvas) {
        if (dismissingSheet != null) {
            getLocationOnScreen(pos2);
            tabsView.getLocationOnScreen(pos);
            tabsView.getTabBounds(rect, 0);
            rect.offset(pos[0] - pos2[0], pos[1] - pos2[1]);

            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - navigationBarInset);

            float radius = dismissingSheet.getWindowView().drawInto(canvas, rect, dismissProgress, clipRect, dismissProgress, false);

            if (dismissingTab != null) {
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                final float y = clipRect.top - dp(50) * (1f - dismissProgress);
                rect.set(clipRect.left, y, clipRect.right, y + dp(50));
                tabsView.setupTab(dismissingTab);
                dismissingTab.draw(canvas, rect, radius, dismissProgress, 1f);
                canvas.restore();
            }

            canvas.restore();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == closeAllButtonBackground || super.verifyDrawable(who);
    }

    private Text closeAllButtonText;
    private boolean closeAllButtonBackgroundDark;
    private Drawable closeAllButtonBackground;

    private GradientClip gradientClip;

    private void drawTabsPreview(Canvas canvas) {
        if (openProgress <= 0 && openingProgress <= 0) return;

        canvas.save();

        if (actionBarLayout != null) {
            actionBarLayout.getLocationOnScreen(pos);
            getLocationOnScreen(pos2);
            tabsViewBounds.set(pos[0] - pos2[0], pos[1] - pos2[1], pos[0] - pos2[0] + actionBarLayout.getWidth(), pos[1] - pos2[1] + actionBarLayout.getHeight());
        } else {
            pos[0] = pos[1] = 0;
            tabsViewBounds.set(0, 0, 0, 0);
        }

        canvas.clipRect(tabsViewBounds);
        canvas.translate(tabsViewBounds.left, tabsViewBounds.top);

        final float thisWidth = tabsViewBounds.width(), thisHeight = tabsViewBounds.height();

        if (blurBitmap != null) {
            blurMatrix.reset();
            final float s = (float) tabsViewBounds.width() / blurBitmap.getWidth();
            blurMatrix.postScale(s, s);
            blurBitmapShader.setLocalMatrix(blurMatrix);

            blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
            canvas.drawRect(0, 0, thisWidth, thisHeight, blurBitmapPaint);
        }

        canvas.saveLayerAlpha(0, 0, thisWidth, thisHeight, 0xFF, Canvas.ALL_SAVE_FLAG);

        final float paddingTop = AndroidUtilities.statusBarHeight + dp(40) + dp(55);
        final float paddingBottom = dp(68);

        final int width = (int) Math.min(dp(340), thisWidth * .95f);
        final int height = (int) (AndroidUtilities.isTablet() ? tabsViewBounds.height() * .5f : thisHeight * .75f);
        final float cx = thisWidth / 2f;
        float tabCount = 0;
        for (int i = 0; i < tabs.size(); ++i) {
            final TabPreview tab = tabs.get(i);
            tabCount += tab.tabDrawable.index >= 0 ? 1 : 0;
        }
        final float count = animatedCount.set(tabCount);
        boolean reverse = true;
        final float open = lerp(0, (1f - Utilities.clamp(getScrollWindow() <= 0 ? 0 : (getScrollMin() - getScrollOffset()) / (getScrollWindow() * .15f) * .2f, 1f, 0f)), openProgress);
        int openingTabIndex = -1;
        for (int i = 0; i < tabs.size() + 1; i++) {
            final TabPreview tab;
            if (i == tabs.size()) {
                if (openingTabIndex >= 0 && openingProgress > .5f) {
                    tab = tabs.get(openingTabIndex);
                } else {
                    continue;
                }
            } else {
                tab = tabs.get(i);
            }
            if (i < tabs.size() && tab.tabDrawable == openingTab && openingProgress > .5f) {
                openingTabIndex = i;
                continue;
            }

            final float tabOpen = tab.tabDrawable == openingTab ? 1f : open;
            final float opening = tab.tabDrawable == openingTab ? openingProgress : 0f;

            final float position = count - 1 - tab.tabDrawable.getPosition();
            final float scroll = tab.tabDrawable == openingTab ? openingTabScroll : (position - Math.max(getScrollMin(), getScrollOffset())) / getScrollWindow();
            final float scrollT = Math.max(scroll, 0f);
            final float oscrollT = Math.max(Math.min(scroll, 1f), -4);

            float alpha = 1f;
            float top, bottom, y;
            top = paddingTop + dp(6) * Math.min(5, position);
            bottom = thisHeight - paddingBottom - height * .26f;
            y = top + (bottom - top) * scroll;

            if (alpha <= 0) continue;

            rect2.set(cx - width / 2f, y, cx + width / 2f, y + height);
            boolean drawSimple = tab.tabDrawable != openingTab && (rect2.top > thisHeight || rect2.bottom < 0 || open < .1f) && position < count - 3;

            if (openingSheet != null && tab.tabDrawable == openingTab) {
                rect.set(openingSheet.getWindowView().getRect());
                AndroidUtilities.lerpCentered(rect2, rect, opening, rect2);
            } else {
                tabsView.getTabBounds(rect, Utilities.clamp(tab.tabDrawable.getPosition(), 1, 0));
                rect.offset(tabsView.getX(), tabsView.getY());
                AndroidUtilities.lerpCentered(rect, rect2, open, rect2);
            }

            if (tabsView != null) {
                tabsView.setupTab(tab.tabDrawable);
            }

            if (tab.tabDrawable != openingTab && (rect2.top > thisHeight || rect2.bottom < 0))
                continue;

            canvas.save();
            tab.clickBounds.set(rect2);

            Canvas tabCanvas = canvas;
            tab.matrix.reset();

            final int p = 0;
            final float Sh = 1f;
            tab.src[0] = rect2.left;
            tab.src[1] = rect2.top;
            tab.src[2] = rect2.right;
            tab.src[3] = rect2.top;
            tab.src[4] = rect2.right;
            tab.src[5] = rect2.top + rect2.height() * Sh;
            tab.src[6] = rect2.left;
            tab.src[7] = rect2.top + rect2.height() * Sh;

            tab.dst[0] = rect2.left;
            tab.dst[1] = rect2.top - dp(p);
            tab.dst[2] = rect2.right;
            tab.dst[3] = rect2.top - dp(p);
            final float s1 = .83f, s2 = .6f;
            tab.dst[4] = rect2.centerX() + rect2.width() / 2f * lerp(1f, s1, tabOpen * (1f - opening));
            tab.dst[5] = rect2.top - dp(p) + (rect2.height() * Sh + dp(p + p)) * lerp(1f, s2, tabOpen * (1f - opening));
            tab.dst[6] = rect2.centerX() - rect2.width() / 2f * lerp(1f, s1, tabOpen * (1f - opening));
            tab.dst[7] = rect2.top - dp(p) + (rect2.height() * Sh + dp(p + p)) * lerp(1f, s2, tabOpen * (1f - opening));

            tab.matrix.setPolyToPoly(tab.src, 0, tab.dst, 0, 4);
            tabCanvas.concat(tab.matrix);

            tab.draw(
                tabCanvas,
                rect2,
                drawSimple,
                tab.tabDrawable == openingTab ? 1f : lerp(tab.tabDrawable.getAlpha(), alpha, openProgress),
                tab.tabDrawable == openingTab ? 1f : tabOpen * (1f - opening),
                opening,
                lerp(clamp01(position - count + 2),1f, clamp01((tabOpen - .1f) / .8f))
            );

            if (openingSheet != null && tab.tabDrawable == openingTab) {
                openingSheet.getWindowView().drawInto(canvas, rect2, 1f, rect2, opening, true);
            }

            canvas.restore();
        }
        canvas.save();
        if (gradientClip == null) {
            gradientClip = new GradientClip();
        }
        AndroidUtilities.rectTmp.set(0, 0, thisWidth, paddingTop);
        gradientClip.draw(canvas, AndroidUtilities.rectTmp, true, openProgress);
        canvas.restore();
        canvas.restore();

        if (closeAllButtonText == null) {
            closeAllButtonText = new Text(getString(R.string.BotCloseAllTabs), 14, AndroidUtilities.bold());
        }
        if (closeAllButtonBackground == null || closeAllButtonBackgroundDark != Theme.isCurrentThemeDark()) {
            closeAllButtonBackgroundDark = Theme.isCurrentThemeDark();
            if (closeAllButtonBackgroundDark) {
                closeAllButtonBackground = Theme.createSimpleSelectorRoundRectDrawable(64, 0x20ffffff, 0x33ffffff);
            } else {
                closeAllButtonBackground = Theme.createSimpleSelectorRoundRectDrawable(64, 0x2e000000, 0x44000000);
            }
            closeAllButtonBackground.setCallback(this);
        }
        final float buttonWidth = closeAllButtonText.getCurrentWidth() + dp(24);
        closeAllButtonBackground.setBounds(
                (int) ((thisWidth - buttonWidth) / 2f),
                (int) (paddingTop - dp(75 + 20) / 2f - dp(14)),
                (int) ((thisWidth + buttonWidth) / 2f),
                (int) (paddingTop - dp(75 + 20) / 2f + dp(14))
        );
        closeAllButtonBackground.setAlpha((int) (0xFF * openProgress));
        closeAllButtonBackground.draw(canvas);
        closeAllButtonText.draw(canvas, (thisWidth - buttonWidth) / 2f + dp(12), paddingTop - dp(75 + 20) / 2f, 0xFFFFFFFF, openProgress);

        canvas.restore();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        drawDismissingTab(canvas);
        drawTabsPreview(canvas);
    }

    private static class TabPreview {

        public final RectF clickBounds = new RectF();

        public final View parentView;
        public final BottomSheetTabs.WebTabData tabData;
        public final BottomSheetTabs.TabDrawable tabDrawable;
        public WebView webView;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

//        private RenderNode node;
        private final Matrix matrix = new Matrix();
        private final float[] src = new float[8];
        private final float[] dst = new float[8];

        public float dismissProgress = 0f;
        private ValueAnimator dismissAnimator;
        public void cancelDismissAnimator() {
            if (dismissAnimator != null) {
                dismissAnimator.cancel();
            }
        }
        public void animateDismiss(float dismissTo) {
            cancelDismissAnimator();
            dismissAnimator = ValueAnimator.ofFloat(dismissProgress, dismissTo);
            dismissAnimator.addUpdateListener(anm -> {
                dismissProgress = (float) anm.getAnimatedValue();
                if (parentView != null) {
                    parentView.invalidate();
                }
            });
            dismissAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dismissProgress = dismissTo;
                    if (parentView != null) {
                        parentView.invalidate();
                    }
                }
            });
            if (Math.abs(dismissTo) < .1f) {
                AndroidUtilities.applySpring(dismissAnimator, 285, 20);
            } else {
                dismissAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            }
            dismissAnimator.start();
        }

        public final ButtonBounce bounce;
        public boolean isPressed() {
            return bounce.isPressed();
        }
        public void setPressed(boolean pressed) {
            bounce.setPressed(pressed);
        }

        public TabPreview(
            View parentView,
            BottomSheetTabs.WebTabData tabData,
            BottomSheetTabs.TabDrawable tabDrawable
        ) {
            this.parentView = parentView;
            this.tabData = tabData;
            this.tabDrawable = tabDrawable;
            this.webView = null;// tabData.webView;
            this.bounce = new ButtonBounce(parentView);

            backgroundPaint.setColor(tabData.backgroundColor);
        }

        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tabBounds = new RectF();
        private final Path clipPath = new Path();
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        private final RadialGradient gradient = new RadialGradient(0, 0, 255, new int[] { 0x00000000, 0x30000000 }, new float[] { .5f, 1 }, Shader.TileMode.CLAMP);
        private final Matrix gradientMatrix = new Matrix();
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public void draw(Canvas canvas, RectF bounds, boolean simple, float alpha, float expandProgress, float openingProgress, float contentAlpha) {
            alpha *= Utilities.clamp(1f - ((Math.abs(dismissProgress) - .3f) / .7f), 1f, 0f);
            if (alpha <= 0)
                return;

            float tabScaleY = lerp(1f, 1.3f, expandProgress * (1f - openingProgress));

            final float tabTranslateY = openingProgress * (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() - dp(50));
            canvas.save();
            canvas.rotate(dismissProgress * 20, bounds.centerX() + dp(50) * dismissProgress, bounds.bottom + dp(350));
            final float s = bounce.getScale(.01f);
            canvas.scale(s, s, bounds.centerX(), bounds.centerY());

            final float r = lerp(dp(10), dp(6), expandProgress);
            if (simple) {
                shadowPaint.setColor(0);
                shadowPaint.setShadowLayer(dp(30), 0, dp(10), Theme.multAlpha(0x20000000, alpha * expandProgress * (1f - openingProgress)));
                canvas.drawRoundRect(bounds, r, r, shadowPaint);
                backgroundPaint.setAlpha((int) (0xFF * alpha));
                canvas.drawRoundRect(bounds, r, r, backgroundPaint);
                canvas.restore();
                return;
            }

            clipPath.rewind();
            clipPath.addRoundRect(bounds, r, r, Path.Direction.CW);
            canvas.save();
            shadowPaint.setColor(0);
            shadowPaint.setShadowLayer(dp(30), 0, dp(10), Theme.multAlpha(0x20000000, alpha * expandProgress * (1f - openingProgress)));
            canvas.drawPath(clipPath, shadowPaint);
            canvas.clipPath(clipPath);

            backgroundPaint.setAlpha((int) (0xFF * alpha * expandProgress));
            canvas.drawRoundRect(bounds, r, r, backgroundPaint);

            canvas.save();
            canvas.translate(bounds.left, bounds.top + dp(50) * tabScaleY + tabTranslateY);
            canvas.scale(1f, lerp(1f, 1.25f, expandProgress * (1f - openingProgress)));
            if (tabData != null && tabData.previewNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ((RenderNode) tabData.previewNode).hasDisplayList()) {
                RenderNode node = (RenderNode) tabData.previewNode;
                final float s2 = bounds.width() / node.getWidth();
                canvas.scale(s2, s2);
                node.setAlpha(alpha * expandProgress);
                canvas.drawRenderNode(node);
            } else if (tabData != null && tabData.previewBitmap != null) {
                final float s2 = bounds.width() / tabData.previewBitmap.getWidth();
                canvas.scale(s2, s2);
                bitmapPaint.setAlpha((int) (0xFF * alpha * expandProgress));
                canvas.drawBitmap(tabData.previewBitmap, 0, 0, bitmapPaint);
            } else if (webView != null) {
                final float s2 = bounds.width() / webView.getWidth();
                canvas.scale(s2, s2);
                canvas.saveLayerAlpha(0, 0, webView.getWidth(), webView.getHeight(), (int) (0xFF * alpha * expandProgress), Canvas.ALL_SAVE_FLAG);
                webView.draw(canvas);
                canvas.restore();
            }
            canvas.restore();

            canvas.save();
            gradientPaint.setAlpha((int) (0xFF * alpha * expandProgress * (1f - openingProgress)));
            gradientMatrix.reset();
            final float gradientScale = bounds.height() / 255f;
            gradientMatrix.postScale(gradientScale, gradientScale);
            gradientMatrix.postTranslate(bounds.centerX(), bounds.top);
            gradient.setLocalMatrix(gradientMatrix);
            gradientPaint.setShader(gradient);
            canvas.drawRect(bounds, gradientPaint);
            canvas.restore();

            tabBounds.set(bounds);
            tabBounds.bottom = tabBounds.top + Math.min(bounds.height(), dp(50));
            tabBounds.offset(0, tabTranslateY);
            tabDrawable.setExpandProgress(expandProgress);
            canvas.scale(1f, tabScaleY, tabBounds.centerX(), tabBounds.top);
            tabDrawable.draw(canvas, tabBounds, r, alpha * alpha, contentAlpha);

            canvas.restore();

            canvas.restore();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static void renderNodeToBitmap(RenderNode renderNode, Utilities.Callback<Bitmap> whenBitmapDone) {
        if (renderNode == null || whenBitmapDone == null || renderNode.getWidth() <= 0 || renderNode.getHeight() <= 0) {
            if (whenBitmapDone != null) {
                whenBitmapDone.run(null);
            }
            return;
        }

        final SurfaceTexture surfaceTexture = new SurfaceTexture(false);
        surfaceTexture.setDefaultBufferSize(renderNode.getWidth(), renderNode.getHeight());
        final Surface surface = new Surface(surfaceTexture);

        final Bitmap bitmap = Bitmap.createBitmap(renderNode.getWidth(), renderNode.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas hwCanvas = surface.lockHardwareCanvas();
        hwCanvas.drawRenderNode(renderNode);
        surface.unlockCanvasAndPost(hwCanvas);

        PixelCopy.request(surface, bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                whenBitmapDone.run(bitmap);
            } else {
                bitmap.recycle();
                whenBitmapDone.run(null);
            }
            surface.release();
            surfaceTexture.release();
        }, new Handler());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void renderHardwareViewToBitmap(View view, float offsetY, Utilities.Callback<Bitmap> whenBitmapDone) {
        if (view == null || whenBitmapDone == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            if (whenBitmapDone != null) {
                whenBitmapDone.run(null);
            }
            return;
        }

        final SurfaceTexture surfaceTexture = new SurfaceTexture(false);
        surfaceTexture.setDefaultBufferSize(view.getWidth(), view.getHeight());
        final Surface surface = new Surface(surfaceTexture);

        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas hwCanvas = surface.lockHardwareCanvas();
        hwCanvas.translate(0, offsetY);
        view.draw(hwCanvas);
        surface.unlockCanvasAndPost(hwCanvas);

        PixelCopy.request(surface, bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                whenBitmapDone.run(bitmap);
            } else {
                bitmap.recycle();
                whenBitmapDone.run(null);
            }
            surface.release();
            surfaceTexture.release();
        }, new Handler());
    }

}
