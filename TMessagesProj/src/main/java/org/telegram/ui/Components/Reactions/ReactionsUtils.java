package org.telegram.ui.Components.Reactions;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiSpan;

public class ReactionsUtils {

    public static final String APPEAR_ANIMATION_FILTER = "30_30_nolimit";
    public static final String SELECT_ANIMATION_FILTER = "60_60_pcache";
    public static final String ACTIVATE_ANIMATION_FILTER = "30_30_pcache";

    public static boolean compare(TLRPC.Reaction reaction, ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        if (reaction instanceof TLRPC.TL_reactionEmoji && visibleReaction.documentId == 0 && TextUtils.equals(((TLRPC.TL_reactionEmoji) reaction).emoticon, visibleReaction.emojicon)) {
            return true;
        }
        if (reaction instanceof TLRPC.TL_reactionCustomEmoji && visibleReaction.documentId != 0 && ((TLRPC.TL_reactionCustomEmoji) reaction).document_id == visibleReaction.documentId) {
            return true;
        }
        return false;
    }

    public static TLRPC.Reaction toTLReaction(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        if (visibleReaction.emojicon != null) {
            TLRPC.TL_reactionEmoji emoji = new TLRPC.TL_reactionEmoji();
            emoji.emoticon = visibleReaction.emojicon;
            return emoji;
        } else {
            TLRPC.TL_reactionCustomEmoji emoji = new TLRPC.TL_reactionCustomEmoji();
            emoji.document_id = visibleReaction.documentId;
            return emoji;
        }

    }

    public static CharSequence reactionToCharSequence(TLRPC.Reaction reaction) {
        if (reaction instanceof TLRPC.TL_reactionEmoji){
            return ((TLRPC.TL_reactionEmoji) reaction).emoticon;
        }
        if (reaction instanceof TLRPC.TL_reactionCustomEmoji){
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("d");
            spannableStringBuilder.setSpan(new AnimatedEmojiSpan(((TLRPC.TL_reactionCustomEmoji) reaction).document_id, null), 0, 1, 0);
            return spannableStringBuilder;
        }
        return "";
    }
}
