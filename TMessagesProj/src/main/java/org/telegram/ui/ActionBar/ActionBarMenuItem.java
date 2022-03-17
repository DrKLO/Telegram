/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

public class ActionBarMenuItem extends FrameLayout {

    private FrameLayout wrappedSearchFrameLayout;

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

        public Animator getCustomToggleTransition() {
            return null;
        }

        public void onLayout(int l, int t, int r, int b) {

        }

        public void onSearchFilterCleared(FiltersView.MediaFilterData filterData) {

        }

        public boolean canToggleSearch() {
            return true;
        }
    }

    public interface ActionBarSubMenuItemDelegate {
        void onShowSubMenu();
        void onHideSubMenu();
    }

    public interface ActionBarMenuItemDelegate {
        void onItemClick(int id);
    }

    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ActionBarMenu parentMenu;
    private ActionBarPopupWindow popupWindow;
    private EditTextBoldCursor searchField;
    private LinearLayout searchFilterLayout;
    private ArrayList<SearchFilterView> searchFilterViews = new ArrayList<>();
    private TextView searchFieldCaption;
    private ImageView clearButton;
    protected RLottieImageView iconView;
    protected TextView textView;
    private FrameLayout searchContainer;
    private boolean isSearchField;
    protected ActionBarMenuItemSearchListener listener;
    private Rect rect;
    private int[] location;
    private View selectedMenuView;
    private Runnable showMenuRunnable;
    private int subMenuOpenSide;
    private int yOffset;
    private ActionBarMenuItemDelegate delegate;
    private ActionBarSubMenuItemDelegate subMenuDelegate;
    private boolean allowCloseAnimation = true;
    protected boolean overrideMenuClick;
    private boolean processedPopupClick;
    private boolean layoutInScreen;
    private boolean animationEnabled = true;
    private boolean ignoreOnTextChange;
    private CloseProgressDrawable2 progressDrawable;
    private int additionalYOffset;
    private int additionalXOffset;
    private boolean longClickEnabled;
    private boolean animateClear = true;
    private boolean measurePopup = true;
    private boolean forceSmoothKeyboard;
    private boolean showSubmenuByMove = true;
    private ArrayList<FiltersView.MediaFilterData> currentSearchFilters = new ArrayList<>();
    private int selectedFilterIndex = -1;
    private int notificationIndex = -1;

    private float transitionOffset;
    private View showSubMenuFrom;
    private final Theme.ResourcesProvider resourcesProvider;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor) {
        this(context, menu, backgroundColor, iconColor, false);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, Theme.ResourcesProvider resourcesProvider) {
        this(context, menu, backgroundColor, iconColor, false, resourcesProvider);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, boolean text) {
        this(context, menu, backgroundColor, iconColor, text, null);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, boolean text, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
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
            textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (iconColor != 0) {
                textView.setTextColor(iconColor);
            }
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        } else {
            iconView = new RLottieImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            if (iconColor != 0) {
                iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
        }
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX + transitionOffset);
    }

    public void setLongClickEnabled(boolean value) {
        longClickEnabled = value;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (longClickEnabled && hasSubMenu() && (popupWindow == null || !popupWindow.isShowing())) {
                showMenuRunnable = () -> {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    toggleSubMenu();
                };
                AndroidUtilities.runOnUIThread(showMenuRunnable, 200);
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (showSubmenuByMove && hasSubMenu() && (popupWindow == null || !popupWindow.isShowing())) {
                if (event.getY() > getHeight()) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    toggleSubMenu();
                    return true;
                }
            } else if (showSubmenuByMove && popupWindow != null && popupWindow.isShowing()) {
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
                    Object tag = child.getTag();
                    if (tag instanceof Integer && (Integer) tag < 100) {
                        if (!rect.contains((int) x, (int) y)) {
                            child.setPressed(false);
                            child.setSelected(false);
                            if (Build.VERSION.SDK_INT == 21 && child.getBackground() != null) {
                                child.getBackground().setVisible(false, false);
                            }
                        } else {
                            child.setPressed(true);
                            child.setSelected(true);
                            if (Build.VERSION.SDK_INT >= 21) {
                                if (Build.VERSION.SDK_INT == 21 && child.getBackground() != null) {
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
            } else if (showSubmenuByMove) {
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

    public void setDelegate(ActionBarMenuItemDelegate actionBarMenuItemDelegate) {
        delegate = actionBarMenuItemDelegate;
    }

    public void setSubMenuDelegate(ActionBarSubMenuItemDelegate actionBarSubMenuItemDelegate) {
        subMenuDelegate = actionBarSubMenuItemDelegate;
    }

    public void setShowSubmenuByMove(boolean value) {
        showSubmenuByMove = value;
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

    public void setForceSmoothKeyboard(boolean value) {
        forceSmoothKeyboard = value;
    }

    private void createPopupLayout() {
        if (popupLayout != null) {
            return;
        }
        rect = new Rect();
        location = new int[2];
        popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), resourcesProvider);
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

    public void setShowedFromBottom(boolean value) {
        if (popupLayout == null) {
            return;
        }
        popupLayout.setShownFromBotton(value);
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
        textView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
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
                if (!allowCloseAnimation) {
                    popupWindow.setAnimationStyle(R.style.PopupAnimation);
                }
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
        return addSubItem(id, icon, null, text, true, false);
    }

    public ActionBarMenuSubItem addSubItem(int id, int icon, CharSequence text, Theme.ResourcesProvider resourcesProvider) {
        return addSubItem(id, icon, null, text, true, false, resourcesProvider);
    }

    public ActionBarMenuSubItem addSubItem(int id, int icon, CharSequence text, boolean needCheck) {
        return addSubItem(id, icon, null, text, true, needCheck);
    }

    public View addGap(int id) {
        createPopupLayout();

        View cell = new View(getContext());
        cell.setMinimumWidth(AndroidUtilities.dp(196));
        cell.setTag(id);
        cell.setTag(R.id.object_tag, 1);
        popupLayout.addView(cell);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) cell.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(6);
        cell.setLayoutParams(layoutParams);
        return cell;
    }

    public ActionBarMenuSubItem addSubItem(int id, int icon, Drawable iconDrawable, CharSequence text, boolean dismiss, boolean needCheck) {
        return addSubItem(id, icon, iconDrawable, text, dismiss, needCheck, null);
    }

    public ActionBarMenuSubItem addSubItem(int id, int icon, Drawable iconDrawable, CharSequence text, boolean dismiss, boolean needCheck, Theme.ResourcesProvider resourcesProvider) {
        createPopupLayout();

        ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext(), needCheck, false, false, resourcesProvider);
        cell.setTextAndIcon(text, icon, iconDrawable);
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
                if (dismiss) {
                    if (processedPopupClick) {
                        return;
                    }
                    processedPopupClick = true;
                    popupWindow.dismiss(allowCloseAnimation);
                }
            }
            if (parentMenu != null) {
                parentMenu.onItemClick((Integer) view.getTag());
            } else if (delegate != null) {
                delegate.onItemClick((Integer) view.getTag());
            }
        });
        return cell;
    }

    public View addDivider(int color) {
        createPopupLayout();

        TextView cell = new TextView(getContext());
        cell.setBackgroundColor(color);
        cell.setMinimumWidth(AndroidUtilities.dp(196));
        popupLayout.addView(cell);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) cell.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = 1;
        layoutParams.topMargin = layoutParams.bottomMargin = AndroidUtilities.dp(3);
        cell.setLayoutParams(layoutParams);

        return cell;
    }

    public void redrawPopup(int color) {
        if (popupLayout != null && popupLayout.getBackgroundColor() != color) {
            popupLayout.setBackgroundColor(color);
            if (popupWindow != null && popupWindow.isShowing()) {
                popupLayout.invalidate();
            }
        }
    }

    public void setPopupItemsColor(int color, boolean icon) {
        if (popupLayout == null) {
            return;
        }
        final LinearLayout layout = popupLayout.linearLayout;
        for (int a = 0, count = layout.getChildCount(); a < count; a++) {
            final View child = layout.getChildAt(a);
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

    public void setPopupItemsSelectorColor(int color) {
        if (popupLayout == null) {
            return;
        }
        final LinearLayout layout = popupLayout.linearLayout;
        for (int a = 0, count = layout.getChildCount(); a < count; a++) {
            final View child = layout.getChildAt(a);
            if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setSelectorColor(color);
            }
        }
    }

    public void setupPopupRadialSelectors(int color) {
        if (popupLayout != null) {
            popupLayout.setupRadialSelectors(color);
        }
    }

    public boolean hasSubMenu() {
        return popupLayout != null;
    }

    public ActionBarPopupWindow.ActionBarPopupWindowLayout getPopupLayout() {
        return popupLayout;
    }

    public void setMenuYOffset(int offset) {
        yOffset = offset;
    }

    public void toggleSubMenu(View topView, View fromView) {
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

        showSubMenuFrom = fromView;

        if (subMenuDelegate != null) {
            subMenuDelegate.onShowSubMenu();
        }
        if (popupLayout.getParent() != null) {
            ((ViewGroup) popupLayout.getParent()).removeView(popupLayout);
        }
        ViewGroup container = popupLayout;
        if (topView != null) {
            LinearLayout linearLayout = new LinearLayout(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    popupLayout.measure(widthMeasureSpec, heightMeasureSpec);
                    topView.getLayoutParams().width = popupLayout.getMeasuredWidth() - AndroidUtilities.dp(16);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            FrameLayout frameLayout = new FrameLayout(getContext());
            frameLayout.setAlpha(0f);
            frameLayout.animate().alpha(1f).setDuration(100).start();
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert2).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(popupLayout.getBackgroundColor(), PorterDuff.Mode.MULTIPLY));

            frameLayout.setBackground(drawable);
            frameLayout.addView(topView);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            linearLayout.addView(popupLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, -AndroidUtilities.dp(4), 0, 0));
            container = linearLayout;
        }
        popupWindow = new ActionBarPopupWindow(container, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
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
        container.setFocusableInTouchMode(true);
        container.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
                return true;
            }
            return false;
        });
        popupWindow.setOnDismissListener(() -> {
            onDismiss();
            if (subMenuDelegate != null) {
                subMenuDelegate.onHideSubMenu();
            }
        });

       // if (measurePopup) {
            container.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST));
            measurePopup = false;
        //}
        processedPopupClick = false;
        popupWindow.setFocusable(true);
        if (container.getMeasuredWidth() == 0) {
            updateOrShowPopup(true, true);
        } else {
            updateOrShowPopup(true, false);
        }
        popupLayout.updateRadialSelectors();
        popupWindow.startAnimation();
    }
    public void toggleSubMenu() {
        toggleSubMenu(null, null);
    }

    public void openSearch(boolean openKeyboard) {
        if (searchContainer == null || searchContainer.getVisibility() == VISIBLE || parentMenu == null) {
            return;
        }
        parentMenu.parentActionBar.onSearchFieldVisibilityChanged(toggleSearch(openKeyboard));
    }

    protected void onDismiss() {

    }

    public boolean isSearchFieldVisible() {
        return searchContainer.getVisibility() == VISIBLE;
    }

    AnimatorSet searchContainerAnimator;

    public boolean toggleSearch(boolean openKeyboard) {
        if (searchContainer == null || (listener != null && !listener.canToggleSearch())) {
            return false;
        }
        if (listener != null) {
            Animator animator = listener.getCustomToggleTransition();
            if (animator != null) {
                animator.start();
                return true;
            }
        }
        ArrayList<View> menuIcons = new ArrayList<>();
        for (int i = 0; i < parentMenu.getChildCount(); i++) {
            View view = parentMenu.getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                View iconView = ((ActionBarMenuItem) view).getIconView();
                if (iconView != null) {
                    menuIcons.add(iconView);
                }
            }
        }

        if (searchContainer.getTag() != null) {
            searchContainer.setTag(null);
            if (searchContainerAnimator != null) {
                searchContainerAnimator.removeAllListeners();
                searchContainerAnimator.cancel();
            }
            searchContainerAnimator = new AnimatorSet();
            searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, searchContainer.getAlpha(), 0f));
            for (int i = 0; i < menuIcons.size(); i++) {
                menuIcons.get(i).setAlpha(0f);
                searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(menuIcons.get(i), View.ALPHA, menuIcons.get(i).getAlpha(), 1f));
            }
            searchContainerAnimator.setDuration(150);
            searchContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    searchContainer.setAlpha(0);
                    for (int i = 0; i < menuIcons.size(); i++) {
                        menuIcons.get(i).setAlpha(1f);
                    }
                    searchContainer.setVisibility(View.GONE);
                }
            });
            searchContainerAnimator.start();

            searchField.clearFocus();
            setVisibility(VISIBLE);
            if (!currentSearchFilters.isEmpty()) {
                if (listener != null) {
                    for (int i = 0; i < currentSearchFilters.size(); i++) {
                        if ( currentSearchFilters.get(i).removable) {
                            listener.onSearchFilterCleared(currentSearchFilters.get(i));
                        }
                    }
                }
                clearSearchFilters();
            }
            if (listener != null) {
                listener.onSearchCollapse();
            }
            if (openKeyboard) {
                AndroidUtilities.hideKeyboard(searchField);
            }
            parentMenu.requestLayout();
            requestLayout();
            return false;
        } else {
            searchContainer.setVisibility(VISIBLE);
            searchContainer.setAlpha(0);
            if (searchContainerAnimator != null) {
                searchContainerAnimator.removeAllListeners();
                searchContainerAnimator.cancel();
            }
            searchContainerAnimator = new AnimatorSet();
            searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, searchContainer.getAlpha(), 1f));
            for (int i = 0; i < menuIcons.size(); i++) {
                searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(menuIcons.get(i), View.ALPHA, menuIcons.get(i).getAlpha(), 0f));
            }
            searchContainerAnimator.setDuration(150);
            searchContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    searchContainer.setAlpha(1f);
                    for (int i = 0; i < menuIcons.size(); i++) {
                        menuIcons.get(i).setAlpha(0f);
                    }
                }
            });
            searchContainerAnimator.start();
            setVisibility(GONE);
            clearSearchFilters();
            searchField.setText("");
            searchField.requestFocus();
            if (openKeyboard) {
                AndroidUtilities.showKeyboard(searchField);
            }
            if (listener != null) {
                listener.onSearchExpand();
            }
            searchContainer.setTag(1);
            return true;
        }
    }

    public void removeSearchFilter(FiltersView.MediaFilterData filter) {
        if (!filter.removable) {
            return;
        }
        currentSearchFilters.remove(filter);
        if (selectedFilterIndex < 0 || selectedFilterIndex > currentSearchFilters.size() - 1) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
        searchField.hideActionMode();
    }
    public void addSearchFilter(FiltersView.MediaFilterData filter) {
        currentSearchFilters.add(filter);
        if (searchContainer.getTag() != null) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
    }

    public void clearSearchFilters() {
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                currentSearchFilters.remove(i);
                i--;
            }
        }
        onFiltersChanged();
    }

    private void onFiltersChanged() {
        boolean visible = !currentSearchFilters.isEmpty();
        ArrayList<FiltersView.MediaFilterData> localFilters = new ArrayList<>(currentSearchFilters);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && searchContainer.getTag() != null) {
            TransitionSet transition = new TransitionSet();
            ChangeBounds changeBounds = new ChangeBounds();
            changeBounds.setDuration(150);
            transition.addTransition(new Visibility() {
                        @Override
                        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                            if (view instanceof SearchFilterView) {
                                AnimatorSet set = new AnimatorSet();
                                set.playTogether(
                                        ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                                        ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
                                        ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
                                );
                                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                return set;
                            }
                            return ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f);
                        }
                        @Override
                        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                            if (view instanceof SearchFilterView) {
                                AnimatorSet set = new AnimatorSet();
                                set.playTogether(
                                        ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
                                        ObjectAnimator.ofFloat(view, View.SCALE_X,  view.getScaleX(), 0.5f),
                                        ObjectAnimator.ofFloat(view, View.SCALE_Y,  view.getScaleX(), 0.5f)
                                );
                                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                return set;
                            }
                            return ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0);
                        }
                    }.setDuration(150)).addTransition(changeBounds);
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            int selectedAccount = UserConfig.selectedAccount;
            transition.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    notificationIndex = NotificationCenter.getInstance(selectedAccount).setAnimationInProgress(notificationIndex,null);
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    NotificationCenter.getInstance(selectedAccount).onAnimationFinish(notificationIndex);
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    NotificationCenter.getInstance(selectedAccount).onAnimationFinish(notificationIndex);
                }

                @Override
                public void onTransitionPause(Transition transition) {

                }

                @Override
                public void onTransitionResume(Transition transition) {

                }
            });
            TransitionManager.beginDelayedTransition(searchFilterLayout, transition);
        }

        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            boolean removed = localFilters.remove(((SearchFilterView)searchFilterLayout.getChildAt(i)).getFilter());
            if (!removed) {
                searchFilterLayout.removeViewAt(i);
                i--;
            }
        }

        for (int i = 0; i < localFilters.size(); i++) {
            SearchFilterView searchFilterView = new SearchFilterView(getContext(), resourcesProvider);
            searchFilterView.setData(localFilters.get(i));
            searchFilterView.setOnClickListener(view -> {
                int index = currentSearchFilters.indexOf(searchFilterView.getFilter());
                if (selectedFilterIndex != index) {
                    selectedFilterIndex = index;
                    onFiltersChanged();
                    return;
                }
                if (searchFilterView.getFilter().removable) {
                    if (!searchFilterView.selectedForDelete) {
                        searchFilterView.setSelectedForDelete(true);
                    } else {
                        FiltersView.MediaFilterData filterToRemove = searchFilterView.getFilter();
                        removeSearchFilter(filterToRemove);
                        if (listener != null) {
                            listener.onSearchFilterCleared(filterToRemove);
                            listener.onTextChanged(searchField);
                        }
                    }
                }
            });
            searchFilterLayout.addView(searchFilterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 6, 0));
        }

        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            ((SearchFilterView)searchFilterLayout.getChildAt(i)).setExpanded(i == selectedFilterIndex);
        }

        searchFilterLayout.setTag(visible ? 1 : null);

        float oldX = searchField.getX();
        if (searchContainer.getTag() != null) {
            searchField.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    searchField.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (searchField.getX() != oldX) {
                        searchField.setTranslationX(oldX - searchField.getX());
                    }
                    searchField.animate().translationX(0).setDuration(250).setStartDelay(0).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    return true;
                }
            });
        }
        checkClearButton();
    }

    public static boolean checkRtl (String string) {
        if (TextUtils.isEmpty(string)) {
            return false;
        }
        char c = string.charAt(0);
        return c >= 0x590 && c <= 0x6ff;
    }

    public boolean isSubMenuShowing() {
        return popupWindow != null && popupWindow.isShowing();
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
        if (drawable instanceof RLottieDrawable) {
            iconView.setAnimation((RLottieDrawable) drawable);
        } else {
            iconView.setImageDrawable(drawable);
        }
    }

    public RLottieImageView getIconView() {
        return iconView;
    }

    public TextView getTextView() {
        return textView;
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
        return setIsSearchField(value, false);
    }

    public ActionBarMenuItem setIsSearchField(boolean value, boolean wrapInScrollView) {
        if (parentMenu == null) {
            return this;
        }
        if (value && searchContainer == null) {
            searchContainer = new FrameLayout(getContext()) {

                private boolean ignoreRequestLayout;

                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);
                    if (clearButton != null) {
                        clearButton.setVisibility(visibility);
                    }
                    if (wrappedSearchFrameLayout != null) {
                        wrappedSearchFrameLayout.setVisibility(visibility);
                    }
                }

                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    if (clearButton != null && clearButton.getTag() != null) {
                        clearButton.setAlpha(alpha);
                        clearButton.setScaleX(alpha);
                        clearButton.setScaleY(alpha);
                    }
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (!wrapInScrollView) {
                        measureChildWithMargins(clearButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                    int width;
                    if (!LocaleController.isRTL) {
                        if (searchFieldCaption.getVisibility() == VISIBLE) {
                            measureChildWithMargins(searchFieldCaption, widthMeasureSpec, MeasureSpec.getSize(widthMeasureSpec) / 2, heightMeasureSpec, 0);
                            width = searchFieldCaption.getMeasuredWidth() + AndroidUtilities.dp(4);
                        } else {
                            width = 0;
                        }
                        int minWidth = MeasureSpec.getSize(widthMeasureSpec);
                        ignoreRequestLayout = true;
                        measureChildWithMargins(searchFilterLayout, widthMeasureSpec, width, heightMeasureSpec, 0);
                        int filterWidth = searchFilterLayout.getVisibility() == View.VISIBLE ? searchFilterLayout.getMeasuredWidth() : 0;
                        measureChildWithMargins(searchField, widthMeasureSpec, width + filterWidth, heightMeasureSpec, 0);
                        ignoreRequestLayout = false;
                        setMeasuredDimension(Math.max(filterWidth + searchField.getMeasuredWidth(), minWidth), MeasureSpec.getSize(heightMeasureSpec));
                    } else {
                        if (searchFieldCaption.getVisibility() == VISIBLE) {
                            measureChildWithMargins(searchFieldCaption, widthMeasureSpec, MeasureSpec.getSize(widthMeasureSpec) / 2, heightMeasureSpec, 0);
                            width = searchFieldCaption.getMeasuredWidth() + AndroidUtilities.dp(4);
                        } else {
                            width = 0;
                        }
                        int minWidth = MeasureSpec.getSize(widthMeasureSpec);
                        ignoreRequestLayout = true;
                        measureChildWithMargins(searchFilterLayout, widthMeasureSpec, width, heightMeasureSpec, 0);
                        int filterWidth = searchFilterLayout.getVisibility() == View.VISIBLE ? searchFilterLayout.getMeasuredWidth() : 0;
                        measureChildWithMargins(searchField, MeasureSpec.makeMeasureSpec(minWidth - AndroidUtilities.dp(12), MeasureSpec.UNSPECIFIED), width + filterWidth, heightMeasureSpec, 0);
                        ignoreRequestLayout = false;
                        setMeasuredDimension(Math.max(filterWidth + searchField.getMeasuredWidth(), minWidth), MeasureSpec.getSize(heightMeasureSpec));
                    }
                }

                @Override
                public void requestLayout() {
                    if (ignoreRequestLayout) {
                        return;
                    }
                    super.requestLayout();
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
                    if (searchFilterLayout.getVisibility() == VISIBLE) {
                        x += searchFilterLayout.getMeasuredWidth();
                    }
                    searchField.layout(x, searchField.getTop(), x + searchField.getMeasuredWidth(), searchField.getBottom());
                }
            };
            searchContainer.setClipChildren(false);
            wrappedSearchFrameLayout = null;
            if (wrapInScrollView) {
                wrappedSearchFrameLayout = new FrameLayout(getContext());
                HorizontalScrollView horizontalScrollView = new HorizontalScrollView(getContext()) {

                    boolean isDragging;

                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent ev) {
                        checkDragg(ev);
                        return super.onInterceptTouchEvent(ev);
                    }

                    @Override
                    public boolean onTouchEvent(MotionEvent ev) {
                        checkDragg(ev);
                        return super.onTouchEvent(ev);
                    }

                    private void checkDragg(MotionEvent ev) {
                        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                            isDragging = true;
                        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                            isDragging = false;
                        }
                    }

                    @Override
                    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
                        if (!isDragging) {
                            return;
                        }
                        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
                    }
                };
                horizontalScrollView.addView(searchContainer, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0));
                horizontalScrollView.setHorizontalScrollBarEnabled(false);
                horizontalScrollView.setClipChildren(false);
                wrappedSearchFrameLayout.addView(horizontalScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 48, 0));
                parentMenu.addView(wrappedSearchFrameLayout, 0, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 0, 0, 0, 0));
            } else {
                parentMenu.addView(searchContainer, 0, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 6, 0, 0, 0));
            }
            searchContainer.setVisibility(GONE);

            searchFieldCaption = new TextView(getContext());
            searchFieldCaption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchFieldCaption.setTextColor(getThemedColor(Theme.key_actionBarDefaultSearch));
            searchFieldCaption.setSingleLine(true);
            searchFieldCaption.setEllipsize(TextUtils.TruncateAt.END);
            searchFieldCaption.setVisibility(GONE);
            searchFieldCaption.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            searchField = new EditTextBoldCursor(getContext()) {

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    int minWidth = MeasureSpec.getSize(widthMeasureSpec);
                    setMeasuredDimension(Math.max(minWidth, getMeasuredWidth()) + AndroidUtilities.dp(3), getMeasuredHeight());
                }

                @Override
                protected void onSelectionChanged(int selStart, int selEnd) {
                    super.onSelectionChanged(selStart, selEnd);
                }

                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && searchField.length() == 0 && ((searchFieldCaption.getVisibility() == VISIBLE && searchFieldCaption.length() > 0) || hasRemovableFilters())) {
                        if (hasRemovableFilters()) {
                            FiltersView.MediaFilterData filterToRemove = currentSearchFilters.get(currentSearchFilters.size() - 1);
                            if (listener != null) {
                                listener.onSearchFilterCleared(filterToRemove);
                            }
                            removeSearchFilter(filterToRemove);
                        } else {
                            clearButton.callOnClick();
                        }
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    boolean result = super.onTouchEvent(event);
                    if (event.getAction() == MotionEvent.ACTION_UP) { //hack to fix android bug with not opening keyboard
                        if (!AndroidUtilities.showKeyboard(this)) {
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return result;
                }
            };
            searchField.setScrollContainer(false);
            searchField.setCursorWidth(1.5f);
            searchField.setCursorColor(getThemedColor(Theme.key_actionBarDefaultSearch));
            searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            searchField.setHintTextColor(getThemedColor(Theme.key_actionBarDefaultSearchPlaceholder));
            searchField.setTextColor(getThemedColor(Theme.key_actionBarDefaultSearch));
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
                    checkClearButton();
                    if (!currentSearchFilters.isEmpty()) {
                        if (!TextUtils.isEmpty(searchField.getText()) && selectedFilterIndex >= 0) {
                            selectedFilterIndex = -1;
                            onFiltersChanged();
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
            searchField.setTextIsSelectable(false);

            searchFilterLayout = new LinearLayout(getContext());
            searchFilterLayout.setOrientation(LinearLayout.HORIZONTAL);
            searchFilterLayout.setVisibility(View.VISIBLE);
            if (!LocaleController.isRTL) {
                searchContainer.addView(searchFieldCaption, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 5.5f, 0, 0));
                searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL, 6, 0, 48, 0));
                searchContainer.addView(searchFilterLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
            } else {
                searchContainer.addView(searchFilterLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_VERTICAL, 0, 0, 48, 0));
                searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL, 0, 0, wrapInScrollView ? 0 : 48, 0));
                searchContainer.addView(searchFieldCaption, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 5.5f, 48, 0));
            }
            searchFilterLayout.setClipChildren(false);

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
                } else if (hasRemovableFilters()) {
                    searchField.hideActionMode();
                    for (int i = 0; i < currentSearchFilters.size(); i++) {
                        if (listener != null && currentSearchFilters.get(i).removable) {
                            listener.onSearchFilterCleared(currentSearchFilters.get(i));
                        }
                    }
                    clearSearchFilters();
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
            if (wrapInScrollView) {
                wrappedSearchFrameLayout.addView(clearButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            } else {
                searchContainer.addView(clearButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
        }
        isSearchField = value;
        return this;
    }

    private void checkClearButton() {
        if (clearButton != null) {
            if (!hasRemovableFilters() && TextUtils.isEmpty(searchField.getText()) &&
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

    private boolean hasRemovableFilters() {
        if (currentSearchFilters.isEmpty()) {
            return false;
        }
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                return true;
            }
        }
        return false;
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

    public ActionBarMenuItem setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener actionBarMenuItemSearchListener) {
        listener = actionBarMenuItemSearchListener;
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
        if (listener != null) {
            listener.onLayout(left, top, right, bottom);
        }
    }

    public void setAdditionalYOffset(int value) {
        additionalYOffset = value;
    }

    public void setAdditionalXOffset(int value) {
        additionalXOffset = value;
    }

    public void forceUpdatePopupPosition() {
        if (popupWindow == null || !popupWindow.isShowing()) {
            return;
        }
        popupLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST));
        updateOrShowPopup(true, true);
    }

    private void updateOrShowPopup(boolean show, boolean update) {
        int offsetY;

        if (parentMenu != null) {
            offsetY = -parentMenu.parentActionBar.getMeasuredHeight() + parentMenu.getTop() + parentMenu.getPaddingTop()/* - (int) parentMenu.parentActionBar.getTranslationY()*/;
        } else {
            float scaleY = getScaleY();
            offsetY = -(int) (getMeasuredHeight() * scaleY - (subMenuOpenSide != 2 ? getTranslationY() : 0) / scaleY) + additionalYOffset;
        }
        offsetY += yOffset;

        if (show) {
            popupLayout.scrollToTop();
        }
        View fromView = showSubMenuFrom == null ? this : showSubMenuFrom;
        if (parentMenu != null) {
            View parent = parentMenu.parentActionBar;
            if (subMenuOpenSide == 0) {
                if (show) {
                    popupWindow.showAsDropDown(parent, fromView.getLeft() + parentMenu.getLeft() + fromView.getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + (int) getTranslationX(), offsetY);
                }
                if (update) {
                    popupWindow.update(parent, fromView.getLeft() + parentMenu.getLeft() + fromView.getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + (int) getTranslationX(), offsetY, -1, -1);
                }
            } else {
                if (show) {
                    if (forceSmoothKeyboard) {
                        popupWindow.showAtLocation(parent, Gravity.LEFT | Gravity.TOP, getLeft() - AndroidUtilities.dp(8) + (int) getTranslationX(), offsetY);
                    } else {
                        popupWindow.showAsDropDown(parent, getLeft() - AndroidUtilities.dp(8) + (int) getTranslationX(), offsetY);
                    }
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
                        popupWindow.showAsDropDown(parent, getLeft() + getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + additionalXOffset, offsetY);
                    }
                    if (update) {
                        popupWindow.update(parent, getLeft() + getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + additionalXOffset, offsetY, -1, -1);
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
                    popupWindow.showAsDropDown(this, getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + additionalXOffset, offsetY);
                }
                if (update) {
                    popupWindow.update(this, getMeasuredWidth() - popupWindow.getContentView().getMeasuredWidth() + additionalXOffset, offsetY, -1, -1);
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
            measurePopup = true;
        }
    }

    /**
     * Hides this menu item if no subitems are available
     */
    public void checkHideMenuItem() {
        boolean isVisible = false;
        for (int i = 0; i < popupLayout.getItemsCount(); i++) {
            if (popupLayout.getItemAt(i).getVisibility() == VISIBLE) {
                isVisible = true;
                break;
            }
        }
        int v = isVisible ? VISIBLE : GONE;
        if (v != getVisibility()) {
            setVisibility(v);
        }
    }

    public void hideAllSubItems() {
        if (popupLayout == null) {
            return;
        }
        for (int a = 0, N = popupLayout.getItemsCount(); a < N; a++) {
            popupLayout.getItemAt(a).setVisibility(GONE);
        }
        measurePopup = true;
        checkHideMenuItem();
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
            measurePopup = true;
        }
    }

    public void requestFocusOnSearchView() {
        if (searchContainer.getWidth() != 0 && !searchField.isFocused()) {
            searchField.requestFocus();
            AndroidUtilities.showKeyboard(searchField);
        }
    }

    public void clearFocusOnSearchView() {
        searchField.clearFocus();
        AndroidUtilities.hideKeyboard(searchField);
    }

    public FrameLayout getSearchContainer() {
        return searchContainer;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (iconView != null) {
            info.setClassName("android.widget.ImageButton");
        } else if (textView != null) {
            info.setClassName("android.widget.Button");
            if (TextUtils.isEmpty(info.getText())) {
                info.setText(textView.getText());
            }
        }
    }

    public void updateColor() {
        if (searchFilterLayout != null) {
            for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
                if (searchFilterLayout.getChildAt(i) instanceof SearchFilterView) {
                    ((SearchFilterView) searchFilterLayout.getChildAt(i)).updateColors();
                }
            }
        }
        if (popupLayout != null) {
            for (int i = 0; i < popupLayout.getItemsCount(); ++i) {
                if (popupLayout.getItemAt(i) instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) popupLayout.getItemAt(i)).setSelectorColor(getThemedColor(Theme.key_dialogButtonSelector));
                }
            }
        }
    }

    public void collapseSearchFilters() {
        selectedFilterIndex = -1;
        onFiltersChanged();
    }

    public void setTransitionOffset(int offset) {
        this.transitionOffset = offset;
        setTranslationX(0);
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private static class SearchFilterView extends FrameLayout {

        Drawable thumbDrawable;
        BackupImageView avatarImageView;
        ImageView closeIconView;
        TextView titleView;
        FiltersView.MediaFilterData data;
        ShapeDrawable shapeDrawable;

        private boolean selectedForDelete;
        private float selectedProgress;
        ValueAnimator selectAnimator;

        Runnable removeSelectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedForDelete) {
                    setSelectedForDelete(false);
                }
            }
        };

        private final Theme.ResourcesProvider resourcesProvider;

        public SearchFilterView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(32, 32));

            closeIconView = new ImageView(context);
            closeIconView.setImageResource(R.drawable.ic_close_white);
            addView(closeIconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 38, 0, 16, 0));
            shapeDrawable = (ShapeDrawable) Theme.createRoundRectDrawable(AndroidUtilities.dp(28), 0xFF446F94);
            setBackground(shapeDrawable);
            updateColors();
        }

        private void updateColors() {
            int defaultBackgroundColor = getThemedColor(Theme.key_groupcreate_spanBackground);
            int selectedBackgroundColor = getThemedColor(Theme.key_avatar_backgroundBlue);
            int textDefaultColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
            int textSelectedColor = getThemedColor(Theme.key_avatar_actionBarIconBlue);
            shapeDrawable.getPaint().setColor(ColorUtils.blendARGB(defaultBackgroundColor, selectedBackgroundColor, selectedProgress));
            titleView.setTextColor(ColorUtils.blendARGB(textDefaultColor, textSelectedColor, selectedProgress));
            closeIconView.setColorFilter(textSelectedColor);

            closeIconView.setAlpha(selectedProgress);
            closeIconView.setScaleX(0.82f * selectedProgress);
            closeIconView.setScaleY(0.82f * selectedProgress);

            if (thumbDrawable != null) {
                Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundBlue), false);
                Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
            }
            avatarImageView.setAlpha(1f - selectedProgress);

            if (data != null && (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE)) {
                setData(data);
            }
            invalidate();
        }

        public void setData(FiltersView.MediaFilterData data) {
            this.data = data;
            titleView.setText(data.title);
            thumbDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), data.iconResFilled);
            Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundBlue), false);
            Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
            if (data.filterType == FiltersView.FILTER_TYPE_CHAT) {
                if (data.chat instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) data.chat;
                    if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id == user.id) {
                        CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), R.drawable.chats_saved);
                        combinedDrawable.setIconSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                        Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_backgroundSaved), false);
                        Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                        avatarImageView.setImageDrawable(combinedDrawable);
                    } else {
                        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(16));
                        avatarImageView.getImageReceiver().setForUserOrChat(user, thumbDrawable);
                    }
                } else if (data.chat instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) data.chat;
                    avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(16));
                    avatarImageView.getImageReceiver().setForUserOrChat(chat, thumbDrawable);
                }
            } else if (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
                CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), R.drawable.chats_archive);
                combinedDrawable.setIconSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_backgroundArchived), false);
                Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                avatarImageView.setImageDrawable(combinedDrawable);
            } else {
                avatarImageView.setImageDrawable(thumbDrawable);
            }
        }

        public void setExpanded(boolean expanded) {
            if (expanded) {
                titleView.setVisibility(View.VISIBLE);
            } else {
                titleView.setVisibility(View.GONE);
                setSelectedForDelete(false);
            }
        }

        public void setSelectedForDelete(boolean select) {
            if (selectedForDelete == select) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(removeSelectionRunnable);
            selectedForDelete = select;
            if (selectAnimator != null) {
                selectAnimator.removeAllListeners();
                selectAnimator.cancel();
            }
            selectAnimator = ValueAnimator.ofFloat(selectedProgress, select ? 1f : 0f);
            selectAnimator.addUpdateListener(valueAnimator -> {
                selectedProgress = (float) valueAnimator.getAnimatedValue();
                updateColors();
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    selectedProgress = select ? 1f : 0f;
                    updateColors();
                }
            });
            selectAnimator.setDuration(150).start();
            if (selectedForDelete) {
                AndroidUtilities.runOnUIThread(removeSelectionRunnable, 2000);
            }
        }

        public FiltersView.MediaFilterData getFilter() {
            return data;
        }

        private int getThemedColor(String key) {
            Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
            return color != null ? color : Theme.getColor(key);
        }
    }
}
