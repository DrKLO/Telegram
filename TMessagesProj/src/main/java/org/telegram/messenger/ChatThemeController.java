package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatThemeController extends BaseController {

    private static final long reloadTimeoutMs = 2 * 60 * 60 * 1000;
    public static volatile DispatchQueue chatThemeQueue = new DispatchQueue("chatThemeQueue");

    //private static final HashMap<Long, Bitmap> themeIdWallpaperMap = new HashMap<>();
    private static final HashMap<Long, Bitmap> themeIdWallpaperThumbMap = new HashMap<>();
    private static List<EmojiThemes> allChatThemes;
    private static volatile long themesHash;
    private static volatile long lastReloadTimeMs;

    public static void init() {
        SharedPreferences preferences = getSharedPreferences();
        themesHash = 0;
        lastReloadTimeMs = 0;
        try {
            themesHash = preferences.getLong("hash", 0);
            lastReloadTimeMs = preferences.getLong("lastReload", 0);
        } catch (Exception e) {
            FileLog.e(e);
        }

        allChatThemes = getAllChatThemesFromPrefs();

        preloadSticker("❌");
        if (!allChatThemes.isEmpty()) {
            for (EmojiThemes chatTheme : allChatThemes) {
               preloadSticker(chatTheme.getEmoticon());
            }
        }
    }

    private static void preloadSticker(String emojicon) {
        ImageReceiver imageReceiver = new ImageReceiver();
        TLRPC.Document document = MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emojicon);
        imageReceiver.setImage(ImageLocation.getForDocument(document), "50_50", null, null, null, 0);
        Emoji.preloadEmoji(emojicon);
    }

    public static void requestAllChatThemes(final ResultCallback<List<EmojiThemes>> callback, boolean withDefault) {
        if (themesHash == 0 || lastReloadTimeMs == 0) {
            init();
        }

        boolean needReload = System.currentTimeMillis() - lastReloadTimeMs > reloadTimeoutMs;
        if (allChatThemes == null || allChatThemes.isEmpty() || needReload) {
            TLRPC.TL_account_getChatThemes request = new TLRPC.TL_account_getChatThemes();
            request.hash = themesHash;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> chatThemeQueue.postRunnable(() -> {
                boolean isError = false;
                final List<EmojiThemes> chatThemes;
                if (response instanceof TLRPC.TL_account_themes) {
                    TLRPC.TL_account_themes resp = (TLRPC.TL_account_themes) response;
                    themesHash = resp.hash;
                    lastReloadTimeMs = System.currentTimeMillis();

                    SharedPreferences.Editor editor = getSharedPreferences().edit();
                    editor.clear();
                    editor.putLong("hash", themesHash);
                    editor.putLong("lastReload", lastReloadTimeMs);
                    editor.putInt("count", resp.themes.size());
                    chatThemes = new ArrayList<>(resp.themes.size());
                    for (int i = 0; i < resp.themes.size(); ++i) {
                        TLRPC.TL_theme tlChatTheme = resp.themes.get(i);
                        Emoji.preloadEmoji(tlChatTheme.emoticon);
                        SerializedData data = new SerializedData(tlChatTheme.getObjectSize());
                        tlChatTheme.serializeToStream(data);
                        editor.putString("theme_" + i, Utilities.bytesToHex(data.toByteArray()));
                        EmojiThemes chatTheme = new EmojiThemes(tlChatTheme, false);
                        chatTheme.preloadWallpaper();
                        chatThemes.add(chatTheme);
                    }
                    editor.apply();
                } else if (response instanceof TLRPC.TL_account_themesNotModified) {
                    chatThemes = getAllChatThemesFromPrefs();
                } else {
                    chatThemes = null;
                    isError = true;
                    AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                }
                if (!isError) {
                    if (withDefault && !chatThemes.get(0).showAsDefaultStub) {
                        chatThemes.add(0, EmojiThemes.createChatThemesDefault());
                    }
                    for (EmojiThemes theme : chatThemes) {
                        theme.initColors();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        allChatThemes = new ArrayList<>(chatThemes);
                        callback.onComplete(chatThemes);
                    });
                }
            }));
        } else {
            List<EmojiThemes> chatThemes = new ArrayList<>(allChatThemes);
            if (withDefault && !chatThemes.get(0).showAsDefaultStub) {
                chatThemes.add(0, EmojiThemes.createChatThemesDefault());
            }
            for (EmojiThemes theme : chatThemes) {
                theme.initColors();
            }
            callback.onComplete(chatThemes);
        }
    }

    private static SharedPreferences getSharedPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig", Context.MODE_PRIVATE);
    }
    private static SharedPreferences getEmojiSharedPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_emoji", Context.MODE_PRIVATE);
    }

    private static List<EmojiThemes> getAllChatThemesFromPrefs() {
        SharedPreferences preferences = getSharedPreferences();
        int count = preferences.getInt("count", 0);
        List<EmojiThemes> themes = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            String value = preferences.getString("theme_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.TL_theme chatTheme = TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                if (chatTheme != null) {
                    themes.add(new EmojiThemes(chatTheme, false));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return themes;
    }

    public static void requestChatTheme(final String emoticon, final ResultCallback<EmojiThemes> callback) {
        if (TextUtils.isEmpty(emoticon)) {
            callback.onComplete(null);
            return;
        }
        requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                for (EmojiThemes theme : result) {
                    if (emoticon.equals(theme.getEmoticon())) {
                        theme.initColors();
                        callback.onComplete(theme);
                        break;
                    }
                }
            }
            @Override
            public void onError(TLRPC.TL_error error) {
                callback.onComplete(null);
            }
        }, false);
    }


    private static final ChatThemeController[] instances = new ChatThemeController[UserConfig.MAX_ACCOUNT_COUNT];

    public static ChatThemeController getInstance(int accountNum) {
        ChatThemeController local = instances[accountNum];
        if (local == null) {
            synchronized (ChatThemeController.class) {
                local = instances[accountNum];
                if (local == null) {
                    local = new ChatThemeController(accountNum);
                    instances[accountNum] = local;
                }
            }
        }
        return local;
    }


    private final LongSparseArray<String> dialogEmoticonsMap = new LongSparseArray<>();

    public ChatThemeController(int num) {
        super(num);
    }

    public void setDialogTheme(long dialogId, String emoticon, boolean sendRequest) {
        String oldEmoticon = dialogEmoticonsMap.get(dialogId);
        if (TextUtils.equals(oldEmoticon, emoticon)) {
            return;
        }

        if (emoticon == null) {
            dialogEmoticonsMap.delete(dialogId);
        } else {
            dialogEmoticonsMap.put(dialogId, emoticon);
        }

        getEmojiSharedPreferences().edit()
                .putString("chatTheme_" + currentAccount + "_" + dialogId, emoticon)
                .apply();

        if (sendRequest) {
            TLRPC.TL_messages_setChatTheme request = new TLRPC.TL_messages_setChatTheme();
            request.emoticon = emoticon != null ? emoticon : "";
            request.peer = getMessagesController().getInputPeer(dialogId);
            getConnectionsManager().sendRequest(request, null);
        }
    }

    public EmojiThemes getDialogTheme(long dialogId) {
        String emoticon = dialogEmoticonsMap.get(dialogId);
        if (emoticon == null) {
            emoticon = getEmojiSharedPreferences().getString("chatTheme_" + currentAccount + "_" + dialogId, null);
            dialogEmoticonsMap.put(dialogId, emoticon);
        }
        if (emoticon != null) {
            for (EmojiThemes theme : allChatThemes) {
                if (emoticon.equals(theme.getEmoticon())) {
                    return theme;
                }
            }
        }
        return null;
    }

    public static void preloadAllWallpaperImages(boolean isDark) {
        for (EmojiThemes chatTheme : allChatThemes) {
            TLRPC.TL_theme theme = chatTheme.getTlTheme(isDark ? 1 : 0);
            if (theme == null) {
                continue;
            }
            long themeId = theme.id;
            if (getPatternFile(themeId).exists()) {
                continue;
            }
            chatTheme.loadWallpaper(isDark ? 1 : 0, null);
        }
    }

    public static void preloadAllWallpaperThumbs(boolean isDark) {
        for (EmojiThemes chatTheme : allChatThemes) {
            TLRPC.TL_theme theme = chatTheme.getTlTheme(isDark ? 1 : 0);
            if (theme == null) {
                continue;
            }
            long themeId = theme.id;
            if (themeIdWallpaperThumbMap.containsKey(themeId)) {
                continue;
            }
            chatTheme.loadWallpaperThumb(isDark ? 1 : 0, result -> {
                if (result != null) {
                    themeIdWallpaperThumbMap.put(result.first, result.second);
                }
            });
        }
    }

    public static void clearWallpaperImages() {

    }

    public static void clearWallpaperThumbImages() {
        themeIdWallpaperThumbMap.clear();
    }

    public static void getWallpaperBitmap(long themeId, ResultCallback<Bitmap> callback) {
        if (themesHash == 0) {
            callback.onComplete(null);
            return;
        }
        File file = getPatternFile(themeId);
        chatThemeQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try {
                if (file.exists()) {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (callback != null) {
                Bitmap finalBitmap = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    callback.onComplete(finalBitmap);
                });
            }
        });
    }

    private static File getPatternFile(long themeId) {
        return new File(ApplicationLoader.getFilesDirFixed(), String.format(Locale.US, "%d_%d.jpg", themeId, themesHash));
    }

    public static void saveWallpaperBitmap(Bitmap bitmap, long themeId) {
        File file = getPatternFile(themeId);
        chatThemeQueue.postRunnable(() -> {
            try {
                FileOutputStream stream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 87, stream);
                stream.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static Bitmap getWallpaperThumbBitmap(long themeId) {
        return themeIdWallpaperThumbMap.get(themeId);
    }

    public void clearCache() {
        themesHash = 0;
        lastReloadTimeMs = 0;
        getSharedPreferences().edit().clear().apply();
    }
}
