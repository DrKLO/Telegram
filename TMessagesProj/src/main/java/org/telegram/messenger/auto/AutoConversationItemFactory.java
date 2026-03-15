package org.telegram.messenger.auto;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.messaging.model.CarMessage;
import androidx.car.app.messaging.model.ConversationCallback;
import androidx.car.app.messaging.model.ConversationItem;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AutoConversationItemFactory {
    private static final int NO_MESSAGE_LIMIT = Integer.MAX_VALUE;
    private static final int MAX_MESSAGES_FILTER_STANDARD = 3;

    private static final class AutoConversationUnreadState {
        final int unreadCount;
        final boolean hasUnread;
        final int readInboxMaxId;

        AutoConversationUnreadState(int unreadCount, int readInboxMaxId) {
            this.unreadCount = Math.max(0, unreadCount);
            this.hasUnread = this.unreadCount > 0;
            this.readInboxMaxId = Math.max(0, readInboxMaxId);
        }
    }

    private static final class AutoPreviewMessageState {
        final MessageObject messageObject;
        final String body;
        final long timestamp;
        final boolean outgoing;
        final boolean read;

        AutoPreviewMessageState(@NonNull MessageObject messageObject,
                                @NonNull String body,
                                long timestamp,
                                boolean outgoing,
                                boolean read) {
            this.messageObject = messageObject;
            this.body = body;
            this.timestamp = timestamp;
            this.outgoing = outgoing;
            this.read = read;
        }
    }

    interface ViewportListener {
        void onVisibleRangeChanged(@NonNull String listKey, int startIndex, int endIndex);

        void onListHidden(@NonNull String listKey);
    }

    interface MoreActionListener {
        void onLoadMore(@NonNull String listKey);
    }

    private final CarContext carContext;
    private final int currentAccount;
    private final AccountInstance accountInstance;
    private final AutoAvatarProvider avatarProvider;
    private final AutoGeoRepository geoRepository;
    private final AutoDialogPreviewFormatter previewFormatter = new AutoDialogPreviewFormatter();
    private final AutoMessagePreviewRepository messagePreviewRepository;
    private final AutoVoiceRecorderController voiceRecorderController;

    AutoConversationItemFactory(@NonNull CarContext carContext,
                                int currentAccount,
                                @NonNull AccountInstance accountInstance,
                                @NonNull AutoAvatarProvider avatarProvider,
                                @NonNull AutoGeoRepository geoRepository,
                                @NonNull AutoMessagePreviewRepository messagePreviewRepository,
                                @NonNull AutoVoiceRecorderController voiceRecorderController) {
        this.carContext = carContext;
        this.currentAccount = currentAccount;
        this.accountInstance = accountInstance;
        this.avatarProvider = avatarProvider;
        this.geoRepository = geoRepository;
        this.messagePreviewRepository = messagePreviewRepository;
        this.voiceRecorderController = voiceRecorderController;
    }

    ItemList buildItemList(@NonNull Screen screen,
                           @NonNull String listKey,
                           @NonNull AutoListRenderMode renderMode,
                           @NonNull AutoDialogsRepository.AutoListSnapshot snapshot,
                           @NonNull String emptyMessage,
                           @NonNull ViewportListener viewportListener,
                           MoreActionListener moreActionListener) {
        ItemList.Builder listBuilder = new ItemList.Builder();
        listBuilder.setOnItemsVisibilityChangedListener((startIndex, endIndex) -> {
            if (startIndex < 0 || endIndex < 0) {
                viewportListener.onListHidden(listKey);
            } else {
                viewportListener.onVisibleRangeChanged(listKey, startIndex, endIndex);
            }
        });
        if (snapshot.dialogs.isEmpty()) {
            listBuilder.setNoItemsMessage(emptyMessage);
            return listBuilder.build();
        }

        MessagesController messagesController = accountInstance.getMessagesController();
        int count = 0;
        for (int i = 0; i < snapshot.dialogs.size(); i++) {
            TLRPC.Dialog dialog = snapshot.dialogs.get(i);
            if (dialog == null || dialog.id == 0) {
                continue;
            }
            long dialogId = dialog.id;
            String title = avatarProvider.resolveDialogName(dialog.id);
            if (TextUtils.isEmpty(title)) {
                continue;
            }
            if (DialogObject.isUserDialog(dialog.id)) {
                TLRPC.User user = messagesController.getUser(dialog.id);
                if (UserObject.isDeleted(user)) {
                    continue;
                }
            }

            CarIcon avatar = renderMode == AutoListRenderMode.CHANNELS_COMPACT
                    ? avatarProvider.getRowDialogIcon(dialogId)
                    : avatarProvider.getDialogIcon(dialogId);
            if (renderMode == AutoListRenderMode.CHANNELS_COMPACT) {
                Row row = buildChannelRow(screen, messagesController, dialog, title, avatar);
                if (row == null) {
                    continue;
                }
                listBuilder.addItem(row);
            } else {
                ConversationItem item = buildConversationItem(
                        screen,
                        messagesController,
                        dialog,
                        title,
                        avatar,
                        getPreviewMessageLimit(renderMode),
                        renderMode != AutoListRenderMode.FILTER_STANDARD);
                if (item == null) {
                    continue;
                }
                listBuilder.addItem(item);
            }
            count++;
        }

        if (snapshot.hasMore && moreActionListener != null) {
            listBuilder.addItem(new Row.Builder()
                    .setTitle(getMoreTitle(renderMode, snapshot.remainingCount))
                    .setBrowsable(true)
                    .setOnClickListener(() -> moreActionListener.onLoadMore(listKey))
                    .build());
        }

        if (count == 0) {
            listBuilder.setNoItemsMessage(emptyMessage);
        }
        return listBuilder.build();
    }

    long getVoiceSignature() {
        return voiceRecorderController.getState().ordinal() * 31L + voiceRecorderController.getActiveDialogId();
    }

    private ConversationItem buildConversationItem(@NonNull Screen screen,
                                                   @NonNull MessagesController messagesController,
                                                   @NonNull TLRPC.Dialog dialog,
                                                   @NonNull String title,
                                                   CarIcon avatar,
                                                   int maxMessagesPerChat,
                                                   boolean preferUnreadPreview) {
        List<CarMessage> messages = buildCarMessages(messagesController, dialog, maxMessagesPerChat, preferUnreadPreview);
        if (messages.isEmpty()) {
            return null;
        }

        final long dialogId = dialog.id;
        final int topMessageId = dialog.top_message;
        ConversationItem.Builder builder = new ConversationItem.Builder(
                String.valueOf(dialogId),
                CarText.create(title),
                avatarProvider.getSelfPerson(),
                messages,
                new ConversationCallback() {
                    @Override
                    public void onMarkAsRead() {
                        accountInstance.getMessagesController().markDialogAsRead(
                                dialogId, topMessageId, topMessageId, 0, false, 0, 0, true, 0);
                    }

                    @Override
                    public void onTextReply(@NonNull String replyText) {
                        sendReply(dialogId, topMessageId, replyText, null);
                    }
                }
        );
        builder.setGroupConversation(DialogObject.isChatDialog(dialogId));
        if (avatar != null) {
            builder.setIcon(avatar);
        }
        addPrimaryAction(screen, builder, dialogId);
        return builder.build();
    }

    private Row buildChannelRow(@NonNull Screen screen,
                                @NonNull MessagesController messagesController,
                                @NonNull TLRPC.Dialog dialog,
                                @NonNull String title,
                                CarIcon avatar) {
        AutoMessagePreviewRepository.Projection projection = messagePreviewRepository.getProjection(dialog.id);
        if (projection == AutoMessagePreviewRepository.Projection.EMPTY) {
            messagePreviewRepository.requestIfVisible(dialog.id);
        }
        String previewText = projection.primaryPreviewText;
        if (TextUtils.isEmpty(previewText)) {
            ArrayList<MessageObject> dialogMessages = messagesController.dialogMessage.get(dialog.id);
            if (dialogMessages != null && !dialogMessages.isEmpty()) {
                previewText = previewFormatter.formatCompactChannelPreview(dialogMessages.get(0));
            }
        }
        previewText = previewFormatter.compact(previewText);
        final String finalPreviewText = previewText;
        Row.Builder builder = new Row.Builder()
                .setTitle(title)
                .addText(finalPreviewText)
                .setBrowsable(true)
                .setOnClickListener(() -> screen.getScreenManager().push(
                        new AutoChannelPreviewScreen(
                                screen.getCarContext(),
                                accountInstance,
                                dialog.id,
                                dialog.top_message,
                                title,
                                finalPreviewText,
                                avatar)));
        if (avatar != null) {
            builder.setImage(avatar, Row.IMAGE_TYPE_LARGE);
        }
        int unreadCount = Math.max(dialog.unread_count, projection.unreadPreviewCount);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("[AutoChannelBadge] dialog=" + dialog.id
                    + " title=" + title
                    + " dialogUnread=" + dialog.unread_count
                    + " projectionUnread=" + projection.unreadPreviewCount
                    + " passedUnread=" + unreadCount);
        }
        if (unreadCount > 0) {
            builder.setNumericDecoration(Math.min(unreadCount, 99));
        }
        return builder.build();
    }

    private int getPreviewMessageLimit(@NonNull AutoListRenderMode renderMode) {
        switch (renderMode) {
            case UNREAD_COMPACT:
                return NO_MESSAGE_LIMIT;
            case PINNED_COMPACT:
                return NO_MESSAGE_LIMIT;
            case BOTS_COMPACT:
                return NO_MESSAGE_LIMIT;
            case FILTER_STANDARD:
                return MAX_MESSAGES_FILTER_STANDARD;
            case CHANNELS_COMPACT:
            default:
                return 0;
        }
    }

    @NonNull
    private String getMoreTitle(@NonNull AutoListRenderMode renderMode, int remainingCount) {
        String prefix = remainingCount > 0 ? "Show " + remainingCount + " more " : "Show more ";
        switch (renderMode) {
            case UNREAD_COMPACT:
                return prefix + "unread chats";
            case PINNED_COMPACT:
                return prefix + "pinned chats";
            case BOTS_COMPACT:
                return prefix + "bots";
            case CHANNELS_COMPACT:
                return prefix + "channels";
            case FILTER_STANDARD:
            default:
                return prefix + "chats";
        }
    }

    private List<CarMessage> buildCarMessages(MessagesController messagesController,
                                              TLRPC.Dialog dialog,
                                              int maxMessagesPerChat,
                                              boolean preferUnreadPreview) {
        AutoMessagePreviewRepository.Projection projection = messagePreviewRepository.getProjection(dialog.id);
        boolean usingPreviewRepository = !projection.messages.isEmpty();
        ArrayList<MessageObject> sourceMessages = usingPreviewRepository ? new ArrayList<>(projection.messages) : messagesController.dialogMessage.get(dialog.id);
        if (!usingPreviewRepository) {
            messagePreviewRepository.requestIfVisible(dialog.id);
        }
        if ((sourceMessages == null || sourceMessages.isEmpty()) && dialog.top_message != 0) {
            MessageObject cachedTopMessage = messagesController.dialogMessagesByIds.get(dialog.top_message);
            if (cachedTopMessage != null) {
                sourceMessages = new ArrayList<>(1);
                sourceMessages.add(cachedTopMessage);
            } else {
                TLRPC.Message storedMessage = accountInstance.getMessagesStorage().getMessage(dialog.id, dialog.top_message);
                if (storedMessage != null) {
                    sourceMessages = new ArrayList<>(1);
                    sourceMessages.add(new MessageObject(currentAccount, storedMessage, false, false));
                }
            }
        }
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return buildFallbackMessages(dialog);
        }
        sourceMessages.sort((first, second) -> {
            int firstDate = first != null && first.messageOwner != null ? first.messageOwner.date : 0;
            int secondDate = second != null && second.messageOwner != null ? second.messageOwner.date : 0;
            return Integer.compare(secondDate, firstDate);
        });

        AutoConversationUnreadState unreadState = new AutoConversationUnreadState(
                dialog.unread_count,
                resolveReadInboxMaxId(messagesController, dialog)
        );
        ArrayList<MessageObject> selected = new ArrayList<>();
        if (preferUnreadPreview && dialog.unread_count > 0) {
            for (int i = 0; i < sourceMessages.size() && selected.size() < maxMessagesPerChat; i++) {
                MessageObject messageObject = sourceMessages.get(i);
                if (messageObject != null && isUnreadCandidate(messageObject, unreadState)) {
                    selected.add(messageObject);
                }
            }
        }
        for (int i = 0; i < sourceMessages.size() && selected.size() < maxMessagesPerChat; i++) {
            MessageObject messageObject = sourceMessages.get(i);
            if (messageObject != null && !selected.contains(messageObject)) {
                selected.add(messageObject);
            }
        }
        if (selected.isEmpty()) {
            return Collections.emptyList();
        }

        String debugTitle = null;
        if (BuildVars.LOGS_ENABLED && unreadState.hasUnread) {
            debugTitle = avatarProvider.resolveDialogName(dialog.id);
            FileLog.d("[AutoUnread] dialog=" + dialog.id
                    + " title=" + debugTitle
                    + " unreadCount=" + dialog.unread_count
                    + " readInboxMaxId=" + unreadState.readInboxMaxId
                    + " source=" + (usingPreviewRepository ? "previewCache" : "dialogMessage")
                    + " sourceSize=" + sourceMessages.size()
                    + " selectedSize=" + selected.size());
        }
        ArrayList<AutoPreviewMessageState> previewStates = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            MessageObject messageObject = selected.get(i);
            String body = previewFormatter.format(messageObject);
            if (TextUtils.isEmpty(body)) {
                continue;
            }

            long timestamp = messageObject.messageOwner != null
                    ? (long) messageObject.messageOwner.date * 1000L
                    : System.currentTimeMillis();
            boolean isOutgoing = messageObject.isOut();
            boolean isUnread = isUnreadCandidate(messageObject, unreadState);
            boolean isRead = !isUnread;
            if (BuildVars.LOGS_ENABLED && unreadState.hasUnread) {
                FileLog.d("[AutoUnread]  msgId=" + messageObject.getId()
                        + " out=" + isOutgoing
                        + " messageUnread=" + messageObject.isUnread()
                        + " boundaryUnread=" + (messageObject.getId() > unreadState.readInboxMaxId)
                        + " assignedRead=" + isRead
                        + " text=" + body);
            }
            previewStates.add(new AutoPreviewMessageState(
                    messageObject,
                    body,
                    timestamp,
                    isOutgoing,
                    isRead
            ));
        }

        if (previewStates.isEmpty()) {
            return buildFallbackMessages(dialog);
        }

        if (BuildVars.LOGS_ENABLED && unreadState.hasUnread) {
            boolean allAssignedRead = true;
            for (int i = 0; i < previewStates.size(); i++) {
                if (!previewStates.get(i).read) {
                    allAssignedRead = false;
                    break;
                }
            }
            if (allAssignedRead) {
                FileLog.d("[AutoUnread]  warning all preview messages ended up read for dialog="
                        + dialog.id + " unreadCount=" + unreadState.unreadCount);
            }
        }

        ArrayList<CarMessage> built = new ArrayList<>(previewStates.size());
        for (int i = 0; i < previewStates.size(); i++) {
            AutoPreviewMessageState previewState = previewStates.get(i);
            Person sender = buildSenderPerson(messagesController, previewState.messageObject);
            built.add(new CarMessage.Builder()
                    .setSender(sender)
                    .setBody(CarText.create(previewState.body))
                    .setReceivedTimeEpochMillis(previewState.timestamp)
                    .setRead(previewState.read)
                    .build());
        }

        Collections.reverse(built);
        return built;
    }

    @NonNull
    private List<CarMessage> buildFallbackMessages(@NonNull TLRPC.Dialog dialog) {
        long timestamp = dialog.last_message_date != 0
                ? (long) dialog.last_message_date * 1000L
                : System.currentTimeMillis();
        CarMessage message = new CarMessage.Builder()
                .setSender(avatarProvider.getSelfPerson())
                .setBody(CarText.create("Message"))
                .setReceivedTimeEpochMillis(timestamp)
                .setRead(dialog.unread_count <= 0)
                .build();
        return Collections.singletonList(message);
    }

    private boolean isUnreadCandidate(@NonNull MessageObject messageObject,
                                      @NonNull AutoConversationUnreadState unreadState) {
        if (messageObject.isOut()) {
            return false;
        }
        return messageObject.isUnread() || messageObject.getId() > unreadState.readInboxMaxId;
    }

    private int resolveReadInboxMaxId(@NonNull MessagesController messagesController, @NonNull TLRPC.Dialog dialog) {
        Integer readInboxMax = messagesController.dialogs_read_inbox_max.get(dialog.id);
        if (readInboxMax != null) {
            return Math.max(dialog.read_inbox_max_id, readInboxMax);
        }
        return dialog.read_inbox_max_id;
    }

    private Person buildSenderPerson(MessagesController messagesController, MessageObject messageObject) {
        if (messageObject.isOutOwner()) {
            return avatarProvider.getSelfPerson();
        }

        long senderId = messageObject.getSenderId();
        if (senderId > 0) {
            TLRPC.User user = messagesController.getUser(senderId);
            String name = user != null ? UserObject.getUserName(user) : "User";
            return avatarProvider.buildSenderPerson(user, name);
        }
        if (senderId < 0) {
            TLRPC.Chat chat = messagesController.getChat(-senderId);
            String name = chat != null ? chat.title : "Chat";
            return avatarProvider.buildSenderPerson(chat, name);
        }
        return new Person.Builder().setName("Unknown").setKey("unknown_0").build();
    }

    private void addPrimaryAction(Screen screen, ConversationItem.Builder builder, long dialogId) {
        if (addVoiceAction(builder, dialogId)) {
            return;
        }
        addNavigateAction(screen, builder, dialogId);
    }

    private void addNavigateAction(Screen screen, ConversationItem.Builder builder, long dialogId) {
        AutoGeoRepository.State geoState = geoRepository.getState(dialogId);
        if (geoState == null) {
            geoRepository.requestIfVisible(dialogId);
            return;
        }
        if (geoState.status != AutoGeoRepository.Status.PRESENT || geoState.result == null) {
            return;
        }
        GeoExtractor.GeoResult geo = geoState.result;
        if (Double.isNaN(geo.lat) || Double.isNaN(geo.lng) || Double.isInfinite(geo.lat) || Double.isInfinite(geo.lng)) {
            return;
        }
        CarIcon navIcon = new CarIcon.Builder(
                IconCompat.createWithResource(screen.getCarContext(), android.R.drawable.ic_menu_directions))
                .build();
        builder.addAction(new Action.Builder()
                .setTitle("Navigate")
                .setIcon(navIcon)
                .setOnClickListener(() -> {
                    Intent navIntent = new Intent(CarContext.ACTION_NAVIGATE,
                            Uri.parse("google.navigation:q=" + geo.lat + "," + geo.lng));
                    screen.getCarContext().startCarApp(navIntent);
                })
                .build());
    }

    private boolean addVoiceAction(ConversationItem.Builder builder, long dialogId) {
        String title = voiceRecorderController.isRecordingForDialog(dialogId) ? "Stop" : "Voice";
        CarIcon micIcon = new CarIcon.Builder(
                IconCompat.createWithResource(carContext, android.R.drawable.ic_btn_speak_now))
                .build();
        builder.addAction(new Action.Builder()
                .setTitle(title)
                .setIcon(micIcon)
                .setOnClickListener(() -> voiceRecorderController.toggleRecording(dialogId))
                .build());
        return true;
    }

    private void sendReply(long dialogId, int maxId, String text, Runnable onSent) {
        new AutoComposeSender(currentAccount, accountInstance).sendText(dialogId, maxId, text, onSent);
    }
}
