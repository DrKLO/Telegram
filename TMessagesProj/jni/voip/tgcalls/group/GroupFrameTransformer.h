#ifndef TGCALLS_GROUP_FRAME_TRANSFORMER_H
#define TGCALLS_GROUP_FRAME_TRANSFORMER_H

#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "api/frame_transformer_interface.h"
#include "rtc_base/synchronization/mutex.h"

namespace tgcalls {

// Encrypt/decrypt callback supplied by the application through
// GroupInstanceDescriptor::e2eEncryptDecrypt:
//   (data, userId, isEncrypt, plaintextPrefixLength) -> transformed data.
// An empty return means failure; the frame is then dropped.
using GroupEncryptDecryptFunction =
    std::function<std::vector<uint8_t>(std::vector<uint8_t> const &, int64_t, bool, int32_t)>;

// Holds the local audio level/speech state stamped into every outgoing Opus
// frame. Written from the levels timer, read from the encoder queue.
class AudioLevelAndSpeechHolder {
public:
    AudioLevelAndSpeechHolder() {
    }

    void set(uint8_t audioLevel, bool hasSpeech) {
        webrtc::MutexLock lock(&_mutex);
        _audioLevel = audioLevel;
        _hasSpeech = hasSpeech;
    }

    std::pair<uint8_t, bool> get() {
        webrtc::MutexLock lock(&_mutex);
        return std::make_pair(_audioLevel, _hasSpeech);
    }

private:
    webrtc::Mutex _mutex;
    uint8_t _audioLevel = 0;
    bool _hasSpeech = false;
};

enum class FrameTransformerPayloadType {
    Unknown,
    Opus,
    H264,
    VP8
};

// Returns the frame with every 3-byte NAL start code rewritten to 4 bytes (the
// receiving WebRTC does the same, which would otherwise shift the ciphertext),
// and reports through `headerSize` how many leading bytes must stay in the
// clear for the SFU to parse the packet.
std::vector<uint8_t> calculateH264FramePlaintextHeaderSize(
    rtc::ArrayView<const uint8_t> frame, uint32_t &headerSize);

// VP8: 10 plaintext bytes for a key frame (full uncompressed header), 1 for a
// delta frame.
std::vector<uint8_t> calculateVp8FramePlaintextHeaderSize(
    rtc::ArrayView<const uint8_t> frame, uint32_t &headerSize);

// H264/H265 ciphertext must not contain a 3- or 4-byte NAL start code, or the
// packetizer misparses the frame and the receiver cannot decrypt it. Always
// true for non-H264 payload types.
bool ValidateEncryptedFrame(FrameTransformerPayloadType payloadType,
                            rtc::ArrayView<uint8_t> frame,
                            int plaintextPrefix);

// The four payload transforms, factored out of FrameTransformer so callers that
// are not a FrameTransformerInterface (the reference engine's unsignaled-SSRC
// discovery tap) reuse the exact same byte layout. Each returns an empty vector
// on failure; the caller drops the frame.

// Appends [0x01][(hasSpeech << 7) | (audioLevel & 0x7f)] and encrypts the whole
// buffer with plaintextPrefixLength 0.
std::vector<uint8_t> encryptGroupAudioFrame(
    GroupEncryptDecryptFunction const &transform,
    int64_t userId,
    rtc::ArrayView<const uint8_t> frame,
    uint8_t audioLevel,
    bool hasSpeech);

// Computes the codec-specific plaintext prefix, encrypts, and retries up to 4
// times while ValidateEncryptedFrame rejects the ciphertext.
std::vector<uint8_t> encryptGroupVideoFrame(
    GroupEncryptDecryptFunction const &transform,
    int64_t userId,
    FrameTransformerPayloadType payloadType,
    rtc::ArrayView<const uint8_t> frame);

// Decrypts and strips the trailer. When the extension flag bit is set, reports
// the level/speech through the out-parameters and strips two bytes; when it is
// clear, strips one and leaves them alone. `hadExtension` reports which happened,
// so a caller can mirror CustomImpl and only publish a level when one was
// actually carried. Every out-parameter may be null.
std::vector<uint8_t> decryptGroupAudioFrame(
    GroupEncryptDecryptFunction const &transform,
    int64_t userId,
    rtc::ArrayView<const uint8_t> frame,
    uint8_t *audioLevel,
    bool *hasSpeech,
    bool *hadExtension);

std::vector<uint8_t> decryptGroupVideoFrame(
    GroupEncryptDecryptFunction const &transform,
    int64_t userId,
    rtc::ArrayView<const uint8_t> frame);

class FrameTransformer : public webrtc::FrameTransformerInterface {
public:
    FrameTransformer(bool isEncryptor,
                     GroupEncryptDecryptFunction transform,
                     int64_t userId,
                     std::map<int32_t, FrameTransformerPayloadType> const &payloadTypeMapping,
                     std::function<std::pair<uint8_t, bool>()> getAudioLevelAndSpeech,
                     std::function<void(uint8_t, bool)> setAudioLevelAndSpeech);

    // Variant whose userId is resolved per frame from its SSRC. The reference
    // engine adds a recvonly transceiver before it knows the sender's user id
    // (the requestMediaChannelDescriptions response lands later), so the id
    // cannot be fixed at construction.
    FrameTransformer(bool isEncryptor,
                     GroupEncryptDecryptFunction transform,
                     std::function<int64_t(uint32_t ssrc)> userIdForSsrc,
                     std::map<int32_t, FrameTransformerPayloadType> const &payloadTypeMapping,
                     std::function<std::pair<uint8_t, bool>()> getAudioLevelAndSpeech,
                     std::function<void(uint8_t, bool)> setAudioLevelAndSpeech);

    void RegisterTransformedFrameCallback(
        rtc::scoped_refptr<webrtc::TransformedFrameCallback> callback) override;
    void RegisterTransformedFrameSinkCallback(
        rtc::scoped_refptr<webrtc::TransformedFrameCallback> callback, uint32_t ssrc) override;
    void UnregisterTransformedFrameSinkCallback(uint32_t ssrc) override;
    void Transform(std::unique_ptr<webrtc::TransformableFrameInterface> frame) override;

private:
    bool _isEncryptor = false;
    GroupEncryptDecryptFunction _transform;
    int64_t _userId = 0;
    std::function<int64_t(uint32_t)> _userIdForSsrc;
    std::map<int32_t, FrameTransformerPayloadType> _payloadTypeMapping;
    std::function<std::pair<uint8_t, bool>()> _getAudioLevelAndSpeech;
    std::function<void(uint8_t, bool)> _setAudioLevelAndSpeech;
    webrtc::Mutex _mutex;
    rtc::scoped_refptr<webrtc::TransformedFrameCallback> _sinkCallback;
    std::map<uint32_t, rtc::scoped_refptr<webrtc::TransformedFrameCallback>> _sinkCallbackBySsrc;
};

} // namespace tgcalls

#endif
