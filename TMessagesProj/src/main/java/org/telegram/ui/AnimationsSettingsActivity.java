package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.pages.AnimationsSettingsPage;
import org.telegram.ui.Animations.pages.BackgroundAnimationSettingsPage;
import org.telegram.ui.Cells.SelectColorCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;
import java.util.List;

public class AnimationsSettingsActivity extends BaseFragment {

    private ViewPagerFixed viewPager;

    @Override
    public View createView(Context context) {
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("", R.string.AnimationSettings));
        actionBar.setCastShadows(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        BackgroundAnimationSettingsPage backgroundPage = new BackgroundAnimationSettingsPage();
        backgroundPage.setOnItemClickListener((view, position) -> {
            if (position == backgroundPage.fullScreenPosition) {
                BackgroundAnimationsPreviewActivity fragment = new BackgroundAnimationsPreviewActivity();
                presentFragment(fragment);
            } else if (view instanceof SelectColorCell) {
                ((SelectColorCell) view).onClick();
            }
        });

        List<AnimationsSettingsPage> pages = new ArrayList<>(1);
        pages.add(backgroundPage);

        SettingsAdapter adapter = new SettingsAdapter(context, pages);
        viewPager = new ViewPagerFixed(context);
        viewPager.setAdapter(adapter);
        rootLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 48, 0, 0));

        View shadowView = new View(context);
        shadowView.setBackgroundResource(R.drawable.header_shadow);
        rootLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP, 0, 48, 0, 0));

        ViewPagerFixed.TabsView tabsView = viewPager.createTabsView();
        tabsView.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        tabsView.setColorKeys(Theme.key_actionBarTabActiveText, Theme.key_actionBarTabActiveText, Theme.key_actionBarTabUnactiveText, Theme.key_actionBarWhiteSelector, Theme.key_actionBarDefault);
        rootLayout.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        fragmentView = rootLayout;
        return fragmentView;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return viewPager.getCurrentPosition() == 0;
    }

    private static class SettingsAdapter extends ViewPagerFixed.Adapter {

        private final Context context;
        private final List<AnimationsSettingsPage> pages;

        public SettingsAdapter(Context context, List<AnimationsSettingsPage> pages) {
            this.context = context;
            this.pages = pages;
        }

        @Override
        public View createView(int viewType) {
            for (int i = 0; i != pages.size(); ++i) {
                if (pages.get(i).type == viewType) {
                    return pages.get(i).createView(context);
                }
            }
            return new View(context);
        }

        @Override
        public void bindView(View view, int position, int viewType) { }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        @Override
        public int getItemViewType(int position) {
            return pages.get(position).type;
        }

        public String getItemTitle(int position) {
            return pages.get(position).title;
        }
    }
}
