//
// Created by Grishka on 19.03.2018.
//

#include "logging.h"
#include "PacketReassembler.h"
#include "PrivateDefines.h"
#include "video/VideoFEC.h"

#include <cassert>
#include <sstream>

#define NUM_OLD_PACKETS 3
#define NUM_FEC_PACKETS 10

using namespace tgvoip;
using namespace tgvoip::video;

PacketReassembler::PacketReassembler() = default;

PacketReassembler::~PacketReassembler() = default;

void PacketReassembler::Reset()
{
}

void PacketReassembler::AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount, std::uint32_t pts, std::uint8_t _fseq, bool keyframe, std::uint16_t rotation)
{
    for (std::unique_ptr<Packet>& packet : m_packets)
    {
        if (packet->timestamp == pts)
        {
            if (fragmentCount != packet->partCount)
            {
                LOGE("Received fragment total count %u inconsistent with previous %u", fragmentCount, packet->partCount);
                return;
            }
            if (fragmentIndex >= packet->partCount)
            {
                LOGE("Received fragment index %u is greater than total %u", fragmentIndex, fragmentCount);
                return;
            }
            packet->AddFragment(std::move(pkt), fragmentIndex);
            return;
        }
    }
    std::uint32_t fseq = (m_lastFrameSeq & 0xFFFFFF00) | static_cast<std::uint32_t>(_fseq);
    if (static_cast<std::uint8_t>(m_lastFrameSeq) > _fseq)
        fseq += 256;

    if (m_lastFrameSeq > 3 && fseq < m_lastFrameSeq - 3)
    {
        LOGW("Packet too late (fseq=%u, lastFseq=%u)", fseq, m_lastFrameSeq);
        return;
    }
    if (fragmentIndex >= fragmentCount)
    {
        LOGE("Received fragment index %u is out of bounds %u", fragmentIndex, fragmentCount);
        return;
    }
    if (fragmentCount > 255)
    {
        LOGE("Received fragment total count too big %u", fragmentCount);
        return;
    }

    m_maxTimestamp = std::max(m_maxTimestamp, pts);

    m_packets.emplace_back(std::make_unique<Packet>(fseq, pts, fragmentCount, 0, keyframe, rotation));
    m_packets[m_packets.size() - 1]->AddFragment(std::move(pkt), fragmentIndex);
    while (m_packets.size() > 3)
    {
        std::unique_ptr<Packet>& _old = m_packets[0];
        if (_old->receivedPartCount == _old->partCount)
        {
            std::unique_ptr<Packet> old = std::move(m_packets[0]);
            m_packets.erase(m_packets.begin());

            Buffer buffer = old->Reassemble();
            m_callback(std::move(buffer), old->seq, old->isKeyframe, old->rotation);
            m_oldPackets.emplace_back(std::move(old));
            while (m_oldPackets.size() > NUM_OLD_PACKETS)
                m_oldPackets.erase(m_oldPackets.begin());
        }
        else
        {
            LOGW("Packet %u not reassembled (%u of %u)", m_packets[0]->seq, m_packets[0]->receivedPartCount, m_packets[0]->partCount);
            if (m_packets[0]->partCount - m_packets[0]->receivedPartCount == 1 && !m_waitingForFEC)
            {
                bool found = false;
                for (FecPacket& fec : m_fecPackets)
                {
                    if (m_packets[0]->seq <= fec.seq && m_packets[0]->seq > fec.seq - fec.prevFrameCount)
                    {
                        LOGI("Found FEC packet: %u %u", fec.seq, fec.prevFrameCount);
                        found = true;
                        TryDecodeFEC(fec);
                        m_packets.erase(m_packets.begin());
                        break;
                    }
                }
                if (!found)
                {
                    m_waitingForFEC = true;
                    break;
                }
            }
            else
            {
                m_waitingForFEC = false;
                LOGE("unrecoverable packet loss");
                std::unique_ptr<Packet> old = std::move(m_packets[0]);
                m_packets.erase(m_packets.begin());
                m_oldPackets.emplace_back(std::move(old));
                while (m_oldPackets.size() > NUM_OLD_PACKETS)
                    m_oldPackets.erase(m_oldPackets.begin());
            }
        }
    }

    m_lastFrameSeq = fseq;
}

void PacketReassembler::AddFEC(Buffer data, std::uint8_t _fseq, unsigned int frameCount, unsigned int fecScheme)
{
    std::uint32_t fseq = (m_lastFrameSeq & 0xFFFFFF00) | static_cast<std::uint32_t>(_fseq);
    std::ostringstream _s;
    for (unsigned int i = 0; i < frameCount; ++i)
    {
        _s << (fseq - i);
        _s << ' ';
    }

    FecPacket fec
    {
        fseq,
        frameCount,
        fecScheme,
        std::move(data)
    };

    if (m_waitingForFEC)
    {
        if (m_packets[0]->seq <= fec.seq && m_packets[0]->seq > fec.seq - fec.prevFrameCount)
        {
            LOGI("Found FEC packet: %u %u", fec.seq, fec.prevFrameCount);
            TryDecodeFEC(fec);
            m_packets.erase(m_packets.begin());
            m_waitingForFEC = false;
        }
    }
    m_fecPackets.emplace_back(std::move(fec));
    while (m_fecPackets.size() > NUM_FEC_PACKETS)
        m_fecPackets.erase(m_fecPackets.begin());
}

void PacketReassembler::SetCallback(std::function<void(Buffer packet, std::uint32_t pts, bool keyframe, std::uint16_t rotation)> callback)
{
    m_callback = std::move(callback);
}

bool PacketReassembler::TryDecodeFEC(PacketReassembler::FecPacket& fec)
{
  /*  LOGI("Decoding FEC");

    std::vector<Buffer> packetsForRecovery;
    for (std::unique_ptr<Packet>& p : m_oldPackets)
    {
        if (p->seq <= fec.seq && p->seq > fec.seq - fec.prevFrameCount)
        {
            LOGD("Adding frame %u from old", p->seq);
            for (std::uint32_t i = 0; i < p->partCount; ++i)
            {
                packetsForRecovery.emplace_back(i < p->parts.size() ? Buffer::CopyOf(p->parts[i]) : Buffer());
            }
        }
    }

    for (std::unique_ptr<Packet>& p : m_packets)
    {
        if (p->seq <= fec.seq && p->seq > fec.seq - fec.prevFrameCount)
        {
            LOGD("Adding frame %u from pending", p->seq);
            for (std::uint32_t i = 0; i < p->partCount; ++i)
            {
                packetsForRecovery.emplace_back(i < p->parts.size() ? Buffer::CopyOf(p->parts[i]) : Buffer());
            }
        }
    }

    if (fec.fecScheme == FEC_SCHEME_XOR)
    {
        Buffer recovered = ParityFEC::Decode(packetsForRecovery, fec.data);
        LOGI("Recovered packet size %u", static_cast<unsigned>(recovered.Length()));
        if (!recovered.IsEmpty())
        {
            std::unique_ptr<Packet>& pkt = m_packets[0];
            if (pkt->parts.size() < pkt->partCount)
            {
                pkt->parts.emplace_back(std::move(recovered));
            }
            else
            {
                for (Buffer& b : pkt->parts)
                {
                    if (b.IsEmpty())
                    {
                        b = std::move(recovered);
                        break;
                    }
                }
            }
            ++pkt->receivedPartCount;
            m_callback(pkt->Reassemble(), pkt->seq, pkt->isKeyframe, pkt->rotation);
        }
    }
*/
    return false;
}

#pragma mark - Packet

PacketReassembler::Packet::Packet(std::uint32_t seq, std::uint32_t timestamp, std::uint32_t partCount,
                                  std::uint32_t receivedPartCount, bool keyframe, std::uint16_t rotation)
   : seq(seq)
   , timestamp(timestamp)
   , partCount(partCount)
   , receivedPartCount(receivedPartCount)
   , isKeyframe(keyframe)
   , rotation(rotation)
{
}

void PacketReassembler::Packet::AddFragment(Buffer pkt, std::uint32_t fragmentIndex)
{
    if (parts.size() == fragmentIndex)
    {
        parts.emplace_back(std::move(pkt));
    }
    else if (parts.size() > fragmentIndex)
    {
        assert(parts[fragmentIndex].IsEmpty());
        parts[fragmentIndex] = std::move(pkt);
    }
    else
    {
        parts.resize(fragmentIndex + 1);
        parts[fragmentIndex] = std::move(pkt);
    }
    ++receivedPartCount;
    if (parts.size() < receivedPartCount)
        LOGW("Received %u parts but parts.size is %u", static_cast<unsigned>(receivedPartCount), static_cast<unsigned>(parts.size()));
}

Buffer PacketReassembler::Packet::Reassemble()
{
    assert(partCount == receivedPartCount);
    assert(parts.size() == partCount);
    if (partCount == 1)
    {
        return Buffer::CopyOf(parts[0]);
    }
    BufferOutputStream out(10240);
    for (unsigned int i = 0; i < partCount; ++i)
    {
        out.WriteBytes(parts[i]);
    }
    return Buffer(std::move(out));
}
