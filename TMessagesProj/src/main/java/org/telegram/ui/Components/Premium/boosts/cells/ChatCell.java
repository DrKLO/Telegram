package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class ChatCell extends BaseCell {

    public interface ChatDeleteListener {
        void onChatDeleted(TLRPC.Chat chat);
    }

    private final ImageView deleteImageView;
    private ChatDeleteListener chatDeleteListener;
    private TLRPC.Chat chat;
    private boolean removable;

    public ChatCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        deleteImageView = new ImageView(context);
        deleteImageView.setFocusable(false);
        deleteImageView.setScaleType(ImageView.ScaleType.CENTER);
        deleteImageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
        deleteImageView.setImageResource(R.drawable.poll_remove);
        deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        deleteImageView.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
        addView(deleteImageView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER, LocaleController.isRTL ? 3 : 0, 0, LocaleController.isRTL ? 0 : 3, 0));
        titleTextView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 24 : 0), 0, AndroidUtilities.dp(LocaleController.isRTL ? 0 : 24), 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        deleteImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected boolean needCheck() {
        return false;
    }

    public void setChat(TLRPC.Chat chat, int boosts, boolean removable) {
        this.removable = removable;
        this.chat = chat;
        avatarDrawable.setInfo(chat);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        imageView.setForUserOrChat(chat, avatarDrawable);

        CharSequence text = chat.title;
        text = Emoji.replaceEmoji(text, titleTextView.getPaint().getFontMetricsInt(), false);
        titleTextView.setText(text);

        if (removable) {
            setSubtitle(null);
        } else {
            setSubtitle(LocaleController.formatPluralString("BoostingChannelWillReceiveBoost", boosts));
        }

        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        setDivider(true);
        if (removable) {
            deleteImageView.setVisibility(VISIBLE);
        } else {
            deleteImageView.setVisibility(INVISIBLE);
        }
        deleteImageView.setOnClickListener(v -> {
            if (chatDeleteListener != null) {
                chatDeleteListener.onChatDeleted(chat);
            }
        });
    }

    public void setChatDeleteListener(ChatDeleteListener chatDeleteListener) {
        this.chatDeleteListener = chatDeleteListener;
    }

    public void setCounter(int count) {
        if (removable) {
            setSubtitle(null);
        } else {
            setSubtitle(LocaleController.formatPluralString("BoostingChannelWillReceiveBoost", count));
        }
    }
}
