package org.telegram.ui.Components;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class FormattedDateSpan extends URLSpan {
    public final String originalText;
    public final TLRPC.TL_messageEntityFormattedDate entity;
    public final TextStyleSpan.TextStyleRun style;
    public final boolean applied;

    public FormattedDateSpan(String originalText, TextStyleSpan.TextStyleRun run, TLRPC.TL_messageEntityFormattedDate entity) {
        super(originalText);
        this.originalText = originalText;
        this.entity = entity;
        this.style = run;
        this.applied = false;
    }

    private FormattedDateSpan(FormattedDateSpan span, boolean applied) {
        super(span.originalText);
        this.originalText = span.originalText;
        this.entity = span.entity;
        this.style = span.style;
        this.applied = applied;
    }

    public boolean needReplaceText() {
        return entity.flags != 0;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint p) {
        int l = p.linkColor;
        int c = p.getColor();
        super.updateDrawState(p);
        if (style != null) {
            style.applyStyle(p);
        }
        p.setUnderlineText(l == c);
    }

    @Override
    public void onClick(@NonNull View widget) {
        // nothing
    }



    public static CharSequence applyFormatedDateEntities(CharSequence text) {
        return rebuildFormatedDateEntities(text, true);
    }

    public static CharSequence restoreFormatedDateEntities(CharSequence text) {
        return rebuildFormatedDateEntities(text, false);
    }

    public static ArrayList<Integer> getAllRelativeDates(CharSequence text) {
        final Spanned spannedText;
        if (text instanceof Spanned) {
            spannedText = (Spanned) text;
        } else {
            return null;
        }

        FormattedDateSpan[] spans = spannedText.getSpans(0, spannedText.length(), FormattedDateSpan.class);
        ArrayList<Integer> result = null;

        for (FormattedDateSpan span : spans) {
            if (!span.entity.relative) {
                continue;
            }

            if (result == null) {
                result = new ArrayList<>(spans.length);
            }

            result.add(span.entity.date);
        }

        return result;
    }

    private static CharSequence rebuildFormatedDateEntities(CharSequence text, boolean apply) {
        final Spanned spannedText;
        if (text instanceof Spanned) {
            spannedText = (Spanned) text;
        } else {
            return text; // nothing to replace
        }

        CharSequence result = text;
        SpannableStringBuilder ssb = null;

        FormattedDateSpan[] spans = spannedText.getSpans(0, spannedText.length(), FormattedDateSpan.class);
        for (FormattedDateSpan span : spans) {
            if (!span.needReplaceText() || span.applied == apply && !(apply && span.entity.relative)) {
                continue;
            }

            if (ssb == null) {
                result = ssb = new SpannableStringBuilder(spannedText);
            }

            final int start = ssb.getSpanStart(span);
            final int end = ssb.getSpanEnd(span);
            final String toReplace = apply ? LocaleController.formatEntityFormattedDate(span.entity) : span.originalText;

            ssb.replace(start, end, toReplace);

            final int startAfterReplace = ssb.getSpanStart(span);
            final int endAfterReplace = ssb.getSpanEnd(span);
            ssb.removeSpan(span);
            ssb.setSpan(new FormattedDateSpan(span, apply), startAfterReplace, endAfterReplace, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return result;
    }
}
