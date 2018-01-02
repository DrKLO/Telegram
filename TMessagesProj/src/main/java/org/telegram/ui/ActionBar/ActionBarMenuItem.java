/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ActionMode;
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
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.reflect.Method;

public class ActionBarMenuItem extends FrameLayout {

    public static class ActionBarMenuItemSearchListener {
        public void onSearchExpand() {
        }

        public boolean canCollapseSearch() {
            return true;
        }

        public void onSearchCollapse() {

        }

        public void onTextChanged(EditText editText) {
        }

        public void onSearchPressed(EditText editText) {
        }

        public void onCaptionCleared() {
        }
    }

    public interface ActionBarMenuItemDelegate {
        void onItemClick(int id);
    }

    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ActionBarMenu parentMenu;
    private ActionBarPopupWindow popupWindow;
    private EditTextBoldCursor searchField;
    private TextView searchFieldCaption;
    private ImageView clearButton;
    protected ImageView iconView;
    private FrameLayout searchContainer;
    private boolean isSearchField;
    private ActionBarMenuItemSearchListener listener;
    private Rect rect;
    private int[] location;
    private View selectedMenuView;
    private Runnable showMenuRunnable;
    private int menuHeight = AndroidUtilities.dp(16);
    private int subMenuOpenSide;
    private ActionBarMenuItemDelegate delegate;
    private boolean allowCloseAnimation = true;
    protected boolean overrideMenuClick;
    private boolean processedPopupClick;
    private boolean layoutInScreen;
    private boolean animationEnabled = true;
    private static Method layoutInScreenMethod;
    private boolean ignoreOnTextChange;
    private CloseProgressDrawable2 progressDrawable;
    private int additionalOffset;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor) {
        super(context);
        if (backgroundColor != 0) {
            setBackgroundDrawable(Theme.createSelectorDrawable(backgroundColor));
        }
        parentMenu = menu;

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (iconColor != 0) {
            iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        }
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
                for (int a = 0; a < popupLayout.getItemsCount(); a++) {
                    View child = popupLayout.getItemAt(a);
                    child.getHitRect(rect);
                    if ((Integer) child.getTag() < 100) {
                        if (!rect.contains((int) x, (int) y)) {
                            child.setPressed(false);
                            child.setSelected(false);
                            if (Build.VERSION.SDK_INT == 21) {
                                child.getBackground().setVisible(false, false);
                            }
                        } else {
                            child.setPressed(true);
                            child.setSelected(true);
                            if (Build.VERSION.SDK_INT >= 21) {
                                if (Build.VERSION.SDK_INT == 21) {
                                    child.getBackground().setVisible(true, false);
                                }
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
                if (parentMenu != null) {
                    parentMenu.onItemClick((Integer) selectedMenuView.getTag());
                } else if (delegate != null) {
                    delegate.onItemClick((Integer) selectedMenuView.getTag());
                }
                popupWindow.dismiss(allowCloseAnimation);
            } else {
                popupWindow.dismiss();
            }
        } else {
            if (selectedMenuView != null) {
                selectedMenuView.setSelected(false);
                selectedMenuView = null;
            }
        }
        return super.onTouchEvent(event);
    }

    public void setDelegate(ActionBarMenuItemDelegate delegate) {
        this.delegate = delegate;
    }

    public void setIconColor(int color) {
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        if (clearButton != null) {
            clearButton.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setSubMenuOpenSide(int side) {
        subMenuOpenSide = side;
    }

    public void setLayoutInScreen(boolean value) {
        layoutInScreen = value;
    }

    private void createPopupLayout() {
        if (popupLayout != null) {
            return;
        }
        rect = new Rect();
        location = new int[2];
        popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
        popupLayout.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (popupWindow != null && popupWindow.isShowing()) {
                        v.getHitRect(rect);
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
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

    public void addSubItem(View view, int width, int height) {
        createPopupLayout();
        popupLayout.addView(view, new LinearLayout.LayoutParams(width, height));
    }

    public TextView addSubItem(int id, String text) {
        createPopupLayout();
        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        if (!LocaleController.isRTL) {
            textView.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        }
        textView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setMinWidth(AndroidUtilities.dp(196));
        textView.setTag(id);
        textView.setText(text);
        popupLayout.addView(textView);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) textView.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        textView.setLayoutParams(layoutParams);

        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (popupWindow != null && popupWindow.isShowing()) {
                    if (processedPopupClick) {
                        return;
                    }
                    processedPopupClick = true;
                    popupWindow.dismiss(allowCloseAnimation);
                }
                if (parentMenu != null) {
                    parentMenu.onItemClick((Integer) view.getTag());
                } else if (delegate != null) {
                    delegate.onItemClick((Integer) view.getTag());
                }
            }
        });
        menuHeight += layoutParams.height;
        return textView;
    }

    public void redrawPopup(int color) {
        if (popupLayout != null) {
            popupLayout.backgroundDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            popupLayout.invalidate();
        }
    }

    public void setPopupItemsColor(int color) {
        if (popupLayout == null) {
            return;
        }
        int count = popupLayout.linearLayout.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = popupLayout.linearLayout.getChildAt(a);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            }
        }
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
            popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            if (animationEnabled && Build.VERSION.SDK_INT >= 19) {
                popupWindow.setAnimationStyle(0);
            } else {
                popupWindow.setAnimationStyle(R.style.PopupAnimation);
            }
            if (!animationEnabled) {
                popupWindow.setAnimationEnabled(animationEnabled);
            }
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            if (layoutInScreen) {
                try {
                    if (layoutInScreenMethod == null) {
                        layoutInScreenMethod = PopupWindow.class.getDeclaredMethod("setLayoutInScreenEnabled", boolean.class);
                        layoutInScreenMethod.setAccessible(true);
                    }
                    layoutInScreenMethod.invoke(popupWindow, true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST));
            popupWindow.getContentView().setFocusableInTouchMode(true);
            popupWindow.getContentView().setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                        return true;
                    }
                    return false;
                }
            });
        }
        processedPopupClick = false;
        popupWindow.setFocusable(true);
        if (popupLayout.getMeasuredWidth() == 0) {
            updateOrShowPopup(true, true);
        } else {
            updateOrShowPopup(true, false);
        }
        popupWindow.startAnimation();
    }

    public void openSearch(boolean openKeyboard) {
        if (searchContainer == null || searchContainer.getVisibility() == VISIBLE || parentMenu == null) {
            return;
        }
        parentMenu.parentActionBar.onSearchFieldVisibilityChanged(toggleSearch(openKeyboard));
    }

    public boolean toggleSearch(boolean openKeyboard) {
        if (searchContainer == null) {
            return false;
        }
        if (searchContainer.getVisibility() == VISIBLE) {
            if (listener == null || listener != null && listener.canCollapseSearch()) {
                searchContainer.setVisibility(GONE);
                searchField.clearFocus();
                setVisibility(VISIBLE);
                if (openKeyboard) {
                    AndroidUtilities.hideKeyboard(searchField);
                }
                if (listener != null) {
                    listener.onSearchCollapse();
                }
            }
            return false;
        } else {
            searchContainer.setVisibility(VISIBLE);
            setVisibility(GONE);
            searchField.setText("");
            searchField.requestFocus();
            if (openKeyboard) {
                AndroidUtilities.showKeyboard(searchField);
            }
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

    public void setIcon(Drawable drawable) {
        iconView.setImageDrawable(drawable);
    }

    public ImageView getImageView() {
        return iconView;
    }

    public EditTextBoldCursor getSearchField() {
        return searchField;
    }

    public ActionBarMenuItem setOverrideMenuClick(boolean value) {
        overrideMenuClick = value;
        return this;
    }

    public ActionBarMenuItem setIsSearchField(boolean value) {
        if (parentMenu == null) {
            return this;
        }
        if (value && searchContainer == null) {
            searchContainer = new FrameLayout(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    measureChildWithMargins(clearButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    int width;
                    if (searchFieldCaption.getVisibility() == VISIBLE) {
                        measureChildWithMargins(searchFieldCaption, widthMeasureSpec, MeasureSpec.getSize(widthMeasureSpec) / 2, heightMeasureSpec, 0);
                        width = searchFieldCaption.getMeasuredWidth() + AndroidUtilities.dp(4);
                    } else {
                        width = 0;
                    }
                    measureChildWithMargins(searchField, widthMeasureSpec, width, heightMeasureSpec, 0);
                    int w = MeasureSpec.getSize(widthMeasureSpec);
                    int h = MeasureSpec.getSize(heightMeasureSpec);
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    int x;
                    if (LocaleController.isRTL) {
                        x = 0;
                    } else {
                        if (searchFieldCaption.getVisibility() == VISIBLE) {
                            x = searchFieldCaption.getMeasuredWidth() + AndroidUtilities.dp(4);
                        } else {
                            x = 0;
                        }
                    }
                    searchField.layout(x, searchField.getTop(), x + searchField.getMeasuredWidth(), searchField.getBottom());
                }
            };
            parentMenu.addView(searchContainer, 0, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 6, 0, 0, 0));
            searchContainer.setVisibility(GONE);

            searchFieldCaption = new TextView(getContext());
            searchFieldCaption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchFieldCaption.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSearch));
            searchFieldCaption.setSingleLine(true);
            searchFieldCaption.setEllipsize(TextUtils.TruncateAt.END);
            searchFieldCaption.setVisibility(GONE);
            searchFieldCaption.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            searchField = new EditTextBoldCursor(getContext()) {
                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && searchField.length() == 0 && searchFieldCaption.getVisibility() == VISIBLE && searchFieldCaption.length() > 0) {
                        clearButton.callOnClick();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }

                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    return super.dispatchKeyEvent(event);
                }
            };
            searchField.setCursorWidth(1.5f);
            searchField.setCursorColor(0xffffffff);
            searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchField.setHintTextColor(Theme.getColor(Theme.key_actionBarDefaultSearchPlaceholder));
            searchField.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSearch));
            searchField.setSingleLine(true);
            searchField.setBackgroundResource(0);
            searchField.setPadding(0, 0, 0, 0);
            int inputType = searchField.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            searchField.setInputType(inputType);
            if (Build.VERSION.SDK_INT < 23) {
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
                    if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
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
                    if (ignoreOnTextChange) {
                        ignoreOnTextChange = false;
                        return;
                    }
                    if (listener != null) {
                        listener.onTextChanged(searchField);
                    }
                    if (clearButton != null) {
                        //clearButton.setAlpha(TextUtils.isEmpty(s) && searchFieldCaption.getVisibility() != VISIBLE ? 0.6f : 1.0f);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
            searchField.setTextIsSelectable(false);
            if (!LocaleController.isRTL) {
                searchContainer.addView(searchFieldCaption, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 5.5f, 0, 0));
                searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
            } else {
                searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
                searchContainer.addView(searchFieldCaption, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 5.5f, 48, 0));
            }

            clearButton = new ImageView(getContext());
            clearButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            clearButton.setColorFilter(new PorterDuffColorFilter(parentMenu.parentActionBar.itemsColor, PorterDuff.Mode.MULTIPLY));
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchField.length() != 0) {
                        searchField.setText("");
                    } else if (searchFieldCaption != null && searchFieldCaption.getVisibility() == VISIBLE) {
                        searchFieldCaption.setVisibility(GONE);
                        //clearButton.setAlpha(searchField.length() == 0 && searchFieldCaption.getVisibility() != VISIBLE ? 0.6f : 1.0f);
                        if (listener != null) {
                            listener.onCaptionCleared();
                        }
                    }
                    searchField.requestFocus();
                    AndroidUtilities.showKeyboard(searchField);
                }
            });
            searchContainer.addView(clearButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        }
        isSearchField = value;
        return this;
    }

    public void setShowSearchProgress(boolean show) {
        if (progressDrawable == null) {
            return;
        }
        if (show) {
            progressDrawable.startAnimation();
        } else {
            progressDrawable.stopAnimation();
        }
    }

    public void setSearchFieldCaption(CharSequence caption) {
        if (TextUtils.isEmpty(caption)) {
            searchFieldCaption.setVisibility(GONE);
        } else {
            searchFieldCaption.setVisibility(VISIBLE);
            searchFieldCaption.setText(caption);
        }
        if (clearButton != null) {
            //clearButton.setAlpha(searchField.length() == 0 && searchFieldCaption.getVisibility() != VISIBLE ? 0.6f : 1.0f);
        }
    }

    public void setIgnoreOnTextChange() {
        ignoreOnTextChange = true;
    }

    public boolean isSearchField() {
        return isSearchField;
    }

    public void clearSearchText() {
        if (searchField == null) {
            return;
        }
        searchField.setText("");
    }

    public ActionBarMenuItem setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener listener) {
        this.listener = listener;
        return this;
    }

    public ActionBarMenuItem setAllowCloseAnimation(boolean value) {
        allowCloseAnimation = value;
        return this;
    }

    public void setPopupAnimationEnabled(boolean value) {
        if (popupWindow != null) {
            popupWindow.setAnimationEnabled(value);
        }
        animationEnabled = value;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (popupWindow != null && popupWindow.isShowing()) {
            updateOrShowPopup(false, true);
        }
    }

    public void setAdditionalOffset(int value) {
        additionalOffset = value;
    }

    private void updateOrShowPopup(boolean show, boolean update) {
        int offsetY;

        if (parentMenu != null) {
            offsetY = -parentMenu.parentActionBar.getMeasuredHeight() + parentMenu.getTop();
        } else {
            float scaleY = getScaleY();
            offsetY = -(int) (getMeasuredHeight() * scaleY - getTranslationY() / scaleY) + additionalOffset;
        }

        if (show) {
            popupLayout.scrollToTop();
        }

        if (parentMenu != null) {
            View parent = parentMenu.parentActionBar;
            if (subMenuOpenSide == 0) {
                if (show) {
                    popupWindow.showAsDropDown(parent, getLeft() + parentMenu.getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth() + (int) getTranslationX(), offsetY);
                }
                if (update) {
                    popupWindow.update(parent, getLeft() + parentMenu.getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth() + (int) getTranslationX(), offsetY, -1, -1);
                }
            } else {
                if (show) {
                    popupWindow.showAsDropDown(parent, getLeft() - AndroidUtilities.dp(8) + (int) getTranslationX(), offsetY);
                }
                if (update) {
                    popupWindow.update(parent, getLeft() - AndroidUtilities.dp(8) + (int) getTranslationX(), offsetY, -1, -1);
                }
            }
        } else {
            if (subMenuOpenSide == 0) {
                if (getParent() != null) {
                    View parent = (View) getParent();
                    if (show) {
                        popupWindow.showAsDropDown(parent, getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth(), offsetY);
                    }
                    if (update) {
                        popupWindow.update(parent, getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth(), offsetY, -1, -1);
                    }
                }
            } else {
                if (show) {
                    popupWindow.showAsDropDown(this, -AndroidUtilities.dp(8), offsetY);
                }
                if (update) {
                    popupWindow.update(this, -AndroidUtilities.dp(8), offsetY, -1, -1);
                }
            }
        }
    }

    public void hideSubItem(int id) {
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setVisibility(GONE);
        }
    }

    public void showSubItem(int id) {
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setVisibility(VISIBLE);
        }
    }
}
