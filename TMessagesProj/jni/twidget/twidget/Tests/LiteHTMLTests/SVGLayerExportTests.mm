#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface SVGLayerExportTests : XCTestCase
@end

@implementation SVGLayerExportTests

static NSString *ChartHTML(void)
{
    NSMutableString *points = [NSMutableString string];
    for(int i = 0; i < 60; i++)
        [points appendFormat:@"%.1f,%.1f ", i * 380.0 / 59, 80 + 40 * sin(i * 0.3)];
    return [NSString stringWithFormat:@"<style>body{margin:0}.card{padding:7px 10px;margin-top:5px;border:3px solid #ccc}</style><div class=\"card\"><b>Title</b>"
                                       "<svg data-twid=\"9\" width=\"380\" height=\"160\" viewBox=\"0 0 380 160\" style=\"margin:4px 2px\">"
                                       "<defs><linearGradient id=\"a\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"#007aff\" stop-opacity=\"0.35\"/><stop offset=\"1\" stop-color=\"#007aff\" stop-opacity=\"0\"/></linearGradient></defs>"
                                       "<path fill=\"url(#a)\" d=\"M0,160 L%@ L380,160 Z\"/><polyline fill=\"none\" stroke=\"#007aff\" stroke-width=\"1.5\" points=\"%@\"/><circle r=\"3.5\" fill=\"#007aff\" cx=\"380\" cy=\"80\"/></svg></div>", points, points];
}

static NSData *Pixels(CGImageRef image)
{
    CFDataRef data = CGDataProviderCopyData(CGImageGetDataProvider(image));
    return CFBridgingRelease(data);
}

- (void)testSnapshotKeepsLayeredSVG
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:ChartHTML() css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    CGRect rect = CGRectMake(0, 0, 420, document.size.height);
    CGImageRef plain = [document copyImageWithMaxWidth:420 rect:rect scale:2];
    [document setLayerExclusions:[NSSet setWithObject:@"9"] attribute:@"data-twid"];
    CGImageRef layered = [document copyImageWithMaxWidth:420 rect:rect scale:2];
    CGImageRef tiles = [document copyImageWithMaxWidth:420 rect:rect scale:2 excluding:document.layerExclusions attribute:@"data-twid"];
    XCTAssertTrue(plain && layered && tiles);
    XCTAssertEqualObjects(Pixels(plain), Pixels(layered));
    XCTAssertNotEqualObjects(Pixels(plain), Pixels(tiles));
    CGImageRelease(plain);
    CGImageRelease(layered);
    CGImageRelease(tiles);
}

- (void)testExportedShapesSitOnTheSVGBox
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:ChartHTML() css:nil];
    [document layoutWithMaxWidth:420 preferredWidth:420];
    LHSVGShapes *shapes = [document svgShapesWithAttribute:@"data-twid" value:@"9" maxWidth:420];
    XCTAssertNotNil(shapes);
    XCTAssertEqual(shapes.shapes.count, 3u);
    XCTAssertEqualWithAccuracy(shapes.frame.size.width, 380, 0.01);
    XCTAssertEqualWithAccuracy(shapes.frame.size.height, 160, 0.01);
    XCTAssertEqualWithAccuracy(shapes.frame.origin.x, 3 + 10 + 2, 0.01);
    CGRect box = CGPathGetPathBoundingBox(shapes.shapes[1].path);
    XCTAssertEqualWithAccuracy(CGRectGetMinX(box), shapes.frame.origin.x, 0.5);
    XCTAssertNotNil(shapes.shapes[0].gradientColors);
    XCTAssertTrue(shapes.shapes[1].strokeColor != nil);
}

- (void)testUnsupportedSVGIsNotExported
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:@"<svg data-twid=\"4\" width=\"40\" height=\"40\"><text x=\"2\" y=\"20\">hi</text></svg>" css:nil];
    [document layoutWithMaxWidth:100 preferredWidth:100];
    XCTAssertNil([document svgShapesWithAttribute:@"data-twid" value:@"4" maxWidth:100]);
    LHDocument *dashed = [[LHDocument alloc] initWithHTML:@"<svg data-twid=\"5\" width=\"40\" height=\"40\"><path d=\"M0 0L40 40\" stroke=\"#000\" stroke-dasharray=\"2 2\"/></svg>" css:nil];
    [dashed layoutWithMaxWidth:100 preferredWidth:100];
    XCTAssertNil([dashed svgShapesWithAttribute:@"data-twid" value:@"5" maxWidth:100]);
    LHDocument *framed = [[LHDocument alloc] initWithHTML:@"<svg data-twid=\"6\" width=\"40\" height=\"40\" style=\"background:#eee;border:1px solid red\"><path d=\"M0 0L40 40\" stroke=\"#000\"/></svg>" css:nil];
    [framed layoutWithMaxWidth:100 preferredWidth:100];
    XCTAssertNil([framed svgShapesWithAttribute:@"data-twid" value:@"6" maxWidth:100]);
}


- (void)testCSSRotatedClockHandMatchesSVGAttributeAndUsesRasterFallback
{
    NSString *prefix = @"<style>body{margin:0}.hand{transform-origin:100px 100px}</style><svg data-twid='9' width='150' height='150' viewBox='0 0 200 200'><line class='hand' x1='100' y1='110' x2='100' y2='20' stroke='red' stroke-width='4' ";
    LHDocument *css = [[LHDocument alloc] initWithHTML:[prefix stringByAppendingString:@"style='transform:rotate(90deg)'/></svg>"] css:nil];
    LHDocument *attr = [[LHDocument alloc] initWithHTML:[prefix stringByAppendingString:@"transform='rotate(90 100 100)'/></svg>"] css:nil];
    [css layoutWithMaxWidth:200 preferredWidth:200];
    [attr layoutWithMaxWidth:200 preferredWidth:200];
    CGImageRef a = [css copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, 160) scale:2];
    CGImageRef b = [attr copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, 160) scale:2];
    XCTAssertEqualObjects(Pixels(a), Pixels(b));
    CGImageRelease(a);
    CGImageRelease(b);
    LHSVGShapes *shapes = [css svgShapesWithAttribute:@"data-twid" value:@"9" maxWidth:200];
    LHSVGShapes *reference = [attr svgShapesWithAttribute:@"data-twid" value:@"9" maxWidth:200];
    XCTAssertNil(shapes);
    XCTAssertNil(reference);
}

@end
