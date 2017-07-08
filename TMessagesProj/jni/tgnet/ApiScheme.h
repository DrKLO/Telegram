/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2016.
 */

#ifndef APISCHEME_H
#define APISCHEME_H

#include <vector>
#include <memory>
#include <bits/unique_ptr.h>
#include "TLObject.h"

class ByteArray;
class NativeByteBuffer;

class Bool : public TLObject {

public:
    static Bool *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
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
    static const uint32_t constructor = 0x5d8c6cc;

    int32_t flags;
    bool ipv6;
    bool media_only;
    bool tcpo_only;
    bool cdn;
    bool isStatic;
    int32_t id;
    std::string ip_address;
    int32_t port;

    static TL_dcOption *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_cdnPublicKey : public TLObject {

public:
    static const uint32_t constructor = 0xc982eaba;

    int32_t dc_id;
    std::string public_key;

    static TL_cdnPublicKey *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_cdnConfig : public TLObject {

public:
    static const uint32_t constructor = 0x5725e40a;

    std::vector<std::unique_ptr<TL_cdnPublicKey>> public_keys;

    static TL_cdnConfig *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_help_getCdnConfig : public TLObject {

public:
    static const uint32_t constructor = 0x52029342;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_disabledFeature : public TLObject {

public:
    static const uint32_t constructor = 0xae636f24;

    std::string feature;
    std::string description;

    static TL_disabledFeature *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_config : public TLObject {

public:
    static const uint32_t constructor = 0x7feec888;

    int32_t flags;
    int32_t date;
    int32_t expires;
    bool test_mode;
    int32_t this_dc;
    std::vector<std::unique_ptr<TL_dcOption>> dc_options;
    int32_t chat_size_max;
    int32_t megagroup_size_max;
    int32_t forwarded_count_max;
    int32_t online_update_period_ms;
    int32_t offline_blur_timeout_ms;
    int32_t offline_idle_timeout_ms;
    int32_t online_cloud_timeout_ms;
    int32_t notify_cloud_delay_ms;
    int32_t notify_default_delay_ms;
    int32_t chat_big_size;
    int32_t push_chat_period_ms;
    int32_t push_chat_limit;
    int32_t saved_gifs_limit;
    int32_t edit_time_limit;
    int32_t rating_e_decay;
    int32_t stickers_recent_limit;
    int32_t tmp_sessions;
    int32_t pinned_dialogs_count_max;
    int32_t call_receive_timeout_ms;
    int32_t call_ring_timeout_ms;
    int32_t call_connect_timeout_ms;
    int32_t call_packet_timeout_ms;
    std::string me_url_prefix;
    std::string suggested_lang_code;
    int32_t lang_pack_version;
    std::vector<std::unique_ptr<TL_disabledFeature>> disabled_features;

    static TL_config *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_help_getConfig : public TLObject {

public:
    static const uint32_t constructor = 0xc4f9186b;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_account_registerDevice : public TLObject {

public:
    static const uint32_t constructor = 0x637ea878;

    int32_t token_type;
    std::string token;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class UserStatus : public TLObject {

public:
    int32_t expires;

    static UserStatus *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_userStatusOffline : public UserStatus {

public:
    static const uint32_t constructor = 0x8c703f;

    void readParams(NativeByteBuffer *stream, bool &error);
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

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userStatusRecently : public UserStatus {

public:
    static const uint32_t constructor = 0xe26f42f1;

    void serializeToStream(NativeByteBuffer *stream);
};

class FileLocation : public TLObject {

public:
    int32_t dc_id;
    int64_t volume_id;
    int32_t local_id;
    int64_t secret;
    std::unique_ptr<ByteArray> key;
    std::unique_ptr<ByteArray> iv;

    static FileLocation *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_fileLocation : public FileLocation {

public:
    static const uint32_t constructor = 0x53d69076;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_fileEncryptedLocation : public FileLocation {

public:
    static const uint32_t constructor = 0x55555554;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_fileLocationUnavailable : public FileLocation {

public:
    static const uint32_t constructor = 0x7c596b46;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class UserProfilePhoto : public TLObject {

public:
    int64_t photo_id;
    std::unique_ptr<FileLocation> photo_small;
    std::unique_ptr<FileLocation> photo_big;

    static UserProfilePhoto *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_userProfilePhotoEmpty : public UserProfilePhoto {

public:
    static const uint32_t constructor = 0x4f11bae1;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_userProfilePhoto : public UserProfilePhoto {

public:
    static const uint32_t constructor = 0xd559d8c8;

    void readParams(NativeByteBuffer *stream, bool &error);
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

    static User *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_userEmpty : public User {

public:
    static const uint32_t constructor = 0x200250ba;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_user : public User {

public:
    static const uint32_t constructor = 0x2e13f4c3;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_authorization : public TLObject {

public:
    static const uint32_t constructor = 0xcd050916;

    int32_t flags;
    int32_t tmp_sessions;
    std::unique_ptr<User> user;

    static TL_auth_authorization *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_auth_exportedAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xdf969c2d;

    int32_t id;
    std::unique_ptr<ByteArray> bytes;

    static TL_auth_exportedAuthorization *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_auth_exportAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xe5bfffcd;

    int32_t dc_id;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_importAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xe3ef9613;

    int32_t id;
    std::unique_ptr<ByteArray> bytes;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class auth_SentCode : public TLObject {

public:
    bool phone_registered;
    std::string phone_code_hash;
    int32_t send_call_timeout;
    bool is_password;

    static auth_SentCode *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_auth_sentAppCode : public auth_SentCode {

public:
    static const uint32_t constructor = 0xe325edcf;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_sentCode : public auth_SentCode {

public:
    static const uint32_t constructor = 0xefed51d9;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_sendCode : public TLObject {

public:
    static const uint32_t constructor = 0x768d5f4d;

    std::string phone_number;
    int32_t sms_type;
    int32_t api_id;
    std::string api_hash;
    std::string lang_code;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_updatesTooLong : public TLObject {

public:
    static const uint32_t constructor = 0xe317af7e;

    void serializeToStream(NativeByteBuffer *stream);
};

class storage_FileType : public TLObject {

public:

    static storage_FileType *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_storage_fileUnknown : public storage_FileType {

public:
    static const uint32_t constructor = 0xaa963b05;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileMp4 : public storage_FileType {

public:
    static const uint32_t constructor = 0xb3cea0e4;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileWebp : public storage_FileType {

public:
    static const uint32_t constructor = 0x1081464c;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_filePng : public storage_FileType {

public:
    static const uint32_t constructor = 0xa4f63c0;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileGif : public storage_FileType {

public:
    static const uint32_t constructor = 0xcae1aadf;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_filePdf : public storage_FileType {

public:
    static const uint32_t constructor = 0xae1e508d;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileMp3 : public storage_FileType {

public:
    static const uint32_t constructor = 0x528a0677;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileJpeg : public storage_FileType {

public:
    static const uint32_t constructor = 0x7efe0e;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_fileMov : public storage_FileType {

public:
    static const uint32_t constructor = 0x4b09ebbc;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_storage_filePartial : public storage_FileType {

public:
    static const uint32_t constructor = 0x40bc6f52;

    void serializeToStream(NativeByteBuffer *stream);
};

class InputFileLocation : public TLObject {

public:
    int64_t id;
    int64_t access_hash;
    int32_t version;
    int64_t volume_id;
    int32_t local_id;
    int64_t secret;

    static InputFileLocation *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_inputDocumentFileLocation : public InputFileLocation {

public:
    static const uint32_t constructor = 0x430f0724;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputFileLocation : public InputFileLocation {

public:
    static const uint32_t constructor = 0x14637196;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputEncryptedFileLocation : public InputFileLocation {

public:
    static const uint32_t constructor = 0xf5235d55;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_upload_saveFilePart : public TLObject {

public:
    static const uint32_t constructor = 0xb304a621;

    int64_t file_id;
    int32_t file_part;
    std::unique_ptr<ByteArray> bytes;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_upload_file : public TLObject {

public:
    static const uint32_t constructor = 0x96a18d5;

    std::unique_ptr<storage_FileType> type;
    int32_t mtime;
    NativeByteBuffer *bytes = nullptr;

    ~TL_upload_file();
    static TL_upload_file *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_upload_getFile : public TLObject {

public:
    static const uint32_t constructor = 0xe3a6cfb5;

    InputFileLocation *location;
    int32_t offset;
    int32_t limit;

    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

#endif
