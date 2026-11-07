#import <XCTest/XCTest.h>
#import <CoreText/CoreText.h>
#import "LHDocument.h"

#include <random>

@interface ColorMatchTests : XCTestCase
@end

@implementation ColorMatchTests

- (void)testMatchedColoursPaintLikeSRGBColours
{
    CGColorSpaceRef srgb = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    CTFontRef       font = CTFontCreateWithName(CFSTR("Helvetica"), 14, nullptr);
    for(CFStringRef name : {kCGColorSpaceDisplayP3, kCGColorSpaceAdobeRGB1998, kCGColorSpaceSRGB})
    {
        CGColorSpaceRef space = CGColorSpaceCreateWithName(name);
        CGContextRef    a     = CGBitmapContextCreate(nullptr, 128, 128, 8, 0, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
        CGContextRef    b     = CGBitmapContextCreate(nullptr, 128, 128, 8, 0, space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
        std::mt19937    rng(11);
        const void     *keys[]  = {kCTFontAttributeName, kCTForegroundColorFromContextAttributeName};
        const void     *vals[]  = {font, kCFBooleanTrue};
        CFDictionaryRef attrs   = CFDictionaryCreate(nullptr, keys, vals, 2, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
        CFAttributedStringRef s = CFAttributedStringCreate(nullptr, CFSTR("Wg 0.5"), attrs);
        CTLineRef       line    = CTLineCreateWithAttributedString(s);
        for(int i = 0; i < 600; i++)
        {
            const CGFloat comps[4] = {(rng() % 256) / 255.0, (rng() % 256) / 255.0, (rng() % 256) / 255.0, (rng() % 256) / 255.0};
            CGColorRef    plain    = CGColorCreate(srgb, comps);
            CGColorRef    matched  = CGColorCreateCopyByMatchingToColorSpace(space, kCGRenderingIntentDefault, plain, nullptr);
            CGRect        r        = CGRectMake(rng() % 128, rng() % 128, 1 + rng() % 30, 1 + rng() % 30);
            CGContextSetFillColorWithColor(a, plain);
            CGContextSetFillColorWithColor(b, matched);
            CGContextFillRect(a, r);
            CGContextFillRect(b, r);
            CGContextSetTextPosition(a, r.origin.x, r.origin.y);
            CGContextSetTextPosition(b, r.origin.x, r.origin.y);
            CTLineDraw(line, a);
            CTLineDraw(line, b);
            CGColorRelease(plain);
            CGColorRelease(matched);
        }
        NSData *da = [NSData dataWithBytes:CGBitmapContextGetData(a) length:CGBitmapContextGetBytesPerRow(a) * 128];
        NSData *db = [NSData dataWithBytes:CGBitmapContextGetData(b) length:CGBitmapContextGetBytesPerRow(b) * 128];
        XCTAssertEqualObjects(da, db, @"%@", name);
        CFRelease(line);
        CFRelease(s);
        CFRelease(attrs);
        CGContextRelease(a);
        CGContextRelease(b);
        CGColorSpaceRelease(space);
    }
    CFRelease(font);
    CGColorSpaceRelease(srgb);
}

@end
