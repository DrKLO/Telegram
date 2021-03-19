#include "GroupInstanceCustomImpl.h"

#include <memory>
#include <iomanip>

#include "Instance.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "CodecSelectHelper.h"
#include "Message.h"
#include "platform/PlatformInterface.h"
#include "StaticThreads.h"
#include "GroupNetworkManager.h"

#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_decoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/audio_codecs/L16/audio_decoder_L16.h"
#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "system_wrappers/include/field_trial.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "call/call.h"
#include "modules/rtp_rtcp/source/rtp_utility.h"
#include "api/call/audio_sink.h"
#include "modules/audio_processing/audio_buffer.h"
#include "absl/strings/match.h"
#include "modules/audio_processing/agc2/vad_with_level.h"
#include "pc/channel_manager.h"
#include "media/base/rtp_data_engine.h"
#include "audio/audio_state.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"

#include "AudioFrame.h"
#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "CodecSelectHelper.h"
#include "StreamingPart.h"
#include "AudioDeviceHelper.h"

#include <random>
#include <sstream>
#include <iostream>

namespace tgcalls {

namespace {

static int stringToInt(std::string const &string) {
    std::stringstream stringStream(string);
    int value = 0;
    stringStream >> value;
    return value;
}

static std::string intToString(int value) {
    std::ostringstream stringStream;
    stringStream << value;
    return stringStream.str();
}

static std::string uint32ToString(uint32_t value) {
    std::ostringstream stringStream;
    stringStream << value;
    return stringStream.str();
}

static uint32_t stringToUInt32(std::string const &string) {
    std::stringstream stringStream(string);
    uint32_t value = 0;
    stringStream >> value;
    return value;
}

static uint16_t stringToUInt16(std::string const &string) {
    std::stringstream stringStream(string);
    uint16_t value = 0;
    stringStream >> value;
    return value;
}

static std::string formatTimestampMillis(int64_t timestamp) {
    std::ostringstream stringStream;
    stringStream << std::fixed << std::setprecision(3) << (double)timestamp / 1000.0;
    return stringStream.str();
}

static VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

struct OutgoingVideoFormat {
    cricket::VideoCodec videoCodec;
    cricket::VideoCodec rtxCodec;
};

static void addDefaultFeedbackParams(cricket::VideoCodec *codec) {
    // Don't add any feedback params for RED and ULPFEC.
    if (codec->name == cricket::kRedCodecName || codec->name == cricket::kUlpfecCodecName) {
        return;
    }
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamRemb, cricket::kParamValueEmpty));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc, cricket::kParamValueEmpty));
    // Don't add any more feedback params for FLEXFEC.
    if (codec->name == cricket::kFlexfecCodecName) {
        return;
    }
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamCcm, cricket::kRtcpFbCcmParamFir));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kParamValueEmpty));
    codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kRtcpFbNackParamPli));
}

static absl::optional<OutgoingVideoFormat> assignPayloadTypes(std::vector<webrtc::SdpVideoFormat> const &formats) {
    if (formats.empty()) {
        return absl::nullopt;
    }

    constexpr int kFirstDynamicPayloadType = 100;
    constexpr int kLastDynamicPayloadType = 127;

    int payload_type = kFirstDynamicPayloadType;

    auto result = OutgoingVideoFormat();

    bool codecSelected = false;

    for (const auto &format : formats) {
        if (codecSelected) {
            break;
        }

        cricket::VideoCodec codec(format);
        codec.id = payload_type;
        addDefaultFeedbackParams(&codec);

        if (!absl::EqualsIgnoreCase(codec.name, cricket::kVp8CodecName)) {
            continue;
        }

        result.videoCodec = codec;
        codecSelected = true;

        // Increment payload type.
        ++payload_type;
        if (payload_type > kLastDynamicPayloadType) {
            RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
            break;
        }

        // Add associated RTX codec for non-FEC codecs.
        if (!absl::EqualsIgnoreCase(codec.name, cricket::kUlpfecCodecName) &&
            !absl::EqualsIgnoreCase(codec.name, cricket::kFlexfecCodecName)) {
            result.rtxCodec = cricket::VideoCodec::CreateRtxCodec(payload_type, codec.id);

            // Increment payload type.
            ++payload_type;
            if (payload_type > kLastDynamicPayloadType) {
                RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
                break;
            }
        }
    }
    return result;
}

struct VideoSsrcs {
    struct SimulcastLayer {
        uint32_t ssrc = 0;
        uint32_t fidSsrc = 0;

        SimulcastLayer(uint32_t ssrc_, uint32_t fidSsrc_) :
            ssrc(ssrc_), fidSsrc(fidSsrc_) {
        }

        SimulcastLayer(const SimulcastLayer &other) :
            ssrc(other.ssrc), fidSsrc(other.fidSsrc) {
        }
    };

    std::vector<SimulcastLayer> simulcastLayers;

    VideoSsrcs() {
    }

    VideoSsrcs(const VideoSsrcs &other) :
        simulcastLayers(other.simulcastLayers) {
    }
};

struct ChannelId {
  uint32_t networkSsrc = 0;
  uint32_t actualSsrc = 0;

  ChannelId(uint32_t networkSsrc_, uint32_t actualSsrc_) :
      networkSsrc(networkSsrc_),
      actualSsrc(actualSsrc_) {
  }

  explicit ChannelId(uint32_t networkSsrc_) :
      networkSsrc(networkSsrc_),
      actualSsrc(networkSsrc_) {
  }

  bool operator <(const ChannelId& rhs) const {
    if (networkSsrc != rhs.networkSsrc) {
      return networkSsrc < rhs.networkSsrc;
    }
    return actualSsrc < rhs.actualSsrc;
  }

  std::string name() {
    if (networkSsrc == actualSsrc) {
      return uint32ToString(networkSsrc);
    } else {
      return uint32ToString(networkSsrc) + "to" + uint32ToString(actualSsrc);
    }
  }
};


class NetworkInterfaceImpl : public cricket::MediaChannel::NetworkInterface {
public:
    NetworkInterfaceImpl(std::function<void(rtc::CopyOnWriteBuffer const *, rtc::SentPacket)> sendPacket) :
    _sendPacket(sendPacket) {

    }

    bool SendPacket(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) {
        rtc::SentPacket sentPacket(options.packet_id, rtc::TimeMillis(), options.info_signaled_after_sent);
        _sendPacket(packet, sentPacket);
        return true;
    }

    bool SendRtcp(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options) {
        rtc::SentPacket sentPacket(options.packet_id, rtc::TimeMillis(), options.info_signaled_after_sent);
        _sendPacket(packet, sentPacket);
        return true;
    }

    int SetOption(cricket::MediaChannel::NetworkInterface::SocketType, rtc::Socket::Option, int) {
        return -1;
    }

private:
    std::function<void(rtc::CopyOnWriteBuffer const *, rtc::SentPacket)> _sendPacket;
};

static const int kVadResultHistoryLength = 8;

class CombinedVad {
private:
    webrtc::VadLevelAnalyzer _vadWithLevel;
    float _vadResultHistory[kVadResultHistoryLength];

public:
    CombinedVad() {
        for (float & i : _vadResultHistory) {
            i = 0.0f;
        }
    }

    ~CombinedVad() = default;

    bool update(webrtc::AudioBuffer *buffer) {
        float speech_probability;
        if (buffer) {
            webrtc::AudioFrameView<float> frameView(buffer->channels(), buffer->num_channels(), buffer->num_frames());
            auto result = _vadWithLevel.AnalyzeFrame(frameView);
            speech_probability = result.speech_probability;
        } else {
            speech_probability = std::min(1.0f, _vadResultHistory[kVadResultHistoryLength - 1] * 1.2f);
        }
        for (int i = 1; i < kVadResultHistoryLength; i++) {
            _vadResultHistory[i - 1] = _vadResultHistory[i];
        }
        _vadResultHistory[kVadResultHistoryLength - 1] = speech_probability;

        float movingAverage = 0.0f;
        for (float i : _vadResultHistory) {
            movingAverage += i;
        }
        movingAverage /= (float)kVadResultHistoryLength;

        bool vadResult = false;
        if (movingAverage > 0.8f) {
            vadResult = true;
        }

        return vadResult;
    }
};

class AudioSinkImpl: public webrtc::AudioSinkInterface {
public:
    struct Update {
        float level = 0.0f;
        std::shared_ptr<webrtc::AudioBuffer> buffer;
        std::shared_ptr<CombinedVad> vad;

        Update(float level_, webrtc::AudioBuffer *buffer_, std::shared_ptr<CombinedVad> vad_) :
            level(level_), buffer(std::shared_ptr<webrtc::AudioBuffer>(buffer_)), vad(vad_) {
        }
    };

public:
    AudioSinkImpl(std::function<void(Update)> update,
        ChannelId channel_id, std::function<void(uint32_t, const AudioFrame &)> onAudioFrame) :
    _update(update), _channel_id(channel_id), _onAudioFrame(std::move(onAudioFrame)) {
        _vad = std::make_shared<CombinedVad>();
    }

    virtual ~AudioSinkImpl() {
    }

    virtual void OnData(const Data& audio) override {
      if (_onAudioFrame) {
        AudioFrame frame;
        frame.audio_samples = audio.data;
        frame.num_samples = audio.samples_per_channel;
        frame.bytes_per_sample = 2;
        frame.num_channels = audio.channels;
        frame.samples_per_sec = audio.sample_rate;
        frame.elapsed_time_ms = 0;
        frame.ntp_time_ms = 0;
        _onAudioFrame(_channel_id.actualSsrc, frame);
      }
      if (audio.channels == 1) {
            const auto samples = (const int16_t *) audio.data;
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

                webrtc::AudioBuffer *buffer;
                if (!_skipNextSampleProcess) {
                    buffer = new webrtc::AudioBuffer(audio.sample_rate, 1, 48000, 1, 48000, 1);
                    webrtc::StreamConfig config(audio.sample_rate, 1);
                    buffer->CopyFrom(samples, config);
                } else {
                    buffer = nullptr;
                }

                _update(Update(level, buffer, _vad));
                _skipNextSampleProcess = !_skipNextSampleProcess;
            }
        }
    }

private:
    std::function<void(Update)> _update;
    ChannelId _channel_id;
    std::function<void(uint32_t, const AudioFrame &)> _onAudioFrame;
    std::shared_ptr<CombinedVad> _vad;

    int _peakCount = 0;
    uint16_t _peak = 0;
    bool _skipNextSampleProcess = false;
};

class VideoSinkImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoSinkImpl() {
    }

    virtual ~VideoSinkImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        _lastFrame = frame;
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnFrame(frame);
            }
        }
    }

    virtual void OnDiscardedFrame() override {
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnDiscardedFrame();
            }
        }
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _sinks.push_back(impl);
        if (_lastFrame) {
            auto strong = impl.lock();
            if (strong) {
                strong->OnFrame(_lastFrame.value());
            }
        }
    }

private:
    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> _sinks;
    absl::optional<webrtc::VideoFrame> _lastFrame;
};

class AudioCaptureAnalyzer : public webrtc::CustomAudioAnalyzer {
private:
    void Initialize(int sample_rate_hz, int num_channels) override {

    }

    void Analyze(const webrtc::AudioBuffer* buffer) override {
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

        bool vadStatus = _vad.update((webrtc::AudioBuffer *)buffer);

        _peakCount += peakCount;
        if (_peak < peak) {
            _peak = peak;
        }
        if (_peakCount >= 1200) {
            float level = _peak / 4000.0f;
            _peak = 0;
            _peakCount = 0;

            _updated(GroupLevelValue{
                level,
                vadStatus,
            });
        }
    }

    std::string ToString() const override {
        return "analyzing";
    }

private:
    std::function<void(GroupLevelValue const &)> _updated;

    CombinedVad _vad;
    int32_t _peakCount = 0;
    float _peak = 0;

public:
    AudioCaptureAnalyzer(std::function<void(GroupLevelValue const &)> updated) :
    _updated(updated) {
    }

    virtual ~AudioCaptureAnalyzer() = default;
};
class IncomingAudioChannel : public sigslot::has_slots<> {
public:
    IncomingAudioChannel(
        cricket::ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        bool isRawPcm,
        ChannelId ssrc,
        std::function<void(AudioSinkImpl::Update)> &&onAudioLevelUpdated,
        std::function<void(uint32_t, const AudioFrame &)> onAudioFrame,
        Threads &threads) :
    _ssrc(ssrc),
    _channelManager(channelManager),
    _call(call) {
        _creationTimestamp = rtc::TimeMillis();

        cricket::AudioOptions audioOptions;
        audioOptions.echo_cancellation = true;
        audioOptions.noise_suppression = true;
        audioOptions.audio_jitter_buffer_fast_accelerate = true;
        audioOptions.audio_jitter_buffer_min_delay_ms = 50;

        std::string streamId = std::string("stream") + ssrc.name();

        _audioChannel = _channelManager->CreateVoiceChannel(call, cricket::MediaConfig(), rtpTransport, threads.getMediaThread(), std::string("audio") + uint32ToString(ssrc.networkSsrc), false, GroupNetworkManager::getDefaulCryptoOptions(), randomIdGenerator, audioOptions);

        const uint8_t opusMinBitrateKbps = 32;
        const uint8_t opusMaxBitrateKbps = 32;
        const uint8_t opusStartBitrateKbps = 32;
        const uint8_t opusPTimeMs = 120;

        cricket::AudioCodec opusCodec(111, "opus", 48000, 0, 2);
        opusCodec.SetParam(cricket::kCodecParamMinBitrate, opusMinBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamStartBitrate, opusStartBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamMaxBitrate, opusMaxBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
        opusCodec.SetParam(cricket::kCodecParamPTime, opusPTimeMs);

        cricket::AudioCodec pcmCodec(112, "l16", 48000, 0, 1);

        auto outgoingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        if (!isRawPcm) {
            outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
            outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
            outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        }
        outgoingAudioDescription->set_rtcp_mux(true);
        outgoingAudioDescription->set_rtcp_reduced_size(true);
        outgoingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        outgoingAudioDescription->set_codecs({ opusCodec, pcmCodec });

        auto incomingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        if (!isRawPcm) {
            incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
            incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
            incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        }
        incomingAudioDescription->set_rtcp_mux(true);
        incomingAudioDescription->set_rtcp_reduced_size(true);
        incomingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        incomingAudioDescription->set_codecs({ opusCodec, pcmCodec });
        cricket::StreamParams streamParams = cricket::StreamParams::CreateLegacy(ssrc.networkSsrc);
        streamParams.set_stream_ids({ streamId });
        incomingAudioDescription->AddStream(streamParams);

        _audioChannel->SetPayloadTypeDemuxingEnabled(false);
        _audioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, nullptr);
        _audioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, nullptr);

        outgoingAudioDescription.reset();
        incomingAudioDescription.reset();

        std::unique_ptr<AudioSinkImpl> audioLevelSink(new AudioSinkImpl([onAudioLevelUpdated = std::move(onAudioLevelUpdated)](AudioSinkImpl::Update update) {
            onAudioLevelUpdated(update);
        }, _ssrc, std::move(onAudioFrame)));
        _audioChannel->media_channel()->SetRawAudioSink(ssrc.networkSsrc, std::move(audioLevelSink));

        _audioChannel->SignalSentPacket().connect(this, &IncomingAudioChannel::OnSentPacket_w);
        //_audioChannel->UpdateRtpTransport(nullptr);

        _audioChannel->Enable(true);
    }

    ~IncomingAudioChannel() {
        _audioChannel->SignalSentPacket().disconnect(this);
        _audioChannel->Enable(false);
        _channelManager->DestroyVoiceChannel(_audioChannel);
        _audioChannel = nullptr;
    }

    void setVolume(double value) {
        _audioChannel->media_channel()->SetOutputVolume(_ssrc.networkSsrc, value);
    }

    void updateActivity() {
        _activityTimestamp = rtc::TimeMillis();
    }

    int64_t getActivity() {
        return _activityTimestamp;
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    ChannelId _ssrc;
    // Memory is managed by _channelManager
    cricket::VoiceChannel *_audioChannel = nullptr;
    // Memory is managed externally
    cricket::ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;
    int64_t _creationTimestamp = 0;
    int64_t _activityTimestamp = 0;
};

class IncomingVideoChannel : public sigslot::has_slots<> {
public:
    IncomingVideoChannel(
        cricket::ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        std::vector<webrtc::SdpVideoFormat> const &availableVideoFormats,
        GroupParticipantDescription const &description,
        Threads &threads) :
    _channelManager(channelManager),
    _call(call) {
        _videoSink.reset(new VideoSinkImpl());

        std::string streamId = std::string("stream") + uint32ToString(description.audioSsrc);

        _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

        _videoChannel = _channelManager->CreateVideoChannel(call, cricket::MediaConfig(), rtpTransport, threads.getMediaThread(), std::string("video") + uint32ToString(description.audioSsrc), false, GroupNetworkManager::getDefaulCryptoOptions(), randomIdGenerator, cricket::VideoOptions(), _videoBitrateAllocatorFactory.get());

        auto payloadTypes = assignPayloadTypes(availableVideoFormats);
        if (!payloadTypes.has_value()) {
            return;
        }

        auto outgoingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kVideoRotationUri, 13));
        outgoingVideoDescription->set_rtcp_mux(true);
        outgoingVideoDescription->set_rtcp_reduced_size(true);
        outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        outgoingVideoDescription->set_codecs({ payloadTypes->videoCodec, payloadTypes->rtxCodec });

        cricket::StreamParams videoRecvStreamParams;

        std::vector<uint32_t> allSsrcs;
        for (const auto &group : description.videoSourceGroups) {
            for (auto ssrc : group.ssrcs) {
                if (std::find(allSsrcs.begin(), allSsrcs.end(), ssrc) == allSsrcs.end()) {
                    allSsrcs.push_back(ssrc);
                }
            }

            if (group.semantics == "SIM") {
                if (_mainVideoSsrc == 0) {
                    _mainVideoSsrc = group.ssrcs[0];
                }
            }

            cricket::SsrcGroup parsedGroup(group.semantics, group.ssrcs);
            videoRecvStreamParams.ssrc_groups.push_back(parsedGroup);
        }
        videoRecvStreamParams.ssrcs = allSsrcs;

        if (_mainVideoSsrc == 0) {
            if (description.videoSourceGroups.size() == 1) {
                _mainVideoSsrc = description.videoSourceGroups[0].ssrcs[0];
            }
        }

        videoRecvStreamParams.cname = "cname";
        videoRecvStreamParams.set_stream_ids({ streamId });

        auto incomingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kVideoRotationUri, 13));
        incomingVideoDescription->set_rtcp_mux(true);
        incomingVideoDescription->set_rtcp_reduced_size(true);
        incomingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        incomingVideoDescription->set_codecs({ payloadTypes->videoCodec, payloadTypes->rtxCodec });

        incomingVideoDescription->AddStream(videoRecvStreamParams);

        _videoChannel->SetPayloadTypeDemuxingEnabled(false);
        _videoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, nullptr);
        _videoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, nullptr);

        _videoChannel->media_channel()->SetSink(_mainVideoSsrc, _videoSink.get());

        _videoChannel->SignalSentPacket().connect(this, &IncomingVideoChannel::OnSentPacket_w);
        //_videoChannel->UpdateRtpTransport(nullptr);

        _videoChannel->Enable(true);
    }

    ~IncomingVideoChannel() {
        _videoChannel->Enable(false);
        _channelManager->DestroyVideoChannel(_videoChannel);
        _videoChannel = nullptr;
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _videoSink->addSink(impl);
    }

private:
    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

private:
    uint32_t _mainVideoSsrc = 0;
    std::unique_ptr<VideoSinkImpl> _videoSink;
    std::vector<GroupJoinPayloadVideoSourceGroup> _ssrcGroups;
    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
    // Memory is managed by _channelManager
    cricket::VideoChannel *_videoChannel;
    // Memory is managed externally
    cricket::ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;
};

struct SsrcMappingInfo {
    uint32_t ssrc = 0;
    bool isVideo = false;
    std::string endpointId;
};

class MissingSsrcPacketBuffer {
public:
    MissingSsrcPacketBuffer(int limit) :
    _limit(limit) {
    }

    ~MissingSsrcPacketBuffer() {
    }

    void add(uint32_t ssrc, rtc::CopyOnWriteBuffer const &packet) {
        if (_packets.size() == _limit) {
            _packets.erase(_packets.begin());
        }
        _packets.push_back(std::make_pair(ssrc, packet));
    }

    std::vector<rtc::CopyOnWriteBuffer> get(uint32_t ssrc) {
        std::vector<rtc::CopyOnWriteBuffer> result;
        for (auto it = _packets.begin(); it != _packets.end(); ) {
            if (it->first == ssrc) {
                result.push_back(it->second);
                _packets.erase(it);
            } else {
                it++;
            }
        }
        return result;
    }

private:
    int _limit = 0;
    std::vector<std::pair<uint32_t, rtc::CopyOnWriteBuffer>> _packets;

};

class RequestedBroadcastPart {
public:
    int64_t timestamp = 0;
    std::shared_ptr<BroadcastPartTask> task;

    explicit RequestedBroadcastPart(int64_t timestamp_, std::shared_ptr<BroadcastPartTask> task_) :
        timestamp(timestamp_), task(task_) {
    }
};

struct DecodedBroadcastPart {
    struct DecodedBroadcastPartChannel {
        uint32_t ssrc = 0;
        std::vector<int16_t> pcmData;
    };

    DecodedBroadcastPart(int numSamples_, std::vector<DecodedBroadcastPartChannel> &&_channels) :
        numSamples(numSamples_), channels(std::move(_channels)) {
    }

    int numSamples = 0;
    std::vector<DecodedBroadcastPartChannel> channels;
};

} // namespace

class GroupInstanceCustomInternal : public sigslot::has_slots<>, public std::enable_shared_from_this<GroupInstanceCustomInternal> {
public:
    GroupInstanceCustomInternal(GroupInstanceDescriptor &&descriptor, std::shared_ptr<Threads> threads) :
    _threads(std::move(threads)),
    _networkStateUpdated(descriptor.networkStateUpdated),
    _audioLevelsUpdated(descriptor.audioLevelsUpdated),
    _onAudioFrame(descriptor.onAudioFrame),
    _incomingVideoSourcesUpdated(descriptor.incomingVideoSourcesUpdated),
    _participantDescriptionsRequired(descriptor.participantDescriptionsRequired),
    _requestBroadcastPart(descriptor.requestBroadcastPart),
    _videoCapture(descriptor.videoCapture),
    _eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
    _taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
	_createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _initialInputDeviceId(std::move(descriptor.initialInputDeviceId)),
    _initialOutputDeviceId(std::move(descriptor.initialOutputDeviceId)),
    _missingPacketBuffer(100),
    _platformContext(descriptor.platformContext) {
        assert(_threads->getMediaThread()->IsCurrent());

        auto generator = std::mt19937(std::random_device()());
        auto distribution = std::uniform_int_distribution<uint32_t>();
        do {
            _outgoingAudioSsrc = distribution(generator) & 0x7fffffffU;
        } while (!_outgoingAudioSsrc);

        uint32_t outgoingVideoSsrcBase = _outgoingAudioSsrc + 1;
        int numVideoSimulcastLayers = 2;
        for (int layerIndex = 0; layerIndex < numVideoSimulcastLayers; layerIndex++) {
            _outgoingVideoSsrcs.simulcastLayers.push_back(VideoSsrcs::SimulcastLayer(outgoingVideoSsrcBase + layerIndex * 2 + 0, outgoingVideoSsrcBase + layerIndex * 2 + 1));
        }
    }

    ~GroupInstanceCustomInternal() {
        _call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkDown);
        _call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkDown);

        _incomingAudioChannels.clear();
        _incomingVideoChannels.clear();

        destroyOutgoingAudioChannel();

        if (_outgoingVideoChannel) {
            _outgoingVideoChannel->SignalSentPacket().disconnect(this);
            _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, nullptr, nullptr);
            _outgoingVideoChannel->Enable(false);
            _channelManager->DestroyVideoChannel(_outgoingVideoChannel);
            _outgoingVideoChannel = nullptr;
        }

        _channelManager = nullptr;
		_audioDeviceModule = nullptr;
    }

    void start() {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        webrtc::field_trial::InitFieldTrialsFromString(
            "WebRTC-Audio-Allocation/min:32kbps,max:32kbps/"
            "WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
        );

        _networkManager.reset(new ThreadLocalObject<GroupNetworkManager>(_threads->getNetworkThread(), [weak, threads = _threads] () mutable {
            return new GroupNetworkManager(
                [=](const GroupNetworkManager::State &state) {
                    threads->getMediaThread()->PostTask(RTC_FROM_HERE, [=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->setIsRtcConnected(state.isReadyToSendData);
                    });
                },
                [=](rtc::CopyOnWriteBuffer const &message, bool isUnresolved) {
                    threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, message, isUnresolved]() mutable {
                        if (const auto strong = weak.lock()) {
                            strong->receivePacket(message, isUnresolved);
                        }
                    });
                },
                [=](rtc::CopyOnWriteBuffer const &message, int64_t timestamp) {
                    threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, message, timestamp]() mutable {
                        if (const auto strong = weak.lock()) {
                            strong->receiveRtcpPacket(message, timestamp);
                        }
                    });
                },
                [=](bool isDataChannelOpen) {
                    threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isDataChannelOpen]() mutable {
                        if (const auto strong = weak.lock()) {
                            strong->updateIsDataChannelOpen(isDataChannelOpen);
                        }
                    });
                },
                [=](std::string const &message) {
                    threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, message]() mutable {
                        if (const auto strong = weak.lock()) {
                        }
                    });
                }, threads);
        }));

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        cricket::MediaEngineDependencies mediaDeps;
        mediaDeps.task_queue_factory = _taskQueueFactory.get();
        mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus, webrtc::AudioEncoderL16>();
        mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus, webrtc::AudioDecoderL16>();

        mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext);
        mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

        auto analyzer = new AudioCaptureAnalyzer([weak, threads = _threads](GroupLevelValue const &level) {
            threads->getProcessThread()->PostTask(RTC_FROM_HERE, [weak, level](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->_myAudioLevel = level;
            });
        });

        webrtc::AudioProcessingBuilder builder;
        builder.SetCaptureAnalyzer(std::unique_ptr<AudioCaptureAnalyzer>(analyzer));

        mediaDeps.audio_processing = builder.Create();

        _audioDeviceModule = createAudioDeviceModule();
        if (!_audioDeviceModule) {
            return;
        }
        mediaDeps.adm = _audioDeviceModule;

        _availableVideoFormats = mediaDeps.video_encoder_factory->GetSupportedFormats();

        std::unique_ptr<cricket::MediaEngineInterface> mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));

        _channelManager.reset(new cricket::ChannelManager(std::move(mediaEngine), std::make_unique<cricket::RtpDataEngine>(), _threads->getMediaThread(), _threads->getNetworkThread()));
        _channelManager->Init();

        setAudioInputDevice(_initialInputDeviceId);
        setAudioOutputDevice(_initialOutputDeviceId);

        webrtc::Call::Config callConfig(_eventLog.get());
        callConfig.task_queue_factory = _taskQueueFactory.get();
        callConfig.trials = &_fieldTrials;
        callConfig.audio_state = _channelManager->media_engine()->voice().GetAudioState();
        _call.reset(webrtc::Call::Create(callConfig));

        _uniqueRandomIdGenerator.reset(new rtc::UniqueRandomIdGenerator());

        _threads->getNetworkThread()->Invoke<void>(RTC_FROM_HERE, [this]() {
            _rtpTransport = _networkManager->getSyncAssumingSameThread()->getRtpTransport();
        });

        _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

        //_outgoingVideoChannel = _channelManager->CreateVideoChannel(_call.get(), cricket::MediaConfig(), _rtpTransport, _threads->getMediaThread(), "1", false, GroupNetworkManager::getDefaulCryptoOptions(), _uniqueRandomIdGenerator.get(), cricket::VideoOptions(), _videoBitrateAllocatorFactory.get());

        configureSendVideo();

        if (_outgoingVideoChannel) {
            _outgoingVideoChannel->SignalSentPacket().connect(this, &GroupInstanceCustomInternal::OnSentPacket_w);
            //_outgoingVideoChannel->UpdateRtpTransport(nullptr);
        }

        beginLevelsTimer(50);

        if (_videoCapture) {
            setVideoCapture(_videoCapture, [](GroupJoinPayload) {}, true);
        }

        adjustBitratePreferences(true);

        addIncomingAudioChannel("_dummy", ChannelId(1), true);

        beginNetworkStatusTimer(0);
    }

    void destroyOutgoingAudioChannel() {
        if (!_outgoingAudioChannel) {
            return;
        }

        _outgoingAudioChannel->SignalSentPacket().disconnect(this);
        _outgoingAudioChannel->media_channel()->SetAudioSend(_outgoingAudioSsrc, false, nullptr, &_audioSource);
        _outgoingAudioChannel->Enable(false);
        _channelManager->DestroyVoiceChannel(_outgoingAudioChannel);
        _outgoingAudioChannel = nullptr;
    }

    void createOutgoingAudioChannel() {
        if (_outgoingAudioChannel) {
            return;
        }

        cricket::AudioOptions audioOptions;
        audioOptions.echo_cancellation = true;
        audioOptions.noise_suppression = true;
        audioOptions.audio_jitter_buffer_fast_accelerate = true;

        std::vector<std::string> streamIds;
        streamIds.push_back("1");

        _outgoingAudioChannel = _channelManager->CreateVoiceChannel(_call.get(), cricket::MediaConfig(), _rtpTransport, _threads->getMediaThread(), "0", false, GroupNetworkManager::getDefaulCryptoOptions(), _uniqueRandomIdGenerator.get(), audioOptions);

        const uint8_t opusMinBitrateKbps = 32;
        const uint8_t opusMaxBitrateKbps = 32;
        const uint8_t opusStartBitrateKbps = 32;
        const uint8_t opusPTimeMs = 120;

        cricket::AudioCodec opusCodec(111, "opus", 48000, 0, 2);
        opusCodec.AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc));
        opusCodec.SetParam(cricket::kCodecParamMinBitrate, opusMinBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamStartBitrate, opusStartBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamMaxBitrate, opusMaxBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
        opusCodec.SetParam(cricket::kCodecParamPTime, opusPTimeMs);

        auto outgoingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        outgoingAudioDescription->set_rtcp_mux(true);
        outgoingAudioDescription->set_rtcp_reduced_size(true);
        outgoingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingAudioDescription->set_codecs({ opusCodec });
        outgoingAudioDescription->AddStream(cricket::StreamParams::CreateLegacy(_outgoingAudioSsrc));

        auto incomingAudioDescription = std::make_unique<cricket::AudioContentDescription>();
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        incomingAudioDescription->set_rtcp_mux(true);
        incomingAudioDescription->set_rtcp_reduced_size(true);
        incomingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingAudioDescription->set_codecs({ opusCodec });

        _outgoingAudioChannel->SetPayloadTypeDemuxingEnabled(false);
        _outgoingAudioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, nullptr);
        _outgoingAudioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, nullptr);

        _outgoingAudioChannel->SignalSentPacket().connect(this, &GroupInstanceCustomInternal::OnSentPacket_w);
        //_outgoingAudioChannel->UpdateRtpTransport(nullptr);

        onUpdatedIsMuted();
    }

    void stop() {
    }

    void beginLevelsTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getProcessThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            GroupLevelsUpdate levelsUpdate;
            levelsUpdate.updates.reserve(strong->_audioLevels.size() + 1);
            for (auto &it : strong->_audioLevels) {
                if (it.second.level > 0.001f) {
                    uint32_t effectiveSsrc = it.first.actualSsrc;
                    if (std::find_if(levelsUpdate.updates.begin(), levelsUpdate.updates.end(), [&](GroupLevelUpdate const &item) {
                        return item.ssrc == effectiveSsrc;
                    }) != levelsUpdate.updates.end()) {
                        continue;
                    }
                    levelsUpdate.updates.push_back(GroupLevelUpdate{
                        effectiveSsrc,
                        it.second,
                        });
                }
            }
            auto myAudioLevel = strong->_myAudioLevel;
            if (strong->_isMuted) {
                myAudioLevel.level = 0.0f;
                myAudioLevel.voice = false;
            }
            levelsUpdate.updates.push_back(GroupLevelUpdate{ 0, myAudioLevel });

            strong->_audioLevels.clear();
            if (strong->_audioLevelsUpdated) {
                strong->_audioLevelsUpdated(levelsUpdate);
            }

            strong->beginLevelsTimer(50);
        }, timeoutMs);
    }

    void beginNetworkStatusTimer(int delayMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (strong->_connectionMode == GroupConnectionMode::GroupConnectionModeBroadcast || strong->_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
                strong->updateBroadcastNetworkStatus();
            }

            strong->beginNetworkStatusTimer(500);
        }, delayMs);
    }

    void updateBroadcastNetworkStatus() {
        auto timestamp = rtc::TimeMillis();

        bool isBroadcastConnected = true;
        if (_lastBroadcastPartReceivedTimestamp < timestamp - 3000) {
            isBroadcastConnected = false;
        }

        if (_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
            auto timestamp = rtc::TimeMillis();
            if (std::abs(timestamp - _broadcastEnabledUntilRtcIsConnectedAtTimestamp.value()) > 3000) {
                _broadcastEnabledUntilRtcIsConnectedAtTimestamp = absl::nullopt;
                if (_currentRequestedBroadcastPart) {
                    if (_currentRequestedBroadcastPart->task) {
                        _currentRequestedBroadcastPart->task->cancel();
                    }
                    _currentRequestedBroadcastPart.reset();
                }
                isBroadcastConnected = false;
            }
        }

        if (isBroadcastConnected != _isBroadcastConnected) {
            _isBroadcastConnected = isBroadcastConnected;
            updateIsConnected();
        }
    }

    absl::optional<DecodedBroadcastPart> getNextBroadcastPart() {
        while (true) {
            if (_sourceBroadcastParts.size() != 0) {
                auto readChannels = _sourceBroadcastParts[0]->get10msPerChannel();
                if (readChannels.size() == 0 || readChannels[0].pcmData.size() == 0) {
                    _sourceBroadcastParts.erase(_sourceBroadcastParts.begin());
                } else {
                    std::vector<DecodedBroadcastPart::DecodedBroadcastPartChannel> channels;

                    int numSamples = (int)readChannels[0].pcmData.size();

                    for (auto &readChannel : readChannels) {
                        DecodedBroadcastPart::DecodedBroadcastPartChannel channel;
                        channel.ssrc = readChannel.ssrc;
                        channel.pcmData = std::move(readChannel.pcmData);
                        channels.push_back(channel);
                    }

                    absl::optional<DecodedBroadcastPart> decodedPart;
                    decodedPart.emplace(numSamples, std::move(channels));

                    return decodedPart;
                }
            } else {
                return absl::nullopt;
            }
        }

        return absl::nullopt;
    }

    void commitBroadcastPackets() {
        int numMillisecondsInQueue = 0;
        for (const auto &part : _sourceBroadcastParts) {
            numMillisecondsInQueue += part->getRemainingMilliseconds();
        }

        int commitMilliseconds = 20;
        if (numMillisecondsInQueue > 1000) {
            commitMilliseconds = numMillisecondsInQueue - 1000;
        }

        std::set<ChannelId> channelsWithActivity;

        for (int msIndex = 0; msIndex < commitMilliseconds; msIndex += 10) {
            auto packetData = getNextBroadcastPart();
            if (!packetData) {
                break;
            }

            for (const auto &decodedChannel : packetData->channels) {
                if (decodedChannel.ssrc == _outgoingAudioSsrc) {
                    continue;
                }

                ChannelId channelSsrc = ChannelId(decodedChannel.ssrc + 1000, decodedChannel.ssrc);
                if (_incomingAudioChannels.find(channelSsrc) == _incomingAudioChannels.end()) {
                    std::ostringstream os;
                    os << "broadcast" << channelSsrc.name();
                    addIncomingAudioChannel(os.str(), channelSsrc, true);
                }

                webrtc::RtpPacket packet(nullptr, 12 + decodedChannel.pcmData.size() * 2);

                packet.SetMarker(false);
                packet.SetPayloadType(112);

                uint16_t packetSeq = 0;

                auto it = _broadcastSeqBySsrc.find(channelSsrc.networkSsrc);
                if (it == _broadcastSeqBySsrc.end()) {
                    packetSeq = 1000;
                    _broadcastSeqBySsrc.insert(std::make_pair(channelSsrc.networkSsrc, packetSeq));
                } else {
                    it->second++;
                    packetSeq = it->second;
                }

                packet.SetSequenceNumber(packetSeq);

                packet.SetTimestamp(_broadcastTimestamp);

                packet.SetSsrc(channelSsrc.networkSsrc);

                uint8_t *payload = packet.SetPayloadSize(decodedChannel.pcmData.size() * 2);
                memcpy(payload, decodedChannel.pcmData.data(), decodedChannel.pcmData.size() * 2);

                for (int i = 0; i < decodedChannel.pcmData.size() * 2; i += 2) {
                    auto temp = payload[i];
                    payload[i] = payload[i + 1];
                    payload[i + 1] = temp;
                }

                auto buffer = packet.Buffer();
                _call->Receiver()->DeliverPacket(webrtc::MediaType::AUDIO, buffer, -1);

                channelsWithActivity.insert(ChannelId(channelSsrc));
            }

            for (const auto channelId : channelsWithActivity) {
                const auto it = _incomingAudioChannels.find(channelId);
                if (it != _incomingAudioChannels.end()) {
                    it->second->updateActivity();
                }
            }

            _broadcastTimestamp += packetData->numSamples;
        }
    }

    void requestNextBroadcastPart() {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        auto requestedPartId = _nextBroadcastTimestampMilliseconds;
        auto task = _requestBroadcastPart(_platformContext, requestedPartId, _broadcastPartDurationMilliseconds, [weak, threads = _threads, requestedPartId](BroadcastPart &&part) {
            threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, part = std::move(part), requestedPartId]() mutable {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                if (strong->_currentRequestedBroadcastPart && strong->_currentRequestedBroadcastPart->timestamp == requestedPartId) {
                    strong->onReceivedNextBroadcastPart(std::move(part));
                }
            });
        });
        if (_currentRequestedBroadcastPart) {
            if (_currentRequestedBroadcastPart->task) {
                _currentRequestedBroadcastPart->task->cancel();
            }
            _currentRequestedBroadcastPart.reset();
        }
        _currentRequestedBroadcastPart.emplace(requestedPartId, task);
    }

    void requestNextBroadcastPartWithDelay(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->requestNextBroadcastPart();
        }, timeoutMs);
    }

    void onReceivedNextBroadcastPart(BroadcastPart &&part) {
        _currentRequestedBroadcastPart.reset();

        if (_connectionMode != GroupConnectionMode::GroupConnectionModeBroadcast && !_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
            return;
        }

        int64_t responseTimestampMilliseconds = (int64_t)(part.responseTimestamp * 1000.0);

        int64_t responseTimestampBoundary = (responseTimestampMilliseconds / _broadcastPartDurationMilliseconds) * _broadcastPartDurationMilliseconds;

        switch (part.status) {
            case BroadcastPart::Status::Success: {
                _lastBroadcastPartReceivedTimestamp = rtc::TimeMillis();
                updateBroadcastNetworkStatus();

                if (std::abs((int64_t)(part.responseTimestamp * 1000.0) - part.timestampMilliseconds) > 2000) {
                    _nextBroadcastTimestampMilliseconds = std::max(part.timestampMilliseconds + _broadcastPartDurationMilliseconds, responseTimestampBoundary);
                } else {
                    _nextBroadcastTimestampMilliseconds = part.timestampMilliseconds + _broadcastPartDurationMilliseconds;
                }
                _sourceBroadcastParts.emplace_back(new StreamingPart(std::move(part.oggData)));
                break;
            }
            case BroadcastPart::Status::NotReady: {
                _nextBroadcastTimestampMilliseconds = part.timestampMilliseconds;
                break;
            }
            case BroadcastPart::Status::ResyncNeeded: {
                _nextBroadcastTimestampMilliseconds = responseTimestampBoundary;
                break;
            }
            default: {
                //RTC_FATAL() << "Unknown part.status";
                break;
            }
        }

        int64_t nextDelay = _nextBroadcastTimestampMilliseconds - responseTimestampMilliseconds;
        int clippedDelay = std::max((int)nextDelay, 100);

        //RTC_LOG(LS_INFO) << "requestNextBroadcastPartWithDelay(" << clippedDelay << ") (from " << nextDelay << ")";

        requestNextBroadcastPartWithDelay(clippedDelay);
    }

    void beginBroadcastPartsDecodeTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (strong->_connectionMode != GroupConnectionMode::GroupConnectionModeBroadcast && !strong->_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
                return;
            }

            strong->commitBroadcastPackets();

            strong->beginBroadcastPartsDecodeTimer(20);
        }, timeoutMs);
    }

    void configureSendVideo() {
        if (!_outgoingVideoChannel) {
            return;
        }

        auto payloadTypes = assignPayloadTypes(_availableVideoFormats);
        if (!payloadTypes.has_value()) {
            return;
        }

        GroupJoinPayloadVideoPayloadType vp8Payload;
        vp8Payload.id = payloadTypes.value().videoCodec.id;
        vp8Payload.name = payloadTypes.value().videoCodec.name;
        vp8Payload.clockrate = payloadTypes.value().videoCodec.clockrate;
        vp8Payload.channels = 0;

        std::vector<GroupJoinPayloadVideoPayloadFeedbackType> vp8FeedbackTypes;

        GroupJoinPayloadVideoPayloadFeedbackType fbGoogRemb;
        fbGoogRemb.type = "goog-remb";
        vp8FeedbackTypes.push_back(fbGoogRemb);

        GroupJoinPayloadVideoPayloadFeedbackType fbTransportCc;
        fbTransportCc.type = "transport-cc";
        vp8FeedbackTypes.push_back(fbTransportCc);

        GroupJoinPayloadVideoPayloadFeedbackType fbCcmFir;
        fbCcmFir.type = "ccm";
        fbCcmFir.subtype = "fir";
        vp8FeedbackTypes.push_back(fbCcmFir);

        GroupJoinPayloadVideoPayloadFeedbackType fbNack;
        fbNack.type = "nack";
        vp8FeedbackTypes.push_back(fbNack);

        GroupJoinPayloadVideoPayloadFeedbackType fbNackPli;
        fbNackPli.type = "nack";
        fbNackPli.subtype = "pli";
        vp8FeedbackTypes.push_back(fbNackPli);

        vp8Payload.feedbackTypes = vp8FeedbackTypes;
        vp8Payload.parameters = {};

        _videoPayloadTypes.push_back(std::move(vp8Payload));

        GroupJoinPayloadVideoPayloadType rtxPayload;
        rtxPayload.id = payloadTypes.value().rtxCodec.id;
        rtxPayload.name = payloadTypes.value().rtxCodec.name;
        rtxPayload.clockrate = payloadTypes.value().rtxCodec.clockrate;
        rtxPayload.parameters.push_back(std::make_pair("apt", intToString(payloadTypes.value().videoCodec.id)));
        _videoPayloadTypes.push_back(std::move(rtxPayload));

        auto outgoingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kVideoRotationUri, 13));

        for (const auto &extension : outgoingVideoDescription->rtp_header_extensions()) {
            _videoExtensionMap.push_back(std::make_pair(extension.id, extension.uri));
        }

        outgoingVideoDescription->set_rtcp_mux(true);
        outgoingVideoDescription->set_rtcp_reduced_size(true);
        outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingVideoDescription->set_codecs({ payloadTypes->videoCodec, payloadTypes->rtxCodec });

        cricket::StreamParams videoSendStreamParams;

        std::vector<uint32_t> simulcastGroupSsrcs;
        std::vector<cricket::SsrcGroup> fidGroups;
        for (const auto &layer : _outgoingVideoSsrcs.simulcastLayers) {
            simulcastGroupSsrcs.push_back(layer.ssrc);

            videoSendStreamParams.ssrcs.push_back(layer.ssrc);
            videoSendStreamParams.ssrcs.push_back(layer.fidSsrc);

            cricket::SsrcGroup fidGroup(cricket::kFidSsrcGroupSemantics, { layer.ssrc, layer.fidSsrc });
            fidGroups.push_back(fidGroup);
        }
        if (simulcastGroupSsrcs.size() > 1) {
            cricket::SsrcGroup simulcastGroup(cricket::kSimSsrcGroupSemantics, simulcastGroupSsrcs);
            videoSendStreamParams.ssrc_groups.push_back(simulcastGroup);

            GroupJoinPayloadVideoSourceGroup payloadSimulcastGroup;
            payloadSimulcastGroup.semantics = "SIM";
            payloadSimulcastGroup.ssrcs = simulcastGroupSsrcs;
            _videoSourceGroups.push_back(payloadSimulcastGroup);
        }

        for (auto fidGroup : fidGroups) {
            videoSendStreamParams.ssrc_groups.push_back(fidGroup);

            GroupJoinPayloadVideoSourceGroup payloadFidGroup;
            payloadFidGroup.semantics = "FID";
            payloadFidGroup.ssrcs = fidGroup.ssrcs;
            _videoSourceGroups.push_back(payloadFidGroup);
        }

        videoSendStreamParams.cname = "cname";

        outgoingVideoDescription->AddStream(videoSendStreamParams);

        auto incomingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kVideoRotationUri, 13));
        incomingVideoDescription->set_rtcp_mux(true);
        incomingVideoDescription->set_rtcp_reduced_size(true);
        incomingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingVideoDescription->set_codecs({ payloadTypes->videoCodec, payloadTypes->rtxCodec });

        _outgoingVideoChannel->SetPayloadTypeDemuxingEnabled(false);
        _outgoingVideoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, nullptr);
        _outgoingVideoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, nullptr);

        webrtc::RtpParameters rtpParameters = _outgoingVideoChannel->media_channel()->GetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc);
        if (rtpParameters.encodings.size() == 3) {
            for (int i = 0; i < (int)rtpParameters.encodings.size(); i++) {
                if (i == 0) {
                    rtpParameters.encodings[i].min_bitrate_bps = 50000;
                    rtpParameters.encodings[i].max_bitrate_bps = 100000;
                    rtpParameters.encodings[i].scale_resolution_down_by = 4.0;
                } else if (i == 1) {
                    rtpParameters.encodings[i].max_bitrate_bps = 150000;
                    rtpParameters.encodings[i].max_bitrate_bps = 200000;
                    rtpParameters.encodings[i].scale_resolution_down_by = 2.0;
                } else if (i == 2) {
                    rtpParameters.encodings[i].min_bitrate_bps = 300000;
                    rtpParameters.encodings[i].max_bitrate_bps = 800000;
                }
            }
        } else if (rtpParameters.encodings.size() == 2) {
            for (int i = 0; i < (int)rtpParameters.encodings.size(); i++) {
                if (i == 0) {
                    rtpParameters.encodings[i].min_bitrate_bps = 50000;
                    rtpParameters.encodings[i].max_bitrate_bps = 100000;
                    rtpParameters.encodings[i].scale_resolution_down_by = 4.0;
                } else if (i == 1) {
                    rtpParameters.encodings[i].min_bitrate_bps = 200000;
                    rtpParameters.encodings[i].max_bitrate_bps = 800000;
                }
            }
        }

        _outgoingVideoChannel->media_channel()->SetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, rtpParameters);
    }

    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

    void adjustBitratePreferences(bool resetStartBitrate) {
        webrtc::BitrateConstraints preferences;
        if (_videoCapture) {
            preferences.min_bitrate_bps = 64000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = (100 + 800 + 32 + 100) * 1000;
            }
            preferences.max_bitrate_bps = (100 + 200 + 800 + 32 + 100) * 1000;
        } else {
            preferences.min_bitrate_bps = 32000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 32000;
            }
            preferences.max_bitrate_bps = 32000;
        }

        _call->GetTransportControllerSend()->SetSdpBitrateParameters(preferences);
    }

    void setIsRtcConnected(bool isConnected) {
        if (_isRtcConnected == isConnected) {
            return;
        }
        _isRtcConnected = isConnected;

        RTC_LOG(LS_INFO) << formatTimestampMillis(rtc::TimeMillis()) << ": " << "setIsRtcConnected: " << _isRtcConnected;

        if (_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
            _broadcastEnabledUntilRtcIsConnectedAtTimestamp = absl::nullopt;
            if (_currentRequestedBroadcastPart) {
                if (_currentRequestedBroadcastPart->task) {
                    _currentRequestedBroadcastPart->task->cancel();
                }
                _currentRequestedBroadcastPart.reset();
            }
        }

        updateIsConnected();
    }

    void updateIsConnected() {
        bool isEffectivelyConnected = false;
        bool isTransitioningFromBroadcastToRtc = false;
        switch (_connectionMode) {
            case GroupConnectionMode::GroupConnectionModeNone: {
                isEffectivelyConnected = false;
                if (_broadcastEnabledUntilRtcIsConnectedAtTimestamp && _isBroadcastConnected) {
                    isEffectivelyConnected = true;
                    isTransitioningFromBroadcastToRtc = true;
                }
                break;
            }
            case GroupConnectionMode::GroupConnectionModeRtc: {
                isEffectivelyConnected = _isRtcConnected;
                if (_broadcastEnabledUntilRtcIsConnectedAtTimestamp && _isBroadcastConnected) {
                    isEffectivelyConnected = true;
                    isTransitioningFromBroadcastToRtc = true;
                }
                break;
            }
            case GroupConnectionMode::GroupConnectionModeBroadcast: {
                isEffectivelyConnected = _isBroadcastConnected;
                break;
            }
        }

        GroupNetworkState effectiveNetworkState;
        effectiveNetworkState.isConnected = isEffectivelyConnected;
        effectiveNetworkState.isTransitioningFromBroadcastToRtc = isTransitioningFromBroadcastToRtc;

        if (_effectiveNetworkState.isConnected != effectiveNetworkState.isConnected || _effectiveNetworkState.isTransitioningFromBroadcastToRtc != effectiveNetworkState.isTransitioningFromBroadcastToRtc) {
            _effectiveNetworkState = effectiveNetworkState;

            if (_effectiveNetworkState.isConnected) {
                _call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkUp);
                _call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkUp);
            } else {
                _call->SignalChannelNetworkState(webrtc::MediaType::AUDIO, webrtc::kNetworkDown);
                _call->SignalChannelNetworkState(webrtc::MediaType::VIDEO, webrtc::kNetworkDown);
            }

            if (_networkStateUpdated) {
                _networkStateUpdated(_effectiveNetworkState);
            }
        }
    }

    void updateIsDataChannelOpen(bool isDataChannelOpen) {
        if (_isDataChannelOpen == isDataChannelOpen) {
            return;
        }
        _isDataChannelOpen = isDataChannelOpen;

        if (_isDataChannelOpen) {
            maybeUpdateRemoteVideoConstaints();
        }
    }

    void receivePacket(rtc::CopyOnWriteBuffer const &packet, bool isUnresolved) {
        if (packet.size() >= 4) {
            if (packet.data()[0] == 0x13 && packet.data()[1] == 0x88 && packet.data()[2] == 0x13 && packet.data()[3] == 0x88) {
                // SCTP packet header (source port 5000, destination port 5000)
                return;
            }
        }

        webrtc::RtpUtility::RtpHeaderParser rtpParser(packet.data(), packet.size());

        webrtc::RTPHeader header;
        if (rtpParser.RTCP()) {
            if (!rtpParser.ParseRtcp(&header)) {
                RTC_LOG(LS_INFO) << "Could not parse rtcp header";
                return;
            }

            _call->Receiver()->DeliverPacket(webrtc::MediaType::ANY, packet, -1);
        } else {
            if (!rtpParser.Parse(&header)) {
                // Probably a data channel message
                return;
            }

            if (header.ssrc == _outgoingAudioSsrc) {
                return;
            }

            auto it = _ssrcMapping.find(header.ssrc);
            if (it == _ssrcMapping.end()) {
                if (isUnresolved) {
                    maybeReportUnknownSsrc(header.ssrc);
                    _missingPacketBuffer.add(header.ssrc, packet);
                }
            } else {
                const auto it = _incomingAudioChannels.find(ChannelId(header.ssrc));
                if (it != _incomingAudioChannels.end()) {
                    it->second->updateActivity();
                }
            }
        }
    }

    void receiveRtcpPacket(rtc::CopyOnWriteBuffer const &packet, int64_t timestamp) {
        _call->Receiver()->DeliverPacket(webrtc::MediaType::ANY, packet, timestamp);
    }

    void maybeReportUnknownSsrc(uint32_t ssrc) {
        if (_reportedUnknownSsrcs.find(ssrc) == _reportedUnknownSsrcs.end()) {
            _reportedUnknownSsrcs.insert(ssrc);

            _pendingUnknownSsrcs.insert(ssrc);

            if (!_isUnknownSsrcsScheduled) {
                auto timestamp = rtc::TimeMillis();
                if (_lastUnknownSsrcsReport < timestamp - 100) {
                    doReportPendingUnknownSsrcs();
                } else {
                    _isUnknownSsrcsScheduled = true;

                    const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
                    _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        strong->_isUnknownSsrcsScheduled = false;
                        strong->doReportPendingUnknownSsrcs();
                    }, 100);
                }
            }
        }
    }

    void doReportPendingUnknownSsrcs() {
      std::vector<uint32_t> ssrcs;
      for (auto ssrc : _pendingUnknownSsrcs) {
        ssrcs.push_back(ssrc);
      }
      _pendingUnknownSsrcs.clear();

      if (ssrcs.size() != 0) {
        _lastUnknownSsrcsReport = rtc::TimeMillis();
        if (_participantDescriptionsRequired) {
          _participantDescriptionsRequired(ssrcs);
        } else {
          std::vector<GroupParticipantDescription> participants;
          for (auto ssrc : ssrcs) {
             GroupParticipantDescription description;
             description.audioSsrc = ssrc;
             participants.push_back(std::move(description));
          }
          addParticipants(std::move(participants));
        }
      }
    }

    void maybeDeliverBufferedPackets(uint32_t ssrc) {
        // TODO: Re-enable after implementing custom transport
        /*auto packets = _missingPacketBuffer.get(ssrc);
        if (packets.size() != 0) {
            auto it = _ssrcMapping.find(ssrc);
            if (it != _ssrcMapping.end()) {
                for (const auto &packet : packets) {
                    _threads->getNetworkThread()->Invoke<void>(RTC_FROM_HERE, [this, packet]() {
                        _rtpTransport->DemuxPacketInternal(packet, -1);
                    });
                }
            }
        }*/
    }

    void maybeUpdateRemoteVideoConstaints() {
        if (!_isDataChannelOpen) {
            return;
        }

        std::vector<std::string> endpointIds;
        for (const auto &incomingVideoChannel : _incomingVideoChannels) {
            auto ssrcMapping = _ssrcMapping.find(incomingVideoChannel.first);
            if (ssrcMapping != _ssrcMapping.end()) {
                if (std::find(endpointIds.begin(), endpointIds.end(), ssrcMapping->second.endpointId) == endpointIds.end()) {
                    endpointIds.push_back(ssrcMapping->second.endpointId);
                }
            }
        }
        std::sort(endpointIds.begin(), endpointIds.end());

        std::string pinnedEndpoint;

        std::ostringstream string;
        string << "{" << "\n";
        string << " \"colibriClass\": \"ReceiverVideoConstraintsChangedEvent\"," << "\n";
        string << " \"videoConstraints\": [" << "\n";
        bool isFirst = true;
        for (size_t i = 0; i < endpointIds.size(); i++) {
            int idealHeight = 180;
            if (_currentHighQualityVideoEndpointId == endpointIds[i]) {
                idealHeight = 720;
            }

            if (isFirst) {
                isFirst = false;
            } else {
                if (i != 0) {
                    string << ",";
                }
            }
            string << "    {\n";
            string << "      \"id\": \"" << endpointIds[i] << "\",\n";
            string << "      \"idealHeight\": " << idealHeight << "\n";
            string << "    }";
            string << "\n";
        }
        string << " ]" << "\n";
        string << "}";

        std::string result = string.str();
        _networkManager->perform(RTC_FROM_HERE, [result = std::move(result)](GroupNetworkManager *networkManager) {
            networkManager->sendDataChannelMessage(result);
        });
    }

    void setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled) {
        if (_connectionMode != connectionMode) {
            GroupConnectionMode previousMode = _connectionMode;
            _connectionMode = connectionMode;
            onConnectionModeUpdated(previousMode, keepBroadcastIfWasEnabled);
        }
    }

    void onConnectionModeUpdated(GroupConnectionMode previousMode, bool keepBroadcastIfWasEnabled) {
        RTC_CHECK(_connectionMode != previousMode);

        if (previousMode == GroupConnectionMode::GroupConnectionModeRtc) {
            _networkManager->perform(RTC_FROM_HERE, [](GroupNetworkManager *networkManager) {
                networkManager->stop();
            });
        } else if (previousMode == GroupConnectionMode::GroupConnectionModeBroadcast) {
            if (keepBroadcastIfWasEnabled) {
                _broadcastEnabledUntilRtcIsConnectedAtTimestamp = rtc::TimeMillis();
            } else {
                if (_currentRequestedBroadcastPart) {
                    if (_currentRequestedBroadcastPart->task) {
                        _currentRequestedBroadcastPart->task->cancel();
                    }
                    _currentRequestedBroadcastPart.reset();
                }
            }
        }

        if (_connectionMode == GroupConnectionMode::GroupConnectionModeNone) {
            destroyOutgoingAudioChannel();

            auto generator = std::mt19937(std::random_device()());
            auto distribution = std::uniform_int_distribution<uint32_t>();
            do {
                _outgoingAudioSsrc = distribution(generator) & 0x7fffffffU;
            } while (!_outgoingAudioSsrc);

            if (!_isMuted) {
                createOutgoingAudioChannel();
            }
        }

        switch (_connectionMode) {
            case GroupConnectionMode::GroupConnectionModeNone: {
                break;
            }
            case GroupConnectionMode::GroupConnectionModeRtc: {
                _networkManager->perform(RTC_FROM_HERE, [](GroupNetworkManager *networkManager) {
                    networkManager->start();
                });
                break;
            }
            case GroupConnectionMode::GroupConnectionModeBroadcast: {
                _broadcastTimestamp = 100001;

                _isBroadcastConnected = false;

                beginBroadcastPartsDecodeTimer(0);
                requestNextBroadcastPart();

                break;
            }
            default: {
                //RTC_FATAL() << "Unknown connectionMode";
                break;
            }
        }

        updateIsConnected();
    }

    void emitJoinPayload(std::function<void(GroupJoinPayload)> completion) {
        _networkManager->perform(RTC_FROM_HERE, [outgoingAudioSsrc = _outgoingAudioSsrc, videoPayloadTypes = _videoPayloadTypes, videoExtensionMap = _videoExtensionMap, videoSourceGroups = _videoSourceGroups, completion](GroupNetworkManager *networkManager) {
            GroupJoinPayload payload;

            payload.ssrc = outgoingAudioSsrc;

            /*payload.videoPayloadTypes = videoPayloadTypes;
            payload.videoExtensionMap = videoExtensionMap;
            payload.videoSourceGroups = videoSourceGroups;*/

            auto localIceParameters = networkManager->getLocalIceParameters();
            payload.ufrag = localIceParameters.ufrag;
            payload.pwd = localIceParameters.pwd;

            auto localFingerprint = networkManager->getLocalFingerprint();
            if (localFingerprint) {
                GroupJoinPayloadFingerprint serializedFingerprint;
                serializedFingerprint.hash = localFingerprint->algorithm;
                serializedFingerprint.fingerprint = localFingerprint->GetRfc4572Fingerprint();
                serializedFingerprint.setup = "passive";
                payload.fingerprints.push_back(std::move(serializedFingerprint));
            }

            completion(payload);
        });
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, std::function<void(GroupJoinPayload)> completion, bool isInitializing) {
        bool resetBitrate = (_videoCapture == nullptr) != (videoCapture == nullptr) && !isInitializing;
        if (!isInitializing && _videoCapture == videoCapture) {
            return;
        }

        _videoCapture = videoCapture;

        if (_outgoingVideoChannel) {
            if (_videoCapture) {
                _outgoingVideoChannel->Enable(true);
                _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, NULL, GetVideoCaptureAssumingSameThread(_videoCapture.get())->source());
            } else {
                _outgoingVideoChannel->Enable(false);
                _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, NULL, nullptr);
            }
        }

        if (resetBitrate) {
            adjustBitratePreferences(true);
        }
    }

	void setAudioOutputDevice(const std::string &id) {
#if not defined(WEBRTC_IOS) && not defined(WEBRTC_ANDROID)
        SetAudioOutputDeviceById(_audioDeviceModule.get(), id);
#endif // WEBRTC_IOS
    }

    void setAudioInputDevice(const std::string &id) {
#if not defined(WEBRTC_IOS) && not defined(WEBRTC_ANDROID)
        SetAudioInputDeviceById(_audioDeviceModule.get(), id);
#endif // WEBRTC_IOS
    }

    void setJoinResponsePayload(GroupJoinResponsePayload payload, std::vector<tgcalls::GroupParticipantDescription> &&participants) {
        RTC_LOG(LS_INFO) << formatTimestampMillis(rtc::TimeMillis()) << ": " << "setJoinResponsePayload";

        _networkManager->perform(RTC_FROM_HERE, [payload](GroupNetworkManager *networkManager) {
            PeerIceParameters remoteIceParameters;
            remoteIceParameters.ufrag = payload.ufrag;
            remoteIceParameters.pwd = payload.pwd;

            std::vector<cricket::Candidate> iceCandidates;
            for (auto const &candidate : payload.candidates) {
                rtc::SocketAddress address(candidate.ip, stringToInt(candidate.port));

                cricket::Candidate parsedCandidate(
                    /*component=*/stringToInt(candidate.component),
                    /*protocol=*/candidate.protocol,
                    /*address=*/address,
                    /*priority=*/stringToUInt32(candidate.priority),
                    /*username=*/payload.ufrag,
                    /*password=*/payload.pwd,
                    /*type=*/candidate.type,
                    /*generation=*/stringToUInt32(candidate.generation),
                    /*foundation=*/candidate.foundation,
                    /*network_id=*/stringToUInt16(candidate.network),
                    /*network_cost=*/0
                );
                iceCandidates.push_back(parsedCandidate);
            }

            std::unique_ptr<rtc::SSLFingerprint> fingerprint;
            if (payload.fingerprints.size() != 0) {
                fingerprint = rtc::SSLFingerprint::CreateUniqueFromRfc4572(payload.fingerprints[0].hash, payload.fingerprints[0].fingerprint);
            }

            networkManager->setRemoteParams(remoteIceParameters, iceCandidates, fingerprint.get());
        });

        addParticipants(std::move(participants));
    }

    void addParticipants(std::vector<GroupParticipantDescription> &&participants) {
        if (_disableIncomingChannels) {
            return;
        }
        for (const auto &participant : participants) {
            if (participant.audioSsrc == _outgoingAudioSsrc) {
                continue;
            }

            _reportedUnknownSsrcs.erase(participant.audioSsrc);

            if (_incomingAudioChannels.find(ChannelId(participant.audioSsrc)) == _incomingAudioChannels.end()) {
                addIncomingAudioChannel(participant.endpointId, ChannelId(participant.audioSsrc));
            }
            if (participant.videoPayloadTypes.size() != 0 && participant.videoSourceGroups.size() != 0) {
                if (_incomingVideoChannels.find(participant.audioSsrc) == _incomingVideoChannels.end()) {
                    addIncomingVideoChannel(participant);
                }
            }
        }
    }

    void removeSsrcs(std::vector<uint32_t> ssrcs) {
        bool updatedIncomingVideoChannels = false;

        for (auto ssrc : ssrcs) {
            auto it = _ssrcMapping.find(ssrc);
            if (it != _ssrcMapping.end()) {
                auto mainSsrc = it->second.ssrc;
                auto audioChannel = _incomingAudioChannels.find(ChannelId(mainSsrc));
                if (audioChannel != _incomingAudioChannels.end()) {
                    _incomingAudioChannels.erase(audioChannel);
                }
                auto videoChannel = _incomingVideoChannels.find(mainSsrc);
                if (videoChannel != _incomingVideoChannels.end()) {
                    _incomingVideoChannels.erase(videoChannel);
                    updatedIncomingVideoChannels = true;
                }
            }
        }

        if (updatedIncomingVideoChannels) {
            updateIncomingVideoSources();
        }
    }

    void setIsMuted(bool isMuted) {
        if (_isMuted == isMuted) {
            return;
        }
        _isMuted = isMuted;

        onUpdatedIsMuted();
    }

    void onUpdatedIsMuted() {
        if (!_isMuted) {
            if (!_outgoingAudioChannel) {
                createOutgoingAudioChannel();
            }
        }

        if (_outgoingAudioChannel) {
            _outgoingAudioChannel->Enable(!_isMuted);
            _outgoingAudioChannel->media_channel()->SetAudioSend(_outgoingAudioSsrc, _isRtcConnected && !_isMuted, nullptr, &_audioSource);
        }
    }

    void addIncomingVideoOutput(uint32_t ssrc, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        auto it = _incomingVideoChannels.find(ssrc);
        if (it != _incomingVideoChannels.end()) {
            it->second->addSink(sink);
        }
    }

    void addIncomingAudioChannel(std::string const &endpointId, ChannelId ssrc, bool isRawPcm = false) {
        if (_incomingAudioChannels.find(ssrc) != _incomingAudioChannels.end()) {
            return;
        }

        if (_incomingAudioChannels.size() > 5) {
            int64_t minActivity = INT64_MAX;
            ChannelId minActivityChannelId(0, 0);

            for (const auto &it : _incomingAudioChannels) {
                auto activity = it.second->getActivity();
                if (activity < minActivity) {
                    minActivity = activity;
                    minActivityChannelId = it.first;
                }
            }

            if (minActivityChannelId.networkSsrc != 0) {
                const auto it = _incomingAudioChannels.find(minActivityChannelId);
                if (it != _incomingAudioChannels.end()) {
                    _incomingAudioChannels.erase(it);
                }
                auto reportedIt = _reportedUnknownSsrcs.find(minActivityChannelId.actualSsrc);
                if (reportedIt != _reportedUnknownSsrcs.end()) {
                    _reportedUnknownSsrcs.erase(reportedIt);
                }
                auto mappingIt = _ssrcMapping.find(minActivityChannelId.actualSsrc);
                if (mappingIt != _ssrcMapping.end()) {
                    _ssrcMapping.erase(mappingIt);
                }
            }
        }

        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        std::unique_ptr<IncomingAudioChannel> channel(new IncomingAudioChannel(
            _channelManager.get(),
            _call.get(),
            _rtpTransport,
            _uniqueRandomIdGenerator.get(),
            isRawPcm,
            ssrc,
            [weak, ssrc = ssrc, threads = _threads](AudioSinkImpl::Update update) {
                threads->getProcessThread()->PostTask(RTC_FROM_HERE, [weak, ssrc, update]() {
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    GroupLevelValue mappedUpdate;
                    mappedUpdate.level = update.level;
                    mappedUpdate.voice = update.vad->update(update.buffer.get());
                    strong->_audioLevels[ssrc] = mappedUpdate;
                });
            },
            _onAudioFrame,
            *_threads
        ));

        auto volume = _volumeBySsrc.find(ssrc.actualSsrc);
        if (volume != _volumeBySsrc.end()) {
            channel->setVolume(volume->second);
        }

        _incomingAudioChannels.insert(std::make_pair(ssrc, std::move(channel)));

        SsrcMappingInfo mapping;
        mapping.ssrc = ssrc.networkSsrc;
        mapping.isVideo = false;
        mapping.endpointId = endpointId;
        _ssrcMapping.insert(std::make_pair(ssrc.networkSsrc, mapping));

        maybeDeliverBufferedPackets(ssrc.networkSsrc);
    }

    void addIncomingVideoChannel(GroupParticipantDescription const &participant) {
        if (_incomingVideoChannels.find(participant.audioSsrc) != _incomingVideoChannels.end()) {
            return;
        }

        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        std::unique_ptr<IncomingVideoChannel> channel(new IncomingVideoChannel(
            _channelManager.get(),
            _call.get(),
            _rtpTransport,
            _uniqueRandomIdGenerator.get(),
            _availableVideoFormats,
            participant,
            *_threads
        ));
        _incomingVideoChannels.insert(std::make_pair(participant.audioSsrc, std::move(channel)));

        std::vector<uint32_t> allSsrcs;
        for (const auto &group : participant.videoSourceGroups) {
            for (auto ssrc : group.ssrcs) {
                if (_ssrcMapping.find(ssrc) == _ssrcMapping.end()) {
                    allSsrcs.push_back(ssrc);

                    SsrcMappingInfo mapping;
                    mapping.ssrc = participant.audioSsrc;
                    mapping.isVideo = true;
                    mapping.endpointId = participant.endpointId;
                    _ssrcMapping.insert(std::make_pair(ssrc, mapping));
                }
            }
        }

        updateIncomingVideoSources();

        for (auto ssrc : allSsrcs) {
            maybeDeliverBufferedPackets(ssrc);
        }
    }

    void updateIncomingVideoSources() {
        if (_incomingVideoSourcesUpdated) {
            std::vector<uint32_t> videoChannelSsrcs;
            for (const auto &it : _incomingVideoChannels) {
                videoChannelSsrcs.push_back(it.first);
            }
            _incomingVideoSourcesUpdated(videoChannelSsrcs);
        }
    }

    void setVolume(uint32_t ssrc, double volume) {
        auto current = _volumeBySsrc.find(ssrc);
        if (current != _volumeBySsrc.end() && std::abs(current->second - volume) < 0.0001) {
            return;
        }

        _volumeBySsrc[ssrc] = volume;

        auto it = _incomingAudioChannels.find(ChannelId(ssrc));
        if (it != _incomingAudioChannels.end()) {
            it->second->setVolume(volume);
        }

        it = _incomingAudioChannels.find(ChannelId(ssrc + 1000, ssrc));
        if (it != _incomingAudioChannels.end()) {
            it->second->setVolume(volume);
        }
    }

    void setFullSizeVideoSsrc(uint32_t ssrc) {
        auto ssrcMapping = _ssrcMapping.find(ssrc);
        std::string currentHighQualityVideoEndpointId;
        if (ssrcMapping != _ssrcMapping.end()) {
            currentHighQualityVideoEndpointId = ssrcMapping->second.endpointId;
        }
        if (_currentHighQualityVideoEndpointId != currentHighQualityVideoEndpointId) {
            _currentHighQualityVideoEndpointId = currentHighQualityVideoEndpointId;
            maybeUpdateRemoteVideoConstaints();
        }
    }

private:
    rtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule() {
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
		return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
    }

private:
    std::shared_ptr<Threads> _threads;
    GroupConnectionMode _connectionMode = GroupConnectionMode::GroupConnectionModeNone;

    std::function<void(GroupNetworkState)> _networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> _audioLevelsUpdated;
    std::function<void(uint32_t, const AudioFrame &)> _onAudioFrame;
    std::function<void(std::vector<uint32_t> const &)> _incomingVideoSourcesUpdated;
    std::function<void(std::vector<uint32_t> const &)> _participantDescriptionsRequired;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> _requestBroadcastPart;
    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    bool _disableIncomingChannels = false;

    int64_t _lastUnknownSsrcsReport = 0;
    std::set<uint32_t> _pendingUnknownSsrcs;
    bool _isUnknownSsrcsScheduled = false;
    std::set<uint32_t> _reportedUnknownSsrcs;

    std::unique_ptr<ThreadLocalObject<GroupNetworkManager>> _networkManager;

    std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;
    std::unique_ptr<cricket::MediaEngineInterface> _mediaEngine;
    std::unique_ptr<webrtc::Call> _call;
    webrtc::FieldTrialBasedConfig _fieldTrials;
    webrtc::LocalAudioSinkAdapter _audioSource;
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;
	std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;
    std::string _initialInputDeviceId;
    std::string _initialOutputDeviceId;

    // _outgoingAudioChannel memory is managed by _channelManager
    cricket::VoiceChannel *_outgoingAudioChannel = nullptr;
    uint32_t _outgoingAudioSsrc = 0;

    std::vector<webrtc::SdpVideoFormat> _availableVideoFormats;

    std::vector<GroupJoinPayloadVideoPayloadType> _videoPayloadTypes;
    std::vector<std::pair<uint32_t, std::string>> _videoExtensionMap;
    std::vector<GroupJoinPayloadVideoSourceGroup> _videoSourceGroups;

    std::unique_ptr<rtc::UniqueRandomIdGenerator> _uniqueRandomIdGenerator;
    webrtc::RtpTransport *_rtpTransport = nullptr;
    std::unique_ptr<cricket::ChannelManager> _channelManager;

    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
    // _outgoingVideoChannel memory is managed by _channelManager
    cricket::VideoChannel *_outgoingVideoChannel = nullptr;
    VideoSsrcs _outgoingVideoSsrcs;

    std::map<ChannelId, GroupLevelValue> _audioLevels;
    GroupLevelValue _myAudioLevel;

    bool _isMuted = true;

    MissingSsrcPacketBuffer _missingPacketBuffer;
    std::map<uint32_t, SsrcMappingInfo> _ssrcMapping;
    std::map<uint32_t, double> _volumeBySsrc;
    std::map<ChannelId, std::unique_ptr<IncomingAudioChannel>> _incomingAudioChannels;
    std::map<uint32_t, std::unique_ptr<IncomingVideoChannel>> _incomingVideoChannels;

    std::string _currentHighQualityVideoEndpointId;

    int64_t _broadcastPartDurationMilliseconds = 500;
    std::vector<std::unique_ptr<StreamingPart>> _sourceBroadcastParts;
    std::map<uint32_t, uint16_t> _broadcastSeqBySsrc;
    uint32_t _broadcastTimestamp = 0;
    int64_t _nextBroadcastTimestampMilliseconds = 0;
    absl::optional<RequestedBroadcastPart> _currentRequestedBroadcastPart;
    int64_t _lastBroadcastPartReceivedTimestamp = 0;

    bool _isRtcConnected = false;
    bool _isBroadcastConnected = false;
    absl::optional<int64_t> _broadcastEnabledUntilRtcIsConnectedAtTimestamp;
    bool _isDataChannelOpen = false;
    GroupNetworkState _effectiveNetworkState;

    std::shared_ptr<PlatformContext> _platformContext;
};

GroupInstanceCustomImpl::GroupInstanceCustomImpl(GroupInstanceDescriptor &&descriptor) {
    if (descriptor.config.need_log) {
      _logSink = std::make_unique<LogSinkImpl>(descriptor.config.logPath);
    }
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(false);
    if (_logSink) {
        rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    }

    _threads = descriptor.threads;
    _internal.reset(new ThreadLocalObject<GroupInstanceCustomInternal>(_threads->getMediaThread(), [descriptor = std::move(descriptor), threads = _threads]() mutable {
        return new GroupInstanceCustomInternal(std::move(descriptor), threads);
    }));
    _internal->perform(RTC_FROM_HERE, [](GroupInstanceCustomInternal *internal) {
        internal->start();
    });
}

GroupInstanceCustomImpl::~GroupInstanceCustomImpl() {
    if (_logSink) {
        rtc::LogMessage::RemoveLogToStream(_logSink.get());
    }
    _internal.reset();

    // Wait until _internal is destroyed
    _threads->getMediaThread()->Invoke<void>(RTC_FROM_HERE, [] {});
}

void GroupInstanceCustomImpl::stop() {
    _internal->perform(RTC_FROM_HERE, [](GroupInstanceCustomInternal *internal) {
        internal->stop();
    });
}

void GroupInstanceCustomImpl::setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled) {
    _internal->perform(RTC_FROM_HERE, [connectionMode, keepBroadcastIfWasEnabled](GroupInstanceCustomInternal *internal) {
        internal->setConnectionMode(connectionMode, keepBroadcastIfWasEnabled);
    });
}

void GroupInstanceCustomImpl::emitJoinPayload(std::function<void(GroupJoinPayload)> completion) {
    _internal->perform(RTC_FROM_HERE, [completion](GroupInstanceCustomInternal *internal) {
        internal->emitJoinPayload(completion);
    });
}

void GroupInstanceCustomImpl::setJoinResponsePayload(GroupJoinResponsePayload payload, std::vector<tgcalls::GroupParticipantDescription> &&participants) {
    _internal->perform(RTC_FROM_HERE, [payload, participants = std::move(participants)](GroupInstanceCustomInternal *internal) mutable {
        internal->setJoinResponsePayload(payload, std::move(participants));
    });
}

void GroupInstanceCustomImpl::addParticipants(std::vector<GroupParticipantDescription> &&participants) {
    _internal->perform(RTC_FROM_HERE, [participants = std::move(participants)](GroupInstanceCustomInternal *internal) mutable {
        internal->addParticipants(std::move(participants));
    });
}

void GroupInstanceCustomImpl::removeSsrcs(std::vector<uint32_t> ssrcs) {
    _internal->perform(RTC_FROM_HERE, [ssrcs = std::move(ssrcs)](GroupInstanceCustomInternal *internal) mutable {
        internal->removeSsrcs(ssrcs);
    });
}

void GroupInstanceCustomImpl::setIsMuted(bool isMuted) {
    _internal->perform(RTC_FROM_HERE, [isMuted](GroupInstanceCustomInternal *internal) {
        internal->setIsMuted(isMuted);
    });
}

void GroupInstanceCustomImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, std::function<void(GroupJoinPayload)> completion) {
    _internal->perform(RTC_FROM_HERE, [videoCapture, completion](GroupInstanceCustomInternal *internal) {
        internal->setVideoCapture(videoCapture, completion, false);
    });
}

void GroupInstanceCustomImpl::setAudioOutputDevice(std::string id) {
    _internal->perform(RTC_FROM_HERE, [id](GroupInstanceCustomInternal *internal) {
        internal->setAudioOutputDevice(id);
	});
}

void GroupInstanceCustomImpl::setAudioInputDevice(std::string id) {
	_internal->perform(RTC_FROM_HERE, [id](GroupInstanceCustomInternal *internal) {
		internal->setAudioInputDevice(id);
	});
}

void GroupInstanceCustomImpl::addIncomingVideoOutput(uint32_t ssrc, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform(RTC_FROM_HERE, [ssrc, sink](GroupInstanceCustomInternal *internal) mutable {
        internal->addIncomingVideoOutput(ssrc, sink);
    });
}

void GroupInstanceCustomImpl::setVolume(uint32_t ssrc, double volume) {
    _internal->perform(RTC_FROM_HERE, [ssrc, volume](GroupInstanceCustomInternal *internal) {
        internal->setVolume(ssrc, volume);
    });
}

void GroupInstanceCustomImpl::setFullSizeVideoSsrc(uint32_t ssrc) {
    _internal->perform(RTC_FROM_HERE, [ssrc](GroupInstanceCustomInternal *internal) {
        internal->setFullSizeVideoSsrc(ssrc);
    });
}

} // namespace tgcalls
