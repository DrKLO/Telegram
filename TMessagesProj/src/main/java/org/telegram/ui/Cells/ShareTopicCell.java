/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.Forum.ForumBubbleDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterDrawable;

public class ShareTopicCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;

    private long currentDialog;
    private long currentTopic;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    public ShareTopicCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(28));
        addView(imageView, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(2);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 66, 6, 0));

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(103), MeasureSpec.EXACTLY));
    }

    public void setTopic(TLRPC.Dialog dialog, TLRPC.TL_forumTopic topic, boolean checked, CharSequence name) {
        if (dialog == null) {
            return;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
        if (name != null) {
            nameTextView.setText(name);
        } else if (chat != null) {
            nameTextView.setText(topic.title);
        } else {
            nameTextView.setText("");
        }
        if (topic.icon_emoji_id != 0) {
            imageView.setImageDrawable(null);
            imageView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC, UserConfig.selectedAccount, topic.icon_emoji_id));
        } else {
            imageView.setAnimatedEmojiDrawable(null);
            ForumBubbleDrawable forumBubbleDrawable = new ForumBubbleDrawable(topic.icon_color);
            LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_TOPIC_DRAWABLE);
            String title = topic.title.trim().toUpperCase();
            letterDrawable.setTitle(title.length() >= 1 ? title.substring(0, 1) : "");
            letterDrawable.scale = 1.8f;
            CombinedDrawable combinedDrawable = new CombinedDrawable(forumBubbleDrawable, letterDrawable, 0, 0);
            combinedDrawable.setFullsize(true);
            imageView.setImageDrawable(combinedDrawable);
        }
        imageView.setRoundRadius(chat != null && chat.forum && !checked ? AndroidUtilities.dp(16) : AndroidUtilities.dp(28));

        currentDialog = dialog.id;
        currentTopic = topic.id;
    }

    public long getCurrentDialog() {
        return currentDialog;
    }

    public long getCurrentTopic() {
        return currentTopic;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
