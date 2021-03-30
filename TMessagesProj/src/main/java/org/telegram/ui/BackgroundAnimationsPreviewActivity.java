package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.AnimationSettings;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.GradientBackgroundView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ViewPagerFixed;

public class BackgroundAnimationsPreviewActivity extends BaseFragment {

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
        actionBar.setTitle(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview));
        actionBar.setCastShadows(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        Adapter adapter = new Adapter(context, AnimationsController.getForCurrentUser().getBackgroundAnimationSettings());
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

    private static class Adapter extends ViewPagerFixed.Adapter {

        private final Context context;
        private final AnimationSettings[] settings;
        private final BackgroundPreviewPage[] pages;

        public Adapter(Context context, AnimationSettings[] settings) {
            this.context = context;
            this.settings = settings;
            this.pages = new BackgroundPreviewPage[settings.length];
            for (int i = 0; i < pages.length; ++i) {
                pages[i] = new BackgroundPreviewPage(settings[i].title);
            }
        }

        @Override
        public View createView(int viewType) {
            return pages[viewType].createView(context);
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            pages[position].bind(settings[position]);
        }

        @Override
        public int getItemCount() {
            return settings.length;
        }

        @Override
        public int getItemViewType(int position) {
            return settings[position].id;
        }

        @Override
        public String getItemTitle(int position) {
            return settings[position].title;
        }
    }


    private static class BackgroundPreviewPage implements View.OnClickListener {

        private static final int animateBtnHeight = 47;

        private final String name;

        private GradientBackgroundView backgroundView;

        public BackgroundPreviewPage(String name) {
            this.name = name;
        }

        public View createView(Context context) {
            FrameLayout rootLayout = new FrameLayout(context);

            backgroundView = new GradientBackgroundView(context, name);
            rootLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, animateBtnHeight));

            View shadowView = new View(context);
            shadowView.setBackgroundResource(R.drawable.header_shadow);
            shadowView.setRotation(180f);
            rootLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.BOTTOM, 0, 0, 0, animateBtnHeight));

            // TODO agolokoz: add ripple
            TextView animateBtn = new TextView(context);
            animateBtn.setAllCaps(true);
            animateBtn.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            animateBtn.setGravity(Gravity.CENTER);
            animateBtn.setOnClickListener(this);
            animateBtn.setText(LocaleController.getString("", R.string.AnimationSettingsAnimate));
            animateBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            animateBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            animateBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            rootLayout.addView(animateBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, animateBtnHeight, Gravity.BOTTOM));

            return rootLayout;
        }

        public void bind(AnimationSettings settings) {
            if (backgroundView != null) {
                backgroundView.setSettings(settings);
            }
        }

        @Override
        public void onClick(View v) {
            if (backgroundView != null) {
                backgroundView.animateBackground();
            }
        }
    }
}
