#pragma once

#include <algorithm>
#include <bit>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <exception>
#include <functional>
#include <iostream>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <streambuf>
#include <type_traits>
#include <variant>
#include <vector>

#include "gram_wallet_scaffolding.hpp"

#ifndef UNIFFI_CPP_RUST_STREAM
#define UNIFFI_CPP_RUST_STREAM
namespace uniffi {
struct RustStreamBuffer: std::basic_streambuf<char> {
    RustStreamBuffer(RustBuffer *buf) {
        char* data = reinterpret_cast<char*>(buf->data);
        this->setg(data, data, data + buf->len);
        this->setp(data, data + buf->capacity);
    }
    ~RustStreamBuffer() = default;

private:
    RustStreamBuffer() = delete;
    RustStreamBuffer(const RustStreamBuffer &) = delete;
    RustStreamBuffer(RustStreamBuffer &&) = delete;

    RustStreamBuffer &operator=(const RustStreamBuffer &) = delete;
    RustStreamBuffer &operator=(RustStreamBuffer &&) = delete;
};

struct RustStream: std::basic_iostream<char> {
    RustStream(RustBuffer *buf):
        std::basic_iostream<char>(&streambuf), streambuf(RustStreamBuffer(buf)) { }

    template <typename T, typename = std::enable_if_t<std::is_arithmetic_v<T>>>
    RustStream &operator>>(T &val) {
        read(reinterpret_cast<char *>(&val), sizeof(T));

        if (std::endian::native != std::endian::big) {
            auto bytes = reinterpret_cast<char *>(&val);

            std::reverse(bytes, bytes + sizeof(T));
        }

        return *this;
    }

    template <typename T, typename = std::enable_if_t<std::is_arithmetic_v<T>>>
    RustStream &operator<<(T val) {
        if (std::endian::native != std::endian::big) {
            auto bytes = reinterpret_cast<char *>(&val);

            std::reverse(bytes, bytes + sizeof(T));
        }

        write(reinterpret_cast<char *>(&val), sizeof(T));

        return *this;
    }
private:
    RustStreamBuffer streambuf;
};

}
#endif

namespace gram_wallet {
struct GramWalletError;

namespace uniffi {
struct FfiConverterGramWalletError;
} // namespace uniffi

struct GramWalletError: std::runtime_error {
    friend uniffi::FfiConverterGramWalletError;

    GramWalletError() : std::runtime_error("") {}
    GramWalletError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~GramWalletError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of GramWalletError
 */
namespace gram_wallet_error {

struct InvalidData: GramWalletError {

    InvalidData() : GramWalletError("") {}
    InvalidData(const std::string &what_arg) : GramWalletError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};

struct Protocol: GramWalletError {
    std::string message;

    Protocol() : GramWalletError("") {}
    Protocol(const std::string &what_arg) : GramWalletError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 2;
    }
};
} // namespace gram_wallet_error

namespace uniffi {
using ::uniffi::RustStream;
using ::uniffi::RustStreamBuffer;

RustBuffer rustbuffer_alloc(uint64_t);
RustBuffer rustbuffer_from_bytes(const ForeignBytes &);
void rustbuffer_free(RustBuffer);
template <typename T> struct HandleMap {
    HandleMap() = default;

    std::shared_ptr<T> at(uint64_t handle) {
        std::lock_guard<std::mutex> guard(this->mutex);

        return this->map.at(handle);
    }

    uint64_t insert(std::shared_ptr<T> impl) {
        std::lock_guard<std::mutex> guard(this->mutex);

        auto handle = this->cur_handle;

        this->map.insert({ handle, impl });
        this->cur_handle += 1;

        return handle;
    }

    void erase(uint64_t handle) {
        // We store the object here to avoid re-entrant locking
        std::shared_ptr<T> cleanup;
        {
            std::lock_guard<std::mutex> guard(this->mutex);
            auto it = this->map.find(handle);
            if (it != this->map.end()) {
                cleanup = it->second;
                this->map.erase(it);
            }
        }
    }
    private:
        HandleMap(const HandleMap<T> &) = delete;
        HandleMap(HandleMap<T> &&) = delete;

        HandleMap<T> &operator=(const HandleMap<T> &) = delete;
        HandleMap<T> &operator=(HandleMap<T> &&) = delete;

        std::mutex mutex;
        uint64_t cur_handle = 0;
        std::map<uint64_t, std::shared_ptr<T>> map;
};
struct FfiConverterBool {
    static bool lift(uint8_t);
    static uint8_t lower(bool);
    static bool read(RustStream &);
    static void write(RustStream &, bool);
    static uint64_t allocation_size(bool);
};
struct FfiConverterString {
    static std::string lift(RustBuffer buf);
    static RustBuffer lower(const std::string &);
    static std::string read(RustStream &);
    static void write(RustStream &, const std::string &);
    static uint64_t allocation_size(const std::string &);
};

struct FfiConverterGramWalletError {
    static std::shared_ptr<GramWalletError> lift(RustBuffer buf);
    static RustBuffer lower(const GramWalletError &);
    static std::shared_ptr<GramWalletError> read(RustStream &stream);
    static void write(RustStream &stream, const GramWalletError &);
    static uint64_t allocation_size(const GramWalletError &);
};
} // namespace uniffi

/**
 * Validates the single-root BoC envelope before passing untrusted cells to the wallet.
 */
bool is_valid_cell_boc(const std::string &boc);
/**
 * Hash of the message body, as used by the MTProto transaction matcher.
 * This deliberately differs from the hash of the complete external message.
 */
std::string message_body_hash(const std::string &boc);
/**
 * Stateless cryptographic adapter. Java owns TON Connect envelopes, methods,
 * capabilities and MTProto workflow; secret keys never leave this call.
 */
std::string gram_ton_connect_crypto(const std::string &phrase, const std::string &parameters);
} // namespace gram_wallet