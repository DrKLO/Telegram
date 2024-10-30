package org.telegram.ui.Cells;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Stories.StoriesUtilities;

public class ExpiredStoryView {
    public boolean visible;

    StaticLayout titleLayout;
    StaticLayout subtitleLayout;

    int width;
    int height;
    float textX;
    float textY;
    float verticalPadding;
    float horizontalPadding;

    public void measure(ChatMessageCell parent) {
        CharSequence title = StoriesUtilities.createExpiredStoryString();
        MessageObject messageObject = parent.getMessageObject();
        TLRPC.TL_messageMediaStory mediaStory;
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaStory) {
            mediaStory = (TLRPC.TL_messageMediaStory) messageObject.messageOwner.media;
        } else {
            verticalPadding = AndroidUtilities.dp(4);
            horizontalPadding = AndroidUtilities.dp(12);
            height = 0;
            width = 0;
            return;
        }
        TLRPC.User user = MessagesController.getInstance(parent.currentAccount).getUser(mediaStory.user_id);
        String fromName = user == null ? "DELETED" : user.first_name;
        int forwardedNameWidth;
        if (AndroidUtilities.isTablet()) {
            forwardedNameWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.4f);
        } else {
            forwardedNameWidth = (int) (parent.getParentWidth()  * 0.4f);
        }
        String from = LocaleController.getString(R.string.From);
        int fromWidth = (int) Math.ceil(Theme.chat_forwardNamePaint.measureText(from + " "));

        if (fromName == null) {
            fromName = "";
        }
        fromName = (String) TextUtils.ellipsize(fromName.replace('\n', ' '), Theme.chat_replyNamePaint, forwardedNameWidth - fromWidth, TextUtils.TruncateAt.END);
        String fromString = LocaleController.getString(R.string.FromFormatted);
        int idx = fromString.indexOf("%1$s");
        CharSequence subtitle = String.format(fromString, fromName);
        if (idx >= 0) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(subtitle);
            spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), idx, idx + fromName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            subtitle = spannableStringBuilder;
        }
        TextPaint titlePaint = Theme.chat_replyTextPaint;
        TextPaint subtitlePaint = Theme.chat_replyTextPaint;
        int w = (int) (titlePaint.measureText(title, 0, title.length()) + 1);
        titleLayout = new StaticLayout(title, titlePaint, w + AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        w = (int) (subtitlePaint.measureText( subtitle, 0, subtitle.length()) + 1);
        subtitleLayout =  new StaticLayout(subtitle, subtitlePaint, w + AndroidUtilities.dp(10), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        height = 0;
        verticalPadding = AndroidUtilities.dp(4);
        horizontalPadding = AndroidUtilities.dp(12);
        height += AndroidUtilities.dp(4) + titleLayout.getHeight() + AndroidUtilities.dp(2) + subtitleLayout.getHeight() + AndroidUtilities.dp(4) + verticalPadding * 2;
        width = Math.max(titleLayout.getWidth(), subtitleLayout.getWidth()) + AndroidUtilities.dp(12) + AndroidUtilities.dp(20) + parent.getExtraTextX();

    }

    public void draw(Canvas canvas, ChatMessageCell parent) {
        float alpha = 1f;
        textY = AndroidUtilities.dp(8) + verticalPadding;
        if (parent.pinnedTop) {
            textY -= AndroidUtilities.dp(2);
        }
        RectF rect = AndroidUtilities.rectTmp;
        if (parent.getMessageObject().isOutOwner()) {
            textX = -(parent.timeWidth + AndroidUtilities.dp(12)) + parent.getExtraTextX() + parent.getMeasuredWidth() - width + AndroidUtilities.dp(24) - horizontalPadding;
            rect.set(parent.getMeasuredWidth() - width - horizontalPadding, verticalPadding, parent.getMeasuredWidth() - horizontalPadding, parent.getMeasuredHeight() - verticalPadding);
        } else {
            float offset = parent.isAvatarVisible ? AndroidUtilities.dp(48) : 0;
            textX = offset + horizontalPadding + AndroidUtilities.dp(12);
            rect.set(offset + horizontalPadding, verticalPadding, offset + horizontalPadding + width, parent.getMeasuredHeight() - verticalPadding);
        }
     //   Theme.chat_replyNamePaint.setColor(getThemedColor(Theme.key_chat_inReplyNameText));

        if (parent.getMessageObject().isOutOwner()) {
            Theme.chat_replyTextPaint.setColor(parent.getThemedColor(Theme.key_chat_outReplyNameText));
        } else {
            Theme.chat_replyTextPaint.setColor(parent.getThemedColor(Theme.key_chat_inReplyNameText));
        }
       // float roundRadius = AndroidUtilities.dp(8);
//        if (alpha != 1f) {
//            int oldAlpha = parent.getThemedPaint(Theme.key_paint_chatActionBackground).getAlpha();
//            parent.getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha((int) (alpha * oldAlpha));
//            canvas.drawRoundRect(rect, roundRadius, roundRadius, parent.getThemedPaint(Theme.key_paint_chatActionBackground));
//            parent.getThemedPaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha);
//        } else {
//            canvas.drawRoundRect(rect, roundRadius, roundRadius, parent.getThemedPaint(Theme.key_paint_chatActionBackground));
//        }
//        if (parent.hasGradientService()) {
//            if (alpha != 1f) {
//                int oldAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
//                Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (alpha * oldAlpha));
//                canvas.drawRoundRect(rect, roundRadius, roundRadius, Theme.chat_actionBackgroundGradientDarkenPaint);
//                Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha);
//            } else {
//                canvas.drawRoundRect(rect, roundRadius, roundRadius, Theme.chat_actionBackgroundGradientDarkenPaint);
//            }
//        }

        canvas.save();
        canvas.translate(textX, textY);
        if (titleLayout != null) {
            titleLayout.draw(canvas);
            canvas.translate(0, titleLayout.getHeight() + AndroidUtilities.dp(2));
        }
        if (subtitleLayout != null) {
            subtitleLayout.draw(canvas);
        }
        canvas.restore();
    }
}
