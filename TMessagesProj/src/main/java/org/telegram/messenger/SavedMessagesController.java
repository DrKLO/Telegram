package org.telegram.messenger;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

public class SavedMessagesController {

    private final int currentAccount;

    public boolean loading, loaded;
    public LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<ArrayList<MessageObject>>();

    public SavedMessagesController(int account) {
        this.currentAccount = account;
    }

    public void getSavedMessagesDialogs() {
        if (loaded || loading) {
            return;
        }
        loading = true;
        final long myself = UserConfig.getInstance(currentAccount).getClientUserId();
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = storage.getDatabase();
            SQLiteCursor cursor = null;
            final LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
            try {
                cursor = database.queryFinalized("SELECT data, mid, date, send_state, read_state, custom_params FROM messages_v2 WHERE out = 0 AND uid = ?", myself);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message.fwd_from == null || message.fwd_from.saved_from_peer == null) {
                            continue;
                        }
                        long did = DialogObject.getPeerDialogId(message.fwd_from.saved_from_peer);

                        message.id = cursor.intValue(1);
                        message.date = cursor.intValue(2);
                        message.send_state = cursor.intValue(3);
                        MessageObject.setUnreadFlags(message, cursor.intValue(4));

                        MessageObject messageObject = new MessageObject(currentAccount, message, true, true);
                        ArrayList<MessageObject> messageObjects = messages.get(did);
                        if (messageObjects == null) {
                            messages.put(did, messageObjects = new ArrayList<>());
                        }
                        messageObjects.add(messageObject);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    SavedMessagesController.this.messages.clear();
                    SavedMessagesController.this.messages.putAll(messages);
                    loading = false;
                });
            } catch (SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                    cursor = null;
                }
            }
        });
    }
}
