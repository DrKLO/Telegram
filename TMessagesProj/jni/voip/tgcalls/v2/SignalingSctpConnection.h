#ifndef TGCALLS_SIGNALING_SCTP_CONNECTION_H_
#define TGCALLS_SIGNALING_SCTP_CONNECTION_H_

#ifdef WEBRTC_WIN
#include <WinSock2.h>
#endif // WEBRTC_WIN

#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/byte_buffer.h"
#include "media/base/media_channel.h"

#include <vector>

#include <absl/types/optional.h>

#include "StaticThreads.h"
#include "SignalingConnection.h"

namespace rtc {
class Socket;
}

namespace cricket {
class SctpTransportFactory;
class SctpTransportInternal;
};

namespace tgcalls {

class SignalingPacketTransport;

class SignalingSctpConnection : public sigslot::has_slots<>, public SignalingConnection, public webrtc::DataChannelSink {
private:
    struct PacketReadState {
        rtc::CopyOnWriteBuffer headerData;
        int remainingHeaderSize = 0;
        bool isHeaderCompleted = false;

        rtc::CopyOnWriteBuffer data;
        int remainingDataSize = 0;
        bool isDataCompleted = false;
    };

public:
    SignalingSctpConnection(std::shared_ptr<Threads> threads, std::function<void(const std::vector<uint8_t> &)> onIncomingData, std::function<void(const std::vector<uint8_t> &)> emitData);
    virtual ~SignalingSctpConnection();

    virtual void receiveExternal(const std::vector<uint8_t> &data) override;
    virtual void start() override;
    virtual void send(const std::vector<uint8_t> &data) override;

    virtual void OnDataReceived(int channel_id,
                                webrtc::DataMessageType type,
                                const rtc::CopyOnWriteBuffer& buffer) override;
    virtual void OnReadyToSend() override;
    virtual void OnTransportClosed(webrtc::RTCError error) override;

    // Unused
    virtual void OnChannelClosing(int channel_id) override{}
    virtual void OnChannelClosed(int channel_id) override{}

private:
    std::shared_ptr<Threads> _threads;
    std::function<void(const std::vector<uint8_t> &)> _emitData;
    std::function<void(const std::vector<uint8_t> &)> _onIncomingData;

    std::unique_ptr<SignalingPacketTransport> _packetTransport;
    std::unique_ptr<cricket::SctpTransportFactory> _sctpTransportFactory;
    std::unique_ptr<cricket::SctpTransportInternal> _sctpTransport;
    
    bool _isReadyToSend = false;
    std::vector<std::vector<uint8_t>> _pendingData;
};

}  // namespace tgcalls

#endif  // TGCALLS_SIGNALING_SCTP_CONNECTION_H_
