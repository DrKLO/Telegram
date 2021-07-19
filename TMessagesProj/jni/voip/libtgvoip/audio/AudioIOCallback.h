//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIO_IO_CALLBACK
#define LIBTGVOIP_AUDIO_IO_CALLBACK

#include "AudioIO.h"
#include <functional>

#include "../threading.h"

namespace tgvoip{
	namespace audio{
		class AudioInputCallback : public AudioInput{
		public:
			AudioInputCallback();
			virtual ~AudioInputCallback();
			virtual void Start() override;
			virtual void Stop() override;
			void SetDataCallback(std::function<void(int16_t*, size_t)> c);
		private:
			void RunThread();
			bool running=false;
			bool recording=false;
			Thread* thread;
			std::function<void(int16_t*, size_t)> dataCallback;
		};
		
		class AudioOutputCallback : public AudioOutput{
		public:
			AudioOutputCallback();
			virtual ~AudioOutputCallback();
			virtual void Start() override;
			virtual void Stop() override;
			virtual bool IsPlaying() override;
			void SetDataCallback(std::function<void(int16_t*, size_t)> c);
		private:
			void RunThread();
			bool running=false;
			bool playing=false;
			Thread* thread;
			std::function<void(int16_t*, size_t)> dataCallback;
		};
		
		class AudioIOCallback : public AudioIO{
		public:
			AudioIOCallback();
			virtual ~AudioIOCallback();
			virtual AudioInput* GetInput() override;
			virtual AudioOutput* GetOutput() override;
		private:
			AudioInputCallback* input;
			AudioOutputCallback* output;
		};
	}
}


#endif /* LIBTGVOIP_AUDIO_IO_CALLBACK */
