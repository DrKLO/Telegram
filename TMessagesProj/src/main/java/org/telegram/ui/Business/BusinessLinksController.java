package org.telegram.ui.Business;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

public class BusinessLinksController {

    private static volatile BusinessLinksController[] Instance = new BusinessLinksController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static BusinessLinksController getInstance(int num) {
        BusinessLinksController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BusinessLinksController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    public final ArrayList<TLRPC.TL_businessChatLink> links = new ArrayList<>();

    private boolean loading = false;
    private boolean loaded = false;

    private BusinessLinksController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public static String stripHttps(String link) {
        if (link.startsWith("https://")) {
            return link.substring(8);
        }
        return link;
    }

    public boolean canAddNew() {
        return links.size() < MessagesController.getInstance(currentAccount).businessChatLinksLimit;
    }

    public void load(boolean forceReload) {
        if (!loaded) {
            load(true, forceReload);
        } else if (forceReload) {
            load(false, true);
        }
    }

    private void load(boolean fromCache, boolean forceReload) {
        if (loading || loaded && (!forceReload || fromCache)) {
            return;
        }

        loading = true;

        if (fromCache) {
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            storage.getStorageQueue().postRunnable(() -> {
                final ArrayList<TLRPC.TL_businessChatLink> result = new ArrayList<>();
                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();

                SQLiteCursor cursor = null;
                try {
                    SQLiteDatabase db = storage.getDatabase();
                    cursor = db.queryFinalized("SELECT data FROM business_links ORDER BY order_value ASC");
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        TLRPC.TL_businessChatLink link = TLRPC.TL_businessChatLink.TLdeserialize(data, data.readInt32(false), false);
                        result.add(link);
                    }
                    cursor.dispose();

                    final ArrayList<Long> usersToLoad = new ArrayList<>();
                    final ArrayList<Long> chatsToLoad = new ArrayList<>();
                    for (int i = 0; i < result.size(); ++i) {
                        TLRPC.TL_businessChatLink link = result.get(i);
                        if (!link.entities.isEmpty()) {
                            for (int a = 0; a < link.entities.size(); a++) {
                                TLRPC.MessageEntity entity = link.entities.get(a);
                                if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                                    usersToLoad.add(((TLRPC.TL_messageEntityMentionName) entity).user_id);
                                } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                                    usersToLoad.add(((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id);
                                }
                            }
                        }
                    }
                    if (!usersToLoad.isEmpty()) {
                        storage.getUsersInternal(usersToLoad, users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        storage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    links.clear();
                    links.addAll(result);
                    MessagesController.getInstance(currentAccount).putUsers(users, true);
                    MessagesController.getInstance(currentAccount).putChats(chats, true);

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.quickRepliesUpdated);
                    loading = false;
                    load(false, forceReload);
                });
            });
        } else {
            TLRPC.TL_account_getBusinessChatLinks req = new TLRPC.TL_account_getBusinessChatLinks();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_account_businessChatLinks) {
                    TLRPC.TL_account_businessChatLinks businessChatLinks = (TLRPC.TL_account_businessChatLinks) res;

                    links.clear();
                    links.addAll(businessChatLinks.links);

                    MessagesController.getInstance(currentAccount).putUsers(businessChatLinks.users, false);
                    MessagesController.getInstance(currentAccount).putChats(businessChatLinks.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(businessChatLinks.users, businessChatLinks.chats, true, true);

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);

                    saveToCache();
                } else {
                    FileLog.e(new RuntimeException("Unexpected response from server!"));
                }

                loading = false;
                loaded = true;
            }));
        }
    }

    public void createEmptyLink() {
        TLRPC.TL_account_createBusinessChatLink req = new TLRPC.TL_account_createBusinessChatLink();
        req.link = new TLRPC.TL_inputBusinessChatLink();
        req.link.message = "";

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_businessChatLink) {
                TLRPC.TL_businessChatLink businessChatLink = (TLRPC.TL_businessChatLink) res;
                links.add(businessChatLink);

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinkCreated, businessChatLink);

                saveToCache();
            }
        }));
    }

    public void deleteLinkUndoable(BaseFragment fragment, String slug) {
        TLRPC.TL_businessChatLink link = findLink(slug);
        if (link != null) {
            int index = links.indexOf(link);
            links.remove(link);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);

            BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getString(R.string.BusinessLinkDeleted), true,
                    () -> {
                        links.add(index, link);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);
                    },
                    () -> {
                        TLRPC.TL_account_deleteBusinessChatLink req = new TLRPC.TL_account_deleteBusinessChatLink();
                        req.slug = slug;

                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            if (res instanceof TLRPC.TL_boolTrue) {
                                if (links.contains(link)) {
                                    links.remove(link);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);
                                }
                                saveToCache();
                            } else {
                                FileLog.e(new RuntimeException("Unexpected response from server!"));
                            }
                        }));
                    }).show();
        }
    }

    public void editLinkMessage(String slug, String message, ArrayList<TLRPC.MessageEntity> entities, Runnable onDone) {
        TLRPC.TL_businessChatLink link = findLink(slug);
        if (link == null) {
            return;
        }

        TLRPC.TL_inputBusinessChatLink inputLink = new TLRPC.TL_inputBusinessChatLink();
        inputLink.message = message;
        inputLink.entities = entities;
        inputLink.title = link.title;

        editLink(link, inputLink, onDone);
    }

    public void editLinkTitle(String slug, String title) {
        TLRPC.TL_businessChatLink link = findLink(slug);
        if (link == null) {
            return;
        }

        TLRPC.TL_inputBusinessChatLink inputLink = new TLRPC.TL_inputBusinessChatLink();
        inputLink.message = link.message;
        inputLink.entities = link.entities;
        inputLink.title = title;

        editLink(link, inputLink, null);
    }

    private void saveToCache() {
        ArrayList<TLRPC.TL_businessChatLink> linksCopy = new ArrayList<>(links);

        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase db = storage.getDatabase();
                db.executeFast("DELETE FROM business_links").stepThis().dispose();
                state = db.executeFast("REPLACE INTO business_links VALUES(?, ?)");
                for (int i = 0; i < linksCopy.size(); i++) {
                    TLRPC.TL_businessChatLink link = linksCopy.get(i);
                    NativeByteBuffer data = new NativeByteBuffer(link.getObjectSize());
                    link.serializeToStream(data);
                    state.requery();
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, i);
                    state.step();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    private void editLink(TLRPC.TL_businessChatLink link, TLRPC.TL_inputBusinessChatLink inputLink, Runnable onDone) {
        TLRPC.TL_account_editBusinessChatLink req = new TLRPC.TL_account_editBusinessChatLink();
        req.slug = link.link;

        if (!inputLink.entities.isEmpty()) {
            inputLink.flags |= 1;
        }
        if (!TextUtils.isEmpty(inputLink.title)) {
            inputLink.flags |= 2;
        }
        req.link = inputLink;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_businessChatLink) {
                TLRPC.TL_businessChatLink updatedLink = (TLRPC.TL_businessChatLink) res;
                int index = links.indexOf(link);
                if (index != -1) {
                    links.set(index, updatedLink);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.businessLinksUpdated);

                    if (onDone != null) {
                        onDone.run();
                    }

                    saveToCache();
                }
            }
        }));
    }

    public TLRPC.TL_businessChatLink findLink(String slug) {
        for (int i = 0; i < links.size(); i++) {
            TLRPC.TL_businessChatLink chatLink = links.get(i);
            if (TextUtils.equals(chatLink.link, slug) ||
                    TextUtils.equals(chatLink.link, "https://" + slug) ||
                    TextUtils.equals(chatLink.link, "https://t.me/m/" + slug) ||
                    TextUtils.equals(chatLink.link, "tg://message?slug=" + slug)) {
                return chatLink;
            }
        }

        return null;
    }
}
