package org.telegram.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class SpaceGramSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SettingsSpaceGram", R.string.SettingsSpaceGram));
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
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGram", R.string.SettingsSpaceGram)));
        items.add(UItem.asSettingsCell(1, R.drawable.filled_profile_settings, LocaleController.getString("SettingsSpaceGramGeneral", R.string.SettingsSpaceGramGeneral)));
        items.add(UItem.asSettingsCell(2, R.drawable.settings_chat, LocaleController.getString("SettingsSpaceGramAppearance", R.string.SettingsSpaceGramAppearance)));
        items.add(UItem.asSettingsCell(3, R.drawable.settings_chat, LocaleController.getString("SettingsSpaceGramChat", R.string.SettingsSpaceGramChat)));
        items.add(UItem.asSettingsCell(4, R.drawable.settings_privacy, LocaleController.getString("SettingsSpaceGramPasscode", R.string.SettingsSpaceGramPasscode)));
        items.add(UItem.asSettingsCell(5, R.drawable.settings_folders, LocaleController.getString("SettingsSpaceGramOther", R.string.SettingsSpaceGramOther)));
        items.add(UItem.asSettingsCell(6, R.drawable.settings_power, LocaleController.getString("SettingsSpaceGramExperimental", R.string.SettingsSpaceGramExperimental)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGramAbout", R.string.SettingsSpaceGramAbout)));
        items.add(UItem.asSettingsCell(10, R.drawable.outline_channel_24, LocaleController.getString("SettingsSpaceGramOfficialChannel", R.string.SettingsSpaceGramOfficialChannel)));
        items.add(UItem.asSettingsCell(11, R.drawable.settings_language, LocaleController.getString("SettingsSpaceGramOfficialSite", R.string.SettingsSpaceGramOfficialSite)));
        items.add(UItem.asSettingsCell(12, R.drawable.filled_profile_message_24, LocaleController.getString("SettingsSpaceGramSourceCode", R.string.SettingsSpaceGramSourceCode)));
        items.add(UItem.asSettingsCell(13, R.drawable.settings_language, LocaleController.getString("SettingsSpaceGramTranslateSpaceGram", R.string.SettingsSpaceGramTranslateSpaceGram)));
        items.add(UItem.asSettingsCell(14, R.drawable.filled_paid_suggest_24, LocaleController.getString("SettingsSpaceGramDonate", R.string.SettingsSpaceGramDonate)));
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case 1:
                presentFragment(new SpaceGramGeneralSettingsActivity());
                break;
            case 6:
                presentFragment(new SpaceGramExperimentalSettingsActivity());
                break;
            case 10:
                Browser.openUrl(getParentActivity(), "https://t.me/Rocket_Redireccion");
                break;
            case 11:
                // Official Site - placeholder
                break;
            case 12:
                // Source Code - placeholder
                break;
            case 13:
                // Translate SpaceGram - placeholder
                break;
            case 14:
                // Donate - placeholder
                break;
        }
    }
}
