/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@TargetApi(23)
public final class FloatingToolbar {

    private static final MenuItem.OnMenuItemClickListener NO_OP_MENUITEM_CLICK_LISTENER = item -> false;
    private final View mWindowView;
    private final FloatingToolbarPopup mPopup;
    private final Rect mContentRect = new Rect();
    private final Rect mPreviousContentRect = new Rect();
    private Menu mMenu;
    private List<MenuItem> mShowingMenuItems = new ArrayList<>();
    private MenuItem.OnMenuItemClickListener mMenuItemClickListener = NO_OP_MENUITEM_CLICK_LISTENER;
    private int mSuggestedWidth;
    private boolean mWidthChanged = true;

    private int currentStyle;

    public static final int STYLE_DIALOG = 0;
    public static final int STYLE_THEME = 1;
    public static final int STYLE_BLACK = 2;

    private Runnable premiumLockClickListener;
    public void setOnPremiumLockClick(Runnable listener) {
        premiumLockClickListener = listener;
    }

    private Utilities.Callback0Return<Boolean> quoteShowCallback;
    public void setQuoteShowVisible(Utilities.Callback0Return<Boolean> callback) {
        quoteShowCallback = callback;
    }

    private final OnLayoutChangeListener mOrientationChangeHandler = new OnLayoutChangeListener() {
        private final Rect mNewRect = new Rect();
        private final Rect mOldRect = new Rect();

        @Override
        public void onLayoutChange(View view, int newLeft, int newRight, int newTop, int newBottom, int oldLeft, int oldRight, int oldTop, int oldBottom) {
            mNewRect.set(newLeft, newRight, newTop, newBottom);
            mOldRect.set(oldLeft, oldRight, oldTop, oldBottom);
            if (mPopup.isShowing() && !mNewRect.equals(mOldRect)) {
                mWidthChanged = true;
                updateLayout();
            }
        }
    };

    private final Comparator<MenuItem> mMenuItemComparator = (menuItem1, menuItem2) -> menuItem1.getOrder() - menuItem2.getOrder();
    
    private final Theme.ResourcesProvider resourcesProvider;

    public FloatingToolbar(Context context, View windowView, int style, Theme.ResourcesProvider resourcesProvider) {
        mWindowView = windowView;
        currentStyle = style;
        this.resourcesProvider = resourcesProvider;
        mPopup = new FloatingToolbarPopup(context, windowView);
    }

    public FloatingToolbar setMenu(Menu menu) {
        mMenu = menu;
        return this;
    }

    public FloatingToolbar setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener menuItemClickListener) {
        if (menuItemClickListener != null) {
            mMenuItemClickListener = menuItemClickListener;
        } else {
            mMenuItemClickListener = NO_OP_MENUITEM_CLICK_LISTENER;
        }
        return this;
    }

    public FloatingToolbar setContentRect(Rect rect) {
        mContentRect.set(rect);
        return this;
    }

    public FloatingToolbar setSuggestedWidth(int suggestedWidth) {
        int difference = Math.abs(suggestedWidth - mSuggestedWidth);
        mWidthChanged = difference > (mSuggestedWidth * 0.2);
        mSuggestedWidth = suggestedWidth;
        return this;
    }

    public FloatingToolbar show() {
        registerOrientationHandler();
        doShow();
        return this;
    }

    public FloatingToolbar updateLayout() {
        if (mPopup.isShowing()) {
            doShow();
        }
        return this;
    }

    public void dismiss() {
        unregisterOrientationHandler();
        mPopup.dismiss();
    }

    public void hide() {
        mPopup.hide();
    }

    public boolean isShowing() {
        return mPopup.isShowing();
    }

    public boolean isHidden() {
        return mPopup.isHidden();
    }

    public void setOutsideTouchable(boolean outsideTouchable, PopupWindow.OnDismissListener onDismiss) {
        if (mPopup.setOutsideTouchable(outsideTouchable, onDismiss) && isShowing()) {
            dismiss();
            doShow();
        }
    }

    private static final int TRANSLATE = 16908353; // android.R.id.textAssist;
    private void doShow() {
        List<MenuItem> menuItems = getVisibleAndEnabledMenuItems(mMenu);
        Collections.sort(menuItems, mMenuItemComparator);
        if (!isCurrentlyShowing(menuItems) || mWidthChanged) {
            mPopup.dismiss();
            mPopup.layoutMenuItems(menuItems, mMenuItemClickListener, mSuggestedWidth);
            mShowingMenuItems = menuItems;
        }
        if (!mPopup.isShowing()) {
            mPopup.show(mContentRect);
        } else if (!mPreviousContentRect.equals(mContentRect)) {
            mPopup.updateCoordinates(mContentRect);
        }
        mWidthChanged = false;
        mPreviousContentRect.set(mContentRect);
    }

    private boolean isCurrentlyShowing(List<MenuItem> menuItems) {
        if (mShowingMenuItems == null || menuItems.size() != mShowingMenuItems.size()) {
            return false;
        }
        final int size = menuItems.size();
        for (int i = 0; i < size; i++) {
            final MenuItem menuItem = menuItems.get(i);
            final MenuItem showingItem = mShowingMenuItems.get(i);
            if (menuItem.getItemId() != showingItem.getItemId() || !TextUtils.equals(menuItem.getTitle(), showingItem.getTitle()) || !Objects.equals(menuItem.getIcon(), showingItem.getIcon()) || menuItem.getGroupId() != showingItem.getGroupId()) {
                return false;
            }
        }
        return true;
    }

    private List<MenuItem> getVisibleAndEnabledMenuItems(Menu menu) {
        List<MenuItem> menuItems = new ArrayList<>();
        for (int i = 0; (menu != null) && (i < menu.size()); i++) {
            MenuItem menuItem = menu.getItem(i);
            if (menuItem.isVisible() && menuItem.isEnabled()) {
                Menu subMenu = menuItem.getSubMenu();
                if (subMenu != null) {
                    menuItems.addAll(getVisibleAndEnabledMenuItems(subMenu));
                } else if (menuItem.getItemId() == R.id.menu_quote && (quoteShowCallback != null && !quoteShowCallback.run())) {
                    continue;
                } else if (menuItem.getItemId() != TRANSLATE && (menuItem.getItemId() != R.id.menu_regular || premiumLockClickListener == null)) {
                    menuItems.add(menuItem);
                }
            }
        }
        return menuItems;
    }

    private void registerOrientationHandler() {
        unregisterOrientationHandler();
        mWindowView.addOnLayoutChangeListener(mOrientationChangeHandler);
    }

    private void unregisterOrientationHandler() {
        mWindowView.removeOnLayoutChangeListener(mOrientationChangeHandler);
    }

    public static final List<Integer> premiumOptions = Arrays.asList(
            R.id.menu_bold,
            R.id.menu_italic,
            R.id.menu_strike,
            R.id.menu_link,
            R.id.menu_mono,
            R.id.menu_underline,
            R.id.menu_spoiler,
            R.id.menu_quote
    );

    private final class FloatingToolbarPopup {

        private static final int MIN_OVERFLOW_SIZE = 2;
        private static final int MAX_OVERFLOW_SIZE = 4;
        private final Context mContext;
        private final View mParent;
        private final PopupWindow mPopupWindow;

        private final int mMarginHorizontal;
        private final int mMarginVertical;

        private final ViewGroup mContentContainer;
        private final ViewGroup mMainPanel;
        private final OverflowPanel mOverflowPanel;
        private final FrameLayout mOverflowButton;
        private final View mOverflowButtonShadow;
        private final ImageView mOverflowButtonIcon;
        private final TextView mOverflowButtonText;

        private final Drawable mArrow;
        private final Drawable mOverflow;
        private final AnimatedVectorDrawable mToArrow;
        private final AnimatedVectorDrawable mToOverflow;
        private final OverflowPanelViewHelper mOverflowPanelViewHelper;

        private final Interpolator mLogAccelerateInterpolator;
        private final Interpolator mFastOutSlowInInterpolator;
        private final Interpolator mLinearOutSlowInInterpolator;
        private final Interpolator mFastOutLinearInInterpolator;

        private final AnimatorSet mShowAnimation;
        private final AnimatorSet mDismissAnimation;
        private final AnimatorSet mHideAnimation;
        private final AnimationSet mOpenOverflowAnimation;
        private final AnimationSet mCloseOverflowAnimation;
        private final Rect mViewPortOnScreen = new Rect();
        private final Point mCoordsOnWindow = new Point();

        private final int[] mTmpCoords = new int[2];
        private final Region mTouchableRegion = new Region();
        /*private final ViewTreeObserver.OnComputeInternalInsetsListener mInsetsComputer = info -> { TODO
            info.contentInsets.setEmpty();
            info.visibleInsets.setEmpty();
            info.touchableRegion.set(mTouchableRegion);
            info.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
        };*/
        private final int mLineHeight;
        private final int mIconTextSpacing;

        private final Runnable mPreparePopupContentRTLHelper = new Runnable() {
            @Override
            public void run() {
                setPanelsStatesAtRestingPosition();
                setContentAreaAsTouchableSurface();
                mContentContainer.setAlpha(1);
            }
        };
        private boolean mDismissed = true; // tracks whether this popup is dismissed or dismissing.
        private boolean mHidden; // tracks whether this popup is hidden or hiding.
        /* Calculated sizes for panels and overflow button. */
        private final Size mOverflowButtonSize;
        private Size mOverflowPanelSize;  // Should be null when there is no overflow.
        private Size mMainPanelSize;
        /* Item click listeners */
        private MenuItem.OnMenuItemClickListener mOnMenuItemClickListener;
        private final View.OnClickListener mMenuItemButtonOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getTag() instanceof MenuItem) {
                    if (mOnMenuItemClickListener != null) {
                        mOnMenuItemClickListener.onMenuItemClick((MenuItem) v.getTag());
                    }
                }
            }
        };
        private boolean mOpenOverflowUpwards;  // Whether the overflow opens upwards or downwards.
        private boolean mIsOverflowOpen;
        private int mTransitionDurationScale;  // Used to scale the toolbar transition duration.


        public FloatingToolbarPopup(Context context, View parent) {
            mParent = parent;
            mContext = context;
            mContentContainer = createContentContainer(context);
            mPopupWindow = createPopupWindow(mContentContainer);
            mMarginHorizontal = AndroidUtilities.dp(16);
            mMarginVertical = AndroidUtilities.dp(8);
            mLineHeight = AndroidUtilities.dp(48);
            mIconTextSpacing = AndroidUtilities.dp(8);

            mLogAccelerateInterpolator = new LogAccelerateInterpolator();
            mFastOutSlowInInterpolator = AnimationUtils.loadInterpolator(mContext, android.R.interpolator.fast_out_slow_in);
            mLinearOutSlowInInterpolator = AnimationUtils.loadInterpolator(mContext, android.R.interpolator.linear_out_slow_in);
            mFastOutLinearInInterpolator = AnimationUtils.loadInterpolator(mContext, android.R.interpolator.fast_out_linear_in);

            mArrow = mContext.getDrawable(R.drawable.ft_avd_tooverflow).mutate();
            mArrow.setAutoMirrored(true);
            mOverflow = mContext.getDrawable(R.drawable.ft_avd_toarrow).mutate();
            mOverflow.setAutoMirrored(true);
            mToArrow = (AnimatedVectorDrawable) mContext.getDrawable(R.drawable.ft_avd_toarrow_animation).mutate();
            mToArrow.setAutoMirrored(true);
            mToOverflow = (AnimatedVectorDrawable) mContext.getDrawable(R.drawable.ft_avd_tooverflow_animation).mutate();
            mToOverflow.setAutoMirrored(true);

            mOverflowButton = new FrameLayout(mContext);
            mOverflowButtonIcon = new ImageButton(mContext) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    if (mIsOverflowOpen) {
                        return false;
                    }
                    return super.dispatchTouchEvent(event);
                }
            };
            mOverflowButtonIcon.setLayoutParams(new ViewGroup.LayoutParams(AndroidUtilities.dp(56), AndroidUtilities.dp(48)));
            mOverflowButtonIcon.setPaddingRelative(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            mOverflowButtonIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mOverflowButtonIcon.setImageDrawable(mOverflow);
            mOverflowButtonText = new TextView(mContext);
            mOverflowButtonText.setText(LocaleController.getString(R.string.Back));
            mOverflowButtonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            mOverflowButtonText.setAlpha(0f);
            mOverflowButtonShadow = new View(mContext);
            int color;
            if (currentStyle == STYLE_DIALOG) {
                color = getThemedColor(Theme.key_dialogTextBlack);
                mOverflowButtonIcon.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
                mOverflowButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                mOverflowButtonShadow.setBackgroundColor(Theme.multAlpha(getThemedColor(Theme.key_dialogTextBlack), .4f));
            } else if (currentStyle == STYLE_BLACK) {
                color = 0xfffafafa;
                mOverflowButtonIcon.setBackground(Theme.createSelectorDrawable(0x20ffffff, Theme.RIPPLE_MASK_CIRCLE_20DP));
                mOverflowButton.setBackground(Theme.createSelectorDrawable(0x20ffffff, Theme.RIPPLE_MASK_ALL));
                mOverflowButtonShadow.setBackgroundColor(0xff000000);
            } else {
                color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
                mOverflowButtonIcon.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
                mOverflowButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                mOverflowButtonShadow.setBackgroundColor(getThemedColor(Theme.key_divider));
            }
            mOverflow.setTint(color);
            mArrow.setTint(color);
            mToArrow.setTint(color);
            mToOverflow.setTint(color);
            mOverflowButtonText.setTextColor(color);
            mOverflowButtonIcon.setOnClickListener(v -> onBackPressed());
            mOverflowButton.addView(mOverflowButtonIcon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));
            mOverflowButton.addView(mOverflowButtonText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 0, 0));
            mOverflowButton.addView(mOverflowButtonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            mOverflowButtonSize = measure(mOverflowButtonIcon);
            mMainPanel = createMainPanel();
            mOverflowPanelViewHelper = new OverflowPanelViewHelper(mContext, mIconTextSpacing);
            mOverflowPanel = createOverflowPanel();

            Animation.AnimationListener mOverflowAnimationListener = createOverflowAnimationListener();
            mOpenOverflowAnimation = new AnimationSet(true);
            mOpenOverflowAnimation.setAnimationListener(mOverflowAnimationListener);
            mCloseOverflowAnimation = new AnimationSet(true);
            mCloseOverflowAnimation.setAnimationListener(mOverflowAnimationListener);
            mShowAnimation = createEnterAnimation(mContentContainer);
            mDismissAnimation = createExitAnimation(mContentContainer, 150, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getInstance(UserConfig.selectedAccount).doOnIdle(() -> {
                        mPopupWindow.dismiss();
                        mContentContainer.removeAllViews();
                    });
                }
            });
            mHideAnimation = createExitAnimation(mContentContainer, 0, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getInstance(UserConfig.selectedAccount).doOnIdle(() -> {
                        mPopupWindow.dismiss();
                    });
                }
            });
        }

        private void onBackPressed() {
            if (mIsOverflowOpen) {
                mOverflowButtonIcon.setImageDrawable(mToOverflow);
                mToOverflow.start();
                closeOverflow();
            } else {
                mOverflowButtonIcon.setImageDrawable(mToArrow);
                mToArrow.start();
                openOverflow();
            }
        }

        private void updateOverflowButtonClickListener() {
            if (mIsOverflowOpen) {
                mOverflowButton.setClickable(true);
                mOverflowButton.setOnClickListener(v -> onBackPressed());
                mOverflowButtonIcon.setClickable(false);
                mOverflowButtonIcon.setOnClickListener(null);
            } else {
                mOverflowButton.setClickable(false);
                mOverflowButton.setOnClickListener(null);
                mOverflowButtonIcon.setClickable(true);
                mOverflowButtonIcon.setOnClickListener(v -> onBackPressed());
            }
        }

        public boolean setOutsideTouchable(boolean outsideTouchable, PopupWindow.OnDismissListener onDismiss) {
            boolean ret = false;
            if (mPopupWindow.isOutsideTouchable() ^ outsideTouchable) {
                mPopupWindow.setOutsideTouchable(outsideTouchable);
                mPopupWindow.setFocusable(!outsideTouchable);
                ret = true;
            }
            mPopupWindow.setOnDismissListener(onDismiss);
            return ret;
        }

        public void layoutMenuItems(List<MenuItem> menuItems, MenuItem.OnMenuItemClickListener menuItemClickListener, int suggestedWidth) {
            mOnMenuItemClickListener = menuItemClickListener;
            cancelOverflowAnimations();
            clearPanels();
            menuItems = layoutMainPanelItems(menuItems, getAdjustedToolbarWidth(suggestedWidth));
            if (!menuItems.isEmpty()) {
                layoutOverflowPanelItems(menuItems);
            }
            updatePopupSize();
        }

        public void show(Rect contentRectOnScreen) {
            if (isShowing()) {
                return;
            }
            mHidden = false;
            mDismissed = false;
            cancelDismissAndHideAnimations();
            cancelOverflowAnimations();
            refreshCoordinatesAndOverflowDirection(contentRectOnScreen);
            preparePopupContent();
            mPopupWindow.showAtLocation(mParent, Gravity.NO_GRAVITY, mCoordsOnWindow.x, mCoordsOnWindow.y);
            setTouchableSurfaceInsetsComputer();
            runShowAnimation();
        }

        public void dismiss() {
            if (mDismissed) {
                return;
            }
            mHidden = false;
            mDismissed = true;
            mHideAnimation.cancel();
            runDismissAnimation();
            setZeroTouchableSurface();
        }

        public void hide() {
            if (!isShowing()) {
                return;
            }
            mHidden = true;
            runHideAnimation();
            setZeroTouchableSurface();
        }

        public boolean isShowing() {
            return !mDismissed && !mHidden;
        }

        public boolean isHidden() {
            return mHidden;
        }

        public void updateCoordinates(Rect contentRectOnScreen) {
            if (!isShowing() || !mPopupWindow.isShowing()) {
                return;
            }
            cancelOverflowAnimations();
            refreshCoordinatesAndOverflowDirection(contentRectOnScreen);
            preparePopupContent();
            mPopupWindow.update(mCoordsOnWindow.x, mCoordsOnWindow.y, mPopupWindow.getWidth(), mPopupWindow.getHeight());
        }

        private void refreshCoordinatesAndOverflowDirection(Rect contentRectOnScreen) {
            refreshViewPort();
            final int x = Math.min(contentRectOnScreen.centerX() - mPopupWindow.getWidth() / 2, mViewPortOnScreen.right - mPopupWindow.getWidth());
            final int y;
            final int availableHeightAboveContent = contentRectOnScreen.top - mViewPortOnScreen.top;
            final int availableHeightBelowContent = mViewPortOnScreen.bottom - contentRectOnScreen.bottom;
            final int margin = 2 * mMarginVertical;
            final int toolbarHeightWithVerticalMargin = mLineHeight + margin;
            if (!hasOverflow()) {
                if (availableHeightAboveContent >= toolbarHeightWithVerticalMargin) {
                    y = contentRectOnScreen.top - toolbarHeightWithVerticalMargin;
                } else if (availableHeightBelowContent >= toolbarHeightWithVerticalMargin) {
                    y = contentRectOnScreen.bottom;
                } else if (availableHeightBelowContent >= mLineHeight) {
                    y = contentRectOnScreen.bottom - mMarginVertical;
                } else {
                    y = Math.max(mViewPortOnScreen.top, contentRectOnScreen.top - toolbarHeightWithVerticalMargin);
                }
            } else {
                final int minimumOverflowHeightWithMargin = calculateOverflowHeight(MIN_OVERFLOW_SIZE) + margin;
                final int availableHeightThroughContentDown = mViewPortOnScreen.bottom - contentRectOnScreen.top + toolbarHeightWithVerticalMargin;
                final int availableHeightThroughContentUp = contentRectOnScreen.bottom - mViewPortOnScreen.top + toolbarHeightWithVerticalMargin;
                if (availableHeightAboveContent >= minimumOverflowHeightWithMargin) {
                    updateOverflowHeight(availableHeightAboveContent - margin);
                    y = contentRectOnScreen.top - mPopupWindow.getHeight();
                    mOpenOverflowUpwards = true;
                } else if (availableHeightAboveContent >= toolbarHeightWithVerticalMargin && availableHeightThroughContentDown >= minimumOverflowHeightWithMargin) {
                    updateOverflowHeight(availableHeightThroughContentDown - margin);
                    y = contentRectOnScreen.top - toolbarHeightWithVerticalMargin;
                    mOpenOverflowUpwards = false;
                } else if (availableHeightBelowContent >= minimumOverflowHeightWithMargin) {
                    updateOverflowHeight(availableHeightBelowContent - margin);
                    y = contentRectOnScreen.bottom;
                    mOpenOverflowUpwards = false;
                } else if (availableHeightBelowContent >= toolbarHeightWithVerticalMargin && mViewPortOnScreen.height() >= minimumOverflowHeightWithMargin) {
                    updateOverflowHeight(availableHeightThroughContentUp - margin);
                    y = contentRectOnScreen.bottom + toolbarHeightWithVerticalMargin - mPopupWindow.getHeight();
                    mOpenOverflowUpwards = true;
                } else {
                    updateOverflowHeight(mViewPortOnScreen.height() - margin);
                    y = mViewPortOnScreen.top;
                    mOpenOverflowUpwards = false;
                }
            }

            mParent.getRootView().getLocationOnScreen(mTmpCoords);
            int rootViewLeftOnScreen = mTmpCoords[0];
            int rootViewTopOnScreen = mTmpCoords[1];
            mParent.getRootView().getLocationInWindow(mTmpCoords);
            int rootViewLeftOnWindow = mTmpCoords[0];
            int rootViewTopOnWindow = mTmpCoords[1];
            int windowLeftOnScreen = rootViewLeftOnScreen - rootViewLeftOnWindow;
            int windowTopOnScreen = rootViewTopOnScreen - rootViewTopOnWindow;
            mCoordsOnWindow.set(Math.max(0, x - windowLeftOnScreen), Math.max(0, y - windowTopOnScreen));
        }

        private void runShowAnimation() {
            mShowAnimation.start();
        }

        private void runDismissAnimation() {
            mDismissAnimation.start();
        }

        private void runHideAnimation() {
            mHideAnimation.start();
        }

        private void cancelDismissAndHideAnimations() {
            mDismissAnimation.cancel();
            mHideAnimation.cancel();
        }

        private void cancelOverflowAnimations() {
            mContentContainer.clearAnimation();
            mMainPanel.animate().cancel();
            mOverflowPanel.animate().cancel();
            mToArrow.stop();
            mToOverflow.stop();
        }

        private void openOverflow() {
            final int targetWidth = mOverflowPanelSize.getWidth();
            final int targetHeight = mOverflowPanelSize.getHeight();
            final int startWidth = mContentContainer.getWidth();
            final int startHeight = mContentContainer.getHeight();
            final float startY = mContentContainer.getY();
            final float left = mContentContainer.getX();
            final float right = left + mContentContainer.getWidth();
            Animation widthAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int deltaWidth = (int) (interpolatedTime * (targetWidth - startWidth));
                    setWidth(mContentContainer, startWidth + deltaWidth);
                    if (isInRTLMode()) {
                        mContentContainer.setX(left);
                        mMainPanel.setX(0);
                        mOverflowPanel.setX(0);
                    } else {
                        mContentContainer.setX(right - mContentContainer.getWidth());
                        mMainPanel.setX(mContentContainer.getWidth() - startWidth);
                        mOverflowPanel.setX(mContentContainer.getWidth() - targetWidth);
                    }
                }
            };
            Animation heightAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int deltaHeight = (int) (interpolatedTime * (targetHeight - startHeight));
                    setHeight(mContentContainer, startHeight + deltaHeight);
                    if (mOpenOverflowUpwards) {
                        mContentContainer.setY(
                                startY - (mContentContainer.getHeight() - startHeight));
                        positionContentYCoordinatesIfOpeningOverflowUpwards();
                    }
                }
            };
            final float overflowButtonStartX = mOverflowButton.getX();
            final float overflowButtonTargetX = isInRTLMode() ? overflowButtonStartX + targetWidth - mOverflowButtonIcon.getWidth() : overflowButtonStartX - targetWidth + mOverflowButtonIcon.getWidth();
            Animation overflowButtonAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    float overflowButtonX = overflowButtonStartX + interpolatedTime * (overflowButtonTargetX - overflowButtonStartX);
                    float deltaContainerWidth = isInRTLMode() ? 0 : mContentContainer.getWidth() - startWidth;
                    float actualOverflowButtonX = overflowButtonX + deltaContainerWidth;
                    mOverflowButton.setX(actualOverflowButtonX);
                    mOverflowButtonText.setAlpha(interpolatedTime);
                    mOverflowButtonShadow.setAlpha(interpolatedTime);
                }
            };
            widthAnimation.setInterpolator(mLogAccelerateInterpolator);
            widthAnimation.setDuration(getAdjustedDuration(250));
            heightAnimation.setInterpolator(mFastOutSlowInInterpolator);
            heightAnimation.setDuration(getAdjustedDuration(250));
            overflowButtonAnimation.setInterpolator(mFastOutSlowInInterpolator);
            overflowButtonAnimation.setDuration(getAdjustedDuration(250));
            mOpenOverflowAnimation.getAnimations().clear();
            mOpenOverflowAnimation.addAnimation(widthAnimation);
            mOpenOverflowAnimation.addAnimation(heightAnimation);
            mOpenOverflowAnimation.addAnimation(overflowButtonAnimation);
            mContentContainer.startAnimation(mOpenOverflowAnimation);
            mIsOverflowOpen = true;
            updateOverflowButtonClickListener();
            mMainPanel.animate().alpha(0).withLayer().setInterpolator(mLinearOutSlowInInterpolator).setDuration(250).start();
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mOverflowButton.getLayoutParams();
            lp.width = mOverflowPanel.getWidth();
            mOverflowButton.setLayoutParams(lp);
            mOverflowPanel.setAlpha(1);
        }

        private void closeOverflow() {
            final int targetWidth = mMainPanelSize.getWidth();
            final int startWidth = mContentContainer.getWidth();
            final float left = mContentContainer.getX();
            final float right = left + mContentContainer.getWidth();
            Animation widthAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int deltaWidth = (int) (interpolatedTime * (targetWidth - startWidth));
                    setWidth(mContentContainer, startWidth + deltaWidth);
                    if (isInRTLMode()) {
                        mContentContainer.setX(left);
                        mMainPanel.setX(0);
                        mOverflowPanel.setX(0);
                    } else {
                        mContentContainer.setX(right - mContentContainer.getWidth());
                        mMainPanel.setX(mContentContainer.getWidth() - targetWidth);
                        mOverflowPanel.setX(mContentContainer.getWidth() - startWidth);
                    }
                }
            };
            final int targetHeight = mMainPanelSize.getHeight();
            final int startHeight = mContentContainer.getHeight();
            final float bottom = mContentContainer.getY() + mContentContainer.getHeight();
            Animation heightAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    int deltaHeight = (int) (interpolatedTime * (targetHeight - startHeight));
                    setHeight(mContentContainer, startHeight + deltaHeight);
                    if (mOpenOverflowUpwards) {
                        mContentContainer.setY(bottom - mContentContainer.getHeight());
                        positionContentYCoordinatesIfOpeningOverflowUpwards();
                    }
                }
            };
            final float overflowButtonStartX = mOverflowButton.getX();
            final float overflowButtonTargetX = isInRTLMode() ? overflowButtonStartX - startWidth + mOverflowButtonIcon.getWidth() : overflowButtonStartX + startWidth - mOverflowButtonIcon.getWidth();
            Animation overflowButtonAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    float overflowButtonX = overflowButtonStartX + interpolatedTime * (overflowButtonTargetX - overflowButtonStartX);
                    float deltaContainerWidth = isInRTLMode() ? 0 : mContentContainer.getWidth() - startWidth;
                    float actualOverflowButtonX = overflowButtonX + deltaContainerWidth;
                    mOverflowButton.setX(actualOverflowButtonX);
                    mOverflowButtonText.setAlpha(1f - interpolatedTime);
                    mOverflowButtonShadow.setAlpha(1f - interpolatedTime);
                }
            };
            widthAnimation.setInterpolator(mFastOutSlowInInterpolator);
            widthAnimation.setDuration(getAdjustedDuration(250));
            heightAnimation.setInterpolator(mLogAccelerateInterpolator);
            heightAnimation.setDuration(getAdjustedDuration(250));
            overflowButtonAnimation.setInterpolator(mFastOutSlowInInterpolator);
            overflowButtonAnimation.setDuration(getAdjustedDuration(250));
            mCloseOverflowAnimation.getAnimations().clear();
            mCloseOverflowAnimation.addAnimation(widthAnimation);
            mCloseOverflowAnimation.addAnimation(heightAnimation);
            mCloseOverflowAnimation.addAnimation(overflowButtonAnimation);
            mContentContainer.startAnimation(mCloseOverflowAnimation);
            mIsOverflowOpen = false;
            updateOverflowButtonClickListener();
            mMainPanel.animate()
                    .alpha(1).withLayer()
                    .setInterpolator(mFastOutLinearInInterpolator)
                    .setDuration(100)
                    .start();
            mOverflowPanel.animate()
                    .alpha(0).withLayer()
                    .setInterpolator(mLinearOutSlowInInterpolator)
                    .setDuration(150)
                    .start();
        }

        private void setPanelsStatesAtRestingPosition() {
            mOverflowButton.setEnabled(true);
            mOverflowPanel.awakenScrollBars();
            if (mIsOverflowOpen) {
                final Size containerSize = mOverflowPanelSize;
                setSize(mContentContainer, containerSize);
                mMainPanel.setAlpha(0);
                mMainPanel.setVisibility(View.INVISIBLE);
                mOverflowPanel.setAlpha(1);
                mOverflowPanel.setVisibility(View.VISIBLE);
                mOverflowButtonIcon.setImageDrawable(mArrow);
                mOverflowButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));

                if (isInRTLMode()) {
                    mContentContainer.setX(mMarginHorizontal);
                    mMainPanel.setX(0);
                    mOverflowButton.setX(containerSize.getWidth() - mOverflowButtonSize.getWidth());
                    mOverflowPanel.setX(0);
                } else {
                    mContentContainer.setX(mPopupWindow.getWidth() - containerSize.getWidth() - mMarginHorizontal);
                    mMainPanel.setX(-mContentContainer.getX());
                    mOverflowButton.setX(0);
                    mOverflowPanel.setX(0);
                }
                if (mOpenOverflowUpwards) {
                    mContentContainer.setY(mMarginVertical);
                    mMainPanel.setY(containerSize.getHeight() - mContentContainer.getHeight());
                    mOverflowButton.setY(containerSize.getHeight() - mOverflowButtonSize.getHeight());
                    mOverflowPanel.setY(0);
                } else {
                    mContentContainer.setY(mMarginVertical);
                    mMainPanel.setY(0);
                    mOverflowButton.setY(0);
                    mOverflowPanel.setY(mOverflowButtonSize.getHeight());
                }
            } else {
                final Size containerSize = mMainPanelSize;
                setSize(mContentContainer, containerSize);
                mMainPanel.setAlpha(1);
                mMainPanel.setVisibility(View.VISIBLE);
                mOverflowPanel.setAlpha(0);
                mOverflowPanel.setVisibility(View.INVISIBLE);
                mOverflowButtonIcon.setImageDrawable(mOverflow);
                mOverflowButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
                if (hasOverflow()) {
                    if (isInRTLMode()) {
                        mContentContainer.setX(mMarginHorizontal);
                        mMainPanel.setX(0);
                        mOverflowButton.setX(0);
                        mOverflowPanel.setX(0);
                    } else {
                        mContentContainer.setX(mPopupWindow.getWidth() - containerSize.getWidth() - mMarginHorizontal);
                        mMainPanel.setX(0);
                        mOverflowButton.setX(containerSize.getWidth() - mOverflowButtonSize.getWidth());
                        mOverflowPanel.setX(containerSize.getWidth() - mOverflowPanelSize.getWidth());
                    }
                    if (mOpenOverflowUpwards) {
                        mContentContainer.setY(mMarginVertical + mOverflowPanelSize.getHeight() - containerSize.getHeight());
                        mMainPanel.setY(0);
                        mOverflowButton.setY(0);
                        mOverflowPanel.setY(containerSize.getHeight() - mOverflowPanelSize.getHeight());
                    } else {
                        mContentContainer.setY(mMarginVertical);
                        mMainPanel.setY(0);
                        mOverflowButton.setY(0);
                        mOverflowPanel.setY(mOverflowButtonSize.getHeight());
                    }
                } else {
                    mContentContainer.setX(mMarginHorizontal);
                    mContentContainer.setY(mMarginVertical);
                    mMainPanel.setX(0);
                    mMainPanel.setY(0);
                }
            }
        }

        private void updateOverflowHeight(int suggestedHeight) {
            if (hasOverflow()) {
                final int maxItemSize = (suggestedHeight - mOverflowButtonSize.getHeight()) / mLineHeight;
                final int newHeight = calculateOverflowHeight(maxItemSize);
                if (mOverflowPanelSize.getHeight() != newHeight) {
                    mOverflowPanelSize = new Size(mOverflowPanelSize.getWidth(), newHeight);
                }
                setSize(mOverflowPanel, mOverflowPanelSize);
                if (mIsOverflowOpen) {
                    setSize(mContentContainer, mOverflowPanelSize);
                    if (mOpenOverflowUpwards) {
                        final int deltaHeight = mOverflowPanelSize.getHeight() - newHeight;
                        mContentContainer.setY(mContentContainer.getY() + deltaHeight);
                        mOverflowButton.setY(mOverflowButton.getY() - deltaHeight);
                    }
                } else {
                    setSize(mContentContainer, mMainPanelSize);
                }
                updatePopupSize();
            }
        }

        private void updatePopupSize() {
            int width = 0;
            int height = 0;
            if (mMainPanelSize != null) {
                width = Math.max(width, mMainPanelSize.getWidth());
                height = Math.max(height, mMainPanelSize.getHeight());
            }
            if (mOverflowPanelSize != null) {
                width = Math.max(width, mOverflowPanelSize.getWidth());
                height = Math.max(height, mOverflowPanelSize.getHeight());
            }
            mPopupWindow.setWidth(width + mMarginHorizontal * 2);
            mPopupWindow.setHeight(height + mMarginVertical * 2);
            maybeComputeTransitionDurationScale();
        }

        private void refreshViewPort() {
            mParent.getWindowVisibleDisplayFrame(mViewPortOnScreen);
        }

        private int getAdjustedToolbarWidth(int suggestedWidth) {
            int width = suggestedWidth;
            refreshViewPort();
            int maximumWidth = mViewPortOnScreen.width() - 2 * AndroidUtilities.dp(16);
            if (width <= 0) {
                width = AndroidUtilities.dp(400);
            }
            return Math.min(width, maximumWidth);
        }

        private void setZeroTouchableSurface() {
            mTouchableRegion.setEmpty();
        }

        private void setContentAreaAsTouchableSurface() {
            final int width;
            final int height;
            if (mIsOverflowOpen) {
                width = mOverflowPanelSize.getWidth();
                height = mOverflowPanelSize.getHeight();
            } else {
                width = mMainPanelSize.getWidth();
                height = mMainPanelSize.getHeight();
            }
            mTouchableRegion.set((int) mContentContainer.getX(), (int) mContentContainer.getY(), (int) mContentContainer.getX() + width, (int) mContentContainer.getY() + height);
        }

        private void setTouchableSurfaceInsetsComputer() {
            /*ViewTreeObserver viewTreeObserver = mPopupWindow.getContentView().getRootView().getViewTreeObserver(); TODO
            viewTreeObserver.removeOnComputeInternalInsetsListener(mInsetsComputer);
            viewTreeObserver.addOnComputeInternalInsetsListener(mInsetsComputer);*/
        }

        private boolean isInRTLMode() {
            return false;
        }

        private boolean hasOverflow() {
            return mOverflowPanelSize != null;
        }

        public List<MenuItem> layoutMainPanelItems(List<MenuItem> menuItems, final int toolbarWidth) {
            int availableWidth = toolbarWidth;
            final LinkedList<MenuItem> remainingMenuItems = new LinkedList<>(menuItems);
            /*final LinkedList<MenuItem> overflowMenuItems = new LinkedList<>();
            for (MenuItem menuItem : menuItems) {
                if (menuItem.requiresOverflow()) {
                    overflowMenuItems.add(menuItem); TODO
                } else {
                    remainingMenuItems.add(menuItem);
                }
            }
            remainingMenuItems.addAll(overflowMenuItems);*/
            mMainPanel.removeAllViews();
            mMainPanel.setPaddingRelative(0, 0, 0, 0);
            boolean isFirstItem = true;
            Iterator<MenuItem> it = remainingMenuItems.iterator();
            while (it.hasNext()) {
                final MenuItem menuItem = it.next();
                boolean isLastItem = !it.hasNext();
                if (menuItem != null && premiumLockClickListener != null) {
                    if (premiumOptions.contains(menuItem.getItemId())) {
                        continue;
                    }
                }
                /*if (!isFirstItem && menuItem.requiresOverflow()) {
                    break;
                }*/
                final View menuItemButton = createMenuItemButton(mContext, menuItem, mIconTextSpacing, isFirstItem, isLastItem);
                if (menuItemButton instanceof LinearLayout) {
                    ((LinearLayout) menuItemButton).setGravity(Gravity.CENTER);
                }
                menuItemButton.setPaddingRelative((int) ((isFirstItem ? 1.5 : 1) * menuItemButton.getPaddingStart()), menuItemButton.getPaddingTop(), (int) ((isLastItem ? 1.5 : 1) * menuItemButton.getPaddingEnd()), menuItemButton.getPaddingBottom());
                menuItemButton.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                final int menuItemButtonWidth = Math.min(menuItemButton.getMeasuredWidth(), toolbarWidth);
                final boolean canFitWithOverflow = menuItemButtonWidth <= availableWidth - mOverflowButtonSize.getWidth();
                final boolean canFitNoOverflow = isLastItem && menuItemButtonWidth <= availableWidth;
                if (canFitWithOverflow || canFitNoOverflow) {
                    setButtonTagAndClickListener(menuItemButton, menuItem);
                    //menuItemButton.setTooltipText(menuItem.getTooltipText()); TODO
                    mMainPanel.addView(menuItemButton);
                    final ViewGroup.LayoutParams params = menuItemButton.getLayoutParams();
                    params.width = menuItemButtonWidth;
                    menuItemButton.setLayoutParams(params);
                    availableWidth -= menuItemButtonWidth;
                    it.remove();
                } else {
                    break;
                }
                isFirstItem = false;
            }
            if (!remainingMenuItems.isEmpty()) {
                mMainPanel.setPaddingRelative(0, 0, mOverflowButtonSize.getWidth(), 0);
            }
            mMainPanelSize = measure(mMainPanel);
            return remainingMenuItems;
        }

        private void updateMainPanelItemsSelectors() {

        }

        @SuppressWarnings("unchecked")
        private void layoutOverflowPanelItems(List<MenuItem> menuItems) {
            ArrayAdapter<MenuItem> overflowPanelAdapter = (ArrayAdapter<MenuItem>) mOverflowPanel.getAdapter();
            overflowPanelAdapter.clear();
            if (premiumLockClickListener != null) {
                Collections.sort(menuItems, (a, b) -> {
                    final int aPremium = premiumOptions.contains(a.getItemId()) ? 1 : 0;
                    final int bPremium = premiumOptions.contains(b.getItemId()) ? 1 : 0;
                    return aPremium - bPremium;
                });
            }
            final int size = menuItems.size();
            final boolean premiumLocked = MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked();
            for (int i = 0; i < size; i++) {
                final MenuItem menuItem = menuItems.get(i);
                final boolean show;
                if (premiumLockClickListener == null) {
                    show = true;
                } else if (premiumOptions.contains(menuItem.getItemId())) {
                    show = !premiumLocked;
                } else {
                    show = true;
                }
                if (show) {
                    overflowPanelAdapter.add(menuItem);
                }
            }
            mOverflowPanel.setAdapter(overflowPanelAdapter);
            if (mOpenOverflowUpwards) {
                mOverflowPanel.setY(0);
            } else {
                mOverflowPanel.setY(mOverflowButtonSize.getHeight());
            }
            int width = Math.max(getOverflowWidth(), mOverflowButtonSize.getWidth());
            int height = calculateOverflowHeight(MAX_OVERFLOW_SIZE);
            mOverflowPanelSize = new Size(width, height);
            setSize(mOverflowPanel, mOverflowPanelSize);
        }

        private void preparePopupContent() {
            mContentContainer.removeAllViews();
            if (hasOverflow()) {
                mContentContainer.addView(mOverflowPanel);
            }
            mContentContainer.addView(mMainPanel);
            if (hasOverflow()) {
                mContentContainer.addView(mOverflowButton);
            }
            setPanelsStatesAtRestingPosition();
            setContentAreaAsTouchableSurface();
            if (isInRTLMode()) {
                mContentContainer.setAlpha(0);
                mContentContainer.post(mPreparePopupContentRTLHelper);
            }
        }

        @SuppressWarnings("unchecked")
        private void clearPanels() {
            mOverflowPanelSize = null;
            mMainPanelSize = null;
            mIsOverflowOpen = false;
            updateOverflowButtonClickListener();
            mMainPanel.removeAllViews();
            ArrayAdapter<MenuItem> overflowPanelAdapter = (ArrayAdapter<MenuItem>) mOverflowPanel.getAdapter();
            overflowPanelAdapter.clear();
            mOverflowPanel.setAdapter(overflowPanelAdapter);
            mContentContainer.removeAllViews();
        }

        private void positionContentYCoordinatesIfOpeningOverflowUpwards() {
            if (mOpenOverflowUpwards) {
                mMainPanel.setY(mContentContainer.getHeight() - mMainPanelSize.getHeight());
                mOverflowButton.setY(mContentContainer.getHeight() - mOverflowButton.getHeight());
                mOverflowPanel.setY(mContentContainer.getHeight() - mOverflowPanelSize.getHeight());
            }
        }

        private int getOverflowWidth() {
            int overflowWidth = 0;
            final int count = mOverflowPanel.getAdapter().getCount();
            for (int i = 0; i < count; i++) {
                MenuItem menuItem = (MenuItem) mOverflowPanel.getAdapter().getItem(i);
                overflowWidth = Math.max(mOverflowPanelViewHelper.calculateWidth(menuItem), overflowWidth);
            }
            return overflowWidth;
        }

        private int calculateOverflowHeight(int maxItemSize) {
            int actualSize = Math.min(MAX_OVERFLOW_SIZE, Math.min(Math.max(MIN_OVERFLOW_SIZE, maxItemSize), mOverflowPanel.getCount()));
            int extension = 0;
            if (actualSize < mOverflowPanel.getCount()) {
                extension = (int) (mLineHeight * 0.5f);
            }
            return actualSize * mLineHeight + mOverflowButtonSize.getHeight() + extension;
        }

        private void setButtonTagAndClickListener(View menuItemButton, MenuItem menuItem) {
            menuItemButton.setTag(menuItem);
            menuItemButton.setOnClickListener(mMenuItemButtonOnClickListener);
        }

        private int getAdjustedDuration(int originalDuration) {
            if (mTransitionDurationScale < 150) {
                return Math.max(originalDuration - 50, 0);
            } else if (mTransitionDurationScale > 300) {
                return originalDuration + 50;
            }
            return originalDuration;
        }

        private void maybeComputeTransitionDurationScale() {
            if (mMainPanelSize != null && mOverflowPanelSize != null) {
                int w = mMainPanelSize.getWidth() - mOverflowPanelSize.getWidth();
                int h = mOverflowPanelSize.getHeight() - mMainPanelSize.getHeight();
                mTransitionDurationScale = (int) (Math.sqrt(w * w + h * h) / mContentContainer.getContext().getResources().getDisplayMetrics().density);
            }
        }

        private ViewGroup createMainPanel() {
            return new LinearLayout(mContext) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (isOverflowAnimating() && mMainPanelSize != null) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMainPanelSize.getWidth(), MeasureSpec.EXACTLY);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    return isOverflowAnimating();
                }
            };
        }

        private int shiftDp = -4;

        private OverflowPanel createOverflowPanel() {
            final OverflowPanel overflowPanel = new OverflowPanel(this);
            overflowPanel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overflowPanel.setDivider(null);
            overflowPanel.setDividerHeight(0);
            final ArrayAdapter adapter = new ArrayAdapter<MenuItem>(mContext, 0) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    return mOverflowPanelViewHelper.getView(getItem(position), mOverflowPanelSize.getWidth(), convertView);
                }
            };
            overflowPanel.setAdapter(adapter);
            overflowPanel.setOnItemClickListener((parent, view, position, id) -> {
                MenuItem menuItem = (MenuItem) overflowPanel.getAdapter().getItem(position);
                if (premiumLockClickListener != null && premiumOptions.contains(menuItem.getItemId())) {
                    AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    premiumLockClickListener.run();
                } else if (mOnMenuItemClickListener != null) {
                    mOnMenuItemClickListener.onMenuItemClick(menuItem);
                }
            });
            return overflowPanel;
        }

        private boolean isOverflowAnimating() {
            final boolean overflowOpening = mOpenOverflowAnimation.hasStarted() && !mOpenOverflowAnimation.hasEnded();
            final boolean overflowClosing = mCloseOverflowAnimation.hasStarted() && !mCloseOverflowAnimation.hasEnded();
            return overflowOpening || overflowClosing;
        }

        private Animation.AnimationListener createOverflowAnimationListener() {
            return new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    mOverflowButton.setEnabled(false);
                    mMainPanel.setVisibility(View.VISIBLE);
                    mOverflowPanel.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mContentContainer.post(() -> {
                        setPanelsStatesAtRestingPosition();
                        setContentAreaAsTouchableSurface();
                    });
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            };
        }

        private Size measure(View view) {
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            return new Size(view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        private void setSize(View view, int width, int height) {
            view.setMinimumWidth(width);
            view.setMinimumHeight(height);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params = (params == null) ? new ViewGroup.LayoutParams(0, 0) : params;
            params.width = width;
            params.height = height;
            view.setLayoutParams(params);
        }

        private void setSize(View view, Size size) {
            setSize(view, size.getWidth(), size.getHeight());
        }

        private void setWidth(View view, int width) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            setSize(view, width, params.height);
        }

        private void setHeight(View view, int height) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            setSize(view, params.width, height);
        }

        private final class OverflowPanel extends ListView {
            private final FloatingToolbarPopup mPopup;

            OverflowPanel(FloatingToolbarPopup popup) {
                super(popup.mContext);
                this.mPopup = popup;
                setVerticalScrollBarEnabled(false);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() + AndroidUtilities.dp(6), AndroidUtilities.dp(6));
                    }
                });
                setClipToOutline(true);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = mPopup.mOverflowPanelSize.getHeight() - mPopup.mOverflowButtonSize.getHeight();
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (mPopup.isOverflowAnimating()) {
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected boolean awakenScrollBars() {
                return super.awakenScrollBars();
            }
        }

        private final class LogAccelerateInterpolator implements Interpolator {
            private final int BASE = 100;
            private final float LOGS_SCALE = 1f / computeLog(1, BASE);

            private float computeLog(float t, int base) {
                return (float) (1 - Math.pow(base, -t));
            }

            @Override
            public float getInterpolation(float t) {
                return 1 - computeLog(1 - t, BASE) * LOGS_SCALE;
            }
        }

        private final class OverflowPanelViewHelper {
            private final View mCalculator;
            private final int mIconTextSpacing;
            private final int mSidePadding;
            private final Context mContext;

            public OverflowPanelViewHelper(Context context, int iconTextSpacing) {
                mContext = context;
                mIconTextSpacing = iconTextSpacing;
                mSidePadding = AndroidUtilities.dp(18);
                mCalculator = createMenuButton(null);
            }

            public View getView(MenuItem menuItem, int minimumWidth, View convertView) {
                if (convertView != null) {
                    updateMenuItemButton(convertView, menuItem, mIconTextSpacing, premiumLockClickListener != null);
                } else {
                    convertView = createMenuButton(menuItem);
                }
                convertView.setMinimumWidth(minimumWidth);
                return convertView;
            }

            public int calculateWidth(MenuItem menuItem) {
                updateMenuItemButton(mCalculator, menuItem, mIconTextSpacing, premiumLockClickListener != null);
                mCalculator.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                return mCalculator.getMeasuredWidth();
            }

            private View createMenuButton(MenuItem menuItem) {
                View button = createMenuItemButton(mContext, menuItem, mIconTextSpacing, false, false);
                button.setPadding(mSidePadding, 0, mSidePadding, 0);
                return button;
            }
        }
    }

    private View createMenuItemButton(Context context, MenuItem menuItem, int iconTextSpacing, boolean first, boolean last) {
        LinearLayout menuItemButton = new LinearLayout(context);
        menuItemButton.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        menuItemButton.setOrientation(LinearLayout.HORIZONTAL);
        menuItemButton.setMinimumWidth(AndroidUtilities.dp(48));
        menuItemButton.setMinimumHeight(AndroidUtilities.dp(48));
        menuItemButton.setPaddingRelative(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setFocusable(false);
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setFocusableInTouchMode(false);
        int color;
        int selectorColor = getThemedColor(Theme.key_listSelector);
        if (currentStyle == STYLE_DIALOG) {
            textView.setTextColor(color = getThemedColor(Theme.key_dialogTextBlack));
        } else if (currentStyle == STYLE_BLACK) {
            textView.setTextColor(color = 0xfffafafa);
            selectorColor = 0x20ffffff;
        } else if (currentStyle == STYLE_THEME) {
            textView.setTextColor(color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        } else {
            color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        }
        if (first || last) {
            menuItemButton.setBackground(Theme.createRadSelectorDrawable(selectorColor, first ? 6 : 0, last ? 6 : 0, last ? 6 : 0, first ? 6 : 0));
        } else {
            menuItemButton.setBackground(Theme.getSelectorDrawable(selectorColor, false));
        }

        textView.setPaddingRelative(AndroidUtilities.dp(11), 0, 0, 0);
        menuItemButton.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(48)));

        menuItemButton.addView(new Space(context), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, 1));

        ImageView lockView = new ImageView(context);
        lockView.setImageResource(R.drawable.msg_mini_lock3);
        lockView.setScaleType(ImageView.ScaleType.CENTER);
        lockView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(color, .4f), PorterDuff.Mode.SRC_IN));
        lockView.setVisibility(View.GONE);
        menuItemButton.addView(lockView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 12, 0, 0, 0));

        if (menuItem != null) {
            updateMenuItemButton(menuItemButton, menuItem, iconTextSpacing, premiumLockClickListener != null);
        }
        return menuItemButton;
    }

    private static void updateMenuItemButton(View menuItemButton, MenuItem menuItem, int iconTextSpacing, boolean containsPremium) {
        ViewGroup viewGroup = (ViewGroup) menuItemButton;
        final TextView buttonText = (TextView) viewGroup.getChildAt(0);
        buttonText.setEllipsize(null);
        if (TextUtils.isEmpty(menuItem.getTitle())) {
            buttonText.setVisibility(View.GONE);
        } else {
            buttonText.setVisibility(View.VISIBLE);
            buttonText.setText(menuItem.getTitle());
        }
        buttonText.setPaddingRelative(0, 0, 0, 0);

        final boolean premium = containsPremium && premiumOptions.contains(menuItem.getItemId());
        viewGroup.getChildAt(2).setVisibility(premium ? View.VISIBLE : View.GONE);
        /*final CharSequence contentDescription = menuItem.getContentDescription(); TODO
        if (TextUtils.isEmpty(contentDescription)) {
            menuItemButton.setContentDescription(menuItem.getTitle());
        } else {
            menuItemButton.setContentDescription(contentDescription);
        }*/
    }

    private ViewGroup createContentContainer(Context context) {
        RelativeLayout contentContainer = new RelativeLayout(context);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.bottomMargin = layoutParams.leftMargin = layoutParams.topMargin = layoutParams.rightMargin = AndroidUtilities.dp(20);
        contentContainer.setLayoutParams(layoutParams);
        contentContainer.setElevation(AndroidUtilities.dp(2));
        contentContainer.setFocusable(true);
        contentContainer.setFocusableInTouchMode(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        int r = AndroidUtilities.dp(6);
        shape.setCornerRadii(new float[] { r, r, r, r, r, r, r, r });
        if (currentStyle == STYLE_DIALOG) {
            shape.setColor(getThemedColor(Theme.key_dialogBackground));
        } else if (currentStyle == STYLE_BLACK) {
            shape.setColor(0xf9222222);
        } else if (currentStyle == STYLE_THEME) {
            shape.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        }
        contentContainer.setBackgroundDrawable(shape);
        contentContainer.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentContainer.setClipToOutline(true);
        return contentContainer;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private static PopupWindow createPopupWindow(ViewGroup content) {
        ViewGroup popupContentHolder = new LinearLayout(content.getContext());
        PopupWindow popupWindow = new PopupWindow(popupContentHolder);
        popupWindow.setClippingEnabled(false);
        popupWindow.setAnimationStyle(0);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        popupContentHolder.addView(content);
        return popupWindow;
    }

    private static AnimatorSet createEnterAnimation(View view) {
        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1).setDuration(150));
        return animation;
    }

    private static AnimatorSet createExitAnimation(View view, int startDelay, Animator.AnimatorListener listener) {
        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, 1, 0).setDuration(100));
        animation.setStartDelay(startDelay);
        animation.addListener(listener);
        return animation;
    }
}
