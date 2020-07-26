package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.StatisticActivity;

public class StatisticPostInfoCell extends FrameLayout {

    private TextView message;
    private TextView views;
    private TextView shares;
    private TextView date;
    private BackupImageView imageView;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();

    private final TLRPC.ChatFull chat;

    public StatisticPostInfoCell(Context context, TLRPC.ChatFull chat) {
        super(context);
        this.chat = chat;
        imageView = new BackupImageView(context);
        addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.START | Gravity.CENTER_VERTICAL, 12, 0, 16, 0));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        message = new TextView(context);
        message.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        message.setTextSize(15);
        message.setTextColor(Color.BLACK);
        message.setLines(1);
        message.setEllipsize(TextUtils.TruncateAt.END);

        views = new TextView(context);
        views.setTextSize(15);
        views.setTextColor(Color.BLACK);

        linearLayout.addView(message, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 16, 0));
        linearLayout.addView(views, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 0, 8, 0, 0));

        date = new TextView(context);
        date.setTextSize(13);
        date.setTextColor(Color.BLACK);
        date.setLines(1);
        date.setEllipsize(TextUtils.TruncateAt.END);

        shares = new TextView(context);
        shares.setTextSize(13);
        shares.setTextColor(Color.BLACK);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        linearLayout.addView(date, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 8, 0));
        linearLayout.addView(shares, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 0, 2, 0, 8));

        addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 72, 0, 12, 0));

        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        views.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        date.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        shares.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
    }

    public void setData(StatisticActivity.RecentPostInfo postInfo) {
        MessageObject messageObject = postInfo.message;
        if (messageObject.photoThumbs != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs,50);
            imageView.setImage(
                    ImageLocation.getForObject(size, messageObject.photoThumbsObject), "50_50",
                    ImageLocation.getForObject(thumbSize, messageObject.photoThumbsObject), "b1", 0, messageObject);
            imageView.setRoundRadius(AndroidUtilities.dp(4));
        } else if (chat.chat_photo.sizes.size() > 0) {
            imageView.setImage(ImageLocation.getForPhoto(chat.chat_photo.sizes.get(0), chat.chat_photo), "50_50", null, null, chat);
            imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
        }

        String text;
        if (messageObject.isMusic()) {
            text = String.format("%s, %s", messageObject.getMusicTitle().trim(), messageObject.getMusicAuthor().trim());
        } else {
            text = messageObject.caption != null ? messageObject.caption.toString() : messageObject.messageText.toString();
        }

        message.setText(text.replace("\n", " ").trim());
        views.setText(String.format(LocaleController.getPluralString("Views", postInfo.counters.views), AndroidUtilities.formatCount(postInfo.counters.views)));
        date.setText(LocaleController.formatDateAudio(postInfo.message.messageOwner.date, false));
        shares.setText(String.format(LocaleController.getPluralString("Shares", postInfo.counters.forwards), AndroidUtilities.formatCount(postInfo.counters.forwards)));
    }

    public void setData(StatisticActivity.MemberData memberData) {
        avatarDrawable.setInfo(memberData.user);
        imageView.setImage(ImageLocation.getForUser(memberData.user, false), "50_50", avatarDrawable, memberData.user);
        imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
        message.setText(memberData.user.first_name);
        date.setText(memberData.description);

        views.setVisibility(View.GONE);
        shares.setVisibility(View.GONE);
    }
}
