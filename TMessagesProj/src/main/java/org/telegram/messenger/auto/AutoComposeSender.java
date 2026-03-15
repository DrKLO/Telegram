package org.telegram.messenger.auto;

import androidx.annotation.NonNull;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

final class AutoComposeSender {

    private final int currentAccount;
    private final AccountInstance accountInstance;

    AutoComposeSender(int currentAccount, @NonNull AccountInstance accountInstance) {
        this.currentAccount = currentAccount;
        this.accountInstance = accountInstance;
    }

    void sendText(long dialogId, int maxId, @NonNull String text, Runnable onSent) {
        AndroidUtilities.runOnUIThread(() -> {
            MessageObject replyToMessage = null;
            if (maxId != 0) {
                TLRPC.TL_message reply = new TLRPC.TL_message();
                reply.message = "";
                reply.id = maxId;
                reply.peer_id = accountInstance.getMessagesController().getPeer(dialogId);
                replyToMessage = new MessageObject(currentAccount, reply, false, false);
            }
            accountInstance.getSendMessagesHelper().sendMessage(
                    SendMessagesHelper.SendMessageParams.of(text, dialogId, replyToMessage, null,
                            null, true, null, null, null, true, 0, 0, null, false));
            accountInstance.getMessagesController().markDialogAsRead(
                    dialogId, maxId, maxId, 0, false, 0, 0, true, 0);
            if (onSent != null) {
                onSent.run();
            }
        });
    }
}
