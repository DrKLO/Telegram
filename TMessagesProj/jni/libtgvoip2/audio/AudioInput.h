//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUT_H
#define LIBTGVOIP_AUDIOINPUT_H

#include <stdint.h>
#include <vector>
#include <string>
#include "../MediaStreamItf.h"

namespace tgvoip{

class AudioInputDevice;
class AudioOutputDevice;

namespace audio{
class AudioInput : public MediaStreamItf{
public:
	AudioInput();
	AudioInput(std::string deviceID);
	virtual ~AudioInput();

	bool IsInitialized();
	virtual std::string GetCurrentDevice();
	virtual void SetCurrentDevice(std::string deviceID);
	//static AudioInput* Create(std::string deviceID, void* platformSpecific);
	static void EnumerateDevices(std::vector<AudioInputDevice>& devs);
	static int32_t GetEstimatedDelay();

protected:
	std::string currentDevice;
	bool failed;
	static int32_t estimatedDelay;
};
}}

#endif //LIBTGVOIP_AUDIOINPUT_H
