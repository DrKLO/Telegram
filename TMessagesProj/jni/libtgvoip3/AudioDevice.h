#ifndef AUDIODEVICE_H
#define AUDIODEVICE_H

#include "utils.h"
#include "audio/AudioIO.h"

#include <string>

namespace tgvoip
{

struct AudioDevice
{
    std::string id;
    std::string displayName;
};

class AudioOutputDevice : public AudioDevice
{
};

class AudioInputDevice : public AudioDevice
{
};

class AudioInputTester
{
public:
    AudioInputTester(std::string deviceID);
    ~AudioInputTester();
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(AudioInputTester);
    float GetAndResetLevel();
    [[nodiscard]] bool Failed() const;

private:
    void Update(std::int16_t* samples, std::size_t count);
    audio::AudioIO* m_io = nullptr;
    audio::AudioInput* m_input = nullptr;
    std::int16_t m_maxSample = 0;
    std::string m_deviceID;
};

} // namespace tgvoip

#endif // AUDIODEVICE_H
