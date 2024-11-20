package org.telegram.tgnet.tl;

import android.graphics.Path;

import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TL_bots {

    public static class botPreviewMedia extends TLObject {
        public static final int constructor = 0x23e91ba3;

        public int date;
        public TLRPC.MessageMedia media;

        public static botPreviewMedia TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (botPreviewMedia.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in botPreviewMedia", constructor));
                } else {
                    return null;
                }
            }
            botPreviewMedia result = new botPreviewMedia();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(date);
            media.serializeToStream(stream);
        }
    }

    public static class addPreviewMedia extends TLObject {
        public static final int constructor = 0x17aeb75a;

        public TLRPC.InputUser bot;
        public String lang_code = "";
        public TLRPC.InputMedia media;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return botPreviewMedia.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            media.serializeToStream(stream);
        }
    }

    public static class editPreviewMedia extends TLObject {
        public static final int constructor = 0x8525606f;

        public TLRPC.InputUser bot;
        public String lang_code = "";

        public TLRPC.InputMedia media;
        public TLRPC.InputMedia new_media;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return botPreviewMedia.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            media.serializeToStream(stream);
            new_media.serializeToStream(stream);
        }
    }

    public static class deletePreviewMedia extends TLObject {
        public static final int constructor = 0x2d0135b3;

        public TLRPC.InputUser bot;
        public String lang_code = "";
        public ArrayList<TLRPC.InputMedia> media = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            stream.writeInt32(0x1cb5c415);
            int count = media.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                media.get(a).serializeToStream(stream);
            }
        }
    }

    public static class reorderPreviewMedias extends TLObject {
        public static final int constructor = 0xb627f3aa;

        public TLRPC.InputUser bot;
        public String lang_code = "";
        public ArrayList<TLRPC.InputMedia> order = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            stream.writeInt32(0x1cb5c415);
            int count = order.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                order.get(a).serializeToStream(stream);
            }
        }
    }

    public static class getPreviewMedias extends TLObject {
        public static final int constructor = 0xa2a5594d;

        public TLRPC.InputUser bot;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            TLRPC.Vector vector = new TLRPC.Vector();
            int size = stream.readInt32(exception);
            for (int a = 0; a < size; a++) {
                vector.objects.add(botPreviewMedia.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
            return vector;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class getPreviewInfo extends TLObject {
        public static final int constructor = 0x423ab3ad;

        public TLRPC.InputUser bot;
        public String lang_code = "";

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return previewInfo.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
        }
    }

    public static class previewInfo extends TLObject {
        public static final int constructor = 0xca71d64;

        public ArrayList<TL_bots.botPreviewMedia> media = new ArrayList<>();
        public ArrayList<String> lang_codes = new ArrayList<>();

        public static previewInfo TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (previewInfo.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in previewInfo", constructor));
                } else {
                    return null;
                }
            }
            previewInfo result = new previewInfo();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                botPreviewMedia object = botPreviewMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                media.add(object);
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
                lang_codes.add(stream.readString(exception));
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(0x1cb5c415);
            int count = media.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                media.get(i).serializeToStream(stream);
            }
            stream.writeInt32(0x1cb5c415);
            count = lang_codes.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                stream.writeString(lang_codes.get(i));
            }
        }
    }

    public static class setBotInfo extends TLObject {
        public static final int constructor = 0x10cf3123;

        public int flags;
        public TLRPC.InputUser bot;
        public String lang_code;
        public String name;
        public String about;
        public String description;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 4) != 0) {
                bot.serializeToStream(stream);
            }
            stream.writeString(lang_code);
            if ((flags & 8) != 0) {
                stream.writeString(name);
            }
            if ((flags & 1) != 0) {
                stream.writeString(about);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
        }
    }

    public static class getBotInfo extends TLObject {
        public static final int constructor = 0xdcd914fd;

        public int flags;
        public TLRPC.InputUser bot;
        public String lang_code;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return BotInfo.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                bot.serializeToStream(stream);
            }
            stream.writeString(lang_code);
        }
    }

    public static class reorderUsernames extends TLObject {
        public static final int constructor = 0x9709b1c2;

        public TLRPC.InputUser bot;
        public ArrayList<String> order = new ArrayList<>();

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeInt32(0x1cb5c415);
            int count = order.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                stream.writeString(order.get(a));
            }
        }
    }

    public static class toggleUsername extends TLObject {
        public static final int constructor = 0x53ca973;

        public TLRPC.InputUser bot;
        public String username;
        public boolean active;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(username);
            stream.writeBool(active);
        }
    }

    public static abstract class BotInfo extends TLObject {
        public long user_id;
        public String description;
        public ArrayList<TLRPC.TL_botCommand> commands = new ArrayList<>();
        public int version;
        public BotMenuButton menu_button;
        public int flags;
        public TLRPC.Photo description_photo;
        public TLRPC.Document description_document;
        public boolean has_preview_medias;
        public String privacy_policy_url;
        public botAppSettings app_settings;

        public static BotInfo TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            BotInfo result = null;
            switch (constructor) {
                case TL_botInfo_layer140.constructor:
                    result = new TL_botInfo_layer140();
                    break;
                case TL_botInfoEmpty_layer48.constructor:
                    result = new TL_botInfoEmpty_layer48();
                    break;
                case TL_botInfo_layer131.constructor:
                    result = new TL_botInfo_layer131();
                    break;
                case TL_botInfo_layer48.constructor:
                    result = new TL_botInfo_layer48();
                    break;
                case TL_botInfo_layer139.constructor:
                    result = new TL_botInfo_layer139();
                    break;
                case TL_botInfo_layer185.constructor:
                    result = new TL_botInfo_layer185();
                    break;
                case TL_botInfo_layer192.constructor:
                    result = new TL_botInfo_layer192();
                    break;
                case TL_botInfo.constructor:
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

    public static class TL_botInfoEmpty_layer48 extends TL_botInfo {
        public static final int constructor = 0xbb2e37ce;


        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_botInfo_layer131 extends TL_botInfo {
        public static final int constructor = 0x98e81d3a;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
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
                TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                commands.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32((int) user_id);
            stream.writeString(description);
            stream.writeInt32(0x1cb5c415);
            int count = commands.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                commands.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer48 extends TL_botInfo {
        public static final int constructor = 0x9cf585d;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            version = stream.readInt32(exception);
            stream.readString(exception);
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
                TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                commands.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32((int) user_id);
            stream.writeInt32(version);
            stream.writeString("");
            stream.writeString(description);
            stream.writeInt32(0x1cb5c415);
            int count = commands.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                commands.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer139 extends BotInfo {
        public static final int constructor = 0x1b74b335;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
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
                TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                commands.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(description);
            stream.writeInt32(0x1cb5c415);
            int count = commands.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                commands.get(a).serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo extends BotInfo {
        public static final int constructor = 0x36607333;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_preview_medias = (flags & 64) != 0;
            if ((flags & 1) != 0) {
                user_id = stream.readInt64(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                description_photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                description_document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 4) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    commands.add(object);
                }
            }
            if ((flags & 8) != 0) {
                menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 128) != 0) {
                privacy_policy_url = stream.readString(exception);
            }
            if ((flags & 256) != 0) {
                app_settings = botAppSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_preview_medias ? flags | 64 : flags &~ 64;
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt64(user_id);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 16) != 0) {
                description_photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                description_document.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = commands.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    commands.get(a).serializeToStream(stream);
                }
            }
            if ((flags & 8) != 0) {
                menu_button.serializeToStream(stream);
            }
            if ((flags & 128) != 0) {
                stream.writeString(privacy_policy_url);
            }
            if ((flags & 256) != 0) {
                app_settings.serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer192 extends TL_botInfo {
        public static final int constructor = 0x82437e74;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_preview_medias = (flags & 64) != 0;
            if ((flags & 1) != 0) {
                user_id = stream.readInt64(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                description_photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                description_document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 4) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    commands.add(object);
                }
            }
            if ((flags & 8) != 0) {
                menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 128) != 0) {
                privacy_policy_url = stream.readString(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_preview_medias ? flags | 64 : flags &~ 64;
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt64(user_id);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 16) != 0) {
                description_photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                description_document.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = commands.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    commands.get(a).serializeToStream(stream);
                }
            }
            if ((flags & 8) != 0) {
                menu_button.serializeToStream(stream);
            }
            if ((flags & 128) != 0) {
                stream.writeString(privacy_policy_url);
            }
        }
    }

    public static class TL_botInfo_layer185 extends TL_botInfo {
        public static final int constructor = 0x8f300b57;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_preview_medias = (flags & 64) != 0;
            if ((flags & 1) != 0) {
                user_id = stream.readInt64(exception);
            }
            if ((flags & 2) != 0) {
                description = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                description_photo = TLRPC.Photo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 32) != 0) {
                description_document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 4) != 0) {
                int magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception) {
                        throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                    }
                    return;
                }
                int count = stream.readInt32(exception);
                for (int a = 0; a < count; a++) {
                    TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                    if (object == null) {
                        return;
                    }
                    commands.add(object);
                }
            }
            if ((flags & 8) != 0) {
                menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_preview_medias ? flags | 64 : flags &~ 64;
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeInt64(user_id);
            }
            if ((flags & 2) != 0) {
                stream.writeString(description);
            }
            if ((flags & 16) != 0) {
                description_photo.serializeToStream(stream);
            }
            if ((flags & 32) != 0) {
                description_document.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(0x1cb5c415);
                int count = commands.size();
                stream.writeInt32(count);
                for (int a = 0; a < count; a++) {
                    commands.get(a).serializeToStream(stream);
                }
            }
            if ((flags & 8) != 0) {
                menu_button.serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer140 extends TL_botInfo {
        public static final int constructor = 0xe4169b5d;


        public void readParams(AbstractSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
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
                TLRPC.TL_botCommand object = TLRPC.TL_botCommand.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                commands.add(object);
            }
            menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(description);
            stream.writeInt32(0x1cb5c415);
            int count = commands.size();
            stream.writeInt32(count);
            for (int a = 0; a < count; a++) {
                commands.get(a).serializeToStream(stream);
            }
            menu_button.serializeToStream(stream);
        }
    }

    public static abstract class BotMenuButton extends TLObject {

        public static BotMenuButton TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            BotMenuButton result = null;
            switch (constructor) {
                case 0xc7b57ce6:
                    result = new TL_botMenuButton();
                    break;
                case 0x7533a588:
                    result = new TL_botMenuButtonDefault();
                    break;
                case 0x4258c205:
                    result = new TL_botMenuButtonCommands();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in BotMenuButton", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_botMenuButton extends BotMenuButton {
        public static final int constructor = 0xc7b57ce6;

        public String text;
        public String url;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            url = stream.readString(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(url);
        }
    }

    public static class TL_botMenuButtonDefault extends BotMenuButton {
        public static final int constructor = 0x7533a588;


        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_botMenuButtonCommands extends BotMenuButton {
        public static final int constructor = 0x4258c205;


        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateBotMenuButton extends TLRPC.Update {
        public static final int constructor = 0x14b85813;

        public long bot_id;
        public BotMenuButton button;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            bot_id = stream.readInt64(exception);
            button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bot_id);
            button.serializeToStream(stream);
        }
    }

    public static class setBotMenuButton extends TLObject {
        public static final int constructor = 0x4504d54f;

        public TLRPC.InputUser user_id;
        public BotMenuButton button;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            button.serializeToStream(stream);
        }
    }

    public static class getBotMenuButton extends TLObject {
        public static final int constructor = 0x9c60eb28;

        public TLRPC.InputUser user_id;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return BotMenuButton.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
        }
    }

    public static class canSendMessage extends TLObject {
        public static final int constructor = 0x1359f4e6;

        public TLRPC.InputUser bot;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class allowSendMessage extends TLObject {
        public static final int constructor = 0xf132e3ef;

        public TLRPC.InputUser bot;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class invokeWebViewCustomMethod extends TLObject {
        public static final int constructor = 0x87fc5e7;

        public TLRPC.InputUser bot;
        public String custom_method;
        public TLRPC.TL_dataJSON params;

        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_dataJSON.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(custom_method);
            params.serializeToStream(stream);
        }
    }

    public static class getPopularAppBots extends TLObject {
        public static final int constructor = 0xc2510192;

        public String offset;
        public int limit;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return popularAppBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(offset);
            stream.writeInt32(limit);
        }
    }

    public static class popularAppBots extends TLObject {
        public static final int constructor = 0x1991b13b;

        public int flags;
        public String next_offset;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static popularAppBots TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (popularAppBots.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_bots_popularAppBots", constructor));
                } else {
                    return null;
                }
            }
            popularAppBots result = new popularAppBots();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception) {
                    throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
                }
                return;
            }
            int count = stream.readInt32(exception);
            for (int a = 0; a < count; a++) {
                TLRPC.User object = TLRPC.User.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (object == null) {
                    return;
                }
                users.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            stream.writeInt32(0x1cb5c415);
            int count = users.size();
            stream.writeInt32(count);
            for (int i = 0; i < count; ++i) {
                users.get(i).serializeToStream(stream);
            }
        }
    }

    public static class botAppSettings extends TLObject {
        public static final int constructor = 0xc99b1950;

        public int flags;
        public byte[] placeholder_path;
        public Path placeholder_svg_path; //custom
        public int background_color;
        public int background_dark_color;
        public int header_color;
        public int header_dark_color;

        public static botAppSettings TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            if (botAppSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in botAppSettings", constructor));
                } else {
                    return null;
                }
            }
            botAppSettings result = new botAppSettings();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                placeholder_path = stream.readByteArray(exception);
                placeholder_svg_path = SvgHelper.doPath(SvgHelper.decompress(placeholder_path));
            }
            if ((flags & 2) != 0) {
                background_color = stream.readInt32(exception);
            }
            if ((flags & 4) != 0) {
                background_dark_color = stream.readInt32(exception);
            }
            if ((flags & 8) != 0) {
                header_color = stream.readInt32(exception);
            }
            if ((flags & 16) != 0) {
                header_dark_color = stream.readInt32(exception);
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeByteArray(placeholder_path);
            }
            if ((flags & 2) != 0) {
                stream.writeInt32(background_color);
            }
            if ((flags & 4) != 0) {
                stream.writeInt32(background_dark_color);
            }
            if ((flags & 8) != 0) {
                stream.writeInt32(header_color);
            }
            if ((flags & 16) != 0) {
                stream.writeInt32(header_dark_color);
            }
        }
    }

    public static class toggleUserEmojiStatusPermission extends TLObject {
        public static final int constructor = 0x6de6392;

        public TLRPC.InputUser bot;
        public boolean enabled;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeBool(enabled);
        }
    }

    public static class checkDownloadFileParams extends TLObject {
        public static final int constructor = 0x50077589;

        public TLRPC.InputUser bot;
        public String file_name;
        public String url;

        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(file_name);
            stream.writeString(url);
        }

    }

}
