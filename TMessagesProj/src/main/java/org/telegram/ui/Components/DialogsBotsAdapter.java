package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.database.sqlite.SQLiteStatement;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogsBotsAdapter extends UniversalAdapter {

    private final Context context;
    private final int currentAccount;
    private final int folderId;
    private final boolean showOnlyPopular;
    private final Theme.ResourcesProvider resourcesProvider;

    private final PopularBots popular;

    public final ArrayList<TLRPC.User> searchMine = new ArrayList<>();
    public final ArrayList<TLRPC.User> searchGlobal = new ArrayList<>();
    public final ArrayList<MessageObject> searchMessages = new ArrayList<>();

    public boolean expandedMyBots;
    public boolean expandedSearchBots;

    private final CharSequence infoText;

    public DialogsBotsAdapter(RecyclerListView listView, Context context, int currentAccount, int folderId, boolean showOnlyPopular, Theme.ResourcesProvider resourcesProvider) {
        super(listView, context, currentAccount, 0, true, null, resourcesProvider);
        super.fillItems = this::fillItems;
        this.context = context;
        this.currentAccount = currentAccount;
        this.folderId = folderId;
        this.resourcesProvider = resourcesProvider;
        this.showOnlyPopular = showOnlyPopular;
        this.popular = new PopularBots(currentAccount, () -> update(true));
        this.infoText = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.AppsTabInfo), () -> {
            final AlertDialog[] alert = new AlertDialog[1];
            SpannableStringBuilder text = AndroidUtilities.replaceTags(AndroidUtilities.replaceLinks(LocaleController.getString(R.string.AppsTabInfoText), resourcesProvider, () -> {
                if (alert[0] != null) {
                    alert[0].dismiss();
                }
            }));
            Matcher m = Pattern.compile("@([a-zA-Z0-9_-]+)").matcher(text);
            while (m.find()) {
                final String username = m.group(1);
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        if (alert[0] != null) {
                            alert[0].dismiss();
                        }
                        Browser.openUrl(context, "https://t.me/" + username);
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                    }
                }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            alert[0] = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(LocaleController.getString(R.string.AppsTabInfoTitle))
                .setMessage(text)
                .setPositiveButton(LocaleController.getString(R.string.AppsTabInfoButton), null)
                .show();
        }), true);
        update(false);
        MediaDataController.getInstance(currentAccount).loadHints(true);
    }

    private int topPeersStart, topPeersEnd;
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        HashSet<Long> uids = new HashSet<>();

        if (!TextUtils.isEmpty(query)) {
            ArrayList<TLRPC.User> foundBots = new ArrayList<>();
            foundBots.addAll(searchMine);
            foundBots.addAll(searchGlobal);
            if (!foundBots.isEmpty()) {
                if (foundBots.size() > 5 && (!searchMessages.isEmpty() && !showOnlyPopular)) {
                    items.add(UItem.asGraySection(getString(R.string.SearchApps), getString(expandedSearchBots ? R.string.ShowLess : R.string.ShowMore), this::toggleExpandedSearchBots));
                } else {
                    items.add(UItem.asGraySection(getString(R.string.SearchApps)));
                }
                int count = foundBots.size();
                if (!expandedSearchBots && (!searchMessages.isEmpty() && !showOnlyPopular))
                    count = Math.min(5, count);
                for (int i = 0; i < count; ++i) {
                    items.add(UItem.asProfileCell(foundBots.get(i)).withOpenButton(openBotCallback));
                }
            }
            if (!searchMessages.isEmpty() && !showOnlyPopular) {
                items.add(UItem.asGraySection(getString(R.string.SearchMessages)));
                for (MessageObject message : searchMessages) {
                    items.add(UItem.asSearchMessage(message));
                }
                if (hasMore) {
                    items.add(UItem.asFlicker(FlickerLoadingView.DIALOG_TYPE));
                }
            }
        } else {
            ArrayList<TLRPC.TL_topPeer> top_peers = MediaDataController.getInstance(currentAccount).webapps;
            ArrayList<TLRPC.User> top_peers_bots = new ArrayList<>();
            if (top_peers != null) {
                for (int i = 0; i < top_peers.size(); ++i) {
                    TLRPC.TL_topPeer peer = top_peers.get(i);
                    long dialogId = DialogObject.getPeerDialogId(peer.peer);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user == null || !user.bot) continue;
                    top_peers_bots.add(user);
                }
            }
            boolean hasAdded = false;
            topPeersStart = items.size();
            if (!top_peers_bots.isEmpty() && !showOnlyPopular) {
                if (top_peers_bots.size() > 5) {
                    items.add(UItem.asGraySection(getString(R.string.SearchAppsMine), getString(expandedMyBots ? R.string.ShowLess : R.string.ShowMore), this::toggleExpandedMyBots));
                } else {
                    items.add(UItem.asGraySection(getString(R.string.SearchAppsMine)));
                }
                for (int i = 0; i < top_peers_bots.size(); ++i) {
                    if (i >= 5 && !expandedMyBots) break;
                    final TLRPC.User user = top_peers_bots.get(i);
                    if (uids.contains(user.id)) continue;
                    uids.add(user.id);
                    items.add(UItem.asProfileCell(user).accent().withOpenButton(openBotCallback));
                }
            }
            uids.clear();
            topPeersEnd = items.size();
            if (!popular.bots.isEmpty()) {
                if (!showOnlyPopular) items.add(UItem.asGraySection(getString(R.string.SearchAppsPopular)));
                for (int i = 0; i < popular.bots.size(); ++i) {
                    final TLRPC.User user = popular.bots.get(i);
                    if (uids.contains(user.id)) continue;
                    uids.add(user.id);
                    items.add(UItem.asProfileCell(user).accent().red().withOpenButton(openBotCallback));
                    hasAdded = true;
                }
                if (popular.loading || !popular.endReached) {
                    items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                    items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                    items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                }
            } else if (popular.loading || !popular.endReached) {
                if (!showOnlyPopular) items.add(UItem.asFlicker(FlickerLoadingView.GRAY_SECTION));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            if (hasAdded) {
                items.add(UItem.asShadow(infoText));
            }
        }
    }

    private void toggleExpandedMyBots(View view) {
        expandedMyBots = !expandedMyBots;
        update(true);
    }

    private void toggleExpandedSearchBots(View view) {
        expandedSearchBots = !expandedSearchBots;
        update(true);
    }


    protected void hideKeyboard() {

    }

    public Object getTopPeerObject(int position) {
        if (position < topPeersStart || position >= topPeersEnd) {
            return false;
        }
        return getObject(position);
    }

    public Object getObject(int position) {
        UItem item = getItem(position);
        return item != null ? item.object : null;
    }

    public boolean loadingMessages;
    public boolean loadingBots;

    private boolean hasMore;
    private int allCount;
    private int nextRate;
    private int searchBotsId;
    public String query;
    private void searchMessages(boolean next) {
        loadingMessages = true;
        final int searchId = ++searchBotsId;
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.broadcasts_only = false;
        if (folderId != 0) {
            req.flags |= 1;
            req.folder_id = folderId;
        }
        req.q = this.query;
        req.limit = 25;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        if (next && !searchMessages.isEmpty()) {
            MessageObject lastMessage = searchMessages.get(searchMessages.size() - 1);
            req.offset_rate = nextRate;
            req.offset_id = lastMessage.getId();
            if (lastMessage.messageOwner.peer_id == null) {
                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            } else {
                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(lastMessage.messageOwner.peer_id);
            }
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (searchId != searchBotsId || !TextUtils.equals(req.q, this.query)) return;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (searchId != searchBotsId || !TextUtils.equals(req.q, this.query)) return;
                loadingMessages = false;
                if (!next) {
                    searchMessages.clear();
                }
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages response = (TLRPC.messages_Messages) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);

                    for (TLRPC.Message message : response.messages) {
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                        messageObject.setQuery(query);
                        searchMessages.add(messageObject);
                    }

                    hasMore = response instanceof TLRPC.TL_messages_messagesSlice;
                    allCount = Math.max(searchMessages.size(), response.count);
                    nextRate = response.next_rate;
                }
                update(true);
            }));
        }, next ? 800 : 0);

        if (!next) {
            loadingBots = true;
            TLRPC.TL_contacts_search req2 = new TLRPC.TL_contacts_search();
            req2.limit = 30;
            req2.q = this.query;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.equals(req2.q, this.query) || TextUtils.isEmpty(this.query)) return;

                loadingBots = false;
                TLRPC.TL_contacts_found response = null;
                if (res instanceof TLRPC.TL_contacts_found) {
                    response = (TLRPC.TL_contacts_found) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);
                }

                HashSet<Long> userIds = new HashSet<>();

                searchMine.clear();
                if (response != null) {
                    for (TLRPC.Peer peer : response.my_results) {
                        if (!(peer instanceof TLRPC.TL_peerUser)) continue;
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
                        if (user == null || !user.bot) continue;
                        if (userIds.contains(user.id))
                            continue;
                        userIds.add(user.id);
                        searchMine.add(user);
                    }
                }

//                searchRecommendedChannels.clear();
//                String q = this.query.toLowerCase(), qT = AndroidUtilities.translitSafe(q);
//                MessagesController.ChannelRecommendations recommendations = MessagesController.getInstance(currentAccount).getCachedChannelRecommendations(0);
//                if (recommendations != null && !recommendations.chats.isEmpty()) {
//                    for (TLRPC.Chat chat : recommendations.chats) {
//                        if (chat == null)
//                            continue;
//                        if (!ChatObject.isChannelAndNotMegaGroup(chat))
//                            continue;
//                        TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
//                        if (!(ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat))))
//                            continue;
//                        String t = chat.title.toLowerCase(), tT = AndroidUtilities.translitSafe(t);
//                        if (
//                                t.startsWith(q) || t.contains(" " + q) ||
//                                        tT.startsWith(qT) || tT.contains(" " + qT)
//                        ) {
//                            if (chatIds.contains(chat.id))
//                                continue;
//                            chatIds.add(chat.id);
//                            searchRecommendedChannels.add(chat);
//                        }
//                    }
//                }

                searchGlobal.clear();
                if (response != null) {
                    for (TLRPC.Peer peer : response.results) {
                        if (!(peer instanceof TLRPC.TL_peerUser)) continue;
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
                        if (user == null || !user.bot) continue;
                        if (userIds.contains(user.id))
                            continue;
                        userIds.add(user.id);
                        searchGlobal.add(user);
                    }
                }

                if (listView != null) {
                    listView.scrollToPosition(0);
                }
                update(true);
            }));
        }
    }

    private Runnable searchMessagesRunnable = () -> searchMessages(false);
    public void search(String query) {
        if (TextUtils.equals(query, this.query)) return;
        this.query = query;
        AndroidUtilities.cancelRunOnUIThread(searchMessagesRunnable);
        if (TextUtils.isEmpty(this.query)) {
            searchMessages.clear();
            update(true);
            searchBotsId++;
            loadingMessages = false;
            loadingBots = false;
            hasMore = false;
            nextRate = 0;
            if (listView != null) {
                listView.scrollToPosition(0);
            }
            return;
        }

        searchMessages.clear();

        AndroidUtilities.runOnUIThread(searchMessagesRunnable, 1000);
        loadingMessages = true;
        loadingBots = true;

        update(true);

        if (listView != null) {
            listView.scrollToPosition(0);
        }
    }

    public void searchMore() {
        if (!hasMore || loadingMessages || TextUtils.isEmpty(this.query)) {
            return;
        }
        searchMessages(true);
    }

    private boolean first = true;
    public void checkBottom() {
        if (!TextUtils.isEmpty(this.query)) {
            if (hasMore && !loadingMessages && seesLoading()) {
                searchMore();
            }
        } else {
            if (first || seesLoading()) {
                popular.load();
            }
        }
        first = false;
    }

    public boolean seesLoading() {
        if (listView == null) return false;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                return true;
            }
        }
        return false;
    }

    public boolean atTop() {
        if (listView == null) return false;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == 0)
                return true;
        }
        return false;
    }

    public static class PopularBots {

        private final int currentAccount;
        private final Runnable whenUpdated;
        public PopularBots(int currentAccount, Runnable whenUpdated) {
            this.currentAccount = currentAccount;
            this.whenUpdated = whenUpdated;
        }

        public boolean loading;
        private boolean cacheLoaded;
        private boolean endReached;

        private long cacheTime;
        private String lastOffset;
        public final ArrayList<TLRPC.User> bots = new ArrayList<>();

        private void loadCache(Runnable whenDone) {
            final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            storage.getStorageQueue().postRunnable(() -> {
                long time = 0;
                String offset = null;
                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<Long> userIds = new ArrayList<>();

                final SQLiteDatabase db = storage.getDatabase();
                SQLiteCursor cursor = null;
                try {
                    cursor = db.queryFinalized("SELECT uid, time, offset FROM popular_bots ORDER BY pos");
                    while (cursor.next()) {
                        userIds.add(cursor.longValue(0));
                        time = Math.max(time, cursor.longValue(1));
                        offset = cursor.stringValue(2);
                    }
                    cursor.dispose();
                    ArrayList<TLRPC.User> usersByIds = storage.getUsers(userIds);
                    if (usersByIds != null) {
                        for (long userId : userIds) {
                            TLRPC.User user = null;
                            for (TLRPC.User u : usersByIds) {
                                if (u != null && u.id == userId) {
                                    user = u;
                                    break;
                                }
                            }
                            if (user != null) {
                                users.add(user);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                final long finalTime = time;
                final String finalOffset = offset;
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getInstance(currentAccount).putUsers(users, true);

                    bots.addAll(users);
                    this.cacheTime = finalTime;
                    this.lastOffset = finalOffset;
                    this.endReached = TextUtils.isEmpty(finalOffset);
                    this.cacheLoaded = true;

                    whenDone.run();
                });
            });
        }

        private boolean savingCache = false;
        private void saveCache() {
            if (savingCache) return;
            savingCache = true;

            final long time = cacheTime;
            final String offset = lastOffset == null ? "" : lastOffset;
            final ArrayList<Long> ids = new ArrayList<>();
            for (int i = 0; i < bots.size(); ++i) {
                ids.add(bots.get(i).id);
            }

            final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            storage.getStorageQueue().postRunnable(() -> {
                final SQLiteDatabase db = storage.getDatabase();
                SQLitePreparedStatement state = null;
                try {
                    db.executeFast("DELETE FROM popular_bots").stepThis().dispose();
                    state = db.executeFast("REPLACE INTO popular_bots VALUES(?, ?, ?, ?)");
                    for (int i = 0; i < ids.size(); i++) {
                        state.requery();
                        state.bindLong(1, ids.get(i));
                        state.bindLong(2, time);
                        state.bindString(3, offset);
                        state.bindInteger(4, i);
                        state.step();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    savingCache = false;
                });
            });
        }

        public void load() {
            if (loading || endReached) return;
            loading = true;

            if (!cacheLoaded) {
                loadCache(() -> {
                    loading = false;
                    whenUpdated.run();

                    if (bots.isEmpty() || System.currentTimeMillis() - cacheTime > 60 * 60 * 1000) {
                        bots.clear();
                        endReached = false;
                        lastOffset = null;
                        load();
                    }
                });
                return;
            }

            final TL_bots.getPopularAppBots req = new TL_bots.getPopularAppBots();
            req.limit = 20;
            req.offset = lastOffset == null ? "" : lastOffset;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_bots.popularAppBots) {
                    TL_bots.popularAppBots r = (TL_bots.popularAppBots) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(r.users, null, false, true);
                    bots.addAll(r.users);
                    lastOffset = r.next_offset;
                    endReached = lastOffset == null;
                    cacheTime = System.currentTimeMillis();
                    saveCache();
                    loading = false;
                    whenUpdated.run();
                } else {
                    lastOffset = null;
                    endReached = true;
                    loading = false;
                    whenUpdated.run();
                }
            }));
        }
    }

    private final Utilities.Callback<TLRPC.User> openBotCallback = this::openBot;
    public void openBot(TLRPC.User user) {
        MessagesController.getInstance(currentAccount).openApp(user, 0);
    }


}
