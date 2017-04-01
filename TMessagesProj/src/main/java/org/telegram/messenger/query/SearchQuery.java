/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.query;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.OpenChatReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SearchQuery {

    public static ArrayList<TLRPC.TL_topPeer> hints = new ArrayList<>();
    public static ArrayList<TLRPC.TL_topPeer> inlineBots = new ArrayList<>();
    private static HashMap<Integer, Integer> inlineDates = new HashMap<>();
    private static boolean loaded;
    private static boolean loading;

    private static Paint roundPaint;
    private static RectF bitmapRect;

    public static void cleanup() {
        loading = false;
        loaded = false;
        hints.clear();
        inlineBots.clear();
        inlineDates.clear();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
    }

    public static void buildShortcuts() {
        if (Build.VERSION.SDK_INT < 25) {
            return;
        }
        final ArrayList<TLRPC.TL_topPeer> hintsFinal = new ArrayList<>();
        for (int a = 0; a < hints.size(); a++) {
            hintsFinal.add(hints.get(a));
            if (hintsFinal.size() == 3) {
                break;
            }
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                ShortcutManager shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager.class);
                List<ShortcutInfo> currentShortcuts = shortcutManager.getDynamicShortcuts();
                ArrayList<String> shortcutsToUpdate = new ArrayList<>();
                ArrayList<String> newShortcutsIds = new ArrayList<>();
                ArrayList<String> shortcutsToDelete = new ArrayList<>();

                if (currentShortcuts != null && !currentShortcuts.isEmpty()) {
                    newShortcutsIds.add("compose");
                    for (int a = 0; a < hintsFinal.size(); a++) {
                        TLRPC.TL_topPeer hint = hintsFinal.get(a);
                        long did;
                        if (hint.peer.user_id != 0) {
                            did = hint.peer.user_id;
                        } else {
                            did = -hint.peer.chat_id;
                            if (did == 0) {
                                did = -hint.peer.channel_id;
                            }
                        }
                        newShortcutsIds.add("did" + did);
                    }
                    for (int a = 0; a < currentShortcuts.size(); a++) {
                        String id = currentShortcuts.get(a).getId();
                        if (!newShortcutsIds.remove(id)) {
                            shortcutsToDelete.add(id);
                        }
                        shortcutsToUpdate.add(id);
                    }
                    if (newShortcutsIds.isEmpty() && shortcutsToDelete.isEmpty()) {
                        return;
                    }
                }

                Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                intent.setAction("new_dialog");
                ArrayList<ShortcutInfo> arrayList = new ArrayList<>();
                arrayList.add(new ShortcutInfo.Builder(ApplicationLoader.applicationContext, "compose")
                        .setShortLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                        .setLongLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                        .setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_compose))
                        .setIntent(intent)
                        .build());
                if (shortcutsToUpdate.contains("compose")) {
                    shortcutManager.updateShortcuts(arrayList);
                } else {
                    shortcutManager.addDynamicShortcuts(arrayList);
                }
                arrayList.clear();

                if (!shortcutsToDelete.isEmpty()) {
                    shortcutManager.removeDynamicShortcuts(shortcutsToDelete);
                }

                for (int a = 0; a < hintsFinal.size(); a++) {
                    Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);
                    TLRPC.TL_topPeer hint = hintsFinal.get(a);

                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    long did;
                    if (hint.peer.user_id != 0) {
                        shortcutIntent.putExtra("userId", hint.peer.user_id);
                        user = MessagesController.getInstance().getUser(hint.peer.user_id);
                        did = hint.peer.user_id;
                    } else {
                        int chat_id = hint.peer.chat_id;
                        if (chat_id == 0) {
                            chat_id = hint.peer.channel_id;
                        }
                        chat = MessagesController.getInstance().getChat(chat_id);
                        shortcutIntent.putExtra("chatId", chat_id);
                        did = -chat_id;
                    }
                    if (user == null && chat == null) {
                        continue;
                    }

                    String name;
                    TLRPC.FileLocation photo = null;

                    if (user != null) {
                        name = ContactsController.formatName(user.first_name, user.last_name);
                        if (user.photo != null) {
                            photo = user.photo.photo_small;
                        }
                    } else {
                        name = chat.title;
                        if (chat.photo != null) {
                            photo = chat.photo.photo_small;
                        }
                    }

                    shortcutIntent.setAction("com.tmessages.openchat" + did);
                    shortcutIntent.addFlags(0x4000000);

                    Bitmap bitmap = null;
                    if (photo != null) {
                        try {
                            File path = FileLoader.getPathToAttach(photo, true);
                            bitmap = BitmapFactory.decodeFile(path.toString());
                            if (bitmap != null) {
                                int size = AndroidUtilities.dp(48);
                                Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                                result.eraseColor(Color.TRANSPARENT);
                                Canvas canvas = new Canvas(result);
                                BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                                if (roundPaint == null) {
                                    roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    bitmapRect = new RectF();
                                }
                                float scale = size / (float) bitmap.getWidth();
                                canvas.scale(scale, scale);
                                roundPaint.setShader(shader);
                                bitmapRect.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(46), AndroidUtilities.dp(46));
                                canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                                try {
                                    canvas.setBitmap(null);
                                } catch (Exception e) {
                                    //don't promt, this will crash on 2.x
                                }
                                bitmap = result;
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }

                    String id = "did" + did;
                    if (TextUtils.isEmpty(name)) {
                        name = " ";
                    }
                    ShortcutInfo.Builder builder = new ShortcutInfo.Builder(ApplicationLoader.applicationContext, id)
                            .setShortLabel(name)
                            .setLongLabel(name)
                            .setIntent(shortcutIntent);
                    if (bitmap != null) {
                        builder.setIcon(Icon.createWithBitmap(bitmap));
                    } else {
                        builder.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_user));
                    }
                    arrayList.add(builder.build());
                    if (shortcutsToUpdate.contains(id)) {
                        shortcutManager.updateShortcuts(arrayList);
                    } else {
                        shortcutManager.addDynamicShortcuts(arrayList);
                    }
                    arrayList.clear();
                }
            }
        });
    }

    public static void loadHints(boolean cache) {
        if (loading) {
            return;
        }
        if (cache) {
            if (loaded) {
                return;
            }
            loading = true;
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<TLRPC.TL_topPeer> hintsNew = new ArrayList<>();
                    final ArrayList<TLRPC.TL_topPeer> inlineBotsNew = new ArrayList<>();
                    final HashMap<Integer, Integer> inlineDatesNew = new HashMap<>();
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    try {
                        ArrayList<Integer> usersToLoad = new ArrayList<>();
                        ArrayList<Integer> chatsToLoad = new ArrayList<>();
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, type, rating, date FROM chat_hints WHERE 1 ORDER BY rating DESC");
                        while (cursor.next()) {
                            int did = cursor.intValue(0);
                            int type = cursor.intValue(1);
                            TLRPC.TL_topPeer peer = new TLRPC.TL_topPeer();
                            peer.rating = cursor.doubleValue(2);
                            if (did > 0) {
                                peer.peer = new TLRPC.TL_peerUser();
                                peer.peer.user_id = did;
                                usersToLoad.add(did);
                            } else {
                                peer.peer = new TLRPC.TL_peerChat();
                                peer.peer.chat_id = -did;
                                chatsToLoad.add(-did);
                            }
                            if (type == 0) {
                                hintsNew.add(peer);
                            } else if (type == 1) {
                                inlineBotsNew.add(peer);
                                inlineDatesNew.put(did, cursor.intValue(3));
                            }
                        }
                        cursor.dispose();
                        if (!usersToLoad.isEmpty()) {
                            MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }

                        if (!chatsToLoad.isEmpty()) {
                            MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance().putUsers(users, true);
                                MessagesController.getInstance().putChats(chats, true);
                                loading = false;
                                loaded = true;
                                hints = hintsNew;
                                inlineBots = inlineBotsNew;
                                inlineDates = inlineDatesNew;
                                buildShortcuts();
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                                if (Math.abs(UserConfig.lastHintsSyncTime - (int) (System.currentTimeMillis() / 1000)) >= 24 * 60 * 60) {
                                    loadHints(false);
                                }
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            loaded = true;
        } else {
            loading = true;
            TLRPC.TL_contacts_getTopPeers req = new TLRPC.TL_contacts_getTopPeers();
            req.hash = 0;
            req.bots_pm = false;
            req.correspondents = true;
            req.groups = false;
            req.channels = false;
            req.bots_inline = true;
            req.offset = 0;
            req.limit = 20;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    if (response instanceof TLRPC.TL_contacts_topPeers) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                final TLRPC.TL_contacts_topPeers topPeers = (TLRPC.TL_contacts_topPeers) response;
                                MessagesController.getInstance().putUsers(topPeers.users, false);
                                MessagesController.getInstance().putChats(topPeers.chats, false);
                                for (int a = 0; a < topPeers.categories.size(); a++) {
                                    TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                    if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                        inlineBots = category.peers;
                                    } else {
                                        hints = category.peers;
                                    }
                                }
                                buildShortcuts();
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                                final HashMap<Integer, Integer> inlineDatesCopy = new HashMap<>(inlineDates);
                                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
                                            MessagesStorage.getInstance().getDatabase().beginTransaction();
                                            MessagesStorage.getInstance().putUsersAndChats(topPeers.users, topPeers.chats, false, false);

                                            SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                                            for (int a = 0; a < topPeers.categories.size(); a++) {
                                                int type;
                                                TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                                if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                                    type = 1;
                                                } else {
                                                    type = 0;
                                                }
                                                for (int b = 0; b < category.peers.size(); b++) {
                                                    TLRPC.TL_topPeer peer = category.peers.get(b);
                                                    int did;
                                                    if (peer.peer instanceof TLRPC.TL_peerUser) {
                                                        did = peer.peer.user_id;
                                                    } else if (peer.peer instanceof TLRPC.TL_peerChat) {
                                                        did = -peer.peer.chat_id;
                                                    } else {
                                                        did = -peer.peer.channel_id;
                                                    }
                                                    Integer date = inlineDatesCopy.get(did);
                                                    state.requery();
                                                    state.bindInteger(1, did);
                                                    state.bindInteger(2, type);
                                                    state.bindDouble(3, peer.rating);
                                                    state.bindInteger(4, date != null ? date : 0);
                                                    state.step();
                                                }
                                            }

                                            state.dispose();

                                            MessagesStorage.getInstance().getDatabase().commitTransaction();
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    UserConfig.lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                                                    UserConfig.saveConfig(false);
                                                }
                                            });
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            });
        }
    }

    public static void increaseInlineRaiting(final int uid) {
        Integer time = inlineDates.get(uid);
        int dt;
        if (time != null) {
            dt = Math.max(1, ((int) (System.currentTimeMillis() / 1000)) - time);
        } else {
            dt = 60;
        }

        TLRPC.TL_topPeer peer = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            TLRPC.TL_topPeer p = inlineBots.get(a);
            if (p.peer.user_id == uid) {
                peer = p;
                break;
            }
        }
        if (peer == null) {
            peer = new TLRPC.TL_topPeer();
            peer.peer = new TLRPC.TL_peerUser();
            peer.peer.user_id = uid;
            inlineBots.add(peer);
        }
        peer.rating += Math.exp(dt / MessagesController.getInstance().ratingDecay);
        Collections.sort(inlineBots, new Comparator<TLRPC.TL_topPeer>() {
            @Override
            public int compare(TLRPC.TL_topPeer lhs, TLRPC.TL_topPeer rhs) {
                if (lhs.rating > rhs.rating) {
                    return -1;
                } else if (lhs.rating < rhs.rating) {
                    return 1;
                }
                return 0;
            }
        });
        if (inlineBots.size() > 20) {
            inlineBots.remove(inlineBots.size() - 1);
        }
        savePeer(uid, 1, peer.rating);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
    }

    public static void removeInline(final int uid) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            if (inlineBots.get(a).peer.user_id == uid) {
                inlineBots.remove(a);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryBotsInline();
                req.peer = MessagesController.getInputPeer(uid);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                deletePeer(uid, 1);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInlineHints);
                return;
            }
        }
    }

    public static void removePeer(final int uid) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < hints.size(); a++) {
            if (hints.get(a).peer.user_id == uid) {
                hints.remove(a);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryCorrespondents();
                req.peer = MessagesController.getInputPeer(uid);
                deletePeer(uid, 0);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                return;
            }
        }
    }

    public static void increasePeerRaiting(final long did) {
        final int lower_id = (int) did;
        if (lower_id <= 0) {
            return;
        }
        //remove chats and bots for now
        final TLRPC.User user = lower_id > 0 ? MessagesController.getInstance().getUser(lower_id) : null;
        //final TLRPC.Chat chat = lower_id < 0 ? MessagesController.getInstance().getChat(-lower_id) : null;
        if (user == null || user.bot/*&& chat == null || ChatObject.isChannel(chat) && !chat.megagroup*/) {
            return;
        }
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                double dt = 0;
                try {
                    int lastTime = 0;
                    int lastMid = 0;
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT MAX(mid), MAX(date) FROM messages WHERE uid = %d AND out = 1", did));
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                        lastTime = cursor.intValue(1);
                    }
                    cursor.dispose();
                    if (lastMid > 0) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT date FROM messages WHERE uid = %d AND mid < %d AND out = 1 ORDER BY date DESC", did, lastMid));
                        if (cursor.next()) {
                            dt = (lastTime - cursor.intValue(0));
                        }
                        cursor.dispose();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final double dtFinal = dt;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        TLRPC.TL_topPeer peer = null;
                        for (int a = 0; a < hints.size(); a++) {
                            TLRPC.TL_topPeer p = hints.get(a);
                            if (lower_id < 0 && (p.peer.chat_id == -lower_id || p.peer.channel_id == -lower_id) || lower_id > 0 && p.peer.user_id == lower_id) {
                                peer = p;
                                break;
                            }
                        }
                        if (peer == null) {
                            peer = new TLRPC.TL_topPeer();
                            if (lower_id > 0) {
                                peer.peer = new TLRPC.TL_peerUser();
                                peer.peer.user_id = lower_id;
                            } else {
                                peer.peer = new TLRPC.TL_peerChat();
                                peer.peer.chat_id = -lower_id;
                            }
                            hints.add(peer);
                        }
                        peer.rating += Math.exp(dtFinal / MessagesController.getInstance().ratingDecay);
                        Collections.sort(hints, new Comparator<TLRPC.TL_topPeer>() {
                            @Override
                            public int compare(TLRPC.TL_topPeer lhs, TLRPC.TL_topPeer rhs) {
                                if (lhs.rating > rhs.rating) {
                                    return -1;
                                } else if (lhs.rating < rhs.rating) {
                                    return 1;
                                }
                                return 0;
                            }
                        });

                        savePeer((int) did, 0, peer.rating);

                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadHints);
                    }
                });
            }
        });
    }

    private static void savePeer(final int did, final int type, final double rating) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                    state.requery();
                    state.bindInteger(1, did);
                    state.bindInteger(2, type);
                    state.bindDouble(3, rating);
                    state.bindInteger(4, (int) System.currentTimeMillis() / 1000);
                    state.step();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private static void deletePeer(final int did, final int type) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().executeFast(String.format(Locale.US, "DELETE FROM chat_hints WHERE did = %d AND type = %d", did, type)).stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }
}
