/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.QuickRepliesActivity;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Cells.BotSwitchCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.MentionCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;

public class MentionsAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private boolean allowStickers = true;
    private boolean allowBots = true;
    private boolean allowChats = true;

    public interface MentionsAdapterDelegate {
        void needChangePanelVisibility(boolean show);
        void onItemCountUpdate(int oldCount, int newCount);
        void onContextSearch(boolean searching);
        void onContextClick(TLRPC.BotInlineResult result);
    }

    private final boolean USE_DIVIDERS = false;

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private long dialog_id;
    private long threadMessageId;
    private TLRPC.ChatFull info;
    private SearchAdapterHelper searchAdapterHelper;
    private ArrayList<TLObject> searchResultUsernames;
    private LongSparseArray<TLObject> searchResultUsernamesMap;
    private Runnable searchGlobalRunnable;
    private ArrayList<String> searchResultHashtags;
    private ArrayList<String> searchResultCommands;
    private ArrayList<String> searchResultCommandsHelp;
    private String quickRepliesQuery;
    private ArrayList<QuickRepliesController.QuickReply> quickReplies;
    private ArrayList<MediaDataController.KeywordResult> searchResultSuggestions;
    private String[] lastSearchKeyboardLanguage;
    private ArrayList<TLRPC.User> searchResultCommandsUsers;
    private ArrayList<TLRPC.BotInlineResult> searchResultBotContext;
    private long searchResultBotContextSwitchUserId;
    private TLRPC.TL_inlineBotSwitchPM searchResultBotContextSwitch;
    private TLRPC.TL_inlineBotWebView searchResultBotWebViewSwitch;
    private MentionsAdapterDelegate delegate;
    private LongSparseArray<TL_bots.BotInfo> botInfo;
    private int resultStartPosition;
    private int resultLength;
    private String lastText;
    private boolean lastForSearch;
    private boolean lastUsernameOnly;
    private int lastPosition;
    private ArrayList<MessageObject> messages;
    private boolean needUsernames = true;
    private boolean needBotContext = true;
    private boolean isDarkTheme;
    private int botsCount;
    private boolean inlineMediaEnabled = true;
    private int channelLastReqId;
    private int channelReqId;
    private boolean isSearchingMentions;
    private TLRPC.User user;
    private TLRPC.Chat chat;

    private boolean searchInDailogs = false;

    private EmojiView.ChooseStickerActionTracker mentionsStickersActionTracker;

    private boolean visibleByStickersSearch;

    private final static String punctuationsChars = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n";

    private Runnable cancelDelayRunnable;

    private String searchingContextUsername;
    private String searchingContextQuery;
    private String nextQueryOffset;
    private int contextUsernameReqid;
    private int contextQueryReqid;
    private boolean noUserName;
    private TLRPC.User foundContextBot;
    private boolean contextMedia;
    private Runnable contextQueryRunnable;
    private Location lastKnownLocation;

    private ArrayList<StickerResult> stickers;
    private HashMap<String, TLRPC.Document> stickersMap;
    private ArrayList<String> stickersToLoad = new ArrayList<>();
    private String lastSticker;
    private int lastReqId;
    private boolean delayLocalResults;
    private Runnable checkAgainRunnable;

    private ChatActivity parentFragment;
    private final Theme.ResourcesProvider resourcesProvider;

    private static class StickerResult {
        public TLRPC.Document sticker;
        public Object parent;

        public StickerResult(TLRPC.Document s, Object p) {
            sticker = s;
            parent = p;
        }
    }

    private SendMessagesHelper.LocationProvider locationProvider = new SendMessagesHelper.LocationProvider(new SendMessagesHelper.LocationProvider.LocationProviderDelegate() {
        @Override
        public void onLocationAcquired(Location location) {
            if (foundContextBot != null && foundContextBot.bot_inline_geo) {
                lastKnownLocation = location;
                searchForContextBotResults(true, foundContextBot, searchingContextQuery, "");
            }
        }

        @Override
        public void onUnableLocationAcquire() {
            onLocationUnavailable();
        }
    }) {
        @Override
        public void stop() {
            super.stop();
            lastKnownLocation = null;
        }
    };

    public MentionsAdapter(Context context, boolean darkTheme, long did, long threadMessageId, MentionsAdapterDelegate mentionsAdapterDelegate, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        mContext = context;
        delegate = mentionsAdapterDelegate;
        isDarkTheme = darkTheme;
        dialog_id = did;
        this.threadMessageId = threadMessageId;
        searchAdapterHelper = new SearchAdapterHelper(true);
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int searchId) {
                notifyDataSetChanged();
            }

            @Override
            public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {
                if (lastText != null) {
                    searchUsernameOrHashtag(lastText, lastPosition, messages, lastUsernameOnly, lastForSearch);
                }
            }
        });
        if (!darkTheme) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadFailed);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
    }

    public TLRPC.User getFoundContextBot() {
        return foundContextBot;
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
            if (stickers != null && !stickers.isEmpty() && !stickersToLoad.isEmpty() && visibleByStickersSearch) {
                String fileName = (String) args[0];
                stickersToLoad.remove(fileName);
                if (stickersToLoad.isEmpty()) {
                    delegate.needChangePanelVisibility(getItemCountInternal() > 0);
                }
            }
        } else if (id == NotificationCenter.recentDocumentsDidLoad) {
            if (checkAgainRunnable != null) {
                AndroidUtilities.runOnUIThread(checkAgainRunnable);
                checkAgainRunnable = null;
            }
        } else if (id == NotificationCenter.stickersDidLoad) {
            if ((int) args[0] == MediaDataController.TYPE_IMAGE) {
                if (checkAgainRunnable != null) {
                    AndroidUtilities.runOnUIThread(checkAgainRunnable);
                    checkAgainRunnable = null;
                }
            }
        }
    }

    private void addStickerToResult(TLRPC.Document document, Object parent) {
        if (document == null) {
            return;
        }
        String key = document.dc_id + "_" + document.id;
        if (stickersMap != null && stickersMap.containsKey(key)) {
            return;
        }
        if (!UserConfig.getInstance(currentAccount).isPremium() && MessageObject.isPremiumSticker(document)) {
            return;
        }
        if (stickers == null) {
            stickers = new ArrayList<>();
            stickersMap = new HashMap<>();
        }

        stickers.add(new StickerResult(document, parent));
        stickersMap.put(key, document);
        if (mentionsStickersActionTracker != null) {
            mentionsStickersActionTracker.checkVisibility();
        }
    }

    private void addStickersToResult(ArrayList<TLRPC.Document> documents, Object parent) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int a = 0, size = documents.size(); a < size; a++) {
            TLRPC.Document document = documents.get(a);
            String key = document.dc_id + "_" + document.id;
            if (stickersMap != null && stickersMap.containsKey(key)) {
                continue;
            }
            if (!UserConfig.getInstance(currentAccount).isPremium() && MessageObject.isPremiumSticker(document)) {
                continue;
            }
            for (int b = 0, size2 = document.attributes.size(); b < size2; b++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    parent = attribute.stickerset;
                    break;
                }
            }
            if (stickers == null) {
                stickers = new ArrayList<>();
                stickersMap = new HashMap<>();
            }
            stickers.add(new StickerResult(document, parent));
            stickersMap.put(key, document);
        }
    }

    private boolean checkStickerFilesExistAndDownload() {
        if (stickers == null) {
            return false;
        }
        stickersToLoad.clear();
        int size = Math.min(6, stickers.size());
        for (int a = 0; a < size; a++) {
            StickerResult result = stickers.get(a);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(result.sticker.thumbs, 90);
            if (thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive) {
                File f = FileLoader.getInstance(currentAccount).getPathToAttach(thumb, "webp", true);
                if (!f.exists()) {
                    stickersToLoad.add(FileLoader.getAttachFileName(thumb, "webp"));
                    FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForDocument(thumb, result.sticker), result.parent, "webp", FileLoader.PRIORITY_NORMAL, 1);
                }
            }
        }
        return stickersToLoad.isEmpty();
    }

    private boolean isValidSticker(TLRPC.Document document, String emoji) {
        for (int b = 0, size2 = document.attributes.size(); b < size2; b++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(b);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.alt != null && attribute.alt.contains(emoji)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private void searchServerStickers(final String emoji, final String originalEmoji) {
        TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
        req.emoticon = originalEmoji;
        req.hash = 0;
        lastReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            lastReqId = 0;
            if (!emoji.equals(lastSticker) || !(response instanceof TLRPC.TL_messages_stickers)) {
                return;
            }
            delayLocalResults = false;
            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
            int oldCount = stickers != null ? stickers.size() : 0;
            addStickersToResult(res.stickers, "sticker_search_" + emoji);
            int newCount = stickers != null ? stickers.size() : 0;
            if (!visibleByStickersSearch && stickers != null && !stickers.isEmpty()) {
                checkStickerFilesExistAndDownload();
                delegate.needChangePanelVisibility(getItemCountInternal() > 0);
                visibleByStickersSearch = true;
            }
            if (oldCount != newCount) {
                notifyDataSetChanged();
            }
        }));
    }

    private Object[] lastData;

    @Override
    public void notifyDataSetChanged() {
        if (lastItemCount == -1 || lastData == null) {
            if (delegate != null) {
                delegate.onItemCountUpdate(0, getItemCount());
            }
            super.notifyDataSetChanged();
            lastData = new Object[getItemCount()];
            for (int i = 0; i < lastData.length; ++i) {
                lastData[i] = getItem(i);
            }
        } else {
            int oldCount = lastItemCount, newCount = getItemCount();
            boolean hadChanges = oldCount != newCount;
            int min = Math.min(oldCount, newCount);
            Object[] newData = new Object[newCount];
            for (int i = 0; i < newCount; ++i) {
                newData[i] = getItem(i);
            }
            for (int i = 0; i < min; ++i) {
                if (i < 0 || i >= lastData.length || i >= newData.length || !itemsEqual(lastData[i], newData[i])) {
                    notifyItemChanged(i);
                    hadChanges = true;
                } else if ((i == oldCount - 1) != (i == newCount - 1) && USE_DIVIDERS) {
                    notifyItemChanged(i); // divider update
                }
            }
            notifyItemRangeRemoved(min, oldCount - min);
            notifyItemRangeInserted(min, newCount - min);
            if (hadChanges && delegate != null) {
                delegate.onItemCountUpdate(oldCount, newCount);
            }
            lastData = newData;
        }

    }

    private boolean itemsEqual(Object a, Object b) {
        if (a instanceof QuickRepliesController.QuickReply) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (a instanceof MentionsAdapter.StickerResult && b instanceof MentionsAdapter.StickerResult && ((StickerResult) a).sticker == ((StickerResult) b).sticker) {
            return true;
        }
        if (a instanceof TLRPC.User && b instanceof TLRPC.User && ((TLRPC.User) a).id == ((TLRPC.User) b).id) {
            return true;
        }
        if (a instanceof TLRPC.Chat && b instanceof TLRPC.Chat && ((TLRPC.Chat) a).id == ((TLRPC.Chat) b).id) {
            return true;
        }
        if (a instanceof String && b instanceof String && a.equals(b)) {
            return true;
        }
        if (a instanceof MediaDataController.KeywordResult && b instanceof MediaDataController.KeywordResult &&
            ((MediaDataController.KeywordResult) a).keyword != null && ((MediaDataController.KeywordResult) a).keyword.equals(((MediaDataController.KeywordResult) b).keyword) &&
            ((MediaDataController.KeywordResult) a).emoji != null && ((MediaDataController.KeywordResult) a).emoji.equals(((MediaDataController.KeywordResult) b).emoji)) {
            return true;
        }
        return false;
    }

    private void clearStickers() {
        lastSticker = null;
        stickers = null;
        stickersMap = null;
        notifyDataSetChanged();
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }
        if (mentionsStickersActionTracker != null) {
            mentionsStickersActionTracker.checkVisibility();
        }
    }

    public void onDestroy() {
        if (locationProvider != null) {
            locationProvider.stop();
        }
        if (contextQueryRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(contextQueryRunnable);
            contextQueryRunnable = null;
        }
        if (contextUsernameReqid != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(contextUsernameReqid, true);
            contextUsernameReqid = 0;
        }
        if (contextQueryReqid != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryReqid, true);
            contextQueryReqid = 0;
        }
        foundContextBot = null;
        searchResultBotContextSwitch = null;
        inlineMediaEnabled = true;
        searchingContextUsername = null;
        searchingContextQuery = null;
        noUserName = false;
        if (!isDarkTheme) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
    }

    public void setParentFragment(ChatActivity fragment) {
        parentFragment = fragment;
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        currentAccount = UserConfig.selectedAccount;
        info = chatInfo;
        if (!inlineMediaEnabled && foundContextBot != null && parentFragment != null) {
            TLRPC.Chat chat = parentFragment.getCurrentChat();
            if (chat != null) {
                inlineMediaEnabled = ChatObject.canSendStickers(chat);
                if (inlineMediaEnabled) {
                    searchResultUsernames = null;
                    notifyDataSetChanged();
                    delegate.needChangePanelVisibility(false);
                    processFoundUser(foundContextBot);
                }
            }
        }
        if (lastText != null) {
            searchUsernameOrHashtag(lastText, lastPosition, messages, lastUsernameOnly, lastForSearch);
        }
    }

    public void setNeedUsernames(boolean value) {
        needUsernames = value;
    }

    public void setNeedBotContext(boolean value) {
        needBotContext = value;
    }

    public void setBotInfo(LongSparseArray<TL_bots.BotInfo> info) {
        botInfo = info;
    }

    public void setBotsCount(int count) {
        botsCount = count;
    }

    public void clearRecentHashtags() {
        searchAdapterHelper.clearRecentHashtags();
        searchResultHashtags.clear();
        notifyDataSetChanged();
        if (delegate != null) {
            delegate.needChangePanelVisibility(false);
        }
    }

    public TLRPC.TL_inlineBotSwitchPM getBotContextSwitch() {
        if (foundContextBot != null && foundContextBot.id != searchResultBotContextSwitchUserId) {
            return null;
        }
        return searchResultBotContextSwitch;
    }

    public TLRPC.TL_inlineBotWebView getBotWebViewSwitch() {
        return searchResultBotWebViewSwitch;
    }

    public long getContextBotId() {
        return foundContextBot != null ? foundContextBot.id : 0;
    }

    public TLRPC.User getContextBotUser() {
        return foundContextBot;
    }

    public String getContextBotName() {
        return foundContextBot != null ? foundContextBot.username : "";
    }

    private void processFoundUser(TLRPC.User user) {
        contextUsernameReqid = 0;
        locationProvider.stop();
        if (user != null && user.bot && user.bot_inline_placeholder != null) {
            foundContextBot = user;
            if (user != null && user.id != searchResultBotContextSwitchUserId) {
                searchResultBotContextSwitch = null;
                searchResultBotContextSwitchUserId = user.id;
            }
            if (parentFragment != null) {
                TLRPC.Chat chat = parentFragment.getCurrentChat();
                if (chat != null) {
                    inlineMediaEnabled = ChatObject.canSendStickers(chat);
                    if (!inlineMediaEnabled) {
                        notifyDataSetChanged();
                        delegate.needChangePanelVisibility(true);
                        return;
                    }
                }
            }
            if (foundContextBot.bot_inline_geo) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                boolean allowGeo = preferences.getBoolean("inlinegeo_" + foundContextBot.id, false);
                if (!allowGeo && parentFragment != null && parentFragment.getParentActivity() != null) {
                    final TLRPC.User foundContextBotFinal = foundContextBot;
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentFragment.getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.ShareYouLocationTitle));
                    builder.setMessage(LocaleController.getString(R.string.ShareYouLocationInline));
                    final boolean[] buttonClicked = new boolean[1];
                    builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                        buttonClicked[0] = true;
                        if (foundContextBotFinal != null) {
                            SharedPreferences preferences1 = MessagesController.getNotificationsSettings(currentAccount);
                            preferences1.edit().putBoolean("inlinegeo_" + foundContextBotFinal.id, true).commit();
                            checkLocationPermissionsOrStart();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
                        buttonClicked[0] = true;
                        onLocationUnavailable();
                    });
                    parentFragment.showDialog(builder.create(), dialog -> {
                        if (!buttonClicked[0]) {
                            onLocationUnavailable();
                        }
                    });
                } else {
                    checkLocationPermissionsOrStart();
                }
            }
        } else {
            foundContextBot = null;
            searchResultBotContextSwitch = null;
            inlineMediaEnabled = true;
        }
        if (foundContextBot == null) {
            noUserName = true;
            searchResultBotContextSwitch = null;
        } else {
            if (delegate != null) {
                delegate.onContextSearch(true);
            }
            searchForContextBotResults(true, foundContextBot, searchingContextQuery, "");
        }
    }

    private void searchForContextBot(final String username, final String query) {
        if (foundContextBot != null && foundContextBot.username != null && foundContextBot.username.equals(username) && searchingContextQuery != null && searchingContextQuery.equals(query)) {
            return;
        }
        if (foundContextBot != null) {
            if (!inlineMediaEnabled && username != null && query != null) {
                return;
            }
            delegate.needChangePanelVisibility(false);
        }
        if (contextQueryRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(contextQueryRunnable);
            contextQueryRunnable = null;
        }
        if (TextUtils.isEmpty(username) || searchingContextUsername != null && !searchingContextUsername.equals(username)) {
            if (contextUsernameReqid != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(contextUsernameReqid, true);
                contextUsernameReqid = 0;
            }
            if (contextQueryReqid != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryReqid, true);
                contextQueryReqid = 0;
            }
            foundContextBot = null;
            searchResultBotContextSwitch = null;
            inlineMediaEnabled = true;
            searchingContextUsername = null;
            searchingContextQuery = null;
            locationProvider.stop();
            noUserName = false;
            if (delegate != null) {
                delegate.onContextSearch(false);
            }
            if (username == null || username.length() == 0) {
                return;
            }
        }
        if (query == null) {
            if (contextQueryReqid != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryReqid, true);
                contextQueryReqid = 0;
            }
            searchingContextQuery = null;
            if (delegate != null) {
                delegate.onContextSearch(false);
            }
            return;
        }
        if (delegate != null) {
            if (foundContextBot != null) {
                delegate.onContextSearch(true);
            } else if (username.equals("gif")) {
                searchingContextUsername = "gif";
                delegate.onContextSearch(false);
            }
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        searchingContextQuery = query;
        contextQueryRunnable = new Runnable() {
            @Override
            public void run() {
                if (contextQueryRunnable != this) {
                    return;
                }
                contextQueryRunnable = null;
                if (foundContextBot != null || noUserName) {
                    if (noUserName) {
                        return;
                    }
                    searchForContextBotResults(true, foundContextBot, query, "");
                } else {
                    searchingContextUsername = username;
                    TLObject object = messagesController.getUserOrChat(searchingContextUsername);
                    if (object instanceof TLRPC.User) {
                        processFoundUser((TLRPC.User) object);
                    } else {
                        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                        req.username = searchingContextUsername;
                        contextUsernameReqid = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (searchingContextUsername == null || !searchingContextUsername.equals(username)) {
                                return;
                            }
                            TLRPC.User user = null;
                            if (error == null) {
                                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                if (!res.users.isEmpty()) {
                                    user = res.users.get(0);
                                    messagesController.putUser(user, false);
                                    messagesStorage.putUsersAndChats(res.users, null, true, true);
                                }
                            }
                            processFoundUser(user);
                            contextUsernameReqid = 0;
                        }));
                    }
                }
            }
        };
        AndroidUtilities.runOnUIThread(contextQueryRunnable, 400);
    }

    private void onLocationUnavailable() {
        if (foundContextBot != null && foundContextBot.bot_inline_geo) {
            lastKnownLocation = new Location("network");
            lastKnownLocation.setLatitude(-1000);
            lastKnownLocation.setLongitude(-1000);
            searchForContextBotResults(true, foundContextBot, searchingContextQuery, "");
        }
    }

    private void checkLocationPermissionsOrStart() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            return;
        }
        if (foundContextBot != null && foundContextBot.bot_inline_geo) {
            locationProvider.start();
        }
    }

    public void setSearchingMentions(boolean value) {
        isSearchingMentions = value;
    }

    public String getBotCaption() {
        if (foundContextBot != null) {
            return foundContextBot.bot_inline_placeholder;
        } else if (searchingContextUsername != null && searchingContextUsername.equals("gif")) {
            return LocaleController.getString(R.string.SearchGifsTitle);
        }
        return null;
    }

    public void searchForContextBotForNextOffset() {
        if (contextQueryReqid != 0 || nextQueryOffset == null || nextQueryOffset.length() == 0 || foundContextBot == null || searchingContextQuery == null) {
            return;
        }
        searchForContextBotResults(true, foundContextBot, searchingContextQuery, nextQueryOffset);
    }

    private void searchForContextBotResults(final boolean cache, final TLRPC.User user, final String query, final String offset) {
        if (contextQueryReqid != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(contextQueryReqid, true);
            contextQueryReqid = 0;
        }
        if (!inlineMediaEnabled || !allowBots) {
            if (delegate != null) {
                delegate.onContextSearch(false);
            }
            return;
        }
        if (query == null || user == null) {
            searchingContextQuery = null;
            return;
        }
        if (user.bot_inline_geo && lastKnownLocation == null) {
            return;
        }
        final String key = dialog_id + "_" + query + "_" + offset + "_" + dialog_id + "_" + user.id + "_" + (user.bot_inline_geo && lastKnownLocation.getLatitude() != -1000 ? lastKnownLocation.getLatitude() + lastKnownLocation.getLongitude() : "");
        final MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!query.equals(searchingContextQuery)) {
                return;
            }
            contextQueryReqid = 0;
            if (cache && response == null) {
                searchForContextBotResults(false, user, query, offset);
            } else if (delegate != null) {
                delegate.onContextSearch(false);
            }
            if (response instanceof TLRPC.TL_messages_botResults) {
                TLRPC.TL_messages_botResults res = (TLRPC.TL_messages_botResults) response;
                if (!cache && res.cache_time != 0) {
                    messagesStorage.saveBotCache(key, res);
                }
                nextQueryOffset = res.next_offset;
                if (searchResultBotContextSwitch == null) {
                    searchResultBotContextSwitch = res.switch_pm;
                }
                searchResultBotWebViewSwitch = res.switch_webview;
                for (int a = 0; a < res.results.size(); a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (!(result.document instanceof TLRPC.TL_document) && !(result.photo instanceof TLRPC.TL_photo) && !"game".equals(result.type) && result.content == null && result.send_message instanceof TLRPC.TL_botInlineMessageMediaAuto) {
                        res.results.remove(a);
                        a--;
                    }
                    result.query_id = res.query_id;
                }
                boolean added = false;
                if (searchResultBotContext == null || offset.length() == 0) {
                    searchResultBotContext = res.results;
                    contextMedia = res.gallery;
                } else {
                    added = true;
                    searchResultBotContext.addAll(res.results);
                    if (res.results.isEmpty()) {
                        nextQueryOffset = "";
                    }
                }
                if (cancelDelayRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable);
                    cancelDelayRunnable = null;
                }
                searchResultHashtags = null;
                stickers = null;
                searchResultUsernames = null;
                searchResultUsernamesMap = null;
                searchResultCommands = null;
                quickReplies = null;
                searchResultSuggestions = null;
                searchResultCommandsHelp = null;
                searchResultCommandsUsers = null;
                delegate.needChangePanelVisibility(!searchResultBotContext.isEmpty() || searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null);
                if (added) {
                    boolean hasTop = searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null;
                    notifyItemChanged(searchResultBotContext.size() - res.results.size() + (hasTop ? 1 : 0) - 1);
                    notifyItemRangeInserted(searchResultBotContext.size() - res.results.size() + (hasTop ? 1 : 0), res.results.size());
                } else {
                    notifyDataSetChanged();
                }
            }
        });

        if (user != null && user.id != searchResultBotContextSwitchUserId) {
            searchResultBotContextSwitch = null;
            searchResultBotContextSwitchUserId = user.id;
        }

        if (cache) {
            messagesStorage.getBotCache(key, requestDelegate);
        } else {
            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(user);
            req.query = query;
            req.offset = offset;
            if (user.bot_inline_geo && lastKnownLocation != null && lastKnownLocation.getLatitude() != -1000) {
                req.flags |= 1;
                req.geo_point = new TLRPC.TL_inputGeoPoint();
                req.geo_point.lat = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLatitude());
                req.geo_point._long = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLongitude());
            }
            if (DialogObject.isEncryptedDialog(dialog_id)) {
                req.peer = new TLRPC.TL_inputPeerEmpty();
            } else {
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialog_id);
            }
            contextQueryReqid = ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    public void searchUsernameOrHashtag(CharSequence charSequence, int position, ArrayList<MessageObject> messageObjects, boolean usernameOnly, boolean forSearch) {
        final String text = charSequence == null ? "" : charSequence.toString();
        TLRPC.Chat currentChat = chat;
        TLRPC.User currentUser = user;
        if (parentFragment != null) {
            currentChat = parentFragment.getCurrentChat();
            currentUser = parentFragment.getCurrentUser();
        }
        if (cancelDelayRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable);
            cancelDelayRunnable = null;
        }
        if (channelReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(channelReqId, true);
            channelReqId = 0;
        }
        if (searchGlobalRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchGlobalRunnable);
            searchGlobalRunnable = null;
        }
        if (checkAgainRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkAgainRunnable);
            checkAgainRunnable = null;
        }
        if (TextUtils.isEmpty(text) || text.length() > MessagesController.getInstance(currentAccount).maxMessageLength) {
            searchForContextBot(null, null);
            delegate.needChangePanelVisibility(false);
            lastText = null;
            clearStickers();
            return;
        }
        int searchPostion = position;
        if (text.length() > 0) {
            searchPostion--;
        }
        lastText = null;
        lastUsernameOnly = usernameOnly;
        lastForSearch = forSearch;
        StringBuilder result = new StringBuilder();
        int foundType = -1;

        boolean searchEmoji = !usernameOnly && text.length() > 0 && text.length() <= 14;
        String originalEmoji = "";
        if (searchEmoji) {
            CharSequence emoji = originalEmoji = text;
            int length = emoji.length();
            for (int a = 0; a < length; a++) {
                char ch = emoji.charAt(a);
                char nch = a < length - 1 ? emoji.charAt(a + 1) : 0;
                if (a < length - 1 && ch == 0xD83C && nch >= 0xDFFB && nch <= 0xDFFF) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                    length -= 2;
                    a--;
                } else if (ch == 0xfe0f) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                    length--;
                    a--;
                }
            }
            lastSticker = emoji.toString().trim();
        }
        boolean isValidEmoji = searchEmoji && (Emoji.isValidEmoji(originalEmoji) || Emoji.isValidEmoji(lastSticker));
        if (isValidEmoji && charSequence instanceof Spanned) {
            AnimatedEmojiSpan[] spans = ((Spanned) charSequence).getSpans(0, charSequence.length(), AnimatedEmojiSpan.class);
            isValidEmoji = spans == null || spans.length == 0;
        }

        if (allowStickers && isValidEmoji && (currentChat == null || ChatObject.canSendStickers(currentChat))) {
            stickersToLoad.clear();
            if (SharedConfig.suggestStickers == 2 || !isValidEmoji) {
                if (visibleByStickersSearch && SharedConfig.suggestStickers == 2) {
                    visibleByStickersSearch = false;
                    delegate.needChangePanelVisibility(false);
                    notifyDataSetChanged();
                }
                return;
            }
            stickers = null;
            stickersMap = null;
            foundType = 4;
            if (lastReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
                lastReqId = 0;
            }
            boolean serverStickersOnly = MessagesController.getInstance(currentAccount).suggestStickersApiOnly;

            delayLocalResults = false;
            if (!serverStickersOnly) {
                checkAgainRunnable = () -> searchUsernameOrHashtag(charSequence, position, messageObjects, usernameOnly, forSearch);
                MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
                MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
                final ArrayList<TLRPC.Document> recentStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE);
                final ArrayList<TLRPC.Document> favsStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_FAVE);
                int recentsAdded = 0;
                for (int a = 0, size = Math.min(20, recentStickers.size()); a < size; a++) {
                    TLRPC.Document document = recentStickers.get(a);
                    if (isValidSticker(document, lastSticker)) {
                        addStickerToResult(document, "recent");
                        recentsAdded++;
                        if (recentsAdded >= 5) {
                            break;
                        }
                    }
                }
                for (int a = 0, size = favsStickers.size(); a < size; a++) {
                    TLRPC.Document document = favsStickers.get(a);
                    if (isValidSticker(document, lastSticker)) {
                        addStickerToResult(document, "fav");
                    }
                }

                MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
                HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();
                ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(lastSticker) : null;
                if (newStickers != null && !newStickers.isEmpty()) {
                    addStickersToResult(newStickers, null);
                }
                if (stickers != null) {
                    Collections.sort(stickers, new Comparator<StickerResult>() {
                        private int getIndex(StickerResult result) {
                            for (int a = 0; a < favsStickers.size(); a++) {
                                if (favsStickers.get(a).id == result.sticker.id) {
                                    return a + 2000000;
                                }
                            }
                            for (int a = 0; a < Math.min(20, recentStickers.size()); a++) {
                                if (recentStickers.get(a).id == result.sticker.id) {
                                    return recentStickers.size() - a + 1000000;
                                }
                            }
                            return -1;
                        }

                        @Override
                        public int compare(StickerResult lhs, StickerResult rhs) {
                            boolean isAnimated1 = MessageObject.isAnimatedStickerDocument(lhs.sticker, true);
                            boolean isAnimated2 = MessageObject.isAnimatedStickerDocument(rhs.sticker, true);
                            if (isAnimated1 == isAnimated2) {
                                int idx1 = getIndex(lhs);
                                int idx2 = getIndex(rhs);
                                if (idx1 > idx2) {
                                    return -1;
                                } else if (idx1 < idx2) {
                                    return 1;
                                }
                                return 0;
                            } else {
                                if (isAnimated1) {
                                    return -1;
                                } else {
                                    return 1;
                                }
                            }
                        }
                    });
                }
            }
            if (SharedConfig.suggestStickers == 0 || serverStickersOnly) {
                searchServerStickers(lastSticker, originalEmoji);
            }

            if (stickers != null && !stickers.isEmpty()) {
                if (SharedConfig.suggestStickers == 0 && stickers.size() < 5) {
                    delayLocalResults = true;
                    delegate.needChangePanelVisibility(false);
                    visibleByStickersSearch = false;
                } else {
                    checkStickerFilesExistAndDownload();
                    boolean show = stickersToLoad.isEmpty();
                    delegate.needChangePanelVisibility(show);
                    visibleByStickersSearch = true;
                }
                notifyDataSetChanged();
            } else if (visibleByStickersSearch) {
                delegate.needChangePanelVisibility(false);
                visibleByStickersSearch = false;
            }
        } else if (!usernameOnly && needBotContext && text.charAt(0) == '@') {
            int index = text.indexOf(' ');
            int len = text.length();
            String username = null;
            String query = null;
            if (index > 0) {
                username = text.substring(1, index);
                query = text.substring(index + 1);
            } else if (text.charAt(len - 1) == 't' && text.charAt(len - 2) == 'o' && text.charAt(len - 3) == 'b') {
                username = text.substring(1);
                query = "";
            } else {
                searchForContextBot(null, null);
            }
            if (username != null && username.length() >= 1) {
                for (int a = 1; a < username.length(); a++) {
                    char ch = username.charAt(a);
                    if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                        username = "";
                        break;
                    }
                }
            } else {
                username = "";
            }
            searchForContextBot(username, query);
        } else {
            searchForContextBot(null, null);
        }
        if (foundContextBot != null) {
            return;
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        int dogPostion = -1;
        if (usernameOnly) {
            result.append(text.substring(1));
            resultStartPosition = 0;
            resultLength = result.length();
            foundType = 0;
        } else {
            for (int a = searchPostion; a >= 0; a--) {
                if (a >= text.length()) {
                    continue;
                }
                char ch = text.charAt(a);
                if (a == 0 || text.charAt(a - 1) == ' ' || text.charAt(a - 1) == '\n' || ch == ':') {
                    if (ch == '@') {
                        if (searchInDailogs || (needUsernames || needBotContext && a == 0)) {
                            if (!searchInDailogs && info == null && a != 0) {
                                lastText = text;
                                lastPosition = position;
                                messages = messageObjects;
                                delegate.needChangePanelVisibility(false);
                                return;
                            }
                            dogPostion = a;
                            foundType = 0;
                            resultStartPosition = a;
                            resultLength = result.length() + 1;
                            break;
                        }
                    } else if (ch == '#') {
                        if (searchAdapterHelper.loadRecentHashtags()) {
                            foundType = 1;
                            resultStartPosition = a;
                            resultLength = result.length() + 1;
                            result.insert(0, ch);
                            break;
                        } else {
                            lastText = text;
                            lastPosition = position;
                            messages = messageObjects;
//                            delegate.needChangePanelVisibility(false);
                            return;
                        }
                    } else if (a == 0 && botInfo != null && ch == '/') {
                        foundType = 2;
                        resultStartPosition = a;
                        resultLength = result.length() + 1;
                        break;
                    } else if (ch == ':' && result.length() > 0) {
                        boolean isNextPunctiationChar = punctuationsChars.indexOf(result.charAt(0)) >= 0;
                        if (!isNextPunctiationChar || result.length() > 1) {
                            foundType = 3;
                            resultStartPosition = a;
                            resultLength = result.length() + 1;
                            break;
                        }
                    }
                }
                result.insert(0, ch);
            }
        }
        if (foundType == -1) {
            contextMedia = false;
            searchResultBotContext = null;
            delegate.needChangePanelVisibility(false);
            return;
        }
        if (foundType == 0) {
            contextMedia = false;
            searchResultBotContext = null;
            final ArrayList<Long> users = new ArrayList<>();
            if (messageObjects != null) {
                for (int a = 0; a < Math.min(100, messageObjects.size()); a++) {
                    long from_id = messageObjects.get(a).getFromChatId();
                    if (from_id > 0 && !users.contains(from_id)) {
                        users.add(from_id);
                    }
                }
            }
            final String usernameString = result.toString().toLowerCase();
            boolean hasSpace = usernameString.indexOf(' ') >= 0;
            ArrayList<TLObject> newResult = new ArrayList<>();
            final LongSparseArray<TLRPC.User> newResultsHashMap = new LongSparseArray<>();
            final LongSparseArray<TLObject> newMap = new LongSparseArray<>();
            ArrayList<TLRPC.TL_topPeer> inlineBots = MediaDataController.getInstance(currentAccount).inlineBots;
            if (!usernameOnly && needBotContext && dogPostion == 0 && !inlineBots.isEmpty()) {
                int count = 0;
                for (int a = 0; a < inlineBots.size(); a++) {
                    TLRPC.User user = messagesController.getUser(inlineBots.get(a).peer.user_id);
                    if (user == null) {
                        continue;
                    }
                    String username = UserObject.getPublicUsername(user);
                    if (!TextUtils.isEmpty(username) && (usernameString.length() == 0 || username.toLowerCase().startsWith(usernameString))) {
                        newResult.add(user);
                        newResultsHashMap.put(user.id, user);
                        newMap.put(user.id, user);
                        count++;
                    }
                    if (count == 5) {
                        break;
                    }
                }
            }
            final TLRPC.Chat chat;
            long threadId;
            if (parentFragment != null) {
                chat = parentFragment.getCurrentChat();
                threadId = parentFragment.getThreadId();
            } else if (info != null) {
                chat = messagesController.getChat(info.id);
                threadId = 0;
            } else {
                chat = currentChat;
                threadId = 0;
            }
            TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (chat != null && info != null && info.participants != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
                for (int a = -2; a < info.participants.participants.size(); a++) {
                    String username;
                    String firstName;
                    String lastName;
                    TLObject object;
                    long id;
                    if (a == -2) {
                        if (me == null || !usernameOnly) {
                            continue;
                        }
                        firstName = me.first_name;
                        lastName = me.last_name;
                        username = UserObject.getPublicUsername(me);
                        object = me;
                        id = me.id;
                    } else if (a == -1) {
                        if (!forSearch) {
                            continue;
                        }
                        if (usernameString.length() == 0) {
                            newResult.add(chat);
                            continue;
                        }
                        firstName = chat.title;
                        lastName = null;
                        username = ChatObject.getPublicUsername(chat);
                        object = chat;
                        id = -chat.id;
                    } else {
                        TLRPC.ChatParticipant chatParticipant = info.participants.participants.get(a);
                        if (me != null && chatParticipant.user_id == me.id) {
                            continue;
                        }
                        TLRPC.User user = messagesController.getUser(chatParticipant.user_id);
                        if (user == null || UserObject.isUserSelf(user) || newResultsHashMap.indexOfKey(user.id) >= 0) {
                            continue;
                        }
                        if (usernameString.length() == 0) {
                            if (!user.deleted) {
                                newResult.add(user);
                                continue;
                            }
                        }
                        firstName = user.first_name;
                        lastName = user.last_name;
                        username = UserObject.getPublicUsername(user);
                        object = user;
                        id = user.id;
                    }
                    if (!TextUtils.isEmpty(username) && username.toLowerCase().startsWith(usernameString) ||
                            !TextUtils.isEmpty(firstName) && firstName.toLowerCase().startsWith(usernameString) ||
                            !TextUtils.isEmpty(lastName) && lastName.toLowerCase().startsWith(usernameString) ||
                            hasSpace && ContactsController.formatName(firstName, lastName).toLowerCase().startsWith(usernameString)) {
                        newResult.add(object);
                        newMap.put(id, object);
                    }
                }
            }
            if (searchInDailogs) {
                ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
               // ArrayList<TLRPC.TL_contact> contacts = ContactsController.getInstance(currentAccount).contacts;
                for (int a = 0; a < dialogs.size(); a++) {
                    String username;
                    String firstName;
                    String lastName;
                    TLObject object;
                    long id = dialogs.get(a).id;
                    if (id > 0) {
                        TLRPC.User user = messagesController.getUser(dialogs.get(a).id);
                        if (user == null || UserObject.isUserSelf(user) || newResultsHashMap.indexOfKey(user.id) >= 0) {
                            continue;
                        }
                        if (usernameString.length() == 0) {
                            if (!user.deleted) {
                                newResult.add(user);
                                continue;
                            }
                        }
                        firstName = user.first_name;
                        lastName = user.last_name;
                        username = UserObject.getPublicUsername(user);
                        object = user;
                        id = user.id;
                        if (!TextUtils.isEmpty(username) && username.toLowerCase().startsWith(usernameString) ||
                                !TextUtils.isEmpty(firstName) && firstName.toLowerCase().startsWith(usernameString) ||
                                !TextUtils.isEmpty(lastName) && lastName.toLowerCase().startsWith(usernameString) ||
                                hasSpace && ContactsController.formatName(firstName, lastName).toLowerCase().startsWith(usernameString)) {
                            newResult.add(object);
                            newMap.put(id, object);
                        }
                    } else if (!TextUtils.isEmpty(usernameString)) {
                        TLRPC.Chat chat1 = messagesController.getChat(-dialogs.get(a).id);
                        if (chat1 == null || chat1.username == null || newResultsHashMap.indexOfKey(chat1.id) >= 0) {
                            continue;
                        }
                        if (usernameString.length() == 0) {
                            newResult.add(chat1);
                            continue;
                        }
                        firstName = chat1.title;
                        username = chat1.username;
                        object = chat1;
                        id = chat1.id;
                        if (!TextUtils.isEmpty(username) && username.toLowerCase().startsWith(usernameString) ||
                                !TextUtils.isEmpty(firstName) && firstName.toLowerCase().startsWith(usernameString)){
                            newResult.add(object);
                            newMap.put(id, object);
                        }
                    }
                }
            }
            Collections.sort(newResult, new Comparator<TLObject>() {

                private long getId(TLObject object) {
                    if (object instanceof TLRPC.User) {
                        return ((TLRPC.User) object).id;
                    } else {
                        return -((TLRPC.Chat) object).id;
                    }
                }

                @Override
                public int compare(TLObject lhs, TLObject rhs) {
                    long id1 = getId(lhs);
                    long id2 = getId(rhs);
                    if (newMap.indexOfKey(id1) >= 0 && newMap.indexOfKey(id2) >= 0) {
                        return 0;
                    } else if (newMap.indexOfKey(id1) >= 0) {
                        return -1;
                    } else if (newMap.indexOfKey(id2) >= 0) {
                        return 1;
                    }
                    int lhsNum = users.indexOf(id1);
                    int rhsNum = users.indexOf(id2);
                    if (lhsNum != -1 && rhsNum != -1) {
                        return lhsNum < rhsNum ? -1 : (lhsNum == rhsNum ? 0 : 1);
                    } else if (lhsNum != -1 && rhsNum == -1) {
                        return -1;
                    } else if (lhsNum == -1 && rhsNum != -1) {
                        return 1;
                    }
                    return 0;
                }
            });
            searchResultHashtags = null;
            stickers = null;
            quickReplies = null;
            searchResultCommands = null;
            searchResultCommandsHelp = null;
            searchResultCommandsUsers = null;
            searchResultSuggestions = null;
            if ((chat != null && chat.megagroup || searchInDailogs) && usernameString.length() > 0) {
                if (newResult.size() < 5) {
                    AndroidUtilities.runOnUIThread(cancelDelayRunnable = () -> {
                        cancelDelayRunnable = null;
                        showUsersResult(newResult, newMap, true);
                    }, 1000);
                } else {
                    showUsersResult(newResult, newMap, true);
                }

                AndroidUtilities.runOnUIThread(searchGlobalRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (searchGlobalRunnable != this) {
                            return;
                        }
                        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
                        req.channel = MessagesController.getInputChannel(chat);
                        req.limit = 20;
                        req.offset = 0;
                        TLRPC.TL_channelParticipantsMentions channelParticipantsMentions = new TLRPC.TL_channelParticipantsMentions();
                        channelParticipantsMentions.flags |= 1;
                        channelParticipantsMentions.q = usernameString;
                        if (threadId != 0) {
                            channelParticipantsMentions.flags |= 2;
                            channelParticipantsMentions.top_msg_id = (int) threadId;
                        }
                        req.filter = channelParticipantsMentions;
                        final int currentReqId = ++channelLastReqId;
                        channelReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (channelReqId != 0 && currentReqId == channelLastReqId && searchResultUsernamesMap != null && searchResultUsernames != null) {
                                showUsersResult(newResult, newMap, false);
                                if (error == null) {
                                    TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                                    messagesController.putUsers(res.users, false);
                                    messagesController.putChats(res.chats, false);
                                    boolean hasResults = !searchResultUsernames.isEmpty();
                                    if (!res.participants.isEmpty()) {
                                        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                                        for (int a = 0; a < res.participants.size(); a++) {
                                            TLRPC.ChannelParticipant participant = res.participants.get(a);
                                            long peerId = MessageObject.getPeerId(participant.peer);
                                            if (searchResultUsernamesMap.indexOfKey(peerId) >= 0 || peerId == 0 && searchResultUsernamesMap.indexOfKey(currentUserId) >= 0 || !isSearchingMentions && (peerId == currentUserId || peerId == 0)) {
                                                continue;
                                            }
                                            if (peerId >= 0) {
                                                TLRPC.User user = messagesController.getUser(peerId);
                                                if (user == null) {
                                                    return;
                                                }
                                                searchResultUsernames.add(user);
                                            } else {
                                                TLRPC.Chat chat = messagesController.getChat(-peerId);
                                                if (chat == null) {
                                                    return;
                                                }
                                                searchResultUsernames.add(chat);
                                            }
                                        }
                                    }
                                }
                                notifyDataSetChanged();
                                delegate.needChangePanelVisibility(!searchResultUsernames.isEmpty());
                            }
                            channelReqId = 0;
                        }));
                    }
                }, 200);
            } else {
                showUsersResult(newResult, newMap, true);
            }
        } else if (foundType == 1) {
            ArrayList<String> newResult = new ArrayList<>();
            String hashtagString = result.toString().toLowerCase();
            ArrayList<SearchAdapterHelper.HashtagObject> hashtags = searchAdapterHelper.getHashtags();
            for (int a = 0; a < hashtags.size(); a++) {
                SearchAdapterHelper.HashtagObject hashtagObject = hashtags.get(a);
                if (hashtagObject != null && hashtagObject.hashtag != null && hashtagObject.hashtag.startsWith(hashtagString)) {
                    newResult.add(hashtagObject.hashtag);
                }
            }
            searchResultHashtags = newResult;
            stickers = null;
            searchResultUsernames = null;
            searchResultUsernamesMap = null;
            quickReplies = null;
            searchResultCommands = null;
            searchResultCommandsHelp = null;
            searchResultCommandsUsers = null;
            searchResultSuggestions = null;
            contextMedia = false;
            searchResultBotContext = null;
            notifyDataSetChanged();
            delegate.needChangePanelVisibility(!searchResultHashtags.isEmpty());
        } else if (foundType == 2) {
            ArrayList<String> newResult = new ArrayList<>();
            ArrayList<String> newResultHelp = new ArrayList<>();
            ArrayList<TLRPC.User> newResultUsers = new ArrayList<>();
            String command = result.toString().toLowerCase();
            for (int b = 0; b < botInfo.size(); b++) {
                TL_bots.BotInfo info = botInfo.valueAt(b);
                for (int a = 0; a < info.commands.size(); a++) {
                    TLRPC.TL_botCommand botCommand = info.commands.get(a);
                    if (botCommand != null && botCommand.command != null && botCommand.command.startsWith(command)) {
                        newResult.add("/" + botCommand.command);
                        newResultHelp.add(botCommand.description);
                        newResultUsers.add(messagesController.getUser(info.user_id));
                    }
                }
            }
            if (parentFragment != null && !DialogObject.isEncryptedDialog(dialog_id) && parentFragment.getChatMode() == 0 && parentFragment.getCurrentUser() != null && !parentFragment.getCurrentUser().bot && !UserObject.isReplyUser(parentFragment.getCurrentUser()) && !UserObject.isService(parentFragment.getCurrentUser().id)) {
                QuickRepliesController quickRepliesController = QuickRepliesController.getInstance(currentAccount);
                quickRepliesController.load();
                quickRepliesQuery = command;
                quickReplies = new ArrayList<QuickRepliesController.QuickReply>();
                for (int i = 0; i < quickRepliesController.replies.size(); i++) {
                    QuickRepliesController.QuickReply reply = quickRepliesController.replies.get(i);
                    if (!reply.isSpecial() && reply.name.startsWith(command)) {
                        quickReplies.add(reply);
                    }
                }
            } else {
                quickRepliesQuery = null;
                quickReplies = null;
            }
            searchResultHashtags = null;
            stickers = null;
            searchResultUsernames = null;
            searchResultUsernamesMap = null;
            searchResultSuggestions = null;
            searchResultCommands = newResult;
            searchResultCommandsHelp = newResultHelp;
            searchResultCommandsUsers = newResultUsers;
            contextMedia = false;
            searchResultBotContext = null;
            notifyDataSetChanged();
            delegate.needChangePanelVisibility(!newResult.isEmpty() || quickReplies != null && !quickReplies.isEmpty());
        } else if (foundType == 3) {
            String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
            if (!Arrays.equals(newLanguage, lastSearchKeyboardLanguage)) {
                MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
            }
            lastSearchKeyboardLanguage = newLanguage;
            MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, result.toString(), false, (param, alias) -> {
                searchResultSuggestions = param;
                searchResultHashtags = null;
                stickers = null;
                searchResultUsernames = null;
                searchResultUsernamesMap = null;
                searchResultCommands = null;
                quickReplies = null;
                searchResultCommandsHelp = null;
                searchResultCommandsUsers = null;
                notifyDataSetChanged();
                delegate.needChangePanelVisibility(searchResultSuggestions != null && !searchResultSuggestions.isEmpty());
            }, SharedConfig.suggestAnimatedEmoji && UserConfig.getInstance(currentAccount).isPremium());
        } else if (foundType == 4) {
            searchResultHashtags = null;
            searchResultUsernames = null;
            searchResultUsernamesMap = null;
            searchResultSuggestions = null;
            searchResultCommands = null;
            quickReplies = null;
            searchResultCommandsHelp = null;
            searchResultCommandsUsers = null;
        }
    }

    private boolean isReversed = false;
    public void setIsReversed(boolean isReversed) {
        if (this.isReversed != isReversed) {
            this.isReversed = isReversed;
            int itemCount = getLastItemCount();
            if (itemCount > 0) {
                notifyItemChanged(0);
            }
            if (itemCount > 1) {
                notifyItemChanged(itemCount - 1);
            }
        }
    }

    private void showUsersResult(ArrayList<TLObject> newResult, LongSparseArray<TLObject> newMap, boolean notify) {
        searchResultUsernames = newResult;
        if ((!allowBots || !allowChats) && searchResultUsernames != null) {
            Iterator<TLObject> i = searchResultUsernames.iterator();
            while (i.hasNext()) {
                TLObject obj = i.next();
                if (obj instanceof TLRPC.Chat && !allowChats) {
                    i.remove();
                } else if (obj instanceof TLRPC.User && (((TLRPC.User) obj).bot || UserObject.isService(((TLRPC.User) obj).id))) {
                    i.remove();
                }
            }
        }
        searchResultUsernamesMap = newMap;
        if (cancelDelayRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(cancelDelayRunnable);
            cancelDelayRunnable = null;
        }
        searchResultBotContext = null;
        stickers = null;
        if (notify) {
            notifyDataSetChanged();
            delegate.needChangePanelVisibility(!searchResultUsernames.isEmpty());
        }
    }

    public int getResultStartPosition() {
        return resultStartPosition;
    }

    public int getResultLength() {
        return resultLength;
    }

    public ArrayList<TLRPC.BotInlineResult> getSearchResultBotContext() {
        return searchResultBotContext;
    }

    private int lastItemCount = -1;

    @Override
    public int getItemCount() {
        return lastItemCount = getItemCountInternal();
    }

    public int getLastItemCount() {
        return lastItemCount;
    }

    public int getItemCountInternal() {
        if (foundContextBot != null && !inlineMediaEnabled) {
            return 1;
        }
        if (stickers != null) {
            return stickers.size();
        } else if (searchResultBotContext != null) {
            return searchResultBotContext.size() + (searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null ? 1 : 0);
        } else if (searchResultUsernames != null) {
            return searchResultUsernames.size();
        } else if (searchResultHashtags != null) {
            return searchResultHashtags.size();
        } else if (searchResultCommands != null || quickReplies != null) {
            return (quickReplies == null ? 0 : quickReplies.size()) + (searchResultCommands == null ? 0 : searchResultCommands.size());
        } else if (searchResultSuggestions != null) {
            return searchResultSuggestions.size();
        }
        return 0;
    }

    public void clear(boolean safe) {
        if (safe && (channelReqId != 0 || contextQueryReqid != 0 || contextUsernameReqid != 0 || lastReqId != 0)) {
            return;
        }
        foundContextBot = null;
        if (stickers != null) {
            stickers.clear();
        }
        if (searchResultBotContext != null) {
            searchResultBotContext.clear();
        }
        searchResultBotContextSwitch = null;
        searchResultBotWebViewSwitch = null;
        if (searchResultUsernames != null) {
            searchResultUsernames.clear();
        }
        if (searchResultHashtags != null) {
            searchResultHashtags.clear();
        }
        if (searchResultCommands != null) {
            searchResultCommands.clear();
        }
        if (quickReplies != null) {
            quickReplies.clear();
        }
        if (searchResultSuggestions != null) {
            searchResultSuggestions.clear();
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (stickers != null) {
            return 4;
        } else if (foundContextBot != null && !inlineMediaEnabled) {
            return 3;
        } else if (searchResultBotContext != null) {
            if (position == 0 && (searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null)) {
                return 2;
            }
            return 1;
        } else if (quickReplies != null && position >= 0 && position < quickReplies.size()) {
            return 5;
        } else {
            return 0;
        }
    }

    public void addHashtagsFromMessage(CharSequence message) {
        searchAdapterHelper.addHashtagsFromMessage(message);
    }

    public int getItemPosition(int i) {
        if (searchResultBotContext != null && (searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null)) {
            i--;
        }
        return i;
    }

    public Object getItemParent(int i) {
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i).parent : null;
    }

    public Object getItem(int i) {
        if (stickers != null) {
            return i >= 0 && i < stickers.size() ? stickers.get(i).sticker : null;
        } else if (searchResultBotContext != null) {
            if (searchResultBotWebViewSwitch != null) {
                if (i == 0) {
                    return searchResultBotWebViewSwitch;
                } else {
                    i--;
                }
            } else if (searchResultBotContextSwitch != null) {
                if (i == 0) {
                    return searchResultBotContextSwitch;
                } else {
                    i--;
                }
            }
            if (i < 0 || i >= searchResultBotContext.size()) {
                return null;
            }
            return searchResultBotContext.get(i);
        } else if (searchResultUsernames != null) {
            if (i < 0 || i >= searchResultUsernames.size()) {
                return null;
            }
            return searchResultUsernames.get(i);
        } else if (searchResultHashtags != null) {
            if (i < 0 || i >= searchResultHashtags.size()) {
                return null;
            }
            return searchResultHashtags.get(i);
        } else if (searchResultSuggestions != null) {
            if (i < 0 || i >= searchResultSuggestions.size()) {
                return null;
            }
            return searchResultSuggestions.get(i);
        } else if (quickReplies != null || searchResultCommands != null) {
            if (quickReplies != null) {
                if (i >= 0 && i < quickReplies.size()) {
                    return quickReplies.get(i);
                } else if (quickReplies != null) {
                    i -= quickReplies.size();
                }
            }
            if (searchResultCommands != null) {
                if (i < 0 || i >= searchResultCommands.size()) {
                    return null;
                }
                if (searchResultCommandsUsers != null && (botsCount != 1 || info instanceof TLRPC.TL_channelFull)) {
                    if (searchResultCommandsUsers.get(i) != null) {
                        return String.format("%s@%s", searchResultCommands.get(i), searchResultCommandsUsers.get(i) != null ? searchResultCommandsUsers.get(i).username : "");
                    } else {
                        return String.format("%s", searchResultCommands.get(i));
                    }
                }
                return searchResultCommands.get(i);
            }
        }
        return null;
    }

    public boolean isLongClickEnabled() {
        return searchResultHashtags != null || searchResultCommands != null;
    }

    public boolean isBotCommands() {
        return searchResultCommands != null;
    }

    public boolean isStickers() {
        return stickers != null;
    }

    public boolean isBotContext() {
        return searchResultBotContext != null;
    }

    public boolean isBannedInline() {
        return foundContextBot != null && !inlineMediaEnabled;
    }

    public boolean isMediaLayout() {
        return contextMedia || stickers != null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return (foundContextBot == null || inlineMediaEnabled) && stickers == null;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new MentionCell(mContext, resourcesProvider);
                ((MentionCell) view).setIsDarkTheme(isDarkTheme);
                break;
            case 1:
                view = new ContextLinkCell(mContext);
                ((ContextLinkCell) view).setDelegate(cell -> delegate.onContextClick(cell.getResult()));
                break;
            case 2:
                view = new BotSwitchCell(mContext);
                break;
            case 3:
                TextView textView = new TextView(mContext);
                textView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
                view = textView;
                break;
            case 5:
                view = new QuickRepliesActivity.QuickReplyView(mContext, false, resourcesProvider);
                break;
            case 4:
            default:
                view = new StickerCell(mContext, resourcesProvider);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int type = holder.getItemViewType();
        if (type == 4) {
            StickerCell stickerCell = (StickerCell) holder.itemView;
            StickerResult result = stickers.get(position);
            stickerCell.setSticker(result.sticker, result.parent);
            stickerCell.setClearsInputField(true);
        } else if (type == 3) {
            TextView textView = (TextView) holder.itemView;
            TLRPC.Chat chat = parentFragment.getCurrentChat();
            if (chat != null) {
                if (!ChatObject.hasAdminRights(chat) && chat.default_banned_rights != null && chat.default_banned_rights.send_inline) {
                    textView.setText(LocaleController.getString(R.string.GlobalAttachInlineRestricted));
                } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                    textView.setText(LocaleController.getString(R.string.AttachInlineRestrictedForever));
                } else {
                    textView.setText(LocaleController.formatString("AttachInlineRestricted", R.string.AttachInlineRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                }
            }
        } else if (type == 5) {
            QuickRepliesActivity.QuickReplyView cell = (QuickRepliesActivity.QuickReplyView) holder.itemView;
            if (quickReplies != null && position >= 0 && position < quickReplies.size()) {
                cell.set(quickReplies.get(position), quickRepliesQuery, USE_DIVIDERS && (position + 1) < getItemCount());
            }
        } else if (searchResultBotContext != null) {
            boolean hasTop = searchResultBotContextSwitch != null || searchResultBotWebViewSwitch != null;
            if (holder.getItemViewType() == 2) {
                if (hasTop) {
                    ((BotSwitchCell) holder.itemView).setText(searchResultBotContextSwitch != null ? searchResultBotContextSwitch.text : searchResultBotWebViewSwitch.text);
                }
            } else {
                if (hasTop) {
                    position--;
                }
                ((ContextLinkCell) holder.itemView).setLink(searchResultBotContext.get(position), foundContextBot, contextMedia, position != searchResultBotContext.size() - 1, hasTop && position == 0, "gif".equals(searchingContextUsername));
            }
        } else {
            MentionCell cell = (MentionCell) holder.itemView;
            if (searchResultUsernames != null) {
                TLObject object = searchResultUsernames.get(position);
                if (object instanceof TLRPC.User) {
                    cell.setUser((TLRPC.User) object);
                } else if (object instanceof TLRPC.Chat) {
                    cell.setChat((TLRPC.Chat) object);
                }
            } else if (searchResultHashtags != null) {
                cell.setText(searchResultHashtags.get(position));
            } else if (searchResultSuggestions != null) {
                cell.setEmojiSuggestion(searchResultSuggestions.get(position));
            } else if (searchResultCommands != null) {
                cell.setBotCommand(searchResultCommands.get(position), searchResultCommandsHelp.get(position), searchResultCommandsUsers != null ? searchResultCommandsUsers.get(position) : null);
            }
            cell.setDivider(USE_DIVIDERS && (isReversed ? position > 0 : position < getItemCount() - 1));
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 2) {
            if (foundContextBot != null && foundContextBot.bot_inline_geo) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationProvider.start();
                } else {
                    onLocationUnavailable();
                }
            }
        }
    }

    public void doSomeStickersAction() {
        if (isStickers()) {
            if (mentionsStickersActionTracker == null) {
                mentionsStickersActionTracker = new EmojiView.ChooseStickerActionTracker(currentAccount, dialog_id, threadMessageId) {
                    @Override
                    public boolean isShown() {
                        return isStickers();
                    }
                };
                mentionsStickersActionTracker.checkVisibility();
            }
            mentionsStickersActionTracker.doSomeAction();
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setDialogId(long dialogId) {
        dialog_id = dialogId;
    }

    public void setUserOrChat(TLRPC.User user, TLRPC.Chat chat) {
        this.user = user;
        this.chat = chat;
    }

    public void setSearchInDailogs(boolean searchInDailogs) {
        this.searchInDailogs = searchInDailogs;
    }

    public void setAllowStickers(boolean allowStickers) {
        this.allowStickers = allowStickers;
    }

    public void setAllowBots(boolean allowBots) {
        this.allowBots = allowBots;
    }

    public void setAllowChats(boolean allowChats) {
        this.allowChats = allowChats;
    }
}
