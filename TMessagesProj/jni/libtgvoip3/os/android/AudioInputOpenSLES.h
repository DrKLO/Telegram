//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTOPENSLES_H
#define LIBTGVOIP_AUDIOINPUTOPENSLES_H

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "../../audio/AudioInput.h"

namespace tgvoip
{
namespace audio
{
    class AudioInputOpenSLES : public AudioInput
    {

    public:
        AudioInputOpenSLES();
        virtual ~AudioInputOpenSLES();
        virtual void Configure(std::uint32_t sampleRate, std::uint32_t bitsPerSample, std::uint32_t channels);
        virtual void Start();
        virtual void Stop();

        static unsigned int nativeBufferSize;

    private:
        static void BufferCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
        void HandleSLCallback();
        SLEngineItf slEngine;
        SLObjectItf slRecorderObj;
        SLRecordItf slRecorder;
        SLAndroidSimpleBufferQueueItf slBufferQueue;
        std::int16_t* buffer;
        std::int16_t* nativeBuffer;
        std::size_t positionInBuffer;
    };
}
}

#endif //LIBTGVOIP_AUDIOINPUTOPENSLES_H
