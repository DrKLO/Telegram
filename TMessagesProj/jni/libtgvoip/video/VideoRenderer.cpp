//
// Created by Grishka on 10.08.2018.
//

#include "VideoRenderer.h"

#ifdef __ANDROID__
#include "../os/android/VideoRendererAndroid.h"
#elif defined(__APPLE__) && !defined(TARGET_OSX32)
#include "../os/darwin/SampleBufferDisplayLayerRenderer.h"
#endif

std::vector<uint32_t> tgvoip::video::VideoRenderer::GetAvailableDecoders(){
#ifdef __ANDROID__
	return VideoRendererAndroid::availableDecoders;
#elif defined(__APPLE__)
	return SampleBufferDisplayLayerRenderer::GetAvailableDecoders();
#endif
	return std::vector<uint32_t>();
}

int tgvoip::video::VideoRenderer::GetMaximumResolution(){
#ifdef __ANDROID__
	return VideoRendererAndroid::maxResolution;
#elif defined(__APPLE__) && !defined(TARGET_OSX32)
	return SampleBufferDisplayLayerRenderer::GetMaximumResolution();
#endif
	return 0;
}
