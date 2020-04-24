//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../logging.h"
#include "AudioInput.h"

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
// nothing
#elif defined(__ANDROID__)
#include "../os/android/AudioInputAndroid.h"
#elif defined(__APPLE__)
#include "../os/darwin/AudioInputAudioUnit.h"
#include <TargetConditionals.h>
#if TARGET_OS_OSX
#include "../os/darwin/AudioInputAudioUnitOSX.h"
#endif
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
#include "../os/windows/AudioInputWave.h"
#endif
#include "../os/windows/AudioInputWASAPI.h"
#elif defined(__linux__) || defined(__FreeBSD_kernel__) || defined(__gnu_hurd__)
#ifndef WITHOUT_ALSA
#include "../os/linux/AudioInputALSA.h"
#endif
#ifndef WITHOUT_PULSE
#include "../os/linux/AudioPulse.h"
#endif
#else
#error "Unsupported operating system"
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

std::int32_t AudioInput::m_estimatedDelay = 60;

AudioInput::AudioInput()
    : m_currentDevice("default")
{
}

AudioInput::AudioInput(std::string deviceID)
    : m_currentDevice(std::move(deviceID))
{
}

AudioInput::~AudioInput() = default;

bool AudioInput::IsInitialized() const
{
    return !m_failed;
}

void AudioInput::EnumerateDevices(std::vector<AudioInputDevice>& devs)
{
#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
    // not supported
#elif defined(__APPLE__) && TARGET_OS_OSX
    AudioInputAudioUnitLegacy::EnumerateDevices(devs);
#elif defined(_WIN32)
#ifdef TGVOIP_WINXP_COMPAT
    if (LOBYTE(LOWORD(GetVersion())) < 6)
    {
        AudioInputWave::EnumerateDevices(devs);
        return;
    }
#endif
    AudioInputWASAPI::EnumerateDevices(devs);
#elif defined(__linux__) && !defined(__ANDROID__)
#if !defined(WITHOUT_PULSE) && !defined(WITHOUT_ALSA)
    if (!AudioInputPulse::EnumerateDevices(devs))
        AudioInputALSA::EnumerateDevices(devs);
#elif defined(WITHOUT_PULSE)
    AudioInputALSA::EnumerateDevices(devs);
#else
    AudioInputPulse::EnumerateDevices(devs);
#endif
#endif
}

std::string AudioInput::GetCurrentDevice() const
{
    return m_currentDevice;
}

void AudioInput::SetCurrentDevice(std::string deviceID)
{
    m_currentDevice = std::move(deviceID);
}

std::int32_t AudioInput::GetEstimatedDelay()
{
    return m_estimatedDelay;
}
