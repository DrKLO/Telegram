//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2025
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
#pragma once

#include <string_view>

namespace tde2e_api {

    enum class ErrorCode : int {
        UnknownError = 100,
        Any = 101,
        InvalidInput = 102,
        InvalidKeyId = 103,
        InvalidId = 104,
        InvalidBlock = 200,
        InvalidBlock_NoChanges = 201,
        InvalidBlock_InvalidSignature = 202,
        InvalidBlock_HashMismatch = 203,
        InvalidBlock_HeightMismatch = 204,
        InvalidBlock_InvalidStateProof_Group = 205,
        InvalidBlock_InvalidStateProof_Secret = 206,
        InvalidBlock_NoPermissions = 207,
        InvalidBlock_InvalidGroupState = 208,
        InvalidBlock_InvalidSharedSecret = 209,
        InvalidCallGroupState_NotParticipant = 300,
        InvalidCallGroupState_WrongUserId = 301,
        Decrypt_UnknownEpoch = 400,
        Encrypt_UnknownEpoch = 401,
        InvalidBroadcast_InFuture = 500,
        InvalidBroadcast_NotInCommit = 501,
        InvalidBroadcast_NotInReveal = 502,
        InvalidBroadcast_UnknownUserId = 503,
        InvalidBroadcast_AlreadyApplied = 504,
        InvalidBroadcast_InvalidReveal = 505,
        InvalidBroadcast_InvalidBlockHash = 506,
        InvalidCallChannelId = 600,
        CallFailed = 601,
        CallKeyAlreadyUsed = 602
    };
    inline std::string_view error_string(ErrorCode error_code) {
        switch (error_code) {
            case ErrorCode::Any:
                return "";
            case ErrorCode::UnknownError:
                return "UNKNOWN_ERROR";
            case ErrorCode::InvalidInput:
                return "INVALID_INPUT";
            case ErrorCode::InvalidKeyId:
                return "INVALID_KEY_ID";
            case ErrorCode::InvalidId:
                return "INVALID_ID";
            case ErrorCode::InvalidBlock:
                return "INVALID_BLOCK";
            case ErrorCode::InvalidBlock_NoChanges:
                return "INVALID_BLOCK__NO_CHANGES";
            case ErrorCode::InvalidBlock_InvalidSignature:
                return "INVALID_BLOCK__INVALID_SIGNATURE";
            case ErrorCode::InvalidBlock_HashMismatch:
                return "INVALID_BLOCK__HASH_MISMATCH";
            case ErrorCode::InvalidBlock_HeightMismatch:
                return "INVALID_BLOCK__HEIGHT_MISMATCH";
            case ErrorCode::InvalidBlock_InvalidStateProof_Group:
                return "INVALID_BLOCK__INVALID_STATE_PROOF__GROUP";
            case ErrorCode::InvalidBlock_InvalidStateProof_Secret:
                return "INVALID_BLOCK__INVALID_STATE_PROOF__SECRET";
            case ErrorCode::InvalidBlock_InvalidGroupState:
                return "INVALID_BLOCK__INVALID_GROUP_STATE";
            case ErrorCode::InvalidBlock_InvalidSharedSecret:
                return "INVALID_BLOCK__INVALID_SHARED_SECRET";
            case ErrorCode::InvalidBlock_NoPermissions:
                return "INVALID_BLOCK__NO_PERMISSIONS";
            case ErrorCode::InvalidCallGroupState_NotParticipant:
                return "INVALID_CALL_GROUP_STATE__NOT_PARTICIPANT";
            case ErrorCode::InvalidCallGroupState_WrongUserId:
                return "INVALID_CALL_GROUP_STATE__WRONG_USER_ID";
            case ErrorCode::Decrypt_UnknownEpoch:
                return "DECRYPT__UNKNOWN_EPOCH";
            case ErrorCode::Encrypt_UnknownEpoch:
                return "ENCRYPT__UNKNOWN_EPOCH";
            case ErrorCode::InvalidBroadcast_InFuture:
                return "INVALID_BROADCAST__IN_FUTURE";
            case ErrorCode::InvalidBroadcast_NotInCommit:
                return "INVALID_BROADCAST__NOT_IN_COMMIT";
            case ErrorCode::InvalidBroadcast_NotInReveal:
                return "INVALID_BROADCAST__NOT_IN_REVEAL";
            case ErrorCode::InvalidBroadcast_UnknownUserId:
                return "INVALID_BROADCAST__UNKNOWN_USER_ID";
            case ErrorCode::InvalidBroadcast_AlreadyApplied:
                return "INVALID_BROADCAST__ALREADY_APPLIED";
            case ErrorCode::InvalidBroadcast_InvalidReveal:
                return "INVALID_BROADCAST__INVALID_REVEAL";
            case ErrorCode::InvalidBroadcast_InvalidBlockHash:
                return "INVALID_BROADCAST__INVALID_BLOCK_HASH";
            case ErrorCode::CallFailed:
                return "CALL_FAILED";
            case ErrorCode::CallKeyAlreadyUsed:
                return "CALL_KEY_ALREADY_USED";
            case ErrorCode::InvalidCallChannelId:
                return "INVALID_CALL_CHANNEL_ID";
        }
        return "UNKNOWN_ERROR";
    }

}  // namespace tde2e_api

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <tuple>
#include <utility>
#include <variant>
#include <vector>

namespace td {
    template <class T>
    class Result;
    class Status;
}  // namespace td

namespace tde2e_api {
//
//  Result and Error helper classes
//

    struct Error {
        ErrorCode code;
        std::string message;
    };

    template <typename T>
    class Result {
    public:
        Result(const T &value) : data_(value) {
        }
        Result(T &&value) : data_(std::move(value)) {
        }

        Result(const Error &error) : data_(error) {
        }
        Result(Error &&error) : data_(std::move(error)) {
        }

        Result(td::Result<T> &&value);
        Result(td::Status &&status);

        // Check if the result is a success
        bool is_ok() const {
            return std::holds_alternative<T>(data_);
        }

        T &value() {
            return std::get<T>(data_);
        }
        const T &value() const {
            return std::get<T>(data_);
        }

        Error &error() {
            return std::get<Error>(data_);
        }
        const Error &error() const {
            return std::get<Error>(data_);
        }

    private:
        std::variant<T, Error> data_;
    };

//
//                  Encryption
//

    using Int256 = std::array<unsigned char, 32>;
    using Int512 = std::array<unsigned char, 64>;
//TODO: strong typed ids
    using PublicKey = std::string;
    using HandshakeId = std::int64_t;
    using LoginId = std::int64_t;
    using UserId = std::int64_t;
    using AnyKeyId = std::int64_t;
    using PrivateKeyId = std::int64_t;
    using PublicKeyId = std::int64_t;
    using SymmetricKeyId = std::int64_t;
    using Bytes = std::string;
    using SecureBytes = std::string;
    using Slice = std::string_view;
    using SecureSlice = std::string_view;
    struct Ok {};

    struct EncryptedMessageForMany {
        std::vector<std::string> encrypted_headers;
        std::string encrypted_message;
    };

    Result<Ok> set_log_verbosity_level(int level);

// Keys management
// private keys will stay inside the library when it is possible
// all keys are stored only in memory and should be created or imported before usage
    Result<PrivateKeyId> key_generate_private_key();
    Result<PrivateKeyId> key_generate_temporary_private_key();
    Result<SymmetricKeyId> key_derive_secret(PrivateKeyId key_id, Slice tag);
    Result<SymmetricKeyId> key_from_bytes(SecureSlice secret);
    Result<Bytes> key_to_encrypted_private_key(PrivateKeyId key_id, SymmetricKeyId secret_id);
    Result<PrivateKeyId> key_from_encrypted_private_key(Slice encrypted_key, SymmetricKeyId secret_id);
    Result<PublicKeyId> key_from_public_key(Slice public_key);
    Result<SymmetricKeyId> key_from_ecdh(PrivateKeyId key_id, PublicKeyId other_public_key_id);
    Result<PublicKey> key_to_public_key(PrivateKeyId key_id);
    Result<SecureBytes> key_to_words(PrivateKeyId key_id);
    Result<PrivateKeyId> key_from_words(SecureSlice words);
    Result<Int512> key_sign(PrivateKeyId key, Slice data);
    Result<Ok> key_destroy(AnyKeyId key_id);
    Result<Ok> key_destroy_all();

// Used to encrypt key between processes, secret_id must be generated with key_from_ecdh
    Result<Bytes> key_to_encrypted_private_key_internal(PrivateKeyId key_id, SymmetricKeyId secret_id);
    Result<PrivateKeyId> key_from_encrypted_private_key_internal(Slice encrypted_key, SymmetricKeyId secret_id);

    Result<EncryptedMessageForMany> encrypt_message_for_many(const std::vector<SymmetricKeyId> &key_ids,
                                                             SecureSlice message);
// keeps encrypted_message empty in result
    Result<EncryptedMessageForMany> re_encrypt_message_for_many(SymmetricKeyId decrypt_key_id,
                                                                const std::vector<SymmetricKeyId> &encrypt_key_ids,
                                                                Slice encrypted_header, Slice encrypted_message);
    Result<SecureBytes> decrypt_message_for_many(SymmetricKeyId key_id, Slice encrypted_header, Slice encrypted_message);
    Result<Bytes> encrypt_message_for_one(SymmetricKeyId key_id, SecureSlice message);
    Result<SecureBytes> decrypt_message_for_one(SymmetricKeyId key_id, Slice encrypted_message);

// Utilities for secret key verification/transfer via qr (or any other alternative channel)
// Requires:
//  - alternative channel to reliably transfer 'start' message from Bob to Alice (scanning QR is such channel)
//
// Alice:
//   - knows shared secret right after the handshake creation
//   - Bob's secret key is verified after finish is received
//
// Bob:
//   - knows shared secret right after accept is received
//   - Alice's secret key is verified after accept is received
//
// Use cases:
//  - Transfer of the key from old device to the new device
//  - Verification of other person's public key
//  - Contact sharing
//
    Result<HandshakeId> handshake_create_for_bob(UserId bob_user_id, PrivateKeyId bob_private_key_id);
    Result<HandshakeId> handshake_create_for_alice(UserId alice_user_id, PrivateKeyId alice_private_key_id,
                                                   UserId bob_user_id, const PublicKey &bob_public_key, Slice start);

    Result<Bytes> handshake_bob_send_start(HandshakeId bob_handshake_id);
    Result<Bytes> handshake_alice_send_accept(HandshakeId alice_handshake_id);
    Result<Bytes> handshake_bob_receive_accept_send_finish(HandshakeId bob_handshake_id, UserId alice_id,
                                                           const PublicKey &alice_public_key, Slice accept);
    Result<Ok> handshake_alice_receive_finish(HandshakeId alice_handshake_id, Slice finish);
    Result<SymmetricKeyId> handshake_get_shared_key_id(HandshakeId handshake_id);
    Result<Ok> handshake_destroy(HandshakeId handshake_id);
    Result<Ok> handshake_destroy_all();

// Helper to get QR-code identifier
    Result<Bytes> handshake_start_id(Slice start);

// There is wrapper for login
    Result<LoginId> login_create_for_bob();
    Result<Bytes> login_bob_send_start(LoginId bob_login_id);
    Result<Bytes> login_create_for_alice(UserId alice_user_id, PrivateKeyId alice_private_key_id, Slice start);
    Result<PrivateKeyId> login_finish_for_bob(LoginId bob_login_id, UserId alice_user_id, const PublicKey &alice_public_key,
                                              Slice data);
    Result<Ok> login_destroy(LoginId login_id);
    Result<Ok> login_destroy_all();

// Personal info

// 1. Each entry stored and signed separately
// 2. Signature is never stored, but always is verified
// 3. It should be possible to save data without signature (but it can't override data with signature)
// 4. We should keep source of entry. Our, Server, Contact+Ts
// 5. We must keep is_contact flag.

    template <class T>
    struct Entry {
        enum Source { Self, Server, Contact };
        Source source;
        std::uint32_t timestamp;
        T value;

        Entry() : source(Self), timestamp(0), value() {
        }
        Entry(Source source, std::uint32_t timestamp, T &&value)
                : source(source), timestamp(timestamp), value(std::move(value)) {
        }
    };

    template <class T>
    struct SignedEntry {
        Int512 signature;
        std::uint32_t timestamp{0};
        T value;
    };

    struct Name {
        std::string first_name;
        std::string last_name;
    };

    struct PhoneNumber {
        std::string phone_number;
    };

    struct EmojiNonces {
        std::optional<Int256> self_nonce;
        std::optional<Int256> contact_nonce_hash;
        std::optional<Int256> contact_nonce;
    };

    struct ContactState {
        enum State { Unknown, Contact, NotContact };
        State state{Unknown};

        ContactState() = default;

        explicit ContactState(State state) : state(state) {
        }
    };

    struct Contact {
        // Each contact has both public_key and user_id
        // how it is stored internally is not clients library user's concern
        std::uint32_t generation{0};

        // we always have public key. Essentially contact is defined by its public key
        PublicKeyId public_key{};

        // Personal data, signed by contact. If it is not signed, it has no relations to this public key
        std::optional<Entry<UserId>> o_user_id;
        std::optional<Entry<Name>> o_name;
        std::optional<Entry<PhoneNumber>> o_phone_number;

        // Personal data save by user_itself
        // It always exists and it always given but we keep timestamp just in case
        Entry<EmojiNonces> emoji_nonces;
        Entry<ContactState> contact_state;
    };

// library can't enforce persistency of changes, because it has no persistent state.
// so it is client's responsibility to ensure that each change will be eventually saved to the server
// NB: it is unclear how protect ourself from server ignoring our queries.

    using StorageId = std::int64_t;
    using UpdateId = std::int64_t;

    struct StorageBlockchainState {
        std::string next_suggested_block;
        std::vector<std::string> required_proofs;
    };
    struct StorageUpdates {
        std::vector<std::pair<PublicKeyId, std::optional<Contact>>> updates;
    };

    Result<StorageId> storage_create(PrivateKeyId key_id, Slice last_block);
    Result<Ok> storage_destroy(StorageId storage_id);
    Result<Ok> storage_destroy_all();

    template <class T>
    Result<UpdateId> storage_update_contact(StorageId storage_id, PublicKeyId key, SignedEntry<T> signed_entry);
    template <class T>
    Result<SignedEntry<T>> storage_sign_entry(PrivateKeyId key, Entry<T> entry);

    Result<std::optional<Contact>> storage_get_contact(StorageId storage_id, PublicKeyId key);
    Result<std::optional<Contact>> storage_get_contact_optimistic(StorageId storage_id, PublicKeyId key);

    Result<std::int64_t> storage_blockchain_height(StorageId storage_id);
    Result<StorageUpdates> storage_blockchain_apply_block(StorageId storage_id, Slice block);
    Result<Ok> storage_blockchain_add_proof(StorageId storage_id, Slice proof, const std::vector<std::string> &keys);

    Result<StorageBlockchainState> storage_get_blockchain_state(StorageId);

    using CallId = std::int64_t;
    using CallChannelId = std::int32_t;
    struct CallParticipant {
        UserId user_id;
        PublicKeyId public_key_id;
        int permissions{};
    };

    struct CallState {
        int height{};
        std::vector<CallParticipant> participants;
    };

    Result<Bytes> call_create_zero_block(PrivateKeyId private_key_id, const CallState &initial_state);
    Result<Bytes> call_create_self_add_block(PrivateKeyId private_key_id, Slice previous_block,
                                             const CallParticipant &self);
    Result<CallId> call_create(UserId user_id, PrivateKeyId private_key_id, Slice last_block);

    Result<std::string> call_describe(CallId call);
    Result<std::string> call_describe_block(Slice block);
    Result<std::string> call_describe_message(Slice message);

    Result<Bytes> call_create_change_state_block(CallId call_id, const CallState &new_state);
    Result<Bytes> call_encrypt(CallId call_id, CallChannelId channel_id, SecureSlice message,
                               size_t unencrypted_prefix_size);
    Result<SecureBytes> call_decrypt(CallId call_id, UserId user_id, CallChannelId channel_id, Slice message);

    Result<int> call_get_height(CallId call_id);
    Result<CallState> call_apply_block(CallId call_id, Slice block);

    Result<CallState> call_get_state(CallId call_id);

    struct CallVerificationState {
        int height{};
        std::optional<Bytes> emoji_hash;
    };
    Result<CallVerificationState> call_get_verification_state(CallId call_id);
    Result<CallVerificationState> call_receive_inbound_message(CallId call_id, Slice message);

// should be called after:
//   - creation
//   - call_apply_block
//   - call_receive_inbound_messages
    Result<std::vector<Bytes>> call_pull_outbound_messages(CallId call_id);

    struct CallVerificationWords {
        int height{};
        std::vector<std::string> words;
    };

    Result<CallVerificationWords> call_get_verification_words(CallId call_id);
    Result<Ok> call_destroy(CallId call_id);
    Result<Ok> call_destroy_all();

}  // namespace tde2e_api
