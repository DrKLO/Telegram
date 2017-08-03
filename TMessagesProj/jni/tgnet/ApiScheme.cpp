/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2016.
 */

#include "ApiScheme.h"
#include "ByteArray.h"
#include "NativeByteBuffer.h"
#include "FileLog.h"

Bool *Bool::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    Bool *result = nullptr;
    switch (constructor) {
        case 0x997275b5:
            result = new TL_boolTrue();
            break;
        case 0xbc799737:
            result = new TL_boolFalse();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in Bool", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_boolTrue::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_boolFalse::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

TL_dcOption *TL_dcOption::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_dcOption::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_dcOption", constructor);
        return nullptr;
    }
    TL_dcOption *result = new TL_dcOption();
    result->readParams(stream, error);
    return result;
}

void TL_dcOption::readParams(NativeByteBuffer *stream, bool &error) {
    flags = stream->readInt32(&error);
    ipv6 = (flags & 1) != 0;
    media_only = (flags & 2) != 0;
    tcpo_only = (flags & 4) != 0;
    cdn = (flags & 8) != 0;
    isStatic = (flags & 16) != 0;
    id = stream->readInt32(&error);
    ip_address = stream->readString(&error);
    port = stream->readInt32(&error);
}

void TL_dcOption::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    flags = ipv6 ? (flags | 1) : (flags &~ 1);
    flags = media_only ? (flags | 2) : (flags &~ 2);
    flags = tcpo_only ? (flags | 4) : (flags &~ 4);
    flags = cdn ? (flags | 8) : (flags &~ 8);
    flags = isStatic ? (flags | 16) : (flags &~ 16);
    stream->writeInt32(flags);
    stream->writeInt32(id);
    stream->writeString(ip_address);
    stream->writeInt32(port);
}

TL_cdnPublicKey *TL_cdnPublicKey::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_cdnPublicKey::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_cdnPublicKey", constructor);
        return nullptr;
    }
    TL_cdnPublicKey *result = new TL_cdnPublicKey();
    result->readParams(stream, error);
    return result;
}

void TL_cdnPublicKey::readParams(NativeByteBuffer *stream, bool &error) {
    dc_id = stream->readInt32(&error);
    public_key = stream->readString(&error);
}

void TL_cdnPublicKey::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(dc_id);
    stream->writeString(public_key);
}

TL_cdnConfig *TL_cdnConfig::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_cdnConfig::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_cdnConfig", constructor);
        return nullptr;
    }
    TL_cdnConfig *result = new TL_cdnConfig();
    result->readParams(stream, error);
    return result;
}

void TL_cdnConfig::readParams(NativeByteBuffer *stream, bool &error) {
    int magic = stream->readInt32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    int count = stream->readInt32(&error);
    for (int a = 0; a < count; a++) {
        TL_cdnPublicKey *object = TL_cdnPublicKey::TLdeserialize(stream, stream->readUint32(&error), error);
        if (object == nullptr) {
            return;
        }
        public_keys.push_back(std::unique_ptr<TL_cdnPublicKey>(object));
    }
}

void TL_cdnConfig::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(0x1cb5c415);
    int count = public_keys.size();
    stream->writeInt32(count);
    for (int a = 0; a < count; a++) {
        public_keys[a]->serializeToStream(stream);
    }
}

bool TL_help_getCdnConfig::isNeedLayer() {
    return true;
}

TLObject *TL_help_getCdnConfig::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_cdnConfig::TLdeserialize(stream, constructor, error);
}

void TL_help_getCdnConfig::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

TL_disabledFeature *TL_disabledFeature::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_disabledFeature::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_disabledFeature", constructor);
        return nullptr;
    }
    TL_disabledFeature *result = new TL_disabledFeature();
    result->readParams(stream, error);
    return result;
}

void TL_disabledFeature::readParams(NativeByteBuffer *stream, bool &error) {
    feature = stream->readString(&error);
    description = stream->readString(&error);
}

void TL_disabledFeature::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeString(feature);
    stream->writeString(description);
}

TL_config *TL_config::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_config::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_config", constructor);
        return nullptr;
    }
    TL_config *result = new TL_config();
    result->readParams(stream, error);
    return result;
}

void TL_config::readParams(NativeByteBuffer *stream, bool &error) {
    flags = stream->readInt32(&error);
    date = stream->readInt32(&error);
    expires = stream->readInt32(&error);
    test_mode = stream->readBool(&error);
    this_dc = stream->readInt32(&error);
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    int32_t count = stream->readInt32(&error);
    for (int32_t a = 0; a < count; a++) {
        TL_dcOption *object = TL_dcOption::TLdeserialize(stream, stream->readUint32(&error), error);
        if (object == nullptr) {
            return;
        }
        dc_options.push_back(std::unique_ptr<TL_dcOption>(object));
    }
    chat_size_max = stream->readInt32(&error);
    megagroup_size_max = stream->readInt32(&error);
    forwarded_count_max = stream->readInt32(&error);
    online_update_period_ms = stream->readInt32(&error);
    offline_blur_timeout_ms = stream->readInt32(&error);
    offline_idle_timeout_ms = stream->readInt32(&error);
    online_cloud_timeout_ms = stream->readInt32(&error);
    notify_cloud_delay_ms = stream->readInt32(&error);
    notify_default_delay_ms = stream->readInt32(&error);
    chat_big_size = stream->readInt32(&error);
    push_chat_period_ms = stream->readInt32(&error);
    push_chat_limit = stream->readInt32(&error);
    saved_gifs_limit = stream->readInt32(&error);
    edit_time_limit = stream->readInt32(&error);
    rating_e_decay = stream->readInt32(&error);
    stickers_recent_limit = stream->readInt32(&error);
    if ((flags & 1) != 0) {
        tmp_sessions = stream->readInt32(&error);
    }
    pinned_dialogs_count_max = stream->readInt32(&error);
    call_receive_timeout_ms = stream->readInt32(&error);
    call_ring_timeout_ms = stream->readInt32(&error);
    call_connect_timeout_ms = stream->readInt32(&error);
    call_packet_timeout_ms = stream->readInt32(&error);
    me_url_prefix = stream->readString(&error);
    if ((flags & 4) != 0) {
        suggested_lang_code = stream->readString(&error);
    }
    if ((flags & 4) != 0) {
        lang_pack_version = stream->readInt32(&error);
    }
    magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    count = stream->readInt32(&error);
    for (int32_t a = 0; a < count; a++) {
        TL_disabledFeature *object = TL_disabledFeature::TLdeserialize(stream, stream->readUint32(&error), error);
        if (object == nullptr) {
            return;
        }
        disabled_features.push_back(std::unique_ptr<TL_disabledFeature>(object));
    }
}

void TL_config::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(flags);
    stream->writeInt32(date);
    stream->writeInt32(expires);
    stream->writeBool(test_mode);
    stream->writeInt32(this_dc);
    stream->writeInt32(0x1cb5c415);
    uint32_t count = (uint32_t) dc_options.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        dc_options[a]->serializeToStream(stream);
    }
    stream->writeInt32(chat_size_max);
    stream->writeInt32(megagroup_size_max);
    stream->writeInt32(forwarded_count_max);
    stream->writeInt32(online_update_period_ms);
    stream->writeInt32(offline_blur_timeout_ms);
    stream->writeInt32(offline_idle_timeout_ms);
    stream->writeInt32(online_cloud_timeout_ms);
    stream->writeInt32(notify_cloud_delay_ms);
    stream->writeInt32(notify_default_delay_ms);
    stream->writeInt32(chat_big_size);
    stream->writeInt32(push_chat_period_ms);
    stream->writeInt32(push_chat_limit);
    stream->writeInt32(saved_gifs_limit);
    stream->writeInt32(edit_time_limit);
    stream->writeInt32(rating_e_decay);
    stream->writeInt32(stickers_recent_limit);
    if ((flags & 1) != 0) {
        stream->writeInt32(tmp_sessions);
    }
    stream->writeInt32(pinned_dialogs_count_max);
    stream->writeInt32(call_receive_timeout_ms);
    stream->writeInt32(call_ring_timeout_ms);
    stream->writeInt32(call_connect_timeout_ms);
    stream->writeInt32(call_packet_timeout_ms);
    stream->writeString(me_url_prefix);
    if ((flags & 4) != 0) {
        stream->writeString(suggested_lang_code);
    }
    if ((flags & 4) != 0) {
        stream->writeInt32(lang_pack_version);
    }
    stream->writeInt32(0x1cb5c415);
    count = (uint32_t) disabled_features.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        disabled_features[a]->serializeToStream(stream);
    }
}

TLObject *TL_help_getConfig::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_config::TLdeserialize(stream, constructor, error);
}

void TL_help_getConfig::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

bool TL_help_getConfig::isNeedLayer() {
    return true;
}

bool TL_account_registerDevice::isNeedLayer() {
    return true;
}

TLObject *TL_account_registerDevice::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return Bool::TLdeserialize(stream, constructor, error);
}

void TL_account_registerDevice::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(token_type);
    stream->writeString(token);
}

User *User::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    User *result = nullptr;
    switch (constructor) {
        case 0x200250ba:
            result = new TL_userEmpty();
            break;
        case 0x2e13f4c3:
            result = new TL_user();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in User", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_userEmpty::readParams(NativeByteBuffer *stream, bool &error) {
    id = stream->readInt32(&error);
}

void TL_userEmpty::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(id);
}

void TL_user::readParams(NativeByteBuffer *stream, bool &error) {
    flags = stream->readInt32(&error);
    id = stream->readInt32(&error);
    if ((flags & 1) != 0) {
        access_hash = stream->readInt64(&error);
    }
    if ((flags & 2) != 0) {
        first_name = stream->readString(&error);
    }
    if ((flags & 4) != 0) {
        last_name = stream->readString(&error);
    }
    if ((flags & 8) != 0) {
        username = stream->readString(&error);
    }
    if ((flags & 16) != 0) {
        phone = stream->readString(&error);
    }
    if ((flags & 32) != 0) {
        photo = std::unique_ptr<UserProfilePhoto>(UserProfilePhoto::TLdeserialize(stream, stream->readUint32(&error), error));
    }
    if ((flags & 64) != 0) {
        status = std::unique_ptr<UserStatus>(UserStatus::TLdeserialize(stream, stream->readUint32(&error), error));
    }
    if ((flags & 16384) != 0) {
        bot_info_version = stream->readInt32(&error);
    }
    if ((flags & 262144) != 0) {
        restriction_reason = stream->readString(&error);
    }
    if ((flags & 524288) != 0) {
        bot_inline_placeholder = stream->readString(&error);
    }
    if ((flags & 4194304) != 0) {
        lang_code = stream->readString(&error);
    }
}

void TL_user::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(flags);
    stream->writeInt32(id);
    if ((flags & 1) != 0) {
        stream->writeInt64(access_hash);
    }
    if ((flags & 2) != 0) {
        stream->writeString(first_name);
    }
    if ((flags & 4) != 0) {
        stream->writeString(last_name);
    }
    if ((flags & 8) != 0) {
        stream->writeString(username);
    }
    if ((flags & 16) != 0) {
        stream->writeString(phone);
    }
    if ((flags & 32) != 0) {
        photo->serializeToStream(stream);
    }
    if ((flags & 64) != 0) {
        status->serializeToStream(stream);
    }
    if ((flags & 16384) != 0) {
        stream->writeInt32(bot_info_version);
    }
    if ((flags & 262144) != 0) {
        stream->writeString(restriction_reason);
    }
    if ((flags & 524288) != 0) {
        stream->writeString(bot_inline_placeholder);
    }
    if ((flags & 4194304) != 0) {
        stream->writeString(lang_code);
    }
}

TL_auth_authorization *TL_auth_authorization::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_auth_authorization::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_auth_authorization", constructor);
        return nullptr;
    }
    TL_auth_authorization *result = new TL_auth_authorization();
    result->readParams(stream, error);
    return result;
}

void TL_auth_authorization::readParams(NativeByteBuffer *stream, bool &error) {
    flags = stream->readInt32(&error);
    if ((flags & 1) != 0) {
        tmp_sessions = stream->readInt32(&error);
    }
    user = std::unique_ptr<User>(User::TLdeserialize(stream, stream->readUint32(&error), error));
}

TL_auth_exportedAuthorization *TL_auth_exportedAuthorization::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_auth_exportedAuthorization::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_auth_exportedAuthorization", constructor);
        return nullptr;
    }
    TL_auth_exportedAuthorization *result = new TL_auth_exportedAuthorization();
    result->readParams(stream, error);
    return result;
}

void TL_auth_exportedAuthorization::readParams(NativeByteBuffer *stream, bool &error) {
    id = stream->readInt32(&error);
    bytes = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

bool TL_auth_exportAuthorization::isNeedLayer() {
    return true;
}

TLObject *TL_auth_exportAuthorization::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_auth_exportedAuthorization::TLdeserialize(stream, constructor, error);
}

void TL_auth_exportAuthorization::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(dc_id);
}

bool TL_auth_importAuthorization::isNeedLayer() {
    return true;
}

TLObject *TL_auth_importAuthorization::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_auth_authorization::TLdeserialize(stream, constructor, error);
}

void TL_auth_importAuthorization::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(id);
    stream->writeByteArray(bytes.get());
}

UserStatus *UserStatus::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    UserStatus *result = nullptr;
    switch (constructor) {
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
        default:
            error = true;
            DEBUG_E("can't parse magic %x in UserStatus", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_userStatusOffline::readParams(NativeByteBuffer *stream, bool &error) {
    expires = stream->readInt32(&error);
}

void TL_userStatusOffline::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(expires);
}

void TL_userStatusLastWeek::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_userStatusEmpty::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_userStatusLastMonth::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_userStatusOnline::readParams(NativeByteBuffer *stream, bool &error) {
    expires = stream->readInt32(&error);
}

void TL_userStatusOnline::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(expires);
}

void TL_userStatusRecently::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

FileLocation *FileLocation::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    FileLocation *result = nullptr;
    switch (constructor) {
        case 0x53d69076:
            result = new TL_fileLocation();
            break;
        case 0x7c596b46:
            result = new TL_fileLocationUnavailable();
            break;
        case 0x55555554:
            result = new TL_fileEncryptedLocation();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in FileLocation", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_fileLocation::readParams(NativeByteBuffer *stream, bool &error) {
    dc_id = stream->readInt32(&error);
    volume_id = stream->readInt64(&error);
    local_id = stream->readInt32(&error);
    secret = stream->readInt64(&error);
}

void TL_fileLocation::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(dc_id);
    stream->writeInt64(volume_id);
    stream->writeInt32(local_id);
    stream->writeInt64(secret);
}

void TL_fileLocationUnavailable::readParams(NativeByteBuffer *stream, bool &error) {
    volume_id = stream->readInt64(&error);
    local_id = stream->readInt32(&error);
    secret = stream->readInt64(&error);
}

void TL_fileLocationUnavailable::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(volume_id);
    stream->writeInt32(local_id);
    stream->writeInt64(secret);
}

void TL_fileEncryptedLocation::readParams(NativeByteBuffer *stream, bool &error) {
    dc_id = stream->readInt32(&error);
    volume_id = stream->readInt64(&error);
    local_id = stream->readInt32(&error);
    secret = stream->readInt64(&error);
    key = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
    iv = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

void TL_fileEncryptedLocation::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(dc_id);
    stream->writeInt64(volume_id);
    stream->writeInt32(local_id);
    stream->writeInt64(secret);
    stream->writeByteArray(key.get());
    stream->writeByteArray(iv.get());
}

UserProfilePhoto *UserProfilePhoto::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    UserProfilePhoto *result = nullptr;
    switch (constructor) {
        case 0x4f11bae1:
            result = new TL_userProfilePhotoEmpty();
            break;
        case 0xd559d8c8:
            result = new TL_userProfilePhoto();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in UserProfilePhoto", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_userProfilePhotoEmpty::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_userProfilePhoto::readParams(NativeByteBuffer *stream, bool &error) {
    photo_id = stream->readInt64(&error);
    photo_small = std::unique_ptr<FileLocation>(FileLocation::TLdeserialize(stream, stream->readUint32(&error), error));
    photo_big = std::unique_ptr<FileLocation>(FileLocation::TLdeserialize(stream, stream->readUint32(&error), error));
}

void TL_userProfilePhoto::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(photo_id);
    photo_small->serializeToStream(stream);
    photo_big->serializeToStream(stream);
}

auth_SentCode *auth_SentCode::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    auth_SentCode *result = nullptr;
    switch (constructor) {
        case 0xe325edcf:
            result = new TL_auth_sentAppCode();
            break;
        case 0xefed51d9:
            result = new TL_auth_sentCode();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in auth_SentCode", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_auth_sentAppCode::readParams(NativeByteBuffer *stream, bool &error) {
    phone_registered = stream->readBool(&error);
    phone_code_hash = stream->readString(&error);
    send_call_timeout = stream->readInt32(&error);
    is_password = stream->readBool(&error);
}

void TL_auth_sentAppCode::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBool(phone_registered);
    stream->writeString(phone_code_hash);
    stream->writeInt32(send_call_timeout);
    stream->writeBool(is_password);
}

void TL_auth_sentCode::readParams(NativeByteBuffer *stream, bool &error) {
    phone_registered = stream->readBool(&error);
    phone_code_hash = stream->readString(&error);
    send_call_timeout = stream->readInt32(&error);
    is_password = stream->readBool(&error);
}

void TL_auth_sentCode::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBool(phone_registered);
    stream->writeString(phone_code_hash);
    stream->writeInt32(send_call_timeout);
    stream->writeBool(is_password);
}

TLObject *TL_auth_sendCode::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return auth_SentCode::TLdeserialize(stream, constructor, error);
}

void TL_auth_sendCode::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeString(phone_number);
    stream->writeInt32(sms_type);
    stream->writeInt32(api_id);
    stream->writeString(api_hash);
    stream->writeString(lang_code);
}

void TL_updatesTooLong::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

TL_upload_file *TL_upload_file::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_upload_file::constructor != constructor) {
        error = true;
        FileLog::e("can't parse magic %x in TL_upload_file", constructor);
        return nullptr;
    }
    TL_upload_file *result = new TL_upload_file();
    result->readParams(stream, error);
    return result;
}

TL_upload_file::~TL_upload_file() {
    if (bytes != nullptr) {
        bytes->reuse();
        bytes = nullptr;
    }
}

void TL_upload_file::readParams(NativeByteBuffer *stream, bool &error) {
    type = std::unique_ptr<storage_FileType>(storage_FileType::TLdeserialize(stream, stream->readUint32(&error), error));
    mtime = stream->readInt32(&error);
    bytes = stream->readByteBuffer(true, &error);
}

InputFileLocation *InputFileLocation::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    InputFileLocation *result = nullptr;
    switch (constructor) {
        case 0x430f0724:
            result = new TL_inputDocumentFileLocation();
            break;
        case 0x14637196:
            result = new TL_inputFileLocation();
            break;
        case 0xf5235d55:
            result = new TL_inputEncryptedFileLocation();
            break;
        default:
            error = true;
            FileLog::e("can't parse magic %x in InputFileLocation", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_inputDocumentFileLocation::readParams(NativeByteBuffer *stream, bool &error) {
    id = stream->readInt64(&error);
    access_hash = stream->readInt64(&error);
    version = stream->readInt32(&error);
}

void TL_inputDocumentFileLocation::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(id);
    stream->writeInt64(access_hash);
    stream->writeInt32(version);
}

void TL_inputFileLocation::readParams(NativeByteBuffer *stream, bool &error) {
    volume_id = stream->readInt64(&error);
    local_id = stream->readInt32(&error);
    secret = stream->readInt64(&error);
}

void TL_inputFileLocation::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(volume_id);
    stream->writeInt32(local_id);
    stream->writeInt64(secret);
}

void TL_inputEncryptedFileLocation::readParams(NativeByteBuffer *stream, bool &error) {
    id = stream->readInt64(&error);
    access_hash = stream->readInt64(&error);
}

void TL_inputEncryptedFileLocation::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(id);
    stream->writeInt64(access_hash);
}

storage_FileType *storage_FileType::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    storage_FileType *result = nullptr;
    switch (constructor) {
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
        default:
            error = true;
            FileLog::e("can't parse magic %x in storage_FileType", constructor);
            return nullptr;
    }
    result->readParams(stream, error);
    return result;
}

void TL_storage_fileUnknown::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileMp4::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileWebp::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_filePng::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileGif::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_filePdf::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileMp3::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileJpeg::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_fileMov::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_storage_filePartial::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

TLObject *TL_upload_saveFilePart::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return Bool::TLdeserialize(stream, constructor, error);
}

void TL_upload_saveFilePart::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(file_id);
    stream->writeInt32(file_part);
    stream->writeByteArray(bytes.get());
}

bool TL_upload_saveFilePart::isNeedLayer() {
    return true;
}

TLObject *TL_upload_getFile::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_upload_file::TLdeserialize(stream, constructor, error);
}

void TL_upload_getFile::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    location->serializeToStream(stream);
    stream->writeInt32(offset);
    stream->writeInt32(limit);
}

bool TL_upload_getFile::isNeedLayer() {
    return true;
}
