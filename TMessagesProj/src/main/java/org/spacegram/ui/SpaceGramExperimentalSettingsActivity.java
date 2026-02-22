package org.spacegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.spacegram.SpaceGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;


import java.util.ArrayList;

public class SpaceGramExperimentalSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SettingsSpaceGramExperimental", R.string.SettingsSpaceGramExperimental));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new UniversalRecyclerView(this, (items, adapter) -> {
            fillItems(items, adapter);
        }, (item, view, position, x, y) -> {
            onClick(item, view, position, x, y);
        }, null);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGramNetwork", R.string.SettingsSpaceGramNetwork)));
        items.add(UItem.asButtonCheck(1, LocaleController.getString("SettingsSpaceGramNetworkSpeed", R.string.SettingsSpaceGramNetworkSpeed), LocaleController.getString("SettingsSpaceGramNetworkSpeedInfo", R.string.SettingsSpaceGramNetworkSpeedInfo))
                .setChecked(SpaceGramConfig.networkSpeedMode == 1));
        items.add(UItem.asShadow(null));

    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            SpaceGramConfig.networkSpeedMode = SpaceGramConfig.networkSpeedMode == 1 ? 0 : 1;
            SpaceGramConfig.saveConfig();
            listView.adapter.update(true);
        }
    }
}
