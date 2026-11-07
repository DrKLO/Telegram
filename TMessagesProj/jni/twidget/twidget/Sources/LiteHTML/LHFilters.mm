#import <CoreImage/CoreImage.h>
#import <Foundation/Foundation.h>
#include "LHFilters.h"
#include <cmath>

namespace lh = litehtml;

static CIContext* FilterContext()
{
    static CIContext* context = [CIContext contextWithOptions:@{
        kCIContextWorkingColorSpace: (__bridge id)CGColorSpaceCreateWithName(kCGColorSpaceSRGB),
        kCIContextCacheIntermediates: @NO,
    }];
    return context;
}

void LHFilterMatrix(const lh::filter_function& fn, float m[20])
{
    for(int i = 0; i < 20; i++)
        m[i] = 0;
    m[0] = m[6] = m[12] = m[18] = 1;
    const float a = fn.amount;
    switch(fn.kind)
    {
    case lh::filter_function::brightness:
        m[0] = m[6] = m[12] = a;
        break;
    case lh::filter_function::contrast:
        m[0] = m[6] = m[12] = a;
        m[4] = m[9] = m[14] = 0.5f - 0.5f * a;
        break;
    case lh::filter_function::grayscale:
    {
        const float g = 1 - a;
        const float v[9] = {0.2126f + 0.7874f * g, 0.7152f - 0.7152f * g, 0.0722f - 0.0722f * g,
                            0.2126f - 0.2126f * g, 0.7152f + 0.2848f * g, 0.0722f - 0.0722f * g,
                            0.2126f - 0.2126f * g, 0.7152f - 0.7152f * g, 0.0722f + 0.9278f * g};
        for(int r = 0; r < 3; r++)
            for(int c = 0; c < 3; c++)
                m[r * 5 + c] = v[r * 3 + c];
        break;
    }
    case lh::filter_function::sepia:
    {
        const float g = 1 - a;
        const float v[9] = {0.393f + 0.607f * g, 0.769f - 0.769f * g, 0.189f - 0.189f * g,
                            0.349f - 0.349f * g, 0.686f + 0.314f * g, 0.168f - 0.168f * g,
                            0.272f - 0.272f * g, 0.534f - 0.534f * g, 0.131f + 0.869f * g};
        for(int r = 0; r < 3; r++)
            for(int c = 0; c < 3; c++)
                m[r * 5 + c] = v[r * 3 + c];
        break;
    }
    case lh::filter_function::saturate:
    {
        const float v[9] = {0.213f + 0.787f * a, 0.715f - 0.715f * a, 0.072f - 0.072f * a,
                            0.213f - 0.213f * a, 0.715f + 0.285f * a, 0.072f - 0.072f * a,
                            0.213f - 0.213f * a, 0.715f - 0.715f * a, 0.072f + 0.928f * a};
        for(int r = 0; r < 3; r++)
            for(int c = 0; c < 3; c++)
                m[r * 5 + c] = v[r * 3 + c];
        break;
    }
    case lh::filter_function::hue_rotate:
    {
        const float rad = a * float(M_PI) / 180, c = std::cos(rad), s = std::sin(rad);
        const float v[9] = {0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f};
        for(int r = 0; r < 3; r++)
            for(int k = 0; k < 3; k++)
                m[r * 5 + k] = v[r * 3 + k];
        break;
    }
    case lh::filter_function::invert:
        m[0] = m[6] = m[12] = 1 - 2 * a;
        m[4] = m[9] = m[14] = a;
        break;
    case lh::filter_function::opacity:
        m[18] = a;
        break;
    default:
        break;
    }
}

static CIImage* ApplyMatrix(CIImage* image, const float m[20])
{
    CIFilter* filter = [CIFilter filterWithName:@"CIColorMatrix"];
    [filter setValue:image forKey:kCIInputImageKey];
    [filter setValue:[CIVector vectorWithX:m[0] Y:m[1] Z:m[2] W:m[3]] forKey:@"inputRVector"];
    [filter setValue:[CIVector vectorWithX:m[5] Y:m[6] Z:m[7] W:m[8]] forKey:@"inputGVector"];
    [filter setValue:[CIVector vectorWithX:m[10] Y:m[11] Z:m[12] W:m[13]] forKey:@"inputBVector"];
    [filter setValue:[CIVector vectorWithX:m[15] Y:m[16] Z:m[17] W:m[18]] forKey:@"inputAVector"];
    [filter setValue:[CIVector vectorWithX:m[4] Y:m[9] Z:m[14] W:m[19]] forKey:@"inputBiasVector"];
    CIFilter* clamp = [CIFilter filterWithName:@"CIColorClamp"];
    [clamp setValue:filter.outputImage forKey:kCIInputImageKey];
    return clamp.outputImage;
}

static CIImage* Blur(CIImage* image, CGFloat sigma)
{
    if(sigma <= 0.01)
        return image;
    const CGFloat limit = 24;
    if(sigma > limit && !CGRectIsInfinite(image.extent))
    {
        const CGFloat k = limit / sigma;
        CIFilter* down = [CIFilter filterWithName:@"CILanczosScaleTransform"];
        [down setValue:image forKey:kCIInputImageKey];
        [down setValue:@(k) forKey:kCIInputScaleKey];
        [down setValue:@1 forKey:kCIInputAspectRatioKey];
        CIImage* small = Blur(down.outputImage, limit);
        return [[small imageBySamplingLinear] imageByApplyingTransform:CGAffineTransformMakeScale(1 / k, 1 / k)];
    }
    CIFilter* blur = [CIFilter filterWithName:@"CIGaussianBlur"];
    [blur setValue:image forKey:kCIInputImageKey];
    [blur setValue:@(sigma) forKey:kCIInputRadiusKey];
    return blur.outputImage;
}

static CIImage* ApplySteps(CIImage* image, const lh::filter_vector& steps, CGFloat scale, bool yDown, bool clamped)
{
    const CGRect extent = image.extent;
    for(const auto& fn : steps)
    {
        if(fn.kind == lh::filter_function::blur)
        {
            const CGFloat sigma = fn.radius.val() * scale;
            image = clamped ? [Blur([image.imageByClampingToExtent imageByCroppingToRect:CGRectInset(extent, -3 * sigma, -3 * sigma)], sigma) imageByCroppingToRect:extent]
                            : Blur(image, sigma);
        } else if(fn.kind == lh::filter_function::drop_shadow)
        {
            const lh::web_color& c = fn.color;
            float m[20] = {0};
            m[4]  = c.red / 255.f;
            m[9]  = c.green / 255.f;
            m[14] = c.blue / 255.f;
            m[18] = c.alpha / 255.f;
            CIImage* shadow = Blur(ApplyMatrix(image, m), fn.radius.val() * scale);
            shadow = [shadow imageByApplyingTransform:CGAffineTransformMakeTranslation(fn.x.val() * scale, (yDown ? -1 : 1) * fn.y.val() * scale)];
            image  = [image imageByCompositingOverImage:shadow];
        } else
        {
            float m[20];
            LHFilterMatrix(fn, m);
            image = ApplyMatrix(image, m);
        }
    }
    return image;
}

CGImageRef LHCreateFilteredImage(CGImageRef source, const lh::filter_vector& steps, CGFloat scale, bool yDown, bool clamped, CGColorSpaceRef space)
{
    if(!source)
        return nullptr;
    @autoreleasepool
    {
        CIImage* input  = [CIImage imageWithCGImage:source options:@{kCIImageColorSpace: (__bridge id)(space ?: CGImageGetColorSpace(source))}];
        CIImage* output = [ApplySteps(input, steps, scale, yDown, clamped) imageByCroppingToRect:input.extent];
        return [FilterContext() createCGImage:output fromRect:input.extent format:kCIFormatBGRA8 colorSpace:space ?: CGImageGetColorSpace(source)];
    }
}
