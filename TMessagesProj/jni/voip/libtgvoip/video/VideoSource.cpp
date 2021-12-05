//
// Created by Grishka on 10.08.2018.
//

#include "VideoSource.h"

#ifdef __ANDROID__
#include "../os/android/VideoSourceAndroid.h"
#elif defined(__APPLE__) && !defined(TARGET_OSX32)
#include "../os/darwin/VideoToolboxEncoderSource.h"
#endif

using namespace tgvoip;
using namespace tgvoip::video;

std::shared_ptr<VideoSource> VideoSource::Create(){
#ifdef __ANDROID__
	//return std::make_shared<VideoSourceAndroid>();
	return nullptr;
#endif
	return nullptr;
}


void VideoSource::SetCallback(std::function<void(const Buffer &, uint32_t, uint32_t)> callback){
	this->callback=callback;
}

bool VideoSource::Failed(){
	return failed;
}

std::string VideoSource::GetErrorDescription(){
	return error;
}

std::vector<uint32_t> VideoSource::GetAvailableEncoders(){
#ifdef __ANDROID__
	return VideoSourceAndroid::availableEncoders;
#elif defined(__APPLE__) && !defined(TARGET_OSX32)
	return VideoToolboxEncoderSource::GetAvailableEncoders();
#endif
	return std::vector<uint32_t>();
}
