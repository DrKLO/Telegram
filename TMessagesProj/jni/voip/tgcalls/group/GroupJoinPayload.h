#ifndef TGCALLS_GROUP_JOIN_PAYLOAD_H
#define TGCALLS_GROUP_JOIN_PAYLOAD_H

#include <vector>
#include <string>
#include <stdint.h>

namespace tgcalls {

struct GroupJoinPayloadVideoSourceGroup {
    std::vector<uint32_t> ssrcs;
    std::string semantics;
};

struct GroupJoinPayloadVideoPayloadType {
    struct FeedbackType {
        std::string type;
        std::string subtype;
    };

    uint32_t id = 0;
    std::string name;
    uint32_t clockrate = 0;
    uint32_t channels = 0;
    std::vector<FeedbackType> feedbackTypes;
    std::vector<std::pair<std::string, std::string>> parameters;
};

struct GroupJoinTransportDescription {
    struct Fingerprint {
        std::string hash;
        std::string setup;
        std::string fingerprint;
    };

    struct Candidate {
        std::string port;
        std::string protocol;
        std::string network;
        std::string generation;
        std::string id;
        std::string component;
        std::string foundation;
        std::string priority;
        std::string ip;
        std::string type;

        std::string tcpType;
        std::string relAddr;
        std::string relPort;
    };

    std::string ufrag;
    std::string pwd;
    std::vector<Fingerprint> fingerprints;
    std::vector<Candidate> candidates;
};

struct GroupJoinVideoInformation {
    uint32_t serverVideoBandwidthProbingSsrc = 0;
    std::string endpointId;
    std::vector<GroupJoinPayloadVideoPayloadType> payloadTypes;
    std::vector<std::pair<uint32_t, std::string>> extensionMap;
};

struct GroupParticipantVideoInformation {
    std::string endpointId;
    std::vector<GroupJoinPayloadVideoSourceGroup> ssrcGroups;
};

struct GroupJoinPayload {
    uint32_t audioSsrc = 0;
    std::string json;
};

}

#endif
