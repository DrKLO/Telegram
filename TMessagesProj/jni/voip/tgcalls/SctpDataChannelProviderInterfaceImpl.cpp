#include "SctpDataChannelProviderInterfaceImpl.h"

#include "p2p/base/dtls_transport.h"

namespace tgcalls {

SctpDataChannelProviderInterfaceImpl::SctpDataChannelProviderInterfaceImpl(
    cricket::DtlsTransport *transportChannel,
    bool isOutgoing,
    std::function<void(bool)> onStateChanged,
    std::function<void()> onTerminated,
    std::function<void(std::string const &)> onMessageReceived,
    std::shared_ptr<Threads> threads
) :
_threads(std::move(threads)),
_onStateChanged(onStateChanged),
_onTerminated(onTerminated),
_onMessageReceived(onMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _sctpTransportFactory.reset(new cricket::SctpTransportFactory(_threads->getNetworkThread()));

    _sctpTransport = _sctpTransportFactory->CreateSctpTransport(transportChannel);
    _sctpTransport->SignalReadyToSendData.connect(this, &SctpDataChannelProviderInterfaceImpl::sctpReadyToSendData);
    _sctpTransport->SignalDataReceived.connect(this, &SctpDataChannelProviderInterfaceImpl::sctpDataReceived);
    _sctpTransport->SignalClosedAbruptly.connect(this, &SctpDataChannelProviderInterfaceImpl::sctpClosedAbruptly);

    webrtc::InternalDataChannelInit dataChannelInit;
    dataChannelInit.id = 0;
    dataChannelInit.open_handshake_role = isOutgoing ? webrtc::InternalDataChannelInit::kOpener : webrtc::InternalDataChannelInit::kAcker;
    _dataChannel = webrtc::SctpDataChannel::Create(
        this,
        "data",
        dataChannelInit,
        _threads->getNetworkThread(),
        _threads->getNetworkThread()
    );

    _dataChannel->RegisterObserver(this);
}


SctpDataChannelProviderInterfaceImpl::~SctpDataChannelProviderInterfaceImpl() {
    assert(_threads->getNetworkThread()->IsCurrent());

    _dataChannel->UnregisterObserver();
    _dataChannel->Close();
    _dataChannel = nullptr;

    _sctpTransport = nullptr;
    _sctpTransportFactory.reset();
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

void SctpDataChannelProviderInterfaceImpl::sctpReadyToSendData() {
    assert(_threads->getNetworkThread()->IsCurrent());

    _dataChannel->OnTransportReady(true);
}

void SctpDataChannelProviderInterfaceImpl::sctpClosedAbruptly() {
    assert(_threads->getNetworkThread()->IsCurrent());

    if (_onTerminated) {
        _onTerminated();
    }
}

void SctpDataChannelProviderInterfaceImpl::sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _dataChannel->OnDataReceived(params, buffer);
}

bool SctpDataChannelProviderInterfaceImpl::SendData(int sid, const webrtc::SendDataParams& params, const rtc::CopyOnWriteBuffer& payload, cricket::SendDataResult* result) {
    assert(_threads->getNetworkThread()->IsCurrent());

    return _sctpTransport->SendData(sid, params, payload);
}

bool SctpDataChannelProviderInterfaceImpl::ConnectDataChannel(webrtc::SctpDataChannel *data_channel) {
    assert(_threads->getNetworkThread()->IsCurrent());

    return true;
}

void SctpDataChannelProviderInterfaceImpl::DisconnectDataChannel(webrtc::SctpDataChannel* data_channel) {
    assert(_threads->getNetworkThread()->IsCurrent());

    return;
}

void SctpDataChannelProviderInterfaceImpl::AddSctpDataStream(int sid) {
  assert(_threads->getNetworkThread()->IsCurrent());

    _sctpTransport->OpenStream(sid);
}

void SctpDataChannelProviderInterfaceImpl::RemoveSctpDataStream(int sid) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _threads->getNetworkThread()->Invoke<void>(RTC_FROM_HERE, [this, sid]() {
        _sctpTransport->ResetStream(sid);
    });
}

bool SctpDataChannelProviderInterfaceImpl::ReadyToSendData() const {
    assert(_threads->getNetworkThread()->IsCurrent());

    return _sctpTransport->ReadyToSendData();
}

}
