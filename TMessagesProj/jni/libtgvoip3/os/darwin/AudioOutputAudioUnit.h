//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_H
#define LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_H

#include "../../audio/AudioOutput.h"
#include "../../utils.h"
#include <AudioUnit/AudioUnit.h>
#include <atomic>

namespace tgvoip
{
namespace audio
{
    class AudioUnitIO;

    class AudioOutputAudioUnit : public AudioOutput
    {
    public:
        TGVOIP_DISALLOW_COPY_AND_ASSIGN(AudioOutputAudioUnit);
        AudioOutputAudioUnit(std::string deviceID, AudioUnitIO* io);
        virtual ~AudioOutputAudioUnit();
        virtual void Start();
        virtual void Stop();
        virtual bool IsPlaying();
        void HandleBufferCallback(AudioBufferList* ioData);
#if TARGET_OS_OSX
        virtual void SetCurrentDevice(std::string deviceID);
#endif

    private:
        std::atomic<bool> isPlaying;
        std::uint8_t remainingData[10240];
        std::size_t remainingDataSize;
        AudioUnitIO* io;
    };
}
}

#endif //LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_H
