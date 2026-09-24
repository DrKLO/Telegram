#ifndef TGCALLS_STREAMING_AUDIO_RENDERER_H
#define TGCALLS_STREAMING_AUDIO_RENDERER_H

#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <vector>

namespace webrtc {
template <typename T>
class PushResampler;
}

namespace tgcalls {

// Renders broadcast (live-stream) audio into an audio-device playout buffer.
//
// The stream decoder always produces 48 kHz, but the playout buffer runs at whatever
// rate the hardware route negotiated: 48 kHz on the built-in speaker and AirPods,
// 44.1 kHz on most third-party A2DP devices, 8 or 16 kHz on HFP. The renderer pulls
// 10 ms from the source and resamples it to the buffer's rate.
//
// This must be webrtc::PushResampler, not the legacy webrtc::Resampler: the latter only
// supports a fixed table of integer ratios, refuses 48000 -> 44100 (160:147), and that
// refusal used to leave the buffer silent on every 44.1 kHz Bluetooth device.
class StreamingAudioRenderer {
public:
    // Fills `samples` with `numSamples` frames of `numChannels` interleaved 16-bit PCM
    // at `sampleRate`.
    using Source = std::function<void(int16_t *samples, size_t numSamples, size_t numChannels, uint32_t sampleRate)>;

    StreamingAudioRenderer();
    ~StreamingAudioRenderer();

    // Overwrites `audioSamples` (`numSamples` frames of `numChannels` interleaved 16-bit
    // PCM at `samplesPerSec`) with the next 10 ms from `source`. `numSamples` must be one
    // 10 ms block, i.e. samplesPerSec / 100. Returns false, leaving the buffer untouched,
    // when the buffer cannot be produced.
    bool render(Source const &source, int16_t *audioSamples, size_t numSamples, size_t numChannels, uint32_t samplesPerSec);

private:
    std::unique_ptr<webrtc::PushResampler<int16_t>> _resampler;
    std::vector<int16_t> _sourceSamples;
};

} // namespace tgcalls

#endif
