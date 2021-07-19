//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_PULSEAUDIOLOADER_H
#define LIBTGVOIP_PULSEAUDIOLOADER_H

#include <string>
#include <functional>
#include <pulse/pulseaudio.h>
#include "../../audio/AudioIO.h"
#include "AudioInputPulse.h"
#include "AudioOutputPulse.h"

#define DECLARE_DL_FUNCTION(name) static typeof(name)* _import_##name

namespace tgvoip{
	namespace audio{
		class AudioPulse : public AudioIO{
		public:
			AudioPulse(std::string inputDevice, std::string outputDevice);
			virtual ~AudioPulse();
			virtual AudioInput* GetInput();
			virtual AudioOutput* GetOutput();

			static bool Load();
			static bool DoOneOperation(std::function<pa_operation*(pa_context*)> f);

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

		private:
			static void* lib;
			static bool loaded;
			AudioInputPulse* input=NULL;
			AudioOutputPulse* output=NULL;

			pa_threaded_mainloop* mainloop;
			pa_mainloop_api* mainloopApi;
			pa_context* context;
			bool isLocked=false;
			bool didStart=false;
		};
	}
}

#undef DECLARE_DL_FUNCTION

#endif // LIBTGVOIP_PULSEAUDIOLOADER_H
