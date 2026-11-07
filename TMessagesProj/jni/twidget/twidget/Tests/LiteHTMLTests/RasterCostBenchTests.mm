#import <XCTest/XCTest.h>
#import <QuartzCore/QuartzCore.h>
#import "LHDocument.h"

#include <cmath>

@interface RasterCostBenchTests : XCTestCase
@end

@implementation RasterCostBenchTests

static NSArray<LHDocument *> *SwarmFrames(int tiles, int orbs, int frames)
{
    NSMutableArray<LHDocument *> *documents = [NSMutableArray array];
    double w = 366, h = 260;
    int cols = (int)std::ceil(std::sqrt(tiles * w / h)), rows = cols ? (tiles + cols - 1) / cols : 0;
    for(int f = 1; f <= frames; f++)
    {
        double t = f / 60.0;
        NSMutableString *html = [NSMutableString stringWithString:@"<style>body{margin:0;width:400px;height:300px}.stage{position:relative;height:260px;border-radius:14px;overflow:hidden;background:#eef0f6}.t{position:absolute;left:0;top:0;width:9px;height:9px;border-radius:3px}.orb{position:absolute;left:0;top:0;width:92px;height:92px;border-radius:46px;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.35);box-shadow:0 8px 30px rgba(0,0,0,.25)}</style><div class=stage>"];
        for(int i = 0; i < tiles; i++)
        {
            int c = i % cols, r = i / cols;
            double ph = c * 0.35 + r * 0.22;
            double x = (c + 0.5) * w / cols - 4.5 + std::cos(t * 1.3 + ph * 0.7) * 6, y = (r + 0.5) * h / rows - 4.5 + std::sin(t * 2.2 + ph) * 14;
            double s = 0.75 + 0.35 * std::sin(t * 3 + ph), rot = std::sin(t + ph) * 40;
            [html appendFormat:@"<div class=t style=\"background-color:hsl(%d,80%%,58%%);transform:translate(%.1fpx,%.1fpx) rotate(%.1fdeg) scale(%.2f)\"></div>", 360 * i / tiles, x, y, rot, s];
        }
        for(int i = 0; i < orbs; i++)
        {
            double a = i * 2.1 + f * 0.012 * (1 + i * 0.4);
            [html appendFormat:@"<div class=orb style=\"transform:translate(%.1fpx,%.1fpx) scale(%.2f)\"></div>", w / 2 - 46 + std::cos(a) * w * 0.33, h / 2 - 46 + std::sin(a * 1.3) * 70, 0.8 + 0.25 * std::sin(a * 2)];
        }
        [html appendString:@"</div>"];
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        [document layoutWithMaxWidth:400 preferredWidth:400];
        [documents addObject:document];
    }
    return documents;
}

static double DrawFrames(NSArray<LHDocument *> *documents, int repeats)
{
    CGFloat scale = 2;
    size_t width = 800, height = 600;
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef context = CGBitmapContextCreate(nullptr, width, height, 8, width * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(context, 0, height);
    CGContextScaleCTM(context, scale, -scale);
    CGContextSetAllowsFontSmoothing(context, false);
    double t0 = CACurrentMediaTime();
    for(int k = 0; k < repeats; k++)
        for(LHDocument *document in documents)
        {
            CGContextClearRect(context, CGRectMake(0, 0, 400, 300));
            [document drawInContext:context origin:CGPointZero clip:CGRectMake(0, 0, 400, 300)];
        }
    double elapsed = CACurrentMediaTime() - t0;
    CGContextRelease(context);
    return elapsed * 1e6 / (repeats * documents.count);
}

- (void)testSwarmRasterCost
{
    if(!getenv("TWIDGET_RASTER_BENCH"))
        return;
    int frames = 120;
    NSArray *tilesOnly = SwarmFrames(800, 0, frames);
    NSArray *orbsOnly = SwarmFrames(0, 3, frames);
    NSArray *both = SwarmFrames(800, 3, frames);
    DrawFrames(both, 1);
    NSLog(@"RASTERBENCH tiles=%.1fus orbs=%.1fus both=%.1fus", DrawFrames(tilesOnly, getenv("RB_REP") ? atoi(getenv("RB_REP")) : 3), DrawFrames(orbsOnly, 3), DrawFrames(both, 3));
}

@end
