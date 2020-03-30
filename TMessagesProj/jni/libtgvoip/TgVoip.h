#ifndef __TGVOIP_H
#define __TGVOIP_H

#include <functional>
#include <vector>
#include <string>
#include <memory>

struct TgVoipProxy {
    std::string host;
    uint16_t port;
    std::string login;
    std::string password;
};

enum class TgVoipEndpointType {
    Inet,
    Lan,
    UdpRelay,
    TcpRelay
};

struct TgVoipEndpoint {
    int64_t endpointId;
    std::string host;
    uint16_t port;
    TgVoipEndpointType type;
    unsigned char peerTag[16];
};

enum class TgVoipNetworkType {
    Unknown,
    Gprs,
    Edge,
    ThirdGeneration,
    Hspa,
    Lte,
    WiFi,
    Ethernet,
    OtherHighSpeed,
    OtherLowSpeed,
    OtherMobile,
    Dialup
};

enum class TgVoipDataSaving {
    Never,
    Mobile,
    Always
};

struct TgVoipPersistentState {
    std::vector<uint8_t> value;
};

#ifdef TGVOIP_USE_CUSTOM_CRYPTO
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
#endif

struct TgVoipConfig {
    double initializationTimeout;
    double receiveTimeout;
    TgVoipDataSaving dataSaving;
    bool enableP2P;
    bool enableAEC;
    bool enableNS;
    bool enableAGC;
    bool enableCallUpgrade;
    std::string logPath;
    int maxApiLayer;
};

struct TgVoipEncryptionKey {
    std::vector<uint8_t> value;
    bool isOutgoing;
};

enum class TgVoipState {
    WaitInit,
    WaitInitAck,
    Estabilished,
    Failed,
    Reconnecting
};

struct TgVoipTrafficStats {
    uint64_t bytesSentWifi;
    uint64_t bytesReceivedWifi;
    uint64_t bytesSentMobile;
    uint64_t bytesReceivedMobile;
};

struct TgVoipFinalState {
    TgVoipPersistentState persistentState;
    std::string debugLog;
    TgVoipTrafficStats trafficStats;
    bool isRatingSuggested;
};

struct TgVoipAudioDataCallbacks {
    std::function<void(int16_t*, size_t)> input;
    std::function<void(int16_t*, size_t)> output;
    std::function<void(int16_t*, size_t)> preprocessed;
};

class TgVoip {
protected:
    TgVoip() = default;
    
public:
    static void setLoggingFunction(std::function<void(std::string const &)> loggingFunction);
    static void setGlobalServerConfig(std::string const &serverConfig);
    static int getConnectionMaxLayer();
    static std::string getVersion();
    static TgVoip *makeInstance(
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
    );
    
    virtual ~TgVoip();
    
    virtual void setNetworkType(TgVoipNetworkType networkType) = 0;
    virtual void setMuteMicrophone(bool muteMicrophone) = 0;
    virtual void setAudioOutputGainControlEnabled(bool enabled) = 0;
    virtual void setEchoCancellationStrength(int strength) = 0;

    virtual std::string getLastError() = 0;
    virtual std::string getDebugInfo() = 0;
    virtual int64_t getPreferredRelayId() = 0;
    virtual TgVoipTrafficStats getTrafficStats() = 0;
    virtual TgVoipPersistentState getPersistentState() = 0;

    virtual void setOnStateUpdated(std::function<void(TgVoipState)> onStateUpdated) = 0;
    virtual void setOnSignalBarsUpdated(std::function<void(int)> onSignalBarsUpdated) = 0;
    
    virtual TgVoipFinalState stop() = 0;
};

#endif
