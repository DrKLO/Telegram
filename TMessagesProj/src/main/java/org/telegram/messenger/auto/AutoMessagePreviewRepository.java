package org.telegram.messenger.auto;

import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AutoMessagePreviewRepository {

    private static final int MAX_MESSAGES = 25;

    static final class Projection {
        static final Projection EMPTY = new Projection(Collections.emptyList(), null, 0, 0L);

        final List<MessageObject> messages;
        final String primaryPreviewText;
        final int unreadPreviewCount;
        final long signature;

        Projection(@NonNull List<MessageObject> messages,
                   @Nullable String primaryPreviewText,
                   int unreadPreviewCount,
                   long signature) {
            this.messages = messages;
            this.primaryPreviewText = primaryPreviewText;
            this.unreadPreviewCount = unreadPreviewCount;
            this.signature = signature;
        }
    }

    private final AccountInstance accountInstance;
    private final AutoDialogPreviewFormatter previewFormatter = new AutoDialogPreviewFormatter();
    private final LongSparseArray<Projection> projectionsByDialogId = new LongSparseArray<>();
    private final LongSparseArray<Boolean> inFlightByDialogId = new LongSparseArray<>();
    private final LongSparseArray<Boolean> staleByDialogId = new LongSparseArray<>();
    private Runnable onStateChanged;
    private long version;

    AutoMessagePreviewRepository(@NonNull AccountInstance accountInstance, @NonNull Runnable onStateChanged) {
        this.accountInstance = accountInstance;
        this.onStateChanged = onStateChanged;
    }

    void setOnStateChanged(@NonNull Runnable onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    long getVersion() {
        return version;
    }

    long getDialogSignature(long dialogId) {
        Projection projection = projectionsByDialogId.get(dialogId);
        return projection != null ? projection.signature : 0L;
    }

    List<MessageObject> getMessages(long dialogId) {
        return getProjection(dialogId).messages;
    }

    @NonNull
    Projection getProjection(long dialogId) {
        Projection projection = projectionsByDialogId.get(dialogId);
        return projection != null ? projection : Projection.EMPTY;
    }

    void requestIfVisible(long dialogId) {
        Projection currentProjection = projectionsByDialogId.get(dialogId);
        if (currentProjection != null
                && currentProjection != Projection.EMPTY
                && !staleByDialogId.get(dialogId, false)) {
            return;
        }
        if (inFlightByDialogId.get(dialogId, false)) {
            return;
        }
        inFlightByDialogId.put(dialogId, true);
        accountInstance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<MessageObject> messages = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = accountInstance.getMessagesStorage().getDatabase()
                        .queryFinalized(
                                "SELECT data, read_state FROM messages_v2 WHERE uid = ? ORDER BY date DESC, mid DESC LIMIT ?",
                                dialogId, MAX_MESSAGES);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message != null) {
                        MessageObject.setUnreadFlags(message, cursor.intValue(1));
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
            Projection projection = buildProjection(messages);
            AndroidUtilities.runOnUIThread(() -> {
                inFlightByDialogId.remove(dialogId);
                staleByDialogId.remove(dialogId);
                projectionsByDialogId.put(dialogId, projection);
                version++;
                if (onStateChanged != null) {
                    onStateChanged.run();
                }
            });
        });
    }

    void requestIfVisible(@NonNull List<Long> dialogIds) {
        for (int i = 0; i < dialogIds.size(); i++) {
            requestIfVisible(dialogIds.get(i));
        }
    }

    void invalidateDialog(long dialogId) {
        if (projectionsByDialogId.indexOfKey(dialogId) >= 0) {
            staleByDialogId.put(dialogId, true);
        }
        inFlightByDialogId.remove(dialogId);
    }

    void invalidateDialogs(@NonNull List<Long> dialogIds) {
        for (int i = 0; i < dialogIds.size(); i++) {
            invalidateDialog(dialogIds.get(i));
        }
    }

    void clear() {
        projectionsByDialogId.clear();
        inFlightByDialogId.clear();
        staleByDialogId.clear();
        version++;
    }

    @NonNull
    private Projection buildProjection(@NonNull List<MessageObject> messages) {
        if (messages.isEmpty()) {
            return Projection.EMPTY;
        }
        long signature = 17L;
        int unreadPreviewCount = 0;
        String primaryPreviewText = null;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject == null || messageObject.messageOwner == null) {
                continue;
            }
            signature = signature * 31 + messageObject.getId();
            signature = signature * 31 + (messageObject.isOut() ? 1 : 0);
            signature = signature * 31 + (messageObject.isUnread() ? 1 : 0);
            signature = signature * 31 + messageObject.messageOwner.date;
            String previewText = previewFormatter.format(messageObject);
            signature = signature * 31 + (previewText != null ? previewText.hashCode() : 0);
            if (!messageObject.isOut() && messageObject.isUnread()) {
                unreadPreviewCount++;
            }
            if (primaryPreviewText == null && previewText != null) {
                primaryPreviewText = previewText;
            }
        }
        return new Projection(Collections.unmodifiableList(new ArrayList<>(messages)), primaryPreviewText, unreadPreviewCount, signature);
    }
}
