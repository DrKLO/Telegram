//
// Created by Grishka on 12.08.2018.
//

#ifndef LIBTGVOIP_VIDEOSOURCEANDROID_H
#define LIBTGVOIP_VIDEOSOURCEANDROID_H

#include "../../video/VideoSource.h"
#include "../../Buffers.h"
#include <jni.h>
#include <vector>

namespace tgvoip{
	namespace video{
		class VideoSourceAndroid : public VideoSource{
		public:
			VideoSourceAndroid(jobject jobj);
			virtual ~VideoSourceAndroid();
			virtual void Start() override;
			virtual void Stop() override;
			virtual void Reset(uint32_t codec, int maxResolution) override;
			void SendFrame(Buffer frame, uint32_t flags);
			void SetStreamParameters(std::vector<Buffer> csd, unsigned int width, unsigned int height);
			virtual void RequestKeyFrame() override;
			virtual void SetBitrate(uint32_t bitrate) override;
			void SetStreamPaused(bool paused);

			static std::vector<uint32_t> availableEncoders;
		private:
			jobject javaObject;
			jmethodID prepareEncoderMethod;
			jmethodID startMethod;
			jmethodID stopMethod;
			jmethodID requestKeyFrameMethod;
			jmethodID setBitrateMethod;
		};
	}
}


#endif //LIBTGVOIP_VIDEOSOURCEANDROID_H
