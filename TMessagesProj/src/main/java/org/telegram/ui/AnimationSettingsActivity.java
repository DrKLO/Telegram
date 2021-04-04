package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AnimationBgPreviewCell;
import org.telegram.ui.Cells.AnimationConfigurationCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.SmallColorPicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class AnimationSettingsActivity extends BaseFragment {

    private class ViewPage extends FrameLayout {

        private RecyclerListView listView;
        //private DataUsageActivity.ListAdapter listAdapter;
        private LinearLayoutManager layoutManager;
        private int selectedType;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private BackgroundListAdapter backgroundAdapter;
    private StubAdapter shortTextAdapter;
    private StubAdapter longTextAdapter;
    private StubAdapter linkAdapter;
    private StubAdapter emojiAdapter;
    private StubAdapter voiceAdapter;

    private FrameLayout pickerLayout;
    private SmallColorPicker colorPicker;

    private ValueAnimator colorPickerAnimator;

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

    private final long[] animationDurations =
            {200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000};

    private ActionBarPopupWindow animationTimePopupWindow;
    private ActionBarMenuSubItem[] animationTimePopupWindowItems;
    private boolean swipeBackEnabled = true;

    private boolean inTransitionAnimation = false;
    private boolean openTransitionAnimation = false;
    private boolean backTransitionAnimation = false;

    private boolean started = false;

    private int activeBgColorIndex;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Animation Settings");
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
                        shareAnimationConfig();
                        break;
                    case 2:
                        importAnimationConfig();
                        break;
                    case 3:
                        showConfirmationDialog();
                        break;
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        headerItem.addSubItem(1, "Share Parameters");
        headerItem.addSubItem(2, "Import Parameters");
        TextView restoreText = headerItem.addSubItem(3, "Restore to Default");
        restoreText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));

        hasOwnBackground = true;

        backgroundAdapter = new BackgroundListAdapter(context);
        shortTextAdapter = new StubAdapter();
        longTextAdapter = new StubAdapter();
        linkAdapter = new StubAdapter();
        emojiAdapter = new StubAdapter();
        voiceAdapter = new StubAdapter();

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
                    if (viewPages[a].listView != null) {
                        viewPages[a].listView
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

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < viewPages.length; a++) {
            if (a == 0) {
                if (viewPages[a] != null && viewPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate =
                            viewPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != viewPages[a].layoutManager
                            .getItemCount() - 1) {
                        RecyclerListView.Holder holder =
                                (RecyclerListView.Holder) viewPages[a].listView
                                        .findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
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

            final LinearLayoutManager layoutManager = viewPages[a].layoutManager =
                    new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                        @Override
                        public boolean supportsPredictiveItemAnimations() {
                            return false;
                        }
                    };
            RecyclerListView listView = new RecyclerListView(context);
            viewPages[a].listView = listView;
            viewPages[a].listView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_DEFAULT);
            viewPages[a].listView.setItemAnimator(null);
            viewPages[a].listView.setClipToPadding(false);
            viewPages[a].listView.setSectionsType(2);
            viewPages[a].listView.setLayoutManager(layoutManager);
            viewPages[a].addView(
                    viewPages[a].listView,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
            );
            viewPages[a].listView.setOnItemClickListener((view, position) -> {
                if (getParentActivity() == null) {
                    return;
                }
                //ListAdapter adapter = (ListAdapter) listView.getAdapter();
                /*if (position == adapter.resetRow) {
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
                }*/
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
                layoutManager.scrollToPositionWithOffset(
                        scrollToPositionOnRecreate,
                        scrollToOffsetOnRecreate
                );
            }
            if (a != 0) {
                viewPages[a].setVisibility(View.GONE);
            }
        }

        viewPages[0].listView.setOnItemClickListener((view, position) -> {
            BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
            if(adapter == null) {
                return;
            }
            int item = adapter.items.get(position);
            if (item == adapter.sendMessageAnimationTimeRow) {
                openChooseTimePopup(view, newDuration -> {
                    getMsgController().bgSendMessageAnimationConfig.updateDuration(newDuration);
                    getMsgController().saveBgSendMsgAnimationConfig();
                    onTimeChanged(adapter.sendMessageAnimationTimeRow, adapter.sendMessageAnimationSettingsRow);
                });
            } else if (item == adapter.openChatAnimationTimeRow) {
                openChooseTimePopup(view, newDuration -> {
                    getMsgController().bgOpenChatAnimationConfig.updateDuration(newDuration);
                    getMsgController().saveBgOpenChatAnimationConfig();
                    onTimeChanged(adapter.openChatAnimationTimeRow, adapter.openChatAnimationSettingsRow);
                });
            } else if (item == adapter.jumpToMessageAnimationTimeRow) {
                openChooseTimePopup(view, newDuration -> {
                    getMsgController().bgJumpToMessageAnimationConfig.updateDuration(newDuration);
                    getMsgController().saveBgJumpToMsgAnimationConfig();
                    onTimeChanged(adapter.jumpToMessageAnimationTimeRow, adapter.jumpToMessageSectionRow);
                });
            } else if (item == adapter.backgroundColor1Row) {
                showColorPicker(getMsgController().bgColor1);
                activeBgColorIndex = 1;
            } else if (item == adapter.backgroundColor2Row) {
                showColorPicker(getMsgController().bgColor2);
                activeBgColorIndex = 2;
            } else if (item == adapter.backgroundColor3Row) {
                showColorPicker(getMsgController().bgColor3);
                activeBgColorIndex = 3;
            } else if (item == adapter.backgroundColor4Row) {
                showColorPicker(getMsgController().bgColor4);
                activeBgColorIndex = 4;
            } else if (item == adapter.backgroundFullScreenRow) {
                presentFragment(new BackgroundPreviewActivity());
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

        colorPicker = new SmallColorPicker(
                context,
                new SmallColorPicker.ColorPickerDelegate() {
                    @Override
                    public void setColor(int color, boolean applyNow) {
                        BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
                        if(adapter == null) {
                            return;
                        }
                        if(applyNow) {
                            MessagesController msgController = getMsgController();
                            switch (activeBgColorIndex) {
                                case 1:
                                    msgController.bgColor1 = color;
                                    onColorChanged(adapter.backgroundColor1Row);
                                    break;
                                case 2:
                                    msgController.bgColor2 = color;
                                    onColorChanged(adapter.backgroundColor2Row);
                                    break;
                                case 3:
                                    msgController.bgColor3 = color;
                                    onColorChanged(adapter.backgroundColor3Row);
                                    break;
                                case 4:
                                    msgController.bgColor4 = color;
                                    onColorChanged(adapter.backgroundColor4Row);
                                    break;
                            }
                            hideColorPicker();
                            MessagesController.getInstance(0).saveBgColors();
                        }
                    }

                    @Override
                    public void onClose() {
                        hideColorPicker();
                    }
                }
        );
        colorPicker.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        pickerLayout = new FrameLayout(context);
        pickerLayout.addView(
                colorPicker,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.MATCH_PARENT,
                        Gravity.BOTTOM
                )
        );
        pickerLayout.setOnClickListener(v -> hideColorPicker());
        pickerLayout.setVisibility(View.INVISIBLE);

        frameLayout.addView(
                pickerLayout,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.MATCH_PARENT,
                        Gravity.BOTTOM
                )
        );
        return fragmentView;
    }

    private AnimationBgPreviewCell findBgPreviewView() {
        RecyclerListView list = viewPages[0].listView;
        BackgroundListAdapter adapter = (BackgroundListAdapter) list.getAdapter();
        if (adapter == null) {
            return null;
        }
        for (int i = 0; i<list.getChildCount(); i++) {
            View childView = list.getChildAt(i);
            int position = list.getChildAdapterPosition(childView);
            if (position == RecyclerView.NO_POSITION || adapter.getItemViewType(position) != 4) {
                continue;
            }
            return  (AnimationBgPreviewCell) childView;
        }
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        started = true;
        AnimationBgPreviewCell cell = findBgPreviewView();
        if (cell != null) {
            cell.bgContainer.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        started = false;
        AnimationBgPreviewCell cell = findBgPreviewView();
        if (cell != null) {
            cell.bgContainer.onStop();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AnimationBgPreviewCell cell = findBgPreviewView();
        if (cell != null) {
            cell.bgContainer.onDestroy();
        }
    }

    private void showConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage("Are you sure?");
        builder.setTitle("Restore Settings");
        String buttonText = "Restore";
        builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
            MessagesController.getInstance(0).resetBgSettings();
            updateItems();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        inTransitionAnimation = true;
        openTransitionAnimation = isOpen;
        backTransitionAnimation = backward;
        if ((isOpen && !backward) || (!isOpen && backward)) {
            BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
            if(adapter != null) {
                adapter.notifyItemChanged(adapter.items.indexOf(adapter.backgroundRow));
            }
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        inTransitionAnimation = false;
        if (isOpen) {
            BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
            if(adapter != null) {
                adapter.notifyItemChanged(adapter.items.indexOf(adapter.backgroundRow));
            }
        }
    }

    private void showShareDialog(File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", file));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignore) {
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            }
        } else {
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        }
        try {
            getParentActivity().startActivityForResult(Intent.createChooser(intent, "Share Background Config"), 501);
        } catch (Throwable ignore) {
        }
    }

    private void importAnimationConfig() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select *.tganim file"), 3462);
        } catch (Throwable e) {
            showImportTipAlert();
        }
    }

    private void updateItems() {
        BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
        if(adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != 3462) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            showImportTipIfNeed();
        } else {
            if(getParentActivity() == null) {
                return;
            }
            if (getMsgController().applyAnimationBgSettings(getParentActivity(), data.getData())) {
                Toast.makeText(getParentActivity(), "Success", Toast.LENGTH_SHORT).show();
                updateItems();
                showImportTipIfNeed();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage("Some problems with read animation settings file.\nTry another file or import animation settings from file in chat.\nWas added 'Apply Animations' option item to files for do that.");
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                showDialog(builder.create());
            }
        }
    }

    private void showImportTipIfNeed() {
        if(MessagesController.getGlobalAnimationsSettings().getBoolean("anim_import_hint", false)) {
            showImportTipAlert();
            MessagesController.getGlobalAnimationsSettings().edit().putBoolean("anim_import_hint", true).apply();
        }
    }

    private void showImportTipAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(
                "You can import animation settings from file in chat.\nWas added 'Apply Animations' option item to files for do that.");
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    private void shareAnimationConfig() {
        //BulletinFactory.of(this).createDownloadBulletin(fileType).show();
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        File tmpAnimationFile =
                new File(getParentActivity().getExternalFilesDir(null), "Animations Settings.tganim");
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (!tmpAnimationFile.exists()) {
                    tmpAnimationFile.createNewFile();
                }
                FileOutputStream stream = new FileOutputStream(tmpAnimationFile);
                PrintWriter writer = new PrintWriter(stream);
                writer.print(getMsgController().exportAnimationBgSettings());
                writer.close();
                AndroidUtilities.runOnUIThread(() -> showShareDialog(tmpAnimationFile));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> Toast
                        .makeText(getParentActivity(), "Oops", Toast.LENGTH_SHORT).show());
            }

        });
    }

    private void showColorPicker(int color) {
        pickerLayout.setVisibility(View.VISIBLE);
        pickerLayout.setBackgroundColor(Color.TRANSPARENT);
        colorPicker.setTranslationY(-colorPicker.getMeasuredHeight());

        if (colorPickerAnimator != null) {
            colorPickerAnimator.cancel();
        }
        colorPicker.setColor(color);
        colorPickerAnimator = ValueAnimator.ofFloat(1, 0);
        colorPickerAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            colorPicker.setTranslationY(Math.round(value * colorPicker.getMeasuredHeight()));
            pickerLayout.setBackgroundColor(Color.argb(Math.round((1 - value) * 50), 0,0,0));
        });
        colorPickerAnimator.setDuration(200);
        colorPickerAnimator.start();
    }

    private void hideColorPicker() {
        pickerLayout.setVisibility(View.VISIBLE);
        colorPicker.setTranslationY(0);

        if (colorPickerAnimator != null) {
            colorPickerAnimator.cancel();
        }
        colorPickerAnimator = ValueAnimator.ofFloat(0, 1);
        colorPickerAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            colorPicker.setTranslationY(Math.round(value * colorPicker.getMeasuredHeight()));
            pickerLayout.setBackgroundColor(Color.argb(Math.round((1 - value) * 50), 0,0,0));
        });
        colorPickerAnimator.setDuration(200);
        colorPickerAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                pickerLayout.setVisibility(View.INVISIBLE);
                colorPicker.setTranslationY(colorPicker.getMeasuredHeight());
            }
        });
        colorPickerAnimator.start();
    }

    private void onTimeChanged(Integer timeRow, Integer settingsRow) {
        BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
        if(adapter == null) {
            return;
        }
        adapter.notifyItemChanged(adapter.items.indexOf(timeRow));
        adapter.notifyItemChanged(adapter.items.indexOf(settingsRow));
    }

    private void onColorChanged(Integer colorRow) {
        BackgroundListAdapter adapter = (BackgroundListAdapter) viewPages[0].listView.getAdapter();
        if(adapter == null) {
            return;
        }
        adapter.notifyItemChanged(adapter.items.indexOf(colorRow));
        adapter.notifyItemChanged(adapter.items.indexOf(adapter.backgroundRow));
    }

    private void initSelectTimePopup() {
        animationTimePopupWindowItems = new ActionBarMenuSubItem[12];
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout =
                new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity());
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        ScrollView scrollView;
        if (Build.VERSION.SDK_INT >= 21) {
            scrollView = new ScrollView(getParentActivity(), null, 0, R.style.scrollbarShapeStyle) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                }
            };
        } else {
            scrollView = new ScrollView(getParentActivity());
        }
        scrollView.setClipToPadding(false);
        popupLayout.addView(
                scrollView,
                LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
        );


        linearLayout.setMinimumWidth(AndroidUtilities.dp(114));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < animationDurations.length; i++) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(),
                    i == 0, i == animationDurations.length - 1
            );
            cell.setText(animationDurations[i] + "ms");
            animationTimePopupWindowItems[i] = cell;
            linearLayout.addView(cell);
        }

        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        animationTimePopupWindow = new ActionBarPopupWindow(popupLayout,
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT
        ) {
            @Override
            public void dismiss() {
                super.dismiss();

            }
        };
        animationTimePopupWindow.setPauseNotifications(true);
        animationTimePopupWindow.setDismissAnimationDuration(220);
        animationTimePopupWindow.setOutsideTouchable(true);
        animationTimePopupWindow.setClippingEnabled(true);
        animationTimePopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        animationTimePopupWindow.setFocusable(true);
        popupLayout.measure(
                View.MeasureSpec
                        .makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST),
                View.MeasureSpec
                        .makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST)
        );
        animationTimePopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        animationTimePopupWindow
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        animationTimePopupWindow.getContentView().setFocusableInTouchMode(true);
    }

    private void openChooseTimePopup(View target, ChangeAnimationDurationCallback callback) {
        initSelectTimePopup();

        for (int i = 0; i < animationTimePopupWindowItems.length; i++) {
            final long duration = animationDurations[i];
            animationTimePopupWindowItems[i].setOnClickListener(v -> {
                callback.onDurationChanged(duration);
                if (animationTimePopupWindow != null) {
                    animationTimePopupWindow.dismiss();
                }
            });
        }
        TextSettingsCell cell = (TextSettingsCell) target;
        View valueView = cell.getChildAt(1);
        Rect visibleRect = new Rect();
        valueView.getGlobalVisibleRect(visibleRect);
        animationTimePopupWindow.showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, visibleRect.left, visibleRect.top);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (ViewPage viewPage : viewPages) {
            viewPage.listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = viewPages[a].listView.getAdapter();
        viewPages[a].listView.setPinnedHeaderShadowDrawable(null);

        if (viewPages[a].selectedType == 0) {
            if (currentAdapter != backgroundAdapter) {
                viewPages[a].listView.setAdapter(backgroundAdapter);
            }
        } else if (viewPages[a].selectedType == 1) {
            if (currentAdapter != shortTextAdapter) {
                viewPages[a].listView.setAdapter(shortTextAdapter);
            }
        } else if (viewPages[a].selectedType == 2) {
            if (currentAdapter != longTextAdapter) {
                viewPages[a].listView.setAdapter(longTextAdapter);
            }
        } else if (viewPages[a].selectedType == 3) {
            if (currentAdapter != linkAdapter) {
                viewPages[a].listView.setAdapter(linkAdapter);
            }
        } else if (viewPages[a].selectedType == 4) {
            if (currentAdapter != emojiAdapter) {
                viewPages[a].listView.setAdapter(emojiAdapter);
            }
        } else if (viewPages[a].selectedType == 5) {
            if (currentAdapter != longTextAdapter) {
                viewPages[a].listView.setAdapter(voiceAdapter);
            }
        }
        viewPages[a].listView.setVisibility(View.VISIBLE);

        if (actionBar.getTranslationY() != 0) {
            viewPages[a].layoutManager
                    .scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        for (ViewPage viewPage : viewPages) {
            viewPage.listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.addTextTab(0, "Background");
        scrollSlidingTextTabStrip.addTextTab(1, "Short Text");
        scrollSlidingTextTabStrip.addTextTab(2, "Long Text");
        scrollSlidingTextTabStrip.addTextTab(3, "Link");
        scrollSlidingTextTabStrip.addTextTab(4, "Emoji");
        scrollSlidingTextTabStrip.addTextTab(5, "Voice");
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

    private class BackgroundListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private int item = 0;

        final int backgroundPreviewHeaderRow = item++;
        final int backgroundRow = item++;
        final int backgroundFullScreenRow = item++;
        final int backgroundSectionRow = item++;

        final int backgroundColorsHeaderRow = item++;
        //top left
        final int backgroundColor1Row = item++;
        //top right
        final int backgroundColor2Row = item++;
        //bottom right
        final int backgroundColor3Row = item++;
        //bottom left
        final int backgroundColor4Row = item++;
        final int backgroundColorsSection = item++;

        final int sendMessageHeaderRow = item++;
        final int sendMessageAnimationTimeRow = item++;
        final int sendMessageAnimationSettingsRow = item++;
        final int sendMessageSectionRow = item++;

        final int openChatHeaderRow = item++;
        final int openChatAnimationTimeRow = item++;
        final int openChatAnimationSettingsRow = item++;
        final int openChatSectionRow = item++;

        final int jumpToMessageHeaderRow = item++;
        final int jumpToMessageAnimationTimeRow = item++;
        final int jumpToMessageAnimationSettingsRow = item++;
        final int jumpToMessageSectionRow = item++;

        private final List<Integer> items = new ArrayList<>();

        public BackgroundListAdapter(Context context) {
            mContext = context;

            updateItems();
        }

        private void updateItems() {
            items.add(backgroundPreviewHeaderRow);
            items.add(backgroundRow);
            items.add(backgroundFullScreenRow);
            items.add(backgroundSectionRow);

            items.add(backgroundColorsHeaderRow);
            items.add(backgroundColor1Row);
            items.add(backgroundColor2Row);
            items.add(backgroundColor3Row);
            items.add(backgroundColor4Row);
            items.add(backgroundColorsSection);

            items.add(sendMessageHeaderRow);
            items.add(sendMessageAnimationTimeRow);
            items.add(sendMessageAnimationSettingsRow);
            items.add(sendMessageSectionRow);

            items.add(openChatHeaderRow);
            items.add(openChatAnimationTimeRow);
            items.add(openChatAnimationSettingsRow);
            items.add(openChatSectionRow);

            items.add(jumpToMessageHeaderRow);
            items.add(jumpToMessageAnimationTimeRow);
            items.add(jumpToMessageAnimationSettingsRow);
            items.add(jumpToMessageSectionRow);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 2 || type == 3 || type == 5;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType
        ) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new AnimationConfigurationCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                case 3:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new AnimationBgPreviewCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int item = items.get(position);
            MessagesController msgController = getMsgController();
            switch (holder.getItemViewType()) {
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if(item == backgroundPreviewHeaderRow) {
                        headerCell.setText("Background Preview");
                    } else if(item == backgroundColorsHeaderRow) {
                        headerCell.setText("Colors");
                    } else if(item == sendMessageHeaderRow) {
                        headerCell.setText("Send Message");
                    } else if(item == openChatHeaderRow) {
                        headerCell.setText("Open Chat");
                    } else if(item == jumpToMessageHeaderRow) {
                        headerCell.setText("Jump to Message");
                    }
                    break;
                case 1:
                    AnimationConfigurationCell configCell =
                            (AnimationConfigurationCell) holder.itemView;
                    if (item == sendMessageAnimationSettingsRow) {
                        configCell.configView
                                .updateValues(msgController.bgSendMessageAnimationConfig);
                        configCell.configView.listener = config -> {
                            msgController.bgSendMessageAnimationConfig.fill(config);
                            msgController.saveBgSendMsgAnimationConfig();
                        };
                    } else if (item == openChatAnimationSettingsRow) {
                        configCell.configView
                                .updateValues(msgController.bgOpenChatAnimationConfig);
                        configCell.configView.listener = config -> {
                            msgController.bgOpenChatAnimationConfig.fill(config);
                            msgController.saveBgOpenChatAnimationConfig();
                        };
                    } else if (item == jumpToMessageAnimationSettingsRow) {
                        configCell.configView
                                .updateValues(msgController.bgJumpToMessageAnimationConfig);
                        configCell.configView.listener = config -> {
                            msgController.bgJumpToMessageAnimationConfig.fill(config);
                            msgController.saveBgJumpToMsgAnimationConfig();
                        };
                    }
                    break;
                case 2:
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (item == sendMessageAnimationTimeRow) {
                        cell.setTextAndValue("Duration", msgController.bgSendMessageAnimationConfig.duration + "ms", true);
                    } else if (item == openChatAnimationTimeRow) {
                        cell.setTextAndValue("Duration", msgController.bgOpenChatAnimationConfig.duration + "ms", true);
                    } else if (item == jumpToMessageAnimationTimeRow) {
                        cell.setTextAndValue("Duration", msgController.bgJumpToMessageAnimationConfig.duration + "ms", true);
                    }
                    break;
                case 3:
                    TextSettingsCell colorCell = (TextSettingsCell) holder.itemView;
                    if (item == backgroundColor1Row) {
                        colorCell.setTextAndColorValue("Color 1", msgController.bgColor1, true);
                    } else if (item == backgroundColor2Row) {
                        colorCell.setTextAndColorValue("Color 2", msgController.bgColor2, true);
                    } else if (item == backgroundColor3Row) {
                        colorCell.setTextAndColorValue("Color 3", msgController.bgColor3, true);
                    } else if (item == backgroundColor4Row) {
                        colorCell.setTextAndColorValue("Color 4", msgController.bgColor4, true);
                    }
                    break;
                case 4:
                    AnimationBgPreviewCell bgCell = (AnimationBgPreviewCell) holder.itemView;
                    if (started) {
                        bgCell.bgContainer.onStart();
                    }
                    bgCell.bgContainer.updateColors();
                    if (inTransitionAnimation) {
                        if(openTransitionAnimation && !backTransitionAnimation) {
                            bgCell.bgContainer.displayPreview();
                        } else if(!openTransitionAnimation && backTransitionAnimation) {
                            bgCell.bgContainer.displayPreview(true, true);
                        } else if(!openTransitionAnimation) {
                            bgCell.bgContainer.displayPreview(true, false);
                        } else {
                            bgCell.bgContainer.displayPreview(true, true);
                        }
                    } else {
                        bgCell.bgContainer.displayBg(true);
                    }
                    break;
                case 5:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == backgroundFullScreenRow) {
                        textCell.setText("Open Full Screen", false);
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            int item = items.get(position);
            if (item == backgroundPreviewHeaderRow || item == sendMessageHeaderRow || item == openChatHeaderRow || item == jumpToMessageHeaderRow || item == backgroundColorsHeaderRow) {
                return 0;
            } else if(item == sendMessageAnimationSettingsRow || item == openChatAnimationSettingsRow || item == jumpToMessageAnimationSettingsRow) {
                return 1;
            } else if (item == sendMessageAnimationTimeRow || item == openChatAnimationTimeRow || item == jumpToMessageAnimationTimeRow) {
                return 2;
            } else if(item == backgroundColor1Row || item == backgroundColor2Row || item == backgroundColor3Row || item == backgroundColor4Row){
                return 3;
            } else if (item == backgroundRow) {
                return 4;
            } else if (item == backgroundFullScreenRow) {
                return 5;
            } else {
                return -1;
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
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

    interface ChangeAnimationDurationCallback {

        void onDurationChanged(long newDuration);
    }

    interface ChangeColorCallback {

        void onColorChanged(int color);
    }
}