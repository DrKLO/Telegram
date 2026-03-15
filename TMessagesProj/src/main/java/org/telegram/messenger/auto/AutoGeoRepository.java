package org.telegram.messenger.auto;

import android.util.LongSparseArray;

import androidx.annotation.NonNull;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

final class AutoGeoRepository {

    enum Status {
        LOADING,
        ABSENT,
        PRESENT
    }

    static final class State {
        static final State LOADING = new State(Status.LOADING, null);
        static final State ABSENT = new State(Status.ABSENT, null);

        final Status status;
        final GeoExtractor.GeoResult result;

        private State(Status status, GeoExtractor.GeoResult result) {
            this.status = status;
            this.result = result;
        }

        static State present(GeoExtractor.GeoResult result) {
            return new State(Status.PRESENT, result);
        }
    }

    private final AccountInstance accountInstance;
    private final LongSparseArray<State> stateByDialogId = new LongSparseArray<>();
    private final LongSparseArray<Boolean> inFlightByDialogId = new LongSparseArray<>();
    private Runnable onStateChanged;

    AutoGeoRepository(@NonNull AccountInstance accountInstance, @NonNull Runnable onStateChanged) {
        this.accountInstance = accountInstance;
        this.onStateChanged = onStateChanged;
    }

    void setOnStateChanged(@NonNull Runnable onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    State getState(long dialogId) {
        return stateByDialogId.get(dialogId);
    }

    void requestIfVisible(long dialogId) {
        State state = stateByDialogId.get(dialogId);
        if (state != null) {
            return;
        }
        if (inFlightByDialogId.get(dialogId, false)) {
            return;
        }
        stateByDialogId.put(dialogId, State.LOADING);
        inFlightByDialogId.put(dialogId, true);
        accountInstance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<MessageObject> messages = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = accountInstance.getMessagesStorage().getDatabase()
                        .queryFinalized(
                                "SELECT data FROM messages_v2 WHERE uid = ? ORDER BY mid DESC LIMIT ?",
                                dialogId, 20);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message != null) {
                        messages.add(new MessageObject(accountInstance.getCurrentAccount(), message, false, false));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            GeoExtractor.GeoResult result = GeoExtractor.extractFromMessages(messages, 20);
            AndroidUtilities.runOnUIThread(() -> {
                inFlightByDialogId.remove(dialogId);
                stateByDialogId.put(dialogId, result != null ? State.present(result) : State.ABSENT);
                if (onStateChanged != null) {
                    onStateChanged.run();
                }
            });
        });
    }

    void invalidateDialog(long dialogId) {
        stateByDialogId.remove(dialogId);
        inFlightByDialogId.remove(dialogId);
    }

    void invalidateDialogs(@NonNull List<Long> dialogIds) {
        for (int i = 0; i < dialogIds.size(); i++) {
            invalidateDialog(dialogIds.get(i));
        }
    }

    void requestIfVisible(@NonNull List<Long> dialogIds) {
        for (int i = 0; i < dialogIds.size(); i++) {
            requestIfVisible(dialogIds.get(i));
        }
    }

    void clear() {
        stateByDialogId.clear();
        inFlightByDialogId.clear();
    }
}
