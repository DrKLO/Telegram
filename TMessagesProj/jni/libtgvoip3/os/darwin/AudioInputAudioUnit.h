//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTAUDIOUNIT_H
#define LIBTGVOIP_AUDIOINPUTAUDIOUNIT_H

#include "../../audio/AudioInput.h"
#include "../../utils.h"
#include <AudioUnit/AudioUnit.h>

namespace tgvoip
{
namespace audio
{
    class AudioUnitIO;

    class AudioInputAudioUnit : public AudioInput
    {

    public:
        TGVOIP_DISALLOW_COPY_AND_ASSIGN(AudioInputAudioUnit);
        AudioInputAudioUnit(std::string deviceID, AudioUnitIO* io);
        virtual ~AudioInputAudioUnit();
        virtual void Start();
        virtual void Stop();
        void HandleBufferCallback(AudioBufferList* ioData);
#if TARGET_OS_OSX
        virtual void SetCurrentDevice(std::string deviceID);
#endif

    private:
        std::uint8_t remainingData[10240];
        std::size_t remainingDataSize;
        bool isRecording;
        AudioUnitIO* io;
    };
}
}

#endif //LIBTGVOIP_AUDIOINPUTAUDIOUNIT_H
