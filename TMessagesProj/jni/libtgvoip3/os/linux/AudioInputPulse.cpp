//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../../logging.h"
#include "../../VoIPController.h"
#include "AudioInputPulse.h"
#include "AudioPulse.h"
#include "PulseFunctions.h"

#include <dlfcn.h>
#include <unistd.h>
#if !defined(__GLIBC__)
#include <libgen.h>
#endif

#include <cassert>
#include <cstring>

#define BUFFER_SIZE 960
#define CHECK_ERROR(res, msg)                      \
    if (res != 0)                                  \
    {                                              \
        LOGE(msg " failed: %s", pa_strerror(res)); \
        m_failed = true;                           \
        return;                                    \
    }

using namespace tgvoip::audio;

AudioInputPulse::AudioInputPulse(pa_context* context, pa_threaded_mainloop* mainloop, std::string devID)
    : m_mainloop(mainloop)
    , m_context(context)
{
    pa_threaded_mainloop_lock(mainloop);

    m_stream = CreateAndInitStream();
    pa_threaded_mainloop_unlock(mainloop);
    if (m_stream == nullptr)
    {
        return;
    }

    SetCurrentDevice(std::move(devID));
}

AudioInputPulse::~AudioInputPulse()
{
    if (m_stream != nullptr)
    {
        pa_stream_disconnect(m_stream);
        pa_stream_unref(m_stream);
    }
}

pa_stream* AudioInputPulse::CreateAndInitStream()
{
    pa_sample_spec sampleSpec
    {
        .format = PA_SAMPLE_S16LE,
        .rate = 48000,
        .channels = 1
    };
    pa_proplist* proplist = pa_proplist_new();
    pa_proplist_sets(proplist, PA_PROP_FILTER_APPLY, ""); // according to PA sources, this disables any possible filters
    pa_stream* stream = pa_stream_new_with_proplist(m_context, "libtgvoip capture", &sampleSpec, nullptr, proplist);
    pa_proplist_free(proplist);
    if (stream == nullptr)
    {
        LOGE("Error initializing PulseAudio (pa_stream_new)");
        m_failed = true;
        return nullptr;
    }
    pa_stream_set_state_callback(stream, AudioInputPulse::StreamStateCallback, this);
    pa_stream_set_read_callback(stream, AudioInputPulse::StreamReadCallback, this);
    return stream;
}

void AudioInputPulse::Start()
{
    if (m_failed || m_isRecording)
        return;

    pa_threaded_mainloop_lock(m_mainloop);
    m_isRecording = true;
    pa_operation_unref(pa_stream_cork(m_stream, 0, nullptr, nullptr));
    pa_threaded_mainloop_unlock(m_mainloop);
}

void AudioInputPulse::Stop()
{
    if (!m_isRecording)
        return;

    m_isRecording = false;
    pa_threaded_mainloop_lock(m_mainloop);
    pa_operation_unref(pa_stream_cork(m_stream, 1, nullptr, nullptr));
    pa_threaded_mainloop_unlock(m_mainloop);
}

bool AudioInputPulse::IsRecording()
{
    return m_isRecording;
}

void AudioInputPulse::SetCurrentDevice(std::string devID)
{
    pa_threaded_mainloop_lock(m_mainloop);
    m_currentDevice = std::move(devID);
    if (m_isRecording && m_isConnected)
    {
        pa_stream_disconnect(m_stream);
        pa_stream_unref(m_stream);
        m_isConnected = false;
        m_stream = CreateAndInitStream();
    }

    pa_buffer_attr bufferAttr =
    {
        .maxlength = std::numeric_limits<std::uint32_t>::max(),
        .tlength = std::numeric_limits<std::uint32_t>::max(),
        .prebuf = std::numeric_limits<std::uint32_t>::max(),
        .minreq = std::numeric_limits<std::uint32_t>::max(),
        .fragsize = 960 * 2
    };
    int streamFlags = PA_STREAM_START_CORKED | PA_STREAM_INTERPOLATE_TIMING | PA_STREAM_AUTO_TIMING_UPDATE | PA_STREAM_ADJUST_LATENCY;

    int err = pa_stream_connect_record(m_stream, m_currentDevice == "default" ? nullptr : m_currentDevice.c_str(), &bufferAttr, (pa_stream_flags_t)streamFlags);
    if (err != 0)
    {
        pa_threaded_mainloop_unlock(m_mainloop);
    }
    CHECK_ERROR(err, "pa_stream_connect_record");

    while (true)
    {
        pa_stream_state_t streamState = pa_stream_get_state(m_stream);
        if (!PA_STREAM_IS_GOOD(streamState))
        {
            LOGE("Error connecting to audio device '%s'", m_currentDevice.c_str());
            pa_threaded_mainloop_unlock(m_mainloop);
            m_failed = true;
            return;
        }
        if (streamState == PA_STREAM_READY)
            break;
        pa_threaded_mainloop_wait(m_mainloop);
    }

    m_isConnected = true;

    if (m_isRecording)
    {
        pa_operation_unref(pa_stream_cork(m_stream, 0, nullptr, nullptr));
    }
    pa_threaded_mainloop_unlock(m_mainloop);
}

bool AudioInputPulse::EnumerateDevices(std::vector<AudioInputDevice>& devs)
{
    return AudioPulse::DoOneOperation([&](pa_context* ctx)
    {
        return pa_context_get_source_info_list(
            ctx, [](pa_context* ctx, const pa_source_info* info, int eol, void* userdata)
            {
                if (eol > 0)
                    return;
                std::vector<AudioInputDevice>* devs = reinterpret_cast<std::vector<AudioInputDevice>*>(userdata);
                AudioInputDevice dev;
                dev.id = std::string(info->name);
                dev.displayName = std::string(info->description);
                devs->emplace_back(dev);
            },
            &devs);
    });
}

void AudioInputPulse::StreamStateCallback(pa_stream* s, void* arg)
{
    AudioInputPulse* self = reinterpret_cast<AudioInputPulse*>(arg);
    pa_threaded_mainloop_signal(self->m_mainloop, 0);
}

void AudioInputPulse::StreamReadCallback(pa_stream* stream, std::size_t requestedBytes, void* userdata)
{
    (reinterpret_cast<AudioInputPulse*>(userdata))->StreamReadCallback(stream, requestedBytes);
}

void AudioInputPulse::StreamReadCallback(pa_stream* stream, std::size_t requestedBytes)
{
    std::size_t bytesRemaining = requestedBytes;
    std::uint8_t* buffer = nullptr;
    pa_usec_t latency;
    if (pa_stream_get_latency(stream, &latency, nullptr) == 0)
    {
        m_estimatedDelay = static_cast<std::int32_t>(latency / 100);
    }
    while (bytesRemaining > 0)
    {
        std::size_t bytesToFill = 102400;

        if (bytesToFill > bytesRemaining)
            bytesToFill = bytesRemaining;

        int err = pa_stream_peek(stream, reinterpret_cast<const void**>(&buffer), &bytesToFill);
        CHECK_ERROR(err, "pa_stream_peek");

        if (m_isRecording)
        {
            if (m_remainingDataSize + bytesToFill > sizeof(m_remainingData))
            {
                LOGE("Capture buffer is too big (%d)", static_cast<int>(bytesToFill));
            }
            std::memcpy(m_remainingData + m_remainingDataSize, buffer, bytesToFill);
            m_remainingDataSize += bytesToFill;
            while (m_remainingDataSize >= 960 * 2)
            {
                InvokeCallback(m_remainingData, 960 * 2);
                std::memmove(m_remainingData, m_remainingData + 960 * 2, m_remainingDataSize - 960 * 2);
                m_remainingDataSize -= 960 * 2;
            }
        }

        err = pa_stream_drop(stream);
        CHECK_ERROR(err, "pa_stream_drop");

        bytesRemaining -= bytesToFill;
    }
}
