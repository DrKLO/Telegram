//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//


#include <assert.h>
#include <dlfcn.h>
#include <unistd.h>
#include "AudioInputPulse.h"
#include "../../logging.h"
#include "../../VoIPController.h"
#include "AudioPulse.h"
#include "PulseFunctions.h"
#if !defined(__GLIBC__)
#include <libgen.h>
#endif

#define BUFFER_SIZE 960
#define CHECK_ERROR(res, msg) if(res!=0){LOGE(msg " failed: %s", pa_strerror(res)); failed=true; return;}

using namespace tgvoip::audio;

AudioInputPulse::AudioInputPulse(pa_context* context, pa_threaded_mainloop* mainloop, std::string devID){
	isRecording=false;
	isConnected=false;
	didStart=false;

	this->mainloop=mainloop;
	this->context=context;
	stream=NULL;
	remainingDataSize=0;

	pa_threaded_mainloop_lock(mainloop);

	stream=CreateAndInitStream();
	pa_threaded_mainloop_unlock(mainloop);
	isLocked=false;
	if(!stream){
		return;
	}

	SetCurrentDevice(devID);
}

AudioInputPulse::~AudioInputPulse(){
	if(stream){
		pa_stream_disconnect(stream);
		pa_stream_unref(stream);
	}
}

pa_stream* AudioInputPulse::CreateAndInitStream(){
	pa_sample_spec sampleSpec{
		.format=PA_SAMPLE_S16LE,
		.rate=48000,
		.channels=1
	};
	pa_proplist* proplist=pa_proplist_new();
	pa_proplist_sets(proplist, PA_PROP_FILTER_APPLY, ""); // according to PA sources, this disables any possible filters
	pa_stream* stream=pa_stream_new_with_proplist(context, "libtgvoip capture", &sampleSpec, NULL, proplist);
	pa_proplist_free(proplist);
	if(!stream){
		LOGE("Error initializing PulseAudio (pa_stream_new)");
		failed=true;
		return NULL;
	}
	pa_stream_set_state_callback(stream, AudioInputPulse::StreamStateCallback, this);
	pa_stream_set_read_callback(stream, AudioInputPulse::StreamReadCallback, this);
	return stream;
}

void AudioInputPulse::Start(){
	if(failed || isRecording)
		return;

	pa_threaded_mainloop_lock(mainloop);
	isRecording=true;
	pa_operation_unref(pa_stream_cork(stream, 0, NULL, NULL));
	pa_threaded_mainloop_unlock(mainloop);
}

void AudioInputPulse::Stop(){
	if(!isRecording)
		return;

	isRecording=false;
	pa_threaded_mainloop_lock(mainloop);
	pa_operation_unref(pa_stream_cork(stream, 1, NULL, NULL));
	pa_threaded_mainloop_unlock(mainloop);
}

bool AudioInputPulse::IsRecording(){
	return isRecording;
}

void AudioInputPulse::SetCurrentDevice(std::string devID){
	pa_threaded_mainloop_lock(mainloop);
	currentDevice=devID;
	if(isRecording && isConnected){
		pa_stream_disconnect(stream);
		pa_stream_unref(stream);
		isConnected=false;
		stream=CreateAndInitStream();
	}

	pa_buffer_attr bufferAttr={
		.maxlength=(uint32_t)-1,
		.tlength=(uint32_t)-1,
		.prebuf=(uint32_t)-1,
		.minreq=(uint32_t)-1,
		.fragsize=960*2
	};
	int streamFlags=PA_STREAM_START_CORKED | PA_STREAM_INTERPOLATE_TIMING | PA_STREAM_AUTO_TIMING_UPDATE | PA_STREAM_ADJUST_LATENCY;

	int err=pa_stream_connect_record(stream, devID=="default" ? NULL : devID.c_str(), &bufferAttr, (pa_stream_flags_t)streamFlags);
	if(err!=0){
		pa_threaded_mainloop_unlock(mainloop);
		/*if(devID!="default"){
			SetCurrentDevice("default");
			return;
		}*/
	}
	CHECK_ERROR(err, "pa_stream_connect_record");

	while(true){
		pa_stream_state_t streamState=pa_stream_get_state(stream);
		if(!PA_STREAM_IS_GOOD(streamState)){
			LOGE("Error connecting to audio device '%s'", devID.c_str());
			pa_threaded_mainloop_unlock(mainloop);
			failed=true;
			return;
		}
		if(streamState==PA_STREAM_READY)
			break;
		pa_threaded_mainloop_wait(mainloop);
	}

	isConnected=true;

	if(isRecording){
		pa_operation_unref(pa_stream_cork(stream, 0, NULL, NULL));
	}
	pa_threaded_mainloop_unlock(mainloop);
}

bool AudioInputPulse::EnumerateDevices(std::vector<AudioInputDevice>& devs){
	return AudioPulse::DoOneOperation([&](pa_context* ctx){
		return pa_context_get_source_info_list(ctx, [](pa_context* ctx, const pa_source_info* info, int eol, void* userdata){
			if(eol>0)
				return;
			std::vector<AudioInputDevice>* devs=(std::vector<AudioInputDevice>*)userdata;
			AudioInputDevice dev;
			dev.id=std::string(info->name);
			dev.displayName=std::string(info->description);
			devs->push_back(dev);
		}, &devs);
	});
}

void AudioInputPulse::StreamStateCallback(pa_stream *s, void* arg) {
	AudioInputPulse* self=(AudioInputPulse*) arg;
	pa_threaded_mainloop_signal(self->mainloop, 0);
}

void AudioInputPulse::StreamReadCallback(pa_stream *stream, size_t requestedBytes, void *userdata){
	((AudioInputPulse*)userdata)->StreamReadCallback(stream, requestedBytes);
}

void AudioInputPulse::StreamReadCallback(pa_stream *stream, size_t requestedBytes) {
	size_t bytesRemaining = requestedBytes;
	uint8_t *buffer = NULL;
	pa_usec_t latency;
	if(pa_stream_get_latency(stream, &latency, NULL)==0){
		estimatedDelay=(int32_t)(latency/100);
	}
	while (bytesRemaining > 0) {
		size_t bytesToFill = 102400;

		if (bytesToFill > bytesRemaining) bytesToFill = bytesRemaining;

		int err=pa_stream_peek(stream, (const void**) &buffer, &bytesToFill);
		CHECK_ERROR(err, "pa_stream_peek");

		if(isRecording){
			if(remainingDataSize+bytesToFill>sizeof(remainingData)){
				LOGE("Capture buffer is too big (%d)", (int)bytesToFill);
			}
			memcpy(remainingData+remainingDataSize, buffer, bytesToFill);
			remainingDataSize+=bytesToFill;
			while(remainingDataSize>=960*2){
				InvokeCallback(remainingData, 960*2);
				memmove(remainingData, remainingData+960*2, remainingDataSize-960*2);
				remainingDataSize-=960*2;
			}
		}

		err=pa_stream_drop(stream);
		CHECK_ERROR(err, "pa_stream_drop");

		bytesRemaining -= bytesToFill;
	}
}
