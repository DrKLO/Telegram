package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;

import java.util.ArrayList;

/**
 * Layer above a simple glass screen where the menus of {@link SimpleActionBar} open. It is not a
 * window: the fragment adds it to its root view (see {@link BaseFragment#getSimpleMenuOverlay()}).
 * The glass of the menu button stretches into the menu (the width leads, the height follows, both
 * with a small spring overshoot), the button's icon fades out inside it and the items fade in; closing
 * pours the menu back into the button. The overlay has its own glass drawable, a glass drawable has
 * a single owner.
 */
public class SimpleActionBarMenuOverlay extends FrameLayout {
    private static final float MENU_RADIUS = 22;

    private float glassColorsFactor = 1f;
    private PopupSwipeBackLayout swipeBackListening;
    private final BlurredBackgroundColorProvider glassColors = new BlurredBackgroundColorProvider() {
        @Nullable
        private BlurredBackgroundColorProvider base() {
            return bar != null ? bar.getMenuGlassColorProvider() : null;
        }

        @Override
        public int getShadowColor() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final int menu = base != null ? base.getShadowColor() : 0;
            return buttons != null ? blend(buttons.getShadowColor(), menu) : menu;
        }

        @Override
        public int getBackgroundColor() {
            final BlurredBackgroundColorProvider base = base();
            final int menuColor = base != null ? base.getBackgroundColor() : 0;
            if (glassColorsFactor >= 1f || bar == null) {
                return menuColor;
            }
            return ColorUtils.blendARGB(bar.getButtonsGlassBackgroundColor(), menuColor, glassColorsFactor);
        }

        @Override
        public int getStrokeColorTop() {
            final BlurredBackgroundColorProvider base = base();
            final int menu = base != null && bar.hasMenuGlassStroke() ? base.getStrokeColorTop() : 0;
            final BlurredBackgroundColorProvider buttons = buttons();
            return buttons != null ? blend(buttons.getStrokeColorTop(), menu) : menu;
        }

        @Override
        public int getStrokeColorBottom() {
            final BlurredBackgroundColorProvider base = base();
            final int menu = base != null && bar.hasMenuGlassStroke() ? base.getStrokeColorBottom() : 0;
            final BlurredBackgroundColorProvider buttons = buttons();
            return buttons != null ? blend(buttons.getStrokeColorBottom(), menu) : menu;
        }

        @Override
        public int getOutlineColor() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final int menu = base != null ? base.getOutlineColor() : 0;
            return buttons != null ? blend(buttons.getOutlineColor(), menu) : menu;
        }

        @Override
        public float getOutlineWidth() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final float menu = base != null ? base.getOutlineWidth() : 0;
            return buttons != null ? AndroidUtilities.lerp(buttons.getOutlineWidth(), menu, glassColorsFactor) : menu;
        }

        @Override
        public int getOutlineShadowColor() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final int menu = base != null ? base.getOutlineShadowColor() : 0;
            return buttons != null ? blend(buttons.getOutlineShadowColor(), menu) : menu;
        }

        @Override
        public float getShadowRadiusOverride() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final float menu = base != null ? base.getShadowRadiusOverride() : -1;
            final float from = buttons != null ? buttons.getShadowRadiusOverride() : menu;
            return from >= 0 && menu >= 0 ? AndroidUtilities.lerp(from, menu, glassColorsFactor) : glassColorsFactor < 0.5f ? from : menu;
        }

        @Override
        public float getShadowDyOverride() {
            final BlurredBackgroundColorProvider base = base(), buttons = buttons();
            final float menu = base != null ? base.getShadowDyOverride() : -1;
            final float from = buttons != null ? buttons.getShadowDyOverride() : menu;
            return from >= 0 && menu >= 0 ? AndroidUtilities.lerp(from, menu, glassColorsFactor) : glassColorsFactor < 0.5f ? from : menu;
        }

        /** The buttons' colors while the menu is (partly) still their glass: it goes over to the menu's by its growth. */
        @Nullable
        private BlurredBackgroundColorProvider buttons() {
            return glassColorsFactor < 1f && bar != null ? bar.getButtonsGlassColorProvider() : null;
        }

        /** Colors with alpha (a stroke may be transparent): blended with their alphas, not over each other. */
        private int blend(int from, int to) {
            return ColorUtils.blendARGB(from, to, glassColorsFactor);
        }
    };

    private MenuOverlayHost bar;
    private ActionBarMenuItem item;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout content;
    private BlurredBackgroundDrawable glass;
    private MenuOverlayHost glassBar;
    private final java.util.WeakHashMap<MenuOverlayHost, BlurredBackgroundDrawable> glasses = new java.util.WeakHashMap<>();
    /** See {@link MenuOverlayHost#isMenuFromPillBelow()}. */
    private boolean pillBelow;
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();

    private boolean opened;
    private boolean startFromPill;
    private boolean trackingOutside;
    private long openedAt;
    private Runnable onClosed;

    // the springs: two factors, the width and the height of the shape, 0 = the button, 1 = the menu
    private long animStart;
    private float animFrom, menuW, menuH, wobble;
    private boolean animating;
    // the notifications wait while the menu moves: a list updated under it would drop frames
    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    private final RectF startRect = new RectF(), endRect = new RectF(), shapeRect = new RectF(), tmpRect = new RectF();
    private final RectF bulgedRect = new RectF();
    private final float[] shapeRadii = new float[8];
    private final Path clipPath = new Path();

    public SimpleActionBarMenuOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setVisibility(GONE);
        dimPaint.setColor(0xFF000000);
    }

    /* State */

    public boolean isOpened() {
        return opened;
    }

    /** Whether the menu is on screen, opening, open or closing. */
    public boolean isShown(ActionBarMenuItem item) {
        return this.item == item && (opened || animating);
    }

    /** Whether the menu of a host without menu items (a pill of its own) is on screen, opening, open or closing. */
    public boolean isShown(MenuOverlayHost host) {
        return bar == host && item == null && (opened || animating);
    }

    /** Opens the menu of a host that is a pill of its own, without menu items: the whole pill grows into the menu. */
    public void open(MenuOverlayHost host, ActionBarPopupWindow.ActionBarPopupWindowLayout content) {
        open(host, null, content);
    }

    void open(MenuOverlayHost bar, @Nullable ActionBarMenuItem item, ActionBarPopupWindow.ActionBarPopupWindowLayout content) {
        if (opened && this.bar == bar && this.item == item) {
            return;
        }
        if (this.bar != null && (this.bar != bar || this.item != item)) {
            finish();
        }
        this.bar = bar;
        this.item = item;
        pillBelow = bar.isMenuFromPillBelow();
        if (this.content != content) {
            if (this.content != null) {
                AndroidUtilities.removeFromParent(this.content);
            }
            this.content = content;
        }
        prepareContent(content);
        if (content.getParent() != this) {
            AndroidUtilities.removeFromParent(content);
            addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        if (glass == null || glassBar != bar) {
            // one glass per host (its own source), each owned by this view; the one of another host is put away
            if (glass != null) {
                glass.clearReservedBounds();
                glass.setBounds(0, 0, 0, 0);
            }
            glass = glasses.get(bar);
            if (glass == null) {
                glass = bar.createMenuGlass(this);
                if (glass != null) {
                    glass.setColorProvider(glassColors);
                    glasses.put(bar, glass);
                }
            }
            glassBar = bar;
        }
        final ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).bringChildToFront(this);
        }
        setVisibility(VISIBLE);
        opened = true;
        openedAt = SystemClock.uptimeMillis();
        startAnimation();
        bar.setMenuOverlay(this, item);
        computeStartRect();
        if (!startFromPill && item != null) {
            item.getIconView().setAlpha(0f); // the overlay draws it (a pill is left to the overlay as a whole)
        }
        // the keys (back, menu) come to the focused overlay; a text field keeps its focus and its keyboard,
        // the back press then reaches the screen, which closes the menu
        final View focused = getRootView() != null ? getRootView().findFocus() : null;
        if (!(focused instanceof EditText) || !focused.isShown()) { // a hidden field (a channel's) keeps no keyboard
            setFocusableInTouchMode(true);
            requestFocus();
        }
    }

    /** Closes the menu: animated pours it back into the button, otherwise it disappears at once. */
    public void close(boolean animated) {
        if (!opened && !animating) {
            return;
        }
        if (opened) {
            opened = false;
            if (animated && isAttachedToWindow() && isLaidOut()) {
                startAnimation();
                return;
            }
        }
        finish();
    }

    /** Runs once the menu has closed (after its animation). */
    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    private void finish() {
        if (glass != null) {
            glass.clearReservedBounds();
        }
        setAnimating(false);
        opened = false;
        menuW = menuH = wobble = 0;
        clearFocus();
        setVisibility(GONE);
        restorePopupLook();
        final ActionBarMenuItem wasItem = item;
        final MenuOverlayHost wasBar = bar;
        item = null;
        bar = null;
        if (wasBar != null) {
            wasBar.setMenuOverlay(null, null);
        }
        if (wasItem != null) {
            wasItem.getIconView().setAlpha(1f);
            wasItem.onOverlayMenuClosed();
        }
        if (onClosed != null) {
            final Runnable r = onClosed;
            onClosed = null;
            r.run();
        }
    }

    /** How the popup layouts looked as popups: the same sub menu may open in a popup window later (one with a view on top). */
    private final ArrayList<PopupState> popupStates = new ArrayList<>();

    private static final class PopupState {
        final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
        final Drawable background;
        final Drawable backgroundDrawable;
        final int backgroundColor;
        final int paddingLeft, paddingTop, paddingRight, paddingBottom;

        PopupState(ActionBarPopupWindow.ActionBarPopupWindowLayout layout) {
            this.layout = layout;
            background = layout.getBackground();
            backgroundDrawable = layout.getBackgroundDrawable();
            backgroundColor = layout.getBackgroundColor();
            paddingLeft = layout.getPaddingLeft();
            paddingTop = layout.getPaddingTop();
            paddingRight = layout.getPaddingRight();
            paddingBottom = layout.getPaddingBottom();
        }

        void restore() {
            layout.setBackground(background);
            layout.setBackgroundDrawable(backgroundDrawable);
            if (backgroundDrawable != null) {
                layout.setBackgroundColor(backgroundColor);
            }
            layout.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }

    /** Takes the popup look off a layout for the glass of the overlay, it comes back once the menu has closed. */
    private void takeOffPopupLook(ActionBarPopupWindow.ActionBarPopupWindowLayout layout, int paddingVertical) {
        boolean saved = false;
        for (int i = 0; i < popupStates.size(); i++) {
            if (popupStates.get(i).layout == layout) {
                saved = true;
                break;
            }
        }
        if (!saved) {
            popupStates.add(new PopupState(layout));
        }
        layout.setBackground(null);
        layout.setBackgroundDrawable(null);
        layout.setPadding(0, paddingVertical, 0, paddingVertical);
    }

    private void restorePopupLook() {
        for (int i = 0; i < popupStates.size(); i++) {
            popupStates.get(i).restore();
        }
        popupStates.clear();
        if (content != null && content.getSwipeBack() != null) {
            content.getSwipeBack().setForegroundOnGlass(false);
        }
    }

    private void prepareContent(ActionBarPopupWindow.ActionBarPopupWindowLayout content) {
        // the glass is drawn by the overlay: no popup background, rows to the edges of the shape
        takeOffPopupLook(content, dp(5));
        content.setTranslationX(0);
        content.setTranslationY(0);
        content.setAlpha(1f);
        // the items are out of the view tree while closed: a theme switch since the last open has not reached them
        ThemeBind.updateTree(content, false);
        ItemOptions.setGapBackgroundColor(content, Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, bar.getMenuResourcesProvider()), 0.06f));
        content.updateRadialSelectors();
        final PopupSwipeBackLayout swipeBack = content.getSwipeBack();
        if (swipeBack != null) {
            swipeBack.closeForeground(false);
            swipeBack.setForegroundOnGlass(true);
            if (swipeBackListening != swipeBack) {
                // a nested page changes the size of the glass: the page's own redraws do not reach this view's drawing
                swipeBackListening = swipeBack;
                swipeBack.addOnSwipeBackProgressListener((layout, toProgress, progress) -> invalidate());
            }
            // the nested pages are popups of their own: their background would be a card inside the glass
            for (int i = 1; i < swipeBack.getChildCount(); i++) {
                if (swipeBack.getChildAt(i) instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    final ActionBarPopupWindow.ActionBarPopupWindowLayout page = (ActionBarPopupWindow.ActionBarPopupWindowLayout) swipeBack.getChildAt(i);
                    // the padding of the popup's background: the rows go to the edges of the glass as on the main page
                    takeOffPopupLook(page, 0);
                    ThemeBind.updateTree(page, false);
                    ItemOptions.setGapBackgroundColor(page, Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, bar.getMenuResourcesProvider()), 0.06f));
                }
            }
        }
        content.scrollToTop();
    }

    /**
     * The part of the content that is shown: a nested page of the menu is smaller than the menu, the popup
     * layout keeps its size and shows the page by its back scales, at the side the pages stick to.
     */
    private void computeContentRect(RectF rect) {
        final float w = content.getMeasuredWidth() * content.getBackScaleX();
        final float h = content.getMeasuredHeight() * content.getBackScaleY();
        final PopupSwipeBackLayout swipeBack = content.getSwipeBack();
        // a popup whose pages open from the right moves itself by the hidden part, the page stays at its right
        if (swipeBack != null && (swipeBack.stickToRight || content.swipeBackGravityRight)) {
            rect.set(content.getRight() - w, content.getTop(), content.getRight(), content.getTop() + h);
        } else {
            rect.set(content.getLeft(), content.getTop(), content.getLeft() + w, content.getTop() + h);
        }
    }

    /* Animation (the same springs as the fee chip menu of the QR screen) */

    private static double springStep(double t, double z, double period) {
        if (t <= 0) return 0;
        final double w0 = 2 * Math.PI / period, wd = w0 * Math.sqrt(1 - z * z);
        return 1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + z / Math.sqrt(1 - z * z) * Math.sin(wd * t));
    }

    private void startAnimation() {
        animFrom = Math.max(menuW, menuH);
        lastMenuH = menuH;
        lastStepTime = 0;
        animStart = 0; // the clock starts with the first frame drawn: opening measures the items first
        setAnimating(true);
        invalidate();
    }

    private void setAnimating(boolean animating) {
        if (this.animating == animating) {
            return;
        }
        this.animating = animating;
        if (animating) {
            notificationsLocker.lock();
        } else {
            notificationsLocker.unlock();
        }
    }

    /** Seconds since the animation has started, following the system animation scale. */
    /** The animator duration scale of the system settings: ValueAnimator#getDurationScale is only on Android 13. */
    private float getAnimatorDurationScale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ValueAnimator.getDurationScale();
        }
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception e) {
            return 1f;
        }
    }

    private float animationTime() {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (animStart == 0) {
            animStart = now;
        }
        final float scale = getAnimatorDurationScale();
        return scale <= 0 ? 10f : (now - animStart) / 1000f / scale;
    }

    private void stepAnimation() {
        if (!animating) {
            return;
        }
        final float t = animationTime();
        final float f = animFrom;
        if (opened) {
            wobble = 0;
            menuW = t > 0.7f ? 1f : f + (1f - f) * (float) springStep(t, 0.78, 0.30);        // ≈2% overshoot
            menuH = t > 0.7f ? 1f : f + (1f - f) * (float) springStep(t - 0.02, 0.80, 0.34); // ≈1.5%
            if (t > 0.7f) {
                setAnimating(false);
            }
        } else {
            final float u = Utilities.clamp01(t / 0.2f), uw = Utilities.clamp01(t / 0.17f);
            menuW = f * (1f - uw * uw * (3f - 2f * uw));
            menuH = f * (1f - u * u * (3f - 2f * u));
            // the button settles after the menu pours back: a small squash, ~0.35 s
            final float tw = t - 0.2f;
            wobble = tw > 0 && startFromPill ? 0.07f * f * (float) Math.exp(-tw / 0.11f) * (float) Math.sin(2 * Math.PI * tw / 0.17f) : 0f;
            if (startFromPill ? tw > 0.45f : tw >= 0) { // an action mode button has no own glass to settle
                wobble = 0;
                post(() -> {
                    if (!opened && animating) { // not opened again meanwhile
                        finish();
                    }
                });
            }
        }
        // the speed of the height, for the bulge of the moving edge (per second of the animation's clock)
        final float dt = lastStepTime == 0 ? 0 : t - lastStepTime;
        if (dt > 0.001f) {
            menuHVel = (menuH - lastMenuH) / dt;
        } else if (lastStepTime == 0) {
            menuHVel = 0;
        }
        lastMenuH = menuH;
        lastStepTime = Math.max(t, 0.0001f);
    }

    private float lastMenuH, menuHVel, lastStepTime;

    private static float smooth(float from, float to, float x) {
        final float u = Utilities.clamp01((x - from) / (to - from));
        return u * u * (3f - 2f * u);
    }

    /* Layout */

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (content != null && content.getParent() == this) {
            computeStartRect();
            final int maxHeight = pillBelow
                ? (int) (startRect.bottom - AndroidUtilities.statusBarHeight - dp(16))
                : height - getContentTop() - dp(16) - AndroidUtilities.navigationBarHeight;
            content.measure(MeasureSpec.makeMeasureSpec(width - dp(16), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(Math.max(dp(48), maxHeight), MeasureSpec.AT_MOST));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (content != null && content.getParent() == this) {
            computeStartRect();
            final int w = content.getMeasuredWidth(), h = content.getMeasuredHeight();
            if (pillBelow) {
                // centred on the pill, its bottom on the pill's: the menu grows upward out of it
                final int l = (int) Math.max(dp(8), Math.min(getWidth() - dp(8) - w, startRect.centerX() - w / 2f));
                final int b = (int) startRect.bottom;
                content.layout(l, b - h, l + w, b);
            } else {
                final int r = (int) startRect.right, t = getContentTop();
                final int l = Math.max(dp(8), r - w);
                content.layout(l, t, l + w, t + h);
            }
        }
    }

    /** The top of a menu growing down out of the button: it rises by the host's value, staying on the screen. */
    private int getContentTop() {
        final int top = (int) startRect.top;
        return Math.max(Math.min(top, 0), top - bar.getMenuRise());
    }

    /** The glass of the menu button in this view: the whole menu pill when the button is its only item. */
    private void computeStartRect() {
        if (bar == null) {
            return;
        }
        startFromPill = bar.getMenuItemGlassRect(item, startRect);
        computeOffset(bar.getMenuHostView());
        startRect.offset(offset[0], offset[1]);
    }

    private final float[] offset = new float[2];
    private final RectF offsetRect = new RectF();

    /** The position of {@code view} in this overlay: the positions of both summed up to their common root. */
    private void computeOffset(View view) {
        if (ViewPositionWatcher.computeRectInParent(view, this, offsetRect)) {
            offset[0] = offsetRect.left;
            offset[1] = offsetRect.top;
        } else {
            offset[0] = offset[1] = 0;
        }
    }

    /* Drawing */

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (bar == null || content == null) {
            return;
        }
        stepAnimation();
        computeStartRect();
        computeContentRect(endRect);

        final float pw = menuW, ph = menuH, m = Utilities.clamp01(Math.max(pw, ph));
        shapeRect.set(
            lerp(startRect.left, endRect.left, pw),
            lerp(startRect.top, endRect.top, ph),
            lerp(startRect.right, endRect.right, pw),
            lerp(startRect.bottom, endRect.bottom, ph)
        );
        if (wobble != 0) {
            final float cx = shapeRect.centerX(), cy = shapeRect.centerY();
            final float hw = shapeRect.width() / 2f * (1f + wobble * 0.5f), hh = shapeRect.height() / 2f * (1f - wobble);
            shapeRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        }
        final float radius = Math.min(Math.min(shapeRect.height(), shapeRect.width()) / 2f, lerp(startRect.height() / 2f, dp(MENU_RADIUS), Utilities.clamp01(ph)));
        // out of a pill below (the QR screen's chip menu): the moving top edge bulges forward with its speed, as liquid;
        // a dome of the glass's own shape, the top raised and its corners rounder, so the rim follows it
        final float bulge = pillBelow ? 0.75f * dp(5) * Utilities.clamp01(menuHVel / 9f) : 0f;
        final float topRadius = radius + bulge;
        shapeRadii[0] = shapeRadii[1] = shapeRadii[2] = shapeRadii[3] = topRadius;
        shapeRadii[4] = shapeRadii[5] = shapeRadii[6] = shapeRadii[7] = radius;
        bulgedRect.set(shapeRect.left, shapeRect.top - bulge, shapeRect.right, shapeRect.bottom);

        // a light dim of the screen
        dimPaint.setAlpha((int) (0x1A * m));
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        // the one glass shape: the button's pill stretched into the menu
        final int p = dp(6);
        // an action mode button has no glass of its own: the menu's glass appears over its pill
        final float glassAlpha = startFromPill ? 1f : Utilities.clamp01(m / 0.25f);
        if (glass != null) {
            // from the color of the button's glass (a tinted header) to the menu's
            glassColorsFactor = startFromPill ? Utilities.clamp01(m) : 1f;
            // and from the button's glass to the frosted glass of the open menu
            bar.setMenuGlassFrostedProgress(glassColorsFactor);
            glass.updateColors();
            // the stroke's width goes over too: the buttons' glass may have thinner or thicker lines than the menu's
            glass.setStrokeWidth(
                lerp(strokeWidth(bar.getButtonsGlassColorProvider(), true), strokeWidth(glassColors, true), glassColorsFactor),
                lerp(strokeWidth(bar.getButtonsGlassColorProvider(), false), strokeWidth(glassColors, false), glassColorsFactor));
            if (bulge > 0) {
                glass.setRadius(topRadius, topRadius, radius, radius, false);
            } else {
                glass.setRadius(radius);
            }
            // rounded, not truncated: at the start and the end the shape is the button's, it must not move by a pixel
            glass.setBounds(Math.round(bulgedRect.left) - p, Math.round(bulgedRect.top) - p, Math.round(bulgedRect.right) + p, Math.round(bulgedRect.bottom) + p);
            reserveGlass();
            glass.setAlpha((int) (255 * glassAlpha));
            glass.draw(canvas);
        } else {
            fallbackPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, bar.getMenuResourcesProvider()));
            fallbackPaint.setAlpha((int) (fallbackPaint.getAlpha() * glassAlpha));
            clipPath.rewind();
            clipPath.addRoundRect(bulgedRect, shapeRadii, Path.Direction.CW);
            canvas.drawPath(clipPath, fallbackPaint);
        }

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(bulgedRect, shapeRadii, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // the buttons (the whole pill, or just this button) fade and shrink away inside the shape (the first ~30%),
        // come back while the items are still fading out on closing
        final float ia = 1f - Utilities.clamp01(m / (opened ? 0.3f : 0.7f));
        final View icon = startFromPill ? bar.getMenuView() : item.getIconView();
        if (ia > 0.004f && icon != null) {
            computeViewRect(icon, tmpRect);
            final float s = 1f - 0.15f * Utilities.clamp01(m / 0.3f);
            saveFaded(canvas, tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom, ia);
            canvas.scale(s * (1f + wobble * 0.5f), s * (1f - wobble), tmpRect.centerX(), tmpRect.centerY());
            canvas.translate(tmpRect.left, tmpRect.top);
            icon.draw(canvas);
            canvas.restore();
        }

        // the items grow with the shape from its corner at the button and fade in once it has grown a bit;
        // closing fades them out first
        final float ca = pillBelow
            ? (opened ? smooth(0.15f, 0.6f, m) : smooth(0.6f, 1f, m))
            : (opened ? smooth(0.25f, 0.75f, m) : smooth(0.5f, 0.95f, m));
        if (ca > 0.004f && pillBelow) {
            // the rows are rigid: they ride the shape's top centre (whole pixels once it settles), the clip reveals them
            float tx = shapeRect.centerX() - endRect.centerX(), ty = shapeRect.top - endRect.top;
            if (Math.abs(menuHVel) < 0.6f) {
                tx = Math.round(tx);
                ty = Math.round(ty);
            }
            saveFaded(canvas, bulgedRect.left, bulgedRect.top, bulgedRect.right, bulgedRect.bottom, ca);
            canvas.translate(tx, ty);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else if (ca > 0.004f) {
            // the width leads: a content scaled with it never sticks out of the sides of the shape
            final float cs = Math.max(0.3f, shapeRect.width() / Math.max(1f, endRect.width()));
            saveFaded(canvas, shapeRect.left, shapeRect.top, shapeRect.right, shapeRect.bottom, ca);
            canvas.translate(shapeRect.right - endRect.right, shapeRect.top - endRect.top);
            canvas.scale(cs, cs, endRect.right, endRect.top);
            super.dispatchDraw(canvas);
            canvas.restore();
        }
        canvas.restore();

        if (animating) {
            invalidate();
        }
    }

    /**
     * A layer only while fading: the open menu draws its rows straight into the clip. The rows clip themselves by a path
     * (the swipe back pages), inside a layer that drops all of them on some Android 9 GPUs (Xiaomi): only the glass was left.
     */
    private static void saveFaded(Canvas canvas, float left, float top, float right, float bottom, float alpha) {
        if (alpha >= 1f) {
            canvas.save();
        } else {
            canvas.saveLayerAlpha(left, top, right, bottom, (int) (255 * alpha));
        }
    }

    /** The stroke width of a glass of these colors, as BlurredBackgroundDrawable takes it from them (its own default otherwise). */
    private float strokeWidth(@Nullable BlurredBackgroundColorProvider colors, boolean top) {
        if (colors == glassColors) {
            colors = bar != null ? bar.getMenuGlassColorProvider() : null;
        }
        if (colors instanceof BlurredBackgroundProvider) {
            final BlurredBackgroundProvider provider = (BlurredBackgroundProvider) colors;
            return top ? provider.getStrokeWidthTop() : provider.getStrokeWidthBottom();
        }
        return top ? AndroidUtilities.dpf2(1) : AndroidUtilities.dpf2(2 / 3f);
    }

    /**
     * The whole way of the shape, from the button to the open menu with the springs' overshoot: the capture under it is
     * made once, not on every frame of the animation.
     */
    private void reserveGlass() {
        tmpRect.set(content.getLeft(), content.getTop(), content.getRight(), content.getBottom());
        tmpRect.union(startRect);
        final float over = dp(4) + 0.04f * Math.max(tmpRect.width(), tmpRect.height());
        glass.setReservedBounds(
            (int) Math.floor(tmpRect.left - over), (int) Math.floor(tmpRect.top - over),
            (int) Math.ceil(tmpRect.right + over), (int) Math.ceil(tmpRect.bottom + over));
    }

    private void computeViewRect(View view, RectF out) {
        computeOffset(view);
        out.set(offset[0], offset[1], offset[0] + view.getWidth(), offset[1] + view.getHeight());
    }

    /* Touches and keys */

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!opened) {
            // a closing menu lets the touches through
            return false;
        }
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            final ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true); // no swipe back under an open menu
            }
            // a quick second tap on the button (a double tap) closes the menu instead of choosing the item under it
            final boolean repeatedTap = SystemClock.uptimeMillis() - openedAt < 300 && startRect.contains(ev.getX(), ev.getY());
            trackingOutside = repeatedTap || !endRect.contains(ev.getX(), ev.getY());
            if (trackingOutside) {
                close(true);
                return true;
            }
        }
        if (trackingOutside) {
            return true;
        }
        super.dispatchTouchEvent(ev);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (opened && event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0
                && (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
            close(true);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (opened || animating) {
            finish();
        }
    }

    @Nullable
    public ActionBarMenuItem getItem() {
        return item;
    }
}
