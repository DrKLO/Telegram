package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.UItem;

import java.util.ArrayList;

public class SpaceGramGeneralSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SettingsSpaceGramGeneral", R.string.SettingsSpaceGramGeneral));
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
                SpaceGramGeneralSettingsActivity.this.fillItems(items, adapter);
            }
        }, new UniversalAdapter.OnItemClick() {
            @Override
            public void onClick(UItem item, View view, int position, float x, float y) {
                SpaceGramGeneralSettingsActivity.this.onClick(item, view, position, x, y);
            }
        }, null);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGramTranslator", R.string.SettingsSpaceGramTranslator)));
        
        String styleName = SharedConfig.spaceGramTranslateStyle == 0 ? LocaleController.getString("TranslateStyleOnMessage", R.string.TranslateStyleOnMessage) : LocaleController.getString("TranslateStylePopup", R.string.TranslateStylePopup);
        items.add(UItem.asDetailSettings(1, LocaleController.getString("SettingsSpaceGramTranslatorStyle", R.string.SettingsSpaceGramTranslatorStyle), styleName));
        
        String providerName = SharedConfig.spaceGramTranslateProvider == 1 ? LocaleController.getString("TranslateProviderGoogle", R.string.TranslateProviderGoogle) : "Telegram";
        items.add(UItem.asDetailSettings(2, LocaleController.getString("SettingsSpaceGramTranslatorProvider", R.string.SettingsSpaceGramTranslatorProvider), providerName));
        
        items.add(UItem.asDetailSettings(3, LocaleController.getString("SettingsSpaceGramTranslatorTargetLang", R.string.SettingsSpaceGramTranslatorTargetLang), SharedConfig.spaceGramTranslateTargetLang.isEmpty() ? LocaleController.getCurrentLanguageName() : SharedConfig.spaceGramTranslateTargetLang));
        items.add(UItem.asDetailSettings(4, LocaleController.getString("SettingsSpaceGramTranslatorSkipLang", R.string.SettingsSpaceGramTranslatorSkipLang), SharedConfig.spaceGramTranslateSkipLang.isEmpty() ? LocaleController.getCurrentLanguageName() : SharedConfig.spaceGramTranslateSkipLang));
        
        items.add(UItem.asCheck(5, LocaleController.getString("SettingsSpaceGramAutoTranslate", R.string.SettingsSpaceGramAutoTranslate)).setChecked(SharedConfig.spaceGramAutoTranslate));
        
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            SharedConfig.spaceGramTranslateStyle = (SharedConfig.spaceGramTranslateStyle + 1) % 2;
            SharedConfig.saveConfig();
            listView.adapter.update(true);
        } else if (item.id == 2) {
            // Only Google for now as choice
            // SharedConfig.spaceGramTranslateProvider = 1;
        } else if (item.id == 3) {
            // Language selection - placeholder or reuse LanguageSelectActivity
            presentFragment(new LanguageSelectActivity());
        } else if (item.id == 4) {
            // Language selection - placeholder
            presentFragment(new LanguageSelectActivity());
        } else if (item.id == 5) {
            SharedConfig.spaceGramAutoTranslate = !SharedConfig.spaceGramAutoTranslate;
            SharedConfig.saveConfig();
            listView.adapter.update(true);
        }
    }
}
