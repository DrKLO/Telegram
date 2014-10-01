/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;

public class ActionBarLayer extends FrameLayout {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {

        }

        public boolean canOpenMenu() {
            return true;
        }
    }

    private FrameLayout backButtonFrameLayout;
    private ImageView logoImageView;
    private ImageView backButtonImageView;
    private TextView titleTextView;
    private TextView subTitleTextView;
    private ActionBarMenu menu;
    private ActionBarMenu actionMode;
    private int logoResourceId;
    private int backResourceId;
    protected ActionBar parentActionBar;
    private boolean oldUseLogo;
    private boolean oldUseBack;
    private View actionOverlay;
    protected boolean isSearchFieldVisible;
    protected int itemsBackgroundResourceId;
    private boolean isBackOverlayVisible;
    protected BaseFragment parentFragment;
    public ActionBarMenuOnItemClick actionBarMenuOnItemClick;
    private int leftMargin = 0;

    public ActionBarLayer(Context context, ActionBar actionBar) {
        super(context);
        parentActionBar = actionBar;
        backButtonFrameLayout = new FrameLayout(context);
        addView(backButtonFrameLayout);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)backButtonFrameLayout.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.FILL_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        backButtonFrameLayout.setLayoutParams(layoutParams);
        backButtonFrameLayout.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        backButtonFrameLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSearchFieldVisible) {
                    closeSearchField();
                    return;
                }
                if (actionBarMenuOnItemClick != null) {
                    actionBarMenuOnItemClick.onItemClick(-1);
                }
            }
        });
        backButtonFrameLayout.setEnabled(false);
    }

    public ActionBarLayer(Context context) {
        super(context);
    }

    public ActionBarLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionBarLayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setExtraLeftMargin(int margin) {
        leftMargin = margin;
    }

    private void positionBackImage(int height) {
        if (backButtonImageView != null) {
            LayoutParams layoutParams = (LayoutParams)backButtonImageView.getLayoutParams();
            layoutParams.width = LayoutParams.WRAP_CONTENT;
            layoutParams.height = LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.setMargins(AndroidUtilities.dp(3 + leftMargin), (height - backButtonImageView.getDrawable().getIntrinsicHeight()) / 2, 0, 0);
            backButtonImageView.setLayoutParams(layoutParams);
        }
    }

    private void positionLogoImage(int height) {
        if (logoImageView != null && logoImageView.getDrawable() != null) {
            LayoutParams layoutParams = (LayoutParams) logoImageView.getLayoutParams();
            if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                layoutParams.width = (int)(logoImageView.getDrawable().getIntrinsicWidth() / 1.3f);
                layoutParams.height = (int)(logoImageView.getDrawable().getIntrinsicHeight() / 1.3f);
                layoutParams.setMargins(AndroidUtilities.dp(12), (height - layoutParams.height) / 2, 0, 0);
            } else {
                layoutParams.width = logoImageView.getDrawable().getIntrinsicWidth();
                layoutParams.height = logoImageView.getDrawable().getIntrinsicHeight();
                layoutParams.setMargins(AndroidUtilities.dp(12), (height - layoutParams.width) / 2, 0, 0);
            }
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            logoImageView.setLayoutParams(layoutParams);
        }
    }

    private void positionTitle(int width, int height) {
        int offset = AndroidUtilities.dp(2);
        if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            offset = AndroidUtilities.dp(1);
        }
        int maxTextWidth = 0;

        LayoutParams layoutParams = null;

        if (titleTextView != null && titleTextView.getVisibility() == VISIBLE) {
            if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                titleTextView.setTextSize(16);
            } else {
                titleTextView.setTextSize(18);
            }

            layoutParams = (LayoutParams) titleTextView.getLayoutParams();
            layoutParams.width = LayoutParams.WRAP_CONTENT;
            layoutParams.height = LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            titleTextView.setLayoutParams(layoutParams);
            titleTextView.measure(width, height);
            maxTextWidth = titleTextView.getMeasuredWidth();
        }
        if (subTitleTextView != null && subTitleTextView.getVisibility() == VISIBLE) {
            if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                subTitleTextView.setTextSize(12);
            } else {
                subTitleTextView.setTextSize(14);
            }

            layoutParams = (LayoutParams) subTitleTextView.getLayoutParams();
            layoutParams.width = LayoutParams.WRAP_CONTENT;
            layoutParams.height = LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            subTitleTextView.setLayoutParams(layoutParams);
            subTitleTextView.measure(width, height);
            maxTextWidth = Math.max(maxTextWidth, subTitleTextView.getMeasuredWidth());
        }

        int x = 0;
        if (logoImageView == null || logoImageView.getVisibility() == GONE) {
            x = AndroidUtilities.dp(16 + leftMargin);
        } else {
            if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                x = AndroidUtilities.dp(22 + leftMargin) + (int)(logoImageView.getDrawable().getIntrinsicWidth() / 1.3f);
            } else {
                x = AndroidUtilities.dp(22 + leftMargin) + logoImageView.getDrawable().getIntrinsicWidth();
            }
        }

        if (menu != null) {
            maxTextWidth = Math.min(maxTextWidth, width - menu.getMeasuredWidth() - AndroidUtilities.dp(16));
        }

        if (titleTextView != null && titleTextView.getVisibility() == VISIBLE) {
            layoutParams = (LayoutParams) titleTextView.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = titleTextView.getMeasuredHeight();
            int y = (height - titleTextView.getMeasuredHeight()) / 2;
            if (subTitleTextView != null && subTitleTextView.getVisibility() == VISIBLE) {
                y = (height / 2 - titleTextView.getMeasuredHeight()) / 2 + offset;
            }
            layoutParams.setMargins(x, y, 0, 0);
            titleTextView.setLayoutParams(layoutParams);
        }
        if (subTitleTextView != null && subTitleTextView.getVisibility() == VISIBLE) {
            layoutParams = (LayoutParams) subTitleTextView.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = subTitleTextView.getMeasuredHeight();
            layoutParams.setMargins(x, height / 2 + (height / 2 - subTitleTextView.getMeasuredHeight()) / 2 - offset, 0, 0);
            subTitleTextView.setLayoutParams(layoutParams);
        }

        ViewGroup.LayoutParams layoutParams1 = backButtonFrameLayout.getLayoutParams();
        layoutParams1.width = x + maxTextWidth + (isSearchFieldVisible ? 0 : AndroidUtilities.dp(6));
        backButtonFrameLayout.setLayoutParams(layoutParams1);
    }

    public void positionMenu(int width, int height) {
        if (menu == null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)menu.getLayoutParams();
        layoutParams.width = isSearchFieldVisible ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        layoutParams.leftMargin = isSearchFieldVisible ? AndroidUtilities.dp(26) + logoImageView.getDrawable().getIntrinsicWidth() : 0;
        menu.setLayoutParams(layoutParams);
        menu.measure(width, height);
    }

    public void setDisplayUseLogoEnabled(boolean value, int resource) {
        if (value && logoImageView == null) {
            logoResourceId = resource;
            logoImageView = new ImageView(getContext());
            logoImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButtonFrameLayout.addView(logoImageView);
        }
        if (logoImageView != null) {
            logoImageView.setVisibility(value ? VISIBLE : GONE);
            logoImageView.setImageResource(resource);
            positionLogoImage(getMeasuredHeight());
        }
    }

    public void setDisplayHomeAsUpEnabled(boolean value, int resource) {
        if (value && backButtonImageView == null) {
            backResourceId = resource;
            backButtonImageView = new ImageView(getContext());
            backButtonFrameLayout.addView(backButtonImageView);
        }
        if (backButtonImageView != null) {
            backButtonImageView.setVisibility(value ? VISIBLE : GONE);
            backButtonFrameLayout.setEnabled(value);
            backButtonImageView.setImageResource(resource);
            positionBackImage(getMeasuredHeight());
        }
    }

    public void setSubtitle(CharSequence value) {
        if (value != null && subTitleTextView == null) {
            subTitleTextView = new TextView(getContext());
            backButtonFrameLayout.addView(subTitleTextView);
            subTitleTextView.setGravity(Gravity.LEFT);
            subTitleTextView.setTextColor(0xffd7e8f7);
            subTitleTextView.setSingleLine(true);
            subTitleTextView.setLines(1);
            subTitleTextView.setMaxLines(1);
            subTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (subTitleTextView != null) {
            subTitleTextView.setVisibility(value != null ? VISIBLE : GONE);
            subTitleTextView.setText(value);
            positionTitle(getMeasuredWidth(), getMeasuredHeight());
        }
    }

    public void setSubTitleIcon(int resourceId, Drawable drawable, int padding) {
        if ((resourceId != 0 || drawable != null) && subTitleTextView == null) {
            subTitleTextView = new TextView(getContext());
            backButtonFrameLayout.addView(subTitleTextView);
            subTitleTextView.setGravity(Gravity.LEFT);
            subTitleTextView.setTextColor(0xffd7e8f7);
            subTitleTextView.setSingleLine(true);
            subTitleTextView.setLines(1);
            subTitleTextView.setMaxLines(1);
            subTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
            positionTitle(getMeasuredWidth(), getMeasuredHeight());
        }
        if (subTitleTextView != null) {
            if (drawable != null) {
                subTitleTextView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            } else {
                subTitleTextView.setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0);
            }
            subTitleTextView.setCompoundDrawablePadding(padding);
        }
    }

    public void setTitle(CharSequence value) {
        if (value != null && titleTextView == null) {
            titleTextView = new TextView(getContext());
            titleTextView.setGravity(Gravity.LEFT);
            titleTextView.setSingleLine(true);
            titleTextView.setLines(1);
            titleTextView.setMaxLines(1);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
            backButtonFrameLayout.addView(titleTextView);
            titleTextView.setTextColor(0xffffffff);
        }
        if (titleTextView != null) {
            titleTextView.setVisibility(value != null ? VISIBLE : GONE);
            titleTextView.setText(value);
            positionTitle(getMeasuredWidth(), getMeasuredHeight());
        }
    }

    public void setTitleIcon(int resourceId, int padding) {
        if (resourceId != 0 && titleTextView == null) {
            titleTextView = new TextView(getContext());
            titleTextView.setGravity(Gravity.LEFT);
            backButtonFrameLayout.addView(titleTextView);
            titleTextView.setTextColor(0xffffffff);
            titleTextView.setSingleLine(true);
            titleTextView.setLines(1);
            titleTextView.setMaxLines(1);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
            positionTitle(getMeasuredWidth(), getMeasuredHeight());
        }
        titleTextView.setCompoundDrawablesWithIntrinsicBounds(resourceId, 0, 0, 0);
        titleTextView.setCompoundDrawablePadding(padding);
    }

    public Drawable getSubTitleIcon() {
        return subTitleTextView.getCompoundDrawables()[0];
    }

    public CharSequence getTitle() {
        if (titleTextView == null) {
            return null;
        }
        return titleTextView.getText();
    }

    public ActionBarMenu createMenu() {
        if (menu != null) {
            return menu;
        }
        menu = new ActionBarMenu(getContext(), parentActionBar, this);
        addView(menu);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)menu.getLayoutParams();
        layoutParams.height = LayoutParams.FILL_PARENT;
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.RIGHT;
        menu.setLayoutParams(layoutParams);
        return menu;
    }

    public void onDestroy() {
        parentActionBar.detachActionBarLayer(this);
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public void setCustomView(int resourceId) {
        LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = li.inflate(resourceId, null);
        addView(view);
    }

    public ActionBarMenu createActionMode() {
        if (actionMode != null) {
            return actionMode;
        }
        actionMode = new ActionBarMenu(getContext(), parentActionBar, this);
        actionMode.setBackgroundResource(R.drawable.editheader);
        addView(actionMode);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)actionMode.getLayoutParams();
        layoutParams.height = LayoutParams.FILL_PARENT;
        layoutParams.width = LayoutParams.FILL_PARENT;
        layoutParams.gravity = Gravity.RIGHT;
        actionMode.setLayoutParams(layoutParams);
        actionMode.setVisibility(GONE);
        return actionMode;
    }

    public void showActionMode() {
        if (actionMode == null) {
            return;
        }
        actionMode.setVisibility(VISIBLE);
        if (backButtonFrameLayout != null) {
            backButtonFrameLayout.setVisibility(INVISIBLE);
        }
        if (menu != null) {
            menu.setVisibility(INVISIBLE);
        }
    }

    public void hideActionMode() {
        if (actionMode == null) {
            return;
        }
        actionMode.setVisibility(GONE);
        if (backButtonFrameLayout != null) {
            backButtonFrameLayout.setVisibility(isSearchFieldVisible || actionOverlay == null || actionOverlay.getVisibility() == GONE ? VISIBLE : INVISIBLE);
        }
        if (menu != null) {
            menu.setVisibility(VISIBLE);
        }
    }

    public boolean isActionModeShowed() {
        return actionMode != null && actionMode.getVisibility() == VISIBLE;
    }

    protected void onSearchFieldVisibilityChanged(boolean visible) {
        isSearchFieldVisible = visible;
        if (titleTextView != null) {
            titleTextView.setVisibility(visible ? GONE : VISIBLE);
        }
        if (subTitleTextView != null) {
            subTitleTextView.setVisibility(visible ? GONE : VISIBLE);
        }
        backButtonFrameLayout.setPadding(0, 0, visible ? 0 : AndroidUtilities.dp(4), 0);
        if (visible) {
            oldUseLogo = logoImageView != null && logoImageView.getVisibility() == VISIBLE;
            setDisplayUseLogoEnabled(true, R.drawable.ic_ab_search);
        } else {
            setDisplayUseLogoEnabled(oldUseLogo, logoResourceId);
        }
        if (visible) {
            oldUseBack = backButtonImageView != null && backButtonImageView.getVisibility() == VISIBLE;
            setDisplayHomeAsUpEnabled(true, R.drawable.ic_ab_back);
        } else {
            setDisplayHomeAsUpEnabled(oldUseBack, backResourceId);
        }
        positionBackOverlay(getMeasuredWidth(), getMeasuredHeight());
    }

    public void closeSearchField() {
        if (!isSearchFieldVisible || menu == null) {
            return;
        }
        menu.closeSearchField();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        positionLogoImage(MeasureSpec.getSize(heightMeasureSpec));
        positionBackImage(MeasureSpec.getSize(heightMeasureSpec));
        positionMenu(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        positionTitle(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        positionBackOverlay(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setAlpha(float alpha) {
        if (menu != null) {
            menu.setAlpha(alpha);
        }
        if (backButtonFrameLayout != null) {
            backButtonFrameLayout.setAlpha(alpha);
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

    public void setBackOverlay(int resourceId) {
        LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        actionOverlay = li.inflate(resourceId, null);
        addView(actionOverlay);
        actionOverlay.setVisibility(GONE);
        actionOverlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (actionBarMenuOnItemClick != null) {
                    actionBarMenuOnItemClick.onItemClick(-1);
                }
            }
        });
    }

    public void setBackOverlayVisible(boolean visible) {
        if (actionOverlay == null || parentFragment == null || parentFragment.parentLayout == null) {
            return;
        }
        isBackOverlayVisible = visible;
        if (visible) {
            parentFragment.parentLayout.onOverlayShow(actionOverlay, parentFragment);
        }
        positionBackOverlay(getMeasuredWidth(), getMeasuredHeight());
    }

    private void positionBackOverlay(int widthMeasureSpec, int heightMeasureSpec) {
        if (actionOverlay == null) {
            return;
        }
        backButtonFrameLayout.setVisibility(isSearchFieldVisible || actionOverlay == null || actionOverlay.getVisibility() == GONE ? VISIBLE : INVISIBLE);
        actionOverlay.setVisibility(!isSearchFieldVisible && isBackOverlayVisible ? VISIBLE : GONE);
        if (actionOverlay.getVisibility() == VISIBLE) {
            ViewGroup.LayoutParams layoutParams = actionOverlay.getLayoutParams();
            layoutParams.width = LayoutParams.WRAP_CONTENT;
            layoutParams.height = LayoutParams.MATCH_PARENT;
            actionOverlay.setLayoutParams(layoutParams);
            actionOverlay.measure(widthMeasureSpec, heightMeasureSpec);
            layoutParams.width = Math.min(actionOverlay.getMeasuredWidth() + AndroidUtilities.dp(4), widthMeasureSpec - (menu != null ? menu.getMeasuredWidth() : 0));
            actionOverlay.setLayoutParams(layoutParams);
        }
    }

    public void setItemsBackground(int resourceId) {
        itemsBackgroundResourceId = resourceId;
        backButtonFrameLayout.setBackgroundResource(itemsBackgroundResourceId);
    }
}