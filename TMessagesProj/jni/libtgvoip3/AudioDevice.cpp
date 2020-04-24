#include "logging.h"
#include "AudioDevice.h"

using namespace tgvoip;

AudioInputTester::AudioInputTester(std::string deviceID)
    : m_deviceID(std::move(deviceID))
{
    m_io = audio::AudioIO::Create(m_deviceID, "default");
    if (m_io->Failed())
    {
        LOGE("Audio IO failed");
        return;
    }
    m_input = m_io->GetInput();
    m_input->SetCallback([](std::uint8_t* data, std::size_t size, void* ctx) -> std::size_t
    {
        reinterpret_cast<AudioInputTester*>(ctx)->Update(reinterpret_cast<std::int16_t*>(data), size / 2);
        return 0;
    },
    this);
    m_input->Start();
}

AudioInputTester::~AudioInputTester()
{
    m_input->Stop();
    delete m_io;
}

void AudioInputTester::Update(std::int16_t* samples, std::size_t count)
{
    for (std::size_t i = 0; i < count; i++)
    {
        std::int16_t s = static_cast<std::int16_t>(std::abs(samples[i]));
        if (s > m_maxSample)
            m_maxSample = s;
    }
}

float AudioInputTester::GetAndResetLevel()
{
    float s = m_maxSample;
    m_maxSample = 0;
    return s / std::numeric_limits<std::int16_t>::max();
}

bool AudioInputTester::Failed() const
{
    return (m_io != nullptr) && m_io->Failed();
}
