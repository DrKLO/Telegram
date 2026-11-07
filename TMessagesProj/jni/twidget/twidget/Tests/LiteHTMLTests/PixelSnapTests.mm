#import <XCTest/XCTest.h>
#import "LHDocument.h"

@interface PixelSnapTests : XCTestCase
@end

@implementation PixelSnapTests

static NSString *Box(NSString *width)
{
    return [NSString stringWithFormat:@"<div style=\"margin:0 0 0 20px;width:%@px;height:10px;background:#000\"></div>", width];
}

static NSInteger PaintedColumns(NSString *html, CGFloat scale)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:@"body{margin:0}"];
    CGSize      size     = [document layoutWithMaxWidth:200 preferredWidth:200];
    CGImageRef  image    = [document copyImageWithMaxWidth:200 rect:CGRectMake(0, 0, 200, size.height) scale:scale];
    NSData     *data     = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    const uint8_t *p     = (const uint8_t *)data.bytes;
    size_t      row      = CGImageGetBytesPerRow(image), y = CGImageGetHeight(image) / 2;
    NSInteger   count    = 0;
    for(size_t x = 0; x < CGImageGetWidth(image); x++)
        count += p[y * row + x * 4 + 3] > 127;
    CGImageRelease(image);
    return count;
}

- (void)testBoxesSnapToDevicePixels
{
    XCTAssertEqual(PaintedColumns(Box(@"99.6"), 2), 199);
    XCTAssertEqual(PaintedColumns(Box(@"99.4"), 2), 199);
    XCTAssertEqual(PaintedColumns(Box(@"99.75"), 2), 200);
    XCTAssertEqual(PaintedColumns(Box(@"99.6"), 1), 100);
    XCTAssertEqual(PaintedColumns(Box(@"99.4"), 1), 99);
}

- (void)testDisplayListsSeeDevicePixelChanges
{
    LHDocument *before = [[LHDocument alloc] initWithHTML:Box(@"99.6") css:@"body{margin:0}"];
    LHDocument *after  = [[LHDocument alloc] initWithHTML:Box(@"99.8") css:@"body{margin:0}"];
    XCTAssertEqual([[after displayListWithMaxWidth:200 scale:1] changedRectsSince:[before displayListWithMaxWidth:200 scale:1]].count, 0u);
    XCTAssertGreaterThan([[after displayListWithMaxWidth:200 scale:2] changedRectsSince:[before displayListWithMaxWidth:200 scale:2]].count, 0u);
}

@end
