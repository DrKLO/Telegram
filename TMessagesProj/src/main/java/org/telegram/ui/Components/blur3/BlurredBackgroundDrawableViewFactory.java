package org.telegram.ui.Components.blur3;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.utils.glass.GlassEngine;

import me.vkryl.core.reference.ReferenceList;

public class BlurredBackgroundDrawableViewFactory {

    private final BlurredBackgroundSource source;
    private int outsetX, outsetY;

    public BlurredBackgroundDrawableViewFactory(BlurredBackgroundSource source) {
        this.source = source;
    }

    public BlurredBackgroundDrawableViewFactory(ViewPositionWatcher watcher, ViewGroup parent, BlurredBackgroundSource source) {
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

    public BlurredBackgroundDrawable create(View view, boolean multiwindow) {
        return create(view, null, multiwindow);
    }

    public BlurredBackgroundDrawable create(View view, BlurredBackgroundColorProvider provider) {
        return create(view, provider, false);
    }

    public BlurredBackgroundDrawable create(View view, BlurredBackgroundColorProvider provider, boolean multiwindow) {
        final BlurredBackgroundDrawable drawable = source.createDrawable();
        if (isLiquidGlassEffectAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (drawable instanceof BlurredBackgroundDrawableRenderNode) {
                ((BlurredBackgroundDrawableRenderNode) drawable).setLiquidGlassEffectAllowed();
            }
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
            viewPositionWatcher.subscribe(view, parent, (v, pos) -> {
                drawable.setSourceOffset(pos.left, pos.top);
                view.invalidate();
            }, multiwindow);
        }

        if (linkedDrawables != null) {
            linkedDrawables.add(drawable);
        }

        return drawable;
    }
}
