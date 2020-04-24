//
// Created by Grishka on 19.03.2018.
//

#ifndef TGVOIP_PACKETREASSEMBLER_H
#define TGVOIP_PACKETREASSEMBLER_H

#include "logging.h"
#include "Buffers.h"

#include <functional>
#include <memory>
#include <vector>

namespace tgvoip
{

class PacketReassembler
{
public:
    PacketReassembler();
    virtual ~PacketReassembler();

    void Reset();
    void AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount,
                     std::uint32_t pts, std::uint8_t fseq, bool keyframe, std::uint16_t rotation);
    void AddFEC(Buffer data, std::uint8_t fseq, unsigned int frameCount, unsigned int fecScheme);

    using CallbackType = std::function<void(Buffer packet, std::uint32_t pts, bool keyframe, std::uint16_t rotation)>;
    void SetCallback(CallbackType m_callback);

private:
    struct Packet
    {
        std::uint32_t seq;
        std::uint32_t timestamp;
        std::uint32_t partCount;
        std::uint32_t receivedPartCount;
        bool isKeyframe;
        std::uint16_t rotation;
        std::vector<Buffer> parts;

        Packet(std::uint32_t seq, std::uint32_t timestamp, std::uint32_t partCount,
               std::uint32_t receivedPartCount, bool keyframe, std::uint16_t rotation);

        void AddFragment(Buffer pkt, std::uint32_t fragmentIndex);
        Buffer Reassemble();
    };

    struct FecPacket
    {
        std::uint32_t seq;
        std::uint32_t prevFrameCount;
        std::uint32_t fecScheme;
        Buffer data;
    };

    CallbackType m_callback;
    std::vector<std::unique_ptr<Packet>> m_packets;
    std::vector<std::unique_ptr<Packet>> m_oldPackets; // for FEC
    std::vector<FecPacket> m_fecPackets;
    std::uint32_t m_maxTimestamp = 0;
    std::uint32_t m_lastFrameSeq = 0;
    bool m_waitingForFEC = false;

    bool TryDecodeFEC(FecPacket& fec);
};

} // namespace tgvoip

#endif // TGVOIP_PACKETREASSEMBLER_H
