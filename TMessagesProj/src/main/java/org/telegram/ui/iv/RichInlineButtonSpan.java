package org.telegram.ui.iv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.Theme;

/** Atomic inline representation of a {@link TL_iv.textButton} while its text is being edited. */
public class RichInlineButtonSpan extends ReplacementSpan {

    private static final int MAX_WIDTH = 240;

    private final TL_iv.textButton button;
    private RichMessageLayout.RichButtonSpan renderedSpan;
    private View attachedView;
    private int currentAccount = UserConfig.selectedAccount;
    private Theme.ResourcesProvider resourcesProvider;

    public RichInlineButtonSpan(TL_iv.textButton button) {
        this.button = button;
    }

    public TL_iv.textButton getButton() {
        return button;
    }

    public static boolean isSupported(TL_keyboard.InlineButtonType type) {
        return type instanceof TL_keyboard.TL_inlineButtonTypeUrl
            || type instanceof TL_keyboard.TL_inlineButtonTypeCopy
            || type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile;
    }

    public void removeNestedReplacementSpans(Spannable text) {
        if (text == null) return;
        final int start = text.getSpanStart(this);
        final int end = text.getSpanEnd(this);
        if (start < 0 || end <= start) return;
        for (ReplacementSpan span : text.getSpans(start, end, ReplacementSpan.class)) {
            if (span == this) continue;
            final int spanStart = text.getSpanStart(span);
            final int spanEnd = text.getSpanEnd(span);
            if (spanStart < end && spanEnd > start) {
                text.removeSpan(span);
            }
        }
    }

    public void bind(View view, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        if (renderedSpan != null && attachedView != null) {
            renderedSpan.detach(attachedView);
        }
        this.attachedView = view;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        renderedSpan = null;
        ensureRenderer();
    }

    public void detach(View view) {
        if (renderedSpan != null && attachedView == view) {
            renderedSpan.detach(view);
            attachedView = null;
        }
    }

    private RichMessageLayout.RichButtonSpan ensureRenderer() {
        if (renderedSpan == null) {
            renderedSpan = RichMessageLayout.createEditorButtonSpan(
                currentAccount, AndroidUtilities.dp(MAX_WIDTH), resourcesProvider, button);
            if (attachedView != null) {
                renderedSpan.attach(attachedView);
            }
        }
        return renderedSpan;
    }

    public void setPressed(boolean pressed) {
        ensureRenderer().setPressed(pressed);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return ensureRenderer().getSize(paint, text, start, end, fm);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        ensureRenderer().draw(canvas, text, start, end, x, top, y, bottom, paint);
    }
}
