package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;
import java.util.Objects;

public class SectionsScrollView extends ScrollView {

    private Theme.ResourcesProvider resourcesProvider;
    private LinearLayout contentView;

    private float sectionRadius = dp(16);
    private float[] sectionRadiusTop, sectionRadiusBottom;

    public static boolean isSectionView(View view) {
        return !Objects.equals(view.getTag(), RecyclerListView.TAG_NOT_SECTION) && !(
            view instanceof TextInfoPrivacyCell ||
            view instanceof ShadowSectionCell ||
            view instanceof FiltersSetupActivity.HintInnerCell
        );
    }

    public SectionsScrollView(Context context, LinearLayout content, Theme.ResourcesProvider resourcesProvider) {
        this(context, content, resourcesProvider, true);
    }
    public SectionsScrollView(
        Context context,
        LinearLayout content,
        Theme.ResourcesProvider resourcesProvider,
        boolean enableTopPadding
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.contentView = content;
        setWillNotDraw(false);

        contentView.setPadding(dp(12), dp(enableTopPadding ? 12 : 4), dp(12), dp(12));

        this.sectionRadiusTop = new float[] {
            dp(16), dp(16),
            dp(16), dp(16),
            0, 0,
            0, 0
        };
        this.sectionRadiusBottom = new float[] {
            0, 0,
            0, 0,
            dp(16), dp(16),
            dp(16), dp(16)
        };
    }

    private ArrayList<Runnable> onScroll = new ArrayList<>();
    public void onScroll(Runnable listener) {
        onScroll.add(listener);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        for (Runnable listener : onScroll)
            listener.run();

        invalidate();
        contentView.invalidate();
    }

    private ArrayList<View> children = new ArrayList<>();
    private void gatherChildren(ViewGroup layout, float x, float y) {
        for (int i = 0; i < layout.getChildCount(); ++i) {
            final View child = layout.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof LinearLayout && ((LinearLayout) child).getOrientation() == LinearLayout.VERTICAL && x + child.getX() <= contentView.getPaddingLeft() && x + child.getX() + child.getWidth() >= contentView.getWidth() - contentView.getPaddingRight()) {
                gatherChildren((LinearLayout) child, x + child.getX(), y + child.getY());
            } else {
                children.add(child);
            }
        }
    }
    private float getChildX(View child) {
        if (child == contentView || !(child.getParent() instanceof View)) return child.getX();
        return getChildX((View) child.getParent()) + child.getX();
    }
    private float getChildY(View child) {
        if (child == contentView || !(child.getParent() instanceof View)) return child.getY();
        return getChildY((View) child.getParent()) + child.getY();
    }

    private void drawSectionsBackgrounds(Canvas canvas) {
        children.clear();
        gatherChildren(contentView, 0, 0);

        View start = null, prev = null;
        for (View child : children) {
            if (!isSectionView(child)) {
                drawSectionBackground(canvas, start, prev);
                start = prev = null;
                continue;
            }
            if (start != null && Math.abs(prev.getAlpha() - child.getAlpha()) > 0.1f) {
                drawSectionBackground(canvas, start, prev);
                start = null;
            }
            if (start == null) {
                start = child;
            }
            prev = child;
        }
        drawSectionBackground(canvas, start, prev);
    }

    private void drawSectionBackground(
        Canvas canvas,
        View from, View to
    ) {
        if (from == null || to == null) return;

        float fromTopMargin = 0, toBottomMargin = 0;
        ViewGroup.LayoutParams fromLp = from.getLayoutParams();
        ViewGroup.LayoutParams toLp = to.getLayoutParams();
        if (from.getParent() != contentView && fromLp instanceof MarginLayoutParams) {
            fromTopMargin = ((MarginLayoutParams) fromLp).topMargin;
        }
        if (to.getParent() != contentView && toLp instanceof MarginLayoutParams) {
            toBottomMargin = ((MarginLayoutParams) toLp).topMargin;
        }

        AndroidUtilities.rectTmp.set(
            contentView.getX() + getChildX(from),
            Math.max(getScrollY() - dp(16), contentView.getY() + getChildY(from) - fromTopMargin),
            contentView.getX() + getChildX(from) + from.getWidth(),
            Math.min(getHeight() + dp(16) + getScrollY(), contentView.getY() + getChildY(to) + to.getHeight() + toBottomMargin)
        );
        if (AndroidUtilities.rectTmp.bottom < AndroidUtilities.rectTmp.top) return;
        RecyclerListView.drawBackgroundRect(canvas, AndroidUtilities.rectTmp, dp(16), dp(16), from.getAlpha(), resourcesProvider);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        drawSectionsBackgrounds(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    private final Path clipPath = new Path();
    private void clipChild(Canvas canvas, View child) {
        if (child == null || !isSectionView(child))
            return;

        boolean prev, next;
        int position = contentView.indexOfChild(child);
        final View prevChild = position - 1 < 0 ? null : contentView.getChildAt(position - 1);
        final View nextChild = position + 1 >= contentView.getChildCount() ? null : contentView.getChildAt(position + 1);
        prev = prevChild != null && isSectionView(prevChild);
        next = nextChild != null && isSectionView(nextChild);

        AndroidUtilities.rectTmp.set(
            child.getX(),
            Math.max(getScrollY() - dp(16), contentView.getY() + child.getY()),
            child.getX() + child.getWidth(),
            Math.min(getHeight() + getScrollY() + dp(16), contentView.getY() + child.getY() + child.getHeight())
        );
        if (prev && next) {
            prev = child.getY() >= AndroidUtilities.rectTmp.top;
            next = child.getY() + child.getHeight() <= AndroidUtilities.rectTmp.bottom;
            if (prev && next) return;
        }
        if (!prev && !next) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadius, sectionRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!prev) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadiusTop, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!next) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadiusBottom, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
    }

    public static class SectionsLinearLayout extends LinearLayout {
        public SectionsLinearLayout(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (getParent() instanceof SectionsScrollView) {
                final SectionsScrollView scrollView = (SectionsScrollView) getParent();
                canvas.save();
                scrollView.clipChild(canvas, child);
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (getParent() instanceof SectionsScrollView) {
                ((SectionsScrollView) getParent()).invalidate();
            }
        }
    }
}
