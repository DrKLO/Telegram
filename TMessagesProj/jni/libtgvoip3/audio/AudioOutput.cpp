//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../logging.h"
#include "AudioOutput.h"

#include <cstdlib>

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
// nothing
#elif defined(__ANDROID__)
#include "../os/android/AudioOutputAndroid.h"
#include "../os/android/AudioOutputOpenSLES.h"
#include <sys/system_properties.h>
#elif defined(__APPLE__)
#include "../os/darwin/AudioOutputAudioUnit.h"
#include <TargetConditionals.h>
#if TARGET_OS_OSX
#include "../os/darwin/AudioOutputAudioUnitOSX.h"
#endif
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
#include "../os/windows/AudioOutputWave.h"
#endif
#include "../os/windows/AudioOutputWASAPI.h"
#elif defined(__linux__) || defined(__FreeBSD_kernel__) || defined(__gnu_hurd__)
#ifndef WITHOUT_ALSA
#include "../os/linux/AudioOutputALSA.h"
#endif
#ifndef WITHOUT_PULSE
#include "../os/linux/AudioOutputPulse.h"
#include "../os/linux/AudioPulse.h"
#endif
#else
#error "Unsupported operating system"
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

std::int32_t AudioOutput::m_estimatedDelay = 60;

AudioOutput::AudioOutput()
    : m_currentDevice("default")
{
}

AudioOutput::AudioOutput(std::string deviceID)
    : m_currentDevice(std::move(deviceID))
{
}

AudioOutput::~AudioOutput() = default;

std::int32_t AudioOutput::GetEstimatedDelay()
{
#if defined(__ANDROID__)
    char sdkNum[PROP_VALUE_MAX];
    __system_property_get("ro.build.version.sdk", sdkNum);
    int systemVersion = atoi(sdkNum);
    return systemVersion < 21 ? 150 : 50;
#endif
    return m_estimatedDelay;
}

void AudioOutput::EnumerateDevices(std::vector<AudioOutputDevice>& devs)
{
#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
    // not supported
#elif defined(__APPLE__) && TARGET_OS_OSX
    AudioOutputAudioUnitLegacy::EnumerateDevices(devs);
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
    if (LOBYTE(LOWORD(GetVersion())) < 6)
    {
        AudioOutputWave::EnumerateDevices(devs);
        return;
    }
#endif
    AudioOutputWASAPI::EnumerateDevices(devs);
#elif defined(__linux__) && !defined(__ANDROID__)
#if !defined(WITHOUT_PULSE) && !defined(WITHOUT_ALSA)
    if (!AudioOutputPulse::EnumerateDevices(devs))
        AudioOutputALSA::EnumerateDevices(devs);
#elif defined(WITHOUT_PULSE)
    AudioOutputALSA::EnumerateDevices(devs);
#else
    AudioOutputPulse::EnumerateDevices(devs);
#endif
#endif
}

std::string AudioOutput::GetCurrentDevice() const
{
    return m_currentDevice;
}

void AudioOutput::SetCurrentDevice(std::string deviceID)
{
    m_currentDevice = std::move(deviceID);
}

bool AudioOutput::IsInitialized() const
{
    return !m_failed;
}
