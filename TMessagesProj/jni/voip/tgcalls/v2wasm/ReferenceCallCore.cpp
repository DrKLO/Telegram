#include "v2wasm/ReferenceCallCore.h"
#include "v2wasm/CallCoreABI.h"
#include "v2wasm/CoreBase64.h"

#include <algorithm>
#include <memory>

namespace tgcalls {
namespace v2wasm {

namespace {

constexpr int kAbiVersion = 1;
constexpr int kStatsTimerToken = 1;
constexpr int kConnectionTimerToken = 2;
constexpr int kDisconnectTimerToken = 3;
constexpr int kIceRestartMinIntervalMs = 5000;
constexpr int kConnectionFailureTimeoutMs = 20000;
constexpr int kAudioMaxBitrateBps = 32 * 1024;      // stock: 32 * 1024
constexpr int kVideoMaxBitrateBps = 1200 * 1024;    // stock: 1200 * 1024

std::string stringField(json11::Json const &object, std::string const &key) {
    const auto &value = object[key];
    return value.is_string() ? value.string_value() : std::string();
}

} // namespace

ReferenceCallCore::ReferenceCallCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit) :
_emit(std::move(emit)) {
    _isOutgoing = config["isOutgoing"].bool_value();
    _enableP2P = config["enableP2P"].bool_value();
    for (const auto &server : config["rtcServers"].array_items()) {
        _rtcServers.push_back(server);
    }

    SignalingFraming::Delegate framingDelegate;
    framingDelegate.sendPacket = [this](std::vector<uint8_t> &&packet) {
        this->emit({ {"@type", "signaling_send_packet"}, {"packetB64", base64Encode(packet)} });
    };
    framingDelegate.deliverMessage = [this](std::string &&message) {
        // Load-bearing log line (P1/P2 wire diffs); stock counterpart logs
        // outbound only, this one aids debugging.
        emitLog("signaling in: " + message);
        handleSignalingData(message);
    };
    framingDelegate.log = [this](std::string const &line) {
        emitLog(line);
    };
    framingDelegate.nowMs = [this]() {
        return _nowMs;
    };
    _framing = std::make_unique<SignalingFraming>(std::move(framingDelegate));

    this->emit({ {"@type", "core_ready"}, {"abiVersion", kAbiVersion} });

    // Policy: map RtcServers to ICE servers (stock start() lines 641–685).
    json11::Json::array iceServers;
    for (const auto &server : _rtcServers) {
        if (server["isTcp"].bool_value()) {
            continue;
        }
        const auto rawHost = stringField(server, "host");
        if (rawHost.empty()) {
            continue;
        }
        // Stock validates via SocketAddress::IsComplete() and brackets IPv6
        // literals via HostAsURIString(); SocketAddress cannot cross the
        // boundary, so replicate the URI form here.
        const auto host = (rawHost.find(':') != std::string::npos)
            ? "[" + rawHost + "]"
            : rawHost;
        const auto port = std::to_string((int)server["port"].number_value());
        if (server["isTurn"].bool_value()) {
            iceServers.push_back(json11::Json::object{
                {"urls", json11::Json::array{ "turn:" + host + ":" + port }},
                {"username", stringField(server, "login")},
                {"password", stringField(server, "password")},
            });
        } else {
            iceServers.push_back(json11::Json::object{
                {"urls", json11::Json::array{ "stun:" + host + ":" + port }},
                {"username", ""},
                {"password", ""},
            });
        }
    }
    this->emit({
        {"@type", "pc_create"},
        {"iceTransportsType", _enableP2P ? "all" : "relay"},
        {"iceServers", std::move(iceServers)},
    });

    if (_isOutgoing) {
        this->emit({ {"@type", "pc_create_data_channel"} });
    }
    this->emit({
        {"@type", "pc_add_transceiver"},
        {"id", "audio0"},
        {"kind", "audio"},
        {"direction", "sendrecv"},
        {"trackSource", "microphone"},
        {"sendEncodings", json11::Json::array{ json11::Json::object{ {"maxBitrateBps", kAudioMaxBitrateBps} } }},
    });

    // stock beginSignaling()
    _didBeginNegotiation = true;
    if (_isOutgoing) {
        requestSetLocalDescription();
    }

    // stock beginLogTimer(0)
    this->emit({ {"@type", "set_timer"}, {"token", kStatsTimerToken}, {"delayMs", 0} });

    _connectionTimerGeneration += 1;
    this->emit({ {"@type", "set_timer"}, {"token", kConnectionTimerToken}, {"generation", _connectionTimerGeneration}, {"delayMs", 1000} });
}

void ReferenceCallCore::emit(json11::Json::object &&command) {
    _emit(std::move(command));
}

void ReferenceCallCore::emitLog(std::string const &message) {
    emit({ {"@type", "log"}, {"message", message} });
}

void ReferenceCallCore::onEvent(json11::Json const &event) {
    if (event["nowMs"].is_number()) {
        _nowMs = (int64_t)event["nowMs"].number_value();
    }

    // Emit a baseline state record as soon as we have a real clock, so a call
    // that never transitions still uploads a non-empty timeline. _nowMs is zero
    // until the first event carries one, which is why this cannot live in the
    // core-init path.
    if (!_didEmitBaselineRecord && _nowMs != 0) {
        _didEmitBaselineRecord = true;
        updateNetworkState(_isConnected, _isFailed);
    }

    const auto type = stringField(event, "@type");

    if (type == "signaling_packet") {
        const auto packet = base64Decode(stringField(event, "packetB64"));
        if (packet) {
            _framing->receivePacket(*packet);
        }
    } else if (type == "pc_renegotiation_needed") {
        // stock onRenegotiationNeeded delegate
        if (_isMakingOffer) {
            // An offer is already in flight and already covers whatever triggered
            // this event - the data channel created moments ago during setup. Stock
            // suppresses this via the is_negotiation_needed_ latch; the host
            // forwards the legacy OnRenegotiationNeeded, which bypasses that, so we
            // suppress it here. Parity with InstanceV2ReferenceImpl.
            emitLog("onRenegotiationNeeded: offer already in flight, skipping");
        } else if (_didBeginNegotiation) {
            if (_isOutgoing || _haveRemoteDescription) {
                requestSetLocalDescription();
            }
        } else {
            emitLog("onRenegotiationNeeded: not sending local description");
        }
    } else if (type == "pc_ice_candidate") {
        // stock sendIceCandidate: exact wire keys @type/sdp/mid/mline
        json11::Json::object candidate{
            {"@type", "candidate"},
            {"sdp", stringField(event, "sdp")},
            {"mid", stringField(event, "mid")},
            {"mline", (int)event["mline"].number_value()},
        };
        sendSignalingMessage(std::move(candidate));
    } else if (type == "pc_ice_state") {
        onIceState(stringField(event, "state"));
    } else if (type == "pc_signaling_state") {
        _signalingState = stringField(event, "state");
    } else if (type == "pc_description_created") {
        if (event["ok"].bool_value()) {
            const auto descType = stringField(event, "type");
            const auto sdp = mungeLocalDescription(descType, stringField(event, "sdp"));
            emit({ {"@type", "pc_set_local_description"}, {"type", descType}, {"sdp", sdp} });
        } else {
            _isMakingOffer = false;
            emitLog("CreateOffer/CreateAnswer failed: " + stringField(event, "error"));
        }
    } else if (type == "pc_candidate_pair_changed") {
        json11::Json::object connection{
            {"local", event["local"]},
            {"remote", event["remote"]},
        };
        json11::Json connectionJson(std::move(connection));
        if (_currentConnection != connectionJson) {
            _currentConnection = std::move(connectionJson);
            updateNetworkState(_isConnected, _isFailed);
        }
    } else if (type == "pc_set_local_done") {
        _isMakingOffer = false;
        // Deviation from stock: stock sends the local description without
        // checking the SLD error (InstanceV2ReferenceImpl.cpp:888-901); we
        // skip the send on failure. Same below for pc_set_remote_done: stock
        // flushes candidates/answers offers even on SRD failure.
        if (event["ok"].bool_value()) {
            _haveLocalDescription = true;
            // stock doSendLocalDescription: exact wire keys @type/sdp
            json11::Json::object description{
                {"@type", stringField(event, "type")},
                {"sdp", stringField(event, "sdp")},
            };
            sendSignalingMessage(std::move(description));
        } else {
            emitLog("SetLocalDescription failed");
        }
        flushPendingRemoteCandidates();
    } else if (type == "pc_set_remote_done") {
        _isSettingRemoteAnswerPending = false;
        if (event["ok"].bool_value()) {
            _haveRemoteDescription = true;
            flushPendingRemoteCandidates();
            if (stringField(event, "sdpType") == "offer") {
                requestSetLocalDescription();
            }
        } else {
            emitLog("SetRemoteDescription failed");
        }
    } else if (type == "dc_state") {
        if (stringField(event, "label") == "data") {
            const bool open = event["open"].bool_value();
            if (open && !_isDataChannelOpen) {
                _isDataChannelOpen = true;
                sendMediaState();
            } else if (!open) {
                _isDataChannelOpen = false;
            }
        }
        onDataChannelEvent(event);
    } else if (type == "dc_message") {
        if (stringField(event, "label") == "data") {
            // stock feeds non-binary data-channel messages into processSignalingData
            handleSignalingData(stringField(event, "data"));
        }
        onDataChannelEvent(event);
    } else if (type == "dc_buffered" || type == "dc_channel") {
        onDataChannelEvent(event);
    } else if (type == "timer") {
        const int token = (int)event["token"].number_value();
        if (token == kStatsTimerToken) {
            emit({ {"@type", "pc_get_stats"} });
            emit({ {"@type", "set_timer"}, {"token", kStatsTimerToken}, {"delayMs", 1000} });
        } else if (token == kConnectionTimerToken) {
            // set_timer does NOT cancel a prior timer of the same token, so every
            // re-arm would otherwise stack another live timer. Ignore stale ones.
            if ((int)event["generation"].number_value() != _connectionTimerGeneration) {
                return;
            }

            if (_isConnected) {
                _lastDisconnectedTimestampMs = _nowMs;
            } else {
                // Seeded from an event-supplied clock, never the constructor: _nowMs
                // is 0 until the first event carries one, and a zero seed would make
                // this fire on every call after ~1s.
                if (_lastDisconnectedTimestampMs == 0) {
                    _lastDisconnectedTimestampMs = _nowMs;
                } else if (_nowMs - _lastDisconnectedTimestampMs > kConnectionFailureTimeoutMs) {
                    updateNetworkState(false, true);
                    return;
                }
            }

            _connectionTimerGeneration += 1;
            emit({ {"@type", "set_timer"}, {"token", kConnectionTimerToken}, {"generation", _connectionTimerGeneration}, {"delayMs", 1000} });
        } else if (token == kDisconnectTimerToken) {
            // Debounce: only report a disconnect (and re-check the failed state)
            // once it has persisted for 2s, matching
            // InstanceV2ReferenceImpl::updateIsConnected. Not re-armed - a fresh
            // disconnect schedules its own timer via updateIsConnected.
            if ((int)event["generation"].number_value() != _disconnectReportGeneration) {
                return;
            }
            if (!_isConnected) {
                updateNetworkState(_isConnected, _isFailed);
            }
        }
    } else if (type == "stats") {
        onStats(event);
    } else if (type == "mute") {
        const bool muted = event["muted"].bool_value();
        if (_isMicrophoneMuted != muted) {
            _isMicrophoneMuted = muted;
            emit({ {"@type", "pc_set_track_enabled"}, {"id", "audio0"}, {"enabled", !muted} });
            sendMediaState();
        }
    } else if (type == "battery_low") {
        const bool low = event["low"].bool_value();
        if (_isBatteryLow != low) {
            _isBatteryLow = low;
            sendMediaState();
        }
    } else if (type == "video_capture") {
        // stock setVideoCapture: always remove, re-add for non-screencast capture
        if (_hasVideoTrack) {
            emit({ {"@type", "pc_remove_track"}, {"id", "video0"} });
            _hasVideoTrack = false;
        }
        _hasVideoCapture = event["active"].bool_value() && !event["screencast"].bool_value();
        if (_hasVideoCapture) {
            emit({
                {"@type", "pc_add_transceiver"},
                {"id", "video0"},
                {"kind", "video"},
                {"direction", "sendrecv"},
                {"trackSource", "camera"},
                {"codecPreferences", json11::Json::array{ "H265", "H264" }},
                {"sendEncodings", json11::Json::array{ json11::Json::object{ {"maxBitrateBps", kVideoMaxBitrateBps} } }},
            });
            _hasVideoTrack = true;
        }
        if (_didBeginNegotiation) {
            sendMediaState();
            requestSetLocalDescription();
        }
    } else if (type == "pc_track") {
        if (stringField(event, "kind") == "video") {
            emit({ {"@type", "pc_set_incoming_sink"}, {"mid", stringField(event, "mid")} });
        }
    } else if (type == "stop") {
        handleStop();
    } else if (type == "error") {
        emitLog("host error: " + stringField(event, "message") + " (command: " + stringField(event, "command") + ")");
        if (stringField(event, "command") == "pc_create") {
            updateNetworkState(false, true);
        }
    } else {
        // Unknown event: ignore (ABI rule).
    }
}

void ReferenceCallCore::requestSetLocalDescription() {
    // Stock sendLocalDescription() sets _isMakingOffer before its no-arg SLD
    // (InstanceV2ReferenceImpl.cpp:886); the flag window now also covers the
    // create step, which is strictly safer for collision detection.
    _isMakingOffer = true;
    // Stock's no-arg SLD creates an answer in have-remote-offer and an offer
    // otherwise. _signalingState is current here: OnSignalingChange is posted
    // to the media thread during SRD execution, before the SRD observer's
    // posted completion, so pc_signaling_state always precedes
    // pc_set_remote_done (FIFO).
    const bool asAnswer = (_signalingState == "have-remote-offer" || _signalingState == "have-remote-pranswer");
    emit({ {"@type", asAnswer ? "pc_create_answer" : "pc_create_offer"} });
}

void ReferenceCallCore::sendSignalingMessage(json11::Json::object &&message) {
    mungeOutgoingSignalingMessage(message);
    const std::string data = json11::Json(std::move(message)).dump();
    // Load-bearing log line: the P1/P2 wire diffs key on it (the stock
    // counterpart is InstanceV2ReferenceImpl's "sendSignalingMessage: ").
    emitLog("signaling out: " + data);
    _framing->sendMessage(data);
}

void ReferenceCallCore::mungeOutgoingSignalingMessage(json11::Json::object &message) {
    (void)message;
}

void ReferenceCallCore::onDataChannelEvent(json11::Json const &event) {
    (void)event;
}

std::string ReferenceCallCore::mungeLocalDescription(std::string const &type, std::string const &sdp) {
    (void)type;
    return sdp;
}

void ReferenceCallCore::onIceState(std::string const &state) {
    bool isConnected = (state == "connected" || state == "completed");

    // ICE 'failed' is NOT terminal. Stock InstanceV2ReferenceImpl never sets its
    // failed flag from the ICE state - the 20s watchdog is its only writer - and
    // instead attempts a restart. 18/19 treating it as terminal is exactly the
    // state-semantics divergence that contaminates the substrate A/B.
    if (state == "failed") {
        maybeRestartIce();
    }

    updateIsConnected(isConnected);
}

void ReferenceCallCore::maybeRestartIce() {
    if (_isFailed) {
        return;
    }
    // Only the caller restarts, matching InstanceV2ReferenceImpl - if both sides
    // restart on the same failure they glare.
    if (!_isOutgoing) {
        return;
    }
    if (_lastIceRestartTimestampMs != 0 && _nowMs - _lastIceRestartTimestampMs < kIceRestartMinIntervalMs) {
        return;
    }
    _lastIceRestartTimestampMs = _nowMs;
    emitLog("ICE failed; requesting restart");
    emit({ {"@type", "pc_restart_ice"} });
}

void ReferenceCallCore::updateIsConnected(bool isConnected) {
    if (_isConnected == isConnected) {
        return;
    }
    _isConnected = isConnected;

    if (isConnected) {
        updateNetworkState(_isConnected, _isFailed);
    } else {
        _lastDisconnectedTimestampMs = _nowMs;

        // The legacy ICE state reports a disconnect on a brief receiving timeout,
        // so a blip would otherwise surface as a Reconnecting episode in the
        // uploaded timeline. Only report (and log) the disconnect once it
        // persists, matching InstanceV2ReferenceImpl::updateIsConnected.
        _disconnectReportGeneration += 1;
        emit({ {"@type", "set_timer"}, {"token", kDisconnectTimerToken}, {"generation", _disconnectReportGeneration}, {"delayMs", 2000} });
    }
}

void ReferenceCallCore::onStats(json11::Json const &event) {
    // stock writeStateLogRecords signal-bars heuristic
    const double sendBitrateKbps = event["sendBitrateKbps"].number_value();
    double bitrateNorm = _hasVideoTrack ? 600.0 : 16.0;
    double adjustedQuality = sendBitrateKbps / bitrateNorm;
    adjustedQuality = std::max(0.0, std::min(1.0, adjustedQuality));
    emit({ {"@type", "emit_signal_bars"}, {"bars", (int)(adjustedQuality * 4.0)} });

    BitrateRecord record;
    record.timestampMs = _nowMs;
    record.bitrateKbps = (int32_t)sendBitrateKbps;
    _bitrateRecords.push_back(record);
}

void ReferenceCallCore::handleSignalingData(std::string const &data) {
    std::string parsingError;
    const auto json = json11::Json::parse(data, parsingError);
    if (!json.is_object()) {
        emitLog("Signaling: message must be an object");
        return;
    }
    const auto type = stringField(json, "@type");
    if (type.empty()) {
        emitLog("Signaling: @type is missing");
        return;
    }

    if (type == "offer" || type == "answer") {
        const auto sdp = stringField(json, "sdp");
        if (sdp.empty()) {
            emitLog("Signaling: sdp is missing");
            return;
        }
        handleRemoteSdp(type, sdp);
    } else if (type == "candidate") {
        if (!json["mid"].is_string() || !json["mline"].is_number() || !json["sdp"].is_string()) {
            return;
        }
        json11::Json::object candidate{
            {"@type", "pc_add_ice_candidate"},
            {"mid", json["mid"]},
            {"mline", json["mline"]},
            {"sdp", json["sdp"]},
        };
        if (_haveLocalDescription && _haveRemoteDescription) {
            emit(std::move(candidate));
        } else {
            _pendingRemoteCandidates.push_back(json11::Json(std::move(candidate)));
        }
    } else if (type == "MediaState") {
        handleMediaStateMessage(json);
    } else {
        // Other signaling::Message kinds are not used by the reference protocol.
    }
}

void ReferenceCallCore::handleRemoteSdp(std::string const &type, std::string const &sdp) {
    // stock handleRemoteSdp perfect-negotiation gate, verbatim semantics
    bool isReadyForOffer = !_isMakingOffer && (_signalingState == "stable" || _isSettingRemoteAnswerPending);
    bool isOfferCollision = (type == "offer") && !isReadyForOffer;
    bool ignoreOffer = !_isOutgoing && isOfferCollision;
    if (ignoreOffer) {
        emitLog("Ignoring remote sdp");
        return;
    }

    _isSettingRemoteAnswerPending = (type == "answer");
    emit({ {"@type", "pc_set_remote_description"}, {"sdpType", type}, {"sdp", sdp} });
}

void ReferenceCallCore::handleMediaStateMessage(json11::Json const &message) {
    // wire keys from stock MediaStateMessage_serialize:
    // muted / lowBattery / videoState / screencastState (values inactive|suspended|active)
    const auto audio = message["muted"].bool_value() ? "muted" : "active";

    const auto mapVideo = [](std::string const &value) -> std::string {
        if (value == "suspended") {
            return "paused";
        } else if (value == "active") {
            return "active";
        }
        return "inactive";
    };
    const auto videoState = mapVideo(stringField(message, "videoState"));
    const auto screencastState = mapVideo(stringField(message, "screencastState"));
    // stock: screencast overrides video when active or paused
    const auto effectiveVideo = (screencastState == "active" || screencastState == "paused") ? screencastState : videoState;

    emit({ {"@type", "emit_remote_media_state"}, {"audio", audio}, {"video", effectiveVideo} });
    emit({ {"@type", "emit_remote_battery_low"}, {"low", message["lowBattery"].bool_value()} });
}

void ReferenceCallCore::flushPendingRemoteCandidates() {
    if (_pendingRemoteCandidates.empty()) {
        return;
    }
    if (!_haveLocalDescription || !_haveRemoteDescription) {
        return;
    }
    for (auto &candidate : _pendingRemoteCandidates) {
        json11::Json::object command = candidate.object_items();
        emit(std::move(command));
    }
    _pendingRemoteCandidates.clear();
}

void ReferenceCallCore::sendMediaState() {
    if (!_isDataChannelOpen) {
        return;
    }
    // wire format from stock MediaStateMessage_serialize (Signaling.cpp):
    // keys @type/muted/lowBattery/videoState/videoRotation/screencastState
    json11::Json::object message{
        {"@type", "MediaState"},
        {"muted", _isMicrophoneMuted},
        {"lowBattery", _isBatteryLow},
        {"videoState", (_hasVideoTrack && _hasVideoCapture) ? "active" : "inactive"},
        {"videoRotation", 0},
        {"screencastState", "inactive"},
    };
    emit({ {"@type", "dc_send"}, {"data", json11::Json(std::move(message)).dump()} });
}

void ReferenceCallCore::updateNetworkState(bool isConnected, bool isFailed) {
    _isConnected = isConnected;
    _isFailed = isFailed;

    NetworkStateRecord record;
    record.timestampMs = _nowMs;
    record.isConnected = _isConnected;
    record.isFailed = _isFailed;
    record.connection = _currentConnection;
    if (_networkStateRecords.empty()
        || _networkStateRecords.back().isConnected != record.isConnected
        || _networkStateRecords.back().isFailed != record.isFailed
        || !(_networkStateRecords.back().connection == record.connection)) {
        _networkStateRecords.push_back(record);
    }

    emitMappedState();
}

void ReferenceCallCore::emitMappedState() {
    std::string mappedState;
    if (_isFailed) {
        mappedState = "failed";
    } else if (_isConnected) {
        mappedState = "established";
    } else {
        mappedState = "reconnecting";
    }
    emit({ {"@type", "emit_state"}, {"state", mappedState} });
}

void ReferenceCallCore::handleStop() {
    // stock stop(): coalesce events within 5ms, then serialize "v":3 stats log
    for (int i = (int)_networkStateRecords.size() - 1; i >= 1; i--) {
        if (_networkStateRecords[i].timestampMs - _networkStateRecords[i - 1].timestampMs < 5) {
            _networkStateRecords.erase(_networkStateRecords.begin() + i - 1);
        }
    }

    json11::Json::array networkRecords;
    int64_t baseTimestamp = 0;
    for (const auto &record : _networkStateRecords) {
        json11::Json::object jsonRecord;
        if (baseTimestamp == 0) {
            baseTimestamp = record.timestampMs;
        }
        jsonRecord.insert(std::make_pair("t", json11::Json(std::to_string(record.timestampMs - baseTimestamp))));
        jsonRecord.insert(std::make_pair("c", json11::Json(record.isConnected ? 1 : 0)));
        if (record.connection.is_object()) {
            jsonRecord.insert(std::make_pair("network", record.connection));
        }
        if (record.isFailed) {
            jsonRecord.insert(std::make_pair("failed", json11::Json(1)));
        }
        networkRecords.push_back(json11::Json(std::move(jsonRecord)));
    }

    json11::Json::array bitrateRecords;
    for (const auto &record : _bitrateRecords) {
        bitrateRecords.push_back(json11::Json(json11::Json::object{ {"b", record.bitrateKbps} }));
    }

    json11::Json::object statsLog{
        {"v", 3},
        {"network", std::move(networkRecords)},
        {"bitrate", std::move(bitrateRecords)},
    };
    emit({ {"@type", "stats_log"}, {"json", json11::Json(std::move(statsLog)).dump()} });
    emit({ {"@type", "close"} });
}

} // namespace v2wasm
} // namespace tgcalls

// ============================================================================
// C ABI wrappers
// ============================================================================

struct TgcallsCallCore {
    std::unique_ptr<tgcalls::v2wasm::ReferenceCallCore> impl;
};

extern "C" {

TgcallsCallCore *tgcalls_core_create(const char *configJson, TgcallsCoreEmitFn emitFn, void *userData) {
    if (!emitFn) {
        return nullptr;
    }
    std::string parsingError;
    const auto config = json11::Json::parse(configJson ? configJson : "", parsingError);

    auto core = new TgcallsCallCore();
    core->impl = std::make_unique<tgcalls::v2wasm::ReferenceCallCore>(config, [emitFn, userData](json11::Json::object &&command) {
        const std::string serialized = json11::Json(std::move(command)).dump();
        emitFn(userData, (const uint8_t *)serialized.data(), serialized.size());
    });
    return core;
}

void tgcalls_core_on_event(TgcallsCallCore *core, const uint8_t *data, size_t len) {
    if (!core || !core->impl || !data) {
        return;
    }
    std::string parsingError;
    const auto event = json11::Json::parse(std::string((const char *)data, len), parsingError);
    if (!event.is_object()) {
        return;
    }
    core->impl->onEvent(event);
}

void tgcalls_core_destroy(TgcallsCallCore *core) {
    delete core;
}

} // extern "C"
