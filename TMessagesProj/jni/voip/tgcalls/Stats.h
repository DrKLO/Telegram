#ifndef TGCALLS_STATS_H
#define TGCALLS_STATS_H

namespace tgcalls {

enum class CallStatsConnectionEndpointType {
    ConnectionEndpointP2P = 0,
    ConnectionEndpointTURN = 1
};

struct CallStatsNetworkRecord {
    int32_t timestamp = 0;
    CallStatsConnectionEndpointType endpointType = CallStatsConnectionEndpointType::ConnectionEndpointP2P;
    bool isLowCost = false;
};

struct CallStatsBitrateRecord {
    int32_t timestamp = 0;
    int32_t bitrate = 0;
};

struct CallStats {
    std::string outgoingCodec;
    std::vector<CallStatsNetworkRecord> networkRecords;
    std::vector<CallStatsBitrateRecord> bitrateRecords;
};

} // namespace tgcalls

#endif
