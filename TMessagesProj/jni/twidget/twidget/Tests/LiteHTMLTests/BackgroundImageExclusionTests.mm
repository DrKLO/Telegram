#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <cmath>

@interface BackgroundImageExclusionTests : XCTestCase
@end

@implementation BackgroundImageExclusionTests

static NSData *Tile(LHDocument *document, CGFloat width, CGFloat height)
{
    size_t pw = (size_t)std::ceil(width * 2), ph = (size_t)std::ceil(height * 2);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef ctx = CGBitmapContextCreate(nullptr, pw, ph, 8, pw * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, 2, -2);
    [document drawInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, width, height)];
    NSData *data = [NSData dataWithBytes:CGBitmapContextGetData(ctx) length:pw * ph * 4];
    CGContextRelease(ctx);
    return data;
}

static NSData *Pixels(CGImageRef image)
{
    return CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
}

- (void)testExcludedGradientLeavesTilesButNotSnapshots
{
    NSString *gradient = @"<style>body{margin:0}.sk{width:200px;height:20px;border-radius:6px;background-color:#ccc;background-image:linear-gradient(90deg,rgba(0,0,0,0),rgba(0,0,0,.5),rgba(0,0,0,0));background-size:40% 100%;background-repeat:no-repeat;background-position:30% 0}</style><div class=\"sk\" data-twid=\"5\"></div>";
    NSString *flat = @"<style>body{margin:0}.sk{width:200px;height:20px;border-radius:6px;background-color:#ccc}</style><div class=\"sk\" data-twid=\"5\"></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:gradient css:nil];
    LHDocument *plain = [[LHDocument alloc] initWithHTML:flat css:nil];
    [document layoutWithMaxWidth:220 preferredWidth:220];
    [plain layoutWithMaxWidth:220 preferredWidth:220];
    CGRect rect = CGRectMake(0, 0, 220, 30);
    CGImageRef before = [document copyImageWithMaxWidth:220 rect:rect scale:2];
    NSData *tileBefore = Tile(document, 220, 30);
    [document setBackgroundImageExclusions:[NSSet setWithObject:@"5"] attribute:@"data-twid"];
    NSData *tileAfter = Tile(document, 220, 30);
    CGImageRef after = [document copyImageWithMaxWidth:220 rect:rect scale:2];
    XCTAssertNotEqualObjects(tileBefore, tileAfter);
    XCTAssertEqualObjects(tileAfter, Tile(plain, 220, 30));
    XCTAssertEqualObjects(Pixels(before), Pixels(after));
    CGImageRelease(before);
    CGImageRelease(after);
}

- (void)testTileOverExcludedRenderMatchesFullRender
{
    NSString *html = @"<style>body{margin:0}.sk{width:200px;height:20px;margin:7px 3px;padding:2px;border-radius:6px;background-color:#ccc;background-image:linear-gradient(90deg,rgba(0,0,0,0),rgba(0,0,0,.5),rgba(0,0,0,0));background-size:40% 100%;background-repeat:no-repeat;background-position:30% 0}</style><div class=\"sk\" data-twid=\"5\"></div>";
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:220 preferredWidth:220];
    NSData *full = Tile(document, 220, 40);
    LHBackgroundTile *tile = [document backgroundTileWithAttribute:@"data-twid" value:@"5" maxWidth:220 scale:2];
    XCTAssertNotNil(tile);
    XCTAssertEqualWithAccuracy(tile.tile.size.width, 0.4 * 204, 0.01);
    XCTAssertEqualWithAccuracy(tile.tile.origin.x, tile.area.origin.x + 0.3 * (tile.area.size.width - tile.tile.size.width), 0.01);
    [document setBackgroundImageExclusions:[NSSet setWithObject:@"5"] attribute:@"data-twid"];
    size_t pw = 440, ph = 80;
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef ctx = CGBitmapContextCreate(nullptr, pw, ph, 8, pw * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, 2, -2);
    [document drawInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, 220, 40)];
    CGContextSaveGState(ctx);
    CGPathRef path = CGPathCreateWithRoundedRect(tile.clip, [tile radius:0], [tile radius:1], nullptr);
    CGContextAddPath(ctx, path);
    CGContextClip(ctx);
    CGPathRelease(path);
    CGContextTranslateCTM(ctx, tile.tile.origin.x, tile.tile.origin.y + tile.tile.size.height);
    CGContextScaleCTM(ctx, 1, -1);
    CGContextDrawImage(ctx, CGRectMake(0, 0, tile.tile.size.width, tile.tile.size.height), tile.image);
    CGContextRestoreGState(ctx);
    const uint8_t *a = (const uint8_t *)full.bytes, *b = (const uint8_t *)CGBitmapContextGetData(ctx);
    int worst = 0;
    for(size_t i = 0; i < pw * ph * 4; i++)
        worst = MAX(worst, abs((int)a[i] - (int)b[i]));
    CGContextRelease(ctx);
    XCTAssertLessThanOrEqual(worst, 24);
}

- (void)testSnapshotsOfAnUnchangedDocumentAreIdentical
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<style>body{margin:0}.sk{width:200px;height:20px;border-radius:6px;background-color:#ccc;background-image:linear-gradient(90deg,rgba(0,0,0,0),rgba(0,0,0,.5),rgba(0,0,0,0));background-size:40% 100%;background-repeat:no-repeat}</style><div class=\"sk\" data-twid=\"5\">text</div><p>more text</p>" css:nil];
    [document layoutWithMaxWidth:220 preferredWidth:220];
    CGRect rect = CGRectMake(0, 0, 220, 60);
    CGImageRef a = [document copyImageWithMaxWidth:220 rect:rect scale:2];
    CGImageRef b = [document copyImageWithMaxWidth:220 rect:rect scale:2];
    CGImageRef c = [document copyImageWithBackgroundPositionX:70 percent:YES attribute:@"data-twid" value:@"5" maxWidth:220 rect:rect scale:2];
    CGImageRef d = [document copyImageWithMaxWidth:220 rect:rect scale:2];
    XCTAssertTrue(a && b && c && d);
    XCTAssertEqualObjects(Pixels(a), Pixels(b));
    XCTAssertEqualObjects(Pixels(a), Pixels(d));
    XCTAssertNotEqualObjects(Pixels(a), Pixels(c));
    XCTAssertEqual(document.restoreMismatches, 0);
    CGImageRelease(a);
    CGImageRelease(b);
    CGImageRelease(c);
    CGImageRelease(d);
}

@end
