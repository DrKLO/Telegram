#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <cstdlib>
#include <initializer_list>

@interface TransformedClipCullingTests : XCTestCase
@end

@implementation TransformedClipCullingTests

static NSData *Render(LHDocument *document, CGFloat scale)
{
    size_t width = (size_t)(240 * scale), height = (size_t)(200 * scale);
    CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CGContextRef context = CGBitmapContextCreate(nullptr, width, height, 8, width * 4, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
    CGColorSpaceRelease(space);
    CGContextTranslateCTM(context, 0, height);
    CGContextScaleCTM(context, scale, -scale);
    CGContextSetAllowsFontSmoothing(context, false);
    [document drawInContext:context origin:CGPointZero clip:CGRectMake(0, 0, 240, 200)];
    NSData *result = [NSData dataWithBytes:CGBitmapContextGetData(context) length:width * height * 4];
    CGContextRelease(context);
    return result;
}

- (void)testTransformedChildrenMatchUnculledClips
{
    NSMutableString *html = [NSMutableString stringWithString:@"<style>body{margin:0;width:240px;height:200px}.s{position:relative;margin:10px;width:200px;height:160px;border-radius:24px;overflow:hidden;background:#eef0f6;transform:rotate(4deg)}.t{position:absolute;left:0;top:0;width:9px;height:9px;border-radius:3px}</style><div class=s>"];
    srand48(7);
    for(int i = 0; i < 300; i++)
        [html appendFormat:@"<div class=t style=\"background:hsl(%d,80%%,58%%);transform:translate(%.2fpx,%.2fpx) rotate(%.1fdeg) scale(%.2f)\"></div>", i * 7 % 360, drand48() * 220 - 15, drand48() * 180 - 15, drand48() * 360, drand48() * 1.6];
    [html appendString:@"<div class=t style=\"transform:scale(0)\"></div></div>"];
    for(CGFloat scale : {1.0, 2.0})
    {
        LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
        [document layoutWithMaxWidth:240 preferredWidth:240];
        document.clipCulling = NO;
        NSData *expected = Render(document, scale);
        document.clipCulling = YES;
        NSData *actual = Render(document, scale);
        XCTAssertEqualObjects(expected, actual);
    }
}


- (void)testCollapsedTransformsDrawNothingInsideClips
{
    NSString *base = @"<style>body{margin:0;width:240px;height:200px}.s{position:relative;margin:10px;width:200px;height:160px;border-radius:24px;overflow:hidden;background:#eef0f6}.t{position:absolute;left:20px;top:20px;width:60px;height:60px;border-radius:8px;background:#ff2d55;overflow:hidden}.t div{width:30px;height:30px;background:#34c759}</style><div class=s>%@</div>";
    NSString *empty = [NSString stringWithFormat:base, @""];
    for(NSString *transform in @[@"scale(0)", @"scale(0,1)", @"scaleX(0) rotate(30deg)", @"matrix(0,0,0,0,10,10)"])
    {
        NSString *html = [NSString stringWithFormat:base, [NSString stringWithFormat:@"<div class=t style=\"transform:%@\"><div></div></div>", transform]];
        for(CGFloat scale : {1.0, 2.0})
        {
            LHDocument *reference = [[LHDocument alloc] initWithHTML:empty css:nil];
            [reference layoutWithMaxWidth:240 preferredWidth:240];
            LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
            [document layoutWithMaxWidth:240 preferredWidth:240];
            XCTAssertEqualObjects(Render(reference, scale), Render(document, scale), @"%@", transform);
        }
    }
}

@end
