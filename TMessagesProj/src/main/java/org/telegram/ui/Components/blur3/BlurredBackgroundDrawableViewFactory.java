package org.telegram.ui.Components.blur3;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.utils.glass.GlassEngine;

import java.lang.ref.WeakReference;

import me.vkryl.core.reference.ReferenceList;

public class BlurredBackgroundDrawableViewFactory {

    private final GlassSource glassSource;
    private final GlassSource frostedSource;
    private final GlassSource staticSource;
    private int outsetX, outsetY;

    public BlurredBackgroundDrawableViewFactory(GlassSource source) {
        this(source, source, source);
    }

    /**
     * The drawables pick one by the glass settings: {@code glassSource} under the liquid glass, {@code frostedSource} with
     * the blur only, {@code staticSource} without the blur, it does not use a capture (a color, a bitmap).
     */
    public BlurredBackgroundDrawableViewFactory(GlassSource glassSource, GlassSource frostedSource, GlassSource staticSource) {
        this.glassSource = glassSource;
        this.frostedSource = frostedSource;
        this.staticSource = staticSource;
    }

    public GlassSource getSource() {
        return glassSource;
    }

    public GlassSource getFrostedSource() {
        return frostedSource;
    }

    public GlassSource getStaticSource() {
        return staticSource;
    }

    public BlurredBackgroundDrawableViewFactory(ViewPositionWatcher watcher, ViewGroup parent, GlassSource source) {
        this(source);
        setSourceRootView(watcher, parent);
    }

    public void setSourceRootView(ViewPositionWatcher watcher, ViewGroup parent) {
        this.viewPositionWatcher = watcher;
        this.parent = parent;
    }

    public void setGlassEngine(GlassEngine engine) {
        this.engine = engine;
    }

    public void setOutset(int outset) {
        setOutset(outset, outset);
    }

    public void setOutset(int dx, int dy) {
        outsetX = dx;
        outsetY = dy;
    }


    private @Nullable ReferenceList<BlurredBackgroundDrawable> linkedDrawables;
    private @Nullable ReferenceList<View> linkedViews;
    private @Nullable ViewPositionWatcher viewPositionWatcher;
    private @Nullable ViewGroup parent;
    private @Nullable GlassEngine engine;

    public void setLinkedViewsRef(@Nullable ReferenceList<View> linkedViews) {
        this.linkedViews = linkedViews;
    }

    public void setLinkedDrawablesRef(@Nullable ReferenceList<BlurredBackgroundDrawable> linkedDrawables) {
        this.linkedDrawables = linkedDrawables;
    }

    public void invalidateAllLinkedViews() {
        if (linkedViews != null) {
            for (View v : linkedViews) {
                v.invalidate();
            }
        }
    }


    private boolean isLiquidGlassEffectAllowed;

    public void setLiquidGlassEffectAllowed(boolean liquidGlassEffectAllowed) {
        isLiquidGlassEffectAllowed = liquidGlassEffectAllowed;
    }

    public BlurredBackgroundDrawable create() {
        return create(null);
    }

    public BlurredBackgroundDrawable create(View view) {
        return create(view, null);
    }

    /** A {@link GlassSource} that makes its own drawables, e.g. render node ones that it keeps track of. */
    public interface DrawableOwner {
        BlurredBackgroundDrawable createDrawable();

        /** A drawable made with several sources, this one among them: to be kept track of as its own ones. */
        default void attachDrawable(BlurredBackgroundDrawable drawable) {

        }
    }

    /** The source's own drawable, otherwise one drawing the source through a canvas. */
    public static BlurredBackgroundDrawable createDrawable(GlassSource source) {
        if (source instanceof DrawableOwner) {
            return ((DrawableOwner) source).createDrawable();
        }
        return new BlurredBackgroundDrawable(source);
    }

    private BlurredBackgroundDrawable createDrawable() {
        if (glassSource == frostedSource && frostedSource == staticSource) {
            return createDrawable(glassSource);
        }
        final BlurredBackgroundDrawable drawable = new BlurredBackgroundDrawable(glassSource, frostedSource, staticSource);
        attach(glassSource, drawable);
        if (frostedSource != glassSource) {
            attach(frostedSource, drawable);
        }
        if (staticSource != glassSource && staticSource != frostedSource) {
            attach(staticSource, drawable);
        }
        return drawable;
    }

    private static void attach(GlassSource source, BlurredBackgroundDrawable drawable) {
        if (source instanceof DrawableOwner) {
            ((DrawableOwner) source).attachDrawable(drawable);
        }
    }

    public BlurredBackgroundDrawable create(View view, boolean multiwindow) {
        return create(view, null, multiwindow);
    }

    public BlurredBackgroundDrawable create(View view, BlurredBackgroundColorProvider provider) {
        return create(view, provider, false);
    }

    public BlurredBackgroundDrawable create(View view, BlurredBackgroundColorProvider provider, boolean multiwindow) {
        final BlurredBackgroundDrawable drawable = createDrawable();
        if (isLiquidGlassEffectAllowed) {
            // drawn while the glass settings have it on
            drawable.setLiquidGlassEffectAllowed();
        }

        drawable.setColorProvider(provider);
        drawable.setOutset(outsetX, outsetY);

        if (linkedViews != null && view != null) {
            linkedViews.add(view);
        }

        if (engine != null && view != null) {
            engine.registerDrawable(view, drawable);
        }

        if (viewPositionWatcher != null && parent != null && view != null) {
            final WeakReference<View> viewInstance = new WeakReference<>(view);
            viewPositionWatcher.subscribe(view, parent, (v, pos) -> {
                drawable.setSourceOffset(pos.left, pos.top);
                final View viewR = viewInstance.get();
                if (viewR != null) {
                    viewR.invalidate();
                }
            }, multiwindow);
        }

        if (linkedDrawables != null) {
            linkedDrawables.add(drawable);
        }

        return drawable;
    }
}
