package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.HintView2;

public class BotAdView extends FrameLayout {

    private final LinearLayout layout;
    private final Theme.ResourcesProvider resourcesProvider;
    public final BackupImageView imageView;
    public final ImageView closeView;
    public final TextView titleView;
    public final TextView channelTitleView;
    public final TextView removeView;
    public final LinkSpanDrawable.LinksTextView textView;

    public BotAdView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(16), dp(5), dp(8), dp(5));
        ScaleStateListAnimator.apply(layout, .025f, 1.4f);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .1f), 0, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.LEFT));

        LinearLayout titleLayout = new LinearLayout(context);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        textLayout.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL));
        NotificationCenter.listenEmojiLoading(titleView);

        removeView = new TextView(context);
        removeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        removeView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        ScaleStateListAnimator.apply(removeView, .1f, 1.5f);
        removeView.setPadding(dp(6.33f), 0, dp(6.33f), 0);
        removeView.setBackground(Theme.createRoundRectDrawable(dp(9), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f)));
        removeView.setText(LocaleController.getString(R.string.BotAdWhat));
        titleLayout.addView(removeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 5, 1, 0, 0));

        channelTitleView = new TextView(context);
        channelTitleView.setVisibility(View.GONE);
        channelTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        channelTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        channelTitleView.setTypeface(AndroidUtilities.bold());
        textLayout.addView(channelTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
        NotificationCenter.listenEmojiLoading(channelTitleView);

        textView = new LinkSpanDrawable.LinksTextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        NotificationCenter.listenEmojiLoading(textView);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(4));
        imageView.setVisibility(View.GONE);
        layout.addView(imageView, LayoutHelper.createLinear(48, 48, Gravity.RIGHT | Gravity.TOP, 10, 0, 2, 2));

        closeView = new ImageView(context);
        closeView.setBackground(Theme.createSelectorDrawable(Theme.RIPPLE_MASK_CIRCLE_AUTO, Theme.multAlpha(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), .20f)));
        ScaleStateListAnimator.apply(closeView);
        closeView.setImageResource(R.drawable.msg_close);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
        closeView.setOnClickListener(v -> {

        });
        closeView.setVisibility(View.GONE);
        layout.addView(closeView, LayoutHelper.createLinear(32, 32, Gravity.RIGHT | Gravity.TOP, 10, 3, 0, 2));
    }

    private boolean invalidatedMeasure = true;
    public void set(ChatActivity chatActivity, MessageObject messageObject, Runnable onRemoveListener, Runnable onCloseListener) {
        if (messageObject == null) return;

        invalidatedMeasure = true;

        CharSequence channel = messageObject.sponsoredTitle;
        channel = Emoji.replaceEmoji(channel, titleView.getPaint().getFontMetricsInt(), false);
        CharSequence text = messageObject.messageText;
        text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
        final String url = messageObject.sponsoredUrl;

        boolean hasMedia;
        if (messageObject.sponsoredMedia != null) {
            imageView.setVisibility(View.VISIBLE);
            closeView.setVisibility(View.GONE);
            if (messageObject.sponsoredMedia.document != null) {
                TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.sponsoredMedia.document.thumbs, 48);
                imageView.setImage(
                    ImageLocation.getForDocument(messageObject.sponsoredMedia.document), "48_48",
                    ImageLocation.getForDocument(thumbSize, messageObject.sponsoredMedia.document), "48_48",
                    null, 0, 0, null
                );
            } else if (messageObject.sponsoredMedia.photo != null) {
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.sponsoredMedia.photo.sizes, 48, true, null, true);
                TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.sponsoredMedia.photo.sizes, 48, true, photoSize, false);
                imageView.setImage(
                    ImageLocation.getForPhoto(photoSize, messageObject.sponsoredMedia.photo), "48_48",
                    ImageLocation.getForPhoto(thumbSize, messageObject.sponsoredMedia.photo), "48_48",
                    null, 0, 0, null
                );
            }
            hasMedia = true;
        } else if (messageObject.sponsoredPhoto != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.sponsoredPhoto.sizes, 48, true, null, true);
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.sponsoredPhoto.sizes, 48, true, photoSize, false);
            imageView.setImage(
                ImageLocation.getForPhoto(photoSize, messageObject.sponsoredPhoto), "48_48",
                ImageLocation.getForPhoto(thumbSize, messageObject.sponsoredPhoto), "48_48",
                null, 0, 0, null
            );
            imageView.setVisibility(View.VISIBLE);
            closeView.setVisibility(View.GONE);
            hasMedia = true;
        } else {
            imageView.setVisibility(View.GONE);
            closeView.setVisibility(View.VISIBLE);
            hasMedia = false;
        }

        SpannableStringBuilder title = new SpannableStringBuilder(LocaleController.getString(R.string.SponsoredMessageAd));
        title.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.append("  ");
        title.append(channel);
        if (titleView.getPaint().measureText(title.toString()) > AndroidUtilities.displaySize.x - dp(16 + 16 + 6.33f + 6.33f) - removeView.getPaint().measureText(removeView.getText().toString()) - dp(32) - dp(hasMedia ? 10 + 48 : 0)) {
            title = new SpannableStringBuilder(LocaleController.getString(R.string.SponsoredMessageAd));
            title.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            channelTitleView.setVisibility(View.VISIBLE);
            channelTitleView.setText(channel);
        } else {
            channelTitleView.setVisibility(View.GONE);
        }
        titleView.setText(title);
        textView.setText(text);

        setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        textView.setOnLinkPressListener(span -> {
            if (chatActivity != null) {
                chatActivity.logSponsoredClicked(messageObject, false, false);
            }
            if (span instanceof URLSpan) {
                String spanUrl = ((URLSpan) span).getURL();
                if (spanUrl != null) spanUrl = spanUrl.trim();
                if (chatActivity != null && spanUrl != null && (spanUrl.startsWith("$") || spanUrl.startsWith("#"))) {
                    chatActivity.openHashtagSearch(spanUrl, true);
                    return;
                }
            }
            span.onClick(textView);
        });
        removeView.setOnClickListener(v -> {
            if (onRemoveListener != null) {
                onRemoveListener.run();
            }
        });
        setOnClickListener(v -> {
            if (chatActivity != null) {
                chatActivity.logSponsoredClicked(messageObject, false, false);
            }
            Browser.openUrl(getContext(), Uri.parse(url), true, false, false, null, null, false, MessagesController.getInstance(UserConfig.selectedAccount).sponsoredLinksInappAllow, false);
        });

        closeView.setOnClickListener(v -> {
            if (onCloseListener != null) {
                onCloseListener.run();
            }
        });
    }

    public int height() {
        if (invalidatedMeasure || getMeasuredHeight() <= 0) {
            measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST));
        }
        return getMeasuredHeight();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        invalidatedMeasure = false;
    }

}
