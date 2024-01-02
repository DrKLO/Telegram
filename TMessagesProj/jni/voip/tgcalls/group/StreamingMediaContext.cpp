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
#include "modules/audio_processing/agc2/vad_wrapper.h"
#include "modules/audio_processing/audio_buffer.h"
#include "api/video/video_sink_interface.h"
#include "audio/utility/audio_frame_operations.h"

namespace tgcalls {

namespace {

struct PendingAudioSegmentData {
};

struct PendingUnifiedSegmentData {
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
    absl::variant<PendingAudioSegmentData, PendingVideoSegmentData, PendingUnifiedSegmentData> typeData;

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
    int _displayedFrames = 0;
    bool isPlaying = false;
    std::shared_ptr<PendingMediaSegmentPart> pendingVideoQualityUpdatePart;
};

struct UnifiedSegment {
    std::shared_ptr<VideoStreamingPart> videoPart;
    double lastFramePts = -1.0;
    int _displayedFrames = 0;
    bool isPlaying = false;
};

struct MediaSegment {
    int64_t timestamp = 0;
    int64_t duration = 0;
    std::shared_ptr<AudioStreamingPart> audio;
    AudioStreamingPartPersistentDecoder audioDecoder;
    std::shared_ptr<VideoStreamingPart> unifiedAudio;
    std::vector<std::shared_ptr<VideoSegment>> video;
    std::vector<std::shared_ptr<UnifiedSegment>> unified;
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
    webrtc::VoiceActivityDetectorWrapper _vadWithLevel;
    VadHistory _history;

public:
    CombinedVad() :
    _vadWithLevel(500, webrtc::GetAvailableCpuFeatures(), webrtc::AudioProcessing::kSampleRate48kHz) {
    }

    ~CombinedVad() {
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
    _isUnifiedBroadcast(arguments.isUnifiedBroadcast),
    _requestCurrentTime(arguments.requestCurrentTime),
    _requestAudioBroadcastPart(arguments.requestAudioBroadcastPart),
    _requestVideoBroadcastPart(arguments.requestVideoBroadcastPart),
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
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->render();

            strong->beginRenderTimer((int)(1.0 * 1000.0 / 120.0));
        }, webrtc::TimeDelta::Millis(timeoutMs));
    }

    void render() {
        int64_t absoluteTimestamp = rtc::TimeMillis();

        while (true) {
            if (_waitForBufferredMillisecondsBeforeRendering) {
                if (getAvailableBufferDuration() < _waitForBufferredMillisecondsBeforeRendering.value()) {
                    break;
                } else {
                    _waitForBufferredMillisecondsBeforeRendering = absl::nullopt;
                }
            }

            if (_availableSegments.empty()) {
                _playbackReferenceTimestamp = 0;

                _waitForBufferredMillisecondsBeforeRendering = _segmentBufferDuration + _segmentDuration;

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
                
                std::shared_ptr<VideoStreamingSharedState> sharedVideoState;
                auto endpointId = videoSegment->part->getActiveEndpointId();
                if (endpointId.has_value()) {
                    auto it = _sharedVideoStateByEndpointId.find(endpointId.value());
                    if (it != _sharedVideoStateByEndpointId.end()) {
                        sharedVideoState = it->second;
                    } else {
                        sharedVideoState = std::make_shared<VideoStreamingSharedState>();
                        _sharedVideoStateByEndpointId.insert(std::make_pair(endpointId.value(), sharedVideoState));
                    }
                }
                
                auto frame = videoSegment->part->getFrameAtRelativeTimestamp(sharedVideoState.get(), relativeTimestamp);
                if (frame) {
                    if (videoSegment->lastFramePts != frame->pts) {
                        videoSegment->lastFramePts = frame->pts;
                        videoSegment->_displayedFrames += 1;

                        auto sinkList = _videoSinks.find(frame->endpointId);
                        if (sinkList != _videoSinks.end()) {
                            for (const auto &weakSink : sinkList->second) {
                                auto sink = weakSink.lock();
                                if (sink) {
                                    sink->OnFrame(frame->frame);
                                }
                            }
                        }
                    }
                }
            }

            for (auto &videoSegment : segment->unified) {
                videoSegment->isPlaying = true;
                
                absl::optional<std::string> endpointId = "unified";
                std::shared_ptr<VideoStreamingSharedState> sharedVideoState;
                if (endpointId.has_value()) {
                    auto it = _sharedVideoStateByEndpointId.find(endpointId.value());
                    if (it != _sharedVideoStateByEndpointId.end()) {
                        sharedVideoState = it->second;
                    } else {
                        sharedVideoState = std::make_shared<VideoStreamingSharedState>();
                        _sharedVideoStateByEndpointId.insert(std::make_pair(endpointId.value(), sharedVideoState));
                    }
                }

                auto frame = videoSegment->videoPart->getFrameAtRelativeTimestamp(sharedVideoState.get(), relativeTimestamp);
                if (frame) {
                    if (videoSegment->lastFramePts != frame->pts) {
                        videoSegment->lastFramePts = frame->pts;
                        videoSegment->_displayedFrames += 1;

                        auto sinkList = _videoSinks.find("unified");
                        if (sinkList != _videoSinks.end()) {
                            for (const auto &weakSink : sinkList->second) {
                                auto sink = weakSink.lock();
                                if (sink) {
                                    sink->OnFrame(frame->frame);
                                }
                            }
                        }
                    }
                }
            }

            if (segment->audio) {
                const auto available = [&] {
                    _audioDataMutex.Lock();
                    const auto result = (_audioRingBuffer.availableForWriting() >= 480 * _audioRingBufferNumChannels);
                    _audioDataMutex.Unlock();

                    return result;
                };
                while (available()) {
                    auto audioChannels = segment->audio->get10msPerChannel(segment->audioDecoder);
                    if (audioChannels.empty()) {
                        break;
                    }

                    std::vector<webrtc::AudioFrame *> audioFrames;

                    for (const auto &audioChannel : audioChannels) {
                        webrtc::AudioFrame *frame = new webrtc::AudioFrame();
                        frame->UpdateFrame(0, audioChannel.pcmData.data(), audioChannel.pcmData.size(), 48000, webrtc::AudioFrame::SpeechType::kNormalSpeech, webrtc::AudioFrame::VADActivity::kVadActive);

                        auto volumeIt = _volumeBySsrc.find(audioChannel.ssrc);
                        if (volumeIt != _volumeBySsrc.end()) {
                            double outputGain = volumeIt->second;
                            if (outputGain < 0.99f || outputGain > 1.01f) {
                                webrtc::AudioFrameOperations::ScaleWithSat(outputGain, frame);
                            }
                        }

                        audioFrames.push_back(frame);
                        processAudioLevel(audioChannel.ssrc, audioChannel.pcmData);
                    }

                    webrtc::AudioFrame frameOut;
                    _audioFrameCombiner.Combine(audioFrames, 1, 48000, audioFrames.size(), &frameOut);

                    for (webrtc::AudioFrame *frame : audioFrames) {
                        delete frame;
                    }

                    _audioDataMutex.Lock();
                    if (frameOut.num_channels() == _audioRingBufferNumChannels) {
                        _audioRingBuffer.write(frameOut.data(), frameOut.samples_per_channel() * frameOut.num_channels());
                    } else {
                        if (_stereoShuffleBuffer.size() < frameOut.samples_per_channel() * _audioRingBufferNumChannels) {
                            _stereoShuffleBuffer.resize(frameOut.samples_per_channel() * _audioRingBufferNumChannels);
                        }
                        for (int i = 0; i < frameOut.samples_per_channel(); i++) {
                            for (int j = 0; j < _audioRingBufferNumChannels; j++) {
                                _stereoShuffleBuffer[i * _audioRingBufferNumChannels + j] = frameOut.data()[i];
                            }
                        }
                        _audioRingBuffer.write(_stereoShuffleBuffer.data(), frameOut.samples_per_channel() * _audioRingBufferNumChannels);
                    }
                    _audioDataMutex.Unlock();
                }
            } else if (segment->unifiedAudio) {
                const auto available = [&] {
                    _audioDataMutex.Lock();
                    const auto result = (_audioRingBuffer.availableForWriting() >= 480);
                    _audioDataMutex.Unlock();

                    return result;
                };
                while (available()) {
                    auto audioChannels = segment->unifiedAudio->getAudio10msPerChannel(_persistentAudioDecoder);
                    if (audioChannels.empty()) {
                        break;
                    }

                    if (audioChannels[0].numSamples < 480) {
                        RTC_LOG(LS_INFO) << "render: got less than 10ms of audio data (" << audioChannels[0].numSamples << " samples)";
                    }
                    
                    int numChannels = std::min(2, (int)audioChannels.size());

                    webrtc::AudioFrame frameOut;
                    
                    if (numChannels == 1) {
                        frameOut.UpdateFrame(0, audioChannels[0].pcmData.data(), audioChannels[0].pcmData.size(), 48000, webrtc::AudioFrame::SpeechType::kNormalSpeech, webrtc::AudioFrame::VADActivity::kVadActive, numChannels);
                    } else if (numChannels == _audioRingBufferNumChannels) {
                        bool skipFrame = false;
                        int numSamples = (int)audioChannels[0].pcmData.size();
                        for (int i = 1; i < numChannels; i++) {
                            if (audioChannels[i].pcmData.size() != numSamples) {
                                skipFrame = true;
                                break;
                            }
                        }
                        if (skipFrame) {
                            break;
                        }
                        if (_stereoShuffleBuffer.size() < numChannels * numSamples) {
                            _stereoShuffleBuffer.resize(numChannels * numSamples);
                        }
                        for (int i = 0; i < numSamples; i++) {
                            for (int j = 0; j < numChannels; j++) {
                                _stereoShuffleBuffer[i * numChannels + j] = audioChannels[j].pcmData[i];
                            }
                        }
                        frameOut.UpdateFrame(0, _stereoShuffleBuffer.data(), numSamples, 48000, webrtc::AudioFrame::SpeechType::kNormalSpeech, webrtc::AudioFrame::VADActivity::kVadActive, numChannels);
                    } else {
                        bool skipFrame = false;
                        int numSamples = (int)audioChannels[0].pcmData.size();
                        for (int i = 1; i < numChannels; i++) {
                            if (audioChannels[i].pcmData.size() != numSamples) {
                                skipFrame = true;
                                break;
                            }
                        }
                        if (skipFrame) {
                            break;
                        }
                        if (_stereoShuffleBuffer.size() < numChannels * numSamples) {
                            _stereoShuffleBuffer.resize(numChannels * numSamples);
                        }
                        for (int i = 0; i < numSamples; i++) {
                            for (int j = 0; j < numChannels; j++) {
                                _stereoShuffleBuffer[i * numChannels + j] = audioChannels[0].pcmData[i];
                            }
                        }
                        frameOut.UpdateFrame(0, _stereoShuffleBuffer.data(), numSamples, 48000, webrtc::AudioFrame::SpeechType::kNormalSpeech, webrtc::AudioFrame::VADActivity::kVadActive, numChannels);
                    }

                    auto volumeIt = _volumeBySsrc.find(1);
                    if (volumeIt != _volumeBySsrc.end()) {
                        double outputGain = volumeIt->second;
                        if (outputGain < 0.99f || outputGain > 1.01f) {
                            webrtc::AudioFrameOperations::ScaleWithSat(outputGain, &frameOut);
                        }
                    }

                    _audioDataMutex.Lock();
                    if (frameOut.num_channels() == _audioRingBufferNumChannels) {
                        _audioRingBuffer.write(frameOut.data(), frameOut.samples_per_channel() * frameOut.num_channels());
                    } else {
                        if (_stereoShuffleBuffer.size() < frameOut.samples_per_channel() * _audioRingBufferNumChannels) {
                            _stereoShuffleBuffer.resize(frameOut.samples_per_channel() * _audioRingBufferNumChannels);
                        }
                        for (int i = 0; i < frameOut.samples_per_channel(); i++) {
                            for (int j = 0; j < _audioRingBufferNumChannels; j++) {
                                _stereoShuffleBuffer[i * _audioRingBufferNumChannels + j] = frameOut.data()[i];
                            }
                        }
                        _audioRingBuffer.write(_stereoShuffleBuffer.data(), frameOut.samples_per_channel() * _audioRingBufferNumChannels);
                    }
                    _audioDataMutex.Unlock();
                }
            }

            if (relativeTimestamp >= segmentDuration) {
                _playbackReferenceTimestamp += segment->duration;

                if (segment->audio && segment->audio->getRemainingMilliseconds() > 0) {
                    RTC_LOG(LS_INFO) << "render: discarding " << segment->audio->getRemainingMilliseconds() << " ms of audio at the end of a segment";
                }
                if (!segment->video.empty()) {
                    if (segment->video[0]->part->getActiveEndpointId()) {
                        RTC_LOG(LS_INFO) << "render: discarding video frames at the end of a segment (displayed " << segment->video[0]->_displayedFrames << " frames)";
                    }
                }
                if (!segment->unified.empty() && segment->unified[0]->videoPart->hasRemainingFrames()) {
                    RTC_LOG(LS_INFO) << "render: discarding video frames at the end of a segment (displayed " << segment->unified[0]->_displayedFrames << " frames)";
                }

                _availableSegments.erase(_availableSegments.begin());
            }

            break;
        }

        requestSegmentsIfNeeded();
        checkPendingSegments();
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

    void getAudio(int16_t *audio_samples, size_t num_samples, size_t num_channels, uint32_t samples_per_sec) {
        int16_t *buffer = nullptr;

        if (num_channels == _audioRingBufferNumChannels) {
            buffer = audio_samples;
        } else {
            if (_tempAudioBuffer.size() < num_samples * _audioRingBufferNumChannels) {
                _tempAudioBuffer.resize(num_samples * _audioRingBufferNumChannels);
            }
            buffer = _tempAudioBuffer.data();
        }

        _audioDataMutex.Lock();
        size_t readSamples = _audioRingBuffer.read(buffer, num_samples * _audioRingBufferNumChannels);
        _audioDataMutex.Unlock();

        if (num_channels != _audioRingBufferNumChannels) {
            for (size_t sampleIndex = 0; sampleIndex < readSamples / _audioRingBufferNumChannels; sampleIndex++) {
                for (size_t channelIndex = 0; channelIndex < num_channels; channelIndex++) {
                    audio_samples[sampleIndex * num_channels + channelIndex] = _tempAudioBuffer[sampleIndex * _audioRingBufferNumChannels + 0];
                }
            }
        }
        if (readSamples < num_samples * num_channels) {
            memset(audio_samples + readSamples, 0, (num_samples * num_channels - readSamples) * sizeof(int16_t));
        }
    }

    int64_t getAvailableBufferDuration() {
        int64_t result = 0;

        for (const auto &segment : _availableSegments) {
            result += segment->duration;
        }

        return (int)result;
    }

    void discardAllPendingSegments() {
        for (size_t i = 0; i < _pendingSegments.size(); i++) {
            for (const auto &it : _pendingSegments[i]->parts) {
                if (it->task) {
                    it->task->cancel();
                }
            }
        }

        _pendingSegments.clear();
    }

    void requestSegmentsIfNeeded() {
        while (true) {
            if (_nextSegmentTimestamp == -1) {
                if (!_pendingRequestTimeTask && _pendingRequestTimeDelayTaskId == 0) {
                    const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
                    _pendingRequestTimeTask = _requestCurrentTime([weak, threads = _threads](int64_t timestamp) {
                        threads->getMediaThread()->PostTask([weak, timestamp]() {
                            auto strong = weak.lock();
                            if (!strong) {
                                return;
                            }

                            strong->_pendingRequestTimeTask.reset();
                            
                            int64_t adjustedTimestamp = 0;
                            if (timestamp > 0) {
                                adjustedTimestamp = (int64_t)((timestamp / strong->_segmentDuration * strong->_segmentDuration) - strong->_segmentBufferDuration);
                            }

                            if (adjustedTimestamp <= 0) {
                                int taskId = strong->_nextPendingRequestTimeDelayTaskId;
                                strong->_pendingRequestTimeDelayTaskId = taskId;
                                strong->_nextPendingRequestTimeDelayTaskId++;

                                strong->_threads->getMediaThread()->PostDelayedTask([weak, taskId]() {
                                    auto strong = weak.lock();
                                    if (!strong) {
                                        return;
                                    }
                                    if (strong->_pendingRequestTimeDelayTaskId != taskId) {
                                        return;
                                    }

                                    strong->_pendingRequestTimeDelayTaskId = 0;

                                    strong->requestSegmentsIfNeeded();
                                }, webrtc::TimeDelta::Millis(1000));
                            } else {
                                strong->_nextSegmentTimestamp = adjustedTimestamp;
                                strong->requestSegmentsIfNeeded();
                            }
                        });
                    });
                }
                break;
            } else {
                int64_t availableAndRequestedSegmentsDuration = 0;
                availableAndRequestedSegmentsDuration += getAvailableBufferDuration();
                availableAndRequestedSegmentsDuration += _pendingSegments.size() * _segmentDuration;

                if (availableAndRequestedSegmentsDuration > _segmentBufferDuration) {
                    break;
                }
            }

            auto pendingSegment = std::make_shared<PendingMediaSegment>();
            pendingSegment->timestamp = _nextSegmentTimestamp;

            if (_nextSegmentTimestamp != -1) {
                _nextSegmentTimestamp += _segmentDuration;
            }

            auto audio = std::make_shared<PendingMediaSegmentPart>();
            if (_isUnifiedBroadcast) {
                audio->typeData = PendingUnifiedSegmentData();
            } else {
                audio->typeData = PendingAudioSegmentData();
            }
            audio->minRequestTimestamp = 0;
            pendingSegment->parts.push_back(audio);

            for (const auto &videoChannel : _activeVideoChannels) {
                auto channelIdIt = _currentEndpointMapping.find(videoChannel.endpoint);
                if (channelIdIt == _currentEndpointMapping.end()) {
                    continue;
                }

                int32_t channelId = channelIdIt->second + 1;

                auto video = std::make_shared<PendingMediaSegmentPart>();
                video->typeData = PendingVideoSegmentData(channelId, videoChannel.quality);
                video->minRequestTimestamp = 0;
                pendingSegment->parts.push_back(video);
            }

            _pendingSegments.push_back(pendingSegment);

            if (_nextSegmentTimestamp == -1) {
                break;
            }
        }
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

        for (const auto &videoChannel : _activeVideoChannels) {
            auto channelIdIt = _currentEndpointMapping.find(videoChannel.endpoint);
            if (channelIdIt == _currentEndpointMapping.end()) {
                continue;
            }

            updatedChannelId = channelIdIt->second + 1;
            updatedQuality = videoChannel.quality;
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
                    strongSegment->part = std::make_shared<VideoStreamingPart>(std::move(result->data), VideoStreamingPart::ContentType::Video);
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

    void checkPendingSegments() {
        const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());

        int64_t absoluteTimestamp = rtc::TimeMillis();
        int64_t minDelayedRequestTimeout = INT_MAX;

        bool shouldRequestMoreSegments = false;

        for (int i = 0; i < _pendingSegments.size(); i++) {
            auto pendingSegment = _pendingSegments[i];
            auto segmentTimestamp = pendingSegment->timestamp;

            bool allPartsDone = true;

            for (auto &part : pendingSegment->parts) {
                if (!part->result) {
                    allPartsDone = false;
                }

                if (!part->result && !part->task) {
                    if (part->minRequestTimestamp != 0) {
                        if (part->minRequestTimestamp > absoluteTimestamp) {
                            minDelayedRequestTimeout = std::min(minDelayedRequestTimeout, part->minRequestTimestamp - absoluteTimestamp);

                            continue;
                        }
                    }

                    const auto weakSegment = std::weak_ptr<PendingMediaSegment>(pendingSegment);
                    const auto weakPart = std::weak_ptr<PendingMediaSegmentPart>(part);

                    std::function<void(BroadcastPart &&)> handleResult = [weak, weakSegment, weakPart, threads = _threads, segmentTimestamp](BroadcastPart &&part) {
                        threads->getMediaThread()->PostTask([weak, weakSegment, weakPart, part = std::move(part), segmentTimestamp]() mutable {
                            auto strong = weak.lock();
                            if (!strong) {
                                return;
                            }
                            auto strongSegment = weakSegment.lock();
                            if (!strongSegment) {
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
                                    if (strong->_nextSegmentTimestamp == -1) {
                                        strong->_nextSegmentTimestamp = part.timestampMilliseconds + strong->_segmentDuration;
                                    }
                                    strong->checkPendingSegments();
                                    break;
                                }
                                case BroadcastPart::Status::NotReady: {
                                    if (segmentTimestamp == 0 && !strong->_isUnifiedBroadcast) {
                                        int64_t responseTimestampMilliseconds = (int64_t)(part.responseTimestamp * 1000.0);
                                        int64_t responseTimestampBoundary = (responseTimestampMilliseconds / strong->_segmentDuration) * strong->_segmentDuration;

                                        strong->_nextSegmentTimestamp = responseTimestampBoundary;
                                        strong->discardAllPendingSegments();
                                        strong->requestSegmentsIfNeeded();
                                        strong->checkPendingSegments();
                                    } else {
                                        pendingPart->minRequestTimestamp = rtc::TimeMillis() + 100;
                                        strong->checkPendingSegments();
                                    }
                                    break;
                                }
                                case BroadcastPart::Status::ResyncNeeded: {
                                    if (strong->_isUnifiedBroadcast) {
                                        strong->_nextSegmentTimestamp = -1;
                                    } else {
                                        int64_t responseTimestampMilliseconds = (int64_t)(part.responseTimestamp * 1000.0);
                                        int64_t responseTimestampBoundary = (responseTimestampMilliseconds / strong->_segmentDuration) * strong->_segmentDuration;

                                        strong->_nextSegmentTimestamp = responseTimestampBoundary;
                                    }

                                    strong->discardAllPendingSegments();
                                    strong->requestSegmentsIfNeeded();
                                    strong->checkPendingSegments();

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
                    } else if (const auto unifiedData = absl::get_if<PendingUnifiedSegmentData>(typeData)) {
                        part->task = _requestVideoBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, 1, VideoChannelDescription::Quality::Full, handleResult);
                    }
                }
            }

            if (allPartsDone && i == 0) {
                std::shared_ptr<MediaSegment> segment = std::make_shared<MediaSegment>();
                segment->timestamp = pendingSegment->timestamp;
                segment->duration = _segmentDuration;
                for (auto &part : pendingSegment->parts) {
                    const auto typeData = &part->typeData;
                    if (const auto audioData = absl::get_if<PendingAudioSegmentData>(typeData)) {
                        segment->audio = std::make_shared<AudioStreamingPart>(std::move(part->result->data), "ogg", false);
                        _currentEndpointMapping = segment->audio->getEndpointMapping();
                    } else if (const auto videoData = absl::get_if<PendingVideoSegmentData>(typeData)) {
                        auto videoSegment = std::make_shared<VideoSegment>();
                        videoSegment->quality = videoData->quality;
                        if (part->result->data.empty()) {
                            RTC_LOG(LS_INFO) << "Video part " << segment->timestamp << " is empty";
                        }
                        videoSegment->part = std::make_shared<VideoStreamingPart>(std::move(part->result->data), VideoStreamingPart::ContentType::Video);
                        segment->video.push_back(videoSegment);
                    } else if (const auto videoData = absl::get_if<PendingUnifiedSegmentData>(typeData)) {
                        auto unifiedSegment = std::make_shared<UnifiedSegment>();
                        if (part->result->data.empty()) {
                            RTC_LOG(LS_INFO) << "Unified part " << segment->timestamp << " is empty";
                        }
                        std::vector<uint8_t> dataCopy = part->result->data;
                        unifiedSegment->videoPart = std::make_shared<VideoStreamingPart>(std::move(part->result->data), VideoStreamingPart::ContentType::Video);
                        segment->unified.push_back(unifiedSegment);
                        segment->unifiedAudio = std::make_shared<VideoStreamingPart>(std::move(dataCopy), VideoStreamingPart::ContentType::Audio);
                    }
                }
                _availableSegments.push_back(segment);

                shouldRequestMoreSegments = true;

                _pendingSegments.erase(_pendingSegments.begin() + i);
                i--;
            }
        }

        if (minDelayedRequestTimeout < INT32_MAX) {
            const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
            _threads->getMediaThread()->PostDelayedTask([weak]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->checkPendingSegments();
            }, webrtc::TimeDelta::Millis(std::max((int32_t)minDelayedRequestTimeout, 10)));
        }

        if (shouldRequestMoreSegments) {
            requestSegmentsIfNeeded();
        }
    }

    void beginPartTask(std::shared_ptr<PendingMediaSegmentPart> part, int64_t segmentTimestamp, std::function<void()> completion) {
        const auto weak = std::weak_ptr<StreamingMediaContextPrivate>(shared_from_this());
        const auto weakPart = std::weak_ptr<PendingMediaSegmentPart>(part);

        std::function<void(BroadcastPart &&)> handleResult = [weak, weakPart, threads = _threads, completion](BroadcastPart &&part) {
            threads->getMediaThread()->PostTask([weak, weakPart, part = std::move(part), completion]() mutable {
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
        } else if (const auto unifiedData = absl::get_if<PendingUnifiedSegmentData>(typeData)) {
            part->task = _requestVideoBroadcastPart(_platformContext, segmentTimestamp, _segmentDuration, 1, VideoChannelDescription::Quality::Full, handleResult);
        }
    }

    void setVolume(uint32_t ssrc, double volume) {
        _volumeBySsrc[ssrc] = volume;
    }

    void setActiveVideoChannels(std::vector<StreamingMediaContext::VideoChannel> const &videoChannels) {
        if (_isUnifiedBroadcast) {
            return;
        }
        _activeVideoChannels = videoChannels;

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

    void addVideoSink(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        auto it = _videoSinks.find(endpointId);
        if (it == _videoSinks.end()) {
            _videoSinks.insert(std::make_pair(endpointId, std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>>()));
        }
        _videoSinks[endpointId].push_back(sink);
    }

private:
    std::shared_ptr<Threads> _threads;
    bool _isUnifiedBroadcast = false;
    std::function<std::shared_ptr<BroadcastPartTask>(std::function<void(int64_t)>)> _requestCurrentTime;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> _requestAudioBroadcastPart;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, int32_t, VideoChannelDescription::Quality, std::function<void(BroadcastPart &&)>)> _requestVideoBroadcastPart;
    std::function<void(uint32_t, float, bool)> _updateAudioLevel;

    const int _segmentDuration = 1000;
    const int _segmentBufferDuration = 2000;

    int64_t _nextSegmentTimestamp = -1;

    absl::optional<int> _waitForBufferredMillisecondsBeforeRendering;
    std::vector<std::shared_ptr<MediaSegment>> _availableSegments;
    AudioStreamingPartPersistentDecoder _persistentAudioDecoder;

    std::shared_ptr<BroadcastPartTask> _pendingRequestTimeTask;
    int _pendingRequestTimeDelayTaskId = 0;
    int _nextPendingRequestTimeDelayTaskId = 0;

    std::vector<std::shared_ptr<PendingMediaSegment>> _pendingSegments;

    int64_t _playbackReferenceTimestamp = 0;

    const int _audioRingBufferNumChannels = 2;
    const size_t _audioDataRingBufferMaxSize = 4800 * 2;
    webrtc::Mutex _audioDataMutex;
    SampleRingBuffer _audioRingBuffer;
    std::vector<int16_t> _tempAudioBuffer;
    std::vector<int16_t> _stereoShuffleBuffer;
    webrtc::FrameCombiner _audioFrameCombiner;
    std::map<uint32_t, std::unique_ptr<SparseVad>> _audioVadMap;

    std::map<uint32_t, double> _volumeBySsrc;
    std::vector<StreamingMediaContext::VideoChannel> _activeVideoChannels;
    std::map<std::string, std::shared_ptr<VideoStreamingSharedState>> _sharedVideoStateByEndpointId;
    std::map<std::string, std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>>> _videoSinks;

    std::map<std::string, int32_t> _currentEndpointMapping;

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

void StreamingMediaContext::setVolume(uint32_t ssrc, double volume) {
    _private->setVolume(ssrc, volume);
}

void StreamingMediaContext::addVideoSink(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _private->addVideoSink(endpointId, sink);
}

void StreamingMediaContext::getAudio(int16_t *audio_samples, const size_t num_samples, const size_t num_channels, const uint32_t samples_per_sec) {
    _private->getAudio(audio_samples, num_samples, num_channels, samples_per_sec);
}

}
