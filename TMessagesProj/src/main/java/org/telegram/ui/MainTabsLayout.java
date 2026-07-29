package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.render.SurfaceTouchLighting;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.LayeredCaptureSource;
import org.telegram.ui.Components.glass.GlassTabView;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ClickHelper;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private BlurredBackgroundDrawable liquidSelectorDrawable;
    private BlurredBackgroundDrawable liquidTabsBackground;
    private BlurredBackgroundDrawable liquidHiddenTabsBackground;
    private LayeredCaptureSource layeredCaptureSource;
    private final Paint liquidTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path liquidSelectorPath = new Path();
    private final RectF liquidSelectorRect = new RectF();
    private final RectF liquidPanelBounds = new RectF();
    private BlendModeColorFilter liquidTintColorFilter;
    private int liquidTintColor;
    private float liquidPressProgress;
    private float liquidSelectorScaleX = 1f;
    private float liquidSelectorScaleY = 1f;
    private float liquidSelectorCenterX;
    private float liquidSelectorVelocity;
    private long liquidSelectorPositionTime;
    private float liquidInteractiveProgress;
    private float liquidSelectorRadius = Float.NaN;
    private boolean liquidReleasePending;
    private float liquidReleaseTargetX;
    private float liquidPanelDragOffset;
    private float liquidPanelOffsetX;
    private float liquidAppliedPanelOffsetX;
    private float liquidLastTouchX;
    private SurfaceTouchLighting surfaceTouchLighting;
    private boolean liquidTracking;
    private View liquidTargetTab;
    private View liquidDownTab;
    private float liquidDownX;
    private float liquidDownY;
    private float liquidLastTouchY;
    private long liquidDownTime;
    private int liquidPointerId = MotionEvent.INVALID_POINTER_ID;
    private LiquidGestureState liquidGestureState = LiquidGestureState.IDLE;
    private final Runnable liquidLongPressRunnable = this::startPendingLiquidLongPress;

    private enum LiquidGestureState {
        IDLE,
        PENDING_LONG_PRESS,
        TRACKING
    }

    public void installGlassSelectionRenderer(
        BlurredBackgroundDrawableViewFactory factory,
        BlurredBackgroundDrawable tabsBackground,
        BlurredBackgroundDrawable hiddenTabsBackground,
        LayeredCaptureSource captureSource
    ) {
        liquidTabsBackground = tabsBackground;
        liquidHiddenTabsBackground = hiddenTabsBackground;
        layeredCaptureSource = captureSource;
        liquidSelectorDrawable = factory.create(null, new BlurredBackgroundColorProvider() {
            @Override
            public int getShadowColor() {
                return 0x1A000000;
            }

            @Override
            public int getBackgroundColor() {
                final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                final int color = dark ? Color.WHITE : Color.BLACK;
                return Theme.multAlpha(color, 0.1f * (1f - liquidPressProgress));
            }

            @Override
            public int getStrokeColorTop() {
                return 0;
            }

            @Override
            public int getStrokeColorBottom() {
                return 0;
            }
        });
        liquidSelectorDrawable
            .setSpectralSeparationEnabled(true)
            .setBackdropSaturation(1f)
            .setSurfaceBlurRadius(0f)
            .setEdgeLightingStrength(0f)
            .setThickness(1)
            .setOpticalDisplacement(0f);
        liquidSelectorDrawable.setShadowParams(dp(24), 0, dp(4));
        liquidSelectorDrawable.setShadowAlpha(0f);
        surfaceTouchLighting = new SurfaceTouchLighting();
        setBackground(null);
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSplitSelectionRendering(true);
            }
        }
        setSkipDrawSelector(true);
        drawCustomSelector = false;
        syncLiquidSelectorToSelectedTab();
    }

    public void renderSelectionCapture(Canvas canvas) {
        if (liquidTabsBackground == null) {
            return;
        }

        final int accent = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        canvas.save();
        canvas.translate(liquidTabsBackground.getSourceOffsetX(), liquidTabsBackground.getSourceOffsetY());
        liquidHiddenTabsBackground.draw(canvas);

        final float contentScale = lerp(1f, 1.2f, liquidPressProgress);
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            canvas.save();
            canvas.translate(child.getX(), child.getY());
            canvas.scale(contentScale, contentScale, child.getWidth() * 0.5f, child.getHeight() * 0.5f);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).renderSelectedPresentation(canvas, accent);
            } else {
                if (liquidTintColorFilter == null || liquidTintColor != accent) {
                    liquidTintColor = accent;
                    liquidTintColorFilter = new BlendModeColorFilter(accent, BlendMode.SRC_IN);
                }
                liquidTintPaint.setColorFilter(liquidTintColorFilter);
                final int layer = canvas.saveLayer(
                    0f,
                    0f,
                    child.getWidth(),
                    child.getHeight(),
                    liquidTintPaint
                );
                child.draw(canvas);
                canvas.restoreToCount(layer);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};
    private static final float LIQUID_PRESSED_SCALE = 80f / 56f;

    private int maxWidthPx;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        if (liquidSelectorDrawable == null) {
            measureLegacyTabs(height, tabHeight, maxTotalWidthForTabs);
            return;
        }

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            final float equalTabWidth = maxTotalWidthForTabs / (float) Math.max(1, visibleChildCount);
            boolean fits = true;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) {
                    continue;
                }
                if (tabsTextWidth[a] + padding * 2 > equalTabWidth) {
                    fits = false;
                    break;
                }
            }
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        applyPassTextSize(chosenPass);

        final int equalWidth = maxTotalWidthForTabs / Math.max(1, visibleChildCount);
        int remainingPixels = maxTotalWidthForTabs - equalWidth * visibleChildCount;
        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                tabsWidth[a] = 0;
                continue;
            }
            tabsWidth[a] = equalWidth + (remainingPixels-- > 0 ? 1 : 0);
            l += tabsWidth[a];
        }
        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    private void measureLegacyTabs(int height, int tabHeight, int maxTotalWidthForTabs) {
        final int minTotalWidthForTabs = Math.min(dp(320), maxTotalWidthForTabs);
        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) {
                    continue;
                }
                total += tabsTextWidth[a] + padding * 2;
            }
            if (total <= maxTotalWidthForTabs || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        applyPassTextSize(chosenPass);
        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq =
            maxTotalWidthForTabs / Math.max(1, visibleChildCount) - tabPadding * 2;
        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }
            tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
            tabsWeight[a] =
                tabsTextWidthWithMargin[a] > maxTabTextWidthIfEq + tabPadding * 2 ? 0 : 1;
            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float multiplier = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= multiplier;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growPerWeight = (minTotalWidthForTabs - totalWidth) / totalWeight;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] += growPerWeight * tabsWeight[a];
            }
        }

        int measuredWidth = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                tabsWidth[a] = 0;
                continue;
            }
            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            measuredWidth += tabsWidth[a];
        }
        setMeasuredDimension(measuredWidth + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY)
            );
        }
        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }



    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkVisualWidth();
        if (liquidTabsBackground != null) {
            liquidTabsBackground.setBounds(0, 0, getWidth(), getHeight());
            liquidPanelBounds.set(liquidTabsBackground.getPaddedBounds());
            liquidHiddenTabsBackground.setBounds(
                Math.round(liquidPanelBounds.left),
                getPaddingTop(),
                Math.round(liquidPanelBounds.right),
                getHeight() - getPaddingBottom()
            );
        }
        if (!liquidTracking && !liquidPositionAnimation.isRunning()) {
            syncLiquidSelectorToSelectedTab();
        }
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkVisualWidth();
    }

    @Override
    public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
        super.onDescendantInvalidated(child, target);
        if (layeredCaptureSource != null) {
            layeredCaptureSource.invalidateConsumers();
        }
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }








    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
            }
        }
        if (liquidSelectorDrawable != null && tab != null && !liquidTracking) {
            moveLiquidSelectorToTab(tab, animated);
        }
    }

    private void moveLiquidSelectorToTab(View tab, boolean animated) {
        final float targetX = getCenterX(tab);
        if (!animated) {
            liquidPositionAnimation.cancel();
            liquidVelocityAnimation.cancel();
            liquidPressAnimation.cancel();
            liquidScaleXAnimation.cancel();
            liquidScaleYAnimation.cancel();
            liquidSelectorCenterX = targetX;
            liquidSelectorVelocity = 0f;
            liquidPressProgress = 0f;
            liquidSelectorScaleX = 1f;
            liquidSelectorScaleY = 1f;
            liquidReleasePending = false;
            invalidateLiquidContent();
            return;
        }
        if (liquidReleasePending && Math.abs(liquidReleaseTargetX - targetX) < 0.5f) {
            return;
        }
        if (!liquidReleasePending && Math.abs(liquidSelectorCenterX - targetX) < 0.5f) {
            return;
        }
        liquidReleaseTargetX = targetX;
        liquidReleasePending = true;
        liquidPressAnimation.animateToFinalPosition(1f);
        liquidScaleXAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        liquidScaleYAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        liquidPositionAnimation.getSpring().setStiffness(1000f).setDampingRatio(1f);
        liquidPositionAnimation.animateToFinalPosition(targetX);
        postOnAnimation(this::maybeFinishLiquidRelease);
    }

    private View findSelectedTab() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    private final Runnable restoreDrawSelector = () -> setSkipDrawSelector(false);

    private boolean drawCustomSelector;
    private void setSkipDrawSelector(boolean skipDrawSelector) {
        skipDrawSelector = skipDrawSelector || liquidSelectorDrawable != null;
        drawCustomSelector = skipDrawSelector && liquidSelectorDrawable == null;
        if (drawCustomSelector) {
            selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f));
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skipDrawSelector);
            }
        }
        invalidate();
    }







    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final boolean liquid = liquidSelectorDrawable != null;
        if (liquid) {
            applyLiquidPanelSourceOffset();
            canvas.save();
            canvas.translate(liquidPanelOffsetX, 0f);
        }
        if (liquidSelectorDrawable != null) {
            final float panelScale = getLiquidPanelScale();
            canvas.save();
            canvas.scale(panelScale, panelScale, getWidth() * 0.5f, getHeight() * 0.5f);
            liquidTabsBackground.draw(canvas);
            surfaceTouchLighting.render(
                canvas,
                liquidPanelBounds,
                liquidSelectorCenterX,
                getHeight() * 0.5f,
                liquidInteractiveProgress
            );
            canvas.restore();
            drawLiquidSelector(canvas);
        }
        if (drawCustomSelector) {
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();

            canvas.drawRoundRect(
                    x - sWidth / 2f, (getHeight() - sHeight) / 2f,
                    x + sWidth / 2f, (getHeight() + sHeight) / 2f,
                    sHeight / 2f, sHeight / 2f, selectorPaint);
        }
        if (liquidSelectorDrawable != null && !liquidSelectorRect.isEmpty()) {
            canvas.save();
            liquidSelectorPath.rewind();
            liquidSelectorPath.addRoundRect(
                liquidSelectorRect,
                liquidSelectorRect.height() * 0.5f,
                liquidSelectorRect.height() * 0.5f,
                Path.Direction.CW
            );
            canvas.clipOutPath(liquidSelectorPath);
            final float panelScale = getLiquidPanelScale();
            canvas.scale(panelScale, panelScale, getWidth() * 0.5f, getHeight() * 0.5f);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
        if (liquid) {
            canvas.restore();
        }
    }

    private void applyLiquidPanelSourceOffset() {
        final float baseOffsetX = liquidTabsBackground.getSourceOffsetX() - liquidAppliedPanelOffsetX;
        final float offsetY = liquidTabsBackground.getSourceOffsetY();
        liquidAppliedPanelOffsetX = liquidPanelOffsetX;
        liquidTabsBackground.setSourceOffset(baseOffsetX + liquidPanelOffsetX, offsetY);
        liquidHiddenTabsBackground.setSourceOffset(baseOffsetX + liquidPanelOffsetX, offsetY);
        liquidSelectorDrawable.setSourceOffset(baseOffsetX + liquidPanelOffsetX, offsetY);
    }

    private float getLiquidPanelScale() {
        return getWidth() > 0
            ? 1f + dp(16) / (float) getWidth() * liquidPressProgress
            : 1f;
    }

    private void drawLiquidSelector(Canvas canvas) {
        final float normalWidth = getInterpolatedWidthByX(liquidSelectorCenterX, this);
        final float normalHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (normalWidth <= 0f || normalHeight <= 0f) {
            liquidSelectorRect.setEmpty();
            return;
        }
        final float velocity = liquidSelectorVelocity / 10f;
        final float velocityX = Math.max(-0.2f, Math.min(0.2f, velocity * 0.75f));
        final float velocityY = Math.max(-0.2f, Math.min(0.2f, velocity * 0.25f));
        final float scaleX = liquidSelectorScaleX / (1f - velocityX);
        final float scaleY = liquidSelectorScaleY * (1f - velocityY);
        final float scaledWidth = normalWidth * scaleX;
        final float scaledHeight = normalHeight * scaleY;
        final float centerY = getHeight() * 0.5f;
        final float selectorRadius = scaledHeight * 0.5f;
        if (Float.isNaN(liquidSelectorRadius) || Math.abs(liquidSelectorRadius - selectorRadius) > 0.1f) {
            liquidSelectorRadius = selectorRadius;
            liquidSelectorDrawable.setRadius(selectorRadius);
        }
        liquidSelectorDrawable.setThickness(
            Math.max(1, Math.round(dp(10) * liquidPressProgress * scaleY))
        );
        liquidSelectorDrawable.setOpticalDisplacement(
            dp(14) * liquidPressProgress * scaleY
        );

        liquidSelectorRect.set(
            liquidSelectorCenterX - scaledWidth * 0.5f,
            centerY - scaledHeight * 0.5f,
            liquidSelectorCenterX + scaledWidth * 0.5f,
            centerY + scaledHeight * 0.5f
        );

        liquidSelectorDrawable.setBounds(
            Math.round(liquidSelectorRect.left),
            Math.round(liquidSelectorRect.top),
            Math.round(liquidSelectorRect.right),
            Math.round(liquidSelectorRect.bottom)
        );
        liquidSelectorDrawable.setSourceOffset(
            liquidTabsBackground.getSourceOffsetX(),
            liquidTabsBackground.getSourceOffsetY()
        );
        liquidHiddenTabsBackground.setSourceOffset(
            liquidTabsBackground.getSourceOffsetX(),
            liquidTabsBackground.getSourceOffsetY()
        );
        liquidSelectorDrawable.draw(canvas);
    }

    private float getLiquidCentersRange() {
        float first = Float.NaN;
        float last = Float.NaN;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            final float center = getCenterX(child);
            if (Float.isNaN(first)) {
                first = center;
            }
            last = center;
        }
        return Float.isNaN(first) || Float.isNaN(last) ? 0f : last - first;
    }

    private void syncLiquidSelectorToSelectedTab() {
        if (liquidSelectorDrawable == null) {
            return;
        }
        final View selected = findSelectedTab();
        if (selected == null || selected.getWidth() == 0) {
            return;
        }
        liquidSelectorCenterX = getCenterX(selected);
        liquidSelectorVelocity = 0f;
        liquidSelectorPositionTime = 0L;
        liquidPositionAnimation.setStartValue(liquidSelectorCenterX);
        invalidateLiquidGeometry();
    }

    private void invalidateLiquidContent() {
        if (liquidSelectorDrawable == null) {
            return;
        }
        liquidSelectorDrawable.setEdgeLightingStrength(liquidPressProgress);
        liquidSelectorDrawable.setShadowAlpha(liquidPressProgress);
        liquidSelectorDrawable.setSurfaceTintColor(
            Theme.multAlpha(Color.BLACK, 0.03f * liquidPressProgress)
        );
        liquidSelectorDrawable.setInsetShadow(
            dp(8) * liquidPressProgress,
            liquidPressProgress
        );
        liquidHiddenTabsBackground.setThickness(Math.max(1, Math.round(dp(24) * liquidPressProgress)));
        liquidHiddenTabsBackground.setOpticalDisplacement(dp(24) * liquidPressProgress);
        liquidHiddenTabsBackground.setEdgeLightingStrength(liquidPressProgress);
        liquidHiddenTabsBackground.updateColors();
        liquidSelectorDrawable.updateColors();
        layeredCaptureSource.invalidateConsumers();
        invalidate();
    }

    private void invalidateLiquidGeometry() {
        if (liquidSelectorDrawable != null) {
            invalidate();
        }
    }

    public void syncGlassSelectionPalette() {
        if (liquidSelectorDrawable == null) {
            invalidate();
            return;
        }
        liquidTabsBackground.updateColors();
        liquidHiddenTabsBackground.updateColors();
        liquidSelectorDrawable.updateColors();
        layeredCaptureSource.invalidateConsumers();
        invalidate();
    }


    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);
    final SpringAnimation liquidPressAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidPressProgress") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidPressProgress;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.liquidPressProgress = value;
            object.invalidateLiquidContent();
        }
    });
    final SpringAnimation liquidScaleXAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidSelectorScaleX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidSelectorScaleX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.liquidSelectorScaleX = value;
            object.invalidateLiquidGeometry();
        }
    });
    final SpringAnimation liquidScaleYAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidSelectorScaleY") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidSelectorScaleY;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.liquidSelectorScaleY = value;
            object.invalidateLiquidGeometry();
        }
    });
    final SpringAnimation liquidInteractiveAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidInteractiveProgress") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidInteractiveProgress;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.liquidInteractiveProgress = value;
            object.invalidate();
        }
    });
    final SpringAnimation liquidVelocityAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidSelectorVelocity") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidSelectorVelocity;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.liquidSelectorVelocity = value;
            object.invalidateLiquidGeometry();
        }
    });
    final SpringAnimation liquidPanelOffsetAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidPanelDragOffset") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidPanelDragOffset;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.setLiquidPanelDragOffset(value);
        }
    });
    final SpringAnimation liquidPositionAnimation = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("liquidSelectorCenterX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.liquidSelectorCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            final long now = System.nanoTime();
            if (object.liquidTracking && object.liquidSelectorPositionTime != 0L) {
                final float dt = (now - object.liquidSelectorPositionTime) / 1_000_000_000f;
                if (dt > 0f) {
                    final float range = Math.max(object.getLiquidCentersRange(), 1f);
                    final float velocity = (value - object.liquidSelectorCenterX) / dt / range;
                    object.liquidVelocityAnimation.animateToFinalPosition(velocity);
                }
            }
            object.liquidSelectorPositionTime = now;
            object.liquidSelectorCenterX = value;
            object.maybeFinishLiquidRelease();
            object.invalidateLiquidGeometry();
        }
    });

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        scaleX.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        scaleY.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        selectedTabPositionX.setSpring(new SpringForce(1f)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        liquidPressAnimation.setSpring(new SpringForce(0f)
            .setStiffness(1000f)
            .setDampingRatio(1f));
        liquidScaleXAnimation.setSpring(new SpringForce(1f)
            .setStiffness(250f)
            .setDampingRatio(0.6f));
        liquidScaleYAnimation.setSpring(new SpringForce(1f)
            .setStiffness(250f)
            .setDampingRatio(0.7f));
        liquidPositionAnimation.setSpring(new SpringForce(0f)
            .setStiffness(1000f)
            .setDampingRatio(1f));
        liquidVelocityAnimation.setSpring(new SpringForce(0f)
            .setStiffness(300f)
            .setDampingRatio(0.5f));
        liquidPanelOffsetAnimation.setSpring(new SpringForce(0f)
            .setStiffness(300f)
            .setDampingRatio(0.5f));
        liquidInteractiveAnimation.setSpring(new SpringForce(0f)
            .setStiffness(300f)
            .setDampingRatio(0.5f));
        liquidPressAnimation.setMinimumVisibleChange(0.001f);
        liquidScaleXAnimation.setMinimumVisibleChange(0.001f);
        liquidScaleYAnimation.setMinimumVisibleChange(0.001f);
        liquidVelocityAnimation.setMinimumVisibleChange(0.01f);
        liquidPanelOffsetAnimation.setMinimumVisibleChange(0.001f);
        liquidInteractiveAnimation.setMinimumVisibleChange(0.001f);
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;

    private boolean isInLongPress;
    private float lastLongSelectedViewCenterX;
    private float lastLongSelectedViewWidth;
    private View lastLongSelectedView;

    private final Set<View> tabsWithIgnoreClick = new HashSet<>();

    public void addTabToIgnoreClick(View view) {
        tabsWithIgnoreClick.add(view);
    }




    public static View findChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE) continue;

            if (x >= child.getX() && x <= child.getX() + child.getWidth()
                    && y >= child.getY() && y <= child.getY() + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    private static View findLegacyChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (x >= child.getLeft() &&
                x <= child.getRight() &&
                y >= child.getTop() &&
                y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void checkLongMove(float x_, float y, boolean start, boolean end) {
        final float x = clampXToChildrenCenters(x_, this);
        final View found = findNearestVisibleChildByX(x, this);
        if (start) {
            View selected = findSelectedTab();
            if (selected != null) {
                animatedLongSelectedViewCenterX = selected.getX() + selected.getWidth() / 2f;
                animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX - x;
                selectedTabPositionOffsetX.animateToFinalPosition(0);
                if (selected != found && found != null) {
                    found.performClick();
                }
            }
            selectedTabPositionX.cancel();
        }

        if (!end) {
            animatedLongSelectedViewCenterX = x;
            invalidate();
        }

        if (found != null) {
            lastLongSelectedView = found;
            setTabSelected(found, true);

            if (end) {
                final float vw = found.getWidth();
                final float cx = found.getX() + vw / 2f;
                if (lastLongSelectedViewWidth != vw || lastLongSelectedViewCenterX != cx) {
                    selectedTabPositionX.animateToFinalPosition(cx);
                }
            }
        }
    }

    private final BoolAnimator animatorIsScaled = new BoolAnimator(0, (a, factor, c, g) -> {
        setScaleX(lerp(1, 1.019f, factor));
        setScaleY(lerp(1, 1.019f, factor));
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            lastLongSelectedView = null;
            final View found = findLegacyChildUnder(MainTabsLayout.this, x, y);
            return found != null && !tabsWithIgnoreClick.contains(found);
        }

        @Override
        public void onClickAt(View view, float x, float y) {
        }

        @Override
        public boolean needLongPress(float x, float y) {
            return true;
        }

        @Override
        public boolean needCancelTouchBySlopMove() {
            return false;
        }


        @Override
        public boolean onLongPressRequestedAt(View view, float x, float y) {
            checkPivot(view, x, y);
            isInLongPress = true;
            AndroidUtilities.cancelRunOnUIThread(restoreDrawSelector);
            setSkipDrawSelector(true);
            checkLongMove(x, y, true, false);
            invalidate();
            longTouchStart();
            return true;
        }

        @Override
        public void onLongPressMove(View view, MotionEvent e, float x, float y, float startX, float startY) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, false);
            invalidate();
        }

        @Override
        public long getLongPressDuration() {
            return ClickHelper.Delegate.super.getLongPressDuration() * 750 / 1000;
        }

        @Override
        public void onLongPressFinish(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            if (lastLongSelectedView != null) {
                lastLongSelectedView.performClick();
            }
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        @Override
        public void onLongPressCancelled(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        private void longTouchStart() {
            animatorIsScaled.setValue(true, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(-0.45f);
                scaleY.setStartVelocity(-0.45f);
            }
            scaleX.animateToFinalPosition(1.012f);
            scaleY.animateToFinalPosition(1.012f);
            */
        }

        private void longTouchEnd() {
            animatorIsScaled.setValue(false, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(0.25f);
                scaleY.setStartVelocity(0.25f);
            }
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
            */
        }
    });

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        checkLayerType();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        checkLayerType();
    }

    private void checkLayerType() {
        final int layerType = Math.abs(getScaleX() - 1f) < 0.0001f && Math.abs(getScaleY() - 1f) < 0.0001f ?
            View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE;

        if (getLayerType() != layerType) {
            setLayerType(layerType, null);
            invalidate();
        }
    }


    private void checkPivot(View view, float x, float y) {
        float w = view.getWidth();
        float h = view.getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        float dx = x - cx;
        float dy = y - cy;

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        float nx = dx / halfW;
        float ny = dy / halfH;

        float r = (float) Math.sqrt(nx * nx + ny * ny);

        float pivotX;
        float pivotY;

        if (r > 1e-4f) {
            float mappedR = 1.5f * r / (r + 0.5f);

            float scale = mappedR / r;
            pivotX = cx + dx * scale;
            pivotY = cy + dy * scale;
        } else {
            pivotX = cx;
            pivotY = cy;
        }

        pivotX = lerp(cx, pivotX, 1f);
        pivotY = lerp(cy, pivotY, 3f);

        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (liquidSelectorDrawable != null) {
            return dispatchLiquidTouchEvent(ev);
        }
        clickHelper.onTouchEvent(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private boolean dispatchLiquidTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelPendingLiquidLongPress();
            liquidPointerId = ev.getPointerId(0);
            liquidDownTime = ev.getDownTime();
            liquidLastTouchX = ev.getX();
            liquidLastTouchY = ev.getY();
            liquidDownX = liquidLastTouchX;
            liquidDownY = liquidLastTouchY;
            liquidDownTab = findChildUnder(this, liquidLastTouchX, liquidLastTouchY);
            if (liquidDownTab == null) {
                liquidGestureState = LiquidGestureState.IDLE;
                return super.dispatchTouchEvent(ev);
            }
            if (liquidDownTab == findSelectedTab()) {
                liquidGestureState = LiquidGestureState.TRACKING;
                startLiquidTracking(liquidDownTab, liquidLastTouchX);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            liquidGestureState = LiquidGestureState.PENDING_LONG_PRESS;
            postDelayed(
                liquidLongPressRunnable,
                ViewConfiguration.getLongPressTimeout() * 3L / 4L
            );
            return super.dispatchTouchEvent(ev);
        }

        final int pointerIndex = liquidPointerId == MotionEvent.INVALID_POINTER_ID
            ? -1
            : ev.findPointerIndex(liquidPointerId);
        if (pointerIndex >= 0) {
            liquidLastTouchX = ev.getX(pointerIndex);
            liquidLastTouchY = ev.getY(pointerIndex);
        }

        if (liquidGestureState == LiquidGestureState.PENDING_LONG_PRESS) {
            final boolean activePointerUp =
                action == MotionEvent.ACTION_POINTER_UP &&
                    ev.getPointerId(ev.getActionIndex()) == liquidPointerId;
            final float dx = liquidLastTouchX - liquidDownX;
            final float dy = liquidLastTouchY - liquidDownY;
            final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL ||
                activePointerUp ||
                pointerIndex < 0 ||
                dx * dx + dy * dy > touchSlop * touchSlop) {
                cancelPendingLiquidLongPress();
            }
            return super.dispatchTouchEvent(ev);
        }

        if (liquidGestureState == LiquidGestureState.TRACKING) {
            if (action == MotionEvent.ACTION_MOVE) {
                moveLiquidTracking(liquidLastTouchX);
            } else if (action == MotionEvent.ACTION_UP) {
                finishLiquidTracking(true);
                resetLiquidGesture();
            } else if (action == MotionEvent.ACTION_CANCEL ||
                action == MotionEvent.ACTION_POINTER_UP &&
                    ev.getPointerId(ev.getActionIndex()) == liquidPointerId) {
                finishLiquidTracking(false);
                resetLiquidGesture();
            }
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    private void startPendingLiquidLongPress() {
        if (liquidGestureState != LiquidGestureState.PENDING_LONG_PRESS ||
            liquidDownTab == null ||
            !isViewVisible(liquidDownTab)) {
            return;
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        liquidGestureState = LiquidGestureState.TRACKING;
        startLiquidTrackingFromInactive(
            liquidDownTab,
            liquidLastTouchX,
            liquidLastTouchY
        );
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void cancelPendingLiquidLongPress() {
        removeCallbacks(liquidLongPressRunnable);
        if (liquidGestureState == LiquidGestureState.PENDING_LONG_PRESS) {
            liquidGestureState = LiquidGestureState.IDLE;
            liquidDownTab = null;
            liquidPointerId = MotionEvent.INVALID_POINTER_ID;
        }
    }

    private void resetLiquidGesture() {
        removeCallbacks(liquidLongPressRunnable);
        liquidGestureState = LiquidGestureState.IDLE;
        liquidDownTab = null;
        liquidPointerId = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private void startLiquidTrackingFromInactive(View target, float touchX, float touchY) {
        liquidTracking = true;
        liquidTargetTab = target;
        liquidReleasePending = false;
        liquidLastTouchX = touchX;
        liquidPositionAnimation.cancel();
        liquidVelocityAnimation.cancel();
        liquidSelectorVelocity = 0f;
        liquidSelectorPositionTime = 0L;
        liquidPressAnimation.animateToFinalPosition(1f);
        liquidInteractiveAnimation.animateToFinalPosition(1f);
        liquidScaleXAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        liquidScaleYAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        liquidPositionAnimation.getSpring().setStiffness(1000f).setDampingRatio(1f);
        liquidPositionAnimation.animateToFinalPosition(getCenterX(target));
        final MotionEvent cancel = MotionEvent.obtain(
            liquidDownTime,
            android.os.SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            touchX,
            touchY,
            0
        );
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        invalidateLiquidGeometry();
    }

    private void startLiquidTracking(View selected, float touchX) {
        liquidTracking = true;
        liquidTargetTab = selected;
        liquidReleasePending = false;
        liquidLastTouchX = touchX;
        liquidPositionAnimation.cancel();
        liquidVelocityAnimation.cancel();
        liquidSelectorCenterX = getCenterX(selected);
        liquidSelectorVelocity = 0f;
        liquidSelectorPositionTime = 0L;
        liquidPressAnimation.animateToFinalPosition(1f);
        liquidInteractiveAnimation.animateToFinalPosition(1f);
        liquidScaleXAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        liquidScaleYAnimation.animateToFinalPosition(LIQUID_PRESSED_SCALE);
        invalidateLiquidGeometry();
    }

    private void moveLiquidTracking(float x) {
        final float dragAmount = x - liquidLastTouchX;
        liquidLastTouchX = x;
        liquidPanelOffsetAnimation.cancel();
        setLiquidPanelDragOffset(liquidPanelDragOffset + dragAmount);
        x = clampXToChildrenCenters(x, this);
        liquidPositionAnimation.getSpring().setStiffness(1000f).setDampingRatio(1f);
        liquidPositionAnimation.animateToFinalPosition(x);
        final View found = findNearestVisibleChildByX(x, this);
        if (found != null && found != liquidTargetTab) {
            liquidTargetTab = found;
        }
    }

    private void finishLiquidTracking(boolean performClick) {
        liquidTracking = false;
        final View target;
        if (performClick) {
            target = liquidTargetTab;
        } else {
            target = findSelectedTab();
            liquidTargetTab = target;
        }
        if (target != null) {
            liquidReleaseTargetX = getCenterX(target);
            liquidReleasePending = true;
            liquidPositionAnimation.getSpring().setStiffness(1000f).setDampingRatio(1f);
            liquidPositionAnimation.animateToFinalPosition(liquidReleaseTargetX);
        }
        liquidVelocityAnimation.animateToFinalPosition(0f);
        liquidPanelOffsetAnimation.animateToFinalPosition(0f);
        liquidInteractiveAnimation.animateToFinalPosition(0f);
        postOnAnimation(this::maybeFinishLiquidRelease);
        if (performClick && target != null) {
            target.performClick();
        }
        liquidTargetTab = null;
    }

    private void setLiquidPanelDragOffset(float value) {
        liquidPanelDragOffset = value;
        final float fraction = getWidth() > 0
            ? Math.max(-1f, Math.min(1f, value / getWidth()))
            : 0f;
        liquidPanelOffsetX = dp(4)
            * Math.signum(fraction)
            * CubicBezierInterpolator.EASE_OUT.getInterpolation(Math.abs(fraction));
        invalidateLiquidContent();
    }

    private void maybeFinishLiquidRelease() {
        if (!liquidReleasePending) {
            return;
        }
        final float threshold = Math.max(getLiquidCentersRange() * 0.025f, 0.001f);
        if (Math.abs(liquidSelectorCenterX - liquidReleaseTargetX) >= threshold) {
            return;
        }
        liquidReleasePending = false;
        postOnAnimation(() -> {
            liquidPressAnimation.animateToFinalPosition(0f);
            liquidScaleXAnimation.animateToFinalPosition(1f);
            liquidScaleYAnimation.animateToFinalPosition(1f);
        });
    }


    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return right.getWidth();
        }

        if (right == null) {
            return left.getWidth();
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return left.getWidth();
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(left.getWidth(), right.getWidth(), ratio);
    }

    private static float getCenterX(View v) {
        return v.getX() + v.getWidth() * 0.5f;
    }
}
