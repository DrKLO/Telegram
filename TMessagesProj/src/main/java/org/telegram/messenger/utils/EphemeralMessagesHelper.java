package org.telegram.messenger.utils;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.BaseController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;

import java.util.Collections;
import java.util.List;

public class EphemeralMessagesHelper extends BaseController {
    public static TLRPC.TL_message convertEphemeralToFakeDefault(TLRPC.EphemeralMessage ephemeralMessage) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.out = ephemeralMessage.out;
        message.id = MessageObject.ephemeralMessageIdPack(ephemeralMessage.id);

        if (ephemeralMessage.from_id != null) {
            message.from_id = ephemeralMessage.from_id;
            message.flags |= TLObject.FLAG_8;
        }
        message.peer_id = ephemeralMessage.peer_id;

        message.ephemeralReceiverBotId = ephemeralMessage.receiver_id;
        // ephemeralMessage.top_msg_id;

        message.date = ephemeralMessage.date;
        message.message = ephemeralMessage.message;

        if (ephemeralMessage.entities != null && !ephemeralMessage.entities.isEmpty()) {
            message.entities = ephemeralMessage.entities;
            message.flags |= TLObject.FLAG_7;
        }
        if (ephemeralMessage.media != null) {
            message.media = ephemeralMessage.media;
            message.flags |= TLObject.FLAG_9;
        }
        if (ephemeralMessage.reply_markup != null) {
            message.reply_markup = ephemeralMessage.reply_markup;
            message.flags |= TLObject.FLAG_6;
        }
        if (ephemeralMessage.reply_to != null) {
            if (ephemeralMessage.reply_to.reply_to_ephemeral) {
                message.reply_to = TLObject.deepCopy(ephemeralMessage.reply_to, TLRPC.MessageReplyHeader::TLdeserialize);
                if (message.reply_to.reply_to_msg_id != 0) {
                    message.reply_to.reply_to_msg_id = MessageObject.ephemeralMessageIdPack(message.reply_to.reply_to_msg_id);
                }
            } else {
                message.reply_to = ephemeralMessage.reply_to;
            }
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
        if (req instanceof TLRPC.TL_ephemeral_sendMessage) {
            return true;
        }

        if (req instanceof TLRPC.TL_messages_sendMessage) {
            final TLRPC.TL_messages_sendMessage request = (TLRPC.TL_messages_sendMessage) req;
            if (request.ephemeralReceiverBotId != 0) {
                TLRPC.TL_ephemeral_sendMessage newRequest = new TLRPC.TL_ephemeral_sendMessage();
                newRequest.peer = request.peer;
                newRequest.receiver_id = getMessagesController().getInputUser(request.ephemeralReceiverBotId);
                newRequest.query_id = 0; // ?
                newRequest.message = request.message;
                newRequest.entities = request.entities;
                newRequest.media = null;
                newRequest.reply_markup = request.reply_markup;
                newRequest.rich_message = request.rich_message;
                newRequest.random_id = request.random_id;
                newRequest.reply_to = applyReplyTo(request.reply_to);

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
                TLRPC.TL_ephemeral_sendMessage newRequest = new TLRPC.TL_ephemeral_sendMessage();
                newRequest.peer = request.peer;
                newRequest.receiver_id = getMessagesController().getInputUser(request.ephemeralReceiverBotId);
                newRequest.query_id = 0; // ?
                newRequest.message = request.message;
                newRequest.entities = request.entities;
                newRequest.media = request.media;
                newRequest.reply_markup = request.reply_markup;
                newRequest.rich_message = null;
                newRequest.random_id = request.random_id;
                newRequest.reply_to = applyReplyTo(request.reply_to);

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
