#ifndef TGCALLS_SIGNALING_CONNECTION_H_
#define TGCALLS_SIGNALING_CONNECTION_H_

#include <memory>
#include <vector>

namespace webrtc {
}

namespace tgcalls {

class SignalingConnection : public std::enable_shared_from_this<SignalingConnection> {
public:
    SignalingConnection();
    virtual ~SignalingConnection() = default;

    virtual void start() = 0;

    virtual void send(const std::vector<uint8_t> &data) = 0;
    virtual void receiveExternal(const std::vector<uint8_t> &data) {
    }
};

}  // namespace tgcalls

#endif  // TGCALLS_SIGNALING_CONNECTION_H_
