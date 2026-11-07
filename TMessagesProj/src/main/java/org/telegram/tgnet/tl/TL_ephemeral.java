package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLMethod;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_ephemeral {
    public static abstract class WelcomeMessages extends TLObject {
        public long hash;
        public ArrayList<EphemeralMessage> messages = new ArrayList<>();

        public static WelcomeMessages TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(WelcomeMessages.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static WelcomeMessages fromConstructor(int constructor) {
            switch (constructor) {
                case TL_welcomeMessagesNotModified.constructor:
                    return new TL_welcomeMessagesNotModified();
                case TL_welcomeMessages.constructor:
                    return new TL_welcomeMessages();
                default:
                    return null;
            }
        }
    }

    public static class TL_welcomeMessagesNotModified extends WelcomeMessages {
        public static final int constructor = 0x59FFDB31;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_welcomeMessages extends WelcomeMessages {
        public static final int constructor = 0x104FC872;

        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            messages = Vector.deserialize(stream, EphemeralMessage::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, messages);
        }
    }



    /* Ephemeral Message */

    public static abstract class EphemeralMessage extends TLObject {
        public int flags;
        public boolean out;
        public boolean welcome;
        public boolean invert_media;
        public boolean noforwards;
        public int id;
        public TLRPC.Peer from_id;
        public TLRPC.Peer peer_id;
        public long receiver_id;
        public int top_msg_id;
        public int date;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities;
        public TLRPC.MessageMedia media;
        public TLRPC.ReplyMarkup reply_markup;
        public TLRPC.MessageReplyHeader reply_to;
        public TL_iv.RichMessage rich_message;
        public long chat_instance;
        public int anchor_msg_id;
        public long via_bot_id; // custom

        public static EphemeralMessage TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(EphemeralMessage.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static EphemeralMessage fromConstructor(int constructor) {
            switch (constructor) {
                case TL_ephemeralMessage.constructor:
                    return new TL_ephemeralMessage();
                case TL_ephemeralMessage_layer229_old.constructor:
                    return new TL_ephemeralMessage_layer229_old();
                case TL_ephemeralMessage_layer228.constructor:
                    return new TL_ephemeralMessage_layer228();
                default:
                    return null;
            }
        }
    }

    public static class TL_ephemeralMessage extends EphemeralMessage {
        public static final int constructor = 0xDD27BEE9;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_0);
            welcome = hasFlag(flags, FLAG_5);
            invert_media = hasFlag(flags, FLAG_7);
            noforwards = hasFlag(flags, FLAG_12);
            id = stream.readInt32(exception);
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_9)) {
                peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            receiver_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_1)) {
                top_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_2)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                rich_message = TL_iv.RichMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                chat_instance = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                anchor_msg_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, out);
            flags = setFlag(flags, FLAG_2, entities != null);
            flags = setFlag(flags, FLAG_3, media != null);
            flags = setFlag(flags, FLAG_4, reply_markup != null);
            flags = setFlag(flags, FLAG_5, welcome);
            flags = setFlag(flags, FLAG_6, reply_to != null);
            flags = setFlag(flags, FLAG_7, invert_media);
            flags = setFlag(flags, FLAG_8, rich_message != null);
            flags = setFlag(flags, FLAG_9, peer_id != null);
            flags = setFlag(flags, FLAG_12, noforwards);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            from_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_9)) {
                peer_id.serializeToStream(stream);
            }
            stream.writeInt64(receiver_id);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(top_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_2)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_3)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_8)) {
                rich_message.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt64(chat_instance);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt32(anchor_msg_id);
            }
        }
    }

    public static class TL_ephemeralMessage_layer229_old extends TL_ephemeralMessage {
        public static final int constructor = 0x8EF3E491;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_0);
            welcome = hasFlag(flags, FLAG_5);
            invert_media = hasFlag(flags, FLAG_7);
            id = stream.readInt32(exception);
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            receiver_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_1)) {
                top_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_2)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_8)) {
                rich_message = TL_iv.RichMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, out);
            flags = setFlag(flags, FLAG_2, entities != null);
            flags = setFlag(flags, FLAG_3, media != null);
            flags = setFlag(flags, FLAG_4, reply_markup != null);
            flags = setFlag(flags, FLAG_5, welcome);
            flags = setFlag(flags, FLAG_6, reply_to != null);
            flags = setFlag(flags, FLAG_7, invert_media);
            flags = setFlag(flags, FLAG_8, rich_message != null);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            from_id.serializeToStream(stream);
            peer_id.serializeToStream(stream);
            stream.writeInt64(receiver_id);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(top_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_2)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_3)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_8)) {
                rich_message.serializeToStream(stream);
            }
        }
    }

    public static class TL_ephemeralMessage_layer228 extends TL_ephemeralMessage {
        public static final int constructor = 0xD9C6DC1A;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_0);
            id = stream.readInt32(exception);
            from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            receiver_id = stream.readInt64(exception);
            if (hasFlag(flags, FLAG_1)) {
                top_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_2)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, out);
            flags = setFlag(flags, FLAG_2, entities != null);
            flags = setFlag(flags, FLAG_3, media != null);
            flags = setFlag(flags, FLAG_4, reply_markup != null);
            flags = setFlag(flags, FLAG_6, reply_to != null);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            from_id.serializeToStream(stream);
            peer_id.serializeToStream(stream);
            stream.writeInt64(receiver_id);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeInt32(top_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_2)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_3)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_to.serializeToStream(stream);
            }
        }
    }



    /* Methods */

    public static class TL_sendMessage extends TLMethod<TLRPC.Updates> {
        public static final int constructor = 0xba8d5f35;

        public int flags;
        public boolean invert_media;
        public boolean welcome;
        public boolean anchor;
        public TLRPC.InputPeer peer;
        public TLRPC.InputUser receiver_id;
        public long query_id;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities;
        public TLRPC.InputMedia media;
        public TLRPC.ReplyMarkup reply_markup;
        public TL_iv.TL_inputRichMessage rich_message;
        public long random_id;
        public TLRPC.InputReplyTo reply_to;

        public TLRPC.Updates deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, entities != null);
            flags = setFlag(flags, FLAG_2, media != null);
            flags = setFlag(flags, FLAG_3, reply_markup != null);
            flags = setFlag(flags, FLAG_4, rich_message != null);
            flags = setFlag(flags, FLAG_5, reply_to != null);
            flags = setFlag(flags, FLAG_6, invert_media);
            flags = setFlag(flags, FLAG_7, welcome);
            flags = setFlag(flags, FLAG_8, peer != null);
            flags = setFlag(flags, FLAG_9, anchor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_8)) {
                peer.serializeToStream(stream);
            }
            receiver_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeInt64(query_id);
            }
            stream.writeString(message);
            if (hasFlag(flags, FLAG_1)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_2)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                rich_message.serializeToStream(stream);
            }
            stream.writeInt64(random_id);
            if (hasFlag(flags, FLAG_5)) {
                reply_to.serializeToStream(stream);
            }
        }
    }

    public static class TL_editMessage extends TLRPC.TL_messages_editMessage {
        public static final int constructor = 0xcf9c725b;

        public int flags;
        public boolean invert_media;
        public boolean welcome;
        public TLRPC.InputUser receiver_id;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, message != null);
            flags = setFlag(flags, FLAG_1, entities != null);
            flags = setFlag(flags, FLAG_2, reply_markup != null);
            flags = setFlag(flags, FLAG_3, media != null);
            flags = setFlag(flags, FLAG_4, rich_message != null);
            flags = setFlag(flags, FLAG_5, invert_media);
            flags = setFlag(flags, FLAG_6, welcome);
            flags = setFlag(flags, FLAG_7, peer != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_7)) {
                peer.serializeToStream(stream);
            }
            receiver_id.serializeToStream(stream);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(message);
            }
            if (hasFlag(flags, FLAG_3)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_1)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_2)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_4)) {
                rich_message.serializeToStream(stream);
            }
        }
    }

    public static class TL_deleteMessage extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x92f6e797;

        public int flags;
        public TLRPC.InputPeer peer;
        public TLRPC.InputUser receiver_id;
        public int id;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, peer != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                peer.serializeToStream(stream);
            }
            receiver_id.serializeToStream(stream);
            stream.writeInt32(id);
        }
    }

    public static class TL_reportMessage extends TLMethod<TLRPC.ReportResult> {
        public static final int constructor = 0x8704F2BF;

        public TLRPC.InputPeer peer;
        public int id;
        public byte[] option;
        public String message;

        public TLRPC.ReportResult deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.ReportResult.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            stream.writeByteArray(option);
            stream.writeString(message);
        }
    }

    public static class TL_getCallbackAnswer extends TLMethod<TLRPC.TL_messages_botCallbackAnswer> {
        public static final int constructor = 0x3FA464C8;

        public int flags;
        public TLRPC.InputPeer peer;
        public int id;
        public byte[] data;

        public TLRPC.TL_messages_botCallbackAnswer deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_messages_botCallbackAnswer.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, data != null);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeByteArray(data);
            }
        }
    }

    public static class TL_deleteWelcomeMessage extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0xE882A9E1;

        public TLRPC.InputPeer peer;
        public int id;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
        }
    }

    public static class TL_deleteAllWelcomeMessages extends TLMethod<TLRPC.Bool> {
        public static final int constructor = 0x734F9721;

        public TLRPC.InputPeer peer;

        public TLRPC.Bool deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_getWelcomeMessages extends TLMethod<WelcomeMessages> {
        public static final int constructor = 0xDB9AC18D;

        public TLRPC.InputPeer peer;
        public long hash;

        public WelcomeMessages deserializeResponseT(InputSerializedData stream, int constructor, boolean exception) {
            return WelcomeMessages.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(hash);
        }
    }
}
