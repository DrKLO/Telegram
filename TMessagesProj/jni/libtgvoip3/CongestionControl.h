//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_CONGESTIONCONTROL_H
#define LIBTGVOIP_CONGESTIONCONTROL_H

#include "threading.h"
#include "Buffers.h"

#include <cstdint>
#include <cstdlib>
#include <array>

namespace tgvoip
{

enum class ConctlAct
{
    NONE,
    INCREASE,
    DECREASE,
};

struct tgvoip_congestionctl_packet_t
{
    double sendTime;
    std::size_t size;
    std::uint32_t seq;
};

class CongestionControl
{
public:
    CongestionControl();
    ~CongestionControl();

    void PacketSent(std::uint32_t seq, std::size_t size);
    void PacketLost(std::uint32_t seq);
    void PacketAcknowledged(std::uint32_t seq);
    void Tick();

    double GetAverageRTT() const;
    double GetMinimumRTT() const;
    std::size_t GetInflightDataSize() const;
    std::size_t GetCongestionWindow() const;
    std::size_t GetAcknowledgedDataSize() const;
    ConctlAct GetBandwidthControlAction() const;
    std::uint32_t GetSendLossCount() const;

private:
    HistoricBuffer<double, 100> m_rttHistory;
    HistoricBuffer<std::size_t, 30> m_inflightHistory;
    std::array<tgvoip_congestionctl_packet_t, 100> m_inflightPackets;

    double m_tmpRtt = 0;
    double m_lastActionRtt = 0;
    double m_stateTransitionTime = 0;
    mutable double m_lastActionTime = 0;

    std::size_t m_inflightDataSize = 0;
    std::size_t m_cwnd;

    int m_tmpRttCount = 0;
    std::uint32_t m_lossCount = 0;
    std::uint32_t m_lastSentSeq = 0;
    std::uint32_t m_tickCount = 0;

};

} // namespace tgvoip

#endif // LIBTGVOIP_CONGESTIONCONTROL_H
