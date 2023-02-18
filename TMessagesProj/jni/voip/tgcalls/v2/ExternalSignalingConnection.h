#ifndef TGCALLS_EXTERNAL_SIGNALING_CONNECTION_H_
#define TGCALLS_EXTERNAL_SIGNALING_CONNECTION_H_

#include <functional>
#include <vector>

#include "SignalingConnection.h"

namespace rtc {
class AsyncPacketSocket;
}

namespace tgcalls {

class ExternalSignalingConnection : public SignalingConnection {
public:
    ExternalSignalingConnection(std::function<void(const std::vector<uint8_t> &)> onIncomingData, std::function<void(const std::vector<uint8_t> &)> emitData);
    virtual ~ExternalSignalingConnection();

    virtual void start() override;
    
    virtual void send(const std::vector<uint8_t> &data) override;
    virtual void receiveExternal(const std::vector<uint8_t> &data) override;

private:
    std::function<void(const std::vector<uint8_t> &)> _onIncomingData;
    std::function<void(const std::vector<uint8_t> &)> _emitData;
};

}  // namespace tgcalls

#endif  // TGCALLS_EXTERNAL_SIGNALING_CONNECTION_H_
