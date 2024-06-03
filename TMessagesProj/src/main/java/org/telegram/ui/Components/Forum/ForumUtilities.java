package org.telegram.ui.Components.Forum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;

public class ForumUtilities {

    static Drawable dialogGeneralIcon;
    static SparseArray<Drawable> dialogForumDrawables = new SparseArray();

    public static void setTopicIcon(BackupImageView backupImageView, TLRPC.TL_forumTopic forumTopic) {
        setTopicIcon(backupImageView, forumTopic, false, false, null);
    }
    public static void setTopicIcon(BackupImageView backupImageView, TLRPC.TL_forumTopic forumTopic, boolean actionBar, boolean largeIcon, Theme.ResourcesProvider resourcesProvider) {
        if (forumTopic == null || backupImageView == null) {
            return;
        }
        if (forumTopic.id == 1) {
            backupImageView.setAnimatedEmojiDrawable(null);
            backupImageView.setImageDrawable(createGeneralTopicDrawable(backupImageView.getContext(), 0.75f, Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false, largeIcon));
        } else if (forumTopic.icon_emoji_id != 0) {
            backupImageView.setImageDrawable(null);
            if (backupImageView.animatedEmojiDrawable == null || forumTopic.icon_emoji_id != backupImageView.animatedEmojiDrawable.getDocumentId()) {
                AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(largeIcon ? AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC_LARGE : AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, UserConfig.selectedAccount, forumTopic.icon_emoji_id);
                drawable.setColorFilter(actionBar ? new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultTitle), PorterDuff.Mode.SRC_IN) : Theme.getAnimatedEmojiColorFilter(resourcesProvider));
                backupImageView.setAnimatedEmojiDrawable(drawable);
            }
        } else {
            backupImageView.setAnimatedEmojiDrawable(null);
            backupImageView.setImageDrawable(createTopicDrawable(forumTopic, false));
        }
    }

    public static GeneralTopicDrawable createGeneralTopicDrawable(Context context, float scale, int color, boolean isDialog) {
        return createGeneralTopicDrawable(context, scale, color, isDialog, false);
    }

    public static GeneralTopicDrawable createGeneralTopicDrawable(Context context, float scale, int color, boolean isDialog, boolean large) {
        if (context == null) {
            return null;
        }
        return new GeneralTopicDrawable(context, scale, color, isDialog, large);
    }

    public static void filterMessagesByTopic(long threadMessageId, ArrayList<MessageObject> messageObjects) {
        if (messageObjects == null) {
            return;
        }
        for (int i = 0; i < messageObjects.size(); i++) {
            if (threadMessageId != MessageObject.getTopicId(messageObjects.get(i).currentAccount, messageObjects.get(i).messageOwner, true)) {
                messageObjects.remove(i);
                i--;
            }
        }
    }

    public static class GeneralTopicDrawable extends Drawable {

        Drawable icon;
        float scale;
        int color;

        public GeneralTopicDrawable(Context context) {
            this(context, 1f);
        }

        public GeneralTopicDrawable(Context context, float scale) {
            this.icon = context.getResources().getDrawable(R.drawable.msg_filled_general).mutate();
            this.scale = scale;
        }

        public GeneralTopicDrawable(Context context, float scale, int color, boolean isDialog, boolean large) {
            if (isDialog) {
                if (dialogGeneralIcon == null) {
                    dialogGeneralIcon = context.getResources().getDrawable(large ? R.drawable.msg_filled_general_large : R.drawable.msg_filled_general).mutate();
                }
                this.icon = dialogGeneralIcon;
            } else {
                this.icon = context.getResources().getDrawable(large ? R.drawable.msg_filled_general_large : R.drawable.msg_filled_general).mutate();
            }
            this.scale = scale;
            setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (scale == 1) {
                icon.setBounds(bounds);
            } else {
                icon.setBounds(
                        (int) (bounds.centerX() - bounds.width() / 2f * scale),
                        (int) (bounds.centerY() - bounds.height() / 2f * scale),
                        (int) (bounds.centerX() + bounds.width() / 2f * scale),
                        (int) (bounds.centerY() + bounds.height() / 2f * scale)
                );
            }
            icon.draw(canvas);
        }

        public void setColor(int color) {
            if (this.color != color) {
                setColorFilter(new PorterDuffColorFilter(this.color = color, PorterDuff.Mode.MULTIPLY));
            }
        }

        @Override
        public void setAlpha(int i) {
            icon.setAlpha(i);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            icon.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static Drawable createTopicDrawable(TLRPC.TL_forumTopic topic, boolean isDialog) {
        return topic == null ? null : createTopicDrawable(topic.title, topic.icon_color, isDialog);
    }

    public static Drawable createTopicDrawable(String text) {
        return createTopicDrawable(text, 0, false);
    }

    public static Drawable createTopicDrawable(String text, int color, boolean isDialog) {
        Drawable forumBubbleDrawable;
        if (isDialog) {
            forumBubbleDrawable = dialogForumDrawables.get(color);
            if (forumBubbleDrawable == null) {
                forumBubbleDrawable = new ForumBubbleDrawable(color);
                dialogForumDrawables.put(color, forumBubbleDrawable);
            }
        } else {
            forumBubbleDrawable = new ForumBubbleDrawable(color);
        }

        LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_TOPIC_DRAWABLE);
        String title = text.trim();
        letterDrawable.setTitle(title.length() >= 1 ? title.substring(0, 1).toUpperCase() : "");
        CombinedDrawable combinedDrawable = new CombinedDrawable(forumBubbleDrawable, letterDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        return combinedDrawable;
    }

    public static Drawable createSmallTopicDrawable(String text, int color) {
        ForumBubbleDrawable forumBubbleDrawable = new ForumBubbleDrawable(color);
        LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_SMALL_TOPIC_DRAWABLE);
        String title = text.trim().toUpperCase();
        letterDrawable.setTitle(title.length() >= 1 ? title.substring(0, 1) : "");
        CombinedDrawable combinedDrawable = new CombinedDrawable(forumBubbleDrawable, letterDrawable, 0, 0);
        combinedDrawable.setFullsize(true);
        return combinedDrawable;
    }

    public static void openTopic(BaseFragment baseFragment, long chatId, TLRPC.TL_forumTopic topic, int fromMessageId) {
        ChatActivity chatActivity = getChatActivityForTopic(baseFragment, chatId, topic, fromMessageId, new Bundle());
        if (chatActivity != null) {
            baseFragment.presentFragment(chatActivity);
        }
    }

    public static ChatActivity getChatActivityForTopic(BaseFragment baseFragment, long chatId, TLRPC.TL_forumTopic topic, int fromMessageId, Bundle args) {
        if (baseFragment == null || topic == null) {
            return null;
        }
        TLRPC.Chat chatLocal = baseFragment.getMessagesController().getChat(chatId);
        if (args == null) {
            args = new Bundle();
        }
        args.putLong("chat_id", chatId);

        if (fromMessageId != 0) {
            args.putInt("message_id", fromMessageId);
        } else if (topic.read_inbox_max_id == 0) {
            //scroll to first message in topic
            args.putInt("message_id", topic.id);
        }
        args.putInt("unread_count", topic.unread_count);
        args.putBoolean("historyPreloaded", false);
        ChatActivity chatActivity = new ChatActivity(args);
        TLRPC.Message message = topic.topicStartMessage;
        if (message == null) {
            TLRPC.TL_forumTopic topicLocal = baseFragment.getMessagesController().getTopicsController().findTopic(chatId, topic.id);
            if (topicLocal != null) {
                topic = topicLocal;
                message = topic.topicStartMessage;
            }
        }
        if (message == null) {
            return null;
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(new MessageObject(baseFragment.getCurrentAccount(), message, false, false));
        chatActivity.setThreadMessages(messageObjects, chatLocal, topic.id, topic.read_inbox_max_id, topic.read_outbox_max_id, topic);
        if (fromMessageId != 0) {
            chatActivity.highlightMessageId = fromMessageId;
        }
        return chatActivity;
    }

    public static CharSequence getTopicSpannedName(TLRPC.ForumTopic topic, Paint paint, boolean isDialog) {
        return getTopicSpannedName(topic, paint, null, isDialog);
    }

    public static CharSequence getTopicSpannedName(TLRPC.ForumTopic topic, Paint paint, Drawable[] drawableToSet, boolean isDialog) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (topic instanceof TLRPC.TL_forumTopic) {
            TLRPC.TL_forumTopic forumTopic = (TLRPC.TL_forumTopic) topic;
            if (forumTopic.id == 1) {
                try {
                    Drawable drawable = createGeneralTopicDrawable(ApplicationLoader.applicationContext, 1f, paint == null ? Theme.getColor(Theme.key_chat_inMenu) : paint.getColor(), isDialog);
                    drawable.setBounds(0, 0, paint == null ? AndroidUtilities.dp(14) : (int) (paint.getTextSize()), paint == null ? AndroidUtilities.dp(14) : (int) (paint.getTextSize()));
                    sb.append(" ");
                    if (drawableToSet != null) {
                        drawableToSet[0] = drawable;
                    }
                    sb.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception e) {}
            } else if (forumTopic.icon_emoji_id != 0) {
                sb.append(" ");
                AnimatedEmojiSpan span;
                sb.setSpan(span = new AnimatedEmojiSpan(forumTopic.icon_emoji_id, .95f, paint == null ? null : paint.getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                span.top = true;
                span.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC;
            } else {
                sb.append(" ");
                Drawable drawable = ForumUtilities.createTopicDrawable(forumTopic, isDialog);
                if (drawableToSet != null) {
                    drawableToSet[0] = ((CombinedDrawable) drawable).getBackgroundDrawable();
                }
                drawable.setBounds(0, 0, (int) (drawable.getIntrinsicWidth() * 0.65f), (int) (drawable.getIntrinsicHeight() * 0.65f));
                if (drawable instanceof CombinedDrawable && ((CombinedDrawable) drawable).getIcon() instanceof LetterDrawable) {
                    ((LetterDrawable) ((CombinedDrawable) drawable).getIcon()).scale = .7f;
                }
                if (paint != null) {
                    ColoredImageSpan imageSpan = new ColoredImageSpan(drawable);
                    imageSpan.setSize((int) (Math.abs(paint.getFontMetrics().descent) + Math.abs(paint.getFontMetrics().ascent)));
                    sb.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    sb.setSpan(new ImageSpan(drawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (!TextUtils.isEmpty(forumTopic.title)) {
                sb.append(" ");
                sb.append(forumTopic.title);
            }
        } else {
            return "DELETED";
        }
        return sb;
    }

    public static void applyTopic(ChatActivity chatActivity, MessagesStorage.TopicKey topicKey) {
        if (topicKey.topicId == 0) {
            return;
        }
        TLRPC.TL_forumTopic topic = chatActivity.getMessagesController().getTopicsController().findTopic(-topicKey.dialogId, topicKey.topicId);
        if (topic == null) {
            return;
        }
        TLRPC.Chat chatLocal = chatActivity.getMessagesController().getChat(-topicKey.dialogId);
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(new MessageObject(chatActivity.getCurrentAccount(), topic.topicStartMessage, false, false));
        chatActivity.setThreadMessages(messageObjects, chatLocal, topic.id, topic.read_inbox_max_id, topic.read_outbox_max_id, topic);
    }

    public static CharSequence createActionTextWithTopic(TLRPC.TL_forumTopic topic, MessageObject messageObject) {
        if (topic == null) {
            return null;
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionTopicCreate) {
            return AndroidUtilities.replaceCharSequence("%s", LocaleController.getString(R.string.TopicWasCreatedAction), ForumUtilities.getTopicSpannedName(topic, null, false));
        }
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionTopicEdit) {
            TLRPC.TL_messageActionTopicEdit topicEdit = (TLRPC.TL_messageActionTopicEdit) messageObject.messageOwner.action;
            long fromId = messageObject.getFromChatId();
            TLRPC.User fromUser = null;
            TLRPC.Chat fromChat = null;
            if (DialogObject.isUserDialog(fromId)) {
                fromUser = MessagesController.getInstance(messageObject.currentAccount).getUser(fromId);
            } else {
                fromChat = MessagesController.getInstance(messageObject.currentAccount).getChat(-fromId);
            }
            String name = null;
            if (fromUser != null) {
                name = ContactsController.formatName(fromUser.first_name, fromUser.last_name);
            } else if (fromChat != null) {
                name = fromChat.title;
            }

            if ((topicEdit.flags & 8) != 0) {
                return AndroidUtilities.replaceCharSequence("%s", topicEdit.hidden ? LocaleController.getString(R.string.TopicHidden2) :  LocaleController.getString(R.string.TopicShown2), name);
            }
            if ((topicEdit.flags & 4) != 0) {
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", topicEdit.closed ? LocaleController.getString(R.string.TopicWasClosedAction) :  LocaleController.getString(R.string.TopicWasReopenedAction), ForumUtilities.getTopicSpannedName(topic, null, false));
                return AndroidUtilities.replaceCharSequence("%1$s", charSequence, name);
            }
            if ((topicEdit.flags & 1) != 0 && (topicEdit.flags & 2) != 0) {
                TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
                forumTopic.icon_emoji_id = topicEdit.icon_emoji_id;
                forumTopic.title = topicEdit.title;
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", LocaleController.getString(R.string.TopicWasRenamedToAction2), ForumUtilities.getTopicSpannedName(forumTopic, null, false));
                return AndroidUtilities.replaceCharSequence("%1$s", charSequence, name);
            }
            if ((topicEdit.flags & 1) != 0) {
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", LocaleController.getString(R.string.TopicWasRenamedToAction), topicEdit.title);
                return AndroidUtilities.replaceCharSequence("%1$s", charSequence, name);
            }
            if ((topicEdit.flags & 2) != 0) {
                TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
                forumTopic.icon_emoji_id = topicEdit.icon_emoji_id;
                forumTopic.title = "";
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", LocaleController.getString(R.string.TopicWasIconChangedToAction), ForumUtilities.getTopicSpannedName(forumTopic, null, false));
                return AndroidUtilities.replaceCharSequence("%1$s", charSequence, name);
            }

        }
        return null;
    }

    public static boolean isTopicCreateMessage(MessageObject message) {
        return message != null && message.messageOwner.action instanceof TLRPC.TL_messageActionTopicCreate;
    }

    public static void applyTopicToMessage(MessageObject messageObject) {
        if (messageObject.getDialogId() > 0) {
            return;
        }
        TLRPC.TL_forumTopic topic = MessagesController.getInstance(messageObject.currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), MessageObject.getTopicId(messageObject.currentAccount, messageObject.messageOwner, true));
        if (topic != null && messageObject.topicIconDrawable[0] instanceof ForumBubbleDrawable) {
            ((ForumBubbleDrawable) messageObject.topicIconDrawable[0]).setColor(topic.icon_color);
        }
    }

    public static void switchAllFragmentsInStackToForum(long chatId, INavigationLayout actionBarLayout) {

//        List<BaseFragment> fragmentStack = actionBarLayout.getFragmentStack();
//        for (int i = 0; i < fragmentStack.size() - 1; i++) {
//            if (fragmentStack.get(i) instanceof ChatActivity) {
//                ChatActivity chatActivity = (ChatActivity) fragmentStack.get(i);
//                if (-chatActivity.getDialogId() == chatId) {
//                    Bundle bundle = new Bundle();
//                    bundle.putLong("chat_id", chatId);
//                    actionBarLayout.removeFragmentFromStack(i);
//                    actionBarLayout.addFragmentToStack(new TopicsFragment(bundle), i);
//                }
//            } else if (fragmentStack.get(i) instanceof TopicsFragment) {
//                TopicsFragment chatActivity = (TopicsFragment) fragmentStack.get(i);
//                if (-chatActivity.getDialogId() == chatId) {
//                    Bundle bundle = new Bundle();
//                    bundle.putLong("dialog_id", -chatId);
//                    actionBarLayout.removeFragmentFromStack(i);
//                    actionBarLayout.addFragmentToStack(new ChatActivity(bundle), i);
//                }
//            }
//        }

        BaseFragment lastFragment = actionBarLayout.getLastFragment();
        if (lastFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) lastFragment;
            if (-chatActivity.getDialogId() == chatId) {
                if (chatActivity.getMessagesController().getChat(chatId).forum) {
                    if (chatActivity.getParentLayout() != null) {
                        if (chatActivity.getParentLayout().checkTransitionAnimation()) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (chatActivity.getParentLayout() != null) {
                                    TopicsFragment.prepareToSwitchAnimation(chatActivity);
                                }
                            }, 500);
                        } else {
                            TopicsFragment.prepareToSwitchAnimation(chatActivity);
                        }
                    }
                }
            }
        }
        if (lastFragment instanceof TopicsFragment) {
            TopicsFragment topicsFragment = (TopicsFragment) lastFragment;
            if (-topicsFragment.getDialogId() == chatId && !topicsFragment.getMessagesController().getChat(chatId).forum) {
                if (topicsFragment.getParentLayout() != null && topicsFragment.getParentLayout().checkTransitionAnimation()) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (topicsFragment.getParentLayout() != null) {
                            topicsFragment.switchToChat(true);
                        }
                    }, 500);
                } else {
                    topicsFragment.switchToChat(true);
                }
            }
        }
    }
}
