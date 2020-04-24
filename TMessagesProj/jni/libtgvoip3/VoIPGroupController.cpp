//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "PrivateDefines.h"
#include "VoIPGroupController.h"
#include "VoIPServerConfig.h"

#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstring>

using namespace tgvoip;

VoIPGroupController::VoIPGroupController(std::int32_t timeDifference)
    : m_audioMixer(new AudioMixer())
    , m_timeDifference(timeDifference)
    , m_userSelfID(0)
{
    std::memset(&m_callbacks, 0, sizeof(m_callbacks));
    LOGV("Created VoIPGroupController; timeDifference=%d", timeDifference);
}

VoIPGroupController::~VoIPGroupController()
{
    if (m_audioOutput != nullptr)
    {
        m_audioOutput->Stop();
    }

    LOGD("before stop audio mixer");
    m_audioMixer->Stop();
    delete m_audioMixer;

    for (GroupCallParticipant& participant : m_participants)
    {
        delete participant.levelMeter;
    }
}

void VoIPGroupController::SetGroupCallInfo(std::uint8_t* encryptionKey, std::uint8_t* reflectorGroupTag, std::uint8_t* reflectorSelfTag,
                                           std::uint8_t* reflectorSelfSecret, std::uint8_t* reflectorSelfTagHash, std::int32_t selfUserID,
                                           const NetworkAddress& reflectorAddress, const NetworkAddress& reflectorAddressV6, std::uint16_t reflectorPort)
{
    Endpoint e;
    e.address = reflectorAddress;
    e.v6address = reflectorAddressV6;
    e.port = reflectorPort;
    std::memcpy(e.peerTag, reflectorGroupTag, 16);
    e.type = Endpoint::Type::UDP_RELAY;
    e.id = FOURCC('G', 'R', 'P', 'R');
    m_endpoints[e.id] = e;
    m_groupReflector = e;
    m_currentEndpoint = e.id;

    std::memcpy(m_encryptionKey, encryptionKey, 256);
    std::memcpy(m_reflectorSelfTag, reflectorSelfTag, 16);
    std::memcpy(m_reflectorSelfSecret, reflectorSelfSecret, 16);
    std::memcpy(m_reflectorSelfTagHash, reflectorSelfTagHash, 16);
    std::uint8_t sha256[SHA256_LENGTH];
    crypto.sha256(encryptionKey, 256, sha256);
    std::memcpy(m_callID, sha256 + (SHA256_LENGTH - 16), 16);
    std::memcpy(m_keyFingerprint, sha256 + (SHA256_LENGTH - 16), 8);
    m_userSelfID = selfUserID;
}

void VoIPGroupController::AddGroupCallParticipant(std::int32_t userID, std::uint8_t* memberTagHash, std::uint8_t* serializedStreams, std::size_t streamsLength)
{
    if (userID == m_userSelfID)
        return;
    if (m_userSelfID == 0)
        return;

    MutexGuard m(m_participantsMutex);
    LOGV("Adding group call user %d, streams length %u", userID, static_cast<unsigned int>(streamsLength));

    for (const GroupCallParticipant& participant : m_participants)
    {
        if (participant.userID == userID)
        {
            LOGE("user %d already added", userID);
            std::abort();
        }
    }

    GroupCallParticipant participant;
    participant.userID = userID;
    std::memcpy(participant.memberTagHash, memberTagHash, sizeof(participant.memberTagHash));
    participant.levelMeter = new AudioLevelMeter();

    BufferInputStream ss(serializedStreams, streamsLength);
    std::vector<std::shared_ptr<Stream>> streams = DeserializeStreams(ss);

    std::uint8_t audioStreamID = 0;

    for (std::shared_ptr<Stream>& stream : streams)
    {
        stream->userID = userID;
        if (stream->type == StreamType::AUDIO && stream->codec == CODEC_OPUS && !audioStreamID)
        {
            audioStreamID = stream->id;
            stream->jitterBuffer = std::make_shared<JitterBuffer>(nullptr, stream->frameDuration);
            if (stream->frameDuration > 50)
                stream->jitterBuffer->SetMinPacketCount(static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_60", 2)));
            else if (stream->frameDuration > 30)
                stream->jitterBuffer->SetMinPacketCount(static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_40", 4)));
            else
                stream->jitterBuffer->SetMinPacketCount(static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_20", 6)));
            stream->callbackWrapper = std::make_shared<CallbackWrapper>();
            stream->decoder = std::make_shared<OpusDecoder>(stream->callbackWrapper.get(), false, false);
            stream->decoder->SetJitterBuffer(stream->jitterBuffer);
            stream->decoder->SetFrameDuration(stream->frameDuration);
            stream->decoder->SetDTX(true);
            stream->decoder->SetLevelMeter(participant.levelMeter);
            m_audioMixer->AddInput(stream->callbackWrapper);
        }
        m_incomingStreams.emplace_back(stream);
    }

    if (audioStreamID == 0)
    {
        LOGW("User %d has no usable audio stream", userID);
    }

    participant.streams.insert(participant.streams.end(), streams.begin(), streams.end());
    m_participants.emplace_back(participant);
    LOGI("Added group call participant %d", userID);
}

void VoIPGroupController::RemoveGroupCallParticipant(std::int32_t userID)
{
    MutexGuard m(m_participantsMutex);
    for (auto it = m_incomingStreams.begin(); it != m_incomingStreams.end();)
    {
        if ((*it)->userID == userID)
        {
            LOGI("Removed stream %d belonging to user %d", (*it)->id, userID);
            m_audioMixer->RemoveInput((*it)->callbackWrapper);
            (*it)->decoder->Stop();
            it = m_incomingStreams.erase(it);
            continue;
        }
        ++it;
    }
    for (auto it = m_participants.begin(); it != m_participants.end(); ++it)
    {
        if (it->userID == userID)
        {
            delete it->levelMeter;
            m_participants.erase(it);
            LOGI("Removed group call participant %d", userID);
            break;
        }
    }
}

std::vector<std::shared_ptr<VoIPController::Stream>> VoIPGroupController::DeserializeStreams(BufferInputStream& in)
{
    std::vector<std::shared_ptr<Stream>> res;
    try
    {
        std::uint8_t count = in.ReadUInt8();
        for (int i = 0; i < count; ++i)
        {
            std::uint16_t len = in.ReadUInt16();
            BufferInputStream inner = in.GetPartBuffer(len, true);
            std::shared_ptr<Stream> s = std::make_shared<Stream>();
            s->id = inner.ReadUInt8();
            s->type = static_cast<StreamType>(inner.ReadUInt8());
            s->codec = inner.ReadUInt32();
            std::uint32_t flags = inner.ReadUInt32();
            s->enabled = (flags & STREAM_FLAG_ENABLED) == STREAM_FLAG_ENABLED;
            s->frameDuration = inner.ReadUInt16();
            res.emplace_back(s);
        }
    }
    catch (const std::out_of_range& exception)
    {
        LOGW("Error deserializing streams.\nwhat():\n%s", exception.what());
    }
    return res;
}

void VoIPGroupController::SetParticipantStreams(std::int32_t userID, std::uint8_t* serializedStreams, std::size_t length)
{
    LOGD("Set participant streams for %d", userID);
    MutexGuard m(m_participantsMutex);
    for (const GroupCallParticipant& participant : m_participants)
    {
        if (participant.userID != userID)
            continue;
        BufferInputStream in(serializedStreams, length);
        std::vector<std::shared_ptr<Stream>> streams = DeserializeStreams(in);
        for (const std::shared_ptr<Stream>& ns : streams)
        {
            bool found = false;
            for (const std::shared_ptr<Stream>& s : participant.streams)
            {
                if (s->id == ns->id)
                {
                    s->enabled = ns->enabled;
                    if (m_groupCallbacks.participantAudioStateChanged)
                        m_groupCallbacks.participantAudioStateChanged(this, userID, s->enabled);
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                LOGW("Tried to add stream %d for user %d but adding/removing streams is not supported", ns->id, userID);
            }
        }
        break;
    }
}

std::size_t VoIPGroupController::GetInitialStreams(std::uint8_t* buf, std::size_t size)
{
    BufferOutputStream s(buf, size);
    s.WriteUInt8(1); // streams count

    s.WriteInt16(12); // this object length
    s.WriteUInt8(1); // stream id
    s.WriteUInt8(static_cast<std::uint8_t>(StreamType::AUDIO));
    s.WriteUInt32(CODEC_OPUS);
    s.WriteInt32(STREAM_FLAG_ENABLED | STREAM_FLAG_DTX); // flags
    s.WriteInt16(60); // frame duration

    return s.GetLength();
}

void VoIPGroupController::SendInit()
{
    SendRecentPacketsRequest();
}

void VoIPGroupController::ProcessIncomingPacket(NetworkPacket& packet, Endpoint& srcEndpoint)
{
}

void VoIPGroupController::SendUdpPing(Endpoint& endpoint)
{
}

void VoIPGroupController::SetNetworkType(NetType type)
{
    m_networkType = type;
    UpdateDataSavingState();
    UpdateAudioBitrateLimit();
    std::string itfName = m_udpSocket->GetLocalInterfaceInfo(nullptr, nullptr);
    if (itfName != m_activeNetItfName)
    {
        m_udpSocket->OnActiveInterfaceChanged();
        LOGI("Active network interface changed: %s -> %s", m_activeNetItfName.c_str(), itfName.c_str());
        bool isFirstChange = m_activeNetItfName.length() == 0;
        m_activeNetItfName = itfName;
        if (isFirstChange)
            return;
        m_udpConnectivityState = UdpState::UNKNOWN;
        m_udpPingCount = 0;
        m_lastUdpPingTime = 0;
        if (m_proxyProtocol == Proxy::SOCKS5)
            InitUDPProxy();
        m_selectCanceller->CancelSelect();
    }
}

void VoIPGroupController::SendRecentPacketsRequest()
{
    BufferOutputStream out(1024);
    out.WriteInt32(TLID_UDP_REFLECTOR_REQUEST_PACKETS_INFO); // TL function
    out.WriteInt32(GetCurrentUnixtime()); // date:int
    out.WriteInt64(0); // query_id:long
    out.WriteInt32(64); // recv_num:int
    out.WriteInt32(0); // sent_num:int
    SendSpecialReflectorRequest(out.GetBuffer(), out.GetLength());
}

void VoIPGroupController::SendSpecialReflectorRequest(std::uint8_t* data, std::size_t len)
{
}

void VoIPGroupController::SendRelayPings()
{
    double currentTime = GetCurrentTime();
    if (currentTime - m_groupReflector.m_lastPingTime >= 0.25)
    {
        SendRecentPacketsRequest();
        m_groupReflector.m_lastPingTime = currentTime;
    }
}

void VoIPGroupController::OnAudioOutputReady()
{
    m_encoder->SetDTX(true);
    m_audioMixer->SetOutput(m_audioOutput);
    m_audioMixer->SetEchoCanceller(m_echoCanceller);
    m_audioMixer->Start();
    m_audioOutput->Start();
    m_audioOutStarted = true;
    m_encoder->SetLevelMeter(&m_selfLevelMeter);
}

void VoIPGroupController::WritePacketHeader(std::uint32_t seq, BufferOutputStream* s, PktType type, std::uint32_t length, PacketSender* source)
{
    s->WriteUInt32(TLID_DECRYPTED_AUDIO_BLOCK);
    std::int64_t randomID;
    crypto.rand_bytes(reinterpret_cast<std::uint8_t*>(&randomID), 8);
    s->WriteInt64(randomID);
    std::uint8_t randBytes[7];
    crypto.rand_bytes(randBytes, 7);
    s->WriteUInt8(std::uint8_t{7});
    s->WriteBytes(randBytes, 7);
    std::uint32_t pflags = PFLAG_HAS_SEQ | PFLAG_HAS_SENDER_TAG_HASH;
    if (length > 0)
        pflags |= PFLAG_HAS_DATA;
    pflags |= static_cast<std::uint32_t>(type) << 24;
    s->WriteUInt32(pflags);

    if (type == PktType::STREAM_DATA || type == PktType::STREAM_DATA_X2 || type == PktType::STREAM_DATA_X3)
    {
        m_congestionControl->PacketSent(seq, length);
    }

    s->WriteUInt32(seq);
    s->WriteBytes(m_reflectorSelfTagHash, 16);
    if (length > 0)
    {
        if (length <= 253)
        {
            s->WriteUInt8(static_cast<std::uint8_t>(length));
        }
        else
        {
            s->WriteUInt8(254);
            s->WriteUInt8(static_cast<std::uint8_t>(length & 0xFF));
            s->WriteUInt8(static_cast<std::uint8_t>((length >> 8) & 0xFF));
            s->WriteUInt8(static_cast<std::uint8_t>((length >> 16) & 0xFF));
        }
    }
}

void VoIPGroupController::SendPacket(std::uint8_t* data, std::size_t len, Endpoint& ep, PendingOutgoingPacket& srcPacket)
{
    if (m_stopping)
        return;
    if (ep.type == Endpoint::Type::TCP_RELAY && !m_useTCP)
        return;
    BufferOutputStream out(len + 128);

    out.WriteBytes(m_reflectorSelfTag, 16);

    if (len > 0)
    {
        BufferOutputStream inner(len + 128);
        inner.WriteUInt32(static_cast<std::uint32_t>(len));
        inner.WriteBytes(data, len);
        std::size_t padLen = 16 - inner.GetLength() % 16;
        if (padLen < 12)
            padLen += 16;
        std::uint8_t padding[28];
        crypto.rand_bytes(padding, padLen);
        inner.WriteBytes(padding, padLen);
        assert(inner.GetLength() % 16 == 0);

        std::uint8_t key[32], iv[32], msgKey[16];
        out.WriteBytes(m_keyFingerprint, 8);
        BufferOutputStream buf(len + 32);
        std::size_t x = 0;
        buf.WriteBytes(m_encryptionKey + 88 + x, 32);
        buf.WriteBytes(inner.GetBuffer() + 4, inner.GetLength() - 4);
        std::uint8_t msgKeyLarge[32];
        crypto.sha256(buf.GetBuffer(), buf.GetLength(), msgKeyLarge);
        std::memcpy(msgKey, msgKeyLarge + 8, 16);
        KDF2(msgKey, 0, key, iv);
        out.WriteBytes(msgKey, 16);

        std::vector<std::uint8_t> aesOut(MSC_STACK_FALLBACK(inner.GetLength(), 1500));
        crypto.aes_ige_encrypt(inner.GetBuffer(), aesOut.data(), inner.GetLength(), key, iv);
        out.WriteBytes(aesOut.data(), inner.GetLength());
    }

    // relay signature
    out.WriteBytes(m_reflectorSelfSecret, 16);
    std::uint8_t sig[32];
    crypto.sha256(out.GetBuffer(), out.GetLength(), sig);
    out.Rewind(16);
    out.WriteBytes(sig, 16);

    if (srcPacket.type == PktType::STREAM_DATA || srcPacket.type == PktType::STREAM_DATA_X2 || srcPacket.type == PktType::STREAM_DATA_X3)
    {
        PacketIdMapping mapping =
        {
            .ackTime = 0.0,
            .seq = srcPacket.seq,
            .id = *reinterpret_cast<std::uint16_t*>(sig + 14),
        };
        MutexGuard m(m_sentPacketsMutex);
        m_recentSentPackets.emplace_back(mapping);
        while (m_recentSentPackets.size() > 64)
            m_recentSentPackets.erase(m_recentSentPackets.begin());
    }
    m_lastSentSeq = srcPacket.seq;

    if (IS_MOBILE_NETWORK(m_networkType))
        m_stats.bytesSentMobile += static_cast<std::uint64_t>(out.GetLength());
    else
        m_stats.bytesSentWifi += static_cast<std::uint64_t>(out.GetLength());
}

void VoIPGroupController::SetCallbacks(VoIPGroupController::Callbacks callbacks)
{
    VoIPController::SetCallbacks(static_cast<VoIPController::Callbacks&>(callbacks));
    this->m_groupCallbacks = callbacks;
}

std::int32_t VoIPGroupController::GetCurrentUnixtime()
{
    return static_cast<std::int32_t>(time(nullptr)) + m_timeDifference;
}

float VoIPGroupController::GetParticipantAudioLevel(std::int32_t userID)
{
    if (userID == m_userSelfID)
        return m_selfLevelMeter.GetLevel();
    MutexGuard m(m_participantsMutex);
    for (const GroupCallParticipant& participant : m_participants)
    {
        if (participant.userID == userID)
        {
            return participant.levelMeter->GetLevel();
        }
    }
    return 0;
}

void VoIPGroupController::SetMicMute(bool mute)
{
    m_micMuted = mute;
    if (m_audioInput)
    {
        if (mute)
            m_audioInput->Stop();
        else
            m_audioInput->Start();
        if (!m_audioInput->IsInitialized())
        {
            m_lastError = Error::AUDIO_IO;
            SetState(State::FAILED);
            return;
        }
    }
    m_outgoingStreams[0]->enabled = !mute;
    SerializeAndUpdateOutgoingStreams();
}

void VoIPGroupController::SetParticipantVolume(std::int32_t userID, float volume)
{
    MutexGuard m(m_participantsMutex);
    for (const GroupCallParticipant& participant : m_participants)
    {
        if (participant.userID != userID)
            continue;
        for (const std::shared_ptr<Stream>& stream : participant.streams)
        {
            if (stream->type == StreamType::AUDIO)
            {
                if (stream->decoder != nullptr)
                {
                    float db;
                    if (volume == 0.0f)
                        db = -INFINITY;
                    else if (volume < 1.0f)
                        db = -50.0f * (1.0f - volume);
                    else if (volume > 1.0f && volume <= 2.0f)
                        db = 10.0f * (volume - 1.0f);
                    else
                        db = 0.0f;
                    m_audioMixer->SetInputVolume(stream->callbackWrapper, db);
                }
                break;
            }
        }
        break;
    }
}

void VoIPGroupController::SerializeAndUpdateOutgoingStreams()
{
    BufferOutputStream out(1024);
    out.WriteUInt8(static_cast<std::uint8_t>(m_outgoingStreams.size()));

    for (const std::shared_ptr<Stream>& stream : m_outgoingStreams)
    {
        BufferOutputStream o(128);
        o.WriteUInt8(stream->id);
        o.WriteUInt8(static_cast<std::uint8_t>(stream->type));
        o.WriteUInt32(stream->codec);
        o.WriteInt32(static_cast<std::uint8_t>((stream->enabled ? STREAM_FLAG_ENABLED : 0) | STREAM_FLAG_DTX));
        o.WriteUInt16(stream->frameDuration);
        out.WriteUInt16(static_cast<std::uint16_t>(o.GetLength()));
        out.WriteBytes(o.GetBuffer(), o.GetLength());
    }
    if (m_groupCallbacks.updateStreams)
        m_groupCallbacks.updateStreams(this, out.GetBuffer(), out.GetLength());
}

std::string VoIPGroupController::GetDebugString()
{
    std::string result = "Remote endpoints: \n";
    char buffer[2048];
    for (const auto& [_, endpoint] : m_endpoints)
    {
        const char* type;
        switch (endpoint.type)
        {
        case Endpoint::Type::UDP_P2P_INET:
            type = "UDP_P2P_INET";
            break;
        case Endpoint::Type::UDP_P2P_LAN:
            type = "UDP_P2P_LAN";
            break;
        case Endpoint::Type::UDP_RELAY:
            type = "UDP_RELAY";
            break;
        case Endpoint::Type::TCP_RELAY:
            type = "TCP_RELAY";
            break;
//        default:
//            type = "UNKNOWN";
//            break;
        }
        std::snprintf(buffer, sizeof(buffer), "%s:%u %dms [%s%s]\n", endpoint.address.ToString().c_str(), endpoint.port,
                      static_cast<int>(endpoint.m_averageRTT * 1000), type, m_currentEndpoint == endpoint.id ? ", IN_USE" : "");
        result += buffer;
    }
    double avgLate[3];
    std::shared_ptr<JitterBuffer> jitterBuffer = m_incomingStreams.size() == 1 ? m_incomingStreams[0]->jitterBuffer : nullptr;
    if (jitterBuffer != nullptr)
        jitterBuffer->GetAverageLateCount(avgLate);
    else
        std::fill(std::begin(avgLate), std::end(avgLate), 0);
    std::snprintf(
        buffer,
        sizeof(buffer),
        "RTT avg/min: %d/%d\n"
        "Congestion window: %d/%d bytes\n"
        "Key fingerprint: %02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX\n"
        "Last sent/ack'd seq: %u/%u\n"
        "Send/recv losses: %u/%u (%d%%)\n"
        "Audio bitrate: %d kbit\n"
        "Bytes sent/recvd: %llu/%llu\n\n",
        static_cast<int>(m_congestionControl->GetAverageRTT() * 1000),
        static_cast<int>(m_congestionControl->GetMinimumRTT() * 1000),
        static_cast<int>(m_congestionControl->GetInflightDataSize()),
        static_cast<int>(m_congestionControl->GetCongestionWindow()),
        m_keyFingerprint[0],
        m_keyFingerprint[1],
        m_keyFingerprint[2],
        m_keyFingerprint[3],
        m_keyFingerprint[4],
        m_keyFingerprint[5],
        m_keyFingerprint[6],
        m_keyFingerprint[7],
        m_lastSentSeq,
        m_lastRemoteAckSeq,
        m_congestionControl->GetSendLossCount(),
        m_recvLossCount,
        m_encoder ? m_encoder->GetPacketLoss() : 0,
        m_encoder ? (m_encoder->GetBitrate() / 1000) : 0,
        static_cast<unsigned long long>(m_stats.bytesSentMobile + m_stats.bytesSentWifi),
        static_cast<unsigned long long>(m_stats.bytesRecvdMobile + m_stats.bytesRecvdWifi));

    MutexGuard m(m_participantsMutex);
    for (const GroupCallParticipant& participant : m_participants)
    {
        std::snprintf(buffer, sizeof(buffer), "Participant id: %d\n", participant.userID);
        result += buffer;
        for (const std::shared_ptr<Stream>& stream : participant.streams)
        {
            char* codec = reinterpret_cast<char*>(&stream->codec);
            std::snprintf(buffer, sizeof(buffer), "Stream %d (type %u, codec '%c%c%c%c', %sabled)\n",
                          stream->id, static_cast<std::uint8_t>(stream->type), codec[3], codec[2], codec[1], codec[0], stream->enabled ? "en" : "dis");
            result += buffer;
            if (stream->enabled)
            {
                if (stream->jitterBuffer != nullptr)
                {
                    std::snprintf(buffer, sizeof(buffer), "Jitter buffer: %d/%.2f\n",
                                  stream->jitterBuffer->GetMinPacketCount(), stream->jitterBuffer->GetAverageDelay());
                    result += buffer;
                }
            }
        }
        result += "\n";
    }
    return result;
}
