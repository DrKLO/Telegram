package org.telegram.messenger;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class SavedMessagesController {

    private final int currentAccount;
    private final long forumDialogId;
    public boolean unsupported;

    public SavedMessagesController(int account) {
        this(account, 0);
    }

    public SavedMessagesController(int account, long forumDialogId) {
        this.currentAccount = account;
        this.forumDialogId = forumDialogId;
        unsupported = MessagesController.getMainSettings(currentAccount).getBoolean("savedMessagesUnsupported", true);
    }

    public void cleanup() {
        cachedDialogs.clear();
        loadedDialogs.clear();
        dialogsLoaded = false;
        dialogsCount = 0;
        dialogsCountHidden = 0;
        dialogsEndReached = false;
        loadedCache = true;
        deleteCache();
        unsupported = true;
        MessagesController.getMainSettings(currentAccount).edit().remove("savedMessagesUnsupported").apply();
    }

    private ArrayList<SavedDialog> cachedDialogs = new ArrayList<>();

    private boolean dialogsLoading, dialogsLoaded;
    public boolean dialogsEndReached;
    private int dialogsCount;
    private int dialogsCountHidden;
    private ArrayList<SavedDialog> loadedDialogs = new ArrayList<>();

    public ArrayList<SavedDialog> allDialogs = new ArrayList<>();
    private void updateAllDialogs(boolean notify) {
        allDialogs.clear();
        HashSet<Long> ids = new HashSet<>();
        for (int i = 0; i < cachedDialogs.size(); ++i) {
            SavedDialog d = cachedDialogs.get(i);
            if (d.pinned && !ids.contains(d.dialogId) && !d.isHidden()) {
                allDialogs.add(d);
                ids.add(d.dialogId);
            }
        }
        for (int i = 0; i < loadedDialogs.size(); ++i) {
            SavedDialog d = loadedDialogs.get(i);
            if (d.pinned && !ids.contains(d.dialogId) && !d.isHidden()) {
                allDialogs.add(d);
                ids.add(d.dialogId);
            }
        }
        ArrayList<SavedDialog> dialogs = new ArrayList<>();
        for (int i = 0; i < loadedDialogs.size(); ++i) {
            SavedDialog d = loadedDialogs.get(i);
            if (!ids.contains(d.dialogId) && !d.isHidden()) {
                dialogs.add(d);
                ids.add(d.dialogId);
            }
        }
        if (!dialogsEndReached) {
            for (int i = 0; i < cachedDialogs.size(); ++i) {
                SavedDialog d = cachedDialogs.get(i);
                if (!ids.contains(d.dialogId) && !d.isHidden()) {
                    dialogs.add(d);
                    ids.add(d.dialogId);
                }
            }
        }
        Collections.sort(dialogs, (d1, d2) -> d2.getDate() - d1.getDate());
        allDialogs.addAll(dialogs);
        if (notify) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.savedMessagesDialogsUpdate, forumDialogId);
            if (!hasDialogs() && MessagesController.getInstance(currentAccount).savedViewAsChats) {
                MessagesController.getInstance(currentAccount).setSavedViewAs(false);
            }
        }
    }

    public boolean isLoading() {
        return dialogsLoading;
    }

    public int getAllCount() {
        if (dialogsEndReached) {
            return allDialogs.size();
        }
        if (dialogsLoaded) {
            return dialogsCount - dialogsCountHidden;
        }
        return cachedDialogs.size();
    }

    public boolean hasDialogs() {
        if (getAllCount() <= 0) return false;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (allDialogs.size() == 1 && allDialogs.get(0).dialogId == selfId) {
            return false;
        }
        return true;
    }

    public int getLoadedCount() {
        return loadedDialogs.size();
    }

    public int getPinnedCount() {
        int count = 0;
        for (int i = 0; i < allDialogs.size(); ++i) {
            if (allDialogs.get(i).pinned) {
                count++;
            }
        }
        return count;
    }

    public SavedDialog findSavedDialog(long did) {
        return findSavedDialog(allDialogs, did);
    }

    public SavedDialog findSavedDialog(ArrayList<SavedDialog> dialogs, long did) {
        for (int i = 0; i < dialogs.size(); ++i) {
            SavedDialog d = dialogs.get(i);
            if (d.dialogId == did) {
                return d;
            }
        }
        return null;
    }

    public ArrayList<SavedDialog> searchDialogs(String q) {
        ArrayList<SavedDialog> result = new ArrayList<>();
        if (TextUtils.isEmpty(q)) return result;
        String lq = AndroidUtilities.translitSafe(q.toLowerCase());
        for (int i = 0; i < allDialogs.size(); ++i) {
            SavedDialog d = allDialogs.get(i);
            final String name;
            String name2 = null;
            if (d.dialogId == UserObject.ANONYMOUS) {
                name = LocaleController.getString(R.string.AnonymousForward);
            } else if (d.dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                name = LocaleController.getString(R.string.MyNotes);
                name2 = LocaleController.getString(R.string.SavedMessages);
            } else if (d.dialogId >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(d.dialogId);
                name = UserObject.getUserName(user);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-d.dialogId);
                name = chat != null ? chat.title : "";
            }
            if (name == null) continue;
            String lname = AndroidUtilities.translitSafe(name.toLowerCase());
            if (lname.startsWith(lq) || lname.contains(" " + lq)) {
                result.add(d);
            } else if (name2 != null) {
                lname = AndroidUtilities.translitSafe(name2.toLowerCase());
                if (lname.startsWith(lq) || lname.contains(" " + lq)) {
                    result.add(d);
                }
            }
        }
        return result;
    }

    public int getMessagesCount(long dialogId) {
        for (int i = 0; i < allDialogs.size(); ++i) {
            SavedDialog d = allDialogs.get(i);
            if (d.dialogId == dialogId)
                return d.messagesCount;
        }
        return 0;
    }

    public boolean containsDialog(long dialogId) {
        for (int i = 0; i < allDialogs.size(); ++i) {
            SavedDialog d = allDialogs.get(i);
            if (d.dialogId == dialogId)
                return true;
        }
        return false;
    }

    public void preloadDialogs(boolean cache) {
        if (!dialogsLoaded) {
            loadDialogs(cache);
        }
    }

    public void loadDialogs(boolean onlyCache) {
        loadingCacheOnly = onlyCache;
        if (dialogsLoading || dialogsEndReached || loadingCache) {
            return;
        }
        if (!loadedCache) {
            loadCache(() -> loadDialogs(false));
            return;
        } else if (onlyCache) return;
        dialogsLoading = true;

        TLRPC.TL_messages_getSavedDialogs req = new TLRPC.TL_messages_getSavedDialogs();
        SavedDialog lastDialog = loadedDialogs.isEmpty() ? null : loadedDialogs.get(loadedDialogs.size() - 1);

        if (lastDialog != null) {
            req.offset_id = lastDialog.top_message_id;
            req.offset_date = lastDialog.getDate();
            req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(lastDialog.dialogId);
        } else {
            req.offset_id = Integer.MAX_VALUE;
            req.offset_date = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        req.limit = 20;
        if (forumDialogId != 0) {
            req.flags |= 2;
            req.parent_peer = MessagesController.getInstance(currentAccount).getInputPeer(forumDialogId);
        }

        final ArrayList<SavedDialog> expectedDialogs = new ArrayList<>();
        expectedDialogs.addAll(allDialogs.subList(
            Math.min(loadedDialogs.size(), allDialogs.size()),
            Math.min(loadedDialogs.size() + req.limit, allDialogs.size())
        ));
        for (int i = 0; i < expectedDialogs.size(); ++i) {
            SavedDialog d = expectedDialogs.get(i);
            req.hash = MediaDataController.calcHash(req.hash, d.pinned ? 1 : 0);
            req.hash = MediaDataController.calcHash(req.hash, Math.abs(d.dialogId));
            req.hash = MediaDataController.calcHash(req.hash, d.top_message_id);
            req.hash = MediaDataController.calcHash(req.hash, d.getDate());
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            final boolean wasUnsupported = unsupported;
            if (res instanceof TLRPC.TL_messages_savedDialogs) {
                dialogsLoaded = true;
                TLRPC.TL_messages_savedDialogs r = (TLRPC.TL_messages_savedDialogs) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(r.users, r.chats, true, true);
                MessagesStorage.getInstance(currentAccount).putMessages(r.messages, false, true, false, 0, false, ChatActivity.MODE_SAVED, 0);
                for (int i = 0; i < r.dialogs.size(); ++i) {
                    SavedDialog d = SavedDialog.fromTL(currentAccount, r.dialogs.get(i), r.messages, forumDialogId == 0);
                    for (int j = 0; j < cachedDialogs.size(); ++j) {
                        if (cachedDialogs.get(j).dialogId == d.dialogId) {
                            d.messagesCount = cachedDialogs.get(j).messagesCount;
                            cachedDialogs.get(j).pinned = d.pinned;
                            break;
                        }
                    }
                    boolean found = false;
                    for (int j = 0; j < loadedDialogs.size(); ++j) {
                        if (loadedDialogs.get(j).dialogId == d.dialogId) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        loadedDialogs.add(d);
                        if (d.isHidden())
                            dialogsCountHidden++;
                    }
                }
                dialogsEndReached = true;
                dialogsCount = r.dialogs.size();
                updateAllDialogs(true);
                saveCacheSchedule();
                unsupported = false;
            } else if (res instanceof TLRPC.TL_messages_savedDialogsSlice) {
                dialogsLoaded = true;
                TLRPC.TL_messages_savedDialogsSlice r = (TLRPC.TL_messages_savedDialogsSlice) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(r.users, r.chats, true, true);
                MessagesStorage.getInstance(currentAccount).putMessages(r.messages, false, true, false, 0, false, ChatActivity.MODE_SAVED, 0);
                for (int i = 0; i < r.dialogs.size(); ++i) {
                    SavedDialog d = SavedDialog.fromTL(currentAccount, r.dialogs.get(i), r.messages, forumDialogId == 0);
                    for (int j = 0; j < cachedDialogs.size(); ++j) {
                        if (cachedDialogs.get(j).dialogId == d.dialogId) {
                            d.messagesCount = cachedDialogs.get(j).messagesCount;
                            cachedDialogs.get(j).pinned = d.pinned;
                            break;
                        }
                    }
                    boolean found = false;
                    for (int j = 0; j < loadedDialogs.size(); ++j) {
                        if (loadedDialogs.get(j).dialogId == d.dialogId) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        loadedDialogs.add(d);
                        if (d.isHidden())
                            dialogsCountHidden++;
                    }
                }
                dialogsCount = r.count;
                dialogsEndReached = getPinnedCount() + loadedDialogs.size() >= dialogsCount || r.dialogs.size() == 0;
                updateAllDialogs(true);
                saveCacheSchedule();
                unsupported = false;
            } else if (res instanceof TLRPC.TL_messages_savedDialogsNotModified) {
                dialogsLoaded = true;
                loadedDialogs.addAll(expectedDialogs);
                dialogsCount = ((TLRPC.TL_messages_savedDialogsNotModified) res).count;
                dialogsCountHidden = 0;
                for (int i = 0; i < expectedDialogs.size(); ++i) {
                    if (expectedDialogs.get(i).isHidden()) {
                        dialogsCountHidden++;
                    }
                }

                final boolean oldDialogsEndReached = dialogsEndReached;
                dialogsEndReached = loadedDialogs.size() >= dialogsCount;
                unsupported = false;
                if (dialogsEndReached && !oldDialogsEndReached) {
                    updateAllDialogs(true); // just for notify about dialogsEndReached = true
                }
            } else if (err != null) {
                dialogsLoaded = true;
                if ("SAVED_DIALOGS_UNSUPPORTED".equals(err.text)) {
                    unsupported = true;
                }
            }
            if (unsupported != wasUnsupported) {
                MessagesController.getMainSettings(currentAccount).edit().putBoolean("savedMessagesUnsupported", unsupported).apply();
            }

            if (forumDialogId != 0 && dialogsEndReached) {
                UserConfig.getInstance(currentAccount).getPreferences().edit().putBoolean("topics_end_reached_" + -forumDialogId, true).apply();
            }

            dialogsLoading = false;
        }));
    }

    public boolean updateSavedDialogs(ArrayList<TLRPC.Message> inputMessages) {
        ///
        if (inputMessages == null) {
            return false;
        }
        LongSparseArray<TLRPC.Message> messages = new LongSparseArray<>();
        LongSparseArray<Integer> messagesCount = new LongSparseArray<>();
        HashSet<Long> dialogsCountToCheck = new HashSet<>();
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        for (int i = 0; i < inputMessages.size(); ++i) {
            TLRPC.Message message = inputMessages.get(i);
            long dialogId = MessageObject.getSavedDialogId(self, message);
            if (dialogId != self && (message.id < 0 || message.send_state != 0 && message.fwd_from != null)) {
                // we might not know does user privacy hide fwd_from.from_id
                continue;
            }
            TLRPC.Message existingMessage = messages.get(dialogId);
            if (existingMessage == null || existingMessage.id < message.id) {
                messages.put(dialogId, message);
            }
            Integer count = messagesCount.get(dialogId);
            messagesCount.put(dialogId, (count == null ? 0 : count) + 1);
        }

        boolean changed = false;
        for (int i = 0; i < messages.size(); ++i) {
            long dialogId = messages.keyAt(i);
            TLRPC.Message message = messages.valueAt(i);
            Integer newMessagesCount = messagesCount.get(dialogId);
            if (forumDialogId != 0 && MessageObject.getMonoForumTopicId(message) == 0) {
                continue;
            }
            boolean found = false;
            for (int j = 0; j < cachedDialogs.size(); ++j) {
                SavedDialog d = cachedDialogs.get(j);
                if (d.dialogId == dialogId) {
                    found = true;
                    if (d.top_message_id < message.id || message.id < 0 && message.date > d.getDate()) {
                        changed = true;
                        if (d.top_message_id < message.id) {
                            int count = 0;
                            for (int k = 0; k < inputMessages.size(); ++k) {
                                if (inputMessages.get(k).id > d.top_message_id) {
                                    count++;
                                }
                            }
                            d.messagesCount += count;
                        }
                        d.message = new MessageObject(currentAccount, message, false, false);
                        d.top_message_id = d.message.getId();
                    }
                    newMessagesCount = newMessagesCount != null ?
                        Math.max(newMessagesCount, d.messagesCount) :
                        d.messagesCount;
                    break;
                }
            }
            if (!found) {
                SavedDialog d = SavedDialog.fromMessage(currentAccount, message, forumDialogId == 0);
                if (newMessagesCount != null) {
                    d.messagesCount = newMessagesCount;
                }
                cachedDialogs.add(d);
                changed = true;
            }
            found = false;
            for (int j = 0; j < loadedDialogs.size(); ++j) {
                SavedDialog d = loadedDialogs.get(j);
                if (d.dialogId == dialogId) {
                    found = true;
                    if (d.top_message_id < message.id || message.id < 0 && message.date > d.getDate()) {
                        changed = true;
                        if (d.top_message_id < message.id) {
                            int count = 0;
                            for (int k = 0; k < inputMessages.size(); ++k) {
                                if (inputMessages.get(k).id > d.top_message_id) {
                                    count++;
                                }
                            }
                            d.messagesCount += count;
                        }
                        d.message = new MessageObject(currentAccount, message, false, false);
                        d.top_message_id = d.message.getId();
                    }
                    newMessagesCount = newMessagesCount != null ?
                        Math.max(newMessagesCount, d.messagesCount) :
                        d.messagesCount;
                    break;
                }
            }
            if (!found /*&& forumDialogId == 0*/) {
                SavedDialog d = SavedDialog.fromMessage(currentAccount, message, forumDialogId == 0);
                if (newMessagesCount != null) {
                    d.messagesCount = newMessagesCount;
                }
                loadedDialogs.add(d);
                changed = true;
            }
        }
        return changed;
    }

    public boolean updateSavedDialog(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        long dialogId = MessageObject.getSavedDialogId(self, message);
        for (int i = 0; i < allDialogs.size(); ++i) {
            SavedDialog d = allDialogs.get(i);
            if (d.dialogId == dialogId) {
                d.message = new MessageObject(currentAccount, message, false, false);
                d.top_message_id = d.message.getId();
                return true;
            }
        }
        return false;
    }

    public boolean updatedDialogCount(long dialogId, int messagesCount) {
        return updatedDialogCount(dialogId, messagesCount, false);
    }

    public boolean updatedDialogCount(long dialogId, int messagesCount, boolean forceIfCountNotLoaded) {
        for (int i = 0; i < allDialogs.size(); ++i) {
            SavedDialog d = allDialogs.get(i);
            if (d.dialogId == dialogId) {
                if (d.messagesCount != messagesCount || (!d.messagesCountLoaded && forceIfCountNotLoaded)) {
                    d.messagesCount = messagesCount;
                    d.messagesCountLoaded = true;
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public void update(long dialogId, TLRPC.messages_Messages messagesRes) {
        boolean changed = false;
        changed = updateSavedDialogs(messagesRes.messages) || changed;
        if (messagesRes instanceof TLRPC.TL_messages_messagesSlice) {
            changed = updatedDialogCount(dialogId, messagesRes.count) || changed;
        } else if (messagesRes instanceof TLRPC.TL_messages_messages) {
            changed = updatedDialogCount(dialogId, messagesRes.messages.size()) || changed;
        } else if (messagesRes instanceof TLRPC.TL_messages_channelMessages) {
            changed = updatedDialogCount(dialogId, messagesRes.count) || changed;
        }
        if (changed) {
            AndroidUtilities.runOnUIThread(this::update);
        }
    }

    public void updateDeleted(LongSparseArray<ArrayList<Integer>> messageIds) {
        boolean changed = false;
        ArrayList<SavedDialog> updateDialogsLastMessageId = new ArrayList<>();
        for (int i = 0; i < messageIds.size(); ++i) {
            long did = messageIds.keyAt(i);
            ArrayList<Integer> ids = messageIds.valueAt(i);
            int maxId = 0;
            for (int j = 0; j < ids.size(); ++j) {
                maxId = Math.max(maxId, ids.get(j));
            }
            SavedDialog d = null;
            for (int j = 0; j < allDialogs.size(); ++j) {
                if (allDialogs.get(j).dialogId == did) {
                    d = allDialogs.get(j);
                    break;
                }
            }
            if (d != null) {
                if (d.messagesCountLoaded && Math.max(0, d.messagesCount - ids.size()) != d.messagesCount) {
                    d.messagesCount = Math.max(0, d.messagesCount - ids.size());
                    changed = true;
                }
                if (d.messagesCountLoaded && d.messagesCount <= 0) {
                    removeDialog(d.dialogId);
                    changed = true;
                } else if (d.top_message_id <= maxId) {
                    updateDialogsLastMessageId.add(d);
                    changed = true;
                }
            }
        }
        if (changed) {
            if (!updateDialogsLastMessageId.isEmpty()) {
                updateDialogsLastMessage(updateDialogsLastMessageId);
            } else {
                update();
            }
        }
    }

    private void invalidate() {
        if (dialogsLoaded && loadedDialogs.isEmpty()) {
            return;
        }

        // put everything in cached
        for (int i = 0; i < loadedDialogs.size(); ++i) {
            SavedDialog ld = loadedDialogs.get(i);
            SavedDialog cd = null;
            for (int j = 0; j < cachedDialogs.size(); ++j) {
                SavedDialog d = cachedDialogs.get(j);
                if (d.dialogId == ld.dialogId) {
                    cd = d;
                    break;
                }
            }
            if (cd == null && !ld.pinned) {
                cachedDialogs.add(ld);
            }
        }
        // reload
        if (forumDialogId != 0) {
            UserConfig.getInstance(currentAccount).getPreferences().edit().remove("topics_end_reached_" + -forumDialogId).apply();
        }
        loadedDialogs.clear();
        dialogsLoaded = false;
        dialogsCount = 0;
        dialogsEndReached = false;
        update();
        loadDialogs(false);
    }

    public void deleteDialog(long did) {
        dialogsCount -= removeDialog(did);
        update();
    }

    public void deleteDialogs(ArrayList<Long> dids) {
        for (int i = 0; i < dids.size(); ++i) {
            dialogsCount -= removeDialog(dids.get(i));
        }
        update();
    }

    public void deleteAllDialogs() {
        dialogsCount = 0;
        allDialogs.clear();
        loadedDialogs.clear();
        cachedDialogs.clear();
        update();
    }

    private int removeDialog(long did) {
        int acount = 0;
        for (int i = 0; i < allDialogs.size(); ++i) {
            if (allDialogs.get(i).dialogId == did) {
                allDialogs.remove(i);
                acount++;
                i--;
            }
        }
        int lcount = 0;
        for (int i = 0; i < loadedDialogs.size(); ++i) {
            if (loadedDialogs.get(i).dialogId == did) {
                loadedDialogs.remove(i);
                lcount++;
                i--;
            }
        }
        for (int i = 0; i < cachedDialogs.size(); ++i) {
            if (cachedDialogs.get(i).dialogId == did) {
                cachedDialogs.remove(i);
                i--;
            }
        }

        if (forumDialogId != 0) {
            MessagesStorage.getInstance(currentAccount).removeTopic(forumDialogId, did);
        }

        return Math.max(acount, lcount);
    }

    public void update() {
        //
        updateAllDialogs(true);
        saveCacheSchedule();
    }

    public boolean updatePinned(ArrayList<Long> dialogIds, boolean pin, boolean toServer) {
        ArrayList<Long> currentOrder = getCurrentPinnedOrder(allDialogs);
        ArrayList<Long> newOrder = new ArrayList<>(currentOrder);
        for (int i = dialogIds.size() - 1; i >= 0; --i) {
            final long did = dialogIds.get(i);
            if (pin && !newOrder.contains(did)) {
                newOrder.add(0, did);
            } else if (!pin) {
                newOrder.remove(did);
            }
        }
        int limit = (
            UserConfig.getInstance(currentAccount).isPremium() ?
                MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitPremium :
                MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitDefault
        );
        if (newOrder.size() > limit) {
            return false;
        }
        if (!sameOrder(currentOrder, newOrder)) {
            if (toServer) {
                updatePinnedOrderToServer(newOrder);
                return true;
            } else {
                final boolean updateLoaded = updatePinnedOrder(loadedDialogs, newOrder);
                final boolean updateCached = updatePinnedOrder(cachedDialogs, newOrder);
                return updateLoaded || updateCached;
            }
        }
        return false;
    }

    public boolean updatePinnedOrder(ArrayList<Long> newOrder) {
        ArrayList<Long> currentOrder = getCurrentPinnedOrder(allDialogs);
        int limit = (
            UserConfig.getInstance(currentAccount).isPremium() ?
                MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitPremium :
                MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitDefault
        );
        if (newOrder.size() > limit) {
            return false;
        }
        if (!sameOrder(currentOrder, newOrder)) {
            updatePinnedOrderToServer(newOrder);
        }
        return true;
    }

    private void updatePinnedOrderToServer(ArrayList<Long> newOrder) {
        final boolean updateLoaded = updatePinnedOrder(loadedDialogs, newOrder);
        final boolean updateCached = updatePinnedOrder(cachedDialogs, newOrder);
        if (updateLoaded || updateCached) {
            TLRPC.TL_messages_reorderPinnedSavedDialogs req = new TLRPC.TL_messages_reorderPinnedSavedDialogs();
            req.force = true;
            for (int i = 0; i < newOrder.size(); ++i) {
                final long did = newOrder.get(i);
                TLRPC.TL_inputDialogPeer dialogPeer = new TLRPC.TL_inputDialogPeer();
                dialogPeer.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
                if (dialogPeer.peer != null) {
                    req.order.add(dialogPeer);
                }
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
            update();
        }
    }

    public void processUpdate(TLRPC.Update update) {
        if (processUpdateInternal(update)) {
            update();
        }
    }

    private boolean processUpdateInternal(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateSavedDialogPinned) {
            TLRPC.TL_updateSavedDialogPinned upd = (TLRPC.TL_updateSavedDialogPinned) update;
            if (!(upd.peer instanceof TLRPC.TL_dialogPeer)) return false;
            long dialogId = DialogObject.getPeerDialogId(((TLRPC.TL_dialogPeer) upd.peer).peer);
            ArrayList<Long> ids = new ArrayList<>();
            ids.add(dialogId);
            return updatePinned(ids, upd.pinned, false);
        } else if (update instanceof TLRPC.TL_updatePinnedSavedDialogs) {
            TLRPC.TL_updatePinnedSavedDialogs upd = (TLRPC.TL_updatePinnedSavedDialogs) update;
            ArrayList<Long> newOrder = new ArrayList<>(upd.order.size());
            for (int i = 0; i < upd.order.size(); ++i) {
                TLRPC.DialogPeer dialogPeer = upd.order.get(i);
                if (!(dialogPeer instanceof TLRPC.TL_dialogPeer)) {
                    continue;
                }
                newOrder.add(DialogObject.getPeerDialogId(((TLRPC.TL_dialogPeer) dialogPeer).peer));
            }
            final boolean updateLoaded = updatePinnedOrder(loadedDialogs, newOrder);
            final boolean updateCached = updatePinnedOrder(cachedDialogs, newOrder);
            return updateLoaded || updateCached;
        }
        return false;
    }

    private ArrayList<Long> getCurrentPinnedOrder(ArrayList<SavedDialog> dialogs) {
        ArrayList<Long> currentOrder = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); ++i) {
            SavedDialog d = dialogs.get(i);
            if (d.pinned) currentOrder.add(d.dialogId);
        }
        return currentOrder;
    }

    private boolean sameOrder(ArrayList<Long> a, ArrayList<Long> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); ++i) {
            if (!Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean updatePinnedOrder(ArrayList<SavedDialog> dialogs, ArrayList<Long> order) {
        ArrayList<Long> currentOrder = getCurrentPinnedOrder(dialogs);
        if (sameOrder(order, currentOrder)) {
            return false;
        }

        // remove all pinned
        ArrayList<SavedDialog> oldPinned = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); ++i) {
            SavedDialog d = dialogs.get(i);
            if (d.pinned) {
                d.pinned = false;
                oldPinned.add(d);
                dialogs.remove(i);
                i--;
            }
        }
        dialogs.addAll(oldPinned);

        // gather new pinned
        ArrayList<SavedDialog> newPinned = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); ++i) {
            SavedDialog d = dialogs.get(i);
            int index;
            if ((index = order.indexOf(d.dialogId)) >= 0) {
                d.pinnedOrder = index;
                d.pinned = true;
                newPinned.add(d);
                dialogs.remove(i);
                i--;
            }
        }

        // sort other not pinned
        Collections.sort(dialogs, (d1, d2) -> d2.getDate() - d1.getDate());

        // sort pinned by new order
        Collections.sort(newPinned, (d1, d2) -> d1.pinnedOrder - d2.pinnedOrder);

        // add pinned
        dialogs.addAll(0, newPinned);

        return true;
    }

    private boolean loadingCache, loadedCache;
    private boolean loadingCacheOnly;
    private void loadCache(Runnable whenDone) {
        if (loadingCache) {
            return;
        }
        loadingCache = true;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase db = messagesStorage.getDatabase();
            SQLiteCursor cursor = null;
            SQLiteCursor cursor2 = null;
            final ArrayList<SavedDialog> dialogs = new ArrayList<>();
            final ArrayList<Long> usersToLoad = new ArrayList<>();
            final ArrayList<Long> chatsToLoad = new ArrayList<>();
            final ArrayList<Long> emojiToLoad = new ArrayList<>();

            final ArrayList<TLRPC.User> users = new ArrayList<>();
            final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            final ArrayList<TLRPC.Document> emojis = new ArrayList<>();

            try {
                cursor = db.queryFinalized("SELECT did, date, last_mid, pinned, flags, folder_id, last_mid_group, count, unread_count, max_read_id, read_outbox FROM saved_dialogs WHERE forumChatId = ? ORDER BY pinned ASC, date DESC", forumDialogId);
                while (cursor.next()) {
                    SavedDialog d = new SavedDialog();
                    d.dialogId = cursor.longValue(0);
                    d.localDate = cursor.intValue(1);
                    d.top_message_id = cursor.intValue(2);
                    d.pinnedOrder = cursor.intValue(3);
                    final int flags = cursor.intValue(4);
                    d.messagesCountLoaded = (flags & 1) != 0;
                    d.pinned = d.pinnedOrder != 999;
                    d.messagesCount = cursor.intValue(7);
                    d.unreadCount = cursor.longValue(8);
                    d.readInboxMaxId = cursor.longValue(9);
                    d.readOutboxMaxId = cursor.longValue(10);
                    if (d.dialogId < 0) {
                        chatsToLoad.add(-d.dialogId);
                    } else {
                        usersToLoad.add(d.dialogId);
                    }

                    cursor2 = db.queryFinalized("SELECT data FROM messages_topics WHERE uid = ? AND mid = ? AND topic_id = ?", forumDialogId != 0 ? forumDialogId : selfId, d.top_message_id, d.dialogId);
                    if (cursor2.next()) {
                        NativeByteBuffer buffer = cursor2.byteBufferValue(0);
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(true), true);
                        MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, emojiToLoad);
                        d.message = new MessageObject(currentAccount, message, null, null, null, null, null, false, false, 0, false, false, forumDialogId == 0);
                    }
                    cursor2.dispose();

                    dialogs.add(d);
                }

                if (!usersToLoad.isEmpty()) {
                    messagesStorage.getUsersInternal(usersToLoad, users);
                }
                if (!chatsToLoad.isEmpty()) {
                    messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!emojiToLoad.isEmpty()) {
                    messagesStorage.getAnimatedEmoji(TextUtils.join(",", emojiToLoad), emojis);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                    cursor = null;
                }
                if (cursor2 != null) {
                    cursor2.dispose();
                    cursor2 = null;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadingCache = false;
                loadedCache = true;

                MessagesController.getInstance(currentAccount).putUsers(users, true);
                MessagesController.getInstance(currentAccount).putChats(chats, true);
                AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).processDocuments(emojis);

                cachedDialogs.clear();
                cachedDialogs.addAll(dialogs);
                updateAllDialogs(true);

                if (whenDone != null && !loadingCacheOnly) {
                    whenDone.run();
                }
            });
        });
    }
    private void updateDialogsLastMessage(ArrayList<SavedDialog> dialogs) {
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {

            SQLiteDatabase db = messagesStorage.getDatabase();
            SQLiteCursor cursor = null;

            final ArrayList<Long> dialogsToDelete = new ArrayList<>();
            final LongSparseArray<TLRPC.Message> newMessages = new LongSparseArray<>();

            final ArrayList<Long> usersToLoad = new ArrayList<>();
            final ArrayList<Long> chatsToLoad = new ArrayList<>();
            final ArrayList<Long> emojiToLoad = new ArrayList<>();

            final ArrayList<TLRPC.User> users = new ArrayList<>();
            final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            final ArrayList<TLRPC.Document> emojis = new ArrayList<>();

            try {
                for (int i = 0; i < dialogs.size(); ++i) {
                    SavedDialog d = dialogs.get(i);

                    cursor = db.queryFinalized("SELECT mid, data FROM messages_topics WHERE uid = ? AND topic_id = ? ORDER BY mid DESC LIMIT 1", forumDialogId != 0 ? forumDialogId : selfId, d.dialogId);
                    if (cursor.next()) {
                        int topMessageId = cursor.intValue(0);
                        NativeByteBuffer buffer = cursor.byteBufferValue(1);
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(true), true);
                        MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, emojiToLoad);
                        newMessages.put(d.dialogId, message);
                    } else {
                        dialogsToDelete.add(d.dialogId);
                    }
                    cursor.dispose();
                }

                if (!usersToLoad.isEmpty()) {
                    messagesStorage.getUsersInternal(usersToLoad, users);
                }
                if (!chatsToLoad.isEmpty()) {
                    messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!emojiToLoad.isEmpty()) {
                    messagesStorage.getAnimatedEmoji(TextUtils.join(",", emojiToLoad), emojis);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                    cursor = null;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount).putUsers(users, true);
                MessagesController.getInstance(currentAccount).putChats(chats, true);
                AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).processDocuments(emojis);

                for (int i = 0; i < dialogsToDelete.size(); ++i) {
                    removeDialog(dialogsToDelete.get(i));
                }
                for (int i = 0; i < newMessages.size(); ++i) {
                    long did = newMessages.keyAt(i);
                    TLRPC.Message message = newMessages.valueAt(i);
                    MessageObject messageObject = new MessageObject(currentAccount, message, null, null, null, null, null, false, false, 0, false, false, forumDialogId == 0);
                    for (int j = 0; j < loadedDialogs.size(); ++j) {
                        SavedDialog d = loadedDialogs.get(j);
                        if (d.dialogId == did) {
                            d.top_message_id = messageObject.getId();
                            d.message = messageObject;
                        }
                    }
                    for (int j = 0; j < cachedDialogs.size(); ++j) {
                        SavedDialog d = cachedDialogs.get(j);
                        if (d.dialogId == did) {
                            d.top_message_id = messageObject.getId();
                            d.message = messageObject;
                        }
                    }
                }

                update();
            });
        });
    }

    private final Runnable saveCacheRunnable = this::saveCache;
    private void saveCacheSchedule() {
        AndroidUtilities.cancelRunOnUIThread(saveCacheRunnable);
        AndroidUtilities.runOnUIThread(saveCacheRunnable, 450);
    }

    private boolean saving;
    private void saveCache() {
        if (saving) {
            return;
        }
        saving = true;
        ArrayList<SavedDialog> dialogsToSave = new ArrayList(allDialogs);
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase db = messagesStorage.getDatabase();
            SQLitePreparedStatement state = null;
            try {
                SQLitePreparedStatement state2 = db.executeFast("DELETE FROM saved_dialogs WHERE forumChatId = ?");
                state2.requery();
                state2.bindLong(1, forumDialogId);
                state2.step();
                state2.dispose();

                state = db.executeFast("REPLACE INTO saved_dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                for (int i = 0; i < dialogsToSave.size(); ++i) {
                    SavedDialog d = dialogsToSave.get(i);
                    state.requery();
                    state.bindLong(1, d.dialogId);
                    state.bindInteger(2, d.getDate());
                    state.bindInteger(3, d.top_message_id);
                    state.bindInteger(4, d.pinned ? i : 999);
                    state.bindInteger(5, d.messagesCountLoaded ? 1 : 0);
                    state.bindInteger(6, 0);
                    state.bindInteger(7, 0);
                    state.bindInteger(8, d.messagesCount);
                    state.bindLong(9, forumDialogId);
                    state.bindLong(10, d.unreadCount);
                    state.bindLong(11, d.readInboxMaxId);
                    state.bindLong(12, d.readOutboxMaxId);

                    state.step();
                }
                state.dispose();

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                    state = null;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                saving = false;
            });
        });
    }
    private void deleteCache() {
        if (saving) {
            return;
        }
        saving = true;
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        messagesStorage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase db = messagesStorage.getDatabase();
            try {
                SQLitePreparedStatement state2 = db.executeFast("DELETE FROM saved_dialogs WHERE forumChatId = ?");
                state2.requery();
                state2.bindLong(1, forumDialogId);
                state2.step();
                state2.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                saving = false;
                loadedCache = false;
            });
        });
    }

    public static class SavedDialog {
        public long dialogId;
        public boolean pinned;
        public int top_message_id;
        public MessageObject message;
        public int messagesCount;
        public long unreadCount;
        public long readInboxMaxId;
        public long readOutboxMaxId;

        public boolean messagesCountLoaded;

        // used only in cache
        private int localDate;

        // used only when sorting in update
        private int pinnedOrder;

        private int lastDate;

        public int getDate() {
            lastDate = getDateInternal();
            return lastDate;
        }

        private int getDateInternal() {
            if (message == null || message.messageOwner == null) {
                return localDate;
            }
            if ((message.messageOwner.flags & TLRPC.MESSAGE_FLAG_EDITED) != 0) {
                return message.messageOwner.edit_date;
            }
            return message.messageOwner.date;
        }

        public boolean isHidden() {
            return message != null && message.messageOwner != null && message.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear;
        }

        public static SavedDialog fromMessage(int currentAccount, TLRPC.Message message, boolean isSavedMessage) {
            SavedDialog d = new SavedDialog();
            d.dialogId = MessageObject.getSavedDialogId(UserConfig.getInstance(currentAccount).getClientUserId(), message);
            d.pinned = false;
            d.top_message_id = message.id;
            d.message = new MessageObject(currentAccount, message, null, null, null, null, null, false, false, 0, false, false, isSavedMessage);
            return d;
        }

        public static SavedDialog fromTL(int currentAccount, TLRPC.savedDialog tl, ArrayList<TLRPC.Message> messages, boolean isSavedMessage) {
            SavedDialog d = new SavedDialog();
            d.dialogId = DialogObject.getPeerDialogId(tl.peer);
            d.pinned = tl.pinned;
            d.top_message_id = tl.top_message;
            d.unreadCount = tl.unread_count;
            d.readInboxMaxId = tl.read_inbox_max_id;
            d.readOutboxMaxId = tl.read_outbox_max_id;
            TLRPC.Message message = null;
            for (int i = 0; i < messages.size(); ++i) {
                TLRPC.Message msg = messages.get(i);
                if (d.top_message_id == msg.id) {
                    message = msg;
                    break;
                }
            }
            if (message != null) {
                d.message = new MessageObject(currentAccount, message, null, null, null, null, null, false, false, 0, false, false, isSavedMessage);
            }
            return d;
        }
    }
    public static void openSavedMessages() {
        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(lastFragment.getCurrentAccount()).getClientUserId());
        lastFragment.presentFragment(new ChatActivity(args));
    }

    public void checkSavedDialogCount(long dialogId) {
        SavedDialog d = findSavedDialog(dialogId);
        if (d != null && !d.messagesCountLoaded) {
            hasSavedMessages(dialogId, null);
        }
    }

    private final LongSparseArray<ArrayList<Utilities.Callback<Boolean>>> checkMessagesCallbacks = new LongSparseArray<>();
    public void hasSavedMessages(long did, Utilities.Callback<Boolean> whenDone) {
        final SavedDialog savedDialog = findSavedDialog(did);
        if (savedDialog != null && savedDialog.messagesCount > 0 && savedDialog.messagesCountLoaded) {
            if (whenDone != null) whenDone.run(true);
            return;
        }

        ArrayList<Utilities.Callback<Boolean>> existingCallbacks = checkMessagesCallbacks.get(did);
        if (existingCallbacks != null) {
            if (whenDone != null) existingCallbacks.add(whenDone);
            return;
        }
        existingCallbacks = new ArrayList<>();
        if (whenDone != null) existingCallbacks.add(whenDone);
        checkMessagesCallbacks.put(did, existingCallbacks);

        TLRPC.TL_messages_getSavedHistory req = new TLRPC.TL_messages_getSavedHistory();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(did);
        if (forumDialogId != 0) {
            req.parent_peer = MessagesController.getInstance(currentAccount).getInputPeer(forumDialogId);
        }
        req.limit = 1;
        req.hash = 0;
        req.offset_id = Integer.MAX_VALUE;
        req.offset_date = Integer.MAX_VALUE;
        req.add_offset = -1;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                int count = r.messages.size();
                if (r instanceof TLRPC.TL_messages_messagesSlice) {
                    count = ((TLRPC.TL_messages_messagesSlice) r).count;
                } else if (forumDialogId != 0 && r instanceof TLRPC.TL_messages_channelMessages) {
                    count = r.count;
                }

                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(r.users, r.chats, true, true);

                boolean hasMessages = count > 0;
                if (count > 0) {
                    if (!updatedDialogCount(did, count, true)) {
                        if (!r.messages.isEmpty()) {
                            SavedDialog dialog = SavedDialog.fromMessage(currentAccount, r.messages.get(0), forumDialogId == 0);
                            dialog.messagesCount = count;
                            dialog.messagesCountLoaded = true;
                            cachedDialogs.add(dialog);
                            update();
                        }
                    } else {
                        update();
                    }
                }

                ArrayList<Utilities.Callback<Boolean>> callbacks = checkMessagesCallbacks.get(did);
                checkMessagesCallbacks.remove(did);
                if (callbacks != null) {
                    for (int i = 0; i < callbacks.size(); ++i) {
                        callbacks.get(i).run(hasMessages);
                    }
                }
            }
        }));
    }
}
