package org.telegram.ui.ActionBar;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/**
 * A glass header whose menus open in a {@link SimpleActionBarMenuOverlay}, growing out of the glass of its buttons:
 * {@link SimpleActionBar}, an {@link ActionBar} on glass, or a pill of its own (e.g. the channel's membership pill).
 */
public interface MenuOverlayHost {
    /** A new glass drawable of a menu owned by {@code owner}; null without glass. */
    @Nullable
    BlurredBackgroundDrawable createMenuGlass(View owner);

    @Nullable
    BlurredBackgroundColorProvider getMenuGlassColorProvider();

    /** The glass color of the buttons, a menu growing out of them starts with it. */
    int getButtonsGlassBackgroundColor();

    /** The colors of the buttons' glass: a menu growing out of them starts with their stroke and outline too. */
    @Nullable
    default BlurredBackgroundColorProvider getButtonsGlassColorProvider() {
        return null;
    }

    boolean hasMenuGlassStroke();

    /** 0 while a menu is the button's glass, 1 once it is open. */
    void setMenuGlassFrostedProgress(float progress);

    /**
     * The glass the menu of {@code item} grows out of, in this view: true for the whole pill of the buttons
     * (then {@link #getMenuView()} is drawn by the overlay), false for a circle around the item.
     */
    boolean getMenuItemGlassRect(ActionBarMenuItem item, RectF out);

    /** The pill with its buttons, drawn by the overlay while a menu grows out of it. */
    View getMenuView();

    /** The overlay draws the glass of {@code item} while its menu is shown; null when it has closed. */
    void setMenuOverlay(@Nullable SimpleActionBarMenuOverlay overlay, @Nullable ActionBarMenuItem item);

    /** The overlay showing a menu of this header, opened or closing. */
    @Nullable
    SimpleActionBarMenuOverlay getOpenMenuOverlay();

    @Nullable
    Theme.ResourcesProvider getMenuResourcesProvider();

    /** The view the rects of {@link #getMenuItemGlassRect} are in. */
    default View getMenuHostView() {
        return (View) this;
    }

    /**
     * The menu grows upward out of a pill under it, centred on it, as the chip menu of the QR screen: the width leads,
     * the moving edge bulges, the rows ride the shape rigidly. Otherwise it grows down out of a header button.
     */
    default boolean isMenuFromPillBelow() {
        return false;
    }

    /** How far above the top of the button's glass a menu growing down out of it starts. */
    default int getMenuRise() {
        return 0;
    }
}
