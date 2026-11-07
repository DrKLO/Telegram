#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <cmath>
#include <initializer_list>

@interface ShadowRasterTests : XCTestCase
@end

@implementation ShadowRasterTests

static NSData *RenderShadow(LHDocument *document, CGRect rect, CGFloat scale, bool flipped = true)
{
    size_t width = (size_t)std::llround(rect.size.width * scale);
    size_t height = (size_t)std::llround(rect.size.height * scale);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef context = CGBitmapContextCreate(nullptr, width, height, 8, width * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    if(flipped)
        CGContextTranslateCTM(context, 0, height);
    CGContextScaleCTM(context, scale, flipped ? -scale : scale);
    CGContextTranslateCTM(context, -rect.origin.x, -rect.origin.y);
    CGContextSetAllowsFontSmoothing(context, false);
    [document drawInContext:context origin:CGPointZero clip:rect];
    NSData *result = [NSData dataWithBytes:CGBitmapContextGetData(context) length:width * height * 4];
    CGContextRelease(context);
    return result;
}

static LHDocument *ShadowDocument(NSString *style)
{
    NSString *html = [NSString stringWithFormat:@"<style>body{margin:0;width:640px;height:420px}div{position:absolute;left:68px;top:90px;width:504px;height:154px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.1);%@}</style><div></div>", style];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:640 preferredWidth:640];
    return document;
}

- (void)tearDown
{
    unsetenv("LH_NO_SHADOW_SLICES");
    unsetenv("LH_NO_SHADOW_RESIZE_CACHE");
    [LHDocument purgeCaches];
    [super tearDown];
}

- (void)testScaledTilesReuseOneShadowRaster
{
    LHDocument *document = ShadowDocument(@"transform:scale(1.02)");
    NSMutableArray<NSData *> *cold = [NSMutableArray array];
    for(int y = 0; y < 3; y++)
        for(int x = 0; x < 5; x++)
        {
            [LHDocument purgeCaches];
            [cold addObject:RenderShadow(document, CGRectMake(x * 128, y * 128, 128, 128), 2)];
        }
    [LHDocument purgeCaches];
    RenderShadow(document, CGRectMake(0, 0, 640, 420), 2);
    NSUInteger single = [LHDocument rasterCacheBytes];
    XCTAssertGreaterThan(single, 0u);
    for(int y = 0; y < 3; y++)
        for(int x = 0; x < 5; x++)
            XCTAssertEqualObjects(cold[y * 5 + x], RenderShadow(document, CGRectMake(x * 128, y * 128, 128, 128), 2));
    XCTAssertEqual([LHDocument rasterCacheBytes], single);
}

- (void)testSlicedShadowsMatchFullRasterThroughLayoutAnimation
{
    NSUInteger maximum = 0, differing = 0, total = 0;
    for(CGFloat scale : {1.0, 1.5, 2.0})
        for(NSString *variant in @[@"", @"border-radius:8px 31px 14px 23px;box-shadow:-7px 6px 11px 4px #843aab", @"box-shadow:12px -9px 18px -3px #1686c8", @"box-shadow:0 1px 2px #1431,0 10px 30px #0002", @"box-shadow:0 6px 16px #0008 inset", @"border-radius:80% 20% / 30% 65%", @"width:236px;box-shadow:32px -24px 17px 7px #ffc028", @"box-shadow:-34px 12px 5px -10px #0cc", @"transform:rotate(3deg);box-shadow:0 6px 16px #0008", @"transform:scale(-1.02,1.013)", @"width:38px;border-radius:50%;box-shadow:0 10px 30px #0004"])
            for(int step = 0; step <= 16; step++)
            {
                NSString *style = [NSString stringWithFormat:@"height:%gpx;transform:translate(%gpx,%gpx) scale(%g);%@", 70.0 + step * 5.25, step * .03125, step * .0625, 1 + step * .00125, variant];
                LHDocument *document = ShadowDocument(style);
                setenv("LH_NO_SHADOW_SLICES", "1", 1);
                NSData *expected = RenderShadow(document, CGRectMake(0, 0, 640, 420), scale);
                unsetenv("LH_NO_SHADOW_SLICES");
                NSData *actual = RenderShadow(document, CGRectMake(0, 0, 640, 420), scale);
                XCTAssertEqual(expected.length, actual.length);
                const uint8_t *a = (const uint8_t *)expected.bytes, *b = (const uint8_t *)actual.bytes;
                NSUInteger frameMaximum = 0;
                for(NSUInteger i = 0; i < actual.length; i++)
                {
                    NSUInteger delta = abs((int)a[i] - (int)b[i]);
                    frameMaximum = MAX(frameMaximum, delta);
                    differing += delta != 0;
                }
                maximum = MAX(maximum, frameMaximum);
                total += actual.length;
                XCTAssertLessThanOrEqual(frameMaximum, 1u, @"scale %g %@", scale, style);
            }
    NSLog(@"SHADOW_PARITY max=%lu differing=%lu total=%lu", (unsigned long)maximum, (unsigned long)differing, (unsigned long)total);
}

- (void)testTallSlicedShadowsMatchFullRaster
{
    NSUInteger maximum = 0, differing = 0, total = 0;
    for(bool flipped : {false, true})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            for(NSString *variant in @[@"", @"border-radius:12px 21px 7px 3px / 9px 24px 11px 18px;box-shadow:-11px 7px 9px 3px #bf528ecc", @"box-shadow:7px -17px 14px #15c8", @"box-shadow:0 3px 12px -2px #ae68", @"transform:scale(1.03,-1.02)"])
                for(int step = 0; step <= 12; step++)
                {
                    NSString *style = [NSString stringWithFormat:@"left:80px;top:50px;width:%gpx;height:%gpx;box-shadow:8px 0 24px #08f7;transform:translate(%gpx,%gpx) scale(%g);%@", 28 + step * 2.25, 210 + step * 5.5, step * .03125, step * .0625, 1 + step * .00125, variant];
                    LHDocument *document = ShadowDocument(style);
                    setenv("LH_NO_SHADOW_SLICES", "1", 1);
                    NSData *expected = RenderShadow(document, CGRectMake(0, 0, 640, 420), scale, flipped);
                    unsetenv("LH_NO_SHADOW_SLICES");
                    NSData *actual = RenderShadow(document, CGRectMake(0, 0, 640, 420), scale, flipped);
                    NSUInteger frameMaximum = 0;
                    const uint8_t *a = (const uint8_t *)expected.bytes, *b = (const uint8_t *)actual.bytes;
                    for(NSUInteger i = 0; i < actual.length; i++)
                    {
                        NSUInteger delta = abs((int)a[i] - (int)b[i]);
                        frameMaximum = MAX(frameMaximum, delta);
                        differing += delta != 0;
                    }
                    maximum = MAX(maximum, frameMaximum);
                    total += actual.length;
                    XCTAssertLessThanOrEqual(frameMaximum, 1u, @"flipped=%d scale=%g %@", flipped, scale, style);
                }
    NSLog(@"TALL_SHADOW_PARITY max=%lu differing=%lu total=%lu", (unsigned long)maximum, (unsigned long)differing, (unsigned long)total);
}

- (void)testFractionalPositionAndShadowChangesKeepDistinctCacheEntries
{
    for(NSString *style in @[@"transform:translate(.125px,.25px) scale(1.02)", @"height:93.5px;transform:scale(1.013)", @"box-shadow:7px 3px 20px 2px #e8349d", @"border-radius:5px 24px 40px 12px"])
    {
        [LHDocument purgeCaches];
        LHDocument *base = ShadowDocument(@"transform:scale(1.02)");
        NSData *before = RenderShadow(base, CGRectMake(0, 0, 640, 420), 2);
        LHDocument *changed = ShadowDocument(style);
        NSData *warm = RenderShadow(changed, CGRectMake(0, 0, 640, 420), 2);
        [LHDocument purgeCaches];
        NSData *cold = RenderShadow(changed, CGRectMake(0, 0, 640, 420), 2);
        XCTAssertNotEqualObjects(before, warm);
        XCTAssertEqualObjects(warm, cold);
    }
}

- (void)testResizedShadowsReuseCompactRasterWithoutChangingPixels
{
    unsetenv("LH_NO_SHADOW_SLICES");
    for(bool flipped : {false, true})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            for(bool horizontal : {false, true})
                @autoreleasepool
                {
                    NSArray<NSNumber *> *sizes = horizontal ? @[@320, @352, @400, @448, @368, @320] : @[@192, @224, @256, @288, @240, @192];
                    NSMutableArray<LHDocument *> *documents = [NSMutableArray array];
                    NSMutableArray<NSData *> *expected = [NSMutableArray array];
                    setenv("LH_NO_SHADOW_RESIZE_CACHE", "1", 1);
                    for(NSNumber *size in sizes)
                    {
                        CGFloat width = horizontal ? size.doubleValue : 48;
                        CGFloat height = horizontal ? 64 : size.doubleValue;
                        NSString *style = [NSString stringWithFormat:@"left:68.25px;top:50.125px;width:%gpx;height:%gpx;border-radius:6px;box-shadow:2px 3px 8px #3a7bd5aa", width, height];
                        LHDocument *document = ShadowDocument(style);
                        [documents addObject:document];
                        [LHDocument purgeCaches];
                        [expected addObject:RenderShadow(document, CGRectMake(0, 0, 640, 420), scale, flipped)];
                    }
                    unsetenv("LH_NO_SHADOW_RESIZE_CACHE");
                    [LHDocument purgeCaches];
                    NSUInteger single = 0;
                    for(NSUInteger index = 0; index < documents.count; index++)
                    {
                        NSData *actual = RenderShadow(documents[index], CGRectMake(0, 0, 640, 420), scale, flipped);
                        XCTAssertEqualObjects(actual, expected[index], @"horizontal=%d flipped=%d scale=%g size=%@", horizontal, flipped, scale, sizes[index]);
                        if(index == 0)
                        {
                            single = [LHDocument rasterCacheBytes];
                            XCTAssertGreaterThan(single, 0u);
                        }
                        else
                        {
                            XCTAssertEqual([LHDocument rasterCacheBytes], single, @"horizontal=%d flipped=%d scale=%g size=%@", horizontal, flipped, scale, sizes[index]);
                        }
                    }
                }
}

- (void)testResizeCacheSeparatesPhaseShadowRadiusAndStyleChanges
{
    unsetenv("LH_NO_SHADOW_SLICES");
    unsetenv("LH_NO_SHADOW_RESIZE_CACHE");
    NSString *baseStyle = @"left:68.25px;top:50.125px;width:384px;height:64px;border-radius:6px;box-shadow:2px 3px 8px #3a7bd5aa;";
    NSArray<NSString *> *variants = @[
        @"transform:translate(.125px,0)",
        @"transform:translate(0,.25px)",
        @"box-shadow:-3px 3px 8px #3a7bd5aa",
        @"box-shadow:2px -4px 8px #3a7bd5aa",
        @"box-shadow:2px 3px 10px #3a7bd5aa",
        @"box-shadow:2px 3px 8px 2px #3a7bd5aa",
        @"box-shadow:2px 3px 8px #c93b7baa",
        @"border-radius:6px 4px 3px 5px / 3px 6px 5px 4px",
        @"box-shadow:2px 3px 8px #3a7bd5aa inset",
        @"box-shadow:2px 3px 8px #3a7bd5aa,0 -2px 3px #e57399aa",
    ];
    for(bool flipped : {false, true})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            for(NSString *variant in variants)
                @autoreleasepool
                {
                    [LHDocument purgeCaches];
                    LHDocument *base = ShadowDocument(baseStyle);
                    NSData *before = RenderShadow(base, CGRectMake(0, 0, 640, 420), scale, flipped);
                    NSUInteger baseBytes = [LHDocument rasterCacheBytes];
                    XCTAssertGreaterThan(baseBytes, 0u);
                    LHDocument *changed = ShadowDocument([baseStyle stringByAppendingString:variant]);
                    NSData *warm = RenderShadow(changed, CGRectMake(0, 0, 640, 420), scale, flipped);
                    NSUInteger changedBytes = [LHDocument rasterCacheBytes];
                    XCTAssertGreaterThan(changedBytes, baseBytes, @"flipped=%d scale=%g %@", flipped, scale, variant);
                    XCTAssertNotEqualObjects(before, warm, @"flipped=%d scale=%g %@", flipped, scale, variant);
                    XCTAssertEqualObjects(before, RenderShadow(base, CGRectMake(0, 0, 640, 420), scale, flipped));
                    XCTAssertEqual([LHDocument rasterCacheBytes], changedBytes);
                    [LHDocument purgeCaches];
                    NSData *cold = RenderShadow(changed, CGRectMake(0, 0, 640, 420), scale, flipped);
                    XCTAssertEqualObjects(warm, cold, @"flipped=%d scale=%g %@", flipped, scale, variant);
                }
}

- (void)testWholeDevicePixelPositionChangesReuseShadowRaster
{
    unsetenv("LH_NO_SHADOW_SLICES");
    unsetenv("LH_NO_SHADOW_RESIZE_CACHE");
    NSString *baseStyle = @"left:68.25px;top:50.125px;width:384px;height:64px;border-radius:6px;box-shadow:2px 3px 8px #3a7bd5aa;";
    for(bool flipped : {false, true})
        for(CGFloat scale : {1.0, 1.5, 2.0})
            @autoreleasepool
            {
                [LHDocument purgeCaches];
                LHDocument *base = ShadowDocument(baseStyle);
                NSData *before = RenderShadow(base, CGRectMake(0, 0, 640, 420), scale, flipped);
                NSUInteger single = [LHDocument rasterCacheBytes];
                XCTAssertGreaterThan(single, 0u);
                LHDocument *moved = ShadowDocument([baseStyle stringByAppendingString:@"left:70.25px;top:52.125px"]);
                NSData *warm = RenderShadow(moved, CGRectMake(0, 0, 640, 420), scale, flipped);
                XCTAssertEqual([LHDocument rasterCacheBytes], single, @"flipped=%d scale=%g", flipped, scale);
                XCTAssertNotEqualObjects(before, warm, @"flipped=%d scale=%g", flipped, scale);
                [LHDocument purgeCaches];
                NSData *cold = RenderShadow(moved, CGRectMake(0, 0, 640, 420), scale, flipped);
                XCTAssertEqualObjects(warm, cold, @"flipped=%d scale=%g", flipped, scale);
            }
}

- (void)testDownsampledShadowsKeepOriginalSizesDistinct
{
    unsetenv("LH_NO_SHADOW_SLICES");
    unsetenv("LH_NO_SHADOW_RESIZE_CACHE");
    NSString *baseStyle = @"left:68px;top:90px;width:504px;height:154px;border-radius:16px;box-shadow:0 0 400px #000a;";
    NSUInteger downsampledBytes = 79u * 58u * 4u;
    for(bool flipped : {false, true})
        @autoreleasepool
        {
            [LHDocument purgeCaches];
            LHDocument *base = ShadowDocument(baseStyle);
            NSData *before = RenderShadow(base, CGRectMake(0, 0, 640, 420), 2, flipped);
            XCTAssertEqual([LHDocument rasterCacheBytes], downsampledBytes, @"flipped=%d", flipped);
            LHDocument *changed = ShadowDocument([baseStyle stringByAppendingString:@"width:505px"]);
            NSData *warm = RenderShadow(changed, CGRectMake(0, 0, 640, 420), 2, flipped);
            XCTAssertEqual([LHDocument rasterCacheBytes], downsampledBytes * 2, @"flipped=%d", flipped);
            XCTAssertEqualObjects(before, RenderShadow(base, CGRectMake(0, 0, 640, 420), 2, flipped));
            XCTAssertEqual([LHDocument rasterCacheBytes], downsampledBytes * 2);
            [LHDocument purgeCaches];
            NSData *cold = RenderShadow(changed, CGRectMake(0, 0, 640, 420), 2, flipped);
            XCTAssertEqual([LHDocument rasterCacheBytes], downsampledBytes, @"flipped=%d", flipped);
            XCTAssertEqualObjects(warm, cold, @"flipped=%d", flipped);
        }
}

@end
