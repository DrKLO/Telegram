//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUT_H
#define LIBTGVOIP_AUDIOOUTPUT_H

#include "../MediaStreamItf.h"

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace tgvoip
{

class AudioInputDevice;
class AudioOutputDevice;

namespace audio
{

class AudioOutput : public MediaStreamItf
{
public:
    AudioOutput();
    AudioOutput(std::string deviceID);
    virtual ~AudioOutput();
    virtual bool IsPlaying() = 0;
    static std::int32_t GetEstimatedDelay();
    virtual std::string GetCurrentDevice() const;
    virtual void SetCurrentDevice(std::string deviceID);
    static void EnumerateDevices(std::vector<AudioOutputDevice>& devs);
    bool IsInitialized() const;

protected:
    std::string m_currentDevice;
    bool m_failed = false;
    static std::int32_t m_estimatedDelay;
};

} // namespace audio

} // namespace tgvoip

#endif //LIBTGVOIP_AUDIOOUTPUT_H
