#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <cmath>

@interface TextBatchingTests : XCTestCase
@end

@implementation TextBatchingTests

static NSArray<NSString *> *BatchingSeeds(void)
{
    NSMutableArray<NSString *> *seeds = [NSMutableArray array];
    NSString *dir = [[@(__FILE__) stringByDeletingLastPathComponent] stringByAppendingPathComponent:@"../../Examples/chats"];
    for(NSString *name in [[[NSFileManager defaultManager] contentsOfDirectoryAtPath:dir error:nil] sortedArrayUsingSelector:@selector(compare:)])
    {
        NSString *text = [NSString stringWithContentsOfFile:[dir stringByAppendingPathComponent:name] encoding:NSUTF8StringEncoding error:nil];
        if(text.length > 0)
            [seeds addObject:text];
    }
    NSMutableString *rows = [NSMutableString string];
    for(int r = 0; r < 40; r++)
    {
        [rows appendString:@"<tr>"];
        for(int c = 0; c < 8; c++)
            [rows appendFormat:@"<td class=\"%@\">%d.%02d</td>", (r * 3 + c) % 2 ? @"up" : @"down", 100 + r * 8 + c, (r * 7 + c * 13) % 100];
        [rows appendString:@"</tr>"];
    }
    [seeds addObject:[NSString stringWithFormat:@"<style>body{margin:0;font:11px -apple-system}td{padding:2px 4px;text-align:right}.up{color:#34c759}.down{color:#ff3b30}</style><table style=\"border-collapse:collapse;width:100%%\">%@</table>", rows]];
    [seeds addObject:@"<style>body{margin:0;font:14px -apple-system}</style><p>Tight <span style=\"letter-spacing:-6px;color:red\">overlapping</span><span style=\"margin-left:-12px;color:blue\">runs</span> and <i style=\"color:green\">italic</i> <b>bold</b> text that wraps across lines <span style=\"text-decoration:underline\">underlined</span> <span style=\"text-shadow:1px 1px 2px #000\">shadowed</span>.</p><div style=\"overflow:hidden;border-radius:9px;height:30px;background:#eef\">clipped rounded text inside a box with more words than fit</div><div style=\"opacity:.5\">half opacity text</div><div style=\"transform:rotate(3deg)\">rotated text</div>"];
    return seeds;
}

static NSData *Render(NSString *html, BOOL batching)
{
    [LHDocument setTextBatchingEnabled:batching];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    CGFloat height = MIN(MAX(document.size.height, 1), 3000);
    size_t pw = 840, ph = (size_t)std::ceil(height * 2);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef ctx = CGBitmapContextCreate(nullptr, pw, ph, 8, pw * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(ctx, 0, ph);
    CGContextScaleCTM(ctx, 2, -2);
    CGContextSetAllowsFontSmoothing(ctx, false);
    CGContextSetShouldAntialias(ctx, true);
    [document drawInContext:ctx origin:CGPointZero clip:CGRectMake(0, 0, 420, height)];
    NSData *data = [NSData dataWithBytes:CGBitmapContextGetData(ctx) length:pw * ph * 4];
    CGContextRelease(ctx);
    return data;
}

- (void)testBatchedTextIsPixelIdentical
{
    NSArray<NSString *> *seeds = BatchingSeeds();
    XCTAssertGreaterThan(seeds.count, 2u);
    for(NSString *seed in seeds)
    {
        NSData *plain   = Render(seed, NO);
        NSData *batched = Render(seed, YES);
        XCTAssertEqualObjects(plain, batched, @"%@", [seed substringToIndex:MIN(seed.length, (NSUInteger)80)]);
    }
    [LHDocument setTextBatchingEnabled:YES];
}

- (void)testBatchedGlowsMatchPerWordGlows
{
    NSString *seed = @"<style>body{margin:0;background:#123;color:#fff;font:bold 30px -apple-system}</style><p style=\"text-shadow:0 0 40px #f0f\">Glow words that wrap across a couple of lines here</p><p style=\"text-shadow:0 0 14px #0ff, 6px 6px 0 #f80\">Two shadows on these words</p>";
    long before     = [LHDocument shadowLineBatches];
    NSData *plain   = Render(seed, NO);
    long afterPlain = [LHDocument shadowLineBatches];
    NSData *batched = Render(seed, YES);
    long afterBatch = [LHDocument shadowLineBatches];
    XCTAssertEqual(afterPlain, before);
    XCTAssertGreaterThan(afterBatch, afterPlain);
    XCTAssertEqual(plain.length, batched.length);
    const uint8_t *a = (const uint8_t *)plain.bytes, *b = (const uint8_t *)batched.bytes;
    size_t pixels = plain.length / 4, off = 0;
    for(size_t i = 0; i < pixels; i++)
    {
        int d = 0;
        for(int c = 0; c < 4; c++)
            d = MAX(d, abs((int)a[i * 4 + c] - (int)b[i * 4 + c]));
        if(d > 24)
            off++;
    }
    size_t any = 0;
    for(size_t i = 0; i < plain.length; i++)
        any += a[i] != b[i];
    NSLog(@"GLOWDIFF line batches %ld, %zu of %zu pixels differ by more than 24 (%.4f%%), %zu bytes differ at all", afterBatch - afterPlain, off, pixels, 100.0 * off / pixels, any);
    XCTAssertLessThan((double)off / (double)pixels, 0.005, @"%zu of %zu pixels differ", off, pixels);
    [LHDocument setTextBatchingEnabled:YES];
}

@end
