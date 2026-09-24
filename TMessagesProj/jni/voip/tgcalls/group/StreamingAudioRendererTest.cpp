#include "group/StreamingAudioRenderer.h"

#include <cstdio>
#include <cstdlib>
#include <vector>

namespace {

int g_failures = 0;

#define CHECK_TRUE(cond)                                                  \
    do {                                                                  \
        if (!(cond)) {                                                    \
            std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);   \
            g_failures++;                                                 \
        }                                                                 \
    } while (0)

// A stream source whose every sample is `value`, regardless of the format asked for.
tgcalls::StreamingAudioRenderer::Source constantSource(int16_t value) {
    return [value](int16_t *samples, size_t numSamples, size_t numChannels, uint32_t) {
        for (size_t i = 0; i < numSamples * numChannels; i++) {
            samples[i] = value;
        }
    };
}

// Number of samples in [from, buffer.size()) that differ from `expected` by more than `tolerance`.
size_t countOutside(std::vector<int16_t> const &buffer, size_t from, int16_t expected, int tolerance) {
    size_t count = 0;
    for (size_t i = from; i < buffer.size(); i++) {
        if (std::abs((int)buffer[i] - (int)expected) > tolerance) {
            count++;
        }
    }
    return count;
}

// Most third-party A2DP devices negotiate a 44.1 kHz playout rate. 48000 -> 44100 reduces
// to 160:147, which the legacy webrtc::Resampler does not support, and the live stream was
// silent on those devices while AirPods (48 kHz) played fine.
void TestRendersInto44100HzStereoBuffer() {
    tgcalls::StreamingAudioRenderer renderer;
    std::vector<int16_t> buffer(441 * 2, 0);

    bool rendered = renderer.render(constantSource(1000), buffer.data(), 441, 2, 44100);

    CHECK_TRUE(rendered);
    // The resampler needs a few frames of history before it settles, so judge the second half.
    CHECK_TRUE(countOutside(buffer, buffer.size() / 2, 1000, 16) == 0);
}

// HFP routes run at 16 kHz mono.
void TestRendersInto16000HzMonoBuffer() {
    tgcalls::StreamingAudioRenderer renderer;
    std::vector<int16_t> buffer(160, 0);

    bool rendered = renderer.render(constantSource(1000), buffer.data(), 160, 1, 16000);

    CHECK_TRUE(rendered);
    CHECK_TRUE(countOutside(buffer, buffer.size() / 2, 1000, 16) == 0);
}

// When the playout rate matches the stream, samples pass through untouched.
void TestPassesThrough48000HzUnchanged() {
    tgcalls::StreamingAudioRenderer renderer;
    std::vector<int16_t> buffer(480 * 2, 0);
    auto ramp = [](int16_t *samples, size_t numSamples, size_t numChannels, uint32_t) {
        for (size_t i = 0; i < numSamples * numChannels; i++) {
            samples[i] = (int16_t)i;
        }
    };

    bool rendered = renderer.render(ramp, buffer.data(), 480, 2, 48000);

    CHECK_TRUE(rendered);
    size_t mismatches = 0;
    for (size_t i = 0; i < buffer.size(); i++) {
        if (buffer[i] != (int16_t)i) {
            mismatches++;
        }
    }
    CHECK_TRUE(mismatches == 0);
}

// A route can change mid-stream (e.g. speaker -> A2DP); the renderer must follow it.
void TestFollowsRateChangeBetweenCalls() {
    tgcalls::StreamingAudioRenderer renderer;
    std::vector<int16_t> first(480 * 2, 0);
    std::vector<int16_t> second(441 * 2, 0);

    CHECK_TRUE(renderer.render(constantSource(1000), first.data(), 480, 2, 48000));
    CHECK_TRUE(renderer.render(constantSource(1000), second.data(), 441, 2, 44100));

    CHECK_TRUE(countOutside(second, second.size() / 2, 1000, 16) == 0);
}

// The renderer only produces whole 10 ms blocks; a buffer of any other size is refused and
// left as it was, rather than resampled into the wrong length.
void TestRefusesBufferThatIsNotOne10msBlock() {
    tgcalls::StreamingAudioRenderer renderer;
    std::vector<int16_t> buffer(400 * 2, 7);

    bool rendered = renderer.render(constantSource(1000), buffer.data(), 400, 2, 48000);

    CHECK_TRUE(!rendered);
    CHECK_TRUE(countOutside(buffer, 0, 7, 0) == 0);
}

} // namespace

int main() {
    TestRendersInto44100HzStereoBuffer();
    TestRendersInto16000HzMonoBuffer();
    TestPassesThrough48000HzUnchanged();
    TestFollowsRateChangeBetweenCalls();
    TestRefusesBufferThatIsNotOne10msBlock();

    if (g_failures != 0) {
        std::printf("%d failure(s)\n", g_failures);
        return 1;
    }
    std::printf("ok\n");
    return 0;
}
