//
// Created by Grishka on 10.08.2018.
//

#ifndef LIBTGVOIP_VIDEORENDERER_H
#define LIBTGVOIP_VIDEORENDERER_H

#include "../Buffers.h"

#include <vector>

namespace tgvoip
{

namespace video
{

class VideoRenderer
{
public:
    static std::vector<std::uint32_t> GetAvailableDecoders();
    virtual ~VideoRenderer() = default;
    virtual void Reset(std::uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer>& csd) = 0;
    virtual void DecodeAndDisplay(Buffer frame, std::uint32_t pts) = 0;
    virtual void SetStreamEnabled(bool enabled) = 0;
    virtual void SetRotation(std::uint16_t rotation) = 0;
    virtual void SetStreamPaused(bool paused) = 0;
    static int GetMaximumResolution();
};

} // namespace video

} // namespace tgvoip

#endif // LIBTGVOIP_VIDEORENDERER_H
