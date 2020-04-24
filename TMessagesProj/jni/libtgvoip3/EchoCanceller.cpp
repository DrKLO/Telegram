//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "EchoCanceller.h"
#include "VoIPServerConfig.h"
#include "audio/AudioInput.h"
#include "audio/AudioOutput.h"

#ifndef TGVOIP_NO_DSP
#include "webrtc_dsp/api/audio/audio_frame.h"
#include "webrtc_dsp/modules/audio_processing/include/audio_processing.h"
#endif

#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>

using namespace tgvoip;

EchoCanceller::EchoCanceller(bool enableAEC, bool enableNS, bool enableAGC)
    : m_enableAEC(enableAEC)
    , m_enableAGC(enableAGC)
    , m_enableNS(enableNS)
    , m_isOn(true)
{
#ifndef TGVOIP_NO_DSP
    webrtc::Config extraConfig;
#ifdef TGVOIP_USE_DESKTOP_DSP
    extraConfig.Set(new webrtc::DelayAgnostic(true));
#endif

    m_apm = webrtc::AudioProcessingBuilder().Create(extraConfig);

    webrtc::AudioProcessing::Config config;
    config.echo_canceller.enabled = enableAEC;
#ifndef TGVOIP_USE_DESKTOP_DSP
    config.echo_canceller.mobile_mode = true;
#else
    config.echo_canceller.mobile_mode = false;
#endif
    config.high_pass_filter.enabled = enableAEC;
    config.gain_controller2.enabled = enableAGC;
    m_apm->ApplyConfig(config);

    webrtc::NoiseSuppression::Level nsLevel;
#ifdef __APPLE__
    switch (ServerConfig::GetSharedInstance()->GetInt("webrtc_ns_level_vpio", 0))
    {
#else
    switch (ServerConfig::GetSharedInstance()->GetInt("webrtc_ns_level", 2))
    {
#endif
    case 0:
        nsLevel = webrtc::NoiseSuppression::Level::kLow;
        break;
    case 1:
        nsLevel = webrtc::NoiseSuppression::Level::kModerate;
        break;
    case 3:
        nsLevel = webrtc::NoiseSuppression::Level::kVeryHigh;
        break;
    case 2:
    default:
        nsLevel = webrtc::NoiseSuppression::Level::kHigh;
        break;
    }
    m_apm->noise_suppression()->set_level(nsLevel);
    m_apm->noise_suppression()->Enable(enableNS);
    if (enableAGC)
    {
        m_apm->gain_control()->set_mode(webrtc::GainControl::Mode::kAdaptiveDigital);
        m_apm->gain_control()->set_target_level_dbfs(ServerConfig::GetSharedInstance()->GetInt("webrtc_agc_target_level", 9));
        m_apm->gain_control()->enable_limiter(ServerConfig::GetSharedInstance()->GetBoolean("webrtc_agc_enable_limiter", true));
        m_apm->gain_control()->set_compression_gain_db(ServerConfig::GetSharedInstance()->GetInt("webrtc_agc_compression_gain", 20));
    }
    m_apm->voice_detection()->set_likelihood(webrtc::VoiceDetection::Likelihood::kVeryLowLikelihood);

    m_audioFrame = new webrtc::AudioFrame();
    m_audioFrame->samples_per_channel_ = 480;
    m_audioFrame->sample_rate_hz_ = 48000;
    m_audioFrame->num_channels_ = 1;

    m_farendQueue = new BlockingQueue<Buffer>(11);
    m_running = true;
    m_bufferFarendThread = new Thread(std::bind(&EchoCanceller::RunBufferFarendThread, this));
    m_bufferFarendThread->SetName("VoipECBufferFarEnd");
    m_bufferFarendThread->Start();

#else
    this->enableAEC = this->enableAGC = enableAGC = this->enableNS = enableNS = false;
    isOn = true;
#endif
}

EchoCanceller::~EchoCanceller()
{
#ifndef TGVOIP_NO_DSP
    m_farendQueue->Put(Buffer());
    m_bufferFarendThread->Join();
    delete m_bufferFarendThread;
    delete m_farendQueue;
    delete m_audioFrame;
    delete m_apm;
#endif
}

void EchoCanceller::Start()
{
}

void EchoCanceller::Stop()
{
}

void EchoCanceller::SpeakerOutCallback(std::uint8_t* data, std::size_t len)
{
    if (len != 960 * 2 || !m_enableAEC || !m_isOn)
        return;
#ifndef TGVOIP_NO_DSP
    try
    {
        Buffer buf = m_farendBufferPool.Get();
        buf.CopyFrom(data, 0, 960 * 2);
        m_farendQueue->Put(std::move(buf));
    }
    catch (const std::bad_alloc& exception)
    {
        LOGW("Echo canceller can't keep up with real time.\nwhat():\n%s", exception.what());
    }
#endif
}

#ifndef TGVOIP_NO_DSP
void EchoCanceller::RunBufferFarendThread()
{
    webrtc::AudioFrame frame;
    frame.num_channels_ = 1;
    frame.sample_rate_hz_ = 48000;
    frame.samples_per_channel_ = 480;
    while (m_running)
    {
        Buffer buf = m_farendQueue->GetBlocking();
        if (buf.IsEmpty())
        {
            LOGI("Echo canceller buffer farend thread exiting");
            return;
        }
        std::int16_t* samplesIn = reinterpret_cast<std::int16_t*>(*buf);
        std::memcpy(frame.mutable_data(), samplesIn, 480 * 2);
        m_apm->ProcessReverseStream(&frame);
        std::memcpy(frame.mutable_data(), samplesIn + 480, 480 * 2);
        m_apm->ProcessReverseStream(&frame);
        m_didBufferFarend = true;
    }
}
#endif

void EchoCanceller::Enable(bool enabled)
{
    m_isOn = enabled;
}

void EchoCanceller::ProcessInput(std::int16_t* inOut, std::size_t numSamples, bool& hasVoice)
{
#ifndef TGVOIP_NO_DSP
    if (!m_isOn || (!m_enableAEC && !m_enableAGC && !m_enableNS))
        return;

    int delay = audio::AudioInput::GetEstimatedDelay() + audio::AudioOutput::GetEstimatedDelay();
    assert(numSamples == 960);

    std::memcpy(m_audioFrame->mutable_data(), inOut, 480 * 2);
    if (m_enableAEC)
        m_apm->set_stream_delay_ms(delay);

    m_apm->ProcessStream(m_audioFrame);

    if (m_enableVAD)
        hasVoice = m_apm->voice_detection()->stream_has_voice();
    std::memcpy(inOut, m_audioFrame->data(), 480 * 2);
    std::memcpy(m_audioFrame->mutable_data(), inOut + 480, 480 * 2);
    if (m_enableAEC)
        m_apm->set_stream_delay_ms(delay);

    m_apm->ProcessStream(m_audioFrame);

    if (m_enableVAD)
        hasVoice = hasVoice || m_apm->voice_detection()->stream_has_voice();
    std::memcpy(inOut + 480, m_audioFrame->data(), 480 * 2);
#endif
}

void EchoCanceller::SetAECStrength(int strength)
{
#ifndef TGVOIP_NO_DSP
#endif
}

void EchoCanceller::SetVoiceDetectionEnabled(bool enabled)
{
    m_enableVAD = enabled;
#ifndef TGVOIP_NO_DSP
    m_apm->voice_detection()->Enable(enabled);
#endif
}

using namespace tgvoip::effects;

AudioEffect::~AudioEffect() = default;

void AudioEffect::SetPassThrough(bool passThrough)
{
    m_passThrough = passThrough;
}

bool AudioEffect::GetPassThrough() const
{
    return m_passThrough;
}

Volume::Volume() = default;

Volume::~Volume() = default;

void Volume::Process(std::int16_t* inOut, std::size_t numSamples) const
{
    if (m_level == 1.0f || GetPassThrough())
    {
        return;
    }
    for (std::size_t i = 0; i < numSamples; ++i)
    {
        float sample = inOut[i] * m_multiplier;
        if (sample > 32767.0f)
            inOut[i] = std::numeric_limits<std::int16_t>::max();
        else if (sample < -32768.0f)
            inOut[i] = std::numeric_limits<std::int16_t>::min();
        else
            inOut[i] = static_cast<std::int16_t>(sample);
    }
}

void Volume::SetLevel(float level)
{
    m_level = level;
    float db;
    if (level < 1.0f)
        db = -50.0f * (1.0f - level);
    else if (level > 1.0f && level <= 2.0f)
        db = 10.0f * (level - 1.0f);
    else
        db = 0.0f;
    m_multiplier = expf(db / 20.0f * logf(10.0f));
}

float Volume::GetLevel() const
{
    return m_level;
}
