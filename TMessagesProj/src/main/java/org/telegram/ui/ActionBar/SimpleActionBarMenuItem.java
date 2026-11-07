package org.telegram.ui.ActionBar;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.EditTextBoldCursor;

/**
 * Menu item of {@link SimpleActionBar}. Popup sub items work as in {@link ActionBarMenuItem};
 * search related calls are routed to the search field of the parent bar.
 */
public class SimpleActionBarMenuItem extends ActionBarMenuItem {
    private final SimpleActionBarMenu parentMenu;

    private boolean isSearchField;
    private ActionBarMenuItemSearchListener searchListener;

    SimpleActionBarMenuItem(Context context, SimpleActionBarMenu menu, int backgroundColor, int iconColor, boolean text, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, backgroundColor, iconColor, text, resourcesProvider);
        parentMenu = menu;
        setDelegate(menu::onItemClick);
    }

    public SimpleActionBarMenu getParentMenu() {
        return parentMenu;
    }

    /* Shown state */

    private boolean shown = true;
    private boolean enabledBeforeHide = true;

    /**
     * Animated replacement of manual alpha/scale animations: the menu fades the item in place,
     * slides the other items and resizes its glass pill. A hidden item becomes {@link View#GONE}
     * when the animation ends.
     */
    public void setShown(boolean shown, boolean animated) {
        if (this.shown == shown) {
            if (!shown && !animated && getVisibility() != GONE) {
                // finishes a running hide
                super.setVisibility(GONE);
                parentMenu.onItemShownChanged(false);
            } else if (shown && getVisibility() != VISIBLE) {
                super.setVisibility(VISIBLE);
            }
            return;
        }
        this.shown = shown;
        // a fading out item takes no clicks; the screen's own enabled state comes back with it
        if (shown) {
            setEnabled(enabledBeforeHide);
        } else {
            enabledBeforeHide = isEnabled();
            setEnabled(false);
        }
        if (shown) {
            super.setVisibility(VISIBLE);
        } else if (!animated || !isLaidOut() || !parentMenu.isLaidOut()) {
            super.setVisibility(GONE);
        }
        parentMenu.onItemShownChanged(animated);
    }

    public boolean isItemShown() {
        return shown;
    }

    /** Screens toggle the buttons of the action mode with visibility: animated while the menu is on screen. */
    @Override
    public void setVisibility(int visibility) {
        if (parentMenu == null || visibility == INVISIBLE) {
            super.setVisibility(visibility);
        } else {
            setShown(visibility == VISIBLE, parentMenu.canAnimateItems());
        }
    }

    /** Hides the item for good once the menu has faded it out. */
    void onHideFinished() {
        if (!shown) {
            super.setVisibility(GONE);
        }
    }

    /* Sub menu: opens in the overlay of the fragment, falls back to the popup window without one */

    @Nullable
    @Override
    SimpleActionBarMenuOverlay getSubMenuOverlay() {
        return parentMenu.parentBar.getMenuOverlay();
    }

    @Nullable
    @Override
    MenuOverlayHost getSubMenuOverlayHost() {
        return parentMenu.parentBar;
    }

    @Override
    boolean canOpenSubMenuOverlay() {
        return !parentMenu.isActionMode || parentMenu.parentBar.isActionModeShowed();
    }

    /* Search */

    @Override
    public ActionBarMenuItem setIsSearchField(boolean value) {
        return setIsSearchField(value, false);
    }

    @Override
    public ActionBarMenuItem setIsSearchField(boolean value, boolean wrapInScrollView) {
        isSearchField = value;
        parentMenu.parentBar.setSearchItem(value ? this : null);
        return this;
    }

    @Override
    public boolean isSearchField() {
        return isSearchField;
    }

    @Override
    public ActionBarMenuItem setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener listener) {
        searchListener = listener;
        return this;
    }

    ActionBarMenuItemSearchListener getSearchListener() {
        return searchListener;
    }

    @Override
    public void setSearchFieldHint(CharSequence hint) {
        parentMenu.parentBar.setSearchFieldHint(hint);
        setContentDescription(hint);
    }

    @Override
    public void setSearchFieldText(CharSequence text, boolean animated) {
        parentMenu.parentBar.setSearchFieldText(text);
    }

    @Override
    public EditTextBoldCursor getSearchField() {
        return parentMenu.parentBar.getSearchField();
    }

    @Override
    public void openSearch(boolean openKeyboard) {
        parentMenu.parentBar.openSearchField(openKeyboard, true);
    }

    @Override
    public boolean toggleSearch(boolean openKeyboard) {
        final SimpleActionBar bar = parentMenu.parentBar;
        if (bar.isSearchFieldVisible()) {
            bar.closeSearchField(openKeyboard);
            return false;
        }
        return bar.openSearchField(openKeyboard, true);
    }

    @Override
    public boolean isSearchFieldVisible() {
        return parentMenu.parentBar.isSearchFieldVisible();
    }

    @Override
    public boolean isSearchFieldVisible2() {
        return parentMenu.parentBar.isSearchFieldVisible();
    }

    @Override
    public void setSearchFieldCaption(CharSequence caption) {
        parentMenu.parentBar.setSearchFieldCaption(caption);
    }

    @Override
    public void clearSearchText() {
        parentMenu.parentBar.setSearchFieldText("");
    }

    @Override
    public void onSearchPressed() {
        if (searchListener != null) {
            searchListener.onSearchPressed(getSearchField());
        }
    }
}
