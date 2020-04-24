#ifndef VOIPGROUPCONTROLLER_H
#define VOIPGROUPCONTROLLER_H

#include "VoIPController.h"

namespace tgvoip
{

class VoIPGroupController : public VoIPController
{
public:
    VoIPGroupController(std::int32_t m_timeDifference);
    ~VoIPGroupController() override;
    void SetGroupCallInfo(std::uint8_t* m_encryptionKey, std::uint8_t* reflectorGroupTag, std::uint8_t* m_reflectorSelfTag,
                          std::uint8_t* m_reflectorSelfSecret, std::uint8_t* m_reflectorSelfTagHash, std::int32_t selfUserID,
                          const NetworkAddress& reflectorAddress, const NetworkAddress& reflectorAddressV6, std::uint16_t reflectorPort);
    void AddGroupCallParticipant(std::int32_t userID, std::uint8_t* memberTagHash, std::uint8_t* serializedStreams, std::size_t streamsLength);
    void RemoveGroupCallParticipant(std::int32_t userID);
    float GetParticipantAudioLevel(std::int32_t userID);
    void SetMicMute(bool mute) override;
    void SetParticipantVolume(std::int32_t userID, float volume);
    void SetParticipantStreams(std::int32_t userID, std::uint8_t* serializedStreams, std::size_t length);
    static std::size_t GetInitialStreams(std::uint8_t* buf, std::size_t size);

    struct Callbacks : public VoIPController::Callbacks
    {
        void (*updateStreams)(VoIPGroupController*, std::uint8_t*, std::size_t);
        void (*participantAudioStateChanged)(VoIPGroupController*, std::int32_t, bool);
    };
    void SetCallbacks(Callbacks m_callbacks);
    std::string GetDebugString() override;
    void SetNetworkType(NetType type) override;

protected:
    void ProcessIncomingPacket(NetworkPacket& packet, Endpoint& srcEndpoint) override;
    void SendInit() override;
    void SendUdpPing(Endpoint& endpoint) override;
    void SendRelayPings() override;
    void SendPacket(std::uint8_t* data, std::size_t len, Endpoint& ep, PendingOutgoingPacket& srcPacket) override;
    void WritePacketHeader(std::uint32_t seq, BufferOutputStream* s, PktType type,
                           std::uint32_t length, PacketSender* source) override;
    void OnAudioOutputReady() override;

private:
    struct GroupCallParticipant
    {
        std::vector<std::shared_ptr<Stream>> streams;
        AudioLevelMeter* levelMeter;
        std::int32_t userID;
        std::uint8_t memberTagHash[32];
    };

    struct PacketIdMapping
    {
        double ackTime;
        std::uint32_t seq;
        std::uint16_t id;
    };

    std::vector<std::shared_ptr<Stream>> DeserializeStreams(BufferInputStream& in);
    std::vector<PacketIdMapping> m_recentSentPackets;
    std::vector<GroupCallParticipant> m_participants;

    Callbacks m_groupCallbacks;
    AudioMixer* m_audioMixer;
    Endpoint m_groupReflector;

    Mutex m_sentPacketsMutex;
    Mutex m_participantsMutex;

    std::int32_t m_timeDifference;
    std::int32_t m_userSelfID;

    AudioLevelMeter m_selfLevelMeter;

    std::uint8_t m_reflectorSelfTag[16];
    std::uint8_t m_reflectorSelfSecret[16];
    std::uint8_t m_reflectorSelfTagHash[32];

    std::int32_t GetCurrentUnixtime();
    void SendRecentPacketsRequest();
    void SendSpecialReflectorRequest(std::uint8_t* data, std::size_t len);
    void SerializeAndUpdateOutgoingStreams();
};

} // namespace tgvoip

#endif // VOIPGROUPCONTROLLER_H
