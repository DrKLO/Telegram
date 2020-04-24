//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../logging.h"
#include "AudioIO.h"

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
#include "AudioIOCallback.h"
#elif defined(__ANDROID__)
#include "../os/android/AudioInputAndroid.h"
#include "../os/android/AudioOutputAndroid.h"
#elif defined(__APPLE__)
#include "../os/darwin/AudioUnitIO.h"
#include <TargetConditionals.h>
#if TARGET_OS_OSX
#include "../os/darwin/AudioInputAudioUnitOSX.h"
#include "../os/darwin/AudioOutputAudioUnitOSX.h"
#endif
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
#include "../os/windows/AudioInputWave.h"
#include "../os/windows/AudioOutputWave.h"
#endif
#include "../os/windows/AudioInputWASAPI.h"
#include "../os/windows/AudioOutputWASAPI.h"
#elif defined(__linux__) || defined(__FreeBSD_kernel__) || defined(__gnu_hurd__)
#ifndef WITHOUT_ALSA
#include "../os/linux/AudioInputALSA.h"
#include "../os/linux/AudioOutputALSA.h"
#endif
#ifndef WITHOUT_PULSE
#include "../os/linux/AudioPulse.h"
#endif
#else
#error "Unsupported operating system"
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

AudioIO* AudioIO::Create(std::string inputDevice, std::string outputDevice)
{
#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
    return new AudioIOCallback();
#elif defined(__ANDROID__)
    return new ContextlessAudioIO<AudioInputAndroid, AudioOutputAndroid>();
#elif defined(__APPLE__)
#if TARGET_OS_OSX
    if (kCFCoreFoundationVersionNumber < kCFCoreFoundationVersionNumber10_7)
        return new ContextlessAudioIO<AudioInputAudioUnitLegacy, AudioOutputAudioUnitLegacy>(std::move(inputDevice), std::move(outputDevice));

#endif
    return new AudioUnitIO(std::move(inputDevice), std::move(outputDevice));
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
    if (LOBYTE(LOWORD(GetVersion())) < 6)
        return new ContextlessAudioIO<AudioInputWave, AudioOutputWave>(std::move(inputDevice), std::move(outputDevice));
#endif
    return new ContextlessAudioIO<AudioInputWASAPI, AudioOutputWASAPI>(std::move(inputDevice), std::move(outputDevice));
#elif defined(__linux__)
#ifndef WITHOUT_ALSA
#ifndef WITHOUT_PULSE
    if (AudioPulse::Load())
    {
        AudioIO* io = new AudioPulse(std::move(inputDevice), std::move(outputDevice));
        if (!io->Failed() && io->GetInput()->IsInitialized() && io->GetOutput()->IsInitialized())
            return io;
        LOGW("PulseAudio available but not working; trying ALSA");
        delete io;
    }
#endif
    return new ContextlessAudioIO<AudioInputALSA, AudioOutputALSA>(std::move(inputDevice), std::move(outputDevice));
#else
    return new AudioPulse(std::move(inputDevice), std::move(outputDevice));
#endif
#endif
}

AudioIO::AudioIO() = default;

AudioIO::~AudioIO() = default;

bool AudioIO::Failed()
{
    return m_failed;
}

std::string AudioIO::GetErrorDescription()
{
    return m_error;
}
