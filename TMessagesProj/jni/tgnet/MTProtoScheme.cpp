/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#include <memory.h>
#include "MTProtoScheme.h"
#include "FileLog.h"
#include "ByteArray.h"
#include "NativeByteBuffer.h"
#include "BuffersStorage.h"
#include "ConnectionsManager.h"

TLObject *TLClassStore::TLdeserialize(NativeByteBuffer *stream, uint32_t bytes, uint32_t constructor, bool &error) {
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
        case TL_rpc_result::constructor:
            object = new TL_rpc_result();
            ((TL_rpc_result *) object)->readParamsEx(stream, bytes, error);
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
    object->readParams(stream, error);
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

TL_future_salt *TL_future_salt::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_future_salt::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_future_salt", constructor);
        return nullptr;
    }
    TL_future_salt *result = new TL_future_salt();
    result->readParams(stream, error);
    return result;
}

void TL_future_salt::readParams(NativeByteBuffer *stream, bool &error) {
    valid_since = stream->readInt32(&error);
    valid_until = stream->readInt32(&error);
    salt = stream->readInt64(&error);
}

TL_msgs_state_info *TL_msgs_state_info::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msgs_state_info::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_state_info", constructor);
        return nullptr;
    }
    TL_msgs_state_info *result = new TL_msgs_state_info();
    result->readParams(stream, error);
    return result;
}

void TL_msgs_state_info::readParams(NativeByteBuffer *stream, bool &error) {
    req_msg_id = stream->readInt64(&error);
    info = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

void TL_msgs_state_info::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(req_msg_id);
    stream->writeByteArray(info.get());
}

Server_DH_Params *Server_DH_Params::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_server_DH_params_fail::readParams(NativeByteBuffer *stream, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_server_DH_params_ok::readParams(NativeByteBuffer *stream, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    encrypted_answer = std::unique_ptr<ByteArray>(stream->readByteArray(&error));
}

TL_resPQ *TL_resPQ::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_resPQ::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_resPQ", constructor);
        return nullptr;
    }
    TL_resPQ *result = new TL_resPQ();
    result->readParams(stream, error);
    return result;
}

void TL_resPQ::readParams(NativeByteBuffer *stream, bool &error) {
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

TL_pong *TL_pong::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_pong::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_pong", constructor);
        return nullptr;
    }
    TL_pong *result = new TL_pong();
    result->readParams(stream, error);
    return result;
}

void TL_pong::readParams(NativeByteBuffer *stream, bool &error) {
    msg_id = stream->readInt64(&error);
    ping_id = stream->readInt64(&error);
}

TL_future_salts *TL_future_salts::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_future_salts::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_future_salts", constructor);
        return nullptr;
    }
    TL_future_salts *result = new TL_future_salts();
    result->readParams(stream, error);
    return result;
}

void TL_future_salts::readParams(NativeByteBuffer *stream, bool &error) {
    req_msg_id = stream->readInt64(&error);
    now = stream->readInt32(&error);
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        TL_future_salt *object = new TL_future_salt();
        object->readParams(stream, error);
        if (error) {
            return;
        }
        salts.push_back(std::unique_ptr<TL_future_salt>(object));
    }
}

RpcDropAnswer *RpcDropAnswer::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_rpc_answer_unknown::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

void TL_rpc_answer_dropped::readParams(NativeByteBuffer *stream, bool &error) {
    msg_id = stream->readInt64(&error);
    seq_no = stream->readInt32(&error);
    bytes = stream->readInt32(&error);
}

void TL_rpc_answer_dropped_running::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
}

Set_client_DH_params_answer *Set_client_DH_params_answer::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_dh_gen_retry::readParams(NativeByteBuffer *stream, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash2 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_dh_gen_fail::readParams(NativeByteBuffer *stream, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash3 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

void TL_dh_gen_ok::readParams(NativeByteBuffer *stream, bool &error) {
    nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    server_nonce = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
    new_nonce_hash1 = std::unique_ptr<ByteArray>(stream->readBytes(16, &error));
}

BadMsgNotification *BadMsgNotification::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_bad_msg_notification::readParams(NativeByteBuffer *stream, bool &error) {
    bad_msg_id = stream->readInt64(&error);
    bad_msg_seqno = stream->readInt32(&error);
    error_code = stream->readInt32(&error);
}

void TL_bad_server_salt::readParams(NativeByteBuffer *stream, bool &error) {
    bad_msg_id = stream->readInt64(&error);
    bad_msg_seqno = stream->readInt32(&error);
    error_code = stream->readInt32(&error);
    new_server_salt = stream->readInt64(&error);
}

TL_msgs_state_req *TL_msgs_state_req::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msgs_state_req::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_state_req", constructor);
        return nullptr;
    }
    TL_msgs_state_req *result = new TL_msgs_state_req();
    result->readParams(stream, error);
    return result;
}

void TL_msgs_state_req::readParams(NativeByteBuffer *stream, bool &error) {
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

MsgDetailedInfo *MsgDetailedInfo::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_msg_new_detailed_info::readParams(NativeByteBuffer *stream, bool &error) {
    answer_msg_id = stream->readInt64(&error);
    bytes = stream->readInt32(&error);
    status = stream->readInt32(&error);
}

void TL_msg_detailed_info::readParams(NativeByteBuffer *stream, bool &error) {
    msg_id = stream->readInt64(&error);
    answer_msg_id = stream->readInt64(&error);
    bytes = stream->readInt32(&error);
    status = stream->readInt32(&error);
}

TL_msg_copy *TL_msg_copy::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msg_copy::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_copy", constructor);
        return nullptr;
    }
    TL_msg_copy *result = new TL_msg_copy();
    result->readParams(stream, error);
    return result;
}

void TL_msg_copy::readParams(NativeByteBuffer *stream, bool &error) {
    orig_message = std::unique_ptr<TL_message>(TL_message::TLdeserialize(stream, stream->readUint32(&error), error));
}

void TL_msg_copy::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    orig_message->serializeToStream(stream);
}

TL_msgs_all_info *TL_msgs_all_info::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msgs_all_info::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_all_info", constructor);
        return nullptr;
    }
    TL_msgs_all_info *result = new TL_msgs_all_info();
    result->readParams(stream, error);
    return result;
}

void TL_msgs_all_info::readParams(NativeByteBuffer *stream, bool &error) {
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

void TL_rpc_result::readParamsEx(NativeByteBuffer *stream, uint32_t bytes, bool &error) {
    req_msg_id = stream->readInt64(&error);
    TLObject *object = ConnectionsManager::getInstance().TLdeserialize(ConnectionsManager::getInstance().getRequestWithMessageId(req_msg_id), bytes - 12, stream);
    if (object != nullptr) {
        result = std::unique_ptr<TLObject>(object);
    } else {
        error = true;
    }
}

void TL_new_session_created::readParams(NativeByteBuffer *stream, bool &error) {
    first_msg_id = stream->readInt64(&error);
    unique_id = stream->readInt64(&error);
    server_salt = stream->readInt64(&error);
}

DestroySessionRes *DestroySessionRes::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
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
    result->readParams(stream, error);
    return result;
}

void TL_destroy_session_ok::readParams(NativeByteBuffer *stream, bool &error) {
    session_id = stream->readInt64(&error);
}

void TL_destroy_session_none::readParams(NativeByteBuffer *stream, bool &error) {
    session_id = stream->readInt64(&error);
}

TL_msgs_ack *TL_msgs_ack::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msgs_ack::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msgs_ack", constructor);
        return nullptr;
    }
    TL_msgs_ack *result = new TL_msgs_ack();
    result->readParams(stream, error);
    return result;
}

void TL_msgs_ack::readParams(NativeByteBuffer *stream, bool &error) {
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

TL_msg_container *TL_msg_container::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msg_container::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_container", constructor);
        return nullptr;
    }
    TL_msg_container *result = new TL_msg_container();
    result->readParams(stream, error);
    return result;
}

void TL_msg_container::readParams(NativeByteBuffer *stream, bool &error) {
    uint32_t count = stream->readUint32(&error);
    for (uint32_t a = 0; a < count; a++) {
        TL_message *object = new TL_message();
        object->readParams(stream, error);
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

TL_message *TL_message::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_message::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_message", constructor);
        return nullptr;
    }
    TL_message *result = new TL_message();
    result->readParams(stream, error);
    return result;
}

void TL_message::readParams(NativeByteBuffer *stream, bool &error) {
    msg_id = stream->readInt64(&error);
    seqno = stream->readInt32(&error);
    bytes = stream->readInt32(&error);
    TLObject *object = ConnectionsManager::getInstance().TLdeserialize(nullptr, (uint32_t) bytes, stream);
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

TL_msg_resend_req *TL_msg_resend_req::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_msg_resend_req::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_msg_resend_req", constructor);
        return nullptr;
    }
    TL_msg_resend_req *result = new TL_msg_resend_req();
    result->readParams(stream, error);
    return result;
}

void TL_msg_resend_req::readParams(NativeByteBuffer *stream, bool &error) {
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

void TL_rpc_error::readParams(NativeByteBuffer *stream, bool &error) {
    error_code = stream->readInt32(&error);
    error_message = stream->readString(&error);
}

void TL_rpc_req_error::readParams(NativeByteBuffer *stream, bool &error) {
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

TL_server_DH_inner_data *TL_server_DH_inner_data::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_server_DH_inner_data::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_server_DH_inner_data", constructor);
        return nullptr;
    }
    TL_server_DH_inner_data *result = new TL_server_DH_inner_data();
    result->readParams(stream, error);
    return result;
}

void TL_server_DH_inner_data::readParams(NativeByteBuffer *stream, bool &error) {
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

TLObject *TL_req_pq::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_resPQ::TLdeserialize(stream, constructor, error);
}

void TL_req_pq::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
}

TLObject *TL_req_DH_params::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return Server_DH_Params::TLdeserialize(stream, constructor, error);
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

TLObject *TL_set_client_DH_params::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return Set_client_DH_params_answer::TLdeserialize(stream, constructor, error);
}

void TL_set_client_DH_params::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeBytes(nonce.get());
    stream->writeBytes(server_nonce.get());
    stream->writeByteArray(encrypted_data.get());
}

TLObject *TL_rpc_drop_answer::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return RpcDropAnswer::TLdeserialize(stream, constructor, error);
}

void TL_rpc_drop_answer::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(req_msg_id);
}

TLObject *TL_get_future_salts::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_future_salts::TLdeserialize(stream, constructor, error);
}

void TL_get_future_salts::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(num);
}

TLObject *TL_ping::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_pong::TLdeserialize(stream, constructor, error);
}

void TL_ping::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(ping_id);
}

TLObject *TL_ping_delay_disconnect::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return TL_pong::TLdeserialize(stream, constructor, error);
}

void TL_ping_delay_disconnect::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt64(ping_id);
    stream->writeInt32(disconnect_delay);
}

TLObject *TL_destroy_session::deserializeResponse(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    return DestroySessionRes::TLdeserialize(stream, constructor, error);
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

void TL_gzip_packed::readParams(NativeByteBuffer *stream, bool &error) {
    packed_data = std::unique_ptr<NativeByteBuffer>(stream->readByteBuffer(false, &error));
}

void TL_gzip_packed::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeByteArray(packed_data_to_send);
}

TL_error *TL_error::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    if (TL_error::constructor != constructor) {
        error = true;
        DEBUG_E("can't parse magic %x in TL_error", constructor);
        return nullptr;
    }
    TL_error *result = new TL_error();
    result->readParams(stream, error);
    return result;
}

void TL_error::readParams(NativeByteBuffer *stream, bool &error) {
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

void initConnection::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(api_id);
    stream->writeString(device_model);
    stream->writeString(system_version);
    stream->writeString(app_version);
    stream->writeString(lang_code);
    query->serializeToStream(stream);
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
    id = stream->readInt32(&error);
    ip_address = stream->readString(&error);
    port = stream->readInt32(&error);
}

void TL_dcOption::serializeToStream(NativeByteBuffer *stream) {
    stream->writeInt32(constructor);
    stream->writeInt32(flags);
    stream->writeInt32(id);
    stream->writeString(ip_address);
    stream->writeInt32(port);
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
    stream->writeString(device_model);
    stream->writeString(system_version);
    stream->writeString(app_version);
    stream->writeBool(app_sandbox);
    stream->writeString(lang_code);
}

User *User::TLdeserialize(NativeByteBuffer *stream, uint32_t constructor, bool &error) {
    User *result = nullptr;
    switch (constructor) {
        case 0x200250ba:
            result = new TL_userEmpty();
            break;
        case 0xd10d979a:
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
