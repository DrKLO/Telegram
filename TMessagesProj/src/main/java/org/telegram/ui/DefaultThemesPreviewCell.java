package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ThemeSmallPreviewView;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class DefaultThemesPreviewCell extends LinearLayout {

    private final RecyclerListView recyclerView;
    private LinearLayoutManager layoutManager = null;
    private final FlickerLoadingView progressView;
    private final ChatThemeBottomSheet.Adapter adapter;
    RLottieDrawable darkThemeDrawable;
    TextCell dayNightCell;
    TextCell browseThemesCell;
    private ValueAnimator navBarAnimator;
    private int navBarColor;

    private int selectedPosition = -1;
    BaseFragment parentFragment;
    int currentType;
    int themeIndex;

    private Boolean wasPortrait = null;

    public DefaultThemesPreviewCell(Context context, BaseFragment parentFragment, int type) {
        super(context);
        this.currentType = type;
        this.parentFragment = parentFragment;
        setOrientation(LinearLayout.VERTICAL);
        FrameLayout frameLayout = new FrameLayout(context);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        adapter = new ChatThemeBottomSheet.Adapter(parentFragment.getCurrentAccount(), null, currentType == ThemeActivity.THEME_TYPE_BASIC ? ThemeSmallPreviewView.TYPE_DEFAULT : ThemeSmallPreviewView.TYPE_GRID);
        recyclerView = new RecyclerListView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setSelectorDrawableColor(0);
        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        updateLayoutManager();

        recyclerView.setFocusable(false);
        recyclerView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        recyclerView.setOnItemClickListener((view, position) -> {
            ChatThemeBottomSheet.ChatThemeItem chatTheme = adapter.items.get(position);
            Theme.ThemeInfo info = chatTheme.chatTheme.getThemeInfo(themeIndex);
            int accentId = -1;
            if (chatTheme.chatTheme.getEmoticon().equals("\uD83C\uDFE0") || chatTheme.chatTheme.getEmoticon().equals("\uD83C\uDFA8")) {
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
            for (int i = 0; i < adapter.items.size(); i++) {
                adapter.items.get(i).isSelected = i == selectedPosition;
            }
            adapter.setSelectedItem(selectedPosition);

            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                ThemeSmallPreviewView child = (ThemeSmallPreviewView) recyclerView.getChildAt(i);
                if (child != view) {
                    child.cancelAnimation();
                }
            }
            ((ThemeSmallPreviewView) view).playEmojiAnimation();

            if (info != null) {
                SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit();
                editor.putString(currentType == ThemeActivity.THEME_TYPE_NIGHT || info.isDark() ? "lastDarkTheme" : "lastDayTheme", info.getKey());
                editor.commit();
            }

            Theme.turnOffAutoNight(parentFragment);
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
        recyclerView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
            darkThemeDrawable.beginApplyLayerColors();
            darkThemeDrawable.commitApplyLayerColors();

            dayNightCell = new TextCell(context);
            dayNightCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            dayNightCell.imageLeft = 21;
            addView(dayNightCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            browseThemesCell = new TextCell(context);
            browseThemesCell.setTextAndIcon(LocaleController.getString("SettingsBrowseThemes", R.string.SettingsBrowseThemes), R.drawable.msg_colors, false);

            addView(browseThemesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            dayNightCell.setOnClickListener(new OnClickListener() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onClick(View view) {
                    if (DrawerProfileCell.switchingTheme) {
                        return;
                    }
                    int iconOldColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
                    int navBarOldColor = Theme.getColor(Theme.key_windowBackgroundGray);
                    DrawerProfileCell.switchingTheme = true;
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

                    Runnable then = () -> {
                        updateDayNightMode();
                        updateSelectedPosition();

                        int iconNewColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
                        darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(iconNewColor, PorterDuff.Mode.SRC_IN));
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                int iconColor = ColorUtils.blendARGB(iconOldColor, iconNewColor, (float) valueAnimator.getAnimatedValue());
                                darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                            }
                        });
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(iconNewColor, PorterDuff.Mode.SRC_IN));
                                super.onAnimationEnd(animation);
                            }
                        });
                        valueAnimator.setDuration(350);
                        valueAnimator.start();

                        int navBarNewColor = Theme.getColor(Theme.key_windowBackgroundGray);
                        final Window window = context instanceof Activity ? ((Activity) context).getWindow() : null;
                        if (window != null) {
                            if (navBarAnimator != null && navBarAnimator.isRunning()) {
                                navBarAnimator.cancel();
                            }
                            final int navBarFromColor = navBarAnimator != null && navBarAnimator.isRunning() ? navBarColor : navBarOldColor;
                            navBarAnimator = ValueAnimator.ofFloat(0, 1);
                            final float startDelay = toDark ? 50 : 200, duration = 150, fullDuration = 350;
                            navBarAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    float t = Math.max(0, Math.min(1, ((float) valueAnimator.getAnimatedValue() * fullDuration - startDelay) / duration));
                                    navBarColor = ColorUtils.blendARGB(navBarFromColor, navBarNewColor, t);
                                    AndroidUtilities.setNavigationBarColor(window, navBarColor, false);
                                    AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(navBarColor) >= 0.721f);
                                }
                            });
                            navBarAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    AndroidUtilities.setNavigationBarColor(window, navBarNewColor, false);
                                    AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(navBarNewColor) >= 0.721f);
                                }
                            });
                            navBarAnimator.setDuration((long) fullDuration);
                            navBarAnimator.start();
                        }

                        if (Theme.isCurrentThemeDay()) {
                            dayNightCell.setTextAndIcon(LocaleController.getString("SettingsSwitchToNightMode", R.string.SettingsSwitchToNightMode), darkThemeDrawable, true);
                        } else {
                            dayNightCell.setTextAndIcon(LocaleController.getString("SettingsSwitchToDayMode", R.string.SettingsSwitchToDayMode), darkThemeDrawable, true);
                        }

                        Theme.turnOffAutoNight(parentFragment);
                    };

                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, dayNightCell.getImageView(), dayNightCell, then);
                }
            });

            darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
            browseThemesCell.setOnClickListener(view -> {
                parentFragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_THEMES_BROWSER));
            });

            if (!Theme.isCurrentThemeDay()) {
                darkThemeDrawable.setCurrentFrame(darkThemeDrawable.getFramesCount() - 1);
                dayNightCell.setTextAndIcon(LocaleController.getString("SettingsSwitchToDayMode", R.string.SettingsSwitchToDayMode), darkThemeDrawable, true);
            } else {
                dayNightCell.setTextAndIcon(LocaleController.getString("SettingsSwitchToNightMode", R.string.SettingsSwitchToNightMode), darkThemeDrawable, true);
            }
        }

        if (!MediaDataController.getInstance(parentFragment.getCurrentAccount()).defaultEmojiThemes.isEmpty()) {
            ArrayList<ChatThemeBottomSheet.ChatThemeItem> themes = new ArrayList<>(MediaDataController.getInstance(parentFragment.getCurrentAccount()).defaultEmojiThemes);
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
        if (selectedPosition >= 0 && layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(selectedPosition, AndroidUtilities.dp(16));
        }
    }

    public void updateLayoutManager() {
        final boolean isPortrait = AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x;
        if (wasPortrait != null && wasPortrait == isPortrait) {
            return;
        }
        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            if (layoutManager == null) {
                recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            }
        } else {
            int spanCount = isPortrait ? 3 : 9;
            if (layoutManager instanceof GridLayoutManager) {
                ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
            } else {
                recyclerView.setHasFixedSize(false);
                GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
                gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return 1;
                    }
                });
                recyclerView.setLayoutManager(layoutManager = gridLayoutManager);
            }
        }
        wasPortrait = isPortrait;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateLayoutManager();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
            Theme.ThemeInfo themeInfo = adapter.items.get(i).chatTheme.getThemeInfo(themeIndex);
            if (theme != null) {
                int settingsIndex = adapter.items.get(i).chatTheme.getSettingsIndex(themeIndex);
                String key = Theme.getBaseThemeKey(theme.settings.get(settingsIndex));
                if (Theme.getActiveTheme().name.equals(key)) {
                    if (Theme.getActiveTheme().accentsByThemeId == null) {
                        selectedPosition = i;
                        break;
                    } else {
                        Theme.ThemeAccent accent = Theme.getActiveTheme().accentsByThemeId.get(theme.id);
                        if (accent != null && accent.id == Theme.getActiveTheme().currentAccentId) {
                            selectedPosition = i;
                            break;
                        }
                    }
                }
            } else if (themeInfo != null) {
                String key = themeInfo.getKey();
                if (Theme.getActiveTheme().name.equals(key) && adapter.items.get(i).chatTheme.getAccentId(themeIndex) == Theme.getActiveTheme().currentAccentId) {
                    selectedPosition = i;
                    break;
                }
            }
        }
        if (selectedPosition == -1 && currentType != ThemeActivity.THEME_TYPE_THEMES_BROWSER) {
            selectedPosition = adapter.items.size() - 1;
        }
        for (int i = 0; i < adapter.items.size(); i++) {
            adapter.items.get(i).isSelected = i == selectedPosition;
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
            if (themeInfo == Theme.getActiveTheme()) {
                return;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);
        }
    }

    public void updateColors() {
        if (currentType == ThemeActivity.THEME_TYPE_BASIC) {
            darkThemeDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), PorterDuff.Mode.SRC_IN));

            Theme.setSelectorDrawableColor(dayNightCell.getBackground(), Theme.getColor(Theme.key_listSelector), true);
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
