//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioPulse.h"
#include <dlfcn.h>
#include "../../logging.h"

#define DECLARE_DL_FUNCTION(name) typeof(name)* AudioPulse::_import_##name=NULL
#define CHECK_DL_ERROR(res, msg) if(!res){LOGE(msg ": %s", dlerror()); return false;}
#define LOAD_DL_FUNCTION(name) {_import_##name=(typeof(_import_##name))dlsym(lib, #name); CHECK_DL_ERROR(_import_##name, "Error getting entry point for " #name);}
#define CHECK_ERROR(res, msg) if(res!=0){LOGE(msg " failed: %s", pa_strerror(res)); failed=true; return;}

using namespace tgvoip;
using namespace tgvoip::audio;

bool AudioPulse::loaded=false;
void* AudioPulse::lib=NULL;

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

bool AudioPulse::Load(){
	if(loaded)
		return true;

	lib=dlopen("libpulse.so.0", RTLD_LAZY);
	if(!lib)
		lib=dlopen("libpulse.so", RTLD_LAZY);
	if(!lib){
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

	loaded=true;
	return true;
}

AudioPulse::AudioPulse(std::string inputDevice, std::string outputDevice){
	if(!Load()){
		failed=true;
		LOGE("Failed to load libpulse");
		return;
	}
		
	mainloop=pa_threaded_mainloop_new();
	if(!mainloop){
		LOGE("Error initializing PulseAudio (pa_threaded_mainloop_new)");
		failed=true;
		return;
	}
	mainloopApi=pa_threaded_mainloop_get_api(mainloop);
#ifndef MAXPATHLEN
	char exeName[20];
#else
	char exePath[MAXPATHLEN];
	char exeName[MAXPATHLEN];
	ssize_t lres=readlink("/proc/self/exe", exePath, sizeof(exePath));
	if(lres==-1)
		lres=readlink("/proc/curproc/file", exePath, sizeof(exePath));
	if(lres==-1)
		lres=readlink("/proc/curproc/exe", exePath, sizeof(exePath));
	if(lres>0){
		strcpy(exeName, basename(exePath));
	}else
#endif
	{
		snprintf(exeName, sizeof(exeName), "Process %d", getpid());
	}
	pa_proplist* proplist=pa_proplist_new();
	pa_proplist_sets(proplist, PA_PROP_MEDIA_ROLE, "phone");
	context=pa_context_new_with_proplist(mainloopApi, exeName, proplist);
	pa_proplist_free(proplist);
	if(!context){
		LOGE("Error initializing PulseAudio (pa_context_new)");
		failed=true;
		return;
	}
	pa_context_set_state_callback(context, [](pa_context* context, void* arg){
		AudioPulse* self=reinterpret_cast<AudioPulse*>(arg);
		pa_threaded_mainloop_signal(self->mainloop, 0);
	}, this);
	pa_threaded_mainloop_lock(mainloop);
	isLocked=true;
	int err=pa_threaded_mainloop_start(mainloop);
	CHECK_ERROR(err, "pa_threaded_mainloop_start");
	didStart=true;

	err=pa_context_connect(context, NULL, PA_CONTEXT_NOAUTOSPAWN, NULL);
	CHECK_ERROR(err, "pa_context_connect");

	while(true){
		pa_context_state_t contextState=pa_context_get_state(context);
		if(!PA_CONTEXT_IS_GOOD(contextState)){
			LOGE("Error initializing PulseAudio (PA_CONTEXT_IS_GOOD)");
			failed=true;
			return;
		}
		if(contextState==PA_CONTEXT_READY)
			break;
		pa_threaded_mainloop_wait(mainloop);
	}
	pa_threaded_mainloop_unlock(mainloop);
	isLocked=false;

	output=new AudioOutputPulse(context, mainloop, outputDevice);
	input=new AudioInputPulse(context, mainloop, inputDevice);
}

AudioPulse::~AudioPulse(){
	if(mainloop && didStart){
		if(isLocked)
			pa_threaded_mainloop_unlock(mainloop);
		pa_threaded_mainloop_stop(mainloop);
	}
	
	if(input)
		delete input;
	if(output)
		delete output;

	if(context){
		pa_context_disconnect(context);
		pa_context_unref(context);
	}
	if(mainloop)
		pa_threaded_mainloop_free(mainloop);
}

AudioOutput* AudioPulse::GetOutput(){
	return output;
}

AudioInput* AudioPulse::GetInput(){
	return input;
}

bool AudioPulse::DoOneOperation(std::function<pa_operation*(pa_context*)> f){
	if(!Load())
		return false;

	pa_mainloop* ml;
	pa_mainloop_api* mlAPI;
	pa_context* ctx;
	pa_operation* op=NULL;
	int paReady=0;

	ml=pa_mainloop_new();
	mlAPI=pa_mainloop_get_api(ml);
	ctx=pa_context_new(mlAPI, "libtgvoip");

	pa_context_connect(ctx, NULL, PA_CONTEXT_NOFLAGS, NULL);
	pa_context_set_state_callback(ctx, [](pa_context* context, void* arg){
		pa_context_state_t state;
		int* pa_ready=(int*)arg;

		state=pa_context_get_state(context);
		switch(state){
			case PA_CONTEXT_UNCONNECTED:
			case PA_CONTEXT_CONNECTING:
			case PA_CONTEXT_AUTHORIZING:
			case PA_CONTEXT_SETTING_NAME:
			default:
				break;
			case PA_CONTEXT_FAILED:
			case PA_CONTEXT_TERMINATED:
				*pa_ready=2;
				break;
			case PA_CONTEXT_READY:
				*pa_ready=1;
				break;
		}
	}, &paReady);

	while(true){
		if(paReady==0){
			pa_mainloop_iterate(ml, 1, NULL);
			continue;
		}
		if(paReady==2){
			pa_context_disconnect(ctx);
			pa_context_unref(ctx);
			pa_mainloop_free(ml);
			return false;
		}
		if(!op){
			op=f(ctx);
			continue;
		}
		if(pa_operation_get_state(op)==PA_OPERATION_DONE){
			pa_operation_unref(op);
			pa_context_disconnect(ctx);
			pa_context_unref(ctx);
			pa_mainloop_free(ml);
			return true;
		}
		pa_mainloop_iterate(ml, 1, NULL);
	}
}
