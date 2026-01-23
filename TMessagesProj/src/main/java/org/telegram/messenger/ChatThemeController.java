package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import org.telegram.messenger.wallpaper.WallpaperBitmapHolder;
import org.telegram.messenger.wallpaper.WallpaperGiftPatternPosition;
import org.telegram.messenger.wallpaper.pgm.PGMImage;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.theme.ThemeKey;
import org.telegram.ui.ChatBackgroundDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ChatThemeController extends BaseController {

    private final long reloadTimeoutMs = 2 * 60 * 60 * 1000;
    public static volatile DispatchQueue chatThemeQueue = new DispatchQueue("chatThemeQueue");

    private final HashMap<Long, Bitmap> themeIdWallpaperThumbMap = new HashMap<>();
    private List<EmojiThemes> allChatThemes;
    private volatile long themesHash;
    private volatile long lastReloadTimeMs;

    private final Map<String, EmojiThemes> allChatGiftThemes = new HashMap<>();
    private final ThemeList giftsThemeList = new ThemeList();

    private static class ThemeList {
        private List<EmojiThemes> themes;
        private long hash;
        private String offset;
        private long lastReloadTimeMs;
        private boolean completed;
    }

    private ChatThemeController(int num) {
        super(num);
        init();
    }

    private void init() {
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
        getMessagesStorage().loadGiftChatTheme(themes -> {
            if (themes != null) {
                for (TLRPC.TL_chatThemeUniqueGift theme: themes) {
                    EmojiThemes emojiThemes = new EmojiThemes(currentAccount, theme);
                    allChatGiftThemes.put(theme.gift.slug, emojiThemes);
                }
            }
        });

        preloadSticker("❌");
        if (!allChatThemes.isEmpty()) {
            for (EmojiThemes chatTheme : allChatThemes) {
               preloadSticker(chatTheme.getEmoticon());
            }
        }
    }

    public void putThemeIfNeeded(TLRPC.ChatTheme theme) {
        if (theme instanceof TLRPC.TL_chatThemeUniqueGift) {
            TLRPC.TL_chatThemeUniqueGift giftTheme = (TLRPC.TL_chatThemeUniqueGift) theme;
            if (!allChatGiftThemes.containsKey(giftTheme.gift.slug)) {
                EmojiThemes emojiThemes = new EmojiThemes(currentAccount, giftTheme);
                emojiThemes.initColors();
                allChatGiftThemes.put(giftTheme.gift.slug, emojiThemes);
                getMessagesStorage().putGiftChatTheme(theme);

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
        if (themesHash == 0 || lastReloadTimeMs == 0) {
            init();
        }

        boolean needReload = System.currentTimeMillis() - lastReloadTimeMs > reloadTimeoutMs;
        if (allChatThemes == null || allChatThemes.isEmpty() || needReload) {
            TL_account.getChatThemes request = new TL_account.getChatThemes();
            request.hash = themesHash;
            getConnectionsManager().sendRequestTyped(request, chatThemeQueue::postRunnable, (response, error) -> {
                boolean isError = false;
                final List<EmojiThemes> chatThemes;
                if (response instanceof TL_account.TL_themes) {
                    TL_account.TL_themes resp = (TL_account.TL_themes) response;
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
                        EmojiThemes chatTheme = new EmojiThemes(currentAccount, tlChatTheme, false);
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
                    AndroidUtilities.runOnUIThread(() -> {
                        allChatThemes = new ArrayList<>(chatThemes);
                        callback.onComplete(getEmojiThemes((withDefault ? THEME_LIST_WITH_DEFAULT : 0) | THEME_LIST_WITH_EMOJI));
                    });
                }
            });
        }
        if (allChatThemes != null && !allChatThemes.isEmpty()) {
            callback.onComplete(getEmojiThemes((withDefault ? THEME_LIST_WITH_DEFAULT : 0) | THEME_LIST_WITH_EMOJI));
        }
    }

    public void loadNextChatThemes(ResultCallback<Void> callback) {
        requestNextChatThemes(callback);
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
                    themes.add(new EmojiThemes(currentAccount, chatTheme, false));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return themes;
    }

    public void requestChatTheme(final ThemeKey key, final ResultCallback<EmojiThemes> callback) {
        if (key == null || key.isEmpty()) {
            callback.onComplete(null);
            return;
        }

        if (!TextUtils.isEmpty(key.giftSlug)) {
            EmojiThemes theme = allChatGiftThemes.get(key.giftSlug);
            if (theme != null) {
                theme.initColors();
                callback.onComplete(theme);
            } else {
                callback.onComplete(null);
            }
            return;
        }

        requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                for (EmojiThemes theme : result) {
                    if (key.equals(theme.getThemeKey())) {
                        theme.initColors();
                        callback.onComplete(theme);
                        return;
                    }
                }
                callback.onComplete(null);
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


    private final LongSparseArray<ThemeKey> dialogEmoticonsMap = new LongSparseArray<>();

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

    public void setDialogTheme(final long dialogId, final @Nullable TLRPC.ChatTheme theme, final boolean sendRequest) {
        final ThemeKey themeKey = ThemeKey.of(theme);
        setDialogTheme(dialogId, themeKey, theme, sendRequest);
    }

    public void setDialogTheme(final long dialogId, final @Nullable ThemeKey themeKey) {
        setDialogTheme(dialogId, themeKey, null, true);
    }

    private void setDialogTheme(final long dialogId, final @Nullable ThemeKey themeKey, final @Nullable TLRPC.ChatTheme theme, final boolean sendRequest) {
        ThemeKey oldKey = dialogEmoticonsMap.get(dialogId);
        if (ThemeKey.equals(oldKey, themeKey)) {
            return;
        }

        if (themeKey == null) {
            dialogEmoticonsMap.delete(dialogId);
        } else {
            dialogEmoticonsMap.put(dialogId, themeKey);
        }

        setGiftThemeUser(themeKey != null ? themeKey.giftSlug: null, dialogId);

        if (dialogId >= 0) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(dialogId);
            if (userFull != null) {
                if (themeKey == null || themeKey.isEmpty() || theme != null) {
                    userFull.theme = theme;
                    getMessagesStorage().updateUserInfo(userFull, true);
                }
            }
        } else {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                chatFull.theme_emoticon = themeKey != null ? themeKey.emoticon : null;
                getMessagesStorage().updateChatInfo(chatFull, true);
            }
        }

        getEmojiSharedPreferences().edit()
                .putString("chatTheme_" + currentAccount + "_" + dialogId, themeKey != null ? themeKey.toSavedString() : null)
                .apply();

        if (sendRequest) {
            TLRPC.TL_messages_setChatTheme request = new TLRPC.TL_messages_setChatTheme();
            request.theme = ThemeKey.toInputTheme(themeKey);
            request.peer = getMessagesController().getInputPeer(dialogId);
            getConnectionsManager().sendRequestTyped(request, null, (updates, error) -> {
                if (updates != null) {
                    getMessagesController().processUpdates(updates, false);
                }
            });
        }
    }

    public EmojiThemes getDialogTheme(long dialogId) {
        ThemeKey themeKey = dialogEmoticonsMap.get(dialogId);
        if (themeKey == null) {
            themeKey = ThemeKey.fromSavedString(getEmojiSharedPreferences().getString("chatTheme_" + currentAccount + "_" + dialogId, null));
            dialogEmoticonsMap.put(dialogId, themeKey);
        }
        return getTheme(themeKey);
    }

    public EmojiThemes getTheme(ThemeKey themeKey) {
        if (themeKey != null) {
            if (!TextUtils.isEmpty(themeKey.giftSlug)) {
                return allChatGiftThemes.get(themeKey.giftSlug);
            }
            for (EmojiThemes theme : allChatThemes) {
                if (themeKey.equals(theme.getThemeKey())) {
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
            long themeId = chatTheme.getThemeId(isDark ? 1 : 0);
            if (themeId == 0) {
                continue;
            }
            if (getPatternFile(themeId).exists()) {
                continue;
            }
            chatTheme.loadWallpaper(isDark ? 1 : 0, null);
        }
    }

    public void preloadAllWallpaperThumbs(boolean isDark) {
        for (EmojiThemes chatTheme : allChatThemes) {
            long themeId = chatTheme.getThemeId(isDark ? 1 : 0);
            if (themeId == 0) {
                continue;
            }
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

    public void clearWallpaperImages() {

    }

    public void clearWallpaperThumbImages() {
        themeIdWallpaperThumbMap.clear();
    }

    private void getWallpaperBitmap(long themeId, ResultCallback<Bitmap> callback) {
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

    private void saveWallpaperBitmap(Bitmap bitmap, long themeId) {
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




    public void saveWallpaperBitmap(WallpaperBitmapHolder wallpaper, long wallpaperId) {
        final Bitmap bitmap = wallpaper.bitmap;
        final int mode = wallpaper.mode;

        if (mode == WallpaperBitmapHolder.MODE_DEFAULT) {
            saveWallpaperBitmap(bitmap, wallpaperId);
        } else if (mode == WallpaperBitmapHolder.MODE_PATTERN) {
            saveWallpaperPatternBitmap(bitmap, wallpaper.giftPatternPositions, wallpaperId);
        }
    }

    public void loadWallpaperBitmap(long wallpaperId, int mode, Utilities.Callback<WallpaperBitmapHolder> callback) {
        if (mode == WallpaperBitmapHolder.MODE_DEFAULT) {
            getWallpaperBitmap(wallpaperId, bitmap -> {
                if (bitmap != null) {
                    callback.run(new WallpaperBitmapHolder(bitmap, WallpaperBitmapHolder.MODE_DEFAULT));
                } else {
                    callback.run(null);
                }
            });
        } else if (mode == WallpaperBitmapHolder.MODE_PATTERN) {
            loadWallpaperPatternBitmap(wallpaperId, callback);
        }
    }



    private void loadWallpaperPatternBitmap(long wallpaperId, Utilities.Callback<WallpaperBitmapHolder> callback) {
        final File file = new File(
            ApplicationLoader.getFilesDirFixed("rasterized/wallpaper"),
            String.format(Locale.US, "pattern_%d.pgm.gz", wallpaperId));

        chatThemeQueue.postRunnable(() -> {
            List<WallpaperGiftPatternPosition> positions = null;
            Bitmap bitmap = null;

            try (
                InputStream fileStream = new FileInputStream(file);
                InputStream gzipStream = new GZIPInputStream(fileStream)
            ) {
                ArrayList<String> comments = new ArrayList<>(1);
                bitmap = PGMImage.read(gzipStream, comments);

                for (String comment : comments) {
                    if (comment.startsWith("patterns = ")) {
                        final byte[] buffer = Utilities.hexToBytes(comment.substring("patterns = ".length()));
                        final int count = buffer.length / WallpaperGiftPatternPosition.SERIALIZED_BUFFER_SIZE;
                        final SerializedData data = new SerializedData(buffer);

                        positions = new ArrayList<>(count);
                        for (int a = 0; a < count; a++) {
                            positions.add(WallpaperGiftPatternPosition.deserialize(data));
                        }

                        data.cleanup();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            final WallpaperBitmapHolder bitmapHolder;
            if (bitmap != null) {
                bitmapHolder = new WallpaperBitmapHolder(bitmap, WallpaperBitmapHolder.MODE_PATTERN, positions);
            } else {
                bitmapHolder = null;
            }

            AndroidUtilities.runOnUIThread(() -> callback.run(bitmapHolder));
        });
    }

    private void saveWallpaperPatternBitmap(Bitmap bitmap, List<WallpaperGiftPatternPosition> positions, long wallpaperId) {
        File file = new File(
            ApplicationLoader.getFilesDirFixed("rasterized/wallpaper"),
            String.format(Locale.US, "pattern_%d.pgm.gz", wallpaperId));
        chatThemeQueue.postRunnable(() -> {
            try (
                OutputStream fileStream = new FileOutputStream(file);
                OutputStream gzipStream = new GZIPOutputStream(fileStream)
            ) {
                List<String> comments = null;
                if (positions != null && !positions.isEmpty()) {
                    SerializedData data = new SerializedData(WallpaperGiftPatternPosition.SERIALIZED_BUFFER_SIZE * positions.size());
                    for (WallpaperGiftPatternPosition position: positions) {
                        position.serialize(data);
                    }

                    comments = Collections.singletonList("patterns = " + Utilities.bytesToHex(data.toByteArray()));
                    data.cleanup();
                }

                if (bitmap.getConfig() == Bitmap.Config.ALPHA_8) {
                    PGMImage.write(bitmap, gzipStream, comments);
                } else {
                    Bitmap tmpBitmap = bitmap.extractAlpha();
                    PGMImage.write(tmpBitmap, gzipStream, comments);
                    tmpBitmap.recycle();
                }
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
                    userFull.flags &=~ 16777216;
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
                    chatFull.flags2 &=~ 128;
                }
                getMessagesStorage().updateChatInfo(chatFull, false);
                saveChatWallpaper(dialogId, chatFull.wallpaper);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
                });
            }
        }
    }

    public static boolean wallpaperEquals(TLRPC.WallPaper a, TLRPC.WallPaper b) {
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

    private final Map<Long, String> usedGiftThemesByUsers = new HashMap<>();
    private final Map<String, Long> usedGiftThemesBySlug = new HashMap<>();

    private void setGiftThemeUser(String slug, long dialogId) {
        if (TextUtils.isEmpty(slug)) {
            String oldSlug = usedGiftThemesByUsers.remove(dialogId);
            if (oldSlug != null) {
                usedGiftThemesBySlug.remove(oldSlug);
            }
            return;
        }

        if (dialogId == 0) {
            Long oldDialog = usedGiftThemesBySlug.remove(slug);
            if (oldDialog != null) {
                usedGiftThemesByUsers.remove(oldDialog);
            }
            return;
        }

        String oldSlug = usedGiftThemesByUsers.put(dialogId, slug);
        Long oldDialog = usedGiftThemesBySlug.put(slug, dialogId);

        if (oldSlug != null && !TextUtils.equals(slug, oldSlug)) {
            usedGiftThemesBySlug.remove(oldSlug);
        }
        if (oldDialog != null && oldDialog != dialogId) {
            usedGiftThemesByUsers.remove(oldDialog);
        }
    }

    public long getGiftThemeUser(String slug) {
        Long did = usedGiftThemesBySlug.get(slug);
        if (did != null) {
            return did;
        }
        return 0;
    }





    public static final int THEME_LIST_WITH_DEFAULT = 1;
    public static final int THEME_LIST_WITH_EMOJI = 1 << 1;
    public static final int THEME_LIST_WITH_GIFTS = 1 << 2;

    public List<EmojiThemes> getEmojiThemes(int flags) {
        final boolean withDefault = TLObject.hasFlag(flags, THEME_LIST_WITH_DEFAULT);
        final boolean withEmoji = TLObject.hasFlag(flags, THEME_LIST_WITH_EMOJI);
        final boolean withGifts = TLObject.hasFlag(flags, THEME_LIST_WITH_GIFTS);

        final List<EmojiThemes> result = new ArrayList<>();
        if (withGifts && giftsThemeList.themes != null) {
            result.addAll(giftsThemeList.themes);
        }

        if (withEmoji && allChatThemes != null) {
            result.addAll(allChatThemes);
        }

        if (withDefault && (result.isEmpty() || !result.get(0).showAsDefaultStub)) {
            result.add(0, EmojiThemes.createChatThemesDefault(currentAccount));
        }

        for (EmojiThemes theme : result) {
            theme.initColors();
        }
        return result;
    }

    public boolean isAllThemesFullyLoaded() {
        return isGiftThemesFullyLoaded() && allChatThemes != null && !allChatThemes.isEmpty();
    }

    public boolean isGiftThemesFullyLoaded() {
        return giftsThemeList.completed;
    }

    private void requestNextChatThemes(ResultCallback<Void> callback) {
        if (giftsThemeList.hash == 0 || giftsThemeList.lastReloadTimeMs == 0) {
            // init();
        }

        final boolean needReload = System.currentTimeMillis() - giftsThemeList.lastReloadTimeMs > reloadTimeoutMs;

        if (giftsThemeList.themes == null || !giftsThemeList.completed || needReload) {
            final TL_account.Tl_getUniqueGiftChatThemes req = new TL_account.Tl_getUniqueGiftChatThemes();
            req.offset = giftsThemeList.offset;
            req.hash = giftsThemeList.hash;
            req.limit = 50;

            getConnectionsManager().sendRequestTyped(req, chatThemeQueue::postRunnable, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        callback.onError(error);
                    });
                    return;
                }

                final List<TLRPC.TL_chatThemeUniqueGift> themes = new ArrayList<>();
                if (response instanceof TL_account.Tl_chatThemes) {
                    final TL_account.Tl_chatThemes t = (TL_account.Tl_chatThemes) response;

                    getMessagesStorage().putGiftChatThemes(t.themes);
                    getMessagesStorage().putUsersAndChats(t.users, t.chats, true, true);
                    getMessagesController().putUsers(t.users, false);
                    getMessagesController().putChats(t.chats, false);

                    for (TLRPC.ChatTheme theme: t.themes) {
                        if (theme instanceof TLRPC.TL_chatThemeUniqueGift) {
                            themes.add((TLRPC.TL_chatThemeUniqueGift) theme);
                        }
                    }

                    final List<EmojiThemes> chatThemes = new ArrayList<>(themes.size());
                    for (int i = 0; i < themes.size(); ++i) {
                        TLRPC.TL_chatThemeUniqueGift tlChatTheme = themes.get(i);
                        EmojiThemes chatTheme = new EmojiThemes(currentAccount, tlChatTheme);
                        chatTheme.preloadWallpaper();
                        chatThemes.add(chatTheme);
                    }

                    // todo save themes

                    AndroidUtilities.runOnUIThread(() -> {
                        giftsThemeList.offset = t.next_offset;
                        giftsThemeList.hash = t.hash;
                        giftsThemeList.lastReloadTimeMs = System.currentTimeMillis();
                        if (giftsThemeList.themes == null) {
                            giftsThemeList.themes = new ArrayList<>(chatThemes);
                        } else {
                            giftsThemeList.themes.addAll(chatThemes);
                        }
                        if (TextUtils.isEmpty(t.next_offset)) {
                            giftsThemeList.completed = true;
                        }

                        for (EmojiThemes emojiTheme: chatThemes) {
                            allChatGiftThemes.put(emojiTheme.getEmoticonOrSlug(), emojiTheme);
                        }
                        for (TLRPC.TL_chatThemeUniqueGift theme: themes) {
                            long busyByDialogId = DialogObject.getPeerDialogId(theme.gift.theme_peer);
                            setGiftThemeUser(theme.gift.slug, busyByDialogId);
                        }

                        callback.onComplete(null);
                    });
                } else if (response instanceof TL_account.TL_chatThemesNotModified) {
                    AndroidUtilities.runOnUIThread(() -> {
                        giftsThemeList.lastReloadTimeMs = System.currentTimeMillis();
                        giftsThemeList.completed = true;
                        callback.onComplete(null);
                    });
                }
            });
        }
    }
}
