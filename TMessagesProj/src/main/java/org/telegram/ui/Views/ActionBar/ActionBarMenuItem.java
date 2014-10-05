/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;

import java.lang.reflect.Field;

public class ActionBarMenuItem extends ImageView {

    public static interface ActionBarMenuItemSearchListener {
        public abstract void onSearchExpand();
        public abstract void onSearchCollapse();
        public abstract void onTextChanged(EditText editText);
    }

    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ActionBarMenu parentMenu;
    private ActionBarPopupWindow popupWindow;
    private ActionBar parentActionBar;
    private EditText searchField;
    private boolean isSearchField = false;
    private ActionBarMenuItemSearchListener listener;
    private Rect rect = null;
    private int[] location = null;
    private View selectedMenuView = null;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, ActionBar actionBar, int background) {
        super(context);
        setBackgroundResource(background);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (hasSubMenu() && (popupWindow == null || popupWindow != null && !popupWindow.isShowing())) {
                if (event.getY() > getHeight()) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    toggleSubMenu();
                    return true;
                }
            } else if (popupWindow != null && popupWindow.isShowing()) {
                getLocationOnScreen(location);
                float x = event.getX() + location[0];
                float y = event.getY() + location[1];
                popupLayout.getLocationOnScreen(location);
                x -= location[0];
                y -= location[1];
                selectedMenuView = null;
                for (int a = 0; a < popupLayout.getChildCount(); a++) {
                    View child = popupLayout.getChildAt(a);
                    child.getHitRect(rect);
                    if ((Integer)child.getTag() < 100) {
                        if (!rect.contains((int)x, (int)y)) {
                            child.setSelected(false);
                        } else {
                            child.setSelected(true);
                            selectedMenuView = child;
                        }
                    }
                }
            }
        } else if (popupWindow != null && popupWindow.isShowing() && event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (selectedMenuView != null) {
                selectedMenuView.setSelected(false);
                parentMenu.onItemClick((Integer) selectedMenuView.getTag());
            }
            popupWindow.dismiss();
        } else {
            if (selectedMenuView != null) {
                selectedMenuView.setSelected(false);
                selectedMenuView = null;
            }
        }
        return super.onTouchEvent(event);
    }

    public void addSubItem(int id, String text, int icon) {
        if (popupLayout == null) {
            rect = new Rect();
            location = new int[2];
            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
            popupLayout.setOrientation(LinearLayout.VERTICAL);
            popupLayout.setBackgroundResource(R.drawable.popup_fixed);
            popupLayout.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (popupWindow != null && popupWindow.isShowing()) {
                            v.getHitRect(rect);
                            if (!rect.contains((int)event.getX(), (int)event.getY())) {
                                popupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            popupLayout.setDispatchKeyEventListener(new ActionBarPopupWindow.OnDispatchKeyEventListener() {
                @Override
                public void onDispatchKeyEvent(KeyEvent keyEvent) {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                    }
                }
            });
        }
        if (popupLayout.getChildCount() != 0) {
            View delimeter = new View(getContext());
            delimeter.setBackgroundColor(0xffdcdcdc);
            popupLayout.addView(delimeter);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)delimeter.getLayoutParams();
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = AndroidUtilities.density >= 3 ? 2 : 1;
            delimeter.setLayoutParams(layoutParams);
            delimeter.setTag(100 + id);
        }
        TextView textView = new TextView(getContext());
        textView.setTextColor(0xff000000);
        textView.setBackgroundResource(R.drawable.list_selector);
        if (!LocaleController.isRTL) {
            textView.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        }
        textView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        textView.setTextSize(18);
        textView.setMinWidth(AndroidUtilities.dp(196));
        textView.setTag(id);
        textView.setText(text);
        if (icon != 0) {
            textView.setCompoundDrawablePadding(AndroidUtilities.dp(12));
            if (!LocaleController.isRTL) {
                textView.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(icon), null, null, null);
            } else {
                textView.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(icon), null);
            }
        }
        popupLayout.addView(textView);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)textView.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = AndroidUtilities.dp(48);
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
            //popupWindow.setBackgroundDrawable(new BitmapDrawable());
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST));
            popupWindow.getContentView().setFocusableInTouchMode(true);
            popupWindow.getContentView().setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode ==  KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                        return true;
                    }
                    return false;
                }
            });
        }
        popupWindow.setFocusable(true);
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
            AndroidUtilities.hideKeyboard(searchField);
            if (listener != null) {
                listener.onSearchCollapse();
            }
            return false;
        } else {
            searchField.setVisibility(VISIBLE);
            setVisibility(GONE);
            searchField.setText("");
            searchField.requestFocus();
            AndroidUtilities.showKeyboard(searchField);
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
            searchField.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
            searchField.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (android.os.Build.VERSION.SDK_INT < 11) {
                searchField.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
                    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                        menu.clear();
                    }
                });
            } else {
                searchField.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public void onDestroyActionMode(ActionMode mode) {
                    }

                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        return false;
                    }
                });
            }
            searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
                        AndroidUtilities.hideKeyboard(searchField);
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
            layoutParams.height = AndroidUtilities.dp(36);
            layoutParams.rightMargin = AndroidUtilities.dp(4);
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
