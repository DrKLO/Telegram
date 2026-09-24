#include "v2/SignalingSctpConnection.h"

#include <random>

#include "rtc_base/async_tcp_socket.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "rtc_base/logging.h"
#include "p2p/base/packet_transport_internal.h"
#include "media/sctp/sctp_transport_factory.h"
#include "media/sctp/dcsctp_transport.h"
#include "v2/CustomDcSctpSocket.h"
#include "net/dcsctp/public/dcsctp_socket_factory.h"
#include "system_wrappers/include/clock.h"

#include "FieldTrialsConfig.h"

namespace tgcalls {

// Factory that creates CustomDcSctpSocket (with the t1 timer backoff cap fix)
// instead of the stock DcSctpSocket, with optional timer overrides for the
// signaling channel.
class CustomDcSctpSocketFactory : public dcsctp::DcSctpSocketFactory {
public:
    explicit CustomDcSctpSocketFactory(SignalingSctpConnection::Options opts = {})
        : _opts(opts) {}

    std::unique_ptr<dcsctp::DcSctpSocketInterface> Create(
        absl::string_view log_prefix,
        dcsctp::DcSctpSocketCallbacks& callbacks,
        std::unique_ptr<dcsctp::PacketObserver> packet_observer,
        const dcsctp::DcSctpOptions& options) override {
        dcsctp::DcSctpOptions opts = options;
        if (_opts.t1InitTimeoutMs > 0) {
            opts.t1_init_timeout = dcsctp::DurationMs(_opts.t1InitTimeoutMs);
        }
        if (_opts.t1CookieTimeoutMs > 0) {
            opts.t1_cookie_timeout = dcsctp::DurationMs(_opts.t1CookieTimeoutMs);
        }
        if (_opts.maxBackoffMs > 0) {
            opts.max_timer_backoff_duration = dcsctp::DurationMs(_opts.maxBackoffMs);
        }
        return std::make_unique<dcsctp::CustomDcSctpSocket>(
            log_prefix, callbacks, std::move(packet_observer), opts);
    }
private:
    SignalingSctpConnection::Options _opts;
};

class SignalingPacketTransport : public rtc::PacketTransportInternal {
public:
    SignalingPacketTransport(std::shared_ptr<Threads> threads, std::function<void(const std::vector<uint8_t> &)> emitData, bool writable) :
    _threads(threads),
    _emitData(emitData),
    _writable(writable),
    _transportName("signaling") {
    }

    virtual ~SignalingPacketTransport() {
    }

    void receiveData(std::vector<uint8_t> const &data) {
        RTC_LOG(LS_INFO) << "SignalingPacketTransport: adding data of " << data.size() << " bytes";
        SignalReadPacket.emit(this, (const char *)data.data(), data.size(), -1, 0);
    }

    void setWritable(bool writable) {
        if (_writable != writable) {
            _writable = writable;
            SignalWritableState.emit(this);
        }
    }

    virtual const std::string& transport_name() const override {
        return _transportName;
    }

    virtual bool writable() const override {
        return _writable;
    }

    virtual bool receiving() const override {
        return false;
    }

    // Attempts to send the given packet.
    // The return value is < 0 on failure. The return value in failure case is not
    // descriptive. Depending on failure cause and implementation details
    // GetError() returns an descriptive errno.h error value.
    // This mimics posix socket send() or sendto() behavior.
    // TODO(johan): Reliable, meaningful, consistent error codes for all
    // implementations would be nice.
    // TODO(johan): Remove the default argument once channel code is updated.
    virtual int SendPacket(const char* data,
                           size_t len,
                           const rtc::PacketOptions& options,
                           int flags = 0) override {
        _emitData(std::vector<uint8_t>(data, data + len));

        rtc::SentPacket sentPacket;
        sentPacket.packet_id = options.packet_id;
        SignalSentPacket.emit(this, sentPacket);

        return (int)len;
    }

    virtual int SetOption(rtc::Socket::Option opt, int value) override {
        return 0;
    }

    virtual bool GetOption(rtc::Socket::Option opt, int* value) override {
        return 0;
    }

    virtual int GetError() override {
        return 0;
    }

    virtual absl::optional<rtc::NetworkRoute> network_route() const override {
        return absl::nullopt;
    }

private:
    std::shared_ptr<Threads> _threads;
    std::function<void(const std::vector<uint8_t> &)> _onIncomingData;
    std::function<void(const std::vector<uint8_t> &)> _emitData;
    bool _writable;
    std::string _transportName;
};

SignalingSctpConnection::SignalingSctpConnection(std::shared_ptr<Threads> threads, std::function<void(const std::vector<uint8_t> &)> onIncomingData, std::function<void(const std::vector<uint8_t> &)> emitData, bool isInitiator, Options options) :
_threads(threads),
_emitData(emitData),
_onIncomingData(onIncomingData) {
    _threads->getNetworkThread()->BlockingCall([&]() {
        _packetTransport = std::make_unique<SignalingPacketTransport>(threads, emitData, /*writable=*/isInitiator);

        // Use DcSctpTransport directly with CustomDcSctpSocketFactory so the
        // signaling SCTP gets the t1 timer backoff cap fix (the stock
        // DcSctpSocket doesn't wire max_timer_backoff_duration to t1 timers).
        _sctpTransport = std::make_unique<webrtc::DcSctpTransport>(
            _threads->getNetworkThread(),
            _packetTransport.get(),
            webrtc::Clock::GetRealTimeClock(),
            std::make_unique<CustomDcSctpSocketFactory>(options));
        _sctpTransport->OpenStream(0);
        _sctpTransport->SetDataChannelSink(this);

        _sctpTransport->Start(5000, 5000, 262144);
    });
}

void SignalingSctpConnection::OnReadyToSend() {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _isReadyToSend = true;
    
    auto pendingData = _pendingData;
    _pendingData.clear();
    
    for (const auto &data : pendingData) {
        webrtc::SendDataParams params;
        params.type = webrtc::DataMessageType::kBinary;
        params.ordered = true;
        
        rtc::CopyOnWriteBuffer payload;
        payload.AppendData(data.data(), data.size());
        
        webrtc::RTCError sendError = _sctpTransport->SendData(0, params, payload);
            
        if (sendError.ok()) {
            RTC_LOG(LS_INFO) << "SignalingSctpConnection: sent data of " << data.size() << " bytes";
        } else {
            _isReadyToSend = false;
            _pendingData.push_back(data);
            RTC_LOG(LS_WARNING) << "SignalingSctpConnection: OnReadyToSend flush error '" << sendError.message() << "' (type=" << ToString(sendError.type()) << "), storing data until ready to send (" << _pendingData.size() << " items)";
            break;
        }
    }
}

void SignalingSctpConnection::OnTransportClosed(webrtc::RTCError error) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void SignalingSctpConnection::OnDataReceived(int channel_id, webrtc::DataMessageType type, const rtc::CopyOnWriteBuffer& buffer) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _onIncomingData(std::vector<uint8_t>(buffer.data(), buffer.data() + buffer.size()));
}

SignalingSctpConnection::~SignalingSctpConnection() {
    _threads->getNetworkThread()->BlockingCall([&]() {
        _sctpTransport.reset();
        _packetTransport.reset();
    });
}

void SignalingSctpConnection::start() {
}

void SignalingSctpConnection::receiveExternal(const std::vector<uint8_t> &data) {
    _threads->getNetworkThread()->BlockingCall([&]() {
        _packetTransport->setWritable(true);
        _packetTransport->receiveData(data);
    });
}

void SignalingSctpConnection::send(const std::vector<uint8_t> &data) {
    _threads->getNetworkThread()->BlockingCall([&]() {
        if (_isReadyToSend) {
            webrtc::SendDataParams params;
            params.type = webrtc::DataMessageType::kBinary;
            params.ordered = true;
            
            rtc::CopyOnWriteBuffer payload;
            payload.AppendData(data.data(), data.size());
            
            webrtc::RTCError sendError = _sctpTransport->SendData(0, params, payload);
            
            if (!sendError.ok()) {
                _isReadyToSend = false;
                _pendingData.push_back(data);
                RTC_LOG(LS_WARNING) << "SignalingSctpConnection: send error '" << sendError.message() << "' (type=" << ToString(sendError.type()) << "), storing data until ready to send (" << _pendingData.size() << " items)";
            } else {
                RTC_LOG(LS_INFO) << "SignalingSctpConnection: sent data of " << data.size() << " bytes";
            }
        } else {
            _pendingData.push_back(data);
            RTC_LOG(LS_INFO) << "SignalingSctpConnection: not ready to send, storing data until ready to send (" << _pendingData.size() << " items)";
        }
    });
}

}
