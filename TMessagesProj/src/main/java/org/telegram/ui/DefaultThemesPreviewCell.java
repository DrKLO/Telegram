package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class DefaultThemesPreviewCell extends LinearLayout {

    private final RecyclerListView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final FlickerLoadingView progressView;
    private final ChatThemeBottomSheet.Adapter adapter;
    RLottieDrawable darkThemeDrawable;
    TextCell dayNightCell;
    TextCell browseThemesCell;

    private int selectedPosition = -1;
    BaseFragment parentFragment;
    int currentType;
    int themeIndex;

    public DefaultThemesPreviewCell(Context context, BaseFragment parentFragment, int type) {
        super(context);
        this.currentType = type;
        this.parentFragment = parentFragment;
        setOrientation(LinearLayout.VERTICAL);
        FrameLayout frameLayout = new FrameLayout(context);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        adapter = new ChatThemeBottomSheet.Adapter(parentFragment.getCurrentAccount(), null, currentType == ThemeActivity.THEME_TYPE_BASIC ? ChatThemeBottomSheet.Adapter.TYPE_DEFAULT : ChatThemeBottomSheet.Adapter.TYPE_GRID);
        recyclerView = new RecyclerListView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        } else {
            recyclerView.setHasFixedSize(false);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return 1;
                }
            });
            recyclerView.setLayoutManager(layoutManager = gridLayoutManager);
        }

        recyclerView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        recyclerView.setOnItemClickListener((view, position) -> {
            ChatThemeBottomSheet.ChatThemeItem chatTheme = adapter.items.get(position);
            Theme.ThemeInfo info = chatTheme.chatTheme.getThemeInfo(themeIndex);
            int accentId = -1;
            if (chatTheme.chatTheme.getEmoticon().equals("\uD83C\uDFE0")) {
                accentId = chatTheme.chatTheme.getAccentId(themeIndex);
            }
            if (info == null) {
                TLRPC.TL_theme theme = chatTheme.chatTheme.getTlTheme(themeIndex);
                int settingsIndex = chatTheme.chatTheme.getSettingsIndex(themeIndex);
                TLRPC.ThemeSettings settings = theme.settings.get(settingsIndex);
                String key = Theme.getBaseThemeKey(settings);
                info = Theme.getTheme(key);

                if (info != null) {
                    Theme.ThemeAccent accent = info.accentsByThemeId.get(theme.id);
                    if (accent == null) {
                        accent = info.createNewAccent(theme, parentFragment.getCurrentAccount());
                    }
                    accentId = accent.id;
                    info.setCurrentAccentId(accentId);
                }
            }

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, info, false, null, accentId);

            selectedPosition = position;
            adapter.setSelectedItem(selectedPosition);

            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                ChatThemeBottomSheet.Adapter.ChatThemeView child = (ChatThemeBottomSheet.Adapter.ChatThemeView) recyclerView.getChildAt(i);
                if (child != view) {
                    child.cancelAnimation();
                }
            }
            ((ChatThemeBottomSheet.Adapter.ChatThemeView) view).playEmojiAnimation();
        });

        progressView = new FlickerLoadingView(getContext(), null);
        progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
        progressView.setVisibility(View.VISIBLE);

        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 8, 0, 8));
            frameLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 8, 0, 8));
        } else {
            frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 8, 0, 8));
            frameLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 0, 8, 0, 8));
        }


        recyclerView.setEmptyView(progressView);
        recyclerView.setAnimateEmptyView(true, 0);

        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
            darkThemeDrawable.beginApplyLayerColors();
            darkThemeDrawable.commitApplyLayerColors();

            dayNightCell = new TextCell(context);
            dayNightCell.setTextAndIcon(LocaleController.getString("SettingsSwitchToNightMode", R.string.SettingsSwitchToNightMode), darkThemeDrawable, true);
            dayNightCell.imageLeft = 21;
            addView(dayNightCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            browseThemesCell = new TextCell(context);
            browseThemesCell.setTextAndIcon(LocaleController.getString("SettingsBrowseThemes", R.string.SettingsBrowseThemes), R.drawable.msg_colors, false);

            addView(browseThemesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            dayNightCell.setOnClickListener(new OnClickListener() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onClick(View view) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
                    String dayThemeName = preferences.getString("lastDayTheme", "Blue");
                    if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                        dayThemeName = "Blue";
                    }
                    String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
                    if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                        nightThemeName = "Dark Blue";
                    }
                    Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
                    if (dayThemeName.equals(nightThemeName)) {
                        if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                            dayThemeName = "Blue";
                        } else {
                            nightThemeName = "Dark Blue";
                        }
                    }

                    boolean toDark;
                    if (toDark = !Theme.isCurrentThemeDark()) {
                        themeInfo = Theme.getTheme(nightThemeName);
                    } else {
                        themeInfo = Theme.getTheme(dayThemeName);
                    }

                    darkThemeDrawable.setCustomEndFrame(toDark ? darkThemeDrawable.getFramesCount() - 1 : 0);

                    dayNightCell.getImageView().playAnimation();
                    int[] pos = new int[2];
                    dayNightCell.getImageView().getLocationInWindow(pos);
                    pos[0] += dayNightCell.getImageView().getMeasuredWidth() / 2;
                    pos[1] += dayNightCell.getImageView().getMeasuredHeight() / 2 + AndroidUtilities.dp(3);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, dayNightCell.getImageView());

                    updateDayNightMode();
                    updateSelectedPosition();
                }
            });

            darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
            browseThemesCell.setOnClickListener(view -> {
                parentFragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_THEMES_BROWSER));
            });

            if (!Theme.isCurrentThemeDay()) {
                darkThemeDrawable.setCurrentFrame(darkThemeDrawable.getFramesCount() - 1);
            }
        }

        if (!Theme.defaultEmojiThemes.isEmpty()) {
            ArrayList<ChatThemeBottomSheet.ChatThemeItem> themes = new ArrayList<>();
            themes.addAll(Theme.defaultEmojiThemes);
            if (currentType == ThemeActivity.THEME_TYPE_BASIC) {

                EmojiThemes chatTheme = EmojiThemes.createPreviewCustom();
                chatTheme.loadPreviewColors(parentFragment.getCurrentAccount());
                ChatThemeBottomSheet.ChatThemeItem item = new ChatThemeBottomSheet.ChatThemeItem(chatTheme);
                item.themeIndex = !Theme.isCurrentThemeDay() ? 2 : 0;
                themes.add(item);
            }

            adapter.setItems(themes);
        }
        updateDayNightMode();
        updateSelectedPosition();
        updateColors();
        if (selectedPosition >= 0) {
            layoutManager.scrollToPositionWithOffset(selectedPosition, AndroidUtilities.dp(16));
        }
    }

    public void updateDayNightMode() {
        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            themeIndex = !Theme.isCurrentThemeDay() ? 2 : 0;
        } else {
            if (Theme.getActiveTheme().getKey().equals("Blue")) {
                themeIndex = 0;
            } else if (Theme.getActiveTheme().getKey().equals("Day")) {
                themeIndex = 1;
            } else if (Theme.getActiveTheme().getKey().equals("Night")) {
                themeIndex = 2;
            } else if (Theme.getActiveTheme().getKey().equals("Dark Blue")) {
                themeIndex = 3;
            } else {
                if (Theme.isCurrentThemeDay() && (themeIndex == 2 || themeIndex == 3)) {
                    themeIndex = 0;
                }
                if (!Theme.isCurrentThemeDay() && (themeIndex == 0 || themeIndex == 1)) {
                    themeIndex = 2;
                }
            }

        }
        if (adapter.items != null) {
            for (int i = 0; i < adapter.items.size(); i++) {
                adapter.items.get(i).themeIndex = themeIndex;
            }
            adapter.notifyItemRangeChanged(0, adapter.items.size());
        }
        updateSelectedPosition();
    }

    private void updateSelectedPosition() {
        if (adapter.items == null) {
            return;
        }
        selectedPosition = -1;
        for (int i = 0; i < adapter.items.size(); i++) {
            TLRPC.TL_theme theme = adapter.items.get(i).chatTheme.getTlTheme(themeIndex);
            if (theme != null) {
                int settingsIndex = adapter.items.get(i).chatTheme.getSettingsIndex(themeIndex);
                String key = Theme.getBaseThemeKey(theme.settings.get(settingsIndex));
                if (Theme.getCurrentTheme().name.equals(key)) {
                    Theme.ThemeAccent accent = Theme.getCurrentTheme().accentsByThemeId.get(theme.id);
                    if (accent != null && accent.id == Theme.getCurrentTheme().currentAccentId) {
                        selectedPosition = i;
                    }
                }
            }
        }
        if (selectedPosition == -1) {
            selectedPosition = adapter.items.size() - 1;
        }
        adapter.setSelectedItem(selectedPosition);
    }

    public void selectTheme(Theme.ThemeInfo themeInfo) {
        if (themeInfo.info != null) {
            if (!themeInfo.themeLoaded) {
                return;
            }
        }
        if (!TextUtils.isEmpty(themeInfo.assetName)) {
            Theme.PatternsLoader.createLoader(false);
        }
        if (currentType != ThemeActivity.THEME_TYPE_OTHER) {
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit();
            editor.putString(currentType == ThemeActivity.THEME_TYPE_NIGHT || themeInfo.isDark() ? "lastDarkTheme" : "lastDayTheme", themeInfo.getKey());
            editor.commit();
        }
        if (currentType == ThemeActivity.THEME_TYPE_NIGHT) {
            if (themeInfo == Theme.getCurrentNightTheme()) {
                return;
            }
            Theme.setCurrentNightTheme(themeInfo);
        } else {
            if (themeInfo == Theme.getCurrentTheme()) {
                return;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);
        }
        //updateRows();

        int count = getChildCount();
//        for (int a = 0; a < count; a++) {
//            View child = getChildAt(a);
//            if (child instanceof ThemesHorizontalListCell.InnerThemeView) {
//                ((ThemesHorizontalListCell.InnerThemeView) child).updateCurrentThemeCheck();
//            }
//        }
    }

    public void updateColors() {
        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            darkThemeDrawable.setLayerColor("Sunny.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            darkThemeDrawable.setLayerColor("Path.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            darkThemeDrawable.setLayerColor("Path 10.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            darkThemeDrawable.setLayerColor("Path 11.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));

            dayNightCell.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            browseThemesCell.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            dayNightCell.setColors(null, Theme.key_windowBackgroundWhiteBlueText4);
            browseThemesCell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        updateColors();
    }
}
