#ifndef TGCALLS_V2WASM_VARIANT_CALL_CORE_H
#define TGCALLS_V2WASM_VARIANT_CALL_CORE_H

// Demo variant core: proves call behavior ships as a .wasm only. Deviations
// from the reference core (all interop-safe against stock peers):
//   1. Opus fmtp munge on local descriptions (useinbandfec/usedtx/
//      maxaveragebitrate).
//   2. BWE-driven sender bitrate cap via pc_set_parameters.
//   3. Periodic ICE restart (outgoing side, ~every 7 stats ticks ~= 7 s).
//   4. Signal bars from RTT/loss instead of the bitrate heuristic.
//   5. Signaling padding: an unknown "_pad" key stuffed into every outgoing
//      candidate message via mungeOutgoingSignalingMessage — stock peers
//      ignore unknown keys inside known message types.
//   6. V1 keepalive packets on the 10.0.0 wire (bare empty framing packet
//      every 5 stats ticks) — stock peers parse and discard them.
//   7. An "exp0" negotiated data-channel ping/pong exercised only between two
//      variant peers (a stock peer never opens the channel).
//   8. One-shot APM toggle (set_audio_processing) and a benign
//      pc_set_configuration once the call has run for a few stats ticks.
// WASM discipline: include only ReferenceCallCore.h, json11, CoreBase64.h
// and C++17 std. This .cpp is compiled ONLY by the variant_core_wasm
// genrule — never in native source lists.

#include "v2wasm/ReferenceCallCore.h"

namespace tgcalls {
namespace v2wasm {

class VariantCallCore : public ReferenceCallCore {
public:
    VariantCallCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit);

protected:
    std::string mungeLocalDescription(std::string const &type, std::string const &sdp) override;
    void onStats(json11::Json const &event) override;
    void mungeOutgoingSignalingMessage(json11::Json::object &message) override;
    void onDataChannelEvent(json11::Json const &event) override;

private:
    int _statsTicks = 0;
    int _iceRestarts = 0;
    int _lastCapKbps = 0;
    int _padCount = 0;
    bool _apmApplied = false;
    bool _configApplied = false;
};

} // namespace v2wasm
} // namespace tgcalls

#endif
