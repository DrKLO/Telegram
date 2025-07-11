package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.StringRes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class SuggestionOffer {
    public static final int PADDING_H = 24;
    public static final int PADDING_V = 14;

    private final Theme.ResourcesProvider resourcesProvider;

    public StaticLayout title;
    public ArrayList<Row> rows = new ArrayList<>(2);
    private int titleX;
    private int rowsTitleX;
    private int rowsInfoX;

    public static class Row {
        public final Text title;
        public final Text info;
        public Row(Text title, Text info) {
            this.title = title;
            this.info = info;
        }

        public int getHeight() {
            return (int) title.getHeight();
        }
    }

    public int height;
    public int width;

    public SuggestionOffer(int currentAccount, View cell, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void update(MessageObject messageObject) {
        final TLRPC.SuggestedPost suggestedPost = messageObject != null && messageObject.messageOwner != null ? messageObject.messageOwner.suggested_post : null;
        if (suggestedPost == null) {
            return;
        }

        MessageSuggestionParams suggestionOffer = MessageSuggestionParams.of(suggestedPost);

        final TextPaint paint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText3);

        height = dp(PADDING_V) * 2;
        float rowsTitleWidth = 0;
        float rowsInfoWidth = 0;
        rows.clear();

        if (suggestionOffer.amount != null && !suggestionOffer.amount.isZero()) {
            rows.add(new Row(
                new Text(getString(R.string.SuggestionOfferInfoPrice), paint),
                new Text(LocaleController.bold(suggestionOffer.amount.formatAsDecimalSpaced()), paint)
            ));
        }
        if (suggestedPost.schedule_date > 0) {
            rows.add(new Row(
                new Text(getString(R.string.SuggestionOfferInfoTime), paint),
                new Text(LocaleController.bold(LocaleController.formatDateTime(suggestedPost.schedule_date, true)), paint)
            ));
        }

        for (Row row: rows) {
            rowsTitleWidth = Math.max(rowsTitleWidth, row.title.getWidth());
            rowsInfoWidth = Math.max(rowsInfoWidth, row.info.getWidth());
            height += row.getHeight();
            height += dp(7);
        }

        final int rowsRealWidth = (int) (rowsTitleWidth + rowsInfoWidth + dp(11));
        final int titleMaxWidth = (int) Math.max(rowsRealWidth, dp(160));
        int titleRealWidth = 0;

        final String author = DialogObject.getName(messageObject.getFromChatId());
        final int flags = messageObject.getEditedSuggestionFlags();
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (flags == 0) {
            if (messageObject.isOutOwner()) {
                ssb.append(LocaleController.getString(R.string.SuggestionOfferInfoTitleYou));
            } else {
                ssb.append(LocaleController.formatString(R.string.SuggestionOfferInfoTitle, author));
            }
        } else {
            final String replyAuthor = messageObject.replyMessageObject != null ?
                DialogObject.getName(messageObject.replyMessageObject.getFromChatId()) : "";

            StringBuilder ssb2 = new StringBuilder();
            final int flagsCount =
                (((flags & MessageObject.SUGGESTION_FLAG_EDIT_TEXT) != 0) ? 1 : 0) +
                (((flags & MessageObject.SUGGESTION_FLAG_EDIT_TIME) != 0) ? 1 : 0) +
                (((flags & MessageObject.SUGGESTION_FLAG_EDIT_MEDIA) != 0) ? 1 : 0) +
                (((flags & MessageObject.SUGGESTION_FLAG_EDIT_PRCIE) != 0) ? 1 : 0);
            int count = 0;

            if ((flags & MessageObject.SUGGESTION_FLAG_EDIT_PRCIE) != 0) {
                updateBuildTitleStep(ssb2, R.string.SuggestionOfferInfoTitleEditedPrice, flagsCount == ++count);
            }
            if ((flags & MessageObject.SUGGESTION_FLAG_EDIT_TIME) != 0) {
                updateBuildTitleStep(ssb2, R.string.SuggestionOfferInfoTitleEditedTime, flagsCount == ++count);
            }
            if ((flags & MessageObject.SUGGESTION_FLAG_EDIT_TEXT) != 0) {
                updateBuildTitleStep(ssb2, R.string.SuggestionOfferInfoTitleEditedText, flagsCount == ++count);
            }
            if ((flags & MessageObject.SUGGESTION_FLAG_EDIT_MEDIA) != 0) {
                updateBuildTitleStep(ssb2, R.string.SuggestionOfferInfoTitleEditedMedia, flagsCount == ++count);
            }

            if (messageObject.isOutOwner()) {
                ssb.append(LocaleController.formatString(R.string.SuggestionOfferInfoTitleEditedFromYou, ssb2));
            } else {
                ssb.append(LocaleController.formatString(R.string.SuggestionOfferInfoTitleEditedFromX, author, ssb2));
            }
        }

        title = new StaticLayout(
            AndroidUtilities.replaceTags(ssb),
            paint, titleMaxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        for (int a = 0; a < title.getLineCount(); a++) {
            titleRealWidth = (int) Math.max(titleRealWidth, title.getLineWidth(a));
        }

        height += title.getHeight();
        height += dp(5);

        width = Math.max(rowsRealWidth, titleRealWidth) + dp(PADDING_H) * 2;

        titleX = (width - titleMaxWidth) / 2;
        rowsTitleX = (width - rowsRealWidth) / 2;
        rowsInfoX = (int) (rowsTitleX + dp(11) + rowsTitleWidth);
    }

    private void updateBuildTitleStep(StringBuilder sb, @StringRes int str, boolean isLast) {
        if (sb.length() > 0) {
            if (isLast) {
                sb.append(' ');
                sb.append(getString(R.string.SuggestionOfferInfoTitleEditedAnd));
                sb.append(' ');
            } else {
                sb.append(", ");
            }
        }

        sb.append(getString(str));
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public void draw(Canvas canvas, int parentWidth, float sideMenuWidth, float top, float bgAlpha, float alpha, boolean withCenter) {

        final int x = (parentWidth - width) / 2;

        AndroidUtilities.rectTmp.set(x, 0, x + width, height);

        canvas.save();
        canvas.translate(sideMenuWidth / 2f, top);

        final Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        int oldAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (oldAlpha * alpha * bgAlpha));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), backgroundPaint);
        backgroundPaint.setAlpha(oldAlpha);
        if (resourcesProvider == null ? Theme.hasGradientService() : resourcesProvider.hasGradientService()) {
            final Paint darkenPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider);
            oldAlpha = darkenPaint.getAlpha();
            darkenPaint.setAlpha((int) (oldAlpha * alpha * bgAlpha));
            canvas.drawRect(AndroidUtilities.rectTmp, darkenPaint);
            darkenPaint.setAlpha(oldAlpha);
        }

        int y = dp(PADDING_V);
        if (title != null) {
            canvas.save();
            canvas.translate(x + titleX, y);
            title.draw(canvas);
            canvas.restore();
            y += title.getHeight() + dp(12);
        }
        for (Row row: rows) {
            row.title.draw(canvas, x + rowsTitleX, y + row.getHeight() / 2f, 0.85f);
            row.info.draw(canvas, x + rowsInfoX, y + row.getHeight() / 2f);
            y += row.getHeight() + dp(7);
        }

        canvas.restore();
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

}
