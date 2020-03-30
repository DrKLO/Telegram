//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef TGVOIP_SAMPLEBUFFERDISPLAYLAYERRENDERER
#define TGVOIP_SAMPLEBUFFERDISPLAYLAYERRENDERER

#include "../../video/VideoRenderer.h"
#include <vector>
#include <objc/objc.h>
#include <VideoToolbox/VideoToolbox.h>

#ifdef __OBJC__
@class TGVVideoRenderer;
#else
typedef struct objc_object TGVVideoRenderer;
#endif

namespace tgvoip{
	namespace video{
		class SampleBufferDisplayLayerRenderer : public VideoRenderer{
		public:
			SampleBufferDisplayLayerRenderer(TGVVideoRenderer* renderer);
			virtual ~SampleBufferDisplayLayerRenderer();
			virtual void Reset(uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer>& csd) override;
			virtual void DecodeAndDisplay(Buffer frame, uint32_t pts) override;
			virtual void SetStreamEnabled(bool enabled) override;
			virtual void SetRotation(uint16_t rotation) override;
			virtual void SetStreamPaused(bool paused) override;
			static int GetMaximumResolution();
			static std::vector<uint32_t> GetAvailableDecoders();
		private:
			TGVVideoRenderer* renderer;
			CMFormatDescriptionRef formatDesc=NULL;
			bool needReset=false;
			bool streamEnabled=false;
		};
	}
}

#endif /* TGVOIP_SAMPLEBUFFERDISPLAYLAYERRENDERER */
