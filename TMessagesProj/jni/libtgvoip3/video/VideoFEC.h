//
// Created by Grishka on 24/03/2019.
//

#ifndef LIBTGVOIP_VIDEOFEC_H
#define LIBTGVOIP_VIDEOFEC_H

#include "../Buffers.h"

namespace tgvoip
{

namespace video
{

namespace ParityFEC
{

Buffer Encode(const std::vector<Buffer>& packets);
Buffer Decode(const std::vector<Buffer>& dataPackets, const Buffer& fecPacket);

} // namespace ParityFEC

class CM256FEC
{
};

} // namespace video
} // namespace tgvoip

#endif // LIBTGVOIP_VIDEOFEC_H
