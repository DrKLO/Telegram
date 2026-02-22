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

        listView = new UniversalRecyclerView(this, new UniversalAdapter.FillItems() {
            @Override
            public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
                SpaceGramSettingsActivity.this.fillItems(items, adapter);
            }
        }, new UniversalAdapter.OnItemClick() {
            @Override
            public void onClick(UItem item, View view, int position, float x, float y) {
                SpaceGramSettingsActivity.this.onClick(item, view, position, x, y);
            }
        }, null);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGram", R.string.SettingsSpaceGram)));
        items.add(UItem.asSettings(1, LocaleController.getString("SettingsSpaceGramGeneral", R.string.SettingsSpaceGramGeneral)).setIcon(R.drawable.settings_general));
        items.add(UItem.asSettings(2, LocaleController.getString("SettingsSpaceGramAppearance", R.string.SettingsSpaceGramAppearance)).setIcon(R.drawable.settings_appearance));
        items.add(UItem.asSettings(3, LocaleController.getString("SettingsSpaceGramChat", R.string.SettingsSpaceGramChat)).setIcon(R.drawable.settings_chat));
        items.add(UItem.asSettings(4, LocaleController.getString("SettingsSpaceGramPasscode", R.string.SettingsSpaceGramPasscode)).setIcon(R.drawable.settings_privacy));
        items.add(UItem.asSettings(5, LocaleController.getString("SettingsSpaceGramOther", R.string.SettingsSpaceGramOther)).setIcon(R.drawable.settings_folders));
        items.add(UItem.asSettings(6, LocaleController.getString("SettingsSpaceGramExperimental", R.string.SettingsSpaceGramExperimental)).setIcon(R.drawable.settings_power));
        items.add(UItem.asShadow(null));

        items.add(UItem.asSettings(10, LocaleController.getString("SettingsSpaceGramOfficialChannel", R.string.SettingsSpaceGramOfficialChannel)).setIcon(R.drawable.msg_channel));
        items.add(UItem.asSettings(11, LocaleController.getString("SettingsSpaceGramOfficialSite", R.string.SettingsSpaceGramOfficialSite)).setIcon(R.drawable.msg_earth));
        items.add(UItem.asSettings(12, LocaleController.getString("SettingsSpaceGramSourceCode", R.string.SettingsSpaceGramSourceCode)).setIcon(R.drawable.msg_message));
        items.add(UItem.asSettings(13, LocaleController.getString("SettingsSpaceGramTranslateSpaceGram", R.string.SettingsSpaceGramTranslateSpaceGram)).setIcon(R.drawable.msg_language));
        items.add(UItem.asSettings(14, LocaleController.getString("SettingsSpaceGramDonate", R.string.SettingsSpaceGramDonate)).setIcon(R.drawable.msg_card));
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
