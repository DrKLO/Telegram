#include <jni.h>
#include "wallet_engine.hpp"
#include "GramWalletAdapter.h"
#include "WalletJournal.h"
#include <functional>
#include <algorithm>

#include <charconv>
#include <limits>
#include <mutex>
#include <unordered_map>

namespace {
namespace we = wallet_engine;

we::NftTransferIntent nft_transfer_intent(JNIEnv *env, jbyteArray comment_bytes, const std::string &recipient, const std::string &nft,
                                         jlong attached, jlong forward) {
    if (!we::is_valid_ton_address(recipient) || !we::is_valid_ton_address(nft))
        throw std::invalid_argument("Valid recipient and NFT addresses are required");
    if (forward < 0 || attached <= forward)
        throw std::invalid_argument("NFT attached amount must exceed its forward amount");
    we::NftTransferPayload payload = we::NftTransferPayload::kEmpty{};
    if (comment_bytes) {
        const auto length = env->GetArrayLength(comment_bytes);
        if (length > 960) throw std::invalid_argument("Comment exceeds 960 UTF-8 bytes");
        if (length > 0) {
            std::string comment(static_cast<size_t>(length), '\0');
            env->GetByteArrayRegion(comment_bytes, 0, length, reinterpret_cast<jbyte *>(comment.data()));
            if (env->ExceptionCheck()) throw std::runtime_error("Could not read NFT comment");
            payload = we::NftTransferPayload::kComment{comment};
        }
    }
    return {nft, recipient,
        we::NftTransferFunding::kExact{std::to_string(attached), std::to_string(forward)},
        payload, we::SendExpiration::kEngineDefault{}};
}

struct SensitiveString {
    std::string value;

    explicit SensitiveString(size_t size) : value(size, '\0') {}
    SensitiveString(const SensitiveString &) = delete;
    SensitiveString &operator=(const SensitiveString &) = delete;

    ~SensitiveString() {
        // Volatile writes keep this cleanup from being optimized away.
        volatile char *data = value.data();
        for (size_t i = 0; i < value.size(); ++i) data[i] = 0;
    }
};

// This host is scoped to a user-requested local operation. Nothing is persisted;
// the mnemonic is released when the synchronous lifecycle calls finish.
class EphemeralWalletHost final : public we::WalletPlatformHost {
    std::unique_ptr<SensitiveString> secret_;
    std::string secret_ref_;
    const we::SecretAccessReason reason_;

    [[noreturn]] static void unavailable() {
        we::protected_secret_host_error::Failed error;
        error.kind = we::ProtectedSecretHostErrorKind::kUnavailable;
        error.diagnostic = "No recovery phrase authorized for this operation";
        throw error;
    }

public:
    explicit EphemeralWalletHost(we::SecretAccessReason reason) : reason_(reason) {}

    void store_protected_secret(const we::ProtectedSecretStore &request) override {
        if (secret_) unavailable();
        auto secret = std::make_unique<SensitiveString>(request.bytes.size());
        std::copy(request.bytes.begin(), request.bytes.end(), secret->value.begin());
        secret_ref_ = request.secret_ref.value;
        secret_ = std::move(secret);
    }

    std::vector<uint8_t> read_protected_secret(const we::ProtectedSecretRead &request) override {
        if (!secret_ || request.secret_ref.value != secret_ref_
                || request.reason != reason_) unavailable();
        return {secret_->value.begin(), secret_->value.end()};
    }

    void delete_protected_secret(const we::ProtectedSecretRef &) override { unavailable(); }
    std::optional<we::JournalRecord> load_journal(const we::JournalKey &) override {
        throw std::logic_error("Local wallet operation does not use a journal");
    }
    we::JournalCompareExchangeResult compare_exchange_journal(const we::JournalCompareExchange &) override {
        throw std::logic_error("Local wallet operation does not use a journal");
    }
};

struct ImportProofRequest {
    we::ImportWalletRequest value{"import-proof", we::Network::kMainnet, {}};
    explicit ImportProofRequest(const std::string &phrase) {
        value.recovery_words.reserve(24);
        const char *whitespace = " \t\r\n\f\v";
        for (size_t start = phrase.find_first_not_of(whitespace); start != std::string::npos;) {
            const auto end = phrase.find_first_of(whitespace, start);
            value.recovery_words.emplace_back(phrase, start,
                end == std::string::npos ? std::string::npos : end - start);
            start = end == std::string::npos ? end : phrase.find_first_not_of(whitespace, end);
        }
    }
    ~ImportProofRequest() {
        for (auto &word : value.recovery_words) {
            volatile char *data = word.data();
            for (size_t i = 0; i < word.size(); ++i) data[i] = 0;
        }
    }
};

class OfflineWalletTransport final : public we::WalletStatuslessHost {
public:
    std::vector<uint8_t> execute_statusless(const we::HttpRequest &) override {
        throw std::logic_error("Local wallet operation cannot access the network");
    }
    void cancel_statusless(const we::HttpRequestId &) override {}
};

// Host callbacks may run on a Rust worker as well as a Java caller thread.
class JavaEnv {
public:
    JNIEnv *env = nullptr;
    explicit JavaEnv(JavaVM *vm) : vm_(vm) {
        const jint status = vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
#ifdef __ANDROID__
            if (vm_->AttachCurrentThread(&env, nullptr) != JNI_OK)
#else
            if (vm_->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK)
#endif
                throw std::runtime_error("Could not attach wallet callback thread");
            attached_ = true;
        } else if (status != JNI_OK) {
            throw std::runtime_error("Could not access wallet callback thread");
        }
        if (env->PushLocalFrame(16) != JNI_OK) {
            if (attached_) vm_->DetachCurrentThread();
            throw std::runtime_error("Could not allocate wallet callback references");
        }
    }
    ~JavaEnv() {
        env->PopLocalFrame(nullptr);
        if (attached_) vm_->DetachCurrentThread();
    }
private:
    JavaVM *vm_;
    bool attached_ = false;
};

class WalletHosts final : public we::WalletStatuslessHost, public we::WalletPlatformHost {
public:
    std::weak_ptr<we::WalletClient> client;
    const SensitiveString *secret = nullptr;
    std::string secret_ref;
    we::SecretAccessReason secret_reason = we::SecretAccessReason::kSignTransfer;
    std::function<void(const we::SendSnapshot &)> on_state;

    void report_state() {
        if (on_state) {
            if (auto value = client.lock()) on_state(value->snapshot().send);
        }
    }

    WalletHosts(JNIEnv *env, jobject transport, std::string journal_directory)
        : journal_(std::move(journal_directory)) {
        if (!transport || env->GetJavaVM(&vm_) != JNI_OK)
            throw std::invalid_argument("Wallet transport is required");
        jclass cls = env->GetObjectClass(transport);
        execute_ = env->GetMethodID(cls, "execute", "(JLjava/lang/String;Z[BJ)[B");
        cancel_ = env->GetMethodID(cls, "cancel", "(J)V");
        env->DeleteLocalRef(cls);
        jclass error_cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$HttpTransport$HostException");
        if (!error_cls || !execute_ || !cancel_)
            throw std::runtime_error("Could not resolve wallet transport callbacks");
        error_kind_ = env->GetFieldID(error_cls, "kind", "I");
        if (!error_kind_) {
            env->DeleteLocalRef(error_cls);
            throw std::runtime_error("Could not resolve wallet transport error");
        }
        error_class_ = static_cast<jclass>(env->NewGlobalRef(error_cls));
        env->DeleteLocalRef(error_cls);
        if (!error_class_) throw std::bad_alloc();
        transport_ = env->NewGlobalRef(transport);
        if (!transport_) {
            env->DeleteGlobalRef(error_class_);
            throw std::bad_alloc();
        }
    }

    ~WalletHosts() override {
        try {
            JavaEnv java(vm_);
            java.env->DeleteGlobalRef(transport_);
            java.env->DeleteGlobalRef(error_class_);
        } catch (...) { }
    }

    std::vector<uint8_t> execute_statusless(const we::HttpRequest &request) override {
        report_state();
        JavaEnv java(vm_);
        JNIEnv *env = java.env;
        jstring url = env->NewStringUTF(request.url.c_str());
        if (!url) check_exception(env);
        jbyteArray body = env->NewByteArray(static_cast<jsize>(request.body.size()));
        if (!body) check_exception(env);
        env->SetByteArrayRegion(body, 0, static_cast<jsize>(request.body.size()),
                               reinterpret_cast<const jbyte *>(request.body.data()));
        check_exception(env);
        auto response = static_cast<jbyteArray>(env->CallObjectMethod(transport_, execute_,
            static_cast<jlong>(request.id.value), url,
            static_cast<jboolean>(request.method == we::HttpMethod::kPost), body,
            static_cast<jlong>(request.timeout_ms)));
        check_exception(env);
        if (!response) throw transport_error(7);
        std::vector<uint8_t> result(env->GetArrayLength(response));
        env->GetByteArrayRegion(response, 0, static_cast<jsize>(result.size()),
                               reinterpret_cast<jbyte *>(result.data()));
        check_exception(env);
        return result;
    }

    void cancel_statusless(const we::HttpRequestId &id) override {
        JavaEnv java(vm_);
        java.env->CallVoidMethod(transport_, cancel_, static_cast<jlong>(id.value));
        // Cancellation is best-effort and must not leave a pending JNI exception.
        if (java.env->ExceptionCheck()) java.env->ExceptionClear();
    }

    std::vector<uint8_t> read_protected_secret(const we::ProtectedSecretRead &request) override {
        report_state();
        if (!secret || request.secret_ref.value != secret_ref || request.reason != secret_reason)
            throw secret_unavailable();
        return {secret->value.begin(), secret->value.end()};
    }

    void store_protected_secret(const we::ProtectedSecretStore &) override {
        throw secret_unavailable();
    }

    void delete_protected_secret(const we::ProtectedSecretRef &) override {
        throw secret_unavailable();
    }

    std::optional<we::JournalRecord> load_journal(const we::JournalKey &key) override {
        report_state();
        try { return journal_.access(key).current; }
        catch (...) { throw journal_unavailable(); }
    }

    we::JournalCompareExchangeResult compare_exchange_journal(const we::JournalCompareExchange &mutation) override {
        report_state();
        try { return journal_.access(mutation.key, &mutation); }
        catch (...) { throw journal_unavailable(); }
    }

private:
    WalletJournal journal_;
    JavaVM *vm_ = nullptr;
    jobject transport_ = nullptr;
    jclass error_class_ = nullptr;
    jmethodID execute_ = nullptr;
    jmethodID cancel_ = nullptr;
    jfieldID error_kind_ = nullptr;

    static we::statusless_host_error::Failed transport_error(jint kind) {
        we::statusless_host_error::Failed error;
        error.kind = static_cast<we::StatuslessHostErrorKind>(kind >= 1 && kind <= 7 ? kind : 7);
        error.diagnostic = kind == 2 ? "Wallet provider request timed out"
            : kind == 6 ? "Wallet provider request was cancelled" : "Wallet provider request failed";
        return error;
    }

    void check_exception(JNIEnv *env) const {
        if (!env->ExceptionCheck()) return;
        jthrowable exception = env->ExceptionOccurred();
        env->ExceptionClear();
        jint kind = env->IsInstanceOf(exception, error_class_) ? env->GetIntField(exception, error_kind_) : 7;
        env->DeleteLocalRef(exception);
        throw transport_error(kind);
    }

    static we::protected_secret_host_error::Failed secret_unavailable() {
        we::protected_secret_host_error::Failed error;
        error.kind = we::ProtectedSecretHostErrorKind::kUnavailable;
        error.diagnostic = "No recovery phrase authorized for this operation";
        return error;
    }

    static we::journal_host_error::Failed journal_unavailable() {
        we::journal_host_error::Failed error;
        error.kind = we::JournalHostErrorKind::kUnavailable;
        error.diagnostic = "Wallet journal storage failed";
        return error;
    }
};

std::mutex clients_mutex;
struct Client {
    std::shared_ptr<we::WalletClient> wallet;
    std::shared_ptr<WalletHosts> hosts;
};
std::unordered_map<jlong, Client> clients;
jlong next_handle = 1;

Client get_client(jlong handle) {
    std::lock_guard<std::mutex> lock(clients_mutex);
    const auto it = clients.find(handle);
    if (it == clients.end()) throw std::runtime_error("NO_WALLET_ENGINE");
    return it->second;
}

std::string java_string(JNIEnv *env, jstring value) {
    if (!value) throw std::invalid_argument("Required string is missing");
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::bad_alloc();
    try {
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    } catch (...) {
        env->ReleaseStringUTFChars(value, chars);
        throw;
    }
}

we::SendIntent transfer_intent(
        const std::string &address, jlong amount,
        we::SendMessageBody body) {
    if (amount < 0) throw std::invalid_argument("Transfer amount must not be negative");
    const auto parsed = we::parse_ton_address(address);
    const auto friendly = std::get_if<we::TonAddressFormat::kUserFriendly>(&parsed.format.get_variant());
    if (friendly != nullptr && friendly->testnet) {
        throw std::invalid_argument("Testnet-only recipient is not allowed");
    }
    // Raw addresses carry no bounce flag; retain the non-bounceable default.
    const bool bounce = friendly != nullptr && friendly->bounceable;
    return {we::SendExpiration::kEngineDefault{}, {
        we::SendMessage{address, we::SendAmount::kExact{std::to_string(amount)},
            std::move(body), bounce, std::nullopt}
    }};
}

we::SendIntent transfer_intent(const std::string &address, jlong amount) {
    return transfer_intent(address, amount, we::SendMessageBody::kEmpty{});
}

// Local references are scoped to the native entry point that creates the result.
class Transaction {
    JNIEnv *env_;
    jclass cls_;
    jclass engine_cls_;
public:
    jobject object;
    Transaction(JNIEnv *env, const std::string &address, jlong amount) : env_(env) {
        engine_cls_ = env_->FindClass("org/telegram/ui/Wallet/WalletEngine2");
        if (!engine_cls_) throw std::runtime_error("Could not resolve wallet engine");
        auto factory = env_->GetStaticMethodID(engine_cls_, "createTransaction",
            "(Ljava/lang/String;J)Lorg/telegram/tgnet/tl/TL_wallet$walletTransaction;");
        if (!factory) throw std::runtime_error("Could not resolve wallet transaction factory");
        auto destination = env_->NewStringUTF(address.c_str());
        if (!destination) throw std::bad_alloc();
        object = env_->CallStaticObjectMethod(engine_cls_, factory, destination, amount);
        if (env_->ExceptionCheck() || !object) throw std::runtime_error("Could not create wallet transaction");
        env_->DeleteLocalRef(destination);
        cls_ = env_->GetObjectClass(object);
    }
    void text(const char *name, const std::string &value) {
        auto field = env_->GetFieldID(cls_, name, "Ljava/lang/String;");
        if (!field) throw std::runtime_error("Could not resolve transaction field");
        auto str = env_->NewStringUTF(value.c_str());
        if (!str) throw std::bad_alloc();
        env_->SetObjectField(object, field, str);
        env_->DeleteLocalRef(str);
    }
    void number(const char *name, jlong value) {
        auto field = env_->GetFieldID(cls_, name, "J");
        if (!field) throw std::runtime_error("Could not resolve transaction field");
        env_->SetLongField(object, field, value);
    }
    void boolean(const char *name, bool value) {
        auto field = env_->GetFieldID(cls_, name, "Z");
        if (!field) throw std::runtime_error("Could not resolve transaction field");
        env_->SetBooleanField(object, field, value);
    }
    void phase(we::SendPhase value) {
        auto setter = env_->GetStaticMethodID(engine_cls_, "setTransactionPhase",
            "(Lorg/telegram/tgnet/tl/TL_wallet$walletTransaction;I)V");
        if (!setter) throw std::runtime_error("Could not resolve transaction phase");
        env_->CallStaticVoidMethod(engine_cls_, setter, object, static_cast<jint>(value));
        if (env_->ExceptionCheck()) throw std::runtime_error("Could not convert transaction phase");
    }
};

jobject preview_transaction(JNIEnv *env, const we::SendPreview &preview,
                            const std::string &destination, jlong amount) {
    Transaction result(env, destination, amount);
    result.boolean("preview", true);
    const auto &decimal_fee = preview.emulation.wallet_fees_nanograms;
    jlong fee = 0;
    const auto parsed = std::from_chars(decimal_fee.data(), decimal_fee.data() + decimal_fee.size(), fee);
    if (parsed.ec != std::errc() || parsed.ptr != decimal_fee.data() + decimal_fee.size() || fee < 0)
        throw std::runtime_error("Estimated fee exceeds the supported range");
    result.number("fee", fee);
    result.text("traceFeesNanograms", preview.emulation.trace_fees_nanograms);
    result.number("validUntil", static_cast<jlong>(preview.valid_until));
    result.boolean("traceSucceeded", preview.emulation.trace_succeeded);
    result.boolean("traceIncomplete", preview.emulation.is_incomplete);
    result.phase(we::SendPhase::kIdle);
    return result.object;
}

class SecretScope {
    std::shared_ptr<WalletHosts> hosts_;
public:
    SecretScope(const Client &client, const SensitiveString &phrase, we::SecretAccessReason reason)
        : hosts_(client.hosts) {
        hosts_->secret = &phrase;
        hosts_->secret_reason = reason;
    }
    ~SecretScope() {
        hosts_->secret = nullptr;
        hosts_->secret_reason = we::SecretAccessReason::kSignTransfer;
    }
};

class SendScope {
    JNIEnv *env_;
    std::shared_ptr<WalletHosts> hosts_;
    jobject callback_ = nullptr;
public:
    SendScope(JNIEnv *env, const Client &client, const SensitiveString &phrase,
              jobject callback, const std::string &operation_id,
              we::SecretAccessReason reason = we::SecretAccessReason::kSignTransfer)
        : env_(env), hosts_(client.hosts) {
        JavaVM *vm = nullptr;
        env->GetJavaVM(&vm);
        auto cls = env->GetObjectClass(callback);
        auto state = env->GetMethodID(cls, "state", "(ILjava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
        if (!state) throw std::runtime_error("Could not resolve send callback");
        callback_ = env->NewGlobalRef(callback);
        if (!callback_) throw std::bad_alloc();
        try {
            hosts_->on_state = [vm, ref = callback_, state, operation_id](const we::SendSnapshot &snapshot) {
                if (!snapshot.operation_id || *snapshot.operation_id != operation_id) return;
                JavaEnv java(vm);
                auto error = snapshot.error_message ? java.env->NewStringUTF(snapshot.error_message->c_str()) : nullptr;
                auto transaction_hash = snapshot.resolution && snapshot.resolution->transaction_hash
                    ? java.env->NewStringUTF(snapshot.resolution->transaction_hash->c_str()) : nullptr;
                if (!java.env->ExceptionCheck())
                    java.env->CallVoidMethod(ref, state, static_cast<jint>(snapshot.phase), error, transaction_hash);
                // UI delivery cannot change whether a signed transfer was submitted.
                if (java.env->ExceptionCheck()) java.env->ExceptionClear();
            };
        } catch (...) {
            env->DeleteGlobalRef(callback_);
            throw;
        }
        hosts_->secret = &phrase;
        hosts_->secret_reason = reason;
    }
    ~SendScope() {
        hosts_->secret = nullptr;
        hosts_->secret_reason = we::SecretAccessReason::kSignTransfer;
        try { hosts_->report_state(); } catch (...) { }
        hosts_->on_state = nullptr;
        env_->DeleteGlobalRef(callback_);
    }
};

void throw_java(JNIEnv *env, const char *type, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type);
    if (cls) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}
} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_mnemonicWordlist(JNIEnv *env, jclass) {
    try {
        const auto words = we::mnemonic_wordlist();
        jclass string_class = env->FindClass("java/lang/String");
        if (!string_class) return nullptr;
        jobjectArray result = env->NewObjectArray(static_cast<jsize>(words.size()), string_class, nullptr);
        env->DeleteLocalRef(string_class);
        if (!result) return nullptr;
        for (size_t i = 0; i < words.size(); ++i) {
            jstring word = env->NewStringUTF(words[i].c_str());
            if (!word) return nullptr;
            env->SetObjectArrayElement(result, static_cast<jsize>(i), word);
            env->DeleteLocalRef(word);
            if (env->ExceptionCheck()) return nullptr;
        }
        return result;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not load mnemonic word list");
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not load mnemonic word list");
    }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeSecretPhraseToPublicKey(
        JNIEnv *env, jclass, jbyteArray secret_phrase) {
    if (!secret_phrase) {
        throw_java(env, "java/lang/IllegalArgumentException", "Recovery phrase is required");
        return nullptr;
    }
    try {
        const jsize length = env->GetArrayLength(secret_phrase);
        SensitiveString phrase(static_cast<size_t>(length));
        env->GetByteArrayRegion(secret_phrase, 0, length, reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        auto key = we::rotation_mnemonic_public_key(phrase.value);
        ImportProofRequest parsed(phrase.value);
        if (parsed.value.recovery_words.size() == 24) {
            SensitiveString signing_phrase(0);
            signing_phrase.value.reserve(phrase.value.size());
            for (size_t i = 12; i < 24; ++i) {
                if (i != 12) signing_phrase.value.push_back(' ');
                signing_phrase.value.append(parsed.value.recovery_words[i]);
            }
            key = we::rotation_mnemonic_public_key(signing_phrase.value);
        }
        if (key.size() != 32) throw std::runtime_error("Unexpected public key size");
        jbyteArray result = env->NewByteArray(32);
        if (result) env->SetByteArrayRegion(result, 0, 32, reinterpret_cast<const jbyte *>(key.data()));
        return result;
    } catch (const we::WalletLifecycleError &) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid Rotation recovery phrase");
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not derive wallet public key");
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not derive wallet public key");
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeSecretPhraseToAddress(
        JNIEnv *env, jclass, jbyteArray secret_phrase) {
    if (!secret_phrase) {
        throw_java(env, "java/lang/IllegalArgumentException", "Recovery phrase is required");
        return nullptr;
    }
    try {
        const jsize length = env->GetArrayLength(secret_phrase);
        SensitiveString phrase(static_cast<size_t>(length));
        env->GetByteArrayRegion(secret_phrase, 0, length, reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        // Reuse import's complete mnemonic validation and canonical address derivation.
        // The host retains the phrase only for this call and wipes it on destruction.
        ImportProofRequest request(phrase.value);
        auto host = std::make_shared<EphemeralWalletHost>(we::SecretAccessReason::kSignTonConnectProof);
        auto lifecycle = we::WalletLifecycle::init(host);
        auto descriptor = lifecycle->import_wallet(request.value);
        return env->NewStringUTF(descriptor.address.c_str());
    } catch (const we::wallet_lifecycle_error::InvalidRecoveryPhrase &) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid Rotation recovery phrase");
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not derive wallet address");
    } catch (...) {
        // Never include phrase contents in errors or logs.
        throw_java(env, "java/lang/IllegalStateException", "Could not derive wallet address");
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_isValidCellBoc(JNIEnv *env, jclass, jstring boc) {
    try {
        return boc && gram_adapter::is_valid_cell_boc(java_string(env, boc)) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeTonConnectCrypto(
        JNIEnv *env, jclass, jbyteArray phrase_bytes, jbyteArray parameters_bytes) {
    if (!phrase_bytes || !parameters_bytes) {
        throw_java(env, "java/lang/IllegalArgumentException", "Missing TON Connect approval data");
        return nullptr;
    }
    try {
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        SensitiveString parameters(static_cast<size_t>(env->GetArrayLength(parameters_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        env->GetByteArrayRegion(parameters_bytes, 0, static_cast<jsize>(parameters.value.size()),
                               reinterpret_cast<jbyte *>(parameters.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        SensitiveString output(0);
        output.value = gram_adapter::ton_connect_crypto(phrase.value, parameters.value);
        auto result = env->NewByteArray(static_cast<jsize>(output.value.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.value.size()),
                                           reinterpret_cast<const jbyte *>(output.value.data()));
        return result;
    } catch (const std::invalid_argument &error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not prepare TON Connect approval");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not prepare TON Connect approval");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeCreateImportProof(
        JNIEnv *env, jclass, jbyteArray phrase_bytes, jbyteArray domain_bytes,
        jbyteArray payload_bytes, jint timestamp, jboolean for_import) {
    if (!phrase_bytes || !domain_bytes || !payload_bytes || timestamp <= 0
            || env->GetArrayLength(phrase_bytes) == 0
            || env->GetArrayLength(domain_bytes) == 0
            || env->GetArrayLength(payload_bytes) == 0) {
        throw_java(env, "java/lang/IllegalArgumentException", "Recovery phrase and proof challenge are required");
        return nullptr;
    }
    try {
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        // Use standard UTF-8 bytes, not JNI's modified UTF-8, for signed fields.
        std::string domain(static_cast<size_t>(env->GetArrayLength(domain_bytes)), '\0');
        std::string payload(static_cast<size_t>(env->GetArrayLength(payload_bytes)), '\0');
        env->GetByteArrayRegion(domain_bytes, 0, static_cast<jsize>(domain.size()),
                               reinterpret_cast<jbyte *>(domain.data()));
        if (env->ExceptionCheck()) return nullptr;
        env->GetByteArrayRegion(payload_bytes, 0, static_cast<jsize>(payload.size()),
                               reinterpret_cast<jbyte *>(payload.data()));
        if (env->ExceptionCheck()) return nullptr;

        ImportProofRequest request(phrase.value);
        auto host = std::make_shared<EphemeralWalletHost>(we::SecretAccessReason::kSignTonConnectProof);
        auto lifecycle = we::WalletLifecycle::init(host);
        auto descriptor = lifecycle->import_wallet(request.value);
        if (for_import && request.value.recovery_words.size() == 24) {
            // The complete phrase was validated above. MTProto import verifies
            // with the submitted address-derivation key, not the rotated key.
            // Importing its first half retains the same address and authorizes
            // that key for this one proof. Wipe discarded signing words first.
            for (size_t i = 12; i < request.value.recovery_words.size(); ++i) {
                auto &word = request.value.recovery_words[i];
                volatile char *data = word.data();
                for (size_t j = 0; j < word.size(); ++j) data[j] = 0;
            }
            request.value.recovery_words.resize(12);
            host = std::make_shared<EphemeralWalletHost>(we::SecretAccessReason::kSignTonConnectProof);
            lifecycle = we::WalletLifecycle::init(host);
            descriptor = lifecycle->import_wallet(request.value);
        }
        auto proof = lifecycle->sign_ton_connect_proof({descriptor, domain,
            static_cast<uint64_t>(timestamp), payload});
        if (descriptor.public_key.size() != 32 || proof.signature.size() != 64)
            throw std::runtime_error("Unexpected ownership proof size");

        auto cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$ImportedWalletProof");
        if (!cls) return nullptr;
        auto constructor = env->GetMethodID(cls, "<init>", "([BI[B)V");
        if (!constructor) return nullptr;
        auto public_key = env->NewByteArray(32);
        if (!public_key) return nullptr;
        auto signature = env->NewByteArray(64);
        if (!signature) return nullptr;
        // MTProto import derives the address and verifies the proof with this
        // same anchor key. Other ownership proofs retain current-key signing.
        env->SetByteArrayRegion(public_key, 0, 32,
                               reinterpret_cast<const jbyte *>(descriptor.public_key.data()));
        if (env->ExceptionCheck()) return nullptr;
        env->SetByteArrayRegion(signature, 0, 64,
                               reinterpret_cast<const jbyte *>(proof.signature.data()));
        if (env->ExceptionCheck()) return nullptr;
        return env->NewObject(cls, constructor, public_key, timestamp, signature);
    } catch (const we::wallet_lifecycle_error::InvalidRecoveryPhrase &) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid Rotation recovery phrase");
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not create wallet ownership proof");
    } catch (...) {
        // Never include phrase contents in errors or logs.
        throw_java(env, "java/lang/IllegalStateException", "Could not create wallet ownership proof");
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeIsValidAddress(
        JNIEnv *env, jclass, jstring address) {
    if (!address) return JNI_FALSE;
    try {
        const auto value = java_string(env, address);
        // TON addresses are ASCII; reject JNI modified UTF-8 before passing it to Rust.
        for (unsigned char c : value) {
            if (c >= 0x80) return JNI_FALSE;
        }
        return we::is_valid_ton_address(value) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not validate TON address");
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not validate TON address");
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeTransactionAddress(
        JNIEnv *env, jclass, jstring address) {
    try {
        const auto friendly = we::convert_ton_address(
            java_string(env, address), we::TonAddressFormat::kUserFriendly{false, false});
        return env->NewStringUTF(friendly.c_str());
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not format transaction address");
    } catch (...) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid transaction address");
    }
    return nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeCreate(
        JNIEnv *env, jclass, jint current_account, jstring address, jbyteArray public_key, jobject transport, jstring journal_directory) {
    try {
        if (!address || !public_key || env->GetArrayLength(public_key) != 32) {
            throw std::invalid_argument("Wallet address and a 32-byte public key are required");
        }
        const char *chars = env->GetStringUTFChars(address, nullptr);
        if (!chars) return 0;
        std::string wallet_address;
        try {
            wallet_address = chars;
        } catch (...) {
            env->ReleaseStringUTFChars(address, chars);
            throw;
        }
        env->ReleaseStringUTFChars(address, chars);

        const auto raw_address = we::convert_ton_address(wallet_address, we::TonAddressFormat::kRaw{});
        std::vector<uint8_t> key(32);
        env->GetByteArrayRegion(public_key, 0, 32, reinterpret_cast<jbyte *>(key.data()));
        if (env->ExceptionCheck()) return 0;

        const auto record_id = "telegram-" + std::to_string(current_account) + ":" + raw_address;
        we::WalletClientConfig config{
            record_id,
            wallet_address,
            std::move(key),
            we::ProtectedSecretRef{record_id + ":mnemonic"},
            we::Network::kMainnet,
            60,
            30,
            we::ProviderConfig{"https://toncenter.com", std::nullopt, 15000}
        };
        auto hosts = std::make_shared<WalletHosts>(env, transport, java_string(env, journal_directory));
        hosts->secret_ref = record_id + ":mnemonic";
        auto client = we::WalletClient::new_statusless(config, hosts, hosts);
        hosts->client = client;

        std::lock_guard<std::mutex> lock(clients_mutex);
        if (next_handle == std::numeric_limits<jlong>::max()) {
            throw std::runtime_error("Wallet engine handles exhausted");
        }
        const jlong handle = next_handle++;
        clients.emplace(handle, Client{std::move(client), std::move(hosts)});
        return handle;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not initialize wallet engine");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not initialize wallet engine");
    }
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeBalance(JNIEnv *env, jclass, jlong handle) {
    try {
        std::shared_ptr<we::WalletClient> client;
        {
            std::lock_guard<std::mutex> lock(clients_mutex);
            auto it = clients.find(handle);
            if (it == clients.end()) throw std::runtime_error("Wallet engine is closed");
            client = it->second.wallet;
        }
        const auto update = client->refresh();
        const auto &snapshot = update.snapshot;
        if (snapshot.account_resource.phase != we::ResourcePhase::kReady || !snapshot.account) {
            throw std::runtime_error(snapshot.account_resource.error
                ? snapshot.account_resource.error->developer_message : "Could not load wallet balance");
        }
        const auto &decimal = snapshot.account->balance_nanograms;
        jlong balance = 0;
        const auto parsed = std::from_chars(decimal.data(), decimal.data() + decimal.size(), balance);
        if (parsed.ec != std::errc() || parsed.ptr != decimal.data() + decimal.size() || balance < 0)
            throw std::runtime_error("Wallet balance exceeds the supported range");
        return balance;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not load wallet balance");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not load wallet balance");
    }
    return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeEmulateSend(
        JNIEnv *env, jclass, jlong handle, jstring address, jlong amount,
        jbyteArray comment_bytes, jboolean comment_encrypted) {
    try {
        auto client = get_client(handle);
        auto destination = java_string(env, address);
        auto body = we::SendMessageBody(we::SendMessageBody::kEmpty{});
        if (comment_bytes) {
            std::string comment(static_cast<size_t>(env->GetArrayLength(comment_bytes)), '\0');
            env->GetByteArrayRegion(comment_bytes, 0, static_cast<jsize>(comment.size()),
                                   reinterpret_cast<jbyte *>(comment.data()));
            if (env->ExceptionCheck()) return nullptr;
            // For encrypted previews Java supplies a size-matched placeholder BOC.
            body = comment_encrypted
                ? we::SendMessageBody(we::SendMessageBody::kRawPayload{comment})
                : we::SendMessageBody(we::SendMessageBody::kComment{comment});
        }
        auto preview = client.wallet->preview_send({transfer_intent(destination, amount, std::move(body))});
        return preview_transaction(env, preview, destination, amount);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not preview transfer");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not preview transfer");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeEmulateRotateKey(
        JNIEnv *env, jclass, jlong handle, jstring address) {
    try {
        // No SecretScope: preview_key_rotation accepts public state only and
        // constructs zero owner/proof signatures before contacting Toncenter.
        const auto preview = get_client(handle).wallet->preview_key_rotation();
        return preview_transaction(env, preview, java_string(env, address), 0);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not emulate key rotation");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not emulate key rotation");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePrepareRotateKey(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jstring gasless_address) {
    try {
        auto client = get_client(handle);
        if (!phrase_bytes || env->GetArrayLength(phrase_bytes) == 0)
            throw std::invalid_argument("Recovery phrase is required");
        if (gasless_address && !we::is_valid_ton_address(java_string(env, gasless_address)))
            throw std::invalid_argument("Valid relayer address is required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        SecretScope scope(client, phrase, we::SecretAccessReason::kPrepareKeyRotation);
        const auto snapshot = client.wallet->refresh().snapshot;
        if (snapshot.account_resource.phase != we::ResourcePhase::kReady || !snapshot.account)
            throw std::runtime_error(snapshot.account_resource.error
                ? snapshot.account_resource.error->developer_message : "Could not load wallet account");
        constexpr uint64_t validity_seconds = 120;
        if (snapshot.account->sync_utime > std::numeric_limits<uint32_t>::max() - validity_seconds)
            throw std::runtime_error("Key rotation expiration exceeds the supported range");
        auto prepared = client.wallet->prepare_key_rotation({
            snapshot.account->sync_utime + validity_seconds, we::KeyRotationMessageKind::kExternal});
        SensitiveString replacement(0);
        replacement.value.swap(prepared.replacement_recovery_phrase.phrase);
        if (prepared.new_public_key.size() != 32)
            throw std::runtime_error("Unexpected signing public key size");
        const auto cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$PreparedRotation");
        if (!cls) return nullptr;
        const auto constructor = env->GetMethodID(cls, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;[B[B)V");
        if (!constructor) return nullptr;
        const auto external = env->NewStringUTF(prepared.external_boc.c_str());
        if (!external) return nullptr;
        const auto internal = gasless_address ? env->NewStringUTF(prepared.internal_boc.c_str()) : nullptr;
        if (env->ExceptionCheck()) return nullptr;
        const auto public_key = env->NewByteArray(32);
        if (!public_key) return nullptr;
        env->SetByteArrayRegion(public_key, 0, 32,
                               reinterpret_cast<const jbyte *>(prepared.new_public_key.data()));
        if (env->ExceptionCheck()) return nullptr;
        const auto bytes = env->NewByteArray(static_cast<jsize>(replacement.value.size()));
        if (!bytes) return nullptr;
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(replacement.value.size()),
                               reinterpret_cast<const jbyte *>(replacement.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        // The Java constructor clears its phrase bytes after creating the result.
        return env->NewObject(cls, constructor, external, internal, bytes, public_key);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not prepare key rotation");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not prepare key rotation");
    }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeDecryptRawComment(
        JNIEnv *env, jclass, jbyteArray phrase_bytes, jstring sender, jstring body) {
    if (!phrase_bytes || !sender || !body || env->GetArrayLength(phrase_bytes) == 0) {
        throw_java(env, "java/lang/IllegalArgumentException", "Recovery phrase, sender and message body are required");
        return nullptr;
    }
    try {
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        ImportProofRequest request(phrase.value);
        auto host = std::make_shared<EphemeralWalletHost>(we::SecretAccessReason::kDecryptComment);
        auto lifecycle = we::WalletLifecycle::init(host);
        auto descriptor = lifecycle->import_wallet(request.value);
        we::WalletClientConfig config{
            descriptor.record_id, descriptor.address, descriptor.public_key,
            descriptor.secret_ref, descriptor.network, 60, 30,
            we::ProviderConfig{"https://toncenter.com", std::nullopt, 15000}
        };
        // The existing engine API takes a client, but decryption uses only local
        // key material. A dedicated transport prohibits any provider requests.
        auto client = we::WalletClient::new_statusless(config,
            std::make_shared<OfflineWalletTransport>(), host);
        SensitiveString plaintext(0);
        plaintext.value = client->decrypt_comment({java_string(env, sender), java_string(env, body)});
        auto result = env->NewByteArray(static_cast<jsize>(plaintext.value.size()));
        if (!result) return nullptr;
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(plaintext.value.size()),
                               reinterpret_cast<const jbyte *>(plaintext.value.data()));
        return result;
    } catch (const we::wallet_lifecycle_error::InvalidRecoveryPhrase &) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid Rotation recovery phrase");
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not decrypt transaction comment");
    } catch (...) {
        // Do not expose secret material or unauthenticated plaintext in diagnostics.
        throw_java(env, "java/lang/IllegalStateException", "Could not decrypt transaction comment");
    }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeDecryptComment(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes,
        jstring sender, jstring body) {
    try {
        if (!phrase_bytes || !sender || !body)
            throw std::invalid_argument("Recovery phrase, sender and message body are required");
        auto client = get_client(handle);
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;

        SecretScope scope(client, phrase, we::SecretAccessReason::kDecryptComment);
        const auto comment = client.wallet->decrypt_comment({
            java_string(env, sender), java_string(env, body)});
        auto result = env->NewByteArray(static_cast<jsize>(comment.size()));
        if (!result) return nullptr;
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(comment.size()),
                               reinterpret_cast<const jbyte *>(comment.data()));
        return result;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not decrypt transaction comment");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not decrypt transaction comment");
    }
    return nullptr;
}

namespace {
we::SendIntent ton_connect_intent(JNIEnv *env, jobjectArray fields, jlong valid_until) {
    const int count = fields ? env->GetArrayLength(fields) : 0;
    if (count == 0 || count % 5 || count > 255 * 5 || valid_until < 0)
        throw std::invalid_argument("Invalid TON Connect transaction");
    we::SendIntent intent{valid_until == 0 ? we::SendExpiration(we::SendExpiration::kEngineDefault{})
        : we::SendExpiration(we::SendExpiration::kExact{static_cast<uint64_t>(valid_until)}), {}};
    for (int i = 0; i < count; i += 5) {
        std::optional<std::string> values[5];
        for (int j = 0; j < 5; ++j) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(fields, i + j));
            if (value) { values[j] = java_string(env, value); env->DeleteLocalRef(value); }
        }
        if (!values[0] || !values[1] || !values[4] || (*values[4] != "0" && *values[4] != "1")) throw std::invalid_argument("Missing transfer fields");
        auto body = values[2] ? we::SendMessageBody(we::SendMessageBody::kRawPayload{*values[2]})
            : we::SendMessageBody(we::SendMessageBody::kEmpty{});
        intent.messages.push_back({*values[0], we::SendAmount::kExact{*values[1]}, body, *values[4] == "1", values[3]});
    }
    return intent;
}
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePreviewTonConnect(
        JNIEnv *env, jclass, jlong handle, jobjectArray fields, jlong valid_until) {
    try {
        auto intent = ton_connect_intent(env, fields, valid_until);
        auto preview = get_client(handle).wallet->preview_ton_connect({"ton-connect-preview", false, intent});
        return preview_transaction(env, preview, intent.messages.front().destination, 0);
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not preview TON Connect transaction");
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePreviewSignMessage(
        JNIEnv *env, jclass, jlong handle, jobjectArray fields, jlong valid_until) {
    try {
        auto intent = ton_connect_intent(env, fields, valid_until);
        get_client(handle).wallet->preview_sign_message({intent});
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not preview message signing");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeSignMessage(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jobjectArray fields,
        jlong valid_until, jstring operation_id) {
    try {
        auto client = get_client(handle);
        auto intent = ton_connect_intent(env, fields, valid_until);
        if (!phrase_bytes) throw std::invalid_argument("Recovery phrase is required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()), reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        SecretScope scope(client, phrase, we::SecretAccessReason::kSignTransfer);
        // The engine journals the internal message before handoff and never broadcasts it.
        const auto signed_message = client.wallet->sign_message({java_string(env, operation_id), false, intent});
        return env->NewStringUTF(signed_message.internal_boc.c_str());
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not sign internal message");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePrepareTonConnectTransfer(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jobjectArray fields,
        jlong valid_until, jstring operation_id) {
    try {
        auto client = get_client(handle);
        auto intent = ton_connect_intent(env, fields, valid_until);
        if (!phrase_bytes) throw std::invalid_argument("Recovery phrase is required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()), reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        SecretScope scope(client, phrase, we::SecretAccessReason::kSignTransfer);
        auto prepared = client.wallet->prepare_transfer({java_string(env, operation_id), intent});
        auto cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$PreparedSend");
        if (!cls) return nullptr;
        auto ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (!ctor) return nullptr;
        return env->NewObject(cls, ctor, env->NewStringUTF(prepared.external_boc.c_str()), nullptr, nullptr);
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not prepare TON Connect transaction");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePrepareSend(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jstring address,
        jlong amount, jbyteArray comment_bytes, jbyteArray comment_recipient_public_key,
        jstring gasless_address, jstring operation_id) {
    try {
        auto client = get_client(handle);
        const auto destination = java_string(env, address);
        if (!we::is_valid_ton_address(destination))
            throw std::invalid_argument("Valid destination address is required");
        std::optional<std::string> relayer;
        if (gasless_address) {
            relayer = java_string(env, gasless_address);
            if (!we::is_valid_ton_address(*relayer))
                throw std::invalid_argument("Valid relayer address is required");
        }
        if (amount < 0) throw std::invalid_argument("Transfer amount must not be negative");
        if (!phrase_bytes) throw std::invalid_argument("Recovery phrase is required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        std::optional<we::Boc> encrypted_comment_body;
        auto body = we::SendMessageBody(we::SendMessageBody::kEmpty{});
        if (comment_bytes) {
            std::string comment(static_cast<size_t>(env->GetArrayLength(comment_bytes)), '\0');
            env->GetByteArrayRegion(comment_bytes, 0, static_cast<jsize>(comment.size()),
                                   reinterpret_cast<jbyte *>(comment.data()));
            if (env->ExceptionCheck()) return nullptr;
            if (comment_recipient_public_key) {
                std::vector<uint8_t> recipient_key(static_cast<size_t>(env->GetArrayLength(comment_recipient_public_key)));
                if (recipient_key.size() != 32) throw std::invalid_argument("Recipient public key must be 32 bytes");
                env->GetByteArrayRegion(comment_recipient_public_key, 0, 32, reinterpret_cast<jbyte *>(recipient_key.data()));
                if (env->ExceptionCheck()) return nullptr;
                SecretScope scope(client, phrase, we::SecretAccessReason::kEncryptComment);
                encrypted_comment_body = client.wallet->create_encrypted_comment({destination, comment, recipient_key});
                body = we::SendMessageBody::kRawPayload{*encrypted_comment_body};
            } else {
                body = we::SendMessageBody::kComment{comment};
            }
        }
        SecretScope scope(client, phrase, we::SecretAccessReason::kSignTransfer);
        const auto prepared = client.wallet->prepare_transfer({
            java_string(env, operation_id), transfer_intent(destination, amount, std::move(body))});
        const auto external_boc = env->NewStringUTF(prepared.external_boc.c_str());
        if (!external_boc) return nullptr;
        const auto internal_boc = relayer ? env->NewStringUTF(prepared.internal_boc.c_str()) : nullptr;
        if (env->ExceptionCheck()) return nullptr;
        const auto comment_body = encrypted_comment_body
            ? env->NewStringUTF(encrypted_comment_body->c_str()) : nullptr;
        if (env->ExceptionCheck()) return nullptr;
        const auto prepared_cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$PreparedSend");
        if (!prepared_cls) return nullptr;
        const auto prepared_constructor = env->GetMethodID(prepared_cls, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (!prepared_constructor) return nullptr;
        return env->NewObject(prepared_cls, prepared_constructor, external_boc, internal_boc, comment_body);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not prepare transfer");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not prepare transfer");
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeMessageBodyHash(
        JNIEnv *env, jclass, jstring boc) {
    try {
        const auto hash = gram_adapter::message_body_hash(java_string(env, boc));
        return env->NewStringUTF(hash.c_str());
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not hash message body");
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not hash message body");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeSend(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jstring address,
        jlong amount, jbyteArray comment_bytes, jbyteArray comment_recipient_public_key,
        jstring operation_id, jobject callbacks) {
    try {
        auto client = get_client(handle);
        auto destination = java_string(env, address);
        std::optional<std::string> comment;
        if (comment_bytes) {
            comment.emplace(static_cast<size_t>(env->GetArrayLength(comment_bytes)), '\0');
            env->GetByteArrayRegion(comment_bytes, 0, static_cast<jsize>(comment->size()),
                                    reinterpret_cast<jbyte *>(comment->data()));
            if (env->ExceptionCheck()) return nullptr;
        }
        auto id = java_string(env, operation_id);
        if (!phrase_bytes || !callbacks) throw std::invalid_argument("Recovery phrase and callbacks are required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        auto body = comment
            ? we::SendMessageBody(we::SendMessageBody::kComment{*comment})
            : we::SendMessageBody(we::SendMessageBody::kEmpty{});
        if (comment && comment_recipient_public_key) {
            std::vector<uint8_t> recipient_key(static_cast<size_t>(env->GetArrayLength(comment_recipient_public_key)));
            if (recipient_key.size() != 32) throw std::invalid_argument("Recipient public key must be 32 bytes");
            env->GetByteArrayRegion(comment_recipient_public_key, 0, 32, reinterpret_cast<jbyte *>(recipient_key.data()));
            if (env->ExceptionCheck()) return nullptr;
            SecretScope scope(client, phrase, we::SecretAccessReason::kEncryptComment);
            body = we::SendMessageBody::kRawPayload{
                client.wallet->create_encrypted_comment({destination, *comment, recipient_key})
            };
        }
        auto intent = transfer_intent(destination, amount, std::move(body));
        SendScope scope(env, client, phrase, callbacks, id);
        auto sent = client.wallet->send({id, false, intent});
        Transaction result(env, destination, amount);
        result.boolean("feeUnknown", true);
        result.text("operationId", sent.operation_id);
        result.text("messageHash", sent.message_hash);
        result.phase(sent.phase);
        return result.object;
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not send transfer");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not send transfer");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeResolvePending(JNIEnv *env, jclass, jlong handle) {
    try {
        const auto snapshot = get_client(handle).wallet->resolve_pending();
        const auto cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$SendResolution");
        if (!cls) return nullptr;
        const auto constructor = env->GetMethodID(cls, "<init>",
            "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;J)V");
        if (!constructor) return nullptr;
        const auto operation_id = snapshot.operation_id ? env->NewStringUTF(snapshot.operation_id->c_str()) : nullptr;
        const auto transaction_hash = snapshot.resolution && snapshot.resolution->transaction_hash
            ? env->NewStringUTF(snapshot.resolution->transaction_hash->c_str()) : nullptr;
        const auto error = snapshot.error_message ? env->NewStringUTF(snapshot.error_message->c_str()) : nullptr;
        const auto retry_after = snapshot.resolution && snapshot.resolution->retry_after_hint_ms
            ? *snapshot.resolution->retry_after_hint_ms : 4000;
        if (env->ExceptionCheck()) return nullptr;
        return env->NewObject(cls, constructor, operation_id, static_cast<jint>(snapshot.phase),
            transaction_hash, error, static_cast<jlong>(retry_after));
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not resolve wallet send");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not resolve wallet send");
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeRotateKey(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes,
        jstring operation_id, jobject callbacks) {
    try {
        auto client = get_client(handle);
        auto id = java_string(env, operation_id);
        if (!phrase_bytes || !callbacks)
            throw std::invalid_argument("Recovery phrase and callbacks are required");
        auto callback_class = env->GetObjectClass(callbacks);
        auto replacement_callback = env->GetMethodID(callback_class, "replacement", "([B[B)V");
        env->DeleteLocalRef(callback_class);
        if (!replacement_callback) return;

        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return;
        SendScope scope(env, client, phrase, callbacks, id, we::SecretAccessReason::kPrepareKeyRotation);

        // Use provider time for the signature rather than the device clock.
        const auto snapshot = client.wallet->refresh().snapshot;
        if (snapshot.account_resource.phase != we::ResourcePhase::kReady || !snapshot.account)
            throw std::runtime_error(snapshot.account_resource.error
                ? snapshot.account_resource.error->developer_message : "Could not load wallet account");
        constexpr uint64_t validity_seconds = 120;
        if (snapshot.account->sync_utime > std::numeric_limits<uint32_t>::max() - validity_seconds)
            throw std::runtime_error("Key rotation expiration exceeds the supported range");
        auto prepared = client.wallet->prepare_key_rotation({
            snapshot.account->sync_utime + validity_seconds, we::KeyRotationMessageKind::kExternal});
        client.hosts->secret = nullptr;

        {
            SensitiveString replacement(0);
            replacement.value.swap(prepared.replacement_recovery_phrase.phrase);
            if (prepared.new_public_key.size() != 32)
                throw std::runtime_error("Unexpected signing public key size");
            auto public_key = env->NewByteArray(32);
            if (!public_key) return;
            env->SetByteArrayRegion(public_key, 0, 32,
                                   reinterpret_cast<const jbyte *>(prepared.new_public_key.data()));
            if (env->ExceptionCheck()) return;
            auto bytes = env->NewByteArray(static_cast<jsize>(replacement.value.size()));
            if (!bytes) return;
            env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(replacement.value.size()),
                                   reinterpret_cast<const jbyte *>(replacement.value.data()));
            if (env->ExceptionCheck()) return;
            // This callback waits for Java to finish saving the phrase. Never submit first.
            env->CallVoidMethod(callbacks, replacement_callback, bytes, public_key);
            if (env->ExceptionCheck()) return;
            env->DeleteLocalRef(bytes);
            env->DeleteLocalRef(public_key);
        }

        client.wallet->send_boc({id, false, prepared.signed_boc, prepared.seqno, prepared.valid_until});
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not rotate wallet key");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not rotate wallet key");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeClose(JNIEnv *env, jclass, jlong handle) {
    try {
        std::shared_ptr<we::WalletClient> client;
        {
            std::lock_guard<std::mutex> lock(clients_mutex);
            auto it = clients.find(handle);
            if (it == clients.end()) return;
            client = std::move(it->second.wallet);
            clients.erase(it);
        }
        // Never hold the registry lock while entering Rust or running host callbacks.
        client->shutdown();
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not close wallet engine");
    }
}


extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativeEmulateSendNFT(
        JNIEnv *env, jclass, jlong handle, jstring recipient, jstring nft_address,
        jbyteArray comment_bytes, jstring operation_id, jlong attached, jlong forward) {
    try {
        auto client = get_client(handle);
        const auto destination = java_string(env, recipient);
        const auto intent = nft_transfer_intent(env, comment_bytes, destination, java_string(env, nft_address), attached, forward);
        const auto preview = client.wallet->preview_nft_transfer({java_string(env, operation_id), intent});
        // The transferred asset is an NFT, so don't present attached execution funds as a coin payment.
        return preview_transaction(env, preview, destination, 0);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not preview NFT transfer");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not preview NFT transfer");
    }
    return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_org_telegram_ui_Wallet_WalletEngine2_nativePrepareSendNFT(
        JNIEnv *env, jclass, jlong handle, jbyteArray phrase_bytes, jstring recipient,
        jstring nft_address, jbyteArray comment_bytes, jstring operation_id, jlong attached, jlong forward) {
    try {
        auto client = get_client(handle);
        const auto intent = nft_transfer_intent(env, comment_bytes, java_string(env, recipient), java_string(env, nft_address), attached, forward);
        if (!phrase_bytes) throw std::invalid_argument("Recovery phrase is required");
        SensitiveString phrase(static_cast<size_t>(env->GetArrayLength(phrase_bytes)));
        env->GetByteArrayRegion(phrase_bytes, 0, static_cast<jsize>(phrase.value.size()),
                               reinterpret_cast<jbyte *>(phrase.value.data()));
        if (env->ExceptionCheck()) return nullptr;
        SecretScope scope(client, phrase, we::SecretAccessReason::kSignTransfer);
        const auto prepared = client.wallet->prepare_nft_transfer({java_string(env, operation_id), intent});
        const auto external = env->NewStringUTF(prepared.external_boc.c_str());
        if (!external) return nullptr;
        const auto cls = env->FindClass("org/telegram/ui/Wallet/WalletEngine2$PreparedSend");
        if (!cls) return nullptr;
        const auto constructor = env->GetMethodID(cls, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (!constructor) return nullptr;
        // The current API requests normal delivery; no relayer/gasless route was selected.
        return env->NewObject(cls, constructor, external, nullptr, nullptr);
    } catch (const std::bad_alloc &) {
        throw_java(env, "java/lang/OutOfMemoryError", "Could not prepare NFT transfer");
    } catch (const std::exception &error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throw_java(env, "java/lang/IllegalStateException", "Could not prepare NFT transfer");
    }
    return nullptr;
}
