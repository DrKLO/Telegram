//
// Created by Grishka on 12.08.2018.
//

#ifndef LIBTGVOIP_VIDEOSOURCEANDROID_H
#define LIBTGVOIP_VIDEOSOURCEANDROID_H

#include "../../Buffers.h"
#include "../../video/VideoSource.h"
#include <jni.h>
#include <vector>

namespace tgvoip
{
namespace video
{
    class VideoSourceAndroid : public VideoSource
    {
    public:
        VideoSourceAndroid(jobject jobj);
        ~VideoSourceAndroid() override;
        void Start() override;
        void Stop() override;
        void Reset(std::uint32_t codec, int maxResolution) override;
        void SendFrame(Buffer frame, std::uint32_t flags);
        void SetStreamParameters(std::vector<Buffer> m_csd, unsigned int m_width, unsigned int m_height);
        void RequestKeyFrame() override;
        void SetBitrate(std::uint32_t bitrate) override;
        void SetStreamPaused(bool paused);

        static std::vector<std::uint32_t> availableEncoders;

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
