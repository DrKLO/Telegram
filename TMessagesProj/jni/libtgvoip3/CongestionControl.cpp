//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "CongestionControl.h"
#include "PrivateDefines.h"
#include "VoIPController.h"
#include "VoIPServerConfig.h"

#include <cassert>
#include <cstring>
#include <limits>

using namespace tgvoip;

CongestionControl::CongestionControl()
    : m_cwnd(static_cast<std::size_t>(ServerConfig::GetSharedInstance()->GetInt("audio_congestion_window", 1024)))
{
    std::memset(m_inflightPackets.data(), 0, STD_ARRAY_SIZEOF(m_inflightPackets));
}

CongestionControl::~CongestionControl() = default;

std::size_t CongestionControl::GetAcknowledgedDataSize() const
{
    return 0;
}

double CongestionControl::GetAverageRTT() const
{
    return m_rttHistory.NonZeroAverage();
}

std::size_t CongestionControl::GetInflightDataSize() const
{
    return m_inflightHistory.Average();
}

std::size_t CongestionControl::GetCongestionWindow() const
{
    return m_cwnd;
}

double CongestionControl::GetMinimumRTT() const
{
    return m_rttHistory.Min();
}

void CongestionControl::PacketAcknowledged(std::uint32_t seq)
{
    for (tgvoip_congestionctl_packet_t& m_inflightPacket : m_inflightPackets)
    {
        if (m_inflightPacket.seq == seq && m_inflightPacket.sendTime > 0)
        {
            m_tmpRtt += (VoIPController::GetCurrentTime() - m_inflightPacket.sendTime);
            ++m_tmpRttCount;
            m_inflightPacket.sendTime = 0;
            m_inflightDataSize -= m_inflightPacket.size;
            break;
        }
    }
}

void CongestionControl::PacketSent(std::uint32_t seq, std::size_t size)
{
    if (!seqgt(seq, m_lastSentSeq) || seq == m_lastSentSeq)
    {
        LOGW("Duplicate outgoing seq %u", seq);
        return;
    }
    m_lastSentSeq = seq;
    double smallestSendTime = std::numeric_limits<double>::infinity();
    tgvoip_congestionctl_packet_t* slot = nullptr;
    for (tgvoip_congestionctl_packet_t& m_inflightPacket : m_inflightPackets)
    {
        if (m_inflightPacket.sendTime == 0)
        {
            slot = &m_inflightPacket;
            break;
        }
        if (smallestSendTime > m_inflightPacket.sendTime)
        {
            slot = &m_inflightPacket;
            smallestSendTime = slot->sendTime;
        }
    }
    assert(slot != nullptr);
    if (slot->sendTime > 0)
    {
        m_inflightDataSize -= slot->size;
        ++m_lossCount;
        LOGD("Packet with seq %u was not acknowledged", slot->seq);
    }
    slot->seq = seq;
    slot->size = size;
    slot->sendTime = VoIPController::GetCurrentTime();
    m_inflightDataSize += size;
}

void CongestionControl::PacketLost(std::uint32_t seq)
{
    for (tgvoip_congestionctl_packet_t& m_inflightPacket : m_inflightPackets)
    {
        if (m_inflightPacket.seq == seq && m_inflightPacket.sendTime > 0)
        {
            m_inflightPacket.sendTime = 0;
            m_inflightDataSize -= m_inflightPacket.size;
            ++m_lossCount;
            break;
        }
    }
}

void CongestionControl::Tick()
{
    ++m_tickCount;
    if (m_tmpRttCount > 0)
    {
        m_rttHistory.Add(m_tmpRtt / m_tmpRttCount);
        m_tmpRtt = 0;
        m_tmpRttCount = 0;
    }
    for (tgvoip_congestionctl_packet_t& m_inflightPacket : m_inflightPackets)
    {
        if (m_inflightPacket.sendTime != 0 && VoIPController::GetCurrentTime() - m_inflightPacket.sendTime > 2)
        {
            m_inflightPacket.sendTime = 0;
            m_inflightDataSize -= m_inflightPacket.size;
            ++m_lossCount;
            LOGD("Packet with seq %u was not acknowledged", m_inflightPacket.seq);
        }
    }
    m_inflightHistory.Add(m_inflightDataSize);
}

ConctlAct CongestionControl::GetBandwidthControlAction() const
{
    if (VoIPController::GetCurrentTime() - m_lastActionTime < 1)
        return ConctlAct::NONE;
    std::size_t inflightAvg = GetInflightDataSize();
    std::size_t max = m_cwnd + m_cwnd / 10;
    std::size_t min = m_cwnd - m_cwnd / 10;
    if (inflightAvg < min)
    {
        m_lastActionTime = VoIPController::GetCurrentTime();
        return ConctlAct::INCREASE;
    }
    if (inflightAvg > max)
    {
        m_lastActionTime = VoIPController::GetCurrentTime();
        return ConctlAct::DECREASE;
    }
    return ConctlAct::NONE;
}

std::uint32_t CongestionControl::GetSendLossCount() const
{
    return m_lossCount;
}
