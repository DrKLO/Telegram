/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class TLRPC {
    public static class ChatPhoto extends TLObject {
        public FileLocation photo_small;
        public FileLocation photo_big;
    }

    public static class TL_chatPhotoEmpty extends ChatPhoto {
        public static int constructor = 0x37c1011c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_chatPhoto extends ChatPhoto {
        public static int constructor = 0x6153276a;


        public void readParams(AbsSerializedData stream) {
            photo_small = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            photo_big = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class BadMsgNotification extends TLObject {
        public long bad_msg_id;
        public int bad_msg_seqno;
        public int error_code;
        public long new_server_salt;
    }

    public static class TL_bad_msg_notification extends BadMsgNotification {
        public static int constructor = 0xa7eff811;


        public void readParams(AbsSerializedData stream) {
            bad_msg_id = stream.readInt64();
            bad_msg_seqno = stream.readInt32();
            error_code = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bad_msg_id);
            stream.writeInt32(bad_msg_seqno);
            stream.writeInt32(error_code);
        }
    }

    public static class TL_bad_server_salt extends BadMsgNotification {
        public static int constructor = 0xedab447b;


        public void readParams(AbsSerializedData stream) {
            bad_msg_id = stream.readInt64();
            bad_msg_seqno = stream.readInt32();
            error_code = stream.readInt32();
            new_server_salt = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bad_msg_id);
            stream.writeInt32(bad_msg_seqno);
            stream.writeInt32(error_code);
            stream.writeInt64(new_server_salt);
        }
    }

    public static class TL_error extends TLObject {
        public static int constructor = 0xc4b9f9bb;

        public int code;
        public String text;

        public void readParams(AbsSerializedData stream) {
            code = stream.readInt32();
            text = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(code);
            stream.writeString(text);
        }
    }

    public static class messages_SentEncryptedMessage extends TLObject {
        public int date;
        public EncryptedFile file;
    }

    public static class TL_messages_sentEncryptedMessage extends messages_SentEncryptedMessage {
        public static int constructor = 0x560f8935;


        public void readParams(AbsSerializedData stream) {
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
        }
    }

    public static class TL_messages_sentEncryptedFile extends messages_SentEncryptedMessage {
        public static int constructor = 0x9493ff32;


        public void readParams(AbsSerializedData stream) {
            date = stream.readInt32();
            file = (EncryptedFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            file.serializeToStream(stream);
        }
    }

    public static class TL_auth_checkedPhone extends TLObject {
        public static int constructor = 0xe300cc3b;

        public boolean phone_registered;
        public boolean phone_invited;

        public void readParams(AbsSerializedData stream) {
            phone_registered = stream.readBool();
            phone_invited = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(phone_registered);
            stream.writeBool(phone_invited);
        }
    }

    public static class TL_msgs_ack extends TLObject {
        public static int constructor = 0x62d6b459;

        public ArrayList<Long> msg_ids = new ArrayList<Long>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (Long msg_id : msg_ids) {
                stream.writeInt64(msg_id);
            }
        }
    }

    public static class TL_messages_chatFull extends TLObject {
        public static int constructor = 0xe5d7d19c;

        public TL_chatFull full_chat;
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            full_chat = (TL_chatFull)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            full_chat.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contactStatus extends TLObject {
        public static int constructor = 0xaa77b873;

        public int user_id;
        public int expires;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            expires = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(expires);
        }
    }

    public static class TL_auth_authorization extends TLObject {
        public static int constructor = 0xf6b673a4;

        public int expires;
        public User user;

        public void readParams(AbsSerializedData stream) {
            expires = stream.readInt32();
            user = (User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(expires);
            user.serializeToStream(stream);
        }
    }

    public static class messages_Messages extends TLObject {
        public ArrayList<Message> messages = new ArrayList<Message>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public int count;
    }

    public static class TL_messages_messages extends messages_Messages {
        public static int constructor = 0x8c718e87;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_messagesSlice extends messages_Messages {
        public static int constructor = 0xb446ae3;


        public void readParams(AbsSerializedData stream) {
            count = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class RpcDropAnswer extends TLObject {
        public long msg_id;
        public int seq_no;
        public int bytes;
    }

    public static class TL_rpc_answer_unknown extends RpcDropAnswer {
        public static int constructor = 0x5e2ad36e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_rpc_answer_dropped extends RpcDropAnswer {
        public static int constructor = 0xa43ad8b7;


        public void readParams(AbsSerializedData stream) {
            msg_id = stream.readInt64();
            seq_no = stream.readInt32();
            bytes = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt32(seq_no);
            stream.writeInt32(bytes);
        }
    }

    public static class TL_rpc_answer_dropped_running extends RpcDropAnswer {
        public static int constructor = 0xcd78e586;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_link extends TLObject {
        public static int constructor = 0xeccea3f5;

        public contacts_MyLink my_link;
        public contacts_ForeignLink foreign_link;
        public User user;

        public void readParams(AbsSerializedData stream) {
            my_link = (contacts_MyLink)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            foreign_link = (contacts_ForeignLink)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            user = (User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            my_link.serializeToStream(stream);
            foreign_link.serializeToStream(stream);
            user.serializeToStream(stream);
        }
    }

    public static class Peer extends TLObject {
        public int user_id;
        public int chat_id;
    }

    public static class TL_peerUser extends Peer {
        public static int constructor = 0x9db1bc6d;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_peerChat extends Peer {
        public static int constructor = 0xbad0e5bb;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class EncryptedFile extends TLObject {
        public long id;
        public long access_hash;
        public int size;
        public int dc_id;
        public int key_fingerprint;
    }

    public static class TL_encryptedFile extends EncryptedFile {
        public static int constructor = 0x4a70994c;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            size = stream.readInt32();
            dc_id = stream.readInt32();
            key_fingerprint = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(size);
            stream.writeInt32(dc_id);
            stream.writeInt32(key_fingerprint);
        }
    }

    public static class TL_encryptedFileEmpty extends EncryptedFile {
        public static int constructor = 0xc21f497e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class DestroySessionRes extends TLObject {
        public long session_id;
    }

    public static class TL_destroy_session_ok extends DestroySessionRes {
        public static int constructor = 0xe22045fc;


        public void readParams(AbsSerializedData stream) {
            session_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class TL_destroy_session_none extends DestroySessionRes {
        public static int constructor = 0x62d350c9;


        public void readParams(AbsSerializedData stream) {
            session_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class updates_Difference extends TLObject {
        public int date;
        public int seq;
        public ArrayList<Message> new_messages = new ArrayList<Message>();
        public ArrayList<EncryptedMessage> new_encrypted_messages = new ArrayList<EncryptedMessage>();
        public ArrayList<Update> other_updates = new ArrayList<Update>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public TL_updates_state intermediate_state;
        public TL_updates_state state;
    }

    public static class TL_updates_differenceEmpty extends updates_Difference {
        public static int constructor = 0x5d75a138;


        public void readParams(AbsSerializedData stream) {
            date = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updates_differenceSlice extends updates_Difference {
        public static int constructor = 0xa8fb1981;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                new_messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                new_encrypted_messages.add((EncryptedMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                other_updates.add((Update)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            intermediate_state = (TL_updates_state)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = new_messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                new_messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = new_encrypted_messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                new_encrypted_messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = other_updates.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                other_updates.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            intermediate_state.serializeToStream(stream);
        }
    }

    public static class TL_updates_difference extends updates_Difference {
        public static int constructor = 0xf49ca0;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                new_messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                new_encrypted_messages.add((EncryptedMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                other_updates.add((Update)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            state = (TL_updates_state)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = new_messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                new_messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = new_encrypted_messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                new_encrypted_messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = other_updates.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                other_updates.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            state.serializeToStream(stream);
        }
    }

    public static class GeoPoint extends TLObject {
        public double _long;
        public double lat;
    }

    public static class TL_geoPointEmpty extends GeoPoint {
        public static int constructor = 0x1117dd5f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_geoPoint extends GeoPoint {
        public static int constructor = 0x2049d70c;


        public void readParams(AbsSerializedData stream) {
            _long = stream.readDouble();
            lat = stream.readDouble();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(_long);
            stream.writeDouble(lat);
        }
    }

    public static class help_AppUpdate extends TLObject {
        public int id;
        public boolean critical;
        public String url;
        public String text;
    }

    public static class TL_help_appUpdate extends help_AppUpdate {
        public static int constructor = 0x8987f311;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            critical = stream.readBool();
            url = stream.readString();
            text = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeBool(critical);
            stream.writeString(url);
            stream.writeString(text);
        }
    }

    public static class TL_help_noAppUpdate extends help_AppUpdate {
        public static int constructor = 0xc45a6536;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageEmpty extends Message {
        public static int constructor = 0x83e5de54;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_messageService extends Message {
        public static int constructor = 0x9f8d60bb;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            from_id = stream.readInt32();
            to_id = (Peer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            out = stream.readBool();
            unread = stream.readBool();
            date = stream.readInt32();
            action = (MessageAction)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool(out);
            stream.writeBool(unread);
            stream.writeInt32(date);
            action.serializeToStream(stream);
        }
    }

    public static class TL_inputPhoneContact extends TLObject {
        public static int constructor = 0xf392b7f4;

        public long client_id;
        public String phone;
        public String first_name;
        public String last_name;

        public void readParams(AbsSerializedData stream) {
            client_id = stream.readInt64();
            phone = stream.readString();
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(client_id);
            stream.writeString(phone);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_invokeAfterMsg extends TLObject {
        public static int constructor = 0xcb9f372d;

        public long msg_id;
        public TLObject query;

        public void readParams(AbsSerializedData stream) {
            msg_id = stream.readInt64();
            query = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            query.serializeToStream(stream);
        }
    }

    public static class MessageMedia extends TLObject {
        public Video video;
        public Photo photo;
        public Document document;
        public GeoPoint geo;
        public Audio audio;
        public String phone_number;
        public String first_name;
        public String last_name;
        public int user_id;
        public byte[] bytes;
    }

    public static class TL_messageMediaVideo extends MessageMedia {
        public static int constructor = 0xa2d24290;


        public void readParams(AbsSerializedData stream) {
            video = (Video)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            video.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaPhoto extends MessageMedia {
        public static int constructor = 0xc8c45a2a;


        public void readParams(AbsSerializedData stream) {
            photo = (Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaDocument extends MessageMedia {
        public static int constructor = 0x2fda2204;


        public void readParams(AbsSerializedData stream) {
            document = (Document)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            document.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaGeo extends MessageMedia {
        public static int constructor = 0x56e0d474;


        public void readParams(AbsSerializedData stream) {
            geo = (GeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaEmpty extends MessageMedia {
        public static int constructor = 0x3ded6320;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageMediaAudio extends MessageMedia {
        public static int constructor = 0xc6b68300;


        public void readParams(AbsSerializedData stream) {
            audio = (Audio)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            audio.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaContact extends MessageMedia {
        public static int constructor = 0x5e7d2f39;


        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            first_name = stream.readString();
            last_name = stream.readString();
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageMediaUnsupported extends MessageMedia {
        public static int constructor = 0x29632a36;


        public void readParams(AbsSerializedData stream) {
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_auth_sentCode extends TLObject {
        public static int constructor = 0xefed51d9;

        public boolean phone_registered;
        public String phone_code_hash;
        public int send_call_timeout;
        public boolean is_password;

        public void readParams(AbsSerializedData stream) {
            phone_registered = stream.readBool();
            phone_code_hash = stream.readString();
            send_call_timeout = stream.readInt32();
            is_password = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(phone_registered);
            stream.writeString(phone_code_hash);
            stream.writeInt32(send_call_timeout);
            stream.writeBool(is_password);
        }
    }

    public static class PeerNotifySettings extends TLObject {
        public int mute_until;
        public String sound;
        public boolean show_previews;
        public int events_mask;
    }

    public static class TL_peerNotifySettingsEmpty extends PeerNotifySettings {
        public static int constructor = 0x70a68512;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_peerNotifySettings extends PeerNotifySettings {
        public static int constructor = 0x8d5e11ee;


        public void readParams(AbsSerializedData stream) {
            mute_until = stream.readInt32();
            sound = stream.readString();
            show_previews = stream.readBool();
            events_mask = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(mute_until);
            stream.writeString(sound);
            stream.writeBool(show_previews);
            stream.writeInt32(events_mask);
        }
    }

    public static class TL_msg_resend_req extends TLObject {
        public static int constructor = 0x7d861a08;

        public ArrayList<Long> msg_ids = new ArrayList<Long>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (Long msg_id : msg_ids) {
                stream.writeInt64(msg_id);
            }
        }
    }

    public static class TL_http_wait extends TLObject {
        public static int constructor = 0x9299359f;

        public int max_delay;
        public int wait_after;
        public int max_wait;

        public void readParams(AbsSerializedData stream) {
            max_delay = stream.readInt32();
            wait_after = stream.readInt32();
            max_wait = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_delay);
            stream.writeInt32(wait_after);
            stream.writeInt32(max_wait);
        }
    }

    public static class contacts_Blocked extends TLObject {
        public ArrayList<TL_contactBlocked> blocked = new ArrayList<TL_contactBlocked>();
        public ArrayList<User> users = new ArrayList<User>();
        public int count;
    }

    public static class TL_contacts_blocked extends contacts_Blocked {
        public static int constructor = 0x1c138d15;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                blocked.add((TL_contactBlocked)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = blocked.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                blocked.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_blockedSlice extends contacts_Blocked {
        public static int constructor = 0x900802a1;


        public void readParams(AbsSerializedData stream) {
            count = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                blocked.add((TL_contactBlocked)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = blocked.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                blocked.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class InputGeoPoint extends TLObject {
        public double lat;
        public double _long;
    }

    public static class TL_inputGeoPoint extends InputGeoPoint {
        public static int constructor = 0xf3b7acc9;


        public void readParams(AbsSerializedData stream) {
            lat = stream.readDouble();
            _long = stream.readDouble();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(lat);
            stream.writeDouble(_long);
        }
    }

    public static class TL_inputGeoPointEmpty extends InputGeoPoint {
        public static int constructor = 0xe4c123d6;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_help_inviteText extends TLObject {
        public static int constructor = 0x18cb9f78;

        public String message;

        public void readParams(AbsSerializedData stream) {
            message = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(message);
        }
    }

    public static class messages_DhConfig extends TLObject {
        public byte[] random;
        public int g;
        public byte[] p;
        public int version;
    }

    public static class TL_messages_dhConfigNotModified extends messages_DhConfig {
        public static int constructor = 0xc0e24635;


        public void readParams(AbsSerializedData stream) {
            random = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(random);
        }
    }

    public static class TL_messages_dhConfig extends messages_DhConfig {
        public static int constructor = 0x2c221edd;


        public void readParams(AbsSerializedData stream) {
            g = stream.readInt32();
            p = stream.readByteArray();
            version = stream.readInt32();
            random = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(g);
            stream.writeByteArray(p);
            stream.writeInt32(version);
            stream.writeByteArray(random);
        }
    }

    public static class TL_audioEmpty extends Audio {
        public static int constructor = 0x586988d8;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_audio extends Audio {
        public static int constructor = 0x427425e7;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            duration = stream.readInt32();
            size = stream.readInt32();
            dc_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            stream.writeInt32(dc_id);
        }
    }

    public static class TL_destroy_sessions_res extends TLObject {
        public static int constructor = 0xfb95abcd;

        public ArrayList<DestroySessionRes> destroy_results = new ArrayList<DestroySessionRes>();

        public void readParams(AbsSerializedData stream) {
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                destroy_results.add((DestroySessionRes)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            int count = destroy_results.size();
            stream.writeInt32(count);
            for (DestroySessionRes destroy_result : destroy_results) {
                destroy_result.serializeToStream(stream);
            }
        }
    }

    public static class contacts_Contacts extends TLObject {
        public ArrayList<TL_contact> contacts = new ArrayList<TL_contact>();
        public ArrayList<User> users = new ArrayList<User>();
    }

    public static class TL_contacts_contacts extends contacts_Contacts {
        public static int constructor = 0x6f8b8cb2;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                contacts.add((TL_contact)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = contacts.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                contacts.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_contactsNotModified extends contacts_Contacts {
        public static int constructor = 0xb74ba9d2;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class photos_Photos extends TLObject {
        public ArrayList<Photo> photos = new ArrayList<Photo>();
        public ArrayList<User> users = new ArrayList<User>();
        public int count;
    }

    public static class TL_photos_photos extends photos_Photos {
        public static int constructor = 0x8dca6aa5;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                photos.add((Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = photos.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                photos.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_photos_photosSlice extends photos_Photos {
        public static int constructor = 0x15051f54;


        public void readParams(AbsSerializedData stream) {
            count = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                photos.add((Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = photos.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                photos.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_chatFull extends TLObject {
        public static int constructor = 0x630e61be;

        public int id;
        public ChatParticipants participants;
        public Photo chat_photo;
        public PeerNotifySettings notify_settings;

        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            participants = (ChatParticipants)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            chat_photo = (Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            notify_settings = (PeerNotifySettings)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            participants.serializeToStream(stream);
            chat_photo.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
        }
    }

    public static class TL_msgs_all_info extends TLObject {
        public static int constructor = 0x8cc0d131;

        public ArrayList<Long> msg_ids = new ArrayList<Long>();
        public String info;

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64());
            }
            info = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (Long msg_id : msg_ids) {
                stream.writeInt64(msg_id);
            }
            stream.writeString(info);
        }
    }

    public static class TL_inputPeerNotifySettings extends TLObject {
        public static int constructor = 0x46a2ce98;

        public int mute_until;
        public String sound;
        public boolean show_previews;
        public int events_mask;

        public void readParams(AbsSerializedData stream) {
            mute_until = stream.readInt32();
            sound = stream.readString();
            show_previews = stream.readBool();
            events_mask = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(mute_until);
            stream.writeString(sound);
            stream.writeBool(show_previews);
            stream.writeInt32(events_mask);
        }
    }

    public static class TL_null extends TLObject {
        public static int constructor = 0x56730bcc;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class InputUser extends TLObject {
        public int user_id;
        public long access_hash;
    }

    public static class TL_inputUserSelf extends InputUser {
        public static int constructor = 0xf7c1b13f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputUserForeign extends InputUser {
        public static int constructor = 0x655e74ff;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputUserEmpty extends InputUser {
        public static int constructor = 0xb98886cf;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputUserContact extends InputUser {
        public static int constructor = 0x86e94f65;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_p_q_inner_data extends TLObject {
        public static int constructor = 0x83c95aec;

        public byte[] pq;
        public byte[] p;
        public byte[] q;
        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] new_nonce;

        public void readParams(AbsSerializedData stream) {
            pq = stream.readByteArray();
            p = stream.readByteArray();
            q = stream.readByteArray();
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            new_nonce = stream.readData(32);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(pq);
            stream.writeByteArray(p);
            stream.writeByteArray(q);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce);
        }
    }

    public static class TL_msgs_state_req extends TLObject {
        public static int constructor = 0xda69fb52;

        public ArrayList<Long> msg_ids = new ArrayList<Long>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (Long msg_id : msg_ids) {
                stream.writeInt64(msg_id);
            }
        }
    }

    public static class Bool extends TLObject {
    }

    public static class TL_boolTrue extends Bool {
        public static int constructor = 0x997275b5;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_boolFalse extends Bool {
        public static int constructor = 0xbc799737;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_exportedAuthorization extends TLObject {
        public static int constructor = 0xdf969c2d;

        public int id;
        public byte[] bytes;

        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeByteArray(bytes);
        }
    }

    public static class messages_StatedMessages extends TLObject {
        public ArrayList<Message> messages = new ArrayList<Message>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public ArrayList<TL_contacts_link> links = new ArrayList<TL_contacts_link>();
        public int pts;
        public int seq;
    }

    public static class TL_messages_statedMessagesLinks extends messages_StatedMessages {
        public static int constructor = 0x3e74f5c6;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                links.add((TL_contacts_link)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            pts = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = links.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                links.get(a).serializeToStream(stream);
            }
            stream.writeInt32(pts);
            stream.writeInt32(seq);
        }
    }

    public static class TL_messages_statedMessages extends messages_StatedMessages {
        public static int constructor = 0x969478bb;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            pts = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(pts);
            stream.writeInt32(seq);
        }
    }

    public static class InputNotifyPeer extends TLObject {
    }

    public static class TL_inputNotifyChats extends InputNotifyPeer {
        public static int constructor = 0x4a95e84e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputNotifyPeer extends InputNotifyPeer {
        public static int constructor = 0xb8bc5b0c;

        public InputPeer peer;

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_inputNotifyUsers extends InputNotifyPeer {
        public static int constructor = 0x193b4417;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputNotifyGeoChatPeer extends InputNotifyPeer {
        public static int constructor = 0x4d8ddec8;

        public TL_inputGeoChat peer;

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_inputNotifyAll extends InputNotifyPeer {
        public static int constructor = 0xa429b886;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class InputFileLocation extends TLObject {
        public long id;
        public long access_hash;
        public long volume_id;
        public int local_id;
        public long secret;
    }

    public static class TL_inputAudioFileLocation extends InputFileLocation {
        public static int constructor = 0x74dc404d;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputEncryptedFileLocation extends InputFileLocation {
        public static int constructor = 0xf5235d55;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputVideoFileLocation extends InputFileLocation {
        public static int constructor = 0x3d0364ec;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputDocumentFileLocation extends InputFileLocation {
        public static int constructor = 0x4e45abe9;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputFileLocation extends InputFileLocation {
        public static int constructor = 0x14637196;


        public void readParams(AbsSerializedData stream) {
            volume_id = stream.readInt64();
            local_id = stream.readInt32();
            secret = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class TL_photos_photo extends TLObject {
        public static int constructor = 0x20212ca8;

        public Photo photo;
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            photo = (Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (User user : users) {
                user.serializeToStream(stream);
            }
        }
    }

    public static class User extends TLObject {
        public int id;
        public String first_name;
        public String last_name;
        public long access_hash;
        public String phone;
        public UserProfilePhoto photo;
        public UserStatus status;
        public boolean inactive;
    }

    public static class TL_userContact extends User {
        public static int constructor = 0xf2fb8319;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
            access_hash = stream.readInt64();
            phone = stream.readString();
            photo = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            status = (UserStatus)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt64(access_hash);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userRequest extends User {
        public static int constructor = 0x22e8ceb0;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
            access_hash = stream.readInt64();
            phone = stream.readString();
            photo = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            status = (UserStatus)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt64(access_hash);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userForeign extends User {
        public static int constructor = 0x5214c89d;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
            access_hash = stream.readInt64();
            photo = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            status = (UserStatus)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt64(access_hash);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userDeleted extends User {
        public static int constructor = 0xb29ad7cc;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_userSelf extends User {
        public static int constructor = 0x720535ec;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
            phone = stream.readString();
            photo = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            status = (UserStatus)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            inactive = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
            stream.writeBool(inactive);
        }
    }

    public static class GeoChatMessage extends TLObject {
        public int chat_id;
        public int id;
        public int from_id;
        public int date;
        public String message;
        public MessageMedia media;
        public MessageAction action;
    }

    public static class TL_geoChatMessage extends GeoChatMessage {
        public static int constructor = 0x4505f8e1;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            id = stream.readInt32();
            from_id = stream.readInt32();
            date = stream.readInt32();
            message = stream.readString();
            media = (MessageMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
        }
    }

    public static class TL_geoChatMessageService extends GeoChatMessage {
        public static int constructor = 0xd34fa24e;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            id = stream.readInt32();
            from_id = stream.readInt32();
            date = stream.readInt32();
            action = (MessageAction)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            stream.writeInt32(date);
            action.serializeToStream(stream);
        }
    }

    public static class TL_geoChatMessageEmpty extends GeoChatMessage {
        public static int constructor = 0x60311a9b;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(id);
        }
    }

    public static class TL_pong extends TLObject {
        public static int constructor = 0x347773c5;

        public long msg_id;
        public long ping_id;

        public void readParams(AbsSerializedData stream) {
            msg_id = stream.readInt64();
            ping_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt64(ping_id);
        }
    }

    public static class TL_messageActionChatEditPhoto extends MessageAction {
        public static int constructor = 0x7fcb13a8;


        public void readParams(AbsSerializedData stream) {
            photo = (Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messageActionChatDeleteUser extends MessageAction {
        public static int constructor = 0xb2ae9b0c;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageActionChatDeletePhoto extends MessageAction {
        public static int constructor = 0x95e3fbef;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionChatAddUser extends MessageAction {
        public static int constructor = 0x5e3cfc4b;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageActionChatCreate extends MessageAction {
        public static int constructor = 0xa6638b9a;


        public void readParams(AbsSerializedData stream) {
            title = stream.readString();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (Integer user : users) {
                stream.writeInt32(user);
            }
        }
    }

    public static class TL_messageActionEmpty extends MessageAction {
        public static int constructor = 0xb6aef7b0;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionChatEditTitle extends MessageAction {
        public static int constructor = 0xb5a1ce5a;


        public void readParams(AbsSerializedData stream) {
            title = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
        }
    }

    public static class TL_messageActionGeoChatCreate extends MessageAction {
        public static int constructor = 0x6f038ebc;


        public void readParams(AbsSerializedData stream) {
            title = stream.readString();
            address = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeString(address);
        }
    }

    public static class TL_messageActionGeoChatCheckin extends MessageAction {
        public static int constructor = 0xc7d53de;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class contacts_ForeignLink extends TLObject {
        public boolean has_phone;
    }

    public static class TL_contacts_foreignLinkMutual extends contacts_ForeignLink {
        public static int constructor = 0x1bea8ce1;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_foreignLinkUnknown extends contacts_ForeignLink {
        public static int constructor = 0x133421f8;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_foreignLinkRequested extends contacts_ForeignLink {
        public static int constructor = 0xa7801f47;


        public void readParams(AbsSerializedData stream) {
            has_phone = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(has_phone);
        }
    }

    public static class Set_client_DH_params_answer extends TLObject {
        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] new_nonce_hash2;
        public byte[] new_nonce_hash3;
        public byte[] new_nonce_hash1;
    }

    public static class TL_dh_gen_retry extends Set_client_DH_params_answer {
        public static int constructor = 0x46dc1fb9;


        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            new_nonce_hash2 = stream.readData(16);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce_hash2);
        }
    }

    public static class TL_dh_gen_fail extends Set_client_DH_params_answer {
        public static int constructor = 0xa69dae02;


        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            new_nonce_hash3 = stream.readData(16);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce_hash3);
        }
    }

    public static class TL_dh_gen_ok extends Set_client_DH_params_answer {
        public static int constructor = 0x3bcbf734;


        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            new_nonce_hash1 = stream.readData(16);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce_hash1);
        }
    }

    public static class PeerNotifyEvents extends TLObject {
    }

    public static class TL_peerNotifyEventsEmpty extends PeerNotifyEvents {
        public static int constructor = 0xadd53cb3;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_peerNotifyEventsAll extends PeerNotifyEvents {
        public static int constructor = 0x6d1ded88;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_chatLocated extends TLObject {
        public static int constructor = 0x3631cf4c;

        public int chat_id;
        public int distance;

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            distance = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(distance);
        }
    }

    public static class DecryptedMessage extends TLObject {
        public long random_id;
        public byte[] random_bytes;
        public TL_decryptedMessageActionSetMessageTTL action;
        public String message;
        public DecryptedMessageMedia media;
    }

    public static class TL_decryptedMessageService extends DecryptedMessage {
        public static int constructor = 0xaa48327d;


        public void readParams(AbsSerializedData stream) {
            random_id = stream.readInt64();
            random_bytes = stream.readByteArray();
            action = (TL_decryptedMessageActionSetMessageTTL)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeByteArray(random_bytes);
            action.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessage extends DecryptedMessage {
        public static int constructor = 0x1f814f1f;


        public void readParams(AbsSerializedData stream) {
            random_id = stream.readInt64();
            random_bytes = stream.readByteArray();
            message = stream.readString();
            media = (DecryptedMessageMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeByteArray(random_bytes);
            stream.writeString(message);
            media.serializeToStream(stream);
        }
    }

    public static class InputPeerNotifyEvents extends TLObject {
    }

    public static class TL_inputPeerNotifyEventsAll extends InputPeerNotifyEvents {
        public static int constructor = 0xe86a2c74;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPeerNotifyEventsEmpty extends InputPeerNotifyEvents {
        public static int constructor = 0xf03064d8;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_client_DH_inner_data extends TLObject {
        public static int constructor = 0x6643b654;

        public byte[] nonce;
        public byte[] server_nonce;
        public long retry_id;
        public byte[] g_b;

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            retry_id = stream.readInt64();
            g_b = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeInt64(retry_id);
            stream.writeByteArray(g_b);
        }
    }

    public static class TL_video extends Video {
        public static int constructor = 0x5a04a49f;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            caption = stream.readString();
            duration = stream.readInt32();
            size = stream.readInt32();
            thumb = (PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            dc_id = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(caption);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_videoEmpty extends Video {
        public static int constructor = 0xc10658a8;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_contactBlocked extends TLObject {
        public static int constructor = 0x561bc879;

        public int user_id;
        public int date;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
        }
    }

    public static class InputDocument extends TLObject {
        public long id;
        public long access_hash;
    }

    public static class TL_inputDocumentEmpty extends InputDocument {
        public static int constructor = 0x72f0eaae;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputDocument extends InputDocument {
        public static int constructor = 0x18798952;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputAppEvent extends TLObject {
        public static int constructor = 0x770656a8;

        public double time;
        public String type;
        public long peer;
        public String data;

        public void readParams(AbsSerializedData stream) {
            time = stream.readDouble();
            type = stream.readString();
            peer = stream.readInt64();
            data = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(time);
            stream.writeString(type);
            stream.writeInt64(peer);
            stream.writeString(data);
        }
    }

    public static class TL_messages_affectedHistory extends TLObject {
        public static int constructor = 0xb7de36f2;

        public int pts;
        public int seq;
        public int offset;

        public void readParams(AbsSerializedData stream) {
            pts = stream.readInt32();
            seq = stream.readInt32();
            offset = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pts);
            stream.writeInt32(seq);
            stream.writeInt32(offset);
        }
    }

    public static class TL_documentEmpty extends Document {
        public static int constructor = 0x36f8c871;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_document extends Document {
        public static int constructor = 0x9efc6326;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            file_name = stream.readString();
            mime_type = stream.readString();
            size = stream.readInt32();
            thumb = (PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            dc_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(file_name);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
        }
    }

    public static class InputMedia extends TLObject {
        public String phone_number;
        public String first_name;
        public String last_name;
        public InputFile file;
        public InputFile thumb;
        public String file_name;
        public String mime_type;
        public InputGeoPoint geo_point;
        public int duration;
        public int w;
        public int h;
    }

    public static class TL_inputMediaContact extends InputMedia {
        public static int constructor = 0xa6e45987;


        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_inputMediaUploadedThumbDocument extends InputMedia {
        public static int constructor = 0x3e46de5d;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            thumb = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            file_name = stream.readString();
            mime_type = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            thumb.serializeToStream(stream);
            stream.writeString(file_name);
            stream.writeString(mime_type);
        }
    }

    public static class TL_inputMediaAudio extends InputMedia {
        public static int constructor = 0x89938781;

        public InputAudio id;

        public void readParams(AbsSerializedData stream) {
            id = (InputAudio)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaDocument extends InputMedia {
        public static int constructor = 0xd184e841;

        public InputDocument id;

        public void readParams(AbsSerializedData stream) {
            id = (InputDocument)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaVideo extends InputMedia {
        public static int constructor = 0x7f023ae6;

        public InputVideo id;

        public void readParams(AbsSerializedData stream) {
            id = (InputVideo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaGeoPoint extends InputMedia {
        public static int constructor = 0xf9c44144;


        public void readParams(AbsSerializedData stream) {
            geo_point = (InputGeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo_point.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaEmpty extends InputMedia {
        public static int constructor = 0x9664f57f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMediaUploadedThumbVideo extends InputMedia {
        public static int constructor = 0xe628a145;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            thumb = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            duration = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            thumb.serializeToStream(stream);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_inputMediaUploadedPhoto extends InputMedia {
        public static int constructor = 0x2dc53a7d;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaUploadedAudio extends InputMedia {
        public static int constructor = 0x61a6d436;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            duration = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeInt32(duration);
        }
    }

    public static class TL_inputMediaUploadedVideo extends InputMedia {
        public static int constructor = 0x4847d92a;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            duration = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_inputMediaUploadedDocument extends InputMedia {
        public static int constructor = 0x34e794bd;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            file_name = stream.readString();
            mime_type = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(file_name);
            stream.writeString(mime_type);
        }
    }

    public static class TL_inputMediaPhoto extends InputMedia {
        public static int constructor = 0x8f2ab2ec;

        public InputPhoto id;

        public void readParams(AbsSerializedData stream) {
            id = (InputPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class geochats_Messages extends TLObject {
        public int count;
        public ArrayList<GeoChatMessage> messages = new ArrayList<GeoChatMessage>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
    }

    public static class TL_geochats_messagesSlice extends geochats_Messages {
        public static int constructor = 0xbc5863e8;


        public void readParams(AbsSerializedData stream) {
            count = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((GeoChatMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_geochats_messages extends geochats_Messages {
        public static int constructor = 0xd1526db1;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((GeoChatMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class messages_SentMessage extends TLObject {
        public int id;
        public int date;
        public int pts;
        public int seq;
        public ArrayList<TL_contacts_link> links = new ArrayList<TL_contacts_link>();
    }

    public static class TL_messages_sentMessage extends messages_SentMessage {
        public static int constructor = 0xd1f4d35c;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            date = stream.readInt32();
            pts = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(date);
            stream.writeInt32(pts);
            stream.writeInt32(seq);
        }
    }

    public static class TL_messages_sentMessageLink extends messages_SentMessage {
        public static int constructor = 0xe9db4a3f;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            date = stream.readInt32();
            pts = stream.readInt32();
            seq = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                links.add((TL_contacts_link)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(date);
            stream.writeInt32(pts);
            stream.writeInt32(seq);
            stream.writeInt32(0x1cb5c415);
            int count = links.size();
            stream.writeInt32(count);
            for (TL_contacts_link link : links) {
                link.serializeToStream(stream);
            }
        }
    }

    public static class EncryptedMessage extends TLObject {
        public long random_id;
        public int chat_id;
        public int date;
        public byte[] bytes;
        public EncryptedFile file;
    }

    public static class TL_encryptedMessageService extends EncryptedMessage {
        public static int constructor = 0x23734b06;


        public void readParams(AbsSerializedData stream) {
            random_id = stream.readInt64();
            chat_id = stream.readInt32();
            date = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeInt32(chat_id);
            stream.writeInt32(date);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_encryptedMessage extends EncryptedMessage {
        public static int constructor = 0xed18c118;


        public void readParams(AbsSerializedData stream) {
            random_id = stream.readInt64();
            chat_id = stream.readInt32();
            date = stream.readInt32();
            bytes = stream.readByteArray();
            file = (EncryptedFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeInt32(chat_id);
            stream.writeInt32(date);
            stream.writeByteArray(bytes);
            file.serializeToStream(stream);
        }
    }

    public static class TL_contactSuggested extends TLObject {
        public static int constructor = 0x3de191a1;

        public int user_id;
        public int mutual_contacts;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            mutual_contacts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(mutual_contacts);
        }
    }

    public static class Server_DH_Params extends TLObject {
        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] new_nonce_hash;
        public byte[] encrypted_answer;
    }

    public static class TL_server_DH_params_fail extends Server_DH_Params {
        public static int constructor = 0x79cb045d;


        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            new_nonce_hash = stream.readData(16);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce_hash);
        }
    }

    public static class TL_server_DH_params_ok extends Server_DH_Params {
        public static int constructor = 0xd0e8075c;


        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            encrypted_answer = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteArray(encrypted_answer);
        }
    }

    public static class TL_msg_copy extends TLObject {
        public static int constructor = 0xe06046b2;

        public TL_protoMessage orig_message;

        public void readParams(AbsSerializedData stream) {
            orig_message = (TL_protoMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            orig_message.serializeToStream(stream);
        }
    }

    public static class TL_contacts_importedContacts extends TLObject {
        public static int constructor = 0xd1cd0a4c;

        public ArrayList<TL_importedContact> imported = new ArrayList<TL_importedContact>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                imported.add((TL_importedContact)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = imported.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                imported.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_futureSalt extends TLObject {
        public static int constructor = 0x0949d9dc;

        public int valid_since;
        public int valid_until;
        public long salt;

        public void readParams(AbsSerializedData stream) {
            valid_since = stream.readInt32();
            valid_until = stream.readInt32();
            salt = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(valid_since);
            stream.writeInt32(valid_until);
            stream.writeInt64(salt);
        }
    }

    public static class Update extends TLObject {
        public int chat_id;
        public int max_date;
        public int date;
        public int user_id;
        public contacts_MyLink my_link;
        public contacts_ForeignLink foreign_link;
        public ArrayList<Integer> messages = new ArrayList<Integer>();
        public int pts;
        public int version;
        public String first_name;
        public String last_name;
        public int qts;
        public int id;
        public long random_id;
        public ArrayList<TL_dcOption> dc_options = new ArrayList<TL_dcOption>();
        public ChatParticipants participants;
        public EncryptedChat chat;
        public long auth_key_id;
        public String device;
        public String location;
        public UserProfilePhoto photo;
        public boolean previous;
        public int inviter_id;
        public UserStatus status;
    }

    public static class TL_updateEncryptedMessagesRead extends Update {
        public static int constructor = 0x38fe25b7;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            max_date = stream.readInt32();
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(max_date);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateContactLink extends Update {
        public static int constructor = 0x51a48a9a;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            my_link = (contacts_MyLink)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            foreign_link = (contacts_ForeignLink)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            my_link.serializeToStream(stream);
            foreign_link.serializeToStream(stream);
        }
    }

    public static class TL_updateReadMessages extends Update {
        public static int constructor = 0xc6649e31;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add(stream.readInt32());
            }
            pts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (Integer message : messages) {
                stream.writeInt32(message);
            }
            stream.writeInt32(pts);
        }
    }

    public static class TL_updateChatParticipantDelete extends Update {
        public static int constructor = 0x6e5f8c22;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            user_id = stream.readInt32();
            version = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(user_id);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateRestoreMessages extends Update {
        public static int constructor = 0xd15de04d;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add(stream.readInt32());
            }
            pts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (Integer message : messages) {
                stream.writeInt32(message);
            }
            stream.writeInt32(pts);
        }
    }

    public static class TL_updateUserTyping extends Update {
        public static int constructor = 0x6baa8508;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_updateChatUserTyping extends Update {
        public static int constructor = 0x3c46cfe6;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_updateUserName extends Update {
        public static int constructor = 0xda22d9ad;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_updateNewEncryptedMessage extends Update {
        public static int constructor = 0x12bcbd9a;

        public EncryptedMessage message;

        public void readParams(AbsSerializedData stream) {
            message = (EncryptedMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            qts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateNewMessage extends Update {
        public static int constructor = 0x13abdb3;

        public Message message;

        public void readParams(AbsSerializedData stream) {
            message = (Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            pts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
        }
    }

    public static class TL_updateMessageID extends Update {
        public static int constructor = 0x4e90bfd6;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_updateDeleteMessages extends Update {
        public static int constructor = 0xa92bfe26;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add(stream.readInt32());
            }
            pts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (Integer message : messages) {
                stream.writeInt32(message);
            }
            stream.writeInt32(pts);
        }
    }

    public static class TL_updateEncryptedChatTyping extends Update {
        public static int constructor = 0x1710f156;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_updateDcOptions extends Update {
        public static int constructor = 0x8e5e9873;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                dc_options.add((TL_dcOption)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = dc_options.size();
            stream.writeInt32(count);
            for (TL_dcOption dc_option : dc_options) {
                dc_option.serializeToStream(stream);
            }
        }
    }

    public static class TL_updateChatParticipants extends Update {
        public static int constructor = 0x7761198;


        public void readParams(AbsSerializedData stream) {
            participants = (ChatParticipants)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            participants.serializeToStream(stream);
        }
    }

    public static class TL_updateEncryption extends Update {
        public static int constructor = 0xb4a2e88d;


        public void readParams(AbsSerializedData stream) {
            chat = (EncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            chat.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateActivation extends Update {
        public static int constructor = 0x6f690963;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_updateNewAuthorization extends Update {
        public static int constructor = 0x8f06529a;


        public void readParams(AbsSerializedData stream) {
            auth_key_id = stream.readInt64();
            date = stream.readInt32();
            device = stream.readString();
            location = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(auth_key_id);
            stream.writeInt32(date);
            stream.writeString(device);
            stream.writeString(location);
        }
    }

    public static class TL_updateNewGeoChatMessage extends Update {
        public static int constructor = 0x5a68e3f7;

        public GeoChatMessage message;

        public void readParams(AbsSerializedData stream) {
            message = (GeoChatMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateUserPhoto extends Update {
        public static int constructor = 0x95313b0c;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            date = stream.readInt32();
            photo = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            previous = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            photo.serializeToStream(stream);
            stream.writeBool(previous);
        }
    }

    public static class TL_updateContactRegistered extends Update {
        public static int constructor = 0x2575bbb9;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateChatParticipantAdd extends Update {
        public static int constructor = 0x3a0eeb22;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            user_id = stream.readInt32();
            inviter_id = stream.readInt32();
            version = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(user_id);
            stream.writeInt32(inviter_id);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateUserStatus extends Update {
        public static int constructor = 0x1bfbd823;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            status = (UserStatus)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            status.serializeToStream(stream);
        }
    }

    public static class TL_contacts_suggested extends TLObject {
        public static int constructor = 0x5649dcc5;

        public ArrayList<TL_contactSuggested> results = new ArrayList<TL_contactSuggested>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                results.add((TL_contactSuggested)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = results.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                results.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class RpcError extends TLObject {
        public int error_code;
        public String error_message;
        public long query_id;
    }

    public static class TL_rpc_error extends RpcError {
        public static int constructor = 0x2144ca19;


        public void readParams(AbsSerializedData stream) {
            error_code = stream.readInt32();
            error_message = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(error_code);
            stream.writeString(error_message);
        }
    }

    public static class TL_rpc_req_error extends RpcError {
        public static int constructor = 0x7ae432f5;


        public void readParams(AbsSerializedData stream) {
            query_id = stream.readInt64();
            error_code = stream.readInt32();
            error_message = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(query_id);
            stream.writeInt32(error_code);
            stream.writeString(error_message);
        }
    }

    public static class InputEncryptedFile extends TLObject {
        public long id;
        public long access_hash;
        public int parts;
        public int key_fingerprint;
        public String md5_checksum;
    }

    public static class TL_inputEncryptedFile extends InputEncryptedFile {
        public static int constructor = 0x5a17b5e5;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputEncryptedFileBigUploaded extends InputEncryptedFile {
        public static int constructor = 0x2dc173c8;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            parts = stream.readInt32();
            key_fingerprint = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeInt32(key_fingerprint);
        }
    }

    public static class TL_inputEncryptedFileEmpty extends InputEncryptedFile {
        public static int constructor = 0x1837c364;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputEncryptedFileUploaded extends InputEncryptedFile {
        public static int constructor = 0x64bd0306;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            parts = stream.readInt32();
            md5_checksum = stream.readString();
            key_fingerprint = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeString(md5_checksum);
            stream.writeInt32(key_fingerprint);
        }
    }

    public static class TL_decryptedMessageActionSetMessageTTL extends TLObject {
        public static int constructor = 0xa1733aec;

        public int ttl_seconds;

        public void readParams(AbsSerializedData stream) {
            ttl_seconds = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(ttl_seconds);
        }
    }

    public static class contacts_MyLink extends TLObject {
        public boolean contact;
    }

    public static class TL_contacts_myLinkRequested extends contacts_MyLink {
        public static int constructor = 0x6c69efee;


        public void readParams(AbsSerializedData stream) {
            contact = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(contact);
        }
    }

    public static class TL_contacts_myLinkContact extends contacts_MyLink {
        public static int constructor = 0xc240ebd9;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_myLinkEmpty extends contacts_MyLink {
        public static int constructor = 0xd22a1c60;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_server_DH_inner_data extends TLObject {
        public static int constructor = 0xb5890dba;

        public byte[] nonce;
        public byte[] server_nonce;
        public int g;
        public byte[] dh_prime;
        public byte[] g_a;
        public int server_time;

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            g = stream.readInt32();
            dh_prime = stream.readByteArray();
            g_a = stream.readByteArray();
            server_time = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeInt32(g);
            stream.writeByteArray(dh_prime);
            stream.writeByteArray(g_a);
            stream.writeInt32(server_time);
        }
    }

    public static class TL_new_session_created extends TLObject {
        public static int constructor = 0x9ec20908;

        public long first_msg_id;
        public long unique_id;
        public long server_salt;

        public void readParams(AbsSerializedData stream) {
            first_msg_id = stream.readInt64();
            unique_id = stream.readInt64();
            server_salt = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(first_msg_id);
            stream.writeInt64(unique_id);
            stream.writeInt64(server_salt);
        }
    }

    public static class UserProfilePhoto extends TLObject {
        public long photo_id;
        public FileLocation photo_small;
        public FileLocation photo_big;
    }

    public static class TL_userProfilePhotoEmpty extends UserProfilePhoto {
        public static int constructor = 0x4f11bae1;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userProfilePhoto extends UserProfilePhoto {
        public static int constructor = 0xd559d8c8;


        public void readParams(AbsSerializedData stream) {
            photo_id = stream.readInt64();
            photo_small = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            photo_big = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(photo_id);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class Photo extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public String caption;
        public GeoPoint geo;
        public ArrayList<PhotoSize> sizes = new ArrayList<PhotoSize>();
    }

    public static class TL_photo extends Photo {
        public static int constructor = 0x22b56751;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            caption = stream.readString();
            geo = (GeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                sizes.add((PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(caption);
            geo.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = sizes.size();
            stream.writeInt32(count);
            for (PhotoSize size : sizes) {
                size.serializeToStream(stream);
            }
        }
    }

    public static class TL_photoEmpty extends Photo {
        public static int constructor = 0x2331b22d;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_encryptedChatWaiting extends EncryptedChat {
        public static int constructor = 0x3bf703dc;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            date = stream.readInt32();
            admin_id = stream.readInt32();
            participant_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
        }
    }

    public static class TL_encryptedChatEmpty extends EncryptedChat {
        public static int constructor = 0xab7ec0a0;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_encryptedChatDiscarded extends EncryptedChat {
        public static int constructor = 0x13d6dd27;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_encryptedChat extends EncryptedChat {
        public static int constructor = 0xfa56ce36;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            date = stream.readInt32();
            admin_id = stream.readInt32();
            participant_id = stream.readInt32();
            g_a_or_b = stream.readByteArray();
            key_fingerprint = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a_or_b);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_encryptedChatRequested extends EncryptedChat {
        public static int constructor = 0xc878527e;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            date = stream.readInt32();
            admin_id = stream.readInt32();
            participant_id = stream.readInt32();
            g_a = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a);
        }
    }

    public static class TL_geochats_statedMessage extends TLObject {
        public static int constructor = 0x17b1578b;

        public GeoChatMessage message;
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public int seq;

        public void readParams(AbsSerializedData stream) {
            message = (GeoChatMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(seq);
        }
    }

    public static class TL_contact extends TLObject {
        public static int constructor = 0xf911c994;

        public int user_id;
        public boolean mutual;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            mutual = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeBool(mutual);
        }
    }

    public static class TL_config extends TLObject {
        public static int constructor = 0x2e54dd74;

        public int date;
        public boolean test_mode;
        public int this_dc;
        public ArrayList<TL_dcOption> dc_options = new ArrayList<TL_dcOption>();
        public int chat_size_max;
        public int broadcast_size_max;

        public void readParams(AbsSerializedData stream) {
            date = stream.readInt32();
            test_mode = stream.readBool();
            this_dc = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                dc_options.add((TL_dcOption)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            chat_size_max = stream.readInt32();
            broadcast_size_max = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            stream.writeBool(test_mode);
            stream.writeInt32(this_dc);
            stream.writeInt32(0x1cb5c415);
            int count = dc_options.size();
            stream.writeInt32(count);
            for (TL_dcOption dc_option : dc_options) {
                dc_option.serializeToStream(stream);
            }
            stream.writeInt32(chat_size_max);
            stream.writeInt32(broadcast_size_max);
        }
    }

    public static class TL_help_support extends TLObject {
        public static int constructor = 0x17c6b5f6;

        public String phone_number;
        public User user;

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            user = (User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            user.serializeToStream(stream);
        }
    }

    public static class TL_help_getSupport extends TLObject {
        public static int constructor = 0x9cdf08cd;

        public Class responseClass () {
            return TL_help_support.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class InputAudio extends TLObject {
        public long id;
        public long access_hash;
    }

    public static class TL_inputAudio extends InputAudio {
        public static int constructor = 0x77d440ff;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputAudioEmpty extends InputAudio {
        public static int constructor = 0xd95adc84;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messages_chats extends TLObject {
        public static int constructor = 0x8150cbd8;

        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_found extends TLObject {
        public static int constructor = 0x566000e;

        public ArrayList<TL_contactFound> results = new ArrayList<TL_contactFound>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                results.add((TL_contactFound)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = results.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                results.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class ChatParticipants extends TLObject {
        public int chat_id;
        public int admin_id;
        public ArrayList<TL_chatParticipant> participants = new ArrayList<TL_chatParticipant>();
        public int version;
    }

    public static class TL_chatParticipants extends ChatParticipants {
        public static int constructor = 0x7841b415;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            admin_id = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                participants.add((TL_chatParticipant)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            version = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(admin_id);
            stream.writeInt32(0x1cb5c415);
            int count = participants.size();
            stream.writeInt32(count);
            for (TL_chatParticipant participant : participants) {
                participant.serializeToStream(stream);
            }
            stream.writeInt32(version);
        }
    }

    public static class TL_chatParticipantsForbidden extends ChatParticipants {
        public static int constructor = 0xfd2bb8a;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class DecryptedMessageMedia extends TLObject {
        public byte[] thumb;
        public int thumb_w;
        public int thumb_h;
        public String file_name;
        public String mime_type;
        public int size;
        public byte[] key;
        public byte[] iv;
        public double lat;
        public double _long;
        public int duration;
        public int w;
        public int h;
        public String phone_number;
        public String first_name;
        public String last_name;
        public int user_id;
    }

    public static class TL_decryptedMessageMediaDocument extends DecryptedMessageMedia {
        public static int constructor = 0xb095434b;


        public void readParams(AbsSerializedData stream) {
            thumb = stream.readByteArray();
            thumb_w = stream.readInt32();
            thumb_h = stream.readInt32();
            file_name = stream.readString();
            mime_type = stream.readString();
            size = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(thumb);
            stream.writeInt32(thumb_w);
            stream.writeInt32(thumb_h);
            stream.writeString(file_name);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaGeoPoint extends DecryptedMessageMedia {
        public static int constructor = 0x35480a59;


        public void readParams(AbsSerializedData stream) {
            lat = stream.readDouble();
            _long = stream.readDouble();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(lat);
            stream.writeDouble(_long);
        }
    }

    public static class TL_decryptedMessageMediaAudio extends DecryptedMessageMedia {
        public static int constructor = 0x6080758f;


        public void readParams(AbsSerializedData stream) {
            duration = stream.readInt32();
            size = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaVideo extends DecryptedMessageMedia {
        public static int constructor = 0x4cee6ef3;


        public void readParams(AbsSerializedData stream) {
            thumb = stream.readByteArray();
            thumb_w = stream.readInt32();
            thumb_h = stream.readInt32();
            duration = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
            size = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(thumb);
            stream.writeInt32(thumb_w);
            stream.writeInt32(thumb_h);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaContact extends DecryptedMessageMedia {
        public static int constructor = 0x588a0a97;


        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            first_name = stream.readString();
            last_name = stream.readString();
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_decryptedMessageMediaEmpty extends DecryptedMessageMedia {
        public static int constructor = 0x89f5c4a;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_decryptedMessageMediaPhoto extends DecryptedMessageMedia {
        public static int constructor = 0x32798a8c;


        public void readParams(AbsSerializedData stream) {
            thumb = stream.readByteArray();
            thumb_w = stream.readInt32();
            thumb_h = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
            size = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(thumb);
            stream.writeInt32(thumb_w);
            stream.writeInt32(thumb_h);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_chatParticipant extends TLObject {
        public static int constructor = 0xc8d7493e;

        public int user_id;
        public int inviter_id;
        public int date;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            inviter_id = stream.readInt32();
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(inviter_id);
            stream.writeInt32(date);
        }
    }

    public static class Chat extends TLObject {
        public int id;
        public String title;
        public int date;
        public long access_hash;
        public String address;
        public String venue;
        public GeoPoint geo;
        public ChatPhoto photo;
        public int participants_count;
        public boolean checked_in;
        public int version;
        public boolean left;
    }

    public static class TL_chatForbidden extends Chat {
        public static int constructor = 0xfb0ccc41;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            title = stream.readString();
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            stream.writeInt32(date);
        }
    }

    public static class TL_geoChat extends Chat {
        public static int constructor = 0x75eaea5a;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            title = stream.readString();
            address = stream.readString();
            venue = stream.readString();
            geo = (GeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            photo = (ChatPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            participants_count = stream.readInt32();
            date = stream.readInt32();
            checked_in = stream.readBool();
            version = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeString(title);
            stream.writeString(address);
            stream.writeString(venue);
            geo.serializeToStream(stream);
            photo.serializeToStream(stream);
            stream.writeInt32(participants_count);
            stream.writeInt32(date);
            stream.writeBool(checked_in);
            stream.writeInt32(version);
        }
    }
    public static class TL_chat extends Chat {
        public static int constructor = 0x6e9c9bc7;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            title = stream.readString();
            photo = (ChatPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            participants_count = stream.readInt32();
            date = stream.readInt32();
            left = stream.readBool();
            version = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            photo.serializeToStream(stream);
            stream.writeInt32(participants_count);
            stream.writeInt32(date);
            stream.writeBool(left);
            stream.writeInt32(version);
        }
    }

    public static class storage_FileType extends TLObject {
    }

    public static class TL_storage_fileUnknown extends storage_FileType {
        public static int constructor = 0xaa963b05;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileWebp extends storage_FileType {
        public static int constructor = 0x1081464c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_filePng extends storage_FileType {
        public static int constructor = 0xa4f63c0;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileGif extends storage_FileType {
        public static int constructor = 0xcae1aadf;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileMov extends storage_FileType {
        public static int constructor = 0x4b09ebbc;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileMp3 extends storage_FileType {
        public static int constructor = 0x528a0677;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileJpeg extends storage_FileType {
        public static int constructor = 0x7efe0e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_filePartial extends storage_FileType {
        public static int constructor = 0x40bc6f52;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_storage_fileMp4 extends storage_FileType {
        public static int constructor = 0xb3cea0e4;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class MessagesFilter extends TLObject {
    }

    public static class TL_inputMessagesFilterVideo extends MessagesFilter {
        public static int constructor = 0x9fc00e65;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterEmpty extends MessagesFilter {
        public static int constructor = 0x57e2f66c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterPhotos extends MessagesFilter {
        public static int constructor = 0x9609a51c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterPhotoVideo extends MessagesFilter {
        public static int constructor = 0x56e9f0e4;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_msgs_state_info extends TLObject {
        public static int constructor = 0x04deb57d;

        public long req_msg_id;
        public String info;

        public void readParams(AbsSerializedData stream) {
            req_msg_id = stream.readInt64();
            info = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            stream.writeString(info);
        }
    }

    public static class TL_fileLocation extends FileLocation {
        public static int constructor = 0x53d69076;


        public void readParams(AbsSerializedData stream) {
            dc_id = stream.readInt32();
            volume_id = stream.readInt64();
            local_id = stream.readInt32();
            secret = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(dc_id);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class TL_fileLocationUnavailable extends FileLocation {
        public static int constructor = 0x7c596b46;


        public void readParams(AbsSerializedData stream) {
            volume_id = stream.readInt64();
            local_id = stream.readInt32();
            secret = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class messages_Message extends TLObject {
        public Message message;
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
    }

    public static class TL_messages_messageEmpty extends messages_Message {
        public static int constructor = 0x3f4e0648;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messages_message extends messages_Message {
        public static int constructor = 0xff90c417;


        public void readParams(AbsSerializedData stream) {
            message = (Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_geochats_located extends TLObject {
        public static int constructor = 0x48feb267;

        public ArrayList<TL_chatLocated> results = new ArrayList<TL_chatLocated>();
        public ArrayList<GeoChatMessage> messages = new ArrayList<GeoChatMessage>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                results.add((TL_chatLocated)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((GeoChatMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = results.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                results.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_inputGeoChat extends TLObject {
        public static int constructor = 0x74d456fa;

        public int chat_id;
        public long access_hash;

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_protoMessage extends TLObject {
        public static int constructor = 0x5bb8e511;

        public long msg_id;
        public int seqno;
        public int bytes;
        public TLObject body;

        public void readParams(AbsSerializedData stream) {
            msg_id = stream.readInt64();
            seqno = stream.readInt32();
            bytes = stream.readInt32();
            body = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt32(seqno);
            stream.writeInt32(bytes);
            body.serializeToStream(stream);
        }
    }

    public static class PhotoSize extends TLObject {
        public String type;
        public FileLocation location;
        public int w;
        public int h;
        public int size;
        public byte[] bytes;
    }

    public static class TL_photoSize extends PhotoSize {
        public static int constructor = 0x77bfb61b;


        public void readParams(AbsSerializedData stream) {
            type = stream.readString();
            location = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            w = stream.readInt32();
            h = stream.readInt32();
            size = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
            location.serializeToStream(stream);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeInt32(size);
        }
    }

    public static class TL_photoSizeEmpty extends PhotoSize {
        public static int constructor = 0xe17e23c;


        public void readParams(AbsSerializedData stream) {
            type = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
        }
    }

    public static class TL_photoCachedSize extends PhotoSize {
        public static int constructor = 0xe9a734fa;


        public void readParams(AbsSerializedData stream) {
            type = stream.readString();
            location = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            w = stream.readInt32();
            h = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
            location.serializeToStream(stream);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_contactFound extends TLObject {
        public static int constructor = 0xea879f95;

        public int user_id;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class InputFile extends TLObject {
        public long id;
        public int parts;
        public String name;
        public String md5_checksum;
    }

    public static class TL_inputFileBig extends InputFile {
        public static int constructor = 0xfa4f0bb5;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            parts = stream.readInt32();
            name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeString(name);
        }
    }

    public static class TL_inputFile extends InputFile {
        public static int constructor = 0xf52ff27f;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            parts = stream.readInt32();
            name = stream.readString();
            md5_checksum = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeString(name);
            stream.writeString(md5_checksum);
        }
    }

    public static class messages_StatedMessage extends TLObject {
        public Message message;
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public ArrayList<TL_contacts_link> links = new ArrayList<TL_contacts_link>();
        public int pts;
        public int seq;
    }

    public static class TL_messages_statedMessageLink extends messages_StatedMessage {
        public static int constructor = 0xa9af2881;


        public void readParams(AbsSerializedData stream) {
            message = (Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                links.add((TL_contacts_link)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            pts = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = links.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                links.get(a).serializeToStream(stream);
            }
            stream.writeInt32(pts);
            stream.writeInt32(seq);
        }
    }

    public static class TL_messages_statedMessage extends messages_StatedMessage {
        public static int constructor = 0xd07ae726;


        public void readParams(AbsSerializedData stream) {
            message = (Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            pts = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(pts);
            stream.writeInt32(seq);
        }
    }

    public static class TL_userFull extends TLObject {
        public static int constructor = 0x771095da;

        public User user;
        public TL_contacts_link link;
        public Photo profile_photo;
        public PeerNotifySettings notify_settings;
        public boolean blocked;
        public String real_first_name;
        public String real_last_name;

        public void readParams(AbsSerializedData stream) {
            user = (User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            link = (TL_contacts_link)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            profile_photo = (Photo)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            notify_settings = (PeerNotifySettings)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            blocked = stream.readBool();
            real_first_name = stream.readString();
            real_last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user.serializeToStream(stream);
            link.serializeToStream(stream);
            profile_photo.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
            stream.writeBool(blocked);
            stream.writeString(real_first_name);
            stream.writeString(real_last_name);
        }
    }

    public static class TL_updates_state extends TLObject {
        public static int constructor = 0xa56c2a3e;

        public int pts;
        public int qts;
        public int date;
        public int seq;
        public int unread_count;

        public void readParams(AbsSerializedData stream) {
            pts = stream.readInt32();
            qts = stream.readInt32();
            date = stream.readInt32();
            seq = stream.readInt32();
            unread_count = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pts);
            stream.writeInt32(qts);
            stream.writeInt32(date);
            stream.writeInt32(seq);
            stream.writeInt32(unread_count);
        }
    }

    public static class TL_resPQ extends TLObject {
        public static int constructor = 0x05162463;

        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] pq;
        public ArrayList<Long> server_public_key_fingerprints = new ArrayList<Long>();

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            pq = stream.readByteArray();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                server_public_key_fingerprints.add(stream.readInt64());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteArray(pq);
            stream.writeInt32(0x1cb5c415);
            int count = server_public_key_fingerprints.size();
            stream.writeInt32(count);
            for (Long server_public_key_fingerprint : server_public_key_fingerprints) {
                stream.writeInt64(server_public_key_fingerprint);
            }
        }
    }

    public static class Updates extends TLObject {
        public int id;
        public int from_id;
        public int chat_id;
        public String message;
        public int pts;
        public int date;
        public int seq;
        public ArrayList<Update> updates = new ArrayList<Update>();
        public ArrayList<User> users = new ArrayList<User>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public Update update;
        public int seq_start;
    }

    public static class TL_updateShortChatMessage extends Updates {
        public static int constructor = 0x2b2fbd4e;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            from_id = stream.readInt32();
            chat_id = stream.readInt32();
            message = stream.readString();
            pts = stream.readInt32();
            date = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            stream.writeInt32(chat_id);
            stream.writeString(message);
            stream.writeInt32(pts);
            stream.writeInt32(date);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updates extends Updates {
        public static int constructor = 0x74ae4240;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                updates.add((Update)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            date = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = updates.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                updates.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updateShortMessage extends Updates {
        public static int constructor = 0xd3f45784;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            from_id = stream.readInt32();
            message = stream.readString();
            pts = stream.readInt32();
            date = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            stream.writeString(message);
            stream.writeInt32(pts);
            stream.writeInt32(date);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updateShort extends Updates {
        public static int constructor = 0x78d4dec1;


        public void readParams(AbsSerializedData stream) {
            update = (Update)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            update.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }

    public static class TL_updatesCombined extends Updates {
        public static int constructor = 0x725b04c3;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                updates.add((Update)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            date = stream.readInt32();
            seq_start = stream.readInt32();
            seq = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = updates.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                updates.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeInt32(seq_start);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updatesTooLong extends Updates {
        public static int constructor = 0xe317af7e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messages_chat extends TLObject {
        public static int constructor = 0x40e9002a;

        public Chat chat;
        public ArrayList<User> users = new ArrayList<User>();

        public void readParams(AbsSerializedData stream) {
            chat = (Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            chat.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (User user : users) {
                user.serializeToStream(stream);
            }
        }
    }

    public static class WallPaper extends TLObject {
        public int id;
        public String title;
        public ArrayList<PhotoSize> sizes = new ArrayList<PhotoSize>();
        public int color;
        public int bg_color;
    }

    public static class TL_wallPaper extends WallPaper {
        public static int constructor = 0xccb03657;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            title = stream.readString();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                sizes.add((PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            color = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            stream.writeInt32(0x1cb5c415);
            int count = sizes.size();
            stream.writeInt32(count);
            for (PhotoSize size : sizes) {
                size.serializeToStream(stream);
            }
            stream.writeInt32(color);
        }
    }

    public static class TL_wallPaperSolid extends WallPaper {
        public static int constructor = 0x63117f24;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            title = stream.readString();
            bg_color = stream.readInt32();
            color = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            stream.writeInt32(bg_color);
            stream.writeInt32(color);
        }
    }

    public static class MsgDetailedInfo extends TLObject {
        public long answer_msg_id;
        public int bytes;
        public int status;
        public long msg_id;
    }

    public static class TL_msg_new_detailed_info extends MsgDetailedInfo {
        public static int constructor = 0x809db6df;


        public void readParams(AbsSerializedData stream) {
            answer_msg_id = stream.readInt64();
            bytes = stream.readInt32();
            status = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(answer_msg_id);
            stream.writeInt32(bytes);
            stream.writeInt32(status);
        }
    }

    public static class TL_msg_detailed_info extends MsgDetailedInfo {
        public static int constructor = 0x276d3ec6;


        public void readParams(AbsSerializedData stream) {
            msg_id = stream.readInt64();
            answer_msg_id = stream.readInt64();
            bytes = stream.readInt32();
            status = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt64(answer_msg_id);
            stream.writeInt32(bytes);
            stream.writeInt32(status);
        }
    }

    public static class TL_inputEncryptedChat extends TLObject {
        public static int constructor = 0xf141b5e1;

        public int chat_id;
        public long access_hash;

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class InputChatPhoto extends TLObject {
        public InputPhoto id;
        public InputPhotoCrop crop;
        public InputFile file;
    }

    public static class TL_inputChatPhoto extends InputChatPhoto {
        public static int constructor = 0xb2e1bf08;


        public void readParams(AbsSerializedData stream) {
            id = (InputPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            crop = (InputPhotoCrop)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class TL_inputChatPhotoEmpty extends InputChatPhoto {
        public static int constructor = 0x1ca48f57;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputChatUploadedPhoto extends InputChatPhoto {
        public static int constructor = 0x94254732;


        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            crop = (InputPhotoCrop)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class InputVideo extends TLObject {
        public long id;
        public long access_hash;
    }

    public static class TL_inputVideoEmpty extends InputVideo {
        public static int constructor = 0x5508ec75;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputVideo extends InputVideo {
        public static int constructor = 0xee579652;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_nearestDc extends TLObject {
        public static int constructor = 0x8e1a1775;

        public String country;
        public int this_dc;
        public int nearest_dc;

        public void readParams(AbsSerializedData stream) {
            country = stream.readString();
            this_dc = stream.readInt32();
            nearest_dc = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(country);
            stream.writeInt32(this_dc);
            stream.writeInt32(nearest_dc);
        }
    }

    public static class InputPhoto extends TLObject {
        public long id;
        public long access_hash;
    }

    public static class TL_inputPhotoEmpty extends InputPhoto {
        public static int constructor = 0x1cd7bf0d;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPhoto extends InputPhoto {
        public static int constructor = 0xfb95c6c4;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_importedContact extends TLObject {
        public static int constructor = 0xd0028438;

        public int user_id;
        public long client_id;

        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            client_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(client_id);
        }
    }

    public static class InputPeer extends TLObject {
        public int user_id;
        public int chat_id;
        public long access_hash;
    }

    public static class TL_inputPeerContact extends InputPeer {
        public static int constructor = 0x1023dbe8;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_inputPeerChat extends InputPeer {
        public static int constructor = 0x179be863;


        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_inputPeerEmpty extends InputPeer {
        public static int constructor = 0x7f3b18ea;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPeerSelf extends InputPeer {
        public static int constructor = 0x7da07ec9;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPeerForeign extends InputPeer {
        public static int constructor = 0x9b447325;


        public void readParams(AbsSerializedData stream) {
            user_id = stream.readInt32();
            access_hash = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_dcOption extends TLObject {
        public static int constructor = 0x2ec2a43c;

        public int id;
        public String hostname;
        public String ip_address;
        public int port;

        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            hostname = stream.readString();
            ip_address = stream.readString();
            port = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(hostname);
            stream.writeString(ip_address);
            stream.writeInt32(port);
        }
    }

    public static class TL_decryptedMessageLayer extends TLObject {
        public static int constructor = 0x99a438cf;

        public int layer;
        public DecryptedMessage message;

        public void readParams(AbsSerializedData stream) {
            layer = stream.readInt32();
            message = (DecryptedMessage)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(layer);
            message.serializeToStream(stream);
        }
    }

    public static class InputPhotoCrop extends TLObject {
        public double crop_left;
        public double crop_top;
        public double crop_width;
    }

    public static class TL_inputPhotoCropAuto extends InputPhotoCrop {
        public static int constructor = 0xade6b004;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPhotoCrop extends InputPhotoCrop {
        public static int constructor = 0xd9915325;


        public void readParams(AbsSerializedData stream) {
            crop_left = stream.readDouble();
            crop_top = stream.readDouble();
            crop_width = stream.readDouble();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(crop_left);
            stream.writeDouble(crop_top);
            stream.writeDouble(crop_width);
        }
    }

    public static class messages_Dialogs extends TLObject {
        public ArrayList<TL_dialog> dialogs = new ArrayList<TL_dialog>();
        public ArrayList<Message> messages = new ArrayList<Message>();
        public ArrayList<Chat> chats = new ArrayList<Chat>();
        public ArrayList<User> users = new ArrayList<User>();
        public int count;
    }

    public static class TL_messages_dialogs extends messages_Dialogs {
        public static int constructor = 0x15ba6c40;


        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                dialogs.add((TL_dialog)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = dialogs.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                dialogs.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_dialogsSlice extends messages_Dialogs {
        public static int constructor = 0x71e094f3;


        public void readParams(AbsSerializedData stream) {
            count = stream.readInt32();
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                dialogs.add((TL_dialog)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                messages.add((Message)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                chats.add((Chat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            stream.readInt32();
            count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((User)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(count);
            stream.writeInt32(0x1cb5c415);
            int count = dialogs.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                dialogs.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                messages.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = chats.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                chats.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_req_pq extends TLObject {
        public static int constructor = 0x60469778;

        public byte[] nonce;

        public Class responseClass () {
            return TL_resPQ.class;
        }

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
        }
    }

    public static class TL_req_DH_params extends TLObject {
        public static int constructor = 0xd712e4be;

        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] p;
        public byte[] q;
        public long public_key_fingerprint;
        public byte[] encrypted_data;

        public Class responseClass () {
            return Server_DH_Params.class;
        }

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            p = stream.readByteArray();
            q = stream.readByteArray();
            public_key_fingerprint = stream.readInt64();
            encrypted_data = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteArray(p);
            stream.writeByteArray(q);
            stream.writeInt64(public_key_fingerprint);
            stream.writeByteArray(encrypted_data);
        }
    }

    public static class TL_set_client_DH_params extends TLObject {
        public static int constructor = 0xf5045f1f;

        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] encrypted_data;

        public Class responseClass () {
            return Set_client_DH_params_answer.class;
        }

        public void readParams(AbsSerializedData stream) {
            nonce = stream.readData(16);
            server_nonce = stream.readData(16);
            encrypted_data = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteArray(encrypted_data);
        }
    }

    public static class TL_auth_checkPhone extends TLObject {
        public static int constructor = 0x6fe51dfb;

        public String phone_number;

        public Class responseClass () {
            return TL_auth_checkedPhone.class;
        }

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
        }
    }

    public static class TL_auth_sendCode extends TLObject {
        public static int constructor = 0x768d5f4d;

        public String phone_number;
        public int sms_type;
        public int api_id;
        public String api_hash;
        public String lang_code;

        public Class responseClass () {
            return TL_auth_sentCode.class;
        }

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            sms_type = stream.readInt32();
            api_id = stream.readInt32();
            api_hash = stream.readString();
            lang_code = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeInt32(sms_type);
            stream.writeInt32(api_id);
            stream.writeString(api_hash);
            stream.writeString(lang_code);
        }
    }

    public static class TL_auth_sendCall extends TLObject {
        public static int constructor = 0x3c51564;

        public String phone_number;
        public String phone_code_hash;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            phone_code_hash = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
        }
    }

    public static class TL_auth_signUp extends TLObject {
        public static int constructor = 0x1b067634;

        public String phone_number;
        public String phone_code_hash;
        public String phone_code;
        public String first_name;
        public String last_name;

        public Class responseClass () {
            return TL_auth_authorization.class;
        }

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            phone_code_hash = stream.readString();
            phone_code = stream.readString();
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_auth_signIn extends TLObject {
        public static int constructor = 0xbcd51581;

        public String phone_number;
        public String phone_code_hash;
        public String phone_code;

        public Class responseClass () {
            return TL_auth_authorization.class;
        }

        public void readParams(AbsSerializedData stream) {
            phone_number = stream.readString();
            phone_code_hash = stream.readString();
            phone_code = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
        }
    }

    public static class TL_auth_logOut extends TLObject {
        public static int constructor = 0x5717da40;


        public Class responseClass () {
            return Bool.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_resetAuthorizations extends TLObject {
        public static int constructor = 0x9fab0d1a;


        public Class responseClass () {
            return Bool.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_sendInvites extends TLObject {
        public static int constructor = 0x771c1d97;

        public ArrayList<String> phone_numbers = new ArrayList<String>();
        public String message;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                phone_numbers.add(stream.readString());
            }
            message = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = phone_numbers.size();
            stream.writeInt32(count);
            for (String phone_number : phone_numbers) {
                stream.writeString(phone_number);
            }
            stream.writeString(message);
        }
    }

    public static class TL_auth_exportAuthorization extends TLObject {
        public static int constructor = 0xe5bfffcd;

        public int dc_id;

        public Class responseClass () {
            return TL_auth_exportedAuthorization.class;
        }

        public void readParams(AbsSerializedData stream) {
            dc_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(dc_id);
        }
    }

    public static class TL_auth_importAuthorization extends TLObject {
        public static int constructor = 0xe3ef9613;

        public int id;
        public byte[] bytes;

        public Class responseClass () {
            return TL_auth_authorization.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_account_registerDevice extends TLObject {
        public static int constructor = 0x446c712c;

        public int token_type;
        public String token;
        public String device_model;
        public String system_version;
        public String app_version;
        public boolean app_sandbox;
        public String lang_code;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            token_type = stream.readInt32();
            token = stream.readString();
            device_model = stream.readString();
            system_version = stream.readString();
            app_version = stream.readString();
            app_sandbox = stream.readBool();
            lang_code = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(token_type);
            stream.writeString(token);
            stream.writeString(device_model);
            stream.writeString(system_version);
            stream.writeString(app_version);
            stream.writeBool(app_sandbox);
            stream.writeString(lang_code);
        }
    }

    public static class TL_account_unregisterDevice extends TLObject {
        public static int constructor = 0x65c55b40;

        public int token_type;
        public String token;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            token_type = stream.readInt32();
            token = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(token_type);
            stream.writeString(token);
        }
    }

    public static class TL_account_updateNotifySettings extends TLObject {
        public static int constructor = 0x84be5b93;

        public InputNotifyPeer peer;
        public TL_inputPeerNotifySettings settings;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputNotifyPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            settings = (TL_inputPeerNotifySettings)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            settings.serializeToStream(stream);
        }
    }

    public static class TL_account_getNotifySettings extends TLObject {
        public static int constructor = 0x12b3ad31;

        public InputNotifyPeer peer;

        public Class responseClass () {
            return PeerNotifySettings.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputNotifyPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_account_resetNotifySettings extends TLObject {
        public static int constructor = 0xdb7e1747;


        public Class responseClass () {
            return Bool.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_updateProfile extends TLObject {
        public static int constructor = 0xf0888d68;

        public String first_name;
        public String last_name;

        public Class responseClass () {
            return User.class;
        }

        public void readParams(AbsSerializedData stream) {
            first_name = stream.readString();
            last_name = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_account_updateStatus extends TLObject {
        public static int constructor = 0x6628562c;

        public boolean offline;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            offline = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(offline);
        }
    }

    public static class TL_users_getUsers extends TLObject {
        public static int constructor = 0xd91a548;

        public ArrayList<InputUser> id = new ArrayList<InputUser>();

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add((InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (InputUser anId : id) {
                anId.serializeToStream(stream);
            }
        }
    }

    public static class TL_users_getFullUser extends TLObject {
        public static int constructor = 0xca30a5b1;

        public InputUser id;

        public Class responseClass () {
            return TL_userFull.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_getStatuses extends TLObject {
        public static int constructor = 0xc4a353ee;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_getContacts extends TLObject {
        public static int constructor = 0x22c6aa08;

        public String hash;

        public Class responseClass () {
            return contacts_Contacts.class;
        }

        public void readParams(AbsSerializedData stream) {
            hash = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
        }
    }

    public static class TL_contacts_importContacts extends TLObject {
        public static int constructor = 0xda30b32d;

        public ArrayList<TL_inputPhoneContact> contacts = new ArrayList<TL_inputPhoneContact>();
        public boolean replace;

        public Class responseClass () {
            return TL_contacts_importedContacts.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                contacts.add((TL_inputPhoneContact)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            replace = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = contacts.size();
            stream.writeInt32(count);
            for (TL_inputPhoneContact contact : contacts) {
                contact.serializeToStream(stream);
            }
            stream.writeBool(replace);
        }
    }

    public static class TL_contacts_search extends TLObject {
        public static int constructor = 0x11f812d8;

        public String q;
        public int limit;

        public Class responseClass () {
            return TL_contacts_found.class;
        }

        public void readParams(AbsSerializedData stream) {
            q = stream.readString();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(q);
            stream.writeInt32(limit);
        }
    }

    public static class TL_contacts_getSuggested extends TLObject {
        public static int constructor = 0xcd773428;

        public int limit;

        public Class responseClass () {
            return TL_contacts_suggested.class;
        }

        public void readParams(AbsSerializedData stream) {
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(limit);
        }
    }

    public static class TL_contacts_deleteContact extends TLObject {
        public static int constructor = 0x8e953744;

        public InputUser id;

        public Class responseClass () {
            return TL_contacts_link.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_deleteContacts extends TLObject {
        public static int constructor = 0x59ab389e;

        public ArrayList<InputUser> id = new ArrayList<InputUser>();

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add((InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (InputUser anId : id) {
                anId.serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_block extends TLObject {
        public static int constructor = 0x332b49fc;

        public InputUser id;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_unblock extends TLObject {
        public static int constructor = 0xe54100bd;

        public InputUser id;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_getBlocked extends TLObject {
        public static int constructor = 0xf57c350f;

        public int offset;
        public int limit;

        public Class responseClass () {
            return contacts_Blocked.class;
        }

        public void readParams(AbsSerializedData stream) {
            offset = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_getMessages extends TLObject {
        public static int constructor = 0x4222fa74;

        public ArrayList<Integer> id = new ArrayList<Integer>();

        public Class responseClass () {
            return messages_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (Integer anId : id) {
                stream.writeInt32(anId);
            }
        }
    }

    public static class TL_messages_getDialogs extends TLObject {
        public static int constructor = 0xeccf1df6;

        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return messages_Dialogs.class;
        }

        public void readParams(AbsSerializedData stream) {
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_getHistory extends TLObject {
        public static int constructor = 0x92a1df2f;

        public InputPeer peer;
        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return messages_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_search extends TLObject {
        public static int constructor = 0x7e9f2ab;

        public InputPeer peer;
        public String q;
        public MessagesFilter filter;
        public int min_date;
        public int max_date;
        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return messages_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            q = stream.readString();
            filter = (MessagesFilter)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            min_date = stream.readInt32();
            max_date = stream.readInt32();
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(q);
            filter.serializeToStream(stream);
            stream.writeInt32(min_date);
            stream.writeInt32(max_date);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_readHistory extends TLObject {
        public static int constructor = 0xb04f2510;

        public InputPeer peer;
        public int max_id;
        public int offset;

        public Class responseClass () {
            return TL_messages_affectedHistory.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            max_id = stream.readInt32();
            offset = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
            stream.writeInt32(offset);
        }
    }

    public static class TL_messages_deleteHistory extends TLObject {
        public static int constructor = 0xf4f8fb61;

        public InputPeer peer;
        public int offset;

        public Class responseClass () {
            return TL_messages_affectedHistory.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            offset = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset);
        }
    }

    public static class TL_messages_setTyping extends TLObject {
        public static int constructor = 0x719839e9;

        public InputPeer peer;
        public boolean typing;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            typing = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(typing);
        }
    }

    public static class TL_messages_sendMessage extends TLObject {
        public static int constructor = 0x4cde0aab;

        public InputPeer peer;
        public String message;
        public long random_id;

        public Class responseClass () {
            return messages_SentMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            message = stream.readString();
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(message);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_messages_sendMedia extends TLObject {
        public static int constructor = 0xa3c85d76;

        public InputPeer peer;
        public InputMedia media;
        public long random_id;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            media = (InputMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            media.serializeToStream(stream);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_messages_forwardMessages extends TLObject {
        public static int constructor = 0x514cd10f;

        public InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<Integer>();

        public Class responseClass () {
            return messages_StatedMessages.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (Integer anId : id) {
                stream.writeInt32(anId);
            }
        }
    }

    public static class TL_messages_getChats extends TLObject {
        public static int constructor = 0x3c6aa187;

        public ArrayList<Integer> id = new ArrayList<Integer>();

        public Class responseClass () {
            return TL_messages_chats.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (Integer anId : id) {
                stream.writeInt32(anId);
            }
        }
    }

    public static class TL_messages_getFullChat extends TLObject {
        public static int constructor = 0x3b831c66;

        public int chat_id;

        public Class responseClass () {
            return TL_messages_chatFull.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_messages_editChatTitle extends TLObject {
        public static int constructor = 0xb4bc68b5;

        public int chat_id;
        public String title;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            title = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeString(title);
        }
    }

    public static class TL_messages_editChatPhoto extends TLObject {
        public static int constructor = 0xd881821d;

        public int chat_id;
        public InputChatPhoto photo;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            photo = (InputChatPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messages_addChatUser extends TLObject {
        public static int constructor = 0x2ee9ee9e;

        public int chat_id;
        public InputUser user_id;
        public int fwd_limit;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            user_id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            fwd_limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            user_id.serializeToStream(stream);
            stream.writeInt32(fwd_limit);
        }
    }

    public static class TL_messages_deleteChatUser extends TLObject {
        public static int constructor = 0xc3c5cd23;

        public int chat_id;
        public InputUser user_id;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
            user_id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            user_id.serializeToStream(stream);
        }
    }

    public static class TL_messages_createChat extends TLObject {
        public static int constructor = 0x419d9aee;

        public ArrayList<InputUser> users = new ArrayList<InputUser>();
        public String title;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                users.add((InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            title = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (InputUser user : users) {
                user.serializeToStream(stream);
            }
            stream.writeString(title);
        }
    }

    public static class TL_updates_getState extends TLObject {
        public static int constructor = 0xedd4882a;


        public Class responseClass () {
            return TL_updates_state.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updates_getDifference extends TLObject {
        public static int constructor = 0xa041495;

        public int pts;
        public int date;
        public int qts;

        public Class responseClass () {
            return updates_Difference.class;
        }

        public void readParams(AbsSerializedData stream) {
            pts = stream.readInt32();
            date = stream.readInt32();
            qts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pts);
            stream.writeInt32(date);
            stream.writeInt32(qts);
        }
    }

    public static class TL_photos_updateProfilePhoto extends TLObject {
        public static int constructor = 0xeef579a0;

        public InputPhoto id;
        public InputPhotoCrop crop;

        public Class responseClass () {
            return UserProfilePhoto.class;
        }

        public void readParams(AbsSerializedData stream) {
            id = (InputPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            crop = (InputPhotoCrop)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class TL_photos_uploadProfilePhoto extends TLObject {
        public static int constructor = 0xd50f9c88;

        public InputFile file;
        public String caption;
        public InputGeoPoint geo_point;
        public InputPhotoCrop crop;

        public Class responseClass () {
            return TL_photos_photo.class;
        }

        public void readParams(AbsSerializedData stream) {
            file = (InputFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            caption = stream.readString();
            geo_point = (InputGeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            crop = (InputPhotoCrop)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(caption);
            geo_point.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class TL_upload_saveFilePart extends TLObject {
        public static int constructor = 0xb304a621;

        public long file_id;
        public int file_part;
        public byte[] bytes;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            file_id = stream.readInt64();
            file_part = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(file_id);
            stream.writeInt32(file_part);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_upload_getFile extends TLObject {
        public static int constructor = 0xe3a6cfb5;

        public InputFileLocation location;
        public int offset;
        public int limit;

        public Class responseClass () {
            return TL_upload_file.class;
        }

        public void readParams(AbsSerializedData stream) {
            location = (InputFileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            offset = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            location.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_help_getConfig extends TLObject {
        public static int constructor = 0xc4f9186b;


        public Class responseClass () {
            return TL_config.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_help_getNearestDc extends TLObject {
        public static int constructor = 0x1fb33026;


        public Class responseClass () {
            return TL_nearestDc.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_help_getAppUpdate extends TLObject {
        public static int constructor = 0xc812ac7e;

        public String device_model;
        public String system_version;
        public String app_version;
        public String lang_code;

        public Class responseClass () {
            return help_AppUpdate.class;
        }

        public void readParams(AbsSerializedData stream) {
            device_model = stream.readString();
            system_version = stream.readString();
            app_version = stream.readString();
            lang_code = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(device_model);
            stream.writeString(system_version);
            stream.writeString(app_version);
            stream.writeString(lang_code);
        }
    }

    public static class TL_help_saveAppLog extends TLObject {
        public static int constructor = 0x6f02f748;

        public ArrayList<TL_inputAppEvent> events = new ArrayList<TL_inputAppEvent>();

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                events.add((TL_inputAppEvent)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = events.size();
            stream.writeInt32(count);
            for (TL_inputAppEvent event : events) {
                event.serializeToStream(stream);
            }
        }
    }

    public static class TL_help_getInviteText extends TLObject {
        public static int constructor = 0xa4a95186;

        public String lang_code;

        public Class responseClass () {
            return TL_help_inviteText.class;
        }

        public void readParams(AbsSerializedData stream) {
            lang_code = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(lang_code);
        }
    }

    public static class TL_photos_getUserPhotos extends TLObject {
        public static int constructor = 0xb7ee553c;

        public InputUser user_id;
        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return photos_Photos.class;
        }

        public void readParams(AbsSerializedData stream) {
            user_id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_forwardMessage extends TLObject {
        public static int constructor = 0x3f3f4f2;

        public InputPeer peer;
        public int id;
        public long random_id;

        public Class responseClass () {
            return messages_StatedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (InputPeer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            id = stream.readInt32();
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_messages_sendBroadcast extends TLObject {
        public static int constructor = 0x41bb0972;

        public ArrayList<InputUser> contacts = new ArrayList<InputUser>();
        public String message;
        public InputMedia media;

        public Class responseClass () {
            return messages_StatedMessages.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                contacts.add((InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32()));
            }
            message = stream.readString();
            media = (InputMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = contacts.size();
            stream.writeInt32(count);
            for (InputUser contact : contacts) {
                contact.serializeToStream(stream);
            }
            stream.writeString(message);
            media.serializeToStream(stream);
        }
    }

    public static class TL_geochats_getLocated extends TLObject {
        public static int constructor = 0x7f192d8f;

        public InputGeoPoint geo_point;
        public int radius;
        public int limit;

        public Class responseClass () {
            return TL_geochats_located.class;
        }

        public void readParams(AbsSerializedData stream) {
            geo_point = (InputGeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            radius = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo_point.serializeToStream(stream);
            stream.writeInt32(radius);
            stream.writeInt32(limit);
        }
    }

    public static class TL_geochats_getRecents extends TLObject {
        public static int constructor = 0xe1427e6f;

        public int offset;
        public int limit;

        public Class responseClass () {
            return geochats_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            offset = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_geochats_checkin extends TLObject {
        public static int constructor = 0x55b3e8fb;

        public TL_inputGeoChat peer;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_geochats_getFullChat extends TLObject {
        public static int constructor = 0x6722dd6f;

        public TL_inputGeoChat peer;

        public Class responseClass () {
            return TL_messages_chatFull.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_geochats_editChatTitle extends TLObject {
        public static int constructor = 0x4c8e2273;

        public TL_inputGeoChat peer;
        public String title;
        public String address;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            title = stream.readString();
            address = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(title);
            stream.writeString(address);
        }
    }

    public static class TL_geochats_editChatPhoto extends TLObject {
        public static int constructor = 0x35d81a95;

        public TL_inputGeoChat peer;
        public InputChatPhoto photo;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            photo = (InputChatPhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_geochats_search extends TLObject {
        public static int constructor = 0xcfcdc44d;

        public TL_inputGeoChat peer;
        public String q;
        public MessagesFilter filter;
        public int min_date;
        public int max_date;
        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return geochats_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            q = stream.readString();
            filter = (MessagesFilter)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            min_date = stream.readInt32();
            max_date = stream.readInt32();
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(q);
            filter.serializeToStream(stream);
            stream.writeInt32(min_date);
            stream.writeInt32(max_date);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_geochats_getHistory extends TLObject {
        public static int constructor = 0xb53f7a68;

        public TL_inputGeoChat peer;
        public int offset;
        public int max_id;
        public int limit;

        public Class responseClass () {
            return geochats_Messages.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            offset = stream.readInt32();
            max_id = stream.readInt32();
            limit = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt32(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_geochats_setTyping extends TLObject {
        public static int constructor = 0x8b8a729;

        public TL_inputGeoChat peer;
        public boolean typing;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            typing = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(typing);
        }
    }

    public static class TL_geochats_sendMessage extends TLObject {
        public static int constructor = 0x61b0044;

        public TL_inputGeoChat peer;
        public String message;
        public long random_id;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            message = stream.readString();
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeString(message);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_geochats_sendMedia extends TLObject {
        public static int constructor = 0xb8f0deff;

        public TL_inputGeoChat peer;
        public InputMedia media;
        public long random_id;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputGeoChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            media = (InputMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            media.serializeToStream(stream);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_geochats_createGeoChat extends TLObject {
        public static int constructor = 0xe092e16;

        public String title;
        public InputGeoPoint geo_point;
        public String address;
        public String venue;

        public Class responseClass () {
            return TL_geochats_statedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            title = stream.readString();
            geo_point = (InputGeoPoint)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            address = stream.readString();
            venue = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            geo_point.serializeToStream(stream);
            stream.writeString(address);
            stream.writeString(venue);
        }
    }

    public static class TL_messages_getDhConfig extends TLObject {
        public static int constructor = 0x26cf8950;

        public int version;
        public int random_length;

        public Class responseClass () {
            return messages_DhConfig.class;
        }

        public void readParams(AbsSerializedData stream) {
            version = stream.readInt32();
            random_length = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(version);
            stream.writeInt32(random_length);
        }
    }

    public static class TL_messages_requestEncryption extends TLObject {
        public static int constructor = 0xf64daf43;

        public InputUser user_id;
        public int random_id;
        public byte[] g_a;

        public Class responseClass () {
            return EncryptedChat.class;
        }

        public void readParams(AbsSerializedData stream) {
            user_id = (InputUser)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt32();
            g_a = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            stream.writeInt32(random_id);
            stream.writeByteArray(g_a);
        }
    }

    public static class TL_messages_acceptEncryption extends TLObject {
        public static int constructor = 0x3dbc0415;

        public TL_inputEncryptedChat peer;
        public byte[] g_b;
        public long key_fingerprint;

        public Class responseClass () {
            return EncryptedChat.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            g_b = stream.readByteArray();
            key_fingerprint = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeByteArray(g_b);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_messages_discardEncryption extends TLObject {
        public static int constructor = 0xedd923c5;

        public int chat_id;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            chat_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_messages_setEncryptedTyping extends TLObject {
        public static int constructor = 0x791451ed;

        public TL_inputEncryptedChat peer;
        public boolean typing;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            typing = stream.readBool();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(typing);
        }
    }

    public static class TL_messages_readEncryptedHistory extends TLObject {
        public static int constructor = 0x7f4b690a;

        public TL_inputEncryptedChat peer;
        public int max_date;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            max_date = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_date);
        }
    }

    public static class TL_messages_sendEncrypted extends TLObject {
        public static int constructor = 0xa9776773;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public byte[] data;

        public Class responseClass () {
            return messages_SentEncryptedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt64();
            data = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteArray(data);
        }
    }

    public static class TL_messages_sendEncryptedFile extends TLObject {
        public static int constructor = 0x9a901b66;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public byte[] data;
        public InputEncryptedFile file;

        public Class responseClass () {
            return messages_SentEncryptedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt64();
            data = stream.readByteArray();
            file = (InputEncryptedFile)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteArray(data);
            file.serializeToStream(stream);
        }
    }

    public static class TL_messages_sendEncryptedService extends TLObject {
        public static int constructor = 0x32d439a4;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public byte[] data;

        public Class responseClass () {
            return messages_SentEncryptedMessage.class;
        }

        public void readParams(AbsSerializedData stream) {
            peer = (TL_inputEncryptedChat)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            random_id = stream.readInt64();
            data = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteArray(data);
        }
    }

    public static class TL_upload_saveBigFilePart extends TLObject {
        public static int constructor = 0xde7b673d;

        public long file_id;
        public int file_part;
        public int file_total_parts;
        public byte[] bytes;

        public Class responseClass () {
            return Bool.class;
        }

        public void readParams(AbsSerializedData stream) {
            file_id = stream.readInt64();
            file_part = stream.readInt32();
            file_total_parts = stream.readInt32();
            bytes = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(file_id);
            stream.writeInt32(file_part);
            stream.writeInt32(file_total_parts);
            stream.writeByteArray(bytes);
        }
    }

    //manually created

    public static class UserStatus extends TLObject {
        public int expires;
    }

    public static class TL_userStatusEmpty extends UserStatus {
        public static int constructor = 0x9d05049;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userStatusOnline extends UserStatus {
        public static int constructor = 0xedb93949;


        public void readParams(AbsSerializedData stream) {
            expires = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(expires);
        }
    }

    public static class TL_userStatusOffline extends UserStatus {
        public static int constructor = 0x8c703f;


        public void readParams(AbsSerializedData stream) {
            expires = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(expires);
        }
    }

    public static class TL_upload_file extends TLObject {
        public static int constructor = 0x96a18d5;

        public storage_FileType type;
        public int mtime;
        public ByteBufferDesc bytes;

        public void readParams(AbsSerializedData stream) {
            type = (storage_FileType)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            mtime = stream.readInt32();
            bytes = stream.readByteBuffer();
        }

        @Override
        public void freeResources() {
            if (bytes != null) {
                BuffersStorage.Instance.reuseFreeBuffer(bytes);
                bytes = null;
            }
        }
    }

    public static class TL_messages_receivedQueue extends TLObject {
        public static int constructor = 0x55a5bb66;

        public int max_qts;

        public Class responseClass () {
            return Vector.class;
        }

        public void parseVector(Vector vector, AbsSerializedData data) {
            int size = data.readInt32();
            for (int a = 0; a < size; a++) {
                vector.objects.add(data.readInt64());
            }
        }

        public void readParams(AbsSerializedData stream) {
            max_qts = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_qts);
        }
    }

    public static class TL_account_getWallPapers extends TLObject {
        public static int constructor = 0xc04cfac2;

        public Class responseClass () {
            return Vector.class;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }

        public void parseVector(Vector vector, AbsSerializedData data) {
            int size = data.readInt32();
            for (int a = 0; a < size; a++) {
                vector.objects.add(TLClassStore.Instance().TLdeserialize(data, data.readInt32()));
            }
        }
    }

    public static class TL_get_future_salts extends TLObject {
        public static int constructor = 0xb921bd04;

        public int num;

        public int layer () {
            return 0;
        }

        public Class responseClass () {
            return TL_futuresalts.class;
        }

        public void readParams(AbsSerializedData stream) {
            num = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(num);
        }
    }

    public static class TL_rpc_drop_answer extends TLObject {
        public static int constructor = 0x58e4a740;

        public long req_msg_id;

        public int layer () {
            return 0;
        }

        public Class responseClass () {
            return RpcDropAnswer.class;
        }

        public void readParams(AbsSerializedData stream) {
            req_msg_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
        }
    }

    public static class TL_msg_container extends TLObject {
        public ArrayList<TL_protoMessage> messages;

        public static int constructor = 0x73f1f8dc;

        public void readParams(AbsSerializedData stream) {
            messages = new ArrayList<TL_protoMessage>();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                TL_protoMessage message = new TL_protoMessage();
                message.msg_id = stream.readInt64();
                message.seqno = stream.readInt32();
                message.bytes = stream.readInt32();
                int constructor = stream.readInt32();
                TLObject request = ConnectionsManager.Instance.getRequestWithMessageId(message.msg_id);
                message.body = TLClassStore.Instance().TLdeserialize(stream, constructor, request);
                messages.add(message);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(messages.size());
            for (TLObject obj : messages) {
                TL_protoMessage proto = (TL_protoMessage)obj;
                stream.writeInt64(proto.msg_id);
                stream.writeInt32(proto.seqno);
                stream.writeInt32(proto.bytes);
                proto.body.serializeToStream(stream);
            }
        }
    }

    public static class TL_rpc_result extends TLObject {
        public static int constructor = 0xf35c6d01;

        public long req_msg_id;
        public TLObject result;

        public void readParams(AbsSerializedData stream) {
            req_msg_id = stream.readInt64();
            TLObject request = ConnectionsManager.Instance.getRequestWithMessageId(req_msg_id);
            result = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32(), request);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            result.serializeToStream(stream);
        }

        @Override
        public void freeResources() {
            if (result != null) {
                result.freeResources();
            }
        }
    }

    public static class TL_futuresalts extends TLObject {
        public static int constructor = 0xae500895;

        public long req_msg_id;
        public int now;
        public ArrayList<TL_futureSalt> salts = new ArrayList<TL_futureSalt>();

        public void readParams(AbsSerializedData stream) {
            req_msg_id = stream.readInt64();
            now = stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                TL_futureSalt salt = new TL_futureSalt();
                salt.readParams(stream);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            stream.writeInt32(now);
            int count = salts.size();
            stream.writeInt32(count);
            for (TL_futureSalt salt : salts) {
                salt.serializeToStream(stream);
            }
        }
    }

    public static class TL_gzip_packed extends TLObject {
        public static int constructor = 0x3072cfa1;

        public byte[] packed_data;

        public void readParams(AbsSerializedData stream) {
            packed_data = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(packed_data);
        }
    }

    public static class Message extends TLObject {
        public int id;
        public int fwd_from_id;
        public int fwd_date;
        public int from_id;
        public Peer to_id;
        public boolean out;
        public boolean unread;
        public int date;
        public String message;
        public MessageMedia media;
        public MessageAction action;
        public int send_state = 0;
        public int fwd_msg_id = 0;
        public String attachPath = "";
        public long random_id;
        public int local_id = 0;
        public long dialog_id;
        public int ttl;
    }

    public static class TL_messageForwarded extends Message {
        public static int constructor = 0x5f46804;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            fwd_from_id = stream.readInt32();
            fwd_date = stream.readInt32();
            from_id = stream.readInt32();
            to_id = (Peer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            out = stream.readBool();
            unread = stream.readBool();
            date = stream.readInt32();
            message = stream.readString();
            media = (MessageMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            if (id < 0) {
                fwd_msg_id = stream.readInt32();
            }
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && message != null && message.length() != 0 && message.equals("-1"))) {
                attachPath = stream.readString();
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(fwd_from_id);
            stream.writeInt32(fwd_date);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool(out);
            stream.writeBool(unread);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            if (id < 0) {
                stream.writeInt32(fwd_msg_id);
            }
            stream.writeString(attachPath);
        }
    }

    public static class TL_message extends Message {
        public static int constructor = 0x22eb6aba;

        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            from_id = stream.readInt32();
            to_id = (Peer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            out = stream.readBool();
            unread = stream.readBool();
            date = stream.readInt32();
            message = stream.readString();
            media = (MessageMedia)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && message != null && message.length() != 0 && message.equals("-1"))) {
                attachPath = stream.readString();
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool(out);
            stream.writeBool(unread);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeString(attachPath);
        }
    }

    public static class TL_messages_deleteMessages extends TLObject {
        public static int constructor = 0x14f2dd0a;

        public ArrayList<Integer> id = new ArrayList<Integer>();

        public Class responseClass () {
            return Vector.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (Integer anId : id) {
                stream.writeInt32(anId);
            }
        }

        public void parseVector(Vector vector, AbsSerializedData data) {
            int size = data.readInt32();
            for (int a = 0; a < size; a++) {
                vector.objects.add(data.readInt32());
            }
        }
    }

    public static class TL_messages_restoreMessages extends TLObject {
        public static int constructor = 0x395f9d7e;

        public ArrayList<Integer> id = new ArrayList<Integer>();

        public Class responseClass () {
            return Vector.class;
        }

        public void readParams(AbsSerializedData stream) {
            stream.readInt32();
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                id.add(stream.readInt32());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (Integer anId : id) {
                stream.writeInt32(anId);
            }
        }

        public void parseVector(Vector vector, AbsSerializedData data) {
            int size = data.readInt32();
            for (int a = 0; a < size; a++) {
                vector.objects.add(data.readInt32());
            }
        }
    }

    public static class TL_messages_receivedMessages extends TLObject {
        public static int constructor = 0x28abcb68;

        public int max_id;

        public Class responseClass () {
            return Vector.class;
        }

        public void readParams(AbsSerializedData stream) {
            max_id = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_id);
        }

        public void parseVector(Vector vector, AbsSerializedData data) {
            int size = data.readInt32();
            for (int a = 0; a < size; a++) {
                vector.objects.add(data.readInt32());
            }
        }
    }

    public static class Vector extends TLObject {
        public static int constructor = 0x1cb5c415;
        public ArrayList<Object> objects = new ArrayList<Object>();
    }

    public static class TL_userEmpty extends User {
        public static int constructor = 0x200250ba;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();

            first_name = "DELETED";
            last_name = "";
            phone = "";
            status = new TL_userStatusEmpty();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_chatEmpty extends Chat {
        public static int constructor = 0x9ba2d800;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();

            title = "DELETED";
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_userProfilePhotoOld extends UserProfilePhoto {
        public static int constructor = 0x990d1493;


        public void readParams(AbsSerializedData stream) {
            photo_small = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            photo_big = (FileLocation)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class TL_ping extends TLObject {
        public static int constructor = 0x7abe77ec;

        public long ping_id;

        public Class responseClass () {
            return TL_pong.class;
        }

        public int layer () {
            return 0;
        }

        public void readParams(AbsSerializedData stream) {
            ping_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(ping_id);
        }
    }

    public static class TL_ping_delay_disconnect extends TLObject {
        public static int constructor = 0xf3427b8c;

        public long ping_id;
        public int disconnect_delay;

        public Class responseClass () {
            return TL_pong.class;
        }

        public int layer () {
            return 0;
        }

        public void readParams(AbsSerializedData stream) {
            ping_id = stream.readInt64();
            disconnect_delay = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(ping_id);
            stream.writeInt32(disconnect_delay);
        }
    }

    public static class TL_destroy_session extends TLObject {
        public static int constructor = 0xe7512126;

        public long session_id;

        public Class responseClass () {
            return DestroySessionRes.class;
        }

        public int layer () {
            return 0;
        }

        public void readParams(AbsSerializedData stream) {
            session_id = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class TL_destroy_sessions extends TLObject {
        public static int constructor = 0xa13dc52f;

        public ArrayList<Long> session_ids = new ArrayList<Long>();

        public Class responseClass () {
            return TL_destroy_sessions_res.class;
        }

        public int layer () {
            return 0;
        }

        public void readParams(AbsSerializedData stream) {
            int count = stream.readInt32();
            for (int a = 0; a < count; a++) {
                session_ids.add(stream.readInt64());
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            int count = session_ids.size();
            stream.writeInt32(count);
            for (Long session_id : session_ids) {
                stream.writeInt64(session_id);
            }
        }
    }

    public static class TL_dialog extends TLObject {
        public static int constructor = 0x214a8cdf;

        public Peer peer;
        public int top_message;
        public int unread_count;
        public int last_message_date;
        public long id;
        public int last_read;

        public void readParams(AbsSerializedData stream) {
            peer = (Peer)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            top_message = stream.readInt32();
            unread_count = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(top_message);
            stream.writeInt32(unread_count);
        }
    }

    public static class EncryptedChat extends TLObject {
        public int id;
        public long access_hash;
        public int date;
        public int admin_id;
        public int participant_id;
        public byte[] g_a_or_b;
        public long key_fingerprint;
        public byte[] g_a;
        public byte[] a_or_b;
        public byte[] auth_key;
        public int user_id;
        public int ttl;
    }

    public static class FileLocation extends TLObject {
        public int dc_id;
        public long volume_id;
        public int local_id;
        public long secret;
        public byte[] key;
        public byte[] iv;
    }

    public static class TL_fileEncryptedLocation extends FileLocation {
        public static int constructor = 0x55555554;


        public void readParams(AbsSerializedData stream) {
            dc_id = stream.readInt32();
            volume_id = stream.readInt64();
            local_id = stream.readInt32();
            secret = stream.readInt64();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(dc_id);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class Video extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public String caption;
        public int duration;
        public int size;
        public PhotoSize thumb;
        public int dc_id;
        public int w;
        public int h;
        public String path;
        public byte[] key;
        public byte[] iv;
    }

    public static class Document extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public String file_name;
        public String mime_type;
        public int size;
        public PhotoSize thumb;
        public int dc_id;
        public String path;
        public byte[] key;
        public byte[] iv;
    }

    public static class Audio extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public int duration;
        public int size;
        public int dc_id;
        public String path;
        public byte[] key;
        public byte[] iv;
    }

    public static class MessageAction extends TLObject {
        public Photo photo;
        public UserProfilePhoto newUserPhoto;
        public int user_id;
        public String title;
        public ArrayList<Integer> users = new ArrayList<Integer>();
        public String address;
        public int ttl;
    }

    public static class TL_messageActionTTLChange extends MessageAction {
        public static int constructor = 0x55555552;

        public void readParams(AbsSerializedData stream) {
            ttl = stream.readInt32();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(ttl);
        }
    }

    public static class TL_documentEncrypted extends Document {
        public static int constructor = 0x55555556;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            file_name = stream.readString();
            mime_type = stream.readString();
            size = stream.readInt32();
            thumb = (PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            dc_id = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(file_name);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_videoEncrypted extends Video {
        public static int constructor = 0x55555553;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            caption = stream.readString();
            duration = stream.readInt32();
            size = stream.readInt32();
            thumb = (PhotoSize)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
            dc_id = stream.readInt32();
            w = stream.readInt32();
            h = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(caption);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_audioEncrypted extends Audio {
        public static int constructor = 0x555555F6;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt64();
            access_hash = stream.readInt64();
            user_id = stream.readInt32();
            date = stream.readInt32();
            duration = stream.readInt32();
            size = stream.readInt32();
            dc_id = stream.readInt32();
            key = stream.readByteArray();
            iv = stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            stream.writeInt32(dc_id);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_messageActionUserUpdatedPhoto extends MessageAction {
        public static int constructor = 0x55555551;

        public void readParams(AbsSerializedData stream) {
            newUserPhoto = (UserProfilePhoto)TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            newUserPhoto.serializeToStream(stream);
        }
    }

    public static class TL_messageActionUserJoined extends MessageAction {
        public static int constructor = 0x55555550;

        public void readParams(AbsSerializedData stream) {

        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionLoginUnknownLocation extends MessageAction {
        public static int constructor = 0x555555F5;

        public void readParams(AbsSerializedData stream) {
            title = stream.readString();
            address = stream.readString();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeString(address);
        }
    }

    public static class invokeWithLayer12 extends TLObject {
        public static int constructor = 0xdda60d3c;

        public TLObject query;

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            query.serializeToStream(stream);
        }
    }

    public static class initConnection extends TLObject {
        public static int constructor = 0x69796de9;

        public int api_id;
        public String device_model;
        public String system_version;
        public String app_version;
        public String lang_code;
        public TLObject query;

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(api_id);
            stream.writeString(device_model);
            stream.writeString(system_version);
            stream.writeString(app_version);
            stream.writeString(lang_code);
            query.serializeToStream(stream);
        }
    }

    public static class decryptedMessageLayer extends TLObject {
        public static int constructor = 0x99a438cf;

        public int layer;
        public TLObject message;

        public void readParams(AbsSerializedData stream) {
            layer = stream.readInt32();
            message = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32());
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(layer);
            message.serializeToStream(stream);
        }
    }

    public static class TL_encryptedChat_old extends TL_encryptedChat {
        public static int constructor = 0x6601d14f;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            date = stream.readInt32();
            admin_id = stream.readInt32();
            participant_id = stream.readInt32();
            g_a_or_b = stream.readByteArray();
            stream.readByteArray();
            key_fingerprint = stream.readInt64();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(TL_encryptedChat.constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a_or_b);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_encryptedChatRequested_old extends EncryptedChat {
        public static int constructor = 0xfda9a7b7;


        public void readParams(AbsSerializedData stream) {
            id = stream.readInt32();
            access_hash = stream.readInt64();
            date = stream.readInt32();
            admin_id = stream.readInt32();
            participant_id = stream.readInt32();
            g_a = stream.readByteArray();
            stream.readByteArray();
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(TL_encryptedChatRequested.constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a);
        }
    }
}
