package org.telegram.tgnet.tl;

import android.graphics.Path;

import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLParseException;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_bots {

    public static class botPreviewMedia extends TLObject {
        public static final int constructor = 0x23e91ba3;

        public int date;
        public TLRPC.MessageMedia media;

        public static botPreviewMedia TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final botPreviewMedia result = botPreviewMedia.constructor != constructor ? null : new botPreviewMedia();
            return TLdeserialize(botPreviewMedia.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            date = stream.readInt32(exception);
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return botPreviewMedia.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return botPreviewMedia.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            Vector.serialize(stream, media);
        }
    }

    public static class reorderPreviewMedias extends TLObject {
        public static final int constructor = 0xb627f3aa;

        public TLRPC.InputUser bot;
        public String lang_code = "";
        public ArrayList<TLRPC.InputMedia> order = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
            Vector.serialize(stream, order);
        }
    }

    public static class getPreviewMedias extends TLObject {
        public static final int constructor = 0xa2a5594d;

        public TLRPC.InputUser bot;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, botPreviewMedia::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class getPreviewInfo extends TLObject {
        public static final int constructor = 0x423ab3ad;

        public TLRPC.InputUser bot;
        public String lang_code = "";

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return previewInfo.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(lang_code);
        }
    }

    public static class previewInfo extends TLObject {
        public static final int constructor = 0xca71d64;

        public ArrayList<TL_bots.botPreviewMedia> media = new ArrayList<>();
        public ArrayList<String> lang_codes = new ArrayList<>();

        public static previewInfo TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final previewInfo result = previewInfo.constructor != constructor ? null : new previewInfo();
            return TLdeserialize(previewInfo.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            media = Vector.deserialize(stream, botPreviewMedia::TLdeserialize, exception);
            lang_codes = Vector.deserializeString(stream, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, media);
            Vector.serializeString(stream, lang_codes);
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return BotInfo.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            Vector.serializeString(stream, order);
        }
    }

    public static class toggleUsername extends TLObject {
        public static final int constructor = 0x53ca973;

        public TLRPC.InputUser bot;
        public String username;
        public boolean active;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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
        public botVerifierSettings verifier_settings;

        public static BotInfo TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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
                case TL_botInfo_layer195.constructor:
                    result = new TL_botInfo_layer195();
                    break;
                case TL_botInfo.constructor:
                    result = new TL_botInfo();
                    break;
            }
            return TLdeserialize(BotInfo.class, result, stream, constructor, exception);
        }
    }

    public static class TL_botInfoEmpty_layer48 extends TL_botInfo {
        public static final int constructor = 0xbb2e37ce;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_botInfo_layer131 extends TL_botInfo {
        public static final int constructor = 0x98e81d3a;


        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            description = stream.readString(exception);
            commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32((int) user_id);
            stream.writeString(description);
            Vector.serialize(stream, commands);
        }
    }

    public static class TL_botInfo_layer48 extends TL_botInfo {
        public static final int constructor = 0x9cf585d;


        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt32(exception);
            version = stream.readInt32(exception);
            stream.readString(exception);
            description = stream.readString(exception);
            commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32((int) user_id);
            stream.writeInt32(version);
            stream.writeString("");
            stream.writeString(description);
            Vector.serialize(stream, commands);
        }
    }

    public static class TL_botInfo_layer139 extends BotInfo {
        public static final int constructor = 0x1b74b335;


        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            description = stream.readString(exception);
            commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(description);
            Vector.serialize(stream, commands);
        }
    }

    public static class TL_botInfo extends BotInfo {
        public static final int constructor = 0x4d8a0299;

        public void readParams(InputSerializedData stream, boolean exception) {
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
                commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
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
            if ((flags & 512) != 0) {
                verifier_settings = botVerifierSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
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
                Vector.serialize(stream, commands);
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
            if ((flags & 512) != 0) {
                verifier_settings.serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer195 extends TL_botInfo {
        public static final int constructor = 0x36607333;

        public void readParams(InputSerializedData stream, boolean exception) {
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
                commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
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

        public void serializeToStream(OutputSerializedData stream) {
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
                Vector.serialize(stream, commands);
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


        public void readParams(InputSerializedData stream, boolean exception) {
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
                commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 128) != 0) {
                privacy_policy_url = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
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
                Vector.serialize(stream, commands);
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


        public void readParams(InputSerializedData stream, boolean exception) {
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
                commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
            }
            if ((flags & 8) != 0) {
                menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
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
                Vector.serialize(stream, commands);
            }
            if ((flags & 8) != 0) {
                menu_button.serializeToStream(stream);
            }
        }
    }

    public static class TL_botInfo_layer140 extends TL_botInfo {
        public static final int constructor = 0xe4169b5d;


        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
            description = stream.readString(exception);
            commands = Vector.deserialize(stream, TLRPC.TL_botCommand::TLdeserialize, exception);
            menu_button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
            stream.writeString(description);
            Vector.serialize(stream, commands);
            menu_button.serializeToStream(stream);
        }
    }

    public static abstract class BotMenuButton extends TLObject {

        public static BotMenuButton TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
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
            return TLdeserialize(BotMenuButton.class, result, stream, constructor, exception);
        }
    }

    public static class TL_botMenuButton extends BotMenuButton {
        public static final int constructor = 0xc7b57ce6;

        public String text;
        public String url;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(url);
        }
    }

    public static class TL_botMenuButtonDefault extends BotMenuButton {
        public static final int constructor = 0x7533a588;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_botMenuButtonCommands extends BotMenuButton {
        public static final int constructor = 0x4258c205;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_updateBotMenuButton extends TLRPC.Update {
        public static final int constructor = 0x14b85813;

        public long bot_id;
        public BotMenuButton button;

        public void readParams(InputSerializedData stream, boolean exception) {
            bot_id = stream.readInt64(exception);
            button = BotMenuButton.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bot_id);
            button.serializeToStream(stream);
        }
    }

    public static class setBotMenuButton extends TLObject {
        public static final int constructor = 0x4504d54f;

        public TLRPC.InputUser user_id;
        public BotMenuButton button;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
            button.serializeToStream(stream);
        }
    }

    public static class getBotMenuButton extends TLObject {
        public static final int constructor = 0x9c60eb28;

        public TLRPC.InputUser user_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return BotMenuButton.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
        }
    }

    public static class canSendMessage extends TLObject {
        public static final int constructor = 0x1359f4e6;

        public TLRPC.InputUser bot;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class allowSendMessage extends TLObject {
        public static final int constructor = 0xf132e3ef;

        public TLRPC.InputUser bot;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

    public static class invokeWebViewCustomMethod extends TLObject {
        public static final int constructor = 0x87fc5e7;

        public TLRPC.InputUser bot;
        public String custom_method;
        public TLRPC.TL_dataJSON params;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_dataJSON.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return popularAppBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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

        public static popularAppBots TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final popularAppBots result = popularAppBots.constructor != constructor ? null : new popularAppBots();
            return TLdeserialize(popularAppBots.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                next_offset = stream.readString(exception);
            }
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(next_offset);
            }
            Vector.serialize(stream, users);
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

        public static botAppSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final botAppSettings result = botAppSettings.constructor != constructor ? null : new botAppSettings();
            return TLdeserialize(botAppSettings.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
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
        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
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
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
            stream.writeString(file_name);
            stream.writeString(url);
        }
    }

    public static class updateStarRefProgram extends TLObject {
        public static final int constructor = 0x778b5ab3;

        public int flags;
        public TLRPC.InputUser bot;
        public int commission_permille;
        public int duration_months;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_payments.starRefProgram.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            bot.serializeToStream(stream);
            stream.writeInt32(commission_permille);
            if ((flags & 1) != 0) {
                stream.writeInt32(duration_months);
            }
        }
    }

    public static class getAdminedBots extends TLObject {
        public static final int constructor = 0xb0711d83;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.User::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class botVerifierSettings extends TLObject {
        public static final int constructor = 0xb0cd6617;

        public int flags;
        public boolean can_modify_custom_description;
        public long icon;
        public String company;
        public String custom_description;

        public static botVerifierSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final botVerifierSettings result = botVerifierSettings.constructor != constructor ? null : new botVerifierSettings();
            return TLdeserialize(botVerifierSettings.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            can_modify_custom_description = (flags & 2) != 0;
            icon = stream.readInt64(exception);
            company = stream.readString(exception);
            if ((flags & 1) != 0) {
                custom_description = stream.readString(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = can_modify_custom_description ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            stream.writeInt64(icon);
            stream.writeString(company);
            if ((flags & 1) != 0) {
                stream.writeString(custom_description);
            }
        }
    }

    public static class botVerification extends TLObject {
        public static final int constructor = 0xf93cd45c;

        public long bot_id;
        public long icon;
        public String description;

        public static botVerification TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final botVerification result = botVerification.constructor != constructor ? null : new botVerification();
            return TLdeserialize(botVerification.class, result, stream, constructor, exception);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            bot_id = stream.readInt64(exception);
            icon = stream.readInt64(exception);
            description = stream.readString(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bot_id);
            stream.writeInt64(icon);
            stream.writeString(description);
        }
    }

    public static class setCustomVerification extends TLObject {
        public static final int constructor = 0x8b89dfbd;

        public int flags;
        public boolean enabled;
        public TLRPC.InputUser bot;
        public TLRPC.InputPeer peer;
        public String custom_description;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = enabled ? flags | 2 : flags &~ 2;
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                bot.serializeToStream(stream);
            }
            peer.serializeToStream(stream);
            if ((flags & 4) != 0) {
                stream.writeString(custom_description);
            }
        }

    }

    public static class getBotRecommendations extends TLObject {
        public static final int constructor = 0xa1b70815;

        public TLRPC.InputUser bot;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Users.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            bot.serializeToStream(stream);
        }
    }

}
