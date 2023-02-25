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
#include "Stats.h"

#include <functional>
#include <memory>

namespace webrtc {
class Call;
class RtcEventLogNull;
class TaskQueueFactory;
class VideoBitrateAllocatorFactory;
class VideoTrackSourceInterface;
class AudioDeviceModule;
} // namespace webrtc

namespace cricket {
class MediaEngineInterface;
class VoiceMediaChannel;
class VideoMediaChannel;
} // namespace cricket

namespace tgcalls {

class VideoSinkInterfaceProxyImpl;

class MediaManager : public sigslot::has_slots<>, public std::enable_shared_from_this<MediaManager> {
public:
	static rtc::Thread *getWorkerThread();

	MediaManager(
		rtc::Thread *thread,
		bool isOutgoing,
        ProtocolVersion protocolVersion,
		const MediaDevicesConfig &devicesConfig,
		std::shared_ptr<VideoCaptureInterface> videoCapture,
		std::function<void(Message &&)> sendSignalingMessage,
		std::function<void(Message &&)> sendTransportMessage,
        std::function<void(int)> signalBarsUpdated,
        std::function<void(float, float)> audioLevelsUpdated,
		std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> createAudioDeviceModule,
        bool enableHighBitrateVideo,
        std::vector<std::string> preferredCodecs,
		std::shared_ptr<PlatformContext> platformContext);
	~MediaManager();

	void start();
	void setIsConnected(bool isConnected);
	void notifyPacketSent(const rtc::SentPacket &sentPacket);
	void setSendVideo(std::shared_ptr<VideoCaptureInterface> videoCapture);
	void sendVideoDeviceUpdated();
    void setRequestedVideoAspect(float aspect);
	void setMuteOutgoingAudio(bool mute);
	void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink);
	void receiveMessage(DecryptedMessage &&message);
    void remoteVideoStateUpdated(VideoState videoState);
    void setNetworkParameters(bool isLowCost, bool isDataSavingActive);
    void fillCallStats(CallStats &callStats);

	void setAudioInputDevice(std::string id);
	void setAudioOutputDevice(std::string id);
	void setInputVolume(float level);
	void setOutputVolume(float level);

    void addExternalAudioSamples(std::vector<uint8_t> &&samples);

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
    int getMaxAudioBitrate() const;
    void adjustBitratePreferences(bool resetStartBitrate);
    bool computeIsReceivingVideo() const;
    void checkIsReceivingVideoChanged(bool wasReceiving);

	void setOutgoingVideoState(VideoState state);
	void setOutgoingAudioState(AudioState state);
	void sendVideoParametersMessage();
	void sendOutgoingMediaStateMessage();

	rtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule();

    void beginStatsTimer(int timeoutMs);
    void beginLevelsTimer(int timeoutMs);
    void collectStats();

	rtc::Thread *_thread = nullptr;
	std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
	std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;

	std::function<void(Message &&)> _sendSignalingMessage;
	std::function<void(Message &&)> _sendTransportMessage;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(float, float)> _audioLevelsUpdated;
	std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;

	SSRC _ssrcAudio;
	SSRC _ssrcVideo;
	bool _enableFlexfec = true;

    ProtocolVersion _protocolVersion;

	bool _isConnected = false;
    bool _didConnectOnce = false;
	bool _readyToReceiveVideo = false;
    bool _didConfigureVideo = false;
	AudioState _outgoingAudioState = AudioState::Active;
	VideoState _outgoingVideoState = VideoState::Inactive;

	VideoFormatsMessage _myVideoFormats;
	std::vector<cricket::VideoCodec> _videoCodecs;
	absl::optional<cricket::VideoCodec> _videoCodecOut;

	std::unique_ptr<cricket::MediaEngineInterface> _mediaEngine;
	std::unique_ptr<webrtc::Call> _call;
	webrtc::LocalAudioSinkAdapter _audioSource;
	rtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;
	std::unique_ptr<cricket::VoiceMediaChannel> _audioChannel;
	std::unique_ptr<cricket::VideoMediaChannel> _videoChannel;
	std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
	std::shared_ptr<VideoCaptureInterface> _videoCapture;
	std::shared_ptr<bool> _videoCaptureGuard;
    bool _isScreenCapture = false;
    std::shared_ptr<VideoSinkInterfaceProxyImpl> _incomingVideoSinkProxy;

    float _localPreferredVideoAspectRatio = 0.0f;
    float _preferredAspectRatio = 0.0f;
    bool _enableHighBitrateVideo = false;
    bool _isLowCostNetwork = false;
    bool _isDataSavingActive = false;

    float _currentAudioLevel = 0.0f;
    float _currentMyAudioLevel = 0.0f;

	std::unique_ptr<MediaManager::NetworkInterfaceImpl> _audioNetworkInterface;
	std::unique_ptr<MediaManager::NetworkInterfaceImpl> _videoNetworkInterface;

    std::vector<CallStatsBitrateRecord> _bitrateRecords;

    std::vector<float> _externalAudioSamples;
    webrtc::Mutex _externalAudioSamplesMutex;

	std::shared_ptr<PlatformContext> _platformContext;
};

} // namespace tgcalls

#endif
