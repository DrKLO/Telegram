package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.LongSparseArray;

import com.google.android.exoplayer2.util.Log;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ChatTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChatThemeController extends BaseController {

    private static final long reloadTimeoutMs = 2 * 60 * 60 * 1000;
    private static volatile DispatchQueue chatThemeQueue = new DispatchQueue("stageQueue");

    private static final HashMap<Long, Bitmap> themeIdWallpaperMap = new HashMap<>();
    private static final HashMap<Long, Bitmap> themeIdWallpaperThumbMap = new HashMap<>();
    private static List<ChatTheme> allChatThemes;
    private static volatile int themesHash;
    private static volatile long lastReloadTimeMs;

    public static void init() {
        SharedPreferences preferences = getSharedPreferences();
        themesHash = 0;
        lastReloadTimeMs = 0;
        if (!BuildVars.DEBUG_VERSION) {
            preferences.getInt("hash", 0);
            lastReloadTimeMs = preferences.getLong("lastReload", 0);
        }

        allChatThemes = getAllChatThemesFromPrefs();
        Emoji.preloadEmoji("❌");
        if (!allChatThemes.isEmpty()) {
            for (ChatTheme chatTheme : allChatThemes) {
                Emoji.preloadEmoji(chatTheme.getEmoticon());
            }
        } else {
            Emoji.preloadEmoji("\uD83E\uDD81");
            Emoji.preloadEmoji("⛄");
            Emoji.preloadEmoji("\uD83D\uDC8E");
            Emoji.preloadEmoji("\uD83D\uDC68\u200D\uD83C\uDFEB");
            Emoji.preloadEmoji("\uD83C\uDF37");
            Emoji.preloadEmoji("\uD83D\uDD2E");
            Emoji.preloadEmoji("\uD83C\uDF84");
            Emoji.preloadEmoji("\uD83C\uDFAE");
        }
    }

    public static void requestAllChatThemes(final ResultCallback<List<ChatTheme>> callback, boolean withDefault) {
        if (themesHash == 0 || lastReloadTimeMs == 0) {
            init();
        }

        boolean needReload = System.currentTimeMillis() - lastReloadTimeMs > reloadTimeoutMs;
        if (allChatThemes == null || allChatThemes.isEmpty() || needReload) {
            TLRPC.TL_account_getChatThemes request = new TLRPC.TL_account_getChatThemes();
            request.hash = themesHash;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> chatThemeQueue.postRunnable(() -> {
                boolean isError = false;
                final List<ChatTheme> chatThemes;
                if (response instanceof TLRPC.TL_account_chatThemes) {
                    TLRPC.TL_account_chatThemes resp = (TLRPC.TL_account_chatThemes) response;
                    themesHash = resp.hash;
                    lastReloadTimeMs = System.currentTimeMillis();

                    SharedPreferences.Editor editor = getSharedPreferences().edit();
                    editor.clear();
                    editor.putInt("hash", themesHash);
                    editor.putLong("lastReload", lastReloadTimeMs);
                    editor.putInt("count", resp.themes.size());
                    chatThemes = new ArrayList<>(resp.themes.size());
                    for (int i = 0; i < resp.themes.size(); ++i) {
                        TLRPC.TL_chatTheme tlChatTheme = resp.themes.get(i);
                        Emoji.preloadEmoji(tlChatTheme.emoticon);
                        SerializedData data = new SerializedData(tlChatTheme.getObjectSize());
                        tlChatTheme.serializeToStream(data);
                        editor.putString("theme_" + i, Utilities.bytesToHex(data.toByteArray()));
                        ChatTheme chatTheme = new ChatTheme(tlChatTheme, false);
                        chatTheme.preloadWallpaper();
                        chatThemes.add(chatTheme);
                    }
                    editor.apply();
                } else if (response instanceof TLRPC.TL_account_chatThemesNotModified) {
                    chatThemes = getAllChatThemesFromPrefs();
                } else {
                    chatThemes = null;
                    isError = true;
                    AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                }
                if (!isError) {
                    if (withDefault && !chatThemes.get(0).isDefault) {
                        chatThemes.add(0, ChatTheme.getDefault());
                    }
                    for (ChatTheme theme : chatThemes) {
                        theme.initColors();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        allChatThemes = new ArrayList<>(chatThemes);
                        callback.onComplete(chatThemes);
                    });
                }
            }));
        } else {
            List<ChatTheme> chatThemes = new ArrayList<>(allChatThemes);
            if (withDefault && !chatThemes.get(0).isDefault) {
                chatThemes.add(0, ChatTheme.getDefault());
            }
            for (ChatTheme theme : chatThemes) {
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

    private static List<ChatTheme> getAllChatThemesFromPrefs() {
        SharedPreferences preferences = getSharedPreferences();
        int count = preferences.getInt("count", 0);
        List<ChatTheme> themes = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            String value = preferences.getString("theme_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.TL_chatTheme chatTheme = TLRPC.TL_chatTheme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                if (chatTheme != null) {
                    themes.add(new ChatTheme(chatTheme, false));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return themes;
    }

    public static void requestChatTheme(final String emoticon, final ResultCallback<ChatTheme> callback) {
        if (TextUtils.isEmpty(emoticon)) {
            callback.onComplete(null);
            return;
        }
        requestAllChatThemes(new ResultCallback<List<ChatTheme>>() {
            @Override
            public void onComplete(List<ChatTheme> result) {
                for (ChatTheme theme : result) {
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

    public ChatTheme getDialogTheme(long dialogId) {
        String emoticon = dialogEmoticonsMap.get(dialogId);
        if (emoticon == null) {
            emoticon = getEmojiSharedPreferences().getString("chatTheme_" + currentAccount + "_" + dialogId, null);
            dialogEmoticonsMap.put(dialogId, emoticon);
        }
        if (emoticon != null) {
            for (ChatTheme theme : allChatThemes) {
                if (emoticon.equals(theme.getEmoticon())) {
                    return theme;
                }
            }
        }
        return null;
    }

    public static void preloadAllWallpaperImages(boolean isDark) {
        for (ChatTheme chatTheme : allChatThemes) {
            long themeId = chatTheme.getTlTheme(isDark).id;
            if (themeIdWallpaperMap.containsKey(themeId)) {
                continue;
            }
            chatTheme.loadWallpaper(isDark, result -> {
                if (result != null) {
                    themeIdWallpaperMap.put(result.first, result.second);
                }
            });
        }
    }

    public static void preloadAllWallpaperThumbs(boolean isDark) {
        for (ChatTheme chatTheme : allChatThemes) {
            long themeId = chatTheme.getTlTheme(isDark).id;
            if (themeIdWallpaperThumbMap.containsKey(themeId)) {
                continue;
            }
            chatTheme.loadWallpaperThumb(isDark, result -> {
                if (result != null) {
                    themeIdWallpaperThumbMap.put(result.first, result.second);
                }
            });
        }
    }

    public static void clearWallpaperImages() {
        themeIdWallpaperMap.clear();
    }

    public static void clearWallpaperThumbImages() {
        themeIdWallpaperThumbMap.clear();
    }

    public static Bitmap getWallpaperBitmap(long themeId) {
        return themeIdWallpaperMap.get(themeId);
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
