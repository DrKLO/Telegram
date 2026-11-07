#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <vector>

@interface CSSMathTests : XCTestCase
@end

namespace
{
    struct Bitmap
    {
        size_t               width = 0, height = 0;
        std::vector<uint8_t> rgba;

        const uint8_t* at(size_t x, size_t y) const { return &rgba[(y * width + x) * 4]; }
    };

    Bitmap Draw(NSString* html, CGFloat width, CGFloat minHeight = 1)
    {
        LHDocument* doc  = [[LHDocument alloc] initWithHTML:[@"<!doctype html><style>body{margin:0}</style>" stringByAppendingString:html] css:nil];
        CGSize      size = [doc layoutWithMaxWidth:width preferredWidth:width];
        Bitmap      out;
        CGImageRef  image = [doc copyImageWithMaxWidth:width rect:CGRectMake(0, 0, width, std::max<CGFloat>(minHeight, size.height)) scale:1];
        if(!image)
            return out;
        out.width  = CGImageGetWidth(image);
        out.height = CGImageGetHeight(image);
        out.rgba.assign(out.width * out.height * 4, 0);
        CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
        CGContextRef    ctx   = CGBitmapContextCreate(out.rgba.data(), out.width, out.height, 8, out.width * 4, space,
                                                      kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big);
        CGContextDrawImage(ctx, CGRectMake(0, 0, out.width, out.height), image);
        CGContextRelease(ctx);
        CGColorSpaceRelease(space);
        CGImageRelease(image);
        return out;
    }
}

@implementation CSSMathTests

- (void)testCalcWithNestedMaxAndEnvFallbackResolvesPadding
{
    Bitmap b = Draw(@"<div style=\"padding:0 0 0 calc(10px + max(0px, env(safe-area-inset-left, 0px)) + 5px)\"><div style=\"height:10px;background:#f00\"></div></div>", 100);
    XCTAssertGreaterThanOrEqual(b.height, 10u);
    XCTAssertEqual(b.at(13, 5)[3], 0);
    XCTAssertEqual(b.at(16, 5)[0], 255);
    XCTAssertEqual(b.at(16, 5)[3], 255);
}

- (void)testEnvWithoutFallbackIsZeroForSafeAreaInsets
{
    Bitmap b = Draw(@"<div style=\"margin-left:calc(env(safe-area-inset-left) + 20px);height:10px;background:#f00\"></div>", 100);
    XCTAssertEqual(b.at(18, 5)[3], 0);
    XCTAssertEqual(b.at(22, 5)[3], 255);
}

- (void)testCalcWithCustomPropertiesAndClamp
{
    Bitmap b = Draw(@"<div style=\"--a:4px;margin-left:calc(var(--a) * 2 + 2px);width:clamp(5px, 100px, 20px);height:10px;background:#f00\"></div>", 100);
    XCTAssertEqual(b.at(8, 5)[3], 0);
    XCTAssertEqual(b.at(12, 5)[3], 255);
    XCTAssertEqual(b.at(28, 5)[3], 255);
    XCTAssertEqual(b.at(32, 5)[3], 0);
}

- (void)testMinAndMaxPickTheExtremes
{
    Bitmap b = Draw(@"<div style=\"width:min(40px, 30px, 50px);height:max(4px, 10px);background:#f00\"></div>", 100);
    XCTAssertEqual(b.height, 10u);
    XCTAssertEqual(b.at(28, 5)[3], 255);
    XCTAssertEqual(b.at(32, 5)[3], 0);
}

- (void)testColorMixWithTransparentKeepsTheHueAndScalesAlpha
{
    Bitmap b = Draw(@"<div style=\"height:10px;background:color-mix(in srgb, #ff0000 60%, transparent)\"></div>", 20);
    const uint8_t* p = b.at(10, 5);
    XCTAssertEqualWithAccuracy(p[3], 153, 2);
    XCTAssertEqualWithAccuracy(p[0], 153, 2);
    XCTAssertEqual(p[1], 0);
    XCTAssertEqual(p[2], 0);
}

- (void)testColorMixOfTwoOpaqueColorsAveragesChannels
{
    Bitmap b = Draw(@"<div style=\"height:10px;background:color-mix(in srgb, #ff0000, #0000ff)\"></div>", 20);
    const uint8_t* p = b.at(10, 5);
    XCTAssertEqual(p[3], 255);
    XCTAssertEqualWithAccuracy(p[0], 128, 2);
    XCTAssertEqualWithAccuracy(p[2], 128, 2);
}

- (void)testColorMixInsideGradientFolds
{
    Bitmap b = Draw(@"<div style=\"height:10px;background:linear-gradient(color-mix(in srgb, #0000ff 50%, #0000ff), #0000ff)\"></div>", 20);
    XCTAssertEqual(b.at(10, 5)[2], 255);
    XCTAssertEqual(b.at(10, 5)[3], 255);
}

- (void)testMainAndSearchAreBlocksWithPadding
{
    for(NSString* tag in @[@"main", @"search"])
    {
        Bitmap b = Draw([NSString stringWithFormat:@"<%@ style=\"padding:0 16px\"><div style=\"height:10px;background:#f00\"></div></%@>", tag, tag], 100);
        XCTAssertGreaterThanOrEqual(b.height, 10u, @"%@", tag);
        XCTAssertEqual(b.at(14, 5)[3], 0, @"%@", tag);
        XCTAssertEqual(b.at(18, 5)[3], 255, @"%@", tag);
        XCTAssertEqual(b.at(86, 5)[3], 0, @"%@", tag);
    }
}

- (void)testMixedUnitCalcIsLeftUnresolved
{
    Bitmap b = Draw(@"<div style=\"width:30px;width:calc(100% - 10px);height:10px;background:#f00\"></div>", 100);
    XCTAssertGreaterThanOrEqual(b.height, 10u);
}

@end

@interface FixedClipTests : XCTestCase
@end

@implementation FixedClipTests

- (void)testFixedElementsEscapeAncestorOverflowClips
{
    Bitmap b = Draw(@"<style>body{height:20px;overflow:hidden}</style><div style='height:100px'><div style='position:fixed;left:0;top:40px;width:20px;height:20px;background:#f00'></div>"
                    @"<div style='position:fixed;left:30px;top:40px;width:20px;height:20px;background:#00f;z-index:2'></div></div>", 60, 100);
    XCTAssertGreaterThanOrEqual(b.height, 60u);
    XCTAssertEqual(b.at(10, 50)[0], 255);
    XCTAssertEqual(b.at(10, 50)[3], 255);
    XCTAssertEqual(b.at(40, 50)[2], 255);
    XCTAssertEqual(b.at(40, 50)[3], 255);
}

- (void)testFixedElementsInsideTransformsStayClipped
{
    Bitmap b = Draw(@"<div style='height:100px'><div style='height:20px;overflow:hidden'><div style='transform:translateX(0)'><div style='position:fixed;left:0;top:40px;width:20px;height:20px;background:#f00'></div></div></div></div>", 60, 100);
    XCTAssertEqual(b.at(10, 50)[3], 0);
}

@end

@interface OverflowPaddingTests : XCTestCase
@end

@implementation OverflowPaddingTests

- (void)testOverflowClipsAtThePaddingBox
{
    Bitmap b = Draw(@"<div style='width:60px;height:40px;padding:0 0 20px 10px;overflow:hidden'><div style='height:100px;background:#f00'></div></div>", 80, 70);
    XCTAssertEqual(b.at(30, 30)[0], 255);
    XCTAssertEqual(b.at(30, 50)[0], 255);
    XCTAssertEqual(b.at(30, 50)[3], 255);
    XCTAssertEqual(b.at(30, 65)[3], 0);
    XCTAssertEqual(b.at(5, 30)[3], 0);
}

- (void)testScrollersReportThePaddingBoxAndPaddedContent
{
    LHDocument* doc = [[LHDocument alloc] initWithHTML:@"<!doctype html><style>body{margin:0}</style><div data-t=s style='height:100px;padding:0 16px 30px;overflow-y:auto'><div style='height:200px'></div></div>" css:nil];
    [doc layoutWithMaxWidth:200 preferredWidth:200];
    NSDictionary* info = [doc scrollersByAttribute:@"data-t"][@"s"];
    XCTAssertNotNil(info);
    NSRect viewport = [info[@"viewport"] rectValue];
    NSSize content  = [info[@"content"] sizeValue];
    XCTAssertEqualWithAccuracy(viewport.size.height, 130, 0.01);
    XCTAssertEqualWithAccuracy(viewport.size.width, 200, 0.01);
    XCTAssertEqualWithAccuracy(viewport.origin.x, 0, 0.01);
    XCTAssertEqualWithAccuracy(content.height, 230, 0.01);
}

@end
