/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include <memory.h>
#include <arpa/inet.h>
#include "MTProtoScheme.h"
#include "ApiScheme.h"
#include "FileLog.h"
#include "ByteArray.h"
#include "NativeByteBuffer.h"
#include "BuffersStorage.h"
#include "ConnectionsManager.h"

TLObject *TLClassStore::TLdeserialize(NativeByteBuffer *stream, uint32_t bytes, uint32_t constructor, int32_t instanceNum, bool &error) {
    TLObject *object = nullptr;
    switch (constructor) {
        case TL_msgs_ack::constructor:
            object = new TL_msgs_ack();
            break;
        case TL_msg_container::constructor:
            object = new TL_msg_container();
            break;
        case TL_pong::constructor:
            object = new TL_pong();
            break;
        case TL_new_session_created::constructor:
            object = new TL_new_session_created();
            break;
        case MsgsStateInfo::constructor:
            object = new MsgsStateInfo();
            break;
        case TL_rpc_result::constructor:
            object = new TL_rpc_result();
            ((TL_rpc_result *) object)->readParamsEx(stream, bytes, instanceNum, error);
            return object;
        case TL_bad_msg_notification::constructor:
            object = new TL_bad_msg_notification();
            break;
        case TL_bad_server_salt::constructor:
            object = new TL_bad_server_salt();
            break;
        case TL_msg_detailed_info::constructor:
            object = new TL_msg_detailed_info();
            break;
        case TL_msg_new_detailed_info::constructor:
            object = new TL_msg_new_detailed_info();
            break;
        case TL_gzip_packed::constructor:
            object = new TL_gzip_packed();
            break;
        case TL_error::constructor:
            object = new TL_error();
            break;
        case TL_rpc_error::constructor:
            object = new TL_rpc_error();
            break;
        case TL_rpc_req_error::constructor:
            object = new TL_rpc_req_error();
            break;
        case TL_future_salts::constructor:
            object = new TL_future_salts();
            break;
        case TL_destroy_session_ok::constructor:
            object = new TL_destroy_session_ok();
            break;
        case TL_destroy_session_none::constructor:
            object = new TL_destroy_session_none();
            break;
        case TL_updatesTooLong::constructor:
            object = new TL_updatesTooLong();
            break;
        default:
            return nullptr;
    }
    object->readParams(stream, instanceNum, error);
    return object;
}

TL_api_request::~TL_api_request() {
    if (request != nullptr) {
        request->reuse();
        request = nullptr;
    }
}

bool TL_api_request::isNeedLayer() {
    return true;
}

TLObject *TL_api_request::deserializeResponse(NativeByteBuffer *stream, uint32_t bytes, bool &error) {
    TL_api_response *result = new TL_api_response();
    result->readParamsEx(stream, bytes, error);
    return result;
}

void TL_api_request::serializeToStream(NativeByteBuffer *stream) {
    request->rewind();
    stream->writeBytes(request);
}

void TL_api_response::readParamsEx(NativeByteBuffer *stream, uint32_t bytes, bool &error) {
    response = std::unique_ptr<NativeByteBuffer>(new NativeByteBuffer(stream->bytes() + stream->position() - 4, bytes));
    stream->skip((uint32_t) (bytes - 4));
}

TL_future_salt *TL_future_salt::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_future_salt::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_future_salt", constructor);
        return nullptr;
    }
    TL_future_salt *result = new TL_future_salt();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_future_salt::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    valid_since = stream->readInt32(&error);
    valid_until = stream->readInt32(&error);
    salt = stream->readInt64(&error);
}

TL_msgs_state_info *TL_msgs_state_info::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msgs_state_info::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_state_info", constructor);
        return nullptr;
    }
    TL_msgs_state_info *result = new TL_msgs_state_info();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msgs_state_info::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    req_msg_id = stream->readInt64(&error);
    info = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

void TL_msgs_state_info::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(req_msg_id);
    stream->writeByteArray(info.get());
}

Server_DH_Params *Server_DH_Params::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    Server_DH_Params *result = nullptr;
    switch (constructor) {
        case 0x79cb045d:
            result = new TL_server_DH_params_fail();
            break;
        case 0xd0e8075c:
            result = new TL_server_DH_params_ok();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in Server_DH_Params", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_server_DH_params_fail::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_server_DH_params_ok::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    encrypted_answer = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

TL_resPQ *TL_resPQ::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_resPQ::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_resPQ", constructor);
        return nullptr;
    }
    TL_resPQ *result = new TL_resPQ();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_resPQ::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    pq = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    uint32_t count = stream->readUint32(&error);
    if (count * sizeof(int64_t) + stream->position() > stream->limit()) {
        error = true;
        return;
    }
    for (uint32_t a = 0; a < count; a++) {
        server_public_key_fingerprints.push_back(stream->readInt64(&error));
    }
}

void TL_p_q_inner_data::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(pq.get());
    stream->writeByteArray(p.get());
    stream->writeByteArray(q.get());
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeBytes(new_nonce.get());
}

void TL_p_q_inner_data_dc::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(pq.get());
    stream->writeByteArray(p.get());
    stream->writeByteArray(q.get());
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeBytes(new_nonce.get());
    stream->writeInt32(dc);
}

void TL_p_q_inner_data_temp::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(pq.get());
    stream->writeByteArray(p.get());
    stream->writeByteArray(q.get());
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeBytes(new_nonce.get());
    stream->writeInt32(expires_in);
}

void TL_p_q_inner_data_temp_dc::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(pq.get());
    stream->writeByteArray(p.get());
    stream->writeByteArray(q.get());
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeBytes(new_nonce.get());
    stream->writeInt32(dc);
    stream->writeInt32(expires_in);
}

void TL_bind_auth_key_inner::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(nonce);
    stream->writeInt64(temp_auth_key_id);
    stream->writeInt64(perm_auth_key_id);
    stream->writeInt64(temp_session_id);
    stream->writeInt32(expires_at);
}

TL_auth_bindTempAuthKey::~TL_auth_bindTempAuthKey() {
    if (encrypted_message != nullptr) {
        encrypted_message->reuse();
        encrypted_message = nullptr;
    }
}

TLObject *TL_auth_bindTempAuthKey::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return Bool::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_auth_bindTempAuthKey::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(perm_auth_key_id);
    stream->writeInt64(nonce);
    stream->writeInt32(expires_at);
    stream->writeByteArray(encrypted_message);
}

TLObject *TL_auth_dropTempAuthKeys::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return Bool::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_auth_dropTempAuthKeys::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(0x1cb5c415);
    uint32_t count = (uint32_t) except_auth_keys.size();
    stream->writeInt32(count);
    for (int a = 0; a < count; a++) {
        stream->writeInt64(except_auth_keys[a]);
    }
}

TL_pong *TL_pong::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_pong::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_pong", constructor);
        return nullptr;
    }
    TL_pong *result = new TL_pong();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_pong::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    msg_id = stream->readInt64(&error);
    ping_id = stream->readInt64(&error);
}

TL_future_salts *TL_future_salts::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_future_salts::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_future_salts", constructor);
        return nullptr;
    }
    TL_future_salts *result = new TL_future_salts();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_future_salts::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    req_msg_id = stream->readInt64(&error);
    now = stream->readInt32(&error);
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        TL_future_salt *object = new TL_future_salt();
        object->readParams(stream, instanceNum, error);
        if (error) {
            return;
        }
        salts.push_back(std::unique_ptr<TL_future_salt>(object));
    }
}

RpcDropAnswer *RpcDropAnswer::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    RpcDropAnswer *result = nullptr;
    switch (constructor) {
        case 0x5e2ad36e:
            result = new TL_rpc_answer_unknown();
            break;
        case 0xa43ad8b7:
            result = new TL_rpc_answer_dropped();
            break;
        case 0xcd78e586:
            result = new TL_rpc_answer_dropped_running();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in RpcDropAnswer", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_rpc_answer_unknown::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_rpc_answer_dropped::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    msg_id = stream->readInt64(&error);
    seq_no = stream->readInt32(&error);
    bytes = stream->readInt32(&error);
}

void TL_rpc_answer_dropped_running::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

Set_client_DH_params_answer *Set_client_DH_params_answer::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    Set_client_DH_params_answer *result = nullptr;
    switch (constructor) {
        case 0x46dc1fb9:
            result = new TL_dh_gen_retry();
            break;
        case 0xa69dae02:
            result = new TL_dh_gen_fail();
            break;
        case 0x3bcbf734:
            result = new TL_dh_gen_ok();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in Set_client_DH_params_answer", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_dh_gen_retry::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash2 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_dh_gen_fail::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash3 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_dh_gen_ok::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash1 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

BadMsgNotification *BadMsgNotification::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    BadMsgNotification *result = nullptr;
    switch (constructor) {
        case 0xa7eff811:
            result = new TL_bad_msg_notification();
            break;
        case 0xedab447b:
            result = new TL_bad_server_salt();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in BadMsgNotification", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_bad_msg_notification::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    bad_msg_id = stream->readInt64(&error);
    bad_msg_seqno = stream->readInt32(&error);
    error_code = stream->readInt32(&error);
}

void TL_bad_server_salt::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    bad_msg_id = stream->readInt64(&error);
    bad_msg_seqno = stream->readInt32(&error);
    error_code = stream->readInt32(&error);
    new_server_salt = stream->readInt64(&error);
}

TL_msgs_state_req *TL_msgs_state_req::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msgs_state_req::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_state_req", constructor);
        return nullptr;
    }
    TL_msgs_state_req *result = new TL_msgs_state_req();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msgs_state_req::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    uint32_t count = stream->readUint32(&error);
    if (count * sizeof(int64_t) + stream->position() > stream->limit()) {
        error = true;
        return;
    }
    for (uint32_t a = 0; a < count; a++) {
        msg_ids.push_back(stream->readInt64(&error));
    }
}

void TL_msgs_state_req::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(0x1cb5c415);
    uint32_t count = (uint32_t) msg_ids.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        stream->writeInt64(msg_ids[a]);
    }
}

MsgDetailedInfo *MsgDetailedInfo::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    MsgDetailedInfo *result = nullptr;
    switch (constructor) {
        case 0x809db6df:
            result = new TL_msg_new_detailed_info();
            break;
        case 0x276d3ec6:
            result = new TL_msg_detailed_info();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in MsgDetailedInfo", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msg_new_detailed_info::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    answer_msg_id = stream->readInt64(&error);
    bytes = stream->readInt32(&error);
    status = stream->readInt32(&error);
}

void TL_msg_detailed_info::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    msg_id = stream->readInt64(&error);
    answer_msg_id = stream->readInt64(&error);
    bytes = stream->readInt32(&error);
    status = stream->readInt32(&error);
}

TL_msg_copy *TL_msg_copy::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msg_copy::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_copy", constructor);
        return nullptr;
    }
    TL_msg_copy *result = new TL_msg_copy();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msg_copy::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    orig_message = std::unique_ptr<TL_message>(TL_message::TLdeserialize(stream, stream->readUint32(&error), instanceNum, error));
}

void TL_msg_copy::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    orig_message->serializeToStream(stream);
}

TL_msgs_all_info *TL_msgs_all_info::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msgs_all_info::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_all_info", constructor);
        return nullptr;
    }
    TL_msgs_all_info *result = new TL_msgs_all_info();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msgs_all_info::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    uint32_t count = stream->readUint32(&error);
    if (count * sizeof(int64_t) + stream->position() > stream->limit()) {
        error = true;
        return;
    }
    for (uint32_t a = 0; a < count; a++) {
        msg_ids.push_back(stream->readInt64(&error));
    }
    info = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

void TL_rpc_result::readParamsEx(NativeByteBuffer *stream, uint32_t bytes, int32_t instanceNum, bool &error) {
    req_msg_id = stream->readInt64(&error);
    ConnectionsManager &connectionsManager = ConnectionsManager::getInstance(instanceNum);
    TLObject *object = connectionsManager.TLdeserialize(connectionsManager.getRequestWithMessageId(req_msg_id), bytes - 12, stream);
    if (object != nullptr) {
        result = std::unique_ptr<TLObject>(object);
    } else {
        error = true;
    }
}

void TL_new_session_created::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    first_msg_id = stream->readInt64(&error);
    unique_id = stream->readInt64(&error);
    server_salt = stream->readInt64(&error);
}

DestroySessionRes *DestroySessionRes::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    DestroySessionRes *result = nullptr;
    switch (constructor) {
        case 0xe22045fc:
            result = new TL_destroy_session_ok();
            break;
        case 0x62d350c9:
            result = new TL_destroy_session_none();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in DestroySessionRes", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_destroy_session_ok::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    session_id = stream->readInt64(&error);
}

void TL_destroy_session_none::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    session_id = stream->readInt64(&error);
}

TL_msgs_ack *TL_msgs_ack::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msgs_ack::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_ack", constructor);
        return nullptr;
    }
    TL_msgs_ack *result = new TL_msgs_ack();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msgs_ack::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    uint32_t count = stream->readUint32(&error);
    if (count * sizeof(int64_t) + stream->position() > stream->limit()) {
        error = true;
        return;
    }
    for (uint32_t a = 0; a < count; a++) {
        msg_ids.push_back(stream->readInt64(&error));
    }
}

void TL_msgs_ack::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(0x1cb5c415);
    uint32_t count = (uint32_t) msg_ids.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        stream->writeInt64(msg_ids[a]);
    }
}

TL_msg_container *TL_msg_container::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msg_container::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_container", constructor);
        return nullptr;
    }
    TL_msg_container *result = new TL_msg_container();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msg_container::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        TL_message *object = new TL_message();
        object->readParams(stream, instanceNum, error);
        if (error) {
            return;
        }
        messages.push_back(std::unique_ptr<TL_message>(object));
    }
}

void TL_msg_container::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    uint32_t count = (uint32_t) messages.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        messages[a]->serializeToStream(stream);
    }
}

TL_message *TL_message::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_message::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_message", constructor);
        return nullptr;
    }
    TL_message *result = new TL_message();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_message::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    msg_id = stream->readInt64(&error);
    seqno = stream->readInt32(&error);
    bytes = stream->readInt32(&error);
    TLObject *object = ConnectionsManager::getInstance(instanceNum).TLdeserialize(nullptr, (uint32_t) bytes, stream);
    if (object == nullptr) {
        unparsedBody = std::unique_ptr<NativeByteBuffer>(new NativeByteBuffer(stream->bytes() + stream->position(), (uint32_t) bytes));
        stream->skip((uint32_t) bytes);
    } else {
        body = std::unique_ptr<TLObject>(object);
    }
}

void TL_message::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt64(msg_id);
    stream->writeInt32(seqno);
    stream->writeInt32(bytes);
    if (outgoingBody != nullptr) {
        outgoingBody->serializeToStream(stream);
    } else {
        body->serializeToStream(stream);
    }
}

TL_msg_resend_req *TL_msg_resend_req::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_msg_resend_req::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_resend_req", constructor);
        return nullptr;
    }
    TL_msg_resend_req *result = new TL_msg_resend_req();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_msg_resend_req::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    uint32_t magic = stream->readUint32(&error);
    if (magic != 0x1cb5c415) {
        error = true;
        DEBUG_E("wrong Vector magic, got %x", magic);
        return;
    }
    uint32_t count = stream->readUint32(&error);
    if (count * sizeof(int64_t) + stream->position() > stream->limit()) {
        error = true;
        return;
    }
    for (uint32_t a = 0; a < count; a++) {
        msg_ids.push_back(stream->readInt64(&error));
    }
}

void TL_msg_resend_req::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(0x1cb5c415);
    uint32_t count = (uint32_t) msg_ids.size();
    stream->writeInt32(count);
    for (uint32_t a = 0; a < count; a++) {
        stream->writeInt64(msg_ids[a]);
    }
}

void MsgsStateInfo::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    req_msg_id = stream->readInt64(&error);
    info = stream->readString(&error);
}

void TL_rpc_error::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    error_code = stream->readInt32(&error);
    error_message = stream->readString(&error);
}

void TL_rpc_req_error::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    query_id = stream->readInt64(&error);
    error_code = stream->readInt32(&error);
    error_message = stream->readString(&error);
}

void TL_client_DH_inner_data::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeInt64(retry_id);
    stream->writeByteArray(g_b.get());
}

TL_server_DH_inner_data *TL_server_DH_inner_data::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_server_DH_inner_data::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_server_DH_inner_data", constructor);
        return nullptr;
    }
    TL_server_DH_inner_data *result = new TL_server_DH_inner_data();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_server_DH_inner_data::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    g = stream->readUint32(&error);
    dh_prime = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
    g_a = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
    server_time = stream->readInt32(&error);
}

void TL_server_DH_inner_data::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeInt32(g);
    stream->writeByteArray(dh_prime.get());
    stream->writeByteArray(g_a.get());
    stream->writeInt32(server_time);
}

TLObject *TL_req_pq::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return TL_resPQ::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_req_pq::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
}

TLObject *TL_req_pq_multi::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return TL_resPQ::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_req_pq_multi::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
}

TLObject *TL_req_DH_params::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return Server_DH_Params::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_req_DH_params::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeByteArray(p.get());
    stream->writeByteArray(q.get());
    stream->writeInt64(public_key_fingerprint);
    stream->writeByteArray(encrypted_data.get());
}

TLObject *TL_set_client_DH_params::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return Set_client_DH_params_answer::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_set_client_DH_params::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeByteArray(encrypted_data.get());
}

TLObject *TL_rpc_drop_answer::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return RpcDropAnswer::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_rpc_drop_answer::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(req_msg_id);
}

TLObject *TL_get_future_salts::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return TL_future_salts::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_get_future_salts::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(num);
}

TLObject *TL_ping::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return TL_pong::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_ping::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(ping_id);
}

TLObject *TL_ping_delay_disconnect::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return TL_pong::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_ping_delay_disconnect::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(ping_id);
    stream->writeInt32(disconnect_delay);
}

TLObject *TL_destroy_session::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    return DestroySessionRes::TLdeserialize(stream, constructor, instanceNum, error);
}

void TL_destroy_session::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(session_id);
}

TL_gzip_packed::~TL_gzip_packed() {
    if (packed_data_to_send != nullptr) {
        packed_data_to_send->reuse();
        packed_data_to_send = nullptr;
    }
}

void TL_gzip_packed::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    packed_data = std::unique_ptr<NativeByteBuffer>(stream->readByteBuffer(false, &error));
}

void TL_gzip_packed::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(packed_data_to_send);
}

TL_error *TL_error::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_error::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_error", constructor);
        return nullptr;
    }
    TL_error *result = new TL_error();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_error::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    code = stream->readInt32(&error);
    text = stream->readString(&error);
}

void TL_error::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(code);
    stream->writeString(text);
}

void TL_invokeAfterMsg::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(msg_id);
    if (outgoingQuery != nullptr) {
        outgoingQuery->serializeToStream(stream);
    } else {
        query->serializeToStream(stream);
    }
}

void invokeWithLayer::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(layer);
    query->serializeToStream(stream);
}

void TL_inputClientProxy::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeString(address);
    stream->writeInt32(port);
}

void initConnection::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(flags);
    stream->writeInt32(api_id);
    stream->writeString(device_model);
    stream->writeString(system_version);
    stream->writeString(app_version);
    stream->writeString(system_lang_code);
    stream->writeString(lang_pack);
    stream->writeString(lang_code);
    if ((flags & 1) != 0) {
        proxy->serializeToStream(stream);
    }
    query->serializeToStream(stream);
}

IpPort *IpPort::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    IpPort *result = nullptr;
    switch (constructor) {
        case 0xd433ad73:
            result = new TL_ipPort();
            break;
        case 0x37982646:
            result = new TL_ipPortSecret();
            break;
        default:
            error = true;
            DEBUG_E("can't parse magic %x in IpPort", constructor);
            return nullptr;
    }
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_ipPort::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    struct in_addr ip_addr;
    ip_addr.s_addr = htonl(stream->readUint32(&error));
    ipv4 = inet_ntoa(ip_addr);
    port = stream->readUint32(&error);
}

void TL_ipPortSecret::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    struct in_addr ip_addr;
    ip_addr.s_addr = htonl(stream->readUint32(&error));
    ipv4 = inet_ntoa(ip_addr);
    port = stream->readUint32(&error);
    secret = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

void TL_accessPointRule::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    phone_prefix_rules = stream->readString(&error);
    dc_id = stream->readUint32(&error);
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        IpPort *object = IpPort::TLdeserialize(stream, stream->readUint32(&error), instanceNum, error);
        if (object == nullptr) {
            return;
        }
        ips.push_back(std::unique_ptr<IpPort>(object));
    }
}

TL_help_configSimple *TL_help_configSimple::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, int32_t instanceNum, bool &error) {
    if (TL_help_configSimple::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_help_configSimple", constructor);
        return nullptr;
    }
    TL_help_configSimple *result = new TL_help_configSimple();
    result->readParams(stream, instanceNum, error);
    return result;
}

void TL_help_configSimple::readParams(NativeByteBuffer *stream, int32_t instanceNum, bool &error) {
    date = stream->readInt32(&error);
    expires = stream->readInt32(&error);
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        TL_accessPointRule *object = new TL_accessPointRule();
        object->readParams(stream, stream->readUint32(&error), error);
        if (error) {
            return;
        }
        rules.push_back(std::unique_ptr<TL_accessPointRule>(object));
    }
}
