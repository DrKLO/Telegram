#include "StreamingMediaContext.h"

#include "AudioStreamingPart.h"
#include "VideoStreamingPart.h"

#include "absl/types/optional.h"
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"
#include "absl/types/variant.h"
#include "rtc_base/logging.h"
#include "rtc_base/synchronization/mutex.h"
#include "common_audio/ring_buffer.h"
#include "modules/audio_mixer/frame_combiner.h"
#include "modules/audio_processing/agc2/vad_with_level.h"
#include "modules/audio_processing/audio_buffer.h"

namespace tgcalls {

namespace {

struct PendingAudioSegmentData {
};

struct PendingVideoSegmentData {
    int32_t channelId = 0;
    VideoChannelDescription::Quality quality = VideoChannelDescription::Quality::Thumbnail;

    PendingVideoSegmentData(int32_t channelId_, VideoChannelDescription::Quality quality_) :
    channelId(channelId_),
    quality(quality_) {
    }
};

struct PendingMediaSegmentPartResult {
    std::vector<uint8_t> data;

    explicit PendingMediaSegmentPartResult(std::vector<uint8_t> &&data_) :
    data(std::move(data_)) {
    }
};

struct PendingMediaSegmentPart {
    absl::variant<PendingAudioSegmentData, PendingVideoSegmentData> typeData;

    int64_t minRequestTimestamp = 0;

    std::shared_ptr<BroadcastPartTask> task;
    std::shared_ptr<PendingMediaSegmentPartResult> result;
};

struct PendingMediaSegment {
    int64_t timestamp = 0;
    std::vector<std::shared_ptr<PendingMediaSegmentPart>> parts;
};

struct VideoSegment {
    VideoChannelDescription::Quality quality;
    std::shared_ptr<VideoStreamingPart> part;
    double lastFramePts = -1.0;
    bool isPlaying = false;
    std::shared_ptr<PendingMediaSegmentPart> pendingVideoQualityUpdatePart;
};

struct MediaSegment {
    int64_t timestamp = 0;
    int64_t duration = 0;
    std::shared_ptr<AudioStreamingPart> audio;
    std::vector<std::shared_ptr<VideoSegment>> video;
};

class SampleRingBuffer {
public:
    SampleRingBuffer(size_t size) {
        _buffer = WebRtc_CreateBuffer(size, sizeof(int16_t));
    }

    ~SampleRingBuffer() {
        if (_buffer) {
            WebRtc_FreeBuffer(_buffer);
        }
    }

    size_t availableForWriting() {
        return WebRtc_available_write(_buffer);
    }

    size_t write(int16_t const *samples, size_t count) {
        return WebRtc_WriteBuffer(_buffer, samples, count);
    }

    size_t read(int16_t *samples, size_t count) {
        return WebRtc_ReadBuffer(_buffer, nullptr, samples, count);
    }

private:
    RingBuffer *_buffer = nullptr;
};

static const int kVadResultHistoryLength = 8;

class VadHistory {
private:
    float _vadResultHistory[kVadResultHistoryLength];

public:
    VadHistory() {
        for (int i = 0; i < kVadResultHistoryLength; i++) {
            _vadResultHistory[i] = 0.0f;
        }
    }

    ~VadHistory() {
    }

    bool update(float vadProbability) {
        for (int i = 1; i < kVadResultHistoryLength; i++) {
            _vadResultHistory[i - 1] = _vadResultHistory[i];
        }
        _vadResultHistory[kVadResultHistoryLength - 1] = vadProbability;

        float movingAverage = 0.0f;
        for (int i = 0; i < kVadResultHistoryLength; i++) {
            movingAverage += _vadResultHistory[i];
        }
        movingAverage /= (float)kVadResultHistoryLength;

        bool vadResult = false;
        if (movingAverage > 0.8f) {
            vadResult = true;
        }

        return vadResult;
    }
};

class CombinedVad {
private:
    std::unique_ptr<webrtc::VadLevelAnalyzer> _vadWithLevel;
    VadHistory _history;

public:
    CombinedVad() {
        _vadWithLevel = std::make_unique<webrtc::VadLevelAnalyzer>(500, webrtc::GetAvailableCpuFeatures());
    }

    ~CombinedVad() {
    }

    bool update(webrtc::AudioBuffer *buffer) {
        if (buffer->num_channels() <= 0) {
            return _history.update(0.0f);
        }
        webrtc::AudioFrameView<float> frameView(buffer->channels(), buffer->num_channels(), buffer->num_frames());
        float peak = 0.0f;
        for (const auto &x : frameView.channel(0)) {
            peak = std::max(std::fabs(x), peak);
        }
        if (peak <= 0.01f) {
            return _history.update(false);
        }

        auto result = _vadWithLevel->AnalyzeFrame(frameView);

        return _history.update(result.speech_probability);
    }

    bool update() {
        return _history.update(0.0f);
    }
};

class SparseVad {
public:
    SparseVad() {
    }

    std::pair<float, bool> update(webrtc::AudioBuffer *buffer) {
        _sampleCount += buffer->num_frames();
        if (_sampleCount >= 400) {
            _sampleCount = 0;
            _currentValue = _vad.update(buffer);
        }

        float currentPeak = 0.0;
        float *samples = buffer->channels()[0];
        for (int i = 0; i < buffer->num_frames(); i++) {
            float sample = samples[i];
            if (sample < 0.0f) {
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
            float norm = 8000.0f;
            _currentLevel = ((float)(_peak)) / norm;
            _peak = 0;
            _peakCount = 0;
        }

        return std::make_pair(_currentLevel, _currentValue);
    }

private:
    CombinedVad _vad;
    bool _currentValue = false;
    size_t _sampleCount = 0;

    int _peakCount = 0;
    float _peak = 0.0;
    float _currentLevel = 0.0;
};

}

class StreamingMediaContextPrivate : public std::enable_shared_from_this<StreamingMediaContextPrivate> {
public:
    StreamingMediaContextPrivate(StreamingMediaContext::StreamingMediaContextArguments &&arguments) :
    _threads(arguments.threads),
    _requestAudioBroadcastPart(arguments.requestAudioBroadcastPart),
    _requestVideoBroadcastPart(arguments.requestVideoBroadcastPart),
    _displayVideoFrame(arguments.displayVideoFrame),
    _updateAudioLevel(arguments.updateAudioLevel),
    _audioRingBuffer(_audioDataRingBufferMaxSize),
    _audioFrameCombiner(false),
    _platformContext(arguments.platformContext) {
    }

    ~StreamingMediaContextPrivate() {
    }

    void start() {
        beginRenderTimer(0);
    }

    void beginRenderTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->render();

            strong->beginRenderTimer(16);
        }, timeoutMs);
    }

    void render() {
        int64_t absoluteTimestamp = rtc::TimeMillis();

        while (getAvailableBufferDuration() > 1000) {
            if (_availableSegments.empty()) {
                _playbackReferenceTimestamp = 0;
                break;
            }

            if (_playbackReferenceTimestamp == 0) {
                _playbackReferenceTimestamp = absoluteTimestamp;
            }

            double relativeTimestamp = ((double)(absoluteTimestamp - _playbackReferenceTimestamp)) / 1000.0;

            auto segment = _availableSegments[0];
            double segmentDuration = ((double)segment->duration) / 1000.0;

            for (auto &videoSegment : segment->video) {
                videoSegment->isPlaying = true;
                cancelPendingVideoQualityUpdate(videoSegment);

                auto frame = videoSegment->part->getFrameAtRelativeTimestamp(relativeTimestamp);
                if (frame) {
                    if (videoSegment->lastFramePts != frame->pts) {
                        videoSegment->lastFramePts = frame->pts;
                        _displayVideoFrame(frame->endpointId, frame->frame);
                    }
                }
            }

            _audioDataMutex.Lock();
            if (segment->audio) {
                while (_audioRingBuffer.availableForWriting() >= 480) {
                    auto audioChannels = segment->audio->get10msPerChannel();
                    if (audioChannels.empty()) {
                        break;
                    }

                    std::vector<webrtc::AudioFrame *> audioFrames;
                    
                    for (const auto &audioChannel : audioChannels) {
                        webrtc::AudioFrame *frame = new webrtc::AudioFrame();
                        frame->UpdateFrame(0, audioChannel.pcmData.data(), audioChannel.pcmData.size(), 48000, webrtc::AudioFrame::SpeechType::kNormalSpeech, webrtc::AudioFrame::VADActivity::kVadActive);
                        audioFrames.push_back(frame);
                        processAudioLevel(audioChannel.ssrc, audioChannel.pcmData);
                    }

                    webrtc::AudioFrame frameOut;
                    _audioFrameCombiner.Combine(audioFrames, 1, 48000, audioFrames.size(), &frameOut);

                    for (webrtc::AudioFrame *frame : audioFrames) {
                        delete frame;
                    }

                    _audioRingBuffer.write(frameOut.data(), frameOut.samples_per_channel());
                }
            }
            _audioDataMutex.Unlock();

            if (relativeTimestamp >= segmentDuration) {
                _playbackReferenceTimestamp += segment->duration;

                if (segment->audio && segment->audio->getRemainingMilliseconds() > 0) {
                    RTC_LOG(LS_INFO) << "render: discarding " << segment->audio->getRemainingMilliseconds() << " ms of audio at the end of a segment";
                }

                _availableSegments.erase(_availableSegments.begin());
            }

            break;
        }

        requestSegmentIfNeeded();
    }

    void processAudioLevel(uint32_t ssrc, std::vector<int16_t> const &samples) {
        if (!_updateAudioLevel) {
            return;
        }

        webrtc::AudioBuffer buffer(48000, 1, 48000, 1, 48000, 1);
        webrtc::StreamConfig config(48000, 1);
        buffer.CopyFrom(samples.data(), config);

        std::pair<float, bool> vadResult = std::make_pair(0.0f, false);
        auto vad = _audioVadMap.find(ssrc);
        if (vad == _audioVadMap.end()) {
            auto newVad = std::make_unique<SparseVad>();
            vadResult = newVad->update(&buffer);
            _audioVadMap.insert(std::make_pair(ssrc, std::move(newVad)));
        } else {
            vadResult = vad->second->update(&buffer);
        }

        _updateAudioLevel(ssrc, vadResult.first, vadResult.second);
    }

    void getAudio(int16_t *audio_samples, const size_t num_samples, const uint32_t samples_per_sec) {
        _audioDataMutex.Lock();

        size_t readSamples = _audioRingBuffer.read(audio_samples, num_samples);
        if (readSamples < num_samples) {
            memset(audio_samples + readSamples, 0, num_samples - readSamples);
        }

        _audioDataMutex.Unlock();
    }

    int64_t getAvailableBufferDuration() {
        int64_t result = 0;

        for (const auto &segment : _availableSegments) {
            result += segment->duration;
        }

        return result;
    }

    void discardPendingSegment() {
        if (!_pendingSegment) {
            return;
        }

        for (const auto &it : _pendingSegment->parts) {
            if (it->task) {
                it->task->cancel();
            }
        }
        _pendingSegment.reset();
    }

    void requestSegmentIfNeeded() {
        if (_pendingSegment) {
            return;
        }

        if (getAvailableBufferDuration() > 2000) {
            return;
        }

        _pendingSegment = std::make_shared<PendingMediaSegment>();
        _pendingSegment->timestamp = _nextSegmentTimestamp;

        //RTC_LOG(LS_INFO) << "initialize pending segment at " << _pendingSegment->timestamp;

        auto audio = std::make_shared<PendingMediaSegmentPart>();
        audio->typeData = PendingAudioSegmentData();
        audio->minRequestTimestamp = 0;
        _pendingSegment->parts.push_back(audio);

        for (size_t i = 0; i < _currentActiveVideoChannelMask.size(); i++) {
            if (_currentActiveVideoChannelMask[i]) {
                int32_t channelId = (int32_t)(i + 1);
                auto video = std::make_shared<PendingMediaSegmentPart>();
                auto endpoint = _currentChannelIdToEndpointMapping.find(channelId);
                auto quality = VideoChannelDescription::Quality::Thumbnail;
                if (endpoint != _currentChannelIdToEndpointMapping.end()) {
                    for (const auto &videoChannel : _activeVideoChannels) {
                        if (videoChannel.endpoint == endpoint->second) {
                            quality = videoChannel.quality;
                            break;
                        }
                    }
                }

                video->typeData = PendingVideoSegmentData(channelId, quality);
                video->minRequestTimestamp = 0;
                _pendingSegment->parts.push_back(video);
            }
        }

        checkPendingSegment();
    }

    void requestPendingVideoQualityUpdate(std::shared_ptr<VideoSegment> segment, int64_t timestamp) {
        if (segment->isPlaying) {
            return;
        }
        auto segmentEndpointId = segment->part->getActiveEndpointId();
        if (!segmentEndpointId) {
            return;
        }

        absl::optional<int32_t> updatedChannelId;
        absl::optional<VideoChannelDescription::Quality> updatedQuality;

        for (size_t i = 0; i < _currentActiveVideoChannelMask.size(); i++) {
            if (_currentActiveVideoChannelMask[i]) {
                int32_t channelId = (int32_t)(i + 1);
                auto video = std::make_shared<PendingMediaSegmentPart>();
                auto endpoint = _currentChannelIdToEndpointMapping.find(channelId);

                if (endpoint != _currentChannelIdToEndpointMapping.end()) {
                    for (const auto &videoChannel : _activeVideoChannels) {
                        if (videoChannel.endpoint == endpoint->second) {
                            updatedChannelId = channelId;
                            updatedQuality = videoChannel.quality;
                            break;
                        }
                    }
                }

                if (updatedQuality) {
                    break;
                }
            }
        }

        if (updatedChannelId && updatedQuality) {
            if (segment->pendingVideoQualityUpdatePart) {
                const auto typeData = &segment->pendingVideoQualityUpdatePart->typeData;
                if (const auto videoData = absl::get_if<PendingVideoSegmentData>(typeData)) {
                    if (videoData->channelId == updatedChannelId.value() && videoData->quality == updatedQuality.value()) {
                        return;
                    }
                }
                cancelPendingVideoQualityUpdate(segment);
            }

            auto video = std::make_shared<PendingMediaSegmentPart>();

            video->typeData = PendingVideoSegmentData(updatedChannelId.value(), updatedQuality.value());
            video->minRequestTimestamp = 0;

            segment->pendingVideoQualityUpdatePart = video;

            const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
            const auto weakSegment = std::weak_ptr<VideoSegment>(segment);
            beginPartTask(video, timestamp, [weak, weakSegment]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto strongSegment = weakSegment.lock();
                if (!strongSegment) {
                    return;
                }

                if (!strongSegment->pendingVideoQualityUpdatePart) {
                    return;
                }

                auto result = strongSegment->pendingVideoQualityUpdatePart->result;
                if (result) {
                    strongSegment->part = std::make_shared<VideoStreamingPart>(std::move(result->data));
                }

                strongSegment->pendingVideoQualityUpdatePart.reset();
            });
        }
    }

    void cancelPendingVideoQualityUpdate(std::shared_ptr<VideoSegment> segment) {
        if (!segment->pendingVideoQualityUpdatePart) {
            return;
        }

        if (segment->pendingVideoQualityUpdatePart->task) {
            segment->pendingVideoQualityUpdatePart->task->cancel();
        }

        segment->pendingVideoQualityUpdatePart.reset();
    }

    void checkPendingSegment() {
        if (!_pendingSegment) {
            //RTC_LOG(LS_INFO) << "checkPendingSegment: _pendingSegment == nullptr";
            return;
        }

        const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());

        auto segmentTimestamp = _pendingSegment->timestamp;

        //RTC_LOG(LS_INFO) << "checkPendingSegment timestamp: " << segmentTimestamp;

        int64_t absoluteTimestamp = rtc::TimeMillis();
        int64_t minDelayedRequestTimeout = INT_MAX;

        bool allPartsDone = true;

        for (auto &part : _pendingSegment->parts) {
            if (!part->result) {
                allPartsDone = false;
            }

            if (!part->result && !part->task) {
                if (part->minRequestTimestamp < absoluteTimestamp) {
                    const auto weakPart = std::weak_ptr<PendingMediaSegmentPart>(part);

                    std::function<void(BroadcastPart &&)> handleResult = [weak, weakPart, threads = _threads, segmentTimestamp](BroadcastPart &&part) {
                        threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, weakPart, part = std::move(part), segmentTimestamp]() mutable {
                            auto strong = weak.lock();
                            if (!strong) {
                                return;
                            }

                            if (!strong->_pendingSegment) {
                                return;
                            }
                            if (strong->_pendingSegment->timestamp != segmentTimestamp) {
                                return;
                            }
                            auto pendingPart = weakPart.lock();
                            if (!pendingPart) {
                                return;
                            }

                            pendingPart->task.reset();

                            switch (part.status) {
                                case BroadcastPart::Status::Success: {
                                    pendingPart->result = std::make_shared<PendingMediaSegmentPartResult>(std::move(part.data));
                                    strong->checkPendingSegment();
                                    break;
                                }
                                case BroadcastPart::Status::NotReady: {
                                    if (segmentTimestamp == 0) {
                                        int64_t responseTimestampMilliseconds = (int64_t)(part.responseTimestamp * 1000.0);
                                        int64_t responseTimestampBoundary = (responseTimestampMilliseconds / strong->_segmentDuration) * strong->_segmentDuration;

                                        strong->_nextSegmentTimestamp = responseTimestampBoundary;
                                        strong->discardPendingSegment();
                                        strong->requestSegmentIfNeeded();
                                    } else {
                                        pendingPart->minRequestTimestamp = rtc::TimeMillis() + 100;
                                        strong->checkPendingSegment();
                                    }
                                    break;
                                }
                                case BroadcastPart::Status::ResyncNeeded: {
                                    int64_t responseTimestampMilliseconds = (int64_t)(part.responseTimestamp * 1000.0);
                                    int64_t responseTimestampBoundary = (responseTimestampMilliseconds / strong->_segmentDuration) * strong->_segmentDuration;

                                    strong->_nextSegmentTimestamp = responseTimestampBoundary;
                                    strong->discardPendingSegment();
                                    strong->requestSegmentIfNeeded();

                                    break;
                                }
                                default: {
                                    RTC_FATAL() << "Unknown part.status";
                                    break;
                                }
                            }
                        });
                    };

                    const auto typeData = &part->typeData;
                    if (const auto audioData = absl::get_if<PendingAudioSegmentData>(typeData)) {
                        part->task = _requestAudioBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, handleResult);
                    } else if (const auto videoData = absl::get_if<PendingVideoSegmentData>(typeData)) {
                        part->task = _requestVideoBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, videoData->channelId, videoData->quality, handleResult);
                    }
                } else {
                    minDelayedRequestTimeout = std::min(minDelayedRequestTimeout, part->minRequestTimestamp - absoluteTimestamp);
                }
            }
        }

        if (minDelayedRequestTimeout < INT32_MAX) {
            //RTC_LOG(LS_INFO) << "delay next checkPendingSegment for " << minDelayedRequestTimeout << " ms";

            const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
            _threads->getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->checkPendingSegment();
            }, (int32_t)minDelayedRequestTimeout);
        }

        if (allPartsDone) {
            std::shared_ptr<MediaSegment> segment = std::make_shared<MediaSegment>();
            segment->timestamp = _pendingSegment->timestamp;
            segment->duration = _segmentDuration;
            for (auto &part : _pendingSegment->parts) {
                const auto typeData = &part->typeData;
                if (const auto audioData = absl::get_if<PendingAudioSegmentData>(typeData)) {
                    segment->audio = std::make_shared<AudioStreamingPart>(std::move(part->result->data));
                    _currentActiveVideoChannelMask = segment->audio->getVideoChannelMask();
                } else if (const auto videoData = absl::get_if<PendingVideoSegmentData>(typeData)) {
                    auto videoSegment = std::make_shared<VideoSegment>();
                    videoSegment->quality = videoData->quality;
                    videoSegment->part = std::make_shared<VideoStreamingPart>(std::move(part->result->data));
                    if (const auto endpointId = videoSegment->part->getActiveEndpointId()) {
                        _currentChannelIdToEndpointMapping[videoData->channelId] = endpointId.value();
                    }
                    segment->video.push_back(videoSegment);
                }
            }
            _availableSegments.push_back(segment);

            //RTC_LOG(LS_INFO) << "commit segment " << segment->timestamp;

            _nextSegmentTimestamp = _pendingSegment->timestamp + _segmentDuration;
            discardPendingSegment();
            requestSegmentIfNeeded();
        }
    }

    void beginPartTask(std::shared_ptr<PendingMediaSegmentPart> part, int64_t segmentTimestamp, std::function<void()> completion) {
        const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
        const auto weakPart = std::weak_ptr<PendingMediaSegmentPart>(part);

        std::function<void(BroadcastPart &&)> handleResult = [weak, weakPart, threads = _threads, completion](BroadcastPart &&part) {
            threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, weakPart, part = std::move(part), completion]() mutable {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto pendingPart = weakPart.lock();
                if (!pendingPart) {
                    return;
                }

                pendingPart->task.reset();

                switch (part.status) {
                    case BroadcastPart::Status::Success: {
                        pendingPart->result = std::make_shared<PendingMediaSegmentPartResult>(std::move(part.data));
                        break;
                    }
                    case BroadcastPart::Status::NotReady: {
                        break;
                    }
                    case BroadcastPart::Status::ResyncNeeded: {
                        break;
                    }
                    default: {
                        RTC_FATAL() << "Unknown part.status";
                        break;
                    }
                }

                completion();
            });
        };

        const auto typeData = &part->typeData;
        if (const auto audioData = absl::get_if<PendingAudioSegmentData>(typeData)) {
            part->task = _requestAudioBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, handleResult);
        } else if (const auto videoData = absl::get_if<PendingVideoSegmentData>(typeData)) {
            part->task = _requestVideoBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, videoData->channelId, videoData->quality, handleResult);
        }
    }

    void setActiveVideoChannels(std::vector<StreamingMediaContext::VideoChannel> const &videoChannels) {
        _activeVideoChannels = videoChannels;

#if DEBUG
        for (auto &updatedVideoChannel : _activeVideoChannels) {
            if (updatedVideoChannel.quality == VideoChannelDescription::Quality::Medium) {
                updatedVideoChannel.quality = VideoChannelDescription::Quality::Thumbnail;
            }
        }
#endif

        for (const auto &updatedVideoChannel : _activeVideoChannels) {
            for (const auto &segment : _availableSegments) {
                for (const auto &video : segment->video) {
                    if (video->part->getActiveEndpointId() == updatedVideoChannel.endpoint) {
                        if (video->quality != updatedVideoChannel.quality) {
                            requestPendingVideoQualityUpdate(video, segment->timestamp);
                        }
                    }
                }
            }
        }
    }

private:
    std::shared_ptr<Threads> _threads;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> _requestAudioBroadcastPart;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, int32_t, VideoChannelDescription::Quality, std::function<void(BroadcastPart &&)>)> _requestVideoBroadcastPart;
    std::function<void(std::string const &, webrtc::VideoFrame const &)> _displayVideoFrame;
    std::function<void(uint32_t, float, bool)> _updateAudioLevel;

    const int64_t _segmentDuration = 500;
    int64_t _nextSegmentTimestamp = 0;

    std::vector<std::shared_ptr<MediaSegment>> _availableSegments;
    std::shared_ptr<PendingMediaSegment> _pendingSegment;

    int64_t _playbackReferenceTimestamp = 0;

    const size_t _audioDataRingBufferMaxSize = 4800;
    webrtc::Mutex _audioDataMutex;
    SampleRingBuffer _audioRingBuffer;
    webrtc::FrameCombiner _audioFrameCombiner;
    std::map<uint32_t, std::unique_ptr<SparseVad>> _audioVadMap;

    std::vector<StreamingMediaContext::VideoChannel> _activeVideoChannels;
    std::bitset<32> _currentActiveVideoChannelMask;
    std::map<int32_t, std::string> _currentChannelIdToEndpointMapping;

    std::shared_ptr<PlatformContext> _platformContext;
};

StreamingMediaContext::StreamingMediaContext(StreamingMediaContextArguments &&arguments) {
    _private = std::make_shared<StreamingMediaContextPrivate>(std::move(arguments));
    _private->start();
}

StreamingMediaContext::~StreamingMediaContext() {
}

void StreamingMediaContext::setActiveVideoChannels(std::vector<VideoChannel> const &videoChannels) {
    _private->setActiveVideoChannels(videoChannels);
}

void StreamingMediaContext::getAudio(int16_t *audio_samples, const size_t num_samples, const uint32_t samples_per_sec) {
    _private->getAudio(audio_samples, num_samples, samples_per_sec);
}

}
