package org.telegram.ui.Components.Premium.boosts.cells.msg;

import android.graphics.Rect;
import android.text.StaticLayout;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;

/**
 * A giveaway and its results are cards drawn by hand, and are no views of their own. What a
 * screen reader needs of either is the same: what the card says, and the row of chats or
 * winners drawn on it as buttons that open them.
 */
public interface GiveawayAccessibilityCard {
    /** Everything the card holds, in the order it is drawn. */
    CharSequence getAccessibilityText();

    /** How many of the chats or winners it names can be opened. */
    int getAccessibilityButtonCount();

    CharSequence getAccessibilityButtonTitle(int index);

    Rect getAccessibilityButtonBounds(int index);

    static void appendLayout(StringBuilder sb, StaticLayout layout) {
        if (layout != null) {
            append(sb, layout.getText());
        }
    }

    static void append(StringBuilder sb, CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        // the card is laid out in lines, and a line break is nothing to say
        sb.append(AndroidUtilities.replaceNewLines(text));
    }
}
