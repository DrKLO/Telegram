package org.telegram.ui.Components.Forum;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import org.telegram.messenger.AndroidUtilities;
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


    public static void setTopicIcon(BackupImageView backupImageView, TLRPC.TL_forumTopic forumTopic) {
        setTopicIcon(backupImageView, forumTopic, false);
    }
    public static void setTopicIcon(BackupImageView backupImageView, TLRPC.TL_forumTopic forumTopic, boolean largeIcon) {
        if (forumTopic == null || backupImageView == null) {
            return;
        }
        if (forumTopic.icon_emoji_id != 0) {
            backupImageView.setImageDrawable(null);
            if (backupImageView.animatedEmojiDrawable == null || forumTopic.icon_emoji_id != backupImageView.animatedEmojiDrawable.getDocumentId()) {
                backupImageView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(largeIcon ? AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC_LARGE : AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, UserConfig.selectedAccount, forumTopic.icon_emoji_id));
            }
        } else {
            backupImageView.setAnimatedEmojiDrawable(null);
            backupImageView.setImageDrawable(createTopicDrawable(forumTopic));
        }
    }


    public static Drawable createTopicDrawable(TLRPC.TL_forumTopic topic) {
        return topic == null ? null : createTopicDrawable(topic.title, topic.icon_color);
    }

    public static Drawable createTopicDrawable(String text) {
        return createTopicDrawable(text, 0);
    }

    public static Drawable createTopicDrawable(String text, int color) {
        ForumBubbleDrawable forumBubbleDrawable = new ForumBubbleDrawable(color);
        LetterDrawable letterDrawable = new LetterDrawable(null, LetterDrawable.STYLE_TOPIC_DRAWABLE);
        String title = text.trim().toUpperCase();
        letterDrawable.setTitle(title.length() >= 1 ? title.substring(0, 1) : "");
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
        if (baseFragment == null || topic == null) {
            return;
        }
        TLRPC.Chat chatLocal = baseFragment.getMessagesController().getChat(chatId);
        Bundle args = new Bundle();
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
            return;
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(new MessageObject(baseFragment.getCurrentAccount(), message, false, false));
        chatActivity.setThreadMessages(messageObjects, chatLocal, topic.id, topic.read_inbox_max_id, topic.read_outbox_max_id, topic);
        if (fromMessageId != 0) {
            chatActivity.highlightMessageId = fromMessageId;
        }
        baseFragment.presentFragment(chatActivity);
    }

    public static CharSequence getTopicSpannedName(TLRPC.ForumTopic topic, Paint paint) {
        return getTopicSpannedName(topic, paint, null);
    }

    public static CharSequence getTopicSpannedName(TLRPC.ForumTopic topic, Paint paint, ForumBubbleDrawable[] drawableToSet) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (topic instanceof TLRPC.TL_forumTopic) {
            TLRPC.TL_forumTopic forumTopic = (TLRPC.TL_forumTopic) topic;
            if (forumTopic.icon_emoji_id != 0) {
                sb.append(" ");
                AnimatedEmojiSpan span;
                sb.setSpan(span = new AnimatedEmojiSpan(forumTopic.icon_emoji_id, .95f, paint == null ? null : paint.getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                span.top = true;
                span.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS;
            } else {
                sb.append(" ");
                Drawable drawable = ForumUtilities.createTopicDrawable(forumTopic);
                if (drawableToSet != null) {
                    drawableToSet[0] = (ForumBubbleDrawable) ((CombinedDrawable) drawable).getBackgroundDrawable();
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
            return AndroidUtilities.replaceCharSequence("%s", LocaleController.getString(R.string.TopicWasCreatedAction), ForumUtilities.getTopicSpannedName(topic, null));
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

            if ((topicEdit.flags & 4) != 0) {
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", topicEdit.closed ? LocaleController.getString(R.string.TopicWasClosedAction) :  LocaleController.getString(R.string.TopicWasReopenedAction), ForumUtilities.getTopicSpannedName(topic, null));
                return AndroidUtilities.replaceCharSequence("%1$s", charSequence, name);
            }
            if ((topicEdit.flags & 1) != 0 && (topicEdit.flags & 2) != 0) {
                TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
                forumTopic.icon_emoji_id = topicEdit.icon_emoji_id;
                forumTopic.title = topicEdit.title;
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", LocaleController.getString(R.string.TopicWasRenamedToAction2), ForumUtilities.getTopicSpannedName(forumTopic, null));
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
                CharSequence charSequence = AndroidUtilities.replaceCharSequence("%2$s", LocaleController.getString(R.string.TopicWasIconChangedToAction), ForumUtilities.getTopicSpannedName(forumTopic, null));
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
        TLRPC.TL_forumTopic topic = MessagesController.getInstance(messageObject.currentAccount).getTopicsController().findTopic(-messageObject.getDialogId(), MessageObject.getTopicId(messageObject.messageOwner));
        if (topic != null && messageObject.topicIconDrawable[0] != null) {
            messageObject.topicIconDrawable[0].setColor(topic.icon_color);
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
