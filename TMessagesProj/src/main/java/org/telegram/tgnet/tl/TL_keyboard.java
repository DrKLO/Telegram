package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLParseException;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_keyboard {
    private TL_keyboard() {

    }

    // false super class
    public static abstract class ButtonTypeProto extends TLObject {}

    public static abstract class ButtonType extends ButtonTypeProto {

        public static ButtonType TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(ButtonType.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static ButtonType fromConstructor(int constructor) {
            switch (constructor) {
                case TL_buttonTypeDefault.constructor:
                    return new TL_buttonTypeDefault();
                case TL_buttonTypeRequestPhone.constructor:
                    return new TL_buttonTypeRequestPhone();
                case TL_buttonTypeRequestGeoLocation.constructor:
                    return new TL_buttonTypeRequestGeoLocation();
                case TL_buttonTypeRequestPoll.constructor:
                    return new TL_buttonTypeRequestPoll();
                case TL_buttonTypeRequestPeer.constructor:
                    return new TL_buttonTypeRequestPeer();
                case TL_inputButtonTypeRequestPeer.constructor:
                    return new TL_inputButtonTypeRequestPeer();
                case TL_buttonTypeSimpleWebView.constructor:
                    return new TL_buttonTypeSimpleWebView();
                default:
                    return null;
            }
        }
    }

    public static class TL_buttonTypeDefault extends ButtonType {
        public static final int constructor = 0xC9DD90E9;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_buttonTypeRequestPhone extends ButtonType {
        public static final int constructor = 0xDF3D36F9;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_buttonTypeRequestGeoLocation extends ButtonType {
        public static final int constructor = 0x9BEEE140;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_buttonTypeRequestPoll extends ButtonType {
        public static final int constructor = 0xAACFFF84;

        public int flags;
        public boolean quiz;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                quiz = stream.readBool(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(quiz);
            }
        }
    }

    public static class TL_buttonTypeRequestPeer extends ButtonType {
        public static final int constructor = 0x4F58A237;

        public int flags;
        public int button_id;
        public TLRPC.RequestPeerType peer_type;
        public int max_quantity;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            button_id = stream.readInt32(exception);
            peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_quantity = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(button_id);
            peer_type.serializeToStream(stream);
            stream.writeInt32(max_quantity);
        }
    }

    public static class TL_buttonTypeSimpleWebView extends ButtonType {
        public static final int constructor = 0xC01A597A;

        public String url;

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    private static class TL_inputButtonTypeRequestPeer extends ButtonType {
        public static final int constructor = 0x3FE268FE;

        public int flags;
        public boolean name_requested;
        public boolean username_requested;
        public boolean photo_requested;
        public int button_id;
        public TLRPC.RequestPeerType peer_type;
        public int max_quantity;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            name_requested = hasFlag(flags, FLAG_0);
            username_requested = hasFlag(flags, FLAG_1);
            photo_requested = hasFlag(flags, FLAG_2);
            button_id = stream.readInt32(exception);
            peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            max_quantity = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, name_requested);
            flags = setFlag(flags, FLAG_1, username_requested);
            flags = setFlag(flags, FLAG_2, photo_requested);
            stream.writeInt32(flags);
            stream.writeInt32(button_id);
            peer_type.serializeToStream(stream);
            stream.writeInt32(max_quantity);
        }
    }



    public static abstract class InlineButtonType extends ButtonTypeProto {

        public static InlineButtonType TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(InlineButtonType.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static InlineButtonType fromConstructor(int constructor) {
            switch (constructor) {
                case TL_inlineButtonTypeUrl.constructor:
                    return new TL_inlineButtonTypeUrl();
                case TL_inlineButtonTypeUrlAuth.constructor:
                    return new TL_inlineButtonTypeUrlAuth();
                case TL_inputInlineButtonTypeUrlAuth.constructor:
                    return new TL_inputInlineButtonTypeUrlAuth();
                case TL_inlineButtonTypeWebView.constructor:
                    return new TL_inlineButtonTypeWebView();
                case TL_inlineButtonTypeCallback.constructor:
                    return new TL_inlineButtonTypeCallback();
                case TL_inlineButtonTypeGame.constructor:
                    return new TL_inlineButtonTypeGame();
                case TL_inlineButtonTypeBuy.constructor:
                    return new TL_inlineButtonTypeBuy();
                case TL_inlineButtonTypeSwitchInline.constructor:
                    return new TL_inlineButtonTypeSwitchInline();
                case TL_inlineButtonTypeUserProfile.constructor:
                    return new TL_inlineButtonTypeUserProfile();
                case TL_inputInlineButtonTypeUserProfile.constructor:
                    return new TL_inputInlineButtonTypeUserProfile();
                case TL_inlineButtonTypeCopy.constructor:
                    return new TL_inlineButtonTypeCopy();
                case TL_inlineButtonTypeDisabled.constructor:
                    return new TL_inlineButtonTypeDisabled();
                default:
                    return null;
            }
        }
    }

    public static class TL_inlineButtonTypeUrl extends InlineButtonType {
        public static final int constructor = 0xECA4F8D4;

        public String url;

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static class TL_inlineButtonTypeUrlAuth extends InlineButtonType {
        public static final int constructor = 0xBFD02DA2;

        public int flags;
        public String fwd_text;
        public String url;
        public int button_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                fwd_text = stream.readString(exception);
            }
            url = stream.readString(exception);
            button_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, fwd_text != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(fwd_text);
            }
            stream.writeString(url);
            stream.writeInt32(button_id);
        }
    }

    public static class TL_inlineButtonTypeWebView extends InlineButtonType {
        public static final int constructor = 0x3BCAB5B4;

        public String url;

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static class TL_inlineButtonTypeCallback extends InlineButtonType {
        public static final int constructor = 0x2955BC38;

        public int flags;
        public boolean requires_password;
        public byte[] data;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            requires_password = hasFlag(flags, FLAG_0);
            data = stream.readByteArray(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, requires_password);
            stream.writeInt32(flags);
            stream.writeByteArray(data);
        }
    }

    public static class TL_inlineButtonTypeGame extends InlineButtonType {
        public static final int constructor = 0x5CD3709D;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inlineButtonTypeBuy extends InlineButtonType {
        public static final int constructor = 0x48BAD7A5;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inlineButtonTypeSwitchInline extends InlineButtonType {
        public static final int constructor = 0x93773FF5;

        public int flags;
        public boolean same_peer;
        public String query;
        public ArrayList<TLRPC.InlineQueryPeerType> peer_types;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            same_peer = hasFlag(flags, FLAG_0);
            query = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                peer_types = Vector.deserialize(stream, TLRPC.InlineQueryPeerType::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, same_peer);
            flags = setFlag(flags, FLAG_1, peer_types != null);
            stream.writeInt32(flags);
            stream.writeString(query);
            if (hasFlag(flags, FLAG_1)) {
                Vector.serialize(stream, peer_types);
            }
        }
    }

    public static class TL_inlineButtonTypeUserProfile extends InlineButtonType {
        public static final int constructor = 0x3FA33FCF;

        public long user_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
        }
    }

    public static class TL_inlineButtonTypeCopy extends InlineButtonType {
        public static final int constructor = 0xB41D3272;

        public String copy_text;

        public void readParams(InputSerializedData stream, boolean exception) {
            copy_text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(copy_text);
        }
    }

    public static class TL_inlineButtonTypeDisabled extends InlineButtonType {
        public static final int constructor = 0xA438619D;

        public void readParams(InputSerializedData stream, boolean exception) {
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    private static class TL_inputInlineButtonTypeUrlAuth extends InlineButtonType {
        public static final int constructor = 0x9961BCB4;

        public int flags;
        public boolean request_write_access;
        public String fwd_text;
        public String url;
        public TLRPC.InputUser bot;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            request_write_access = hasFlag(flags, FLAG_0);
            if (hasFlag(flags, FLAG_1)) {
                fwd_text = stream.readString(exception);
            }
            url = stream.readString(exception);
            if (hasFlag(flags, FLAG_2)) {
                bot = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, request_write_access);
            flags = setFlag(flags, FLAG_1, fwd_text != null);
            flags = setFlag(flags, FLAG_2, bot != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_1)) {
                stream.writeString(fwd_text);
            }
            stream.writeString(url);
            if (hasFlag(flags, FLAG_2)) {
                bot.serializeToStream(stream);
            }
        }
    }

    private static class TL_inputInlineButtonTypeUserProfile extends InlineButtonType {
        public static final int constructor = 0x53F3CE5A;

        public TLRPC.InputUser user_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
        }
    }



    public static class PageButton extends TLObject implements KeyboardButtonProto {
        public static final int constructor = 0x692A5488;

        public int flags;
        public TL_iv.RichText text;
        public InlineButtonType type;
        public RichButtonStyle style;

        public static PageButton TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final PageButton result = constructor != PageButton.constructor ? null : new PageButton();
            return TLdeserialize(PageButton.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            text = TL_iv.RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            type = InlineButtonType.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                style = RichButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, style != null);
            stream.writeInt32(flags);
            text.serializeToStream(stream);
            type.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                style.serializeToStream(stream);
            }
        }

        @Override
        public InlineButtonType getType() {
            return type;
        }

        @Override
        public String getText() {
            if (text instanceof TL_iv.textPlain) {
                return ((TL_iv.textPlain) text).text;
            }
            return null;
        }
    }

    public static class RichButtonStyle extends TLObject {
        public static final int constructor = 0x03C610BD;

        public int flags;
        public boolean bg_primary;
        public boolean bg_danger;
        public boolean bg_success;
        public boolean link;

        public static RichButtonStyle TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final RichButtonStyle result = constructor != RichButtonStyle.constructor ? null : new RichButtonStyle();
            return TLdeserialize(RichButtonStyle.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bg_primary = hasFlag(flags, FLAG_0);
            bg_danger = hasFlag(flags, FLAG_1);
            bg_success = hasFlag(flags, FLAG_2);
            link = hasFlag(flags, FLAG_3);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, bg_primary);
            flags = setFlag(flags, FLAG_1, bg_danger);
            flags = setFlag(flags, FLAG_2, bg_success);
            flags = setFlag(flags, FLAG_3, link);
            stream.writeInt32(flags);
        }
    }

    public static class KeyboardButtonStyle extends TLObject {
        public static final int constructor = 0x4FDD3430;

        public int flags;
        public boolean bg_primary;
        public boolean bg_danger;
        public boolean bg_success;
        public long icon;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bg_primary = hasFlag(flags, FLAG_0);
            bg_danger = hasFlag(flags, FLAG_1);
            bg_success = hasFlag(flags, FLAG_2);
            if (hasFlag(flags, FLAG_3)) {
                icon = stream.readInt64(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, bg_primary);
            flags = setFlag(flags, FLAG_1, bg_danger);
            flags = setFlag(flags, FLAG_2, bg_success);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt64(icon);
            }
        }

        public static KeyboardButtonStyle TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final KeyboardButtonStyle result = constructor != KeyboardButtonStyle.constructor ? null : new KeyboardButtonStyle();
            return TLdeserialize(KeyboardButtonStyle.class, result, stream, constructor, exception);
        }
    }


    /* * */

    private static TLObject TLdeserializeLegacy(InputSerializedData stream, int constructor, boolean exception) {
        final KeyboardButton keyboardButton = KeyboardButton.fromConstructor(constructor);
        if (keyboardButton != null) {
            keyboardButton.readParams(stream, exception);
            return keyboardButton;
        }

        final KeyboardInlineButton keyboardInlineButton = KeyboardInlineButton.fromConstructor(constructor);
        if (keyboardInlineButton != null) {
            keyboardInlineButton.readParams(stream, exception);
            return keyboardInlineButton;
        }

        TLParseException.doThrowOrLog(stream, KeyboardButtonProto.class.getName(), constructor, exception);
        return null;
    }

    // false super class
    public interface KeyboardButtonProto {

        abstract public ButtonTypeProto getType();

        abstract public String getText();

        @Deprecated
        public default String getUrl() {
            final ButtonTypeProto type = getType();
            if (type instanceof TL_inlineButtonTypeUrl) {
                return ((TL_inlineButtonTypeUrl)type).url;
            }
            if (type instanceof TL_inlineButtonTypeUrlAuth) {
                return ((TL_inlineButtonTypeUrlAuth) type).url;
            }
            if (type instanceof TL_inlineButtonTypeWebView) {
                return ((TL_inlineButtonTypeWebView) type).url;
            }
            if (type instanceof TL_buttonTypeSimpleWebView) {
                return ((TL_buttonTypeSimpleWebView) type).url;
            }
            return null;
        }

        @Deprecated
        public default byte[] getData() {
            final ButtonTypeProto type = getType();
            if (type instanceof TL_inlineButtonTypeCallback) {
                return ((TL_inlineButtonTypeCallback) type).data;
            }

            return null;
        }
    }

    public static abstract class KeyboardInlineButtonRow extends TLObject {
        public ArrayList<KeyboardInlineButton> buttons = new ArrayList<>();

        public static KeyboardInlineButtonRow fromConstructor(int constructor) {
            switch (constructor) {
                case TL_keyboardInlineButtonRow.constructor:
                    return new TL_keyboardInlineButtonRow();
                case TL_keyboardInlineButtonRow_layer228.constructor:
                    return new TL_keyboardInlineButtonRow_layer228();
            }
            return null;
        }

        public static KeyboardInlineButtonRow TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(KeyboardInlineButtonRow.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    public static class TL_keyboardInlineButtonRow extends KeyboardInlineButtonRow {
        public static final int constructor = 0x19420AF6;

        public void readParams(InputSerializedData stream, boolean exception) {
            buttons = Vector.deserialize(stream, KeyboardInlineButton::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, buttons);
        }
    }

    private static class TL_keyboardInlineButtonRow_layer228 extends TL_keyboardInlineButtonRow {
        public static final int constructor = 0x77608b83;

        public void readParams(InputSerializedData stream, boolean exception) {
            final ArrayList<TLObject> buttonProto = Vector.deserialize(stream, TL_keyboard::TLdeserializeLegacy, exception);
            buttons = new ArrayList<>(buttonProto.size());
            for (TLObject proto : buttonProto) {
                if (proto instanceof KeyboardInlineButton) {
                    buttons.add((KeyboardInlineButton) proto);
                }
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, buttons);
        }
    }

    public static abstract class KeyboardInlineButton extends TLObject implements KeyboardButtonProto {
        public int flags;
        public String text;
        public KeyboardButtonStyle style;
        public InlineButtonType type;

        public static KeyboardInlineButton fromConstructor(int constructor) {
            if (constructor == TL_keyboardInlineButton.constructor) {
                return new TL_keyboardInlineButton();
            }
            return fromConstructorLegacy(constructor);
        }

        private static KeyboardInlineButton fromConstructorLegacy(int constructor) {
            // Only for layers 228 and below. If the layer is higher, it must be in fromConstructor
            switch (constructor) {
                case TL_keyboardInlineButton_legacy.constructor:
                    return new TL_keyboardInlineButton_legacy();
                case TL_keyboardButtonCallback_layer228.constructor:
                    return new TL_keyboardButtonCallback_layer228();
                case TL_keyboardButtonCallback_layer223.constructor:
                    return new TL_keyboardButtonCallback_layer223();
                case TL_keyboardButtonCallback_layer117.constructor:
                    return new TL_keyboardButtonCallback_layer117();
                case TL_keyboardButtonSwitchInline_layer228.constructor:
                    return new TL_keyboardButtonSwitchInline_layer228();
                case TL_keyboardButtonSwitchInline_layer223.constructor:
                    return new TL_keyboardButtonSwitchInline_layer223();
                case TL_keyboardButtonSwitchInline_layer157.constructor:
                    return new TL_keyboardButtonSwitchInline_layer157();
                case TL_keyboardButtonGame_layer228.constructor:
                    return new TL_keyboardButtonGame_layer228();
                case TL_keyboardButtonGame_layer223.constructor:
                    return new TL_keyboardButtonGame_layer223();
                case TL_keyboardButtonUrl_layer228.constructor:
                    return new TL_keyboardButtonUrl_layer228();
                case TL_keyboardButtonUrl_layer223.constructor:
                    return new TL_keyboardButtonUrl_layer223();
                case TL_keyboardButtonUrlAuth_layer228.constructor:
                    return new TL_keyboardButtonUrlAuth_layer228();
                case TL_keyboardButtonUrlAuth_layer223.constructor:
                    return new TL_keyboardButtonUrlAuth_layer223();
                case TL_inputKeyboardButtonUrlAuth_layer228.constructor:
                    return new TL_inputKeyboardButtonUrlAuth_layer228();
                case TL_inputKeyboardButtonUrlAuth_layer223.constructor:
                    return new TL_inputKeyboardButtonUrlAuth_layer223();
                case TL_keyboardButtonBuy_layer228.constructor:
                    return new TL_keyboardButtonBuy_layer228();
                case TL_keyboardButtonBuy_layer223.constructor:
                    return new TL_keyboardButtonBuy_layer223();
                case TL_keyboardButtonCopy_layer228.constructor:
                    return new TL_keyboardButtonCopy_layer228();
                case TL_keyboardButtonCopy_layer223.constructor:
                    return new TL_keyboardButtonCopy_layer223();
                case TL_inputKeyboardButtonUserProfile_layer228.constructor:
                    return new TL_inputKeyboardButtonUserProfile_layer228();
                case TL_inputKeyboardButtonUserProfile_layer223.constructor:
                    return new TL_inputKeyboardButtonUserProfile_layer223();
                case TL_keyboardButtonUserProfile_layer228.constructor:
                    return new TL_keyboardButtonUserProfile_layer228();
                case TL_keyboardButtonUserProfile_layer223.constructor:
                    return new TL_keyboardButtonUserProfile_layer223();
                case TL_keyboardButtonWebView_layer228.constructor:
                    return new TL_keyboardButtonWebView_layer228();
                case TL_keyboardButtonWebView_layer223.constructor:
                    return new TL_keyboardButtonWebView_layer223();
            }
            return null;
        }

        public static KeyboardInlineButton TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            KeyboardButton keyboardButtonLegacy = KeyboardButton.fromConstructor(constructor);
            if (keyboardButtonLegacy != null) {
                keyboardButtonLegacy.readParams(stream, exception);

                final TL_keyboardInlineButton button = new TL_keyboardInlineButton();
                button.text = keyboardButtonLegacy.text;
                button.type = new TL_inlineButtonTypeDisabled();
                return button;
            }

            return TLdeserialize(KeyboardInlineButton.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    public static class KeyboardButtonRow extends TLObject {
        public static final int constructor = 0x77608b83;

        public ArrayList<KeyboardButton> buttons = new ArrayList<>();

        public static KeyboardButtonRow TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final KeyboardButtonRow result = KeyboardButtonRow.constructor != constructor ? null : new KeyboardButtonRow();
            return TLdeserialize(KeyboardButtonRow.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            buttons = Vector.deserialize(stream, KeyboardButton::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, buttons);
        }
    }

    public static abstract class KeyboardButton extends TLObject implements KeyboardButtonProto {
        protected int flags;
        public String text;
        public KeyboardButtonStyle style;
        public ButtonType type;

        public static KeyboardButton fromConstructor(int constructor) {
            switch (constructor) {
                case TL_keyboardButton.constructor:
                    return new TL_keyboardButton();
                case TL_keyboardButtonSimpleWebView_layer228.constructor:
                    return new TL_keyboardButtonSimpleWebView_layer228();
                case TL_keyboardButtonSimpleWebView_layer223.constructor:
                    return new TL_keyboardButtonSimpleWebView_layer223();
                case TL_keyboardButtonRequestPhone_layer228.constructor:
                    return new TL_keyboardButtonRequestPhone_layer228();
                case TL_keyboardButtonRequestPhone_layer223.constructor:
                    return new TL_keyboardButtonRequestPhone_layer223();
                case TL_keyboardButtonRequestGeoLocation_layer228.constructor:
                    return new TL_keyboardButtonRequestGeoLocation_layer228();
                case TL_keyboardButtonRequestGeoLocation_layer223.constructor:
                    return new TL_keyboardButtonRequestGeoLocation_layer223();
                case TL_keyboardButtonRequestPoll_layer228.constructor:
                    return new TL_keyboardButtonRequestPoll_layer228();
                case TL_keyboardButtonRequestPoll_layer223.constructor:
                    return new TL_keyboardButtonRequestPoll_layer223();
                case TL_keyboardButton_layer228.constructor:
                    return new TL_keyboardButton_layer228();
                case TL_keyboardButton_layer223.constructor:
                    return new TL_keyboardButton_layer223();
                case TL_keyboardButtonRequestPeer_layer228.constructor:
                    return new TL_keyboardButtonRequestPeer_layer228();
                case TL_keyboardButtonRequestPeer_layer223.constructor:
                    return new TL_keyboardButtonRequestPeer_layer223();
                case TL_keyboardButtonRequestPeer_layer168.constructor:
                    return new TL_keyboardButtonRequestPeer_layer168();
                case TL_inputKeyboardButtonRequestPeer_layer228.constructor:
                    return new TL_inputKeyboardButtonRequestPeer_layer228();
                case TL_inputKeyboardButtonRequestPeer_layer221.constructor:
                    return new TL_inputKeyboardButtonRequestPeer_layer221();
            }
            return null;
        }

        public static KeyboardButton TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            // KeyboardInlineButton keyboardInlineButtonLegacy = KeyboardInlineButton.fromConstructorLegacy(constructor);
            KeyboardInlineButton keyboardInlineButtonLegacy = KeyboardInlineButton.fromConstructor(constructor);
            if (keyboardInlineButtonLegacy != null) {
                keyboardInlineButtonLegacy.readParams(stream, exception);

                final TL_keyboardButton button = new TL_keyboardButton_layer223();
                button.text = keyboardInlineButtonLegacy.text;
                button.type = new TL_buttonTypeDefault();
                return button;
            }

            return TLdeserialize(KeyboardButton.class, fromConstructor(constructor), stream, constructor, exception);
        }
    }

    /* * */

    public static class TL_keyboardButton extends KeyboardButton {
        public static final int constructor = 0x2F67A72F;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            type = ButtonType.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            type.serializeToStream(stream);
        }

        @Override
        public ButtonTypeProto getType() {
            return type;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    public static class TL_keyboardInlineButton extends KeyboardInlineButton {
        public static final int constructor = 0x11C1A322;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            type = InlineButtonType.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            type.serializeToStream(stream);
        }

        @Override
        public ButtonTypeProto getType() {
            return type;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    public static class TL_keyboardInlineButton_legacy extends TL_keyboardInlineButton {
        public static final int constructor = 0x9C1C0C55;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            type = InlineButtonType.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            type.serializeToStream(stream);
        }
    }



    // region Legacy button classes

    private static class TL_keyboardButton_layer228 extends TL_keyboardButton {
        public static final int constructor = 0x7D170CFF;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            type = new TL_buttonTypeDefault();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButton_layer223 extends TL_keyboardButton {
        public static final int constructor = 0xa2fa4880;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            type = new TL_buttonTypeDefault();
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonRequestPhone_layer228 extends TL_keyboardButton {
        public static final int constructor = 0x417EFD8F;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_buttonTypeRequestPhone();
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonRequestPhone_layer223 extends TL_keyboardButtonRequestPhone_layer228 {
        public static final int constructor = 0xb16a6c29;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_buttonTypeRequestPhone();
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonGame_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0x89C590F9;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_inlineButtonTypeGame();
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonGame_layer223 extends TL_keyboardButtonGame_layer228 {
        public static final int constructor = 0x50f41ccf;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_inlineButtonTypeGame();
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonUrl_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xD80C25EC;

        public final TL_inlineButtonTypeUrl mType = new TL_inlineButtonTypeUrl();

        public TL_keyboardButtonUrl_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonUrl_layer223 extends TL_keyboardButtonUrl_layer228 {
        public static final int constructor = 0x258aff05;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonRequestGeoLocation_layer228 extends TL_keyboardButton {
        public static final int constructor = 0xAA40F94D;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_buttonTypeRequestGeoLocation();
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonRequestGeoLocation_layer223 extends TL_keyboardButtonRequestGeoLocation_layer228 {
        public static final int constructor = 0xfc796b3f;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_buttonTypeRequestGeoLocation();
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonUrlAuth_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xF51006F9;

        public final TL_inlineButtonTypeUrlAuth mType = new TL_inlineButtonTypeUrlAuth();

        public TL_keyboardButtonUrlAuth_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                mType.fwd_text = stream.readString(exception);
            }
            mType.url = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.flags = flags & FLAG_0;
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.fwd_text != null);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(mType.fwd_text);
            }
            stream.writeString(mType.url);
            stream.writeInt32(mType.button_id);
        }
    }

    private static class TL_keyboardButtonUrlAuth_layer223 extends TL_keyboardButtonUrlAuth_layer228 {
        public static final int constructor = 0x10b78d29;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            text = stream.readString(exception);
            if (hasFlag(flags, FLAG_0)) {
                mType.fwd_text = stream.readString(exception);
            }
            mType.url = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.flags = flags & FLAG_0;
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(text);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(mType.fwd_text);
            }
            stream.writeString(mType.url);
            stream.writeInt32(mType.button_id);
        }
    }

    private static class TL_inputKeyboardButtonUrlAuth_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0x68013E72;

        public final TL_inputInlineButtonTypeUrlAuth mType = new TL_inputInlineButtonTypeUrlAuth();

        public TL_inputKeyboardButtonUrlAuth_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            mType.request_write_access = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                mType.fwd_text = stream.readString(exception);
            }
            mType.url = stream.readString(exception);
            mType.bot = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.request_write_access);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.fwd_text != null);
            mType.flags = setFlag(mType.flags, FLAG_2, mType.bot != null);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.request_write_access);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            if (mType.fwd_text != null) {
                stream.writeString(mType.fwd_text);
            }
            stream.writeString(mType.url);
            mType.bot.serializeToStream(stream);
        }
    }

    private static class TL_inputKeyboardButtonUrlAuth_layer223 extends TL_inputKeyboardButtonUrlAuth_layer228 {
        public static final int constructor = 0xd02e7fd4;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.request_write_access = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                mType.fwd_text = stream.readString(exception);
            }
            mType.url = stream.readString(exception);
            mType.bot = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.request_write_access);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.fwd_text != null);
            mType.flags = setFlag(mType.flags, FLAG_2, mType.bot != null);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.request_write_access);
            stream.writeInt32(flags);
            stream.writeString(text);
            if (mType.fwd_text != null) {
                stream.writeString(mType.fwd_text);
            }
            stream.writeString(mType.url);
            mType.bot.serializeToStream(stream);
        }
    }

    private static class TL_keyboardButtonRequestPoll_layer228 extends TL_keyboardButton {
        public static final int constructor = 0x7A11D782;

        public final TL_buttonTypeRequestPoll mType = new TL_buttonTypeRequestPoll();

        public TL_keyboardButtonRequestPoll_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            mType.flags = flags & FLAG_0;
            if (hasFlag(flags, FLAG_0)) {
                mType.quiz = stream.readBool(exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(mType.quiz);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonRequestPoll_layer223 extends TL_keyboardButtonRequestPoll_layer228 {
        public static final int constructor = 0xbbc7515d;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.flags = flags & FLAG_0;
            if (hasFlag(flags, FLAG_0)) {
                mType.quiz = stream.readBool(exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeBool(mType.quiz);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonBuy_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0x3FA53905;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_inlineButtonTypeBuy();
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonBuy_layer223 extends TL_keyboardButtonBuy_layer228 {
        public static final int constructor = 0xafd93fbb;

        public void readParams(InputSerializedData stream, boolean exception) {
            type = new TL_inlineButtonTypeBuy();
            text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
        }
    }

    private static class TL_keyboardButtonCopy_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xBCC4AF10;

        public final TL_inlineButtonTypeCopy mType = new TL_inlineButtonTypeCopy();

        public TL_keyboardButtonCopy_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.copy_text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeString(mType.copy_text);
        }
    }

    private static class TL_keyboardButtonCopy_layer223 extends TL_keyboardButtonCopy_layer228 {
        public static final int constructor = 0x75d2698e;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.copy_text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(mType.copy_text);
        }
    }

    private static class TL_inputKeyboardButtonUserProfile_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0x7D5E07C7;

        public final TL_inputInlineButtonTypeUserProfile mType = new TL_inputInlineButtonTypeUserProfile();

        public TL_inputKeyboardButtonUserProfile_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.user_id = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            mType.user_id.serializeToStream(stream);
        }
    }

    private static class TL_inputKeyboardButtonUserProfile_layer223 extends TL_inputKeyboardButtonUserProfile_layer228 {
        public static final int constructor = 0xe988037b;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.user_id = TLRPC.InputUser.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            mType.user_id.serializeToStream(stream);
        }
    }

    private static class TL_keyboardButtonUserProfile_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xC0FD5D09;

        public final TL_inlineButtonTypeUserProfile mType = new TL_inlineButtonTypeUserProfile();

        public TL_keyboardButtonUserProfile_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.user_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeInt64(mType.user_id);
        }
    }

    private static class TL_keyboardButtonUserProfile_layer223 extends TL_keyboardButtonUserProfile_layer228 {
        public static final int constructor = 0x308660c1;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.user_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeInt64(mType.user_id);
        }
    }

    private static class TL_keyboardButtonWebView_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xE846B1A0;

        public final TL_inlineButtonTypeWebView mType = new TL_inlineButtonTypeWebView();

        public TL_keyboardButtonWebView_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonWebView_layer223 extends TL_keyboardButtonWebView_layer228 {
        public static final int constructor = 0x13767230;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonSimpleWebView_layer228 extends TL_keyboardButton {
        public static final int constructor = 0xE15C4370;

        public final TL_buttonTypeSimpleWebView mType = new TL_buttonTypeSimpleWebView();

        public TL_keyboardButtonSimpleWebView_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonSimpleWebView_layer223 extends TL_keyboardButtonSimpleWebView_layer228 {
        public static final int constructor = 0xa0c0505c;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeString(mType.url);
        }
    }

    private static class TL_keyboardButtonRequestPeer_layer228 extends TL_keyboardButton {
        public static final int constructor = 0x5B0F15F5;

        public final TL_buttonTypeRequestPeer mType = new TL_buttonTypeRequestPeer();

        public TL_keyboardButtonRequestPeer_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.max_quantity = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeInt32(mType.button_id);
            mType.peer_type.serializeToStream(stream);
            stream.writeInt32(mType.max_quantity);
        }
    }

    private static class TL_keyboardButtonRequestPeer_layer223 extends TL_keyboardButtonRequestPeer_layer228 {
        public static final int constructor = 0x53d7bfd8;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.max_quantity = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeInt32(mType.button_id);
            mType.peer_type.serializeToStream(stream);
            stream.writeInt32(mType.max_quantity);
        }
    }

    private static class TL_keyboardButtonRequestPeer_layer168 extends TL_keyboardButtonRequestPeer_layer228 {
        public static final int constructor = 0xd0b468c;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeInt32(mType.button_id);
            mType.peer_type.serializeToStream(stream);
        }
    }

    private static class TL_keyboardButtonCallback_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0xE62BC960;

        public final TL_inlineButtonTypeCallback mType = new TL_inlineButtonTypeCallback();

        public TL_keyboardButtonCallback_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.requires_password = hasFlag(flags, FLAG_0);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.data = stream.readByteArray(exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.requires_password);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.requires_password);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeByteArray(mType.data);
        }
    }

    private static class TL_keyboardButtonCallback_layer223 extends TL_keyboardButtonCallback_layer228 {
        public static final int constructor = 0x35bbdb6b;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.requires_password = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            mType.data = stream.readByteArray(exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.requires_password);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.requires_password);
            stream.writeInt32(flags);
            stream.writeString(text);
            stream.writeByteArray(mType.data);
        }
    }

    private static class TL_keyboardButtonCallback_layer117 extends TL_keyboardButtonCallback_layer228 {
        public static final int constructor = 0x683a5e46;

        public void readParams(InputSerializedData stream, boolean exception) {
            text = stream.readString(exception);
            mType.data = stream.readByteArray(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(text);
            stream.writeByteArray(mType.data);
        }
    }

    private static class TL_keyboardButtonSwitchInline_layer228 extends TL_keyboardInlineButton {
        public static final int constructor = 0x991399FC;

        public final TL_inlineButtonTypeSwitchInline mType = new TL_inlineButtonTypeSwitchInline();

        public TL_keyboardButtonSwitchInline_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            mType.same_peer = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            mType.query = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                mType.peer_types = Vector.deserialize(stream, TLRPC.InlineQueryPeerType::TLdeserialize, exception);
            }
            mType.flags = setFlag(mType.flags, FLAG_0, mType.same_peer);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.peer_types != null);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.same_peer);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeString(mType.query);
            if (mType.peer_types != null) {
                Vector.serialize(stream, mType.peer_types);
            }
        }
    }

    private static class TL_keyboardButtonSwitchInline_layer223 extends TL_keyboardButtonSwitchInline_layer228 {
        public static final int constructor = 0x93b9fbb5;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.same_peer = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            mType.query = stream.readString(exception);
            if (hasFlag(flags, FLAG_1)) {
                mType.peer_types = Vector.deserialize(stream, TLRPC.InlineQueryPeerType::TLdeserialize, exception);
            }
            mType.flags = setFlag(mType.flags, FLAG_0, mType.same_peer);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.peer_types != null);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.same_peer);
            stream.writeInt32(flags);
            stream.writeString(text);
            stream.writeString(mType.query);
            if (mType.peer_types != null) {
                Vector.serialize(stream, mType.peer_types);
            }
        }
    }

    private static class TL_keyboardButtonSwitchInline_layer157 extends TL_keyboardButtonSwitchInline_layer228 {
        public static final int constructor = 0x568a748;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.same_peer = hasFlag(flags, FLAG_0);
            text = stream.readString(exception);
            mType.query = stream.readString(exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.same_peer);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.same_peer);
            stream.writeInt32(flags);
            stream.writeString(text);
            stream.writeString(mType.query);
        }
    }

    private static class TL_inputKeyboardButtonRequestPeer_layer228 extends TL_keyboardButton {
        public static final int constructor = 0x02B78156;

        public final TL_inputButtonTypeRequestPeer mType = new TL_inputButtonTypeRequestPeer();

        public TL_inputKeyboardButtonRequestPeer_layer228() {
            type = mType;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.name_requested = hasFlag(flags, FLAG_0);
            mType.username_requested = hasFlag(flags, FLAG_1);
            mType.photo_requested = hasFlag(flags, FLAG_2);
            if (hasFlag(flags, FLAG_10)) {
                style = KeyboardButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            text = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.max_quantity = stream.readInt32(exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.name_requested);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.username_requested);
            mType.flags = setFlag(mType.flags, FLAG_2, mType.photo_requested);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.name_requested);
            flags = setFlag(flags, FLAG_1, mType.username_requested);
            flags = setFlag(flags, FLAG_2, mType.photo_requested);
            flags = setFlag(flags, FLAG_10, style != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_10)) {
                style.serializeToStream(stream);
            }
            stream.writeString(text);
            stream.writeInt32(mType.button_id);
            mType.peer_type.serializeToStream(stream);
            stream.writeInt32(mType.max_quantity);
        }
    }

    private static class TL_inputKeyboardButtonRequestPeer_layer221 extends TL_inputKeyboardButtonRequestPeer_layer228 {
        public static final int constructor = 0xC9662D05;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            mType.name_requested = hasFlag(flags, FLAG_0);
            mType.username_requested = hasFlag(flags, FLAG_1);
            mType.photo_requested = hasFlag(flags, FLAG_2);
            text = stream.readString(exception);
            mType.button_id = stream.readInt32(exception);
            mType.peer_type = TLRPC.RequestPeerType.TLdeserialize(stream, stream.readInt32(exception), exception);
            mType.max_quantity = stream.readInt32(exception);
            mType.flags = setFlag(mType.flags, FLAG_0, mType.name_requested);
            mType.flags = setFlag(mType.flags, FLAG_1, mType.username_requested);
            mType.flags = setFlag(mType.flags, FLAG_2, mType.photo_requested);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, mType.name_requested);
            flags = setFlag(flags, FLAG_1, mType.username_requested);
            flags = setFlag(flags, FLAG_2, mType.photo_requested);
            stream.writeInt32(flags);
            stream.writeString(text);
            stream.writeInt32(mType.button_id);
            mType.peer_type.serializeToStream(stream);
            stream.writeInt32(mType.max_quantity);
        }
    }

    // endregion
}
