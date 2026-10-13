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

#include "wallet_engine_scaffolding.hpp"

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

namespace wallet_engine {
struct TonConnectDerivedSession;
struct TonConnectSession;
struct WalletClient;
struct WalletHttpHost;
struct WalletLifecycle;
struct WalletPlatformHost;
struct WalletStatuslessHost;
struct AccountSnapshot;
struct ActivityCursor;
struct ActivityItem;
struct ActivityList;
struct CreateEncryptedCommentRequest;
struct CreateWalletRequest;
struct CreatedWallet;
struct DecryptCommentRequest;
struct DomainError;
struct EncryptedCommentRecipientRequest;
struct HttpHeader;
struct HttpRequest;
struct HttpRequestId;
struct HttpResponse;
struct ImportWalletRequest;
struct JournalCompareExchange;
struct JournalCompareExchangeResult;
struct JournalKey;
struct JournalRecord;
struct NftCollectionDescriptor;
struct NftItem;
struct NftList;
struct NftTransferIntent;
struct NftTransferPreviewRequest;
struct NftTransferRequest;
struct ParsedTonTransferLink;
struct PrepareKeyRotationRequest;
struct PrepareNftTransferRequest;
struct PrepareTransferRequest;
struct PreparedKeyRotation;
struct PreparedTransfer;
struct ProtectedSecretRead;
struct ProtectedSecretRef;
struct ProtectedSecretStore;
struct ProviderConfig;
struct RecoveryPhrase;
struct ResolutionInfo;
struct ResourceState;
struct SendBocRequest;
struct SendEmulation;
struct SendEmulationAction;
struct SendEmulationTransaction;
struct SendIntent;
struct SendMessage;
struct SendPreview;
struct SendPreviewRequest;
struct SendRequest;
struct SendResult;
struct SendSnapshot;
struct SignMessagePreview;
struct SignMessageRequest;
struct SignMessageResult;
struct TonAddressInfo;
struct TonConnectAccountInfo;
struct TonConnectConnectPrompt;
struct TonConnectDerivedRequest;
struct TonConnectDerivedSessionRequest;
struct TonConnectDevice;
struct TonConnectManifest;
struct TonConnectPreparedPost;
struct TonConnectProofReply;
struct TonConnectProofSignRequest;
struct TonConnectProofSignature;
struct TonConnectSessionConfig;
struct TonConnectSignDataRequest;
struct TonConnectSignDataSignRequest;
struct TonConnectSignedData;
struct WalletClientConfig;
struct WalletDescriptor;
struct WalletSnapshot;
struct WalletUpdate;
enum class AccountStatus;
enum class ActivityDirection;
enum class ActivityStatus;
enum class ErrorCategory;
enum class ErrorCode;
struct HttpHostError;
enum class HttpHostErrorKind;
enum class HttpMethod;
struct JournalHostError;
enum class JournalHostErrorKind;
enum class KeyRotationMessageKind;
enum class MnemonicScheme;
enum class Network;
struct NftTransferFunding;
struct NftTransferPayload;
enum class PendingReason;
struct ProtectedSecretHostError;
enum class ProtectedSecretHostErrorKind;
enum class ResourcePhase;
enum class RetryAdvice;
enum class SecretAccessReason;
struct SendAmount;
struct SendExpiration;
struct SendMessageBody;
enum class SendPhase;
struct StatuslessHostError;
enum class StatuslessHostErrorKind;
struct TonAddressError;
struct TonAddressFormat;
enum class TonConnectConnectErrorCode;
enum class TonConnectDevicePlatform;
struct TonConnectIncomingRequest;
enum class TonConnectRpcErrorCode;
struct TonConnectSessionError;
enum class TonConnectSessionPhase;
struct TonConnectSignDataPayload;
struct TonTransferAsset;
struct TonTransferLinkError;
struct TonTransferPayload;
struct WalletClientError;
struct WalletLifecycleError;
enum class WalletOperationOutcome;
typedef std::string Base64Hash;
typedef std::string Boc;
typedef std::string NonEmptyString;
typedef std::string TonAddressString;
typedef std::string UnsignedDecimalString;


/**
 * The lifecycle state of a TON account.
 */
enum class AccountStatus: int32_t {
    /**
     * No account exists at the address.
     */
    kNonexistent = 1,
    /**
     * The account exists but its contract state is not initialized.
     */
    kUninitialized = 2,
    /**
     * The account contract is active.
     */
    kActive = 3,
    /**
     * The account contract is frozen.
     */
    kFrozen = 4,
    /**
     * The provider returned an unrecognized account state.
     */
    kUnknown = 5
};


/**
 * The terminal result of a wallet client operation.
 */
enum class WalletOperationOutcome: int32_t {
    /**
     * All requested resources completed successfully.
     */
    kCompleted = 1,
    /**
     * Some refresh resources succeeded and some failed.
     */
    kPartiallyCompleted = 2,
    /**
     * The requested operation failed.
     */
    kFailed = 3,
    /**
     * The host or caller cancelled the operation.
     */
    kCancelled = 4,
    /**
     * A newer operation replaced this operation before publication completed.
     */
    kSuperseded = 5,
    /**
     * Preconditions did not permit work, so the client made no request.
     */
    kSkipped = 6
};


/**
 * An HTTP method that the host must execute.
 */
enum class HttpMethod: int32_t {
    /**
     * An HTTP GET request.
     */
    kGet = 1,
    /**
     * An HTTP POST request.
     */
    kPost = 2
};


/**
 * Classifies a failure reported by the HTTP host.
 */
enum class HttpHostErrorKind: int32_t {
    /**
     * No network connection is available.
     */
    kOffline = 1,
    /**
     * The request exceeded a host timeout.
     */
    kTimeout = 2,
    /**
     * An established connection ended before completion.
     */
    kConnectionLost = 3,
    /**
     * DNS resolution failed.
     */
    kDns = 4,
    /**
     * TLS negotiation or certificate validation failed.
     */
    kTls = 5,
    /**
     * The request violated a host security policy.
     */
    kPolicyViolation = 6,
    /**
     * The response exceeded a limit imposed by the host.
     */
    kResponseTooLarge = 7,
    /**
     * The host cancelled the request.
     */
    kCancelled = 8,
    /**
     * The failure does not match another kind.
     */
    kOther = 9
};


/**
 * Classifies a protected-storage failure reported by the host.
 */
enum class ProtectedSecretHostErrorKind: int32_t {
    /**
     * No secret exists for the reference.
     */
    kNotFound = 1,
    /**
     * User or device authentication failed.
     */
    kAuthenticationFailed = 2,
    /**
     * The user or host cancelled the operation.
     */
    kCancelled = 3,
    /**
     * Protected storage is temporarily unavailable.
     */
    kUnavailable = 4,
    /**
     * The request violates a platform security policy.
     */
    kPolicyViolation = 5,
    /**
     * The failure does not match another kind.
     */
    kOther = 6
};

namespace uniffi {
struct FfiConverterTonAddressFormat;
} // namespace uniffi

/**
 * Selects a raw representation or a user-friendly representation with flags.
 */
struct TonAddressFormat {
    friend uniffi::FfiConverterTonAddressFormat;
    /**
     * `workchain:64-hex` without display or network flags.
     */
    struct kRaw {
    };
    /**
     * TEP-2 user-friendly Base64 with a checksum and display flags.
     */
    struct kUserFriendly {
        /**
         * Whether senders should use a bounceable internal message.
         */
        bool bounceable;
        /**
         * Whether mainnet applications must reject the address.
         */
        bool testnet;
    };
    TonAddressFormat(kRaw variant): variant(variant) {}
    TonAddressFormat(kUserFriendly variant): variant(variant) {}

    TonAddressFormat(const TonAddressFormat &other): variant(other.variant) {}
    TonAddressFormat(TonAddressFormat &&other): variant(std::move(other.variant)) {}

    TonAddressFormat &operator=(const TonAddressFormat &other) {
        variant = other.variant;
        return *this;
    }

    TonAddressFormat &operator=(TonAddressFormat &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kRaw, kUserFriendly> &get_variant() const {
        return variant;
    }

private:
    std::variant<kRaw, kUserFriendly> variant;

    TonAddressFormat();
};


/**
 * TON Connect RPC error code returned for an unsupported request.
 */
enum class TonConnectRpcErrorCode: int32_t {
    /**
     * Unexpected wallet-side failure.
     */
    kUnknown = 1,
    /**
     * Malformed request.
     */
    kBadRequest = 2,
    /**
     * Unknown or revoked application session.
     */
    kUnknownApp = 3,
    /**
     * User declined the request.
     */
    kUserDeclined = 4,
    /**
     * Wallet does not implement the method or shape.
     */
    kMethodNotSupported = 5
};


/**
 * An opaque reference to recovery words in host protected storage.
 */
struct ProtectedSecretRef {
    /**
     * The host storage key. The value does not contain secret bytes.
     */
    std::string value;
};


/**
 * Correlation identity generated by Rust. Hosts can use it to bind their
 * native cancellation bookkeeping, but must not assign semantic meaning to
 * the numeric value.
 */
struct HttpRequestId {
    /**
     * An opaque numeric value allocated by this client instance.
     */
    uint64_t value;
};


/**
 * One HTTP header name and value.
 */
struct HttpHeader {
    /**
     * The header name.
     */
    std::string name;
    /**
     * The header value.
     */
    std::string value;
};


/**
 * Selects how the signed key-rotation request will reach Wallet rev00.
 */
enum class KeyRotationMessageKind: int32_t {
    /**
     * Complete incoming external message for direct provider submission.
     */
    kExternal = 1,
    /**
     * Complete owner-signed internal message for a gas-paying relayer.
     */
    kInternal = 2
};


/**
 * A deliberately one-shot presentation value, not wallet state.
 *
 * The UI must discard this value as soon as the recovery-phrase screen is
 * dismissed. Neither `WalletDescriptor` nor `WalletSnapshot` contains it.
 *
 * A newly created wallet has a 12-word phrase; the phrase becomes 24 words
 * when the wallet's key is rotated.
 */
struct RecoveryPhrase {
    /**
     * The recovery words in order, separated by one ASCII space.
     *
     * Keeping the phrase in one allocation avoids separately allocated
     * secret strings at the FFI boundary. Split it only while presenting
     * individual words, then release those temporary views.
     */
    std::string phrase;
};


/**
 * Runtime platform advertised as the TypeScript protocol's `DeviceInfo.platform`.
 *
 * This is deliberately different from wallets-list platforms such as `ios`,
 * `macos`, or `chrome`, which describe installation targets rather than the
 * runtime that produced a connect event.
 */
enum class TonConnectDevicePlatform: int32_t {
    /**
     * iPhone application.
     */
    kIphone = 1,
    /**
     * iPad application.
     */
    kIpad = 2,
    /**
     * Android application.
     */
    kAndroid = 3,
    /**
     * Windows application.
     */
    kWindows = 4,
    /**
     * macOS application.
     */
    kMac = 5,
    /**
     * Linux application.
     */
    kLinux = 6,
    /**
     * Browser application or extension.
     */
    kBrowser = 7
};


/**
 * The value flow direction for one activity item.
 */
enum class ActivityDirection: int32_t {
    /**
     * Value left the wallet.
     */
    kSent = 1,
    /**
     * Value entered the wallet.
     */
    kReceived = 2
};


/**
 * The public phase of the current or last send workflow.
 */
enum class SendPhase: int32_t {
    /**
     * No send has started.
     */
    kIdle = 1,
    /**
     * The engine validates the request, journal, and chain state.
     */
    kValidating = 2,
    /**
     * The host authorizes protected-secret access.
     */
    kAuthorizing = 3,
    /**
     * Rust constructs and signs the transfer.
     */
    kPreparing = 4,
    /**
     * The host writes a durable journal record.
     */
    kPersisting = 5,
    /**
     * The exact signed BOC is durable and ready for submission.
     */
    kReadyToSubmit = 6,
    /**
     * The HTTP host submits the signed BOC.
     */
    kSubmitting = 7,
    /**
     * Submission can have succeeded, but the engine has no definite response.
     */
    kSubmissionUnknown = 8,
    /**
     * The provider accepted the signed BOC.
     */
    kSubmitted = 9,
    /**
     * A signed internal message was durably returned for submission by a relayer.
     */
    kHandedOff = 10,
    /**
     * The external message was found as the inbound message of an on-chain transaction.
     */
    kConfirmed = 11,
    /**
     * Another external message consumed the sequence number reserved by this send.
     */
    kReplaced = 12,
    /**
     * The signed sequence number was consumed, but available provider evidence
     * cannot identify which competing internal request executed.
     */
    kSequenceNumberConsumed = 13,
    /**
     * Provider time passed the signed validity window and the message was not observed.
     */
    kExpired = 14,
    /**
     * An explicit same-sequence-number resend replaced this journal attempt.
     */
    kSuperseded = 15,
    /**
     * The send failed before an ambiguous submission result.
     */
    kFailed = 16,
    /**
     * The send was cancelled before its durable commit boundary.
     */
    kCancelled = 17
};


/**
 * Classifies a durable journal failure reported by the host.
 */
enum class JournalHostErrorKind: int32_t {
    /**
     * Durable storage is temporarily unavailable.
     */
    kUnavailable = 1,
    /**
     * The stored record cannot be read without data loss.
     */
    kCorruptData = 2,
    /**
     * The host cancelled the storage operation.
     */
    kCancelled = 3,
    /**
     * The failure does not match another kind.
     */
    kOther = 4
};

namespace uniffi {
struct FfiConverterSendExpiration;
} // namespace uniffi

/**
 * The policy used to select the wallet message expiration boundary.
 */
struct SendExpiration {
    friend uniffi::FfiConverterSendExpiration;
    /**
     * Derive expiration from fresh provider time and engine configuration.
     */
    struct kEngineDefault {
    };
    /**
     * Preserve a caller-selected Unix expiration timestamp.
     */
    struct kExact {
        /**
         * The Unix expiration timestamp in seconds.
         */
        uint64_t unix_timestamp;
    };
    SendExpiration(kEngineDefault variant): variant(variant) {}
    SendExpiration(kExact variant): variant(variant) {}

    SendExpiration(const SendExpiration &other): variant(other.variant) {}
    SendExpiration(SendExpiration &&other): variant(std::move(other.variant)) {}

    SendExpiration &operator=(const SendExpiration &other) {
        variant = other.variant;
        return *this;
    }

    SendExpiration &operator=(SendExpiration &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kEngineDefault, kExact> &get_variant() const {
        return variant;
    }

private:
    std::variant<kEngineDefault, kExact> variant;

    SendExpiration();
};


/**
 * Classifies a failure reported by a status-less provider host.
 */
enum class StatuslessHostErrorKind: int32_t {
    /**
     * No network connection is available.
     */
    kOffline = 1,
    /**
     * The request exceeded the host deadline.
     */
    kTimeout = 2,
    /**
     * An established relay or proxy connection ended before completion.
     */
    kConnectionLost = 3,
    /**
     * The request violated a host security policy.
     */
    kPolicyViolation = 4,
    /**
     * The response exceeded a limit imposed by the host.
     */
    kResponseTooLarge = 5,
    /**
     * The host cancelled the request.
     */
    kCancelled = 6,
    /**
     * The failure does not match another kind.
     */
    kOther = 7
};

namespace uniffi {
struct FfiConverterTonConnectSignDataPayload;
} // namespace uniffi

/**
 * The data of one `signData` request, exactly as the dApp sent it.
 */
struct TonConnectSignDataPayload {
    friend uniffi::FfiConverterTonConnectSignDataPayload;
    /**
     * UTF-8 text shown to the user and signed verbatim.
     */
    struct kText {
        /**
         * The text to sign.
         */
        std::string text;
    };
    /**
     * Opaque bytes.
     */
    struct kBinary {
        /**
         * The bytes in base64, exactly as the dApp sent them.
         */
        std::string bytes;
    };
    /**
     * A TVM cell described by a TL-B schema; the engine does not decode it.
     */
    struct kCell {
        /**
         * The TL-B schema whose last declared type is the root, exactly as sent.
         */
        std::string schema;
        /**
         * The one-root cell `BoC` in base64, exactly as the dApp sent it.
         */
        std::string cell;
    };
    TonConnectSignDataPayload(kText variant): variant(variant) {}
    TonConnectSignDataPayload(kBinary variant): variant(variant) {}
    TonConnectSignDataPayload(kCell variant): variant(variant) {}

    TonConnectSignDataPayload(const TonConnectSignDataPayload &other): variant(other.variant) {}
    TonConnectSignDataPayload(TonConnectSignDataPayload &&other): variant(std::move(other.variant)) {}

    TonConnectSignDataPayload &operator=(const TonConnectSignDataPayload &other) {
        variant = other.variant;
        return *this;
    }

    TonConnectSignDataPayload &operator=(TonConnectSignDataPayload &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kText, kBinary, kCell> &get_variant() const {
        return variant;
    }

private:
    std::variant<kText, kBinary, kCell> variant;

    TonConnectSignDataPayload();
};


/**
 * A stable machine-readable domain error code.
 */
enum class ErrorCode: int32_t {
    /**
     * The provider response did not match the expected schema.
     */
    kInvalidProviderResponse = 1,
    /**
     * The provider returned an HTTP or protocol rejection.
     */
    kHttpRejected = 2,
    /**
     * The provider applied a request limit.
     */
    kRateLimited = 3,
    /**
     * The host reported a transport failure.
     */
    kTransportFailed = 4,
    /**
     * The host cancelled the request.
     */
    kHostCancelled = 5,
    /**
     * The response exceeded a configured bound.
     */
    kResponseTooLarge = 6,
    /**
     * The request violated a host security policy.
     */
    kHostPolicyViolation = 7
};


/**
 * States whether the same operation can be retried safely.
 */
enum class RetryAdvice: int32_t {
    /**
     * Do not retry the operation without a user or configuration change.
     */
    kNone = 1,
    /**
     * The same read-only operation can be retried.
     */
    kSafe = 2,
    /**
     * Retry after [`DomainError::retry_after_ms`].
     */
    kAfterDelay = 3
};


/**
 * Selects the TON network used for addresses, providers, and wallet derivation.
 */
enum class Network: int32_t {
    /**
     * The production TON network.
     */
    kMainnet = 1,
    /**
     * The public TON test network.
     */
    kTestnet = 2
};


/**
 * Explains why the engine requests access to a protected secret.
 */
enum class SecretAccessReason: int32_t {
    /**
     * Reserved for wallet creation storage policy.
     */
    kCreateWallet = 1,
    /**
     * The engine needs the mnemonic to sign a transfer.
     */
    kSignTransfer = 2,
    /**
     * The engine needs the mnemonic for an off-chain TON Connect ownership proof.
     */
    kSignTonConnectProof = 3,
    /**
     * The engine needs the current mnemonic to prepare a signing-key rotation.
     */
    kPrepareKeyRotation = 4,
    /**
     * The engine needs the mnemonic to encrypt a transfer comment.
     */
    kEncryptComment = 5,
    /**
     * The engine needs the mnemonic to decrypt a transfer comment.
     */
    kDecryptComment = 6,
    /**
     * The user requested the recovery phrase.
     */
    kRevealRecoveryPhrase = 7,
    /**
     * The engine needs the current signing key to derive a TON Connect session key.
     */
    kDeriveTonConnectSessionKey = 8,
    /**
     * The engine needs the current signing key to sign TON Connect signData.
     */
    kSignTonConnectData = 9
};


/**
 * One opaque versioned journal value owned by the engine.
 */
struct JournalRecord {
    /**
     * The positive compare-and-swap version.
     */
    uint64_t version;
    /**
     * The opaque engine payload. The host must preserve these bytes exactly.
     */
    std::vector<uint8_t> payload;
};


/**
 * Selects one durable journal slot for one wallet record.
 */
struct JournalKey {
    /**
     * The stable application record identifier.
     */
    std::string record_id;
    /**
     * The engine-defined slot name.
     */
    std::string slot;
};


/**
 * Why a durable outgoing message cannot yet be resolved to a final outcome.
 */
enum class PendingReason: int32_t {
    /**
     * Toncenter still exposes an emulated pending transaction for the message.
     */
    kInMempool = 1,
    /**
     * No terminal evidence exists and the signed validity window is still open.
     */
    kAwaitingWindow = 2
};


/**
 * The current state of one independently loaded resource.
 */
enum class ResourcePhase: int32_t {
    /**
     * No load is active and no successful value is available yet.
     */
    kIdle = 1,
    /**
     * A load is active.
     */
    kLoading = 2,
    /**
     * The latest load for this resource succeeded.
     */
    kReady = 3,
    /**
     * The latest load for this resource failed.
     */
    kFailed = 4
};


/**
 * The on-chain result of the transaction or internal message.
 */
enum class ActivityStatus: int32_t {
    /**
     * The transaction completed and this message was not a bounce.
     */
    kSuccess = 1,
    /**
     * The transaction was aborted while processing the message.
     */
    kFailed = 2,
    /**
     * The internal message has the on-chain `bounced` flag.
     */
    kBounced = 3
};


/**
 * The broad source of a provider or host error.
 */
enum class ErrorCategory: int32_t {
    /**
     * Network transport failed.
     */
    kTransport = 1,
    /**
     * The provider rejected a request or returned invalid data.
     */
    kProviderProtocol = 2,
    /**
     * The provider applied a request limit.
     */
    kRateLimit = 3,
    /**
     * The host cancelled the request.
     */
    kCancellation = 4,
    /**
     * The host rejected the request because of a security or size policy.
     */
    kHostPolicy = 5
};


/**
 * A request to store secret bytes in platform protected storage.
 */
struct ProtectedSecretStore {
    /**
     * The host storage key.
     */
    ProtectedSecretRef secret_ref;
    /**
     * The mnemonic bytes. The host must not log or persist them outside protected storage.
     */
    std::vector<uint8_t> bytes;
    /**
     * Whether later reads must require user presence or device authentication.
     */
    bool require_user_presence;
};


/**
 * Requests durable submission of an already signed external wallet message.
 *
 * `seqno` and `valid_until` must be the exact values covered by `signed_boc`.
 * They let the engine reconcile an ambiguous submission without parsing a
 * wallet-version-specific body.
 */
struct SendBocRequest {
    /**
     * A unique idempotency identifier chosen by the application.
     */
    NonEmptyString operation_id;
    /**
     * Allows this submission to replace an unresolved durable send after confirmation.
     */
    bool force;
    /**
     * The complete signed external-message BOC.
     */
    Boc signed_boc;
    /**
     * The wallet sequence number covered by the signed message.
     */
    uint32_t seqno;
    /**
     * The Unix expiration timestamp covered by the signed message.
     */
    uint64_t valid_until;
};


/**
 * The latest parsed balance and status for a wallet account.
 */
struct AccountSnapshot {
    /**
     * The exact account balance, in nanograms.
     */
    UnsignedDecimalString balance_nanograms;
    /**
     * The account lifecycle state.
     */
    AccountStatus status;
    /**
     * The provider synchronization time as a Unix timestamp.
     */
    uint64_t sync_utime;
};


/**
 * A request to read and authorize access to protected secret bytes.
 */
struct ProtectedSecretRead {
    /**
     * The host storage key.
     */
    ProtectedSecretRef secret_ref;
    /**
     * The operation that needs secret access.
     */
    SecretAccessReason reason;
    /**
     * User-facing authentication text supplied by Rust.
     */
    std::string prompt;
};


/**
 * Parsed TON address identity and any flags carried by its input representation.
 */
struct TonAddressInfo {
    /**
     * Canonical lowercase `workchain:64-hex` account identity.
     */
    std::string raw;
    /**
     * Signed workchain identifier from the address.
     */
    int32_t workchain;
    /**
     * Representation and flags used by the parsed input.
     */
    TonAddressFormat format;
};


/**
 * One nonzero incoming or outgoing value transfer in wallet activity.
 */
struct ActivityItem {
    /**
     * A stable item key derived from the transaction, direction, and message index.
     */
    std::string id;
    /**
     * The transaction hash in standard padded Base64.
     */
    Base64Hash transaction_hash;
    /**
     * The transaction logical time.
     */
    UnsignedDecimalString logical_time;
    /**
     * The transaction Unix timestamp.
     */
    uint64_t timestamp;
    /**
     * The value flow direction relative to this wallet.
     */
    ActivityDirection direction;
    /**
     * The exact transferred value, in nanograms.
     */
    UnsignedDecimalString amount_nanograms;
    /**
     * The total fee charged by this transaction, in nanograms.
     *
     * A transaction with multiple visible messages repeats this value on each
     * activity item; callers must not sum it per row.
     */
    UnsignedDecimalString transaction_fee_nanograms;
    /**
     * The on-chain execution status for this message and transaction.
     */
    ActivityStatus status;
    /**
     * A decoded zero-opcode plaintext comment, including an empty comment.
     */
    std::optional<std::string> comment;
    /**
     * An opaque encrypted-comment body that can be passed to
     * [`crate::WalletClient::decrypt_comment`].
     *
     * A sender encrypts to the signing key this wallet had at that time.
     * Decryption tries the current signing key and the anchor key first, then
     * signing keys that earlier rotations replaced, which it recovers from the
     * wallet's public key-change history.
     */
    std::optional<Boc> encrypted_comment;
    /**
     * The source or destination address, if the provider supplies it.
     */
    std::optional<TonAddressString> counterparty;
};


/**
 * An atomic compare-and-swap request for a journal slot.
 */
struct JournalCompareExchange {
    /**
     * The journal slot to change.
     */
    JournalKey key;
    /**
     * The required current version, or `None` when the slot must be absent.
     */
    std::optional<uint64_t> expected_version;
    /**
     * The complete replacement value.
     */
    JournalRecord replacement;
};


/**
 * Requests a ready-to-send TON encrypted-comment body.
 *
 * The engine uses the supplied recipient public key or loads it from chain
 * state, then asks the platform host to authorize access to this wallet's
 * protected mnemonic.
 */
struct CreateEncryptedCommentRequest {
    /**
     * Recipient wallet address. Must expose `get_public_key` when no key is supplied.
     */
    TonAddressString recipient;
    /**
     * UTF-8 comment to encrypt. Its encoded form must not exceed 960 bytes.
     */
    std::string comment;
    /**
     * Optional 32-byte Ed25519 public key used instead of an on-chain lookup.
     *
     * Used directly without deriving or checking the recipient address. The
     * caller must supply the key used by the recipient for comment decryption.
     */
    std::optional<std::vector<uint8_t>> recipient_public_key = std::nullopt;
};


/**
 * Requests explicit decryption of one encrypted-comment message body.
 *
 * [`crate::WalletClient::decrypt_comment`] reads the protected mnemonic once
 * and tries the current signing key, then the anchor key, without any HTTP
 * request. When neither matches and the wallet has rotated its key, it reads
 * the wallet's `change_wallet_key` history from Toncenter v3, recovers each
 * earlier signing key from the encrypted old key its rotation published, and
 * tries those keys. Recovered keys never leave the call.
 */
struct DecryptCommentRequest {
    /**
     * Address that sent the encrypted comment.
     *
     * TON binds this bounceable, URL-safe, non-test-only address to the
     * authentication tag. For an incoming activity item this is its
     * `counterparty`; for an outgoing item it is this wallet's address.
     */
    TonAddressString sender;
    /**
     * Complete message-body cell encoded as a Base64 BOC.
     */
    Boc body;
};


/**
 * The provider cursor for the next older activity page.
 */
struct ActivityCursor {
    /**
     * The oldest loaded transaction logical time.
     */
    UnsignedDecimalString logical_time;
    /**
     * The oldest loaded transaction hash in standard padded Base64.
     */
    Base64Hash hash;
};

namespace uniffi {
struct FfiConverterTonTransferPayload;
} // namespace uniffi

/**
 * The optional payload carried by a parsed TON transfer link.
 */
struct TonTransferPayload {
    friend uniffi::FfiConverterTonTransferPayload;
    /**
     * The link contains no message payload.
     */
    struct kNone {
    };
    /**
     * The link contains a plaintext comment, which may intentionally be empty.
     */
    struct kText {
        /**
         * The decoded UTF-8 comment.
         */
        std::string text;
    };
    /**
     * The link contains a validated single-root BOC.
     */
    struct kBoc {
        /**
         * The complete payload cell BOC.
         */
        Boc boc;
    };
    TonTransferPayload(kNone variant): variant(variant) {}
    TonTransferPayload(kText variant): variant(variant) {}
    TonTransferPayload(kBoc variant): variant(variant) {}

    TonTransferPayload(const TonTransferPayload &other): variant(other.variant) {}
    TonTransferPayload(TonTransferPayload &&other): variant(std::move(other.variant)) {}

    TonTransferPayload &operator=(const TonTransferPayload &other) {
        variant = other.variant;
        return *this;
    }

    TonTransferPayload &operator=(TonTransferPayload &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kNone, kText, kBoc> &get_variant() const {
        return variant;
    }

private:
    std::variant<kNone, kText, kBoc> variant;

    TonTransferPayload();
};


/**
 * One high-level action recognized by Toncenter during emulation.
 *
 * `details_json` preserves action-specific fields without making the stable
 * wallet API depend on Toncenter's growing list of action schemas.
 */
struct SendEmulationAction {
    /**
     * The validated action identifier in standard padded Base64.
     */
    Base64Hash action_id;
    /**
     * Toncenter's action kind, for example `ton_transfer` or `call_contract`.
     */
    NonEmptyString kind;
    /**
     * Whether Toncenter considers the complete high-level action successful.
     */
    bool succeeded;
    /**
     * Validated TON account addresses involved in the action.
     */
    std::vector<TonAddressString> accounts;
    /**
     * Validated transaction hashes associated with the action.
     */
    std::vector<Base64Hash> transaction_hashes;
    /**
     * Action-specific details serialized as a JSON object.
     */
    std::string details_json;
};


/**
 * A complete HTTP request for the embedding language.
 *
 * The host can add its Toncenter credential according to the actual request
 * URL and its local security policy. Redirects are forbidden.
 */
struct HttpRequest {
    /**
     * The identifier used for host cancellation.
     */
    HttpRequestId id;
    /**
     * The request method.
     */
    HttpMethod method;
    /**
     * The complete HTTPS request URL.
     */
    std::string url;
    /**
     * The public request headers. Credential material is not present here.
     */
    std::vector<HttpHeader> headers;
    /**
     * The request body. GET requests use an empty body.
     */
    std::vector<uint8_t> body;
    /**
     * End-to-end request timeout, in milliseconds.
     *
     * The host must enforce this deadline across connection setup, response
     * headers, and response-body reads. Expiry is reported as
     * [`HttpHostErrorKind::Timeout`].
     */
    uint64_t timeout_ms;
};

namespace uniffi {
struct FfiConverterNftTransferPayload;
} // namespace uniffi

/**
 * Optional payload delivered to the new owner with `ownership_assigned`.
 */
struct NftTransferPayload {
    friend uniffi::FfiConverterNftTransferPayload;
    /**
     * Use an empty forward payload.
     */
    struct kEmpty {
    };
    /**
     * Encode a zero opcode followed by UTF-8 TON snake data.
     */
    struct kComment {
        /**
         * Plaintext comment delivered to the new owner.
         */
        std::string text;
    };
    /**
     * Preserve one caller-built cell as the forward payload.
     */
    struct kRawPayload {
        /**
         * Complete payload cell encoded as a validated BOC.
         */
        Boc boc;
    };
    NftTransferPayload(kEmpty variant): variant(variant) {}
    NftTransferPayload(kComment variant): variant(variant) {}
    NftTransferPayload(kRawPayload variant): variant(variant) {}

    NftTransferPayload(const NftTransferPayload &other): variant(other.variant) {}
    NftTransferPayload(NftTransferPayload &&other): variant(std::move(other.variant)) {}

    NftTransferPayload &operator=(const NftTransferPayload &other) {
        variant = other.variant;
        return *this;
    }

    NftTransferPayload &operator=(NftTransferPayload &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kEmpty, kComment, kRawPayload> &get_variant() const {
        return variant;
    }

private:
    std::variant<kEmpty, kComment, kRawPayload> variant;

    NftTransferPayload();
};


/**
 * Two signed delivery forms prepared from the same wallet state.
 *
 * Both BOCs cover the same `seqno` and `valid_until`, so they are alternative
 * representations of one transfer. The caller must hand off only the delivery
 * form selected by its server. This result is not written to the send journal.
 */
struct PreparedTransfer {
    /**
     * The application operation identifier.
     */
    NonEmptyString operation_id;
    /**
     * Complete incoming external message for direct provider submission.
     */
    Boc external_boc;
    /**
     * Complete owner-signed internal message for a gas-paying relayer.
     */
    Boc internal_boc;
    /**
     * Fresh wallet sequence number covered by both signed messages.
     */
    uint32_t seqno;
    /**
     * Unix expiration timestamp covered by both signed messages.
     */
    uint64_t valid_until;
};

namespace uniffi {
struct FfiConverterTonTransferAsset;
} // namespace uniffi

/**
 * The asset selected by a parsed TON transfer link.
 */
struct TonTransferAsset {
    friend uniffi::FfiConverterTonTransferAsset;
    /**
     * Transfer Gram.
     */
    struct kGram {
    };
    /**
     * Transfer a jetton identified by its master contract.
     */
    struct kJetton {
        /**
         * The jetton master contract address.
         */
        TonAddressString master;
    };
    TonTransferAsset(kGram variant): variant(variant) {}
    TonTransferAsset(kJetton variant): variant(variant) {}

    TonTransferAsset(const TonTransferAsset &other): variant(other.variant) {}
    TonTransferAsset(TonTransferAsset &&other): variant(std::move(other.variant)) {}

    TonTransferAsset &operator=(const TonTransferAsset &other) {
        variant = other.variant;
        return *this;
    }

    TonTransferAsset &operator=(TonTransferAsset &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kGram, kJetton> &get_variant() const {
        return variant;
    }

private:
    std::variant<kGram, kJetton> variant;

    TonTransferAsset();
};


/**
 * Host-owned subset of the TypeScript protocol's `DeviceInfo`.
 *
 * Rust fills `maxProtocolVersion` and `features` from the methods the engine
 * actually implements, so platform clients cannot advertise unsupported
 * capabilities.
 */
struct TonConnectDevice {
    /**
     * Current wallet runtime platform.
     */
    TonConnectDevicePlatform platform;
    /**
     * Wallet registry identifier.
     */
    std::string app_name;
    /**
     * Wallet application version.
     */
    std::string app_version;
};


/**
 * Requests creation of the data needed for Wallet rev00 key rotation.
 */
struct PrepareKeyRotationRequest {
    /**
     * Unix timestamp covered by the current key's request signature.
     */
    uint64_t valid_until;
    /**
     * Delivery channel and channel-specific request opcode.
     */
    KeyRotationMessageKind message_kind;
};


/**
 * A secret-bearing one-shot command. It must never be persisted or logged.
 *
 * The words cross FFI because the user enters them. Rust validates and derives
 * the wallet. The host only provides protected storage.
 */
struct ImportWalletRequest {
    /**
     * The stable application record identifier for the imported wallet.
     */
    std::string record_id;
    /**
     * The network used to derive the wallet contract and address.
     */
    Network network;
    /**
     * Lowercase English words of a rotation mnemonic (TEP-0003 section 3.3).
     *
     * Pass the phrase exactly as the user recorded it: 12 words for a wallet
     * whose key was never rotated, or 24 words - two independently
     * checksummed BIP-39 halves - after rotation. The engine expands the
     * 12-word form itself; never duplicate the words in the application.
     *
     * [`detect_mnemonic_schemes`] classifies the same word vector without
     * importing it, so the application can explain a rejection.
     */
    std::vector<std::string> recovery_words;
};


/**
 * The result returned after the send workflow reaches a terminal phase.
 */
struct SendResult {
    /**
     * The application operation identifier.
     */
    NonEmptyString operation_id;
    /**
     * The normalized signed external-message hash in standard padded Base64.
     */
    Base64Hash message_hash;
    /**
     * The exact signed external-message `BoC` submitted to the provider.
     */
    Boc signed_boc;
    /**
     * The terminal phase. This can be [`SendPhase::SubmissionUnknown`].
     */
    SendPhase phase;
};


/**
 * Chain-derived metadata for an NFT collection.
 *
 * Product-specific classification, such as Telegram gifts or usernames,
 * intentionally stays outside the engine.
 */
struct NftCollectionDescriptor {
    /**
     * The NFT collection contract address.
     */
    TonAddressString address;
    /**
     * The standard TEP-64 collection name, when available.
     */
    std::optional<std::string> name;
    /**
     * The standard TEP-64 collection description, when available.
     */
    std::optional<std::string> description;
    /**
     * The standard TEP-64 collection image reference, when available.
     */
    std::optional<std::string> image;
    /**
     * All string-valued collection metadata returned by the provider.
     */
    std::unordered_map<std::string, std::string> content;
};


/**
 * The complete HTTP response returned by the host.
 */
struct HttpResponse {
    /**
     * The HTTP status code.
     */
    uint16_t status;
    /**
     * The response headers accepted by the host.
     */
    std::vector<HttpHeader> headers;
    /**
     * The response body accepted by the host.
     */
    std::vector<uint8_t> body;
    /**
     * The final response URL reported by the host. It must equal the request URL
     * because the engine does not permit redirects.
     */
    std::string final_url;
};

namespace uniffi {
struct FfiConverterSendMessageBody;
} // namespace uniffi

/**
 * The body encoded into one outgoing internal TON message.
 *
 * Select `Empty` for a value-only transfer, `Comment` for a standard text
 * comment, or `RawPayload` for a pre-serialized contract call.
 */
struct SendMessageBody {
    friend uniffi::FfiConverterSendMessageBody;
    /**
     * Encode no body bits or references.
     */
    struct kEmpty {
    };
    /**
     * Encode a zero opcode followed by the UTF-8 text as TON snake data.
     */
    struct kComment {
        /**
         * The plaintext UTF-8 comment, including an intentionally empty one.
         */
        std::string text;
    };
    /**
     * Preserve one caller-built cell as the internal-message body.
     */
    struct kRawPayload {
        /**
         * The complete body cell encoded as a validated BOC.
         */
        Boc boc;
    };
    SendMessageBody(kEmpty variant): variant(variant) {}
    SendMessageBody(kComment variant): variant(variant) {}
    SendMessageBody(kRawPayload variant): variant(variant) {}

    SendMessageBody(const SendMessageBody &other): variant(other.variant) {}
    SendMessageBody(SendMessageBody &&other): variant(std::move(other.variant)) {}

    SendMessageBody &operator=(const SendMessageBody &other) {
        variant = other.variant;
        return *this;
    }

    SendMessageBody &operator=(SendMessageBody &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kEmpty, kComment, kRawPayload> &get_variant() const {
        return variant;
    }

private:
    std::variant<kEmpty, kComment, kRawPayload> variant;

    SendMessageBody();
};

namespace uniffi {
struct FfiConverterProtectedSecretHostError;
} // namespace uniffi

/**
 * A protected-storage failure returned by [`crate::WalletPlatformHost`].
 */
struct ProtectedSecretHostError: std::runtime_error {
    friend uniffi::FfiConverterProtectedSecretHostError;

    ProtectedSecretHostError() : std::runtime_error("") {}
    ProtectedSecretHostError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~ProtectedSecretHostError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of ProtectedSecretHostError
 */
namespace protected_secret_host_error {

/**
 * Reports a classified host failure with a safe diagnostic message.
 */
struct Failed: ProtectedSecretHostError {
    ProtectedSecretHostErrorKind kind;
    std::string diagnostic;

    Failed() : ProtectedSecretHostError("") {}
    Failed(const std::string &what_arg) : ProtectedSecretHostError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};
} // namespace protected_secret_host_error


/**
 * Configures the Toncenter endpoint.
 */
struct ProviderConfig {
    /**
     * The HTTPS base URL for a Toncenter-compatible provider.
     *
     * The engine appends the path for the API used by each request. An optional
     * deployment prefix is preserved, so `https://provider.example/toncenter`
     * produces paths below `/toncenter/api/...`.
     * Loopback HTTP URLs are accepted for local development networks.
     * Supply an already serialized ASCII URL, with any path escaping applied
     * and no credentials, query, or fragment. The engine does not normalize it.
     */
    std::string toncenter_base_url;
    /**
     * Optional TON DNS root resolver override.
     *
     * When absent, the engine selects the built-in current root for the wallet
     * network. Set an explicit address to update the resolver without changing
     * the engine.
     */
    std::optional<TonAddressString> dns_root_address;
    /**
     * End-to-end timeout applied to every provider request, in milliseconds.
     *
     * The selected provider host must enforce this deadline across its
     * complete HTTP, relay, or protocol-proxy call.
     */
    uint64_t request_timeout_ms;
};


/**
 * Requests generation of a new wallet account.
 */
struct CreateWalletRequest {
    /**
     * Stable application identity, normally a UUID generated by the host.
     */
    std::string record_id;
    /**
     * The network used to derive the wallet contract and address.
     */
    Network network;
};

namespace uniffi {
struct FfiConverterWalletClientError;
} // namespace uniffi

/**
 * An operational failure returned by [`crate::WalletClient`].
 */
struct WalletClientError: std::runtime_error {
    friend uniffi::FfiConverterWalletClientError;

    WalletClientError() : std::runtime_error("") {}
    WalletClientError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~WalletClientError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of WalletClientError
 */
namespace wallet_client_error {

/**
 * The configured protected-secret reference is blank.
 */
struct InvalidLocalSecretReference: WalletClientError {

    InvalidLocalSecretReference() : WalletClientError("") {}
    InvalidLocalSecretReference(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};

/**
 * The configured public key cannot derive the wallet state.
 */
struct InvalidWalletPublicKey: WalletClientError {

    InvalidWalletPublicKey() : WalletClientError("") {}
    InvalidWalletPublicKey(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 2;
    }
};

/**
 * The configured address does not belong to the public key and network.
 */
struct WalletIdentityMismatch: WalletClientError {

    WalletIdentityMismatch() : WalletClientError("") {}
    WalletIdentityMismatch(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 3;
    }
};

/**
 * The configured provider base cannot be extended with an endpoint path.
 */
struct InvalidProviderBaseUrl: WalletClientError {

    InvalidProviderBaseUrl() : WalletClientError("") {}
    InvalidProviderBaseUrl(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 4;
    }
};

/**
 * The transfer request has an invalid operation ID, destination, or amount.
 */
struct InvalidSendRequest: WalletClientError {

    InvalidSendRequest() : WalletClientError("") {}
    InvalidSendRequest(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 5;
    }
};

/**
 * NFT transfer validation or TEP-62 message construction failed before signing.
 */
struct NftTransferUnavailable: WalletClientError {
    std::string diagnostic;

    NftTransferUnavailable() : WalletClientError("") {}
    NftTransferUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 6;
    }
};

/**
 * Emulation did not prove a complete successful NFT ownership transfer.
 */
struct NftTransferEmulationRejected: WalletClientError {
    std::string diagnostic;

    NftTransferEmulationRejected() : WalletClientError("") {}
    NftTransferEmulationRejected(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 7;
    }
};

/**
 * Encrypted-comment preparation or decryption failed safely.
 */
struct EncryptedCommentUnavailable: WalletClientError {
    std::string diagnostic;

    EncryptedCommentUnavailable() : WalletClientError("") {}
    EncryptedCommentUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 8;
    }
};

/**
 * TON DNS validation, provider resolution, or wallet-record parsing failed.
 */
struct DnsResolutionUnavailable: WalletClientError {
    std::string diagnostic;

    DnsResolutionUnavailable() : WalletClientError("") {}
    DnsResolutionUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 9;
    }
};

/**
 * Key-rotation chain-state loading or signed-message construction failed.
 */
struct KeyRotationUnavailable: WalletClientError {
    std::string diagnostic;

    KeyRotationUnavailable() : WalletClientError("") {}
    KeyRotationUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 10;
    }
};

/**
 * The wallet has public identity but no protected secret configured for local signing.
 */
struct LocalSigningUnavailable: WalletClientError {

    LocalSigningUnavailable() : WalletClientError("") {}
    LocalSigningUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 11;
    }
};

/**
 * A client-local identifier or revision counter overflowed.
 */
struct IdentifierExhausted: WalletClientError {

    IdentifierExhausted() : WalletClientError("") {}
    IdentifierExhausted(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 12;
    }
};

/**
 * The internal state lock or active operation state is unavailable.
 */
struct StateUnavailable: WalletClientError {

    StateUnavailable() : WalletClientError("") {}
    StateUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 13;
    }
};

/**
 * Another signed message is already being prepared or submitted by this client.
 */
struct SendAlreadyInProgress: WalletClientError {

    SendAlreadyInProgress() : WalletClientError("") {}
    SendAlreadyInProgress(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 14;
    }
};

/**
 * Another send preview is already fetching or emulating current chain state.
 */
struct SendPreviewAlreadyInProgress: WalletClientError {

    SendPreviewAlreadyInProgress() : WalletClientError("") {}
    SendPreviewAlreadyInProgress(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 15;
    }
};

/**
 * A durable prior submission has no definite provider outcome.
 *
 * The caller must not create a replacement transfer because the stored
 * signed message can already be on the network.
 */
struct PreviousSubmissionUnresolved: WalletClientError {

    PreviousSubmissionUnresolved() : WalletClientError("") {}
    PreviousSubmissionUnresolved(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 16;
    }
};

/**
 * The provider still reports the sequence number used by the previous send.
 *
 * Refresh chain state and retry only after the wallet sequence number advances.
 */
struct WalletSeqnoNotAdvanced: WalletClientError {

    WalletSeqnoNotAdvanced() : WalletClientError("") {}
    WalletSeqnoNotAdvanced(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 17;
    }
};

/**
 * The current on-chain account status does not permit a transfer.
 */
struct SendAccountUnavailable: WalletClientError {
    AccountStatus status;

    SendAccountUnavailable() : WalletClientError("") {}
    SendAccountUnavailable(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 18;
    }
};

/**
 * The fresh on-chain balance is smaller than the requested transfer value.
 *
 * This check excludes network fees. A transfer can still fail on-chain
 * when its value fits but the remaining balance cannot pay fees.
 */
struct InsufficientBalance: WalletClientError {
    UnsignedDecimalString available_nanograms;
    UnsignedDecimalString requested_nanograms;

    InsufficientBalance() : WalletClientError("") {}
    InsufficientBalance(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 19;
    }
};

/**
 * The requested exact value fits, but the value and emulated wallet fee do not.
 */
struct InsufficientBalanceForFees: WalletClientError {
    UnsignedDecimalString available_nanograms;
    UnsignedDecimalString requested_nanograms;
    UnsignedDecimalString estimated_fee_nanograms;

    InsufficientBalanceForFees() : WalletClientError("") {}
    InsufficientBalanceForFees(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 20;
    }
};

/**
 * The protected secret cannot be decoded as a valid wallet recovery phrase.
 */
struct InvalidProtectedSecret: WalletClientError {

    InvalidProtectedSecret() : WalletClientError("") {}
    InvalidProtectedSecret(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 21;
    }
};

/**
 * A preview failed while loading fresh wallet state or building its fake-signed message.
 */
struct SendPreviewFailed: WalletClientError {
    std::string diagnostic;

    SendPreviewFailed() : WalletClientError("") {}
    SendPreviewFailed(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 22;
    }
};

/**
 * Toncenter could not execute or decode the fake-signed preview emulation.
 */
struct EmulationFailed: WalletClientError {
    std::string diagnostic;

    EmulationFailed() : WalletClientError("") {}
    EmulationFailed(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 23;
    }
};

/**
 * The emulator ran correctly, but the current wallet state did not accept
 * the external message, for example because its seqno became stale.
 */
struct EmulationMessageNotAccepted: WalletClientError {
    std::string diagnostic;

    EmulationMessageNotAccepted() : WalletClientError("") {}
    EmulationMessageNotAccepted(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 24;
    }
};

/**
 * Emulation proved that the source wallet transaction would not complete.
 */
struct EmulationRejected: WalletClientError {
    std::string diagnostic;
    std::optional<int32_t> compute_exit_code;
    std::optional<int32_t> action_result_code;

    EmulationRejected() : WalletClientError("") {}
    EmulationRejected(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 25;
    }
};

/**
 * A send failed before submission became ambiguous.
 */
struct SendFailed: WalletClientError {
    std::string diagnostic;

    SendFailed() : WalletClientError("") {}
    SendFailed(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 26;
    }
};

/**
 * A send can have reached the provider, but no definite result is available.
 */
struct SubmissionUnknown: WalletClientError {
    std::string diagnostic;

    SubmissionUnknown() : WalletClientError("") {}
    SubmissionUnknown(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 27;
    }
};

/**
 * A send crossed its durable commit boundary and cannot be cancelled.
 */
struct SendCancellationTooLate: WalletClientError {

    SendCancellationTooLate() : WalletClientError("") {}
    SendCancellationTooLate(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 28;
    }
};

/**
 * The client is shut down and accepts no new work.
 */
struct Shutdown: WalletClientError {

    Shutdown() : WalletClientError("") {}
    Shutdown(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 29;
    }
};

/**
 * Provider data an encrypted-comment operation needs could not be read now.
 *
 * For encryption, the recipient's public key lookup failed, was limited,
 * or was cancelled, so nothing is known about the recipient. For
 * decryption, the wallet's key-change history could not be read or does
 * not reach the current signing key yet, so earlier signing keys could
 * not be tried. The same request can be retried. It is never returned
 * when the recipient is known to be unable to receive an encrypted
 * comment or when every key of this wallet was tried; that is
 * [`Self::EncryptedCommentUnavailable`]. The display text keeps its
 * original "recipient lookup" wording for compatibility.
 */
struct EncryptedCommentLookupFailed: WalletClientError {
    std::string diagnostic;

    EncryptedCommentLookupFailed() : WalletClientError("") {}
    EncryptedCommentLookupFailed(const std::string &what_arg) : WalletClientError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 30;
    }
};
} // namespace wallet_client_error


/**
 * One account transaction observed in an emulated message trace.
 */
struct SendEmulationTransaction {
    /**
     * Account whose contract executed this transaction.
     */
    TonAddressString account;
    /**
     * Whether the observed compute and action phases contain no explicit failure.
     */
    bool succeeded;
    /**
     * Whether this is the source wallet transaction at the root of the trace.
     */
    bool is_root;
};


/**
 * Chain evidence and retry guidance for a durable outgoing message.
 */
struct ResolutionInfo {
    /**
     * The confirmed transaction hash, when the message was executed.
     */
    std::optional<Base64Hash> transaction_hash;
    /**
     * The confirmed transaction logical time.
     */
    std::optional<UnsignedDecimalString> transaction_lt;
    /**
     * Why the message remains unresolved, when no terminal evidence exists.
     */
    std::optional<PendingReason> pending_reason;
    /**
     * Whether a new send with `force` can replace this unresolved signed send.
     */
    bool can_force_retry;
    /**
     * A UI polling hint. This is not a correctness deadline.
     */
    std::optional<uint64_t> retry_after_hint_ms;
};

namespace uniffi {
struct FfiConverterSendAmount;
} // namespace uniffi

/**
 * The transfer value policy applied by the wallet contract.
 */
struct SendAmount {
    friend uniffi::FfiConverterSendAmount;
    /**
     * Send one exact nonnegative value and pay network fees separately.
     */
    struct kExact {
        /**
         * The exact value, in nanograms.
         */
        UnsignedDecimalString nanograms;
    };
    /**
     * Send the complete remaining wallet balance after network fees.
     */
    struct kAll {
    };
    SendAmount(kExact variant): variant(variant) {}
    SendAmount(kAll variant): variant(variant) {}

    SendAmount(const SendAmount &other): variant(other.variant) {}
    SendAmount(SendAmount &&other): variant(std::move(other.variant)) {}

    SendAmount &operator=(const SendAmount &other) {
        variant = other.variant;
        return *this;
    }

    SendAmount &operator=(SendAmount &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kExact, kAll> &get_variant() const {
        return variant;
    }

private:
    std::variant<kExact, kAll> variant;

    SendAmount();
};

namespace uniffi {
struct FfiConverterHttpHostError;
} // namespace uniffi

/**
 * An HTTP failure returned by [`WalletHttpHost`].
 */
struct HttpHostError: std::runtime_error {
    friend uniffi::FfiConverterHttpHostError;

    HttpHostError() : std::runtime_error("") {}
    HttpHostError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~HttpHostError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of HttpHostError
 */
namespace http_host_error {

/**
 * Reports a classified host failure with a safe diagnostic message.
 */
struct Failed: HttpHostError {
    HttpHostErrorKind kind;
    std::string diagnostic;

    Failed() : HttpHostError("") {}
    Failed(const std::string &what_arg) : HttpHostError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};
} // namespace http_host_error


/**
 * Structured error data for account and activity resources.
 */
struct DomainError {
    /**
     * The stable machine-readable code.
     */
    ErrorCode code;
    /**
     * The broad source of the error.
     */
    ErrorCategory category;
    /**
     * Retry guidance for the failed operation.
     */
    RetryAdvice retry;
    /**
     * A sanitized developer-facing diagnostic with a bounded length.
     */
    std::string developer_message;
    /**
     * The provider status code, if the provider returned one.
     */
    std::optional<uint16_t> provider_status;
    /**
     * The provider delay in milliseconds, if it returned a numeric `Retry-After` header.
     */
    std::optional<uint64_t> retry_after_ms;
    /**
     * The normalized host failure kind, if the error came from a callback.
     *
     * Status-less host kinds map to the corresponding legacy HTTP kind so
     * existing consumers retain one stable classification field.
     */
    std::optional<HttpHostErrorKind> host_kind;
};

namespace uniffi {
struct FfiConverterJournalHostError;
} // namespace uniffi

/**
 * A durable journal failure returned by [`crate::WalletPlatformHost`].
 */
struct JournalHostError: std::runtime_error {
    friend uniffi::FfiConverterJournalHostError;

    JournalHostError() : std::runtime_error("") {}
    JournalHostError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~JournalHostError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of JournalHostError
 */
namespace journal_host_error {

/**
 * Reports a classified journal failure with a safe diagnostic message.
 */
struct Failed: JournalHostError {
    JournalHostErrorKind kind;
    std::string diagnostic;

    Failed() : JournalHostError("") {}
    Failed(const std::string &what_arg) : JournalHostError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};
} // namespace journal_host_error


/**
 * A `signData` request already validated against the session's wallet.
 */
struct TonConnectSignDataRequest {
    /**
     * The data to show and sign.
     */
    TonConnectSignDataPayload payload;
    /**
     * The network global ID the dApp named, exactly as sent; it matches the wallet.
     */
    std::optional<std::string> network;
    /**
     * The signer address the dApp named, exactly as sent; it is the wallet's address.
     */
    std::optional<std::string> from;
};


/**
 * Public, persistable wallet metadata. Recovery words never belong here.
 */
struct WalletDescriptor {
    /**
     * The stable application record identifier.
     */
    std::string record_id;
    /**
     * The derived friendly non-bounceable TON address.
     */
    TonAddressString address;
    /**
     * The raw 32-byte Ed25519 anchor public key of the rotation mnemonic.
     *
     * The anchor key determines the wallet account address and never
     * changes, so this is safe to persist. It lets the engine emulate a
     * first deployment before it asks the host to unlock the recovery
     * phrase.
     */
    std::vector<uint8_t> public_key;
    /**
     * The network used for derivation and future operations.
     */
    Network network;
    /**
     * The host protected-storage reference for the mnemonic.
     */
    ProtectedSecretRef secret_ref;
};


/**
 * A durable owner-signed internal message returned to its caller for relaying.
 */
struct SignMessageResult {
    /**
     * The application operation identifier.
     */
    NonEmptyString operation_id;
    /**
     * The complete relaxed internal message encoded as a validated BOC.
     */
    Boc internal_boc;
    /**
     * The Unix expiration timestamp covered by the signature.
     */
    uint64_t valid_until;
    /**
     * The durable workflow phase after the message becomes available to the caller.
     */
    SendPhase phase;
};


/**
 * The result of an atomic journal compare-and-swap operation.
 */
struct JournalCompareExchangeResult {
    /**
     * Whether the host stored the replacement.
     */
    bool applied;
    /**
     * The current record after the operation, if one exists.
     */
    std::optional<JournalRecord> current;
};

namespace uniffi {
struct FfiConverterStatuslessHostError;
} // namespace uniffi

/**
 * A failure returned by [`WalletStatuslessHost`].
 */
struct StatuslessHostError: std::runtime_error {
    friend uniffi::FfiConverterStatuslessHostError;

    StatuslessHostError() : std::runtime_error("") {}
    StatuslessHostError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~StatuslessHostError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of StatuslessHostError
 */
namespace statusless_host_error {

/**
 * Reports a classified transport failure with a safe diagnostic message.
 */
struct Failed: StatuslessHostError {
    StatuslessHostErrorKind kind;
    std::string diagnostic;

    Failed() : StatuslessHostError("") {}
    Failed(const std::string &what_arg) : StatuslessHostError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};
} // namespace statusless_host_error

namespace uniffi {
struct FfiConverterWalletLifecycleError;
} // namespace uniffi

/**
 * A wallet creation, import, secret, or derivation failure.
 */
struct WalletLifecycleError: std::runtime_error {
    friend uniffi::FfiConverterWalletLifecycleError;

    WalletLifecycleError() : std::runtime_error("") {}
    WalletLifecycleError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~WalletLifecycleError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of WalletLifecycleError
 */
namespace wallet_lifecycle_error {

/**
 * The record ID is empty, too long, or contains unsupported characters.
 */
struct InvalidRecordId: WalletLifecycleError {

    InvalidRecordId() : WalletLifecycleError("") {}
    InvalidRecordId(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};

/**
 * The recovery phrase is not a valid 12- or 24-word Rotation mnemonic.
 *
 * [`detect_mnemonic_schemes`] reports whether the words form a TON or
 * 24-word BIP-39 mnemonic instead, so the application can say why the
 * phrase was rejected.
 */
struct InvalidRecoveryPhrase: WalletLifecycleError {

    InvalidRecoveryPhrase() : WalletLifecycleError("") {}
    InvalidRecoveryPhrase(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 2;
    }
};

/**
 * Rust cannot construct the requested wallet address.
 */
struct AddressDerivationFailed: WalletLifecycleError {

    AddressDerivationFailed() : WalletLifecycleError("") {}
    AddressDerivationFailed(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 3;
    }
};

/**
 * The TON Connect proof fields or signing key are invalid.
 */
struct TonConnectSigningFailed: WalletLifecycleError {

    TonConnectSigningFailed() : WalletLifecycleError("") {}
    TonConnectSigningFailed(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 4;
    }
};

/**
 * The protected mnemonic derives a different address from the descriptor.
 */
struct SecretWalletMismatch: WalletLifecycleError {

    SecretWalletMismatch() : WalletLifecycleError("") {}
    SecretWalletMismatch(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 5;
    }
};

/**
 * The host reported a protected-storage failure.
 */
struct ProtectedSecretHost: WalletLifecycleError {
    ProtectedSecretHostErrorKind kind;
    std::string diagnostic;

    ProtectedSecretHost() : WalletLifecycleError("") {}
    ProtectedSecretHost(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 6;
    }
};

/**
 * The dApp client id is not 64 lowercase hex characters or the session nonce is empty.
 */
struct InvalidTonConnectSessionInput: WalletLifecycleError {

    InvalidTonConnectSessionInput() : WalletLifecycleError("") {}
    InvalidTonConnectSessionInput(const std::string &what_arg) : WalletLifecycleError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 7;
    }
};
} // namespace wallet_lifecycle_error

namespace uniffi {
struct FfiConverterNftTransferFunding;
} // namespace uniffi

/**
 * The TON amounts used to execute one NFT item transfer.
 *
 * Both values are deliberately explicit. The engine does not choose a gas
 * deposit or recipient notification amount on behalf of the application.
 */
struct NftTransferFunding {
    friend uniffi::FfiConverterNftTransferFunding;
    /**
     * Attach an exact amount to the NFT item call and forward an exact amount
     * with the ownership notification.
     */
    struct kExact {
        /**
         * Value attached to the internal message sent to the NFT item contract.
         */
        UnsignedDecimalString attached_nanograms;
        /**
         * Value forwarded to the new owner in `ownership_assigned`.
         */
        UnsignedDecimalString forward_nanograms;
    };
    NftTransferFunding(kExact variant): variant(variant) {}

    NftTransferFunding(const NftTransferFunding &other): variant(other.variant) {}
    NftTransferFunding(NftTransferFunding &&other): variant(std::move(other.variant)) {}

    NftTransferFunding &operator=(const NftTransferFunding &other) {
        variant = other.variant;
        return *this;
    }

    NftTransferFunding &operator=(NftTransferFunding &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kExact> &get_variant() const {
        return variant;
    }

private:
    std::variant<kExact> variant;

    NftTransferFunding();
};


/**
 * One-shot data prepared for a Wallet rev00 signing-key rotation.
 *
 * This value does not mean that rotation succeeded on-chain. Before
 * submission, the host must store `replacement_recovery_phrase` in protected
 * storage. It must store `signed_boc` as a pending durable record. Words 13-24
 * are the new signing half. Until chain state resolves the request, the host
 * must block ordinary signing.
 *
 * The signed request also carries `encryptedOldPrivateKey`, computed over
 * 32-byte Ed25519 seeds as
 * `sha256(new_private_key ‖ "keyChangeSaltV1") XOR old_private_key`, where the
 * old key is the signing key being replaced. Only the new key opens it. The
 * contract publishes it in its key-changed log, so
 * [`crate::WalletClient::decrypt_comment`] can later recover the replaced key.
 * The engine adds it to `signed_boc`; the host handles nothing extra.
 */
struct PreparedKeyRotation {
    /**
     * Full 24-word phrase that preserves the anchor and contains the new signing half.
     */
    RecoveryPhrase replacement_recovery_phrase;
    /**
     * New raw 32-byte Ed25519 signing public key stored by the contract on success.
     */
    std::vector<uint8_t> new_public_key;
    /**
     * Complete external or relaxed internal signed message as a validated BOC.
     *
     * Its `ChangePublicKey` request holds two references: the new key's
     * wallet-address proof signature and the encrypted old private key.
     */
    Boc signed_boc;
    /**
     * External delivery form for the same replacement key, sequence and expiration.
     */
    Boc external_boc;
    /**
     * Internal alternative; submit only one of the two delivery forms.
     */
    Boc internal_boc;
    /**
     * Sequence number covered by the signed request.
     */
    uint32_t seqno;
    /**
     * Unix expiration timestamp covered by the signed request.
     */
    uint64_t valid_until;
    /**
     * Delivery channel encoded into the request opcode.
     */
    KeyRotationMessageKind message_kind;
};


/**
 * Asks which public key a TON encrypted comment for a recipient would use.
 *
 * The fields mean the same as in [`CreateEncryptedCommentRequest`], so the
 * answer predicts whether that request can encrypt, without a comment and
 * without any protected-secret access.
 */
struct EncryptedCommentRecipientRequest {
    /**
     * Recipient wallet address.
     */
    TonAddressString recipient;
    /**
     * Optional 32-byte Ed25519 public key used instead of an on-chain lookup.
     *
     * The host authenticates it as described by
     * [`CreateEncryptedCommentRequest::recipient_public_key`].
     */
    std::optional<std::vector<uint8_t>> recipient_public_key = std::nullopt;
};


/**
 * Requests a TON Connect `signData` signature with the wallet's current signing key.
 */
struct TonConnectSignDataSignRequest {
    /**
     * Wallet whose protected key must sign the data.
     */
    WalletDescriptor descriptor;
    /**
     * The decoded request to sign.
     */
    TonConnectSignDataRequest request;
    /**
     * Exact dApp manifest domain shown to and approved by the user.
     */
    std::string domain;
    /**
     * Unix signing time in seconds.
     */
    uint64_t timestamp;
};


/**
 * Result of creating a wallet.
 *
 * The descriptor is safe to persist. The recovery phrase is transient and
 * exists only so the UI can show it once.
 */
struct CreatedWallet {
    /**
     * Metadata that the application must persist.
     */
    WalletDescriptor descriptor;
    /**
     * A transient phrase that the application can present once.
     */
    RecoveryPhrase recovery_phrase;
};


/**
 * One NFT item returned by the configured Toncenter v3 provider.
 *
 * `content` contains the string-valued on-chain and indexed metadata fields.
 * Common keys include `name`, `description`, `image`, `image_url`, `preview`,
 * `uri`, and `collection_name`.
 */
struct NftItem {
    /**
     * The NFT item contract address.
     */
    TonAddressString address;
    /**
     * The collection contract address, if this item belongs to a collection.
     */
    std::optional<TonAddressString> collection_address;
    /**
     * Chain-derived collection metadata, if this item belongs to a collection.
     */
    std::optional<NftCollectionDescriptor> collection;
    /**
     * The current owner reported by the NFT item contract.
     */
    std::optional<TonAddressString> owner_address;
    /**
     * The effective owner while a sale contract owns the item.
     */
    std::optional<TonAddressString> real_owner;
    /**
     * The active sale contract address, if the item is on sale.
     */
    std::optional<TonAddressString> sale_contract_address;
    /**
     * The active auction contract address, if the item is in an auction.
     */
    std::optional<TonAddressString> auction_contract_address;
    /**
     * The item index inside its collection.
     */
    UnsignedDecimalString index;
    /**
     * The logical time of the item's latest indexed transaction.
     */
    UnsignedDecimalString last_transaction_lt;
    /**
     * Whether the item contract has initialized NFT data.
     */
    bool initialized;
    /**
     * Whether the provider reports an active sale or auction.
     */
    bool on_sale;
    /**
     * The item contract code hash in the provider representation.
     */
    std::string code_hash;
    /**
     * The item contract data hash in the provider representation.
     */
    std::string data_hash;
    /**
     * String-valued item metadata after Toncenter metadata enrichment.
     */
    std::unordered_map<std::string, std::string> content;
    /**
     * The provider's content-safety classification, when available.
     */
    std::optional<bool> is_nsfw;
    /**
     * The provider's scam classification, when available.
     */
    std::optional<bool> is_scam;
};


/**
 * A signed TON Connect `signData` request, ready for the success response.
 */
struct TonConnectSignedData {
    /**
     * The signed request.
     */
    TonConnectSignDataRequest request;
    /**
     * The domain bound into the signature.
     */
    std::string domain;
    /**
     * The Unix signing time in seconds bound into the signature.
     */
    uint64_t timestamp;
    /**
     * Exact 64-byte Ed25519 signature.
     */
    std::vector<uint8_t> signature;
    /**
     * The current 32-byte Ed25519 signing public key that made the signature.
     */
    std::vector<uint8_t> public_key;
};


/**
 * One outgoing internal TON message before wallet-contract serialization.
 */
struct SendMessage {
    /**
     * A friendly or raw TON destination address.
     */
    TonAddressString destination;
    /**
     * The exact-value or whole-balance transfer policy.
     */
    SendAmount amount;
    /**
     * The single body representation encoded into the message.
     */
    SendMessageBody body;
    /**
     * Whether a failed destination-contract call returns the remaining value.
     *
     * Ordinary account transfers normally keep this false. Contract calls,
     * including NFT item transfers, must enable it.
     */
    bool bounce;
    /**
     * Optional destination-contract `StateInit` attached independently of the body.
     */
    std::optional<Boc> state_init;
};


/**
 * A resource phase and its optional failure details.
 */
struct ResourceState {
    /**
     * The current resource phase.
     */
    ResourcePhase phase;
    /**
     * The last error. This value is present only for [`ResourcePhase::Failed`].
     */
    std::optional<DomainError> error;
};


/**
 * The observable state of the send workflow.
 */
struct SendSnapshot {
    /**
     * The active or last operation identifier, if a send started.
     */
    std::optional<NonEmptyString> operation_id;
    /**
     * The current public phase.
     */
    SendPhase phase;
    /**
     * A sanitized diagnostic for failed or unknown submission states.
     */
    std::optional<std::string> error_message;
    /**
     * Resolution evidence or pending guidance for the durable outgoing message.
     */
    std::optional<ResolutionInfo> resolution;
};


/**
 * A syntax-validated transfer invoice parsed from a `ton://transfer/` link.
 *
 * This value is not an executable send request. Network policy, expiration,
 * asset resolution, bounce selection, emulation, and user approval remain the
 * responsibility of the later admission and send stages.
 */
struct ParsedTonTransferLink {
    /**
     * The Gram recipient or the jetton owner's account address.
     */
    TonAddressString recipient;
    /**
     * The asset requested by the link.
     */
    TonTransferAsset asset;
    /**
     * The exact amount in the asset's elementary units, when supplied.
     */
    std::optional<UnsignedDecimalString> amount;
    /**
     * The optional decoded text or binary payload.
     */
    TonTransferPayload payload;
    /**
     * The requested Unix expiration, or the engine-default policy when absent.
     */
    SendExpiration expiration;
};


/**
 * Inputs of one derived `MTProto` TON Connect session.
 */
struct TonConnectDerivedSessionRequest {
    /**
     * Wallet whose current signing key derives the session key.
     */
    WalletDescriptor descriptor;
    /**
     * The dApp client id `A`: exactly 64 lowercase hex characters.
     */
    std::string dapp_client_id;
    /**
     * The raw per-session nonce issued by the server. It must not be empty.
     */
    std::vector<uint8_t> nonce;
};


/**
 * Requests an address-bound off-chain TON Connect ownership proof signature.
 */
struct TonConnectProofSignRequest {
    /**
     * Wallet whose protected key must sign the proof.
     */
    WalletDescriptor descriptor;
    /**
     * Exact manifest domain shown to and approved by the user.
     */
    std::string domain;
    /**
     * Unix signing timestamp in seconds.
     */
    uint64_t timestamp;
    /**
     * Exact dApp challenge from the `ton_proof` connect item.
     */
    std::string payload;
};


/**
 * Identifies one wallet client and its provider configuration.
 */
struct WalletClientConfig {
    /**
     * The stable application record identifier for this wallet.
     */
    NonEmptyString record_id;
    /**
     * The friendly TON address that the client reads and sends from.
     */
    TonAddressString address;
    /**
     * The raw 32-byte Ed25519 public key used for wallet preflight.
     *
     * For an undeployed wallet, this must be the initial key that derives its
     * address and `StateInit`. For an active wallet, this can be the current
     * signing key after rotation; it need not derive the wallet address.
     */
    std::vector<uint8_t> public_key;
    /**
     * The protected mnemonic reference used for local signing.
     *
     * `None` configures a public-key-only wallet. Read operations and send
     * previews remain available, while [`crate::WalletClient::send`] returns
     * [`crate::WalletClientError::LocalSigningUnavailable`].
     */
    std::optional<ProtectedSecretRef> local_secret_ref;
    /**
     * The network for the address and all provider requests.
     */
    Network network;
    /**
     * Lifetime of a newly signed external message, in seconds.
     *
     * The engine adds this value to the synchronization timestamp returned by
     * the fresh account-state request. It does not use the host device clock.
     * A short value, including zero, can expire before the network includes
     * the message. A long value extends the period in which it can be submitted.
     */
    uint64_t send_validity_seconds;
    /**
     * Additional provider-time margin before an unseen message becomes expired.
     *
     * This protects against declaring expiration while the transaction index is
     * still catching up with the account state used by the resolver.
     */
    uint64_t resolution_margin_seconds;
    /**
     * The provider endpoint.
     */
    ProviderConfig providers;
};


/**
 * A bounded summary of the Toncenter trace emulated before authorization.
 */
struct SendEmulation {
    /**
     * The masterchain block used as the emulation state snapshot.
     */
    uint32_t mc_block_seqno;
    /**
     * Fees charged by the source wallet transaction, in nanograms.
     */
    UnsignedDecimalString wallet_fees_nanograms;
    /**
     * Sum of fees across every transaction currently present in the trace.
     */
    UnsignedDecimalString trace_fees_nanograms;
    /**
     * Number of transactions currently present in the emulated trace.
     */
    uint64_t transaction_count;
    /**
     * High-level actions recognized by Toncenter in the emulated trace.
     */
    std::vector<SendEmulationAction> actions;
    /**
     * Per-account transaction outcomes observed before optional action parsing.
     *
     * This evidence lets typed contract flows validate execution even when a
     * Toncenter deployment does not recognize the corresponding high-level action.
     */
    std::vector<SendEmulationTransaction> transactions;
    /**
     * Whether every returned transaction completed without an observed phase failure.
     *
     * A false value does not by itself block submission. A recipient can
     * reject or bounce after the source wallet transaction succeeds.
     */
    bool trace_succeeded;
    /**
     * Whether Toncenter reports that the trace still has unresolved messages.
     */
    bool is_incomplete;
};


/**
 * Immutable choices for a TEP-62 NFT item transfer.
 */
struct NftTransferIntent {
    /**
     * NFT item contract that currently owns the transferable item state.
     */
    TonAddressString nft_address;
    /**
     * Account that becomes the new NFT owner.
     */
    TonAddressString recipient;
    /**
     * Explicit attached and forwarded TON values.
     */
    NftTransferFunding funding;
    /**
     * Payload forwarded to the new owner.
     */
    NftTransferPayload payload;
    /**
     * Wallet message expiration policy.
     */
    SendExpiration expiration;
};


/**
 * Requests fresh validation and emulation of an NFT transfer.
 *
 * Reuse the same operation ID in [`NftTransferRequest`] after confirmation so
 * both operations use the same deterministic TEP-62 `query_id`.
 */
struct NftTransferPreviewRequest {
    /**
     * Application-generated correlation identifier.
     */
    NonEmptyString operation_id;
    /**
     * Immutable NFT transfer choices.
     */
    NftTransferIntent intent;
};


/**
 * The complete transfer intent shared by preview and signed-send operations.
 *
 * Applications can preview this value and then pass the same value to a
 * signed send after the user confirms it.
 */
struct SendIntent {
    /**
     * The expiration policy for the wallet message.
     */
    SendExpiration expiration;
    /**
     * The non-empty ordered outgoing internal-message batch.
     *
     * Wallet V5 accepts at most 255 actions. The engine validates that limit
     * before emulation or secret authorization.
     */
    std::vector<SendMessage> messages;
};


/**
 * A validated internal-message signing preview without submission fee claims.
 */
struct SignMessagePreview {
    /**
     * The complete ordered outgoing message batch covered by the future signature.
     */
    std::vector<SendMessage> messages;
    /**
     * The resolved Unix expiration timestamp that the signer will preserve.
     */
    uint64_t valid_until;
    /**
     * Whether fresh account state requires the relayed message to carry wallet `StateInit`.
     */
    bool needs_state_init;
};


/**
 * Paginated wallet activity and its independent load states.
 */
struct ActivityList {
    /**
     * Activity in descending logical-time order with duplicate items removed.
     */
    std::vector<ActivityItem> items;
    /**
     * The load state for the first activity page.
     */
    ResourceState resource;
    /**
     * The load state for an additional activity page.
     */
    ResourceState pagination_resource;
    /**
     * Whether the provider can have another activity page.
     */
    bool has_more;
};


/**
 * Requests one owner-signed TEP-62 NFT transfer.
 */
struct NftTransferRequest {
    /**
     * Unique idempotency identifier chosen by the application.
     */
    NonEmptyString operation_id;
    /**
     * Allows replacement of an unresolved durable send after explicit confirmation.
     */
    bool force;
    /**
     * Immutable NFT transfer choices.
     */
    NftTransferIntent intent;
};


/**
 * Requests signed NFT transfer delivery forms without submitting either one.
 */
struct PrepareNftTransferRequest {
    /**
     * Correlation identifier, shared with the preview for a stable TEP-62 query ID.
     */
    NonEmptyString operation_id;
    /**
     * Immutable NFT transfer choices.
     */
    NftTransferIntent intent;
};


/**
 * Paginated NFT items owned by the wallet and their independent load states.
 */
struct NftList {
    /**
     * NFT items in descending last-transaction order.
     */
    std::vector<NftItem> items;
    /**
     * The load state for the first NFT page.
     */
    ResourceState resource;
    /**
     * The load state for an additional NFT page.
     */
    ResourceState pagination_resource;
    /**
     * Whether the provider can have another NFT page.
     */
    bool has_more;
};


/**
 * An informational transfer preview produced without unlocking the wallet secret.
 */
struct SendPreview {
    /**
     * The complete ordered outgoing message batch that was emulated.
     *
     * This is empty for [`crate::WalletClient::preview_send_boc`] because a
     * caller-supplied signed message is preserved as an opaque BOC.
     */
    std::vector<SendMessage> messages;
    /**
     * The resolved wallet message expiration timestamp used by this emulation.
     * A signed send resolves `EngineDefault` again from fresh provider time
     * and preserves the same timestamp for `Exact`.
     */
    uint64_t valid_until;
    /**
     * The complete external message submitted for emulation.
     *
     * Intent previews use a fake signature; BOC previews preserve the caller's
     * exact signed message.
     * The value is a standard padded Base64-encoded BOC. Clients can pass it
     * to an independent emulator or explorer without reconstructing the message.
     */
    Boc message_boc_base64;
    /**
     * The bounded Toncenter emulation summary shown before authorization.
     */
    SendEmulation emulation;
};


/**
 * Requests both signed delivery forms for one wallet transfer.
 *
 * The engine resolves one fresh wallet sequence number and one expiration
 * timestamp, then signs the transfer as both an incoming external message and
 * an owner-signed internal message. It does not submit or persist either BOC.
 */
struct PrepareTransferRequest {
    /**
     * A unique identifier chosen by the application for the prepared transfer.
     */
    NonEmptyString operation_id;
    /**
     * The immutable messages and expiration policy covered by both signatures.
     */
    SendIntent intent;
};


/**
 * Public transfer intent used to preview a send without unlocking its secret.
 *
 * A preview uses fresh account state and a fake signature. It does not reserve
 * an operation identifier, read the send journal, or request the mnemonic.
 */
struct SendPreviewRequest {
    /**
     * The same immutable intent accepted by the signed-send operation.
     */
    SendIntent intent;
};


/**
 * An immutable view of all observable state in one wallet client.
 *
 * Each published change increments `revision`. A failed refresh preserves
 * the last successful resource value and changes its resource state.
 */
struct WalletSnapshot {
    /**
     * A client-local counter that increases for every published state change.
     */
    uint64_t revision;
    /**
     * The stable application record identifier.
     */
    NonEmptyString record_id;
    /**
     * The wallet address.
     */
    TonAddressString address;
    /**
     * The wallet network.
     */
    Network network;
    /**
     * The latest successful account value, if available.
     */
    std::optional<AccountSnapshot> account;
    /**
     * The load state for `account`.
     */
    ResourceState account_resource;
    /**
     * Paginated wallet activity.
     */
    ActivityList activity;
    /**
     * The cursor for the next older activity page.
     */
    std::optional<ActivityCursor> activity_cursor;
    /**
     * Paginated NFT items owned by the wallet.
     */
    NftList nfts;
    /**
     * The current transfer workflow state.
     */
    SendSnapshot send;
};


/**
 * Requests an owner-signed Wallet V5 message for delivery by a relayer.
 */
struct SignMessageRequest {
    /**
     * A unique idempotency identifier chosen by the application.
     */
    NonEmptyString operation_id;
    /**
     * Allows this request to replace an earlier signed message whose outcome is unresolved.
     *
     * The earlier message remains valid until its expiration time and can win
     * the sequence-number race. Require explicit user confirmation before use.
     */
    bool force;
    /**
     * The immutable messages and expiration boundary covered by the signature.
     */
    SendIntent intent;
};


/**
 * Requests one signed wallet transfer.
 */
struct SendRequest {
    /**
     * A unique idempotency identifier chosen by the application.
     */
    NonEmptyString operation_id;
    /**
     * Allows this send to replace an earlier signed send whose on-chain outcome is unresolved.
     *
     * The earlier send can still execute. Require explicit user confirmation
     * before setting this flag because both transfers can affect the balance.
     */
    bool force;
    /**
     * The immutable message and expiration choices for this operation.
     */
    SendIntent intent;
};


/**
 * The result of a refresh or pagination operation and its resulting snapshot.
 */
struct WalletUpdate {
    /**
     * The terminal operation outcome.
     */
    WalletOperationOutcome outcome;
    /**
     * The number of new unique activity items added by pagination.
     */
    uint64_t activity_items_added;
    /**
     * The number of new unique NFT items added by pagination.
     */
    uint64_t nft_items_added;
    /**
     * The immutable snapshot after the operation.
     */
    WalletSnapshot snapshot;
};

namespace uniffi {
struct FfiConverterTonConnectIncomingRequest;
} // namespace uniffi

/**
 * One replay-safe authenticated request awaiting a wallet response.
 */
struct TonConnectIncomingRequest {
    friend uniffi::FfiConverterTonConnectIncomingRequest;
    /**
     * A raw `sendTransaction` request that the wallet signs and submits.
     */
    struct kSendTransaction {
        /**
         * Exact dApp request identifier.
         */
        std::string id;
        /**
         * Exact RPC method name.
         */
        std::string method;
        /**
         * Validated wallet send operation.
         */
        SendRequest request;
    };
    /**
     * A raw `signMessage` request that the wallet signs without submitting.
     */
    struct kSignMessage {
        /**
         * Exact dApp request identifier.
         */
        std::string id;
        /**
         * Exact RPC method name.
         */
        std::string method;
        /**
         * Validated internal-message signing operation.
         */
        SignMessageRequest request;
    };
    /**
     * A dApp-initiated disconnect request.
     */
    struct kDisconnect {
        /**
         * Exact dApp request identifier.
         */
        std::string id;
        /**
         * Exact RPC method name.
         */
        std::string method;
    };
    /**
     * A malformed or unsupported RPC request with its protocol response.
     */
    struct kUnsupported {
        /**
         * Exact dApp request identifier.
         */
        std::string id;
        /**
         * Exact RPC method name.
         */
        std::string method;
        /**
         * Recommended TON Connect RPC error.
         */
        TonConnectRpcErrorCode error_code;
        /**
         * Sanitized protocol diagnostic.
         */
        std::string error_message;
    };
    /**
     * A validated `signData` request of a derived session; the classic session reports it as `Unsupported`.
     */
    struct kSignData {
        /**
         * Exact dApp request identifier.
         */
        std::string id;
        /**
         * Exact RPC method name.
         */
        std::string method;
        /**
         * Validated data to show and sign.
         */
        TonConnectSignDataRequest request;
    };
    TonConnectIncomingRequest(kSendTransaction variant): variant(variant) {}
    TonConnectIncomingRequest(kSignMessage variant): variant(variant) {}
    TonConnectIncomingRequest(kDisconnect variant): variant(variant) {}
    TonConnectIncomingRequest(kUnsupported variant): variant(variant) {}
    TonConnectIncomingRequest(kSignData variant): variant(variant) {}

    TonConnectIncomingRequest(const TonConnectIncomingRequest &other): variant(other.variant) {}
    TonConnectIncomingRequest(TonConnectIncomingRequest &&other): variant(std::move(other.variant)) {}

    TonConnectIncomingRequest &operator=(const TonConnectIncomingRequest &other) {
        variant = other.variant;
        return *this;
    }

    TonConnectIncomingRequest &operator=(TonConnectIncomingRequest &&other) {
        variant = std::move(other.variant);
        return *this;
    }

    /**
     * Returns the variant of this enum
     */
    const std::variant<kSendTransaction, kSignMessage, kDisconnect, kUnsupported, kSignData> &get_variant() const {
        return variant;
    }

private:
    std::variant<kSendTransaction, kSignMessage, kDisconnect, kUnsupported, kSignData> variant;

    TonConnectIncomingRequest();
};


/**
 * One authenticated dApp request relayed by the server.
 */
struct TonConnectDerivedRequest {
    /**
     * The validated request. Only a derived session reports `SignData`; the
     * classic bridge session reports `signData` as `Unsupported`.
     */
    TonConnectIncomingRequest request;
    /**
     * The request `id` as a signed 64-bit value.
     *
     * `None` when `id` is not a canonical decimal that fits a signed 64-bit
     * value; `request` is still decoded so the host can answer with the exact
     * id string.
     */
    std::optional<int64_t> request_id;
};


namespace uniffi {
    struct FfiConverterTonConnectDerivedSession;
} // namespace uniffi

/**
 * One `MTProto`-relayed TON Connect session whose key pair was derived from the wallet key.
 *
 * The object keeps no replay state and no event counter: the server issues event
 * identifiers and enforces request ordering. Nothing here is persisted.
 */
struct TonConnectDerivedSession



{
    friend uniffi::FfiConverterTonConnectDerivedSession;

    TonConnectDerivedSession() = delete;

    TonConnectDerivedSession(TonConnectDerivedSession &&) = delete;

    TonConnectDerivedSession &operator=(const TonConnectDerivedSession &) = delete;
    TonConnectDerivedSession &operator=(TonConnectDerivedSession &&) = delete;

    ~TonConnectDerivedSession();
    /**
     * Opens an opaque MTProto envelope for hosts that own RPC decoding.
     * In particular, Gram Wallet also supports relayed `signMessage`.
     */
    std::vector<uint8_t> decrypt_envelope(const std::vector<uint8_t> &body);
    /**
     * Authenticates and validates one dApp request relayed by the server.
     *
     * `body` is the raw `nonce(24) || box` bytes from the dApp; `now` is the
     * current Unix time used for `valid_until` validation.
     */
    TonConnectDerivedRequest decrypt_request(const std::vector<uint8_t> &body, uint64_t now);
    /**
     * Encrypts a failed-connection event for the dApp.
     */
    std::vector<uint8_t> encrypt_connect_error(uint64_t event_id, const TonConnectConnectErrorCode &code, const std::string &message);
    /**
     * Encrypts a successful connect event for the dApp.
     *
     * `account` must belong to this session; `proof` is included when the
     * dApp requested `ton_proof`. The device advertises `SendTransaction` and
     * `SignData` (`text`, `binary`, `cell`).
     */
    std::vector<uint8_t> encrypt_connect_event(uint64_t event_id, const TonConnectAccountInfo &account, std::optional<TonConnectProofReply> proof, const TonConnectDevice &device);
    /**
     * Encrypts a wallet-initiated disconnect event.
     */
    std::vector<uint8_t> encrypt_disconnect_event(uint64_t event_id);
    /**
     * Encrypts the empty-object result of a dApp-initiated disconnect request.
     */
    std::vector<uint8_t> encrypt_disconnect_success(const std::string &request_id);
    /**
     * Seals a host-validated protocol envelope without exporting session keys.
     */
    std::vector<uint8_t> encrypt_envelope(const std::vector<uint8_t> &body);
    /**
     * Encrypts a protocol RPC error response for the selected request.
     */
    std::vector<uint8_t> encrypt_error(const std::string &request_id, const TonConnectRpcErrorCode &code, const std::string &message);
    /**
     * Encrypts a successful `sendTransaction` response carrying the signed `BoC`.
     */
    std::vector<uint8_t> encrypt_send_success(const std::string &request_id, const std::string &signed_boc);
    /**
     * Encrypts a successful `signData` response for the signed data.
     *
     * The response names this session's wallet address. It is refused unless
     * the request still matches this session's network and wallet and
     * `signed_data.signature` verifies with this session's signing public key
     * for exactly that address, request, domain, and timestamp.
     */
    std::vector<uint8_t> encrypt_sign_data_success(const std::string &request_id, const TonConnectSignedData &signed_data);
    /**
     * Opens a server challenge and returns its 32-byte answer.
     *
     * `challenge` is exactly `ephemeral_pk(32) || nonce(24) || box(48)`: a
     * 32-byte answer sealed to `W` under an ephemeral key.
     */
    std::vector<uint8_t> open_challenge(const std::vector<uint8_t> &challenge);
    /**
     * Returns the session public key `W` as 64 lowercase hex characters.
     */
    std::string public_key_hex();
    /**
     * Returns the 32-byte Ed25519 signing public key the session was derived from.
     */
    std::vector<uint8_t> signing_public_key();

    private:
    TonConnectDerivedSession(const TonConnectDerivedSession &);

    TonConnectDerivedSession(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};


namespace uniffi {
    struct FfiConverterTonConnectSession;
} // namespace uniffi

/**
 * Thread-safe TON Connect session exposed to Swift, Kotlin, and other hosts.
 */
struct TonConnectSession



{
    friend uniffi::FfiConverterTonConnectSession;

    TonConnectSession() = delete;

    TonConnectSession(TonConnectSession &&) = delete;

    TonConnectSession &operator=(const TonConnectSession &) = delete;
    TonConnectSession &operator=(TonConnectSession &&) = delete;

    ~TonConnectSession();
    /**
     * Approves the connect request and keeps its encrypted response pending delivery.
     *
     * Persist the session before posting the returned response. After the
     * bridge accepts it, call [`Self::complete_pending_post`].
     */
    TonConnectPreparedPost approve_connect(const TonConnectAccountInfo &account, std::optional<TonConnectProofReply> proof, const TonConnectDevice &device);
    /**
     * Returns a complete SSE subscription URL and resets stream framing.
     */
    std::string begin_events_subscription();
    /**
     * Removes the pending response after the bridge accepts it.
     */
    void complete_pending_post();
    /**
     * Returns the initial connect prompt while approval is pending.
     */
    std::optional<TonConnectConnectPrompt> connect_prompt();
    /**
     * Keeps a wallet-initiated disconnect event pending bridge delivery.
     */
    TonConnectPreparedPost disconnect();
    /**
     * Ingests an SSE chunk and returns only fresh authenticated requests.
     */
    std::vector<TonConnectIncomingRequest> ingest_sse_chunk(const std::vector<uint8_t> &chunk, uint64_t now);
    /**
     * Returns the exact response that must be retried before reading more events.
     */
    std::optional<TonConnectPreparedPost> pending_post();
    /**
     * Returns restored requests that still await a wallet response.
     */
    std::vector<TonConnectIncomingRequest> pending_requests(uint64_t now);
    /**
     * Serializes the secret-bearing session, requests, and pending response.
     */
    std::string persisted();
    /**
     * Returns the current lifecycle phase.
     */
    TonConnectSessionPhase phase();
    /**
     * Keeps a successful dApp-initiated disconnect response pending delivery.
     */
    TonConnectPreparedPost prepare_disconnect_success(const std::string &request_id);
    /**
     * Keeps a protocol RPC error pending delivery for the selected request.
     */
    TonConnectPreparedPost prepare_error(const std::string &request_id, const TonConnectRpcErrorCode &code, const std::string &message);
    /**
     * Keeps a successful `sendTransaction` response pending bridge delivery.
     *
     * The response contains the exact signed BOC returned to the dApp.
     */
    TonConnectPreparedPost prepare_send_success(const std::string &request_id, const std::string &signed_boc);
    /**
     * Keeps a successful `signMessage` response pending bridge delivery.
     *
     * `internal_boc` must contain the complete signed internal message
     * returned by [`crate::WalletClient::sign_message`].
     */
    TonConnectPreparedPost prepare_sign_message_success(const std::string &request_id, const std::string &internal_boc);
    /**
     * Rejects the initial connection and keeps its terminal event pending delivery.
     *
     * Persist the session before posting the returned response. After the
     * bridge accepts it, call [`Self::complete_pending_post`].
     */
    TonConnectPreparedPost reject_connect(const std::string &message);

    private:
    TonConnectSession(const TonConnectSession &);

    TonConnectSession(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};


namespace uniffi {
    struct FfiConverterWalletClient;
} // namespace uniffi

/**
 * Coordinates state and operations for one wallet record.
 *
 * The client owns no transport or platform resources. Call [`Self::shutdown`]
 * before the host releases callback objects or application services.
 */
struct WalletClient



{
    friend uniffi::FfiConverterWalletClient;

    WalletClient() = delete;

    WalletClient(WalletClient &&) = delete;

    WalletClient &operator=(const WalletClient &) = delete;
    WalletClient &operator=(WalletClient &&) = delete;

    ~WalletClient();
    /**
     * Creates a client after validation of identifiers, URLs, and credential origin.
     *
     * The initial snapshot has revision zero and idle resource states.
     */
    static std::shared_ptr<WalletClient> init(const WalletClientConfig &config, const std::shared_ptr<WalletHttpHost> &http_host, const std::shared_ptr<WalletPlatformHost> &platform_host);
    /**
     * Creates a client backed by a provider transport without HTTP metadata.
     *
     * Use this constructor for a relay or protocol proxy that can return only
     * a response body or an opaque transport error. The engine does not apply
     * HTTP status, header, redirect, or final-URL checks in this mode.
     */
    static std::shared_ptr<WalletClient> new_statusless(const WalletClientConfig &config, const std::shared_ptr<WalletStatuslessHost> &statusless_host, const std::shared_ptr<WalletPlatformHost> &platform_host);
    /**
     * Cancels the active activity page load.
     *
     * This method has no effect when no page load is active.
     */
    void cancel_load_more_activity();
    /**
     * Loads the next older activity page and merges unique items by item ID.
     *
     * The method returns `Skipped` during refresh, during another page load,
     * or when no advancing cursor exists. A page must move to an older logical
     * time. Otherwise pagination stops and adds no items.
     */
    WalletUpdate load_more_activity();
    /**
     * Stops new work, cancels active host requests, and releases snapshot waiters.
     *
     * The operation is idempotent. It cancels reversible work immediately.
     * If a send crossed its durable commit boundary, shutdown rejects new
     * operations and waits for that send to reach a terminal journal state.
     */
    void shutdown();
    /**
     * Returns a clone of the current immutable snapshot.
     *
     * A returned snapshot never changes. Read a newer snapshot to observe a
     * higher revision.
     */
    WalletSnapshot snapshot();
    /**
     * Waits until the snapshot revision is greater than `after_revision`.
     *
     * This method returns immediately when a newer revision already exists.
     * Shutdown releases all waiters and returns [`WalletClientError::Shutdown`].
     */
    WalletSnapshot wait_for_change(uint64_t after_revision);
    /**
     * Resolves the standard TON DNS `wallet` record for a `.ton` name.
     *
     * The operation is read-only and never requests protected wallet secrets.
     * It uses the configured or network-default root resolver, then asks the
     * configured provider to perform bounded DNS recursion.
     */
    std::optional<TonAddressString> resolve_dns(const std::string &name);
    /**
     * Creates a TON encrypted-comment body ready for `SendMessageBody::RawPayload`.
     *
     * The engine uses the supplied recipient public key or calls the recipient
     * wallet's `get_public_key` get-method, then asks the platform host to
     * authorize this wallet's protected mnemonic. The sender key is this
     * wallet's current signing key.
     * The host authenticates supplied recipient keys, including rotated keys.
     * No secret is requested when the comment is already too large.
     * The recipient key is resolved exactly as
     * [`Self::resolve_encrypted_comment_recipient`] resolves it, including its
     * errors, and before any secret is requested.
     */
    Boc create_encrypted_comment(const CreateEncryptedCommentRequest &request);
    /**
     * Decrypts one TON encrypted-comment body after explicit host authorization.
     *
     * The caller supplies the sender address because TON uses its bounceable,
     * URL-safe, non-test-only representation as authenticated salt.
     *
     * The body may use any signing key this wallet ever had. The engine first
     * tries the current signing key and the anchor key, which the recovery
     * phrase holds, without any HTTP request. When neither matches and the
     * wallet has rotated its key, the engine reads the wallet's
     * `change_wallet_key` actions from Toncenter v3, recovers each earlier
     * signing key from the encrypted old key its rotation published, and tries
     * those. The secret is read once per call and recovered keys never leave
     * it. The history holds no secret; later calls reuse it while it still
     * contains the rotation that installed the current signing key.
     *
     * `EncryptedCommentLookupFailed` means the provider did not answer or did
     * not return a history that reaches the current signing key yet; a retry
     * can succeed.
     * `EncryptedCommentUnavailable` means no key of this wallet decrypts the
     * body or the body is malformed.
     */
    std::string decrypt_comment(const DecryptCommentRequest &request);
    /**
     * Resolves the Ed25519 public key an encrypted comment for a recipient uses.
     *
     * This answers whether [`Self::create_encrypted_comment`] with the same
     * recipient and supplied key can encrypt, without a comment and without
     * requesting any protected secret, so a client configured without a local
     * signing secret can ask it too. A supplied key is verified locally, with
     * no HTTP request and without the single-flight slot. Otherwise the engine
     * reads the recipient account state and, for an active contract, calls its
     * `get_public_key` get-method.
     *
     * `EncryptedCommentUnavailable` means the recipient cannot receive an
     * encrypted comment: the supplied key does not derive its address, its
     * wallet is not deployed or is frozen, or its contract did not return a
     * public key. `EncryptedCommentLookupFailed` means the provider did not
     * answer and nothing is known about the recipient.
     */
    std::vector<uint8_t> resolve_encrypted_comment_recipient(const EncryptedCommentRecipientRequest &request);
    /**
     * Generates one new signing half and both signed Wallet rev00 delivery forms.
     * `signed_boc` aliases the form selected by `request.message_kind`.
     *
     * The client first fetches fresh account state through its configured
     * provider. Active wallets use the on-chain `seqno` getter, while an
     * account without deployed contract code uses sequence number zero and
     * attaches anchor-based `StateInit`; only the initial 12-word phrase can
     * sign such a deploying request, so a post-rotation phrase then fails with
     * `KeyRotationUnavailable`. The client then asks the host to unlock the
     * protected phrase. It does not update protected storage and does not
     * submit the returned BOC.
     *
     * The request, signed with the current signing key, carries the new key's
     * wallet-address proof signature and the current signing key encrypted
     * with the new one as
     * `sha256(new_private_key ‖ "keyChangeSaltV1") XOR old_private_key`.
     * The contract revision with bytecode hash
     * `e30911420bef1191c09dce58b9df2b4ca4c2d9c383cc3b6a91170349ffa70e2c`
     * publishes that value in its key-changed log, which lets
     * [`WalletClient::decrypt_comment`] recover the replaced key later.
     */
    PreparedKeyRotation prepare_key_rotation(const PrepareKeyRotationRequest &request);
    /**
     * Cancels the active additional-page NFT load.
     *
     * This method has no effect when no NFT page load is active.
     */
    void cancel_load_more_nfts();
    /**
     * Cancels the active first-page NFT refresh.
     *
     * This method has no effect when no NFT refresh is active.
     */
    void cancel_refresh_nfts();
    /**
     * Loads the next NFT page and appends items not already present by address.
     *
     * The method returns `Skipped` during an NFT refresh, during another NFT
     * page load, or after the provider returns a short page.
     */
    WalletUpdate load_more_nfts();
    /**
     * Loads the newest page of NFTs owned by this wallet.
     *
     * This operation is independent from [`Self::refresh`], so applications
     * can load account, activity, and NFT data concurrently. A newer NFT
     * refresh supersedes the older one and cancels its host request.
     */
    WalletUpdate refresh_nfts();
    /**
     * Validates ownership and emulation, then signs external and internal delivery
     * alternatives without broadcasting or writing the send journal.
     */
    PreparedTransfer prepare_nft_transfer(const PrepareNftTransferRequest &request);
    /**
     * Validates current ownership and emulates one typed TEP-62 NFT transfer.
     *
     * Reuse this request's operation ID for [`Self::send_nft_transfer`] after
     * user confirmation so the TEP-62 query ID remains unchanged.
     */
    SendPreview preview_nft_transfer(const NftTransferPreviewRequest &request);
    /**
     * Revalidates current ownership and signs/submits one typed TEP-62 transfer.
     *
     * Provider acceptance confirms the source wallet message, not final NFT
     * ownership. Applications must refresh NFT state after confirmation.
     */
    SendResult send_nft_transfer(const NftTransferRequest &request);
    /**
     * Signs external and internal delivery forms without submitting either one.
     *
     * The client fetches account state and `seqno` once, resolves one
     * `valid_until`, and unlocks the protected phrase once. Both returned BOCs
     * therefore authorize the same transfer and are mutually exclusive
     * delivery alternatives. This method does not read or write the send
     * journal and does not submit either message.
     */
    PreparedTransfer prepare_transfer(const PrepareTransferRequest &request);
    /**
     * Cancels the current send preview and its active provider request.
     */
    void cancel_send_preview();
    /**
     * Emulates external key rotation using public state and zero signatures.
     * Never unlocks a secret, generates a replacement phrase, or submits a BOC.
     */
    SendPreview preview_key_rotation();
    /**
     * Emulates a transfer without reading the journal or protected secret.
     *
     * The engine fetches fresh account state and seqno, builds a complete
     * Wallet message with a fake signature, and asks Toncenter to execute it.
     * Calling [`Self::send`] later repeats the chain-state checks and builds a
     * new message from fresh state before the real signature is created.
     */
    SendPreview preview_send(const SendPreviewRequest &request);
    /**
     * Emulates an already signed external-message BOC without submitting it.
     *
     * The request is validated against the configured source and fresh wallet
     * seqno and provider time. The exact BOC is sent only to the emulation
     * endpoint; this operation does not read or write the journal and does not
     * publish the message to the network. The operation identifier and force
     * flag are ignored, so the same request can be reused with [`Self::send_boc`].
     */
    SendPreview preview_send_boc(const SendBocRequest &request);
    /**
     * Validates an internal-message signing request from fresh public state.
     *
     * No wallet-paid fee estimate is returned because a relayer supplies the
     * TON attached to the internal request. Signing repeats these checks and
     * reads the protected secret only after host approval.
     */
    SignMessagePreview preview_sign_message(const SendPreviewRequest &request);
    /**
     * Emulates the exact transfer fields supplied by a TON Connect request.
     *
     * The preview preserves the dApp validity boundary, payload, and
     * destination `StateInit`. It does not consume the operation identifier.
     */
    SendPreview preview_ton_connect(const SendRequest &request);
    /**
     * Cancels the active refresh and requests cancellation of its provider requests.
     *
     * This method has no effect when no refresh is active.
     */
    void cancel_refresh();
    /**
     * Refreshes account and first-page activity data concurrently.
     *
     * Each resource publishes independently. One request can succeed while
     * the other fails, which produces [`WalletOperationOutcome::PartiallyCompleted`].
     * A newer refresh supersedes the older refresh and cancels its host requests.
     */
    WalletUpdate refresh();
    /**
     * Resolves the durable outgoing message from chain evidence without signing.
     *
     * The operation is idempotent. It never reads protected secret storage and
     * commits terminal evidence with compare-and-swap journal transitions.
     */
    SendSnapshot resolve_pending();
    /**
     * Cancels the active send before its durable commit boundary.
     *
     * After journal persistence starts, this method returns
     * [`WalletClientError::SendCancellationTooLate`]. The caller must let the
     * send finish because the signed BOC can already be durable or submitted.
     */
    void cancel_send();
    /**
     * Signs, records, and submits one wallet transfer.
     *
     * A preceding [`Self::preview_send`] is an informational UI step, not a
     * prerequisite. This method independently reloads account state and seqno,
     * calculates a new validity window, and builds the real message only after
     * protected-secret authorization.
     *
     * A transport error after submission produces `SubmissionUnknown`. A later
     * [`Self::resolve_pending`] call, refresh, or send attempt can reconcile the
     * persisted message against provider evidence. By default, the unresolved
     * send blocks a new signature. `SendRequest::force` overrides that block
     * after the application obtains explicit user confirmation.
     *
     * Workflow failures return a typed send error. The same bounded diagnostic
     * is published in `snapshot().send.error_message`.
     */
    SendResult send(const SendRequest &request);
    /**
     * Durably records and submits an already signed external-message BOC.
     *
     * The request must carry the exact `seqno` and expiration covered by the
     * BOC. The engine validates them against fresh provider state, stores the
     * exact BOC in the wallet-wide journal before provider handoff, and exposes
     * the normal pending-resolution and cancellation behavior.
     */
    SendResult send_boc(const SendBocRequest &request);
    /**
     * Signs and durably records a Wallet V5 `internal_signed` request.
     *
     * This method does not submit the message. The returned BOC is a complete
     * relaxed internal message that the caller can give to a relayer until
     * `valid_until`. The shared send journal prevents another signature from
     * silently reusing the same wallet sequence number.
     */
    SignMessageResult sign_message(const SignMessageRequest &request);

    private:
    WalletClient(const WalletClient &);

    WalletClient(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};




/**
 * Executes HTTP work for the engine.
 *
 * The host must enforce the request timeout while it connects and reads the
 * response. It owns any local response limits, must reject redirects, and must
 * return the observed URL in `final_url`.
 */
struct WalletHttpHost {
    virtual ~WalletHttpHost() {}
    /**
     * Executes one complete HTTP request and returns an accepted response.
     *
     * The host can add its Toncenter credential according to the actual URL
     * and its local security policy. It must return
     * [`HttpHostErrorKind::Timeout`] when `request.timeout_ms` expires.
     */
    virtual
    HttpResponse execute_http(const HttpRequest &request) = 0;
    /**
     * Requests cancellation of the request with `request_id`.
     *
     * This callback must be idempotent. It can run before `execute_http`
     * registers the request, so the host must remember an early cancellation.
     */
    virtual
    void cancel_http(const HttpRequestId &request_id) = 0;
};

namespace uniffi {
    struct UniffiCallbackInterfaceWalletHttpHost {
        static void execute_http(uint64_t uniffi_handle,RustBuffer request,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void cancel_http(uint64_t uniffi_handle,RustBuffer request_id,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);

        static void uniffi_free(uint64_t uniffi_handle);
        static uint64_t uniffi_clone(uint64_t uniffi_handle);
        static void init();
    private:
        static inline UniffiVTableCallbackInterfaceWalletHttpHost vtable = UniffiVTableCallbackInterfaceWalletHttpHost {
            .uniffi_free = reinterpret_cast<void *>(&uniffi_free),
            .uniffi_clone = reinterpret_cast<void *>(&uniffi_clone),
            .execute_http = reinterpret_cast<void *>(&execute_http),
            .cancel_http = reinterpret_cast<void *>(&cancel_http)
        };
    };
}

namespace uniffi {
    struct FfiConverterWalletHttpHost;
} // namespace uniffi

/**
 * Executes HTTP work for the engine.
 *
 * The host must enforce the request timeout while it connects and reads the
 * response. It owns any local response limits, must reject redirects, and must
 * return the observed URL in `final_url`.
 */
struct WalletHttpHostImpl

 : public WalletHttpHost 

{
    friend uniffi::FfiConverterWalletHttpHost;

    WalletHttpHostImpl() = delete;

    WalletHttpHostImpl(WalletHttpHostImpl &&) = delete;

    WalletHttpHostImpl &operator=(const WalletHttpHostImpl &) = delete;
    WalletHttpHostImpl &operator=(WalletHttpHostImpl &&) = delete;

    ~WalletHttpHostImpl();
    /**
     * Executes one complete HTTP request and returns an accepted response.
     *
     * The host can add its Toncenter credential according to the actual URL
     * and its local security policy. It must return
     * [`HttpHostErrorKind::Timeout`] when `request.timeout_ms` expires.
     */
    HttpResponse execute_http(const HttpRequest &request);
    /**
     * Requests cancellation of the request with `request_id`.
     *
     * This callback must be idempotent. It can run before `execute_http`
     * registers the request, so the host must remember an early cancellation.
     */
    void cancel_http(const HttpRequestId &request_id);

    private:
    WalletHttpHostImpl(const WalletHttpHostImpl &);

    WalletHttpHostImpl(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};


namespace uniffi {
    struct FfiConverterWalletLifecycle;
} // namespace uniffi

/**
 * Coordinates wallet creation and recovery-phrase access.
 *
 * It intentionally has no stream, refresh, reconciliation, or journal API.
 * The embedding application owns public wallet metadata. This object owns the
 * boundary between Rust key derivation and protected storage.
 */
struct WalletLifecycle



{
    friend uniffi::FfiConverterWalletLifecycle;

    WalletLifecycle() = delete;

    WalletLifecycle(WalletLifecycle &&) = delete;

    WalletLifecycle &operator=(const WalletLifecycle &) = delete;
    WalletLifecycle &operator=(WalletLifecycle &&) = delete;

    ~WalletLifecycle();
    /**
     * Creates a lifecycle service backed by `platform_host`.
     *
     * The application can share this object across wallet operations. The
     * application remains responsible for the lifetime of host resources.
     */
    static std::shared_ptr<WalletLifecycle> init(const std::shared_ptr<WalletPlatformHost> &platform_host);
    /**
     * Generates the initial 12-word recovery phrase, derives its wallet
     * account, and stores the phrase.
     *
     * A new wallet starts before its first key rotation, so its signing
     * key equals its anchor key and the user records a single 12-word half.
     *
     * Persist `descriptor` after this method succeeds. Present the recovery
     * phrase once, then release all application copies of it.
     */
    CreatedWallet create_wallet(const CreateWalletRequest &request);
    /**
     * Deletes the protected secret for a wallet descriptor.
     *
     * This method does not delete application metadata or journal records.
     * Delete the stored descriptor only after this method succeeds.
     */
    void delete_wallet(const WalletDescriptor &descriptor);
    /**
     * Authorizes the current signing key and derives one `MTProto` TON Connect session.
     *
     * Scheme v1: the session secret is HMAC-derived from the signing-key seed, the
     * dApp client id and the server nonce, so every device of the user derives the
     * same key pair. The returned object exposes only the public key `W` and the
     * signing public key it was derived from; the engine does not persist it.
     * A signing-key rotation changes the derived key of every later derivation.
     */
    std::shared_ptr<TonConnectDerivedSession> derive_ton_connect_session(const TonConnectDerivedSessionRequest &request);
    /**
     * Validates and imports an entered mnemonic as a wallet account.
     *
     * The host stores the phrase with a user-presence requirement. The result
     * contains only metadata that is safe for normal application storage.
     */
    WalletDescriptor import_wallet(const ImportWalletRequest &request);
    /**
     * Authorizes secret access and returns a verified recovery phrase.
     *
     * Rust derives an address from the returned secret and compares it with
     * the descriptor. Release the returned phrase after the UI closes.
     */
    RecoveryPhrase reveal_recovery_phrase(const WalletDescriptor &descriptor);
    /**
     * Authorizes the current signing key and signs one TON Connect `signData` request.
     *
     * Rust validates the request against this wallet and builds the protocol digest
     * for `domain` and `timestamp` before any secret access, so the caller cannot use
     * this API as a generic signing oracle. The returned record is what
     * `TonConnectDerivedSession::encrypt_sign_data_success` answers with. The host
     * must block this operation while a key rotation is unresolved.
     */
    TonConnectSignedData sign_ton_connect_data(const TonConnectSignDataSignRequest &request);
    /**
     * Authorizes the current signing key and signs a TON Connect ownership proof.
     *
     * Rust constructs the protocol digest itself. The caller cannot use this
     * API as a generic Ed25519 signing oracle, and no mnemonic or private-key
     * bytes cross the API boundary.
     * The returned public key must accompany the proof in `ton_addr`.
     * The host must block this operation while a key rotation is unresolved.
     */
    TonConnectProofSignature sign_ton_connect_proof(const TonConnectProofSignRequest &request);
    /**
     * Derives initial TON Connect account material without reading a secret.
     *
     * Address and `StateInit` remain anchor-based after rotation. For a proof
     * reply, replace `public_key` with [`TonConnectProofSignature::public_key`].
     */
    TonConnectAccountInfo ton_connect_account(const WalletDescriptor &descriptor);

    private:
    WalletLifecycle(const WalletLifecycle &);

    WalletLifecycle(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};




/**
 * Supplies protected storage and durable journal storage.
 *
 * Callback implementations must not call the same client operation
 * recursively. The engine does not hold its wallet-state lock during calls.
 */
struct WalletPlatformHost {
    virtual ~WalletPlatformHost() {}
    /**
     * Reads protected secret bytes after the required user authorization.
     *
     * The host must not log the bytes. Return a classified host error when
     * authorization fails or the user cancels the prompt.
     */
    virtual
    std::vector<uint8_t> read_protected_secret(const ProtectedSecretRead &request) = 0;
    /**
     * Stores secret bytes under the supplied reference.
     *
     * The host must apply the `require_user_presence` policy to later reads.
     */
    virtual
    void store_protected_secret(const ProtectedSecretStore &request) = 0;
    /**
     * Deletes the protected secret for `secret_ref`.
     */
    virtual
    void delete_protected_secret(const ProtectedSecretRef &secret_ref) = 0;
    /**
     * Loads the current opaque journal record for `key`.
     */
    virtual
    std::optional<JournalRecord> load_journal(const JournalKey &key) = 0;
    /**
     * Atomically replaces a journal record when its version matches.
     *
     * The host must compare and replace in one durable transaction. It must
     * return the current record when the expected version does not match.
     */
    virtual
    JournalCompareExchangeResult compare_exchange_journal(const JournalCompareExchange &mutation) = 0;
};

namespace uniffi {
    struct UniffiCallbackInterfaceWalletPlatformHost {
        static void read_protected_secret(uint64_t uniffi_handle,RustBuffer request,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void store_protected_secret(uint64_t uniffi_handle,RustBuffer request,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void delete_protected_secret(uint64_t uniffi_handle,RustBuffer secret_ref,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void load_journal(uint64_t uniffi_handle,RustBuffer key,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void compare_exchange_journal(uint64_t uniffi_handle,RustBuffer mutation,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);

        static void uniffi_free(uint64_t uniffi_handle);
        static uint64_t uniffi_clone(uint64_t uniffi_handle);
        static void init();
    private:
        static inline UniffiVTableCallbackInterfaceWalletPlatformHost vtable = UniffiVTableCallbackInterfaceWalletPlatformHost {
            .uniffi_free = reinterpret_cast<void *>(&uniffi_free),
            .uniffi_clone = reinterpret_cast<void *>(&uniffi_clone),
            .read_protected_secret = reinterpret_cast<void *>(&read_protected_secret),
            .store_protected_secret = reinterpret_cast<void *>(&store_protected_secret),
            .delete_protected_secret = reinterpret_cast<void *>(&delete_protected_secret),
            .load_journal = reinterpret_cast<void *>(&load_journal),
            .compare_exchange_journal = reinterpret_cast<void *>(&compare_exchange_journal)
        };
    };
}

namespace uniffi {
    struct FfiConverterWalletPlatformHost;
} // namespace uniffi

/**
 * Supplies protected storage and durable journal storage.
 *
 * Callback implementations must not call the same client operation
 * recursively. The engine does not hold its wallet-state lock during calls.
 */
struct WalletPlatformHostImpl

 : public WalletPlatformHost 

{
    friend uniffi::FfiConverterWalletPlatformHost;

    WalletPlatformHostImpl() = delete;

    WalletPlatformHostImpl(WalletPlatformHostImpl &&) = delete;

    WalletPlatformHostImpl &operator=(const WalletPlatformHostImpl &) = delete;
    WalletPlatformHostImpl &operator=(WalletPlatformHostImpl &&) = delete;

    ~WalletPlatformHostImpl();
    /**
     * Reads protected secret bytes after the required user authorization.
     *
     * The host must not log the bytes. Return a classified host error when
     * authorization fails or the user cancels the prompt.
     */
    std::vector<uint8_t> read_protected_secret(const ProtectedSecretRead &request);
    /**
     * Stores secret bytes under the supplied reference.
     *
     * The host must apply the `require_user_presence` policy to later reads.
     */
    void store_protected_secret(const ProtectedSecretStore &request);
    /**
     * Deletes the protected secret for `secret_ref`.
     */
    void delete_protected_secret(const ProtectedSecretRef &secret_ref);
    /**
     * Loads the current opaque journal record for `key`.
     */
    std::optional<JournalRecord> load_journal(const JournalKey &key);
    /**
     * Atomically replaces a journal record when its version matches.
     *
     * The host must compare and replace in one durable transaction. It must
     * return the current record when the expected version does not match.
     */
    JournalCompareExchangeResult compare_exchange_journal(const JournalCompareExchange &mutation);

    private:
    WalletPlatformHostImpl(const WalletPlatformHostImpl &);

    WalletPlatformHostImpl(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};




/**
 * Executes provider requests through a transport without HTTP response metadata.
 *
 * The request URL is the logical Toncenter destination and does not require a
 * direct connection to that origin. The host can route it through a trusted
 * relay or protocol proxy, but must not follow or emulate provider redirects.
 * A successful callback returns only the provider body: it makes no claim
 * about an HTTP status, response headers, or final URL.
 */
struct WalletStatuslessHost {
    virtual ~WalletStatuslessHost() {}
    /**
     * Executes one complete logical provider request.
     *
     * The host must enforce `request.timeout_ms`. It reports timeout and
     * cancellation explicitly; opaque RPC failures can use
     * [`StatuslessHostErrorKind::Other`] with a bounded diagnostic.
     */
    virtual
    std::vector<uint8_t> execute_statusless(const HttpRequest &request) = 0;
    /**
     * Requests cancellation of the request with `request_id`.
     *
     * This callback has the same idempotency and early-cancellation contract
     * as [`crate::WalletHttpHost::cancel_http`].
     */
    virtual
    void cancel_statusless(const HttpRequestId &request_id) = 0;
};

namespace uniffi {
    struct UniffiCallbackInterfaceWalletStatuslessHost {
        static void execute_statusless(uint64_t uniffi_handle,RustBuffer request,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);
        static void cancel_statusless(uint64_t uniffi_handle,RustBuffer request_id,void * uniffi_future_callback,uint64_t uniffi_callback_data,UniffiForeignFutureDroppedCallbackStruct & uniffi_out_dropped_callback);

        static void uniffi_free(uint64_t uniffi_handle);
        static uint64_t uniffi_clone(uint64_t uniffi_handle);
        static void init();
    private:
        static inline UniffiVTableCallbackInterfaceWalletStatuslessHost vtable = UniffiVTableCallbackInterfaceWalletStatuslessHost {
            .uniffi_free = reinterpret_cast<void *>(&uniffi_free),
            .uniffi_clone = reinterpret_cast<void *>(&uniffi_clone),
            .execute_statusless = reinterpret_cast<void *>(&execute_statusless),
            .cancel_statusless = reinterpret_cast<void *>(&cancel_statusless)
        };
    };
}

namespace uniffi {
    struct FfiConverterWalletStatuslessHost;
} // namespace uniffi

/**
 * Executes provider requests through a transport without HTTP response metadata.
 *
 * The request URL is the logical Toncenter destination and does not require a
 * direct connection to that origin. The host can route it through a trusted
 * relay or protocol proxy, but must not follow or emulate provider redirects.
 * A successful callback returns only the provider body: it makes no claim
 * about an HTTP status, response headers, or final URL.
 */
struct WalletStatuslessHostImpl

 : public WalletStatuslessHost 

{
    friend uniffi::FfiConverterWalletStatuslessHost;

    WalletStatuslessHostImpl() = delete;

    WalletStatuslessHostImpl(WalletStatuslessHostImpl &&) = delete;

    WalletStatuslessHostImpl &operator=(const WalletStatuslessHostImpl &) = delete;
    WalletStatuslessHostImpl &operator=(WalletStatuslessHostImpl &&) = delete;

    ~WalletStatuslessHostImpl();
    /**
     * Executes one complete logical provider request.
     *
     * The host must enforce `request.timeout_ms`. It reports timeout and
     * cancellation explicitly; opaque RPC failures can use
     * [`StatuslessHostErrorKind::Other`] with a bounded diagnostic.
     */
    std::vector<uint8_t> execute_statusless(const HttpRequest &request);
    /**
     * Requests cancellation of the request with `request_id`.
     *
     * This callback has the same idempotency and early-cancellation contract
     * as [`crate::WalletHttpHost::cancel_http`].
     */
    void cancel_statusless(const HttpRequestId &request_id);

    private:
    WalletStatuslessHostImpl(const WalletStatuslessHostImpl &);

    WalletStatuslessHostImpl(uint64_t);

    uint64_t _uniffi_internal_clone_pointer() const;

    uint64_t instance = 0;
};


/**
 * Public account material sent in a TON Connect `ton_addr` reply.
 */
struct TonConnectAccountInfo {
    /**
     * Canonical raw TON address (`workchain:hex`).
     */
    std::string address;
    /**
     * TON network global ID encoded as a decimal string.
     */
    std::string network;
    /**
     * Canonical standard-base64 wallet `StateInit` `BoC`.
     */
    std::string wallet_state_init;
    /**
     * Raw 32-byte Ed25519 public key advertised to the dApp.
     *
     * [`WalletLifecycle::ton_connect_account`] supplies the initial anchor
     * key. When signing a proof, replace it with the returned signing key.
     */
    std::vector<uint8_t> public_key;
};


/**
 * Initial dApp request that must be shown before connection approval.
 */
struct TonConnectConnectPrompt {
    /**
     * URL of `tonconnect-manifest.json`.
     */
    std::string manifest_url;
    /**
     * Optional requested network identifier.
     */
    std::optional<std::string> requested_network;
    /**
     * Optional ownership-proof challenge.
     */
    std::optional<std::string> proof_payload;
};


/**
 * Validated dApp manifest fields needed by wallet UI and proof signing.
 */
struct TonConnectManifest {
    /**
     * Canonical dApp URL.
     */
    std::string url;
    /**
     * Human-readable dApp name.
     */
    std::string name;
    /**
     * HTTPS raster icon URL.
     */
    std::string icon_url;
    /**
     * DNS domain bound into `ton_proof`.
     */
    std::string domain;
};


/**
 * A complete encrypted HTTP bridge POST.
 */
struct TonConnectPreparedPost {
    /**
     * Absolute bridge message URL including recipient and TTL.
     */
    std::string url;
    /**
     * Canonical base64 encrypted body.
     */
    std::string body;
};


/**
 * Signed ownership proof supplied when approving a connect request.
 */
struct TonConnectProofReply {
    /**
     * Unix signing timestamp in seconds.
     */
    uint64_t timestamp;
    /**
     * Manifest domain shown to the user.
     */
    std::string domain;
    /**
     * Exact dApp challenge.
     */
    std::string payload;
    /**
     * Exact 64-byte Ed25519 signature.
     */
    std::vector<uint8_t> signature;
};


/**
 * Ed25519 result of an authorized TON Connect ownership proof.
 */
struct TonConnectProofSignature {
    /**
     * Exact 64-byte Ed25519 signature.
     */
    std::vector<uint8_t> signature;
    /**
     * Current 32-byte Ed25519 signing public key used for this proof.
     *
     * Use this key in the accompanying `ton_addr` reply. After rotation it
     * differs from the anchor key in the descriptor and initial `StateInit`.
     */
    std::vector<uint8_t> public_key;
};


/**
 * Limits and bridge identity for one wallet-side TON Connect session.
 */
struct TonConnectSessionConfig {
    /**
     * Wallet-owned HTTP bridge base URL.
     */
    std::string bridge_url;
    /**
     * Maximum bytes accepted for one SSE event.
     */
    uint64_t max_event_bytes;
    /**
     * Bridge message lifetime in seconds.
     */
    uint32_t message_ttl_seconds;
};


/**
 * A recovery-phrase scheme recognized by [`detect_mnemonic_schemes`].
 *
 * The names follow
 * [TEP-0003](https://github.com/ton-blockchain/TEPs/blob/master/text/0003-wallets.md).
 */
enum class MnemonicScheme: int32_t {
    /**
     * A rotation mnemonic (TEP-0003 section 3.3): 12 words before the
     * wallet's first key rotation or 24 words after it. The only scheme
     * wallet import accepts; later rotations replace the signing half.
     */
    kRotation = 1,
    /**
     * A passwordless legacy 24-word TON mnemonic (TEP-0003 section 3.1).
     * Recognized so the application can explain the rejection; never
     * imported, and no key is derived from it.
     */
    kTon = 2,
    /**
     * A standard 24-word BIP-39 phrase - the Multichain mnemonic of TEP-0003
     * section 3.2. Recognized so the application can explain the rejection;
     * never imported, and no key is derived from it.
     */
    kBip39 = 3
};

namespace uniffi {
struct FfiConverterTonAddressError;
} // namespace uniffi

/**
 * Reports invalid address text or a raw address that cannot use TEP-2 formatting.
 */
struct TonAddressError: std::runtime_error {
    friend uniffi::FfiConverterTonAddressError;

    TonAddressError() : std::runtime_error("") {}
    TonAddressError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~TonAddressError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of TonAddressError
 */
namespace ton_address_error {

/**
 * The input is neither a valid raw address nor a valid TEP-2 friendly address.
 */
struct InvalidAddress: TonAddressError {

    InvalidAddress() : TonAddressError("") {}
    InvalidAddress(const std::string &what_arg) : TonAddressError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};

/**
 * The workchain cannot be encoded by the supported TEP-2 friendly format.
 */
struct UnsupportedWorkchain: TonAddressError {

    UnsupportedWorkchain() : TonAddressError("") {}
    UnsupportedWorkchain(const std::string &what_arg) : TonAddressError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 2;
    }
};
} // namespace ton_address_error


/**
 * Connect-event error codes a wallet reports for a failed connection.
 */
enum class TonConnectConnectErrorCode: int32_t {
    /**
     * The dApp manifest could not be fetched (protocol code 2).
     */
    kManifestNotFound = 1,
    /**
     * The dApp manifest is malformed (protocol code 3).
     */
    kManifestContent = 2,
    /**
     * The user declined the connection (protocol code 300).
     */
    kUserDeclined = 3
};

namespace uniffi {
struct FfiConverterTonConnectSessionError;
} // namespace uniffi

/**
 * Failure at the FFI-safe TON Connect session boundary.
 */
struct TonConnectSessionError: std::runtime_error {
    friend uniffi::FfiConverterTonConnectSessionError;

    TonConnectSessionError() : std::runtime_error("") {}
    TonConnectSessionError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~TonConnectSessionError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of TonConnectSessionError
 */
namespace ton_connect_session_error {

/**
 * The session or protocol input is invalid.
 */
struct Failed: TonConnectSessionError {
    std::string diagnostic;

    Failed() : TonConnectSessionError("") {}
    Failed(const std::string &what_arg) : TonConnectSessionError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};
} // namespace ton_connect_session_error


/**
 * Public lifecycle phase of a TON Connect session.
 */
enum class TonConnectSessionPhase: int32_t {
    /**
     * Waiting for connect approval.
     */
    kPendingConnect = 1,
    /**
     * Connected and accepting RPC requests.
     */
    kConnected = 2,
    /**
     * Terminally disconnected.
     */
    kDisconnected = 3
};

namespace uniffi {
struct FfiConverterTonTransferLinkError;
} // namespace uniffi

/**
 * Reports why a `ton://transfer/` link could not be parsed.
 */
struct TonTransferLinkError: std::runtime_error {
    friend uniffi::FfiConverterTonTransferLinkError;

    TonTransferLinkError() : std::runtime_error("") {}
    TonTransferLinkError(const std::string &what_arg) : std::runtime_error(what_arg) {}

    virtual ~TonTransferLinkError() = default;

    virtual void throw_underlying() {
        throw *this;
    }

protected:
    virtual int32_t get_variant_idx() const {
        return 0;
    };
};
/**
 * Contains variants of TonTransferLinkError
 */
namespace ton_transfer_link_error {

/**
 * The URI belongs to a different scheme or command.
 */
struct NotTonTransfer: TonTransferLinkError {

    NotTonTransfer() : TonTransferLinkError("") {}
    NotTonTransfer(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 1;
    }
};

/**
 * The URI structure or encoding is invalid.
 */
struct InvalidUrl: TonTransferLinkError {

    InvalidUrl() : TonTransferLinkError("") {}
    InvalidUrl(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 2;
    }
};

/**
 * The transfer command has no recipient path segment.
 */
struct MissingRecipient: TonTransferLinkError {

    MissingRecipient() : TonTransferLinkError("") {}
    MissingRecipient(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 3;
    }
};

/**
 * The recipient is not a valid TON account address.
 */
struct InvalidRecipient: TonTransferLinkError {

    InvalidRecipient() : TonTransferLinkError("") {}
    InvalidRecipient(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 4;
    }
};

/**
 * A singleton baseline query parameter occurs more than once.
 */
struct DuplicateParameter: TonTransferLinkError {
    std::string name;

    DuplicateParameter() : TonTransferLinkError("") {}
    DuplicateParameter(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 5;
    }
};

/**
 * The strict baseline does not recognize a query parameter.
 */
struct UnsupportedParameter: TonTransferLinkError {
    std::string name;

    UnsupportedParameter() : TonTransferLinkError("") {}
    UnsupportedParameter(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 6;
    }
};

/**
 * `amount` is not a canonical unsigned decimal integer.
 */
struct InvalidAmount: TonTransferLinkError {

    InvalidAmount() : TonTransferLinkError("") {}
    InvalidAmount(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 7;
    }
};

/**
 * `exp` is not a canonical unsigned `u64` Unix timestamp.
 */
struct InvalidExpiration: TonTransferLinkError {

    InvalidExpiration() : TonTransferLinkError("") {}
    InvalidExpiration(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 8;
    }
};

/**
 * `jetton` is not a valid TON account address.
 */
struct InvalidJettonMaster: TonTransferLinkError {

    InvalidJettonMaster() : TonTransferLinkError("") {}
    InvalidJettonMaster(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 9;
    }
};

/**
 * `bin` is not standard Base64 containing one valid BOC root.
 */
struct InvalidBinaryPayload: TonTransferLinkError {

    InvalidBinaryPayload() : TonTransferLinkError("") {}
    InvalidBinaryPayload(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 10;
    }
};

/**
 * `text` and `bin` cannot both define the payload.
 */
struct ConflictingPayloads: TonTransferLinkError {

    ConflictingPayloads() : TonTransferLinkError("") {}
    ConflictingPayloads(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 11;
    }
};

/**
 * Strict baseline mode does not define the meaning of `bin` for a jetton.
 */
struct BinaryJettonConflict: TonTransferLinkError {

    BinaryJettonConflict() : TonTransferLinkError("") {}
    BinaryJettonConflict(const std::string &what_arg) : TonTransferLinkError(what_arg) {}

    void throw_underlying() override {
        throw *this;
    }

protected:
    int32_t get_variant_idx() const override {
        return 12;
    }
};
} // namespace ton_transfer_link_error

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
struct FfiConverterUInt16 {
    static uint16_t lift(uint16_t);
    static uint16_t lower(uint16_t);
    static uint16_t read(RustStream &);
    static void write(RustStream &, uint16_t);
    static uint64_t allocation_size(uint16_t);
};
struct FfiConverterUInt32 {
    static uint32_t lift(uint32_t);
    static uint32_t lower(uint32_t);
    static uint32_t read(RustStream &);
    static void write(RustStream &, uint32_t);
    static uint64_t allocation_size(uint32_t);
};
struct FfiConverterInt32 {
    static int32_t lift(int32_t);
    static int32_t lower(int32_t);
    static int32_t read(RustStream &);
    static void write(RustStream &, int32_t);
    static uint64_t allocation_size(int32_t);
};
struct FfiConverterUInt64 {
    static uint64_t lift(uint64_t);
    static uint64_t lower(uint64_t);
    static uint64_t read(RustStream &);
    static void write(RustStream &, uint64_t);
    static uint64_t allocation_size(uint64_t);
};
struct FfiConverterInt64 {
    static int64_t lift(int64_t);
    static int64_t lower(int64_t);
    static int64_t read(RustStream &);
    static void write(RustStream &, int64_t);
    static uint64_t allocation_size(int64_t);
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

struct FfiConverterBytes {
    static std::vector<uint8_t> lift(RustBuffer);
    static RustBuffer lower(const std::vector<uint8_t> &);
    static std::vector<uint8_t> read(RustStream &);
    static void write(RustStream &, const std::vector<uint8_t> &);
    static uint64_t allocation_size(const std::vector<uint8_t> &);
};


struct FfiConverterTonConnectDerivedSession {
    static std::shared_ptr<TonConnectDerivedSession> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<TonConnectDerivedSession> &);
    static std::shared_ptr<TonConnectDerivedSession> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<TonConnectDerivedSession> &);
    static uint64_t allocation_size(const std::shared_ptr<TonConnectDerivedSession> &);
private:
};


struct FfiConverterTonConnectSession {
    static std::shared_ptr<TonConnectSession> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<TonConnectSession> &);
    static std::shared_ptr<TonConnectSession> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<TonConnectSession> &);
    static uint64_t allocation_size(const std::shared_ptr<TonConnectSession> &);
private:
};


struct FfiConverterWalletClient {
    static std::shared_ptr<WalletClient> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<WalletClient> &);
    static std::shared_ptr<WalletClient> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<WalletClient> &);
    static uint64_t allocation_size(const std::shared_ptr<WalletClient> &);
private:
};


struct FfiConverterWalletHttpHost {
    static std::shared_ptr<WalletHttpHost> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<WalletHttpHost> &);
    static std::shared_ptr<WalletHttpHost> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<WalletHttpHost> &);
    static uint64_t allocation_size(const std::shared_ptr<WalletHttpHost> &);
private:
    friend struct UniffiCallbackInterfaceWalletHttpHost;
    inline static HandleMap<WalletHttpHost> handle_map = {};
};


struct FfiConverterWalletLifecycle {
    static std::shared_ptr<WalletLifecycle> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<WalletLifecycle> &);
    static std::shared_ptr<WalletLifecycle> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<WalletLifecycle> &);
    static uint64_t allocation_size(const std::shared_ptr<WalletLifecycle> &);
private:
};


struct FfiConverterWalletPlatformHost {
    static std::shared_ptr<WalletPlatformHost> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<WalletPlatformHost> &);
    static std::shared_ptr<WalletPlatformHost> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<WalletPlatformHost> &);
    static uint64_t allocation_size(const std::shared_ptr<WalletPlatformHost> &);
private:
    friend struct UniffiCallbackInterfaceWalletPlatformHost;
    inline static HandleMap<WalletPlatformHost> handle_map = {};
};


struct FfiConverterWalletStatuslessHost {
    static std::shared_ptr<WalletStatuslessHost> lift(uint64_t);
    static uint64_t lower(const std::shared_ptr<WalletStatuslessHost> &);
    static std::shared_ptr<WalletStatuslessHost> read(RustStream &);
    static void write(RustStream &, const std::shared_ptr<WalletStatuslessHost> &);
    static uint64_t allocation_size(const std::shared_ptr<WalletStatuslessHost> &);
private:
    friend struct UniffiCallbackInterfaceWalletStatuslessHost;
    inline static HandleMap<WalletStatuslessHost> handle_map = {};
};

struct FfiConverterTypeAccountSnapshot {
    static AccountSnapshot lift(RustBuffer);
    static RustBuffer lower(const AccountSnapshot &);
    static AccountSnapshot read(RustStream &);
    static void write(RustStream &, const AccountSnapshot &);
    static uint64_t allocation_size(const AccountSnapshot &);
};

struct FfiConverterTypeActivityCursor {
    static ActivityCursor lift(RustBuffer);
    static RustBuffer lower(const ActivityCursor &);
    static ActivityCursor read(RustStream &);
    static void write(RustStream &, const ActivityCursor &);
    static uint64_t allocation_size(const ActivityCursor &);
};

struct FfiConverterTypeActivityItem {
    static ActivityItem lift(RustBuffer);
    static RustBuffer lower(const ActivityItem &);
    static ActivityItem read(RustStream &);
    static void write(RustStream &, const ActivityItem &);
    static uint64_t allocation_size(const ActivityItem &);
};

struct FfiConverterTypeActivityList {
    static ActivityList lift(RustBuffer);
    static RustBuffer lower(const ActivityList &);
    static ActivityList read(RustStream &);
    static void write(RustStream &, const ActivityList &);
    static uint64_t allocation_size(const ActivityList &);
};

struct FfiConverterTypeCreateEncryptedCommentRequest {
    static CreateEncryptedCommentRequest lift(RustBuffer);
    static RustBuffer lower(const CreateEncryptedCommentRequest &);
    static CreateEncryptedCommentRequest read(RustStream &);
    static void write(RustStream &, const CreateEncryptedCommentRequest &);
    static uint64_t allocation_size(const CreateEncryptedCommentRequest &);
};

struct FfiConverterTypeCreateWalletRequest {
    static CreateWalletRequest lift(RustBuffer);
    static RustBuffer lower(const CreateWalletRequest &);
    static CreateWalletRequest read(RustStream &);
    static void write(RustStream &, const CreateWalletRequest &);
    static uint64_t allocation_size(const CreateWalletRequest &);
};

struct FfiConverterTypeCreatedWallet {
    static CreatedWallet lift(RustBuffer);
    static RustBuffer lower(const CreatedWallet &);
    static CreatedWallet read(RustStream &);
    static void write(RustStream &, const CreatedWallet &);
    static uint64_t allocation_size(const CreatedWallet &);
};

struct FfiConverterTypeDecryptCommentRequest {
    static DecryptCommentRequest lift(RustBuffer);
    static RustBuffer lower(const DecryptCommentRequest &);
    static DecryptCommentRequest read(RustStream &);
    static void write(RustStream &, const DecryptCommentRequest &);
    static uint64_t allocation_size(const DecryptCommentRequest &);
};

struct FfiConverterTypeDomainError {
    static DomainError lift(RustBuffer);
    static RustBuffer lower(const DomainError &);
    static DomainError read(RustStream &);
    static void write(RustStream &, const DomainError &);
    static uint64_t allocation_size(const DomainError &);
};

struct FfiConverterTypeEncryptedCommentRecipientRequest {
    static EncryptedCommentRecipientRequest lift(RustBuffer);
    static RustBuffer lower(const EncryptedCommentRecipientRequest &);
    static EncryptedCommentRecipientRequest read(RustStream &);
    static void write(RustStream &, const EncryptedCommentRecipientRequest &);
    static uint64_t allocation_size(const EncryptedCommentRecipientRequest &);
};

struct FfiConverterTypeHttpHeader {
    static HttpHeader lift(RustBuffer);
    static RustBuffer lower(const HttpHeader &);
    static HttpHeader read(RustStream &);
    static void write(RustStream &, const HttpHeader &);
    static uint64_t allocation_size(const HttpHeader &);
};

struct FfiConverterTypeHttpRequest {
    static HttpRequest lift(RustBuffer);
    static RustBuffer lower(const HttpRequest &);
    static HttpRequest read(RustStream &);
    static void write(RustStream &, const HttpRequest &);
    static uint64_t allocation_size(const HttpRequest &);
};

struct FfiConverterTypeHttpRequestId {
    static HttpRequestId lift(RustBuffer);
    static RustBuffer lower(const HttpRequestId &);
    static HttpRequestId read(RustStream &);
    static void write(RustStream &, const HttpRequestId &);
    static uint64_t allocation_size(const HttpRequestId &);
};

struct FfiConverterTypeHttpResponse {
    static HttpResponse lift(RustBuffer);
    static RustBuffer lower(const HttpResponse &);
    static HttpResponse read(RustStream &);
    static void write(RustStream &, const HttpResponse &);
    static uint64_t allocation_size(const HttpResponse &);
};

struct FfiConverterTypeImportWalletRequest {
    static ImportWalletRequest lift(RustBuffer);
    static RustBuffer lower(const ImportWalletRequest &);
    static ImportWalletRequest read(RustStream &);
    static void write(RustStream &, const ImportWalletRequest &);
    static uint64_t allocation_size(const ImportWalletRequest &);
};

struct FfiConverterTypeJournalCompareExchange {
    static JournalCompareExchange lift(RustBuffer);
    static RustBuffer lower(const JournalCompareExchange &);
    static JournalCompareExchange read(RustStream &);
    static void write(RustStream &, const JournalCompareExchange &);
    static uint64_t allocation_size(const JournalCompareExchange &);
};

struct FfiConverterTypeJournalCompareExchangeResult {
    static JournalCompareExchangeResult lift(RustBuffer);
    static RustBuffer lower(const JournalCompareExchangeResult &);
    static JournalCompareExchangeResult read(RustStream &);
    static void write(RustStream &, const JournalCompareExchangeResult &);
    static uint64_t allocation_size(const JournalCompareExchangeResult &);
};

struct FfiConverterTypeJournalKey {
    static JournalKey lift(RustBuffer);
    static RustBuffer lower(const JournalKey &);
    static JournalKey read(RustStream &);
    static void write(RustStream &, const JournalKey &);
    static uint64_t allocation_size(const JournalKey &);
};

struct FfiConverterTypeJournalRecord {
    static JournalRecord lift(RustBuffer);
    static RustBuffer lower(const JournalRecord &);
    static JournalRecord read(RustStream &);
    static void write(RustStream &, const JournalRecord &);
    static uint64_t allocation_size(const JournalRecord &);
};

struct FfiConverterTypeNftCollectionDescriptor {
    static NftCollectionDescriptor lift(RustBuffer);
    static RustBuffer lower(const NftCollectionDescriptor &);
    static NftCollectionDescriptor read(RustStream &);
    static void write(RustStream &, const NftCollectionDescriptor &);
    static uint64_t allocation_size(const NftCollectionDescriptor &);
};

struct FfiConverterTypeNftItem {
    static NftItem lift(RustBuffer);
    static RustBuffer lower(const NftItem &);
    static NftItem read(RustStream &);
    static void write(RustStream &, const NftItem &);
    static uint64_t allocation_size(const NftItem &);
};

struct FfiConverterTypeNftList {
    static NftList lift(RustBuffer);
    static RustBuffer lower(const NftList &);
    static NftList read(RustStream &);
    static void write(RustStream &, const NftList &);
    static uint64_t allocation_size(const NftList &);
};

struct FfiConverterTypeNftTransferIntent {
    static NftTransferIntent lift(RustBuffer);
    static RustBuffer lower(const NftTransferIntent &);
    static NftTransferIntent read(RustStream &);
    static void write(RustStream &, const NftTransferIntent &);
    static uint64_t allocation_size(const NftTransferIntent &);
};

struct FfiConverterTypeNftTransferPreviewRequest {
    static NftTransferPreviewRequest lift(RustBuffer);
    static RustBuffer lower(const NftTransferPreviewRequest &);
    static NftTransferPreviewRequest read(RustStream &);
    static void write(RustStream &, const NftTransferPreviewRequest &);
    static uint64_t allocation_size(const NftTransferPreviewRequest &);
};

struct FfiConverterTypeNftTransferRequest {
    static NftTransferRequest lift(RustBuffer);
    static RustBuffer lower(const NftTransferRequest &);
    static NftTransferRequest read(RustStream &);
    static void write(RustStream &, const NftTransferRequest &);
    static uint64_t allocation_size(const NftTransferRequest &);
};

struct FfiConverterTypeParsedTonTransferLink {
    static ParsedTonTransferLink lift(RustBuffer);
    static RustBuffer lower(const ParsedTonTransferLink &);
    static ParsedTonTransferLink read(RustStream &);
    static void write(RustStream &, const ParsedTonTransferLink &);
    static uint64_t allocation_size(const ParsedTonTransferLink &);
};

struct FfiConverterTypePrepareKeyRotationRequest {
    static PrepareKeyRotationRequest lift(RustBuffer);
    static RustBuffer lower(const PrepareKeyRotationRequest &);
    static PrepareKeyRotationRequest read(RustStream &);
    static void write(RustStream &, const PrepareKeyRotationRequest &);
    static uint64_t allocation_size(const PrepareKeyRotationRequest &);
};

struct FfiConverterTypePrepareNftTransferRequest {
    static PrepareNftTransferRequest lift(RustBuffer);
    static RustBuffer lower(const PrepareNftTransferRequest &);
    static PrepareNftTransferRequest read(RustStream &);
    static void write(RustStream &, const PrepareNftTransferRequest &);
    static uint64_t allocation_size(const PrepareNftTransferRequest &);
};

struct FfiConverterTypePrepareTransferRequest {
    static PrepareTransferRequest lift(RustBuffer);
    static RustBuffer lower(const PrepareTransferRequest &);
    static PrepareTransferRequest read(RustStream &);
    static void write(RustStream &, const PrepareTransferRequest &);
    static uint64_t allocation_size(const PrepareTransferRequest &);
};

struct FfiConverterTypePreparedKeyRotation {
    static PreparedKeyRotation lift(RustBuffer);
    static RustBuffer lower(const PreparedKeyRotation &);
    static PreparedKeyRotation read(RustStream &);
    static void write(RustStream &, const PreparedKeyRotation &);
    static uint64_t allocation_size(const PreparedKeyRotation &);
};

struct FfiConverterTypePreparedTransfer {
    static PreparedTransfer lift(RustBuffer);
    static RustBuffer lower(const PreparedTransfer &);
    static PreparedTransfer read(RustStream &);
    static void write(RustStream &, const PreparedTransfer &);
    static uint64_t allocation_size(const PreparedTransfer &);
};

struct FfiConverterTypeProtectedSecretRead {
    static ProtectedSecretRead lift(RustBuffer);
    static RustBuffer lower(const ProtectedSecretRead &);
    static ProtectedSecretRead read(RustStream &);
    static void write(RustStream &, const ProtectedSecretRead &);
    static uint64_t allocation_size(const ProtectedSecretRead &);
};

struct FfiConverterTypeProtectedSecretRef {
    static ProtectedSecretRef lift(RustBuffer);
    static RustBuffer lower(const ProtectedSecretRef &);
    static ProtectedSecretRef read(RustStream &);
    static void write(RustStream &, const ProtectedSecretRef &);
    static uint64_t allocation_size(const ProtectedSecretRef &);
};

struct FfiConverterTypeProtectedSecretStore {
    static ProtectedSecretStore lift(RustBuffer);
    static RustBuffer lower(const ProtectedSecretStore &);
    static ProtectedSecretStore read(RustStream &);
    static void write(RustStream &, const ProtectedSecretStore &);
    static uint64_t allocation_size(const ProtectedSecretStore &);
};

struct FfiConverterTypeProviderConfig {
    static ProviderConfig lift(RustBuffer);
    static RustBuffer lower(const ProviderConfig &);
    static ProviderConfig read(RustStream &);
    static void write(RustStream &, const ProviderConfig &);
    static uint64_t allocation_size(const ProviderConfig &);
};

struct FfiConverterTypeRecoveryPhrase {
    static RecoveryPhrase lift(RustBuffer);
    static RustBuffer lower(const RecoveryPhrase &);
    static RecoveryPhrase read(RustStream &);
    static void write(RustStream &, const RecoveryPhrase &);
    static uint64_t allocation_size(const RecoveryPhrase &);
};

struct FfiConverterTypeResolutionInfo {
    static ResolutionInfo lift(RustBuffer);
    static RustBuffer lower(const ResolutionInfo &);
    static ResolutionInfo read(RustStream &);
    static void write(RustStream &, const ResolutionInfo &);
    static uint64_t allocation_size(const ResolutionInfo &);
};

struct FfiConverterTypeResourceState {
    static ResourceState lift(RustBuffer);
    static RustBuffer lower(const ResourceState &);
    static ResourceState read(RustStream &);
    static void write(RustStream &, const ResourceState &);
    static uint64_t allocation_size(const ResourceState &);
};

struct FfiConverterTypeSendBocRequest {
    static SendBocRequest lift(RustBuffer);
    static RustBuffer lower(const SendBocRequest &);
    static SendBocRequest read(RustStream &);
    static void write(RustStream &, const SendBocRequest &);
    static uint64_t allocation_size(const SendBocRequest &);
};

struct FfiConverterTypeSendEmulation {
    static SendEmulation lift(RustBuffer);
    static RustBuffer lower(const SendEmulation &);
    static SendEmulation read(RustStream &);
    static void write(RustStream &, const SendEmulation &);
    static uint64_t allocation_size(const SendEmulation &);
};

struct FfiConverterTypeSendEmulationAction {
    static SendEmulationAction lift(RustBuffer);
    static RustBuffer lower(const SendEmulationAction &);
    static SendEmulationAction read(RustStream &);
    static void write(RustStream &, const SendEmulationAction &);
    static uint64_t allocation_size(const SendEmulationAction &);
};

struct FfiConverterTypeSendEmulationTransaction {
    static SendEmulationTransaction lift(RustBuffer);
    static RustBuffer lower(const SendEmulationTransaction &);
    static SendEmulationTransaction read(RustStream &);
    static void write(RustStream &, const SendEmulationTransaction &);
    static uint64_t allocation_size(const SendEmulationTransaction &);
};

struct FfiConverterTypeSendIntent {
    static SendIntent lift(RustBuffer);
    static RustBuffer lower(const SendIntent &);
    static SendIntent read(RustStream &);
    static void write(RustStream &, const SendIntent &);
    static uint64_t allocation_size(const SendIntent &);
};

struct FfiConverterTypeSendMessage {
    static SendMessage lift(RustBuffer);
    static RustBuffer lower(const SendMessage &);
    static SendMessage read(RustStream &);
    static void write(RustStream &, const SendMessage &);
    static uint64_t allocation_size(const SendMessage &);
};

struct FfiConverterTypeSendPreview {
    static SendPreview lift(RustBuffer);
    static RustBuffer lower(const SendPreview &);
    static SendPreview read(RustStream &);
    static void write(RustStream &, const SendPreview &);
    static uint64_t allocation_size(const SendPreview &);
};

struct FfiConverterTypeSendPreviewRequest {
    static SendPreviewRequest lift(RustBuffer);
    static RustBuffer lower(const SendPreviewRequest &);
    static SendPreviewRequest read(RustStream &);
    static void write(RustStream &, const SendPreviewRequest &);
    static uint64_t allocation_size(const SendPreviewRequest &);
};

struct FfiConverterTypeSendRequest {
    static SendRequest lift(RustBuffer);
    static RustBuffer lower(const SendRequest &);
    static SendRequest read(RustStream &);
    static void write(RustStream &, const SendRequest &);
    static uint64_t allocation_size(const SendRequest &);
};

struct FfiConverterTypeSendResult {
    static SendResult lift(RustBuffer);
    static RustBuffer lower(const SendResult &);
    static SendResult read(RustStream &);
    static void write(RustStream &, const SendResult &);
    static uint64_t allocation_size(const SendResult &);
};

struct FfiConverterTypeSendSnapshot {
    static SendSnapshot lift(RustBuffer);
    static RustBuffer lower(const SendSnapshot &);
    static SendSnapshot read(RustStream &);
    static void write(RustStream &, const SendSnapshot &);
    static uint64_t allocation_size(const SendSnapshot &);
};

struct FfiConverterTypeSignMessagePreview {
    static SignMessagePreview lift(RustBuffer);
    static RustBuffer lower(const SignMessagePreview &);
    static SignMessagePreview read(RustStream &);
    static void write(RustStream &, const SignMessagePreview &);
    static uint64_t allocation_size(const SignMessagePreview &);
};

struct FfiConverterTypeSignMessageRequest {
    static SignMessageRequest lift(RustBuffer);
    static RustBuffer lower(const SignMessageRequest &);
    static SignMessageRequest read(RustStream &);
    static void write(RustStream &, const SignMessageRequest &);
    static uint64_t allocation_size(const SignMessageRequest &);
};

struct FfiConverterTypeSignMessageResult {
    static SignMessageResult lift(RustBuffer);
    static RustBuffer lower(const SignMessageResult &);
    static SignMessageResult read(RustStream &);
    static void write(RustStream &, const SignMessageResult &);
    static uint64_t allocation_size(const SignMessageResult &);
};

struct FfiConverterTypeTonAddressInfo {
    static TonAddressInfo lift(RustBuffer);
    static RustBuffer lower(const TonAddressInfo &);
    static TonAddressInfo read(RustStream &);
    static void write(RustStream &, const TonAddressInfo &);
    static uint64_t allocation_size(const TonAddressInfo &);
};

struct FfiConverterTypeTonConnectAccountInfo {
    static TonConnectAccountInfo lift(RustBuffer);
    static RustBuffer lower(const TonConnectAccountInfo &);
    static TonConnectAccountInfo read(RustStream &);
    static void write(RustStream &, const TonConnectAccountInfo &);
    static uint64_t allocation_size(const TonConnectAccountInfo &);
};

struct FfiConverterTypeTonConnectConnectPrompt {
    static TonConnectConnectPrompt lift(RustBuffer);
    static RustBuffer lower(const TonConnectConnectPrompt &);
    static TonConnectConnectPrompt read(RustStream &);
    static void write(RustStream &, const TonConnectConnectPrompt &);
    static uint64_t allocation_size(const TonConnectConnectPrompt &);
};

struct FfiConverterTypeTonConnectDerivedRequest {
    static TonConnectDerivedRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectDerivedRequest &);
    static TonConnectDerivedRequest read(RustStream &);
    static void write(RustStream &, const TonConnectDerivedRequest &);
    static uint64_t allocation_size(const TonConnectDerivedRequest &);
};

struct FfiConverterTypeTonConnectDerivedSessionRequest {
    static TonConnectDerivedSessionRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectDerivedSessionRequest &);
    static TonConnectDerivedSessionRequest read(RustStream &);
    static void write(RustStream &, const TonConnectDerivedSessionRequest &);
    static uint64_t allocation_size(const TonConnectDerivedSessionRequest &);
};

struct FfiConverterTypeTonConnectDevice {
    static TonConnectDevice lift(RustBuffer);
    static RustBuffer lower(const TonConnectDevice &);
    static TonConnectDevice read(RustStream &);
    static void write(RustStream &, const TonConnectDevice &);
    static uint64_t allocation_size(const TonConnectDevice &);
};

struct FfiConverterTypeTonConnectManifest {
    static TonConnectManifest lift(RustBuffer);
    static RustBuffer lower(const TonConnectManifest &);
    static TonConnectManifest read(RustStream &);
    static void write(RustStream &, const TonConnectManifest &);
    static uint64_t allocation_size(const TonConnectManifest &);
};

struct FfiConverterTypeTonConnectPreparedPost {
    static TonConnectPreparedPost lift(RustBuffer);
    static RustBuffer lower(const TonConnectPreparedPost &);
    static TonConnectPreparedPost read(RustStream &);
    static void write(RustStream &, const TonConnectPreparedPost &);
    static uint64_t allocation_size(const TonConnectPreparedPost &);
};

struct FfiConverterTypeTonConnectProofReply {
    static TonConnectProofReply lift(RustBuffer);
    static RustBuffer lower(const TonConnectProofReply &);
    static TonConnectProofReply read(RustStream &);
    static void write(RustStream &, const TonConnectProofReply &);
    static uint64_t allocation_size(const TonConnectProofReply &);
};

struct FfiConverterTypeTonConnectProofSignRequest {
    static TonConnectProofSignRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectProofSignRequest &);
    static TonConnectProofSignRequest read(RustStream &);
    static void write(RustStream &, const TonConnectProofSignRequest &);
    static uint64_t allocation_size(const TonConnectProofSignRequest &);
};

struct FfiConverterTypeTonConnectProofSignature {
    static TonConnectProofSignature lift(RustBuffer);
    static RustBuffer lower(const TonConnectProofSignature &);
    static TonConnectProofSignature read(RustStream &);
    static void write(RustStream &, const TonConnectProofSignature &);
    static uint64_t allocation_size(const TonConnectProofSignature &);
};

struct FfiConverterTypeTonConnectSessionConfig {
    static TonConnectSessionConfig lift(RustBuffer);
    static RustBuffer lower(const TonConnectSessionConfig &);
    static TonConnectSessionConfig read(RustStream &);
    static void write(RustStream &, const TonConnectSessionConfig &);
    static uint64_t allocation_size(const TonConnectSessionConfig &);
};

struct FfiConverterTypeTonConnectSignDataRequest {
    static TonConnectSignDataRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectSignDataRequest &);
    static TonConnectSignDataRequest read(RustStream &);
    static void write(RustStream &, const TonConnectSignDataRequest &);
    static uint64_t allocation_size(const TonConnectSignDataRequest &);
};

struct FfiConverterTypeTonConnectSignDataSignRequest {
    static TonConnectSignDataSignRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectSignDataSignRequest &);
    static TonConnectSignDataSignRequest read(RustStream &);
    static void write(RustStream &, const TonConnectSignDataSignRequest &);
    static uint64_t allocation_size(const TonConnectSignDataSignRequest &);
};

struct FfiConverterTypeTonConnectSignedData {
    static TonConnectSignedData lift(RustBuffer);
    static RustBuffer lower(const TonConnectSignedData &);
    static TonConnectSignedData read(RustStream &);
    static void write(RustStream &, const TonConnectSignedData &);
    static uint64_t allocation_size(const TonConnectSignedData &);
};

struct FfiConverterTypeWalletClientConfig {
    static WalletClientConfig lift(RustBuffer);
    static RustBuffer lower(const WalletClientConfig &);
    static WalletClientConfig read(RustStream &);
    static void write(RustStream &, const WalletClientConfig &);
    static uint64_t allocation_size(const WalletClientConfig &);
};

struct FfiConverterTypeWalletDescriptor {
    static WalletDescriptor lift(RustBuffer);
    static RustBuffer lower(const WalletDescriptor &);
    static WalletDescriptor read(RustStream &);
    static void write(RustStream &, const WalletDescriptor &);
    static uint64_t allocation_size(const WalletDescriptor &);
};

struct FfiConverterTypeWalletSnapshot {
    static WalletSnapshot lift(RustBuffer);
    static RustBuffer lower(const WalletSnapshot &);
    static WalletSnapshot read(RustStream &);
    static void write(RustStream &, const WalletSnapshot &);
    static uint64_t allocation_size(const WalletSnapshot &);
};

struct FfiConverterTypeWalletUpdate {
    static WalletUpdate lift(RustBuffer);
    static RustBuffer lower(const WalletUpdate &);
    static WalletUpdate read(RustStream &);
    static void write(RustStream &, const WalletUpdate &);
    static uint64_t allocation_size(const WalletUpdate &);
};
struct FfiConverterAccountStatus {
    static AccountStatus lift(RustBuffer);
    static RustBuffer lower(const AccountStatus &);
    static AccountStatus read(RustStream &);
    static void write(RustStream &, const AccountStatus &);
    static uint64_t allocation_size(const AccountStatus &);
};
struct FfiConverterActivityDirection {
    static ActivityDirection lift(RustBuffer);
    static RustBuffer lower(const ActivityDirection &);
    static ActivityDirection read(RustStream &);
    static void write(RustStream &, const ActivityDirection &);
    static uint64_t allocation_size(const ActivityDirection &);
};
struct FfiConverterActivityStatus {
    static ActivityStatus lift(RustBuffer);
    static RustBuffer lower(const ActivityStatus &);
    static ActivityStatus read(RustStream &);
    static void write(RustStream &, const ActivityStatus &);
    static uint64_t allocation_size(const ActivityStatus &);
};
struct FfiConverterErrorCategory {
    static ErrorCategory lift(RustBuffer);
    static RustBuffer lower(const ErrorCategory &);
    static ErrorCategory read(RustStream &);
    static void write(RustStream &, const ErrorCategory &);
    static uint64_t allocation_size(const ErrorCategory &);
};
struct FfiConverterErrorCode {
    static ErrorCode lift(RustBuffer);
    static RustBuffer lower(const ErrorCode &);
    static ErrorCode read(RustStream &);
    static void write(RustStream &, const ErrorCode &);
    static uint64_t allocation_size(const ErrorCode &);
};

struct FfiConverterHttpHostError {
    static std::shared_ptr<HttpHostError> lift(RustBuffer buf);
    static RustBuffer lower(const HttpHostError &);
    static std::shared_ptr<HttpHostError> read(RustStream &stream);
    static void write(RustStream &stream, const HttpHostError &);
    static uint64_t allocation_size(const HttpHostError &);
};
struct FfiConverterHttpHostErrorKind {
    static HttpHostErrorKind lift(RustBuffer);
    static RustBuffer lower(const HttpHostErrorKind &);
    static HttpHostErrorKind read(RustStream &);
    static void write(RustStream &, const HttpHostErrorKind &);
    static uint64_t allocation_size(const HttpHostErrorKind &);
};
struct FfiConverterHttpMethod {
    static HttpMethod lift(RustBuffer);
    static RustBuffer lower(const HttpMethod &);
    static HttpMethod read(RustStream &);
    static void write(RustStream &, const HttpMethod &);
    static uint64_t allocation_size(const HttpMethod &);
};

struct FfiConverterJournalHostError {
    static std::shared_ptr<JournalHostError> lift(RustBuffer buf);
    static RustBuffer lower(const JournalHostError &);
    static std::shared_ptr<JournalHostError> read(RustStream &stream);
    static void write(RustStream &stream, const JournalHostError &);
    static uint64_t allocation_size(const JournalHostError &);
};
struct FfiConverterJournalHostErrorKind {
    static JournalHostErrorKind lift(RustBuffer);
    static RustBuffer lower(const JournalHostErrorKind &);
    static JournalHostErrorKind read(RustStream &);
    static void write(RustStream &, const JournalHostErrorKind &);
    static uint64_t allocation_size(const JournalHostErrorKind &);
};
struct FfiConverterKeyRotationMessageKind {
    static KeyRotationMessageKind lift(RustBuffer);
    static RustBuffer lower(const KeyRotationMessageKind &);
    static KeyRotationMessageKind read(RustStream &);
    static void write(RustStream &, const KeyRotationMessageKind &);
    static uint64_t allocation_size(const KeyRotationMessageKind &);
};
struct FfiConverterMnemonicScheme {
    static MnemonicScheme lift(RustBuffer);
    static RustBuffer lower(const MnemonicScheme &);
    static MnemonicScheme read(RustStream &);
    static void write(RustStream &, const MnemonicScheme &);
    static uint64_t allocation_size(const MnemonicScheme &);
};
struct FfiConverterNetwork {
    static Network lift(RustBuffer);
    static RustBuffer lower(const Network &);
    static Network read(RustStream &);
    static void write(RustStream &, const Network &);
    static uint64_t allocation_size(const Network &);
};
struct FfiConverterNftTransferFunding {
    static NftTransferFunding lift(RustBuffer);
    static RustBuffer lower(const NftTransferFunding &);
    static NftTransferFunding read(RustStream &);
    static void write(RustStream &, const NftTransferFunding &);
    static uint64_t allocation_size(const NftTransferFunding &);
};
struct FfiConverterNftTransferPayload {
    static NftTransferPayload lift(RustBuffer);
    static RustBuffer lower(const NftTransferPayload &);
    static NftTransferPayload read(RustStream &);
    static void write(RustStream &, const NftTransferPayload &);
    static uint64_t allocation_size(const NftTransferPayload &);
};
struct FfiConverterPendingReason {
    static PendingReason lift(RustBuffer);
    static RustBuffer lower(const PendingReason &);
    static PendingReason read(RustStream &);
    static void write(RustStream &, const PendingReason &);
    static uint64_t allocation_size(const PendingReason &);
};

struct FfiConverterProtectedSecretHostError {
    static std::shared_ptr<ProtectedSecretHostError> lift(RustBuffer buf);
    static RustBuffer lower(const ProtectedSecretHostError &);
    static std::shared_ptr<ProtectedSecretHostError> read(RustStream &stream);
    static void write(RustStream &stream, const ProtectedSecretHostError &);
    static uint64_t allocation_size(const ProtectedSecretHostError &);
};
struct FfiConverterProtectedSecretHostErrorKind {
    static ProtectedSecretHostErrorKind lift(RustBuffer);
    static RustBuffer lower(const ProtectedSecretHostErrorKind &);
    static ProtectedSecretHostErrorKind read(RustStream &);
    static void write(RustStream &, const ProtectedSecretHostErrorKind &);
    static uint64_t allocation_size(const ProtectedSecretHostErrorKind &);
};
struct FfiConverterResourcePhase {
    static ResourcePhase lift(RustBuffer);
    static RustBuffer lower(const ResourcePhase &);
    static ResourcePhase read(RustStream &);
    static void write(RustStream &, const ResourcePhase &);
    static uint64_t allocation_size(const ResourcePhase &);
};
struct FfiConverterRetryAdvice {
    static RetryAdvice lift(RustBuffer);
    static RustBuffer lower(const RetryAdvice &);
    static RetryAdvice read(RustStream &);
    static void write(RustStream &, const RetryAdvice &);
    static uint64_t allocation_size(const RetryAdvice &);
};
struct FfiConverterSecretAccessReason {
    static SecretAccessReason lift(RustBuffer);
    static RustBuffer lower(const SecretAccessReason &);
    static SecretAccessReason read(RustStream &);
    static void write(RustStream &, const SecretAccessReason &);
    static uint64_t allocation_size(const SecretAccessReason &);
};
struct FfiConverterSendAmount {
    static SendAmount lift(RustBuffer);
    static RustBuffer lower(const SendAmount &);
    static SendAmount read(RustStream &);
    static void write(RustStream &, const SendAmount &);
    static uint64_t allocation_size(const SendAmount &);
};
struct FfiConverterSendExpiration {
    static SendExpiration lift(RustBuffer);
    static RustBuffer lower(const SendExpiration &);
    static SendExpiration read(RustStream &);
    static void write(RustStream &, const SendExpiration &);
    static uint64_t allocation_size(const SendExpiration &);
};
struct FfiConverterSendMessageBody {
    static SendMessageBody lift(RustBuffer);
    static RustBuffer lower(const SendMessageBody &);
    static SendMessageBody read(RustStream &);
    static void write(RustStream &, const SendMessageBody &);
    static uint64_t allocation_size(const SendMessageBody &);
};
struct FfiConverterSendPhase {
    static SendPhase lift(RustBuffer);
    static RustBuffer lower(const SendPhase &);
    static SendPhase read(RustStream &);
    static void write(RustStream &, const SendPhase &);
    static uint64_t allocation_size(const SendPhase &);
};

struct FfiConverterStatuslessHostError {
    static std::shared_ptr<StatuslessHostError> lift(RustBuffer buf);
    static RustBuffer lower(const StatuslessHostError &);
    static std::shared_ptr<StatuslessHostError> read(RustStream &stream);
    static void write(RustStream &stream, const StatuslessHostError &);
    static uint64_t allocation_size(const StatuslessHostError &);
};
struct FfiConverterStatuslessHostErrorKind {
    static StatuslessHostErrorKind lift(RustBuffer);
    static RustBuffer lower(const StatuslessHostErrorKind &);
    static StatuslessHostErrorKind read(RustStream &);
    static void write(RustStream &, const StatuslessHostErrorKind &);
    static uint64_t allocation_size(const StatuslessHostErrorKind &);
};

struct FfiConverterTonAddressError {
    static std::shared_ptr<TonAddressError> lift(RustBuffer buf);
    static RustBuffer lower(const TonAddressError &);
    static std::shared_ptr<TonAddressError> read(RustStream &stream);
    static void write(RustStream &stream, const TonAddressError &);
    static uint64_t allocation_size(const TonAddressError &);
};
struct FfiConverterTonAddressFormat {
    static TonAddressFormat lift(RustBuffer);
    static RustBuffer lower(const TonAddressFormat &);
    static TonAddressFormat read(RustStream &);
    static void write(RustStream &, const TonAddressFormat &);
    static uint64_t allocation_size(const TonAddressFormat &);
};
struct FfiConverterTonConnectConnectErrorCode {
    static TonConnectConnectErrorCode lift(RustBuffer);
    static RustBuffer lower(const TonConnectConnectErrorCode &);
    static TonConnectConnectErrorCode read(RustStream &);
    static void write(RustStream &, const TonConnectConnectErrorCode &);
    static uint64_t allocation_size(const TonConnectConnectErrorCode &);
};
struct FfiConverterTonConnectDevicePlatform {
    static TonConnectDevicePlatform lift(RustBuffer);
    static RustBuffer lower(const TonConnectDevicePlatform &);
    static TonConnectDevicePlatform read(RustStream &);
    static void write(RustStream &, const TonConnectDevicePlatform &);
    static uint64_t allocation_size(const TonConnectDevicePlatform &);
};
struct FfiConverterTonConnectIncomingRequest {
    static TonConnectIncomingRequest lift(RustBuffer);
    static RustBuffer lower(const TonConnectIncomingRequest &);
    static TonConnectIncomingRequest read(RustStream &);
    static void write(RustStream &, const TonConnectIncomingRequest &);
    static uint64_t allocation_size(const TonConnectIncomingRequest &);
};
struct FfiConverterTonConnectRpcErrorCode {
    static TonConnectRpcErrorCode lift(RustBuffer);
    static RustBuffer lower(const TonConnectRpcErrorCode &);
    static TonConnectRpcErrorCode read(RustStream &);
    static void write(RustStream &, const TonConnectRpcErrorCode &);
    static uint64_t allocation_size(const TonConnectRpcErrorCode &);
};

struct FfiConverterTonConnectSessionError {
    static std::shared_ptr<TonConnectSessionError> lift(RustBuffer buf);
    static RustBuffer lower(const TonConnectSessionError &);
    static std::shared_ptr<TonConnectSessionError> read(RustStream &stream);
    static void write(RustStream &stream, const TonConnectSessionError &);
    static uint64_t allocation_size(const TonConnectSessionError &);
};
struct FfiConverterTonConnectSessionPhase {
    static TonConnectSessionPhase lift(RustBuffer);
    static RustBuffer lower(const TonConnectSessionPhase &);
    static TonConnectSessionPhase read(RustStream &);
    static void write(RustStream &, const TonConnectSessionPhase &);
    static uint64_t allocation_size(const TonConnectSessionPhase &);
};
struct FfiConverterTonConnectSignDataPayload {
    static TonConnectSignDataPayload lift(RustBuffer);
    static RustBuffer lower(const TonConnectSignDataPayload &);
    static TonConnectSignDataPayload read(RustStream &);
    static void write(RustStream &, const TonConnectSignDataPayload &);
    static uint64_t allocation_size(const TonConnectSignDataPayload &);
};
struct FfiConverterTonTransferAsset {
    static TonTransferAsset lift(RustBuffer);
    static RustBuffer lower(const TonTransferAsset &);
    static TonTransferAsset read(RustStream &);
    static void write(RustStream &, const TonTransferAsset &);
    static uint64_t allocation_size(const TonTransferAsset &);
};

struct FfiConverterTonTransferLinkError {
    static std::shared_ptr<TonTransferLinkError> lift(RustBuffer buf);
    static RustBuffer lower(const TonTransferLinkError &);
    static std::shared_ptr<TonTransferLinkError> read(RustStream &stream);
    static void write(RustStream &stream, const TonTransferLinkError &);
    static uint64_t allocation_size(const TonTransferLinkError &);
};
struct FfiConverterTonTransferPayload {
    static TonTransferPayload lift(RustBuffer);
    static RustBuffer lower(const TonTransferPayload &);
    static TonTransferPayload read(RustStream &);
    static void write(RustStream &, const TonTransferPayload &);
    static uint64_t allocation_size(const TonTransferPayload &);
};

struct FfiConverterWalletClientError {
    static std::shared_ptr<WalletClientError> lift(RustBuffer buf);
    static RustBuffer lower(const WalletClientError &);
    static std::shared_ptr<WalletClientError> read(RustStream &stream);
    static void write(RustStream &stream, const WalletClientError &);
    static uint64_t allocation_size(const WalletClientError &);
};

struct FfiConverterWalletLifecycleError {
    static std::shared_ptr<WalletLifecycleError> lift(RustBuffer buf);
    static RustBuffer lower(const WalletLifecycleError &);
    static std::shared_ptr<WalletLifecycleError> read(RustStream &stream);
    static void write(RustStream &stream, const WalletLifecycleError &);
    static uint64_t allocation_size(const WalletLifecycleError &);
};
struct FfiConverterWalletOperationOutcome {
    static WalletOperationOutcome lift(RustBuffer);
    static RustBuffer lower(const WalletOperationOutcome &);
    static WalletOperationOutcome read(RustStream &);
    static void write(RustStream &, const WalletOperationOutcome &);
    static uint64_t allocation_size(const WalletOperationOutcome &);
};
struct FfiConverterOptionalUInt16 {
    static std::optional<uint16_t> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<uint16_t>& val);
    static std::optional<uint16_t> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<uint16_t>& value);
    static uint64_t allocation_size(const std::optional<uint16_t> &val);
};
struct FfiConverterOptionalInt32 {
    static std::optional<int32_t> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<int32_t>& val);
    static std::optional<int32_t> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<int32_t>& value);
    static uint64_t allocation_size(const std::optional<int32_t> &val);
};
struct FfiConverterOptionalUInt64 {
    static std::optional<uint64_t> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<uint64_t>& val);
    static std::optional<uint64_t> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<uint64_t>& value);
    static uint64_t allocation_size(const std::optional<uint64_t> &val);
};
struct FfiConverterOptionalInt64 {
    static std::optional<int64_t> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<int64_t>& val);
    static std::optional<int64_t> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<int64_t>& value);
    static uint64_t allocation_size(const std::optional<int64_t> &val);
};
struct FfiConverterOptionalBool {
    static std::optional<bool> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<bool>& val);
    static std::optional<bool> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<bool>& value);
    static uint64_t allocation_size(const std::optional<bool> &val);
};
struct FfiConverterOptionalString {
    static std::optional<std::string> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<std::string>& val);
    static std::optional<std::string> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<std::string>& value);
    static uint64_t allocation_size(const std::optional<std::string> &val);
};
struct FfiConverterOptionalBytes {
    static std::optional<std::vector<uint8_t>> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<std::vector<uint8_t>>& val);
    static std::optional<std::vector<uint8_t>> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<std::vector<uint8_t>>& value);
    static uint64_t allocation_size(const std::optional<std::vector<uint8_t>> &val);
};
struct FfiConverterOptionalTypeAccountSnapshot {
    static std::optional<AccountSnapshot> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<AccountSnapshot>& val);
    static std::optional<AccountSnapshot> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<AccountSnapshot>& value);
    static uint64_t allocation_size(const std::optional<AccountSnapshot> &val);
};
struct FfiConverterOptionalTypeActivityCursor {
    static std::optional<ActivityCursor> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<ActivityCursor>& val);
    static std::optional<ActivityCursor> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<ActivityCursor>& value);
    static uint64_t allocation_size(const std::optional<ActivityCursor> &val);
};
struct FfiConverterOptionalTypeDomainError {
    static std::optional<DomainError> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<DomainError>& val);
    static std::optional<DomainError> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<DomainError>& value);
    static uint64_t allocation_size(const std::optional<DomainError> &val);
};
struct FfiConverterOptionalTypeJournalRecord {
    static std::optional<JournalRecord> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<JournalRecord>& val);
    static std::optional<JournalRecord> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<JournalRecord>& value);
    static uint64_t allocation_size(const std::optional<JournalRecord> &val);
};
struct FfiConverterOptionalTypeNftCollectionDescriptor {
    static std::optional<NftCollectionDescriptor> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<NftCollectionDescriptor>& val);
    static std::optional<NftCollectionDescriptor> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<NftCollectionDescriptor>& value);
    static uint64_t allocation_size(const std::optional<NftCollectionDescriptor> &val);
};
struct FfiConverterOptionalTypeProtectedSecretRef {
    static std::optional<ProtectedSecretRef> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<ProtectedSecretRef>& val);
    static std::optional<ProtectedSecretRef> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<ProtectedSecretRef>& value);
    static uint64_t allocation_size(const std::optional<ProtectedSecretRef> &val);
};
struct FfiConverterOptionalTypeResolutionInfo {
    static std::optional<ResolutionInfo> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<ResolutionInfo>& val);
    static std::optional<ResolutionInfo> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<ResolutionInfo>& value);
    static uint64_t allocation_size(const std::optional<ResolutionInfo> &val);
};
struct FfiConverterOptionalTypeTonConnectConnectPrompt {
    static std::optional<TonConnectConnectPrompt> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<TonConnectConnectPrompt>& val);
    static std::optional<TonConnectConnectPrompt> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<TonConnectConnectPrompt>& value);
    static uint64_t allocation_size(const std::optional<TonConnectConnectPrompt> &val);
};
struct FfiConverterOptionalTypeTonConnectPreparedPost {
    static std::optional<TonConnectPreparedPost> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<TonConnectPreparedPost>& val);
    static std::optional<TonConnectPreparedPost> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<TonConnectPreparedPost>& value);
    static uint64_t allocation_size(const std::optional<TonConnectPreparedPost> &val);
};
struct FfiConverterOptionalTypeTonConnectProofReply {
    static std::optional<TonConnectProofReply> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<TonConnectProofReply>& val);
    static std::optional<TonConnectProofReply> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<TonConnectProofReply>& value);
    static uint64_t allocation_size(const std::optional<TonConnectProofReply> &val);
};
struct FfiConverterOptionalHttpHostErrorKind {
    static std::optional<HttpHostErrorKind> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<HttpHostErrorKind>& val);
    static std::optional<HttpHostErrorKind> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<HttpHostErrorKind>& value);
    static uint64_t allocation_size(const std::optional<HttpHostErrorKind> &val);
};
struct FfiConverterOptionalPendingReason {
    static std::optional<PendingReason> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<PendingReason>& val);
    static std::optional<PendingReason> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<PendingReason>& value);
    static uint64_t allocation_size(const std::optional<PendingReason> &val);
};
struct FfiConverterOptionalTypeBase64Hash {
    static std::optional<Base64Hash> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<Base64Hash>& val);
    static std::optional<Base64Hash> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<Base64Hash>& value);
    static uint64_t allocation_size(const std::optional<Base64Hash> &val);
};
struct FfiConverterOptionalTypeBoc {
    static std::optional<Boc> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<Boc>& val);
    static std::optional<Boc> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<Boc>& value);
    static uint64_t allocation_size(const std::optional<Boc> &val);
};
struct FfiConverterOptionalTypeNonEmptyString {
    static std::optional<NonEmptyString> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<NonEmptyString>& val);
    static std::optional<NonEmptyString> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<NonEmptyString>& value);
    static uint64_t allocation_size(const std::optional<NonEmptyString> &val);
};
struct FfiConverterOptionalTypeTonAddressString {
    static std::optional<TonAddressString> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<TonAddressString>& val);
    static std::optional<TonAddressString> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<TonAddressString>& value);
    static uint64_t allocation_size(const std::optional<TonAddressString> &val);
};
struct FfiConverterOptionalTypeUnsignedDecimalString {
    static std::optional<UnsignedDecimalString> lift(RustBuffer buf);
    static RustBuffer lower(const std::optional<UnsignedDecimalString>& val);
    static std::optional<UnsignedDecimalString> read(RustStream &stream);
    static void write(RustStream &stream, const std::optional<UnsignedDecimalString>& value);
    static uint64_t allocation_size(const std::optional<UnsignedDecimalString> &val);
};

struct FfiConverterSequenceString {
    static std::vector<std::string> lift(RustBuffer);
    static RustBuffer lower(const std::vector<std::string> &);
    static std::vector<std::string> read(RustStream &);
    static void write(RustStream &, const std::vector<std::string> &);
    static uint64_t allocation_size(const std::vector<std::string> &);
};

struct FfiConverterSequenceTypeActivityItem {
    static std::vector<ActivityItem> lift(RustBuffer);
    static RustBuffer lower(const std::vector<ActivityItem> &);
    static std::vector<ActivityItem> read(RustStream &);
    static void write(RustStream &, const std::vector<ActivityItem> &);
    static uint64_t allocation_size(const std::vector<ActivityItem> &);
};

struct FfiConverterSequenceTypeHttpHeader {
    static std::vector<HttpHeader> lift(RustBuffer);
    static RustBuffer lower(const std::vector<HttpHeader> &);
    static std::vector<HttpHeader> read(RustStream &);
    static void write(RustStream &, const std::vector<HttpHeader> &);
    static uint64_t allocation_size(const std::vector<HttpHeader> &);
};

struct FfiConverterSequenceTypeNftItem {
    static std::vector<NftItem> lift(RustBuffer);
    static RustBuffer lower(const std::vector<NftItem> &);
    static std::vector<NftItem> read(RustStream &);
    static void write(RustStream &, const std::vector<NftItem> &);
    static uint64_t allocation_size(const std::vector<NftItem> &);
};

struct FfiConverterSequenceTypeSendEmulationAction {
    static std::vector<SendEmulationAction> lift(RustBuffer);
    static RustBuffer lower(const std::vector<SendEmulationAction> &);
    static std::vector<SendEmulationAction> read(RustStream &);
    static void write(RustStream &, const std::vector<SendEmulationAction> &);
    static uint64_t allocation_size(const std::vector<SendEmulationAction> &);
};

struct FfiConverterSequenceTypeSendEmulationTransaction {
    static std::vector<SendEmulationTransaction> lift(RustBuffer);
    static RustBuffer lower(const std::vector<SendEmulationTransaction> &);
    static std::vector<SendEmulationTransaction> read(RustStream &);
    static void write(RustStream &, const std::vector<SendEmulationTransaction> &);
    static uint64_t allocation_size(const std::vector<SendEmulationTransaction> &);
};

struct FfiConverterSequenceTypeSendMessage {
    static std::vector<SendMessage> lift(RustBuffer);
    static RustBuffer lower(const std::vector<SendMessage> &);
    static std::vector<SendMessage> read(RustStream &);
    static void write(RustStream &, const std::vector<SendMessage> &);
    static uint64_t allocation_size(const std::vector<SendMessage> &);
};

struct FfiConverterSequenceMnemonicScheme {
    static std::vector<MnemonicScheme> lift(RustBuffer);
    static RustBuffer lower(const std::vector<MnemonicScheme> &);
    static std::vector<MnemonicScheme> read(RustStream &);
    static void write(RustStream &, const std::vector<MnemonicScheme> &);
    static uint64_t allocation_size(const std::vector<MnemonicScheme> &);
};

struct FfiConverterSequenceTonConnectIncomingRequest {
    static std::vector<TonConnectIncomingRequest> lift(RustBuffer);
    static RustBuffer lower(const std::vector<TonConnectIncomingRequest> &);
    static std::vector<TonConnectIncomingRequest> read(RustStream &);
    static void write(RustStream &, const std::vector<TonConnectIncomingRequest> &);
    static uint64_t allocation_size(const std::vector<TonConnectIncomingRequest> &);
};

struct FfiConverterSequenceTypeBase64Hash {
    static std::vector<Base64Hash> lift(RustBuffer);
    static RustBuffer lower(const std::vector<Base64Hash> &);
    static std::vector<Base64Hash> read(RustStream &);
    static void write(RustStream &, const std::vector<Base64Hash> &);
    static uint64_t allocation_size(const std::vector<Base64Hash> &);
};

struct FfiConverterSequenceTypeTonAddressString {
    static std::vector<TonAddressString> lift(RustBuffer);
    static RustBuffer lower(const std::vector<TonAddressString> &);
    static std::vector<TonAddressString> read(RustStream &);
    static void write(RustStream &, const std::vector<TonAddressString> &);
    static uint64_t allocation_size(const std::vector<TonAddressString> &);
};

struct FfiConverterMapStringString {
    static std::unordered_map<std::string, std::string> lift(RustBuffer);
    static RustBuffer lower(const std::unordered_map<std::string, std::string> &);
    static std::unordered_map<std::string, std::string> read(RustStream &);
    static void write(RustStream &, const std::unordered_map<std::string, std::string> &);
    static uint64_t allocation_size(const std::unordered_map<std::string, std::string> &);
};
typedef struct FfiConverterString FfiConverterTypeBase64Hash;
typedef struct FfiConverterString FfiConverterTypeBoc;
typedef struct FfiConverterString FfiConverterTypeNonEmptyString;
typedef struct FfiConverterString FfiConverterTypeTonAddressString;
typedef struct FfiConverterString FfiConverterTypeUnsignedDecimalString;
} // namespace uniffi

/**
 * Parses the strict baseline `ton://transfer/` format without reading chain or clock state.
 *
 * Query names are case-sensitive. Percent escapes are decoded exactly once,
 * and literal `+` characters remain plus signs instead of becoming spaces.
 */
ParsedTonTransferLink parse_ton_transfer_link(const std::string &value);
/**
 * Parses and validates a dApp manifest with the Rust protocol implementation.
 */
TonConnectManifest parse_ton_connect_manifest(const std::string &json);
/**
 * Creates a fresh encrypted session from a full TON Connect link.
 */
std::shared_ptr<TonConnectSession> ton_connect_session_from_link(const std::string &link, const TonConnectSessionConfig &config);
/**
 * Restores a secret-bearing session without generating new crypto keys.
 */
std::shared_ptr<TonConnectSession> ton_connect_session_restore(const std::string &persisted, const TonConnectSessionConfig &config);
/**
 * Converts a TON address to the requested canonical representation.
 *
 * Raw output is lowercase `workchain:64-hex`. User-friendly output is
 * unpadded URL-safe TEP-2 Base64. Requested friendly flags replace any flags
 * carried by the input.
 */
std::string convert_ton_address(const std::string &value, TonAddressFormat format);
/**
 * Reports whether `value` is a valid raw or user-friendly TON address.
 */
bool is_valid_ton_address(const std::string &value);
/**
 * Parses a raw or user-friendly TON address without changing its account identity.
 *
 * Friendly input accepts the standard and URL-safe Base64 alphabets and returns
 * the flags protected by its checksum in [`TonAddressFormat::UserFriendly`].
 */
TonAddressInfo parse_ton_address(const std::string &value);
/**
 * Returns the English word list accepted by recovery-phrase validation.
 *
 * This is the canonical BIP-39 English list, so the source order is also the
 * BIP-39 index order. Both halves of a rotation mnemonic draw from it.
 */
std::vector<std::string> mnemonic_wordlist();
/**
 * Reports every scheme under which the entered recovery words validate.
 *
 * Pass the words exactly as the user recorded them, one word per element -
 * the same value an `ImportWalletRequest` would carry. Each word is trimmed
 * and lowercased before validation, exactly like wallet import.
 *
 * The result lists all matching schemes in the fixed order
 * [`Rotation`](MnemonicScheme::Rotation), [`Ton`](MnemonicScheme::Ton),
 * [`Bip39`](MnemonicScheme::Bip39); the checks are independent, so one
 * phrase can match more than one scheme. An empty result means the words
 * validate under no scheme the engine knows.
 *
 * Wallet import succeeds exactly when the result contains
 * [`MnemonicScheme::Rotation`]. A result without it explains why import
 * reports `InvalidRecoveryPhrase`; the user-facing wording of that
 * explanation belongs to the application.
 *
 * The function derives no key material and logs nothing. Password-protected
 * TON mnemonics are not detectable without the password and report no match.
 */
std::vector<MnemonicScheme> detect_mnemonic_schemes(const std::vector<std::string> &words);
/**
 * Returns the 32-byte anchor public key of a 12- or 24-word Rotation mnemonic.
 *
 * The anchor key is independent of network and does not change on key rotation.
 * Both halves of a 24-word phrase must validate. No storage or network is used.
 */
std::vector<uint8_t> rotation_mnemonic_public_key(const std::string &phrase);
} // namespace wallet_engine