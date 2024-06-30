package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.firebase.encoders.ValueEncoder;
import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Text;
import org.telegram.ui.GradientClip;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.bots.BotWebViewMenuContainer;
import org.telegram.ui.bots.BotWebViewSheet;

import java.util.ArrayList;

public class BottomSheetTabsOverlay extends FrameLayout {

    private BottomSheetTabs tabsView;

    private BotWebViewAttachedSheet dismissingSheet;
    private BotWebViewSheet dismissingSheet2;
    private BotWebViewMenuContainer dismissingMenuContainer;
    private BottomSheetTabs.TabDrawable dismissingTab;
    private ValueAnimator dismissingAnimator;
    private float dismissProgress;

    private final AnimatedFloat animatedCount = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final OverScroller scroller;
    private final int maximumVelocity, minimumVelocity;

    public BottomSheetTabsOverlay(Context context) {
        super(context);

        setWillNotDraw(false);

        scroller = new OverScroller(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + AndroidUtilities.navigationBarHeight, MeasureSpec.EXACTLY));
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        closeAllButtonBackground.setHotspot(event.getX(), event.getY());
                    }
                    closeAllButtonBackground.setState(hitCloseAllButton ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[] {});
                }
                verticallyScrolling = false;
                horizontallySwiping = false;
                pressTabClose = false;
                if (pressTab != null) {
                    pressTab.cancelDismissAnimator();
                    pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains((int) (event.getX() - pressTab.clickBounds.left), (int) (event.getY() - pressTab.clickBounds.top - dp(24)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && pressTabClose) {
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
        return Math.min(SharedConfig.botTabs3DEffect ? 3 : 6, getScrollRange());
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
        return getScrollRange(animated) - getScrollWindow() - getScrollWindow() / 3f * Utilities.clamp(4f - getScrollRange(animated), .5f, 0);
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

    public boolean dismissSheet(BotWebViewAttachedSheet sheet) {
        if (sheet == null) return false;
        if (tabsView == null) return false;

        if (dismissingSheet != null) {
            if (dismissingAnimator != null) {
                dismissingAnimator.end();
                dismissingAnimator = null;
            }
        }

        dismissingSheet = sheet;
        sheet.getWindowView().setDrawingFromOverlay(true);
        invalidate();

        if (dismissingAnimator != null) {
            dismissingAnimator.cancel();
        }

        BottomSheetTabs.WebTabData tab = sheet.saveState();
        dismissingTab = tabsView.pushTab(tab);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            renderHardwareViewToBitmap(tab.webView, -tab.webViewScroll, b -> tab.previewBitmap = b);
        }

        dismissProgress = 0;
        dismissingAnimator = ValueAnimator.ofFloat(0, 1);
        dismissingAnimator.addUpdateListener(anm -> {
            dismissProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        dismissingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (tab.webView != null && tab.previewBitmap == null && tab.webViewWidth > 0 && tab.webViewHeight > 0) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        tab.previewBitmap = Bitmap.createBitmap(tab.webViewWidth, tab.webViewHeight, Bitmap.Config.RGB_565);
                        Canvas canvas = new Canvas(tab.previewBitmap);
                        canvas.translate(0, -tab.webViewScroll);
                        tab.webView.draw(canvas);
                    }
                }
                sheet.release();
                dismissingSheet = null;
                invalidate();
            }
        });
        if (slowerDismiss || sheet.getFullSize()) {
            AndroidUtilities.applySpring(dismissingAnimator, 260, 30, 1);
        } else {
            AndroidUtilities.applySpring(dismissingAnimator, 350, 30, 1);
        }
        dismissingAnimator.start();

        slowerDismiss = false;

        return true;
    }

    public boolean dismissSheet(BotWebViewSheet sheet) {
        if (sheet == null) return false;
        if (tabsView == null) return false;

        if (dismissingSheet2 != null) {
            if (dismissingAnimator != null) {
                dismissingAnimator.end();
                dismissingAnimator = null;
            }
        }

        dismissingSheet2 = sheet;
        sheet.getWindowView().setDrawingFromOverlay(true);
        invalidate();

        if (dismissingAnimator != null) {
            dismissingAnimator.cancel();
        }

        BottomSheetTabs.WebTabData tab = sheet.saveState();
        dismissingTab = tabsView.pushTab(tab);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            renderHardwareViewToBitmap(tab.webView, -tab.webViewScroll, b -> tab.previewBitmap = b);
        }

        dismissProgress = 0;
        dismissingAnimator = ValueAnimator.ofFloat(0, 1);
        dismissingAnimator.addUpdateListener(anm -> {
            dismissProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        dismissingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (tab.webView != null && tab.previewBitmap == null && tab.webViewWidth > 0 && tab.webViewHeight > 0) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        tab.previewBitmap = Bitmap.createBitmap(tab.webViewWidth, tab.webViewHeight, Bitmap.Config.RGB_565);
                        Canvas canvas = new Canvas(tab.previewBitmap);
                        canvas.translate(0, -tab.webViewScroll);
                        tab.webView.draw(canvas);
                    }
                }
                sheet.release();
                dismissingSheet2 = null;
                invalidate();
            }
        });
        AndroidUtilities.applySpring(dismissingAnimator, 350, 30, 1);
        dismissingAnimator.setDuration(dismissingAnimator.getDuration() * 2);
        dismissingAnimator.start();

        slowerDismiss = false;

        return true;
    }

    public boolean dismissSheet(BotWebViewMenuContainer menuContainer) {
        if (menuContainer == null) return false;
        if (tabsView == null) return false;

        dismissingMenuContainer = menuContainer;
        menuContainer.setDrawingFromOverlay(true);
        invalidate();

        if (dismissingAnimator != null) {
            dismissingAnimator.cancel();
        }

        BottomSheetTabs.WebTabData tab = menuContainer.saveState();
        dismissingTab = tabsView.pushTab(tab);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            renderHardwareViewToBitmap(tab.webView, -tab.webViewScroll, b -> tab.previewBitmap = b);
        }

        dismissProgress = 0;
        dismissingAnimator = ValueAnimator.ofFloat(0, 1);
        dismissingAnimator.addUpdateListener(anm -> {
            dismissProgress = (float) anm.getAnimatedValue();
            invalidate();
        });
        dismissingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (tab.webView != null && tab.previewBitmap == null && tab.webViewWidth > 0 && tab.webViewHeight > 0) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        tab.previewBitmap = Bitmap.createBitmap(tab.webViewWidth, tab.webViewHeight, Bitmap.Config.RGB_565);
                        Canvas canvas = new Canvas(tab.previewBitmap);
                        canvas.translate(0, -tab.webViewScroll);
                        tab.webView.draw(canvas);
                    }
                }
                menuContainer.onDismiss();
                menuContainer.setDrawingFromOverlay(false);
                dismissingMenuContainer = null;
                invalidate();
            }
        });
        AndroidUtilities.applySpring(dismissingAnimator, 350, 30, 1);
        dismissingAnimator.setDuration(dismissingAnimator.getDuration());
        dismissingAnimator.start();

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
        blurBitmap = AndroidUtilities.makeBlurBitmap(view, 14, 14);

        blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : +.25f);
        blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        blurMatrix = new Matrix();
    }

    private final RectF tabsViewBounds = new RectF();
    private final ArrayList<TabPreview> tabs = new ArrayList<>();

    public void openTabsView() {
        if (tabsView == null || !(tabsView.getParent() instanceof View)) return;
        View actionBarLayout = (View) tabsView.getParent();

        actionBarLayout.getLocationOnScreen(pos);
        getLocationOnScreen(pos2);
        tabsViewBounds.set(pos[0] - pos2[0], pos[1] - pos2[1], pos[0] - pos2[0] + actionBarLayout.getWidth(), pos[1] - pos2[1] + actionBarLayout.getHeight());

        prepareBlur(actionBarLayout);
        clearTabs();
        prepareTabs();
        animateOpen(true);
    }

    private void clearTabs() {
//        for (int i = 0; i < tabs.size(); ++i) {
//            TabPreview tab = tabs.get(i);
//            if (tab.webView != null) {
//                tab.webView.onPause();
//                AndroidUtilities.removeFromParent(tab.webView);
//            }
//        }
        tabs.clear();
    }

    private void prepareTabs() {
        ArrayList<BottomSheetTabs.WebTabData> tabs = tabsView.getTabs();
        ArrayList<BottomSheetTabs.TabDrawable> tabDrawables = tabsView.getTabDrawables();

        for (int i = tabs.size() - 1; i >= 0; --i) {
            BottomSheetTabs.WebTabData tabData = tabs.get(i);
//            if (tabData.webView != null) {
//                AndroidUtilities.removeFromParent(tabData.webView);
//                tabData.webView.onResume();
//                tabData.webView.post(tabData.webView::onPause);
//                addView(tabData.webView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
//            }
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
                if (!isOpen) {
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
            float radius = dismissingSheet.getWindowView().drawInto(canvas, rect, dismissProgress, clipRect);

            if (dismissingTab != null) {
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                final float y = clipRect.top - dp(50) * (1f - dismissProgress);
                rect.set(clipRect.left, y, clipRect.right, y + dp(50));
                tabsView.setupTab(dismissingTab);
                dismissingTab.draw(canvas, rect, radius, dismissProgress);
                canvas.restore();
            }
        }

        if (dismissingSheet2 != null) {
            BotWebViewSheet.WindowView windowView = dismissingSheet2.getWindowView();
            getLocationOnScreen(pos2);
            tabsView.getLocationOnScreen(pos);
            tabsView.getTabBounds(rect, 0);
            rect.offset(pos[0] - pos2[0], pos[1] - pos2[1]);
            float radius = windowView.drawInto(canvas, rect, dismissProgress, clipRect);

            if (dismissingTab != null) {
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                final float y = clipRect.top - dp(50) * (1f - dismissProgress);
                rect.set(clipRect.left, y, clipRect.right, y + dp(50));
                tabsView.setupTab(dismissingTab);
                dismissingTab.draw(canvas, rect, radius, dismissProgress);
                canvas.restore();
            }
        }

        if (dismissingMenuContainer != null) {
            getLocationOnScreen(pos2);
            dismissingMenuContainer.getLocationOnScreen(pos3);
            tabsView.getLocationOnScreen(pos);
            tabsView.getTabBounds(rect, 0);
            rect.offset(pos[0] - pos2[0], pos[1] - pos2[1]);
            float radius = dismissingMenuContainer.drawInto(canvas, rect, dismissProgress, clipRect);

            if (dismissingTab != null) {
                clipPath.rewind();
                clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                final float y = clipRect.top - dp(50) * (1f - dismissProgress);
                rect.set(clipRect.left, y, clipRect.right, y + dp(50));
                tabsView.setupTab(dismissingTab);
                dismissingTab.draw(canvas, rect, radius, dismissProgress);
                canvas.restore();
            }
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
        if (openProgress <= 0) return;

        canvas.save();
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
        final int height = (int) (AndroidUtilities.isTablet() ? Math.min(thisWidth, thisHeight) * .75f : thisHeight * .75f);
        final float cx = thisWidth / 2f;
        float tabCount = 0;
        for (int i = 0; i < tabs.size(); ++i) {
            final TabPreview tab = tabs.get(i);
            tabCount += tab.tabDrawable.index >= 0 ? 1 : 0;
        }
        final float count = animatedCount.set(tabCount);
        boolean reverse = true;
        for (int i = (reverse ? 0 : tabs.size() - 1); (reverse ? i < tabs.size() : i >= 0); i = (reverse ? i + 1 : i - 1)) {
            final TabPreview tab = tabs.get(i);

            final float position = count - 1 - tab.tabDrawable.getPosition();
            final float scroll = (position - getScrollOffset()) / getScrollWindow();
            final float scrollT = Math.max(scroll, 0f);
            final float oscrollT = Math.max(Math.min(scroll, 1f), -4);

            float alpha = 1f;
            float top, bottom, y;
            if (SharedConfig.botTabs3DEffect) {
                top = paddingTop + dp(6) * Math.min(5, position);
                bottom = thisHeight - paddingBottom - height * .26f;// - dp(6) * Math.min(5, count - position);
                y = top + (bottom - top) * scroll;
                alpha = 1f; // Utilities.clamp(oscrollT * 4f + 1f, 1f, 0f);
            } else {
                top = paddingTop + dp(20) * ((float) Math.pow(1.1f, position) - 1f);
                bottom = thisHeight - paddingBottom - height * .26f;
                y = top + (bottom - top) * (float) (Math.pow(scrollT, 2));
                y = Math.min(y, thisHeight);
            }

            if (alpha <= 0) continue;

            rect2.set(cx - width / 2f, y, cx + width / 2f, y + height);
            tabsView.getTabBounds(rect, Utilities.clamp(tab.tabDrawable.getPosition(), 1, 0));
            rect.offset(tabsView.getX(), tabsView.getY());
            AndroidUtilities.lerpCentered(rect, rect2, openProgress, rect2);

            if (tabsView != null) {
                tabsView.setupTab(tab.tabDrawable);
            }

            canvas.save();
            tab.clickBounds.set(rect2);
            if (SharedConfig.botTabs3DEffect) {
//                final float scale = lerp(1f, .5f, openProgress);
//                canvas.scale(scale, scale, rect2.centerX(), rect2.centerY());
//                scale(tab.clickBounds, scale, rect.centerX(), rect2.centerY());

                Canvas tabCanvas = canvas;
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alpha < 1 && false) {
//                    if (tab.node == null) {
//                        tab.node = new RenderNode("a");
//                    }
//                    tab.node.setRenderEffect(RenderEffect.createBlurEffect((1f - alpha) * 300, (1f - alpha) * 300, Shader.TileMode.CLAMP));
//                    tab.node.setPosition(0, 0, (int) thisWidth, (int) thisHeight);
//                    tabCanvas = tab.node.beginRecording();
//                }

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

//                final float ws = .75f;
//                final float wss = 1.2f;
//                final float hs = 1f;
//                final float wstop = 1f;
//                final float wsbottom = 1.2f;
//                tab.dst[0] = rect2.centerX() - rect2.width() / 2f * ws * wstop;
//                tab.dst[1] = rect2.centerY() - rect2.height() / 2f * hs;
//                tab.dst[2] = rect2.centerX() + rect2.width() / 2f * ws * wstop;
//                tab.dst[3] = rect2.centerY() - rect2.height() / 2f * hs;
//                tab.dst[4] = rect2.centerX() + rect2.width() / 2f * ws * (2f - wsbottom);
//                tab.dst[5] = rect2.centerY() + rect2.height() / 2f * hs;
//                tab.dst[6] = rect2.centerX() - rect2.width() / 2f * ws * (2f - wsbottom);
//                tab.dst[7] = rect2.centerY() + rect2.height() / 2f * hs;

                tab.dst[0] = rect2.left;
                tab.dst[1] = rect2.top - dp(p);
                tab.dst[2] = rect2.right;
                tab.dst[3] = rect2.top - dp(p);
                final float s1 = .83f, s2 = .6f;
                tab.dst[4] = rect2.centerX() + rect2.width() / 2f * lerp(1f, s1, openProgress);
                tab.dst[5] = rect2.top - dp(p) + (rect2.height() * Sh + dp(p + p)) * lerp(1f, s2, openProgress);
                tab.dst[6] = rect2.centerX() - rect2.width() / 2f * lerp(1f, s1, openProgress);
                tab.dst[7] = rect2.top - dp(p) + (rect2.height() * Sh + dp(p + p)) * lerp(1f, s2, openProgress);

                tab.matrix.setPolyToPoly(tab.src, 0, tab.dst, 0, 4);
                tabCanvas.concat(tab.matrix);

                tab.draw(tabCanvas, rect2, lerp(tab.tabDrawable.getAlpha(), alpha, openProgress), openProgress);

                canvas.restore();
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alpha < 1 && false) {
//                    tab.node.endRecording();
//                    canvas.drawRenderNode(tab.node);
//                }
            } else {
                final float s = lerp(
                        1f,
                        lerp(
                                lerp(1f, 1f - Utilities.clamp(count * .1f, .5f, .25f), 1f - scrollT),
                                Math.min(1, (float) Math.pow(0.7f, 1f - oscrollT)),
                                Utilities.clamp(count - 3, 1, 0)
                        ),
                        openProgress
                );
                canvas.scale(s, s, rect2.centerX(), rect2.top);
                scale(tab.clickBounds, s, rect.centerX(), rect2.top);

                tab.draw(canvas, rect2, lerp(tab.tabDrawable.getAlpha(), 1f, openProgress), openProgress);
                canvas.restore();
            }
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

    private void scale(RectF rect, float s, float px, float py) {
        final float wl = px - rect.left, wr = rect.right - px;
        final float ht = py - rect.top, hb = rect.bottom - py;
        rect.set(
            px - wl * s,
            py - ht * s,
            px + wr * s,
            py + hb * s
        );
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        drawDismissingTab(canvas);
        drawTabsPreview(canvas);
    }

    private static class TabPreview {

        public final RectF clickBounds = new RectF();

        public final View parentView;
        public final BottomSheetTabs.WebTabData tabData;
        public final BottomSheetTabs.TabDrawable tabDrawable;
        public final Bitmap previewBitmap;
        public WebView webView;
        public final Object previewNode;

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
            this.previewBitmap = tabData.previewBitmap;
            this.webView = null;// tabData.webView;
            this.previewNode = tabData.previewNode;
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

        public void draw(Canvas canvas, RectF bounds, float alpha, float expandProgress) {
            alpha *= Utilities.clamp(1f - ((Math.abs(dismissProgress) - .3f) / .7f), 1f, 0f);
            if (alpha <= 0)
                return;

            float tabScaleY = 1f;
            if (SharedConfig.botTabs3DEffect) {
                tabScaleY = lerp(1f, 1.3f, expandProgress);
            }

            canvas.save();
            canvas.rotate(dismissProgress * 20, bounds.centerX() + dp(50) * dismissProgress, bounds.bottom + dp(350));
            final float s = bounce.getScale(.01f);
            canvas.scale(s, s, bounds.centerX(), bounds.centerY());

            final float r = lerp(dp(10), dp(8), expandProgress);
            clipPath.rewind();
            clipPath.addRoundRect(bounds, r, r, Path.Direction.CW);
            canvas.save();
            shadowPaint.setColor(0);
            shadowPaint.setShadowLayer(dp(30), 0, dp(10), Theme.multAlpha(0x20000000, alpha * (expandProgress > .7f ? expandProgress : 0)));
            canvas.drawPath(clipPath, shadowPaint);
            canvas.clipPath(clipPath);

            backgroundPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(bounds, r, r, backgroundPaint);

            canvas.save();
            canvas.translate(bounds.left, bounds.top + dp(50) * tabScaleY);
            canvas.scale(1f, lerp(1f, 1.25f, expandProgress));
            if (previewNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ((RenderNode) previewNode).hasDisplayList()) {
                RenderNode node = (RenderNode) previewNode;
                final float s2 = bounds.width() / node.getWidth();
                canvas.scale(s2, s2);
                node.setAlpha(alpha * expandProgress);
                canvas.drawRenderNode(node);
            } else if (previewBitmap != null) {
                final float s2 = bounds.width() / previewBitmap.getWidth();
                canvas.scale(s2, s2);
                bitmapPaint.setAlpha((int) (0xFF * alpha * expandProgress));
                canvas.drawBitmap(previewBitmap, 0, 0, bitmapPaint);
            } else if (webView != null) {
                final float s2 = bounds.width() / webView.getWidth();
                canvas.scale(s2, s2);
                canvas.saveLayerAlpha(0, 0, webView.getWidth(), webView.getHeight(), (int) (0xFF * alpha * expandProgress), Canvas.ALL_SAVE_FLAG);
                webView.draw(canvas);
                canvas.restore();
            }
            canvas.restore();

            canvas.save();
            gradientPaint.setAlpha((int) (0xFF * alpha * expandProgress));
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
            tabDrawable.setExpandProgress(expandProgress);
            canvas.scale(1f, tabScaleY, tabBounds.centerX(), tabBounds.top);
            tabDrawable.draw(canvas, tabBounds, r, alpha * alpha);

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

        PixelCopy.request(surface, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                if (copyResult == PixelCopy.SUCCESS) {
                    whenBitmapDone.run(bitmap);
                } else {
                    bitmap.recycle();
                    whenBitmapDone.run(null);
                }
                surface.release();
                surfaceTexture.release();
            }
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

        PixelCopy.request(surface, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                if (copyResult == PixelCopy.SUCCESS) {
                    whenBitmapDone.run(bitmap);
                } else {
                    bitmap.recycle();
                    whenBitmapDone.run(null);
                }
                surface.release();
                surfaceTexture.release();
            }
        }, new Handler());
    }

}
