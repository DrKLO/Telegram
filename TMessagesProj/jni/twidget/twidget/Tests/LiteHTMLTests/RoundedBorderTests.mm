#import <XCTest/XCTest.h>
#import <AppKit/AppKit.h>
#import "LHDocument.h"

@interface RoundedBorderTests : XCTestCase
@end

@implementation RoundedBorderTests

- (NSBitmapImageRep *)image:(NSString *)style scale:(CGFloat)scale
{
    NSString *html = [NSString stringWithFormat:@"<style>body{margin:0}#ring{position:absolute;left:20px;top:20px;width:36px;height:36px;border:4px solid #e5e7eb;border-radius:50%%;%@}</style><div id='ring'></div>", style];
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:120 preferredWidth:120];
    CGImageRef image = [document copyImageWithMaxWidth:120 rect:CGRectMake(0, 0, 120, 120) scale:scale];
    XCTAssertNotEqual(image, nullptr);
    if(!image) return nil;
    NSBitmapImageRep *bitmap = [[NSBitmapImageRep alloc] initWithCGImage:image];
    CGImageRelease(image);
    return bitmap;
}

- (void)testSpinnerRingStaysContinuousThroughoutRotation
{
    for(NSNumber *scale in @[@1, @2])
    {
        for(int rotation = 0; rotation < 360; rotation += 15)
        {
            NSString *transform = [NSString stringWithFormat:@"transform:rotate(%ddeg)", rotation];
            NSBitmapImageRep *expected = [self image:transform scale:scale.doubleValue];
            NSBitmapImageRep *actual = [self image:[@"border-top-color:#2481cc;" stringByAppendingString:transform] scale:scale.doubleValue];
            NSUInteger missing = 0, outside = 0;
            for(NSInteger y = 0; y < actual.pixelsHigh; y++)
                for(NSInteger x = 0; x < actual.pixelsWide; x++)
                {
                    CGFloat reference = [expected colorAtX:x y:y].alphaComponent;
                    CGFloat alpha = [actual colorAtX:x y:y].alphaComponent;
                    if(reference > 0.98 && alpha < 0.8) missing++;
                    if(reference < 0.01 && alpha > 0.1) outside++;
                }
            XCTAssertLessThanOrEqual(missing, 8u * scale.unsignedIntegerValue, @"rotation=%d scale=%@", rotation, scale);
            XCTAssertEqual(outside, 0u, @"rotation=%d scale=%@", rotation, scale);
        }
    }
}

- (void)testRoundedBorderQuadrantsRetainTheirColors
{
    NSBitmapImageRep *image = [self image:@"border-color:red green blue black" scale:2];
    NSMutableArray<NSColor *> *colors = [NSMutableArray array];
    for(NSString *color in @[@"red", @"green", @"blue", @"black"])
    {
        NSBitmapImageRep *reference = [self image:[@"border-color:" stringByAppendingString:color] scale:2];
        [colors addObject:[[reference colorAtX:84 y:44] colorUsingColorSpace:NSColorSpace.sRGBColorSpace]];
    }
    for(int angle = 0; angle < 360; angle += 15)
    {
        if(angle % 90 == 45) continue;
        CGFloat radians = angle * M_PI / 180;
        NSColor *actual = [[image colorAtX:(NSInteger)((42 + 20 * sin(radians)) * 2) y:(NSInteger)((42 - 20 * cos(radians)) * 2)] colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
        NSColor *expected = colors[((angle + 45) / 90) % 4];
        XCTAssertEqualWithAccuracy(actual.alphaComponent, 1, 0.02, @"angle=%d", angle);
        XCTAssertEqualWithAccuracy(actual.redComponent, expected.redComponent, 0.02, @"angle=%d", angle);
        XCTAssertEqualWithAccuracy(actual.greenComponent, expected.greenComponent, 0.02, @"angle=%d", angle);
        XCTAssertEqualWithAccuracy(actual.blueComponent, expected.blueComponent, 0.02, @"angle=%d", angle);
    }
}

- (void)testMixedColorsPreserveSquareEllipticalAndThickBorderCoverage
{
    for(NSString *style in @[@"border-radius:0", @"border-radius:8px", @"border-radius:12px 20px 4px 18px", @"width:68px;height:20px", @"width:10px;height:10px;border-width:12px", @"width:0;height:0;border-width:12px"])
    {
        NSBitmapImageRep *expected = [self image:style scale:2];
        NSBitmapImageRep *actual = [self image:[style stringByAppendingString:@";border-top-color:#2481cc"] scale:2];
        NSUInteger missing = 0, outside = 0;
        for(NSInteger y = 0; y < actual.pixelsHigh; y++)
            for(NSInteger x = 0; x < actual.pixelsWide; x++)
            {
                CGFloat reference = [expected colorAtX:x y:y].alphaComponent;
                CGFloat alpha = [actual colorAtX:x y:y].alphaComponent;
                if(reference > 0.98 && alpha < 0.7) missing++;
                if(reference < 0.01 && alpha > 0.1) outside++;
            }
        XCTAssertEqual(missing, 0u, @"%@", style);
        XCTAssertEqual(outside, 0u, @"%@", style);
    }
}

- (void)testTransparentBorderSidesStayTransparent
{
    NSBitmapImageRep *image = [self image:@"border-color:transparent;border-top-color:#2481cc" scale:2];
    for(int angle = 0; angle < 360; angle += 15)
    {
        if(angle % 90 == 45) continue;
        CGFloat radians = angle * M_PI / 180;
        CGFloat alpha = [image colorAtX:(NSInteger)((42 + 20 * sin(radians)) * 2) y:(NSInteger)((42 - 20 * cos(radians)) * 2)].alphaComponent;
        XCTAssertEqualWithAccuracy(alpha, (angle < 45 || angle > 315) ? 1 : 0, 0.02, @"angle=%d", angle);
    }
}

@end
