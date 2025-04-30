package org.telegram.tgnet.tl;

import androidx.annotation.Nullable;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

public class TL_account {

    public static class contentSettings extends TLObject {
        public static final int constructor = 0x57e28221;

        public int flags;
        public boolean sensitive_enabled;
        public boolean sensitive_can_change;

        public static contentSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (contentSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account.contentSettings", constructor));
                } else {
                    return null;
                }
            }
            contentSettings result = new contentSettings();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            sensitive_enabled = (flags & 1) != 0;
            sensitive_can_change = (flags & 2) != 0;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = sensitive_enabled ? (flags | 1) : (flags &~ 1);
            flags = sensitive_can_change ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
        }
    }

    public static class setContentSettings extends TLObject {
        public static final int constructor = 0xb574b16b;

        public int flags;
        public boolean sensitive_enabled;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = sensitive_enabled ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
        }
    }

    public static class getContentSettings extends TLObject {
        public static final int constructor = 0x8b9b4dae;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return contentSettings.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class EmailVerified extends TLObject {
        public static EmailVerified TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            EmailVerified result = null;
            switch (constructor) {
                case 0x2b96cd1b:
                    result = new TL_emailVerified();
                    break;
                case 0xe1bb0d61:
                    result = new TL_emailVerifiedLogin();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_EmailVerified", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_emailVerified extends EmailVerified {
        public static final int constructor = 0x2b96cd1b;

        public String email;

        public void readParams(InputSerializedData stream, boolean exception) {
            email = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(email);
        }
    }

    public static class TL_emailVerifiedLogin extends EmailVerified {
        public static final int constructor = 0xe1bb0d61;

        public String email;
        public TLRPC.auth_SentCode sent_code;

        public void readParams(InputSerializedData stream, boolean exception) {
            email = stream.readString(exception);
            sent_code = TLRPC.auth_SentCode.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(email);
            sent_code.serializeToStream(stream);
        }
    }

    public static class passwordSettings extends TLObject {
        public static final int constructor = 0x9a5c33e5;

        public int flags;
        public String email;
        public TLRPC.TL_secureSecretSettings secure_settings;

        public static passwordSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (passwordSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_passwordSettings", constructor));
                } else {
                    return null;
                }
            }
            passwordSettings result = new passwordSettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                email = stream.readString(exception);
            }
            if ((flags & 2) != 0) {
                secure_settings = TLRPC.TL_secureSecretSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(email);
            }
            if ((flags & 2) != 0) {
                secure_settings.serializeToStream(stream);
            }
        }
    }

    public static class privacyRules extends TLObject {
        public static final int constructor = 0x50a04e45;

        public ArrayList<TLRPC.PrivacyRule> rules = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static privacyRules TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (privacyRules.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_privacyRules", constructor));
                }
                return null;
            }
            privacyRules result = new privacyRules();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            rules = Vector.deserialize(stream, TLRPC.PrivacyRule::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, rules);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class EmojiStatuses extends TLObject {

        public long hash;
        public ArrayList<TLRPC.EmojiStatus> statuses = new ArrayList<>();

        public static EmojiStatuses TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            EmojiStatuses result;
            switch (constructor) {
                case TL_emojiStatusesNotModified.constructor:
                    result = new TL_emojiStatusesNotModified();
                    break;
                case TL_emojiStatuses.constructor:
                    result = new TL_emojiStatuses();
                    break;
                default:
                    if (exception) {
                        throw new RuntimeException(String.format("can't parse magic %x in account_EmojiStatuses", constructor));
                    }
                    return null;
            }
            result.readParams(stream, exception);
            return result;
        }
    }

    public static class TL_emojiStatusesNotModified extends EmojiStatuses {
        public static final int constructor = 0xd08ce645;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_emojiStatuses extends EmojiStatuses {
        public static final int constructor = 0x90c467d1;

        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            statuses = Vector.deserialize(stream, TLRPC.EmojiStatus::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, statuses);
        }
    }

    public static class Themes extends TLObject {

        public static Themes TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            Themes result;
            switch (constructor) {
                case TL_themes.constructor:
                    result = new TL_themes();
                    break;
                case TL_themesNotModified.constructor:
                    result = new TL_themesNotModified();
                    break;
                default:
                    if (exception) {
                        throw new RuntimeException(String.format("can't parse magic %x in account_Themes", constructor));
                    }
                    return null;
            }
            result.readParams(stream, exception);
            return result;
        }
    }

    public static class TL_themes extends Themes {
        public static final int constructor = 0x9a3d8c6d;

        public long hash;
        public ArrayList<TLRPC.TL_theme> themes = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            themes = Vector.deserialize(stream, TLRPC.TL_theme::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, themes);
        }
    }

    public static class TL_themesNotModified extends Themes {
        public static final int constructor = 0xf41eb622;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class WallPapers extends TLObject {
        public static WallPapers TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            WallPapers result = null;
            switch (constructor) {
                case 0x1c199183:
                    result = new TL_wallPapersNotModified();
                    break;
                case 0xcdc3858c:
                    result = new TL_wallPapers();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_WallPapers", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_wallPapersNotModified extends WallPapers {
        public static final int constructor = 0x1c199183;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_wallPapers extends WallPapers {
        public static final int constructor = 0xcdc3858c;

        public long hash;
        public ArrayList<TLRPC.WallPaper> wallpapers = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            wallpapers = Vector.deserialize(stream, TLRPC.WallPaper::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, wallpapers);
        }
    }

    public static class Password extends TLObject {

        public int flags;
        public boolean has_recovery;
        public boolean has_secure_values;
        public boolean has_password;
        public TLRPC.PasswordKdfAlgo current_algo;
        public byte[] srp_B;
        public long srp_id;
        public String hint;
        public String email_unconfirmed_pattern;
        public TLRPC.PasswordKdfAlgo new_algo;
        public TLRPC.SecurePasswordKdfAlgo new_secure_algo;
        public byte[] secure_random;
        public int pending_reset_date;
        public String login_email_pattern;

        public static Password TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            Password result = null;
            switch (constructor) {
                case 0x957b50fb:
                    result = new TL_password();
                    break;
                case 0x185b184f:
                    result = new TL_password_layer144();
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

    public static class TL_password extends Password {
        public static final int constructor = 0x957b50fb;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_recovery = (flags & 1) != 0;
            has_secure_values = (flags & 2) != 0;
            has_password = (flags & 4) != 0;
            if ((flags & 4) != 0) {
                current_algo = TLRPC.PasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 4) != 0) {
                srp_B = stream.readByteArray(exception);
            }
            if ((flags & 4) != 0) {
                srp_id = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                hint = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                email_unconfirmed_pattern = stream.readString(exception);
            }
            new_algo = TLRPC.PasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            new_secure_algo = TLRPC.SecurePasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            secure_random = stream.readByteArray(exception);
            if ((flags & 32) != 0) {
                pending_reset_date = stream.readInt32(exception);
            }
            if ((flags & 64) != 0) {
                login_email_pattern = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_recovery ? (flags | 1) : (flags &~ 1);
            flags = has_secure_values ? (flags | 2) : (flags &~ 2);
            flags = has_password ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            if ((flags & 4) != 0) {
                current_algo.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                stream.writeByteArray(srp_B);
            }
            if ((flags & 4) != 0) {
                stream.writeInt64(srp_id);
            }
            if ((flags & 8) != 0) {
                stream.writeString(hint);
            }
            if ((flags & 16) != 0) {
                stream.writeString(email_unconfirmed_pattern);
            }
            new_algo.serializeToStream(stream);
            new_secure_algo.serializeToStream(stream);
            stream.writeByteArray(secure_random);
            if ((flags & 32) != 0) {
                stream.writeInt32(pending_reset_date);
            }
            if ((flags & 64) != 0) {
                stream.writeString(login_email_pattern);
            }
        }
    }

    public static class TL_password_layer144 extends Password {
        public static final int constructor = 0x185b184f;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            has_recovery = (flags & 1) != 0;
            has_secure_values = (flags & 2) != 0;
            has_password = (flags & 4) != 0;
            if ((flags & 4) != 0) {
                current_algo = TLRPC.PasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 4) != 0) {
                srp_B = stream.readByteArray(exception);
            }
            if ((flags & 4) != 0) {
                srp_id = stream.readInt64(exception);
            }
            if ((flags & 8) != 0) {
                hint = stream.readString(exception);
            }
            if ((flags & 16) != 0) {
                email_unconfirmed_pattern = stream.readString(exception);
            }
            new_algo = TLRPC.PasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            new_secure_algo = TLRPC.SecurePasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
            secure_random = stream.readByteArray(exception);
            if ((flags & 32) != 0) {
                pending_reset_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = has_recovery ? (flags | 1) : (flags &~ 1);
            flags = has_secure_values ? (flags | 2) : (flags &~ 2);
            flags = has_password ? (flags | 4) : (flags &~ 4);
            stream.writeInt32(flags);
            if ((flags & 4) != 0) {
                current_algo.serializeToStream(stream);
            }
            if ((flags & 4) != 0) {
                stream.writeByteArray(srp_B);
            }
            if ((flags & 4) != 0) {
                stream.writeInt64(srp_id);
            }
            if ((flags & 8) != 0) {
                stream.writeString(hint);
            }
            if ((flags & 16) != 0) {
                stream.writeString(email_unconfirmed_pattern);
            }
            new_algo.serializeToStream(stream);
            new_secure_algo.serializeToStream(stream);
            stream.writeByteArray(secure_random);
            if ((flags & 32) != 0) {
                stream.writeInt32(pending_reset_date);
            }
        }
    }

    public static class tmpPassword extends TLObject {
        public static final int constructor = 0xdb64fd34;

        public byte[] tmp_password;
        public int valid_until;

        public static tmpPassword TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (tmpPassword.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_tmpPassword", constructor));
                } else {
                    return null;
                }
            }
            tmpPassword result = new tmpPassword();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            tmp_password = stream.readByteArray(exception);
            valid_until = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeByteArray(tmp_password);
            stream.writeInt32(valid_until);
        }
    }

    public static class authorizationForm extends TLObject {
        public static final int constructor = 0xad2e1cd8;

        public int flags;
        public ArrayList<TLRPC.SecureRequiredType> required_types = new ArrayList<>();
        public ArrayList<TLRPC.TL_secureValue> values = new ArrayList<>();
        public ArrayList<TLRPC.SecureValueError> errors = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();
        public String privacy_policy_url;

        public static authorizationForm TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (authorizationForm.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_authorizationForm", constructor));
                } else {
                    return null;
                }
            }
            authorizationForm result = new authorizationForm();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            required_types = Vector.deserialize(stream, TLRPC.SecureRequiredType::TLdeserialize, exception);
            values = Vector.deserialize(stream, TLRPC.TL_secureValue::TLdeserialize, exception);
            errors = Vector.deserialize(stream, TLRPC.SecureValueError::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
            if ((flags & 1) != 0) {
                privacy_policy_url = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            Vector.serialize(stream, required_types);
            Vector.serialize(stream, values);
            Vector.serialize(stream, errors);
            Vector.serialize(stream, users);
            if ((flags & 1) != 0) {
                stream.writeString(privacy_policy_url);
            }
        }
    }

    public static class autoDownloadSettings extends TLObject {
        public static final int constructor = 0x63cacf26;

        public TLRPC.TL_autoDownloadSettings low;
        public TLRPC.TL_autoDownloadSettings medium;
        public TLRPC.TL_autoDownloadSettings high;

        public static autoDownloadSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (autoDownloadSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_autoDownloadSettings", constructor));
                } else {
                    return null;
                }
            }
            autoDownloadSettings result = new autoDownloadSettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            low = TLRPC.TL_autoDownloadSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            medium = TLRPC.TL_autoDownloadSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            high = TLRPC.TL_autoDownloadSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            low.serializeToStream(stream);
            medium.serializeToStream(stream);
            high.serializeToStream(stream);
        }
    }

    public static class sentEmailCode extends TLObject {
        public static final int constructor = 0x811f854f;

        public String email_pattern;
        public int length;

        public static sentEmailCode TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (sentEmailCode.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_sentEmailCode", constructor));
                } else {
                    return null;
                }
            }
            sentEmailCode result = new sentEmailCode();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            email_pattern = stream.readString(exception);
            length = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(email_pattern);
            stream.writeInt32(length);
        }
    }

    public static class webAuthorizations extends TLObject {
        public static final int constructor = 0xed56c9fc;

        public ArrayList<TLRPC.TL_webAuthorization> authorizations = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static webAuthorizations TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (webAuthorizations.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_webAuthorizations", constructor));
                }
                return null;
            }
            webAuthorizations result = new webAuthorizations();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            authorizations = Vector.deserialize(stream, TLRPC.TL_webAuthorization::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, authorizations);
            Vector.serialize(stream, users);
        }
    }

    public static class passwordInputSettings extends TLObject {
        public static final int constructor = 0xc23727c9;

        public int flags;
        public TLRPC.PasswordKdfAlgo new_algo;
        public byte[] new_password_hash;
        public String hint;
        public String email;
        public TLRPC.TL_secureSecretSettings new_secure_settings;

        public static passwordInputSettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (passwordInputSettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_passwordInputSettings", constructor));
                } else {
                    return null;
                }
            }
            passwordInputSettings result = new passwordInputSettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                new_algo = TLRPC.PasswordKdfAlgo.TLdeserialize(stream, stream.readInt32(exception), exception);
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
            if ((flags & 4) != 0) {
                new_secure_settings = TLRPC.TL_secureSecretSettings.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                new_algo.serializeToStream(stream);
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
            if ((flags & 4) != 0) {
                new_secure_settings.serializeToStream(stream);
            }
        }
    }

    public static class ResetPasswordResult extends TLObject {

        public static ResetPasswordResult TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            ResetPasswordResult result = null;
            switch (constructor) {
                case 0xe3779861:
                    result = new resetPasswordFailedWait();
                    break;
                case 0xe9effc7d:
                    result = new resetPasswordRequestedWait();
                    break;
                case 0xe926d63e:
                    result = new resetPasswordOk();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_ResetPasswordResult", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class resetPasswordFailedWait extends ResetPasswordResult {
        public static final int constructor = 0xe3779861;

        public int retry_date;

        public void readParams(InputSerializedData stream, boolean exception) {
            retry_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(retry_date);
        }
    }

    public static class resetPasswordRequestedWait extends ResetPasswordResult {
        public static final int constructor = 0xe9effc7d;

        public int until_date;

        public void readParams(InputSerializedData stream, boolean exception) {
            until_date = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(until_date);
        }
    }

    public static class resetPasswordOk extends ResetPasswordResult {
        public static final int constructor = 0xe926d63e;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class authorizations extends TLObject {
        public static final int constructor = 0x4bff8ea0;

        public int authorization_ttl_days;
        public ArrayList<TLRPC.TL_authorization> authorizations = new ArrayList<>();

        public static TL_account.authorizations TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_account.authorizations.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_authorizations", constructor));
                } else {
                    return null;
                }
            }
            TL_account.authorizations result = new authorizations();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            authorization_ttl_days = stream.readInt32(exception);
            authorizations = Vector.deserialize(stream, TLRPC.TL_authorization::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(authorization_ttl_days);
            Vector.serialize(stream, authorizations);
        }
    }

    public static class registerDevice extends TLObject {
        public static final int constructor = 0xec86017a;

        public int flags;
        public boolean no_muted;
        public int token_type;
        public String token;
        public boolean app_sandbox;
        public byte[] secret;
        public ArrayList<Long> other_uids = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = no_muted ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            stream.writeInt32(token_type);
            stream.writeString(token);
            stream.writeBool(app_sandbox);
            stream.writeByteArray(secret);
            Vector.serializeLong(stream, other_uids);
        }
    }

    public static class unregisterDevice extends TLObject {
        public static final int constructor = 0x6a0d3206;

        public int token_type;
        public String token;
        public ArrayList<Long> other_uids = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(token_type);
            stream.writeString(token);
            Vector.serializeLong(stream, other_uids);
        }
    }

    public static class updateNotifySettings extends TLObject {
        public static final int constructor = 0x84be5b93;

        public TLRPC.InputNotifyPeer peer;
        public TLRPC.TL_inputPeerNotifySettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            settings.serializeToStream(stream);
        }
    }

    public static class getNotifySettings extends TLObject {
        public static final int constructor = 0x12b3ad31;

        public TLRPC.InputNotifyPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.PeerNotifySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class resetNotifySettings extends TLObject {
        public static final int constructor = 0xdb7e1747;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class updateProfile extends TLObject {
        public static final int constructor = 0x78515775;

        public int flags;
        public String first_name;
        public String last_name;
        public String about;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeString(first_name);
            }
            if ((flags & 2) != 0) {
                stream.writeString(last_name);
            }
            if ((flags & 4) != 0) {
                stream.writeString(about);
            }
        }
    }

    public static class updateStatus extends TLObject {
        public static final int constructor = 0x6628562c;

        public boolean offline;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(offline);
        }
    }

    public static class getWallPapers extends TLObject {
        public static final int constructor = 0x7967d36;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return WallPapers.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class reportPeer extends TLObject {
        public static final int constructor = 0xc5ba3d86;

        public TLRPC.InputPeer peer;
        public TLRPC.ReportReason reason;
        public String message;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            reason.serializeToStream(stream);
            stream.writeString(message);
        }
    }

    public static class resetPassword extends TLObject {
        public static final int constructor = 0x9308ce1b;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return ResetPasswordResult.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class declinePasswordReset extends TLObject {
        public static final int constructor = 0x4c9409f6;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getChatThemes extends TLObject {
        public static final int constructor = 0xd638de89;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Themes.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class setAuthorizationTTL extends TLObject {
        public static final int constructor = 0xbf899aa0;

        public int authorization_ttl_days;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(authorization_ttl_days);
        }
    }

    public static class changeAuthorizationSettings extends TLObject {
        public static final int constructor = 0x40f48462;

        public int flags;
        public boolean confirmed;
        public long hash;
        public boolean encrypted_requests_disabled;
        public boolean call_requests_disabled;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = confirmed ? (flags | 8) : (flags &~ 8);
            stream.writeInt32(flags);
            stream.writeInt64(hash);
            if ((flags & 1) != 0) {
                stream.writeBool(encrypted_requests_disabled);
            }
            if ((flags & 2) != 0) {
                stream.writeBool(call_requests_disabled);
            }
        }
    }

    public static class checkUsername extends TLObject {
        public static final int constructor = 0x2714d86c;

        public String username;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }
    }

    public static class updateUsername extends TLObject {
        public static final int constructor = 0x3e0bdd7c;

        public String username;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
        }
    }

    public static class getPrivacy extends TLObject {
        public static final int constructor = 0xdadbc950;

        public TLRPC.InputPrivacyKey key;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return privacyRules.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
        }
    }

    public static class setPrivacy extends TLObject {
        public static final int constructor = 0xc9f81ce8;

        public TLRPC.InputPrivacyKey key;
        public ArrayList<TLRPC.InputPrivacyRule> rules = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return privacyRules.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            key.serializeToStream(stream);
            Vector.serialize(stream, rules);
        }
    }

    public static class deleteAccount extends TLObject {
        public static final int constructor = 0x418d4e0b;

        public String reason;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(reason);
        }
    }

    public static class getAccountTTL extends TLObject {
        public static final int constructor = 0x8fc711d;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_accountDaysTTL.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class setAccountTTL extends TLObject {
        public static final int constructor = 0x2442485e;

        public TLRPC.TL_accountDaysTTL ttl;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            ttl.serializeToStream(stream);
        }
    }

    public static class sendChangePhoneCode extends TLObject {
        public static final int constructor = 0x82574ae5;

        public String phone_number;
        public TLRPC.TL_codeSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_auth_sentCode.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            settings.serializeToStream(stream);
        }
    }

    public static class changePhone extends TLObject {
        public static final int constructor = 0x70c32edb;

        public String phone_number;
        public String phone_code_hash;
        public String phone_code;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.User.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
        }
    }

    public static class getWebAuthorizations extends TLObject {
        public static final int constructor = 0x182e6d6f;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return webAuthorizations.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class resetWebAuthorization extends TLObject {
        public static final int constructor = 0x2d01b9ef;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class resetWebAuthorizations extends TLObject {
        public static final int constructor = 0x682d2594;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getMultiWallPapers extends TLObject {
        public static final int constructor = 0x65ad71dc;

        public ArrayList<TLRPC.InputWallPaper> wallpapers = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.WallPaper::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, wallpapers);
        }
    }

    public static class getGlobalPrivacySettings extends TLObject {
        public static final int constructor = 0xeb2b4cf6;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.GlobalPrivacySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class setGlobalPrivacySettings extends TLObject {
        public static final int constructor = 0x1edaaac2;

        public TLRPC.GlobalPrivacySettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.GlobalPrivacySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            settings.serializeToStream(stream);
        }
    }

    public static class reportProfilePhoto extends TLObject {
        public static final int constructor = 0xfa8cc6f5;

        public TLRPC.InputPeer peer;
        public TLRPC.InputPhoto photo_id;
        public TLRPC.ReportReason reason;
        public String message;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            photo_id.serializeToStream(stream);
            reason.serializeToStream(stream);
            stream.writeString(message);
        }
    }

    public static class getAllSecureValues extends TLObject {
        public static final int constructor = 0xb288bc7d;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.TL_secureValue::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getSecureValue extends TLObject {
        public static final int constructor = 0x73665bc2;

        public ArrayList<TLRPC.SecureValueType> types = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.TL_secureValue::TLdeserialize);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, types);
        }
    }

    public static class saveSecureValue extends TLObject {
        public static final int constructor = 0x899fe31d;

        public TLRPC.TL_inputSecureValue value;
        public long secure_secret_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_secureValue.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            value.serializeToStream(stream);
            stream.writeInt64(secure_secret_id);
        }
    }

    public static class deleteSecureValue extends TLObject {
        public static final int constructor = 0xb880bc4b;

        public ArrayList<TLRPC.SecureValueType> types = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, types);
        }
    }

    public static class getAuthorizationForm extends TLObject {
        public static final int constructor = 0xa929597a;

        public long bot_id;
        public String scope;
        public String public_key;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return authorizationForm.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bot_id);
            stream.writeString(scope);
            stream.writeString(public_key);
        }
    }

    public static class acceptAuthorization extends TLObject {
        public static final int constructor = 0xf3ed4c73;

        public long bot_id;
        public String scope;
        public String public_key;
        public ArrayList<TLRPC.TL_secureValueHash> value_hashes = new ArrayList<>();
        public TLRPC.TL_secureCredentialsEncrypted credentials;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(bot_id);
            stream.writeString(scope);
            stream.writeString(public_key);
            Vector.serialize(stream, value_hashes);
            credentials.serializeToStream(stream);
        }
    }

    public static class sendVerifyPhoneCode extends TLObject {
        public static final int constructor = 0xa5a356f9;

        public String phone_number;
        public TLRPC.TL_codeSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_auth_sentCode.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            settings.serializeToStream(stream);
        }
    }

    public static class verifyPhone extends TLObject {
        public static final int constructor = 0x4dd3a7f6;

        public String phone_number;
        public String phone_code_hash;
        public String phone_code;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_number);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
        }
    }

    public static class sendVerifyEmailCode extends TLObject {
        public static final int constructor = 0x98e037bb;

        public TLRPC.EmailVerifyPurpose purpose;
        public String email;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return sentEmailCode.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            purpose.serializeToStream(stream);
            stream.writeString(email);
        }
    }

    public static class verifyEmail extends TLObject {
        public static final int constructor = 0x32da4cf;

        public TLRPC.EmailVerifyPurpose purpose;
        public TLRPC.EmailVerification verification;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return EmailVerified.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            purpose.serializeToStream(stream);
            verification.serializeToStream(stream);
        }
    }

    public static class confirmPasswordEmail extends TLObject {
        public static final int constructor = 0x8fdf1920;

        public String code;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(code);
        }
    }

    public static class resendPasswordEmail extends TLObject {
        public static final int constructor = 0x7a7f2a15;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class cancelPasswordEmail extends TLObject {
        public static final int constructor = 0xc1cbd5b6;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getContactSignUpNotification extends TLObject {
        public static final int constructor = 0x9f07c728;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class setContactSignUpNotification extends TLObject {
        public static final int constructor = 0xcff43f61;

        public boolean silent;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(silent);
        }
    }

    public static class getNotifyExceptions extends TLObject {
        public static final int constructor = 0x53577479;

        public int flags;
        public boolean compare_sound;
        public TLRPC.InputNotifyPeer peer;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = compare_sound ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                peer.serializeToStream(stream);
            }
        }
    }

    public static class getWallPaper extends TLObject {
        public static final int constructor = 0xfc8ddbea;

        public TLRPC.InputWallPaper wallpaper;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.WallPaper.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            wallpaper.serializeToStream(stream);
        }
    }

    public static class uploadWallPaper extends TLObject {
        public static final int constructor = 0xdd853661;

        public TLRPC.InputFile file;
        public String mime_type;
        public TLRPC.TL_wallPaperSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.WallPaper.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(mime_type);
            settings.serializeToStream(stream);
        }
    }

    public static class saveWallPaper extends TLObject {
        public static final int constructor = 0x6c5a5b37;

        public TLRPC.InputWallPaper wallpaper;
        public boolean unsave;
        public TLRPC.TL_wallPaperSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            wallpaper.serializeToStream(stream);
            stream.writeBool(unsave);
            settings.serializeToStream(stream);
        }
    }

    public static class installWallPaper extends TLObject {
        public static final int constructor = 0xfeed5769;

        public TLRPC.InputWallPaper wallpaper;
        public TLRPC.TL_wallPaperSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            wallpaper.serializeToStream(stream);
            settings.serializeToStream(stream);
        }
    }

    public static class resetWallPapers extends TLObject {
        public static final int constructor = 0xbb3b9804;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getAutoDownloadSettings extends TLObject {
        public static final int constructor = 0x56da0b3f;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return autoDownloadSettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class saveAutoDownloadSettings extends TLObject {
        public static final int constructor = 0x76f36233;

        public int flags;
        public boolean low;
        public boolean high;
        public TLRPC.TL_autoDownloadSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = low ? (flags | 1) : (flags &~ 1);
            flags = high ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            settings.serializeToStream(stream);
        }
    }

    public static class uploadTheme extends TLObject {
        public static final int constructor = 0x1c3db333;

        public int flags;
        public TLRPC.InputFile file;
        public TLRPC.InputFile thumb;
        public String file_name;
        public String mime_type;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Document.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            file.serializeToStream(stream);
            if ((flags & 1) != 0) {
                thumb.serializeToStream(stream);
            }
            stream.writeString(file_name);
            stream.writeString(mime_type);
        }
    }

    public static class createTheme extends TLObject {
        public static final int constructor = 0x8432c21f;

        public int flags;
        public String slug;
        public String title;
        public TLRPC.InputDocument document;
        public TLRPC.TL_inputThemeSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Theme.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(slug);
            stream.writeString(title);
            if ((flags & 4) != 0) {
                document.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                settings.serializeToStream(stream);
            }
        }
    }

    public static class updateTheme extends TLObject {
        public static final int constructor = 0x5cb367d5;

        public int flags;
        public String format;
        public TLRPC.InputTheme theme;
        public String slug;
        public String title;
        public TLRPC.InputDocument document;
        public TLRPC.TL_inputThemeSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Theme.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(format);
            theme.serializeToStream(stream);
            if ((flags & 1) != 0) {
                stream.writeString(slug);
            }
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
            if ((flags & 4) != 0) {
                document.serializeToStream(stream);
            }
            if ((flags & 8) != 0) {
                settings.serializeToStream(stream);
            }
        }
    }

    public static class saveTheme extends TLObject {
        public static final int constructor = 0xf257106c;

        public TLRPC.InputTheme theme;
        public boolean unsave;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            theme.serializeToStream(stream);
            stream.writeBool(unsave);
        }
    }

    public static class installTheme extends TLObject {
        public static final int constructor = 0x7ae43737;

        public int flags;
        public boolean dark;
        public String format;
        public TLRPC.InputTheme theme;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = dark ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                stream.writeString(format);
            }
            if ((flags & 2) != 0) {
                theme.serializeToStream(stream);
            }
        }
    }

    public static class getTheme extends TLObject {
        public static final int constructor = 0x8d9d742b;

        public String format;
        public TLRPC.InputTheme theme;
        public long document_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Theme.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(format);
            theme.serializeToStream(stream);
            stream.writeInt64(document_id);
        }
    }

    public static class getThemes extends TLObject {
        public static final int constructor = 0x7206e458;

        public String format;
        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Themes.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(format);
            stream.writeInt64(hash);
        }
    }

    public static class updateEmojiStatus extends TLObject {
        public static final int constructor = 0xfbd3de6b;

        public TLRPC.EmojiStatus emoji_status;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            emoji_status.serializeToStream(stream);
        }
    }

    public static class getDefaultBackgroundEmojis extends TLObject {
        public static final int constructor = 0xa60ab9ce;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.EmojiList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getChannelDefaultEmojiStatuses extends TLObject {
        public static final int constructor = 0x7727a7d5;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return EmojiStatuses.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getDefaultEmojiStatuses extends TLObject {
        public static final int constructor = 0xd6753386;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return EmojiStatuses.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getRecentEmojiStatuses extends TLObject {
        public static final int constructor = 0xf578105;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return EmojiStatuses.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class clearRecentEmojiStatuses extends TLObject {
        public static final int constructor = 0x18201aae;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class updateDeviceLocked extends TLObject {
        public static final int constructor = 0x38df3532;

        public int period;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(period);
        }
    }

    public static class webPagePreview extends TLObject {
        public static final int constructor = 0xb53e8b21;

        public TLRPC.MessageMedia media;
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static webPagePreview TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (webPagePreview.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in webPagePreview", constructor));
                } else {
                    return null;
                }
            }
            webPagePreview result = new webPagePreview();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            media.serializeToStream(stream);
            Vector.serialize(stream, users);
        }
    }

    public static class getWebPagePreview extends TLObject {
        public static final int constructor = 0x570d6f6f;

        public int flags;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return webPagePreview.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(message);
            if ((flags & 8) != 0) {
                Vector.serialize(stream, entities);
            }
        }
    }

    public static class getAuthorizations extends TLObject {
        public static final int constructor = 0xe320c158;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return authorizations.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class resetAuthorization extends TLObject {
        public static final int constructor = 0xdf77f3bc;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getPassword extends TLObject {
        public static final int constructor = 0x548a30f5;


        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_password.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getPasswordSettings extends TLObject {
        public static final int constructor = 0x9cd4eaf9;

        public TLRPC.InputCheckPasswordSRP password;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return passwordSettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            password.serializeToStream(stream);
        }
    }

    public static class updatePasswordSettings extends TLObject {
        public static final int constructor = 0xa59b102f;

        public TLRPC.InputCheckPasswordSRP password;
        public passwordInputSettings new_settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            password.serializeToStream(stream);
            new_settings.serializeToStream(stream);
        }
    }

    public static class sendConfirmPhoneCode extends TLObject {
        public static final int constructor = 0x1b3faa88;

        public String hash;
        public TLRPC.TL_codeSettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.TL_auth_sentCode.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(hash);
            settings.serializeToStream(stream);
        }
    }

    public static class confirmPhone extends TLObject {
        public static final int constructor = 0x5f2178c3;

        public String phone_code_hash;
        public String phone_code;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone_code_hash);
            stream.writeString(phone_code);
        }
    }

    public static class getTmpPassword extends TLObject {
        public static final int constructor = 0x449e0b51;

        public TLRPC.InputCheckPasswordSRP password;
        public int period;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return tmpPassword.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            password.serializeToStream(stream);
            stream.writeInt32(period);
        }
    }

    public static class SavedRingtones extends TLObject {

        public static SavedRingtones TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            SavedRingtones result = null;
            switch (constructor) {
                case 0xfbf6e8b1:
                    result = new TL_savedRingtonesNotModified();
                    break;
                case 0xc1e92cc5:
                    result = new TL_savedRingtones();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_SavedRingtones", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_savedRingtonesNotModified extends SavedRingtones {
        public static final int constructor = 0xfbf6e8b1;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_savedRingtones extends SavedRingtones {
        public static final int constructor = 0xc1e92cc5;

        public long hash;
        public ArrayList<TLRPC.Document> ringtones = new ArrayList<>();

        public void readParams(InputSerializedData stream, boolean exception) {
            hash = stream.readInt64(exception);
            ringtones = Vector.deserialize(stream, TLRPC.Document::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
            Vector.serialize(stream, ringtones);
        }
    }

    public static class uploadRingtone extends TLObject {
        public static final int constructor = 0x831a83a2;

        public TLRPC.InputFile file;
        public String file_name;
        public String mime_type;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Document.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            file.serializeToStream(stream);
            stream.writeString(file_name);
            stream.writeString(mime_type);
        }
    }

    public static class getSavedRingtones extends TLObject {
        public static final int constructor = 0xe1902288;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return SavedRingtones.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class saveRingtone extends TLObject {
        public static final int constructor = 0x3dea5b03;

        public TLRPC.InputDocument id;
        public boolean unsave;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return SavedRingtone.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            id.serializeToStream(stream);
            stream.writeBool(unsave);
        }
    }

    public static class reorderUsernames extends TLObject {
        public static final int constructor = 0xef500eab;

        public ArrayList<String> order = new ArrayList<>();

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serializeString(stream, order);
        }
    }

    public static class toggleUsername extends TLObject {
        public static final int constructor = 0x58d6b376;

        public String username;
        public boolean active;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(username);
            stream.writeBool(active);
        }
    }

    public static class SavedRingtone extends TLObject {

        public static SavedRingtone TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            SavedRingtone result = null;
            switch (constructor) {
                case 0x1f307eb7:
                    result = new TL_savedRingtoneConverted();
                    break;
                case 0xb7263f6d:
                    result = new TL_savedRingtone();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in account_SavedRingtone", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_savedRingtoneConverted extends SavedRingtone {
        public static final int constructor = 0x1f307eb7;

        public TLRPC.Document document;

        public void readParams(InputSerializedData stream, boolean exception) {
            document = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            document.serializeToStream(stream);
        }
    }

    public static class TL_savedRingtone extends SavedRingtone {
        public static final int constructor = 0xb7263f6d;


        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class getDefaultProfilePhotoEmojis extends TLObject {
        public static final int constructor = 0xe2750328;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.EmojiList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getDefaultGroupPhotoEmojis extends TLObject {
        public static final int constructor = 0x915860ae;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.EmojiList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class getChannelRestrictedStatusEmojis extends TLObject {
        public static final int constructor = 0x35a9e0d5;

        public long hash;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.EmojiList.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(hash);
        }
    }

    public static class updateColor extends TLObject {
        public static final int constructor = 0x7cefa15d;

        public int flags;
        public boolean for_profile;
        public int color;
        public long background_emoji_id;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = for_profile ? (flags | 2) : (flags &~ 2);
            stream.writeInt32(flags);
            if ((flags & 4) != 0) {
                stream.writeInt32(color);
            }
            if ((flags & 1) != 0) {
                stream.writeInt64(background_emoji_id);
            }
        }
    }

    public static class TL_businessWeeklyOpen extends TLObject {
        public static final int constructor = 0x120b1ab9;

        public int start_minute;
        public int end_minute;

        public static TL_businessWeeklyOpen TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessWeeklyOpen.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessWeeklyOpen", constructor));
                }
                return null;
            }
            TL_businessWeeklyOpen result = new TL_businessWeeklyOpen();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            start_minute = stream.readInt32(exception);
            end_minute = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(start_minute);
            stream.writeInt32(end_minute);
        }
    }

    public static class TL_businessWorkHours extends TLObject {
        public static final int constructor = 0x8c92b098;

        public int flags;
        public boolean open_now;
        public String timezone_id;
        public ArrayList<TL_businessWeeklyOpen> weekly_open = new ArrayList<>();

        public static TL_businessWorkHours TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessWorkHours.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessWorkHours", constructor));
                }
                return null;
            }
            TL_businessWorkHours result = new TL_businessWorkHours();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            open_now = (flags & 1) != 0;
            timezone_id = stream.readString(exception);
            weekly_open = Vector.deserialize(stream, TL_businessWeeklyOpen::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = open_now ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            stream.writeString(timezone_id);
            Vector.serialize(stream, weekly_open);
        }
    }

    public static class updateBusinessWorkHours extends TLObject {
        public static final int constructor = 0x4b00e066;

        public int flags;
        public TL_businessWorkHours business_work_hours;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                business_work_hours.serializeToStream(stream);
            }
        }
    }

    public static class updateBusinessLocation extends TLObject {
        public static final int constructor = 0x9e6b131a;

        public int flags;
        public TLRPC.InputGeoPoint geo_point;
        public String address;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 2) != 0) {
                geo_point.serializeToStream(stream);
            }
            if ((flags & 1) != 0) {
                stream.writeString(address);
            }
        }
    }

    public static class BusinessAwayMessageSchedule extends TLObject {
        public static BusinessAwayMessageSchedule TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            BusinessAwayMessageSchedule result = null;
            switch (constructor) {
                case TL_businessAwayMessageScheduleAlways.constructor:
                    result = new TL_businessAwayMessageScheduleAlways();
                    break;
                case TL_businessAwayMessageScheduleOutsideWorkHours.constructor:
                    result = new TL_businessAwayMessageScheduleOutsideWorkHours();
                    break;
                case TL_businessAwayMessageScheduleCustom.constructor:
                    result = new TL_businessAwayMessageScheduleCustom();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in BusinessAwayMessageSchedule", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_businessAwayMessageScheduleAlways extends BusinessAwayMessageSchedule {
        public static final int constructor = 0xc9b9e2b9;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_businessAwayMessageScheduleOutsideWorkHours extends BusinessAwayMessageSchedule {
        public static final int constructor = 0xc3f2f501;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_businessAwayMessageScheduleCustom extends BusinessAwayMessageSchedule {
        public static final int constructor = 0xcc4d9ecc;

        public int start_date;
        public int end_date;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            start_date = stream.readInt32(exception);
            end_date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(start_date);
            stream.writeInt32(end_date);
        }
    }

    public static class TL_inputBusinessGreetingMessage extends TLObject {
        public static final int constructor = 0x194cb3b;

        public int shortcut_id;
        public TL_inputBusinessRecipients recipients;
        public int no_activity_days;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            shortcut_id = stream.readInt32(exception);
            recipients = TL_inputBusinessRecipients.TLdeserialize(stream, stream.readInt32(exception), exception);
            no_activity_days = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(shortcut_id);
            recipients.serializeToStream(stream);
            stream.writeInt32(no_activity_days);
        }
    }

    public static class TL_businessGreetingMessage extends TLObject {
        public static final int constructor = 0xe519abab;

        public int shortcut_id;
        public TL_businessRecipients recipients;
        public int no_activity_days;

        public static TL_businessGreetingMessage TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessGreetingMessage.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessGreetingMessage", constructor));
                }
                return null;
            }
            TL_businessGreetingMessage result = new TL_businessGreetingMessage();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            shortcut_id = stream.readInt32(exception);
            recipients = TL_businessRecipients.TLdeserialize(stream, stream.readInt32(exception), exception);
            no_activity_days = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(shortcut_id);
            recipients.serializeToStream(stream);
            stream.writeInt32(no_activity_days);
        }
    }

    public static class TL_inputBusinessAwayMessage extends TLObject {
        public static final int constructor = 0x832175e0;

        public int flags;
        public boolean offline_only;
        public int shortcut_id;
        public BusinessAwayMessageSchedule schedule;
        public TL_inputBusinessRecipients recipients;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            offline_only = (flags & 1) != 0;
            shortcut_id = stream.readInt32(exception);
            schedule = BusinessAwayMessageSchedule.TLdeserialize(stream, stream.readInt32(exception), exception);
            recipients = TL_inputBusinessRecipients.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = offline_only ? (flags | 1) : (flags & 1);
            stream.writeInt32(flags);
            stream.writeInt32(shortcut_id);
            schedule.serializeToStream(stream);
            recipients.serializeToStream(stream);
        }
    }

    public static class TL_businessAwayMessage extends TLObject {
        public static final int constructor = 0xef156a5c;

        public int flags;
        public boolean offline_only;
        public int shortcut_id;
        public BusinessAwayMessageSchedule schedule;
        public TL_businessRecipients recipients;

        public static TL_businessAwayMessage TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessAwayMessage.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessAwayMessage", constructor));
                }
                return null;
            }
            TL_businessAwayMessage result = new TL_businessAwayMessage();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            offline_only = (flags & 1) != 0;
            shortcut_id = stream.readInt32(exception);
            schedule = BusinessAwayMessageSchedule.TLdeserialize(stream, stream.readInt32(exception), exception);
            recipients = TL_businessRecipients.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = offline_only ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            stream.writeInt32(shortcut_id);
            schedule.serializeToStream(stream);
            recipients.serializeToStream(stream);
        }
    }

    public static class updateBusinessAwayMessage extends TLObject {
        public static final int constructor = 0xa26a7fa5;

        public int flags;
        public TL_inputBusinessAwayMessage message;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                message.serializeToStream(stream);
            }
        }
    }

    public static class updateBusinessGreetingMessage extends TLObject {
        public static final int constructor = 0x66cdafc4;

        public int flags;
        public TL_inputBusinessGreetingMessage message;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                message.serializeToStream(stream);
            }
        }
    }

    public static class TL_inputBusinessBotRecipients extends TLObject {
        public static final int constructor = 0xc4e5921e;

        public int flags;
        public boolean existing_chats;
        public boolean new_chats;
        public boolean contacts;
        public boolean non_contacts;
        public boolean exclude_selected;
        public ArrayList<TLRPC.InputUser> users = new ArrayList<>();
        public ArrayList<TLRPC.InputUser> exclude_users = new ArrayList<>();

        public static TL_inputBusinessRecipients TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_inputBusinessRecipients.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputBusinessRecipients", constructor));
                }
                return null;
            }
            TL_inputBusinessRecipients result = new TL_inputBusinessRecipients();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            existing_chats = (flags & 1) != 0;
            new_chats = (flags & 2) != 0;
            contacts = (flags & 4) != 0;
            non_contacts = (flags & 8) != 0;
            exclude_selected = (flags & 32) != 0;
            if ((flags & 16) != 0) {
                users = Vector.deserialize(stream, TLRPC.InputUser::TLdeserialize, exception);
            }
            if ((flags & 64) != 0) {
                exclude_users = Vector.deserialize(stream, TLRPC.InputUser::TLdeserialize, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = existing_chats ? (flags | 1) : (flags &~ 1);
            flags = new_chats ? (flags | 2) : (flags &~ 2);
            flags = contacts ? (flags | 4) : (flags &~ 4);
            flags = non_contacts ? (flags | 8) : (flags &~ 8);
            flags = exclude_selected ? (flags | 32) : (flags &~ 32);
            stream.writeInt32(flags);
            if ((flags & 16) != 0) {
                Vector.serialize(stream, users);
            }
            if ((flags & 64) != 0) {
                Vector.serialize(stream, exclude_users);
            }
        }
    }

    public static class TL_businessBotRecipients extends TLObject {
        public static final int constructor = 0xb88cf373;

        public int flags;
        public boolean existing_chats;
        public boolean new_chats;
        public boolean contacts;
        public boolean non_contacts;
        public boolean exclude_selected;
        public ArrayList<Long> users = new ArrayList<>();
        public ArrayList<Long> exclude_users = new ArrayList<>();

        public static TL_businessBotRecipients TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessBotRecipients.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessBotRecipients", constructor));
                }
                return null;
            }
            TL_businessBotRecipients result = new TL_businessBotRecipients();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            existing_chats = (flags & 1) != 0;
            new_chats = (flags & 2) != 0;
            contacts = (flags & 4) != 0;
            non_contacts = (flags & 8) != 0;
            exclude_selected = (flags & 32) != 0;
            if ((flags & 16) != 0) {
                users = Vector.deserializeLong(stream, exception);
            }
            if ((flags & 64) != 0) {
                exclude_users = Vector.deserializeLong(stream, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = existing_chats ? (flags | 1) : (flags &~ 1);
            flags = new_chats ? (flags | 2) : (flags &~ 2);
            flags = contacts ? (flags | 4) : (flags &~ 4);
            flags = non_contacts ? (flags | 8) : (flags &~ 8);
            flags = exclude_selected ? (flags | 32) : (flags &~ 32);
            stream.writeInt32(flags);
            if ((flags & 16) != 0) {
                Vector.serializeLong(stream, users);
            }
            if ((flags & 64) != 0) {
                Vector.serializeLong(stream, exclude_users);
            }
        }
    }

    public static class TL_inputBusinessRecipients extends TLObject {
        public static final int constructor = 0x6f8b32aa;

        public int flags;
        public boolean existing_chats;
        public boolean new_chats;
        public boolean contacts;
        public boolean non_contacts;
        public boolean exclude_selected;
        public ArrayList<TLRPC.InputUser> users = new ArrayList<>();

        public static TL_inputBusinessRecipients TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_inputBusinessRecipients.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputBusinessRecipients", constructor));
                }
                return null;
            }
            TL_inputBusinessRecipients result = new TL_inputBusinessRecipients();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            existing_chats = (flags & 1) != 0;
            new_chats = (flags & 2) != 0;
            contacts = (flags & 4) != 0;
            non_contacts = (flags & 8) != 0;
            exclude_selected = (flags & 32) != 0;
            if ((flags & 16) != 0) {
                users = Vector.deserialize(stream, TLRPC.InputUser::TLdeserialize, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = existing_chats ? (flags | 1) : (flags &~ 1);
            flags = new_chats ? (flags | 2) : (flags &~ 2);
            flags = contacts ? (flags | 4) : (flags &~ 4);
            flags = non_contacts ? (flags | 8) : (flags &~ 8);
            flags = exclude_selected ? (flags | 32) : (flags &~ 32);
            stream.writeInt32(flags);
            if ((flags & 16) != 0) {
                Vector.serialize(stream, users);
            }
        }
    }

    public static class TL_businessRecipients extends TLObject {
        public static final int constructor = 0x21108ff7;

        public int flags;
        public boolean existing_chats;
        public boolean new_chats;
        public boolean contacts;
        public boolean non_contacts;
        public boolean exclude_selected;
        public ArrayList<Long> users = new ArrayList<>();

        public static TL_businessRecipients TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessRecipients.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessRecipients", constructor));
                }
                return null;
            }
            TL_businessRecipients result = new TL_businessRecipients();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            existing_chats = (flags & 1) != 0;
            new_chats = (flags & 2) != 0;
            contacts = (flags & 4) != 0;
            non_contacts = (flags & 8) != 0;
            exclude_selected = (flags & 32) != 0;
            if ((flags & 16) != 0) {
                users = Vector.deserializeLong(stream, exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = existing_chats ? (flags | 1) : (flags &~ 1);
            flags = new_chats ? (flags | 2) : (flags &~ 2);
            flags = contacts ? (flags | 4) : (flags &~ 4);
            flags = non_contacts ? (flags | 8) : (flags &~ 8);
            flags = exclude_selected ? (flags | 32) : (flags &~ 32);
            stream.writeInt32(flags);
            if ((flags & 16) != 0) {
                Vector.serializeLong(stream, users);
            }
        }
    }

    public static class TL_businessBotRights extends TLObject {
        public static final int constructor = 0xa0624cf7;

        public int flags;
        public boolean reply;
        public boolean read_messages;
        public boolean delete_sent_messages;
        public boolean delete_received_messages;
        public boolean edit_name;
        public boolean edit_bio;
        public boolean edit_profile_photo;
        public boolean edit_username;
        public boolean view_gifts;
        public boolean sell_gifts;
        public boolean change_gift_settings;
        public boolean transfer_and_upgrade_gifts;
        public boolean transfer_stars;
        public boolean manage_stories;

        public static TL_businessBotRights TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessBotRights.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessBotRights", constructor));
                }
                return null;
            }
            TL_businessBotRights result = new TL_businessBotRights();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            reply = (flags & 1) != 0;
            read_messages = (flags & 2) != 0;
            delete_sent_messages = (flags & 4) != 0;
            delete_received_messages = (flags & 8) != 0;
            edit_name = (flags & 16) != 0;
            edit_bio = (flags & 32) != 0;
            edit_profile_photo = (flags & 64) != 0;
            edit_username = (flags & 128) != 0;
            view_gifts = (flags & 256) != 0;
            sell_gifts = (flags & 512) != 0;
            change_gift_settings = (flags & 1024) != 0;
            transfer_and_upgrade_gifts = (flags & 2048) != 0;
            transfer_stars = (flags & 4096) != 0;
            manage_stories = (flags & 8192) != 0;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = reply ? flags | 1 : flags &~ 1;
            flags = read_messages ? flags | 2 : flags &~ 2;
            flags = delete_sent_messages ? flags | 4 : flags &~ 4;
            flags = delete_received_messages ? flags | 8 : flags &~ 8;
            flags = edit_name ? flags | 16 : flags &~ 16;
            flags = edit_bio ? flags | 32 : flags &~ 32;
            flags = edit_profile_photo ? flags | 64 : flags &~ 64;
            flags = edit_username ? flags | 128 : flags &~ 128;
            flags = view_gifts ? flags | 256 : flags &~ 256;
            flags = sell_gifts ? flags | 512 : flags &~ 512;
            flags = change_gift_settings ? flags | 1024 : flags &~ 1024;
            flags = transfer_and_upgrade_gifts ? flags | 2048 : flags &~ 2048;
            flags = transfer_stars ? flags | 4096 : flags &~ 4096;
            flags = manage_stories ? flags | 8192 : flags &~ 8192;
            stream.writeInt32(flags);
        }

        public static TL_businessBotRights all() {
            final TL_businessBotRights rights = new TL_businessBotRights();
            rights.reply = true;
            rights.read_messages = true;
            rights.delete_sent_messages = true;
            rights.delete_received_messages = true;
            rights.edit_name = true;
            rights.edit_bio = true;
            rights.edit_profile_photo = true;
            rights.edit_username = true;
            rights.view_gifts = true;
            rights.sell_gifts = true;
            rights.change_gift_settings = true;
            rights.transfer_and_upgrade_gifts = true;
            rights.transfer_stars = true;
            rights.manage_stories = true;
            return rights;
        }

        public static TL_businessBotRights clone(TL_businessBotRights a) {
            final TL_businessBotRights rights = new TL_businessBotRights();
            rights.reply = a.reply;
            rights.read_messages = a.read_messages;
            rights.delete_sent_messages = a.delete_sent_messages;
            rights.delete_received_messages = a.delete_received_messages;
            rights.edit_name = a.edit_name;
            rights.edit_bio = a.edit_bio;
            rights.edit_profile_photo = a.edit_profile_photo;
            rights.edit_username = a.edit_username;
            rights.view_gifts = a.view_gifts;
            rights.sell_gifts = a.sell_gifts;
            rights.change_gift_settings = a.change_gift_settings;
            rights.transfer_and_upgrade_gifts = a.transfer_and_upgrade_gifts;
            rights.transfer_stars = a.transfer_stars;
            rights.manage_stories = a.manage_stories;
            return rights;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof TL_businessBotRights))
                return false;
            final TL_businessBotRights b = (TL_businessBotRights) obj;
            return (
                reply == b.reply &&
                read_messages == b.read_messages &&
                delete_sent_messages == b.delete_sent_messages &&
                delete_received_messages == b.delete_received_messages &&
                edit_name == b.edit_name &&
                edit_bio == b.edit_bio &&
                edit_profile_photo == b.edit_profile_photo &&
                edit_username == b.edit_username &&
                view_gifts == b.view_gifts &&
                sell_gifts == b.sell_gifts &&
                change_gift_settings == b.change_gift_settings &&
                transfer_and_upgrade_gifts == b.transfer_and_upgrade_gifts &&
                transfer_stars == b.transfer_stars &&
                manage_stories == b.manage_stories
            );
        }
    }

    public static class TL_connectedBot extends TLObject {
        public static final int constructor = 0xcd64636c;

        public int flags;
        public long bot_id;
        public TL_businessBotRecipients recipients;
        public TL_businessBotRights rights;

        public static TL_connectedBot TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_connectedBot.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_connectedBot", constructor));
                }
                return null;
            }
            TL_connectedBot result = new TL_connectedBot();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bot_id = stream.readInt64(exception);
            recipients = TL_businessBotRecipients.TLdeserialize(stream, stream.readInt32(exception), exception);
            rights = TL_businessBotRights.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt64(bot_id);
            recipients.serializeToStream(stream);
            rights.serializeToStream(stream);
        }
    }

    public static class connectedBots extends TLObject {
        public static final int constructor = 0x17d7f87b;

        public ArrayList<TL_connectedBot> connected_bots = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static connectedBots TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != connectedBots.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_connectedBots", constructor));
                }
                return null;
            }
            connectedBots result = new connectedBots();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            connected_bots = Vector.deserialize(stream, TL_connectedBot::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, connected_bots);
            Vector.serialize(stream, users);
        }
    }

    public static class updateConnectedBot extends TLObject {
        public static final int constructor = 0x66a08c7e;

        public int flags;
        public boolean deleted;
        public TL_businessBotRights rights;
        public TLRPC.InputUser bot;
        public TL_inputBusinessBotRecipients recipients;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Updates.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = deleted ? (flags | 2) : (flags &~ 2);
            flags = rights != null ? (flags | 1) : (flags &~ 1);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                rights.serializeToStream(stream);
            }
            bot.serializeToStream(stream);
            recipients.serializeToStream(stream);
        }
    }

    public static class getConnectedBots extends TLObject {
        public static final int constructor = 0x4ea4c80f;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return connectedBots.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class toggleConnectedBotPaused extends TLObject {
        public static final int constructor = 0x646E1097;

        public TLRPC.InputPeer peer;
        public boolean paused;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
            stream.writeBool(paused);
        }
    }

    public static class disablePeerConnectedBot extends TLObject {
        public static final int constructor = 0x5e437ed9;

        public TLRPC.InputPeer peer;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            peer.serializeToStream(stream);
        }
    }

    public static class TL_birthday extends TLObject {
        public static final int constructor = 0x6c8e1e06;

        public int flags;
        public int day;
        public int month;
        public int year;

        public static TL_birthday TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_birthday.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_birthday", constructor));
                }
                return null;
            }
            TL_birthday result = new TL_birthday();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            day = stream.readInt32(exception);
            month = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                year = stream.readInt32(exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(day);
            stream.writeInt32(month);
            if ((flags & 1) != 0) {
                stream.writeInt32(year);
            }
        }
    }

    public static class TL_contactBirthday extends TLObject {
        public static final int constructor = 0x1d998733;

        public long contact_id;
        public TL_birthday birthday;

        public static TL_contactBirthday TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_contactBirthday.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contactBirthday", constructor));
                }
                return null;
            }
            TL_contactBirthday result = new TL_contactBirthday();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            contact_id = stream.readInt64(exception);
            birthday = TL_birthday.TLdeserialize(stream, stream.readInt32(exception), exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(contact_id);
            birthday.serializeToStream(stream);
        }
    }

    public static class contactBirthdays extends TLObject {
        public static final int constructor = 0x114ff30d;

        public ArrayList<TL_contactBirthday> contacts = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static contactBirthdays TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != contactBirthdays.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_contacts_contactBirthdays", constructor));
                }
                return null;
            }
            contactBirthdays result = new contactBirthdays();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            contacts = Vector.deserialize(stream, TL_contactBirthday::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, contacts);
            Vector.serialize(stream, users);
        }
    }

    public static class updateBirthday extends TLObject {
        public static final int constructor = 0xcc6e0c11;

        public int flags;
        public TL_birthday birthday;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                birthday.serializeToStream(stream);
            }
        }
    }

    public static class getBirthdays extends TLObject {
        public static final int constructor = 0xdaeda864;

        public int flags;
        public TL_birthday birthday;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return contactBirthdays.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inputBusinessChatLink extends TLObject {
        public static final int constructor = 0x11679fa7;

        public int flags;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public String title;

        public static TL_inputBusinessChatLink TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_inputBusinessChatLink.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputBusinessChatLink", constructor));
                } else {
                    return null;
                }
            }
            TL_inputBusinessChatLink result = new TL_inputBusinessChatLink();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            message = stream.readString(exception);
            if ((flags & 1) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if ((flags & 2) != 0) {
                title = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(message);
            if ((flags & 1) != 0) {
                Vector.serialize(stream, entities);
            }
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
        }
    }

    public static class TL_businessChatLink extends TLObject {
        public static final int constructor = 0xb4ae666f;

        public int flags;
        public String link;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public String title;
        public int views;

        public static TL_businessChatLink TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_businessChatLink.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessChatLink", constructor));
                } else {
                    return null;
                }
            }
            TL_businessChatLink result = new TL_businessChatLink();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            link = stream.readString(exception);
            message = stream.readString(exception);
            if ((flags & 1) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if ((flags & 2) != 0) {
                title = stream.readString(exception);
            }
            views = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(link);
            stream.writeString(message);
            if ((flags & 1) != 0) {
                Vector.serialize(stream, entities);
            }
            if ((flags & 2) != 0) {
                stream.writeString(title);
            }
            stream.writeInt32(views);
        }
    }

    public static class businessChatLinks extends TLObject {
        public static final int constructor = 0xec43a2d1;

        public ArrayList<TL_businessChatLink> links = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static businessChatLinks TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (businessChatLinks.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_businessChatLinks", constructor));
                } else {
                    return null;
                }
            }
            businessChatLinks result = new businessChatLinks();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            links = Vector.deserialize(stream, TL_businessChatLink::TLdeserialize, exception);
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, links);
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class resolvedBusinessChatLinks extends TLObject {
        public static final int constructor = 0x9a23af21;

        public int flags;
        public TLRPC.Peer peer;
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        public ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        public ArrayList<TLRPC.User> users = new ArrayList<>();

        public static resolvedBusinessChatLinks TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (resolvedBusinessChatLinks.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_businessChatLinks", constructor));
                } else {
                    return null;
                }
            }
            resolvedBusinessChatLinks result = new resolvedBusinessChatLinks();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            peer = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            message = stream.readString(exception);
            if ((flags & 1) != 0) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            chats = Vector.deserialize(stream, TLRPC.Chat::TLdeserialize, exception);
            users = Vector.deserialize(stream, TLRPC.User::TLdeserialize, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            peer.serializeToStream(stream);
            stream.writeString(message);
            if ((flags & 1) != 0) {
                Vector.serialize(stream, entities);
            }
            Vector.serialize(stream, chats);
            Vector.serialize(stream, users);
        }
    }

    public static class createBusinessChatLink extends TLObject {
        public static final int constructor = 0x8851e68e;

        public TL_inputBusinessChatLink link;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_businessChatLink.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            link.serializeToStream(stream);
        }
    }

    public static class editBusinessChatLink extends TLObject {
        public static final int constructor = 0x8c3410af;

        public String slug;
        public TL_inputBusinessChatLink link;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_businessChatLink.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
            link.serializeToStream(stream);
        }
    }

    public static class deleteBusinessChatLink extends TLObject {
        public static final int constructor = 0x60073674;

        public String slug;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class getBusinessChatLinks extends TLObject {
        public static final int constructor = 0x6f70dde1;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return businessChatLinks.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class resolveBusinessChatLink extends TLObject {
        public static final int constructor = 0x5492e5ee;

        public String slug;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return resolvedBusinessChatLinks.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(slug);
        }
    }

    public static class toggleSponsoredMessages extends TLObject {
        public static final int constructor = 0xb9d9a38d;

        public boolean enabled;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeBool(enabled);
        }
    }

    public static class TL_businessIntro extends TLObject {
        public static final int constructor = 0x5a0a066d;

        public int flags;
        public String title;
        public String description;
        public TLRPC.Document sticker;

        public static TL_businessIntro TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_businessIntro.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_businessIntro", constructor));
                }
                return null;
            }
            TL_businessIntro result = new TL_businessIntro();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            title = stream.readString(exception);
            description = stream.readString(exception);
            if ((flags & 1) != 0) {
                sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(title);
            stream.writeString(description);
            if ((flags & 1) != 0) {
                sticker.serializeToStream(stream);
            }
        }
    }

    public static class TL_inputBusinessIntro extends TLObject {
        public static final int constructor = 0x9c469cd;

        public int flags;
        public String title;
        public String description;
        public TLRPC.InputDocument sticker;

        public static TL_inputBusinessIntro TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_inputBusinessIntro.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_inputBusinessIntro", constructor));
                }
                return null;
            }
            TL_inputBusinessIntro result = new TL_inputBusinessIntro();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            title = stream.readString(exception);
            description = stream.readString(exception);
            if ((flags & 1) != 0) {
                sticker = TLRPC.InputDocument.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeString(title);
            stream.writeString(description);
            if ((flags & 1) != 0) {
                sticker.serializeToStream(stream);
            }
        }
    }

    public static class updateBusinessIntro extends TLObject {
        public static final int constructor = 0xa614d034;

        public int flags;
        public TL_inputBusinessIntro intro;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                intro.serializeToStream(stream);
            }
        }
    }

    public static class updatePersonalChannel extends TLObject {
        public static final int constructor = 0xd94305e0;

        public TLRPC.InputChannel channel;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            channel.serializeToStream(stream);
        }
    }

    public static class ReactionNotificationsFrom extends TLObject {
        public static ReactionNotificationsFrom TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            ReactionNotificationsFrom result = null;
            switch (constructor) {
                case TL_account.TL_reactionNotificationsFromContacts.constructor:
                    result = new TL_account.TL_reactionNotificationsFromContacts();
                    break;
                case TL_account.TL_reactionNotificationsFromAll.constructor:
                    result = new TL_account.TL_reactionNotificationsFromAll();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in ReactionNotificationsFrom", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class TL_reactionNotificationsFromContacts extends ReactionNotificationsFrom {
        public static final int constructor = 0xbac3a61a;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_reactionNotificationsFromAll extends ReactionNotificationsFrom {
        public static final int constructor = 0x4b9e22a0;

        public void readParams(InputSerializedData stream, boolean exception) {}

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_reactionsNotifySettings extends TLObject {
        public static final int constructor = 0x56e34970;

        public int flags;
        public ReactionNotificationsFrom messages_notify_from;
        public ReactionNotificationsFrom stories_notify_from;
        public TLRPC.NotificationSound sound;
        public boolean show_previews;

        public static TL_reactionsNotifySettings TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_reactionsNotifySettings.constructor != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_reactionsNotifySettings", constructor));
                } else {
                    return null;
                }
            }
            TL_reactionsNotifySettings result = new TL_reactionsNotifySettings();
            result.readParams(stream, exception);
            return result;
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if ((flags & 1) != 0) {
                messages_notify_from = ReactionNotificationsFrom.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if ((flags & 2) != 0) {
                stories_notify_from = ReactionNotificationsFrom.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            sound = TLRPC.NotificationSound.TLdeserialize(stream, stream.readInt32(exception), exception);
            show_previews = stream.readBool(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                messages_notify_from.serializeToStream(stream);
            }
            if ((flags & 2) != 0) {
                stories_notify_from.serializeToStream(stream);
            }
            sound.serializeToStream(stream);
            stream.writeBool(show_previews);
        }
    }

    public static class getReactionsNotifySettings extends TLObject {
        public static final int constructor = 0x6dd654c;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_reactionsNotifySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class setReactionsNotifySettings extends TLObject {
        public static final int constructor = 0x316ce548;

        public TL_reactionsNotifySettings settings;

        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_reactionsNotifySettings.TLdeserialize(stream, constructor, exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            settings.serializeToStream(stream);
        }
    }

    public static class paidMessagesRevenue extends TLObject {
        public static final int constructor = 0x1e109708;

        public long stars_amount;

        public static paidMessagesRevenue TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != paidMessagesRevenue.constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in paidMessagesRevenue", constructor));
                }
                return null;
            }
            paidMessagesRevenue result = new paidMessagesRevenue();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            stars_amount = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(stars_amount);
        }
    }

    public static class addNoPaidMessagesException extends TLObject {
        public static final int constructor = 0x6f688aa7;

        public int flags;
        public boolean refund_charged;
        public TLRPC.InputUser user_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = refund_charged ? flags | 1 : flags &~ 1;
            stream.writeInt32(flags);
            user_id.serializeToStream(stream);
        }
    }

    public static class getPaidMessagesRevenue extends TLObject {
        public static final int constructor = 0xf1266f38;

        public TLRPC.InputUser user_id;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return paidMessagesRevenue.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            user_id.serializeToStream(stream);
        }
    }

    public static class RequirementToContact extends TLObject {
        public static RequirementToContact TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            RequirementToContact result = null;
            switch (constructor) {
                case requirementToContactEmpty.constructor:
                    result = new requirementToContactEmpty();
                    break;
                case requirementToContactPremium.constructor:
                    result = new requirementToContactPremium();
                    break;
                case requirementToContactPaidMessages.constructor:
                    result = new requirementToContactPaidMessages();
                    break;
            }
            if (result == null && exception) {
                throw new RuntimeException(String.format("can't parse magic %x in RequirementToContact", constructor));
            }
            if (result != null) {
                result.readParams(stream, exception);
            }
            return result;
        }
    }

    public static class requirementToContactEmpty extends RequirementToContact {
        public static final int constructor = 0x50a9839;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class requirementToContactPremium extends RequirementToContact {
        public static final int constructor = 0xe581e4e9;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class requirementToContactPaidMessages extends RequirementToContact {
        public static final int constructor = 0xb4f67e93;

        public long stars_amount;

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            stars_amount = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(stars_amount);
        }
    }

    public static class getRequirementsToContact extends TLObject {
        public static final int constructor = 0xd89a83a3;

        public ArrayList<TLRPC.InputUser> id = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, RequirementToContact::TLdeserialize);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = Vector.deserialize(stream, TLRPC.InputUser::TLdeserialize, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            Vector.serialize(stream, id);
        }
    }



}
