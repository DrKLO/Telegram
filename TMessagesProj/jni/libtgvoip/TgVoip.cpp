#include "TgVoip.h"

#include "VoIPController.h"
#include "VoIPServerConfig.h"

#include <stdarg.h>

#ifndef TGVOIP_USE_CUSTOM_CRYPTO
extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
//#include <openssl/modes.h>
#include <openssl/rand.h>
}

void tgvoip_openssl_aes_ige_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_ige_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

void tgvoip_openssl_rand_bytes(uint8_t* buffer, size_t len){
  RAND_bytes(buffer, len);
}

void tgvoip_openssl_sha1(uint8_t* msg, size_t len, uint8_t* output){
  SHA1(msg, len, output);
}

void tgvoip_openssl_sha256(uint8_t* msg, size_t len, uint8_t* output){
  SHA256(msg, len, output);
}

void tgvoip_openssl_aes_ctr_encrypt(uint8_t* inout, size_t length, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
  AES_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num);
}

void tgvoip_openssl_aes_cbc_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_cbc_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}


tgvoip::CryptoFunctions tgvoip::VoIPController::crypto={
    tgvoip_openssl_rand_bytes,
    tgvoip_openssl_sha1,
    tgvoip_openssl_sha256,
    tgvoip_openssl_aes_ige_encrypt,
    tgvoip_openssl_aes_ige_decrypt,
    tgvoip_openssl_aes_ctr_encrypt,
    tgvoip_openssl_aes_cbc_encrypt,
    tgvoip_openssl_aes_cbc_decrypt
};
#else
struct TgVoipCrypto {
    void (*rand_bytes)(uint8_t* buffer, size_t length);
    void (*sha1)(uint8_t* msg, size_t length, uint8_t* output);
    void (*sha256)(uint8_t* msg, size_t length, uint8_t* output);
    void (*aes_ige_encrypt)(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv);
    void (*aes_ige_decrypt)(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv);
    void (*aes_ctr_encrypt)(uint8_t* inout, size_t length, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num);
    void (*aes_cbc_encrypt)(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv);
    void (*aes_cbc_decrypt)(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv);
};
tgvoip::CryptoFunctions tgvoip::VoIPController::crypto; // set it yourself upon initialization
#endif


class TgVoipImpl : public TgVoip {
private:
    tgvoip::VoIPController *controller_;
    std::function<void(TgVoipState)> onStateUpdated_;
    std::function<void(int)> onSignalBarsUpdated_;
public:
    TgVoipImpl(
        std::vector<TgVoipEndpoint> const &endpoints,
        TgVoipPersistentState const &persistentState,
        std::unique_ptr<TgVoipProxy> const &proxy,
        TgVoipConfig const &config,
        TgVoipEncryptionKey const &encryptionKey,
        TgVoipNetworkType initialNetworkType
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
        ,
        TgVoipCrypto const &crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
        ,
        TgVoipAudioDataCallbacks const &audioDataCallbacks
#endif
    ) {
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
        tgvoip::VoIPController::crypto.sha1 = crypto.sha1;
        tgvoip::VoIPController::crypto.sha256 = crypto.sha256;
        tgvoip::VoIPController::crypto.rand_bytes = crypto.rand_bytes;
        tgvoip::VoIPController::crypto.aes_ige_encrypt = crypto.aes_ige_encrypt;
        tgvoip::VoIPController::crypto.aes_ige_decrypt = crypto.aes_ige_decrypt;
        tgvoip::VoIPController::crypto.aes_ctr_encrypt = crypto.aes_ctr_encrypt;
#endif

        controller_ = new tgvoip::VoIPController();
        controller_->implData = this;

#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
        controller_->SetAudioDataCallbacks(audioDataCallbacks.input, audioDataCallbacks.output, audioDataCallbacks.preprocessed);
#endif

        controller_->SetPersistentState(persistentState.value);

        if (proxy != nullptr) {
            controller_->SetProxy(tgvoip::PROXY_SOCKS5, proxy->host, proxy->port, proxy->login, proxy->password);
        }

        auto callbacks = tgvoip::VoIPController::Callbacks();
        callbacks.connectionStateChanged = &TgVoipImpl::controllerStateCallback;
        callbacks.groupCallKeyReceived = nullptr;
        callbacks.groupCallKeySent = nullptr;
        callbacks.signalBarCountChanged = &TgVoipImpl::signalBarsCallback;
        callbacks.upgradeToGroupCallRequested = nullptr;
        controller_->SetCallbacks(callbacks);

        std::vector<tgvoip::Endpoint> mappedEndpoints;
        for (auto endpoint : endpoints) {
            bool isIpv6 = false;
            struct in6_addr addrIpV6;
            if (inet_pton(AF_INET6, endpoint.host.c_str(), &addrIpV6)) {
                isIpv6 = true;
            }

            tgvoip::Endpoint::Type mappedType;
            switch (endpoint.type) {
                case TgVoipEndpointType::UdpRelay:
                    mappedType = tgvoip::Endpoint::Type::UDP_RELAY;
                    break;
                case TgVoipEndpointType::Lan:
                    mappedType = tgvoip::Endpoint::Type::UDP_P2P_LAN;
                    break;
                case TgVoipEndpointType::Inet:
                    mappedType = tgvoip::Endpoint::Type::UDP_P2P_INET;
                    break;
                case TgVoipEndpointType::TcpRelay:
                    mappedType = tgvoip::Endpoint::Type::TCP_RELAY;
                    break;
                default:
                    mappedType = tgvoip::Endpoint::Type::UDP_RELAY;
                    break;
            }

            tgvoip::IPv4Address address(isIpv6 ? std::string() : endpoint.host);
            tgvoip::IPv6Address addressv6(isIpv6 ? endpoint.host : std::string());

            mappedEndpoints.emplace_back(endpoint.endpointId, endpoint.port, address, addressv6, mappedType, endpoint.peerTag);
        }

        int mappedDataSaving;
        switch (config.dataSaving) {
            case TgVoipDataSaving::Mobile:
                mappedDataSaving = tgvoip::DATA_SAVING_MOBILE;
                break;
            case TgVoipDataSaving::Always:
                mappedDataSaving = tgvoip::DATA_SAVING_ALWAYS;
                break;
            default:
                mappedDataSaving = tgvoip::DATA_SAVING_NEVER;
                break;
        }

        tgvoip::VoIPController::Config mappedConfig(
            config.initializationTimeout,
            config.receiveTimeout,
            mappedDataSaving,
            config.enableAEC,
            config.enableNS,
            config.enableAGC,
            config.enableCallUpgrade
        );
        mappedConfig.logFilePath = config.logPath;
        mappedConfig.statsDumpFilePath = "";

        controller_->SetConfig(mappedConfig);

        setNetworkType(initialNetworkType);

        std::vector<uint8_t> encryptionKeyValue = encryptionKey.value;
        controller_->SetEncryptionKey((char *)(encryptionKeyValue.data()), encryptionKey.isOutgoing);
        controller_->SetRemoteEndpoints(mappedEndpoints, config.enableP2P, config.maxApiLayer);

        controller_->Start();

        controller_->Connect();
    }

    ~TgVoipImpl() override = default;

    void setOnStateUpdated(std::function<void(TgVoipState)> onStateUpdated) override {
        onStateUpdated_ = onStateUpdated;
    }

    void setOnSignalBarsUpdated(std::function<void(int)> onSignalBarsUpdated) override {
        onSignalBarsUpdated_ = onSignalBarsUpdated;
    }

    void setNetworkType(TgVoipNetworkType networkType) override {
        int mappedType;

        switch (networkType) {
            case TgVoipNetworkType::Unknown:
                mappedType = tgvoip::NET_TYPE_UNKNOWN;
                break;
            case TgVoipNetworkType::Gprs:
                mappedType = tgvoip::NET_TYPE_GPRS;
                break;
            case TgVoipNetworkType::Edge:
                mappedType = tgvoip::NET_TYPE_EDGE;
                break;
            case TgVoipNetworkType::ThirdGeneration:
                mappedType = tgvoip::NET_TYPE_3G;
                break;
            case TgVoipNetworkType::Hspa:
                mappedType = tgvoip::NET_TYPE_HSPA;
                break;
            case TgVoipNetworkType::Lte:
                mappedType = tgvoip::NET_TYPE_LTE;
                break;
            case TgVoipNetworkType::WiFi:
                mappedType = tgvoip::NET_TYPE_WIFI;
                break;
            case TgVoipNetworkType::Ethernet:
                mappedType = tgvoip::NET_TYPE_ETHERNET;
                break;
            case TgVoipNetworkType::OtherHighSpeed:
                mappedType = tgvoip::NET_TYPE_OTHER_HIGH_SPEED;
                break;
            case TgVoipNetworkType::OtherLowSpeed:
                mappedType = tgvoip::NET_TYPE_OTHER_LOW_SPEED;
                break;
            case TgVoipNetworkType::OtherMobile:
                mappedType = tgvoip::NET_TYPE_OTHER_MOBILE;
                break;
            case TgVoipNetworkType::Dialup:
                mappedType = tgvoip::NET_TYPE_DIALUP;
                break;
            default:
                mappedType = tgvoip::NET_TYPE_UNKNOWN;
                break;
        }

        controller_->SetNetworkType(mappedType);
    }

    void setMuteMicrophone(bool muteMicrophone) override {
        controller_->SetMicMute(muteMicrophone);
    }

    void setAudioOutputGainControlEnabled(bool enabled) override {
        controller_->SetAudioOutputGainControlEnabled(enabled);
    }

    void setEchoCancellationStrength(int strength) override {
        controller_->SetEchoCancellationStrength(strength);
    }

    std::string getLastError() override {
        switch (controller_->GetLastError()) {
            case tgvoip::ERROR_INCOMPATIBLE: return "ERROR_INCOMPATIBLE";
            case tgvoip::ERROR_TIMEOUT: return "ERROR_TIMEOUT";
            case tgvoip::ERROR_AUDIO_IO: return "ERROR_AUDIO_IO";
            case tgvoip::ERROR_PROXY: return "ERROR_PROXY";
            default: return "ERROR_UNKNOWN";
        }
    }

    std::string getDebugInfo() override {
        return controller_->GetDebugString();
    }

    int64_t getPreferredRelayId() override {
        return controller_->GetPreferredRelayID();
    }

    TgVoipTrafficStats getTrafficStats() override {
        tgvoip::VoIPController::TrafficStats stats;
        controller_->GetStats(&stats);
        return {
            .bytesSentWifi = stats.bytesSentWifi,
            .bytesReceivedWifi = stats.bytesRecvdWifi,
            .bytesSentMobile = stats.bytesSentMobile,
            .bytesReceivedMobile = stats.bytesRecvdMobile
        };
    }

    TgVoipPersistentState getPersistentState() override {
        return {controller_->GetPersistentState()};
    }

    TgVoipFinalState stop() override {
        controller_->Stop();

        TgVoipFinalState finalState = {
            .trafficStats = getTrafficStats(),
            .persistentState = getPersistentState(),
            .debugLog = controller_->GetDebugLog(),
            .isRatingSuggested = controller_->NeedRate()
        };

        delete controller_;
        controller_ = nullptr;

        return finalState;
    }

    static void controllerStateCallback(tgvoip::VoIPController *controller, int state) {
        auto *self = (TgVoipImpl *) controller->implData;
        if (self->onStateUpdated_) {
            TgVoipState mappedState;
            switch (state) {
                case tgvoip::STATE_WAIT_INIT:
                    mappedState = TgVoipState::WaitInit;
                    break;
                case tgvoip::STATE_WAIT_INIT_ACK:
                    mappedState = TgVoipState::WaitInitAck;
                    break;
                case tgvoip::STATE_ESTABLISHED:
                    mappedState = TgVoipState::Estabilished;
                    break;
                case tgvoip::STATE_FAILED:
                    mappedState = TgVoipState::Failed;
                    break;
                case tgvoip::STATE_RECONNECTING:
                    mappedState = TgVoipState::Reconnecting;
                    break;
                default:
                    mappedState = TgVoipState::Estabilished;
                    break;
            }

            self->onStateUpdated_(mappedState);
        }
    }

    static void signalBarsCallback(tgvoip::VoIPController *controller, int signalBars) {
        auto *self = (TgVoipImpl *) controller->implData;
        if (self->onSignalBarsUpdated_) {
            self->onSignalBarsUpdated_(signalBars);
        }
    }
};

std::function<void(std::string const &)> globalLoggingFunction;

void __tgvoip_call_tglog(const char *format, ...){
    va_list vaArgs;
    va_start(vaArgs, format);

    va_list vaCopy;
    va_copy(vaCopy, vaArgs);
    const int length = std::vsnprintf(nullptr, 0, format, vaCopy);
    va_end(vaCopy);

    std::vector<char> zc(length + 1);
    std::vsnprintf(zc.data(), zc.size(), format, vaArgs);
    va_end(vaArgs);

    if (globalLoggingFunction != nullptr) {
        globalLoggingFunction(std::string(zc.data(), zc.size()));
    }
}

void TgVoip::setLoggingFunction(std::function<void(std::string const &)> loggingFunction) {
    globalLoggingFunction = loggingFunction;
}

void TgVoip::setGlobalServerConfig(const std::string &serverConfig) {
    tgvoip::ServerConfig::GetSharedInstance()->Update(serverConfig);
}

int TgVoip::getConnectionMaxLayer() {
    return tgvoip::VoIPController::GetConnectionMaxLayer();
}

std::string TgVoip::getVersion() {
    return tgvoip::VoIPController::GetVersion();
}

TgVoip *TgVoip::makeInstance(
    TgVoipConfig const &config,
    TgVoipPersistentState const &persistentState,
    std::vector<TgVoipEndpoint> const &endpoints,
    std::unique_ptr<TgVoipProxy> const &proxy,
    TgVoipNetworkType initialNetworkType,
    TgVoipEncryptionKey const &encryptionKey
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
    ,
    TgVoipCrypto const &crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
    ,
    TgVoipAudioDataCallbacks const &audioDataCallbacks
#endif
) {
    return new TgVoipImpl(
        endpoints,
        persistentState,
        proxy,
        config,
        encryptionKey,
        initialNetworkType
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
        ,
        crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
        ,
        audioDataCallbacks
#endif
    );
}

TgVoip::~TgVoip() = default;