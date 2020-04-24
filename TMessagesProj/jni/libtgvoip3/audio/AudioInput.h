//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUT_H
#define LIBTGVOIP_AUDIOINPUT_H

#include "../MediaStreamItf.h"

#include <cstdint>
#include <string>
#include <vector>

namespace tgvoip
{

class AudioInputDevice;
class AudioOutputDevice;

namespace audio
{

class AudioInput : public MediaStreamItf
{
public:
    AudioInput();
    AudioInput(std::string deviceID);
    virtual ~AudioInput();

    bool IsInitialized() const;
    virtual std::string GetCurrentDevice() const;
    virtual void SetCurrentDevice(std::string deviceID);
    static void EnumerateDevices(std::vector<AudioInputDevice>& devs);
    static std::int32_t GetEstimatedDelay();

protected:
    std::string m_currentDevice;
    bool m_failed = false;
    static std::int32_t m_estimatedDelay;
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_AUDIOINPUT_H
