/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
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
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.ui.Components.FrameLayoutFixed;

import java.lang.reflect.Field;

public class ActionBarMenuItem extends FrameLayoutFixed {

    public static class ActionBarMenuItemSearchListener {
        public void onSearchExpand() { }
        public boolean onSearchCollapse() { return true; }
        public void onTextChanged(EditText editText) { }
        public void onSearchPressed(EditText editText) { }
    }

    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ActionBarMenu parentMenu;
    private ActionBarPopupWindow popupWindow;
    private EditText searchField;
    private ImageView clearButton;
    protected ImageView iconView;
    private FrameLayout searchContainer;
    private boolean isSearchField = false;
    private ActionBarMenuItemSearchListener listener;
    private Rect rect;
    private int[] location;
    private View selectedMenuView;
    private Runnable showMenuRunnable;
    private boolean showFromBottom;
    private int menuHeight = AndroidUtilities.dp(16);
    private boolean needOffset = Build.VERSION.SDK_INT >= 21;
    private int subMenuOpenSide = 0;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int background) {
        super(context);
        setBackgroundResource(background);
        parentMenu = menu;

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconView);
        LayoutParams layoutParams = (LayoutParams) iconView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        iconView.setLayoutParams(layoutParams);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (hasSubMenu() && (popupWindow == null || popupWindow != null && !popupWindow.isShowing())) {
                showMenuRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        toggleSubMenu();
                    }
                };
                AndroidUtilities.runOnUIThread(showMenuRunnable, 200);
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
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
                            child.setPressed(false);
                            child.setSelected(false);
                            if (Build.VERSION.SDK_INT >= 21) {
                                child.getBackground().setVisible(false, false);
                            }
                        } else {
                            child.setPressed(true);
                            child.setSelected(true);
                            if (Build.VERSION.SDK_INT >= 21) {
                                child.getBackground().setVisible(true, false);
                                child.drawableHotspotChanged(x, y - child.getTop());
                            }
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

    public void setShowFromBottom(boolean value) {
        showFromBottom = value;
    }

    public void setNeedOffset(boolean value) {
        needOffset = Build.VERSION.SDK_INT >= 21 && value;
    }

    public void setSubMenuOpenSide(int side) {
        subMenuOpenSide = side;
    }

    public TextView addSubItem(int id, String text, int icon) {
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
        TextView textView = new TextView(getContext());
        textView.setTextColor(0xff212121);
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
        layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
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
        menuHeight += layoutParams.height;
        return textView;
    }

    public boolean hasSubMenu() {
        return popupLayout != null;
    }

    public void toggleSubMenu() {
        if (popupLayout == null) {
            return;
        }
        if (showMenuRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showMenuRunnable);
            showMenuRunnable = null;
        }
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }
        if (popupWindow == null) {
            popupWindow = new ActionBarPopupWindow(popupLayout, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            //popupWindow.setBackgroundDrawable(new BitmapDrawable());
            popupWindow.setAnimationStyle(R.style.PopupAnimation);
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
            if (subMenuOpenSide == 0) {
                if (showFromBottom) {
                    popupWindow.showAsDropDown(this, -popupLayout.getMeasuredWidth() + getMeasuredWidth(), getOffsetY());
                    popupWindow.update(this, -popupLayout.getMeasuredWidth() + getMeasuredWidth(), getOffsetY(), -1, -1);
                } else {
                    popupWindow.showAsDropDown(this, parentMenu.parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), getOffsetY());
                    popupWindow.update(this, parentMenu.parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), getOffsetY(), -1, -1);
                }
            } else {
                popupWindow.showAsDropDown(this, -AndroidUtilities.dp(8), getOffsetY());
                popupWindow.update(this, -AndroidUtilities.dp(8), getOffsetY(), -1, -1);
            }
        } else {
            if (subMenuOpenSide == 0) {
                if (showFromBottom) {
                    popupWindow.showAsDropDown(this, -popupLayout.getMeasuredWidth() + getMeasuredWidth(), getOffsetY());
                } else {
                    popupWindow.showAsDropDown(this, parentMenu.parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), getOffsetY());
                }
            } else {
                popupWindow.showAsDropDown(this, -AndroidUtilities.dp(8), getOffsetY());
            }
        }
    }

    private int getOffsetY() {
        if (showFromBottom) {
            getLocationOnScreen(location);
            int diff = location[1] - AndroidUtilities.statusBarHeight + getMeasuredHeight() - menuHeight;
            int y = -menuHeight;
            if (diff < 0) {
                y -= diff;
            }
            return y - (needOffset ? AndroidUtilities.statusBarHeight : 0);
        } else {
            return -getMeasuredHeight() - (needOffset ? AndroidUtilities.statusBarHeight : 0);
        }
    }

    public void openSearch() {
        if (searchContainer == null || searchContainer.getVisibility() == VISIBLE) {
            return;
        }
        parentMenu.parentActionBar.onSearchFieldVisibilityChanged(toggleSearch());
    }

    public boolean toggleSearch() {
        if (searchContainer == null) {
            return false;
        }
        if (searchContainer.getVisibility() == VISIBLE) {
            if (listener == null || listener != null && listener.onSearchCollapse()) {
                searchContainer.setVisibility(GONE);
                setVisibility(VISIBLE);
                AndroidUtilities.hideKeyboard(searchField);
            }
            return false;
        } else {
            searchContainer.setVisibility(VISIBLE);
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

    public void setIcon(int resId) {
        iconView.setImageResource(resId);
    }

    public EditText getSearchField() {
        return searchField;
    }

    public ActionBarMenuItem setIsSearchField(boolean value) {
        if (value && searchContainer == null) {
            searchContainer = new FrameLayout(getContext());
            parentMenu.addView(searchContainer, 0);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)searchContainer.getLayoutParams();
            layoutParams.weight = 1;
            layoutParams.width = 0;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.leftMargin = AndroidUtilities.dp(6);
            searchContainer.setLayoutParams(layoutParams);
            searchContainer.setVisibility(GONE);

            searchField = new EditText(getContext());
            searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchField.setHintTextColor(0x88ffffff);
            searchField.setTextColor(0xffffffff);
            searchField.setSingleLine(true);
            searchField.setBackgroundResource(0);
            searchField.setPadding(0, 0, 0, 0);
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
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                        AndroidUtilities.hideKeyboard(searchField);
                        if (listener != null) {
                            listener.onSearchPressed(searchField);
                        }
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
                    ViewProxy.setAlpha(clearButton, s == null || s.length() == 0 ? 0.6f : 1.0f);
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

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
            searchContainer.addView(searchField);
            FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) searchField.getLayoutParams();
            layoutParams2.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams2.gravity = Gravity.CENTER_VERTICAL;
            layoutParams2.height = AndroidUtilities.dp(36);
            layoutParams2.rightMargin = AndroidUtilities.dp(48);
            searchField.setLayoutParams(layoutParams2);

            clearButton = new ImageView(getContext());
            clearButton.setImageResource(R.drawable.ic_close_white);
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchField.setText("");
                    AndroidUtilities.showKeyboard(searchField);
                }
            });
            searchContainer.addView(clearButton);
            layoutParams2 = (FrameLayout.LayoutParams) clearButton.getLayoutParams();
            layoutParams2.width = AndroidUtilities.dp(48);
            layoutParams2.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            layoutParams2.height = FrameLayout.LayoutParams.MATCH_PARENT;
            clearButton.setLayoutParams(layoutParams2);
        }
        isSearchField = value;
        return this;
    }

    public boolean isSearchField() {
        return isSearchField;
    }

    public ActionBarMenuItem setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (popupWindow != null && popupWindow.isShowing()) {
            if (subMenuOpenSide == 0) {
                if (showFromBottom) {
                    popupWindow.update(this, -popupLayout.getMeasuredWidth() + getMeasuredWidth(), getOffsetY(), -1, -1);
                } else {
                    popupWindow.update(this, parentMenu.parentActionBar.getMeasuredWidth() - popupLayout.getMeasuredWidth() - getLeft() - parentMenu.getLeft(), getOffsetY(), -1, -1);
                }
            } else {
                popupWindow.update(this, -AndroidUtilities.dp(8), getOffsetY(), -1, -1);
            }
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
