//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_MEDIASTREAMINPUT_H
#define LIBTGVOIP_MEDIASTREAMINPUT_H

#include <string.h>
#include <vector>
#include <memory>
#include <stdint.h>
#include "threading.h"
#include "BlockingQueue.h"
#include "Buffers.h"

namespace tgvoip{

	class EchoCanceller;

class MediaStreamItf{
public:
	virtual void Start()=0;
	virtual void Stop()=0;
	void SetCallback(size_t (*f)(unsigned char*, size_t, void*), void* param);

//protected:
	size_t InvokeCallback(unsigned char* data, size_t length);

private:
	size_t (*callback)(unsigned char*, size_t, void*)=NULL;
	void* callbackParam;
};

	class AudioMixer : public MediaStreamItf{
	public:
		AudioMixer();
		virtual ~AudioMixer();
		void SetOutput(MediaStreamItf* output);
		virtual void Start();
		virtual void Stop();
		void AddInput(std::shared_ptr<MediaStreamItf> input);
		void RemoveInput(std::shared_ptr<MediaStreamItf> input);
		void SetInputVolume(std::shared_ptr<MediaStreamItf> input, float volumeDB);
		void SetEchoCanceller(EchoCanceller* aec);
	private:
		void RunThread();
		struct MixerInput{
			std::shared_ptr<MediaStreamItf> source;
			float multiplier;
		};
		Mutex inputsMutex;
		void DoCallback(unsigned char* data, size_t length);
		static size_t OutputCallback(unsigned char* data, size_t length, void* arg);
		std::vector<MixerInput> inputs;
		Thread* thread;
		BufferPool bufferPool;
		BlockingQueue<unsigned char*> processedQueue;
		Semaphore semaphore;
		EchoCanceller* echoCanceller;
		bool running;
	};

	class CallbackWrapper : public MediaStreamItf{
	public:
		CallbackWrapper(){};
		virtual ~CallbackWrapper(){};
		virtual void Start(){};
		virtual void Stop(){};

	};

	class AudioLevelMeter{
	public:
		AudioLevelMeter();
		float GetLevel();
		void Update(int16_t* samples, size_t count);
	private:
		int16_t absMax;
		int16_t count;
		int8_t currentLevel;
		int16_t currentLevelFullRange;
	};
};


#endif //LIBTGVOIP_MEDIASTREAMINPUT_H
