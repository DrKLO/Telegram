#ifndef TGCALLS_GROUP_JOIN_PAYLOAD_INTERNAL_H
#define TGCALLS_GROUP_JOIN_PAYLOAD_INTERNAL_H

#include "GroupJoinPayload.h"

#include <vector>
#include <string>
#include <stdint.h>

#include "absl/types/optional.h"

namespace tgcalls {

struct GroupJoinResponsePayload {
    GroupJoinTransportDescription transport;
    absl::optional<GroupJoinVideoInformation> videoInformation;

    static absl::optional<GroupJoinResponsePayload> parse(std::string const &data);
};

struct GroupJoinInternalPayload {
    GroupJoinTransportDescription transport;

    uint32_t audioSsrc = 0;
    absl::optional<GroupParticipantVideoInformation> videoInformation;

    std::string serialize();
};

}

#endif
