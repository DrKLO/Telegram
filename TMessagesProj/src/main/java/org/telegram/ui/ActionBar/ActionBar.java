/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ActionBar extends FrameLayout {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {

        }

        public boolean canOpenMenu() {
            return true;
        }
    }

    private ImageView backButtonImageView;
    private SimpleTextView titleTextView;
    private SimpleTextView subtitleTextView;
    private View actionModeTop;
    private ActionBarMenu menu;
    private ActionBarMenu actionMode;
    private boolean occupyStatusBar = Build.VERSION.SDK_INT >= 21;
    private boolean actionModeVisible;
    private boolean addToContainer = true;
    private boolean interceptTouches = true;
    private int extraHeight;
    private AnimatorSet actionModeAnimation;

    private int titleRightMargin;

    private boolean allowOverlayTitle;
    private CharSequence lastTitle;
    private CharSequence lastSubtitle;
    private Runnable titleActionRunnable;
    private boolean castShadows = true;

    protected boolean isSearchFieldVisible;
    protected int itemsBackgroundColor;
    protected int itemsActionModeBackgroundColor;
    protected int itemsColor;
    protected int itemsActionModeColor;
    private boolean isBackOverlayVisible;
    protected BaseFragment parentFragment;
    public ActionBarMenuOnItemClick actionBarMenuOnItemClick;

    public ActionBar(Context context) {
        super(context);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (titleActionRunnable != null) {
                    titleActionRunnable.run();
                }
            }
        });
    }

    private void createBackButtonImage() {
        if (backButtonImageView != null) {
            return;
        }
        backButtonImageView = new ImageView(getContext());
        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
        backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
        if (itemsColor != 0) {
            backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.MULTIPLY));
        }
        backButtonImageView.setPadding(AndroidUtilities.dp(1), 0, 0, 0);
        addView(backButtonImageView, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.TOP));

        backButtonImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!actionModeVisible && isSearchFieldVisible) {
                    closeSearchField();
                    return;
                }
                if (actionBarMenuOnItemClick != null) {
                    actionBarMenuOnItemClick.onItemClick(-1);
                }
            }
        });
    }

    public void setBackButtonDrawable(Drawable drawable) {
        if (backButtonImageView == null) {
            createBackButtonImage();
        }
        backButtonImageView.setVisibility(drawable == null ? GONE : VISIBLE);
        backButtonImageView.setImageDrawable(drawable);
        if (drawable instanceof BackDrawable) {
            BackDrawable backDrawable = (BackDrawable) drawable;
            backDrawable.setRotation(isActionModeShowed() ? 1 : 0, false);
            backDrawable.setRotatedColor(itemsActionModeColor);
            backDrawable.setColor(itemsColor);
        }
    }

    public void setBackButtonImage(int resource) {
        if (backButtonImageView == null) {
            createBackButtonImage();
        }
        backButtonImageView.setVisibility(resource == 0 ? GONE : VISIBLE);
        backButtonImageView.setImageResource(resource);
    }

    private void createSubtitleTextView() {
        if (subtitleTextView != null) {
            return;
        }
        subtitleTextView = new SimpleTextView(getContext());
        subtitleTextView.setGravity(Gravity.LEFT);
        subtitleTextView.setVisibility(GONE);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        addView(subtitleTextView, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    public void setAddToContainer(boolean value) {
        addToContainer = value;
    }

    public boolean getAddToContainer() {
        return addToContainer;
    }

    public void setSubtitle(CharSequence value) {
        if (value != null && subtitleTextView == null) {
            createSubtitleTextView();
        }
        if (subtitleTextView != null) {
            lastSubtitle = value;
            subtitleTextView.setVisibility(!TextUtils.isEmpty(value) && !isSearchFieldVisible ? VISIBLE : GONE);
            subtitleTextView.setText(value);
        }
    }

    private void createTitleTextView() {
        if (titleTextView != null) {
            return;
        }
        titleTextView = new SimpleTextView(getContext());
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(titleTextView, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    public void setTitleRightMargin(int value) {
        titleRightMargin = value;
    }

    public void setTitle(CharSequence value) {
        if (value != null && titleTextView == null) {
            createTitleTextView();
        }
        if (titleTextView != null) {
            lastTitle = value;
            titleTextView.setVisibility(value != null && !isSearchFieldVisible ? VISIBLE : INVISIBLE);
            titleTextView.setText(value);
        }
    }

    public void setTitleColor(int color) {
        if (titleTextView == null) {
            createTitleTextView();
        }
        titleTextView.setTextColor(color);
    }

    public void setSubtitleColor(int color) {
        if (subtitleTextView == null) {
            createSubtitleTextView();
        }
        subtitleTextView.setTextColor(color);
    }

    public void setPopupItemsColor(int color) {
        if (menu != null) {
            menu.setPopupItemsColor(color);
        }
    }

    public void setPopupBackgroundColor(int color) {
        if (menu != null) {
            menu.redrawPopup(color);
        }
    }

    public SimpleTextView getSubtitleTextView() {
        return subtitleTextView;
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView;
    }

    public String getTitle() {
        if (titleTextView == null) {
            return null;
        }
        return titleTextView.getText().toString();
    }

    public String getSubtitle() {
        if (subtitleTextView == null) {
            return null;
        }
        return subtitleTextView.getText().toString();
    }

    public ActionBarMenu createMenu() {
        if (menu != null) {
            return menu;
        }
        menu = new ActionBarMenu(getContext(), this);
        addView(menu, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        return menu;
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public ActionBarMenuOnItemClick getActionBarMenuOnItemClick() {
        return actionBarMenuOnItemClick;
    }

    public View getBackButton() {
        return backButtonImageView;
    }

    public ActionBarMenu createActionMode() {
        if (actionMode != null) {
            return actionMode;
        }
        actionMode = new ActionBarMenu(getContext(), this);
        actionMode.isActionMode = true;
        actionMode.setBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefault));
        addView(actionMode, indexOfChild(backButtonImageView));
        actionMode.setPadding(0, occupyStatusBar ? AndroidUtilities.statusBarHeight : 0, 0, 0);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)actionMode.getLayoutParams();
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.RIGHT;
        actionMode.setLayoutParams(layoutParams);
        actionMode.setVisibility(INVISIBLE);

        if (occupyStatusBar && actionModeTop == null) {
            actionModeTop = new View(getContext());
            actionModeTop.setBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultTop));
            addView(actionModeTop);
            layoutParams = (FrameLayout.LayoutParams)actionModeTop.getLayoutParams();
            layoutParams.height = AndroidUtilities.statusBarHeight;
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            actionModeTop.setLayoutParams(layoutParams);
            actionModeTop.setVisibility(INVISIBLE);
        }

        return actionMode;
    }

    public void showActionMode() {
        if (actionMode == null || actionModeVisible) {
            return;
        }
        actionModeVisible = true;
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(actionMode, "alpha", 0.0f, 1.0f));
        if (occupyStatusBar && actionModeTop != null) {
            animators.add(ObjectAnimator.ofFloat(actionModeTop, "alpha", 0.0f, 1.0f));
        }
        if (actionModeAnimation != null) {
            actionModeAnimation.cancel();
        }
        actionModeAnimation = new AnimatorSet();
        actionModeAnimation.playTogether(animators);
        actionModeAnimation.setDuration(200);
        actionModeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                actionMode.setVisibility(VISIBLE);
                if (occupyStatusBar && actionModeTop != null) {
                    actionModeTop.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                    if (titleTextView != null) {
                        titleTextView.setVisibility(INVISIBLE);
                    }
                    if (subtitleTextView != null) {
                        subtitleTextView.setVisibility(INVISIBLE);
                    }
                    if (menu != null) {
                        menu.setVisibility(INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                }
            }
        });
        actionModeAnimation.start();
        if (backButtonImageView != null) {
            Drawable drawable = backButtonImageView.getDrawable();
            if (drawable instanceof BackDrawable) {
                ((BackDrawable) drawable).setRotation(1, true);
            }
            backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsActionModeBackgroundColor));
        }
    }

    public void hideActionMode() {
        if (actionMode == null || !actionModeVisible) {
            return;
        }
        actionModeVisible = false;
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(actionMode, "alpha", 0.0f));
        if (occupyStatusBar && actionModeTop != null) {
            animators.add(ObjectAnimator.ofFloat(actionModeTop, "alpha", 0.0f));
        }
        if (actionModeAnimation != null) {
            actionModeAnimation.cancel();
        }
        actionModeAnimation = new AnimatorSet();
        actionModeAnimation.playTogether(animators);
        actionModeAnimation.setDuration(200);
        actionModeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                    actionMode.setVisibility(INVISIBLE);
                    if (occupyStatusBar && actionModeTop != null) {
                        actionModeTop.setVisibility(INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (actionModeAnimation != null && actionModeAnimation.equals(animation)) {
                    actionModeAnimation = null;
                }
            }
        });
        actionModeAnimation.start();
        if (titleTextView != null) {
            titleTextView.setVisibility(VISIBLE);
        }
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(VISIBLE);
        }
        if (menu != null) {
            menu.setVisibility(VISIBLE);
        }
        if (backButtonImageView != null) {
            Drawable drawable = backButtonImageView.getDrawable();
            if (drawable instanceof BackDrawable) {
                ((BackDrawable) drawable).setRotation(0, true);
            }
            backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
        }
    }

    public void showActionModeTop() {
        if (occupyStatusBar && actionModeTop == null) {
            actionModeTop = new View(getContext());
            actionModeTop.setBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultTop));
            addView(actionModeTop);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionModeTop.getLayoutParams();
            layoutParams.height = AndroidUtilities.statusBarHeight;
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            actionModeTop.setLayoutParams(layoutParams);
        }
    }

    public void setActionModeTopColor(int color) {
        if (actionModeTop != null) {
            actionModeTop.setBackgroundColor(color);
        }
    }

    public void setSearchTextColor(int color, boolean placeholder) {
        if (menu != null) {
            menu.setSearchTextColor(color, placeholder);
        }
    }

    public void setActionModeColor(int color) {
        if (actionMode != null) {
            actionMode.setBackgroundColor(color);
        }
    }

    public boolean isActionModeShowed() {
        return actionMode != null && actionModeVisible;
    }

    protected void onSearchFieldVisibilityChanged(boolean visible) {
        isSearchFieldVisible = visible;
        if (titleTextView != null) {
            titleTextView.setVisibility(visible ? INVISIBLE : VISIBLE);
        }
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(visible ? INVISIBLE : VISIBLE);
        }
        Drawable drawable = backButtonImageView.getDrawable();
        if (drawable != null && drawable instanceof MenuDrawable) {
            ((MenuDrawable) drawable).setRotation(visible ? 1 : 0, true);
        }
    }

    public void setInterceptTouches(boolean value) {
        interceptTouches = value;
    }

    public void setExtraHeight(int value) {
        extraHeight = value;
    }

    public void closeSearchField() {
        closeSearchField(true);
    }

    public void closeSearchField(boolean closeKeyboard) {
        if (!isSearchFieldVisible || menu == null) {
            return;
        }
        menu.closeSearchField(closeKeyboard);
    }

    public void openSearchField(String text) {
        if (menu == null || text == null) {
            return;
        }
        menu.openSearchField(!isSearchFieldVisible, text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int actionBarHeight = getCurrentActionBarHeight();
        int actionBarHeightSpec = MeasureSpec.makeMeasureSpec(actionBarHeight, MeasureSpec.EXACTLY);

        setMeasuredDimension(width, actionBarHeight + (occupyStatusBar ? AndroidUtilities.statusBarHeight : 0) + extraHeight);

        int textLeft;
        if (backButtonImageView != null && backButtonImageView.getVisibility() != GONE) {
            backButtonImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(54), MeasureSpec.EXACTLY), actionBarHeightSpec);
            textLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 80 : 72);
        } else {
            textLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 26 : 18);
        }

        if (menu != null && menu.getVisibility() != GONE) {
            int menuWidth;
            if (isSearchFieldVisible) {
                menuWidth = MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 74 : 66), MeasureSpec.EXACTLY);
            } else {
                menuWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
            }
            menu.measure(menuWidth, actionBarHeightSpec);
        }

        if (titleTextView != null && titleTextView.getVisibility() != GONE || subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
            int availableWidth = width - (menu != null ? menu.getMeasuredWidth() : 0) - AndroidUtilities.dp(16) - textLeft - titleRightMargin;

            if (titleTextView != null && titleTextView.getVisibility() != GONE) {
                titleTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20);
                titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
            }
            if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                subtitleTextView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 14 : 16);
                subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
            }
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || child == titleTextView || child == subtitleTextView || child == menu || child == backButtonImageView) {
                continue;
            }
            measureChildWithMargins(child, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY), 0);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int additionalTop = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;

        int textLeft;
        if (backButtonImageView != null && backButtonImageView.getVisibility() != GONE) {
            backButtonImageView.layout(0, additionalTop, backButtonImageView.getMeasuredWidth(), additionalTop + backButtonImageView.getMeasuredHeight());
            textLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 80 : 72);
        } else {
            textLeft = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 26 : 18);
        }

        if (menu != null && menu.getVisibility() != GONE) {
            int menuLeft = isSearchFieldVisible ? AndroidUtilities.dp(AndroidUtilities.isTablet() ? 74 : 66) : (right - left) - menu.getMeasuredWidth();
            menu.layout(menuLeft, additionalTop, menuLeft + menu.getMeasuredWidth(), additionalTop + menu.getMeasuredHeight());
        }

        if (titleTextView != null && titleTextView.getVisibility() != GONE) {
            int textTop;
            if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
                textTop = (getCurrentActionBarHeight() / 2 - titleTextView.getTextHeight()) / 2 + AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 2 : 3);
            } else {
                textTop = (getCurrentActionBarHeight() - titleTextView.getTextHeight()) / 2;
            }
            titleTextView.layout(textLeft, additionalTop + textTop, textLeft + titleTextView.getMeasuredWidth(), additionalTop + textTop + titleTextView.getTextHeight());
        }
        if (subtitleTextView != null && subtitleTextView.getVisibility() != GONE) {
            int textTop = getCurrentActionBarHeight() / 2 + (getCurrentActionBarHeight() / 2 - subtitleTextView.getTextHeight()) / 2 - AndroidUtilities.dp(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 1 : 1);
            subtitleTextView.layout(textLeft, additionalTop + textTop, textLeft + subtitleTextView.getMeasuredWidth(), additionalTop + textTop + subtitleTextView.getTextHeight());
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || child == titleTextView || child == subtitleTextView || child == menu || child == backButtonImageView) {
                continue;
            }

            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
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
                    childLeft = (right - left - width) / 2 + lp.leftMargin - lp.rightMargin;
                    break;
                case Gravity.RIGHT:
                    childLeft = right - width - lp.rightMargin;
                    break;
                case Gravity.LEFT:
                default:
                    childLeft = lp.leftMargin;
            }

            switch (verticalGravity) {
                case Gravity.TOP:
                    childTop = lp.topMargin;
                    break;
                case Gravity.CENTER_VERTICAL:
                    childTop = (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin;
                    break;
                case Gravity.BOTTOM:
                    childTop = (bottom - top) - height - lp.bottomMargin;
                    break;
                default:
                    childTop = lp.topMargin;
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }

    public void onMenuButtonPressed() {
        if (menu != null) {
            menu.onMenuButtonPressed();
        }
    }

    protected void onPause() {
        if (menu != null) {
            menu.hideAllPopupMenus();
        }
    }

    public void setAllowOverlayTitle(boolean value) {
        allowOverlayTitle = value;
    }

    public void setTitleOverlayText(String title, String subtitle, Runnable action) {
        if (!allowOverlayTitle || parentFragment.parentLayout == null) {
            return;
        }
        CharSequence textToSet = title != null ? title : lastTitle;
        if (textToSet != null && titleTextView == null) {
            createTitleTextView();
        }
        if (titleTextView != null) {
            titleTextView.setVisibility(textToSet != null && !isSearchFieldVisible ? VISIBLE : INVISIBLE);
            titleTextView.setText(textToSet);
        }
        textToSet = subtitle != null ? subtitle : lastSubtitle;
        if (textToSet != null && subtitleTextView == null) {
            createSubtitleTextView();
        }
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(!TextUtils.isEmpty(textToSet) && !isSearchFieldVisible ? VISIBLE : GONE);
            subtitleTextView.setText(textToSet);
        }
        titleActionRunnable = action;
    }

    public boolean isSearchFieldVisible() {
        return isSearchFieldVisible;
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
        if (actionMode != null) {
            actionMode.setPadding(0, occupyStatusBar ? AndroidUtilities.statusBarHeight : 0, 0, 0);
        }
    }

    public boolean getOccupyStatusBar() {
        return occupyStatusBar;
    }

    public void setItemsBackgroundColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeBackgroundColor = color;
            if (actionModeVisible) {
                if (backButtonImageView != null) {
                    backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsActionModeBackgroundColor));
                }
            }
            if (actionMode != null) {
                actionMode.updateItemsBackgroundColor();
            }
        } else {
            itemsBackgroundColor = color;
            if (backButtonImageView != null) {
                backButtonImageView.setBackgroundDrawable(Theme.createSelectorDrawable(itemsBackgroundColor));
            }
            if (menu != null) {
                menu.updateItemsBackgroundColor();
            }
        }
    }

    public void setItemsColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeColor = color;
            if (actionMode != null) {
                actionMode.updateItemsColor();
            }
            if (backButtonImageView != null) {
                Drawable drawable = backButtonImageView.getDrawable();
                if (drawable instanceof BackDrawable) {
                    ((BackDrawable) drawable).setRotatedColor(color);
                }
            }
        } else {
            itemsColor = color;
            if (backButtonImageView != null) {
                if (itemsColor != 0) {
                    backButtonImageView.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.MULTIPLY));
                    Drawable drawable = backButtonImageView.getDrawable();
                    if (drawable instanceof BackDrawable) {
                        ((BackDrawable) drawable).setColor(color);
                    }
                }
            }
            if (menu != null) {
                menu.updateItemsColor();
            }
        }
    }

    public void setCastShadows(boolean value) {
        castShadows = value;
    }

    public boolean getCastShadows() {
        return castShadows;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event) || interceptTouches;
    }

    public static int getCurrentActionBarHeight() {
        if (AndroidUtilities.isTablet()) {
            return AndroidUtilities.dp(64);
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return AndroidUtilities.dp(48);
        } else {
            return AndroidUtilities.dp(56);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}