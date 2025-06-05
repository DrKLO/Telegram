#ifndef TGCALLS_SCTP_DATA_CHANNEL_PROVIDER_IMPL_H
#define TGCALLS_SCTP_DATA_CHANNEL_PROVIDER_IMPL_H

#include "rtc_base/weak_ptr.h"
#include "api/turn_customizer.h"
#include "api/data_channel_interface.h"
#include "pc/sctp_data_channel.h"
#include "media/sctp/sctp_transport_factory.h"

#include "StaticThreads.h"

namespace cricket {
class DtlsTransport;
} // namespace cricket

namespace tgcalls {

class SctpDataChannelProviderInterfaceImpl : public sigslot::has_slots<>, public webrtc::SctpDataChannelControllerInterface, public webrtc::DataChannelObserver, public webrtc::DataChannelSink {
public:
    SctpDataChannelProviderInterfaceImpl(
        rtc::PacketTransportInternal *transportChannel,
        bool isOutgoing,
        std::function<void(bool)> onStateChanged,
        std::function<void()> onTerminated,
        std::function<void(std::string const &)> onMessageReceived,
        std::shared_ptr<Threads> threads
    );
    virtual ~SctpDataChannelProviderInterfaceImpl();

    virtual bool IsOkToCallOnTheNetworkThread() override;
    
    void updateIsConnected(bool isConnected);
    void sendDataChannelMessage(std::string const &message);

    virtual void OnStateChange() override;
    virtual void OnMessage(const webrtc::DataBuffer& buffer) override;
    virtual webrtc::RTCError SendData(
        webrtc::StreamId sid,
        const webrtc::SendDataParams& params,
        const rtc::CopyOnWriteBuffer& payload) override;
    
    virtual void AddSctpDataStream(webrtc::StreamId sid) override;
    virtual void RemoveSctpDataStream(webrtc::StreamId sid) override;
    virtual void OnChannelStateChanged(webrtc::SctpDataChannel *data_channel, webrtc::DataChannelInterface::DataState state) override;

    virtual void OnDataReceived(int channel_id,
                                webrtc::DataMessageType type,
                                const rtc::CopyOnWriteBuffer& buffer) override;
    virtual void OnReadyToSend() override;
    virtual void OnTransportClosed(webrtc::RTCError error) override;

    // Unused
    virtual void OnChannelClosing(int channel_id) override{}
    virtual void OnChannelClosed(int channel_id) override{}

private:
    rtc::WeakPtrFactory<SctpDataChannelProviderInterfaceImpl> _weakFactory;
    std::shared_ptr<Threads> _threads;
    std::function<void(bool)> _onStateChanged;
    std::function<void()> _onTerminated;
    std::function<void(std::string const &)> _onMessageReceived;

    std::unique_ptr<cricket::SctpTransportFactory> _sctpTransportFactory;
    std::unique_ptr<cricket::SctpTransportInternal> _sctpTransport;
    webrtc::scoped_refptr<webrtc::SctpDataChannel> _dataChannel;

    bool _isSctpTransportStarted = false;
    bool _isDataChannelOpen = false;

};

} // namespace tgcalls

#endif
