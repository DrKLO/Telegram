#ifndef TGCALLS_SIGNALING_KCP_CONNECTION_H_
#define TGCALLS_SIGNALING_KCP_CONNECTION_H_

#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/byte_buffer.h"
#include "media/base/media_channel.h"

#include <vector>

#include <absl/types/optional.h>

#include "StaticThreads.h"
#include "SignalingConnection.h"

#include "ikcp.h"

namespace rtc {
class Socket;
}

namespace cricket {
class SctpTransportFactory;
class SctpTransportInternal;
};

namespace tgcalls {

class SignalingPacketTransport;

class SignalingKcpConnection : public sigslot::has_slots<>, public SignalingConnection, public std::enable_shared_from_this<SignalingKcpConnection> {
public:
    SignalingKcpConnection(std::shared_ptr<Threads> threads, std::function<void(const std::vector<uint8_t> &)> onIncomingData, std::function<void(const std::vector<uint8_t> &)> emitData);
    virtual ~SignalingKcpConnection();

    virtual void receiveExternal(const std::vector<uint8_t> &data) override;
    virtual void start() override;
    virtual void send(const std::vector<uint8_t> &data) override;
    
private:
    void scheduleInternalUpdate(int timeoutMs);
    void performInternalUpdate();
    static int udpOutput(const char *buf, int len, ikcpcb *kcp, void *user);

private:
    std::shared_ptr<Threads> _threads;
    std::function<void(const std::vector<uint8_t> &)> _emitData;
    std::function<void(const std::vector<uint8_t> &)> _onIncomingData;

    ikcpcb *_kcp = nullptr;
    std::vector<uint8_t> _receiveBuffer;
};

}  // namespace tgcalls

#endif  // TGCALLS_SIGNALING_SCTP_CONNECTION_H_
