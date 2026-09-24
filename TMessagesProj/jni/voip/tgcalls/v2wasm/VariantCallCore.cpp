#include "v2wasm/VariantCallCore.h"

#include "v2wasm/CoreBase64.h"

#include <algorithm>
#include <cstdlib>
#include <string>

namespace tgcalls {
namespace v2wasm {

namespace {

constexpr int kIceRestartEveryStatsTicks = 7; // stats tick ~= 1 s
constexpr double kCapFractionOfBwe = 0.8;
constexpr int kMinCapKbps = 24;
constexpr int kMaxCapKbps = 1500;

// Append DTX/FEC/bitrate params to the opus fmtp line (CRLF line ends).
// Returns sdp unchanged if opus or its fmtp line is absent.
std::string mungeOpusFmtp(std::string const &sdp) {
    const std::string rtpmapKey = "a=rtpmap:";
    std::string opusPt;
    size_t pos = 0;
    while (pos < sdp.size()) {
        size_t end = sdp.find("\r\n", pos);
        if (end == std::string::npos) {
            end = sdp.size();
        }
        const std::string line = sdp.substr(pos, end - pos);
        if (line.rfind(rtpmapKey, 0) == 0 && line.find(" opus/") != std::string::npos) {
            const size_t space = line.find(' ');
            opusPt = line.substr(rtpmapKey.size(), space - rtpmapKey.size());
            break;
        }
        pos = end + 2;
    }
    if (opusPt.empty()) {
        return sdp;
    }
    const std::string fmtpKey = "a=fmtp:" + opusPt + " ";
    const size_t fmtpPos = sdp.find(fmtpKey);
    if (fmtpPos == std::string::npos) {
        return sdp;
    }
    size_t lineEnd = sdp.find("\r\n", fmtpPos);
    if (lineEnd == std::string::npos) {
        lineEnd = sdp.size();
    }
    const std::string line = sdp.substr(fmtpPos, lineEnd - fmtpPos);
    std::string addition;
    if (line.find("useinbandfec") == std::string::npos) {
        addition += ";useinbandfec=1";
    }
    if (line.find("usedtx") == std::string::npos) {
        addition += ";usedtx=1";
    }
    if (line.find("maxaveragebitrate") == std::string::npos) {
        addition += ";maxaveragebitrate=24000";
    }
    std::string munged = sdp;
    munged.insert(lineEnd, addition);
    return munged;
}

} // namespace

VariantCallCore::VariantCallCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit) :
ReferenceCallCore(config, std::move(emit)) {
    emitLog("variant: core active");
    // Experiment channel: negotiated (both sides create id 5 locally; no
    // in-band announcement, so a stock peer simply never opens it — the
    // strongest form of non-interference).
    // NOTE: qualified as this->emit (not bare emit) — the ctor parameter
    // named `emit` shadows the inherited member function in this scope, and
    // it was already moved-from via std::move(emit) in the mem-initializer
    // list above; bare `emit(...)` here would call the moved-from (empty)
    // std::function and crash. See ReferenceCallCore's own ctor for the same
    // pattern.
    this->emit({ {"@type", "pc_create_data_channel"}, {"label", "exp0"}, {"negotiated", true}, {"id", 5} });
}

std::string VariantCallCore::mungeLocalDescription(std::string const &type, std::string const &sdp) {
    const std::string munged = mungeOpusFmtp(sdp);
    if (munged != sdp) {
        emitLog("variant: munge applied (" + type + ")");
    }
    return munged;
}

void VariantCallCore::mungeOutgoingSignalingMessage(json11::Json::object &message) {
    const auto typeIt = message.find("@type");
    if (typeIt == message.end() || !typeIt->second.is_string() || typeIt->second.string_value() != "candidate") {
        return;
    }
    // Traffic-shape padding demo via an unknown JSON key (stock signaling
    // parsers ignore unknown fields inside known message types). The core has
    // no entropy source, so an LCG stands in — the point is the seam + stock
    // tolerance, not cryptographic-quality cover traffic.
    _padCount += 1;
    uint32_t state = 2654435761u * (uint32_t)_padCount;
    static const char kChars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    std::string pad;
    pad.reserve(256);
    for (int i = 0; i < 256; i++) {
        state = state * 1664525u + 1013904223u;
        pad.push_back(kChars[(state >> 24) % 62]);
    }
    message["_pad"] = pad;
    emitLog("variant: pad " + std::to_string(_padCount));
}

void VariantCallCore::onDataChannelEvent(json11::Json const &event) {
    const auto type = event["@type"].string_value();
    if (event["label"].string_value() != "exp0") {
        return;
    }
    if (type == "dc_state" && event["open"].bool_value() && _isOutgoing) {
        emit({ {"@type", "dc_send"}, {"label", "exp0"}, {"data", "ping 1"} });
    } else if (type == "dc_message") {
        // Pongs travel binary (dataB64) to exercise the binary dc path
        // end-to-end (host encode -> SCTP binary -> host decode -> core
        // parse); pings stay text. Materialize the payload from whichever of
        // data/dataB64 is present before running the ping/pong logic.
        std::string payload;
        if (event["data"].is_string()) {
            payload = event["data"].string_value();
        } else if (event["dataB64"].is_string()) {
            const auto decoded = base64Decode(event["dataB64"].string_value());
            if (decoded) {
                payload.assign(decoded->begin(), decoded->end());
            }
        }
        if (payload.rfind("ping ", 0) == 0) {
            const int n = std::atoi(payload.c_str() + 5);
            const std::string pong = "pong " + std::to_string(n);
            emit({ {"@type", "dc_send"}, {"label", "exp0"}, {"dataB64", base64Encode(std::vector<uint8_t>(pong.begin(), pong.end()))} });
            emitLog("variant: dc pong " + std::to_string(n));
        } else if (payload.rfind("pong ", 0) == 0) {
            const int n = std::atoi(payload.c_str() + 5);
            emitLog("variant: dc pong " + std::to_string(n));
            if (n < 5) {
                emit({ {"@type", "dc_send"}, {"label", "exp0"}, {"data", "ping " + std::to_string(n + 1)} });
            }
        }
    }
}

void VariantCallCore::onStats(json11::Json const &event) {
    // Keep the reference bitrate record so the stats log stays well-formed.
    BitrateRecord record;
    record.timestampMs = _nowMs;
    record.bitrateKbps = (int32_t)event["sendBitrateKbps"].number_value();
    _bitrateRecords.push_back(record);

    // Signal bars from RTT/loss instead of the bitrate heuristic.
    const auto &transport = event["transport"];
    const double rttMs = transport["rttMs"].is_number() ? transport["rttMs"].number_value() : -1.0;
    const auto &audioSend = event["audio"]["send"];
    const double lossFraction = audioSend["remoteLossFraction"].is_number() ? audioSend["remoteLossFraction"].number_value() : -1.0;
    int bars = 4;
    if (rttMs > 400.0) {
        bars -= 2;
    } else if (rttMs > 150.0) {
        bars -= 1;
    }
    if (lossFraction > 0.1) {
        bars -= 2;
    } else if (lossFraction > 0.02) {
        bars -= 1;
    }
    bars = std::max(0, std::min(4, bars));
    emit({ {"@type", "emit_signal_bars"}, {"bars", bars} });

    // BWE-driven sender cap: 80% of available outgoing bitrate, clamped;
    // re-emitted only on >10% change to avoid SetParameters spam.
    if (transport["availableOutgoingKbps"].is_number()) {
        int capKbps = (int)(transport["availableOutgoingKbps"].number_value() * kCapFractionOfBwe);
        capKbps = std::max(kMinCapKbps, std::min(kMaxCapKbps, capKbps));
        if (_lastCapKbps == 0 || std::abs(capKbps - _lastCapKbps) * 10 > _lastCapKbps) {
            _lastCapKbps = capKbps;
            const int audioCapBps = std::min(capKbps * 1024, 32 * 1024);
            emit({
                {"@type", "pc_set_parameters"},
                {"id", "audio0"},
                {"encodings", json11::Json::array{ json11::Json::object{ {"maxBitrateBps", audioCapBps} } }},
            });
            if (_hasVideoTrack) {
                emit({
                    {"@type", "pc_set_parameters"},
                    {"id", "video0"},
                    {"encodings", json11::Json::array{ json11::Json::object{ {"maxBitrateBps", capKbps * 1024} } }},
                });
            }
            emitLog("variant: cap " + std::to_string(capKbps) + " kbps");
        }
    }

    // Periodic ICE restart: outgoing side only (keeps variant-vs-variant runs
    // to one restarter), once connected. Recovery flows through the normal
    // perfect-negotiation offer path.
    _statsTicks += 1;
    if (_isOutgoing && _isConnected && _statsTicks % kIceRestartEveryStatsTicks == 0) {
        _iceRestarts += 1;
        emit({ {"@type", "pc_restart_ice"} });
        emitLog("variant: ice_restart " + std::to_string(_iceRestarts));
    }

    // Session-config demos: one-shot APM toggle and a benign SetConfiguration.
    if (!_apmApplied && _statsTicks >= 10) {
        _apmApplied = true;
        emit({ {"@type", "set_audio_processing"}, {"noiseSuppression", false}, {"autoGainControl", false} });
        emitLog("variant: apm applied");
    }
    if (!_configApplied && _statsTicks >= 12) {
        _configApplied = true;
        // NOTE: deviates from the brief's literal {"candidatePoolSize", 2} —
        // WebRTC's PeerConnection::SetConfiguration rejects any
        // ice_candidate_pool_size change once SetLocalDescription has been
        // called (peer_connection.cc: ValidateIceCandidatePoolSize), and by
        // the time onStats reaches this tick the call is already connected
        // (SLD long done), so that command would deterministically fail
        // every run with "Can't change candidate pool size after calling
        // SetLocalDescription." iceTransportsType has no such post-SLD
        // guard and is a true no-op here (the reference core already
        // requests "all" for P2P mode at pc_create) — a config command that
        // is actually benign and always succeeds.
        emit({ {"@type", "pc_set_configuration"}, {"iceTransportsType", "all"} });
        emitLog("variant: config applied");
    }
}

} // namespace v2wasm
} // namespace tgcalls
