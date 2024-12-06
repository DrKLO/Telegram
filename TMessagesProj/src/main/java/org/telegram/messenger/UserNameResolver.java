package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LruCache;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class UserNameResolver {

    private final int currentAccount;
    private final static long CACHE_TIME = 1000 * 60 * 60; //1 hour

    UserNameResolver(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    LruCache<String, CachedPeer> resolvedCache = new LruCache<>(100);
    HashMap<String, ArrayList<Consumer<Long>>> resolvingConsumers = new HashMap<>();

    public int resolve(String username, Consumer<Long> resolveConsumer) {
        return resolve(username, null, resolveConsumer);
    }

    public int resolve(String username, String referrer, Consumer<Long> resolveConsumer) {
        if (TextUtils.isEmpty(referrer)) {
            CachedPeer cachedPeer = resolvedCache.get(username);
            if (cachedPeer != null) {
                if (System.currentTimeMillis() - cachedPeer.time < CACHE_TIME) {
                    resolveConsumer.accept(cachedPeer.peerId);
                    FileLog.d("resolve username from cache " + username + " " + cachedPeer.peerId);
                    return -1;
                } else {
                    resolvedCache.remove(username);
                }
            }
        }

        ArrayList<Consumer<Long>> consumers = resolvingConsumers.get(username);
        if (consumers != null) {
            consumers.add(resolveConsumer);
            return -1;
        }
        consumers = new ArrayList<>();
        consumers.add(resolveConsumer);
        resolvingConsumers.put(username, consumers);


        TLObject req;
        if (AndroidUtilities.isNumeric(username)) {
            TLRPC.TL_contacts_resolvePhone resolvePhone = new TLRPC.TL_contacts_resolvePhone();
            resolvePhone.phone = username;
            req = resolvePhone;
        } else {
            TLRPC.TL_contacts_resolveUsername resolveUsername = new TLRPC.TL_contacts_resolveUsername();
            resolveUsername.username = username;
            if (!TextUtils.isEmpty(referrer)) {
                resolveUsername.flags |= 1;
                resolveUsername.referer = referrer;
            }
            req = resolveUsername;
        }
        return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<Consumer<Long>> finalConsumers = resolvingConsumers.remove(username);
            if (finalConsumers == null) {
                return;
            }
            if (error != null) {
                if (error != null && error.text != null && "STARREF_EXPIRED".equals(error.text)) {
                    for (int i = 0; i < finalConsumers.size(); i++) {
                        finalConsumers.get(i).accept(Long.MAX_VALUE);
                    }
                    return;
                }
                for (int i = 0; i < finalConsumers.size(); i++) {
                    finalConsumers.get(i).accept(null);
                }

                if (error != null && error.text != null && error.text.contains("FLOOD_WAIT")) {
                    BaseFragment fragment = LaunchActivity.getLastFragment();
                    if (fragment != null) {
                        BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.FloodWait)).show();
                    }
                }
                return;
            }
            TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;

            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true);

            long peerId = MessageObject.getPeerId(res.peer);
            resolvedCache.put(username, new CachedPeer(peerId));
            for (int i = 0; i < finalConsumers.size(); i++) {
                finalConsumers.get(i).accept(peerId);
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors));
    };

    public void update(TLRPC.User oldUser, TLRPC.User user) {
        if (oldUser == null || user == null || oldUser.username == null || TextUtils.equals(oldUser.username, user.username)) {
            return;
        }
        resolvedCache.remove(oldUser.username);
        if (user.username != null) {
            resolvedCache.put(user.username, new CachedPeer(user.id));
        }
    }

    public void update(TLRPC.Chat oldChat, TLRPC.Chat chat) {
        if (oldChat == null || chat == null || oldChat.username == null || TextUtils.equals(oldChat.username, chat.username)) {
            return;
        }
        resolvedCache.remove(oldChat.username);
        if (chat.username != null) {
            resolvedCache.put(chat.username, new CachedPeer(-chat.id));
        }
    }

    private class CachedPeer {
        final long peerId;
        final long time;

        public CachedPeer(long peerId) {
            this.peerId = peerId;
            time = System.currentTimeMillis();
        }
    }
}
