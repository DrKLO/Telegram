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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;

public class ActionBarMenu extends LinearLayout {

    public boolean drawBlur = true;
    protected ActionBar parentActionBar;
    protected boolean isActionMode;

    private ArrayList<Integer> ids;

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

    public ActionBarMenuItem addItemWithWidth(int id, Drawable drawable, int width, CharSequence title) {
        return addItem(id, 0, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, drawable, width, title);
    }

    public ActionBarMenuItem addItemWithWidth(int id, int icon, int width, CharSequence title) {
        return addItem(id, icon, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null, width, title);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title) {
        return addItem(id, icon, text, backgroundColor, drawable, width, title, null);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
        if (ids == null) {
            ids = new ArrayList<>();
        }
        ids.add(id);
        return addItemAt(-1, id, icon, text, backgroundColor, drawable, width, title, resourcesProvider);
    }

    protected ActionBarMenuItem addItemAt(int index, int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
        ActionBarMenuItem menuItem = new ActionBarMenuItem(getContext(), this, backgroundColor, isActionMode ? parentActionBar.itemsActionModeColor : parentActionBar.itemsColor, text != null, resourcesProvider);
        menuItem.setTag(id);
        if (text != null) {
            menuItem.textView.setText(text);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width != 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.leftMargin = layoutParams.rightMargin = AndroidUtilities.dp(14);
            addView(menuItem, index, layoutParams);
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
            addView(menuItem, index, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
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

    public LazyItem lazilyAddItem(int id, int icon, Theme.ResourcesProvider resourcesProvider) {
        return lazilyAddItem(id, icon, null, isActionMode ? parentActionBar.itemsActionModeBackgroundColor : parentActionBar.itemsBackgroundColor, null, AndroidUtilities.dp(48), null, resourcesProvider);
    }

    public LazyItem lazilyAddItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
        if (ids == null) {
            ids = new ArrayList<>();
        }
        ids.add(id);
        return new LazyItem(this, id, icon, text, backgroundColor, drawable, width, title, resourcesProvider);
    }

    public static class LazyItem {
        ActionBarMenu parent;

        int id;
        int icon;
        CharSequence text;
        CharSequence contentDescription;
        int backgroundColor;
        Drawable drawable;
        int width;
        CharSequence title;
        Theme.ResourcesProvider resourcesProvider;

        float alpha = 1;
        Boolean overrideMenuClick;
        Boolean allowCloseAnimation;
        Boolean isSearchField;
        ActionBarMenuItem.ActionBarMenuItemSearchListener searchListener;
        CharSequence searchFieldHint;

        public LazyItem(ActionBarMenu parent, int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title, Theme.ResourcesProvider resourcesProvider) {
            this.parent = parent;
            this.id = id;
            this.icon = icon;
            this.text = text;
            this.backgroundColor = backgroundColor;
            this.drawable = drawable;
            this.width = width;
            this.title = title;
            this.resourcesProvider = resourcesProvider;
        }

        int visibility = GONE;
        ActionBarMenuItem cell;

        public void setVisibility(int visibility) {
            if (this.visibility != visibility) {
                this.visibility = visibility;
                if (visibility == VISIBLE) {
                    add();
                }
                if (cell != null) {
                    cell.setVisibility(visibility);
                }
            }
        }

        public int getVisibility() {
            return visibility;
        }

        Object tag;
        public Object getTag() {
            return tag;
        }
        public void setTag(Object tag) {
            this.tag = tag;
        }

        @Nullable
        public ActionBarMenuItem getView() {
            return cell;
        }

        public ActionBarMenuItem createView() {
            add();
            return cell;
        }

        public void setContentDescription(CharSequence contentDescription) {
            this.contentDescription = contentDescription;
            if (cell != null) {
                cell.setContentDescription(contentDescription);
            }
        }

        public void setOverrideMenuClick(boolean value) {
            overrideMenuClick = value;
            if (cell != null) {
                cell.setOverrideMenuClick(value);
            }
        }

        public void setAllowCloseAnimation(boolean value) {
            allowCloseAnimation = value;
            if (cell != null) {
                cell.setAllowCloseAnimation(allowCloseAnimation);
            }
        }

        public void setIsSearchField(boolean value) {
            isSearchField = value;
            if (cell != null) {
                cell.setIsSearchField(isSearchField);
            }
        }

        public void setActionBarMenuItemSearchListener(ActionBarMenuItem.ActionBarMenuItemSearchListener listener) {
            this.searchListener = listener;
            if (cell != null) {
                cell.setActionBarMenuItemSearchListener(listener);
            }
        }

        public void setSearchFieldHint(CharSequence searchFieldHint) {
            this.searchFieldHint = searchFieldHint;
            if (cell != null) {
                cell.setSearchFieldHint(searchFieldHint);
            }
        }

        public void setAlpha(float alpha) {
            this.alpha = alpha;
            if (cell != null) {
                cell.setAlpha(alpha);
            }
        }

        public void add() {
            if (cell != null) {
                return;
            }

            int index = parent.getChildCount();
            if (parent.ids != null) {
                int myIndex = parent.ids.indexOf(this.id);
                for (int i = 0; i < parent.getChildCount(); ++i) {
                    View child = parent.getChildAt(i);
                    Object tag = child.getTag();
                    if (tag instanceof Integer) {
                        int thisId = (Integer) tag;
                        int thisIndex = parent.ids.indexOf(thisId);
                        if (thisIndex > myIndex) {
                            index = i;
                            break;
                        }
                    }
                }
            }
            cell = parent.addItemAt(index, id, icon, text, backgroundColor, drawable, width, title, resourcesProvider);
            cell.setVisibility(visibility);
            if (contentDescription != null) {
                cell.setContentDescription(contentDescription);
            }
            if (allowCloseAnimation != null) {
                cell.setAllowCloseAnimation(allowCloseAnimation);
            }
            if (overrideMenuClick != null) {
                cell.setOverrideMenuClick(overrideMenuClick);
            }
            if (isSearchField != null) {
                cell.setIsSearchField(isSearchField);
            }
            if (searchListener != null) {
                cell.setActionBarMenuItemSearchListener(searchListener);
            }
            if (searchFieldHint != null) {
                cell.setSearchFieldHint(searchFieldHint);
            }
            cell.setAlpha(alpha);
        }
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
        if (ids != null) {
            ids.clear();
        }
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

    public void setSearchCursorColor(int color) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    item.getSearchField().setCursorColor(color);
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

    public void openSearchField(boolean toggle, boolean showKeyboard, String text, boolean animated) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField()) {
                    if (toggle) {
                        parentActionBar.onSearchFieldVisibilityChanged(item.toggleSearch(showKeyboard));
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

    public void translateXItems(float offset) {
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

    private Runnable onLayoutListener;
    public void setOnLayoutListener(Runnable listener) {
        this.onLayoutListener = listener;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (onLayoutListener != null) {
            onLayoutListener.run();
        }
    }
}
