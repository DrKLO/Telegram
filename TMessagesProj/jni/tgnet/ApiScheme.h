/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef APISCHEME_H
#define APISCHEME_H

#include <vector>
#include <memory>
#include "TLObject.h"

class ByteArray;
class NativeByteBuffer;

class Bool : public TLObject {

public:
    static Bool *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_boolTrue : public Bool {

public:
    static const uint32_t constructor = 0x997275b5;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_boolFalse : public Bool {

public:
    static const uint32_t constructor = 0xbc799737;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_dcOption : public TLObject {

public:
    static const uint32_t constructor = 0x18b7a10d;

    int32_t flags;
    bool ipv6;
    bool media_only;
    bool tcpo_only;
    bool cdn;
    bool isStatic;
    int32_t id;
    std::string ip_address;
    int32_t port;
    std::unique_ptr<ByteArray> secret;

    static TL_dcOption *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_cdnPublicKey : public TLObject {

public:
    static const uint32_t constructor = 0xc982eaba;

    int32_t dc_id;
    std::string public_key;

    static TL_cdnPublicKey *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_cdnConfig : public TLObject {

public:
    static const uint32_t constructor = 0x5725e40a;

    std::vector<std::unique_ptr<TL_cdnPublicKey>> public_keys;

    static TL_cdnConfig *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_help_getCdnConfig : public TLObject {

public:
    static const uint32_t constructor = 0x52029342;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_config : public TLObject {

public:
    static const uint32_t constructor = 0x330b4067;

    int32_t flags;
    int32_t date;
    int32_t expires;
    bool test_mode;
    int32_t this_dc;
    std::vector<std::unique_ptr<TL_dcOption>> dc_options;
    std::string dc_txt_domain_name;
    int32_t chat_size_max;
    int32_t megagroup_size_max;
    int32_t forwarded_count_max;
    int32_t online_update_period_ms;
    int32_t offline_blur_timeout_ms;
    int32_t offline_idle_timeout_ms;
    int32_t online_cloud_timeout_ms;
    int32_t notify_cloud_delay_ms;
    int32_t notify_default_delay_ms;
    int32_t push_chat_period_ms;
    int32_t push_chat_limit;
    int32_t saved_gifs_limit;
    int32_t edit_time_limit;
    int32_t revoke_time_limit;
    int32_t revoke_pm_time_limit;
    int32_t rating_e_decay;
    int32_t stickers_recent_limit;
    int32_t stickers_faved_limit;
    int32_t channels_read_media_period;
    int32_t tmp_sessions;
    int32_t pinned_dialogs_count_max;
    int32_t pinned_infolder_count_max;
    int32_t call_receive_timeout_ms;
    int32_t call_ring_timeout_ms;
    int32_t call_connect_timeout_ms;
    int32_t call_packet_timeout_ms;
    std::string me_url_prefix;
    std::string autoupdate_url_prefix;
    std::string gif_search_username;
    std::string venue_search_username;
    std::string img_search_username;
    std::string static_maps_provider;
    int32_t caption_length_max;
    int32_t message_length_max;
    int32_t webfile_dc_id;
    std::string suggested_lang_code;
    int32_t lang_pack_version;
    int32_t base_lang_pack_version;

    static TL_config *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_help_getConfig : public TLObject {

public:
    static const uint32_t constructor = 0xc4f9186b;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_account_registerDevice : public TLObject {

public:
    static const uint32_t constructor = 0x637ea878;

    int32_t token_type;
    std::string token;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class UserStatus : public TLObject {

public:
    int32_t expires;

    static UserStatus *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_userStatusOffline : public UserStatus {

public:
    static const uint32_t constructor = 0x8c703f;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusLastWeek : public UserStatus {

public:
    static const uint32_t constructor = 0x7bf09fc;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusEmpty : public UserStatus {

public:
    static const uint32_t constructor = 0x9d05049;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusLastMonth : public UserStatus {

public:
    static const uint32_t constructor = 0x77ebc742;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusOnline : public UserStatus {

public:
    static const uint32_t constructor = 0xedb93949;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusRecently : public UserStatus {

public:
    static const uint32_t constructor = 0xe26f42f1;

    void serializeToStream(NativeByteBuffer *stream);
};

class FileLocation : public TLObject {

public:
    int64_t volume_id;
    int32_t local_id;

    static FileLocation *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_fileLocationToBeDeprecated : public FileLocation {

public:
    static const uint32_t constructor = 0xbc7fc6cd;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class UserProfilePhoto : public TLObject {

public:
    int64_t photo_id;
    std::unique_ptr<FileLocation> photo_small;
    std::unique_ptr<FileLocation> photo_big;
    int32_t dc_id;

    static UserProfilePhoto *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_userProfilePhotoEmpty : public UserProfilePhoto {

public:
    static const uint32_t constructor = 0x4f11bae1;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userProfilePhoto : public UserProfilePhoto {

public:
    static const uint32_t constructor = 0xecd75d8c;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class User : public TLObject {

public:
    int32_t id;
    std::string first_name;
    std::string last_name;
    std::string username;
    int64_t access_hash;
    std::string phone;
    std::unique_ptr<UserProfilePhoto> photo;
    std::unique_ptr<UserStatus> status;
    int32_t flags;
    int32_t bot_info_version;
    std::string restriction_reason;
    std::string bot_inline_placeholder;
    std::string lang_code;

    static User *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_userEmpty : public User {

public:
    static const uint32_t constructor = 0x200250ba;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_user : public User {

public:
    static const uint32_t constructor = 0x2e13f4c3;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_authorization : public TLObject {

public:
    static const uint32_t constructor = 0xcd050916;

    int32_t flags;
    int32_t tmp_sessions;
    std::unique_ptr<User> user;

    static TL_auth_authorization *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
};

class TL_auth_exportedAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xdf969c2d;

    int32_t id;
    std::unique_ptr<ByteArray> bytes;

    static TL_auth_exportedAuthorization *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
};

class TL_auth_exportAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xe5bfffcd;

    int32_t dc_id;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_importAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xe3ef9613;

    int32_t id;
    std::unique_ptr<ByteArray> bytes;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_updatesTooLong : public TLObject {

public:
    static const uint32_t constructor = 0xe317af7e;

    void serializeToStream(NativeByteBuffer *stream);
};

#endif
