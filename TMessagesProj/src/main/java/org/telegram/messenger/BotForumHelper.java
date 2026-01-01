package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_forum;
import org.telegram.ui.MultiLayoutTypingAnimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BotForumHelper extends BaseController {

    public boolean isThinking(long userId, int topicId) {
        LongSparseArray<BotDraftMessage> messages = botTextDraftsByRandomIds.get(userId, topicId);
        return messages != null && messages.size() > 0;
    }

    private MessageObject createDraftMessage(long userId, int topicId, long randomId, int messageId, TLRPC.TL_textWithEntities text) {
        TLRPC.Message message = new TLRPC.TL_message();
        message.dialog_id = userId;
        message.peer_id = getMessagesController().getPeer(userId);
        message.from_id = getMessagesController().getPeer(userId);
        message.id = message.local_id = messageId;
        message.random_id = randomId;

        message.message = text.text;

        message.entities = text.entities;
        message.flags |= 128;

        message.date = getConnectionsManager().getCurrentTime();

        message.reply_to = new TLRPC.TL_messageReplyHeader();
        message.flags |= 16;

        message.reply_to.forum_topic = true;
        message.reply_to.reply_to_top_id = topicId;
        message.reply_to.flags |= 2;

        message.media = new TLRPC.TL_messageMediaEmpty();
        message.flags |= 512;

        MessageObject messageObject = new MessageObject(currentAccount, message, true, true);
        // messageObject.wasJustSent = true;
        messageObject.isBotPendingDraft = true;
        messageObject.resetLayout();

        return messageObject;
    }

    // user_id > topic_id -> random_id - message
    private final DialogTopicIdKeyMap<BotDraftMessage> botTextDraftsByRandomIds = new DialogTopicIdKeyMap<>();

    public void onBotForumDraftUpdate(long userId, int topicId, long randomId, TLRPC.TL_textWithEntities text) {
        FileLog.d("[BotForum] onDraftNewDraft " + userId + " " + topicId + " " + randomId);

        BotDraftMessage draftMessage = botTextDraftsByRandomIds.get(userId, topicId, randomId);
        if (draftMessage == null) {
            draftMessage = new BotDraftMessage(userId, topicId, randomId, getUserConfig().getNewMessageId());
            botTextDraftsByRandomIds.put(userId, topicId, randomId, draftMessage);
        }
        final boolean isNew = draftMessage.messageObject == null;
        if (draftMessage.selfDestruct != null) {
            AndroidUtilities.cancelRunOnUIThread(draftMessage.selfDestruct);
        }

        draftMessage.selfDestruct = () -> onBotForumDraftTimeout(userId, topicId, randomId);
        draftMessage.text = text;
        draftMessage.messageObject = createDraftMessage(userId, topicId, randomId, draftMessage.localMessageId, text);

        AndroidUtilities.runOnUIThread(draftMessage.selfDestruct, getAppGlobalConfig().messageTypingDraftTtl.get(TimeUnit.MILLISECONDS));

        getNotificationCenter().postNotificationName(NotificationCenter.botForumDraftUpdate,
            new BotForumTextDraftUpdateNotification(userId, topicId, draftMessage.messageObject, isNew));
    }

    public MessageObject onBotForumDraftCheckNewMessages(long userId, int topicId, int messageId, String message) {
        LongSparseArray<BotDraftMessage> messages = botTextDraftsByRandomIds.get(userId, topicId);
        if (messages == null) {
            return null;
        }

        for (int i = 0; i < messages.size(); i++) {
            final BotDraftMessage draftMessage = messages.valueAt(i);
            if (message.startsWith(draftMessage.text.text)) {
                if (draftMessage.selfDestruct != null) {
                    AndroidUtilities.cancelRunOnUIThread(draftMessage.selfDestruct);
                }
                botTextDraftsByRandomIds.remove(userId, topicId, draftMessage.randomId);

                FileLog.d("[BotForum] onDraftNewMessage " + userId + " " + topicId);
                return draftMessage.messageObject;
            }
        }

        return null;
    }

    private void onBotForumDraftTimeout(long userId, int topicId, long randomId) {
        BotDraftMessage draftMessage = botTextDraftsByRandomIds.remove(userId, topicId, randomId);
        if (draftMessage == null) {
            return;
        }

        getNotificationCenter().postNotificationName(NotificationCenter.botForumDraftDelete,
                new BotForumTextDraftDeleteNotification(userId, topicId, draftMessage.localMessageId));
    }





    private static class BotDraftMessage {
        public final long userId;
        public final int topicId;
        public final long randomId;
        public final int localMessageId;

        private Runnable selfDestruct;
        private TLRPC.TL_textWithEntities text;
        private MessageObject messageObject;

        private BotDraftMessage(long userId, int topicId, long randomId, int localMessageId) {
            this.userId = userId;
            this.topicId = topicId;
            this.randomId = randomId;
            this.localMessageId = localMessageId;
        }
    }


    /** Send message interceptors **/

    public boolean beforeSendingFinalRequest(TLObject req, MessageObject msg, Runnable send) {
        return beforeSendingFinalRequest(req, Collections.singletonList(msg), send);
    }

    public boolean beforeSendingFinalRequest(TLObject req, List<MessageObject> messages, Runnable send) {
        if (messages == null || messages.isEmpty()) return true;

        final TLRPC.InputPeer inputPeer = TlUtils.getInputPeerFromSendMessageRequest(req);
        final long dialogId = DialogObject.getPeerDialogId(inputPeer);

        if (inputPeer == null || dialogId <= 0) {
            return true;
        }

        final TLRPC.User user = getMessagesController().getUser(dialogId);
        if (!UserObject.isBotForum(user)) {
            return true;
        }

        final long[] messageIds = new long[messages.size()];
        for (int a = 0; a < messages.size(); a++) {
            messageIds[a] = messages.get(a).getId();
        }

        final long messageRandomId = TlUtils.getOrCalculateRandomIdFromSendMessageRequest(req);

        final TLRPC.InputReplyTo inputReplyTo = TlUtils.getInputReplyToFromSendMessageRequest(req);
        if (inputReplyTo instanceof TLRPC.TL_inputReplyToMessage) {
            return true;
        }

        if (req instanceof TLRPC.TL_messages_forwardMessages) {
            if (((TLRPC.TL_messages_forwardMessages) req).top_msg_id != 0) {
                return true;
            }
        }

        final String messageText = TlUtils.getMessageFromSendMessageRequest(req);
        final long randomId = messageRandomId != 0 ? (~messageRandomId) : (getSendMessagesHelper().getNextRandomId());

        final String topicName;
        if (!TextUtils.isEmpty(messageText)) {
            if (messageText.length() > 16) {
                topicName = messageText.substring(0, 16) + "...";
            } else {
                topicName = messageText;
            }
        } else {
            topicName = LocaleController.getString(R.string.TopicsTitleMedia);
        }

        performSendBotTopicCreate(inputPeer, topicName, randomId, topicId -> {
            if (req instanceof TLRPC.TL_messages_forwardMessages) {
                TLRPC.TL_messages_forwardMessages request = (TLRPC.TL_messages_forwardMessages) req;
                request.top_msg_id = topicId;
                request.flags |= 512;
            } else {
                final TLRPC.TL_inputReplyToMessage fixedReplyTo = new TLRPC.TL_inputReplyToMessage();
                fixedReplyTo.reply_to_msg_id = topicId;
                TlUtils.setInputReplyToFromSendMessageRequest(req, fixedReplyTo);
            }

            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                for (long messageId : messageIds) {
                    getMessagesStorage().updateMessageTopicId(dialogId, messageId, topicId);
                }
                AndroidUtilities.runOnUIThread(send);
            });
        });

        return false;
    }



    //  userId -> topicId
    private final LongSparseArray<List<MessagesStorage.IntCallback>> pendingBotTopics = new LongSparseArray<>();

    private void performSendBotTopicCreate(final TLRPC.InputPeer inputPeer,
                                           final String title,
                                           final long randomId,
                                           final MessagesStorage.IntCallback onCreateTopic
    ) {
        final long dialogId = DialogObject.getPeerDialogId(inputPeer);

        List<MessagesStorage.IntCallback> callbacks = pendingBotTopics.get(dialogId);
        if (callbacks != null) {
            callbacks.add(onCreateTopic);
            return;
        }

        callbacks = new ArrayList<>(1);
        callbacks.add(onCreateTopic);
        pendingBotTopics.put(dialogId, callbacks);

        final TL_forum.TL_messages_createForumTopic req = new TL_forum.TL_messages_createForumTopic();
        final boolean titleMissing = TextUtils.isEmpty(title);
        req.title = titleMissing ? "#New Chat" : title;
        req.title_missing = true;
        req.peer = inputPeer;
        req.random_id = randomId;

        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (updates, err) -> {
            if (updates == null) {
                performSendBotTopicCreateComplete(dialogId, -1);
                return;
            }

            getMessagesController().processUpdates(updates, false);

            TLRPC.TL_updateMessageID updateMessageID = null;
            for (TLRPC.Update update: updates.updates) {
                if (update instanceof TLRPC.TL_updateMessageID) {
                    updateMessageID = (TLRPC.TL_updateMessageID) update;
                    break;
                }
            }

            if (updateMessageID == null) {
                performSendBotTopicCreateComplete(dialogId, -1);
                return;
            }

            final TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();
            final TLRPC.TL_messageService message = new TLRPC.TL_messageService();

            final TLRPC.TL_messageActionTopicCreate actionMessage = new TLRPC.TL_messageActionTopicCreate();
            actionMessage.title = title;

            message.action = actionMessage;
            message.peer_id = getMessagesController().getPeer(dialogId);
            message.dialog_id = dialogId;
            message.id = updateMessageID.id;
            message.date = (int) (System.currentTimeMillis() / 1000);

            forumTopic.id = updateMessageID.id;
            forumTopic.my = true;
            forumTopic.flags |= 2;
            forumTopic.topicStartMessage = message;
            forumTopic.title = title;
            forumTopic.top_message = updateMessageID.id;
            forumTopic.topMessage = message;
            forumTopic.from_id = getMessagesController().getPeer(getUserConfig().clientUserId);
            forumTopic.notify_settings = new TLRPC.TL_peerNotifySettings();
            forumTopic.icon_color = 0;
            forumTopic.title_missing = true;

            getMessagesController().getTopicsController().onTopicCreated(dialogId, forumTopic, true);

            performSendBotTopicCreateComplete(dialogId, updateMessageID.id);
            getNotificationCenter().postNotificationName(
                NotificationCenter.botForumTopicDidCreate,
                new BotForumTopicCreateNotification(dialogId, updateMessageID.id)
            );
        });
    }

    private void performSendBotTopicCreateComplete(final long dialogId, final int topicId) {
        final List<MessagesStorage.IntCallback> doneCallbacks = pendingBotTopics.get(dialogId);
        if (doneCallbacks != null) {
            pendingBotTopics.remove(dialogId);
            for (MessagesStorage.IntCallback onDone : doneCallbacks) {
                onDone.run(topicId);
            }
        }
    }



    /** Notification classes **/

    public static class BotForumTopicCreateNotification {
        public final long dialogId;
        public final int topicId;

        public BotForumTopicCreateNotification(long dialogId, int topicId) {
            this.dialogId = dialogId;
            this.topicId = topicId;
        }
    }

    public static class BotForumTextDraftUpdateNotification {
        public final long botUserId;
        public final long botTopicId;
        public final MessageObject messageObject;
        public final boolean isNew;

        public BotForumTextDraftUpdateNotification(long botUserId, long botTopicId, MessageObject messageObject, boolean isNew) {
            this.botUserId = botUserId;
            this.botTopicId = botTopicId;
            this.messageObject = messageObject;
            this.isNew = isNew;
        }
    }

    public static class BotForumTextDraftDeleteNotification {
        public final long botUserId;
        public final long botTopicId;
        public final int messageId;

        public BotForumTextDraftDeleteNotification(long botUserId, long botTopicId, int messageId) {
            this.botUserId = botUserId;
            this.botTopicId = botTopicId;
            this.messageId = messageId;
        }
    }


    /** Helper Utils **/

    public static boolean isBotForum(int currentAccount, long dialogId) {
        if (dialogId > 0) {
            return UserObject.isBotForum(MessagesController.getInstance(currentAccount).getUser(dialogId));
        } else {
            MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return false;
        }
    }



    /** Instance **/

    private BotForumHelper(int currentAccount) {
        super(currentAccount);
    }

    private static volatile BotForumHelper[] Instance = new BotForumHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public static BotForumHelper getInstance(final int num) {
        BotForumHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (BotForumHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new BotForumHelper(num);
                }
            }
        }
        return localInstance;
    }



    public static class BotDraftAnimationsPool {
        private final DialogTopicIdKeyMap<MultiLayoutTypingAnimator> animators = new DialogTopicIdKeyMap<>();
        private final SparseIntArray ids = new SparseIntArray();   // messageId -> pendingId;

        @Nullable
        public MultiLayoutTypingAnimator getAnimator(long dialogId, int messageId, boolean allowCreate) {
            final int animatorId = messageId > 0 ? ids.get(messageId, 0) : messageId;
            if (animatorId == 0) {
                return null;
            }

            MultiLayoutTypingAnimator animator = animators.get(dialogId, 0, animatorId);
            if (animator == null && allowCreate) {
                animator = new MultiLayoutTypingAnimator();
                animators.put(dialogId, 0, animatorId, animator);
            }

            return animator;
        }

        public void bind(int pendingMessageId, int messageId) {
            ids.put(messageId, pendingMessageId);
        }

        public void removeAnimator(long dialogId, int messageId) {
            final int animatorId = messageId > 0 ? ids.get(messageId, 0) : messageId;
            if (animatorId == 0) {
                return;
            }

            animators.remove(dialogId, 0, animatorId);
        }
    }

    public static class DialogTopicIdKeyMap<T> {
        private final LongSparseArray<LongSparseArray<LongSparseArray<T>>> map = new LongSparseArray<>();

        public LongSparseArray<T> get(long dialogId, long topicId) {
            LongSparseArray<LongSparseArray<T>> topics = map.get(dialogId);
            if (topics == null) {
                return null;
            }

            return topics.get(topicId);
        }

        public T get(long dialogId, long topicId, long messageId) {
            LongSparseArray<T> messages = get(dialogId, topicId);
            if (messages == null) {
                return null;
            }
            return messages.get(messageId);
        }

        public T put(long dialogId, long topicId, long messageId, T value) {
            LongSparseArray<LongSparseArray<T>> topics = map.get(dialogId);
            if (topics == null) {
                topics = new LongSparseArray<>();
                map.put(dialogId, topics);
            }

            LongSparseArray<T> messages = topics.get(topicId);
            if (messages == null) {
                messages = new LongSparseArray<>();
                topics.put(topicId, messages);
            }

            T oldValue = messages.get(messageId);
            messages.put(messageId, value);

            return oldValue;
        }

        public T remove(long dialogId, long topicId, long messageId) {
            LongSparseArray<LongSparseArray<T>> topics = map.get(dialogId);
            if (topics == null) {
                return null;
            }

            LongSparseArray<T> messages = topics.get(topicId);
            if (messages == null) {
                return null;
            }

            T oldValue = messages.get(messageId);
            messages.remove(messageId);

            return oldValue;
        }
    }
}
