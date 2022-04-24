#ifndef TGCALLS_SIGNALING_H
#define TGCALLS_SIGNALING_H

#include <string>
#include <vector>

#include "absl/types/variant.h"
#include "absl/types/optional.h"
#include "api/rtp_parameters.h"

namespace tgcalls {

namespace signaling {

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
    
    bool operator==(SsrcGroup const &rhs) const {
        if (ssrcs != rhs.ssrcs) {
            return false;
        }
        
        if (semantics != rhs.semantics) {
            return false;
        }
        
        return true;
    }
};

struct FeedbackType {
    std::string type;
    std::string subtype;
    
    bool operator==(FeedbackType const &rhs) const {
        if (type != rhs.type) {
            return false;
        }
        if (subtype != rhs.subtype) {
            return false;
        }
        
        return true;
    }
};

struct PayloadType {
    uint32_t id = 0;
    std::string name;
    uint32_t clockrate = 0;
    uint32_t channels = 0;
    std::vector<FeedbackType> feedbackTypes;
    std::vector<std::pair<std::string, std::string>> parameters;
    
    bool operator==(PayloadType const &rhs) const {
        if (id != rhs.id) {
            return false;
        }
        if (name != rhs.name) {
            return false;
        }
        if (clockrate != rhs.clockrate) {
            return false;
        }
        if (channels != rhs.channels) {
            return false;
        }
        if (feedbackTypes != rhs.feedbackTypes) {
            return false;
        }
        if (parameters != rhs.parameters) {
            return false;
        }
        
        return true;
    }
};

struct MediaContent {
    enum class Type {
        Audio,
        Video
    };
    
    Type type = Type::Audio;
    uint32_t ssrc = 0;
    std::vector<SsrcGroup> ssrcGroups;
    std::vector<PayloadType> payloadTypes;
    std::vector<webrtc::RtpExtension> rtpExtensions;
    
    bool operator==(const MediaContent& rhs) const {
        if (type != rhs.type) {
            return false;
        }
        if (ssrc != rhs.ssrc) {
            return false;
        }
        if (ssrcGroups != rhs.ssrcGroups) {
            return false;
        }
        
        std::vector<PayloadType> sortedPayloadTypes = payloadTypes;
        std::sort(sortedPayloadTypes.begin(), sortedPayloadTypes.end(), [](PayloadType const &lhs, PayloadType const &rhs) {
            return lhs.id < rhs.id;
        });
        std::vector<PayloadType> sortedRhsPayloadTypes = rhs.payloadTypes;
        std::sort(sortedRhsPayloadTypes.begin(), sortedRhsPayloadTypes.end(), [](PayloadType const &lhs, PayloadType const &rhs) {
            return lhs.id < rhs.id;
        });
        if (sortedPayloadTypes != sortedRhsPayloadTypes) {
            return false;
        }
        
        if (rtpExtensions != rhs.rtpExtensions) {
            return false;
        }
        
        return true;
    }
};

struct InitialSetupMessage {
    std::string ufrag;
    std::string pwd;
    bool supportsRenomination = false;
    std::vector<DtlsFingerprint> fingerprints;
};

struct NegotiateChannelsMessage {
    uint32_t exchangeId = 0;
    std::vector<MediaContent> contents;
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
        NegotiateChannelsMessage,
        CandidatesMessage,
        MediaStateMessage> data;

    std::vector<uint8_t> serialize() const;
    static absl::optional<Message> parse(const std::vector<uint8_t> &data);
};

};

} // namespace tgcalls

#endif
