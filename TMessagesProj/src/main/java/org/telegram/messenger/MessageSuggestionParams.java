package org.telegram.messenger;

import androidx.annotation.Nullable;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.utils.tlutils.AmountUtils;

public class MessageSuggestionParams {
    public final @Nullable AmountUtils.Amount amount;
    public final long time;

    private MessageSuggestionParams(@Nullable AmountUtils.Amount amount, long time) {
        this.amount = amount;
        this.time = time;
    }

    public @Nullable TLRPC.SuggestedPost toTl() {
        TLRPC.SuggestedPost suggestedPost = new TLRPC.SuggestedPost();

        if (amount != null && !amount.isZero()) {
            suggestedPost.price = amount.toTl();
        }
        if (time > 0) {
            suggestedPost.schedule_date = (int) time;
            suggestedPost.flags |= TLObject.FLAG_0;
        }

        return suggestedPost;
    }

    public boolean isEmpty() {
        return (amount == null || amount.isZero()) && time <= 0;
    }

    public static MessageSuggestionParams empty() {
        return new MessageSuggestionParams(AmountUtils.Amount.fromDecimal(0, AmountUtils.Currency.STARS), 0);
    }

    public static MessageSuggestionParams of(TLRPC.SuggestedPost suggestedPost) {
        if (suggestedPost == null) {
            return empty();
        }

        return new MessageSuggestionParams(AmountUtils.Amount.of(suggestedPost.price), suggestedPost.schedule_date);
    }

    public static MessageSuggestionParams of(TLRPC.TL_messageActionSuggestedPostApproval approval) {
        return of(AmountUtils.Amount.of(approval.price), approval.schedule_date);
    }

    public static MessageSuggestionParams of(AmountUtils.Amount amount, long time) {
        return new MessageSuggestionParams(amount, time);
    }
}
