#include "MediaManager.h"

#include "Instance.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "CodecSelectHelper.h"
#include "AudioDeviceHelper.h"
#include "Message.h"
#include "platform/PlatformInterface.h"
#include "StaticThreads.h"

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
#include "api/call/audio_sink.h"
#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_device/include/audio_device_factory.h"

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


VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
	return videoCapture
		? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
		: nullptr;
}

class AudioCaptureAnalyzer : public webrtc::CustomAudioAnalyzer {
private:
    void Initialize(int sample_rate_hz, int num_channels) override {

    }
    // Analyzes the given capture or render signal.
    void Analyze(const webrtc::AudioBuffer* audio) override {
        _analyze(audio);
    }
    // Returns a string representation of the module state.
    std::string ToString() const override {
        return "analyzing";
    }

    std::function<void(const webrtc::AudioBuffer*)> _analyze;

public:
    AudioCaptureAnalyzer(std::function<void(const webrtc::AudioBuffer*)> analyze) :
    _analyze(analyze) {
    }

    virtual ~AudioCaptureAnalyzer() = default;
};

class AudioCapturePostProcessor : public webrtc::CustomProcessing {
public:
    AudioCapturePostProcessor(std::function<void(float)> updated, std::vector<float> *externalAudioSamples, webrtc::Mutex *externalAudioSamplesMutex) :
    _updated(updated),
    _externalAudioSamples(externalAudioSamples),
    _externalAudioSamplesMutex(externalAudioSamplesMutex) {
    }

    virtual ~AudioCapturePostProcessor() {
    }

private:
    virtual void Initialize(int sample_rate_hz, int num_channels) override {
    }

    virtual void Process(webrtc::AudioBuffer *buffer) override {
        if (!buffer) {
            return;
        }
        if (buffer->num_channels() != 1) {
            return;
        }

        float peak = 0;
        int peakCount = 0;
        const float *samples = buffer->channels_const()[0];
        for (int i = 0; i < buffer->num_frames(); i++) {
            float sample = samples[i];
            if (sample < 0) {
                sample = -sample;
            }
            if (peak < sample) {
                peak = sample;
            }
            peakCount += 1;
        }

        _peakCount += peakCount;
        if (_peak < peak) {
            _peak = peak;
        }
        if (_peakCount >= 1200) {
            float level = _peak / 8000.0f;
            _peak = 0;
            _peakCount = 0;

            _updated(level);
        }

        _externalAudioSamplesMutex->Lock();
        if (!_externalAudioSamples->empty()) {
            float *bufferData = buffer->channels()[0];
            int takenSamples = 0;
            for (int i = 0; i < _externalAudioSamples->size() && i < buffer->num_frames(); i++) {
                float sample = (*_externalAudioSamples)[i];
                sample += bufferData[i];
                sample = std::min(sample, 32768.f);
                sample = std::max(sample, -32768.f);
                bufferData[i] = sample;
                takenSamples++;
            }
            if (takenSamples != 0) {
                _externalAudioSamples->erase(_externalAudioSamples->begin(), _externalAudioSamples->begin() + takenSamples);
            }
        }
        _externalAudioSamplesMutex->Unlock();
    }

    virtual std::string ToString() const override {
        return "CustomPostProcessing";
    }

    virtual void SetRuntimeSetting(webrtc::AudioProcessing::RuntimeSetting setting) override {
    }

private:
    std::function<void(float)> _updated;

    int32_t _peakCount = 0;
    float _peak = 0;

    std::vector<float> *_externalAudioSamples = nullptr;
    webrtc::Mutex *_externalAudioSamplesMutex = nullptr;
};

} // namespace

class VideoSinkInterfaceProxyImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoSinkInterfaceProxyImpl(bool rewriteRotation) :
    _rewriteRotation(rewriteRotation) {
    }

    virtual ~VideoSinkInterfaceProxyImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        if (_impl) {
            if (_rewriteRotation) {
                webrtc::VideoFrame updatedFrame = frame;
                //updatedFrame.set_rotation(webrtc::VideoRotation::kVideoRotation_90);
                _impl->OnFrame(updatedFrame);
            } else {
                _impl->OnFrame(frame);
            }
        }
    }

    virtual void OnDiscardedFrame() override {
        if (_impl) {
            _impl->OnDiscardedFrame();
        }
    }

    void setSink(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _impl = impl;
    }

private:
    bool _rewriteRotation = false;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _impl;

};

class AudioTrackSinkInterfaceImpl: public webrtc::AudioSinkInterface {
private:
    std::function<void(float)> _update;

    int _peakCount = 0;
    uint16_t _peak = 0;

public:
    AudioTrackSinkInterfaceImpl(std::function<void(float)> update) :
    _update(update) {
    }

    virtual ~AudioTrackSinkInterfaceImpl() {
    }

    virtual void OnData(const Data& audio) override {
        if (audio.channels == 1) {
            int16_t *samples = (int16_t *)audio.data;
            int numberOfSamplesInFrame = (int)audio.samples_per_channel;

            for (int i = 0; i < numberOfSamplesInFrame; i++) {
                int16_t sample = samples[i];
                if (sample < 0) {
                    sample = -sample;
                }
                if (_peak < sample) {
                    _peak = sample;
                }
                _peakCount += 1;
            }

            if (_peakCount >= 1200) {
                float level = ((float)(_peak)) / 4000.0f;
                _peak = 0;
                _peakCount = 0;
                _update(level);
            }
        }
    }
};

MediaManager::MediaManager(
	rtc::Thread *thread,
	bool isOutgoing,
    ProtocolVersion protocolVersion,
	const MediaDevicesConfig &devicesConfig,
	std::shared_ptr<VideoCaptureInterface> videoCapture,
	std::function<void(Message &&)> sendSignalingMessage,
	std::function<void(Message &&)> sendTransportMessage,
    std::function<void(int)> signalBarsUpdated,
    std::function<void(float)> audioLevelUpdated,
    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> createAudioDeviceModule,
    bool enableHighBitrateVideo,
    std::vector<std::string> preferredCodecs,
    std::shared_ptr<PlatformContext> platformContext) :
_thread(thread),
_eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
_taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
_sendSignalingMessage(std::move(sendSignalingMessage)),
_sendTransportMessage(std::move(sendTransportMessage)),
_signalBarsUpdated(std::move(signalBarsUpdated)),
_audioLevelUpdated(std::move(audioLevelUpdated)),
_createAudioDeviceModule(std::move(createAudioDeviceModule)),
_protocolVersion(protocolVersion),
_outgoingVideoState(videoCapture ? VideoState::Active : VideoState::Inactive),
_videoCapture(std::move(videoCapture)),
_enableHighBitrateVideo(enableHighBitrateVideo),
_platformContext(platformContext) {
    bool rewriteFrameRotation = false;
    switch (_protocolVersion) {
        case ProtocolVersion::V0:
            rewriteFrameRotation = true;
            break;
        case ProtocolVersion::V1:
            rewriteFrameRotation = false;
            break;
        default:
            break;
    }
    _incomingVideoSinkProxy.reset(new VideoSinkInterfaceProxyImpl(rewriteFrameRotation));

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
		"WebRTC-Audio-Allocation/min:32kbps,max:32kbps/"
		"WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
		"WebRTC-FlexFEC-03/Enabled/"
		"WebRTC-FlexFEC-03-Advertised/Enabled/"
        "WebRTC-Turn-AllowSystemPorts/Enabled/"
	);

	PlatformInterface::SharedInstance()->configurePlatformAudio();

	_videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

	cricket::MediaEngineDependencies mediaDeps;
	mediaDeps.task_queue_factory = _taskQueueFactory.get();
	mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
	mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();

    mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext);
    mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

    _myVideoFormats = ComposeSupportedFormats(
		mediaDeps.video_encoder_factory->GetSupportedFormats(),
		mediaDeps.video_decoder_factory->GetSupportedFormats(),
        preferredCodecs,
        _platformContext);

    webrtc::AudioProcessingBuilder builder;
    std::unique_ptr<AudioCapturePostProcessor> audioProcessor = std::make_unique<AudioCapturePostProcessor>([this](float level) {
        this->_thread->PostTask(RTC_FROM_HERE, [this, level](){
            auto strong = this;
            strong->_currentMyAudioLevel = level;
        });
    }, &_externalAudioSamples, &_externalAudioSamplesMutex);
    builder.SetCapturePostProcessing(std::move(audioProcessor));
    mediaDeps.audio_processing = builder.Create();

	_audioDeviceModule = this->createAudioDeviceModule();
	if (!_audioDeviceModule) {
		return;
	}
	mediaDeps.adm = _audioDeviceModule;

	_mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));
	_mediaEngine->Init();

	/*setAudioInputDevice(devicesConfig.audioInputId);
	setAudioOutputDevice(devicesConfig.audioOutputId);
	setInputVolume(devicesConfig.inputVolume);
	setOutputVolume(devicesConfig.outputVolume);*/

	webrtc::Call::Config callConfig(_eventLog.get());
	callConfig.task_queue_factory = _taskQueueFactory.get();
	callConfig.trials = &_fieldTrials;
	callConfig.audio_state = _mediaEngine->voice().GetAudioState();
	_call.reset(webrtc::Call::Create(callConfig));

    cricket::AudioOptions audioOptions;
    audioOptions.echo_cancellation = true;
    audioOptions.noise_suppression = true;
    audioOptions.audio_jitter_buffer_fast_accelerate = true;

    std::vector<std::string> streamIds;
    streamIds.push_back("1");

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
    cricket::StreamParams audioRecvStreamParams = cricket::StreamParams::CreateLegacy(_ssrcAudio.incoming);
    audioRecvStreamParams.set_stream_ids(streamIds);
    _audioChannel->AddRecvStream(audioRecvStreamParams);
	_audioChannel->SetPlayout(true);

	_videoChannel->SetInterface(_videoNetworkInterface.get());

    adjustBitratePreferences(true);
}

rtc::scoped_refptr<webrtc::AudioDeviceModule> MediaManager::createAudioDeviceModule() {
	const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
		return webrtc::AudioDeviceModule::Create(
			layer,
            _taskQueueFactory.get());
	};
	const auto check = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
        return (result && result->Init() == 0) ? result : nullptr;
	};
	if (_createAudioDeviceModule) {
        if (const auto result = check(_createAudioDeviceModule(_taskQueueFactory.get()))) {
            return result;
        }
	}
#ifdef ANDROID
    return check(create(webrtc::AudioDeviceModule::kAndroidMergedScreenAudio));
#else
    return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
#endif
}

void MediaManager::start() {
    const auto weak = std::weak_ptr<MediaManager>(shared_from_this());

    // Here we hope that thread outlives the sink
    rtc::Thread *thread = _thread;
    std::unique_ptr<AudioTrackSinkInterfaceImpl> incomingSink(new AudioTrackSinkInterfaceImpl([weak, thread](float level) {
        thread->PostTask(RTC_FROM_HERE, [weak, level] {
            if (const auto strong = weak.lock()) {
                strong->_currentAudioLevel = level;
            }
        });
    }));
    _audioChannel->SetRawAudioSink(_ssrcAudio.incoming, std::move(incomingSink));

    _sendSignalingMessage({ _myVideoFormats });

	if (_videoCapture != nullptr) {
        setSendVideo(_videoCapture);
    }

    beginStatsTimer(3000);
    if (_audioLevelUpdated != nullptr) {
        beginLevelsTimer(100);
    }
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

    _audioDeviceModule = nullptr;
}

void MediaManager::setIsConnected(bool isConnected) {
	if (_isConnected == isConnected) {
		return;
	}
    bool isFirstConnection = false;
    if (!_isConnected && isConnected) {
        _didConnectOnce = true;
        isFirstConnection = true;
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
    if (isFirstConnection) {
        sendVideoParametersMessage();
        sendOutgoingMediaStateMessage();
    }
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

void MediaManager::beginLevelsTimer(int timeoutMs) {
    const auto weak = std::weak_ptr<MediaManager>(shared_from_this());
    _thread->PostDelayedTask(RTC_FROM_HERE, [weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }

        float effectiveLevel = fmaxf(strong->_currentAudioLevel, strong->_currentMyAudioLevel);
        strong->_audioLevelUpdated(effectiveLevel);

        strong->beginLevelsTimer(100);
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

    _bitrateRecords.push_back(CallStatsBitrateRecord { (int32_t)(rtc::TimeMillis() / 1000), stats.send_bandwidth_bps / 1000 });

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
        const auto object = GetVideoCaptureAssumingSameThread(_videoCapture.get());
        _isScreenCapture = object->isScreenCapture();
		object->setStateUpdated([=](VideoState state) {
			thread->PostTask(RTC_FROM_HERE, [=] {
				if (const auto strong = weak.lock()) {
					strong->setOutgoingVideoState(state);
				}
			});
		});
        setOutgoingVideoState(VideoState::Active);
    } else {
        _isScreenCapture = false;

        setOutgoingVideoState(VideoState::Inactive);
    }

    if (_enableFlexfec) {
        _videoChannel->RemoveSendStream(_ssrcVideo.outgoing);
        _videoChannel->RemoveSendStream(_ssrcVideo.fecOutgoing);
    } else {
        _videoChannel->RemoveSendStream(_ssrcVideo.outgoing);
    }

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

    checkIsSendingVideoChanged(wasSending);
    checkIsReceivingVideoChanged(wasReceiving);
}

void MediaManager::sendVideoDeviceUpdated() {
    if (!computeIsSendingVideo()) {
        return;
    }
    const auto wasScreenCapture = _isScreenCapture;
    const auto object = GetVideoCaptureAssumingSameThread(_videoCapture.get());
    _isScreenCapture = object->isScreenCapture();
    if (_isScreenCapture != wasScreenCapture) {
        adjustBitratePreferences(true);
    }
}

void MediaManager::setRequestedVideoAspect(float aspect) {
    if (_localPreferredVideoAspectRatio != aspect) {
        _localPreferredVideoAspectRatio = aspect;
        if (_didConnectOnce) {
            sendVideoParametersMessage();
        }
    }
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
    switch (_protocolVersion) {
        case ProtocolVersion::V1:
            videoSendParameters.extensions.emplace_back(webrtc::RtpExtension::kVideoRotationUri, 3);
            videoSendParameters.extensions.emplace_back(
                webrtc::RtpExtension::kTimestampOffsetUri, 4);
            break;
        default:
            break;
    }
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
            _videoChannel->SetVideoSend(_ssrcVideo.outgoing, NULL, GetVideoCaptureAssumingSameThread(_videoCapture.get())->source());
            _videoChannel->SetVideoSend(_ssrcVideo.fecOutgoing, NULL, nullptr);
        } else {
            _videoChannel->SetVideoSend(_ssrcVideo.outgoing, NULL, GetVideoCaptureAssumingSameThread(_videoCapture.get())->source());
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

int MediaManager::getMaxAudioBitrate() const {
    if (_isDataSavingActive) {
        return 16000;
    } else {
        return 32000;
    }
}

void MediaManager::adjustBitratePreferences(bool resetStartBitrate) {
    if (computeIsSendingVideo()) {
        webrtc::BitrateConstraints preferences;
        if (_isScreenCapture) {
            preferences.min_bitrate_bps = 700000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 700000;
            }
        } else {
            preferences.min_bitrate_bps = 64000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 400000;
            }
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
            preferences.max_bitrate_bps = getMaxAudioBitrate();
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
#ifndef WEBRTC_DISABLE_H265
            cricket::kH265CodecName,
#endif
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
        switch (_protocolVersion) {
            case ProtocolVersion::V1:
                videoRecvParameters.extensions.emplace_back(webrtc::RtpExtension::kVideoRotationUri, 3);
                videoRecvParameters.extensions.emplace_back(
                    webrtc::RtpExtension::kTimestampOffsetUri, 4);
                break;
            default:
                break;
        }
        videoRecvParameters.rtcp.reduced_size = true;
        videoRecvParameters.rtcp.remote_estimate = true;

        cricket::StreamParams videoRecvStreamParams;
        cricket::SsrcGroup videoRecvSsrcGroup(cricket::kFecFrSsrcGroupSemantics, {_ssrcVideo.incoming, _ssrcVideo.fecIncoming});
        videoRecvStreamParams.ssrcs = {_ssrcVideo.incoming};
        videoRecvStreamParams.ssrc_groups.push_back(videoRecvSsrcGroup);
        videoRecvStreamParams.cname = "cname";
        std::vector<std::string> streamIds;
        streamIds.push_back("1");
        videoRecvStreamParams.set_stream_ids(streamIds);

        _videoChannel->SetRecvParameters(videoRecvParameters);
        _videoChannel->AddRecvStream(videoRecvStreamParams);
        _readyToReceiveVideo = true;
        _videoChannel->SetSink(_ssrcVideo.incoming, _incomingVideoSinkProxy.get());
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
    _incomingVideoSinkProxy->setSink(sink);
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

void MediaManager::setNetworkParameters(bool isLowCost, bool isDataSavingActive) {
    if (_isLowCostNetwork != isLowCost || _isDataSavingActive != isDataSavingActive) {
        _isLowCostNetwork = isLowCost;
        _isDataSavingActive = isDataSavingActive;
        RTC_LOG(LS_INFO) << "MediaManager isLowCostNetwork: " << (isLowCost ? 1 : 0) << ", isDataSavingActive: " << (isDataSavingActive ? 1 : 0);
        adjustBitratePreferences(false);
    }
}

void MediaManager::fillCallStats(CallStats &callStats) {
    if (_videoCodecOut.has_value()) {
        callStats.outgoingCodec = _videoCodecOut->name;
    }
    callStats.bitrateRecords = std::move(_bitrateRecords);
}

void MediaManager::setAudioInputDevice(std::string id) {
#if defined(WEBRTC_IOS)
#else
    SetAudioInputDeviceById(_audioDeviceModule.get(), id);
#endif
}

void MediaManager::setAudioOutputDevice(std::string id) {
#if defined(WEBRTC_IOS)
#else
    SetAudioOutputDeviceById(_audioDeviceModule.get(), id);
#endif
}

void MediaManager::setInputVolume(float level) {
	// This is not what we want, it changes OS volume on macOS.
//	auto min = uint32_t();
//	auto max = uint32_t();
//	if (const auto result = _audioDeviceModule->MinMicrophoneVolume(&min)) {
//		RTC_LOG(LS_ERROR) << "setInputVolume(" << level << "): MinMicrophoneVolume failed: " << result << ".";
//		return;
//	} else if (const auto result = _audioDeviceModule->MaxMicrophoneVolume(&max)) {
//		RTC_LOG(LS_ERROR) << "setInputVolume(" << level << "): MaxMicrophoneVolume failed: " << result << ".";
//		return;
//	}
//	const auto volume = min + uint32_t(std::round((max - min) * std::min(std::max(level, 0.f), 1.f)));
//	if (const auto result = _audioDeviceModule->SetMicrophoneVolume(volume)) {
//		RTC_LOG(LS_ERROR) << "setInputVolume(" << level << "): SetMicrophoneVolume(" << volume << ") failed: " << result << ".";
//	} else {
//		RTC_LOG(LS_INFO) << "setInputVolume(" << level << ") volume " << volume << " success.";
//	}
}

void MediaManager::setOutputVolume(float level) {
	// This is not what we want, it changes OS volume on macOS.
//	auto min = uint32_t();
//	auto max = uint32_t();
//	if (const auto result = _audioDeviceModule->MinSpeakerVolume(&min)) {
//		RTC_LOG(LS_ERROR) << "setOutputVolume(" << level << "): MinSpeakerVolume failed: " << result << ".";
//		return;
//	} else if (const auto result = _audioDeviceModule->MaxSpeakerVolume(&max)) {
//		RTC_LOG(LS_ERROR) << "setOutputVolume(" << level << "): MaxSpeakerVolume failed: " << result << ".";
//		return;
//	}
//	const auto volume = min + uint32_t(std::round((max - min) * std::min(std::max(level, 0.f), 1.f)));
//	if (const auto result = _audioDeviceModule->SetSpeakerVolume(volume)) {
//		RTC_LOG(LS_ERROR) << "setOutputVolume(" << level << "): SetSpeakerVolume(" << volume << ") failed: " << result << ".";
//	} else {
//		RTC_LOG(LS_INFO) << "setOutputVolume(" << level << ") volume " << volume << " success.";
//	}
}

void MediaManager::addExternalAudioSamples(std::vector<uint8_t> &&samples) {
    if (samples.size() % 2 != 0) {
        return;
    }
    _externalAudioSamplesMutex.Lock();

    size_t previousSize = _externalAudioSamples.size();
    _externalAudioSamples.resize(_externalAudioSamples.size() + samples.size() / 2);
    webrtc::S16ToFloatS16((const int16_t *)samples.data(), samples.size() / 2, _externalAudioSamples.data() + previousSize);

    if (_externalAudioSamples.size() > 2 * 48000) {
        _externalAudioSamples.erase(_externalAudioSamples.begin(), _externalAudioSamples.begin() + (_externalAudioSamples.size() - 2 * 48000));
    }

    _externalAudioSamplesMutex.Unlock();
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
