//
// Created by Grishka on 10.08.2018.
//

#ifndef LIBTGVOIP_VIDEORENDERER_H
#define LIBTGVOIP_VIDEORENDERER_H

#include <vector>
#include "../Buffers.h"

namespace tgvoip{
	namespace video{
		class VideoRenderer{
		public:
			static std::vector<uint32_t> GetAvailableDecoders();
			virtual ~VideoRenderer(){};
			virtual void Reset(uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer>& csd)=0;
			virtual void DecodeAndDisplay(Buffer frame, uint32_t pts)=0;
			virtual void SetStreamEnabled(bool enabled)=0;
			virtual void SetRotation(uint16_t rotation)=0;
			static int GetMaximumResolution();
		};
	}
}

#endif //LIBTGVOIP_VIDEORENDERER_H
