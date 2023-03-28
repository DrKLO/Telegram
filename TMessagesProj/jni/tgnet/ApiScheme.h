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
    bool thisPortOnly;
    bool force_try_ipv6;
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

class Reaction : public TLObject {

public:
    static Reaction *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};


class TL_config : public TLObject {

public:
    static const uint32_t constructor = 0xcc1a241e;

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
    // int32_t saved_gifs_limit;
    int32_t edit_time_limit;
    int32_t revoke_time_limit;
    int32_t revoke_pm_time_limit;
    int32_t rating_e_decay;
    int32_t stickers_recent_limit;
    // int32_t stickers_faved_limit;
    int32_t channels_read_media_period;
    int32_t tmp_sessions;
    // int32_t pinned_dialogs_count_max;
    // int32_t pinned_infolder_count_max;
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
    std::unique_ptr<Reaction> reactions_default;
    std::string autologin_token;

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
    int32_t flags;
    bool has_video;
    int64_t photo_id;
    std::unique_ptr<ByteArray> stripped_thumb;
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
    static const uint32_t constructor = 0x82d1f706;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_restrictionReason : public TLObject {

public:
    static const uint32_t constructor = 0xd072acb4;

    std::string platform;
    std::string reason;
    std::string text;

    static TL_restrictionReason *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_username : public TLObject {

public:
    static const uint32_t constructor = 0xb4073647;
    int32_t flags;
    bool editable;
    bool active;
    std::string username;

    static TL_username *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class User : public TLObject {

public:
    int64_t id;
    std::string first_name;
    std::string last_name;
    std::string username;
    int64_t access_hash;
    std::string phone;
    std::unique_ptr<UserProfilePhoto> photo;
    std::unique_ptr<UserStatus> status;
    int32_t flags;
    int32_t flags2;
    int32_t bot_info_version;
    std::vector<std::unique_ptr<TL_restrictionReason>> restriction_reason;
    std::string bot_inline_placeholder;
    std::string lang_code;
    std::vector<std::unique_ptr<TL_username>> usernames;

    static User *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_userEmpty : public User {

public:
    static const uint32_t constructor = 0xd3bc4b7a;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_user : public User {

public:
    static const uint32_t constructor = 0x8f97c628;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class InputPeer : public TLObject {

public:
    int64_t user_id;
    int64_t chat_id;
    int64_t channel_id;
    int64_t access_hash;

    static InputPeer *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_inputPeerSelf : public InputPeer {

public:
    static const uint32_t constructor = 0x7da07ec9;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerUser : public InputPeer {

public:
    static const uint32_t constructor = 0xdde8a54c;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerChat : public InputPeer {

public:
    static const uint32_t constructor = 0x35a95cb9;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerUserFromMessage : public InputPeer {

public:
    static const uint32_t constructor = 0xa87b0a1c;

    std::unique_ptr<InputPeer> peer;
    int32_t msg_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerChannelFromMessage : public InputPeer {
    
public:
    static const uint32_t constructor = 0xbd2a0840;

    std::unique_ptr<InputPeer> peer;
    int32_t msg_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerChannel : public InputPeer {
    
public:
    static const uint32_t constructor = 0x27bcbbfc;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputPeerEmpty : public InputPeer {

public:
    static const uint32_t constructor = 0x7f3b18ea;

    void serializeToStream(NativeByteBuffer *stream);
};

class InputUser : public TLObject {

public:
    int64_t user_id;
    int64_t access_hash;

    static InputUser *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_inputUserSelf : public InputUser {
    
public:
    static const uint32_t constructor = 0xf7c1b13f;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputUser : public InputUser {

public:
    static const uint32_t constructor = 0xf21158c6;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputUserEmpty : public InputUser {
    
public:
    static const uint32_t constructor = 0xb98886cf;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputUserFromMessage : public InputUser {

public:
    static const uint32_t constructor = 0x1da448e2;

    std::unique_ptr<InputPeer> peer;
    int32_t msg_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class MessageEntity : public TLObject {

public:
    int32_t offset;
    int32_t length;
    std::string url;
    std::string language;

    static MessageEntity *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_messageEntityTextUrl : public MessageEntity {

public:
    static const uint32_t constructor = 0x76a6d327;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityBotCommand : public MessageEntity {

public:
    static const uint32_t constructor = 0x6cef8ac7;
    
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityEmail : public MessageEntity {
    
public:
    static const uint32_t constructor = 0x64e475c2;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityPre : public MessageEntity {

public:
    static const uint32_t constructor = 0x73924be0;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityUnknown : public MessageEntity {
public:
    static const uint32_t constructor = 0xbb92ba95;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityUrl : public MessageEntity {

public:
    static const uint32_t constructor = 0x6ed02538;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityItalic : public MessageEntity {

public:
    static const uint32_t constructor = 0x826f8b60;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityMention : public MessageEntity {

public:
    static const uint32_t constructor = 0xfa04579d;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityMentionName : public MessageEntity {

public:
    static const uint32_t constructor = 0xdc7b1140;

    int64_t user_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_inputMessageEntityMentionName : public MessageEntity {

public:
    static const uint32_t constructor = 0x208e68c9;

    std::unique_ptr<InputUser> user_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityCashtag : public MessageEntity {
    
public:
    static const uint32_t constructor = 0x4c4e743f;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityBold : public MessageEntity {

public:
    static const uint32_t constructor = 0xbd610bc9;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityHashtag : public MessageEntity {

public:
    static const uint32_t constructor = 0x6f635b0d;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityCode : public MessageEntity {

public:
    static const uint32_t constructor = 0x28a20571;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityStrike : public MessageEntity {
    
public:
    static const uint32_t constructor = 0xbf0693d4;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityBlockquote : public MessageEntity {

public:
    static const uint32_t constructor = 0x20df5d0;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityUnderline : public MessageEntity {

public:
    static const uint32_t constructor = 0x9c4e7e8b;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_messageEntityPhone : public MessageEntity {
    
public:
    static const uint32_t constructor = 0x9b69e34b;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_dataJSON : public TLObject {

public:
    static const uint32_t constructor = 0x7d748d04;

    std::string data;

    static TL_dataJSON *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_help_termsOfService : public TLObject {

public:
    static const uint32_t constructor = 0x780a0310;

    int32_t flags;
    bool popup;
    std::unique_ptr<TL_dataJSON> id;
    std::string text;
    std::vector<std::unique_ptr<MessageEntity>> entities;
    int32_t min_age_confirm;

    static TL_help_termsOfService *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class auth_Authorization : public TLObject {

public:
    static auth_Authorization *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error);
};

class TL_auth_authorizationSignUpRequired : public auth_Authorization {

public:
    static const uint32_t constructor = 0x44747e9a;

    int32_t flags;
    std::unique_ptr<TL_help_termsOfService> terms_of_service;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_authorization : public auth_Authorization {
    
public:
    static const uint32_t constructor = 0x2ea2c0d4;

    int32_t flags;
    int32_t tmp_sessions;
    int32_t otherwise_relogin_days;
    std::unique_ptr<ByteArray> future_auth_token;
    std::unique_ptr<User> user;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_exportedAuthorization : public TLObject {

public:
    static const uint32_t constructor = 0xb434e2b8;

    int64_t id;
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
    static const uint32_t constructor = 0xa57a7dad;

    int64_t id;
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


class TL_reactionCustomEmoji : public Reaction {

public:
    static const uint32_t constructor = 0x8935fc73;
    int64_t document_id;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};


class TL_reactionEmoji : public Reaction {

public:
    static const uint32_t constructor = 0x1b2286b8;
    std::string emoticon;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};



class TL_reactionEmpty : public Reaction {

public:
    static const uint32_t constructor = 0x79f5d419;

    void readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

#endif
