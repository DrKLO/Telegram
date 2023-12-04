package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.Stories.StoriesUtilities;

import java.util.Date;

@SuppressLint("ViewConstructor")
public class StatisticPostInfoCell extends FrameLayout {

    private final BackupImageView imageView;
    private final SimpleTextView message;
    private final TextView views;
    private final TextView shares;
    private final TextView date;
    private final TextView likes;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final StoriesUtilities.AvatarStoryParams storyAvatarParams = new StoriesUtilities.AvatarStoryParams(false);
    private final Theme.ResourcesProvider resourcesProvider;
    private StatisticActivity.RecentPostInfo postInfo;
    private final TLRPC.ChatFull chat;
    private boolean needDivider;

    public StatisticPostInfoCell(Context context, TLRPC.ChatFull chat, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.chat = chat;
        this.resourcesProvider = resourcesProvider;
        imageView = new BackupImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (postInfo != null && postInfo.isStory()) {
                    int pad = AndroidUtilities.dp(1);
                    storyAvatarParams.originalAvatarRect.set(pad, pad, getMeasuredWidth() - pad, getMeasuredHeight() - pad);
                    storyAvatarParams.drawSegments = false;
                    storyAvatarParams.animate = false;
                    storyAvatarParams.drawInside = true;
                    storyAvatarParams.isArchive = false;
                    storyAvatarParams.forceState = StoriesUtilities.STATE_HAS_UNREAD;
                    storyAvatarParams.resourcesProvider = resourcesProvider;
                    StoriesUtilities.drawAvatarWithStory(0, canvas, imageReceiver, storyAvatarParams);
                } else {
                    super.onDraw(canvas);
                }
            }
        };
        setClipChildren(false);
        addView(imageView, LayoutHelper.createFrame(46, 46, (!LocaleController.isRTL ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL, !LocaleController.isRTL ? 12 : 16, 0, !LocaleController.isRTL ? 16 : 12, 0));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        message = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                value = Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), false);
                return super.setText(value);
            }
        };
        NotificationCenter.listenEmojiLoading(message);
        message.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        message.setTextSize(16);
        message.setMaxLines(1);
        message.setTextColor(Color.BLACK);
        message.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        views = new TextView(context);
        views.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        views.setTextColor(Color.BLACK);
        if (!LocaleController.isRTL) {
            linearLayout.addView(message, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 16, 0));
            linearLayout.addView(views, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        } else {
            linearLayout.addView(views, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            linearLayout.addView(message, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 16, 0, 0, 0));
        }

        contentLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 0, 7, 0, 0));

        date = new TextView(context);
        date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        date.setTextColor(Color.BLACK);
        date.setLines(1);
        date.setEllipsize(TextUtils.TruncateAt.END);

        shares = new TextView(context);
        shares.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        shares.setTextColor(Color.BLACK);
        shares.setGravity(Gravity.CENTER_VERTICAL);

        likes = new TextView(context);
        likes.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        likes.setTextColor(Color.BLACK);
        likes.setGravity(Gravity.CENTER_VERTICAL);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        if (!LocaleController.isRTL) {
            linearLayout.addView(date, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 0, 0, 8, 0));
            linearLayout.addView(likes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            linearLayout.addView(shares, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        } else {
            linearLayout.addView(shares, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
            linearLayout.addView(likes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            linearLayout.addView(date, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.NO_GRAVITY, 8, 0, 0, 0));
        }

        contentLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 0, 3, 0, 9));

        addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, !LocaleController.isRTL ? 72 : 18, 0, !LocaleController.isRTL ? 18 : 72, 0));

        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        views.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        date.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        shares.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        likes.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        Drawable likesDrawable = ContextCompat.getDrawable(context, R.drawable.mini_stats_likes).mutate();
        DrawableCompat.setTint(likesDrawable, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        Drawable sharesDrawable = ContextCompat.getDrawable(context, R.drawable.mini_stats_shares).mutate();
        DrawableCompat.setTint(sharesDrawable, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));

        CombinedDrawable likesCombinedDrawable = new CombinedDrawable(null, likesDrawable, 0, dp(1));
        likesCombinedDrawable.setCustomSize(sharesDrawable.getIntrinsicWidth(), sharesDrawable.getIntrinsicHeight());
        likes.setCompoundDrawablesWithIntrinsicBounds(likesCombinedDrawable, null, null, null);
        likes.setCompoundDrawablePadding(dp(2));

        CombinedDrawable sharesCombinedDrawable = new CombinedDrawable(null, sharesDrawable, 0, dp(1));
        sharesCombinedDrawable.setCustomSize(sharesDrawable.getIntrinsicWidth(), sharesDrawable.getIntrinsicHeight());
        shares.setCompoundDrawablesWithIntrinsicBounds(sharesCombinedDrawable, null, null, null);
        shares.setCompoundDrawablePadding(dp(2));
        setWillNotDraw(false);
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    public StoriesUtilities.AvatarStoryParams getStoryAvatarParams() {
        return storyAvatarParams;
    }

    public StatisticActivity.RecentPostInfo getPostInfo() {
        return postInfo;
    }

    public void setImageViewAction(View.OnClickListener action){
        imageView.setOnClickListener(action);
    }

    public void setData(StatisticActivity.RecentPostInfo postInfo, boolean isLast) {
        this.postInfo = postInfo;
        this.needDivider = !isLast;
        MessageObject messageObject = postInfo.message;
        if (messageObject.photoThumbs != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
            imageView.setImage(
                    ImageLocation.getForObject(size, messageObject.photoThumbsObject), "50_50",
                    ImageLocation.getForObject(thumbSize, messageObject.photoThumbsObject), "b1", 0, messageObject);
            imageView.setRoundRadius(AndroidUtilities.dp(9));
            imageView.setScaleX(0.96f);
            imageView.setScaleY(0.96f);
        } else if (chat.chat_photo.sizes.size() > 0) {
            imageView.setImage(ImageLocation.getForPhoto(chat.chat_photo.sizes.get(0), chat.chat_photo), "50_50", null, null, chat);
            imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
            imageView.setScaleX(0.96f);
            imageView.setScaleY(0.96f);
        } else {
            TLRPC.Chat currentChat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(chat.id);
            avatarDrawable.setInfo(currentChat);
            imageView.setForUserOrChat(currentChat, avatarDrawable);
            imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
        }
        if (messageObject.isStory()) {
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
        }
        CharSequence text;
        if (messageObject.isMusic()) {
            text = String.format("%s, %s", messageObject.getMusicTitle().trim(), messageObject.getMusicAuthor().trim());
        } else if (messageObject.isStory()) {
            text = LocaleController.getString("Story", R.string.Story);
        } else {
            text = messageObject.caption != null ? messageObject.caption : messageObject.messageText;
        }
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text == null ? "" : text);
        URLSpan[] urlSpans = stringBuilder.getSpans(0, stringBuilder.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            stringBuilder.removeSpan(urlSpan);
        }
        message.setText(AndroidUtilities.trim(AndroidUtilities.replaceNewLines(stringBuilder), null));
        views.setText(String.format(LocaleController.getPluralString("Views", postInfo.getViews()), AndroidUtilities.formatWholeNumber(postInfo.getViews(), 0)));

        Date time = new Date(postInfo.getDate() * 1000L);
        String monthTxt = LocaleController.getInstance().formatterYear.format(time);
        String timeTxt = LocaleController.getInstance().formatterDay.format(time);
        date.setText(LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, monthTxt, timeTxt));

        shares.setText(AndroidUtilities.formatWholeNumber(postInfo.getForwards(), 0));
        likes.setText(AndroidUtilities.formatWholeNumber(postInfo.getReactions(), 0));
        shares.setVisibility(postInfo.getForwards() != 0 ? VISIBLE : GONE);
        likes.setVisibility(postInfo.getReactions() != 0 ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (needDivider) {
            dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            int paddingDp = 72;
            if (LocaleController.isRTL) {
                canvas.drawRect(0, getHeight() - 1, getWidth() - dp(paddingDp), getHeight(), dividerPaint);
            } else {
                canvas.drawRect(dp(paddingDp), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }
    }

    public void setData(StatisticActivity.MemberData memberData) {
        avatarDrawable.setInfo(memberData.user);
        imageView.setForUserOrChat(memberData.user, avatarDrawable);
        imageView.setRoundRadius(AndroidUtilities.dp(46) >> 1);
        message.setText(memberData.user.first_name);
        date.setText(memberData.description);

        views.setVisibility(View.GONE);
        shares.setVisibility(View.GONE);
        likes.setVisibility(View.GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        storyAvatarParams.onDetachFromWindow();
    }
}
