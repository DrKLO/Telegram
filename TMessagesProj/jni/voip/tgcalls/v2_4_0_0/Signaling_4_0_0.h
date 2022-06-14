#ifndef TGCALLS_SIGNALING_4_0_0_H
#define TGCALLS_SIGNALING_4_0_0_H

#include <string>
#include <vector>

#include "absl/types/variant.h"
#include "absl/types/optional.h"
#include "api/rtp_parameters.h"

namespace tgcalls {

namespace signaling_4_0_0 {

struct DtlsFingerprint {
    std::string hash;
    std::string setup;
    std::string fingerprint;
};

struct ConnectionAddress {
    std::string ip;
    int port = 0;
};

struct IceCandidate {
    std::string sdpString;
};

struct SsrcGroup {
    std::vector<uint32_t> ssrcs;
    std::string semantics;
};

struct FeedbackType {
    std::string type;
    std::string subtype;
};

struct PayloadType {
    uint32_t id = 0;
    std::string name;
    uint32_t clockrate = 0;
    uint32_t channels = 0;
    std::vector<FeedbackType> feedbackTypes;
    std::vector<std::pair<std::string, std::string>> parameters;
};

struct MediaContent {
    uint32_t ssrc = 0;
    std::vector<SsrcGroup> ssrcGroups;
    std::vector<PayloadType> payloadTypes;
    std::vector<webrtc::RtpExtension> rtpExtensions;
};

struct InitialSetupMessage {
    std::string ufrag;
    std::string pwd;
    std::vector<DtlsFingerprint> fingerprints;
    absl::optional<MediaContent> audio;
    absl::optional<MediaContent> video;
    absl::optional<MediaContent> screencast;
};

struct CandidatesMessage {
    std::vector<IceCandidate> iceCandidates;
};

struct MediaStateMessage {
    enum class VideoState {
        Inactive,
        Suspended,
        Active
    };

    enum class VideoRotation {
        Rotation0,
        Rotation90,
        Rotation180,
        Rotation270
    };

    bool isMuted = false;
    VideoState videoState = VideoState::Inactive;
    VideoRotation videoRotation = VideoRotation::Rotation0;
    VideoState screencastState = VideoState::Inactive;
    bool isBatteryLow = false;

};

struct Message {
    absl::variant<
        InitialSetupMessage,
        CandidatesMessage,
        MediaStateMessage> data;

    std::vector<uint8_t> serialize() const;
    static absl::optional<Message> parse(const std::vector<uint8_t> &data);
};

};

} // namespace tgcalls

#endif