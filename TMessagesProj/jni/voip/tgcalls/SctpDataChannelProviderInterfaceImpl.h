#ifndef TGCALLS_SCTP_DATA_CHANNEL_PROVIDER_IMPL_H
#define TGCALLS_SCTP_DATA_CHANNEL_PROVIDER_IMPL_H

#include "media/sctp/sctp_transport_factory.h"
#include "api/turn_customizer.h"
#include "api/data_channel_interface.h"
#include "pc/sctp_data_channel.h"
#include "media/sctp/sctp_transport_factory.h"
#include "pc/sctp_transport.h"

#include "StaticThreads.h"

namespace cricket {
class DtlsTransport;
} // namespace cricket

namespace tgcalls {

class SctpDataChannelProviderInterfaceImpl : public sigslot::has_slots<>, public webrtc::SctpDataChannelControllerInterface, public webrtc::DataChannelObserver, public webrtc::DataChannelSink {
public:
    SctpDataChannelProviderInterfaceImpl(
        cricket::DtlsTransport *transportChannel,
        bool isOutgoing,
        std::function<void(bool)> onStateChanged,
        std::function<void()> onTerminated,
        std::function<void(std::string const &)> onMessageReceived,
        std::shared_ptr<Threads> threads
    );
    virtual ~SctpDataChannelProviderInterfaceImpl();

    void updateIsConnected(bool isConnected);
    void sendDataChannelMessage(std::string const &message);

    virtual void OnStateChange() override;
    virtual void OnMessage(const webrtc::DataBuffer& buffer) override;
    virtual bool SendData(
        int sid,
        const webrtc::SendDataParams& params,
        const rtc::CopyOnWriteBuffer& payload,
        cricket::SendDataResult* result) override;
    virtual bool ConnectDataChannel(webrtc::SctpDataChannel *data_channel) override;
    virtual void DisconnectDataChannel(webrtc::SctpDataChannel* data_channel) override;
    virtual void AddSctpDataStream(int sid) override;
    virtual void RemoveSctpDataStream(int sid) override;
    virtual bool ReadyToSendData() const override;

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
    std::function<void(bool)> _onStateChanged;
    std::function<void()> _onTerminated;
    std::function<void(std::string const &)> _onMessageReceived;

    std::unique_ptr<cricket::SctpTransportFactory> _sctpTransportFactory;
    std::unique_ptr<cricket::SctpTransportInternal> _sctpTransport;
    rtc::scoped_refptr<webrtc::SctpDataChannel> _dataChannel;

    bool _isSctpTransportStarted = false;
    bool _isDataChannelOpen = false;

};

} // namespace tgcalls

#endif
