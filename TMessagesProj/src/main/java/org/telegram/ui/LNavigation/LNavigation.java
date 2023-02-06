package org.telegram.ui.LNavigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BackButtonMenu;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugProvider;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LNavigation extends FrameLayout implements INavigationLayout, FloatingDebugProvider {
    private final static boolean ALLOW_OPEN_STIFFNESS_CONTROL = false;
    private final static boolean USE_ACTIONBAR_CROSSFADE = false;
    private static float SPRING_STIFFNESS = 1000f;
    private static float SPRING_DAMPING_RATIO = 1f;
    private final static float SPRING_STIFFNESS_PREVIEW = 650f;
    private final static float SPRING_STIFFNESS_PREVIEW_OUT = 800f;
    private final static float SPRING_STIFFNESS_PREVIEW_EXPAND = 750f;
    private final static float SPRING_MULTIPLIER = 1000f;
    private List<BackButtonMenu.PulledDialog> pulledDialogs = new ArrayList<>();

    /**
     * Temp rect to calculate if it's ignored view
     */
    private Rect ignoreRect = new Rect();

    /**
     * Temp path for clipping
     */
    private Path path = new Path();

    /**
     * Darker paint
     */
    private Paint dimmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Flag if we should remove extra height for action bar
     */
    private boolean removeActionBarExtraHeight;

    /**
     * Current fragment stack
     */
    private List<BaseFragment> fragmentStack = new ArrayList<>();

    /**
     * Unmodifiable fragment stack for {@link LNavigation#getFragmentStack()}
     */
    private List<BaseFragment> unmodifiableFragmentStack = Collections.unmodifiableList(fragmentStack);

    /**
     * Delegate for this view
     */
    private INavigationLayoutDelegate delegate;

    /**
     * A listener when fragment stack is being changed
     */
    private Runnable onFragmentStackChangedListener;

    /**
     * Drawer layout container (For the swipe-back-to-drawer feature)
     */
    private DrawerLayoutContainer drawerLayoutContainer;

    /**
     * Currently running spring animation
     */
    private SpringAnimation currentSpringAnimation;

    /**
     * Overlay layout for containers like shared ActionBar
     */
    private FrameLayout overlayLayout;

    /**
     * Current swipe progress
     */
    private float swipeProgress;

    /**
     * Start scroll offset
     */
    private float startScroll;

    /**
     * Header shadow drawable
     */
    private Drawable headerShadowDrawable;

    /**
     * Front view shadow drawable
     */
    private Drawable layerShadowDrawable;

    /**
     * Gesture detector for scroll
     */
    private GestureDetectorCompat gestureDetector;

    /**
     * If there's currently scroll in progress
     */
    private boolean isSwipeInProgress;

    /**
     * If swipe back should be disallowed
     */
    private boolean isSwipeDisallowed;

    /**
     * If set, should be canceled if trying to open another fragment
     */
    private Runnable delayedPresentAnimation;

    /**
     * If navigation is used in bubble mode
     */
    private boolean isInBubbleMode;

    /**
     * If device is currently showing action mode over our ActionBar
     */
    private boolean isInActionMode;

    /**
     * If menu buttons in preview should be highlighted
     */
    private boolean highlightActionButtons = false;

    /**
     * Custom animation in progress
     */
    private AnimatorSet customAnimation;

    /**
     * Preview fragment's menu
     */
    private ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu;

    /**
     * A blurred snapshot of background fragment
     */
    private Bitmap blurredBackFragmentForPreview;

    /**
     * Snapshot of a small preview fragment
     */
    private Bitmap previewFragmentSnapshot;

    /**
     * Bounds of small preview fragment
     */
    private Rect previewFragmentRect = new Rect();

    /**
     * Preview expand progress
     */
    private float previewExpandProgress;

    /**
     * Paint for blurred snapshot
     */
    private Paint blurPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);

    /**
     * Back button drawable
     */
    private MenuDrawable menuDrawable = new MenuDrawable(MenuDrawable.TYPE_DEFAULT);

    /**
     * View that captured current touch input
     */
    private View touchCapturedView;

    /**
     * Flag if layout was portrait
     */
    private boolean wasPortrait;

    /**
     * Callback after preview fragment is opened
     */
    private Runnable previewOpenCallback;

    /**
     * Flag if navigation view should disappear when last fragment closes
     */
    private boolean useAlphaAnimations;

    /**
     * Background view for tablets
     */
    private View backgroundView;

    /**
     * Flag that indicates that user can press button of the preview menu
     */
    private boolean allowToPressByHover;

    /**
     * Flag if menu hover should be allowed (Only first time opening preview)
     */
    private boolean isFirstHoverAllowed;

    // TODO: Split theme logic to another component
    private ValueAnimator themeAnimator;
    private StartColorsProvider startColorsProvider = new StartColorsProvider();
    private Theme.MessageDrawable messageDrawableOutStart;
    private Theme.MessageDrawable messageDrawableOutMediaStart;
    private ThemeAnimationSettings.onAnimationProgress animationProgressListener;
    private ArrayList<ThemeDescription.ThemeDescriptionDelegate> themeAnimatorDelegate = new ArrayList<>();
    private ArrayList<ThemeDescription> presentingFragmentDescriptions;

    private float themeAnimationValue;
    private ArrayList<ArrayList<ThemeDescription>> themeAnimatorDescriptions = new ArrayList<>();
    private ArrayList<int[]> animateStartColors = new ArrayList<>();
    private ArrayList<int[]> animateEndColors = new ArrayList<>();

    private int fromBackgroundColor;

    private LinearLayout stiffnessControl;
    private CheckBoxCell openChatCheckbox;

    private String titleOverlayTitle;
    private int titleOverlayTitleId;
    private Runnable titleOverlayAction;

    public LNavigation(@NonNull Context context) {
        this(context, null);
    }

    public LNavigation(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        overlayLayout = new FrameLayout(context);
        addView(overlayLayout);

        headerShadowDrawable = getResources().getDrawable(R.drawable.header_shadow).mutate();
        layerShadowDrawable = getResources().getDrawable(R.drawable.layer_shadow).mutate();

        dimmPaint.setColor(0x7a000000);
        setWillNotDraw(false);

        menuDrawable.setRoundCap();

        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (highlightActionButtons && !allowToPressByHover && isFirstHoverAllowed && isInPreviewMode() && (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) && !isSwipeInProgress && previewMenu != null) {
                    allowToPressByHover = true;
                }

                if (allowToPressByHover && previewMenu != null && (previewMenu.getSwipeBack() == null || previewMenu.getSwipeBack().isForegroundOpen())) {
                    for (int i = 0; i < previewMenu.getItemsCount(); ++i) {
                        ActionBarMenuSubItem button = (ActionBarMenuSubItem) previewMenu.getItemAt(i);
                        if (button != null) {
                            Drawable ripple = button.getBackground();
                            button.getGlobalVisibleRect(AndroidUtilities.rectTmp2);
                            boolean shouldBeEnabled = AndroidUtilities.rectTmp2.contains((int) e2.getX(), (int) e2.getY()), enabled = ripple.getState().length == 2;
                            if (shouldBeEnabled != enabled) {
                                ripple.setState(shouldBeEnabled ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
                                if (shouldBeEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    try {
                                        button.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                                    } catch (Exception ignore) {}
                                }
                            }
                        }
                    }
                }

                if (!isSwipeInProgress && !isSwipeDisallowed) {
                    if (Math.abs(distanceX) >= Math.abs(distanceY) * 1.5f && distanceX <= -touchSlop && !isIgnoredView(getForegroundView(), e2, ignoreRect) &&
                            getLastFragment() != null && getLastFragment().canBeginSlide() && getLastFragment().isSwipeBackEnabled(e2) && fragmentStack.size() >= 2 && !isInActionMode &&
                            !isInPreviewMode()) {
                        isSwipeInProgress = true;

                        startScroll = swipeProgress - MathUtils.clamp((e2.getX() - e1.getX()) / getWidth(), 0, 1);

                        if (getParentActivity().getCurrentFocus() != null) {
                            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                        }

                        if (getBackgroundView() != null) {
                            getBackgroundView().setVisibility(VISIBLE);
                        }
                        getLastFragment().prepareFragmentToSlide(true, true);
                        getLastFragment().onBeginSlide();
                        BaseFragment bgFragment = getBackgroundFragment();
                        if (bgFragment != null) {
                            bgFragment.setPaused(false);
                            bgFragment.prepareFragmentToSlide(false, true);
                            bgFragment.onBeginSlide();
                        }

                        MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        for (int i = 0; i < getChildCount(); i++) {
                            getChildAt(i).dispatchTouchEvent(e);
                        }
                        e.recycle();

                        invalidateActionBars();
                    } else {
                        isSwipeDisallowed = true;
                    }
                }

                if (isSwipeInProgress) {
                    swipeProgress = MathUtils.clamp(startScroll + (e2.getX() - e1.getX()) / getWidth(), 0, 1);
                    invalidateTranslation();
                }
                return isSwipeInProgress;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isSwipeInProgress) {
                    if (velocityX >= 800) {
                        closeLastFragment(true, false, velocityX / 15f);
                        clearTouchFlags();
                        return true;
                    }
                }
                return false;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);

        stiffnessControl = new LinearLayout(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stiffnessControl.setElevation(AndroidUtilities.dp(12));
        }
        stiffnessControl.setOrientation(LinearLayout.VERTICAL);
        stiffnessControl.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(String.format(Locale.ROOT, "Stiffness: %f", SPRING_STIFFNESS));
        stiffnessControl.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));

        SeekBarView seekBarView = new SeekBarView(context);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                titleView.setText(String.format(Locale.ROOT, "Stiffness: %f", 500f + progress * 1000f));
                if (stop) {
                    SPRING_STIFFNESS = 500f + progress * 1000f;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBarView.setProgress((SPRING_STIFFNESS - 500f) / 1000f);
        stiffnessControl.addView(seekBarView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38));

        TextView dampingTitle = new TextView(context);
        dampingTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        dampingTitle.setGravity(Gravity.CENTER);
        dampingTitle.setText(String.format(Locale.ROOT, "Damping ratio: %f", SPRING_DAMPING_RATIO));
        stiffnessControl.addView(dampingTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));

        seekBarView = new SeekBarView(context);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                dampingTitle.setText(String.format(Locale.ROOT, "Damping ratio: %f", 0.2f + progress * 0.8f));
                if (stop) {
                    SPRING_DAMPING_RATIO = 0.2f + progress * 0.8f;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBarView.setProgress((SPRING_DAMPING_RATIO - 0.2f) / 0.8f);
        stiffnessControl.addView(seekBarView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38));

        openChatCheckbox = new CheckBoxCell(context, 1);
        openChatCheckbox.setText("Show chat open measurement", null, false, false);
        openChatCheckbox.setOnClickListener(v -> openChatCheckbox.setChecked(!openChatCheckbox.isChecked(), true));
        stiffnessControl.addView(openChatCheckbox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));

        stiffnessControl.setVisibility(GONE);
        overlayLayout.addView(stiffnessControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() >= 3) {
            throw new IllegalStateException("LNavigation must have no more than 3 child views!");
        }

        super.addView(child, index, params);
    }

    public boolean doShowOpenChat() {
        return openChatCheckbox.isChecked();
    }

    public LinearLayout getStiffnessControl() {
        return stiffnessControl;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);

        if (disallowIntercept && isSwipeInProgress) {
            isSwipeInProgress = false;
            onReleaseTouch();
        }
        isSwipeDisallowed = disallowIntercept;
    }

    private void animateReset() {
        BaseFragment fragment = getLastFragment();
        BaseFragment bgFragment = getBackgroundFragment();
        if (fragment == null) {
            return;
        }

        fragment.onTransitionAnimationStart(true, true);

        FloatValueHolder valueHolder = new FloatValueHolder(swipeProgress * SPRING_MULTIPLIER);
        currentSpringAnimation = new SpringAnimation(valueHolder)
                .setSpring(new SpringForce(0f)
                        .setStiffness(SPRING_STIFFNESS)
                        .setDampingRatio(SPRING_DAMPING_RATIO));
        currentSpringAnimation.addUpdateListener((animation, value, velocity) -> {
            swipeProgress = value / SPRING_MULTIPLIER;
            invalidateTranslation();
            fragment.onTransitionAnimationProgress(true, 1f - swipeProgress);
        });
        Runnable onEnd = ()->{
            fragment.onTransitionAnimationEnd(true, true);
            fragment.prepareFragmentToSlide(true, false);

            swipeProgress = 0f;
            invalidateTranslation();
            if (getBackgroundView() != null) {
                getBackgroundView().setVisibility(GONE);
            }

            fragment.onBecomeFullyVisible();
            if (bgFragment != null) {
                bgFragment.setPaused(true);
                bgFragment.onBecomeFullyHidden();
                bgFragment.prepareFragmentToSlide(false, false);
            }

            currentSpringAnimation = null;
            invalidateActionBars();
        };
        currentSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
            if (animation == currentSpringAnimation) {
                onEnd.run();
            }
        });
        if (swipeProgress != 0f) {
            currentSpringAnimation.start();
        } else {
            onEnd.run();
        }
    }

    private void invalidateActionBars() {
        if (getLastFragment() != null && getLastFragment().getActionBar() != null) {
            getLastFragment().getActionBar().invalidate();
        }
        if (getBackgroundFragment() != null && getBackgroundFragment().getActionBar() != null) {
            getBackgroundFragment().getActionBar().invalidate();
        }
    }

    private boolean processTouchEvent(MotionEvent ev) {
        int act = ev.getActionMasked();
        if (isTransitionAnimationInProgress()) {
            return true;
        }

        if (!gestureDetector.onTouchEvent(ev)) {
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (isFirstHoverAllowed && !allowToPressByHover) {
                        clearTouchFlags();
                    } else if (allowToPressByHover && previewMenu != null) {
                        for (int i = 0; i < previewMenu.getItemsCount(); ++i) {
                            ActionBarMenuSubItem button = (ActionBarMenuSubItem) previewMenu.getItemAt(i);
                            if (button != null) {
                                button.getGlobalVisibleRect(AndroidUtilities.rectTmp2);
                                boolean shouldBeEnabled = AndroidUtilities.rectTmp2.contains((int) ev.getX(), (int) ev.getY());
                                if (shouldBeEnabled) {
                                    button.performClick();
                                }
                            }
                        }

                        clearTouchFlags();
                    } else if (isSwipeInProgress) {
                        clearTouchFlags();
                        onReleaseTouch();
                    } else if (isSwipeDisallowed) {
                        clearTouchFlags();
                    }
                    return false;
            }
        }
        return isSwipeInProgress;
    }

    private void onReleaseTouch() {
        if (swipeProgress < 0.5f) {
            animateReset();
        } else {
            closeLastFragment(true, false);
        }
    }

    private void clearTouchFlags() {
        isSwipeDisallowed = false;
        isSwipeInProgress = false;
        allowToPressByHover = false;
        isFirstHoverAllowed = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        processTouchEvent(event);

        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (processTouchEvent(ev) && touchCapturedView == null) {
            return true;
        }

        if (getChildCount() < 1) {
            return false;
        }

        if (getForegroundView() != null) {
            View capturedView = touchCapturedView;
            View fg = getForegroundView();
            ev.offsetLocation(-getPaddingLeft(), -getPaddingTop());
            boolean overlay = overlayLayout.dispatchTouchEvent(ev) || capturedView == overlayLayout;
            if (overlay) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    touchCapturedView = overlayLayout;

                    MotionEvent e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    for (int i = 0; i < getChildCount() - 1; i++) {
                        getChildAt(i).dispatchTouchEvent(e);
                    }
                    e.recycle();
                }
            }
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                touchCapturedView = null;
            }
            if (overlay) {
                return true;
            }
            if (capturedView != null) {
                return capturedView.dispatchTouchEvent(ev) || ev.getActionMasked() == MotionEvent.ACTION_DOWN;
            }

            boolean foreground = fg.dispatchTouchEvent(ev);
            if (foreground) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    touchCapturedView = fg;
                }
            }
            return foreground || ev.getActionMasked() == MotionEvent.ACTION_DOWN;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean hasIntegratedBlurInPreview() {
        return true;
    }

    @Override
    public boolean presentFragment(NavigationParams params) {
        BaseFragment fragment = params.fragment;
        if (!params.isFromDelay && (fragment == null || checkTransitionAnimation() || delegate != null && params.checkPresentFromDelegate &&
                !delegate.needPresentFragment(this, params) || !fragment.onFragmentCreate() || delayedPresentAnimation != null)) {
            return false;
        }

        if (!fragmentStack.isEmpty() && getChildCount() < 2) {
            rebuildFragments(REBUILD_FLAG_REBUILD_LAST);
        }

        if (getParentActivity().getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        }

        if (!params.isFromDelay) {
            fragment.setInPreviewMode(params.preview);
            if (previewMenu != null) {
                if (previewMenu.getParent() != null) {
                    ((ViewGroup) previewMenu.getParent()).removeView(previewMenu);
                }
            }
            previewMenu = params.menuView;
            fragment.setInMenuMode(previewMenu != null);
            fragment.setParentLayout(this);
        }
        boolean animate = params.preview || MessagesController.getGlobalMainSettings().getBoolean("view_animations", true) &&
                !params.noAnimation && (useAlphaAnimations || fragmentStack.size() >= 1);

        BaseFragment prevFragment = params.isFromDelay ? getBackgroundFragment() : getLastFragment();
        Runnable onFragmentOpened = ()->{
            if (params.removeLast && prevFragment != null) {
                removeFragmentFromStack(prevFragment);
            }
            invalidateActionBars();
        };
        if (animate) {
            if (!params.isFromDelay) {
                if (params.preview) {
                    View bgView = getForegroundView();
                    if (bgView != null) {
                        float scaleFactor = 8;
                        int w = (int) (bgView.getMeasuredWidth() / scaleFactor);
                        int h = (int) (bgView.getMeasuredHeight() / scaleFactor);
                        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        canvas.scale(1.0f / scaleFactor, 1.0f / scaleFactor);
                        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        bgView.draw(canvas);
                        Utilities.stackBlurBitmap(bitmap, Math.max(8, Math.max(w, h) / 150));
                        blurredBackFragmentForPreview = bitmap;
                    }

                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    isFirstHoverAllowed = true;
                }

                FragmentHolderView holderView = onCreateHolderView(fragment);
                if (params.preview) {
                    MarginLayoutParams layoutParams = (MarginLayoutParams) holderView.getLayoutParams();
                    layoutParams.leftMargin = layoutParams.topMargin = layoutParams.rightMargin = layoutParams.bottomMargin = AndroidUtilities.dp(8);

                    if (previewMenu != null) {
                        previewMenu.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec((int) (getHeight() * 0.5f), MeasureSpec.AT_MOST));
                        layoutParams = (MarginLayoutParams) fragment.getFragmentView().getLayoutParams();
                        layoutParams.bottomMargin += AndroidUtilities.dp(8) + previewMenu.getMeasuredHeight();

                        if (LocaleController.isRTL) {
                            previewMenu.setTranslationX(getWidth() - previewMenu.getMeasuredWidth() - AndroidUtilities.dp(8));
                        } else {
                            previewMenu.setTranslationX(-AndroidUtilities.dp(8));
                        }
                        previewMenu.setTranslationY(getHeight() - AndroidUtilities.dp(24) - previewMenu.getMeasuredHeight());
                        holderView.addView(previewMenu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 8));
                    } else {
                        layoutParams.topMargin += AndroidUtilities.dp(52);
                    }

                    holderView.setOnClickListener(v -> finishPreviewFragment());
                }
                addView(holderView, getChildCount() - 1);
                fragmentStack.add(fragment);
                notifyFragmentStackChanged();
                fragment.setPaused(false);

                swipeProgress = 1f;
                invalidateTranslation();
            }

            if (fragment.needDelayOpenAnimation() && !params.delayDone) {
                AndroidUtilities.runOnUIThread(delayedPresentAnimation = ()->{
                    delayedPresentAnimation = null;

                    params.isFromDelay = true;
                    params.delayDone = true;
                    presentFragment(params);
                }, 200);
                return true;
            }

            fragment.onTransitionAnimationStart(true, false);
            if (prevFragment != null) {
                prevFragment.onTransitionAnimationStart(false, false);
            }

            customAnimation = fragment.onCustomTransitionAnimation(true, ()-> {
                customAnimation = null;
                fragment.onTransitionAnimationEnd(true, false);
                if (prevFragment != null) {
                    prevFragment.onTransitionAnimationEnd(false, false);
                }

                swipeProgress = 0f;
                invalidateTranslation();
                if (getBackgroundView() != null) {
                    getBackgroundView().setVisibility(GONE);
                }

                fragment.onBecomeFullyVisible();
                if (prevFragment != null) {
                    prevFragment.onBecomeFullyHidden();
                }
                onFragmentOpened.run();

            });
            if (customAnimation != null) {
                getForegroundView().setTranslationX(0);
                return true;
            }

            invalidateActionBars();
            FloatValueHolder valueHolder = new FloatValueHolder(SPRING_MULTIPLIER);
            currentSpringAnimation = new SpringAnimation(valueHolder)
                    .setSpring(new SpringForce(0f)
                            .setStiffness(params.preview ? SPRING_STIFFNESS_PREVIEW : SPRING_STIFFNESS)
                            .setDampingRatio(params.preview ? 0.6f : SPRING_DAMPING_RATIO));
            currentSpringAnimation.addUpdateListener((animation, value, velocity) -> {
                swipeProgress = value / SPRING_MULTIPLIER;
                invalidateTranslation();
                fragment.onTransitionAnimationProgress(true, 1f - swipeProgress);
            });
            currentSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (animation == currentSpringAnimation) {
                    fragment.onTransitionAnimationEnd(true, false);
                    if (prevFragment != null) {
                        prevFragment.onTransitionAnimationEnd(false, false);
                    }

                    swipeProgress = 0f;
                    invalidateTranslation();
                    if (!params.preview && getBackgroundView() != null) {
                        getBackgroundView().setVisibility(GONE);
                    }

                    fragment.onBecomeFullyVisible();
                    if (prevFragment != null) {
                        prevFragment.onBecomeFullyHidden();
                        prevFragment.setPaused(true);
                    }
                    onFragmentOpened.run();

                    currentSpringAnimation = null;

                    if (params.preview && previewOpenCallback != null) {
                        previewOpenCallback.run();
                    }
                    previewOpenCallback = null;
                }
            });
            currentSpringAnimation.start();
        } else if (!params.preview) {
            if (fragment.needDelayOpenAnimation() && !params.delayDone && params.needDelayWithoutAnimation) {
                AndroidUtilities.runOnUIThread(delayedPresentAnimation = ()->{
                    delayedPresentAnimation = null;

                    params.isFromDelay = true;
                    params.delayDone = true;
                    presentFragment(params);
                }, 200);
                return true;
            }
            addFragmentToStack(fragment, -1, true);
            onFragmentOpened.run();
        }

        return true;
    }

    /**
     * Invalidates current fragment and action bar translation
     */
    private void invalidateTranslation() {
        if (useAlphaAnimations && fragmentStack.size() == 1) {
            backgroundView.setAlpha(1f - swipeProgress);
            setAlpha(1f - swipeProgress);
            return;
        }

        FragmentHolderView bgView = getBackgroundView();
        FragmentHolderView fgView = getForegroundView();

        boolean preview = isInPreviewMode();

        float widthNoPaddings = getWidth() - getPaddingLeft() - getPaddingRight();
        float heightNoPadding = getHeight() - getPaddingTop() - getPaddingBottom();
        if (preview) {
            if (bgView != null) {
                bgView.setTranslationX(0);
                bgView.invalidate();
            }
            if (fgView != null) {
                fgView.setPivotX(widthNoPaddings / 2f);
                fgView.setPivotY(heightNoPadding / 2f);

                fgView.setTranslationX(0);
                fgView.setTranslationY(0);

                float scale = 0.5f + (1f - swipeProgress) * 0.5f;
                fgView.setScaleX(scale);
                fgView.setScaleY(scale);
                fgView.setAlpha(1f - Math.max(swipeProgress, 0f));

                fgView.invalidate();
            }
        } else {
            if (bgView != null) {
                bgView.setTranslationX(-(1f - swipeProgress) * 0.35f * widthNoPaddings);
            }
            if (fgView != null) {
                fgView.setTranslationX(swipeProgress * widthNoPaddings);
            }
        }
        invalidate();

        try {
            if (bgView != null && fgView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int navColor = ColorUtils.blendARGB(fgView.fragment.getNavigationBarColor(), bgView.fragment.getNavigationBarColor(), swipeProgress);
                getParentActivity().getWindow().setNavigationBarColor(navColor);
                AndroidUtilities.setLightNavigationBar(getParentActivity().getWindow(), AndroidUtilities.computePerceivedBrightness(navColor) > 0.721f);
            }
        } catch (Exception ignore) {}

        if (getLastFragment() != null) {
            getLastFragment().onSlideProgressFront(false, swipeProgress);
        }
        if (getBackgroundFragment() != null) {
            getBackgroundFragment().onSlideProgress(false, swipeProgress);
        }
    }

    @Override
    public List<FloatingDebugController.DebugItem> onGetDebugItems() {
        List<FloatingDebugController.DebugItem> items = new ArrayList<>();
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            if (fragment instanceof FloatingDebugProvider) {
                items.addAll(((FloatingDebugProvider) fragment).onGetDebugItems());
            }
            observeDebugItemsFromView(items, fragment.getFragmentView());
        }
        if (ALLOW_OPEN_STIFFNESS_CONTROL) {
            items.add(new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugAltNavigation)));
            items.add(new FloatingDebugController.DebugItem(LocaleController.getString(R.string.DebugAltNavigationToggleControls), () -> getStiffnessControl().setVisibility(getStiffnessControl().getVisibility() == VISIBLE ? GONE : VISIBLE)));
        }
        return items;
    }

    private void observeDebugItemsFromView(List<FloatingDebugController.DebugItem> items, View v) {
        if (v instanceof FloatingDebugProvider) {
            items.addAll(((FloatingDebugProvider) v).onGetDebugItems());
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                observeDebugItemsFromView(items, vg.getChildAt(i));
            }
        }
    }


    private FragmentHolderView getForegroundView() {
        if (getChildCount() >= 2) {
            return (FragmentHolderView) getChildAt(getChildCount() >= 3 ? 1 : 0);
        }
        return null;
    }

    private FragmentHolderView getBackgroundView() {
        if (getChildCount() >= 3) {
            return (FragmentHolderView) getChildAt(0);
        }
        return null;
    }

    @Override
    public boolean checkTransitionAnimation() {
        return isTransitionAnimationInProgress();
    }

    @Override
    public boolean addFragmentToStack(BaseFragment fragment, int position) {
        return addFragmentToStack(fragment, position, false);
    }

    public boolean addFragmentToStack(BaseFragment fragment, int position, boolean fromPresent) {
        if (!fromPresent && (delegate != null && !delegate.needAddFragmentToStack(fragment, this) || !fragment.onFragmentCreate())) {
            return false;
        }
        if (!fragmentStack.isEmpty() && getChildCount() < 2) {
            rebuildFragments(REBUILD_FLAG_REBUILD_LAST);
        }
        fragment.setParentLayout(this);
        if (position == -1 || position >= fragmentStack.size()) {
            BaseFragment lastFragment = getLastFragment();
            if (lastFragment != null) {
                lastFragment.setPaused(true);
                lastFragment.onTransitionAnimationStart(false, true);
                lastFragment.onTransitionAnimationEnd(false, true);
                lastFragment.onBecomeFullyHidden();
            }

            fragmentStack.add(fragment);
            notifyFragmentStackChanged();

            FragmentHolderView holderView = onCreateHolderView(fragment);
            addView(holderView, getChildCount() - 1);

            fragment.setPaused(false);
            fragment.onTransitionAnimationStart(true, false);
            fragment.onTransitionAnimationEnd(true, false);
            fragment.onBecomeFullyVisible();

            if (getBackgroundView() != null) {
                getBackgroundView().setVisibility(GONE);
            }
            getForegroundView().setVisibility(VISIBLE);
        } else {
            fragmentStack.add(position, fragment);
            notifyFragmentStackChanged();

            if (position == fragmentStack.size() - 2) {
                FragmentHolderView holderView = onCreateHolderView(fragment);
                addView(holderView, getChildCount() - 2);
                getBackgroundView().setVisibility(GONE);
                getForegroundView().setVisibility(VISIBLE);
            }
        }
        invalidateTranslation();
        return true;
    }

    private FragmentHolderView onCreateHolderView(BaseFragment fragment) {
        FragmentHolderView holderView;
        if (getChildCount() >= 3) {
            holderView = getBackgroundView();
        } else {
            holderView = new FragmentHolderView(getContext());
        }
        holderView.setFragment(fragment);
        if (holderView.getParent() != null) {
            holderView.setVisibility(VISIBLE);
            removeView(holderView);
        }
        holderView.setOnClickListener(null);
        resetViewProperties(holderView);
        resetViewProperties(fragment.getFragmentView());
        if (fragment.getActionBar() != null) {
            fragment.getActionBar().setTitleOverlayText(titleOverlayTitle, titleOverlayTitleId, titleOverlayAction);
        }
        return holderView;
    }

    private void resetViewProperties(View v) {
        if (v == null) {
            return;
        }

        v.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setAlpha(1f);
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(1);
        v.setScaleY(1);
        v.setTranslationX(0);
        v.setTranslationY(0);
    }

    /**
     * Called to notify ImageLoader and listeners about fragments stack changed
     */
    private void notifyFragmentStackChanged() {
        if (onFragmentStackChangedListener != null) {
            onFragmentStackChangedListener.run();
        }
        if (useAlphaAnimations) {
            if (fragmentStack.isEmpty()) {
                setVisibility(GONE);
                backgroundView.setVisibility(GONE);
            } else {
                setVisibility(VISIBLE);
                backgroundView.setVisibility(VISIBLE);
            }
            if (drawerLayoutContainer != null) {
                drawerLayoutContainer.setAllowOpenDrawer(fragmentStack.isEmpty(), false);
            }
        }
        ImageLoader.getInstance().onFragmentStackChanged();
    }

    @Override
    public void removeFragmentFromStack(BaseFragment fragment, boolean immediate) {
        int i = fragmentStack.indexOf(fragment);
        if (i == -1) {
            return;
        }

        int wasSize = fragmentStack.size();

        fragment.setRemovingFromStack(true);
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentStack.remove(i);
        notifyFragmentStackChanged();

        if (i == wasSize - 1) {
            BaseFragment newLastFragment = getLastFragment();
            if (newLastFragment != null) {
                newLastFragment.setPaused(false);
                newLastFragment.onBecomeFullyVisible();
            }

            FragmentHolderView holderView = getForegroundView();
            if (holderView != null) {
                removeView(holderView);
                resetViewProperties(holderView);
            }

            if (getForegroundView() != null) {
                getForegroundView().setVisibility(VISIBLE);
            }

            if (fragmentStack.size() >= 2) {
                BaseFragment bgFragment = getBackgroundFragment();
                bgFragment.setParentLayout(this);
                if (holderView != null) {
                    holderView.setFragment(bgFragment);
                } else {
                    holderView = onCreateHolderView(bgFragment);
                }
                bgFragment.onBecomeFullyHidden();
                holderView.setVisibility(GONE);
                addView(holderView, getChildCount() - 2);
            }
        } else if (i == wasSize - 2) {
            FragmentHolderView holderView = getBackgroundView();
            if (holderView != null) {
                removeView(holderView);
                resetViewProperties(holderView);
            }

            if (fragmentStack.size() >= 2) {
                BaseFragment bgFragment = getBackgroundFragment();
                bgFragment.setParentLayout(this);
                if (holderView != null) {
                    holderView.setFragment(bgFragment);
                } else {
                    holderView = onCreateHolderView(bgFragment);
                }
                bgFragment.onBecomeFullyHidden();
                holderView.setVisibility(GONE);
                addView(holderView, getChildCount() - 2);
            }
        }

        invalidateTranslation();
    }

    @Override
    public List<BaseFragment> getFragmentStack() {
        return unmodifiableFragmentStack;
    }

    @Override
    public void setFragmentStack(List<BaseFragment> fragmentStack) {
        this.fragmentStack = fragmentStack;
        unmodifiableFragmentStack = Collections.unmodifiableList(fragmentStack);
    }

    @Override
    public void showLastFragment() {
        rebuildFragments(REBUILD_FLAG_REBUILD_LAST);
    }

    @Override
    public void rebuildFragments(int flags) {
        if (currentSpringAnimation != null && currentSpringAnimation.isRunning()) {
            currentSpringAnimation.addEndListener((animation, canceled, value, velocity) -> AndroidUtilities.runOnUIThread(()-> rebuildFragments(flags)));
            return;
        } else if (customAnimation != null && customAnimation.isRunning()) {
            customAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AndroidUtilities.runOnUIThread(()-> rebuildFragments(flags));
                }
            });
            return;
        }
        if (fragmentStack.isEmpty()) {
            while (getChildCount() > 1) {
                removeViewAt(0);
            }
            return;
        }

        boolean rebuildLast = (flags & REBUILD_FLAG_REBUILD_LAST) != 0;
        boolean rebuildBeforeLast = (flags & REBUILD_FLAG_REBUILD_ONLY_LAST) == 0 || rebuildLast && (getBackgroundView() != null && getBackgroundView().fragment != getBackgroundFragment() || getForegroundView() != null && getForegroundView().fragment == getLastFragment());

        if (rebuildBeforeLast) {
            if (getChildCount() >= 3) {
                View child = getChildAt(0);
                if (child instanceof FragmentHolderView) {
                    ((FragmentHolderView) child).fragment.setPaused(true);
                }
                removeViewAt(0);
            }
        }
        if (rebuildLast) {
            if (getChildCount() >= 2) {
                View child = getChildAt(0);
                if (child instanceof FragmentHolderView) {
                    ((FragmentHolderView) child).fragment.setPaused(true);
                }
                removeViewAt(0);
            }
        }
        for (int i = rebuildBeforeLast ? 0 : fragmentStack.size() - 1; i < fragmentStack.size() - (rebuildLast ? 0 : 1); i++) {
            BaseFragment fragment = fragmentStack.get(i);
            fragment.clearViews();
            fragment.setParentLayout(this);
            FragmentHolderView holderView = new FragmentHolderView(getContext());
            holderView.setFragment(fragment);

            if (i >= fragmentStack.size() - 2) {
                addView(holderView, getChildCount() - 1);
            }
        }
        if (delegate != null) {
            delegate.onRebuildAllFragments(this, rebuildLast);
        }
        if (getLastFragment() != null) {
            getLastFragment().setPaused(false);
        }
    }

    @Override
    public void setDelegate(INavigationLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isActionBarInCrossfade() {
        boolean crossfadeNoFragments = USE_ACTIONBAR_CROSSFADE && !isInPreviewMode() && (isSwipeInProgress() || isTransitionAnimationInProgress()) && customAnimation == null;
        return crossfadeNoFragments && getLastFragment() != null && getLastFragment().isActionBarCrossfadeEnabled() && getBackgroundFragment() != null && getBackgroundFragment().isActionBarCrossfadeEnabled();
    }

    @Override
    public void draw(Canvas canvas) {
        boolean crossfade = isActionBarInCrossfade();
        if (useAlphaAnimations) {
            canvas.save();
            path.rewind();
            AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
            canvas.clipPath(path);
        }
        super.draw(canvas);

        if (!isInPreviewMode() && !(useAlphaAnimations && fragmentStack.size() <= 1) && (isSwipeInProgress() || isTransitionAnimationInProgress()) && swipeProgress != 0) {
            int top = getPaddingTop();
            if (crossfade) {
                top += AndroidUtilities.lerp(getBackgroundFragment().getActionBar().getHeight(), getLastFragment().getActionBar().getHeight(), 1f - swipeProgress);
            }
            int widthNoPaddings = getWidth() - getPaddingLeft() - getPaddingRight();
            dimmPaint.setAlpha((int) (0x7a * (1f - swipeProgress)));
            canvas.drawRect(getPaddingLeft(), top, widthNoPaddings * swipeProgress + getPaddingLeft(), getHeight() - getPaddingBottom(), dimmPaint);

            layerShadowDrawable.setAlpha((int) (0xFF * (1f - swipeProgress)));
            layerShadowDrawable.setBounds((int) (widthNoPaddings * swipeProgress - layerShadowDrawable.getIntrinsicWidth()) + getPaddingLeft(), top, (int) (widthNoPaddings * swipeProgress) + getPaddingLeft(), getHeight() - getPaddingBottom());
            layerShadowDrawable.draw(canvas);
        }
        if (useAlphaAnimations) {
            canvas.restore();
        }

        if (previewFragmentSnapshot != null) {
            canvas.save();
            path.rewind();
            AndroidUtilities.rectTmp.set(previewFragmentRect.left * (1f - previewExpandProgress), previewFragmentRect.top * (1f - previewExpandProgress), AndroidUtilities.lerp(previewFragmentRect.right, getWidth(), previewExpandProgress), AndroidUtilities.lerp(previewFragmentRect.bottom, getHeight(), previewExpandProgress));
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
            canvas.clipPath(path);

            canvas.translate(previewFragmentRect.left * (1f - previewExpandProgress), previewFragmentRect.top * (1f - previewExpandProgress));
            canvas.scale(AndroidUtilities.lerp(1f, (float) getWidth() / previewFragmentRect.width(), previewExpandProgress), AndroidUtilities.lerp(1f, (float) getHeight() / previewFragmentRect.height(), previewExpandProgress));
            blurPaint.setAlpha((int) (0xFF * (1f - Math.min(1f, previewExpandProgress))));
            canvas.drawBitmap(previewFragmentSnapshot, 0, 0, blurPaint);
            canvas.restore();
        }

        if (crossfade) {
            BaseFragment foregroundFragment = getLastFragment();
            BaseFragment backgroundFragment = getBackgroundFragment();

            ActionBar fgActionBar = foregroundFragment.getActionBar();
            ActionBar bgActionBar = backgroundFragment.getActionBar();

            boolean useBackDrawable = false;
            boolean backDrawableReverse = false;
            Float backDrawableForcedProgress = null;

            if (backgroundFragment.getBackButtonState() == BackButtonState.MENU && foregroundFragment.getBackButtonState() == BackButtonState.BACK) {
                useBackDrawable = true;
                backDrawableReverse = false;
            } else if (backgroundFragment.getBackButtonState() == BackButtonState.BACK && foregroundFragment.getBackButtonState() == BackButtonState.MENU) {
                useBackDrawable = true;
                backDrawableReverse = true;
            } else if (backgroundFragment.getBackButtonState() == BackButtonState.BACK && foregroundFragment.getBackButtonState() == BackButtonState.BACK) {
                useBackDrawable = true;
                backDrawableForcedProgress = 0f;
            } else if (backgroundFragment.getBackButtonState() == BackButtonState.MENU && foregroundFragment.getBackButtonState() == BackButtonState.MENU) {
                useBackDrawable = true;
                backDrawableForcedProgress = 1f;
            }

            AndroidUtilities.rectTmp.set(0, 0, getWidth(), bgActionBar.getY() + bgActionBar.getHeight());
            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (swipeProgress * 0xFF), Canvas.ALL_SAVE_FLAG);
            bgActionBar.onDrawCrossfadeBackground(canvas);
            canvas.restore();

            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) ((1 - swipeProgress) * 0xFF), Canvas.ALL_SAVE_FLAG);
            fgActionBar.onDrawCrossfadeBackground(canvas);
            canvas.restore();

            if (useBackDrawable) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), bgActionBar.getY() + bgActionBar.getHeight());
                float progress = backDrawableForcedProgress != null ? backDrawableForcedProgress : swipeProgress;
                float bgAlpha = 1f - (bgActionBar.getY() / -(bgActionBar.getHeight() - AndroidUtilities.statusBarHeight));
                float fgAlpha = 1f - (fgActionBar.getY() / -(fgActionBar.getHeight() - AndroidUtilities.statusBarHeight));
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (AndroidUtilities.lerp(bgAlpha, fgAlpha, 1f - swipeProgress) * 0xFF), Canvas.ALL_SAVE_FLAG);
                canvas.translate(AndroidUtilities.dp(16) - AndroidUtilities.dp(1) * (1f - progress), AndroidUtilities.dp(16) + (fgActionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0));
                menuDrawable.setRotation(backDrawableReverse ? progress : 1f - progress, false);
                menuDrawable.draw(canvas);
                canvas.restore();
            }

            AndroidUtilities.rectTmp.set(0, AndroidUtilities.statusBarHeight, getWidth(), bgActionBar.getY() + bgActionBar.getHeight());
            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (swipeProgress * 0xFF), Canvas.ALL_SAVE_FLAG);
            canvas.translate(0, bgActionBar.getY());
            bgActionBar.onDrawCrossfadeContent(canvas, false, useBackDrawable, swipeProgress);
            canvas.restore();

            canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) ((1 - swipeProgress) * 0xFF), Canvas.ALL_SAVE_FLAG);
            canvas.translate(0, fgActionBar.getY());
            fgActionBar.onDrawCrossfadeContent(canvas, true, useBackDrawable, swipeProgress);
            canvas.restore();
        }
    }

    @Override
    public void resumeDelayedFragmentAnimation() {
        if (delayedPresentAnimation != null) {
            AndroidUtilities.cancelRunOnUIThread(delayedPresentAnimation);
            delayedPresentAnimation.run();
        }
    }

    @Override
    public void setUseAlphaAnimations(boolean useAlphaAnimations) {
        this.useAlphaAnimations = useAlphaAnimations;
    }

    @Override
    public void setBackgroundView(View backgroundView) {
        this.backgroundView = backgroundView;
    }

    @Override
    public void closeLastFragment(boolean animated, boolean forceNoAnimation) {
        closeLastFragment(animated, forceNoAnimation, 0);
    }

    public void closeLastFragment(boolean animated, boolean forceNoAnimation, float velocityX) {
        BaseFragment fragment = getLastFragment();
        if (fragment != null && fragment.closeLastFragment()) {
            return;
        }
        if (fragmentStack.isEmpty() || checkTransitionAnimation() || delegate != null && !delegate.needCloseLastFragment(this)) {
            return;
        }

        boolean animate = animated && !forceNoAnimation && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true) && (useAlphaAnimations || fragmentStack.size() >= 2);
        if (animate) {
            AndroidUtilities.hideKeyboard(this);

            BaseFragment lastFragment = getLastFragment();

            BaseFragment newLastFragment = getBackgroundFragment();

            if (getBackgroundView() != null) {
                getBackgroundView().setVisibility(VISIBLE);
            }

            lastFragment.onTransitionAnimationStart(false, true);
            if (newLastFragment != null) {
                newLastFragment.setPaused(false);
            }

            if (swipeProgress == 0) {
                customAnimation = lastFragment.onCustomTransitionAnimation(false, () -> {
                    onCloseAnimationEnd(lastFragment, newLastFragment);

                    customAnimation = null;
                });
                if (customAnimation != null) {
                    getForegroundView().setTranslationX(0);
                    if (getBackgroundView() != null) {
                        getBackgroundView().setTranslationX(0);
                    }
                    return;
                }
            }

            FloatValueHolder valueHolder = new FloatValueHolder(swipeProgress * SPRING_MULTIPLIER);
            currentSpringAnimation = new SpringAnimation(valueHolder)
                    .setSpring(new SpringForce(SPRING_MULTIPLIER)
                            .setStiffness(isInPreviewMode() ? SPRING_STIFFNESS_PREVIEW_OUT : SPRING_STIFFNESS)
                            .setDampingRatio(SPRING_DAMPING_RATIO));
            if (velocityX != 0) {
                currentSpringAnimation.setStartVelocity(velocityX);
            }
            currentSpringAnimation.addUpdateListener((animation, value, velocity) -> {
                swipeProgress = value / SPRING_MULTIPLIER;
                invalidateTranslation();
                lastFragment.onTransitionAnimationProgress(false, swipeProgress);

                if (newLastFragment != null) {
                    lastFragment.onTransitionAnimationProgress(true, swipeProgress);
                }
            });
            currentSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (animation == currentSpringAnimation) {
                    onCloseAnimationEnd(lastFragment, newLastFragment);

                    currentSpringAnimation = null;
                }
            });
            currentSpringAnimation.start();
        } else {
            swipeProgress = 0f;
            removeFragmentFromStack(getLastFragment());
        }
    }

    private void onCloseAnimationEnd(BaseFragment lastFragment, BaseFragment newLastFragment) {
        lastFragment.setPaused(true);
        lastFragment.setRemovingFromStack(true);
        lastFragment.onTransitionAnimationEnd(false, true);
        lastFragment.prepareFragmentToSlide(true, false);
        lastFragment.onBecomeFullyHidden();
        lastFragment.onFragmentDestroy();
        lastFragment.setParentLayout(null);
        fragmentStack.remove(lastFragment);
        notifyFragmentStackChanged();

        FragmentHolderView holderView = getForegroundView();
        if (holderView != null) {
            holderView.setFragment(null);
            removeView(holderView);
            resetViewProperties(holderView);
        }

        if (newLastFragment != null) {
            newLastFragment.prepareFragmentToSlide(false, false);
            newLastFragment.onTransitionAnimationEnd(true, true);
            newLastFragment.onBecomeFullyVisible();
        }

        if (fragmentStack.size() >= 2) {
            BaseFragment prevFragment = getBackgroundFragment();
            prevFragment.setParentLayout(this);

            if (holderView == null) {
                holderView = onCreateHolderView(prevFragment);
            } else {
                holderView.setFragment(prevFragment);
            }
            holderView.setVisibility(GONE);
            addView(holderView, getChildCount() - 2);
        }
        swipeProgress = 0f;
        invalidateTranslation();

        previewMenu = null;
        if (blurredBackFragmentForPreview != null) {
            blurredBackFragmentForPreview.recycle();
            blurredBackFragmentForPreview = null;
        }
        previewOpenCallback = null;
        invalidateActionBars();
    }

    @Override
    public DrawerLayoutContainer getDrawerLayoutContainer() {
        return drawerLayoutContainer;
    }

    @Override
    public void setDrawerLayoutContainer(DrawerLayoutContainer drawerLayoutContainer) {
        this.drawerLayoutContainer = drawerLayoutContainer;
    }

    @Override
    public void setRemoveActionBarExtraHeight(boolean removeExtraHeight) {
        this.removeActionBarExtraHeight = removeExtraHeight;
    }

    private ActionBar getCurrentActionBar() {
        return getLastFragment() != null ? getLastFragment().getActionBar() : null;
    }

    @Override
    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        titleOverlayTitle = title;
        titleOverlayTitleId = titleId;
        titleOverlayAction = action;
        for (BaseFragment fragment : fragmentStack) {
            if (fragment.getActionBar() != null) {
                fragment.getActionBar().setTitleOverlayText(title, titleId, action);
            }
        }
    }

    private void addStartDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        themeAnimatorDescriptions.add(descriptions);
        int[] startColors = new int[descriptions.size()];
        animateStartColors.add(startColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            ThemeDescription description = descriptions.get(a);
            startColors[a] = description.getSetColor();
            ThemeDescription.ThemeDescriptionDelegate delegate = description.setDelegateDisabled();
            if (delegate != null && !themeAnimatorDelegate.contains(delegate)) {
                themeAnimatorDelegate.add(delegate);
            }
        }
    }

    private void addEndDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        int[] endColors = new int[descriptions.size()];
        animateEndColors.add(endColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            endColors[a] = descriptions.get(a).getSetColor();
        }
    }

    @Override
    public void animateThemedValues(ThemeAnimationSettings settings, Runnable onDone) {
        if (themeAnimator != null) {
            themeAnimator.cancel();
            themeAnimator = null;
        }
        int fragmentCount = settings.onlyTopFragment ? 1 : fragmentStack.size();
        Runnable next = () -> {
            boolean startAnimation = false;
            for (int i = 0; i < fragmentCount; i++) {
                BaseFragment fragment;
                if (i == 0) {
                    fragment = getLastFragment();
                } else {
                    if (!isInPreviewMode() && !isPreviewOpenAnimationInProgress() || fragmentStack.size() <= 1) {
                        continue;
                    }
                    fragment = fragmentStack.get(fragmentStack.size() - 2);
                }
                if (fragment != null) {
                    startAnimation = true;
                    if (settings.resourcesProvider != null) {
                        if (messageDrawableOutStart == null) {
                            messageDrawableOutStart = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false, startColorsProvider);
                            messageDrawableOutStart.isCrossfadeBackground = true;
                            messageDrawableOutMediaStart = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, false, startColorsProvider);
                            messageDrawableOutMediaStart.isCrossfadeBackground = true;
                        }
                        startColorsProvider.saveColors(settings.resourcesProvider);
                    }
                    ArrayList<ThemeDescription> descriptions = fragment.getThemeDescriptions();
                    addStartDescriptions(descriptions);
                    if (fragment.getVisibleDialog() instanceof BottomSheet) {
                        BottomSheet sheet = (BottomSheet) fragment.getVisibleDialog();
                        addStartDescriptions(sheet.getThemeDescriptions());
                    } else if (fragment.getVisibleDialog() instanceof AlertDialog) {
                        AlertDialog dialog = (AlertDialog) fragment.getVisibleDialog();
                        addStartDescriptions(dialog.getThemeDescriptions());
                    }
                    if (i == 0) {
                        if (settings.afterStartDescriptionsAddedRunnable != null) {
                            settings.afterStartDescriptionsAddedRunnable.run();
                        }
                    }
                    addEndDescriptions(descriptions);
                    if (fragment.getVisibleDialog() instanceof BottomSheet) {
                        addEndDescriptions(((BottomSheet) fragment.getVisibleDialog()).getThemeDescriptions());
                    } else if (fragment.getVisibleDialog() instanceof AlertDialog) {
                        addEndDescriptions(((AlertDialog) fragment.getVisibleDialog()).getThemeDescriptions());
                    }
                }
            }
            if (startAnimation) {
                if (!settings.onlyTopFragment) {
                    int count = fragmentStack.size() - (isInPreviewMode() || isPreviewOpenAnimationInProgress() ? 2 : 1);
                    boolean needRebuild = false;
                    for (int i = 0; i < count; i++) {
                        BaseFragment fragment = fragmentStack.get(i);
                        fragment.clearViews();
                        fragment.setParentLayout(this);

                        if (i == fragmentStack.size() - 1) {
                            if (getForegroundView() != null) {
                                getForegroundView().setFragment(fragment);
                            } else {
                                needRebuild = true;
                            }
                        } else if (i == fragmentStack.size() - 2) {
                            if (getBackgroundView() != null) {
                                getBackgroundView().setFragment(fragment);
                            } else {
                                needRebuild = true;
                            }
                        }
                    }
                    if (needRebuild) {
                        rebuildFragments(REBUILD_FLAG_REBUILD_LAST);
                    }
                }
                if (settings.instant) {
                    setThemeAnimationValue(1.0f);
                    themeAnimatorDescriptions.clear();
                    animateStartColors.clear();
                    animateEndColors.clear();
                    themeAnimatorDelegate.clear();
                    presentingFragmentDescriptions = null;
                    if (settings.afterAnimationRunnable != null) {
                        settings.afterAnimationRunnable.run();
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                    return;
                }
                Theme.setAnimatingColor(true);
                if (settings.beforeAnimationRunnable != null) {
                    settings.beforeAnimationRunnable.run();
                }
                animationProgressListener = settings.animationProgress;
                if (animationProgressListener != null) {
                    animationProgressListener.setProgress(0);
                }
                fromBackgroundColor = getBackground() instanceof ColorDrawable ? ((ColorDrawable) getBackground()).getColor() : 0;
                themeAnimator = ValueAnimator.ofFloat(0, 1).setDuration(settings.duration);
                themeAnimator.addUpdateListener(animation -> setThemeAnimationValue((float) animation.getAnimatedValue()));
                themeAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(themeAnimator)) {
                            themeAnimatorDescriptions.clear();
                            animateStartColors.clear();
                            animateEndColors.clear();
                            themeAnimatorDelegate.clear();
                            Theme.setAnimatingColor(false);
                            presentingFragmentDescriptions = null;
                            themeAnimator = null;
                            if (settings.afterAnimationRunnable != null) {
                                settings.afterAnimationRunnable.run();
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(themeAnimator)) {
                            themeAnimatorDescriptions.clear();
                            animateStartColors.clear();
                            animateEndColors.clear();
                            themeAnimatorDelegate.clear();
                            Theme.setAnimatingColor(false);
                            presentingFragmentDescriptions = null;
                            themeAnimator = null;
                            if (settings.afterAnimationRunnable != null) {
                                settings.afterAnimationRunnable.run();
                            }
                        }
                    }
                });
                themeAnimator.start();
            }
            if (onDone != null) {
                onDone.run();
            }
        };
        if (fragmentCount >= 1 && settings.applyTheme) {
            if (settings.accentId != -1 && settings.theme != null) {
                settings.theme.setCurrentAccentId(settings.accentId);
                Theme.saveThemeAccents(settings.theme, true, false, true, false);
            }
            if (onDone == null) {
                Theme.applyTheme(settings.theme, settings.nightTheme);
                next.run();
            } else {
                Theme.applyThemeInBackground(settings.theme, settings.nightTheme, () -> AndroidUtilities.runOnUIThread(next));
            }
        } else {
            next.run();
        }
    }

    private void setThemeAnimationValue(float value) {
        themeAnimationValue = value;
        for (int j = 0, N = themeAnimatorDescriptions.size(); j < N; j++) {
            ArrayList<ThemeDescription> descriptions = themeAnimatorDescriptions.get(j);
            int[] startColors = animateStartColors.get(j);
            int[] endColors = animateEndColors.get(j);
            int rE, gE, bE, aE, rS, gS, bS, aS, a, r, g, b;
            for (int i = 0, N2 = descriptions.size(); i < N2; i++) {
                rE = Color.red(endColors[i]);
                gE = Color.green(endColors[i]);
                bE = Color.blue(endColors[i]);
                aE = Color.alpha(endColors[i]);

                rS = Color.red(startColors[i]);
                gS = Color.green(startColors[i]);
                bS = Color.blue(startColors[i]);
                aS = Color.alpha(startColors[i]);

                a = Math.min(255, (int) (aS + (aE - aS) * value));
                r = Math.min(255, (int) (rS + (rE - rS) * value));
                g = Math.min(255, (int) (gS + (gE - gS) * value));
                b = Math.min(255, (int) (bS + (bE - bS) * value));
                int color = Color.argb(a, r, g, b);
                ThemeDescription description = descriptions.get(i);
                description.setAnimatedColor(color);
                description.setColor(color, false, false);
            }
        }
        for (int j = 0, N = themeAnimatorDelegate.size(); j < N; j++) {
            ThemeDescription.ThemeDescriptionDelegate delegate = themeAnimatorDelegate.get(j);
            if (delegate != null) {
                delegate.didSetColor();
                delegate.onAnimationProgress(value);
            }
        }
        if (presentingFragmentDescriptions != null) {
            for (int i = 0, N = presentingFragmentDescriptions.size(); i < N; i++) {
                ThemeDescription description = presentingFragmentDescriptions.get(i);
                String key = description.getCurrentKey();
                description.setColor(Theme.getColor(key), false, false);
            }
        }
        if (animationProgressListener != null) {
            animationProgressListener.setProgress(value);
        }
        if (delegate != null) {
            delegate.onThemeProgress(value);
        }
    }

    @Override
    public float getThemeAnimationValue() {
        return themeAnimationValue;
    }

    @Override
    public void setFragmentStackChangedListener(Runnable onFragmentStackChanged) {
        this.onFragmentStackChangedListener = onFragmentStackChanged;
    }

    @Override
    public boolean isTransitionAnimationInProgress() {
        return currentSpringAnimation != null || customAnimation != null;
    }

    @Override
    public boolean isInPassivePreviewMode() {
        return (isInPreviewMode() && previewMenu == null) || isTransitionAnimationInProgress();
    }

    @Override
    public void setInBubbleMode(boolean bubbleMode) {
        this.isInBubbleMode = bubbleMode;
    }

    @Override
    public boolean isInBubbleMode() {
        return isInBubbleMode;
    }

    @Override
    public boolean isInPreviewMode() {
        return getLastFragment() != null && getLastFragment().isInPreviewMode() || blurredBackFragmentForPreview != null;
    }

    @Override
    public boolean isPreviewOpenAnimationInProgress() {
        return isInPreviewMode() && isTransitionAnimationInProgress();
    }

    @Override
    public void movePreviewFragment(float dy) {
        if (!isInPreviewMode() || previewMenu != null || isTransitionAnimationInProgress() || getForegroundView() == null) {
            return;
        }
        float currentTranslation = getForegroundView().getTranslationY();
        float nextTranslation = -dy;
        if (nextTranslation > 0) {
            nextTranslation = 0;
        } else if (nextTranslation < -AndroidUtilities.dp(60)) {
            nextTranslation = 0;
            expandPreviewFragment();
        }
        if (currentTranslation != nextTranslation) {
            getForegroundView().setTranslationY(nextTranslation);
            invalidate();
        }
    }

    @Override
    public void expandPreviewFragment() {
        if (!isInPreviewMode() || isTransitionAnimationInProgress() || fragmentStack.isEmpty()) {
            return;
        }

        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {}

        BaseFragment fragment = getLastFragment();
        View bgView = getBackgroundView();
        View fgView = getForegroundView();
        View fragmentView = fragment.getFragmentView();
        previewFragmentRect.set(fragmentView.getLeft(), fragmentView.getTop(), fragmentView.getRight(), fragmentView.getBottom());
        previewFragmentSnapshot = AndroidUtilities.snapshotView(fgView);

        resetViewProperties(fgView);
        resetViewProperties(fragment.getFragmentView());
        fragment.setInPreviewMode(false);
        swipeProgress = 0f;
        invalidateTranslation();

        float fromMenuY;
        if (previewMenu != null) {
            fromMenuY = previewMenu.getTranslationY();
        } else {
            fromMenuY = 0;
        }

        FloatValueHolder valueHolder = new FloatValueHolder(0);
        currentSpringAnimation = new SpringAnimation(valueHolder)
                .setSpring(new SpringForce(SPRING_MULTIPLIER)
                        .setStiffness(SPRING_STIFFNESS_PREVIEW_EXPAND)
                        .setDampingRatio(0.6f));
        currentSpringAnimation.addUpdateListener((animation, value, velocity) -> {
            previewExpandProgress = value / SPRING_MULTIPLIER;
            bgView.invalidate();

            fgView.setPivotX(previewFragmentRect.centerX());
            fgView.setPivotY(previewFragmentRect.centerY());
            fgView.setScaleX(AndroidUtilities.lerp(previewFragmentRect.width() / (float) fgView.getWidth(), 1f, previewExpandProgress));
            fgView.setScaleY(AndroidUtilities.lerp(previewFragmentRect.height() / (float) fgView.getHeight(), 1f, previewExpandProgress));
            fgView.invalidate();

            if (previewMenu != null) {
                previewMenu.setTranslationY(AndroidUtilities.lerp(fromMenuY, getHeight(), previewExpandProgress));
            }

            invalidate();
        });
        currentSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
            if (animation == currentSpringAnimation) {
                currentSpringAnimation = null;
                fragment.onPreviewOpenAnimationEnd();

                previewFragmentSnapshot.recycle();
                previewFragmentSnapshot = null;

                if (blurredBackFragmentForPreview != null) {
                    blurredBackFragmentForPreview.recycle();
                    blurredBackFragmentForPreview = null;
                }

                if (previewMenu != null && previewMenu.getParent() != null) {
                    ((ViewGroup) previewMenu.getParent()).removeView(previewMenu);
                }
                previewMenu = null;
                previewOpenCallback = null;
                previewExpandProgress = 0;

                if (getBackgroundView() != null) {
                    getBackgroundView().setVisibility(GONE);
                }
            }
        });
        currentSpringAnimation.start();
    }

    @Override
    public void finishPreviewFragment() {
        if (isInPreviewMode()) {
            Runnable callback = () -> {
                if (delayedPresentAnimation != null) {
                    AndroidUtilities.cancelRunOnUIThread(delayedPresentAnimation);
                    delayedPresentAnimation = null;
                }

                closeLastFragment();
            };
            if (!isTransitionAnimationInProgress()) {
                callback.run();
            } else {
                previewOpenCallback = callback;
            }
        }
    }

    @Override
    public void setFragmentPanTranslationOffset(int offset) {
        FragmentHolderView holderView = getForegroundView();
        if (holderView != null) {
            holderView.setFragmentPanTranslationOffset(offset);
        }
    }

    @Override
    public ViewGroup getOverlayContainerView() {
        return overlayLayout;
    }

    @Override
    public void setHighlightActionButtons(boolean highlightActionButtons) {
        this.highlightActionButtons = highlightActionButtons;
    }

    @Override
    public float getCurrentPreviewFragmentAlpha() {
        return isInPreviewMode() ? getForegroundView().getAlpha() : 0f;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int index = indexOfChild(child);
        if (drawerLayoutContainer != null && drawerLayoutContainer.isDrawCurrentPreviewFragmentAbove() && isInPreviewMode() && index == 1) {
            drawerLayoutContainer.invalidate();
            return false;
        }

        boolean clipBackground = getChildCount() >= 3 && index == 0 && customAnimation == null && !isInPreviewMode();
        if (clipBackground) {
            canvas.save();
            AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) * swipeProgress, getHeight() - getPaddingBottom());
            canvas.clipRect(AndroidUtilities.rectTmp);
        }
        if (index == 1 && isInPreviewMode()) {
            drawPreviewDrawables(canvas, (ViewGroup) child);
        }
        boolean draw = super.drawChild(canvas, child, drawingTime);
        if (index == 0 && isInPreviewMode() && blurredBackFragmentForPreview != null) {
            canvas.save();

            if (previewFragmentSnapshot != null) {
                blurPaint.setAlpha((int) (0xFF * (1f - Math.min(previewExpandProgress, 1f))));
            } else {
                blurPaint.setAlpha((int) (0xFF * (1f - Math.max(swipeProgress, 0f))));
            }

            canvas.scale(child.getWidth() / (float)blurredBackFragmentForPreview.getWidth(), child.getHeight() / (float)blurredBackFragmentForPreview.getHeight());
            canvas.drawBitmap(blurredBackFragmentForPreview, 0, 0, blurPaint);
            canvas.restore();
        }
        if (clipBackground) {
            canvas.restore();
        }
        return draw;
    }

    @Override
    public void drawCurrentPreviewFragment(Canvas canvas, Drawable foregroundDrawable) {
        if (isInPreviewMode()) {
            FragmentHolderView v = getForegroundView();
            drawPreviewDrawables(canvas, v);
            if (v.getAlpha() < 1f) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (v.getAlpha() * 255), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.concat(v.getMatrix());
            MarginLayoutParams params = (MarginLayoutParams) v.getLayoutParams();
            canvas.translate(params.leftMargin, params.topMargin);
            path.rewind();
            AndroidUtilities.rectTmp.set(0, previewExpandProgress != 0 ? 0 : AndroidUtilities.statusBarHeight, v.getWidth(), v.getHeight());
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
            canvas.clipPath(path);
            v.draw(canvas);
            if (foregroundDrawable != null) {
                View child = v.getChildAt(0);
                if (child != null) {
                    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    Rect rect = new Rect();
                    child.getLocalVisibleRect(rect);
                    rect.offset(lp.leftMargin, lp.topMargin);
                    rect.top += Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight - 1 : 0;
                    foregroundDrawable.setAlpha((int) (v.getAlpha() * 255));
                    foregroundDrawable.setBounds(rect);
                    foregroundDrawable.draw(canvas);
                }
            }
            canvas.restore();
        }
    }

    private void drawPreviewDrawables(Canvas canvas, ViewGroup containerView) {
        View view = containerView.getChildAt(0);
        if (view != null) {
            MarginLayoutParams params = (MarginLayoutParams) containerView.getLayoutParams();

            float alpha = 1f - Math.max(swipeProgress, 0);
            if (previewFragmentSnapshot != null) {
                alpha = 1f - Math.min(previewExpandProgress, 1f);
            }
            canvas.drawColor(Color.argb((int)(0x2e * alpha), 0, 0, 0));
            if (previewMenu == null) {
                int width = AndroidUtilities.dp(32), height = width / 2;
                int x = (getMeasuredWidth() - width) / 2;
                int y = (int) (params.topMargin + containerView.getTranslationY() - AndroidUtilities.dp(12 + (Build.VERSION.SDK_INT < 21 ? 20 : 0)));
                Theme.moveUpDrawable.setAlpha((int) (alpha * 0xFF));
                Theme.moveUpDrawable.setBounds(x, y, x + width, y + height);
                Theme.moveUpDrawable.draw(canvas);
            }
        }
    }

    @Override
    public void drawHeaderShadow(Canvas canvas, int alpha, int y) {
        if (headerShadowDrawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (headerShadowDrawable.getAlpha() != alpha) {
                    headerShadowDrawable.setAlpha(alpha);
                }
            } else {
                headerShadowDrawable.setAlpha(alpha);
            }
            headerShadowDrawable.setBounds(0, y, getMeasuredWidth(), y + headerShadowDrawable.getIntrinsicHeight());
            headerShadowDrawable.draw(canvas);
        }
    }

    @Override
    public boolean isSwipeInProgress() {
        return isSwipeInProgress;
    }

    @Override
    public void onPause() {
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            fragment.setPaused(true);
        }
    }

    @Override
    public void onResume() {
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            fragment.setPaused(false);
        }
    }

    @Override
    public void onUserLeaveHint() {
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            fragment.onUserLeaveHint();
        }
    }

    @Override
    public void onLowMemory() {
        for (BaseFragment fragment : fragmentStack) {
            fragment.onLowMemory();
        }
    }

    @Override
    public void onBackPressed() {
        if (isSwipeInProgress() || checkTransitionAnimation() || fragmentStack.isEmpty()) {
            return;
        }
        if (GroupCallPip.onBackPressed()) {
            return;
        }
        if (getCurrentActionBar() != null && !getCurrentActionBar().isActionModeShowed() && getCurrentActionBar().isSearchFieldVisible()) {
            getCurrentActionBar().closeSearchField();
            return;
        }
        BaseFragment lastFragment = getLastFragment();
        if (lastFragment.onBackPressed()) {
            closeLastFragment(true);
        }
    }

    @Override
    public boolean extendActionMode(Menu menu) {
        BaseFragment lastFragment = getLastFragment();
        return lastFragment != null && lastFragment.extendActionMode(menu);
    }

    @Override
    public void onActionModeStarted(Object mode) {
        if (getCurrentActionBar() != null) {
            getCurrentActionBar().setVisibility(GONE);
        }
        isInActionMode = true;
    }

    @Override
    public void onActionModeFinished(Object mode) {
        if (getCurrentActionBar() != null) {
            getCurrentActionBar().setVisibility(VISIBLE);
        }
        isInActionMode = false;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        Activity parentActivity = getParentActivity();
        if (parentActivity == null) {
            return;
        }
        // Maybe reset current animation?

        if (intent != null) {
            parentActivity.startActivityForResult(intent, requestCode);
        }
    }

    @Override
    public Theme.MessageDrawable getMessageDrawableOutStart() {
        return messageDrawableOutStart;
    }

    @Override
    public Theme.MessageDrawable getMessageDrawableOutMediaStart() {
        return messageDrawableOutMediaStart;
    }

    @Override
    public List<BackButtonMenu.PulledDialog> getPulledDialogs() {
        return pulledDialogs;
    }

    @Override
    public void setPulledDialogs(List<BackButtonMenu.PulledDialog> pulledDialogs) {
        this.pulledDialogs = pulledDialogs;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !checkTransitionAnimation() && !isSwipeInProgress() && getCurrentActionBar() != null) {
            getCurrentActionBar().onMenuButtonPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        boolean isPortrait = height > width;
        if (wasPortrait != isPortrait && isInPreviewMode()) {
            finishPreviewFragment();
        }
        wasPortrait = isPortrait;
    }

    private final class FragmentHolderView extends FrameLayout {
        private BaseFragment fragment;
        private int fragmentPanTranslationOffset;
        private Paint backgroundPaint = new Paint();
        private int backgroundColor;

        public FragmentHolderView(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void invalidateBackgroundColor() {
            if (fragment == null || fragment.hasOwnBackground()) {
                setBackground(null);
            } else {
                setBackgroundColor(fragment.getThemedColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);

            int actionBarHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof ActionBar) {
                    child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    actionBarHeight = child.getMeasuredHeight();
                }
            }
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof ActionBar)) {
                    if (child.getFitsSystemWindows()) {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
                    }
                }
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int actionBarHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof ActionBar) {
                    child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
                    actionBarHeight = child.getMeasuredHeight();
                }
            }
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof ActionBar)) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) child.getLayoutParams();
                    if (child.getFitsSystemWindows()) {
                        child.layout(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + child.getMeasuredHeight());
                    } else {
                        child.layout(layoutParams.leftMargin, layoutParams.topMargin + actionBarHeight, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + actionBarHeight + child.getMeasuredHeight());
                    }
                }
            }
        }

        public void setFragmentPanTranslationOffset(int fragmentPanTranslationOffset) {
            this.fragmentPanTranslationOffset = fragmentPanTranslationOffset;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (fragmentPanTranslationOffset != 0) {
                int color = Theme.getColor(Theme.key_windowBackgroundWhite);
                if (backgroundColor != color) {
                    backgroundPaint.setColor(backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite));
                }
                canvas.drawRect(0, getMeasuredHeight() - fragmentPanTranslationOffset - 3, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            fragment.drawOverlay(canvas, this);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof ActionBar) {
                return super.drawChild(canvas, child, drawingTime);
            } else {
                int actionBarHeight = 0;
                int actionBarY = 0;
                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View view = getChildAt(i);
                    if (view == child) {
                        continue;
                    }
                    if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                        if (((ActionBar) view).getCastShadows()) {
                            actionBarHeight = (int) (view.getMeasuredHeight() * view.getScaleY());
                            actionBarY = (int) view.getY();
                        }
                        break;
                    }
                }

                boolean clipRoundForeground = indexOfChild(child) == 0 && fragment.isInPreviewMode();
                if (clipRoundForeground) {
                    canvas.save();
                    path.rewind();
                    AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop() + AndroidUtilities.statusBarHeight, child.getRight(), child.getBottom());
                    path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
                    canvas.clipPath(path);
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (clipRoundForeground) {
                    canvas.restore();
                }
                if (actionBarHeight != 0 && headerShadowDrawable != null) {
                    headerShadowDrawable.setBounds(0, actionBarY + actionBarHeight, getMeasuredWidth(), actionBarY + actionBarHeight + headerShadowDrawable.getIntrinsicHeight());
                    headerShadowDrawable.draw(canvas);
                }
                return result;
            }
        }

        public void setFragment(BaseFragment fragment) {
            this.fragment = fragment;
            fragmentPanTranslationOffset = 0;
            invalidate();

            removeAllViews();

            if (fragment == null) {
                invalidateBackgroundColor();
                return;
            }

            View v = fragment.getFragmentView();
            if (v == null) {
                v = fragment.createView(getContext());
                fragment.setFragmentView(v);
            }
            if (v != null && v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
            addView(v);

            if (removeActionBarExtraHeight) {
                fragment.getActionBar().setOccupyStatusBar(false);
            }
            if (fragment.getActionBar() != null && fragment.getActionBar().shouldAddToContainer()) {
                ViewGroup parent = (ViewGroup) fragment.getActionBar().getParent();
                if (parent != null) {
                    parent.removeView(fragment.getActionBar());
                }
                addView(fragment.getActionBar());
            }

            invalidateBackgroundColor();
        }
    }

    private boolean isIgnoredView(ViewGroup root, MotionEvent e, Rect rect) {
        if (root == null) return false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View ch = root.getChildAt(i);
            if (isIgnoredView0(ch, e, rect)) {
                return true;
            }

            if (ch instanceof ViewGroup) {
                if (isIgnoredView((ViewGroup) ch, e, rect)) {
                    return true;
                }
            }
        }
        return isIgnoredView0(root, e, rect);
    }

    private boolean isIgnoredView0(View v, MotionEvent e, Rect rect) {
        v.getGlobalVisibleRect(rect);
        if (v.getVisibility() != View.VISIBLE || !rect.contains((int)e.getX(), (int)e.getY())) {
            return false;
        }

        if (v instanceof ViewPager) {
            ViewPager vp = (ViewPager) v;
            return vp.getCurrentItem() != 0;
        }

        return v.canScrollHorizontally(-1) || v instanceof SeekBarView;
    }
}
