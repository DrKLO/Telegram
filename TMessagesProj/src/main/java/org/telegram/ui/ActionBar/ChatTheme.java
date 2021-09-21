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
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ChatTheme {

    public final boolean isDefault;
    private final TLRPC.TL_chatTheme chatThemeObject;
    private HashMap<String, Integer> lightCurrentColors;
    private HashMap<String, Integer> darkCurrentColors;
    private String darkWallpaperLink;
    private String lightWallpaperLink;

    public ChatTheme(TLRPC.TL_chatTheme chatThemeObject, boolean isDefault) {
        this.chatThemeObject = chatThemeObject;
        this.isDefault = isDefault;
    }

    public void initColors() {
        getCurrentColors(0, false);
        getCurrentColors(0, true);
    }

    public String getEmoticon() {
        return chatThemeObject.emoticon;
    }

    public TLRPC.TL_theme getTlTheme(boolean isDark) {
        return isDark ? ((TLRPC.TL_theme) chatThemeObject.dark_theme) : ((TLRPC.TL_theme) chatThemeObject.theme);
    }

    public TLRPC.WallPaper getWallpaper(boolean isDark) {
        return getTlTheme(isDark).settings.wallpaper;
    }

    public String getWallpaperLink(boolean isDark) {
        return isDark ? darkWallpaperLink : lightWallpaperLink;
    }

    public HashMap<String, Integer> getCurrentColors(int currentAccount, boolean isDark) {
        HashMap<String, Integer> currentColors = isDark ? darkCurrentColors : lightCurrentColors;
        if (currentColors != null) {
            return currentColors;
        }

        TLRPC.TL_theme tlTheme = getTlTheme(isDark);
        Theme.ThemeInfo baseTheme = Theme.getTheme(Theme.getBaseThemeKey(tlTheme.settings));
        Theme.ThemeInfo themeInfo = new Theme.ThemeInfo(baseTheme);
        Theme.ThemeAccent accent = themeInfo.createNewAccent(tlTheme, currentAccount, true);
        themeInfo.setCurrentAccentId(accent.id);

        HashMap<String, Integer> currentColorsNoAccent = new HashMap<>();
        String[] wallpaperLink = new String[1];
        if (themeInfo.pathToFile != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink));
        } else if (themeInfo.assetName != null) {
            currentColorsNoAccent.putAll(Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink));
        }
        if (isDark) {
            darkWallpaperLink = wallpaperLink[0];
        } else {
            lightWallpaperLink = wallpaperLink[0];
        }
        currentColors = new HashMap<>(currentColorsNoAccent);
        accent.fillAccentColors(currentColorsNoAccent, currentColors);
        if (!isDark) {
            currentColors.put(Theme.key_chat_messageTextOut, Theme.MSG_OUT_COLOR_BLACK);
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
        if (isDark) {
            darkCurrentColors = currentColors;
        } else {
            lightCurrentColors = currentColors;
        }
        return currentColors;
    }

    public void loadWallpaper(boolean isDark, ResultCallback<Pair<Long, Bitmap>> callback) {
        final TLRPC.WallPaper wallPaper = getWallpaper(isDark);
        if (wallPaper == null && callback != null) {
            callback.onComplete(null);
            return;
        }

        long themeId = getTlTheme(isDark).id;
        Bitmap cachedBitmap = ChatThemeController.getWallpaperBitmap(themeId);
        if (cachedBitmap != null && callback != null) {
            callback.onComplete(new Pair<>(themeId, cachedBitmap));
            return;
        }

        ImageLocation imageLocation = ImageLocation.getForDocument(wallPaper.document);
        ImageReceiver imageReceiver = new ImageReceiver();
        String imageFilter;
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            imageFilter = (int) (w / AndroidUtilities.density) + "_" + (int) (h / AndroidUtilities.density) + "_f";
        } else {
            imageFilter = (int) (1080 / AndroidUtilities.density) + "_" + (int) (1920 / AndroidUtilities.density) + "_f";
        }
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
        });
        ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);
    }

    public void loadWallpaperThumb(boolean isDark, ResultCallback<Pair<Long, Bitmap>> callback) {
        final TLRPC.WallPaper wallpaper = getWallpaper(isDark);
        if (wallpaper == null) {
            if (callback != null) {
                callback.onComplete(null);
            }
            return;
        }

        long themeId = getTlTheme(isDark).id;
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

        final TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(wallpaper.document.thumbs, 120);
        ImageLocation imageLocation = ImageLocation.getForDocument(thumbSize, wallpaper.document);
        ImageReceiver imageReceiver = new ImageReceiver();
        imageReceiver.setImage(imageLocation, "120_80", null, null, null, 1);
        imageReceiver.setDelegate((receiver, set, thumb, memCache) -> {
            ImageReceiver.BitmapHolder holder = receiver.getBitmapSafe();
            if (!set || holder == null) {
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
        loadWallpaperThumb(false, null);
        loadWallpaperThumb(true, null);
        loadWallpaper(false, null);
        loadWallpaper(true, null);
    }

    private File getWallpaperThumbFile(long themeId) {
        return new File(ApplicationLoader.getFilesDirFixed(), "wallpaper_thumb_" + themeId + ".png");
    }

    public static Theme.ThemeInfo getDefaultThemeInfo(boolean isDark) {
        Theme.ThemeInfo themeInfo = isDark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
        if (isDark != themeInfo.isDark()) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String lastThemeName = isDark
                    ? preferences.getString("lastNightTheme", "Dark Blue")
                    : preferences.getString("lastDayTheme", "Blue");
            themeInfo = Theme.getTheme(lastThemeName);
            if (themeInfo == null) {
                themeInfo = Theme.getTheme(isDark ? "Dark Blue" : "Blue");
            }
        }
        return new Theme.ThemeInfo(themeInfo);
    }

    public static ChatTheme getDefault() {
        Theme.ThemeInfo darkThemeInfo = getDefaultThemeInfo(true);
        fillTlTheme(darkThemeInfo);
        Theme.ThemeInfo lightThemeInfo = getDefaultThemeInfo(false);
        fillTlTheme(lightThemeInfo);

        TLRPC.TL_chatTheme tlChatTheme = new TLRPC.TL_chatTheme();
        tlChatTheme.emoticon = "❌";
        tlChatTheme.dark_theme = darkThemeInfo.info;
        tlChatTheme.theme = lightThemeInfo.info;

        ChatTheme chatTheme = new ChatTheme(tlChatTheme, true);
        chatTheme.darkCurrentColors = getCurrentColors(darkThemeInfo);
        chatTheme.lightCurrentColors = getCurrentColors(lightThemeInfo);
        return chatTheme;
    }

    private static void fillTlTheme(Theme.ThemeInfo themeInfo) {
        if (themeInfo.info == null) {
            themeInfo.info = new TLRPC.TL_theme();
        }
        if (themeInfo.info.settings == null) {
            themeInfo.info.settings = new TLRPC.TL_themeSettings();
        }
        ArrayList<Integer> messageColors = new ArrayList<>();
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            if (accent.myMessagesAccentColor != 0) {
                messageColors.add(accent.myMessagesAccentColor);
            }
            if (accent.myMessagesGradientAccentColor1 != 0) {
                messageColors.add(accent.myMessagesGradientAccentColor1);
            }
            if (accent.myMessagesGradientAccentColor2 != 0) {
                messageColors.add(accent.myMessagesGradientAccentColor2);
            }
            if (accent.myMessagesGradientAccentColor3 != 0) {
                messageColors.add(accent.myMessagesGradientAccentColor3);
            }
        }
        themeInfo.info.settings.message_colors = messageColors;
    }

    private static HashMap<String, Integer> getCurrentColors(Theme.ThemeInfo themeInfo) {
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

}
