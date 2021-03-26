package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
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
import org.telegram.ui.Components.AnimationsController;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ViewPagerFixed;

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

        SettingsAdapter viewPagerAdapter = new SettingsAdapter(context);
        viewPager = new ViewPagerFixed(context);
        viewPager.setAdapter(viewPagerAdapter);
        rootLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 48, 0, 0));

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

        public SettingsAdapter(Context context) {
            this.context = context;
        }

        @Override
        public View createView(int viewType) {
            return new View(context);
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            if (position == 0) {
                view.setBackgroundColor(Color.WHITE);
            } else if (position == 1) {
                view.setBackgroundColor(Color.GREEN);
            } else if (position == 2) {
                view.setBackgroundColor(Color.BLUE);
            }
        }

        @Override
        public int getItemCount() {
            return AnimationsController.animationTypes.length + 1;
        }

        public String getItemTitle(int position) {
            if (position == 0) {
                return LocaleController.getString("", R.string.AnimationSettingsBackground);
            } else {
                return AnimationsController.animationTypes[position - 1].title;
            }
        }
    }
}
