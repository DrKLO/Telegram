package org.spacegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.spacegram.SpaceGramConfig;
import org.spacegram.translator.SpaceGramTranslator;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LanguageSelectActivity;


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

        listView = new UniversalRecyclerView(this, (items, adapter) -> {
            fillItems(items, adapter);
        }, (item, view, position, x, y) -> {
            onClick(item, view, position, x, y);
        }, null);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGramTranslator", R.string.SettingsSpaceGramTranslator)));
        
        String styleName = SpaceGramConfig.translateStyle == 0 ? LocaleController.getString("TranslateStyleOnMessage", R.string.TranslateStyleOnMessage) : LocaleController.getString("TranslateStylePopup", R.string.TranslateStylePopup);
        items.add(UItem.asSettingsCell(1, 0, LocaleController.getString("SettingsSpaceGramTranslatorStyle", R.string.SettingsSpaceGramTranslatorStyle), styleName));
        
        String providerName = SpaceGramTranslator.getProviderName(SpaceGramConfig.translateProvider);
        items.add(UItem.asSettingsCell(2, 0, LocaleController.getString("SettingsSpaceGramTranslatorProvider", R.string.SettingsSpaceGramTranslatorProvider), providerName));
        
        items.add(UItem.asSettingsCell(3, 0, LocaleController.getString("SettingsSpaceGramTranslatorTargetLang", R.string.SettingsSpaceGramTranslatorTargetLang), SpaceGramConfig.translateTargetLang.isEmpty() ? LocaleController.getCurrentLanguageName() : SpaceGramConfig.translateTargetLang));
        items.add(UItem.asSettingsCell(4, 0, LocaleController.getString("SettingsSpaceGramTranslatorSkipLang", R.string.SettingsSpaceGramTranslatorSkipLang), SpaceGramConfig.translateSkipLang.isEmpty() ? LocaleController.getCurrentLanguageName() : SpaceGramConfig.translateSkipLang));
        
        items.add(UItem.asCheck(5, LocaleController.getString("SettingsSpaceGramAutoTranslate", R.string.SettingsSpaceGramAutoTranslate)).setChecked(SpaceGramConfig.autoTranslate));

        
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            SpaceGramConfig.translateStyle = (SpaceGramConfig.translateStyle + 1) % 2;
            SpaceGramConfig.saveConfig();
            listView.adapter.update(true);
        } else if (item.id == 2) {
            showProviderSelector();
        } else if (item.id == 3) {
            // Language selection - placeholder or reuse LanguageSelectActivity
            presentFragment(new LanguageSelectActivity());
        } else if (item.id == 4) {
            // Language selection - placeholder
            presentFragment(new LanguageSelectActivity());
        } else if (item.id == 5) {
            SpaceGramConfig.autoTranslate = !SpaceGramConfig.autoTranslate;
            SpaceGramConfig.saveConfig();
            listView.adapter.update(true);
        }
    }

    private void showProviderSelector() {
        String[] providerNames = SpaceGramTranslator.getAllProviderNames();
        int[] providerIds = SpaceGramTranslator.getAllProviderIds();
        
        // Find current provider index
        int currentIndex = 0;
        for (int i = 0; i < providerIds.length; i++) {
            if (providerIds[i] == SpaceGramConfig.translateProvider) {
                currentIndex = i;
                break;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SettingsSpaceGramTranslatorProvider", R.string.SettingsSpaceGramTranslatorProvider));
        builder.setSingleChoiceItems(providerNames, currentIndex, (dialog, which) -> {
            SpaceGramConfig.translateProvider = providerIds[which];
            SpaceGramConfig.saveConfig();
            listView.adapter.update(true);
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }
}
