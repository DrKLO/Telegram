#import <XCTest/XCTest.h>
#import <AppKit/AppKit.h>
#import "LHDocument.h"

@interface SVGGradientStrokeTests : XCTestCase
@end

@implementation SVGGradientStrokeTests

- (NSData *)pixels:(NSString *)html
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:200 preferredWidth:200];
    CGImageRef image = [document copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, 160) scale:2];
    XCTAssertNotEqual(image, nullptr);
    if(!image) return [NSData data];
    NSData *data = CFBridgingRelease(CGDataProviderCopyData(CGImageGetDataProvider(image)));
    CGImageRelease(image);
    return data;
}

- (NSString *)scene:(NSString *)paint attributes:(NSString *)attributes
{
    return [NSString stringWithFormat:@"<style>body{margin:0;background:white}</style><svg id='scene' width='200' height='160'><defs><linearGradient id='paint' gradientUnits='userSpaceOnUse' x1='0' y1='0' x2='200' y2='0'><stop stop-color='#d02080'/><stop offset='1' stop-color='#d02080'/></linearGradient></defs><path d='M20 40 L150 40 L100 110 Q50 140 20 80' fill='none' stroke='%@' stroke-width='12' %@/></svg>", paint, attributes];
}

- (void)testConstantGradientStrokeMatchesSolidStroke
{
    for(NSString *attributes in @[@"", @"stroke-linecap='round' stroke-linejoin='round'", @"stroke-dasharray='18 12' stroke-dashoffset='5' stroke-linecap='square'", @"stroke-opacity='.4' transform='translate(5 3) rotate(12 90 70)'"])
    {
        NSData *gradient = [self pixels:[self scene:@"url(#paint)" attributes:attributes]];
        NSData *solid = [self pixels:[self scene:@"#d02080" attributes:attributes]];
        XCTAssertEqual(gradient.length, solid.length);
        if(gradient.length != solid.length) continue;
        const uint8_t *a = (const uint8_t *)gradient.bytes, *b = (const uint8_t *)solid.bytes;
        NSUInteger differing = 0;
        for(NSUInteger i = 0; i < gradient.length; i++)
            if(abs((int)a[i] - (int)b[i]) > 2) differing++;
        XCTAssertLessThan(differing, 100u, @"%@", attributes);
    }
}

- (void)testGradientStrokeUsesRasterFallback
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:[self scene:@"url(#paint)" attributes:@""] css:nil];
    [document layoutWithMaxWidth:200 preferredWidth:200];
    XCTAssertNil([document svgShapesWithAttribute:@"id" value:@"scene" maxWidth:200]);
}

- (NSColor *)color:(NSString *)html x:(NSInteger)x y:(NSInteger)y
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil];
    [document layoutWithMaxWidth:200 preferredWidth:200];
    CGImageRef image = [document copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, 160) scale:2];
    XCTAssertNotEqual(image, nullptr);
    if(!image) return NSColor.clearColor;
    NSBitmapImageRep *bitmap = [[NSBitmapImageRep alloc] initWithCGImage:image];
    CGImageRelease(image);
    return [[bitmap colorAtX:x * 2 y:y * 2] colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
}

- (void)testLinearGradientStrokeInterpolatesColors
{
    NSString *html = [self scene:@"url(#paint)" attributes:@""];
    html = [html stringByReplacingOccurrencesOfString:@"<stop stop-color='#d02080'/>" withString:@"<stop stop-color='#ff0000'/>"];
    html = [html stringByReplacingOccurrencesOfString:@"stop-color='#d02080'" withString:@"stop-color='#0000ff'"];
    NSColor *left = [self color:html x:30 y:40];
    NSColor *right = [self color:html x:140 y:40];
    XCTAssertGreaterThan(left.redComponent, 0.8);
    XCTAssertLessThan(left.blueComponent, 0.2);
    XCTAssertGreaterThan(right.blueComponent, 0.65);
    XCTAssertLessThan(right.redComponent, 0.45);
}

- (void)testRadialStrokeWithTransparentLastStopStillPaints
{
    NSString *html = @"<style>body{margin:0;background:white}</style><svg width='200' height='160'><defs><radialGradient id='paint' gradientUnits='userSpaceOnUse' cx='50' cy='80' r='100'><stop stop-color='red'/><stop offset='1' stop-color='red' stop-opacity='0'/></radialGradient></defs><circle cx='100' cy='80' r='50' fill='none' stroke='url(#paint)' stroke-width='12'/></svg>";
    NSColor *left = [self color:html x:50 y:80];
    NSColor *right = [self color:html x:150 y:80];
    XCTAssertGreaterThan(left.redComponent, 0.95);
    XCTAssertLessThan(left.greenComponent, 0.2);
    XCTAssertGreaterThan(right.greenComponent, 0.95);
}

- (void)testUserSpaceGradientFillKeepsItsCoordinates
{
    NSString *html = @"<style>body{margin:0;background:white}</style><svg width='200' height='160'><defs><linearGradient id='paint' gradientUnits='userSpaceOnUse' x1='20' y1='0' x2='180' y2='0'><stop stop-color='red'/><stop offset='1' stop-color='blue'/></linearGradient></defs><rect x='20' y='20' width='160' height='80' fill='url(#paint)'/></svg>";
    NSColor *left = [self color:html x:30 y:40];
    NSColor *right = [self color:html x:170 y:40];
    XCTAssertGreaterThan(left.redComponent, 0.9);
    XCTAssertLessThan(left.blueComponent, 0.1);
    XCTAssertGreaterThan(right.blueComponent, 0.9);
    XCTAssertLessThan(right.redComponent, 0.1);
}

- (void)testTransparentGradientStopDoesNotTintVisibleStroke
{
    NSString *html = [self scene:@"url(#paint)" attributes:@""];
    html = [html stringByReplacingOccurrencesOfString:@"<stop stop-color='#d02080'/>" withString:@"<stop stop-color='red'/>"];
    html = [html stringByReplacingOccurrencesOfString:@"stop-color='#d02080'" withString:@"stop-color='blue' stop-opacity='0'"];
    NSColor *middle = [self color:html x:100 y:40];
    XCTAssertGreaterThan(middle.redComponent, 0.98);
    XCTAssertEqualWithAccuracy(middle.greenComponent, middle.blueComponent, 0.015);
    XCTAssertGreaterThan(middle.greenComponent, 0.2);
    XCTAssertLessThan(middle.greenComponent, 0.85);
}

@end
