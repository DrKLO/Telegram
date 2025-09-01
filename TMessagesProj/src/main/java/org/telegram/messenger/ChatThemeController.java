package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Stars.StarsController;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatThemeController extends BaseController {

    private final long reloadTimeoutMs = 2 * 60 * 60 * 1000;
    public static volatile DispatchQueue chatThemeQueue = new DispatchQueue("chatThemeQueue");

    private final HashMap<Long, Bitmap> themeIdWallpaperThumbMap = new HashMap<>();
    private List<EmojiThemes> allChatThemes;
    private volatile long themesHash;
    private volatile long lastReloadTimeMs;
    private volatile int giftThemesOffset;
    private volatile long starGiftHash;
    private volatile boolean hasMoreGiftThemes;

    private ChatThemeController(int num) {
        super(num);
        init();
    }

    private void init() {
        SharedPreferences preferences = getSharedPreferences();
        themesHash = 0;
        lastReloadTimeMs = 0;
        giftThemesOffset = 0;
        hasMoreGiftThemes = true;
        try {
            themesHash = preferences.getLong("hash", 0);
            lastReloadTimeMs = preferences.getLong("lastReload", 0);
            starGiftHash = preferences.getLong("starGiftHash", 0);
            hasMoreGiftThemes = preferences.getBoolean("hasMoreGiftThemes", true);
        } catch (Exception e) {
            FileLog.e(e);
        }

        allChatThemes = getAllChatThemesFromPrefs();

        preloadSticker("❌");
        if (!allChatThemes.isEmpty()) {
            for (EmojiThemes chatTheme : allChatThemes) {
                ImageReceiver imageReceiver = new ImageReceiver();
                TLRPC.Document document = null;
                if (chatTheme instanceof EmojiThemes.Default) {
                    Emoji.preloadEmoji(((EmojiThemes.Default) chatTheme).emoji);
                    document = MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(((EmojiThemes.Default) chatTheme).emoji);
                } else if (chatTheme instanceof EmojiThemes.Gift) {
                    if (((EmojiThemes.Gift) chatTheme).starGift != null) {
                        document = ((EmojiThemes.Gift) chatTheme).starGift.getDocument();
                    }
                }
                imageReceiver.setImage(ImageLocation.getForDocument(document), "50_50", null, null, null, 0);
            }
        }
    }

    private void preloadSticker(String emojicon) {
        ImageReceiver imageReceiver = new ImageReceiver();
        TLRPC.Document document = MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emojicon);
        imageReceiver.setImage(ImageLocation.getForDocument(document), "50_50", null, null, null, 0);
        Emoji.preloadEmoji(emojicon);
    }

    public void requestAllChatThemes(final ResultCallback<List<EmojiThemes>> callback, boolean withDefault) {
        requestAllChatThemes(callback, withDefault, false);
    }

    public void requestAllChatThemes(final ResultCallback<List<EmojiThemes>> callback, boolean withDefault, boolean withGifts) {
        if (themesHash == 0 || lastReloadTimeMs == 0) {
            init();
        }

        boolean needReload = System.currentTimeMillis() - lastReloadTimeMs > reloadTimeoutMs;
        if (allChatThemes == null || allChatThemes.isEmpty() || needReload) {
            TL_account.getChatThemes request = new TL_account.getChatThemes();
            getSharedPreferences().edit().clear().apply();

            request.hash = themesHash;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> chatThemeQueue.postRunnable(() -> {
                boolean isError = false;
                final List<EmojiThemes> chatThemes;
                if (response instanceof TL_account.TL_themes) {
                    TL_account.TL_themes resp = (TL_account.TL_themes) response;
                    themesHash = resp.hash;
                    lastReloadTimeMs = System.currentTimeMillis();

                    SharedPreferences.Editor editor = getSharedPreferences().edit();
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
                        EmojiThemes chatTheme = new EmojiThemes.Default(currentAccount, tlChatTheme, false);
                        chatTheme.preloadWallpaper();
                        chatThemes.add(chatTheme);
                    }
                    editor.apply();
                } else if (response instanceof TL_account.TL_themesNotModified) {
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
                    if (withDefault && !chatThemes.isEmpty() && !chatThemes.get(0).showAsDefaultStub) {
                        chatThemes.add(0, EmojiThemes.createChatThemesDefault(currentAccount));
                    }
                    for (EmojiThemes theme : chatThemes) {
                        theme.initColors();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        allChatThemes = new ArrayList<>(chatThemes);
                        callback.onComplete(chatThemes);
                    });
                }
                if (withGifts) {
                    boolean finalIsError = isError;
                    hasMoreGiftThemes = true;
                    giftThemesOffset = 0;

                    requestGiftChatThemes(new ResultCallback<List<EmojiThemes>>() {
                        @Override
                        public void onComplete(List<EmojiThemes> result) {
                            if (result != null && !result.isEmpty()) {
                                if (!finalIsError) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        allChatThemes.addAll(result);
                                        callback.onComplete(allChatThemes);
                                    });
                                } else {
                                    if (withDefault && !result.get(0).showAsDefaultStub) {
                                        result.add(0, EmojiThemes.createChatThemesDefault(currentAccount));
                                    }
                                    for (EmojiThemes theme : result) {
                                        theme.initColors();
                                    }
                                    AndroidUtilities.runOnUIThread(() -> {
                                        allChatThemes = new ArrayList<>(result);
                                        callback.onComplete(result);
                                    });
                                }
                            }
                        }

                        @Override
                        public void onError(TLRPC.TL_error error) {
                            if (finalIsError) {
                                AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                            }
                        }
                    });
                }
            }));
        }
        if (allChatThemes != null && !allChatThemes.isEmpty()) {
            List<EmojiThemes> chatThemes = new ArrayList<>(allChatThemes);
            if (withDefault && !chatThemes.get(0).showAsDefaultStub) {
                chatThemes.add(0, EmojiThemes.createChatThemesDefault(currentAccount));
            }
            for (EmojiThemes theme : chatThemes) {
                theme.initColors();
            }
            AndroidUtilities.runOnUIThread(() -> callback.onComplete(chatThemes));
        }
    }

    public void setNeedUpdate(boolean needUpdate) {
        hasMoreGiftThemes = needUpdate;
    }

    public void requestGiftChatThemes(final ResultCallback<List<EmojiThemes>> callback) {
        if (starGiftHash == 0) {
            init();
        }

        if (!hasMoreGiftThemes) {
            callback.onComplete(new ArrayList<>());
            return;
        }

        TL_account.Tl_getUniqueGiftChatThemes request = new TL_account.Tl_getUniqueGiftChatThemes();
        request.hash = starGiftHash;
        request.offset = giftThemesOffset;
        request.limit = 20;
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(request, (response, error) -> chatThemeQueue.postRunnable(() -> {
            boolean isError = false;
            List<EmojiThemes> chatThemes;
            if (response instanceof TL_account.Tl_chatThemes) {
                TL_account.Tl_chatThemes resp = (TL_account.Tl_chatThemes) response;
                starGiftHash = resp.hash;

                SharedPreferences.Editor editor = getSharedPreferences().edit();
                editor.putLong("starGiftHash", starGiftHash);
                editor.putLong("giftThemesOffset", giftThemesOffset);
                editor.putInt("giftThemesCount", giftThemesOffset + resp.themes.size());
                chatThemes = new ArrayList<>(resp.themes.size());
                for (int i = 0; i < resp.themes.size(); ++i) {
                    TLRPC.ChatTheme tlChatTheme = resp.themes.get(i);
                    SerializedData data = new SerializedData(tlChatTheme.getObjectSize());
                    tlChatTheme.serializeToStream(data);
                    editor.putString("gift_theme_" + (giftThemesOffset + i), Utilities.bytesToHex(data.toByteArray()));
                    EmojiThemes chatTheme = EmojiThemes.createPreviewGiftTheme(currentAccount, tlChatTheme);
                    chatTheme.preloadWallpaper();
                    chatThemes.add(chatTheme);
                }
                if (resp.next_offset == 0) {
                    giftThemesOffset = giftThemesOffset + resp.themes.size();
                    hasMoreGiftThemes = false;
                } else {
                    giftThemesOffset = resp.next_offset;
                    hasMoreGiftThemes = true;
                }
                editor.putBoolean("hasMoreGiftThemes", hasMoreGiftThemes);
                editor.putInt("giftThemesOffset", giftThemesOffset);
                editor.apply();
                    
            } else {
                chatThemes = new ArrayList<>();
                if (response instanceof TL_account.TL_chatThemesNotModified) {

                } else {
                    isError = true;
                    AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                }
            }
            if (!isError && !chatThemes.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> callback.onComplete(chatThemes));
            }
        }));
    }

    private SharedPreferences getSharedPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_" + currentAccount, Context.MODE_PRIVATE);
    }

    private SharedPreferences getEmojiSharedPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("chatthemeconfig_emoji", Context.MODE_PRIVATE);
    }

    private List<EmojiThemes> getAllChatThemesFromPrefs() {
        SharedPreferences preferences = getSharedPreferences();
        int count = preferences.getInt("count", 0);
        List<EmojiThemes> themes = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            String value = preferences.getString("theme_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.TL_theme chatTheme = TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                if (chatTheme != null) {
                    themes.add(new EmojiThemes.Default(currentAccount, chatTheme, false));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        int giftCount = preferences.getInt("giftThemesCount", 0);
        for (int i = 0; i < giftCount; ++i) {
            String value = preferences.getString("gift_theme_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.ChatTheme tlChatTheme = TLRPC.ChatTheme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                if (tlChatTheme != null) {
                    themes.add(EmojiThemes.createPreviewGiftTheme(currentAccount, tlChatTheme));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return themes;
    }

    public void requestChatTheme(final String emoticon, final ResultCallback<EmojiThemes> callback) {
        if (TextUtils.isEmpty(emoticon)) {
            callback.onComplete(null);
            return;
        }
        requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                for (EmojiThemes theme : result) {
                    if (emoticon.equals(theme.getStickerUniqueKey())) {
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
        }, false, true);
        requestGiftChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                for (EmojiThemes theme : result) {
                    if (emoticon.equals(theme.getStickerUniqueKey())) {
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
        });
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


    private final LongSparseArray<EmojiThemes> dialogEmoticonsMap = new LongSparseArray<>();

    public static boolean equals(TLRPC.WallPaper wallPaper, TLRPC.WallPaper oldWallpaper) {
        if (wallPaper == null && oldWallpaper == null) {
            return true;
        }
        if (wallPaper != null && oldWallpaper != null) {
            if (wallPaper.uploadingImage != null) {
                return TextUtils.equals(oldWallpaper.uploadingImage, wallPaper.uploadingImage);
            }
            return wallPaper.id == oldWallpaper.id && TextUtils.equals(ChatBackgroundDrawable.hash(wallPaper.settings), ChatBackgroundDrawable.hash(oldWallpaper.settings)) && TextUtils.equals(ChatThemeController.getWallpaperEmoticon(wallPaper), ChatThemeController.getWallpaperEmoticon(oldWallpaper));
        }
        return false;
    }

    public void setDialogTheme(long dialogId, String emoticon, boolean sendRequest) {
        EmojiThemes theme = getTheme(emoticon);
        setDialogTheme(dialogId, theme, sendRequest);
    }

    public void setDialogTheme(long dialogId, EmojiThemes theme, boolean sendRequest) {
        if (theme == null) return;

        EmojiThemes oldTheme = dialogEmoticonsMap.get(dialogId);
        if (oldTheme != null && TextUtils.equals(oldTheme.getStickerUniqueKey(), theme.getStickerUniqueKey())) {
            return;
        }

        if (theme instanceof EmojiThemes.Gift && ((EmojiThemes.Gift) theme).starGift != null && ((EmojiThemes.Gift) theme).starGift.theme_peer != null) {
            long did = DialogObject.getPeerDialogId(((EmojiThemes.Gift) theme).starGift.theme_peer);
            TLRPC.UserFull userFull = getMessagesController().getUserFull(did);
            userFull.theme = null;
            clearWallpaper(did, true);
            getMessagesStorage().updateUserInfo(userFull, true);
            dialogEmoticonsMap.remove(did);
        }
        dialogEmoticonsMap.put(dialogId, theme);

        if (dialogId >= 0) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                if (theme instanceof EmojiThemes.Gift) {
                    ((EmojiThemes.Gift) theme).starGift.theme_peer = getMessagesController().getPeer(dialogId);
                    userFull.theme = TLRPC.ChatTheme.ofGift(((EmojiThemes.Gift) theme).starGift, ((EmojiThemes.Gift) theme).theme_settings);
                } else {
                    userFull.theme = TLRPC.ChatTheme.ofEmoticon(theme.getStickerUniqueKey());
                }
                getMessagesStorage().updateUserInfo(userFull, true);
            }
        } else {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                chatFull.theme_emoticon = theme.getStickerUniqueKey();
                getMessagesStorage().updateChatInfo(chatFull, true);
            }
        }

        getEmojiSharedPreferences().edit()
            .putString("chatTheme_" + currentAccount + "_" + dialogId, theme.getStickerUniqueKey())
            .apply();

        if (sendRequest) {
            TLRPC.TL_messages_setChatTheme request = new TLRPC.TL_messages_setChatTheme();
            if (!TextUtils.isEmpty(theme.getStickerUniqueKey())) {
                if (theme instanceof EmojiThemes.Gift) {
                    TLRPC.Tl_inputChatThemeUniqueGift inputTheme = new TLRPC.Tl_inputChatThemeUniqueGift();
                    inputTheme.slug = theme.getStickerUniqueKey();
                    request.theme = inputTheme;
                } else {
                    TLRPC.Tl_inputChatTheme inputTheme = new TLRPC.Tl_inputChatTheme();
                    inputTheme.emoticon = theme.getStickerUniqueKey();
                    request.theme = inputTheme;
                }
            } else {
                request.theme = new TLRPC.Tl_inputChatThemeEmpty();
            }
            request.peer = getMessagesController().getInputPeer(dialogId);
            getConnectionsManager().sendRequest(request, null);
        }
    }

    public EmojiThemes getDialogTheme(long dialogId) {
        EmojiThemes themes = dialogEmoticonsMap.get(dialogId);
        if (themes == null) {
            String emoticon = getEmojiSharedPreferences().getString("chatTheme_" + currentAccount + "_" + dialogId, null);
            if (emoticon != null) {
                dialogEmoticonsMap.put(dialogId, getTheme(emoticon));
                return getTheme(emoticon);
            }
            return null;
        }
        return themes;
    }

    public EmojiThemes getTheme(String key) {
        if (key != null) {
            for (EmojiThemes theme : allChatThemes) {
                if (key.equals(theme.getStickerUniqueKey())) {
                    return theme;
                }
            }
        }
        return null;
    }

    public void saveChatWallpaper(long dialogId, TLRPC.WallPaper wallPaper) {
        if (wallPaper != null) {
            if (wallPaper.document == null) {
                return;
            }
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
        if (dialogId >= 0) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                return userFull.wallpaper;
            }
        } else {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                return chatFull.wallpaper;
            }
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

    public void preloadAllWallpaperImages(boolean isDark) {
        for (EmojiThemes chatTheme : allChatThemes) {
            long themeId = chatTheme.getThemeId();
            if (themeId == 0 || getPatternFile(themeId).exists()) continue;
            chatTheme.loadWallpaper(isDark ? 1 : 0, null);
        }
    }

    public void preloadAllWallpaperThumbs(boolean isDark) {
        for (EmojiThemes chatTheme : allChatThemes) {
            long themeId = chatTheme.getThemeId();
            if (themeId == 0 || themeIdWallpaperThumbMap.containsKey(themeId)) {
                continue;
            }
            chatTheme.loadWallpaperThumb(isDark ? 1 : 0, result -> {
                if (result != null) {
                    themeIdWallpaperThumbMap.put(result.first, result.second);
                }
            });
        }
    }

    public void clearWallpaperImages() {

    }

    public void clearWallpaperThumbImages() {
        themeIdWallpaperThumbMap.clear();
    }

    public void getWallpaperBitmap(long themeId, ResultCallback<Bitmap> callback) {
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

    private File getPatternFile(long themeId) {
        return new File(ApplicationLoader.getFilesDirFixed(), String.format(Locale.US, "%d_%d.jpg", themeId, themesHash));
    }

    public void saveWallpaperBitmap(Bitmap bitmap, long themeId) {
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

    public Bitmap getWallpaperThumbBitmap(long themeId) {
        return themeIdWallpaperThumbMap.get(themeId);
    }

    public void clearCache() {
        themesHash = 0;
        lastReloadTimeMs = 0;
        getSharedPreferences().edit().clear().apply();
    }

    public void processUpdate(TLRPC.TL_updatePeerWallpaper update) {
        if (update.peer instanceof TLRPC.TL_peerUser) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(update.peer.user_id);
            if (userFull != null) {
                if (wallpaperEquals(userFull.wallpaper, update.wallpaper)) {
                    return;
                }
                final long dialogId = userFull.id;
                if ((update.flags & 1) != 0) {
                    userFull.wallpaper_overridden = update.wallpaper_overridden;
                    userFull.wallpaper = update.wallpaper;
                    userFull.flags |= 16777216;
                } else {
                    userFull.wallpaper_overridden = false;
                    userFull.wallpaper = null;
                    userFull.flags &= ~16777216;
                }
                getMessagesStorage().updateUserInfo(userFull, false);
                saveChatWallpaper(dialogId, userFull.wallpaper);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
                });
            }
        } else {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-DialogObject.getPeerDialogId(update.peer));
            if (chatFull != null) {
                if (wallpaperEquals(chatFull.wallpaper, update.wallpaper)) {
                    return;
                }
                final long dialogId = -chatFull.id;
                if ((update.flags & 1) != 0) {
                    chatFull.wallpaper = update.wallpaper;
                    chatFull.flags2 |= 128;
                } else {
                    chatFull.wallpaper = null;
                    chatFull.flags2 &= ~128;
                }
                getMessagesStorage().updateChatInfo(chatFull, false);
                saveChatWallpaper(dialogId, chatFull.wallpaper);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
                });
            }
        }
    }

    public static boolean wallpaperEquals(TLRPC.WallPaper a, org.telegram.tgnet.TLRPC.WallPaper b) {
        if (a == null && b == null) {
            return true;
        }
        if (a instanceof TLRPC.TL_wallPaper && b instanceof TLRPC.TL_wallPaper) {
            return a.id == b.id;
        }
        if (a instanceof TLRPC.TL_wallPaperNoFile && b instanceof TLRPC.TL_wallPaperNoFile) {
            if (a.settings != null && b.settings != null) {
                return TextUtils.equals(getWallpaperEmoticon(a), getWallpaperEmoticon(b));
            }
            return a.id == b.id;
        }
        return false;
    }

    public static String getWallpaperEmoticon(TLRPC.WallPaper a) {
        if (a != null) {
            if (a.settings != null && !TextUtils.isEmpty(a.settings.emoticon)) {
                return a.settings.emoticon;
            }
            return "";
        }
        return null;
    }

    public static boolean isNotEmoticonWallpaper(TLRPC.WallPaper a) {
        String emoticon = getWallpaperEmoticon(a);
        return emoticon != null && emoticon.length() == 0;
    }

    public void clearWallpaper(long dialogId, boolean notify) {
        clearWallpaper(dialogId, notify, false);
    }

    public void clearWallpaper(long dialogId, boolean notify, boolean onlyRevert) {
        TLRPC.TL_messages_setChatWallPaper req = new TLRPC.TL_messages_setChatWallPaper();
        if (dialogId >= 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            req.peer = MessagesController.getInputPeer(user);
            req.revert = onlyRevert;
            if (!onlyRevert) {
                TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
                if (userFull != null) {
                    userFull.wallpaper = null;
                    userFull.flags &= ~16777216;
                    getMessagesStorage().updateUserInfo(userFull, false);
                }
                saveChatWallpaper(dialogId, null);
                if (notify) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
                }
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            req.peer = MessagesController.getInputPeer(chat);
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                chatFull.wallpaper = null;
                chatFull.flags2 &= ~128;
                getMessagesStorage().updateChatInfo(chatFull, false);
            }
            saveChatWallpaper(dialogId, null);
            if (notify) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
            }
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public int setWallpaperToPeer(long dialogId, String wallpaperLocalPath, Theme.OverrideWallpaperInfo wallpaperInfo, MessageObject serverWallpaper, Runnable callback) {
        TLRPC.TL_messages_setChatWallPaper req = new TLRPC.TL_messages_setChatWallPaper();
        if (dialogId >= 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            req.peer = MessagesController.getInputPeer(user);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            req.peer = MessagesController.getInputPeer(chat);
        }
        req.for_both = wallpaperInfo.forBoth;
        boolean applyOnRequest = true;
        if (serverWallpaper != null && serverWallpaper.messageOwner.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
            applyOnRequest = false;
            req.flags |= 2;
            req.id = serverWallpaper.getId();

            TLRPC.UserFull userFull = null;
            TLRPC.ChatFull chatFull = null;
            if (dialogId >= 0) {
                userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            } else {
                chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            }

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
            TLRPC.WallPaper pastWallpaper = null;
            if (userFull != null) {
                pastWallpaper = userFull.wallpaper;
            } else if (chatFull != null) {
                pastWallpaper = chatFull.wallpaper;
            }
            if (pastWallpaper != null && pastWallpaper.uploadingImage != null && pastWallpaper.uploadingImage.equals(wallPaper.uploadingImage)) {
                wallPaper.stripedThumb = pastWallpaper.stripedThumb;
            }

            wallPaper.settings.flags |= 1;
            wallPaper.settings.flags |= 8;
            wallPaper.settings.flags |= 16;
            wallPaper.settings.flags |= 32;
            wallPaper.settings.flags |= 64;

            TLRPC.TL_wallPaper wallpaper = new TLRPC.TL_wallPaper();
            wallpaper.pattern = action.wallpaper.pattern;
            wallpaper.id = action.wallpaper.id;
            wallpaper.document = action.wallpaper.document;
            wallpaper.flags = action.wallpaper.flags;
            wallpaper.creator = action.wallpaper.creator;
            wallpaper.dark = action.wallpaper.dark;
            wallpaper.isDefault = action.wallpaper.isDefault;
            wallpaper.slug = action.wallpaper.slug;
            wallpaper.access_hash = action.wallpaper.access_hash;
            wallpaper.stripedThumb = action.wallpaper.stripedThumb;
            wallpaper.settings = wallPaper.settings;
            wallpaper.flags |= 4;
            if (userFull != null) {
                userFull.wallpaper = wallpaper;
                userFull.flags |= 16777216;
                getMessagesStorage().updateUserInfo(userFull, false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
            } else if (chatFull != null) {
                chatFull.wallpaper = wallpaper;
                chatFull.flags2 |= 128;
                getMessagesStorage().updateChatInfo(chatFull, false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
            }

            if (callback != null) {
                callback.run();
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
                TLRPC.UserFull userFull = null;
                TLRPC.ChatFull chatFull = null;
                if (dialogId >= 0) {
                    userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                } else {
                    chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                }
                TLRPC.WallPaper pastWallpaper = null;
                if (userFull != null) {
                    pastWallpaper = userFull.wallpaper;
                } else if (chatFull != null) {
                    pastWallpaper = chatFull.wallpaper;
                }
                for (int i = 0; i < res.updates.size(); i++) {
                    if (res.updates.get(i) instanceof TLRPC.TL_updateNewMessage) {
                        TLRPC.Message message = ((TLRPC.TL_updateNewMessage) res.updates.get(i)).message;
                        if (message.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
                            if (finalApplyOnRequest) {
                                TLRPC.TL_messageActionSetChatWallPaper actionSetChatWallPaper = (TLRPC.TL_messageActionSetChatWallPaper) message.action;
                                actionSetChatWallPaper.wallpaper.uploadingImage = wallpaperLocalPath;
                                if (pastWallpaper != null && pastWallpaper.uploadingImage != null && pastWallpaper.uploadingImage.equals(actionSetChatWallPaper.wallpaper.uploadingImage)) {
                                    actionSetChatWallPaper.wallpaper.stripedThumb = pastWallpaper.stripedThumb;
                                }
                                if (userFull != null) {
                                    userFull.wallpaper = actionSetChatWallPaper.wallpaper;
                                    userFull.flags |= 16777216;
                                    saveChatWallpaper(dialogId, userFull.wallpaper);
                                    getMessagesStorage().updateUserInfo(userFull, false);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, dialogId, userFull);
                                } else if (chatFull != null) {
                                    chatFull.wallpaper = actionSetChatWallPaper.wallpaper;
                                    chatFull.flags2 |= 128;
                                    saveChatWallpaper(dialogId, chatFull.wallpaper);
                                    getMessagesStorage().updateChatInfo(chatFull, false);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
                                }
                            }
                            break;
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
