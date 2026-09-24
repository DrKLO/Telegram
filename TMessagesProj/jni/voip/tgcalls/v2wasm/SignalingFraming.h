#ifndef TGCALLS_V2WASM_SIGNALING_FRAMING_H
#define TGCALLS_V2WASM_SIGNALING_FRAMING_H

// Core-side signaling framing: everything between JSON messages and the
// plaintext packets (seq(4, network order) || body) that the host seals.
// Wire 11.0.0: a plain incrementing seq plus a gzip'd body. There is no
// retransmission here by design — the transport underneath is reliable
// (SCTP data channel, or MTProto for server-relayed signaling), matching
// stock v2/InstanceV2ReferenceImpl.cpp:775-792.
// WASM discipline: std + CoreGzip (host compression service) only.

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace tgcalls {
namespace v2wasm {

class SignalingFraming {
public:
    struct Delegate {
        std::function<void(std::vector<uint8_t> &&packet)> sendPacket; // full plaintext packet incl. seq
        std::function<void(std::string &&message)> deliverMessage;     // decoded JSON message
        std::function<void(std::string const &line)> log;              // stock-format framing log lines
        std::function<int64_t()> nowMs;                                // core clock (event nowMs)
    };

    explicit SignalingFraming(Delegate delegate);

    void sendMessage(std::string const &message);
    void receivePacket(std::vector<uint8_t> const &packet);

private:
    Delegate _delegate;
    uint32_t _counter = 0;
};

} // namespace v2wasm
} // namespace tgcalls

#endif
