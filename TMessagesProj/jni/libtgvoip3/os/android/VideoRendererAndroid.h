//
// Created by Grishka on 12.08.2018.
//

#ifndef LIBTGVOIP_VIDEORENDERERANDROID_H
#define LIBTGVOIP_VIDEORENDERERANDROID_H

#include "../../MessageThread.h"
#include "../../video/VideoRenderer.h"

#include "../../BlockingQueue.h"
#include <jni.h>

namespace tgvoip
{
namespace video
{
    class VideoRendererAndroid : public VideoRenderer
    {
    public:
        VideoRendererAndroid(jobject jobj);
        ~VideoRendererAndroid() override;
        void Reset(std::uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer>& csd) override;
        void DecodeAndDisplay(Buffer frame, std::uint32_t pts) override;
        void SetStreamEnabled(bool enabled) override;
        void SetRotation(std::uint16_t rotation) override;
        void SetStreamPaused(bool paused) override;

        static jmethodID resetMethod;
        static jmethodID decodeAndDisplayMethod;
        static jmethodID setStreamEnabledMethod;
        static jmethodID setRotationMethod;
        static std::vector<std::uint32_t> availableDecoders;
        static int maxResolution;

    private:
        struct Request
        {
            enum Type
            {
                DecodeFrame,
                ResetDecoder,
                UpdateStreamState,
                Shutdown
            };

            Buffer buffer;
            Type type;
        };
        void RunThread();
        Thread* thread = nullptr;
        bool running = true;
        BlockingQueue<Request> queue;
        std::vector<Buffer> csd;
        unsigned int width;
        unsigned int height;
        bool streamEnabled = true;
        bool streamPaused = false;
        std::uint32_t codec;
        std::uint16_t rotation = 0;
        jobject jobj;
    };
}
}

#endif //LIBTGVOIP_VIDEORENDERERANDROID_H
