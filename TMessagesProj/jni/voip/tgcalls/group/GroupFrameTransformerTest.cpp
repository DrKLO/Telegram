#include "group/GroupFrameTransformer.h"

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

// Reversible stand-in for real crypto. Keeps the first `prefix` bytes verbatim,
// XORs the rest with 0xA5, then appends [prefix][0x5A] so the decrypt side can
// recover the prefix (real crypto recovers it from its own framing; the app
// always passes 0 on decrypt).
std::vector<uint8_t> fakeTransform(std::vector<uint8_t> const &data, int64_t,
                                   bool isEncrypt, int32_t prefix) {
    std::vector<uint8_t> out;
    if (isEncrypt) {
        out = data;
        for (size_t i = (size_t)prefix; i < out.size(); i++) {
            out[i] = (uint8_t)(out[i] ^ 0xA5);
        }
        out.push_back((uint8_t)prefix);
        out.push_back(0x5A);
        return out;
    }
    if (data.size() < 2 || data[data.size() - 1] != 0x5A) {
        return {};
    }
    size_t p = data[data.size() - 2];
    out.assign(data.begin(), data.end() - 2);
    for (size_t i = p; i < out.size(); i++) {
        out[i] = (uint8_t)(out[i] ^ 0xA5);
    }
    return out;
}

// The two-byte trailer is the only way a CustomImpl peer learns our level and
// speaking state when encryption is on, so its exact position is contractual.
void TestAudioTrailerLayout() {
    std::vector<uint8_t> captured;
    int32_t capturedPrefix = -1;
    auto capture = [&](std::vector<uint8_t> const &data, int64_t userId,
                       bool isEncrypt, int32_t prefix) {
        captured = data;
        capturedPrefix = prefix;
        return fakeTransform(data, userId, isEncrypt, prefix);
    };

    const std::vector<uint8_t> payload = {0x11, 0x22, 0x33};
    auto encrypted = tgcalls::encryptGroupAudioFrame(capture, 0, payload, 0x2a, true);

    CHECK_TRUE(!encrypted.empty());
    CHECK_TRUE(capturedPrefix == 0);
    CHECK_TRUE(captured.size() == payload.size() + 2);
    CHECK_TRUE(captured[captured.size() - 2] == 0x01);
    CHECK_TRUE(captured[captured.size() - 1] == (uint8_t)(0x80 | 0x2a));
}

void TestAudioRoundTrip() {
    const std::vector<uint8_t> payload = {0x11, 0x22, 0x33, 0x44};
    auto encrypted = tgcalls::encryptGroupAudioFrame(fakeTransform, 0, payload, 0x2a, true);
    CHECK_TRUE(!encrypted.empty());

    uint8_t level = 0;
    bool hasSpeech = false;
    bool hadExtension = false;
    auto decrypted = tgcalls::decryptGroupAudioFrame(fakeTransform, 7, encrypted,
                                                     &level, &hasSpeech, &hadExtension);
    CHECK_TRUE(decrypted == payload);
    CHECK_TRUE(level == 0x2a);
    CHECK_TRUE(hasSpeech == true);
    CHECK_TRUE(hadExtension == true);
}

void TestAudioNoSpeechFlag() {
    const std::vector<uint8_t> payload = {0x01, 0x02};
    auto encrypted = tgcalls::encryptGroupAudioFrame(fakeTransform, 0, payload, 0x7f, false);
    uint8_t level = 0;
    bool hasSpeech = true;
    auto decrypted = tgcalls::decryptGroupAudioFrame(fakeTransform, 0, encrypted,
                                                     &level, &hasSpeech, nullptr);
    CHECK_TRUE(decrypted == payload);
    CHECK_TRUE(level == 0x7f);
    CHECK_TRUE(hasSpeech == false);
}

// Quirk preserved verbatim from CustomImpl: when the extension flag bit is
// clear, exactly ONE trailing byte is stripped, not two. A peer that stripped
// two here would corrupt every Opus frame from an older sender.
void TestDecryptWithoutExtensionFlagStripsOneByte() {
    // Plaintext the fake will produce: {0xAA, 0xBB, 0x00, 0x55}. The
    // second-to-last byte is 0x00, so bit 0 is clear.
    const std::vector<uint8_t> plaintext = {0xAA, 0xBB, 0x00, 0x55};
    auto asIfEncrypted = fakeTransform(plaintext, 0, true, 0);

    uint8_t level = 0x11;
    bool hasSpeech = true;
    bool hadExtension = true;
    auto decrypted = tgcalls::decryptGroupAudioFrame(fakeTransform, 0, asIfEncrypted,
                                                     &level, &hasSpeech, &hadExtension);
    CHECK_TRUE(decrypted.size() == plaintext.size() - 1);
    CHECK_TRUE(level == 0x11);        // untouched
    CHECK_TRUE(hasSpeech == true);    // untouched
    CHECK_TRUE(hadExtension == false);
}

void TestEmptyTransformResultFails() {
    auto alwaysEmpty = [](std::vector<uint8_t> const &, int64_t, bool, int32_t) {
        return std::vector<uint8_t>();
    };
    const std::vector<uint8_t> payload = {0x01, 0x02};
    CHECK_TRUE(tgcalls::encryptGroupAudioFrame(alwaysEmpty, 0, payload, 0, false).empty());
    CHECK_TRUE(tgcalls::decryptGroupAudioFrame(alwaysEmpty, 0, payload, nullptr, nullptr, nullptr).empty());
    CHECK_TRUE(tgcalls::encryptGroupVideoFrame(alwaysEmpty, 0,
        tgcalls::FrameTransformerPayloadType::VP8, payload).empty());
}

// VP8: key frame (P bit clear) keeps 10 bytes in the clear, delta frame 1.
void TestVp8PlaintextPrefix() {
    int32_t capturedPrefix = -1;
    auto capture = [&](std::vector<uint8_t> const &data, int64_t userId,
                       bool isEncrypt, int32_t prefix) {
        capturedPrefix = prefix;
        return fakeTransform(data, userId, isEncrypt, prefix);
    };

    std::vector<uint8_t> keyFrame(32, 0x00);
    keyFrame[0] = 0x00;  // P bit clear -> key frame
    tgcalls::encryptGroupVideoFrame(capture, 0, tgcalls::FrameTransformerPayloadType::VP8, keyFrame);
    CHECK_TRUE(capturedPrefix == 10);

    std::vector<uint8_t> deltaFrame(32, 0x00);
    deltaFrame[0] = 0x01;  // P bit set -> delta frame
    tgcalls::encryptGroupVideoFrame(capture, 0, tgcalls::FrameTransformerPayloadType::VP8, deltaFrame);
    CHECK_TRUE(capturedPrefix == 1);
}

// H264 3-byte start codes are rewritten to 4 bytes before encryption, because
// the receiving WebRTC does the same and the ciphertext would otherwise shift.
void TestH264ShortStartCodeIsWidened() {
    std::vector<uint8_t> captured;
    int32_t capturedPrefix = -1;
    auto capture = [&](std::vector<uint8_t> const &data, int64_t userId,
                       bool isEncrypt, int32_t prefix) {
        captured = data;
        capturedPrefix = prefix;
        return fakeTransform(data, userId, isEncrypt, prefix);
    };

    // One IDR NAL (0x65 & 0x1F == 5) behind a 3-byte start code.
    std::vector<uint8_t> frame = {0x00, 0x00, 0x01, 0x65};
    frame.resize(24, 0x42);

    tgcalls::encryptGroupVideoFrame(capture, 0,
        tgcalls::FrameTransformerPayloadType::H264, frame);

    CHECK_TRUE(captured.size() == frame.size() + 1);
    CHECK_TRUE(capturedPrefix > 0);
    CHECK_TRUE((size_t)capturedPrefix <= captured.size());
}

// Ciphertext containing a NAL start code is rejected and the encrypt retried,
// up to 4 attempts. Real crypto re-randomizes; this stand-in counts attempts.
//
// The start code is injected 8 bytes from the end, not at the very end:
// FindNextH26XNaluIndex loops `i < bufferSize - 3`, so a start code occupying
// the final three bytes is never examined. That blind spot is pre-existing
// CustomImpl behavior, faithfully preserved by the extraction; injecting there
// would make this test pass vacuously.
void TestH264ValidationRetries() {
    int attempts = 0;
    auto dirtyThenClean = [&](std::vector<uint8_t> const &data, int64_t, bool, int32_t prefix) {
        attempts++;
        std::vector<uint8_t> out = data;
        for (size_t i = (size_t)prefix; i < out.size(); i++) {
            out[i] = (uint8_t)(out[i] ^ 0xA5);
        }
        if (attempts < 3 && out.size() >= (size_t)prefix + 11) {
            out[out.size() - 8] = 0x00;
            out[out.size() - 7] = 0x00;
            out[out.size() - 6] = 0x01;
        }
        return out;
    };

    std::vector<uint8_t> frame = {0x00, 0x00, 0x00, 0x01, 0x65};
    frame.resize(32, 0x42);

    auto result = tgcalls::encryptGroupVideoFrame(dirtyThenClean, 0,
        tgcalls::FrameTransformerPayloadType::H264, frame);
    CHECK_TRUE(!result.empty());
    CHECK_TRUE(attempts == 3);

    attempts = 0;
    auto alwaysDirty = [&](std::vector<uint8_t> const &data, int64_t, bool, int32_t prefix) {
        attempts++;
        std::vector<uint8_t> out = data;
        for (size_t i = (size_t)prefix; i < out.size(); i++) {
            out[i] = (uint8_t)(out[i] ^ 0xA5);
        }
        if (out.size() >= (size_t)prefix + 11) {
            out[out.size() - 8] = 0x00;
            out[out.size() - 7] = 0x00;
            out[out.size() - 6] = 0x01;
        }
        return out;
    };
    CHECK_TRUE(tgcalls::encryptGroupVideoFrame(alwaysDirty, 0,
        tgcalls::FrameTransformerPayloadType::H264, frame).empty());
    CHECK_TRUE(attempts == 4);
}

// Video decrypt adds no trailer handling: the payload comes back verbatim.
void TestVideoRoundTrip() {
    std::vector<uint8_t> frame = {0x00, 0x00, 0x00, 0x01, 0x65};
    frame.resize(32, 0x42);

    auto encrypted = tgcalls::encryptGroupVideoFrame(fakeTransform, 0,
        tgcalls::FrameTransformerPayloadType::H264, frame);
    CHECK_TRUE(!encrypted.empty());

    auto decrypted = tgcalls::decryptGroupVideoFrame(fakeTransform, 5, encrypted);
    CHECK_TRUE(decrypted.size() == frame.size());
    CHECK_TRUE(decrypted == frame);
}

} // namespace

int main() {
    TestAudioTrailerLayout();
    TestAudioRoundTrip();
    TestAudioNoSpeechFlag();
    TestDecryptWithoutExtensionFlagStripsOneByte();
    TestEmptyTransformResultFails();
    TestVp8PlaintextPrefix();
    TestH264ShortStartCodeIsWidened();
    TestH264ValidationRetries();
    TestVideoRoundTrip();

    if (g_failures != 0) {
        std::printf("%d failure(s)\n", g_failures);
        return 1;
    }
    std::printf("All GroupFrameTransformer tests passed\n");
    return 0;
}
