package org.telegram.messenger;

import android.os.SystemClock;
import android.util.Log;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class FileRefController extends BaseController {

    private static class Requester {
        private TLRPC.InputFileLocation location;
        private Object[] args;
        private String locationKey;
        private boolean completed;
    }

    private static class CachedResult {
        private TLObject response;
        private long firstQueryTime;
    }

    private static class Waiter {

        private String locationKey;
        private String parentKey;

        public Waiter(String loc, String parent) {
            locationKey = loc;
            parentKey = parent;
        }
    }

    private HashMap<String, ArrayList<Requester>> locationRequester = new HashMap<>();
    private HashMap<String, ArrayList<Requester>> parentRequester = new HashMap<>();
    private HashMap<String, CachedResult> responseCache = new HashMap<>();
    private HashMap<TLRPC.TL_messages_sendMultiMedia, Object[]> multiMediaCache = new HashMap<>();

    private long lastCleanupTime = SystemClock.elapsedRealtime();

    private ArrayList<Waiter> wallpaperWaiters = new ArrayList<>();
    private ArrayList<Waiter> savedGifsWaiters = new ArrayList<>();
    private ArrayList<Waiter> recentStickersWaiter = new ArrayList<>();
    private ArrayList<Waiter> favStickersWaiter = new ArrayList<>();

    private static volatile FileRefController[] Instance = new FileRefController[UserConfig.MAX_ACCOUNT_COUNT];

    public static FileRefController getInstance(int num) {
        FileRefController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (FileRefController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new FileRefController(num);
                }
            }
        }
        return localInstance;
    }

    public FileRefController(int instance) {
        super(instance);
    }

    public static String getKeyForParentObject(Object parentObject) {
        if (parentObject instanceof TL_stories.StoryItem) {
            TL_stories.StoryItem storyItem = (TL_stories.StoryItem) parentObject;
            if (storyItem.dialogId == 0) {
                FileLog.d("failed request reference can't find dialogId");
                return null;
            }
            return "story_" + storyItem.dialogId + "_" + storyItem.id;
        } else if (parentObject instanceof TLRPC.TL_help_premiumPromo) {
            return "premium_promo";
        } else if (parentObject instanceof TLRPC.TL_availableReaction) {
            return "available_reaction_" + ((TLRPC.TL_availableReaction) parentObject).reaction;
        } else if (parentObject instanceof TLRPC.BotInfo) {
            TLRPC.BotInfo botInfo = (TLRPC.BotInfo) parentObject;
            return "bot_info_" + botInfo.user_id;
        } else if (parentObject instanceof TLRPC.TL_attachMenuBot) {
            TLRPC.TL_attachMenuBot bot = (TLRPC.TL_attachMenuBot) parentObject;
            long botId = bot.bot_id;
            return "attach_menu_bot_" + botId;
        } else if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            long channelId = messageObject.getChannelId();
            return "message" + messageObject.getRealId() + "_" + channelId + "_" + messageObject.scheduled + "_" + messageObject.getQuickReplyId();
        } else if (parentObject instanceof TLRPC.Message) {
            TLRPC.Message message = (TLRPC.Message) parentObject;
            long channelId = message.peer_id != null ? message.peer_id.channel_id : 0;
            return "message" + message.id + "_" + channelId + "_" + message.from_scheduled;
        } else if (parentObject instanceof TLRPC.WebPage) {
            TLRPC.WebPage webPage = (TLRPC.WebPage) parentObject;
            return "webpage" + webPage.id;
        } else if (parentObject instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) parentObject;
            return "user" + user.id;
        } else if (parentObject instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) parentObject;
            return "chat" + chat.id;
        } else if (parentObject instanceof String) {
            String string = (String) parentObject;
            return "str" + string;
        } else if (parentObject instanceof TLRPC.TL_messages_stickerSet) {
            TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) parentObject;
            return "set" + stickerSet.set.id;
        } else if (parentObject instanceof TLRPC.StickerSetCovered) {
            TLRPC.StickerSetCovered stickerSet = (TLRPC.StickerSetCovered) parentObject;
            return "set" + stickerSet.set.id;
        } else if (parentObject instanceof TLRPC.InputStickerSet) {
            TLRPC.InputStickerSet inputStickerSet = (TLRPC.InputStickerSet) parentObject;
            return "set" + inputStickerSet.id;
        } else if (parentObject instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) parentObject;
            return "wallpaper" + wallPaper.id;
        } else if (parentObject instanceof TLRPC.TL_theme) {
            TLRPC.TL_theme theme = (TLRPC.TL_theme) parentObject;
            return "theme" + theme.id;
        }
        return parentObject != null ? "" + parentObject : null;
    }

    @SuppressWarnings("unchecked")
    public void requestReference(Object parentObject, Object... args) {
        String locationKey;
        TLRPC.InputFileLocation location;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start loading request reference parent " + getObjectString(parentObject) + " args = " + args[0]);
        }
        if (args[0] instanceof TL_stories.TL_storyItem) {
            TL_stories.TL_storyItem storyItem = (TL_stories.TL_storyItem) args[0];
            locationKey = "story_" + storyItem.id;
            location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = storyItem.media.document.id;
        } else if (args[0] instanceof TLRPC.TL_inputSingleMedia) {
            TLRPC.TL_inputSingleMedia req = (TLRPC.TL_inputSingleMedia) args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                locationKey = "file_" + mediaDocument.id.id;
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = mediaDocument.id.id;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                locationKey = "photo_" + mediaPhoto.id.id;
                location = new TLRPC.TL_inputPhotoFileLocation();
                location.id = mediaPhoto.id.id;
            } else {
                sendErrorToObject(args, 0);
                return;
            }
        } else if (args[0] instanceof TLRPC.TL_messages_sendMultiMedia) {
            TLRPC.TL_messages_sendMultiMedia req = (TLRPC.TL_messages_sendMultiMedia) args[0];
            ArrayList<Object> parentObjects = (ArrayList<Object>) parentObject;
            multiMediaCache.put(req, args);
            for (int a = 0, size = req.multi_media.size(); a < size; a++) {
                TLRPC.TL_inputSingleMedia media = req.multi_media.get(a);
                parentObject = parentObjects.get(a);
                if (parentObject == null) {
                    continue;
                }
                requestReference(parentObject, media, req);
            }
            return;
        } else if (args[0] instanceof TLRPC.TL_messages_sendMedia) {
            TLRPC.TL_messages_sendMedia req = (TLRPC.TL_messages_sendMedia) args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                locationKey = "file_" + mediaDocument.id.id;
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = mediaDocument.id.id;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                locationKey = "photo_" + mediaPhoto.id.id;
                location = new TLRPC.TL_inputPhotoFileLocation();
                location.id = mediaPhoto.id.id;
            } else {
                sendErrorToObject(args, 0);
                return;
            }
        } else if (args[0] instanceof TLRPC.TL_messages_editMessage) {
            TLRPC.TL_messages_editMessage req = (TLRPC.TL_messages_editMessage) args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                locationKey = "file_" + mediaDocument.id.id;
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = mediaDocument.id.id;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                locationKey = "photo_" + mediaPhoto.id.id;
                location = new TLRPC.TL_inputPhotoFileLocation();
                location.id = mediaPhoto.id.id;
            } else {
                sendErrorToObject(args, 0);
                return;
            }
        } else if (args[0] instanceof TLRPC.TL_messages_saveGif) {
            TLRPC.TL_messages_saveGif req = (TLRPC.TL_messages_saveGif) args[0];
            locationKey = "file_" + req.id.id;
            location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = req.id.id;
        } else if (args[0] instanceof TLRPC.TL_messages_saveRecentSticker) {
            TLRPC.TL_messages_saveRecentSticker req = (TLRPC.TL_messages_saveRecentSticker) args[0];
            locationKey = "file_" + req.id.id;
            location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = req.id.id;
        } else if (args[0] instanceof TLRPC.TL_stickers_addStickerToSet) {
            TLRPC.TL_stickers_addStickerToSet req = (TLRPC.TL_stickers_addStickerToSet) args[0];
            locationKey = "file_" + req.sticker.document.id;
            location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = req.sticker.document.id;
        } else if (args[0] instanceof TLRPC.TL_messages_faveSticker) {
            TLRPC.TL_messages_faveSticker req = (TLRPC.TL_messages_faveSticker) args[0];
            locationKey = "file_" + req.id.id;
            location = new TLRPC.TL_inputDocumentFileLocation();
            location.id = req.id.id;
        } else if (args[0] instanceof TLRPC.TL_messages_getAttachedStickers) {
            TLRPC.TL_messages_getAttachedStickers req = (TLRPC.TL_messages_getAttachedStickers) args[0];
            if (req.media instanceof TLRPC.TL_inputStickeredMediaDocument) {
                TLRPC.TL_inputStickeredMediaDocument mediaDocument = (TLRPC.TL_inputStickeredMediaDocument) req.media;
                locationKey = "file_" + mediaDocument.id.id;
                location = new TLRPC.TL_inputDocumentFileLocation();
                location.id = mediaDocument.id.id;
            } else if (req.media instanceof TLRPC.TL_inputStickeredMediaPhoto) {
                TLRPC.TL_inputStickeredMediaPhoto mediaPhoto = (TLRPC.TL_inputStickeredMediaPhoto) req.media;
                locationKey = "photo_" + mediaPhoto.id.id;
                location = new TLRPC.TL_inputPhotoFileLocation();
                location.id = mediaPhoto.id.id;
            } else {
                sendErrorToObject(args, 0);
                return;
            }
        } else if (args[0] instanceof TLRPC.TL_inputFileLocation) {
            location = (TLRPC.TL_inputFileLocation) args[0];
            locationKey = "loc_" + location.local_id + "_" + location.volume_id;
        } else if (args[0] instanceof TLRPC.TL_inputDocumentFileLocation) {
            location = (TLRPC.TL_inputDocumentFileLocation) args[0];
            locationKey = "file_" + location.id;
        } else if (args[0] instanceof TLRPC.TL_inputPhotoFileLocation) {
            location = (TLRPC.TL_inputPhotoFileLocation) args[0];
            locationKey = "photo_" + location.id;
        } else if (args[0] instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
            location = (TLRPC.TL_inputPeerPhotoFileLocation) args[0];
            locationKey = "avatar_" + location.id;
        } else {
            sendErrorToObject(args, 0);
            return;
        }
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            if (messageObject.getRealId() < 0 && messageObject.messageOwner != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                parentObject = messageObject.messageOwner.media.webpage;
            }
        }
        String parentKey = getKeyForParentObject(parentObject);

        if (parentKey == null) {
            sendErrorToObject(args, 0);
            return;
        }

        Requester requester = new Requester();
        requester.args = args;
        requester.location = location;
        requester.locationKey = locationKey;

        int added = 0;
        ArrayList<Requester> arrayList = locationRequester.get(locationKey);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            locationRequester.put(locationKey, arrayList);
            added++;
        }
        arrayList.add(requester);

        arrayList = parentRequester.get(parentKey);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            parentRequester.put(parentKey, arrayList);
            added++;
        }
        arrayList.add(requester);
        if (added != 2) {
            return;
        }

        String cacheKey = locationKey;
        if (parentObject instanceof String) {
            String string = (String) parentObject;
            if ("wallpaper".equals(string)) {
                cacheKey = "wallpaper";
            } else if (string.startsWith("gif")) {
                cacheKey = "gif";
            } else if ("recent".equals(string)) {
                cacheKey = "recent";
            } else if ("fav".equals(string)) {
                cacheKey = "fav";
            } else if ("update".equals(string)) {
                cacheKey = "update";
            }
        }

        cleanupCache();
        CachedResult cachedResult = getCachedResponse(cacheKey);
        if (cachedResult != null) {
            if (!onRequestComplete(locationKey, parentKey, cachedResult.response, null,false, true)) {
                responseCache.remove(locationKey);
            } else {
                return;
            }
        } else {
            cachedResult = getCachedResponse(parentKey);
            if (cachedResult != null) {
                if (!onRequestComplete(locationKey, parentKey, cachedResult.response, null, false, true)) {
                    responseCache.remove(parentKey);
                } else {
                    return;
                }
            }
        }
        requestReferenceFromServer(parentObject, locationKey, parentKey, args);
    }

    private String getObjectString(Object parentObject) {
        if (parentObject instanceof String) {
            return (String) parentObject;
        }
        if (parentObject instanceof TL_stories.StoryItem) {
            TL_stories.StoryItem storyItem = (TL_stories.StoryItem) parentObject;
            return "story(dialogId=" + storyItem.dialogId + " id=" + storyItem.id + ")";
        }
        if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            return "message(dialogId=" + messageObject.getDialogId() + "messageId" + messageObject.getId() + ")";
        }
        if (parentObject == null) {
            return null;
        }
        return parentObject.getClass().getSimpleName();
    }

    private void broadcastWaitersData(ArrayList<Waiter> waiters, TLObject response, TLRPC.TL_error error) {
        for (int a = 0, N = waiters.size(); a < N; a++) {
            Waiter waiter = waiters.get(a);
            onRequestComplete(waiter.locationKey, waiter.parentKey, response, error, a == N - 1, false);
        }
        waiters.clear();
    }

    private void requestReferenceFromServer(Object parentObject, String locationKey, String parentKey, Object[] args) {
        if (parentObject instanceof TL_stories.StoryItem) {
            TL_stories.StoryItem storyItem = (TL_stories.StoryItem) parentObject;
            TL_stories.TL_stories_getStoriesByID req = new TL_stories.TL_stories_getStoriesByID();
            req.peer = getMessagesController().getInputPeer(storyItem.dialogId);
            req.id.add(storyItem.id);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                onRequestComplete(locationKey, parentKey, response, error, true, false);
            });
        } else if (parentObject instanceof TLRPC.TL_help_premiumPromo) {
            TLRPC.TL_help_getPremiumPromo req = new TLRPC.TL_help_getPremiumPromo();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                int date = (int) (System.currentTimeMillis() / 1000);
                if (response instanceof TLRPC.TL_help_premiumPromo) {
                    TLRPC.TL_help_premiumPromo r = (TLRPC.TL_help_premiumPromo) response;
                    getMediaDataController().processLoadedPremiumPromo(r, date, false);
                }

                onRequestComplete(locationKey, parentKey, response, error, true, false);
            });
        } else if (parentObject instanceof TLRPC.TL_availableReaction) {
            TLRPC.TL_messages_getAvailableReactions req = new TLRPC.TL_messages_getAvailableReactions();
            req.hash = 0;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.BotInfo) {
            TLRPC.BotInfo botInfo = (TLRPC.BotInfo) parentObject;
            TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
            req.id = getMessagesController().getInputUser(botInfo.user_id);
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.TL_attachMenuBot) {
            TLRPC.TL_attachMenuBot bot = (TLRPC.TL_attachMenuBot) parentObject;
            TLRPC.TL_messages_getAttachMenuBot req = new TLRPC.TL_messages_getAttachMenuBot();
            req.bot = getMessagesController().getInputUser(bot.bot_id);
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof MessageObject) {
            MessageObject messageObject = (MessageObject) parentObject;
            long channelId = messageObject.getChannelId();
            if (messageObject.scheduled) {
                TLRPC.TL_messages_getScheduledMessages req = new TLRPC.TL_messages_getScheduledMessages();
                req.peer = getMessagesController().getInputPeer(messageObject.getDialogId());
                req.id.add(messageObject.getRealId());
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            } else if (messageObject.isQuickReply()) {
                TLRPC.TL_messages_getQuickReplyMessages req = new TLRPC.TL_messages_getQuickReplyMessages();
                req.shortcut_id = messageObject.getQuickReplyId();
                req.flags |= 1;
                req.id.add(messageObject.getRealId());
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            } else if (channelId != 0) {
                TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                req.channel = getMessagesController().getInputChannel(channelId);
                req.id.add(messageObject.getRealId());
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            } else {
                TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                req.id.add(messageObject.getRealId());
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            }
        } else if (parentObject instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) parentObject;
            TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
            TLRPC.TL_inputWallPaper inputWallPaper = new TLRPC.TL_inputWallPaper();
            inputWallPaper.id = wallPaper.id;
            inputWallPaper.access_hash = wallPaper.access_hash;
            req.wallpaper = inputWallPaper;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.TL_theme) {
            TLRPC.TL_theme theme = (TLRPC.TL_theme) parentObject;
            TLRPC.TL_account_getTheme req = new TLRPC.TL_account_getTheme();
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = theme.id;
            inputTheme.access_hash = theme.access_hash;
            req.theme = inputTheme;
            req.format = "android";
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.WebPage) {
            TLRPC.WebPage webPage = (TLRPC.WebPage) parentObject;
            TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
            req.url = webPage.url;
            req.hash = 0;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) parentObject;
            TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
            req.id.add(getMessagesController().getInputUser(user));
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) parentObject;
            if (chat instanceof TLRPC.TL_chat) {
                TLRPC.TL_messages_getChats req = new TLRPC.TL_messages_getChats();
                req.id.add(chat.id);
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            } else if (chat instanceof TLRPC.TL_channel) {
                TLRPC.TL_channels_getChannels req = new TLRPC.TL_channels_getChannels();
                req.id.add(MessagesController.getInputChannel(chat));
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            }
        } else if (parentObject instanceof String) {
            String string = (String) parentObject;
            if ("wallpaper".equals(string)) {
                if (wallpaperWaiters.isEmpty()) {
                    TLRPC.TL_account_getWallPapers req = new TLRPC.TL_account_getWallPapers();
                    getConnectionsManager().sendRequest(req, (response, error) -> broadcastWaitersData(wallpaperWaiters, response, error));
                }
                wallpaperWaiters.add(new Waiter(locationKey, parentKey));
            } else if (string.startsWith("gif")) {
                if (savedGifsWaiters.isEmpty()) {
                    TLRPC.TL_messages_getSavedGifs req = new TLRPC.TL_messages_getSavedGifs();
                    getConnectionsManager().sendRequest(req, (response, error) -> broadcastWaitersData(savedGifsWaiters, response, error));
                }
                savedGifsWaiters.add(new Waiter(locationKey, parentKey));
            } else if ("recent".equals(string)) {
                if (recentStickersWaiter.isEmpty()) {
                    TLRPC.TL_messages_getRecentStickers req = new TLRPC.TL_messages_getRecentStickers();
                    getConnectionsManager().sendRequest(req, (response, error) -> broadcastWaitersData(recentStickersWaiter, response, error));
                }
                recentStickersWaiter.add(new Waiter(locationKey, parentKey));
            } else if ("fav".equals(string)) {
                if (favStickersWaiter.isEmpty()) {
                    TLRPC.TL_messages_getFavedStickers req = new TLRPC.TL_messages_getFavedStickers();
                    getConnectionsManager().sendRequest(req, (response, error) -> broadcastWaitersData(favStickersWaiter, response, error));
                }
                favStickersWaiter.add(new Waiter(locationKey, parentKey));
            } else if ("update".equals(string)) {
                TLRPC.TL_help_getAppUpdate req = new TLRPC.TL_help_getAppUpdate();
                try {
                    req.source = ApplicationLoader.applicationContext.getPackageManager().getInstallerPackageName(ApplicationLoader.applicationContext.getPackageName());
                } catch (Exception ignore) {

                }
                if (req.source == null) {
                    req.source = "";
                }
                getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
            } else if (string.startsWith("avatar_")) {
                long id = Utilities.parseLong(string);
                if (id > 0) {
                    TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
                    req.limit = 80;
                    req.offset = 0;
                    req.max_id = 0;
                    req.user_id = getMessagesController().getInputUser(id);
                    getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
                } else {
                    TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                    req.filter = new TLRPC.TL_inputMessagesFilterChatPhotos();
                    req.limit = 80;
                    req.offset_id = 0;
                    req.q = "";
                    req.peer = getMessagesController().getInputPeer(id);
                    getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
                }
            } else if (string.startsWith("sent_")) {
                String[] params = string.split("_");
                if (params.length >= 3) {
                    long channelId = Utilities.parseLong(params[1]);
                    if (channelId != 0) {
                        TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                        req.channel = getMessagesController().getInputChannel(channelId);
                        req.id.add(Utilities.parseInt(params[2]));
                        getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, false, false));
                    } else {
                        TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                        req.id.add(Utilities.parseInt(params[2]));
                        getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, false, false));
                    }
                } else {
                    sendErrorToObject(args, 0);
                }
            } else {
                sendErrorToObject(args, 0);
            }
        } else if (parentObject instanceof TLRPC.TL_messages_stickerSet) {
            TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) parentObject;
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = stickerSet.set.id;
            req.stickerset.access_hash = stickerSet.set.access_hash;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.StickerSetCovered) {
            TLRPC.StickerSetCovered stickerSet = (TLRPC.StickerSetCovered) parentObject;
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = stickerSet.set.id;
            req.stickerset.access_hash = stickerSet.set.access_hash;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else if (parentObject instanceof TLRPC.InputStickerSet) {
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = (TLRPC.InputStickerSet) parentObject;
            getConnectionsManager().sendRequest(req, (response, error) -> onRequestComplete(locationKey, parentKey, response, error, true, false));
        } else {
            sendErrorToObject(args, 0);
        }
    }

    private boolean isSameReference(byte[] oldRef, byte[] newRef) {
        return Arrays.equals(oldRef, newRef);
    }

    @SuppressWarnings("unchecked")
    private boolean onUpdateObjectReference(Requester requester, byte[] file_reference, TLRPC.InputFileLocation locationReplacement, boolean fromCache) {
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("fileref updated for " + requester.args[0] + " " + requester.locationKey);
        }
        if (requester.args[0] instanceof TL_stories.TL_storyItem) {
            TL_stories.TL_storyItem storyItem = (TL_stories.TL_storyItem) requester.args[0];
            storyItem.media.document.file_reference = file_reference;
            return true;
        } else if (requester.args[0] instanceof TLRPC.TL_inputSingleMedia) {
            TLRPC.TL_messages_sendMultiMedia multiMedia = (TLRPC.TL_messages_sendMultiMedia) requester.args[1];
            Object[] objects = multiMediaCache.get(multiMedia);
            if (objects == null) {
                return true;
            }

            TLRPC.TL_inputSingleMedia req = (TLRPC.TL_inputSingleMedia) requester.args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                if (fromCache && isSameReference(mediaDocument.id.file_reference, file_reference)) {
                    return false;
                }
                mediaDocument.id.file_reference = file_reference;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                if (fromCache && isSameReference(mediaPhoto.id.file_reference, file_reference)) {
                    return false;
                }
                mediaPhoto.id.file_reference = file_reference;
            }

            int index = multiMedia.multi_media.indexOf(req);
            if (index < 0) {
                return true;
            }
            ArrayList<Object> parentObjects = (ArrayList<Object>) objects[3];
            parentObjects.set(index, null);

            boolean done = true;
            for (int a = 0, size; a < parentObjects.size(); a++) {
                if (parentObjects.get(a) != null) {
                    done = false;
                }
            }
            if (done) {
                multiMediaCache.remove(multiMedia);
                AndroidUtilities.runOnUIThread(() -> getSendMessagesHelper().performSendMessageRequestMulti(multiMedia, (ArrayList<MessageObject>) objects[1], (ArrayList<String>) objects[2], null, (SendMessagesHelper.DelayedMessage) objects[4], (Boolean) objects[5]));
            }
        } else if (requester.args[0] instanceof TLRPC.TL_messages_sendMedia) {
            TLRPC.TL_messages_sendMedia req = (TLRPC.TL_messages_sendMedia) requester.args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                if (fromCache && isSameReference(mediaDocument.id.file_reference, file_reference)) {
                    return false;
                }
                mediaDocument.id.file_reference = file_reference;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                if (fromCache && isSameReference(mediaPhoto.id.file_reference, file_reference)) {
                    return false;
                }
                mediaPhoto.id.file_reference = file_reference;
            }
            AndroidUtilities.runOnUIThread(() -> getSendMessagesHelper().performSendMessageRequest((TLObject) requester.args[0], (MessageObject) requester.args[1], (String) requester.args[2], (SendMessagesHelper.DelayedMessage) requester.args[3], (Boolean) requester.args[4], (SendMessagesHelper.DelayedMessage) requester.args[5], null, null, (Boolean) requester.args[6]));
        } else if (requester.args[0] instanceof TLRPC.TL_messages_editMessage) {
            TLRPC.TL_messages_editMessage req = (TLRPC.TL_messages_editMessage) requester.args[0];
            if (req.media instanceof TLRPC.TL_inputMediaDocument) {
                TLRPC.TL_inputMediaDocument mediaDocument = (TLRPC.TL_inputMediaDocument) req.media;
                if (fromCache && isSameReference(mediaDocument.id.file_reference, file_reference)) {
                    return false;
                }
                mediaDocument.id.file_reference = file_reference;
            } else if (req.media instanceof TLRPC.TL_inputMediaPhoto) {
                TLRPC.TL_inputMediaPhoto mediaPhoto = (TLRPC.TL_inputMediaPhoto) req.media;
                if (fromCache && isSameReference(mediaPhoto.id.file_reference, file_reference)) {
                    return false;
                }
                mediaPhoto.id.file_reference = file_reference;
            }
            AndroidUtilities.runOnUIThread(() -> getSendMessagesHelper().performSendMessageRequest((TLObject) requester.args[0], (MessageObject) requester.args[1], (String) requester.args[2], (SendMessagesHelper.DelayedMessage) requester.args[3], (Boolean) requester.args[4], (SendMessagesHelper.DelayedMessage) requester.args[5], null, null, (Boolean) requester.args[6]));
        } else if (requester.args[0] instanceof TLRPC.TL_messages_saveGif) {
            TLRPC.TL_messages_saveGif req = (TLRPC.TL_messages_saveGif) requester.args[0];
            if (fromCache && isSameReference(req.id.file_reference, file_reference)) {
                return false;
            }
            req.id.file_reference = file_reference;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else if (requester.args[0] instanceof TLRPC.TL_messages_saveRecentSticker) {
            TLRPC.TL_messages_saveRecentSticker req = (TLRPC.TL_messages_saveRecentSticker) requester.args[0];
            if (fromCache && isSameReference(req.id.file_reference, file_reference)) {
                return false;
            }
            req.id.file_reference = file_reference;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else if (requester.args[0] instanceof TLRPC.TL_stickers_addStickerToSet) {
            TLRPC.TL_stickers_addStickerToSet req = (TLRPC.TL_stickers_addStickerToSet) requester.args[0];
            if (fromCache && isSameReference(req.sticker.document.file_reference, file_reference)) {
                return false;
            }
            req.sticker.document.file_reference = file_reference;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else if (requester.args[0] instanceof TLRPC.TL_messages_faveSticker) {
            TLRPC.TL_messages_faveSticker req = (TLRPC.TL_messages_faveSticker) requester.args[0];
            if (fromCache && isSameReference(req.id.file_reference, file_reference)) {
                return false;
            }
            req.id.file_reference = file_reference;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else if (requester.args[0] instanceof TLRPC.TL_messages_getAttachedStickers) {
            TLRPC.TL_messages_getAttachedStickers req = (TLRPC.TL_messages_getAttachedStickers) requester.args[0];
            if (req.media instanceof TLRPC.TL_inputStickeredMediaDocument) {
                TLRPC.TL_inputStickeredMediaDocument mediaDocument = (TLRPC.TL_inputStickeredMediaDocument) req.media;
                if (fromCache && isSameReference(mediaDocument.id.file_reference, file_reference)) {
                    return false;
                }
                mediaDocument.id.file_reference = file_reference;
            } else if (req.media instanceof TLRPC.TL_inputStickeredMediaPhoto) {
                TLRPC.TL_inputStickeredMediaPhoto mediaPhoto = (TLRPC.TL_inputStickeredMediaPhoto) req.media;
                if (fromCache && isSameReference(mediaPhoto.id.file_reference, file_reference)) {
                    return false;
                }
                mediaPhoto.id.file_reference = file_reference;
            }
            getConnectionsManager().sendRequest(req, (RequestDelegate) requester.args[1]);
        } else if (requester.args[1] instanceof FileLoadOperation) {
            FileLoadOperation fileLoadOperation = (FileLoadOperation) requester.args[1];
            String oldRef = null;
            String newRef = null;
            if (locationReplacement != null) {
                if (fromCache && isSameReference(fileLoadOperation.location.file_reference, locationReplacement.file_reference)) {
                    return false;
                }
                if (BuildVars.LOGS_ENABLED) {
                    oldRef = Utilities.bytesToHex(fileLoadOperation.location.file_reference);
                }
                fileLoadOperation.location = locationReplacement;
                if (BuildVars.LOGS_ENABLED) {
                    newRef = Utilities.bytesToHex(fileLoadOperation.location.file_reference);
                }
            } else {
                if (fromCache && isSameReference(requester.location.file_reference, file_reference)) {
                    return false;
                }
                if (BuildVars.LOGS_ENABLED) {
                    oldRef = Utilities.bytesToHex(fileLoadOperation.location.file_reference);
                }
                fileLoadOperation.location.file_reference = requester.location.file_reference = file_reference;
                if (BuildVars.LOGS_ENABLED) {
                    newRef = Utilities.bytesToHex(fileLoadOperation.location.file_reference);
                }
            }
            fileLoadOperation.requestingReference = false;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("debug_loading: " + fileLoadOperation.getCacheFileFinal().getName() + " " + oldRef + " " + newRef + " reference updated resume download");
            }
            fileLoadOperation.startDownloadRequest(-1);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void sendErrorToObject(Object[] args, int reason) {
        if (args[0] instanceof TLRPC.TL_inputSingleMedia) {
            TLRPC.TL_messages_sendMultiMedia req = (TLRPC.TL_messages_sendMultiMedia) args[1];
            Object[] objects = multiMediaCache.get(req);
            if (objects != null) {
                multiMediaCache.remove(req);
                AndroidUtilities.runOnUIThread(() -> getSendMessagesHelper().performSendMessageRequestMulti(req, (ArrayList<MessageObject>) objects[1], (ArrayList<String>) objects[2], null, (SendMessagesHelper.DelayedMessage) objects[4], (Boolean) objects[5]));
            }
        } else if (args[0] instanceof TLRPC.TL_messages_sendMedia || args[0] instanceof TLRPC.TL_messages_editMessage) {
            AndroidUtilities.runOnUIThread(() -> getSendMessagesHelper().performSendMessageRequest((TLObject) args[0], (MessageObject) args[1], (String) args[2], (SendMessagesHelper.DelayedMessage) args[3], (Boolean) args[4], (SendMessagesHelper.DelayedMessage) args[5], null, null, (Boolean) args[6]));
        } else if (args[0] instanceof TLRPC.TL_messages_saveGif) {
            TLRPC.TL_messages_saveGif req = (TLRPC.TL_messages_saveGif) args[0];
            //do nothing
        } else if (args[0] instanceof TLRPC.TL_messages_saveRecentSticker) {
            TLRPC.TL_messages_saveRecentSticker req = (TLRPC.TL_messages_saveRecentSticker) args[0];
            //do nothing
        } else if (args[0] instanceof TLRPC.TL_stickers_addStickerToSet) {
            TLRPC.TL_stickers_addStickerToSet req = (TLRPC.TL_stickers_addStickerToSet) args[0];
            //do nothing
        } else if (args[0] instanceof TLRPC.TL_messages_faveSticker) {
            TLRPC.TL_messages_faveSticker req = (TLRPC.TL_messages_faveSticker) args[0];
            //do nothing
        } else if (args[0] instanceof TLRPC.TL_messages_getAttachedStickers) {
            TLRPC.TL_messages_getAttachedStickers req = (TLRPC.TL_messages_getAttachedStickers) args[0];
            getConnectionsManager().sendRequest(req, (RequestDelegate) args[1]);
        } else {
            if (args[1] instanceof FileLoadOperation) {
                FileLoadOperation fileLoadOperation = (FileLoadOperation) args[1];
                fileLoadOperation.requestingReference = false;
                FileLog.e("debug_loading: " + fileLoadOperation.getCacheFileFinal().getName() + " reference can't update: fail operation ");
                fileLoadOperation.onFail(false, 0);
            }
        }
    }

    private boolean onRequestComplete(String locationKey, String parentKey, TLObject response, TLRPC.TL_error error, boolean cache, boolean fromCache) {
        boolean found = false;
        String cacheKey = parentKey;
        if (response instanceof TLRPC.TL_help_premiumPromo) {
            cacheKey = "premium_promo";
        } else if (response instanceof TLRPC.TL_account_wallPapers) {
            cacheKey = "wallpaper";
        } else if (response instanceof TLRPC.TL_messages_savedGifs) {
            cacheKey = "gif";
        } else if (response instanceof TLRPC.TL_messages_recentStickers) {
            cacheKey = "recent";
        } else if (response instanceof TLRPC.TL_messages_favedStickers) {
            cacheKey = "fav";
        }
        if (parentKey != null) {
            ArrayList<Requester> arrayList = parentRequester.get(parentKey);
            if (arrayList != null) {
                for (int q = 0, N = arrayList.size(); q < N; q++) {
                    Requester requester = arrayList.get(q);
                    if (requester.completed) {
                        continue;
                    }
                    if (onRequestComplete(requester.locationKey, null, response, error, cache && !found, fromCache)) {
                        found = true;
                    }
                }
                if (found) {
                    putReponseToCache(cacheKey, response);
                }
                parentRequester.remove(parentKey);
            }
        }
        byte[] result = null;
        TLRPC.InputFileLocation[] locationReplacement = null;
        boolean[] needReplacement = null;
        ArrayList<Requester> arrayList = locationRequester.get(locationKey);
        if (arrayList == null) {
            return found;
        }
        cacheKey = locationKey;
        for (int q = 0, N = arrayList.size(); q < N; q++) {
            Requester requester = arrayList.get(q);
            if (requester.completed) {
                continue;
            }
            if (error != null && BuildVars.LOGS_ENABLED) {
                if (requester.args.length > 1 && requester.args[1] instanceof FileLoadOperation) {
                    FileLoadOperation operation = (FileLoadOperation) requester.args[1];
                    FileLog.e("debug_loading: " + operation.getCacheFileFinal().getName() + " can't update file reference: " + error.code + " " + error.text);
                }
            }
            if (requester.location instanceof TLRPC.TL_inputFileLocation || requester.location instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
                locationReplacement = new TLRPC.InputFileLocation[1];
                needReplacement = new boolean[1];
            }
            requester.completed = true;
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                if (!res.messages.isEmpty()) {
                    for (int i = 0, size3 = res.messages.size(); i < size3; i++) {
                        TLRPC.Message message = res.messages.get(i);
                        if (message.media != null) {
                            if (message.media.document != null) {
                                result = getFileReference(message.media.document, requester.location, needReplacement, locationReplacement);
                            } else if (message.media.game != null) {
                                result = getFileReference(message.media.game.document, requester.location, needReplacement, locationReplacement);
                                if (result == null) {
                                    result = getFileReference(message.media.game.photo, requester.location, needReplacement, locationReplacement);
                                }
                            } else if (message.media.photo != null) {
                                result = getFileReference(message.media.photo, requester.location, needReplacement, locationReplacement);
                            } else if (message.media.webpage != null) {
                                result = getFileReference(message.media.webpage, requester.location, needReplacement, locationReplacement);
                            }
                        } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
                            result = getFileReference(message.action.photo, requester.location, needReplacement, locationReplacement);
                        }
                        if (result != null) {
                            if (cache) {
                                getMessagesStorage().replaceMessageIfExists(message, res.users, res.chats, false);
                            }
                            break;
                        }
                    }
                    if (result == null) {
                        getMessagesStorage().replaceMessageIfExists(res.messages.get(0), res.users, res.chats, true);
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("file ref not found in messages, replacing message");
                        }
                    }
                } else {
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("empty messages, file ref not found");
                    }
                }
            } else if (response instanceof TLRPC.TL_help_premiumPromo) {
                TLRPC.TL_help_premiumPromo premiumPromo = (TLRPC.TL_help_premiumPromo) response;
                for (TLRPC.Document document : premiumPromo.videos) {
                    result = getFileReference(document, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
            } else if (response instanceof TLRPC.TL_messages_availableReactions) {
                TLRPC.TL_messages_availableReactions availableReactions = (TLRPC.TL_messages_availableReactions) response;
                getMediaDataController().processLoadedReactions(availableReactions.reactions, availableReactions.hash, (int) (System.currentTimeMillis() / 1000), false);

                for (TLRPC.TL_availableReaction reaction : availableReactions.reactions) {
                    result = getFileReference(reaction.static_icon, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.appear_animation, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.select_animation, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.activate_animation, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.effect_animation, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.around_animation, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                    result = getFileReference(reaction.center_icon, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
            } else if (response instanceof TLRPC.TL_users_userFull) {
                TLRPC.TL_users_userFull usersFull = (TLRPC.TL_users_userFull) response;
                getMessagesController().putUsers(usersFull.users, false);
                getMessagesController().putChats(usersFull.chats, false);
                TLRPC.UserFull userFull = usersFull.full_user;
                TLRPC.BotInfo botInfo = userFull.bot_info;
                if (botInfo != null) {
                    getMessagesStorage().updateUserInfo(userFull, true);

                    result = getFileReference(botInfo.description_document, requester.location, needReplacement, locationReplacement);

                    if (result != null) {
                        continue;
                    }

                    result = getFileReference(botInfo.description_photo, requester.location, needReplacement, locationReplacement);
                }
            } else if (response instanceof TLRPC.TL_attachMenuBotsBot) {
                TLRPC.TL_attachMenuBot bot = ((TLRPC.TL_attachMenuBotsBot) response).bot;
                for (TLRPC.TL_attachMenuBotIcon icon : bot.icons) {
                    result = getFileReference(icon.icon, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
                if (cache) {
                    TLRPC.TL_attachMenuBots bots = getMediaDataController().getAttachMenuBots();
                    ArrayList<TLRPC.TL_attachMenuBot> newBotsList = new ArrayList<>(bots.bots);
                    for (int i = 0; i < newBotsList.size(); i++) {
                        TLRPC.TL_attachMenuBot wasBot = newBotsList.get(i);
                        if (wasBot.bot_id == bot.bot_id) {
                            newBotsList.set(i, bot);
                            break;
                        }
                    }
                    bots.bots = newBotsList;
                    getMediaDataController().processLoadedMenuBots(bots, bots.hash, (int) (System.currentTimeMillis() / 1000), false);
                }
            } else if (response instanceof TLRPC.TL_help_appUpdate) {
                TLRPC.TL_help_appUpdate appUpdate = (TLRPC.TL_help_appUpdate) response;
                try {
                    SharedConfig.pendingAppUpdate = appUpdate;
                    SharedConfig.saveConfig();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    if (appUpdate.document != null) {
                        result = appUpdate.document.file_reference;
                        TLRPC.TL_inputDocumentFileLocation location = new TLRPC.TL_inputDocumentFileLocation();
                        location.id = appUpdate.document.id;
                        location.access_hash = appUpdate.document.access_hash;
                        location.file_reference = appUpdate.document.file_reference;
                        location.thumb_size = "";
                        locationReplacement = new TLRPC.InputFileLocation[1];
                        locationReplacement[0] = location;
                    }
                } catch (Exception e) {
                    result = null;
                    FileLog.e(e);
                }
                if (result == null) {
                    result = getFileReference(appUpdate.document, requester.location, needReplacement, locationReplacement);
                }
                if (result == null) {
                    result = getFileReference(appUpdate.sticker, requester.location, needReplacement, locationReplacement);
                }
            } else if (response instanceof TLRPC.TL_messages_webPage) {
                TLRPC.TL_messages_webPage res = (TLRPC.TL_messages_webPage) response;
                getMessagesController().putChats(res.chats, false);
                getMessagesController().putUsers(res.users, false);
                result = getFileReference(res.webpage, requester.location, needReplacement, locationReplacement);
            } else if (response instanceof TLRPC.WebPage) {
                result = getFileReference((TLRPC.WebPage) response, requester.location, needReplacement, locationReplacement);
            } else if (response instanceof TLRPC.TL_account_wallPapers) {
                TLRPC.TL_account_wallPapers accountWallPapers = (TLRPC.TL_account_wallPapers) response;
                for (int i = 0, size10 = accountWallPapers.wallpapers.size(); i < size10; i++) {
                    result = getFileReference(((TLRPC.WallPaper) accountWallPapers.wallpapers.get(i)).document, requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
                if (result != null && cache) {
                    getMessagesStorage().putWallpapers(accountWallPapers.wallpapers, 1);
                }
            } else if (response instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) response;
                result = getFileReference(wallPaper.document, requester.location, needReplacement, locationReplacement);
                if (result != null && cache) {
                    ArrayList<TLRPC.WallPaper> wallpapers = new ArrayList<>();
                    wallpapers.add(wallPaper);
                    getMessagesStorage().putWallpapers(wallpapers, 0);
                }
            } else if (response instanceof TLRPC.TL_theme) {
                TLRPC.TL_theme theme = (TLRPC.TL_theme) response;
                result = getFileReference(theme.document, requester.location, needReplacement, locationReplacement);
                if (result != null && cache) {
                    AndroidUtilities.runOnUIThread(() -> Theme.setThemeFileReference(theme));
                }
            } else if (response instanceof TLRPC.Vector) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                if (!vector.objects.isEmpty()) {
                    for (int i = 0, size10 = vector.objects.size(); i < size10; i++) {
                        Object object = vector.objects.get(i);
                        if (object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            result = getFileReference(user, requester.location, needReplacement, locationReplacement);
                            if (cache && result != null) {
                                ArrayList<TLRPC.User> arrayList1 = new ArrayList<>();
                                arrayList1.add(user);
                                getMessagesStorage().putUsersAndChats(arrayList1, null, true, true);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().putUser(user, false));
                            }
                        } else if (object instanceof TLRPC.Chat) {
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            result = getFileReference(chat, requester.location, needReplacement, locationReplacement);
                            if (cache && result != null) {
                                ArrayList<TLRPC.Chat> arrayList1 = new ArrayList<>();
                                arrayList1.add(chat);
                                getMessagesStorage().putUsersAndChats(null, arrayList1, true, true);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().putChat(chat, false));
                            }
                        }
                        if (result != null) {
                            break;
                        }
                    }
                }
            } else if (response instanceof TLRPC.TL_messages_chats) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                if (!res.chats.isEmpty()) {
                    for (int i = 0, size10 = res.chats.size(); i < size10; i++) {
                        TLRPC.Chat chat = res.chats.get(i);
                        result = getFileReference(chat, requester.location, needReplacement, locationReplacement);
                        if (result != null) {
                            if (cache) {
                                ArrayList<TLRPC.Chat> arrayList1 = new ArrayList<>();
                                arrayList1.add(chat);
                                getMessagesStorage().putUsersAndChats(null, arrayList1, true, true);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().putChat(chat, false));
                            }
                            break;
                        }
                    }
                }
            } else if (response instanceof TLRPC.TL_messages_savedGifs) {
                TLRPC.TL_messages_savedGifs savedGifs = (TLRPC.TL_messages_savedGifs) response;
                for (int b = 0, size2 = savedGifs.gifs.size(); b < size2; b++) {
                    result = getFileReference(savedGifs.gifs.get(b), requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
                if (cache) {
                    getMediaDataController().processLoadedRecentDocuments(MediaDataController.TYPE_IMAGE, savedGifs.gifs, true, 0, true);
                }
            } else if (response instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) response;
                if (result == null) {
                    for (int b = 0, size2 = stickerSet.documents.size(); b < size2; b++) {
                        result = getFileReference(stickerSet.documents.get(b), requester.location, needReplacement, locationReplacement);
                        if (result != null) {
                            break;
                        }
                    }
                }
                if (cache) {
                    AndroidUtilities.runOnUIThread(() -> getMediaDataController().replaceStickerSet(stickerSet));
                }
            } else if (response instanceof TLRPC.TL_messages_recentStickers) {
                TLRPC.TL_messages_recentStickers recentStickers = (TLRPC.TL_messages_recentStickers) response;
                for (int b = 0, size2 = recentStickers.stickers.size(); b < size2; b++) {
                    result = getFileReference(recentStickers.stickers.get(b), requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
                if (cache) {
                    getMediaDataController().processLoadedRecentDocuments(MediaDataController.TYPE_IMAGE, recentStickers.stickers, false, 0, true);
                }
            } else if (response instanceof TLRPC.TL_messages_favedStickers) {
                TLRPC.TL_messages_favedStickers favedStickers = (TLRPC.TL_messages_favedStickers) response;
                for (int b = 0, size2 = favedStickers.stickers.size(); b < size2; b++) {
                    result = getFileReference(favedStickers.stickers.get(b), requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
                if (cache) {
                    getMediaDataController().processLoadedRecentDocuments(MediaDataController.TYPE_FAVE, favedStickers.stickers, false, 0, true);
                }
            } else if (response instanceof TLRPC.photos_Photos) {
                TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                for (int b = 0, size = res.photos.size(); b < size; b++) {
                    result = getFileReference(res.photos.get(b), requester.location, needReplacement, locationReplacement);
                    if (result != null) {
                        break;
                    }
                }
            } else if (response instanceof TL_stories.TL_stories_stories) {
                TL_stories.TL_stories_stories stories = (TL_stories.TL_stories_stories) response;
                TL_stories.StoryItem newStoryItem = null;
                if (!stories.stories.isEmpty()) {
                    TL_stories.StoryItem storyItem = stories.stories.get(0);
                    if (storyItem.media != null) {
                        newStoryItem = storyItem;
                        if (result == null && storyItem.media.photo != null) {
                            result = getFileReference(storyItem.media.photo, requester.location, needReplacement, locationReplacement);
                        }
                        if (result == null && storyItem.media.document != null) {
                            result = getFileReference(storyItem.media.document, requester.location, needReplacement, locationReplacement);
                        }
                        if (result == null && storyItem.media.alt_document != null) {
                            result = getFileReference(storyItem.media.alt_document, requester.location, needReplacement, locationReplacement);
                        }
                    }
                }
                Object arg = requester.args[1];
                if (arg instanceof FileLoadOperation) {
                    FileLoadOperation operation = (FileLoadOperation) requester.args[1];
                    if (operation.parentObject instanceof TL_stories.StoryItem) {
                        TL_stories.StoryItem storyItem = (TL_stories.StoryItem) operation.parentObject;
                        if (newStoryItem == null) {
                            TL_stories.TL_updateStory story = new TL_stories.TL_updateStory();
                            story.peer = getMessagesController().getPeer(storyItem.dialogId);
                            story.story = new TL_stories.TL_storyItemDeleted();
                            story.story.id = storyItem.id;
                            ArrayList<TLRPC.Update> updates = new ArrayList<>();
                            updates.add(story);
                            getMessagesController().processUpdateArray(updates, null, null, false, 0);
                        } else {
                            TLRPC.User user = getMessagesController().getUser(storyItem.dialogId);
                            if (user != null && user.contact) {
                                MessagesController.getInstance(currentAccount).getStoriesController().getStoriesStorage().updateStoryItem(storyItem.dialogId, newStoryItem);
                            }
                        }
                        if (newStoryItem != null && result == null) {
                            TL_stories.TL_updateStory updateStory = new TL_stories.TL_updateStory();
                            updateStory.peer = MessagesController.getInstance(currentAccount).getPeer(storyItem.dialogId);
                            updateStory.story = newStoryItem;
                            ArrayList<TLRPC.Update> updates = new ArrayList<>();
                            updates.add(updateStory);
                            MessagesController.getInstance(currentAccount).processUpdateArray(updates, null, null, false, 0);
                        }
                    }
                }
            }
            if (result != null) {
                if (onUpdateObjectReference(requester, result, locationReplacement != null ? locationReplacement[0] : null, fromCache)) {
                    found = true;
                }
            } else {
                sendErrorToObject(requester.args, 1);
            }
        }
        locationRequester.remove(locationKey);
        if (found) {
            putReponseToCache(cacheKey, response);
        }
        return found;
    }

    private void cleanupCache() {
        if (Math.abs(SystemClock.elapsedRealtime() - lastCleanupTime) < 60 * 10 * 1000) {
            return;
        }
        lastCleanupTime = SystemClock.elapsedRealtime();

        ArrayList<String> keysToDelete = null;
        for (HashMap.Entry<String, CachedResult> entry : responseCache.entrySet()) {
            CachedResult cachedResult = entry.getValue();
            if (Math.abs(System.currentTimeMillis() - cachedResult.firstQueryTime) >= 60  * 1000) {
                if (keysToDelete == null) {
                    keysToDelete = new ArrayList<>();
                }
                keysToDelete.add(entry.getKey());
            }
        }
        if (keysToDelete != null) {
            for (int a = 0, size = keysToDelete.size(); a < size; a++) {
                responseCache.remove(keysToDelete.get(a));
            }
        }
    }

    private CachedResult getCachedResponse(String key) {
        CachedResult cachedResult = responseCache.get(key);
        if (cachedResult != null && Math.abs(System.currentTimeMillis() - cachedResult.firstQueryTime) >= 60 * 1000) {
            responseCache.remove(key);
            cachedResult = null;
        }
        return cachedResult;
    }

    private void putReponseToCache(String key, TLObject response) {
        CachedResult cachedResult = responseCache.get(key);
        if (cachedResult == null) {
            cachedResult = new CachedResult();
            cachedResult.response = response;
            cachedResult.firstQueryTime = System.currentTimeMillis();
            responseCache.put(key, cachedResult);
        }
    }

    private byte[] getFileReference(TLRPC.Document document, TLRPC.InputFileLocation location, boolean[] needReplacement, TLRPC.InputFileLocation[] replacement) {
        if (document == null || location == null) {
            return null;
        }
        if (location instanceof TLRPC.TL_inputDocumentFileLocation) {
            if (document.id == location.id) {
                return document.file_reference;
            }
        } else {
            for (int a = 0, size = document.thumbs.size(); a < size; a++) {
                TLRPC.PhotoSize photoSize = document.thumbs.get(a);
                byte[] result = getFileReference(photoSize, location, needReplacement);
                if (needReplacement != null && needReplacement[0]) {
                    replacement[0] = new TLRPC.TL_inputDocumentFileLocation();
                    replacement[0].id = document.id;
                    replacement[0].volume_id = location.volume_id;
                    replacement[0].local_id = location.local_id;
                    replacement[0].access_hash = document.access_hash;
                    replacement[0].file_reference = document.file_reference;
                    replacement[0].thumb_size = photoSize.type;
                    return document.file_reference;
                }
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private boolean getPeerReferenceReplacement(TLRPC.User user, TLRPC.Chat chat, boolean big, TLRPC.InputFileLocation location, TLRPC.InputFileLocation[] replacement, boolean[] needReplacement) {
        if (needReplacement != null && needReplacement[0]) {
            TLRPC.TL_inputPeerPhotoFileLocation inputPeerPhotoFileLocation = new TLRPC.TL_inputPeerPhotoFileLocation();
            inputPeerPhotoFileLocation.id = location.volume_id;
            inputPeerPhotoFileLocation.volume_id = location.volume_id;
            inputPeerPhotoFileLocation.local_id = location.local_id;
            inputPeerPhotoFileLocation.big = big;
            TLRPC.InputPeer peer;
            if (user != null) {
                TLRPC.TL_inputPeerUser inputPeerUser = new TLRPC.TL_inputPeerUser();
                inputPeerUser.user_id = user.id;
                inputPeerUser.access_hash = user.access_hash;
                inputPeerPhotoFileLocation.photo_id = user.photo.photo_id;
                peer = inputPeerUser;
            } else {
                if (ChatObject.isChannel(chat)) {
                    TLRPC.TL_inputPeerChannel inputPeerChannel = new TLRPC.TL_inputPeerChannel();
                    inputPeerChannel.channel_id = chat.id;
                    inputPeerChannel.access_hash = chat.access_hash;
                    peer = inputPeerChannel;
                } else {
                    TLRPC.TL_inputPeerChat inputPeerChat = new TLRPC.TL_inputPeerChat();
                    inputPeerChat.chat_id = chat.id;
                    peer = inputPeerChat;
                }
                inputPeerPhotoFileLocation.photo_id = chat.photo.photo_id;
            }
            inputPeerPhotoFileLocation.peer = peer;
            replacement[0] = inputPeerPhotoFileLocation;
            return true;
        }
        return false;
    }

    private byte[] getFileReference(TLRPC.User user, TLRPC.InputFileLocation location, boolean[] needReplacement, TLRPC.InputFileLocation[] replacement) {
        if (user == null || user.photo == null || !(location instanceof TLRPC.TL_inputFileLocation)) {
            return null;
        }
        byte[] result = getFileReference(user.photo.photo_small, location, needReplacement);
        if (getPeerReferenceReplacement(user, null, false, location, replacement, needReplacement)) {
            return new byte[0];
        }
        if (result == null) {
            result = getFileReference(user.photo.photo_big, location, needReplacement);
            if (getPeerReferenceReplacement(user, null, true, location, replacement, needReplacement)) {
                return new byte[0];
            }
        }
        return result;
    }

    private byte[] getFileReference(TLRPC.Chat chat, TLRPC.InputFileLocation location, boolean[] needReplacement, TLRPC.InputFileLocation[] replacement) {
        if (chat == null || chat.photo == null || !(location instanceof TLRPC.TL_inputFileLocation) && !(location instanceof TLRPC.TL_inputPeerPhotoFileLocation)) {
            return null;
        }
        if (location instanceof TLRPC.TL_inputPeerPhotoFileLocation) {
            needReplacement[0] = true;
            if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
                return new byte[0];
            }
            return null;
        } else {
            byte[] result = getFileReference(chat.photo.photo_small, location, needReplacement);
            if (getPeerReferenceReplacement(null, chat, false, location, replacement, needReplacement)) {
                return new byte[0];
            }
            if (result == null) {
                result = getFileReference(chat.photo.photo_big, location, needReplacement);
                if (getPeerReferenceReplacement(null, chat, true, location, replacement, needReplacement)) {
                    return new byte[0];
                }
            }
            return result;
        }
    }

    private byte[] getFileReference(TLRPC.Photo photo, TLRPC.InputFileLocation location, boolean[] needReplacement, TLRPC.InputFileLocation[] replacement) {
        if (photo == null) {
            return null;
        }
        if (location instanceof TLRPC.TL_inputPhotoFileLocation) {
            return photo.id == location.id ? photo.file_reference : null;
        } else if (location instanceof TLRPC.TL_inputFileLocation) {
            for (int a = 0, size = photo.sizes.size(); a < size; a++) {
                TLRPC.PhotoSize photoSize = photo.sizes.get(a);
                byte[] result = getFileReference(photoSize, location, needReplacement);
                if (needReplacement != null && needReplacement[0]) {
                    replacement[0] = new TLRPC.TL_inputPhotoFileLocation();
                    replacement[0].id = photo.id;
                    replacement[0].volume_id = location.volume_id;
                    replacement[0].local_id = location.local_id;
                    replacement[0].access_hash = photo.access_hash;
                    replacement[0].file_reference = photo.file_reference;
                    replacement[0].thumb_size = photoSize.type;
                    return photo.file_reference;
                }
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private byte[] getFileReference(TLRPC.PhotoSize photoSize, TLRPC.InputFileLocation location, boolean[] needReplacement) {
        if (photoSize == null || !(location instanceof TLRPC.TL_inputFileLocation)) {
            return null;
        }
        return getFileReference(photoSize.location, location, needReplacement);
    }

    private byte[] getFileReference(TLRPC.FileLocation fileLocation, TLRPC.InputFileLocation location, boolean[] needReplacement) {
        if (fileLocation == null || !(location instanceof TLRPC.TL_inputFileLocation)) {
            return null;
        }
        if (fileLocation.local_id == location.local_id && fileLocation.volume_id == location.volume_id) {
            if (fileLocation.file_reference == null && needReplacement != null) {
                needReplacement[0] = true;
            }
            return fileLocation.file_reference;
        }
        return null;
    }

    private byte[] getFileReference(TLRPC.WebPage webpage, TLRPC.InputFileLocation location, boolean[] needReplacement, TLRPC.InputFileLocation[] replacement) {
        byte[] result = getFileReference(webpage.document, location, needReplacement, replacement);
        if (result != null) {
            return result;
        }
        result = getFileReference(webpage.photo, location, needReplacement, replacement);
        if (result != null) {
            return result;
        }
        if (!webpage.attributes.isEmpty()) {
            for (int a = 0, size1 = webpage.attributes.size(); a < size1; a++) {
                TLRPC.WebPageAttribute attribute_ = webpage.attributes.get(a);
                if (!(attribute_ instanceof TLRPC.TL_webPageAttributeTheme)) {
                    continue;
                }
                TLRPC.TL_webPageAttributeTheme attribute = (TLRPC.TL_webPageAttributeTheme) attribute_;
                for (int b = 0, size2 = attribute.documents.size(); b < size2; b++) {
                    result = getFileReference(attribute.documents.get(b), location, needReplacement, replacement);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        if (webpage.cached_page != null) {
            for (int b = 0, size2 = webpage.cached_page.documents.size(); b < size2; b++) {
                result = getFileReference(webpage.cached_page.documents.get(b), location, needReplacement, replacement);
                if (result != null) {
                    return result;
                }
            }
            for (int b = 0, size2 = webpage.cached_page.photos.size(); b < size2; b++) {
                result = getFileReference(webpage.cached_page.photos.get(b), location, needReplacement, replacement);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static boolean isFileRefError(String error) {
        return "FILEREF_EXPIRED".equals(error) || "FILE_REFERENCE_EXPIRED".equals(error) || "FILE_REFERENCE_EMPTY".equals(error) || error != null && error.startsWith("FILE_REFERENCE_");
    }
}
