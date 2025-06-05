package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelColorActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class StoryThemeSheet extends FrameLayout {

    private final ImageView backButtonView;
    private final BackDrawable backButtonDrawable;

    private final TextView titleView;

    private final ChannelColorActivity.ThemeChooser themeView;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable whenDie;

    public StoryThemeSheet(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Runnable whenDie) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.whenDie = whenDie;

        setBackground(Theme.createRoundRectDrawable(dp(14),0, Theme.getColor(Theme.key_dialogBackground, resourcesProvider)));

        backButtonView = new ImageView(getContext());
        int padding = dp(10);
        backButtonView.setPadding(padding, padding, padding, padding);
        backButtonView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButtonDrawable = new BackDrawable(true);
        backButtonView.setImageDrawable(backButtonDrawable);
        backButtonView.setOnClickListener(v -> {
            dismiss();
        });
        addView(backButtonView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT, 7, 8, 0, 0));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setText(LocaleController.getString(R.string.StorySetWallpaper));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 54, 16, 24, 0));

        themeView = new ChannelColorActivity.ThemeChooser(context, false, currentAccount, resourcesProvider) {
            @Override
            public boolean isDark() {
                return currentEntry != null ? currentEntry.isDark : super.isDark();
            }
        };
        themeView.setOnEmoticonSelected(emoticon -> {
            if (currentEntry != null && !TextUtils.equals(currentEntry.backgroundWallpaperEmoticon, emoticon)) {
                currentEntry.backgroundWallpaperEmoticon = emoticon;
                updateWallpaper();
            }
        });
        addView(themeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 56, 0, 0));

    }

    public void updateColors() {
        themeView.updateColors();
    }

    protected void updateWallpaper() {

    }

    private boolean openWhenMeasured;
    private StoryEntry currentEntry;

    public void open(StoryEntry entry) {
        if (currentEntry != entry) {
            currentEntry = entry;
            themeView.updateColors();
        }
        currentEntry = entry;
        if (getMeasuredHeight() == 0) {
            openWhenMeasured = true;
            return;
        }

        if (entry != null) {
            TLRPC.WallPaper wallpaper = null;
            if (entry.backgroundWallpaperPeerId != Long.MIN_VALUE) {
                if (entry.backgroundWallpaperPeerId < 0) {
                    TLRPC.ChatFull chatFull = MessagesController.getInstance(entry.currentAccount).getChatFull(-entry.backgroundWallpaperPeerId);
                    if (chatFull != null) {
                        wallpaper = chatFull.wallpaper;
                    }
                } else {
                    TLRPC.UserFull userFull = MessagesController.getInstance(entry.currentAccount).getUserFull(entry.backgroundWallpaperPeerId);
                    if (userFull != null) {
                        wallpaper = userFull.wallpaper;
                    }
                }
            }
            themeView.setGalleryWallpaper(wallpaper);
            if (entry.backgroundWallpaperEmoticon != null) {
                themeView.setSelectedEmoticon(entry.backgroundWallpaperEmoticon, false);
            } else if (!TextUtils.isEmpty(ChatThemeController.getWallpaperEmoticon(wallpaper))) {
                themeView.setSelectedEmoticon(ChatThemeController.getWallpaperEmoticon(wallpaper), false);
            } else {
                themeView.setSelectedEmoticon(null, false);
            }
        } else {
            themeView.setGalleryWallpaper(null);
            themeView.setSelectedEmoticon(null, false);
        }

        setTranslationY(getMeasuredHeight());
        animate().translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        isOpen = true;
    }

    public boolean isOpen;

    public void dismiss() {
        animate().translationY(getMeasuredHeight()).withEndAction(() -> {
            if (whenDie != null) {
                whenDie.run();
            } else {
                setVisibility(GONE);
            }
        }).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        isOpen = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(235) + AndroidUtilities.navigationBarHeight, MeasureSpec.EXACTLY));
        if (openWhenMeasured) {
            openWhenMeasured = false;
            open(currentEntry);
        }
    }
}
