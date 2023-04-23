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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatThemeController extends BaseController {

    private static final long reloadTimeoutMs = 2 * 60 * 60 * 1000;
    public static volatile DispatchQueue chatThemeQueue = new DispatchQueue("chatThemeQueue");

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
        if (true || allChatThemes == null || allChatThemes.isEmpty() || needReload) {
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
                   // if (allChatThemes == null || allChatThemes.isEmpty()) {
                        chatThemes = getAllChatThemesFromPrefs();
//                    } else {
//                   //     return;
//                    }
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
        }
        if (allChatThemes != null && !allChatThemes.isEmpty()) {
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

    public static boolean equals(TLRPC.WallPaper wallPaper, TLRPC.WallPaper oldWallpaper) {
        if (wallPaper == null && oldWallpaper == null) {
            return true;
        }
        if (wallPaper != null && oldWallpaper != null) {
            if (wallPaper.uploadingImage != null) {
                return TextUtils.equals(oldWallpaper.uploadingImage, wallPaper.uploadingImage);
            }
            return wallPaper.id == oldWallpaper.id && TextUtils.equals(ChatBackgroundDrawable.hash(wallPaper.settings), ChatBackgroundDrawable.hash(oldWallpaper.settings));
        }
        return false;
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

        if (dialogId >= 0) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                userFull.theme_emoticon = emoticon;
                getMessagesStorage().updateUserInfo(userFull, true);
            }
        } else {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                chatFull.theme_emoticon = emoticon;
                getMessagesStorage().updateChatInfo(chatFull, true);
            }
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

    public void saveChatWallpaper(long dialogId, TLRPC.WallPaper wallPaper) {
        if (dialogId < 0) {
            return;
        }
        if (wallPaper != null) {
            SerializedData data = new SerializedData(wallPaper.getObjectSize());
            wallPaper.serializeToStream(data);
            String wallpaperString = Utilities.bytesToHex(data.toByteArray());

            getEmojiSharedPreferences().edit()
                    .putString("chatWallpaper_" + currentAccount + "_" + dialogId, wallpaperString)
                    .apply();
        } else {
            getEmojiSharedPreferences().edit()
                    .remove("chatWallpaper_" + currentAccount + "_" + dialogId)
                    .apply();
        }
    }

    public TLRPC.WallPaper getDialogWallpaper(long dialogId) {
        if (dialogId < 0) {
            return null;
        }
        TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
        if (userFull != null) {
            return userFull.wallpaper;
        }
        String wallpaperString = getEmojiSharedPreferences().getString("chatWallpaper_" + currentAccount + "_" + dialogId, null);
        if (wallpaperString != null) {
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(wallpaperString));
            try {
                return TLRPC.WallPaper.TLdeserialize(serializedData, serializedData.readInt32(true), true);
            } catch (Throwable e) {
                FileLog.e(e);
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

    public void clearWallpaper(long dialogId) {
        TLRPC.TL_messages_setChatWallPaper req = new TLRPC.TL_messages_setChatWallPaper();
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            req.peer = MessagesController.getInputPeer(user);
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                userFull.wallpaper = null;
                userFull.flags &= ~16777216;
                getMessagesStorage().updateUserInfo(userFull, false);
            }
            saveChatWallpaper(dialogId, null);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            req.peer = MessagesController.getInputPeer(chat);
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public int setWallpaperToUser(long dialogId, String wallpaperLocalPath, Theme.OverrideWallpaperInfo wallpaperInfo, MessageObject serverWallpaper, Runnable callback) {
        TLRPC.TL_messages_setChatWallPaper req = new TLRPC.TL_messages_setChatWallPaper();
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            req.peer = MessagesController.getInputPeer(user);
        } else {
            //chat not supported yet
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            req.peer = MessagesController.getInputPeer(chat);
        }
        boolean applyOnRequest = true;
        if (serverWallpaper != null && serverWallpaper.messageOwner.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
            applyOnRequest = false;
            req.flags |= 2;
            req.id = serverWallpaper.getId();

            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            if (userFull != null) {
                TLRPC.TL_messageActionSetChatWallPaper action = (TLRPC.TL_messageActionSetChatWallPaper) serverWallpaper.messageOwner.action;
                TLRPC.WallPaper wallPaper = new TLRPC.TL_wallPaper();
                wallPaper.id = action.wallpaper.id;
                wallPaper.document = action.wallpaper.document;
                wallPaper.settings = new TLRPC.TL_wallPaperSettings();
                wallPaper.settings.intensity = (int) (wallpaperInfo.intensity * 100);
                wallPaper.settings.motion = wallpaperInfo.isMotion;
                wallPaper.settings.blur = wallpaperInfo.isBlurred;
                wallPaper.settings.background_color = wallpaperInfo.color;
                wallPaper.settings.second_background_color = wallpaperInfo.gradientColor1;
                wallPaper.settings.third_background_color = wallpaperInfo.gradientColor2;
                wallPaper.settings.fourth_background_color = wallpaperInfo.gradientColor3;
                wallPaper.settings.rotation = wallpaperInfo.rotation;
                wallPaper.uploadingImage = wallpaperLocalPath;
                if (userFull.wallpaper != null && userFull.wallpaper.uploadingImage != null && userFull.wallpaper.uploadingImage.equals(wallPaper.uploadingImage)) {
                    wallPaper.stripedThumb = userFull.wallpaper.stripedThumb;
                }

                wallPaper.settings.flags |= 1;
                wallPaper.settings.flags |= 8;
                wallPaper.settings.flags |= 16;
                wallPaper.settings.flags |= 32;
                wallPaper.settings.flags |= 64;

                userFull.wallpaper = new TLRPC.TL_wallPaper();
                userFull.wallpaper.pattern = action.wallpaper.pattern;
                userFull.wallpaper.id = action.wallpaper.id;
                userFull.wallpaper.document = action.wallpaper.document;
                userFull.wallpaper.flags = action.wallpaper.flags;
                userFull.wallpaper.creator = action.wallpaper.creator;
                userFull.wallpaper.dark = action.wallpaper.dark;
                userFull.wallpaper.isDefault = action.wallpaper.isDefault;
                userFull.wallpaper.slug = action.wallpaper.slug;
                userFull.wallpaper.access_hash = action.wallpaper.access_hash;
                userFull.wallpaper.stripedThumb = action.wallpaper.stripedThumb;
                userFull.wallpaper.settings = wallPaper.settings;
                userFull.wallpaper.flags |= 4;
                userFull.flags |= 16777216;

                getMessagesStorage().updateUserInfo(userFull, false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
                if (callback != null) {
                    callback.run();
                }
            }
        } else {
            req.flags |= 1;
            req.wallpaper = MessagesController.getInputWallpaper(wallpaperInfo);
        }
        req.flags |= 4;
        req.settings = MessagesController.getWallpaperSetting(wallpaperInfo);


        boolean finalApplyOnRequest = applyOnRequest;
        return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.Updates) {
                TLRPC.Updates res = (TLRPC.Updates) response;
                TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                if (userFull != null) {
                    for (int i = 0; i < res.updates.size(); i++) {
                        if (res.updates.get(i) instanceof TLRPC.TL_updateNewMessage) {
                            TLRPC.Message message = ((TLRPC.TL_updateNewMessage) res.updates.get(i)).message;
                            if (message.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
                                if (finalApplyOnRequest) {
                                    TLRPC.TL_messageActionSetChatWallPaper actionSetChatWallPaper = (TLRPC.TL_messageActionSetChatWallPaper) message.action;
                                    actionSetChatWallPaper.wallpaper.uploadingImage = wallpaperLocalPath;
                                    if (userFull.wallpaper != null && userFull.wallpaper.uploadingImage != null && userFull.wallpaper.uploadingImage.equals(actionSetChatWallPaper.wallpaper.uploadingImage)) {
                                        actionSetChatWallPaper.wallpaper.stripedThumb = userFull.wallpaper.stripedThumb;
                                    }
                                    userFull.wallpaper = actionSetChatWallPaper.wallpaper;
                                    userFull.flags |= 16777216;

                                    saveChatWallpaper(dialogId, userFull.wallpaper);
                                    getMessagesStorage().updateUserInfo(userFull, false);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
                                }
                                break;
                            }
                        }
                    }
                }
                MessagesController.getInstance(currentAccount).processUpdateArray(res.updates, res.users, res.chats, false, res.date);
                if (callback != null) {
                    callback.run();
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.wallpaperSettedToUser);
            }
        }));
    }
}
