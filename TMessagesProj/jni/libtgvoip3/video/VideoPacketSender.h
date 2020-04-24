//
// Created by Grishka on 19/03/2019.
//

#ifndef LIBTGVOIP_VIDEOPACKETSENDER_H
#define LIBTGVOIP_VIDEOPACKETSENDER_H

#include "../threading.h"
#include "../Buffers.h"
#include "../PacketSender.h"

#include <memory>
#include <cstdint>
#include <vector>

namespace tgvoip
{

namespace video
{

class VideoSource;

class VideoPacketSender : public PacketSender
{
public:
    VideoPacketSender(VoIPController* m_controller, VideoSource* videoSource, std::shared_ptr<VoIPController::Stream> stream);
    ~VideoPacketSender() override;
    void PacketAcknowledged(std::uint32_t seq, double sendTime, double ackTime, PktType type, std::uint32_t size) override;
    void PacketLost(std::uint32_t seq, PktType type, std::uint32_t size) override;
    void SetSource(VideoSource* m_source);
    [[nodiscard]] std::uint32_t GetBitrate() const;

private:
    struct SentVideoFrame
    {
        std::vector<std::uint32_t> unacknowledgedPackets;
        std::uint32_t seq;
        std::uint32_t fragmentCount;
        std::uint32_t fragmentsInQueue;
    };

    struct QueuedPacket
    {
        VoIPController::PendingOutgoingPacket packet;
        std::uint32_t seq;
    };

    video::ScreamCongestionController m_videoCongestionControl;
    std::vector<SentVideoFrame> m_sentVideoFrames;
    std::vector<Buffer> m_packetsForFEC;

    std::shared_ptr<VoIPController::Stream> m_stm;
    VideoSource* m_source = nullptr;

    std::size_t m_fecFrameCount = 0;

    double m_firstVideoFrameTime = 0.0;
    double m_lastVideoResolutionChangeTime = 0.0;
    double m_sourceChangeTime = 0.0;

    std::uint32_t m_videoFrameCount = 0;
    std::uint32_t m_sendVideoPacketID = MessageThread::INVALID_ID;
    std::uint32_t m_videoPacketLossCount = 0;
    std::uint32_t m_currentVideoBitrate = 0;
    std::uint32_t m_frameSeq = 0;

    bool m_videoKeyframeRequested = false;

    void SendFrame(const Buffer& frame, std::uint32_t flags, std::uint32_t rotation);
    InitVideoRes GetVideoResolutionForCurrentBitrate();
};

} // namespace video

} // namespace tgvoip

#endif // LIBTGVOIP_VIDEOPACKETSENDER_H
