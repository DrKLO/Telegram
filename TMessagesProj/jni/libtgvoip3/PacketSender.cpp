#include "PacketSender.h"

using namespace tgvoip;

PacketSender::PacketSender(VoIPController* controller)
    : m_controller(controller)
{
}

PacketSender::~PacketSender() = default;

void PacketSender::SendExtra(Buffer& data, ExtraType type) const
{
    m_controller->SendExtra(data, type);
}

void PacketSender::IncrementUnsentStreamPackets()
{
    ++m_controller->m_unsentStreamPackets;
}

std::uint32_t PacketSender::SendPacket(VoIPController::PendingOutgoingPacket pkt)
{
    std::uint32_t seq = m_controller->GenerateOutSeq();
    pkt.seq = seq;
    m_controller->SendOrEnqueuePacket(std::move(pkt), true, this);
    return seq;
}

double PacketSender::GetConnectionInitTime() const
{
    return m_controller->m_connectionInitTime;
}

const HistoricBuffer<double, 32>& PacketSender::RTTHistory() const
{
    return m_controller->m_RTTHistory;
}

MessageThread& PacketSender::GetMessageThread()
{
    return m_controller->m_messageThread;
}

const MessageThread& PacketSender::GetMessageThread() const
{
    return m_controller->m_messageThread;
}

const VoIPController::ProtocolInfo& PacketSender::GetProtocolInfo() const
{
    return m_controller->m_protocolInfo;
}

void PacketSender::SendStreamFlags(VoIPController::Stream& stm) const
{
    m_controller->SendStreamFlags(stm);
}

const VoIPController::Config& PacketSender::GetConfig() const
{
    return m_controller->m_config;
}
