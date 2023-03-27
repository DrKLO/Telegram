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
#include "media/base/rtp_utils.h"
#include "api/call/audio_sink.h"
#include "modules/audio_processing/audio_buffer.h"
#include "absl/strings/match.h"
#include "modules/audio_processing/agc2/cpu_features.h"
#include "modules/audio_processing/agc2/vad_wrapper.h"
#include "pc/channel.h"
#include "pc/rtp_transport.h"
#include "audio/audio_state.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "common_audio/include/audio_util.h"
#include "modules/audio_device/include/audio_device_data_observer.h"
#include "common_audio/resampler/include/resampler.h"
#include "modules/rtp_rtcp/source/rtp_util.h"

#include "ChannelManager.h"
#include "AudioFrame.h"
#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "CodecSelectHelper.h"
#include "AudioStreamingPart.h"
#include "VideoStreamingPart.h"
#include "AudioDeviceHelper.h"
#include "FakeAudioDeviceModule.h"
#include "StreamingMediaContext.h"
#ifdef WEBRTC_IOS
#include "platform/darwin/iOS/tgcalls_audio_device_module_ios.h"
#endif
#include <mutex>
#include <random>
#include <sstream>
#include <iostream>


#ifndef USE_RNNOISE
#define USE_RNNOISE 1
#endif

#if USE_RNNOISE
#include "rnnoise.h"
#endif

#include "GroupJoinPayloadInternal.h"
#include "FieldTrialsConfig.h"

#include "third-party/json11.hpp"

namespace tgcalls {

namespace {

template <typename Out>
void splitString(const std::string &s, char delim, Out result) {
    std::istringstream iss(s);
    std::string item;
    while (std::getline(iss, item, delim)) {
        *result++ = item;
    }
}

std::vector<std::string> splitString(const std::string &s, char delim) {
    std::vector<std::string> elems;
    splitString(s, delim, std::back_inserter(elems));
    return elems;
}

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

struct H264FormatParameters {
    std::string profileLevelId;
    std::string packetizationMode;
    std::string levelAssymetryAllowed;
};

H264FormatParameters parseH264FormatParameters(webrtc::SdpVideoFormat const &format) {
    H264FormatParameters result;

    for (const auto &parameter : format.parameters) {
        if (parameter.first == "profile-level-id") {
            result.profileLevelId = parameter.second;
        } else if (parameter.first == "packetization-mode") {
            result.packetizationMode = parameter.second;
        } else if (parameter.first == "level-asymmetry-allowed") {
            result.levelAssymetryAllowed = parameter.second;
        }
    }

    return result;
}

static int getH264ProfileLevelIdPriority(std::string const &profileLevelId) {
    if (profileLevelId == cricket::kH264ProfileLevelConstrainedHigh) {
        return 0;
    } else if (profileLevelId == cricket::kH264ProfileLevelConstrainedBaseline) {
        return 1;
    } else {
        return 2;
    }
}

static int getH264PacketizationModePriority(std::string const &packetizationMode) {
    if (packetizationMode == "1") {
        return 0;
    } else {
        return 1;
    }
}

static int getH264LevelAssymetryAllowedPriority(std::string const &levelAssymetryAllowed) {
    if (levelAssymetryAllowed == "1") {
        return 0;
    } else {
        return 1;
    }
}

static std::vector<webrtc::SdpVideoFormat> filterSupportedVideoFormats(std::vector<webrtc::SdpVideoFormat> const &formats) {
    std::vector<webrtc::SdpVideoFormat> filteredFormats;

    std::vector<std::string> filterCodecNames = {
        cricket::kVp8CodecName,
        cricket::kVp9CodecName,
        cricket::kH264CodecName
    };

    std::vector<webrtc::SdpVideoFormat> vp9Formats;
    std::vector<webrtc::SdpVideoFormat> h264Formats;

    for (const auto &format : formats) {
        if (std::find(filterCodecNames.begin(), filterCodecNames.end(), format.name) == filterCodecNames.end()) {
            continue;
        }

        if (format.name == cricket::kVp9CodecName) {
            vp9Formats.push_back(format);
        } else if (format.name == cricket::kH264CodecName) {
            h264Formats.push_back(format);
        } else {
            filteredFormats.push_back(format);
        }
    }

    if (!vp9Formats.empty()) {
        bool added = false;
        for (const auto &format : vp9Formats) {
            if (added) {
                break;
            }
            for (const auto &parameter : format.parameters) {
                if (parameter.first == "profile-id") {
                    if (parameter.second == "0") {
                        filteredFormats.push_back(format);
                        added = true;
                        break;
                    }
                }
            }
        }

        if (!added) {
            filteredFormats.push_back(vp9Formats[0]);
        }
    }

    if (!h264Formats.empty()) {
        std::sort(h264Formats.begin(), h264Formats.end(), [](const webrtc::SdpVideoFormat &lhs, const webrtc::SdpVideoFormat &rhs) {
            auto lhsParameters = parseH264FormatParameters(lhs);
            auto rhsParameters = parseH264FormatParameters(rhs);

            int lhsLevelIdPriority = getH264ProfileLevelIdPriority(lhsParameters.profileLevelId);
            int lhsPacketizationModePriority = getH264PacketizationModePriority(lhsParameters.packetizationMode);
            int lhsLevelAssymetryAllowedPriority = getH264LevelAssymetryAllowedPriority(lhsParameters.levelAssymetryAllowed);

            int rhsLevelIdPriority = getH264ProfileLevelIdPriority(rhsParameters.profileLevelId);
            int rhsPacketizationModePriority = getH264PacketizationModePriority(rhsParameters.packetizationMode);
            int rhsLevelAssymetryAllowedPriority = getH264LevelAssymetryAllowedPriority(rhsParameters.levelAssymetryAllowed);

            if (lhsLevelIdPriority != rhsLevelIdPriority) {
                return lhsLevelIdPriority < rhsLevelIdPriority;
            }
            if (lhsPacketizationModePriority != rhsPacketizationModePriority) {
                return lhsPacketizationModePriority < rhsPacketizationModePriority;
            }
            if (lhsLevelAssymetryAllowedPriority != rhsLevelAssymetryAllowedPriority) {
                return lhsLevelAssymetryAllowedPriority < rhsLevelAssymetryAllowedPriority;
            }

            return false;
        });

        filteredFormats.push_back(h264Formats[0]);
    }

    return filteredFormats;
}

static std::vector<OutgoingVideoFormat> assignPayloadTypes(std::vector<webrtc::SdpVideoFormat> const &formats) {
    if (formats.empty()) {
        return {};
    }

    constexpr int kFirstDynamicPayloadType = 100;
    constexpr int kLastDynamicPayloadType = 127;

    int payload_type = kFirstDynamicPayloadType;

    std::vector<OutgoingVideoFormat> result;

    std::vector<std::string> filterCodecNames = {
        cricket::kVp8CodecName,
        cricket::kVp9CodecName,
        cricket::kH264CodecName,
    };

    for (const auto &codecName : filterCodecNames) {
        for (const auto &format : formats) {
            if (format.name != codecName) {
                continue;
            }

            cricket::VideoCodec codec(format);
            codec.id = payload_type;
            addDefaultFeedbackParams(&codec);

            OutgoingVideoFormat resultFormat;

            resultFormat.videoCodec = codec;

            // Increment payload type.
            ++payload_type;
            if (payload_type > kLastDynamicPayloadType) {
                RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
                break;
            }

            // Add associated RTX codec for non-FEC codecs.
            if (!absl::EqualsIgnoreCase(codec.name, cricket::kUlpfecCodecName) &&
                !absl::EqualsIgnoreCase(codec.name, cricket::kFlexfecCodecName)) {
                resultFormat.rtxCodec = cricket::VideoCodec::CreateRtxCodec(payload_type, codec.id);

                // Increment payload type.
                ++payload_type;
                if (payload_type > kLastDynamicPayloadType) {
                    RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
                    break;
                }
            }

            result.push_back(std::move(resultFormat));
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

struct InternalGroupLevelValue {
    GroupLevelValue value;
    int64_t timestamp = 0;
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

struct VideoChannelId {
    std::string endpointId;

    explicit VideoChannelId(std::string const &endpointId_) :
    endpointId(endpointId_) {
    }

    bool operator <(const VideoChannelId& rhs) const {
      return endpointId < rhs.endpointId;
    }
};

struct ChannelSsrcInfo {
    enum class Type {
        Audio,
        Video
    };

    Type type = Type::Audio;
    std::vector<uint32_t> allSsrcs;
    std::string videoEndpointId;
};

struct RequestedMediaChannelDescriptions {
    std::shared_ptr<RequestMediaChannelDescriptionTask> task;
    std::vector<uint32_t> ssrcs;

    RequestedMediaChannelDescriptions(std::shared_ptr<RequestMediaChannelDescriptionTask> task_, std::vector<uint32_t> ssrcs_) :
    task(task_), ssrcs(std::move(ssrcs_)) {
    }
};

static const int kVadResultHistoryLength = 6;

class VadHistory {
private:
    float _vadResultHistory[kVadResultHistoryLength];

public:
    VadHistory() {
        for (float & i : _vadResultHistory) {
            i = 0.0f;
        }
    }

    ~VadHistory() = default;

    bool update(float vadProbability) {
        if (vadProbability >= 0.0f) {
            for (int i = 1; i < kVadResultHistoryLength; i++) {
                _vadResultHistory[i - 1] = _vadResultHistory[i];
            }
            _vadResultHistory[kVadResultHistoryLength - 1] = vadProbability;
        }

        float movingAverage = 0.0f;
        for (float i : _vadResultHistory) {
            movingAverage += i;
        }
        movingAverage /= (float)kVadResultHistoryLength;

        return movingAverage > 0.6f;
    }
};

class CombinedVad {
private:
    webrtc::VoiceActivityDetectorWrapper _vadWithLevel;
    VadHistory _history;
    bool _countFrames;
    std::atomic<int32_t> _waitingFramesToProcess{0};

public:
    CombinedVad() :
    _vadWithLevel(500, webrtc::GetAvailableCpuFeatures(), webrtc::AudioProcessing::kSampleRate48kHz) {
    }

    ~CombinedVad() = default;

    bool incWaitingFrames() {
        if (_waitingFramesToProcess > 5) {
            return false;
        }
        _waitingFramesToProcess++;
        return true;
    }

    bool update(webrtc::AudioBuffer *buffer) {
        if (buffer->num_channels() <= 0) {
            return _history.update(0.0f);
        }
        webrtc::AudioFrameView<float> frameView(buffer->channels(), (int)(buffer->num_channels()), (int)(buffer->num_frames()));
        float peak = 0.0f;
        for (const auto &x : frameView.channel(0)) {
            peak = std::max(std::fabs(x), peak);
        }
        if (peak <= 0.01f) {
            return _history.update(false);
        }

        auto result = _vadWithLevel.Analyze(frameView);

        return _history.update(result);
    }

    bool update() {
        return _history.update(0.0f);
    }
};

class SparseVad {
public:
    SparseVad() {
    }

    bool update(webrtc::AudioBuffer *buffer) {
        _sampleCount += buffer->num_frames();
        if (_sampleCount < 400) {
            return _currentValue;
        }
        _sampleCount = 0;

        _currentValue = _vad.update(buffer);

        return _currentValue;
    }

private:
    CombinedVad _vad;
    bool _currentValue = false;
    size_t _sampleCount = 0;
};

class AudioSinkImpl: public webrtc::AudioSinkInterface {
public:
    struct Update {
        float level = 0.0f;
        bool hasSpeech = false;

        Update(float level_, bool hasSpech_) :
                level(level_), hasSpeech(hasSpech_) {
        }

        Update(const Update &other) :
                level(other.level), hasSpeech(other.hasSpeech) {
        }
    };

public:
    AudioSinkImpl(std::function<void(Update)> update,
        ChannelId channel_id, std::function<void(uint32_t, const AudioFrame &)> onAudioFrame) :
    _update(update), _channel_id(channel_id), _onAudioFrame(std::move(onAudioFrame)) {
        //_vad = std::make_shared<CombinedVad>(true);
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
        if (_update && audio.channels == 1) {
            const int16_t *samples = (const int16_t *)audio.data;
            int numberOfSamplesInFrame = (int)audio.samples_per_channel;

            int16_t currentPeak = 0;
            for (int i = 0; i < numberOfSamplesInFrame; i++) {
                int16_t sample = samples[i];
                if (sample < 0) {
                    sample = -sample;
                }
                if (_peak < sample) {
                    _peak = sample;
                }
                if (currentPeak < sample) {
                    currentPeak = sample;
                }
                _peakCount += 1;
            }

            if (_peakCount >= 4400) {
                float level = ((float)(_peak)) / 8000.0f;
                _peak = 0;
                _peakCount = 0;
                _update(Update(level, level >= 1.0f));
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
};

class VideoSinkImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoSinkImpl(std::string const &endpointId) :
    _endpointId(endpointId) {
    }

    virtual ~VideoSinkImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        std::unique_lock<std::mutex> lock{ _mutex };
        int64_t timestamp = rtc::TimeMillis();
        if (_lastFrame) {
            if (_lastFrame->video_frame_buffer()->width() != frame.video_frame_buffer()->width()) {
                int64_t deltaTime = std::abs(_lastFrameSizeChangeTimestamp - timestamp);
                if (deltaTime < 200) {
                    RTC_LOG(LS_WARNING) << "VideoSinkImpl: frequent frame size change detected for " << _endpointId << ": " << _lastFrameSizeChangeHeight << " -> " << _lastFrame->video_frame_buffer()->height() << " -> " << frame.video_frame_buffer()->height() << " in " << deltaTime << " ms";
                }

                _lastFrameSizeChangeHeight = _lastFrame->video_frame_buffer()->height();
                _lastFrameSizeChangeTimestamp = timestamp;
            }
        } else {
            _lastFrameSizeChangeHeight = 0;
            _lastFrameSizeChangeTimestamp = timestamp;
        }
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
        std::unique_lock<std::mutex> lock{ _mutex };
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
        if (const auto strong = impl.lock()) {
            std::unique_lock<std::mutex> lock{ _mutex };
            _sinks.push_back(impl);
            if (_lastFrame) {
                strong->OnFrame(_lastFrame.value());
            }
        }
    }

    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> getSinks() {
        return _sinks;
    }

private:
    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> _sinks;
    absl::optional<webrtc::VideoFrame> _lastFrame;
    std::mutex _mutex;
    int64_t _lastFrameSizeChangeTimestamp = 0;
    int _lastFrameSizeChangeHeight = 0;
    std::string _endpointId;

};

struct NoiseSuppressionConfiguration {
    NoiseSuppressionConfiguration(bool isEnabled_) :
    isEnabled(isEnabled_) {

    }

    bool isEnabled = false;
};

#if USE_RNNOISE
class AudioCapturePostProcessor : public webrtc::CustomProcessing {
public:
    AudioCapturePostProcessor(std::function<void(GroupLevelValue const &)> updated, std::shared_ptr<NoiseSuppressionConfiguration> noiseSuppressionConfiguration, std::vector<float> *externalAudioSamples, webrtc::Mutex *externalAudioSamplesMutex) :
    _updated(updated),
    _noiseSuppressionConfiguration(noiseSuppressionConfiguration),
    _externalAudioSamples(externalAudioSamples),
    _externalAudioSamplesMutex(externalAudioSamplesMutex) {
        int frameSize = rnnoise_get_frame_size();
        _frameSamples.resize(frameSize);

        _denoiseState = rnnoise_create(nullptr);
    }

    virtual ~AudioCapturePostProcessor() {
        if (_denoiseState) {
            rnnoise_destroy(_denoiseState);
        }
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
        if (!_denoiseState) {
            return;
        }
        if (buffer->num_frames() != _frameSamples.size()) {
            return;
        }

        float sourcePeak = 0.0f;
        float *sourceSamples = buffer->channels()[0];
        for (int i = 0; i < _frameSamples.size(); i++) {
            sourcePeak = std::max(std::fabs(sourceSamples[i]), sourcePeak);
        }

        if (_noiseSuppressionConfiguration->isEnabled) {
            float vadProbability = 0.0f;
            if (sourcePeak >= 0.01f) {
                vadProbability = rnnoise_process_frame(_denoiseState, _frameSamples.data(), buffer->channels()[0]);
                if (_noiseSuppressionConfiguration->isEnabled) {
                    memcpy(buffer->channels()[0], _frameSamples.data(), _frameSamples.size() * sizeof(float));
                }
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

            bool vadStatus = _history.update(vadProbability);

            _peakCount += peakCount;
            if (_peak < peak) {
                _peak = peak;
            }
            if (_peakCount >= 4400) {
                float level = _peak / 4000.0f;
                _peak = 0;
                _peakCount = 0;

                _updated(GroupLevelValue{
                    level,
                    vadStatus,
                });
            }
        } else {
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

                _updated(GroupLevelValue{
                    level,
                    level >= 1.0f,
                });
            }
        }

        if (_externalAudioSamplesMutex && _externalAudioSamples) {
            _externalAudioSamplesMutex->Lock();
            if (!_externalAudioSamples->empty()) {
                float *bufferData = buffer->channels()[0];
                int takenSamples = 0;
                for (int i = 0; i < _externalAudioSamples->size() && i < _frameSamples.size(); i++) {
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
    }

    virtual std::string ToString() const override {
        return "CustomPostProcessing";
    }

    virtual void SetRuntimeSetting(webrtc::AudioProcessing::RuntimeSetting setting) override {
    }

private:
    std::function<void(GroupLevelValue const &)> _updated;
    std::shared_ptr<NoiseSuppressionConfiguration> _noiseSuppressionConfiguration;

    DenoiseState *_denoiseState = nullptr;
    std::vector<float> _frameSamples;
    int32_t _peakCount = 0;
    float _peak = 0;
    VadHistory _history;
    SparseVad _vad;

    std::vector<float> *_externalAudioSamples = nullptr;
    webrtc::Mutex *_externalAudioSamplesMutex = nullptr;
};
#endif

class ExternalAudioRecorder : public FakeAudioDeviceModule::Recorder {
public:
    ExternalAudioRecorder(std::vector<float> *externalAudioSamples, webrtc::Mutex *externalAudioSamplesMutex) :
    _externalAudioSamples(externalAudioSamples),
    _externalAudioSamplesMutex(externalAudioSamplesMutex) {
        _samples.resize(480);
    }

    virtual ~ExternalAudioRecorder() {
    }

    virtual AudioFrame Record() override {
        AudioFrame result;

        _externalAudioSamplesMutex->Lock();
        if (!_externalAudioSamples->empty() && _externalAudioSamples->size() >= 480) {
            size_t takenSamples = std::min(_samples.size(), _externalAudioSamples->size());
            webrtc::FloatS16ToS16(_externalAudioSamples->data(), takenSamples, _samples.data());

            result.num_samples = takenSamples;

            if (takenSamples != 0) {
                _externalAudioSamples->erase(_externalAudioSamples->begin(), _externalAudioSamples->begin() + takenSamples);
            }
        } else {
            result.num_samples = 0;
        }
        _externalAudioSamplesMutex->Unlock();

        result.audio_samples = _samples.data();
        result.bytes_per_sample = 2;
        result.num_channels = 1;
        result.samples_per_sec = 48000;
        result.elapsed_time_ms = 0;
        result.ntp_time_ms = 0;

        return result;
    }

    virtual int32_t WaitForUs() override {
        _externalAudioSamplesMutex->Lock();
        _externalAudioSamplesMutex->Unlock();

        return 1000;
    }

private:
    std::vector<float> *_externalAudioSamples = nullptr;
    webrtc::Mutex *_externalAudioSamplesMutex = nullptr;
    std::vector<int16_t> _samples;
};

class IncomingAudioChannel : public sigslot::has_slots<> {
public:
    IncomingAudioChannel(
        ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        bool isRawPcm,
        ChannelId ssrc,
        std::function<void(AudioSinkImpl::Update)> &&onAudioLevelUpdated,
        std::function<void(uint32_t, const AudioFrame &)> onAudioFrame,
        std::shared_ptr<Threads> threads) :
    _threads(threads),
    _ssrc(ssrc),
    _channelManager(channelManager),
    _call(call) {
        _creationTimestamp = rtc::TimeMillis();

        threads->getWorkerThread()->BlockingCall([this, rtpTransport, ssrc, onAudioFrame = std::move(onAudioFrame), onAudioLevelUpdated = std::move(onAudioLevelUpdated), isRawPcm]() mutable {
            cricket::AudioOptions audioOptions;
            audioOptions.audio_jitter_buffer_fast_accelerate = true;
            audioOptions.audio_jitter_buffer_min_delay_ms = 50;

            std::string streamId = std::string("stream") + ssrc.name();

            _audioChannel = _channelManager->CreateVoiceChannel(_call, cricket::MediaConfig(), std::string("audio") + uint32ToString(ssrc.networkSsrc), false, GroupNetworkManager::getDefaulCryptoOptions(), audioOptions);

            _threads->getNetworkThread()->BlockingCall([&]() {
                _audioChannel->SetRtpTransport(rtpTransport);
            });

            const uint8_t opusPTimeMs = 120;

            cricket::AudioCodec opusCodec(111, "opus", 48000, 0, 2);
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
            outgoingAudioDescription->set_bandwidth(1300000);

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
            incomingAudioDescription->set_bandwidth(1300000);
            cricket::StreamParams streamParams = cricket::StreamParams::CreateLegacy(ssrc.networkSsrc);
            streamParams.set_stream_ids({ streamId });
            incomingAudioDescription->AddStream(streamParams);

            std::string errorDesc;
            _audioChannel->SetPayloadTypeDemuxingEnabled(false);
            _audioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _audioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, errorDesc);

            outgoingAudioDescription.reset();
            incomingAudioDescription.reset();

            if (_ssrc.actualSsrc != 1) {
                std::unique_ptr<AudioSinkImpl> audioLevelSink(new AudioSinkImpl(std::move(onAudioLevelUpdated), _ssrc, std::move(onAudioFrame)));
                _audioChannel->media_channel()->SetRawAudioSink(ssrc.networkSsrc, std::move(audioLevelSink));
            }
        });

        _audioChannel->Enable(true);
    }

    ~IncomingAudioChannel() {
        _threads->getNetworkThread()->BlockingCall([&]() {
            _audioChannel->SetRtpTransport(nullptr);
        });
        _threads->getWorkerThread()->BlockingCall([this]() {
            _channelManager->DestroyChannel(_audioChannel);
            _audioChannel = nullptr;
        });
    }

    void setVolume(double value) {
        _threads->getWorkerThread()->BlockingCall([this, value]() {
            _audioChannel->media_channel()->SetOutputVolume(_ssrc.networkSsrc, value);
        });
    }

    void updateActivity() {
        _activityTimestamp = rtc::TimeMillis();
    }

    int64_t getActivity() {
        return _activityTimestamp;
    }

private:
    std::shared_ptr<Threads> _threads;
    ChannelId _ssrc;
    // Memory is managed by _channelManager
    cricket::VoiceChannel *_audioChannel = nullptr;
    // Memory is managed externally
    ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;
    int64_t _creationTimestamp = 0;
    int64_t _activityTimestamp = 0;
};

class IncomingVideoChannel : public sigslot::has_slots<> {
public:
    IncomingVideoChannel(
        ChannelManager *channelManager,
        webrtc::Call *call,
        webrtc::RtpTransport *rtpTransport,
        rtc::UniqueRandomIdGenerator *randomIdGenerator,
        std::vector<webrtc::SdpVideoFormat> const &availableVideoFormats,
        GroupJoinVideoInformation sharedVideoInformation,
        uint32_t audioSsrc,
        VideoChannelDescription::Quality minQuality,
        VideoChannelDescription::Quality maxQuality,
        GroupParticipantVideoInformation const &description,
        std::shared_ptr<Threads> threads) :
    _threads(threads),
    _endpointId(description.endpointId),
    _channelManager(channelManager),
    _call(call),
    _requestedMinQuality(minQuality),
    _requestedMaxQuality(maxQuality) {
        _videoSink.reset(new VideoSinkImpl(_endpointId));

        _threads->getWorkerThread()->BlockingCall([this, rtpTransport, &availableVideoFormats, &description, randomIdGenerator]() mutable {
            uint32_t mid = randomIdGenerator->GenerateId();
            std::string streamId = std::string("video") + uint32ToString(mid);

            _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

            auto payloadTypes = assignPayloadTypes(availableVideoFormats);
            std::vector<cricket::VideoCodec> codecs;
            for (const auto &payloadType : payloadTypes) {
                codecs.push_back(payloadType.videoCodec);
                codecs.push_back(payloadType.rtxCodec);
            }

            auto outgoingVideoDescription = std::make_unique<cricket::VideoContentDescription>();
            outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
            outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
            outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kVideoRotationUri, 13));
            outgoingVideoDescription->set_rtcp_mux(true);
            outgoingVideoDescription->set_rtcp_reduced_size(true);
            outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
            outgoingVideoDescription->set_codecs(codecs);
            outgoingVideoDescription->set_bandwidth(1300000);

            cricket::StreamParams videoRecvStreamParams;

            std::vector<uint32_t> allSsrcs;
            for (const auto &group : description.ssrcGroups) {
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
                if (description.ssrcGroups.size() == 1) {
                    _mainVideoSsrc = description.ssrcGroups[0].ssrcs[0];
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
            incomingVideoDescription->set_codecs(codecs);
            incomingVideoDescription->set_bandwidth(1300000);

            incomingVideoDescription->AddStream(videoRecvStreamParams);

            _videoChannel = _channelManager->CreateVideoChannel(_call, cricket::MediaConfig(), std::string("video") + uint32ToString(mid), false, GroupNetworkManager::getDefaulCryptoOptions(), cricket::VideoOptions(), _videoBitrateAllocatorFactory.get());

            _threads->getNetworkThread()->BlockingCall([&]() {
                _videoChannel->SetRtpTransport(rtpTransport);
            });

            std::string errorDesc;
            _videoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _videoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, errorDesc);
            _videoChannel->SetPayloadTypeDemuxingEnabled(false);
            _videoChannel->media_channel()->SetSink(_mainVideoSsrc, _videoSink.get());
        });

        _videoChannel->Enable(true);
    }

    ~IncomingVideoChannel() {
        _videoChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _videoChannel->SetRtpTransport(nullptr);
        });
        _threads->getWorkerThread()->BlockingCall([this]() {
            _channelManager->DestroyChannel(_videoChannel);
            _videoChannel = nullptr;
        });
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _videoSink->addSink(impl);
    }

    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> getSinks() {
        return _videoSink->getSinks();
    }

    std::string const &endpointId() {
        return _endpointId;
    }

    VideoChannelDescription::Quality requestedMinQuality() {
        return _requestedMinQuality;
    }

    VideoChannelDescription::Quality requestedMaxQuality() {
        return _requestedMaxQuality;
    }

    void setRequstedMinQuality(VideoChannelDescription::Quality quality) {
        _requestedMinQuality = quality;
    }

    void setRequstedMaxQuality(VideoChannelDescription::Quality quality) {
        _requestedMaxQuality = quality;
    }

    void setStats(absl::optional<GroupInstanceStats::IncomingVideoStats> stats) {
        _stats = stats;
    }

    absl::optional<GroupInstanceStats::IncomingVideoStats> getStats() {
        return _stats;
    }

private:
    std::shared_ptr<Threads> _threads;
    uint32_t _mainVideoSsrc = 0;
    std::string _endpointId;
    std::unique_ptr<VideoSinkImpl> _videoSink;
    std::vector<GroupJoinPayloadVideoSourceGroup> _ssrcGroups;
    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
    // Memory is managed by _channelManager
    cricket::VideoChannel *_videoChannel;
    // Memory is managed externally
    ChannelManager *_channelManager = nullptr;
    webrtc::Call *_call = nullptr;

    VideoChannelDescription::Quality _requestedMinQuality = VideoChannelDescription::Quality::Thumbnail;
    VideoChannelDescription::Quality _requestedMaxQuality = VideoChannelDescription::Quality::Thumbnail;

    absl::optional<GroupInstanceStats::IncomingVideoStats> _stats;
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

std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> videoCaptureToGetVideoSource(std::shared_ptr<VideoCaptureInterface> videoCapture) {
  return [videoCapture]() {
    VideoCaptureInterfaceObject *videoCaptureImpl = GetVideoCaptureAssumingSameThread(videoCapture.get());
    return videoCaptureImpl ? videoCaptureImpl->source() : nullptr;
  };
}

class AudioDeviceDataObserverShared {
public:
    AudioDeviceDataObserverShared() {
    }

    ~AudioDeviceDataObserverShared() {
    }

    void setStreamingContext(std::shared_ptr<StreamingMediaContext> streamingContext) {
        _mutex.Lock();
        _streamingContext = streamingContext;
        _mutex.Unlock();
    }

    void mixAudio(int16_t *audio_samples, const size_t num_samples, const size_t num_channels, const uint32_t samples_per_sec) {
        _mutex.Lock();
        const auto context = _streamingContext;
        _mutex.Unlock();

        if (context) {
            if (_samplesToResample.size() < 480 * num_channels) {
                _samplesToResample.resize(480 * num_channels);
            }
            memset(_samplesToResample.data(), 0, _samplesToResample.size() * sizeof(int16_t));

            context->getAudio(_samplesToResample.data(), 480, num_channels, 48000);

            if (_resamplerFrequency != samples_per_sec || _resamplerNumChannels != num_channels) {
                _resamplerFrequency = samples_per_sec;
                _resamplerNumChannels = num_channels;
                _resampler = std::make_unique<webrtc::Resampler>();
                if (_resampler->Reset(48000, samples_per_sec, num_channels) == -1) {
                    _resampler = nullptr;
                }
            }

            if (_resampler) {
                size_t outLen = 0;
                _resampler->Push(_samplesToResample.data(), _samplesToResample.size(), (int16_t *)audio_samples, num_samples * num_channels, outLen);
            }
        }
    }

private:
    webrtc::Mutex _mutex;
    std::unique_ptr<webrtc::Resampler> _resampler;
    uint32_t _resamplerFrequency = 0;
    size_t _resamplerNumChannels = 0;
    std::vector<int16_t> _samplesToResample;
    std::shared_ptr<StreamingMediaContext> _streamingContext;
};

class AudioDeviceDataObserverImpl : public webrtc::AudioDeviceDataObserver {
public:
    AudioDeviceDataObserverImpl(std::shared_ptr<AudioDeviceDataObserverShared> shared) :
    _shared(shared) {
    }

    virtual ~AudioDeviceDataObserverImpl() {
    }

    virtual void OnCaptureData(const void* audio_samples,
                               const size_t num_samples,
                               const size_t bytes_per_sample,
                               const size_t num_channels,
                               const uint32_t samples_per_sec) override {
    }

    virtual void OnRenderData(const void* audio_samples,
                              const size_t num_samples,
                              const size_t bytes_per_sample,
                              const size_t num_channels,
                              const uint32_t samples_per_sec) override {
        if (bytes_per_sample != num_channels * 2) {
            return;
        }
        if (samples_per_sec % 100 != 0) {
            return;
        }
        if (num_samples != samples_per_sec / 100) {
            return;
        }

        if (_shared) {
            _shared->mixAudio((int16_t *)audio_samples, num_samples, num_channels, samples_per_sec);
        }
    }

private:
    std::shared_ptr<AudioDeviceDataObserverShared> _shared;
};

class CustomNetEqFactory: public webrtc::NetEqFactory {
public:
    virtual ~CustomNetEqFactory() = default;

    std::unique_ptr<webrtc::NetEq> CreateNetEq(
        const webrtc::NetEq::Config& config,
        const rtc::scoped_refptr<webrtc::AudioDecoderFactory>& decoder_factory, webrtc::Clock* clock
    ) const override {
        webrtc::NetEq::Config updatedConfig = config;
        updatedConfig.sample_rate_hz = 48000;
        return webrtc::DefaultNetEqFactory().CreateNetEq(updatedConfig, decoder_factory, clock);
    }
};

std::unique_ptr<webrtc::NetEqFactory> createNetEqFactory() {
    return std::make_unique<CustomNetEqFactory>();
}

class CustomEchoDetector : public webrtc::EchoDetector {
public:
    // (Re-)Initializes the submodule.
    virtual void Initialize(int capture_sample_rate_hz,
                            int num_capture_channels,
                            int render_sample_rate_hz,
                            int num_render_channels) override {
    }

    // Analysis (not changing) of the render signal.
    virtual void AnalyzeRenderAudio(rtc::ArrayView<const float> render_audio) override {
    }

    // Analysis (not changing) of the capture signal.
    virtual void AnalyzeCaptureAudio(
                                     rtc::ArrayView<const float> capture_audio) override {
    }

    // Collect current metrics from the echo detector.
    virtual Metrics GetMetrics() const override {
        return webrtc::EchoDetector::Metrics();
    }
};

} // namespace

class GroupInstanceCustomInternal : public sigslot::has_slots<>, public std::enable_shared_from_this<GroupInstanceCustomInternal> {
public:
    GroupInstanceCustomInternal(GroupInstanceDescriptor &&descriptor, std::shared_ptr<Threads> threads) :
    _threads(std::move(threads)),
    _networkStateUpdated(descriptor.networkStateUpdated),
    _audioLevelsUpdated(descriptor.audioLevelsUpdated),
    _onAudioFrame(descriptor.onAudioFrame),
    _requestMediaChannelDescriptions(descriptor.requestMediaChannelDescriptions),
    _requestCurrentTime(descriptor.requestCurrentTime),
    _requestAudioBroadcastPart(descriptor.requestAudioBroadcastPart),
    _requestVideoBroadcastPart(descriptor.requestVideoBroadcastPart),
    _videoCapture(descriptor.videoCapture),
    _videoCaptureSink(new VideoSinkImpl("VideoCapture")),
    _getVideoSource(descriptor.getVideoSource),
    _disableIncomingChannels(descriptor.disableIncomingChannels),
    _useDummyChannel(descriptor.useDummyChannel),
    _outgoingAudioBitrateKbit(descriptor.outgoingAudioBitrateKbit),
    _disableOutgoingAudioProcessing(descriptor.disableOutgoingAudioProcessing),
#ifdef WEBRTC_IOS
    _disableAudioInput(descriptor.disableAudioInput),
#endif
    _minOutgoingVideoBitrateKbit(descriptor.minOutgoingVideoBitrateKbit),
    _videoContentType(descriptor.videoContentType),
    _videoCodecPreferences(std::move(descriptor.videoCodecPreferences)),
    _eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
    _taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
    _netEqFactory(createNetEqFactory()),
    _createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _initialInputDeviceId(std::move(descriptor.initialInputDeviceId)),
    _initialOutputDeviceId(std::move(descriptor.initialOutputDeviceId)),
    _missingPacketBuffer(50),
    _platformContext(descriptor.platformContext) {
        assert(_threads->getMediaThread()->IsCurrent());

        _threads->getWorkerThread()->BlockingCall([this] {
            _workerThreadSafery = webrtc::PendingTaskSafetyFlag::Create();
        });
        _threads->getNetworkThread()->BlockingCall([this] {
            _networkThreadSafery = webrtc::PendingTaskSafetyFlag::Create();
        });

        if (_videoCapture) {
          assert(!_getVideoSource);
          _getVideoSource = videoCaptureToGetVideoSource(std::move(descriptor.videoCapture));
        }
        generateSsrcs();

        _noiseSuppressionConfiguration = std::make_shared<NoiseSuppressionConfiguration>(descriptor.initialEnableNoiseSuppression);

        _externalAudioRecorder.reset(new ExternalAudioRecorder(&_externalAudioSamples, &_externalAudioSamplesMutex));
    }

    ~GroupInstanceCustomInternal() {
        _incomingAudioChannels.clear();
        _incomingVideoChannels.clear();
        _serverBandwidthProbingVideoSsrc.reset();

        destroyOutgoingAudioChannel();
        destroyOutgoingVideoChannel();

        _threads->getNetworkThread()->BlockingCall([this]() {
            _rtpTransport->SignalSentPacket.disconnect(this);
            _rtpTransport->SignalRtcpPacketReceived.disconnect(this);
        });

        _channelManager = nullptr;

        _threads->getWorkerThread()->BlockingCall([this]() {
            if (_audioDeviceModule) {
                _audioDeviceModule->Stop();
                _audioDeviceModule = nullptr;
            }
            _call.reset();
        });
    }

    void start() {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        webrtc::field_trial::InitFieldTrialsFromString(
            "WebRTC-DataChannel-Dcsctp/Enabled/"
            "WebRTC-Audio-Allocation/min:32kbps,max:32kbps/"
            "WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
            "WebRTC-TaskQueuePacer/Enabled/"
            "WebRTC-VP8ConferenceTemporalLayers/1/"
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/"
            "WebRTC-BweLossExperiment/Enabled/"
        );

        _networkManager.reset(new ThreadLocalObject<GroupNetworkManager>(_threads->getNetworkThread(), [weak, threads = _threads] () mutable {
            return new GroupNetworkManager(
                fieldTrialsBasedConfig,
                [=](const GroupNetworkManager::State &state) {
                    threads->getMediaThread()->PostTask([=] {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->setIsRtcConnected(state.isReadyToSendData);
                    });
                },
                [=](uint32_t ssrc, int payloadType) {
                    threads->getMediaThread()->PostTask([weak, ssrc, payloadType]() mutable {
                        if (const auto strong = weak.lock()) {
                            strong->receiveUnknownSsrcPacket(ssrc, payloadType);
                        }
                    });
                },
                [=](bool isDataChannelOpen) {
                    threads->getMediaThread()->PostTask([weak, isDataChannelOpen]() mutable {
                        if (const auto strong = weak.lock()) {
                            strong->updateIsDataChannelOpen(isDataChannelOpen);
                        }
                    });
                },
                [=](std::string const &message) {
                    threads->getMediaThread()->PostTask([weak, message]() {
                        if (const auto strong = weak.lock()) {
                            strong->receiveDataChannelMessage(message);
                        }
                    });
                },
                [=](uint32_t ssrc, uint8_t audioLevel, bool isSpeech) {
                    threads->getMediaThread()->PostTask([weak, ssrc, audioLevel, isSpeech]() {
                        if (const auto strong = weak.lock()) {
                            strong->updateSsrcAudioLevel(ssrc, audioLevel, isSpeech);
                        }
                    });
                }, threads);
        }));

    #if USE_RNNOISE
        std::unique_ptr<AudioCapturePostProcessor> audioProcessor = nullptr;
    #endif
        if (_videoContentType != VideoContentType::Screencast) {
            int numChannels = 1;
#ifdef WEBRTC_IOS
            if (_disableAudioInput) {
                numChannels = 2;
            }
#endif
            PlatformInterface::SharedInstance()->configurePlatformAudio(numChannels);

    #if USE_RNNOISE
            audioProcessor = std::make_unique<AudioCapturePostProcessor>([weak, threads = _threads](GroupLevelValue const &level) {
                threads->getMediaThread()->PostTask([weak, level](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->_myAudioLevel = level;
                });
            }, _noiseSuppressionConfiguration, nullptr, nullptr);
    #endif
        }

        _audioDeviceDataObserverShared = std::make_shared<AudioDeviceDataObserverShared>();

        _threads->getWorkerThread()->BlockingCall([this]() mutable {
            _audioDeviceModule = createAudioDeviceModule();
            if (!_audioDeviceModule) {
                return;
            }
        });

        cricket::MediaEngineDependencies mediaDeps;
        mediaDeps.task_queue_factory = _taskQueueFactory.get();
        mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus, webrtc::AudioEncoderL16>();
        mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus, webrtc::AudioDecoderL16>();

        mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext, false, _videoContentType == VideoContentType::Screencast);
        mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

#if USE_RNNOISE
        if (_audioLevelsUpdated && audioProcessor) {
            webrtc::AudioProcessingBuilder builder;
            builder.SetCapturePostProcessing(std::move(audioProcessor));

            builder.SetEchoDetector(rtc::make_ref_counted<CustomEchoDetector>());

            mediaDeps.audio_processing = builder.Create();
        }
#endif

        mediaDeps.adm = _audioDeviceModule;

        _availableVideoFormats = filterSupportedVideoFormats(mediaDeps.video_encoder_factory->GetSupportedFormats());

        std::unique_ptr<cricket::MediaEngineInterface> mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));

        _channelManager = ChannelManager::Create(
            std::move(mediaEngine),
            _threads->getWorkerThread(),
            _threads->getNetworkThread()
        );

        setAudioInputDevice(_initialInputDeviceId);
        setAudioOutputDevice(_initialOutputDeviceId);

        _threads->getWorkerThread()->BlockingCall([this]() {
            webrtc::Call::Config callConfig(_eventLog.get(), _threads->getNetworkThread());
            callConfig.neteq_factory = _netEqFactory.get();
            callConfig.task_queue_factory = _taskQueueFactory.get();
            callConfig.trials = &fieldTrialsBasedConfig;
            callConfig.audio_state = _channelManager->media_engine()->voice().GetAudioState();
            _call.reset(webrtc::Call::Create(callConfig));
        });

        _uniqueRandomIdGenerator.reset(new rtc::UniqueRandomIdGenerator());

        _threads->getNetworkThread()->BlockingCall([this]() {
            _rtpTransport = _networkManager->getSyncAssumingSameThread()->getRtpTransport();
            _rtpTransport->SignalSentPacket.connect(this, &GroupInstanceCustomInternal::OnSentPacket_w);
            _rtpTransport->SignalRtcpPacketReceived.connect(this, &GroupInstanceCustomInternal::OnRtcpPacketReceived_n);
        });

        _videoBitrateAllocatorFactory = webrtc::CreateBuiltinVideoBitrateAllocatorFactory();

        if (_audioLevelsUpdated) {
            beginLevelsTimer(100);
        }

        if (_getVideoSource) {
            setVideoSource(_getVideoSource, true);
        }

        if (_useDummyChannel && _videoContentType != VideoContentType::Screencast) {
            addIncomingAudioChannel(ChannelId(1), true);
        }

        if (_videoContentType == VideoContentType::Screencast) {
            setIsMuted(false);
        }

        /*if (_videoContentType != VideoContentType::Screencast) {
            createOutgoingAudioChannel();
        }*/

        beginNetworkStatusTimer(0);
        //beginAudioChannelCleanupTimer(0);

        adjustBitratePreferences(true);

        beginRemoteConstraintsUpdateTimer(5000);
    }

    void destroyOutgoingVideoChannel() {
        if (!_outgoingVideoChannel) {
            return;
        }
        _outgoingVideoChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingVideoChannel->SetRtpTransport(nullptr);
        });
        _threads->getWorkerThread()->BlockingCall([this]() {
            _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, nullptr, nullptr);
            _channelManager->DestroyChannel(_outgoingVideoChannel);
        });
		_outgoingVideoChannel = nullptr;
    }

    void createOutgoingVideoChannel() {
        if (_outgoingVideoChannel
            || _videoContentType == VideoContentType::None) {
            return;
        }
        configureVideoParams();

        if (!_selectedPayloadType) {
            RTC_LOG(LS_ERROR) << "Could not select payload type.";
            return;
        }

        cricket::VideoOptions videoOptions;
        if (_videoContentType == VideoContentType::Screencast) {
            videoOptions.is_screencast = true;
        }
        _outgoingVideoChannel = _channelManager->CreateVideoChannel(_call.get(), cricket::MediaConfig(), "1", false, GroupNetworkManager::getDefaulCryptoOptions(), videoOptions, _videoBitrateAllocatorFactory.get());
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingVideoChannel->SetRtpTransport(_rtpTransport);
        });

        if (!_outgoingVideoChannel) {
            RTC_LOG(LS_ERROR) << "Could not create outgoing video channel.";
            return;
        }

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
        }

        for (auto fidGroup : fidGroups) {
            videoSendStreamParams.ssrc_groups.push_back(fidGroup);

            GroupJoinPayloadVideoSourceGroup payloadFidGroup;
            payloadFidGroup.semantics = "FID";
            payloadFidGroup.ssrcs = fidGroup.ssrcs;
        }

        videoSendStreamParams.cname = "cname";

        auto outgoingVideoDescription = std::make_shared<cricket::VideoContentDescription>();
        for (const auto &extension : _videoExtensionMap) {
            outgoingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(extension.second, extension.first));
        }
        outgoingVideoDescription->set_rtcp_mux(true);
        outgoingVideoDescription->set_rtcp_reduced_size(true);
        outgoingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingVideoDescription->set_codecs({ _selectedPayloadType->videoCodec, _selectedPayloadType->rtxCodec });
        outgoingVideoDescription->set_bandwidth(1300000);
        outgoingVideoDescription->AddStream(videoSendStreamParams);

        auto incomingVideoDescription = std::make_shared<cricket::VideoContentDescription>();
        for (const auto &extension : _videoExtensionMap) {
            incomingVideoDescription->AddRtpHeaderExtension(webrtc::RtpExtension(extension.second, extension.first));
        }
        incomingVideoDescription->set_rtcp_mux(true);
        incomingVideoDescription->set_rtcp_reduced_size(true);
        incomingVideoDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingVideoDescription->set_codecs({ _selectedPayloadType->videoCodec, _selectedPayloadType->rtxCodec });
        incomingVideoDescription->set_bandwidth(1300000);

        _threads->getWorkerThread()->BlockingCall([&]() {
            std::string errorDesc;
            _outgoingVideoChannel->SetRemoteContent(incomingVideoDescription.get(), webrtc::SdpType::kAnswer, errorDesc);
            _outgoingVideoChannel->SetLocalContent(outgoingVideoDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _outgoingVideoChannel->SetPayloadTypeDemuxingEnabled(false);
        });

        adjustVideoSendParams();
        updateVideoSend();
    }

    void adjustVideoSendParams() {
        if (!_outgoingVideoChannel) {
            return;
        }

        if (_videoContentType == VideoContentType::Screencast) {
            _threads->getWorkerThread()->BlockingCall([this]() {
                webrtc::RtpParameters rtpParameters = _outgoingVideoChannel->media_channel()->GetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc);
                if (rtpParameters.encodings.size() == 3) {
                    for (int i = 0; i < (int)rtpParameters.encodings.size(); i++) {
                        if (i == 0) {
                            rtpParameters.encodings[i].min_bitrate_bps = 50000;
                            rtpParameters.encodings[i].max_bitrate_bps = 100000;
                            rtpParameters.encodings[i].scale_resolution_down_by = 4.0;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 180;
                        } else if (i == 1) {
                            rtpParameters.encodings[i].min_bitrate_bps = 150000;
                            rtpParameters.encodings[i].max_bitrate_bps = 200000;
                            rtpParameters.encodings[i].scale_resolution_down_by = 2.0;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 360;
                        } else if (i == 2) {
                            rtpParameters.encodings[i].min_bitrate_bps = 300000;
                            rtpParameters.encodings[i].max_bitrate_bps = 800000 + 100000;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 720;
                        }
                    }
                } else if (rtpParameters.encodings.size() == 2) {
                    for (int i = 0; i < (int)rtpParameters.encodings.size(); i++) {
                        if (i == 0) {
                            rtpParameters.encodings[i].min_bitrate_bps = 50000;
                            rtpParameters.encodings[i].max_bitrate_bps = 100000;
                            rtpParameters.encodings[i].scale_resolution_down_by = 2.0;
                        } else if (i == 1) {
                            rtpParameters.encodings[i].min_bitrate_bps = 200000;
                            rtpParameters.encodings[i].max_bitrate_bps = 900000 + 100000;
                        }
                    }
                } else {
                    rtpParameters.encodings[0].max_bitrate_bps = (800000 + 100000) * 2;
                }

                _outgoingVideoChannel->media_channel()->SetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, rtpParameters);
            });
        } else {
            _threads->getWorkerThread()->BlockingCall([this]() {
                webrtc::RtpParameters rtpParameters = _outgoingVideoChannel->media_channel()->GetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc);
                if (rtpParameters.encodings.size() == 3) {
                    for (int i = 0; i < (int)rtpParameters.encodings.size(); i++) {
                        if (i == 0) {
                            rtpParameters.encodings[i].min_bitrate_bps = 50000;
                            rtpParameters.encodings[i].max_bitrate_bps = 60000;
                            rtpParameters.encodings[i].scale_resolution_down_by = 4.0;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 180;
                        } else if (i == 1) {
                            rtpParameters.encodings[i].min_bitrate_bps = 100000;
                            rtpParameters.encodings[i].max_bitrate_bps = 110000;
                            rtpParameters.encodings[i].scale_resolution_down_by = 2.0;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 360;
                        } else if (i == 2) {
                            rtpParameters.encodings[i].min_bitrate_bps = 300000;
                            rtpParameters.encodings[i].max_bitrate_bps = 800000 + 100000;
                            rtpParameters.encodings[i].active = _outgoingVideoConstraint >= 720;
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
                            rtpParameters.encodings[i].max_bitrate_bps = 900000 + 100000;
                        }
                    }
                } else {
                    rtpParameters.encodings[0].max_bitrate_bps = (800000 + 100000) * 2;
                }

                _outgoingVideoChannel->media_channel()->SetRtpSendParameters(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, rtpParameters);
            });
        }
    }

    void updateVideoSend() {
        if (!_outgoingVideoChannel) {
            return;
        }

        rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> videoSource = _getVideoSource ? _getVideoSource() : nullptr;
        if (_getVideoSource) {
            _outgoingVideoChannel->Enable(true);
        } else {
            _outgoingVideoChannel->Enable(false);
        }
        _threads->getWorkerThread()->BlockingCall([this, videoSource]() {
            if (_getVideoSource) {
                _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, nullptr, videoSource.get());
            } else {
                _outgoingVideoChannel->media_channel()->SetVideoSend(_outgoingVideoSsrcs.simulcastLayers[0].ssrc, nullptr, nullptr);
            }
        });
    }

    void destroyOutgoingAudioChannel() {
        if (!_outgoingAudioChannel) {
            return;
        }

        _outgoingAudioChannel->Enable(false);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingAudioChannel->SetRtpTransport(nullptr);
        });
        _threads->getWorkerThread()->BlockingCall([this]() {
            _outgoingAudioChannel->media_channel()->SetAudioSend(_outgoingAudioSsrc, false, nullptr, &_audioSource);
            _channelManager->DestroyChannel(_outgoingAudioChannel);
        });
        _outgoingAudioChannel = nullptr;
    }

    void createOutgoingAudioChannel() {
        if (_outgoingAudioChannel) {
            return;
        }

        cricket::AudioOptions audioOptions;
        if (_disableOutgoingAudioProcessing || _videoContentType == VideoContentType::Screencast) {
            audioOptions.echo_cancellation = false;
            audioOptions.noise_suppression = false;
            audioOptions.auto_gain_control = false;
            audioOptions.highpass_filter = false;
            //audioOptions.typing_detection = false;
            //audioOptions.residual_echo_detector = false;
        } else {
            audioOptions.echo_cancellation = true;
            audioOptions.noise_suppression = true;
            //audioOptions.residual_echo_detector = true;
        }

        std::vector<std::string> streamIds;
        streamIds.push_back("1");

        _outgoingAudioChannel = _channelManager->CreateVoiceChannel(_call.get(), cricket::MediaConfig(), "0", false, GroupNetworkManager::getDefaulCryptoOptions(), audioOptions);
        _threads->getNetworkThread()->BlockingCall([&]() {
            _outgoingAudioChannel->SetRtpTransport(_rtpTransport);
        });

        const uint8_t opusMinBitrateKbps = _outgoingAudioBitrateKbit;
        const uint8_t opusMaxBitrateKbps = _outgoingAudioBitrateKbit;
        const uint8_t opusStartBitrateKbps = _outgoingAudioBitrateKbit;
        const uint8_t opusPTimeMs = 120;

        cricket::AudioCodec opusCodec(111, "opus", 48000, 0, 2);
        opusCodec.AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc));
        opusCodec.SetParam(cricket::kCodecParamMinBitrate, opusMinBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamStartBitrate, opusStartBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamMaxBitrate, opusMaxBitrateKbps);
        opusCodec.SetParam(cricket::kCodecParamUseInbandFec, 1);
        opusCodec.SetParam(cricket::kCodecParamPTime, opusPTimeMs);

        auto outgoingAudioDescription = std::make_shared<cricket::AudioContentDescription>();
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        outgoingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        outgoingAudioDescription->set_rtcp_mux(true);
        outgoingAudioDescription->set_rtcp_reduced_size(true);
        outgoingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kSendOnly);
        outgoingAudioDescription->set_codecs({ opusCodec });
        outgoingAudioDescription->set_bandwidth(1300000);
        outgoingAudioDescription->AddStream(cricket::StreamParams::CreateLegacy(_outgoingAudioSsrc));

        auto incomingAudioDescription = std::make_shared<cricket::AudioContentDescription>();
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAudioLevelUri, 1));
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kAbsSendTimeUri, 2));
        incomingAudioDescription->AddRtpHeaderExtension(webrtc::RtpExtension(webrtc::RtpExtension::kTransportSequenceNumberUri, 3));
        incomingAudioDescription->set_rtcp_mux(true);
        incomingAudioDescription->set_rtcp_reduced_size(true);
        incomingAudioDescription->set_direction(webrtc::RtpTransceiverDirection::kRecvOnly);
        incomingAudioDescription->set_codecs({ opusCodec });
        incomingAudioDescription->set_bandwidth(1300000);

        _threads->getWorkerThread()->BlockingCall([&]() {
            std::string errorDesc;
            _outgoingAudioChannel->SetLocalContent(outgoingAudioDescription.get(), webrtc::SdpType::kOffer, errorDesc);
            _outgoingAudioChannel->SetRemoteContent(incomingAudioDescription.get(), webrtc::SdpType::kAnswer, errorDesc);
            _outgoingAudioChannel->SetPayloadTypeDemuxingEnabled(false);
        });

        _outgoingAudioChannel->Enable(true);

        onUpdatedIsMuted();

        adjustBitratePreferences(false);
    }

    void stop() {
        _networkManager->perform([](GroupNetworkManager *networkManager) {
            networkManager->stop();
        });
    }

    void updateSsrcAudioLevel(uint32_t ssrc, uint8_t audioLevel, bool isSpeech) {
        float mappedLevelDb = ((float)audioLevel) / (float)(0x7f);

        //mappedLevelDb = fabs(1.0f - mappedLevelDb);
        //float mappedLevel = pow(10.0f, mappedLevelDb * 0.1f);

        //printf("mappedLevelDb: %f, mappedLevel: %f\n", mappedLevelDb, mappedLevel);

        float mappedLevel = (fabs(1.0f - mappedLevelDb)) * 1.0f;

        auto it = _audioLevels.find(ChannelId(ssrc));
        if (it != _audioLevels.end()) {
            it->second.value.level = fmax(it->second.value.level, mappedLevel);
            if (isSpeech) {
                it->second.value.voice = true;
            }
            it->second.timestamp = rtc::TimeMillis();
        } else {
            InternalGroupLevelValue updated;
            updated.value.level = mappedLevel;
            updated.value.voice = isSpeech;
            updated.timestamp = rtc::TimeMillis();
            _audioLevels.insert(std::make_pair(ChannelId(ssrc), std::move(updated)));
        }

        auto audioChannel = _incomingAudioChannels.find(ChannelId(ssrc));
        if (audioChannel != _incomingAudioChannels.end()) {
            audioChannel->second->updateActivity();
        }
    }

    void beginLevelsTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            //int64_t timestamp = rtc::TimeMillis();
            //int64_t maxSampleTimeout = 400;

            GroupLevelsUpdate levelsUpdate;
            levelsUpdate.updates.reserve(strong->_audioLevels.size() + 1);
            for (auto &it : strong->_audioLevels) {
                /*if (it.second.value.level < 0.001f) {
                    continue;
                }
                if (it.second.timestamp <= timestamp - maxSampleTimeout) {
                    continue;
                }*/

                uint32_t effectiveSsrc = it.first.actualSsrc;
                if (std::find_if(levelsUpdate.updates.begin(), levelsUpdate.updates.end(), [&](GroupLevelUpdate const &item) {
                    return item.ssrc == effectiveSsrc;
                }) != levelsUpdate.updates.end()) {
                    continue;
                }
                levelsUpdate.updates.push_back(GroupLevelUpdate{
                    effectiveSsrc,
                    it.second.value,
                    });
                if (it.second.value.level > 0.001f) {
                    auto audioChannel = strong->_incomingAudioChannels.find(it.first);
                    if (audioChannel != strong->_incomingAudioChannels.end()) {
                        audioChannel->second->updateActivity();
                    }
                }

                //it.second.value.level *= 0.5f;
                //it.second.value.voice = false;
            }

            strong->_audioLevels.clear();

            auto myAudioLevel = strong->_myAudioLevel;
            myAudioLevel.isMuted = strong->_isMuted;
            levelsUpdate.updates.push_back(GroupLevelUpdate{ 0, myAudioLevel });

            if (strong->_audioLevelsUpdated) {
                strong->_audioLevelsUpdated(levelsUpdate);
            }

            bool isSpeech = myAudioLevel.voice && !myAudioLevel.isMuted;
            strong->_networkManager->perform([isSpeech = isSpeech](GroupNetworkManager *networkManager) {
                networkManager->setOutgoingVoiceActivity(isSpeech);
            });

            strong->beginLevelsTimer(100);
        }, webrtc::TimeDelta::Millis(timeoutMs));
    }

    void beginAudioChannelCleanupTimer(int delayMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            auto timestamp = rtc::TimeMillis();

            std::vector<ChannelId> removeChannels;
            for (const auto &it : strong->_incomingAudioChannels) {
                if (it.first.networkSsrc == 1) {
                    continue;
                }
                auto activity = it.second->getActivity();
                if (activity < timestamp - 1000) {
                    removeChannels.push_back(it.first);
                }
            }

            for (const auto &channelId : removeChannels) {
                strong->removeIncomingAudioChannel(channelId);
            }

            strong->beginAudioChannelCleanupTimer(500);
        }, webrtc::TimeDelta::Millis(delayMs));
    }

    void beginRemoteConstraintsUpdateTimer(int delayMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->maybeUpdateRemoteVideoConstraints();

            strong->beginRemoteConstraintsUpdateTimer(5000);
        }, webrtc::TimeDelta::Millis(delayMs));
    }

    void beginNetworkStatusTimer(int delayMs) {
        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (strong->_connectionMode == GroupConnectionMode::GroupConnectionModeBroadcast || strong->_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
                strong->updateBroadcastNetworkStatus();
            }

            strong->beginNetworkStatusTimer(500);
        }, webrtc::TimeDelta::Millis(delayMs));
    }

    void updateBroadcastNetworkStatus() {
        bool isBroadcastConnected = true;

        if (isBroadcastConnected != _isBroadcastConnected) {
            _isBroadcastConnected = isBroadcastConnected;
            updateIsConnected();
        }
    }


    void configureVideoParams() {
        if (!_sharedVideoInformation) {
            return;
        }
        if (_selectedPayloadType) {
            // Already configured.
            return;
        }

        _availablePayloadTypes = assignPayloadTypes(_availableVideoFormats);
        if (_availablePayloadTypes.empty()) {
            return;
        }

        for (const auto &payloadType : _availablePayloadTypes) {
            GroupJoinPayloadVideoPayloadType payload;
            payload.id = payloadType.videoCodec.id;
            payload.name = payloadType.videoCodec.name;
            payload.clockrate = payloadType.videoCodec.clockrate;
            payload.channels = 0;

            std::vector<GroupJoinPayloadVideoPayloadType::FeedbackType> feedbackTypes;

            GroupJoinPayloadVideoPayloadType::FeedbackType fbGoogRemb;
            fbGoogRemb.type = "goog-remb";
            feedbackTypes.push_back(fbGoogRemb);

            GroupJoinPayloadVideoPayloadType::FeedbackType fbTransportCc;
            fbTransportCc.type = "transport-cc";
            feedbackTypes.push_back(fbTransportCc);

            GroupJoinPayloadVideoPayloadType::FeedbackType fbCcmFir;
            fbCcmFir.type = "ccm";
            fbCcmFir.subtype = "fir";
            feedbackTypes.push_back(fbCcmFir);

            GroupJoinPayloadVideoPayloadType::FeedbackType fbNack;
            fbNack.type = "nack";
            feedbackTypes.push_back(fbNack);

            GroupJoinPayloadVideoPayloadType::FeedbackType fbNackPli;
            fbNackPli.type = "nack";
            fbNackPli.subtype = "pli";
            feedbackTypes.push_back(fbNackPli);

            payload.feedbackTypes = feedbackTypes;
            payload.parameters = {};

            _videoPayloadTypes.push_back(std::move(payload));

            GroupJoinPayloadVideoPayloadType rtxPayload;
            rtxPayload.id = payloadType.rtxCodec.id;
            rtxPayload.name = payloadType.rtxCodec.name;
            rtxPayload.clockrate = payloadType.rtxCodec.clockrate;
            rtxPayload.parameters.push_back(std::make_pair("apt", intToString(payloadType.videoCodec.id)));
            _videoPayloadTypes.push_back(std::move(rtxPayload));
        }

        std::vector<std::string> codecPriorities;
        for (const auto name : _videoCodecPreferences) {
            std::string codecName;
            switch (name) {
            case VideoCodecName::VP8: {
                codecName = cricket::kVp8CodecName;
                break;
            }
            case VideoCodecName::VP9: {
                codecName = cricket::kVp9CodecName;
                break;
            }
            case VideoCodecName::H264: {
                codecName = cricket::kH264CodecName;
                break;
            }
            default: {
                break;
            }
            }
            if (codecName.size() != 0) {
                codecPriorities.push_back(std::move(codecName));
            }
        }
        std::vector<std::string> defaultCodecPriorities = {
            cricket::kVp8CodecName,
            cricket::kVp9CodecName
        };

        bool enableH264 = false;
        for (const auto &payloadType : _sharedVideoInformation->payloadTypes) {
            if (payloadType.name == cricket::kH264CodecName) {
                enableH264 = true;
                break;
            }
        }
        if (enableH264) {
            defaultCodecPriorities.insert(defaultCodecPriorities.begin(), cricket::kH264CodecName);
        }

        for (const auto &name : defaultCodecPriorities) {
            if (std::find(codecPriorities.begin(), codecPriorities.end(), name) == codecPriorities.end()) {
                codecPriorities.push_back(name);
            }
        }

        for (const auto &codecName : codecPriorities) {
            if (_selectedPayloadType) {
                break;
            }
            for (const auto &payloadType : _availablePayloadTypes) {
                if (payloadType.videoCodec.name == codecName) {
                    _selectedPayloadType = payloadType;
                    break;
                }
            }
        }
        if (!_selectedPayloadType) {
            return;
        }

        _videoExtensionMap.emplace_back(2, webrtc::RtpExtension::kAbsSendTimeUri);
        _videoExtensionMap.emplace_back(3, webrtc::RtpExtension::kTransportSequenceNumberUri);
        _videoExtensionMap.emplace_back(13, webrtc::RtpExtension::kVideoRotationUri);
    }

    void OnSentPacket_w(const rtc::SentPacket& sent_packet) {
        _call->OnSentPacket(sent_packet);
    }

    void OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *buffer, int64_t packet_time_us) {
        rtc::CopyOnWriteBuffer packet = *buffer;
        if (_call) {
            _call->Receiver()->DeliverPacket(webrtc::MediaType::ANY, packet, packet_time_us);
        }
    }

    void adjustBitratePreferences(bool resetStartBitrate) {
        webrtc::BitrateConstraints preferences;
        webrtc::BitrateSettings settings;
        if (_getVideoSource) {
            settings.min_bitrate_bps = _minOutgoingVideoBitrateKbit * 1024;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = std::max(preferences.min_bitrate_bps, 400 * 1000);
            }
            if (_videoContentType == VideoContentType::Screencast) {
                preferences.max_bitrate_bps = std::max(preferences.min_bitrate_bps, (1020 + 32) * 1000);
            } else {
                preferences.max_bitrate_bps = std::max(preferences.min_bitrate_bps, (1020 + 32) * 1000);
            }
        } else {
            preferences.min_bitrate_bps = 32000;
            if (resetStartBitrate) {
                preferences.start_bitrate_bps = 32000;
            }
            preferences.max_bitrate_bps = 32000;
        }

        settings.min_bitrate_bps = preferences.min_bitrate_bps;
        settings.start_bitrate_bps = preferences.start_bitrate_bps;
        settings.max_bitrate_bps = preferences.max_bitrate_bps;

        _call->GetTransportControllerSend()->SetSdpBitrateParameters(preferences);
		_threads->getWorkerThread()->BlockingCall([&]() {
			_call->SetClientBitratePreferences(settings);
		});
    }

    void setIsRtcConnected(bool isConnected) {
        if (_isRtcConnected == isConnected) {
            return;
        }
        _isRtcConnected = isConnected;

        RTC_LOG(LS_INFO) << formatTimestampMillis(rtc::TimeMillis()) << ": " << "setIsRtcConnected: " << _isRtcConnected;

        if (_broadcastEnabledUntilRtcIsConnectedAtTimestamp) {
            _broadcastEnabledUntilRtcIsConnectedAtTimestamp = absl::nullopt;

            if (_streamingContext) {
                _streamingContext.reset();
                _audioDeviceDataObserverShared->setStreamingContext(nullptr);
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
            maybeUpdateRemoteVideoConstraints();
        }
    }

    void receiveUnknownSsrcPacket(uint32_t ssrc, int payloadType) {
        if (ssrc == _outgoingAudioSsrc) {
            return;
        }

        auto ssrcInfo = _channelBySsrc.find(ssrc);
        if (ssrcInfo == _channelBySsrc.end()) {
            // opus
            if (payloadType == 111) {
                maybeRequestUnknownSsrc(ssrc);
            }
        } else {
            switch (ssrcInfo->second.type) {
                case ChannelSsrcInfo::Type::Audio: {
                    const auto it = _incomingAudioChannels.find(ChannelId(ssrc));
                    if (it != _incomingAudioChannels.end()) {
                        it->second->updateActivity();
                    }

                    break;
                }
                case ChannelSsrcInfo::Type::Video: {
                    break;
                }
                default: {
                    break;
                }
            }
        }
    }

    void receiveRtcpPacket(rtc::CopyOnWriteBuffer const &packet, int64_t timestamp) {
        _threads->getWorkerThread()->PostTask([this, packet, timestamp]() {
            _call->Receiver()->DeliverPacket(webrtc::MediaType::ANY, packet, timestamp);
        });
    }

    void receiveDataChannelMessage(std::string const &message) {
        std::string parsingError;
        auto json = json11::Json::parse(message, parsingError);
        if (json.type() != json11::Json::OBJECT) {
            RTC_LOG(LS_WARNING) << "receiveDataChannelMessage: error parsing message: " << parsingError;
            return;
        }

        if (json.is_object()) {
            const auto colibriClass = json.object_items().find("colibriClass");
            if (colibriClass != json.object_items().end() && colibriClass->second.is_string()) {
                const auto messageType = colibriClass->second.string_value();
                if (messageType == "SenderVideoConstraints") {
                    const auto videoConstraints = json.object_items().find("videoConstraints");
                    if (videoConstraints != json.object_items().end() && videoConstraints->second.is_object()) {
                        const auto idealHeight = videoConstraints->second.object_items().find("idealHeight");
                        if (idealHeight != videoConstraints->second.object_items().end() && idealHeight->second.is_number()) {
                            int outgoingVideoConstraint = idealHeight->second.int_value();
                            if (_outgoingVideoConstraint != outgoingVideoConstraint) {
                                if (_outgoingVideoConstraint > outgoingVideoConstraint) {
                                    _pendingOutgoingVideoConstraint = outgoingVideoConstraint;

                                    int requestId = _pendingOutgoingVideoConstraintRequestId;
                                    _pendingOutgoingVideoConstraintRequestId += 1;

                                    const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
                                    _threads->getMediaThread()->PostDelayedTask([weak, requestId]() {
                                        auto strong = weak.lock();
                                        if (!strong) {
                                            return;
                                        }
                                        if (strong->_pendingOutgoingVideoConstraint != -1 && strong->_pendingOutgoingVideoConstraintRequestId == requestId) {
                                            if (strong->_outgoingVideoConstraint != strong->_pendingOutgoingVideoConstraint) {
                                                strong->_outgoingVideoConstraint = strong->_pendingOutgoingVideoConstraint;
                                                strong->adjustVideoSendParams();
                                            }
                                            strong->_pendingOutgoingVideoConstraint = -1;
                                        }
                                    }, webrtc::TimeDelta::Millis(2000));
                                } else {
                                    _pendingOutgoingVideoConstraint = -1;
                                    _pendingOutgoingVideoConstraintRequestId += 1;
                                    _outgoingVideoConstraint = outgoingVideoConstraint;
                                    adjustVideoSendParams();
                                }
                            }
                        }
                    }
                } else if (messageType == "DebugMessage") {
                    const auto message = json.object_items().find("message");
                    if (message != json.object_items().end() && message->second.is_string()) {
                        std::vector<std::string> parts = splitString(message->second.string_value(), '\n');
                        for (const auto &part : parts) {
                            std::string cleanString = part;
                            std::size_t index = cleanString.find("=");
                            if (index == std::string::npos) {
                                continue;
                            }
                            cleanString.erase(cleanString.begin(), cleanString.begin() + index + 1);

                            index = cleanString.find("target=");
                            if (index == std::string::npos) {
                                continue;
                            }

                            std::string endpointId = cleanString.substr(0, index);
                            cleanString.erase(cleanString.begin(), cleanString.begin() + index + 7);

                            index = cleanString.find("p/");
                            if (index == std::string::npos) {
                                continue;
                            }

                            std::string targetQuality = cleanString.substr(0, index);
                            cleanString.erase(cleanString.begin(), cleanString.begin() + index + 2);

                            index = cleanString.find("ideal=");
                            if (index == std::string::npos) {
                                continue;
                            }

                            cleanString.erase(cleanString.begin(), cleanString.begin() + index + 6);

                            index = cleanString.find("p/");
                            if (index == std::string::npos) {
                                continue;
                            }

                            std::string availableQuality = cleanString.substr(0, index);

                            for (const auto &it : _incomingVideoChannels) {
                                if (it.second->endpointId() == endpointId) {
                                    GroupInstanceStats::IncomingVideoStats incomingVideoStats;
                                    incomingVideoStats.receivingQuality = stringToInt(targetQuality);
                                    incomingVideoStats.availableQuality = stringToInt(availableQuality);
                                    it.second->setStats(incomingVideoStats);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void maybeRequestUnknownSsrc(uint32_t ssrc) {
        if (!_requestMediaChannelDescriptions) {
            MediaChannelDescription description;
            description.audioSsrc = ssrc;
            processMediaChannelDescriptionsResponse(-1, {description});
            return;
        }

        for (const auto &it : _requestedMediaChannelDescriptions) {
            if (std::find(it.second.ssrcs.begin(), it.second.ssrcs.end(), ssrc) != it.second.ssrcs.end()) {
                return;
            }
        }

        int requestId = _nextMediaChannelDescriptionsRequestId;
        _nextMediaChannelDescriptionsRequestId += 1;

        std::vector<uint32_t> requestSsrcs = { ssrc };

        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
        auto task = _requestMediaChannelDescriptions(requestSsrcs, [weak, threads = _threads, requestId](std::vector<MediaChannelDescription> &&descriptions) {
            threads->getMediaThread()->PostTask([weak, requestId, descriptions = std::move(descriptions)]() mutable {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                strong->processMediaChannelDescriptionsResponse(requestId, descriptions);
            });
        });
        _requestedMediaChannelDescriptions.insert(std::make_pair(requestId, RequestedMediaChannelDescriptions(task, std::move(requestSsrcs))));
    }

    void processMediaChannelDescriptionsResponse(int requestId, std::vector<MediaChannelDescription> const &descriptions) {
        _requestedMediaChannelDescriptions.erase(requestId);

        if (_disableIncomingChannels) {
            return;
        }

        for (const auto &description : descriptions) {
            switch (description.type) {
                case MediaChannelDescription::Type::Audio: {
                    if (description.audioSsrc != 0) {
                        addIncomingAudioChannel(ChannelId(description.audioSsrc));
                    }
                    break;
                }
                case MediaChannelDescription::Type::Video: {
                    break;
                }
                default: {
                    break;
                }
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
                    _threads->getNetworkThread()->BlockingCall([this, packet]() {
                        _rtpTransport->DemuxPacketInternal(packet, -1);
                    });
                }
            }
        }*/
    }

    void maybeUpdateRemoteVideoConstraints() {
        if (!_isDataChannelOpen) {
            return;
        }

        std::string pinnedEndpoint;

        json11::Json::object json;
        json.insert(std::make_pair("colibriClass", json11::Json("ReceiverVideoConstraints")));

        json11::Json::object defaultConstraints;
        defaultConstraints.insert(std::make_pair("maxHeight", json11::Json(0)));
        json.insert(std::make_pair("defaultConstraints", json11::Json(std::move(defaultConstraints))));

        json11::Json::array onStageEndpoints;
        json11::Json::object constraints;

        for (const auto &incomingVideoChannel : _incomingVideoChannels) {
            json11::Json::object selectedConstraint;

            switch (incomingVideoChannel.second->requestedMinQuality()) {
                case VideoChannelDescription::Quality::Full: {
                    selectedConstraint.insert(std::make_pair("minHeight", json11::Json(720)));
                    break;
                }
                case VideoChannelDescription::Quality::Medium: {
                    selectedConstraint.insert(std::make_pair("minHeight", json11::Json(360)));
                    break;
                }
                case VideoChannelDescription::Quality::Thumbnail: {
                    selectedConstraint.insert(std::make_pair("minHeight", json11::Json(180)));
                    break;
                }
                default: {
                    break;
                }
            }
            switch (incomingVideoChannel.second->requestedMaxQuality()) {
                case VideoChannelDescription::Quality::Full: {
                    onStageEndpoints.push_back(json11::Json(incomingVideoChannel.first.endpointId));
                    selectedConstraint.insert(std::make_pair("maxHeight", json11::Json(720)));
                    break;
                }
                case VideoChannelDescription::Quality::Medium: {
                    selectedConstraint.insert(std::make_pair("maxHeight", json11::Json(360)));
                    break;
                }
                case VideoChannelDescription::Quality::Thumbnail: {
                    selectedConstraint.insert(std::make_pair("maxHeight", json11::Json(180)));
                    break;
                }
                default: {
                    break;
                }
            }

            constraints.insert(std::make_pair(incomingVideoChannel.first.endpointId, json11::Json(std::move(selectedConstraint))));
        }

        json.insert(std::make_pair("onStageEndpoints", json11::Json(std::move(onStageEndpoints))));
        json.insert(std::make_pair("constraints", json11::Json(std::move(constraints))));

        std::string result = json11::Json(std::move(json)).dump();
        _networkManager->perform([result = std::move(result)](GroupNetworkManager *networkManager) {
            networkManager->sendDataChannelMessage(result);
        });
    }

    void setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled, bool isUnifiedBroadcast) {
        if (_connectionMode != connectionMode || connectionMode == GroupConnectionMode::GroupConnectionModeNone) {
            GroupConnectionMode previousMode = _connectionMode;
            _connectionMode = connectionMode;
            _isUnifiedBroadcast = isUnifiedBroadcast;
            onConnectionModeUpdated(previousMode, keepBroadcastIfWasEnabled);
        }
    }

    void onConnectionModeUpdated(GroupConnectionMode previousMode, bool keepBroadcastIfWasEnabled) {
        RTC_CHECK(_connectionMode != previousMode || _connectionMode == GroupConnectionMode::GroupConnectionModeNone);

        if (previousMode == GroupConnectionMode::GroupConnectionModeRtc) {
            _networkManager->perform([](GroupNetworkManager *networkManager) {
                networkManager->stop();
            });
        } else if (previousMode == GroupConnectionMode::GroupConnectionModeBroadcast) {
            if (keepBroadcastIfWasEnabled) {
                _broadcastEnabledUntilRtcIsConnectedAtTimestamp = rtc::TimeMillis();
            } else {
                if (_streamingContext) {
                    _streamingContext.reset();
                    _audioDeviceDataObserverShared->setStreamingContext(nullptr);
                }
            }
        }

        if (_connectionMode == GroupConnectionMode::GroupConnectionModeNone) {
            destroyOutgoingAudioChannel();
            destroyOutgoingVideoChannel();

            // Regenerate and reconfigure.
            generateSsrcs();

            if (!_isMuted) {
                createOutgoingAudioChannel();
            }
            createOutgoingVideoChannel();
        }

        switch (_connectionMode) {
            case GroupConnectionMode::GroupConnectionModeNone: {
                break;
            }
            case GroupConnectionMode::GroupConnectionModeRtc: {
                _networkManager->perform([](GroupNetworkManager *networkManager) {
                    networkManager->start();
                });
                break;
            }
            case GroupConnectionMode::GroupConnectionModeBroadcast: {
                _isBroadcastConnected = false;

                if (!_streamingContext) {
                    StreamingMediaContext::StreamingMediaContextArguments arguments;
                    const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());
                    arguments.threads = _threads;
                    arguments.platformContext = _platformContext;
                    arguments.isUnifiedBroadcast = _isUnifiedBroadcast;
                    arguments.requestCurrentTime = _requestCurrentTime;
                    arguments.requestAudioBroadcastPart = _requestAudioBroadcastPart;
                    arguments.requestVideoBroadcastPart = _requestVideoBroadcastPart;
                    arguments.updateAudioLevel = [weak, threads = _threads](uint32_t ssrc, float level, bool isSpeech) {
                        assert(threads->getMediaThread()->IsCurrent());

                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        InternalGroupLevelValue updated;
                        updated.value.level = level;
                        updated.value.voice = isSpeech;
                        updated.timestamp = rtc::TimeMillis();
                        strong->_audioLevels.insert(std::make_pair(ChannelId(ssrc), std::move(updated)));
                    };
                    _streamingContext = std::make_shared<StreamingMediaContext>(std::move(arguments));

                    for (const auto &it : _pendingVideoSinks) {
                        for (const auto &sink : it.second) {
                            _streamingContext->addVideoSink(it.first.endpointId, sink);
                        }
                    }

                    for (const auto &it : _volumeBySsrc) {
                        _streamingContext->setVolume(it.first, it.second);
                    }

                    std::vector<StreamingMediaContext::VideoChannel> streamingVideoChannels;
                    for (const auto &it : _pendingRequestedVideo) {
                        streamingVideoChannels.emplace_back(it.maxQuality, it.endpointId);
                    }
                    _streamingContext->setActiveVideoChannels(streamingVideoChannels);

                    _audioDeviceDataObserverShared->setStreamingContext(_streamingContext);
                }

                break;
            }
            default: {
                RTC_FATAL() << "Unknown connectionMode";
                break;
            }
        }

        updateIsConnected();
    }

    void generateSsrcs() {
        auto generator = std::mt19937(std::random_device()());
        auto distribution = std::uniform_int_distribution<uint32_t>();
        do {
            _outgoingAudioSsrc = distribution(generator) & 0x7fffffffU;
        } while (!_outgoingAudioSsrc);

        uint32_t outgoingVideoSsrcBase = _outgoingAudioSsrc + 1;
        int numVideoSimulcastLayers = 3;
        if (_videoContentType == VideoContentType::Screencast) {
            numVideoSimulcastLayers = 2;
        }
        _outgoingVideoSsrcs.simulcastLayers.clear();
        for (int layerIndex = 0; layerIndex < numVideoSimulcastLayers; layerIndex++) {
            _outgoingVideoSsrcs.simulcastLayers.push_back(VideoSsrcs::SimulcastLayer(outgoingVideoSsrcBase + layerIndex * 2 + 0, outgoingVideoSsrcBase + layerIndex * 2 + 1));
        }

        _videoSourceGroups.clear();

        std::vector<uint32_t> simulcastGroupSsrcs;
        std::vector<cricket::SsrcGroup> fidGroups;
        for (const auto &layer : _outgoingVideoSsrcs.simulcastLayers) {
            simulcastGroupSsrcs.push_back(layer.ssrc);

            cricket::SsrcGroup fidGroup(cricket::kFidSsrcGroupSemantics, { layer.ssrc, layer.fidSsrc });
            fidGroups.push_back(fidGroup);
        }
        if (simulcastGroupSsrcs.size() > 1) {
            cricket::SsrcGroup simulcastGroup(cricket::kSimSsrcGroupSemantics, simulcastGroupSsrcs);

            GroupJoinPayloadVideoSourceGroup payloadSimulcastGroup;
            payloadSimulcastGroup.semantics = "SIM";
            payloadSimulcastGroup.ssrcs = simulcastGroupSsrcs;
            _videoSourceGroups.push_back(payloadSimulcastGroup);
        }

        for (auto fidGroup : fidGroups) {
            GroupJoinPayloadVideoSourceGroup payloadFidGroup;
            payloadFidGroup.semantics = "FID";
            payloadFidGroup.ssrcs = fidGroup.ssrcs;
            _videoSourceGroups.push_back(payloadFidGroup);
        }
    }

    void emitJoinPayload(std::function<void(GroupJoinPayload const &)> completion) {
        _networkManager->perform([outgoingAudioSsrc = _outgoingAudioSsrc, /*videoPayloadTypes = _videoPayloadTypes, videoExtensionMap = _videoExtensionMap, */videoSourceGroups = _videoSourceGroups, videoContentType = _videoContentType, completion](GroupNetworkManager *networkManager) {
            GroupJoinInternalPayload payload;

            payload.audioSsrc = outgoingAudioSsrc;

            if (videoContentType != VideoContentType::None) {
                GroupParticipantVideoInformation videoInformation;
                videoInformation.ssrcGroups = videoSourceGroups;
                payload.videoInformation = std::move(videoInformation);
            }

            GroupJoinTransportDescription transportDescription;

            auto localIceParameters = networkManager->getLocalIceParameters();
            transportDescription.ufrag = localIceParameters.ufrag;
            transportDescription.pwd = localIceParameters.pwd;

            auto localFingerprint = networkManager->getLocalFingerprint();
            if (localFingerprint) {
                GroupJoinTransportDescription::Fingerprint serializedFingerprint;
                serializedFingerprint.hash = localFingerprint->algorithm;
                serializedFingerprint.fingerprint = localFingerprint->GetRfc4572Fingerprint();
                serializedFingerprint.setup = "passive";
                transportDescription.fingerprints.push_back(std::move(serializedFingerprint));
            }

            payload.transport = std::move(transportDescription);

            GroupJoinPayload result;
            result.audioSsrc = payload.audioSsrc;
            result.json = payload.serialize();
            completion(result);
        });
    }

    void setVideoSource(std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> getVideoSource, bool isInitializing) {
        bool resetBitrate = (!_getVideoSource) != (!getVideoSource) && !isInitializing;
        if (!isInitializing && _getVideoSource && getVideoSource && getVideoSource() == _getVideoSource()) {
            return;
        }

        _getVideoSource = std::move(getVideoSource);
		updateVideoSend();
        if (resetBitrate) {
            adjustBitratePreferences(true);
        }
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, bool isInitializing) {
        _videoCapture = videoCapture;
        setVideoSource(videoCaptureToGetVideoSource(std::move(videoCapture)), isInitializing);
    }

    void setAudioOutputDevice(const std::string &id) {
#if not defined(WEBRTC_IOS) && not defined(WEBRTC_ANDROID)
        _threads->getWorkerThread()->BlockingCall([&] {
                    SetAudioOutputDeviceById(_audioDeviceModule.get(), id);
        });
#endif // WEBRTC_IOS
    }

    void setAudioInputDevice(const std::string &id) {
#if not defined(WEBRTC_IOS) && not defined(WEBRTC_ANDROID)
        _threads->getWorkerThread()->BlockingCall([&] {
                    SetAudioInputDeviceById(_audioDeviceModule.get(), id);
        });
#endif // WEBRTC_IOS
    }

    void addExternalAudioSamples(std::vector<uint8_t> &&samples) {
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

    void setJoinResponsePayload(std::string const &payload) {
        RTC_LOG(LS_INFO) << formatTimestampMillis(rtc::TimeMillis()) << ": " << "setJoinResponsePayload";

        auto parsedPayload = GroupJoinResponsePayload::parse(payload);
        if (!parsedPayload) {
            RTC_LOG(LS_ERROR) << "Could not parse json response payload";
            return;
        }

        _sharedVideoInformation = parsedPayload->videoInformation;

        _serverBandwidthProbingVideoSsrc.reset();

        if (parsedPayload->videoInformation && parsedPayload->videoInformation->serverVideoBandwidthProbingSsrc) {
            setServerBandwidthProbingChannelSsrc(parsedPayload->videoInformation->serverVideoBandwidthProbingSsrc);
        }

        _networkManager->perform([parsedTransport = parsedPayload->transport](GroupNetworkManager *networkManager) {
            PeerIceParameters remoteIceParameters;
            remoteIceParameters.ufrag = parsedTransport.ufrag;
            remoteIceParameters.pwd = parsedTransport.pwd;

            std::vector<cricket::Candidate> iceCandidates;
            for (auto const &candidate : parsedTransport.candidates) {
                rtc::SocketAddress address(candidate.ip, stringToInt(candidate.port));

                cricket::Candidate parsedCandidate(
                    /*component=*/stringToInt(candidate.component),
                    /*protocol=*/candidate.protocol,
                    /*address=*/address,
                    /*priority=*/stringToUInt32(candidate.priority),
                    /*username=*/parsedTransport.ufrag,
                    /*password=*/parsedTransport.pwd,
                    /*type=*/candidate.type,
                    /*generation=*/stringToUInt32(candidate.generation),
                    /*foundation=*/candidate.foundation,
                    /*network_id=*/stringToUInt16(candidate.network),
                    /*network_cost=*/0
                );
                iceCandidates.push_back(parsedCandidate);
            }

            std::unique_ptr<rtc::SSLFingerprint> fingerprint;
            if (parsedTransport.fingerprints.size() != 0) {
                fingerprint = rtc::SSLFingerprint::CreateUniqueFromRfc4572(parsedTransport.fingerprints[0].hash, parsedTransport.fingerprints[0].fingerprint);
            }

            networkManager->setRemoteParams(remoteIceParameters, iceCandidates, fingerprint.get());
        });

        configureVideoParams();
        createOutgoingVideoChannel();

        adjustBitratePreferences(true);

        if (!_pendingRequestedVideo.empty()) {
            setRequestedVideoChannels(std::move(_pendingRequestedVideo));
            _pendingRequestedVideo.clear();
        }
    }

    void setServerBandwidthProbingChannelSsrc(uint32_t probingSsrc) {
        RTC_CHECK(probingSsrc);

        if (!_sharedVideoInformation || _availablePayloadTypes.empty()) {
            return;
        }

        GroupParticipantVideoInformation videoInformation;

        GroupJoinPayloadVideoSourceGroup sourceGroup;
        sourceGroup.ssrcs.push_back(probingSsrc);
        sourceGroup.semantics = "SIM";

        videoInformation.ssrcGroups.push_back(std::move(sourceGroup));

        _serverBandwidthProbingVideoSsrc.reset(new IncomingVideoChannel(
            _channelManager.get(),
            _call.get(),
            _rtpTransport,
            _uniqueRandomIdGenerator.get(),
            _availableVideoFormats,
            _sharedVideoInformation.value(),
            123456,
            VideoChannelDescription::Quality::Thumbnail,
            VideoChannelDescription::Quality::Thumbnail,
            videoInformation,
            _threads
        ));

        ChannelSsrcInfo mapping;
        mapping.type = ChannelSsrcInfo::Type::Video;
        mapping.allSsrcs.push_back(probingSsrc);
        _channelBySsrc.insert(std::make_pair(probingSsrc, std::move(mapping)));
    }

    void removeSsrcs(std::vector<uint32_t> ssrcs) {
    }

    void removeIncomingVideoSource(uint32_t ssrc) {
    }

    void setIsMuted(bool isMuted) {
        if (_isMuted == isMuted) {
            return;
        }
        _isMuted = isMuted;

        if (!_isMuted && !_outgoingAudioChannel) {
            createOutgoingAudioChannel();
        }

        onUpdatedIsMuted();
    }

    void onUpdatedIsMuted() {
        if (_outgoingAudioChannel) {
            _threads->getWorkerThread()->BlockingCall([this]() {
                _outgoingAudioChannel->media_channel()->SetAudioSend(_outgoingAudioSsrc, !_isMuted, nullptr, &_audioSource);
            });

            _outgoingAudioChannel->Enable(!_isMuted);
        }
    }

    void setIsNoiseSuppressionEnabled(bool isNoiseSuppressionEnabled) {
        _noiseSuppressionConfiguration->isEnabled = isNoiseSuppressionEnabled;
    }

    void addOutgoingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        _videoCaptureSink->addSink(sink);

        if (_videoCapture) {
            _videoCapture->setOutput(_videoCaptureSink);
        }
    }

    void addIncomingVideoOutput(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        if (_sharedVideoInformation && endpointId == _sharedVideoInformation->endpointId) {
            if (_videoCapture) {
                _videoCaptureSink->addSink(sink);
                _videoCapture->setOutput(_videoCaptureSink);
            }
        } else {
            auto it = _incomingVideoChannels.find(VideoChannelId(endpointId));
            if (it != _incomingVideoChannels.end()) {
                it->second->addSink(sink);
            } else {
                _pendingVideoSinks[VideoChannelId(endpointId)].push_back(sink);
            }

            if (_streamingContext) {
                _streamingContext->addVideoSink(endpointId, sink);
            }
        }
    }

    void addIncomingAudioChannel(ChannelId ssrc, bool isRawPcm = false) {
        if (_incomingAudioChannels.find(ssrc) != _incomingAudioChannels.end()) {
            return;
        }

        if (_incomingAudioChannels.size() > 10) {
            auto timestamp = rtc::TimeMillis();

            int64_t minActivity = INT64_MAX;
            ChannelId minActivityChannelId(0, 0);

            for (const auto &it : _incomingAudioChannels) {
                if (it.first.networkSsrc == 1) {
                    continue;
                }
                auto activity = it.second->getActivity();
                if (activity < minActivity && activity < timestamp - 1000) {
                    minActivity = activity;
                    minActivityChannelId = it.first;
                }
            }

            if (minActivityChannelId.networkSsrc != 0) {
                removeIncomingAudioChannel(minActivityChannelId);
            }

            if (_incomingAudioChannels.size() > 10) {
                // Wait until there is a channel that hasn't been active in 1 second
                return;
            }
        }

        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        std::function<void(AudioSinkImpl::Update)> onAudioSinkUpdate;
        if (ssrc.actualSsrc != ssrc.networkSsrc) {
            if (_audioLevelsUpdated) {
                onAudioSinkUpdate = [weak, ssrc = ssrc, threads = _threads](AudioSinkImpl::Update update) {
                    threads->getMediaThread()->PostTask([weak, ssrc, update]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        auto it = strong->_audioLevels.find(ChannelId(ssrc));
                        if (it != strong->_audioLevels.end()) {
                            it->second.value.level = fmax(it->second.value.level, update.level);
                            if (update.hasSpeech) {
                                it->second.value.voice = true;
                            }
                            it->second.timestamp = rtc::TimeMillis();
                        } else {
                            InternalGroupLevelValue updated;
                            updated.value.level = update.level;
                            updated.value.voice = update.hasSpeech;
                            updated.timestamp = rtc::TimeMillis();
                            strong->_audioLevels.insert(std::make_pair(ChannelId(ssrc), std::move(updated)));
                        }
                    });
                };
            }
        }

        std::unique_ptr<IncomingAudioChannel> channel(new IncomingAudioChannel(
          _channelManager.get(),
            _call.get(),
            _rtpTransport,
            _uniqueRandomIdGenerator.get(),
            isRawPcm,
            ssrc,
            std::move(onAudioSinkUpdate),
            _onAudioFrame,
            _threads
        ));

        auto volume = _volumeBySsrc.find(ssrc.actualSsrc);
        if (volume != _volumeBySsrc.end()) {
            channel->setVolume(volume->second);
        }

        _incomingAudioChannels.insert(std::make_pair(ssrc, std::move(channel)));

        auto currentMapping = _channelBySsrc.find(ssrc.networkSsrc);
        if (currentMapping != _channelBySsrc.end()) {
            if (currentMapping->second.type == ChannelSsrcInfo::Type::Audio) {
                if (std::find(currentMapping->second.allSsrcs.begin(), currentMapping->second.allSsrcs.end(), ssrc.networkSsrc) == currentMapping->second.allSsrcs.end()) {
                    currentMapping->second.allSsrcs.push_back(ssrc.networkSsrc);
                }
            }
        } else {
            ChannelSsrcInfo mapping;
            mapping.type = ChannelSsrcInfo::Type::Audio;
            mapping.allSsrcs.push_back(ssrc.networkSsrc);
            _channelBySsrc.insert(std::make_pair(ssrc.networkSsrc, std::move(mapping)));
        }

        maybeDeliverBufferedPackets(ssrc.networkSsrc);

        adjustBitratePreferences(false);
    }

    void removeIncomingAudioChannel(ChannelId const &channelId) {
        const auto it = _incomingAudioChannels.find(channelId);
        if (it != _incomingAudioChannels.end()) {
            _incomingAudioChannels.erase(it);
        }

        auto currentMapping = _channelBySsrc.find(channelId.networkSsrc);
        if (currentMapping != _channelBySsrc.end()) {
            if (currentMapping->second.type == ChannelSsrcInfo::Type::Audio) {
                auto ssrcs = currentMapping->second.allSsrcs;
                for (auto ssrc : ssrcs) {
                    auto it = _channelBySsrc.find(ssrc);
                    if (it != _channelBySsrc.end()) {
                        _channelBySsrc.erase(it);
                    }
                }
            }
        }
    }

    void addIncomingVideoChannel(uint32_t audioSsrc, GroupParticipantVideoInformation const &videoInformation, VideoChannelDescription::Quality minQuality, VideoChannelDescription::Quality maxQuality) {
        if (!_sharedVideoInformation) {
            return;
        }
        if (_incomingVideoChannels.find(VideoChannelId(videoInformation.endpointId)) != _incomingVideoChannels.end()) {
            return;
        }

        const auto weak = std::weak_ptr<GroupInstanceCustomInternal>(shared_from_this());

        std::unique_ptr<IncomingVideoChannel> channel(new IncomingVideoChannel(
            _channelManager.get(),
            _call.get(),
            _rtpTransport,
            _uniqueRandomIdGenerator.get(),
            _availableVideoFormats,
            _sharedVideoInformation.value(),
            audioSsrc,
            minQuality,
            maxQuality,
            videoInformation,
            _threads
        ));

        const auto pendingSinks = _pendingVideoSinks.find(VideoChannelId(videoInformation.endpointId));
        if (pendingSinks != _pendingVideoSinks.end()) {
            for (const auto &sink : pendingSinks->second) {
                channel->addSink(sink);
            }

            _pendingVideoSinks.erase(pendingSinks);
        }

        _incomingVideoChannels.insert(std::make_pair(VideoChannelId(videoInformation.endpointId), std::move(channel)));

        std::vector<uint32_t> allSsrcs;
        for (const auto &group : videoInformation.ssrcGroups) {
            for (auto ssrc : group.ssrcs) {
                if (std::find(allSsrcs.begin(), allSsrcs.end(), ssrc) == allSsrcs.end()) {
                    allSsrcs.push_back(ssrc);
                }
            }
        }

        for (auto ssrc : allSsrcs) {
            ChannelSsrcInfo mapping;
            mapping.type = ChannelSsrcInfo::Type::Video;
            mapping.allSsrcs = allSsrcs;
            mapping.videoEndpointId = videoInformation.endpointId;
            _channelBySsrc.insert(std::make_pair(ssrc, std::move(mapping)));
        }

        for (auto ssrc : allSsrcs) {
            maybeDeliverBufferedPackets(ssrc);
        }

        adjustBitratePreferences(false);
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

        if (_streamingContext) {
            _streamingContext->setVolume(ssrc, volume);
        }
    }

    void setRequestedVideoChannels(std::vector<VideoChannelDescription> &&requestedVideoChannels) {
        if (_streamingContext) {
            std::vector<StreamingMediaContext::VideoChannel> streamingVideoChannels;
            for (const auto &it : requestedVideoChannels) {
                streamingVideoChannels.emplace_back(it.maxQuality, it.endpointId);
            }
            _streamingContext->setActiveVideoChannels(streamingVideoChannels);
        }

        if (!_sharedVideoInformation) {
            _pendingRequestedVideo = std::move(requestedVideoChannels);
            return;
        }
        bool updated = false;
        std::vector<std::string> allEndpointIds;

        for (const auto &description : requestedVideoChannels) {
            if (_sharedVideoInformation && _sharedVideoInformation->endpointId == description.endpointId) {
                continue;
            }

            GroupParticipantVideoInformation videoInformation;
            videoInformation.endpointId = description.endpointId;
            for (const auto &group : description.ssrcGroups) {
                GroupJoinPayloadVideoSourceGroup parsedGroup;
                parsedGroup.semantics = group.semantics;
                parsedGroup.ssrcs = group.ssrcs;
                videoInformation.ssrcGroups.push_back(std::move(parsedGroup));
            }

            allEndpointIds.push_back(videoInformation.endpointId);

            auto current = _incomingVideoChannels.find(VideoChannelId(videoInformation.endpointId));
            if (current != _incomingVideoChannels.end()) {
                if (current->second->requestedMinQuality() != description.minQuality || current->second->requestedMaxQuality() != description.maxQuality) {
                    current->second->setRequstedMinQuality(description.minQuality);
                    current->second->setRequstedMaxQuality(description.maxQuality);
                    updated = true;
                }
                continue;
            }

            addIncomingVideoChannel(description.audioSsrc, videoInformation, description.minQuality, description.maxQuality);
            updated = true;
        }

        std::vector<std::string> removeEndpointIds;
        for (const auto &it : _incomingVideoChannels) {
            if (std::find(allEndpointIds.begin(), allEndpointIds.end(), it.first.endpointId) == allEndpointIds.end()) {
                removeEndpointIds.push_back(it.first.endpointId);
                updated = true;
            }
        }

        for (const auto &endpointId : removeEndpointIds) {
            const auto it = _incomingVideoChannels.find(VideoChannelId(endpointId));
            if (it != _incomingVideoChannels.end()) {
                auto sinks = it->second->getSinks();
                for (const auto &sink : sinks) {
                    _pendingVideoSinks[VideoChannelId(endpointId)].push_back(sink);
                }
                _incomingVideoChannels.erase(it);
            }
        }

        if (updated) {
            maybeUpdateRemoteVideoConstraints();
        }
    }

    void getStats(std::function<void(GroupInstanceStats)> completion) {
        GroupInstanceStats result;

        for (const auto &it : _incomingVideoChannels) {
            const auto videoStats = it.second->getStats();
            if (videoStats) {
                result.incomingVideoStats.push_back(std::make_pair(it.second->endpointId(), videoStats.value()));
            }
        }

        completion(result);
    }

private:
    rtc::scoped_refptr<WrappedAudioDeviceModule> createAudioDeviceModule() {
        auto audioDeviceDataObserverShared = _audioDeviceDataObserverShared;
#ifdef WEBRTC_IOS
        bool disableRecording = _disableAudioInput;
#endif
        const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
#ifdef WEBRTC_IOS
            return rtc::make_ref_counted<webrtc::tgcalls_ios_adm::AudioDeviceModuleIOS>(false, disableRecording, disableRecording ? 2 : 1);
#else
            return webrtc::AudioDeviceModule::Create(
                layer,
                _taskQueueFactory.get());
#endif
        };
        const auto check = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) -> rtc::scoped_refptr<WrappedAudioDeviceModule> {
            if (!result) {
                return nullptr;
            }

            auto audioDeviceObserver = std::make_unique<AudioDeviceDataObserverImpl>(audioDeviceDataObserverShared);
            auto module = webrtc::CreateAudioDeviceWithDataObserver(result, std::move(audioDeviceObserver));

            if (module->Init() == 0) {
                return PlatformInterface::SharedInstance()->wrapAudioDeviceModule(module);
            } else {
                return nullptr;
            }
        };
        if (_createAudioDeviceModule) {
            if (const auto result = check(_createAudioDeviceModule(_taskQueueFactory.get()))) {
                return result;
            }
        } else if (_videoContentType == VideoContentType::Screencast) {
#ifdef WEBRTC_ANDROID
            return check(create(webrtc::AudioDeviceModule::kAndroidScreenAudio));
#else
            FakeAudioDeviceModule::Options options;
            options.num_channels = 1;
            return check(FakeAudioDeviceModule::Creator(nullptr, _externalAudioRecorder, options)(_taskQueueFactory.get()));
#endif
        }
        return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
    }

private:
    std::shared_ptr<Threads> _threads;
    GroupConnectionMode _connectionMode = GroupConnectionMode::GroupConnectionModeNone;
    bool _isUnifiedBroadcast = false;

    std::function<void(GroupNetworkState)> _networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> _audioLevelsUpdated;
    std::function<void(uint32_t, const AudioFrame &)> _onAudioFrame;
    std::function<std::shared_ptr<RequestMediaChannelDescriptionTask>(std::vector<uint32_t> const &, std::function<void(std::vector<MediaChannelDescription> &&)>)> _requestMediaChannelDescriptions;
    std::function<std::shared_ptr<BroadcastPartTask>(std::function<void(int64_t)>)> _requestCurrentTime;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> _requestAudioBroadcastPart;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, int32_t, VideoChannelDescription::Quality, std::function<void(BroadcastPart &&)>)> _requestVideoBroadcastPart;
    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    std::shared_ptr<VideoSinkImpl> _videoCaptureSink;
    std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> _getVideoSource;
    bool _disableIncomingChannels = false;
    bool _useDummyChannel{true};
    int _outgoingAudioBitrateKbit{32};
    bool _disableOutgoingAudioProcessing{false};
#ifdef WEBRTC_IOS
    bool _disableAudioInput{false};
#endif
    int _minOutgoingVideoBitrateKbit{100};
    VideoContentType _videoContentType{VideoContentType::None};
    std::vector<VideoCodecName> _videoCodecPreferences;

    int _nextMediaChannelDescriptionsRequestId = 0;
    std::map<int, RequestedMediaChannelDescriptions> _requestedMediaChannelDescriptions;

    std::unique_ptr<ThreadLocalObject<GroupNetworkManager>> _networkManager;

    std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;
    std::unique_ptr<webrtc::NetEqFactory> _netEqFactory;
    std::unique_ptr<webrtc::Call> _call;
    webrtc::LocalAudioSinkAdapter _audioSource;
    std::shared_ptr<AudioDeviceDataObserverShared> _audioDeviceDataObserverShared;
    rtc::scoped_refptr<WrappedAudioDeviceModule> _audioDeviceModule;
    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;
    std::string _initialInputDeviceId;
    std::string _initialOutputDeviceId;

    // _outgoingAudioChannel memory is managed by _channelManager
    cricket::VoiceChannel *_outgoingAudioChannel = nullptr;
    uint32_t _outgoingAudioSsrc = 0;

    std::vector<webrtc::SdpVideoFormat> _availableVideoFormats;
    std::vector<OutgoingVideoFormat> _availablePayloadTypes;
    absl::optional<OutgoingVideoFormat> _selectedPayloadType;

    std::vector<GroupJoinPayloadVideoPayloadType> _videoPayloadTypes;
    std::vector<std::pair<uint32_t, std::string>> _videoExtensionMap;
    std::vector<GroupJoinPayloadVideoSourceGroup> _videoSourceGroups;

    std::unique_ptr<rtc::UniqueRandomIdGenerator> _uniqueRandomIdGenerator;
    webrtc::RtpTransport *_rtpTransport = nullptr;
    std::unique_ptr<ChannelManager> _channelManager;

    std::unique_ptr<webrtc::VideoBitrateAllocatorFactory> _videoBitrateAllocatorFactory;
    // _outgoingVideoChannel memory is managed by _channelManager
    cricket::VideoChannel *_outgoingVideoChannel = nullptr;
    VideoSsrcs _outgoingVideoSsrcs;
    int _outgoingVideoConstraint = 720;
    int _pendingOutgoingVideoConstraint = -1;
    int _pendingOutgoingVideoConstraintRequestId = 0;

    std::map<ChannelId, InternalGroupLevelValue> _audioLevels;
    GroupLevelValue _myAudioLevel;

    bool _isMuted = true;
    std::shared_ptr<NoiseSuppressionConfiguration> _noiseSuppressionConfiguration;

    MissingSsrcPacketBuffer _missingPacketBuffer;
    std::map<uint32_t, ChannelSsrcInfo> _channelBySsrc;
    std::map<uint32_t, double> _volumeBySsrc;
    std::map<ChannelId, std::unique_ptr<IncomingAudioChannel>> _incomingAudioChannels;
    std::map<VideoChannelId, std::unique_ptr<IncomingVideoChannel>> _incomingVideoChannels;

    std::map<VideoChannelId, std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>>> _pendingVideoSinks;
    std::vector<VideoChannelDescription> _pendingRequestedVideo;

    std::unique_ptr<IncomingVideoChannel> _serverBandwidthProbingVideoSsrc;

    absl::optional<GroupJoinVideoInformation> _sharedVideoInformation;

    std::vector<float> _externalAudioSamples;
    webrtc::Mutex _externalAudioSamplesMutex;
    std::shared_ptr<ExternalAudioRecorder> _externalAudioRecorder;

    bool _isRtcConnected = false;
    bool _isBroadcastConnected = false;
    absl::optional<int64_t> _broadcastEnabledUntilRtcIsConnectedAtTimestamp;
    bool _isDataChannelOpen = false;
    GroupNetworkState _effectiveNetworkState;

    std::shared_ptr<StreamingMediaContext> _streamingContext;

    rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> _workerThreadSafery;
    rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> _networkThreadSafery;

    std::shared_ptr<PlatformContext> _platformContext;
};

GroupInstanceCustomImpl::GroupInstanceCustomImpl(GroupInstanceDescriptor &&descriptor) {
    if (descriptor.config.need_log) {
      _logSink = std::make_unique<LogSinkImpl>(descriptor.config.logPath);
      rtc::LogMessage::SetLogToStderr(true);
    } else {
        rtc::LogMessage::SetLogToStderr(false);
    }
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    if (_logSink) {
        rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    }

    _threads = descriptor.threads;
    _internal.reset(new ThreadLocalObject<GroupInstanceCustomInternal>(_threads->getMediaThread(), [descriptor = std::move(descriptor), threads = _threads]() mutable {
        return new GroupInstanceCustomInternal(std::move(descriptor), threads);
    }));
    _internal->perform([](GroupInstanceCustomInternal *internal) {
        internal->start();
    });
}

GroupInstanceCustomImpl::~GroupInstanceCustomImpl() {
    if (_logSink) {
        rtc::LogMessage::RemoveLogToStream(_logSink.get());
    }
    _internal.reset();

    // Wait until _internal is destroyed
    _threads->getMediaThread()->BlockingCall([] {});
}

void GroupInstanceCustomImpl::stop() {
    _internal->perform([](GroupInstanceCustomInternal *internal) {
        internal->stop();
    });
}

void GroupInstanceCustomImpl::setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled, bool isUnifiedBroadcast) {
    _internal->perform([connectionMode, keepBroadcastIfWasEnabled, isUnifiedBroadcast](GroupInstanceCustomInternal *internal) {
        internal->setConnectionMode(connectionMode, keepBroadcastIfWasEnabled, isUnifiedBroadcast);
    });
}

void GroupInstanceCustomImpl::emitJoinPayload(std::function<void(GroupJoinPayload const &)> completion) {
    _internal->perform([completion](GroupInstanceCustomInternal *internal) {
        internal->emitJoinPayload(completion);
    });
}

void GroupInstanceCustomImpl::setJoinResponsePayload(std::string const &payload) {
    _internal->perform([payload](GroupInstanceCustomInternal *internal) {
        internal->setJoinResponsePayload(payload);
    });
}

void GroupInstanceCustomImpl::removeSsrcs(std::vector<uint32_t> ssrcs) {
    _internal->perform([ssrcs = std::move(ssrcs)](GroupInstanceCustomInternal *internal) mutable {
        internal->removeSsrcs(ssrcs);
    });
}

void GroupInstanceCustomImpl::removeIncomingVideoSource(uint32_t ssrc) {
    _internal->perform([ssrc](GroupInstanceCustomInternal *internal) mutable {
        internal->removeIncomingVideoSource(ssrc);
    });
}

void GroupInstanceCustomImpl::setIsMuted(bool isMuted) {
    _internal->perform([isMuted](GroupInstanceCustomInternal *internal) {
        internal->setIsMuted(isMuted);
    });
}

void GroupInstanceCustomImpl::setIsNoiseSuppressionEnabled(bool isNoiseSuppressionEnabled) {
    _internal->perform([isNoiseSuppressionEnabled](GroupInstanceCustomInternal *internal) {
        internal->setIsNoiseSuppressionEnabled(isNoiseSuppressionEnabled);
    });
}

void GroupInstanceCustomImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _internal->perform([videoCapture](GroupInstanceCustomInternal *internal) {
        internal->setVideoCapture(videoCapture, false);
    });
}

void GroupInstanceCustomImpl::setVideoSource(std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> getVideoSource) {
  _internal->perform([getVideoSource](GroupInstanceCustomInternal *internal) {
    internal->setVideoSource(getVideoSource, false);
  });
}

void GroupInstanceCustomImpl::setAudioOutputDevice(std::string id) {
    _internal->perform([id](GroupInstanceCustomInternal *internal) {
        internal->setAudioOutputDevice(id);
    });
}

void GroupInstanceCustomImpl::setAudioInputDevice(std::string id) {
    _internal->perform([id](GroupInstanceCustomInternal *internal) {
        internal->setAudioInputDevice(id);
    });
}

void GroupInstanceCustomImpl::addExternalAudioSamples(std::vector<uint8_t> &&samples) {
    _internal->perform([samples = std::move(samples)](GroupInstanceCustomInternal *internal) mutable {
        internal->addExternalAudioSamples(std::move(samples));
    });
}

void GroupInstanceCustomImpl::addOutgoingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform([sink](GroupInstanceCustomInternal *internal) mutable {
        internal->addOutgoingVideoOutput(sink);
    });
}

void GroupInstanceCustomImpl::addIncomingVideoOutput(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform([endpointId, sink](GroupInstanceCustomInternal *internal) mutable {
        internal->addIncomingVideoOutput(endpointId, sink);
    });
}

void GroupInstanceCustomImpl::setVolume(uint32_t ssrc, double volume) {
    _internal->perform([ssrc, volume](GroupInstanceCustomInternal *internal) {
        internal->setVolume(ssrc, volume);
    });
}

void GroupInstanceCustomImpl::setRequestedVideoChannels(std::vector<VideoChannelDescription> &&requestedVideoChannels) {
    _internal->perform([requestedVideoChannels = std::move(requestedVideoChannels)](GroupInstanceCustomInternal *internal) mutable {
        internal->setRequestedVideoChannels(std::move(requestedVideoChannels));
    });
}

void GroupInstanceCustomImpl::getStats(std::function<void(GroupInstanceStats)> completion) {
    _internal->perform([completion = std::move(completion)](GroupInstanceCustomInternal *internal) mutable {
        internal->getStats(completion);
    });
}

std::vector<GroupInstanceInterface::AudioDevice> GroupInstanceInterface::getAudioDevices(AudioDevice::Type type) {
  auto result = std::vector<AudioDevice>();
#ifdef WEBRTC_LINUX //Not needed for ios, and some crl::sync stuff is needed for windows
  const auto resolve = [&] {
    const auto queueFactory = webrtc::CreateDefaultTaskQueueFactory();
    const auto info = webrtc::AudioDeviceModule::Create(
        webrtc::AudioDeviceModule::kPlatformDefaultAudio,
        queueFactory.get());
    if (!info || info->Init() < 0) {
      return;
    }
    const auto count = type == AudioDevice::Type::Input ? info->RecordingDevices() : info->PlayoutDevices();
    if (count <= 0) {
      return;
    }
    for (auto i = int16_t(); i != count; ++i) {
      char name[webrtc::kAdmMaxDeviceNameSize + 1] = { 0 };
      char id[webrtc::kAdmMaxGuidSize + 1] = { 0 };
      if (type == AudioDevice::Type::Input) {
        info->RecordingDeviceName(i, name, id);
      } else {
        info->PlayoutDeviceName(i, name, id);
      }
      result.push_back({ id, name });
    }
  };
  resolve();
#endif
  return result;
}

} // namespace tgcalls
