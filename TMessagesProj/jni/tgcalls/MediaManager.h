#ifndef TGCALLS_MEDIA_MANAGER_H
#define TGCALLS_MEDIA_MANAGER_H

#include "rtc_base/thread.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "api/transport/field_trial_based_config.h"
#include "pc/rtp_sender.h"

#include "Instance.h"
#include "Message.h"
#include "VideoCaptureInterface.h"

#include <functional>
#include <memory>

namespace webrtc {
class Call;
class RtcEventLogNull;
class TaskQueueFactory;
class VideoBitrateAllocatorFactory;
class VideoTrackSourceInterface;
} // namespace webrtc

namespace cricket {
class MediaEngineInterface;
class VoiceMediaChannel;
class VideoMediaChannel;
} // namespace cricket

namespace tgcalls {

class MediaManager : public sigslot::has_slots<>, public std::enable_shared_from_this<MediaManager> {
public:
	static rtc::Thread *getWorkerThread();

	MediaManager(
		rtc::Thread *thread,
		bool isOutgoing,
		std::shared_ptr<VideoCaptureInterface> videoCapture,
		std::function<void(Message &&)> sendSignalingMessage,
		std::function<void(Message &&)> sendTransportMessage,
        std::function<void(int)> signalBarsUpdated,
        float localPreferredVideoAspectRatio,
        bool enableHighBitrateVideo,
        std::vector<std::string> preferredCodecs);
	~MediaManager();

	void start();
	void setIsConnected(bool isConnected);
	void notifyPacketSent(const rtc::SentPacket &sentPacket);
	void setSendVideo(std::shared_ptr<VideoCaptureInterface> videoCapture);
	void setMuteOutgoingAudio(bool mute);
	void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
	void receiveMessage(DecryptedMessage &&message);
    void remoteVideoStateUpdated(VideoState videoState);
    void setIsCurrentNetworkLowCost(bool isCurrentNetworkLowCost);

private:
	struct SSRC {
		uint32_t incoming = 0;
		uint32_t outgoing = 0;
		uint32_t fecIncoming = 0;
		uint32_t fecOutgoing = 0;
	};

	class NetworkInterfaceImpl : public cricket::MediaChannel::NetworkInterface {
	public:
		NetworkInterfaceImpl(MediaManager *mediaManager, bool isVideo);
		bool SendPacket(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) override;
		bool SendRtcp(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) override;
		int SetOption(SocketType type, rtc::Socket::Option opt, int option) override;

	private:
		bool sendTransportMessage(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options);

		MediaManager *_mediaManager = nullptr;
		bool _isVideo = false;

	};

	friend class MediaManager::NetworkInterfaceImpl;

	void setPeerVideoFormats(VideoFormatsMessage &&peerFormats);

	bool computeIsSendingVideo() const;
    void configureSendingVideoIfNeeded();
	void checkIsSendingVideoChanged(bool wasSending);
	bool videoCodecsNegotiated() const;
    
    int getMaxVideoBitrate() const;
    void adjustBitratePreferences(bool resetStartBitrate);
    bool computeIsReceivingVideo() const;
    void checkIsReceivingVideoChanged(bool wasReceiving);

	void setOutgoingVideoState(VideoState state);
	void setOutgoingAudioState(AudioState state);
	void sendVideoParametersMessage();
	void sendOutgoingMediaStateMessage();
    
    void beginStatsTimer(int timeoutMs);
    void collectStats();

	rtc::Thread *_thread = nullptr;
	std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
	std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;

	std::function<void(Message &&)> _sendSignalingMessage;
	std::function<void(Message &&)> _sendTransportMessage;
    std::function<void(int)> _signalBarsUpdated;

	SSRC _ssrcAudio;
	SSRC _ssrcVideo;
	bool _enableFlexfec = true;

	bool _isConnected = false;
	bool _readyToReceiveVideo = false;
    bool _didConfigureVideo = false;
	AudioState _outgoingAudioState = AudioState::Active;
	VideoState _outgoingVideoState = VideoState::Inactive;

	VideoFormatsMessage _myVideoFormats;
	std::vector<cricket::VideoCodec> _videoCodecs;
	absl::optional<cricket::VideoCodec> _videoCodecOut;

	std::unique_ptr<cricket::MediaEngineInterface> _mediaEngine;
	std::unique_ptr<webrtc::Call> _call;
	webrtc::FieldTrialBasedConfig _fieldTrials;
	webrtc::LocalAudioSinkAdapter _audioSource;
	std::unique_ptr<cricket::VoiceMediaChannel> _audioChannel;
	std::unique_ptr<cricket::VideoMediaChannel> _videoChannel;
	std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
	std::shared_ptr<VideoCaptureInterface> _videoCapture;
	std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentIncomingVideoSink;

    float _localPreferredVideoAspectRatio = 0.0f;
    float _preferredAspectRatio = 0.0f;
    bool _enableHighBitrateVideo = false;
    bool _isLowCostNetwork = false;

	std::unique_ptr<MediaManager::NetworkInterfaceImpl> _audioNetworkInterface;
	std::unique_ptr<MediaManager::NetworkInterfaceImpl> _videoNetworkInterface;
};

} // namespace tgcalls

#endif
