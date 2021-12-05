//
// Created by Grishka on 10.08.2018.
//

#ifndef LIBTGVOIP_VIDEOSOURCE_H
#define LIBTGVOIP_VIDEOSOURCE_H

#include <vector>
#include <functional>
#include <memory>
#include <string>
#include "../Buffers.h"

namespace tgvoip{
	namespace video {
		class VideoSource{
		public:
			virtual ~VideoSource(){};
			static std::shared_ptr<VideoSource> Create();
			static std::vector<uint32_t> GetAvailableEncoders();
			void SetCallback(std::function<void(const Buffer& buffer, uint32_t flags, uint32_t rotation)> callback);
			virtual void Start()=0;
			virtual void Stop()=0;
			virtual void Reset(uint32_t codec, int maxResolution)=0;
			virtual void RequestKeyFrame()=0;
			virtual void SetBitrate(uint32_t bitrate)=0;
			bool Failed();
			std::string GetErrorDescription();
			std::vector<Buffer>& GetCodecSpecificData(){
				return csd;
			}
			unsigned int GetFrameWidth(){
				return width;
			}
			unsigned int GetFrameHeight(){
				return height;
			}
			void SetRotation(unsigned int rotation){
				this->rotation=rotation;
			}

		protected:
			std::function<void(const Buffer &, uint32_t, uint32_t)> callback;
			bool failed;
			std::string error;
			unsigned int width=0;
			unsigned int height=0;
			unsigned int rotation=0;
			std::vector<Buffer> csd;
		};
	}
}

#endif //LIBTGVOIP_VIDEOSOURCE_H
