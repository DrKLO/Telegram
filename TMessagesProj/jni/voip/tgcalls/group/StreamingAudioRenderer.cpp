#include "StreamingAudioRenderer.h"

#include "common_audio/resampler/include/push_resampler.h"
#include "rtc_base/logging.h"

#include <cstring>

namespace tgcalls {

namespace {

constexpr uint32_t kSourceSampleRate = 48000;
constexpr size_t kSourceSamplesPer10ms = kSourceSampleRate / 100;

} // namespace

StreamingAudioRenderer::StreamingAudioRenderer() :
_resampler(std::make_unique<webrtc::PushResampler<int16_t>>()) {
}

StreamingAudioRenderer::~StreamingAudioRenderer() = default;

bool StreamingAudioRenderer::render(Source const &source, int16_t *audioSamples, size_t numSamples, size_t numChannels, uint32_t samplesPerSec) {
    // PushResampler consumes and produces exactly one 10 ms block per call, and its sinc
    // stage aborts on any other size, so refuse anything else up front.
    if (numChannels == 0 || samplesPerSec == 0 || numSamples != samplesPerSec / 100) {
        RTC_LOG(LS_ERROR) << "StreamingAudioRenderer: refusing buffer of " << numSamples << " frames x " << numChannels << " channels at " << samplesPerSec << " Hz, expected one 10 ms block";
        return false;
    }

    const size_t sourceLength = kSourceSamplesPer10ms * numChannels;
    if (_sourceSamples.size() < sourceLength) {
        _sourceSamples.resize(sourceLength);
    }
    memset(_sourceSamples.data(), 0, sourceLength * sizeof(int16_t));

    source(_sourceSamples.data(), kSourceSamplesPer10ms, numChannels, kSourceSampleRate);

    if (_resampler->InitializeIfNeeded((int)kSourceSampleRate, (int)samplesPerSec, numChannels) != 0) {
        RTC_LOG(LS_ERROR) << "StreamingAudioRenderer: cannot resample " << kSourceSampleRate << " -> " << samplesPerSec << " Hz, " << numChannels << " channels";
        return false;
    }

    const size_t destinationLength = numSamples * numChannels;
    const int written = _resampler->Resample(_sourceSamples.data(), sourceLength, audioSamples, destinationLength);
    if (written != (int)destinationLength) {
        RTC_LOG(LS_ERROR) << "StreamingAudioRenderer: resampled " << written << " samples, expected " << destinationLength;
        return false;
    }
    return true;
}

} // namespace tgcalls
