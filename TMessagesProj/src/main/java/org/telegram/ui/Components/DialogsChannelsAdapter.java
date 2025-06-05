package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.UserInfoActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DialogsChannelsAdapter extends UniversalAdapter {

    private final Context context;
    private final int currentAccount;
    private final int folderId;
    private final Theme.ResourcesProvider resourcesProvider;

    public final ArrayList<MessageObject> messages = new ArrayList<>();
    public final ArrayList<TLRPC.Chat> searchMyChannels = new ArrayList<>();
    public final ArrayList<TLRPC.Chat> searchRecommendedChannels = new ArrayList<>();
    public final ArrayList<TLRPC.Chat> searchChannels = new ArrayList<>();
    public boolean expandedSearchChannels;

    public boolean expandedMyChannels;
    public final ArrayList<TLRPC.Chat> myChannels = new ArrayList<>();

    public DialogsChannelsAdapter(RecyclerListView listView, Context context, int currentAccount, int folderId, Theme.ResourcesProvider resourcesProvider) {
        super(listView, context, currentAccount, 0, null, resourcesProvider);
        super.fillItems = this::fillItems;
        this.context = context;
        this.currentAccount = currentAccount;
        this.folderId = folderId;
        this.resourcesProvider = resourcesProvider;
        update(false);
    }

    public void updateMyChannels() {
        ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
        for (TLRPC.Dialog d : dialogs) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-d.id);
            if (chat == null || !ChatObject.isChannelAndNotMegaGroup(chat) || !ChatObject.isPublic(chat) || ChatObject.isNotInChat(chat)) continue;
            channels.add(chat);
            if (channels.size() >= 100)
                break;
        }
        myChannels.clear();
        myChannels.addAll(channels);
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (TextUtils.isEmpty(query)) {
            if (myChannels != null && !myChannels.isEmpty()) {
                if (myChannels.size() > 5) {
                    items.add(UItem.asGraySection(getString(R.string.SearchMyChannels), getString(expandedMyChannels ? R.string.ShowLess : R.string.ShowMore), this::toggleExpandedMyChannels));
                } else {
                    items.add(UItem.asGraySection(getString(R.string.SearchMyChannels)));
                }
                int count = myChannels.size();
                if (!expandedMyChannels)
                    count = Math.min(5, count);
                for (int i = 0; i < count; ++i) {
                    items.add(UItem.asProfileCell(myChannels.get(i)).withUsername(true));
                }
            }
            MessagesController.ChannelRecommendations recommendations = MessagesController.getInstance(currentAccount).getCachedChannelRecommendations(0);
            if (recommendations != null) {
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                for (TLObject obj : recommendations.chats) {
                    if (obj instanceof TLRPC.Chat) {
                        final TLRPC.Chat chat = (TLRPC.Chat) obj;
                        TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                        if (ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat)))
                            chats.add(chat);
                    }
                }
                if (!chats.isEmpty()) {
                    items.add(UItem.asGraySection(getString(R.string.SearchRecommendedChannels)));
                }
                for (TLRPC.Chat chat : chats) {
                    items.add(UItem.asProfileCell(chat));
                }
            } else {
                items.add(UItem.asFlicker(FlickerLoadingView.GRAY_SECTION));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
        } else {
            ArrayList<TLRPC.Chat> foundChannels = new ArrayList<>();
            for (TLRPC.Chat chat : searchMyChannels) {
                TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                if (ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat)))
                    foundChannels.add(chat);
            }
            for (TLRPC.Chat chat : searchRecommendedChannels) {
                TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                if (ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat)))
                    foundChannels.add(chat);
            }
            for (TLRPC.Chat chat : searchChannels) {
                TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                if (ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat)))
                    foundChannels.add(chat);
            }
            if (!foundChannels.isEmpty()) {
                if (foundChannels.size() > 5 && !messages.isEmpty()) {
                    items.add(UItem.asGraySection(getString(R.string.SearchChannels), getString(expandedSearchChannels ? R.string.ShowLess : R.string.ShowMore), this::toggleExpandedSearchChannels));
                } else {
                    items.add(UItem.asGraySection(getString(R.string.SearchChannels)));
                }
                int count = foundChannels.size();
                if (!expandedSearchChannels && !messages.isEmpty())
                    count = Math.min(5, count);
                for (int i = 0; i < count; ++i) {
                    items.add(UItem.asProfileCell(foundChannels.get(i)));
                }
            }
            if (!messages.isEmpty()) {
                items.add(UItem.asGraySection(getString(R.string.SearchMessages)));
                for (MessageObject message : messages) {
                    items.add(UItem.asSearchMessage(message));
                }
                if (hasMore) {
                    items.add(UItem.asFlicker(FlickerLoadingView.DIALOG_TYPE));
                }
            }
        }
    }

    public void toggleExpandedSearchChannels(View view) {
        expandedSearchChannels = !expandedSearchChannels;
        update(true);
        if (expandedSearchChannels) {
            hideKeyboard();
        }
    }

    public void toggleExpandedMyChannels(View view) {
        expandedMyChannels = !expandedMyChannels;
        update(true);
        if (expandedMyChannels) {
            hideKeyboard();
        }
    }

    protected void hideKeyboard() {

    }

    public TLRPC.Chat getChat(int position) {
        UItem item = getItem(position);
        return item != null && item.object instanceof TLRPC.Chat ? (TLRPC.Chat) item.object : null;
    }

    public Object getObject(int position) {
        UItem item = getItem(position);
        return item != null ? item.object : null;
    }

    public boolean loadingMessages;
    public boolean loadingChannels;

    private boolean hasMore;
    private int allCount;
    private int nextRate;
    private int searchChannelsId;
    public String query;
    private void searchMessages(boolean next) {
        loadingMessages = true;
        final int searchId = ++searchChannelsId;
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.broadcasts_only = true;
        if (folderId != 0) {
            req.flags |= 1;
            req.folder_id = folderId;
        }
        req.q = this.query;
        req.limit = 25;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        if (next && !messages.isEmpty()) {
            MessageObject lastMessage = messages.get(messages.size() - 1);
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
            if (searchId != searchChannelsId || !TextUtils.equals(req.q, this.query)) return;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (searchId != searchChannelsId || !TextUtils.equals(req.q, this.query)) return;
                loadingMessages = false;
                if (!next) {
                    messages.clear();
                }
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages response = (TLRPC.messages_Messages) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);

                    for (TLRPC.Message message : response.messages) {
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                        messageObject.setQuery(query);
                        messages.add(messageObject);
                    }

                    hasMore = response instanceof TLRPC.TL_messages_messagesSlice;
                    allCount = Math.max(messages.size(), response.count);
                    nextRate = response.next_rate;
                }
                update(true);
            }));
        }, next ? 800 : 0);

        if (!next) {
            loadingChannels = true;
            TLRPC.TL_contacts_search req2 = new TLRPC.TL_contacts_search();
            req2.limit = 20;
            req2.q = this.query;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.equals(req2.q, this.query) || TextUtils.isEmpty(this.query)) return;

                loadingChannels = false;
                TLRPC.TL_contacts_found response = null;
                if (res instanceof TLRPC.TL_contacts_found) {
                    response = (TLRPC.TL_contacts_found) res;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                    MessagesController.getInstance(currentAccount).putChats(response.chats, false);
                }

                HashSet<Long> chatIds = new HashSet<>();

                searchMyChannels.clear();
                if (response != null) {
                    for (TLRPC.Peer peer : response.my_results) {
                        if (!(peer instanceof TLRPC.TL_peerChannel)) continue;
                        TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(peer.channel_id);
                        if (channel == null) continue;
                        if (!ChatObject.isChannelAndNotMegaGroup(channel))
                            continue;
                        if (chatIds.contains(channel.id))
                            continue;
                        chatIds.add(channel.id);
                        searchMyChannels.add(channel);
                    }
                }

                searchRecommendedChannels.clear();
                String q = this.query.toLowerCase(), qT = AndroidUtilities.translitSafe(q);
                MessagesController.ChannelRecommendations recommendations = MessagesController.getInstance(currentAccount).getCachedChannelRecommendations(0);
                if (recommendations != null && !recommendations.chats.isEmpty()) {
                    for (TLObject obj : recommendations.chats) {
                        if (obj instanceof TLRPC.Chat) {
                            final TLRPC.Chat chat = (TLRPC.Chat) obj;
                            if (!ChatObject.isChannelAndNotMegaGroup(chat))
                                continue;
                            TLRPC.Chat localChat = MessagesController.getInstance(currentAccount).getChat(chat.id);
                            if (!(ChatObject.isNotInChat(chat) && (localChat == null || ChatObject.isNotInChat(localChat))))
                                continue;
                            String t = chat.title.toLowerCase(), tT = AndroidUtilities.translitSafe(t);
                            if (
                                t.startsWith(q) || t.contains(" " + q) ||
                                tT.startsWith(qT) || tT.contains(" " + qT)
                            ) {
                                if (chatIds.contains(chat.id))
                                    continue;
                                chatIds.add(chat.id);
                                searchRecommendedChannels.add(chat);
                            }
                        }
                    }
                }

                searchChannels.clear();
                if (response != null) {
                    for (TLRPC.Peer peer : response.results) {
                        if (!(peer instanceof TLRPC.TL_peerChannel)) continue;
                        TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(peer.channel_id);
                        if (channel == null) continue;
                        if (!ChatObject.isChannelAndNotMegaGroup(channel))
                            continue;
                        if (chatIds.contains(channel.id))
                            continue;
                        chatIds.add(channel.id);
                        searchChannels.add(channel);
                    }
                }

                update(true);
            }));
        }
    }

    private final Runnable searchMessagesRunnable = () -> searchMessages(false);
    public void search(String query) {
        updateMyChannels();
        if (TextUtils.equals(query, this.query)) return;
        this.query = query;
        AndroidUtilities.cancelRunOnUIThread(searchMessagesRunnable);
        if (TextUtils.isEmpty(this.query)) {
            messages.clear();
            searchChannels.clear();
            searchRecommendedChannels.clear();
            searchMyChannels.clear();
            update(true);
            searchChannelsId++;
            loadingMessages = false;
            loadingChannels = false;
            hasMore = false;
            nextRate = 0;
            if (listView != null) {
                listView.scrollToPosition(0);
            }
            return;
        }

        messages.clear();
        searchChannels.clear();
        searchRecommendedChannels.clear();
        searchMyChannels.clear();

        AndroidUtilities.runOnUIThread(searchMessagesRunnable, 1000);
        loadingMessages = true;
        loadingChannels = true;

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

    public ArrayList<TLRPC.Chat> getNextChannels(int position) {
        ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        for (int pos = position + 1; pos < getItemCount(); ++pos) {
            TLRPC.Chat chat = getChat(pos);
            if (chat == null) continue;
            channels.add(chat);
        }
        return channels;
    }

    public void checkBottom() {
        if (!hasMore || loadingMessages || TextUtils.isEmpty(this.query) || listView == null)
            return;
        if (seesLoading()) {
            searchMore();
        }
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
}
