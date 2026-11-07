#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include "../../Sources/LiteHTML/LHImmutableBitmap.h"
#include <algorithm>
#include <array>
#include <chrono>
#include <cstring>
#include <cstdlib>
#include <mach/mach.h>
#include <string>
#include <sys/resource.h>
#include <vector>

namespace
{
struct ImmutableScrollMode
{
    bool hadValue;
    std::string previous;

    explicit ImmutableScrollMode(bool enabled)
    {
        const char* value = getenv("LH_NO_IMMUTABLE_SCROLL_BITMAP");
        hadValue = value != nullptr;
        if(value)
            previous = value;
        if(enabled)
            unsetenv("LH_NO_IMMUTABLE_SCROLL_BITMAP");
        else
            setenv("LH_NO_IMMUTABLE_SCROLL_BITMAP", "1", 1);
    }

    ~ImmutableScrollMode()
    {
        if(hadValue)
            setenv("LH_NO_IMMUTABLE_SCROLL_BITMAP", previous.c_str(), 1);
        else
            unsetenv("LH_NO_IMMUTABLE_SCROLL_BITMAP");
    }
};

static LHDocument *ImmutableScrollDocument(CGColorSpaceRef space)
{
    NSString *markup = @"<!DOCTYPE html><style>body{margin:0;font:13px -apple-system;color:#edf3ff}.window{margin:7px;width:193.25px;height:67.5px;overflow:auto;border:1px solid #2349;padding:3px;border-radius:9px}.track{width:650px;height:420px;background:linear-gradient(90deg,#193e4787,#265857e1);position:relative}.panel{position:absolute;left:12.25px;top:6.375px;width:180px;height:115px;border-radius:13px;overflow:hidden;background:#f0743a87;box-shadow:-3px 5px 9px #0006}.inner{width:270px;padding:7px;opacity:.67;background:linear-gradient(130deg,#ffd08e,#193e4700);transform:translate(.125px,.375px)}.far{position:absolute;left:480px;top:265px;width:160px;height:90px;background:#36c7;border-radius:11px}</style><div id=scroll class=window><div id=track class=track style='transform:translate(.125px,.375px)'><div class=panel>Immutable scroll bitmap · 012345<div class=inner>Partial alpha, rounded clipping and overlapping glyph resources</div></div><div class=far>Second tile<br>Москва 日本語</div></div></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:markup css:nil previous:nil viewportSize:CGSizeMake(280, 180)];
    document.colorSpace = space;
    [document layoutWithMaxWidth:280 preferredWidth:280];
    return document;
}

static CGImageRef ImmutableScrollCapture(LHDocument *document, CGRect rect, CGFloat scale, bool enabled)
{
    ImmutableScrollMode mode(enabled);
    return [document copyScrollContentWithAttribute:@"id" value:@"scroll" rect:rect scale:scale];
}

static NSData *ImmutableBytes(CGImageRef image)
{
    if(!image)
        return nil;
    CFDataRef source = CGDataProviderCopyData(CGImageGetDataProvider(image));
    if(!source)
        return nil;
    NSData *result = [NSData dataWithBytes:CFDataGetBytePtr(source) length:CFDataGetLength(source)];
    CFRelease(source);
    return result;
}

static void ImmutablePattern(CGContextRef context, unsigned seed, bool dense)
{
    const size_t width = CGBitmapContextGetWidth(context), height = CGBitmapContextGetHeight(context);
    const size_t stride = CGBitmapContextGetBytesPerRow(context);
    auto* bytes = static_cast<uint8_t*>(CGBitmapContextGetData(context));
    for(size_t y = 0; y < (dense ? height : std::min(height, size_t(7))); y++)
        for(size_t x = 0; x < (dense ? width : std::min(width, size_t(11))); x++)
        {
            const uint8_t alpha = (x * 13 + y * 17 + seed * 29) & 255;
            uint8_t* pixel = bytes + y * stride + x * 4;
            pixel[0] = ((x + seed) & 255) * alpha / 255;
            pixel[1] = ((y * 3 + seed) & 255) * alpha / 255;
            pixel[2] = ((x + y + seed * 5) & 255) * alpha / 255;
            pixel[3] = alpha;
        }
}

static double ImmutableCPU()
{
    rusage usage = {};
    getrusage(RUSAGE_SELF, &usage);
    return usage.ru_utime.tv_sec + usage.ru_stime.tv_sec +
           (usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1e6;
}

static uint64_t ImmutableFootprint()
{
    task_vm_info_data_t info = {};
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    return task_info(mach_task_self(), TASK_VM_INFO, reinterpret_cast<task_info_t>(&info), &count) == KERN_SUCCESS
        ? info.phys_footprint : 0;
}

static task_vm_info_data_t ImmutableMemory()
{
    task_vm_info_data_t info = {};
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    if(task_info(mach_task_self(), TASK_VM_INFO, reinterpret_cast<task_info_t>(&info), &count) != KERN_SUCCESS)
        return {};
    return info;
}

static uint32_t ImmutableCRC(const uint8_t* bytes, size_t count)
{
    static const std::array<uint32_t, 256> table = [] {
        std::array<uint32_t, 256> result{};
        for(size_t index = 0; index < result.size(); ++index)
        {
            uint32_t crc = uint32_t(index);
            for(unsigned bit = 0; bit < 8; ++bit)
                crc = (crc >> 1) ^ (0xedb88320u & (0u - (crc & 1u)));
            result[index] = crc;
        }
        return result;
    }();
    uint32_t crc = 0xffffffffu;
    for(size_t index = 0; index < count; ++index)
        crc = (crc >> 8) ^ table[(crc ^ bytes[index]) & 255];
    return crc ^ 0xffffffffu;
}
}

@interface ImmutableBitmapTests : XCTestCase
@end

@implementation ImmutableBitmapTests

- (void)assertImage:(CGImageRef)actual equals:(CGImageRef)expected label:(NSString *)label
{
    XCTAssertTrue(actual != nullptr, @"%@", label);
    XCTAssertTrue(expected != nullptr, @"%@", label);
    if(!actual || !expected)
        return;
    XCTAssertEqual(CGImageGetWidth(actual), CGImageGetWidth(expected), @"%@", label);
    XCTAssertEqual(CGImageGetHeight(actual), CGImageGetHeight(expected), @"%@", label);
    XCTAssertEqual(CGImageGetBitsPerComponent(actual), CGImageGetBitsPerComponent(expected), @"%@", label);
    XCTAssertEqual(CGImageGetBitsPerPixel(actual), CGImageGetBitsPerPixel(expected), @"%@", label);
    XCTAssertEqual(CGImageGetBytesPerRow(actual), CGImageGetBytesPerRow(expected), @"%@", label);
    XCTAssertEqual(CGImageGetBitmapInfo(actual), CGImageGetBitmapInfo(expected), @"%@", label);
    XCTAssertEqual(CGImageGetAlphaInfo(actual), CGImageGetAlphaInfo(expected), @"%@", label);
    XCTAssertEqual(CGImageGetShouldInterpolate(actual), CGImageGetShouldInterpolate(expected), @"%@", label);
    XCTAssertEqual(CGImageGetRenderingIntent(actual), CGImageGetRenderingIntent(expected), @"%@", label);
    XCTAssertTrue(CGImageGetDecode(actual) == nullptr && CGImageGetDecode(expected) == nullptr, @"%@", label);
    XCTAssertTrue(CFEqual(CGImageGetColorSpace(actual), CGImageGetColorSpace(expected)), @"%@", label);
    CFDataRef actualProfile = CGColorSpaceCopyICCData(CGImageGetColorSpace(actual));
    CFDataRef expectedProfile = CGColorSpaceCopyICCData(CGImageGetColorSpace(expected));
    XCTAssertEqualObjects((__bridge NSData *)actualProfile, (__bridge NSData *)expectedProfile, @"%@", label);
    if(actualProfile)
        CFRelease(actualProfile);
    if(expectedProfile)
        CFRelease(expectedProfile);
    XCTAssertEqualObjects(ImmutableBytes(actual), ImmutableBytes(expected), @"%@", label);
}

- (void)testBitmapBytesMetadataAndZeroFillMatchSnapshot
{
    const size_t retained = LHImmutableBitmap::retainedContexts();
    const size_t retainedBytes = LHImmutableBitmap::retainedBytes();
    for(CFStringRef profile : {kCGColorSpaceSRGB, kCGColorSpaceDisplayP3, kCGColorSpaceLinearSRGB})
        for(size_t width : {size_t(1), size_t(7), size_t(63), size_t(302), size_t(514)})
            for(bool padded : {false, true})
                for(bool dense : {false, true})
                    @autoreleasepool
                    {
                        CGColorSpaceRef space = CGColorSpaceCreateWithName(profile);
                        const size_t stride = padded ? width * 4 + 28 : 0;
                        LHImmutableBitmap actual(width, 35, space, stride), reference(width, 35, space, stride);
                        XCTAssertTrue(actual.context() != nullptr && reference.context() != nullptr);
                        if(actual.context() && reference.context())
                        {
                            const size_t bytes = CGBitmapContextGetBytesPerRow(actual.context()) * 35;
                            const auto* data = static_cast<const uint8_t*>(CGBitmapContextGetData(actual.context()));
                            XCTAssertTrue(std::all_of(data, data + bytes, [](uint8_t byte) { return byte == 0; }));
                            ImmutablePattern(actual.context(), 11, dense);
                            ImmutablePattern(reference.context(), 11, dense);
                            CGImageRef image = actual.takeImage(), expected = reference.takeImage(false);
                            [self assertImage:image equals:expected label:[NSString stringWithFormat:@"%@ width=%zu padded=%d dense=%d", profile, width, padded, dense]];
                            XCTAssertTrue(actual.context() == nullptr);
                            XCTAssertTrue(actual.takeImage() == nullptr);
                            if(image)
                                CGImageRelease(image);
                            if(expected)
                                CGImageRelease(expected);
                        }
                        CGColorSpaceRelease(space);
                    }
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), retained);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), retainedBytes);
}

- (void)testQueuedPaintingAndFractionalClipMatchSnapshotExactly
{
    NSString *markup = @"<style>body{margin:0;background:transparent;font:13px -apple-system;color:#edf3ff}.card{margin:9px;padding:8px;width:210px;height:100px;background:linear-gradient(90deg,#163c42,#263858);border:1px solid #39516e;border-radius:11px;box-shadow:-3px 5px 9px #0006;overflow:hidden}.inner{opacity:.67;transform:translate(.125px,.375px);background:#f0743a87;border-radius:7px;padding:6px}.small{background:linear-gradient(130deg,#ffd08e,#193e4700);height:31px}</style><div class=card>Immutable bitmap · 012345<div class=inner>Partial alpha and rounded clip<div class=small></div></div></div>";
    for(CFStringRef profile : {kCGColorSpaceSRGB, kCGColorSpaceDisplayP3, kCGColorSpaceLinearSRGB})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            for(CGFloat phase : {0.0, .125, -.375})
                @autoreleasepool
                {
                    CGColorSpaceRef space = CGColorSpaceCreateWithName(profile);
                    LHDocument *document = [[LHDocument alloc] initWithHTML:markup css:nil];
                    [document layoutWithMaxWidth:260 preferredWidth:260];
                    const size_t width = size_t(260 * scale), height = size_t(150 * scale);
                    LHImmutableBitmap actual(width, height, space), reference(width, height, space);
                    for(CGContextRef context : {actual.context(), reference.context()})
                    {
                        XCTAssertTrue(context != nullptr);
                        if(!context)
                            continue;
                        CGContextTranslateCTM(context, 0, height);
                        CGContextScaleCTM(context, scale, -scale);
                        CGContextTranslateCTM(context, -phase, phase * 2);
                        CGContextClipToRect(context, CGRectMake(phase + .25, -.5, 249.5, 141.25));
                        CGContextSetAllowsFontSmoothing(context, false);
                        [document drawInContext:context origin:CGPointZero clip:CGRectMake(-1, -1, 262, 152)];
                    }
                    CGImageRef image = actual.takeImage(), expected = reference.takeImage(false);
                    document = nil;
                    [self assertImage:image equals:expected label:[NSString stringWithFormat:@"%@ scale=%g phase=%g", profile, scale, phase]];
                    if(image)
                        CGImageRelease(image);
                    if(expected)
                        CGImageRelease(expected);
                    CGColorSpaceRelease(space);
                }
}

- (void)testRetainedImagesAndCropsOwnImmutableBytes
{
    const size_t contexts = LHImmutableBitmap::retainedContexts();
    const size_t bytes = LHImmutableBitmap::retainedBytes();
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceDisplayP3);
    std::vector<CGImageRef> images;
    NSMutableArray<NSData *> *expected = [NSMutableArray array];
    size_t expectedBytes = 0;
    for(unsigned index = 0; index < 32; index++)
    {
        LHImmutableBitmap original(73, 61, space);
        LHImmutableBitmap bitmap(std::move(original));
        XCTAssertTrue(original.context() == nullptr);
        XCTAssertTrue(bitmap.context() != nullptr);
        if(!bitmap.context())
            continue;
        ImmutablePattern(bitmap.context(), index, true);
        const size_t imageBytes = CGBitmapContextGetBytesPerRow(bitmap.context()) * 61;
        CGImageRef image = bitmap.takeImage();
        XCTAssertTrue(image != nullptr);
        if(image)
        {
            expectedBytes += imageBytes;
            images.push_back(image);
            [expected addObject:ImmutableBytes(image)];
        }
    }
    CGColorSpaceRelease(space);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts + images.size());
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes + expectedBytes);
    for(size_t index = 0; index < images.size(); index++)
        XCTAssertEqualObjects(ImmutableBytes(images[index]), expected[index]);
    CGImageRef crop = images.empty() ? nullptr : CGImageCreateWithImageInRect(images[0], CGRectMake(3, 4, 15, 17));
    NSData *cropBytes = ImmutableBytes(crop);
    for(CGImageRef image : images)
        CGImageRelease(image);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts + (crop ? 1 : 0));
    XCTAssertEqualObjects(ImmutableBytes(crop), cropBytes);
    if(crop)
        CGImageRelease(crop);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes);
}

- (void)testUnsupportedFormatAndInvalidInputsKeepSafeOwnership
{
    const size_t count = LHImmutableBitmap::retainedContexts();
    LHImmutableBitmap empty(nullptr), invalid(0, 12, nullptr);
    XCTAssertTrue(empty.takeImage() == nullptr && invalid.takeImage() == nullptr);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    LHImmutableBitmap badStride(12, 12, space, 4);
    XCTAssertTrue(badStride.context() == nullptr);
    CGContextRef context = CGBitmapContextCreate(nullptr, 13, 9, 8, 0, space, kCGImageAlphaPremultipliedLast);
    XCTAssertTrue(context != nullptr);
    if(context)
    {
        CGContextSetRGBFillColor(context, .2, .4, .7, .6);
        CGContextFillRect(context, CGRectMake(1, 2, 9, 5));
        CGImageRef expected = CGBitmapContextCreateImage(context);
        LHImmutableBitmap bitmap(context);
        CGImageRef image = bitmap.takeImage();
        [self assertImage:image equals:expected label:@"unsupported format fallback"];
        if(image)
            CGImageRelease(image);
        if(expected)
            CGImageRelease(expected);
    }
    CGColorSpaceRelease(space);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), count);
}

- (void)testScrollCapturesPreserveBytesMetadataAndOffsets
{
    const size_t contexts = LHImmutableBitmap::retainedContexts(), bytes = LHImmutableBitmap::retainedBytes();
    for(CFStringRef profile : {kCGColorSpaceSRGB, kCGColorSpaceDisplayP3, kCGColorSpaceLinearSRGB})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            @autoreleasepool
            {
                CGColorSpaceRef space = CGColorSpaceCreateWithName(profile);
                LHDocument *document = ImmutableScrollDocument(space);
                const CGPoint offset = [document setScrollOffset:CGPointMake(17.25, 11.5) attribute:@"id" value:@"scroll"];
                XCTAssertGreaterThan(offset.x, 0);
                XCTAssertGreaterThan(offset.y, 0);
                for(CGRect rect : {CGRectMake(-1 / scale, -1 / scale, 302, 98), CGRectMake(511.5, 255.5, 140.5, 97.25), CGRectMake(.125, .375, 9.25, 7.5)})
                {
                    CGImageRef actual = ImmutableScrollCapture(document, rect, scale, true);
                    const size_t retained = LHImmutableBitmap::retainedContexts();
                    CGImageRef expected = ImmutableScrollCapture(document, rect, scale, false);
                    XCTAssertEqual(retained, contexts + (actual ? 1 : 0));
                    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), retained);
                    [self assertImage:actual equals:expected label:[NSString stringWithFormat:@"scroll %@ scale=%g rect=%@", profile, scale, NSStringFromRect(rect)]];
                    NSValue *savedOffset = [document scrollersByAttribute:@"id"][@"scroll"][@"offset"];
                    XCTAssertEqualWithAccuracy(savedOffset.pointValue.x, offset.x, .0001);
                    XCTAssertEqualWithAccuracy(savedOffset.pointValue.y, offset.y, .0001);
                    if(actual)
                        CGImageRelease(actual);
                    if(expected)
                        CGImageRelease(expected);
                }
                CGColorSpaceRelease(space);
            }
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes);
}

- (void)testScrollImagesSurviveFurtherCapturesConsumptionRestorationAndDocumentDestruction
{
    const size_t contexts = LHImmutableBitmap::retainedContexts(), bytes = LHImmutableBitmap::retainedBytes();
    std::vector<CGImageRef> retained;
    NSMutableArray<NSData *> *expectedBytes = [NSMutableArray array];
    __weak LHDocument *oldDocument;
    __weak LHDocument *newDocument;
    @autoreleasepool
    {
        CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceDisplayP3);
        LHDocument *document = ImmutableScrollDocument(space);
        oldDocument = document;
        for(unsigned step = 0; step < 3; ++step)
        {
            const CGRect rect = CGRectMake(step * 17.125 - .5, step * 5.375 - .5, 302, 98);
            CGImageRef actual = ImmutableScrollCapture(document, rect, 2, true);
            CGImageRef expected = ImmutableScrollCapture(document, rect, 2, false);
            [self assertImage:actual equals:expected label:@"retained source capture"];
            if(actual && expected)
            {
                retained.push_back(actual);
                [expectedBytes addObject:ImmutableBytes(expected)];
            }
            else if(actual)
                CGImageRelease(actual);
            if(expected)
                CGImageRelease(expected);
        }
        LHDocument *next = [[LHDocument alloc] initWithPrevious:document styles:@{@"track":@"transform:translate(-27.375px,-3.125px)"} attribute:@"id" css:nil];
        XCTAssertNotNil(next);
        XCTAssertTrue(next.reusedPrevious);
        next.colorSpace = space;
        [next layoutWithMaxWidth:280 preferredWidth:280];
        newDocument = next;
        __block NSUInteger checks = 0;
        CGImageRef canceled = [document copyScrollContentIfCurrentWithAttribute:@"id" value:@"scroll" rect:CGRectMake(-.5, -.5, 302, 98) scale:2 shouldContinue:^BOOL { ++checks; return YES; }];
        XCTAssertTrue(canceled == nullptr);
        XCTAssertEqual(checks, 0u);
        if(canceled)
            CGImageRelease(canceled);
        for(LHDocument *source in @[next ?: document, document])
        {
            CGImageRef actual = ImmutableScrollCapture(source, CGRectMake(-.5, -.5, 302, 98), 2, true);
            CGImageRef expected = ImmutableScrollCapture(source, CGRectMake(-.5, -.5, 302, 98), 2, false);
            [self assertImage:actual equals:expected label:source == document ? @"restored source" : @"consuming document"];
            if(source == document && expectedBytes.count > 0)
                XCTAssertEqualObjects(ImmutableBytes(actual), expectedBytes[0]);
            if(actual && expected)
            {
                retained.push_back(actual);
                [expectedBytes addObject:ImmutableBytes(expected)];
            }
            else if(actual)
                CGImageRelease(actual);
            if(expected)
                CGImageRelease(expected);
        }
        CGColorSpaceRelease(space);
    }
    XCTAssertNil(oldDocument);
    XCTAssertNil(newDocument);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts + retained.size());
    for(size_t index = 0; index < retained.size(); ++index)
    {
        XCTAssertEqualObjects(ImmutableBytes(retained[index]), expectedBytes[index], @"retained frame=%zu", index);
        CGImageRelease(retained[index]);
    }
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes);
}

- (void)testCanceledAndInvalidScrollCapturesDoNotRetainBitmapStorage
{
    ImmutableScrollMode mode(true);
    const size_t contexts = LHImmutableBitmap::retainedContexts(), bytes = LHImmutableBitmap::retainedBytes();
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    LHDocument *document = ImmutableScrollDocument(space);
    __block NSUInteger checks = 0;
    CGImageRef canceled = [document copyScrollContentIfCurrentWithAttribute:@"id" value:@"scroll" rect:CGRectMake(-.5, -.5, 302, 98) scale:2 shouldContinue:^BOOL { ++checks; return NO; }];
    XCTAssertTrue(canceled == nullptr);
    XCTAssertEqual(checks, 1u);
    if(canceled)
        CGImageRelease(canceled);
    for(NSString *value in @[@"missing", @"track"])
    {
        CGImageRef invalid = [document copyScrollContentIfCurrentWithAttribute:@"id" value:value rect:CGRectMake(0, 0, 302, 98) scale:2 shouldContinue:^BOOL { return YES; }];
        XCTAssertTrue(invalid == nullptr);
        if(invalid)
            CGImageRelease(invalid);
    }
    for(CGRect rect : {CGRectZero, CGRectMake(0, 0, 8193, 1)})
    {
        CGImageRef invalid = [document copyScrollContentWithAttribute:@"id" value:@"scroll" rect:rect scale:1];
        XCTAssertTrue(invalid == nullptr);
        if(invalid)
            CGImageRelease(invalid);
    }
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes);
    CGImageRef current = [document copyScrollContentIfCurrentWithAttribute:@"id" value:@"scroll" rect:CGRectMake(-.5, -.5, 302, 98) scale:2 shouldContinue:^BOOL { return YES; }];
    CGImageRef expected = ImmutableScrollCapture(document, CGRectMake(-.5, -.5, 302, 98), 2, false);
    [self assertImage:current equals:expected label:@"current after cancellation"];
    if(current)
        CGImageRelease(current);
    if(expected)
        CGImageRelease(expected);
    CGColorSpaceRelease(space);
    XCTAssertEqual(LHImmutableBitmap::retainedContexts(), contexts);
    XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytes);
}

- (void)testSnapshotOwnershipFixedWorkProbe
{
    XCTSkipIf(!getenv("TWIDGET_IMMUTABLE_BITMAP_BENCH"), @"Run separately while other renderer workloads are idle");
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    for(CGSize size : {CGSizeMake(8, 8), CGSizeMake(302, 34), CGSizeMake(514, 258), CGSizeMake(1025, 513)})
        for(bool dense : {false, true})
        {
            for(unsigned seed : {0u, 47u, 95u})
                @autoreleasepool
                {
                    LHImmutableBitmap actual(size.width, size.height, space), reference(size.width, size.height, space);
                    XCTAssertTrue(actual.context() && reference.context());
                    if(!actual.context() || !reference.context())
                        continue;
                    ImmutablePattern(actual.context(), seed, dense);
                    ImmutablePattern(reference.context(), seed, dense);
                    CGImageRef actualImage = actual.takeImage(), referenceImage = reference.takeImage(false);
                    [self assertImage:actualImage equals:referenceImage label:@"probe source parity"];
                    LHImmutableBitmap actualTarget(size.width, size.height, space), referenceTarget(size.width, size.height, space);
                    if(actualImage && referenceImage && actualTarget.context() && referenceTarget.context())
                    {
                        CGContextSetBlendMode(actualTarget.context(), kCGBlendModeCopy);
                        CGContextSetBlendMode(referenceTarget.context(), kCGBlendModeCopy);
                        CGContextDrawImage(actualTarget.context(), CGRectMake(0, 0, size.width, size.height), actualImage);
                        CGContextDrawImage(referenceTarget.context(), CGRectMake(0, 0, size.width, size.height), referenceImage);
                        CGImageRef actualDrawn = actualTarget.takeImage(false), referenceDrawn = referenceTarget.takeImage(false);
                        [self assertImage:actualDrawn equals:referenceDrawn label:@"probe drawn parity"];
                        if(actualDrawn)
                            CGImageRelease(actualDrawn);
                        if(referenceDrawn)
                            CGImageRelease(referenceDrawn);
                    }
                    if(actualImage)
                        CGImageRelease(actualImage);
                    if(referenceImage)
                        CGImageRelease(referenceImage);
                }
            for(bool immutable : {false, true, true, false})
            {
                const size_t countBefore = LHImmutableBitmap::retainedContexts(), bytesBefore = LHImmutableBitmap::retainedBytes();
                const auto before = ImmutableMemory();
                uint64_t snapshotFootprint = before.phys_footprint, providerFootprint = before.phys_footprint;
                uint64_t consumerFootprint = before.phys_footprint, releasedProviderFootprint = before.phys_footprint;
                uint64_t snapshotResident = before.resident_size, providerResident = before.resident_size, consumerResident = before.resident_size;
                task_vm_info_data_t peakConsumer = before;
                size_t peakBytes = bytesBefore;
                size_t imageLogicalBytes = 0, consumedImages = 0;
                double setupCPU = 0, paintCPU = 0, snapshotCPU = 0, snapshotWall = 0, providerCPU = 0, drawCPU = 0, releaseCPU = 0;
                bool complete = true;
                struct Image
                {
                    CGImageRef image;
                    uint32_t crc;
                };
                std::vector<Image> retained;
                CGContextRef target = CGBitmapContextCreate(nullptr, size.width, size.height, 8, 0, space,
                    kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
                XCTAssertTrue(target != nullptr);
                if(!target)
                    continue;
                CGContextSetBlendMode(target, kCGBlendModeCopy);
                CGContextSetInterpolationQuality(target, kCGInterpolationNone);
                for(unsigned batch = 0; batch < 12; batch++)
                {
                    std::vector<LHImmutableBitmap> bitmaps;
                    std::array<uint32_t, 8> expectedCRC{};
                    bitmaps.reserve(8);
                    double cpu = ImmutableCPU();
                    for(unsigned index = 0; index < 8; index++)
                        bitmaps.emplace_back(size.width, size.height, space);
                    setupCPU += ImmutableCPU() - cpu;
                    cpu = ImmutableCPU();
                    for(unsigned index = 0; index < bitmaps.size(); index++)
                        if(bitmaps[index].context())
                            ImmutablePattern(bitmaps[index].context(), batch * 8 + index, dense);
                        else
                            complete = false;
                    paintCPU += ImmutableCPU() - cpu;
                    for(unsigned index = 0; index < bitmaps.size(); ++index)
                        if(bitmaps[index].context())
                            expectedCRC[index] = ImmutableCRC(static_cast<const uint8_t*>(CGBitmapContextGetData(bitmaps[index].context())),
                                CGBitmapContextGetBytesPerRow(bitmaps[index].context()) * size.height);
                    cpu = ImmutableCPU();
                    const auto start = std::chrono::steady_clock::now();
                    for(unsigned index = 0; index < bitmaps.size(); ++index)
                    {
                        CGImageRef image = bitmaps[index].takeImage(immutable);
                        complete = complete && image != nullptr;
                        if(image)
                            retained.push_back({image, expectedCRC[index]});
                    }
                    snapshotWall += std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();
                    snapshotCPU += ImmutableCPU() - cpu;
                    peakBytes = std::max(peakBytes, LHImmutableBitmap::retainedBytes());
                    auto memory = ImmutableMemory();
                    snapshotFootprint = std::max<uint64_t>(snapshotFootprint, memory.phys_footprint);
                    snapshotResident = std::max<uint64_t>(snapshotResident, memory.resident_size);
                    cpu = ImmutableCPU();
                    for(const auto& image : retained)
                        CGContextDrawImage(target, CGRectMake(0, 0, size.width, size.height), image.image);
                    CGContextFlush(target);
                    drawCPU += ImmutableCPU() - cpu;
                    memory = ImmutableMemory();
                    consumerFootprint = std::max<uint64_t>(consumerFootprint, memory.phys_footprint);
                    consumerResident = std::max<uint64_t>(consumerResident, memory.resident_size);
                    if(memory.phys_footprint > peakConsumer.phys_footprint)
                        peakConsumer = memory;
                    std::vector<CFDataRef> data;
                    std::vector<uint32_t> actualCRC;
                    data.reserve(retained.size());
                    actualCRC.reserve(retained.size());
                    cpu = ImmutableCPU();
                    for(const auto& image : retained)
                    {
                        CFDataRef providerData = CGDataProviderCopyData(CGImageGetDataProvider(image.image));
                        data.push_back(providerData);
                        actualCRC.push_back(providerData ? ImmutableCRC(CFDataGetBytePtr(providerData), CFDataGetLength(providerData)) : 0);
                    }
                    providerCPU += ImmutableCPU() - cpu;
                    consumedImages += retained.size();
                    memory = ImmutableMemory();
                    providerFootprint = std::max<uint64_t>(providerFootprint, memory.phys_footprint);
                    providerResident = std::max<uint64_t>(providerResident, memory.resident_size);
                    size_t logicalBytes = 0;
                    for(size_t index = 0; index < retained.size(); ++index)
                    {
                        XCTAssertTrue(data[index] != nullptr);
                        XCTAssertEqual(actualCRC[index], retained[index].crc);
                        logicalBytes += CGImageGetBytesPerRow(retained[index].image) * CGImageGetHeight(retained[index].image);
                    }
                    imageLogicalBytes = std::max(imageLogicalBytes, logicalBytes);
                    cpu = ImmutableCPU();
                    for(CFDataRef providerData : data)
                        if(providerData)
                            CFRelease(providerData);
                    releaseCPU += ImmutableCPU() - cpu;
                    releasedProviderFootprint = std::max<uint64_t>(releasedProviderFootprint, ImmutableMemory().phys_footprint);
                    cpu = ImmutableCPU();
                    while(retained.size() > 8)
                    {
                        CGImageRelease(retained.front().image);
                        retained.erase(retained.begin());
                    }
                    releaseCPU += ImmutableCPU() - cpu;
                }
                const double cpu = ImmutableCPU();
                for(const auto& image : retained)
                    CGImageRelease(image.image);
                CGContextRelease(target);
                releaseCPU += ImmutableCPU() - cpu;
                XCTAssertTrue(complete);
                XCTAssertEqual(LHImmutableBitmap::retainedContexts(), countBefore);
                XCTAssertEqual(LHImmutableBitmap::retainedBytes(), bytesBefore);
                NSLog(@"IMMUTABLE_BITMAP_FIXED_WORK width=%.0f height=%.0f dense=%d immutable=%d images=96 retained_limit=16 consumed_images=%zu setup_cpu_ms=%.3f paint_cpu_ms=%.3f snapshot_cpu_ms=%.3f snapshot_wall_ms=%.3f provider_crc_cpu_ms=%.3f draw_cpu_ms=%.3f release_cpu_ms=%.3f render_lifecycle_cpu_ms=%.3f full_lifecycle_cpu_ms=%.3f image_logical_bytes=%zu retained_context_bytes=%zu footprint_before=%llu footprint_snapshot=%llu footprint_provider=%llu footprint_draw=%llu footprint_provider_released=%llu footprint_after=%llu resident_before=%llu resident_snapshot=%llu resident_provider=%llu resident_draw=%llu peak_internal=%llu peak_external=%llu peak_reusable=%llu peak_compressed=%llu peak_graphics_nofootprint=%lld",
                    size.width, size.height, dense, immutable, consumedImages, setupCPU * 1000, paintCPU * 1000,
                    snapshotCPU * 1000, snapshotWall * 1000, providerCPU * 1000, drawCPU * 1000, releaseCPU * 1000,
                    (setupCPU + paintCPU + snapshotCPU + drawCPU + releaseCPU) * 1000,
                    (setupCPU + paintCPU + snapshotCPU + providerCPU + drawCPU + releaseCPU) * 1000, imageLogicalBytes, peakBytes - bytesBefore,
                    (unsigned long long)before.phys_footprint, (unsigned long long)snapshotFootprint,
                    (unsigned long long)providerFootprint, (unsigned long long)consumerFootprint,
                    (unsigned long long)releasedProviderFootprint, (unsigned long long)ImmutableFootprint(),
                    (unsigned long long)before.resident_size, (unsigned long long)snapshotResident,
                    (unsigned long long)providerResident, (unsigned long long)consumerResident,
                    (unsigned long long)peakConsumer.internal, (unsigned long long)peakConsumer.external,
                    (unsigned long long)peakConsumer.reusable, (unsigned long long)peakConsumer.compressed,
                    (long long)peakConsumer.ledger_tag_graphics_nofootprint);
            }
        }
    CGColorSpaceRelease(space);
}

@end
