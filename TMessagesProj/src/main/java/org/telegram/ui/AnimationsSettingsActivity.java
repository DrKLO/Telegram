package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Animations.AnimationsController;
import org.telegram.ui.Animations.MsgAnimationSettings;
import org.telegram.ui.Animations.pages.AnimationsSettingsPage;
import org.telegram.ui.Animations.pages.BackgroundAnimationSettingsPage;
import org.telegram.ui.Animations.pages.MessageAnimationSettingsPage;
import org.telegram.ui.Cells.SelectColorCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ViewPagerFixed;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AnimationsSettingsActivity extends BaseFragment {

    private static final int menuItemShare = 1;
    private static final int menuItemImport = 2;
    private static final int menuItemRestore = 3;

    private static final int exportSettingsRequestCode = 500;
    private static final int importSettingsRequestCode = 501;

    private ViewPagerFixed viewPager;
    private SettingsAdapter adapter;

    @Override
    public View createView(Context context) {
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == menuItemShare) {
                    shareSettings();
                } else if (id == menuItemImport) {
                    importSettings();
                } else if (id == menuItemRestore) {
                    AnimationsController.getInstance().resetSettings();
                    reloadPages();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setCastShadows(false);
        actionBar.setTitle(LocaleController.getString("", R.string.AnimationSettings));
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(LocaleController.getString(LocaleController.getString("", R.string.Settings)));
        menuItem.addSubItem(menuItemShare, LocaleController.getString("", R.string.AnimationSettingsShare));
        menuItem.addSubItem(menuItemImport, LocaleController.getString("", R.string.AnimationSettingsImport));
        TextView menuItemText = menuItem.addSubItem(menuItemRestore, LocaleController.getString("", R.string.AnimationSettingsRestore));
        menuItemText.setTextColor(Color.RED);
        menuItem.updateItemsBackgrounds();

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
//        for (int i = 0; i < AnimationsController.msgAnimCount; ++i) {
//            MsgAnimationSettings settings = AnimationsController.getInstance().getMsgAnimSettings(i);
//            MessageAnimationSettingsPage page = new MessageAnimationSettingsPage(settings.id, settings.title);
//            pages.add(page);
//        }

        adapter = new SettingsAdapter(context, pages);
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

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == importSettingsRequestCode && resultCode == Activity.RESULT_OK) {
            performImportSettings(data.getData());
        }
    }

    private void shareSettings() {
        String settingsJsonString = null;
        try {
            JSONObject settingsObject = AnimationsController.getInstance().serializeAllSettings();
            settingsJsonString = settingsObject.toString();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (TextUtils.isEmpty(settingsJsonString)) {
            return;
        }

        File dir = AndroidUtilities.getSharingDirectory();
        dir.mkdirs();

        File file = new File(dir, UserConfig.getInstance(UserConfig.selectedAccount).clientUserId + ".tgas");
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(AndroidUtilities.getStringBytes(settingsJsonString));
        } catch (Exception e) {
            FileLog.e(e);
            return;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/json");
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", file));
                shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignore) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            }
        } else {
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        }

        Intent chooserIntent = Intent.createChooser(shareIntent, LocaleController.getString("ShareFile", R.string.ShareFile));
        getParentActivity().startActivityForResult(chooserIntent, exportSettingsRequestCode);
    }

    private void importSettings() {
        try {
            Intent importIntent = new Intent(Intent.ACTION_GET_CONTENT);
            if (Build.VERSION.SDK_INT >= 18) {
                importIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            }
            importIntent.setType("*/*");
            startActivityForResult(importIntent, importSettingsRequestCode);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void performImportSettings(Uri uri) {
        InputStream inputStream = null;
        try {
            inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            String settingJsonString = AndroidUtilities.readTextFromInputStream(inputStream, false);
            JSONObject jsonObject = new JSONObject(settingJsonString);
            AnimationsController.getInstance().loadAllSettings(jsonObject);
            reloadPages();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void reloadPages() {
        for (AnimationsSettingsPage page : adapter.pages) {
            page.refresh();
        }
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
