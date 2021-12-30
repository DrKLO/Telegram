/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.RLottieDrawable;

public class ActionBarMenu extends LinearLayout {

    protected ActionBar parentActionBar;
    protected boolean isActionMode;

    public ActionBarMenu(Context context, ActionBar layer) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        parentActionBar = layer;
    }

    public ActionBarMenu(Context context) {
        super(context);
    }

    protected void updateItemsBackgroundColor() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                view.setBackgroundDrawable(Theme.createSelectorDrawable(isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor));
            }
        }
    }

    protected void updateItemsColor() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setIconColor(isActionMode ? parentActionBar.itemsActionModeColor : parentActionBar.itemsColor);
            }
        }
    }

    public ActionBarMenuItem addItem(int id, Drawable drawable) {
        return addItem(id, 0, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, drawable, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItem(int id, int icon) {
        return addItem(id, icon, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null);
    }

    public ActionBarMenuItem addItem(int id, int icon, Theme.ResourcesProvider resourcesProvider) {
        return addItem(id, icon, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, resourcesProvider);
    }

    public ActionBarMenuItem addItem(int id, CharSequence text) {
        return addItem(id, 0, text, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null, 0, text);
    }

    public ActionBarMenuItem addItem(int id, int icon, int backgroundColor) {
        return addItem(id, icon, backgroundColor, null);
    }

    public ActionBarMenuItem addItem(int id, int icon, int backgroundColor, Theme.ResourcesProvider resourcesProvider) {
        return addItem(id, icon, null, backgroundColor, null, AndroidUtilities.dp(48), null, resourcesProvider);
    }

    public ActionBarMenuItem addItemWithWidth(int id, int icon, int width) {
        return addItem(id, icon, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null, width, null);
    }

    public ActionBarMenuItem addItemWithWidth(int id, int icon, int width, CharSequence title) {
        return addItem(id, icon, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null, width, title);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title) {
        return addItem(id, icon, text, backgroundColor, drawable, width, title, null);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
        ActionBarMenuItem menuItem = new ActionBarMenuItem(getContext(), this, backgroundColor, isActionMode ? parentActionBar.itemsActionModeColor : parentActionBar.itemsColor, text != null, resourcesProvider);
        menuItem.setTag(id);
        if (text != null) {
            menuItem.textView.setText(text);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width != 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.leftMargin = layoutParams.rightMargin = AndroidUtilities.dp(14);
            addView(menuItem, layoutParams);
        } else {
            if (drawable != null) {
                if (drawable instanceof RLottieDrawable) {
                    menuItem.iconView.setAnimation((RLottieDrawable) drawable);
                } else {
                    menuItem.iconView.setImageDrawable(drawable);
                }
            } else if (icon != 0) {
                menuItem.iconView.setImageResource(icon);
            }
            addView(menuItem, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        menuItem.setOnClickListener(view -> {
            ActionBarMenuItem item = (ActionBarMenuItem) view;
            if (item.hasSubMenu()) {
                if (parentActionBar.actionBarMenuOnItemClick.canOpenMenu()) {
                    item.toggleSubMenu();
                }
            } else if (item.isSearchField()) {
                parentActionBar.onSearchFieldVisibilityChanged(item.toggleSearch(true));
            } else {
                onItemClick((Integer) view.getTag());
            }
        });
        if (title != null) {
            menuItem.setContentDescription(title);
        }
        return menuItem;
    }

    public void hideAllPopupMenus() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).closeSubMenu();
            }
        }
    }

    protected void setPopupItemsColor(int color, boolean icon) {
        for (int a = 0, count = getChildCount(); a < count; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setPopupItemsColor(color, icon);
            }
        }
    }

    protected void setPopupItemsSelectorColor(int color) {
        for (int a = 0, count = getChildCount(); a < count; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setPopupItemsSelectorColor(color);
            }
        }
    }

    protected void redrawPopup(int color) {
        for (int a = 0, count = getChildCount(); a < count; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).redrawPopup(color);
            }
        }
    }

    public void onItemClick(int id) {
        if (parentActionBar.actionBarMenuOnItemClick != null) {
            parentActionBar.actionBarMenuOnItemClick.onItemClick(id);
        }
    }

    public void clearItems() {
        removeAllViews();
    }

    public void onMenuButtonPressed() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.getVisibility() != VISIBLE) {
                    continue;
                }
                if (item.hasSubMenu()) {
                    item.toggleSubMenu();
                    break;
                } else if (item.overrideMenuClick) {
                    onItemClick((Integer) item.getTag());
                    break;
                }
            }
        }
    }

    public void closeSearchField(boolean closeKeyboard) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField() && item.isSearchFieldVisible()) {
                    if (item.listener == null || item.listener.canCollapseSearch()) {
                        parentActionBar.onSearchFieldVisibilityChanged(false);
                        item.toggleSearch(closeKeyboard);
                    }
                    break;
                }
            }
        }
    }

    public void setSearchTextColor(int color, boolean placeholder) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    if (placeholder) {
                        item.getSearchField().setHintTextColor(color);
                    } else {
                        item.getSearchField().setTextColor(color);
                    }
                    break;
                }
            }
        }
    }

    public void setSearchFieldText(String text) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    item.setSearchFieldText(text, false);
                    item.getSearchField().setSelection(text.length());
                }
            }
        }
    }

    public void onSearchPressed() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    item.onSearchPressed();
                }
            }
        }
    }

    public void openSearchField(boolean toggle, String text, boolean animated) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    if (toggle) {
                        parentActionBar.onSearchFieldVisibilityChanged(item.toggleSearch(true));
                    }
                    item.setSearchFieldText(text, animated);
                    item.getSearchField().setSelection(text.length());
                    break;
                }
            }
        }
    }

    public void setFilter(FiltersView.MediaFilterData filter) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    item.addSearchFilter(filter);
                    break;
                }
            }
        }
    }

    public ActionBarMenuItem getItem(int id) {
        View v = findViewWithTag(id);
        if (v instanceof ActionBarMenuItem) {
            return (ActionBarMenuItem) v;
        }
        return null;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            view.setEnabled(enabled);
        }
    }

    public int getItemsMeasuredWidth() {
        int w = 0;
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                w += view.getMeasuredWidth();
            }
        }
        return w;
    }

    public int getVisibleItemsMeasuredWidth() {
        int w = 0;
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem && view.getVisibility() != View.GONE) {
                w += view.getMeasuredWidth();
            }
        }
        return w;
    }

    public boolean searchFieldVisible() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem && ((ActionBarMenuItem) view).getSearchContainer() != null && ((ActionBarMenuItem) view).getSearchContainer().getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
    }

    public void translateXItems(int offset) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setTransitionOffset(offset);
            }
        }
    }

    public void clearSearchFilters() {

    }
}
