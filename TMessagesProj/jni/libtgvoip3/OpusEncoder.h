//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_OPUSENCODER_H
#define LIBTGVOIP_OPUSENCODER_H

#include "threading.h"
#include "utils.h"
#include "BlockingQueue.h"
#include "Buffers.h"
#include "EchoCanceller.h"
#include "MediaStreamItf.h"

#include <atomic>
#include <cstdint>
#include <vector>

struct OpusEncoder;

namespace tgvoip
{

class OpusEncoder
{
public:
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(OpusEncoder);
    OpusEncoder(MediaStreamItf* m_source, bool needSecondary);
    virtual ~OpusEncoder();
    virtual void Start();
    virtual void Stop();

    using CallbackType = std::function<void(std::uint8_t* data, std::size_t length, std::uint8_t* secondaryData, std::size_t secondaryLength)>;
    void SetCallback(CallbackType callback);

    void SetBitrate(std::uint32_t bitrate);
    void SetEchoCanceller(EchoCanceller* aec);
    void SetOutputFrameDuration(std::uint32_t duration);
    void SetPacketLoss(int percent);
    int GetPacketLoss() const;
    std::uint32_t GetBitrate() const;
    void SetDTX(bool enable);
    void SetLevelMeter(AudioLevelMeter* m_levelMeter);
    void SetSecondaryEncoderEnabled(bool enabled);
    void SetVadMode(bool vad);
    void AddAudioEffect(effects::AudioEffect* effect);
    void RemoveAudioEffect(effects::AudioEffect* effect);
    int GetComplexity() const;

private:
    std::vector<effects::AudioEffect*> m_postProcEffects;
    BlockingQueue<Buffer> m_queue;
    BufferPool<960 * 2, 10> m_bufferPool;

    MediaStreamItf* m_source;
    ::OpusEncoder* m_enc;
    ::OpusEncoder* m_secondaryEncoder;
    EchoCanceller* m_echoCanceller;
    AudioLevelMeter* m_levelMeter;
    Thread* m_thread;

    CallbackType m_callback;

    std::atomic<std::uint32_t> m_requestedBitrate;
    std::uint32_t m_currentBitrate;
    std::uint32_t m_frameDuration;
    std::uint32_t m_vadNoVoiceBitrate;
    std::atomic<int> m_complexity;
    int m_packetLossPercent;
    int m_secondaryEnabledBandwidth;
    int m_vadModeVoiceBandwidth;
    int m_vadModeNoVoiceBandwidth;

    unsigned char m_buffer[4096];

    std::atomic<bool> m_running;
    std::atomic<bool> m_secondaryEncoderEnabled;
    bool m_wasSecondaryEncoderEnabled = false;
    bool m_vadMode = false;

    static std::size_t Callback(std::uint8_t* data, std::size_t len, void* param);
    void RunThread();
    void Encode(std::int16_t* data, std::size_t len);
    void InvokeCallback(std::uint8_t* data, std::size_t length, std::uint8_t* secondaryData, std::size_t secondaryLength);
};

} // namespace tgvoip

#endif // LIBTGVOIP_OPUSENCODER_H
