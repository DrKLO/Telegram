package org.spacegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.FrameLayout;

import org.spacegram.SpaceGramConfig;
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

import java.util.ArrayList;

public class SpaceGramExperimentalSettingsActivity extends BaseFragment {

    private static final int ITEM_DOWNLOAD_SPEED = 1;
    private static final int ITEM_UPLOAD_SPEED = 2;

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

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString("SettingsSpaceGramNetwork", R.string.SettingsSpaceGramNetwork)));
        items.add(UItem.asSettingsCell(
            ITEM_DOWNLOAD_SPEED,
            0,
            LocaleController.getString("SettingsSpaceGramDownloadSpeed", R.string.SettingsSpaceGramDownloadSpeed),
            getModeLabel(SpaceGramConfig.networkDownloadSpeedMode)
        ));
        items.add(UItem.asSettingsCell(
            ITEM_UPLOAD_SPEED,
            0,
            LocaleController.getString("SettingsSpaceGramUploadSpeed", R.string.SettingsSpaceGramUploadSpeed),
            getModeLabel(SpaceGramConfig.networkUploadSpeedMode)
        ));
        items.add(UItem.asShadow(LocaleController.getString("SettingsSpaceGramNetworkSpeedInfo", R.string.SettingsSpaceGramNetworkSpeedInfo)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ITEM_DOWNLOAD_SPEED) {
            showModeSelector(true);
        } else if (item.id == ITEM_UPLOAD_SPEED) {
            showModeSelector(false);
        }
    }

    private void showModeSelector(boolean isDownload) {
        final int currentMode = isDownload ? SpaceGramConfig.networkDownloadSpeedMode : SpaceGramConfig.networkUploadSpeedMode;
        final String title = LocaleController.getString(
            isDownload ? "SettingsSpaceGramDownloadSpeed" : "SettingsSpaceGramUploadSpeed",
            isDownload ? R.string.SettingsSpaceGramDownloadSpeed : R.string.SettingsSpaceGramUploadSpeed
        );

        final CharSequence[] modeLabels = new CharSequence[] {
            LocaleController.getString("SettingsSpaceGramSpeedModeNormal", R.string.SettingsSpaceGramSpeedModeNormal),
            LocaleController.getString("SettingsSpaceGramSpeedModeFast", R.string.SettingsSpaceGramSpeedModeFast),
            LocaleController.getString("SettingsSpaceGramSpeedModeExtreme", R.string.SettingsSpaceGramSpeedModeExtreme)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setSingleChoiceItems(modeLabels, currentMode, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (isDownload) {
                    SpaceGramConfig.networkDownloadSpeedMode = which;
                } else {
                    SpaceGramConfig.networkUploadSpeedMode = which;
                }
                SpaceGramConfig.saveConfig();
                listView.adapter.update(true);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String getModeLabel(int mode) {
        switch (mode) {
            case 1:
                return LocaleController.getString("SettingsSpaceGramSpeedModeFast", R.string.SettingsSpaceGramSpeedModeFast);
            case 2:
                return LocaleController.getString("SettingsSpaceGramSpeedModeExtreme", R.string.SettingsSpaceGramSpeedModeExtreme);
            default:
                return LocaleController.getString("SettingsSpaceGramSpeedModeNormal", R.string.SettingsSpaceGramSpeedModeNormal);
        }
    }
}
