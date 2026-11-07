#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface SourceFilterCacheTests : XCTestCase
@end

@implementation SourceFilterCacheTests

static NSString *const kBase = @"<style>body{margin:0;width:400px}#bg{position:absolute;left:0;top:0;width:400px;height:420px;background:linear-gradient(90deg,#f00,#0a0,#00f)}.f{position:absolute;width:120px;height:80px;background:#ff0;filter:blur(16px)}.g{position:absolute;left:20px;width:360px;height:110px;background:#fff6;backdrop-filter:blur(24px)}</style><div id=bg></div><div class=f style=\"left:40px;top:40px\"></div><div class=f style=\"left:220px;top:180px\"></div><div class=f style=\"left:120px;top:300px\"></div>";

static NSData *Draw(NSString *html, BOOL cache)
{
    [LHDocument setSourceFilterCacheEnabled:cache];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:400 preferredWidth:400];
    const size_t w = 800, h = 840;
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef ctx = CGBitmapContextCreate(nullptr, w, h, 8, w * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(ctx, 0, h);
    CGContextScaleCTM(ctx, 2, -2);
    [document drawInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, 400, 420)];
    NSData *data = [NSData dataWithBytes:CGBitmapContextGetData(ctx) length:w * h * 4];
    CGContextRelease(ctx);
    [LHDocument setSourceFilterCacheEnabled:YES];
    return data;
}

- (void)testBackdropSourcesReuseFilteredElementsWithinThreshold
{
    NSString *html = [kBase stringByAppendingString:@"<div class=g style=\"top:20px\"></div><div class=g style=\"top:150px\"></div><div class=g style=\"top:280px\"></div>"];
    NSData *plain = Draw(html, NO);
    long hits = [LHDocument sourceFilterCacheHits];
    NSData *cached = Draw(html, YES);
    XCTAssertGreaterThan([LHDocument sourceFilterCacheHits], hits);
    const uint8_t *a = (const uint8_t *)plain.bytes, *b = (const uint8_t *)cached.bytes;
    size_t pixels = plain.length / 4, off = 0;
    for(size_t i = 0; i < pixels; i++)
    {
        int d = 0;
        for(int c = 0; c < 4; c++)
            d = MAX(d, abs((int)a[i * 4 + c] - (int)b[i * 4 + c]));
        if(d > 24)
            off++;
    }
    NSLog(@"SOURCEDIFF hits %ld, %zu of %zu pixels differ by more than 24 (%.4f%%)", [LHDocument sourceFilterCacheHits] - hits, off, pixels, 100.0 * off / pixels);
    XCTAssertLessThan((double)off / (double)pixels, 0.005);
}

- (void)testPagesWithoutBackdropsAreByteIdentical
{
    long hits = [LHDocument sourceFilterCacheHits];
    XCTAssertEqualObjects(Draw(kBase, NO), Draw(kBase, YES));
    XCTAssertEqual([LHDocument sourceFilterCacheHits], hits);
}

@end
