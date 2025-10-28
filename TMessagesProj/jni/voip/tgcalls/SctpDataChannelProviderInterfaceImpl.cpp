#include "SctpDataChannelProviderInterfaceImpl.h"

#include "p2p/base/dtls_transport.h"
#include "api/transport/field_trial_based_config.h"
#include "FieldTrialsConfig.h"

namespace tgcalls {

SctpDataChannelProviderInterfaceImpl::SctpDataChannelProviderInterfaceImpl(
    rtc::PacketTransportInternal *transportChannel,
    bool isOutgoing,
    std::function<void(bool)> onStateChanged,
    std::function<void()> onTerminated,
    std::function<void(std::string const &)> onMessageReceived,
    std::shared_ptr<Threads> threads
) :
_weakFactory(this),
_threads(std::move(threads)),
_onStateChanged(onStateChanged),
_onTerminated(onTerminated),
_onMessageReceived(onMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _sctpTransportFactory.reset(new cricket::SctpTransportFactory(_threads->getNetworkThread()));

    _sctpTransport = _sctpTransportFactory->CreateSctpTransport(transportChannel);
    _sctpTransport->SetDataChannelSink(this);

    // TODO: should we disconnect the data channel sink?

    webrtc::InternalDataChannelInit dataChannelInit;
    dataChannelInit.id = 0;
    dataChannelInit.open_handshake_role = isOutgoing ? webrtc::InternalDataChannelInit::kOpener : webrtc::InternalDataChannelInit::kAcker;
    
    _dataChannel = webrtc::SctpDataChannel::Create(
        _weakFactory.GetWeakPtr(),
        "data",
        true,
        dataChannelInit,
        _threads->getNetworkThread(),
        _threads->getNetworkThread()
    );

    _dataChannel->RegisterObserver(this);
    
    AddSctpDataStream(webrtc::StreamId(0));
}

SctpDataChannelProviderInterfaceImpl::~SctpDataChannelProviderInterfaceImpl() {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _weakFactory.InvalidateWeakPtrs();

    _dataChannel->UnregisterObserver();
    _dataChannel->Close();
    _dataChannel = nullptr;

    _sctpTransport = nullptr;
    _sctpTransportFactory.reset();
}

bool SctpDataChannelProviderInterfaceImpl::IsOkToCallOnTheNetworkThread() {
    return true;
}

void SctpDataChannelProviderInterfaceImpl::sendDataChannelMessage(std::string const &message) {
    assert(_threads->getNetworkThread()->IsCurrent());

    if (_isDataChannelOpen) {
        RTC_LOG(LS_INFO) << "Outgoing DataChannel message: " << message;

        webrtc::DataBuffer buffer(message);
        _dataChannel->Send(buffer);
    } else {
        RTC_LOG(LS_INFO) << "Could not send an outgoing DataChannel message: the channel is not open";
    }
}

void SctpDataChannelProviderInterfaceImpl::OnStateChange() {
    assert(_threads->getNetworkThread()->IsCurrent());

    auto state = _dataChannel->state();
    bool isDataChannelOpen = state == webrtc::DataChannelInterface::DataState::kOpen;
    if (_isDataChannelOpen != isDataChannelOpen) {
        _isDataChannelOpen = isDataChannelOpen;
        _onStateChanged(_isDataChannelOpen);
    }
}

void SctpDataChannelProviderInterfaceImpl::OnMessage(const webrtc::DataBuffer& buffer) {
    assert(_threads->getNetworkThread()->IsCurrent());

    if (!buffer.binary) {
        std::string messageText(buffer.data.data(), buffer.data.data() + buffer.data.size());
        RTC_LOG(LS_INFO) << "Incoming DataChannel message: " << messageText;

        _onMessageReceived(messageText);
    }
}

void SctpDataChannelProviderInterfaceImpl::updateIsConnected(bool isConnected) {
    assert(_threads->getNetworkThread()->IsCurrent());

    if (isConnected) {
        if (!_isSctpTransportStarted) {
            _isSctpTransportStarted = true;
            _sctpTransport->Start(5000, 5000, 262144);
        }
    }
}

void SctpDataChannelProviderInterfaceImpl::OnReadyToSend() {
    assert(_threads->getNetworkThread()->IsCurrent());

    _dataChannel->OnTransportReady();
}

void SctpDataChannelProviderInterfaceImpl::OnTransportClosed(webrtc::RTCError error) {
    assert(_threads->getNetworkThread()->IsCurrent());

    if (_onTerminated) {
        _onTerminated();
    }
}

void SctpDataChannelProviderInterfaceImpl::OnDataReceived(int channel_id, webrtc::DataMessageType type, const rtc::CopyOnWriteBuffer& buffer) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _dataChannel->OnDataReceived(type, buffer);
}

webrtc::RTCError SctpDataChannelProviderInterfaceImpl::SendData(
    webrtc::StreamId sid,
    const webrtc::SendDataParams& params,
    const rtc::CopyOnWriteBuffer& payload
) {
    assert(_threads->getNetworkThread()->IsCurrent());

    return _sctpTransport->SendData(sid.stream_id_int(), params, payload);
}

void SctpDataChannelProviderInterfaceImpl::AddSctpDataStream(webrtc::StreamId sid) {
  assert(_threads->getNetworkThread()->IsCurrent());

    _sctpTransport->OpenStream(sid.stream_id_int());
}

void SctpDataChannelProviderInterfaceImpl::RemoveSctpDataStream(webrtc::StreamId sid) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _threads->getNetworkThread()->BlockingCall([this, sid]() {
        _sctpTransport->ResetStream(sid.stream_id_int());
    });
}

void SctpDataChannelProviderInterfaceImpl::OnChannelStateChanged(webrtc::SctpDataChannel *data_channel, webrtc::DataChannelInterface::DataState state) {
    
}

}
