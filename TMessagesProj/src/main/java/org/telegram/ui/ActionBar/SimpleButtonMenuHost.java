package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

/**
 * A button without glass of its own (an icon over a header, e.g. the ⋮ of a gift sheet) as a host of a
 * {@link SimpleActionBarMenuOverlay}: the menu's glass appears round on the button and grows down out of it.
 * The overlay draws the button inside the growing menu, the button itself is hidden meanwhile.
 */
public class SimpleButtonMenuHost implements MenuOverlayHost {
    private final SimpleActionBarMenuOverlay overlay;
    private final BaseFragmentSimpleGlass glass;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundColorProvider colors;

    private View button;
    private boolean shown;
    private int buttonVisibility = View.VISIBLE;

    public SimpleButtonMenuHost(SimpleActionBarMenuOverlay overlay, BaseFragmentSimpleGlass glass, Theme.ResourcesProvider resourcesProvider) {
        this.overlay = overlay;
        this.glass = glass;
        this.resourcesProvider = resourcesProvider;
        colors = BlurredBackgroundProviderImpl.simpleActionBar(resourcesProvider);
    }

    /** The button the next menu grows out of; not while a menu of this host is shown. */
    public void setButton(View button) {
        if (!shown) {
            this.button = button;
        }
    }

    public boolean isShown() {
        return overlay.isShown(this);
    }

    @Nullable
    @Override
    public BlurredBackgroundDrawable createMenuGlass(View owner) {
        return glass.menuFactory.create(owner).setColorProvider(colors).setRadius(dp(21)).setPadding(dp(6));
    }

    @Nullable
    @Override
    public BlurredBackgroundColorProvider getMenuGlassColorProvider() {
        return colors;
    }

    @Override
    public int getButtonsGlassBackgroundColor() {
        return colors.getBackgroundColor();
    }

    @Nullable
    @Override
    public BlurredBackgroundColorProvider getButtonsGlassColorProvider() {
        return colors;
    }

    @Override
    public boolean hasMenuGlassStroke() {
        return true;
    }

    @Override
    public void setMenuGlassFrostedProgress(float progress) {
        if (glass.menuGlassSource != null) {
            glass.menuGlassSource.setProgress(progress);
        }
    }

    @Override
    public boolean getMenuItemGlassRect(@Nullable ActionBarMenuItem item, RectF out) {
        out.set(0, 0, button.getWidth(), button.getHeight());
        return true;
    }

    @Override
    public View getMenuView() {
        return button;
    }

    @Override
    public void setMenuOverlay(@Nullable SimpleActionBarMenuOverlay overlay, @Nullable ActionBarMenuItem item) {
        final boolean shown = overlay != null;
        if (this.shown == shown) {
            return;
        }
        this.shown = shown;
        // the overlay draws the button inside the growing menu
        if (shown) {
            buttonVisibility = button.getVisibility();
            button.setVisibility(View.INVISIBLE);
        } else if (button.getVisibility() == View.INVISIBLE) {
            button.setVisibility(buttonVisibility);
        }
    }

    @Nullable
    @Override
    public SimpleActionBarMenuOverlay getOpenMenuOverlay() {
        return shown ? overlay : null;
    }

    @Nullable
    @Override
    public Theme.ResourcesProvider getMenuResourcesProvider() {
        return resourcesProvider;
    }

    @NonNull
    @Override
    public View getMenuHostView() {
        return button;
    }

    @Override
    public boolean hasButtonGlass() {
        return false;
    }
}
