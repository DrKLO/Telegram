package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PhotoPickerSearchActivity extends BaseFragment {

    private class ViewPage extends FrameLayout {
        private BaseFragment parentFragment;
        private FrameLayout fragmentView;
        private ActionBar actionBar;
        private RecyclerListView listView;
        private int selectedType;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private PhotoPickerActivity imagesSearch;
    private PhotoPickerActivity gifsSearch;
    private ActionBarMenuItem searchItem;
    private EditTextEmoji commentTextView;

    private boolean sendPressed;
    private int selectPhotoType;
    private ChatActivity chatActivity;

    private final static int search_button = 0;

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

    public PhotoPickerSearchActivity(HashMap<Object, Object> selectedPhotos, ArrayList<Object> selectedPhotosOrder, ArrayList<MediaController.SearchImage> recentImages, int selectPhotoType, boolean allowCaption, ChatActivity chatActivity) {
        super();

        imagesSearch = new PhotoPickerActivity(0, null, selectedPhotos, selectedPhotosOrder, recentImages, selectPhotoType, allowCaption, chatActivity);
        gifsSearch = new PhotoPickerActivity(1, null, selectedPhotos, selectedPhotosOrder, recentImages, selectPhotoType, allowCaption, chatActivity);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
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

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                imagesSearch.getActionBar().openSearchField("", false);
                gifsSearch.getActionBar().openSearchField("", false);
                searchItem.getSearchField().requestFocus();

            }

            @Override
            public boolean canCollapseSearch() {
                finishFragment();
                return false;
            }

            @Override
            public void onTextChanged(EditText editText) {
                imagesSearch.getActionBar().setSearchFieldText(editText.getText().toString());
                gifsSearch.getActionBar().setSearchFieldText(editText.getText().toString());
            }

            @Override
            public void onSearchPressed(EditText editText) {
                imagesSearch.getActionBar().onSearchPressed();
                gifsSearch.getActionBar().onSearchPressed();
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        scrollSlidingTextTabStrip.setUseSameWidth(true);
        scrollSlidingTextTabStrip.setColors(Theme.key_chat_attachActiveTab, Theme.key_chat_attachActiveTab, Theme.key_chat_attachUnactiveTab, Theme.key_dialogButtonSelector);
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
                if (id == 0) {
                    searchItem.setSearchFieldHint(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
                } else {
                    searchItem.setSearchFieldHint(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
                }
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

        SizeNotifierFrameLayout sizeNotifierFrameLayout;
        fragmentView = sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

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
            public void forceHasOverlappingRendering(boolean hasOverlappingRendering) {
                super.forceHasOverlappingRendering(hasOverlappingRendering);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int keyboardSize = getKeyboardHeight();
                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= commentTextView.getEmojiPadding();
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                } else {
                    globalIgnoreLayout = true;
                    commentTextView.hideEmojiView();
                    globalIgnoreLayout = false;
                }

                int actionBarHeight = actionBar.getMeasuredHeight();
                globalIgnoreLayout = true;
                for (int a = 0; a < viewPages.length; a++) {
                    if (viewPages[a] == null) {
                        continue;
                    }
                    if (viewPages[a].listView != null) {
                        viewPages[a].listView.setPadding(AndroidUtilities.dp(4), actionBarHeight + AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
                    }
                }
                globalIgnoreLayout = false;

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight();
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + getKeyboardHeight() - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
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
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        if (velocityTracker != null) {
                            velocityTracker.clear();
                        }
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        velocityTracker.addMovement(ev);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) / 3 > dy) {
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
                    } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                        if (!startedTracking) {
                            float velX = velocityTracker.getXVelocity();
                            float velY = velocityTracker.getYVelocity();
                            if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                prepareForMoving(ev, velX < 0);
                            }
                        }
                        if (startedTracking) {
                            float x = viewPages[0].getX();
                            tabsAnimation = new AnimatorSet();
                            float velX = velocityTracker.getXVelocity();
                            float velY = velocityTracker.getYVelocity();
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
                        } else {
                            maybeStartTracking = false;
                            startedTracking = false;
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
        sizeNotifierFrameLayout.setWillNotDraw(false);

        imagesSearch.setParentFragment(this);
        commentTextView = imagesSearch.commentTextView;
        commentTextView.setSizeNotifierLayout(sizeNotifierFrameLayout);
        for (int a = 0; a < 4; a++) {
            View view;
            switch (a) {
                case 0:
                    view = imagesSearch.frameLayout2;
                    break;
                case 1:
                    view = imagesSearch.writeButtonContainer;
                    break;
                case 2:
                    view = imagesSearch.selectedCountView;
                    break;
                case 3:
                default:
                    view = imagesSearch.shadow;
                    break;
            }
            ViewGroup parent = (ViewGroup) view.getParent();
            parent.removeView(view);
        }
        gifsSearch.setLayoutViews(imagesSearch.frameLayout2, imagesSearch.writeButtonContainer, imagesSearch.selectedCountView, imagesSearch.shadow, imagesSearch.commentTextView );
        gifsSearch.setParentFragment(this);

        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a] = new ViewPage(context) {
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
            sizeNotifierFrameLayout.addView(viewPages[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            if (a == 0) {
                viewPages[a].parentFragment = imagesSearch;
                viewPages[a].listView = imagesSearch.getListView();
            } else if (a == 1) {
                viewPages[a].parentFragment = gifsSearch;
                viewPages[a].listView = gifsSearch.getListView();
                viewPages[a].setVisibility(View.GONE);
            }
            viewPages[a].fragmentView = (FrameLayout) viewPages[a].parentFragment.getFragmentView();
            viewPages[a].listView.setClipToPadding(false);
            viewPages[a].actionBar = viewPages[a].parentFragment.getActionBar();
            viewPages[a].addView(viewPages[a].fragmentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a].addView(viewPages[a].actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            viewPages[a].actionBar.setVisibility(View.GONE);

            RecyclerView.OnScrollListener onScrollListener = viewPages[a].listView.getOnScrollListener();
            viewPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
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
                    onScrollListener.onScrolled(recyclerView, dx, dy);
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
        }

        sizeNotifierFrameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sizeNotifierFrameLayout.addView(imagesSearch.shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));
        sizeNotifierFrameLayout.addView(imagesSearch.frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        sizeNotifierFrameLayout.addView(imagesSearch.writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 10));
        sizeNotifierFrameLayout.addView(imagesSearch.selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        updateTabs();
        switchToCurrentSelectedMode(false);
        swipeBackEnabled = scrollSlidingTextTabStrip.getCurrentTabId() == scrollSlidingTextTabStrip.getFirstTabId();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (searchItem != null) {
            searchItem.openSearch(true);
            getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        if (imagesSearch != null) {
            imagesSearch.onResume();
        }
        if (gifsSearch != null) {
            gifsSearch.onResume();
        }
    }

    public void setCaption(CharSequence text) {
        if (imagesSearch != null) {
            imagesSearch.setCaption(text);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (imagesSearch != null) {
            imagesSearch.onPause();
        }
        if (gifsSearch != null) {
            gifsSearch.onPause();
        }
    }

    @Override
    public void onFragmentDestroy() {
        if (imagesSearch != null) {
            imagesSearch.onFragmentDestroy();
        }
        if (gifsSearch != null) {
            gifsSearch.onFragmentDestroy();
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (imagesSearch != null) {
            imagesSearch.onConfigurationChanged(newConfig);
        }
        if (gifsSearch != null) {
            gifsSearch.onConfigurationChanged(newConfig);
        }
    }

    private void setScrollY(float value) {
        actionBar.setTranslationY(value);
        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a].listView.setPinnedSectionOffsetY((int) value);
        }
        fragmentView.invalidate();
    }

    public void setDelegate(PhotoPickerActivity.PhotoPickerActivityDelegate delegate) {
        imagesSearch.setDelegate(delegate);
        gifsSearch.setDelegate(delegate);
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        imagesSearch.setMaxSelectedPhotos(value, order);
        gifsSearch.setMaxSelectedPhotos(value, order);
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("ImagesTab", R.string.ImagesTab));
        scrollSlidingTextTabStrip.addTextTab(1, LocaleController.getString("GifsTab", R.string.GifsTab));
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
        if (actionBar.getTranslationY() != 0) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) viewPages[a].listView.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_dialogButtonSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_chat_messagePanelHint));
        arrayList.add(new ThemeDescription(searchItem.getSearchField(), ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_chat_attachActiveTab));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_chat_attachUnactiveTab));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_dialogButtonSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, new Drawable[]{scrollSlidingTextTabStrip.getSelectorDrawable()}, null, Theme.key_chat_attachActiveTab));

        Collections.addAll(arrayList, imagesSearch.getThemeDescriptions());
        Collections.addAll(arrayList, gifsSearch.getThemeDescriptions());

        return arrayList.toArray(new ThemeDescription[0]);
    }
}
