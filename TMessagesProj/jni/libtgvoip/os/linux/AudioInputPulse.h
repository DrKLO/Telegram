//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTPULSE_H
#define LIBTGVOIP_AUDIOINPUTPULSE_H

#include "../../audio/AudioInput.h"
#include "../../threading.h"
#include <pulse/pulseaudio.h>

#define DECLARE_DL_FUNCTION(name) typeof(name)* _import_##name

namespace tgvoip{
namespace audio{

class AudioInputPulse : public AudioInput{
public:
	AudioInputPulse(pa_context* context, pa_threaded_mainloop* mainloop, std::string devID);
	virtual ~AudioInputPulse();
	virtual void Start();
	virtual void Stop();
	virtual bool IsRecording();
	virtual void SetCurrentDevice(std::string devID);
	static bool EnumerateDevices(std::vector<AudioInputDevice>& devs);

private:
	static void StreamStateCallback(pa_stream* s, void* arg);
	static void StreamReadCallback(pa_stream* stream, size_t requested_bytes, void* userdata);
	void StreamReadCallback(pa_stream* stream, size_t requestedBytes);
	pa_stream* CreateAndInitStream();

	pa_threaded_mainloop* mainloop;
	pa_context* context;
	pa_stream* stream;

	bool isRecording;
	bool isConnected;
	bool didStart;
	bool isLocked;
	unsigned char remainingData[960*8*2];
	size_t remainingDataSize;
};

}
}

#undef DECLARE_DL_FUNCTION

#endif //LIBTGVOIP_AUDIOINPUTPULSE_H
