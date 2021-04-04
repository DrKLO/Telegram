package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.AnimatedBg.AnimatedBgContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.util.ArrayList;

public class BackgroundPreviewActivity extends BaseFragment {

    private class ViewPage extends FrameLayout {

        private View stubView;
        private int selectedType;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private AnimateTextView animateText;
    private AnimatedBgContainer bgContainer;

    private Paint backgroundPaint = new Paint();
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private ViewPage[] viewPages = new ViewPage[3];
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private boolean backAnimation;
    private int maximumVelocity;
    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    private boolean swipeBackEnabled = true;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Background Preview");
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
                switch (id) {
                    case -1:
                        finishFragment();
                        break;
                    case 1:
                        bgContainer.setEnabledGravityProcessing(!bgContainer.isEnabledGravityProcessing());
                        break;
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        headerItem.addSubItem(1, R.drawable.tool_rotate, "Switch Gravity Animation");

        hasOwnBackground = true;

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        scrollSlidingTextTabStrip.setUseSameWidth(true);
        actionBar.addView(
                scrollSlidingTextTabStrip,
                LayoutHelper
                        .createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM)
        );
        scrollSlidingTextTabStrip
                .setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
                    @Override
                    public void onPageSelected(int id, boolean forward) {
                        if (viewPages[0].selectedType == id) {
                            return;
                        }
                        swipeBackEnabled = id == scrollSlidingTextTabStrip.getFirstTabId();
                        viewPages[1].selectedType = id;
                        viewPages[1].setVisibility(View.VISIBLE);
                        switchToCurrentSelectedMode(true);
                        animatingForward = forward;
                    }

                    @Override
                    public void onPageScrolled(float progress) {
                        if (progress == 1 && viewPages[1].getVisibility() != View.VISIBLE) {
                            return;
                        }
                        if (animatingForward) {
                            viewPages[0]
                                    .setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                            viewPages[1].setTranslationX(viewPages[0]
                                    .getMeasuredWidth() - progress * viewPages[0]
                                    .getMeasuredWidth());
                        } else {
                            viewPages[0]
                                    .setTranslationX(progress * viewPages[0].getMeasuredWidth());
                            viewPages[1].setTranslationX(progress * viewPages[0]
                                    .getMeasuredWidth() - viewPages[0].getMeasuredWidth());
                        }
                        if (progress == 1) {
                            ViewPage tempPage = viewPages[0];
                            viewPages[0] = viewPages[1];
                            viewPages[1] = tempPage;
                            viewPages[1].setVisibility(View.GONE);
                        }
                    }
                });

        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;
            private boolean globalIgnoreLayout;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
                }
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                globalIgnoreLayout = true;
                for (int a = 0; a < viewPages.length; a++) {
                    if (viewPages[a] == null) {
                        continue;
                    }
                    if (viewPages[a].stubView != null) {
                        viewPages[a].stubView
                                .setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(4));
                    }
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(
                            canvas,
                            actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY()
                    );
                }
            }

            @Override
            public void requestLayout() {
                if (globalIgnoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            public boolean checkTabsAnimationInProgress() {
                if (tabsAnimationInProgress) {
                    boolean cancel = false;
                    if (backAnimation) {
                        if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                            viewPages[0].setTranslationX(0);
                            viewPages[1].setTranslationX(viewPages[0]
                                    .getMeasuredWidth() * (animatingForward ? 1 : -1));
                            cancel = true;
                        }
                    } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                        viewPages[0].setTranslationX(viewPages[0]
                                .getMeasuredWidth() * (animatingForward ? -1 : 1));
                        viewPages[1].setTranslationX(0);
                        cancel = true;
                    }
                    if (cancel) {
                        if (tabsAnimation != null) {
                            tabsAnimation.cancel();
                            tabsAnimation = null;
                        }
                        tabsAnimationInProgress = false;
                    }
                    return tabsAnimationInProgress;
                }
                return false;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return checkTabsAnimationInProgress() || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                canvas.drawRect(
                        0,
                        actionBar.getMeasuredHeight() + actionBar.getTranslationY(),
                        getMeasuredWidth(),
                        getMeasuredHeight(),
                        backgroundPaint
                );
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!parentLayout.checkTransitionAnimation() && !checkTabsAnimationInProgress()) {
                    if (ev != null) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.addMovement(ev);
                    }
                    if (ev != null && ev
                            .getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev
                            .getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                viewPages[0].setTranslationX(0);
                                viewPages[1].setTranslationX(animatingForward ? viewPages[0]
                                        .getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip
                                        .selectTabWithId(viewPages[1].selectedType, 0);
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            if (animatingForward) {
                                viewPages[0].setTranslationX(dx);
                                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                            } else {
                                viewPages[0].setTranslationX(dx);
                                viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                            }
                            float scrollProgress =
                                    Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip
                                    .selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev
                            .getAction() == MotionEvent.ACTION_CANCEL || ev
                            .getAction() == MotionEvent.ACTION_UP || ev
                            .getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        float velX;
                        float velY;
                        if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                            velX = velocityTracker.getXVelocity();
                            velY = velocityTracker.getYVelocity();
                            if (!startedTracking) {
                                if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                    prepareForMoving(ev, velX < 0);
                                }
                            }
                        } else {
                            velX = 0;
                            velY = 0;
                        }
                        if (startedTracking) {
                            float x = viewPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            backAnimation =
                                    Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math
                                            .abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float distToMove;
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator
                                                    .ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(
                                                    viewPages[1],
                                                    View.TRANSLATION_X,
                                                    viewPages[1].getMeasuredWidth()
                                            )
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator
                                                    .ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(
                                                    viewPages[1],
                                                    View.TRANSLATION_X,
                                                    -viewPages[1].getMeasuredWidth()
                                            )
                                    );
                                }
                            } else {
                                dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(
                                                    viewPages[0],
                                                    View.TRANSLATION_X,
                                                    -viewPages[0].getMeasuredWidth()
                                            ),
                                            ObjectAnimator
                                                    .ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(
                                                    viewPages[0],
                                                    View.TRANSLATION_X,
                                                    viewPages[0].getMeasuredWidth()
                                            ),
                                            ObjectAnimator
                                                    .ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                }
                            }
                            tabsAnimation.setInterpolator(interpolator);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                            float distance =
                                    (float) halfWidth + (float) halfWidth * AndroidUtilities
                                            .distanceInfluenceForSnapDuration(distanceRatio);
                            velX = Math.abs(velX);
                            int duration;
                            if (velX > 0) {
                                duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                            } else {
                                float pageDelta = dx / getMeasuredWidth();
                                duration = (int) ((pageDelta + 1.0f) * 100.0f);
                            }
                            duration = Math.max(150, Math.min(duration, 600));

                            tabsAnimation.setDuration(duration);
                            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    tabsAnimation = null;
                                    if (backAnimation) {
                                        viewPages[1].setVisibility(View.GONE);
                                    } else {
                                        ViewPage tempPage = viewPages[0];
                                        viewPages[0] = viewPages[1];
                                        viewPages[1] = tempPage;
                                        viewPages[1].setVisibility(View.GONE);
                                        swipeBackEnabled =
                                                viewPages[0].selectedType == scrollSlidingTextTabStrip
                                                        .getFirstTabId();
                                        scrollSlidingTextTabStrip
                                                .selectTabWithId(viewPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                }
                            });
                            tabsAnimation.start();
                            tabsAnimationInProgress = true;
                            startedTracking = false;
                        } else {
                            maybeStartTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };
        frameLayout.setWillNotDraw(false);

        bgContainer = new AnimatedBgContainer(context);
        frameLayout.addView(
                bgContainer,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
        );

        for (int a = 0; a < viewPages.length; a++) {
            final ViewPage
                    ViewPage = new ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (viewPages[0] == this) {
                            float scrollProgress =
                                    Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0]
                                            .getMeasuredWidth();
                            scrollSlidingTextTabStrip
                                    .selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    }
                }
            };
            frameLayout.addView(
                    ViewPage,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
            );
            viewPages[a] = ViewPage;
            viewPages[a].stubView = new View(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return true;
                }
            };
            viewPages[a].addView(
                    viewPages[a].stubView,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
            );
            if (a != 0) {
                viewPages[a].setVisibility(View.GONE);
            }
        }

        animateText = new AnimateTextView(context);
        animateText.setText("ANIMATE");
        animateText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(animateText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        animateText.setOnClickListener(v -> {
            MessagesController msg = MessagesController.getInstance(0);
            switch (scrollSlidingTextTabStrip.getCurrentPosition()) {
                case 0:
                    bgContainer.animateToNext(msg.bgSendMessageAnimationConfig);
                    break;
                case 1:
                    bgContainer.animateToNext(msg.bgOpenChatAnimationConfig);
                    break;
                case 2:
                    bgContainer.animateToNext(msg.bgJumpToMessageAnimationConfig);
                    break;
            }
        });

        frameLayout.addView(
                actionBar,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        );

        updateTabs();
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip
                .getFirstTabId();
        return fragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        bgContainer.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        bgContainer.onStop();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        bgContainer.onDestroy();
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if(isOpen && !backward) {
            bgContainer.displayPreview();
        } else if(!isOpen && backward) {
            bgContainer.displayPreview();
        } else if(!isOpen) {
            bgContainer.displayPreview(false, false);
        } else {
            bgContainer.displayPreview(false, true);
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) {
            bgContainer.displayBg();
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        int a = animated ? 1 : 0;
        viewPages[a].stubView.setVisibility(View.VISIBLE);
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.addTextTab(0, "Send Message");
        scrollSlidingTextTabStrip.addTextTab(1, "Open Chat");
        scrollSlidingTextTabStrip.addTextTab(2, "Jump to Message");
        scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            viewPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private class StubAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            return 0;
        }
    }

    private MessagesController getMsgController() {
        return MessagesController.getInstance(0);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabActiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabUnactiveText));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarTabLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, new Drawable[]{scrollSlidingTextTabStrip.getSelectorDrawable()}, null, Theme.key_actionBarTabSelector));

        return arrayList;
    }
        
    private class AnimateTextView extends View {

        private int textWidth;
        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private int circleWidth;
        private int rippleColor;

        private StaticLayout textLayout;
        private StaticLayout textLayoutOut;
        private int layoutTextWidth;
        private TextPaint layoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Drawable selectableBackground;

        ValueAnimator replaceAnimator;
        float replaceProgress = 1f;
        boolean animatedFromBottom;
        int textColor;
        int panelBackgroundColor;
        int counterColor;

        public AnimateTextView(Context context) {
            super(context);
            textPaint.setTextSize(AndroidUtilities.dp(13));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            layoutPaint.setTextSize(AndroidUtilities.dp(15));
            layoutPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        }

        public void setText(CharSequence text, boolean animatedFromBottom) {
            this.animatedFromBottom = animatedFromBottom;
            textLayoutOut = textLayout;
            layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
            textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            setContentDescription(text);
            invalidate();

            if (textLayoutOut != null) {
                if (replaceAnimator != null) {
                    replaceAnimator.cancel();
                }
                replaceProgress = 0;
                replaceAnimator = ValueAnimator.ofFloat(0,1f);
                replaceAnimator.addUpdateListener(animation -> {
                    replaceProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                replaceAnimator.setDuration(150);
                replaceAnimator.start();
            }
        }

        public void setText(CharSequence text) {
            layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
            textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            setContentDescription(text);
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (selectableBackground != null) {
                selectableBackground.setState(getDrawableState());
            }
        }

        @Override
        public boolean verifyDrawable(Drawable drawable) {
            if (selectableBackground != null) {
                return selectableBackground == drawable || super.verifyDrawable(drawable);
            }
            return super.verifyDrawable(drawable);
        }

        @Override
        public void jumpDrawablesToCurrentState() {
            super.jumpDrawablesToCurrentState();
            if (selectableBackground != null) {
                selectableBackground.jumpToCurrentState();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (textLayout != null) {
                    int lineWidth = (int) Math.ceil(textLayout.getLineWidth(0));
                    int contentWidth;
                    if (getMeasuredWidth() == ((View)getParent()).getMeasuredWidth()) {
                        contentWidth = getMeasuredWidth() - AndroidUtilities.dp(96);
                    } else {
                        contentWidth = lineWidth + (circleWidth > 0 ? circleWidth + AndroidUtilities.dp(8) : 0);
                        contentWidth += AndroidUtilities.dp(48);
                    }
                    int x = (getMeasuredWidth() - contentWidth) / 2;
                    rect.set(
                            x, getMeasuredHeight() / 2f - contentWidth / 2f,
                            x + contentWidth, getMeasuredHeight() / 2f + contentWidth / 2f
                    );
                    if (!rect.contains(event.getX(), event.getY())) {
                        setPressed(false);
                        return false;
                    }
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int color = Theme.getColor(Theme.key_chat_fieldOverlayText);
            if (textColor != color) {
                layoutPaint.setColor(textColor = color);
            }
            color = Theme.getColor(Theme.key_chat_messagePanelBackground);
            if (panelBackgroundColor != color) {
                textPaint.setColor(panelBackgroundColor = color);
            }
            color = Theme.getColor(Theme.key_chat_goDownButtonCounterBackground);
            if (counterColor != color) {
                paint.setColor(counterColor = color);
            }

            if (getParent() != null) {
                int contentWidth = getMeasuredWidth();
                int x = (getMeasuredWidth() - contentWidth) / 2;
                if (rippleColor != Theme.getColor(Theme.key_chat_fieldOverlayText) || selectableBackground == null) {
                    selectableBackground = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(60), 0, ColorUtils
                            .setAlphaComponent(rippleColor = Theme.getColor(Theme.key_chat_fieldOverlayText), 26));
                    selectableBackground.setCallback(this);
                }
                int start = (getLeft() + x) <= 0 ? x - AndroidUtilities.dp(20) : x;
                int end = x + contentWidth > ((View) getParent()).getMeasuredWidth() ? x + contentWidth + AndroidUtilities.dp(20) : x + contentWidth;
                selectableBackground.setBounds(
                        start, getMeasuredHeight() / 2 - contentWidth / 2,
                        end, getMeasuredHeight() / 2 + contentWidth / 2
                );
                selectableBackground.draw(canvas);
            }
            if (textLayout != null) {
                canvas.save();
                if (replaceProgress != 1f && textLayoutOut != null) {
                    int oldAlpha = layoutPaint.getAlpha();

                    canvas.save();
                    canvas.translate((getMeasuredWidth() - textLayoutOut.getWidth()) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    canvas.translate(0, (animatedFromBottom ? -1f : 1f) * AndroidUtilities.dp(18) * replaceProgress);
                    layoutPaint.setAlpha((int) (oldAlpha * (1f - replaceProgress)));
                    textLayoutOut.draw(canvas);
                    canvas.restore();

                    canvas.save();
                    canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    canvas.translate(0, (animatedFromBottom ? 1f : -1f) * AndroidUtilities.dp(18) * (1f - replaceProgress));
                    layoutPaint.setAlpha((int) (oldAlpha * (replaceProgress)));
                    textLayout.draw(canvas);
                    canvas.restore();

                    layoutPaint.setAlpha(oldAlpha);
                } else {
                    canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    textLayout.draw(canvas);
                }

                canvas.restore();
            }
        }
    }
}