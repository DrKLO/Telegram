#ifndef TGCALLS_V2WASM_REFERENCE_CALL_CORE_H
#define TGCALLS_V2WASM_REFERENCE_CALL_CORE_H

// Control-logic core for the pump-based reference call implementation.
// WASM discipline: this unit may include only CallCoreABI.h, json11,
// SignalingFraming.h (and, transitively via it, CoreGzip — backed by the
// ABI's host compression service, no third-party code) and the C++17
// standard library. It has no clock, no threads, and never blocks.

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "third-party/json11.hpp"
#include "v2wasm/SignalingFraming.h"

namespace tgcalls {
namespace v2wasm {

class ReferenceCallCore {
public:
    ReferenceCallCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit);
    virtual ~ReferenceCallCore() = default;

    void onEvent(json11::Json const &event);

protected:
    // Variant hook points. Defaults reproduce stock behavior exactly;
    // VariantCallCore overrides these (and only these).
    virtual std::string mungeLocalDescription(std::string const &type, std::string const &sdp);
    virtual void onStats(json11::Json const &event);
    virtual void onIceState(std::string const &state);
    void maybeRestartIce();
    void updateIsConnected(bool isConnected);
    // Called on every outgoing signaling message before framing; variants
    // may mutate/extend the JSON (unknown keys are ignored by stock peers).
    virtual void mungeOutgoingSignalingMessage(json11::Json::object &message);
    // Called for every dc_state/dc_message/dc_buffered/dc_channel event,
    // after the reference core's own (label "data") handling.
    virtual void onDataChannelEvent(json11::Json const &event);

    struct NetworkStateRecord {
        int64_t timestampMs = 0;
        bool isConnected = false;
        bool isFailed = false;
        json11::Json connection; // object {local:{...},remote:{...}} or null
    };
    struct BitrateRecord {
        int64_t timestampMs = 0;
        int32_t bitrateKbps = 0;
    };

    void emit(json11::Json::object &&command);
    void emitLog(std::string const &message);
    void requestSetLocalDescription();
    void handleSignalingData(std::string const &data);
    void handleRemoteSdp(std::string const &type, std::string const &sdp);
    void handleMediaStateMessage(json11::Json const &message);
    void flushPendingRemoteCandidates();
    void sendMediaState();
    void updateNetworkState(bool isConnected, bool isFailed);
    void emitMappedState();
    void handleStop();
    void sendSignalingMessage(json11::Json::object &&message);

    std::function<void(json11::Json::object &&)> _emit;
    std::unique_ptr<SignalingFraming> _framing;

    // config
    bool _isOutgoing = false;
    bool _enableP2P = false;
    std::vector<json11::Json> _rtcServers;

    // clock (from event nowMs)
    int64_t _nowMs = 0;

    // negotiation state (ported 1:1 from InstanceV2ReferenceImplInternal)
    bool _didBeginNegotiation = false;
    bool _isMakingOffer = false;
    bool _isSettingRemoteAnswerPending = false;
    bool _haveLocalDescription = false;
    bool _haveRemoteDescription = false;
    std::string _signalingState = "stable";
    std::vector<json11::Json> _pendingRemoteCandidates;

    // media state
    bool _isMicrophoneMuted = false;
    bool _isBatteryLow = false;
    bool _hasVideoCapture = false;
    bool _hasVideoTrack = false;
    bool _isDataChannelOpen = false;

    // network state + logs
    bool _isConnected = false;
    bool _isFailed = false;
    bool _didEmitBaselineRecord = false;
    int64_t _lastDisconnectedTimestampMs = 0;
    int64_t _lastIceRestartTimestampMs = 0;
    int _connectionTimerGeneration = 0;
    int _disconnectReportGeneration = 0;
    json11::Json _currentConnection; // null until first candidate pair
    std::vector<NetworkStateRecord> _networkStateRecords;
    std::vector<BitrateRecord> _bitrateRecords;
};

} // namespace v2wasm
} // namespace tgcalls

#endif
