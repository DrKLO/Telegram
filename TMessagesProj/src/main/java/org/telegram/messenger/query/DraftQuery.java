/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.query;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DraftQuery {

    private static HashMap<Long, TLRPC.DraftMessage> drafts = new HashMap<>();
    private static HashMap<Long, TLRPC.Message> draftMessages = new HashMap<>();
    private static boolean inTransaction;
    private static SharedPreferences preferences;
    private static boolean loadingDrafts;

    static {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("drafts", Activity.MODE_PRIVATE);
        Map<String, ?> values = preferences.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            try {
                String key = entry.getKey();
                long did = Utilities.parseLong(key);
                byte[] bytes = Utilities.hexToBytes((String) entry.getValue());
                SerializedData serializedData = new SerializedData(bytes);
                if (key.startsWith("r_")) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    if (message != null) {
                        draftMessages.put(did, message);
                    }
                } else {
                    TLRPC.DraftMessage draftMessage = TLRPC.DraftMessage.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    if (draftMessage != null) {
                        drafts.put(did, draftMessage);
                    }
                }
            } catch (Exception e) {
                //igonre
            }
        }
    }

    public static void loadDrafts() {
        if (UserConfig.draftsLoaded || loadingDrafts) {
            return;
        }
        loadingDrafts = true;
        TLRPC.TL_messages_getAllDrafts req = new TLRPC.TL_messages_getAllDrafts();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                MessagesController.getInstance().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        UserConfig.draftsLoaded = true;
                        loadingDrafts = false;
                        UserConfig.saveConfig(false);
                    }
                });
            }
        });
    }

    public static void cleanup() {
        drafts.clear();
        draftMessages.clear();
        preferences.edit().clear().commit();
    }

    public static TLRPC.DraftMessage getDraft(long did) {
        return drafts.get(did);
    }

    public static TLRPC.Message getDraftMessage(long did) {
        return draftMessages.get(did);
    }

    public static void saveDraft(long did, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage) {
        saveDraft(did, message, entities, replyToMessage, noWebpage, false);
    }

    public static void saveDraft(long did, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage, boolean clean) {
        TLRPC.DraftMessage draftMessage;
        if (!TextUtils.isEmpty(message) || replyToMessage != null) {
            draftMessage = new TLRPC.TL_draftMessage();
        } else {
            draftMessage = new TLRPC.TL_draftMessageEmpty();
        }
        draftMessage.date = (int) (System.currentTimeMillis() / 1000);
        draftMessage.message = message == null ? "" : message.toString();
        draftMessage.no_webpage = noWebpage;
        if (replyToMessage != null) {
            draftMessage.reply_to_msg_id = replyToMessage.id;
            draftMessage.flags |= 1;
        }
        if (entities != null && !entities.isEmpty()) {
            draftMessage.entities = entities;
            draftMessage.flags |= 8;
        }

        TLRPC.DraftMessage currentDraft = drafts.get(did);
        if (!clean) {
            if (currentDraft != null && currentDraft.message.equals(draftMessage.message) && currentDraft.reply_to_msg_id == draftMessage.reply_to_msg_id && currentDraft.no_webpage == draftMessage.no_webpage ||
                    currentDraft == null && TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0) {
                return;
            }
        }

        saveDraft(did, draftMessage, replyToMessage, false);
        int lower_id = (int) did;
        if (lower_id != 0) {
            TLRPC.TL_messages_saveDraft req = new TLRPC.TL_messages_saveDraft();
            req.peer = MessagesController.getInputPeer(lower_id);
            if (req.peer == null) {
                return;
            }
            req.message = draftMessage.message;
            req.no_webpage = draftMessage.no_webpage;
            req.reply_to_msg_id = draftMessage.reply_to_msg_id;
            req.entities = draftMessage.entities;
            req.flags = draftMessage.flags;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
        MessagesController.getInstance().sortDialogs(null);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static void saveDraft(final long did, TLRPC.DraftMessage draft, TLRPC.Message replyToMessage, boolean fromServer) {
        SharedPreferences.Editor editor = preferences.edit();
        if (draft == null || draft instanceof TLRPC.TL_draftMessageEmpty) {
            drafts.remove(did);
            draftMessages.remove(did);
            preferences.edit().remove("" + did).remove("r_" + did).commit();
        } else {
            drafts.put(did, draft);
            try {
                SerializedData serializedData = new SerializedData(draft.getObjectSize());
                draft.serializeToStream(serializedData);
                editor.putString("" + did, Utilities.bytesToHex(serializedData.toByteArray()));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (replyToMessage == null) {
            draftMessages.remove(did);
            editor.remove("r_" + did);
        } else {
            draftMessages.put(did, replyToMessage);
            SerializedData serializedData = new SerializedData(replyToMessage.getObjectSize());
            replyToMessage.serializeToStream(serializedData);
            editor.putString("r_" + did, Utilities.bytesToHex(serializedData.toByteArray()));
        }
        editor.commit();
        if (fromServer) {
            if (draft.reply_to_msg_id != 0 && replyToMessage == null) {
                int lower_id = (int) did;
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (lower_id > 0) {
                    user = MessagesController.getInstance().getUser(lower_id);
                } else {
                    chat = MessagesController.getInstance().getChat(-lower_id);
                }
                if (user != null || chat != null) {
                    long messageId = draft.reply_to_msg_id;
                    final int channelIdFinal;
                    if (ChatObject.isChannel(chat)) {
                        messageId |= ((long) chat.id) << 32;
                        channelIdFinal = chat.id;
                    } else {
                        channelIdFinal = 0;
                    }
                    final long messageIdFinal = messageId;

                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TLRPC.Message message = null;
                                SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM messages WHERE mid = %d", messageIdFinal));
                                if (cursor.next()) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                    }
                                }
                                cursor.dispose();
                                if (message == null) {
                                    if (channelIdFinal != 0) {
                                        final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                                        req.channel = MessagesController.getInputChannel(channelIdFinal);
                                        req.id.add((int) messageIdFinal);
                                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (error == null) {
                                                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                                    if (!messagesRes.messages.isEmpty()) {
                                                        saveDraftReplyMessage(did, messagesRes.messages.get(0));
                                                    }
                                                }
                                            }
                                        });
                                    } else {
                                        TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                                        req.id.add((int) messageIdFinal);
                                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (error == null) {
                                                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                                    if (!messagesRes.messages.isEmpty()) {
                                                        saveDraftReplyMessage(did, messagesRes.messages.get(0));
                                                    }
                                                }
                                            }
                                        });
                                    }
                                } else {
                                    saveDraftReplyMessage(did, message);
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    });
                }
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.newDraftReceived, did);
        }
    }

    private static void saveDraftReplyMessage(final long did, final TLRPC.Message message) {
        if (message == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                TLRPC.DraftMessage draftMessage = drafts.get(did);
                if (draftMessage != null && draftMessage.reply_to_msg_id == message.id) {
                    draftMessages.put(did, message);
                    SerializedData serializedData = new SerializedData(message.getObjectSize());
                    message.serializeToStream(serializedData);
                    preferences.edit().putString("r_" + did, Utilities.bytesToHex(serializedData.toByteArray())).commit();
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.newDraftReceived, did);
                }
            }
        });
    }

    public static void cleanDraft(long did, boolean replyOnly) {
        TLRPC.DraftMessage draftMessage = drafts.get(did);
        if (draftMessage == null) {
            return;
        }
        if (!replyOnly) {
            drafts.remove(did);
            draftMessages.remove(did);
            preferences.edit().remove("" + did).remove("r_" + did).commit();
            MessagesController.getInstance().sortDialogs(null);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        } else if (draftMessage.reply_to_msg_id != 0) {
            draftMessage.reply_to_msg_id = 0;
            draftMessage.flags &= ~1;
            saveDraft(did, draftMessage.message, draftMessage.entities, null, draftMessage.no_webpage, true);
        }
    }

    public static void beginTransaction() {
        inTransaction = true;
    }

    public static void endTransaction() {
        inTransaction = false;
    }
}
