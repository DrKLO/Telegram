package org.telegram.messenger.utils;

import android.util.SparseIntArray;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.telegram.messenger.BaseController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_ephemeral;
import org.telegram.tgnet.tl.TL_update;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EphemeralMessagesHelper extends BaseController {
    public static TL_ephemeral.EphemeralMessage convertFakeDefaultToEphemeral(TLRPC.Message message, int topicId) {
        TL_ephemeral.TL_ephemeralMessage ephemeralMessage = new TL_ephemeral.TL_ephemeralMessage();
        ephemeralMessage.out = message.out;
        ephemeralMessage.invert_media = message.invert_media;
        ephemeralMessage.noforwards = message.noforwards;
        ephemeralMessage.id = MessageObject.ephemeralMessageIdUnpack(message.id);
        ephemeralMessage.from_id = message.from_id;
        ephemeralMessage.peer_id = message.peer_id;
        ephemeralMessage.receiver_id = message.ephemeralReceiverBotId;
        if (topicId != 0) {
            ephemeralMessage.top_msg_id = topicId;
            ephemeralMessage.flags |= TLObject.FLAG_1;
        }
        ephemeralMessage.date = message.date;
        ephemeralMessage.message = message.message;
        ephemeralMessage.entities = message.entities;
        ephemeralMessage.media = message.media;
        ephemeralMessage.reply_markup = message.reply_markup;
        ephemeralMessage.reply_to = message.reply_to;
        ephemeralMessage.rich_message = message.rich_message;
        ephemeralMessage.via_bot_id = message.via_bot_id;
        ephemeralMessage.anchor_msg_id = message.ephemeralAnchorMsgId;
        return ephemeralMessage;
    }

    public static TLRPC.TL_message convertEphemeralToFakeDefault(TL_ephemeral.EphemeralMessage ephemeralMessage) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.out = ephemeralMessage.out;
        message.id = MessageObject.ephemeralMessageIdPack(ephemeralMessage.id);

        if (ephemeralMessage.from_id != null) {
            message.from_id = ephemeralMessage.from_id;
            message.flags |= TLObject.FLAG_8;
        }
        message.peer_id = ephemeralMessage.peer_id;
        message.ephemeralAnchorMsgId = ephemeralMessage.anchor_msg_id;
        if (ephemeralMessage.welcome || ephemeralMessage.anchor_msg_id != 0) {
            message.ephemeralReceiverBotId = -1;
        } else {
            message.ephemeralReceiverBotId = ephemeralMessage.receiver_id;
        }
        message.date = ephemeralMessage.date;
        message.message = ephemeralMessage.message;

        if (ephemeralMessage.noforwards) {
            message.noforwards = true;
            message.flags |= TLObject.FLAG_26;
        }
        if (ephemeralMessage.invert_media) {
            message.invert_media = true;
            message.flags |= TLObject.FLAG_27;
        }
        if (ephemeralMessage.rich_message != null) {
            message.rich_message = ephemeralMessage.rich_message;
            message.flags2 |= TLObject.FLAG_13;
        }
        if (ephemeralMessage.entities != null && !ephemeralMessage.entities.isEmpty()) {
            message.entities = ephemeralMessage.entities;
            message.flags |= TLObject.FLAG_7;
        }
        if (ephemeralMessage.media != null && ephemeralMessage.rich_message == null) {
            message.media = ephemeralMessage.media;
            message.flags |= TLObject.FLAG_9;
        }
        if (ephemeralMessage.reply_markup != null) {
            message.reply_markup = ephemeralMessage.reply_markup;
            message.flags |= TLObject.FLAG_6;
        }
        if (ephemeralMessage.via_bot_id != 0) {
            message.via_bot_id = ephemeralMessage.via_bot_id;
            message.flags |= TLObject.FLAG_11;
        }
        if (ephemeralMessage.reply_to != null) {
            message.reply_to = TLObject.deepCopy(ephemeralMessage.reply_to, TLRPC.MessageReplyHeader::TLdeserialize);
            if (ephemeralMessage.reply_to.reply_to_ephemeral) {
                if (message.reply_to.reply_to_msg_id != 0) {
                    message.reply_to.reply_to_msg_id = MessageObject.ephemeralMessageIdPack(message.reply_to.reply_to_msg_id);
                    message.reply_to.reply_to_msg_id |= TLObject.FLAG_4;
                }
            }
            if (message.reply_to.reply_to_top_id == 0 && ephemeralMessage.top_msg_id != 0) {
                message.reply_to.reply_to_top_id = ephemeralMessage.top_msg_id;
                message.reply_to.forum_topic = true;
                message.reply_to.flags |= TLObject.FLAG_1;
            }
            message.flags |= TLObject.FLAG_3;
        } else if (ephemeralMessage.top_msg_id != 0) {
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.reply_to_top_id = ephemeralMessage.top_msg_id;
            message.reply_to.forum_topic = true;
            message.reply_to.flags |= TLObject.FLAG_1;
            message.flags |= TLObject.FLAG_3;
        }

        MessageObject.getDialogId(message);

        return message;
    }






    public boolean beforeSendingFinalRequest(TLObject req, MessageObject msg, Utilities.Callback<TLObject> send) {
        return beforeSendingFinalRequest(req, Collections.singletonList(msg), send);
    }

    public boolean beforeSendingFinalRequest(TLObject req, List<MessageObject> messages, Utilities.Callback<TLObject> send) {
        if (messages == null || messages.isEmpty()) return true;
        if (req instanceof TL_ephemeral.TL_sendMessage) {
            return true;
        }

        if (req instanceof TLRPC.TL_messages_sendMessage) {
            final TLRPC.TL_messages_sendMessage request = (TLRPC.TL_messages_sendMessage) req;
            if (request.ephemeralReceiverBotId != 0) {
                TL_ephemeral.TL_sendMessage newRequest = new TL_ephemeral.TL_sendMessage();
                newRequest.peer = request.peer;
                if (request.ephemeralReceiverBotId == -1) {
                    newRequest.receiver_id = new TLRPC.TL_inputUserEmpty();
                    newRequest.welcome = true;
                } else {
                    newRequest.receiver_id = getMessagesController().getInputUser(request.ephemeralReceiverBotId);
                }
                newRequest.query_id = 0; // ?
                newRequest.message = request.message;
                newRequest.entities = request.entities;
                newRequest.media = null;
                newRequest.reply_markup = request.reply_markup;
                newRequest.rich_message = request.rich_message;
                newRequest.random_id = request.random_id;
                newRequest.reply_to = applyReplyTo(request.reply_to);
                newRequest.rich_message = request.rich_message;
                newRequest.invert_media = request.invert_media;

                send.run(newRequest);
                return false;
            } else {
                final long dialogId = DialogObject.getPeerDialogId(request.peer);
                if (dialogId < 0) {
                    TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
                    if (chatFull != null) {
                        final long ephemeralBotId = getEphemeralCommandBotId(request.message, chatFull.bot_info);
                        if (ephemeralBotId != 0) {
                            request.ephemeralReceiverBotId = ephemeralBotId;
                            return beforeSendingFinalRequest(req, messages, send);
                        }
                    } else {
                        // todo: request chat full ???
                    }
                }
            }
        }

        if (req instanceof TLRPC.TL_messages_sendMedia) {
            final TLRPC.TL_messages_sendMedia request = (TLRPC.TL_messages_sendMedia) req;
            if (request.ephemeralReceiverBotId != 0) {
                TL_ephemeral.TL_sendMessage newRequest = new TL_ephemeral.TL_sendMessage();
                newRequest.peer = request.peer;
                if (request.ephemeralReceiverBotId == -1) {
                    newRequest.receiver_id = new TLRPC.TL_inputUserEmpty();
                    newRequest.welcome = true;
                } else {
                    newRequest.receiver_id = getMessagesController().getInputUser(request.ephemeralReceiverBotId);
                }
                newRequest.query_id = 0; // ?
                newRequest.message = request.message;
                newRequest.entities = request.entities;
                newRequest.media = request.media;
                newRequest.reply_markup = request.reply_markup;
                newRequest.rich_message = null;
                newRequest.random_id = request.random_id;
                newRequest.reply_to = applyReplyTo(request.reply_to);
                newRequest.invert_media = request.invert_media;

                send.run(newRequest);
                return false;
            } else {
                final long dialogId = DialogObject.getPeerDialogId(request.peer);
                if (dialogId < 0) {
                    TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
                    if (chatFull != null) {
                        final long ephemeralBotId = getEphemeralCommandBotId(request.message, chatFull.bot_info);
                        if (ephemeralBotId != 0) {
                            request.ephemeralReceiverBotId = ephemeralBotId;
                            return beforeSendingFinalRequest(req, messages, send);
                        }
                    } else {
                        // todo: request chat full ???
                    }
                }
            }
        }

        return true;
    }

    private static TLRPC.InputReplyTo applyReplyTo(TLRPC.InputReplyTo oldReplyToX) {
        if (oldReplyToX instanceof TLRPC.TL_inputReplyToMessage) {
            final TLRPC.TL_inputReplyToMessage oldReplyTo = (TLRPC.TL_inputReplyToMessage) oldReplyToX;
            if (MessageObject.isEphemeralMessageId(oldReplyTo.reply_to_msg_id)) {
                final TLRPC.TL_inputReplyToEphemeralMessage replyTo = new TLRPC.TL_inputReplyToEphemeralMessage();
                replyTo.id = MessageObject.ephemeralMessageIdUnpack(oldReplyTo.reply_to_msg_id);
                return replyTo;
            }
        }
        return oldReplyToX;
    }



    /* Utils */

    public boolean isEphemeralCommand(String text, LongSparseArray<TL_bots.BotInfo> botInfo) {
        return getEphemeralCommandBotId(text, botInfo) > 0;
    }

    public long getEphemeralCommandBotId(String text, long dialogId) {
        if (dialogId < 0) {
            TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
            if (chatFull != null) {
                return getEphemeralCommandBotId(text, chatFull.bot_info);
            }
        }
        return 0;
    }

    public long getEphemeralCommandBotId(String text, List<TL_bots.BotInfo> botInfo) {
        if (text == null || botInfo == null || botInfo.isEmpty() || !text.startsWith("/") || text.length() < 2) return 0;

        final LongSparseArray<TL_bots.BotInfo> bots = new LongSparseArray<>(botInfo.size());
        for (TL_bots.BotInfo bot : botInfo) {
            bots.put(bot.user_id, bot);
        }

        return getEphemeralCommandBotId(text, bots);
    }

    public long getEphemeralCommandBotId(String text, LongSparseArray<TL_bots.BotInfo> botInfo) {
        if (text == null || botInfo == null || botInfo.isEmpty() || !text.startsWith("/") || text.length() < 2) return 0;

        final String body;
        int spaceIdx = text.indexOf(' ');
        if (spaceIdx != -1) {
            body = text.substring(1, spaceIdx);
        } else {
            body = text.substring(1);
        }

        final String command;
        final String botUsername;
        int atIdx = body.indexOf('@');
        if (atIdx != -1) {
            command = body.substring(0, atIdx);
            botUsername = body.substring(atIdx + 1);
        } else {
            command = body;
            botUsername = null;
        }

        if (command.isEmpty()) return 0;

        if (botUsername != null) {
            for (int i = 0; i < botInfo.size(); i++) {
                final TL_bots.BotInfo info = botInfo.valueAt(i);
                final TLRPC.User botUser = getMessagesController().getUser(info.user_id);
                if (!UserObject.hasPublicUsername(botUser, botUsername)) continue;
                for (TLRPC.BotCommand cmd : info.commands) {
                    if (cmd.command.equalsIgnoreCase(command)) {
                        return cmd.ephemeral ? info.user_id : 0;
                    }
                }
            }
        } else {
            long foundId = 0;
            boolean foundEphemeral = false;
            for (int i = 0; i < botInfo.size(); i++) {
                final TL_bots.BotInfo info = botInfo.valueAt(i);
                for (TLRPC.BotCommand cmd : info.commands) {
                    if (cmd.command.equalsIgnoreCase(command)) {
                        if (foundId != 0) return 0;
                        foundId = info.user_id;
                        foundEphemeral = cmd.ephemeral;
                    }
                }
            }
            return foundEphemeral ? foundId : 0;
        }

        return 0;
    }

    public static class EphemeralUpdates {
        public final Struct welcomeMessagesToAdd = new Struct();
        public final Struct welcomeMessagesToEdit = new Struct();

        public final StructBuilder ephemeralMessagesToAdd = new StructBuilder();
        public final StructBuilder ephemeralMessagesToEdit = new StructBuilder();
        public final StructBuilder welcomeMessagesAnchor = new StructBuilder();

        public static class StructBuilder extends Struct {
            public void put(TL_ephemeral.EphemeralMessage ephemeralMessage) {
                messages.add(ephemeralMessage);
            }

            public void build(int currentAccount, AbstractMap<Long, TLRPC.User> usersDict, AbstractMap<Long, TLRPC.Chat> chatsDict, int editDate) {
                for (TL_ephemeral.EphemeralMessage ephemeralMessage : messages) {
                    final TLRPC.Message convertedMessage = convertEphemeralToFakeDefault(ephemeralMessage);
                    final MessageObject messageObject = new MessageObject(currentAccount, convertedMessage, usersDict, chatsDict, true, true);
                    final long dialogId = MessageObject.getDialogId(convertedMessage);

                    if (editDate != 0) {
                        convertedMessage.edit_date = editDate;
                        convertedMessage.flags |= TLObject.FLAG_15;
                    }

                    TLRPC.TL_messages_messages res = convertedByDialog.get(dialogId);
                    if (res == null) {
                        res = new TLRPC.TL_messages_messages();
                        convertedByDialog.put(dialogId, res);
                    }
                    res.messages.add(convertedMessage);

                    ArrayList<MessageObject> messageObjects = objectsByDialog.get(dialogId);
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                        objectsByDialog.put(dialogId, messageObjects);
                    }
                    messageObjects.add(messageObject);
                }
            }

            public boolean isEmpty() {
                return messages.isEmpty();
            }
        }

        public static class Struct {
            public final ArrayList<TL_ephemeral.EphemeralMessage> messages = new ArrayList<>();
            public final LongSparseArray<TLRPC.TL_messages_messages> convertedByDialog = new LongSparseArray<>();
            public final LongSparseArray<ArrayList<MessageObject>> objectsByDialog = new LongSparseArray<>();

            private void put(TL_ephemeral.EphemeralMessage ephemeralMessage, TLRPC.Message convertedMessage, MessageObject messageObject) {
                final long dialogId = MessageObject.getDialogId(convertedMessage);

                messages.add(ephemeralMessage);

                TLRPC.TL_messages_messages res = convertedByDialog.get(dialogId);
                if (res == null) {
                    res = new TLRPC.TL_messages_messages();
                    convertedByDialog.put(dialogId, res);
                }
                res.messages.add(convertedMessage);

                ArrayList<MessageObject> messageObjects = objectsByDialog.get(dialogId);
                if (messageObjects == null) {
                    messageObjects = new ArrayList<>();
                    objectsByDialog.put(dialogId, messageObjects);
                }
                messageObjects.add(messageObject);
            }
        }

        public void apply(TL_update.TL_updateNewEphemeralMessage update, int currentAccount, AbstractMap<Long, TLRPC.User> usersDict, AbstractMap<Long, TLRPC.Chat> chatsDict) {
            final TL_ephemeral.EphemeralMessage ephemeralMessage = update.message;
            if (ephemeralMessage.anchor_msg_id != 0) {
                welcomeMessagesAnchor.put(ephemeralMessage);
                return;
            }

            final boolean isWelcome = ephemeralMessage.welcome;
            if (isWelcome) {
                final TLRPC.Message convertedMessage = convertEphemeralToFakeDefault(ephemeralMessage);
                final MessageObject messageObject = new MessageObject(currentAccount, convertedMessage, usersDict, chatsDict, true, true);
                welcomeMessagesToAdd.put(ephemeralMessage, convertedMessage, messageObject);
            } else {
                ephemeralMessagesToAdd.put(ephemeralMessage);
            }
        }

        public void apply(TL_update.TL_updateEditEphemeralMessage update, int currentAccount, AbstractMap<Long, TLRPC.User> usersDict, AbstractMap<Long, TLRPC.Chat> chatsDict) {
            final TL_ephemeral.EphemeralMessage ephemeralMessage = update.message;
            if (ephemeralMessage.anchor_msg_id != 0) {
                welcomeMessagesAnchor.put(ephemeralMessage);
                return;
            }

            final boolean isWelcome = ephemeralMessage.welcome;
            if (isWelcome) {
                final TLRPC.Message convertedMessage = convertEphemeralToFakeDefault(ephemeralMessage);
                final MessageObject messageObject = new MessageObject(currentAccount, convertedMessage, usersDict, chatsDict, true, true);
                convertedMessage.edit_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                convertedMessage.flags |= TLObject.FLAG_15;
                welcomeMessagesToEdit.put(ephemeralMessage, convertedMessage, messageObject);
            } else {
                ephemeralMessagesToEdit.put(ephemeralMessage);
            }
        }

        public void apply(TL_update.TL_updateDeleteEphemeralMessages update) {
            final long dialogId = DialogObject.getPeerDialogId(update.peer);
            final ArrayList<Integer> ids = update.ids;
        }
    }


    public static class WelcomeAnchorsState {
        private final LongSparseArray<SparseIntArray> state = new LongSparseArray<>();

        public void put(long dialogId, int messageId, int ephemeralMessageId) {
            SparseIntArray array = state.get(dialogId);
            if (array == null) {
                array = new SparseIntArray();
                state.put(dialogId, array);
            }
            array.put(messageId, ephemeralMessageId);
        }

        public void remove(long dialogId, int messageId, int ephemeralMessageId) {
            SparseIntArray array = state.get(dialogId);
            if (array == null) {
                return;
            }
            if (array.get(messageId, -1) != ephemeralMessageId) {
                return;
            }
            array.delete(messageId);
            if (array.size() == 0) {
                state.remove(dialogId);
            }
        }

        @Nullable
        public SparseIntArray getAnchorBindings(long dialogId) {
            return state.get(dialogId);
        }
    }




    /* Instance */

    private EphemeralMessagesHelper(int currentAccount) {
        super(currentAccount);
    }

    private static volatile EphemeralMessagesHelper[] Instance = new EphemeralMessagesHelper[UserConfig.MAX_ACCOUNT_COUNT];
    public static EphemeralMessagesHelper getInstance(final int num) {
        EphemeralMessagesHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (EphemeralMessagesHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new EphemeralMessagesHelper(num);
                }
            }
        }
        return localInstance;
    }
}
