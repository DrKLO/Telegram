//
// Created by Grishka on 01.04.17.
//

#ifndef LIBTGVOIP_RESAMPLER_H
#define LIBTGVOIP_RESAMPLER_H

#include <cstdint>
#include <cstddef>

namespace tgvoip
{

namespace audio
{

class Resampler
{
public:
    static std::size_t Convert48To44(const std::int16_t* from, std::int16_t* to, std::size_t fromLen, std::size_t toLen);
    static std::size_t Convert44To48(const std::int16_t* from, std::int16_t* to, std::size_t fromLen, std::size_t toLen);
    static std::size_t Convert(const std::int16_t* from, std::int16_t* to, std::size_t fromLen, std::size_t toLen, std::size_t num, std::size_t denom);
    static void Rescale60To80(const std::int16_t* in, std::int16_t* out);
    static void Rescale60To40(const std::int16_t* in, std::int16_t* out);
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_RESAMPLER_H
