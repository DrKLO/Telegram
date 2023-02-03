/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.util.ArrayList;

public class DataUsageActivity extends BaseFragment {

    private class ViewPage extends FrameLayout {
        private RecyclerListView listView;
        private ListAdapter listAdapter;
        private LinearLayoutManager layoutManager;
        private int selectedType;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private ListAdapter mobileAdapter;
    private ListAdapter roamingAdapter;
    private ListAdapter wifiAdapter;

    private Paint backgroundPaint = new Paint();
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private ViewPage[] viewPages = new ViewPage[2];
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

    public DataUsageActivity() {
        super();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("NetworkUsage", R.string.NetworkUsage));
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
            }
        });
        hasOwnBackground = true;

        mobileAdapter = new ListAdapter(context, 0);
        wifiAdapter = new ListAdapter(context, 1);
        roamingAdapter = new ListAdapter(context, 2);

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        scrollSlidingTextTabStrip.setUseSameWidth(true);
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
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
                    viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
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
                    if (viewPages[a].listView != null) {
                        viewPages[a].listView.setPadding(0, actionBarHeight, 0, AndroidUtilities.dp(4));
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
                    parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight() + (int) actionBar.getTranslationY());
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
                            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                            cancel = true;
                        }
                    } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                        viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
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
                canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
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
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        velocityTracker.clear();
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                                viewPages[0].setTranslationX(0);
                                viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                                scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, 0);
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
                            float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
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
                            backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float distToMove;
                            float dx;
                            if (backAnimation) {
                                dx = Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, viewPages[1].getMeasuredWidth())
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -viewPages[1].getMeasuredWidth())
                                    );
                                }
                            } else {
                                dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                                if (animatingForward) {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, -viewPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                } else {
                                    tabsAnimation.playTogether(
                                            ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, viewPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                                    );
                                }
                            }
                            tabsAnimation.setInterpolator(interpolator);

                            int width = getMeasuredWidth();
                            int halfWidth = width / 2;
                            float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                            float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
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
                                        swipeBackEnabled = viewPages[0].selectedType == scrollSlidingTextTabStrip.getFirstTabId();
                                        scrollSlidingTextTabStrip.selectTabWithId(viewPages[0].selectedType, 1.0f);
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

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < viewPages.length; a++) {
            if (a == 0) {
                if (viewPages[a] != null && viewPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate = viewPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != viewPages[a].layoutManager.getItemCount() - 1) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) viewPages[a].listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                        if (holder != null) {
                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                        } else {
                            scrollToPositionOnRecreate = -1;
                        }
                    } else {
                        scrollToPositionOnRecreate = -1;
                    }
                }
            }
            final ViewPage ViewPage = new ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (viewPages[0] == this) {
                            float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                            scrollSlidingTextTabStrip.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    }
                }
            };
            frameLayout.addView(ViewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a] = ViewPage;

            final LinearLayoutManager layoutManager = viewPages[a].layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            };
            RecyclerListView listView = new RecyclerListView(context);
            viewPages[a].listView = listView;
            viewPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
            viewPages[a].listView.setItemAnimator(null);
            viewPages[a].listView.setClipToPadding(false);
            viewPages[a].listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
            viewPages[a].listView.setLayoutManager(layoutManager);
            viewPages[a].addView(viewPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a].listView.setOnItemClickListener((view, position) -> {
                if (getParentActivity() == null) {
                    return;
                }
                ListAdapter adapter = (ListAdapter) listView.getAdapter();
                if (position == adapter.resetRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("ResetStatisticsAlertTitle", R.string.ResetStatisticsAlertTitle));
                    builder.setMessage(LocaleController.getString("ResetStatisticsAlert", R.string.ResetStatisticsAlert));
                    builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                        StatsController.getInstance(currentAccount).resetStats(adapter.currentType);
                        adapter.notifyDataSetChanged();
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                }
            });
            viewPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
                        int scrollY = (int) -actionBar.getTranslationY();
                        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
                        if (scrollY != 0 && scrollY != actionBarHeight) {
                            if (scrollY < actionBarHeight / 2) {
                                viewPages[0].listView.smoothScrollBy(0, -scrollY);
                            } else {
                                viewPages[0].listView.smoothScrollBy(0, actionBarHeight - scrollY);
                            }
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (recyclerView == viewPages[0].listView) {
                        float currentTranslation = actionBar.getTranslationY();
                        float newTranslation = currentTranslation - dy;
                        if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
                            newTranslation = -ActionBar.getCurrentActionBarHeight();
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        if (newTranslation != currentTranslation) {
                            setScrollY(newTranslation);
                        }
                    }
                }
            });
            if (a == 0 && scrollToPositionOnRecreate != -1) {
                layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            }
            if (a != 0) {
                viewPages[a].setVisibility(View.GONE);
            }
        }

        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateTabs();
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mobileAdapter != null) {
            mobileAdapter.notifyDataSetChanged();
        }
        if (wifiAdapter != null) {
            wifiAdapter.notifyDataSetChanged();
        }
        if (roamingAdapter != null) {
            roamingAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return swipeBackEnabled;
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a].listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("NetworkUsageMobileTab", R.string.NetworkUsageMobileTab));
        scrollSlidingTextTabStrip.addTextTab(1, LocaleController.getString("NetworkUsageWiFiTab", R.string.NetworkUsageWiFiTab));
        scrollSlidingTextTabStrip.addTextTab(2, LocaleController.getString("NetworkUsageRoamingTab", R.string.NetworkUsageRoamingTab));
        scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            viewPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a].listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = viewPages[a].listView.getAdapter();
        viewPages[a].listView.setPinnedHeaderShadowDrawable(null);

        if (viewPages[a].selectedType == 0) {
            if (currentAdapter != mobileAdapter) {
                viewPages[a].listView.setAdapter(mobileAdapter);
            }
        } else if (viewPages[a].selectedType == 1) {
            if (currentAdapter != wifiAdapter) {
                viewPages[a].listView.setAdapter(wifiAdapter);
            }
        } else if (viewPages[a].selectedType == 2) {
            if (currentAdapter != roamingAdapter) {
                viewPages[a].listView.setAdapter(roamingAdapter);
            }
        }
        viewPages[a].listView.setVisibility(View.VISIBLE);

        if (actionBar.getTranslationY() != 0) {
            viewPages[a].layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private int currentType;

        private int messagesSectionRow;
        private int messagesSentRow;
        private int messagesReceivedRow;
        private int messagesBytesSentRow;
        private int messagesBytesReceivedRow;
        private int messagesSection2Row;

        private int photosSectionRow;
        private int photosSentRow;
        private int photosReceivedRow;
        private int photosBytesSentRow;
        private int photosBytesReceivedRow;
        private int photosSection2Row;

        private int videosSectionRow;
        private int videosSentRow;
        private int videosReceivedRow;
        private int videosBytesSentRow;
        private int videosBytesReceivedRow;
        private int videosSection2Row;

        private int audiosSectionRow;
        private int audiosSentRow;
        private int audiosReceivedRow;
        private int audiosBytesSentRow;
        private int audiosBytesReceivedRow;
        private int audiosSection2Row;

        private int filesSectionRow;
        private int filesSentRow;
        private int filesReceivedRow;
        private int filesBytesSentRow;
        private int filesBytesReceivedRow;
        private int filesSection2Row;

        private int callsSectionRow;
        private int callsSentRow;
        private int callsReceivedRow;
        private int callsBytesSentRow;
        private int callsBytesReceivedRow;
        private int callsTotalTimeRow;
        private int callsSection2Row;

        private int totalSectionRow;
        private int totalBytesSentRow;
        private int totalBytesReceivedRow;
        private int totalSection2Row;

        private int resetRow;
        private int resetSection2Row;

        private int rowCount;

        public ListAdapter(Context context, int type) {
            mContext = context;
            currentType = type;

            rowCount = 0;

            photosSectionRow = rowCount++;
            photosSentRow = rowCount++;
            photosReceivedRow = rowCount++;
            photosBytesSentRow = rowCount++;
            photosBytesReceivedRow = rowCount++;
            photosSection2Row = rowCount++;

            videosSectionRow = rowCount++;
            videosSentRow = rowCount++;
            videosReceivedRow = rowCount++;
            videosBytesSentRow = rowCount++;
            videosBytesReceivedRow = rowCount++;
            videosSection2Row = rowCount++;

            audiosSectionRow = rowCount++;
            audiosSentRow = rowCount++;
            audiosReceivedRow = rowCount++;
            audiosBytesSentRow = rowCount++;
            audiosBytesReceivedRow = rowCount++;
            audiosSection2Row = rowCount++;

            filesSectionRow = rowCount++;
            filesSentRow = rowCount++;
            filesReceivedRow = rowCount++;
            filesBytesSentRow = rowCount++;
            filesBytesReceivedRow = rowCount++;
            filesSection2Row = rowCount++;

            callsSectionRow = rowCount++;
            callsSentRow = rowCount++;
            callsReceivedRow = rowCount++;
            callsBytesSentRow = rowCount++;
            callsBytesReceivedRow = rowCount++;
            callsTotalTimeRow = rowCount++;
            callsSection2Row = rowCount++;

            messagesSectionRow = rowCount++;
            messagesSentRow = -1;
            messagesReceivedRow = -1;
            messagesBytesSentRow = rowCount++;
            messagesBytesReceivedRow = rowCount++;
            messagesSection2Row = rowCount++;

            totalSectionRow = rowCount++;
            totalBytesSentRow = rowCount++;
            totalBytesReceivedRow = rowCount++;
            totalSection2Row = rowCount++;

            resetRow = rowCount++;
            resetSection2Row = rowCount++;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (position == resetSection2Row) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == resetRow) {
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setText(LocaleController.getString("ResetStatistics", R.string.ResetStatistics), false);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                    } else {
                        int type;
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == callsSentRow || position == callsReceivedRow || position == callsBytesSentRow || position == callsBytesReceivedRow) {
                            type = StatsController.TYPE_CALLS;
                        } else if (position == messagesSentRow || position == messagesReceivedRow || position == messagesBytesSentRow || position == messagesBytesReceivedRow) {
                            type = StatsController.TYPE_MESSAGES;
                        } else if (position == photosSentRow || position == photosReceivedRow || position == photosBytesSentRow || position == photosBytesReceivedRow) {
                            type = StatsController.TYPE_PHOTOS;
                        } else if (position == audiosSentRow || position == audiosReceivedRow || position == audiosBytesSentRow || position == audiosBytesReceivedRow) {
                            type = StatsController.TYPE_AUDIOS;
                        } else if (position == videosSentRow || position == videosReceivedRow || position == videosBytesSentRow || position == videosBytesReceivedRow) {
                            type = StatsController.TYPE_VIDEOS;
                        } else if (position == filesSentRow || position == filesReceivedRow || position == filesBytesSentRow || position == filesBytesReceivedRow) {
                            type = StatsController.TYPE_FILES;
                        } else {
                            type = StatsController.TYPE_TOTAL;
                        }
                        if (position == callsSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("OutgoingCalls", R.string.OutgoingCalls), String.format("%d", StatsController.getInstance(currentAccount).getSentItemsCount(currentType, type)), true);
                        } else if (position == callsReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("IncomingCalls", R.string.IncomingCalls), String.format("%d", StatsController.getInstance(currentAccount).getRecivedItemsCount(currentType, type)), true);
                        } else if (position == callsTotalTimeRow) {
                            String time = AndroidUtilities.formatShortDuration(StatsController.getInstance(currentAccount).getCallsTotalTime(currentType));
                            textCell.setTextAndValue(LocaleController.getString("CallsTotalTime", R.string.CallsTotalTime), time, false);
                        } else if (position == messagesSentRow || position == photosSentRow || position == videosSentRow || position == audiosSentRow || position == filesSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("CountSent", R.string.CountSent), String.format("%d", StatsController.getInstance(currentAccount).getSentItemsCount(currentType, type)), true);
                        } else if (position == messagesReceivedRow || position == photosReceivedRow || position == videosReceivedRow || position == audiosReceivedRow || position == filesReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("CountReceived", R.string.CountReceived), String.format("%d", StatsController.getInstance(currentAccount).getRecivedItemsCount(currentType, type)), true);
                        } else if (position == messagesBytesSentRow || position == photosBytesSentRow || position == videosBytesSentRow || position == audiosBytesSentRow || position == filesBytesSentRow || position == callsBytesSentRow || position == totalBytesSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("BytesSent", R.string.BytesSent), AndroidUtilities.formatFileSize(StatsController.getInstance(currentAccount).getSentBytesCount(currentType, type)), true);
                        } else if (position == messagesBytesReceivedRow || position == photosBytesReceivedRow || position == videosBytesReceivedRow || position == audiosBytesReceivedRow || position == filesBytesReceivedRow || position == callsBytesReceivedRow || position == totalBytesReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("BytesReceived", R.string.BytesReceived), AndroidUtilities.formatFileSize(StatsController.getInstance(currentAccount).getReceivedBytesCount(currentType, type)), position == callsBytesReceivedRow);
                        }
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == totalSectionRow) {
                        headerCell.setText(LocaleController.getString("TotalDataUsage", R.string.TotalDataUsage));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(LocaleController.getString("CallsDataUsage", R.string.CallsDataUsage));
                    } else if (position == filesSectionRow) {
                        headerCell.setText(LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage));
                    } else if (position == audiosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalAudioCache", R.string.LocalAudioCache));
                    } else if (position == videosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache));
                    } else if (position == photosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache));
                    } else if (position == messagesSectionRow) {
                        headerCell.setText(LocaleController.getString("MessagesDataUsage", R.string.MessagesDataUsage));
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    cell.setText(LocaleController.formatString("NetworkUsageSince", R.string.NetworkUsageSince, LocaleController.getInstance().formatterStats.format(StatsController.getInstance(currentAccount).getResetStatsDate(currentType))));
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == resetRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == resetSection2Row) {
                return 3;
            } else if (position == callsSection2Row || position == filesSection2Row || position == audiosSection2Row || position == videosSection2Row || position == photosSection2Row || position == messagesSection2Row || position == totalSection2Row) {
                return 0;
            } else if (position == totalSectionRow || position == callsSectionRow || position == filesSectionRow || position == audiosSectionRow || position == videosSectionRow || position == photosSectionRow || position == messagesSectionRow) {
                return 2;
            } else {
                return 1;
            }
        }
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

        for (int a = 0; a < viewPages.length; a++) {
            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(viewPages[a].listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
            arrayList.add(new ThemeDescription(viewPages[a].listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        }

        return arrayList;
    }
}
