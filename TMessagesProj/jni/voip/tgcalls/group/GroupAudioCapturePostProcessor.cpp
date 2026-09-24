#include "group/GroupAudioCapturePostProcessor.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "modules/audio_processing/audio_buffer.h"

#if USE_RNNOISE
#include "rnnoise.h"
#endif

namespace tgcalls {

VadHistory::VadHistory() {
    for (int i = 0; i < kLength; i++) {
        _vadResultHistory[i] = 0.0f;
    }
}

bool VadHistory::update(float vadProbability) {
    for (int i = 1; i < kLength; i++) {
        _vadResultHistory[i - 1] = _vadResultHistory[i];
    }
    _vadResultHistory[kLength - 1] = vadProbability;

    float movingAverage = 0.0f;
    for (int i = 0; i < kLength; i++) {
        movingAverage += _vadResultHistory[i];
    }
    movingAverage /= (float)kLength;

    bool vadResult = false;
    if (movingAverage > 0.8f) {
        vadResult = true;
    }

    return vadResult;
}

#if USE_RNNOISE

AudioCapturePostProcessor::AudioCapturePostProcessor(std::function<void(GroupLevelValue const &)> updated, std::shared_ptr<NoiseSuppressionConfiguration> noiseSuppressionConfiguration, std::vector<float> *externalAudioSamples, webrtc::Mutex *externalAudioSamplesMutex) :
_updated(updated),
_noiseSuppressionConfiguration(noiseSuppressionConfiguration),
_externalAudioSamples(externalAudioSamples),
_externalAudioSamplesMutex(externalAudioSamplesMutex) {
    int frameSize = rnnoise_get_frame_size();
    _frameSamples.resize(frameSize);

    _denoiseState = rnnoise_create(nullptr);
}

AudioCapturePostProcessor::~AudioCapturePostProcessor() {
    if (_denoiseState) {
        rnnoise_destroy(_denoiseState);
    }
}

void AudioCapturePostProcessor::Initialize(int sample_rate_hz, int num_channels) {
    _currentSampleRate = sample_rate_hz;
}

void AudioCapturePostProcessor::Process(webrtc::AudioBuffer *originalBuffer) {
    if (!originalBuffer) {
        return;
    }
    if (originalBuffer->num_channels() != 1) {
        return;
    }
    if (!_denoiseState) {
        return;
    }
    
    webrtc::AudioBuffer *buffer = originalBuffer;
    bool freeBuffer = false;
    
    if (buffer->num_frames() != _frameSamples.size()) {
        //TODO:optimize by running processing in another thread
        freeBuffer = true;
        size_t sourceSampleRate = _currentSampleRate;
        webrtc::AudioBuffer *newBuffer = new webrtc::AudioBuffer(sourceSampleRate, 1, 48000, 1, 48000, 1);
        webrtc::StreamConfig config((int)sourceSampleRate, 1);
        newBuffer->CopyFrom(buffer->channels(), config);
        buffer = newBuffer;
    }

    float sourcePeak = 0.0f;
    float *sourceSamples = buffer->channels()[0];
    for (int i = 0; i < _frameSamples.size(); i++) {
        sourcePeak = std::max(std::fabs(sourceSamples[i]), sourcePeak);
    }

    if (_noiseSuppressionConfiguration) {
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
    
    if (freeBuffer) {
        delete buffer;
    }
}

std::string AudioCapturePostProcessor::ToString() const {
    return "CustomPostProcessing";
}

void AudioCapturePostProcessor::SetRuntimeSetting(webrtc::AudioProcessing::RuntimeSetting setting) {
}

#endif

} // namespace tgcalls
