package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.PremiumGradient;

public class StatusBadgeComponent {

    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private Drawable verifiedDrawable;

    public StatusBadgeComponent(View parentView) {
        this(parentView, 18);
    }

    public StatusBadgeComponent(View parentView, int sizeDp) {
        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parentView, AndroidUtilities.dp(sizeDp));
    }

    public Drawable updateDrawable(TLObject object, int colorFilter, boolean animated) {
        if (object instanceof TLRPC.User) {
            return updateDrawable((TLRPC.User) object, null, colorFilter, animated);
        } else if (object instanceof TLRPC.Chat) {
            return updateDrawable(null, (TLRPC.Chat) object, colorFilter, animated);
        }
        return updateDrawable(null, null, colorFilter, animated);
    }

    public Drawable updateDrawable(TLRPC.User user, TLRPC.Chat chat, int colorFilter, boolean animated) {
        if (chat != null && chat.verified) {
            statusDrawable.set(verifiedDrawable = (verifiedDrawable == null ? new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable) : verifiedDrawable), animated);
            statusDrawable.setColor(null);
        } else if (chat != null && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(chat.emoji_status), animated);
            statusDrawable.setColor(colorFilter);
        } else if (user != null && user.verified) {
            statusDrawable.set(verifiedDrawable = (verifiedDrawable == null ? new CombinedDrawable(Theme.dialogs_verifiedDrawable, Theme.dialogs_verifiedCheckDrawable) : verifiedDrawable), animated);
            statusDrawable.setColor(null);
        } else if (user != null && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0) {
            statusDrawable.set(DialogObject.getEmojiStatusDocumentId(user.emoji_status), animated);
            statusDrawable.setColor(colorFilter);
        } else if (user != null && user.premium) {
            statusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, animated);
            statusDrawable.setColor(colorFilter);
        } else {
            statusDrawable.set((Drawable) null, animated);
            statusDrawable.setColor(null);
        }
        return statusDrawable;
    }

    public Drawable getDrawable() {
        return statusDrawable;
    }

    public void onAttachedToWindow() {
        statusDrawable.attach();
    }

    public void onDetachedFromWindow() {
        statusDrawable.detach();
    }
}
