#include "MediaManager.h"

#include "Instance.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "CodecSelectHelper.h"
#include "Message.h"
#include "platform/PlatformInterface.h"

#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "system_wrappers/include/field_trial.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "call/call.h"
#include "modules/rtp_rtcp/source/rtp_utility.h"

namespace tgcalls {
namespace {

constexpr uint32_t ssrcAudioIncoming = 1;
constexpr uint32_t ssrcAudioOutgoing = 2;
constexpr uint32_t ssrcAudioFecIncoming = 5;
constexpr uint32_t ssrcAudioFecOutgoing = 6;
constexpr uint32_t ssrcVideoIncoming = 3;
constexpr uint32_t ssrcVideoOutgoing = 4;
constexpr uint32_t ssrcVideoFecIncoming = 7;
constexpr uint32_t ssrcVideoFecOutgoing = 8;

rtc::Thread *makeWorkerThread() {
	static std::unique_ptr<rtc::Thread> value = rtc::Thread::Create();
	value->SetName("WebRTC-Worker", nullptr);
	value->Start();
	return value.get();
}

VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
	return videoCapture
		? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
		: nullptr;
}

} // namespace

rtc::Thread *MediaManager::getWorkerThread() {
	static rtc::Thread *value = makeWorkerThread();
	return value;
}

MediaManager::MediaManager(
	rtc::Thread *thread,
	bool isOutgoing,
	std::shared_ptr<VideoCaptureInterface> videoCapture,
	std::function<void(Message &&)> sendSignalingMessage,
	std::function<void(Message &&)> sendTransportMessage,
    std::function<void(int)> signalBarsUpdated,
    float localPreferredVideoAspectRatio,
    bool enableHighBitrateVideo,
    std::vector<std::string> preferredCodecs) :
_thread(thread),
_eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
_taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
_sendSignalingMessage(std::move(sendSignalingMessage)),
_sendTransportMessage(std::move(sendTransportMessage)),
_signalBarsUpdated(std::move(signalBarsUpdated)),
_outgoingVideoState(videoCapture ? VideoState::Active : VideoState::Inactive),
_videoCapture(std::move(videoCapture)),
_localPreferredVideoAspectRatio(localPreferredVideoAspectRatio),
_enableHighBitrateVideo(enableHighBitrateVideo) {
	_ssrcAudio.incoming = isOutgoing ? ssrcAudioIncoming : ssrcAudioOutgoing;
	_ssrcAudio.outgoing = (!isOutgoing) ? ssrcAudioIncoming : ssrcAudioOutgoing;
	_ssrcAudio.fecIncoming = isOutgoing ? ssrcAudioFecIncoming : ssrcAudioFecOutgoing;
	_ssrcAudio.fecOutgoing = (!isOutgoing) ? ssrcAudioFecIncoming : ssrcAudioFecOutgoing;
	_ssrcVideo.incoming = isOutgoing ? ssrcVideoIncoming : ssrcVideoOutgoing;
	_ssrcVideo.outgoing = (!isOutgoing) ? ssrcVideoIncoming : ssrcVideoOutgoing;
	_ssrcVideo.fecIncoming = isOutgoing ? ssrcVideoFecIncoming : ssrcVideoFecOutgoing;
	_ssrcVideo.fecOutgoing = (!isOutgoing) ? ssrcVideoFecIncoming : ssrcVideoFecOutgoing;

	_audioNetworkInterface = std::unique_ptr<MediaManager::NetworkInterfaceImpl>(new MediaManager::NetworkInterfaceImpl(this, false));
	_videoNetworkInterface = std::unique_ptr<MediaManager::NetworkInterfaceImpl>(new MediaManager::NetworkInterfaceImpl(this, true));

	webrtc::field_trial::InitFieldTrialsFromString(
		"WebRTC-Audio-SendSideBwe/Enabled/"
		"WebRTC-Audio-Allocation/min:6kbps,max:32kbps/"
		"WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
		"WebRTC-FlexFEC-03/Enabled/"
		"WebRTC-FlexFEC-03-Advertised/Enabled/"
	);

	PlatformInterface::SharedInstance()->configurePlatformAudio();

	_videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

	cricket::MediaEngineDependencies mediaDeps;
	mediaDeps.task_queue_factory = _taskQueueFactory.get();
	mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
	mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();

	mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory();
	mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory();

	_myVideoFormats = ComposeSupportedFormats(
		mediaDeps.video_encoder_factory->GetSupportedFormats(),
		mediaDeps.video_decoder_factory->GetSupportedFormats(),
        preferredCodecs);

	mediaDeps.audio_processing = webrtc::AudioProcessingBuilder().Create();
	_mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));
	_mediaEngine->Init();
	webrtc::Call::Config callConfig(_eventLog.get());
	callConfig.task_queue_factory = _taskQueueFactory.get();
	callConfig.trials = &_fieldTrials;
	callConfig.audio_state = _mediaEngine->voice().GetAudioState();
	_call.reset(webrtc::Call::Create(callConfig));

    cricket::AudioOptions audioOptions;
    audioOptions.echo_cancellation = true;
    audioOptions.noise_suppression = true;
    audioOptions.audio_jitter_buffer_fast_accelerate = true;

	_audioChannel.reset(_mediaEngine->voice().CreateMediaChannel(_call.get(), cricket::MediaConfig(), audioOptions, webrtc::CryptoOptions::NoGcm()));
	_videoChannel.reset(_mediaEngine->video().CreateMediaChannel(_call.get(), cricket::MediaConfig(), cricket::VideoOptions(), webrtc::CryptoOptions::NoGcm(), _videoBitrateAllocatorFactory.get()));

	const uint32_t opusClockrate = 48000;
	const uint16_t opusSdpPayload = 111;
	const char *opusSdpName = "opus";
	const uint8_t opusSdpChannels = 2;
	const uint32_t opusSdpBitrate = 0;

	const uint8_t opusMinBitrateKbps = 6;
	const uint8_t opusMaxBitrateKbps = 32;
	const uint8_t opusStartBitrateKbps = 8;
	const uint8_t opusPTimeMs = 120;

	cricket::AudioCodec opusCodec(opusSdpPayload, opusSdpName, opusClockrate, opusSdpBitrate, opusSdpChannels);
	opusCodec.AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc));
	opusCodec.SetParam(cricket::kCodecParamMinBitrate, opusMinBitrateKbps);
	opusCodec.SetParam(cricket::kCodecParamStartBitrate, opusStartBitrateKbps);
	opusCodec.SetParam(cricket::kCodecParamMaxBitrate, opusMaxBitrateKbps);
	opusCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
	opusCodec.SetParam(cricket::kCodecParamPTime, opusPTimeMs);

	cricket::AudioSendParameters audioSendPrameters;
	audioSendPrameters.codecs.push_back(opusCodec);
	audioSendPrameters.extensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 1);
	audioSendPrameters.options.echo_cancellation = true;
	//audioSendPrameters.options.experimental_ns = false;
	audioSendPrameters.options.noise_suppression = true;
	audioSendPrameters.options.auto_gain_control = true;
	//audioSendPrameters.options.highpass_filter = false;
	audioSendPrameters.options.typing_detection = false;
	//audioSendPrameters.max_bandwidth_bps = 16000;
	audioSendPrameters.rtcp.reduced_size = true;
	audioSendPrameters.rtcp.remote_estimate = true;
	_audioChannel->SetSendParameters(audioSendPrameters);
	_audioChannel->AddSendStream(cricket::StreamParams::CreateLegacy(_ssrcAudio.outgoing));
	_audioChannel->SetInterface(_audioNetworkInterface.get());

	cricket::AudioRecvParameters audioRecvParameters;
	audioRecvParameters.codecs.emplace_back(opusSdpPayload, opusSdpName, opusClockrate, opusSdpBitrate, opusSdpChannels);
	audioRecvParameters.extensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 1);
	audioRecvParameters.rtcp.reduced_size = true;
	audioRecvParameters.rtcp.remote_estimate = true;

	_audioChannel->SetRecvParameters(audioRecvParameters);
	_audioChannel->AddRecvStream(cricket::StreamParams::CreateLegacy(_ssrcAudio.incoming));
	_audioChannel->SetPlayout(true);

	_videoChannel->SetInterface(_videoNetworkInterface.get());
    
    adjustBitratePreferences(true);
}

void MediaManager::start() {
	_sendSignalingMessage({ _myVideoFormats });

	if (_videoCapture != nullptr) {
        setSendVideo(_videoCapture);
    }

    beginStatsTimer(3000);
}

MediaManager::~MediaManager() {
	assert(_thread->IsCurrent());
    
    RTC_LOG(LS_INFO) << "MediaManager::~MediaManager()";

	_call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkDown);
	_call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkDown);

	_audioChannel->OnReadyToSend(false);
	_audioChannel->SetSend(false);
	_audioChannel->SetAudioSend(_ssrcAudio.outgoing, false, nullptr, &_audioSource);

	_audioChannel->SetPlayout(false);

	_audioChannel->RemoveRecvStream(_ssrcAudio.incoming);
	_audioChannel->RemoveSendStream(_ssrcAudio.outgoing);

	_audioChannel->SetInterface(nullptr);

	setSendVideo(nullptr);
    
    if (computeIsReceivingVideo()) {
        _videoChannel->RemoveRecvStream(_ssrcVideo.incoming);
        if (_enableFlexfec) {
            _videoChannel->RemoveRecvStream(_ssrcVideo.fecIncoming);
        }
    }
    
    if (_didConfigureVideo) {
        _videoChannel->OnReadyToSend(false);
        _videoChannel->SetSend(false);
        
        if (_enableFlexfec) {
            _videoChannel->RemoveSendStream(_ssrcVideo.outgoing);
            _videoChannel->RemoveSendStream(_ssrcVideo.fecOutgoing);
        } else {
            _videoChannel->RemoveSendStream(_ssrcVideo.outgoing);
        }
    }
    
    _videoChannel->SetInterface(nullptr);
}

void MediaManager::setIsConnected(bool isConnected) {
	if (_isConnected == isConnected) {
		return;
	}
	_isConnected = isConnected;

	if (_isConnected) {
		_call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkUp);
		_call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkUp);
	} else {
		_call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkDown);
		_call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkDown);
	}
	if (_audioChannel) {
		_audioChannel->OnReadyToSend(_isConnected);
		_audioChannel->SetSend(_isConnected);
		_audioChannel->SetAudioSend(_ssrcAudio.outgoing, _isConnected && (_outgoingAudioState == AudioState::Active), nullptr, &_audioSource);
	}
	if (computeIsSendingVideo() && _videoChannel) {
		_videoChannel->OnReadyToSend(_isConnected);
		_videoChannel->SetSend(_isConnected);
	}
	sendVideoParametersMessage();
	sendOutgoingMediaStateMessage();
}

void MediaManager::sendVideoParametersMessage() {
	const auto aspectRatioValue = uint32_t(_localPreferredVideoAspectRatio * 1000.0);
	_sendTransportMessage({ VideoParametersMessage{ aspectRatioValue } });
}

void MediaManager::sendOutgoingMediaStateMessage() {
	_sendTransportMessage({ RemoteMediaStateMessage{ _outgoingAudioState, _outgoingVideoState } });
}

void MediaManager::beginStatsTimer(int timeoutMs) {
    const auto weak = std::weak_ptr<MediaManager>(shared_from_this());
    _thread->PostDelayedTask(RTC_FROM_HERE, [weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }
        strong->collectStats();
    }, timeoutMs);
}

void MediaManager::collectStats() {
    auto stats = _call->GetStats();
    float bitrateNorm = 16.0f;
    switch (_outgoingVideoState) {
        case VideoState::Active:
            bitrateNorm = 600.0f;
            break;
        default:
            break;
    }
    float sendBitrateKbps = ((float)stats.send_bandwidth_bps / 1000.0f);
    
    RTC_LOG(LS_INFO) << "MediaManager sendBitrateKbps=" << (stats.send_bandwidth_bps / 1000);

    float signalBarsNorm = 4.0f;
    float adjustedQuality = sendBitrateKbps / bitrateNorm;
    adjustedQuality = fmaxf(0.0f, adjustedQuality);
    adjustedQuality = fminf(1.0f, adjustedQuality);
	if (_signalBarsUpdated) {
		_signalBarsUpdated((int)(adjustedQuality * signalBarsNorm));
	}

    beginStatsTimer(2000);
}

void MediaManager::notifyPacketSent(const rtc::SentPacket &sentPacket) {
	_call->OnSentPacket(sentPacket);
}

void MediaManager::setPeerVideoFormats(VideoFormatsMessage &&peerFormats) {
	if (!_videoCodecs.empty()) {
		return;
	}

    bool wasReceivingVideo = computeIsReceivingVideo();

	assert(!_videoCodecOut.has_value());
	auto formats = ComputeCommonFormats(
		_myVideoFormats,
		std::move(peerFormats));
	auto codecs = AssignPayloadTypesAndDefaultCodecs(std::move(formats));
	if (codecs.myEncoderIndex >= 0) {
		assert(codecs.myEncoderIndex < codecs.list.size());
		_videoCodecOut = codecs.list[codecs.myEncoderIndex];
	}
	_videoCodecs = std::move(codecs.list);
	if (_videoCodecOut.has_value()) {
		checkIsSendingVideoChanged(false);
	}
    if (_videoCodecs.size() != 0) {
        checkIsReceivingVideoChanged(wasReceivingVideo);
    }
}

bool MediaManager::videoCodecsNegotiated() const {
	return !_videoCodecs.empty();
}

bool MediaManager::computeIsSendingVideo() const {
	return _videoCapture != nullptr && _videoCodecOut.has_value();
}

bool MediaManager::computeIsReceivingVideo() const {
    return _videoCodecs.size() != 0;
}

void MediaManager::setSendVideo(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    const auto wasSending = computeIsSendingVideo();
    const auto wasReceiving = computeIsReceivingVideo();

    if (_videoCapture) {
		GetVideoCaptureAssumingSameThread(_videoCapture.get())->setStateUpdated(nullptr);
    }
    _videoCapture = videoCapture;
	if (_videoCapture) {
        _videoCapture->setPreferredAspectRatio(_preferredAspectRatio);

		const auto thread = _thread;
		const auto weak = std::weak_ptr<MediaManager>(shared_from_this());
		GetVideoCaptureAssumingSameThread(_videoCapture.get())->setStateUpdated([=](VideoState state) {
			thread->PostTask(RTC_FROM_HERE, [=] {
				if (const auto strong = weak.lock()) {
					strong->setOutgoingVideoState(state);
				}
			});
		});
        setOutgoingVideoState(VideoState::Active);
    } else {
        setOutgoingVideoState(VideoState::Inactive);
    }

    checkIsSendingVideoChanged(wasSending);
    checkIsReceivingVideoChanged(wasReceiving);
}

void MediaManager::configureSendingVideoIfNeeded() {
    if (_didConfigureVideo) {
        return;
    }
    if (!_videoCodecOut.has_value()) {
        return;
    }
    _didConfigureVideo = true;
    
    auto codec = *_videoCodecOut;

    codec.SetParam(cricket::kCodecParamMinBitrate, 64);
    codec.SetParam(cricket::kCodecParamStartBitrate, 400);
    codec.SetParam(cricket::kCodecParamMaxBitrate, _enableHighBitrateVideo ? 2000 : 800);

    cricket::VideoSendParameters videoSendParameters;
    videoSendParameters.codecs.push_back(codec);

    if (_enableFlexfec) {
        for (auto &c : _videoCodecs) {
            if (c.name == cricket::kFlexfecCodecName) {
                videoSendParameters.codecs.push_back(c);
                break;
            }
        }
    }

    videoSendParameters.extensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 2);
    videoSendParameters.rtcp.remote_estimate = true;
    _videoChannel->SetSendParameters(videoSendParameters);

    if (_enableFlexfec) {
        cricket::StreamParams videoSendStreamParams;
        cricket::SsrcGroup videoSendSsrcGroup(cricket::kFecFrSsrcGroupSemantics, {_ssrcVideo.outgoing, _ssrcVideo.fecOutgoing});
        videoSendStreamParams.ssrcs = {_ssrcVideo.outgoing};
        videoSendStreamParams.ssrc_groups.push_back(videoSendSsrcGroup);
        videoSendStreamParams.cname = "cname";
        _videoChannel->AddSendStream(videoSendStreamParams);
    } else {
        _videoChannel->AddSendStream(cricket::StreamParams::CreateLegacy(_ssrcVideo.outgoing));
    }
    
    adjustBitratePreferences(true);
}

void MediaManager::checkIsSendingVideoChanged(bool wasSending) {
	const auto sending = computeIsSendingVideo();
	if (sending == wasSending) {
		return;
	} else if (sending) {
        configureSendingVideoIfNeeded();
        
        if (_enableFlexfec) {
            _videoChannel->SetVideoSend(_ssrcVideo.outgoing, NULL, GetVideoCaptureAssumingSameThread(_videoCapture.get())->_videoSource);
            _videoChannel->SetVideoSend(_ssrcVideo.fecOutgoing, NULL, nullptr);
        } else {
            _videoChannel->SetVideoSend(_ssrcVideo.outgoing, NULL, GetVideoCaptureAssumingSameThread(_videoCapture.get())->_videoSource);
        }

		_videoChannel->OnReadyToSend(_isConnected);
		_videoChannel->SetSend(_isConnected);
	} else {
		_videoChannel->SetVideoSend(_ssrcVideo.outgoing, NULL, nullptr);
		_videoChannel->SetVideoSend(_ssrcVideo.fecOutgoing, NULL, nullptr);
	}
    
    adjustBitratePreferences(true);
}

int MediaManager::getMaxVideoBitrate() const {
    return (_enableHighBitrateVideo && _isLowCostNetwork) ? 2000000 : 800000;
}

void MediaManager::adjustBitratePreferences(bool resetStartBitrate) {
    if (computeIsSendingVideo()) {
        webrtc::BitrateConstraints preferences;
        preferences.min_bitrate_bps = 64000;
        if (resetStartBitrate) {
            preferences.start_bitrate_bps = 400000;
        }
        preferences.max_bitrate_bps = getMaxVideoBitrate();
        
        _call->GetTransportControllerSend()->SetSdpBitrateParameters(preferences);
    } else {
        webrtc::BitrateConstraints preferences;
        if (_didConfigureVideo) {
            // After we have configured outgoing video, RTCP stops working for outgoing audio
            // TODO: investigate
            preferences.min_bitrate_bps = 16000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 16000;
            }
            preferences.max_bitrate_bps = 32000;
        } else {
            preferences.min_bitrate_bps = 8000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 16000;
            }
            preferences.max_bitrate_bps = 32000;
        }
        
        _call->GetTransportControllerSend()->SetSdpBitrateParameters(preferences);
    }
}

void MediaManager::checkIsReceivingVideoChanged(bool wasReceiving) {
    const auto receiving = computeIsReceivingVideo();
    if (receiving == wasReceiving) {
        return;
    } else {
        cricket::VideoRecvParameters videoRecvParameters;

        const auto codecs = {
            cricket::kFlexfecCodecName,
            cricket::kH264CodecName,
            cricket::kH265CodecName,
            cricket::kVp8CodecName,
            cricket::kVp9CodecName,
            cricket::kAv1CodecName,
        };
        for (const auto &c : _videoCodecs) {
            for (const auto known : codecs) {
                if (c.name == known) {
                    videoRecvParameters.codecs.push_back(c);
                    break;
                }
            }
        }

        videoRecvParameters.extensions.emplace_back(webrtc::RtpExtension::kTransportSequenceNumberUri, 2);
        //recv_parameters.rtcp.reduced_size = true;
        videoRecvParameters.rtcp.remote_estimate = true;

        cricket::StreamParams videoRecvStreamParams;
        cricket::SsrcGroup videoRecvSsrcGroup(cricket::kFecFrSsrcGroupSemantics, {_ssrcVideo.incoming, _ssrcVideo.fecIncoming});
        videoRecvStreamParams.ssrcs = {_ssrcVideo.incoming};
        videoRecvStreamParams.ssrc_groups.push_back(videoRecvSsrcGroup);
        videoRecvStreamParams.cname = "cname";

        _videoChannel->SetRecvParameters(videoRecvParameters);
        _videoChannel->AddRecvStream(videoRecvStreamParams);
        _readyToReceiveVideo = true;
        if (_currentIncomingVideoSink) {
            _videoChannel->SetSink(_ssrcVideo.incoming, _currentIncomingVideoSink.get());
        }
    }
}

void MediaManager::setMuteOutgoingAudio(bool mute) {
	setOutgoingAudioState(mute ? AudioState::Muted : AudioState::Active);
	_audioChannel->SetAudioSend(_ssrcAudio.outgoing, _isConnected && (_outgoingAudioState == AudioState::Active), nullptr, &_audioSource);
}

void MediaManager::setOutgoingAudioState(AudioState state) {
	if (_outgoingAudioState == state) {
		return;
	}
	_outgoingAudioState = state;
	sendOutgoingMediaStateMessage();
}

void MediaManager::setOutgoingVideoState(VideoState state) {
	if (_outgoingVideoState == state) {
		return;
	}
	_outgoingVideoState = state;
	sendOutgoingMediaStateMessage();
}

void MediaManager::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	_currentIncomingVideoSink = sink;
	_videoChannel->SetSink(_ssrcVideo.incoming, _currentIncomingVideoSink.get());
}

static bool IsRtcp(const uint8_t* packet, size_t length) {
    webrtc::RtpUtility::RtpHeaderParser rtp_parser(packet, length);
    return rtp_parser.RTCP();
}

void MediaManager::receiveMessage(DecryptedMessage &&message) {
	const auto data = &message.message.data;
	if (const auto formats = absl::get_if<VideoFormatsMessage>(data)) {
		setPeerVideoFormats(std::move(*formats));
	} else if (const auto audio = absl::get_if<AudioDataMessage>(data)) {
        if (IsRtcp(audio->data.data(), audio->data.size())) {
            RTC_LOG(LS_VERBOSE) << "Deliver audio RTCP";
        }
        _call->Receiver()->DeliverPacket(webrtc::MediaType::AUDIO, audio->data, -1);
	} else if (const auto video = absl::get_if<VideoDataMessage>(data)) {
		if (_videoChannel) {
			if (_readyToReceiveVideo) {
                _call->Receiver()->DeliverPacket(webrtc::MediaType::VIDEO, video->data, -1);
			} else {
				// maybe we need to queue packets for some time?
			}
		}
    } else if (const auto videoParameters = absl::get_if<VideoParametersMessage>(data)) {
        float value = ((float)videoParameters->aspectRatio) / 1000.0;
        _preferredAspectRatio = value;
        if (_videoCapture) {
            _videoCapture->setPreferredAspectRatio(value);
        }
    }
}

void MediaManager::remoteVideoStateUpdated(VideoState videoState) {
    switch (videoState) {
        case VideoState::Active:
        case VideoState::Paused:
            configureSendingVideoIfNeeded();
            break;
        default:
            break;
    }
}

void MediaManager::setIsCurrentNetworkLowCost(bool isCurrentNetworkLowCost) {
    if (_isLowCostNetwork != isCurrentNetworkLowCost) {
        _isLowCostNetwork = isCurrentNetworkLowCost;
        RTC_LOG(LS_INFO) << "MediaManager isLowCostNetwork updated: " << isCurrentNetworkLowCost ? 1 : 0;
        adjustBitratePreferences(false);
    }
}

MediaManager::NetworkInterfaceImpl::NetworkInterfaceImpl(MediaManager *mediaManager, bool isVideo) :
_mediaManager(mediaManager),
_isVideo(isVideo) {
}

bool MediaManager::NetworkInterfaceImpl::SendPacket(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) {
	return sendTransportMessage(packet, options);
}

bool MediaManager::NetworkInterfaceImpl::SendRtcp(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) {
	return sendTransportMessage(packet, options);
}

bool MediaManager::NetworkInterfaceImpl::sendTransportMessage(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) {
    if (_isVideo) {
        RTC_LOG(LS_VERBOSE) << "Send video packet";
    }
	_mediaManager->_sendTransportMessage(_isVideo
		? Message{ VideoDataMessage{ *packet } }
		: Message{ AudioDataMessage{ *packet } });
	rtc::SentPacket sentPacket(options.packet_id, rtc::TimeMillis(), options.info_signaled_after_sent);
	_mediaManager->notifyPacketSent(sentPacket);
	return true;
}

int MediaManager::NetworkInterfaceImpl::SetOption(cricket::MediaChannel::NetworkInterface::SocketType, rtc::Socket::Option, int) {
	return -1;
}

} // namespace tgcalls
