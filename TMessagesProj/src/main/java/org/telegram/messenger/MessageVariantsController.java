package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import org.telegram.tgnet.TLRPC;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-account correlation store for "variant sibling" message groups: a set of real,
 * independently-persisted TL_message rows that represent alternative answers to the same
 * prompt, of which only one is shown at a time in the chat's visible render list. The others
 * remain in MessagesStorage untouched -- this class only tracks which member of a group is
 * currently "active" (shown) and which are hidden, plus enough bookkeeping (ordered member
 * list, per-group active pointer) for a pager UI to page between them later.
 *
 * Deliberately independent of TLRPC.Message.grouped_id / MessageObject.GroupedMessages -- those
 * drive native album/collage rendering and are not safe to repurpose for this (verified during
 * planning). Message/group ids here are ints, matching TLRPC.Message.id / MessageObject.getId().
 */
public class MessageVariantsController {

    private static final MessageVariantsController[] instances = new MessageVariantsController[UserConfig.MAX_ACCOUNT_COUNT];

    private static final String PREFS_NAME_PREFIX = "message_variants_";
    private static final String KEY_GROUP_OF = "group_";
    private static final String KEY_MEMBERS = "members_";
    private static final String KEY_ACTIVE = "active_";

    private final int currentAccount;
    // Not persisted -- rebuilt opportunistically as messages are observed (both the cache-reload
    // and live filter hooks call observe() so this cache warms across a cold restart too).
    private final SparseArray<MessageObject> objectCache = new SparseArray<>();

    private RegenerateListener regenerateListener;

    public interface RegenerateListener {
        void onRegenerateRequested(MessageObject sourceMessage);
    }

    public static MessageVariantsController getInstance(int account) {
        MessageVariantsController local = instances[account];
        if (local == null) {
            synchronized (MessageVariantsController.class) {
                local = instances[account];
                if (local == null) {
                    local = instances[account] = new MessageVariantsController(account);
                }
            }
        }
        return local;
    }

    private MessageVariantsController(int account) {
        this.currentAccount = account;
    }

    /**
     * Registry of peers that author alternative answers to the same prompt, i.e. the peers whose
     * messages this controller may group into variant packs.
     *
     * <p>This lives here, on the client, and NOT on a TL_user field: the bits of the TL schema are
     * assigned by the server, so a client contribution cannot introduce one (upstream 12.9.0 took
     * flags2 bit 21 for linked_community_id). The core stays product-agnostic -- it only ever knows
     * a user id; the product layer registers its own bot at startup.
     *
     * <p>Static rather than per-account: the id of such a peer does not depend on the account, and
     * a static registry keeps the predicate testable on a plain JVM.
     *
     * <p>In-memory only, deliberately not persisted: the registering layer knows its own peer ids
     * and re-registers them synchronously on every cold start, before the first cell is drawn. If
     * such peers ever have to be discovered at runtime instead, this registry needs to be persisted
     * or rebuilt from the peer list.
     */
    private static final Set<Long> generativeBots = Collections.synchronizedSet(new HashSet<>());

    /** Mark a peer as an author of alternative answers. Called by the product layer on startup. */
    public static void registerGenerativeBot(long userId) {
        if (userId != 0) {
            generativeBots.add(userId);
        }
    }

    /** True when the peer authors alternative answers. Null-safe by contract: 0 is never a peer. */
    public static boolean isGenerativeBot(long userId) {
        return userId != 0 && generativeBots.contains(userId);
    }

    /** Convenience overload for the render/dispatch call sites, which hold a user, not an id. */
    public static boolean isGenerativeBot(TLRPC.User user) {
        return user != null && isGenerativeBot(user.id);
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME_PREFIX + currentAccount, Context.MODE_PRIVATE);
    }

    // Start a new variant group anchored on `first`'s own message id (matches Telegram's own
    // "id doubles as key" convention). Returns the new group id.
    public int startGroup(MessageObject first) {
        int groupId = first.getId();
        SharedPreferences.Editor editor = prefs().edit();
        editor.putInt(KEY_GROUP_OF + groupId, groupId);
        editor.putString(KEY_MEMBERS + groupId, String.valueOf(groupId));
        editor.putInt(KEY_ACTIVE + groupId, groupId);
        editor.apply();
        objectCache.put(groupId, first);
        return groupId;
    }

    // Append a new sibling to an existing group and make it the active/shown member.
    public void addSibling(int groupId, MessageObject sibling) {
        int siblingId = sibling.getId();
        int[] existing = memberIds(groupId);
        StringBuilder sb = new StringBuilder();
        for (int id : existing) {
            sb.append(id).append(',');
        }
        sb.append(siblingId);
        SharedPreferences.Editor editor = prefs().edit();
        editor.putString(KEY_MEMBERS + groupId, sb.toString());
        editor.putInt(KEY_GROUP_OF + siblingId, groupId);
        editor.putInt(KEY_ACTIVE + groupId, siblingId);
        editor.apply();
        objectCache.put(siblingId, sibling);
    }

    public boolean isHidden(int messageId) {
        int groupId = groupIdOf(messageId);
        if (groupId == 0) {
            return false;
        }
        return messageId != activeId(groupId);
    }

    public int groupIdOf(int messageId) {
        return prefs().getInt(KEY_GROUP_OF + messageId, 0);
    }

    public int activeId(int groupId) {
        return prefs().getInt(KEY_ACTIVE + groupId, 0);
    }

    public int[] memberIds(int groupId) {
        String csv = prefs().getString(KEY_MEMBERS + groupId, null);
        if (csv == null || csv.isEmpty()) {
            return new int[0];
        }
        String[] parts = csv.split(",");
        int[] ids = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ids[i] = Integer.parseInt(parts[i]);
        }
        return ids;
    }

    public int size(int groupId) {
        return memberIds(groupId).length;
    }

    // 0-based position of the active member within memberIds -- for a future pager's counter.
    public int indexOfActive(int groupId) {
        int active = activeId(groupId);
        int[] ids = memberIds(groupId);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == active) {
                return i;
            }
        }
        return -1;
    }

    // Page the active member by `delta`, wrapping at both ends (cyclic). Clamping into
    // [0, size - 1] does not work here: addSibling always makes the NEWEST sibling active, i.e. the
    // last index, and the swipe gesture in ChatActivity always pages +1 -- so from the last index a
    // clamped page would be a permanent no-op and the swipe would appear dead. Cyclic paging makes
    // every swipe land on a different sibling (with two variants: 2/2 -> 1/2 -> 2/2 ...) and keeps
    // the tap arrows responsive at the edges too. Safe modulo handles a negative delta.
    public int page(int groupId, int delta) {
        int[] ids = memberIds(groupId);
        if (ids.length == 0) {
            return 0;
        }
        int index = indexOfActive(groupId);
        if (index < 0) {
            index = 0;
        }
        int newActive = ids[wrapIndex(index, delta, ids.length)];
        prefs().edit().putInt(KEY_ACTIVE + groupId, newActive).apply();
        return newActive;
    }

    // Pure cyclic-index math, extracted so the wrap invariant can be unit-tested on a plain JVM,
    // without a device or SharedPreferences. Safe modulo handles a negative delta and |delta| > size.
    // size must be > 0 -- page() guards ids.length == 0 before calling.
    static int wrapIndex(int index, int delta, int size) {
        return ((index + delta) % size + size) % size;
    }

    // Warm the object cache for a message that belongs to a group -- called from both
    // ChatActivity filter hooks (cache-reload path and live path) so a later page/pager
    // operation can retrieve a hidden sibling's MessageObject without a MessagesStorage
    // round-trip, even across a cold restart.
    public void observe(MessageObject mo) {
        if (groupIdOf(mo.getId()) != 0) {
            objectCache.put(mo.getId(), mo);
        }
    }

    public MessageObject getCached(int messageId) {
        return objectCache.get(messageId);
    }

    public void setRegenerateListener(RegenerateListener l) {
        this.regenerateListener = l;
    }

    public void notifyRegenerateRequested(MessageObject sourceMessage) {
        if (regenerateListener != null) {
            regenerateListener.onRegenerateRequested(sourceMessage);
        }
    }
}
