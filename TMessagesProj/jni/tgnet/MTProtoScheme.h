/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef MTPROTOSCHEME_H
#define MTPROTOSCHEME_H

#include <vector>
#include <memory>
#include <map>
#include <bits/unique_ptr.h>
#include "TLObject.h"

class ByteArray;
class NativeByteBuffer;

class TLClassStore {

public:
    static TLObject *TLdeserialize(NativeByteBuffer *stream, uint32_t bytes, uint32_t constructor, bool &error);

};

class TL_api_request : public TLObject {

public:
    NativeByteBuffer *request = nullptr;

    ~TL_api_request();
    bool isNeedLayer();
    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t bytes, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_api_response : public TLObject {

public:
    std::unique_ptr<NativeByteBuffer> response;

    void readParamsEx(NativeByteBuffer *stream, uint32_t bytes, bool &error);
};

class TL_future_salt : public TLObject {

public:
    static const uint32_t constructor = 0x0949d9dc;

    int32_t valid_since;
    int32_t valid_until;
    int64_t salt;

    static TL_future_salt *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_msgs_state_info : public TLObject {

public:
    static const uint32_t constructor = 0x04deb57d;

    int64_t req_msg_id;
    std::unique_ptr<ByteArray> info;

    static TL_msgs_state_info *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class Server_DH_Params : public TLObject {

public:
    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> new_nonce_hash;
    std::unique_ptr<ByteArray> encrypted_answer;

    static Server_DH_Params *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_server_DH_params_fail : public Server_DH_Params {

public:
    static const uint32_t constructor = 0x79cb045d;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_server_DH_params_ok : public Server_DH_Params {

public:
    static const uint32_t constructor = 0xd0e8075c;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_resPQ : public TLObject {

public:
    static const uint32_t constructor = 0x05162463;

    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> pq;
    std::vector<int64_t> server_public_key_fingerprints;

    static TL_resPQ *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_p_q_inner_data : public TLObject {

public:
    static const uint32_t constructor = 0x83c95aec;

    std::unique_ptr<ByteArray> pq;
    std::unique_ptr<ByteArray> p;
    std::unique_ptr<ByteArray> q;
    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> new_nonce;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_pong : public TLObject {

public:
    static const uint32_t constructor = 0x347773c5;

    int64_t msg_id;
    int64_t ping_id;

    static TL_pong *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_future_salts : public TLObject {

public:
    static const uint32_t constructor = 0xae500895;

    int64_t req_msg_id;
    int32_t now;
    std::vector<std::unique_ptr<TL_future_salt>> salts;

    static TL_future_salts *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class RpcDropAnswer : public TLObject {

public:
    int64_t msg_id;
    int32_t seq_no;
    int32_t bytes;

    static RpcDropAnswer *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_rpc_answer_unknown : public RpcDropAnswer {

public:
    static const uint32_t constructor = 0x5e2ad36e;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_rpc_answer_dropped : public RpcDropAnswer {

public:
    static const uint32_t constructor = 0xa43ad8b7;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_rpc_answer_dropped_running : public RpcDropAnswer {

public:
    static const uint32_t constructor = 0xcd78e586;

    void serializeToStream(NativeByteBuffer *stream);
};

class Set_client_DH_params_answer : public TLObject {

public:
    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> new_nonce_hash2;
    std::unique_ptr<ByteArray> new_nonce_hash3;
    std::unique_ptr<ByteArray> new_nonce_hash1;

    static Set_client_DH_params_answer *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_message : public TLObject {

public:
    static const uint32_t constructor = 0x5bb8e511;

    int64_t msg_id;
    int32_t seqno;
    int32_t bytes;
    std::unique_ptr<TLObject> body;
    TLObject *outgoingBody = nullptr;
    std::unique_ptr<NativeByteBuffer> unparsedBody;

    static TL_message *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_dh_gen_retry : public Set_client_DH_params_answer {

public:
    static const uint32_t constructor = 0x46dc1fb9;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_dh_gen_fail : public Set_client_DH_params_answer {

public:
    static const uint32_t constructor = 0xa69dae02;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_dh_gen_ok : public Set_client_DH_params_answer {

public:
    static const uint32_t constructor = 0x3bcbf734;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class BadMsgNotification : public TLObject {

public:
    int64_t bad_msg_id;
    int32_t bad_msg_seqno;
    int32_t error_code;
    int64_t new_server_salt;

    static BadMsgNotification *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_bad_msg_notification : public BadMsgNotification {

public:
    static const uint32_t constructor = 0xa7eff811;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_bad_server_salt : public BadMsgNotification {

public:
    static const uint32_t constructor = 0xedab447b;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_msgs_state_req : public TLObject {

public:
    static const uint32_t constructor = 0xda69fb52;

    std::vector<int64_t> msg_ids;

    static TL_msgs_state_req *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class MsgDetailedInfo : public TLObject {

public:
    int64_t answer_msg_id;
    int32_t bytes;
    int32_t status;
    int64_t msg_id;

    static MsgDetailedInfo *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_msg_new_detailed_info : public MsgDetailedInfo {

public:
    static const uint32_t constructor = 0x809db6df;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_msg_detailed_info : public MsgDetailedInfo {

public:
    static const uint32_t constructor = 0x276d3ec6;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_msg_copy : public TLObject {

public:
    static const uint32_t constructor = 0xe06046b2;

    std::unique_ptr<TL_message> orig_message;

    static TL_msg_copy *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_msgs_all_info : public TLObject {

public:
    static const uint32_t constructor = 0x8cc0d131;

    std::vector<int64_t> msg_ids;
    std::unique_ptr<ByteArray> info;

    static TL_msgs_all_info *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_rpc_result : public TLObject {

public:
    static const uint32_t constructor = 0xf35c6d01;

    int64_t req_msg_id;
    std::unique_ptr<TLObject> result;

    void readParamsEx(NativeByteBuffer *stream, uint32_t bytes, bool &error);
};

class TL_new_session_created : public TLObject {

public:
    static const uint32_t constructor = 0x9ec20908;

    int64_t first_msg_id;
    int64_t unique_id;
    int64_t server_salt;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class DestroySessionRes : public TLObject {

public:
    int64_t session_id;

    static DestroySessionRes *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_destroy_session_ok : public DestroySessionRes {

public:
    static const uint32_t constructor = 0xe22045fc;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_destroy_session_none : public DestroySessionRes {

public:
    static const uint32_t constructor = 0x62d350c9;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_msgs_ack : public TLObject {

public:
    static const uint32_t constructor = 0x62d6b459;

    std::vector<int64_t> msg_ids;

    static TL_msgs_ack *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_msg_container : public TLObject {

public:
    static const uint32_t constructor = 0x73f1f8dc;

    std::vector<std::unique_ptr<TL_message>> messages;

    static TL_msg_container *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_msg_resend_req : public TLObject {

public:
    static const uint32_t constructor = 0x7d861a08;

    std::vector<int64_t> msg_ids;

    static TL_msg_resend_req *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class RpcError : public TLObject {

public:
    int32_t error_code;
    std::string error_message;
    int64_t query_id;
};

class TL_rpc_error : public RpcError {

public:
    static const uint32_t constructor = 0x2144ca19;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_rpc_req_error : public RpcError {

public:
    static const uint32_t constructor = 0x7ae432f5;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_client_DH_inner_data : public TLObject {

public:
    static const uint32_t constructor = 0x6643b654;

    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    int64_t retry_id;
    std::unique_ptr<ByteArray> g_b;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_server_DH_inner_data : public TLObject {

public:
    static const uint32_t constructor = 0xb5890dba;

    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    uint32_t g;
    std::unique_ptr<ByteArray> dh_prime;
    std::unique_ptr<ByteArray> g_a;
    int32_t server_time;

    static TL_server_DH_inner_data *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_req_pq : public TLObject {

public:
    static const uint32_t constructor = 0x60469778;

    std::unique_ptr<ByteArray> nonce;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_req_DH_params : public TLObject {

public:
    static const uint32_t constructor = 0xd712e4be;

    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> p;
    std::unique_ptr<ByteArray> q;
    int64_t public_key_fingerprint;
    std::unique_ptr<ByteArray> encrypted_data;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_set_client_DH_params : public TLObject {

public:
    static const uint32_t constructor = 0xf5045f1f;

    std::unique_ptr<ByteArray> nonce;
    std::unique_ptr<ByteArray> server_nonce;
    std::unique_ptr<ByteArray> encrypted_data;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_rpc_drop_answer : public TLObject {

public:
    static const uint32_t constructor = 0x58e4a740;

    int64_t req_msg_id;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_get_future_salts : public TLObject {

public:
    static const uint32_t constructor = 0xb921bd04;

    int32_t num;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_ping : public TLObject {

public:
    static const uint32_t constructor = 0x7abe77ec;

    int64_t ping_id;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_ping_delay_disconnect : public TLObject {

public:
    static const uint32_t constructor = 0xf3427b8c;

    int64_t ping_id;
    int32_t disconnect_delay;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_destroy_session : public TLObject {

public:
    static const uint32_t constructor = 0xe7512126;

    int64_t session_id;

    TLObject *deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_gzip_packed : public TLObject {

public:
    static const uint32_t constructor = 0x3072cfa1;

    NativeByteBuffer *packed_data_to_send = nullptr;
    std::unique_ptr<NativeByteBuffer> packed_data;
    std::unique_ptr<TLObject> originalRequest;

    ~TL_gzip_packed();
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_error : public TLObject {

public:
    static const uint32_t constructor = 0xc4b9f9bb;

    int32_t code;
    std::string text;

    static TL_error *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_invokeAfterMsg : public TLObject {

public:
    static const uint32_t constructor = 0xcb9f372d;

    int64_t msg_id;
    TLObject *outgoingQuery = nullptr;
    std::unique_ptr<TLObject> query;

    void serializeToStream(NativeByteBuffer *stream);
};

class invokeWithLayer : public TLObject {

public:
    static const uint32_t constructor = 0xda9b0d0d;

    int32_t layer;
    std::unique_ptr<TLObject> query;

    void serializeToStream(NativeByteBuffer *stream);
};

class initConnection : public TLObject {

public:
    static const uint32_t constructor = 0x69796de9;

    int32_t api_id;
    std::string device_model;
    std::string system_version;
    std::string app_version;
    std::string lang_code;
    std::unique_ptr<TLObject> query;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_dcOption : public TLObject {

public:
    static const uint32_t constructor = 0x5d8c6cc;

    int32_t flags;
    int32_t id;
    std::string ip_address;
    int32_t port;

    static TL_dcOption *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
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
    static const uint32_t constructor = 0x317ceef4;

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

class TL_account_registerDevice : public TLObject {

public:
    static const uint32_t constructor = 0x446c712c;

    int32_t token_type;
    std::string token;
    std::string device_model;
    std::string system_version;
    std::string app_version;
    bool app_sandbox;
    std::string lang_code;

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

    static FileLocation *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
};

class TL_fileLocation : public FileLocation {

public:
    static const uint32_t constructor = 0x53d69076;

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
    static const uint32_t constructor = 0xd10d979a;

    void readParams(NativeByteBuffer *stream, bool &error);
    void serializeToStream(NativeByteBuffer *stream);
};

class TL_auth_authorization : public TLObject {

public:
    static const uint32_t constructor = 0xff036af1;

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

#endif
