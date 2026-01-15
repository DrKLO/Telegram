package org.telegram.ui;

import android.content.Context;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;

public abstract class ViewPagerActivity extends BaseFragment {
    protected final SparseArray<FragmentState> fragmentsArr = new SparseArray<>();

    protected FrameLayout contentView;
    protected ViewPagerFixed viewPager;
    private int initialFragmentPosition = -1;


    abstract protected int getStartPosition();

    abstract protected int getFragmentsCount();

    abstract protected BaseFragment createBaseFragmentAt(int position);


    protected void onViewPagerScrollEnd() {

    }

    protected void onViewPagerTabAnimationUpdate(boolean manual) {

    }


    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context);
    }

    @Override
    public View createView(Context context) {
        contentView = createContentView(context);

        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onScrollEnd() {
                super.onScrollEnd();
                onViewPagerScrollEnd();
                checkFragmentsVisibility();
            }

            @Override
            protected float getAvailableTranslationX() {
                return getMeasuredWidth();
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                super.onItemSelected(currentPage, oldPage, position, oldPosition);
                checkFragmentsVisibility();
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                onViewPagerTabAnimationUpdate(manual);
                checkFragmentsVisibility();
                checkSystemBarColors();
            }

            @Override
            protected boolean canScrollBackward(MotionEvent e) {
                return ViewPagerActivity.this.canScrollBackward(e);
            }

            @Override
            protected boolean canScrollForward(MotionEvent e) {
                return ViewPagerActivity.this.canScrollForward(e);
            }

            @Override
            protected long getManualScrollDuration() {
                return 380L;
            }
        };

        if (initialFragmentPosition == -1) {
            initialFragmentPosition = getStartPosition();
        }
        viewPager.setPosition(initialFragmentPosition);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return getFragmentsCount();
            }

            @Override
            public View createView(int viewType) {
                return new FrameLayout(context);
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                FragmentState state = fragmentsArr.get(position);
                final BaseFragment fragment;
                if (state != null) {
                    fragment = state.fragment;
                } else {
                    fragment = createBaseFragmentAt(position);

                    state = new FragmentState(fragment);
                    fragmentsArr.put(position, state);
                }

                if (!state.onCreateCalled) {
                    fragment.onFragmentCreate();
                    state.onCreateCalled = true;
                }

                fragment.setParentLayout(getParentLayout());
                if (fragment.getFragmentView() == null) {
                    fragment.createView(context);
                    fragment.setTitleOverlayText(titleOverlay, titleOverlayId, titleOverlayAction);
                }

                FrameLayout container = (FrameLayout) view;
                container.removeAllViews();
                AndroidUtilities.removeFromParent(fragment.getFragmentView());

                container.addView(fragment.getFragmentView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                if (fragment.getActionBar() != null && fragment.getActionBar().shouldAddToContainer()) {
                    AndroidUtilities.removeFromParent(fragment.getActionBar());
                    container.addView(fragment.getActionBar());
                }

                ViewCompat.requestApplyInsets(container);
                checkSystemBarColors();
                checkFragmentsVisibility();
            }
        });


        contentView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = contentView;
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, this::onApplyWindowInsets);
        return fragmentView;
    }

    protected void putFragmentAtPosition(int position, BaseFragment fragment) {
        fragmentsArr.put(position, new FragmentState(fragment));
    }

    protected boolean canScrollBackward(MotionEvent ev) {
        return true;
    }

    protected boolean canScrollForward(MotionEvent ev) {
        return true;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return null;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @NonNull
    protected WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            if (state != null) {
                final View view = state.fragment.getFragmentView();
                if (view != null) {
                    ViewCompat.dispatchApplyWindowInsets(view, insets);
                }
            }
        }

        return WindowInsetsCompat.CONSUMED;
    }

    public BaseFragment getCurrentVisibleFragment() {
        if (viewPager == null) {
            return null;
        }

        final int currentPosition = viewPager.getCurrentPosition();
        final FragmentState state = fragmentsArr.get(currentPosition);
        return state != null ? state.fragment : null;
    }

    @Override
    public void clearViews() {
        if (viewPager != null) {
            initialFragmentPosition = viewPager.getCurrentPosition();
        }
        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            if (state != null) {
                state.fragment.clearViews();
            }
        }

        super.clearViews();
    }

    protected void clearAllHiddenFragments() {
        final int visiblePosition = viewPager.getCurrentPosition();
        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            final int position = fragmentsArr.keyAt(a);
            if (position == visiblePosition) {
                continue;
            }

            if (state != null) {
                state.fragment.clearViews();
            }
        }
    }

    @Override
    public boolean isLightStatusBar() {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null && fragment.fragmentView != null ? fragment.isLightStatusBar() : super.isLightStatusBar();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (hasShownSheet()) {
            if (invoked) closeSheet();
            return false;
        }
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null && !fragment.onBackPressed(invoked))
            return false;
        return super.onBackPressed(invoked);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            if (state != null && state.fragment.fragmentView != null) {
                arrayList.addAll(state.fragment.getThemeDescriptions());
            }
        }

        return arrayList;
    }



    private float visibilityByParent = 0;
    private boolean isResumed;
    private boolean isFullyVisible;

    @Override
    public void onPause() {
        super.onPause();
        isResumed = false;
        checkFragmentsVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        isResumed = true;
        checkSystemBarColors();
        checkFragmentsVisibility();
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        super.onTransitionAnimationProgress(isOpen, progress);
        visibilityByParent = isOpen ? progress : (1f - progress);
        checkFragmentsVisibility();
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        visibilityByParent = 0f;
        isFullyVisible = false;
        checkFragmentsVisibility();
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        visibilityByParent = 1f;
        isFullyVisible = true;
        checkFragmentsVisibility();
        checkSystemBarColors();
    }

    private void checkFragmentsVisibility() {
        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            final int position = fragmentsArr.keyAt(a);
            if (state != null && state.fragment.fragmentView != null) {
                state.setVisibility(viewPager.getPositionVisibility(position), isResumed ? visibilityByParent : 0, isFullyVisible, isResumed);
            }
        }
    }

    private String titleOverlay;
    private int titleOverlayId;
    private Runnable titleOverlayAction;

    @Override
    public void setTitleOverlayTextIfActionBarAttached(String title, int titleId, Runnable action) {
        setTitleOverlayText(title, titleId, action);
    }

    @Override
    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        super.setTitleOverlayText(title, titleId, action);
        this.titleOverlay = title;
        this.titleOverlayId = titleId;
        this.titleOverlayAction = action;

        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            if (state != null) {
                state.fragment.setTitleOverlayText(title, titleId, action);
            }
        }
    }



    /* * */

    protected static class FragmentState {
        public final @NonNull BaseFragment fragment;
        private boolean onCreateCalled;

        private boolean isFullyVisible;
        private boolean isResumed;
        private boolean isInAnimation;

        private float lastVisibility;

        public void setVisibility(float visibilityByViewPage, float visibilityByParent, boolean parentIsFullyVisible, boolean parentIsResumed) {
            final float oldVisibility = lastVisibility;
            final float newVisibility = visibilityByViewPage * visibilityByParent;
            lastVisibility = newVisibility;

            final boolean isOpen = newVisibility > oldVisibility;
            final boolean backward = false; // todo: support backward

            if (!isResumed && visibilityByViewPage > 0) {
                fragment.onResume();
                isResumed = true;
            }
            if (!isInAnimation && (oldVisibility == 0 || oldVisibility == 1f) && oldVisibility != newVisibility && (Math.abs(oldVisibility - newVisibility) != 1f)) {
                fragment.onTransitionAnimationStart(isOpen, backward);
                isInAnimation = true;
            }
            if (isInAnimation && oldVisibility != newVisibility) {
                fragment.onTransitionAnimationProgress(isOpen, isOpen ? newVisibility : (1f - newVisibility));
            }
            if (isInAnimation && (newVisibility == 0 || newVisibility == 1f)) {
                fragment.onTransitionAnimationEnd(isOpen, backward);
                isInAnimation = false;
            }
            if (!isFullyVisible && newVisibility >= 1)  {
                fragment.onBecomeFullyVisible();
                isFullyVisible = true;
            }
            if (isFullyVisible && (newVisibility == 0 && !parentIsFullyVisible || visibilityByViewPage == 0)) {
                fragment.onBecomeFullyHidden();
                isFullyVisible = false;
            }
            if (isResumed && (newVisibility == 0 && !parentIsResumed || visibilityByViewPage == 0)) {
                fragment.onPause();
                isResumed = false;
            }
        }

        private FragmentState(@NonNull BaseFragment fragment) {
            this.fragment = fragment;
        }
    }
}
