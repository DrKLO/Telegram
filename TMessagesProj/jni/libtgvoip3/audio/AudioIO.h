//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOIO_H
#define LIBTGVOIP_AUDIOIO_H

#include "../utils.h"
#include "AudioInput.h"
#include "AudioOutput.h"

#include <memory>
#include <string>

namespace tgvoip
{

namespace audio
{

class AudioIO
{
public:
    AudioIO();
    virtual ~AudioIO();
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(AudioIO);
    static AudioIO* Create(std::string inputDevice, std::string outputDevice);
    virtual AudioInput* GetInput() = 0;
    virtual AudioOutput* GetOutput() = 0;
    bool Failed();
    std::string GetErrorDescription();

protected:
    std::string m_error;
    bool m_failed = false;
};

template <class I, class O>
class ContextlessAudioIO : public AudioIO
{
public:
    ContextlessAudioIO()
        : m_input(new I())
        , m_output(new O())
    {
    }

    ContextlessAudioIO(std::string inputDeviceID, std::string outputDeviceID)
        : m_input(new I(std::move(inputDeviceID)))
        , m_output(new O(std::move(outputDeviceID)))
    {
    }

    virtual ~ContextlessAudioIO()
    {
        delete m_input;
        delete m_output;
    }

    virtual AudioInput* GetInput()
    {
        return m_input;
    }

    virtual AudioOutput* GetOutput()
    {
        return m_output;
    }

private:
    I* m_input;
    O* m_output;
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_AUDIOIO_H
