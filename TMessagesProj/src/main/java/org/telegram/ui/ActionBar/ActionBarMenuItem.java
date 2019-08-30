/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

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

        public boolean forceShowClear() {
            return false;
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
    protected TextView textView;
    private FrameLayout searchContainer;
    private boolean isSearchField;
    private ActionBarMenuItemSearchListener listener;
    private Rect rect;
    private int[] location;
    private View selectedMenuView;
    private Runnable showMenuRunnable;
    private int subMenuOpenSide;
    private int yOffset;
    private ActionBarMenuItemDelegate delegate;
    private boolean allowCloseAnimation = true;
    protected boolean overrideMenuClick;
    private boolean processedPopupClick;
    private boolean layoutInScreen;
    private boolean animationEnabled = true;
    private boolean ignoreOnTextChange;
    private CloseProgressDrawable2 progressDrawable;
    private int additionalYOffset;
    private int additionalXOffset;
    private boolean longClickEnabled = true;
    private boolean animateClear = true;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor) {
        this(context, menu, backgroundColor, iconColor, false);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, boolean text) {
        super(context);
        if (backgroundColor != 0) {
            setBackgroundDrawable(Theme.createSelectorDrawable(backgroundColor, text ? 5 : 1));
        }
        parentMenu = menu;

        if (text) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            if (iconColor != 0) {
                textView.setTextColor(iconColor);
            }
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        } else {
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            if (iconColor != 0) {
                iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    public void setLongClickEnabled(boolean value) {
        longClickEnabled = value;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (longClickEnabled && hasSubMenu() && (popupWindow == null || popupWindow != null && !popupWindow.isShowing())) {
                showMenuRunnable = () -> {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    toggleSubMenu();
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
        if (iconView != null) {
            iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        if (textView != null) {
            textView.setTextColor(color);
        }
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
        popupLayout.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (popupWindow != null && popupWindow.isShowing()) {
                    v.getHitRect(rect);
                    if (!rect.contains((int) event.getX(), (int) event.getY())) {
                        popupWindow.dismiss();
                    }
                }
            }
            return false;
        });
        popupLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });
    }

    public void removeAllSubItems() {
        if (popupLayout == null) {
            return;
        }
        popupLayout.removeInnerViews();
    }

    public void addSubItem(View view, int width, int height) {
        createPopupLayout();
        popupLayout.addView(view, new LinearLayout.LayoutParams(width, height));
    }

    public void addSubItem(int id, View view, int width, int height) {
        createPopupLayout();
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        popupLayout.addView(view);
        view.setTag(id);
        view.setOnClickListener(view1 -> {
            if (popupWindow != null && popupWindow.isShowing()) {
                if (processedPopupClick) {
                    return;
                }
                processedPopupClick = true;
                popupWindow.dismiss(allowCloseAnimation);
            }
            if (parentMenu != null) {
                parentMenu.onItemClick((Integer) view1.getTag());
            } else if (delegate != null) {
                delegate.onItemClick((Integer) view1.getTag());
            }
        });
        view.setBackgroundDrawable(Theme.getSelectorDrawable(false));
    }

    public TextView addSubItem(int id, CharSequence text) {
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
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
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

        textView.setOnClickListener(view -> {
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
        });
        return textView;
    }

    public ActionBarMenuSubItem addSubItem(int id, int icon, CharSequence text) {
        createPopupLayout();

        ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext());
        cell.setTextAndIcon(text, icon);
        cell.setMinimumWidth(AndroidUtilities.dp(196));
        cell.setTag(id);
        popupLayout.addView(cell);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) cell.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        cell.setLayoutParams(layoutParams);
        cell.setOnClickListener(view -> {
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
        });
        return cell;
    }

    public void redrawPopup(int color) {
        if (popupLayout != null) {
            popupLayout.backgroundDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            popupLayout.invalidate();
        }
    }

    public void setPopupItemsColor(int color, boolean icon) {
        if (popupLayout == null) {
            return;
        }
        int count = popupLayout.linearLayout.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = popupLayout.linearLayout.getChildAt(a);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            } else if (child instanceof ActionBarMenuSubItem) {
                if (icon) {
                    ((ActionBarMenuSubItem) child).setIconColor(color);
                } else {
                    ((ActionBarMenuSubItem) child).setTextColor(color);
                }
            }
        }
    }

    public boolean hasSubMenu() {
        return popupLayout != null;
    }

    public void setMenuYOffset(int offset) {
        yOffset = offset;
    }

    public void toggleSubMenu() {
        if (popupLayout == null || parentMenu != null && parentMenu.isActionMode && parentMenu.parentActionBar != null && !parentMenu.parentActionBar.isActionModeShowed()) {
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
                popupWindow.setLayoutInScreen(true);
            }
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST));
            popupWindow.getContentView().setFocusableInTouchMode(true);
            popupWindow.getContentView().setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss();
                    return true;
                }
                return false;
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
                if (openKeyboard) {
                    AndroidUtilities.hideKeyboard(searchField);
                }
                searchField.setText("");
                searchContainer.setVisibility(GONE);
                searchField.clearFocus();
                setVisibility(VISIBLE);
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

    public void setIcon(Drawable drawable) {
        if (iconView == null) {
            return;
        }
        iconView.setImageDrawable(drawable);
    }

    public void setIcon(int resId) {
        if (iconView == null) {
            return;
        }
        iconView.setImageResource(resId);
    }

    public void setText(CharSequence text) {
        if (textView == null) {
            return;
        }
        textView.setText(text);
    }

    public View getContentView() {
        return iconView != null ? iconView : textView;
    }

    public void setSearchFieldHint(CharSequence hint) {
        if (searchFieldCaption == null) {
            return;
        }
        searchField.setHint(hint);
        setContentDescription(hint);
    }

    public void setSearchFieldText(CharSequence text, boolean animated) {
        if (searchFieldCaption == null) {
            return;
        }
        animateClear = animated;
        searchField.setText(text);
        if (!TextUtils.isEmpty(text)) {
            searchField.setSelection(text.length());
        }
    }

    public void onSearchPressed() {
        if (listener != null) {
            listener.onSearchPressed(searchField);
        }
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
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!AndroidUtilities.showKeyboard(this)) {
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return super.onTouchEvent(event);
                }
            };
            searchField.setCursorWidth(1.5f);
            searchField.setCursorColor(Theme.getColor(Theme.key_actionBarDefaultSearch));
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
            searchField.setOnEditorActionListener((v, actionId, event) -> {
                if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    AndroidUtilities.hideKeyboard(searchField);
                    if (listener != null) {
                        listener.onSearchPressed(searchField);
                    }
                }
                return false;
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
                        if (TextUtils.isEmpty(s) &&
                                (listener == null || !listener.forceShowClear()) &&
                                (searchFieldCaption == null || searchFieldCaption.getVisibility() != VISIBLE)) {
                            if (clearButton.getTag() != null) {
                                clearButton.setTag(null);
                                clearButton.clearAnimation();
                                if (animateClear) {
                                    clearButton.animate().setInterpolator(new DecelerateInterpolator()).alpha(0.0f).setDuration(180).scaleY(0.0f).scaleX(0.0f).rotation(45).withEndAction(() -> clearButton.setVisibility(INVISIBLE)).start();
                                } else {
                                    clearButton.setAlpha(0.0f);
                                    clearButton.setRotation(45);
                                    clearButton.setScaleX(0.0f);
                                    clearButton.setScaleY(0.0f);
                                    clearButton.setVisibility(INVISIBLE);
                                    animateClear = true;
                                }
                            }
                        } else {
                            if (clearButton.getTag() == null) {
                                clearButton.setTag(1);
                                clearButton.clearAnimation();
                                clearButton.setVisibility(VISIBLE);
                                if (animateClear) {
                                    clearButton.animate().setInterpolator(new DecelerateInterpolator()).alpha(1.0f).setDuration(180).scaleY(1.0f).scaleX(1.0f).rotation(0).start();
                                } else {
                                    clearButton.setAlpha(1.0f);
                                    clearButton.setRotation(0);
                                    clearButton.setScaleX(1.0f);
                                    clearButton.setScaleY(1.0f);
                                    animateClear = true;
                                }
                            }
                        }
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

            clearButton = new ImageView(getContext()) {
                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    clearAnimation();
                    if (getTag() == null) {
                        clearButton.setVisibility(INVISIBLE);
                        clearButton.setAlpha(0.0f);
                        clearButton.setRotation(45);
                        clearButton.setScaleX(0.0f);
                        clearButton.setScaleY(0.0f);
                    } else {
                        clearButton.setAlpha(1.0f);
                        clearButton.setRotation(0);
                        clearButton.setScaleX(1.0f);
                        clearButton.setScaleY(1.0f);
                    }
                }
            };
            clearButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            clearButton.setColorFilter(new PorterDuffColorFilter(parentMenu.parentActionBar.itemsColor, PorterDuff.Mode.MULTIPLY));
            clearButton.setScaleType(ImageView.ScaleType.CENTER);
            clearButton.setAlpha(0.0f);
            clearButton.setRotation(45);
            clearButton.setScaleX(0.0f);
            clearButton.setScaleY(0.0f);
            clearButton.setOnClickListener(v -> {
                if (searchField.length() != 0) {
                    searchField.setText("");
                } else if (searchFieldCaption != null && searchFieldCaption.getVisibility() == VISIBLE) {
                    searchFieldCaption.setVisibility(GONE);
                    if (listener != null) {
                        listener.onCaptionCleared();
                    }
                }
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            });
            clearButton.setContentDescription(LocaleController.getString("ClearButton", R.string.ClearButton));
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
        if (searchFieldCaption == null) {
            return;
        }
        if (TextUtils.isEmpty(caption)) {
            searchFieldCaption.setVisibility(GONE);
        } else {
            searchFieldCaption.setVisibility(VISIBLE);
            searchFieldCaption.setText(caption);
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

    public void setAdditionalYOffset(int value) {
        additionalYOffset = value;
    }

    public void setAdditionalXOffset(int value) {
        additionalXOffset = value;
    }

    private void updateOrShowPopup(boolean show, boolean update) {
        int offsetY;

        if (parentMenu != null) {
            offsetY = -parentMenu.parentActionBar.getMeasuredHeight() + parentMenu.getTop() + parentMenu.getPaddingTop();
        } else {
            float scaleY = getScaleY();
            offsetY = -(int) (getMeasuredHeight() * scaleY - (subMenuOpenSide != 2 ? getTranslationY() : 0) / scaleY) + additionalYOffset;
        }
        offsetY += yOffset;

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
                        popupWindow.showAsDropDown(parent, getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth() + additionalXOffset, offsetY);
                    }
                    if (update) {
                        popupWindow.update(parent, getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth() + additionalXOffset, offsetY, -1, -1);
                    }
                }
            } else if (subMenuOpenSide == 1) {
                if (show) {
                    popupWindow.showAsDropDown(this, -AndroidUtilities.dp(8) + additionalXOffset, offsetY);
                }
                if (update) {
                    popupWindow.update(this, -AndroidUtilities.dp(8) + additionalXOffset, offsetY, -1, -1);
                }
            } else {
                if (show) {
                    popupWindow.showAsDropDown(this, getMeasuredWidth() - popupLayout.getMeasuredWidth() + additionalXOffset, offsetY);
                }
                if (update) {
                    popupWindow.update(this, getMeasuredWidth() - popupLayout.getMeasuredWidth() + additionalXOffset, offsetY, -1, -1);
                }
            }
        }
    }

    public void hideSubItem(int id) {
        if (popupLayout == null) {
            return;
        }
        View view = popupLayout.findViewWithTag(id);
        if (view != null && view.getVisibility() != GONE) {
            view.setVisibility(GONE);
        }
    }

    public boolean isSubItemVisible(int id) {
        if (popupLayout == null) {
            return false;
        }
        View view = popupLayout.findViewWithTag(id);
        return view != null && view.getVisibility() == VISIBLE;
    }

    public void showSubItem(int id) {
        if (popupLayout == null) {
            return;
        }
        View view = popupLayout.findViewWithTag(id);
        if (view != null && view.getVisibility() != VISIBLE) {
            view.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.ImageButton");
    }
}
