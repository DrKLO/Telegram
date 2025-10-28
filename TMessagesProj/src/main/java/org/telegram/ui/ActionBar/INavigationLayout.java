package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.core.util.Supplier;

import org.telegram.ui.Components.BackButtonMenu;

import java.util.ArrayList;
import java.util.List;

public interface INavigationLayout {
    int REBUILD_FLAG_REBUILD_LAST = 1, REBUILD_FLAG_REBUILD_ONLY_LAST = 2;

    int FORCE_NOT_ATTACH_VIEW = -2;
    int FORCE_ATTACH_VIEW_AS_FIRST = -3;

    boolean presentFragment(NavigationParams params);
    boolean checkTransitionAnimation();
    boolean addFragmentToStack(BaseFragment fragment, int position);
    void removeFragmentFromStack(BaseFragment fragment, boolean immediate);
    List<BaseFragment> getFragmentStack();
    void setDelegate(INavigationLayoutDelegate INavigationLayoutDelegate);
    void closeLastFragment(boolean animated, boolean forceNoAnimation);
    DrawerLayoutContainer getDrawerLayoutContainer();
    void setDrawerLayoutContainer(DrawerLayoutContainer drawerLayoutContainer);
    void setRemoveActionBarExtraHeight(boolean removeExtraHeight);
    void setTitleOverlayText(String title, int titleId, Runnable action);
    void animateThemedValues(ThemeAnimationSettings settings, Runnable onDone);
    float getThemeAnimationValue();
    void setFragmentStackChangedListener(Runnable onFragmentStackChanged);
    boolean isTransitionAnimationInProgress();
    void resumeDelayedFragmentAnimation();
    boolean allowSwipe();

    boolean isInPassivePreviewMode();
    void setInBubbleMode(boolean bubbleMode);
    boolean isInBubbleMode();

    boolean isInPreviewMode();
    boolean isPreviewOpenAnimationInProgress();
    void movePreviewFragment(float dy);
    void expandPreviewFragment();
    void finishPreviewFragment();
    void setFragmentPanTranslationOffset(int offset);
    FrameLayout getOverlayContainerView();
    void setHighlightActionButtons(boolean highlight);
    float getCurrentPreviewFragmentAlpha();
    void drawCurrentPreviewFragment(Canvas canvas, Drawable foregroundDrawable);

    void drawHeaderShadow(Canvas canvas, int alpha, int y);

    boolean isSwipeInProgress();

    void onPause();
    void onResume();
    void onBackPressed();
    void onUserLeaveHint();
    void onLowMemory();
    boolean extendActionMode(Menu menu);
    void onActionModeStarted(Object mode);
    void onActionModeFinished(Object mode);
    void startActivityForResult(Intent intent, int requestCode);

    // TODO: Migrate them to be out of navigation layout
    Theme.MessageDrawable getMessageDrawableOutStart();
    Theme.MessageDrawable getMessageDrawableOutMediaStart();

    // TODO: Make something like FieldsContainer and put them there?
    List<BackButtonMenu.PulledDialog> getPulledDialogs();
    void setPulledDialogs(List<BackButtonMenu.PulledDialog> pulledDialogs);

    static INavigationLayout newLayout(Context context, boolean main) {
        return new ActionBarLayout(context, main);
    }

    static INavigationLayout newLayout(Context context, boolean main, Supplier<BottomSheet> supplier) {
        return new ActionBarLayout(context, main) {
            @Override
            public BottomSheet getBottomSheet() {
                return supplier.get();
            }
        };
    }

    default void removeFragmentFromStack(BaseFragment fragment) {
        removeFragmentFromStack(fragment, false);
    }
    default boolean isActionBarInCrossfade() {
        return false;
    }

    default boolean hasIntegratedBlurInPreview() {
        return false;
    }

    default void rebuildFragments(int flags) {
        if ((flags & REBUILD_FLAG_REBUILD_ONLY_LAST) != 0) {
            showLastFragment();
            return;
        }
        boolean last = (flags & REBUILD_FLAG_REBUILD_LAST) != 0;
        rebuildAllFragmentViews(last, last);
    }

    default void setBackgroundView(View backgroundView) {
        // Not always required
    }

    default void setUseAlphaAnimations(boolean useAlphaAnimations) {
        // Not always required
    }

    /**
     * @deprecated Should be replaced with {@link INavigationLayout#rebuildFragments(int)}
     */
    @Deprecated
    default void rebuildLogout() {
        // No-op usually, can contain hackfixes
    }

    /**
     * @deprecated Should be replaced with {@link INavigationLayout#rebuildFragments(int)}
     */
    @Deprecated
    default void showLastFragment() {}

    /**
     * @deprecated Should be replaced with {@link INavigationLayout#rebuildFragments(int)}
     */
    @Deprecated
    default void rebuildAllFragmentViews(boolean last, boolean showLastAfter) {}

    default void drawHeaderShadow(Canvas canvas, int y) {
        drawHeaderShadow(canvas, 0xFF, y);
    }

    default BaseFragment getBackgroundFragment() {
        return getFragmentStack().size() <= 1 ? null : getFragmentStack().get(getFragmentStack().size() - 2);
    }

    default BaseFragment getLastFragment() {
        return getFragmentStack().isEmpty() ? null : getFragmentStack().get(getFragmentStack().size() - 1);
    }

    default BaseFragment getSafeLastFragment() {
        if (getFragmentStack().isEmpty()) return null;
        for (int i = getFragmentStack().size() - 1; i >= 0; --i) {
            BaseFragment fragment = getFragmentStack().get(i);
            if (fragment == null || fragment.isFinishing() || fragment.isRemovingFromStack())
                continue;
            return fragment;
        }
        return null;
    }

    default <T extends BaseFragment> T findFragment(Class<T> clazz) {
        if (getFragmentStack().isEmpty()) return null;
        for (int i = getFragmentStack().size() - 1; i >= 0; --i) {
            BaseFragment fragment = getFragmentStack().get(i);
            if (fragment == null || fragment.isFinishing() || fragment.isRemovingFromStack())
                continue;
            if (clazz.isInstance(fragment))
                return (T) fragment;
        }
        return null;
    }

    default void animateThemedValues(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
        animateThemedValues(new ThemeAnimationSettings(theme, accentId, nightTheme, instant), null);
    }

    default void animateThemedValues(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant, Runnable onDone) {
        animateThemedValues(new ThemeAnimationSettings(theme, accentId, nightTheme, instant), onDone);
    }

    /**
     * @deprecated Deprecated in favor of {@link INavigationLayout#bringToFront(int)}
     */
    @Deprecated
    default void showFragment(int i) {
        bringToFront(i);
    }

    default void bringToFront(int i) {
        BaseFragment fragment = getFragmentStack().get(i);
        removeFragmentFromStack(fragment);
        addFragmentToStack(fragment);
        rebuildFragments(REBUILD_FLAG_REBUILD_ONLY_LAST);
    }

    default void removeAllFragments() {
        for (BaseFragment fragment : new ArrayList<>(getFragmentStack())) {
            removeFragmentFromStack(fragment);
        }
    }

    default Activity getParentActivity() {
        Context ctx = getView().getContext();
        if (ctx instanceof Activity) {
            return (Activity) ctx;
        }
        throw new IllegalArgumentException("NavigationLayout added in non-activity context!");
    }

    default ViewGroup getView() {
        if (this instanceof ViewGroup) {
            return (ViewGroup) this;
        }
        throw new IllegalArgumentException("You should override getView() if you're not inheriting from it.");
    }

    default void closeLastFragment() {
        closeLastFragment(true);
    }

    default void closeLastFragment(boolean animated) {
        closeLastFragment(animated, false);
    }

    default void setFragmentStack(List<BaseFragment> stack) {
        init(stack);
    }

    /**
     * @deprecated This method was replaced with {@link INavigationLayout#setFragmentStack(List)}
     */
    @Deprecated
    default void init(List<BaseFragment> stack) {
        throw new RuntimeException("Neither setFragmentStack(...) or init(...) were overriden!");
    }

    default void removeFragmentFromStack(int i) {
        if (i < 0 || i >= getFragmentStack().size()) {
            return;
        }
        removeFragmentFromStack(getFragmentStack().get(i));
    }

    default boolean addFragmentToStack(BaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    default boolean presentFragment(BaseFragment fragment) {
        return presentFragment(new NavigationParams(fragment));
    }

    default boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return presentFragment(new NavigationParams(fragment).setRemoveLast(removeLast));
    }

    default boolean presentFragmentAsPreview(BaseFragment fragment) {
        return presentFragment(new NavigationParams(fragment).setPreview(true));
    }

    default boolean presentFragmentAsPreviewWithMenu(BaseFragment fragment, ActionBarPopupWindow.ActionBarPopupWindowLayout menuView) {
        return presentFragment(new NavigationParams(fragment).setPreview(true).setMenuView(menuView));
    }

    /**
     * @deprecated You should use {@link INavigationLayout.NavigationParams} for advanced params
     */
    @Deprecated
    default boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, boolean check, boolean preview) {
        return presentFragment(new NavigationParams(fragment).setRemoveLast(removeLast).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(check).setPreview(preview));
    }

    /**
     * @deprecated You should use {@link INavigationLayout.NavigationParams} for advanced params
     */
    @Deprecated
    default boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, boolean check, boolean preview, ActionBarPopupWindow.ActionBarPopupWindowLayout menuView) {
        return presentFragment(new NavigationParams(fragment).setRemoveLast(removeLast).setNoAnimation(forceWithoutAnimation).setCheckPresentFromDelegate(check).setPreview(preview).setMenuView(menuView));
    }

    default void dismissDialogs() {
        List<BaseFragment> fragmentsStack = getFragmentStack();
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.dismissCurrentDialog();
        }
    }

    default Window getWindow() {
        if (getParentActivity() != null) {
            return getParentActivity().getWindow();
        }
        return null;
    }

    default BottomSheet getBottomSheet() {
        return null;
    }

    void setIsSheet(boolean isSheet);

    boolean isSheet();

    void updateTitleOverlay();

    void setWindow(Window window);

    interface INavigationLayoutDelegate {
        default boolean needPresentFragment(INavigationLayout layout, NavigationParams params) {
            return needPresentFragment(params.fragment, params.removeLast, params.noAnimation, layout);
        }

        /**
         * @deprecated You should override {@link INavigationLayoutDelegate#needPresentFragment(INavigationLayout, NavigationParams)} for more fields
         */
        default boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, INavigationLayout layout) {
            return true;
        }

        default boolean needAddFragmentToStack(BaseFragment fragment, INavigationLayout layout) {
            return true;
        }

        default boolean onPreIme() {
            return false;
        }

        default boolean needCloseLastFragment(INavigationLayout layout) {
            return true;
        }

        default void onMeasureOverride(int[] measureSpec) {}
        default void onRebuildAllFragments(INavigationLayout layout, boolean last) {}
        default void onThemeProgress(float progress) {}
    }

    class NavigationParams {
        public BaseFragment fragment;
        public boolean removeLast;
        public boolean noAnimation;
        public boolean checkPresentFromDelegate = true;
        public boolean preview;
        public ActionBarPopupWindow.ActionBarPopupWindowLayout menuView;
        public boolean needDelayWithoutAnimation;

        public boolean isFromDelay;
        public boolean delayDone;

        public NavigationParams(BaseFragment fragment) {
            this.fragment = fragment;
        }

        public NavigationParams setRemoveLast(boolean removeLast) {
            this.removeLast = removeLast;
            return this;
        }

        public NavigationParams setNoAnimation(boolean noAnimation) {
            this.noAnimation = noAnimation;
            return this;
        }

        public NavigationParams setCheckPresentFromDelegate(boolean checkPresentFromDelegate) {
            this.checkPresentFromDelegate = checkPresentFromDelegate;
            return this;
        }

        public NavigationParams setPreview(boolean preview) {
            this.preview = preview;
            return this;
        }

        public NavigationParams setMenuView(ActionBarPopupWindow.ActionBarPopupWindowLayout menuView) {
            this.menuView = menuView;
            return this;
        }

        public NavigationParams setNeedDelayWithoutAnimation(boolean needDelayWithoutAnimation) {
            this.needDelayWithoutAnimation = needDelayWithoutAnimation;
            return this;
        }
    }

    class ThemeAnimationSettings {
        public final Theme.ThemeInfo theme;
        public final int accentId;
        public final boolean nightTheme;
        public final boolean instant;
        public boolean onlyTopFragment;
        public boolean applyTheme = true;
        public boolean applyTrulyTheme = true;
        public Runnable afterStartDescriptionsAddedRunnable;
        public Runnable beforeAnimationRunnable;
        public Runnable afterAnimationRunnable;
        public onAnimationProgress animationProgress;
        public long duration = 200;
        public Theme.ResourcesProvider resourcesProvider;

        public ThemeAnimationSettings(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
            this.theme = theme;
            this.accentId = accentId;
            this.nightTheme = nightTheme;
            this.instant = instant;
        }

        public interface onAnimationProgress {
            void setProgress(float p);
        }
    }

    class StartColorsProvider implements Theme.ResourcesProvider {
        SparseIntArray colors = new SparseIntArray();
        int[] keysToSave = new int[] {
                Theme.key_chat_outBubble,
                Theme.key_chat_outBubbleGradient1,
                Theme.key_chat_outBubbleGradient2,
                Theme.key_chat_outBubbleGradient3,
                Theme.key_chat_outBubbleGradientAnimated,
                Theme.key_chat_outBubbleShadow
        };

        @Override
        public int getColor(int key) {
            int index = colors.indexOfKey(key);
            if (index >= 0) {
                return colors.valueAt(index);
            }
            return Theme.getColor(key);
        }

        @Override
        public int getCurrentColor(int key) {
            return colors.get(key);
        }

        public void saveColors(Theme.ResourcesProvider fragmentResourceProvider) {
            colors.clear();
            for (int key : keysToSave) {
                colors.put(key, fragmentResourceProvider.getCurrentColor(key));
            }
        }
    }

    void setNavigationBarColor(int color);

    default int getBottomTabsHeight(boolean animated) {
        return 0;
    }

    default BottomSheetTabs getBottomSheetTabs() {
        return null;
    }
    enum BackButtonState {
        BACK,
        MENU
    }

    interface IBackButtonDrawable {
        BackButtonState getBackButtonState();
    }
}
