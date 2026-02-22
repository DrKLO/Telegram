package org.telegram.messenger.utils;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

import org.telegram.ui.ActionBar.ActionBarMenuItem;

public class SearchTextWatcher implements TextWatcher {
    public final ActionBarMenuItem.ActionBarMenuItemSearchListener listener;
    private final EditText editText;
    private final boolean toggleByFocus;

    public SearchTextWatcher(EditText editText, ActionBarMenuItem.ActionBarMenuItemSearchListener listener) {
        this(editText, listener, false);
    }

    public SearchTextWatcher(EditText editText, ActionBarMenuItem.ActionBarMenuItemSearchListener listener, boolean toggleByFocus) {
        this.listener = listener;
        this.editText = editText;
        this.toggleByFocus = toggleByFocus;
    }

    private String searchQuery;
    private boolean searchIsExpanded;
    private boolean doNotCloseAfterFieldEmpty;

    public void setDoNotCloseAfterFieldEmpty() {
        this.doNotCloseAfterFieldEmpty = true;
    }

    @Override
    public void afterTextChanged(Editable s) {
        String text = s.toString();
        boolean oldQueryIsEmpty = TextUtils.isEmpty(searchQuery);
        boolean newQueryIsEmpty = TextUtils.isEmpty(text);
        if (oldQueryIsEmpty && !newQueryIsEmpty) {
            toggleSearch(true);
        }
        searchQuery = text;
        listener.onTextChanged(editText);
        if (!oldQueryIsEmpty && newQueryIsEmpty && !doNotCloseAfterFieldEmpty) {
            toggleSearch(false);
        }
    }

    public boolean isSearchExpanded() {
        return searchIsExpanded;
    }

    public boolean toggleSearch(boolean expand) {
        if (searchIsExpanded == expand) {
            return false;
        }

        listener.onPreToggleSearch();
        if (!listener.canToggleSearch()) {
            return false;
        }

        if (expand) {
            listener.onSearchExpand();
        } else {
            listener.onSearchCollapse();
        }

        searchIsExpanded = expand;
        return true;
    }



    /* * */

    @Override
    public final void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public final void onTextChanged(CharSequence s, int start, int before, int count) {

    }
}
