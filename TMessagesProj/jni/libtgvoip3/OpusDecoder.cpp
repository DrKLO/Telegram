//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "OpusDecoder.h"
#include "audio/Resampler.h"

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstring>
#if defined HAVE_CONFIG_H || defined TGVOIP_USE_INSTALLED_OPUS
#include <opus/opus.h>
#else
#include "opus.h"
#endif

#include "VoIPController.h"

#define PACKET_SIZE (960 * 2)

using namespace tgvoip;

tgvoip::OpusDecoder::OpusDecoder(MediaStreamItf* dst, bool isAsync, bool needEC)
{
    dst->SetCallback(OpusDecoder::Callback, this);
    Initialize(isAsync, needEC);
}

void tgvoip::OpusDecoder::Initialize(bool isAsync, bool needEC)
{
    m_async = isAsync;
    if (m_async)
    {
        m_decodedQueue = new BlockingQueue<Buffer>(33);
        m_semaphore = new Semaphore(32, 0);
    }
    else
    {
        m_decodedQueue = nullptr;
        m_semaphore = nullptr;
    }
    m_dec = opus_decoder_create(48000, 1, nullptr);
    if (needEC)
        m_ecDec = opus_decoder_create(48000, 1, nullptr);
    else
        m_ecDec = nullptr;
    m_buffer=(unsigned char *) malloc(8192);
    m_lastDecoded = nullptr;
    m_outputBufferSize = 0;
    m_echoCanceller = nullptr;
    m_frameDuration = 20;
    m_consecutiveLostPackets = 0;
    m_enableDTX = false;
    m_silentPacketCount = 0;
    m_levelMeter = nullptr;
    m_nextLen = 0;
    m_running = false;
    m_remainingDataLen = 0;
    m_processedBuffer = nullptr;
    m_prevWasEC = false;
    m_prevLastSample = 0;
}

tgvoip::OpusDecoder::~OpusDecoder()
{
    opus_decoder_destroy(m_dec);
    if (m_ecDec)
        opus_decoder_destroy(m_ecDec);
    std::free(m_buffer);
    delete m_decodedQueue;
    delete m_semaphore;
}

void tgvoip::OpusDecoder::SetEchoCanceller(EchoCanceller* canceller)
{
    m_echoCanceller = canceller;
}

std::size_t tgvoip::OpusDecoder::Callback(std::uint8_t* data, std::size_t len, void* param)
{
    return (reinterpret_cast<OpusDecoder*>(param)->HandleCallback(data, len));
}

std::size_t tgvoip::OpusDecoder::HandleCallback(std::uint8_t* data, std::size_t len)
{
    if (m_async)
    {
        if (!m_running)
        {
            std::memset(data, 0, len);
            return 0;
        }
        if (m_outputBufferSize == 0)
        {
            m_outputBufferSize = len;
            int packetsNeeded;
            if (len > PACKET_SIZE)
                packetsNeeded = static_cast<int>(len) / PACKET_SIZE;
            else
                packetsNeeded = 1;
            packetsNeeded *= 2;
            m_semaphore->Release(packetsNeeded);
        }
        assert(m_outputBufferSize == len && "output buffer size is supposed to be the same throughout callbacks");
        if (len == PACKET_SIZE)
        {
            Buffer lastDecoded = m_decodedQueue->GetBlocking();
            if (lastDecoded.IsEmpty())
                return 0;
            std::memcpy(data, *lastDecoded, PACKET_SIZE);
            m_semaphore->Release();
            if (m_silentPacketCount > 0)
            {
                --m_silentPacketCount;
                if (m_levelMeter)
                    m_levelMeter->Update(reinterpret_cast<std::int16_t*>(data), 0);
                return 0;
            }
            if (m_echoCanceller)
            {
                m_echoCanceller->SpeakerOutCallback(data, PACKET_SIZE);
            }
        }
        else
        {
            LOGE("Opus decoder buffer length != 960 samples");
            std::abort();
        }
    }
    else
    {
        if (m_remainingDataLen == 0 && m_silentPacketCount == 0)
        {
            int duration = DecodeNextFrame();
            m_remainingDataLen = static_cast<std::size_t>(duration) / 20 * 960 * 2;
        }
        if (m_silentPacketCount > 0 || m_remainingDataLen == 0 || m_processedBuffer == nullptr)
        {
            if (m_silentPacketCount > 0)
                m_silentPacketCount--;
            std::memset(data, 0, 960 * 2);
            if (m_levelMeter)
                m_levelMeter->Update(reinterpret_cast<std::int16_t*>(data), 0);
            return 0;
        }
        std::memcpy(data, m_processedBuffer, 960 * 2);
        m_remainingDataLen -= 960 * 2;
        if (m_remainingDataLen > 0)
        {
            std::memmove(m_processedBuffer, m_processedBuffer + 960 * 2, static_cast<std::size_t>(m_remainingDataLen));
        }
    }
    if (m_levelMeter != nullptr)
        m_levelMeter->Update(reinterpret_cast<std::int16_t*>(data), len / 2);
    return len;
}

void tgvoip::OpusDecoder::Start()
{
    if (!m_async)
        return;
    m_running = true;
    m_thread = new Thread(std::bind(&tgvoip::OpusDecoder::RunThread, this));
    m_thread->SetName("opus_decoder");
    m_thread->SetMaxPriority();
    m_thread->Start();
}

void tgvoip::OpusDecoder::Stop()
{
    if (!m_running || !m_async)
        return;
    m_running = false;
    m_semaphore->Release();
    m_thread->Join();
    delete m_thread;
}

void tgvoip::OpusDecoder::RunThread()
{
    LOGI("decoder: packets per frame %d", m_packetsPerFrame);
    while (m_running)
    {
        int playbackDuration = DecodeNextFrame();
        for (int i = 0; i < playbackDuration / 20; ++i)
        {
            m_semaphore->Acquire();
            if (!m_running)
            {
                LOGI("==== decoder exiting ====");
                return;
            }
            try
            {
                Buffer buf = m_bufferPool.Get();
                if (m_remainingDataLen > 0)
                {
                    for (effects::AudioEffect*& effect : m_postProcEffects)
                    {
                        effect->Process(reinterpret_cast<std::int16_t*>(m_processedBuffer + (PACKET_SIZE * i)), 960);
                    }
                    buf.CopyFrom(m_processedBuffer + (PACKET_SIZE * i), 0, PACKET_SIZE);
                }
                else
                {
                    std::memset(*buf, 0, PACKET_SIZE);
                }
                m_decodedQueue->Put(std::move(buf));
            }
            catch (const std::bad_alloc& exception)
            {
                LOGW("decoder: no buffers left!\nwhat():\n%s", exception.what());
            }
        }
    }
}

int tgvoip::OpusDecoder::DecodeNextFrame()
{
    int playbackDuration = 0;
    bool isEC = false;
    std::size_t len = m_jitterBuffer->HandleOutput(m_buffer, 8192, 0, true, playbackDuration, isEC);
    bool fec = false;
    if (len == 0)
    {
        fec = true;
        len = m_jitterBuffer->HandleOutput(m_buffer, 8192, 0, false, playbackDuration, isEC);
    }
    int size;
    if (len != 0)
    {
        size = opus_decode(isEC ? m_ecDec : m_dec, m_buffer, len, reinterpret_cast<opus_int16*>(m_decodeBuffer), m_packetsPerFrame * 960, fec ? 1 : 0);
        m_consecutiveLostPackets = 0;
        if (m_prevWasEC != isEC && size)
        {
            // It turns out the waveforms generated by the PLC feature are also great to help smooth out the
            // otherwise audible transition between the frames from different decoders. Those are basically an extrapolation
            // of the previous successfully decoded data -- which is exactly what we need here.
            size = opus_decode(m_prevWasEC ? m_ecDec : m_dec, nullptr, 0, reinterpret_cast<opus_int16*>(m_nextBuffer), m_packetsPerFrame * 960, 0);
            if (size != 0)
            {
                std::int16_t* plcSamples = reinterpret_cast<std::int16_t*>(m_nextBuffer);
                std::int16_t* samples = reinterpret_cast<std::int16_t*>(m_decodeBuffer);
                constexpr float coeffs[] = { 0.999802f, 0.995062f, 0.984031f, 0.966778f, 0.943413f, 0.914084f, 0.878975f, 0.838309f, 0.792344f, 0.741368f,
                                             0.685706f, 0.625708f, 0.561754f, 0.494249f, 0.423619f, 0.350311f, 0.274788f, 0.197527f, 0.119018f, 0.039757f };
                for (int i = 0; i < 20; ++i)
                {
                    samples[i] = static_cast<std::int16_t>(std::round(plcSamples[i] * coeffs[i] + samples[i] * (1.f - coeffs[i])));
                }
            }
        }
        m_prevWasEC = isEC;
        m_prevLastSample = m_decodeBuffer[size - 1];
    }
    else
    { // do packet loss concealment
        m_consecutiveLostPackets++;
        if (m_consecutiveLostPackets > 2 && m_enableDTX)
        {
            m_silentPacketCount += m_packetsPerFrame;
            size = static_cast<int>(m_packetsPerFrame) * 960;
        }
        else
        {
            size = opus_decode(m_prevWasEC ? m_ecDec : m_dec, nullptr, 0, reinterpret_cast<opus_int16*>(m_decodeBuffer), m_packetsPerFrame * 960, 0);
        }
    }
    if (size < 0)
        LOGW("decoder: opus_decode error %d", size);
    m_remainingDataLen = size;
    if (playbackDuration == 80)
    {
        m_processedBuffer = m_buffer;
        audio::Resampler::Rescale60To80(reinterpret_cast<std::int16_t*>(m_decodeBuffer),
            reinterpret_cast<std::int16_t*>(m_processedBuffer));
    }
    else if (playbackDuration == 40)
    {
        m_processedBuffer = m_buffer;
        audio::Resampler::Rescale60To40(reinterpret_cast<std::int16_t*>(m_decodeBuffer),
            reinterpret_cast<std::int16_t*>(m_processedBuffer));
    }
    else
    {
        m_processedBuffer = m_decodeBuffer;
    }
    return playbackDuration;
}

void tgvoip::OpusDecoder::SetFrameDuration(std::uint32_t duration)
{
    m_frameDuration = duration;
    m_packetsPerFrame = m_frameDuration / 20;
}

void tgvoip::OpusDecoder::SetJitterBuffer(std::shared_ptr<JitterBuffer> jitterBuffer)
{
    m_jitterBuffer = std::move(jitterBuffer);
}

void tgvoip::OpusDecoder::SetDTX(bool enable)
{
    m_enableDTX = enable;
}

void tgvoip::OpusDecoder::SetLevelMeter(AudioLevelMeter* levelMeter)
{
    m_levelMeter = levelMeter;
}

void tgvoip::OpusDecoder::AddAudioEffect(effects::AudioEffect* effect)
{
    m_postProcEffects.emplace_back(effect);
}

void tgvoip::OpusDecoder::RemoveAudioEffect(effects::AudioEffect* effect)
{
    auto it = std::find(m_postProcEffects.begin(), m_postProcEffects.end(), effect);
    if (it != m_postProcEffects.end())
        m_postProcEffects.erase(it);
}
