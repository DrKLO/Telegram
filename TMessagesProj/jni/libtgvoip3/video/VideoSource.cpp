//
// Created by Grishka on 10.08.2018.
//

#include "VideoSource.h"

#ifdef __ANDROID__
#include "../os/android/VideoSourceAndroid.h"
#elif defined(__APPLE__) && !defined(TARGET_OSX)
#include "../os/darwin/VideoToolboxEncoderSource.h"
#endif

using namespace tgvoip;
using namespace tgvoip::video;

std::shared_ptr<VideoSource> VideoSource::Create()
{
#ifdef __ANDROID__
    //return std::make_shared<VideoSourceAndroid>();
    return nullptr;
#endif
    return nullptr;
}

void VideoSource::SetCallback(CallbackType callback)
{
    m_callback = std::move(callback);
}
void VideoSource::SetStreamStateCallback(std::function<void(bool)> callback)
{
    m_streamStateCallback = std::move(callback);
}

bool VideoSource::Failed() const
{
    return m_failed;
}

std::string VideoSource::GetErrorDescription() const
{
    return m_error;
}

std::vector<Buffer>& VideoSource::GetCodecSpecificData()
{
    return m_csd;
}
unsigned int VideoSource::GetFrameWidth() const
{
    return m_width;
}
unsigned int VideoSource::GetFrameHeight() const
{
    return m_height;
}
void VideoSource::SetRotation(unsigned int rotation)
{
    m_rotation = rotation;
}

std::vector<std::uint32_t> VideoSource::GetAvailableEncoders()
{
#ifdef __ANDROID__
    return VideoSourceAndroid::availableEncoders;
#elif defined(__APPLE__) && !defined(TARGET_OSX)
    return VideoToolboxEncoderSource::GetAvailableEncoders();
#endif
    return std::vector<std::uint32_t>();
}
