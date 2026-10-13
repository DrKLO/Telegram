package org.telegram.messenger.multiaccount;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Searches chats and messages of all logged-in accounts at once. Every result carries the account
 * that found it, so it is always opened, read and answered through that account.
 */
public class UnifiedSearchController {

    public interface Listener {
        void onUnifiedSearchResults(String query);
    }

    public static final class ChatResult {
        public final int account;
        public final long dialogId;
        public final TLRPC.Dialog dialog;
        /** Found on the server (public peer), not in the account's chat list. */
        public final boolean global;

        ChatResult(int account, long dialogId, TLRPC.Dialog dialog, boolean global) {
            this.account = account;
            this.dialogId = dialogId;
            this.dialog = dialog;
            this.global = global;
        }
    }

    public static final class MessageResult {
        public final int account;
        public final MessageObject message;

        MessageResult(int account, MessageObject message) {
            this.account = account;
            this.message = message;
        }
    }

    private static final int LOCAL_LIMIT = 50;
    private static final int GLOBAL_LIMIT = 30;

    private final Listener listener;
    private String query;
    private int searchToken;
    private int pendingRequests;
    private final ArrayList<int[]> requests = new ArrayList<>();
    private final ArrayList<ChatResult> chats = new ArrayList<>();
    private final ArrayList<ChatResult> globalChats = new ArrayList<>();
    private final ArrayList<MessageResult> messages = new ArrayList<>();
    private Set<Integer> accountFilter;

    private final Runnable remoteSearch = this::startRemoteSearch;

    public UnifiedSearchController(Listener listener) {
        this.listener = listener;
    }

    public String getQuery() {
        return query;
    }

    public ArrayList<ChatResult> getChats() {
        ArrayList<ChatResult> result = new ArrayList<>(chats.size() + globalChats.size());
        result.addAll(chats);
        result.addAll(globalChats);
        return result;
    }

    public ArrayList<MessageResult> getMessages() {
        return messages;
    }

    public boolean isSearching() {
        return pendingRequests > 0;
    }

    public void search(String text, Set<Integer> accounts) {
        String normalized = text == null ? "" : text.trim();
        if (TextUtils.equals(normalized, query) && same(accounts, accountFilter)) {
            return;
        }
        cancel();
        query = normalized;
        accountFilter = accounts == null || accounts.isEmpty() ? null : new HashSet<>(accounts);
        if (TextUtils.isEmpty(query)) {
            notifyListener();
            return;
        }
        searchLocal();
        notifyListener();
        AndroidUtilities.runOnUIThread(remoteSearch, 300);
    }

    public void cancel() {
        AndroidUtilities.cancelRunOnUIThread(remoteSearch);
        for (int[] request : requests) {
            ConnectionsManager.getInstance(request[0]).cancelRequest(request[1], true);
        }
        requests.clear();
        searchToken++;
        pendingRequests = 0;
        chats.clear();
        globalChats.clear();
        messages.clear();
        query = null;
    }

    private ArrayList<Integer> accounts() {
        ArrayList<Integer> result = MultiAccountController.getActiveAccounts();
        if (accountFilter != null) {
            result.retainAll(accountFilter);
        }
        return result;
    }

    private void searchLocal() {
        String q = query.toLowerCase(Locale.ROOT);
        String translit = LocaleController.getInstance().getTranslitString(q);
        if (q.equals(translit) || translit != null && translit.isEmpty()) {
            translit = null;
        }
        ArrayList<ChatResult> found = new ArrayList<>();
        for (int account : accounts()) {
            MessagesController controller = MessagesController.getInstance(account);
            for (int folder = 0; folder <= 1; folder++) {
                ArrayList<TLRPC.Dialog> dialogs = controller.getDialogs(folder);
                for (int i = 0, n = dialogs.size(); i < n; i++) {
                    TLRPC.Dialog dialog = dialogs.get(i);
                    if (dialog == null || DialogObject.isFolderDialogId(dialog.id) || dialog instanceof TLRPC.TL_dialogFolder) {
                        continue;
                    }
                    if (matches(account, dialog.id, q, translit)) {
                        found.add(new ChatResult(account, dialog.id, dialog, false));
                    }
                }
            }
        }
        Collections.sort(found, (r1, r2) -> Integer.compare(r2.dialog.last_message_date, r1.dialog.last_message_date));
        if (found.size() > LOCAL_LIMIT) {
            found.subList(LOCAL_LIMIT, found.size()).clear();
        }
        chats.addAll(found);
    }

    private static boolean matches(int account, long dialogId, String q, String translit) {
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<String> names = new ArrayList<>(3);
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) {
            names.add(LocaleController.getString(org.telegram.messenger.R.string.SavedMessages));
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            if (encryptedChat != null) {
                addUserNames(names, controller.getUser(encryptedChat.user_id));
            }
        } else if (DialogObject.isUserDialog(dialogId)) {
            addUserNames(names, controller.getUser(dialogId));
        } else {
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null) {
                names.add(chat.title);
                String username = ChatObject.getPublicUsername(chat);
                if (username != null) {
                    names.add("@" + username);
                }
            }
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains(q) || translit != null && lower.contains(translit)) {
                return true;
            }
        }
        return false;
    }

    private static void addUserNames(ArrayList<String> names, TLRPC.User user) {
        if (user == null) {
            return;
        }
        names.add(UserObject.getUserName(user));
        names.add(ContactsController.formatName(user.last_name, user.first_name));
        String username = UserObject.getPublicUsername(user);
        if (username != null) {
            names.add("@" + username);
        }
    }

    private void startRemoteSearch() {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        final int token = searchToken;
        final String q = query;
        for (int account : accounts()) {
            searchMessages(account, q, token);
            if (q.length() >= 2) {
                searchPeers(account, q, token);
            }
        }
        notifyListener();
    }

    private void searchMessages(int account, String q, int token) {
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.q = q;
        req.limit = GLOBAL_LIMIT;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        pendingRequests++;
        int reqId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            ArrayList<MessageObject> objects = new ArrayList<>();
            TLRPC.messages_Messages res = response instanceof TLRPC.messages_Messages ? (TLRPC.messages_Messages) response : null;
            if (res != null) {
                LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
                LongSparseArray<TLRPC.Chat> chatsMap = new LongSparseArray<>();
                for (TLRPC.User user : res.users) {
                    users.put(user.id, user);
                }
                for (TLRPC.Chat chat : res.chats) {
                    chatsMap.put(chat.id, chat);
                }
                for (TLRPC.Message message : res.messages) {
                    MessageObject messageObject = new MessageObject(account, message, users, chatsMap, false, true);
                    messageObject.setQuery(q);
                    objects.add(messageObject);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (token != searchToken) {
                    return;
                }
                pendingRequests--;
                if (res != null) {
                    MessagesStorage.getInstance(account).putUsersAndChats(res.users, res.chats, true, true);
                    MessagesController.getInstance(account).putUsers(res.users, false);
                    MessagesController.getInstance(account).putChats(res.chats, false);
                    for (MessageObject messageObject : objects) {
                        long did = messageObject.getDialogId();
                        int deletedMax = MessagesController.getInstance(account).deletedHistory.get(did);
                        if (deletedMax != 0 && messageObject.getId() <= deletedMax) {
                            continue;
                        }
                        messages.add(new MessageResult(account, messageObject));
                    }
                    Collections.sort(messages, (m1, m2) -> {
                        int d = Integer.compare(m2.message.messageOwner.date, m1.message.messageOwner.date);
                        return d != 0 ? d : Integer.compare(m1.account, m2.account);
                    });
                }
                notifyListener();
            });
        });
        requests.add(new int[]{account, reqId});
    }

    private void searchPeers(int account, String q, int token) {
        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = q;
        req.limit = 20;
        pendingRequests++;
        int reqId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != searchToken) {
                return;
            }
            pendingRequests--;
            if (response instanceof TLRPC.TL_contacts_found) {
                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                MessagesController controller = MessagesController.getInstance(account);
                MessagesStorage.getInstance(account).putUsersAndChats(res.users, res.chats, true, true);
                controller.putUsers(res.users, false);
                controller.putChats(res.chats, false);
                ArrayList<TLRPC.Peer> peers = new ArrayList<>(res.my_results);
                peers.addAll(res.results);
                for (TLRPC.Peer peer : peers) {
                    long did = DialogObject.getPeerDialogId(peer);
                    if (did == 0 || containsChat(account, did)) {
                        continue;
                    }
                    TLRPC.Dialog dialog = controller.dialogs_dict.get(did);
                    if (dialog == null && (did > 0 ? controller.getUser(did) : controller.getChat(-did)) == null) {
                        continue;
                    }
                    globalChats.add(new ChatResult(account, did, dialog, dialog == null));
                }
            }
            notifyListener();
        }));
        requests.add(new int[]{account, reqId});
    }

    private boolean containsChat(int account, long dialogId) {
        for (ChatResult result : chats) {
            if (result.account == account && result.dialogId == dialogId) {
                return true;
            }
        }
        for (ChatResult result : globalChats) {
            if (result.account == account && result.dialogId == dialogId) {
                return true;
            }
        }
        return false;
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onUnifiedSearchResults(query);
        }
    }

    private static boolean same(Set<Integer> a, Set<Integer> b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty || bEmpty) {
            return aEmpty == bEmpty;
        }
        return a.equals(b);
    }
}
