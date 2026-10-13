#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef UNIFFI_CPP_INTERNALSTRUCTS
#define UNIFFI_CPP_INTERNALSTRUCTS
struct ForeignBytes {
    int32_t len;
    uint8_t *data;
};

struct RustBuffer {
    uint64_t capacity;
    uint64_t len;
    uint8_t *data;
};

struct RustCallStatus {
    int8_t code;
    RustBuffer error_buf;
};

#endif
void RustFutureContinuationCallback(
    uint64_t data,int8_t poll_result
);
void ForeignFutureDroppedCallback(
    uint64_t handle
);
void CallbackInterfaceFree(
    uint64_t handle
);
uint64_t CallbackInterfaceClone(
    uint64_t handle
);
struct UniffiForeignFutureDroppedCallbackStruct {
    uint64_t handle;
    void * free;
};
struct UniffiForeignFutureResultU8 {
    uint8_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteU8(
    uint64_t callback_data,UniffiForeignFutureResultU8 result
);
struct UniffiForeignFutureResultI8 {
    int8_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteI8(
    uint64_t callback_data,UniffiForeignFutureResultI8 result
);
struct UniffiForeignFutureResultU16 {
    uint16_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteU16(
    uint64_t callback_data,UniffiForeignFutureResultU16 result
);
struct UniffiForeignFutureResultI16 {
    int16_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteI16(
    uint64_t callback_data,UniffiForeignFutureResultI16 result
);
struct UniffiForeignFutureResultU32 {
    uint32_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteU32(
    uint64_t callback_data,UniffiForeignFutureResultU32 result
);
struct UniffiForeignFutureResultI32 {
    int32_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteI32(
    uint64_t callback_data,UniffiForeignFutureResultI32 result
);
struct UniffiForeignFutureResultU64 {
    uint64_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteU64(
    uint64_t callback_data,UniffiForeignFutureResultU64 result
);
struct UniffiForeignFutureResultI64 {
    int64_t return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteI64(
    uint64_t callback_data,UniffiForeignFutureResultI64 result
);
struct UniffiForeignFutureResultF32 {
    float return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteF32(
    uint64_t callback_data,UniffiForeignFutureResultF32 result
);
struct UniffiForeignFutureResultF64 {
    double return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteF64(
    uint64_t callback_data,UniffiForeignFutureResultF64 result
);
struct UniffiForeignFutureResultRustBuffer {
    RustBuffer return_value;
    RustCallStatus call_status;
};
void ForeignFutureCompleteRustBuffer(
    uint64_t callback_data,UniffiForeignFutureResultRustBuffer result
);
struct UniffiForeignFutureResultVoid {
    RustCallStatus call_status;
};
void ForeignFutureCompleteVoid(
    uint64_t callback_data,UniffiForeignFutureResultVoid result
);
int8_t uniffi_gram_wallet_fn_func_is_valid_cell_boc(RustBuffer boc, RustCallStatus *out_status);
RustBuffer uniffi_gram_wallet_fn_func_message_body_hash(RustBuffer boc, RustCallStatus *out_status);
RustBuffer uniffi_gram_wallet_fn_func_gram_ton_connect_crypto(RustBuffer phrase, RustBuffer parameters, RustCallStatus *out_status);
RustBuffer ffi_gram_wallet_rustbuffer_alloc(uint64_t size, RustCallStatus *out_status);
RustBuffer ffi_gram_wallet_rustbuffer_from_bytes(ForeignBytes bytes, RustCallStatus *out_status);
void ffi_gram_wallet_rustbuffer_free(RustBuffer buf, RustCallStatus *out_status);
RustBuffer ffi_gram_wallet_rustbuffer_reserve(RustBuffer buf, uint64_t additional, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_u8(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_u8(uint64_t handle);
void ffi_gram_wallet_rust_future_free_u8(uint64_t handle);
uint8_t ffi_gram_wallet_rust_future_complete_u8(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_i8(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_i8(uint64_t handle);
void ffi_gram_wallet_rust_future_free_i8(uint64_t handle);
int8_t ffi_gram_wallet_rust_future_complete_i8(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_u16(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_u16(uint64_t handle);
void ffi_gram_wallet_rust_future_free_u16(uint64_t handle);
uint16_t ffi_gram_wallet_rust_future_complete_u16(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_i16(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_i16(uint64_t handle);
void ffi_gram_wallet_rust_future_free_i16(uint64_t handle);
int16_t ffi_gram_wallet_rust_future_complete_i16(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_u32(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_u32(uint64_t handle);
void ffi_gram_wallet_rust_future_free_u32(uint64_t handle);
uint32_t ffi_gram_wallet_rust_future_complete_u32(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_i32(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_i32(uint64_t handle);
void ffi_gram_wallet_rust_future_free_i32(uint64_t handle);
int32_t ffi_gram_wallet_rust_future_complete_i32(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_u64(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_u64(uint64_t handle);
void ffi_gram_wallet_rust_future_free_u64(uint64_t handle);
uint64_t ffi_gram_wallet_rust_future_complete_u64(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_i64(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_i64(uint64_t handle);
void ffi_gram_wallet_rust_future_free_i64(uint64_t handle);
int64_t ffi_gram_wallet_rust_future_complete_i64(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_f32(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_f32(uint64_t handle);
void ffi_gram_wallet_rust_future_free_f32(uint64_t handle);
float ffi_gram_wallet_rust_future_complete_f32(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_f64(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_f64(uint64_t handle);
void ffi_gram_wallet_rust_future_free_f64(uint64_t handle);
double ffi_gram_wallet_rust_future_complete_f64(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_rust_buffer(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_rust_buffer(uint64_t handle);
void ffi_gram_wallet_rust_future_free_rust_buffer(uint64_t handle);
RustBuffer ffi_gram_wallet_rust_future_complete_rust_buffer(uint64_t handle, RustCallStatus *out_status);
void ffi_gram_wallet_rust_future_poll_void(uint64_t handle, void * callback, uint64_t callback_data);
void ffi_gram_wallet_rust_future_cancel_void(uint64_t handle);
void ffi_gram_wallet_rust_future_free_void(uint64_t handle);
void ffi_gram_wallet_rust_future_complete_void(uint64_t handle, RustCallStatus *out_status);
uint16_t uniffi_gram_wallet_checksum_func_is_valid_cell_boc();
uint16_t uniffi_gram_wallet_checksum_func_message_body_hash();
uint16_t uniffi_gram_wallet_checksum_func_gram_ton_connect_crypto();
uint32_t ffi_gram_wallet_uniffi_contract_version();
#ifdef __cplusplus
}
#endif