package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.DividerItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.Item;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.SectionItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.HeaderItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.TextItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.PreviewItem;
import org.telegram.ui.Animations.AnimationsSettingsAdapter.SelectColorItem;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
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

        List<SettingsPage> pages = new ArrayList<>(1 + AnimationsController.animationTypes.length);

        int pos = 0;
        final int fullScreenPosition;

        SectionItem sectionItem = new SectionItem();
        DividerItem dividerItem = new DividerItem();

        List<Item> backgroundPageItems = new ArrayList<>();
        backgroundPageItems.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsBackgroundPreview)));
        backgroundPageItems.add(pos++, new PreviewItem(AnimationsController.currentColors));
        backgroundPageItems.add(fullScreenPosition = pos++, new TextItem(LocaleController.getString("", R.string.AnimationSettingsOpenFullScreen)));
        backgroundPageItems.add(pos++, sectionItem);
        backgroundPageItems.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsColors)));
        for (int i = 0; i != AnimationsController.pointsCount; ++i) {
            backgroundPageItems.add(pos++, new SelectColorItem(LocaleController.formatString("", R.string.AnimationSettingsColorN, i + 1), AnimationsController.currentColors[i]));
            if (i < AnimationsController.pointsCount - 1) {
                backgroundPageItems.add(pos++, dividerItem);
            }
        }
        backgroundPageItems.add(pos++, sectionItem);
        backgroundPageItems.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsSendMessage)));
        backgroundPageItems.add(pos++, sectionItem);
        backgroundPageItems.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsOpenChat)));
        backgroundPageItems.add(pos++, sectionItem);
        backgroundPageItems.add(pos++, new HeaderItem(LocaleController.getString("", R.string.AnimationSettingsJumpToMessage)));
        backgroundPageItems.add(pos++, sectionItem);

        SettingsPage backgroundPage = new SettingsPage(context, -1, LocaleController.getString("", R.string.AnimationSettingsBackground));
        backgroundPage.setItems(backgroundPageItems);
        backgroundPage.setOnItemClickListener((view, position) -> {
            if (position == fullScreenPosition) {
                AnimationsPreviewActivity fragment = new AnimationsPreviewActivity();
                presentFragment(fragment);
            }
        });
        pages.add(backgroundPage);

        SettingsAdapter viewPagerAdapter = new SettingsAdapter(context, pages);
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
        private final List<SettingsPage> pages;

        public SettingsAdapter(Context context, List<SettingsPage> pages) {
            this.context = context;
            this.pages = pages;
        }

        @Override
        public View createView(int viewType) {
            for (int i = 0; i != pages.size(); ++i) {
                if (pages.get(i).type == viewType) {
                    return pages.get(i).getView();
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

    private static class SettingsPage {

        public final int type;
        public final String title;

        private final AnimationsSettingsAdapter adapter = new AnimationsSettingsAdapter();
        private final RecyclerListView recyclerView;

        public SettingsPage(Context context, int type, String title) {
            this.type = type;
            this.title = title;

            recyclerView = new RecyclerListView(context);
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
        }

        public View getView() {
            return recyclerView;
        }

        public void setItems(List<AnimationsSettingsAdapter.Item> items) {
            adapter.setItems(items);
        }

        public void setOnItemClickListener(RecyclerListView.OnItemClickListener clickListener) {
            recyclerView.setOnItemClickListener(clickListener);
        }
    }
}
