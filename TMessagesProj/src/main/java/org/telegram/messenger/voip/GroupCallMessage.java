package org.telegram.messenger.voip;

import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import javax.annotation.Nullable;

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
}
