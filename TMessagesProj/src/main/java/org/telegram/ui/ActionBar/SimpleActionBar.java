package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.utils.glass.source.GlassSourceCrossfade;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

/**
 * Lightweight header for the "simple glass" screens (settings-like lists under a progressive blur).
 * It mirrors the commonly used part of the {@link ActionBar} API: back button, centered title and
 * subtitle, menu with popup sub items, search and action mode. Buttons sit on glass pills; the
 * menu pill morphs into the search field and into the action mode pill.
 */
public class SimpleActionBar extends FrameLayout implements FactorAnimator.Target, NotificationCenter.NotificationCenterDelegate, Theme.Colorable, MenuOverlayHost {
    static final long DURATION = 380;

    private static final int ANIMATOR_SEARCH = 0;
    private static final int ANIMATOR_ACTION_MODE = 1;
    private static final int ANIMATOR_SEARCH_CLEAR = 2;

    final Theme.ResourcesProvider resourcesProvider;

    private final BackButtonView backButton;
    private final TitleLayout titleLayout;
    private SimpleActionBarMenu menu;
    private SimpleActionBarMenu actionMode;
    private AnimatedTextView actionModeTitle;
    private AnimatedTextView actionModeSubtitle;
    private SimpleActionBarDoneButton doneButton;

    private FrameLayout searchLayout;
    private EditTextBoldCursor searchField;
    private ImageView searchClearButton;
    private TextView searchCaptionView;
    private SimpleActionBarMenuItem searchItem;
    private CharSequence searchFieldHint;
    private CharSequence pendingSearchFieldText;
    private boolean searchFieldVisible;

    private boolean actionModeVisible;

    private BlurredBackgroundDrawable glassBack;
    private BlurredBackgroundDrawable glassMenu;
    private BlurredBackgroundDrawable glassDone;
    private float glassMenuRadius = -1;
    private BlurredBackgroundDrawableViewFactory glassFactory;
    private BlurredBackgroundColorProvider glassColorProvider;
    /** The glass of the buttons over a colored header (a profile); the menus stay on {@link #glassColorProvider}. */
    @Nullable
    private BlurredBackgroundColorProvider buttonsGlassColorProvider;
    /** The glass of the buttons when they have their own source, e.g. made like the buttons of a profile. */
    @Nullable
    private BlurredBackgroundDrawableViewFactory buttonsGlassFactory;

    private Utilities.Callback0Return<SimpleActionBarMenuOverlay> menuOverlayProvider;
    private SimpleActionBarMenuOverlay openMenuOverlay;
    private ActionBarMenuItem openMenuItem;

    private final BoolAnimator searchAnimator = new BoolAnimator(ANIMATOR_SEARCH, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION);
    private final BoolAnimator actionModeAnimator = new BoolAnimator(ANIMATOR_ACTION_MODE, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION);
    private final BoolAnimator searchClearAnimator = new BoolAnimator(ANIMATOR_SEARCH_CLEAR, this, CubicBezierInterpolator.EASE_OUT_QUINT, 250);

    private ActionBar.ActionBarMenuOnItemClick actionBarMenuOnItemClick;

    private boolean occupyStatusBar = true;
    private int extraHeight;
    private boolean animationsAllowed;
    private boolean attached;

    private int titleColor, subtitleColor, itemsColor, itemsActionModeColor, itemsBackgroundColor, itemsActionModeBackgroundColor;
    private boolean hasCustomTitleColor, hasCustomSubtitleColor, hasCustomItemsColor, hasCustomItemsActionModeColor, hasCustomItemsBackgroundColor, hasCustomItemsActionModeBackgroundColor;

    public SimpleActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);
        setWillNotDraw(false);
        resolveColors();

        titleLayout = new TitleLayout(context);
        addView(titleLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        backButton = new BackButtonView(context);
        backButton.setVisibility(GONE);
        backButton.setContentDescription(LocaleController.getString(R.string.AccDescrGoBack));
        backButton.setOnClickListener(v -> {
            if (!actionModeVisible && searchFieldVisible) {
                closeSearchField();
                return;
            }
            onItemClick(-1);
        });
        addView(backButton, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.LEFT));
    }

    /* Glass */

    public void setupGlass(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        glassFactory = factory;
        glassColorProvider = colorProvider;
        createButtonsGlass();
    }

    private void createButtonsGlass() {
        final BlurredBackgroundDrawableViewFactory factory = buttonsGlassFactory != null ? buttonsGlassFactory : glassFactory;
        if (factory == null) {
            return;
        }
        final BlurredBackgroundColorProvider buttonsColors = buttonsGlassColorProvider != null ? buttonsGlassColorProvider : glassColorProvider;
        glassBack = factory.create(this).setColorProvider(buttonsColors).setRadius(dp(23)).setPadding(dp(6));
        glassMenu = factory.create(this).setColorProvider(buttonsColors).setRadius(dp(23)).setPadding(dp(6));
        glassDone = factory.create(this).setColorProvider(buttonsColors).setRadius(dp(23)).setPadding(dp(6));
        glassMenuRadius = dp(23);
        invalidate();
    }

    /**
     * The back, the done and the menu buttons on glass of their own source, set before {@link #setupGlass};
     * {@link #setMenuGlass} puts the menus on it too, so that they start as the button.
     */
    public void setButtonsGlass(@Nullable BlurredBackgroundDrawableViewFactory factory) {
        buttonsGlassFactory = factory;
        if (glassFactory != null) {
            createButtonsGlass();
        }
    }

    /**
     * The glass color of the back, the done and the menu buttons, e.g. tinted like the content of a colored header.
     * Call {@link #updateButtonsGlassColors()} when its colors change.
     */
    public void setButtonsGlassColorProvider(@Nullable BlurredBackgroundColorProvider provider) {
        buttonsGlassColorProvider = provider;
        final BlurredBackgroundColorProvider colors = provider != null ? provider : glassColorProvider;
        if (glassBack != null) {
            glassBack.setColorProvider(colors);
            glassMenu.setColorProvider(colors);
            glassDone.setColorProvider(colors);
        }
        invalidate();
    }

    public void updateButtonsGlassColors() {
        if (glassBack != null) {
            glassBack.updateColors();
            glassMenu.updateColors();
            glassDone.updateColors();
        }
        invalidate();
        if (openMenuOverlay != null) {
            openMenuOverlay.invalidate();
        }
    }

    /** The glass color of the buttons, a menu growing out of the pill starts with it. */
    @Override
    public int getButtonsGlassBackgroundColor() {
        final BlurredBackgroundColorProvider colors = buttonsGlassColorProvider != null ? buttonsGlassColorProvider : glassColorProvider;
        return colors != null ? colors.getBackgroundColor() : 0;
    }

    @Nullable
    @Override
    public BlurredBackgroundColorProvider getMenuGlassColorProvider() {
        return glassColorProvider;
    }

    @Nullable
    @Override
    public BlurredBackgroundColorProvider getButtonsGlassColorProvider() {
        return buttonsGlassColorProvider != null ? buttonsGlassColorProvider : glassColorProvider;
    }

    private boolean menuGlassStroke = true;

    /** Without the stroke of the menus' glass, e.g. over a colored header, where it would vanish at once on closing. */
    public void setMenuGlassStroke(boolean stroke) {
        menuGlassStroke = stroke;
    }

    @Override
    public boolean hasMenuGlassStroke() {
        return menuGlassStroke;
    }

    @Nullable
    private BlurredBackgroundDrawableViewFactory menuGlassFactory;
    @Nullable
    private GlassSourceCrossfade menuGlassSource;

    /**
     * The glass of the menus: the glass of the buttons going over to frosted glass by {@code source}'s progress,
     * see {@link #setMenuGlassFrostedProgress}; without a source the menus are on the glass of the buttons.
     */
    public void setMenuGlass(@Nullable BlurredBackgroundDrawableViewFactory factory, @Nullable GlassSourceCrossfade source) {
        menuGlassFactory = factory;
        menuGlassSource = source;
    }

    /** 0 while a menu is the button's glass, 1 once it is open: its frosted glass. */
    @Override
    public void setMenuGlassFrostedProgress(float progress) {
        if (menuGlassSource != null) {
            menuGlassSource.setProgress(progress);
        }
    }

    /** A new glass drawable of a menu owned by {@code owner}, like the ones of the buttons; null without glass. */
    @Nullable
    @Override
    public BlurredBackgroundDrawable createMenuGlass(View owner) {
        final BlurredBackgroundDrawableViewFactory factory = menuGlassFactory != null ? menuGlassFactory : glassFactory;
        if (factory == null) {
            return null;
        }
        return factory.create(owner).setColorProvider(glassColorProvider).setRadius(dp(23)).setPadding(dp(6));
    }

    /* Menu overlay */

    /** Where the menus open: the overlay of the fragment, see {@link BaseFragment#getSimpleMenuOverlay()}. */
    public void setMenuOverlayProvider(Utilities.Callback0Return<SimpleActionBarMenuOverlay> provider) {
        menuOverlayProvider = provider;
    }

    @Nullable
    SimpleActionBarMenuOverlay getMenuOverlay() {
        return menuOverlayProvider != null ? menuOverlayProvider.run() : null;
    }

    /** The overlay draws the glass of {@code item} while its menu is shown; null when it has closed. */
    @Override
    public void setMenuOverlay(@Nullable SimpleActionBarMenuOverlay overlay, @Nullable ActionBarMenuItem item) {
        openMenuOverlay = overlay;
        openMenuItem = item;
        invalidate();
    }

    /** The overlay showing a menu of this bar, opened or closing. */
    @Nullable
    @Override
    public Theme.ResourcesProvider getMenuResourcesProvider() {
        return resourcesProvider;
    }

    @Nullable
    @Override
    public SimpleActionBarMenuOverlay getOpenMenuOverlay() {
        return openMenuOverlay;
    }

    private boolean isMenuPillInOverlay() {
        return openMenuItem != null && openMenuItem.getParent() == menu;
    }

    /** The menu pill with its buttons, drawn by the overlay while a menu grows out of it. */
    @Override
    public View getMenuView() {
        return menu;
    }

    public boolean isMenuOverlayOpened() {
        return openMenuOverlay != null && openMenuOverlay.isOpened();
    }

    public void closeMenuOverlay(boolean animated) {
        if (openMenuOverlay != null) {
            openMenuOverlay.close(animated);
        }
    }

    /**
     * The glass under {@code item} in this bar's coordinates. Returns true when it is the menu pill:
     * the menu then grows out of the whole pill, and the bar leaves the pill and its buttons to the
     * overlay while the menu is shown. Otherwise (an action mode item) it is the item's circle.
     */
    @Override
    public boolean getMenuItemGlassRect(ActionBarMenuItem item, RectF out) {
        if (item.getParent() == menu && getSearchFactor() == 0 && getActionModeFactor() == 0) {
            computeMenuPill(out);
            return true;
        }
        final float half = dp(23);
        final View itemMenu = (View) item.getParent();
        final float cx = (itemMenu != null ? itemMenu.getX() : 0) + item.getX() + item.getWidth() / 2f;
        final float cy = getRowTop() + ActionBar.getCurrentActionBarHeight() / 2f;
        out.set(cx - half, cy - half, cx + half, cy + half);
        return false;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        animationsAllowed = attached;

        final int p = dp(6);

        if (glassBack != null && backButton.getVisibility() == VISIBLE && backButton.getAlpha() > 0) {
            final float cx = backButton.getX() + backButton.getWidth() / 2f, cy = backButton.getY() + backButton.getHeight() / 2f;
            final float hw = backButton.getWidth() / 2f * backButton.getScaleX(), hh = backButton.getHeight() / 2f * backButton.getScaleY();
            glassBack.setBounds((int) (cx - hw) - p, (int) (cy - hh) - p, (int) (cx + hw) + p, (int) (cy + hh) + p);
            glassBack.setAlpha((int) (255 * backButton.getAlpha()));
            glassBack.draw(canvas);
        }

        if (glassDone != null && doneButton != null && doneButton.drawsGlass() && doneButton.getVisibility() == VISIBLE && doneButton.getAlpha() > 0) {
            final float cx = doneButton.getX() + doneButton.getWidth() / 2f, cy = doneButton.getY() + doneButton.getHeight() / 2f;
            final float hw = doneButton.getWidth() / 2f * doneButton.getScaleX(), hh = doneButton.getHeight() / 2f * doneButton.getScaleY();
            glassDone.setBounds((int) (cx - hw) - p, (int) (cy - hh) - p, (int) (cx + hw) + p, (int) (cy + hh) + p);
            glassDone.setAlpha((int) (255 * doneButton.getAlpha()));
            glassDone.draw(canvas);
        }

        if (glassMenu != null) {
            final float alpha = computeMenuPill(menuPillRect);
            // a menu growing out of the pill takes over its glass
            final boolean pillInOverlay = isMenuPillInOverlay();
            if (alpha > 0 && menuPillRect.width() > 0 && !pillInOverlay) {
                final float radius = menuPillRect.height() / 2f;
                if (Math.abs(radius - glassMenuRadius) > 0.5f) {
                    glassMenuRadius = radius;
                    glassMenu.setRadius(radius);
                }
                glassMenu.setBounds((int) menuPillRect.left - p, (int) menuPillRect.top - p, (int) menuPillRect.right + p, (int) menuPillRect.bottom + p);
                glassMenu.setAlpha((int) (255 * alpha));
                glassMenu.draw(canvas);
            }
        }

        super.dispatchDraw(canvas);
    }

    private final RectF menuPillRect = new RectF();

    /** Where the glass of the right pill starts. */
    public float getMenuPillLeft() {
        computeMenuPill(menuPillRect);
        return menuPillRect.left;
    }

    /**
     * Visible bounds of the right pill: the menu or the action mode buttons, morphing into the search field.
     * Returns the pill alpha; a pill narrower than a single button shrinks as a circle.
     */
    private float computeMenuPill(RectF rect) {
        final int p = dp(6);
        final float half = dp(23);
        final float cy = getRowTop() + ActionBar.getCurrentActionBarHeight() / 2f;
        final float s = getSearchFactor();
        final float a = getActionModeFactor();
        final float menuWidth = menu != null && menu.getVisibility() != GONE ? menu.getPillWidth() : 0;
        final float modeWidth = actionMode != null ? actionMode.getPillWidth() : 0;
        final float width;
        final float fade;
        if (menuWidth <= 0) {
            // no regular menu: the action mode pill fades in place together with its buttons
            width = modeWidth;
            fade = a;
        } else if (modeWidth <= 0) {
            width = menuWidth;
            fade = 1f - a;
        } else {
            width = lerp(menuWidth, modeWidth, a);
            fade = 1f;
        }
        final float sk = s * (1f - a);
        // a pill narrower than a button shrinks as a circle; a fading pill shrinks a little
        final float k = Math.min(1f, width / (half * 2)) * lerp(0.8f, 1f, fade);

        float right = getWidth() - p - getDoneButtonSpace(false);
        float left = right - Math.max(width, half * 2);
        float top = cy - half, bottom = cy + half;
        if (k < 1f) {
            final float cx = (left + right) / 2f;
            final float hw = (right - left) / 2f - half * (1f - k);
            left = cx - hw;
            right = cx + hw;
            top = cy - half * k;
            bottom = cy + half * k;
        }
        rect.set(
            lerp(left, getSearchPillLeft(), sk),
            lerp(top, cy - half, sk),
            lerp(right, getWidth() - p, sk),
            lerp(bottom, cy + half, sk)
        );
        return lerp(Math.min(1f, width / (half * 2)) * fade, 1f, sk);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == menu && isMenuPillInOverlay()) {
            return false; // the overlay draws the buttons inside the menu growing out of their pill
        }
        if (child == searchLayout && getSearchFactor() < 1f) {
            computeMenuPill(menuPillRect);
            canvas.save();
            canvas.clipRect(menuPillRect);
            final boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private float getSearchPillLeft() {
        return hasBackButton() ? dp(6 + 46 + 6) : dp(6);
    }

    /** Without a back button the close button appears only in action mode. */
    private boolean hasBackButton() {
        return backButton.drawable != null;
    }

    boolean isAnimationsAllowed() {
        return animationsAllowed && attached;
    }

    /* Layout */

    public void setOccupyStatusBar(boolean value) {
        if (occupyStatusBar != value) {
            occupyStatusBar = value;
            requestLayout();
        }
    }

    public boolean getOccupyStatusBar() {
        return occupyStatusBar;
    }

    public void setExtraHeight(int value) {
        if (extraHeight != value) {
            extraHeight = value;
            requestLayout();
        }
    }

    public int getExtraHeight() {
        return extraHeight;
    }

    private int titleMinSideInset;

    /** The centered title keeps this far from both edges, e.g. clear of a custom child at the right (a balance). */
    public void setTitleMinSideInset(int value) {
        if (titleMinSideInset != value) {
            titleMinSideInset = value;
            requestLayout();
        }
    }

    private int getRowTop() {
        return occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int rowTop = getRowTop();
        final int rowHeight = ActionBar.getCurrentActionBarHeight();
        final int height = rowTop + rowHeight + extraHeight;
        final boolean hasBack = hasBackButton();

        final int buttonTop = rowTop + (rowHeight - dp(46)) / 2;
        setMargins(backButton, dp(6), buttonTop, 0);

        final float menuWidth = menu != null && menu.getVisibility() != GONE ? menu.getTargetPillWidth() : 0;
        final float doneSpace = getDoneButtonSpace(true);
        int titleSide = Math.max(hasBack ? dp(64) : dp(18), menuWidth > 0 || doneSpace > 0 ? (int) (menuWidth + doneSpace) + dp(6 + 12) : 0);
        titleSide = Math.max(titleSide, titleMinSideInset);
        titleLayout.setSideInset(titleSide);
        setMargins(titleLayout, 0, rowTop, 0);
        titleLayout.getLayoutParams().height = rowHeight;

        if (menu != null) {
            setMargins(menu, 0, rowTop, dp(10));
            menu.getLayoutParams().height = rowHeight;
        }
        if (actionMode != null) {
            setMargins(actionMode, 0, rowTop, dp(10));
            actionMode.getLayoutParams().height = rowHeight;
        }
        if (actionModeTitle != null) {
            final float modeWidth = actionMode != null ? actionMode.getTargetPillWidth() : 0;
            final int right = (int) modeWidth + dp(6 + 12);
            if (hasActionModeSubtitle()) {
                setMargins(actionModeTitle, dp(66), rowTop + rowHeight / 2 - dp(22), right);
                actionModeTitle.getLayoutParams().height = dp(22);
                setMargins(actionModeSubtitle, dp(66), rowTop + rowHeight / 2 + dp(1), right);
                actionModeSubtitle.getLayoutParams().height = dp(18);
            } else {
                setMargins(actionModeTitle, dp(66), rowTop, right);
                actionModeTitle.getLayoutParams().height = rowHeight;
            }
        }
        if (searchLayout != null) {
            setMargins(searchLayout, (int) getSearchPillLeft(), buttonTop, dp(6));
        }
        if (doneButton != null) {
            setMargins(doneButton, 0, buttonTop, dp(6));
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private boolean isOwnChild(View child) {
        return child == backButton || child == titleLayout || child == menu || child == actionMode || child == actionModeTitle || child == actionModeSubtitle || child == searchLayout || child == doneButton;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // custom children (buttons added by screens) are centered in the header row, below the status bar
        final int rowTop = getRowTop();
        final int rowHeight = ActionBar.getCurrentActionBarHeight();
        final int width = right - left;
        for (int i = 0, N = getChildCount(); i < N; i++) {
            final View child = getChildAt(i);
            if (isOwnChild(child) || child.getVisibility() == GONE) {
                continue;
            }
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            final int horizontal = Gravity.getAbsoluteGravity(lp.gravity, getLayoutDirection()) & Gravity.HORIZONTAL_GRAVITY_MASK;
            final int l;
            if (horizontal == Gravity.RIGHT) {
                l = width - w - lp.rightMargin;
            } else if (horizontal == Gravity.CENTER_HORIZONTAL) {
                l = (width - w) / 2 + lp.leftMargin - lp.rightMargin;
            } else {
                l = lp.leftMargin;
            }
            final int t = rowTop + (rowHeight - h) / 2 + lp.topMargin - lp.bottomMargin;
            child.layout(l, t, l + w, t + h);
        }
    }

    private static void setMargins(View view, int left, int top, int right) {
        final MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.rightMargin = right;
    }

    private boolean forceSkipTouches;

    /** Lets touches that miss the buttons through to the content below, e.g. a collapsing header. */
    public void setForceSkipTouches(boolean value) {
        forceSkipTouches = value;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return !forceSkipTouches;
    }

    /* Colors */

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private void resolveColors() {
        if (!hasCustomTitleColor) titleColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        if (!hasCustomSubtitleColor) subtitleColor = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
        if (!hasCustomItemsColor) itemsColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        if (!hasCustomItemsActionModeColor) itemsActionModeColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        if (!hasCustomItemsBackgroundColor) itemsBackgroundColor = getThemedColor(Theme.key_listSelector);
        if (!hasCustomItemsActionModeBackgroundColor) itemsActionModeBackgroundColor = getThemedColor(Theme.key_listSelector);
    }

    @Override
    public void updateColors() {
        resolveColors();
        titleLayout.updateColors();
        backButton.updateColors();
        if (menu != null) {
            menu.updateItemsColors();
        }
        if (actionMode != null) {
            actionMode.updateItemsColors();
        }
        if (actionModeTitle != null) {
            actionModeTitle.setTextColor(itemsActionModeColor);
        }
        if (doneButton != null) {
            doneButton.updateColors();
        }
        if (actionModeSubtitle != null) {
            actionModeSubtitle.setTextColor(subtitleColor);
        }
        if (searchField != null) {
            updateSearchColors();
        }
        if (glassBack != null) {
            glassBack.updateColors();
        }
        if (glassMenu != null) {
            glassMenu.updateColors();
        }
        invalidate();
    }

    public void setTitleColor(int color) {
        hasCustomTitleColor = true;
        titleColor = color;
        titleLayout.updateColors();
    }

    public void setSubtitleColor(int color) {
        hasCustomSubtitleColor = true;
        subtitleColor = color;
        titleLayout.updateColors();
    }

    public void setItemsColor(int color, boolean isActionMode) {
        if (isActionMode) {
            hasCustomItemsActionModeColor = true;
            itemsActionModeColor = color;
        } else {
            hasCustomItemsColor = true;
            itemsColor = color;
        }
        updateColors();
    }

    public void setItemsBackgroundColor(int color, boolean isActionMode) {
        if (isActionMode) {
            hasCustomItemsActionModeBackgroundColor = true;
            itemsActionModeBackgroundColor = color;
        } else {
            hasCustomItemsBackgroundColor = true;
            itemsBackgroundColor = color;
        }
        updateColors();
    }

    int getItemsColor(boolean isActionMode) {
        return isActionMode ? itemsActionModeColor : itemsColor;
    }

    int getItemsBackgroundColor(boolean isActionMode) {
        return isActionMode ? itemsActionModeBackgroundColor : itemsBackgroundColor;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        animationsAllowed = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        }
    }

    /* Back button */

    public void setBackButtonImage(int resId) {
        setBackButtonDrawable(resId != 0 ? ContextCompat.getDrawable(getContext(), resId).mutate() : null);
    }

    public void setBackButtonDrawable(@Nullable Drawable drawable) {
        backButton.setDrawable(drawable);
        applyFactors();
        requestLayout();
    }

    /** Changes the icon of the back button with a shrink/grow animation; the glass circle stays in place. */
    public void setBackButtonImageAnimated(int resId) {
        final Drawable drawable = ContextCompat.getDrawable(getContext(), resId).mutate();
        final boolean hadBack = hasBackButton();
        backButton.replaceDrawable(drawable);
        if (!hadBack) {
            applyFactors();
            requestLayout();
        }
    }

    public Drawable getBackButtonDrawable() {
        return backButton.drawable;
    }

    public View getBackButton() {
        return backButton;
    }

    public void setBackButtonContentDescription(CharSequence description) {
        backButton.setContentDescription(description);
    }

    /* Title */

    public void setTitle(CharSequence value) {
        titleLayout.setTitle(value);
    }

    public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration) {
        setTitleAnimated(title, fromBottom, duration, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration, Interpolator interpolator) {
        if (!isAnimationsAllowed()) {
            setTitle(title);
            return;
        }
        titleLayout.setTitleAnimated(title, fromBottom, duration, interpolator);
    }

    public String getTitle() {
        final CharSequence title = titleLayout.titleViews[0] != null ? titleLayout.titleViews[0].getText() : null;
        return title != null ? title.toString() : null;
    }

    public SimpleTextView getTitleTextView() {
        return titleLayout.titleViews[0];
    }

    /** Second title view, used by {@link #setTitleAnimated}; may be null. */
    public SimpleTextView getTitleTextView2() {
        return titleLayout.titleViews[1];
    }

    public Paint.FontMetricsInt getTitleFontMetricsInt() {
        titleLayout.createTitleView(0);
        return titleLayout.titleViews[0].getTextPaint().getFontMetricsInt();
    }

    public void setSubtitle(CharSequence value) {
        titleLayout.setSubtitle(value);
    }

    /** Changes a shown subtitle with a short slide and fade, e.g. a counter; shows or hides it at once. */
    public void setSubtitleAnimated(CharSequence value) {
        if (!isAnimationsAllowed()) {
            setSubtitle(value);
            return;
        }
        titleLayout.setSubtitleAnimated(value);
    }

    public String getSubtitle() {
        return titleLayout.subtitle != null ? titleLayout.subtitle.toString() : null;
    }

    public SimpleTextView getSubtitleTextView() {
        return titleLayout.subtitleView;
    }

    private float titlesAlpha = 1f;

    /** Extra alpha of the title and the subtitle, multiplied by the search and action mode fade. */
    public void setTitlesAlpha(float alpha) {
        if (titlesAlpha != alpha) {
            titlesAlpha = alpha;
            applyFactors();
        }
    }

    public float getTitlesAlpha() {
        return titlesAlpha;
    }

    /* Menu */

    public void setActionBarMenuOnItemClick(ActionBar.ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public ActionBar.ActionBarMenuOnItemClick getActionBarMenuOnItemClick() {
        return actionBarMenuOnItemClick;
    }

    void onItemClick(int id) {
        if (actionBarMenuOnItemClick != null) {
            actionBarMenuOnItemClick.onItemClick(id);
        }
    }

    boolean canOpenMenu() {
        return actionBarMenuOnItemClick == null || actionBarMenuOnItemClick.canOpenMenu();
    }

    public SimpleActionBarMenu createMenu() {
        if (menu == null) {
            menu = new SimpleActionBarMenu(getContext(), this, false);
            addView(menu, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));
            applyFactors();
        }
        return menu;
    }

    public SimpleActionBarMenu getMenu() {
        return menu;
    }

    /* Done button */

    /**
     * Creates the confirm button right of the menu. Its color and shape follow the experimental
     * settings; the text ("Save", "Create", "Apply") is shown by the text shape and read by TalkBack.
     * A click on an active button calls the menu listener with {@code id}.
     */
    public SimpleActionBarDoneButton createDoneButton(int id, CharSequence text) {
        if (doneButton == null) {
            doneButton = new SimpleActionBarDoneButton(getContext(), this, id, text);
            addView(doneButton, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 46, Gravity.TOP | Gravity.RIGHT));
            applyFactors();
        }
        return doneButton;
    }

    public SimpleActionBarDoneButton getDoneButton() {
        return doneButton;
    }

    /** Removes the done button, e.g. to create it again with another style. */
    public void removeDoneButton() {
        if (doneButton != null) {
            removeView(doneButton);
            doneButton = null;
            applyFactors();
            requestLayout();
        }
    }

    void onDoneButtonFactorChanged() {
        applyFactors();
    }

    /** Width the done button takes from the right edge, including the gap before the menu pill. */
    private float getDoneButtonSpace(boolean target) {
        if (doneButton == null || doneButton.getVisibility() == GONE && target) {
            return 0;
        }
        final float visible = target ? doneButton.getTargetVisibleFactor() : doneButton.getVisibleFactor() * (1f - getSearchFactor()) * (1f - getActionModeFactor());
        return (doneButton.getButtonWidth() + dp(12)) * visible;
    }

    public void onMenuButtonPressed() {
        if (!actionModeVisible && menu != null) {
            menu.onMenuButtonPressed();
        }
    }

    /* Action mode */

    public SimpleActionBarMenu createActionMode() {
        if (actionMode == null) {
            actionMode = new SimpleActionBarMenu(getContext(), this, true);
            addView(actionMode, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));

            actionModeTitle = new AnimatedTextView(getContext(), true, true, true);
            actionModeTitle.setTextSize(dp(18));
            actionModeTitle.setTypeface(AndroidUtilities.bold());
            actionModeTitle.setTextColor(itemsActionModeColor);
            actionModeTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            actionModeTitle.setEllipsizeByGradient(true);
            actionModeTitle.setAnimationProperties(0.6f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            addView(actionModeTitle, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
            applyFactors();
        }
        return actionMode;
    }

    public SimpleActionBarMenu getActionMode() {
        return actionMode;
    }

    public boolean actionModeIsExist() {
        return actionMode != null;
    }

    public AnimatedTextView getActionModeTitle() {
        createActionMode();
        return actionModeTitle;
    }

    public void setActionModeTitle(CharSequence title, boolean animated) {
        createActionMode();
        actionModeTitle.setText(title, animated && isAnimationsAllowed() && actionModeVisible);
    }

    /** Second line under the action mode title, e.g. the number of the selected files; empty to hide. */
    public void setActionModeSubtitle(CharSequence subtitle, boolean animated) {
        createActionMode();
        if (actionModeSubtitle == null) {
            if (TextUtils.isEmpty(subtitle)) {
                return;
            }
            actionModeSubtitle = new AnimatedTextView(getContext(), true, true, true);
            actionModeSubtitle.setTextSize(dp(14));
            actionModeSubtitle.setTextColor(subtitleColor);
            actionModeSubtitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            actionModeSubtitle.setEllipsizeByGradient(true);
            actionModeSubtitle.setAnimationProperties(0.35f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            addView(actionModeSubtitle, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT));
            applyFactors();
        }
        final boolean hadSubtitle = hasActionModeSubtitle();
        actionModeSubtitle.setText(subtitle, animated && isAnimationsAllowed() && actionModeVisible);
        actionModeSubtitle.setTag(TextUtils.isEmpty(subtitle) ? null : 1);
        if (hadSubtitle != hasActionModeSubtitle()) {
            requestLayout();
        }
    }

    private boolean hasActionModeSubtitle() {
        return actionModeSubtitle != null && actionModeSubtitle.getTag() != null;
    }

    public void setActionModeCount(int count, boolean animated) {
        setActionModeTitle(LocaleController.formatNumber(count, ' '), animated);
    }

    public void showActionMode() {
        showActionMode(true);
    }

    public void showActionMode(boolean animated) {
        if (actionMode == null || actionModeVisible) {
            return;
        }
        actionModeVisible = true;
        if (menu != null) {
            menu.hideAllPopupMenus();
        }
        if (searchField != null && searchFieldVisible) {
            AndroidUtilities.hideKeyboard(searchField);
        }
        actionModeAnimator.setValue(true, animated && isAnimationsAllowed());
        applyFactors();
        backButton.setActionMode(true, animated && isAnimationsAllowed());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
    }

    public void hideActionMode() {
        if (actionMode == null || !actionModeVisible) {
            return;
        }
        actionModeVisible = false;
        actionMode.hideAllPopupMenus();
        actionModeAnimator.setValue(false, isAnimationsAllowed());
        applyFactors();
        backButton.setActionMode(false, isAnimationsAllowed());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
    }

    public boolean isActionModeShowed() {
        return actionMode != null && actionModeVisible;
    }

    public float getActionModeFactor() {
        return actionModeAnimator.getFloatValue();
    }

    /* Search */

    void setSearchItem(@Nullable SimpleActionBarMenuItem item) {
        if (item == null && searchItem != null && searchFieldVisible) {
            closeSearchField(true);
        }
        searchItem = item;
    }

    private ActionBarMenuItem.ActionBarMenuItemSearchListener getSearchListener() {
        return searchItem != null ? searchItem.getSearchListener() : null;
    }

    public float getSearchFactor() {
        return searchAnimator.getFloatValue();
    }

    public boolean isSearchFieldVisible() {
        return searchFieldVisible;
    }

    public void setSearchFieldHint(CharSequence hint) {
        searchFieldHint = hint;
        if (searchField != null) {
            searchField.setHint(hint);
        }
    }

    public void setSearchFieldText(CharSequence text) {
        if (searchField == null) {
            pendingSearchFieldText = text;
            return;
        }
        searchField.setText(text);
        if (!TextUtils.isEmpty(text)) {
            searchField.setSelection(text.length());
        }
    }

    public EditTextBoldCursor getSearchField() {
        createSearchLayout();
        return searchField;
    }

    public void openSearchField(boolean animated) {
        openSearchField(true, animated);
    }

    public void openSearchField(String text, boolean animated) {
        if (openSearchField(true, animated) && text != null) {
            setSearchFieldText(text);
        }
    }

    public boolean openSearchField(boolean openKeyboard, boolean animated) {
        if (searchItem == null || searchFieldVisible || actionModeVisible) {
            return false;
        }
        final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
        if (listener != null) {
            listener.onPreToggleSearch();
            if (!listener.canToggleSearch()) {
                return false;
            }
        }
        createSearchLayout();
        searchFieldVisible = true;
        if (menu != null) {
            menu.hideAllPopupMenus();
        }
        searchField.setText(pendingSearchFieldText != null ? pendingSearchFieldText : "");
        pendingSearchFieldText = null;
        searchField.requestFocus();
        if (openKeyboard) {
            AndroidUtilities.showKeyboard(searchField);
        }
        searchAnimator.setValue(true, animated && isAnimationsAllowed());
        applyFactors();
        if (listener != null) {
            listener.onSearchExpand();
        }
        return true;
    }

    public void closeSearchField() {
        closeSearchField(true);
    }

    public void closeSearchField(boolean closeKeyboard) {
        if (!searchFieldVisible) {
            return;
        }
        final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
        if (listener != null && !listener.canCollapseSearch()) {
            return;
        }
        searchFieldVisible = false;
        searchField.clearFocus();
        if (closeKeyboard) {
            AndroidUtilities.hideKeyboard(searchField);
        }
        searchAnimator.setValue(false, isAnimationsAllowed());
        applyFactors();
        if (listener != null) {
            listener.onSearchCollapse();
        }
    }

    /**
     * Whether a back press stays in the header: it closes an open menu or the search field. A closing
     * menu takes it too: a back gesture whose touch has started closing the menu must not leave the screen.
     */
    public boolean hasBackAction() {
        return openMenuOverlay != null || !actionModeVisible && searchFieldVisible;
    }

    /** Closes an open menu or the search field. Returns true if the back press was consumed. */
    public boolean onBackPressed() {
        if (openMenuOverlay != null) {
            closeMenuOverlay(true);
            return true;
        }
        if (!actionModeVisible && searchFieldVisible) {
            closeSearchField();
            return true;
        }
        return false;
    }

    private void createSearchLayout() {
        if (searchLayout != null) {
            return;
        }
        final Context context = getContext();
        searchLayout = new FrameLayout(context);
        searchLayout.setVisibility(INVISIBLE);
        addView(searchLayout, indexOfChild(backButton), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46, Gravity.TOP | Gravity.LEFT));


        final LinearLayout searchFieldLayout = new LinearLayout(context);
        searchFieldLayout.setOrientation(LinearLayout.HORIZONTAL);
        searchLayout.addView(searchFieldLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 18, 0, 42, 0));

        searchCaptionView = new TextView(context);
        searchCaptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        searchCaptionView.setSingleLine(true);
        searchCaptionView.setEllipsize(TextUtils.TruncateAt.END);
        searchCaptionView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        searchCaptionView.setPadding(0, 0, dp(4), 0);
        searchCaptionView.setVisibility(GONE);
        searchFieldLayout.addView(searchCaptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        searchField = new EditTextBoldCursor(context) {
            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && length() == 0 && isSearchCaptionVisible()) {
                    clearSearchCaption();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        searchField.setSingleLine(true);
        searchField.setBackground(null);
        searchField.setPadding(0, 0, 0, 0);
        searchField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        searchField.setInputType(searchField.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchField.setTextIsSelectable(false);
        searchField.setCursorSize(dp(20));
        searchField.setCursorWidth(1.5f);
        if (searchFieldHint != null) {
            searchField.setHint(searchFieldHint);
        }
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                AndroidUtilities.hideKeyboard(searchField);
                final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
                if (listener != null) {
                    listener.onSearchPressed(searchField);
                }
            }
            return false;
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkSearchClearButton();
                final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
                if (listener != null) {
                    listener.onTextChanged(searchField);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        searchFieldLayout.addView(searchField, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        searchClearButton = new ImageView(context);
        searchClearButton.setScaleType(ImageView.ScaleType.CENTER);
        searchClearButton.setImageResource(R.drawable.ic_close_white);
        searchClearButton.setContentDescription(LocaleController.getString(R.string.ClearButton));
        searchClearButton.setOnClickListener(v -> {
            if (searchField.length() != 0) {
                searchField.setText("");
            } else if (isSearchCaptionVisible()) {
                clearSearchCaption();
            }
            searchField.requestFocus();
            AndroidUtilities.showKeyboard(searchField);
        });
        searchLayout.addView(searchClearButton, LayoutHelper.createFrame(42, 42, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 2, 0));

        updateSearchColors();
        checkSearchClearButton();
        onFactorChanged(ANIMATOR_SEARCH_CLEAR, searchClearAnimator.getFloatValue(), 0, null);
    }

    private void updateSearchColors() {
        searchField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchCaptionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        searchField.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        searchField.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        final PorterDuffColorFilter filter = new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN);
        searchClearButton.setColorFilter(filter);
        searchClearButton.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor, 1));
    }

    /** A non editable prefix of the search query, e.g. the selected color; null to hide. */
    public void setSearchFieldCaption(CharSequence caption) {
        createSearchLayout();
        searchCaptionView.setText(caption);
        searchCaptionView.setVisibility(TextUtils.isEmpty(caption) ? GONE : VISIBLE);
        checkSearchClearButton();
    }

    private boolean isSearchCaptionVisible() {
        return searchCaptionView != null && searchCaptionView.getVisibility() == VISIBLE;
    }

    private void clearSearchCaption() {
        final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
        if (listener != null && !listener.canClearCaption()) {
            return;
        }
        searchCaptionView.setVisibility(GONE);
        checkSearchClearButton();
        if (listener != null) {
            listener.onCaptionCleared();
        }
    }

    private void checkSearchClearButton() {
        if (searchField == null) {
            return;
        }
        final ActionBarMenuItem.ActionBarMenuItemSearchListener listener = getSearchListener();
        final boolean visible = searchField.length() > 0 || isSearchCaptionVisible() && (listener == null || listener.showClearForCaption())
            || listener != null && listener.forceShowClear();
        searchClearAnimator.setValue(visible, isAnimationsAllowed() && searchFieldVisible);
        searchClearButton.setEnabled(visible);
    }

    /* Animations */

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_SEARCH_CLEAR) {
            if (searchClearButton != null) {
                searchClearButton.setAlpha(factor);
                searchClearButton.setScaleX(0.5f + 0.5f * factor);
                searchClearButton.setScaleY(0.5f + 0.5f * factor);
                searchClearButton.setVisibility(factor > 0 ? VISIBLE : INVISIBLE);
            }
            return;
        }
        applyFactors();
    }

    private void applyFactors() {
        final float s = getSearchFactor();
        final float a = getActionModeFactor();
        final float sk = s * (1f - a);

        final float titleAlpha = (1f - s) * (1f - a) * titlesAlpha;
        final float titleScale = lerp(1f, 0.94f, Math.max(s, a));
        setAlphaAndVisibility(titleLayout, titleAlpha);
        titleLayout.setScaleX(titleScale);
        titleLayout.setScaleY(titleScale);

        if (doneButton != null) {
            final float hide = (1f - s) * (1f - a);
            final float visible = doneButton.getVisibleFactor() * hide;
            setAlphaAndVisibility(doneButton, visible);
            final float scale = doneButton.drawsGlass() ? lerp(0.6f, 1f, visible) : lerp(0.8f, 1f, hide);
            doneButton.setScaleX(scale);
            doneButton.setScaleY(scale);
        }
        if (menu != null) {
            menu.setTranslationX(-getDoneButtonSpace(false));
            final float menuAlpha = (1f - s) * (1f - a);
            setAlphaAndVisibility(menu, menuAlpha);
            final float scale = lerp(0.7f, 1f, menuAlpha);
            menu.setPivotX(menu.getWidth() - dp(19));
            menu.setPivotY(menu.getHeight() / 2f);
            menu.setScaleX(scale);
            menu.setScaleY(scale);
        }
        // custom children (screen buttons, avatars) behave like the menu
        final float customAlpha = (1f - s) * (1f - a);
        for (int i = 0, N = getChildCount(); i < N; i++) {
            final View child = getChildAt(i);
            if (!isOwnChild(child) && child.getVisibility() != GONE) {
                setAlphaAndVisibility(child, customAlpha);
            }
        }
        if (actionMode != null) {
            setAlphaAndVisibility(actionMode, a);
            final float scale = lerp(0.7f, 1f, a);
            actionMode.setPivotX(actionMode.getWidth() - dp(19));
            actionMode.setPivotY(actionMode.getHeight() / 2f);
            actionMode.setScaleX(scale);
            actionMode.setScaleY(scale);
        }
        if (actionModeTitle != null) {
            setAlphaAndVisibility(actionModeTitle, a);
            actionModeTitle.setTranslationX(-dp(16) * (1f - a));
        }
        if (actionModeSubtitle != null) {
            setAlphaAndVisibility(actionModeSubtitle, a);
            actionModeSubtitle.setTranslationX(-dp(16) * (1f - a));
        }
        if (searchLayout != null) {
            setAlphaAndVisibility(searchLayout, sk);
            computeMenuPill(menuPillRect);
            searchLayout.setTranslationX(Math.max(0, menuPillRect.left - getSearchPillLeft()));
        }
        if (hasBackButton()) {
            backButton.setVisibility(VISIBLE);
            backButton.setAlpha(1f);
            backButton.setScaleX(1f);
            backButton.setScaleY(1f);
        } else {
            backButton.setVisibility(a > 0 ? VISIBLE : GONE);
            backButton.setAlpha(a);
            backButton.setScaleX(lerp(0.6f, 1f, a));
            backButton.setScaleY(lerp(0.6f, 1f, a));
        }
        backButton.invalidate();
        invalidate();
    }

    private static void setAlphaAndVisibility(View view, float alpha) {
        view.setAlpha(alpha);
        view.setVisibility(alpha > 0 ? VISIBLE : INVISIBLE);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        backButton.setEnabled(enabled);
        if (menu != null) {
            menu.setEnabled(enabled);
        }
        if (doneButton != null) {
            doneButton.setEnabled(enabled);
        }
    }

    /* Back button view: crossfades its icon with a cross in action mode */

    private class BackButtonView extends View implements FactorAnimator.Target {
        private Drawable drawable;
        private Drawable replacedDrawable;
        private final Drawable closeDrawable;
        private final BoolAnimator closeAnimator = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION);
        private final FactorAnimator replaceAnimator = new FactorAnimator(1, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION, 1f);

        BackButtonView(Context context) {
            super(context);
            closeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ab_close).mutate();
            updateColors();
        }

        void setDrawable(Drawable drawable) {
            replaceAnimator.cancel();
            replaceAnimator.forceFactor(1f);
            replacedDrawable = null;
            if (this.drawable != null) {
                this.drawable.setCallback(null);
            }
            this.drawable = drawable;
            if (drawable != null) {
                drawable.setCallback(this);
                if (!actionModeVisible) {
                    closeAnimator.setValue(false, false);
                }
            }
            updateColors();
            invalidate();
        }

        /** The old icon shrinks away and the new one grows in its place, the glass circle stays. */
        void replaceDrawable(Drawable drawable) {
            if (this.drawable == null || !isAnimationsAllowed()) {
                setDrawable(drawable);
                return;
            }
            final Drawable old = this.drawable;
            setDrawable(drawable);
            replacedDrawable = old;
            replaceAnimator.forceFactor(0f);
            replaceAnimator.animateTo(1f);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == drawable || who == replacedDrawable || super.verifyDrawable(who);
        }

        void updateColors() {
            setBackground(Theme.createSelectorDrawable(itemsBackgroundColor, 1));
            if (drawable instanceof BackDrawable) {
                ((BackDrawable) drawable).setColor(itemsColor);
                ((BackDrawable) drawable).setRotatedColor(itemsActionModeColor);
            } else if (drawable instanceof MenuDrawable) {
                ((MenuDrawable) drawable).setIconColor(itemsColor);
            } else if (drawable != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.SRC_IN));
            }
            closeDrawable.setColorFilter(new PorterDuffColorFilter(itemsActionModeColor, PorterDuff.Mode.SRC_IN));
            invalidate();
        }

        void setActionMode(boolean value, boolean animated) {
            if (drawable == null) {
                // the close button is shown alone: it scales in and out with the action mode
                if (value) {
                    closeAnimator.setValue(true, false);
                }
                return;
            }
            if (drawable instanceof BackDrawable) {
                ((BackDrawable) drawable).setRotation(value ? 1 : 0, animated);
                return;
            }
            closeAnimator.setValue(value, animated);
        }

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            invalidate();
        }

        @Override
        public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
            if (id == 1 && finalFactor == 1f) {
                replacedDrawable = null;
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final float f = closeAnimator.getFloatValue();
            final float r = replaceAnimator.getFactor();
            if (replacedDrawable != null && r < 0.5f) {
                drawCentered(canvas, replacedDrawable, cx, cy, (1f - f) * (1f - r * 2f), 0);
            } else if (drawable != null && f < 1f) {
                drawCentered(canvas, drawable, cx, cy, (1f - f) * Math.min(1f, (r - 0.5f) * 2f + (replacedDrawable == null ? 1f : 0f)), -90f * f);
            }
            if (f > 0f) {
                drawCentered(canvas, closeDrawable, cx, cy, f, 90f * (1f - f));
            }
        }

        private void drawCentered(Canvas canvas, Drawable d, float cx, float cy, float alpha, float rotation) {
            int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            if (w <= 0 || h <= 0) {
                w = h = dp(24);
            }
            // some drawables (BackDrawable) ignore their bounds, so the canvas is moved instead
            d.setBounds(0, 0, w, h);
            d.setAlpha((int) (255 * alpha));
            canvas.save();
            canvas.rotate(rotation, cx, cy);
            final float scale = lerp(0.6f, 1f, alpha);
            canvas.scale(scale, scale, cx, cy);
            canvas.translate(cx - w / 2f, cy - h / 2f);
            d.draw(canvas);
            canvas.restore();
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            SimpleActionBar.this.invalidate();
        }

        @Override
        public void setTranslationX(float translationX) {
            super.setTranslationX(translationX);
            SimpleActionBar.this.invalidate();
        }
    }

    /* Title and subtitle, centered between the back button and the menu */

    private class TitleLayout extends FrameLayout {
        final SimpleTextView[] titleViews = new SimpleTextView[2];
        SimpleTextView subtitleView;
        /** The previous subtitle while it slides out, see {@link #setSubtitleAnimated}. */
        SimpleTextView subtitleOutView;
        CharSequence subtitle;
        private int sideInset;

        TitleLayout(Context context) {
            super(context);
            setClipChildren(false);
        }

        void setSideInset(int value) {
            if (sideInset != value) {
                sideInset = value;
                requestLayout();
            }
        }

        void createTitleView(int i) {
            if (titleViews[i] != null) {
                return;
            }
            final SimpleTextView view = new SimpleTextView(getContext());
            view.setGravity(Gravity.CENTER);
            view.setTypeface(AndroidUtilities.bold());
            view.setTextSize(17);
            view.setTextColor(titleColor);
            view.setEmojiColor(titleColor);
            view.setDrawablePadding(dp(4));
            view.setPadding(0, dp(8), 0, dp(8));
            view.setRightDrawableTopPadding(-dp(1));
            titleViews[i] = view;
            addView(view, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        }

        void setTitle(CharSequence value) {
            createTitleView(0);
            if (titleViews[1] != null) {
                titleViews[1].animate().cancel();
                titleViews[1].setVisibility(GONE);
            }
            titleViews[0].animate().cancel();
            titleViews[0].setAlpha(1f);
            titleViews[0].setTranslationY(0);
            titleViews[0].setText(value);
            titleViews[0].setVisibility(TextUtils.isEmpty(value) ? INVISIBLE : VISIBLE);
        }

        void setTitleAnimated(CharSequence value, boolean fromBottom, long duration, Interpolator interpolator) {
            createTitleView(0);
            createTitleView(1);
            final SimpleTextView old = titleViews[0];
            titleViews[0] = titleViews[1];
            titleViews[1] = old;

            final SimpleTextView now = titleViews[0];
            now.animate().cancel();
            now.setText(value);
            now.setVisibility(VISIBLE);
            now.setAlpha(0f);
            now.setTranslationY(fromBottom ? dp(16) : -dp(16));
            now.animate().alpha(1f).translationY(0).setDuration(duration).setInterpolator(interpolator).start();

            old.animate().cancel();
            old.animate().alpha(0f).translationY(fromBottom ? -dp(16) : dp(16)).setDuration(duration).setInterpolator(interpolator)
                .withEndAction(() -> old.setVisibility(GONE)).start();
            requestLayout();
        }

        private SimpleTextView createSubtitleView() {
            final SimpleTextView view = new SimpleTextView(getContext());
            view.setGravity(Gravity.CENTER);
            view.setTextSize(14);
            view.setTextColor(subtitleColor);
            addView(view, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
            return view;
        }

        void setSubtitle(CharSequence value) {
            subtitle = value;
            if (subtitleOutView != null) {
                subtitleOutView.animate().cancel();
                subtitleOutView.setVisibility(GONE);
            }
            if (subtitleView == null) {
                if (TextUtils.isEmpty(value)) {
                    return;
                }
                subtitleView = createSubtitleView();
            }
            subtitleView.animate().cancel();
            subtitleView.setAlpha(1f);
            subtitleView.setTranslationY(0);
            subtitleView.setText(value);
            subtitleView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
            requestLayout();
        }

        void setSubtitleAnimated(CharSequence value) {
            if (!hasSubtitle() || TextUtils.isEmpty(value) || TextUtils.equals(subtitle, value)) {
                if (!TextUtils.equals(subtitle, value)) {
                    setSubtitle(value);
                }
                return;
            }
            subtitle = value;
            if (subtitleOutView == null) {
                subtitleOutView = createSubtitleView();
            }
            final SimpleTextView out = subtitleView;
            subtitleView = subtitleOutView;
            subtitleOutView = out;

            subtitleView.animate().cancel();
            subtitleView.setText(value);
            subtitleView.setVisibility(VISIBLE);
            subtitleView.setAlpha(0f);
            subtitleView.setTranslationY(dp(6));
            subtitleView.animate().alpha(1f).translationY(0).setDuration(DURATION).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();

            out.animate().cancel();
            out.animate().alpha(0f).translationY(-dp(6)).setDuration(DURATION).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> out.setVisibility(GONE)).start();
            requestLayout();
        }

        private boolean isSubtitleView(View child) {
            return child == subtitleView || child == subtitleOutView;
        }

        void updateColors() {
            for (SimpleTextView view : titleViews) {
                if (view != null) {
                    view.setTextColor(titleColor);
                    view.setEmojiColor(titleColor);
                }
            }
            if (subtitleView != null) {
                subtitleView.setTextColor(subtitleColor);
            }
            if (subtitleOutView != null) {
                subtitleOutView.setTextColor(subtitleColor);
            }
        }

        private boolean hasSubtitle() {
            return subtitleView != null && subtitleView.getVisibility() != GONE;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            final int available = Math.max(0, width - sideInset * 2);
            for (SimpleTextView view : titleViews) {
                if (view != null && view.getVisibility() != GONE) {
                    view.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(24) + view.getPaddingTop() + view.getPaddingBottom(), MeasureSpec.AT_MOST));
                }
            }
            if (hasSubtitle()) {
                subtitleView.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));
            }
            if (subtitleOutView != null && subtitleOutView.getVisibility() != GONE) {
                subtitleOutView.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.AT_MOST));
            }
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final View child = getChildAt(i);
                if (child != titleViews[0] && child != titleViews[1] && !isSubtitleView(child) && child.getVisibility() != GONE) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int width = right - left;
            final int rowHeight = bottom - top;
            final boolean hasSubtitle = hasSubtitle();
            for (SimpleTextView view : titleViews) {
                if (view == null || view.getVisibility() == GONE) {
                    continue;
                }
                final int textTop = hasSubtitle
                    ? (rowHeight / 2 - view.getTextHeight()) / 2 + dp(5)
                    : (rowHeight - view.getTextHeight()) / 2;
                final int l = (width - view.getMeasuredWidth()) / 2;
                final int t = textTop - view.getPaddingTop();
                view.layout(l, t, l + view.getMeasuredWidth(), t + view.getMeasuredHeight());
            }
            if (hasSubtitle) {
                for (SimpleTextView view : new SimpleTextView[] { subtitleView, subtitleOutView }) {
                    if (view == null || view.getVisibility() == GONE) {
                        continue;
                    }
                    final int textTop = rowHeight / 2 + (rowHeight / 2 - view.getTextHeight()) / 2 - dp(2);
                    final int l = (width - view.getMeasuredWidth()) / 2;
                    view.layout(l, textTop, l + view.getMeasuredWidth(), textTop + view.getTextHeight());
                }
            }
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final View child = getChildAt(i);
                if (child != titleViews[0] && child != titleViews[1] && !isSubtitleView(child) && child.getVisibility() != GONE) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    final int l = lp.leftMargin;
                    final int t = (rowHeight - child.getMeasuredHeight()) / 2 + lp.topMargin - lp.bottomMargin;
                    child.layout(l, t, l + child.getMeasuredWidth(), t + child.getMeasuredHeight());
                }
            }
        }
    }
}
