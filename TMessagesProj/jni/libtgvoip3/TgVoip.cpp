#include "TgVoip.h"
#include "VoIPController.h"
#include "VoIPServerConfig.h"

#include <mutex>
#include <cstdarg>

#ifndef TGVOIP_USE_CUSTOM_CRYPTO
extern "C"
{
#include <openssl/aes.h>
//#include <openssl/modes.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
}

void tgvoip_openssl_aes_ige_encrypt(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv)
{
    AES_KEY akey;
    AES_set_encrypt_key(key, 32 * 8, &akey);
    AES_ige_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_ige_decrypt(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv)
{
    AES_KEY akey;
    AES_set_decrypt_key(key, 32 * 8, &akey);
    AES_ige_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

void tgvoip_openssl_rand_bytes(std::uint8_t* buffer, std::size_t len)
{
    RAND_bytes(buffer, static_cast<int>(len));
}

void tgvoip_openssl_sha1(const std::uint8_t* msg, std::size_t len, std::uint8_t* output)
{
    SHA1(msg, len, output);
}

void tgvoip_openssl_sha256(const std::uint8_t* msg, std::size_t len, std::uint8_t* output)
{
    SHA256(msg, len, output);
}

void tgvoip_openssl_aes_ctr_encrypt(std::uint8_t* inout, std::size_t length, const std::uint8_t* key, std::uint8_t* iv, std::uint8_t* ecount, std::uint32_t* num)
{
    AES_KEY akey;
    AES_set_encrypt_key(key, 32 * 8, &akey);
    AES_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num);
}

void tgvoip_openssl_aes_cbc_encrypt(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv)
{
    AES_KEY akey;
    AES_set_encrypt_key(key, 256, &akey);
    AES_cbc_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_cbc_decrypt(const std::uint8_t* in, std::uint8_t* out, std::size_t length, const std::uint8_t* key, std::uint8_t* iv)
{
    AES_KEY akey;
    AES_set_decrypt_key(key, 256, &akey);
    AES_cbc_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

tgvoip::CryptoFunctions tgvoip::VoIPController::crypto =
{
    tgvoip_openssl_rand_bytes,
    tgvoip_openssl_sha1,
    tgvoip_openssl_sha256,
    tgvoip_openssl_aes_ige_encrypt,
    tgvoip_openssl_aes_ige_decrypt,
    tgvoip_openssl_aes_ctr_encrypt,
    tgvoip_openssl_aes_cbc_encrypt,
    tgvoip_openssl_aes_cbc_decrypt
};
#endif

class TgVoipImpl : public TgVoip
{
public:
    TgVoipImpl(
        std::vector<TgVoipEndpoint> const& endpoints,
        TgVoipPersistentState const& persistentState,
        std::unique_ptr<TgVoipProxy> const& proxy,
        TgVoipConfig const& config,
        TgVoipEncryptionKey const& encryptionKey,
        TgVoipNetworkType initialNetworkType
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
        ,
        TgVoipCrypto const& crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
        ,
        TgVoipAudioDataCallbacks const& audioDataCallbacks
#endif
    );

    ~TgVoipImpl() override;
    void setOnStateUpdated(std::function<void(TgVoipState)> onStateUpdated) override;
    void setOnSignalBarsUpdated(std::function<void(int)> onSignalBarsUpdated) override;
    void setNetworkType(TgVoipNetworkType networkType) final;
    void setMuteMicrophone(bool muteMicrophone) override;
    void setAudioOutputGainControlEnabled(bool enabled) override;
    void setEchoCancellationStrength(int strength) override;
    std::string getLastError() override;
    std::string getDebugInfo() override;
    std::int64_t getPreferredRelayId() override;
    TgVoipTrafficStats getTrafficStats() override;
    TgVoipPersistentState getPersistentState() override;
    TgVoipFinalState stop() override;

    static void controllerStateCallback(tgvoip::VoIPController* controller, tgvoip::State state);
    static void signalBarsCallback(tgvoip::VoIPController* controller, int signalBars);

private:
    tgvoip::VoIPController* m_controller;
    std::function<void(TgVoipState)> m_onStateUpdated;
    std::function<void(int)> m_onSignalBarsUpdated;
    std::mutex m_mutexOnStateUpdated, m_mutexOnSignalBarsUpdated;
};

TgVoipImpl::TgVoipImpl(
    std::vector<TgVoipEndpoint> const& endpoints,
    TgVoipPersistentState const& persistentState,
    std::unique_ptr<TgVoipProxy> const& proxy,
    TgVoipConfig const& config,
    TgVoipEncryptionKey const& encryptionKey,
    TgVoipNetworkType initialNetworkType
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
    ,
    TgVoipCrypto const& crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
    ,
    TgVoipAudioDataCallbacks const& audioDataCallbacks
#endif
)
{
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
    tgvoip::VoIPController::crypto.sha1 = crypto.sha1;
    tgvoip::VoIPController::crypto.sha256 = crypto.sha256;
    tgvoip::VoIPController::crypto.rand_bytes = crypto.rand_bytes;
    tgvoip::VoIPController::crypto.aes_ige_encrypt = crypto.aes_ige_encrypt;
    tgvoip::VoIPController::crypto.aes_ige_decrypt = crypto.aes_ige_decrypt;
    tgvoip::VoIPController::crypto.aes_ctr_encrypt = crypto.aes_ctr_encrypt;
#endif

    m_controller = new tgvoip::VoIPController();
    m_controller->implData = this;

#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
    m_controller->SetAudioDataCallbacks(audioDataCallbacks.input, audioDataCallbacks.output, audioDataCallbacks.preprocessed);
#endif

    m_controller->SetPersistentState(persistentState.value);

    if (proxy != nullptr)
    {
        m_controller->SetProxy(tgvoip::Proxy::SOCKS5, proxy->host, proxy->port, proxy->login, proxy->password);
    }

    auto callbacks = tgvoip::VoIPController::Callbacks();
    callbacks.connectionStateChanged = &TgVoipImpl::controllerStateCallback;
    callbacks.groupCallKeyReceived = nullptr;
    callbacks.groupCallKeySent = nullptr;
    callbacks.signalBarCountChanged = &TgVoipImpl::signalBarsCallback;
    callbacks.upgradeToGroupCallRequested = nullptr;
    m_controller->SetCallbacks(callbacks);

    std::vector<tgvoip::Endpoint> mappedEndpoints;
    for (const TgVoipEndpoint& endpoint : endpoints)
    {
        tgvoip::Endpoint::Type mappedType;
        switch (endpoint.type)
        {
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
//        default:
//            mappedType = tgvoip::Endpoint::Type::UDP_RELAY;
//            break;
        }

        tgvoip::IPv4Address address(endpoint.host.ipv4);
        tgvoip::IPv6Address addressv6(endpoint.host.ipv6);

        mappedEndpoints.emplace_back(endpoint.endpointId, endpoint.port, address, addressv6, mappedType, endpoint.peerTag);
    }

    tgvoip::DataSaving mappedDataSaving;
    switch (config.dataSaving)
    {
    case TgVoipDataSaving::Mobile:
        mappedDataSaving = tgvoip::DataSaving::MOBILE;
        break;
    case TgVoipDataSaving::Always:
        mappedDataSaving = tgvoip::DataSaving::ALWAYS;
        break;
    case TgVoipDataSaving::Never:
        mappedDataSaving = tgvoip::DataSaving::NEVER;
        break;
    }

    tgvoip::VoIPController::Config mappedConfig(
        config.initializationTimeout,
        config.receiveTimeout,
        mappedDataSaving,
        config.enableAEC,
        config.enableNS,
        config.enableAGC,
        config.enableCallUpgrade);
    mappedConfig.logFilePath = config.logPath;
    mappedConfig.statsDumpFilePath = {};

    m_controller->SetConfig(mappedConfig);

    setNetworkType(initialNetworkType);

    std::vector<std::uint8_t> encryptionKeyValue = encryptionKey.value;
    m_controller->SetEncryptionKey(reinterpret_cast<char*>(encryptionKeyValue.data()), encryptionKey.isOutgoing);
    m_controller->SetRemoteEndpoints(mappedEndpoints, config.enableP2P, config.maxApiLayer);

    m_controller->Start();

    m_controller->Connect();
}

TgVoipImpl::~TgVoipImpl() = default;

void TgVoipImpl::setOnStateUpdated(std::function<void(TgVoipState)> onStateUpdated)
{
    std::lock_guard<std::mutex> lock(m_mutexOnStateUpdated);
    m_onStateUpdated = onStateUpdated;
}

void TgVoipImpl::setOnSignalBarsUpdated(std::function<void(int)> onSignalBarsUpdated)
{
    std::lock_guard<std::mutex> lock(m_mutexOnSignalBarsUpdated);
    m_onSignalBarsUpdated = onSignalBarsUpdated;
}

void TgVoipImpl::setNetworkType(TgVoipNetworkType networkType)
{
    tgvoip::NetType mappedType;

    switch (networkType)
    {
    case TgVoipNetworkType::Unknown:
        mappedType = tgvoip::NetType::UNKNOWN;
        break;
    case TgVoipNetworkType::Gprs:
        mappedType = tgvoip::NetType::GPRS;
        break;
    case TgVoipNetworkType::Edge:
        mappedType = tgvoip::NetType::EDGE;
        break;
    case TgVoipNetworkType::ThirdGeneration:
        mappedType = tgvoip::NetType::THREE_G;
        break;
    case TgVoipNetworkType::Hspa:
        mappedType = tgvoip::NetType::HSPA;
        break;
    case TgVoipNetworkType::Lte:
        mappedType = tgvoip::NetType::LTE;
        break;
    case TgVoipNetworkType::WiFi:
        mappedType = tgvoip::NetType::WIFI;
        break;
    case TgVoipNetworkType::Ethernet:
        mappedType = tgvoip::NetType::ETHERNET;
        break;
    case TgVoipNetworkType::OtherHighSpeed:
        mappedType = tgvoip::NetType::OTHER_HIGH_SPEED;
        break;
    case TgVoipNetworkType::OtherLowSpeed:
        mappedType = tgvoip::NetType::OTHER_LOW_SPEED;
        break;
    case TgVoipNetworkType::OtherMobile:
        mappedType = tgvoip::NetType::OTHER_MOBILE;
        break;
    case TgVoipNetworkType::Dialup:
        mappedType = tgvoip::NetType::DIALUP;
        break;
//    default:
//        mappedType = tgvoip::NetType::UNKNOWN;
//        break;
    }

    m_controller->SetNetworkType(mappedType);
}

void TgVoipImpl::setMuteMicrophone(bool muteMicrophone)
{
    m_controller->SetMicMute(muteMicrophone);
}

void TgVoipImpl::setAudioOutputGainControlEnabled(bool enabled)
{
    m_controller->SetAudioOutputGainControlEnabled(enabled);
}

void TgVoipImpl::setEchoCancellationStrength(int strength)
{
    m_controller->SetEchoCancellationStrength(strength);
}

std::string TgVoipImpl::getLastError()
{
    tgvoip::Error error = m_controller->GetLastError();
    switch (error)
    {
    case tgvoip::Error::INCOMPATIBLE:
        return "Error::INCOMPATIBLE";
    case tgvoip::Error::TIMEOUT:
        return "Error::TIMEOUT";
    case tgvoip::Error::AUDIO_IO:
        return "Error::AUDIO_IO";
    case tgvoip::Error::PROXY:
        return "Error::PROXY";
    case tgvoip::Error::UNKNOWN:
        return "Error::UNKNOWN";
    }
    throw std::runtime_error("Error " + std::to_string(static_cast<int>(error)) + " is not one of enum values!");
}

std::string TgVoipImpl::getDebugInfo()
{
    return m_controller->GetDebugString();
}

std::int64_t TgVoipImpl::getPreferredRelayId()
{
    return m_controller->GetPreferredRelayID();
}

TgVoipTrafficStats TgVoipImpl::getTrafficStats()
{
    tgvoip::VoIPController::TrafficStats stats;
    m_controller->GetStats(&stats);
    return TgVoipTrafficStats
    {
        .bytesSentWifi = stats.bytesSentWifi,
        .bytesReceivedWifi = stats.bytesRecvdWifi,
        .bytesSentMobile = stats.bytesSentMobile,
        .bytesReceivedMobile = stats.bytesRecvdMobile
    };
}

TgVoipPersistentState TgVoipImpl::getPersistentState()
{
    return {m_controller->GetPersistentState()};
}

TgVoipFinalState TgVoipImpl::stop()
{
    m_controller->Stop();

    TgVoipFinalState finalState =
    {
        .persistentState = getPersistentState(),
        .debugLog = m_controller->GetDebugLog(),
        .trafficStats = getTrafficStats(),
        .isRatingSuggested = m_controller->NeedRate()
    };

    delete m_controller;
    m_controller = nullptr;

    return finalState;
}

void TgVoipImpl::controllerStateCallback(tgvoip::VoIPController* controller, tgvoip::State state)
{
    TgVoipImpl* self = reinterpret_cast<TgVoipImpl*>(controller->implData);
    std::lock_guard<std::mutex> lock(self->m_mutexOnStateUpdated);

    if (self->m_onStateUpdated)
    {
        TgVoipState mappedState;
        switch (state)
        {
        case tgvoip::State::WAIT_INIT:
            mappedState = TgVoipState::WaitInit;
            break;
        case tgvoip::State::WAIT_INIT_ACK:
            mappedState = TgVoipState::WaitInitAck;
            break;
        case tgvoip::State::ESTABLISHED:
            mappedState = TgVoipState::Estabilished;
            break;
        case tgvoip::State::FAILED:
            mappedState = TgVoipState::Failed;
            break;
        case tgvoip::State::RECONNECTING:
            mappedState = TgVoipState::Reconnecting;
            break;
//        default:
//            mappedState = TgVoipState::Estabilished;
//            break;
        }

        self->m_onStateUpdated(mappedState);
    }
}

void TgVoipImpl::signalBarsCallback(tgvoip::VoIPController* controller, int signalBars)
{
    TgVoipImpl* self = reinterpret_cast<TgVoipImpl*>(controller->implData);
    std::lock_guard<std::mutex> lock(self->m_mutexOnSignalBarsUpdated);

    if (self->m_onSignalBarsUpdated)
    {
        self->m_onSignalBarsUpdated(signalBars);
    }
}

static std::function<void(std::string const&)> globalLoggingFunction;

void __tgvoip_call_tglog(const char* format, ...)
{
    va_list vaArgs;
    va_start(vaArgs, format);

    va_list vaCopy;
    va_copy(vaCopy, vaArgs);
    const int length = std::vsnprintf(nullptr, 0, format, vaCopy);
    va_end(vaCopy);

    std::vector<char> zc(static_cast<std::size_t>(length) + 1);
    std::vsnprintf(zc.data(), zc.size(), format, vaArgs);
    va_end(vaArgs);

    if (globalLoggingFunction != nullptr)
    {
        globalLoggingFunction(std::string(zc.data(), zc.size()));
    }
}

void TgVoip::setLoggingFunction(std::function<void(std::string const&)> loggingFunction)
{
    globalLoggingFunction = std::move(loggingFunction);
}

void TgVoip::setGlobalServerConfig(const std::string& serverConfig)
{
    tgvoip::ServerConfig::GetSharedInstance()->Update(serverConfig);
}

int TgVoip::getConnectionMaxLayer()
{
    return tgvoip::VoIPController::GetConnectionMaxLayer();
}

std::string TgVoip::getVersion()
{
    return tgvoip::VoIPController::GetVersion();
}

TgVoip* TgVoip::makeInstance(
    TgVoipConfig const& config,
    TgVoipPersistentState const& persistentState,
    std::vector<TgVoipEndpoint> const& endpoints,
    std::unique_ptr<TgVoipProxy> const& proxy,
    TgVoipNetworkType initialNetworkType,
    TgVoipEncryptionKey const& encryptionKey
#ifdef TGVOIP_USE_CUSTOM_CRYPTO
    ,
    TgVoipCrypto const& crypto
#endif
#ifdef TGVOIP_USE_CALLBACK_AUDIO_IO
    ,
    TgVoipAudioDataCallbacks const& audioDataCallbacks
#endif
)
{
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
