/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.lang.reflect.Field;

public class ActionBarMenuItem extends ImageView {

    public static interface ActionBarMenuItemSearchListener {
        public abstract void onSearchExpand();
        public abstract void onSearchCollapse();
        public abstract void onTextChanged(EditText editText);
    }

    private LinearLayout popupLayout;
    private ActionBarMenu parentMenu;
    private ActionBarPopupWindow popupWindow;
    private ActionBar parentActionBar;
    private EditText searchField;
    private boolean isSearchField = false;
    private ActionBarMenuItemSearchListener listener;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, ActionBar actionBar) {
        super(context);
        setBackgroundResource(actionBar.itemsBackgroundResourceId);
        parentMenu = menu;
        parentActionBar = actionBar;
    }

    public ActionBarMenuItem(Context context) {
        super(context);
    }

    public ActionBarMenuItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ActionBarMenuItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void addSubItem(int id, String text, int icon) {
        if (popupLayout == null) {
            popupLayout = new LinearLayout(getContext());
            popupLayout.setOrientation(LinearLayout.VERTICAL);
            popupLayout.setBackgroundResource(R.drawable.popup_fixed);
        }
        if (popupLayout.getChildCount() != 0) {
            View delimeter = new View(getContext());
            delimeter.setBackgroundColor(0xffdcdcdc);
            popupLayout.addView(delimeter);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)delimeter.getLayoutParams();
            layoutParams.width = Utilities.dp(196);
            layoutParams.height = Utilities.density >= 3 ? 2 : 1;
            delimeter.setLayoutParams(layoutParams);
            delimeter.setTag(100 + id);
        }
        TextView textView = new TextView(getContext());
        textView.setTextColor(0xff000000);
        textView.setBackgroundResource(R.drawable.list_selector);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setPadding(Utilities.dp(16), 0, Utilities.dp(16), 0);
        textView.setTextSize(18);
        textView.setMinWidth(Utilities.dp(196));
        textView.setTag(id);
        textView.setText(text);
        if (icon != 0) {
            textView.setCompoundDrawablePadding(Utilities.dp(12));
            textView.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(icon), null, null, null);
        }
        popupLayout.addView(textView);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)textView.getLayoutParams();
        layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = Utilities.dp(48);
        textView.setLayoutParams(layoutParams);
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                parentMenu.onItemClick((Integer) view.getTag());
                if (popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss();
                }
            }
        });
    }

    public boolean hasSubMenu() {
        return popupLayout != null;
    }

    public void toggleSubMenu() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }
        if (popupWindow == null) {
            popupWindow = new ActionBarPopupWindow(popupLayout, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            popupWindow.setFocusable(true);
            popupWindow.setBackgroundDrawable(new BitmapDrawable());
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        }
        if (popupLayout.getMeasuredWidth() == 0) {
            popupWindow.showAsDropDown(this, parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), 0);
            popupWindow.update(this, parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), 0, -1, -1);
        } else {
            popupWindow.showAsDropDown(this, parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), 0);
        }
    }

    public boolean toggleSearch() {
        if (searchField == null) {
            return false;
        }
        if (searchField.getVisibility() == VISIBLE) {
            searchField.setVisibility(GONE);
            setVisibility(VISIBLE);
            Utilities.hideKeyboard(searchField);
            if (listener != null) {
                listener.onSearchCollapse();
            }
            return false;
        } else {
            searchField.setVisibility(VISIBLE);
            setVisibility(GONE);
            searchField.setText("");
            searchField.requestFocus();
            Utilities.showKeyboard(searchField);
            if (listener != null) {
                listener.onSearchExpand();
            }
            return true;
        }
    }

    public void closeSubMenu() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public EditText getSearchField() {
        return searchField;
    }

    public ActionBarMenuItem setIsSearchField(boolean value) {
        if (value && searchField == null) {
            searchField = new EditText(getContext());
            searchField.setTextSize(18);
            searchField.setTextColor(0xffffffff);
            searchField.setSingleLine(true);
            searchField.setBackgroundResource(R.drawable.search_light_states);
            searchField.setPadding(Utilities.dp(6), 0, Utilities.dp(6), 0);
            searchField.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
                        Utilities.hideKeyboard(searchField);
                    }
                    return false;
                }
            });
            searchField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (listener != null) {
                        listener.onTextChanged(searchField);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            /*
            ImageView img = (ImageView) searchView.findViewById(R.id.search_close_btn);
        if (img != null) {
            img.setImageResource(R.drawable.ic_msg_btn_cross_custom);
        }
             */
            try {
                Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                mCursorDrawableRes.setAccessible(true);
                mCursorDrawableRes.set(searchField, R.drawable.search_carret);
            } catch (Exception e) {
                //nothing to do
            }
            if (Build.VERSION.SDK_INT >= 11) {
                searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
                searchField.setTextIsSelectable(false);
            } else {
                searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            }
            parentMenu.addView(searchField, 0);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)searchField.getLayoutParams();
            layoutParams.weight = 1;
            layoutParams.width = 0;
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            layoutParams.height = Utilities.dp(36);
            layoutParams.rightMargin = Utilities.dp(4);
            searchField.setLayoutParams(layoutParams);
            searchField.setVisibility(GONE);
        }
        isSearchField = value;
        return this;
    }

    public boolean isSearchField() {
        return isSearchField;
    }

    public void setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.update(this, parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), 0, -1, -1);
        }
    }

    public void hideSubItem(int id) {
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setVisibility(GONE);
        }
        view = popupLayout.findViewWithTag(100 + id);
        if (view != null) {
            view.setVisibility(GONE);
        }
    }

    public void showSubItem(int id) {
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setVisibility(VISIBLE);
        }
        view = popupLayout.findViewWithTag(100 + id);
        if (view != null) {
            view.setVisibility(VISIBLE);
        }
    }
}
