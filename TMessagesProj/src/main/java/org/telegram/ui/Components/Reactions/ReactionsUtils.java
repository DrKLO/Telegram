package org.telegram.ui.Components.Reactions;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
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

    public static boolean compare(TLRPC.Reaction reaction,  TLRPC.Reaction reaction2) {
        if (reaction instanceof TLRPC.TL_reactionEmoji && reaction2 instanceof TLRPC.TL_reactionEmoji && TextUtils.equals(((TLRPC.TL_reactionEmoji) reaction).emoticon, ((TLRPC.TL_reactionEmoji) reaction2).emoticon)) {
            return true;
        }
        if (reaction instanceof TLRPC.TL_reactionCustomEmoji && reaction2 instanceof TLRPC.TL_reactionCustomEmoji && ((TLRPC.TL_reactionCustomEmoji) reaction).document_id == ((TLRPC.TL_reactionCustomEmoji) reaction2).document_id) {
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

    public static void applyForStoryViews(TLRPC.Reaction oldReaction, TLRPC.Reaction newReaction, TL_stories.StoryViews views) {
        boolean found = false;
        if (views == null) {
            return;
        }
        for (int i = 0; i < views.reactions.size(); i++) {
            TLRPC.ReactionCount reactionCount = views.reactions.get(i);
            if (oldReaction != null) {
                if (compare(reactionCount.reaction, oldReaction)) {
                    reactionCount.count--;
                    if (reactionCount.count <= 0) {
                        views.reactions.remove(i);
                        i--;
                        continue;
                    }
                }
            }
            if (newReaction != null) {
                if (compare(reactionCount.reaction, newReaction)) {
                    reactionCount.count++;
                    found = true;
                }
            }
        }
        if (!found) {
            TLRPC.ReactionCount reactionCount = new TLRPC.TL_reactionCount();
            reactionCount.count = 1;
            reactionCount.reaction = newReaction;
            views.reactions.add(reactionCount);
        }
    }
}
