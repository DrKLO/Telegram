package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Pair;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EmojiThemes {

    public boolean showAsDefaultStub;
    public String emoji;
    int currentIndex = 0;
    public ArrayList<ThemeItem> items = new ArrayList<>();

    private static final String[] previewColorKeys = new String[]{
            Theme.key_chat_inBubble,
            Theme.key_chat_outBubble,
            Theme.key_featuredStickers_addButton,
            Theme.key_chat_wallpaper,
            Theme.key_chat_wallpaper_gradient_to1,
            Theme.key_chat_wallpaper_gradient_to2,
            Theme.key_chat_wallpaper_gradient_to3,
            Theme.key_chat_wallpaper_gradient_rotation
    };

    private EmojiThemes() {
    }

    public EmojiThemes(TLRPC.TL_theme chatThemeObject, boolean isDefault) {
        this.showAsDefaultStub = isDefault;
        this.emoji = chatThemeObject.emoticon;
        if (!isDefault) {
            ThemeItem lightTheme = new ThemeItem();
            lightTheme.tlTheme = chatThemeObject;
            lightTheme.settingsIndex = 0;
            items.add(lightTheme);

            ThemeItem darkTheme = new ThemeItem();
            darkTheme.tlTheme = chatThemeObject;
            darkTheme.settingsIndex = 1;
            items.add(darkTheme);
        }
    }

    public static EmojiThemes createPreviewFullTheme(TLRPC.TL_theme tl_theme) {
        EmojiThemes chatTheme = new EmojiThemes();
        chatTheme.emoji = tl_theme.emoticon;

        for (int i = 0; i < tl_theme.settings.size(); i++) {
            ThemeItem theme = new ThemeItem();
            theme.tlTheme = tl_theme;
            theme.settingsIndex = i;
            chatTheme.items.add(theme);
        }
        return chatTheme;
    }


    public static EmojiThemes createChatThemesDefault() {

        EmojiThemes themeItem = new EmojiThemes();
        themeItem.emoji = "❌";
        themeItem.showAsDefaultStub = true;

        ThemeItem lightTheme = new ThemeItem();
        lightTheme.themeInfo = getDefaultThemeInfo(true);
        themeItem.items.add(lightTheme);

        ThemeItem darkTheme = new ThemeItem();
        darkTheme.themeInfo = getDefaultThemeInfo(false);
        themeItem.items.add(darkTheme);

        return themeItem;
    }

    public static EmojiThemes createPreviewCustom() {
        EmojiThemes themeItem = new EmojiThemes();
        themeItem.emoji = "\uD83C\uDFA8";

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String lastDayCustomTheme = preferences.getString("lastDayCustomTheme", null);
        int dayAccentId = preferences.getInt("lastDayCustomThemeAccentId", -1);
        if (lastDayCustomTheme == null || Theme.getTheme(lastDayCustomTheme) == null) {
            lastDayCustomTheme = preferences.getString("lastDayTheme", "Blue");
            Theme.ThemeInfo themeInfo = Theme.getTheme(lastDayCustomTheme);
            if (themeInfo == null) {
                lastDayCustomTheme = "Blue";
                dayAccentId = 99;
            } else {
                dayAccentId = themeInfo.currentAccentId;
            }
            preferences.edit().putString("lastDayCustomTheme", lastDayCustomTheme).apply();
        } else {
            if (dayAccentId == -1) {
                dayAccentId = Theme.getTheme(lastDayCustomTheme).lastAccentId;
            }
        }

        if (dayAccentId == -1) {
            lastDayCustomTheme = "Blue";
            dayAccentId = 99;
        }

        String lastDarkCustomTheme = preferences.getString("lastDarkCustomTheme", null);
        int darkAccentId = preferences.getInt("lastDarkCustomThemeAccentId", -1);
        if (lastDarkCustomTheme == null || Theme.getTheme(lastDarkCustomTheme) == null) {
            lastDarkCustomTheme = preferences.getString("lastDarkTheme", "Dark Blue");
            Theme.ThemeInfo themeInfo = Theme.getTheme(lastDarkCustomTheme);
            if (themeInfo == null) {
                lastDarkCustomTheme = "Dark Blue";
                darkAccentId = 0;
            } else {
                darkAccentId = themeInfo.currentAccentId;
            }
            preferences.edit().putString("lastDarkCustomTheme", lastDarkCustomTheme).apply();
        } else {
            if (darkAccentId == -1) {
                darkAccentId = Theme.getTheme(lastDayCustomTheme).lastAccentId;
            }
        }

        if (darkAccentId == -1) {
            lastDarkCustomTheme = "Dark Blue";
            darkAccentId = 0;
        }

        ThemeItem lightTheme = new ThemeItem();
        lightTheme.themeInfo = Theme.getTheme(lastDayCustomTheme);
        lightTheme.accentId = dayAccentId;
        themeItem.items.add(lightTheme);
        themeItem.items.add(null);

        ThemeItem darkTheme = new ThemeItem();
        darkTheme.themeInfo = Theme.getTheme(lastDarkCustomTheme);
        darkTheme.accentId = darkAccentId;
        themeItem.items.add(darkTheme);
        themeItem.items.add(null);

        return themeItem;
    }

    public static EmojiThemes createHomePreviewTheme() {
        EmojiThemes themeItem = new EmojiThemes();
        themeItem.emoji = "\uD83C\uDFE0";

        ThemeItem blue = new ThemeItem();
        blue.themeInfo = Theme.getTheme("Blue");
        blue.accentId = 99;
        themeItem.items.add(blue);

        ThemeItem day = new ThemeItem();
        day.themeInfo = Theme.getTheme("Day");
        day.accentId = 9;
        themeItem.items.add(day);

        ThemeItem night = new ThemeItem();
        night.themeInfo = Theme.getTheme("Night");
        night.accentId = 0;
        themeItem.items.add(night);

        ThemeItem nightBlue = new ThemeItem();
        nightBlue.themeInfo = Theme.getTheme("Dark Blue");
        nightBlue.accentId = 0;
        themeItem.items.add(nightBlue);
        return themeItem;
    }

    public static EmojiThemes createHomeQrTheme() {
        EmojiThemes themeItem = new EmojiThemes();
        themeItem.emoji = "\uD83C\uDFE0";

        ThemeItem blue = new ThemeItem();
        blue.themeInfo = Theme.getTheme("Blue");
        blue.accentId = 99;
        themeItem.items.add(blue);

        ThemeItem nightBlue = new ThemeItem();
        nightBlue.themeInfo = Theme.getTheme("Dark Blue");
        nightBlue.accentId = 0;
        themeItem.items.add(nightBlue);

        return themeItem;
    }

    public void initColors() {
        getPreviewColors(0, 0);
        getPreviewColors(0, 1);
    }

    public String getEmoticon() {
        return emoji;
    }

    public TLRPC.TL_theme getTlTheme(int index) {
        return items.get(index).tlTheme;
    }

    public TLRPC.WallPaper getWallpaper(int index) {
        int settingsIndex = items.get(index).settingsIndex;
        if (settingsIndex >= 0) {
            TLRPC.TL_theme tlTheme = getTlTheme(index);
            if (tlTheme != null) {
                return tlTheme.settings.get(settingsIndex).wallpaper;
            }
        }
        return null;
    }

    public String getWallpaperLink(int index) {
        return items.get(index).wallpaperLink;
    }

    public int getSettingsIndex(int index) {
        return items.get(index).settingsIndex;
    }

    public HashMap<String, Integer> getPreviewColors(int currentAccount, int index) {
        HashMap<String, Integer> currentColors = items.get(index).currentPreviewColors;
        if (currentColors != null) {
            return currentColors;
        }

        Theme.ThemeInfo themeInfo = getThemeInfo(index);
        Theme.ThemeAccent accent = null;
        if (themeInfo == null) {
            int settingsIndex = getSettingsIndex(index);
            TLRPC.TL_theme tlTheme = getTlTheme(index);
            Theme.ThemeInfo baseTheme;
            if (tlTheme != null) {
                baseTheme = Theme.getTheme(Theme.getBaseThemeKey(tlTheme.settings.get(settingsIndex)));
            } else {
                baseTheme = Theme.getTheme("Blue");
            }
            themeInfo = new Theme.ThemeInfo(baseTheme);
            accent = themeInfo.createNewAccent(tlTheme, currentAccount, true, settingsIndex);
            themeInfo.setCurrentAccentId(accent.id);
        } else {
            if (themeInfo.themeAccentsMap != null) {
                accent = themeInfo.themeAccentsMap.get(items.get(index).accentId);
            }
        }

        HashMap<String, Integer> currentColorsNoAccent = new HashMap<>();
        String[] wallpaperLink = new String[1];
        if (themeInfo.pathToFile != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink));
        } else if (themeInfo.assetName != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink));
        }

        items.get(index).wallpaperLink = wallpaperLink[0];

        if (accent != null) {
            currentColors = new HashMap<>(currentColorsNoAccent);
            accent.fillAccentColors(currentColorsNoAccent, currentColors);
            currentColorsNoAccent.clear();
        } else {
            currentColors = currentColorsNoAccent;
        }

        HashMap<String, String> fallbackKeys = Theme.getFallbackKeys();
        items.get(index).currentPreviewColors = new HashMap<>();
        for (int i = 0; i < previewColorKeys.length; i++) {
            String key = previewColorKeys[i];
            items.get(index).currentPreviewColors.put(key, currentColors.get(key));

            if (!items.get(index).currentPreviewColors.containsKey(key)) {
                Integer color = currentColors.get(fallbackKeys.get(key));
                currentColors.put(key, color);
            }
        }
        currentColors.clear();

        return items.get(index).currentPreviewColors;
    }

    public HashMap<String, Integer> createColors(int currentAccount, int index) {
        HashMap<String, Integer> currentColors;

        Theme.ThemeInfo themeInfo = getThemeInfo(index);
        Theme.ThemeAccent accent = null;
        if (themeInfo == null) {
            int settingsIndex = getSettingsIndex(index);
            TLRPC.TL_theme tlTheme = getTlTheme(index);
            Theme.ThemeInfo baseTheme = Theme.getTheme(Theme.getBaseThemeKey(tlTheme.settings.get(settingsIndex)));
            themeInfo = new Theme.ThemeInfo(baseTheme);
            accent = themeInfo.createNewAccent(tlTheme, currentAccount, true, settingsIndex);
            themeInfo.setCurrentAccentId(accent.id);
        } else {
            if (themeInfo.themeAccentsMap != null) {
                accent = themeInfo.themeAccentsMap.get(items.get(index).accentId);
            }
        }

        HashMap<String, Integer> currentColorsNoAccent = new HashMap<>();
        String[] wallpaperLink = new String[1];
        if (themeInfo.pathToFile != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink));
        } else if (themeInfo.assetName != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink));
        }

        items.get(index).wallpaperLink = wallpaperLink[0];

        if (accent != null) {
            currentColors = new HashMap<>(currentColorsNoAccent);
            accent.fillAccentColors(currentColorsNoAccent, currentColors);
            currentColorsNoAccent.clear();
        } else {
            currentColors = currentColorsNoAccent;
        }

        HashMap<String, String> fallbackKeys = Theme.getFallbackKeys();
        for (Map.Entry<String, String> fallbackEntry : fallbackKeys.entrySet()) {
            String colorKey = fallbackEntry.getKey();
            if (!currentColors.containsKey(colorKey)) {
                Integer color = currentColors.get(fallbackEntry.getValue());
                currentColors.put(colorKey, color);
            }
        }
        HashMap<String, Integer> defaultColors = Theme.getDefaultColors();
        for (Map.Entry<String, Integer> entry : defaultColors.entrySet()) {
            if (!currentColors.containsKey(entry.getKey())) {
                currentColors.put(entry.getKey(), entry.getValue());
            }
        }
        return currentColors;
    }

    public Theme.ThemeInfo getThemeInfo(int index) {
        return items.get(index).themeInfo;
    }

    public void loadWallpaper(int index, ResultCallback<Pair<Long, Bitmap>> callback) {
        final TLRPC.WallPaper wallPaper = getWallpaper(index);
        if (wallPaper == null) {
            if (callback != null) {
                callback.onComplete(null);
            }
            return;
        }

        long themeId = getTlTheme(index).id;
        ChatThemeController.getWallpaperBitmap(themeId, cachedBitmap -> {
            if (cachedBitmap != null && callback != null) {
                callback.onComplete(new Pair<>(themeId, cachedBitmap));
                return;
            }
            ImageLocation imageLocation = ImageLocation.getForDocument(wallPaper.document);
            ImageReceiver imageReceiver = new ImageReceiver();
            imageReceiver.setAllowLoadingOnAttachedOnly(false);

            String imageFilter;
            int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            imageFilter = (int) (w / AndroidUtilities.density) + "_" + (int) (h / AndroidUtilities.density) + "_f";

            imageReceiver.setImage(imageLocation, imageFilter, null, ".jpg", wallPaper, 1);
            imageReceiver.setDelegate((receiver, set, thumb, memCache) -> {
                ImageReceiver.BitmapHolder holder = receiver.getBitmapSafe();
                if (!set || holder == null) {
                    return;
                }
                Bitmap bitmap = holder.bitmap;
                if (bitmap == null && (holder.drawable instanceof BitmapDrawable)) {
                    bitmap = ((BitmapDrawable) holder.drawable).getBitmap();
                }
                if (callback != null) {
                    callback.onComplete(new Pair<>(themeId, bitmap));
                }
                ChatThemeController.saveWallpaperBitmap(bitmap, themeId);
            });
            ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);
        });
    }

    public void loadWallpaperThumb(int index, ResultCallback<Pair<Long, Bitmap>> callback) {
        final TLRPC.WallPaper wallpaper = getWallpaper(index);
        if (wallpaper == null) {
            if (callback != null) {
                callback.onComplete(null);
            }
            return;
        }

        long themeId = getTlTheme(index).id;
        Bitmap bitmap = ChatThemeController.getWallpaperThumbBitmap(themeId);
        File file = getWallpaperThumbFile(themeId);
        if (bitmap == null && file.exists() && file.length() > 0) {
            try {
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (bitmap != null) {
            if (callback != null) {
                callback.onComplete(new Pair<>(themeId, bitmap));
            }
            return;
        }

        if (wallpaper.document == null) {
            if (callback != null) {
                callback.onComplete(new Pair<>(themeId, null));
            }
            return;
        }
        final TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(wallpaper.document.thumbs, 140);
        ImageLocation imageLocation = ImageLocation.getForDocument(thumbSize, wallpaper.document);
        ImageReceiver imageReceiver = new ImageReceiver();
        imageReceiver.setAllowLoadingOnAttachedOnly(false);
        imageReceiver.setImage(imageLocation, "120_140", null, null, null, 1);
        imageReceiver.setDelegate((receiver, set, thumb, memCache) -> {
            ImageReceiver.BitmapHolder holder = receiver.getBitmapSafe();
            if (!set || holder == null || holder.bitmap.isRecycled()) {
                return;
            }
            Bitmap resultBitmap = holder.bitmap;
            if (resultBitmap == null && (holder.drawable instanceof BitmapDrawable)) {
                resultBitmap = ((BitmapDrawable) holder.drawable).getBitmap();
            }
            if (resultBitmap != null) {
                if (callback != null) {
                    callback.onComplete(new Pair<>(themeId, resultBitmap));
                }
                final Bitmap saveBitmap = resultBitmap;
                Utilities.globalQueue.postRunnable(() -> {
                    try (FileOutputStream outputStream = new FileOutputStream(file)) {
                        saveBitmap.compress(Bitmap.CompressFormat.PNG, 87, outputStream);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            } else {
                if (callback != null) {
                    callback.onComplete(null);
                }
            }
        });
        ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);
    }

    public void preloadWallpaper() {
        loadWallpaperThumb(0, null);
        loadWallpaperThumb(1, null);
        loadWallpaper(0, null);
        loadWallpaper(1, null);
    }

    private File getWallpaperThumbFile(long themeId) {
        return new File(ApplicationLoader.getFilesDirFixed(), "wallpaper_thumb_" + themeId + ".png");
    }

    public static Theme.ThemeInfo getDefaultThemeInfo(boolean isDark) {
        Theme.ThemeInfo themeInfo = isDark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
        if (isDark != themeInfo.isDark()) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String lastThemeName = isDark
                    ? preferences.getString("lastDarkTheme", "Dark Blue")
                    : preferences.getString("lastDayTheme", "Blue");
            themeInfo = Theme.getTheme(lastThemeName);
            if (themeInfo == null) {
                themeInfo = Theme.getTheme(isDark ? "Dark Blue" : "Blue");
            }
        }
        return new Theme.ThemeInfo(themeInfo);
    }

    public static void fillTlTheme(Theme.ThemeInfo themeInfo) {
        if (themeInfo.info == null) {
            themeInfo.info = new TLRPC.TL_theme();
        }
    }

    public static HashMap<String, Integer> getPreviewColors(Theme.ThemeInfo themeInfo) {
        HashMap<String, Integer> currentColorsNoAccent = new HashMap<>();
        if (themeInfo.pathToFile != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, null));
        } else if (themeInfo.assetName != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(null, themeInfo.assetName, null));
        }
        HashMap<String, Integer> currentColors = new HashMap<>(currentColorsNoAccent);
        Theme.ThemeAccent themeAccent = themeInfo.getAccent(false);
        if (themeAccent != null) {
            themeAccent.fillAccentColors(currentColorsNoAccent, currentColors);
        }
        return currentColors;
    }

    public int getAccentId(int themeIndex) {
        return items.get(themeIndex).accentId;
    }

    public void loadPreviewColors(int currentAccount) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) {
                continue;
            }
            HashMap<String, Integer> colorsMap = getPreviewColors(currentAccount, i);
            Integer color = colorsMap.get(Theme.key_chat_inBubble);
            if (color == null) {
                color = Theme.getDefaultColor(Theme.key_chat_inBubble);
            }
            items.get(i).inBubbleColor = color;
            color = colorsMap.get(Theme.key_chat_outBubble);
            if (color == null) {
                color = Theme.getDefaultColor(Theme.key_chat_outBubble);
            }
            items.get(i).outBubbleColor = color;
            color = colorsMap.get(Theme.key_featuredStickers_addButton);
            if (color == null) {
                color = Theme.getDefaultColor(Theme.key_featuredStickers_addButton);
            }
            items.get(i).outLineColor = color;
            color = colorsMap.get(Theme.key_chat_wallpaper);
            if (color == null) {
                items.get(i).patternBgColor = 0;
            } else {
                items.get(i).patternBgColor = color;
            }
            color = colorsMap.get(Theme.key_chat_wallpaper_gradient_to1);
            if (color == null) {
                items.get(i).patternBgGradientColor1 = 0;
            } else {
                items.get(i).patternBgGradientColor1 = color;
            }
            color = colorsMap.get(Theme.key_chat_wallpaper_gradient_to2);
            if (color == null) {
                items.get(i).patternBgGradientColor2 = 0;
            } else {
                items.get(i).patternBgGradientColor2 = color;
            }
            color = colorsMap.get(Theme.key_chat_wallpaper_gradient_to3);
            if (color == null) {
                items.get(i).patternBgGradientColor3 = 0;
            } else {
                items.get(i).patternBgGradientColor3 = color;
            }
            color = colorsMap.get(Theme.key_chat_wallpaper_gradient_rotation);
            if (color == null) {
                items.get(i).patternBgRotation = 0;
            } else {
                items.get(i).patternBgRotation = color;
            }
            if (items.get(i).themeInfo != null && items.get(i).themeInfo.getKey().equals("Blue")) {
                int accentId = items.get(i).accentId >= 0 ? items.get(i).accentId : items.get(i).themeInfo.currentAccentId;
                if (accentId == 99) {
                    items.get(i).patternBgColor = 0xffdbddbb;
                    items.get(i).patternBgGradientColor1 = 0xff6ba587;
                    items.get(i).patternBgGradientColor2 = 0xffd5d88d;
                    items.get(i).patternBgGradientColor3 = 0xff88b884;
                }
            }
        }
    }

    public ThemeItem getThemeItem(int index) {
        return items.get(index);
    }

    public static void saveCustomTheme(Theme.ThemeInfo themeInfo, int accentId) {
        if (themeInfo == null) {
            return;
        }
        if (accentId >= 0 && themeInfo.themeAccentsMap != null) {
            Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(accentId);
            if (accent == null || accent.isDefault) {
                return;
            }
        }
        if (themeInfo.getKey().equals("Blue") && accentId == 99) {
            return;
        }
        if (themeInfo.getKey().equals("Day") && accentId == 9) {
            return;
        }
        if (themeInfo.getKey().equals("Night") && accentId == 0) {
            return;
        }
        if (themeInfo.getKey().equals("Dark Blue") && accentId == 0) {
            return;
        }

        boolean dark = themeInfo.isDark();
        String key = dark ? "lastDarkCustomTheme" : "lastDayCustomTheme";
        String accentKey = dark ? "lastDarkCustomThemeAccentId" : "lastDayCustomThemeAccentId";
        ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit()
                .putString(key, themeInfo.getKey())
                .putInt(accentKey, accentId)
                .apply();
    }

    public static class ThemeItem {

        public Theme.ThemeInfo themeInfo;
        TLRPC.TL_theme tlTheme;
        int settingsIndex;
        public int accentId = -1;
        public HashMap<String, Integer> currentPreviewColors;
        private String wallpaperLink;

        public int inBubbleColor;
        public int outBubbleColor;
        public int outLineColor;
        public int patternBgColor;
        public int patternBgGradientColor1;
        public int patternBgGradientColor2;
        public int patternBgGradientColor3;
        public int patternBgRotation;
    }
}
