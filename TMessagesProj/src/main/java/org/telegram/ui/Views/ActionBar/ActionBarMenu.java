/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.Utilities;

public class ActionBarMenu extends LinearLayout {

    private ActionBar parentActionBar;
    private ActionBarLayer parentActionBarLayer;

    public ActionBarMenu(Context context, ActionBar actionBar, ActionBarLayer layer) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        parentActionBar = actionBar;
        parentActionBarLayer = layer;
    }

    public ActionBarMenu(Context context) {
        super(context);
    }

    public ActionBarMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionBarMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public View addItemResource(int id, int resourceId) {
        LayoutInflater li = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = li.inflate(resourceId, null);
        view.setTag(id);
        addView(view);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)view.getLayoutParams();
        layoutParams.height = FrameLayout.LayoutParams.FILL_PARENT;
        view.setBackgroundResource(parentActionBar.itemsBackgroundResourceId);
        view.setLayoutParams(layoutParams);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onItemClick((Integer)view.getTag());
            }
        });
        return view;
    }

    public ActionBarMenuItem addItem(int id, int icon) {
        ActionBarMenuItem menuItem = new ActionBarMenuItem(getContext(), this, parentActionBar);
        menuItem.setTag(id);
        menuItem.setScaleType(ImageView.ScaleType.CENTER);
        menuItem.setImageResource(icon);
        addView(menuItem);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)menuItem.getLayoutParams();
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.width = Utilities.dp(56);
        menuItem.setLayoutParams(layoutParams);
        menuItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ActionBarMenuItem item = (ActionBarMenuItem)view;
                if (item.hasSubMenu()) {
                    item.toggleSubMenu();
                } else if (item.isSearchField()) {
                    parentActionBarLayer.onSearchFieldVisibilityChanged(item.toggleSearch());
                } else {
                    onItemClick((Integer)view.getTag());
                }
            }
        });
        return menuItem;
    }

    public void hideAllPopupMenus() {
        for (int a = 0; a < getChildCount(); a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem)view).closeSubMenu();
            }
        }
    }

    public void onItemClick(int id) {
        if (parentActionBarLayer.actionBarMenuOnItemClick != null) {
            parentActionBarLayer.actionBarMenuOnItemClick.onItemClick(id);
        }
    }

    public void clearItems() {
        for (int a = 0; a < getChildCount(); a++) {
            View view = getChildAt(a);
            removeView(view);
        }
    }

    public void onMenuButtonPressed() {
        for (int a = 0; a < getChildCount(); a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem)view;
                if (item.hasSubMenu()) {
                    item.toggleSubMenu();
                }
            }
        }
    }

    public void closeSearchField() {
        for (int a = 0; a < getChildCount(); a++) {
            View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem)view;
                if (item.isSearchField()) {
                    parentActionBarLayer.onSearchFieldVisibilityChanged(item.toggleSearch());
                }
            }
        }
    }

    public ActionBarMenuItem getItem(int id) {
        View v = findViewWithTag(id);
        if (v instanceof ActionBarMenuItem) {
            return (ActionBarMenuItem)v;
        }
        return null;
    }
}
