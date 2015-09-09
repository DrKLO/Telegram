/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class TLRPC {

    public static final int USER_FLAG_ACCESS_HASH           = 0x00000001;
    public static final int USER_FLAG_FIRST_NAME            = 0x00000002;
    public static final int USER_FLAG_LAST_NAME             = 0x00000004;
    public static final int USER_FLAG_USERNAME              = 0x00000008;
    public static final int USER_FLAG_PHONE                 = 0x00000010;
    public static final int USER_FLAG_PHOTO                 = 0x00000020;
    public static final int USER_FLAG_STATUS                = 0x00000040;
    public static final int USER_FLAG_UNUSED                = 0x00000080;
    public static final int USER_FLAG_UNUSED2               = 0x00000100;
    public static final int USER_FLAG_UNUSED3               = 0x00000200;
    public static final int USER_FLAG_SELF                  = 0x00000400;
    public static final int USER_FLAG_CONTACT               = 0x00000800;
    public static final int USER_FLAG_MUTUAL_CONTACT        = 0x00001000;
    public static final int USER_FLAG_DELETED               = 0x00002000;
    public static final int USER_FLAG_BOT                   = 0x00004000;
    public static final int USER_FLAG_BOT_READING_HISTORY   = 0x00008000;
    public static final int USER_FLAG_BOT_CANT_JOIN_GROUP   = 0x00010000;


    public static final int MESSAGE_FLAG_UNREAD             = 0x00000001;
    public static final int MESSAGE_FLAG_OUT                = 0x00000002;
    public static final int MESSAGE_FLAG_FWD                = 0x00000004;
    public static final int MESSAGE_FLAG_REPLY              = 0x00000008;
    public static final int MESSAGE_FLAG_MENTION            = 0x00000010;
    public static final int MESSAGE_FLAG_CONTENT_UNREAD     = 0x00000020;
    public static final int MESSAGE_FLAG_HAS_MARKUP         = 0x00000040;
    public static final int MESSAGE_FLAG_HAS_ENTITIES       = 0x00000080;

    public static final int LAYER = 34;

    public static class TL_inputEncryptedChat extends TLObject {
        public static int constructor = 0xf141b5e1;

        public int chat_id;
        public long access_hash;

        public static TL_inputEncryptedChat TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputEncryptedChat.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputEncryptedChat", constructor));
                } else {
                    return null;
                }
            }
            TL_inputEncryptedChat result = new TL_inputEncryptedChat();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class WebPage extends TLObject {
        public long id;
        public int date;
        public int flags;
        public String url;
        public String display_url;
        public String type;
        public String site_name;
        public String title;
        public String description;
        public Photo photo;
        public String embed_url;
        public String embed_type;
        public int embed_width;
        public int embed_height;
        public int duration;
        public String author;

        public static WebPage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            WebPage result = null;
            switch(constructor) {
                case 0xc586da1c:
                    result = new TL_webPagePending();
                    break;
                case 0xeb1477e8:
                    result = new TL_webPageEmpty();
                    break;
                case 0xa31ea0b5:
                    result = new TL_webPage();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in WebPage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_webPagePending extends WebPage {
        public static int constructor = 0xc586da1c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(date);
        }
    }

    public static class TL_webPageEmpty extends WebPage {
        public static int constructor = 0xeb1477e8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_webPage extends WebPage {
        public static int constructor = 0xa31ea0b5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            url = stream.readString(exception);
            display_url = stream.readString(exception);
            if ((flags & 1) != 0) {
                type = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                site_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                title = stream.readString(exception);
            }
            if ((flags & 8) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                embed_url = stream.readString(exception);
            }
            if ((flags & 32) != 0) {
                embed_type = stream.readString(exception);
            }
            if ((flags & 64) != 0) {
                embed_width = stream.readInt32(exception);
            }
            if ((flags & 64) != 0) {
                embed_height = stream.readInt32(exception);
            }
            if ((flags & 128) != 0) {
                duration = stream.readInt32(exception);
            }
            if ((flags & 256) != 0) {
                author = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeString(url);
            stream.writeString(display_url);
            if ((flags & 1) != 0) {
                stream.writeString(type);
            }
            if ((flags & 2) != 0) {
                stream.writeString(site_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(title);
            }
            if ((flags & 8) != 0) {
                stream.writeString(description);
            }
            if ((flags & 16) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                stream.writeString(embed_url);
            }
            if ((flags & 32) != 0) {
                stream.writeString(embed_type);
            }
            if ((flags & 64) != 0) {
                stream.writeInt32(embed_width);
            }
            if ((flags & 64) != 0) {
                stream.writeInt32(embed_height);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(duration);
            }
            if ((flags & 256) != 0) {
                stream.writeString(author);
            }
        }
    }

    public static class TL_inputPeerNotifySettings extends TLObject {
        public static int constructor = 0x46a2ce98;

        public int mute_until;
        public String sound;
        public boolean show_previews;
        public int events_mask;

        public static TL_inputPeerNotifySettings TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputPeerNotifySettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputPeerNotifySettings", constructor));
                } else {
                    return null;
                }
            }
            TL_inputPeerNotifySettings result = new TL_inputPeerNotifySettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            mute_until = stream.readInt32(exception);
            sound = stream.readString(exception);
            show_previews = stream.readBool(exception);
            events_mask = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(mute_until);
            stream.writeString(sound);
            stream.writeBool(show_previews);
            stream.writeInt32(events_mask);
        }
    }

    public static class InputEncryptedFile extends TLObject {
        public long id;
        public long access_hash;
        public int parts;
        public int key_fingerprint;
        public String md5_checksum;

        public static InputEncryptedFile TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputEncryptedFile result = null;
            switch(constructor) {
                case 0x5a17b5e5:
                    result = new TL_inputEncryptedFile();
                    break;
                case 0x2dc173c8:
                    result = new TL_inputEncryptedFileBigUploaded();
                    break;
                case 0x1837c364:
                    result = new TL_inputEncryptedFileEmpty();
                    break;
                case 0x64bd0306:
                    result = new TL_inputEncryptedFileUploaded();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputEncryptedFile", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputEncryptedFile extends InputEncryptedFile {
        public static int constructor = 0x5a17b5e5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputEncryptedFileBigUploaded extends InputEncryptedFile {
        public static int constructor = 0x2dc173c8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            parts = stream.readInt32(exception);
            key_fingerprint = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            parts = stream.readInt32(exception);
            md5_checksum = stream.readString(exception);
            key_fingerprint = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeString(md5_checksum);
            stream.writeInt32(key_fingerprint);
        }
    }

    public static class MsgDetailedInfo extends TLObject {
        public long answer_msg_id;
        public int bytes;
        public int status;
        public long msg_id;

        public static MsgDetailedInfo TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            MsgDetailedInfo result = null;
            switch(constructor) {
                case 0x809db6df:
                    result = new TL_msg_new_detailed_info();
                    break;
                case 0x276d3ec6:
                    result = new TL_msg_detailed_info();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MsgDetailedInfo", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_msg_new_detailed_info extends MsgDetailedInfo {
        public static int constructor = 0x809db6df;


        public void readParams(AbsSerializedData stream, boolean exception) {
            answer_msg_id = stream.readInt64(exception);
            bytes = stream.readInt32(exception);
            status = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            msg_id = stream.readInt64(exception);
            answer_msg_id = stream.readInt64(exception);
            bytes = stream.readInt32(exception);
            status = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt64(answer_msg_id);
            stream.writeInt32(bytes);
            stream.writeInt32(status);
        }
    }

    public static class GeoPoint extends TLObject {
        public double _long;
        public double lat;

        public static GeoPoint TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            GeoPoint result = null;
            switch(constructor) {
                case 0x1117dd5f:
                    result = new TL_geoPointEmpty();
                    break;
                case 0x2049d70c:
                    result = new TL_geoPoint();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in GeoPoint", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_geoPointEmpty extends GeoPoint {
        public static int constructor = 0x1117dd5f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_geoPoint extends GeoPoint {
        public static int constructor = 0x2049d70c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            _long = stream.readDouble(exception);
            lat = stream.readDouble(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(_long);
            stream.writeDouble(lat);
        }
    }

    public static class TL_accountDaysTTL extends TLObject {
        public static int constructor = 0xb8d0afdf;

        public int days;

        public static TL_accountDaysTTL TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_accountDaysTTL.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_accountDaysTTL", constructor));
                } else {
                    return null;
                }
            }
            TL_accountDaysTTL result = new TL_accountDaysTTL();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            days = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(days);
        }
    }

    public static class TL_error extends TLObject {
        public static int constructor = 0xc4b9f9bb;

        public int code;
        public String text;

        public static TL_error TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_error.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_error", constructor));
                } else {
                    return null;
                }
            }
            TL_error result = new TL_error();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            code = stream.readInt32(exception);
            text = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(code);
            stream.writeString(text);
        }
    }

    public static class TL_messageEmpty extends Message {
        public static int constructor = 0x83e5de54;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_messageService extends Message {
        public static int constructor = 0x1d86f70e;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            action = MessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeInt32(date);
            action.serializeToStream(stream);
        }
    }

    public static class TL_messages_stickerSet extends TLObject {
        public static int constructor = 0xb60a24a6;

        public StickerSet set;
        public ArrayList<TL_stickerPack> packs = new ArrayList<>();
        public ArrayList<Document> documents = new ArrayList<>();

        public static TL_messages_stickerSet TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_stickerSet.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_stickerSet", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_stickerSet result = new TL_messages_stickerSet();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            set = StickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_stickerPack object = TL_stickerPack.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                packs.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Document object = Document.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                documents.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            set.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = packs.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                packs.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = documents.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                documents.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contactStatus extends TLObject {
        public static int constructor = 0xd3680c61;

        public int user_id;
        public UserStatus status;

        public static TL_contactStatus TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contactStatus.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contactStatus", constructor));
                } else {
                    return null;
                }
            }
            TL_contactStatus result = new TL_contactStatus();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            status.serializeToStream(stream);
        }
    }

    public static class TL_keyboardButtonRow extends TLObject {
        public static int constructor = 0x77608b83;

        public ArrayList<TL_keyboardButton> buttons = new ArrayList<>();

        public static TL_keyboardButtonRow TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_keyboardButtonRow.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_keyboardButtonRow", constructor));
                } else {
                    return null;
                }
            }
            TL_keyboardButtonRow result = new TL_keyboardButtonRow();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_keyboardButton object = TL_keyboardButton.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                buttons.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = buttons.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                buttons.get(a).serializeToStream(stream);
            }
        }
    }

    public static class InputDocument extends TLObject {
        public long id;
        public long access_hash;

        public static InputDocument TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputDocument result = null;
            switch(constructor) {
                case 0x72f0eaae:
                    result = new TL_inputDocumentEmpty();
                    break;
                case 0x18798952:
                    result = new TL_inputDocument();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputDocument", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputDocumentEmpty extends InputDocument {
        public static int constructor = 0x72f0eaae;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputDocument extends InputDocument {
        public static int constructor = 0x18798952;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class BotInfo extends TLObject {
        public int user_id;
        public int version;
        public String share_text;
        public String description;
        public ArrayList<TL_botCommand> commands = new ArrayList<>();

        public static BotInfo TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            BotInfo result = null;
            switch(constructor) {
                case 0xbb2e37ce:
                    result = new TL_botInfoEmpty();
                    break;
                case 0x9cf585d:
                    result = new TL_botInfo();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in BotInfo", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_botInfoEmpty extends BotInfo {
        public static int constructor = 0xbb2e37ce;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_botInfo extends BotInfo {
        public static int constructor = 0x9cf585d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            version = stream.readInt32(exception);
            share_text = stream.readString(exception);
            description = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_botCommand object = TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                commands.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(version);
            stream.writeString(share_text);
            stream.writeString(description);
            stream.writeInt32(0x1cb5c415);
            int count = commands.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                commands.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_auth_authorization extends TLObject {
        public static int constructor = 0xff036af1;

        public User user;

        public static TL_auth_authorization TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_auth_authorization.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_auth_authorization", constructor));
                } else {
                    return null;
                }
            }
            TL_auth_authorization result = new TL_auth_authorization();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user = User.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user.serializeToStream(stream);
        }
    }

    public static class Set_client_DH_params_answer extends TLObject {
        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] new_nonce_hash2;
        public byte[] new_nonce_hash3;
        public byte[] new_nonce_hash1;

        public static Set_client_DH_params_answer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Set_client_DH_params_answer result = null;
            switch(constructor) {
                case 0x46dc1fb9:
                    result = new TL_dh_gen_retry();
                    break;
                case 0xa69dae02:
                    result = new TL_dh_gen_fail();
                    break;
                case 0x3bcbf734:
                    result = new TL_dh_gen_ok();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Set_client_DH_params_answer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_dh_gen_retry extends Set_client_DH_params_answer {
        public static int constructor = 0x46dc1fb9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            new_nonce_hash2 = stream.readData(16, exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            new_nonce_hash3 = stream.readData(16, exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            new_nonce_hash1 = stream.readData(16, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeRaw(new_nonce_hash1);
        }
    }

    public static class InputMedia extends TLObject {
        public String phone_number;
        public String first_name;
        public String last_name;
        public InputFile file;
        public InputFile thumb;
        public String mime_type;
        public ArrayList<DocumentAttribute> attributes = new ArrayList<>();
        public String caption;
        public InputGeoPoint geo_point;
        public int duration;
        public int w;
        public int h;
        public String title;
        public String address;
        public String provider;
        public String venue_id;

        public static InputMedia TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputMedia result = null;
            switch(constructor) {
                case 0xa6e45987:
                    result = new TL_inputMediaContact();
                    break;
                case 0x41481486:
                    result = new TL_inputMediaUploadedThumbDocument();
                    break;
                case 0x89938781:
                    result = new TL_inputMediaAudio();
                    break;
                case 0xd184e841:
                    result = new TL_inputMediaDocument();
                    break;
                case 0x936a4ebd:
                    result = new TL_inputMediaVideo();
                    break;
                case 0xf9c44144:
                    result = new TL_inputMediaGeoPoint();
                    break;
                case 0x9664f57f:
                    result = new TL_inputMediaEmpty();
                    break;
                case 0x96fb97dc:
                    result = new TL_inputMediaUploadedThumbVideo();
                    break;
                case 0xf7aff1c0:
                    result = new TL_inputMediaUploadedPhoto();
                    break;
                case 0x2827a81a:
                    result = new TL_inputMediaVenue();
                    break;
                case 0x4e498cab:
                    result = new TL_inputMediaUploadedAudio();
                    break;
                case 0xe13fd4bc:
                    result = new TL_inputMediaUploadedVideo();
                    break;
                case 0xffe76b78:
                    result = new TL_inputMediaUploadedDocument();
                    break;
                case 0xe9bfb4f3:
                    result = new TL_inputMediaPhoto();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputMedia", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputMediaContact extends InputMedia {
        public static int constructor = 0xa6e45987;


        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_number = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_inputMediaUploadedThumbDocument extends InputMedia {
        public static int constructor = 0x41481486;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            thumb = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            mime_type = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                attributes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            thumb.serializeToStream(stream);
            stream.writeString(mime_type);
            stream.writeInt32(0x1cb5c415);
            int count = attributes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                attributes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_inputMediaAudio extends InputMedia {
        public static int constructor = 0x89938781;

        public InputAudio id;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = InputAudio.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaDocument extends InputMedia {
        public static int constructor = 0xd184e841;

        public InputDocument id;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = InputDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_inputMediaVideo extends InputMedia {
        public static int constructor = 0x936a4ebd;

        public InputVideo id;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = InputVideo.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
            stream.writeString(caption);
        }
    }

    public static class TL_inputMediaGeoPoint extends InputMedia {
        public static int constructor = 0xf9c44144;


        public void readParams(AbsSerializedData stream, boolean exception) {
            geo_point = InputGeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
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
        public static int constructor = 0x96fb97dc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            thumb = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            duration = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            thumb.serializeToStream(stream);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeString(caption);
        }
    }

    public static class TL_inputMediaUploadedPhoto extends InputMedia {
        public static int constructor = 0xf7aff1c0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(caption);
        }
    }

    public static class TL_inputMediaVenue extends InputMedia {
        public static int constructor = 0x2827a81a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            geo_point = InputGeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            title = stream.readString(exception);
            address = stream.readString(exception);
            provider = stream.readString(exception);
            venue_id = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo_point.serializeToStream(stream);
            stream.writeString(title);
            stream.writeString(address);
            stream.writeString(provider);
            stream.writeString(venue_id);
        }
    }

    public static class TL_inputMediaUploadedAudio extends InputMedia {
        public static int constructor = 0x4e498cab;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
        }
    }

    public static class TL_inputMediaUploadedVideo extends InputMedia {
        public static int constructor = 0xe13fd4bc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            duration = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeString(caption);
        }
    }

    public static class TL_inputMediaUploadedDocument extends InputMedia {
        public static int constructor = 0xffe76b78;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            mime_type = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                attributes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(mime_type);
            stream.writeInt32(0x1cb5c415);
            int count = attributes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                attributes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_inputMediaPhoto extends InputMedia {
        public static int constructor = 0xe9bfb4f3;

        public InputPhoto id;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = InputPhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
            stream.writeString(caption);
        }
    }

    public static class TL_geochats_statedMessage extends TLObject {
        public static int constructor = 0x17b1578b;

        public GeoChatMessage message;
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public int seq;

        public static TL_geochats_statedMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_geochats_statedMessage.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_geochats_statedMessage", constructor));
                } else {
                    return null;
                }
            }
            TL_geochats_statedMessage result = new TL_geochats_statedMessage();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            message = GeoChatMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
            seq = stream.readInt32(exception);
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

    public static class messages_Dialogs extends TLObject {
        public ArrayList<TL_dialog> dialogs = new ArrayList<>();
        public ArrayList<Message> messages = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public int count;

        public static messages_Dialogs TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_Dialogs result = null;
            switch(constructor) {
                case 0x15ba6c40:
                    result = new TL_messages_dialogs();
                    break;
                case 0x71e094f3:
                    result = new TL_messages_dialogsSlice();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_Dialogs", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_dialogs extends messages_Dialogs {
        public static int constructor = 0x15ba6c40;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_dialog object = TL_dialog.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                dialogs.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_dialog object = TL_dialog.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                dialogs.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_server_DH_inner_data extends TLObject {
        public static int constructor = 0xb5890dba;

        public byte[] nonce;
        public byte[] server_nonce;
        public int g;
        public byte[] dh_prime;
        public byte[] g_a;
        public int server_time;

        public static TL_server_DH_inner_data TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_server_DH_inner_data.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_server_DH_inner_data", constructor));
                } else {
                    return null;
                }
            }
            TL_server_DH_inner_data result = new TL_server_DH_inner_data();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            g = stream.readInt32(exception);
            dh_prime = stream.readByteArray(exception);
            g_a = stream.readByteArray(exception);
            server_time = stream.readInt32(exception);
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

    public static class InputPrivacyRule extends TLObject {
        public ArrayList<InputUser> users = new ArrayList<>();

        public static InputPrivacyRule TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputPrivacyRule result = null;
            switch(constructor) {
                case 0x90110467:
                    result = new TL_inputPrivacyValueDisallowUsers();
                    break;
                case 0xd66b66c9:
                    result = new TL_inputPrivacyValueDisallowAll();
                    break;
                case 0xba52007:
                    result = new TL_inputPrivacyValueDisallowContacts();
                    break;
                case 0x184b35ce:
                    result = new TL_inputPrivacyValueAllowAll();
                    break;
                case 0xd09e07b:
                    result = new TL_inputPrivacyValueAllowContacts();
                    break;
                case 0x131cc67f:
                    result = new TL_inputPrivacyValueAllowUsers();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputPrivacyRule", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputPrivacyValueDisallowUsers extends InputPrivacyRule {
        public static int constructor = 0x90110467;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                InputUser object = InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_inputPrivacyValueDisallowAll extends InputPrivacyRule {
        public static int constructor = 0xd66b66c9;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPrivacyValueDisallowContacts extends InputPrivacyRule {
        public static int constructor = 0xba52007;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPrivacyValueAllowAll extends InputPrivacyRule {
        public static int constructor = 0x184b35ce;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPrivacyValueAllowContacts extends InputPrivacyRule {
        public static int constructor = 0xd09e07b;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPrivacyValueAllowUsers extends InputPrivacyRule {
        public static int constructor = 0x131cc67f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                InputUser object = InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_link extends TLObject {
        public static int constructor = 0x3ace484c;

        public ContactLink my_link;
        public ContactLink foreign_link;
        public User user;

        public static TL_contacts_link TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contacts_link.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contacts_link", constructor));
                } else {
                    return null;
                }
            }
            TL_contacts_link result = new TL_contacts_link();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            my_link = ContactLink.TLdeserialize(stream, stream.readInt32(exception), exception);
            foreign_link = ContactLink.TLdeserialize(stream, stream.readInt32(exception), exception);
            user = User.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            my_link.serializeToStream(stream);
            foreign_link.serializeToStream(stream);
            user.serializeToStream(stream);
        }
    }

    public static class photos_Photos extends TLObject {
        public ArrayList<Photo> photos = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public int count;

        public static photos_Photos TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            photos_Photos result = null;
            switch(constructor) {
                case 0x8dca6aa5:
                    result = new TL_photos_photos();
                    break;
                case 0x15051f54:
                    result = new TL_photos_photosSlice();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in photos_Photos", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_photos_photos extends photos_Photos {
        public static int constructor = 0x8dca6aa5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Photo object = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                photos.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Photo object = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                photos.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_msgs_ack extends TLObject {
        public static int constructor = 0x62d6b459;

        public ArrayList<Long> msg_ids = new ArrayList<>();

        public static TL_msgs_ack TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msgs_ack.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msgs_ack", constructor));
                } else {
                    return null;
                }
            }
            TL_msgs_ack result = new TL_msgs_ack();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(msg_ids.get(a));
            }
        }
    }

    public static class UserStatus extends TLObject {
        public int expires;

        public static UserStatus TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            UserStatus result = null;
            switch(constructor) {
                case 0x8c703f:
                    result = new TL_userStatusOffline();
                    break;
                case 0x7bf09fc:
                    result = new TL_userStatusLastWeek();
                    break;
                case 0x9d05049:
                    result = new TL_userStatusEmpty();
                    break;
                case 0x77ebc742:
                    result = new TL_userStatusLastMonth();
                    break;
                case 0xedb93949:
                    result = new TL_userStatusOnline();
                    break;
                case 0xe26f42f1:
                    result = new TL_userStatusRecently();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in UserStatus", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_userStatusOffline extends UserStatus {
        public static int constructor = 0x8c703f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            expires = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(expires);
        }
    }

    public static class TL_userStatusLastWeek extends UserStatus {
        public static int constructor = 0x7bf09fc;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userStatusEmpty extends UserStatus {
        public static int constructor = 0x9d05049;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userStatusLastMonth extends UserStatus {
        public static int constructor = 0x77ebc742;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userStatusOnline extends UserStatus {
        public static int constructor = 0xedb93949;


        public void readParams(AbsSerializedData stream, boolean exception) {
            expires = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(expires);
        }
    }

    public static class TL_userStatusRecently extends UserStatus {
        public static int constructor = 0xe26f42f1;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userFull extends TLObject {
        public static int constructor = 0x5a89ac5b;

        public User user;
        public TL_contacts_link link;
        public Photo profile_photo;
        public PeerNotifySettings notify_settings;
        public boolean blocked;
        public BotInfo bot_info;

        public static TL_userFull TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_userFull.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_userFull", constructor));
                } else {
                    return null;
                }
            }
            TL_userFull result = new TL_userFull();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user = User.TLdeserialize(stream, stream.readInt32(exception), exception);
            link = TL_contacts_link.TLdeserialize(stream, stream.readInt32(exception), exception);
            profile_photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            notify_settings = PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            blocked = stream.readBool(exception);
            bot_info = BotInfo.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user.serializeToStream(stream);
            link.serializeToStream(stream);
            profile_photo.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
            stream.writeBool(blocked);
            bot_info.serializeToStream(stream);
        }
    }

    public static class TL_msg_resend_req extends TLObject {
        public static int constructor = 0x7d861a08;

        public ArrayList<Long> msg_ids = new ArrayList<>();

        public static TL_msg_resend_req TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msg_resend_req.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msg_resend_req", constructor));
                } else {
                    return null;
                }
            }
            TL_msg_resend_req result = new TL_msg_resend_req();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(msg_ids.get(a));
            }
        }
    }

    public static class TL_contact extends TLObject {
        public static int constructor = 0xf911c994;

        public int user_id;
        public boolean mutual;

        public static TL_contact TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contact.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contact", constructor));
                } else {
                    return null;
                }
            }
            TL_contact result = new TL_contact();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            mutual = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeBool(mutual);
        }
    }

    public static class TL_chatLocated extends TLObject {
        public static int constructor = 0x3631cf4c;

        public int chat_id;
        public int distance;

        public static TL_chatLocated TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_chatLocated.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatLocated", constructor));
                } else {
                    return null;
                }
            }
            TL_chatLocated result = new TL_chatLocated();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            distance = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(distance);
        }
    }

    public static class RpcError extends TLObject {
        public int error_code;
        public String error_message;
        public long query_id;

        public static RpcError TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            RpcError result = null;
            switch(constructor) {
                case 0x2144ca19:
                    result = new TL_rpc_error();
                    break;
                case 0x7ae432f5:
                    result = new TL_rpc_req_error();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in RpcError", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_rpc_error extends RpcError {
        public static int constructor = 0x2144ca19;


        public void readParams(AbsSerializedData stream, boolean exception) {
            error_code = stream.readInt32(exception);
            error_message = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(error_code);
            stream.writeString(error_message);
        }
    }

    public static class TL_rpc_req_error extends RpcError {
        public static int constructor = 0x7ae432f5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            query_id = stream.readInt64(exception);
            error_code = stream.readInt32(exception);
            error_message = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(query_id);
            stream.writeInt32(error_code);
            stream.writeString(error_message);
        }
    }

    public static class TL_privacyKeyStatusTimestamp extends TLObject {
        public static int constructor = 0xbc2eab30;


        public static TL_privacyKeyStatusTimestamp TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_privacyKeyStatusTimestamp.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_privacyKeyStatusTimestamp", constructor));
                } else {
                    return null;
                }
            }
            TL_privacyKeyStatusTimestamp result = new TL_privacyKeyStatusTimestamp();
            result.readParams(stream, exception);
            return result;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class ChatParticipants extends TLObject {
        public int chat_id;
        public int admin_id;
        public ArrayList<TL_chatParticipant> participants = new ArrayList<>();
        public int version;

        public static ChatParticipants TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ChatParticipants result = null;
            switch(constructor) {
                case 0x7841b415:
                    result = new TL_chatParticipants();
                    break;
                case 0xfd2bb8a:
                    result = new TL_chatParticipantsForbidden();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ChatParticipants", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatParticipants extends ChatParticipants {
        public static int constructor = 0x7841b415;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_chatParticipant object = TL_chatParticipant.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                participants.add(object);
            }
            version = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(admin_id);
            stream.writeInt32(0x1cb5c415);
            int count = participants.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                participants.get(a).serializeToStream(stream);
            }
            stream.writeInt32(version);
        }
    }

    public static class TL_chatParticipantsForbidden extends ChatParticipants {
        public static int constructor = 0xfd2bb8a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_auth_passwordRecovery extends TLObject {
        public static int constructor = 0x137948a5;

        public String email_pattern;

        public static TL_auth_passwordRecovery TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_auth_passwordRecovery.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_auth_passwordRecovery", constructor));
                } else {
                    return null;
                }
            }
            TL_auth_passwordRecovery result = new TL_auth_passwordRecovery();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            email_pattern = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(email_pattern);
        }
    }

    public static class DecryptedMessage extends TLObject {
        public long random_id;
        public DecryptedMessageAction action;
        public byte[] random_bytes;
        public String message;
        public DecryptedMessageMedia media;
        public int ttl;

        public static DecryptedMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            DecryptedMessage result = null;
            switch(constructor) {
                case 0x73164160:
                    result = new TL_decryptedMessageService();
                    break;
                case 0x1f814f1f:
                    result = new TL_decryptedMessage_old();
                    break;
                case 0x204d3878:
                    result = new TL_decryptedMessage();
                    break;
                case 0xaa48327d:
                    result = new TL_decryptedMessageService_old();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in DecryptedMessage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_decryptedMessageService extends DecryptedMessage {
        public static int constructor = 0x73164160;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            action = DecryptedMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            action.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessage_old extends TL_decryptedMessage {
        public static int constructor = 0x1f814f1f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            random_bytes = stream.readByteArray(exception);
            message = stream.readString(exception);
            media = DecryptedMessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeByteArray(random_bytes);
            stream.writeString(message);
            media.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessage extends DecryptedMessage {
        public static int constructor = 0x204d3878;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            ttl = stream.readInt32(exception);
            message = stream.readString(exception);
            media = DecryptedMessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeInt32(ttl);
            stream.writeString(message);
            media.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessageService_old extends TL_decryptedMessageService {
        public static int constructor = 0xaa48327d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            random_bytes = stream.readByteArray(exception);
            action = DecryptedMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeByteArray(random_bytes);
            action.serializeToStream(stream);
        }
    }

    public static class messages_Messages extends TLObject {
        public ArrayList<Message> messages = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public int count;

        public static messages_Messages TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_Messages result = null;
            switch(constructor) {
                case 0x8c718e87:
                    result = new TL_messages_messages();
                    break;
                case 0xb446ae3:
                    result = new TL_messages_messagesSlice();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_Messages", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_messages extends messages_Messages {
        public static int constructor = 0x8c718e87;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class BadMsgNotification extends TLObject {
        public long bad_msg_id;
        public int bad_msg_seqno;
        public int error_code;
        public long new_server_salt;

        public static BadMsgNotification TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            BadMsgNotification result = null;
            switch(constructor) {
                case 0xa7eff811:
                    result = new TL_bad_msg_notification();
                    break;
                case 0xedab447b:
                    result = new TL_bad_server_salt();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in BadMsgNotification", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_bad_msg_notification extends BadMsgNotification {
        public static int constructor = 0xa7eff811;


        public void readParams(AbsSerializedData stream, boolean exception) {
            bad_msg_id = stream.readInt64(exception);
            bad_msg_seqno = stream.readInt32(exception);
            error_code = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            bad_msg_id = stream.readInt64(exception);
            bad_msg_seqno = stream.readInt32(exception);
            error_code = stream.readInt32(exception);
            new_server_salt = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bad_msg_id);
            stream.writeInt32(bad_msg_seqno);
            stream.writeInt32(error_code);
            stream.writeInt64(new_server_salt);
        }
    }

    public static class DecryptedMessageMedia extends TLObject {
        public int thumb_w;
        public int thumb_h;
        public String file_name;
        public String mime_type;
        public int size;
        public byte[] key;
        public byte[] iv;
        public long id;
        public long access_hash;
        public int date;
        public int dc_id;
        public ArrayList<DocumentAttribute> attributes = new ArrayList<>();
        public int duration;
        public double lat;
        public double _long;
        public int w;
        public int h;
        public String phone_number;
        public String first_name;
        public String last_name;
        public int user_id;

        public static DecryptedMessageMedia TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            DecryptedMessageMedia result = null;
            switch(constructor) {
                case 0x89f5c4a:
                    result = new TL_decryptedMessageMediaEmpty();
                    break;
                case 0xb095434b:
                    result = new TL_decryptedMessageMediaDocument();
                    break;
                case 0xfa95b0dd:
                    result = new TL_decryptedMessageMediaExternalDocument();
                    break;
                case 0x6080758f:
                    result = new TL_decryptedMessageMediaAudio_old();
                    break;
                case 0x35480a59:
                    result = new TL_decryptedMessageMediaGeoPoint();
                    break;
                case 0x57e0a9cb:
                    result = new TL_decryptedMessageMediaAudio();
                    break;
                case 0x524a415d:
                    result = new TL_decryptedMessageMediaVideo();
                    break;
                case 0x588a0a97:
                    result = new TL_decryptedMessageMediaContact();
                    break;
                case 0x32798a8c:
                    result = new TL_decryptedMessageMediaPhoto();
                    break;
                case 0x4cee6ef3:
                    result = new TL_decryptedMessageMediaVideo_old();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in DecryptedMessageMedia", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_decryptedMessageMediaEmpty extends DecryptedMessageMedia {
        public static int constructor = 0x89f5c4a;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_decryptedMessageMediaDocument extends DecryptedMessageMedia {
        public static int constructor = 0xb095434b;

        public byte[] thumb;

        public void readParams(AbsSerializedData stream, boolean exception) {
            thumb = stream.readByteArray(exception);
            thumb_w = stream.readInt32(exception);
            thumb_h = stream.readInt32(exception);
            file_name = stream.readString(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_decryptedMessageMediaExternalDocument extends DecryptedMessageMedia {
        public static int constructor = 0xfa95b0dd;

        public PhotoSize thumb;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                attributes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(0x1cb5c415);
            int count = attributes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                attributes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_decryptedMessageMediaAudio_old extends TL_decryptedMessageMediaAudio {
        public static int constructor = 0x6080758f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaGeoPoint extends DecryptedMessageMedia {
        public static int constructor = 0x35480a59;


        public void readParams(AbsSerializedData stream, boolean exception) {
            lat = stream.readDouble(exception);
            _long = stream.readDouble(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(lat);
            stream.writeDouble(_long);
        }
    }

    public static class TL_decryptedMessageMediaAudio extends DecryptedMessageMedia {
        public static int constructor = 0x57e0a9cb;


        public void readParams(AbsSerializedData stream, boolean exception) {
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaVideo extends DecryptedMessageMedia {
        public static int constructor = 0x524a415d;

        public byte[] thumb;

        public void readParams(AbsSerializedData stream, boolean exception) {
            thumb = stream.readByteArray(exception);
            thumb_w = stream.readInt32(exception);
            thumb_h = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(thumb);
            stream.writeInt32(thumb_w);
            stream.writeInt32(thumb_h);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(w);
            stream.writeInt32(h);
            stream.writeInt32(size);
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_decryptedMessageMediaContact extends DecryptedMessageMedia {
        public static int constructor = 0x588a0a97;


        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_number = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_decryptedMessageMediaPhoto extends DecryptedMessageMedia {
        public static int constructor = 0x32798a8c;

        public byte[] thumb;

        public void readParams(AbsSerializedData stream, boolean exception) {
            thumb = stream.readByteArray(exception);
            thumb_w = stream.readInt32(exception);
            thumb_h = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_decryptedMessageMediaVideo_old extends TL_decryptedMessageMediaVideo {
        public static int constructor = 0x4cee6ef3;

        public byte[] thumb;

        public void readParams(AbsSerializedData stream, boolean exception) {
            thumb = stream.readByteArray(exception);
            thumb_w = stream.readInt32(exception);
            thumb_h = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            size = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class User extends TLObject {
        public int id;
        public String first_name;
        public String last_name;
        public String username;
        public long access_hash;
        public String phone;
        public UserProfilePhoto photo;
        public UserStatus status;
        public boolean inactive;
        public int flags;
        public int bot_info_version;

        public static User TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            User result = null;
            switch(constructor) {
                case 0xcab35e18:
                    result = new TL_userContact_old2();
                    break;
                case 0xf2fb8319:
                    result = new TL_userContact_old();
                    break;
                case 0x720535ec:
                    result = new TL_userSelf_old();
                    break;
                case 0x1c60e608:
                    result = new TL_userSelf_old3();
                    break;
                case 0xd6016d7a:
                    result = new TL_userDeleted_old2();
                    break;
                case 0x200250ba:
                    result = new TL_userEmpty();
                    break;
                case 0x22e8ceb0:
                    result = new TL_userRequest_old();
                    break;
                case 0x5214c89d:
                    result = new TL_userForeign_old();
                    break;
                case 0x75cf7a8:
                    result = new TL_userForeign_old2();
                    break;
                case 0xd9ccc4ef:
                    result = new TL_userRequest_old2();
                    break;
                case 0xb29ad7cc:
                    result = new TL_userDeleted_old();
                    break;
                case 0x7007b451:
                    result = new TL_userSelf_old2();
                    break;
                case 0x22e49072:
                    result = new TL_user();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in User", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_userContact_old2 extends User {
        public static int constructor = 0xcab35e18;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
            stream.writeInt64(access_hash);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userContact_old extends TL_userContact_old2 {
        public static int constructor = 0xf2fb8319;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class TL_userSelf_old extends TL_userSelf_old2 {
        public static int constructor = 0x720535ec;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
            inactive = stream.readBool(exception);
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

    public static class TL_userSelf_old3 extends User {
        public static int constructor = 0x1c60e608;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userDeleted_old2 extends User {
        public static int constructor = 0xd6016d7a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
        }
    }

    public static class TL_userEmpty extends User {
        public static int constructor = 0x200250ba;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_userRequest_old extends TL_userRequest_old2 {
        public static int constructor = 0x22e8ceb0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class TL_userForeign_old extends TL_userForeign_old2 {
        public static int constructor = 0x5214c89d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class TL_userForeign_old2 extends User {
        public static int constructor = 0x75cf7a8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
            stream.writeInt64(access_hash);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userRequest_old2 extends User {
        public static int constructor = 0xd9ccc4ef;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
            access_hash = stream.readInt64(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
            stream.writeInt64(access_hash);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
        }
    }

    public static class TL_userDeleted_old extends TL_userDeleted_old2 {
        public static int constructor = 0xb29ad7cc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class TL_userSelf_old2 extends TL_userSelf_old3 {
        public static int constructor = 0x7007b451;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
            phone = stream.readString(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
            inactive = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
            stream.writeString(phone);
            photo.serializeToStream(stream);
            status.serializeToStream(stream);
            stream.writeBool(inactive);
        }
    }

    public static class TL_user extends User {
        public static int constructor = 0x22e49072;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                access_hash = stream.readInt64(exception);
            }
            if ((flags & 2) != 0) {
                first_name = stream.readString(exception);
            }
            if ((flags & 4) != 0) {
                last_name = stream.readString(exception);
            }
            if ((flags & 8) != 0) {
                username = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                phone = stream.readString(exception);
            }
            if ((flags & 32) != 0) {
                photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 64) != 0) {
                status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 16384) != 0) {
                bot_info_version = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if ((flags & 1) != 0) {
                stream.writeInt64(access_hash);
            }
            if ((flags & 2) != 0) {
                stream.writeString(first_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(last_name);
            }
            if ((flags & 8) != 0) {
                stream.writeString(username);
            }
            if ((flags & 16) != 0) {
                stream.writeString(phone);
            }
            if ((flags & 32) != 0) {
                photo.serializeToStream(stream);
            }
            if ((flags & 64) != 0) {
                status.serializeToStream(stream);
            }
            if ((flags & 16384) != 0) {
                stream.writeInt32(bot_info_version);
            }
        }
    }

    public static class MessageMedia extends TLObject {
        public byte[] bytes;
        public Video video;
        public String caption;
        public Photo photo;
        public Audio audio;
        public GeoPoint geo;
        public String title;
        public String address;
        public String provider;
        public String venue_id;
        public Document document;
        public String phone_number;
        public String first_name;
        public String last_name;
        public int user_id;
        public WebPage webpage;

        public static MessageMedia TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            MessageMedia result = null;
            switch(constructor) {
                case 0x29632a36:
                    result = new TL_messageMediaUnsupported_old();
                    break;
                case 0x5bcf1675:
                    result = new TL_messageMediaVideo();
                    break;
                case 0xc8c45a2a:
                    result = new TL_messageMediaPhoto_old();
                    break;
                case 0xc6b68300:
                    result = new TL_messageMediaAudio();
                    break;
                case 0x9f84f49e:
                    result = new TL_messageMediaUnsupported();
                    break;
                case 0x3ded6320:
                    result = new TL_messageMediaEmpty();
                    break;
                case 0x7912b71f:
                    result = new TL_messageMediaVenue();
                    break;
                case 0xa2d24290:
                    result = new TL_messageMediaVideo_old();
                    break;
                case 0x2fda2204:
                    result = new TL_messageMediaDocument();
                    break;
                case 0x5e7d2f39:
                    result = new TL_messageMediaContact();
                    break;
                case 0x3d8ce53d:
                    result = new TL_messageMediaPhoto();
                    break;
                case 0xa32dd600:
                    result = new TL_messageMediaWebPage();
                    break;
                case 0x56e0d474:
                    result = new TL_messageMediaGeo();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MessageMedia", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messageMediaUnsupported_old extends TL_messageMediaUnsupported {
        public static int constructor = 0x29632a36;


        public void readParams(AbsSerializedData stream, boolean exception) {
            bytes = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_messageMediaVideo extends MessageMedia {
        public static int constructor = 0x5bcf1675;


        public void readParams(AbsSerializedData stream, boolean exception) {
            video = Video.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            video.serializeToStream(stream);
            stream.writeString(caption);
        }
    }

    public static class TL_messageMediaPhoto_old extends TL_messageMediaPhoto {
        public static int constructor = 0xc8c45a2a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaAudio extends MessageMedia {
        public static int constructor = 0xc6b68300;


        public void readParams(AbsSerializedData stream, boolean exception) {
            audio = Audio.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            audio.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaUnsupported extends MessageMedia {
        public static int constructor = 0x9f84f49e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageMediaEmpty extends MessageMedia {
        public static int constructor = 0x3ded6320;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageMediaVenue extends MessageMedia {
        public static int constructor = 0x7912b71f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            geo = GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            title = stream.readString(exception);
            address = stream.readString(exception);
            provider = stream.readString(exception);
            venue_id = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo.serializeToStream(stream);
            stream.writeString(title);
            stream.writeString(address);
            stream.writeString(provider);
            stream.writeString(venue_id);
        }
    }

    public static class TL_messageMediaVideo_old extends TL_messageMediaVideo {
        public static int constructor = 0xa2d24290;


        public void readParams(AbsSerializedData stream, boolean exception) {
            video = Video.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            video.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaDocument extends MessageMedia {
        public static int constructor = 0x2fda2204;


        public void readParams(AbsSerializedData stream, boolean exception) {
            document = Document.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            document.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaContact extends MessageMedia {
        public static int constructor = 0x5e7d2f39;


        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_number = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageMediaPhoto extends MessageMedia {
        public static int constructor = 0x3d8ce53d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            caption = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
            stream.writeString(caption);
        }
    }

    public static class TL_messageMediaWebPage extends MessageMedia {
        public static int constructor = 0xa32dd600;


        public void readParams(AbsSerializedData stream, boolean exception) {
            webpage = WebPage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            webpage.serializeToStream(stream);
        }
    }

    public static class TL_messageMediaGeo extends MessageMedia {
        public static int constructor = 0x56e0d474;


        public void readParams(AbsSerializedData stream, boolean exception) {
            geo = GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            geo.serializeToStream(stream);
        }
    }

    public static class TL_nearestDc extends TLObject {
        public static int constructor = 0x8e1a1775;

        public String country;
        public int this_dc;
        public int nearest_dc;

        public static TL_nearestDc TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_nearestDc.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_nearestDc", constructor));
                } else {
                    return null;
                }
            }
            TL_nearestDc result = new TL_nearestDc();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            country = stream.readString(exception);
            this_dc = stream.readInt32(exception);
            nearest_dc = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(country);
            stream.writeInt32(this_dc);
            stream.writeInt32(nearest_dc);
        }
    }

    public static class TL_contactFound extends TLObject {
        public static int constructor = 0xea879f95;

        public int user_id;

        public static TL_contactFound TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contactFound.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contactFound", constructor));
                } else {
                    return null;
                }
            }
            TL_contactFound result = new TL_contactFound();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_new_session_created extends TLObject {
        public static int constructor = 0x9ec20908;

        public long first_msg_id;
        public long unique_id;
        public long server_salt;

        public static TL_new_session_created TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_new_session_created.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_new_session_created", constructor));
                } else {
                    return null;
                }
            }
            TL_new_session_created result = new TL_new_session_created();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            first_msg_id = stream.readInt64(exception);
            unique_id = stream.readInt64(exception);
            server_salt = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(first_msg_id);
            stream.writeInt64(unique_id);
            stream.writeInt64(server_salt);
        }
    }

    public static class TL_contacts_suggested extends TLObject {
        public static int constructor = 0x5649dcc5;

        public ArrayList<TL_contactSuggested> results = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_contacts_suggested TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contacts_suggested.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contacts_suggested", constructor));
                } else {
                    return null;
                }
            }
            TL_contacts_suggested result = new TL_contacts_suggested();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contactSuggested object = TL_contactSuggested.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                results.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class WallPaper extends TLObject {
        public int id;
        public String title;
        public ArrayList<PhotoSize> sizes = new ArrayList<>();
        public int color;
        public int bg_color;

        public static WallPaper TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            WallPaper result = null;
            switch(constructor) {
                case 0xccb03657:
                    result = new TL_wallPaper();
                    break;
                case 0x63117f24:
                    result = new TL_wallPaperSolid();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in WallPaper", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_wallPaper extends WallPaper {
        public static int constructor = 0xccb03657;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            title = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PhotoSize object = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sizes.add(object);
            }
            color = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            stream.writeInt32(0x1cb5c415);
            int count = sizes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                sizes.get(a).serializeToStream(stream);
            }
            stream.writeInt32(color);
        }
    }

    public static class TL_wallPaperSolid extends WallPaper {
        public static int constructor = 0x63117f24;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            title = stream.readString(exception);
            bg_color = stream.readInt32(exception);
            color = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeString(title);
            stream.writeInt32(bg_color);
            stream.writeInt32(color);
        }
    }

    public static class NotifyPeer extends TLObject {
        public Peer peer;

        public static NotifyPeer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            NotifyPeer result = null;
            switch(constructor) {
                case 0x74d07c60:
                    result = new TL_notifyAll();
                    break;
                case 0xc007cec3:
                    result = new TL_notifyChats();
                    break;
                case 0xb4c83b4c:
                    result = new TL_notifyUsers();
                    break;
                case 0x9fd40bd8:
                    result = new TL_notifyPeer();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in NotifyPeer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_notifyAll extends NotifyPeer {
        public static int constructor = 0x74d07c60;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_notifyChats extends NotifyPeer {
        public static int constructor = 0xc007cec3;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_notifyUsers extends NotifyPeer {
        public static int constructor = 0xb4c83b4c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_notifyPeer extends NotifyPeer {
        public static int constructor = 0x9fd40bd8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_inputPrivacyKeyStatusTimestamp extends TLObject {
        public static int constructor = 0x4f96cb18;


        public static TL_inputPrivacyKeyStatusTimestamp TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputPrivacyKeyStatusTimestamp.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputPrivacyKeyStatusTimestamp", constructor));
                } else {
                    return null;
                }
            }
            TL_inputPrivacyKeyStatusTimestamp result = new TL_inputPrivacyKeyStatusTimestamp();
            result.readParams(stream, exception);
            return result;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_sentChangePhoneCode extends TLObject {
        public static int constructor = 0xa4f58c4c;

        public String phone_code_hash;
        public int send_call_timeout;

        public static TL_account_sentChangePhoneCode TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_account_sentChangePhoneCode.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_sentChangePhoneCode", constructor));
                } else {
                    return null;
                }
            }
            TL_account_sentChangePhoneCode result = new TL_account_sentChangePhoneCode();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_code_hash = stream.readString(exception);
            send_call_timeout = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_code_hash);
            stream.writeInt32(send_call_timeout);
        }
    }

    public static class ChatInvite extends TLObject {
        public String title;
        public Chat chat;

        public static ChatInvite TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ChatInvite result = null;
            switch(constructor) {
                case 0xce917dcd:
                    result = new TL_chatInvite();
                    break;
                case 0x5a686d7c:
                    result = new TL_chatInviteAlready();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ChatInvite", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatInvite extends ChatInvite {
        public static int constructor = 0xce917dcd;


        public void readParams(AbsSerializedData stream, boolean exception) {
            title = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
        }
    }

    public static class TL_chatInviteAlready extends ChatInvite {
        public static int constructor = 0x5a686d7c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            chat.serializeToStream(stream);
        }
    }

    public static class TL_photos_photo extends TLObject {
        public static int constructor = 0x20212ca8;

        public Photo photo;
        public ArrayList<User> users = new ArrayList<>();

        public static TL_photos_photo TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_photos_photo.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_photos_photo", constructor));
                } else {
                    return null;
                }
            }
            TL_photos_photo result = new TL_photos_photo();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_resPQ extends TLObject {
        public static int constructor = 0x05162463;

        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] pq;
        public ArrayList<Long> server_public_key_fingerprints = new ArrayList<>();

        public static TL_resPQ TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_resPQ.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_resPQ", constructor));
                } else {
                    return null;
                }
            }
            TL_resPQ result = new TL_resPQ();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            pq = stream.readByteArray(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                server_public_key_fingerprints.add(stream.readInt64(exception));
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
            for (int a = 0; a < count; a++) {
                stream.writeInt64(server_public_key_fingerprints.get(a));
            }
        }
    }

    public static class messages_SentEncryptedMessage extends TLObject {
        public int date;
        public EncryptedFile file;

        public static messages_SentEncryptedMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_SentEncryptedMessage result = null;
            switch(constructor) {
                case 0x560f8935:
                    result = new TL_messages_sentEncryptedMessage();
                    break;
                case 0x9493ff32:
                    result = new TL_messages_sentEncryptedFile();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_SentEncryptedMessage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_sentEncryptedMessage extends messages_SentEncryptedMessage {
        public static int constructor = 0x560f8935;


        public void readParams(AbsSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
        }
    }

    public static class TL_messages_sentEncryptedFile extends messages_SentEncryptedMessage {
        public static int constructor = 0x9493ff32;


        public void readParams(AbsSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            file = EncryptedFile.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            file.serializeToStream(stream);
        }
    }

    public static class Update extends TLObject {
        public int chat_id;
        public int max_date;
        public int date;
        public int user_id;
        public ContactLink my_link;
        public ContactLink foreign_link;
        public int max_id;
        public int pts;
        public int pts_count;
        public int version;
        public WebPage webpage;
        public String type;
        public MessageMedia media;
        public boolean popup;
        public PeerNotifySettings notify_settings;
        public SendMessageAction action;
        public String first_name;
        public String last_name;
        public String username;
        public int qts;
        public int id;
        public long random_id;
        public ArrayList<TL_dcOption> dc_options = new ArrayList<>();
        public ArrayList<Integer> messages = new ArrayList<>();
        public ChatParticipants participants;
        public TL_privacyKeyStatusTimestamp key;
        public ArrayList<PrivacyRule> rules = new ArrayList<>();
        public EncryptedChat chat;
        public boolean blocked;
        public String phone;
        public long auth_key_id;
        public String device;
        public String location;
        public UserProfilePhoto photo;
        public boolean previous;
        public int inviter_id;
        public UserStatus status;

        public static Update TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Update result = null;
            switch(constructor) {
                case 0x38fe25b7:
                    result = new TL_updateEncryptedMessagesRead();
                    break;
                case 0x9d2e67c5:
                    result = new TL_updateContactLink();
                    break;
                case 0x9961fd5c:
                    result = new TL_updateReadHistoryInbox();
                    break;
                case 0x2f2f21bf:
                    result = new TL_updateReadHistoryOutbox();
                    break;
                case 0x6e5f8c22:
                    result = new TL_updateChatParticipantDelete();
                    break;
                case 0x2cc36971:
                    result = new TL_updateWebPage();
                    break;
                case 0x382dd3e4:
                    result = new TL_updateServiceNotification();
                    break;
                case 0xbec268ef:
                    result = new TL_updateNotifySettings();
                    break;
                case 0x5c486927:
                    result = new TL_updateUserTyping();
                    break;
                case 0x9a65ea1f:
                    result = new TL_updateChatUserTyping();
                    break;
                case 0xa7332b73:
                    result = new TL_updateUserName();
                    break;
                case 0x12bcbd9a:
                    result = new TL_updateNewEncryptedMessage();
                    break;
                case 0x1f2b0afd:
                    result = new TL_updateNewMessage();
                    break;
                case 0x4e90bfd6:
                    result = new TL_updateMessageID();
                    break;
                case 0x8e5e9873:
                    result = new TL_updateDcOptions();
                    break;
                case 0x1710f156:
                    result = new TL_updateEncryptedChatTyping();
                    break;
                case 0xa20db0e5:
                    result = new TL_updateDeleteMessages();
                    break;
                case 0x68c13933:
                    result = new TL_updateReadMessagesContents();
                    break;
                case 0x7761198:
                    result = new TL_updateChatParticipants();
                    break;
                case 0xee3b272a:
                    result = new TL_updatePrivacy();
                    break;
                case 0xb4a2e88d:
                    result = new TL_updateEncryption();
                    break;
                case 0x80ece81a:
                    result = new TL_updateUserBlocked();
                    break;
                case 0x12b9417b:
                    result = new TL_updateUserPhone();
                    break;
                case 0x8f06529a:
                    result = new TL_updateNewAuthorization();
                    break;
                case 0x5a68e3f7:
                    result = new TL_updateNewGeoChatMessage();
                    break;
                case 0x95313b0c:
                    result = new TL_updateUserPhoto();
                    break;
                case 0x2575bbb9:
                    result = new TL_updateContactRegistered();
                    break;
                case 0x3a0eeb22:
                    result = new TL_updateChatParticipantAdd();
                    break;
                case 0x1bfbd823:
                    result = new TL_updateUserStatus();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Update", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_updateEncryptedMessagesRead extends Update {
        public static int constructor = 0x38fe25b7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            max_date = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(max_date);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateContactLink extends Update {
        public static int constructor = 0x9d2e67c5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            my_link = ContactLink.TLdeserialize(stream, stream.readInt32(exception), exception);
            foreign_link = ContactLink.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            my_link.serializeToStream(stream);
            foreign_link.serializeToStream(stream);
        }
    }

    public static class TL_updateReadHistoryInbox extends Update {
        public static int constructor = 0x9961fd5c;

        public Peer peer;

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_id = stream.readInt32(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateReadHistoryOutbox extends Update {
        public static int constructor = 0x2f2f21bf;

        public Peer peer;

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_id = stream.readInt32(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_id);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateChatParticipantDelete extends Update {
        public static int constructor = 0x6e5f8c22;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            user_id = stream.readInt32(exception);
            version = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(user_id);
            stream.writeInt32(version);
        }
    }

    public static class TL_updateWebPage extends Update {
        public static int constructor = 0x2cc36971;


        public void readParams(AbsSerializedData stream, boolean exception) {
            webpage = WebPage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            webpage.serializeToStream(stream);
        }
    }

    public static class TL_updateServiceNotification extends Update {
        public static int constructor = 0x382dd3e4;

        public String message;

        public void readParams(AbsSerializedData stream, boolean exception) {
            type = stream.readString(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            popup = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeBool(popup);
        }
    }

    public static class TL_updateNotifySettings extends Update {
        public static int constructor = 0xbec268ef;

        public NotifyPeer peer;

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = NotifyPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            notify_settings = PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
        }
    }

    public static class TL_updateUserTyping extends Update {
        public static int constructor = 0x5c486927;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            action = SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            action.serializeToStream(stream);
        }
    }

    public static class TL_updateChatUserTyping extends Update {
        public static int constructor = 0x9a65ea1f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            user_id = stream.readInt32(exception);
            action = SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(user_id);
            action.serializeToStream(stream);
        }
    }

    public static class TL_updateUserName extends Update {
        public static int constructor = 0xa7332b73;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            username = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeString(username);
        }
    }

    public static class TL_updateNewEncryptedMessage extends Update {
        public static int constructor = 0x12bcbd9a;

        public EncryptedMessage message;

        public void readParams(AbsSerializedData stream, boolean exception) {
            message = EncryptedMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
            qts = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(qts);
        }
    }

    public static class TL_updateNewMessage extends Update {
        public static int constructor = 0x1f2b0afd;

        public Message message;

        public void readParams(AbsSerializedData stream, boolean exception) {
            message = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateMessageID extends Update {
        public static int constructor = 0x4e90bfd6;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            random_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_updateDcOptions extends Update {
        public static int constructor = 0x8e5e9873;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_dcOption object = TL_dcOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                dc_options.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = dc_options.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                dc_options.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_updateEncryptedChatTyping extends Update {
        public static int constructor = 0x1710f156;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_updateDeleteMessages extends Update {
        public static int constructor = 0xa20db0e5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                messages.add(stream.readInt32(exception));
            }
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(messages.get(a));
            }
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateReadMessagesContents extends Update {
        public static int constructor = 0x68c13933;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                messages.add(stream.readInt32(exception));
            }
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = messages.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(messages.get(a));
            }
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_updateChatParticipants extends Update {
        public static int constructor = 0x7761198;


        public void readParams(AbsSerializedData stream, boolean exception) {
            participants = ChatParticipants.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            participants.serializeToStream(stream);
        }
    }

    public static class TL_updatePrivacy extends Update {
        public static int constructor = 0xee3b272a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            key = TL_privacyKeyStatusTimestamp.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PrivacyRule object = PrivacyRule.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                rules.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = rules.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                rules.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_updateEncryption extends Update {
        public static int constructor = 0xb4a2e88d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat = EncryptedChat.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            chat.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateUserBlocked extends Update {
        public static int constructor = 0x80ece81a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            blocked = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeBool(blocked);
        }
    }

    public static class TL_updateUserPhone extends Update {
        public static int constructor = 0x12b9417b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            phone = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeString(phone);
        }
    }

    public static class TL_updateNewAuthorization extends Update {
        public static int constructor = 0x8f06529a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            auth_key_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            device = stream.readString(exception);
            location = stream.readString(exception);
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

        public void readParams(AbsSerializedData stream, boolean exception) {
            message = GeoChatMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            message.serializeToStream(stream);
        }
    }

    public static class TL_updateUserPhoto extends Update {
        public static int constructor = 0x95313b0c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            photo = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            previous = stream.readBool(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
        }
    }

    public static class TL_updateChatParticipantAdd extends Update {
        public static int constructor = 0x3a0eeb22;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            user_id = stream.readInt32(exception);
            inviter_id = stream.readInt32(exception);
            version = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            status = UserStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            status.serializeToStream(stream);
        }
    }

    public static class TL_config extends TLObject {
        public static int constructor = 0x4e32b894;

        public int date;
        public int expires;
        public boolean test_mode;
        public int this_dc;
        public ArrayList<TL_dcOption> dc_options = new ArrayList<>();
        public int chat_size_max;
        public int broadcast_size_max;
        public int forwarded_count_max;
        public int online_update_period_ms;
        public int offline_blur_timeout_ms;
        public int offline_idle_timeout_ms;
        public int online_cloud_timeout_ms;
        public int notify_cloud_delay_ms;
        public int notify_default_delay_ms;
        public int chat_big_size;
        public int push_chat_period_ms;
        public int push_chat_limit;
        public ArrayList<TL_disabledFeature> disabled_features = new ArrayList<>();

        public static TL_config TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_config.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_config", constructor));
                } else {
                    return null;
                }
            }
            TL_config result = new TL_config();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            expires = stream.readInt32(exception);
            test_mode = stream.readBool(exception);
            this_dc = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_dcOption object = TL_dcOption.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                dc_options.add(object);
            }
            chat_size_max = stream.readInt32(exception);
            broadcast_size_max = stream.readInt32(exception);
            forwarded_count_max = stream.readInt32(exception);
            online_update_period_ms = stream.readInt32(exception);
            offline_blur_timeout_ms = stream.readInt32(exception);
            offline_idle_timeout_ms = stream.readInt32(exception);
            online_cloud_timeout_ms = stream.readInt32(exception);
            notify_cloud_delay_ms = stream.readInt32(exception);
            notify_default_delay_ms = stream.readInt32(exception);
            chat_big_size = stream.readInt32(exception);
            push_chat_period_ms = stream.readInt32(exception);
            push_chat_limit = stream.readInt32(exception);
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_disabledFeature object = TL_disabledFeature.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                disabled_features.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            stream.writeInt32(expires);
            stream.writeBool(test_mode);
            stream.writeInt32(this_dc);
            stream.writeInt32(0x1cb5c415);
            int count = dc_options.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                dc_options.get(a).serializeToStream(stream);
            }
            stream.writeInt32(chat_size_max);
            stream.writeInt32(broadcast_size_max);
            stream.writeInt32(forwarded_count_max);
            stream.writeInt32(online_update_period_ms);
            stream.writeInt32(offline_blur_timeout_ms);
            stream.writeInt32(offline_idle_timeout_ms);
            stream.writeInt32(online_cloud_timeout_ms);
            stream.writeInt32(notify_cloud_delay_ms);
            stream.writeInt32(notify_default_delay_ms);
            stream.writeInt32(chat_big_size);
            stream.writeInt32(push_chat_period_ms);
            stream.writeInt32(push_chat_limit);
            stream.writeInt32(0x1cb5c415);
            count = disabled_features.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                disabled_features.get(a).serializeToStream(stream);
            }
        }
    }

    public static class InputAudio extends TLObject {
        public long id;
        public long access_hash;

        public static InputAudio TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputAudio result = null;
            switch(constructor) {
                case 0x77d440ff:
                    result = new TL_inputAudio();
                    break;
                case 0xd95adc84:
                    result = new TL_inputAudioEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputAudio", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputAudio extends InputAudio {
        public static int constructor = 0x77d440ff;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
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

    public static class PeerNotifySettings extends TLObject {
        public int mute_until;
        public String sound;
        public boolean show_previews;
        public int events_mask;

        public static PeerNotifySettings TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            PeerNotifySettings result = null;
            switch(constructor) {
                case 0x70a68512:
                    result = new TL_peerNotifySettingsEmpty();
                    break;
                case 0x8d5e11ee:
                    result = new TL_peerNotifySettings();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PeerNotifySettings", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_peerNotifySettingsEmpty extends PeerNotifySettings {
        public static int constructor = 0x70a68512;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_peerNotifySettings extends PeerNotifySettings {
        public static int constructor = 0x8d5e11ee;


        public void readParams(AbsSerializedData stream, boolean exception) {
            mute_until = stream.readInt32(exception);
            sound = stream.readString(exception);
            show_previews = stream.readBool(exception);
            events_mask = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(mute_until);
            stream.writeString(sound);
            stream.writeBool(show_previews);
            stream.writeInt32(events_mask);
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

        public static GeoChatMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            GeoChatMessage result = null;
            switch(constructor) {
                case 0x4505f8e1:
                    result = new TL_geoChatMessage();
                    break;
                case 0xd34fa24e:
                    result = new TL_geoChatMessageService();
                    break;
                case 0x60311a9b:
                    result = new TL_geoChatMessageEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in GeoChatMessage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_geoChatMessage extends GeoChatMessage {
        public static int constructor = 0x4505f8e1;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            action = MessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt32(id);
        }
    }

    public static class messages_SentMessage extends TLObject {
        public int id;
        public int date;
        public MessageMedia media;
        public ArrayList<MessageEntity> entities = new ArrayList<>();
        public int pts;
        public int pts_count;
        public ArrayList<TL_contacts_link> links = new ArrayList<>();
        public int seq;

        public static messages_SentMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_SentMessage result = null;
            switch(constructor) {
                case 0x8a99d8e0:
                    result = new TL_messages_sentMessage();
                    break;
                case 0x35a1a663:
                    result = new TL_messages_sentMessageLink();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_SentMessage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_sentMessage extends messages_SentMessage {
        public static int constructor = 0x8a99d8e0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                MessageEntity object = MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                entities.add(object);
            }
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(date);
            media.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = entities.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                entities.get(a).serializeToStream(stream);
            }
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class TL_messages_sentMessageLink extends messages_SentMessage {
        public static int constructor = 0x35a1a663;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contacts_link object = TL_contacts_link.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                links.add(object);
            }
            seq = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(date);
            media.serializeToStream(stream);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
            stream.writeInt32(0x1cb5c415);
            int count = links.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                links.get(a).serializeToStream(stream);
            }
            stream.writeInt32(seq);
        }
    }

    public static class TL_contactSuggested extends TLObject {
        public static int constructor = 0x3de191a1;

        public int user_id;
        public int mutual_contacts;

        public static TL_contactSuggested TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contactSuggested.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contactSuggested", constructor));
                } else {
                    return null;
                }
            }
            TL_contactSuggested result = new TL_contactSuggested();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            mutual_contacts = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(mutual_contacts);
        }
    }

    public static class InputChatPhoto extends TLObject {
        public InputPhoto id;
        public InputPhotoCrop crop;
        public InputFile file;

        public static InputChatPhoto TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputChatPhoto result = null;
            switch(constructor) {
                case 0xb2e1bf08:
                    result = new TL_inputChatPhoto();
                    break;
                case 0x1ca48f57:
                    result = new TL_inputChatPhotoEmpty();
                    break;
                case 0x94254732:
                    result = new TL_inputChatUploadedPhoto();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputChatPhoto", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputChatPhoto extends InputChatPhoto {
        public static int constructor = 0xb2e1bf08;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = InputPhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            crop = InputPhotoCrop.TLdeserialize(stream, stream.readInt32(exception), exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            file = InputFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            crop = InputPhotoCrop.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class InputPeer extends TLObject {
        public int user_id;
        public long access_hash;
        public int chat_id;

        public static InputPeer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputPeer result = null;
            switch(constructor) {
                case 0x7b8e7de6:
                    result = new TL_inputPeerUser();
                    break;
                case 0x179be863:
                    result = new TL_inputPeerChat();
                    break;
                case 0x7f3b18ea:
                    result = new TL_inputPeerEmpty();
                    break;
                case 0x7da07ec9:
                    result = new TL_inputPeerSelf();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputPeer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputPeerUser extends InputPeer {
        public static int constructor = 0x7b8e7de6;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputPeerChat extends InputPeer {
        public static int constructor = 0x179be863;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
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

    public static class TL_msg_copy extends TLObject {
        public static int constructor = 0xe06046b2;

        public TL_protoMessage orig_message;

        public static TL_msg_copy TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msg_copy.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msg_copy", constructor));
                } else {
                    return null;
                }
            }
            TL_msg_copy result = new TL_msg_copy();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            orig_message = TL_protoMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            orig_message.serializeToStream(stream);
        }
    }

    public static class FileLocation extends TLObject {
        public int dc_id;
        public long volume_id;
        public int local_id;
        public long secret;
        public byte[] key;
        public byte[] iv;

        public static FileLocation TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            FileLocation result = null;
            switch(constructor) {
                case 0x53d69076:
                    result = new TL_fileLocation();
                    break;
                case 0x55555554:
                    result = new TL_fileEncryptedLocation();
                    break;
                case 0x7c596b46:
                    result = new TL_fileLocationUnavailable();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in FileLocation", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_fileLocation extends FileLocation {
        public static int constructor = 0x53d69076;


        public void readParams(AbsSerializedData stream, boolean exception) {
            dc_id = stream.readInt32(exception);
            volume_id = stream.readInt64(exception);
            local_id = stream.readInt32(exception);
            secret = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(dc_id);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class TL_fileEncryptedLocation extends FileLocation {
        public static int constructor = 0x55555554;


        public void readParams(AbsSerializedData stream, boolean exception) {
            dc_id = stream.readInt32(exception);
            volume_id = stream.readInt64(exception);
            local_id = stream.readInt32(exception);
            secret = stream.readInt64(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_fileLocationUnavailable extends FileLocation {
        public static int constructor = 0x7c596b46;


        public void readParams(AbsSerializedData stream, boolean exception) {
            volume_id = stream.readInt64(exception);
            local_id = stream.readInt32(exception);
            secret = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class StickerSet extends TLObject {
        public long id;
        public long access_hash;
        public String title;
        public String short_name;
        public int flags;
        public int count;
        public int hash;

        public static StickerSet TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            StickerSet result = null;
            switch(constructor) {
                case 0xa7a43b17:
                    result = new TL_stickerSet_old();
                    break;
                case 0xcd303b41:
                    result = new TL_stickerSet();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in StickerSet", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_stickerSet_old extends TL_stickerSet {
        public static int constructor = 0xa7a43b17;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            title = stream.readString(exception);
            short_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeString(title);
            stream.writeString(short_name);
        }
    }

    public static class TL_stickerSet extends StickerSet {
        public static int constructor = 0xcd303b41;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            title = stream.readString(exception);
            short_name = stream.readString(exception);
            count = stream.readInt32(exception);
            hash = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeString(title);
            stream.writeString(short_name);
            stream.writeInt32(count);
            stream.writeInt32(hash);
        }
    }

    public static class TL_pong extends TLObject {
        public static int constructor = 0x347773c5;

        public long msg_id;
        public long ping_id;

        public static TL_pong TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_pong.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_pong", constructor));
                } else {
                    return null;
                }
            }
            TL_pong result = new TL_pong();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            msg_id = stream.readInt64(exception);
            ping_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt64(ping_id);
        }
    }

    public static class TL_inputAppEvent extends TLObject {
        public static int constructor = 0x770656a8;

        public double time;
        public String type;
        public long peer;
        public String data;

        public static TL_inputAppEvent TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputAppEvent.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputAppEvent", constructor));
                } else {
                    return null;
                }
            }
            TL_inputAppEvent result = new TL_inputAppEvent();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            time = stream.readDouble(exception);
            type = stream.readString(exception);
            peer = stream.readInt64(exception);
            data = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(time);
            stream.writeString(type);
            stream.writeInt64(peer);
            stream.writeString(data);
        }
    }

    public static class TL_messages_chatFull extends TLObject {
        public static int constructor = 0xe5d7d19c;

        public TL_chatFull full_chat;
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_messages_chatFull TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_chatFull.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_chatFull", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_chatFull result = new TL_messages_chatFull();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            full_chat = TL_chatFull.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class InputNotifyPeer extends TLObject {

        public static InputNotifyPeer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputNotifyPeer result = null;
            switch(constructor) {
                case 0x4a95e84e:
                    result = new TL_inputNotifyChats();
                    break;
                case 0xb8bc5b0c:
                    result = new TL_inputNotifyPeer();
                    break;
                case 0x193b4417:
                    result = new TL_inputNotifyUsers();
                    break;
                case 0x4d8ddec8:
                    result = new TL_inputNotifyGeoChatPeer();
                    break;
                case 0xa429b886:
                    result = new TL_inputNotifyAll();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputNotifyPeer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
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

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = InputPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
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

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = TL_inputGeoChat.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class TL_messages_affectedHistory extends TLObject {
        public static int constructor = 0xb45c69d1;

        public int pts;
        public int pts_count;
        public int offset;

        public static TL_messages_affectedHistory TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_affectedHistory.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_affectedHistory", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_affectedHistory result = new TL_messages_affectedHistory();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
            offset = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
            stream.writeInt32(offset);
        }
    }

    public static class RpcDropAnswer extends TLObject {
        public long msg_id;
        public int seq_no;
        public int bytes;

        public static RpcDropAnswer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            RpcDropAnswer result = null;
            switch(constructor) {
                case 0x5e2ad36e:
                    result = new TL_rpc_answer_unknown();
                    break;
                case 0xa43ad8b7:
                    result = new TL_rpc_answer_dropped();
                    break;
                case 0xcd78e586:
                    result = new TL_rpc_answer_dropped_running();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in RpcDropAnswer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_rpc_answer_unknown extends RpcDropAnswer {
        public static int constructor = 0x5e2ad36e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_rpc_answer_dropped extends RpcDropAnswer {
        public static int constructor = 0xa43ad8b7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            msg_id = stream.readInt64(exception);
            seq_no = stream.readInt32(exception);
            bytes = stream.readInt32(exception);
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

    public static class InputVideo extends TLObject {
        public long id;
        public long access_hash;

        public static InputVideo TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputVideo result = null;
            switch(constructor) {
                case 0x5508ec75:
                    result = new TL_inputVideoEmpty();
                    break;
                case 0xee579652:
                    result = new TL_inputVideo();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputVideo", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputVideoEmpty extends InputVideo {
        public static int constructor = 0x5508ec75;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputVideo extends InputVideo {
        public static int constructor = 0xee579652;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class messages_DhConfig extends TLObject {
        public byte[] random;
        public int g;
        public byte[] p;
        public int version;

        public static messages_DhConfig TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_DhConfig result = null;
            switch(constructor) {
                case 0xc0e24635:
                    result = new TL_messages_dhConfigNotModified();
                    break;
                case 0x2c221edd:
                    result = new TL_messages_dhConfig();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_DhConfig", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_dhConfigNotModified extends messages_DhConfig {
        public static int constructor = 0xc0e24635;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(random);
        }
    }

    public static class TL_messages_dhConfig extends messages_DhConfig {
        public static int constructor = 0x2c221edd;


        public void readParams(AbsSerializedData stream, boolean exception) {
            g = stream.readInt32(exception);
            p = stream.readByteArray(exception);
            version = stream.readInt32(exception);
            random = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(g);
            stream.writeByteArray(p);
            stream.writeInt32(version);
            stream.writeByteArray(random);
        }
    }

    public static class Peer extends TLObject {
        public int user_id;
        public int chat_id;

        public static Peer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Peer result = null;
            switch(constructor) {
                case 0x9db1bc6d:
                    result = new TL_peerUser();
                    break;
                case 0xbad0e5bb:
                    result = new TL_peerChat();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Peer", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_peerUser extends Peer {
        public static int constructor = 0x9db1bc6d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_peerChat extends Peer {
        public static int constructor = 0xbad0e5bb;


        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_dcOption extends TLObject {
        public static int constructor = 0x5d8c6cc;

        public int flags;
        public int id;
        public String ip_address;
        public int port;

        public static TL_dcOption TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_dcOption.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_dcOption", constructor));
                } else {
                    return null;
                }
            }
            TL_dcOption result = new TL_dcOption();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            ip_address = stream.readString(exception);
            port = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeString(ip_address);
            stream.writeInt32(port);
        }
    }

    public static class InputFile extends TLObject {
        public long id;
        public int parts;
        public String name;
        public String md5_checksum;

        public static InputFile TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputFile result = null;
            switch(constructor) {
                case 0xfa4f0bb5:
                    result = new TL_inputFileBig();
                    break;
                case 0xf52ff27f:
                    result = new TL_inputFile();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputFile", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputFileBig extends InputFile {
        public static int constructor = 0xfa4f0bb5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            parts = stream.readInt32(exception);
            name = stream.readString(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            parts = stream.readInt32(exception);
            name = stream.readString(exception);
            md5_checksum = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt32(parts);
            stream.writeString(name);
            stream.writeString(md5_checksum);
        }
    }

    public static class TL_account_passwordInputSettings extends TLObject {
        public static int constructor = 0xbcfc532c;

        public int flags;
        public byte[] new_salt;
        public byte[] new_password_hash;
        public String hint;
        public String email;

        public static TL_account_passwordInputSettings TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_account_passwordInputSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_passwordInputSettings", constructor));
                } else {
                    return null;
                }
            }
            TL_account_passwordInputSettings result = new TL_account_passwordInputSettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                new_salt = stream.readByteArray(exception);
            }
            if ((flags & 1) != 0) {
                new_password_hash = stream.readByteArray(exception);
            }
            if ((flags & 1) != 0) {
                hint = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                email = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeByteArray(new_salt);
            }
            if ((flags & 1) != 0) {
                stream.writeByteArray(new_password_hash);
            }
            if ((flags & 1) != 0) {
                stream.writeString(hint);
            }
            if ((flags & 2) != 0) {
                stream.writeString(email);
            }
        }
    }

    public static class InputUser extends TLObject {
        public int user_id;
        public long access_hash;

        public static InputUser TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputUser result = null;
            switch(constructor) {
                case 0xb98886cf:
                    result = new TL_inputUserEmpty();
                    break;
                case 0xf7c1b13f:
                    result = new TL_inputUserSelf();
                    break;
                case 0xd8292816:
                    result = new TL_inputUser();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputUser", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputUserEmpty extends InputUser {
        public static int constructor = 0xb98886cf;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputUserSelf extends InputUser {
        public static int constructor = 0xf7c1b13f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputUser extends InputUser {
        public static int constructor = 0xd8292816;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_chatParticipant extends TLObject {
        public static int constructor = 0xc8d7493e;

        public int user_id;
        public int inviter_id;
        public int date;

        public static TL_chatParticipant TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_chatParticipant.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatParticipant", constructor));
                } else {
                    return null;
                }
            }
            TL_chatParticipant result = new TL_chatParticipant();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            inviter_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(inviter_id);
            stream.writeInt32(date);
        }
    }

    public static class EncryptedFile extends TLObject {
        public long id;
        public long access_hash;
        public int size;
        public int dc_id;
        public int key_fingerprint;

        public static EncryptedFile TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            EncryptedFile result = null;
            switch(constructor) {
                case 0x4a70994c:
                    result = new TL_encryptedFile();
                    break;
                case 0xc21f497e:
                    result = new TL_encryptedFileEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in EncryptedFile", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_encryptedFile extends EncryptedFile {
        public static int constructor = 0x4a70994c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            size = stream.readInt32(exception);
            dc_id = stream.readInt32(exception);
            key_fingerprint = stream.readInt32(exception);
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

    public static class TL_account_privacyRules extends TLObject {
        public static int constructor = 0x554abb6f;

        public ArrayList<PrivacyRule> rules = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_account_privacyRules TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_account_privacyRules.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_privacyRules", constructor));
                } else {
                    return null;
                }
            }
            TL_account_privacyRules result = new TL_account_privacyRules();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PrivacyRule object = PrivacyRule.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                rules.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = rules.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                rules.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_auth_exportedAuthorization extends TLObject {
        public static int constructor = 0xdf969c2d;

        public int id;
        public byte[] bytes;

        public static TL_auth_exportedAuthorization TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_auth_exportedAuthorization.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_auth_exportedAuthorization", constructor));
                } else {
                    return null;
                }
            }
            TL_auth_exportedAuthorization result = new TL_auth_exportedAuthorization();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            bytes = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_botCommand extends TLObject {
        public static int constructor = 0xc27ac8c7;

        public String command;
        public String description;

        public static TL_botCommand TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_botCommand.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_botCommand", constructor));
                } else {
                    return null;
                }
            }
            TL_botCommand result = new TL_botCommand();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            command = stream.readString(exception);
            description = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(command);
            stream.writeString(description);
        }
    }

    public static class InputFileLocation extends TLObject {
        public long id;
        public long access_hash;
        public long volume_id;
        public int local_id;
        public long secret;

        public static InputFileLocation TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputFileLocation result = null;
            switch(constructor) {
                case 0x74dc404d:
                    result = new TL_inputAudioFileLocation();
                    break;
                case 0xf5235d55:
                    result = new TL_inputEncryptedFileLocation();
                    break;
                case 0x3d0364ec:
                    result = new TL_inputVideoFileLocation();
                    break;
                case 0x4e45abe9:
                    result = new TL_inputDocumentFileLocation();
                    break;
                case 0x14637196:
                    result = new TL_inputFileLocation();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputFileLocation", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputAudioFileLocation extends InputFileLocation {
        public static int constructor = 0x74dc404d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputEncryptedFileLocation extends InputFileLocation {
        public static int constructor = 0xf5235d55;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputVideoFileLocation extends InputFileLocation {
        public static int constructor = 0x3d0364ec;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputDocumentFileLocation extends InputFileLocation {
        public static int constructor = 0x4e45abe9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputFileLocation extends InputFileLocation {
        public static int constructor = 0x14637196;


        public void readParams(AbsSerializedData stream, boolean exception) {
            volume_id = stream.readInt64(exception);
            local_id = stream.readInt32(exception);
            secret = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
        }
    }

    public static class TL_chatFull extends TLObject {
        public static int constructor = 0x2e02a614;

        public int id;
        public ChatParticipants participants;
        public Photo chat_photo;
        public PeerNotifySettings notify_settings;
        public ExportedChatInvite exported_invite;
        public ArrayList<BotInfo> bot_info = new ArrayList<>();

        public static TL_chatFull TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_chatFull.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_chatFull", constructor));
                } else {
                    return null;
                }
            }
            TL_chatFull result = new TL_chatFull();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            participants = ChatParticipants.TLdeserialize(stream, stream.readInt32(exception), exception);
            chat_photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            notify_settings = PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            exported_invite = ExportedChatInvite.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                BotInfo object = BotInfo.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                bot_info.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            participants.serializeToStream(stream);
            chat_photo.serializeToStream(stream);
            notify_settings.serializeToStream(stream);
            exported_invite.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = bot_info.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                bot_info.get(a).serializeToStream(stream);
            }
        }
    }

    public static class InputGeoPoint extends TLObject {
        public double lat;
        public double _long;

        public static InputGeoPoint TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputGeoPoint result = null;
            switch(constructor) {
                case 0xf3b7acc9:
                    result = new TL_inputGeoPoint();
                    break;
                case 0xe4c123d6:
                    result = new TL_inputGeoPointEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputGeoPoint", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputGeoPoint extends InputGeoPoint {
        public static int constructor = 0xf3b7acc9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            lat = stream.readDouble(exception);
            _long = stream.readDouble(exception);
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

    public static class Photo extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public GeoPoint geo;
        public ArrayList<PhotoSize> sizes = new ArrayList<>();
        public String caption;

        public static Photo TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Photo result = null;
            switch(constructor) {
                case 0x2331b22d:
                    result = new TL_photoEmpty();
                    break;
                case 0xcded42fe:
                    result = new TL_photo();
                    break;
                case 0xc3838076:
                    result = new TL_photo_old2();
                    break;
                case 0x22b56751:
                    result = new TL_photo_old();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Photo", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_photoEmpty extends Photo {
        public static int constructor = 0x2331b22d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_photo extends Photo {
        public static int constructor = 0xcded42fe;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PhotoSize object = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sizes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(0x1cb5c415);
            int count = sizes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                sizes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_photo_old2 extends TL_photo {
        public static int constructor = 0xc3838076;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            geo = GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PhotoSize object = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sizes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            geo.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = sizes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                sizes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_photo_old extends TL_photo {
        public static int constructor = 0x22b56751;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            caption = stream.readString(exception);
            geo = GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                PhotoSize object = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sizes.add(object);
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
            for (int a = 0; a < count; a++) {
                sizes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_help_support extends TLObject {
        public static int constructor = 0x17c6b5f6;

        public String phone_number;
        public User user;

        public static TL_help_support TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_help_support.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_help_support", constructor));
                } else {
                    return null;
                }
            }
            TL_help_support result = new TL_help_support();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_number = stream.readString(exception);
            user = User.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            user.serializeToStream(stream);
        }
    }

    public static class TL_inputPhoneContact extends TLObject {
        public static int constructor = 0xf392b7f4;

        public long client_id;
        public String phone;
        public String first_name;
        public String last_name;

        public static TL_inputPhoneContact TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputPhoneContact.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputPhoneContact", constructor));
                } else {
                    return null;
                }
            }
            TL_inputPhoneContact result = new TL_inputPhoneContact();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            client_id = stream.readInt64(exception);
            phone = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(client_id);
            stream.writeString(phone);
            stream.writeString(first_name);
            stream.writeString(last_name);
        }
    }

    public static class Bool extends TLObject {

        public static Bool TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Bool result = null;
            switch(constructor) {
                case 0x997275b5:
                    result = new TL_boolTrue();
                    break;
                case 0xbc799737:
                    result = new TL_boolFalse();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Bool", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
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

    public static class EncryptedMessage extends TLObject {
        public long random_id;
        public int chat_id;
        public int date;
        public byte[] bytes;
        public EncryptedFile file;

        public static EncryptedMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            EncryptedMessage result = null;
            switch(constructor) {
                case 0x23734b06:
                    result = new TL_encryptedMessageService();
                    break;
                case 0xed18c118:
                    result = new TL_encryptedMessage();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in EncryptedMessage", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_encryptedMessageService extends EncryptedMessage {
        public static int constructor = 0x23734b06;


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            chat_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            bytes = stream.readByteArray(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            chat_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            bytes = stream.readByteArray(exception);
            file = EncryptedFile.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class TL_messages_messageEmpty extends TLObject {
        public static int constructor = 0x3f4e0648;


        public static TL_messages_messageEmpty TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_messageEmpty.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_messageEmpty", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_messageEmpty result = new TL_messages_messageEmpty();
            result.readParams(stream, exception);
            return result;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class DestroySessionRes extends TLObject {
        public long session_id;

        public static DestroySessionRes TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            DestroySessionRes result = null;
            switch(constructor) {
                case 0xe22045fc:
                    result = new TL_destroy_session_ok();
                    break;
                case 0x62d350c9:
                    result = new TL_destroy_session_none();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in DestroySessionRes", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_destroy_session_ok extends DestroySessionRes {
        public static int constructor = 0xe22045fc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            session_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class TL_destroy_session_none extends DestroySessionRes {
        public static int constructor = 0x62d350c9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            session_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class PhotoSize extends TLObject {
        public String type;
        public FileLocation location;
        public int w;
        public int h;
        public int size;
        public byte[] bytes;

        public static PhotoSize TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            PhotoSize result = null;
            switch(constructor) {
                case 0x77bfb61b:
                    result = new TL_photoSize();
                    break;
                case 0xe17e23c:
                    result = new TL_photoSizeEmpty();
                    break;
                case 0xe9a734fa:
                    result = new TL_photoCachedSize();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PhotoSize", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_photoSize extends PhotoSize {
        public static int constructor = 0x77bfb61b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            type = stream.readString(exception);
            location = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            size = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            type = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(type);
        }
    }

    public static class TL_photoCachedSize extends PhotoSize {
        public static int constructor = 0xe9a734fa;


        public void readParams(AbsSerializedData stream, boolean exception) {
            type = stream.readString(exception);
            location = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            bytes = stream.readByteArray(exception);
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

    public static class MessageAction extends TLObject {
        public String title;
        public String address;
        public int user_id;
        public int inviter_id;
        public DecryptedMessageAction encryptedAction;
        public int ttl;
        public UserProfilePhoto newUserPhoto;
        public Photo photo;
        public ArrayList<Integer> users = new ArrayList<>();

        public static MessageAction TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            MessageAction result = null;
            switch(constructor) {
                case 0xc7d53de:
                    result = new TL_messageActionGeoChatCheckin();
                    break;
                case 0x55555557:
                    result = new TL_messageActionCreatedBroadcastList();
                    break;
                case 0xb5a1ce5a:
                    result = new TL_messageActionChatEditTitle();
                    break;
                case 0x555555F5:
                    result = new TL_messageActionLoginUnknownLocation();
                    break;
                case 0x5e3cfc4b:
                    result = new TL_messageActionChatAddUser();
                    break;
                case 0xf89cf5e8:
                    result = new TL_messageActionChatJoinedByLink();
                    break;
                case 0x55555550:
                    result = new TL_messageActionUserJoined();
                    break;
                case 0x555555F7:
                    result = new TL_messageEncryptedAction();
                    break;
                case 0x55555552:
                    result = new TL_messageActionTTLChange();
                    break;
                case 0x55555551:
                    result = new TL_messageActionUserUpdatedPhoto();
                    break;
                case 0xb6aef7b0:
                    result = new TL_messageActionEmpty();
                    break;
                case 0x95e3fbef:
                    result = new TL_messageActionChatDeletePhoto();
                    break;
                case 0xb2ae9b0c:
                    result = new TL_messageActionChatDeleteUser();
                    break;
                case 0x7fcb13a8:
                    result = new TL_messageActionChatEditPhoto();
                    break;
                case 0xa6638b9a:
                    result = new TL_messageActionChatCreate();
                    break;
                case 0x6f038ebc:
                    result = new TL_messageActionGeoChatCreate();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MessageAction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messageActionGeoChatCheckin extends MessageAction {
        public static int constructor = 0xc7d53de;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionCreatedBroadcastList extends MessageAction {
        public static int constructor = 0x55555557;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionChatEditTitle extends MessageAction {
        public static int constructor = 0xb5a1ce5a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            title = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
        }
    }

    public static class TL_messageActionLoginUnknownLocation extends MessageAction {
        public static int constructor = 0x555555F5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            title = stream.readString(exception);
            address = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeString(address);
        }
    }

    public static class TL_messageActionChatAddUser extends MessageAction {
        public static int constructor = 0x5e3cfc4b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageActionChatJoinedByLink extends MessageAction {
        public static int constructor = 0xf89cf5e8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            inviter_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(inviter_id);
        }
    }

    public static class TL_messageActionUserJoined extends MessageAction {
        public static int constructor = 0x55555550;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageEncryptedAction extends MessageAction {
        public static int constructor = 0x555555F7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            encryptedAction = DecryptedMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            encryptedAction.serializeToStream(stream);
        }
    }

    public static class TL_messageActionTTLChange extends MessageAction {
        public static int constructor = 0x55555552;


        public void readParams(AbsSerializedData stream, boolean exception) {
            ttl = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(ttl);
        }
    }

    public static class TL_messageActionUserUpdatedPhoto extends MessageAction {
        public static int constructor = 0x55555551;


        public void readParams(AbsSerializedData stream, boolean exception) {
            newUserPhoto = UserProfilePhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            newUserPhoto.serializeToStream(stream);
        }
    }

    public static class TL_messageActionEmpty extends MessageAction {
        public static int constructor = 0xb6aef7b0;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionChatDeletePhoto extends MessageAction {
        public static int constructor = 0x95e3fbef;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messageActionChatDeleteUser extends MessageAction {
        public static int constructor = 0xb2ae9b0c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
        }
    }

    public static class TL_messageActionChatEditPhoto extends MessageAction {
        public static int constructor = 0x7fcb13a8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo = Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messageActionChatCreate extends MessageAction {
        public static int constructor = 0xa6638b9a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            title = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                users.add(stream.readInt32(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(users.get(a));
            }
        }
    }

    public static class TL_messageActionGeoChatCreate extends MessageAction {
        public static int constructor = 0x6f038ebc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            title = stream.readString(exception);
            address = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(title);
            stream.writeString(address);
        }
    }

    public static class DecryptedMessageAction extends TLObject {
        public int ttl_seconds;
        public int layer;
        public ArrayList<Long> random_ids = new ArrayList<>();
        public long exchange_id;
        public long key_fingerprint;
        public SendMessageAction action;
        public byte[] g_b;
        public int start_seq_no;
        public int end_seq_no;
        public byte[] g_a;

        public static DecryptedMessageAction TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            DecryptedMessageAction result = null;
            switch(constructor) {
                case 0xa1733aec:
                    result = new TL_decryptedMessageActionSetMessageTTL();
                    break;
                case 0xf3048883:
                    result = new TL_decryptedMessageActionNotifyLayer();
                    break;
                case 0x65614304:
                    result = new TL_decryptedMessageActionDeleteMessages();
                    break;
                case 0xec2e0b9b:
                    result = new TL_decryptedMessageActionCommitKey();
                    break;
                case 0xdd05ec6b:
                    result = new TL_decryptedMessageActionAbortKey();
                    break;
                case 0x6719e45c:
                    result = new TL_decryptedMessageActionFlushHistory();
                    break;
                case 0xccb27641:
                    result = new TL_decryptedMessageActionTyping();
                    break;
                case 0x6fe1735b:
                    result = new TL_decryptedMessageActionAcceptKey();
                    break;
                case 0xc4f40be:
                    result = new TL_decryptedMessageActionReadMessages();
                    break;
                case 0x511110b0:
                    result = new TL_decryptedMessageActionResend();
                    break;
                case 0xf3c9611b:
                    result = new TL_decryptedMessageActionRequestKey();
                    break;
                case 0x8ac1f475:
                    result = new TL_decryptedMessageActionScreenshotMessages();
                    break;
                case 0xa82fdd63:
                    result = new TL_decryptedMessageActionNoop();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in DecryptedMessageAction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_decryptedMessageActionSetMessageTTL extends DecryptedMessageAction {
        public static int constructor = 0xa1733aec;


        public void readParams(AbsSerializedData stream, boolean exception) {
            ttl_seconds = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(ttl_seconds);
        }
    }

    public static class TL_decryptedMessageActionNotifyLayer extends DecryptedMessageAction {
        public static int constructor = 0xf3048883;


        public void readParams(AbsSerializedData stream, boolean exception) {
            layer = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(layer);
        }
    }

    public static class TL_decryptedMessageActionDeleteMessages extends DecryptedMessageAction {
        public static int constructor = 0x65614304;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                random_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = random_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(random_ids.get(a));
            }
        }
    }

    public static class TL_decryptedMessageActionCommitKey extends DecryptedMessageAction {
        public static int constructor = 0xec2e0b9b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            exchange_id = stream.readInt64(exception);
            key_fingerprint = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(exchange_id);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_decryptedMessageActionAbortKey extends DecryptedMessageAction {
        public static int constructor = 0xdd05ec6b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            exchange_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(exchange_id);
        }
    }

    public static class TL_decryptedMessageActionFlushHistory extends DecryptedMessageAction {
        public static int constructor = 0x6719e45c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_decryptedMessageActionTyping extends DecryptedMessageAction {
        public static int constructor = 0xccb27641;


        public void readParams(AbsSerializedData stream, boolean exception) {
            action = SendMessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            action.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessageActionAcceptKey extends DecryptedMessageAction {
        public static int constructor = 0x6fe1735b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            exchange_id = stream.readInt64(exception);
            g_b = stream.readByteArray(exception);
            key_fingerprint = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(exchange_id);
            stream.writeByteArray(g_b);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_decryptedMessageActionReadMessages extends DecryptedMessageAction {
        public static int constructor = 0xc4f40be;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                random_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = random_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(random_ids.get(a));
            }
        }
    }

    public static class TL_decryptedMessageActionResend extends DecryptedMessageAction {
        public static int constructor = 0x511110b0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            start_seq_no = stream.readInt32(exception);
            end_seq_no = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(start_seq_no);
            stream.writeInt32(end_seq_no);
        }
    }

    public static class TL_decryptedMessageActionRequestKey extends DecryptedMessageAction {
        public static int constructor = 0xf3c9611b;


        public void readParams(AbsSerializedData stream, boolean exception) {
            exchange_id = stream.readInt64(exception);
            g_a = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(exchange_id);
            stream.writeByteArray(g_a);
        }
    }

    public static class TL_decryptedMessageActionScreenshotMessages extends DecryptedMessageAction {
        public static int constructor = 0x8ac1f475;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                random_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = random_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(random_ids.get(a));
            }
        }
    }

    public static class TL_decryptedMessageActionNoop extends DecryptedMessageAction {
        public static int constructor = 0xa82fdd63;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputGeoChat extends TLObject {
        public static int constructor = 0x74d456fa;

        public int chat_id;
        public long access_hash;

        public static TL_inputGeoChat TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_inputGeoChat.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputGeoChat", constructor));
                } else {
                    return null;
                }
            }
            TL_inputGeoChat result = new TL_inputGeoChat();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            chat_id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_msgs_state_req extends TLObject {
        public static int constructor = 0xda69fb52;

        public ArrayList<Long> msg_ids = new ArrayList<>();

        public static TL_msgs_state_req TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msgs_state_req.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msgs_state_req", constructor));
                } else {
                    return null;
                }
            }
            TL_msgs_state_req result = new TL_msgs_state_req();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(msg_ids.get(a));
            }
        }
    }

    public static class TL_importedContact extends TLObject {
        public static int constructor = 0xd0028438;

        public int user_id;
        public long client_id;

        public static TL_importedContact TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_importedContact.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_importedContact", constructor));
                } else {
                    return null;
                }
            }
            TL_importedContact result = new TL_importedContact();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            client_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt64(client_id);
        }
    }

    public static class auth_SentCode extends TLObject {
        public boolean phone_registered;
        public String phone_code_hash;
        public int send_call_timeout;
        public boolean is_password;

        public static auth_SentCode TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            auth_SentCode result = null;
            switch(constructor) {
                case 0xe325edcf:
                    result = new TL_auth_sentAppCode();
                    break;
                case 0xefed51d9:
                    result = new TL_auth_sentCode();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in auth_SentCode", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_auth_sentAppCode extends auth_SentCode {
        public static int constructor = 0xe325edcf;


        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_registered = stream.readBool(exception);
            phone_code_hash = stream.readString(exception);
            send_call_timeout = stream.readInt32(exception);
            is_password = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(phone_registered);
            stream.writeString(phone_code_hash);
            stream.writeInt32(send_call_timeout);
            stream.writeBool(is_password);
        }
    }

    public static class TL_auth_sentCode extends auth_SentCode {
        public static int constructor = 0xefed51d9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_registered = stream.readBool(exception);
            phone_code_hash = stream.readString(exception);
            send_call_timeout = stream.readInt32(exception);
            is_password = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(phone_registered);
            stream.writeString(phone_code_hash);
            stream.writeInt32(send_call_timeout);
            stream.writeBool(is_password);
        }
    }

    public static class TL_help_inviteText extends TLObject {
        public static int constructor = 0x18cb9f78;

        public String message;

        public static TL_help_inviteText TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_help_inviteText.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_help_inviteText", constructor));
                } else {
                    return null;
                }
            }
            TL_help_inviteText result = new TL_help_inviteText();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            message = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(message);
        }
    }

    public static class messages_AllStickers extends TLObject {
        public String hash;
        public ArrayList<StickerSet> sets = new ArrayList<>();
        public ArrayList<TL_stickerPack> packs = new ArrayList<>();
        public ArrayList<Document> documents = new ArrayList<>();

        public static messages_AllStickers TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_AllStickers result = null;
            switch(constructor) {
                case 0xd51dafdb:
                    result = new TL_messages_allStickers();
                    break;
                case 0xdcef3102:
                    result = new TL_messages_allStickers_old();
                    break;
                case 0x5ce352ec:
                    result = new TL_messages_allStickers_old2();
                    break;
                case 0xe86602c3:
                    result = new TL_messages_allStickersNotModified();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_AllStickers", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_allStickers extends messages_AllStickers {
        public static int constructor = 0xd51dafdb;


        public void readParams(AbsSerializedData stream, boolean exception) {
            hash = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                StickerSet object = StickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sets.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
            stream.writeInt32(0x1cb5c415);
            int count = sets.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                sets.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_allStickers_old extends TL_messages_allStickers {
        public static int constructor = 0xdcef3102;


        public void readParams(AbsSerializedData stream, boolean exception) {
            hash = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_stickerPack object = TL_stickerPack.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                packs.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Document object = Document.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                documents.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
            stream.writeInt32(0x1cb5c415);
            int count = packs.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                packs.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = documents.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                documents.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_allStickers_old2 extends TL_messages_allStickers {
        public static int constructor = 0x5ce352ec;


        public void readParams(AbsSerializedData stream, boolean exception) {
            hash = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_stickerPack object = TL_stickerPack.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                packs.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                StickerSet object = StickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                sets.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Document object = Document.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                documents.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
            stream.writeInt32(0x1cb5c415);
            int count = packs.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                packs.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = sets.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                sets.get(a).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = documents.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                documents.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_allStickersNotModified extends messages_AllStickers {
        public static int constructor = 0xe86602c3;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_checkedPhone extends TLObject {
        public static int constructor = 0x811ea28e;

        public boolean phone_registered;

        public static TL_auth_checkedPhone TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_auth_checkedPhone.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_auth_checkedPhone", constructor));
                } else {
                    return null;
                }
            }
            TL_auth_checkedPhone result = new TL_auth_checkedPhone();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            phone_registered = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(phone_registered);
        }
    }

    public static class UserProfilePhoto extends TLObject {
        public long photo_id;
        public FileLocation photo_small;
        public FileLocation photo_big;

        public static UserProfilePhoto TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            UserProfilePhoto result = null;
            switch(constructor) {
                case 0x4f11bae1:
                    result = new TL_userProfilePhotoEmpty();
                    break;
                case 0xd559d8c8:
                    result = new TL_userProfilePhoto();
                    break;
                case 0x990d1493:
                    result = new TL_userProfilePhoto_old();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in UserProfilePhoto", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_userProfilePhotoEmpty extends UserProfilePhoto {
        public static int constructor = 0x4f11bae1;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_userProfilePhoto extends UserProfilePhoto {
        public static int constructor = 0xd559d8c8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo_id = stream.readInt64(exception);
            photo_small = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
            photo_big = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(photo_id);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class TL_userProfilePhoto_old extends TL_userProfilePhoto {
        public static int constructor = 0x990d1493;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo_small = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
            photo_big = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class TL_authorization extends TLObject {
        public static int constructor = 0x7bf2e6f6;

        public long hash;
        public int flags;
        public String device_model;
        public String platform;
        public String system_version;
        public int api_id;
        public String app_name;
        public String app_version;
        public int date_created;
        public int date_active;
        public String ip;
        public String country;
        public String region;

        public static TL_authorization TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_authorization.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_authorization", constructor));
                } else {
                    return null;
                }
            }
            TL_authorization result = new TL_authorization();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            flags = stream.readInt32(exception);
            device_model = stream.readString(exception);
            platform = stream.readString(exception);
            system_version = stream.readString(exception);
            api_id = stream.readInt32(exception);
            app_name = stream.readString(exception);
            app_version = stream.readString(exception);
            date_created = stream.readInt32(exception);
            date_active = stream.readInt32(exception);
            ip = stream.readString(exception);
            country = stream.readString(exception);
            region = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            stream.writeInt32(flags);
            stream.writeString(device_model);
            stream.writeString(platform);
            stream.writeString(system_version);
            stream.writeInt32(api_id);
            stream.writeString(app_name);
            stream.writeString(app_version);
            stream.writeInt32(date_created);
            stream.writeInt32(date_active);
            stream.writeString(ip);
            stream.writeString(country);
            stream.writeString(region);
        }
    }

    public static class Server_DH_Params extends TLObject {
        public byte[] nonce;
        public byte[] server_nonce;
        public byte[] new_nonce_hash;
        public byte[] encrypted_answer;

        public static Server_DH_Params TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Server_DH_Params result = null;
            switch(constructor) {
                case 0x79cb045d:
                    result = new TL_server_DH_params_fail();
                    break;
                case 0xd0e8075c:
                    result = new TL_server_DH_params_ok();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Server_DH_Params", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_server_DH_params_fail extends Server_DH_Params {
        public static int constructor = 0x79cb045d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            new_nonce_hash = stream.readData(16, exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            encrypted_answer = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteArray(encrypted_answer);
        }
    }

    public static class TL_protoMessage extends TLObject {
        public static int constructor = 0x5bb8e511;

        public long msg_id;
        public int seqno;
        public int bytes;
        public TLObject body;

        public static TL_protoMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_protoMessage.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_protoMessage", constructor));
                } else {
                    return null;
                }
            }
            TL_protoMessage result = new TL_protoMessage();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            msg_id = stream.readInt64(exception);
            seqno = stream.readInt32(exception);
            bytes = stream.readInt32(exception);
            body = TLClassStore.Instance().TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            stream.writeInt32(seqno);
            stream.writeInt32(bytes);
            body.serializeToStream(stream);
        }
    }

    public static class TL_geochats_located extends TLObject {
        public static int constructor = 0x48feb267;

        public ArrayList<TL_chatLocated> results = new ArrayList<>();
        public ArrayList<GeoChatMessage> messages = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_geochats_located TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_geochats_located.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_geochats_located", constructor));
                } else {
                    return null;
                }
            }
            TL_geochats_located result = new TL_geochats_located();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_chatLocated object = TL_chatLocated.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                results.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                GeoChatMessage object = GeoChatMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_msgs_all_info extends TLObject {
        public static int constructor = 0x8cc0d131;

        public ArrayList<Long> msg_ids = new ArrayList<>();
        public String info;

        public static TL_msgs_all_info TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msgs_all_info.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msgs_all_info", constructor));
                } else {
                    return null;
                }
            }
            TL_msgs_all_info result = new TL_msgs_all_info();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                msg_ids.add(stream.readInt64(exception));
            }
            info = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = msg_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(msg_ids.get(a));
            }
            stream.writeString(info);
        }
    }

    public static class contacts_Blocked extends TLObject {
        public ArrayList<TL_contactBlocked> blocked = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public int count;

        public static contacts_Blocked TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            contacts_Blocked result = null;
            switch(constructor) {
                case 0x1c138d15:
                    result = new TL_contacts_blocked();
                    break;
                case 0x900802a1:
                    result = new TL_contacts_blockedSlice();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in contacts_Blocked", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_contacts_blocked extends contacts_Blocked {
        public static int constructor = 0x1c138d15;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contactBlocked object = TL_contactBlocked.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                blocked.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contactBlocked object = TL_contactBlocked.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                blocked.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_encryptedChatRequested_old extends TL_encryptedChatRequested {
        public static int constructor = 0xfda9a7b7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            participant_id = stream.readInt32(exception);
            g_a = stream.readByteArray(exception);
            nonce = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a);
            stream.writeByteArray(nonce);
        }
    }

    public static class TL_encryptedChatRequested extends EncryptedChat {
        public static int constructor = 0xc878527e;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            participant_id = stream.readInt32(exception);
            g_a = stream.readByteArray(exception);
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

    public static class TL_encryptedChat extends EncryptedChat {
        public static int constructor = 0xfa56ce36;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            participant_id = stream.readInt32(exception);
            g_a_or_b = stream.readByteArray(exception);
            key_fingerprint = stream.readInt64(exception);
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

    public static class TL_encryptedChat_old extends TL_encryptedChat {
        public static int constructor = 0x6601d14f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            participant_id = stream.readInt32(exception);
            g_a_or_b = stream.readByteArray(exception);
            nonce = stream.readByteArray(exception);
            key_fingerprint = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(admin_id);
            stream.writeInt32(participant_id);
            stream.writeByteArray(g_a_or_b);
            stream.writeByteArray(nonce);
            stream.writeInt64(key_fingerprint);
        }
    }

    public static class TL_encryptedChatEmpty extends EncryptedChat {
        public static int constructor = 0xab7ec0a0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class TL_encryptedChatWaiting extends EncryptedChat {
        public static int constructor = 0x3bf703dc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            admin_id = stream.readInt32(exception);
            participant_id = stream.readInt32(exception);
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

    public static class TL_encryptedChatDiscarded extends EncryptedChat {
        public static int constructor = 0x13d6dd27;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }

    public static class help_AppUpdate extends TLObject {
        public int id;
        public boolean critical;
        public String url;
        public String text;

        public static help_AppUpdate TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            help_AppUpdate result = null;
            switch(constructor) {
                case 0x8987f311:
                    result = new TL_help_appUpdate();
                    break;
                case 0xc45a6536:
                    result = new TL_help_noAppUpdate();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in help_AppUpdate", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_help_appUpdate extends help_AppUpdate {
        public static int constructor = 0x8987f311;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            critical = stream.readBool(exception);
            url = stream.readString(exception);
            text = stream.readString(exception);
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

    public static class messages_Stickers extends TLObject {
        public String hash;
        public ArrayList<Document> stickers = new ArrayList<>();

        public static messages_Stickers TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            messages_Stickers result = null;
            switch(constructor) {
                case 0xf1749a22:
                    result = new TL_messages_stickersNotModified();
                    break;
                case 0x8a8ecd32:
                    result = new TL_messages_stickers();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in messages_Stickers", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messages_stickersNotModified extends messages_Stickers {
        public static int constructor = 0xf1749a22;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_messages_stickers extends messages_Stickers {
        public static int constructor = 0x8a8ecd32;


        public void readParams(AbsSerializedData stream, boolean exception) {
            hash = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Document object = Document.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                stickers.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
            stream.writeInt32(0x1cb5c415);
            int count = stickers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stickers.get(a).serializeToStream(stream);
            }
        }
    }

    public static class Video extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public int duration;
        public int size;
        public PhotoSize thumb;
        public int dc_id;
        public int w;
        public int h;
        public String caption;
        public byte[] key;
        public byte[] iv;
        public String mime_type;

        public static Video TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Video result = null;
            switch(constructor) {
                case 0xf72887d3:
                    result = new TL_video();
                    break;
                case 0xee9f4a4d:
                    result = new TL_video_old3();
                    break;
                case 0x55555553:
                    result = new TL_videoEncrypted();
                    break;
                case 0x5a04a49f:
                    result = new TL_video_old();
                    break;
                case 0x388fa391:
                    result = new TL_video_old2();
                    break;
                case 0xc10658a8:
                    result = new TL_videoEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Video", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_video extends Video {
        public static int constructor = 0xf72887d3;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_video_old3 extends TL_video {
        public static int constructor = 0xee9f4a4d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_videoEncrypted extends TL_video {
        public static int constructor = 0x55555553;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            caption = stream.readString(exception);
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_video_old extends TL_video {
        public static int constructor = 0x5a04a49f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            caption = stream.readString(exception);
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
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

    public static class TL_video_old2 extends TL_video {
        public static int constructor = 0x388fa391;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            caption = stream.readString(exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeString(caption);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_videoEmpty extends Video {
        public static int constructor = 0xc10658a8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_messages_affectedMessages extends TLObject {
        public static int constructor = 0x84d19185;

        public int pts;
        public int pts_count;

        public static TL_messages_affectedMessages TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_affectedMessages.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_affectedMessages", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_affectedMessages result = new TL_messages_affectedMessages();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
        }
    }

    public static class geochats_Messages extends TLObject {
        public int count;
        public ArrayList<GeoChatMessage> messages = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static geochats_Messages TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            geochats_Messages result = null;
            switch(constructor) {
                case 0xbc5863e8:
                    result = new TL_geochats_messagesSlice();
                    break;
                case 0xd1526db1:
                    result = new TL_geochats_messages();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in geochats_Messages", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_geochats_messagesSlice extends geochats_Messages {
        public static int constructor = 0xbc5863e8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            count = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                GeoChatMessage object = GeoChatMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                GeoChatMessage object = GeoChatMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_stickerPack extends TLObject {
        public static int constructor = 0x12b299d4;

        public String emoticon;
        public ArrayList<Long> documents = new ArrayList<>();

        public static TL_stickerPack TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_stickerPack.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_stickerPack", constructor));
                } else {
                    return null;
                }
            }
            TL_stickerPack result = new TL_stickerPack();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            emoticon = stream.readString(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                documents.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(emoticon);
            stream.writeInt32(0x1cb5c415);
            int count = documents.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(documents.get(a));
            }
        }
    }

    public static class help_AppChangelog extends TLObject {
        public String text;

        public static help_AppChangelog TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            help_AppChangelog result = null;
            switch(constructor) {
                case 0xaf7e0394:
                    result = new TL_help_appChangelogEmpty();
                    break;
                case 0x4668e6bd:
                    result = new TL_help_appChangelog();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in help_AppChangelog", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_help_appChangelogEmpty extends help_AppChangelog {
        public static int constructor = 0xaf7e0394;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_help_appChangelog extends help_AppChangelog {
        public static int constructor = 0x4668e6bd;


        public void readParams(AbsSerializedData stream, boolean exception) {
            text = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    public static class TL_keyboardButton extends TLObject {
        public static int constructor = 0xa2fa4880;

        public String text;

        public static TL_keyboardButton TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_keyboardButton.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_keyboardButton", constructor));
                } else {
                    return null;
                }
            }
            TL_keyboardButton result = new TL_keyboardButton();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            text = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    public static class TL_disabledFeature extends TLObject {
        public static int constructor = 0xae636f24;

        public String feature;
        public String description;

        public static TL_disabledFeature TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_disabledFeature.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_disabledFeature", constructor));
                } else {
                    return null;
                }
            }
            TL_disabledFeature result = new TL_disabledFeature();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            feature = stream.readString(exception);
            description = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(feature);
            stream.writeString(description);
        }
    }

    public static class InputStickerSet extends TLObject {
        public long id;
        public long access_hash;
        public String short_name;

        public static InputStickerSet TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputStickerSet result = null;
            switch(constructor) {
                case 0xffb62b95:
                    result = new TL_inputStickerSetEmpty();
                    break;
                case 0x9de7a269:
                    result = new TL_inputStickerSetID();
                    break;
                case 0x861cc8a0:
                    result = new TL_inputStickerSetShortName();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputStickerSet", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputStickerSetEmpty extends InputStickerSet {
        public static int constructor = 0xffb62b95;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputStickerSetID extends InputStickerSet {
        public static int constructor = 0x9de7a269;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class TL_inputStickerSetShortName extends InputStickerSet {
        public static int constructor = 0x861cc8a0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            short_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(short_name);
        }
    }

    public static class storage_FileType extends TLObject {

        public static storage_FileType TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            storage_FileType result = null;
            switch(constructor) {
                case 0xaa963b05:
                    result = new TL_storage_fileUnknown();
                    break;
                case 0xb3cea0e4:
                    result = new TL_storage_fileMp4();
                    break;
                case 0x1081464c:
                    result = new TL_storage_fileWebp();
                    break;
                case 0xa4f63c0:
                    result = new TL_storage_filePng();
                    break;
                case 0xcae1aadf:
                    result = new TL_storage_fileGif();
                    break;
                case 0xae1e508d:
                    result = new TL_storage_filePdf();
                    break;
                case 0x528a0677:
                    result = new TL_storage_fileMp3();
                    break;
                case 0x7efe0e:
                    result = new TL_storage_fileJpeg();
                    break;
                case 0x4b09ebbc:
                    result = new TL_storage_fileMov();
                    break;
                case 0x40bc6f52:
                    result = new TL_storage_filePartial();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in storage_FileType", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_storage_fileUnknown extends storage_FileType {
        public static int constructor = 0xaa963b05;


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

    public static class TL_storage_filePdf extends storage_FileType {
        public static int constructor = 0xae1e508d;


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

    public static class TL_storage_fileMov extends storage_FileType {
        public static int constructor = 0x4b09ebbc;


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

    public static class TL_account_authorizations extends TLObject {
        public static int constructor = 0x1250abde;

        public ArrayList<TL_authorization> authorizations = new ArrayList<>();

        public static TL_account_authorizations TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_account_authorizations.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_authorizations", constructor));
                } else {
                    return null;
                }
            }
            TL_account_authorizations result = new TL_account_authorizations();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_authorization object = TL_authorization.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                authorizations.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = authorizations.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                authorizations.get(a).serializeToStream(stream);
            }
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

        public static TL_p_q_inner_data TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_p_q_inner_data.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_p_q_inner_data", constructor));
                } else {
                    return null;
                }
            }
            TL_p_q_inner_data result = new TL_p_q_inner_data();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            pq = stream.readByteArray(exception);
            p = stream.readByteArray(exception);
            q = stream.readByteArray(exception);
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            new_nonce = stream.readData(32, exception);
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

    public static class InputPhoto extends TLObject {
        public long id;
        public long access_hash;

        public static InputPhoto TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputPhoto result = null;
            switch(constructor) {
                case 0x1cd7bf0d:
                    result = new TL_inputPhotoEmpty();
                    break;
                case 0xfb95c6c4:
                    result = new TL_inputPhoto();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputPhoto", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputPhotoEmpty extends InputPhoto {
        public static int constructor = 0x1cd7bf0d;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPhoto extends InputPhoto {
        public static int constructor = 0xfb95c6c4;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
        }
    }

    public static class contacts_Contacts extends TLObject {
        public ArrayList<TL_contact> contacts = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static contacts_Contacts TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            contacts_Contacts result = null;
            switch(constructor) {
                case 0xb74ba9d2:
                    result = new TL_contacts_contactsNotModified();
                    break;
                case 0x6f8b8cb2:
                    result = new TL_contacts_contacts();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in contacts_Contacts", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_contacts_contactsNotModified extends contacts_Contacts {
        public static int constructor = 0xb74ba9d2;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_contacts extends contacts_Contacts {
        public static int constructor = 0x6f8b8cb2;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contact object = TL_contact.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                contacts.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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

    public static class TL_contactBlocked extends TLObject {
        public static int constructor = 0x561bc879;

        public int user_id;
        public int date;

        public static TL_contactBlocked TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contactBlocked.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contactBlocked", constructor));
                } else {
                    return null;
                }
            }
            TL_contactBlocked result = new TL_contactBlocked();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
        }
    }

    public static class Updates extends TLObject {
        public int flags;
        public int id;
        public int from_id;
        public int chat_id;
        public String message;
        public int pts;
        public int pts_count;
        public int date;
        public int fwd_from_id;
        public int fwd_date;
        public int reply_to_msg_id;
        public ArrayList<MessageEntity> entities = new ArrayList<>();
        public ArrayList<Update> updates = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public int seq;
        public int user_id;
        public Update update;
        public int seq_start;

        public static Updates TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Updates result = null;
            switch(constructor) {
                case 0xf9409b3d:
                    result = new TL_updateShortChatMessage();
                    break;
                case 0x74ae4240:
                    result = new TL_updates();
                    break;
                case 0x3f32d858:
                    result = new TL_updateShortMessage();
                    break;
                case 0x78d4dec1:
                    result = new TL_updateShort();
                    break;
                case 0x725b04c3:
                    result = new TL_updatesCombined();
                    break;
                case 0xe317af7e:
                    result = new TL_updatesTooLong();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Updates", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_updateShortChatMessage extends Updates {
        public static int constructor = 0xf9409b3d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            chat_id = stream.readInt32(exception);
            message = stream.readString(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
            date = stream.readInt32(exception);
            if ((flags & 4) != 0) {
                fwd_from_id = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                fwd_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reply_to_msg_id = stream.readInt32(exception);
            }
            if ((flags & 128) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    MessageEntity object = MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    entities.add(object);
                }
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            stream.writeInt32(chat_id);
            stream.writeString(message);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
            stream.writeInt32(date);
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_from_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = entities.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    entities.get(a).serializeToStream(stream);
                }
            }
        }
    }

    public static class TL_updates extends Updates {
        public static int constructor = 0x74ae4240;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Update object = Update.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                updates.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            date = stream.readInt32(exception);
            seq = stream.readInt32(exception);
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
        public static int constructor = 0x3f32d858;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            user_id = stream.readInt32(exception);
            message = stream.readString(exception);
            pts = stream.readInt32(exception);
            pts_count = stream.readInt32(exception);
            date = stream.readInt32(exception);
            if ((flags & 4) != 0) {
                fwd_from_id = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                fwd_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reply_to_msg_id = stream.readInt32(exception);
            }
            if ((flags & 128) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    MessageEntity object = MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    entities.add(object);
                }
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(user_id);
            stream.writeString(message);
            stream.writeInt32(pts);
            stream.writeInt32(pts_count);
            stream.writeInt32(date);
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_from_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = entities.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    entities.get(a).serializeToStream(stream);
                }
            }
        }
    }

    public static class TL_updateShort extends Updates {
        public static int constructor = 0x78d4dec1;


        public void readParams(AbsSerializedData stream, boolean exception) {
            update = Update.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            update.serializeToStream(stream);
            stream.writeInt32(date);
        }
    }

    public static class TL_updatesCombined extends Updates {
        public static int constructor = 0x725b04c3;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Update object = Update.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                updates.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            date = stream.readInt32(exception);
            seq_start = stream.readInt32(exception);
            seq = stream.readInt32(exception);
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

    public static class InputPeerNotifyEvents extends TLObject {

        public static InputPeerNotifyEvents TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputPeerNotifyEvents result = null;
            switch(constructor) {
                case 0xe86a2c74:
                    result = new TL_inputPeerNotifyEventsAll();
                    break;
                case 0xf03064d8:
                    result = new TL_inputPeerNotifyEventsEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputPeerNotifyEvents", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
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

    public static class ReplyMarkup extends TLObject {
        public int flags;
        public ArrayList<TL_keyboardButtonRow> rows = new ArrayList<>();

        public static ReplyMarkup TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ReplyMarkup result = null;
            switch(constructor) {
                case 0xa03e5b85:
                    result = new TL_replyKeyboardHide();
                    break;
                case 0x3502758c:
                    result = new TL_replyKeyboardMarkup();
                    break;
                case 0xf4108aa0:
                    result = new TL_replyKeyboardForceReply();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ReplyMarkup", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_replyKeyboardHide extends ReplyMarkup {
        public static int constructor = 0xa03e5b85;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
        }
    }

    public static class TL_replyKeyboardMarkup extends ReplyMarkup {
        public static int constructor = 0x3502758c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_keyboardButtonRow object = TL_keyboardButtonRow.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                rows.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(0x1cb5c415);
            int count = rows.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                rows.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_replyKeyboardForceReply extends ReplyMarkup {
        public static int constructor = 0xf4108aa0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
        }
    }

    public static class TL_msgs_state_info extends TLObject {
        public static int constructor = 0x04deb57d;

        public long req_msg_id;
        public String info;

        public static TL_msgs_state_info TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_msgs_state_info.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_msgs_state_info", constructor));
                } else {
                    return null;
                }
            }
            TL_msgs_state_info result = new TL_msgs_state_info();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            req_msg_id = stream.readInt64(exception);
            info = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            stream.writeString(info);
        }
    }

    public static class updates_Difference extends TLObject {
        public int date;
        public int seq;
        public ArrayList<Message> new_messages = new ArrayList<>();
        public ArrayList<EncryptedMessage> new_encrypted_messages = new ArrayList<>();
        public ArrayList<Update> other_updates = new ArrayList<>();
        public ArrayList<Chat> chats = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();
        public TL_updates_state intermediate_state;
        public TL_updates_state state;

        public static updates_Difference TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            updates_Difference result = null;
            switch(constructor) {
                case 0x5d75a138:
                    result = new TL_updates_differenceEmpty();
                    break;
                case 0xa8fb1981:
                    result = new TL_updates_differenceSlice();
                    break;
                case 0xf49ca0:
                    result = new TL_updates_difference();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in updates_Difference", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_updates_differenceEmpty extends updates_Difference {
        public static int constructor = 0x5d75a138;


        public void readParams(AbsSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            seq = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            stream.writeInt32(seq);
        }
    }

    public static class TL_updates_differenceSlice extends updates_Difference {
        public static int constructor = 0xa8fb1981;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                new_messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                EncryptedMessage object = EncryptedMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                new_encrypted_messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Update object = Update.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                other_updates.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
            intermediate_state = TL_updates_state.TLdeserialize(stream, stream.readInt32(exception), exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Message object = Message.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                new_messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                EncryptedMessage object = EncryptedMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                new_encrypted_messages.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Update object = Update.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                other_updates.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
            state = TL_updates_state.TLdeserialize(stream, stream.readInt32(exception), exception);
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

    public static class ChatPhoto extends TLObject {
        public FileLocation photo_small;
        public FileLocation photo_big;

        public static ChatPhoto TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ChatPhoto result = null;
            switch(constructor) {
                case 0x37c1011c:
                    result = new TL_chatPhotoEmpty();
                    break;
                case 0x6153276a:
                    result = new TL_chatPhoto();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ChatPhoto", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatPhotoEmpty extends ChatPhoto {
        public static int constructor = 0x37c1011c;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_chatPhoto extends ChatPhoto {
        public static int constructor = 0x6153276a;


        public void readParams(AbsSerializedData stream, boolean exception) {
            photo_small = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
            photo_big = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            photo_small.serializeToStream(stream);
            photo_big.serializeToStream(stream);
        }
    }

    public static class TL_decryptedMessageLayer extends TLObject {
        public static int constructor = 0x1be31789;

        public byte[] random_bytes;
        public int layer;
        public int in_seq_no;
        public int out_seq_no;
        public DecryptedMessage message;

        public static TL_decryptedMessageLayer TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_decryptedMessageLayer.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_decryptedMessageLayer", constructor));
                } else {
                    return null;
                }
            }
            TL_decryptedMessageLayer result = new TL_decryptedMessageLayer();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            random_bytes = stream.readByteArray(exception);
            layer = stream.readInt32(exception);
            in_seq_no = stream.readInt32(exception);
            out_seq_no = stream.readInt32(exception);
            message = DecryptedMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(random_bytes);
            stream.writeInt32(layer);
            stream.writeInt32(in_seq_no);
            stream.writeInt32(out_seq_no);
            message.serializeToStream(stream);
        }
    }

    public static class Audio extends TLObject {
        public long id;
        public long access_hash;
        public int user_id;
        public int date;
        public int duration;
        public int size;
        public int dc_id;
        public String mime_type;
        public byte[] key;
        public byte[] iv;

        public static Audio TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Audio result = null;
            switch(constructor) {
                case 0x586988d8:
                    result = new TL_audioEmpty();
                    break;
                case 0x427425e7:
                    result = new TL_audio_old();
                    break;
                case 0xf9e35055:
                    result = new TL_audio();
                    break;
                case 0x555555F6:
                    result = new TL_audioEncrypted();
                    break;
                case 0xc7ac6496:
                    result = new TL_audio_old2();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Audio", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_audio_old extends TL_audio {
        public static int constructor = 0x427425e7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            dc_id = stream.readInt32(exception);
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

    public static class TL_audio_old2 extends TL_audio {
        public static int constructor = 0xc7ac6496;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            dc_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            stream.writeInt32(dc_id);
        }
    }

    public static class TL_audio extends Audio {
        public static int constructor = 0xf9e35055;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            dc_id = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeInt32(duration);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            stream.writeInt32(dc_id);
        }
    }

    public static class TL_audioEncrypted extends TL_audio {
        public static int constructor = 0x555555F6;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            duration = stream.readInt32(exception);
            size = stream.readInt32(exception);
            dc_id = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_audioEmpty extends Audio {
        public static int constructor = 0x586988d8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_contacts_found extends TLObject {
        public static int constructor = 0x566000e;

        public ArrayList<TL_contactFound> results = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_contacts_found TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contacts_found.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contacts_found", constructor));
                } else {
                    return null;
                }
            }
            TL_contacts_found result = new TL_contacts_found();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_contactFound object = TL_contactFound.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                results.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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
        public byte[] key;
        public byte[] iv;
        public ArrayList<DocumentAttribute> attributes = new ArrayList<>();

        public static Document TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Document result = null;
            switch(constructor) {
                case 0x55555556:
                    result = new TL_documentEncrypted_old();
                    break;
                case 0x9efc6326:
                    result = new TL_document_old();
                    break;
                case 0x36f8c871:
                    result = new TL_documentEmpty();
                    break;
                case 0x55555558:
                    result = new TL_documentEncrypted();
                    break;
                case 0xf9a39f4f:
                    result = new TL_document();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Document", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_documentEncrypted_old extends TL_document {
        public static int constructor = 0x55555556;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            file_name = stream.readString(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
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

    public static class TL_document_old extends TL_document {
        public static int constructor = 0x9efc6326;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
            date = stream.readInt32(exception);
            file_name = stream.readString(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
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

    public static class TL_documentEmpty extends Document {
        public static int constructor = 0x36f8c871;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_documentEncrypted extends Document {
        public static int constructor = 0x55555558;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                attributes.add(object);
            }
            key = stream.readByteArray(exception);
            iv = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(0x1cb5c415);
            int count = attributes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                attributes.get(a).serializeToStream(stream);
            }
            stream.writeByteArray(key);
            stream.writeByteArray(iv);
        }
    }

    public static class TL_document extends Document {
        public static int constructor = 0xf9a39f4f;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
            date = stream.readInt32(exception);
            mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
            dc_id = stream.readInt32(exception);
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                attributes.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(date);
            stream.writeString(mime_type);
            stream.writeInt32(size);
            thumb.serializeToStream(stream);
            stream.writeInt32(dc_id);
            stream.writeInt32(0x1cb5c415);
            int count = attributes.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                attributes.get(a).serializeToStream(stream);
            }
        }
    }

    public static class MessagesFilter extends TLObject {

        public static MessagesFilter TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            MessagesFilter result = null;
            switch(constructor) {
                case 0x9eddf188:
                    result = new TL_inputMessagesFilterDocument();
                    break;
                case 0x5afbf764:
                    result = new TL_inputMessagesFilterAudioDocuments();
                    break;
                case 0x9fc00e65:
                    result = new TL_inputMessagesFilterVideo();
                    break;
                case 0x9609a51c:
                    result = new TL_inputMessagesFilterPhotos();
                    break;
                case 0xd95e73bb:
                    result = new TL_inputMessagesFilterPhotoVideoDocuments();
                    break;
                case 0xcfc87522:
                    result = new TL_inputMessagesFilterAudio();
                    break;
                case 0x57e2f66c:
                    result = new TL_inputMessagesFilterEmpty();
                    break;
                case 0x56e9f0e4:
                    result = new TL_inputMessagesFilterPhotoVideo();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MessagesFilter", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputMessagesFilterDocument extends MessagesFilter {
        public static int constructor = 0x9eddf188;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterAudioDocuments extends MessagesFilter {
        public static int constructor = 0x5afbf764;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterVideo extends MessagesFilter {
        public static int constructor = 0x9fc00e65;


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

    public static class TL_inputMessagesFilterPhotoVideoDocuments extends MessagesFilter {
        public static int constructor = 0xd95e73bb;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputMessagesFilterAudio extends MessagesFilter {
        public static int constructor = 0xcfc87522;


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

    public static class TL_inputMessagesFilterUrl extends MessagesFilter {
        public static int constructor = 0x7ef0dd87;


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

    public static class TL_account_passwordSettings extends TLObject {
        public static int constructor = 0xb7b72ab3;

        public String email;

        public static TL_account_passwordSettings TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_account_passwordSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_passwordSettings", constructor));
                } else {
                    return null;
                }
            }
            TL_account_passwordSettings result = new TL_account_passwordSettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            email = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(email);
        }
    }

    public static class ExportedChatInvite extends TLObject {
        public String link;

        public static ExportedChatInvite TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ExportedChatInvite result = null;
            switch(constructor) {
                case 0xfc2e05bc:
                    result = new TL_chatInviteExported();
                    break;
                case 0x69df3769:
                    result = new TL_chatInviteEmpty();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ExportedChatInvite", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatInviteExported extends ExportedChatInvite {
        public static int constructor = 0xfc2e05bc;


        public void readParams(AbsSerializedData stream, boolean exception) {
            link = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(link);
        }
    }

    public static class TL_chatInviteEmpty extends ExportedChatInvite {
        public static int constructor = 0x69df3769;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_http_wait extends TLObject {
        public static int constructor = 0x9299359f;

        public int max_delay;
        public int wait_after;
        public int max_wait;

        public static TL_http_wait TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_http_wait.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_http_wait", constructor));
                } else {
                    return null;
                }
            }
            TL_http_wait result = new TL_http_wait();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            max_delay = stream.readInt32(exception);
            wait_after = stream.readInt32(exception);
            max_wait = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_delay);
            stream.writeInt32(wait_after);
            stream.writeInt32(max_wait);
        }
    }

    public static class InputPhotoCrop extends TLObject {
        public double crop_left;
        public double crop_top;
        public double crop_width;

        public static InputPhotoCrop TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            InputPhotoCrop result = null;
            switch(constructor) {
                case 0xade6b004:
                    result = new TL_inputPhotoCropAuto();
                    break;
                case 0xd9915325:
                    result = new TL_inputPhotoCrop();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in InputPhotoCrop", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_inputPhotoCropAuto extends InputPhotoCrop {
        public static int constructor = 0xade6b004;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputPhotoCrop extends InputPhotoCrop {
        public static int constructor = 0xd9915325;


        public void readParams(AbsSerializedData stream, boolean exception) {
            crop_left = stream.readDouble(exception);
            crop_top = stream.readDouble(exception);
            crop_width = stream.readDouble(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeDouble(crop_left);
            stream.writeDouble(crop_top);
            stream.writeDouble(crop_width);
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

        public static Chat TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Chat result = null;
            switch(constructor) {
                case 0xfb0ccc41:
                    result = new TL_chatForbidden();
                    break;
                case 0x75eaea5a:
                    result = new TL_geoChat();
                    break;
                case 0x9ba2d800:
                    result = new TL_chatEmpty();
                    break;
                case 0x6e9c9bc7:
                    result = new TL_chat();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Chat", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_chatForbidden extends Chat {
        public static int constructor = 0xfb0ccc41;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            title = stream.readString(exception);
            date = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            access_hash = stream.readInt64(exception);
            title = stream.readString(exception);
            address = stream.readString(exception);
            venue = stream.readString(exception);
            geo = GeoPoint.TLdeserialize(stream, stream.readInt32(exception), exception);
            photo = ChatPhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            participants_count = stream.readInt32(exception);
            date = stream.readInt32(exception);
            checked_in = stream.readBool(exception);
            version = stream.readInt32(exception);
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


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            title = stream.readString(exception);
            photo = ChatPhoto.TLdeserialize(stream, stream.readInt32(exception), exception);
            participants_count = stream.readInt32(exception);
            date = stream.readInt32(exception);
            left = stream.readBool(exception);
            version = stream.readInt32(exception);
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

    public static class TL_messages_chats extends TLObject {
        public static int constructor = 0x64ff9fd5;

        public ArrayList<Chat> chats = new ArrayList<>();

        public static TL_messages_chats TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_messages_chats.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_messages_chats", constructor));
                } else {
                    return null;
                }
            }
            TL_messages_chats result = new TL_messages_chats();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                Chat object = Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                chats.add(object);
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
        }
    }

    public static class ContactLink extends TLObject {

        public static ContactLink TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            ContactLink result = null;
            switch(constructor) {
                case 0xfeedd3ad:
                    result = new TL_contactLinkNone();
                    break;
                case 0xd502c2d0:
                    result = new TL_contactLinkContact();
                    break;
                case 0x268f3f59:
                    result = new TL_contactLinkHasPhone();
                    break;
                case 0x5f4f9247:
                    result = new TL_contactLinkUnknown();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ContactLink", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_contactLinkNone extends ContactLink {
        public static int constructor = 0xfeedd3ad;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contactLinkContact extends ContactLink {
        public static int constructor = 0xd502c2d0;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contactLinkHasPhone extends ContactLink {
        public static int constructor = 0x268f3f59;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contactLinkUnknown extends ContactLink {
        public static int constructor = 0x5f4f9247;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class PeerNotifyEvents extends TLObject {

        public static PeerNotifyEvents TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            PeerNotifyEvents result = null;
            switch(constructor) {
                case 0xadd53cb3:
                    result = new TL_peerNotifyEventsEmpty();
                    break;
                case 0x6d1ded88:
                    result = new TL_peerNotifyEventsAll();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PeerNotifyEvents", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
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

    public static class SendMessageAction extends TLObject {
        public int progress;

        public static SendMessageAction TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            SendMessageAction result = null;
            switch(constructor) {
                case 0xd52f73f7:
                    result = new TL_sendMessageRecordAudioAction();
                    break;
                case 0x92042ff7:
                    result = new TL_sendMessageUploadVideoAction_old();
                    break;
                case 0xe6ac8a6f:
                    result = new TL_sendMessageUploadAudioAction_old();
                    break;
                case 0xf351d7ab:
                    result = new TL_sendMessageUploadAudioAction();
                    break;
                case 0xd1d34a26:
                    result = new TL_sendMessageUploadPhotoAction();
                    break;
                case 0x8faee98e:
                    result = new TL_sendMessageUploadDocumentAction_old();
                    break;
                case 0xe9763aec:
                    result = new TL_sendMessageUploadVideoAction();
                    break;
                case 0xfd5ec8f5:
                    result = new TL_sendMessageCancelAction();
                    break;
                case 0x176f8ba1:
                    result = new TL_sendMessageGeoLocationAction();
                    break;
                case 0x628cbc6f:
                    result = new TL_sendMessageChooseContactAction();
                    break;
                case 0x16bf744e:
                    result = new TL_sendMessageTypingAction();
                    break;
                case 0x990a3c1a:
                    result = new TL_sendMessageUploadPhotoAction_old();
                    break;
                case 0xaa0cd9e4:
                    result = new TL_sendMessageUploadDocumentAction();
                    break;
                case 0xa187d66f:
                    result = new TL_sendMessageRecordVideoAction();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in SendMessageAction", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_sendMessageRecordAudioAction extends SendMessageAction {
        public static int constructor = 0xd52f73f7;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadVideoAction_old extends TL_sendMessageUploadVideoAction {
        public static int constructor = 0x92042ff7;

        public void readParams(AbsSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadAudioAction_old extends TL_sendMessageUploadAudioAction {
        public static int constructor = 0xe6ac8a6f;

        public void readParams(AbsSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadAudioAction extends SendMessageAction {
        public static int constructor = 0xf351d7ab;


        public void readParams(AbsSerializedData stream, boolean exception) {
            progress = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(progress);
        }
    }

    public static class TL_sendMessageUploadPhotoAction extends SendMessageAction {
        public static int constructor = 0xd1d34a26;


        public void readParams(AbsSerializedData stream, boolean exception) {
            progress = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(progress);
        }
    }

    public static class TL_sendMessageUploadDocumentAction_old extends TL_sendMessageUploadDocumentAction {
        public static int constructor = 0x8faee98e;

        public void readParams(AbsSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadVideoAction extends SendMessageAction {
        public static int constructor = 0xe9763aec;


        public void readParams(AbsSerializedData stream, boolean exception) {
            progress = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(progress);
        }
    }

    public static class TL_sendMessageCancelAction extends SendMessageAction {
        public static int constructor = 0xfd5ec8f5;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageGeoLocationAction extends SendMessageAction {
        public static int constructor = 0x176f8ba1;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageChooseContactAction extends SendMessageAction {
        public static int constructor = 0x628cbc6f;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageTypingAction extends SendMessageAction {
        public static int constructor = 0x16bf744e;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadPhotoAction_old extends TL_sendMessageUploadPhotoAction {
        public static int constructor = 0x990a3c1a;

        public void readParams(AbsSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_sendMessageUploadDocumentAction extends SendMessageAction {
        public static int constructor = 0xaa0cd9e4;


        public void readParams(AbsSerializedData stream, boolean exception) {
            progress = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(progress);
        }
    }

    public static class TL_sendMessageRecordVideoAction extends SendMessageAction {
        public static int constructor = 0xa187d66f;


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

        public static TL_client_DH_inner_data TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_client_DH_inner_data.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_client_DH_inner_data", constructor));
                } else {
                    return null;
                }
            }
            TL_client_DH_inner_data result = new TL_client_DH_inner_data();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            nonce = stream.readData(16, exception);
            server_nonce = stream.readData(16, exception);
            retry_id = stream.readInt64(exception);
            g_b = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeInt64(retry_id);
            stream.writeByteArray(g_b);
        }
    }

    public static class TL_null extends TLObject {
        public static int constructor = 0x56730bcc;


        public static TL_null TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_null.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_null", constructor));
                } else {
                    return null;
                }
            }
            TL_null result = new TL_null();
            result.readParams(stream, exception);
            return result;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class account_Password extends TLObject {
        public byte[] current_salt;
        public byte[] new_salt;
        public String hint;
        public boolean has_recovery;
        public String email_unconfirmed_pattern;

        public static account_Password TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            account_Password result = null;
            switch(constructor) {
                case 0x7c18141c:
                    result = new TL_account_password();
                    break;
                case 0x96dabc18:
                    result = new TL_account_noPassword();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_Password", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_account_password extends account_Password {
        public static int constructor = 0x7c18141c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            current_salt = stream.readByteArray(exception);
            new_salt = stream.readByteArray(exception);
            hint = stream.readString(exception);
            has_recovery = stream.readBool(exception);
            email_unconfirmed_pattern = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(current_salt);
            stream.writeByteArray(new_salt);
            stream.writeString(hint);
            stream.writeBool(has_recovery);
            stream.writeString(email_unconfirmed_pattern);
        }
    }

    public static class TL_account_noPassword extends account_Password {
        public static int constructor = 0x96dabc18;


        public void readParams(AbsSerializedData stream, boolean exception) {
            new_salt = stream.readByteArray(exception);
            email_unconfirmed_pattern = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(new_salt);
            stream.writeString(email_unconfirmed_pattern);
        }
    }

    public static class DocumentAttribute extends TLObject {
        public int w;
        public int h;
        public int duration;
        public String alt;
        public InputStickerSet stickerset;
        public String title;
        public String performer;
        public String file_name;

        public static DocumentAttribute TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            DocumentAttribute result = null;
            switch(constructor) {
                case 0x11b58939:
                    result = new TL_documentAttributeAnimated();
                    break;
                case 0xfb0a5727:
                    result = new TL_documentAttributeSticker_old();
                    break;
                case 0x6c37c15c:
                    result = new TL_documentAttributeImageSize();
                    break;
                case 0x51448e5:
                    result = new TL_documentAttributeAudio_old();
                    break;
                case 0x3a556302:
                    result = new TL_documentAttributeSticker();
                    break;
                case 0x5910cccb:
                    result = new TL_documentAttributeVideo();
                    break;
                case 0xded218e0:
                    result = new TL_documentAttributeAudio();
                    break;
                case 0x994c9882:
                    result = new TL_documentAttributeSticker_old2();
                    break;
                case 0x15590068:
                    result = new TL_documentAttributeFilename();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in DocumentAttribute", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_documentAttributeAnimated extends DocumentAttribute {
        public static int constructor = 0x11b58939;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_documentAttributeSticker_old extends TL_documentAttributeSticker {
        public static int constructor = 0xfb0a5727;

        public void readParams(AbsSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_documentAttributeImageSize extends DocumentAttribute {
        public static int constructor = 0x6c37c15c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_documentAttributeAudio_old extends TL_documentAttributeAudio {
        public static int constructor = 0x51448e5;


        public void readParams(AbsSerializedData stream, boolean exception) {
            duration = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
        }
    }

    public static class TL_documentAttributeFilename extends DocumentAttribute {
        public static int constructor = 0x15590068;


        public void readParams(AbsSerializedData stream, boolean exception) {
            file_name = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(file_name);
        }
    }

    public static class TL_documentAttributeSticker extends DocumentAttribute {
        public static int constructor = 0x3a556302;


        public void readParams(AbsSerializedData stream, boolean exception) {
            alt = stream.readString(exception);
            stickerset = InputStickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(alt);
            stickerset.serializeToStream(stream);
        }
    }

    public static class TL_documentAttributeVideo extends DocumentAttribute {
        public static int constructor = 0x5910cccb;


        public void readParams(AbsSerializedData stream, boolean exception) {
            duration = stream.readInt32(exception);
            w = stream.readInt32(exception);
            h = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
            stream.writeInt32(w);
            stream.writeInt32(h);
        }
    }

    public static class TL_documentAttributeAudio extends DocumentAttribute {
        public static int constructor = 0xded218e0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            duration = stream.readInt32(exception);
            title = stream.readString(exception);
            performer = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(duration);
            stream.writeString(title);
            stream.writeString(performer);
        }
    }

    public static class TL_documentAttributeSticker_old2 extends TL_documentAttributeSticker {
        public static int constructor = 0x994c9882;


        public void readParams(AbsSerializedData stream, boolean exception) {
            alt = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(alt);
        }
    }

    public static class TL_contacts_importedContacts extends TLObject {
        public static int constructor = 0xad524315;

        public ArrayList<TL_importedContact> imported = new ArrayList<>();
        public ArrayList<Long> retry_contacts = new ArrayList<>();
        public ArrayList<User> users = new ArrayList<>();

        public static TL_contacts_importedContacts TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_contacts_importedContacts.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contacts_importedContacts", constructor));
                } else {
                    return null;
                }
            }
            TL_contacts_importedContacts result = new TL_contacts_importedContacts();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_importedContact object = TL_importedContact.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                imported.add(object);
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                retry_contacts.add(stream.readInt64(exception));
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
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
            count = retry_contacts.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(retry_contacts.get(a));
            }
            stream.writeInt32(0x1cb5c415);
            count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
        }
    }

    public static class PrivacyRule extends TLObject {
        public ArrayList<Integer> users = new ArrayList<>();

        public static PrivacyRule TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            PrivacyRule result = null;
            switch(constructor) {
                case 0x4d5bbe0c:
                    result = new TL_privacyValueAllowUsers();
                    break;
                case 0x8b73e763:
                    result = new TL_privacyValueDisallowAll();
                    break;
                case 0xfffe1bac:
                    result = new TL_privacyValueAllowContacts();
                    break;
                case 0xf888fa1a:
                    result = new TL_privacyValueDisallowContacts();
                    break;
                case 0x65427b82:
                    result = new TL_privacyValueAllowAll();
                    break;
                case 0xc7f49b7:
                    result = new TL_privacyValueDisallowUsers();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in PrivacyRule", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_privacyValueAllowUsers extends PrivacyRule {
        public static int constructor = 0x4d5bbe0c;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                users.add(stream.readInt32(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(users.get(a));
            }
        }
    }

    public static class TL_privacyValueDisallowAll extends PrivacyRule {
        public static int constructor = 0x8b73e763;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_privacyValueAllowContacts extends PrivacyRule {
        public static int constructor = 0xfffe1bac;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_privacyValueDisallowContacts extends PrivacyRule {
        public static int constructor = 0xf888fa1a;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_privacyValueAllowAll extends PrivacyRule {
        public static int constructor = 0x65427b82;


        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_privacyValueDisallowUsers extends PrivacyRule {
        public static int constructor = 0xc7f49b7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                users.add(stream.readInt32(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(users.get(a));
            }
        }
    }

    public static class TL_updates_state extends TLObject {
        public static int constructor = 0xa56c2a3e;

        public int pts;
        public int qts;
        public int date;
        public int seq;
        public int unread_count;

        public static TL_updates_state TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_updates_state.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_updates_state", constructor));
                } else {
                    return null;
                }
            }
            TL_updates_state result = new TL_updates_state();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            pts = stream.readInt32(exception);
            qts = stream.readInt32(exception);
            date = stream.readInt32(exception);
            seq = stream.readInt32(exception);
            unread_count = stream.readInt32(exception);
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

    public static class MessageEntity extends TLObject {
        public int offset;
        public int length;
        public String language;
        public String url;

        public static MessageEntity TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            MessageEntity result = null;
            switch(constructor) {
                case 0x6ed02538:
                    result = new TL_messageEntityUrl();
                    break;
                case 0xbd610bc9:
                    result = new TL_messageEntityBold();
                    break;
                case 0x826f8b60:
                    result = new TL_messageEntityItalic();
                    break;
                case 0x64e475c2:
                    result = new TL_messageEntityEmail();
                    break;
                case 0x73924be0:
                    result = new TL_messageEntityPre();
                    break;
                case 0x76a6d327:
                    result = new TL_messageEntityTextUrl();
                    break;
                case 0xbb92ba95:
                    result = new TL_messageEntityUnknown();
                    break;
                case 0x6f635b0d:
                    result = new TL_messageEntityHashtag();
                    break;
                case 0x6cef8ac7:
                    result = new TL_messageEntityBotCommand();
                    break;
                case 0x28a20571:
                    result = new TL_messageEntityCode();
                    break;
                case 0xfa04579d:
                    result = new TL_messageEntityMention();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in MessageEntity", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messageEntityUrl extends MessageEntity {
        public static int constructor = 0x6ed02538;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityBold extends MessageEntity {
        public static int constructor = 0xbd610bc9;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityItalic extends MessageEntity {
        public static int constructor = 0x826f8b60;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityEmail extends MessageEntity {
        public static int constructor = 0x64e475c2;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityPre extends MessageEntity {
        public static int constructor = 0x73924be0;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
            language = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
            stream.writeString(language);
        }
    }

    public static class TL_messageEntityTextUrl extends MessageEntity {
        public static int constructor = 0x76a6d327;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
            url = stream.readString(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
            stream.writeString(url);
        }
    }

    public static class TL_messageEntityUnknown extends MessageEntity {
        public static int constructor = 0xbb92ba95;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityHashtag extends MessageEntity {
        public static int constructor = 0x6f635b0d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityBotCommand extends MessageEntity {
        public static int constructor = 0x6cef8ac7;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityCode extends MessageEntity {
        public static int constructor = 0x28a20571;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_messageEntityMention extends MessageEntity {
        public static int constructor = 0xfa04579d;


        public void readParams(AbsSerializedData stream, boolean exception) {
            offset = stream.readInt32(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(length);
        }
    }

    public static class TL_destroy_sessions_res extends TLObject {
        public static int constructor = 0xfb95abcd;

        public ArrayList<DestroySessionRes> destroy_results = new ArrayList<>();

        public static TL_destroy_sessions_res TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_destroy_sessions_res.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_destroy_sessions_res", constructor));
                } else {
                    return null;
                }
            }
            TL_destroy_sessions_res result = new TL_destroy_sessions_res();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                DestroySessionRes object = DestroySessionRes.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                destroy_results.add(object);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            int count = destroy_results.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                destroy_results.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_receivedNotifyMessage extends TLObject {
        public static int constructor = 0xa384b779;

        public int id;
        public int flags;

        public static TL_receivedNotifyMessage TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_receivedNotifyMessage.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_receivedNotifyMessage", constructor));
                } else {
                    return null;
                }
            }
            TL_receivedNotifyMessage result = new TL_receivedNotifyMessage();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            flags = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(flags);
        }
    }

    public static class TL_req_pq extends TLObject {
        public static int constructor = 0x60469778;

        public byte[] nonce;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_resPQ.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Server_DH_Params.TLdeserialize(stream, constructor, exception);
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

    public static class TL_auth_checkPhone extends TLObject {
        public static int constructor = 0x6fe51dfb;

        public String phone_number;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_checkedPhone.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return auth_SentCode.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_authorization.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_authorization.TLdeserialize(stream, constructor, exception);
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


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_resetAuthorizations extends TLObject {
        public static int constructor = 0x9fab0d1a;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_sendInvites extends TLObject {
        public static int constructor = 0x771c1d97;

        public ArrayList<String> phone_numbers = new ArrayList<>();
        public String message;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = phone_numbers.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeString(phone_numbers.get(a));
            }
            stream.writeString(message);
        }
    }

    public static class TL_auth_exportAuthorization extends TLObject {
        public static int constructor = 0xe5bfffcd;

        public int dc_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_exportedAuthorization.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_authorization.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeByteArray(bytes);
        }
    }

    public static class TL_auth_bindTempAuthKey extends TLObject {
        public static int constructor = 0xcdd42a05;

        public long perm_auth_key_id;
        public long nonce;
        public int expires_at;
        public byte[] encrypted_message;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(perm_auth_key_id);
            stream.writeInt64(nonce);
            stream.writeInt32(expires_at);
            stream.writeByteArray(encrypted_message);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return PeerNotifySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_account_resetNotifySettings extends TLObject {
        public static int constructor = 0xdb7e1747;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_updateProfile extends TLObject {
        public static int constructor = 0xf0888d68;

        public String first_name;
        public String last_name;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return User.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(offline);
        }
    }

    public static class TL_account_getWallPapers extends TLObject {
        public static int constructor = 0xc04cfac2;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                WallPaper object = WallPaper.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_users_getUsers extends TLObject {
        public static int constructor = 0xd91a548;

        public ArrayList<InputUser> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                User object = User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                id.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_users_getFullUser extends TLObject {
        public static int constructor = 0xca30a5b1;

        public InputUser id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_userFull.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_getStatuses extends TLObject {
        public static int constructor = 0xc4a353ee;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TL_contactStatus object = TL_contactStatus.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_getContacts extends TLObject {
        public static int constructor = 0x22c6aa08;

        public String hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return contacts_Contacts.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
        }
    }

    public static class TL_contacts_importContacts extends TLObject {
        public static int constructor = 0xda30b32d;

        public ArrayList<TL_inputPhoneContact> contacts = new ArrayList<>();
        public boolean replace;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_contacts_importedContacts.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = contacts.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                contacts.get(a).serializeToStream(stream);
            }
            stream.writeBool(replace);
        }
    }

    public static class TL_contacts_getSuggested extends TLObject {
        public static int constructor = 0xcd773428;

        public int limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_contacts_suggested.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(limit);
        }
    }

    public static class TL_contacts_deleteContact extends TLObject {
        public static int constructor = 0x8e953744;

        public InputUser id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_contacts_link.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_deleteContacts extends TLObject {
        public static int constructor = 0x59ab389e;

        public ArrayList<InputUser> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                id.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_contacts_block extends TLObject {
        public static int constructor = 0x332b49fc;

        public InputUser id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
        }
    }

    public static class TL_contacts_unblock extends TLObject {
        public static int constructor = 0xe54100bd;

        public InputUser id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return contacts_Blocked.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
            stream.writeInt32(limit);
        }
    }

    public static class TL_contacts_exportCard extends TLObject {
        public static int constructor = 0x84e53737;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                vector.objects.add(stream.readInt32(exception));
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_contacts_importCard extends TLObject {
        public static int constructor = 0x4fe196fe;

        public ArrayList<Integer> export_card = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = export_card.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(export_card.get(a));
            }
        }
    }

    public static class TL_messages_getMessages extends TLObject {
        public static int constructor = 0x4222fa74;

        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_Messages.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(id.get(a));
            }
        }
    }

    public static class TL_messages_getDialogs extends TLObject {
        public static int constructor = 0xeccf1df6;

        public int offset;
        public int max_id;
        public int limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_Dialogs.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_Messages.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_Messages.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_affectedHistory.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_affectedHistory.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(offset);
        }
    }

    public static class TL_messages_deleteMessages extends TLObject {
        public static int constructor = 0xa5f18925;

        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_affectedMessages.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(id.get(a));
            }
        }
    }

    public static class TL_messages_receivedMessages extends TLObject {
        public static int constructor = 0x5a954c0;

        public int max_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                TL_receivedNotifyMessage object = TL_receivedNotifyMessage.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return vector;
                }
                vector.objects.add(object);
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_id);
        }
    }

    public static class TL_messages_setTyping extends TLObject {
        public static int constructor = 0xa3825e50;

        public InputPeer peer;
        public SendMessageAction action;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            action.serializeToStream(stream);
        }
    }

    public static class TL_messages_sendMessage extends TLObject {
        public static int constructor = 0xdf12390;

        public int flags;
        public InputPeer peer;
        public int reply_to_msg_id;
        public String message;
        public long random_id;
        public ReplyMarkup reply_markup;
        public ArrayList<MessageEntity> entities = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_SentMessage.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            stream.writeString(message);
            stream.writeInt64(random_id);
            if ((flags & 4) != 0) {
                reply_markup.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = entities.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    entities.get(a).serializeToStream(stream);
                }
            }
        }
    }

    public static class TL_messages_sendMedia extends TLObject {
        public static int constructor = 0xc8f16791;

        public int flags;
        public InputPeer peer;
        public int reply_to_msg_id;
        public InputMedia media;
        public long random_id;
        public ReplyMarkup reply_markup;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            media.serializeToStream(stream);
            stream.writeInt64(random_id);
            if ((flags & 4) != 0) {
                reply_markup.serializeToStream(stream);
            }
        }
    }

    public static class TL_messages_forwardMessages extends TLObject {
        public static int constructor = 0x55e1728d;

        public InputPeer peer;
        public ArrayList<Integer> id = new ArrayList<>();
        public ArrayList<Long> random_id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(id.get(a));
            }
            stream.writeInt32(0x1cb5c415);
            count = random_id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(random_id.get(a));
            }
        }
    }

    public static class TL_messages_getChats extends TLObject {
        public static int constructor = 0x3c6aa187;

        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_chats.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(id.get(a));
            }
        }
    }

    public static class TL_messages_getFullChat extends TLObject {
        public static int constructor = 0x3b831c66;

        public int chat_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_chatFull.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_messages_editChatTitle extends TLObject {
        public static int constructor = 0xdc452855;

        public int chat_id;
        public String title;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            stream.writeString(title);
        }
    }

    public static class TL_messages_editChatPhoto extends TLObject {
        public static int constructor = 0xca4c79d8;

        public int chat_id;
        public InputChatPhoto photo;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            photo.serializeToStream(stream);
        }
    }

    public static class TL_messages_addChatUser extends TLObject {
        public static int constructor = 0xf9a0aa09;

        public int chat_id;
        public InputUser user_id;
        public int fwd_limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            user_id.serializeToStream(stream);
            stream.writeInt32(fwd_limit);
        }
    }

    public static class TL_messages_deleteChatUser extends TLObject {
        public static int constructor = 0xe0611f16;

        public int chat_id;
        public InputUser user_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
            user_id.serializeToStream(stream);
        }
    }

    public static class TL_messages_createChat extends TLObject {
        public static int constructor = 0x9cb126e;

        public ArrayList<InputUser> users = new ArrayList<>();
        public String title;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                users.get(a).serializeToStream(stream);
            }
            stream.writeString(title);
        }
    }

    public static class TL_updates_getState extends TLObject {
        public static int constructor = 0xedd4882a;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_updates_state.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return updates_Difference.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return UserProfilePhoto.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_photos_photo.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(caption);
            geo_point.serializeToStream(stream);
            crop.serializeToStream(stream);
        }
    }

    public static class TL_photos_deletePhotos extends TLObject {
        public static int constructor = 0x87cf7f2f;

        public ArrayList<InputPhoto> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                vector.objects.add(stream.readInt64(exception));
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                id.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_upload_getFile extends TLObject {
        public static int constructor = 0xe3a6cfb5;

        public InputFileLocation location;
        public int offset;
        public int limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_upload_file.TLdeserialize(stream, constructor, exception);
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


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_config.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_help_getNearestDc extends TLObject {
        public static int constructor = 0x1fb33026;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_nearestDc.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return help_AppUpdate.TLdeserialize(stream, constructor, exception);
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

        public ArrayList<TL_inputAppEvent> events = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = events.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                events.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_help_getInviteText extends TLObject {
        public static int constructor = 0xa4a95186;

        public String lang_code;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_help_inviteText.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(lang_code);
        }
    }

    public static class TL_photos_getUserPhotos extends TLObject {
        public static int constructor = 0x91cd32a8;

        public InputUser user_id;
        public int offset;
        public long max_id;
        public int limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return photos_Photos.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            stream.writeInt32(offset);
            stream.writeInt64(max_id);
            stream.writeInt32(limit);
        }
    }

    public static class TL_messages_forwardMessage extends TLObject {
        public static int constructor = 0x33963bf9;

        public InputPeer peer;
        public int id;
        public long random_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(id);
            stream.writeInt64(random_id);
        }
    }

    public static class TL_messages_sendBroadcast extends TLObject {
        public static int constructor = 0xbf73f4da;

        public ArrayList<InputUser> contacts = new ArrayList<>();
        public ArrayList<Long> random_id = new ArrayList<>();
        public String message;
        public InputMedia media;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
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
            count = random_id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt64(random_id.get(a));
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_located.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return geochats_Messages.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_geochats_getFullChat extends TLObject {
        public static int constructor = 0x6722dd6f;

        public TL_inputGeoChat peer;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_chatFull.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return geochats_Messages.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return geochats_Messages.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_geochats_statedMessage.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_DhConfig.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return EncryptedChat.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return EncryptedChat.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(max_date);
        }
    }

    public static class TL_messages_receivedQueue extends TLObject {
        public static int constructor = 0x55a5bb66;

        public int max_qts;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            Vector vector = new Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                vector.objects.add(stream.readInt64(exception));
            }
            return vector;
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(max_qts);
        }
    }

    public static class TL_help_getSupport extends TLObject {
        public static int constructor = 0x9cdf08cd;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_help_support.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_sendSms extends TLObject {
        public static int constructor = 0xda9f3e8;

        public String phone_number;
        public String phone_code_hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
        }
    }

    public static class TL_messages_readMessageContents extends TLObject {
        public static int constructor = 0x36a73f77;

        public ArrayList<Integer> id = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_affectedMessages.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = id.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeInt32(id.get(a));
            }
        }
    }

    public static class TL_account_checkUsername extends TLObject {
        public static int constructor = 0x2714d86c;

        public String username;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }
    }

    public static class TL_account_updateUsername extends TLObject {
        public static int constructor = 0x3e0bdd7c;

        public String username;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }
    }

    public static class TL_contacts_search extends TLObject {
        public static int constructor = 0x11f812d8;

        public String q;
        public int limit;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_contacts_found.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(q);
            stream.writeInt32(limit);
        }
    }

    public static class TL_account_getPrivacy extends TLObject {
        public static int constructor = 0xdadbc950;

        public TL_inputPrivacyKeyStatusTimestamp key;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_account_privacyRules.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
        }
    }

    public static class TL_account_setPrivacy extends TLObject {
        public static int constructor = 0xc9f81ce8;

        public TL_inputPrivacyKeyStatusTimestamp key;
        public ArrayList<InputPrivacyRule> rules = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_account_privacyRules.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = rules.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                rules.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_account_deleteAccount extends TLObject {
        public static int constructor = 0x418d4e0b;

        public String reason;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(reason);
        }
    }

    public static class TL_account_getAccountTTL extends TLObject {
        public static int constructor = 0x8fc711d;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_accountDaysTTL.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_setAccountTTL extends TLObject {
        public static int constructor = 0x2442485e;

        public TL_accountDaysTTL ttl;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            ttl.serializeToStream(stream);
        }
    }

    public static class TL_contacts_resolveUsername extends TLObject {
        public static int constructor = 0xbf0131c;

        public String username;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }
    }

    public static class TL_account_sendChangePhoneCode extends TLObject {
        public static int constructor = 0xa407a8f4;

        public String phone_number;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_account_sentChangePhoneCode.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
        }
    }

    public static class TL_account_changePhone extends TLObject {
        public static int constructor = 0x70c32edb;

        public String phone_number;
        public String phone_code_hash;
        public String phone_code;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
        }
    }

    public static class TL_messages_getStickers extends TLObject {
        public static int constructor = 0xae22e045;

        public String emoticon;
        public String hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_Stickers.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(emoticon);
            stream.writeString(hash);
        }
    }

    public static class TL_messages_getAllStickers extends TLObject {
        public static int constructor = 0xaa3bc868;

        public String hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_AllStickers.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
        }
    }

    public static class TL_account_updateDeviceLocked extends TLObject {
        public static int constructor = 0x38df3532;

        public int period;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(period);
        }
    }

    public static class TL_messages_getWebPagePreview extends TLObject {
        public static int constructor = 0x25223e24;

        public String message;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return MessageMedia.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(message);
        }
    }

    public static class TL_account_getAuthorizations extends TLObject {
        public static int constructor = 0xe320c158;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_account_authorizations.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_resetAuthorization extends TLObject {
        public static int constructor = 0xdf77f3bc;

        public long hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class TL_account_getPassword extends TLObject {
        public static int constructor = 0x548a30f5;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return account_Password.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_account_getPasswordSettings extends TLObject {
        public static int constructor = 0xbc8d11bb;

        public byte[] current_password_hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_account_passwordSettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(current_password_hash);
        }
    }

    public static class TL_account_updatePasswordSettings extends TLObject {
        public static int constructor = 0xfa7c4b86;

        public byte[] current_password_hash;
        public TL_account_passwordInputSettings new_settings;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(current_password_hash);
            new_settings.serializeToStream(stream);
        }
    }

    public static class TL_auth_checkPassword extends TLObject {
        public static int constructor = 0xa63011e;

        public byte[] password_hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_authorization.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(password_hash);
        }
    }

    public static class TL_auth_requestPasswordRecovery extends TLObject {
        public static int constructor = 0xd897bc66;


        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_passwordRecovery.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_auth_recoverPassword extends TLObject {
        public static int constructor = 0x4ea56e92;

        public String code;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_auth_authorization.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(code);
        }
    }

    public static class TL_messages_exportChatInvite extends TLObject {
        public static int constructor = 0x7d885289;

        public int chat_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return ExportedChatInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(chat_id);
        }
    }

    public static class TL_messages_checkChatInvite extends TLObject {
        public static int constructor = 0x3eadb1bb;

        public String hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return ChatInvite.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
        }
    }

    public static class TL_messages_importChatInvite extends TLObject {
        public static int constructor = 0x6c50051c;

        public String hash;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
        }
    }

    public static class TL_messages_getStickerSet extends TLObject {
        public static int constructor = 0x2619a90e;

        public InputStickerSet stickerset;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_messages_stickerSet.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stickerset.serializeToStream(stream);
        }
    }

    public static class TL_messages_installStickerSet extends TLObject {
        public static int constructor = 0x7b30c3a6;

        public InputStickerSet stickerset;
        public boolean disabled;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stickerset.serializeToStream(stream);
            stream.writeBool(disabled);
        }
    }

    public static class TL_messages_uninstallStickerSet extends TLObject {
        public static int constructor = 0xf96e55de;

        public InputStickerSet stickerset;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stickerset.serializeToStream(stream);
        }
    }

    public static class TL_messages_startBot extends TLObject {
        public static int constructor = 0x1b3e0ffc;

        public InputUser bot;
        public int chat_id;
        public long random_id;
        public String start_param;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeInt32(chat_id);
            stream.writeInt64(random_id);
            stream.writeString(start_param);
        }
    }

    public static class TL_help_getAppChangelog extends TLObject {
        public static int constructor = 0x5bab7fb2;

        public String device_model;
        public String system_version;
        public String app_version;
        public String lang_code;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return help_AppChangelog.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(device_model);
            stream.writeString(system_version);
            stream.writeString(app_version);
            stream.writeString(lang_code);
        }
    }

    //manually created

    //EncryptedChat start
    public static class EncryptedChat extends TLObject {
        public int id;
        public long access_hash;
        public int date;
        public int admin_id;
        public int participant_id;
        public byte[] g_a;
        public byte[] nonce;
        public byte[] g_a_or_b;
        public long key_fingerprint;
        public byte[] a_or_b; //custom
        public byte[] auth_key; //custom
        public int user_id; //custom
        public int ttl; //custom
        public int layer; //custom
        public int seq_in; //custom
        public int seq_out; //custom
        public byte[] key_hash; //custom
        public short key_use_count_in; //custom
        public short key_use_count_out; //custom
        public long exchange_id; //custom
        public int key_create_date; //custom
        public long future_key_fingerprint; //custom
        public byte[] future_auth_key; //custom

        public static EncryptedChat TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            EncryptedChat result = null;
            switch(constructor) {
                case 0xfda9a7b7:
                    result = new TL_encryptedChatRequested_old();
                    break;
                case 0xc878527e:
                    result = new TL_encryptedChatRequested();
                    break;
                case 0xfa56ce36:
                    result = new TL_encryptedChat();
                    break;
                case 0x6601d14f:
                    result = new TL_encryptedChat_old();
                    break;
                case 0xab7ec0a0:
                    result = new TL_encryptedChatEmpty();
                    break;
                case 0x3bf703dc:
                    result = new TL_encryptedChatWaiting();
                    break;
                case 0x13d6dd27:
                    result = new TL_encryptedChatDiscarded();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in EncryptedChat", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }
    //EncryptedChat end

    //Message start
    public static class Message extends TLObject {
        public int id;
        public int from_id;
        public Peer to_id;
        public int date;
        public MessageAction action;
        public int fwd_from_id;
        public int fwd_date;
        public int reply_to_msg_id;
        public String message;
        public MessageMedia media;
        public int flags;
        public ArrayList<MessageEntity> entities = new ArrayList<>();
        public ReplyMarkup reply_markup;
        public int send_state = 0; //custom
        public int fwd_msg_id = 0; //custom
        public String attachPath = ""; //custom
        public long random_id; //custom
        public int local_id = 0; //custom
        public long dialog_id; //custom
        public int ttl; //custom
        public int destroyTime; //custom
        public int layer; //custom
        public int seq_in; //custom
        public int seq_out; //custom
        public TLRPC.Message replyMessage; //custom

        public static Message TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            Message result = null;
            switch(constructor) {
                case 0x1d86f70e:
                    result = new TL_messageService();
                    break;
                case 0xa7ab1991:
                    result = new TL_message_old3();
                    break;
                case 0xc3060325:
                    result = new TL_message_old4();
                    break;
                case 0x83e5de54:
                    result = new TL_messageEmpty();
                    break;
                case 0xf07814c8:
                    result = new TL_message();
                    break;
                case 0xa367e716:
                    result = new TL_messageForwarded_old2(); //custom
                    break;
                case 0x5f46804:
                    result = new TL_messageForwarded_old(); //custom
                    break;
                case 0x567699b3:
                    result = new TL_message_old2(); //custom
                    break;
                case 0x9f8d60bb:
                    result = new TL_messageService_old(); //custom
                    break;
                case 0x22eb6aba:
                    result = new TL_message_old(); //custom
                    break;
                case 0x555555F8:
                    result = new TL_message_secret(); //custom
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Message", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_messageForwarded_old2 extends Message {
        public static int constructor = 0xa367e716;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            fwd_from_id = stream.readInt32(exception);
            fwd_date = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            flags |= MESSAGE_FLAG_FWD;
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0) {
                fwd_msg_id = stream.readInt32(exception);
            }
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(fwd_from_id);
            stream.writeInt32(fwd_date);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
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
        public static int constructor = 0xf07814c8;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                fwd_from_id = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                fwd_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reply_to_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 64) != 0) {
                reply_markup = ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 128) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    MessageEntity object = MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    entities.add(object);
                }
            }
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                fwd_msg_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_from_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            if ((flags & 64) != 0) {
                reply_markup.serializeToStream(stream);
            }
            if ((flags & 128) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = entities.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    entities.get(a).serializeToStream(stream);
                }
            }
            stream.writeString(attachPath);
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                stream.writeInt32(fwd_msg_id);
            }
        }
    }

    public static class TL_message_old4 extends TL_message {
        public static int constructor = 0xc3060325;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                fwd_from_id = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                fwd_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reply_to_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 64) != 0) {
                reply_markup = ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                fwd_msg_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_from_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            if ((flags & 64) != 0) {
                reply_markup.serializeToStream(stream);
            }
            stream.writeString(attachPath);
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                stream.writeInt32(fwd_msg_id);
            }
        }
    }

    public static class TL_message_old3 extends TL_message {
        public static int constructor = 0xa7ab1991;

        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if ((flags & 4) != 0) {
                fwd_from_id = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                fwd_date = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                reply_to_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                fwd_msg_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_from_id);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(fwd_date);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(reply_to_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeString(attachPath);
            if ((flags & MESSAGE_FLAG_FWD) != 0 && id < 0) {
                stream.writeInt32(fwd_msg_id);
            }
        }
    }

    public static class TL_message_old2 extends TL_message {
        public static int constructor = 0x567699b3;


        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeString(attachPath);
        }
    }

    public static class TL_messageService_old extends TL_messageService {
        public static int constructor = 0x9f8d60bb;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_OUT : 0;
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_UNREAD : 0;
            date = stream.readInt32(exception);
            action = MessageAction.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool((flags & MESSAGE_FLAG_OUT) != 0);
            stream.writeBool((flags & MESSAGE_FLAG_UNREAD) != 0);
            stream.writeInt32(date);
            action.serializeToStream(stream);
        }
    }

    public static class TL_messageForwarded_old extends TL_messageForwarded_old2 {
        public static int constructor = 0x5f46804;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            fwd_from_id = stream.readInt32(exception);
            fwd_date = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_OUT : 0;
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_UNREAD : 0;
            flags |= MESSAGE_FLAG_FWD;
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0) {
                fwd_msg_id = stream.readInt32(exception);
            }
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(fwd_from_id);
            stream.writeInt32(fwd_date);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool((flags & MESSAGE_FLAG_OUT) != 0);
            stream.writeBool((flags & MESSAGE_FLAG_UNREAD) != 0);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            if (id < 0) {
                stream.writeInt32(fwd_msg_id);
            }
            stream.writeString(attachPath);
        }
    }

    public static class TL_message_old extends TL_message {
        public static int constructor = 0x22eb6aba;

        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_OUT : 0;
            flags |= stream.readBool(exception) ? MESSAGE_FLAG_UNREAD : 0;
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeBool((flags & MESSAGE_FLAG_OUT) != 0);
            stream.writeBool((flags & MESSAGE_FLAG_UNREAD) != 0);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeString(attachPath);
        }
    }

    public static class TL_message_secret extends TL_message {
        public static int constructor = 0x555555F8;

        public void readParams(AbsSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            id = stream.readInt32(exception);
            ttl = stream.readInt32(exception);
            from_id = stream.readInt32(exception);
            to_id = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            media = MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (id < 0 || (media != null && !(media instanceof TL_messageMediaEmpty) && !(media instanceof TL_messageMediaWebPage) && message != null && message.length() != 0 && message.startsWith("-1"))) {
                attachPath = stream.readString(exception);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            stream.writeInt32(ttl);
            stream.writeInt32(from_id);
            to_id.serializeToStream(stream);
            stream.writeInt32(date);
            stream.writeString(message);
            media.serializeToStream(stream);
            stream.writeString(attachPath);
        }
    }
    //Message end

    //TL_dialog start
    public static class TL_dialog extends TLObject {
        public static int constructor = 0xc1dd804a;

        public Peer peer;
        public int top_message;
        public int read_inbox_max_id;
        public int unread_count;
        public PeerNotifySettings notify_settings;
        public int last_message_date; //custom
        public long id; //custom
        public int last_read; //custom

        public static TL_dialog TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_dialog.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_dialog", constructor));
                } else {
                    return null;
                }
            }
            TL_dialog result = new TL_dialog();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            peer = Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            top_message = stream.readInt32(exception);
            read_inbox_max_id = stream.readInt32(exception);
            unread_count = stream.readInt32(exception);
            notify_settings = PeerNotifySettings.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt32(top_message);
            stream.writeInt32(read_inbox_max_id);
            stream.writeInt32(unread_count);
            notify_settings.serializeToStream(stream);
        }
    }
    //TL_dialog end

    //Chat start
    public static class TL_chatEmpty extends Chat {
        public static int constructor = 0x9ba2d800;


        public void readParams(AbsSerializedData stream, boolean exception) {
            id = stream.readInt32(exception);

            title = "DELETED";
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(id);
        }
    }
    //Chat end

    //functions memory optimize
    public static class TL_upload_saveFilePart extends TLObject {
        public static int constructor = 0xb304a621;

        public long file_id;
        public int file_part;
        public ByteBufferDesc bytes;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(file_id);
            stream.writeInt32(file_part);
            stream.writeByteBuffer(bytes);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (bytes != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(bytes);
                bytes = null;
            }
        }
    }

    public static class TL_upload_saveBigFilePart extends TLObject {
        public static int constructor = 0xde7b673d;

        public long file_id;
        public int file_part;
        public int file_total_parts;
        public ByteBufferDesc bytes;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(file_id);
            stream.writeInt32(file_part);
            stream.writeInt32(file_total_parts);
            stream.writeByteBuffer(bytes);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (bytes != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(bytes);
                bytes = null;
            }
        }
    }

    public static class TL_upload_file extends TLObject {
        public static int constructor = 0x96a18d5;

        public storage_FileType type;
        public int mtime;
        public ByteBufferDesc bytes;

        public static TL_upload_file TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_upload_file.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_upload_file", constructor));
                } else {
                    return null;
                }
            }
            TL_upload_file result = new TL_upload_file();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            type = storage_FileType.TLdeserialize(stream, stream.readInt32(exception), exception);
            mtime = stream.readInt32(exception);
            bytes = stream.readByteBuffer(exception);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (bytes != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(bytes);
                bytes = null;
            }
        }
    }

    public static class TL_messages_sendEncryptedFile extends TLObject {
        public static int constructor = 0x9a901b66;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public ByteBufferDesc data;
        public InputEncryptedFile file;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_SentEncryptedMessage.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteBuffer(data);
            file.serializeToStream(stream);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (data != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(data);
                data = null;
            }
        }
    }

    public static class TL_messages_sendEncrypted extends TLObject {
        public static int constructor = 0xa9776773;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public ByteBufferDesc data;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_SentEncryptedMessage.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteBuffer(data);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (data != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(data);
                data = null;
            }
        }
    }

    public static class TL_messages_sendEncryptedService extends TLObject {
        public static int constructor = 0x32d439a4;

        public TL_inputEncryptedChat peer;
        public long random_id;
        public ByteBufferDesc data;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return messages_SentEncryptedMessage.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeInt64(random_id);
            stream.writeByteBuffer(data);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (data != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(data);
                data = null;
            }
        }
    }

    public static class TL_set_client_DH_params extends TLObject {
        public static int constructor = 0xf5045f1f;

        public byte[] nonce;
        public byte[] server_nonce;
        public ByteBufferDesc encrypted_data;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return Set_client_DH_params_answer.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeRaw(nonce);
            stream.writeRaw(server_nonce);
            stream.writeByteBuffer(encrypted_data);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (encrypted_data != null) {
                BuffersStorage.getInstance().reuseFreeBuffer(encrypted_data);
                encrypted_data = null;
            }
        }
    }

    //functions
    public static class invokeWithLayer extends TLObject {
        public static int constructor = 0xda9b0d0d;

        public int layer = LAYER;
        public TLObject query;

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(layer);
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

    public static class TL_destroy_sessions extends TLObject {
        public static int constructor = 0xa13dc52f;

        public ArrayList<Long> session_ids = new ArrayList<>();

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_destroy_sessions_res.TLdeserialize(stream, constructor, exception);
        }

        public int layer() {
            return 0;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                session_ids.add(stream.readInt64(exception));
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            int count = session_ids.size();
            stream.writeInt32(count);
            for (int a = 0; a < session_ids.size(); a++) {
                Long session_id = session_ids.get(a);
                stream.writeInt64(session_id);
            }
        }
    }

    public static class TL_ping extends TLObject {
        public static int constructor = 0x7abe77ec;

        public long ping_id;

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_pong.TLdeserialize(stream, constructor, exception);
        }

        public int layer() {
            return 0;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            ping_id = stream.readInt64(exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_pong.TLdeserialize(stream, constructor, exception);
        }

        public int layer() {
            return 0;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            ping_id = stream.readInt64(exception);
            disconnect_delay = stream.readInt32(exception);
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

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return DestroySessionRes.TLdeserialize(stream, constructor, exception);
        }

        public int layer() {
            return 0;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            session_id = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(session_id);
        }
    }

    public static class TL_invokeAfterMsg extends TLObject {
        public static int constructor = 0xcb9f372d;

        public long msg_id;
        public TLObject query;

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(msg_id);
            query.serializeToStream(stream);
        }
    }

    public static class TL_get_future_salts extends TLObject {
        public static int constructor = 0xb921bd04;

        public int num;

        public int layer() {
            return 0;
        }

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return TL_futuresalts.TLdeserialize(stream, constructor, exception);
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            num = stream.readInt32(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(num);
        }
    }

    public static class TL_rpc_drop_answer extends TLObject {
        public static int constructor = 0x58e4a740;

        public long req_msg_id;

        public int layer() {
            return 0;
        }

        public TLObject deserializeResponse(AbsSerializedData stream, int constructor, boolean exception) {
            return RpcDropAnswer.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
        }
    }

    public static class TL_msg_container extends TLObject {
        public ArrayList<TL_protoMessage> messages;

        public static int constructor = 0x73f1f8dc;

        public void readParams(AbsSerializedData stream, boolean exception) {
            messages = new ArrayList<>();
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_protoMessage message = new TL_protoMessage();
                message.msg_id = stream.readInt64(exception);
                message.seqno = stream.readInt32(exception);
                message.bytes = stream.readInt32(exception);
                int position = stream.getPosition();
                message.body = ConnectionsManager.getInstance().deserialize(ConnectionsManager.getInstance().getRequestWithMessageId(message.msg_id), stream, exception);
                if (message.body == null) {
                    stream.skip(message.bytes - (stream.getPosition() - position));
                } else {
                    messages.add(message);
                }
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(messages.size());
            for (int a = 0; a < messages.size(); a++) {
                TLObject obj = messages.get(a);
                TL_protoMessage proto = (TL_protoMessage) obj;
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

        public static TL_rpc_result TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_rpc_result.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_rpc_result", constructor));
                } else {
                    return null;
                }
            }
            TL_rpc_result result = new TL_rpc_result();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            req_msg_id = stream.readInt64(exception);
            result = ConnectionsManager.getInstance().deserialize(ConnectionsManager.getInstance().getRequestWithMessageId(req_msg_id), stream, exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            result.serializeToStream(stream);
        }

        @Override
        public void freeResources() {
            if (disableFree) {
                return;
            }
            if (result != null) {
                result.freeResources();
            }
        }
    }

    public static class TL_futureSalt extends TLObject {
        public static int constructor = 0x0949d9dc;

        public int valid_since;
        public int valid_until;
        public long salt;

        public void readParams(AbsSerializedData stream, boolean exception) {
            valid_since = stream.readInt32(exception);
            valid_until = stream.readInt32(exception);
            salt = stream.readInt64(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(valid_since);
            stream.writeInt32(valid_until);
            stream.writeInt64(salt);
        }
    }

    public static class TL_futuresalts extends TLObject {
        public static int constructor = 0xae500895;

        public long req_msg_id;
        public int now;
        public ArrayList<TL_futureSalt> salts = new ArrayList<>();

        public static TL_futuresalts TLdeserialize(AbsSerializedData stream, int constructor, boolean exception) {
            if (TL_futuresalts.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_futuresalts", constructor));
                } else {
                    return null;
                }
            }
            TL_futuresalts result = new TL_futuresalts();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbsSerializedData stream, boolean exception) {
            req_msg_id = stream.readInt64(exception);
            now = stream.readInt32(exception);
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TL_futureSalt salt = new TL_futureSalt();
                salt.readParams(stream, exception);
                salts.add(salt);
            }
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(req_msg_id);
            stream.writeInt32(now);
            int count = salts.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                salts.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_gzip_packed extends TLObject {
        public static int constructor = 0x3072cfa1;

        public byte[] packed_data;

        public void readParams(AbsSerializedData stream, boolean exception) {
            packed_data = stream.readByteArray(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(packed_data);
        }
    }

    public static class Vector extends TLObject {
        public static int constructor = 0x1cb5c415;
        public ArrayList<Object> objects = new ArrayList<>();
    }

    public static class TL_decryptedMessageHolder extends TLObject {
        public static int constructor = 0x555555F9;

        public long random_id;
        public int date;
        public TL_decryptedMessageLayer layer;
        public EncryptedFile file;
        public boolean new_key_used;

        public void readParams(AbsSerializedData stream, boolean exception) {
            random_id = stream.readInt64(exception);
            date = stream.readInt32(exception);
            layer = TL_decryptedMessageLayer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (stream.readBool(exception)) {
                file = EncryptedFile.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            new_key_used = stream.readBool(exception);
        }

        public void serializeToStream(AbsSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(random_id);
            stream.writeInt32(date);
            layer.serializeToStream(stream);
            stream.writeBool(file != null);
            if (file != null) {
                file.serializeToStream(stream);
            }
            stream.writeBool(new_key_used);
        }
    }
}
