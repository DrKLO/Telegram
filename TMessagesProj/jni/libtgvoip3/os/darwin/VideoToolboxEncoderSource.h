//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_VIDEOTOOLBOXENCODERSOURCE
#define LIBTGVOIP_VIDEOTOOLBOXENCODERSOURCE

#include "../../video/VideoSource.h"
#include <CoreMedia/CoreMedia.h>
#include <VideoToolbox/VideoToolbox.h>
#include <vector>

#ifdef __OBJC__
@class TGVVideoSource;
#else
typedef struct objc_object TGVVideoSource;
#endif

namespace tgvoip
{
namespace video
{
    class VideoToolboxEncoderSource : public VideoSource
    {
    public:
        VideoToolboxEncoderSource(TGVVideoSource* parent);
        virtual ~VideoToolboxEncoderSource();
        virtual void Start() override;
        virtual void Stop() override;
        virtual void Reset(std::uint32_t codec, int maxResolution) override;
        virtual void RequestKeyFrame() override;
        virtual void SetBitrate(std::uint32_t bitrate) override;
        void EncodeFrame(CMSampleBufferRef frame);
        void SetStreamPaused(bool paused);
        static std::vector<std::uint32_t> GetAvailableEncoders();
        static bool SupportsFullHD();

    private:
        void EncoderCallback(OSStatus status, CMSampleBufferRef buffer, VTEncodeInfoFlags flags);
        void SetEncoderBitrateAndLimit(std::uint32_t bitrate);
        bool needUpdateStreamParams = true;
        std::uint32_t codec = 0;
        VTCompressionSessionRef session = NULL;
        bool keyframeRequested = false;
        std::uint32_t bitrateChangeRequested = 0;
        std::uint32_t lastBitrate = 512 * 1024;
        unsigned int lastFrameRate = 0;
        TGVVideoSource* objcObject;
    };
}
}

#endif /* LIBTGVOIP_VIDEOTOOLBOXENCODERSOURCE */
