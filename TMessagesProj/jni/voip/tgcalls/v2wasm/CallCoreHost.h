#ifndef TGCALLS_V2WASM_CALL_CORE_HOST_H
#define TGCALLS_V2WASM_CALL_CORE_HOST_H

#include <deque>
#include <map>
#include <memory>
#include <set>

#include "Instance.h"
#include "StaticThreads.h"

#include "third-party/json11.hpp"

#include "api/peer_connection_interface.h"
#include "rtc_base/network_monitor_factory.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/client/relay_port_factory_interface.h"

#include "v2wasm/CallCoreABI.h"
#include "v2wasm/CallCoreBackend.h"

namespace tgcalls {

class EncryptedConnection;
class SignalingConnection;
class VideoCaptureInterface;

namespace v2wasm_detail {
class PeerConnectionDelegateAdapter;
class DataChannelObserverImpl;
} // namespace v2wasm_detail

// Fixed harness for the pump boundary: owns PeerConnection, ADM, signaling
// crypto/transport, timers and stats; executes core commands and forwards
// platform callbacks to the core as events. Lives on the media thread.
class CallCoreHost final : public std::enable_shared_from_this<CallCoreHost> {
public:
    CallCoreHost(Descriptor &&descriptor, std::shared_ptr<Threads> threads);
    ~CallCoreHost();

    void start();

    void receiveSignalingData(const std::vector<uint8_t> &data);
    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture);
    void setMuteMicrophone(bool mute);
    void setIsLowBatteryLevel(bool low);
    void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
    void setAudioInputDevice(std::string id);
    void setAudioOutputDevice(std::string id);
    void stop(std::function<void(FinalState)> completion);

private:
    friend class v2wasm_detail::PeerConnectionDelegateAdapter;

    void deliverEvent(json11::Json::object &&event);
    void deliverEventNow(json11::Json::object &&event);
    void processPendingCommands();
    void executeCommand(json11::Json const &command);
    void emitErrorEvent(std::string const &message, std::string const &commandType);
    void disableCoreWithFailure(std::string const &reason);

    void executePcCreate(json11::Json const &command);
    void applyAudioProcessingConfig(json11::Json const &config, std::string const &commandName);
    void executeSetConfiguration(json11::Json const &command);
    void executeCreateDescription(bool isOffer);
    void executeSetLocalDescription(json11::Json const &command);
    void executeSetRemoteDescription(json11::Json const &command);
    void executeAddIceCandidate(json11::Json const &command);
    void executeAddTransceiver(json11::Json const &command);
    void executeSetParameters(json11::Json const &command);
    void executeSetTrackEnabled(json11::Json const &command);
    void executeRemoveTrack(json11::Json const &command);
    void executeSetIncomingSink(json11::Json const &command);
    void executeCreateDataChannel(json11::Json const &command);
    void executeSignalingSendPacket(json11::Json const &command);
    void executeGetStats();
    void executeClose();

    void attachDataChannel(std::string const &label, webrtc::scoped_refptr<webrtc::DataChannelInterface> dataChannel);
    void onDataChannelStateUpdated(std::string const &label);
    void onDataChannelBufferedAmountChanged(std::string const &label);
    void executeDcSend(json11::Json const &command);
    void onSignalingData(const std::vector<uint8_t> &data);
    void connectIncomingVideoSink(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver);
    void disconnectIncomingVideoSink(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver);
    void disconnectAllIncomingVideoSinks();
    webrtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule();

    std::shared_ptr<Threads> _threads;

    // descriptor
    std::string _version;
    std::vector<RtcServer> _rtcServers;
    bool _enableP2P = false;
    EncryptionKey _encryptionKey;
    std::string _customParameters;
    std::map<std::string, json11::Json> _parsedCustomParameters;
    std::function<void(State)> _stateUpdated;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<webrtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory *)> _createAudioDeviceModule;
    std::function<webrtc::scoped_refptr<WrappedAudioDeviceModule>(webrtc::TaskQueueFactory *)> _createWrappedAudioDeviceModule;
    FilePath _statsLogPath;

    // core + pump
    std::unique_ptr<CallCoreBackend> _core;
    std::deque<json11::Json> _pendingCommands;
    bool _isProcessingCommands = false;
    bool _isDeliveringEvent = false;
    bool _isCoreDisabled = false;

    // signaling
    std::unique_ptr<SignalingConnection> _signalingConnection;
    std::unique_ptr<EncryptedConnection> _signalingEncryptedConnection;
    // Signaling transport routing only — the framing (gzip, acks, resends,
    // service packets) is core-owned since Phase 2.6.
    bool _useSctpSignalingTransport = false;
    // Host-enforced strict monotonicity of the core-chosen packet counter
    // (low 30 bits of the seq) — preserves AEAD IV freshness.
    uint32_t _lastSentSignalingCounter = 0;

    // webrtc
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;
    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
    std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
    std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
    std::unique_ptr<cricket::RelayPortFactoryInterface> _relayPortFactory;
    webrtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> _peerConnectionFactory;
    std::unique_ptr<webrtc::PeerConnectionObserver> _peerConnectionObserver;
    webrtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;
    webrtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;
    webrtc::scoped_refptr<webrtc::AudioProcessing> _audioProcessing;

    // Core-managed transceivers/tracks, keyed by the core-chosen id from
    // pc_add_transceiver. Incoming (remote) transceivers stay keyed by mid in
    // _incomingVideoTransceivers.
    std::map<std::string, webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>> _coreTransceivers;
    std::map<std::string, webrtc::scoped_refptr<webrtc::MediaStreamTrackInterface>> _coreTracks;
    // Incoming mids the core asked to bind the app video sink to.
    std::set<std::string> _requestedSinkMids;
    std::map<std::string, webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>> _incomingVideoTransceivers;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentStrongSink;
    // Exactly the tracks we called AddOrUpdateSink on. RemoveSink DCHECKs when the
    // sink was never added, and attachment is asymmetric (a transceiver added while
    // no sink is set is never attached), so removal must be driven by this set, not
    // by the transceiver map.
    std::set<webrtc::VideoTrackInterface*> _attachedSinkTracks;
    std::shared_ptr<VideoCaptureInterface> _videoCapture;

    struct HostDataChannel {
        webrtc::scoped_refptr<webrtc::DataChannelInterface> channel;
        std::unique_ptr<v2wasm_detail::DataChannelObserverImpl> observer;
        bool isOpen = false;
        uint64_t lastBufferedAmount = 0;
    };
    std::map<std::string, HostDataChannel> _dataChannels;

    // stop flow
    std::function<void(FinalState)> _stopCompletion;
    std::string _pendingStatsLogJson;
    std::atomic<bool> _isStopped{false};

    // stats delta state (media thread only): cumulative byte counters from
    // the previous pc_get_stats, for host-computed bitrate deltas.
    int64_t _lastStatsTimestampMs = 0;
    uint64_t _lastAudioBytesSent = 0;
    uint64_t _lastVideoBytesSent = 0;
    uint64_t _lastAudioBytesReceived = 0;
    uint64_t _lastVideoBytesReceived = 0;
};

} // namespace tgcalls

#endif
