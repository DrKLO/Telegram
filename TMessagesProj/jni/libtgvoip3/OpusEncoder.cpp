//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "OpusEncoder.h"
#include "VoIPServerConfig.h"

#include <algorithm>
#include <cassert>
#include <cstring>
#if defined HAVE_CONFIG_H || defined TGVOIP_USE_INSTALLED_OPUS
#include <opus/opus.h>
#else
#include "opus.h"
#endif

namespace
{

int serverConfigValueToBandwidth(int config)
{
    switch (config)
    {
    case 0:
        return OPUS_BANDWIDTH_NARROWBAND;
    case 1:
        return OPUS_BANDWIDTH_MEDIUMBAND;
    case 2:
        return OPUS_BANDWIDTH_WIDEBAND;
    case 3:
        return OPUS_BANDWIDTH_SUPERWIDEBAND;
    case 4:
    default:
        return OPUS_BANDWIDTH_FULLBAND;
    }
}

} // namespace

tgvoip::OpusEncoder::OpusEncoder(MediaStreamItf* source, bool needSecondary)
    : m_queue(10)
{
    this->m_source = source;
    source->SetCallback(tgvoip::OpusEncoder::Callback, this);
    m_enc = opus_encoder_create(48000, 1, OPUS_APPLICATION_VOIP, nullptr);
    opus_encoder_ctl(m_enc, OPUS_SET_COMPLEXITY(10));
    opus_encoder_ctl(m_enc, OPUS_SET_PACKET_LOSS_PERC(1));
    opus_encoder_ctl(m_enc, OPUS_SET_INBAND_FEC(1));
    opus_encoder_ctl(m_enc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    opus_encoder_ctl(m_enc, OPUS_SET_BANDWIDTH(OPUS_AUTO));
    m_requestedBitrate = 20000;
    m_currentBitrate = 0;
    m_running = false;
    m_echoCanceller = nullptr;
    m_complexity = 10;
    m_frameDuration = 20;
    m_levelMeter = nullptr;
    m_vadNoVoiceBitrate = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("audio_vad_no_voice_bitrate", 6000));
    m_vadModeVoiceBandwidth = serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_vad_bandwidth", 3));
    m_vadModeNoVoiceBandwidth = serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_vad_no_voice_bandwidth", 0));
    m_secondaryEnabledBandwidth = serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_extra_ec_bandwidth", 2));
    m_secondaryEncoderEnabled = false;

    if (needSecondary)
    {
        m_secondaryEncoder = opus_encoder_create(48000, 1, OPUS_APPLICATION_VOIP, nullptr);
        opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_COMPLEXITY(10));
        opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
        opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_BITRATE(8000));
    }
    else
    {
        m_secondaryEncoder = nullptr;
    }
}

tgvoip::OpusEncoder::~OpusEncoder()
{
    opus_encoder_destroy(m_enc);
    if (m_secondaryEncoder != nullptr)
        opus_encoder_destroy(m_secondaryEncoder);
}

void tgvoip::OpusEncoder::Start()
{
    if (m_running)
        return;
    m_running = true;
    m_thread = new Thread(std::bind(&tgvoip::OpusEncoder::RunThread, this));
    m_thread->SetName("OpusEncoder");
    m_thread->SetMaxPriority();
    m_thread->Start();
}

void tgvoip::OpusEncoder::Stop()
{
    if (!m_running)
        return;
    m_running = false;
    m_queue.Put(Buffer());
    m_thread->Join();
    delete m_thread;
}

void tgvoip::OpusEncoder::SetBitrate(std::uint32_t bitrate)
{
    m_requestedBitrate = bitrate;
}

void tgvoip::OpusEncoder::Encode(std::int16_t* data, std::size_t len)
{
    if (m_requestedBitrate != m_currentBitrate)
    {
        opus_encoder_ctl(m_enc, OPUS_SET_BITRATE(m_requestedBitrate));
        m_currentBitrate = m_requestedBitrate;
        LOGV("opus_encoder: setting bitrate to %u", m_currentBitrate);
    }
    if (m_levelMeter)
        m_levelMeter->Update(data, len);
    if (m_secondaryEncoderEnabled != m_wasSecondaryEncoderEnabled)
    {
        m_wasSecondaryEncoderEnabled = m_secondaryEncoderEnabled;
    }
    std::int32_t r = opus_encode(m_enc, data, static_cast<int>(len), m_buffer, 4096);
    if (r <= 0)
    {
        LOGE("Error encoding: %d", r);
    }
    else if (r == 1)
    {
        LOGW("DTX");
    }
    else if (m_running)
    {
        std::int32_t secondaryLen = 0;
        std::uint8_t secondaryBuffer[128];
        if (m_secondaryEncoderEnabled && m_secondaryEncoder != nullptr)
        {
            secondaryLen = opus_encode(m_secondaryEncoder, data, static_cast<int>(len), secondaryBuffer, sizeof(secondaryBuffer));
        }
        InvokeCallback(m_buffer, static_cast<std::size_t>(r), secondaryBuffer, static_cast<std::size_t>(secondaryLen));
    }
}

std::size_t tgvoip::OpusEncoder::Callback(std::uint8_t* data, std::size_t len, void* param)
{
    assert(len == 960 * 2);
    OpusEncoder* e = reinterpret_cast<OpusEncoder*>(param);
    try
    {
        Buffer buf = e->m_bufferPool.Get();
        buf.CopyFrom(data, 0, 960 * 2);
        e->m_queue.Put(std::move(buf));
    }
    catch (const std::bad_alloc& exception)
    {
        LOGW("opus_encoder: no buffer slots left.\nwhat():\n%s", exception.what());
        if (e->m_complexity > 1)
        {
            --e->m_complexity;
            opus_encoder_ctl(e->m_enc, OPUS_SET_COMPLEXITY(e->m_complexity));
        }
    }
    return 0;
}

std::uint32_t tgvoip::OpusEncoder::GetBitrate() const
{
    return m_requestedBitrate;
}

void tgvoip::OpusEncoder::SetEchoCanceller(EchoCanceller* aec)
{
    m_echoCanceller = aec;
}

void tgvoip::OpusEncoder::RunThread()
{
    std::uint32_t bufferedCount = 0;
    std::uint32_t packetsPerFrame = m_frameDuration / 20;
    LOGV("starting encoder, packets per frame=%d", packetsPerFrame);
    std::int16_t* frame;
    if (packetsPerFrame > 1)
        frame = reinterpret_cast<std::int16_t*>(std::malloc(960 * 2 * packetsPerFrame));
    else
        frame = nullptr;
    bool frameHasVoice = false;
    bool wasVadMode = false;
    while (m_running)
    {
        Buffer _packet = m_queue.GetBlocking();
        if (!_packet.IsEmpty())
        {
            std::int16_t* packet = reinterpret_cast<std::int16_t*>(*_packet);
            bool hasVoice = true;
            if (m_echoCanceller)
                m_echoCanceller->ProcessInput(packet, 960, hasVoice);
            if (!m_postProcEffects.empty())
            {
                for (effects::AudioEffect* effect : m_postProcEffects)
                {
                    effect->Process(packet, 960);
                }
            }
            if (packetsPerFrame == 1)
            {
                Encode(packet, 960);
            }
            else
            {
                std::memcpy(frame + (960 * bufferedCount), packet, 960 * 2);
                frameHasVoice = frameHasVoice || hasVoice;
                bufferedCount++;
                if (bufferedCount == packetsPerFrame)
                {
                    if (m_vadMode)
                    {
                        if (frameHasVoice)
                        {
                            opus_encoder_ctl(m_enc, OPUS_SET_BITRATE(m_currentBitrate));
                            if (m_secondaryEncoder)
                            {
                                opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_BITRATE(m_currentBitrate));
                            }
                        }
                        else
                        {
                            opus_encoder_ctl(m_enc, OPUS_SET_BITRATE(m_vadNoVoiceBitrate));
                            if (m_secondaryEncoder)
                            {
                                opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_BITRATE(m_vadNoVoiceBitrate));
                            }
                        }
                        wasVadMode = true;
                    }
                    else if (wasVadMode)
                    {
                        wasVadMode = false;
                        opus_encoder_ctl(m_enc, OPUS_SET_BITRATE(m_currentBitrate));
                        if (m_secondaryEncoder)
                        {
                            opus_encoder_ctl(m_secondaryEncoder, OPUS_SET_BITRATE(m_currentBitrate));
                        }
                    }
                    Encode(frame, 960 * packetsPerFrame);
                    bufferedCount = 0;
                    frameHasVoice = false;
                }
            }
        }
        else
        {
            break;
        }
    }
    if (frame != nullptr)
        std::free(frame);
}

void tgvoip::OpusEncoder::SetOutputFrameDuration(std::uint32_t duration)
{
    m_frameDuration = duration;
}

void tgvoip::OpusEncoder::SetPacketLoss(int percent)
{
    m_packetLossPercent = std::min(20, percent);
    opus_encoder_ctl(m_enc, OPUS_SET_PACKET_LOSS_PERC(m_packetLossPercent));
    opus_encoder_ctl(m_enc, OPUS_SET_INBAND_FEC(percent > 0 && !m_secondaryEncoderEnabled ? 1 : 0));
}

int tgvoip::OpusEncoder::GetPacketLoss() const
{
    return m_packetLossPercent;
}

void tgvoip::OpusEncoder::SetDTX(bool enable)
{
    opus_encoder_ctl(m_enc, OPUS_SET_DTX(enable ? 1 : 0));
}

void tgvoip::OpusEncoder::SetLevelMeter(tgvoip::AudioLevelMeter* levelMeter)
{
    this->m_levelMeter = levelMeter;
}

void tgvoip::OpusEncoder::SetCallback(std::function<void(std::uint8_t*, std::size_t, std::uint8_t*, std::size_t)> callback)
{
    m_callback = std::move(callback);
}

void tgvoip::OpusEncoder::InvokeCallback(std::uint8_t* data, std::size_t length, std::uint8_t* secondaryData, std::size_t secondaryLength)
{
    m_callback(data, length, secondaryData, secondaryLength);
}

void tgvoip::OpusEncoder::SetSecondaryEncoderEnabled(bool enabled)
{
    m_secondaryEncoderEnabled = enabled;
}

void tgvoip::OpusEncoder::SetVadMode(bool vad)
{
    m_vadMode = vad;
}
void tgvoip::OpusEncoder::AddAudioEffect(effects::AudioEffect* effect)
{
    m_postProcEffects.emplace_back(effect);
}

void tgvoip::OpusEncoder::RemoveAudioEffect(effects::AudioEffect* effect)
{
    auto it = std::find(m_postProcEffects.begin(), m_postProcEffects.end(), effect);
    if (it != m_postProcEffects.end())
        m_postProcEffects.erase(it);
}

int tgvoip::OpusEncoder::GetComplexity() const
{
    return m_complexity;
}
