package org.telegram.ui.Wallet;

import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.ui.Components.AnimatedTextView;

/**
 * What a screen reader is given for the wallet's views, which draw icons as part of their text
 * and are pressed for what their icons say.
 */
public class WalletAccessibility {

    // the parts of a text drawn as icons, such as the sign of a gram or the arrows that switch
    // a currency, are left out of what is read
    public static CharSequence withoutIcons(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        final Spanned spanned = (Spanned) text;
        final ReplacementSpan[] spans = spanned.getSpans(0, spanned.length(), ReplacementSpan.class);
        if (spans.length == 0) {
            return text.toString();
        }
        final StringBuilder sb = new StringBuilder(spanned.length());
        for (int i = 0; i < spanned.length(); i++) {
            boolean icon = false;
            for (ReplacementSpan span : spans) {
                if (i >= spanned.getSpanStart(span) && i < spanned.getSpanEnd(span)) {
                    icon = true;
                    break;
                }
            }
            if (!icon) {
                sb.append(spanned.charAt(i));
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    // a view whose text changes as it goes is read without its icons, and a press on it is said
    // with what it does when that is given
    public static void readWithoutIcons(AnimatedTextView view, CharSequence clickLabel) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription(withoutIcons(view.getText()));
                if (clickLabel != null) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, clickLabel));
                }
            }
        });
    }

    // a press on a view is said with what it does
    public static void setClickLabel(View view, CharSequence clickLabel) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, clickLabel));
            }
        });
    }
}
