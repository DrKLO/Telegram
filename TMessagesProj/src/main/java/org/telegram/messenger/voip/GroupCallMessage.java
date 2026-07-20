package org.telegram.messenger.voip;

import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;

import javax.annotation.Nullable;

import me.vkryl.core.BitwiseUtils;

public class GroupCallMessage {
    public final int currentAccount;
    public final long fromId;
    public final long randomId;
    public final TLRPC.TL_textWithEntities message;

    public final long reactionAnimatedEmojiId;
    public final @Nullable ReactionsLayoutInBubble.VisibleReaction visibleReaction;

    public GroupCallMessage(int currentAccount, long fromId, long randomId, TLRPC.TL_textWithEntities message) {
        this.currentAccount = currentAccount;
        this.fromId = fromId;
        this.randomId = randomId;
        this.message = message;

        long emojiId = 0;
        if (message.entities != null && message.entities.size() == 1) {
            TLRPC.MessageEntity entity = message.entities.get(0);
            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                TLRPC.TL_messageEntityCustomEmoji customEmoji = (TLRPC.TL_messageEntityCustomEmoji) entity;
                emojiId = customEmoji.document_id;
            }
        }

        ReactionsLayoutInBubble.VisibleReaction visibleReaction = null;
        if (emojiId != 0) {
            visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromCustomEmoji(emojiId);
        } else if (message.entities == null || message.entities.isEmpty()) {
            TLRPC.TL_availableReaction reaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(message.text);
            if (reaction != null) {
                visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(reaction);
            }
        }

        this.reactionAnimatedEmojiId = emojiId;
        this.visibleReaction = visibleReaction;
    }



    private int flags;
    private static final int FLAG_IS_OUT = 1;
    private static final int FLAG_SEND_DELAYED = 1 << 1; // The server response has not been received for more than a few seconds.
    private static final int FLAG_SEND_ERROR = 1 << 2;
    private static final int FLAG_SEND_CONFIRMED = 1 << 3;

    public void setIsOut(boolean isOut) {
        flags = BitwiseUtils.setFlag(flags, FLAG_IS_OUT, isOut);
    }

    public void setIsSendDelayed(boolean isDelayed) {
        flags = BitwiseUtils.setFlag(flags, FLAG_SEND_DELAYED, isDelayed);
    }

    public void setIsSendError(boolean isError) {
        flags = BitwiseUtils.setFlag(flags, FLAG_SEND_ERROR, isError);
    }

    public void setIsSendConfirmed(boolean isConfirmed) {
        flags = BitwiseUtils.setFlag(flags, FLAG_SEND_CONFIRMED, isConfirmed);
    }

    public boolean isOut() {
        return BitwiseUtils.hasFlag(flags, FLAG_IS_OUT);
    }

    public boolean isSendDelayed() {
        return BitwiseUtils.hasFlag(flags, FLAG_SEND_DELAYED);
    }

    public boolean isSendError() {
        return BitwiseUtils.hasFlag(flags, FLAG_SEND_ERROR);
    }

    public boolean isSendConfirmed() {
        return BitwiseUtils.hasFlag(flags, FLAG_SEND_CONFIRMED);
    }



    private final ArrayList<Runnable> listeners = new ArrayList<>();

    public void subscribeToStateUpdates(Runnable listener) {
        listeners.add(listener);
    }

    public void unsubscribeFromStateUpdates(Runnable listener) {
        listeners.remove(listener);
    }

    public void notifyStateUpdate() {
        for (Runnable r : listeners) {
            r.run();
        }
    }
}
