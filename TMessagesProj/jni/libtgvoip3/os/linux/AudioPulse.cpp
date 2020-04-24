//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../../logging.h"
#include "AudioPulse.h"

#include <dlfcn.h>

#include <cstring>

#define DECLARE_DL_FUNCTION(name) decltype(name)* AudioPulse::_import_##name = nullptr
#define CHECK_DL_ERROR(res, msg)     \
    if (!res)                        \
    {                                \
        LOGE(msg ": %s", dlerror()); \
        return false;                \
    }
#define LOAD_DL_FUNCTION(name)                                                              \
    {                                                                                       \
        _import_##name = reinterpret_cast<decltype(_import_##name)>(::dlsym(m_lib, #name)); \
        CHECK_DL_ERROR(_import_##name, "Error getting entry point for " #name);             \
    }
#define CHECK_ERROR(res, msg)                      \
    if (res != 0)                                  \
    {                                              \
        LOGE(msg " failed: %s", pa_strerror(res)); \
        m_failed = true;                           \
        return;                                    \
    }

using namespace tgvoip;
using namespace tgvoip::audio;

bool AudioPulse::m_loaded = false;
void* AudioPulse::m_lib = nullptr;

DECLARE_DL_FUNCTION(pa_threaded_mainloop_new);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_get_api);
DECLARE_DL_FUNCTION(pa_context_new);
DECLARE_DL_FUNCTION(pa_context_new_with_proplist);
DECLARE_DL_FUNCTION(pa_context_set_state_callback);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_lock);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_unlock);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_start);
DECLARE_DL_FUNCTION(pa_context_connect);
DECLARE_DL_FUNCTION(pa_context_get_state);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_wait);
DECLARE_DL_FUNCTION(pa_stream_new_with_proplist);
DECLARE_DL_FUNCTION(pa_stream_set_state_callback);
DECLARE_DL_FUNCTION(pa_stream_set_write_callback);
DECLARE_DL_FUNCTION(pa_stream_connect_playback);
DECLARE_DL_FUNCTION(pa_operation_unref);
DECLARE_DL_FUNCTION(pa_stream_cork);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_stop);
DECLARE_DL_FUNCTION(pa_stream_disconnect);
DECLARE_DL_FUNCTION(pa_stream_unref);
DECLARE_DL_FUNCTION(pa_context_disconnect);
DECLARE_DL_FUNCTION(pa_context_unref);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_free);
DECLARE_DL_FUNCTION(pa_threaded_mainloop_signal);
DECLARE_DL_FUNCTION(pa_stream_begin_write);
DECLARE_DL_FUNCTION(pa_stream_write);
DECLARE_DL_FUNCTION(pa_stream_get_state);
DECLARE_DL_FUNCTION(pa_strerror);
DECLARE_DL_FUNCTION(pa_stream_set_read_callback);
DECLARE_DL_FUNCTION(pa_stream_connect_record);
DECLARE_DL_FUNCTION(pa_stream_peek);
DECLARE_DL_FUNCTION(pa_stream_drop);
DECLARE_DL_FUNCTION(pa_mainloop_new);
DECLARE_DL_FUNCTION(pa_mainloop_get_api);
DECLARE_DL_FUNCTION(pa_mainloop_iterate);
DECLARE_DL_FUNCTION(pa_mainloop_free);
DECLARE_DL_FUNCTION(pa_context_get_sink_info_list);
DECLARE_DL_FUNCTION(pa_context_get_source_info_list);
DECLARE_DL_FUNCTION(pa_operation_get_state);
DECLARE_DL_FUNCTION(pa_proplist_new);
DECLARE_DL_FUNCTION(pa_proplist_sets);
DECLARE_DL_FUNCTION(pa_proplist_free);
DECLARE_DL_FUNCTION(pa_stream_get_latency);

#include "PulseFunctions.h"

bool AudioPulse::Load()
{
    if (m_loaded)
        return true;

    m_lib = dlopen("libpulse.so.0", RTLD_LAZY);
    if (!m_lib)
        m_lib = dlopen("libpulse.so", RTLD_LAZY);
    if (!m_lib)
    {
        LOGE("Error loading libpulse: %s", dlerror());
        return false;
    }

    LOAD_DL_FUNCTION(pa_threaded_mainloop_new);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_get_api);
    LOAD_DL_FUNCTION(pa_context_new);
    LOAD_DL_FUNCTION(pa_context_new_with_proplist);
    LOAD_DL_FUNCTION(pa_context_set_state_callback);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_lock);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_unlock);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_start);
    LOAD_DL_FUNCTION(pa_context_connect);
    LOAD_DL_FUNCTION(pa_context_get_state);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_wait);
    LOAD_DL_FUNCTION(pa_stream_new_with_proplist);
    LOAD_DL_FUNCTION(pa_stream_set_state_callback);
    LOAD_DL_FUNCTION(pa_stream_set_write_callback);
    LOAD_DL_FUNCTION(pa_stream_connect_playback);
    LOAD_DL_FUNCTION(pa_operation_unref);
    LOAD_DL_FUNCTION(pa_stream_cork);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_stop);
    LOAD_DL_FUNCTION(pa_stream_disconnect);
    LOAD_DL_FUNCTION(pa_stream_unref);
    LOAD_DL_FUNCTION(pa_context_disconnect);
    LOAD_DL_FUNCTION(pa_context_unref);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_free);
    LOAD_DL_FUNCTION(pa_threaded_mainloop_signal);
    LOAD_DL_FUNCTION(pa_stream_begin_write);
    LOAD_DL_FUNCTION(pa_stream_write);
    LOAD_DL_FUNCTION(pa_stream_get_state);
    LOAD_DL_FUNCTION(pa_strerror);
    LOAD_DL_FUNCTION(pa_stream_set_read_callback);
    LOAD_DL_FUNCTION(pa_stream_connect_record);
    LOAD_DL_FUNCTION(pa_stream_peek);
    LOAD_DL_FUNCTION(pa_stream_drop);
    LOAD_DL_FUNCTION(pa_mainloop_new);
    LOAD_DL_FUNCTION(pa_mainloop_get_api);
    LOAD_DL_FUNCTION(pa_mainloop_iterate);
    LOAD_DL_FUNCTION(pa_mainloop_free);
    LOAD_DL_FUNCTION(pa_context_get_sink_info_list);
    LOAD_DL_FUNCTION(pa_context_get_source_info_list);
    LOAD_DL_FUNCTION(pa_operation_get_state);
    LOAD_DL_FUNCTION(pa_proplist_new);
    LOAD_DL_FUNCTION(pa_proplist_sets);
    LOAD_DL_FUNCTION(pa_proplist_free);
    LOAD_DL_FUNCTION(pa_stream_get_latency);

    m_loaded = true;
    return true;
}

AudioPulse::AudioPulse(std::string inputDevice, std::string outputDevice)
{
    if (!Load())
    {
        m_failed = true;
        LOGE("Failed to load libpulse");
        return;
    }

    m_mainloop = pa_threaded_mainloop_new();
    if (!m_mainloop)
    {
        LOGE("Error initializing PulseAudio (pa_threaded_mainloop_new)");
        m_failed = true;
        return;
    }
    m_mainloopApi = pa_threaded_mainloop_get_api(m_mainloop);
#ifndef MAXPATHLEN
    char exeName[20];
#else
    char exePath[MAXPATHLEN];
    char exeName[MAXPATHLEN];
    ssize_t lres = readlink("/proc/self/exe", exePath, sizeof(exePath));
    if (lres == -1)
        lres = readlink("/proc/curproc/file", exePath, sizeof(exePath));
    if (lres == -1)
        lres = readlink("/proc/curproc/exe", exePath, sizeof(exePath));
    if (lres > 0)
    {
        std::strcpy(exeName, ::basename(exePath));
    }
    else
#endif
    {
        std::snprintf(exeName, sizeof(exeName), "Process %d", ::getpid());
    }
    pa_proplist* proplist = pa_proplist_new();
    pa_proplist_sets(proplist, PA_PROP_MEDIA_ROLE, "phone");
    m_context = pa_context_new_with_proplist(m_mainloopApi, exeName, proplist);
    pa_proplist_free(proplist);
    if (m_context == nullptr)
    {
        LOGE("Error initializing PulseAudio (pa_context_new)");
        m_failed = true;
        return;
    }
    pa_context_set_state_callback(
        m_context, [](pa_context* context, void* arg)
        {
            AudioPulse* self = reinterpret_cast<AudioPulse*>(arg);
            pa_threaded_mainloop_signal(self->m_mainloop, 0);
        },
        this);
    pa_threaded_mainloop_lock(m_mainloop);
    m_isLocked = true;
    int err = pa_threaded_mainloop_start(m_mainloop);
    CHECK_ERROR(err, "pa_threaded_mainloop_start");
    m_didStart = true;

    err = pa_context_connect(m_context, nullptr, PA_CONTEXT_NOAUTOSPAWN, nullptr);
    CHECK_ERROR(err, "pa_context_connect");

    while (true)
    {
        pa_context_state_t contextState = pa_context_get_state(m_context);
        if (!PA_CONTEXT_IS_GOOD(contextState))
        {
            LOGE("Error initializing PulseAudio (PA_CONTEXT_IS_GOOD)");
            m_failed = true;
            return;
        }
        if (contextState == PA_CONTEXT_READY)
            break;
        pa_threaded_mainloop_wait(m_mainloop);
    }
    pa_threaded_mainloop_unlock(m_mainloop);
    m_isLocked = false;

    m_output = new AudioOutputPulse(m_context, m_mainloop, std::move(outputDevice));
    m_input = new AudioInputPulse(m_context, m_mainloop, std::move(inputDevice));
}

AudioPulse::~AudioPulse()
{
    if (m_mainloop != nullptr && m_didStart)
    {
        if (m_isLocked)
            pa_threaded_mainloop_unlock(m_mainloop);
        pa_threaded_mainloop_stop(m_mainloop);
    }

    delete m_input;
    delete m_output;

    if (m_context != nullptr)
    {
        pa_context_disconnect(m_context);
        pa_context_unref(m_context);
    }
    if (m_mainloop != nullptr)
        pa_threaded_mainloop_free(m_mainloop);
}

AudioOutput* AudioPulse::GetOutput()
{
    return m_output;
}

AudioInput* AudioPulse::GetInput()
{
    return m_input;
}

bool AudioPulse::DoOneOperation(std::function<pa_operation*(pa_context*)> f)
{
    if (!Load())
        return false;

    pa_mainloop* ml;
    pa_mainloop_api* mlAPI;
    pa_context* ctx;
    pa_operation* op = nullptr;
    int paReady = 0;

    ml = pa_mainloop_new();
    mlAPI = pa_mainloop_get_api(ml);
    ctx = pa_context_new(mlAPI, "libtgvoip");

    pa_context_connect(ctx, nullptr, PA_CONTEXT_NOFLAGS, nullptr);
    pa_context_set_state_callback(
        ctx, [](pa_context* context, void* arg)
        {
            pa_context_state_t state;
            int* pa_ready = reinterpret_cast<int*>(arg);

            state = pa_context_get_state(context);
            switch (state)
            {
            case PA_CONTEXT_UNCONNECTED:
            case PA_CONTEXT_CONNECTING:
            case PA_CONTEXT_AUTHORIZING:
            case PA_CONTEXT_SETTING_NAME:
            default:
                break;
            case PA_CONTEXT_FAILED:
            case PA_CONTEXT_TERMINATED:
                *pa_ready = 2;
                break;
            case PA_CONTEXT_READY:
                *pa_ready = 1;
                break;
            }
        },
        &paReady);

    while (true)
    {
        if (paReady == 0)
        {
            pa_mainloop_iterate(ml, 1, nullptr);
            continue;
        }
        if (paReady == 2)
        {
            pa_context_disconnect(ctx);
            pa_context_unref(ctx);
            pa_mainloop_free(ml);
            return false;
        }
        if (op == nullptr)
        {
            op = f(ctx);
            continue;
        }
        if (pa_operation_get_state(op) == PA_OPERATION_DONE)
        {
            pa_operation_unref(op);
            pa_context_disconnect(ctx);
            pa_context_unref(ctx);
            pa_mainloop_free(ml);
            return true;
        }
        pa_mainloop_iterate(ml, 1, nullptr);
    }
}
