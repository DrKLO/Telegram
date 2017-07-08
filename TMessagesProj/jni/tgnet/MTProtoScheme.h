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
    static const uint32_t constructor = 0xc7481da6;

    int32_t api_id;
    std::string device_model;
    std::string system_version;
    std::string app_version;
    std::string system_lang_code;
    std::string lang_pack;
    std::string lang_code;
    std::unique_ptr<TLObject> query;

    void serializeToStream(NativeByteBuffer *stream);
};

class TL_ipPort : public TLObject {

public:
    std::string ipv4;
    uint32_t port;

    void readParams(NativeByteBuffer *stream, bool &error);
};

class TL_help_configSimple : public TLObject {

public:
    static const uint32_t constructor = 0xd997c3c5;

    int32_t date;
    int32_t expires;
    uint32_t dc_id;
    std::vector<std::unique_ptr<TL_ipPort>> ip_port_list;

    static TL_help_configSimple *TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error);
    void readParams(NativeByteBuffer *stream, bool &error);
};

#endif
